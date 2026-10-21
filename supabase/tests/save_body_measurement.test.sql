-- pgTAP: public.save_body_measurement one-per-day rule, locked date, Health Connect rows, isolation (T-076, US-25).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(30);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- A: a Health Connect row on 2026-10-19 and one on 2026-10-21 (HC-only days); B: a manual and a HC row.
insert into public.body_measurements (id, user_id, measured_at, weight_kg, source, external_id) values
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', '2026-10-19T10:00:00Z', 79, 'health_connect', 'hc-1'),
  ('a0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', '2026-10-21T10:00:00Z', 78, 'health_connect', 'hc-2'),
  ('b0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', '2026-10-19T08:00:00Z', 60, 'manual', null),
  ('b0000000-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', '2026-10-19T09:00:00Z', 61, 'health_connect', 'hc-b');

-- Security
select has_function('public', 'save_body_measurement',
  array['uuid', 'numeric', 'numeric', 'timestamp with time zone', 'timestamp with time zone',
        'timestamp with time zone'],
  'save_body_measurement exists');
select is(
  (select prosecdef from pg_proc where proname = 'save_body_measurement'
     and pronamespace = 'public'::regnamespace),
  false, 'save_body_measurement is security invoker');
select ok(not has_function_privilege('anon',
  'public.save_body_measurement(uuid, numeric, numeric, timestamptz, timestamptz, timestamptz)', 'execute'),
  'anon cannot execute save_body_measurement');
select ok(has_function_privilege('authenticated',
  'public.save_body_measurement(uuid, numeric, numeric, timestamptz, timestamptz, timestamptz)', 'execute'),
  'authenticated can execute save_body_measurement');

-- As user A
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- New save on an HC-only day inserts a manual row and leaves the HC row alone.
select isnt(
  public.save_body_measurement(null, 80.5, 18.5, '2026-10-19T12:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z'),
  null, 'a first save returns the row id');
select results_eq(
  $$select weight_kg, body_fat_pct, source::text from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and measured_at >= '2026-10-19T00:00:00Z'
      and measured_at < '2026-10-20T00:00:00Z' order by source$$,
  $$values (79.00::numeric, null::numeric, 'health_connect'), (80.50::numeric, 18.5::numeric, 'manual')$$,
  'a Health Connect-only day still gets its manual row, the HC row is untouched');

