-- pgTAP: routine sets can plan a duration instead of reps (T-182, US-72, ADR-85).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(32);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select
    (select id from public.exercises where key = 'walking') as walk,
    (select id from public.exercises where key = 'stationary-bike') as bike,
    (select id from public.exercises where metric_type = 'weight_reps' and not is_custom order by key limit 1) as lift,
    (select id from public.exercises where metric_type = 'weight_reps' and not is_custom order by key offset 1 limit 1) as lift2;
grant select on cat to authenticated;

select has_column('public', 'routine_exercise_sets', 'target_duration_s', 'sets have target_duration_s');
select col_is_null('public', 'routine_exercise_sets', 'target_reps', 'target_reps is nullable');
select function_privs_are('public', 'save_routine', array['jsonb'], 'anon', array[]::text[],
  'anon cannot execute save_routine');
select is((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.routine_exercise_sets'::regclass),
  true, 'routine_exercise_sets keeps RLS enabled and forced');

-- ---------------------------------------------------------------------------
-- Fixtures (as postgres): column bounds and xor check
-- ---------------------------------------------------------------------------
insert into public.routines (id, user_id, name, track_as_routine) values
  ('a0000000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'Fixture', true);
insert into public.routine_exercises (id, routine_id, exercise_id, position, target_sets, target_reps) values
  ('e1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-0000000000a1', (select walk from cat), 0, 1, 1);

select throws_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_duration_s)
  values ('e1000000-0000-4000-8000-000000000001', 1, 59)$$, '23514', null, '59 s is below the bound');
select throws_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_duration_s)
  values ('e1000000-0000-4000-8000-000000000001', 1, 14401)$$, '23514', null, '14401 s is above the bound');
select lives_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_duration_s)
  values ('e1000000-0000-4000-8000-000000000001', 1, 60), ('e1000000-0000-4000-8000-000000000001', 2, 14400)$$,
  '60 s and 14400 s are accepted');
select throws_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_reps, target_duration_s)
  values ('e1000000-0000-4000-8000-000000000001', 3, 10, 600)$$, '23514', null, 'reps and duration together are rejected');
select throws_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index)
  values ('e1000000-0000-4000-8000-000000000001', 3)$$, '23514', null, 'neither reps nor duration is rejected');
select lives_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_reps)
  values ('e1000000-0000-4000-8000-000000000001', 3, 12)$$, 'a reps-only set is still accepted');

-- ---------------------------------------------------------------------------
-- save_routine as user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(format($$select public.save_routine(jsonb_build_object('name', 'Con cardio', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'rest_seconds', 90, 'rep_min', 8, 'rep_max', 12,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 40, 'reps', 10, 'set_type', 'working'))),
    jsonb_build_object('exercise_id', %L, 'rest_seconds', 0,
      'sets', jsonb_build_array(jsonb_build_object('duration_s', 3000, 'set_type', 'working'))))))$$,
  (select lift from cat), (select bike from cat)), 'save_routine stores a lifting exercise and a cardio exercise');
select results_eq(
  $$select e.position::int, e.target_reps::int, e.target_sets::int, e.rest_seconds::int, s.target_reps::int, s.target_duration_s, s.target_weight_kg
    from public.routine_exercises e join public.routine_exercise_sets s on s.routine_exercise_id = e.id
    join public.routines r on r.id = e.routine_id where r.name = 'Con cardio' order by e.position$$,
  $$values (0, 10, 1, 90, 10, null::int, 40.00::numeric), (1, 1, 1, 0, null::int, 3000, null::numeric)$$,
  'the duration round-trips with no reps and the legacy summary is 1');

select lives_ok(format($$select public.save_routine(jsonb_build_object('name', 'Camina', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('duration_s', 60), jsonb_build_object('duration_s', 14400))))))$$,
  (select walk from cat)), 'two duration sets on walking are accepted at the bounds');

select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'X1', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('duration_s', 600))))))$$,
  (select lift from cat)), '22023', null, 'duration on a weight_reps exercise is rejected');
select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'X2', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('duration_s', 600, 'reps', 10))))))$$,
  (select walk from cat)), '22023', null, 'duration together with reps is rejected');
select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'X3', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('duration_s', 600, 'weight_kg', 5))))))$$,
  (select walk from cat)), '22023', null, 'duration together with weight is rejected');
select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'X4', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('duration_s', 59))))))$$,
  (select walk from cat)), '22023', null, 'duration below 60 s is rejected');
select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'X5', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('duration_s', 14401))))))$$,
  (select walk from cat)), '22023', null, 'duration above 14400 s is rejected');
