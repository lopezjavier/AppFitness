-- T-313 (US-141, ADR-110): the weekly progress builder, the `progress` object of the weekly report snapshot v2.
-- Rules: planning/07-coach-design.md section 3 "Weekly report: progress first" (0)-(5) and "Stored weeks and late data".
-- Contract and plan-level notes (R6 one pace, R8 cost, R9 "as of the week's end"): tasks/todo.md "## T-313" and
-- "### T-310 split".
--
-- Layout (ownership by function):
--   1. Twins and helpers.
--   2. Section functions: strength, lifts, records, context (run A); weight, waist, phase run (run B).
--   3. The composer coach_week_progress_for.
--   4. Live path: the stall-card trigger in coach_week_summary_week and coach_block_review_build (recreated),
--      coach_week_summary and record_paused_week_report (run B, appended at the end).
--
-- Security: every function of parts 1-3 reads with an explicit p_uid and only the composer is security definer; none
-- needs a JWT (T-314 calls the composer from a migration). All of them are internal: execute is revoked from public,
-- anon and authenticated (Supabase default privileges would grant it), so they run only inside the composer or the
-- definer live path, as owner, which bypasses RLS; that is why every read below filters by p_uid. Part 4 keeps the
-- auth.uid() scoping of the functions it recreates.

-- ---------------------------------------------------------------------------
-- 1. Twins and helpers
-- ---------------------------------------------------------------------------

-- Twin of warm_up_included() for an explicit user.
create function public.coach_warm_up_included_for(p_uid uuid)
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(
    (select tp.warm_up_included from public.training_preferences tp where tp.user_id = p_uid),
    false);
$$;

revoke execute on function public.coach_warm_up_included_for(uuid) from public, anon, authenticated;

-- Twin of coach_stalled_lifts(p_plan_id, p_week) (latest body: 20270110 + the 20270126 window-end patch) for an explicit
-- user; coach_stalled_lifts itself is unchanged.
create function public.coach_stalled_lifts_for(p_uid uuid, p_plan_id uuid, p_week integer)
returns table (lift_id uuid, week_starts date[])
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := p_uid;
  v_ok    boolean;
  v_ws    date[];
begin
  select true into v_ok from public.coach_plans p where p.id = p_plan_id and p.user_id = v_uid;
  if v_ok is null or p_week is null or p_week < 1 then
    return;
  end if;

  select array_agg(x.ws order by x.ws) into v_ws
  from (select t.start_date as ws
        from public.coach_plan_trained_weeks_for(v_uid, p_plan_id) t
        where t.week <= p_week
          and not exists (select 1 from public.coach_plan_sessions s
                          where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = t.week and s.deload)
        order by t.week desc
        limit 3) x;
  if coalesce(cardinality(v_ws), 0) < 3 then
    return;
  end if;

  return query
  select b.eid, v_ws
  from (select st.exercise_id as eid,
               max(public.coach_e1rm(st.weight_kg, st.reps)) filter (where w.i = 1) as w1,
               max(public.coach_e1rm(st.weight_kg, st.reps)) filter (where w.i = 2) as w2,
               max(public.coach_e1rm(st.weight_kg, st.reps)) filter (where w.i = 3) as w3
        from public.workout_sessions s
        join public.workout_sets st on st.session_id = s.id
        join public.exercises e on e.id = st.exercise_id
        join unnest(v_ws) with ordinality as w(ws, i)
          on (s.started_at at time zone 'utc')::date between w.ws and public.coach_plan_window_end_for(v_uid, p_plan_id, w.ws)
        where s.user_id = v_uid and s.status = 'completed'
          and st.completed_at is not null
          and (st.set_type <> 'warm_up' or public.coach_warm_up_included_for(v_uid))
          and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                           'horizontal_pull', 'vertical_pull')
        group by st.exercise_id) b
  where b.w1 is not null and b.w2 is not null and b.w3 is not null
    and not (greatest(b.w2, b.w3) > b.w1)
  order by b.eid;
end;
$$;

revoke execute on function public.coach_stalled_lifts_for(uuid, uuid, integer) from public, anon, authenticated;

-- The discomforts open at the end of day p_end (UTC), `07` section 7: logged before p_end + 1 00:00 UTC and not
-- resolved before it; levels 1-3 also within 7 days of being logged. The newest open row per exercise.
create function public.coach_open_discomforts_for(p_uid uuid, p_end date)
returns table (exercise_id uuid, id uuid)
language sql
stable
security invoker
set search_path = ''
as $$
  select distinct on (d.exercise_id) d.exercise_id, d.id
  from public.exercise_discomforts d
  cross join (select (p_end + 1)::timestamp at time zone 'utc' as ts) t
  where d.user_id = p_uid
    and d.logged_at < t.ts
    and (d.resolved_at is null or d.resolved_at >= t.ts)
    and (d.level >= 4 or t.ts < d.logged_at + interval '7 days')
  order by d.exercise_id, d.logged_at desc, d.id desc;
$$;

revoke execute on function public.coach_open_discomforts_for(uuid, date) from public, anon, authenticated;

-- The block's completed sets up to p_end, one row per set: completed sessions of p_uid with a UTC date in
-- [coalesce(training_start, block_start), p_end], their plan week (windows of coach_plan_windows_for as of p_end, so
-- extensions are honoured; null outside a plan week) and its deload flag, and the exercise flags. Every set type is
-- returned: each section applies its own set filter.
create function public.coach_progress_sessions_for(p_uid uuid, p_plan_id uuid, p_end date)
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
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  win as (
    select w.week, w.start_date, w.end_date from public.coach_plan_windows_for(p_uid, p_plan_id, p_end) w
  ),
  dl as (
    select s.week, bool_or(s.deload) as deload
    from public.coach_plan_sessions s
    where s.plan_id = p_plan_id and s.user_id = p_uid
    group by s.week
  )
  select s.id, s.started_at, (s.started_at at time zone 'utc')::date, win.week, coalesce(dl.deload, false),
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
  from plan
  join public.workout_sessions s
    on s.user_id = p_uid and s.status = 'completed'
   and (s.started_at at time zone 'utc')::date between plan.s0 and p_end
  join public.workout_sets st on st.session_id = s.id and st.completed_at is not null
  join public.exercises e on e.id = st.exercise_id
  left join win on (s.started_at at time zone 'utc')::date between win.start_date and win.end_date
  left join dl on dl.week = win.week;
$$;

revoke execute on function public.coach_progress_sessions_for(uuid, uuid, date) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2. Section functions: (p_uid, p_plan_id, p_week, p_start, p_end) -> jsonb
-- ---------------------------------------------------------------------------

