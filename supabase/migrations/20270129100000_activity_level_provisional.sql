-- T-308 (US-140, ADR-109): the activity level is owned by the trainer and its "Provisional" chips follow the data.
-- 1. coach_compute: with measured data (activity source 'data') the level is the estimate: no stored confirmation or
--    override applies, it is not provisional and never re-asked (new flag `measured`). Maintenance backed by the weight
--    slope and an experience that reached the reported class are no longer provisional (07 section 2 Rule B).
-- 2. coach_unconfirmed_keys: the activity level blocks accepting a plan only on the formula path (BMR x factor).
-- 3. coach_plan_inputs('plan'): the activity entry is ok when measured and carries its `basis`.
-- 4. confirm_derived_values: an edited level is ignored while the level is measured.
-- Bodies are the live definitions (20261221100000, 20270109100000, 20270127100000, 20261205100000). No data changes.

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
  v_measured   boolean := v_act->>'source' = 'data';
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
  v_tdee_src   text;
  v_handover   boolean := false;
begin
  select * into v_in from public.coach_user_intake where user_id = v_uid;
  v_in_found := found;

  -- Activity level. Measured: the estimate, nothing stored or passed in applies (T-308).
  if v_measured then
    v_eff_level := v_est_level;
  else
    select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'activity_level';
    if found then
      v_act_reask := abs(v_est_factor - r.estimate) > r.uncertainty;
      v_act_conf := not v_act_reask;
      v_act_edited := v_act_conf and r.edited;
    end if;
    v_eff_level := coalesce(p_level_override,
                            case when v_act_conf then coalesce(r.level_value, v_est_level) else v_est_level end);
  end if;
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

  return jsonb_build_object(
    'as_of', p_today,
    'activity_level', jsonb_build_object(
      'value', v_eff_level,
      'estimate', v_est_level,
      'factor', v_eff_factor,
      'source', v_act->>'source',
      'measured', v_measured,
      'provisional', not (v_act_conf or v_measured),
      'confirmed', v_act_conf or v_measured,
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
      'provisional', case when v_tdee_src = 'intake' then true
                          when v_tdee_src = 'adaptive' then not (v_tdee_conf or v_slope_used)
                          else not v_tdee_conf end,
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
      -- Rule B: a self-report that the history has reached is backed by data.
      'provisional', not (v_ex_conf or (v_in_found and v_in.training_years is not null and v_ex_source = 'history')),
      'confirmed', v_ex_conf,
      'reask', v_ex_reask,
      'uncertainty', 0.5,
      'missing', case when (v_exp->>'sessions')::int = 0 and v_ex_source = 'history' then jsonb_build_array('training_history')
                  else '[]'::jsonb end),
    'profile_intake', jsonb_build_object('saved', v_in_found));
end;
$$;

-- The activity level blocks only on the formula path, where it changes the expenditure (04 section 7.2).
create or replace function public.coach_unconfirmed_keys(p_est jsonb)
returns text[]
language sql
immutable
security invoker
set search_path = ''
as $$
  select coalesce(array_agg(k order by o), '{}'::text[])
  from (
    select 1 as o, 'activity_level' as k
    where coalesce((p_est->'activity_level'->>'provisional')::boolean, false)
      and (p_est->'total_expenditure'->>'value' is null
           or coalesce(p_est->'total_expenditure'->>'source', '') = 'formula')
    union all
    select 2, 'total_expenditure'
    where p_est->'total_expenditure'->>'value' is not null
      and coalesce((p_est->'total_expenditure'->>'provisional')::boolean, false)
      and coalesce(p_est->'total_expenditure'->>'source', '') <> 'intake'
    union all
    select 3, 'experience'
    where coalesce((p_est->'experience'->>'provisional')::boolean, false)
    union all
    select 4, 'weight_trend'
    where p_est->'weight_trend'->>'slope_kg_week' is not null
      and coalesce((p_est->'weight_trend'->>'provisional')::boolean, false)
  ) t;
$$;

create or replace function public.coach_plan_inputs(p_kind text, p_today date)
returns jsonb
language plpgsql
stable
set search_path to ''
as $function$
declare
  v_uid       uuid := public.coach_guard(p_today);
  v_est       jsonb;
  v_prof      public.profiles;
  v_tp        public.training_preferences;
  v_weigh_at  date;
  v_weigh_kg  numeric;
  v_current   text;
  v_tdee      jsonb;
  v_trend     jsonb;
  v_act       jsonb;
  v_age       integer;
  v_has_tdee  boolean;
  v_entries   jsonb := '[]'::jsonb;
  v_intention jsonb;
  v_cardio    public.equipment[];
  v_lifting   public.equipment[];
  v_intake    public.coach_user_intake;
  v_blockers  text[];
  v_measured  boolean;
