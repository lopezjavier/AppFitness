-- T-061 (US-21, ADR-42): saved meals ("comidas") stored as meal templates, and the atomic save RPC.

create table public.meal_templates (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users (id) on delete cascade default auth.uid(),
  name         text not null check (char_length(btrim(name)) between 1 and 60),
  meal_slot_id uuid null,
  created_at   timestamptz not null default now(),
  -- The suggested meal never blocks deleting a meal (ADR-41): it is just cleared.
  foreign key (meal_slot_id, user_id)
    references public.meal_slots (id, user_id) on delete set null (meal_slot_id)
);

create unique index meal_templates_user_name_key on public.meal_templates (user_id, lower(btrim(name)));

create table public.meal_template_items (
  id             uuid primary key default gen_random_uuid(),
  template_id    uuid not null references public.meal_templates (id) on delete cascade,
  -- Deferred to commit: deleting a user cascades to their personal foods before their templates' items.
  food_id        uuid not null references public.foods (id) on delete no action deferrable initially deferred,
  amount         numeric(7, 1) not null check (amount > 0 and amount <= 5000),
  portion_label  text null,
  position       smallint not null,
  unique (template_id, position)
);

create index meal_template_items_food_id_idx on public.meal_template_items (food_id);

alter table public.meal_templates enable row level security;
alter table public.meal_templates force row level security;
alter table public.meal_template_items enable row level security;
alter table public.meal_template_items force row level security;

create policy meal_templates_select_own on public.meal_templates
  for select to authenticated using (user_id = (select auth.uid()));
create policy meal_templates_insert_own on public.meal_templates
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy meal_templates_update_own on public.meal_templates
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
create policy meal_templates_delete_own on public.meal_templates
  for delete to authenticated using (user_id = (select auth.uid()));

create policy meal_template_items_select_own on public.meal_template_items
  for select to authenticated
  using (exists (select 1 from public.meal_templates t
                 where t.id = template_id and t.user_id = (select auth.uid())));
-- The foods subquery runs under RLS: another user's personal food cannot be referenced.
create policy meal_template_items_insert_own on public.meal_template_items
  for insert to authenticated
  with check (
    exists (select 1 from public.meal_templates t
            where t.id = template_id and t.user_id = (select auth.uid()))
    and exists (select 1 from public.foods f where f.id = food_id));
create policy meal_template_items_update_own on public.meal_template_items
  for update to authenticated
  using (exists (select 1 from public.meal_templates t
                 where t.id = template_id and t.user_id = (select auth.uid())))
  with check (
    exists (select 1 from public.meal_templates t
            where t.id = template_id and t.user_id = (select auth.uid()))
    and exists (select 1 from public.foods f where f.id = food_id));
create policy meal_template_items_delete_own on public.meal_template_items
  for delete to authenticated
  using (exists (select 1 from public.meal_templates t
                 where t.id = template_id and t.user_id = (select auth.uid())));

revoke all on table public.meal_templates from anon, authenticated;
revoke all on table public.meal_template_items from anon, authenticated;
grant select, insert, update, delete on table public.meal_templates to authenticated;
grant select, insert, update, delete on table public.meal_template_items to authenticated;

-- Creates (p_template_id null) or overwrites a comida atomically.
-- p_items = array in final order of {"food_id": uuid, "amount": number, "portion_label": text|null}.
-- security invoker: RLS applies (another user's template is invisible, another user's personal food is rejected).
create function public.save_meal_template(
  p_template_id uuid, p_name text, p_meal_slot_id uuid, p_items jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid    uuid := auth.uid();
  v_name   text := btrim(p_name);
  v_item   jsonb;
  v_amount numeric;
  v_pos    int := 0;
  v_id     uuid;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if v_name is null or char_length(v_name) not between 1 and 60 then
    raise exception 'name must have 1 to 60 characters' using errcode = '22023';
  end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'p_items must be a non-empty array' using errcode = '22023';
  end if;

  for v_item in select value from jsonb_array_elements(p_items) loop
    if jsonb_typeof(v_item) <> 'object'
       or jsonb_typeof(v_item -> 'food_id') is distinct from 'string'
       or jsonb_typeof(v_item -> 'amount') is distinct from 'number'
       or coalesce(jsonb_typeof(v_item -> 'portion_label'), 'null') not in ('null', 'string') then
      raise exception 'invalid template item' using errcode = '22023';
    end if;
    v_amount := (v_item ->> 'amount')::numeric;
    if v_amount <= 0 or v_amount > 5000 then
      raise exception 'amount must be between 0 and 5000' using errcode = '22023';
    end if;
    begin
      perform (v_item ->> 'food_id')::uuid;
    exception when invalid_text_representation then
      raise exception 'invalid food id' using errcode = '22023';
    end;
  end loop;

  if p_template_id is null then
    insert into public.meal_templates (name, meal_slot_id)
    values (v_name, p_meal_slot_id)
    returning id into v_id;
  else
    update public.meal_templates
    set name = v_name, meal_slot_id = p_meal_slot_id
    where id = p_template_id and user_id = v_uid
    returning id into v_id;
    if v_id is null then
      raise exception 'meal template not found' using errcode = 'P0002';
    end if;
    delete from public.meal_template_items where template_id = v_id;
  end if;

  for v_item in select value from jsonb_array_elements(p_items) loop
    insert into public.meal_template_items (template_id, food_id, amount, portion_label, position)
    values (v_id, (v_item ->> 'food_id')::uuid, (v_item ->> 'amount')::numeric,
            v_item ->> 'portion_label', v_pos);
    v_pos := v_pos + 1;
  end loop;

  return v_id;
end;
$$;

revoke execute on function public.save_meal_template(uuid, text, uuid, jsonb) from public, anon;
grant execute on function public.save_meal_template(uuid, text, uuid, jsonb) to authenticated;
