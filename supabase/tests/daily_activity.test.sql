-- pgTAP: public.daily_activity and import_health_activity (T-140, US-61/62).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(33);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.daily_activity (id, user_id, day, steps) values
  ('bbbbbbbb-0000-4000-8000-0000000000d1', '22222222-2222-4222-8222-222222222222', '2026-10-27', 1234);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select has_table('public', 'daily_activity', 'public.daily_activity exists');
select columns_are('public', 'daily_activity', array[
  'id', 'user_id', 'day', 'steps', 'active_kcal', 'total_kcal', 'updated_at'
], 'daily_activity has exactly the planned columns');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.daily_activity'::regclass),
  'RLS is enabled and forced on daily_activity');
select policies_are('public', 'daily_activity', array[
  'daily_activity_select_own', 'daily_activity_insert_own', 'daily_activity_update_own', 'daily_activity_delete_own'
], 'daily_activity has the four owner policies');
select table_privs_are('public', 'daily_activity', 'anon', array[]::text[],
  'anon has no privileges on daily_activity');
select table_privs_are('public', 'daily_activity', 'authenticated', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has full CRUD on daily_activity');
select is(
  (select prosecdef from pg_proc where proname = 'import_health_activity' and pronamespace = 'public'::regnamespace),
  false, 'import_health_activity is security invoker');
select is(
  (select prosecdef from pg_proc where proname = 'mark_health_auto_sync' and pronamespace = 'public'::regnamespace),
  false, 'mark_health_auto_sync is security invoker');
select ok(not has_function_privilege('anon', 'public.import_health_activity(jsonb, jsonb, jsonb)', 'execute'),
  'anon cannot execute import_health_activity');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  public.import_health_activity(
    '[{"day":"2026-10-27","steps":8500,"active_kcal":420.5,"total_kcal":2600}]'::jsonb, '[]'::jsonb, '[]'::jsonb),
  '{"daily":1,"sleep":0,"resting_hr":0}'::jsonb, 'the first import writes one daily row');
select is(
  (select steps from public.daily_activity where day = '2026-10-27'), 8500, 'yesterday''s steps round-trip');
select lives_ok(
  $$select public.import_health_activity(
      '[{"day":"2026-10-27","steps":8500,"active_kcal":420.5,"total_kcal":2600}]'::jsonb, '[]', '[]')$$,
  'importing the same day again raises no error');
select is(
  (select count(*)::int from public.daily_activity where day = '2026-10-27'), 1,
  'a second import the same day leaves one row');

select public.import_health_activity('[{"day":"2026-10-27","steps":9000,"active_kcal":500}]'::jsonb, '[]', '[]');
select results_eq(
  $$select steps, active_kcal, total_kcal from public.daily_activity where day = '2026-10-27'$$,
  $$values (9000, 500.00::numeric, null::numeric)$$,
  'a re-read day replaces every value, including nulls');

select public.import_health_activity(
  '[{"day":"2026-10-26","steps":1},{"day":"2026-10-26","steps":2}]'::jsonb, '[]', '[]');
select is((select steps from public.daily_activity where day = '2026-10-26'), 2,
  'two entries for one day in a payload keep the last');

select is(
  public.import_health_activity('[{"steps":5},{"day":"2026-10-25","steps":7}]'::jsonb, '[]', '[]') ->> 'daily',
  '1', 'an entry without day is skipped and not counted');
select is((select count(*)::int from public.daily_activity), 3, 'only the dated rows are stored');

select public.import_health_activity('[{"day":"2026-10-24","active_kcal":300}]'::jsonb, '[]', '[]');
select results_eq(
  $$select steps, active_kcal from public.daily_activity where day = '2026-10-24'$$,
  $$values (null::int, 300.00::numeric)$$, 'a day with kcal only keeps steps null');

-- Range checks
select throws_ok(
  $$select public.import_health_activity('[{"day":"2026-10-20","steps":-1}]'::jsonb, '[]', '[]')$$,
  '23514', null, 'negative steps are rejected');
select throws_ok(
  $$select public.import_health_activity('[{"day":"2026-10-20","steps":5,"total_kcal":100001}]'::jsonb, '[]', '[]')$$,
  '23514', null, 'too many kcal are rejected');

-- Validation
select throws_ok(
  $$select public.import_health_activity(null, '[]', '[]')$$, '22023', null, 'a null p_daily is rejected');
select throws_ok(
  $$select public.import_health_activity('[]', null, '[]')$$, '22023', null, 'a null p_sleep is rejected');
select throws_ok(
  $$select public.import_health_activity('[]', '[]', null)$$, '22023', null, 'a null p_resting_hr is rejected');
select throws_ok(
  $$select public.import_health_activity('{"day":"2026-10-20"}'::jsonb, '[]', '[]')$$,
  '22023', null, 'a non-array p_daily is rejected');
select throws_ok(
  $$select public.import_health_activity(
      (select jsonb_agg(jsonb_build_object('day', date '2026-01-01' + g, 'steps', 1))
         from generate_series(1, 401) g), '[]', '[]')$$,
  '22023', null, '401 daily rows are rejected');
select lives_ok(
  $$select public.import_health_activity(
      (select jsonb_agg(jsonb_build_object('day', date '2025-01-01' + g, 'steps', 1))
         from generate_series(1, 400) g), '[]', '[]')$$,
  'exactly 400 daily rows are accepted');

-- RLS
select is(
  (select count(*)::int from public.daily_activity where user_id = '22222222-2222-4222-8222-222222222222'),
  0, 'A cannot read B''s rows');
select throws_ok(
  $$insert into public.daily_activity (user_id, day, steps)
    values ('22222222-2222-4222-8222-222222222222', '2026-09-01', 1)$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.daily_activity set steps = 1 where id = 'bbbbbbbb-0000-4000-8000-0000000000d1' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.daily_activity where id = 'bbbbbbbb-0000-4000-8000-0000000000d1' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'A deleting B''s row affects 0 rows');

-- Unauthenticated
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.import_health_activity('[]', '[]', '[]')$$, '28000', null, 'import without a user raises 28000');

reset role;
select is(
  (select steps from public.daily_activity where id = 'bbbbbbbb-0000-4000-8000-0000000000d1'), 1234,
  'B''s row is untouched');

set local role anon;
select throws_ok('select * from public.daily_activity', '42501', null, 'anon cannot read daily_activity');
reset role;

select * from finish();
rollback;
