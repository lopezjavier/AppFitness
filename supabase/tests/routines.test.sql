-- pgTAP: routine_folders, routines, routine_exercises and save_routine (T-043, US-10).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(62);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- Custom exercises: B's, and C's (C is deleted at the end while used in a routine).
insert into public.exercises (id, name, muscle_group, equipment, is_custom, created_by) values
  ('bbbbbbbb-0000-4000-8000-000000000001', 'Remo B', 'back', 'cable', true, '22222222-2222-4222-8222-222222222222'),
  ('cccccccc-0000-4000-8000-000000000001', 'Remo C', 'back', 'cable', true, '33333333-3333-4333-8333-333333333333');

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises where not is_custom limit 3;
grant select on cat to authenticated;

-- Fixtures (as postgres): B owns a folder, a routine with one exercise; A owns a routine.
insert into public.routine_folders (id, user_id, name) values
  ('f2000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Carpeta B');
insert into public.routines (id, user_id, name) values
  ('b0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Rutina B'),
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Rutina A');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps) values
  ('b0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8);
update public.routine_exercises set target_weight_kg = 60
  where routine_id = 'b0000000-0000-4000-8000-000000000001';

-- Data migration (T-089): the migration's own SQL body, replayed on B's legacy-style row (no planned sets yet).
insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
select re.id, gs.n, re.target_weight_kg, re.target_reps, re.planned_set_type
from public.routine_exercises re
cross join lateral generate_series(1, re.target_sets) as gs(n)
where re.routine_id = 'b0000000-0000-4000-8000-000000000001'
on conflict (routine_exercise_id, set_index) do nothing;
create temp table b_re as
  select id from public.routine_exercises where routine_id = 'b0000000-0000-4000-8000-000000000001';
grant select on b_re to authenticated;
select is((select count(*)::int from public.routine_exercise_sets), 3,
  'migration: a legacy row with target_sets 3 becomes three planned sets');
select results_eq(
  $$select set_index::int, target_weight_kg::float8, target_reps::int, set_type::text
    from public.routine_exercise_sets order by set_index$$,
  $$values (1, 60::float8, 8, 'working'), (2, 60::float8, 8, 'working'), (3, 60::float8, 8, 'working')$$,
  'migration: planned sets copy reps, weight and type of the legacy row');

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'set_type', array['working', 'warm_up', 'failure', 'drop'], 'set_type labels');
select columns_are('public', 'routine_exercises', array[
  'id', 'routine_id', 'exercise_id', 'position', 'superset_group', 'target_sets', 'target_reps',
  'target_reps_max', 'target_rpe', 'planned_set_type', 'target_weight_kg', 'rest_seconds', 'notes', 'note_kind'
], 'routine_exercises has the planned columns');
select ok(
  (select bool_and(relrowsecurity and relforcerowsecurity) from pg_class
   where oid in ('public.routine_folders'::regclass, 'public.routines'::regclass,
                 'public.routine_exercises'::regclass)),
  'RLS is enabled and forced on the three routine tables');
select policies_are('public', 'routines', array[
  'routines_select', 'routines_insert', 'routines_update', 'routines_delete'], 'routines has four policies');
select policies_are('public', 'routine_exercises', array[
  'routine_exercises_select', 'routine_exercises_insert', 'routine_exercises_update',
  'routine_exercises_delete'], 'routine_exercises has four policies');
select policies_are('public', 'routine_folders', array[
  'routine_folders_select', 'routine_folders_insert', 'routine_folders_update',
  'routine_folders_delete'], 'routine_folders has four policies');
select table_privs_are('public', 'routines', 'anon', array[]::text[], 'anon has no privileges on routines');
select table_privs_are('public', 'routine_exercises', 'authenticated',
  array['SELECT', 'INSERT', 'UPDATE', 'DELETE'], 'authenticated has CRUD without TRUNCATE');
select function_privs_are('public', 'save_routine', array['jsonb'], 'anon', array[]::text[],
  'anon cannot execute save_routine');
