-- T-161 (E13, US-69/US-74): nutrition plans proposed by the coach, the decisions about them and in-block calorie changes.
-- Rules: planning/04-business-rules.md section 8, planning/07-coach-design.md sections 4 and 9.
-- Both tables are read-only for the owner; every write goes through the security definer RPCs below, each one
-- filtering every statement by auth.uid().

create table public.coach_plans (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  block_start  date not null,
  block_weeks  smallint not null default 4
    constraint coach_plans_block_weeks_range check (block_weeks between 1 and 12),
  intention    public.training_intention not null,
  -- Deterministic calories and macros (data_version 1; shape in planning/03-data-model.md).
  nutrition    jsonb not null constraint coach_plans_nutrition_object check (jsonb_typeof(nutrition) = 'object'),
  outcomes     jsonb not null default '[]'::jsonb constraint coach_plans_outcomes_array check (jsonb_typeof(outcomes) = 'array'),
  -- Model id, attempts, cost, constants version.
  method       jsonb not null default '{}'::jsonb constraint coach_plans_method_object check (jsonb_typeof(method) = 'object'),
  status       text not null default 'proposed'
    constraint coach_plans_status_values check (status in ('proposed', 'accepted', 'superseded', 'discarded')),
  data_version smallint not null default 1,
  created_at   timestamptz not null default now(),
  accepted_at  timestamptz,
  updated_at   timestamptz not null default now()
);

create trigger coach_plans_set_updated_at
  before update on public.coach_plans
  for each row execute function public.set_updated_at();

create unique index coach_plans_one_accepted on public.coach_plans (user_id) where status = 'accepted';
create unique index coach_plans_one_proposed on public.coach_plans (user_id) where status = 'proposed';
create index coach_plans_user_status_idx on public.coach_plans (user_id, status, created_at desc);

create table public.coach_recommendations (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  plan_id       uuid references public.coach_plans (id) on delete cascade,
  -- text + check (not an enum) so later tasks extend the set by swapping the constraint.
  kind          text not null constraint coach_recommendations_kind_values check (kind in ('plan', 'calorie_change')),
  payload       jsonb not null constraint coach_recommendations_payload_object check (jsonb_typeof(payload) = 'object'),
  -- A code such as rate_outside_interval; the Spanish text lives in the UI.
  reason        text,
  -- The coach_summary the recommendation was computed from.
  input_summary jsonb not null,
  status        text not null default 'pending'
    constraint coach_recommendations_status_values check (status in ('pending', 'accepted', 'edited', 'discarded')),
  created_at    timestamptz not null default now(),
  decided_at    timestamptz,
  constraint coach_recommendations_decided_iff_not_pending check ((decided_at is null) = (status = 'pending'))
);

create index coach_recommendations_user_created_idx on public.coach_recommendations (user_id, created_at desc);
create index coach_recommendations_plan_idx on public.coach_recommendations (plan_id);

alter table public.coach_plans enable row level security;
alter table public.coach_plans force row level security;
alter table public.coach_recommendations enable row level security;
alter table public.coach_recommendations force row level security;

create policy coach_plans_select_own on public.coach_plans
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy coach_recommendations_select_own on public.coach_recommendations
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_plans from anon, authenticated;
revoke all on table public.coach_recommendations from anon, authenticated;
grant select on table public.coach_plans to authenticated;
grant select on table public.coach_recommendations to authenticated;

-- ---------------------------------------------------------------------------
-- Q8: a manual edit of the nutrition goal (Preferencias) marks the accepted plan as edited, so the coach stops
-- proposing calorie changes on top of the user's own numbers. Writes made by the coach RPCs set app.coach_write.
-- ---------------------------------------------------------------------------
create function public.coach_flag_manual_goal_edit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if coalesce(current_setting('app.coach_write', true), '') = 'on' then
    return new;
  end if;
  if tg_op = 'INSERT'
     or new.target_kcal is distinct from old.target_kcal
     or new.strategy is distinct from old.strategy
     or new.calorie_adjustment is distinct from old.calorie_adjustment
     or new.protein_pct is distinct from old.protein_pct
     or new.carbs_pct is distinct from old.carbs_pct
     or new.fat_pct is distinct from old.fat_pct then
    update public.coach_plans
      set nutrition = nutrition || '{"edited_by_user": true}'::jsonb
      where user_id = new.user_id and status = 'accepted';
  end if;
  return new;
