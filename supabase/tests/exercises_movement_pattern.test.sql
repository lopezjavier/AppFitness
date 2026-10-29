-- pgTAP: exercises.movement_pattern, catalogue data, update_custom_exercise with pattern (T-150, ADR-78).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(21);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('a8000000-0000-4000-8000-000000000001', 'Libre', 'chest', 'dumbbell', 'weight_reps', true, '11111111-1111-4111-8111-111111111111'),
  ('b8000000-0000-4000-8000-000000000001', 'De Bea', 'chest', 'dumbbell', 'weight_reps', true, '22222222-2222-4222-8222-222222222222');
update public.exercises set movement_pattern = 'carry' where id = 'b8000000-0000-4000-8000-000000000001';

-- ---------------------------------------------------------------------------
-- Schema and catalogue data
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'movement_pattern',
  array['squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull', 'carry',
        'core', 'isolation', 'cardio', 'other'],
  'movement_pattern has the 12 labels');
select col_type_is('public', 'exercises', 'movement_pattern', 'public.movement_pattern', 'column uses the enum');
select col_is_null('public', 'exercises', 'movement_pattern', 'movement_pattern is nullable');
select is((select count(*)::int from public.exercises where not is_custom and movement_pattern is null), 0,
  'no catalogue row has a null movement pattern');
select is((select movement_pattern::text from public.exercises where key = 'squat'), 'squat', 'squat is squat');
select is((select movement_pattern::text from public.exercises where key = 'bench-press'), 'horizontal_push',
  'bench-press is horizontal_push');
select is((select movement_pattern::text from public.exercises where key = 'pull-up'), 'vertical_pull',
  'pull-up is vertical_pull');
select is((select movement_pattern::text from public.exercises where key = 'bicep-curl'), 'isolation',
  'bicep-curl is isolation');
select ok(has_function_privilege('authenticated',
  'public.update_custom_exercise(uuid, text, public.muscle_group, public.equipment, boolean, public.movement_pattern)',
  'execute'), 'authenticated can execute update_custom_exercise');
select ok(not has_function_privilege('anon',
  'public.update_custom_exercise(uuid, text, public.muscle_group, public.equipment, boolean, public.movement_pattern)',
  'execute'), 'anon cannot execute update_custom_exercise');

-- ---------------------------------------------------------------------------
-- As Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$insert into public.exercises (id, name, muscle_group, equipment, is_custom)
    values ('a8000000-0000-4000-8000-000000000002', 'Sin patron', 'back', 'cable', true)$$,
  'a custom exercise can be created without a pattern');
select is((select movement_pattern::text from public.exercises where id = 'a8000000-0000-4000-8000-000000000002'),
  null, 'a custom exercise created without a pattern stores none');
select lives_ok(
  $$insert into public.exercises (id, name, muscle_group, equipment, is_custom, movement_pattern)
    values ('a8000000-0000-4000-8000-000000000003', 'Con patron', 'back', 'cable', true, 'horizontal_pull')$$,
  'a custom exercise can be created with a pattern');
select is((select movement_pattern::text from public.exercises where id = 'a8000000-0000-4000-8000-000000000003'),
  'horizontal_pull', 'the chosen pattern is stored');

select lives_ok(
  $$select public.update_custom_exercise('a8000000-0000-4000-8000-000000000001', 'Libre', 'chest', 'dumbbell', false, 'squat')$$,
  'Ana sets the pattern of her own exercise');
select is((select movement_pattern::text from public.exercises where id = 'a8000000-0000-4000-8000-000000000001'),
  'squat', 'the pattern was updated');
select lives_ok(
  $$select public.update_custom_exercise('a8000000-0000-4000-8000-000000000001', 'Libre', 'chest', 'dumbbell', false)$$,
  'the old five-argument call form still works (null pattern)');
select is((select movement_pattern::text from public.exercises where id = 'a8000000-0000-4000-8000-000000000001'),
  null, 'omitting or passing null clears the pattern');
select throws_ok(
  $$select public.update_custom_exercise('b8000000-0000-4000-8000-000000000001', 'De Bea', 'chest', 'dumbbell', false, 'core')$$,
  'P0002', null, 'Ana cannot change Bea''s pattern');
select throws_ok(
  $$select public.update_custom_exercise((select id from public.exercises where key = 'squat'), 'x', 'quads', 'barbell', false, 'core')$$,
  'P0002', null, 'a catalogue exercise cannot be updated through the RPC');
select throws_ok(
  $$insert into public.exercises (key, muscle_group, equipment, is_custom, movement_pattern)
    values ('mi-catalogo', 'chest', 'barbell', false, 'core')$$,
  '42501', null, 'a user cannot insert a catalogue (is_custom = false) row');

select * from finish();
rollback;
