-- T-041 (US-06/07, ADR-37): atomic save for the Preferencias screen.
-- Replaces save_profile_settings: besides the biometrics and the goals it also updates the display name and,
-- optionally, writes today's manual weight (replace the latest manual row of the caller's local day, or insert
-- one), all in one transaction so "Guardar" never leaves the target without its weight or the reverse.
-- security invoker: RLS and the column grants apply exactly as for direct writes.

create or replace function public.save_preferences(
  p_display_name       text,
  p_sex                public.sex,
  p_birth_date         date,
  p_height_cm          numeric,
  p_activity_level     public.activity_level,
  p_strategy           public.goal_strategy,
  p_calorie_adjustment int,
  p_target_kcal        int,
  p_protein_pct        smallint,
  p_carbs_pct          smallint,
  p_fat_pct            smallint,
  p_weight_kg          numeric default null,
  p_measured_at        timestamptz default null,
  p_day_start          timestamptz default null,
  p_day_end            timestamptz default null
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
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz
) from public, anon;
grant execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz
) to authenticated;

-- Its only caller (the Profile screen) moved to save_preferences.
drop function public.save_profile_settings(
  public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint
);
