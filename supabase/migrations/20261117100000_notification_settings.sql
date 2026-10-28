-- T-135 (US-57): per-user silencing of in-app reminders. Reminders themselves are derived, never stored.
create type public.notification_kind as enum ('weight', 'circumference', 'weekly_check_in');

-- A missing row means the reminder kind is enabled; a row exists only once the user has toggled it.
create table public.notification_settings (
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind       public.notification_kind not null,
  enabled    boolean not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, kind)
);

create trigger notification_settings_set_updated_at
  before update on public.notification_settings
  for each row execute function public.set_updated_at();

alter table public.notification_settings enable row level security;
alter table public.notification_settings force row level security;

create policy notification_settings_select_own on public.notification_settings
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy notification_settings_insert_own on public.notification_settings
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy notification_settings_update_own on public.notification_settings
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.notification_settings from anon, authenticated;
grant select, insert, update on table public.notification_settings to authenticated;
