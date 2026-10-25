-- T-109 (US-51, ADR-61): default comidas per meal and weekday, applied on the first open of a new day.

-- Target of the composite FK from meal_defaults (id is already unique; this makes (id, user_id) referenceable).
alter table public.meal_templates add constraint meal_templates_id_user_id_key unique (id, user_id);

-- ---------------------------------------------------------------------------
-- meal_defaults: one default comida per meal and ISO weekday
-- ---------------------------------------------------------------------------
create table public.meal_defaults (
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  meal_slot_id uuid not null,
  template_id  uuid not null,
  weekday      smallint not null check (weekday between 1 and 7),
  created_at   timestamptz not null default now(),
  primary key (user_id, meal_slot_id, weekday),
  foreign key (meal_slot_id, user_id) references public.meal_slots (id, user_id) on delete cascade,
  foreign key (template_id, user_id) references public.meal_templates (id, user_id) on delete cascade
);

create index meal_defaults_template_idx on public.meal_defaults (template_id, user_id);

alter table public.meal_defaults enable row level security;
alter table public.meal_defaults force row level security;

create policy meal_defaults_select_own on public.meal_defaults
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy meal_defaults_insert_own on public.meal_defaults
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy meal_defaults_update_own on public.meal_defaults
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy meal_defaults_delete_own on public.meal_defaults
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.meal_defaults from anon, authenticated;
grant select, insert, update, delete on table public.meal_defaults to authenticated;

-- ---------------------------------------------------------------------------
-- diary_default_applications: idempotency marker of apply_meal_defaults
-- ---------------------------------------------------------------------------
create table public.diary_default_applications (
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  log_date   date not null,
  applied_at timestamptz not null default now(),
  primary key (user_id, log_date)
);

alter table public.diary_default_applications enable row level security;
alter table public.diary_default_applications force row level security;

create policy diary_default_applications_select_own on public.diary_default_applications
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy diary_default_applications_insert_own on public.diary_default_applications
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy diary_default_applications_update_own on public.diary_default_applications
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy diary_default_applications_delete_own on public.diary_default_applications
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.diary_default_applications from anon, authenticated;
grant select, insert, update, delete on table public.diary_default_applications to authenticated;

