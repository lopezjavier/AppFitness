-- T-112 (US-50, ADR-65): cooking adds fat as a percentage of the raw weight instead of grams of oil x retention.
-- added_fat_g = round(raw_g * pct / 100, 1); added_kcal = round(added_fat_g * 9, 1).
-- Mirrored by the Kotlin CookingImpact / FoodPreferences (defaults 1 / 2 / 8 %); keep in sync.
--
-- The previously saved gram amounts (olive_oil_*_g) mean something else and are discarded: every existing
-- food_preferences row is reset to the new defaults. Past entries and their snapshots are not recalculated.
-- food_log_entries.cooking_oil_retained_g keeps its name and now stores the added fat in grams.

-- ---------------------------------------------------------------------------
-- food_preferences: percentages instead of grams
-- ---------------------------------------------------------------------------
alter table public.food_preferences
  drop column olive_oil_griddle_g,
  drop column olive_oil_air_fryer_g,
  drop column olive_oil_fried_g,
  add column fat_pct_griddle   numeric(4, 1) not null default 1 check (fat_pct_griddle between 0 and 20),
  add column fat_pct_air_fryer numeric(4, 1) not null default 2 check (fat_pct_air_fryer between 0 and 20),
  add column fat_pct_fried     numeric(4, 1) not null default 8 check (fat_pct_fried between 0 and 20);

-- ---------------------------------------------------------------------------
-- cooking_impact: fat and kcal added by a cooking method to p_amount_g of raw food
-- ---------------------------------------------------------------------------
drop function public.cooking_impact(public.food_cooking_method, uuid);

create function public.cooking_impact(
  p_method   public.food_cooking_method,
  p_amount_g numeric,
  p_user     uuid default auth.uid())
returns table (added_fat_g numeric, added_kcal numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with pct as (
    select case p_method
             when 'griddle'   then coalesce(fp.fat_pct_griddle, 1)
             when 'air_fryer' then coalesce(fp.fat_pct_air_fryer, 2)
             when 'fried'     then coalesce(fp.fat_pct_fried, 8)
             else 0
           end as p
    from (select 1) one
    left join public.food_preferences fp on fp.user_id = p_user
  ),
  fat as (
    select round(p_amount_g * pct.p / 100, 1) as g from pct
  )
  select fat.g, round(fat.g * 9, 1) from fat;
$$;

revoke execute on function public.cooking_impact(public.food_cooking_method, numeric, uuid) from public, anon;
grant execute on function public.cooking_impact(public.food_cooking_method, numeric, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- save_preferences: the three olive oil amounts become three percentages (null = unchanged)
-- ---------------------------------------------------------------------------
drop function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric
);

create function public.save_preferences(
  p_display_name         text,
  p_sex                  public.sex,
  p_birth_date           date,
  p_height_cm            numeric,
  p_activity_level       public.activity_level,
  p_strategy             public.goal_strategy,
  p_calorie_adjustment   int,
  p_target_kcal          int,
  p_protein_pct          smallint,
  p_carbs_pct            smallint,
  p_fat_pct              smallint,
  p_weight_kg            numeric default null,
  p_measured_at          timestamptz default null,
  p_day_start            timestamptz default null,
  p_day_end              timestamptz default null,
  p_warm_up_included     boolean default null,
  p_previous_values_mode public.previous_values_mode default null,
  p_bar_weight_kg        numeric default null,
  p_plate_kg             numeric[] default null,
  p_dumbbell_handle_kg   numeric default null,
  p_fat_pct_griddle      numeric default null,
  p_fat_pct_air_fryer    numeric default null,
  p_fat_pct_fried        numeric default null
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_weight_id uuid;
begin
  -- Weight arguments are all-or-none; validate before writing anything.
  if p_weight_kg is not null then
    if p_measured_at is null or p_day_start is null or p_day_end is null or p_day_end <= p_day_start then
      raise exception 'weight requires measured_at and a valid day range' using errcode = '22023';
    end if;
  end if;

  update public.profiles
  set display_name = btrim(p_display_name, E' \t\n\r\f'),
      sex = p_sex,
      birth_date = p_birth_date,
      height_cm = p_height_cm,
      activity_level = p_activity_level
  where id = (select auth.uid());

  if not found then
    raise exception 'profile not found' using errcode = 'P0002';
  end if;

  insert into public.nutrition_goals (
    user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct
  )
  values (
    (select auth.uid()), p_strategy, p_calorie_adjustment, p_target_kcal,
    p_protein_pct, p_carbs_pct, p_fat_pct
  )
  on conflict (user_id) do update
  set strategy = excluded.strategy,
      calorie_adjustment = excluded.calorie_adjustment,
      target_kcal = excluded.target_kcal,
      protein_pct = excluded.protein_pct,
      carbs_pct = excluded.carbs_pct,
      fat_pct = excluded.fat_pct;

  if p_warm_up_included is not null or p_previous_values_mode is not null
     or p_bar_weight_kg is not null or p_plate_kg is not null
     or p_dumbbell_handle_kg is not null then
    insert into public.training_preferences (
      user_id, warm_up_included, previous_values_mode,
      bar_weight_kg, plate_kg, dumbbell_handle_kg
    )
    values (
      (select auth.uid()), coalesce(p_warm_up_included, false), coalesce(p_previous_values_mode, 'overall'),
      coalesce(p_bar_weight_kg, 20),
      coalesce(p_plate_kg, '{25,20,15,10,5,2.5,2,1.25,1,0.5}'::numeric[]),
      coalesce(p_dumbbell_handle_kg, 1)
    )
    on conflict (user_id) do update
    set warm_up_included = coalesce(p_warm_up_included, public.training_preferences.warm_up_included),
        previous_values_mode = coalesce(p_previous_values_mode, public.training_preferences.previous_values_mode),
        bar_weight_kg = coalesce(p_bar_weight_kg, public.training_preferences.bar_weight_kg),
        plate_kg = coalesce(p_plate_kg, public.training_preferences.plate_kg),
        dumbbell_handle_kg = coalesce(p_dumbbell_handle_kg, public.training_preferences.dumbbell_handle_kg);
  end if;

  -- Out-of-range percentages surface as 23514 from the table checks and roll the whole call back.
  if p_fat_pct_griddle is not null or p_fat_pct_air_fryer is not null
     or p_fat_pct_fried is not null then
    insert into public.food_preferences (
      user_id, fat_pct_griddle, fat_pct_air_fryer, fat_pct_fried
    )
    values (
      (select auth.uid()),
      coalesce(p_fat_pct_griddle, 1),
      coalesce(p_fat_pct_air_fryer, 2),
      coalesce(p_fat_pct_fried, 8)
    )
    on conflict (user_id) do update
    set fat_pct_griddle = coalesce(p_fat_pct_griddle, public.food_preferences.fat_pct_griddle),
        fat_pct_air_fryer = coalesce(p_fat_pct_air_fryer, public.food_preferences.fat_pct_air_fryer),
        fat_pct_fried = coalesce(p_fat_pct_fried, public.food_preferences.fat_pct_fried);
  end if;

  if p_weight_kg is not null then
    select m.id into v_weight_id
    from public.body_measurements m
    where m.user_id = (select auth.uid())
      and m.source = 'manual'
      and m.measured_at >= p_day_start
      and m.measured_at < p_day_end
    order by m.measured_at desc
    limit 1;

    if v_weight_id is not null then
      update public.body_measurements
      set weight_kg = p_weight_kg, measured_at = p_measured_at
      where id = v_weight_id;
    else
      insert into public.body_measurements (user_id, measured_at, weight_kg, source)
      values ((select auth.uid()), p_measured_at, p_weight_kg, 'manual');
    end if;
  end if;
end;
$$;

revoke execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric
) from public, anon;
grant execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric
) to authenticated;

