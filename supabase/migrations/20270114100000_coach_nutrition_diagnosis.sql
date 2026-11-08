-- T-247 (E16, US-113/US-114): "why is my weight not moving" for nutrition and the plateau explanation.
-- Rules: planning/07-coach-design.md section 4 "Nutrition follow-up (E16)" (b), (c); planning/04-business-rules.md 6.7.
-- Deterministic, no model call. No table or column changes.

-- ---------------------------------------------------------------------------
-- coach_daily_goals: the goal in force on each day p_from..p_to (nutrition_goals keeps no history): the current goal,
-- minus the calorie changes accepted after that day; in a diet-break week (plan week 4, diet break on, goal not edited
-- by the user) the diet-break figures. When p_today is itself in a diet-break week the goals row holds the break
-- figures, so the base figures come from the plan. A user edit of the goal is not recoverable: the current row is used.
-- Extracted from coach_week_summary_week (20270113100000) so the summary and the diagnosis share one rule.
-- ---------------------------------------------------------------------------
create function public.coach_daily_goals(p_uid uuid, p_plan_id uuid, p_today date, p_from date, p_to date)
returns table (log_date date, kcal numeric, protein_g numeric)
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_plan      public.coach_plans;
  v_goal      public.nutrition_goals;
  v_break_on  boolean;
  v_today_brk boolean;
  v_base_kcal numeric;
  v_base_prot numeric;
  v_brk_kcal  numeric;
  v_brk_prot  numeric;
begin
  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = p_uid;
  if not found then
    return;
  end if;

  select * into v_goal from public.nutrition_goals where user_id = p_uid;
  v_break_on := coalesce(v_plan.phase_options->>'diet_break', 'false') = 'true'
                and jsonb_typeof(v_plan.nutrition->'diet_break') = 'object'
                and not coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false);
  v_today_brk := v_break_on and public.coach_plan_week_of_date_for(p_uid, p_plan_id, p_today) = 4;
  if v_today_brk or v_goal.user_id is null then
    v_base_kcal := (v_plan.nutrition->>'target_kcal')::numeric;
    v_base_prot := (v_plan.nutrition->>'target_kcal')::numeric * (v_plan.nutrition->>'protein_pct')::numeric / 400;
  else
    v_base_kcal := v_goal.target_kcal;
    v_base_prot := v_goal.target_kcal::numeric * v_goal.protein_pct / 400;
  end if;
  if v_break_on then
    v_brk_kcal := (v_plan.nutrition->'diet_break'->>'target_kcal')::numeric;
    v_brk_prot := v_brk_kcal * (v_plan.nutrition->'diet_break'->>'protein_pct')::numeric / 400;
  end if;

  return query
  select d.d::date,
         case when v_break_on and public.coach_plan_week_of_date_for(p_uid, p_plan_id, d.d::date) = 4
              then v_brk_kcal
              else v_base_kcal - coalesce((select sum((r.payload->>'to_kcal')::int - (r.payload->>'from_kcal')::int)
                                           from public.coach_recommendations r
                                           where r.user_id = p_uid and r.plan_id = p_plan_id
                                             and r.kind = 'calorie_change' and r.status = 'accepted'
                                             and (r.decided_at at time zone 'utc')::date > d.d::date), 0) end,
         case when v_break_on and public.coach_plan_week_of_date_for(p_uid, p_plan_id, d.d::date) = 4
              then v_brk_prot else v_base_prot end
  from generate_series(p_from, p_to, interval '1 day') d(d);
end;
$$;

