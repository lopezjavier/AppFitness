-- T-330 (US-149): food search lists ordered by use in the comida being added to.
-- Two new read functions; `most_eaten_foods(int)` is left untouched (legacy, still called by the installed
-- v1.10.0). Adding a defaulted parameter to it would make a call with only `p_limit` ambiguous in PostgREST.
--
-- Window: a use counts when log_date > p_today - 90 (today and the 89 days before). p_today comes from the
-- client (device-local date); null falls back to the UTC date.
-- Ties (both lists): more uses first, then later log_date, then later created_at, then the name ignoring
-- case and accents, then id.

-- Habituales: foods with at least one use in the window, ranked by uses in the comida (all comidas when
-- p_meal_slot_id is null). A comida of another user or an unknown id matches no entries and returns no rows.
create function public.habitual_foods(
  p_meal_slot_id uuid default null,
  p_limit int default 20,
  p_today date default null
)
  returns table (food_id uuid, times_logged bigint)
  language sql
  stable
  security invoker
  set search_path = ''
as $$
  select e.food_id, count(*) as times_logged
  from public.food_log_entries e
  join public.foods f on f.id = e.food_id
  where e.user_id = (select auth.uid())
    and e.food_id is not null
    and e.log_date > coalesce(p_today, (now() at time zone 'utc')::date) - 90
    and (p_meal_slot_id is null or e.meal_slot_id = p_meal_slot_id)
  group by e.food_id, f.name
  order by count(*) desc,
    max(e.log_date) desc,
    max(e.created_at) desc,
    public.immutable_unaccent(lower(f.name)) asc,
    e.food_id asc
  limit least(greatest(coalesce(p_limit, 20), 1), 100)
$$;

revoke execute on function public.habitual_foods(uuid, int, date) from public, anon;
grant execute on function public.habitual_foods(uuid, int, date) to authenticated;

-- Favoritos: every favourite of the caller exactly once, ranked by uses in the comida (window), uses overall
-- (window), last use, then name. A favourite with no use in the window has zero counts and null recency, so
-- it lands last, by name.
create function public.ranked_favourite_foods(
  p_meal_slot_id uuid default null,
  p_today date default null
)
  returns table (food_id uuid)
  language sql
  stable
  security invoker
  set search_path = ''
as $$
  select ff.food_id
  from public.favorite_foods ff
  join public.foods f on f.id = ff.food_id
  left join (
    select e.food_id,
      count(*) filter (where p_meal_slot_id is not null and e.meal_slot_id = p_meal_slot_id) as slot_uses,
      count(*) as all_uses,
      max(e.log_date) as last_date,
      max(e.created_at) as last_created
    from public.food_log_entries e
    where e.user_id = (select auth.uid())
      and e.food_id is not null
      and e.log_date > coalesce(p_today, (now() at time zone 'utc')::date) - 90
    group by e.food_id
  ) u on u.food_id = ff.food_id
  where ff.user_id = (select auth.uid())
  order by coalesce(u.slot_uses, 0) desc,
    coalesce(u.all_uses, 0) desc,
    u.last_date desc nulls last,
    u.last_created desc nulls last,
    public.immutable_unaccent(lower(f.name)) asc,
    ff.food_id asc
$$;

revoke execute on function public.ranked_favourite_foods(uuid, date) from public, anon;
grant execute on function public.ranked_favourite_foods(uuid, date) to authenticated;
