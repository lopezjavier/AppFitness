-- pgTAP: session controls (T-096, US-38): pause/resume accounting, delete_workout_set re-indexing,
-- last_performance routine filter and the previous_values_mode preference.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(37);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises
  where not is_custom and metric_type = 'weight_reps' limit 2;
grant select on cat to authenticated;

insert into public.routines (id, user_id, name) values
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Torso A'),
  ('a0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Pierna A');

-- Ana: open session with 4 sets of exercise 1 (position 0) and 2 sets at position 1; Bea: an open session.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, routine_id) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Open', 'in_progress',
   now() - interval '1 hour', null, null),
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Open B', 'in_progress',
   now() - interval '1 hour', null, null);
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps) values
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 10, 1),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 2, 20, 2),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 30, 3),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 4, 40, 4),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 1, 1, 5, 5),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 1, 2, 6, 6),
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 99, 9),
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 2, 98, 8);

-- Completed sessions for last_performance: routine A old, routine B newer, Bea newest.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, routine_id) values
  ('a2000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'In A', 'completed',
   '2026-01-01 10:00+00', '2026-01-01 11:00+00', 'a0000000-0000-4000-8000-000000000001'),
  ('a2000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'In B', 'completed',
   '2026-01-08 10:00+00', '2026-01-08 11:00+00', 'a0000000-0000-4000-8000-000000000002'),
  ('a2000000-0000-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed',
   '2026-02-01 10:00+00', '2026-02-01 11:00+00', null);
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at) values
  ('a2000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 50, 10, '2026-01-01 10:10+00'),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 1), 0, 1, 70, 5, '2026-01-08 10:10+00'),
  ('a2000000-0000-4000-8000-000000000002', (select id from cat where n = 2), 1, 1, 25, 5, '2026-01-08 10:20+00'),
  ('a2000000-0000-4000-8000-000000000003', (select id from cat where n = 1), 0, 1, 400, 3, '2026-02-01 10:10+00');

-- Security
select function_privs_are('public', 'pause_workout_session', array['uuid', 'timestamp with time zone'], 'anon',
  array[]::text[], 'anon cannot execute pause_workout_session');
select function_privs_are('public', 'resume_workout_session', array['uuid', 'timestamp with time zone'], 'anon',
  array[]::text[], 'anon cannot execute resume_workout_session');
select function_privs_are('public', 'delete_workout_set', array['uuid', 'smallint', 'smallint'], 'anon',
  array[]::text[], 'anon cannot execute delete_workout_set');
select function_privs_are('public', 'delete_workout_set', array['uuid', 'smallint', 'smallint'], 'authenticated',
  array['EXECUTE'], 'authenticated can execute delete_workout_set');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Pause / resume
select is((select paused_total_s from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001'),
  0, 'a new session has no paused time');
select lives_ok($$select public.pause_workout_session('a1000000-0000-4000-8000-000000000001', '2026-03-01 10:00+00')$$,
  'pause an in-progress session');
select is((select paused_at from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001'),
  '2026-03-01 10:00+00'::timestamptz, 'pause stores the instant');
select lives_ok($$select public.pause_workout_session('a1000000-0000-4000-8000-000000000001', '2026-03-01 10:03+00')$$,
  'pausing twice is a no-op');
select is((select paused_at from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001'),
  '2026-03-01 10:00+00'::timestamptz, 'the first pause instant is kept');
select lives_ok($$select public.resume_workout_session('a1000000-0000-4000-8000-000000000001', '2026-03-01 10:05:30+00')$$,
  'resume a paused session');
select results_eq(
  $$select paused_total_s, paused_at from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001'$$,
  $$values (330, null::timestamptz)$$, 'resume adds the paused seconds and clears paused_at');
select lives_ok($$select public.resume_workout_session('a1000000-0000-4000-8000-000000000001', '2026-03-01 11:00+00')$$,
  'resuming a session that is not paused is a no-op');
select is((select paused_total_s from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001'),
  330, 'a redundant resume adds nothing');
select public.pause_workout_session('a1000000-0000-4000-8000-000000000001', '2026-03-01 12:00+00');
select public.resume_workout_session('a1000000-0000-4000-8000-000000000001', '2026-03-01 11:59+00');
select is((select paused_total_s from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001'),
  330, 'a resume before the pause instant adds zero and never goes negative');
select throws_ok($$select public.pause_workout_session('b1000000-0000-4000-8000-000000000001', now())$$,
  'P0002', null, 'A cannot pause B''s session');
select throws_ok($$select public.resume_workout_session('b1000000-0000-4000-8000-000000000001', now())$$,
  'P0002', null, 'A cannot resume B''s session');
select throws_ok($$select public.pause_workout_session('a2000000-0000-4000-8000-000000000001', now())$$,
  'P0002', null, 'a completed session cannot be paused');
select throws_ok($$select public.resume_workout_session('a2000000-0000-4000-8000-000000000001', now())$$,
  'P0002', null, 'a completed session cannot be resumed');

-- Delete set and re-index
select lives_ok($$select public.delete_workout_set('a1000000-0000-4000-8000-000000000001', 0::smallint, 2::smallint)$$,
  'delete a middle set');
select results_eq(
  $$select set_index::int, weight_kg::int from public.workout_sets
    where session_id = 'a1000000-0000-4000-8000-000000000001' and exercise_position = 0 order by set_index$$,
  $$values (1, 10), (2, 30), (3, 40)$$, 'later sets shift down by one');
select results_eq(
  $$select set_index::int from public.workout_sets
    where session_id = 'a1000000-0000-4000-8000-000000000001' and exercise_position = 1 order by set_index$$,
  $$values (1), (2)$$, 'other exercises are untouched');
select lives_ok($$select public.delete_workout_set('a1000000-0000-4000-8000-000000000001', 0::smallint, 3::smallint)$$,
  'delete the last set');
select results_eq(
  $$select set_index::int from public.workout_sets
    where session_id = 'a1000000-0000-4000-8000-000000000001' and exercise_position = 0 order by set_index$$,
  $$values (1), (2)$$, 'deleting the last set leaves the others as they were');
select lives_ok($$select public.delete_workout_set('a1000000-0000-4000-8000-000000000001', 0::smallint, 1::smallint)$$,
  'delete the first set');
select results_eq(
  $$select set_index::int, weight_kg::int from public.workout_sets
    where session_id = 'a1000000-0000-4000-8000-000000000001' and exercise_position = 0$$,
  $$values (1, 30)$$, 'the remaining set is re-indexed to 1');
select lives_ok($$select public.delete_workout_set('a1000000-0000-4000-8000-000000000001', 1::smallint, 9::smallint)$$,
  'deleting a missing row is idempotent');
select throws_ok($$select public.delete_workout_set('b1000000-0000-4000-8000-000000000001', 0::smallint, 1::smallint)$$,
  'P0002', null, 'A cannot delete a set of B''s session');

-- Finish while paused folds the open pause
select public.pause_workout_session('a1000000-0000-4000-8000-000000000001', now() - interval '10 minutes');
select public.finish_workout_session('a1000000-0000-4000-8000-000000000001');
select ok((select paused_total_s from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001') >= 330 + 599
  and (select paused_at from public.workout_sessions where id = 'a1000000-0000-4000-8000-000000000001') is null,
  'finish folds the open pause into paused_total_s and clears paused_at');
select throws_ok($$select public.delete_workout_set('a1000000-0000-4000-8000-000000000001', 0::smallint, 1::smallint)$$,
  'P0002', null, 'sets of a finished session cannot be deleted');

-- last_performance routine filter
select results_eq(
  $$select weight_kg::int from public.last_performance((select id from cat where n = 1), 'a0000000-0000-4000-8000-000000000001')$$,
  $$values (50)$$, 'routine filter ignores the newer session of another routine');
select is((select count(*)::int from public.last_performance((select id from cat where n = 2), 'a0000000-0000-4000-8000-000000000001')),
  0, 'an exercise never done in that routine has no previous values');
select results_eq(
  $$select weight_kg::int from public.last_performance((select id from cat where n = 1))$$,
  $$values (70)$$, 'a null routine keeps the overall behaviour and ignores Bea');

-- previous_values_mode
select is((select count(*)::int from public.training_preferences), 0, 'no preference row yet for Ana');
select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
  35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, 'routine');
select is((select previous_values_mode::text from public.training_preferences), 'routine',
  'save_preferences stores the mode');
select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
  35::smallint, 42::smallint, 23::smallint);
select is((select previous_values_mode::text from public.training_preferences), 'routine',
  'a null mode leaves it unchanged');

reset role;
select throws_ok($$update public.training_preferences set previous_values_mode = 'x'::public.previous_values_mode$$,
  '22P02', null, 'an invalid mode is rejected');
select is((select previous_values_mode::text from public.training_preferences
  where user_id = '22222222-2222-4222-8222-222222222222'), null, 'Bea has no row touched by Ana');

select * from finish();
rollback;
