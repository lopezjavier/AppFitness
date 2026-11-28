-- Superset: cardio rows (always one set) are excluded from the same-set-count rule of save_routine,
-- save_workout_as_routine and update_routine_from_session, so a cardio keeps its group next to multi-set members
-- (save_routine below is the 20261217100000 body with only that check changed).
-- T-332 (US-151): the cardio target of a routine is static (one timed set, edited only in the routine editor).
-- Only the bodies of two RPCs change (same signatures, security invoker); no table, check or RLS change.
-- "Cardio exercise" = exercises.movement_pattern = 'cardio' (holds are timed too but are not cardio).
--  * save_workout_as_routine: a cardio exercise becomes ONE timed set. Target = the planned target of the source routine
--    for that exercise (sum of its target_duration_s, clamped 60-14400); with no source routine or exercise not in it,
--    the time done: round(sum of completed duration_s / 60) whole minutes, clamped 1-240, stored as minutes * 60.
--  * update_routine_from_session: a cardio exercise already in the routine keeps its previous sets exactly as they were
--    (never the performed time); a cardio exercise new to the routine takes the time done like save_workout_as_routine.
--    Holds and strength are unchanged.

create or replace function public.save_workout_as_routine(p_session_id uuid, p_name text)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := (select auth.uid());
  v_name     text := btrim(p_name, E' \t\n\r\f');
  v_rests    jsonb;
  v_src      uuid;
  v_routine  uuid;
  v_ex       record;
  v_set      record;
  v_re_id    uuid;
  v_pos      int := 0;
  v_reps     smallint;
  v_rest     smallint;
  v_group    smallint;
  v_cardio   boolean;
  v_target   int;
  v_done_s   numeric;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  select s.exercise_rests, s.routine_id into v_rests, v_src
  from public.workout_sessions s
  where s.id = p_session_id and s.user_id = v_uid and s.status = 'completed';
  if not found then
    raise exception 'workout not found' using errcode = 'P0002';
  end if;
  if v_name is null or char_length(v_name) not between 1 and 100 then
    raise exception 'invalid name' using errcode = '22023';
  end if;
  if not exists (select 1 from public.workout_sets ws
                 where ws.session_id = p_session_id and ws.completed_at is not null) then
    raise exception 'session has no completed sets' using errcode = '22023';
  end if;
  if exists (
    select 1 from public.workout_sets ws
    where ws.session_id = p_session_id and ws.completed_at is not null
    group by ws.exercise_position
    having count(*) > 10
  ) then
    raise exception 'an exercise needs 1 to 10 sets' using errcode = '22023';
  end if;

  insert into public.routines (name) values (v_name) returning id into v_routine;

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
    select coalesce(x.movement_pattern = 'cardio', false) into v_cardio
    from public.exercises x where x.id = v_ex.exercise_id;
    v_cardio := coalesce(v_cardio, false);

    -- Superset members must keep the same set count (ADR-38): otherwise the group is dropped.
    -- Cardio rows (always one set) are excluded from the count and keep their group.
    if v_group is not null and not v_cardio and exists (
      select 1
      from public.workout_sets ws
      join public.exercises x on x.id = ws.exercise_id
      where ws.session_id = p_session_id and ws.completed_at is not null and ws.superset_group = v_group
        and x.movement_pattern is distinct from 'cardio'
      group by ws.exercise_position
      having count(*) <> v_ex.n
    ) then
      v_group := null;
    end if;

    v_rest := coalesce((v_rests ->> v_ex.exercise_position::text)::smallint, 120::smallint);

    if v_cardio then
      -- One timed set. Planned target of the source routine, else the time done in whole minutes (1-240).
      select sum(rs.target_duration_s)::int into v_target
      from public.routine_exercises re
      join public.routine_exercise_sets rs on rs.routine_exercise_id = re.id
      where re.routine_id = v_src and re.exercise_id = v_ex.exercise_id;
      if v_target is not null and v_target > 0 then
        v_target := greatest(60, least(14400, v_target));
      else
        select coalesce(sum(ws.duration_s), 0) into v_done_s
        from public.workout_sets ws
        where ws.session_id = p_session_id and ws.completed_at is not null
          and ws.exercise_position = v_ex.exercise_position;
        v_target := greatest(1, least(240, round(v_done_s / 60)::int)) * 60;
      end if;

      insert into public.routine_exercises
        (routine_id, exercise_id, position, superset_group, target_sets, target_reps, rest_seconds)
      values (v_routine, v_ex.exercise_id, v_pos::smallint, v_group, 1, 1, v_rest)
      returning id into v_re_id;
      v_pos := v_pos + 1;

      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, target_duration_s, set_type)
      values (v_re_id, 1, null, null, v_target, 'working');
      continue;
    end if;

    select greatest(1, least(50, ws.reps))::smallint into v_reps
    from public.workout_sets ws
    where ws.session_id = p_session_id and ws.completed_at is not null
      and ws.exercise_position = v_ex.exercise_position
    order by (ws.set_type <> 'working'), ws.set_index
    limit 1;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, rest_seconds)
    values (v_routine, v_ex.exercise_id, v_pos::smallint, v_group, v_ex.n::smallint, v_reps, v_rest)
    returning id into v_re_id;
    v_pos := v_pos + 1;

    for v_set in
      select ws.weight_kg, ws.reps, ws.set_type,
             row_number() over (order by ws.set_index) as idx
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.completed_at is not null
        and ws.exercise_position = v_ex.exercise_position
      order by ws.set_index
    loop
      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
      values (v_re_id, v_set.idx::smallint, v_set.weight_kg,
              greatest(1, least(50, v_set.reps))::smallint, v_set.set_type);
    end loop;
  end loop;

  return v_routine;
