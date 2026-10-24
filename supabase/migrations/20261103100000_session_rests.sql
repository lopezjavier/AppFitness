-- T-099 (US-36, ADR-54 amendment): rests changed from "Descanso" during a session are stored on the session so
-- they survive closing the app. Map exercise_position -> seconds. A column on an RLS table that already exists:
-- no new table and no new policy.

alter table public.workout_sessions
  add column exercise_rests jsonb not null default '{}'::jsonb
  check (jsonb_typeof(exercise_rests) = 'object');

-- Upserts the rest (0-600 s) of one exercise position of the caller's in-progress session.
create function public.set_session_rest(p_session_id uuid, p_exercise_position int, p_rest_seconds int)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if p_rest_seconds is null or p_rest_seconds not between 0 and 600
     or p_exercise_position is null or p_exercise_position < 0 then
    raise exception 'invalid rest' using errcode = '22023';
  end if;
  update public.workout_sessions
  set exercise_rests = jsonb_set(exercise_rests, array[p_exercise_position::text], to_jsonb(p_rest_seconds), true)
  where id = p_session_id and user_id = (select auth.uid()) and status = 'in_progress';
  if not found then
    raise exception 'session not found' using errcode = 'P0002';
  end if;
end;
$$;

revoke execute on function public.set_session_rest(uuid, int, int) from public, anon;
grant execute on function public.set_session_rest(uuid, int, int) to authenticated;

-- save_routine: same body as T-089 plus the optional flags track_as_routine and auto_update_routine
-- (omitted or null = unchanged on update; the column defaults on create).
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
  v_auto    boolean;
  v_ex      record;
  v_set     record;
  v_re_id   uuid;
  v_count   int;
  v_reps    smallint;
  v_weight  numeric;
  v_type    public.set_type;
begin
  if p_routine is null or jsonb_typeof(p_routine) <> 'object'
     or jsonb_typeof(coalesce(p_routine -> 'exercises', 'null'::jsonb)) <> 'array' then
    raise exception 'invalid routine payload' using errcode = '22023';
  end if;

  begin
    v_track := (p_routine ->> 'track_as_routine')::boolean;
    v_auto := (p_routine ->> 'auto_update_routine')::boolean;
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
    insert into public.routines (name, notes, track_as_routine, auto_update_routine)
    values (v_name, p_routine ->> 'notes', coalesce(v_track, true), coalesce(v_auto, false))
    returning id into v_id;
  else
    v_id := (p_routine ->> 'id')::uuid;
    update public.routines
    set name = v_name,
        notes = p_routine ->> 'notes',
        track_as_routine = coalesce(v_track, track_as_routine),
        auto_update_routine = coalesce(v_auto, auto_update_routine)
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

    -- Legacy summary: first working set's reps, else the first set's.
    select (s.value ->> 'reps')::smallint into v_reps
    from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
    order by (coalesce(s.value ->> 'set_type', 'working') <> 'working'), s.ordinality
    limit 1;
    if v_reps is null or v_reps not between 1 and 50 then
      raise exception 'reps must be between 1 and 50' using errcode = '22023';
    end if;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds)
    values (v_id,
            (v_ex.value ->> 'exercise_id')::uuid,
            (v_ex.ordinality - 1)::smallint,
            (v_ex.value ->> 'superset_group')::smallint,
            v_count,
            v_reps,
            (v_ex.value ->> 'target_rpe')::numeric,
            coalesce((v_ex.value ->> 'rest_seconds')::smallint, 120::smallint))
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
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'invalid set' using errcode = '22023';
      end;
      if v_reps is null or v_reps not between 1 and 50 or coalesce(v_weight, 0) < 0 then
        raise exception 'invalid set' using errcode = '22023';
      end if;
      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
      values (v_re_id, v_set.ordinality::smallint, v_weight, v_reps, v_type);
    end loop;
  end loop;

  return v_id;
end;
$$;