-- Strength per muscle group, `07` section 3 "Weekly report: progress first" (1): baseline, compared session, states
-- and their order. Working sets only; grouped by the exercise's main muscle.
create function public.coach_progress_strength_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with ws as (
    select x.*, max(x.weight_kg) over (partition by x.exercise_id, x.session_id) as top
    from public.coach_progress_sessions_for(p_uid, p_plan_id, p_end) x
    where x.h1 and x.set_type = 'working'
  ),
  -- One row per (exercise, session): top working load and, at that load, the reps in set order, the highest RPE,
  -- the lowest reps and the lowest margin over the set's rep_min (ranged: every set at that load has a rep_min).
  ses as (
    select ws.exercise_id, ws.session_id, min(ws.started_at) as started_at, min(ws.day) as day,
           bool_or(ws.deload) as deload, max(ws.top) as top,
           (array_agg(ws.rep_min order by ws.exercise_position, ws.set_index))[1] as rmin,
           (array_agg(ws.rep_max order by ws.exercise_position, ws.set_index))[1] as rmax,
           array_agg(ws.reps order by ws.exercise_position, ws.set_index) filter (where ws.weight_kg = ws.top) as reps,
           max(ws.rpe) filter (where ws.weight_kg = ws.top) as rpe,
           min(ws.reps) filter (where ws.weight_kg = ws.top) as low_reps,
           bool_and(ws.rep_min is not null) filter (where ws.weight_kg = ws.top) as ranged,
           min(ws.reps - ws.rep_min) filter (where ws.weight_kg = ws.top) as margin
    from ws
    group by ws.exercise_id, ws.session_id
  ),
  seq as (
    select ses.*, lag(ses.day) over w as prev_day, lag(ses.rmin) over w as prev_min, lag(ses.rmax) over w as prev_max,
           row_number() over w as rn
    from ses
    window w as (partition by ses.exercise_id order by ses.started_at, ses.session_id)
  ),
  -- Segment number: a new segment starts on a rep range change or a gap of 14 days or more.
  seg as (
    select seq.*,
           count(*) filter (where seq.rn > 1
                              and (seq.day - seq.prev_day >= 14
                                   or seq.rmin is distinct from seq.prev_min
                                   or seq.rmax is distinct from seq.prev_max))
             over (partition by seq.exercise_id order by seq.started_at, seq.session_id) as segno
    from seq
  ),
  -- Rows: exercises with a non-deload session in the block to date; the current segment is the latest one.
  cur as (
    select seg.exercise_id, max(seg.segno) as segno
    from seg
    group by seg.exercise_id
    having bool_or(not seg.deload)
  ),
  base as (
    select distinct on (s.exercise_id) s.*
    from seg s
    join cur c on c.exercise_id = s.exercise_id and c.segno = s.segno
    where not s.deload
    order by s.exercise_id, s.started_at, s.session_id
  ),
  -- Compared session: the best non-deload session of the report week after the baseline, else the latest
  -- non-deload session of the block to date after it. Week 1 is the base: nothing is compared.
  cmp as (
    select distinct on (s.exercise_id) s.*
    from seg s
    join base b on b.exercise_id = s.exercise_id and b.segno = s.segno
    where p_week > 1
      and not s.deload
      and (s.started_at, s.session_id) > (b.started_at, b.session_id)
    order by s.exercise_id,
             (s.day between p_start and p_end) desc,
             case when s.day between p_start and p_end then s.top end desc nulls last,
             case when s.day between p_start and p_end then (select sum(r) from unnest(s.reps) r) end desc nulls last,
             s.started_at desc, s.session_id desc
  ),
  -- Open discomfort as of the week's end: the newest open row per exercise.
  disc as (
    select d.exercise_id, d.id from public.coach_open_discomforts_for(p_uid, p_end) d
  ),
  stl as (
    select st.lift_id from public.coach_stalled_lifts_for(p_uid, p_plan_id, p_week) st
  ),
  cmpd as (
    select c.exercise_id, e.muscle_group, coalesce(e.key, e.id::text) as xkey,
           coalesce(e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                                 'horizontal_pull', 'vertical_pull'), false) as main_lift,
           b.day as b_day, b.top as b_top, b.reps as b_reps, b.rpe as b_rpe,
           m.day as m_day, m.top as m_top, m.reps as m_reps, m.rpe as m_rpe,
           case
             when b.exercise_id is null or m.exercise_id is null then 'uncompared'
             when m.top < b.top then 'down'
             when m.top > b.top then
               case when (case when m.ranged then m.margin >= 0 else m.low_reps >= b.low_reps end)
                         and (m.rpe is null or b.rpe is null or m.rpe <= b.rpe + 1)
                    then 'up' else 'same' end
             when t.m_sum > t.b_sum then
               case when m.rpe is null or b.rpe is null or m.rpe <= b.rpe + 1 then 'up' else 'same' end
             when t.m_sum < t.b_sum then 'down'
             else 'same'
           end as s1
    from cur c
    join public.exercises e on e.id = c.exercise_id
    left join base b on b.exercise_id = c.exercise_id
    left join cmp m on m.exercise_id = c.exercise_id
    -- Reps at the top load compared set by set over the sets both sessions share.
    cross join lateral (select least(cardinality(b.reps), cardinality(m.reps)) as n) k
    cross join lateral (select (select sum(r) from unnest(m.reps[1:k.n]) r) as m_sum,
                               (select sum(r) from unnest(b.reps[1:k.n]) r) as b_sum) t
  ),
  -- State order: with an open discomfort, 'up' only when the stall test does not hold, else 'paused'; without one, a
  -- stalled main lift is 'stalled'.
  stx as (
    select x.*, d.id as disc_id,
           x.main_lift and exists (select 1 from stl where stl.lift_id = x.exercise_id) as stall
    from cmpd x
    left join disc d on d.exercise_id = x.exercise_id
  ),
  fin as (
    select x.*,
           case when x.disc_id is not null and (x.s1 <> 'up' or x.stall) then 'paused'
                when x.stall then 'stalled'
                else x.s1 end as state,
           case when x.disc_id is not null and (x.s1 <> 'up' or x.stall) then x.disc_id end as discomfort_id
    from stx x
  ),
  grp as (
    select f.muscle_group,
           jsonb_agg(jsonb_build_object(
             'exercise_id', f.exercise_id,
             'main_lift', f.main_lift,
             'state', f.state,
             'baseline', case when f.b_day is not null then
                           jsonb_build_object('date', f.b_day, 'load_kg', f.b_top, 'reps', to_jsonb(f.b_reps), 'rpe', f.b_rpe)
                         end,
             'best', case when f.m_day is not null then
                       jsonb_build_object('date', f.m_day, 'load_kg', f.m_top, 'reps', to_jsonb(f.m_reps), 'rpe', f.m_rpe)
                     end,
             'discomfort_id', f.discomfort_id)
             order by f.main_lift desc, f.xkey, f.exercise_id) as exercises
    from fin f
    group by f.muscle_group
  )
  select jsonb_build_object(
    'block_week', p_week,
    'groups', coalesce((select jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'exercises', g.exercises)
                                         order by g.muscle_group)
                        from grp g), '[]'::jsonb));
$$;

