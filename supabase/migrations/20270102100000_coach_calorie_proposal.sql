-- T-229 (US-99): calorie proposals the user approves ("Aplicar" / "Ahora no"), planning/04 section 8.5 and 07 section 4.
-- Removes the "edited" decision (typed kcal), adds the weekly data gate, the safe band and one pending proposal at a time.

-- ---------------------------------------------------------------------------
-- Status values: edited -> accepted (the typed value stays only in the history it already wrote), superseded added.
-- ---------------------------------------------------------------------------
update public.coach_recommendations
set status = 'accepted', payload = payload - 'edited'
where status = 'edited';

alter table public.coach_recommendations drop constraint coach_recommendations_status_values;
alter table public.coach_recommendations add constraint coach_recommendations_status_values
  check (status in ('pending', 'accepted', 'discarded', 'superseded'));

-- One proposal at a time, at database level. Defensive: older duplicates (none expected) are superseded first.
update public.coach_recommendations r
set status = 'superseded', decided_at = now()
where r.kind = 'calorie_change' and r.status = 'pending'
  and exists (select 1 from public.coach_recommendations n
              where n.user_id = r.user_id and n.kind = 'calorie_change' and n.status = 'pending'
                and (n.created_at, n.id) > (r.created_at, r.id));

create unique index coach_recommendations_one_pending_calorie
  on public.coach_recommendations (user_id) where kind = 'calorie_change' and status = 'pending';

-- ---------------------------------------------------------------------------
-- coach_week_gate: data quality of the 7 days p_end-6..p_end (04 section 8.5). A day is complete when its diary sum is
-- at least 40 % of the median daily sum of the 28 days ending p_end (the 7.3 partial-day rule).
-- ---------------------------------------------------------------------------
create function public.coach_week_gate(p_end date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with d as (
    select log_date, sum(kcal) as kcal
    from public.food_log_entries
    where user_id = (select auth.uid()) and log_date between p_end - 27 and p_end
    group by log_date
  ), m as (
    select (percentile_cont(0.5) within group (order by kcal::double precision))::numeric as med from d
  ), c as (
    select count(*) filter (where d.log_date >= p_end - 6 and d.kcal >= m.med * 0.4)::int as n
    from d cross join m
  ), w as (
    select count(distinct (measured_at at time zone 'UTC')::date)::int as n
    from public.body_measurements
    where user_id = (select auth.uid())
      and (measured_at at time zone 'UTC')::date between p_end - 6 and p_end
  )
  select jsonb_build_object('complete_days', c.n, 'weigh_in_days', w.n, 'ok', c.n >= 5 and w.n >= 3)
  from c cross join w;
$$;

-- ---------------------------------------------------------------------------
-- evaluate_coach_calorie_change: a pending proposal only after two weeks outside the interval on clean data and inside
-- the safe band. Deterministic and idempotent within a week.
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

  -- Two "Ahora no" in the block: nothing more until the review.
  if (select count(*) from public.coach_recommendations
      where user_id = v_uid and kind = 'calorie_change' and plan_id = v_plan.id and status = 'discarded'
        and created_at >= v_plan.block_start::timestamp at time zone 'UTC') >= 2 then
    return jsonb_build_object('status', 'none', 'reason', 'declined_twice');
  end if;

  if exists (select 1 from public.coach_recommendations
             where user_id = v_uid and kind = 'calorie_change' and status in ('accepted', 'discarded')
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

-- ---------------------------------------------------------------------------
-- decide_calorie_proposal: "Aplicar" (p_apply = true) or "Ahora no" (false). Replaces decide_recommendation.
-- ---------------------------------------------------------------------------
drop function public.decide_recommendation(uuid, text, jsonb, date);

create function public.decide_calorie_proposal(
  p_id uuid,
  p_apply boolean,
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
  if p_apply is null then
    raise exception 'p_apply is required' using errcode = '22023';
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

  if not p_apply then
    update public.coach_recommendations set status = 'discarded', decided_at = now() where id = p_id;
  else
    select * into v_plan from public.coach_plans where id = v_rec.plan_id and user_id = v_uid for update;
    if not found or v_plan.status <> 'accepted' then
      raise exception 'plan is not accepted' using errcode = '55000';
    end if;
    v_n := v_plan.nutrition;
    v_new := (v_rec.payload->>'to_kcal')::int;

    v_change := v_new - (v_n->>'target_kcal')::int;
    v_adj := (v_n->>'calorie_adjustment')::int + v_change;
    if v_new < coalesce((v_n->>'floor_kcal')::int, 0) or v_adj < -500 or v_adj > 750 then
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

    update public.coach_recommendations set status = 'accepted', decided_at = now() where id = p_id;
  end if;

  select to_jsonb(r) into v_out from public.coach_recommendations r where r.id = p_id;
  return v_out;
end;
$$;

revoke execute on function public.coach_week_gate(date) from public, anon;
revoke execute on function public.evaluate_coach_calorie_change(date) from public, anon;
revoke execute on function public.decide_calorie_proposal(uuid, boolean, date) from public, anon;
grant execute on function public.coach_week_gate(date) to authenticated;
grant execute on function public.evaluate_coach_calorie_change(date) to authenticated;
grant execute on function public.decide_calorie_proposal(uuid, boolean, date) to authenticated;
