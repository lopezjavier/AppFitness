-- pgTAP: public.exercises schema, constraints, catalog and RLS isolation (T-042, US-09, ADR-38).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(42);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- A custom exercise owned by B and one by C (C is deleted later to test the cascade).
insert into public.exercises (id, name, muscle_group, equipment, is_custom, created_by) values
  ('bbbbbbbb-0000-4000-8000-000000000001', 'Remo B', 'back', 'cable', true, '22222222-2222-4222-8222-222222222222'),
  ('cccccccc-0000-4000-8000-000000000001', 'Remo C', 'back', 'cable', true, '33333333-3333-4333-8333-333333333333');

-- ---------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'muscle_group', array['chest', 'back', 'shoulders', 'biceps', 'triceps',
  'forearms', 'quads', 'hamstrings', 'glutes', 'calves', 'core', 'full_body'], 'muscle_group enum labels');
select enum_has_labels('public', 'equipment', array['barbell', 'dumbbell', 'machine', 'cable', 'bodyweight',
  'kettlebell', 'band', 'other'], 'equipment enum labels');
select enum_has_labels('public', 'exercise_metric_type', array['weight_reps', 'reps_only', 'duration',
  'weight_duration', 'distance_duration', 'bodyweight', 'assisted', 'weighted', 'steps_duration',
  'floors_duration'], 'exercise_metric_type enum labels');
select columns_are('public', 'exercises', array[
  'id', 'name', 'key', 'muscle_group', 'secondary_muscles', 'equipment', 'metric_type', 'is_custom',
  'created_by', 'created_at', 'per_hand', 'movement_pattern'
], 'exercises has exactly the planned columns');
select fk_ok('public', 'exercises', 'created_by', 'auth', 'users', 'id', 'created_by references auth.users.id');
select has_index('public', 'exercises', 'exercises_created_by_idx', 'index on created_by exists');
select has_index('public', 'exercises', 'exercises_key_key', 'unique index on key exists');

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.exercises'::regclass),
  'RLS is enabled and forced on exercises');
select policies_are('public', 'exercises', array[
  'exercises_select', 'exercises_insert_own_custom', 'exercises_update_own_custom', 'exercises_delete_own_custom'
], 'exercises has the four policies');
select table_privs_are('public', 'exercises', 'anon', array[]::text[], 'anon has no privileges on exercises');
select table_privs_are('public', 'exercises', 'authenticated', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  'authenticated has CRUD but no TRUNCATE on exercises');

-- ---------------------------------------------------------------------------
-- Catalog (seeded by the exercise_catalog migration)
-- ---------------------------------------------------------------------------
select cmp_ok(
  (select count(*)::int from public.exercises where not is_custom and key is not null), '>=', 600,
  'the catalog migration loaded the catalog rows');
select is(
  (select count(*)::int from public.exercises where not is_custom and name is not null), 0,
  'catalog rows carry no name (ADR-38)');
select is(
  (select count(*)::int from public.exercises where not is_custom and created_by is not null), 0,
  'catalog rows have no owner');
select results_eq(
  $$select key, metric_type::text from public.exercises
    where key in ('pull-up', 'plank', 'treadmill-running', 'assisted-pull-ups', 'weighted-dips',
                  'dumbbell-farmers-walk', 'stair-climber', 'crunches', 'bench-press')
    order by key$$,
  $$values ('assisted-pull-ups', 'assisted'), ('bench-press', 'weight_reps'), ('crunches', 'reps_only'),
           ('dumbbell-farmers-walk', 'weight_duration'), ('plank', 'duration'), ('pull-up', 'bodyweight'),
           ('stair-climber', 'floors_duration'), ('treadmill-running', 'distance_duration'),
           ('weighted-dips', 'weighted')$$,
  'catalog example keys carry their mapped metric type (T-092)');
select cmp_ok(
  (select count(distinct metric_type)::int from public.exercises where not is_custom), '>=', 8,
  'the catalog uses at least eight of the ten metric types');
select is(
  (select count(*)::int from public.exercises where not is_custom and metric_type = 'steps_duration'), 0,
  'no catalog row uses steps_duration (custom exercises only)');
select is(
  (select count(distinct key)::int from public.exercises where key is not null),
  (select count(*)::int from public.exercises where key is not null),
  'catalog keys are unique');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.exercises (key, muscle_group, equipment)
    select key, muscle_group, equipment from public.exercises where not is_custom limit 1$$,
  '23505', null, 'a duplicate catalog key is rejected');
