-- T-185 (US-81, US-68, US-88; planning/04 section 7.5): coach_compute reads coach_user_intake. Same body as
-- 20261214100000 plus: experience = higher of history and self-reported years (source, self_reported_years), situation
-- (28-day rule), the known maintenance as a provisional expenditure prior (source intake) with the one-time handover
-- flag, and profile_intake. known_cut_kcal and the free text feed no calculation. Without an intake the figures are
-- unchanged.

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
  v_in         public.coach_user_intake;
  v_in_found   boolean;
  v_self_ord   integer;
  v_ex_source  text := 'history';
  v_last_sess  date;
  v_days_since integer;
  v_sit        text;
  v_sit_source text;
  v_tdee_src   text;
  v_handover   boolean := false;
begin
  select * into v_in from public.coach_user_intake where user_id = v_uid;
  v_in_found := found;

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
    v_tdee_src := 'adaptive';
    v_handover := v_in_found and v_in.known_maintenance_kcal is not null and v_in.maintenance_handover_at is null;
  elsif v_in_found and v_in.known_maintenance_kcal is not null then
    -- T-185: what the user knows about their maintenance is a provisional prior until the diary is reliable.
    v_tdee := v_in.known_maintenance_kcal;
    v_tdee_src := 'intake';
  elsif v_basal is not null then
    v_tdee := round(v_basal * v_eff_factor);
    v_tdee_src := 'formula';
  else
    v_tdee_miss := v_tdee_miss || 'profile_data'::text;
    v_tdee_src := 'formula';
  end if;
  if not v_reliable then v_tdee_miss := v_tdee_miss || 'diary'::text; end if;
  v_tdee_unc := case when v_tdee is not null then round(v_tdee * 0.1, 1) end;
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'total_expenditure';
  if found and v_tdee is not null and v_tdee_src <> 'intake' then
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

  -- Experience: the higher of the history and what the user said (04 section 7.5); history wins once it is higher.
  if v_in_found and v_in.training_years is not null then
    v_self_ord := case when v_in.training_years < 0.5 then 0 when v_in.training_years < 2 then 1 else 2 end;
    if v_self_ord > v_ex_ord then
      v_ex_ord := v_self_ord;
      v_ex_source := 'intake';
    end if;
  end if;
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'experience';
  if found then
    v_ex_reask := abs(v_ex_ord - r.estimate) > r.uncertainty;
    v_ex_conf := not v_ex_reask;
  end if;

  -- Situation (04 section 7.5): 28 days without a completed session means returning; the end of returning is T-190.
  select max((started_at at time zone 'UTC')::date) into v_last_sess
  from public.workout_sessions
  where user_id = v_uid and status = 'completed' and (started_at at time zone 'UTC')::date <= p_today;
  v_days_since := p_today - v_last_sess;
  if v_in_found and v_in.situation in ('returning_break', 'returning_injury') then
    v_sit := v_in.situation::text;
    v_sit_source := 'intake';
  elsif v_days_since >= 28
        and (not v_in_found or v_in.situation is null or v_in.situation in ('first_time', 'continuous')) then
    v_sit := 'returning_break';
    v_sit_source := 'sessions';
  elsif v_in_found and v_in.situation is not null then
    v_sit := v_in.situation::text;
    v_sit_source := 'intake';
  else
    v_sit := case when (v_exp->>'sessions')::int > 0 then 'continuous' else 'first_time' end;
    v_sit_source := 'history';
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
      'source', v_tdee_src,
      'slope_used', v_slope_used,
      'anchor_kcal', v_anchor,
      'unreliable', not v_reliable,
      'allow_calorie_changes', v_reliable,
      'provisional', v_tdee_src = 'intake' or not v_tdee_conf,
      'confirmed', v_tdee_conf,
      'reask', v_tdee_reask,
      'uncertainty', v_tdee_unc,
      'handover', v_handover,
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
      'source', v_ex_source,
      'self_reported_years', case when v_in_found then to_jsonb(v_in.training_years) end,
      'provisional', not v_ex_conf,
      'confirmed', v_ex_conf,
      'reask', v_ex_reask,
      'uncertainty', 0.5,
      'missing', case when (v_exp->>'sessions')::int = 0 and v_ex_source = 'history' then jsonb_build_array('training_history')
                  else '[]'::jsonb end),
    'situation', jsonb_build_object(
      'value', v_sit,
      'source', v_sit_source,
      'days_since_last_session', v_days_since),
    'profile_intake', jsonb_build_object(
      'saved', v_in_found,
      'known_cut_kcal', case when v_in_found then to_jsonb(v_in.known_cut_kcal) end,
      'has_pre_break_bests', v_in_found and jsonb_array_length(v_in.pre_break_bests) > 0));
end;
$$;
