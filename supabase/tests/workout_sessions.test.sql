-- pgTAP: workout_sessions, workout_sets, session RPCs and exercise_history (T-044, US-11/US-15).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(63);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

insert into public.exercises (id, name, muscle_group, equipment, is_custom, created_by) values
  ('bbbbbbbb-0000-4000-8000-000000000001', 'Remo B', 'back', 'cable', true, '22222222-2222-4222-8222-222222222222'),
  ('cccccccc-0000-4000-8000-000000000001', 'Remo C', 'back', 'cable', true, '33333333-3333-4333-8333-333333333333');

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises where not is_custom limit 3;
grant select on cat to authenticated;

-- Fixtures (as postgres)
insert into public.routines (id, user_id, name) values
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Torso A'),
  ('a0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Vacia A'),
  ('b0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Rutina B');
insert into public.routine_exercises (routine_id, exercise_id, position, superset_group, target_sets, target_reps) values
  ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 3, 8),
  ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 1, 1, 3, 8);

-- B has a completed session with a set (isolation checks) and an in-progress one.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Sesion B', 'completed',
   '2026-01-10 10:00+00', '2026-01-10 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at) values
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 200, 5, '2026-01-10 10:30+00');

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'session_status', array['in_progress', 'completed', 'discarded'], 'session_status labels');
select ok(
  (select bool_and(relrowsecurity and relforcerowsecurity) from pg_class
   where oid in ('public.workout_sessions'::regclass, 'public.workout_sets'::regclass)),
  'RLS is enabled and forced on both session tables');
select policies_are('public', 'workout_sessions', array[
  'workout_sessions_select', 'workout_sessions_insert', 'workout_sessions_update', 'workout_sessions_delete'],
  'workout_sessions has four policies');
select policies_are('public', 'workout_sets', array[
  'workout_sets_select', 'workout_sets_insert', 'workout_sets_update', 'workout_sets_delete'],
  'workout_sets has four policies');
select table_privs_are('public', 'workout_sessions', 'anon', array[]::text[], 'anon has no privileges on sessions');
select table_privs_are('public', 'workout_sets', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'], 'authenticated has CRUD without TRUNCATE on sets');
select function_privs_are('public', 'start_workout_session', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute start_workout_session');
select function_privs_are('public', 'finish_workout_session', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute finish_workout_session');
select function_privs_are('public', 'discard_workout_session', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute discard_workout_session');
select function_privs_are('public', 'exercise_history', array['uuid', 'date'], 'anon', array[]::text[],
  'anon cannot execute exercise_history');

-- ---------------------------------------------------------------------------
-- Checks and keys (as postgres)
-- ---------------------------------------------------------------------------
insert into public.workout_sessions (id, user_id, name) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Sesion check');

select throws_ok($$insert into public.workout_sessions (user_id, name)
  values ('11111111-1111-4111-8111-111111111111', '   ')$$,
  '23514', null, 'blank session name rejected');
select throws_ok($$insert into public.workout_sessions (user_id, name)
  values ('11111111-1111-4111-8111-111111111111', 'Segunda en curso')$$,
  '23505', null, 'second in-progress session for the same user rejected');
select throws_ok($$insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  values ('11111111-1111-4111-8111-111111111111', 'Al reves', 'completed', '2026-02-02 10:00+00', '2026-02-02 09:00+00')$$,
  '23514', null, 'ended_at before started_at rejected');
select throws_ok($$insert into public.workout_sessions (user_id, name, status, ended_at)
  values ('11111111-1111-4111-8111-111111111111', 'En curso con fin', 'in_progress', now())$$,
  '23514', null, 'in-progress session with ended_at rejected');
select lives_ok($$insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  values ('11111111-1111-4111-8111-111111111111', 'Antigua', 'completed', '2026-02-01 10:00+00', '2026-02-01 11:00+00')$$,
  'a completed session coexists with the in-progress one');

select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, -1)$$,
  '23514', null, 'negative weight rejected');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, reps)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, -1)$$,
  '23514', null, 'negative reps rejected');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 0)$$,
  '23514', null, 'rpe 0 rejected');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 11)$$,
  '23514', null, 'rpe 11 rejected');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 0)$$,
  '23514', null, 'set_index 0 rejected');

insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps) values
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 50, 10);
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1)$$,
  '23505', null, 'duplicate (session, position, set_index) rejected');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 60, 8)
  on conflict (session_id, exercise_position, set_index)
  do update set weight_kg = excluded.weight_kg, reps = excluded.reps;
select results_eq(
  $$select count(*)::int, max(weight_kg)::int from public.workout_sets
    where session_id = 'a1000000-0000-4000-8000-000000000001'$$,
  $$values (1, 60)$$,
  'upsert on the key replaces the row instead of duplicating it');

