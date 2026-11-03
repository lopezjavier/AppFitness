-- T-205 (E13, US-72 amended, US-93 "from scratch" data side, US-96 coach part, ADR-93, ADR-94): the model builds the block.
--   (1) unresolved_severe_discomforts: exercises with an open level 4-5 discomfort (hard check H6).
--   (2) coach_block_model_input: what the model sees besides coach_summary (folders, 8 weeks of history, previous
--       blocks with adherence, favourites, unresolved exercises).
--   (3) coach_block_rep_ok: the H4 rep-range bounds (internal helper).
--   (4) coach_assert_block: the structural half of the routine checker (D6). The TypeScript checker
--       (supabase/functions/coach/routineChecker.ts) is the full one; both use these codes in the message
--       'block invariant: <code>':
--         h4_rep_range, h5_rpe, h5_set_type, h6_equipment, h6_excluded_discomfort, h6_sessions, h6_sets_per_exercise,
--         h6_rest, h7_order, superset_invalid, superset_sets_mismatch, drop_invalid
--       plus the cardio codes and the legacy shape codes. H1 weekly sets, H2, H3/H3b and the time fit stay TypeScript-only
--       (they need the intention, age and conditions the stored block does not carry).
--   (5) save_coach_block: target RPE, superset groups, drop sets and planned weights reach save_routine.
-- Stored exercise shape (coach_plan_sessions.exercises[]): role (main | isolation | cardio), exercise_id, exercise_key,
-- rep_min, rep_max, rest_seconds, reason, and either sets: <integer> (all working sets, the shape before this task)
-- or sets: [{type: working | drop, rep_min?, rep_max?, rpe?, rest_seconds?}], plus optional target_rpe (7-9),
-- superset_group (integer), planned_weight_kg (> 0) and technique_note (text).
-- Bases (never edited): coach_block_cardio 20261217100200, coach_phase 20261220100000, coach_minimal_removed 20261227100200.

-- ---------------------------------------------------------------------------
-- (1) unresolved_severe_discomforts. T-194 extends it with the graded-return step 3 ("range without discomfort"
-- confirmed); today an exercise is unresolved while a level 4-5 discomfort is open.
-- ---------------------------------------------------------------------------
create function public.unresolved_severe_discomforts(p_user uuid)
returns setof uuid
language sql
stable
security invoker
set search_path = ''
as $$
  select distinct d.exercise_id
  from public.exercise_discomforts d
  where p_user is not null
    and p_user = (select auth.uid())
    and d.user_id = p_user
    and d.level >= 4
    and d.resolved_at is null
$$;

