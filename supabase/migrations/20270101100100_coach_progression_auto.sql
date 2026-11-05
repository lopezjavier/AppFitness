-- T-227 (E14, US-97/US-104): the coach applies the double progression on its own inside an accepted block.
-- Rules: planning/04-business-rules.md section 4.14. Everything here is security invoker and filtered by
-- auth.uid() through RLS; there is no security definer.
--
-- Trigger: the client calls apply_coach_progression(session) once the "update the routine?" question (ADR-70) is
-- resolved, so the coach's write always lands on top of update_routine_from_session. Calling it before that update
-- is not supported (the update would replace the coach's values).

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

-- Step of an exercise (04 section 4.9): dumbbells use the handle weight, everything else twice the smallest enabled
-- plate (1 kg with the default plates).
create function public.coach_load_step(p_uid uuid, p_exercise_id uuid)
returns numeric
language sql
stable
security invoker
set search_path = ''
as $$
  select case
           when e.equipment = 'dumbbell' then coalesce(tp.dumbbell_handle_kg, 1)
           else 2 * coalesce((select min(x) from unnest(tp.plate_kg) as x), 0.5)
         end
  from (select 1) d
  left join public.exercises e on e.id = p_exercise_id
  left join public.training_preferences tp on tp.user_id = p_uid
$$;

-- True when the session was done in a week of its plan that has an accepted deload (coach_plan_sessions.deload).
create function public.coach_session_in_deload(p_uid uuid, p_session_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select exists (
    select 1
    from public.workout_sessions s
    join public.coach_plan_sessions ps on ps.routine_id = s.routine_id and ps.user_id = p_uid and ps.deload
    join public.coach_plans p on p.id = ps.plan_id and p.user_id = p_uid
    where s.id = p_session_id and s.user_id = p_uid
      and (s.started_at at time zone 'utc')::date
          between coalesce(p.training_start, p.block_start) + 7 * (ps.week - 1)
              and coalesce(p.training_start, p.block_start) + 7 * (ps.week - 1) + 6
  )
$$;

-- Planned values of one routine exercise (snapshot entry, planning/03-data-model.md).
create function public.coach_re_snapshot(p_re_id uuid)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select jsonb_build_object(
    'routine_id', re.routine_id, 'exercise_id', re.exercise_id,
    'rep_min', re.rep_min, 'rep_max', re.rep_max,
    'sets', coalesce((select jsonb_agg(jsonb_build_object(
                        'set_index', s.set_index, 'set_type', s.set_type::text,
                        'target_weight_kg', s.target_weight_kg, 'target_reps', s.target_reps)
                        order by s.set_index)
                      from public.routine_exercise_sets s where s.routine_exercise_id = re.id), '[]'::jsonb))
  from public.routine_exercises re
  where re.id = p_re_id
$$;

-- The routine exercise of an exercise in a routine (the first one by position), or null.
create function public.coach_find_re(p_routine_id uuid, p_exercise_id uuid)
returns uuid
language sql
stable
security invoker
set search_path = ''
as $$
  select re.id
  from public.routine_exercises re
  join public.routines r on r.id = re.routine_id
  where re.routine_id = p_routine_id and re.exercise_id = p_exercise_id and r.user_id = (select auth.uid())
  order by re.position
  limit 1
$$;

-- Highest planned working weight of a snapshot entry.
create function public.coach_top_weight(p_entry jsonb)
returns numeric
language sql
immutable
set search_path = ''
as $$
  select max((s ->> 'target_weight_kg')::numeric)
  from jsonb_array_elements(p_entry -> 'sets') as s
  where s ->> 'set_type' = 'working'
$$;

-- The routine exercises of the block that hold an exercise with an exact rep range: one per routine of the
-- folder, only those with planned sets.
create function public.coach_block_targets(
  p_uid uuid, p_folder_id uuid, p_exercise_id uuid, p_rep_min smallint, p_rep_max smallint)
returns table (re_id uuid, routine_id uuid)
language sql
stable
security invoker
set search_path = ''
as $$
  select distinct on (re.routine_id) re.id, re.routine_id
  from public.routines r
  join public.routine_exercises re on re.routine_id = r.id
  where r.user_id = p_uid and r.folder_id = p_folder_id
    and re.exercise_id = p_exercise_id and re.rep_min = p_rep_min and re.rep_max = p_rep_max
    and exists (select 1 from public.routine_exercise_sets s where s.routine_exercise_id = re.id)
  order by re.routine_id, re.position
$$;

create function public.coach_targets_snapshot(
  p_uid uuid, p_folder_id uuid, p_exercise_id uuid, p_rep_min smallint, p_rep_max smallint)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(jsonb_agg(public.coach_re_snapshot(t.re_id) order by t.routine_id), '[]'::jsonb)
  from public.coach_block_targets(p_uid, p_folder_id, p_exercise_id, p_rep_min, p_rep_max) t
$$;

-- Legacy summary of a routine exercise: first working set's reps, else the first set's.
create function public.coach_refresh_summary(p_re_id uuid)
returns void
language sql
volatile
security invoker
set search_path = ''
as $$
  update public.routine_exercises re
  set target_reps = x.reps
  from (select s.target_reps as reps
        from public.routine_exercise_sets s
        where s.routine_exercise_id = p_re_id
        order by (s.set_type <> 'working'), s.set_index
        limit 1) x
  where re.id = p_re_id
$$;

-- Writes a new weight to the planned working sets (warm-up, drop and failure sets and sets without weight stay) of
-- every block routine holding the exercise with that range; a raise also sets the planned reps to rep_min.
create function public.coach_write_load(
  p_uid uuid, p_folder_id uuid, p_exercise_id uuid, p_rep_min smallint, p_rep_max smallint,
  p_weight numeric, p_reset_reps boolean)
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_t record;
begin
  for v_t in
    select t.re_id from public.coach_block_targets(p_uid, p_folder_id, p_exercise_id, p_rep_min, p_rep_max) t
  loop
    update public.routine_exercise_sets
    set target_weight_kg = p_weight,
        target_reps = case when p_reset_reps then p_rep_min else target_reps end
    where routine_exercise_id = v_t.re_id and set_type = 'working' and target_weight_kg is not null;
    perform public.coach_refresh_summary(v_t.re_id);
  end loop;
end;
$$;

create function public.coach_change_json(c public.coach_changes)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', c.id, 'kind', c.kind, 'exercise_id', c.exercise_id, 'status', c.status, 'reason', c.reason,
    'figures', c.figures, 'before_values', c.before_values, 'after_values', c.after_values,
    'decision', c.decision)
$$;

-- ---------------------------------------------------------------------------
-- progression_decision_core: the body of progression_suggestions (T-159), shared with the coach writer.
-- p_include_self = false: the suggestion for a session in progress (earlier sessions only), output unchanged.
-- p_include_self = true: the coach's decision for a finished session, which is S1 and must be the latest completed
-- session of the exercise with that range. In this mode a deload-week session counts for nothing (it neither
-- decides nor serves as S2), and after 14 or more days without a completed session of the exercise there is no
-- raise and one failed session is enough for the drop (04 section 4.14). It also reports reps_best, rpe_max,
-- sessions and discomfort_level.
-- ---------------------------------------------------------------------------
create function public.progression_decision_core(p_uid uuid, p_session_id uuid, p_include_self boolean)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_ex       record;
  v_level    smallint;
  v_sids     uuid[];
  v_n1       int;
  v_min1     smallint;
  v_max1     smallint;
  v_top1     numeric;
  v_rpe1     numeric;
  v_anyrpe   boolean;
  v_below1   int;
  v_below2   int;
  v_n2       int;
  v_action   text;
  v_reason   text;
  v_goal     smallint;
  v_used     jsonb;
  v_item     jsonb;
  v_start1   timestamptz;
  v_prev     timestamptz;
  v_gap      boolean;
  v_result   jsonb := '[]'::jsonb;
begin
  if p_uid is null or p_uid is distinct from (select auth.uid()) then
    return '[]'::jsonb;
  end if;
  perform 1
  from public.workout_sessions s
  where s.id = p_session_id and s.user_id = p_uid;
  if not found then
    return '[]'::jsonb;
  end if;

  for v_ex in
    select distinct on (ws.exercise_id) ws.exercise_id, ws.rep_min, ws.rep_max
    from public.workout_sets ws
    where ws.session_id = p_session_id and ws.rep_min is not null
    order by ws.exercise_id, ws.exercise_position, ws.set_index
  loop
    select max(d.level) into v_level
    from public.exercise_discomforts d
    where d.user_id = p_uid and d.exercise_id = v_ex.exercise_id
      and d.resolved_at is null and now() < d.logged_at + interval '7 days';
    if coalesce(v_level, 0) >= 4 then
      continue;
    end if;

    -- The two most recent completed sessions of this exercise done with the exact same range.
    select array_agg(m.id order by m.started_at desc) into v_sids
    from (
      select s.id, s.started_at
      from public.workout_sessions s
      where s.user_id = p_uid and s.status = 'completed'
        and (p_include_self or s.id <> p_session_id)
        and (not p_include_self or not public.coach_session_in_deload(p_uid, s.id))
        and exists (select 1 from public.workout_sets ws
                    where ws.session_id = s.id and ws.exercise_id = v_ex.exercise_id
                      and ws.rep_min = v_ex.rep_min and ws.rep_max = v_ex.rep_max
                      and ws.set_type = 'working' and ws.completed_at is not null)
      order by s.started_at desc
      limit 2
    ) m;
    if v_sids is null then
      continue;
    end if;
    -- A session that is not the latest of its exercise (or sits in a deload week) decides nothing.
    if p_include_self and v_sids[1] is distinct from p_session_id then
      continue;
    end if;

    select count(*), min(ws.reps), max(ws.reps), max(ws.weight_kg), max(ws.rpe), bool_or(ws.rpe is not null),
           count(*) filter (where ws.reps < v_ex.rep_min)
    into v_n1, v_min1, v_max1, v_top1, v_rpe1, v_anyrpe, v_below1
    from public.workout_sets ws
    where ws.session_id = v_sids[1] and ws.exercise_id = v_ex.exercise_id
      and ws.rep_min = v_ex.rep_min and ws.rep_max = v_ex.rep_max
      and ws.set_type = 'working' and ws.completed_at is not null;

    v_n2 := 0;
    v_below2 := 0;
    if array_length(v_sids, 1) = 2 then
      select count(*), count(*) filter (where ws.reps < v_ex.rep_min) into v_n2, v_below2
      from public.workout_sets ws
      where ws.session_id = v_sids[2] and ws.exercise_id = v_ex.exercise_id
        and ws.rep_min = v_ex.rep_min and ws.rep_max = v_ex.rep_max
        and ws.set_type = 'working' and ws.completed_at is not null;
    end if;

    v_gap := false;
    if p_include_self then
      select s.started_at into v_start1 from public.workout_sessions s where s.id = v_sids[1];
      select max(s.started_at) into v_prev
      from public.workout_sessions s
      where s.user_id = p_uid and s.status = 'completed' and s.id <> v_sids[1] and s.started_at < v_start1
        and exists (select 1 from public.workout_sets ws
                    where ws.session_id = s.id and ws.exercise_id = v_ex.exercise_id
                      and ws.set_type = 'working' and ws.completed_at is not null);
      v_gap := v_prev is not null and v_start1 - v_prev >= interval '14 days';
    end if;

    v_goal := least(v_ex.rep_max, v_max1 + 1)::smallint;
    if v_min1 >= v_ex.rep_max and coalesce(v_rpe1, 0) <= 8 then
      if v_gap then
        v_action := 'add_rep'; v_reason := 'after_gap';
      elsif coalesce(v_level, 0) >= 1 then
        v_action := 'add_rep'; v_reason := 'kept_discomfort'; v_goal := v_ex.rep_max;
      else
        v_action := 'raise_weight'; v_reason := 'all_sets_at_max'; v_goal := v_ex.rep_min;
      end if;
    elsif v_gap and v_below1 * 2 > v_n1 then
      v_action := 'lower_weight'; v_reason := 'one_session_below_min_after_gap'; v_goal := v_ex.rep_min;
    elsif v_n2 > 0 and v_below1 * 2 > v_n1 and v_below2 * 2 > v_n2 then
      v_action := 'lower_weight'; v_reason := 'two_sessions_below_min'; v_goal := v_ex.rep_min;
    else
      v_action := 'add_rep'; v_reason := 'below_max';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
             'session_date', (s.started_at at time zone 'utc')::date,
             'weight_kg', ws.weight_kg, 'reps', ws.reps, 'rpe', ws.rpe)
             order by s.started_at desc, ws.set_index), '[]'::jsonb)
    into v_used
    from public.workout_sessions s
    join public.workout_sets ws on ws.session_id = s.id
    where s.id = any (case when v_action = 'lower_weight' and not v_gap then v_sids else v_sids[1:1] end)
      and ws.exercise_id = v_ex.exercise_id
      and ws.rep_min = v_ex.rep_min and ws.rep_max = v_ex.rep_max
      and ws.set_type = 'working' and ws.completed_at is not null;

    v_item := jsonb_build_object(
      'exercise_id', v_ex.exercise_id, 'rep_min', v_ex.rep_min, 'rep_max', v_ex.rep_max,
      'action', v_action, 'base_weight_kg', v_top1, 'goal_reps', v_goal, 'reason', v_reason,
      'used_rpe', v_anyrpe, 'sets_used', v_used);
    if p_include_self then
      v_item := v_item || jsonb_build_object(
        'reps_best', v_max1, 'rpe_max', v_rpe1, 'discomfort_level', v_level,
        'sessions', case when v_action = 'lower_weight' and not v_gap then 2 else 1 end);
    end if;
    v_result := v_result || v_item;
  end loop;

  return v_result;
