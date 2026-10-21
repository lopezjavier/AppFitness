-- T-076 (US-25): save one manual weight (+ optional body fat) per local day, with the date locked on edit.
-- The caller sends the bounds of the local day [p_day_start, p_day_end) because the server does not know the
-- user's time zone. security invoker: RLS and the column grants apply exactly as for direct writes.
-- Returns the id of the written row.

create or replace function public.save_body_measurement(
  p_id           uuid default null,
  p_weight_kg    numeric default null,
  p_body_fat_pct numeric default null,
  p_measured_at  timestamptz default null,
  p_day_start    timestamptz default null,
  p_day_end      timestamptz default null
)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id uuid;
  v_old_measured_at timestamptz;
begin
  if (select auth.uid()) is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if p_day_start is null or p_day_end is null or p_day_end <= p_day_start
     or p_measured_at is null or p_measured_at < p_day_start or p_measured_at >= p_day_end then
    raise exception 'measured_at must lie in a valid day range' using errcode = '22023';
  end if;

  if p_id is null then
    -- New save: replace the latest manual row of the day, or insert one. Health Connect rows are ignored.
    select m.id into v_id
    from public.body_measurements m
    where m.user_id = (select auth.uid())
      and m.source = 'manual'
      and m.measured_at >= p_day_start
      and m.measured_at < p_day_end
    order by m.measured_at desc, m.id desc
    limit 1;

    if v_id is not null then
      update public.body_measurements
      set weight_kg = p_weight_kg, body_fat_pct = p_body_fat_pct, measured_at = p_measured_at
      where id = v_id;
    else
      insert into public.body_measurements (user_id, measured_at, weight_kg, body_fat_pct, source)
      values ((select auth.uid()), p_measured_at, p_weight_kg, p_body_fat_pct, 'manual')
      returning id into v_id;
    end if;
  else
    -- Edit: only the caller's own manual row (RLS hides the rest); its day can never change.
    select m.measured_at into v_old_measured_at
    from public.body_measurements m
    where m.id = p_id
      and m.user_id = (select auth.uid())
      and m.source = 'manual';

    if not found then
      raise exception 'measurement not found' using errcode = 'P0002';
    end if;

    if v_old_measured_at < p_day_start or v_old_measured_at >= p_day_end then
      raise exception 'the measurement does not belong to that day' using errcode = '22023';
    end if;

    update public.body_measurements
    set weight_kg = p_weight_kg, body_fat_pct = p_body_fat_pct, measured_at = p_measured_at
    where id = p_id;
    v_id := p_id;
  end if;

  return v_id;
end;
$$;

revoke execute on function public.save_body_measurement(
  uuid, numeric, numeric, timestamptz, timestamptz, timestamptz
) from public, anon;
grant execute on function public.save_body_measurement(
  uuid, numeric, numeric, timestamptz, timestamptz, timestamptz
) to authenticated;
