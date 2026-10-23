-- pgTAP: last_performance (T-046, US-12).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(10);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises where not is_custom limit 3;
grant select on cat to authenticated;

-- Ana: exercise 1 has an old completed session, a newer completed one (target), a newer discarded one
-- and an in-progress one; exercise 2 only has a pending set in a completed session.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a2000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Old', 'completed',
   '2026-01-01 10:00+00', '2026-01-01 11:00+00'),
  ('a2000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Latest', 'completed',
   '2026-01-08 10:00+00', '2026-01-08 11:00+00'),
  ('a2000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'Discarded', 'discarded',
   '2026-01-20 10:00+00', '2026-01-20 11:00+00'),
  ('a2000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'Open', 'in_progress',
   '2026-01-25 10:00+00', null),
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea newer', 'completed',
   '2026-02-01 10:00+00', '2026-02-01 11:00+00');

insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at, rpe) values
  ('a2000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 50, 10, '2026-01-01 10:10+00', null),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 1), 0, 2, 85, 8, '2026-01-08 10:20+00', 8),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 1), 0, 1, 82.5, 10, '2026-01-08 10:10+00', null),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 1), 0, 3, 90, 5, null, null),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 1), 1, 1, 10, 20, '2026-01-08 10:40+00', null),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 2), 2, 1, 70, 8, null, null),
  ('a2000000-0000-4000-8000-000000000003', (select id from cat where n = 1), 0, 1, 500, 5, '2026-01-20 10:10+00', null),
  ('a2000000-0000-4000-8000-000000000004', (select id from cat where n = 1), 0, 1, 600, 5, '2026-01-25 10:10+00', null),
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 400, 3, '2026-02-01 10:10+00', null),
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 1, 1, 300, 3, '2026-02-01 10:10+00', null);

select function_privs_are('public', 'last_performance', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute last_performance');
select function_privs_are('public', 'last_performance', array['uuid'], 'authenticated', array['EXECUTE'],
  'authenticated can execute last_performance');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  $$select set_index, weight_kg, reps from public.last_performance((select id from cat where n = 1))$$,
  $$values (1, 82.5::numeric, 10), (2, 85::numeric, 8)$$,
  'returns the completed sets of the latest completed session ordered by set_index');
select results_eq(
  $$select set_index, rpe from public.last_performance((select id from cat where n = 1))$$,
  $$values (1, null::numeric), (2, 8::numeric)$$,
  'returns the logged rpe, null where it was not logged');
select is(
  (select count(*)::int from public.last_performance((select id from cat where n = 1)) where weight_kg in (500, 600, 50, 90, 10)),
  0, 'ignores pending sets, discarded, in-progress, older sessions and later positions of the same exercise');
select is((select count(*)::int from public.last_performance((select id from cat where n = 2))), 0,
  'an exercise with only pending sets has no previous performance');
select is((select count(*)::int from public.last_performance((select id from cat where n = 3))), 0,
  'ignores other users sessions (exercise only done by Bea)');
select is((select count(*)::int from public.last_performance('99999999-0000-4000-8000-000000000001')), 0,
  'a never-done exercise returns no rows');

select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select results_eq(
  $$select set_index, weight_kg, reps from public.last_performance((select id from cat where n = 1))$$,
  $$values (1, 400::numeric, 3)$$,
  'Bea sees only her own last performance');
select is((select count(*)::int from public.last_performance((select id from cat where n = 2))), 0,
  'Bea does not see Ana data');

select * from finish();
rollback;