revoke execute on function public.unresolved_severe_discomforts(uuid) from public, anon;
grant execute on function public.unresolved_severe_discomforts(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- (2) coach_block_model_input
-- ---------------------------------------------------------------------------
create function public.coach_block_model_input(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_from date := public.coach_week_start(p_today) - 49;
  v_out  jsonb;
begin
  with
  sets as (
    select public.coach_week_start((s.started_at at time zone 'UTC')::date) as ws, s.id as sid, st.completed_at,
           st.weight_kg, st.reps, st.rpe, st.set_type, e.id as eid, coalesce(e.key, e.id::text) as xid
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and st.set_type <> 'warm_up'
      and e.movement_pattern is distinct from 'cardio'
      and (s.started_at at time zone 'UTC')::date between v_from and p_today
  ),
  weekly as (
    select xid, ws, max(public.coach_e1rm(weight_kg, reps)) as e1rm from sets group by xid, ws
  ),
  hist as (
    select xid,
           count(distinct sid) as sessions,
           count(*) as working_sets,
           round(avg(rpe), 1) as mean_rpe
    from sets group by xid
    order by count(distinct sid) desc, xid
    limit 80
  ),
  lastset as (
    select distinct on (xid) xid, weight_kg, reps
    from sets order by xid, completed_at desc
  ),
  folders as (
    select f.id, f.name, f.created_at
    from public.routine_folders f
    where f.user_id = v_uid
    order by f.created_at desc, f.id
    limit 12
  ),
  blocks as (
    select p.id, p.block_start, p.phase, p.intention, p.training
    from public.coach_plans p
    where p.user_id = v_uid and p.status in ('superseded', 'accepted')
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
    order by p.block_start desc, p.created_at desc
    limit 3
  )
  select jsonb_build_object(
    'generated_for', p_today,
    'folders', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', f.id, 'name', f.name,
        'routines', coalesce((
          select jsonb_agg(jsonb_build_object(
            'name', r.name,
            'exercises', coalesce((
              select jsonb_agg(jsonb_build_object(
                'exercise', coalesce(x.key, x.id::text),
                'sets', re.target_sets,
                'rep_min', coalesce(re.rep_min, re.target_reps),
                'rep_max', coalesce(re.rep_max, re.target_reps_max, re.target_reps),
                'target_rpe', re.target_rpe) order by re.position)
              from public.routine_exercises re
              join public.exercises x on x.id = re.exercise_id
              where re.routine_id = r.id), '[]'::jsonb)) order by r.position, r.name)
          from public.routines r
          where r.folder_id = f.id and r.user_id = v_uid and not r.archived), '[]'::jsonb)) order by f.created_at desc, f.id)
      from folders f), '[]'::jsonb),
    'history', coalesce((
      select jsonb_agg(jsonb_build_object(
        'exercise', h.xid,
        'sessions', h.sessions,
        'working_sets', h.working_sets,
        'e1rm_first_week', (select w.e1rm from weekly w where w.xid = h.xid order by w.ws limit 1),
        'e1rm_last_week', (select w.e1rm from weekly w where w.xid = h.xid order by w.ws desc limit 1),
        'mean_rpe', h.mean_rpe,
        'last_weight_kg', l.weight_kg,
        'last_reps', l.reps) order by h.sessions desc, h.xid)
      from hist h join lastset l on l.xid = h.xid), '[]'::jsonb),
    'previous_blocks', coalesce((
      select jsonb_agg(jsonb_build_object(
        'block_start', b.block_start,
        'phase', b.phase,
        'intention', b.intention,
        'days_per_week', b.training->'days_per_week',
        'planned_sessions', (select count(*) from public.coach_plan_sessions s where s.plan_id = b.id),
        'completed_sessions', (select count(*) from public.coach_plan_sessions s
                               where s.plan_id = b.id and s.session_id is not null),
        'adherence_pct', (select round(count(*) filter (where s.session_id is not null) * 100.0 / count(*))::int
                          from public.coach_plan_sessions s where s.plan_id = b.id),
        'exercises', coalesce((
          select jsonb_agg(distinct coalesce(x.key, x.id::text))
          from public.coach_plan_sessions s
          cross join lateral jsonb_array_elements(s.exercises) e(value)
          join public.exercises x on x.id = (e.value->>'exercise_id')::uuid
          where s.plan_id = b.id and s.week = 1 and e.value->>'role' <> 'cardio'), '[]'::jsonb))
        order by b.block_start desc)
      from blocks b), '[]'::jsonb),
    'favourites', coalesce((
      select jsonb_agg(coalesce(x.key, x.id::text) order by coalesce(x.key, x.id::text))
      from public.exercise_favorites fv
      join public.exercises x on x.id = fv.exercise_id
      where fv.user_id = v_uid), '[]'::jsonb),
    'unresolved_exercise_ids', coalesce((
      select jsonb_agg(u.id order by u.id)
      from public.unresolved_severe_discomforts(v_uid) u(id)), '[]'::jsonb)
  ) into v_out;

  return v_out;
end;
$$;

revoke execute on function public.coach_block_model_input(date) from public, anon;
grant execute on function public.coach_block_model_input(date) to authenticated;

