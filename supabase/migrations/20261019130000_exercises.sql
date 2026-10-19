-- T-042 (US-09): exercises table (catalog rows + private custom exercises) and the training enums (ADR-30, ADR-38).
-- Catalog rows hold only key + muscle group + equipment; Spanish names, instructions and images live in the
-- client bundle (ADR-39). The catalog rows themselves arrive in the next migration.

create type public.muscle_group as enum ('chest', 'back', 'shoulders', 'biceps', 'triceps', 'forearms',
                                          'quads', 'hamstrings', 'glutes', 'calves', 'core', 'full_body');
create type public.equipment as enum ('barbell', 'dumbbell', 'machine', 'cable', 'bodyweight', 'kettlebell',
                                      'band', 'other');
create type public.exercise_metric_type as enum ('weight_reps', 'reps_only', 'duration', 'weight_duration',
                                                 'distance_duration', 'bodyweight', 'assisted', 'weighted',
                                                 'steps_duration', 'floors_duration');

create table public.exercises (
  id                uuid primary key default gen_random_uuid(),
  -- Null for catalog rows (the name comes from the client bundle); required for custom exercises.
  name              text,
  -- Slug linking a catalog row to the client bundle; null for custom exercises.
  key               text,
  muscle_group      public.muscle_group not null,
  secondary_muscles public.muscle_group[] not null default '{}',
  equipment         public.equipment not null,
  metric_type       public.exercise_metric_type not null default 'weight_reps',
  is_custom         boolean not null default false,
  created_by        uuid references auth.users (id) on delete cascade default auth.uid(),
  created_at        timestamptz not null default now(),
  constraint exercises_consistency check (
    (is_custom
      and created_by is not null
      and key is null
      and name is not null
      and char_length(btrim(name)) between 1 and 100)
    or
    (not is_custom
      and created_by is null
      and key is not null
      and name is null)
  )
);

create unique index exercises_key_key on public.exercises (key) where key is not null;
create index exercises_created_by_idx on public.exercises (created_by);

alter table public.exercises enable row level security;
alter table public.exercises force row level security;

create policy exercises_select on public.exercises
  for select to authenticated
  using (not is_custom or created_by = (select auth.uid()));

create policy exercises_insert_own_custom on public.exercises
  for insert to authenticated
  with check (is_custom and created_by = (select auth.uid()));

create policy exercises_update_own_custom on public.exercises
  for update to authenticated
  using (is_custom and created_by = (select auth.uid()))
  with check (is_custom and created_by = (select auth.uid()));

create policy exercises_delete_own_custom on public.exercises
  for delete to authenticated
  using (is_custom and created_by = (select auth.uid()));

-- Supabase default privileges grant ALL (incl. TRUNCATE, not covered by RLS) to anon/authenticated.
revoke all on table public.exercises from anon, authenticated;
grant select, insert, update, delete on table public.exercises to authenticated;
