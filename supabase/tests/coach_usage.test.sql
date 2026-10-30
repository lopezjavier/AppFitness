-- pgTAP: coach_usage and its service-role RPCs (T-160, US-76; budget rules in planning/07 section 11).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(21);

insert into auth.users (id, email) values
  ('c6000000-0000-4000-8000-000000000001', 'usage-a@example.com'),
  ('c6000000-0000-4000-8000-000000000002', 'usage-b@example.com');

insert into public.coach_usage (user_id, month, input_tokens, output_tokens, cost_eur) values
  ('c6000000-0000-4000-8000-000000000001', date '2026-10-01', 100, 50, 0.5),
  ('c6000000-0000-4000-8000-000000000002', date '2026-10-01', 200, 80, 1);

select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_usage'::regclass),
  'RLS is enabled and forced on coach_usage');

-- ---------------------------------------------------------------------------
-- Owner isolation (authenticated user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"c6000000-0000-4000-8000-000000000001","role":"authenticated"}', true);

select is((select count(*)::int from public.coach_usage), 1, 'user A sees exactly one row');
select is(
  (select count(*)::int from public.coach_usage where user_id = 'c6000000-0000-4000-8000-000000000002'),
  0, 'user A cannot read user B''s usage');
select throws_ok(
  $$insert into public.coach_usage (user_id, month) values ('c6000000-0000-4000-8000-000000000001', date '2026-11-01')$$,
  '42501', null, 'an authenticated user cannot insert usage');
select throws_ok(
  $$update public.coach_usage set cost_eur = 0$$,
  '42501', null, 'an authenticated user cannot update usage');
select throws_ok(
  $$delete from public.coach_usage$$,
  '42501', null, 'an authenticated user cannot delete usage');
select throws_ok(
  $$select public.coach_record_usage('c6000000-0000-4000-8000-000000000001', date '2026-11-01', 1, 1, 0.01)$$,
  '42501', null, 'an authenticated user cannot call coach_record_usage');
reset role;

set local role anon;
select throws_ok(
  $$select * from public.coach_usage$$,
  '42501', null, 'anon cannot read usage');
reset role;

-- ---------------------------------------------------------------------------
-- Function privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('authenticated', 'public.coach_record_usage(uuid,date,bigint,bigint,numeric)', 'execute'),
  'authenticated cannot execute coach_record_usage');
select ok(not has_function_privilege('anon', 'public.coach_record_usage(uuid,date,bigint,bigint,numeric)', 'execute'),
  'anon cannot execute coach_record_usage');
select ok(not has_function_privilege('authenticated', 'public.coach_global_month_cost(date)', 'execute'),
  'authenticated cannot execute coach_global_month_cost');
select ok(not has_function_privilege('anon', 'public.coach_global_month_cost(date)', 'execute'),
  'anon cannot execute coach_global_month_cost');
select ok(has_function_privilege('service_role', 'public.coach_record_usage(uuid,date,bigint,bigint,numeric)', 'execute'),
  'service_role can execute coach_record_usage');
select ok(has_function_privilege('service_role', 'public.coach_global_month_cost(date)', 'execute'),
  'service_role can execute coach_global_month_cost');

-- ---------------------------------------------------------------------------
-- service_role path
-- ---------------------------------------------------------------------------
set local role service_role;
select public.coach_record_usage('c6000000-0000-4000-8000-000000000001', date '2026-11-01', 10, 5, 0.25);
select public.coach_record_usage('c6000000-0000-4000-8000-000000000001', date '2026-11-01', 20, 10, 0.5);
select public.coach_record_usage('c6000000-0000-4000-8000-000000000001', date '2026-12-01', 1, 1, 0.01);
reset role;

select is(
  (select (input_tokens, output_tokens, cost_eur)::text from public.coach_usage
   where user_id = 'c6000000-0000-4000-8000-000000000001' and month = date '2026-11-01'),
  '(30,15,0.750000)', 'recording twice in the same month adds up');
select is(
  (select count(*)::int from public.coach_usage where user_id = 'c6000000-0000-4000-8000-000000000001'),
  3, 'a different month creates its own row');

set local role service_role;
select is(public.coach_global_month_cost(date '2026-10-01'), 1.5::numeric,
  'the global total sums every user of the month');
select is(public.coach_global_month_cost(date '2030-01-01'), 0::numeric,
  'the global total is 0 when there are no rows');
select is(pg_typeof(public.coach_global_month_cost(date '2026-10-01'))::text, 'numeric',
  'the global total is a single scalar, not rows');
reset role;

-- ---------------------------------------------------------------------------
-- Constraints
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.coach_usage (user_id, month) values ('c6000000-0000-4000-8000-000000000002', date '2026-10-15')$$,
  '23514', null, 'month must be the first day of a month');
select throws_ok(
  $$insert into public.coach_usage (user_id, month, cost_eur) values ('c6000000-0000-4000-8000-000000000002', date '2026-09-01', -1)$$,
  '23514', null, 'cost cannot be negative');

select * from finish();
rollback;
