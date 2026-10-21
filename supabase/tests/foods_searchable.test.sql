-- pgTAP: foods_searchable view and is_spain_off_food (T-074, US-18, ADR-46).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(14);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.foods (id, source, barcode, name, kcal_100, off_data) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'zzq es', 50, '{"countries_tags":["en:spain"],"product_name_es":"x"}'),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '1000000000002', 'zzq francia', 50, '{"countries_tags":["en:france"],"product_name_es":"x"}'),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '1000000000003', 'zzq ingles', 50, '{"countries_tags":["en:spain"],"lang":"en","product_name":"x"}'),
  ('0ff00000-0000-4000-8000-000000000004', 'off', '1000000000004', 'zzq lang es', 50, '{"countries_tags":["en:france","en:spain"],"lang":"es","product_name":"x"}'),
  ('0ff00000-0000-4000-8000-000000000005', 'off', '1000000000005', 'zzq blanco', 50, '{"countries_tags":["en:spain"],"product_name_es":"   "}'),
  ('0ff00000-0000-4000-8000-000000000006', 'off', '1000000000006', 'zzq sin datos', 50, null),
  ('0ff00000-0000-4000-8000-000000000007', 'off', '1000000000007', 'zzq tags texto', 50, '{"countries_tags":"en:spain","product_name_es":"x"}');
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'zzq tortilla de Ana', 190, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000002', 'user', 'zzq tortilla de Bea', 190, '22222222-2222-4222-8222-222222222222');

select is(public.is_spain_off_food('{"countries_tags":["en:spain"],"product_name_es":"Avena"}'), true, 'Spain + product_name_es is visible');
select is(public.is_spain_off_food('{"countries_tags":["en:spain"],"lang":"es","product_name":"Avena"}'), true, 'Spain + lang es + product_name is visible');
select is(public.is_spain_off_food('{"countries_tags":["en:spain"],"lang":"en","product_name":"Oats"}'), false, 'Spain with an English name is hidden');
select is(public.is_spain_off_food('{"countries_tags":["en:spain"],"product_name_es":"  "}'), false, 'a whitespace-only Spanish name is hidden');
select is(public.is_spain_off_food('{"countries_tags":"en:spain","product_name_es":"x"}'), false, 'non-array countries_tags is hidden');
select is(public.is_spain_off_food(null), false, 'null off_data is hidden');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select array_agg(name order by name) from public.foods_searchable where name like 'zzq%'),
  array['zzq es', 'zzq lang es', 'zzq tortilla de Ana'],
  'the view shows Spain OFF foods with a Spanish name and the caller personal foods only');
select is((select count(*)::int from public.foods_searchable where name = 'zzq tortilla de Bea'), 0,
  'another user personal food is not visible (RLS stays in force)');
select is((select count(*)::int from public.foods_searchable where search_vector @@ websearch_to_tsquery('spanish', 'tortilla')), 1,
  'the search_vector column works through the view');
select is((select count(*)::int from information_schema.columns
           where table_schema = 'public' and table_name = 'foods_searchable' and column_name = 'off_data'), 0,
  'off_data is not exposed by the view');

reset role;
select is(has_table_privilege('anon', 'public.foods_searchable', 'select'), false, 'anon cannot select the view');
select is(has_table_privilege('authenticated', 'public.foods_searchable', 'select'), true, 'authenticated can select the view');
select is(has_table_privilege('authenticated', 'public.foods_searchable', 'insert'), false, 'authenticated cannot write through the view');
select is(has_function_privilege('anon', 'public.is_spain_off_food(jsonb)', 'execute'), false, 'anon cannot execute the predicate');

select * from finish();
rollback;
