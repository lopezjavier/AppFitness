-- pgTAP: public.exercise_options, volume_factor(), set_exercise_double_dumbbell() and their effect on
-- exercise_history and weekly_muscle_volume (T-093, US-32).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(27);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- d1, d2: dumbbell exercises in different muscle groups; b1: a barbell exercise in a third group.
create temp table cat as
  select 'd1' as k, (select id from public.exercises where not is_custom and equipment = 'dumbbell'
                       and metric_type = 'weight_reps' and muscle_group = 'biceps' order by key limit 1) as id
  union all
  select 'd2', (select id from public.exercises where not is_custom and equipment = 'dumbbell'
                  and metric_type = 'weight_reps' and muscle_group = 'shoulders' order by key limit 1)
  union all
  select 'b1', (select id from public.exercises where not is_custom and equipment = 'barbell'
                  and metric_type = 'weight_reps' and muscle_group = 'chest' order by key limit 1);
grant select on cat to authenticated;

-- Ana: one completed session (2026-10-12, a Monday) with d1 20 x 10, d2 20 x 10, b1 20 x 10.
-- Bea: same d1 set. Carl: an option row, deleted later to test the cascade.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a6000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Ana', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00'),
  ('b6000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00');
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'd1'), 0, 1, 'working', 20, 10, '2026-10-12 10:05+00'),
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'd2'), 1, 1, 'working', 20, 10, '2026-10-12 10:10+00'),
  ('a6000000-0000-4000-8000-000000000001', (select id from cat where k = 'b1'), 2, 1, 'working', 20, 10, '2026-10-12 10:15+00'),
  ('b6000000-0000-4000-8000-000000000001', (select id from cat where k = 'd1'), 0, 1, 'working', 20, 10, '2026-10-12 10:05+00');

insert into public.exercise_options (user_id, exercise_id, double_dumbbell) values
  ('22222222-2222-4222-8222-222222222222', (select id from cat where k = 'd1'), true),
  ('33333333-3333-4333-8333-333333333333', (select id from cat where k = 'd1'), true);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select has_table('public', 'exercise_options', 'public.exercise_options exists');
select columns_are('public', 'exercise_options', array['user_id', 'exercise_id', 'double_dumbbell', 'updated_at'],
  'exercise_options has exactly the planned columns');
select fk_ok('public', 'exercise_options', 'user_id', 'auth', 'users', 'id', 'user_id references auth.users.id');
select fk_ok('public', 'exercise_options', 'exercise_id', 'public', 'exercises', 'id',
  'exercise_id references exercises.id');
select col_is_pk('public', 'exercise_options', array['user_id', 'exercise_id'], 'the primary key is (user_id, exercise_id)');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.exercise_options'::regclass),
  'RLS is enabled and forced on exercise_options');
select policies_are('public', 'exercise_options', array[
  'exercise_options_select_own', 'exercise_options_insert_own',
  'exercise_options_update_own', 'exercise_options_delete_own'
], 'exercise_options has the four owner policies');
select table_privs_are('public', 'exercise_options', 'anon', array[]::text[], 'anon has no privileges on exercise_options');
select table_privs_are('public', 'exercise_options', 'authenticated', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on exercise_options');
select ok(not has_function_privilege('anon', 'public.set_exercise_double_dumbbell(uuid, boolean)', 'execute'),
  'anon cannot execute set_exercise_double_dumbbell');
select ok(has_function_privilege('authenticated', 'public.volume_factor(uuid)', 'execute'),
  'authenticated can execute volume_factor');

delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is((select count(*)::int from public.exercise_options where user_id = '33333333-3333-4333-8333-333333333333'),
  0, 'deleting a user cascades to its options');

-- ---------------------------------------------------------------------------
-- As Ana (no options): defaults, isolation, RPC
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.exercise_options), 0, 'A cannot read B''s row');
select throws_ok(
  $$insert into public.exercise_options (user_id, exercise_id, double_dumbbell)
    values ('22222222-2222-4222-8222-222222222222', (select id from cat where k = 'd2'), true)$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.exercise_options set double_dumbbell = false returning 1) select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.exercise_options returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'A deleting B''s row affects 0 rows');

select is(public.volume_factor((select id from cat where k = 'd1')), 1::numeric,
  'A''s factor is 1 even though B enabled the same exercise');
select results_eq(
  $$select total_volume from public.exercise_history((select id from cat where k = 'd1'))$$,
  $$values (200::numeric)$$, 'off: 20 x 10 is 200');

select lives_ok($$select public.set_exercise_double_dumbbell((select id from cat where k = 'd1'), true)$$,
  'A enables double dumbbell on d1');
select results_eq(
  $$select best_one_rm, total_volume from public.exercise_history((select id from cat where k = 'd1'))$$,
  $$values (26.5::numeric, 400::numeric)$$, 'on: volume doubles (400) and the 1RM (26.5) does not');
select results_eq(
  $$select total_volume from public.exercise_history((select id from cat where k = 'd2'))$$,
  $$values (200::numeric)$$, 'a second dumbbell exercise is unaffected');
select results_eq(
  $$select muscle_group::text, total_volume from public.weekly_muscle_volume('2026-10-01') order by 1$$,
  $$values ('biceps', 400::numeric), ('chest', 200::numeric), ('shoulders', 200::numeric)$$,
  'muscle volume doubles only the enabled exercise''s group');

select throws_ok($$select public.set_exercise_double_dumbbell((select id from cat where k = 'b1'), true)$$,
  '22023', null, 'a non-dumbbell exercise is rejected');
select throws_ok(
  $$select public.set_exercise_double_dumbbell('99999999-9999-4999-8999-999999999999', true)$$,
  'P0002', null, 'an unknown exercise is rejected');

select lives_ok($$select public.set_exercise_double_dumbbell((select id from cat where k = 'd1'), false)$$,
  'A disables it again (upsert)');
select results_eq(
  $$select total_volume from public.exercise_history((select id from cat where k = 'd1'))$$,
  $$values (200::numeric)$$, 'off again restores 200');

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
reset role;
set local role anon;
select throws_ok('select * from public.exercise_options', '42501', null, 'anon cannot read exercise_options');
reset role;

select * from finish();
rollback;
