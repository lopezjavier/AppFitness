-- T-107 (US-50, ADR-60/63): cooking methods. A food entry and a saved comida item carry a cooking method;
-- the entry also stores the retained olive oil. The oil amounts per method live in food_preferences.

create type public.food_cooking_method as enum ('raw', 'griddle', 'air_fryer', 'fried');

alter table public.food_log_entries
  add column cooking_method public.food_cooking_method not null default 'raw',
  add column cooking_oil_retained_g numeric(7, 2) null,
  add constraint food_log_entries_cooking_oil_check check (
    cooking_oil_retained_g is null
    or (cooking_oil_retained_g >= 0 and (cooking_method <> 'raw' or cooking_oil_retained_g = 0)));

alter table public.meal_template_items
  add column cooking_method public.food_cooking_method not null default 'raw';

-- ---------------------------------------------------------------------------
-- food_preferences: olive oil grams per cooking method (one row per user, created on first save)
-- ---------------------------------------------------------------------------
create table public.food_preferences (
  user_id             uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  olive_oil_griddle_g   numeric(5, 1) not null default 5   check (olive_oil_griddle_g between 0 and 500),
  olive_oil_air_fryer_g numeric(5, 1) not null default 5   check (olive_oil_air_fryer_g between 0 and 500),
  olive_oil_fried_g     numeric(5, 1) not null default 50  check (olive_oil_fried_g between 0 and 500),
  updated_at          timestamptz not null default now()
);

create trigger food_preferences_set_updated_at
  before update on public.food_preferences
  for each row execute function public.set_updated_at();

alter table public.food_preferences enable row level security;
alter table public.food_preferences force row level security;

create policy food_preferences_select_own on public.food_preferences
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy food_preferences_insert_own on public.food_preferences
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy food_preferences_update_own on public.food_preferences
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy food_preferences_delete_own on public.food_preferences
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.food_preferences from anon, authenticated;
grant select, insert, update, delete on table public.food_preferences to authenticated;

-- ---------------------------------------------------------------------------
-- cooking_impact: oil retained by a cooking method and its kcal / fat, from the user's amounts.
-- Retention factors: griddle 0.5, air_fryer 1, fried 0.1 (ADR-60). A missing row reads as the defaults.
-- ---------------------------------------------------------------------------
create function public.cooking_impact(
  p_method public.food_cooking_method,
  p_user   uuid default auth.uid())
