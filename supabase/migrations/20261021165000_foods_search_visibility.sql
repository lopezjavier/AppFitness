-- T-074 (US-18, ADR-46): a single SQL definition of "visible in text search".
-- Personal foods are always visible; OFF foods only when sold in Spain with a Spanish name
-- (same rule as isSpainSearchHit in the Edge Functions). Used by the search view and the purge.

create function public.is_spain_off_food(p_off_data jsonb)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select coalesce(
    p_off_data is not null
    and jsonb_typeof(p_off_data -> 'countries_tags') = 'array'
    and p_off_data -> 'countries_tags' @> '["en:spain"]'::jsonb
    and (
      btrim(coalesce(p_off_data ->> 'product_name_es', '')) <> ''
      or (
        p_off_data ->> 'lang' = 'es'
        and btrim(coalesce(p_off_data ->> 'product_name', '')) <> ''
      )
    ),
    false
  );
$$;

-- Explicit columns: off_data never leaves through search. security_invoker keeps the foods RLS in force.
create view public.foods_searchable
with (security_invoker = true) as
select id, source, barcode, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100,
       serving_label, serving_amount, image_url, fetched_at, search_vector
from public.foods
where source = 'user' or (source = 'off' and public.is_spain_off_food(off_data));

revoke all on table public.foods_searchable from anon, authenticated;
grant select on table public.foods_searchable to authenticated;
grant all on table public.foods_searchable to service_role;
revoke execute on function public.is_spain_off_food(jsonb) from public, anon;
grant execute on function public.is_spain_off_food(jsonb) to authenticated, service_role;