begin
  if p_kind is null or p_kind not in ('plan', 'block') then
    raise exception 'kind must be plan or block' using errcode = '22023';
  end if;

  select * into v_prof from public.profiles where id = v_uid;
  select * into v_tp from public.training_preferences where user_id = v_uid;
  select * into v_intake from public.coach_user_intake where user_id = v_uid;
  v_est := public.coach_compute(p_today, null);

  -- Cardio machines are a group of their own (T-184): the lifting list never carries them, an empty cardio list is
  -- a valid answer (the coach proposes walking), never missing.
  select coalesce(array_agg(e order by o) filter (where e = 'cardio_machine'), '{}'),
         coalesce(array_agg(e order by o) filter (where e <> 'cardio_machine'), '{}')
  into v_cardio, v_lifting
  from unnest(coalesce(v_tp.equipment, '{}'::public.equipment[])) with ordinality t(e, o);

  select case when exists (select 1 from public.coach_plans where user_id = v_uid and status = 'accepted') then 'accepted'
              when exists (select 1 from public.coach_plans where user_id = v_uid and status = 'proposed') then 'proposed'
         end
  into v_current;

  v_intention := public.coach_input_entry(
    'intention', to_jsonb(v_tp.intention), 'my_training',
    case when v_tp.intention is null then 'missing' else 'ok' end,
    v_tp.intention is null, 'my_training');

  if p_kind = 'plan' then
    v_tdee := v_est->'total_expenditure';
    v_trend := v_est->'weight_trend';
    v_act := v_est->'activity_level';
    v_measured := coalesce((v_act->>'measured')::boolean, false);
    v_has_tdee := v_tdee->>'value' is not null;
    v_age := case when v_prof.birth_date is null then null
                  else date_part('year', age(p_today, v_prof.birth_date))::integer end;
    v_blockers := public.coach_unconfirmed_keys(v_est);

    select (b.measured_at at time zone 'UTC')::date, b.weight_kg into v_weigh_at, v_weigh_kg
    from public.body_measurements b
    where b.user_id = v_uid
    order by b.measured_at desc limit 1;

    v_entries := jsonb_build_array(
      v_intention,
      public.coach_input_entry('sex', to_jsonb(v_prof.sex), 'profile',
        case when v_prof.sex is null then 'missing' else 'ok' end, v_prof.sex is null, 'personal_data'),
      public.coach_input_entry('age', to_jsonb(v_age), 'profile',
        case when v_age is null then 'missing' else 'ok' end, v_age is null and not v_has_tdee, 'personal_data'),
      public.coach_input_entry('height', to_jsonb(v_prof.height_cm), 'profile',
        case when v_prof.height_cm is null then 'missing' else 'ok' end,
        v_prof.height_cm is null and not v_has_tdee, 'personal_data'),
      public.coach_input_entry('weight', to_jsonb(v_weigh_kg), 'weigh_in',
        case when v_weigh_at is null then 'missing' when p_today - v_weigh_at > 14 then 'stale' else 'ok' end,
        v_weigh_at is null, 'weight', v_weigh_at),
      -- T-308: a measured level has no editor (no fix target) and says what it is measured from.
      (public.coach_input_entry('activity_level', v_act->'value', 'coach_estimate',
            case when (v_act->>'provisional')::boolean then 'provisional' else 'ok' end, false,
            case when v_measured then null else 'activity_sheet' end)
          || jsonb_build_object('basis',
               case when not v_measured then null
                    when v_act->'sources'->>'steps_avg' is not null then 'steps'
                    else 'active_kcal' end)),
      public.coach_input_entry('total_expenditure', v_tdee->'value', 'coach_estimate',
        case when not v_has_tdee then 'missing'
             when (v_tdee->>'provisional')::boolean then 'provisional' else 'ok' end, false, null),
      public.coach_input_entry('weight_trend', v_trend->'slope_kg_week', 'coach_estimate',
        case when v_trend->>'slope_kg_week' is null or (v_trend->>'provisional')::boolean then 'provisional'
             else 'ok' end, false, null),
      public.coach_input_entry('diary', v_est->'intake'->'ratio', 'coach_estimate',
        case when (v_est->'intake'->>'reliable')::boolean then 'ok' else 'provisional' end, false, null),
      public.coach_input_entry('experience', v_est->'experience'->'value', 'coach_estimate',
        case when (v_est->'experience'->>'provisional')::boolean then 'provisional' else 'ok' end, false,
        case when v_est->'experience'->>'source' = 'intake' then 'intake' end),
      public.coach_input_entry('training_years', to_jsonb(v_intake.training_years), 'intake',
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('known_maintenance', to_jsonb(v_intake.known_maintenance_kcal), 'intake',
        case when v_intake.known_maintenance_kcal is null then 'unset_optional' else 'ok' end, false, 'intake'));

    select jsonb_agg(x || jsonb_build_object('blocks_accept',
                                             (x->>'key') = any (v_blockers) and x->>'status' = 'provisional')
                     order by o)
    into v_entries
    from jsonb_array_elements(v_entries) with ordinality t(x, o);
  else
    v_entries := jsonb_build_array(
      v_intention,
      public.coach_input_entry('place', to_jsonb(v_tp.place), 'my_training',
        case when v_tp.place is null then 'missing' else 'ok' end, v_tp.place is null, 'my_training'),
      public.coach_input_entry('equipment', to_jsonb(v_lifting), 'my_training',
        case when coalesce(cardinality(v_lifting), 0) = 0 then 'unset_optional' else 'ok' end,
        false, 'my_training'),
      public.coach_input_entry('cardio', to_jsonb(v_cardio), 'my_training', 'ok', false, 'my_training'),
      public.coach_input_entry('days_per_week', to_jsonb(v_tp.days_per_week), 'my_training',
        case when v_tp.days_per_week is null then 'unset_optional' else 'ok' end, false, 'my_training'),
      public.coach_input_entry('session_minutes', to_jsonb(v_tp.session_minutes), 'my_training',
        case when v_tp.session_minutes is null then 'unset_optional' else 'ok' end, false, 'my_training'),
      public.coach_input_entry('experience', v_est->'experience'->'value', 'coach_estimate',
        case when (v_est->'experience'->>'provisional')::boolean then 'provisional' else 'ok' end, false, null),
      public.coach_input_entry('training_years', to_jsonb(v_intake.training_years), 'intake',
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'));
  end if;

  return jsonb_build_object('kind', p_kind, 'as_of', p_today)
         || case when v_current is not null then jsonb_build_object('current_plan', v_current) else '{}'::jsonb end
         || jsonb_build_object('inputs', v_entries);
end;
$function$;

-- confirm_derived_values: an edited level is ignored while the level is measured (no error).
create or replace function public.confirm_derived_values(p_today date, p_activity_level public.activity_level default null)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_raw   jsonb := public.coach_compute(p_today, p_activity_level);
  v_est   public.activity_level := (v_raw->'activity_level'->>'estimate')::public.activity_level;
  v_level public.activity_level := case when (v_raw->'activity_level'->>'measured')::boolean then null
                                        else p_activity_level end;
begin
  insert into public.coach_confirmed_values as c (user_id, key, estimate, uncertainty, level_value, edited, confirmed_at)
  values (v_uid, 'activity_level', public.coach_activity_factor(v_est),
          (v_raw->'activity_level'->>'uncertainty')::numeric,
          coalesce(v_level, v_est), v_level is not null, now())
  on conflict (user_id, key) do update
    set estimate = excluded.estimate, uncertainty = excluded.uncertainty, level_value = excluded.level_value,
        edited = excluded.edited, confirmed_at = excluded.confirmed_at;

  if v_raw->'total_expenditure'->>'value' is not null then
    insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty)
    values (v_uid, 'total_expenditure', (v_raw->'total_expenditure'->>'value')::numeric,
            (v_raw->'total_expenditure'->>'uncertainty')::numeric)
    on conflict (user_id, key) do update
      set estimate = excluded.estimate, uncertainty = excluded.uncertainty, confirmed_at = now();
  end if;

  if v_raw->'weight_trend'->>'slope_kg_week' is not null then
    insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty)
    values (v_uid, 'weight_trend', (v_raw->'weight_trend'->>'slope_kg_week')::numeric,
            (v_raw->'weight_trend'->>'uncertainty')::numeric)
    on conflict (user_id, key) do update
      set estimate = excluded.estimate, uncertainty = excluded.uncertainty, confirmed_at = now();
  end if;

  insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty)
  values (v_uid, 'experience',
          array_position(array['beginner', 'intermediate', 'advanced'], v_raw->'experience'->>'value') - 1,
          0.5)
  on conflict (user_id, key) do update
    set estimate = excluded.estimate, uncertainty = excluded.uncertainty, confirmed_at = now();

  return public.coach_compute(p_today, null);
end;
$$;
