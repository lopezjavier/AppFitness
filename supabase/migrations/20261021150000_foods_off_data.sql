-- T-068 (US-25, ADR-43): keep the full Open Food Facts product in `foods.off_data` and the optional label
-- values of manual foods in `foods.micronutrients`. Display only: nothing here feeds totals or entries.

alter table public.foods
  add column off_data jsonb null,
  add column micronutrients jsonb null;

-- Valid label values: null or an object with only the four known keys, each a number in [0, 9999.99].
create function public.is_valid_micronutrients(m jsonb) returns boolean
  language sql
  immutable
  set search_path = ''
as $$
  select case
    when m is null then true
    when jsonb_typeof(m) <> 'object' then false
    when (m - array['saturated-fat_100g', 'sugars_100g', 'fiber_100g', 'salt_100g']) <> '{}'::jsonb then false
    else not exists (
      select 1 from jsonb_each(m) e
      where case
        when jsonb_typeof(e.value) <> 'number' then true
        else (e.value #>> '{}')::numeric < 0 or (e.value #>> '{}')::numeric > 9999.99
      end)
  end
$$;

alter table public.foods
  add constraint foods_off_data_object check (off_data is null or jsonb_typeof(off_data) = 'object'),
  add constraint foods_off_data_source check (off_data is null or source = 'off'),
  add constraint foods_micronutrients_valid check (public.is_valid_micronutrients(micronutrients)),
  add constraint foods_micronutrients_source check (micronutrients is null or source = 'user');

-- Cache cleanup (ADR-43): OFF rows stored before this change lack `off_data`. Drop the ones nobody references
-- (their portions cascade); referenced rows keep their id and are refilled the next time they are fetched.
delete from public.foods f
where f.source = 'off'
  and f.off_data is null
  and not exists (select 1 from public.food_log_entries e where e.food_id = f.id)
  and not exists (select 1 from public.favorite_foods ff where ff.food_id = f.id)
  and not exists (select 1 from public.meal_template_items i where i.food_id = f.id);

-- create_personal_food gains p_micronutrients (default null keeps old callers working).
drop function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb);

create function public.create_personal_food(
  p_name            text,
  p_brand           text,
  p_base_unit       public.food_base_unit,
  p_kcal            numeric,
  p_protein         numeric,
  p_carbs           numeric,
  p_fat             numeric,
  p_barcode         text,
  p_portions        jsonb,
  p_micronutrients  jsonb default null
) returns uuid
  language plpgsql
  security invoker
  set search_path = ''
as $$
declare
  v_user     uuid := auth.uid();
  v_id       uuid;
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
  if p_base_unit is null
     or p_kcal is null or p_kcal < 0
     or p_protein is null or p_protein < 0
     or p_carbs is null or p_carbs < 0
     or p_fat is null or p_fat < 0 then
    raise exception 'invalid nutrition values' using errcode = '22023';
  end if;
  if jsonb_typeof(v_portions) <> 'array' or jsonb_array_length(v_portions) > 10 then
    raise exception 'invalid portions' using errcode = '22023';
  end if;
  -- JSON null (not SQL null) means "nothing typed" as well.
  if v_micro is not null and jsonb_typeof(v_micro) = 'null' then
    v_micro := null;
  end if;
  if not public.is_valid_micronutrients(v_micro) then
    raise exception 'invalid micronutrients' using errcode = '22023';
  end if;
  if v_micro = '{}'::jsonb then
    v_micro := null;
  end if;

  insert into public.foods (source, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100, barcode,
                            micronutrients, created_by)
  values ('user', btrim(p_name), p_brand, p_base_unit, p_kcal, p_protein, p_carbs, p_fat,
          nullif(btrim(p_barcode), ''), v_micro, v_user)
  returning id into v_id;

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
    values (v_id, v_label, v_amount, v_pos);
    v_pos := v_pos + 1;
  end loop;

  return v_id;
end;
$$;

revoke all on function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb, jsonb)
  from public, anon;
grant execute on function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb, jsonb)
  to authenticated;
