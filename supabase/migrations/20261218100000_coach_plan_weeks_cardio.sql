-- T-183: coach_plan_weeks also returns the weekly cardio minutes and steps (04 section 9.9).
-- Same signature and security invoker as the base; existing keys unchanged, four keys added per week.
create or replace function public.coach_plan_weeks(p_plan_id uuid, p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select p.id, p.block_start,
           nullif(coalesce(p.nutrition->'activity'->>'cardio_min_week',
                           p.training->'cardio'->>'target_min_week'), '')::int as cardio_target,
           nullif(p.nutrition->'activity'->>'steps_day', '')::int as steps_target
    from public.coach_plans p
    where p.id = p_plan_id and p.user_id = (select auth.uid())
  ),
  weeks as (
    select s.week,
           bool_or(s.deload) as deload,
           count(*)::int as planned,
           count(*) filter (where s.session_id is not null)::int as completed,
           jsonb_agg(jsonb_build_object(
             'session_index', s.session_index, 'name', s.name, 'routine_id', s.routine_id,
             'session_id', s.session_id, 'done', s.session_id is not null) order by s.session_index) as sessions
    from public.coach_plan_sessions s
    join plan on plan.id = s.plan_id
    where s.user_id = (select auth.uid())
    group by s.week
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'week', w.week,
    'start_date', plan.block_start + 7 * (w.week - 1),
    'end_date', plan.block_start + 7 * (w.week - 1) + 6,
    'phase', case when p_today < plan.block_start + 7 * (w.week - 1) then 'upcoming'
                  when p_today > plan.block_start + 7 * (w.week - 1) + 6 then 'past'
                  else 'current' end,
    'deload', w.deload,
    'planned', w.planned,
    'completed', w.completed,
    'adherence_pct', round(w.completed * 100.0 / w.planned)::int,
    'sessions', w.sessions,
    'cardio_min_done', (
      select round(coalesce(sum(st.duration_s), 0) / 60.0)::int
      from public.workout_sessions ws
      join public.workout_sets st on st.session_id = ws.id
      join public.exercises e on e.id = st.exercise_id
      where ws.user_id = (select auth.uid()) and ws.status = 'completed'
        and st.completed_at is not null and e.movement_pattern = 'cardio'
        and (ws.started_at at time zone 'utc')::date
            between plan.block_start + 7 * (w.week - 1) and plan.block_start + 7 * (w.week - 1) + 6),
    'cardio_min_target', plan.cardio_target,
    'steps_avg', (
      select round(avg(a.steps))::int
      from public.daily_activity a
      where a.user_id = (select auth.uid()) and a.steps is not null
        and a.day between plan.block_start + 7 * (w.week - 1) and plan.block_start + 7 * (w.week - 1) + 6),
    'steps_target', plan.steps_target) order by w.week), '[]'::jsonb)
  from weeks w cross join plan;
$$;
