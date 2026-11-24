-- T-323 (US-147): "Frente a la ultima vez" on the finished-workout summary.
-- Rules: planning/07-coach-design.md section 3 (F) and "Weekly report: progress first" (W1); the device computes the
-- state from the inputs this RPC fetches, it mirrors the strength states of coach_progress_strength_for.
--
-- Additive and read-only: two new functions and a rewrite of the body of coach_progress_sessions_for (same signature,
-- same columns, same rows). No table or data is touched.
--
-- 1. coach_user_sets_for: the per-set rows of coach_progress_sessions_for generalised to any date range and to every
--    plan of the user. coach_progress_sessions_for now calls it (one plan, block-to-date range), so the weekly report
--    keeps its exact output (coach_week_progress.test.sql is unchanged).
-- 2. session_comparison(p_session_id): per exercise of a completed session, its working sets and those of the latest
--    earlier non-deload session of the caller.
--
-- Which plan decides a session's plan week and deload flag when the user has several plans: only plans that were ever
-- accepted count (status accepted or superseded; proposed and discarded plans never governed a day). Of the plans whose
-- window (coach_plan_windows_for as of the end of the range, so extensions are honoured) contains the session's UTC
-- date, the most recently accepted one wins (accepted_at desc, then id). A session in no window has week null and
-- deload false. When one plan is given (the weekly report) only that plan is considered, whatever its status.
--
-- Security: the helper reads with an explicit p_uid and, like the other `_for` helpers, is executable by nobody.
-- session_comparison is security definer ONLY because it must call the internal helpers (coach_user_sets_for,
-- coach_open_discomforts_for), whose execute stays revoked from authenticated (coach_week_progress.test.sql asserts it);
-- every read is scoped to (select auth.uid()), no JWT gives [], and the session must be the caller's and completed.

-- ---------------------------------------------------------------------------
-- 1. Helper
-- ---------------------------------------------------------------------------
create function public.coach_user_sets_for(p_uid uuid, p_from date, p_to date, p_plan_id uuid default null)
returns table (
  session_id uuid, started_at timestamptz, day date, week integer, deload boolean,
  exercise_id uuid, exercise_position smallint, set_index smallint, set_type public.set_type,
  weight_kg numeric, reps integer, rpe numeric, rep_min smallint, rep_max smallint,
  muscle_group public.muscle_group, xkey text, h1 boolean, main_lift boolean)
language sql
stable
security invoker
set search_path = ''
as $$
  with plans as (
    select p.id, p.accepted_at
    from public.coach_plans p
    where p.user_id = p_uid
      and (case when p_plan_id is null then p.status in ('accepted', 'superseded') else p.id = p_plan_id end)
  ),
  win as (
    select pl.id as plan_id, pl.accepted_at, w.week, w.start_date, w.end_date,
           coalesce((select bool_or(s.deload) from public.coach_plan_sessions s
                     where s.plan_id = pl.id and s.user_id = p_uid and s.week = w.week), false) as deload
    from plans pl
    cross join lateral public.coach_plan_windows_for(p_uid, pl.id, p_to) w
  )
  select s.id, s.started_at, (s.started_at at time zone 'utc')::date, m.week, coalesce(m.deload, false),
         st.exercise_id, st.exercise_position, st.set_index, st.set_type,
         st.weight_kg, st.reps::integer, st.rpe, st.rep_min, st.rep_max,
         e.muscle_group, coalesce(e.key, e.id::text),
         -- Counts for H1: the same rule as muscle_group_sets(.., true).
         e.counts_as_strength
           and (not e.is_custom
                or e.metric_type not in ('duration', 'weight_duration', 'distance_duration', 'floors_duration',
                                         'steps_duration')),
         coalesce(e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                               'horizontal_pull', 'vertical_pull'), false)
  from public.workout_sessions s
  join public.workout_sets st on st.session_id = s.id and st.completed_at is not null
  join public.exercises e on e.id = st.exercise_id
  left join lateral (
    select w.week, w.deload
    from win w
    where (s.started_at at time zone 'utc')::date between w.start_date and w.end_date
    order by w.accepted_at desc nulls last, w.plan_id
    limit 1
  ) m on true
  where s.user_id = p_uid and s.status = 'completed'
    and (s.started_at at time zone 'utc')::date between p_from and p_to;
