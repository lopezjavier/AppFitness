-- T-320 (E19, US-145 data side): the read-only data of the redesigned Estadisticas "Entrenos".
-- Rules: planning/07-coach-design.md section 3 (D1) progress of the period, (D2) records of the period, (D4) change of
-- the principal exercises, (D6) sets per muscle group by week; planning/04 section 4.1 (Epley e1RM), 4.5 and 4.6
-- (record set filter). No table change.
--   1. progress_records_in_range: the records rule shared by the weekly report, the block review and the range.
--   2. training_range_progress(p_from, p_to): per-exercise progress and the records of the range.
--   3. weekly_muscle_sets(p_weeks): sets per muscle group for each of the last weeks.

-- ---------------------------------------------------------------------------
-- 1. Records of a window (07 section 3 "Weekly report: progress first" (3) and "Other reports" (A3)): each session of
-- the window against every earlier completed session of the exercise (any date up to p_end; set filter of 04 section
-- 4.6, warm-up sets only when p_warm), strictly greater, the first session of an exercise never a record. At most 3
-- items (main lifts first, then the largest relative gain) and `total`, the number of records in the window.
-- p_warm is a parameter because coach_warm_up_included_for is not callable by `authenticated`.
-- ---------------------------------------------------------------------------
create function public.progress_records_in_range(p_uid uuid, p_start date, p_end date, p_warm boolean)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with sx as (
    select s.id as sid, s.started_at, (s.started_at at time zone 'utc')::date as day, st.exercise_id,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm,
           max(st.weight_kg) filter (where st.weight_kg > 0 and st.reps >= 1) as heaviest
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    where s.user_id = p_uid and s.status = 'completed'
      and (s.started_at at time zone 'utc')::date <= p_end
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or p_warm)
    group by s.id, s.started_at, st.exercise_id
  ),
  kv as (
    select sx.sid, sx.started_at, sx.day, sx.exercise_id, k.kind, k.v
    from sx
    cross join lateral (values ('e1rm', sx.e1rm), ('heaviest', sx.heaviest)) k(kind, v)
    where k.v is not null
  ),
  hist as (
    select kv.*,
           max(kv.v) over (partition by kv.exercise_id, kv.kind order by kv.started_at, kv.sid
                           rows between unbounded preceding and 1 preceding) as prior
    from kv
  ),
  rec as (
    select h.exercise_id, h.kind, max(h.v) as value_kg,
           (array_agg(h.prior order by h.started_at, h.sid))[1] as previous_kg
    from hist h
    where h.day between p_start and p_end and h.v > h.prior
    group by h.exercise_id, h.kind
  ),
  ranked as (
    select r.*,
           coalesce(e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                                 'horizontal_pull', 'vertical_pull'), false) as main_lift,
           r.value_kg / r.previous_kg as gain
    from rec r
    join public.exercises e on e.id = r.exercise_id
  ),
  best3 as (
    select r.* from ranked r
    order by r.main_lift desc, r.gain desc, r.exercise_id, r.kind
    limit 3
  )
  select jsonb_build_object(
    'items', coalesce((select jsonb_agg(jsonb_build_object('exercise_id', b.exercise_id, 'kind', b.kind,
                                                           'value_kg', b.value_kg, 'previous_kg', b.previous_kg,
                                                           'main_lift', b.main_lift)
                                        order by b.main_lift desc, b.gain desc, b.exercise_id, b.kind)
                       from best3 b), '[]'::jsonb),
    'total', (select count(*) from ranked)::int);
$$;

-- Granted because the invoker range RPC calls it as the caller; invoker + RLS make a foreign p_uid return nothing.
revoke execute on function public.progress_records_in_range(uuid, date, date, boolean) from public, anon;
grant execute on function public.progress_records_in_range(uuid, date, date, boolean) to authenticated;

-- The two coach record functions now share that body (same signatures, grants and output).
create or replace function public.coach_progress_records_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select public.progress_records_in_range(p_uid, p_start, p_end, public.coach_warm_up_included_for(p_uid)) -> 'items';
$$;

