-- pgTAP: the model-built block, data side (T-205, US-72 amended, US-96 coach part, ADR-93, ADR-94):
-- unresolved_severe_discomforts, coach_block_model_input and the structural codes of coach_assert_block.
-- Rules: planning/07 section 3 (H1-H7, "Routine tools"), planning/04 section 9.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(59);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('d5000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.p(n int) returns uuid language sql as $$
  select ('d5000000-0000-4000-8000-00000000a0' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- Catalogue exercises by pattern (barbell mains) and isolation (machine).
create function pg_temp.main(p_pattern text, p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = p_pattern
    and metric_type = 'weight_reps' and equipment = 'barbell' order by key offset p_nth limit 1 $$;
create function pg_temp.iso(p_muscle text, p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation'
    and metric_type = 'weight_reps' and equipment = 'machine' and muscle_group::text = p_muscle
  order by key offset p_nth limit 1 $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;

-- One lifting exercise; p_extra merges over the defaults (sets, target_rpe, superset_group, ...).
create function pg_temp.lift(p_id uuid, p_role text, p_extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', 3,
    'rep_min', case when p_role = 'main' then 6 else 10 end, 'rep_max', case when p_role = 'main' then 10 else 15 end,
    'rest_seconds', case when p_role = 'main' then 150 else 90 end, 'target_rpe', 8,
    'reason', 'Buen ejercicio base para tu objetivo.') || p_extra $$;

-- Attaches a one-session block with the given exercises for the user's plan.
create function pg_temp.attach(p_plan uuid, p_exercises jsonb, p_extra jsonb default '{}') returns jsonb language sql as $$
  select public.attach_coach_block(p_plan,
    jsonb_build_object('days_per_week', 1, 'days_chosen', false, 'session_minutes', 60, 'block_weeks', 4,
                       'equipment', '[]'::jsonb) || p_extra,
    jsonb_build_array(jsonb_build_object('session_index', 1, 'name', 'Sesion', 'focus', jsonb_build_array('chest'),
                                         'exercises', p_exercises)),
    '{"model":"m","attempts":1}', '{}') $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'built' || n || '@example.com' from generate_series(1, 3) n;
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by, movement_pattern) values
  ('d5000000-0000-4000-8000-0000000000c1', 'Press propio', 'chest', 'barbell', 'weight_reps', true, pg_temp.u(1), 'horizontal_push');
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status) values
  (pg_temp.p(1), pg_temp.u(1), pg_temp.d0(), 'muscle', '{}', 'proposed'),
  (pg_temp.p(3), pg_temp.u(3), pg_temp.d0(), 'muscle', '{}', 'proposed');
create temp table cat on commit drop as
  select pg_temp.k('squat') as squat, pg_temp.k('bench-press') as bench, pg_temp.k('lateral-raise') as lat,
         pg_temp.k('deadlift') as deadlift, pg_temp.k('leg-extension') as leg_ext;
grant select on cat to authenticated;

-- ---------------------------------------------------------------------------
-- unresolved_severe_discomforts
-- ---------------------------------------------------------------------------
insert into public.exercise_discomforts (user_id, exercise_id, level, note, resolved_at, logged_at) values
  (pg_temp.u(1), (select squat from cat), 4, 'rodilla', null, now() - interval '3 days'),
  (pg_temp.u(1), (select bench from cat), 3, 'hombro leve', null, now() - interval '3 days'),
  (pg_temp.u(1), (select deadlift from cat), 5, 'lumbar', now() - interval '1 day', now() - interval '3 days'),
  (pg_temp.u(2), (select leg_ext from cat), 5, 'de otra persona', null, now() - interval '3 days');

select ok(not has_function_privilege('anon', 'public.unresolved_severe_discomforts(uuid)', 'execute'),
  'anon cannot execute unresolved_severe_discomforts');
select ok(not has_function_privilege('anon', 'public.coach_block_model_input(date)', 'execute'),
  'anon cannot execute coach_block_model_input');
select ok(not has_function_privilege('authenticated', 'public.coach_assert_block(uuid,jsonb,jsonb)', 'execute'),
  'coach_assert_block is internal');

set local role authenticated;
select pg_temp.as_user(1);
select is((select array_agg(x) from public.unresolved_severe_discomforts(pg_temp.u(1)) x), array[(select squat from cat)],
  'only the open level 4-5 discomfort is unresolved (level 3 and resolved ones are not listed)');
select is((select count(*)::int from public.unresolved_severe_discomforts(pg_temp.u(2))), 0,
  'the helper never returns another user''s rows');
select pg_temp.as_user(2);
select is((select count(*)::int from public.unresolved_severe_discomforts(pg_temp.u(1))), 0,
  'user B asking for user A gets nothing');
select is((select array_agg(x) from public.unresolved_severe_discomforts(pg_temp.u(2)) x), array[(select leg_ext from cat)],
  'and gets their own');

-- ---------------------------------------------------------------------------
-- coach_block_model_input
-- ---------------------------------------------------------------------------
reset role;
-- Folders: 13 for user 1 (the newest 12 are sent), one routine with one exercise in the newest.
insert into public.routine_folders (id, user_id, name, created_at)
select ('d5000000-0000-4000-8000-0000000001' || lpad(i::text, 2, '0'))::uuid, pg_temp.u(1), 'Carpeta ' || i,
       now() - make_interval(days => i)
from generate_series(1, 13) i;
insert into public.routines (id, user_id, name, folder_id)
values ('d5000000-0000-4000-8000-000000000201', pg_temp.u(1), 'Rutina A', 'd5000000-0000-4000-8000-000000000101');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps, target_reps_max, target_rpe,
                                      rest_seconds, rep_min, rep_max)
values ('d5000000-0000-4000-8000-000000000201', (select squat from cat), 0, 4, 6, 10, 8, 150, 6, 10);
-- History: squat 3 sessions (10, 20 and 70 days ago), bench 1 session 5 days ago with a warm-up and a working set.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
select ('d5000000-0000-4000-8000-0000000003' || lpad(d::text, 2, '0'))::uuid, pg_temp.u(1), 's', 'completed',
       now() - make_interval(days => d), now() - make_interval(days => d) + interval '1 hour'
from unnest(array[5, 10, 20, 70]) d;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe, completed_at)
select ('d5000000-0000-4000-8000-0000000003' || lpad(d::text, 2, '0'))::uuid, (select squat from cat), 0, 1, 'working',
       100 + d, 5, 8, now() - make_interval(days => d)
