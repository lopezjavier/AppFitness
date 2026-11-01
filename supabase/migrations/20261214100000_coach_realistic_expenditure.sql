-- T-180: realistic expenditure (planning/04 sections 7.2-7.4, ADR-77 in planning/00-decisions.md).
-- The adaptive expenditure anchors on a capped 28-day intake and only adds the weight-slope term when the trend has
-- enough data (4 weekly averages, 8 weigh-in days, reliable diary). Before this, a slope from 5 weigh-ins moved the
-- expenditure by +289 kcal. New JSON keys only: coach_weight_trend.weigh_in_days, coach_intake.anchor_kcal and, in
-- coach_compute, total_expenditure.slope_used / anchor_kcal, weight_trend.weigh_in_days, intake.mean_kcal / anchor_kcal.

create or replace function public.coach_weight_trend(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := (select auth.uid());
  v_start date := (p_today - (extract(isodow from p_today)::int - 1)) - 49;
  v_t     numeric[] := array[12.706, 4.303, 3.182, 2.776, 2.571, 2.447];
  v_n     integer;
  v_days  integer;
  v_slope double precision;
  v_sxx   double precision;
  v_syy   double precision;
  v_sxy   double precision;
  v_half  numeric;
  v_low   numeric;
  v_high  numeric;
  v_se    double precision;
begin
  with daily as (
    select (measured_at at time zone 'UTC')::date as d, avg(weight_kg) as w
    from public.body_measurements
    where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and p_today
    group by 1
  ), weekly as (
    select (d - (extract(isodow from d)::int - 1) - v_start) / 7 as x, avg(w)::double precision as y
    from daily group by 1
  )
  select count(*), regr_slope(y, x), regr_sxx(y, x), regr_syy(y, x), regr_sxy(y, x), (select count(*) from daily)
  into v_n, v_slope, v_sxx, v_syy, v_sxy, v_days
  from weekly;

  if v_n < 2 then
    return jsonb_build_object('slope', null, 'low', null, 'high', null, 'weeks', v_n, 'weigh_in_days', v_days,
                              'low_confidence', true, 'missing', jsonb_build_array('weigh_ins'));
  end if;
  if v_n = 2 then
    return jsonb_build_object('slope', round(v_slope::numeric, 3), 'low', null, 'high', null, 'weeks', v_n,
                              'weigh_in_days', v_days, 'low_confidence', true, 'missing', '[]'::jsonb);
  end if;

  v_se := sqrt(greatest(0, v_syy - v_sxy * v_sxy / v_sxx) / (v_n - 2)) / sqrt(v_sxx);
  v_half := v_t[v_n - 2] * v_se::numeric;
  v_low := round(v_slope::numeric - v_half, 3);
  v_high := round(v_slope::numeric + v_half, 3);
  return jsonb_build_object('slope', round(v_slope::numeric, 3), 'low', v_low, 'high', v_high, 'weeks', v_n,
                            'weigh_in_days', v_days, 'low_confidence', v_n < 4, 'missing', '[]'::jsonb);
end;
$$;

-- Intake over the 28 days ending p_today. anchor_kcal (04 section 7.3): with a reliable diary, m = median of the daily
-- sums; days under 40 % of m are partial logging and drop out, days over 150 % of m are capped at 150 % of m; the anchor
-- is the rounded mean of what remains. mean_kcal stays the raw mean of the daily sums.
create or replace function public.coach_intake(p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with d as (
    select log_date, sum(kcal) as kcal
    from public.food_log_entries
    where user_id = (select auth.uid()) and log_date between p_today - 27 and p_today
    group by log_date
  ), m as (
    select (percentile_cont(0.5) within group (order by kcal::double precision))::numeric as med from d
  ), a as (
    select round(avg(least(d.kcal, m.med * 1.5)), 0) as anchor
    from d cross join m
    where d.kcal >= m.med * 0.4
  )
  select jsonb_build_object(
    'logged_days', count(*),
    'window_days', 28,
    'ratio', round(count(*) / 28.0, 3),
    'reliable', count(*) * 10 >= 28 * 7,
    'mean_kcal', case when count(*) > 0 then round(avg(kcal), 0) end,
    'anchor_kcal', case when count(*) * 10 >= 28 * 7 then (select anchor from a) end)
  from d;
$$;

create or replace function public.coach_compute(p_today date, p_level_override public.activity_level default null)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid        uuid := (select auth.uid());
  v_act        jsonb := public.coach_activity_estimate(p_today);
  v_trend      jsonb := public.coach_weight_trend(p_today);
  v_intake     jsonb := public.coach_intake(p_today);
  v_exp        jsonb := public.coach_experience(p_today);
  v_basal      numeric := public.coach_basal_kcal(p_today);
  r            public.coach_confirmed_values;
  v_est_level  public.activity_level := (v_act->>'level')::public.activity_level;
  v_est_factor numeric := public.coach_activity_factor(v_est_level);
  v_act_unc    numeric := case when v_act->>'source' = 'data' then 0.1 else 0.2 end;
  v_act_reask  boolean := false;
  v_act_conf   boolean := false;
  v_act_edited boolean := false;
  v_eff_level  public.activity_level;
  v_eff_factor numeric;
  v_reliable   boolean := (v_intake->>'reliable')::boolean;
  v_weeks      integer := (v_trend->>'weeks')::int;
  v_slope      numeric := (v_trend->>'slope')::numeric;
  v_anchor     numeric := (v_intake->>'anchor_kcal')::numeric;
  v_adaptive   boolean;
  v_slope_used boolean;
  v_tdee       numeric;
  v_tdee_unc   numeric;
  v_tdee_miss  text[] := array[]::text[];
  v_tdee_reask boolean := false;
  v_tdee_conf  boolean := false;
  v_tr_unc     numeric;
  v_tr_reask   boolean := false;
  v_tr_conf    boolean := false;
  v_tr_miss    jsonb := v_trend->'missing';
  v_ex_ord     integer := (v_exp->>'ordinal')::int;
  v_ex_reask   boolean := false;
  v_ex_conf    boolean := false;
begin
  -- Activity level.
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'activity_level';
  if found then
    v_act_reask := abs(v_est_factor - r.estimate) > r.uncertainty;
    v_act_conf := not v_act_reask;
    v_act_edited := v_act_conf and r.edited;
  end if;
  v_eff_level := coalesce(p_level_override,
                          case when v_act_conf then coalesce(r.level_value, v_est_level) else v_est_level end);
  v_eff_factor := public.coach_activity_factor(v_eff_level);

  -- Total expenditure (04 section 7.2): the capped intake anchor, plus the slope term only with enough weight data.
  v_adaptive := v_reliable and v_anchor is not null;
  v_slope_used := v_adaptive and v_slope is not null and v_weeks >= 4
                  and (v_trend->>'weigh_in_days')::int >= 8;
  if v_adaptive then
    v_tdee := round(v_anchor - case when v_slope_used then v_slope * 7700 / 7 else 0 end);
  elsif v_basal is not null then
    v_tdee := round(v_basal * v_eff_factor);
  else
    v_tdee_miss := v_tdee_miss || 'profile_data'::text;
  end if;
  if not v_reliable then v_tdee_miss := v_tdee_miss || 'diary'::text; end if;
  v_tdee_unc := case when v_tdee is not null then round(v_tdee * 0.1, 1) end;
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'total_expenditure';
  if found and v_tdee is not null then
    v_tdee_reask := abs(v_tdee - r.estimate) > r.uncertainty;
    v_tdee_conf := not v_tdee_reask;
  end if;

  -- Weight trend.
  v_tr_unc := coalesce(((v_trend->>'high')::numeric - v_slope), 0.5);
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'weight_trend';
  if found and v_slope is not null then
    v_tr_reask := abs(v_slope - r.estimate) > r.uncertainty;
    v_tr_conf := not v_tr_reask;
  end if;

  -- Experience.
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'experience';
  if found then
    v_ex_reask := abs(v_ex_ord - r.estimate) > r.uncertainty;
    v_ex_conf := not v_ex_reask;
  end if;

  return jsonb_build_object(
    'as_of', p_today,
    'activity_level', jsonb_build_object(
      'value', v_eff_level,
      'estimate', v_est_level,
      'factor', v_eff_factor,
      'source', v_act->>'source',
      'provisional', not v_act_conf,
      'confirmed', v_act_conf,
      'edited', v_act_edited,
      'reask', v_act_reask,
      'uncertainty', v_act_unc,
      'sources', jsonb_build_object(
        'steps_avg', v_act->'steps_avg',
        'steps_days', v_act->'steps_days',
        'active_kcal_avg', v_act->'active_kcal_avg',
        'active_kcal_days', v_act->'active_kcal_days',
        'sessions_per_week', v_act->'sessions_per_week'),
      'missing', v_act->'missing'),
    'basal_kcal', jsonb_build_object(
      'value', v_basal,
      'missing', case when v_basal is null then jsonb_build_array('profile_data') else '[]'::jsonb end),
    'total_expenditure', jsonb_build_object(
      'value', v_tdee,
      'source', case when v_adaptive then 'adaptive' else 'formula' end,
      'slope_used', v_slope_used,
      'anchor_kcal', v_anchor,
      'unreliable', not v_reliable,
      'allow_calorie_changes', v_reliable,
      'provisional', not v_tdee_conf,
      'confirmed', v_tdee_conf,
      'reask', v_tdee_reask,
      'uncertainty', v_tdee_unc,
      'missing', to_jsonb(v_tdee_miss)),
    'weight_trend', jsonb_build_object(
      'slope_kg_week', v_trend->'slope',
      'interval_low', v_trend->'low',
      'interval_high', v_trend->'high',
      'weeks', v_weeks,
      'weigh_in_days', v_trend->'weigh_in_days',
      'low_confidence', (v_trend->>'low_confidence')::boolean,
      'provisional', not v_tr_conf,
      'confirmed', v_tr_conf,
      'reask', v_tr_reask,
      'uncertainty', v_tr_unc,
      'missing', v_tr_miss),
    'intake', jsonb_build_object(
      'logged_days', v_intake->'logged_days',
      'window_days', v_intake->'window_days',
      'ratio', v_intake->'ratio',
      'reliable', v_reliable,
      'mean_kcal', v_intake->'mean_kcal',
      'anchor_kcal', v_intake->'anchor_kcal'),
    'experience', jsonb_build_object(
      'value', (array['beginner', 'intermediate', 'advanced'])[v_ex_ord + 1],
      'sessions', v_exp->'sessions',
      'weeks', v_exp->'weeks',
      'provisional', not v_ex_conf,
      'confirmed', v_ex_conf,
      'reask', v_ex_reask,
      'uncertainty', 0.5,
      'missing', case when (v_exp->>'sessions')::int = 0 then jsonb_build_array('training_history') else '[]'::jsonb end));
end;
$$;
