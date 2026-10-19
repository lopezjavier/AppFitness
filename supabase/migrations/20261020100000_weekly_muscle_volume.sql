-- T-048b (US-15): weekly volume per muscle group.
-- Volume = sum(weight_kg * reps) of completed, non-warm-up sets of completed sessions (same filter as
-- exercise_history). Attribution by the exercise's primary muscle group. Week = ISO week (Monday) by the UTC
-- date of started_at. Only weeks/groups with data are returned. RLS limits it to the caller's sessions.
create function public.weekly_muscle_volume(p_from date)
returns table (week_start date, muscle_group public.muscle_group, total_volume numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select date_trunc('week', s.started_at at time zone 'UTC')::date as week_start,
         e.muscle_group,
         sum(ws.weight_kg * ws.reps) as total_volume
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  where s.status = 'completed'
    and ws.completed_at is not null
    and ws.set_type <> 'warm_up'
    and (s.started_at at time zone 'UTC')::date >= p_from
  group by 1, e.muscle_group
  order by 1, 2;
$$;

revoke execute on function public.weekly_muscle_volume(date) from public, anon;
grant execute on function public.weekly_muscle_volume(date) to authenticated;
