-- T-153 (US-66): a note on a set / routine exercise carries a kind (an option) and an optional text.
-- "other" needs text; any other kind may omit it. Kinds are never interpreted (ADR-81).
-- Set options and routine options are distinct enums so the DB refuses a set option on a routine note.
-- RLS is unchanged: both columns live on owner-protected tables.

create type public.set_note_kind as enum ('technique', 'range', 'grip', 'assisted', 'other');
create type public.exercise_note_kind as enum ('weight_up', 'weight_down', 'technique', 'setup', 'other');

alter table public.workout_sets add column note_kind public.set_note_kind null;
alter table public.routine_exercises add column note_kind public.exercise_note_kind null;

-- Old free-text notes become "other".
update public.workout_sets set note_kind = 'other' where notes is not null;
update public.routine_exercises set note_kind = 'other' where notes is not null;

alter table public.workout_sets
  add constraint workout_sets_note_kind_text check (
    (notes is null or note_kind is not null) and (note_kind is distinct from 'other' or notes is not null));
alter table public.routine_exercises
  add constraint routine_exercises_note_kind_text check (
    (notes is null or note_kind is not null) and (note_kind is distinct from 'other' or notes is not null));

-- save_routine: same body as 20261130100000_training_notes.sql plus the per-exercise note kind.
-- A note with text but no kind (old clients) is stored as "other".
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
  v_type    public.set_type;
  v_note    text;
  v_kind    public.exercise_note_kind;
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

    -- Legacy summary: first working set's reps, else the first set's.
    select (s.value ->> 'reps')::smallint into v_reps
    from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
    order by (coalesce(s.value ->> 'set_type', 'working') <> 'working'), s.ordinality
    limit 1;
    if v_reps is null or v_reps not between 1 and 50 then
      raise exception 'reps must be between 1 and 50' using errcode = '22023';
    end if;

    v_note := nullif(btrim(v_ex.value ->> 'notes', E' \t\n\r\f'), '');
    begin
      v_kind := nullif(v_ex.value ->> 'note_kind', '')::public.exercise_note_kind;
    exception when invalid_text_representation then
      raise exception 'invalid note kind' using errcode = '22023';
    end;
    if v_kind is null and v_note is not null then
      v_kind := 'other';
    end if;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds,
       notes, note_kind)
    values (v_id,
            (v_ex.value ->> 'exercise_id')::uuid,
            (v_ex.ordinality - 1)::smallint,
            (v_ex.value ->> 'superset_group')::smallint,
            v_count,
            v_reps,
            (v_ex.value ->> 'target_rpe')::numeric,
            coalesce((v_ex.value ->> 'rest_seconds')::smallint, 120::smallint),
            v_note, v_kind)
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

