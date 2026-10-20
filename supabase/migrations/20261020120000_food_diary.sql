-- T-055 (US-16, US-22, US-23): nutrition catalog (Open Food Facts cache + personal foods), named portions,
-- configurable meal slots and the food diary entries. `off` rows are only written by Edge Functions with the
-- secret key; clients only write `user` foods they own.

create type public.meal_type as enum ('breakfast', 'lunch', 'snack', 'dinner');
create type public.food_source as enum ('off', 'user');
create type public.food_base_unit as enum ('g', 'ml');

-- unaccent() is only STABLE, so a generated column cannot use it directly. This wrapper pins the dictionary
-- and the search_path, which makes it safe to declare IMMUTABLE.
create function public.immutable_unaccent(p_text text) returns text
  language sql
  immutable
  parallel safe
  strict
  set search_path = ''
as $$ select extensions.unaccent('extensions.unaccent'::regdictionary, p_text) $$;

-- ---------------------------------------------------------------------------
-- foods
-- ---------------------------------------------------------------------------
create table public.foods (
  id             uuid primary key default gen_random_uuid(),
  source         public.food_source not null,
  barcode        text,
  off_id         text,
  name           text not null check (char_length(btrim(name)) between 1 and 200),
  brand          text check (brand is null or char_length(brand) <= 200),
  base_unit      public.food_base_unit not null default 'g',
  kcal_100       numeric(6, 2) not null check (kcal_100 >= 0),
  protein_100    numeric(6, 2) not null default 0 check (protein_100 >= 0),
  carbs_100      numeric(6, 2) not null default 0 check (carbs_100 >= 0),
  fat_100        numeric(6, 2) not null default 0 check (fat_100 >= 0),
  serving_label  text check (serving_label is null or char_length(btrim(serving_label)) between 1 and 100),
  serving_amount numeric(7, 1) check (serving_amount is null or serving_amount > 0),
  image_url      text check (image_url is null or char_length(image_url) <= 2048),
  created_by     uuid references auth.users (id) on delete cascade default auth.uid(),
  search_vector  tsvector generated always as (
    to_tsvector('spanish'::regconfig, public.immutable_unaccent(name || ' ' || coalesce(brand, '')))
  ) stored,
  fetched_at     timestamptz not null default now(),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint foods_source_owner check ((source = 'user') = (created_by is not null))
);

create unique index foods_off_barcode_key on public.foods (barcode) where source = 'off';
create index foods_barcode_idx on public.foods (barcode) where barcode is not null;
create index foods_search_vector_idx on public.foods using gin (search_vector);
create index foods_created_by_idx on public.foods (created_by);

create trigger foods_set_updated_at
  before update on public.foods
  for each row execute function public.set_updated_at();

alter table public.foods enable row level security;
alter table public.foods force row level security;

create policy foods_select on public.foods
  for select to authenticated
  using (source = 'off' or created_by = (select auth.uid()));

create policy foods_insert_own on public.foods
  for insert to authenticated
  with check (source = 'user' and created_by = (select auth.uid()));

create policy foods_update_own on public.foods
  for update to authenticated
  using (source = 'user' and created_by = (select auth.uid()))
  with check (source = 'user' and created_by = (select auth.uid()));

create policy foods_delete_own on public.foods
  for delete to authenticated
  using (source = 'user' and created_by = (select auth.uid()));

revoke all on table public.foods from anon, authenticated;
grant select, insert, update, delete on table public.foods to authenticated;
grant all on table public.foods to service_role;

-- ---------------------------------------------------------------------------
-- food_portions
-- ---------------------------------------------------------------------------
create table public.food_portions (
  id       uuid primary key default gen_random_uuid(),
  food_id  uuid not null references public.foods (id) on delete cascade,
  label    text not null check (char_length(btrim(label)) between 1 and 100),
  amount   numeric(7, 1) not null check (amount > 0),
  position smallint not null default 0,
  unique (food_id, position)
);

alter table public.food_portions enable row level security;
alter table public.food_portions force row level security;

-- The subqueries on foods run under the caller's RLS, so a portion is readable only with its readable parent.
create policy food_portions_select on public.food_portions
  for select to authenticated
  using (exists (select 1 from public.foods f where f.id = food_id));

create policy food_portions_insert_own on public.food_portions
  for insert to authenticated
  with check (exists (
    select 1 from public.foods f
    where f.id = food_id and f.source = 'user' and f.created_by = (select auth.uid())));

create policy food_portions_update_own on public.food_portions
  for update to authenticated
  using (exists (
    select 1 from public.foods f
    where f.id = food_id and f.source = 'user' and f.created_by = (select auth.uid())))
  with check (exists (
    select 1 from public.foods f
    where f.id = food_id and f.source = 'user' and f.created_by = (select auth.uid())));

