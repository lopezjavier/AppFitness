-- pgTAP: exercises.per_hand, catalogue flags, update_custom_exercise, delete_custom_exercise (T-097i, US-09, US-32).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(23);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('a7000000-0000-4000-8000-000000000001', 'Libre', 'chest', 'dumbbell', 'weight_reps', true, '11111111-1111-4111-8111-111111111111'),
  ('a7000000-0000-4000-8000-000000000002', 'Con series', 'back', 'cable', 'weight_reps', true, '11111111-1111-4111-8111-111111111111'),
  ('a7000000-0000-4000-8000-000000000003', 'En rutina', 'back', 'cable', 'weight_reps', true, '11111111-1111-4111-8111-111111111111'),
  ('b7000000-0000-4000-8000-000000000001', 'De Bea', 'chest', 'dumbbell', 'weight_reps', true, '22222222-2222-4222-8222-222222222222');

insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a7100000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Ana', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00');
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a7100000-0000-4000-8000-000000000001', 'a7000000-0000-4000-8000-000000000002', 0, 1, 'working', 20, 10, '2026-10-12 10:05+00');
insert into public.routines (id, user_id, name) values
  ('a7200000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Rutina');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps) values
  ('a7200000-0000-4000-8000-000000000001', 'a7000000-0000-4000-8000-000000000003', 0, 3, 10);

-- ---------------------------------------------------------------------------
-- Schema and catalogue data
-- ---------------------------------------------------------------------------
select col_type_is('public', 'exercises', 'per_hand', 'boolean', 'per_hand is boolean');
select col_not_null('public', 'exercises', 'per_hand', 'per_hand is not null');
select col_default_is('public', 'exercises', 'per_hand', 'false', 'per_hand defaults to false');
select is((select count(*)::int from public.exercises where per_hand and not is_custom), 178,
  'exactly 178 catalogue exercises are flagged per hand');
select is((select per_hand from public.exercises where key = 'hammer-curl'), true, 'hammer-curl is per hand');
select is((select per_hand from public.exercises where key = 'concentration-curl'), false,
  'concentration-curl (one hand) is not');
select is((select per_hand from public.exercises where key = 'bench-press'), false, 'bench-press is not');
select ok(has_function_privilege('authenticated', 'public.update_custom_exercise(uuid, text, public.muscle_group, public.equipment, boolean)', 'execute'),
  'authenticated can execute update_custom_exercise');
select ok(not has_function_privilege('anon', 'public.update_custom_exercise(uuid, text, public.muscle_group, public.equipment, boolean)', 'execute'),
  'anon cannot execute update_custom_exercise');
select ok(has_function_privilege('authenticated', 'public.delete_custom_exercise(uuid)', 'execute'),
  'authenticated can execute delete_custom_exercise');
select ok(not has_function_privilege('anon', 'public.delete_custom_exercise(uuid)', 'execute'),
  'anon cannot execute delete_custom_exercise');

-- ---------------------------------------------------------------------------
-- As Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$select public.update_custom_exercise('a7000000-0000-4000-8000-000000000001', '  Press unilateral  ', 'shoulders', 'machine', true)$$,
  'Ana updates her own exercise');
select results_eq(
  $$select name, muscle_group::text, equipment::text, per_hand, metric_type::text from public.exercises
    where id = 'a7000000-0000-4000-8000-000000000001'$$,
  $$values ('Press unilateral', 'shoulders', 'machine', true, 'weight_reps')$$,
  'the new values are stored (name trimmed) and the metric type is untouched');
select throws_ok(
  $$select public.update_custom_exercise('a7000000-0000-4000-8000-000000000001', '   ', 'chest', 'dumbbell', false)$$,
  '22023', null, 'a blank name is rejected');
select throws_ok(
  $$select public.update_custom_exercise('a7000000-0000-4000-8000-000000000001', repeat('x', 101), 'chest', 'dumbbell', false)$$,
  '22023', null, 'a name over 100 characters is rejected');
select throws_ok(
  $$select public.update_custom_exercise('b7000000-0000-4000-8000-000000000001', 'Hack', 'chest', 'dumbbell', true)$$,
  'P0002', null, 'Ana cannot update Bea''s exercise');
select throws_ok(
  $$select public.update_custom_exercise((select id from public.exercises where key = 'hammer-curl'), 'Hack', 'biceps', 'dumbbell', false)$$,
  'P0002', null, 'a catalogue exercise cannot be updated');
select throws_ok(
  $$select public.delete_custom_exercise('b7000000-0000-4000-8000-000000000001')$$,
  'P0002', null, 'Ana cannot delete Bea''s exercise');
select throws_ok(
  $$select public.delete_custom_exercise((select id from public.exercises where key = 'hammer-curl'))$$,
  'P0002', null, 'a catalogue exercise cannot be deleted');
select throws_ok(
  $$select public.delete_custom_exercise('a7000000-0000-4000-8000-000000000002')$$,
  '23503', null, 'an exercise with logged sets cannot be deleted');
select throws_ok(
  $$select public.delete_custom_exercise('a7000000-0000-4000-8000-000000000003')$$,
  '23503', null, 'an exercise in a routine cannot be deleted');
select lives_ok($$select public.delete_custom_exercise('a7000000-0000-4000-8000-000000000001')$$,
  'an unused own exercise is deleted');

reset role;
select is((select count(*)::int from public.exercises
           where id in ('a7000000-0000-4000-8000-000000000001') or id = 'a7000000-0000-4000-8000-000000000002'
              or id = 'a7000000-0000-4000-8000-000000000003' or id = 'b7000000-0000-4000-8000-000000000001'), 3,
  'only the unused exercise was removed; Bea''s and the in-use ones remain');

select * from finish();
rollback;
