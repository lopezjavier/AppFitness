-- T-097h (US-33, ADR-56): rounding is derived (2 x smallest enabled plate), so the two stored
-- rounding columns and their save_preferences parameters go away.

alter table public.training_preferences
  drop column plate_rounding_kg,
  drop column dumbbell_rounding_kg;

drop function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric
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
  p_dumbbell_handle_kg   numeric default null
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
  public.previous_values_mode, numeric, numeric[], numeric
) from public, anon;
grant execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric
) to authenticated;