select columns_are('public', 'routine_exercise_sets', array[
  'id', 'routine_exercise_id', 'set_index', 'target_weight_kg', 'target_reps', 'set_type'
], 'routine_exercise_sets has the planned columns');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.routine_exercise_sets'::regclass),
  'RLS is enabled and forced on routine_exercise_sets');
select policies_are('public', 'routine_exercise_sets', array[
  'routine_exercise_sets_select', 'routine_exercise_sets_insert', 'routine_exercise_sets_update',
  'routine_exercise_sets_delete'], 'routine_exercise_sets has four policies');
select table_privs_are('public', 'routine_exercise_sets', 'anon', array[]::text[],
  'anon has no privileges on routine_exercise_sets');

-- ---------------------------------------------------------------------------
-- Checks and indexes (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 0, 8)$$,
  '23514', null, 'sets 0 rejected');
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 11, 8)$$,
  '23514', null, 'sets 11 rejected');
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 0)$$,
  '23514', null, 'reps 0 rejected');
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 51)$$,
  '23514', null, 'reps 51 rejected');
select throws_ok($$insert into public.routine_exercises
  (routine_id, exercise_id, position, target_sets, target_reps, rest_seconds)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8, 601)$$,
  '23514', null, 'rest 601 rejected');
select throws_ok($$insert into public.routine_exercises
  (routine_id, exercise_id, position, target_sets, target_reps, rest_seconds)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8, -1)$$,
  '23514', null, 'rest -1 rejected');
select throws_ok($$insert into public.routine_exercises
  (routine_id, exercise_id, position, target_sets, target_reps, target_rpe)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8, 11)$$,
  '23514', null, 'rpe 11 rejected');
select throws_ok($$insert into public.routine_exercises
  (routine_id, exercise_id, position, target_sets, target_reps, target_reps_max)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8, 6)$$,
  '23514', null, 'reps_max below reps rejected');
select throws_ok($$insert into public.routines (user_id, name)
  values ('11111111-1111-4111-8111-111111111111', '   ')$$,
  '23514', null, 'blank routine name rejected');

update public.routines set is_next = true where id = 'a0000000-0000-4000-8000-000000000001';
select throws_ok($$insert into public.routines (user_id, name, is_next)
  values ('11111111-1111-4111-8111-111111111111', 'Otra', true)$$,
  '23505', null, 'second is_next for the same user rejected');
select lives_ok($$update public.routines set is_next = true where id = 'b0000000-0000-4000-8000-000000000001'$$,
  'is_next allowed for another user');

-- ---------------------------------------------------------------------------
-- RLS isolation and save_routine (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.routines), 1, 'A sees only their routine');
select is((select count(*)::int from public.routine_exercises), 0, 'A does not see B''s routine exercises');
select is((select count(*)::int from public.routine_folders), 0, 'A does not see B''s folders');
select is_empty($$update public.routines set name = 'hack' where id = 'b0000000-0000-4000-8000-000000000001' returning 1$$,
  'A cannot update B''s routine');
select is_empty($$delete from public.routines where id = 'b0000000-0000-4000-8000-000000000001' returning 1$$,
  'A cannot delete B''s routine');
select is((select count(*)::int from public.routine_exercise_sets), 0, 'A does not see B''s planned sets');
select throws_ok($$insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_reps)
  values ((select id from b_re), 9, 8)$$,
  '42501', null, 'A cannot insert a planned set under B''s routine exercise');
select is_empty($$update public.routine_exercise_sets set target_reps = 1 returning 1$$,
  'A cannot update B''s planned sets');
select is_empty($$delete from public.routine_exercise_sets returning 1$$,
  'A cannot delete B''s planned sets');
select throws_ok($$insert into public.routines (user_id, name)
  values ('22222222-2222-4222-8222-222222222222', 'Para B')$$,
  '42501', null, 'A cannot insert a routine for B');
select throws_ok($$insert into public.routines (name, folder_id)
  values ('Con carpeta ajena', 'f2000000-0000-4000-8000-000000000001')$$,
  '42501', null, 'A cannot put a routine in B''s folder');
