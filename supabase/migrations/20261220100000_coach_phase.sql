-- T-187 (E13, US-82 data side, US-69 deficit cap, US-88 minimal): the phase of a coach plan, its reason and options,
-- the deficit cap of 500 kcal, the diet-break week, the cut-rate recompute and the minimal block.
-- Rules: planning/07-coach-design.md sections 3, 4 and 12.3; planning/04-business-rules.md sections 8 and 9.
-- Bases (never edited): coach_plans 20261208100000, coach_plan_sessions 20261210100000, coach_block_cardio 20261217100200.

-- ---------------------------------------------------------------------------
-- Columns: phase, phase_reason and phase_options. The plan JSON (nutrition.phase, .phase_reason, .phase_options) is
-- the source; the columns mirror it so the history and the block builder can filter without parsing JSON.
-- ---------------------------------------------------------------------------
create type public.coach_phase as enum ('cut', 'gain', 'maintenance', 'recomposition', 'regain', 'minimal');

alter table public.coach_plans
  add column phase         public.coach_phase,
  -- A code (intention, returning_break, returning_injury, user_busy); the Spanish text lives in the UI.
  add column phase_reason  text
    constraint coach_plans_phase_reason_values
      check (phase_reason in ('intention', 'returning_break', 'returning_injury', 'user_busy')),
  add column phase_options jsonb not null default '{"cut_rate_pct":0.5,"diet_break":false}'::jsonb
    constraint coach_plans_phase_options_shape check (
      jsonb_typeof(phase_options) = 'object'
      and jsonb_typeof(phase_options->'diet_break') = 'boolean'
      and case when jsonb_typeof(phase_options->'cut_rate_pct') = 'number'
               then (phase_options->>'cut_rate_pct')::numeric between 0.5 and 1.0
               else false end);

-- Plans stored before this task: the phase their intention implies.
update public.coach_plans
set phase = (case intention
               when 'fat_loss' then 'cut'
               when 'muscle' then 'gain'
               when 'recomposition' then 'recomposition'
               else 'maintenance' end)::public.coach_phase,
    phase_reason = 'intention';

update public.coach_plans
set nutrition = nutrition || jsonb_build_object(
      'phase', phase::text, 'phase_reason', phase_reason, 'phase_options', phase_options);

alter table public.coach_plans alter column phase set not null;

-- A row inserted without a phase (older callers, fixtures) takes the one its intention implies.
create function public.coach_plans_default_phase()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.phase is null then
    new.phase := (case new.intention
                    when 'fat_loss' then 'cut'
                    when 'muscle' then 'gain'
                    when 'recomposition' then 'recomposition'
                    else 'maintenance' end)::public.coach_phase;
  end if;
  if new.phase_reason is null then
    new.phase_reason := case when new.phase = 'minimal' then 'user_busy' else 'intention' end;
  end if;
  return new;
end;
$$;

create trigger coach_plans_default_phase
  before insert on public.coach_plans
  for each row execute function public.coach_plans_default_phase();