-- Clean A's fixtures so the RPC tests start from no sessions.
delete from public.workout_sessions where user_id = '11111111-1111-4111-8111-111111111111';

-- ---------------------------------------------------------------------------
-- RLS isolation and RPCs (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.workout_sessions), 0, 'A does not see B''s sessions');
select is((select count(*)::int from public.workout_sets), 0, 'A does not see B''s sets');
select is_empty($$update public.workout_sessions set name = 'hack'
  where id = 'b1000000-0000-4000-8000-000000000001' returning 1$$, 'A cannot update B''s session');
select is_empty($$delete from public.workout_sessions
  where id = 'b1000000-0000-4000-8000-000000000001' returning 1$$, 'A cannot delete B''s session');
select throws_ok($$insert into public.workout_sessions (user_id, name)
  values ('22222222-2222-4222-8222-222222222222', 'Para B')$$,
  '42501', null, 'A cannot insert a session for B');
select throws_ok($$insert into public.workout_sessions (name, routine_id)
  values ('Con rutina ajena', 'b0000000-0000-4000-8000-000000000001')$$,
  '42501', null, 'A cannot reference B''s routine in a session');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index)
  values ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 0, 5)$$,
  '42501', null, 'A cannot insert a set into B''s session');
select is_empty($$update public.workout_sets set reps = 99
  where session_id = 'b1000000-0000-4000-8000-000000000001' returning 1$$, 'A cannot update B''s sets');
select throws_ok($$select public.start_workout_session('b0000000-0000-4000-8000-000000000001')$$,
  'P0002', null, 'A cannot start a session from B''s routine');

-- start from routine
create temp table started (id uuid);
grant all on started to authenticated;
insert into started select public.start_workout_session('a0000000-0000-4000-8000-000000000001');
select results_eq(
  $$select name, status::text from public.workout_sessions where id = (select id from started)$$,
  $$values ('Torso A'::text, 'in_progress'::text)$$,
  'start_workout_session snapshots the routine name and is in progress');
select results_eq(
  $$select exercise_position::int, superset_group::int, set_index::int, weight_kg::int, reps::int,
           completed_at is null
    from public.workout_sets where session_id = (select id from started)
    order by exercise_position, set_index$$,
  $$values (0, 1, 1, 0, 0, true), (0, 1, 2, 0, 0, true), (0, 1, 3, 0, 0, true),
           (1, 1, 1, 0, 0, true), (1, 1, 2, 0, 0, true), (1, 1, 3, 0, 0, true)$$,
  'a 3x8 + 3x8 routine creates six pending sets with positions and superset group');
select throws_ok($$select public.start_workout_session()$$,
  '23505', null, 'starting a second session while one is in progress fails');

-- complete two sets, then finish: pending dropped
update public.workout_sets set weight_kg = 60, reps = 12, completed_at = now()
  where session_id = (select id from started) and exercise_position = 0 and set_index = 1;
update public.workout_sets set weight_kg = 87.5, reps = 8, completed_at = now()
  where session_id = (select id from started) and exercise_position = 0 and set_index in (2, 3);
select lives_ok($$select public.finish_workout_session((select id from started))$$, 'finish succeeds');
select results_eq(
  $$select status::text, ended_at is not null from public.workout_sessions where id = (select id from started)$$,
  $$values ('completed'::text, true)$$,
  'finish marks the session completed with ended_at');
select is((select count(*)::int from public.workout_sets where session_id = (select id from started)), 3,
  'finish deletes only the pending sets');
select throws_ok($$select public.finish_workout_session((select id from started))$$,
  'P0002', null, 'finishing a completed session raises P0002');
select throws_ok($$select public.finish_workout_session('b1000000-0000-4000-8000-000000000001')$$,
  'P0002', null, 'A cannot finish B''s session');

-- empty start and discard
create temp table started2 (id uuid);
grant all on started2 to authenticated;
insert into started2 select public.start_workout_session();
select results_eq(
  $$select name, routine_id is null,
           (select count(*)::int from public.workout_sets where session_id = (select id from started2))
    from public.workout_sessions where id = (select id from started2)$$,
  $$values ('Entreno libre'::text, true, 0)$$,
  'an empty start creates a free session without sets');
select lives_ok($$select public.discard_workout_session((select id from started2))$$, 'discard succeeds');
select results_eq(
  $$select status::text, ended_at is not null from public.workout_sessions where id = (select id from started2)$$,
  $$values ('discarded'::text, true)$$,
  'discard marks the session discarded');
select throws_ok($$select public.discard_workout_session((select id from started2))$$,
  'P0002', null, 'discarding a non in-progress session raises P0002');

-- a routine with no exercises starts a session with no sets
create temp table started3 (id uuid);
grant all on started3 to authenticated;
insert into started3 select public.start_workout_session('a0000000-0000-4000-8000-000000000002');
select is((select name from public.workout_sessions where id = (select id from started3)), 'Vacia A',
  'a routine without exercises starts a named session');
