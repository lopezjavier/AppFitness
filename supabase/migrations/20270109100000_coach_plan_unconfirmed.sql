-- T-245 (US-68, R-59): one shared definition of "the values that block accepting a plan". accept_coach_plan checks it and
-- coach_plan_inputs('plan') reports it per entry (`blocks_accept`), so the proposal screen never blocks on something the
-- server would accept (the diary and a trend without a slope) nor hides what the server blocks on (experience).
-- Q1: an expenditure that comes from the user's own stated maintenance (source = 'intake') does not block: nothing the
-- user could do would clear it, and it is their own number.

-- Pure: the keys that block, from a coach_compute result. No data access, so authenticated may call it
-- (coach_plan_inputs is security invoker).
create function public.coach_unconfirmed_keys(p_est jsonb)
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

-- The keys that block accepting a plan for the current user (internal: the definer functions call it).
create function public.coach_plan_unconfirmed(p_today date)
returns text[]
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  perform public.coach_guard(p_today);
  return public.coach_unconfirmed_keys(public.coach_compute(p_today, null));
end;
$$;

revoke execute on function public.coach_unconfirmed_keys(jsonb) from public, anon;
grant execute on function public.coach_unconfirmed_keys(jsonb) to authenticated;
revoke execute on function public.coach_plan_unconfirmed(date) from public, anon, authenticated;

-- accept_coach_plan: same body as 20261208100000, only the blocking check uses the shared set. Grants are kept.
create or replace function public.accept_coach_plan(p_plan_id uuid, p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_n     jsonb;
  v_out   jsonb;
begin
  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_plan.status <> 'proposed' then
    raise exception 'plan is not proposed' using errcode = '55000';
  end if;

  -- Re-check the stored plan: nothing that breaks the invariants reaches nutrition_goals.
  perform public.coach_assert_plan(v_uid, v_plan.intention, v_plan.block_start, v_plan.nutrition, v_plan.outcomes);

  -- Unconfirmed derived values block the acceptance (US-68).
  if cardinality(public.coach_plan_unconfirmed(p_today)) > 0 then
    raise exception 'derived values unconfirmed' using errcode = '55000';
  end if;

  update public.coach_plans set status = 'superseded'
    where user_id = v_uid and status = 'accepted' and id <> p_plan_id;
  update public.coach_plans set status = 'accepted', accepted_at = now()
    where id = p_plan_id and user_id = v_uid
    returning * into v_plan;

  v_n := v_plan.nutrition;
  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (v_uid, (v_n->>'strategy')::public.goal_strategy, (v_n->>'calorie_adjustment')::int,
          (v_n->>'target_kcal')::int, (v_n->>'protein_pct')::smallint, (v_n->>'carbs_pct')::smallint,
          (v_n->>'fat_pct')::smallint)
  on conflict (user_id) do update
    set strategy = excluded.strategy, calorie_adjustment = excluded.calorie_adjustment,
        target_kcal = excluded.target_kcal, protein_pct = excluded.protein_pct,
        carbs_pct = excluded.carbs_pct, fat_pct = excluded.fat_pct;
  perform set_config('app.coach_write', 'off', true);

  update public.coach_recommendations set status = 'accepted', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind = 'plan' and status = 'pending';

  select to_jsonb(p) into v_out from public.coach_plans p where p.id = p_plan_id;
  return v_out;
end;
$$;

-- coach_plan_inputs: body copied from 20261223100300. Changes: the plan list gains `experience` (after `diary`) and
-- every plan entry carries `blocks_accept` (the key is in coach_unconfirmed_keys and its status is provisional).
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
  v_blockers  text[];
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
      public.coach_input_entry('experience', v_est->'experience'->'value', 'coach_estimate',
        case when (v_est->'experience'->>'provisional')::boolean then 'provisional' else 'ok' end, false,
        case when v_est->'experience'->>'source' = 'intake' then 'intake' end),
      public.coach_input_entry('training_years', to_jsonb(v_intake.training_years), 'intake',
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('known_maintenance', to_jsonb(v_intake.known_maintenance_kcal), 'intake',
        case when v_intake.known_maintenance_kcal is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('free_text', to_jsonb(v_intake.free_text), 'intake',
        case when v_intake.free_text is null then 'unset_optional' else 'ok' end, false, 'intake'));

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
