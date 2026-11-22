-- T-317 (US-143, US-144 data; ADR-111; planning/07-coach-design.md section 3 "Other reports: progress first" (A)):
-- every block review is built with `schema_version: 2` and `progress` (the weekly progress sections evaluated as of the
-- block's last day, `ended_on`), built once at the first open and never recomputed (`04` section 6.11 as amended).
--   1. Self-check, part 1: the count of coach_block_reviews before the migration.
--   2. coach_progress_block_weight_for: the block's weight section (3-weigh-in gate, first and last qualifying week).
--   3. coach_progress_block_records_for: the block's records, at most 3 items plus the total.
--   4. coach_block_review_build (recreated): the v1 keys unchanged, plus schema_version, progress, and the weight
--      outcome read from the last plan week's average with the block's last day as its fallback deadline.
--   5. Self-check, part 2: the count is unchanged (no backfill; production holds no stored block review).
-- The count self-check keeps the count before in a session-level setting (set_config(..., false)): it relies on both
-- parts running on the one connection of the migration (`db push` / `db reset` apply a file on a single connection).
-- The outcome keys stay `deadline`, `current_value`, `reached` (no rename). list_coach_reports is T-317 run B.

select set_config('app.t317_reviews_before', (select count(*)::text from public.coach_block_reviews), false);

-- ---------------------------------------------------------------------------
-- Block weight, `07` section 3 "Other reports: progress first" (A4). Plan-week averages of the daily values (`04`
-- section 3.1, UTC days) over the block's plan weeks (paused windows are not plan weeks), the 3-weigh-in-day gate, the
-- change between the last and the first qualifying week (two distinct weeks needed, else null with the averages kept)
-- and the series of the block's weeks. Trend and rate as coach_progress_weight_for: ISO-week averages over min(block
-- weeks, 8) weeks ending the ISO week of p_end (the phase run does not apply, `04` section 7.4: one pace). Null when
-- the block has no weigh-in at all. Definer so that it reads the user's rows whoever calls it; every row is filtered on
-- p_uid and dated <= p_end.
-- ---------------------------------------------------------------------------
create function public.coach_progress_block_weight_for(p_uid uuid, p_plan_id uuid, p_start date, p_end date)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with plan as (
    select p.nutrition, p.outcomes from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  win as (
    select w.week, w.start_date, w.end_date
    from public.coach_plan_windows_for(p_uid, p_plan_id, p_end) w
    where w.start_date <= p_end
  ),
  tw as (
    select x.mon - 7 * (x.n - 1) as ws
    from (select date_trunc('week', p_end::timestamp)::date as mon,
                 greatest(1, least((select count(*) from win), 8))::int as n) x
  ),
  daily as (
    select (b.measured_at at time zone 'utc')::date as day, avg(b.weight_kg) as kg
    from public.body_measurements b
    cross join (select least(p_start, (select tw.ws from tw)) as d0) lim
    where b.user_id = p_uid
      and b.measured_at >= lim.d0::timestamp at time zone 'utc'
      and b.measured_at < (p_end + 1)::timestamp at time zone 'utc'
    group by 1
  ),
  wk as (
    select win.*, a.avg_kg, a.days
    from win
    cross join lateral (select round(avg(d.kg), 2) as avg_kg, count(*)::int as days
                        from daily d where d.day between win.start_date and least(win.end_date, p_end)) a
  ),
  qual as (
    select wk.* from wk where wk.days >= 3
  ),
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
    'avg_first_kg', (select wk.avg_kg from wk order by wk.week limit 1),
    'avg_last_kg', (select wk.avg_kg from wk order by wk.week desc limit 1),
    'days_first', coalesce((select wk.days from wk order by wk.week limit 1), 0),
    'days_last', coalesce((select wk.days from wk order by wk.week desc limit 1), 0),
    'change_kg',
      case when (select count(*) from qual) >= 2 then
        round((select q.avg_kg from qual q order by q.week desc limit 1)
              - (select q.avg_kg from qual q order by q.week limit 1), 1)
      end,
    'series', coalesce((select jsonb_agg(jsonb_build_object('start_date', s.start_date, 'avg_kg', s.avg_kg, 'days', s.days)
                                         order by s.week)
                        from wk s where s.days >= 1), '[]'::jsonb),
    'outcome', (select jsonb_build_object(
                         'kg', nullif(o.value->>'target_value', '')::numeric,
                         'deadline', (select max(w.end_date) from win w))
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
  cross join band
  where exists (select 1 from daily d where d.day between p_start and p_end);
$$;

revoke execute on function public.coach_progress_block_weight_for(uuid, uuid, date, date)
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Block records, `07` section 3 "Other reports: progress first" (A3): the weekly records rule
-- (coach_progress_records_for) with the window = the whole block; at most 3 items (main lifts first, then the largest
-- relative gain) and `total`, the number of records in the block.
-- ---------------------------------------------------------------------------
create function public.coach_progress_block_records_for(p_uid uuid, p_start date, p_end date)
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
  select jsonb_build_object(
    'items', coalesce((select jsonb_agg(jsonb_build_object('exercise_id', b.exercise_id, 'kind', b.kind,
                                                           'value_kg', b.value_kg, 'previous_kg', b.previous_kg,
                                                           'main_lift', b.main_lift)
                                        order by b.main_lift desc, b.gain desc, b.exercise_id, b.kind)
                       from best3 b), '[]'::jsonb),
    'total', (select count(*) from ranked)::int);
$$;

revoke execute on function public.coach_progress_block_records_for(uuid, date, date) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- The block review build (latest body: 20270131100000_coach_week_progress.sql): the v1 keys unchanged, plus
-- schema_version 2 and progress, and the weight outcome from the last plan week's average (T-317).
-- ---------------------------------------------------------------------------
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
  v_wstart    date;
  v_bw        jsonb;
  v_strength  jsonb;
  v_lifts2    jsonb;
  v_records   jsonb;
  v_bwaist    jsonb;
  v_ctx       jsonb;
  v_missing   jsonb;
  v_headline  jsonb;
  v_progress  jsonb;
  v_ups       integer;
  v_compared  integer;
  v_wdl       date;
  v_wown      text;
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

  -- Progress sections (`07` section 3 "Other reports: progress first" (A)), as of v_end, by the T-313 builders. The
  -- compared week is the block's last plan week (so the block to date); records and waist read the whole block.
  select w.start_date into v_wstart
  from public.coach_plan_windows_for(v_uid, p_plan_id, v_end) w where w.week = v_weeks;
  v_wstart := coalesce(v_wstart, v_start);
  v_bw := public.coach_progress_block_weight_for(v_uid, p_plan_id, v_start, v_end);
  -- The weight outcome's one deadline, in progress.weight.outcome and in outcomes[]: its own, else the block's real
  -- last day (an early review's v_end is only today).
  v_wdl := public.coach_block_end(p_plan_id, p_today);
  select o.value->>'deadline' into v_wown
  from jsonb_array_elements(v_plan.outcomes) with ordinality o(value, i)
  where o.value->>'kind' = 'weight' order by o.i limit 1;
  if v_bw->'outcome' is not null and v_bw->'outcome' <> 'null'::jsonb then
    v_bw := jsonb_set(v_bw, '{outcome,deadline}', to_jsonb(coalesce(v_wown, v_wdl::text)));
  end if;
  v_strength := public.coach_progress_strength_for(v_uid, p_plan_id, v_weeks, v_wstart, v_end);
  v_lifts2 := public.coach_progress_lifts_for(v_uid, p_plan_id, v_weeks, v_wstart, v_end);
  v_records := public.coach_progress_block_records_for(v_uid, v_start, v_end);
  v_bwaist := public.coach_progress_waist_for(v_uid, p_plan_id, v_weeks, v_start, v_end);
  -- Context over every plan week of the block: the weekly line summed (sets_planned null when any week has no match).
  select jsonb_build_object(
           'sessions_done', coalesce(sum((c.x->>'sessions_done')::int), 0)::int,
           'sessions_planned', coalesce(sum((c.x->>'sessions_planned')::int), 0)::int,
           'sets_done', coalesce(sum((c.x->>'sets_done')::int), 0)::int,
           'sets_planned', case when count(*) > 0 and bool_and(c.x->>'sets_planned' is not null)
                                then sum((c.x->>'sets_planned')::int)::int end)
  into v_ctx
  from (select public.coach_progress_context_for(v_uid, p_plan_id, g.n, v_start, v_end) as x
        from generate_series(1, v_weeks) g(n)) c;
  -- Gates of `07` section 2: the block weight change needs two qualifying weeks; fewer than 5 complete diary days.
  v_missing := to_jsonb(array_remove(array[
    case when v_bw is null or v_bw->'change_kg' = 'null'::jsonb then 'weigh_ins' end,
    case when (select count(*) from public.coach_complete_days(v_uid, v_end)) < 5 then 'diary' end], null));
  select count(*) filter (where e->>'state' = 'up')::int, count(*) filter (where e->>'state' <> 'uncompared')::int
  into v_ups, v_compared
  from jsonb_array_elements(v_strength->'groups') g
  cross join lateral jsonb_array_elements(g->'exercises') e;
  v_headline := jsonb_build_object(
    'ups', case when v_weeks > 1 then v_ups end,
    'total', case when v_weeks > 1 then v_compared end,
    'main_lift', (select jsonb_build_object('exercise_id', l->'exercise_id', 'change_kg', l->'change_kg',
                                            'change_pct', l->'change_pct')
                  from jsonb_array_elements(v_lifts2) l
                  where l->>'state' = 'up'
                  order by (l->>'change_pct')::numeric desc, l->>'exercise_id'
                  limit 1),
    'weight_change_kg', v_bw->'change_kg');
  v_progress := jsonb_build_object(
    'as_of', v_end, 'intention', v_plan.intention,
    'phase', coalesce(v_plan.nutrition->>'phase', v_plan.phase::text),
    'missing', v_missing, 'headline', v_headline, 'context', v_ctx, 'strength', v_strength, 'lifts', v_lifts2,
    'records', v_records, 'weight', v_bw, 'waist', v_bwaist);


  -- Outcomes: informational only (nothing reads "reached" as a trigger). The weight outcome reads the last plan week's
  -- average, the figure the weight card shows (T-317), and falls back to the block's last day as its deadline.
  for v_o in select value from jsonb_array_elements(v_plan.outcomes) loop
    v_kind := v_o->>'kind';
    v_key := v_o->>'key';
    v_target := nullif(v_o->>'target_value', '')::numeric;
    v_current := null;
    v_reached := false;
    if v_kind = 'weight' then
      v_current := (v_bw->>'avg_last_kg')::numeric;
      v_reached := v_current is not null and v_target is not null and v_rate is not null
                   and case when v_rate < 0 then v_current <= v_target
                            when v_rate > 0 then v_current >= v_target
                            else false end;
      -- The weight gate of the card (`07` section 2): under 3 weigh-in days in the last plan week the average is shown
      -- but is no verdict, so `reached` is null (no chip).
      if coalesce((v_bw->>'days_last')::int, 0) < 3 then
        v_reached := null;
      end if;
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
      'kind', v_kind, 'key', v_key, 'target_value', v_target, 'unit', v_o->>'unit',
      'deadline', case when v_kind = 'weight' then coalesce(v_o->>'deadline', v_wdl::text) else v_o->>'deadline' end,
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
                                 'outcomes', v_outcomes, 'waist', v_waist, 'stalls', v_stalls,
                                 'schema_version', 2, 'progress', v_progress),
    'phase_proposal', v_prop);
end;
$$;

-- Same signature as before (create or replace keeps its revoke): internal, called only by the definer paths.

-- ---------------------------------------------------------------------------
-- list_coach_reports (body of 20270123100000_coach_reports.sql) with a `teaser` per row (`07` section 3 "Other reports:
-- progress first" (B)), so the list never recomputes a sentence: a week reads its stored progress ({ups, total,
-- weight_change_kg}: ups and total null in plan week 1, the weight change only when both weeks passed the 3-weigh-in
-- gate), a block reads inputs.progress.headline, and a paused week or a report without progress has null. Grants as before.
-- ---------------------------------------------------------------------------
drop function public.list_coach_reports();