revoke execute on function public.coach_progress_strength_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- Main lifts, `07` section 3 "Weekly report: progress first" (2): weekly best e1RM over working sets for plan weeks
-- 1..p_week, at most 6 lifts (most sessions in the block first, then key, as coach_summary).
create function public.coach_progress_lifts_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with ws as (
    select x.* from public.coach_progress_sessions_for(p_uid, p_plan_id, p_end) x
    where x.main_lift and x.set_type = 'working'
  ),
  lift as (
    select ws.exercise_id, ws.xkey, count(distinct ws.session_id) as n
    from ws
    group by ws.exercise_id, ws.xkey
    order by count(distinct ws.session_id) desc, ws.xkey
    limit 6
  ),
  wk as (
    select w.week, coalesce(bool_or(s.deload), false) as deload
    from public.coach_plan_windows_for(p_uid, p_plan_id, p_end) w
    left join public.coach_plan_sessions s on s.plan_id = p_plan_id and s.user_id = p_uid and s.week = w.week
    where w.week <= p_week
    group by w.week
  ),
  pts as (
    select l.exercise_id, wk.week, wk.deload,
           (select max(public.coach_e1rm(ws.weight_kg, ws.reps)) from ws
            where ws.exercise_id = l.exercise_id and ws.week = wk.week) as e
    from lift l cross join wk
  ),
  -- Counted weeks: non-deload weeks with a value, oldest first.
  cnt as (
    select l.exercise_id,
           coalesce((select array_agg(p.e order by p.week) from pts p
                     where p.exercise_id = l.exercise_id and not p.deload and p.e is not null), '{}') as v
    from lift l
  ),
  st as (
    select l.exercise_id, l.n, l.xkey, c.v, cardinality(c.v) as k,
           case
             when cardinality(c.v) < 3 then 'provisional'
             -- A lift with an open discomfort never reads stalled (`07` section 3 (1) state order).
             when exists (select 1 from public.coach_stalled_lifts_for(p_uid, p_plan_id, p_week) s
                          where s.lift_id = l.exercise_id)
                  and not exists (select 1 from public.coach_open_discomforts_for(p_uid, p_end) d
                                  where d.exercise_id = l.exercise_id) then 'stalled'
             when greatest(c.v[cardinality(c.v) - 1], c.v[cardinality(c.v)]) > c.v[cardinality(c.v) - 2]
                  and c.v[cardinality(c.v)] >= 1.04 * c.v[1] then 'up'
             else 'margin'
           end as state
    from lift l
    join cnt c on c.exercise_id = l.exercise_id
  )
  select coalesce(jsonb_agg(jsonb_build_object(
           'exercise_id', s.exercise_id,
           'weeks', coalesce((select jsonb_agg(jsonb_build_object('week', p.week, 'e1rm_kg', p.e, 'deload', p.deload)
                                               order by p.week)
                              from pts p where p.exercise_id = s.exercise_id), '[]'::jsonb),
           'state', s.state,
           'change_kg', case when s.state in ('up', 'margin') then s.v[s.k] - s.v[1] end,
           'change_pct', case when s.state in ('up', 'margin') then round((s.v[s.k] / s.v[1] - 1) * 100, 1) end)
           order by s.n desc, s.xkey), '[]'::jsonb)
  from st s;
$$;

