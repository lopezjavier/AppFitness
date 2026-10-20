-- pgTAP: public.foods schema, constraints, search vector and RLS isolation (T-055, US-16, ADR-29).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(34);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.foods (id, source, barcode, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '8410000810004', 'Copos de avena', 'Hacendado', 'g', 370, 13, 60, 7);
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de mi madre', 190, '11111111-1111-4111-8111-111111111111');

-- ---------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'meal_type', array['breakfast', 'lunch', 'snack', 'dinner'], 'meal_type enum labels');
select enum_has_labels('public', 'food_source', array['off', 'user'], 'food_source enum labels');
select enum_has_labels('public', 'food_base_unit', array['g', 'ml'], 'food_base_unit enum labels');
select columns_are('public', 'foods', array[
  'id', 'source', 'barcode', 'off_id', 'name', 'brand', 'base_unit', 'kcal_100', 'protein_100', 'carbs_100',
  'fat_100', 'serving_label', 'serving_amount', 'image_url', 'created_by', 'search_vector', 'fetched_at',
  'created_at', 'updated_at'
], 'foods has exactly the planned columns');
select fk_ok('public', 'foods', 'created_by', 'auth', 'users', 'id', 'created_by references auth.users.id');
select has_index('public', 'foods', 'foods_search_vector_idx', 'GIN index on search_vector exists');
select has_index('public', 'foods', 'foods_created_by_idx', 'index on created_by exists');
select has_index('public', 'foods', 'foods_off_barcode_key', 'unique OFF barcode index exists');

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.foods'::regclass),
  'RLS is enabled and forced on foods');
select policies_are('public', 'foods', array[
  'foods_select', 'foods_insert_own', 'foods_update_own', 'foods_delete_own'
], 'foods has the four policies');
select table_privs_are('public', 'foods', 'anon', array[]::text[], 'anon has no privileges on foods');
select table_privs_are('public', 'foods', 'authenticated', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on foods');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.foods (source, barcode, name, kcal_100) values ('off', '8410000810004', 'Duplicado', 100)$$,
  '23505', null, 'a duplicate OFF barcode is rejected');
select lives_ok(
  $$insert into public.foods (source, barcode, name, kcal_100, created_by)
    values ('user', '8410000810004', 'Mi avena con codigo', 100, '11111111-1111-4111-8111-111111111111')$$,
  'a personal food may reuse an OFF barcode');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100) values ('user', 'Sin dueno', 100)$$,
  '23514', null, 'a user food without created_by is rejected');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by)
    values ('off', 'Off con dueno', 100, '11111111-1111-4111-8111-111111111111')$$,
  '23514', null, 'an off food with created_by is rejected');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100) values ('off', '   ', 100)$$,
  '23514', null, 'a blank name is rejected');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100) values ('off', repeat('x', 201), 100)$$,
  '23514', null, 'a 201-character name is rejected');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100) values ('off', 'Negativo', -1)$$,
  '23514', null, 'a negative nutrient is rejected');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, serving_amount) values ('off', 'Racion cero', 100, 0)$$,
  '23514', null, 'a zero serving amount is rejected');

select is(
  (select count(*)::int from public.foods
    where search_vector @@ websearch_to_tsquery('spanish', public.immutable_unaccent('Tortilla'))),
  1, 'the search vector finds a food by name, ignoring case');
select is(
  (select count(*)::int from public.foods
    where search_vector @@ websearch_to_tsquery('spanish', public.immutable_unaccent('hacendado'))),
  1, 'the search vector also covers the brand');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  (select count(*)::int from public.foods
    where search_vector @@ websearch_to_tsquery('spanish', public.immutable_unaccent('tortilla'))),
  1, 'A finds their personal food by search');
select is((select count(*)::int from public.foods where source = 'off'), 1, 'A reads OFF foods');
insert into public.foods (id, source, name, kcal_100) values
  ('aaaaaaaa-0000-4000-8000-000000000002', 'user', 'Batido casero', 80);
select is(
  (select created_by from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000002'),
  '11111111-1111-4111-8111-111111111111'::uuid, 'created_by defaults to the caller');
select throws_ok(
  $$insert into public.foods (source, name, kcal_100, created_by)
    values ('user', 'Para B', 100, '22222222-2222-4222-8222-222222222222')$$,
  '42501', null, 'A cannot create a food owned by B');
select throws_ok(
  $$insert into public.foods (source, barcode, name, kcal_100) values ('off', '1234567890128', 'Falso OFF', 100)$$,
  '42501', null, 'A cannot insert an off food');
select throws_ok(
  $$update public.foods set source = 'off', created_by = null where id = 'aaaaaaaa-0000-4000-8000-000000000001'$$,
  '42501', null, 'A cannot turn their food into an off food');
select results_eq(
  $$with u as (update public.foods set name = 'Hackeado' where source = 'off' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating OFF foods affects 0 rows');
select throws_ok('truncate public.foods', '42501', null, 'A cannot truncate foods');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user B) and anonymous
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select is(
  (select count(*)::int from public.foods
    where search_vector @@ websearch_to_tsquery('spanish', public.immutable_unaccent('tortilla'))),
  0, 'B does not find A''s food by search');
select results_eq(
  $$with u as (update public.foods set name = 'Hackeado' where created_by = '11111111-1111-4111-8111-111111111111' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'B updating A''s foods affects 0 rows');
select results_eq(
  $$with d as (delete from public.foods where created_by = '11111111-1111-4111-8111-111111111111' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'B deleting A''s foods affects 0 rows');

reset role;
set local role anon;
select throws_ok('select * from public.foods', '42501', null, 'anon cannot read foods');
reset role;

select * from finish();
rollback;
