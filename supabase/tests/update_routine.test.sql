-- pgTAP: session rests and "Actualizar rutina" (T-099, US-36, ADR-54 amendment).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(36);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id, (row_number() over (order by key))::int as n
  from public.exercises where not is_custom order by key limit 3;
grant select on cat to authenticated;

select has_column('public', 'workout_sessions', 'exercise_rests', 'workout_sessions has exercise_rests');
select function_privs_are('public', 'update_routine_from_session', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute update_routine_from_session');
select function_privs_are('public', 'set_session_rest', array['uuid', 'int', 'int'], 'anon', array[]::text[],
  'anon cannot execute set_session_rest');

-- ---------------------------------------------------------------------------
-- Fixtures (as postgres)
-- ---------------------------------------------------------------------------
-- RA: A's routine with ex1 (rest 90, rpe 8, 3 sets) and ex2 (rest 60, 2 sets). RO: another routine of A.
insert into public.routines (id, user_id, name, notes, track_as_routine, auto_update_routine) values
  ('a0000000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'Rutina A', 'nota', true, false),
  ('a0000000-0000-4000-8000-0000000000a2', '11111111-1111-4111-8111-111111111111', 'Rutina otra', null, true, false),
  ('a0000000-0000-4000-8000-0000000000a3', '11111111-1111-4111-8111-111111111111', 'Rutina C', null, true, false),
  ('b0000000-0000-4000-8000-0000000000b1', '22222222-2222-4222-8222-222222222222', 'Rutina B', null, true, false);

insert into public.routine_exercises (id, routine_id, exercise_id, position, target_sets, target_reps, target_rpe, rest_seconds) values
  ('e1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-0000000000a1', (select id from cat where n = 1), 0, 3, 8, 8, 90),
  ('e1000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-0000000000a1', (select id from cat where n = 2), 1, 2, 8, null, 60),
  ('e2000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-0000000000a2', (select id from cat where n = 2), 0, 1, 8, null, 60),
  ('e3000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-0000000000a3', (select id from cat where n = 1), 0, 1, 5, 7, 45);
insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps) values
  ('e1000000-0000-4000-8000-000000000001', 1, 40, 8), ('e1000000-0000-4000-8000-000000000001', 2, 40, 8),
  ('e1000000-0000-4000-8000-000000000001', 3, 40, 8),
  ('e1000000-0000-4000-8000-000000000002', 1, 20, 8), ('e1000000-0000-4000-8000-000000000002', 2, 20, 8),
  ('e2000000-0000-4000-8000-000000000001', 1, 20, 8),
  ('e3000000-0000-4000-8000-000000000001', 1, 60, 5);

-- Sessions. S0 earlier completed, S1 completed from RA (rest of position 1 changed), S3/S4 completed from RC,
-- SB B's completed, SD completed without routine (deleted routine), SIP A's in progress.
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at, exercise_rests) values
  ('c0000000-0000-4000-8000-000000000000', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a1',
   'Rutina A', 'completed', '2026-01-01 10:00+00', '2026-01-01 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a1',
   'Rutina A', 'completed', '2026-02-01 10:00+00', '2026-02-01 11:00+00', '{"1": 150}'),
  ('c0000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a3',
   'Rutina C', 'completed', '2026-03-01 10:00+00', '2026-03-01 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a3',
   'Rutina C', 'completed', '2026-03-02 10:00+00', '2026-03-02 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-0000000000bb', '22222222-2222-4222-8222-222222222222', 'b0000000-0000-4000-8000-0000000000b1',
   'Rutina B', 'completed', '2026-03-03 10:00+00', '2026-03-03 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-0000000000dd', '11111111-1111-4111-8111-111111111111', null,
   'Entreno libre', 'completed', '2026-03-04 10:00+00', '2026-03-04 11:00+00', '{}');
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at) values
  ('c0000000-0000-4000-8000-0000000000ee', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a2',
   'Rutina otra', 'in_progress', '2026-03-05 10:00+00');