select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'X6', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'sets', jsonb_build_array(jsonb_build_object('set_type', 'working'))))))$$,
  (select walk from cat)), '22023', null, 'a set with neither reps nor duration is rejected');
select is((select count(*)::int from public.routines where name like 'X_'), 0, 'rejected saves leave nothing behind');

-- ---------------------------------------------------------------------------
-- start_workout_session on a cardio routine (A1: planned values stay on the routine)
-- ---------------------------------------------------------------------------
select lives_ok($$select public.start_workout_session((select id from public.routines where name = 'Con cardio'))$$,
  'a cardio routine can be started');
select results_eq(
  $$select ws.exercise_position::int, ws.set_type::text, ws.duration_s, ws.reps::int
    from public.workout_sets ws order by ws.exercise_position$$,
  $$values (0, 'working', null::int, 0), (1, 'working', null::int, 0)$$,
  'the pending cardio set is created without a performed duration');
select is((select s.target_duration_s from public.routine_exercise_sets s
           join public.routine_exercises e on e.id = s.routine_exercise_id
           join public.routines r on r.id = e.routine_id where r.name = 'Con cardio' and e.position = 1),
  3000, 'the planned duration stays readable on the routine');

-- ---------------------------------------------------------------------------
-- update_routine_from_session
-- ---------------------------------------------------------------------------
reset role;
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
select 'c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', r.id, 'Con cardio', 'completed',
       '2026-03-01 10:00+00', '2026-03-01 12:00+00'
from public.routines r where r.name = 'Con cardio';
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
select 'c0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', r.id, 'Con cardio', 'completed',
       '2026-03-02 10:00+00', '2026-03-02 12:00+00'
from public.routines r where r.name = 'Con cardio';
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, duration_s, completed_at) values
  ('c0000000-0000-4000-8000-000000000001', (select lift from cat), 0, 1, 'working', 42.5, 9, null, '2026-03-01 10:10+00'),
  ('c0000000-0000-4000-8000-000000000001', (select bike from cat), 1, 1, 'working', 0, 0, 3120, '2026-03-01 11:10+00'),
  ('c0000000-0000-4000-8000-000000000002', (select lift from cat), 0, 1, 'working', 42.5, 9, null, '2026-03-02 10:10+00'),
  ('c0000000-0000-4000-8000-000000000002', (select bike from cat), 1, 1, 'working', 0, 0, null, '2026-03-02 11:10+00');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-000000000001')$$,
  'a session with a performed cardio set updates the routine');
select results_eq(
  $$select e.position::int, e.target_reps::int, s.target_reps::int, s.target_duration_s, s.target_weight_kg
    from public.routine_exercises e join public.routine_exercise_sets s on s.routine_exercise_id = e.id
    join public.routines r on r.id = e.routine_id where r.name = 'Con cardio' order by e.position$$,
  $$values (0, 9, 9, null::int, 42.50::numeric), (1, 1, null::int, 3120, null::numeric)$$,
  'cardio planned 3120 s from 52 min performed; the lifting set keeps reps and weight');
select results_eq(
  $$select e.rep_min::int, e.rep_max::int, e.rest_seconds::int from public.routine_exercises e
    join public.routines r on r.id = e.routine_id where r.name = 'Con cardio' and e.position = 0$$,
  $$values (8, 12, 90)$$, 'the lifting range and rest are carried over as before');
select lives_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-000000000002')$$,
  'a cardio set without a performed duration updates the routine');
select is((select s.target_duration_s from public.routine_exercise_sets s
           join public.routine_exercises e on e.id = s.routine_exercise_id
           join public.routines r on r.id = e.routine_id where r.name = 'Con cardio' and e.position = 1),
  60, 'a cardio set without a performed duration is planned as the 60 s minimum');

-- ---------------------------------------------------------------------------
-- RLS cross-user and anon
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.routine_exercise_sets where target_duration_s is not null), 0,
  'B cannot read A''s duration sets');
update public.routine_exercise_sets set target_duration_s = 100 where target_duration_s is not null;
reset role;
select is((select count(*)::int from public.routine_exercise_sets where target_duration_s = 100), 0,
  'B cannot update A''s duration sets');
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select throws_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_duration_s)
  values ('e1000000-0000-4000-8000-000000000001', 9, 600)$$, '42501', null, 'B cannot insert into A''s routine exercise');

reset role;
set local role anon;
select throws_ok($$select count(*) from public.routine_exercise_sets$$, '42501', null, 'anon has no access to the table');

select * from finish();
rollback;