-- Second save the same local day updates the first (still one manual row), same id.
select is(
  public.save_body_measurement(null, 81, null, '2026-10-19T15:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z'),
  (select id from public.body_measurements
     where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'),
  'a second save the same day returns the same row id');
select results_eq(
  $$select weight_kg, body_fat_pct, measured_at from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'$$,
  $$values (81.00::numeric, null::numeric, timestamptz '2026-10-19T15:00:00Z')$$,
  'the same-day manual row is replaced (one row) and null fat clears the fat');

-- Another day inserts another row.
select lives_ok(
  $$select public.save_body_measurement(null, 79.5, null, '2026-10-20T09:00:00Z', '2026-10-20T00:00:00Z', '2026-10-21T00:00:00Z')$$,
  'a save on another day');
select is(
  (select count(*)::int from public.body_measurements
     where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'),
  2, 'another day inserts a second manual row');

-- Edit by id: keeps the day, updates weight and fat.
select lives_ok(
  $$select public.save_body_measurement(
      (select id from public.body_measurements where user_id = '11111111-1111-4111-8111-111111111111'
         and source = 'manual' and measured_at < '2026-10-20T00:00:00Z'),
      82, 20, '2026-10-19T16:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  'an edit of the same day');
select results_eq(
  $$select weight_kg, body_fat_pct, measured_at from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'
      and measured_at < '2026-10-20T00:00:00Z'$$,
  $$values (82.00::numeric, 20.0::numeric, timestamptz '2026-10-19T16:00:00Z')$$,
  'the edit updates weight, fat and time');

-- Date is locked: moving measured_at to another day is rejected.
select throws_ok(
  $$select public.save_body_measurement(
      (select id from public.body_measurements where user_id = '11111111-1111-4111-8111-111111111111'
         and source = 'manual' and measured_at < '2026-10-20T00:00:00Z'),
      82, null, '2026-10-20T09:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  '22023', null, 'an edit cannot move measured_at out of the day bounds');
-- Bounds that do not contain the row's current day are rejected.
select throws_ok(
  $$select public.save_body_measurement(
      (select id from public.body_measurements where user_id = '11111111-1111-4111-8111-111111111111'
         and source = 'manual' and measured_at < '2026-10-20T00:00:00Z'),
      82, null, '2026-10-20T09:00:00Z', '2026-10-20T00:00:00Z', '2026-10-21T00:00:00Z')$$,
  '22023', null, 'an edit with the bounds of another day is rejected');
select results_eq(
  $$select weight_kg, measured_at from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'
      and measured_at < '2026-10-20T00:00:00Z'$$,
  $$values (82.00::numeric, timestamptz '2026-10-19T16:00:00Z')$$,
  'rejected edits leave the row unchanged');

-- Bounds validation.
select throws_ok(
  $$select public.save_body_measurement(null, 80, null, '2026-10-19T12:00:00Z', null, null)$$,
  '22023', null, 'missing day bounds are rejected');
select throws_ok(
  $$select public.save_body_measurement(null, 80, null, '2026-10-19T12:00:00Z', '2026-10-20T00:00:00Z', '2026-10-19T00:00:00Z')$$,
  '22023', null, 'a day range that ends before it starts is rejected');
select throws_ok(
  $$select public.save_body_measurement(null, 80, null, null, '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  '22023', null, 'a missing measured_at is rejected');
select throws_ok(
  $$select public.save_body_measurement(null, 80, null, '2026-10-20T00:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  '22023', null, 'measured_at at the exclusive end of the day is rejected');

-- Table checks.
select throws_ok(
  $$select public.save_body_measurement(null, 19.9, null, '2026-10-22T12:00:00Z', '2026-10-22T00:00:00Z', '2026-10-23T00:00:00Z')$$,
  '23514', null, '19.9 kg is rejected');
select throws_ok(
  $$select public.save_body_measurement(null, 401, null, '2026-10-22T12:00:00Z', '2026-10-22T00:00:00Z', '2026-10-23T00:00:00Z')$$,
  '23514', null, '401 kg is rejected');
select throws_ok(
  $$select public.save_body_measurement(null, 80, 80, '2026-10-22T12:00:00Z', '2026-10-22T00:00:00Z', '2026-10-23T00:00:00Z')$$,
  '23514', null, '80 % body fat is rejected');

-- Health Connect row: not editable, but deletable.
select throws_ok(
  $$select public.save_body_measurement('a0000000-0000-4000-8000-000000000002', 80, null,
      '2026-10-21T11:00:00Z', '2026-10-21T00:00:00Z', '2026-10-22T00:00:00Z')$$,
  'P0002', null, 'a Health Connect row cannot be edited');
select throws_ok(
  $$select public.save_body_measurement('a0000000-0000-4000-8000-0000000000ff', 80, null,
      '2026-10-21T11:00:00Z', '2026-10-21T00:00:00Z', '2026-10-22T00:00:00Z')$$,
  'P0002', null, 'an unknown id is not found');
select throws_ok(
  $$select public.save_body_measurement('b0000000-0000-4000-8000-000000000001', 10.5, null,
      '2026-10-19T11:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  'P0002', null, 'another user''s row cannot be edited');

-- Delete: another user's row deletes 0 rows; own HC row is deleted.
with d as (delete from public.body_measurements where id = 'b0000000-0000-4000-8000-000000000001' returning 1)
select is((select count(*)::int from d), 0, 'another user''s row deletes 0 rows');
with d as (delete from public.body_measurements where id = 'a0000000-0000-4000-8000-000000000002' returning 1)
select is((select count(*)::int from d), 1, 'own Health Connect row can be deleted');

-- Isolation, anon.
reset role;
select is(
  (select count(*)::int from public.body_measurements where user_id = '22222222-2222-4222-8222-222222222222'),
  2, 'B''s rows are intact');
select ok(
  (select weight_kg = 60 and measured_at = '2026-10-19T08:00:00Z' from public.body_measurements
    where id = 'b0000000-0000-4000-8000-000000000001'),
  'B''s manual row is unchanged by A''s calls');

set local role anon;
select throws_ok(
  $$select public.save_body_measurement(null, 80, null, '2026-10-22T12:00:00Z', '2026-10-22T00:00:00Z', '2026-10-23T00:00:00Z')$$,
  '42501', null, 'anon cannot call save_body_measurement');
reset role;

select * from finish();
rollback;
