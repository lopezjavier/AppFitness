-- T-248 (US-115): the diet-break switch of an ACCEPTED cut ("Tu plan"). The week-4 figures come from the coach function
-- (kind plan_diet_break, no model, no usage); this function re-checks every invariant and writes ONLY
-- nutrition.diet_break, nutrition.phase_options.diet_break and the phase_options column. Calories, band, outcomes,
-- `edited_by_user` and the recommendations are never touched.
-- Gap closed here: apply_coach_diet_break returns early when the option is off, so turning the break OFF during week 4
-- would leave the maintenance goal forever. This function restores the base goal itself in that case.
create function public.set_coach_plan_diet_break(
  p_plan_id uuid,
  p_diet_break boolean,
  p_week jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_today   date := (now() at time zone 'utc')::date;
  v_uid     uuid := public.coach_guard(v_today);
  v_plan    public.coach_plans;
  v_off     boolean;
  v_old     jsonb;
  v_goal    public.nutrition_goals;
  v_same    boolean;
  v_options jsonb;
  v_n       jsonb;
begin
  if p_plan_id is null or p_diet_break is null then
    raise exception 'invalid diet break' using errcode = '22023';
  end if;
  v_off := p_week is null or jsonb_typeof(p_week) = 'null';

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_plan.status <> 'accepted' then
    raise exception 'plan is not accepted' using errcode = '55000';
  end if;
  if v_plan.phase <> 'cut' then
    raise exception 'only a cut has a diet break' using errcode = '22023';
  end if;
  if coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false) then
    raise exception 'plan edited by the user has no diet break' using errcode = '22023';
  end if;

  -- The week is there when (and only when) the break is on, with the keys of a DietBreakWeek.
  if p_diet_break = v_off then
    raise exception 'diet break and week are inconsistent' using errcode = '22023';
  end if;
  if not v_off and (
       jsonb_typeof(p_week) <> 'object'
       or not (p_week ?& array['target_kcal', 'calorie_adjustment', 'strategy', 'protein_pct', 'carbs_pct',
                               'fat_pct', 'protein_g', 'carbs_g', 'fat_g', 'week'])) then
    raise exception 'malformed diet break week' using errcode = '22023';
  end if;

  v_old := v_plan.nutrition->'diet_break';
  v_options := coalesce(v_plan.phase_options, '{}'::jsonb) || jsonb_build_object('diet_break', p_diet_break);
  v_n := v_plan.nutrition || jsonb_build_object(
    'phase_options', coalesce(v_plan.nutrition->'phase_options', '{}'::jsonb) || jsonb_build_object('diet_break', p_diet_break),
    'diet_break', case when v_off then 'null'::jsonb else p_week end);

  perform public.coach_assert_plan(v_uid, v_plan.intention, v_plan.block_start, v_n, v_plan.outcomes);

  update public.coach_plans
    set nutrition = v_n, phase_options = v_options
    where id = p_plan_id and user_id = v_uid;

  -- Turning OFF while the goal sits at the week-4 maintenance figures: back to the base figures now.
  if v_off and coalesce(v_plan.phase_options->>'diet_break', 'false') = 'true'
     and jsonb_typeof(v_old) = 'object'
     and public.coach_plan_week(p_plan_id, v_today) = 4 then
    select * into v_goal from public.nutrition_goals where user_id = v_uid;
    v_same := found
      and v_goal.target_kcal = (v_old->>'target_kcal')::int
      and v_goal.calorie_adjustment = (v_old->>'calorie_adjustment')::int
      and v_goal.strategy::text = v_old->>'strategy'
      and v_goal.protein_pct = (v_old->>'protein_pct')::smallint
      and v_goal.carbs_pct = (v_old->>'carbs_pct')::smallint
      and v_goal.fat_pct = (v_old->>'fat_pct')::smallint;
    if v_same then
      perform set_config('app.coach_write', 'on', true);
      update public.nutrition_goals
        set strategy = (v_n->>'strategy')::public.goal_strategy,
            calorie_adjustment = (v_n->>'calorie_adjustment')::int,
            target_kcal = (v_n->>'target_kcal')::int,
            protein_pct = (v_n->>'protein_pct')::smallint,
            carbs_pct = (v_n->>'carbs_pct')::smallint,
            fat_pct = (v_n->>'fat_pct')::smallint
        where user_id = v_uid;
      perform set_config('app.coach_write', 'off', true);
    end if;
  end if;

  return jsonb_build_object('plan_id', p_plan_id, 'nutrition', v_n);
end;
$$;

revoke execute on function public.set_coach_plan_diet_break(uuid, boolean, jsonb) from public, anon;
grant execute on function public.set_coach_plan_diet_break(uuid, boolean, jsonb) to authenticated;
