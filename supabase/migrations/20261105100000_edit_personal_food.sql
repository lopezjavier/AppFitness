-- T-102 (US-47, ADR-53): edit manual foods. Widens the label whitelist to every Spanish-label nutrient and adds
-- update_personal_food. Past food_log_entries keep their snapshots (nothing references food_portions).

create or replace function public.is_valid_micronutrients(m jsonb) returns boolean
  language sql
  immutable
  set search_path = ''
as $$
  select case
    when m is null then true
    when jsonb_typeof(m) <> 'object' then false
    when (m - array[
      'saturated-fat_100g', 'monounsaturated-fat_100g', 'polyunsaturated-fat_100g', 'sugars_100g', 'polyols_100g',
      'starch_100g', 'fiber_100g', 'salt_100g',
      'vitamin-a_100g', 'vitamin-d_100g', 'vitamin-e_100g', 'vitamin-k_100g', 'vitamin-c_100g', 'vitamin-b1_100g',
      'vitamin-b2_100g', 'vitamin-pp_100g', 'vitamin-b6_100g', 'vitamin-b9_100g', 'vitamin-b12_100g', 'biotin_100g',
      'pantothenic-acid_100g',
      'potassium_100g', 'chloride_100g', 'calcium_100g', 'phosphorus_100g', 'magnesium_100g', 'iron_100g',
      'zinc_100g', 'copper_100g', 'manganese_100g', 'fluoride_100g', 'selenium_100g', 'chromium_100g',
      'molybdenum_100g', 'iodine_100g'
    ]) <> '{}'::jsonb then false
    else not exists (
      select 1 from jsonb_each(m) e
      where case
        when jsonb_typeof(e.value) <> 'number' then true
        else (e.value #>> '{}')::numeric < 0 or (e.value #>> '{}')::numeric > 9999.99
      end)
  end
$$;

create function public.update_personal_food(
  p_food_id         uuid,
  p_name            text,
  p_brand           text,
  p_kcal            numeric,
  p_protein         numeric,
  p_carbs           numeric,
  p_fat             numeric,
  p_portions        jsonb,
  p_micronutrients  jsonb default null
) returns void
  language plpgsql
  security invoker
  set search_path = ''
as $$
declare
  v_user     uuid := auth.uid();
  v_locked   uuid;
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

  select f.id into v_locked
  from public.foods f
  where f.id = p_food_id and f.source = 'user' and f.created_by = v_user
  for update;
  if v_locked is null then
    raise exception 'food not found' using errcode = 'P0002';
  end if;

  update public.foods
  set name = btrim(p_name), brand = p_brand, kcal_100 = p_kcal, protein_100 = p_protein,
      carbs_100 = p_carbs, fat_100 = p_fat, micronutrients = v_micro
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

revoke all on function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb)
  from public, anon;
grant execute on function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb)
  to authenticated;