from unnest(array[10, 20, 70]) d;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe, completed_at) values
  ('d5000000-0000-4000-8000-000000000305', (select bench from cat), 0, 1, 'warm_up', 20, 10, null, now() - interval '5 days'),
  ('d5000000-0000-4000-8000-000000000305', (select bench from cat), 0, 2, 'working', 80, 8, 9, now() - interval '5 days');
insert into public.exercise_favorites (user_id, exercise_id) values (pg_temp.u(1), (select lat from cat));
-- A previous block with 2 sessions per week, one done.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, training)
values (pg_temp.p(2), pg_temp.u(1), pg_temp.d0() - 60, 'muscle', '{}', 'superseded', '{"days_per_week":2}');
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
values ('d5000000-0000-4000-8000-000000000399', pg_temp.u(1), 'prev', 'completed', now() - interval '59 days', now() - interval '59 days');
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, focus, exercises, session_id)
select pg_temp.p(2), pg_temp.u(1), 1, i, 'S' || i, '{chest}',
       jsonb_build_array(pg_temp.lift((select bench from cat), 'main'),
                         jsonb_build_object('exercise_id', (select squat from cat), 'role', 'cardio')),
       case when i = 1 then 'd5000000-0000-4000-8000-000000000399'::uuid end
from generate_series(1, 2) i;

set local role authenticated;
select pg_temp.as_user(1);
select is(jsonb_array_length(public.coach_block_model_input(pg_temp.d0())->'folders'), 12, 'at most the 12 newest folders are sent');
select is(public.coach_block_model_input(pg_temp.d0())->'folders'->0->>'name', 'Carpeta 1', 'newest folder first');
select is(public.coach_block_model_input(pg_temp.d0())->'folders'->0->'routines'->0->'exercises'->0,
  jsonb_build_object('exercise', 'squat', 'sets', 4, 'rep_min', 6, 'rep_max', 10, 'target_rpe', 8),
  'a folder carries its routines with exercise key, sets, rep range and target RPE');
select is((select h->>'sessions' from jsonb_array_elements(public.coach_block_model_input(pg_temp.d0())->'history') h
           where h->>'exercise' = 'squat'), '2', 'history covers 8 weeks: the 70-day-old session is out');
select is((select h->>'e1rm_first_week' <> h->>'e1rm_last_week'
           from jsonb_array_elements(public.coach_block_model_input(pg_temp.d0())->'history') h where h->>'exercise' = 'squat'),
  true, 'history carries the best e1RM of the first and the last week with data');
select is((select h->>'working_sets' from jsonb_array_elements(public.coach_block_model_input(pg_temp.d0())->'history') h
           where h->>'exercise' = 'bench-press'), '1', 'a warm-up set is not a working set');