revoke execute on function public.coach_progress_lifts_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- Records of the week, `07` section 3 "Weekly report: progress first" (3): each session of the week against every
-- earlier completed session of the exercise (any date up to p_end; set filter of `04` section 4.6 with the warm-up
-- preference), strictly greater; at most 3, main lifts first, then the largest relative gain.
create function public.coach_progress_records_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with pref as (
    select public.coach_warm_up_included_for(p_uid) as warm
  ),
  sx as (
    select s.id as sid, s.started_at, (s.started_at at time zone 'utc')::date as day, st.exercise_id,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm,
           max(st.weight_kg) filter (where st.weight_kg > 0 and st.reps >= 1) as heaviest
    from pref
    join public.workout_sessions s on s.user_id = p_uid and s.status = 'completed'
                                  and (s.started_at at time zone 'utc')::date <= p_end
    join public.workout_sets st on st.session_id = s.id
    where st.completed_at is not null
      and (st.set_type <> 'warm_up' or pref.warm)
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
  select coalesce(jsonb_agg(jsonb_build_object('exercise_id', b.exercise_id, 'kind', b.kind, 'value_kg', b.value_kg,
                                               'previous_kg', b.previous_kg, 'main_lift', b.main_lift)
                            order by b.main_lift desc, b.gain desc, b.exercise_id, b.kind), '[]'::jsonb)
  from best3 b;
$$;

revoke execute on function public.coach_progress_records_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- Context line, `07` section 3 "Weekly report: progress first" (1) "Graphic": sessions as in v1 (coach_plan_weeks),
-- completed non-warm-up sets of the week's linked sessions, and the planned sets of the week's plan sessions; null
-- when a session of the week has no routine or its routine's exercises differ from the plan's.
create function public.coach_progress_context_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with ps as (
    select s.id, s.session_id, s.routine_id, s.exercises
    from public.coach_plan_sessions s
    where s.plan_id = p_plan_id and s.user_id = p_uid and s.week = p_week
  ),
  planned as (
    select ps.id,
           coalesce((select sum(case jsonb_typeof(e.value->'sets')
                                  when 'number' then (e.value->>'sets')::int
                                  when 'array' then (select count(*)::int from jsonb_array_elements(e.value->'sets') x
                                                     where coalesce(x.value->>'type', 'working') in ('working', 'drop'))
                                  else 0 end)
                     from jsonb_array_elements(ps.exercises) e), 0) as sets,
           ps.routine_id is not null
             and array(select distinct (e.value->>'exercise_id')::uuid
                       from jsonb_array_elements(ps.exercises) e order by 1)
               = array(select distinct re.exercise_id
                       from public.routine_exercises re
                       join public.routines r on r.id = re.routine_id and r.user_id = p_uid
                       where re.routine_id = ps.routine_id order by 1) as matches
    from ps
  )
  select jsonb_build_object(
    'sessions_done', (select count(*)
                      from ps
                      join public.workout_sessions w on w.id = ps.session_id and w.user_id = p_uid and w.status = 'completed'
                                                    and (w.started_at at time zone 'utc')::date <= p_end)::int,
    'sessions_planned', (select count(*) from ps)::int,
    'sets_done', (select count(*)
                  from ps
                  join public.workout_sessions w on w.id = ps.session_id and w.user_id = p_uid and w.status = 'completed'
                                                and (w.started_at at time zone 'utc')::date <= p_end
                  join public.workout_sets st on st.session_id = w.id
                  where st.completed_at is not null and st.set_type <> 'warm_up')::int,
    'sets_planned', (select case when bool_and(p.matches) then sum(p.sets)::int end from planned p));
$$;

revoke execute on function public.coach_progress_context_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- Every counted plan week of p_uid's blocks up to the report week, oldest first: earlier blocks (accepted or
-- superseded, with sessions, before the report plan) give weeks 1..their counted weeks (as coach_block_counted_weeks,
-- windows as of the day before the next block starts); the report plan gives weeks 1..p_week (windows as of p_end).
-- in_run marks the consecutive blocks ending at the report plan with its calorie phase (`07` section 12.9, the walk of
-- coach_weeks_in_phase, but as of the report's plan and week instead of today).
create function public.coach_phase_run_weeks_for(p_uid uuid, p_plan_id uuid, p_week integer, p_end date)
returns table (plan_id uuid, week integer, start_date date, end_date date, in_run boolean, ord integer)
language sql
stable
security invoker
set search_path = ''
as $$
  with rp as (
    select p.id, p.block_start, p.created_at, coalesce(p.nutrition->>'phase', p.phase::text) as ph
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  blk as (
    select p.id, p.block_start, p.created_at, p.id = rp.id as is_report,
           coalesce(p.nutrition->>'phase', p.phase::text) = rp.ph as same_phase,
           lead(coalesce(p.training_start, p.block_start)) over (order by p.block_start, p.created_at) as next_start
    from public.coach_plans p
    cross join rp
    where p.user_id = p_uid
      and (p.id = rp.id
           or (p.status in ('superseded', 'accepted')
               and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id and s.user_id = p_uid)
               and (p.block_start, p.created_at) < (rp.block_start, rp.created_at)))
  ),
  chain as (
    select b.*,
           bool_and(b.same_phase) over (order by b.block_start desc, b.created_at desc
                                        rows between unbounded preceding and current row) as in_run,
           case when b.is_report then p_end else b.next_start - 1 end as as_of,
           case when b.is_report then p_week
                else public.coach_plan_week_of_date_for(p_uid, b.id, b.next_start - 1) end as last_week
    from blk b
  )
  select c.id, w.week, w.start_date, w.end_date, c.in_run,
         (row_number() over (order by c.block_start, c.created_at, w.week))::int
  from chain c
  cross join lateral public.coach_plan_windows_for(p_uid, c.id, c.as_of) w
  where w.week <= c.last_week
  order by c.block_start, c.created_at, w.week;
$$;

revoke execute on function public.coach_phase_run_weeks_for(uuid, uuid, integer, date) from public, anon, authenticated;

-- Weight, `07` section 3 "Weekly report: progress first" (4): plan-week averages of the daily values (`04` section 3.1,
-- UTC days) with the 3-weigh-in gate, the change since the phase run started and the sparkline series. The trend is
-- WeightPaceFit.compute (R6, `04` section 7.4) with today = p_end and the weeks of the phase run: ISO-week averages
-- over min(phase weeks, 8) weeks ending the ISO week of p_end, so the report and the app show one pace.
create function public.coach_progress_weight_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select p.nutrition, p.outcomes from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  rw as (
    select r.* from public.coach_phase_run_weeks_for(p_uid, p_plan_id, p_week, p_end) r
  ),
  -- Trend window: ISO weeks ending the week of p_end, as many as the phase run has (1 to 8).
  tw as (
    select x.mon - 7 * (x.n - 1) as ws
    from (select date_trunc('week', p_end::timestamp)::date as mon,
                 greatest(1, least((select count(*) from rw where rw.in_run), 8))::int as n) x
  ),
  daily as (
    select (b.measured_at at time zone 'utc')::date as day, avg(b.weight_kg) as kg
    from public.body_measurements b
    cross join (select least(p_start, (select min(rw.start_date) from rw), (select tw.ws from tw)) as d0) lim
    where b.user_id = p_uid
      and b.measured_at >= lim.d0::timestamp at time zone 'utc'
      and b.measured_at < (p_end + 1)::timestamp at time zone 'utc'
    group by 1
  ),
  wk as (
    select rw.*, a.avg_kg, a.days
    from rw
    cross join lateral (select round(avg(d.kg), 2) as avg_kg, count(*)::int as days
                        from daily d where d.day between rw.start_date and rw.end_date) a
  ),
  cur as (
    select round(avg(d.kg), 2) as avg_kg, count(*)::int as days from daily d where d.day between p_start and p_end
  ),
  prev as (
    select wk.avg_kg, wk.days from wk
    where wk.ord = (select r.ord from rw r where r.plan_id = p_plan_id and r.week = p_week) - 1
  ),
  qual as (
    select wk.* from wk where wk.in_run and wk.days >= 3
  ),
  ser as (
    select wk.* from wk where wk.in_run and wk.days >= 1 order by wk.ord desc limit 8
  ),
  -- Least squares of the ISO-week averages against the week index, with the t interval from 3 points.
  pts as (
    select ((d.day - tw.ws) / 7)::float8 as x, avg(d.kg)::float8 as y
    from daily d cross join tw
    where d.day between tw.ws and p_end
    group by (d.day - tw.ws) / 7
  ),
  m as (
    select count(*)::int as n, avg(pts.x) as mx, avg(pts.y) as my from pts
  ),
  fit as (
    select m.n, m.mx, m.my, s.sxx,
           case when m.n >= 2 then s.sxy / s.sxx end as slope
    from m
    cross join lateral (select sum((pts.x - m.mx) ^ 2) as sxx, sum((pts.x - m.mx) * (pts.y - m.my)) as sxy from pts) s
  ),
  band as (
    select fit.n, fit.slope,
           case when fit.n >= 3 then
             (array[12.706, 4.303, 3.182, 2.776, 2.571, 2.447])[fit.n - 2]::float8
               * sqrt((select sum((pts.y - (fit.my + fit.slope * (pts.x - fit.mx))) ^ 2) from pts) / (fit.n - 2))
               / sqrt(fit.sxx)
           end as half
    from fit
  )
  select jsonb_build_object(
    'avg_kg', cur.avg_kg,
    'days', cur.days,
    'prev_avg_kg', prev.avg_kg,
    'prev_days', coalesce(prev.days, 0),
    'change_kg', case when cur.days >= 3 and prev.days >= 3 then round(cur.avg_kg - prev.avg_kg, 1) end,
    'start_avg_kg', (select q.avg_kg from qual q order by q.ord limit 1),
    'phase_weeks', (select count(*) from rw where rw.in_run)::int,
    'change_since_start_kg',
      case when (select count(*) from qual) >= 2 then
        round((select q.avg_kg from qual q order by q.ord desc limit 1)
              - (select q.avg_kg from qual q order by q.ord limit 1), 1)
      end,
    'series', coalesce((select jsonb_agg(jsonb_build_object('start_date', s.start_date, 'avg_kg', s.avg_kg, 'days', s.days)
                                         order by s.ord)
                        from ser s), '[]'::jsonb),
    'outcome', (select jsonb_build_object(
                         'kg', nullif(o.value->>'target_value', '')::numeric,
                         'deadline', (select max(w.end_date) from public.coach_plan_windows_for(p_uid, p_plan_id, p_end) w))
                from jsonb_array_elements(plan.outcomes) with ordinality o(value, i)
                where o.value->>'kind' = 'weight'
                order by o.i
                limit 1),
    'trend', jsonb_build_object(
               'slope', round(band.slope::numeric, 3),
               'low', round((band.slope - band.half)::numeric, 3),
               'high', round((band.slope + band.half)::numeric, 3),
               'weeks', band.n),
    'rate_kg_week', to_jsonb(nullif(plan.nutrition->>'rate_kg_week', '')::numeric))
  from plan
  cross join cur
  cross join band
  left join prev on true;
$$;

revoke execute on function public.coach_progress_weight_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- Waist, `07` section 3 "Weekly report: progress first" (5): only with a waist measurement in the week; the latest one
-- of the week against the latest at least 28 days older (UTC days); no pair, null.
create function public.coach_progress_waist_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with cur as (
    select c.value_cm, (c.measured_at at time zone 'utc')::date as day
    from public.body_circumferences c
    where c.user_id = p_uid and c.site = 'waist'
      and c.measured_at >= p_start::timestamp at time zone 'utc'
      and c.measured_at < (p_end + 1)::timestamp at time zone 'utc'
    order by c.measured_at desc, c.id desc
    limit 1
  ),
  ref as (
    select c.value_cm, (c.measured_at at time zone 'utc')::date as day
    from public.body_circumferences c
    cross join cur
    where c.user_id = p_uid and c.site = 'waist'
      and c.measured_at < (cur.day - 27)::timestamp at time zone 'utc'
    order by c.measured_at desc, c.id desc
    limit 1
  )
  select jsonb_build_object('cm', cur.value_cm, 'date', cur.day, 'ref_cm', ref.value_cm, 'ref_date', ref.day,
                            'change_cm', cur.value_cm - ref.value_cm)
  from cur cross join ref;
$$;

revoke execute on function public.coach_progress_waist_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3. The composer
-- ---------------------------------------------------------------------------

-- The `progress` object of the snapshot v2 contract as of p_end for p_uid's plan p_plan_id (null when the plan is not
-- p_uid's). `final` is always false here: callers set it.
create function public.coach_week_progress_for(p_uid uuid, p_plan_id uuid, p_week integer, p_start date, p_end date)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_plan public.coach_plans;
begin
  if p_uid is null or p_week is null or p_start is null or p_end is null then
    return null;
  end if;
  select * into v_plan from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid;
  if not found then
    return null;
  end if;

  return jsonb_build_object(
    'as_of', p_end,
    'refreshed_at', now(),
    'final', false,
    'intention', v_plan.intention,
    'phase', coalesce(v_plan.nutrition->>'phase', v_plan.phase::text),
    -- Gates of `07` section 2: fewer than 3 weigh-in days in the week, fewer than 5 complete diary days.
    'missing', to_jsonb(array_remove(array[
      case when (select count(distinct (b.measured_at at time zone 'utc')::date)
                 from public.body_measurements b
                 where b.user_id = p_uid
                   and b.measured_at >= p_start::timestamp at time zone 'utc'
                   and b.measured_at < (p_end + 1)::timestamp at time zone 'utc') < 3 then 'weigh_ins' end,
      case when (select count(*) from public.coach_complete_days(p_uid, p_end)) < 5 then 'diary' end], null)),
    'context', public.coach_progress_context_for(p_uid, p_plan_id, p_week, p_start, p_end),
    'strength', public.coach_progress_strength_for(p_uid, p_plan_id, p_week, p_start, p_end),
    'lifts', public.coach_progress_lifts_for(p_uid, p_plan_id, p_week, p_start, p_end),
    'records', public.coach_progress_records_for(p_uid, p_plan_id, p_week, p_start, p_end),
    'weight', public.coach_progress_weight_for(p_uid, p_plan_id, p_week, p_start, p_end),
    'waist', public.coach_progress_waist_for(p_uid, p_plan_id, p_week, p_start, p_end));
end;
$$;

revoke execute on function public.coach_week_progress_for(uuid, uuid, integer, date, date)
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4. Live path: the v1 week builder and the block review build (stall card trigger), coach_week_summary and
--    record_paused_week_report
-- ---------------------------------------------------------------------------

-- Sets progress.final on every earlier non-final v2 row of p_uid (any plan; period_start before p_before) after one
-- last rebuild as of its own week (`07` section 3 "Stored weeks and late data", Q17). v1 and paused rows are untouched.
create function public.coach_finalise_week_reports_for(p_uid uuid, p_before date)
returns void
language sql
security invoker
set search_path = ''
as $$
  update public.coach_week_reports r
  set snapshot = jsonb_set(r.snapshot, '{progress}',
                           coalesce(public.coach_week_progress_for(r.user_id, r.plan_id, r.week, r.period_start,
                                                                   r.period_end),
                                    r.snapshot->'progress')
                             || '{"final": true}'::jsonb)
  where r.user_id = p_uid and not r.paused
    and r.snapshot->>'schema_version' = '2' and r.snapshot ? 'progress'
    and not coalesce((r.snapshot->'progress'->>'final')::boolean, false)
    and r.period_start < p_before;
$$;

revoke execute on function public.coach_finalise_week_reports_for(uuid, date) from public, anon, authenticated;

-- The v1 week builder (latest body, read with pg_get_functiondef: 20270113 + 20270114), unchanged except the stall
-- card (`stalls`), `07` section 3 "Block follow-up" (7): a lift stalled this week gets its card only with no open
-- discomfort at the week's end and when no earlier week of the same unbroken run of stalled weeks was stalled with no
-- open discomfort (its card was shown then). The run is walked back with coach_stalled_lifts week by week; it replaces
-- the single "not stalled one plan week earlier" test, which it equals when no discomfort is open.
create or replace function public.coach_week_summary_week(p_today date)
returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  v_uid       uuid := public.coach_guard(p_today);
  v_plan      public.coach_plans;
  v_pw        jsonb;
  v_w         jsonb;
  v_week      integer;
  v_start     date;
  v_end       date;
  v_gate      jsonb;
  v_trend     jsonb;
  v_avg       numeric;
  v_diary     boolean;
  v_from_ts   timestamptz;
  v_to_ts     timestamptz;
  v_groups    jsonb;
  v_changes   jsonb;
  v_rec       jsonb;
  v_stalls    jsonb;
  v_sdiag     jsonb;
  v_cand      uuid[];
  v_live      uuid[];
  v_shown     uuid[];
  v_prev      uuid[];
  v_k         integer;
  v_end_k     date;
  v_days      integer;
  v_nut       jsonb;
  v_decisions jsonb;
  v_diag      jsonb;
  v_avg_kcal  numeric;
  v_avg_prot  numeric;
  v_tgt_kcal  numeric;
  v_tgt_prot  numeric;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found then
    return null;
  end if;

  v_pw := public.coach_plan_weeks(v_plan.id, p_today);
  select w into v_w
  from jsonb_array_elements(v_pw) w
  where w->>'phase' = 'past'
  order by (w->>'week')::int desc
  limit 1;
  if v_w is null then
    return null;
  end if;

  v_week := (v_w->>'week')::int;
  v_start := (v_w->>'start_date')::date;
  v_end := (v_w->>'end_date')::date;
  v_gate := public.coach_week_gate(v_end);
  select exists (select 1 from public.food_log_entries
                 where user_id = v_uid and log_date between v_start and v_end) into v_diary;

  if (v_w->>'completed')::int = 0 and (v_gate->>'weigh_in_days')::int = 0 and not v_diary then
    return null;
  end if;

  select round(avg(d.w), 2) into v_avg
  from (select avg(weight_kg) as w
        from public.body_measurements
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and v_end
        group by (measured_at at time zone 'UTC')::date) d;
  v_trend := public.coach_weight_trend(v_end);

  select coalesce(jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets)
                            order by g.sets desc, g.muscle_group), '[]'::jsonb)
  into v_groups
  from public.muscle_group_sets(v_start, v_end, true) g;

  v_from_ts := v_start::timestamp at time zone 'UTC';
  v_to_ts := (v_end + 1)::timestamp at time zone 'UTC';

  -- Same rows the Trainer list reads (select * from coach_changes), newest first.
  select coalesce(jsonb_agg(to_jsonb(c) order by c.created_at desc, c.id), '[]'::jsonb)
  into v_changes
  from public.coach_changes c
  where c.user_id = v_uid and c.plan_id = v_plan.id and c.created_at >= v_from_ts and c.created_at < v_to_ts;

  select jsonb_build_object('id', r.id, 'status', r.status, 'reason', r.reason, 'payload', r.payload,
                            'created_at', r.created_at, 'decided_at', r.decided_at)
  into v_rec
  from public.coach_recommendations r
  where r.user_id = v_uid and r.plan_id = v_plan.id and r.kind = 'calorie_change'
    and r.created_at >= v_from_ts and r.created_at < v_to_ts
  order by r.created_at desc, r.id
  limit 1;

  -- Stall card: candidates are this week's stalled lifts with no open discomfort at its end; walking back the run of
  -- stalled weeks, a lift stalled with no open discomfort at that week's end already had its card.
  v_sdiag := public.coach_stall_diagnosis(v_plan.id, v_week);
  select coalesce(array_agg((s.e->>'exercise_id')::uuid), '{}'::uuid[]) into v_cand
  from jsonb_array_elements(v_sdiag) s(e)
  where not exists (select 1 from public.coach_open_discomforts_for(v_uid, v_end) d
                    where d.exercise_id = (s.e->>'exercise_id')::uuid);
  v_live := v_cand;
  v_k := v_week - 1;
  while v_k >= 1 and cardinality(v_live) > 0 loop
    select coalesce(array_agg(p.lift_id), '{}'::uuid[]) into v_prev from public.coach_stalled_lifts(v_plan.id, v_k) p;
    select (w->>'end_date')::date into v_end_k from jsonb_array_elements(v_pw) w where (w->>'week')::int = v_k;
    v_live := array(select x from unnest(v_live) x where x = any (v_prev));
    v_shown := array(select x from unnest(v_live) x
                     where not exists (select 1 from public.coach_open_discomforts_for(v_uid, v_end_k) d
                                       where d.exercise_id = x));
    v_cand := array(select x from unnest(v_cand) x where not (x = any (v_shown)));
    v_live := array(select x from unnest(v_live) x where not (x = any (v_shown)));
    v_k := v_k - 1;
  end loop;
  select coalesce(jsonb_agg(s.e order by s.ord), '[]'::jsonb) into v_stalls
  from jsonb_array_elements(v_sdiag) with ordinality s(e, ord)
  where (s.e->>'exercise_id')::uuid = any (v_cand);

  -- Nutrition: calorie decisions of the week (created or decided inside it), newest first.
  select coalesce(jsonb_agg(jsonb_build_object(
           'id', r.id, 'status', r.status,
           'from_kcal', (r.payload->>'from_kcal')::int, 'to_kcal', (r.payload->>'to_kcal')::int,
           'payload', r.payload, 'reason', r.reason,
           'created_at', r.created_at, 'decided_at', r.decided_at)
         order by r.created_at desc, r.id), '[]'::jsonb)
  into v_decisions
  from public.coach_recommendations r
  where r.user_id = v_uid and r.plan_id = v_plan.id and r.kind = 'calorie_change'
    and ((r.created_at >= v_from_ts and r.created_at < v_to_ts)
         or (r.decided_at >= v_from_ts and r.decided_at < v_to_ts));

  v_days := (v_gate->>'complete_days')::int;
  if v_days < 5 then
    v_nut := jsonb_build_object('complete_days', v_days, 'gate_ok', false, 'decisions', v_decisions);
  else
    select avg(c.kcal), avg(c.protein_g) into v_avg_kcal, v_avg_prot
    from public.coach_complete_days(v_uid, v_end) c;

    select avg(g.kcal), avg(g.protein_g) into v_tgt_kcal, v_tgt_prot
    from public.coach_complete_days(v_uid, v_end) c
    join public.coach_daily_goals(v_uid, v_plan.id, p_today, v_end - 6, v_end) g on g.log_date = c.log_date;

    v_nut := jsonb_build_object(
      'complete_days', v_days,
      'gate_ok', true,
      'avg_kcal', round(v_avg_kcal)::int,
      'avg_protein_g', round(v_avg_prot, 1),
      'target_kcal', round(v_tgt_kcal)::int,
      'target_protein_g', round(v_tgt_prot)::int,
      'protein_g_per_kg', case when v_avg > 0 then round(round(v_avg_prot, 1) / v_avg, 2) end,
      'decisions', v_decisions);
  end if;

  v_diag := public.coach_nutrition_diagnosis(v_uid, v_plan.id, v_end, p_today);
  if v_diag is not null then
    v_nut := v_nut || jsonb_build_object('diagnosis', v_diag);
  end if;

  return jsonb_build_object(
    'plan_id', v_plan.id,
    'week', v_week,
    'start_date', v_start,
    'end_date', v_end,
    'seen', exists (select 1 from public.coach_week_summary_seen s
                    where s.user_id = v_uid and s.plan_id = v_plan.id and s.week = v_week),
    'sessions_done', (v_w->>'completed')::int,
    'sessions_planned', (v_w->>'planned')::int,
    'groups', v_groups,
    'weight', jsonb_build_object(
      'avg_kg', v_avg,
      'slope_kg_week', v_trend->'slope',
      'interval_low', v_trend->'low',
      'interval_high', v_trend->'high',
      'target_rate_kg_week', to_jsonb(nullif(v_plan.nutrition->>'rate_kg_week', '')::numeric)),
    'complete_days', v_days,
    'weigh_in_days', (v_gate->>'weigh_in_days')::int,
    'missing_records', not coalesce((v_gate->>'ok')::boolean, false),
    'changes', v_changes,
    'calorie_proposal', v_rec,
    'deload', coalesce((v_w->>'deload')::boolean, false),
    'stalls', v_stalls,
    'nutrition', v_nut);
