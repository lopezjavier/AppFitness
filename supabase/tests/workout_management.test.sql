-- pgTAP: workout management (T-101, US-37): save_completed_workout, save_workout_as_routine and delete.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(62);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table ex as
  select (array_agg(id order by key))[1]::text as e1,
         (array_agg(id order by key))[2]::text as e2,
         (array_agg(id order by key))[3]::text as e3
  from (select id, key from public.exercises where not is_custom and metric_type = 'weight_reps'
        order by key limit 3) x;
grant select on ex to authenticated;
create temp table ids (k text primary key, id uuid);
grant select, insert on ids to authenticated;

-- Catalogue / grants ------------------------------------------------------------------------------------------
select has_function('public', 'save_completed_workout', array['jsonb'], 'save_completed_workout exists');
select has_function('public', 'save_workout_as_routine', array['uuid', 'text'], 'save_workout_as_routine exists');
select is((select not bool_or(prosecdef) from pg_proc
           where oid in ('public.save_completed_workout(jsonb)'::regprocedure,
                         'public.save_workout_as_routine(uuid, text)'::regprocedure)),
  true, 'both functions are security invoker');
select function_privs_are('public', 'save_completed_workout', array['jsonb'], 'anon', array[]::text[],
  'anon cannot execute save_completed_workout');
select function_privs_are('public', 'save_workout_as_routine', array['uuid', 'text'], 'anon', array[]::text[],
  'anon cannot execute save_workout_as_routine');

-- Fixtures (as postgres) --------------------------------------------------------------------------------------
insert into public.routines (id, user_id, name) values
  ('a0000000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'Existente'),
  ('b0000000-0000-4000-8000-0000000000b1', '22222222-2222-4222-8222-222222222222', 'Rutina B');

-- S0: A's recent completed session with ex1 (80 x 3); SB: B's completed session; SIP: A's in-progress session;
-- SR: source for save-as-routine (rests {"1":45}); SRG: equal-size superset; SR0: no completed sets; SR11: 11 sets.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, exercise_rests) values
  ('c0000000-0000-4000-8000-000000000000', '11111111-1111-4111-8111-111111111111', 'Reciente', 'completed',
   '2026-06-01 10:00+00', '2026-06-01 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-0000000000bb', '22222222-2222-4222-8222-222222222222', 'De B', 'completed',
   '2026-06-02 10:00+00', '2026-06-02 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-0000000000aa', '11111111-1111-4111-8111-111111111111', 'Fuente', 'completed',
   '2026-01-03 10:00+00', '2026-01-03 11:00+00', '{"1": 45}'),
  ('c0000000-0000-4000-8000-0000000000ab', '11111111-1111-4111-8111-111111111111', 'Super', 'completed',
   '2026-01-04 10:00+00', '2026-01-04 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-0000000000a0', '11111111-1111-4111-8111-111111111111', 'Vacia', 'completed',
   '2026-01-05 10:00+00', '2026-01-05 11:00+00', '{}'),
  ('c0000000-0000-4000-8000-0000000000a9', '11111111-1111-4111-8111-111111111111', 'Once', 'completed',
   '2026-01-06 10:00+00', '2026-01-06 11:00+00', '{}');
insert into public.workout_sessions (id, user_id, name, status, started_at) values
  ('c0000000-0000-4000-8000-0000000000ee', '11111111-1111-4111-8111-111111111111', 'En curso', 'in_progress',
   '2026-06-07 10:00+00');

insert into public.workout_sets (session_id, exercise_id, exercise_position, superset_group, set_index, set_type,
                                 weight_kg, reps, completed_at)