select is((select h->>'last_weight_kg' from jsonb_array_elements(public.coach_block_model_input(pg_temp.d0())->'history') h
           where h->>'exercise' = 'bench-press'), '80.00', 'the last working weight is sent');
select is((select h->>'mean_rpe' from jsonb_array_elements(public.coach_block_model_input(pg_temp.d0())->'history') h
           where h->>'exercise' = 'bench-press'), '9.0', 'with the mean RPE');
select is(public.coach_block_model_input(pg_temp.d0())->'previous_blocks'->0->>'adherence_pct', '50',
  'the previous block carries its adherence (1 of 2 sessions)');
select is(public.coach_block_model_input(pg_temp.d0())->'previous_blocks'->0->'exercises', '["bench-press"]'::jsonb,
  'and its lifting exercises (cardio left out)');
select is(public.coach_block_model_input(pg_temp.d0())->'favourites', '["lateral-raise"]'::jsonb, 'favourites are listed by key');
select is(public.coach_block_model_input(pg_temp.d0())->'unresolved_exercise_ids', jsonb_build_array((select squat from cat)::text),
  'unresolved exercises come from the helper');
select pg_temp.as_user(2);
select is(public.coach_block_model_input(pg_temp.d0()) - 'generated_for' - 'unresolved_exercise_ids',
  '{"folders":[],"history":[],"previous_blocks":[],"favourites":[]}'::jsonb,
  'user B sees none of user A''s folders, history, blocks or favourites');
select set_config('request.jwt.claims', '', true);
select throws_ok($$select public.coach_block_model_input(pg_temp.d0())$$, '28000', null, 'unauthenticated callers are rejected');

-- ---------------------------------------------------------------------------
-- coach_assert_block through attach_coach_block (user 1, plan 1)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"planned_weight_kg":60}'),
    pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation',
      '{"sets":[{"type":"working"},{"type":"working"},{"type":"drop"}],"technique_note":"Codo ligeramente flexionado."}')))$$,
  'a block with RPE, a planned weight, a shoulder_abduction exercise as isolation and a final drop set is accepted');
select is((select exercises->1->>'exercise_key' from public.coach_plan_sessions where plan_id = pg_temp.p(1) and week = 1),
  'k', 'and it is stored with its exercise JSON');

select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
      jsonb_build_object('reason', repeat('r', 600), 'technique_note', repeat('t', 600)))))$$,
  'T-212: a 600-character reason and technique note are accepted (no upper cap)');
select is((select length(exercises->0->>'reason') from public.coach_plan_sessions where plan_id = pg_temp.p(1) and week = 1),
  600, 'and the long reason is stored whole');

select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"rep_min":3,"rep_max":5}')), '{"intention":"muscle"}')$$,
  '22023', 'block invariant: h4_rep_range', 'muscle building: 3-5 reps is below the 6-20 range');
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"rep_min":3,"rep_max":5}')), '{"intention":"strength"}')$$,
  'strength: a main lift at 3-5 reps is accepted');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"rep_min":10,"rep_max":15}')), '{"intention":"strength"}')$$,
  '22023', 'block invariant: h4_rep_range', 'strength: a main lift at 10-15 reps is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"rep_min":6,"rep_max":21}')), '{"intention":"muscle"}')$$,
  '22023', 'block invariant: h4_rep_range', '21 reps is above the 6-20 range');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"target_rpe":6}')))$$, '22023', 'block invariant: h5_rpe', 'target RPE 6 is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"target_rpe":10}')))$$, '22023', 'block invariant: h5_rpe', 'target RPE 10 is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"target_rpe":7.5}')))$$, '22023', 'block invariant: h5_rpe', 'a half RPE is rejected');
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"target_rpe":7}')))$$, 'target RPE 7 is accepted');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"sets":[{"type":"warm_up"},{"type":"working"}]}')))$$, '22023', 'block invariant: h5_set_type', 'a warm-up set is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"sets":[{"type":"working"},{"type":"failure"}]}')))$$, '22023', 'block invariant: h5_set_type', 'a failure set is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main')),
  '{"equipment":["dumbbell"]}')$$, '22023', 'block invariant: h6_equipment', 'an exercise outside the equipment is rejected');
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift((select squat from cat), 'main')))$$,
  'an exercise with an unresolved level 4-5 discomfort is accepted (ADR-99, T-221)');
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift((select bench from cat), 'main')))$$,
  'a level 3 discomfort does not exclude the exercise');
