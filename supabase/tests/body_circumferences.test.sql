-- pgTAP: public.body_circumferences schema, security, constraints and RLS isolation (T-078, US-25).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(31);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

insert into public.body_circumferences (id, user_id, site, value_cm) values
  ('bbbbbbbb-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'waist', 80),
  ('cccccccc-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'hip', 95);

-- Schema
select enum_has_labels('public', 'circumference_site',
  array['neck', 'chest', 'waist', 'hip', 'arm', 'thigh', 'calf'],
  'circumference_site enum labels in order');
select has_table('public', 'body_circumferences', 'public.body_circumferences exists');
select columns_are('public', 'body_circumferences', array[
  'id', 'user_id', 'measured_at', 'site', 'value_cm', 'source', 'created_at', 'updated_at'
], 'body_circumferences has exactly the planned columns');
select col_type_is('public', 'body_circumferences', 'value_cm', 'numeric(5,1)', 'value_cm is numeric(5,1)');
select fk_ok('public', 'body_circumferences', 'user_id', 'auth', 'users', 'id',
  'user_id references auth.users.id');
select is(
  (select pg_get_indexdef('public.body_circumferences_user_id_site_measured_at_idx'::regclass)),
  'CREATE INDEX body_circumferences_user_id_site_measured_at_idx ON public.body_circumferences USING btree (user_id, site, measured_at DESC)',
  'the index covers (user_id, site, measured_at desc)');
select has_trigger('public', 'body_circumferences', 'body_circumferences_set_updated_at',
  'updated_at trigger exists');

-- Security
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.body_circumferences'::regclass),
  'RLS is enabled and forced on body_circumferences');
select policies_are('public', 'body_circumferences', array[
  'body_circumferences_select_own', 'body_circumferences_insert_own',
  'body_circumferences_update_own', 'body_circumferences_delete_own'
], 'body_circumferences has the four owner policies');
select table_privs_are('public', 'body_circumferences', 'anon', array[]::text[],
  'anon has no privileges on body_circumferences');
select table_privs_are('public', 'body_circumferences', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on body_circumferences');

-- Constraints (as postgres)
select throws_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm)
    values ('11111111-1111-4111-8111-111111111111', 'waist', 19.9)$$,
  '23514', null, 'value_cm 19.9 is rejected');
select throws_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm)
    values ('11111111-1111-4111-8111-111111111111', 'waist', 250.1)$$,
  '23514', null, 'value_cm 250.1 is rejected');
select throws_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm)
    values ('11111111-1111-4111-8111-111111111111', 'waist', 251)$$,
  '23514', null, 'value_cm 251 is rejected');
select lives_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm)
    values ('11111111-1111-4111-8111-111111111111', 'neck', 20),
           ('11111111-1111-4111-8111-111111111111', 'chest', 250)$$,
  'inclusive bounds 20 and 250 are accepted');
insert into public.body_circumferences (id, user_id, site, value_cm)
  values ('dddddddd-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'calf', 84.56);
select is(
  (select value_cm from public.body_circumferences where id = 'dddddddd-0000-4000-8000-000000000001'),
  84.6,
  'a value with two decimals is stored with one decimal');
select throws_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm)
    values ('11111111-1111-4111-8111-111111111111', 'elbow', 40)$$,
  '22P02', null, 'an invalid site is rejected');
select throws_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm, source)
    values ('11111111-1111-4111-8111-111111111111', 'arm', 40, 'bogus')$$,
  '22P02', null, 'an invalid source is rejected');
delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.body_circumferences where user_id = '33333333-3333-4333-8333-333333333333'),
  0,
  'deleting the auth user cascades to its circumferences');

-- RLS isolation (as user A)
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

insert into public.body_circumferences (id, site, value_cm)
  values ('aaaaaaaa-0000-4000-8000-000000000001', 'hip', 98.0);
select is(
  (select user_id from public.body_circumferences where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  '11111111-1111-4111-8111-111111111111'::uuid,
  'user_id defaults to the caller');
select is(
  (select source::text from public.body_circumferences where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  'manual',
  'source defaults to manual');
select is(
  (select count(*)::int from public.body_circumferences where user_id <> '11111111-1111-4111-8111-111111111111'),
  0,
  'A sees only their own rows');
select throws_ok(
  $$insert into public.body_circumferences (user_id, site, value_cm)
    values ('22222222-2222-4222-8222-222222222222', 'waist', 70)$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.body_circumferences set value_cm = 100
               where id = 'bbbbbbbb-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$,
  'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.body_circumferences
               where id = 'bbbbbbbb-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$,
  'A deleting B''s row affects 0 rows');
select throws_ok(
  $$update public.body_circumferences set user_id = '22222222-2222-4222-8222-222222222222'
    where id = 'aaaaaaaa-0000-4000-8000-000000000001'$$,
  '42501', null, 'A cannot reassign their row to B');
select throws_ok(
  'truncate public.body_circumferences',
  '42501', null, 'A cannot truncate body_circumferences');

-- Owner can update and delete; updated_at advances (now() is frozen, so backdate first).
reset role;
update public.body_circumferences set updated_at = '2000-01-01'
  where id = 'aaaaaaaa-0000-4000-8000-000000000001';
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
update public.body_circumferences set value_cm = 97.5 where id = 'aaaaaaaa-0000-4000-8000-000000000001';
select is(
  (select updated_at from public.body_circumferences where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  now(),
  'updated_at advances on update');
select results_eq(
  $$with d as (delete from public.body_circumferences
               where id = 'aaaaaaaa-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from d$$,
  $$values (1)$$,
  'A can delete their own row');

reset role;
select is(
  (select value_cm from public.body_circumferences where id = 'bbbbbbbb-0000-4000-8000-000000000001'),
  80.0,
  'B''s row is unchanged after A''s attempts');

-- Anonymous
set local role anon;
select throws_ok(
  'select * from public.body_circumferences',
  '42501', null, 'anon cannot read body_circumferences');
reset role;

select * from finish();
rollback;