create or replace function public.coach_progress_block_records_for(p_uid uuid, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select public.progress_records_in_range(p_uid, p_start, p_end, public.coach_warm_up_included_for(p_uid));
$$;

-- ---------------------------------------------------------------------------
-- 2. Progress of the period (D1), principal-exercise change (D4) and records of the period (D2).
-- Per exercise: the e1RM (coach_e1rm, working sets) of each completed session in [p_from, p_to] (UTC dates, null = open
-- bound); first = earliest session, last = latest. "Todo" (p_from null) compares the exercise's first and last calendar
-- month with sessions (best e1RM of each month; its date is the earliest session holding it). State on the unrounded
-- ratio, as 07 section 3 (D1): up at last >= 1.04 * first, down at last <= 0.96 * first, else margin (display only);
-- uncompared with a single session (or month). Exercises without any e1RM (bodyweight, duration) are absent.
-- ---------------------------------------------------------------------------
create function public.training_range_progress(p_from date default null, p_to date default null)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with sx as (
    select s.id as sid, s.started_at, (s.started_at at time zone 'utc')::date as day, st.exercise_id,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    where s.status = 'completed'
      and (s.started_at at time zone 'utc')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
      and st.completed_at is not null
      and st.set_type = 'working'
    group by s.id, s.started_at, st.exercise_id
    having max(public.coach_e1rm(st.weight_kg, st.reps)) is not null
  ),
  keyed as (
    select sx.*,
           case when p_from is null then date_trunc('month', sx.started_at at time zone 'utc') at time zone 'utc'
                else sx.started_at end as bucket
    from sx
  ),
  banded as (
    select k.*, max(k.e1rm) over (partition by k.exercise_id, k.bucket) as best
    from keyed k
  ),
  pts as (
    select b.exercise_id, b.bucket, max(b.e1rm) as e1rm, min(b.day) filter (where b.e1rm = b.best) as day
    from banded b
    group by b.exercise_id, b.bucket
  ),
  agg as (
    select p.exercise_id,
           (select count(*) from sx where sx.exercise_id = p.exercise_id)::int as sessions,
           count(*) as n,
           (array_agg(p.e1rm order by p.bucket))[1] as f_kg,
           (array_agg(p.day order by p.bucket))[1] as f_day,
           (array_agg(p.e1rm order by p.bucket desc))[1] as l_kg,
           (array_agg(p.day order by p.bucket desc))[1] as l_day
    from pts p
    group by p.exercise_id
  ),
  st as (
    select a.*,
           coalesce(e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                                 'horizontal_pull', 'vertical_pull'), false) as main_lift,
           coalesce(e.key, e.id::text) as xkey,
           case when a.n < 2 then 'uncompared'
                when a.l_kg >= 1.04 * a.f_kg then 'up'
                when a.l_kg <= 0.96 * a.f_kg then 'down'
                else 'margin' end as state
    from agg a
    join public.exercises e on e.id = a.exercise_id
  )
  select jsonb_build_object(
    'exercises', coalesce((select jsonb_agg(jsonb_build_object(
                     'exercise_id', s.exercise_id,
                     'main_lift', s.main_lift,
                     'sessions', s.sessions,
                     'first', jsonb_build_object('date', s.f_day, 'e1rm_kg', s.f_kg),
                     'last', case when s.state <> 'uncompared'
                                  then jsonb_build_object('date', s.l_day, 'e1rm_kg', s.l_kg) end,
                     'change_kg', case when s.state <> 'uncompared' then s.l_kg - s.f_kg end,
                     'change_pct', case when s.state <> 'uncompared' then round((s.l_kg / s.f_kg - 1) * 100, 1) end,
                     'state', s.state)
                   order by s.main_lift desc, s.sessions desc, s.xkey, s.exercise_id)
                   from st s), '[]'::jsonb),
    'records', public.progress_records_in_range((select auth.uid()), coalesce(p_from, '-infinity'::date),
                                                coalesce(p_to, 'infinity'::date), public.warm_up_included()));
$$;

revoke execute on function public.training_range_progress(date, date) from public, anon;
grant execute on function public.training_range_progress(date, date) to authenticated;

-- ---------------------------------------------------------------------------
-- 3. Sets per muscle group by week (D6): the last p_weeks ISO weeks (UTC), oldest first, the last one the current
-- (partial) week; empty weeks present with no groups. Each week uses muscle_group_sets (secondary at half, warm-up
-- preference, not strength-only: the same counting as the period's "sets per muscle group").
-- ---------------------------------------------------------------------------
create function public.weekly_muscle_sets(p_weeks integer default 8)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  if p_weeks is null or p_weeks not between 1 and 52 then
    raise exception 'p_weeks must be between 1 and 52' using errcode = '22023';
  end if;
  return (
    select jsonb_agg(jsonb_build_object(
             'week_start', w.week_start,
             'groups', coalesce((select jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets)
                                                  order by g.sets desc, g.muscle_group)
                                 from public.muscle_group_sets(w.week_start, w.week_start + 6, false) g), '[]'::jsonb))
           order by w.week_start)
    from (
      select (date_trunc('week', (now() at time zone 'utc')::date)::date - 7 * i) as week_start
      from generate_series(0, p_weeks - 1) i
    ) w);
end;
$$;

revoke execute on function public.weekly_muscle_sets(integer) from public, anon;
grant execute on function public.weekly_muscle_sets(integer) to authenticated;