-- save_completed_workout: same body as 20261130100000_training_notes.sql plus the per-set note kind.
create or replace function public.save_completed_workout(p_workout jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := (select auth.uid());
  v_id       uuid;
  v_name     text;
  v_started  timestamptz;
  v_duration int;
  v_ended    timestamptz;
  v_routine  uuid;
  v_rests    jsonb := '{}'::jsonb;
  v_ex       record;
  v_set      record;
  v_count    int;
  v_rest     int;
  v_group    smallint;
  v_type     public.set_type;
  v_rpe      numeric;
  v_weight   numeric;
  v_reps     smallint;
  v_dur      int;
  v_dist     numeric;
  v_steps    int;
  v_floors   int;
  v_note     text;
  v_kind     public.set_note_kind;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_workout is null or jsonb_typeof(p_workout) <> 'object'
     or jsonb_typeof(coalesce(p_workout -> 'exercises', 'null'::jsonb)) <> 'array' then
    raise exception 'invalid workout payload' using errcode = '22023';
  end if;

  v_name := btrim(p_workout ->> 'name', E' \t\n\r\f');
  if v_name is null or char_length(v_name) not between 1 and 100 then
    raise exception 'invalid name' using errcode = '22023';
  end if;
  if jsonb_array_length(p_workout -> 'exercises') not between 1 and 30 then
    raise exception 'a workout needs 1 to 30 exercises' using errcode = '22023';
  end if;

  begin
    v_duration := (p_workout ->> 'duration_s')::int;
    v_started := (p_workout ->> 'started_at')::timestamptz;
    v_routine := (p_workout ->> 'routine_id')::uuid;
    v_id := (p_workout ->> 'id')::uuid;
  exception when invalid_text_representation or numeric_value_out_of_range or datetime_field_overflow
            or invalid_datetime_format then
    raise exception 'invalid workout header' using errcode = '22023';
  end;
  if v_duration is not null and v_duration not between 0 and 86400 then
    raise exception 'invalid duration' using errcode = '22023';
  end if;

  -- Superset members must be consecutive and agree on the number of sets.
  if exists (
    select 1
    from jsonb_array_elements(p_workout -> 'exercises') with ordinality as e(value, ordinality)
    where e.value ->> 'superset_group' is not null
    group by e.value ->> 'superset_group'
    having count(distinct coalesce(jsonb_array_length(e.value -> 'sets'), -1)) > 1
        or max(e.ordinality) - min(e.ordinality) + 1 <> count(*)
  ) then
    raise exception 'superset members must be consecutive and have the same number of sets'
      using errcode = '22023';
  end if;

  if v_id is null then
    if v_started is null or v_started > now() then
      raise exception 'started_at must be present and not in the future' using errcode = '22023';
    end if;
    if v_routine is not null
       and not exists (select 1 from public.routines r where r.id = v_routine and r.user_id = v_uid) then
      raise exception 'routine not found' using errcode = 'P0002';
    end if;
    v_ended := v_started + make_interval(secs => coalesce(v_duration, 0));
  else
    select s.started_at,
           s.started_at + make_interval(secs => coalesce(v_duration,
                                                         extract(epoch from (s.ended_at - s.started_at))::int))
    into v_started, v_ended
    from public.workout_sessions s
    where s.id = v_id and s.user_id = v_uid and s.status = 'completed'
    for update;
    if not found then
      raise exception 'workout not found' using errcode = 'P0002';
    end if;
  end if;

  -- Rests per position, validated before writing anything.
  for v_ex in
    select e.value, e.ordinality
    from jsonb_array_elements(p_workout -> 'exercises') with ordinality as e(value, ordinality)
  loop
    if jsonb_typeof(v_ex.value) <> 'object'
       or jsonb_typeof(coalesce(v_ex.value -> 'sets', 'null'::jsonb)) <> 'array' then
      raise exception 'invalid exercise' using errcode = '22023';
    end if;
    v_count := jsonb_array_length(v_ex.value -> 'sets');
    if v_count not between 1 and 30 then
      raise exception 'an exercise needs 1 to 30 sets' using errcode = '22023';
    end if;
    begin
      v_rest := (v_ex.value ->> 'rest_seconds')::int;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'invalid rest' using errcode = '22023';
    end;
    if v_rest is not null then
      if v_rest not between 0 and 600 then
        raise exception 'rest must be between 0 and 600' using errcode = '22023';
      end if;
      v_rests := v_rests || jsonb_build_object((v_ex.ordinality - 1)::text, v_rest);
    end if;
  end loop;

  if p_workout ->> 'id' is null then
    insert into public.workout_sessions
      (routine_id, name, status, started_at, ended_at, description, gym, exercise_rests)
    values (v_routine, v_name, 'completed', v_started, v_ended,
            nullif(btrim(p_workout ->> 'description', E' \t\n\r\f'), ''),
            nullif(btrim(p_workout ->> 'gym', E' \t\n\r\f'), ''),
            v_rests)
    returning id into v_id;
  else
    update public.workout_sessions
    set name = v_name,
        ended_at = v_ended,
        description = nullif(btrim(p_workout ->> 'description', E' \t\n\r\f'), ''),
        gym = nullif(btrim(p_workout ->> 'gym', E' \t\n\r\f'), ''),
        exercise_rests = v_rests
    where id = v_id;
    delete from public.workout_sets where session_id = v_id;
  end if;

  for v_ex in
    select e.value, e.ordinality
    from jsonb_array_elements(p_workout -> 'exercises') with ordinality as e(value, ordinality)
    order by e.ordinality
  loop
    begin
      v_group := (v_ex.value ->> 'superset_group')::smallint;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'invalid superset group' using errcode = '22023';
    end;
    for v_set in
      select s.value, s.ordinality
      from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
      order by s.ordinality
    loop
      begin
        v_type := coalesce(v_set.value ->> 'set_type', 'working')::public.set_type;
        v_rpe := (v_set.value ->> 'rpe')::numeric;
        v_weight := coalesce((v_set.value ->> 'weight_kg')::numeric, 0);
        v_reps := coalesce((v_set.value ->> 'reps')::smallint, 0);
        v_dur := (v_set.value ->> 'duration_s')::int;
        v_dist := (v_set.value ->> 'distance_m')::numeric;
        v_steps := (v_set.value ->> 'steps')::int;
        v_floors := (v_set.value ->> 'floors')::int;
        v_kind := nullif(v_set.value ->> 'note_kind', '')::public.set_note_kind;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'invalid set' using errcode = '22023';
      end;
      if (v_rpe is not null and (v_rpe <> trunc(v_rpe) or v_rpe not between 6 and 10))
         or v_weight < 0 or v_reps < 0 or coalesce(v_dur, 0) < 0 or coalesce(v_dist, 0) < 0
         or coalesce(v_steps, 0) < 0 or coalesce(v_floors, 0) < 0 then
        raise exception 'invalid set' using errcode = '22023';
      end if;
      v_note := nullif(btrim(v_set.value ->> 'notes', E' \t\n\r\f'), '');
      if v_kind is null and v_note is not null then
        v_kind := 'other';
      end if;
      insert into public.workout_sets
        (session_id, exercise_id, exercise_position, superset_group, set_index, set_type, rpe, weight_kg, reps,
         duration_s, distance_m, steps, floors, completed_at, notes, note_kind)
      values (v_id, (v_ex.value ->> 'exercise_id')::uuid, (v_ex.ordinality - 1)::smallint, v_group,
              v_set.ordinality::smallint, v_type, v_rpe, v_weight, v_reps, v_dur, v_dist, v_steps, v_floors,
              v_ended, v_note, v_kind);
    end loop;
  end loop;

  return v_id;
end;
$$;

-- update_routine_from_session: same body as 20261130100000_training_notes.sql, but the routine exercise
-- note kind is carried over together with the text.
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
                                                      'notes', x.notes, 'note_kind', x.note_kind)),
                  '{}'::jsonb)
  into v_prev
  from (
    select distinct on (re.exercise_id) re.exercise_id, re.rest_seconds, re.target_rpe, re.notes, re.note_kind
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

    -- Legacy summary: first working set's reps, else the first set's.
    select greatest(1, least(50, ws.reps))::smallint into v_reps
    from public.workout_sets ws
    where ws.session_id = p_session_id and ws.completed_at is not null
      and ws.exercise_position = v_ex.exercise_position
    order by (ws.set_type <> 'working'), ws.set_index
    limit 1;

    v_rest := coalesce((v_rests ->> v_ex.exercise_position::text)::smallint,
                       (v_prev -> v_ex.exercise_id::text ->> 'rest')::smallint,
                       120::smallint);
    v_rpe := (v_prev -> v_ex.exercise_id::text ->> 'rpe')::numeric;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds,
       notes, note_kind)
    values (v_routine_id, v_ex.exercise_id, v_pos::smallint, v_group, v_ex.n::smallint, v_reps, v_rpe, v_rest,
            v_prev -> v_ex.exercise_id::text ->> 'notes',
            (v_prev -> v_ex.exercise_id::text ->> 'note_kind')::public.exercise_note_kind)
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

  return v_routine_id;
end;
$$;
