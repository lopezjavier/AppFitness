-- pgTAP: public.food_portions constraints and RLS through the parent food (T-055, US-22).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(19);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64);
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de mi madre', 190, '11111111-1111-4111-8111-111111111111');

insert into public.food_portions (id, food_id, label, amount, position) values
  ('0ff0aaaa-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000001', '1 vaso', 200, 0),
  ('aaaa0000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', '1 racion', 150, 0);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select columns_are('public', 'food_portions', array['id', 'food_id', 'label', 'amount', 'position'],
  'food_portions has exactly the planned columns');
select fk_ok('public', 'food_portions', 'food_id', 'public', 'foods', 'id', 'food_id references foods.id');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.food_portions'::regclass),
  'RLS is enabled and forced on food_portions');
select table_privs_are('public', 'food_portions', 'anon', array[]::text[], 'anon has no privileges on food_portions');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('aaaaaaaa-0000-4000-8000-000000000001', 'Duplicada', 100, 0)$$,
  '23505', null, 'a duplicate (food, position) is rejected');
select throws_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('aaaaaaaa-0000-4000-8000-000000000001', 'Cero', 0, 1)$$,
  '23514', null, 'a zero amount is rejected');
select throws_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('aaaaaaaa-0000-4000-8000-000000000001', '  ', 10, 1)$$,
  '23514', null, 'a blank label is rejected');
select throws_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('aaaaaaaa-0000-4000-8000-000000000001', repeat('x', 101), 10, 1)$$,
  '23514', null, 'a 101-character label is rejected');

-- ---------------------------------------------------------------------------
-- RLS (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.food_portions), 2, 'A reads OFF portions and their own');
select lives_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('aaaaaaaa-0000-4000-8000-000000000001', '1 trozo', 80, 1)$$,
  'A adds a portion to their own food');
select throws_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('0ff00000-0000-4000-8000-000000000001', '1 taza', 250, 1)$$,
  '42501', null, 'A cannot add a portion to an OFF food');
select results_eq(
  $$with u as (update public.food_portions set amount = 999 where food_id = '0ff00000-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating OFF portions affects 0 rows');
select results_eq(
  $$with d as (delete from public.food_portions where food_id = '0ff00000-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'A deleting OFF portions affects 0 rows');

-- ---------------------------------------------------------------------------
-- RLS (as user B)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select is((select count(*)::int from public.food_portions), 1, 'B reads only the OFF portion');
select is(
  (select count(*)::int from public.food_portions where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  0, 'B does not see A''s portions');
select throws_ok(
  $$insert into public.food_portions (food_id, label, amount, position)
    values ('aaaaaaaa-0000-4000-8000-000000000001', 'Intruso', 10, 5)$$,
  '42501', null, 'B cannot add a portion to A''s food');
select results_eq(
  $$with u as (update public.food_portions set amount = 999 where food_id = 'aaaaaaaa-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'B updating A''s portions affects 0 rows');
select results_eq(
  $$with d as (delete from public.food_portions where food_id = 'aaaaaaaa-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'B deleting A''s portions affects 0 rows');

reset role;
set local role anon;
select throws_ok('select * from public.food_portions', '42501', null, 'anon cannot read food_portions');
reset role;

select * from finish();
rollback;
