-- T-239 (E15, US-110, US-111): "Bloque nuevo" and goal switching, backend. Rules: planning/07 section 12.8 rows 2-4 and
-- 12.9; planning/04 sections 8.5, 8.9, 8.10 and 9.15-9.17. No new table.
--   (1) coach_block_counted_weeks: the one place with the "weeks in phase" arithmetic (T-242 changes only its body).
--   (2) coach_weeks_in_phase: weeks lived in the current calorie phase.
--   (3) coach_phase_history: the deficit-week run restarts only after 4 consecutive non-deficit weeks (07 12.9 rule 4).
--   (4) evaluate_coach_calorie_change: "recent_change" looks at the current plan only (counters restart with the block).
--   (5) coach_block_review_build with p_end: the early review opened mid-block (nothing is stored).
--   (6) open_coach_block_new: the single entry of "Bloque nuevo" (early review, or the stored review after the block end).
--   (7) propose_goal_switch: the deterministic goal preview stores one PROPOSED plan (the vehicle of the new block).
--   (8) "Crear plan nuevo" is retired on the server while a block exists: propose_coach_plan needs app.coach_next_plan.
--   (9) coach_begin_next_block re-bases the plan start on the save day.

-- ---------------------------------------------------------------------------
-- (1) coach_block_counted_weeks: weeks started by a block. The newest block counts the plan weeks reached up to today
-- (never more than its last plan week); an earlier block counts the weeks started before the next block began (a block
-- ended early counts only those). A diet-break week counts as a week in the phase.
-- ---------------------------------------------------------------------------
create function public.coach_block_counted_weeks(p_plan_id uuid, p_today date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select case
           when nx.next_start is null
             then least(m.max_week, greatest(public.coach_plan_week(p.id, p_today), 0))
           else least(m.max_week,
                      greatest(0, ceil((nx.next_start - coalesce(p.training_start, p.block_start)) / 7.0)::int))
         end
  from public.coach_plans p
  cross join lateral (
    select max(s.week)::int as max_week
    from public.coach_plan_sessions s where s.plan_id = p.id and s.user_id = p.user_id
  ) m
  left join lateral (
    select coalesce(n.training_start, n.block_start) as next_start
    from public.coach_plans n
    where n.user_id = p.user_id and n.status in ('superseded', 'accepted') and n.id <> p.id
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = n.id and s.user_id = n.user_id)
      and (n.block_start, n.created_at) > (p.block_start, p.created_at)
    order by n.block_start, n.created_at
    limit 1
  ) nx on true
  where p.id = p_plan_id and p.user_id = (select auth.uid()) and m.max_week is not null;
$$;

revoke execute on function public.coach_block_counted_weeks(uuid, date) from public, anon;
grant execute on function public.coach_block_counted_weeks(uuid, date) to authenticated;

-- ---------------------------------------------------------------------------
-- (2) coach_weeks_in_phase: {phase, weeks} of the accepted plan's calorie phase; the weeks of the consecutive blocks of
-- that phase, newest first, until a block of another phase. Null without an accepted plan. No limit, no warning.
-- ---------------------------------------------------------------------------
create function public.coach_weeks_in_phase(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_phase text;
  v_weeks integer := 0;
  v_row   record;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found then
    return null;
  end if;
  v_phase := coalesce(v_plan.nutrition->>'phase', v_plan.phase::text);

  for v_row in
    select p.id, coalesce(p.nutrition->>'phase', p.phase::text) as calorie_phase
    from public.coach_plans p
    where p.user_id = v_uid and p.status in ('superseded', 'accepted')
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id and s.user_id = v_uid)
    order by p.block_start desc, p.created_at desc
  loop
    exit when v_row.calorie_phase <> v_phase;
    v_weeks := v_weeks + coalesce(public.coach_block_counted_weeks(v_row.id, p_today), 0);
  end loop;

  return jsonb_build_object('phase', v_phase, 'weeks', v_weeks);
end;
$$;

revoke execute on function public.coach_weeks_in_phase(date) from public, anon;
grant execute on function public.coach_weeks_in_phase(date) to authenticated;

