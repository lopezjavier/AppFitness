-- T-124 (US-56, ADR-70): routine names are unique per user (case/space-insensitive, non-archived rows only) and
-- "Actualizar automáticamente" becomes one global setting on training_preferences instead of a per-routine flag.

-- 1. Unique routine name. Partial: legacy archived rows are invisible to the user and must not block names.
create unique index routines_user_name_key
  on public.routines (user_id, lower(btrim(name)))
  where not archived;

-- 2. Global auto-update setting.
alter table public.training_preferences
  add column auto_update_routine boolean not null default false;

-- 3. save_routine: same body as 20261103100000_session_rests.sql without the auto_update_routine flag
--    (an old client still sending the key is ignored).
create or replace function public.save_routine(p_routine jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id      uuid;
  v_name    text := btrim(p_routine ->> 'name', E' \t\n\r\f');
  v_track   boolean;
  v_ex      record;
  v_set     record;
  v_re_id   uuid;
  v_count   int;
  v_reps    smallint;
  v_weight  numeric;
  v_type    public.set_type;
begin
  if p_routine is null or jsonb_typeof(p_routine) <> 'object'
     or jsonb_typeof(coalesce(p_routine -> 'exercises', 'null'::jsonb)) <> 'array' then
    raise exception 'invalid routine payload' using errcode = '22023';
  end if;

  begin
    v_track := (p_routine ->> 'track_as_routine')::boolean;
  exception when invalid_text_representation then
    raise exception 'invalid routine flags' using errcode = '22023';
  end;

  -- Superset members must agree on the number of sets.
  if exists (
    select 1
    from jsonb_array_elements(p_routine -> 'exercises') as e(value)
    where e.value ->> 'superset_group' is not null
    group by e.value ->> 'superset_group'
    having count(distinct coalesce(jsonb_array_length(e.value -> 'sets'), -1)) > 1
  ) then
    raise exception 'superset members must have the same number of sets' using errcode = '22023';
  end if;

  if p_routine ->> 'id' is null then
    insert into public.routines (name, notes, track_as_routine)
    values (v_name, p_routine ->> 'notes', coalesce(v_track, true))
    returning id into v_id;
  else
    v_id := (p_routine ->> 'id')::uuid;
    update public.routines
    set name = v_name,
        notes = p_routine ->> 'notes',
        track_as_routine = coalesce(v_track, track_as_routine)
    where id = v_id;
    if not found then
      raise exception 'routine not found' using errcode = 'P0002';
    end if;
    delete from public.routine_exercises where routine_id = v_id;
  end if;

  for v_ex in
    select e.value, e.ordinality
    from jsonb_array_elements(p_routine -> 'exercises') with ordinality as e(value, ordinality)
    order by e.ordinality
  loop
    if jsonb_typeof(coalesce(v_ex.value -> 'sets', 'null'::jsonb)) <> 'array' then
      raise exception 'invalid sets' using errcode = '22023';
    end if;
    v_count := jsonb_array_length(v_ex.value -> 'sets');
    if v_count not between 1 and 10 then
      raise exception 'an exercise needs 1 to 10 sets' using errcode = '22023';
    end if;

    -- Legacy summary: first working set's reps, else the first set's.
    select (s.value ->> 'reps')::smallint into v_reps
    from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
    order by (coalesce(s.value ->> 'set_type', 'working') <> 'working'), s.ordinality
    limit 1;
    if v_reps is null or v_reps not between 1 and 50 then
      raise exception 'reps must be between 1 and 50' using errcode = '22023';
    end if;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds)
    values (v_id,
            (v_ex.value ->> 'exercise_id')::uuid,
            (v_ex.ordinality - 1)::smallint,
            (v_ex.value ->> 'superset_group')::smallint,
            v_count,
            v_reps,
            (v_ex.value ->> 'target_rpe')::numeric,
            coalesce((v_ex.value ->> 'rest_seconds')::smallint, 120::smallint))
    returning id into v_re_id;

    for v_set in
      select s.value, s.ordinality
      from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
      order by s.ordinality
    loop
      begin
        v_type := coalesce(v_set.value ->> 'set_type', 'working')::public.set_type;
        v_weight := (v_set.value ->> 'weight_kg')::numeric;
        v_reps := (v_set.value ->> 'reps')::smallint;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'invalid set' using errcode = '22023';
      end;
      if v_reps is null or v_reps not between 1 and 50 or coalesce(v_weight, 0) < 0 then
        raise exception 'invalid set' using errcode = '22023';
      end if;
      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
      values (v_re_id, v_set.ordinality::smallint, v_weight, v_reps, v_type);
    end loop;
  end loop;

  return v_id;
end;
$$;

-- 4. The per-routine flag is gone.
alter table public.routines drop column auto_update_routine;

-- 5. save_preferences gains p_auto_update_routine (null = unchanged).
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
  p_fat_pct_fried        numeric default null,
  p_auto_update_routine  boolean default null
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
     or p_dumbbell_handle_kg is not null or p_auto_update_routine is not null then
    insert into public.training_preferences (
      user_id, warm_up_included, previous_values_mode,
      bar_weight_kg, plate_kg, dumbbell_handle_kg, auto_update_routine
    )
    values (
      (select auth.uid()), coalesce(p_warm_up_included, false), coalesce(p_previous_values_mode, 'overall'),
      coalesce(p_bar_weight_kg, 20),
      coalesce(p_plate_kg, '{25,20,15,10,5,2.5,2,1.25,1,0.5}'::numeric[]),
      coalesce(p_dumbbell_handle_kg, 1),
      coalesce(p_auto_update_routine, false)
    )
    on conflict (user_id) do update
    set warm_up_included = coalesce(p_warm_up_included, public.training_preferences.warm_up_included),
        previous_values_mode = coalesce(p_previous_values_mode, public.training_preferences.previous_values_mode),
        bar_weight_kg = coalesce(p_bar_weight_kg, public.training_preferences.bar_weight_kg),
        plate_kg = coalesce(p_plate_kg, public.training_preferences.plate_kg),
        dumbbell_handle_kg = coalesce(p_dumbbell_handle_kg, public.training_preferences.dumbbell_handle_kg),
        auto_update_routine = coalesce(p_auto_update_routine, public.training_preferences.auto_update_routine);
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
  public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric, boolean
) from public, anon;
grant execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric, boolean
) to authenticated;
