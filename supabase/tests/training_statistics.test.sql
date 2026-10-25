-- pgTAP: training_summary, weekly_set_counts, muscle_group_sets, exercise_set_counts (T-114, US-40/US-41, 04 §4.11).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(35);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.exercises (name, muscle_group, secondary_muscles, equipment, metric_type, is_custom, created_by) values
  ('Press test', 'chest', '{triceps,shoulders,chest}', 'barbell', 'weight_reps', true, '11111111-1111-4111-8111-111111111111'),
  ('Curl test', 'biceps', '{forearms}', 'dumbbell', 'weight_reps', true, '11111111-1111-4111-8111-111111111111'),
  ('Dominada test', 'back', '{biceps}', 'bodyweight', 'bodyweight', true, '11111111-1111-4111-8111-111111111111');

create temp table cat as
  select 'b' as k, (select id from public.exercises where is_custom and name = 'Press test') as id
  union all select 'c', (select id from public.exercises where is_custom and name = 'Curl test')
  union all select 'd', (select id from public.exercises where is_custom and name = 'Dominada test')
  union all select 'cat', (select id from public.exercises where not is_custom and metric_type = 'weight_reps' and not per_hand order by key limit 1);
grant select on cat to authenticated;

-- Ana. Mondays: 2026-09-28 and 2026-10-05.
--  sA 10-01: bench 3 working 100x5 + 1 warm-up 60x5 + 1 pending; 1 h, paused 600 s -> 3000 s
--  sB 10-05 23:30 UTC: pull-up (bodyweight) + curl 20x10; paused longer than elapsed -> 0 s
--  sG 10-06 01:00+02 = 10-05 23:00 UTC: curl 10x10; 1 h
--  sH 10-06 00:00 UTC: bench 100x5; 1 h
--  sC discarded, sD in progress (ignored); sE 2025-01-01: only a completed warm-up set, 1 h
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at, paused_total_s) values
  ('a1000000-0000-4000-8000-00000000000a', '11111111-1111-4111-8111-111111111111', 'sA', 'completed', '2026-10-01 10:00+00', '2026-10-01 11:00+00', 600),
  ('a1000000-0000-4000-8000-00000000000b', '11111111-1111-4111-8111-111111111111', 'sB', 'completed', '2026-10-05 23:30+00', '2026-10-06 00:30+00', 5000),
  ('a1000000-0000-4000-8000-00000000000c', '11111111-1111-4111-8111-111111111111', 'sC', 'discarded', '2026-10-02 10:00+00', '2026-10-02 11:00+00', 0),
  ('a1000000-0000-4000-8000-00000000000e', '11111111-1111-4111-8111-111111111111', 'sE', 'completed', '2025-01-01 10:00+00', '2025-01-01 11:00+00', 0),
  ('a1000000-0000-4000-8000-00000000000f', '11111111-1111-4111-8111-111111111111', 'sG', 'completed', '2026-10-06 01:00+02', '2026-10-06 02:00+02', 0),
  ('a1000000-0000-4000-8000-000000000011', '11111111-1111-4111-8111-111111111111', 'sH', 'completed', '2026-10-06 00:00+00', '2026-10-06 01:00+00', 0);
insert into public.workout_sessions (id, user_id, name, status, started_at) values
  ('a1000000-0000-4000-8000-00000000000d', '11111111-1111-4111-8111-111111111111', 'sD', 'in_progress', '2026-10-03 10:00+00');

insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a1000000-0000-4000-8000-00000000000a', (select id from cat where k = 'b'), 0, 1, 'working', 100, 5, '2026-10-01 10:05+00'),
  ('a1000000-0000-4000-8000-00000000000a', (select id from cat where k = 'b'), 0, 2, 'working', 100, 5, '2026-10-01 10:10+00'),
  ('a1000000-0000-4000-8000-00000000000a', (select id from cat where k = 'b'), 0, 3, 'working', 100, 5, '2026-10-01 10:15+00'),
  ('a1000000-0000-4000-8000-00000000000a', (select id from cat where k = 'b'), 0, 4, 'warm_up', 60, 5, '2026-10-01 10:02+00'),
  ('a1000000-0000-4000-8000-00000000000a', (select id from cat where k = 'b'), 0, 5, 'working', 100, 5, null),
  ('a1000000-0000-4000-8000-00000000000b', (select id from cat where k = 'd'), 0, 1, 'working', 0, 10, '2026-10-05 23:40+00'),
  ('a1000000-0000-4000-8000-00000000000b', (select id from cat where k = 'c'), 1, 1, 'working', 20, 10, '2026-10-05 23:50+00'),
  ('a1000000-0000-4000-8000-00000000000c', (select id from cat where k = 'b'), 0, 1, 'working', 300, 5, '2026-10-02 10:05+00'),
  ('a1000000-0000-4000-8000-00000000000d', (select id from cat where k = 'b'), 0, 1, 'working', 400, 5, '2026-10-03 10:05+00'),
  ('a1000000-0000-4000-8000-00000000000e', (select id from cat where k = 'b'), 0, 1, 'warm_up', 50, 5, '2025-01-01 10:05+00'),
  ('a1000000-0000-4000-8000-00000000000f', (select id from cat where k = 'c'), 0, 1, 'working', 10, 10, '2026-10-06 01:10+02'),
  ('a1000000-0000-4000-8000-000000000011', (select id from cat where k = 'b'), 0, 1, 'working', 100, 5, '2026-10-06 00:10+00');