-- ---------------------------------------------------------------------------
-- (3) coach_phase_history: as 20261227100200 except deficit_weeks_done. Blocks are walked newest to oldest. A block
-- that is not a cut adds its weeks to a gap; entering a cut block adds its trailing diet-break week to the gap; with a
-- gap of 4 or more the run ended, else the block adds its deficit weeks (counted weeks, minus the diet-break week) and
-- the gap restarts. Less than 4 weeks outside the cut only pause it (07 section 12.9 rule 4).
-- ---------------------------------------------------------------------------
create or replace function public.coach_phase_history(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_prev    public.coach_plans;
  v_main    jsonb;
  v_ranges  jsonb;
  v_deficit integer := 0;
  v_gap     integer := 0;
  v_weeks   integer;
  v_break   boolean;
  v_row     record;
begin
  select p.* into v_prev
  from public.coach_plans p
  where p.user_id = v_uid and p.status in ('superseded', 'accepted')
    and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
  order by p.block_start desc, p.created_at desc
  limit 1;
  if not found then
    return jsonb_build_object('previous', null, 'minimal_blocks_in_a_row', 0, 'deficit_weeks_done', 0);
  end if;

  select coalesce(jsonb_agg(x.id order by x.first_pos), '[]'::jsonb) into v_main
  from (
    select e.value->>'exercise_id' as id, min(s.session_index * 100 + e.ordinality) as first_pos
    from public.coach_plan_sessions s
    cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
    where s.plan_id = v_prev.id and s.week = 1 and e.value->>'role' = 'main'
    group by e.value->>'exercise_id'
  ) x;

  select coalesce(jsonb_object_agg(r.id, jsonb_build_array(r.rep_min, r.rep_max)), '{}'::jsonb) into v_ranges
  from (
    select distinct on (e.value->>'exercise_id')
           e.value->>'exercise_id' as id, (e.value->>'rep_min')::int as rep_min, (e.value->>'rep_max')::int as rep_max
    from public.coach_plan_sessions s
    cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
    where s.plan_id = v_prev.id and s.week = 1 and e.value->>'role' in ('main', 'isolation')
    order by e.value->>'exercise_id', s.session_index, e.ordinality
  ) r;

  for v_row in
    select p.id, coalesce(p.nutrition->>'phase', p.phase::text) as calorie_phase,
           coalesce((p.phase_options->>'diet_break')::boolean, false) as diet_break
    from public.coach_plans p
    where p.user_id = v_uid and p.status in ('superseded', 'accepted')
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
    order by p.block_start desc, p.created_at desc
  loop
    v_weeks := coalesce(public.coach_block_counted_weeks(v_row.id, p_today), 0);
    if v_row.calorie_phase <> 'cut' then
      v_gap := v_gap + v_weeks;
    else
      -- The diet-break week is the fourth: it exists only once the block reached it.
      v_break := v_row.diet_break and v_weeks >= 4;
      if v_break then
        v_gap := v_gap + 1;
      end if;
      exit when v_gap >= 4;
      v_deficit := v_deficit + v_weeks - case when v_break then 1 else 0 end;
      v_gap := 0;
    end if;
  end loop;

  return jsonb_build_object(
    'previous', jsonb_build_object(
      'phase', v_prev.phase::text,
      'sets_per_group', case when jsonb_typeof(v_prev.training->'weekly_sets') = 'object'
                             then v_prev.training->'weekly_sets' else '{}'::jsonb end,
      'main_lifts', v_main,
      'rep_ranges', v_ranges),
    'minimal_blocks_in_a_row', 0,
    'deficit_weeks_done', v_deficit);
end;
$$;

revoke execute on function public.coach_phase_history(date) from public, anon;
grant execute on function public.coach_phase_history(date) to authenticated;

-- ---------------------------------------------------------------------------
-- (4) evaluate_coach_calorie_change: the latest body (20270106100000) with one change: "recent_change" looks at the
-- decisions of the current plan only, so a new block (a new plan) starts with no pause from the previous one.
-- ---------------------------------------------------------------------------
create or replace function public.evaluate_coach_calorie_change(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_plan    public.coach_plans;
  v_pending uuid;
  v_rate    numeric;
  v_target  integer;
  v_floor   integer;
  v_adj     integer;
  v_t0      jsonb := public.coach_weight_trend(p_today);
  v_t1      jsonb := public.coach_weight_trend(p_today - 7);
  v_slope   numeric;
  v_low0    numeric;
  v_high0   numeric;
  v_low1    numeric;
  v_high1   numeric;
  v_below   boolean;
  v_above   boolean;
  v_size    integer;
  v_cap     integer;
  v_change  integer;
  v_new     integer;
  v_applied integer;
  v_base    integer;
  v_phase   text;
  v_limited text;
  v_exp     numeric;
  v_w       numeric;
  v_delta   numeric;
  v_min     numeric;
  v_max     numeric;
  v_rec     uuid;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found or coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'edited');
  end if;

  select id into v_pending from public.coach_recommendations
    where user_id = v_uid and kind = 'calorie_change' and status = 'pending'
      and created_at >= public.coach_week_start(p_today)::timestamp at time zone 'UTC';
  if v_pending is not null then
    return jsonb_build_object('status', 'pending', 'reason', 'already_pending_this_week', 'recommendation_id', v_pending);
  end if;

  -- After the block's last day nothing is proposed until the review (a plan with no saved block still proposes).
  if v_plan.folder_id is not null and p_today > coalesce(public.coach_block_end(v_plan.id), 'infinity'::date) then
    return jsonb_build_object('status', 'none', 'reason', 'block_ended');
  end if;

  -- Two "Ahora no" in the block: nothing more until the review.
  if (select count(*) from public.coach_recommendations
      where user_id = v_uid and kind = 'calorie_change' and plan_id = v_plan.id and status = 'discarded'
        and created_at >= v_plan.block_start::timestamp at time zone 'UTC') >= 2 then
    return jsonb_build_object('status', 'none', 'reason', 'declined_twice');
  end if;

  if exists (select 1 from public.coach_recommendations
             where user_id = v_uid and kind = 'calorie_change' and plan_id = v_plan.id
               and status in ('accepted', 'discarded')
               and decided_at > now() - interval '14 days') then
    return jsonb_build_object('status', 'none', 'reason', 'recent_change');
  end if;

  if not coalesce((public.coach_week_gate(p_today)->>'ok')::boolean, false)
     or not coalesce((public.coach_week_gate(p_today - 7)->>'ok')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'missing_records');
  end if;

  if not coalesce((public.coach_intake(p_today)->>'reliable')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'unreliable_intake');
  end if;

  v_rate := (v_plan.nutrition->>'rate_kg_week')::numeric;
  v_slope := (v_t0->>'slope')::numeric;
  v_low0 := (v_t0->>'low')::numeric;
  v_high0 := (v_t0->>'high')::numeric;
  v_low1 := (v_t1->>'low')::numeric;
  v_high1 := (v_t1->>'high')::numeric;
  if v_low0 is null or v_high0 is null or v_low1 is null or v_high1 is null or v_rate is null then
    return jsonb_build_object('status', 'none', 'reason', 'inside_interval');
  end if;
  -- Strictly outside on both weeks and on the same side; on the edge counts as inside (04 section 7.6).
  v_below := v_rate < v_low0 and v_rate < v_low1;
  v_above := v_rate > v_high0 and v_rate > v_high1;
  if not v_below and not v_above then
    return jsonb_build_object('status', 'none', 'reason', 'inside_interval');
  end if;

  v_target := (v_plan.nutrition->>'target_kcal')::int;
  v_floor := coalesce((v_plan.nutrition->>'floor_kcal')::int, 0);
  v_adj := (v_plan.nutrition->>'calorie_adjustment')::int;
  -- 7 700 kcal/kg over 7 days = 1 100 kcal/day per kg/week; rounded to 10, at least 50, at most 10 % of the target.
  v_size := greatest(50, (round(abs(v_slope - v_rate) * 1100 / 10) * 10)::int);
  v_cap := (floor(v_target * 0.10 / 10) * 10)::int;
  v_size := least(v_size, v_cap);
  v_change := case when v_below then -v_size else v_size end;
  v_new := v_target + v_change;

  -- Phase range (04 section 8.7 / 8.9) against the expenditure in use, by the phase and never by the intention.
  v_phase := coalesce(v_plan.nutrition->>'phase',
                      case v_plan.intention when 'fat_loss' then 'cut' when 'muscle' then 'gain' else 'maintenance' end);
  v_limited := v_plan.nutrition->>'limited_by';
  v_exp := (public.coach_compute(p_today)->'total_expenditure'->>'value')::numeric;
  select weight_kg into v_w from public.body_measurements
    where user_id = v_uid order by measured_at desc limit 1;

  if v_exp is not null and v_w is not null then
    if v_phase = 'gain' then
      v_min := 0.25 * v_w * 11;
      v_max := 0.5 * v_w * 11 + 10;
    elsif v_phase = 'cut' then
      v_min := -1.0 * v_w * 11 - 10;
      v_max := -0.5 * v_w * 11;
    else
      v_min := -0.25 * v_w * 11;
      v_max := 0.25 * v_w * 11;
    end if;
  end if;

  if v_new < v_floor then
    return jsonb_build_object('status', 'none', 'reason', 'floor');
  end if;
  if v_adj + v_change < -500 then
    return jsonb_build_object('status', 'none', 'reason', 'deficit_cap');
  end if;
  if v_adj + v_change > 750 then
    return jsonb_build_object('status', 'none', 'reason', 'rate_range');
  end if;

  if v_exp is not null and v_w is not null then
    v_delta := v_new - v_exp;
    -- Mirrors intakeRangeViolation (nutritionRules.ts): a limiter may make a plan slower than the range, never faster.
    -- A change that moves the target toward the range is never refused for being outside it (a gaining maintenance
    -- user does get a decrease, down to the range bottom): only a step past an edge, away from the range, is.
    if v_delta > v_max + 1e-9 and v_change > 0 and not (case when v_phase = 'cut' then v_limited is not null
                                                              else coalesce(v_limited = 'floor', false) end) then
      return jsonb_build_object('status', 'none', 'reason', 'rate_range');
    end if;
    if v_delta < v_min - 1e-9 and v_change < 0 and not (v_phase = 'gain' and v_limited is not null) then
      return jsonb_build_object('status', 'none', 'reason', 'rate_range');
    end if;
  end if;

  -- Band: the changes applied in this block plus this one stay within 10 % of the block's accepted calories.
  select coalesce(sum((payload->>'change_kcal')::int), 0)::int into v_applied
    from public.coach_recommendations
    where user_id = v_uid and kind = 'calorie_change' and plan_id = v_plan.id and status = 'accepted'
      and created_at >= v_plan.block_start::timestamp at time zone 'UTC';
  v_base := v_target - v_applied;
  if abs(v_applied + v_change) > v_base * 0.10 then
    return jsonb_build_object('status', 'none', 'reason', 'band');
  end if;

  -- A pending proposal from an earlier week is replaced; never two pending.
  update public.coach_recommendations set status = 'superseded', decided_at = now()
    where user_id = v_uid and kind = 'calorie_change' and status = 'pending';

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary)
  values (v_uid, v_plan.id, 'calorie_change',
          jsonb_build_object('from_kcal', v_target, 'to_kcal', v_new, 'change_kcal', v_change,
                             'change_pct', round(v_change * 100.0 / v_target, 1),
                             'slope_kg_week', v_slope, 'interval_low', v_low0, 'interval_high', v_high0,
                             'rate_kg_week', v_rate, 'base_kcal', v_base, 'band_used_kcal', v_applied, 'weeks', 2),
          'rate_outside_interval', public.coach_summary(p_today))
  returning id into v_rec;

  return jsonb_build_object('status', 'created', 'reason', 'rate_outside_interval', 'recommendation_id', v_rec);
