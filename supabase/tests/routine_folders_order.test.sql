-- pgTAP: routine folders, next workout and manual order (T-098, US-35).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(34);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id from public.exercises where not is_custom order by key limit 1;
grant select on cat to authenticated;

-- Fixtures (as postgres). B owns a folder and two routines (one is_next).
insert into public.routine_folders (id, user_id, name) values
  ('f2000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Carpeta B');
insert into public.routines (id, user_id, name, folder_id, is_next) values
  ('b0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Rutina B1',
   'f2000000-0000-4000-8000-000000000001', true),
  ('b0000000-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'Rutina B2', null, false);

-- Backfill ordering: the migration's own expression on a seeded set (A to Z, case-insensitive).
insert into public.routines (id, user_id, name) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'banana'),
  ('a1000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Apple'),
  ('a1000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'cherry');
select results_eq(
  $$select name from public.routines where user_id = '11111111-1111-4111-8111-111111111111'
    order by row_number() over (partition by user_id, folder_id order by lower(name), name, id)$$,
  $$values ('Apple'), ('banana'), ('cherry')$$,
  'backfill expression orders A to Z ignoring case');
select results_eq(
  $$select position::int from public.routines where user_id = '11111111-1111-4111-8111-111111111111' order by position$$,
  $$values (0), (1), (2)$$,
  'insert trigger gives positions 0..n-1 in the no-folder group');
select is((select position::int from public.routines where id = 'b0000000-0000-4000-8000-000000000001'), 0,
  'first routine of a folder is position 0');
select is((select position::int from public.routines where id = 'b0000000-0000-4000-8000-000000000002'), 0,
  'positions are independent per group');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

create temp table ids (k text primary key, v uuid);
grant all on ids to authenticated;

insert into ids values ('f1', public.create_routine_folder('  Pierna  '));
insert into ids values ('f2', public.create_routine_folder('Empuje'));
select is((select name from public.routine_folders where id = (select v from ids where k = 'f1')), 'Pierna',
  'create_routine_folder trims the name');
select is((select position::int from public.routine_folders where id = (select v from ids where k = 'f2')), 1,
  'a new folder goes to the end');
select throws_ok($$select public.create_routine_folder('   ')$$, '22023', null, 'blank folder name is rejected');
select throws_ok($$select public.create_routine_folder(repeat('x', 101))$$, '22023', null, 'folder name over 100 chars is rejected');

select lives_ok($$select public.rename_routine_folder((select v from ids where k = 'f2'), ' Push ')$$, 'A renames own folder');
select is((select name from public.routine_folders where id = (select v from ids where k = 'f2')), 'Push',
  'rename trims and persists');
select throws_ok($$select public.rename_routine_folder('f2000000-0000-4000-8000-000000000001', 'hack')$$, 'P0002', null,
  'A cannot rename B''s folder');
select throws_ok($$select public.delete_routine_folder('f2000000-0000-4000-8000-000000000001')$$, 'P0002', null,
  'A cannot delete B''s folder');
select throws_ok($$select public.move_routine_to_folder('a1000000-0000-4000-8000-000000000001',
  'f2000000-0000-4000-8000-000000000001')$$, 'P0002', null, 'A cannot move a routine into B''s folder');
select throws_ok($$select public.move_routine_to_folder('b0000000-0000-4000-8000-000000000002', null)$$, 'P0002', null,
  'A cannot move B''s routine');

-- Move two routines into folder f1; they land at the end in call order.
select public.move_routine_to_folder('a1000000-0000-4000-8000-000000000003', (select v from ids where k = 'f1'));
select public.move_routine_to_folder('a1000000-0000-4000-8000-000000000001', (select v from ids where k = 'f1'));
select results_eq(
  $$select name from public.routines where folder_id = (select v from ids where k = 'f1') order by position$$,
  $$values ('cherry'), ('banana')$$,
  'move_routine_to_folder places the routine at the end of the target group');

-- Order.
select public.save_routine_order((select v from ids where k = 'f1'),
  array['a1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000003']::uuid[]);
select results_eq(
  $$select name from public.routines where folder_id = (select v from ids where k = 'f1') order by position$$,
  $$values ('banana'), ('cherry')$$,
  'save_routine_order persists the order');
select throws_ok($$select public.save_routine_order(null,
  array['b0000000-0000-4000-8000-000000000002']::uuid[])$$, '22023', null, 'save_routine_order rejects a foreign routine');
select throws_ok($$select public.save_routine_order(null,
  array['a1000000-0000-4000-8000-000000000001']::uuid[])$$, '22023', null,
  'save_routine_order rejects a routine of another group');
select throws_ok($$select public.save_routine_order((select v from ids where k = 'f1'),
  array['a1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001']::uuid[])$$, '22023', null,
  'save_routine_order rejects duplicated ids');

-- Next workout.
select public.set_next_routine('a1000000-0000-4000-8000-000000000002');
select public.set_next_routine('a1000000-0000-4000-8000-000000000003');
select results_eq($$select name from public.routines where is_next$$, $$values ('cherry')$$,
  'setting another routine moves is_next (at most one)');
select throws_ok($$select public.set_next_routine('b0000000-0000-4000-8000-000000000002')$$, 'P0002', null,
  'A cannot set B''s routine as next');
select public.set_next_routine(null);
select is((select count(*)::int from public.routines where is_next), 0, 'null clears the next routine');
select is((select count(*)::int from public.routine_exercises), 0, 'A sees no exercises yet');

-- Delete folder f1: its two routines go; the no-folder routine survives; a session keeps its data.
reset role;
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat), 0, 3, 8);
insert into public.workout_sessions (id, user_id, routine_id, name, status, ended_at)
  values ('55000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111',
          'a1000000-0000-4000-8000-000000000001', 'Sesion', 'completed', now());
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(public.delete_routine_folder((select v from ids where k = 'f1')), 2,
  'delete_routine_folder returns the number of routines deleted');
