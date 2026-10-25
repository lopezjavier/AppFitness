-- T-117 (US-55, ADR-68): comidas added to a meal show in the Diario as one collapsible group.
-- Snapshot grouping: food_log_entries carry an optional (group_id, group_name); there is no link to meal_templates
-- and no backfill (old entries stay ungrouped). Totals, food_diary_summary and goals still sum the flat entries.
-- Any future code that clones entries (copy day / copy meal) must give the clones a new group_id per source group.

-- ---------------------------------------------------------------------------
-- Columns and checks
-- ---------------------------------------------------------------------------
alter table public.food_log_entries
  add column group_id uuid,
  add column group_name text;

alter table public.food_log_entries
  add constraint food_log_entries_group_pair_check check ((group_id is null) = (group_name is null)),
  add constraint food_log_entries_group_name_check
    check (group_name is null or char_length(btrim(group_name)) between 1 and 60);

-- ---------------------------------------------------------------------------
-- A group never spans two meals: moving an entry to another meal ungroups it
-- ---------------------------------------------------------------------------
create function public.food_log_entries_clear_group_on_move()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if new.meal_slot_id is distinct from old.meal_slot_id then
    new.group_id := null;
    new.group_name := null;
  end if;
  return new;
end;
$$;

create trigger food_log_entries_clear_group_on_move
  before update on public.food_log_entries
  for each row execute function public.food_log_entries_clear_group_on_move();

-- ---------------------------------------------------------------------------
-- apply_meal_defaults: same behavior as 20261109100000; every default (one per meal and weekday) is one group
-- ---------------------------------------------------------------------------
create or replace function public.apply_meal_defaults(p_log_date date)
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

  with grp as materialized (
    select d.meal_slot_id, d.template_id, t.name, gen_random_uuid() as gid
    from public.meal_defaults d
    join public.meal_templates t on t.id = d.template_id and t.user_id = d.user_id
    where d.user_id = v_uid
      and d.weekday = extract(isodow from p_log_date)::smallint
  )
  insert into public.food_log_entries (
    user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name,
    kcal, protein_g, carbs_g, fat_g, cooking_method, cooking_oil_retained_g, group_id, group_name)
  select v_uid, p_log_date, d.meal_slot_id, i.food_id, i.amount, i.portion_label, f.name,
         round(f.kcal_100 * i.amount / 100, 1) + c.added_kcal,
         round(f.protein_100 * i.amount / 100, 1),
         round(f.carbs_100 * i.amount / 100, 1),
         round(f.fat_100 * i.amount / 100, 1) + c.added_fat_g,
         m.method,
         case when m.method = 'raw' then null else c.added_fat_g end,
         g.gid,
         btrim(g.name)
  from public.meal_defaults d
  join grp g on g.meal_slot_id = d.meal_slot_id and g.template_id = d.template_id
  join public.meal_slots s on s.id = d.meal_slot_id and s.user_id = d.user_id
  join public.meal_template_items i on i.template_id = d.template_id
  join public.foods f on f.id = i.food_id
  cross join lateral (
    select case when f.base_unit = 'g' then i.cooking_method else 'raw'::public.food_cooking_method end as method
  ) m
  cross join lateral public.cooking_impact(m.method, i.amount, v_uid) c
  where d.user_id = v_uid
    and d.weekday = extract(isodow from p_log_date)::smallint
  order by s.position, i.position;

  get diagnostics v_added = row_count;
  return v_added;
end;
$$;

revoke execute on function public.apply_meal_defaults(date) from public, anon;
grant execute on function public.apply_meal_defaults(date) to authenticated;

-- ---------------------------------------------------------------------------
-- save_meal_as_comida: saves a new comida and groups the meal's entries read by the sheet, atomically
-- ---------------------------------------------------------------------------
create function public.save_meal_as_comida(
  p_log_date     date,
  p_meal_slot_id uuid,
  p_name         text,
  p_items        jsonb,
  p_entry_ids    uuid[])
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_group_id uuid;
  v_id       uuid;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_entry_ids is null or cardinality(p_entry_ids) = 0 then
    raise exception 'p_entry_ids must be a non-empty array' using errcode = '22023';
  end if;

  v_id := public.save_meal_template(null, p_name, p_meal_slot_id, p_items);

  v_group_id := gen_random_uuid();
  update public.food_log_entries
  set group_id = v_group_id, group_name = btrim(p_name)
  where user_id = v_uid
    and log_date = p_log_date
    and meal_slot_id = p_meal_slot_id
    and id = any (p_entry_ids);

  return v_id;
end;
$$;

revoke execute on function public.save_meal_as_comida(date, uuid, text, jsonb, uuid[]) from public, anon;
grant execute on function public.save_meal_as_comida(date, uuid, text, jsonb, uuid[]) to authenticated;