insert into public.body_measurements (user_id, weight_kg, source) values ('11111111-1111-4111-8111-111111111111', 80, 'manual');

-- Bea: one completed session on a catalogue exercise (1000 kg volume, 1 h).
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed', '2026-10-01 10:00+00', '2026-10-01 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where k = 'cat'), 0, 1, 'working', 200, 5, '2026-10-01 10:05+00');

-- Security: no execute for anon or public.
select ok(not has_function_privilege('anon', 'public.training_summary(date, date)', 'execute')
      and not has_function_privilege('public', 'public.training_summary(date, date)', 'execute'), 'training_summary: no execute for anon/public');
select ok(not has_function_privilege('anon', 'public.weekly_set_counts(date, date)', 'execute')
      and not has_function_privilege('public', 'public.weekly_set_counts(date, date)', 'execute'), 'weekly_set_counts: no execute for anon/public');
select ok(not has_function_privilege('anon', 'public.muscle_group_sets(date, date)', 'execute')
      and not has_function_privilege('public', 'public.muscle_group_sets(date, date)', 'execute'), 'muscle_group_sets: no execute for anon/public');
select ok(not has_function_privilege('anon', 'public.exercise_set_counts(date, date, public.muscle_group)', 'execute')
      and not has_function_privilege('public', 'public.exercise_set_counts(date, date, public.muscle_group)', 'execute'), 'exercise_set_counts: no execute for anon/public');

-- ---------------------------------------------------------------------------
-- As Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- training_summary
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary()$$,
  $$values (5::bigint, 7::bigint, 3100::numeric, 13800::bigint)$$,
  'summary, open window: discarded/in-progress/pending/warm-up excluded; bodyweight uses 80 kg; a session with no eligible set still counts');
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary('2026-10-01', '2026-10-05')$$,
  $$values (3::bigint, 6::bigint, 2600::numeric, 6600::bigint)$$,
  'summary: p_from and p_to days included; UTC date used near midnight; a pause longer than the elapsed time adds 0 s');
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary('2026-10-02', '2026-10-05')$$,
  $$values (2::bigint, 3::bigint, 1100::numeric, 3600::bigint)$$, 'summary: the day before p_from is excluded');
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary('2026-10-06', null)$$,
  $$values (1::bigint, 1::bigint, 500::numeric, 3600::bigint)$$, 'summary: the day after the previous p_to is excluded, null p_to is open');
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary(null, '2026-10-04')$$,
  $$values (2::bigint, 3::bigint, 1500::numeric, 6600::bigint)$$,
  'summary: null p_from counts a session older than one year; p_to day after excluded');
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary('2025-01-01', '2025-01-01')$$,
  $$values (1::bigint, 0::bigint, 0::numeric, 3600::bigint)$$, 'summary: a warm-up-only session counts as a workout with zero sets');
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary('2030-01-01', null)$$,
  $$values (0::bigint, 0::bigint, 0::numeric, 0::bigint)$$, 'summary: exactly one zero row without data');

-- weekly_set_counts
select results_eq($$select week_start, sets from public.weekly_set_counts()$$,
  $$values ('2026-09-28'::date, 3::bigint), ('2026-10-05'::date, 4::bigint)$$, 'weekly: Monday of the UTC week, only weeks with data, ordered');
select is_empty($$select * from public.weekly_set_counts('2030-01-01', null)$$, 'weekly: empty window gives no rows');

-- muscle_group_sets
select results_eq($$select muscle_group, sets from public.muscle_group_sets()$$,
  $$values ('chest'::public.muscle_group, 4::numeric), ('biceps', 2.5), ('shoulders', 2), ('triceps', 2), ('back', 1), ('forearms', 1)$$,
  'muscle groups: primary 1, distinct secondary 0.5, primary-equal secondary ignored; ties by enum order');
