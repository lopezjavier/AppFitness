-- T-081 (US-27): optional weight goal on the profile.
alter table public.profiles
  add column target_weight_kg numeric(5,2)
  constraint profiles_target_weight_kg_range check (target_weight_kg between 20 and 400);

grant update (target_weight_kg) on public.profiles to authenticated;
