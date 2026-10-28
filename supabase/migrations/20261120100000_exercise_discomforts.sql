-- T-147 (US-67): discomfort log per exercise. Rows are never deleted (history for E13); only resolved_at can change.
-- The 7-day open window is evaluated by the client from logged_at.
create table public.exercise_discomforts (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  exercise_id uuid not null references public.exercises (id) on delete cascade,
  logged_at   timestamptz not null default now(),
  note        text,
  severe      boolean not null default false,
  resolved_at timestamptz,
  constraint exercise_discomforts_note_length check (char_length(note) between 1 and 200),
  constraint exercise_discomforts_resolved_after_logged check (resolved_at is null or resolved_at >= logged_at)
);

create index exercise_discomforts_user_exercise_logged_idx
  on public.exercise_discomforts (user_id, exercise_id, logged_at desc);

alter table public.exercise_discomforts enable row level security;
alter table public.exercise_discomforts force row level security;

create policy exercise_discomforts_select_own on public.exercise_discomforts
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy exercise_discomforts_insert_own on public.exercise_discomforts
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy exercise_discomforts_update_own on public.exercise_discomforts
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.exercise_discomforts from anon, authenticated;
grant select, insert on table public.exercise_discomforts to authenticated;
grant update (resolved_at) on table public.exercise_discomforts to authenticated;
