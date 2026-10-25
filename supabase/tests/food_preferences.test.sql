-- pgTAP: public.food_preferences (olive oil per cooking method) and public.cooking_impact()
-- (T-107, US-50, ADR-60).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(28);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- B has custom amounts; A starts with no row (defaults).
insert into public.food_preferences (user_id, olive_oil_griddle_g, olive_oil_air_fryer_g, olive_oil_fried_g) values
  ('22222222-2222-4222-8222-222222222222', 10, 0, 500);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select has_table('public', 'food_preferences', 'public.food_preferences exists');
select columns_are('public', 'food_preferences',
  array['user_id', 'olive_oil_griddle_g', 'olive_oil_air_fryer_g', 'olive_oil_fried_g', 'updated_at'],
  'food_preferences has exactly the planned columns');
select fk_ok('public', 'food_preferences', 'user_id', 'auth', 'users', 'id',
  'user_id references auth.users.id');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.food_preferences'::regclass),
  'RLS is enabled and forced on food_preferences');
select policies_are('public', 'food_preferences', array[
  'food_preferences_select_own', 'food_preferences_insert_own',
  'food_preferences_update_own', 'food_preferences_delete_own'
], 'food_preferences has the four owner policies');
select table_privs_are('public', 'food_preferences', 'anon', array[]::text[],
  'anon has no privileges on food_preferences');
select table_privs_are('public', 'food_preferences', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on food_preferences');
select is(
  (select prosecdef from pg_proc where proname = 'cooking_impact' and pronamespace = 'public'::regnamespace),
  false, 'cooking_impact is security invoker');
select ok(not has_function_privilege('anon', 'public.cooking_impact(public.food_cooking_method, uuid)', 'execute'),
  'anon cannot execute cooking_impact');

-- ---------------------------------------------------------------------------
-- As A without a row: defaults
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq($$select * from public.cooking_impact('griddle')$$,
  $$values (2.50::numeric, 22.5::numeric, 2.5::numeric)$$, 'griddle defaults: 2.5 g, +22.5 kcal, +2.5 g fat');
select results_eq($$select * from public.cooking_impact('air_fryer')$$,
  $$values (5.00::numeric, 45.0::numeric, 5.0::numeric)$$, 'air fryer defaults: 5 g, +45 kcal, +5 g fat');
select results_eq($$select * from public.cooking_impact('fried')$$,
  $$values (5.00::numeric, 45.0::numeric, 5.0::numeric)$$, 'fried defaults: 5 g, +45 kcal, +5 g fat');
select results_eq($$select * from public.cooking_impact('raw')$$,
  $$values (0::numeric, 0::numeric, 0::numeric)$$, 'raw adds nothing');

-- ---------------------------------------------------------------------------
-- RLS and constraints
-- ---------------------------------------------------------------------------
select lives_ok($$insert into public.food_preferences default values$$,
  'A creates their row; user_id defaults to the caller');
select results_eq(
  $$select olive_oil_griddle_g, olive_oil_air_fryer_g, olive_oil_fried_g from public.food_preferences$$,
  $$values (5.0::numeric, 5.0::numeric, 50.0::numeric)$$, 'the row defaults to 5 / 5 / 50');
select is((select count(*)::int from public.food_preferences), 1, 'A does not see B''s row');
select throws_ok(
  $$insert into public.food_preferences (user_id) values ('22222222-2222-4222-8222-222222222222')$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.food_preferences set olive_oil_fried_g = 1
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1) select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.food_preferences
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'A deleting B''s row affects 0 rows');

select lives_ok(
  $$update public.food_preferences set olive_oil_griddle_g = 0, olive_oil_air_fryer_g = 500$$,
  '0 and 500 are accepted');
select throws_ok($$update public.food_preferences set olive_oil_griddle_g = -1$$,
  '23514', null, 'a negative amount is rejected');
select throws_ok($$update public.food_preferences set olive_oil_fried_g = 500.1$$,
  '23514', null, 'an amount over 500 is rejected');

-- ---------------------------------------------------------------------------
-- cooking_impact with custom amounts
-- ---------------------------------------------------------------------------
update public.food_preferences set olive_oil_griddle_g = 10, olive_oil_air_fryer_g = 5, olive_oil_fried_g = 500;
select results_eq($$select * from public.cooking_impact('griddle')$$,
  $$values (5.00::numeric, 45.0::numeric, 5.0::numeric)$$, 'griddle 10 g: retained 5 g, +45 kcal, +5 g fat');
select results_eq($$select * from public.cooking_impact('fried')$$,
  $$values (50.00::numeric, 450.0::numeric, 50.0::numeric)$$, 'fried 500 g: retained 50 g, +450 kcal, +50 g fat');
update public.food_preferences set olive_oil_griddle_g = 0;
select results_eq($$select * from public.cooking_impact('griddle')$$,
  $$values (0::numeric, 0::numeric, 0::numeric)$$, 'griddle 0 g adds nothing');
select results_eq(
  $$select * from public.cooking_impact('fried', '22222222-2222-4222-8222-222222222222')$$,
  $$values (5.00::numeric, 45.0::numeric, 5.0::numeric)$$,
  'another user''s amounts are not used (RLS hides them, defaults apply)');

reset role;
set local role anon;
select throws_ok('select * from public.food_preferences', '42501', null, 'anon cannot read food_preferences');
reset role;

-- ---------------------------------------------------------------------------
-- Cascade
-- ---------------------------------------------------------------------------
delete from auth.users where id = '22222222-2222-4222-8222-222222222222';
select is((select count(*)::int from public.food_preferences where user_id = '22222222-2222-4222-8222-222222222222'),
  0, 'deleting the user deletes their preferences');

select * from finish();
rollback;
