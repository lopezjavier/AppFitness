-- T-182 (US-72, ADR-85): the coach block gains one cardio exercise, last in every routine whose share of the weekly
-- cardio minutes is above zero. Rules: planning/04 section 9 and 07 section 3 "Cardio prescription".
-- Cardio exercise shape: {exercise_id, exercise_key, role:'cardio', sets:1, duration_min, pace:'suave', reason};
-- it has NO rep range, rest or RPE. training.cardio = {target_min_week, planned_min_week, on_your_own_min,
-- per_routine_min[], pace}; training.routine_total_min[i] = session_minutes + per_routine_min[i].
-- A block without training.cardio (stored before this task) is valid and has no cardio exercise.
-- Bases: coach_assert_block 20261210100000, coach_apply_block_edits and save_coach_block 20261213100400,
-- exercise_alternatives 20261213100200.

-- ---------------------------------------------------------------------------
-- coach_cardio_options: the cardio exercises the coach may plan or swap to, for a list of selected equipment.
-- Continuous "suave" cardio only (Q28): cardio pattern with a duration metric, minus the non-continuous moves.
-- walking and running are always allowed; an empty list means no restriction (the caller resolves "home with
-- nothing selected" before calling). Visible exercises only: catalogue rows and the caller's own custom rows.
-- ---------------------------------------------------------------------------
create function public.coach_cardio_options(p_equipment text[])
returns table (exercise_id uuid, key text)
language sql
stable
security invoker
set search_path = ''
as $$
  select e.id, e.key
  from public.exercises e
  where e.movement_pattern = 'cardio'
    and e.metric_type in ('duration', 'distance_duration')
    and coalesce(e.key, '') not in ('jumping-jacks', 'jump-rope', 'battle-ropes', 'mountain-climbers')
    and (not e.is_custom or e.created_by = (select auth.uid()))
    and (e.key in ('walking', 'running')
         or coalesce(cardinality(p_equipment), 0) = 0
         or e.equipment::text = any (p_equipment))
$$;

revoke execute on function public.coach_cardio_options(text[]) from public, anon;
grant execute on function public.coach_cardio_options(text[]) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_assert_block: the server-side invariants of a block. Raises 22023.
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
begin
  if p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array' then
    raise exception 'block invariant: shape' using errcode = '22023';
  end if;

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
    raise exception 'block invariant: session_count' using errcode = '22023';
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
    v_planned := 5 * least(v_units, 12 * v_days);
    for v_i in 1..v_days loop
      v_per := v_per || (5 * ((least(v_units, 12 * v_days) / v_days)
                              + case when v_i <= least(v_units, 12 * v_days) % v_days then 1 else 0 end));
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
        if v_ex.value ?| array['rep_min', 'rep_max', 'rest_seconds', 'rpe', 'target_rpe', 'reps'] then
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
          raise exception 'block invariant: sets' using errcode = '22023';
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
        v_sets := (v_ex.value->>'sets')::numeric::integer;
        v_rmin := (v_ex.value->>'rep_min')::numeric::integer;
        v_rmax := (v_ex.value->>'rep_max')::numeric::integer;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'block invariant: malformed_exercise' using errcode = '22023';
      end;
      if v_id is null or v_role is null or v_rest is null or v_sets is null or v_rmin is null or v_rmax is null
         or jsonb_typeof(v_ex.value->'reason') is distinct from 'string' then
        raise exception 'block invariant: missing_field' using errcode = '22023';
      end if;

      -- Visible to the caller: a catalogue row or one of their own custom exercises.
      select e.equipment::text as equipment, e.movement_pattern::text as pattern into v_row
      from public.exercises e
      where e.id = v_id and (not e.is_custom or e.created_by = p_uid);
      if not found then
        raise exception 'block invariant: unknown_exercise' using errcode = '22023';
      end if;
      if cardinality(v_equipment) > 0 and not (v_row.equipment = any (v_equipment)) then
        raise exception 'block invariant: equipment' using errcode = '22023';
      end if;
      if v_id = any (v_ids) then
        raise exception 'block invariant: duplicate_exercise' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;

      if v_role = 'main' then
        if v_seen_iso then
          raise exception 'block invariant: order' using errcode = '22023';
        end if;
        if v_row.pattern is null or v_row.pattern not in
           ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 120 and 180 then
          raise exception 'block invariant: rest' using errcode = '22023';
        end if;
      elsif v_role = 'isolation' then
        v_seen_iso := true;
        if v_row.pattern is null or v_row.pattern not in ('isolation', 'core') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 60 and 120 then
          raise exception 'block invariant: rest' using errcode = '22023';
        end if;
      else
        raise exception 'block invariant: role' using errcode = '22023';
      end if;

      if v_sets not between 1 and 10 then
        raise exception 'block invariant: sets' using errcode = '22023';
      end if;
      if v_rmin not between 1 and 50 or v_rmax not between 1 and 50 or v_rmin > v_rmax then
        raise exception 'block invariant: rep_range' using errcode = '22023';
      end if;
      v_reason := btrim(v_ex.value->>'reason');
      if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
        raise exception 'block invariant: reason' using errcode = '22023';
      end if;
      v_lifting := v_lifting + 1;
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
-- exercise_alternatives: a cardio-duration exercise has every eligible cardio exercise as alternative (any to any,
-- same list from every member), within the caller's equipment; the curated pairs are read for the rest.
-- Equipment: the caller's training_preferences; nothing selected at home / elsewhere means bodyweight only.
-- ---------------------------------------------------------------------------
create or replace function public.exercise_alternatives(p_exercise_id uuid)
returns table (exercise_id uuid, similarity numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with me as (
    select 1 as one
    from public.exercises e
    where e.id = p_exercise_id and e.movement_pattern = 'cardio'
      and e.metric_type in ('duration', 'distance_duration')
  ),
  prefs as (
    select case when cardinality(t.equipment) = 0 and t.place in ('home', 'other') then array['bodyweight']
                else t.equipment::text[] end as eq
    from public.training_preferences t
    where t.user_id = (select auth.uid())
  ),
  cardio as (
    select o.exercise_id as id, 1::numeric as similarity, o.key
    from me
    cross join public.coach_cardio_options(coalesce((select p.eq from prefs p), '{}'::text[])) o
    where o.exercise_id <> p_exercise_id
  ),
  pairs as (
    select x.id, x.similarity, e.key
    from (
      select a.alternative_id as id, a.similarity
        from public.exercise_alternatives a where a.exercise_id = p_exercise_id
      union all
      select a.exercise_id as id, a.similarity
        from public.exercise_alternatives a where a.alternative_id = p_exercise_id
    ) x
    join public.exercises e on e.id = x.id
    where not exists (select 1 from me)
  )
  select u.id, u.similarity
  from (select * from cardio union all select * from pairs) u
  order by u.similarity desc, u.key
$$;

revoke execute on function public.exercise_alternatives(uuid) from public, anon;
grant execute on function public.exercise_alternatives(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_apply_block_edits: replacements chosen in the block preview. A cardio slot accepts any exercise of
-- coach_cardio_options for the plan's equipment (minutes, position and reason are kept); a lifting slot refuses a
-- cardio replacement and keeps the curated-pair rule.
-- ---------------------------------------------------------------------------
create or replace function public.coach_apply_block_edits(p_plan_id uuid, p_edits jsonb)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_edit record;
  v_cur  uuid;
  v_exs  jsonb;
  v_dedup jsonb;
  v_equipment text[];
begin
  if coalesce(current_setting('app.coach_block_save', true), '') <> 'on' then
    raise exception 'coach_apply_block_edits is internal' using errcode = '42501';
  end if;
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_edits is null or jsonb_typeof(p_edits) <> 'array' then
    raise exception 'invalid edits' using errcode = '22023';
  end if;
  if jsonb_array_length(p_edits) = 0 then
    return;
  end if;
  select coalesce(array(select jsonb_array_elements_text(
           case when jsonb_typeof(p.training->'equipment') = 'array' then p.training->'equipment' else '[]'::jsonb end)),
         '{}'::text[])
  into v_equipment
  from public.coach_plans p where p.id = p_plan_id and p.user_id = v_uid;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_edits) e(value)
    where jsonb_typeof(e.value) <> 'object'
       or jsonb_typeof(e.value->'session_index') is distinct from 'number'
       or jsonb_typeof(e.value->'position') is distinct from 'number'
       or jsonb_typeof(e.value->'exercise_id') is distinct from 'string'
       or (e.value->>'exercise_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       or (e.value->>'session_index') !~ '^[0-9]{1,3}$'
       or (e.value->>'position') !~ '^[0-9]{1,3}$') then
    raise exception 'invalid edit' using errcode = '22023';
  end if;

  -- Last edit of each slot wins.
  select coalesce(jsonb_agg(jsonb_build_object('s', q.session_index, 'p', q.position, 'x', q.exercise_id)), '[]'::jsonb)
  into v_dedup
  from (
    select distinct on (session_index, position) session_index, position, exercise_id
    from (
      select (e.value->>'session_index')::int as session_index,
             (e.value->>'position')::int as position,
             (e.value->>'exercise_id')::uuid as exercise_id,
             e.ordinality as ord
      from jsonb_array_elements(p_edits) with ordinality e(value, ordinality)
    ) q0
    order by session_index, position, ord desc
  ) q;

  if exists (select 1 from jsonb_array_elements(v_dedup) d(value)
             group by d.value->>'s', d.value->>'x' having count(*) > 1) then
    raise exception 'a replacement is used twice in a session' using errcode = '22023';
  end if;

  -- Validate every edit against the stored (unedited) week 1 before changing anything.
  for v_edit in select (d.value->>'s')::int as session_index, (d.value->>'p')::int as position, (d.value->>'x')::uuid as exercise_id
                from jsonb_array_elements(v_dedup) d(value) loop
    select s.exercises into v_exs from public.coach_plan_sessions s
      where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = 1 and s.session_index = v_edit.session_index;
    if not found or v_edit.position < 1 or v_edit.position > jsonb_array_length(v_exs) then
      raise exception 'edit targets a missing slot' using errcode = '22023';
    end if;
    v_cur := (v_exs->(v_edit.position - 1)->>'exercise_id')::uuid;
    if v_exs->(v_edit.position - 1)->>'role' = 'cardio' then
      if not exists (select 1 from public.coach_cardio_options(v_equipment) o where o.exercise_id = v_edit.exercise_id) then
        raise exception 'replacement is not a cardio option' using errcode = '22023';
      end if;
    else
      if exists (select 1 from public.exercises e where e.id = v_edit.exercise_id and e.movement_pattern = 'cardio') then
        raise exception 'cardio cannot replace a lifting exercise' using errcode = '22023';
      end if;
      if not exists (select 1 from public.exercise_alternatives a
                     where (a.exercise_id = v_cur and a.alternative_id = v_edit.exercise_id)
                        or (a.alternative_id = v_cur and a.exercise_id = v_edit.exercise_id)) then
        raise exception 'replacement is not an alternative' using errcode = '22023';
      end if;
    end if;
    if exists (select 1 from jsonb_array_elements(v_exs) x(value) where (x.value->>'exercise_id')::uuid = v_edit.exercise_id) then
      raise exception 'replacement already in the session' using errcode = '22023';
    end if;
  end loop;

  -- Change only the exercise (id and key) in every week of the session.
  for v_edit in select (d.value->>'s')::int as session_index, (d.value->>'p')::int as position, (d.value->>'x')::uuid as exercise_id
                from jsonb_array_elements(v_dedup) d(value) loop
    update public.coach_plan_sessions s
      set exercises = jsonb_set(
            jsonb_set(s.exercises, array[(v_edit.position - 1)::text, 'exercise_id'], to_jsonb(v_edit.exercise_id::text)),
            array[(v_edit.position - 1)::text, 'exercise_key'],
            to_jsonb((select e.key from public.exercises e where e.id = v_edit.exercise_id)))
      where s.plan_id = p_plan_id and s.user_id = v_uid and s.session_index = v_edit.session_index;
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- save_coach_block: as before; a cardio exercise becomes one working set with its planned duration (minutes x 60),
-- no reps, rest 0 and no rep range.
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
                 'notes', btrim(e.value->>'reason'),
                 'note_kind', 'other',
                 -- One working set per planned set, at the bottom of the rep range.
                 'sets', to_jsonb(array_fill(
                            jsonb_build_object('reps', (e.value->>'rep_min')::int, 'set_type', 'working'),
                            array[(e.value->>'sets')::int])))
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

revoke execute on function public.coach_apply_block_edits(uuid, jsonb) from public, anon;
revoke execute on function public.save_coach_block(uuid, jsonb) from public, anon;
grant execute on function public.coach_apply_block_edits(uuid, jsonb) to authenticated;
grant execute on function public.save_coach_block(uuid, jsonb) to authenticated;
