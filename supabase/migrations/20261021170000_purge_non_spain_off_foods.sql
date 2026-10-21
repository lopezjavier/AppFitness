-- T-074 (US-18, ADR-46): text search is limited to products sold in Spain with a Spanish name.
-- Selective cleanup of the cached OFF foods that search would never show again: only rows that no diary
-- entry, favourite or meal template references are deleted (history is preserved, unlike the T-070 purge).
-- Referenced non-Spain rows stay; the food-search function hides them from search. Idempotent.
-- food_portions go by ON DELETE CASCADE.

delete from public.foods f
where f.source = 'off'
  and not coalesce(
    f.off_data is not null
    and f.off_data -> 'countries_tags' @> '["en:spain"]'::jsonb
    and (
      btrim(coalesce(f.off_data ->> 'product_name_es', '')) <> ''
      or (
        f.off_data ->> 'lang' = 'es'
        and btrim(coalesce(f.off_data ->> 'product_name', '')) <> ''
      )
    ),
    false
  )
  and not exists (select 1 from public.food_log_entries e where e.food_id = f.id)
  and not exists (select 1 from public.favorite_foods v where v.food_id = f.id)
  and not exists (select 1 from public.meal_template_items i where i.food_id = f.id);