-- ---------------------------------------------------------------------------
-- apply_meal_defaults: same signature; the added fat now depends on the item's raw amount.
-- cooking_oil_retained_g stores the added fat in grams.
-- ---------------------------------------------------------------------------
create or replace function public.apply_meal_defaults(p_log_date date)
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_utc_date date := (now() at time zone 'utc')::date;
  v_marked   integer;
  v_added    integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_log_date is null or p_log_date < v_utc_date - 1 or p_log_date > v_utc_date + 1 then
    raise exception 'log date must be today' using errcode = '22023';
  end if;

  insert into public.diary_default_applications (user_id, log_date)
  values (v_uid, p_log_date)
  on conflict do nothing;
  get diagnostics v_marked = row_count;
  if v_marked = 0 then
    return 0;
  end if;

  if exists (select 1 from public.food_log_entries e where e.user_id = v_uid and e.log_date = p_log_date) then
    return 0;
  end if;

  insert into public.food_log_entries (
    user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name,
    kcal, protein_g, carbs_g, fat_g, cooking_method, cooking_oil_retained_g)
  select v_uid, p_log_date, d.meal_slot_id, i.food_id, i.amount, i.portion_label, f.name,
         round(f.kcal_100 * i.amount / 100, 1) + c.added_kcal,
         round(f.protein_100 * i.amount / 100, 1),
         round(f.carbs_100 * i.amount / 100, 1),
         round(f.fat_100 * i.amount / 100, 1) + c.added_fat_g,
         m.method,
         case when m.method = 'raw' then null else c.added_fat_g end
  from public.meal_defaults d
  join public.meal_slots s on s.id = d.meal_slot_id and s.user_id = d.user_id
  join public.meal_template_items i on i.template_id = d.template_id
  join public.foods f on f.id = i.food_id
  cross join lateral (
    select case when f.base_unit = 'g' then i.cooking_method else 'raw'::public.food_cooking_method end as method
  ) m
  cross join lateral public.cooking_impact(m.method, i.amount, v_uid) c
  where d.user_id = v_uid
    and d.weekday = extract(isodow from p_log_date)::smallint
  order by s.position, i.position;

  get diagnostics v_added = row_count;
  return v_added;
end;
$$;
