-- T-200 (E13, ADR-91): the "regain" phase (Recuperar) is retired. Nothing can select it after T-198.
-- A stored regain plan becomes Maintenance: phase and phase_reason (column and JSON mirror) change, nothing else
-- (calories, macros, block and edited_by_user stay as they were). The retirement reason code "regain_retired" is
-- documented only: phase_reason keeps the UI-meaningful codes (intention, user_busy), so the plan stores "intention".
-- Postgres cannot drop an enum value, so the type is recreated. Bases (never edited): 20261220100000.

-- (1) Backfill. The coach_plans_default_phase trigger is before insert only, so these updates are not rewritten.
update public.coach_plans
set phase = 'maintenance',
    phase_reason = 'intention',
    nutrition = jsonb_set(jsonb_set(nutrition, '{phase}', '"maintenance"'), '{phase_reason}', '"intention"')
where phase = 'regain';

-- T-198 removed the situation question: its two reason codes are retired too.
update public.coach_plans
set phase_reason = 'intention',
    nutrition = case when nutrition ? 'phase_reason'
                     then jsonb_set(nutrition, '{phase_reason}', '"intention"') else nutrition end
where phase_reason in ('returning_break', 'returning_injury');

alter table public.coach_plans drop constraint coach_plans_phase_reason_values;
alter table public.coach_plans
  add constraint coach_plans_phase_reason_values check (phase_reason in ('intention', 'user_busy'));

-- (2) Enum without regain (no index, policy or default depends on the column; plpgsql bodies cast by name).
alter type public.coach_phase rename to coach_phase_old;
create type public.coach_phase as enum ('cut', 'gain', 'maintenance', 'recomposition', 'minimal');
alter table public.coach_plans
  alter column phase type public.coach_phase using phase::text::public.coach_phase;
drop type public.coach_phase_old;

-- (3) coach_assert_plan without the regain branch and the retired reasons.
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
  if v_phase not in ('cut', 'gain', 'maintenance', 'recomposition', 'minimal') then
    raise exception 'plan invariant: phase' using errcode = '22023';
  end if;
  if p_nutrition ? 'phase_reason'
     and p_nutrition->>'phase_reason' not in ('intention', 'user_busy') then
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

-- (4) coach_assert_block: only its accepted phase list changes (it has no other regain rule). Patch the stored
-- definition and fail loudly if the text is not found.
do $patch$
declare
  v_def text;
  v_new text;
begin
  v_def := pg_get_functiondef('public.coach_assert_block(uuid, jsonb, jsonb)'::regprocedure);
  v_new := replace(v_def, '''recomposition'', ''regain'', ''minimal''', '''recomposition'', ''minimal''');
  if v_new = v_def or v_new like '%regain%' then
    raise exception 'coach_assert_block patch did not apply';
  end if;
  execute v_new;
end
$patch$;

revoke execute on function public.coach_assert_block(uuid, jsonb, jsonb) from public, anon, authenticated;