end;
$$;

create trigger nutrition_goals_flag_manual_edit
  after insert or update on public.nutrition_goals
  for each row execute function public.coach_flag_manual_goal_edit();

revoke execute on function public.coach_flag_manual_goal_edit() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- coach_assert_plan: the server-side invariants of a plan (04 section 8), mirroring the coach function's
-- validator so that no caller can store or accept a plan that breaks them. Raises 22023.
-- ---------------------------------------------------------------------------
create function public.coach_assert_plan(
  p_uid uuid,
  p_intention public.training_intention,
  p_block_start date,
  p_nutrition jsonb,
  p_outcomes jsonb
)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_sex      public.sex;
  v_floor    integer;
  v_target   integer;
  v_exp      integer;
  v_adj      integer;
  v_strategy text;
  v_limited  text;
  v_rate     numeric;
  v_w        numeric;
  v_gkg      numeric;
  v_prot_g   numeric;
  v_prot     integer;
  v_carbs    integer;
  v_fat      integer;
  v_min      numeric;
  v_max      numeric;
  v_item     jsonb;
  v_reason   text;
  v_deadline date;
begin
  select sex into v_sex from public.profiles where id = p_uid;
  if v_sex is null then
    raise exception 'plan invariant: sex_missing' using errcode = '22023';
  end if;
  v_floor := case v_sex when 'female' then 1200 else 1500 end;

  begin
    v_target := (p_nutrition->>'target_kcal')::numeric::integer;
    v_exp := (p_nutrition->>'expenditure_kcal')::numeric::integer;
    v_adj := (p_nutrition->>'calorie_adjustment')::numeric::integer;
    v_strategy := p_nutrition->>'strategy';
    v_limited := p_nutrition->>'limited_by';
    v_rate := (p_nutrition->>'rate_pct_week')::numeric;
    v_w := (p_nutrition->>'weight_kg')::numeric;
    v_gkg := (p_nutrition->>'protein_g_per_kg')::numeric;
    v_prot_g := (p_nutrition->>'protein_g')::numeric;
    v_prot := (p_nutrition->>'protein_pct')::numeric::integer;
    v_carbs := (p_nutrition->>'carbs_pct')::numeric::integer;
    v_fat := (p_nutrition->>'fat_pct')::numeric::integer;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception 'plan invariant: malformed_nutrition' using errcode = '22023';
  end;

  if v_target is null or v_exp is null or v_adj is null or v_strategy is null or v_rate is null
     or v_w is null or v_w <= 0 or v_gkg is null or v_prot_g is null
     or v_prot is null or v_carbs is null or v_fat is null then
    raise exception 'plan invariant: missing_field' using errcode = '22023';
  end if;

  -- Calories: the floor of the user's sex, the adjustment range and a consistent strategy.
  if (p_nutrition->>'floor_kcal')::numeric is distinct from v_floor::numeric or v_target < v_floor then
    raise exception 'plan invariant: target_below_floor' using errcode = '22023';
  end if;
  if v_limited = 'floor' and v_target <> v_floor then
    raise exception 'plan invariant: floor_flag_without_floor_target' using errcode = '22023';
  end if;
  if v_adj <> v_target - v_exp or v_adj < -1000 or v_adj > 750 then
    raise exception 'plan invariant: adjustment' using errcode = '22023';
  end if;
  if v_strategy is distinct from (case when v_adj < 0 then 'deficit' when v_adj > 0 then 'surplus' else 'maintenance' end) then
    raise exception 'plan invariant: strategy_mismatch' using errcode = '22023';
  end if;

  -- Rate of change by intention (a slower rate needs a limiter flag; the maximum carries the rounding tolerance).
  if p_intention in ('fat_loss', 'muscle') then
    if p_intention = 'fat_loss' then
      if v_target >= v_exp then
        raise exception 'plan invariant: loss_needs_deficit' using errcode = '22023';
      end if;
      v_min := 0.5; v_max := 1.0;
    else
      if v_target <= v_exp then
        raise exception 'plan invariant: gain_needs_surplus' using errcode = '22023';
      end if;
      v_min := 0.25; v_max := 0.5;
    end if;
    if v_rate > v_max + 0.05 or (v_rate < v_min and v_limited is null) then
      raise exception 'plan invariant: rate_out_of_range' using errcode = '22023';
    end if;
  elsif v_adj < 0 or (v_adj > 0 and v_limited is distinct from 'floor') then
    raise exception 'plan invariant: adjustment_for_intention' using errcode = '22023';
  end if;

  -- Macros: protein 1.6..2.2 g/kg, fat 20..35 %, shares summing to 100.
  if v_gkg < 1.6 or v_gkg > 2.2 then
    raise exception 'plan invariant: protein_g_per_kg_range' using errcode = '22023';
  end if;
  if v_prot_g < 1.6 * v_w - 0.5 then
    raise exception 'plan invariant: protein_below_min' using errcode = '22023';
  end if;
  if v_prot_g > 2.2 * v_w + v_target / 400.0 + 1 then
    raise exception 'plan invariant: protein_above_max' using errcode = '22023';
  end if;
  if v_prot not between 5 and 90 or v_carbs not between 5 and 90 or v_fat not between 5 and 90
     or v_prot + v_carbs + v_fat <> 100 then
    raise exception 'plan invariant: pct' using errcode = '22023';
  end if;
  if v_fat < 20 or v_fat > 35 then
    raise exception 'plan invariant: fat_range' using errcode = '22023';
  end if;

  -- Outcomes: at most 4, each with a reason of 10..300 characters and a deadline 7..28 days into the block.
  if jsonb_typeof(p_outcomes) <> 'array' or jsonb_array_length(p_outcomes) > 4 then
    raise exception 'plan invariant: outcomes' using errcode = '22023';
  end if;
  for v_item in select value from jsonb_array_elements(p_outcomes) loop
    if jsonb_typeof(v_item) <> 'object' or jsonb_typeof(v_item->'reason') is distinct from 'string'
       or jsonb_typeof(v_item->'deadline') is distinct from 'string' then
      raise exception 'plan invariant: outcome_shape' using errcode = '22023';
    end if;
    v_reason := btrim(v_item->>'reason');
    if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
      raise exception 'plan invariant: outcome_reason' using errcode = '22023';
    end if;
    begin
      v_deadline := (v_item->>'deadline')::date;
    exception when invalid_datetime_format or datetime_field_overflow or invalid_text_representation then
      raise exception 'plan invariant: outcome_deadline' using errcode = '22023';
    end;
    if (v_item->>'deadline') !~ '^\d{4}-\d{2}-\d{2}$'
       or v_deadline < p_block_start + 7 or v_deadline > p_block_start + 28 then
      raise exception 'plan invariant: outcome_deadline' using errcode = '22023';
    end if;
  end loop;