select throws_ok($$select public.attach_coach_block(pg_temp.p(1),
  '{"days_per_week":2,"session_minutes":60,"block_weeks":4,"equipment":[]}'::jsonb,
  jsonb_build_array(jsonb_build_object('session_index', 1, 'name', 'S', 'focus', '[]'::jsonb,
    'exercises', jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main')))), '{"model":"m"}', '{}')$$,
  '22023', 'block invariant: h6_sessions', 'sessions must equal the days per week');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"sets":11}')))$$, '22023', 'block invariant: h6_sets_per_exercise', '11 sets are rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"sets":0}')))$$, '22023', 'block invariant: h6_sets_per_exercise', '0 sets are rejected');
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"sets":10}')))$$, '10 sets are accepted');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main',
  '{"rest_seconds":90}')))$$, '22023', 'block invariant: h6_rest', 'a main lift resting 90 s is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main'),
  pg_temp.lift(pg_temp.iso('biceps'), 'isolation', '{"rest_seconds":150}')))$$,
  '22023', 'block invariant: h6_rest', 'an isolation exercise resting 150 s is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.iso('biceps'), 'isolation'), pg_temp.lift(pg_temp.main('horizontal_push'), 'main')),
  '{"intention":"strength"}')$$, '22023', 'block invariant: h7_order', 'strength: an isolation exercise before a main lift is rejected');
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.iso('biceps'), 'isolation'), pg_temp.lift(pg_temp.main('horizontal_push'), 'main')),
  '{"intention":"muscle"}')$$, 'muscle building: the order is free');

-- Supersets and drop sets.
select lives_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"superset_group":1}'),
    pg_temp.lift(pg_temp.main('horizontal_pull'), 'main', '{"superset_group":1}')), '{"intention":"muscle"}')$$,
  'a chest-back superset of two main lifts is accepted for muscle building');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"superset_group":1,"rep_min":4,"rep_max":6}'),
    pg_temp.lift(pg_temp.main('horizontal_pull'), 'main', '{"superset_group":1,"rep_min":4,"rep_max":6}')),
  '{"intention":"strength"}')$$,
  '22023', 'block invariant: superset_invalid', 'strength: a main lift cannot be in a superset');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"superset_group":1}')))$$,
  '22023', 'block invariant: superset_invalid', 'a superset needs exactly two exercises');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.iso('biceps', 0), 'isolation', '{"superset_group":1}'),
    pg_temp.lift(pg_temp.iso('biceps', 1), 'isolation', '{"superset_group":1}')))$$,
  '22023', 'block invariant: superset_invalid', 'two exercises of the same muscle cannot be paired');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"superset_group":1,"sets":3}'),
    pg_temp.lift(pg_temp.main('horizontal_pull'), 'main', '{"superset_group":1,"sets":4}')))$$,
  '22023', 'block invariant: superset_sets_mismatch', 'superset members must have the same number of sets');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation', '{"sets":[{"type":"working"},{"type":"drop"},{"type":"drop"}]}')))$$,
  '22023', 'block invariant: drop_invalid', 'two drop sets on one exercise are rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation', '{"sets":[{"type":"drop"},{"type":"working"}]}')))$$,
  '22023', 'block invariant: drop_invalid', 'a drop set must be the last set');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"sets":[{"type":"working"},{"type":"drop"}]}')))$$,
  '22023', 'block invariant: drop_invalid', 'a main lift cannot have a drop set');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"planned_weight_kg":0}')))$$,
  '22023', 'block invariant: planned_weight', 'a planned weight must be positive');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main', '{"role":"main","exercise_key":"warm-up"}')))$$,
  '22023', 'block invariant: warmup', 'a warm-up exercise is rejected');
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(
    pg_temp.lift(pg_temp.main('horizontal_push'), 'main')), '{"phase":"minimal"}')$$,
  '22023', 'block invariant: phase', 'the removed minimal phase is rejected');

-- Another user's plan and exercises.
select pg_temp.as_user(2);
select throws_ok($$select pg_temp.attach(pg_temp.p(1), jsonb_build_array(pg_temp.lift(pg_temp.main('horizontal_push'), 'main')))$$,
  'P0002', null, 'user B cannot attach a block to user A''s plan');
select pg_temp.as_user(3);
select throws_ok($$select pg_temp.attach(pg_temp.p(3), jsonb_build_array(
    pg_temp.lift('d5000000-0000-4000-8000-0000000000c1', 'main')))$$,
  '22023', 'block invariant: unknown_exercise', 'a custom exercise of another user is not visible to the block');

select * from finish();
rollback;
