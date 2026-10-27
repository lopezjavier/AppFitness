-- T-126 (US-56): meal names are unique per user (case/space-insensitive) and the meals editor saves
-- renames + order + additions + removals in one atomic RPC. security invoker: RLS applies.

-- Guard run before the unique index: fail loudly (never rename) when a user already has colliding labels.
create function public.assert_unique_meal_labels()
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_dup record;
begin
  select user_id, string_agg(label, ' | ' order by position) as labels
  into v_dup
  from public.meal_slots
  group by user_id, lower(btrim(label))
  having count(*) > 1
  limit 1;
  if found then
    raise exception 'duplicate meal labels for user %: %', v_dup.user_id, v_dup.labels
      using errcode = '23505';
  end if;
end;
$$;

revoke execute on function public.assert_unique_meal_labels() from public, anon, authenticated;

select public.assert_unique_meal_labels();

create unique index meal_slots_user_label_key
  on public.meal_slots (user_id, lower(btrim(label)));

-- p_slots = array in final order of {"id": uuid|null, "label": text}; `code` is never taken from the client.
-- p_removed_ids = explicit removals. An existing slot missing from both lists is rejected (22023).
create function public.save_meal_structure(p_slots jsonb, p_removed_ids uuid[])
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
  v_pos     int := 0;
  v_ids     uuid[] := array[]::uuid[];
  v_labels  text[] := array[]::text[];
  v_removed uuid[] := coalesce(p_removed_ids, array[]::uuid[]);
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_slots is null or jsonb_typeof(p_slots) <> 'array' then
    raise exception 'p_slots must be an array' using errcode = '22023';
  end if;
  if jsonb_array_length(p_slots) = 0 then
    -- Removing everything with nothing to replace it leaves no meal; anything else is a malformed call.
    if cardinality(v_removed) > 0 then
      raise exception 'at least one meal must remain' using errcode = '23514';
    end if;
    raise exception 'p_slots must be a non-empty array' using errcode = '22023';
  end if;
  if array_position(v_removed, null) is not null
     or cardinality(v_removed) <> (select count(distinct r) from unnest(v_removed) r) then
    raise exception 'invalid removed ids' using errcode = '22023';
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
    if lower(v_label) = any (v_labels) then
      raise exception 'duplicate meal name' using errcode = '23505';
    end if;
    v_labels := v_labels || lower(v_label);
    if v_item ->> 'id' is not null then
      v_id := (v_item ->> 'id')::uuid;
      if v_id = any (v_ids) then
        raise exception 'duplicate meal slot id' using errcode = '22023';
      end if;
      if v_id = any (v_removed) then
        raise exception 'a meal cannot be kept and removed' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;
    end if;
  end loop;

  -- Every id (kept or removed) must be one of the caller's slots (RLS hides the others).
  if (select count(*) from public.meal_slots where id = any (v_ids || v_removed))
       <> cardinality(v_ids) + cardinality(v_removed) then
    raise exception 'meal slot not found' using errcode = 'P0002';
  end if;

  -- Every existing slot of the caller must be either kept or explicitly removed.
  if exists (
    select 1 from public.meal_slots
    where user_id = v_uid and id <> all (v_ids) and id <> all (v_removed)
  ) then
    raise exception 'all existing meal slots must be sent' using errcode = '22023';
  end if;

  if exists (
    select 1 from public.food_log_entries
    where user_id = v_uid and meal_slot_id = any (v_removed)
  ) then
    raise exception 'a meal with logged entries cannot be deleted' using errcode = '23514';
  end if;

  -- p_slots is non-empty here, so at least one meal always remains.

  delete from public.meal_slots where user_id = v_uid and id = any (v_removed);

  -- Move positions out of range and park labels on a unique temporary value so swaps never collide mid-way.
  update public.meal_slots set position = position + 1000, label = id::text where user_id = v_uid;

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

revoke execute on function public.save_meal_structure(jsonb, uuid[]) from public, anon;
grant execute on function public.save_meal_structure(jsonb, uuid[]) to authenticated;

-- Ids of the caller's meals that have any logged entry on any day (cannot be removed).
create function public.list_meal_slots_in_use()
returns uuid[]
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  return coalesce((
    select array_agg(s.id)
    from public.meal_slots s
    where s.user_id = v_uid
      and exists (select 1 from public.food_log_entries f where f.meal_slot_id = s.id and f.user_id = v_uid)
  ), array[]::uuid[]);
end;
$$;

revoke execute on function public.list_meal_slots_in_use() from public, anon;
grant execute on function public.list_meal_slots_in_use() to authenticated;
