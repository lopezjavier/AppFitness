-- pgTAP: public.health_sync_state, import_health_measurements and delete_body_measurement (T-079, US-26).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(45);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'cai@example.com');

insert into public.health_sync_state (user_id, last_synced_at, changes_token) values
  ('22222222-2222-4222-8222-222222222222', '2026-10-01T00:00:00Z', 'b-token');
insert into public.body_measurements (id, user_id, weight_kg, source, external_id) values
  ('bbbbbbbb-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 60, 'health_connect', 'b-hc');

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select has_table('public', 'health_sync_state', 'public.health_sync_state exists');
select columns_are('public', 'health_sync_state', array[
  'user_id', 'last_synced_at', 'changes_token', 'dismissed_external_ids', 'updated_at', 'last_auto_sync_date'
], 'health_sync_state has exactly the planned columns');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.health_sync_state'::regclass),
  'RLS is enabled and forced on health_sync_state');
select policies_are('public', 'health_sync_state', array[
  'health_sync_state_select_own', 'health_sync_state_insert_own', 'health_sync_state_update_own'
], 'health_sync_state has the three owner policies');
select table_privs_are('public', 'health_sync_state', 'anon', array[]::text[],
  'anon has no privileges on health_sync_state');
select table_privs_are('public', 'health_sync_state', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'],
  'authenticated can select, insert and update health_sync_state');
select has_function('public', 'import_health_measurements',
  array['jsonb', 'timestamp with time zone', 'text'], 'import_health_measurements exists');
select is(
  (select prosecdef from pg_proc where proname = 'import_health_measurements'
     and pronamespace = 'public'::regnamespace),
  false, 'import_health_measurements is security invoker');
select ok(not has_function_privilege('anon',
  'public.import_health_measurements(jsonb, timestamptz, text)', 'execute'),
  'anon cannot execute import_health_measurements');
select has_function('public', 'delete_body_measurement', array['uuid'], 'delete_body_measurement exists');
select ok(not has_function_privilege('anon', 'public.delete_body_measurement(uuid)', 'execute'),
  'anon cannot execute delete_body_measurement');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  public.import_health_measurements(
    '[{"external_id":"e1","measured_at":"2026-10-20T07:31:00Z","weight_kg":70},
      {"external_id":"e2","measured_at":"2026-10-21T07:31:00Z","weight_kg":71}]'::jsonb,
    '2026-10-22T08:00:00Z', 'tok1'),
  2, 'the first import inserts both rows');
select is(
  public.import_health_measurements(
    '[{"external_id":"e1","measured_at":"2026-10-20T07:31:00Z","weight_kg":70},
      {"external_id":"e2","measured_at":"2026-10-21T07:31:00Z","weight_kg":71}]'::jsonb,
    '2026-10-22T08:05:00Z', 'tok1'),
  0, 'importing the same rows again inserts nothing');
select is(
  (select count(*)::int from public.body_measurements where source = 'health_connect'
     and user_id = '11111111-1111-4111-8111-111111111111'),
  2, 'the row count is unchanged after the second import');

-- Null-fill from a later batch; weight and non-null values are never overwritten.
select is(
  public.import_health_measurements(
    '[{"external_id":"e1","measured_at":"2026-10-20T07:31:00Z","weight_kg":99,"body_fat_pct":20,
       "lean_mass_kg":55,"water_kg":40,"bone_kg":3,"bmr_kcal":1700}]'::jsonb,
    '2026-10-22T08:10:00Z', 'tok2'),
  0, 'a later batch for a known id inserts nothing');
select is(
  public.import_health_measurements(
    '[{"external_id":"e1","measured_at":"2026-10-20T07:31:00Z","weight_kg":98,"body_fat_pct":25,
       "lean_mass_kg":56}]'::jsonb,
    '2026-10-22T08:11:00Z', 'tok2'),
  0, 'a batch with other values for a known id inserts nothing');
select results_eq(
  $$select weight_kg, body_fat_pct, lean_mass_kg, water_kg, bone_kg, bmr_kcal from public.body_measurements
    where external_id = 'e1'$$,
  $$values (70.00::numeric, 20.0::numeric, 55.00::numeric, 40.00::numeric, 3.00::numeric, 1700)$$,
  'missing columns are filled once and existing values are never overwritten');
select is(
  (select changes_token from public.health_sync_state), 'tok2', 'the changes token is stored');

-- An empty array only advances the state; last_synced_at is clamped to now.
select is(
  public.import_health_measurements('[]'::jsonb, now() + interval '1 day', 'tok3'),
  0, 'an empty array inserts nothing');
select is((select last_synced_at from public.health_sync_state), now(),
  'last_synced_at is clamped to now');
select is((select changes_token from public.health_sync_state), 'tok3', 'an empty import still stores the token');