end;
$$;

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

  -- Superset members must agree on the number of sets; cardio rows (one timed set) are excluded from the check.
  if exists (
    select 1
    from jsonb_array_elements(p_routine -> 'exercises') as e(value)
    where e.value ->> 'superset_group' is not null
      and not exists (select 1 from public.exercises x
                      where x.id::text = e.value ->> 'exercise_id' and x.movement_pattern = 'cardio')
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
  v_pset       record;
  v_re_id      uuid;
  v_pos        int := 0;
  v_reps       smallint;
  v_rest       smallint;
  v_rpe        numeric;
  v_group      smallint;
  v_timed      boolean;
  v_cardio     boolean;
  v_keep       boolean;
  v_psets      jsonb;
  v_done_s     numeric;
begin
  select s.routine_id, s.exercise_rests into v_routine_id, v_rests
  from public.workout_sessions s
  where s.id = p_session_id and s.user_id = (select auth.uid()) and s.status = 'completed';
  if not found or v_routine_id is null
     or not exists (select 1 from public.routines r
                    where r.id = v_routine_id and r.user_id = (select auth.uid())) then
    raise exception 'session or routine not found' using errcode = 'P0002';
  end if;

  -- Snapshot of the previous routine exercises (first one per exercise_id wins), with their sets.
  select coalesce(jsonb_object_agg(x.exercise_id::text,
                                   jsonb_build_object('rest', x.rest_seconds, 'rpe', x.target_rpe,
                                                      'notes', x.notes, 'note_kind', x.note_kind,
                                                      'rep_min', x.rep_min, 'rep_max', x.rep_max,
                                                      'timed', x.timed, 'reps', x.target_reps,
                                                      'sets', x.sets)),
                  '{}'::jsonb)
  into v_prev
  from (
    select distinct on (re.exercise_id) re.exercise_id, re.rest_seconds, re.target_rpe, re.notes, re.note_kind,
           re.rep_min, re.rep_max, re.target_reps,
           exists (select 1 from public.routine_exercise_sets rs
                   where rs.routine_exercise_id = re.id and rs.target_duration_s is not null) as timed,
           (select coalesce(jsonb_agg(jsonb_build_object('w', rs.target_weight_kg, 'r', rs.target_reps,
                                                         'd', rs.target_duration_s, 't', rs.set_type)
                                      order by rs.set_index), '[]'::jsonb)
            from public.routine_exercise_sets rs where rs.routine_exercise_id = re.id) as sets
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
    select coalesce(x.movement_pattern = 'cardio', false) into v_cardio
    from public.exercises x where x.id = v_ex.exercise_id;
    v_cardio := coalesce(v_cardio, false);

    -- Superset members must keep the same set count (save_routine invariant, ADR-38): otherwise the group is dropped.
    -- Cardio rows are excluded from the count and keep their group.
    if v_group is not null and not v_cardio and exists (
      select 1
      from public.workout_sets ws
      join public.exercises x on x.id = ws.exercise_id
      where ws.session_id = p_session_id and ws.completed_at is not null and ws.superset_group = v_group
        and x.movement_pattern is distinct from 'cardio'
      group by ws.exercise_position
      having count(*) <> v_ex.n
    ) then
      v_group := null;
    end if;

    v_timed := coalesce((v_prev -> v_ex.exercise_id::text ->> 'timed')::boolean, false);
    -- Cardio already in the routine keeps its previous sets exactly (static target).
    v_keep := v_cardio and (v_prev -> v_ex.exercise_id::text) is not null
              and jsonb_array_length(v_prev -> v_ex.exercise_id::text -> 'sets') > 0;
    v_psets := case when v_keep then v_prev -> v_ex.exercise_id::text -> 'sets' end;

    if v_keep then
      v_reps := coalesce((v_prev -> v_ex.exercise_id::text ->> 'reps')::smallint, 1::smallint);
    elsif v_cardio then
      -- Cardio new to the routine: one timed set with the time done in whole minutes (1-240).
      v_reps := 1;
    elsif v_timed then
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
    values (v_routine_id, v_ex.exercise_id, v_pos::smallint, v_group,
            case when v_keep then jsonb_array_length(v_psets) when v_cardio then 1 else v_ex.n end::smallint,
            v_reps, v_rpe, v_rest,
            v_prev -> v_ex.exercise_id::text ->> 'notes',
            (v_prev -> v_ex.exercise_id::text ->> 'note_kind')::public.exercise_note_kind,
            (v_prev -> v_ex.exercise_id::text ->> 'rep_min')::smallint,
            (v_prev -> v_ex.exercise_id::text ->> 'rep_max')::smallint)
    returning id into v_re_id;
    v_pos := v_pos + 1;

    if v_keep then
      for v_pset in
        select (e.value ->> 'w')::numeric as w, (e.value ->> 'r')::smallint as r, (e.value ->> 'd')::int as d,
               (e.value ->> 't')::public.set_type as t, e.ordinality as idx
        from jsonb_array_elements(v_psets) with ordinality as e(value, ordinality)
        order by e.ordinality
      loop
        insert into public.routine_exercise_sets
          (routine_exercise_id, set_index, target_weight_kg, target_reps, target_duration_s, set_type)
        values (v_re_id, v_pset.idx::smallint, v_pset.w, v_pset.r, v_pset.d, v_pset.t);
      end loop;
      continue;
    elsif v_cardio then
      select coalesce(sum(ws.duration_s), 0) into v_done_s
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.completed_at is not null
        and ws.exercise_position = v_ex.exercise_position;
      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, target_duration_s, set_type)
      values (v_re_id, 1, null, null, greatest(1, least(240, round(v_done_s / 60)::int)) * 60, 'working');
      continue;
    end if;

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

revoke execute on function public.save_workout_as_routine(uuid, text) from public, anon;
grant execute on function public.save_workout_as_routine(uuid, text) to authenticated;
revoke execute on function public.update_routine_from_session(uuid) from public, anon;
grant execute on function public.update_routine_from_session(uuid) to authenticated;
revoke execute on function public.save_routine(jsonb) from public, anon;
grant execute on function public.save_routine(jsonb) to authenticated;
