-- T-182 (US-72, ADR-85): a routine set can plan a duration instead of reps (the cardio exercise of the coach block).
-- Same table, so RLS is unchanged. Exactly one of target_reps / target_duration_s per set; existing rows keep reps.
-- routine_exercises.target_reps (legacy summary) stays not null: a duration-only exercise stores 1.
-- start_workout_session is unchanged (ADR-50: planned values are read from the routine, pending sets carry only type).

alter table public.routine_exercise_sets
  alter column target_reps drop not null,
  add column target_duration_s int null
    constraint routine_exercise_sets_duration_bounds check (target_duration_s between 60 and 14400),
  add constraint routine_exercise_sets_reps_xor_duration check ((target_reps is null) <> (target_duration_s is null));

-- save_routine: same body as 20261206100000_progression.sql plus per-set duration_s (duration-type exercises only).
create or replace function public.save_routine(p_routine jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id      uuid;
  v_name    text := btrim(p_routine ->> 'name', E' \t\n\r\f');
  v_track   boolean;
  v_ex      record;
  v_set     record;
  v_re_id   uuid;
  v_count   int;
  v_reps    smallint;
  v_weight  numeric;
  v_dur     int;
  v_type    public.set_type;
  v_note    text;
  v_kind    public.exercise_note_kind;
  v_rmin    smallint;
  v_rmax    smallint;
  v_metric  public.exercise_metric_type;
begin
  if p_routine is null or jsonb_typeof(p_routine) <> 'object'
     or jsonb_typeof(coalesce(p_routine -> 'exercises', 'null'::jsonb)) <> 'array' then
    raise exception 'invalid routine payload' using errcode = '22023';
  end if;

  begin
    v_track := (p_routine ->> 'track_as_routine')::boolean;
  exception when invalid_text_representation then
    raise exception 'invalid routine flags' using errcode = '22023';
  end;

  -- Superset members must agree on the number of sets.
  if exists (
    select 1
    from jsonb_array_elements(p_routine -> 'exercises') as e(value)
    where e.value ->> 'superset_group' is not null
    group by e.value ->> 'superset_group'
    having count(distinct coalesce(jsonb_array_length(e.value -> 'sets'), -1)) > 1
  ) then
    raise exception 'superset members must have the same number of sets' using errcode = '22023';
  end if;

  if p_routine ->> 'id' is null then
    insert into public.routines (name, notes, track_as_routine)
    values (v_name, p_routine ->> 'notes', coalesce(v_track, true))
    returning id into v_id;
  else
    v_id := (p_routine ->> 'id')::uuid;
    update public.routines
    set name = v_name,
        notes = p_routine ->> 'notes',
        track_as_routine = coalesce(v_track, track_as_routine)
    where id = v_id;
    if not found then
      raise exception 'routine not found' using errcode = 'P0002';
    end if;
    delete from public.routine_exercises where routine_id = v_id;
  end if;

  for v_ex in
    select e.value, e.ordinality
    from jsonb_array_elements(p_routine -> 'exercises') with ordinality as e(value, ordinality)
    order by e.ordinality
  loop
    if jsonb_typeof(coalesce(v_ex.value -> 'sets', 'null'::jsonb)) <> 'array' then
      raise exception 'invalid sets' using errcode = '22023';
    end if;
    v_count := jsonb_array_length(v_ex.value -> 'sets');
    if v_count not between 1 and 10 then
      raise exception 'an exercise needs 1 to 10 sets' using errcode = '22023';
    end if;

    select x.metric_type into v_metric
    from public.exercises x
    where x.id = (v_ex.value ->> 'exercise_id')::uuid;

    -- Legacy summary: first working set's reps, else the first set's with reps, else 1 (duration-only exercise).
    begin
      select (s.value ->> 'reps')::smallint into v_reps
      from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
      where s.value ->> 'reps' is not null
      order by (coalesce(s.value ->> 'set_type', 'working') <> 'working'), s.ordinality
      limit 1;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'reps must be between 1 and 50' using errcode = '22023';
    end;
    if v_reps is not null and v_reps not between 1 and 50 then
      raise exception 'reps must be between 1 and 50' using errcode = '22023';
    end if;
    v_reps := coalesce(v_reps, 1::smallint);

    v_note := nullif(btrim(v_ex.value ->> 'notes', E' \t\n\r\f'), '');
    begin
      v_kind := nullif(v_ex.value ->> 'note_kind', '')::public.exercise_note_kind;
    exception when invalid_text_representation then
      raise exception 'invalid note kind' using errcode = '22023';
    end;
    if v_kind is null and v_note is not null then
      v_kind := 'other';
    end if;

    begin
      v_rmin := (v_ex.value ->> 'rep_min')::smallint;
      v_rmax := (v_ex.value ->> 'rep_max')::smallint;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'invalid rep range' using errcode = '22023';
    end;
    if (v_rmin is null) <> (v_rmax is null)
       or (v_rmin is not null and (v_rmin not between 1 and 50 or v_rmax not between 1 and 50 or v_rmin > v_rmax)) then
      raise exception 'invalid rep range' using errcode = '22023';
    end if;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds,
       notes, note_kind, rep_min, rep_max)
    values (v_id,
            (v_ex.value ->> 'exercise_id')::uuid,
            (v_ex.ordinality - 1)::smallint,
            (v_ex.value ->> 'superset_group')::smallint,
            v_count,
            v_reps,
            (v_ex.value ->> 'target_rpe')::numeric,
            coalesce((v_ex.value ->> 'rest_seconds')::smallint, 120::smallint),
            v_note, v_kind, v_rmin, v_rmax)
    returning id into v_re_id;

    for v_set in
      select s.value, s.ordinality
      from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
      order by s.ordinality
    loop
      begin
        v_type := coalesce(v_set.value ->> 'set_type', 'working')::public.set_type;
        v_weight := (v_set.value ->> 'weight_kg')::numeric;
        v_reps := (v_set.value ->> 'reps')::smallint;
        v_dur := (v_set.value ->> 'duration_s')::int;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'invalid set' using errcode = '22023';
      end;
      if v_dur is not null then
        if v_metric is null or v_metric not in ('duration', 'distance_duration')
           or v_reps is not null or v_weight is not null or v_dur not between 60 and 14400 then
          raise exception 'invalid set' using errcode = '22023';
        end if;
      elsif v_reps is null or v_reps not between 1 and 50 or coalesce(v_weight, 0) < 0 then
        raise exception 'invalid set' using errcode = '22023';
      end if;
      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, target_duration_s, set_type)
      values (v_re_id, v_set.ordinality::smallint, v_weight, v_reps, v_dur, v_type);
    end loop;
  end loop;

  return v_id;