-- Validation
select throws_ok(
  $$select public.import_health_measurements(
      (select jsonb_agg(jsonb_build_object('external_id', 'x' || g, 'measured_at', now(), 'weight_kg', 70))
         from generate_series(1, 1001) g),
      now(), 't')$$,
  '22023', null, 'more than 1000 rows are rejected');
select throws_ok(
  $$select public.import_health_measurements('{"external_id":"x"}'::jsonb, now(), 't')$$,
  '22023', null, 'a non-array payload is rejected');
select lives_ok(
  $$select public.import_health_measurements(
      (select jsonb_agg(jsonb_build_object('external_id', 'y' || g, 'measured_at', now(), 'weight_kg', 70))
         from generate_series(1, 1000) g),
      now(), 't')$$,
  'exactly 1000 rows are accepted');

-- delete_body_measurement: tombstones for imported rows only.
select lives_ok(
  $$select public.delete_body_measurement((select id from public.body_measurements where external_id = 'e2'))$$,
  'deleting an imported row');
select is(
  (select dismissed_external_ids from public.health_sync_state), array['e2'],
  'the deleted imported id is appended to the tombstones');
select is(
  public.import_health_measurements(
    '[{"external_id":"e2","measured_at":"2026-10-21T07:31:00Z","weight_kg":71}]'::jsonb, now(), 'tok4'),
  0, 're-importing a deleted reading inserts nothing');
select is(
  (select count(*)::int from public.body_measurements where external_id = 'e2'), 0,
  'the deleted reading does not come back');

insert into public.body_measurements (id, weight_kg, source)
  values ('aaaaaaaa-0000-4000-8000-000000000001', 74, 'manual');
select lives_ok(
  $$select public.delete_body_measurement('aaaaaaaa-0000-4000-8000-000000000001')$$,
  'deleting a manual row');
select is(
  (select dismissed_external_ids from public.health_sync_state), array['e2'],
  'deleting a manual row appends nothing');
select lives_ok(
  $$select public.delete_body_measurement('bbbbbbbb-0000-4000-8000-000000000001')$$,
  'deleting another user''s row raises no error');

-- RLS on the state table
select is(
  (select count(*)::int from public.health_sync_state where user_id = '22222222-2222-4222-8222-222222222222'),
  0, 'A cannot read B''s state');
select throws_ok(
  $$insert into public.health_sync_state (user_id, last_synced_at)
    values ('22222222-2222-4222-8222-222222222222', now())$$,
  '42501', null, 'A cannot insert a state for B');
select results_eq(
  $$with u as (update public.health_sync_state set changes_token = 'hacked'
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$,
  'A updating B''s state affects 0 rows');

-- mark_health_auto_sync (T-140, US-60): the only writer of last_auto_sync_date.
select is((select last_auto_sync_date from public.health_sync_state), null::date,
  'the auto-sync date is empty before any mark');
select public.mark_health_auto_sync('2026-10-27');
select is((select last_auto_sync_date from public.health_sync_state), date '2026-10-27',
  'mark_health_auto_sync stores the date');
select public.mark_health_auto_sync('2026-10-28');
select results_eq(
  $$select last_auto_sync_date, last_synced_at, changes_token from public.health_sync_state$$,
  $$values (date '2026-10-28', now(), 'tok4')$$,
  'a later mark updates only the date, not last_synced_at or the changes token');
select public.import_health_activity('[]', '[]', '[]');
select is((select last_auto_sync_date from public.health_sync_state), date '2026-10-28',
  'import_health_activity does not change the auto-sync date');
select throws_ok($$select public.mark_health_auto_sync(null)$$, '22023', null, 'a null day is rejected');

select set_config('request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}', true);
select public.mark_health_auto_sync('2026-10-27');
select results_eq(
  $$select last_auto_sync_date, last_synced_at is not null, changes_token from public.health_sync_state$$,
  $$values (date '2026-10-27', true, null::text)$$,
  'mark creates the state row when missing');

-- Unauthenticated
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.mark_health_auto_sync('2026-10-27')$$, '28000', null, 'mark without a user raises 28000');
select throws_ok(
  $$select public.import_health_measurements('[]'::jsonb, now(), 't')$$,
  '28000', null, 'import without a user raises 28000');
select throws_ok(
  $$select public.delete_body_measurement('bbbbbbbb-0000-4000-8000-000000000001')$$,
  '28000', null, 'delete without a user raises 28000');

reset role;
select is(
  (select count(*)::int from public.body_measurements where id = 'bbbbbbbb-0000-4000-8000-000000000001'),
  1, 'B''s row is untouched');

set local role anon;
select throws_ok(
  'select * from public.health_sync_state',
  '42501', null, 'anon cannot read health_sync_state');
reset role;

select * from finish();
rollback;
