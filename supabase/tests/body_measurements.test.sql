-- pgTAP: public.body_measurements schema, security, constraints and RLS isolation (T-035, US-06).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(30);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- Rows owned by B (and one by C, deleted later to test the cascade).
insert into public.body_measurements (id, user_id, weight_kg, source, external_id) values
  ('bbbbbbbb-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 80, 'manual', null),
  ('cccccccc-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 60, 'manual', null);

-- ---------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'measurement_source', array['manual', 'health_connect'],
  'measurement_source enum labels');
select has_table('public', 'body_measurements', 'public.body_measurements exists');
select columns_are('public', 'body_measurements', array[
  'id', 'user_id', 'measured_at', 'weight_kg', 'body_fat_pct', 'source', 'external_id',
  'created_at', 'updated_at'
], 'body_measurements has exactly the planned columns');
select col_type_is('public', 'body_measurements', 'weight_kg', 'numeric(5,2)', 'weight_kg is numeric(5,2)');
select col_type_is('public', 'body_measurements', 'body_fat_pct', 'numeric(4,1)', 'body_fat_pct is numeric(4,1)');
select fk_ok('public', 'body_measurements', 'user_id', 'auth', 'users', 'id',
  'user_id references auth.users.id');
select has_index('public', 'body_measurements', 'body_measurements_user_id_measured_at_idx',
  'index on (user_id, measured_at desc) exists');
select is(
  (select pg_get_indexdef('public.body_measurements_user_id_measured_at_idx'::regclass)),
  'CREATE INDEX body_measurements_user_id_measured_at_idx ON public.body_measurements USING btree (user_id, measured_at DESC)',
  'the index orders measured_at descending');
select has_trigger('public', 'body_measurements', 'body_measurements_set_updated_at',
  'updated_at trigger exists');

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.body_measurements'::regclass),
  'RLS is enabled and forced on body_measurements'
);
select policies_are('public', 'body_measurements', array[
  'body_measurements_select_own', 'body_measurements_insert_own',
  'body_measurements_update_own', 'body_measurements_delete_own'
], 'body_measurements has the four owner policies');
select table_privs_are('public', 'body_measurements', 'anon', array[]::text[],
  'anon has no privileges on body_measurements');
select table_privs_are('public', 'body_measurements', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on body_measurements');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.body_measurements (user_id, weight_kg, source)
    values ('11111111-1111-4111-8111-111111111111', 19.9, 'manual')$$,
  '23514', null, 'weight_kg 19.9 is rejected');
select throws_ok(
  $$insert into public.body_measurements (user_id, weight_kg, source)
    values ('11111111-1111-4111-8111-111111111111', 400.1, 'manual')$$,
  '23514', null, 'weight_kg 400.1 is rejected');
select throws_ok(
  $$insert into public.body_measurements (user_id, weight_kg, body_fat_pct, source)
    values ('11111111-1111-4111-8111-111111111111', 70, 1.9, 'manual')$$,
  '23514', null, 'body_fat_pct 1.9 is rejected');
select throws_ok(
  $$insert into public.body_measurements (user_id, weight_kg, body_fat_pct, source)
    values ('11111111-1111-4111-8111-111111111111', 70, 75.1, 'manual')$$,
  '23514', null, 'body_fat_pct 75.1 is rejected');
select lives_ok(
  $$insert into public.body_measurements (user_id, weight_kg, body_fat_pct, source)
    values ('11111111-1111-4111-8111-111111111111', 20, 2, 'manual'),
           ('11111111-1111-4111-8111-111111111111', 400, 75, 'manual')$$,
  'inclusive bounds are accepted');
insert into public.body_measurements (user_id, weight_kg, source, external_id)
  values ('22222222-2222-4222-8222-222222222222', 81, 'health_connect', 'hc-1');
select throws_ok(
  $$insert into public.body_measurements (user_id, weight_kg, source, external_id)
    values ('22222222-2222-4222-8222-222222222222', 82, 'health_connect', 'hc-1')$$,
  '23505', null, 'a duplicate (user_id, external_id) is rejected');
delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.body_measurements where user_id = '33333333-3333-4333-8333-333333333333'),
  0,
  'deleting the auth user cascades to its measurements');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

insert into public.body_measurements (id, weight_kg, source)
  values ('aaaaaaaa-0000-4000-8000-000000000001', 74.2, 'manual');
select is(
  (select user_id from public.body_measurements where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  '11111111-1111-4111-8111-111111111111'::uuid,
  'user_id defaults to the caller');
select is(
  (select count(*)::int from public.body_measurements where user_id <> '11111111-1111-4111-8111-111111111111'),
  0,
  'A sees only their own rows');
select throws_ok(
  $$insert into public.body_measurements (user_id, weight_kg, source)
    values ('22222222-2222-4222-8222-222222222222', 70, 'manual')$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.body_measurements set weight_kg = 100
               where id = 'bbbbbbbb-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$,
  'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.body_measurements
               where id = 'bbbbbbbb-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$,
  'A deleting B''s row affects 0 rows');
select throws_ok(
  $$update public.body_measurements set user_id = '22222222-2222-4222-8222-222222222222'
    where id = 'aaaaaaaa-0000-4000-8000-000000000001'$$,
  '42501', null, 'A cannot reassign their row to B');
select throws_ok(
  'truncate public.body_measurements',
  '42501', null, 'A cannot truncate body_measurements');

-- updated_at advances on update (now() is frozen inside the transaction, so backdate first).
reset role;
update public.body_measurements set updated_at = '2000-01-01'
  where id = 'aaaaaaaa-0000-4000-8000-000000000001';
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
update public.body_measurements set weight_kg = 74.3 where id = 'aaaaaaaa-0000-4000-8000-000000000001';
select is(
  (select updated_at from public.body_measurements where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  now(),
  'updated_at advances on update');

reset role;
select is(
  (select weight_kg from public.body_measurements where id = 'bbbbbbbb-0000-4000-8000-000000000001'),
  80.00,
  'B''s row is unchanged after A''s attempts');

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
set local role anon;
select throws_ok(
  'select * from public.body_measurements',
  '42501', null, 'anon cannot read body_measurements');
reset role;

select * from finish();
rollback;