end;
$$;

-- Internal, as coach_week_summary_build: it reads the revoked coach_open_discomforts_for, so it runs only inside the
-- definer live path (the app never calls it directly).
revoke execute on function public.coach_week_summary_week(date) from public, anon, authenticated;

-- The block review build (latest body, read with pg_get_functiondef: 20270108 + the 20270110 and 20270126 patches),
-- unchanged except its stall cards: a lift with an open discomfort at the block's last day is skipped (`07` section 3
-- "Block follow-up" (7)). The review shows the lifts stalled at the block end once, so it needs no run walk.
create or replace function public.coach_block_review_build(p_plan_id uuid, p_today date, p_end date default null)
returns jsonb
language plpgsql
stable
set search_path = ''
as $$
declare
  v_uid       uuid := (select auth.uid());
  v_plan      public.coach_plans;
  v_start     date;
  v_end       date;
  v_weeks     integer;
  v_pweeks    jsonb;
  v_adh       jsonb;
  v_lifts     jsonb := '[]'::jsonb;
  v_lift      record;
  v_wk        jsonb;
  v_groups    jsonb;
  v_first_avg numeric;
  v_last_avg  numeric;
  v_trend     jsonb;
  v_rate      numeric;
  v_slope     numeric;
  v_weight    jsonb;
  v_outcomes  jsonb := '[]'::jsonb;
  v_o         jsonb;
  v_kind      text;
  v_key       text;
  v_target    numeric;
  v_current   numeric;
  v_reached   boolean;
  v_wl        numeric;
  v_wd        date;
  v_wp        numeric;
  v_wpd       date;
  v_waist     jsonb := null;
  v_stalled   uuid[];
  v_stalls    jsonb;
  v_calph     text;
  v_deficit   integer;
  v_floor     integer;
  v_prop      jsonb := null;
  v_row       record;
  v_blocks    integer := 0;
  v_after_cut boolean := false;
  v_goal      text;