select throws_ok($$insert into public.routine_folders (user_id, name)
  values ('22222222-2222-4222-8222-222222222222', 'X')$$,
  '42501', null, 'A cannot insert a folder for B');
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('b0000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 0, 3, 8)$$,
  '42501', null, 'A cannot add an exercise row to B''s routine');
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a0000000-0000-4000-8000-000000000001', 'bbbbbbbb-0000-4000-8000-000000000001', 0, 3, 8)$$,
  '42501', null, 'A cannot reference B''s custom exercise');
select lives_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8)$$,
  'A can reference a catalog exercise in their routine');

-- save_routine: create
create temp table saved (id uuid);
grant all on saved to authenticated;
insert into saved
  select public.save_routine(jsonb_build_object(
    'name', '  Torso  ', 'notes', null,
    'exercises', jsonb_build_array(
      jsonb_build_object('exercise_id', (select id from cat where n = 1), 'position', 9,
                         'superset_group', 1, 'rest_seconds', 90, 'target_rpe', 8,
                         'sets', jsonb_build_array(
                           jsonb_build_object('weight_kg', 40, 'reps', 10, 'set_type', 'warm_up'),
                           jsonb_build_object('weight_kg', 80, 'reps', 8, 'set_type', 'working'),
                           jsonb_build_object('weight_kg', 80, 'reps', 8, 'set_type', 'working'),
                           jsonb_build_object('weight_kg', null, 'reps', 6, 'set_type', 'working'))),
      jsonb_build_object('exercise_id', (select id from cat where n = 2), 'position', 3,
                         'superset_group', 1,
                         'sets', jsonb_build_array(
                           jsonb_build_object('reps', 10), jsonb_build_object('reps', 10),
                           jsonb_build_object('reps', 10), jsonb_build_object('reps', 10))))));
select is((select name from public.routines where id = (select id from saved)), 'Torso',
  'save_routine creates the routine with a trimmed name');
select results_eq(
  $$select position::int, superset_group::int, target_sets::int, rest_seconds::int
    from public.routine_exercises where routine_id = (select id from saved) order by position$$,
  $$values (0, 1, 4, 90), (1, 1, 4, 120)$$,
  'positions are re-assigned from array order and rest defaults to 120');
select results_eq(
  $$select s.set_index::int, s.target_weight_kg::float8, s.target_reps::int, s.set_type::text
    from public.routine_exercise_sets s join public.routine_exercises re on re.id = s.routine_exercise_id
    where re.routine_id = (select id from saved) and re.position = 0 order by s.set_index$$,
  $$values (1, 40::float8, 10, 'warm_up'), (2, 80::float8, 8, 'working'), (3, 80::float8, 8, 'working'),
           (4, null::float8, 6, 'working')$$,
  'the four planned sets are saved in array order with weight, reps and type');
select results_eq(
  $$select target_sets::int, target_reps::int, target_rpe::float8
    from public.routine_exercises where routine_id = (select id from saved) order by position$$,
  $$values (4, 8, 8::float8), (4, 10, null::float8)$$,
  'target_sets is the set count, target_reps the first working set, rpe is kept');

-- save_routine: replace
select lives_ok($$select public.save_routine(jsonb_build_object(
    'id', (select id from saved), 'name', 'Torso v2',
    'exercises', jsonb_build_array(
      jsonb_build_object('exercise_id', (select id from cat where n = 3),
        'sets', jsonb_build_array(jsonb_build_object('reps', 5, 'weight_kg', 100),
                                  jsonb_build_object('reps', 5, 'weight_kg', 100))))))$$,
  'save_routine updates an own routine');
select results_eq(
  $$select (select name from public.routines where id = (select id from saved)),
           (select count(*)::int from public.routine_exercises where routine_id = (select id from saved)),
           (select count(*)::int from public.routine_exercise_sets s
              join public.routine_exercises re on re.id = s.routine_exercise_id
              where re.routine_id = (select id from saved))$$,
  $$values ('Torso v2'::text, 1, 2)$$,
  'resaving replaces the exercise rows and their planned sets');

