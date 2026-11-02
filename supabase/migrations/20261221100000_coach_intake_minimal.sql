-- T-198 (US-81, US-78, US-89; R-53, R-55): the intake is reduced to training_years, known_maintenance_kcal and
-- free_text. The situation, injured area, pre-break bests and cutting calories (and the two enums) are dropped, and
-- save_coach_intake becomes a partial update (only the keys present are written), which also fixes the lost update of
-- the old "overwrite everything" upsert. Readers (coach_compute, coach_plan_inputs) are re-created without them.

alter table public.coach_user_intake
  drop constraint coach_user_intake_bests_array,
  drop constraint coach_user_intake_cut_range,
  drop column situation,
  drop column injured_area,
  drop column pre_break_bests,
  drop column known_cut_kcal;

drop type public.coach_situation;
drop type public.coach_injured_area;

-- Same name and signature as before so an installed client keeps working: unknown keys are ignored, and an old
-- client that sends every key overwrites the three kept ones as it always did.
create or replace function public.save_coach_intake(p jsonb)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid       uuid := auth.uid();
  v_years     numeric;
  v_maint     integer;
  v_text      text;
  v_old       public.coach_user_intake;
  v_found     boolean;
  v_handover  timestamptz;
  v_adaptive  boolean := false;
  v_row       public.coach_user_intake;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p is null or jsonb_typeof(p) <> 'object' then
    raise exception 'intake must be a JSON object' using errcode = '22023';
  end if;

  if jsonb_typeof(p->'training_years') = 'number' then
    v_years := (p->>'training_years')::numeric;
    if v_years < 0 or v_years > 60 then
      raise exception 'training_years out of range' using errcode = '22023';
    end if;
  elsif p ? 'training_years' and jsonb_typeof(p->'training_years') <> 'null' then
    raise exception 'training_years must be a number' using errcode = '22023';
  end if;

  if p ? 'known_maintenance_kcal' and jsonb_typeof(p->'known_maintenance_kcal') <> 'null' then
    if jsonb_typeof(p->'known_maintenance_kcal') <> 'number'
       or (p->>'known_maintenance_kcal')::numeric <> trunc((p->>'known_maintenance_kcal')::numeric)
       or (p->>'known_maintenance_kcal')::numeric not between 1200 and 6000 then
      raise exception 'invalid known_maintenance_kcal' using errcode = '22023';
    end if;
    v_maint := (p->>'known_maintenance_kcal')::numeric::integer;
  end if;

  if p ? 'free_text' and jsonb_typeof(p->'free_text') <> 'null' then
    if jsonb_typeof(p->'free_text') <> 'string' then
      raise exception 'free_text must be a string' using errcode = '22023';
    end if;
    v_text := nullif(btrim(p->>'free_text'), '');
    if char_length(v_text) > 500 then
      raise exception 'free_text longer than 500 characters' using errcode = '22023';
    end if;
  end if;

  select * into v_old from public.coach_user_intake where user_id = v_uid;
  v_found := found;

  -- Handover: only when this call changes the maintenance to a new value.
  v_handover := v_old.maintenance_handover_at;
  if p ? 'known_maintenance_kcal' then
    if v_maint is null then
      v_handover := null;
    elsif not (v_found and v_old.known_maintenance_kcal is not distinct from v_maint) then
      v_adaptive := public.coach_compute((now() at time zone 'utc')::date, null)
                      ->'total_expenditure'->>'source' = 'adaptive';
      v_handover := case when v_adaptive then now() end;
    end if;
  end if;

  insert into public.coach_user_intake as c (
    user_id, training_years, known_maintenance_kcal, free_text, maintenance_handover_at, saved_at)
  values (
    v_uid,
    case when p ? 'training_years' then v_years end,
    case when p ? 'known_maintenance_kcal' then v_maint end,
    case when p ? 'free_text' then v_text end,
    v_handover, now())
  on conflict (user_id) do update
    set training_years = case when p ? 'training_years' then v_years else c.training_years end,
        known_maintenance_kcal = case when p ? 'known_maintenance_kcal' then v_maint else c.known_maintenance_kcal end,
        free_text = case when p ? 'free_text' then v_text else c.free_text end,
        maintenance_handover_at = v_handover,
        saved_at = now()
  returning * into v_row;

  return jsonb_build_object(
    'training_years', v_row.training_years,
    'known_maintenance_kcal', v_row.known_maintenance_kcal,
    'free_text', v_row.free_text,
    'maintenance_handover_at', v_row.maintenance_handover_at,
    'saved_at', v_row.saved_at);
end;
$$;

revoke execute on function public.save_coach_intake(jsonb) from public, anon;
grant execute on function public.save_coach_intake(jsonb) to authenticated;

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
    'profile_intake', jsonb_build_object('saved', v_in_found));
end;
$$;

-- coach_plan_inputs: the single "intake" entry is replaced by one entry per kept input (never blocking); block mode
-- lists "training_years" and "free_text". Rest unchanged from 20261219100400.

create or replace function public.coach_plan_inputs(p_kind text, p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
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
  select coalesce(array_agg(e order by o) filter (where e in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')), '{}'),
         coalesce(array_agg(e order by o) filter (where e not in ('cardio_bike', 'treadmill', 'elliptical', 'rower', 'cardio_other')), '{}')
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
    v_has_tdee := v_tdee->>'value' is not null;
    v_age := case when v_prof.birth_date is null then null
                  else date_part('year', age(p_today, v_prof.birth_date))::integer end;

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
      public.coach_input_entry('activity_level', v_act->'value', 'coach_estimate',
        case when (v_act->>'provisional')::boolean then 'provisional' else 'ok' end, false, 'activity_sheet'),
      public.coach_input_entry('total_expenditure', v_tdee->'value', 'coach_estimate',
        case when not v_has_tdee then 'missing'
             when (v_tdee->>'provisional')::boolean then 'provisional' else 'ok' end, false, null),
      public.coach_input_entry('weight_trend', v_trend->'slope_kg_week', 'coach_estimate',
        case when v_trend->>'slope_kg_week' is null or (v_trend->>'provisional')::boolean then 'provisional'
             else 'ok' end, false, null),
      public.coach_input_entry('diary', v_est->'intake'->'ratio', 'coach_estimate',
        case when (v_est->'intake'->>'reliable')::boolean then 'ok' else 'provisional' end, false, null),
      public.coach_input_entry('training_years', to_jsonb(v_intake.training_years), 'intake',
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('known_maintenance', to_jsonb(v_intake.known_maintenance_kcal), 'intake',
        case when v_intake.known_maintenance_kcal is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('free_text', to_jsonb(v_intake.free_text), 'intake',
        case when v_intake.free_text is null then 'unset_optional' else 'ok' end, false, 'intake'));
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
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('free_text', to_jsonb(v_intake.free_text), 'intake',
        case when v_intake.free_text is null then 'unset_optional' else 'ok' end, false, 'intake'));
  end if;

  return jsonb_build_object('kind', p_kind, 'as_of', p_today)
         || case when v_current is not null then jsonb_build_object('current_plan', v_current) else '{}'::jsonb end
         || jsonb_build_object('inputs', v_entries);
end;
$$;

revoke execute on function public.coach_plan_inputs(text, date) from public, anon;
grant execute on function public.coach_plan_inputs(text, date) to authenticated;
