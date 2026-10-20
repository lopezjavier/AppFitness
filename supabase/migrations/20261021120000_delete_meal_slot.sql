-- T-067 (US-23, ADR-41): meals are deleted when unused, never disabled.
-- security invoker: RLS on meal_slots / food_log_entries applies.

-- No UI re-enables a meal any more, so every previously disabled meal comes back.
update public.meal_slots set enabled = true where not enabled;

-- Replace save_meal_slots: no viewed day, no disable rules. `enabled` is accepted and ignored (always true).
drop function public.save_meal_slots(jsonb, date);

-- p_slots = array in final order of {"id": uuid|null, "label": text}; `code` is never taken from the client.
create function public.save_meal_slots(p_slots jsonb)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := auth.uid();
  v_item  jsonb;
  v_id    uuid;
  v_label text;
  v_pos   int := 0;
  v_ids   uuid[] := array[]::uuid[];
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_slots is null or jsonb_typeof(p_slots) <> 'array' or jsonb_array_length(p_slots) = 0 then
    raise exception 'p_slots must be a non-empty array' using errcode = '22023';
  end if;

  for v_item in select value from jsonb_array_elements(p_slots) loop
    if jsonb_typeof(v_item) <> 'object'
       or jsonb_typeof(v_item -> 'label') is distinct from 'string'
       or coalesce(jsonb_typeof(v_item -> 'id'), 'null') not in ('null', 'string') then
      raise exception 'invalid meal slot element' using errcode = '22023';
    end if;
    v_label := btrim(v_item ->> 'label');
    if char_length(v_label) not between 1 and 50 then
      raise exception 'label must have 1 to 50 characters' using errcode = '22023';
    end if;
    if v_item ->> 'id' is not null then
      v_id := (v_item ->> 'id')::uuid;
      if v_id = any (v_ids) then
        raise exception 'duplicate meal slot id' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;
    end if;
  end loop;

  -- Every given id must be one of the caller's slots (RLS hides the others).
  if (select count(*) from public.meal_slots where id = any (v_ids)) <> coalesce(array_length(v_ids, 1), 0) then
    raise exception 'meal slot not found' using errcode = 'P0002';
  end if;

  -- Every existing slot of the caller must be present: deletion goes through delete_meal_slot.
  if exists (select 1 from public.meal_slots where user_id = v_uid and id <> all (v_ids)) then
    raise exception 'all existing meal slots must be sent' using errcode = '22023';
  end if;

  -- Move positions out of range so the final 0..n-1 write never collides with unique (user_id, position).
  update public.meal_slots set position = position + 1000 where user_id = v_uid;

  for v_item in select value from jsonb_array_elements(p_slots) loop
    v_label := btrim(v_item ->> 'label');
    if v_item ->> 'id' is null then
      insert into public.meal_slots (code, label, position, enabled)
      values (null, v_label, v_pos, true);
    else
      update public.meal_slots
      set label = v_label, enabled = true, position = v_pos
      where id = (v_item ->> 'id')::uuid and user_id = v_uid;
    end if;
    v_pos := v_pos + 1;
  end loop;
end;
$$;

revoke execute on function public.save_meal_slots(jsonb) from public, anon;
grant execute on function public.save_meal_slots(jsonb) to authenticated;

-- Deleting a meal is now allowed (only through delete_meal_slot's rules; the FK from food_log_entries blocks used ones).
create policy meal_slots_delete_own on public.meal_slots
  for delete to authenticated
  using ((select auth.uid()) = user_id);
grant delete on table public.meal_slots to authenticated;

-- Deletes an unused meal and re-packs the remaining positions to 0..n-1.
create function public.delete_meal_slot(p_id uuid)
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
  if not exists (select 1 from public.meal_slots where id = p_id and user_id = v_uid) then
    raise exception 'meal slot not found' using errcode = 'P0002';
  end if;
  if (select count(*) from public.meal_slots where user_id = v_uid) <= 1 then
    raise exception 'at least one meal must remain' using errcode = '23514';
  end if;
  if exists (select 1 from public.food_log_entries where meal_slot_id = p_id and user_id = v_uid) then
    raise exception 'a meal with logged entries cannot be deleted' using errcode = '23514';
  end if;

  delete from public.meal_slots where id = p_id and user_id = v_uid;

  -- Re-pack: move out of range first so unique (user_id, position) never collides.
  update public.meal_slots set position = position + 1000 where user_id = v_uid;
  update public.meal_slots s
  set position = r.rn
  from (
    select id, (row_number() over (order by position) - 1)::int as rn
    from public.meal_slots where user_id = v_uid
  ) r
  where s.id = r.id;
end;
$$;

revoke execute on function public.delete_meal_slot(uuid) from public, anon;
grant execute on function public.delete_meal_slot(uuid) to authenticated;