begin
  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid;
  v_start := coalesce(v_plan.training_start, v_plan.block_start);
  select max(s.week) into v_weeks from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid;
  if p_end is null then
    v_end := public.coach_block_end(p_plan_id, p_today);
  else
    -- Early review (T-239): the block is read up to p_end and only the plan weeks started so far count.
    v_end := p_end;
    v_weeks := greatest(least(public.coach_plan_week(p_plan_id, p_today), v_weeks), 1);
  end if;

  -- Adherence over every plan week, a deload week included (an early review: the weeks started so far).
  v_pweeks := public.coach_plan_weeks(p_plan_id, p_today);
  select jsonb_build_object('completed', coalesce(sum((w->>'completed')::int), 0),
                            'planned', coalesce(sum((w->>'planned')::int), 0),
                            'pct', case when coalesce(sum((w->>'planned')::int), 0) = 0 then null
                                        else round(sum((w->>'completed')::int) * 100.0 / sum((w->>'planned')::int)) end)
  into v_adh
  from jsonb_array_elements(v_pweeks) w
  where p_end is null or (w->>'week')::int <= v_weeks;

  select coalesce(array_agg(l.lift_id), '{}') into v_stalled from public.coach_stalled_lifts(p_plan_id, v_weeks) l;

  -- Main lifts of the block (role main in week 1, in order of appearance); weekly best e1RM without deload weeks.
  for v_lift in
    select x.id::uuid as eid
    from (select e.value->>'exercise_id' as id, min(s.session_index * 100 + e.ordinality) as first_pos
          from public.coach_plan_sessions s
          cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
          where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = 1 and e.value->>'role' = 'main'
          group by e.value->>'exercise_id') x
    order by x.first_pos
  loop
    select coalesce(jsonb_agg(jsonb_build_object('week', b.wk, 'e1rm', b.e) order by b.wk), '[]'::jsonb)
    into v_wk
    from (select public.coach_plan_week_of_date_for(v_uid, p_plan_id, (ws.started_at at time zone 'utc')::date) as wk,
                 max(public.coach_e1rm(st.weight_kg, st.reps)) as e
          from public.workout_sessions ws
          join public.workout_sets st on st.session_id = ws.id
          where ws.user_id = v_uid and ws.status = 'completed' and st.exercise_id = v_lift.eid
            and st.completed_at is not null
            and (st.set_type <> 'warm_up' or public.warm_up_included())
            and (ws.started_at at time zone 'utc')::date between v_start and v_end
          group by 1) b
    where b.e is not null
      and not exists (select 1 from public.coach_plan_sessions s
                      where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = b.wk and s.deload);
    v_lifts := v_lifts || jsonb_build_array(jsonb_build_object(
      'exercise_id', v_lift.eid,
      'weeks', v_wk,
      'first_e1rm', v_wk->0->'e1rm',
      'last_e1rm', v_wk->(jsonb_array_length(v_wk) - 1)->'e1rm',
      'change_kg', case when jsonb_array_length(v_wk) = 0 then null
                        else to_jsonb((v_wk->(jsonb_array_length(v_wk) - 1)->>'e1rm')::numeric
                                      - (v_wk->0->>'e1rm')::numeric) end,
      'stalled', v_lift.eid = any (v_stalled)));
  end loop;

  select coalesce(jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets)
                            order by g.sets desc, g.muscle_group), '[]'::jsonb)
  into v_groups
  from public.muscle_group_sets(v_start, v_end, true) g;

  -- Weight: average of the daily means in plan week 1 and in the last plan week; trend up to the last day.
  select round(avg(d.w), 2) into v_first_avg
  from (select avg(weight_kg) as w from public.body_measurements
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and public.coach_plan_window_end_for(v_uid, p_plan_id, v_start)
        group by (measured_at at time zone 'UTC')::date) d;
  select round(avg(d.w), 2) into v_last_avg
  from (select avg(weight_kg) as w from public.body_measurements
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_end - 6 and v_end
        group by (measured_at at time zone 'UTC')::date) d;
  v_trend := public.coach_weight_trend(v_end);
  v_rate := nullif(v_plan.nutrition->>'rate_kg_week', '')::numeric;
  v_slope := (v_trend->>'slope')::numeric;
  v_weight := jsonb_build_object('first_avg_kg', v_first_avg, 'last_avg_kg', v_last_avg,
                                 'slope_kg_week', v_trend->'slope', 'interval_low', v_trend->'low',
                                 'interval_high', v_trend->'high', 'target_rate_kg_week', to_jsonb(v_rate));

  -- Outcomes: informational only (nothing reads "reached" as a trigger).
  for v_o in select value from jsonb_array_elements(v_plan.outcomes) loop
    v_kind := v_o->>'kind';
    v_key := v_o->>'key';
    v_target := nullif(v_o->>'target_value', '')::numeric;
    v_current := null;
    v_reached := false;
    if v_kind = 'weight' then
      v_current := v_last_avg;
      v_reached := v_current is not null and v_target is not null and v_rate is not null
                   and case when v_rate < 0 then v_current <= v_target
                            when v_rate > 0 then v_current >= v_target
                            else false end;
    elsif v_kind = 'e1rm' then
      select max(public.coach_e1rm(st.weight_kg, st.reps)) into v_current
      from public.workout_sessions ws
      join public.workout_sets st on st.session_id = ws.id
      join public.exercises e on e.id = st.exercise_id
      where ws.user_id = v_uid and ws.status = 'completed' and coalesce(e.key, e.id::text) = v_key
        and st.completed_at is not null
        and (st.set_type <> 'warm_up' or public.warm_up_included())
        and (ws.started_at at time zone 'utc')::date between v_start and v_end;
      v_reached := v_current is not null and v_target is not null and v_current >= v_target;
    end if;
    v_outcomes := v_outcomes || jsonb_build_array(jsonb_build_object(
      'kind', v_kind, 'key', v_key, 'target_value', v_target, 'unit', v_o->>'unit', 'deadline', v_o->>'deadline',
      'current_value', v_current, 'reached', v_reached));
  end loop;

  -- Waist: the latest on or before the last day against the latest one at least 28 days older. Never a trigger.
  select c.value_cm, (c.measured_at at time zone 'UTC')::date into v_wl, v_wd
  from public.body_circumferences c
  where c.user_id = v_uid and c.site = 'waist' and (c.measured_at at time zone 'UTC')::date <= v_end
  order by c.measured_at desc limit 1;
  if v_wd is not null then
    select c.value_cm, (c.measured_at at time zone 'UTC')::date into v_wp, v_wpd
    from public.body_circumferences c
    where c.user_id = v_uid and c.site = 'waist' and (c.measured_at at time zone 'UTC')::date <= v_wd - 28
    order by c.measured_at desc limit 1;
    if v_wpd is not null then
      v_waist := jsonb_build_object('delta_cm', v_wl - v_wp, 'from_date', v_wpd, 'to_date', v_wd,
                                    'within_margin', abs(v_wl - v_wp) < 3.0);
    end if;
  end if;

  select coalesce(jsonb_agg(s.e order by s.ord), '[]'::jsonb) into v_stalls
  from jsonb_array_elements(public.coach_stall_diagnosis(p_plan_id, v_weeks)) with ordinality s(e, ord)
  where not exists (select 1 from public.coach_open_discomforts_for(v_uid, v_end) d
                    where d.exercise_id = (s.e->>'exercise_id')::uuid);

  -- Phase proposal: the first rule that fires, on measured data only (07 section 12.3).
  v_calph := coalesce(v_plan.nutrition->>'phase', v_plan.phase::text);
  if p_end is not null then
    -- An early review proposes no phase change: the goal pick is the phase change.
    null;
  elsif v_calph = 'cut' then
    v_deficit := (public.coach_phase_history(p_today)->>'deficit_weeks_done')::int;
    v_floor := nullif(v_plan.nutrition->>'floor_kcal', '')::int;
    if v_deficit >= 12 then
      v_prop := jsonb_build_object('to_phase', 'maintenance', 'reason', 'cut_length',
                                   'figures', jsonb_build_object('deficit_weeks', v_deficit));
    elsif v_plan.nutrition->>'limited_by' = 'floor' and v_rate is not null and v_rate < 0 and v_slope is not null
          and v_slope > 0.5 * v_rate
          and not exists (select 1 from public.coach_recommendations r
                          where r.user_id = v_uid and r.plan_id = p_plan_id and r.kind = 'calorie_change'
                            and r.status = 'accepted' and v_floor is not null
                            and (r.payload->>'to_kcal')::int > v_floor) then
      v_prop := jsonb_build_object('to_phase', 'maintenance', 'reason', 'floor_limited',
                                   'figures', jsonb_build_object('slope_kg_week', v_slope,
                                                                 'target_rate_kg_week', v_rate));
    end if;
  elsif v_calph = 'maintenance' then
    v_goal := case v_plan.intention when 'fat_loss' then 'cut' when 'muscle' then 'gain'
                                    when 'recomposition' then 'recomposition' else null end;
    if v_goal is not null then
      for v_row in
        select coalesce(p.nutrition->>'phase', p.phase::text) as cp
        from public.coach_plans p
        where p.user_id = v_uid and p.status in ('superseded', 'accepted')
          and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
          and (p.block_start, p.created_at) <= (v_plan.block_start, v_plan.created_at)
        order by p.block_start desc, p.created_at desc
      loop
        if v_row.cp = 'maintenance' then
          v_blocks := v_blocks + 1;
        else
          v_after_cut := v_row.cp = 'cut';
          exit;
        end if;
      end loop;
      if v_after_cut and v_blocks >= 1 then
        v_prop := jsonb_build_object('to_phase', v_goal, 'reason', 'back_to_goal',
                                     'figures', jsonb_build_object('blocks_in_maintenance', v_blocks));
      end if;
    end if;
  end if;

  return jsonb_build_object(
    'inputs', jsonb_build_object('adherence', v_adh, 'lifts', v_lifts, 'groups', v_groups, 'weight', v_weight,
                                 'outcomes', v_outcomes, 'waist', v_waist, 'stalls', v_stalls),
    'phase_proposal', v_prop);
