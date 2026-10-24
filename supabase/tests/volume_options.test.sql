-- pgTAP: volume_factor() follows exercises.per_hand and its effect on exercise_history and
-- weekly_muscle_volume (T-097i, US-32; replaces the T-093 per-user double-dumbbell option).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(11);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.exercises (name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('Curl propio', 'forearms', 'cable', 'weight_reps', true, '11111111-1111-4111-8111-111111111111');

-- h1, h2: per-hand catalogue exercises; n1: catalogue exercise without the flag; c1: Ana's custom exercise.
create temp table cat as
  select 'h1' as k, (select id from public.exercises where key = 'hammer-curl') as id
  union all select 'h2', (select id from public.exercises where key = 'arnold-press')
  union all select 'n1', (select id from public.exercises where key = 'bench-press')
  union all select 'c1', (select id from public.exercises where name = 'Curl propio' and is_custom);
grant select on cat to authenticated;

-- Ana: one completed session (2026-10-12, a Monday), 20 x 10 of each exercise.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a6000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Ana', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00');
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'h1'), 0, 1, 'working', 20, 10, '2026-10-12 10:05+00'),
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'h2'), 1, 1, 'working', 20, 10, '2026-10-12 10:10+00'),
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'n1'), 2, 1, 'working', 20, 10, '2026-10-12 10:15+00'),
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'c1'), 3, 1, 'working', 20, 10, '2026-10-12 10:20+00');

select ok(has_function_privilege('authenticated', 'public.volume_factor(uuid)', 'execute'),
  'authenticated can execute volume_factor');
select ok(not has_function_privilege('anon', 'public.volume_factor(uuid)', 'execute'),
  'anon cannot execute volume_factor');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(public.volume_factor((select id from cat where k = 'h1')), 2::numeric,
  'a per-hand catalogue exercise has factor 2');
select is(public.volume_factor((select id from cat where k = 'n1')), 1::numeric,
  'an exercise without the flag has factor 1');
select is(public.volume_factor('99999999-9999-4999-8999-999999999999'), 1::numeric,
  'an unknown exercise has factor 1');
select results_eq(
  $$select best_one_rm, total_volume from public.exercise_history((select id from cat where k = 'h1'))$$,
  $$values (26.5::numeric, 400::numeric)$$, 'per hand: volume doubles (400) and the 1RM (26.5) does not');
select results_eq(
  $$select total_volume from public.exercise_history((select id from cat where k = 'n1'))$$,
  $$values (200::numeric)$$, 'without the flag: 20 x 10 is 200');
select results_eq(
  $$select muscle_group::text, total_volume from public.weekly_muscle_volume('2026-10-01') order by 1$$,
  $$values ('biceps', 400::numeric), ('chest', 200::numeric), ('forearms', 200::numeric), ('shoulders', 400::numeric)$$,
  'muscle volume doubles only the per-hand exercises'' groups');

select public.update_custom_exercise((select id from cat where k = 'c1'), 'Curl propio', 'forearms', 'cable', true);
select is(public.volume_factor((select id from cat where k = 'c1')), 2::numeric,
  'flagging a custom exercise makes its factor 2');
select results_eq(
  $$select total_volume from public.exercise_history((select id from cat where k = 'c1'))$$,
  $$values (400::numeric)$$, 'past volume of the custom exercise is recalculated');

-- Another user sees the same flag-derived factor but no data of Ana.
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.exercise_history((select id from cat where k = 'h1'))), 0,
  'Bea has no history for an exercise only Ana trained');
reset role;

select * from finish();
rollback;
