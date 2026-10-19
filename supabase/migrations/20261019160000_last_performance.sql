-- T-046 (US-12): last performance of an exercise, used as ghost data in the live session.
-- Sets (completed only) of the caller's most recent completed session containing the exercise.
-- If the exercise appears twice in that session, only the lowest exercise_position is returned.
-- Empty result = never done. RLS limits it to the caller's sessions.
create function public.last_performance(p_exercise_id uuid)
returns table (set_index int, weight_kg numeric, reps int)
language sql
stable
security invoker
set search_path = ''
as $$
  with last_session as (
    select s.id
    from public.workout_sessions s
    where s.status = 'completed'
      and exists (select 1 from public.workout_sets x
                  where x.session_id = s.id
                    and x.exercise_id = p_exercise_id
                    and x.completed_at is not null)
    order by s.ended_at desc, s.started_at desc
    limit 1
  ),
  first_position as (
    select min(ws.exercise_position) as pos
    from public.workout_sets ws
    where ws.session_id = (select id from last_session)
      and ws.exercise_id = p_exercise_id
      and ws.completed_at is not null
  )
  select ws.set_index::int, ws.weight_kg, ws.reps::int
  from public.workout_sets ws
  where ws.session_id = (select id from last_session)
    and ws.exercise_id = p_exercise_id
    and ws.exercise_position = (select pos from first_position)
    and ws.completed_at is not null
  order by ws.set_index;
$$;

revoke execute on function public.last_performance(uuid) from public, anon;
grant execute on function public.last_performance(uuid) to authenticated;