-- ---------------------------------------------------------------------------
-- save_meal_defaults: atomically replaces all the caller's defaults.
-- p_defaults = [{"meal_slot_id": uuid, "template_id": uuid, "weekdays": [1,3,5]}]; [] clears them.
-- ---------------------------------------------------------------------------
create function public.save_meal_defaults(p_defaults jsonb)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_el       jsonb;
  v_day      jsonb;
  v_slot     uuid;
  v_template uuid;
  v_seen     text[] := array[]::text[];
  v_key      text;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_defaults is null or jsonb_typeof(p_defaults) <> 'array' then
    raise exception 'p_defaults must be an array' using errcode = '22023';
  end if;

  -- Validate everything before writing anything.
  for v_el in select value from jsonb_array_elements(p_defaults) loop
    if jsonb_typeof(v_el) <> 'object'
       or jsonb_typeof(v_el -> 'meal_slot_id') is distinct from 'string'
       or jsonb_typeof(v_el -> 'template_id') is distinct from 'string'
       or jsonb_typeof(v_el -> 'weekdays') is distinct from 'array' then
      raise exception 'invalid default' using errcode = '22023';
    end if;
    begin
      v_slot := (v_el ->> 'meal_slot_id')::uuid;
      v_template := (v_el ->> 'template_id')::uuid;
    exception when invalid_text_representation then
      raise exception 'invalid id' using errcode = '22023';
    end;

    for v_day in select value from jsonb_array_elements(v_el -> 'weekdays') loop
      if jsonb_typeof(v_day) <> 'number'
         or (v_day #>> '{}') !~ '^[1-7]$' then
        raise exception 'weekday must be an integer from 1 to 7' using errcode = '22023';
      end if;
      v_key := v_slot::text || ':' || (v_day #>> '{}');
      if v_key = any (v_seen) then
        raise exception 'duplicate meal and weekday' using errcode = '22023';
      end if;
      v_seen := v_seen || v_key;
    end loop;

    if not exists (select 1 from public.meal_slots s where s.id = v_slot and s.user_id = v_uid) then
      raise exception 'meal not found' using errcode = 'P0002';
    end if;
    if not exists (select 1 from public.meal_templates t where t.id = v_template and t.user_id = v_uid) then
      raise exception 'meal template not found' using errcode = 'P0002';
    end if;
    if not exists (select 1 from public.meal_template_items i where i.template_id = v_template) then
      raise exception 'meal template has no items' using errcode = '23514';
    end if;
  end loop;

  delete from public.meal_defaults where user_id = v_uid;

  insert into public.meal_defaults (user_id, meal_slot_id, template_id, weekday)
  select v_uid, (e.value ->> 'meal_slot_id')::uuid, (e.value ->> 'template_id')::uuid,
         (d.value #>> '{}')::smallint
  from jsonb_array_elements(p_defaults) e,
       jsonb_array_elements(e.value -> 'weekdays') d;
end;
$$;

revoke execute on function public.save_meal_defaults(jsonb) from public, anon;
grant execute on function public.save_meal_defaults(jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- apply_meal_defaults: fills a day with the defaults of its ISO weekday, once. Returns the entries added.
-- Entry snapshots mirror the client (BuildCookedEntries / CookingImpact): nutrition = food_100 * amount / 100
-- rounded to tenths, plus the oil of the cooking method (see cooking_impact); drinks (ml) are always raw.
-- Dates more than one day from UTC today are refused: the server does not know the user's timezone, and every
-- local date from UTC-12 to UTC+14 falls inside the window, so a legitimate "today" is never refused.
-- ---------------------------------------------------------------------------
create function public.apply_meal_defaults(p_log_date date)
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_utc_date date := (now() at time zone 'utc')::date;
  v_marked   integer;
  v_added    integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_log_date is null or p_log_date < v_utc_date - 1 or p_log_date > v_utc_date + 1 then
    raise exception 'log date must be today' using errcode = '22023';
  end if;

  insert into public.diary_default_applications (user_id, log_date)
  values (v_uid, p_log_date)
  on conflict do nothing;
  get diagnostics v_marked = row_count;
  if v_marked = 0 then
    return 0;
  end if;

  if exists (select 1 from public.food_log_entries e where e.user_id = v_uid and e.log_date = p_log_date) then
    return 0;
  end if;

  insert into public.food_log_entries (
    user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name,
    kcal, protein_g, carbs_g, fat_g, cooking_method, cooking_oil_retained_g)
  select v_uid, p_log_date, d.meal_slot_id, i.food_id, i.amount, i.portion_label, f.name,
         round(f.kcal_100 * i.amount / 100, 1) + c.added_kcal,
         round(f.protein_100 * i.amount / 100, 1),
         round(f.carbs_100 * i.amount / 100, 1),
         round(f.fat_100 * i.amount / 100, 1) + c.added_fat_g,
         m.method,
         case when m.method = 'raw' then null else c.retained_g end
  from public.meal_defaults d
  join public.meal_slots s on s.id = d.meal_slot_id and s.user_id = d.user_id
  join public.meal_template_items i on i.template_id = d.template_id
  join public.foods f on f.id = i.food_id
  cross join lateral (
    select case when f.base_unit = 'g' then i.cooking_method else 'raw'::public.food_cooking_method end as method
  ) m
  cross join lateral public.cooking_impact(m.method, v_uid) c
  where d.user_id = v_uid
    and d.weekday = extract(isodow from p_log_date)::smallint
  order by s.position, i.position;

  get diagnostics v_added = row_count;
  return v_added;
end;
$$;

revoke execute on function public.apply_meal_defaults(date) from public, anon;
grant execute on function public.apply_meal_defaults(date) to authenticated;