end;
$$;

-- update_routine_from_session: same body as 20261206100000_progression.sql, except that an exercise whose previous
-- routine sets planned a duration keeps planning a duration: target_duration_s = clamp(performed duration, 60, 14400)
-- (null or 0 -> 60), no reps, no weight, legacy summary 1. Free workouts saved as routines keep the reps clamp.
create or replace function public.update_routine_from_session(p_session_id uuid)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_routine_id uuid;
  v_rests      jsonb;
  v_prev       jsonb;
  v_ex         record;
  v_set        record;
  v_re_id      uuid;
  v_pos        int := 0;
  v_reps       smallint;
  v_rest       smallint;
  v_rpe        numeric;
  v_group      smallint;
  v_timed      boolean;
begin
  select s.routine_id, s.exercise_rests into v_routine_id, v_rests
  from public.workout_sessions s
  where s.id = p_session_id and s.user_id = (select auth.uid()) and s.status = 'completed';
  if not found or v_routine_id is null
     or not exists (select 1 from public.routines r
                    where r.id = v_routine_id and r.user_id = (select auth.uid())) then
    raise exception 'session or routine not found' using errcode = 'P0002';
  end if;

  -- Snapshot of the previous routine exercises (first one per exercise_id wins).
  select coalesce(jsonb_object_agg(x.exercise_id::text,
                                   jsonb_build_object('rest', x.rest_seconds, 'rpe', x.target_rpe,
                                                      'notes', x.notes, 'note_kind', x.note_kind,
                                                      'rep_min', x.rep_min, 'rep_max', x.rep_max,
                                                      'timed', x.timed)),
                  '{}'::jsonb)
  into v_prev
  from (
    select distinct on (re.exercise_id) re.exercise_id, re.rest_seconds, re.target_rpe, re.notes, re.note_kind,
           re.rep_min, re.rep_max,
           exists (select 1 from public.routine_exercise_sets rs
                   where rs.routine_exercise_id = re.id and rs.target_duration_s is not null) as timed
    from public.routine_exercises re
    where re.routine_id = v_routine_id
    order by re.exercise_id, re.position
  ) x;

  -- A session with no completed sets would empty the routine: reject it.
  if not exists (select 1 from public.workout_sets ws
                 where ws.session_id = p_session_id and ws.completed_at is not null) then
    raise exception 'session has no completed sets' using errcode = '22023';
  end if;

  -- Sets-per-exercise limit, before touching anything.
  if exists (
    select 1 from public.workout_sets ws
    where ws.session_id = p_session_id and ws.completed_at is not null
    group by ws.exercise_position
    having count(*) > 10
  ) then
    raise exception 'an exercise needs 1 to 10 sets' using errcode = '22023';
  end if;

  delete from public.routine_exercises where routine_id = v_routine_id;

  for v_ex in
    select ws.exercise_position,
           (array_agg(ws.exercise_id order by ws.set_index))[1] as exercise_id,
           (array_agg(ws.superset_group order by ws.set_index))[1] as superset_group,
           count(*) as n
    from public.workout_sets ws
    where ws.session_id = p_session_id and ws.completed_at is not null
    group by ws.exercise_position
    order by ws.exercise_position
  loop
    v_group := v_ex.superset_group;
    -- Superset members must keep the same set count (save_routine invariant, ADR-38): otherwise the group is dropped.
    if v_group is not null and exists (
      select 1
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.completed_at is not null and ws.superset_group = v_group
      group by ws.exercise_position
      having count(*) <> v_ex.n
    ) then
      v_group := null;
    end if;

    v_timed := coalesce((v_prev -> v_ex.exercise_id::text ->> 'timed')::boolean, false);

    if v_timed then
      v_reps := 1;
    else
      -- Legacy summary: first working set's reps, else the first set's.
      select greatest(1, least(50, ws.reps))::smallint into v_reps
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.completed_at is not null
        and ws.exercise_position = v_ex.exercise_position
      order by (ws.set_type <> 'working'), ws.set_index
      limit 1;
    end if;

    v_rest := coalesce((v_rests ->> v_ex.exercise_position::text)::smallint,
                       (v_prev -> v_ex.exercise_id::text ->> 'rest')::smallint,
                       120::smallint);
    v_rpe := (v_prev -> v_ex.exercise_id::text ->> 'rpe')::numeric;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds,
       notes, note_kind, rep_min, rep_max)
    values (v_routine_id, v_ex.exercise_id, v_pos::smallint, v_group, v_ex.n::smallint, v_reps, v_rpe, v_rest,
            v_prev -> v_ex.exercise_id::text ->> 'notes',
            (v_prev -> v_ex.exercise_id::text ->> 'note_kind')::public.exercise_note_kind,
            (v_prev -> v_ex.exercise_id::text ->> 'rep_min')::smallint,
            (v_prev -> v_ex.exercise_id::text ->> 'rep_max')::smallint)
    returning id into v_re_id;
    v_pos := v_pos + 1;

    for v_set in
      select ws.weight_kg, ws.reps, ws.duration_s, ws.set_type,
             row_number() over (order by ws.set_index) as idx
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.completed_at is not null
        and ws.exercise_position = v_ex.exercise_position
      order by ws.set_index
    loop
      if v_timed then
        insert into public.routine_exercise_sets
          (routine_exercise_id, set_index, target_weight_kg, target_reps, target_duration_s, set_type)
        values (v_re_id, v_set.idx::smallint, null, null,
                greatest(60, least(14400, coalesce(v_set.duration_s, 0))), v_set.set_type);
      else
        insert into public.routine_exercise_sets
          (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
        values (v_re_id, v_set.idx::smallint, v_set.weight_kg,
                greatest(1, least(50, v_set.reps))::smallint, v_set.set_type);
      end if;
    end loop;
  end loop;

  return v_routine_id;
end;
$$;

revoke execute on function public.save_routine(jsonb) from public, anon;
grant execute on function public.save_routine(jsonb) to authenticated;
revoke execute on function public.update_routine_from_session(uuid) from public, anon;
grant execute on function public.update_routine_from_session(uuid) to authenticated;
