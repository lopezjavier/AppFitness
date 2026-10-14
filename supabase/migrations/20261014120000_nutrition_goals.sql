-- T-036 (US-07): nutrition_goals table (energy strategy, daily calorie target and macro split).

create type public.goal_strategy as enum ('deficit', 'maintenance', 'surplus');

create table public.nutrition_goals (
  user_id            uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  strategy           public.goal_strategy not null default 'maintenance',
  -- Union of the per-strategy ranges (planning/04 section 1.4); the app enforces the per-strategy one.
  calorie_adjustment int not null default 0
    constraint nutrition_goals_calorie_adjustment_range check (calorie_adjustment between -1000 and 750),
  -- Stored snapshot of the daily target (may sit at the safety floor above TDEE + adjustment).
  target_kcal        int not null constraint nutrition_goals_target_kcal_positive check (target_kcal > 0),
  protein_pct        smallint not null default 30
    constraint nutrition_goals_protein_pct_range check (protein_pct between 5 and 90),
  carbs_pct          smallint not null default 45
    constraint nutrition_goals_carbs_pct_range check (carbs_pct between 5 and 90),
  fat_pct            smallint not null default 25
    constraint nutrition_goals_fat_pct_range check (fat_pct between 5 and 90),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint nutrition_goals_pct_sum check (protein_pct + carbs_pct + fat_pct = 100)
);

create trigger nutrition_goals_set_updated_at
  before update on public.nutrition_goals
  for each row execute function public.set_updated_at();

alter table public.nutrition_goals enable row level security;
alter table public.nutrition_goals force row level security;

create policy nutrition_goals_select_own on public.nutrition_goals
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy nutrition_goals_insert_own on public.nutrition_goals
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy nutrition_goals_update_own on public.nutrition_goals
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy nutrition_goals_delete_own on public.nutrition_goals
  for delete to authenticated
  using (user_id = (select auth.uid()));

-- Supabase default privileges grant ALL (incl. TRUNCATE, not covered by RLS) to anon/authenticated.
revoke all on table public.nutrition_goals from anon, authenticated;
grant select, insert, update, delete on table public.nutrition_goals to authenticated;
