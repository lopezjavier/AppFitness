-- T-113: save_meal_defaults validates with set-based checks instead of a row-by-row loop.
-- Signature, grants, security, error codes (22023, P0002, 23514, 28000) and the atomic replace are unchanged.
create or replace function public.save_meal_defaults(p_defaults jsonb)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_defaults is null or jsonb_typeof(p_defaults) <> 'array' then
    raise exception 'p_defaults must be an array' using errcode = '22023';
  end if;

  -- Validate everything before writing anything.
  if exists (
    select 1 from jsonb_array_elements(p_defaults) e
    where jsonb_typeof(e.value) <> 'object'
       or jsonb_typeof(e.value -> 'meal_slot_id') is distinct from 'string'
       or jsonb_typeof(e.value -> 'template_id') is distinct from 'string'
       or jsonb_typeof(e.value -> 'weekdays') is distinct from 'array'
  ) then
    raise exception 'invalid default' using errcode = '22023';
  end if;

  begin
    perform (e.value ->> 'meal_slot_id')::uuid, (e.value ->> 'template_id')::uuid
    from jsonb_array_elements(p_defaults) e;
  exception when invalid_text_representation then
    raise exception 'invalid id' using errcode = '22023';
  end;

  if exists (
    select 1
    from jsonb_array_elements(p_defaults) e, jsonb_array_elements(e.value -> 'weekdays') d
    where jsonb_typeof(d.value) <> 'number' or (d.value #>> '{}') !~ '^[1-7]$'
  ) then
    raise exception 'weekday must be an integer from 1 to 7' using errcode = '22023';
  end if;

  if exists (
    select 1
    from jsonb_array_elements(p_defaults) e, jsonb_array_elements(e.value -> 'weekdays') d
    group by (e.value ->> 'meal_slot_id')::uuid, (d.value #>> '{}')::smallint
    having count(*) > 1
  ) then
    raise exception 'duplicate meal and weekday' using errcode = '22023';
  end if;

  if exists (
    select 1 from jsonb_array_elements(p_defaults) e
    where not exists (
      select 1 from public.meal_slots s
      where s.id = (e.value ->> 'meal_slot_id')::uuid and s.user_id = v_uid)
  ) then
    raise exception 'meal not found' using errcode = 'P0002';
  end if;

  if exists (
    select 1 from jsonb_array_elements(p_defaults) e
    where not exists (
      select 1 from public.meal_templates t
      where t.id = (e.value ->> 'template_id')::uuid and t.user_id = v_uid)
  ) then
    raise exception 'meal template not found' using errcode = 'P0002';
  end if;

  if exists (
    select 1 from jsonb_array_elements(p_defaults) e
    where not exists (
      select 1 from public.meal_template_items i
      where i.template_id = (e.value ->> 'template_id')::uuid)
  ) then
    raise exception 'meal template has no items' using errcode = '23514';
  end if;

  delete from public.meal_defaults where user_id = v_uid;

  insert into public.meal_defaults (user_id, meal_slot_id, template_id, weekday)
  select v_uid, (e.value ->> 'meal_slot_id')::uuid, (e.value ->> 'template_id')::uuid,
         (d.value #>> '{}')::smallint
  from jsonb_array_elements(p_defaults) e,
       jsonb_array_elements(e.value -> 'weekdays') d;
end;
$$;