revoke execute on function public.coach_daily_goals(uuid, uuid, date, date, date) from public, anon;
grant execute on function public.coach_daily_goals(uuid, uuid, date, date, date) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_nutrition_diagnosis: null (no card) or {kind, ...figures}. p_end = last day of the summary's week E;
-- W1 = E-6..E, W0 = E-13..E-7. p_today only anchors the goal figures (see coach_daily_goals); default: today (UTC).
-- Only the caller's own data: another p_uid gives null.
-- ---------------------------------------------------------------------------
create function public.coach_nutrition_diagnosis(p_uid uuid, p_plan_id uuid, p_end date, p_today date default null)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_plan    public.coach_plans;
  v_today   date := coalesce(p_today, (now() at time zone 'utc')::date);
  v_rate    numeric;
  v_t1      jsonb;
  v_t0      jsonb;
  v_slope   numeric;
  v_low1    numeric;
  v_high1   numeric;
  v_low0    numeric;
  v_high0   numeric;
  v_break_on boolean;
  v_n1      integer;
  v_n0      integer;
  v_wi1     integer;
  v_wi0     integer;
  v_dir     text;
  v_thr     numeric;
  v_logged  numeric;
  v_goal    numeric;
  v_excess  numeric;
  v_wkend   numeric;
begin
  if p_uid is null or p_plan_id is null or p_end is null or p_uid is distinct from (select auth.uid()) then
    return null;
  end if;
  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = p_uid;
  if not found then
    return null;
  end if;

  v_rate := (v_plan.nutrition->>'rate_kg_week')::numeric;
  v_t1 := public.coach_weight_trend(p_end);
  v_t0 := public.coach_weight_trend(p_end - 7);
  v_slope := (v_t1->>'slope')::numeric;
  v_low1 := (v_t1->>'low')::numeric;
  v_high1 := (v_t1->>'high')::numeric;
  v_low0 := (v_t0->>'low')::numeric;
  v_high0 := (v_t0->>'high')::numeric;
  if v_rate is null or v_slope is null or v_low1 is null or v_high1 is null or v_low0 is null or v_high0 is null then
    return null;
  end if;
  -- Trigger: strictly outside on both summaries and on the same side; on the edge counts as inside (same as 04 7.6).
  if not ((v_rate < v_low1 and v_rate < v_low0) or (v_rate > v_high1 and v_rate > v_high0)) then
    return null;
  end if;

  -- A window containing a diet-break week does not trigger: its target differs.
  v_break_on := coalesce(v_plan.phase_options->>'diet_break', 'false') = 'true'
                and jsonb_typeof(v_plan.nutrition->'diet_break') = 'object'
                and not coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false);
  if v_break_on and exists (select 1 from generate_series(p_end - 13, p_end, interval '1 day') d(d)
                            where public.coach_plan_week_of_date_for(p_uid, p_plan_id, d.d::date) = 4) then
    return null;
  end if;

  -- Check 1: logging (the earlier short week is named).
  select count(*)::int into v_n1 from public.coach_complete_days(p_uid, p_end);
  select count(*)::int into v_n0 from public.coach_complete_days(p_uid, p_end - 7);
  if v_n0 < 5 then
    return jsonb_build_object('kind', 'logging_food', 'n', v_n0, 'week_start', p_end - 13);
  elsif v_n1 < 5 then
    return jsonb_build_object('kind', 'logging_food', 'n', v_n1, 'week_start', p_end - 6);
  end if;
  v_wi0 := (public.coach_week_gate(p_end - 7)->>'weigh_in_days')::int;
  v_wi1 := (public.coach_week_gate(p_end)->>'weigh_in_days')::int;
  if v_wi0 < 3 then
    return jsonb_build_object('kind', 'logging_weigh_in', 'n', v_wi0, 'week_start', p_end - 13);
  elsif v_wi1 < 3 then
    return jsonb_build_object('kind', 'logging_weigh_in', 'n', v_wi1, 'week_start', p_end - 6);
  end if;

  -- Check 2: average calories. The direction that explains the miss is the sign of (rate - slope): a slower cut or a
  -- faster gain is explained by eating above the goal, the opposite by eating below it.
  v_dir := case when v_rate - v_slope < 0 then 'above' else 'below' end;
  v_thr := 0.5 * abs(v_rate - v_slope) * 7700 / 7;
  with cd as (
    select * from public.coach_complete_days(p_uid, p_end)
    union all
    select * from public.coach_complete_days(p_uid, p_end - 7)
  ), x as (
    select cd.log_date, cd.kcal as logged, g.kcal as goal,
           case when v_dir = 'above' then cd.kcal - g.kcal else g.kcal - cd.kcal end as ex
    from cd
    join public.coach_daily_goals(p_uid, p_plan_id, v_today, p_end - 13, p_end) g on g.log_date = cd.log_date
  )
  select avg(logged), avg(goal),
         coalesce(sum(greatest(ex, 0)), 0),
         coalesce(sum(greatest(ex, 0)) filter (where extract(dow from log_date) in (0, 6)), 0)
  into v_logged, v_goal, v_excess, v_wkend
  from x;
  if v_logged is not null and
     (case when v_dir = 'above' then v_logged - v_goal else v_goal - v_logged end) >= v_thr then
    return jsonb_build_object('kind', 'calories', 'avg_kcal', round(v_logged)::int, 'goal_kcal', round(v_goal)::int,
                              'diff_kcal', round(abs(v_logged - v_goal))::int, 'direction', v_dir,
                              'weekend', v_excess > 0 and v_wkend * 2 >= v_excess);
  end if;

  -- Check 3: plateau. A flat trend on near-complete logs, in a phase with a non-zero rate; exactly 5 days is not enough.
  if v_rate <> 0 and v_low1 <= 0 and v_high1 >= 0 and v_low0 <= 0 and v_high0 >= 0
     and v_n1 >= 6 and v_n0 >= 6 then
    return jsonb_build_object('kind', 'plateau', 'slope', v_slope, 'low', v_low1, 'high', v_high1);
  end if;

  -- Check 4: no clear cause.
  return jsonb_build_object('kind', 'none');
