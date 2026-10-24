-- T-104 (US-52, ADR-62): the base unit of a manual food is editable while nothing references it.
-- Replaces update_personal_food with a version that takes p_base_unit (null = keep) and adds personal_food_in_use.
--
-- Concurrency: the FOR UPDATE lock on the food row conflicts with the FOR KEY SHARE that a food_log_entries insert
-- takes through its FK, so an entry insert and a unit change serialise. meal_template_items.food_id is a deferred FK
-- (key share is taken at commit), so an uncommitted template insert is invisible to the check; accepted for 1-5 users.

drop function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb);

create function public.update_personal_food(
  p_food_id         uuid,
  p_name            text,
  p_brand           text,
  p_kcal            numeric,
  p_protein         numeric,
  p_carbs           numeric,
  p_fat             numeric,
  p_portions        jsonb,
  p_micronutrients  jsonb default null,
  p_base_unit       text default null
) returns void
  language plpgsql
  security invoker
  set search_path = ''
as $$
declare
  v_user     uuid := auth.uid();
  v_locked   uuid;
  v_current  public.food_base_unit;
  v_unit     public.food_base_unit;
  v_portions jsonb := coalesce(p_portions, '[]'::jsonb);
  v_micro    jsonb := p_micronutrients;
  v_portion  jsonb;
  v_label    text;
  v_amount   numeric;
  v_pos      int := 0;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_name is null or char_length(btrim(p_name)) not between 1 and 200 then
    raise exception 'invalid name' using errcode = '22023';
  end if;
  if p_brand is not null and char_length(p_brand) > 200 then
    raise exception 'invalid brand' using errcode = '22023';
  end if;
  if p_kcal is null or p_kcal < 0
     or p_protein is null or p_protein < 0
     or p_carbs is null or p_carbs < 0
     or p_fat is null or p_fat < 0 then
    raise exception 'invalid nutrition values' using errcode = '22023';
  end if;
  if p_base_unit is not null and p_base_unit not in ('g', 'ml') then
    raise exception 'invalid base unit' using errcode = '22023';
  end if;
  if jsonb_typeof(v_portions) <> 'array' or jsonb_array_length(v_portions) > 10 then
    raise exception 'invalid portions' using errcode = '22023';
  end if;
  if v_micro is not null and jsonb_typeof(v_micro) = 'null' then
    v_micro := null;
  end if;
  if not public.is_valid_micronutrients(v_micro) then
    raise exception 'invalid micronutrients' using errcode = '22023';
  end if;
  if v_micro = '{}'::jsonb then
    v_micro := null;
  end if;

  select f.id, f.base_unit into v_locked, v_current
  from public.foods f
  where f.id = p_food_id and f.source = 'user' and f.created_by = v_user
  for update;
  if v_locked is null then
    raise exception 'food not found' using errcode = 'P0002';
  end if;

  v_unit := coalesce(p_base_unit::public.food_base_unit, v_current);
  if v_unit <> v_current and (
       exists (select 1 from public.food_log_entries e where e.food_id = p_food_id)
       or exists (select 1 from public.meal_template_items i where i.food_id = p_food_id)) then
    raise exception 'food unit locked: referenced' using errcode = '23514';
  end if;

  update public.foods
  set name = btrim(p_name), brand = p_brand, kcal_100 = p_kcal, protein_100 = p_protein,
      carbs_100 = p_carbs, fat_100 = p_fat, micronutrients = v_micro, base_unit = v_unit
  where id = p_food_id;

  delete from public.food_portions where food_id = p_food_id;
  for v_portion in select * from jsonb_array_elements(v_portions) loop
    v_label := btrim(v_portion ->> 'label');
    begin
      v_amount := (v_portion ->> 'amount')::numeric;
    exception when others then
      raise exception 'invalid portion amount' using errcode = '22023';
    end;
    if v_label is null or char_length(v_label) not between 1 and 100 or v_amount is null or v_amount <= 0 then
      raise exception 'invalid portion' using errcode = '22023';
    end if;
    insert into public.food_portions (food_id, label, amount, position)
    values (p_food_id, v_label, v_amount, v_pos);
    v_pos := v_pos + 1;
  end loop;
end;
$$;

revoke all on function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb, text)
  from public, anon;
grant execute on function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb, text)
  to authenticated;

create function public.personal_food_in_use(p_food_id uuid) returns boolean
  language plpgsql
  security invoker
  set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not exists (
    select 1 from public.foods f where f.id = p_food_id and f.source = 'user' and f.created_by = v_user) then
    raise exception 'food not found' using errcode = 'P0002';
  end if;
  return exists (select 1 from public.food_log_entries e where e.food_id = p_food_id)
      or exists (select 1 from public.meal_template_items i where i.food_id = p_food_id);
end;
$$;

revoke all on function public.personal_food_in_use(uuid) from public, anon;
grant execute on function public.personal_food_in_use(uuid) to authenticated;