end;
$$;

-- The weekly summary (body of 20270123100000_coach_reports.sql) storing snapshot v2: the v1 keys as built plus
-- schema_version 2 and progress. Late data (`07` section 3 "Stored weeks and late data", Q17): a stored v2 row whose
-- progress is not final is rebuilt when older than 10 minutes; storing a new week finalises every earlier non-final v2
-- row of the user (any plan). v1 rows are returned as stored (T-314 rebuilds them).
create or replace function public.coach_week_summary(p_today date)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_json  jsonb;
  v_plan  uuid;
  v_week  integer;
  v_start date;
  v_end   date;
  v_seen  boolean;
  v_row   public.coach_week_reports;
begin
  v_json := public.coach_week_summary_build(p_today);
  if v_json is null or coalesce((v_json->>'paused')::boolean, false) then
    return v_json;
  end if;

  v_plan := (v_json->>'plan_id')::uuid;
  v_week := (v_json->>'week')::int;
  v_start := (v_json->>'start_date')::date;
  v_end := (v_json->>'end_date')::date;
  v_seen := exists (select 1 from public.coach_week_summary_seen s
                    where s.user_id = v_uid and s.plan_id = v_plan and s.week = v_week);

  select * into v_row from public.coach_week_reports where user_id = v_uid and plan_id = v_plan and period_start = v_start;
  if found then
    if v_row.snapshot->>'schema_version' = '2' and v_row.snapshot ? 'progress'
       and not coalesce((v_row.snapshot->'progress'->>'final')::boolean, false)
       and (v_row.snapshot->'progress'->>'refreshed_at')::timestamptz < now() - interval '10 minutes' then
      -- The same staleness test in the update, so parallel calls write once.
      update public.coach_week_reports r
      set snapshot = jsonb_set(r.snapshot, '{progress}',
                               coalesce(public.coach_week_progress_for(r.user_id, r.plan_id, r.week, r.period_start,
                                                                       r.period_end),
                                        r.snapshot->'progress'))
      where r.id = v_row.id
        and not coalesce((r.snapshot->'progress'->>'final')::boolean, false)
        and (r.snapshot->'progress'->>'refreshed_at')::timestamptz < now() - interval '10 minutes';
      select * into v_row from public.coach_week_reports where id = v_row.id;
    end if;
    return jsonb_set(v_row.snapshot, '{seen}', to_jsonb(v_seen));
  end if;

  insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
  values (v_uid, v_plan, v_week, v_start, v_end,
          (v_json - 'seen')
            || jsonb_build_object('schema_version', 2,
                                  'progress', public.coach_week_progress_for(v_uid, v_plan, v_week, v_start, v_end)))
  on conflict (user_id, plan_id, period_start) do nothing;
  if found then
    perform public.coach_finalise_week_reports_for(v_uid, v_start);
  end if;

  select * into v_row from public.coach_week_reports where user_id = v_uid and plan_id = v_plan and period_start = v_start;
  return jsonb_set(v_row.snapshot, '{seen}', to_jsonb(v_seen));
