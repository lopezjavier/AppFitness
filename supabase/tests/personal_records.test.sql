-- pgTAP: exercise_session_records() and personal_records() (T-094, US-34).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(27);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- The catalogue has no steps exercise yet: use a custom one.
insert into public.exercises (name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('Escalador', 'full_body', 'other', 'steps_duration', true, '11111111-1111-4111-8111-111111111111');

create temp table cat as
  select 'b' as k, (select id from public.exercises where not is_custom and equipment = 'barbell'
                      and metric_type = 'weight_reps' order by key limit 1) as id
  union all
  select 'd', (select id from public.exercises where not is_custom and equipment = 'dumbbell'
                 and metric_type = 'weight_reps' order by key limit 1)
  union all
  select 'bw', (select id from public.exercises where not is_custom and metric_type = 'bodyweight' order by key limit 1)
  union all
  select 'dist', (select id from public.exercises where not is_custom and metric_type = 'distance_duration' order by key limit 1)
  union all
  select 'steps', (select id from public.exercises where is_custom and metric_type = 'steps_duration' limit 1)
  union all
  select 'floors', (select id from public.exercises where not is_custom and metric_type = 'floors_duration' order by key limit 1);
grant select on cat to authenticated;

-- Ana: s1..s5 on the barbell exercise (s3 discarded, s4 in progress), s6..s9 for other metrics.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 's1', 'completed', '2026-10-01 10:00+00', '2026-10-01 11:00+00'),
  ('a1000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 's2', 'completed', '2026-10-05 10:00+00', '2026-10-05 11:00+00'),
  ('a1000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 's3', 'discarded', '2026-10-08 10:00+00', '2026-10-08 11:00+00'),
  ('a1000000-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 's5', 'completed', '2026-10-10 10:00+00', '2026-10-10 11:00+00'),
  ('a1000000-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111111', 's6', 'completed', '2026-10-11 10:00+00', '2026-10-11 11:00+00'),
  ('a1000000-0000-4000-8000-000000000007', '11111111-1111-4111-8111-111111111111', 's7', 'completed', '2026-10-12 10:00+00', '2026-10-12 11:00+00'),
  ('a1000000-0000-4000-8000-000000000008', '11111111-1111-4111-8111-111111111111', 's8', 'completed', '2026-10-13 10:00+00', '2026-10-13 11:00+00'),
  ('a1000000-0000-4000-8000-000000000009', '11111111-1111-4111-8111-111111111111', 's9', 'completed', '2026-10-14 10:00+00', '2026-10-14 11:00+00');
insert into public.workout_sessions (id, user_id, name, status, started_at) values
  ('a1000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 's4', 'in_progress', '2026-10-09 10:00+00');

insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, duration_s, distance_m, steps, floors, completed_at) values
  -- s1: 100x5 and 80x10
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where k = 'b'), 0, 1, 'working', 100, 5, null, null, null, null, '2026-10-01 10:05+00'),
  ('a1000000-0000-4000-8000-000000000001', (select id from cat where k = 'b'), 0, 2, 'working', 80, 10, null, null, null, null, '2026-10-01 10:10+00'),
  -- s2: 100x5 again, a heavier warm-up, a pending set
  ('a1000000-0000-4000-8000-000000000002', (select id from cat where k = 'b'), 0, 1, 'working', 100, 5, null, null, null, null, '2026-10-05 10:05+00'),
  ('a1000000-0000-4000-8000-000000000002', (select id from cat where k = 'b'), 0, 2, 'warm_up', 150, 3, null, null, null, null, '2026-10-05 10:06+00'),
  ('a1000000-0000-4000-8000-000000000002', (select id from cat where k = 'b'), 0, 3, 'working', 200, 5, null, null, null, null, null),
  -- s3 discarded, s4 in progress: must be ignored
  ('a1000000-0000-4000-8000-000000000003', (select id from cat where k = 'b'), 0, 1, 'working', 300, 5, null, null, null, null, '2026-10-08 10:05+00'),
  ('a1000000-0000-4000-8000-000000000004', (select id from cat where k = 'b'), 0, 1, 'working', 400, 1, null, null, null, null, '2026-10-09 10:05+00'),
  -- s5: 110x3 (new heaviest and 1RM)
  ('a1000000-0000-4000-8000-000000000005', (select id from cat where k = 'b'), 0, 1, 'working', 110, 3, null, null, null, null, '2026-10-10 10:05+00'),
  -- s6: dumbbell 20x10
  ('a1000000-0000-4000-8000-000000000006', (select id from cat where k = 'd'), 0, 1, 'working', 20, 10, null, null, null, null, '2026-10-11 10:05+00'),
  -- s7: bodyweight 10 reps, no added weight
  ('a1000000-0000-4000-8000-000000000007', (select id from cat where k = 'bw'), 0, 1, 'working', 0, 10, null, null, null, null, '2026-10-12 10:05+00'),
  -- s8: distance/duration, two sets; the longer duration is not the longer distance
  ('a1000000-0000-4000-8000-000000000008', (select id from cat where k = 'dist'), 0, 1, 'working', 0, 0, 600, 1500, null, null, '2026-10-13 10:05+00'),
  ('a1000000-0000-4000-8000-000000000008', (select id from cat where k = 'dist'), 0, 2, 'working', 0, 0, 900, 1200, null, null, '2026-10-13 10:20+00'),
  -- s9: steps and floors
  ('a1000000-0000-4000-8000-000000000009', (select id from cat where k = 'steps'), 0, 1, 'working', 0, 0, 300, null, 500, null, '2026-10-14 10:05+00'),
  ('a1000000-0000-4000-8000-000000000009', (select id from cat where k = 'floors'), 1, 1, 'working', 0, 0, 300, null, null, 12, '2026-10-14 10:15+00');

insert into public.body_measurements (user_id, weight_kg, source) values ('11111111-1111-4111-8111-111111111111', 80, 'manual');

-- Bea: one completed session on the barbell exercise.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed', '2026-10-01 10:00+00', '2026-10-01 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('b1000000-0000-4000-8000-000000000001', (select id from cat where k = 'b'), 0, 1, 'working', 500, 5, '2026-10-01 10:05+00');

-- Security
select ok(not has_function_privilege('anon', 'public.personal_records(uuid)', 'execute'), 'anon cannot execute personal_records');
select ok(not has_function_privilege('anon', 'public.exercise_session_records(uuid)', 'execute'), 'anon cannot execute exercise_session_records');
select ok(has_function_privilege('authenticated', 'public.personal_records(uuid)', 'execute'), 'authenticated can execute personal_records');
select ok(has_function_privilege('authenticated', 'public.exercise_session_records(uuid)', 'execute'), 'authenticated can execute exercise_session_records');

-- ---------------------------------------------------------------------------
-- As Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  $$select session_id, heaviest_lift, best_set, session_volume, best_one_rm, total_reps
    from public.exercise_session_records((select id from cat where k = 'b'))$$,
  $$values ('a1000000-0000-4000-8000-000000000001'::uuid, 100::numeric, 800::numeric, 1300::numeric, 116.5::numeric, 15::bigint),
           ('a1000000-0000-4000-8000-000000000002'::uuid, 100::numeric, 500::numeric, 500::numeric, 116.5::numeric, 5::bigint),
           ('a1000000-0000-4000-8000-000000000005'::uuid, 110::numeric, 330::numeric, 330::numeric, 121::numeric, 3::bigint)$$,
  'session records: only completed sessions, completed non-warm-up sets, ordered by start');

