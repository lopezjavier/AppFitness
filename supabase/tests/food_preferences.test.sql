-- pgTAP: public.food_preferences (added-fat percentage per cooking method) and public.cooking_impact()
-- (T-107 / T-112, US-50, ADR-60 / ADR-65).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(29);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- B has custom percentages; A starts with no row (defaults).
insert into public.food_preferences (user_id, fat_pct_griddle, fat_pct_air_fryer, fat_pct_fried) values
  ('22222222-2222-4222-8222-222222222222', 10, 0, 20);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select has_table('public', 'food_preferences', 'public.food_preferences exists');
select columns_are('public', 'food_preferences',
  array['user_id', 'fat_pct_griddle', 'fat_pct_air_fryer', 'fat_pct_fried', 'updated_at'],
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
select ok(not has_function_privilege('anon', 'public.cooking_impact(public.food_cooking_method, numeric, uuid)', 'execute'),
  'anon cannot execute cooking_impact');

-- ---------------------------------------------------------------------------
-- As A without a row: defaults 1 / 2 / 8 % of the raw weight
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq($$select * from public.cooking_impact('griddle', 300)$$,
  $$values (3.0::numeric, 27.0::numeric)$$, 'griddle default 1 % of 300 g: +3 g fat, +27 kcal');
select results_eq($$select * from public.cooking_impact('air_fryer', 300)$$,
  $$values (6.0::numeric, 54.0::numeric)$$, 'air fryer default 2 % of 300 g: +6 g fat, +54 kcal');
select results_eq($$select * from public.cooking_impact('fried', 300)$$,
  $$values (24.0::numeric, 216.0::numeric)$$, 'fried default 8 % of 300 g: +24 g fat, +216 kcal');
select results_eq($$select * from public.cooking_impact('raw', 300)$$,
  $$values (0::numeric, 0::numeric)$$, 'raw adds nothing');
select results_eq($$select * from public.cooking_impact('griddle', 175)$$,
  $$values (1.8::numeric, 16.2::numeric)$$, '175 g at 1 % rounds the fat to tenths (1.75 -> 1.8) and kcal from that fat');

-- ---------------------------------------------------------------------------
-- RLS and constraints
-- ---------------------------------------------------------------------------
select lives_ok($$insert into public.food_preferences default values$$,
  'A creates their row; user_id defaults to the caller');
select results_eq(
  $$select fat_pct_griddle, fat_pct_air_fryer, fat_pct_fried from public.food_preferences$$,
  $$values (1.0::numeric, 2.0::numeric, 8.0::numeric)$$, 'the row defaults to 1 / 2 / 8');
select is((select count(*)::int from public.food_preferences), 1, 'A does not see B''s row');
select throws_ok(
  $$insert into public.food_preferences (user_id) values ('22222222-2222-4222-8222-222222222222')$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.food_preferences set fat_pct_fried = 1
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1) select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.food_preferences
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'A deleting B''s row affects 0 rows');

select lives_ok(
  $$update public.food_preferences set fat_pct_griddle = 0, fat_pct_air_fryer = 20$$,
  '0 and 20 are accepted');
select throws_ok($$update public.food_preferences set fat_pct_griddle = -0.1$$,
  '23514', null, 'a negative percentage is rejected');
select throws_ok($$update public.food_preferences set fat_pct_fried = 20.1$$,
  '23514', null, 'a percentage over 20 is rejected');

-- ---------------------------------------------------------------------------
-- cooking_impact with custom percentages
-- ---------------------------------------------------------------------------
update public.food_preferences set fat_pct_griddle = 10, fat_pct_air_fryer = 2, fat_pct_fried = 20;
select results_eq($$select * from public.cooking_impact('griddle', 300)$$,
  $$values (30.0::numeric, 270.0::numeric)$$, 'griddle 10 % of 300 g: +30 g fat, +270 kcal');
select results_eq($$select * from public.cooking_impact('fried', 200)$$,
  $$values (40.0::numeric, 360.0::numeric)$$, 'fried 20 % of 200 g: +40 g fat, +360 kcal');
update public.food_preferences set fat_pct_griddle = 0;
select results_eq($$select * from public.cooking_impact('griddle', 300)$$,
  $$values (0::numeric, 0::numeric)$$, 'griddle 0 % adds nothing');
select results_eq(
  $$select * from public.cooking_impact('fried', 100, '22222222-2222-4222-8222-222222222222')$$,
  $$values (8.0::numeric, 72.0::numeric)$$,
  'another user''s percentages are not used (RLS hides them, defaults apply)');

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
