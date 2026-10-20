-- T-059 (US-23): atomic save of the user's meal structure (rename, add, enable/disable, reorder).
-- security invoker: RLS on meal_slots / food_log_entries applies. Nothing is ever deleted.
-- p_slots = array in final order of {"id": uuid|null, "label": text, "enabled": boolean}; `code` is never
-- taken from the client (defaults keep their stored code, new rows get null).
create function public.save_meal_slots(p_slots jsonb, p_viewed_day date)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := auth.uid();
  v_item    jsonb;
  v_id      uuid;
  v_label   text;
  v_enabled boolean;
  v_pos     int := 0;
  v_ids     uuid[] := array[]::uuid[];
  v_any_on  boolean := false;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_viewed_day is null then
    raise exception 'viewed day is required' using errcode = '22023';
  end if;
  if p_slots is null or jsonb_typeof(p_slots) <> 'array' or jsonb_array_length(p_slots) = 0 then
    raise exception 'p_slots must be a non-empty array' using errcode = '22023';
  end if;

  -- Validate the shape of every element.
  for v_item in select value from jsonb_array_elements(p_slots) loop
    if jsonb_typeof(v_item) <> 'object'
       or jsonb_typeof(v_item -> 'label') is distinct from 'string'
       or jsonb_typeof(v_item -> 'enabled') is distinct from 'boolean'
       or coalesce(jsonb_typeof(v_item -> 'id'), 'null') not in ('null', 'string') then
      raise exception 'invalid meal slot element' using errcode = '22023';
    end if;
    v_label := btrim(v_item ->> 'label');
    if char_length(v_label) not between 1 and 50 then
      raise exception 'label must have 1 to 50 characters' using errcode = '22023';
    end if;
    if (v_item ->> 'enabled')::boolean then
      v_any_on := true;
    end if;
    if v_item ->> 'id' is not null then
      v_id := (v_item ->> 'id')::uuid;
      if v_id = any (v_ids) then
        raise exception 'duplicate meal slot id' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;
    end if;
  end loop;

  if not v_any_on then
    raise exception 'at least one meal must be enabled' using errcode = '22023';
  end if;

  -- Every given id must be one of the caller's slots (RLS hides the others).
  if (select count(*) from public.meal_slots where id = any (v_ids)) <> coalesce(array_length(v_ids, 1), 0) then
    raise exception 'meal slot not found' using errcode = 'P0002';
  end if;

  -- Every existing slot of the caller must be present: nothing is deleted.
  if exists (select 1 from public.meal_slots where user_id = v_uid and id <> all (v_ids)) then
    raise exception 'all existing meal slots must be sent' using errcode = '22023';
  end if;

  -- A slot with entries on the viewed day cannot be disabled.
  if exists (
    select 1
    from jsonb_array_elements(p_slots) e
    join public.meal_slots s on s.id = (e.value ->> 'id')::uuid and s.user_id = v_uid
    where not (e.value ->> 'enabled')::boolean
      and s.enabled
      and exists (
        select 1 from public.food_log_entries f
        where f.meal_slot_id = s.id and f.user_id = v_uid and f.log_date = p_viewed_day)
  ) then
    raise exception 'a meal with entries on the viewed day cannot be disabled' using errcode = '23514';
  end if;

  -- Move positions out of range so the final 0..n-1 write never collides with unique (user_id, position).
  update public.meal_slots set position = position + 1000 where user_id = v_uid;

  for v_item in select value from jsonb_array_elements(p_slots) loop
    v_label := btrim(v_item ->> 'label');
    v_enabled := (v_item ->> 'enabled')::boolean;
    if v_item ->> 'id' is null then
      insert into public.meal_slots (code, label, position, enabled)
      values (null, v_label, v_pos, v_enabled);
    else
      update public.meal_slots
      set label = v_label, enabled = v_enabled, position = v_pos
      where id = (v_item ->> 'id')::uuid and user_id = v_uid;
    end if;
    v_pos := v_pos + 1;
  end loop;
end;
$$;

revoke execute on function public.save_meal_slots(jsonb, date) from public, anon;
grant execute on function public.save_meal_slots(jsonb, date) to authenticated;
