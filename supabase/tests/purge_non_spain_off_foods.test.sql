-- pgTAP: selective purge of non-Spain OFF foods (T-074, US-18, ADR-46).
-- Migrations run before fixtures exist, so the migration's statement is re-run here.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(10);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, barcode, name, kcal_100, off_data) values
  -- unreferenced, to delete
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Francia sin referencias', 50,
   '{"countries_tags":["en:france"],"product_name_es":"Francia"}'),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '1000000000002', 'Sin off_data', 50, null),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '1000000000003', 'Espana nombre ingles', 50,
   '{"countries_tags":["en:spain"],"lang":"en","product_name":"Oats"}'),
  -- unreferenced, to keep
  ('0ff00000-0000-4000-8000-000000000004', 'off', '1000000000004', 'Espana con nombre es', 50,
   '{"countries_tags":["en:spain"],"product_name_es":"Avena"}'),
  ('0ff00000-0000-4000-8000-000000000005', 'off', '1000000000005', 'Espana lang es', 50,
   '{"countries_tags":["en:france","en:spain"],"lang":"es","product_name":"Avena"}'),
  -- referenced non-Spain rows, to keep
  ('0ff00000-0000-4000-8000-000000000006', 'off', '1000000000006', 'En el diario de Ana', 50,
   '{"countries_tags":["en:france"]}'),
  ('0ff00000-0000-4000-8000-000000000007', 'off', '1000000000007', 'Favorito de Bea', 50, null),
  ('0ff00000-0000-4000-8000-000000000008', 'off', '1000000000008', 'En una comida', 50,
   '{"countries_tags":["en:germany"]}');
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de Ana', 190, '11111111-1111-4111-8111-111111111111');
insert into public.food_portions (food_id, label, amount, position) values
  ('0ff00000-0000-4000-8000-000000000001', '1 racion', 30, 0),
  ('0ff00000-0000-4000-8000-000000000004', '1 racion', 30, 0);

insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select user_id, '2026-10-21', id, '0ff00000-0000-4000-8000-000000000006', 100, 'En el diario de Ana', 50, 0, 0, 0
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';
insert into public.favorite_foods (user_id, food_id) values
  ('22222222-2222-4222-8222-222222222222', '0ff00000-0000-4000-8000-000000000007');
insert into public.meal_templates (id, user_id, name) values
  ('cccccccc-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Comida de Ana');
insert into public.meal_template_items (template_id, food_id, amount, position) values
  ('cccccccc-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000008', 100, 0);

create temp table before_counts as
select (select count(*) from public.food_log_entries) as entries,
       (select count(*) from public.favorite_foods) as favs,
       (select count(*) from public.meal_template_items) as items;

-- Same statement as the migration, run twice to check idempotency.
delete from public.foods f
where f.source = 'off'
  and not coalesce(
    f.off_data is not null
    and f.off_data -> 'countries_tags' @> '["en:spain"]'::jsonb
    and (
      btrim(coalesce(f.off_data ->> 'product_name_es', '')) <> ''
      or (f.off_data ->> 'lang' = 'es' and btrim(coalesce(f.off_data ->> 'product_name', '')) <> '')
    ),
    false
  )
  and not exists (select 1 from public.food_log_entries e where e.food_id = f.id)
  and not exists (select 1 from public.favorite_foods v where v.food_id = f.id)
  and not exists (select 1 from public.meal_template_items i where i.food_id = f.id);

create temp table after_first as select count(*) as foods from public.foods;

delete from public.foods f
where f.source = 'off'
  and not coalesce(
    f.off_data is not null
    and f.off_data -> 'countries_tags' @> '["en:spain"]'::jsonb
    and (
      btrim(coalesce(f.off_data ->> 'product_name_es', '')) <> ''
      or (f.off_data ->> 'lang' = 'es' and btrim(coalesce(f.off_data ->> 'product_name', '')) <> '')
    ),
    false
  )
  and not exists (select 1 from public.food_log_entries e where e.food_id = f.id)
  and not exists (select 1 from public.favorite_foods v where v.food_id = f.id)
  and not exists (select 1 from public.meal_template_items i where i.food_id = f.id);

select is((select count(*)::int from public.foods
           where id in ('0ff00000-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000002',
                        '0ff00000-0000-4000-8000-000000000003')), 0,
  'unreferenced non-Spain, no-off_data and English-name OFF rows are deleted');
select is((select count(*)::int from public.food_portions where food_id = '0ff00000-0000-4000-8000-000000000001'), 0,
  'portions of a deleted food go with it');
select is((select count(*)::int from public.foods
           where id in ('0ff00000-0000-4000-8000-000000000004', '0ff00000-0000-4000-8000-000000000005')), 2,
  'unreferenced Spain rows with a Spanish name are kept');
select is((select count(*)::int from public.food_portions where food_id = '0ff00000-0000-4000-8000-000000000004'), 1,
  'portions of a kept food survive');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000006'), 1,
  'a non-Spain row referenced by a diary entry is kept');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000007'), 1,
  'a non-Spain row referenced by a favourite is kept');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000008'), 1,
  'a non-Spain row referenced by a template item is kept');
select is((select count(*)::int from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 1,
  'personal foods are untouched');
select is(
  (select row(entries, favs, items)::text from before_counts),
  (select row(
     (select count(*) from public.food_log_entries),
     (select count(*) from public.favorite_foods),
     (select count(*) from public.meal_template_items))::text),
  'diary entries, favourites and template items of every user are unchanged');
select is((select foods from after_first), (select count(*) from public.foods),
  'running the purge a second time deletes nothing');

select * from finish();
rollback;
