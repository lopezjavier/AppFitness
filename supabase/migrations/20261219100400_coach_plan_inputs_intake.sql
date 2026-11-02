-- T-185 (US-81): coach_plan_inputs('plan') gains an optional, never blocking "intake" entry ({saved, situation} or
-- null; ok when the user filled "Para conocerte", unset_optional otherwise). Rest unchanged from 20261216100200.

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
      public.coach_input_entry('intake',
        case when v_intake.user_id is not null
             then jsonb_build_object('saved', true, 'situation', v_est->'situation'->'value') end,
        'intake', case when v_intake.user_id is not null then 'ok' else 'unset_optional' end, false, 'intake'));
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
        case when (v_est->'experience'->>'provisional')::boolean then 'provisional' else 'ok' end, false, null));
  end if;

  return jsonb_build_object('kind', p_kind, 'as_of', p_today)
         || case when v_current is not null then jsonb_build_object('current_plan', v_current) else '{}'::jsonb end
         || jsonb_build_object('inputs', v_entries);
end;
$$;

revoke execute on function public.coach_input_entry(text, jsonb, text, text, boolean, text, date) from public, anon;
revoke execute on function public.coach_plan_inputs(text, date) from public, anon;
grant execute on function public.coach_input_entry(text, jsonb, text, text, boolean, text, date) to authenticated;
grant execute on function public.coach_plan_inputs(text, date) to authenticated;