insert into public.workout_sets (session_id, exercise_id, exercise_position, superset_group, set_index, set_type, weight_kg, reps, completed_at) values
  ('c0000000-0000-4000-8000-000000000000', (select id from cat where n = 1), 0, null, 1, 'working', 40, 8, '2026-01-01 10:10+00'),
  -- S1 position 0: new exercise ex3, warm-up + working completed, one pending
  ('c0000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 0, 0, 1, 'warm_up', 10, 12, '2026-02-01 10:10+00'),
  ('c0000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 0, 0, 2, 'working', 20, 10, '2026-02-01 10:11+00'),
  ('c0000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 0, 0, 3, 'working', 0, 0, null),
  -- S1 position 1: ex1, two completed
  ('c0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 1, 0, 1, 'working', 50, 5, '2026-02-01 10:20+00'),
  ('c0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 1, 0, 2, 'failure', 55, 4, '2026-02-01 10:21+00'),
  -- S1 position 2: ex2, only pending -> dropped
  ('c0000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 2, null, 1, 'working', 0, 0, null),
  -- S3: superset with unequal counts, reps out of range
  ('c0000000-0000-4000-8000-000000000003', (select id from cat where n = 1), 0, 1, 1, 'working', 30, 60, '2026-03-01 10:10+00'),
  ('c0000000-0000-4000-8000-000000000003', (select id from cat where n = 1), 0, 1, 2, 'working', 30, 5, '2026-03-01 10:11+00'),
  ('c0000000-0000-4000-8000-000000000003', (select id from cat where n = 2), 1, 1, 1, 'working', 0, 0, '2026-03-01 10:12+00');
-- S4: 11 completed sets of one exercise
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at)
select 'c0000000-0000-4000-8000-000000000004', (select id from cat where n = 1), 0, g, 10, 10, '2026-03-02 10:10+00'
from generate_series(1, 11) as g;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at) values
  ('c0000000-0000-4000-8000-0000000000bb', (select id from cat where n = 1), 0, 1, 10, 10, '2026-03-03 10:10+00');

-- ---------------------------------------------------------------------------
-- As user A: set_session_rest
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok($$select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', 0, 90)$$,
  'set_session_rest writes a rest on an in-progress session');
select is((select exercise_rests from public.workout_sessions where id = 'c0000000-0000-4000-8000-0000000000ee'),
  '{"0": 90}'::jsonb, 'the rest is stored under its position');
select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', 0, 100);
select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', 2, 0);
select is((select exercise_rests from public.workout_sessions where id = 'c0000000-0000-4000-8000-0000000000ee'),
  '{"0": 100, "2": 0}'::jsonb, 'a second write upserts the same position and adds others (0 is valid)');
select throws_ok($$select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', 0, 601)$$,
  '22023', null, 'a rest above 600 is rejected');
select throws_ok($$select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', 0, -1)$$,
  '22023', null, 'a negative rest is rejected');
select throws_ok($$select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', -1, 60)$$,
  '22023', null, 'a negative position is rejected');
select is((select exercise_rests from public.workout_sessions where id = 'c0000000-0000-4000-8000-0000000000ee'),
  '{"0": 100, "2": 0}'::jsonb, 'rejected writes leave the rests intact');
select throws_ok($$select public.set_session_rest('c0000000-0000-4000-8000-000000000001', 0, 60)$$,
  'P0002', null, 'a completed session rejects set_session_rest');
select throws_ok($$select public.set_session_rest('c0000000-0000-4000-8000-0000000000bb', 0, 60)$$,
  'P0002', null, 'A cannot set a rest on B''s session');

-- ---------------------------------------------------------------------------
-- update_routine_from_session
-- ---------------------------------------------------------------------------
select is(public.update_routine_from_session('c0000000-0000-4000-8000-000000000001'),
  'a0000000-0000-4000-8000-0000000000a1'::uuid, 'returns the routine id');
select results_eq(
  $$select position::int, exercise_id, superset_group::int, target_sets::int, target_reps::int, target_rpe, rest_seconds::int
    from public.routine_exercises where routine_id = 'a0000000-0000-4000-8000-0000000000a1' order by position$$,
  $$select 0, (select id from cat where n = 3), 0, 2, 10, null::numeric, 120
    union all select 1, (select id from cat where n = 1), 0, 2, 5, 8.0::numeric, 150$$,
  'the routine equals the session: order, supersets, summaries, rests (session, else previous, else 120) and carried rpe');
select results_eq(
  $$select s.set_index::int, s.target_weight_kg::int, s.target_reps::int, s.set_type::text
    from public.routine_exercise_sets s join public.routine_exercises e on e.id = s.routine_exercise_id
    where e.routine_id = 'a0000000-0000-4000-8000-0000000000a1' and e.position = 0 order by s.set_index$$,
  $$values (1, 10, 12, 'warm_up'), (2, 20, 10, 'working')$$,
  'position 0 sets keep weight, reps and type and ignore the pending set');
select results_eq(
  $$select s.set_index::int, s.target_weight_kg::int, s.target_reps::int, s.set_type::text
    from public.routine_exercise_sets s join public.routine_exercises e on e.id = s.routine_exercise_id
    where e.routine_id = 'a0000000-0000-4000-8000-0000000000a1' and e.position = 1 order by s.set_index$$,
  $$values (1, 50, 5, 'working'), (2, 55, 4, 'failure')$$,
  'position 1 sets are replaced by the session sets');
select is((select count(*)::int from public.routine_exercise_sets s join public.routine_exercises e on e.id = s.routine_exercise_id
           where e.routine_id = 'a0000000-0000-4000-8000-0000000000a1'), 4,
  'only completed sets enter the routine (an exercise with none is dropped)');
select results_eq(
  $$select name, notes, track_as_routine, auto_update_routine from public.routines
    where id = 'a0000000-0000-4000-8000-0000000000a1'$$,
  $$values ('Rutina A', 'nota', true, false)$$, 'name, notes and flags are not touched');
select is((select count(*)::int from public.workout_sets where session_id = 'c0000000-0000-4000-8000-000000000001'), 6,
  'the session''s sets are not modified');
select is((select count(*)::int from public.workout_sets where session_id = 'c0000000-0000-4000-8000-000000000000'), 1,
  'an earlier session is unchanged');
select results_eq(
  $$select e.position::int, e.rest_seconds::int, s.target_weight_kg::int
    from public.routine_exercises e join public.routine_exercise_sets s on s.routine_exercise_id = e.id
    where e.routine_id = 'a0000000-0000-4000-8000-0000000000a2'$$,
  $$values (0, 60, 20)$$, 'the sets of other routines are untouched');

-- Atomic: more than 10 sets fails and leaves the routine as it was.
select throws_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-000000000004')$$,
  '22023', null, 'more than 10 completed sets of an exercise is rejected');
select results_eq(
  $$select e.position::int, e.rest_seconds::int, (select count(*)::int from public.routine_exercise_sets s where s.routine_exercise_id = e.id)
    from public.routine_exercises e where e.routine_id = 'a0000000-0000-4000-8000-0000000000a3'$$,
  $$values (0, 45, 1)$$, 'a failed update leaves the routine intact');

-- Previous rest and rpe carried by exercise_id; superset with unequal counts is nulled; reps clamped.
select lives_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-000000000003')$$,
  'update with a superset of unequal set counts succeeds');
select results_eq(
  $$select position::int, exercise_id, superset_group::int, target_sets::int, target_reps::int, target_rpe, rest_seconds::int
    from public.routine_exercises where routine_id = 'a0000000-0000-4000-8000-0000000000a3' order by position$$,
  $$select 0, (select id from cat where n = 1), null::int, 2, 50, 7.0::numeric, 45
    union all select 1, (select id from cat where n = 2), null::int, 1, 1, null::numeric, 120$$,
  'unequal superset is nulled, reps clamp to 1-50, rest and rpe come from the previous routine exercise');
select results_eq(
  $$select s.set_index::int, s.target_reps::int from public.routine_exercise_sets s
    join public.routine_exercises e on e.id = s.routine_exercise_id
    where e.routine_id = 'a0000000-0000-4000-8000-0000000000a3' and e.position = 0 order by s.set_index$$,
  $$values (1, 50), (2, 5)$$, 'set reps are clamped to 1-50');

select throws_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-0000000000bb')$$,
  'P0002', null, 'A cannot update a routine from B''s session');
select throws_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-0000000000ee')$$,
  'P0002', null, 'an in-progress session is rejected');