end;
$$;

-- progression_suggestions now delegates (same output as 20261206100000_progression.sql).
create or replace function public.progression_suggestions(p_session_id uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  return public.progression_decision_core((select auth.uid()), p_session_id, false);
end;
$$;

-- ---------------------------------------------------------------------------
-- apply_coach_progression: decides and writes the progression of a finished session of a block routine.
-- Returns the change rows of the session (created now or by an earlier call): [] outside an accepted block, for a
-- session that is not the latest of its exercise, in a deload week, or when nothing is due. Idempotent.
-- ---------------------------------------------------------------------------
create function public.apply_coach_progression(p_session_id uuid)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_s       public.workout_sessions;
  v_plan    public.coach_plans;
  v_item    jsonb;
  v_ex      record;
  v_exid    uuid;
  v_rmin    smallint;
  v_rmax    smallint;
  v_base    numeric;
  v_step    numeric;
  v_new     numeric;
  v_before  jsonb;
  v_after   jsonb;
  v_target  numeric;
begin
  if p_session_id is null then
    raise exception 'session id is required' using errcode = '22023';
  end if;
  if v_uid is null then
    return '[]'::jsonb;
  end if;

  select * into v_s from public.workout_sessions s
  where s.id = p_session_id and s.user_id = v_uid and s.status = 'completed';
  if not found or v_s.routine_id is null then
    return '[]'::jsonb;
  end if;

  select p.* into v_plan
  from public.coach_plans p
  join public.routines r on r.id = v_s.routine_id and r.user_id = v_uid and r.folder_id = p.folder_id
  where p.user_id = v_uid and p.status = 'accepted' and p.folder_id is not null;
  if not found then
    return '[]'::jsonb;
  end if;

  -- A no-range flag closes by itself once the exercise has a range in a routine of the block.
  update public.coach_changes c
  set status = 'decided', decision = 'range_added', decided_at = now()
  where c.user_id = v_uid and c.plan_id = v_plan.id and c.kind = 'no_range_flag' and c.status = 'pending'
    and exists (select 1 from public.routines r
                join public.routine_exercises re on re.routine_id = r.id
                where r.user_id = v_uid and r.folder_id = v_plan.folder_id
                  and re.exercise_id = c.exercise_id and re.rep_min is not null);

  if not exists (select 1 from public.coach_changes c
                 where c.user_id = v_uid and c.source_session_id = p_session_id)
     and not public.coach_session_in_deload(v_uid, p_session_id) then

    for v_item in
      select e from jsonb_array_elements(public.progression_decision_core(v_uid, p_session_id, true)) e
    loop
      v_exid := (v_item ->> 'exercise_id')::uuid;
      v_rmin := (v_item ->> 'rep_min')::smallint;
      v_rmax := (v_item ->> 'rep_max')::smallint;
      v_base := (v_item ->> 'base_weight_kg')::numeric;
      v_before := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax);
      if v_before = '[]'::jsonb or v_base is null or v_base <= 0 then
        continue;
      end if;

      if v_item ->> 'action' = 'raise_weight' then
        -- A flag waiting for the user blocks the next raise of that exercise.
        if exists (select 1 from public.coach_changes c
                   where c.user_id = v_uid and c.plan_id = v_plan.id and c.exercise_id = v_exid
                     and c.kind in ('load_flag', 'no_range_flag') and c.status = 'pending') then
          continue;
        end if;
        v_step := public.coach_load_step(v_uid, v_exid);
        if v_step / v_base > 0.10 then
          insert into public.coach_changes
            (plan_id, kind, exercise_id, source_session_id, before_values, reason, figures, status)
          values (v_plan.id, 'load_flag', v_exid, p_session_id, v_before, 'step_over_10_percent',
                  jsonb_build_object('current_kg', v_base, 'step_kg', v_step, 'next_kg', v_base + v_step,
                                     'percent', round(v_step / v_base * 100, 1),
                                     'rep_min', v_rmin, 'rep_max', v_rmax),
                  'pending')
          on conflict do nothing;
        else
          v_new := v_base + v_step;
          perform public.coach_write_load(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax, v_new, true);
          v_after := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax);
          insert into public.coach_changes
            (plan_id, kind, exercise_id, source_session_id, before_values, after_values, reason, figures, status)
          values (v_plan.id, 'load_raise', v_exid, p_session_id, v_before, v_after, 'all_sets_at_max',
                  jsonb_build_object('weight_from_kg', v_base, 'weight_to_kg', v_new,
                                     'reps_best', v_item -> 'reps_best', 'rpe_max', v_item -> 'rpe_max',
                                     'sessions', v_item -> 'sessions', 'step_kg', v_step),
                  'applied')
          on conflict do nothing;
        end if;

      elsif v_item ->> 'action' = 'lower_weight' then
        v_step := public.coach_load_step(v_uid, v_exid);
        v_target := v_base * 0.95;
        v_new := case when v_step <= 0 then v_target else round(v_target / v_step) * v_step end;
        if v_new >= v_base then
          v_new := v_base - v_step;
        end if;
        v_new := greatest(v_new, 0);
        if v_new >= v_base then
          continue;
        end if;
        perform public.coach_write_load(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax, v_new, false);
        v_after := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax);
        insert into public.coach_changes
          (plan_id, kind, exercise_id, source_session_id, before_values, after_values, reason, figures, status)
        values (v_plan.id, 'load_drop', v_exid, p_session_id, v_before, v_after, v_item ->> 'reason',
                jsonb_build_object('weight_from_kg', v_base, 'weight_to_kg', v_new,
                                   'reps_best', v_item -> 'reps_best', 'rpe_max', v_item -> 'rpe_max',
                                   'sessions', v_item -> 'sessions', 'step_kg', v_step, 'percent', 5),
                'applied')
        on conflict do nothing;

      elsif v_item ->> 'reason' = 'kept_discomfort' then
        insert into public.coach_changes
          (plan_id, kind, exercise_id, source_session_id, before_values, after_values, reason, figures, status)
        values (v_plan.id, 'load_kept', v_exid, p_session_id, v_before, v_before, 'kept_discomfort',
                jsonb_build_object('weight_kg', v_base, 'reps_best', v_item -> 'reps_best',
                                   'rpe_max', v_item -> 'rpe_max', 'level', v_item -> 'discomfort_level'),
                'applied')
        on conflict do nothing;
      end if;
    end loop;

    -- Exercises of the block done without a rep range: flag only, nothing is written.
    for v_ex in
      select distinct ws.exercise_id
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.rep_min is null
        and ws.set_type = 'working' and ws.completed_at is not null
        and exists (select 1 from public.routines r
                    join public.routine_exercises re on re.routine_id = r.id
                    where r.user_id = v_uid and r.folder_id = v_plan.folder_id
                      and re.exercise_id = ws.exercise_id and re.rep_min is null)
    loop
      insert into public.coach_changes (plan_id, kind, exercise_id, source_session_id, reason, status)
      values (v_plan.id, 'no_range_flag', v_ex.exercise_id, p_session_id, 'no_rep_range', 'pending')
      on conflict do nothing;
    end loop;
  end if;

  return coalesce((select jsonb_agg(public.coach_change_json(c) order by c.created_at, c.id)
                   from public.coach_changes c
                   where c.user_id = v_uid and c.source_session_id = p_session_id), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- undo_coach_change: restores the planned values from before a raise or drop, routine by routine, only where the
-- current values still equal what the coach wrote (a later edit by the user wins and is skipped).
-- ---------------------------------------------------------------------------
create function public.undo_coach_change(p_id uuid)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := (select auth.uid());
  v_c        public.coach_changes;
  v_after    jsonb;
  v_before   jsonb;
  v_re       uuid;
  v_set      jsonb;
  v_restored int := 0;
  v_skipped  int := 0;
begin
  select * into v_c from public.coach_changes c where c.id = p_id and c.user_id = v_uid for update;
  if not found then
    raise exception 'coach change not found' using errcode = 'P0002';
  end if;
  if v_c.kind not in ('load_raise', 'load_drop') or v_c.status <> 'applied' then
    raise exception 'coach_change_not_undoable' using errcode = 'P0001';
  end if;

  for v_after in select e from jsonb_array_elements(v_c.after_values) e loop
    v_re := public.coach_find_re((v_after ->> 'routine_id')::uuid, (v_after ->> 'exercise_id')::uuid);
    if v_re is null or public.coach_re_snapshot(v_re) <> v_after then
      v_skipped := v_skipped + 1;
      continue;
    end if;
    select b into v_before
    from jsonb_array_elements(v_c.before_values) b
    where b ->> 'routine_id' = v_after ->> 'routine_id' and b ->> 'exercise_id' = v_after ->> 'exercise_id'
    limit 1;
    if v_before is null then
      v_skipped := v_skipped + 1;
      continue;
    end if;

    update public.routine_exercises
    set rep_min = (v_before ->> 'rep_min')::smallint, rep_max = (v_before ->> 'rep_max')::smallint
    where id = v_re;
    for v_set in select s from jsonb_array_elements(v_before -> 'sets') s loop
      update public.routine_exercise_sets
      set target_weight_kg = (v_set ->> 'target_weight_kg')::numeric,
          target_reps = (v_set ->> 'target_reps')::smallint
      where routine_exercise_id = v_re and set_index = (v_set ->> 'set_index')::smallint;
    end loop;
    perform public.coach_refresh_summary(v_re);
    v_restored := v_restored + 1;
  end loop;

  if v_restored = 0 then
    raise exception 'coach_change_conflict' using errcode = 'P0001';
  end if;

  update public.coach_changes set status = 'undone', decided_at = now() where id = v_c.id;
  return jsonb_build_object('restored', v_restored, 'skipped', v_skipped);
end;
$$;

-- ---------------------------------------------------------------------------
-- decide_progression_flag: the user's answer to a flagged raise (over 10 %). 'raise' applies the stored step and
-- records a new load_raise row (undoable); 'keep' adds 2 repetitions to the top of the range (capped at 50).
-- ---------------------------------------------------------------------------
create function public.decide_progression_flag(p_id uuid, p_decision text)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid    uuid := (select auth.uid());
  v_f      public.coach_changes;
  v_plan   public.coach_plans;
  v_new    public.coach_changes;
  v_e      jsonb;
  v_re     uuid;
  v_alive  int := 0;
  v_rmin   smallint;
  v_rmax   smallint;
  v_before jsonb;
  v_after  jsonb;
begin
  if p_decision is null or p_decision not in ('raise', 'keep') then
    raise exception 'invalid decision' using errcode = '22023';
  end if;
  select * into v_f from public.coach_changes c where c.id = p_id and c.user_id = v_uid for update;
  if not found then
    raise exception 'coach change not found' using errcode = 'P0002';
  end if;
  if v_f.kind <> 'load_flag' then
    raise exception 'this flag cannot be decided' using errcode = '22023';
  end if;
  if v_f.status <> 'pending' then
    raise exception 'coach_change_not_pending' using errcode = 'P0001';
  end if;

  select * into v_plan from public.coach_plans p where p.id = v_f.plan_id and p.user_id = v_uid;
  if not found or v_plan.folder_id is null then
    raise exception 'coach_change_conflict' using errcode = 'P0001';
  end if;
  v_rmin := (v_f.figures ->> 'rep_min')::smallint;
  v_rmax := (v_f.figures ->> 'rep_max')::smallint;

  if p_decision = 'raise' then
    -- The planned weight must still be what it was when the flag was raised.
    for v_e in select e from jsonb_array_elements(v_f.before_values) e loop
      v_re := public.coach_find_re((v_e ->> 'routine_id')::uuid, (v_e ->> 'exercise_id')::uuid);
      if v_re is null then
        continue;
      end if;
      v_alive := v_alive + 1;
      if public.coach_top_weight(public.coach_re_snapshot(v_re)) is distinct from public.coach_top_weight(v_e) then
        raise exception 'coach_change_conflict' using errcode = 'P0001';
      end if;
    end loop;
    v_before := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_f.exercise_id, v_rmin, v_rmax);
    if v_alive = 0 or v_before = '[]'::jsonb then
      raise exception 'coach_change_conflict' using errcode = 'P0001';
    end if;

    perform public.coach_write_load(v_uid, v_plan.folder_id, v_f.exercise_id, v_rmin, v_rmax,
                                    (v_f.figures ->> 'next_kg')::numeric, true);
    v_after := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_f.exercise_id, v_rmin, v_rmax);
    insert into public.coach_changes
      (plan_id, kind, exercise_id, origin_flag_id, before_values, after_values, reason, figures, status)
    values (v_f.plan_id, 'load_raise', v_f.exercise_id, v_f.id, v_before, v_after, 'all_sets_at_max',
            jsonb_build_object('weight_from_kg', v_f.figures -> 'current_kg', 'weight_to_kg', v_f.figures -> 'next_kg',
                               'step_kg', v_f.figures -> 'step_kg', 'percent', v_f.figures -> 'percent'),
            'applied')
    returning * into v_new;

    update public.coach_changes
    set status = 'decided', decision = 'raise', decided_at = now()
    where id = v_f.id
    returning * into v_f;
    return jsonb_build_object('flag', public.coach_change_json(v_f), 'change', public.coach_change_json(v_new));
  end if;

  -- keep
  v_before := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_f.exercise_id, v_rmin, v_rmax);
  if v_before = '[]'::jsonb then
    raise exception 'coach_change_conflict' using errcode = 'P0001';
  end if;
  update public.routine_exercises
  set rep_max = least(50, rep_max + 2)
  where id in (select t.re_id
               from public.coach_block_targets(v_uid, v_plan.folder_id, v_f.exercise_id, v_rmin, v_rmax) t);
  select jsonb_agg(public.coach_re_snapshot(public.coach_find_re((e ->> 'routine_id')::uuid, (e ->> 'exercise_id')::uuid))
                   order by e ->> 'routine_id')
  into v_after
  from jsonb_array_elements(v_before) e;
  update public.coach_changes
  set status = 'decided', decision = 'keep', decided_at = now(), before_values = v_before, after_values = v_after
  where id = v_f.id
  returning * into v_f;
  return jsonb_build_object('flag', public.coach_change_json(v_f), 'change', null);
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges: nobody anonymous; the helpers are invoker functions that read through RLS.
-- ---------------------------------------------------------------------------
revoke execute on function public.coach_load_step(uuid, uuid) from public, anon;
revoke execute on function public.coach_session_in_deload(uuid, uuid) from public, anon;
revoke execute on function public.coach_re_snapshot(uuid) from public, anon;
revoke execute on function public.coach_find_re(uuid, uuid) from public, anon;
revoke execute on function public.coach_top_weight(jsonb) from public, anon;
revoke execute on function public.coach_block_targets(uuid, uuid, uuid, smallint, smallint) from public, anon;
revoke execute on function public.coach_targets_snapshot(uuid, uuid, uuid, smallint, smallint) from public, anon;
revoke execute on function public.coach_refresh_summary(uuid) from public, anon;
revoke execute on function public.coach_write_load(uuid, uuid, uuid, smallint, smallint, numeric, boolean) from public, anon;
revoke execute on function public.coach_change_json(public.coach_changes) from public, anon;
revoke execute on function public.progression_decision_core(uuid, uuid, boolean) from public, anon;
revoke execute on function public.apply_coach_progression(uuid) from public, anon;
revoke execute on function public.undo_coach_change(uuid) from public, anon;
revoke execute on function public.decide_progression_flag(uuid, text) from public, anon;

grant execute on function public.coach_load_step(uuid, uuid) to authenticated;
grant execute on function public.coach_session_in_deload(uuid, uuid) to authenticated;
grant execute on function public.coach_re_snapshot(uuid) to authenticated;
grant execute on function public.coach_find_re(uuid, uuid) to authenticated;
grant execute on function public.coach_top_weight(jsonb) to authenticated;
grant execute on function public.coach_block_targets(uuid, uuid, uuid, smallint, smallint) to authenticated;
grant execute on function public.coach_targets_snapshot(uuid, uuid, uuid, smallint, smallint) to authenticated;
grant execute on function public.coach_refresh_summary(uuid) to authenticated;
grant execute on function public.coach_write_load(uuid, uuid, uuid, smallint, smallint, numeric, boolean) to authenticated;
grant execute on function public.coach_change_json(public.coach_changes) to authenticated;
grant execute on function public.progression_decision_core(uuid, uuid, boolean) to authenticated;
grant execute on function public.apply_coach_progression(uuid) to authenticated;
grant execute on function public.undo_coach_change(uuid) to authenticated;
grant execute on function public.decide_progression_flag(uuid, text) to authenticated;
