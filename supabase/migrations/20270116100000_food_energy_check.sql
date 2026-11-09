-- T-251 (E17, US-116, US-117, ADR-102): food energy check, optional alcohol, "saved anyway" flag and the audit.
-- The rule lives in planning/07-coach-design.md section 4 "Food energy check" (no formula is restated in prose here).

-- ---------------------------------------------------------------------------
-- 1. Columns. Alcohol is grams per 100 base units (never % vol); unlike micronutrients it is allowed on 'off' rows.
-- ---------------------------------------------------------------------------
alter table public.foods
  add column alcohol_g_100 numeric(5,1) null check (alcohol_g_100 between 0 and 100),
  add column energy_check_saved_anyway boolean not null default false;

-- ---------------------------------------------------------------------------
-- 2. The deterministic check (same figures as the on-device version).
-- Null fibre, polyols and alcohol count as 0. computed and gap are returned unrounded.
-- ---------------------------------------------------------------------------
create function public.food_energy_check(
  p_kcal    numeric,
  p_protein numeric,
  p_carbs   numeric,
  p_fat     numeric,
  p_fibre   numeric,
  p_polyols numeric,
  p_alcohol numeric
) returns table (result text, computed numeric, gap numeric)
  language sql
  immutable
  set search_path = ''
as $$
  select
    case
      when p_kcal <= 13 and c.computed <= 13 then 'skipped'
      when abs(c.computed - p_kcal) > 0.30 * p_kcal + 5 then 'clearly_wrong'
      when abs(c.computed - p_kcal) > 0.15 * p_kcal + 5 then 'mismatch'
      else 'ok'
    end,
    c.computed,
    c.computed - p_kcal
  from (
    select 4 * p_protein
         + 4 * (p_carbs - least(coalesce(p_polyols, 0), p_carbs))
         + 2.4 * least(coalesce(p_polyols, 0), p_carbs)
         + 9 * p_fat
         + 2 * coalesce(p_fibre, 0)
         + 7 * coalesce(p_alcohol, 0) as computed
  ) c;
$$;

revoke all on function public.food_energy_check(numeric, numeric, numeric, numeric, numeric, numeric, numeric)
  from public, anon;
grant execute on function public.food_energy_check(numeric, numeric, numeric, numeric, numeric, numeric, numeric)
  to authenticated;

-- ---------------------------------------------------------------------------
-- 3. OFF alcohol: OFF stores % vol under nutriments.alcohol_100g (or alcohol); grams = % vol x 0.789, capped at 100.
-- A value that is absent or not a plain number gives null.
-- ---------------------------------------------------------------------------
create function public.off_alcohol_g(p_off_data jsonb) returns numeric
  language sql
  immutable
  set search_path = ''
as $$
  select least(100, round(v.pct::numeric * 0.789, 1))
  from (
    select coalesce(
      case when p_off_data -> 'nutriments' ->> 'alcohol_100g' ~ '^\d+(\.\d+)?$'
           then p_off_data -> 'nutriments' ->> 'alcohol_100g' end,
      case when p_off_data -> 'nutriments' ->> 'alcohol' ~ '^\d+(\.\d+)?$'
           then p_off_data -> 'nutriments' ->> 'alcohol' end
    ) as pct
  ) v
  where v.pct is not null;
$$;

revoke all on function public.off_alcohol_g(jsonb) from public, anon;
grant execute on function public.off_alcohol_g(jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Trigger: any change of an input of the check clears the "saved anyway" flag, unless the same write changes the
-- flag itself (an explicit "save anyway"). Name, brand, portions, other micronutrients and base unit do not touch it.
-- ---------------------------------------------------------------------------
create function public.foods_energy_flag() returns trigger
  language plpgsql
  security invoker
  set search_path = ''
as $$
begin
  if new.energy_check_saved_anyway is not distinct from old.energy_check_saved_anyway
     and (new.kcal_100, new.protein_100, new.carbs_100, new.fat_100, new.alcohol_g_100,
          new.micronutrients -> 'fiber_100g', new.micronutrients -> 'polyols_100g')
         is distinct from
         (old.kcal_100, old.protein_100, old.carbs_100, old.fat_100, old.alcohol_g_100,
          old.micronutrients -> 'fiber_100g', old.micronutrients -> 'polyols_100g') then
    new.energy_check_saved_anyway := false;
  end if;
  return new;
end;
$$;

create trigger foods_energy_flag
  before update on public.foods
  for each row execute function public.foods_energy_flag();

-- ---------------------------------------------------------------------------
-- 5. create_personal_food / update_personal_food gain p_alcohol and p_saved_anyway (defaults keep old callers working).
-- The flag is only ever stored on a food that fails the check (a passing food never carries it, 07 section 4).
-- update_personal_food: p_alcohol null means "no alcohol" (clears the column), because the form sends the full state.
-- ---------------------------------------------------------------------------
drop function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb, jsonb);

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
  p_micronutrients  jsonb default null,
  p_alcohol         numeric default null,
  p_saved_anyway    boolean default false
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
  v_result   text;
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
     or p_fat is null or p_fat < 0
     or (p_alcohol is not null and p_alcohol not between 0 and 100) then
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

  select c.result into v_result
  from public.food_energy_check(p_kcal, p_protein, p_carbs, p_fat,
         (v_micro ->> 'fiber_100g')::numeric, (v_micro ->> 'polyols_100g')::numeric, p_alcohol) c;

  insert into public.foods (source, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100, barcode,
                            micronutrients, alcohol_g_100, energy_check_saved_anyway, created_by)
  values ('user', btrim(p_name), p_brand, p_base_unit, p_kcal, p_protein, p_carbs, p_fat,
          nullif(btrim(p_barcode), ''), v_micro, p_alcohol,
          coalesce(p_saved_anyway, false) and v_result in ('mismatch', 'clearly_wrong'), v_user)
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