select lives_ok($$select public.discard_workout_session((select id from started3))$$, 'discard the third session');

-- T-089: one pending set per planned set, carrying its type and planned order
create temp table planned_routine (id uuid);
grant all on planned_routine to authenticated;
insert into planned_routine select public.save_routine(jsonb_build_object(
  'name', 'Press planificado',
  'exercises', jsonb_build_array(jsonb_build_object(
    'exercise_id', (select id from cat where n = 1),
    'sets', jsonb_build_array(
      jsonb_build_object('weight_kg', 40, 'reps', 10, 'set_type', 'warm_up'),
      jsonb_build_object('weight_kg', 80, 'reps', 8),
      jsonb_build_object('weight_kg', 80, 'reps', 8),
      jsonb_build_object('reps', 6, 'set_type', 'failure'))))));
create temp table started5 (id uuid);
grant all on started5 to authenticated;
insert into started5 select public.start_workout_session((select id from planned_routine));
select results_eq(
  $$select set_index::int, set_type::text, weight_kg::int, reps::int, completed_at is null
    from public.workout_sets where session_id = (select id from started5) order by set_index$$,
  $$values (1, 'warm_up', 0, 0, true), (2, 'working', 0, 0, true), (3, 'working', 0, 0, true),
           (4, 'failure', 0, 0, true)$$,
  'start creates one pending set per planned set with its type in planned order');
select lives_ok($$select public.discard_workout_session((select id from started5))$$, 'discard the planned session');

-- US-10: editing or archiving a routine leaves performed sessions unchanged
create temp table started4 (id uuid);
grant all on started4 to authenticated;
insert into started4 select public.start_workout_session('a0000000-0000-4000-8000-000000000001');
create temp table sets_before as
  select exercise_id, exercise_position, set_index, superset_group
  from public.workout_sets where session_id = (select id from started4);
grant select on sets_before to authenticated;

select lives_ok(
  format($$select public.save_routine(jsonb_build_object(
    'id', 'a0000000-0000-4000-8000-000000000001', 'name', 'Torso renombrada',
    'exercises', jsonb_build_array(jsonb_build_object(
      'exercise_id', %L,
      'sets', jsonb_build_array(jsonb_build_object('reps', 5), jsonb_build_object('reps', 5))))))$$,
    (select id from cat where n = 3)),
  'renaming the routine and replacing its exercises succeeds');
update public.routines set archived = true where id = 'a0000000-0000-4000-8000-000000000001';
select is((select archived from public.routines where id = 'a0000000-0000-4000-8000-000000000001'), true,
  'the routine is archived');

select results_eq(
  $$select name, routine_id from public.workout_sessions where id = (select id from started4)$$,
  $$values ('Torso A'::text, 'a0000000-0000-4000-8000-000000000001'::uuid)$$,
  'the session keeps its snapshot name after the routine is renamed and archived');
select is((select count(*)::int from public.workout_sets where session_id = (select id from started4)), 6,
  'the session keeps its six sets after the routine is edited and archived');
select is_empty(
  $$select exercise_id, exercise_position, set_index, superset_group
    from public.workout_sets where session_id = (select id from started4)
    except select * from sets_before$$,
  'the session sets are identical to those created at start');
select public.discard_workout_session((select id from started4));

-- ---------------------------------------------------------------------------
-- exercise_history (as A; fixtures inserted as postgres)
-- ---------------------------------------------------------------------------
reset role;
-- Exercise 1: s1 (Jan 1) 100x5 -> 116.5 and 85x8 -> 107.5, volume 500+680 = 1180 (+ pending ignored)
--             s2 (Jan 8) 100x4 -> 113.5; volume 400 + 60x12 + 2 x 87.5x8 = 2520
--             s3 (Jan 15) only ineligible sets (reps 0, reps 13, weight 0, warm-up): 1RM null, volume 520
--             s4 discarded, s5 in progress, B's session: ignored
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a2000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'S1', 'completed',
   '2026-01-01 10:00+00', '2026-01-01 11:00+00'),
  ('a2000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'S2', 'completed',
   '2026-01-08 10:00+00', '2026-01-08 11:00+00'),
  ('a2000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'S3', 'completed',
   '2026-01-15 10:00+00', '2026-01-15 11:00+00'),
  ('a2000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'S4', 'discarded',
   '2026-01-20 10:00+00', '2026-01-20 11:00+00'),
  ('a2000000-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'S5', 'in_progress',
   '2026-01-25 10:00+00', null),
  ('a2000000-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111111', 'S6', 'completed',
   '2026-01-26 10:00+00', '2026-01-26 11:00+00');

insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a2000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 0, 1, 'working', 100, 5, '2026-01-01 10:10+00'),
  ('a2000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 0, 2, 'working', 85, 8, '2026-01-01 10:20+00'),
  ('a2000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 0, 3, 'working', 300, 5, null),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 3), 0, 1, 'working', 100, 4, '2026-01-08 10:10+00'),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 3), 0, 2, 'working', 60, 12, '2026-01-08 10:20+00'),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 3), 0, 3, 'working', 87.5, 8, '2026-01-08 10:30+00'),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 3), 0, 4, 'working', 87.5, 8, '2026-01-08 10:40+00'),
  ('a2000000-0000-4000-8000-000000000003', (select id from cat where n = 3), 0, 1, 'working', 100, 0, '2026-01-15 10:10+00'),
  ('a2000000-0000-4000-8000-000000000003', (select id from cat where n = 3), 0, 2, 'working', 40, 13, '2026-01-15 10:20+00'),
  ('a2000000-0000-4000-8000-000000000003', (select id from cat where n = 3), 0, 3, 'working', 0, 10, '2026-01-15 10:30+00'),
  ('a2000000-0000-4000-8000-000000000003', (select id from cat where n = 3), 0, 4, 'warm_up', 150, 3, '2026-01-15 10:40+00'),
  ('a2000000-0000-4000-8000-000000000004', (select id from cat where n = 3), 0, 1, 'working', 500, 5, '2026-01-20 10:10+00'),
  ('a2000000-0000-4000-8000-000000000005', (select id from cat where n = 3), 0, 1, 'working', 500, 5, '2026-01-25 10:10+00'),
  ('a2000000-0000-4000-8000-000000000006', (select id from cat where n = 3), 0, 1, 'working', 90, 1, '2026-01-26 10:10+00'),
  ('a2000000-0000-4000-8000-000000000006', (select id from cat where n = 1), 1, 1, 'working', 70, 10, '2026-01-26 10:20+00');
-- B's session with the same exercise
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at)
  values ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 3), 1, 1, 400, 5, '2026-01-10 10:40+00');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  $$select session_date::text, best_one_rm, total_volume from public.exercise_history((select id from cat where n = 3))
    order by session_date$$,
  $$values ('2026-01-01'::text, 116.5::numeric, 1180::numeric),
           ('2026-01-08'::text, 113.5::numeric, 2520::numeric),
           ('2026-01-15'::text, null::numeric, 520::numeric),
           ('2026-01-26'::text, 90::numeric, 90::numeric)$$,
  'history lists completed sessions only with 1RM, volume, pending/warm-up/other users ignored');
select is(
  (select best_one_rm from public.exercise_history((select id from cat where n = 3), '2026-01-08') order by session_date limit 1),
  113.5::numeric, 'p_from keeps sessions on or after the date (100 x 4 -> 113.5)');
select is((select count(*)::int from public.exercise_history((select id from cat where n = 3), '2026-01-09')), 2,
  'p_from excludes earlier sessions');
select is((select count(*)::int from public.exercise_history((select id from cat where n = 1))), 2,
  'history is per exercise (finished session volume 2120 plus the fixture session)');
select is((select total_volume from public.exercise_history((select id from cat where n = 1), null)
           where session_id = (select id from started)), 2120::numeric,
  'finished session volume is 60x12 + 87.5x8 + 87.5x8 = 2120');
select is((select count(*)::int from public.exercise_history('bbbbbbbb-0000-4000-8000-000000000001')), 0,
  'history of an unseen exercise is empty');
select is(
  (select best_one_rm from public.exercise_history((select id from cat where n = 3)) where session_date = '2026-01-01'),
  116.5::numeric, '100 x 5 and 85 x 8 give best 1RM 116.5');
select is(
  (select best_one_rm from public.exercise_history((select id from cat where n = 3)) where session_date = '2026-01-26'),
  90::numeric, 'a single rep uses the weight itself');

-- ---------------------------------------------------------------------------
-- Cascade on user deletion (as postgres): sessions + a custom exercise used in a set
-- ---------------------------------------------------------------------------
reset role;
insert into public.workout_sessions (id, user_id, name) values
  ('c1000000-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'Sesion C');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index) values
  ('c1000000-0000-4000-8000-000000000001', 'cccccccc-0000-4000-8000-000000000001', 0, 1);
select lives_ok($t$do $body$ begin
    delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
    set constraints all immediate;
  end $body$$t$,
  'deleting a user with a session using their custom exercise succeeds');
select is((select count(*)::int from public.workout_sessions where user_id = '33333333-3333-4333-8333-333333333333'), 0,
  'the deleted user''s sessions are gone');
select is((select count(*)::int from public.workout_sets where session_id = 'c1000000-0000-4000-8000-000000000001'), 0,
  'the deleted user''s sets are gone');

select * from finish();
rollback;
