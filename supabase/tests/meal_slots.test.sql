-- pgTAP: public.meal_slots RLS, constraints and default-meal seeding (T-055, US-23).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(24);

-- The on_auth_user_created trigger creates each profile (onboarding_completed = false: no slots yet).
insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select columns_are('public', 'meal_slots', array['id', 'user_id', 'code', 'label', 'position', 'enabled'],
  'meal_slots has exactly the planned columns');
select fk_ok('public', 'meal_slots', 'user_id', 'auth', 'users', 'id', 'user_id references auth.users.id');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.meal_slots'::regclass),
  'RLS is enabled and forced on meal_slots');
select table_privs_are('public', 'meal_slots', 'anon', array[]::text[], 'anon has no privileges on meal_slots');
select table_privs_are('public', 'meal_slots', 'authenticated', array['DELETE', 'SELECT', 'INSERT', 'UPDATE'],
  'authenticated can select, insert, update and delete own meal_slots (delete only via delete_meal_slot rules)');

-- ---------------------------------------------------------------------------
-- Seeding
-- ---------------------------------------------------------------------------
select is(
  (select count(*)::int from public.meal_slots where user_id in (
    '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222')),
  0, 'users who have not onboarded get no meal slots');

update public.profiles set onboarding_completed = true where id = '11111111-1111-4111-8111-111111111111';
select results_eq(
  $$select label, code::text, position::int, enabled from public.meal_slots
    where user_id = '11111111-1111-4111-8111-111111111111' order by position$$,
  $$values ('Desayuno', 'breakfast', 0, true), ('Comida', 'lunch', 1, true),
           ('Merienda', 'snack', 2, true), ('Cena', 'dinner', 3, true)$$,
  'completing onboarding seeds the four default meals in order');

update public.profiles set onboarding_completed = true, display_name = 'Ana M'
  where id = '11111111-1111-4111-8111-111111111111';
update public.profiles set onboarding_completed = false where id = '11111111-1111-4111-8111-111111111111';
update public.profiles set onboarding_completed = true where id = '11111111-1111-4111-8111-111111111111';
select is(
  (select count(*)::int from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111'),
  4, 'repeated onboarding updates keep exactly four meal slots');

select is(
  (select count(*)::int from public.meal_slots where user_id = '22222222-2222-4222-8222-222222222222'),
  0, 'a not-onboarded user still has none');

select lives_ok(
  $$select public.seed_default_meal_slots('11111111-1111-4111-8111-111111111111')$$,
  'running the backfill function again does not fail');
select is(
  (select count(*)::int from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111'),
  4, 'the backfill is idempotent');

-- A profile that is already onboarded when its slots are missing (pre-migration user) is repaired by the backfill.
delete from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111';
select public.seed_default_meal_slots('11111111-1111-4111-8111-111111111111');
select is(
  (select count(*)::int from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111'),
  4, 'the backfill restores the default meals of an onboarded user');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.meal_slots (user_id, label, position)
    values ('11111111-1111-4111-8111-111111111111', 'Duplicada', 0)$$,
  '23505', null, 'a duplicate (user, position) is rejected');
select throws_ok(
  $$insert into public.meal_slots (user_id, label, position)
    values ('22222222-2222-4222-8222-222222222222', '  ', 0)$$,
  '23514', null, 'a blank label is rejected');
select throws_ok(
  $$insert into public.meal_slots (user_id, label, position)
    values ('22222222-2222-4222-8222-222222222222', repeat('x', 51), 0)$$,
  '23514', null, 'a 51-character label is rejected');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A, then B)
-- ---------------------------------------------------------------------------
select public.seed_default_meal_slots('22222222-2222-4222-8222-222222222222');
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.meal_slots), 4, 'A sees only their own four slots');
select lives_ok(
  $$insert into public.meal_slots (label, position) values ('Pre-entreno', 4)$$,
  'A adds a custom meal');
select throws_ok(
  $$insert into public.meal_slots (user_id, label, position)
    values ('22222222-2222-4222-8222-222222222222', 'Para B', 9)$$,
  '42501', null, 'A cannot create a slot for B');
select lives_ok(
  $$update public.meal_slots set enabled = false where code = 'snack'$$,
  'A disables a meal');
select lives_ok(
  $$delete from public.meal_slots where user_id = '22222222-2222-4222-8222-222222222222'$$,
  'A deleting B''s slots is a silent no-op (RLS hides them)');
reset role;
select is(
  (select count(*)::int from public.meal_slots where user_id = '22222222-2222-4222-8222-222222222222'),
  4, 'B''s meal slots are still there after A''s delete attempt');
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select set_config('request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}', true);
select is((select count(*)::int from public.meal_slots), 0, 'C does not see A''s slots');
select results_eq(
  $$with u as (update public.meal_slots set label = 'Hackeado' returning 1) select count(*)::int from u$$,
  $$values (0)$$, 'C updating A''s slots affects 0 rows');

reset role;
set local role anon;
select throws_ok('select * from public.meal_slots', '42501', null, 'anon cannot read meal_slots');
reset role;

select * from finish();
rollback;
