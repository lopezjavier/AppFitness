-- pgTAP: public.training_preferences, warm_up_included(), its effect on exercise_history and the
-- save_preferences argument (T-090, US-29).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(44);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- B opted in (and must never be affected by A); C is deleted later to test the cascade.
insert into public.training_preferences (user_id, warm_up_included) values
  ('22222222-2222-4222-8222-222222222222', true),
  ('33333333-3333-4333-8333-333333333333', true);

create temp table cat as
  select id from public.exercises where not is_custom and metric_type = 'weight_reps' order by key limit 1;
grant select on cat to authenticated;

-- Ana has one completed session far in the past (older than any "first page"):
-- warm-up 100 x 3 (volume 300, Epley 110.0) and working 80 x 8 (volume 640, Epley 101.33 -> 101.5).
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a5000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Old', 'completed',
   '2025-09-01 10:00+00', '2025-09-01 11:00+00');
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a5000000-0000-4000-8000-000000000001', (select id from cat), 0, 1, 'warm_up', 100, 3, '2025-09-01 10:05+00'),
  ('a5000000-0000-4000-8000-000000000001', (select id from cat), 0, 2, 'working', 80, 8, '2025-09-01 10:20+00');

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select has_table('public', 'training_preferences', 'public.training_preferences exists');
select columns_are('public', 'training_preferences', array['user_id', 'warm_up_included', 'updated_at', 'previous_values_mode', 'bar_weight_kg', 'plate_kg', 'plate_rounding_kg', 'dumbbell_rounding_kg'],
  'training_preferences has exactly the planned columns');
select fk_ok('public', 'training_preferences', 'user_id', 'auth', 'users', 'id',
  'user_id references auth.users.id');
select has_pk('public', 'training_preferences', 'training_preferences has a primary key');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.training_preferences'::regclass),
  'RLS is enabled and forced on training_preferences');
select policies_are('public', 'training_preferences', array[
  'training_preferences_select_own', 'training_preferences_insert_own',
  'training_preferences_update_own', 'training_preferences_delete_own'
], 'training_preferences has the four owner policies');
select table_privs_are('public', 'training_preferences', 'anon', array[]::text[],
  'anon has no privileges on training_preferences');
select table_privs_are('public', 'training_preferences', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on training_preferences');
select is(
  (select prosecdef from pg_proc where proname = 'warm_up_included' and pronamespace = 'public'::regnamespace),
  false, 'warm_up_included is security invoker');
select ok(not has_function_privilege('anon', 'public.warm_up_included()', 'execute'),
  'anon cannot execute warm_up_included');
select ok(has_function_privilege('authenticated', 'public.warm_up_included()', 'execute'),
  'authenticated can execute warm_up_included');

-- Defaults, one row per user, cascade (as postgres)
insert into public.training_preferences (user_id) values ('11111111-1111-4111-8111-111111111111');
select is(
  (select warm_up_included from public.training_preferences where user_id = '11111111-1111-4111-8111-111111111111'),
  false, 'the default is warm-up excluded');
select throws_ok(
  $$insert into public.training_preferences (user_id) values ('11111111-1111-4111-8111-111111111111')$$,
  '23505', null, 'a second row for the same user is rejected');
delete from public.training_preferences where user_id = '11111111-1111-4111-8111-111111111111';
delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.training_preferences where user_id = '33333333-3333-4333-8333-333333333333'),
  0, 'deleting a user with a preference row succeeds and cascades');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A, who has no row)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.training_preferences), 0, 'A cannot read B''s row');
select throws_ok(
  $$insert into public.training_preferences (user_id) values ('22222222-2222-4222-8222-222222222222')$$,
  '42501', null, 'A cannot insert a row for B');
