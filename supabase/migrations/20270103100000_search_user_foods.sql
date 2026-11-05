-- Manual food search that ignores case and accents (T-230, part B).
-- Substring semantics (no stemming), unlike foods.search_vector. No index: a user has tens of
-- manual foods, so a sequential scan over the caller's rows (RLS) is fine.
create function public.search_user_food_ids(p_query text, p_limit int default 20)
  returns table (food_id uuid)
  language sql
  stable
  security invoker
  set search_path = ''
as $$
  select f.id
  from public.foods f
  where f.source = 'user'
    and f.created_by = (select auth.uid())
    and public.immutable_unaccent(lower(f.name)) like
      '%' || replace(replace(replace(public.immutable_unaccent(lower(coalesce(p_query, ''))),
        '\', '\\'), '%', '\%'), '_', '\_') || '%' escape '\'
  order by f.name
  limit least(greatest(coalesce(p_limit, 20), 1), 100)
$$;

revoke execute on function public.search_user_food_ids(text, int) from public, anon;
grant execute on function public.search_user_food_ids(text, int) to authenticated;
