-- T-078 (US-25): body circumferences (neck, chest, waist, hip, arm, thigh, calf).

create type public.circumference_site as enum ('neck', 'chest', 'waist', 'hip', 'arm', 'thigh', 'calf');

create table public.body_circumferences (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  measured_at timestamptz not null default now(),
  site        public.circumference_site not null,
  value_cm    numeric(5, 1) not null constraint body_circumferences_value_cm_range check (value_cm between 20 and 250),
  source      public.measurement_source not null default 'manual',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index body_circumferences_user_id_site_measured_at_idx
  on public.body_circumferences (user_id, site, measured_at desc);

create trigger body_circumferences_set_updated_at
  before update on public.body_circumferences
  for each row execute function public.set_updated_at();

alter table public.body_circumferences enable row level security;
alter table public.body_circumferences force row level security;

create policy body_circumferences_select_own on public.body_circumferences
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy body_circumferences_insert_own on public.body_circumferences
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy body_circumferences_update_own on public.body_circumferences
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy body_circumferences_delete_own on public.body_circumferences
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.body_circumferences from anon, authenticated;
grant select, insert, update, delete on table public.body_circumferences to authenticated;