end;
$$;

revoke execute on function public.coach_nutrition_diagnosis(uuid, uuid, date, date) from public, anon;
grant execute on function public.coach_nutrition_diagnosis(uuid, uuid, date, date) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_week_summary_week: the body of 20270113100000 with the goal-in-force logic moved into coach_daily_goals and
-- `nutrition.diagnosis` added (key absent when there is no card). Paused weeks stay {paused:true} only.
-- ---------------------------------------------------------------------------
create or replace function public.coach_week_summary_week(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid       uuid := public.coach_guard(p_today);
  v_plan      public.coach_plans;
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
  v_prev      uuid[];
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

  select w into v_w
  from jsonb_array_elements(public.coach_plan_weeks(v_plan.id, p_today)) w
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

  -- First found: stalled now and not stalled one plan week earlier (fewer than 3 weeks done there: not stalled).
  -- The previous week's stalled lifts are computed once.
  select coalesce(array_agg(p.lift_id), '{}'::uuid[]) into v_prev
  from public.coach_stalled_lifts(v_plan.id, v_week - 1) p;
  select coalesce(jsonb_agg(s.e order by s.ord), '[]'::jsonb) into v_stalls
  from jsonb_array_elements(public.coach_stall_diagnosis(v_plan.id, v_week)) with ordinality s(e, ord)
  where not ((s.e->>'exercise_id')::uuid = any (v_prev));

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

revoke execute on function public.coach_week_summary_week(date) from public, anon;
grant execute on function public.coach_week_summary_week(date) to authenticated;

-- ---------------------------------------------------------------------------
-- evaluate_coach_calorie_change: the body of 20270108100000 plus one rule. When the diagnosis names the average
-- calories as the cause, no proposal is made and the 14-day clock is not restarted (nothing is written). E is the end
-- of the latest past plan week, the same frame as the summary, so the card and the proposal agree.
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
  v_wend    date;
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

  -- The calories cause (07 section 4 (b) check 2): the goal is not the problem, so no proposal and no clock restart.
  select (w->>'end_date')::date into v_wend
  from jsonb_array_elements(public.coach_plan_weeks(v_plan.id, p_today)) w
  where w->>'phase' = 'past'
  order by (w->>'week')::int desc
  limit 1;
  if v_wend is not null
     and public.coach_nutrition_diagnosis(v_uid, v_plan.id, v_wend, p_today)->>'kind' = 'calories' then
    return jsonb_build_object('status', 'none', 'reason', 'calorie_cause');
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