select results_eq($$select name from public.routines order by name$$, $$values ('Apple')$$,
  'only the folder''s routines are deleted');
select is((select count(*)::int from public.routine_folders where id = (select v from ids where k = 'f1')), 0,
  'the folder is gone');
select is((select count(*)::int from public.routine_folders where id = (select v from ids where k = 'f2')), 1,
  'another folder is untouched');
select is(public.delete_routine_folder((select v from ids where k = 'f2')), 0, 'an empty folder deletes with count 0');

-- ---------------------------------------------------------------------------
-- As postgres: side effects and B untouched
-- ---------------------------------------------------------------------------
reset role;
select is((select routine_id from public.workout_sessions where id = '55000000-0000-4000-8000-000000000001'), null,
  'the session of a deleted routine remains with routine_id null');
select is((select count(*)::int from public.routine_exercises), 0, 'routine_exercises of deleted routines are gone');
select is((select count(*)::int from public.routines where user_id = '22222222-2222-4222-8222-222222222222'), 2,
  'B''s routines survive');
select is((select is_next from public.routines where id = 'b0000000-0000-4000-8000-000000000001'), true,
  'B''s is_next is untouched');

-- A new routine via save_routine goes to the end of the no-folder group.
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
insert into ids values ('r', public.save_routine(
  jsonb_build_object('name', 'Nueva', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', (select id from cat), 'sets', jsonb_build_array(jsonb_build_object('reps', 8)))))));
select is((select position::int from public.routines where id = (select v from ids where k = 'r')), 2,
  'save_routine places a new routine at max+1 of the no-folder group');

reset role;
delete from auth.users where id = '22222222-2222-4222-8222-222222222222';
select is((select count(*)::int from public.routine_folders where user_id = '22222222-2222-4222-8222-222222222222'), 0,
  'deleting a user with folders cascades');

select * from finish();
rollback;