create policy food_portions_delete_own on public.food_portions
  for delete to authenticated
  using (exists (
    select 1 from public.foods f
    where f.id = food_id and f.source = 'user' and f.created_by = (select auth.uid())));

revoke all on table public.food_portions from anon, authenticated;
grant select, insert, update, delete on table public.food_portions to authenticated;
grant all on table public.food_portions to service_role;

-- ---------------------------------------------------------------------------
-- meal_slots (a meal is disabled, never deleted by the client)
-- ---------------------------------------------------------------------------
create table public.meal_slots (
  id       uuid primary key default gen_random_uuid(),
  user_id  uuid not null references auth.users (id) on delete cascade default auth.uid(),
  code     public.meal_type,
  label    text not null check (char_length(btrim(label)) between 1 and 50),
  position smallint not null,
  enabled  boolean not null default true,
  unique (user_id, position),
  -- Target of the composite FK from food_log_entries: an entry can never point at another user's slot.
  unique (id, user_id)
);

alter table public.meal_slots enable row level security;
alter table public.meal_slots force row level security;

create policy meal_slots_select_own on public.meal_slots
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy meal_slots_insert_own on public.meal_slots
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy meal_slots_update_own on public.meal_slots
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.meal_slots from anon, authenticated;
grant select, insert, update on table public.meal_slots to authenticated;

-- ---------------------------------------------------------------------------
-- food_log_entries
-- ---------------------------------------------------------------------------
-- meal_slot_id and food_id use `on delete no action` (checked at statement end), not `restrict` (checked
-- immediately): deleting an auth user cascades to entries, slots and personal foods in one statement (ADR-29),
-- and restrict could fire before the entries are gone.
create table public.food_log_entries (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users (id) on delete cascade default auth.uid(),
  log_date     date not null,
  meal_slot_id uuid not null,
  food_id      uuid not null references public.foods (id) on delete no action,
  amount       numeric(7, 1) not null check (amount > 0 and amount <= 5000),
  portion_label text check (portion_label is null or char_length(btrim(portion_label)) between 1 and 100),
  food_name    text not null check (char_length(btrim(food_name)) between 1 and 200),
  kcal         numeric(7, 1) not null check (kcal >= 0),
  protein_g    numeric(7, 1) not null check (protein_g >= 0),
  carbs_g      numeric(7, 1) not null check (carbs_g >= 0),
  fat_g        numeric(7, 1) not null check (fat_g >= 0),
  created_at   timestamptz not null default now(),
  foreign key (meal_slot_id, user_id) references public.meal_slots (id, user_id) on delete no action
);

create index food_log_entries_user_date_idx on public.food_log_entries (user_id, log_date);
create index food_log_entries_food_id_idx on public.food_log_entries (food_id);
create index food_log_entries_meal_slot_idx on public.food_log_entries (meal_slot_id, user_id);

alter table public.food_log_entries enable row level security;
alter table public.food_log_entries force row level security;

create policy food_log_entries_select_own on public.food_log_entries
  for select to authenticated
  using (user_id = (select auth.uid()));

-- The food subquery runs under RLS: an entry cannot reference another user's personal food.
create policy food_log_entries_insert_own on public.food_log_entries
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.foods f where f.id = food_id));

create policy food_log_entries_update_own on public.food_log_entries
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.foods f where f.id = food_id));

create policy food_log_entries_delete_own on public.food_log_entries
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.food_log_entries from anon, authenticated;
grant select, insert, update, delete on table public.food_log_entries to authenticated;

-- ---------------------------------------------------------------------------
-- Default meals (0-based positions; clients only order by position)
-- ---------------------------------------------------------------------------
create function public.seed_default_meal_slots(p_user uuid) returns void
  language sql
  security definer
  set search_path = ''
as $$
  insert into public.meal_slots (user_id, code, label, position)
  values
    (p_user, 'breakfast', 'Desayuno', 0),
    (p_user, 'lunch', 'Comida', 1),
    (p_user, 'snack', 'Merienda', 2),
    (p_user, 'dinner', 'Cena', 3)
  on conflict (user_id, position) do nothing
$$;

revoke execute on function public.seed_default_meal_slots(uuid) from public, anon, authenticated;

create function public.seed_meal_slots_on_onboarding() returns trigger
  language plpgsql
  security definer
  set search_path = ''
as $$
begin
  if new.onboarding_completed and (tg_op = 'INSERT' or not old.onboarding_completed) then
    perform public.seed_default_meal_slots(new.id);
  end if;
  return null;
end;
$$;

revoke execute on function public.seed_meal_slots_on_onboarding() from public, anon, authenticated;

create trigger profiles_seed_meal_slots
  after insert or update of onboarding_completed on public.profiles
  for each row execute function public.seed_meal_slots_on_onboarding();

-- Backfill users who already finished onboarding.
select public.seed_default_meal_slots(id) from public.profiles where onboarding_completed;