select s.session_id::uuid, s.ex::uuid, s.pos, s.grp, s.idx, s.typ::public.set_type, s.w, s.r, s.done
from ex, lateral (values
  ('c0000000-0000-4000-8000-000000000000', ex.e1, 0, null, 1, 'working', 80, 3, timestamptz '2026-06-01 10:10+00'),
  ('c0000000-0000-4000-8000-0000000000bb', ex.e1, 0, null, 1, 'working', 10, 10, timestamptz '2026-06-02 10:10+00'),
  -- SR: pos0 ex1 (superset 0, 3 sets), pos1 ex2 (superset 0, 2 sets), pos2 ex3 (1 set, reps 0), pending set ignored
  ('c0000000-0000-4000-8000-0000000000aa', ex.e1, 0, 0, 1, 'warm_up', 20, 10, timestamptz '2026-01-03 10:10+00'),
  ('c0000000-0000-4000-8000-0000000000aa', ex.e1, 0, 0, 2, 'working', 50, 5, timestamptz '2026-01-03 10:11+00'),
  ('c0000000-0000-4000-8000-0000000000aa', ex.e1, 0, 0, 3, 'failure', 55, 0, timestamptz '2026-01-03 10:12+00'),
  ('c0000000-0000-4000-8000-0000000000aa', ex.e2, 1, 0, 1, 'working', 30, 8, timestamptz '2026-01-03 10:13+00'),
  ('c0000000-0000-4000-8000-0000000000aa', ex.e2, 1, 0, 2, 'working', 30, 8, timestamptz '2026-01-03 10:14+00'),
  ('c0000000-0000-4000-8000-0000000000aa', ex.e3, 2, null, 1, 'working', 0, 0, timestamptz '2026-01-03 10:15+00'),
  ('c0000000-0000-4000-8000-0000000000aa', ex.e3, 2, null, 2, 'working', 0, 0, null),
  -- SRG: equal-size superset keeps its group
  ('c0000000-0000-4000-8000-0000000000ab', ex.e1, 0, 1, 1, 'working', 40, 8, timestamptz '2026-01-04 10:10+00'),
  ('c0000000-0000-4000-8000-0000000000ab', ex.e2, 1, 1, 1, 'working', 40, 8, timestamptz '2026-01-04 10:11+00'),
  -- SR0: only pending
  ('c0000000-0000-4000-8000-0000000000a0', ex.e1, 0, null, 1, 'working', 0, 0, null)
) as s(session_id, ex, pos, grp, idx, typ, w, r, done);
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at)
select 'c0000000-0000-4000-8000-0000000000a9', ex.e1::uuid, 0, g, 10, 10, '2026-01-06 10:10+00'
from ex, generate_series(1, 11) as g;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps)
select 'c0000000-0000-4000-8000-0000000000ee', ex.e1::uuid, 0, 1, 0, 0 from ex;

-- ---------------------------------------------------------------------------------------------------------------
-- Unauthenticated
-- ---------------------------------------------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '28000', null, 'save_completed_workout without a session is rejected');
select throws_ok($q$select public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000aa', 'X')$q$,
  '28000', null, 'save_workout_as_routine without a session is rejected');

-- ---------------------------------------------------------------------------------------------------------------
-- As user A: create a past workout (another session is in progress)
-- ---------------------------------------------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

insert into ids
select 'w1', public.save_completed_workout((select replace(replace($j$
  {"name":"  Pierna  ","description":"   ","gym":"Gym X","started_at":"2026-05-06T12:00:00Z","duration_s":3600,
   "exercises":[
     {"exercise_id":"E1","rest_seconds":90,"sets":[{"set_type":"working","rpe":8,"weight_kg":100,"reps":5},
                                                   {"set_type":"warm_up","weight_kg":40,"reps":10}]},
     {"exercise_id":"E2","sets":[{"weight_kg":50,"reps":8}]}]}$j$, 'E1', e1), 'E2', e2) from ex)::jsonb);

select results_eq(
  $$select status::text, started_at, ended_at, name, description, gym, routine_id, exercise_rests
    from public.workout_sessions where id = (select id from ids where k = 'w1')$$,
  $$select 'completed', timestamptz '2026-05-06 12:00+00', timestamptz '2026-05-06 13:00+00', 'Pierna',
           null::text, 'Gym X', null::uuid, '{"0": 90}'::jsonb$$,
  'a past workout is stored completed with the given date, end, trimmed name, blank description null and rests');
