-- T-122: save_meal_as_comida is strict. Every listed entry id must be grouped (same user, day and meal),
-- otherwise the whole call fails and rolls back, including the comida created by save_meal_template.
create or replace function public.save_meal_as_comida(
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
  v_n        integer;
  v_expected integer;
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
  get diagnostics v_n = row_count;

  select count(distinct e) into v_expected from unnest(p_entry_ids) as e;
  if v_n <> v_expected then
    raise exception 'unknown or foreign entry id' using errcode = '22023';
  end if;

  return v_id;
end;
$$;

revoke execute on function public.save_meal_as_comida(date, uuid, text, jsonb, uuid[]) from public, anon;
grant execute on function public.save_meal_as_comida(date, uuid, text, jsonb, uuid[]) to authenticated;