returns table (retained_g numeric, added_kcal numeric, added_fat_g numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with used as (
    select case p_method
             when 'griddle'   then coalesce(fp.olive_oil_griddle_g, 5) * 0.5
             when 'air_fryer' then coalesce(fp.olive_oil_air_fryer_g, 5) * 1
             when 'fried'     then coalesce(fp.olive_oil_fried_g, 50) * 0.1
             else 0
           end as g
    from (select 1) one
    left join public.food_preferences fp on fp.user_id = p_user
  )
  select round(u.g, 2), round(u.g * 9, 1), round(u.g, 1)
  from used u;
$$;

revoke execute on function public.cooking_impact(public.food_cooking_method, uuid) from public, anon;
grant execute on function public.cooking_impact(public.food_cooking_method, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- save_preferences: + three olive oil amounts (null = unchanged)
-- ---------------------------------------------------------------------------
drop function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric
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
  p_olive_oil_griddle_g   numeric default null,
  p_olive_oil_air_fryer_g numeric default null,
  p_olive_oil_fried_g     numeric default null
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

  -- Out-of-range amounts surface as 23514 from the table checks and roll the whole call back.
  if p_olive_oil_griddle_g is not null or p_olive_oil_air_fryer_g is not null
     or p_olive_oil_fried_g is not null then
    insert into public.food_preferences (
      user_id, olive_oil_griddle_g, olive_oil_air_fryer_g, olive_oil_fried_g
    )
    values (
      (select auth.uid()),
      coalesce(p_olive_oil_griddle_g, 5),
      coalesce(p_olive_oil_air_fryer_g, 5),
      coalesce(p_olive_oil_fried_g, 50)
    )
    on conflict (user_id) do update
    set olive_oil_griddle_g = coalesce(p_olive_oil_griddle_g, public.food_preferences.olive_oil_griddle_g),
        olive_oil_air_fryer_g = coalesce(p_olive_oil_air_fryer_g, public.food_preferences.olive_oil_air_fryer_g),
        olive_oil_fried_g = coalesce(p_olive_oil_fried_g, public.food_preferences.olive_oil_fried_g);
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
-- save_meal_template: items accept an optional "cooking_method" (missing or null = raw).
-- A non-raw method is only valid for foods measured in grams.
-- ---------------------------------------------------------------------------
create or replace function public.save_meal_template(
  p_template_id uuid, p_name text, p_meal_slot_id uuid, p_items jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid    uuid := auth.uid();
  v_name   text := btrim(p_name);
  v_item   jsonb;
  v_amount numeric;
  v_method public.food_cooking_method;
  v_unit   public.food_base_unit;
  v_pos    int := 0;
  v_id     uuid;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if v_name is null or char_length(v_name) not between 1 and 60 then
    raise exception 'name must have 1 to 60 characters' using errcode = '22023';
  end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'p_items must be a non-empty array' using errcode = '22023';
  end if;

  for v_item in select value from jsonb_array_elements(p_items) loop
    if jsonb_typeof(v_item) <> 'object'
       or jsonb_typeof(v_item -> 'food_id') is distinct from 'string'
       or jsonb_typeof(v_item -> 'amount') is distinct from 'number'
       or coalesce(jsonb_typeof(v_item -> 'portion_label'), 'null') not in ('null', 'string')
       or coalesce(jsonb_typeof(v_item -> 'cooking_method'), 'null') not in ('null', 'string') then
      raise exception 'invalid template item' using errcode = '22023';
    end if;
    v_amount := (v_item ->> 'amount')::numeric;
    if v_amount <= 0 or v_amount > 5000 then
      raise exception 'amount must be between 0 and 5000' using errcode = '22023';
    end if;
    begin
      perform (v_item ->> 'food_id')::uuid;
    exception when invalid_text_representation then
      raise exception 'invalid food id' using errcode = '22023';
    end;
    begin
      v_method := coalesce(v_item ->> 'cooking_method', 'raw')::public.food_cooking_method;
    exception when invalid_text_representation then
      raise exception 'invalid cooking method' using errcode = '22023';
    end;
    if v_method <> 'raw' then
      select f.base_unit into v_unit from public.foods f where f.id = (v_item ->> 'food_id')::uuid;
      if v_unit is distinct from 'g' then
        raise exception 'only foods measured in grams can be cooked' using errcode = '22023';
      end if;
    end if;
  end loop;

  if p_template_id is null then
    insert into public.meal_templates (name, meal_slot_id)
    values (v_name, p_meal_slot_id)
    returning id into v_id;
  else
    update public.meal_templates
    set name = v_name, meal_slot_id = p_meal_slot_id
    where id = p_template_id and user_id = v_uid
    returning id into v_id;
    if v_id is null then
      raise exception 'meal template not found' using errcode = 'P0002';
    end if;
    delete from public.meal_template_items where template_id = v_id;
  end if;

  for v_item in select value from jsonb_array_elements(p_items) loop
    insert into public.meal_template_items (
      template_id, food_id, amount, portion_label, position, cooking_method)
    values (v_id, (v_item ->> 'food_id')::uuid, (v_item ->> 'amount')::numeric,
            v_item ->> 'portion_label', v_pos,
            coalesce(v_item ->> 'cooking_method', 'raw')::public.food_cooking_method);
    v_pos := v_pos + 1;
  end loop;

  return v_id;
end;
$$;