select throws_ok(
  $$insert into public.exercises (name, muscle_group, equipment, is_custom, created_by)
    values ('  ', 'chest', 'barbell', true, '11111111-1111-4111-8111-111111111111')$$,
  '23514', null, 'a blank custom name is rejected');
select throws_ok(
  $$insert into public.exercises (name, muscle_group, equipment, is_custom, created_by)
    values (repeat('x', 101), 'chest', 'barbell', true, '11111111-1111-4111-8111-111111111111')$$,
  '23514', null, 'a 101-character custom name is rejected');
select lives_ok(
  $$insert into public.exercises (name, muscle_group, equipment, is_custom, created_by)
    values (repeat('x', 100), 'chest', 'barbell', true, '11111111-1111-4111-8111-111111111111')$$,
  'a 100-character custom name is accepted');
select throws_ok(
  $$insert into public.exercises (name, muscle_group, equipment, is_custom)
    values ('Sin dueno', 'chest', 'barbell', true)$$,
  '23514', null, 'a custom exercise without owner is rejected');
select throws_ok(
  $$insert into public.exercises (name, key, muscle_group, equipment, is_custom, created_by)
    values ('Con key', 'con-key', 'chest', 'barbell', true, '11111111-1111-4111-8111-111111111111')$$,
  '23514', null, 'a custom exercise with a key is rejected');
select throws_ok(
  $$insert into public.exercises (key, muscle_group, equipment, name)
    values ('catalogo-con-nombre', 'chest', 'barbell', 'Nombre')$$,
  '23514', null, 'a catalog row with a name is rejected');
select throws_ok(
  $$insert into public.exercises (muscle_group, equipment) values ('chest', 'barbell')$$,
  '23514', null, 'a catalog row without key is rejected');

delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.exercises where created_by = '33333333-3333-4333-8333-333333333333'),
  0, 'deleting the auth user cascades to their custom exercises');
select cmp_ok(
  (select count(*)::int from public.exercises where not is_custom), '>=', 600,
  'deleting a user keeps the catalog');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select cmp_ok(
  (select count(*)::int from public.exercises where not is_custom), '>=', 600,
  'A can read the catalog');
insert into public.exercises (id, name, muscle_group, equipment, is_custom)
  values ('aaaaaaaa-0000-4000-8000-000000000001', 'Press Hammer unilateral', 'chest', 'machine', true);
select is(
  (select created_by from public.exercises where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  '11111111-1111-4111-8111-111111111111'::uuid,
  'created_by defaults to the caller');
select is(
  (select count(*)::int from public.exercises where name = 'Press Hammer unilateral'), 1,
  'A sees their custom exercise');
select is(
  (select count(*)::int from public.exercises where name = 'Remo B'), 0,
  'A does not see B''s custom exercise');
select throws_ok(
  $$insert into public.exercises (name, muscle_group, equipment, is_custom, created_by)
    values ('Para B', 'chest', 'barbell', true, '22222222-2222-4222-8222-222222222222')$$,
  '42501', null, 'A cannot create a custom exercise owned by B');
select throws_ok(
  $$insert into public.exercises (key, muscle_group, equipment) values ('mi-catalogo', 'chest', 'barbell')$$,
  '42501', null, 'A cannot insert a catalog row');
select results_eq(
  $$with u as (update public.exercises set name = 'Hackeado' where id = 'bbbbbbbb-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s exercise affects 0 rows');
select results_eq(
  $$with d as (delete from public.exercises where id = 'bbbbbbbb-0000-4000-8000-000000000001' returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'A deleting B''s exercise affects 0 rows');
select results_eq(
  $$with u as (update public.exercises set muscle_group = 'back' where not is_custom returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating catalog rows affects 0 rows');
select results_eq(
  $$with d as (delete from public.exercises where not is_custom returning 1)
    select count(*)::int from d$$,
  $$values (0)$$, 'A deleting catalog rows affects 0 rows');
select throws_ok(
  $$update public.exercises set created_by = '22222222-2222-4222-8222-222222222222'
    where id = 'aaaaaaaa-0000-4000-8000-000000000001'$$,
  '42501', null, 'A cannot hand their exercise over to B');
select throws_ok('truncate public.exercises', '42501', null, 'A cannot truncate exercises');

-- ---------------------------------------------------------------------------
-- RLS isolation (as user B) and anonymous
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is(
  (select count(*)::int from public.exercises where name = 'Press Hammer unilateral'), 0,
  'B does not see A''s custom exercise');

reset role;
set local role anon;
select throws_ok('select * from public.exercises', '42501', null, 'anon cannot read exercises');
reset role;

select * from finish();
rollback;