-- save_routine: atomic on an invalid set
select throws_ok($$select public.save_routine(jsonb_build_object(
    'id', (select id from saved), 'name', 'Roto',
    'exercises', jsonb_build_array(
      jsonb_build_object('exercise_id', (select id from cat where n = 1),
                         'sets', jsonb_build_array(jsonb_build_object('reps', 8))),
      jsonb_build_object('exercise_id', (select id from cat where n = 2),
                         'sets', jsonb_build_array(jsonb_build_object('reps', 0))))))$$,
  '22023', null, 'reps 0 aborts save_routine');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'Roto', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat where n = 1),
                       'sets', jsonb_build_array(jsonb_build_object('reps', 51))))))$$,
  '22023', null, 'reps 51 aborts save_routine');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'Roto', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat where n = 1),
                       'sets', jsonb_build_array(jsonb_build_object('reps', 8, 'weight_kg', -1))))))$$,
  '22023', null, 'negative weight aborts save_routine');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'Roto', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat where n = 1),
                       'sets', (select jsonb_agg(jsonb_build_object('reps', 8)) from generate_series(1, 11))))))$$,
  '22023', null, 'eleven sets abort save_routine');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'Roto', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat where n = 1), 'sets', '[]'::jsonb))))$$,
  '22023', null, 'zero sets abort save_routine');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'Roto', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat where n = 1),
                       'sets', jsonb_build_array(jsonb_build_object('reps', 8, 'set_type', 'bogus'))))))$$,
  '22023', null, 'an unknown set type aborts save_routine');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'Roto', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat where n = 1), 'superset_group', 1,
                       'sets', jsonb_build_array(jsonb_build_object('reps', 8))),
    jsonb_build_object('exercise_id', (select id from cat where n = 2), 'superset_group', 1,
                       'sets', jsonb_build_array(jsonb_build_object('reps', 8), jsonb_build_object('reps', 8))))))$$,
  '22023', null, 'superset members with different set counts are rejected');
select results_eq(
  $$select (select name from public.routines where id = (select id from saved)),
           (select count(*)::int from public.routine_exercises where routine_id = (select id from saved)),
           (select count(*)::int from public.routine_exercise_sets s
              join public.routine_exercises re on re.id = s.routine_exercise_id
              where re.routine_id = (select id from saved))$$,
  $$values ('Torso v2'::text, 1, 2)$$,
  'failed saves leave the previous state unchanged');
select throws_ok($$select public.save_routine(jsonb_build_object(
    'name', 'Con ajeno',
    'exercises', jsonb_build_array(
      jsonb_build_object('exercise_id', 'bbbbbbbb-0000-4000-8000-000000000001',
                         'sets', jsonb_build_array(jsonb_build_object('reps', 8))))))$$,
  '42501', null, 'save_routine rejects another user''s custom exercise');
select is((select count(*)::int from public.routines where name = 'Con ajeno'), 0,
  'the rejected create left no routine behind');
select throws_ok($$select public.save_routine(jsonb_build_object(
    'id', 'b0000000-0000-4000-8000-000000000001', 'name', 'Robada', 'exercises', '[]'::jsonb))$$,
  'P0002', null, 'save_routine cannot update B''s routine');

-- ---------------------------------------------------------------------------
-- Cascade on user deletion (as postgres): routines + a custom exercise used in a routine
-- ---------------------------------------------------------------------------
reset role;
insert into public.routines (id, user_id, name) values
  ('c0000000-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'Rutina C');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps) values
  ('c0000000-0000-4000-8000-000000000001', 'cccccccc-0000-4000-8000-000000000001', 0, 3, 8);
-- The FK is deferred to commit; force the check now to prove the final state is consistent.
select lives_ok($t$do $body$ begin
    delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
    set constraints all immediate;
  end $body$$t$,
  'deleting a user with a routine using their custom exercise succeeds');
select is((select count(*)::int from public.routines where user_id = '33333333-3333-4333-8333-333333333333'), 0,
  'the deleted user''s routines are gone');
select is((select count(*)::int from public.routine_exercises
           where routine_id = 'c0000000-0000-4000-8000-000000000001'), 0,
  'the deleted user''s routine exercises are gone');
select is((select count(*)::int from public.routine_exercise_sets s
           where not exists (select 1 from public.routine_exercises re where re.id = s.routine_exercise_id)), 0,
  'no orphan planned sets remain');

select * from finish();
rollback;
