-- pgTAP: public.nutrition_goals schema, security, constraints and RLS isolation (T-036, US-07).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(29);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- Rows owned by B (and one by C, deleted later to test the cascade).
insert into public.nutrition_goals (user_id, target_kcal) values
  ('22222222-2222-4222-8222-222222222222', 2500),
  ('33333333-3333-4333-8333-333333333333', 2000);

-- ---------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'goal_strategy', array['deficit', 'maintenance', 'surplus'],
  'goal_strategy enum labels');
select has_table('public', 'nutrition_goals', 'public.nutrition_goals exists');
select columns_are('public', 'nutrition_goals', array[
  'user_id', 'strategy', 'calorie_adjustment', 'target_kcal',
  'protein_pct', 'carbs_pct', 'fat_pct', 'created_at', 'updated_at'
], 'nutrition_goals has exactly the planned columns');
select fk_ok('public', 'nutrition_goals', 'user_id', 'auth', 'users', 'id',
  'user_id references auth.users.id');
select has_pk('public', 'nutrition_goals', 'nutrition_goals has a primary key');
select has_trigger('public', 'nutrition_goals', 'nutrition_goals_set_updated_at',
  'updated_at trigger exists');
select results_eq(
  $$ select strategy::text, calorie_adjustment, protein_pct::int, carbs_pct::int, fat_pct::int
     from public.nutrition_goals where user_id = '22222222-2222-4222-8222-222222222222' $$,
  $$ values ('maintenance', 0, 30, 45, 25) $$,
  'defaults are maintenance, 0 and a 30/45/25 split');

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.nutrition_goals'::regclass),
  'RLS is enabled and forced on nutrition_goals'
);
select policies_are('public', 'nutrition_goals', array[
  'nutrition_goals_select_own', 'nutrition_goals_insert_own',
  'nutrition_goals_update_own', 'nutrition_goals_delete_own'
], 'nutrition_goals has the four owner policies');
select table_privs_are('public', 'nutrition_goals', 'anon', array[]::text[],
  'anon has no privileges on nutrition_goals');
select table_privs_are('public', 'nutrition_goals', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on nutrition_goals');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, protein_pct, carbs_pct, fat_pct)
    values ('11111111-1111-4111-8111-111111111111', 2000, 4, 71, 25)$$,
  '23514', null, 'protein_pct 4 is rejected');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, protein_pct, carbs_pct, fat_pct)
    values ('11111111-1111-4111-8111-111111111111', 2000, 91, 5, 5)$$,
  '23514', null, 'protein_pct 91 is rejected');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, protein_pct, carbs_pct, fat_pct)
    values ('11111111-1111-4111-8111-111111111111', 2000, 30, 46, 25)$$,
  '23514', null, 'a split summing to 101 is rejected');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, calorie_adjustment)
    values ('11111111-1111-4111-8111-111111111111', 2000, -1001)$$,
  '23514', null, 'calorie_adjustment -1001 is rejected');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, calorie_adjustment)
    values ('11111111-1111-4111-8111-111111111111', 2000, 751)$$,
  '23514', null, 'calorie_adjustment 751 is rejected');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal)
    values ('11111111-1111-4111-8111-111111111111', 0)$$,
  '23514', null, 'target_kcal 0 is rejected');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, strategy)
    values ('11111111-1111-4111-8111-111111111111', 2000, 'bulk')$$,
  '22P02', null, 'an unknown strategy is rejected');
select lives_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal, strategy, calorie_adjustment, protein_pct, carbs_pct, fat_pct)
    values ('11111111-1111-4111-8111-111111111111', 1200, 'deficit', -1000, 5, 90, 5)$$,
  'inclusive bounds are accepted');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal)
    values ('11111111-1111-4111-8111-111111111111', 2100)$$,
  '23505', null, 'a second row for the same user is rejected');
delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.nutrition_goals where user_id = '33333333-3333-4333-8333-333333333333'),
  0,
  'deleting the auth user cascades to its goals');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  (select count(*)::int from public.nutrition_goals where user_id <> '11111111-1111-4111-8111-111111111111'),
  0,
  'A sees only their own row');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal)
    values ('22222222-2222-4222-8222-222222222222', 2000)$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.nutrition_goals set target_kcal = 1
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$,
  'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.nutrition_goals
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$,
  'A deleting B''s row affects 0 rows');
select throws_ok(
  'truncate public.nutrition_goals',
  '42501', null, 'A cannot truncate nutrition_goals');

reset role;
select is(
  (select target_kcal from public.nutrition_goals where user_id = '22222222-2222-4222-8222-222222222222'),
  2500,
  'B''s row is unchanged after A''s attempts');

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
set local role anon;
select throws_ok(
  'select * from public.nutrition_goals',
  '42501', null, 'anon cannot read nutrition_goals');
select throws_ok(
  $$insert into public.nutrition_goals (user_id, target_kcal)
    values ('11111111-1111-4111-8111-111111111111', 2000)$$,
  '42501', null, 'anon cannot insert into nutrition_goals');
reset role;

select * from finish();
rollback;