select results_eq(
  $$select exercise_position::int, set_index::int, set_type::text, rpe, weight_kg::int, reps::int,
           completed_at = timestamptz '2026-05-06 13:00+00'
    from public.workout_sets where session_id = (select id from ids where k = 'w1')
    order by exercise_position, set_index$$,
  $$values (0, 1, 'working', 8.0::numeric, 100, 5, true), (0, 2, 'warm_up', null::numeric, 40, 10, true),
           (1, 1, 'working', null::numeric, 50, 8, true)$$,
  'positions and set indexes follow the array order and every set is completed');
select is((select count(*)::int from public.workout_sessions
           where user_id = '11111111-1111-4111-8111-111111111111' and status = 'in_progress'), 1,
  'the in-progress session is untouched and no second one was created');

-- Visibility in the aggregates
select is((select count(*)::int from public.exercise_history((select e1::uuid from ex))
           where session_id = (select id from ids where k = 'w1') and session_date = date '2026-05-06'), 1,
  'the past workout appears in exercise_history on its date');
select is((select heaviest_lift from public.exercise_session_records((select e1::uuid from ex))
           where session_id = (select id from ids where k = 'w1')), 100::numeric,
  'the past workout feeds exercise_session_records');
select is((select value from public.personal_records((select e1::uuid from ex)) where record_type = 'heaviest_lift'),
  100::numeric, 'the past workout sets the personal record');
select ok((select coalesce(sum(total_volume), 0) from public.weekly_muscle_volume(date '2026-05-04')
           where week_start = date '2026-05-04') > 0,
  'the past workout counts in the muscle volume of its week');
select is((select weight_kg from public.last_performance((select e1::uuid from ex)) where set_index = 1), 80::numeric,
  'a past workout does not displace a more recent session in last_performance');
select is((select weight_kg from public.last_performance((select e2::uuid from ex)) where set_index = 1), 50::numeric,
  'last_performance uses the past workout when it is the latest for that exercise');