select throws_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-0000000000dd')$$,
  'P0002', null, 'a session without a routine (deleted) is rejected');

-- ---------------------------------------------------------------------------
-- save_routine flags
-- ---------------------------------------------------------------------------
create temp table ids (k text primary key, v uuid);
grant all on ids to authenticated;
insert into ids values ('f1', public.save_routine('{"name":"Con flags","track_as_routine":false,"auto_update_routine":true,"exercises":[]}'::jsonb));
insert into ids values ('f2', public.save_routine('{"name":"Sin flags","exercises":[]}'::jsonb));
select results_eq(
  $$select track_as_routine, auto_update_routine from public.routines where id = (select v from ids where k = 'f1')$$,
  $$values (false, true)$$, 'save_routine creates with the given flags');
select results_eq(
  $$select track_as_routine, auto_update_routine from public.routines where id = (select v from ids where k = 'f2')$$,
  $$values (true, false)$$, 'save_routine creates with the column defaults when the flags are omitted');
select public.save_routine(jsonb_build_object('id', (select v from ids where k = 'f1'), 'name', 'Con flags', 'exercises', '[]'::jsonb));
select results_eq(
  $$select track_as_routine, auto_update_routine from public.routines where id = (select v from ids where k = 'f1')$$,
  $$values (false, true)$$, 'an update that omits the flags leaves them unchanged');
select public.save_routine(jsonb_build_object('id', (select v from ids where k = 'f1'), 'name', 'Con flags',
  'auto_update_routine', false, 'exercises', '[]'::jsonb));
select results_eq(
  $$select track_as_routine, auto_update_routine from public.routines where id = (select v from ids where k = 'f1')$$,
  $$values (false, false)$$, 'an update with one flag changes only that flag');
select throws_ok($$select public.save_routine('{"name":"X","auto_update_routine":"maybe","exercises":[]}'::jsonb)$$,
  '22023', null, 'an invalid flag value is rejected');

-- ---------------------------------------------------------------------------
-- As user B: cannot touch A's data
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select throws_ok($$select public.set_session_rest('c0000000-0000-4000-8000-0000000000ee', 0, 30)$$,
  'P0002', null, 'B cannot set a rest on A''s session');
select throws_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-000000000001')$$,
  'P0002', null, 'B cannot update A''s routine from A''s session');

select * from finish();
rollback;