select results_eq(
  $$select record_type, value, session_id from public.personal_records((select id from cat where k = 'b'))$$,
  $$values ('heaviest_lift', 110::numeric, 'a1000000-0000-4000-8000-000000000005'::uuid),
           ('best_set', 800::numeric, 'a1000000-0000-4000-8000-000000000001'::uuid),
           ('max_volume', 1300::numeric, 'a1000000-0000-4000-8000-000000000001'::uuid),
           ('best_1rm', 121::numeric, 'a1000000-0000-4000-8000-000000000005'::uuid),
           ('total_reps', 23::numeric, null::uuid)$$,
  'the five weight families, each from the right session; total_reps is all-time with no session');

select is((select session_date from public.personal_records((select id from cat where k = 'b')) where record_type = 'best_set'),
  '2026-10-01'::date, 'session_date is the UTC date of the record session');

select is((select session_id from public.personal_records((select id from cat where k = 'b')) where record_type = 'best_1rm'),
  'a1000000-0000-4000-8000-000000000005'::uuid, 'best 1RM is the 121 of s5, not the 116.5 tie of s1/s2');

select results_eq(
  $$select h.best_one_rm, h.total_volume from public.exercise_history((select id from cat where k = 'b')) h order by h.session_date$$,
  $$select r.best_one_rm, r.session_volume from public.exercise_session_records((select id from cat where k = 'b')) r order by r.started_at$$,
  '1RM and volume match exercise_history');

-- Warm-up toggle
insert into public.training_preferences (warm_up_included) values (true);
select results_eq(
  $$select record_type, value, session_id from public.personal_records((select id from cat where k = 'b'))
    where record_type in ('heaviest_lift', 'total_reps') order by 1$$,
  $$values ('heaviest_lift', 150::numeric, 'a1000000-0000-4000-8000-000000000002'::uuid),
           ('total_reps', 26::numeric, null::uuid)$$,
  'warm-up included: the 150 kg warm-up is the heaviest lift and its reps count');