select results_eq(
  $$with u as (update public.training_preferences set warm_up_included = false
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s row affects 0 rows');
select results_eq(
  $$with d as (delete from public.training_preferences
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'A deleting B''s row affects 0 rows');
select throws_ok('truncate public.training_preferences', '42501', null, 'A cannot truncate training_preferences');

-- ---------------------------------------------------------------------------
-- Aggregates follow the flag (A, session 400 days old)
-- ---------------------------------------------------------------------------
select is(public.warm_up_included(), false, 'with no row the helper returns false');
select results_eq(
  $$select best_one_rm, total_volume from public.exercise_history((select id from cat))$$,
  $$values (101.5::numeric, 640::numeric)$$,
  'off: warm-up ignored (volume 640, 1RM from 80 x 8)');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, true)$$,
  'A saves with the flag on');
select is(public.warm_up_included(), true, 'save_preferences wrote the flag and the helper returns true');
select results_eq(
  $$select best_one_rm, total_volume from public.exercise_history((select id from cat))$$,
  $$values (110.0::numeric, 940::numeric)$$,
  'on: the old warm-up counts (volume 940, 1RM from 100 x 3)');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint)$$,
  'A saves without the flag argument');
select is(public.warm_up_included(), true, 'a null flag argument leaves the preference unchanged');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, false)$$,
  'A saves with the flag off');
select is(public.warm_up_included(), false, 'the flag can be turned off again');

-- Calculator settings (T-097): defaults, save_preferences params, constraints, isolation.
select results_eq(
  $$select bar_weight_kg, plate_kg::numeric[], plate_rounding_kg, dumbbell_rounding_kg
    from public.training_preferences$$,
  $$values (20.0::numeric, '{25,20,15,10,5,2.5,2,1.25,1,0.5}'::numeric[], 2.50::numeric, 2.00::numeric)$$,
  'the calculator columns default to the section 4.9 values');
select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null,
      7.5, '{20,10,2.5,0.5}'::numeric[], 5, 1)$$,
  'A saves the calculator settings');
select results_eq(
  $$select bar_weight_kg, plate_kg::numeric[], plate_rounding_kg, dumbbell_rounding_kg
    from public.training_preferences$$,
  $$values (7.5::numeric, '{20,10,2.5,0.5}'::numeric[], 5.00::numeric, 1.00::numeric)$$,
  'save_preferences wrote the four calculator columns');
select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint)$$,
  'A saves without the calculator arguments');
select results_eq(
  $$select bar_weight_kg, plate_kg::numeric[], plate_rounding_kg, dumbbell_rounding_kg
    from public.training_preferences$$,
  $$values (7.5::numeric, '{20,10,2.5,0.5}'::numeric[], 5.00::numeric, 1.00::numeric)$$,
  'null calculator arguments leave the columns unchanged');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, 12)$$,
  '23514', null, 'a bar outside the catalogue is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, null, '{}'::numeric[])$$,
  '23514', null, 'an empty plate set is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, null, '{25,25}'::numeric[])$$,
  '23514', null, 'a duplicate plate is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, null, '{25,3}'::numeric[])$$,
  '23514', null, 'an unknown plate is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, null, null, 0)$$,
  '23514', null, 'a zero plate rounding is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, null, null, 26)$$,
  '23514', null, 'a plate rounding above 25 is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint, null, null, null, null, null, null, null, null, null, 0)$$,
  '23514', null, 'a zero dumbbell rounding is rejected');
select results_eq(
  $$with u as (update public.training_preferences set plate_rounding_kg = 10
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A cannot update B''s calculator settings');

reset role;
select is(
  (select warm_up_included from public.training_preferences where user_id = '22222222-2222-4222-8222-222222222222'),
  true, 'B''s row is unchanged after A''s attempts and saves');

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
set local role anon;
select throws_ok('select * from public.training_preferences', '42501', null,
  'anon cannot read training_preferences');
select throws_ok(
  $$insert into public.training_preferences (user_id) values ('11111111-1111-4111-8111-111111111111')$$,
  '42501', null, 'anon cannot insert into training_preferences');
reset role;

select * from finish();
rollback;