-- ---------------------------------------------------------------------------
-- (3) H4 rep-range bounds: 6-20 on any exercise; "Ganar fuerza" main lifts 3-8. Without an intention (blocks stored
-- before this task) only the structural 1-50 applies.
-- ---------------------------------------------------------------------------
create function public.coach_block_rep_ok(p_intention text, p_main boolean, p_min integer, p_max integer)
returns boolean
language sql
immutable
security invoker
set search_path = ''
as $$
  select case
    when p_min is null or p_max is null or p_min > p_max then false
    when p_intention is null then p_min between 1 and 50 and p_max between 1 and 50
    when p_intention = 'strength' and p_main then p_min >= 3 and p_max <= 8
    else p_min >= 6 and p_max <= 20
  end
$$;

revoke execute on function public.coach_block_rep_ok(text, boolean, integer, integer) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- (4) coach_assert_block
-- ---------------------------------------------------------------------------
create or replace function public.coach_assert_block(
  p_uid uuid,
  p_training jsonb,
  p_sessions jsonb
)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_days      integer;
  v_equipment text[];
  v_session   record;
  v_ex        record;
  v_set       record;
  v_row       record;
  v_role      text;
  v_rest      integer;
  v_sets      integer;
  v_rmin      integer;
  v_rmax      integer;
  v_reason    text;
  v_seen_iso  boolean;
  v_ids       uuid[];
  v_id        uuid;
  v_cardio    jsonb := p_training->'cardio';
  v_target    integer;
  v_units     integer;
  v_planned   integer;
  v_per       integer[] := array[]::integer[];
  v_totals    integer[] := array[]::integer[];
  v_smin      integer;
  v_dur       integer;
  v_seen_card boolean;
  v_lifting   integer;
  v_capunits  integer := 12;
  v_intention text := p_training->>'intention';
  v_main      boolean;
  v_unres     uuid[];
  v_drops     integer;
  v_ss        jsonb;
  v_group     text;