$$;

revoke execute on function public.coach_user_sets_for(uuid, date, date, uuid) from public, anon, authenticated;

-- Same signature, columns and rows as before: one plan, from its first training day to p_end.
create or replace function public.coach_progress_sessions_for(p_uid uuid, p_plan_id uuid, p_end date)
returns table (
  session_id uuid, started_at timestamptz, day date, week integer, deload boolean,
  exercise_id uuid, exercise_position smallint, set_index smallint, set_type public.set_type,
  weight_kg numeric, reps integer, rpe numeric, rep_min smallint, rep_max smallint,
  muscle_group public.muscle_group, xkey text, h1 boolean, main_lift boolean)
language sql
stable
security invoker
set search_path = ''
as $$
  select h.*
  from public.coach_plans p
  cross join lateral public.coach_user_sets_for(p_uid, coalesce(p.training_start, p.block_start), p_end, p_plan_id) h
  where p.id = p_plan_id and p.user_id = p_uid;
$$;

-- ---------------------------------------------------------------------------
-- 2. session_comparison
-- ---------------------------------------------------------------------------
-- [{exercise_id, open_discomfort, current: S, previous: S | null}] ordered by the exercise's first position in the
-- session; S = {session_id, day, deload, sets: [{weight_kg, reps, rpe, rep_min, rep_max}]}. Working sets with a load and
-- reps only; the sets of every position of the same exercise are merged in (exercise_position, set_index) order.
-- previous = the latest completed non-deload session of the caller before this one (started_at, then id) with such a
-- set of that exercise; no age or rep-range filter here (the device applies them).
create function public.session_comparison(p_session_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with me as (
    select (select auth.uid()) as uid,
           (now() at time zone 'utc')::date as today
  ),
  cur as (
    select s.id, s.started_at, (s.started_at at time zone 'utc')::date as day
    from public.workout_sessions s, me
    where s.id = p_session_id and s.user_id = me.uid and s.status = 'completed'
  ),
  h as materialized (
    select x.*
    from me, cur,
         lateral public.coach_user_sets_for(me.uid, date '1900-01-01', greatest(me.today, cur.day)) x
    where x.set_type = 'working' and x.weight_kg is not null and x.reps is not null
  ),
  ses as (
    select h.exercise_id, h.session_id, min(h.started_at) as started_at, min(h.day) as day,
           bool_or(h.deload) as deload, min(h.exercise_position) as pos,
           jsonb_agg(jsonb_build_object('weight_kg', h.weight_kg, 'reps', h.reps, 'rpe', h.rpe,
                                        'rep_min', h.rep_min, 'rep_max', h.rep_max)
                     order by h.exercise_position, h.set_index) as sets
    from h
    group by h.exercise_id, h.session_id
  ),
  pairs as (
    select c.exercise_id, c.pos, c.session_id as c_id, c.day as c_day, c.deload as c_deload, c.sets as c_sets,
           p.session_id as p_id, p.day as p_day, p.deload as p_deload, p.sets as p_sets
    from ses c
    left join lateral (
      select q.* from ses q
      where q.exercise_id = c.exercise_id and not q.deload
        and (q.started_at, q.session_id) < (c.started_at, c.session_id)
      order by q.started_at desc, q.session_id desc
      limit 1
    ) p on true
    where c.session_id = p_session_id
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'exercise_id', pr.exercise_id,
    'open_discomfort', exists (select 1 from me, public.coach_open_discomforts_for(me.uid, me.today) d
                               where d.exercise_id = pr.exercise_id),
    'current', jsonb_build_object('session_id', pr.c_id, 'day', pr.c_day, 'deload', pr.c_deload, 'sets', pr.c_sets),
    'previous', case when pr.p_id is null then null
                     else jsonb_build_object('session_id', pr.p_id, 'day', pr.p_day, 'deload', pr.p_deload,
                                             'sets', pr.p_sets) end
  ) order by pr.pos, pr.exercise_id), '[]'::jsonb)
  from pairs pr;
$$;

revoke execute on function public.session_comparison(uuid) from public, anon;
grant execute on function public.session_comparison(uuid) to authenticated;
