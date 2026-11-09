-- T-254 (E17, US-118): personal copies of Open Food Facts foods.
-- copy_off_food(food_id) creates (or reuses) the caller's own 'user' food from an OFF row. The copy is the user's from then on.

-- ---------------------------------------------------------------------------
-- 1. off_micronutrients: server twin of OffMicronutrientsMapper.kt. For each whitelisted key read nutriments.<key>_100ml
-- (ml foods) or _100g (g foods), falling back to the other suffix; keep plain numbers in 0..9999.99; store as <key>_100g.
-- ---------------------------------------------------------------------------
create function public.off_micronutrients(p_off_data jsonb, p_base_unit public.food_base_unit) returns jsonb
  language sql
  immutable
  set search_path = ''
as $$
  select nullif(coalesce(jsonb_object_agg(k.key || '_100g', k.val), '{}'::jsonb), '{}'::jsonb)
  from (
    select w.key,
           coalesce(
             case when (p_off_data -> 'nutriments' ->> (w.key || case when p_base_unit = 'ml' then '_100ml' else '_100g' end))
                        ~ '^\d{1,4}(\.\d{1,2})?$'
                  then (p_off_data -> 'nutriments' ->> (w.key || case when p_base_unit = 'ml' then '_100ml' else '_100g' end))::numeric
             end,
             case when (p_off_data -> 'nutriments' ->> (w.key || case when p_base_unit = 'ml' then '_100g' else '_100ml' end))
                        ~ '^\d{1,4}(\.\d{1,2})?$'
                  then (p_off_data -> 'nutriments' ->> (w.key || case when p_base_unit = 'ml' then '_100g' else '_100ml' end))::numeric
             end
           ) as val
    from unnest(array[
      'saturated-fat', 'monounsaturated-fat', 'polyunsaturated-fat', 'sugars', 'polyols', 'starch', 'fiber', 'salt',
      'vitamin-a', 'vitamin-d', 'vitamin-e', 'vitamin-k', 'vitamin-c', 'vitamin-b1', 'vitamin-b2', 'vitamin-pp',
      'vitamin-b6', 'vitamin-b9', 'vitamin-b12', 'biotin', 'pantothenic-acid',
      'potassium', 'chloride', 'calcium', 'phosphorus', 'magnesium', 'iron', 'zinc', 'copper', 'manganese',
      'fluoride', 'selenium', 'chromium', 'molybdenum', 'iodine'
    ]) as w(key)
  ) k
  where k.val is not null and k.val <= 9999.99
$$;

revoke all on function public.off_micronutrients(jsonb, public.food_base_unit) from public, anon;
grant execute on function public.off_micronutrients(jsonb, public.food_base_unit) to authenticated;

-- ---------------------------------------------------------------------------
-- 2. Core: copy an OFF food for a given user. Not callable by users (the RPC and the data migration call it).
-- ---------------------------------------------------------------------------
create function public.copy_off_food_core(p_user uuid, p_food_id uuid) returns uuid
  language plpgsql
  set search_path = ''
as $$
declare
  v_src  public.foods%rowtype;
  v_copy uuid;
begin
  select * into v_src from public.foods f where f.id = p_food_id and f.source = 'off';
  if not found then
    raise exception 'food not found' using errcode = 'P0002';
  end if;

  -- Serialise concurrent copies of the same barcode for the same user.
  if v_src.barcode is not null then
    perform pg_advisory_xact_lock(hashtextextended(p_user::text || ':' || v_src.barcode, 0));
    select f.id into v_copy
    from public.foods f
    where f.source = 'user' and f.created_by = p_user and f.barcode = v_src.barcode
    order by f.created_at, f.id
    limit 1;
    if v_copy is not null then
      return v_copy;
    end if;
  end if;

  insert into public.foods (
    source, created_by, name, brand, barcode, base_unit, kcal_100, protein_100, carbs_100, fat_100,
    alcohol_g_100, serving_label, serving_amount, image_url, micronutrients, energy_check_saved_anyway)
  values (
    'user', p_user, v_src.name, v_src.brand, v_src.barcode, v_src.base_unit, v_src.kcal_100, v_src.protein_100,
    v_src.carbs_100, v_src.fat_100, v_src.alcohol_g_100, v_src.serving_label, v_src.serving_amount, v_src.image_url,
    public.off_micronutrients(v_src.off_data, v_src.base_unit), false)
  returning id into v_copy;

  insert into public.food_portions (food_id, label, amount, position)
  select v_copy, p.label, p.amount, p.position from public.food_portions p where p.food_id = v_src.id;

  return v_copy;
end;
$$;

revoke all on function public.copy_off_food_core(uuid, uuid) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3. RPC. Definer because the core is not callable by users; the caller can only copy for themselves.
-- ---------------------------------------------------------------------------
create function public.copy_off_food(p_food_id uuid) returns uuid
  language plpgsql
  security definer
  set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  return public.copy_off_food_core(v_user, p_food_id);
end;
$$;

revoke all on function public.copy_off_food(uuid) from public, anon;
grant execute on function public.copy_off_food(uuid) to authenticated;