begin
  if p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array' then
    raise exception 'block invariant: shape' using errcode = '22023';
  end if;

  if p_training ? 'phase' then
    if p_training->>'phase' is null
       or p_training->>'phase' not in ('cut', 'gain', 'maintenance', 'recomposition') then
      raise exception 'block invariant: phase' using errcode = '22023';
    end if;
  end if;
  if v_intention is not null
     and v_intention not in ('fat_loss', 'muscle', 'recomposition', 'maintenance', 'strength') then
    raise exception 'block invariant: intention' using errcode = '22023';
  end if;

  select coalesce(array_agg(x.id), '{}') into v_unres from public.unresolved_severe_discomforts(p_uid) x(id);

  begin
    v_days := (p_training->>'days_per_week')::numeric::integer;
    if jsonb_typeof(coalesce(p_training->'equipment', '[]'::jsonb)) <> 'array' then
      raise exception 'block invariant: equipment_shape' using errcode = '22023';
    end if;
    select coalesce(array_agg(e.value), '{}') into v_equipment
    from jsonb_array_elements_text(coalesce(p_training->'equipment', '[]'::jsonb)) e(value);
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception 'block invariant: malformed_training' using errcode = '22023';
  end;

  if jsonb_typeof(p_training->'block_weeks') is distinct from 'number'
     or (p_training->>'block_weeks')::numeric not between 1 and 4
     or (p_training->>'block_weeks')::numeric <> trunc((p_training->>'block_weeks')::numeric) then
    raise exception 'block invariant: block_weeks' using errcode = '22023';
  end if;
  if v_days is null or v_days not between 1 and 7 or jsonb_array_length(p_sessions) <> v_days then
    raise exception 'block invariant: h6_sessions' using errcode = '22023';
  end if;

  -- Weekly cardio plan: the stated split must equal the one derived from the target (units of 5 min, at most
  -- 60 min per routine, remainder to the first routines, the rest is "por tu cuenta").
  if v_cardio is not null then
    begin
      if jsonb_typeof(v_cardio) <> 'object'
         or jsonb_typeof(v_cardio->'target_min_week') is distinct from 'number'
         or jsonb_typeof(v_cardio->'per_routine_min') is distinct from 'array'
         or jsonb_typeof(p_training->'session_minutes') is distinct from 'number'
         or jsonb_typeof(p_training->'routine_total_min') is distinct from 'array' then
        raise exception 'block invariant: cardio_shape' using errcode = '22023';
      end if;
      v_target := (v_cardio->>'target_min_week')::numeric::integer;
      v_smin := (p_training->>'session_minutes')::numeric::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'block invariant: cardio_shape' using errcode = '22023';
    end;
    if v_target not between 0 and 600 then
      raise exception 'block invariant: cardio_total' using errcode = '22023';
    end if;
    v_units := v_target / 5;
    v_planned := 5 * least(v_units, v_capunits * v_days);
    for v_i in 1..v_days loop
      v_per := v_per || (5 * ((least(v_units, v_capunits * v_days) / v_days)
                              + case when v_i <= least(v_units, v_capunits * v_days) % v_days then 1 else 0 end));
      v_totals := v_totals || (v_smin + v_per[v_i]);
    end loop;
    if v_cardio->'planned_min_week' is distinct from to_jsonb(v_planned)
       or v_cardio->'on_your_own_min' is distinct from to_jsonb(v_target - v_planned)
       or v_cardio->'per_routine_min' is distinct from to_jsonb(v_per)
       or p_training->'routine_total_min' is distinct from to_jsonb(v_totals) then
      raise exception 'block invariant: cardio_total' using errcode = '22023';
    end if;
    if v_cardio->>'pace' is distinct from 'suave' then
      raise exception 'block invariant: cardio_pace' using errcode = '22023';
    end if;
  end if;

  for v_session in
    select s.value, s.ordinality from jsonb_array_elements(p_sessions) with ordinality s(value, ordinality)
  loop
    if jsonb_typeof(v_session.value) <> 'object'
       or jsonb_typeof(v_session.value->'name') is distinct from 'string'
       or char_length(btrim(v_session.value->>'name')) not between 1 and 100
       or jsonb_typeof(coalesce(v_session.value->'focus', '[]'::jsonb)) <> 'array'
       or jsonb_typeof(v_session.value->'exercises') is distinct from 'array'
       or jsonb_array_length(v_session.value->'exercises') = 0 then
      raise exception 'block invariant: session_shape' using errcode = '22023';
    end if;

    v_seen_iso := false;
    v_seen_card := false;
    v_lifting := 0;
    v_ids := '{}';
    v_ss := '{}'::jsonb;
    for v_ex in
      select e.value from jsonb_array_elements(v_session.value->'exercises') with ordinality e(value, ordinality)
      order by e.ordinality
    loop
      if jsonb_typeof(v_ex.value) <> 'object' then
        raise exception 'block invariant: exercise_shape' using errcode = '22023';
      end if;
      -- No warm-up anywhere in a block.
      if lower(coalesce(v_ex.value->>'role', '') || ' ' || coalesce(v_ex.value->>'exercise_key', '')) ~ 'warm[-_ ]?up' then
        raise exception 'block invariant: warmup' using errcode = '22023';
      end if;

      if v_ex.value->>'role' = 'cardio' then
        -- Cardio: last, only one, planned minutes of the routine, no lifting fields.
        if v_seen_card then
          raise exception 'block invariant: cardio_count' using errcode = '22023';
        end if;
        v_seen_card := true;
        if v_cardio is null or coalesce(v_per[v_session.ordinality::integer], 0) = 0 then
          raise exception 'block invariant: cardio_total' using errcode = '22023';
        end if;
        if v_ex.value ?| array['rep_min', 'rep_max', 'rest_seconds', 'rpe', 'target_rpe', 'reps', 'superset_group',
                               'planned_weight_kg'] then
          raise exception 'block invariant: cardio_fields' using errcode = '22023';
        end if;
        begin
          v_id := (v_ex.value->>'exercise_id')::uuid;
          v_sets := (v_ex.value->>'sets')::numeric::integer;
          v_dur := (v_ex.value->>'duration_min')::numeric::integer;
          if (v_ex.value->>'duration_min')::numeric <> v_dur then
            raise exception 'block invariant: cardio_minutes' using errcode = '22023';
          end if;
        exception when invalid_text_representation or numeric_value_out_of_range then
          raise exception 'block invariant: malformed_exercise' using errcode = '22023';
        end;
        if v_id is null or v_sets is null or v_dur is null
           or jsonb_typeof(v_ex.value->'reason') is distinct from 'string' then
          raise exception 'block invariant: missing_field' using errcode = '22023';
        end if;
        if not exists (select 1 from public.exercises e
                       where e.id = v_id and (not e.is_custom or e.created_by = p_uid)) then
          raise exception 'block invariant: unknown_exercise' using errcode = '22023';
        end if;
        if not exists (select 1 from public.coach_cardio_options(v_equipment) o where o.exercise_id = v_id) then
          raise exception 'block invariant: cardio_equipment' using errcode = '22023';
        end if;
        if v_id = any (v_ids) then
          raise exception 'block invariant: duplicate_exercise' using errcode = '22023';
        end if;
        v_ids := v_ids || v_id;
        if v_sets <> 1 then
          raise exception 'block invariant: h6_sets_per_exercise' using errcode = '22023';
        end if;
        if v_dur not between 5 and 60 or v_dur % 5 <> 0 or v_dur <> v_per[v_session.ordinality::integer]
           or v_ex.value->>'pace' is distinct from 'suave' then
          raise exception 'block invariant: cardio_minutes' using errcode = '22023';
        end if;
        v_reason := btrim(v_ex.value->>'reason');
        if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
          raise exception 'block invariant: reason' using errcode = '22023';
        end if;
        continue;
      end if;

      if v_seen_card then
        raise exception 'block invariant: cardio_order' using errcode = '22023';
      end if;

      begin
        v_id := (v_ex.value->>'exercise_id')::uuid;
        v_role := v_ex.value->>'role';
        v_rest := (v_ex.value->>'rest_seconds')::numeric::integer;
        v_rmin := (v_ex.value->>'rep_min')::numeric::integer;
        v_rmax := (v_ex.value->>'rep_max')::numeric::integer;
        if jsonb_typeof(v_ex.value->'sets') = 'array' then
          v_sets := jsonb_array_length(v_ex.value->'sets');
        else
          v_sets := (v_ex.value->>'sets')::numeric::integer;
        end if;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'block invariant: malformed_exercise' using errcode = '22023';
      end;
      if v_id is null or v_role is null or v_rest is null or v_sets is null or v_rmin is null or v_rmax is null
         or jsonb_typeof(v_ex.value->'reason') is distinct from 'string' then
        raise exception 'block invariant: missing_field' using errcode = '22023';
      end if;

      -- Visible to the caller: a catalogue row or one of their own custom exercises.
      select e.equipment::text as equipment, e.movement_pattern::text as pattern, e.muscle_group::text as muscle into v_row
      from public.exercises e
      where e.id = v_id and (not e.is_custom or e.created_by = p_uid);
      if not found then
        raise exception 'block invariant: unknown_exercise' using errcode = '22023';
      end if;
      if cardinality(v_equipment) > 0 and not (v_row.equipment = any (v_equipment)) then
        raise exception 'block invariant: h6_equipment' using errcode = '22023';
      end if;
      if v_id = any (v_unres) then
        raise exception 'block invariant: h6_excluded_discomfort' using errcode = '22023';
      end if;
      if v_id = any (v_ids) then
        raise exception 'block invariant: duplicate_exercise' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;

      v_main := v_role = 'main';
      if v_role = 'main' then
        -- Order is a hard rule only for "Ganar fuerza" (H7); blocks stored before the intention was carried keep
        -- the older main-first rule.
        if v_seen_iso and (v_intention is null or v_intention = 'strength') then
          raise exception 'block invariant: h7_order' using errcode = '22023';
        end if;
        if v_row.pattern is null or v_row.pattern not in
           ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 120 and 180 then
          raise exception 'block invariant: h6_rest' using errcode = '22023';
        end if;
      elsif v_role = 'isolation' then
        v_seen_iso := true;
        if v_row.pattern is null or v_row.pattern in
           ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull', 'cardio') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 60 and 120 then
          raise exception 'block invariant: h6_rest' using errcode = '22023';
        end if;
      else
        raise exception 'block invariant: role' using errcode = '22023';
      end if;

      if v_sets not between 1 and 10 then
        raise exception 'block invariant: h6_sets_per_exercise' using errcode = '22023';
      end if;
      if not public.coach_block_rep_ok(v_intention, v_main, v_rmin, v_rmax) then
        raise exception 'block invariant: h4_rep_range' using errcode = '22023';
      end if;

      -- Target RPE: a whole number from 7 to 9 (H5).
      if v_ex.value ? 'target_rpe'
         and (jsonb_typeof(v_ex.value->'target_rpe') is distinct from 'number'
              or (v_ex.value->>'target_rpe')::numeric not between 7 and 9
              or (v_ex.value->>'target_rpe')::numeric <> trunc((v_ex.value->>'target_rpe')::numeric)) then
        raise exception 'block invariant: h5_rpe' using errcode = '22023';
      end if;

      -- Sets: working or drop only (no warm-up, no failure). A drop set is the single last set of an isolation
      -- exercise.
      if jsonb_typeof(v_ex.value->'sets') = 'array' then
        v_drops := 0;
        for v_set in
          select s.value, s.ordinality from jsonb_array_elements(v_ex.value->'sets') with ordinality s(value, ordinality)
        loop
          if jsonb_typeof(v_set.value) <> 'object'
             or coalesce(v_set.value->>'type', 'working') not in ('working', 'drop') then
            raise exception 'block invariant: h5_set_type' using errcode = '22023';
          end if;
          if v_set.value ? 'rpe'
             and (jsonb_typeof(v_set.value->'rpe') is distinct from 'number'
                  or (v_set.value->>'rpe')::numeric not between 7 and 9
                  or (v_set.value->>'rpe')::numeric <> trunc((v_set.value->>'rpe')::numeric)) then
            raise exception 'block invariant: h5_rpe' using errcode = '22023';
          end if;
          if v_set.value ? 'rep_min' or v_set.value ? 'rep_max' then
            if jsonb_typeof(v_set.value->'rep_min') is distinct from 'number'
               or jsonb_typeof(v_set.value->'rep_max') is distinct from 'number'
               or not public.coach_block_rep_ok(v_intention, v_main, (v_set.value->>'rep_min')::numeric::integer,
                                                (v_set.value->>'rep_max')::numeric::integer) then
              raise exception 'block invariant: h4_rep_range' using errcode = '22023';
            end if;
          end if;
          if v_set.value ? 'rest_seconds'
             and (jsonb_typeof(v_set.value->'rest_seconds') is distinct from 'number'
                  or (v_set.value->>'rest_seconds')::numeric not between case when v_main then 120 else 60 end
                                                                      and case when v_main then 180 else 120 end) then
            raise exception 'block invariant: h6_rest' using errcode = '22023';
          end if;
          if v_set.value->>'type' = 'drop' then
            v_drops := v_drops + 1;
            if v_drops > 1 or v_set.ordinality <> v_sets or v_main
               or v_row.pattern not in ('isolation', 'shoulder_abduction') then
              raise exception 'block invariant: drop_invalid' using errcode = '22023';
            end if;
          end if;
        end loop;
      end if;

      if v_ex.value ? 'planned_weight_kg'
         and (jsonb_typeof(v_ex.value->'planned_weight_kg') is distinct from 'number'
              or (v_ex.value->>'planned_weight_kg')::numeric <= 0
              or (v_ex.value->>'planned_weight_kg')::numeric > 1000) then
        raise exception 'block invariant: planned_weight' using errcode = '22023';
      end if;
      if v_ex.value ? 'technique_note'
         and (jsonb_typeof(v_ex.value->'technique_note') is distinct from 'string'
              or char_length(btrim(v_ex.value->>'technique_note')) not between 1 and 200) then
        raise exception 'block invariant: technique_note' using errcode = '22023';
      end if;

      -- Superset groups: collected per session and judged below.
      if v_ex.value ? 'superset_group' then
        if jsonb_typeof(v_ex.value->'superset_group') is distinct from 'number'
           or (v_ex.value->>'superset_group')::numeric <> trunc((v_ex.value->>'superset_group')::numeric)
           or (v_ex.value->>'superset_group')::numeric not between 1 and 20 then
          raise exception 'block invariant: superset_invalid' using errcode = '22023';
        end if;
        v_group := (v_ex.value->>'superset_group')::numeric::integer::text;
        v_ss := jsonb_set(v_ss, array[v_group],
                  coalesce(v_ss->v_group, '[]'::jsonb)
                  || jsonb_build_array(jsonb_build_object('muscle', v_row.muscle, 'main', v_main, 'sets', v_sets)));
      end if;

      v_reason := btrim(v_ex.value->>'reason');
      if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
        raise exception 'block invariant: reason' using errcode = '22023';
      end if;
      v_lifting := v_lifting + 1;
    end loop;

    for v_group in select k from jsonb_object_keys(v_ss) k loop
      if jsonb_array_length(v_ss->v_group) <> 2
         or (v_ss->v_group->0->>'muscle') = (v_ss->v_group->1->>'muscle')
         or (v_intention = 'strength' and ((v_ss->v_group->0->>'main')::boolean or (v_ss->v_group->1->>'main')::boolean)) then
        raise exception 'block invariant: superset_invalid' using errcode = '22023';
      end if;
      if (v_ss->v_group->0->>'sets') <> (v_ss->v_group->1->>'sets') then
        raise exception 'block invariant: superset_sets_mismatch' using errcode = '22023';
      end if;
    end loop;

    if v_lifting = 0 then
      raise exception 'block invariant: empty_session' using errcode = '22023';
    end if;
    -- A routine with cardio minutes must carry its cardio exercise (no minutes, no exercise).
    if v_cardio is not null and coalesce(v_per[v_session.ordinality::integer], 0) > 0 and not v_seen_card then
      raise exception 'block invariant: cardio_total' using errcode = '22023';
    end if;
  end loop;