revoke all on function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb, jsonb, numeric, boolean)
  from public, anon;
grant execute on function public.create_personal_food(text, text, public.food_base_unit, numeric, numeric, numeric, numeric, text, jsonb, jsonb, numeric, boolean)
  to authenticated;

drop function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb, text);

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
  p_base_unit       text default null,
  p_alcohol         numeric default null,
  p_saved_anyway    boolean default false
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
  v_failing  boolean;
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
     or p_fat is null or p_fat < 0
     or (p_alcohol is not null and p_alcohol not between 0 and 100) then
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

  -- The foods_energy_flag trigger clears the flag here when an input of the check changes.
  update public.foods
  set name = btrim(p_name), brand = p_brand, kcal_100 = p_kcal, protein_100 = p_protein,
      carbs_100 = p_carbs, fat_100 = p_fat, micronutrients = v_micro, base_unit = v_unit,
      alcohol_g_100 = p_alcohol
  where id = p_food_id;

  select c.result in ('mismatch', 'clearly_wrong') into v_failing
  from public.foods f
  cross join lateral public.food_energy_check(f.kcal_100, f.protein_100, f.carbs_100, f.fat_100,
         (f.micronutrients ->> 'fiber_100g')::numeric, (f.micronutrients ->> 'polyols_100g')::numeric,
         f.alcohol_g_100) c
  where f.id = p_food_id;

  if coalesce(p_saved_anyway, false) then
    -- explicit "save anyway": the flag is true exactly when the food fails
    update public.foods set energy_check_saved_anyway = v_failing where id = p_food_id;
  elsif not v_failing then
    update public.foods set energy_check_saved_anyway = false where id = p_food_id;
  end if;

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

revoke all on function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb, text, numeric, boolean)
  from public, anon;
grant execute on function public.update_personal_food(uuid, text, text, numeric, numeric, numeric, numeric, jsonb, jsonb, text, numeric, boolean)
  to authenticated;

-- ---------------------------------------------------------------------------
-- 6. The audit (US-117): the caller's own personal foods that fail the check and are not flagged. Writes nothing.
-- ---------------------------------------------------------------------------
create function public.food_energy_audit()
  returns table (food_id uuid, name text, brand text, kcal_100 numeric, computed numeric, gap numeric, result text)
  language plpgsql
  stable
  security invoker
  set search_path = ''
as $$
#variable_conflict use_column
begin
  if (select auth.uid()) is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  return query
  select f.id, f.name, f.brand, f.kcal_100, c.computed, c.gap, c.result
  from public.foods f
  cross join lateral public.food_energy_check(f.kcal_100, f.protein_100, f.carbs_100, f.fat_100,
         (f.micronutrients ->> 'fiber_100g')::numeric, (f.micronutrients ->> 'polyols_100g')::numeric,
         f.alcohol_g_100) c
  where f.source = 'user'
    and f.created_by = (select auth.uid())
    and not f.energy_check_saved_anyway
    and c.result in ('mismatch', 'clearly_wrong')
  order by (c.result = 'clearly_wrong') desc, abs(c.gap) desc, f.name, f.id;
end;
$$;

revoke all on function public.food_energy_audit() from public, anon;
grant execute on function public.food_energy_audit() to authenticated;

-- ---------------------------------------------------------------------------
-- 7. Backfill: cached OFF rows get their alcohol in grams from the stored product. Nothing else is touched.
-- ---------------------------------------------------------------------------
update public.foods
set alcohol_g_100 = public.off_alcohol_g(off_data)
where source = 'off'
  and off_data is not null
  and alcohol_g_100 is null
  and public.off_alcohol_g(off_data) is not null;