end;
$$;

revoke execute on function public.evaluate_coach_calorie_change(date) from public, anon;
grant execute on function public.evaluate_coach_calorie_change(date) to authenticated;

-- ---------------------------------------------------------------------------
-- (5) coach_block_review_build with an optional end date: p_end set = the early review (T-239). Internal; only the
-- security definer open_coach_block_* functions call it. Without p_end the behaviour is the one of T-233.
-- ---------------------------------------------------------------------------
drop function public.coach_block_review_build(uuid, date);

create function public.coach_block_review_build(p_plan_id uuid, p_today date, p_end date default null)
returns jsonb
language plpgsql
stable
security invoker
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
    v_end := public.coach_block_end(p_plan_id);
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
    from (select ((ws.started_at at time zone 'utc')::date - v_start) / 7 + 1 as wk,
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
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and v_start + 6
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

  v_stalls := public.coach_stall_diagnosis(p_plan_id, v_weeks);

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

revoke execute on function public.coach_block_review_build(uuid, date, date) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- (8) propose_coach_plan: the latest body (20261220100000) plus the "Crear plan nuevo" retirement. While the user has an
-- accepted plan with a saved block the only ways to propose a plan are the block review (accept_coach_phase) and the
-- goal switch (propose_goal_switch); both set the transaction-local app.coach_next_plan. A plan without a saved block
-- ("Crear plan") is unaffected.
-- ---------------------------------------------------------------------------
create or replace function public.propose_coach_plan(
  p_block_start date,
  p_intention public.training_intention,
  p_nutrition jsonb,
  p_outcomes jsonb,
  p_method jsonb,
  p_input_summary jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_plan    uuid;
  v_rec     uuid;
begin
  if coalesce(current_setting('app.coach_next_plan', true), '') <> 'on'
     and exists (select 1 from public.coach_plans p
                 where p.user_id = v_uid and p.status = 'accepted' and p.folder_id is not null) then
    raise exception 'coach_block_in_progress' using errcode = '55000';
  end if;
  if p_block_start is null or p_intention is null
     or p_nutrition is null or jsonb_typeof(p_nutrition) <> 'object'
     or p_outcomes is null or jsonb_typeof(p_outcomes) <> 'array'
     or p_method is null or jsonb_typeof(p_method) <> 'object'
     or p_input_summary is null then
    raise exception 'invalid plan' using errcode = '22023';
  end if;
  if jsonb_typeof(p_nutrition->'phase') is distinct from 'string'
     or jsonb_typeof(p_nutrition->'phase_reason') is distinct from 'string'
     or jsonb_typeof(p_nutrition->'phase_options') is distinct from 'object' then
    raise exception 'invalid plan: phase keys are required' using errcode = '22023';
  end if;
  perform public.coach_assert_plan(v_uid, p_intention, p_block_start, p_nutrition, p_outcomes);

  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where user_id = v_uid and kind = 'plan' and status = 'pending'
      and plan_id in (select id from public.coach_plans where user_id = v_uid and status = 'proposed');
  update public.coach_plans set status = 'superseded' where user_id = v_uid and status = 'proposed';

  insert into public.coach_plans (user_id, block_start, intention, nutrition, outcomes, method,
                                  phase, phase_reason, phase_options)
  values (v_uid, p_block_start, p_intention, p_nutrition, p_outcomes, p_method,
          (p_nutrition->>'phase')::public.coach_phase, p_nutrition->>'phase_reason', p_nutrition->'phase_options')
  returning id into v_plan;

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
  values (v_uid, v_plan, 'plan', jsonb_build_object('nutrition', p_nutrition, 'outcomes', p_outcomes), p_input_summary)
  returning id into v_rec;

  return jsonb_build_object('plan_id', v_plan, 'recommendation_id', v_rec);
end;
$$;

revoke execute on function public.propose_coach_plan(date, public.training_intention, jsonb, jsonb, jsonb, jsonb)
  from public, anon;
grant execute on function public.propose_coach_plan(date, public.training_intention, jsonb, jsonb, jsonb, jsonb)
  to authenticated;

-- accept_coach_phase: the latest body (20270106100000) with the next-plan setting around propose_coach_plan.
create or replace function public.accept_coach_phase(
  p_review_id uuid,
  p_nutrition jsonb,
  p_outcomes jsonb,
  p_method jsonb,
  p_input_summary jsonb,
  p_block_start date default null
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(coalesce(p_block_start, (now() at time zone 'utc')::date));
  v_rev   public.coach_block_reviews;
  v_plan  public.coach_plans;
  v_res   jsonb;
begin
  select * into v_rev from public.coach_block_reviews where id = p_review_id and user_id = v_uid for update;
  if not found then
    raise exception 'review not found' using errcode = 'P0002';
  end if;
  if v_rev.phase_proposal is null or v_rev.phase_decision is not null then
    raise exception 'no open phase proposal' using errcode = '55000';
  end if;
  if p_nutrition is null or jsonb_typeof(p_nutrition) <> 'object'
     or p_nutrition->>'phase' is distinct from v_rev.phase_proposal->>'to_phase' then
    raise exception 'phase does not match the proposal' using errcode = '22023';
  end if;
  select * into v_plan from public.coach_plans where id = v_rev.plan_id and user_id = v_uid;

  perform set_config('app.coach_next_plan', 'on', true);
  v_res := public.propose_coach_plan(coalesce(p_block_start, (now() at time zone 'utc')::date), v_plan.intention,
                                     p_nutrition, p_outcomes, p_method, p_input_summary);
  perform set_config('app.coach_next_plan', 'off', true);

  update public.coach_block_reviews
    set phase_decision = 'accepted', decided_at = now(), next_plan_id = (v_res->>'plan_id')::uuid
    where id = v_rev.id returning * into v_rev;
  return jsonb_build_object('review', public.coach_block_review_json(v_rev), 'plan_id', v_res->'plan_id');
end;
$$;

revoke execute on function public.accept_coach_phase(uuid, jsonb, jsonb, jsonb, jsonb, date) from public, anon;
grant execute on function public.accept_coach_phase(uuid, jsonb, jsonb, jsonb, jsonb, date) to authenticated;

-- ---------------------------------------------------------------------------
-- (6) open_coach_block_new: the single entry of "Bloque nuevo". Null without an accepted saved block. Up to the block's
-- last day the review is the early review, computed on every call and stored nowhere (repeated opens cannot stack or
-- go stale); after it, the stored end-of-block review of T-233. `goal` compares the plan's intention with the one saved
-- in the training preferences; when they match, any goal-switch proposal left over from an earlier pick is dropped.
-- Nothing else is written: no block, plan, session or nutrition_goals row changes.
-- ---------------------------------------------------------------------------
create function public.open_coach_block_new(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_plan    public.coach_plans;
  v_end     date;
  v_built   jsonb;
  v_review  jsonb;
  v_saved   public.training_intention;
  v_kcal    integer;
  v_changed boolean;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted' and folder_id is not null;
  if not found then
    return null;
  end if;
  v_end := public.coach_block_end(v_plan.id);
  if v_end is null then
    return null;
  end if;

  if p_today <= v_end then
    v_built := public.coach_block_review_build(v_plan.id, p_today, p_today);
    v_review := jsonb_build_object(
      'id', null, 'plan_id', v_plan.id, 'ended_on', p_today, 'early', true, 'inputs', v_built->'inputs',
      'phase_proposal', null, 'phase_decision', null, 'decided_at', null, 'next_plan_id', null);
  else
    v_review := public.open_coach_block_review(p_today) || jsonb_build_object('early', false);
  end if;

  select tp.intention into v_saved from public.training_preferences tp where tp.user_id = v_uid;
  select g.target_kcal into v_kcal from public.nutrition_goals g where g.user_id = v_uid;
  v_changed := v_saved is not null and v_saved <> v_plan.intention;

  if not v_changed then
    update public.coach_recommendations
      set status = 'discarded', decided_at = now()
      where user_id = v_uid and kind = 'plan' and status = 'pending'
        and plan_id in (select id from public.coach_plans
                        where user_id = v_uid and status = 'proposed' and method->>'source' = 'goal_switch');
    update public.coach_plans set status = 'superseded'
      where user_id = v_uid and status = 'proposed' and method->>'source' = 'goal_switch';
  end if;

  return jsonb_build_object(
    'review', v_review,
    'goal', jsonb_build_object('changed', v_changed, 'plan_intention', v_plan.intention,
                               'saved_intention', v_saved, 'current_kcal', v_kcal));
end;
$$;

revoke execute on function public.open_coach_block_new(date) from public, anon;
grant execute on function public.open_coach_block_new(date) to authenticated;

-- ---------------------------------------------------------------------------
-- (7) propose_goal_switch: the figures come from the coach function (plan_goal_switch, no model, no usage);
-- propose_coach_plan re-checks every invariant and stores the new PROPOSED plan (the vehicle of the new block), tagged
-- method.source = goal_switch. An older proposal is superseded by propose_coach_plan. Nothing touches nutrition_goals
-- or the accepted plan: the block is ended and the plan accepted only by the save (save_built_coach_block).
-- ---------------------------------------------------------------------------
create function public.propose_goal_switch(
  p_today date,
  p_nutrition jsonb,
  p_outcomes jsonb,
  p_method jsonb,
  p_input_summary jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_saved public.training_intention;
  v_kcal  integer;
  v_res   jsonb;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted' and folder_id is not null;
  if not found then
    raise exception 'no saved block' using errcode = '55000';
  end if;
  select tp.intention into v_saved from public.training_preferences tp where tp.user_id = v_uid;
  if v_saved is null or v_saved = v_plan.intention then
    raise exception 'no goal change' using errcode = '22023';
  end if;
  if p_method is null or jsonb_typeof(p_method) <> 'object' then
    raise exception 'invalid plan' using errcode = '22023';
  end if;

  select g.target_kcal into v_kcal from public.nutrition_goals g where g.user_id = v_uid;

  perform set_config('app.coach_next_plan', 'on', true);
  v_res := public.propose_coach_plan(p_today, v_saved, p_nutrition, p_outcomes,
                                     p_method || jsonb_build_object('source', 'goal_switch'), p_input_summary);
  perform set_config('app.coach_next_plan', 'off', true);

  return jsonb_build_object('plan_id', v_res->'plan_id', 'recommendation_id', v_res->'recommendation_id',
                            'current_kcal', v_kcal, 'new_kcal', (p_nutrition->>'target_kcal')::int,
                            'phase', p_nutrition->>'phase');
end;
$$;

revoke execute on function public.propose_goal_switch(date, jsonb, jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.propose_goal_switch(date, jsonb, jsonb, jsonb, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- (9) coach_begin_next_block: the latest body (20270107100000). A proposed plan accepted by the save (review "Aceptar" or
-- goal switch) also starts on the save day (block_start), so the counters and the plan weeks of the new block restart
-- there and not on the day the proposal was made.
-- ---------------------------------------------------------------------------
create or replace function public.coach_begin_next_block(p_plan_id uuid, p_previous_plan_id uuid)
returns uuid
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_today date := (now() at time zone 'utc')::date;
  v_prev  public.coach_plans;
  v_plan  public.coach_plans;
  v_id    uuid;
begin
  if coalesce(current_setting('app.coach_block_save', true), '') <> 'on' then
    raise exception 'coach_begin_next_block is internal' using errcode = '42501';
  end if;
  if p_plan_id is null or p_previous_plan_id is null then
    raise exception 'invalid plan' using errcode = '22023';
  end if;

  select * into v_prev from public.coach_plans where id = p_previous_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;

  if p_plan_id <> p_previous_plan_id then
    select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
    if not found then
      raise exception 'plan not found' using errcode = 'P0002';
    end if;
    if v_plan.status not in ('proposed', 'accepted') or v_plan.folder_id is not null then
      raise exception 'plan is not open' using errcode = '55000';
    end if;
    if v_plan.status = 'proposed' then
      perform public.accept_coach_plan(p_plan_id, v_today);
      update public.coach_plans set block_start = v_today where id = p_plan_id and user_id = v_uid;
    end if;
    v_id := p_plan_id;
  else
    if v_prev.status <> 'accepted' or v_prev.folder_id is null then
      raise exception 'previous block is not the accepted block' using errcode = '55000';
    end if;
    update public.coach_plans set status = 'superseded' where id = v_prev.id and user_id = v_uid;
    insert into public.coach_plans (user_id, block_start, block_weeks, intention, nutrition, outcomes, method,
                                    status, accepted_at, phase, phase_reason, phase_options)
    values (v_uid, v_today, v_prev.block_weeks, v_prev.intention, v_prev.nutrition, v_prev.outcomes, v_prev.method,
            'accepted', now(), v_prev.phase, v_prev.phase_reason, v_prev.phase_options)
    returning id into v_id;
  end if;

  update public.coach_plans set training_start = v_today where id = v_id and user_id = v_uid;
  return v_id;
end;
$$;

revoke execute on function public.coach_begin_next_block(uuid, uuid) from public, anon;
grant execute on function public.coach_begin_next_block(uuid, uuid) to authenticated;
