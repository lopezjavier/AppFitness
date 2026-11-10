-- T-268: a cardio exercise never gets the no-range flag; the flag stores the routine so the app can open it.
-- Only apply_coach_progression writes no_range_flag rows (the block review reuses it).

create or replace function public.apply_coach_progression(p_session_id uuid)
returns jsonb
language plpgsql
volatile
security definer
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

  -- A session started after the block's last day writes nothing (the review comes first).
  if (v_s.started_at at time zone 'utc')::date > coalesce(public.coach_block_end(v_plan.id), 'infinity'::date) then
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
          -- The user already planned this weight or more: the coach never lowers a plan on a raise.
          if (select max(public.coach_top_weight(b)) from jsonb_array_elements(v_before) b) >= v_new then
            continue;
          end if;
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
        and not exists (select 1 from public.exercises e
                        where e.id = ws.exercise_id and e.movement_pattern = 'cardio')
    loop
      insert into public.coach_changes (plan_id, kind, exercise_id, source_session_id, reason, figures, status)
      values (v_plan.id, 'no_range_flag', v_ex.exercise_id, p_session_id, 'no_rep_range',
              jsonb_build_object('routine_id', (
                select r.id from public.routines r
                join public.routine_exercises re on re.routine_id = r.id
                where r.user_id = v_uid and r.folder_id = v_plan.folder_id
                  and re.exercise_id = v_ex.exercise_id and re.rep_min is null
                order by (r.id = v_s.routine_id) desc, r.id
                limit 1)),
              'pending')
      on conflict do nothing;
    end loop;
  end if;

  return coalesce((select jsonb_agg(public.coach_change_json(c) order by c.created_at, c.id)
                   from public.coach_changes c
                   where c.user_id = v_uid and c.source_session_id = p_session_id), '[]'::jsonb);
end;
$$;

-- Pending flags already raised for cardio exercises are noise: remove them.
delete from public.coach_changes c
using public.exercises e
where c.kind = 'no_range_flag' and c.status = 'pending'
  and e.id = c.exercise_id and e.movement_pattern = 'cardio';
