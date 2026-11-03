-- T-204 (US-96): favourite exercises per user (catalogue and own custom exercises).

create table public.exercise_favorites (
  user_id     uuid not null references auth.users (id) on delete cascade default auth.uid(),
  exercise_id uuid not null references public.exercises (id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (user_id, exercise_id)
);

create index exercise_favorites_exercise_id_idx on public.exercise_favorites (exercise_id);

alter table public.exercise_favorites enable row level security;
alter table public.exercise_favorites force row level security;

create policy exercise_favorites_select_own on public.exercise_favorites
  for select to authenticated
  using (user_id = (select auth.uid()));

-- The exercise subquery runs under RLS: another user's custom exercise cannot be starred.
create policy exercise_favorites_insert_own on public.exercise_favorites
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.exercises e where e.id = exercise_id));

create policy exercise_favorites_delete_own on public.exercise_favorites
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.exercise_favorites from anon, authenticated;
grant select, insert, delete on table public.exercise_favorites to authenticated;
