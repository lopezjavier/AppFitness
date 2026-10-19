-- T-037 (US-06/07/08, ADR-24): atomic save for the Profile screen.
-- Updates the caller's biometric fields and upserts their nutrition goals in one
-- transaction, so "Guardar" never leaves the profile and the daily target inconsistent.
-- security invoker: RLS and the column grants apply exactly as for direct writes.

create or replace function public.save_profile_settings(
  p_sex                public.sex,
  p_birth_date         date,
  p_height_cm          numeric,
  p_activity_level     public.activity_level,
  p_strategy           public.goal_strategy,
  p_calorie_adjustment int,
  p_target_kcal        int,
  p_protein_pct        smallint,
  p_carbs_pct          smallint,
  p_fat_pct            smallint
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.profiles
  set sex = p_sex,
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
end;
$$;

revoke execute on function public.save_profile_settings(
  public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint
) from public, anon;
grant execute on function public.save_profile_settings(
  public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint
) to authenticated;
