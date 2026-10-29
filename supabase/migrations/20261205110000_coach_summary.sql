-- T-157 (E13, US-68/US-69): compact summary of the last 8 ISO weeks for the coach model (planning/07 section 8).
-- Per-week aggregates only: no raw rows and no free text (notes, food names, custom exercise names), and a hard
-- size cap of 16 384 bytes (checked by coach_summary.test.sql on a worst-case fixture).
-- Set-type filtering follows 04 section 4.6 (warm_up_included()); e1RM is coach_e1rm (Epley, 04 section 4.1).

-- Monday of the ISO week of a date.
create function public.coach_week_start(p_day date)
returns date
language sql
immutable
security invoker
set search_path = ''
as $$
  select p_day - (extract(isodow from p_day)::int - 1);
$$;

create function public.coach_summary(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_from date := public.coach_week_start(p_today) - 49;
  v_out  jsonb;
begin
  with
  w as (select v_from + 7 * i as ws from generate_series(0, 7) i),
  wt_daily as (
    select (measured_at at time zone 'UTC')::date as d, avg(weight_kg) as a
    from public.body_measurements
    where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_from and p_today
    group by 1
  ),
  wt as (
    select public.coach_week_start(d) as ws, avg(a) as a, count(*) as n from wt_daily group by 1
  ),
  diary_daily as (
    select log_date, sum(kcal) as k, sum(protein_g) as p, sum(carbs_g) as c, sum(fat_g) as f
    from public.food_log_entries
    where user_id = v_uid and log_date between v_from and p_today
    group by log_date
  ),
  dw as (
    select public.coach_week_start(log_date) as ws, count(*) as n, avg(k) as k, avg(p) as p, avg(c) as c, avg(f) as f
    from diary_daily group by 1
  ),
  ac as (
    select public.coach_week_start(day) as ws, avg(steps) as s, avg(active_kcal) as k
    from public.daily_activity
    where user_id = v_uid and day between v_from and p_today
    group by 1
  ),
  sl_daily as (
    select (ended_at at time zone 'UTC')::date as d, sum(minutes) as m
    from public.sleep_sessions
    where user_id = v_uid and (ended_at at time zone 'UTC')::date between v_from and p_today
    group by 1
  ),
  sl as (select public.coach_week_start(d) as ws, avg(m) as m from sl_daily group by 1),
  se as (
    select public.coach_week_start((started_at at time zone 'UTC')::date) as ws, count(*) as n
    from public.workout_sessions
    where user_id = v_uid and status = 'completed'
      and (started_at at time zone 'UTC')::date between v_from and p_today
    group by 1
  ),
  sets as (
    select public.coach_week_start((s.started_at at time zone 'UTC')::date) as ws, s.id as sid,
           st.weight_kg, st.reps, st.duration_s, e.id as eid, coalesce(e.key, e.id::text) as xid,
           e.muscle_group as mg, e.movement_pattern as mp
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or public.warm_up_included())
      and (s.started_at at time zone 'UTC')::date between v_from and p_today
  ),
  mus as (
    select ws, mg, count(*) as n, row_number() over (partition by ws order by count(*) desc, mg) as rn
    from sets where mp is distinct from 'cardio' group by ws, mg
  ),
  ca as (
    select ws, count(distinct sid) as n, round(coalesce(sum(duration_s), 0) / 60.0, 0) as m
    from sets where mp = 'cardio' group by ws
  ),
  ci as (select week_start as ws, energy from public.weekly_check_ins where user_id = v_uid),
  ml as (
    select eid, xid, count(distinct sid) as n
    from sets
    where mp in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull')
    group by eid, xid
    order by n desc, xid
    limit 12
  ),
  disc as (
    select coalesce(e.key, e.id::text) as xid, d.level, (d.logged_at at time zone 'UTC')::date as logged_on, d.logged_at
    from public.exercise_discomforts d
    join public.exercises e on e.id = d.exercise_id
    where d.user_id = v_uid and d.resolved_at is null and now() < d.logged_at + interval '7 days'
    order by d.logged_at desc
    limit 10
  )
  select jsonb_build_object(
    'generated_for', p_today,
    'window', jsonb_build_object('from', v_from, 'to', p_today),
    'profile', (
      select jsonb_build_object(
        'sex', p.sex,
        'age', case when p.birth_date is not null then date_part('year', age(p_today, p.birth_date))::int end,
        'height_cm', p.height_cm,
        'weight_kg', (select b.weight_kg from public.body_measurements b
                      where b.user_id = v_uid order by b.measured_at desc limit 1))
      from public.profiles p where p.id = v_uid),
    'training_preferences', (
      select jsonb_build_object(
        'intention', tp.intention, 'place', tp.place, 'equipment', coalesce(to_jsonb(tp.equipment), '[]'::jsonb),
        'days_per_week', tp.days_per_week, 'session_minutes', tp.session_minutes)
      from (select 1) one left join public.training_preferences tp on tp.user_id = v_uid),
    'estimates', public.coach_compute(p_today, null),
    'weeks', (
      select jsonb_agg(jsonb_build_object(
        'week_start', w.ws,
        'weigh_in_avg', round(wt.a, 2),
        'weigh_in_days', coalesce(wt.n, 0),
        'diary_days', coalesce(dw.n, 0),
        'kcal_avg', round(dw.k, 0),
        'protein_g_avg', round(dw.p, 0),
        'carbs_g_avg', round(dw.c, 0),
        'fat_g_avg', round(dw.f, 0),
        'steps_avg', round(ac.s, 0),
        'active_kcal_avg', round(ac.k, 0),
        'sleep_minutes_avg', round(sl.m, 0),
        'sessions', coalesce(se.n, 0),
        'sets_by_muscle', coalesce((
          select jsonb_agg(jsonb_build_object('muscle', mr.mg, 'sets', mr.n) order by mr.rn)
          from mus mr where mr.ws = w.ws and mr.rn <= 8), '[]'::jsonb),
        'cardio', jsonb_build_object('sessions', coalesce(ca.n, 0), 'minutes', coalesce(ca.m, 0)),
        'check_in_energy', ci.energy) order by w.ws)
      from w
      left join wt on wt.ws = w.ws
      left join dw on dw.ws = w.ws
      left join ac on ac.ws = w.ws
      left join sl on sl.ws = w.ws
      left join se on se.ws = w.ws
      left join ca on ca.ws = w.ws
      left join ci on ci.ws = w.ws),
    'main_lifts', coalesce((
      select jsonb_agg(jsonb_build_object(
        'exercise', ml.xid,
        'sessions', ml.n,
        'weekly_e1rm', (
          select jsonb_agg(x.e order by w.ws)
          from w left join (
            select s2.ws, max(public.coach_e1rm(s2.weight_kg, s2.reps)) as e
            from sets s2 where s2.eid = ml.eid group by s2.ws) x on x.ws = w.ws)
      ) order by ml.n desc, ml.xid)
      from ml), '[]'::jsonb),
    'open_discomforts', coalesce((
      select jsonb_agg(jsonb_build_object('exercise', disc.xid, 'level', disc.level, 'logged_on', disc.logged_on)
                       order by disc.logged_at desc)
      from disc), '[]'::jsonb)
  ) into v_out;

  return v_out;
end;
$$;

revoke execute on function public.coach_week_start(date) from public, anon;
revoke execute on function public.coach_summary(date) from public, anon;
grant execute on function public.coach_week_start(date) to authenticated;
grant execute on function public.coach_summary(date) to authenticated;