select is((select best_one_rm from public.exercise_session_records((select id from cat where k = 'b')) where session_id = 'a1000000-0000-4000-8000-000000000002'),
  165::numeric, 'warm-up included: the 150 x 3 warm-up also feeds the 1RM (same rule as exercise_history)');
update public.training_preferences set warm_up_included = false;
select is((select value from public.personal_records((select id from cat where k = 'b')) where record_type = 'heaviest_lift'),
  110::numeric, 'warm-up excluded again: heaviest lift back to 110');

-- Double dumbbell and bodyweight
select results_eq(
  $$select best_set, session_volume, best_one_rm from public.exercise_session_records((select id from cat where k = 'd'))$$,
  $$values (200::numeric, 200::numeric, 26.5::numeric)$$, 'dumbbell off: volume 200');
select lives_ok($$select public.set_exercise_double_dumbbell((select id from cat where k = 'd'), true)$$, 'enable double dumbbell');
select results_eq(
  $$select best_set, session_volume, best_one_rm from public.exercise_session_records((select id from cat where k = 'd'))$$,
  $$values (400::numeric, 400::numeric, 26.5::numeric)$$, 'double dumbbell doubles volume records but not the 1RM');
select results_eq(
  $$select record_type, value from public.personal_records((select id from cat where k = 'bw')) order by 1$$,
  $$values ('best_set', 800::numeric), ('max_volume', 800::numeric), ('total_reps', 10::numeric)$$,
  'bodyweight volume uses the latest body weight (80 x 10); no 1RM, no heaviest lift');

-- Duration / distance / steps / floors
select results_eq(
  $$select record_type, value from public.personal_records((select id from cat where k = 'dist')) order by 1$$,
  $$values ('best_distance', 1500::numeric), ('best_duration', 900::numeric)$$,
  'distance exercise: max single set for time and for distance, no weight families');
select results_eq(
  $$select record_type, value from public.personal_records((select id from cat where k = 'steps')) order by 1$$,
  $$values ('best_duration', 300::numeric), ('most_steps', 500::numeric)$$, 'steps exercise');
select results_eq(
  $$select record_type, value from public.personal_records((select id from cat where k = 'floors')) order by 1$$,
  $$values ('best_duration', 300::numeric), ('most_floors', 12::numeric)$$, 'floors exercise');

-- Ties and empty
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a1000000-0000-4000-8000-00000000000a', '11111111-1111-4111-8111-111111111111', 's10', 'completed', '2026-10-15 10:00+00', '2026-10-15 11:00+00'),
  ('a1000000-0000-4000-8000-00000000000b', '11111111-1111-4111-8111-111111111111', 's11', 'completed', '2026-10-16 10:00+00', '2026-10-16 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, duration_s, distance_m, completed_at) values
  ('a1000000-0000-4000-8000-00000000000a', (select id from cat where k = 'dist'), 0, 1, 0, 0, 900, 1500, '2026-10-15 10:05+00'),
  ('a1000000-0000-4000-8000-00000000000b', (select id from cat where k = 'dist'), 0, 1, 0, 0, 900, 1500, '2026-10-16 10:05+00');
select results_eq(
  $$select record_type, session_id from public.personal_records((select id from cat where k = 'dist')) order by 1$$,
  $$values ('best_distance', 'a1000000-0000-4000-8000-000000000008'::uuid), ('best_duration', 'a1000000-0000-4000-8000-000000000008'::uuid)$$,
  'ties pick the earliest session');
select is((select count(*)::int from public.personal_records('99999999-9999-4999-8999-999999999999')), 0,
  'unknown exercise returns no rows');
select is((select count(*)::int from public.exercise_session_records('99999999-9999-4999-8999-999999999999')), 0,
  'unknown exercise returns no session rows');

-- ---------------------------------------------------------------------------
-- As Bea: isolation
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select results_eq(
  $$select record_type, value from public.personal_records((select id from cat where k = 'b')) order by 1$$,
  $$values ('best_1rm', 583.5::numeric), ('best_set', 2500::numeric), ('heaviest_lift', 500::numeric),
           ('max_volume', 2500::numeric), ('total_reps', 5::numeric)$$,
  'Bea sees only her own history for the same exercise');
select is((select count(*)::int from public.personal_records((select id from cat where k = 'dist'))), 0,
  'Bea sees nothing of Ana''s distance sessions');
select is((select count(*)::int from public.exercise_session_records((select id from cat where k = 'd'))), 0,
  'Bea sees none of Ana''s session records');

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
reset role;
set local role anon;
select throws_ok($$select * from public.personal_records('99999999-9999-4999-8999-999999999999')$$, '42501', null,
  'anon is denied on personal_records');
select throws_ok($$select * from public.exercise_session_records('99999999-9999-4999-8999-999999999999')$$, '42501', null,
  'anon is denied on exercise_session_records');
reset role;

select * from finish();
rollback;
