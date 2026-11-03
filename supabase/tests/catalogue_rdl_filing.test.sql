-- pgTAP: straight-knee hinge filing of the catalogue (T-213, 07 section 3 "Straight-knee hinge").
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(11);

insert into auth.users (id, email) values ('11111111-1111-4111-8111-111111111111', 'ana@example.com');

-- A custom hinge exercise must stay as the user filed it.
insert into public.exercises (id, name, muscle_group, equipment, metric_type, is_custom, created_by) values
  ('a1300000-0000-4000-8000-000000000001', 'Mi buenos dias', 'back', 'barbell', 'weight_reps', true,
   '11111111-1111-4111-8111-111111111111');

-- A user routine that references an RDL keeps its row after the re-filing.
insert into public.routines (id, user_id, name) values
  ('a1300000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'Pierna');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
select 'a1300000-0000-4000-8000-0000000000a1', id, 0, 3, 8 from public.exercises where key = 'romanian-deadlift';

create temp table hinge_keys (key text) on commit drop;
insert into hinge_keys values
  ('romanian-deadlift'), ('ez-bar-romanian-deadlift'), ('dumbbell-romanian-deadlift'), ('banded-romanian-deadlift'),
  ('smith-machine-rdl'), ('stiff-leg-deadlift'), ('single-leg-romanian-deadlift'),
  ('one-arm-single-leg-dumbbell-romanian-deadlift'), ('one-arm-single-leg-kettlebell-romanian-deadlift'),
  ('kettlebell-single-leg-deadlift'), ('single-leg-rdl-hold'), ('db-kickstand-deadlift'),
  ('double-db-kickstand-deadlift'), ('kettlebell-kickstand-deadlift'), ('good-morning'), ('banded-good-morning'),
  ('bodyweight-good-morning'), ('smith-machine-good-morning');

select is((select count(*)::int from public.exercises e join hinge_keys k using (key)
           where not e.is_custom and e.muscle_group = 'hamstrings'), 18,
  'the 18 straight-knee hinge keys are hamstrings primary');
select is((select count(*)::int from public.exercises e join hinge_keys k using (key)
           where not e.is_custom and e.secondary_muscles = array['glutes']::public.muscle_group[]), 18,
  'each of them lists exactly glutes as secondary');
select is((select count(*)::int from public.exercises
           where not is_custom and muscle_group = any (secondary_muscles)), 0,
  'no catalogue row lists its primary group as secondary');
select is((select muscle_group::text from public.exercises where key = 'good-morning' and not is_custom),
  'hamstrings', 'a good morning is no longer back');
select is((select count(*)::int from public.exercises e join hinge_keys k using (key)
           where not e.is_custom and e.muscle_group = 'back'), 0, 'no straight-knee hinge counts as back');

select isnt((select muscle_group::text from public.exercises where key = 'deadlift' and not is_custom),
  'hamstrings', 'the conventional deadlift is not re-filed');
select isnt((select muscle_group::text from public.exercises where key = 'sumo-deadlift' and not is_custom),
  'hamstrings', 'the sumo deadlift is not re-filed');
select isnt((select muscle_group::text from public.exercises where key = 'hex-bar-deadlift' and not is_custom),
  'hamstrings', 'the hex-bar deadlift is not re-filed');
select is((select count(*)::int from public.exercises
           where not is_custom and key in ('deficit-deadlift', 'pause-deadlift') and muscle_group = 'hamstrings'), 0,
  'the deficit and pause deadlifts are not re-filed');

select is((select muscle_group::text from public.exercises where id = 'a1300000-0000-4000-8000-000000000001'),
  'back', 'a custom exercise keeps the group its owner chose');
select is((select count(*)::int from public.routine_exercises re join public.exercises e on e.id = re.exercise_id
           where re.routine_id = 'a1300000-0000-4000-8000-0000000000a1' and e.key = 'romanian-deadlift'), 1,
  'a routine referencing an RDL keeps its row');

select * from finish();
rollback;