revoke execute on function public.coach_plans_default_phase() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- coach_assert_plan: the invariants of a plan by phase (04 sections 8.2 and 8.9). Same signature as the base.
-- Raises 22023. A legacy JSON without nutrition.phase takes the phase its intention implies.
-- ---------------------------------------------------------------------------
create or replace function public.coach_assert_plan(
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
  v_phase    text;
  v_options  jsonb := p_nutrition->'phase_options';
  v_break    jsonb := p_nutrition->'diet_break';
  v_nochange numeric;
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

  -- Phase, its reason and its options.
  v_phase := coalesce(p_nutrition->>'phase',
                      case p_intention when 'fat_loss' then 'cut' when 'muscle' then 'gain'
                                       when 'recomposition' then 'recomposition' else 'maintenance' end);
  if v_phase not in ('cut', 'gain', 'maintenance', 'recomposition', 'regain', 'minimal') then
    raise exception 'plan invariant: phase' using errcode = '22023';
  end if;
  if p_nutrition ? 'phase_reason'
     and p_nutrition->>'phase_reason' not in ('intention', 'returning_break', 'returning_injury', 'user_busy') then
    raise exception 'plan invariant: phase_reason' using errcode = '22023';
  end if;
  if v_options is not null
     and (jsonb_typeof(v_options) <> 'object'
          or jsonb_typeof(v_options->'diet_break') is distinct from 'boolean'
          or jsonb_typeof(v_options->'cut_rate_pct') is distinct from 'number'
          or (v_options->>'cut_rate_pct')::numeric not between 0.5 and 1.0) then
    raise exception 'plan invariant: phase_options' using errcode = '22023';
  end if;

  -- Calories: the floor of the user's sex, the adjustment range and a consistent strategy.
  if (p_nutrition->>'floor_kcal')::numeric is distinct from v_floor::numeric or v_target < v_floor then
    raise exception 'plan invariant: target_below_floor' using errcode = '22023';
  end if;
  if v_limited is not null and v_limited not in ('floor', 'goal_range', 'deficit_cap') then
    raise exception 'plan invariant: limited_by' using errcode = '22023';
  end if;
  if v_limited = 'floor' and v_target <> v_floor then
    raise exception 'plan invariant: floor_flag_without_floor_target' using errcode = '22023';
  end if;
  -- The deficit cap: at most 500 kcal below the expenditure (07 section 4).
  if v_adj <> v_target - v_exp or v_adj < -500 or v_adj > 750 then
    raise exception 'plan invariant: adjustment' using errcode = '22023';
  end if;
  if v_limited = 'deficit_cap' and (v_phase <> 'cut' or v_adj <> -500) then
    raise exception 'plan invariant: deficit_cap_flag' using errcode = '22023';
  end if;
  if v_strategy is distinct from (case when v_adj < 0 then 'deficit' when v_adj > 0 then 'surplus' else 'maintenance' end) then
    raise exception 'plan invariant: strategy_mismatch' using errcode = '22023';
  end if;

  -- Rate of change by phase (a slower rate needs a limiter flag; the maximum carries the rounding tolerance).
  if v_phase in ('cut', 'gain') then
    if v_phase = 'cut' then
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
  elsif v_phase = 'regain' then
    -- Never a deficit; +0.25 %/week only for a muscle or strength intention, otherwise the expenditure itself.
    if v_adj < 0 then
      raise exception 'plan invariant: regain_deficit' using errcode = '22023';
    end if;
    if p_intention in ('muscle', 'strength') then
      if v_adj > 0 and v_rate > 0.25 + 0.05 then
        raise exception 'plan invariant: regain_rate' using errcode = '22023';
      end if;
    elsif v_adj > 0 and v_limited is distinct from 'floor' then
      raise exception 'plan invariant: regain_surplus' using errcode = '22023';
    end if;
  else
    -- Maintenance, recomposition and minimal: inside the no-change range of 04 section 8.7 (0.25 %/week + rounding).
    v_nochange := 0.25 * v_w * 11 + 10;
    if abs(v_adj) > v_nochange then
      raise exception 'plan invariant: no_change_range' using errcode = '22023';
    end if;
  end if;

  -- The diet-break week: only inside a cut, consistent with the option flag, maintenance figures.
  if v_break is not null and jsonb_typeof(v_break) <> 'null' then
    if jsonb_typeof(v_break) <> 'object' or v_phase <> 'cut'
       or coalesce(v_options->>'diet_break', 'false') <> 'true' then
      raise exception 'plan invariant: diet_break_phase' using errcode = '22023';
    end if;
    begin
      if (v_break->>'target_kcal')::numeric < v_floor
         or (v_break->>'calorie_adjustment')::numeric <> (v_break->>'target_kcal')::numeric - v_exp
         or (v_break->>'calorie_adjustment')::numeric < 0
         or (v_break->>'week')::numeric <> 4 then
        raise exception 'plan invariant: diet_break' using errcode = '22023';
      end if;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'plan invariant: diet_break' using errcode = '22023';
    end;
  elsif v_phase = 'cut' and coalesce(v_options->>'diet_break', 'false') = 'true' then
    raise exception 'plan invariant: diet_break' using errcode = '22023';
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
-- propose_coach_plan: as before; the phase keys of the plan JSON are required and mirrored into the columns.
-- ---------------------------------------------------------------------------
create or replace function public.propose_coach_plan(
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
  if jsonb_typeof(p_nutrition->'phase') is distinct from 'string'
     or jsonb_typeof(p_nutrition->'phase_reason') is distinct from 'string'
     or jsonb_typeof(p_nutrition->'phase_options') is distinct from 'object' then
    raise exception 'invalid plan: phase keys are required' using errcode = '22023';
  end if;
  perform public.coach_assert_plan(v_uid, p_intention, p_block_start, p_nutrition, p_outcomes);

  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where user_id = v_uid and kind = 'plan' and status = 'pending'
      and plan_id in (select id from public.coach_plans where user_id = v_uid and status = 'proposed');
  update public.coach_plans set status = 'superseded' where user_id = v_uid and status = 'proposed';

  insert into public.coach_plans (user_id, block_start, intention, nutrition, outcomes, method,
                                  phase, phase_reason, phase_options)
  values (v_uid, p_block_start, p_intention, p_nutrition, p_outcomes, p_method,
          (p_nutrition->>'phase')::public.coach_phase, p_nutrition->>'phase_reason', p_nutrition->'phase_options')
  returning id into v_plan;

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
  values (v_uid, v_plan, 'plan', jsonb_build_object('nutrition', p_nutrition, 'outcomes', p_outcomes), p_input_summary)
  returning id into v_rec;

  return jsonb_build_object('plan_id', v_plan, 'recommendation_id', v_rec);
end;
$$;

-- ---------------------------------------------------------------------------
-- update_coach_plan_options: the recompute of a proposed cut with another rate / the diet break. The figures come
-- from the coach function (plan_recompute, no model, no usage); this function re-checks every invariant, rewrites the
-- nutrition and the options, and drops the `weight` outcome (its target and reason cited the old rate).
-- ---------------------------------------------------------------------------
create function public.update_coach_plan_options(
  p_plan_id uuid,
  p_nutrition jsonb,
  p_cut_rate_pct numeric,
  p_diet_break boolean
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_plan    public.coach_plans;
  v_options jsonb;
  v_n       jsonb;
  v_out     jsonb;
begin
  if p_plan_id is null or p_nutrition is null or jsonb_typeof(p_nutrition) <> 'object'
     or p_cut_rate_pct is null or p_diet_break is null then
    raise exception 'invalid options' using errcode = '22023';
  end if;

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_plan.status <> 'proposed' then
    raise exception 'plan is not proposed' using errcode = '55000';
  end if;
  if v_plan.phase <> 'cut' then
    raise exception 'only a cut has options' using errcode = '22023';
  end if;
  if p_cut_rate_pct < 0.5 or p_cut_rate_pct > 1.0 then
    raise exception 'cut rate must be between 0.5 and 1.0' using errcode = '22023';
  end if;

  v_options := jsonb_build_object('cut_rate_pct', p_cut_rate_pct, 'diet_break', p_diet_break);
  v_n := p_nutrition || jsonb_build_object(
    'phase', v_plan.phase::text, 'phase_reason', v_plan.phase_reason, 'phase_options', v_options);

  v_out := (select coalesce(jsonb_agg(o.value order by o.ordinality), '[]'::jsonb)
            from jsonb_array_elements(v_plan.outcomes) with ordinality o(value, ordinality)
            where o.value->>'kind' is distinct from 'weight');

  perform public.coach_assert_plan(v_uid, v_plan.intention, v_plan.block_start, v_n, v_out);

  update public.coach_plans
    set nutrition = v_n, outcomes = v_out, phase_options = v_options
    where id = p_plan_id and user_id = v_uid;
  update public.coach_recommendations
    set payload = jsonb_build_object('nutrition', v_n, 'outcomes', v_out)
    where plan_id = p_plan_id and user_id = v_uid and kind = 'plan' and status = 'pending';

  return jsonb_build_object('plan_id', p_plan_id, 'nutrition', v_n, 'outcomes', v_out);
end;
$$;

-- ---------------------------------------------------------------------------
-- coach_plan_week: the current week of a plan's block (1-based; 0 before the block starts). Calendar weeks from
-- block_start for now; T-189 moves this definition to training weeks and edits only this function.
-- ---------------------------------------------------------------------------
create function public.coach_plan_week(p_plan_id uuid, p_today date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select case when p_today < p.block_start then 0 else (p_today - p.block_start) / 7 + 1 end
  from public.coach_plans p
  where p.id = p_plan_id and p.user_id = (select auth.uid())
$$;

-- ---------------------------------------------------------------------------
-- apply_coach_diet_break: idempotent. In week 4 of an accepted cut with the diet break on, the nutrition goal takes
-- the maintenance figures of the plan; in any other week it takes the base figures. Never marks the goal as edited.
-- Returns {applied, week, target_kcal}: target_kcal is the target the week asks for (null when nothing applies).
-- ---------------------------------------------------------------------------
create function public.apply_coach_diet_break(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_week  integer;
  v_src   jsonb;
  v_goal  public.nutrition_goals;
  v_same  boolean;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted';
  if not found
     or coalesce(v_plan.phase_options->>'diet_break', 'false') <> 'true'
     or jsonb_typeof(v_plan.nutrition->'diet_break') is distinct from 'object'
     or coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false)
     or not (v_plan.folder_id is not null
             or exists (select 1 from public.coach_plan_sessions s
                        where s.plan_id = v_plan.id and s.user_id = v_uid and s.routine_id is not null)) then
    return jsonb_build_object('applied', false, 'week', 0, 'target_kcal', null);
  end if;

  v_week := public.coach_plan_week(v_plan.id, p_today);
  v_src := case when v_week = 4 then v_plan.nutrition->'diet_break' else v_plan.nutrition end;

  select * into v_goal from public.nutrition_goals where user_id = v_uid;
  v_same := found
    and v_goal.target_kcal = (v_src->>'target_kcal')::int
    and v_goal.calorie_adjustment = (v_src->>'calorie_adjustment')::int
    and v_goal.strategy::text = v_src->>'strategy'
    and v_goal.protein_pct = (v_src->>'protein_pct')::smallint
    and v_goal.carbs_pct = (v_src->>'carbs_pct')::smallint
    and v_goal.fat_pct = (v_src->>'fat_pct')::smallint;
  if v_same then
    return jsonb_build_object('applied', false, 'week', v_week, 'target_kcal', (v_src->>'target_kcal')::int);
  end if;

  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (v_uid, (v_src->>'strategy')::public.goal_strategy, (v_src->>'calorie_adjustment')::int,
          (v_src->>'target_kcal')::int, (v_src->>'protein_pct')::smallint, (v_src->>'carbs_pct')::smallint,
          (v_src->>'fat_pct')::smallint)
  on conflict (user_id) do update
    set strategy = excluded.strategy, calorie_adjustment = excluded.calorie_adjustment,
        target_kcal = excluded.target_kcal, protein_pct = excluded.protein_pct,
        carbs_pct = excluded.carbs_pct, fat_pct = excluded.fat_pct;
  perform set_config('app.coach_write', 'off', true);

  return jsonb_build_object('applied', true, 'week', v_week, 'target_kcal', (v_src->>'target_kcal')::int);
end;
$$;

-- ---------------------------------------------------------------------------
-- coach_phase_history: what the next block is built from (security invoker: RLS shows the caller's plans only).
-- previous = the latest superseded / accepted plan with a saved block: its phase, weekly sets per group, main lifts
-- (week 1, in order) and rep ranges per exercise (the first appearance). minimal_blocks_in_a_row = the latest
-- consecutive minimal blocks; deficit_weeks_done = 4 per block of the latest consecutive run whose calories are a cut
-- (nutrition.phase; a minimal block keeps the calories of the plan), 3 with a diet break.
-- ---------------------------------------------------------------------------
create function public.coach_phase_history(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_prev    public.coach_plans;
  v_main    jsonb;
  v_ranges  jsonb;
  v_minimal integer := 0;
  v_deficit integer := 0;
  v_row     record;
begin
  select p.* into v_prev
  from public.coach_plans p
  where p.user_id = v_uid and p.status in ('superseded', 'accepted')
    and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
  order by p.block_start desc, p.created_at desc
  limit 1;
  if not found then
    return jsonb_build_object('previous', null, 'minimal_blocks_in_a_row', 0, 'deficit_weeks_done', 0);
  end if;

  select coalesce(jsonb_agg(x.id order by x.first_pos), '[]'::jsonb) into v_main
  from (
    select e.value->>'exercise_id' as id, min(s.session_index * 100 + e.ordinality) as first_pos
    from public.coach_plan_sessions s
    cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
    where s.plan_id = v_prev.id and s.week = 1 and e.value->>'role' = 'main'
    group by e.value->>'exercise_id'
  ) x;

  select coalesce(jsonb_object_agg(r.id, jsonb_build_array(r.rep_min, r.rep_max)), '{}'::jsonb) into v_ranges
  from (
    select distinct on (e.value->>'exercise_id')
           e.value->>'exercise_id' as id, (e.value->>'rep_min')::int as rep_min, (e.value->>'rep_max')::int as rep_max
    from public.coach_plan_sessions s
    cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
    where s.plan_id = v_prev.id and s.week = 1 and e.value->>'role' in ('main', 'isolation')
    order by e.value->>'exercise_id', s.session_index, e.ordinality
  ) r;

  -- Consecutive blocks, newest first.
  for v_row in
    select p.phase, coalesce((p.phase_options->>'diet_break')::boolean, false) as diet_break
    from public.coach_plans p
    where p.user_id = v_uid and p.status in ('superseded', 'accepted')
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
    order by p.block_start desc, p.created_at desc
  loop
    exit when v_row.phase <> 'minimal';
    v_minimal := v_minimal + 1;
  end loop;
  for v_row in
    select coalesce(p.nutrition->>'phase', p.phase::text) as calorie_phase,
           coalesce((p.phase_options->>'diet_break')::boolean, false) as diet_break
    from public.coach_plans p
    where p.user_id = v_uid and p.status in ('superseded', 'accepted')
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
    order by p.block_start desc, p.created_at desc
  loop
    exit when v_row.calorie_phase <> 'cut';
    v_deficit := v_deficit + case when v_row.diet_break then 3 else 4 end;
  end loop;

  return jsonb_build_object(
    'previous', jsonb_build_object(
      'phase', v_prev.phase::text,
      'sets_per_group', case when jsonb_typeof(v_prev.training->'weekly_sets') = 'object'
                             then v_prev.training->'weekly_sets' else '{}'::jsonb end,
      'main_lifts', v_main,
      'rep_ranges', v_ranges),
    'minimal_blocks_in_a_row', v_minimal,
    'deficit_weeks_done', v_deficit);
end;
$$;

-- ---------------------------------------------------------------------------
-- coach_assert_block: as 20261217100200 plus the minimal phase (training.phase = 'minimal'): 1 session (2 from 65),
-- 1 set per lifting slot (2 from 65), main lifts only, cardio at most 30 min per routine (6 units instead of 12).
-- ---------------------------------------------------------------------------
create or replace function public.coach_assert_block(
  p_uid uuid,
  p_training jsonb,
  p_sessions jsonb
)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_days      integer;
  v_equipment text[];
  v_session   record;
  v_ex        record;
  v_row       record;
  v_role      text;
  v_rest      integer;
  v_sets      integer;
  v_rmin      integer;
  v_rmax      integer;
  v_reason    text;
  v_seen_iso  boolean;
  v_ids       uuid[];
  v_id        uuid;
  v_cardio    jsonb := p_training->'cardio';
  v_target    integer;
  v_units     integer;
  v_planned   integer;
  v_per       integer[] := array[]::integer[];
  v_totals    integer[] := array[]::integer[];
  v_smin      integer;
  v_dur       integer;
  v_seen_card boolean;
  v_lifting   integer;
  v_minimal   boolean := false;
  v_senior    boolean := false;
  v_capunits  integer := 12;
  v_mincount  integer := 1;
begin
  if p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array' then
    raise exception 'block invariant: shape' using errcode = '22023';
  end if;

  if p_training ? 'phase' then
    if p_training->>'phase' is null
       or p_training->>'phase' not in ('cut', 'gain', 'maintenance', 'recomposition', 'regain', 'minimal') then
      raise exception 'block invariant: phase' using errcode = '22023';
    end if;
    v_minimal := p_training->>'phase' = 'minimal';
  end if;
  if v_minimal then
    v_capunits := 6;
    select coalesce(pr.birth_date <= (now() at time zone 'utc')::date - interval '65 years', false) into v_senior
    from public.profiles pr where pr.id = p_uid;
    v_senior := coalesce(v_senior, false);
    v_mincount := 1 + v_senior::int;
  end if;

  begin
    v_days := (p_training->>'days_per_week')::numeric::integer;
    if jsonb_typeof(coalesce(p_training->'equipment', '[]'::jsonb)) <> 'array' then
      raise exception 'block invariant: equipment_shape' using errcode = '22023';
    end if;
    select coalesce(array_agg(e.value), '{}') into v_equipment
    from jsonb_array_elements_text(coalesce(p_training->'equipment', '[]'::jsonb)) e(value);
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception 'block invariant: malformed_training' using errcode = '22023';
  end;

  if jsonb_typeof(p_training->'block_weeks') is distinct from 'number'
     or (p_training->>'block_weeks')::numeric not between 1 and 4
     or (p_training->>'block_weeks')::numeric <> trunc((p_training->>'block_weeks')::numeric) then
    raise exception 'block invariant: block_weeks' using errcode = '22023';
  end if;
  if v_days is null or v_days not between 1 and 7 or jsonb_array_length(p_sessions) <> v_days then
    raise exception 'block invariant: session_count' using errcode = '22023';
  end if;
  if v_minimal and v_days <> v_mincount then
    raise exception 'block invariant: minimal_sessions' using errcode = '22023';
  end if;

  -- Weekly cardio plan: the stated split must equal the one derived from the target (units of 5 min, at most
  -- 60 min per routine (30 in the minimal phase), remainder to the first routines, the rest is "por tu cuenta").
  if v_cardio is not null then
    begin
      if jsonb_typeof(v_cardio) <> 'object'
         or jsonb_typeof(v_cardio->'target_min_week') is distinct from 'number'
         or jsonb_typeof(v_cardio->'per_routine_min') is distinct from 'array'
         or jsonb_typeof(p_training->'session_minutes') is distinct from 'number'
         or jsonb_typeof(p_training->'routine_total_min') is distinct from 'array' then
        raise exception 'block invariant: cardio_shape' using errcode = '22023';
      end if;
      v_target := (v_cardio->>'target_min_week')::numeric::integer;
      v_smin := (p_training->>'session_minutes')::numeric::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'block invariant: cardio_shape' using errcode = '22023';
    end;
    if v_target not between 0 and 600 then
      raise exception 'block invariant: cardio_total' using errcode = '22023';
    end if;
    v_units := v_target / 5;
    v_planned := 5 * least(v_units, v_capunits * v_days);
    for v_i in 1..v_days loop
      v_per := v_per || (5 * ((least(v_units, v_capunits * v_days) / v_days)
                              + case when v_i <= least(v_units, v_capunits * v_days) % v_days then 1 else 0 end));
      v_totals := v_totals || (v_smin + v_per[v_i]);
    end loop;
    if v_cardio->'planned_min_week' is distinct from to_jsonb(v_planned)
       or v_cardio->'on_your_own_min' is distinct from to_jsonb(v_target - v_planned)
       or v_cardio->'per_routine_min' is distinct from to_jsonb(v_per)
       or p_training->'routine_total_min' is distinct from to_jsonb(v_totals) then
      raise exception 'block invariant: cardio_total' using errcode = '22023';
    end if;
    if v_cardio->>'pace' is distinct from 'suave' then
      raise exception 'block invariant: cardio_pace' using errcode = '22023';
    end if;
  end if;

  for v_session in
    select s.value, s.ordinality from jsonb_array_elements(p_sessions) with ordinality s(value, ordinality)
  loop
    if jsonb_typeof(v_session.value) <> 'object'
       or jsonb_typeof(v_session.value->'name') is distinct from 'string'
       or char_length(btrim(v_session.value->>'name')) not between 1 and 100
       or jsonb_typeof(coalesce(v_session.value->'focus', '[]'::jsonb)) <> 'array'
       or jsonb_typeof(v_session.value->'exercises') is distinct from 'array'
       or jsonb_array_length(v_session.value->'exercises') = 0 then
      raise exception 'block invariant: session_shape' using errcode = '22023';
    end if;

    v_seen_iso := false;
    v_seen_card := false;
    v_lifting := 0;
    v_ids := '{}';
    for v_ex in
      select e.value from jsonb_array_elements(v_session.value->'exercises') with ordinality e(value, ordinality)
      order by e.ordinality
    loop
      if jsonb_typeof(v_ex.value) <> 'object' then
        raise exception 'block invariant: exercise_shape' using errcode = '22023';
      end if;
      -- No warm-up anywhere in a block.
      if lower(coalesce(v_ex.value->>'role', '') || ' ' || coalesce(v_ex.value->>'exercise_key', '')) ~ 'warm[-_ ]?up' then
        raise exception 'block invariant: warmup' using errcode = '22023';
      end if;

      if v_ex.value->>'role' = 'cardio' then
        -- Cardio: last, only one, planned minutes of the routine, no lifting fields.
        if v_seen_card then
          raise exception 'block invariant: cardio_count' using errcode = '22023';
        end if;
        v_seen_card := true;
        if v_cardio is null or coalesce(v_per[v_session.ordinality::integer], 0) = 0 then
          raise exception 'block invariant: cardio_total' using errcode = '22023';
        end if;
        if v_ex.value ?| array['rep_min', 'rep_max', 'rest_seconds', 'rpe', 'target_rpe', 'reps'] then
          raise exception 'block invariant: cardio_fields' using errcode = '22023';
        end if;
        begin
          v_id := (v_ex.value->>'exercise_id')::uuid;
          v_sets := (v_ex.value->>'sets')::numeric::integer;
          v_dur := (v_ex.value->>'duration_min')::numeric::integer;
          if (v_ex.value->>'duration_min')::numeric <> v_dur then
            raise exception 'block invariant: cardio_minutes' using errcode = '22023';
          end if;
        exception when invalid_text_representation or numeric_value_out_of_range then
          raise exception 'block invariant: malformed_exercise' using errcode = '22023';
        end;
        if v_id is null or v_sets is null or v_dur is null
           or jsonb_typeof(v_ex.value->'reason') is distinct from 'string' then
          raise exception 'block invariant: missing_field' using errcode = '22023';
        end if;
        if not exists (select 1 from public.exercises e
                       where e.id = v_id and (not e.is_custom or e.created_by = p_uid)) then
          raise exception 'block invariant: unknown_exercise' using errcode = '22023';
        end if;
        if not exists (select 1 from public.coach_cardio_options(v_equipment) o where o.exercise_id = v_id) then
          raise exception 'block invariant: cardio_equipment' using errcode = '22023';
        end if;
        if v_id = any (v_ids) then
          raise exception 'block invariant: duplicate_exercise' using errcode = '22023';
        end if;
        v_ids := v_ids || v_id;
        if v_sets <> 1 then
          raise exception 'block invariant: sets' using errcode = '22023';
        end if;
        if v_dur not between 5 and 60 or v_dur % 5 <> 0 or v_dur <> v_per[v_session.ordinality::integer]
           or v_ex.value->>'pace' is distinct from 'suave' then
          raise exception 'block invariant: cardio_minutes' using errcode = '22023';
        end if;
        v_reason := btrim(v_ex.value->>'reason');
        if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
          raise exception 'block invariant: reason' using errcode = '22023';
        end if;
        continue;
      end if;

      if v_seen_card then
        raise exception 'block invariant: cardio_order' using errcode = '22023';
      end if;

      begin
        v_id := (v_ex.value->>'exercise_id')::uuid;
        v_role := v_ex.value->>'role';
        v_rest := (v_ex.value->>'rest_seconds')::numeric::integer;
        v_sets := (v_ex.value->>'sets')::numeric::integer;
        v_rmin := (v_ex.value->>'rep_min')::numeric::integer;
        v_rmax := (v_ex.value->>'rep_max')::numeric::integer;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'block invariant: malformed_exercise' using errcode = '22023';
      end;
      if v_id is null or v_role is null or v_rest is null or v_sets is null or v_rmin is null or v_rmax is null
         or jsonb_typeof(v_ex.value->'reason') is distinct from 'string' then
        raise exception 'block invariant: missing_field' using errcode = '22023';
      end if;

      -- Visible to the caller: a catalogue row or one of their own custom exercises.
      select e.equipment::text as equipment, e.movement_pattern::text as pattern into v_row
      from public.exercises e
      where e.id = v_id and (not e.is_custom or e.created_by = p_uid);
      if not found then
        raise exception 'block invariant: unknown_exercise' using errcode = '22023';
      end if;
      if cardinality(v_equipment) > 0 and not (v_row.equipment = any (v_equipment)) then
        raise exception 'block invariant: equipment' using errcode = '22023';
      end if;
      if v_id = any (v_ids) then
        raise exception 'block invariant: duplicate_exercise' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;

      if v_role = 'main' then
        if v_seen_iso then
          raise exception 'block invariant: order' using errcode = '22023';
        end if;
        if v_row.pattern is null or v_row.pattern not in
           ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 120 and 180 then
          raise exception 'block invariant: rest' using errcode = '22023';
        end if;
      elsif v_role = 'isolation' then
        v_seen_iso := true;
        if v_minimal then
          raise exception 'block invariant: minimal_main_only' using errcode = '22023';
        end if;
        if v_row.pattern is null or v_row.pattern not in ('isolation', 'core') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 60 and 120 then
          raise exception 'block invariant: rest' using errcode = '22023';
        end if;
      else
        raise exception 'block invariant: role' using errcode = '22023';
      end if;

      if v_sets not between 1 and 10 then
        raise exception 'block invariant: sets' using errcode = '22023';
      end if;
      if v_minimal and v_sets <> v_mincount then
        raise exception 'block invariant: minimal_sets' using errcode = '22023';
      end if;
      if v_rmin not between 1 and 50 or v_rmax not between 1 and 50 or v_rmin > v_rmax then
        raise exception 'block invariant: rep_range' using errcode = '22023';
      end if;
      v_reason := btrim(v_ex.value->>'reason');
      if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
        raise exception 'block invariant: reason' using errcode = '22023';
      end if;
      v_lifting := v_lifting + 1;
    end loop;

    if v_lifting = 0 then
      raise exception 'block invariant: empty_session' using errcode = '22023';
    end if;
    -- A routine with cardio minutes must carry its cardio exercise (no minutes, no exercise).
    if v_cardio is not null and coalesce(v_per[v_session.ordinality::integer], 0) > 0 and not v_seen_card then
      raise exception 'block invariant: cardio_total' using errcode = '22023';
    end if;
  end loop;
end;
$$;

revoke execute on function public.coach_assert_block(uuid, jsonb, jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- attach_coach_block: as 20261210100000 plus the phase. A minimal block sets the plan's phase to minimal
-- (reason user_busy) and leaves its calories untouched; any other block restores the phase of the plan JSON. A block
-- with cardio keeps nutrition.activity.cardio_min_week at the block's target unless the user edited it, so
-- coach_plan_weeks.cardio_min_target stays coherent.
-- ---------------------------------------------------------------------------
create or replace function public.attach_coach_block(
  p_plan_id uuid,
  p_training jsonb,
  p_sessions jsonb,
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
  v_uid   uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_plan  public.coach_plans;
  v_ids   jsonb;
begin
  if p_plan_id is null or p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array'
     or p_method is null or jsonb_typeof(p_method) <> 'object'
     or p_input_summary is null then
    raise exception 'invalid block' using errcode = '22023';
  end if;

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;
  if v_plan.folder_id is not null
     or exists (select 1 from public.coach_plan_sessions s
                where s.plan_id = p_plan_id and s.user_id = v_uid and s.routine_id is not null) then
    raise exception 'block already saved' using errcode = '55000';
  end if;

  perform public.coach_assert_block(v_uid, p_training, p_sessions);

  delete from public.coach_plan_sessions where plan_id = p_plan_id and user_id = v_uid;
  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind = 'block' and status = 'pending';

  update public.coach_plans
    set training = p_training,
        phase = case when p_training->>'phase' = 'minimal' then 'minimal'::public.coach_phase
                     else coalesce((nutrition->>'phase')::public.coach_phase, phase) end,
        phase_reason = case when p_training->>'phase' = 'minimal' then 'user_busy'
                            else coalesce(nutrition->>'phase_reason', phase_reason) end,
        nutrition = case
          when jsonb_typeof(nutrition->'activity') = 'object'
               and coalesce((nutrition->'activity'->>'cardio_edited')::boolean, false) = false
               and jsonb_typeof(p_training->'cardio'->'target_min_week') = 'number'
          then jsonb_set(nutrition, '{activity,cardio_min_week}', p_training->'cardio'->'target_min_week')
          else nutrition end
    where id = p_plan_id and user_id = v_uid;

  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, focus, exercises)
  select p_plan_id, v_uid, w.n::smallint, s.ordinality::smallint, btrim(s.value->>'name'),
         coalesce((select array_agg(f.value) from jsonb_array_elements_text(coalesce(s.value->'focus', '[]'::jsonb)) f(value)), '{}'),
         s.value->'exercises'
  from jsonb_array_elements(p_sessions) with ordinality s(value, ordinality)
  cross join generate_series(1, (p_training->>'block_weeks')::int) w(n);

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
  values (v_uid, p_plan_id, 'block',
          jsonb_build_object('training', p_training, 'sessions', p_sessions, 'method', p_method),
          p_input_summary);

  select coalesce(jsonb_agg(s.id order by s.week, s.session_index), '[]'::jsonb) into v_ids
  from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid;

  return jsonb_build_object('plan_id', p_plan_id, 'sessions', v_ids);
end;
$$;

revoke execute on function public.update_coach_plan_options(uuid, jsonb, numeric, boolean) from public, anon;
revoke execute on function public.coach_plan_week(uuid, date) from public, anon;
revoke execute on function public.apply_coach_diet_break(date) from public, anon;
revoke execute on function public.coach_phase_history(date) from public, anon;
grant execute on function public.update_coach_plan_options(uuid, jsonb, numeric, boolean) to authenticated;
grant execute on function public.coach_plan_week(uuid, date) to authenticated;
grant execute on function public.apply_coach_diet_break(date) to authenticated;
grant execute on function public.coach_phase_history(date) to authenticated;