select results_eq($$select muscle_group, sets from public.muscle_group_sets('2026-10-01', '2026-10-01')$$,
  $$values ('chest'::public.muscle_group, 3::numeric), ('shoulders', 1.5), ('triceps', 1.5)$$,
  'muscle groups: bench example chest 3, triceps 1.5, shoulders 1.5');
select is_empty($$select * from public.muscle_group_sets('2030-01-01', null)$$, 'muscle groups: empty window gives no rows');

-- exercise_set_counts
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts()$$,
  $$values ((select id from cat where k = 'b'), 4::numeric, 2::bigint),
           ((select id from cat where k = 'c'), 2::numeric, 2::bigint),
           ((select id from cat where k = 'd'), 1::numeric, 1::bigint)$$,
  'exercise counts, no group: sets and distinct sessions, ordered by sets desc');
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts('2026-10-01', '2026-10-01', 'triceps')$$,
  $$values ((select id from cat where k = 'b'), 1.5::numeric, 1::bigint)$$, 'exercise counts, triceps: bench weighs 0.5 per set');
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts(null, null, 'triceps')$$,
  $$values ((select id from cat where k = 'b'), 2::numeric, 2::bigint)$$, 'exercise counts, triceps, open window');
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts(null, null, 'biceps')$$,
  $$values ((select id from cat where k = 'c'), 2::numeric, 2::bigint), ((select id from cat where k = 'd'), 0.5::numeric, 1::bigint)$$,
  'exercise counts, biceps: primary weighs 1 and secondary 0.5, ordered by sets');
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts(null, null, 'chest')$$,
  $$values ((select id from cat where k = 'b'), 4::numeric, 2::bigint)$$, 'exercise counts, chest: a secondary equal to the primary adds nothing');
select is_empty($$select * from public.exercise_set_counts(null, null, 'quads')$$, 'exercise counts: a group no exercise has is excluded');

-- Warm-up inclusion
insert into public.training_preferences (warm_up_included) values (true);
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary()$$,
  $$values (5::bigint, 9::bigint, 3650::numeric, 13800::bigint)$$, 'warm-up included: summary counts the two warm-up sets and their volume');
select results_eq($$select week_start, sets from public.weekly_set_counts()$$,
  $$values ('2024-12-30'::date, 1::bigint), ('2026-09-28', 4), ('2026-10-05', 4)$$, 'warm-up included: weekly counts');
select results_eq($$select muscle_group, sets from public.muscle_group_sets('2026-10-01', '2026-10-01')$$,
  $$values ('chest'::public.muscle_group, 4::numeric), ('shoulders', 2), ('triceps', 2)$$, 'warm-up included: bench example 4 / 2 / 2');
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts('2026-10-01', '2026-10-01', 'triceps')$$,
  $$values ((select id from cat where k = 'b'), 2::numeric, 1::bigint)$$, 'warm-up included: exercise counts');
update public.training_preferences set warm_up_included = false;

-- Per-hand volume
update public.exercises set per_hand = true where id = (select id from cat where k = 'c');
select results_eq($$select volume from public.training_summary()$$, $$values (3400::numeric)$$,
  'per-hand exercise doubles the volume (curls 300 -> 600)');

-- ---------------------------------------------------------------------------
-- As Bea: never sees Ana's data, and her data never reaches Ana (checked above)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select results_eq($$select workouts, sets, volume, duration_s from public.training_summary()$$,
  $$values (1::bigint, 1::bigint, 1000::numeric, 3600::bigint)$$, 'isolation: Bea summary counts only her session');
select results_eq($$select week_start, sets from public.weekly_set_counts()$$,
  $$values ('2026-09-28'::date, 1::bigint)$$, 'isolation: Bea weekly counts only her set');
select results_eq(
  $$select sets from public.muscle_group_sets()
    where muscle_group = (select e.muscle_group from public.exercises e where e.id = (select id from cat where k = 'cat'))$$,
  $$values (1::numeric)$$, 'isolation: Bea primary group counts only her one set');
select results_eq($$select exercise_id, sets, sessions from public.exercise_set_counts()$$,
  $$values ((select id from cat where k = 'cat'), 1::numeric, 1::bigint)$$, 'isolation: Bea exercise counts');

-- ---------------------------------------------------------------------------
-- Unauthenticated
-- ---------------------------------------------------------------------------
set local role anon;
select throws_ok($$select * from public.training_summary()$$, '42501', null, 'anon denied: training_summary');
select throws_ok($$select * from public.weekly_set_counts()$$, '42501', null, 'anon denied: weekly_set_counts');
select throws_ok($$select * from public.muscle_group_sets()$$, '42501', null, 'anon denied: muscle_group_sets');
select throws_ok($$select * from public.exercise_set_counts()$$, '42501', null, 'anon denied: exercise_set_counts');

select * from finish();
rollback;
