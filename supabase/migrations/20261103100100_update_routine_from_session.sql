-- T-099 (US-36, ADR-54 amendment): replaces a routine's exercises and planned sets with the COMPLETED sets of a
-- finished session that was started from it. Runs after finish_workout_session (which drops pending sets).
-- Never touches workout_sessions / workout_sets, nor the routine's name, notes, folder, order or flags.
-- security invoker: RLS applies. One function = one transaction, so any error leaves the routine intact.
create function public.update_routine_from_session(p_session_id uuid)
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
  select coalesce(jsonb_object_agg(x.exercise_id::text, jsonb_build_object('rest', x.rest_seconds, 'rpe', x.target_rpe)),
                  '{}'::jsonb)
  into v_prev
  from (
    select distinct on (re.exercise_id) re.exercise_id, re.rest_seconds, re.target_rpe
    from public.routine_exercises re
    where re.routine_id = v_routine_id
    order by re.exercise_id, re.position
  ) x;

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
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds)
    values (v_routine_id, v_ex.exercise_id, v_pos::smallint, v_group, v_ex.n::smallint, v_reps, v_rpe, v_rest)
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

revoke execute on function public.update_routine_from_session(uuid) from public, anon;
grant execute on function public.update_routine_from_session(uuid) to authenticated;
