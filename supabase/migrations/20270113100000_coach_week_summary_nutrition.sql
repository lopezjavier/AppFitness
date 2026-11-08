-- T-246 (E16, US-112): the nutrition section of the weekly summary. Rules: planning/07-coach-design.md section 4
-- "Nutrition follow-up (E16)" (a); planning/04-business-rules.md section 6.7. Deterministic, no model call.
-- No table or column changes: coach_week_summary_week gains the `nutrition` object; every legacy key is unchanged.

-- ---------------------------------------------------------------------------
-- coach_complete_days: the complete diary days of the 7 days p_end-6..p_end with their sums. A day is complete when its
-- diary sum is at least 40 % of the median daily sum of the 28 days ending p_end (the same rule as coach_week_gate,
-- which is left as it is: its complete_days count equals the rows returned here).
-- ---------------------------------------------------------------------------
create function public.coach_complete_days(p_uid uuid, p_end date)
returns table (log_date date, kcal numeric, protein_g numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with d as (
    select f.log_date, sum(f.kcal) as kcal, sum(f.protein_g) as protein_g
    from public.food_log_entries f
    where f.user_id = p_uid and f.log_date between p_end - 27 and p_end
    group by f.log_date
  ), m as (
    select (percentile_cont(0.5) within group (order by d.kcal::double precision))::numeric as med from d
  )
  select d.log_date, d.kcal::numeric, d.protein_g::numeric
  from d cross join m
  where d.log_date >= p_end - 6 and d.kcal >= m.med * 0.4
  order by d.log_date;
$$;

revoke execute on function public.coach_complete_days(uuid, date) from public, anon;
grant execute on function public.coach_complete_days(uuid, date) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_week_summary_week: the body of 20270105100000 plus `nutrition`, with the previous-week stalled lifts computed
-- once. Goal in force per day (nutrition_goals keeps no history): the current goal, minus the calorie changes accepted
-- after that day; in a diet-break week (plan week 4, diet break on, goal not edited by the user) the diet-break figures.
-- When today is itself in a diet-break week the goals row holds the break figures, so the base figures come from the plan.
-- A user edit of the goal is not recoverable: the current row is used for every day.
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
  v_goal      public.nutrition_goals;
  v_break_on  boolean;
  v_today_brk boolean;
  v_base_kcal numeric;
  v_base_prot numeric;
  v_brk_kcal  numeric;
  v_brk_prot  numeric;
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

    -- Goal in force. Base figures: the goals row, or the plan's base figures while the row holds the break week's.
    select * into v_goal from public.nutrition_goals where user_id = v_uid;
    v_break_on := coalesce(v_plan.phase_options->>'diet_break', 'false') = 'true'
                  and jsonb_typeof(v_plan.nutrition->'diet_break') = 'object'
                  and not coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false);
    v_today_brk := v_break_on and public.coach_plan_week_of_date_for(v_uid, v_plan.id, p_today) = 4;
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

    select avg(case when v_break_on and public.coach_plan_week_of_date_for(v_uid, v_plan.id, c.log_date) = 4
                    then v_brk_kcal
                    else v_base_kcal - coalesce((select sum((r.payload->>'to_kcal')::int - (r.payload->>'from_kcal')::int)
                                                 from public.coach_recommendations r
                                                 where r.user_id = v_uid and r.plan_id = v_plan.id
                                                   and r.kind = 'calorie_change' and r.status = 'accepted'
                                                   and (r.decided_at at time zone 'utc')::date > c.log_date), 0) end),
           avg(case when v_break_on and public.coach_plan_week_of_date_for(v_uid, v_plan.id, c.log_date) = 4
                    then v_brk_prot else v_base_prot end)
    into v_tgt_kcal, v_tgt_prot
    from public.coach_complete_days(v_uid, v_end) c;

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
