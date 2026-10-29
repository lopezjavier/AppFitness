-- T-159 (US-70): optional rep range per routine exercise and a deterministic progression suggestion
-- (double progression, planning/04 section 4.14). No new table: RLS of the existing tables is unchanged.
-- workout_sets carries a snapshot of the range of the session it was started with, so the suggestion only
-- compares sessions done with the exact same range. Free workouts and save_completed_workout leave it null.

alter table public.routine_exercises
  add column rep_min smallint null,
  add column rep_max smallint null,
  add constraint routine_exercises_rep_range check (
    (rep_min is null and rep_max is null)
    or (rep_min is not null and rep_max is not null
        and rep_min between 1 and 50 and rep_max between 1 and 50 and rep_min <= rep_max));

alter table public.workout_sets
  add column rep_min smallint null,
  add column rep_max smallint null,
  add constraint workout_sets_rep_range check (
    (rep_min is null and rep_max is null)
    or (rep_min is not null and rep_max is not null
        and rep_min between 1 and 50 and rep_max between 1 and 50 and rep_min <= rep_max));

-- save_routine: same body as 20261203100000_note_kind.sql plus the optional rep range (both keys or neither).
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
  v_rmin    smallint;
  v_rmax    smallint;
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

-- start_workout_session: same body as 20261022120000_routine_planned_sets.sql, but every created set carries the
-- rep range of its routine exercise (null when the exercise has none).
create or replace function public.start_workout_session(p_routine_id uuid default null)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id   uuid;
  v_name text := 'Entreno libre';
begin
  if p_routine_id is not null then
    select r.name into v_name from public.routines r where r.id = p_routine_id;
    if not found then
      raise exception 'routine not found' using errcode = 'P0002';
    end if;
  end if;

  insert into public.workout_sessions (routine_id, name)
  values (p_routine_id, v_name)
  returning id into v_id;

  if p_routine_id is not null then
    insert into public.workout_sets
      (session_id, exercise_id, exercise_position, superset_group, set_index, set_type, rep_min, rep_max)
    select v_id, re.exercise_id, re.position, re.superset_group, rs.set_index, rs.set_type, re.rep_min, re.rep_max
    from public.routine_exercises re
    join public.routine_exercise_sets rs on rs.routine_exercise_id = re.id
    where re.routine_id = p_routine_id;

    insert into public.workout_sets
      (session_id, exercise_id, exercise_position, superset_group, set_index, rep_min, rep_max)
    select v_id, re.exercise_id, re.position, re.superset_group, gs.n, re.rep_min, re.rep_max
    from public.routine_exercises re
    cross join lateral generate_series(1, re.target_sets) as gs(n)
    where re.routine_id = p_routine_id
      and not exists (select 1 from public.routine_exercise_sets rs where rs.routine_exercise_id = re.id);
  end if;

  return v_id;
end;
$$;

-- update_routine_from_session: same body as 20261203100000_note_kind.sql, but the previous rep range of the
-- exercise is carried over (first routine exercise per exercise_id wins), like rest, RPE and notes.
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
                                                      'notes', x.notes, 'note_kind', x.note_kind,
                                                      'rep_min', x.rep_min, 'rep_max', x.rep_max)),
                  '{}'::jsonb)
  into v_prev
  from (
    select distinct on (re.exercise_id) re.exercise_id, re.rest_seconds, re.target_rpe, re.notes, re.note_kind,
           re.rep_min, re.rep_max
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
       notes, note_kind, rep_min, rep_max)
    values (v_routine_id, v_ex.exercise_id, v_pos::smallint, v_group, v_ex.n::smallint, v_reps, v_rpe, v_rest,
            v_prev -> v_ex.exercise_id::text ->> 'notes',
            (v_prev -> v_ex.exercise_id::text ->> 'note_kind')::public.exercise_note_kind,
            (v_prev -> v_ex.exercise_id::text ->> 'rep_min')::smallint,
            (v_prev -> v_ex.exercise_id::text ->> 'rep_max')::smallint)
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

-- progression_suggestions: double progression per exercise of one session (planning/04 section 4.14).
-- Reads only the caller's own data (RLS, security invoker). The action and the base weight are decided here;
-- the client applies the weight increment (it depends on plates/dumbbells kept in client preferences).
-- Reason codes: all_sets_at_max (raise_weight), below_max (add_rep), two_sessions_below_min (lower_weight),
-- kept_discomfort (add_rep at the max, because an open discomfort of level 1-3 blocks the increase).
-- An exercise is absent without a range, without a matching earlier session, or with an open discomfort >= 4.
create or replace function public.progression_suggestions(p_session_id uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_ex      record;
  v_level   smallint;
  v_sids    uuid[];
  v_n1      int;
  v_min1    smallint;
  v_max1    smallint;
  v_top1    numeric;
  v_rpe1    numeric;
  v_anyrpe  boolean;
  v_below1  int;
  v_below2  int;
  v_n2      int;
  v_action  text;
  v_reason  text;
  v_goal    smallint;
  v_used    jsonb;
  v_result  jsonb := '[]'::jsonb;
begin
  if v_uid is null then
    return '[]'::jsonb;
  end if;
  perform 1
  from public.workout_sessions s
  where s.id = p_session_id and s.user_id = v_uid;
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
    where d.user_id = v_uid and d.exercise_id = v_ex.exercise_id
      and d.resolved_at is null and now() < d.logged_at + interval '7 days';
    if coalesce(v_level, 0) >= 4 then
      continue;
    end if;

    -- The two most recent completed sessions of this exercise done with the exact same range.
    select array_agg(m.id order by m.started_at desc) into v_sids
    from (
      select s.id, s.started_at
      from public.workout_sessions s
      where s.user_id = v_uid and s.status = 'completed' and s.id <> p_session_id
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

    v_goal := least(v_ex.rep_max, v_max1 + 1)::smallint;
    if v_min1 >= v_ex.rep_max and coalesce(v_rpe1, 0) <= 8 then
      if coalesce(v_level, 0) >= 1 then
        v_action := 'add_rep'; v_reason := 'kept_discomfort'; v_goal := v_ex.rep_max;
      else
        v_action := 'raise_weight'; v_reason := 'all_sets_at_max'; v_goal := v_ex.rep_min;
      end if;
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
    where s.id = any (case when v_action = 'lower_weight' then v_sids else v_sids[1:1] end)
      and ws.exercise_id = v_ex.exercise_id
      and ws.rep_min = v_ex.rep_min and ws.rep_max = v_ex.rep_max
      and ws.set_type = 'working' and ws.completed_at is not null;

    v_result := v_result || jsonb_build_object(
      'exercise_id', v_ex.exercise_id, 'rep_min', v_ex.rep_min, 'rep_max', v_ex.rep_max,
      'action', v_action, 'base_weight_kg', v_top1, 'goal_reps', v_goal, 'reason', v_reason,
      'used_rpe', v_anyrpe, 'sets_used', v_used);
  end loop;

  return v_result;
end;
$$;

revoke execute on function public.progression_suggestions(uuid) from public, anon;
grant execute on function public.progression_suggestions(uuid) to authenticated;
