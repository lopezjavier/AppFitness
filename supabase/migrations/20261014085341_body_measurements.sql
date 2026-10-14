-- T-035 (US-06): body_measurements table (onboarding weight; US-15/US-16 add more rows later).

create type public.measurement_source as enum ('manual', 'health_connect');

create table public.body_measurements (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  measured_at  timestamptz not null default now(),
  weight_kg    numeric(5, 2) not null constraint body_measurements_weight_kg_range check (weight_kg between 20 and 400),
  body_fat_pct numeric(4, 1) constraint body_measurements_body_fat_pct_range check (body_fat_pct between 2 and 75),
  source       public.measurement_source not null,
  -- Health Connect record id.
  external_id  text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint body_measurements_user_id_external_id_key unique (user_id, external_id)
);

create index body_measurements_user_id_measured_at_idx
  on public.body_measurements (user_id, measured_at desc);

create trigger body_measurements_set_updated_at
  before update on public.body_measurements
  for each row execute function public.set_updated_at();

alter table public.body_measurements enable row level security;
alter table public.body_measurements force row level security;

create policy body_measurements_select_own on public.body_measurements
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy body_measurements_insert_own on public.body_measurements
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy body_measurements_update_own on public.body_measurements
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy body_measurements_delete_own on public.body_measurements
  for delete to authenticated
  using (user_id = (select auth.uid()));

-- Supabase default privileges grant ALL (incl. TRUNCATE, not covered by RLS) to anon/authenticated.
revoke all on table public.body_measurements from anon, authenticated;
grant select, insert, update, delete on table public.body_measurements to authenticated;