end;
$$;

revoke execute on function public.coach_week_summary(date) from public, anon;
grant execute on function public.coach_week_summary(date) to authenticated;

-- The paused week report (body of 20270123100000_coach_reports.sql), stored with schema_version 2 and no progress;
-- storing it finalises the earlier non-final v2 rows, as storing a normal week does.
create or replace function public.record_paused_week_report(p_today date)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_json jsonb;
  v_row  public.coach_week_reports;
begin
  v_json := public.coach_week_summary_build(p_today);
  if v_json is null or not coalesce((v_json->>'paused')::boolean, false) then
    return null;
  end if;
  insert into public.coach_week_reports (user_id, plan_id, week, paused, period_start, period_end, snapshot)
  values (v_uid, (v_json->>'plan_id')::uuid, null, true, (v_json->>'start_date')::date, (v_json->>'end_date')::date,
          v_json || jsonb_build_object('schema_version', 2))
  on conflict (user_id, plan_id, period_start) do nothing;
  if found then
    perform public.coach_finalise_week_reports_for(v_uid, (v_json->>'start_date')::date);
  end if;
  select * into v_row from public.coach_week_reports
    where user_id = v_uid and plan_id = (v_json->>'plan_id')::uuid and period_start = (v_json->>'start_date')::date;
  return v_row.snapshot;
end;
$$;

revoke execute on function public.record_paused_week_report(date) from public, anon;
grant execute on function public.record_paused_week_report(date) to authenticated;
