-- T-062 (US-24): per-day totals of the food diary over a date range (history calendar, week and month summaries).
-- Read-only RPC; security invoker + RLS on food_log_entries isolate users. Only days with entries are returned.
-- plpgsql (not plain sql) so the argument guard can raise.
create function public.food_diary_summary(p_from date, p_to date)
  returns table (log_date date, kcal numeric, protein_g numeric, carbs_g numeric, fat_g numeric)
  language plpgsql
  stable
  security invoker
  set search_path = ''
as $$
begin
  if p_from is null or p_to is null then
    raise exception 'p_from and p_to are required' using errcode = '22023';
  end if;
  if p_from > p_to then
    raise exception 'p_from must not be after p_to' using errcode = '22023';
  end if;
  if p_to - p_from > 400 then
    raise exception 'range must not exceed 400 days' using errcode = '22023';
  end if;

  return query
  select e.log_date, sum(e.kcal), sum(e.protein_g), sum(e.carbs_g), sum(e.fat_g)
  from public.food_log_entries e
  where e.user_id = (select auth.uid())
    and e.log_date between p_from and p_to
  group by e.log_date
  order by e.log_date;
end;
$$;

revoke execute on function public.food_diary_summary(date, date) from public, anon;
grant execute on function public.food_diary_summary(date, date) to authenticated;
