-- T-058 (US-18, US-22): create a personal food and its named portions atomically. SECURITY INVOKER, so the
-- foods_insert_own / food_portions_insert_own RLS policies keep applying.

create function public.create_personal_food(
  p_name      text,
  p_brand     text,
  p_base_unit public.food_base_unit,
  p_kcal      numeric,
  p_protein   numeric,
  p_carbs     numeric,
  p_fat       numeric,
  p_barcode   text,
  p_portions  jsonb
) returns uuid
  language plpgsql
  security invoker
  set search_path = ''
as $$
declare
  v_user     uuid := auth.uid();
  v_id       uuid;
  v_portions jsonb := coalesce(p_portions, '[]'::jsonb);
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

  insert into public.foods (source, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100, barcode, created_by)
  values ('user', btrim(p_name), p_brand, p_base_unit, p_kcal, p_protein, p_carbs, p_fat,
          nullif(btrim(p_barcode), ''), v_user)
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

revoke all on function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb)
  from public, anon;
grant execute on function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb)
  to authenticated;
