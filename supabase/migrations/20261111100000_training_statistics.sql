-- T-114 (US-40, US-41): training statistics (04 §4.11). Four read-only RPCs, no table change.
-- Eligibility (inline in each, same filter as exercise_session_records): completed sessions whose UTC start date is
-- inside [p_from, p_to] (null = open bound), completed sets, warm-up sets only when warm_up_included().
-- RLS plus security invoker scopes everything to the caller.

create function public.training_summary(p_from date default null, p_to date default null)
returns table (workouts bigint, sets bigint, volume numeric, duration_s bigint)
language sql
stable
security invoker
set search_path = ''
as $$
  -- Two independent aggregates cross-joined so that sets never multiply the duration.
  -- A completed session in the window counts as a workout even when it has no eligible set.
  select ses.workouts, st.sets, st.volume, ses.duration_s
  from (
    select count(*)::bigint as workouts,
           coalesce(sum(greatest(0, floor(extract(epoch from (coalesce(s.ended_at, s.started_at) - s.started_at))
                                          - s.paused_total_s))), 0)::bigint as duration_s
    from public.workout_sessions s
    where s.status = 'completed'
      and (s.started_at at time zone 'UTC')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
  ) ses
  cross join (
    select count(*)::bigint as sets,
           coalesce(sum(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg) * public.volume_factor(e.id)), 0)::numeric as volume
    from public.workout_sessions s
    join public.workout_sets ws on ws.session_id = s.id
    join public.exercises e on e.id = ws.exercise_id
    cross join (select public.latest_body_weight_kg() as kg) bw
    where s.status = 'completed'
      and (s.started_at at time zone 'UTC')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
      and ws.completed_at is not null
      and (ws.set_type <> 'warm_up' or public.warm_up_included())
  ) st;
$$;

revoke execute on function public.training_summary(date, date) from public, anon;
grant execute on function public.training_summary(date, date) to authenticated;

create function public.weekly_set_counts(p_from date default null, p_to date default null)
returns table (week_start date, sets bigint)
language sql
stable
security invoker
set search_path = ''
as $$
  select date_trunc('week', s.started_at at time zone 'UTC')::date as week_start,
         count(*)::bigint as sets
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  where s.status = 'completed'
    and (s.started_at at time zone 'UTC')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
  group by 1
  order by 1;
$$;

revoke execute on function public.weekly_set_counts(date, date) from public, anon;
grant execute on function public.weekly_set_counts(date, date) to authenticated;

-- Each eligible set adds 1 to the primary group and 0.5 to each distinct secondary group other than the primary.
-- Ties on sets are broken by muscle_group in enum declaration order (not alphabetical).
create function public.muscle_group_sets(p_from date default null, p_to date default null)
returns table (muscle_group public.muscle_group, sets numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with el as (
    select e.muscle_group as mg, e.secondary_muscles as sec
    from public.workout_sessions s
    join public.workout_sets ws on ws.session_id = s.id
    join public.exercises e on e.id = ws.exercise_id
    where s.status = 'completed'
      and (s.started_at at time zone 'UTC')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
      and ws.completed_at is not null
      and (ws.set_type <> 'warm_up' or public.warm_up_included())
  ),
  contrib as (
    select el.mg as g, 1::numeric as w from el
    union all
    select d.u, 0.5::numeric
    from el
    cross join lateral (select distinct u from unnest(el.sec) as u where u <> el.mg) d
  )
  select c.g as muscle_group, sum(c.w) as sets
  from contrib c
  group by c.g
  having sum(c.w) > 0
  order by sum(c.w) desc, c.g;
$$;

revoke execute on function public.muscle_group_sets(date, date) from public, anon;
grant execute on function public.muscle_group_sets(date, date) to authenticated;

-- Without a group: eligible sets and distinct sessions per exercise. With a group: only exercises whose primary or
-- secondary includes it, sets weighted 1 (primary) or 0.5 (secondary).
create function public.exercise_set_counts(
  p_from date default null, p_to date default null, p_muscle_group public.muscle_group default null)
returns table (exercise_id uuid, sets numeric, sessions bigint)
language sql
stable
security invoker
set search_path = ''
as $$
  select e.id as exercise_id,
         sum(case when p_muscle_group is null or e.muscle_group = p_muscle_group then 1 else 0.5 end)::numeric as sets,
         count(distinct s.id)::bigint as sessions
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  where s.status = 'completed'
    and (s.started_at at time zone 'UTC')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
    and (p_muscle_group is null or e.muscle_group = p_muscle_group or p_muscle_group = any (e.secondary_muscles))
  group by e.id
  order by 2 desc, 3 desc, 1;
$$;

revoke execute on function public.exercise_set_counts(date, date, public.muscle_group) from public, anon;
grant execute on function public.exercise_set_counts(date, date, public.muscle_group) to authenticated;