-- From a routine, A's own; B's routine is rejected
select lives_ok(
  $q$insert into ids select 'w2', public.save_completed_workout((select replace('{"routine_id":"a0000000-0000-4000-8000-0000000000a1","name":"Desde rutina","started_at":"2026-05-07T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  'a past workout can start from the caller''s routine');
select is((select routine_id from public.workout_sessions where id = (select id from ids where k = 'w2')),
  'a0000000-0000-4000-8000-0000000000a1'::uuid, 'the routine is linked');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"routine_id":"b0000000-0000-4000-8000-0000000000b1","name":"X","started_at":"2026-05-07T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  'P0002', null, 'another user''s routine cannot be referenced');

-- Validation of create
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2999-01-01T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'a future date is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'a missing started_at is rejected on create');
select throws_ok($q$select public.save_completed_workout('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[]}'::jsonb)$q$,
  '22023', null, 'an empty workout is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'an exercise without sets is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"  ","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'a blank name is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5,"rpe":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'RPE 5 is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5,"rpe":7.5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'a fractional RPE is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5,"weight_kg":-1}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'a negative weight is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"reps":5,"set_type":"bogus"}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'an unknown set type is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","rest_seconds":601,"sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'a rest above 600 is rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace(replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","superset_group":0,"sets":[{"reps":5},{"reps":5}]},{"exercise_id":"E2","superset_group":0,"sets":[{"reps":5}]}]}', 'E1', e1), 'E2', e2) from ex)::jsonb)$q$,
  '22023', null, 'superset members with different set counts are rejected');
select throws_ok(
  $q$select public.save_completed_workout((select replace(replace('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"E1","superset_group":0,"sets":[{"reps":5}]},{"exercise_id":"E2","sets":[{"reps":5}]},{"exercise_id":"E1","superset_group":0,"sets":[{"reps":5}]}]}', 'E1', e1), 'E2', e2) from ex)::jsonb)$q$,
  '22023', null, 'non-consecutive superset members are rejected');
select throws_ok(
  $q$select public.save_completed_workout('{"name":"X","started_at":"2026-05-06T12:00:00Z","exercises":[{"exercise_id":"00000000-0000-4000-8000-00000000dead","sets":[{"reps":5}]}]}'::jsonb)$q$,
  '42501', null, 'an exercise that is not visible is rejected');
select is((select count(*)::int from public.workout_sessions where user_id = '11111111-1111-4111-8111-111111111111'),
  8, 'rejected creates leave no session behind');

-- ---------------------------------------------------------------------------------------------------------------
-- Update
-- ---------------------------------------------------------------------------------------------------------------
select lives_ok(
  $q$select public.save_completed_workout((select replace(replace(format('{"id":"%s","name":"Pierna 2","description":"nota","started_at":"2020-01-01T12:00:00Z","exercises":[{"exercise_id":"E1","sets":[{"weight_kg":120,"reps":5}]},{"exercise_id":"E2","sets":[{"weight_kg":50,"reps":8}]}]}', (select id from ids where k = 'w1')), 'E1', e1), 'E2', e2) from ex)::jsonb)$q$,
  'update of a completed workout succeeds');
select results_eq(
  $$select name, description, gym, started_at, ended_at, exercise_rests
    from public.workout_sessions where id = (select id from ids where k = 'w1')$$,
  $$select 'Pierna 2', 'nota', null::text, timestamptz '2026-05-06 12:00+00', timestamptz '2026-05-06 13:00+00',
           '{}'::jsonb$$,
  'update rewrites name, description, gym and rests, keeps the date and duration');
select results_eq(
  $$select exercise_position::int, weight_kg::int from public.workout_sets
    where session_id = (select id from ids where k = 'w1') order by exercise_position, set_index$$,
  $$values (0, 120), (1, 50)$$, 'update replaces the sets');
select is((select heaviest_lift from public.exercise_session_records((select e1::uuid from ex))
           where session_id = (select id from ids where k = 'w1')), 120::numeric,
  'an edit changes the aggregates');

select throws_ok(
  $q$select public.save_completed_workout((select replace(format('{"id":"%s","name":"Roto","description":"x","exercises":[{"exercise_id":"E1","rest_seconds":30,"sets":[{"weight_kg":999,"reps":5},{"reps":5,"rpe":5}]}]}', (select id from ids where k = 'w1')), 'E1', e1) from ex)::jsonb)$q$,
  '22023', null, 'an invalid set in an update is rejected');
select results_eq(
  $$select s.name, s.description, s.exercise_rests, (select count(*)::int from public.workout_sets w where w.session_id = s.id),
           (select max(w.weight_kg)::int from public.workout_sets w where w.session_id = s.id)
    from public.workout_sessions s where s.id = (select id from ids where k = 'w1')$$,
  $$select 'Pierna 2', 'nota', '{}'::jsonb, 2, 120$$,
  'a failed update leaves sets, description and rests untouched');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"id":"c0000000-0000-4000-8000-0000000000ee","name":"X","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  'P0002', null, 'an in-progress session cannot be updated');
select throws_ok(
  $q$select public.save_completed_workout((select replace('{"id":"c0000000-0000-4000-8000-0000000000bb","name":"X","exercises":[{"exercise_id":"E1","sets":[{"reps":5}]}]}', 'E1', e1) from ex)::jsonb)$q$,
  'P0002', null, 'A cannot update B''s session');

-- ---------------------------------------------------------------------------------------------------------------
-- save_workout_as_routine (as A)
-- ---------------------------------------------------------------------------------------------------------------
insert into ids select 'r1', public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000aa', '  Mi rutina  ');
select results_eq(
  $$select name, folder_id, is_next, track_as_routine, notes, position::int
    from public.routines where id = (select id from ids where k = 'r1')$$,
  $$select 'Mi rutina', null::uuid, false, true, null::text, 1$$,
  'a new routine is created without folder, at the end, with default flags');
select results_eq(
  $$select position::int, exercise_id::text, superset_group::int, target_sets::int, target_reps::int, rest_seconds::int
    from public.routine_exercises where routine_id = (select id from ids where k = 'r1') order by position$$,
  $$select 0, e1, null::int, 3, 5, 120 from ex
    union all select 1, e2, null::int, 2, 8, 45 from ex
    union all select 2, e3, null::int, 1, 1, 120 from ex$$,
  'exercises keep order; unequal superset is nulled; rest is the session rest else 120; reps summary clamped');
select results_eq(
  $$select e.position::int, s.set_index::int, s.target_weight_kg::int, s.target_reps::int, s.set_type::text
    from public.routine_exercise_sets s join public.routine_exercises e on e.id = s.routine_exercise_id
    where e.routine_id = (select id from ids where k = 'r1') and e.position = 0 order by s.set_index$$,
  $$values (0, 1, 20, 10, 'warm_up'), (0, 2, 50, 5, 'working'), (0, 3, 55, 1, 'failure')$$,
  'planned sets carry weight, reps (clamped to 1-50) and type; pending sets are ignored');
select is((select count(*)::int from public.workout_sets where session_id = 'c0000000-0000-4000-8000-0000000000aa'), 7,
  'the source session is not modified');

insert into ids select 'r2', public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000ab', 'Super');
select is((select count(*)::int from public.routine_exercises
           where routine_id = (select id from ids where k = 'r2') and superset_group = 1), 2,
  'an equal-size superset keeps its group');
select throws_ok($q$select public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000aa', '   ')$q$,
  '22023', null, 'a blank routine name is rejected');
select throws_ok($q$select public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000a0', 'X')$q$,
  '22023', null, 'a session without completed sets is rejected');
select throws_ok($q$select public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000a9', 'X')$q$,
  '22023', null, 'an exercise with 11 sets is rejected');
select throws_ok($q$select public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000ee', 'X')$q$,
  'P0002', null, 'an in-progress session is rejected');
select throws_ok($q$select public.save_workout_as_routine('c0000000-0000-4000-8000-0000000000bb', 'X')$q$,
  'P0002', null, 'A cannot save B''s session as a routine');
select is((select count(*)::int from public.routines where user_id = '11111111-1111-4111-8111-111111111111'), 3,
  'rejected calls create no routine');

-- ---------------------------------------------------------------------------------------------------------------
-- As user B: isolation
-- ---------------------------------------------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select throws_ok(
  $q$select public.save_completed_workout((select replace(format('{"id":"%s","name":"Hack","exercises":[{"exercise_id":"E1","sets":[{"reps":1}]}]}', (select id from ids where k = 'w1')), 'E1', e1) from ex)::jsonb)$q$,
  'P0002', null, 'B cannot update A''s session');
select throws_ok(
  $q$select public.save_workout_as_routine((select id from ids where k = 'w1'), 'Hack')$q$,
  'P0002', null, 'B cannot save A''s session as a routine');
delete from public.workout_sessions where id = (select id from ids where k = 'w1');

-- ---------------------------------------------------------------------------------------------------------------
-- As user A: delete
-- ---------------------------------------------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select is((select count(*)::int from public.workout_sessions where id = (select id from ids where k = 'w1')), 1,
  'B''s delete did not remove A''s session');
delete from public.workout_sessions where id = (select id from ids where k = 'w1');
select is((select count(*)::int from public.workout_sets where session_id = (select id from ids where k = 'w1')), 0,
  'delete removes the sets');
select is((select count(*)::int from public.exercise_history((select e1::uuid from ex))
           where session_id = (select id from ids where k = 'w1')), 0,
  'a deleted workout disappears from exercise_history');
select is((select count(*)::int from public.exercise_session_records((select e1::uuid from ex))
           where session_id = (select id from ids where k = 'w1')), 0,
  'a deleted workout disappears from the session records');
select is((select value from public.personal_records((select e1::uuid from ex)) where record_type = 'heaviest_lift'),
  80::numeric, 'a deleted workout no longer holds the personal record');
select is((select coalesce(sum(total_volume), 0) from public.weekly_muscle_volume(date '2026-05-04')
           where week_start = date '2026-05-04'), 0::numeric, 'a deleted workout leaves the weekly volume');
select is((select count(*)::int from public.workout_sessions where id = 'c0000000-0000-4000-8000-000000000000'), 1,
  'other sessions are untouched by a delete');

-- Deleting a user with sessions still succeeds
reset role;
select lives_ok($$delete from auth.users where id = '11111111-1111-4111-8111-111111111111'$$,
  'deleting a user with completed sessions succeeds');

select * from finish();
rollback;
