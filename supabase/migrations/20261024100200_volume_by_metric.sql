-- T-092 (US-31): volume follows the exercise metric type (04 §4.2, §4.7).
--   weight_reps, weighted : weight * reps          bodyweight : (body weight + added) * reps
--   assisted              : max(body weight - assistance, 0) * reps
--   everything else       : 0 (no load x reps)
-- Body weight is the caller's newest body_measurements row at read time (0 when none).

create function public.latest_body_weight_kg()
returns numeric
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(
    (select m.weight_kg from public.body_measurements m
     where m.user_id = (select auth.uid())
     order by m.measured_at desc limit 1),
    0);
$$;

revoke execute on function public.latest_body_weight_kg() from public, anon;
grant execute on function public.latest_body_weight_kg() to authenticated;

create function public.set_volume(p_metric public.exercise_metric_type, p_weight numeric, p_reps numeric, p_body_weight numeric)
returns numeric
language sql
immutable
security invoker
set search_path = ''
as $$
  select case p_metric
           when 'weight_reps' then p_weight * p_reps
           when 'weighted'    then p_weight * p_reps
           when 'bodyweight'  then (p_body_weight + p_weight) * p_reps
           when 'assisted'    then greatest(p_body_weight - p_weight, 0) * p_reps
           else 0
         end;
$$;

revoke execute on function public.set_volume(public.exercise_metric_type, numeric, numeric, numeric) from public, anon;
grant execute on function public.set_volume(public.exercise_metric_type, numeric, numeric, numeric) to authenticated;

-- 1RM stays for loaded rep metrics only; volume follows the metric.
create or replace function public.exercise_history(p_exercise_id uuid, p_from date default null)
returns table (session_id uuid, session_date date, best_one_rm numeric, total_volume numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select s.id,
         (s.started_at at time zone 'UTC')::date,
         max(case
               when e.metric_type in ('weight_reps', 'weighted')
                    and ws.reps between 1 and 12 and ws.weight_kg > 0 then
                 round(case when ws.reps = 1 then ws.weight_kg
                            else ws.weight_kg * (1 + ws.reps / 30.0) end * 2) / 2
             end),
         coalesce(sum(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg)), 0)
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  cross join (select public.latest_body_weight_kg() as kg) bw
  where s.status = 'completed'
    and ws.exercise_id = p_exercise_id
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
    and (p_from is null or (s.started_at at time zone 'UTC')::date >= p_from)
  group by s.id, s.started_at
  order by s.started_at;
$$;

revoke execute on function public.exercise_history(uuid, date) from public, anon;
grant execute on function public.exercise_history(uuid, date) to authenticated;

create or replace function public.weekly_muscle_volume(p_from date)
returns table (week_start date, muscle_group public.muscle_group, total_volume numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select date_trunc('week', s.started_at at time zone 'UTC')::date as week_start,
         e.muscle_group,
         sum(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg)) as total_volume
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  cross join (select public.latest_body_weight_kg() as kg) bw
  where s.status = 'completed'
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
    and (s.started_at at time zone 'UTC')::date >= p_from
  group by 1, e.muscle_group
  order by 1, 2;
$$;

revoke execute on function public.weekly_muscle_volume(date) from public, anon;
grant execute on function public.weekly_muscle_volume(date) to authenticated;
