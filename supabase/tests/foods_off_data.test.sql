-- pgTAP: foods.off_data / foods.micronutrients columns, constraints, cache cleanup, RLS and the
-- p_micronutrients argument of create_personal_food (T-068, US-25, ADR-43).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(37);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

-- Fixtures for the cache cleanup: old OFF rows without off_data, referenced in different ways.
insert into public.foods (id, source, barcode, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Sin referencias', 50),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '1000000000002', 'En el diario', 50),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '1000000000003', 'Favorito', 50),
  ('0ff00000-0000-4000-8000-000000000004', 'off', '1000000000004', 'En una comida', 50);
insert into public.foods (id, source, barcode, name, kcal_100, off_data) values
  ('0ff00000-0000-4000-8000-000000000005', 'off', '1000000000005', 'Con datos', 50,
   '{"code":"1000000000005","nutriments":{"fiber_100g":2.5}}');
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de Ana', 190, '11111111-1111-4111-8111-111111111111');
insert into public.food_portions (food_id, label, amount, position) values
  ('0ff00000-0000-4000-8000-000000000001', '1 racion', 30, 0),
  ('0ff00000-0000-4000-8000-000000000002', '1 racion', 30, 0);
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select 'e1000000-0000-4000-8000-000000000001', user_id, '2026-10-21', id, '0ff00000-0000-4000-8000-000000000002',
         100, 'En el diario', 50, 0, 0, 0
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';
insert into public.favorite_foods (user_id, food_id) values
  ('11111111-1111-4111-8111-111111111111', '0ff00000-0000-4000-8000-000000000003');
insert into public.meal_templates (id, user_id, name) values
  ('cccccccc-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Desayuno');
insert into public.meal_template_items (template_id, food_id, amount, position) values
  ('cccccccc-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000004', 100, 0);

-- ---------------------------------------------------------------------------
-- Schema and constraints
-- ---------------------------------------------------------------------------
select has_column('public', 'foods', 'off_data', 'foods has off_data');
select has_column('public', 'foods', 'micronutrients', 'foods has micronutrients');
select col_is_null('public', 'foods', 'off_data', 'off_data is nullable');
select col_is_null('public', 'foods', 'micronutrients', 'micronutrients is nullable');

select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, off_data)
    values ('user', 'Casera', 10, '11111111-1111-4111-8111-111111111111', '{"code":"1"}')$$,
  '23514', null, 'off_data is rejected on a personal food');
select throws_ok(
  $$insert into public.foods (source, barcode, name, kcal_100, micronutrients)
    values ('off', '2000000000001', 'Con etiqueta', 10, '{"fiber_100g":1}')$$,
  '23514', null, 'micronutrients is rejected on an OFF food');
select throws_ok(
  $$insert into public.foods (source, barcode, name, kcal_100, off_data)
    values ('off', '2000000000002', 'Array', 10, '[1,2]')$$,
  '23514', null, 'off_data must be a JSON object, not an array');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, micronutrients)
    values ('user', 'Array', 10, '11111111-1111-4111-8111-111111111111', '[1]')$$,
  '23514', null, 'micronutrients must be a JSON object, not an array');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, micronutrients)
    values ('user', 'Clave', 10, '11111111-1111-4111-8111-111111111111', '{"vitamin-c_100g":1}')$$,
  '23514', null, 'micronutrients rejects unknown keys');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, micronutrients)
    values ('user', 'Negativo', 10, '11111111-1111-4111-8111-111111111111', '{"fiber_100g":-0.1}')$$,
  '23514', null, 'micronutrients rejects negative values');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, micronutrients)
    values ('user', 'Texto', 10, '11111111-1111-4111-8111-111111111111', '{"fiber_100g":"abc"}')$$,
  '23514', null, 'micronutrients rejects non-numeric values');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, micronutrients)
    values ('user', 'Enorme', 10, '11111111-1111-4111-8111-111111111111', '{"salt_100g":10000}')$$,
  '23514', null, 'micronutrients rejects values above 9999.99');
select lives_ok(
  $$insert into public.foods (source, name, kcal_100, created_by, micronutrients)
    values ('user', 'Valida', 10, '11111111-1111-4111-8111-111111111111',
            '{"saturated-fat_100g":1.2,"sugars_100g":4,"fiber_100g":0,"salt_100g":9999.99}')$$,
  'a personal food accepts the four label values');
select lives_ok(
  $$insert into public.foods (source, barcode, name, kcal_100, off_data)
    values ('off', '2000000000003', 'Con off_data', 10, '{"code":"2000000000003","ingredients_text":"agua"}')$$,
  'an OFF food accepts a JSON object as off_data');