end;
$$;

revoke execute on function public.coach_assert_plan(uuid, public.training_intention, date, jsonb, jsonb)
  from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- propose_coach_plan: stores a new proposed plan (called by the coach function with the caller's token).
-- ---------------------------------------------------------------------------
create function public.propose_coach_plan(
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
  if p_block_start is null or p_intention is null
     or p_nutrition is null or jsonb_typeof(p_nutrition) <> 'object'
     or p_outcomes is null or jsonb_typeof(p_outcomes) <> 'array'
     or p_method is null or jsonb_typeof(p_method) <> 'object'
     or p_input_summary is null then
    raise exception 'invalid plan' using errcode = '22023';
  end if;
  perform public.coach_assert_plan(v_uid, p_intention, p_block_start, p_nutrition, p_outcomes);

  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where user_id = v_uid and kind = 'plan' and status = 'pending'
      and plan_id in (select id from public.coach_plans where user_id = v_uid and status = 'proposed');
  update public.coach_plans set status = 'superseded' where user_id = v_uid and status = 'proposed';

  insert into public.coach_plans (user_id, block_start, intention, nutrition, outcomes, method)
  values (v_uid, p_block_start, p_intention, p_nutrition, p_outcomes, p_method)
  returning id into v_plan;

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
  values (v_uid, v_plan, 'plan', jsonb_build_object('nutrition', p_nutrition, 'outcomes', p_outcomes), p_input_summary)
  returning id into v_rec;

  return jsonb_build_object('plan_id', v_plan, 'recommendation_id', v_rec);
end;
$$;

-- ---------------------------------------------------------------------------
-- accept_coach_plan: nutrition part only (the training part is added by later tasks).
-- ---------------------------------------------------------------------------
create function public.accept_coach_plan(p_plan_id uuid, p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_est   jsonb;
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
  v_est := public.coach_compute(p_today, null);
  if coalesce((v_est->'activity_level'->>'provisional')::boolean, false)
     or (v_est->'total_expenditure'->>'value' is not null
         and coalesce((v_est->'total_expenditure'->>'provisional')::boolean, false))
     or coalesce((v_est->'experience'->>'provisional')::boolean, false)
     or (v_est->'weight_trend'->>'slope_kg_week' is not null
         and coalesce((v_est->'weight_trend'->>'provisional')::boolean, false)) then
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

create function public.discard_coach_plan(p_plan_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_status text;
begin
  select status into v_status from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_status <> 'proposed' then
    raise exception 'plan is not proposed' using errcode = '55000';
  end if;
  update public.coach_plans set status = 'discarded' where id = p_plan_id and user_id = v_uid;
  update public.coach_recommendations set status = 'discarded', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind = 'plan' and status = 'pending';
end;
$$;

-- ---------------------------------------------------------------------------
-- evaluate_coach_calorie_change: deterministic and idempotent (04 section 8.5).
-- ---------------------------------------------------------------------------
create function public.evaluate_coach_calorie_change(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_plan    public.coach_plans;
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
  v_rec     uuid;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found or coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'edited');
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

  if exists (select 1 from public.coach_recommendations
             where user_id = v_uid and kind = 'calorie_change'
               and (status = 'pending' or decided_at > now() - interval '14 days')) then
    return jsonb_build_object('status', 'none', 'reason', 'recent_change');
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
  if v_new < v_floor then
    return jsonb_build_object('status', 'none', 'reason', 'floor');
  end if;
  if v_adj + v_change < -1000 or v_adj + v_change > 750 then
    return jsonb_build_object('status', 'none', 'reason', 'range');
  end if;

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary)
  values (v_uid, v_plan.id, 'calorie_change',
          jsonb_build_object('from_kcal', v_target, 'to_kcal', v_new, 'change_kcal', v_change,
                             'change_pct', round(v_change * 100.0 / v_target, 1),
                             'slope_kg_week', v_slope, 'interval_low', v_low0, 'interval_high', v_high0,
                             'rate_kg_week', v_rate),
          'rate_outside_interval', public.coach_summary(p_today))
  returning id into v_rec;

  return jsonb_build_object('status', 'created', 'reason', 'rate_outside_interval', 'recommendation_id', v_rec);
end;
$$;

-- ---------------------------------------------------------------------------
-- decide_recommendation: accepts, edits or discards a pending calorie change.
-- ---------------------------------------------------------------------------
create function public.decide_recommendation(
  p_id uuid,
  p_status text,
  p_payload jsonb default null,
  p_today date default null
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(coalesce(p_today, (now() at time zone 'utc')::date));
  v_rec     public.coach_recommendations;
  v_plan    public.coach_plans;
  v_n       jsonb;
  v_from    integer;
  v_new     integer;
  v_change  integer;
  v_adj     integer;
  v_prot_g  numeric;
  v_prot    integer;
  v_fat     integer;
  v_carbs   integer;
  v_strat   public.goal_strategy;
  v_out     jsonb;
begin
  if p_status is null or p_status not in ('accepted', 'edited', 'discarded') then
    raise exception 'invalid status' using errcode = '22023';
  end if;
  select * into v_rec from public.coach_recommendations where id = p_id and user_id = v_uid for update;
  if not found then
    raise exception 'recommendation not found' using errcode = 'P0002';
  end if;
  if v_rec.kind <> 'calorie_change' then
    raise exception 'use accept_coach_plan or discard_coach_plan' using errcode = '22023';
  end if;
  if v_rec.status <> 'pending' then
    raise exception 'recommendation already decided' using errcode = '55000';
  end if;

  if p_status = 'discarded' then
    update public.coach_recommendations set status = 'discarded', decided_at = now() where id = p_id;
  else
    select * into v_plan from public.coach_plans where id = v_rec.plan_id and user_id = v_uid for update;
    if not found or v_plan.status <> 'accepted' then
      raise exception 'plan is not accepted' using errcode = '55000';
    end if;
    v_n := v_plan.nutrition;
    v_from := (v_rec.payload->>'from_kcal')::int;

    if p_status = 'accepted' then
      v_new := (v_rec.payload->>'to_kcal')::int;
    else
      if p_payload is null or jsonb_typeof(p_payload) <> 'object'
         or jsonb_typeof(p_payload->'target_kcal') is distinct from 'number' then
        raise exception 'target_kcal is required' using errcode = '22023';
      end if;
      v_new := (p_payload->>'target_kcal')::numeric::int;
      if (p_payload->>'target_kcal')::numeric <> v_new
         or abs(v_new - v_from) > floor(v_from * 0.10) then
        raise exception 'change beyond 10 percent' using errcode = '22023';
      end if;
    end if;

    v_change := v_new - (v_n->>'target_kcal')::int;
    v_adj := (v_n->>'calorie_adjustment')::int + v_change;
    if v_new < coalesce((v_n->>'floor_kcal')::int, 0) or v_adj < -1000 or v_adj > 750 then
      raise exception 'target out of range' using errcode = '22023';
    end if;

    -- Keep the protein grams and the fat share; carbs take the rest.
    v_prot_g := (v_n->>'protein_g')::numeric;
    v_prot := ceil(v_prot_g * 4 / v_new * 100)::int;
    v_fat := (v_n->>'fat_pct')::int;
    v_carbs := 100 - v_prot - v_fat;
    if v_prot > 90 or v_carbs < 5 then
      raise exception 'macros do not fit the new target' using errcode = '22023';
    end if;
    v_strat := (case when v_adj < 0 then 'deficit' when v_adj > 0 then 'surplus' else 'maintenance' end)::public.goal_strategy;

    perform set_config('app.coach_write', 'on', true);
    insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
    values (v_uid, v_strat, v_adj, v_new, v_prot, v_carbs, v_fat)
    on conflict (user_id) do update
      set strategy = excluded.strategy, calorie_adjustment = excluded.calorie_adjustment,
          target_kcal = excluded.target_kcal, protein_pct = excluded.protein_pct,
          carbs_pct = excluded.carbs_pct, fat_pct = excluded.fat_pct;
    perform set_config('app.coach_write', 'off', true);

    update public.coach_plans
      set nutrition = nutrition || jsonb_build_object(
        'target_kcal', v_new, 'calorie_adjustment', v_adj, 'strategy', v_strat,
        'protein_pct', v_prot, 'carbs_pct', v_carbs, 'fat_pct', v_fat,
        'carbs_g', round(v_new * v_carbs / 100.0 / 4), 'fat_g', round(v_new * v_fat / 100.0 / 9),
        'calorie_changes', coalesce((nutrition->>'calorie_changes')::int, 0) + 1)
      where id = v_plan.id and user_id = v_uid;

    update public.coach_recommendations
      set status = p_status, decided_at = now(),
          payload = case when p_status = 'edited' then payload || jsonb_build_object('edited', p_payload) else payload end
      where id = p_id;
  end if;

  select to_jsonb(r) into v_out from public.coach_recommendations r where r.id = p_id;
  return v_out;
end;
$$;

revoke execute on function public.propose_coach_plan(date, public.training_intention, jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke execute on function public.accept_coach_plan(uuid, date) from public, anon;
revoke execute on function public.discard_coach_plan(uuid) from public, anon;
revoke execute on function public.evaluate_coach_calorie_change(date) from public, anon;
revoke execute on function public.decide_recommendation(uuid, text, jsonb, date) from public, anon;
grant execute on function public.propose_coach_plan(date, public.training_intention, jsonb, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.accept_coach_plan(uuid, date) to authenticated;
grant execute on function public.discard_coach_plan(uuid) to authenticated;
grant execute on function public.evaluate_coach_calorie_change(date) to authenticated;
grant execute on function public.decide_recommendation(uuid, text, jsonb, date) to authenticated;
