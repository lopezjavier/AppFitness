-- pgTAP: public.resting_heart_rate and the resting-HR part of import_health_activity (T-140, US-61/62).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(18);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.resting_heart_rate (id, user_id, day, bpm) values
  ('bbbbbbbb-0000-4000-8000-0000000000f1', '22222222-2222-4222-8222-222222222222', '2026-10-27', 60);

select has_table('public', 'resting_heart_rate', 'public.resting_heart_rate exists');
select columns_are('public', 'resting_heart_rate', array[
  'id', 'user_id', 'day', 'bpm', 'external_id', 'updated_at'
], 'resting_heart_rate has exactly the planned columns');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.resting_heart_rate'::regclass),
  'RLS is enabled and forced on resting_heart_rate');
select policies_are('public', 'resting_heart_rate', array[
  'resting_heart_rate_select_own', 'resting_heart_rate_insert_own',
  'resting_heart_rate_update_own', 'resting_heart_rate_delete_own'
], 'resting_heart_rate has the four owner policies');
select table_privs_are('public', 'resting_heart_rate', 'anon', array[]::text[],
  'anon has no privileges on resting_heart_rate');
select table_privs_are('public', 'resting_heart_rate', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'], 'authenticated has full CRUD on resting_heart_rate');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  public.import_health_activity('[]', '[]', '[]'),
  '{"daily":0,"sleep":0,"resting_hr":0}'::jsonb, 'an empty resting-HR array raises no error and returns 0');
select is((select count(*)::int from public.resting_heart_rate), 0, 'no resting heart rate is stored');

select is(
  public.import_health_activity('[]', '[]', '[{"day":"2026-10-27","bpm":52,"external_id":"r1"}]') ->> 'resting_hr',
  '1', 'a reading is imported');
select public.import_health_activity('[]', '[]', '[{"day":"2026-10-27","bpm":55,"external_id":"r2"}]');
select results_eq(
  $$select bpm::int, external_id from public.resting_heart_rate$$,
  $$values (55, 'r2')$$, 'two imports the same day keep one row with the latest bpm');

select public.import_health_activity('[]', '[]',
  '[{"day":"2026-10-26","bpm":50},{"day":"2026-10-26","bpm":48}]');
select is((select bpm::int from public.resting_heart_rate where day = '2026-10-26'), 48,
  'duplicates in one payload keep the last');

select lives_ok(
  $$select public.import_health_activity('[]', '[]', '[{"day":"2026-10-20","bpm":20},{"day":"2026-10-21","bpm":250}]')$$,
  'bpm 20 and 250 are accepted');
select throws_ok(
  $$select public.import_health_activity('[]', '[]', '[{"day":"2026-10-22","bpm":19}]')$$,
  '23514', null, 'bpm 19 is rejected');
select throws_ok(
  $$select public.import_health_activity('[]', '[]', '[{"day":"2026-10-22","bpm":251}]')$$,
  '23514', null, 'bpm 251 is rejected');
select throws_ok(
  $$select public.import_health_activity('[]', '[]',
      (select jsonb_agg(jsonb_build_object('day', date '2026-01-01' + g, 'bpm', 60))
         from generate_series(1, 401) g))$$,
  '22023', null, '401 resting-HR rows are rejected');

select is((select count(*)::int from public.resting_heart_rate where user_id = '22222222-2222-4222-8222-222222222222'),
  0, 'A cannot read B''s readings');
select throws_ok(
  $$insert into public.resting_heart_rate (user_id, day, bpm)
    values ('22222222-2222-4222-8222-222222222222', '2026-09-01', 60)$$,
  '42501', null, 'A cannot insert a reading for B');
select results_eq(
  $$with u as (update public.resting_heart_rate set bpm = 30 where id = 'bbbbbbbb-0000-4000-8000-0000000000f1' returning 1),
         d as (delete from public.resting_heart_rate where id = 'bbbbbbbb-0000-4000-8000-0000000000f1' returning 1)
    select (select count(*) from u)::int + (select count(*) from d)::int$$,
  $$values (0)$$, 'A updating or deleting B''s reading affects 0 rows');

reset role;
select * from finish();
rollback;