-- ---------------------------------------------------------------------------
-- Cache cleanup (same statement as the migration)
-- ---------------------------------------------------------------------------
delete from public.foods f
where f.source = 'off'
  and f.off_data is null
  and not exists (select 1 from public.food_log_entries e where e.food_id = f.id)
  and not exists (select 1 from public.favorite_foods ff where ff.food_id = f.id)
  and not exists (select 1 from public.meal_template_items i where i.food_id = f.id);

select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000001'), 0,
  'cleanup deletes an unreferenced OFF food without off_data');
select is((select count(*)::int from public.food_portions where food_id = '0ff00000-0000-4000-8000-000000000001'), 0,
  'cleanup also removes its portions');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000002'), 1,
  'cleanup keeps a food referenced by a log entry (same id)');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000003'), 1,
  'cleanup keeps a favourite food (same id)');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000004'), 1,
  'cleanup keeps a food used by a saved meal (same id)');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000005'), 1,
  'cleanup keeps an OFF food that already has off_data');
select is((select count(*)::int from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 1,
  'cleanup never touches personal foods');
select is(
  (select (select count(*) from public.food_log_entries)::int * 100
        + (select count(*) from public.favorite_foods)::int * 10
        + (select count(*) from public.meal_template_items)::int),
  111, 'entries, favourites and saved-meal items are untouched');
select is((select count(*)::int from public.food_portions where food_id = '0ff00000-0000-4000-8000-000000000002'), 1,
  'portions of kept foods are untouched');

-- ---------------------------------------------------------------------------
-- RLS: unchanged policies
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
set local role authenticated;

select is((select count(*)::int from public.foods where name = 'Valida'), 0,
  'user B cannot read user A''s personal micronutrients');
select is((select off_data -> 'nutriments' ->> 'fiber_100g' from public.foods
           where id = '0ff00000-0000-4000-8000-000000000005'), '2.5',
  'an authenticated user can read the OFF off_data nutriments');

reset role;
set local role anon;
select throws_ok($$select off_data from public.foods$$, '42501', null, 'anon cannot read foods');
reset role;

-- ---------------------------------------------------------------------------
-- create_personal_food with p_micronutrients
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

create temp table result (k text, id uuid);
grant all on result to authenticated;

insert into result select 'full', public.create_personal_food('Con etiqueta RPC', null, 'g', 100, 1, 2, 3, null, '[]'::jsonb,
  '{"saturated-fat_100g":1.5,"sugars_100g":4,"fiber_100g":0,"salt_100g":0.5}'::jsonb);
select is(
  (select micronutrients from public.foods where id = (select id from result where k = 'full')),
  '{"saturated-fat_100g":1.5,"sugars_100g":4,"fiber_100g":0,"salt_100g":0.5}'::jsonb,
  'the RPC stores the four label values');

insert into result select 'empty', public.create_personal_food('Etiqueta vacia', null, 'g', 100, 1, 2, 3, null, '[]'::jsonb, '{}'::jsonb);
select is((select micronutrients from public.foods where id = (select id from result where k = 'empty')), null,
  'an empty object is stored as null');

insert into result select 'partial', public.create_personal_food('Solo fibra', null, 'ml', 10, 0, 0, 0, null, '[]'::jsonb,
  '{"fiber_100g":3}'::jsonb);
select is((select micronutrients from public.foods where id = (select id from result where k = 'partial')),
  '{"fiber_100g":3}'::jsonb, 'a single typed value is stored alone');

insert into result select 'legacy', public.create_personal_food('Sin argumento', null, 'g', 100, 1, 2, 3, null, '[]'::jsonb);
select is((select micronutrients from public.foods where id = (select id from result where k = 'legacy')), null,
  'the legacy 9-argument call still works and stores null');

select throws_ok(
  $$select public.create_personal_food('Mala 1', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"vitamin-c_100g":1}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'an unknown key is rejected');
select throws_ok(
  $$select public.create_personal_food('Mala 2', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"salt_100g":-1}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'a negative value is rejected');
select throws_ok(
  $$select public.create_personal_food('Mala 3', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"salt_100g":"abc"}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'a non-numeric value is rejected');
select throws_ok(
  $$select public.create_personal_food('Mala 4', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"salt_100g":10000}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'a value above 9999.99 is rejected');
select throws_ok(
  $$select public.create_personal_food('Mala 5', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '[1]'::jsonb)$$,
  '22023', 'invalid micronutrients', 'a non-object argument is rejected');
select is((select count(*)::int from public.foods where name like 'Mala %'), 0,
  'rejected calls leave no food row');

select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.create_personal_food('Anonima', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"fiber_100g":1}'::jsonb)$$,
  '28000', 'not authenticated', 'an unauthenticated call is rejected');

select * from finish();
rollback;
