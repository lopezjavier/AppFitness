-- pgTAP: public.notification_settings and enum notification_kind (T-135, US-57).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(17);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'cai@example.com');

insert into public.notification_settings (user_id, kind, enabled) values
  ('22222222-2222-4222-8222-222222222222', 'weight', false),
  ('33333333-3333-4333-8333-333333333333', 'weight', true);

-- Schema and security
select has_table('public', 'notification_settings', 'public.notification_settings exists');
select enum_has_labels('public', 'notification_kind', array['weight', 'circumference', 'weekly_check_in'],
  'notification_kind has the three planned values');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.notification_settings'::regclass),
  'RLS is enabled and forced on notification_settings');
select policies_are('public', 'notification_settings', array[
  'notification_settings_select_own', 'notification_settings_insert_own', 'notification_settings_update_own'
], 'notification_settings has the three owner policies');
select table_privs_are('public', 'notification_settings', 'anon', array[]::text[],
  'anon has no privileges on notification_settings');
select table_privs_are('public', 'notification_settings', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'],
  'authenticated can select, insert and update notification_settings');

-- As user A
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$insert into public.notification_settings (kind, enabled) values ('weight', false)$$,
  'A inserts a row; user_id defaults to the caller');
select is(
  (select user_id from public.notification_settings where kind = 'weight'),
  '11111111-1111-4111-8111-111111111111'::uuid, 'the default user_id is auth.uid()');
select throws_ok(
  $$insert into public.notification_settings (kind, enabled) values ('weight', true)$$,
  '23505', null, 'a second row for the same user and kind is rejected');
select lives_ok(
  $$insert into public.notification_settings (kind, enabled) values ('weight', true)
    on conflict (user_id, kind) do update set enabled = excluded.enabled$$,
  'upserting on (user_id, kind) works');
select is(
  (select enabled from public.notification_settings where kind = 'weight'),
  true, 'the upsert updated the existing row');
select is(
  (select count(*)::int from public.notification_settings), 1, 'A sees only their own rows');
select throws_ok(
  $$insert into public.notification_settings (user_id, kind, enabled)
    values ('22222222-2222-4222-8222-222222222222', 'circumference', false)$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.notification_settings set enabled = true
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s row affects 0 rows');
select throws_ok(
  $$insert into public.notification_settings (kind, enabled) values ('other', true)$$,
  '22P02', null, 'an unknown kind is rejected');

reset role;
select is(
  (select enabled from public.notification_settings where user_id = '22222222-2222-4222-8222-222222222222'),
  false, 'B''s row is untouched');

-- Cascade
delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.notification_settings where user_id = '33333333-3333-4333-8333-333333333333'),
  0, 'deleting the user removes their settings');

select * from finish();
rollback;
