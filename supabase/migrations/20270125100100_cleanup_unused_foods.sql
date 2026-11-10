-- T-272 (E18, US-133, ADR-105): personal foods (source = 'user', OFF copies included) unused for 30 days that are
-- not favourites and not in a saved comida are deleted by a monthly pg_cron job; the first run happens here.
-- Diary entries survive (food_id set to null, snapshots untouched). The self-check raises on any difference in
-- the diary, so the whole migration (one transaction) aborts and production data stays as it was.

create extension if not exists pg_cron with schema pg_catalog;

create function public.cleanup_unused_foods() returns integer
  language plpgsql
  security definer
  set search_path = ''
as $$
declare
  v_before text;
  v_after text;
  v_deleted integer;
begin
  select coalesce(string_agg(concat_ws('|', e.id, e.user_id, e.log_date, e.food_name, e.amount, e.kcal,
                                       e.protein_g, e.carbs_g, e.fat_g), ';' order by e.id), '')
  into v_before
  from public.food_log_entries e;

  with doomed as (
    select f.id
    from public.foods f
    where f.source = 'user'
      and greatest(f.created_at, coalesce(
            (select max(e.created_at) from public.food_log_entries e
             where e.food_id = f.id and e.user_id = f.created_by), '-infinity'::timestamptz))
          < now() - interval '30 days'
      and not exists (select 1 from public.favorite_foods ff
                      where ff.food_id = f.id and ff.user_id = f.created_by)
      -- Foods in a saved comida are protected here, not by the FK: meal_template_items.food_id is left as it is (deferred).
  and not exists (select 1 from public.meal_template_items i
                      join public.meal_templates t on t.id = i.template_id
                      where i.food_id = f.id and t.user_id = f.created_by)
  ),
  gone as (
    delete from public.foods f using doomed d where f.id = d.id returning f.id
  )
  select count(*)::integer into v_deleted from gone;

  select coalesce(string_agg(concat_ws('|', e.id, e.user_id, e.log_date, e.food_name, e.amount, e.kcal,
                                       e.protein_g, e.carbs_g, e.fat_g), ';' order by e.id), '')
  into v_after
  from public.food_log_entries e;

  if v_before is distinct from v_after then
    raise exception 'cleanup_unused_foods: a diary entry changed';
  end if;

  return v_deleted;
end;
$$;

revoke all on function public.cleanup_unused_foods() from public, anon, authenticated;

select public.cleanup_unused_foods();

select cron.schedule('cleanup-unused-foods', '0 3 1 * *', $$select public.cleanup_unused_foods()$$);