create function public.list_coach_reports()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(jsonb_agg(x.j order by x.created_at desc, x.id), '[]'::jsonb)
  from (
    select w.id, w.created_at,
           jsonb_build_object('id', w.id, 'kind', 'week_summary', 'plan_id', w.plan_id, 'week', w.week, 'paused', w.paused,
                              'period_start', w.period_start, 'period_end', w.period_end,
                              'created_at', w.created_at, 'phase_decision', null::text,
                              'teaser',
                                case when w.paused or jsonb_typeof(w.snapshot->'progress') is distinct from 'object' then null
                                else (
                                  select jsonb_build_object(
                                    'ups', case when coalesce((w.snapshot->'progress'->'strength'->>'block_week')::int, 1) > 1
                                                then count(*) filter (where e->>'state' = 'up')::int end,
                                    'total', case when coalesce((w.snapshot->'progress'->'strength'->>'block_week')::int, 1) > 1
                                                  then count(*) filter (where e->>'state' <> 'uncompared')::int end,
                                    'weight_change_kg', w.snapshot->'progress'->'weight'->'change_kg')
                                  from jsonb_array_elements(coalesce(w.snapshot->'progress'->'strength'->'groups', '[]'::jsonb)) g
                                  cross join lateral jsonb_array_elements(g->'exercises') e) end) as j
    from public.coach_week_reports w
    where w.user_id = (select auth.uid())
    union all
    select r.id, r.created_at,
           jsonb_build_object('id', r.id, 'kind', 'block_review', 'plan_id', r.plan_id, 'week', null::int, 'paused', false,
                              'period_start', coalesce(p.training_start, p.block_start), 'period_end', r.ended_on,
                              'created_at', r.created_at, 'phase_decision', r.phase_decision,
                              'teaser',
                                case when jsonb_typeof(r.inputs->'progress'->'headline') = 'object'
                                     then jsonb_build_object('ups', r.inputs->'progress'->'headline'->'ups',
                                                             'total', r.inputs->'progress'->'headline'->'total',
                                                             'weight_change_kg', r.inputs->'progress'->'headline'->'weight_change_kg')
                                end)
    from public.coach_block_reviews r
    join public.coach_plans p on p.id = r.plan_id and p.user_id = r.user_id
    where r.user_id = (select auth.uid())
  ) x;
$$;

revoke execute on function public.list_coach_reports() from public, anon;
grant execute on function public.list_coach_reports() to authenticated;

-- Self-check, part 2: nothing stored, nothing backfilled; the count is unchanged and printed.
do $$
declare
  v_before integer := current_setting('app.t317_reviews_before')::integer;
  v_after  integer := (select count(*) from public.coach_block_reviews);
begin
  if v_before is distinct from v_after then
    raise exception 'T-317: coach_block_reviews count changed (% before, % after)', v_before, v_after;
  end if;
  raise notice 'T-317: coach_block_reviews count unchanged (%)', v_after;
end;
$$;

select set_config('app.t317_reviews_before', '', false);