end;
$$;

revoke execute on function public.coach_assert_block(uuid, jsonb, jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- (5) save_coach_block: as 20261217100200; the exercise keeps its target RPE, superset group, per-set types (drop)
-- and planned weight, and a technique note replaces the reason as the note. Edits (replacements) change the
-- exercise only, so sets, range, RPE and rest stay (coach_apply_block_edits is unchanged).
-- ---------------------------------------------------------------------------
create or replace function public.save_coach_block(p_plan_id uuid, p_edits jsonb default '[]'::jsonb)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_plan    public.coach_plans;
  v_folder  uuid;
  v_session record;
  v_name    text;
  v_n       integer;
  v_rid     uuid;
  v_rids    uuid[] := array[]::uuid[];
  v_payload jsonb;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  -- Serialises concurrent confirmations of the same plan.
  perform pg_advisory_xact_lock(hashtextextended(p_plan_id::text, 0));

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;

  if v_plan.folder_id is not null
     or exists (select 1 from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.routine_id is not null) then
    return jsonb_build_object(
      'folder_id', v_plan.folder_id,
      'routine_ids', coalesce((select jsonb_agg(s.routine_id order by s.session_index)
                               from public.coach_plan_sessions s
                               where s.plan_id = p_plan_id and s.week = 1 and s.routine_id is not null), '[]'::jsonb));
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;
  if v_plan.training is null or not exists (select 1 from public.coach_plan_sessions s where s.plan_id = p_plan_id) then
    raise exception 'plan has no block' using errcode = '55000';
  end if;

  perform set_config('app.coach_block_save', 'on', true);
  perform public.coach_apply_block_edits(p_plan_id, coalesce(p_edits, '[]'::jsonb));
  perform set_config('app.coach_block_save', 'off', true);

  v_folder := public.create_routine_folder('Bloque ' || to_char(v_plan.block_start, 'DD/MM/YYYY'));

  for v_session in
    select s.name, s.exercises from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.week = 1 order by s.session_index
  loop
    -- Routine names are unique per user: add a number when the name is taken.
    v_name := v_session.name;
    v_n := 1;
    while exists (select 1 from public.routines r
                  where r.user_id = v_uid and not r.archived and lower(btrim(r.name)) = lower(btrim(v_name))) loop
      v_n := v_n + 1;
      v_name := left(v_session.name, 90) || ' ' || v_n;
    end loop;

    select jsonb_build_object(
      'name', v_name,
      'track_as_routine', true,
      'exercises', coalesce(jsonb_agg(x.obj order by x.pos), '[]'::jsonb))
    into v_payload
    from (
      select e.ordinality as pos,
             case when e.value->>'role' = 'cardio' then
               jsonb_build_object(
                 'exercise_id', e.value->>'exercise_id',
                 'rest_seconds', 0,
                 'notes', btrim(e.value->>'reason'),
                 'note_kind', 'other',
                 'sets', jsonb_build_array(jsonb_build_object(
                           'duration_s', (e.value->>'duration_min')::int * 60, 'set_type', 'working')))
             else
               jsonb_build_object(
                 'exercise_id', e.value->>'exercise_id',
                 'rest_seconds', (e.value->>'rest_seconds')::int,
                 'rep_min', (e.value->>'rep_min')::int,
                 'rep_max', (e.value->>'rep_max')::int,
                 'target_rpe', e.value->'target_rpe',
                 'superset_group', e.value->'superset_group',
                 'notes', case when nullif(btrim(e.value->>'technique_note'), '') is not null
                               then btrim(e.value->>'technique_note') else btrim(e.value->>'reason') end,
                 'note_kind', case when nullif(btrim(e.value->>'technique_note'), '') is not null
                                   then 'technique' else 'other' end,
                 -- One set per planned set, at the bottom of the rep range; a drop set has no planned weight.
                 'sets', case
                   when jsonb_typeof(e.value->'sets') = 'array' then
                     (select jsonb_agg(jsonb_build_object(
                               'reps', coalesce((s.value->>'rep_min')::int, (e.value->>'rep_min')::int),
                               'set_type', coalesce(s.value->>'type', 'working'),
                               'weight_kg', case when coalesce(s.value->>'type', 'working') = 'working'
                                                 then e.value->'planned_weight_kg' else null end)
                             order by s.ordinality)
                      from jsonb_array_elements(e.value->'sets') with ordinality s(value, ordinality))
                   else
                     to_jsonb(array_fill(
                       jsonb_build_object('reps', (e.value->>'rep_min')::int, 'set_type', 'working',
                                          'weight_kg', e.value->'planned_weight_kg'),
                       array[(e.value->>'sets')::int]))
                 end)
             end as obj
      from jsonb_array_elements(v_session.exercises) with ordinality e(value, ordinality)
    ) x;

    v_rid := public.save_routine(v_payload);
    perform public.move_routine_to_folder(v_rid, v_folder);
    v_rids := v_rids || v_rid;
  end loop;

  perform set_config('app.coach_block_save', 'on', true);
  perform public.coach_link_block(p_plan_id, v_folder, v_rids);
  perform set_config('app.coach_block_save', 'off', true);

  return jsonb_build_object('folder_id', v_folder, 'routine_ids', to_jsonb(v_rids));
end;
$$;

revoke execute on function public.save_coach_block(uuid, jsonb) from public, anon;
grant execute on function public.save_coach_block(uuid, jsonb) to authenticated;
