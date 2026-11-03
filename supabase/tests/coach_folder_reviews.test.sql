-- pgTAP: coach_folder_reviews, save_folder_review, coach_folder_for_review, coach_apply_change and
-- apply_folder_review_copy (T-207, US-95 and US-93 "from a folder").
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(45);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('d7000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;
create function pg_temp.ex(p_key text, p_sets int, p_group int default null) returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', pg_temp.k(p_key), 'rest_seconds', 90, 'rep_min', 8, 'rep_max', 12,
    'target_rpe', 8, 'superset_group', p_group, 'notes', 'nota ' || p_key,
    'sets', (select jsonb_agg(jsonb_build_object('reps', 8, 'set_type', 'working', 'weight_kg', 20)) from generate_series(1, p_sets))) $$;
-- The change list of the review: remove the second exercise of routine 1, raise the RPE of its third, add one to routine 2.
create function pg_temp.changes() returns jsonb language sql as $$
  select jsonb_build_array(
    jsonb_build_object('id', 'c1', 'op', 'set_rpe', 'routine_index', 1, 'position', 3, 'rpe', 9, 'reason', 'Sube el esfuerzo.'),
    jsonb_build_object('id', 'c2', 'op', 'remove_exercise', 'routine_index', 1, 'position', 2, 'reason', 'Sobra este ejercicio.'),
    jsonb_build_object('id', 'c3', 'op', 'add_exercise', 'routine_index', 2, 'exercise_key', 'lateral-raise',
      'sets', jsonb_build_array(jsonb_build_object('n', 2, 'type', 'working', 'rep_min', 10, 'rep_max', 15, 'rpe', 8, 'rest_seconds', 90)),
      'reason', 'Falta deltoides lateral.')) $$;
-- Snapshot of every row of the folder's routines (exercises and sets included).
create function pg_temp.snap(p_folder uuid) returns jsonb language sql as $$
  select jsonb_build_object(
    'routines', (select jsonb_agg(to_jsonb(r) order by r.id) from public.routines r where r.folder_id = p_folder),
    'exercises', (select jsonb_agg(to_jsonb(re) order by re.id) from public.routine_exercises re
                  join public.routines r on r.id = re.routine_id where r.folder_id = p_folder),
    'sets', (select jsonb_agg(to_jsonb(s) order by s.id) from public.routine_exercise_sets s
             join public.routine_exercises re on re.id = s.routine_exercise_id
             join public.routines r on r.id = re.routine_id where r.folder_id = p_folder)) $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'fr' || n || '@example.com' from generate_series(1, 3) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status) values
  ('d7000000-0000-4000-8000-00000000a001', pg_temp.u(1), current_date, 'muscle', '{}', 'proposed');

-- ---------------------------------------------------------------------------
-- Structure
-- ---------------------------------------------------------------------------
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_folder_reviews'::regclass),
  'RLS is enabled and forced on coach_folder_reviews');
select table_privs_are('public', 'coach_folder_reviews', 'anon', array[]::text[], 'anon has no privileges on coach_folder_reviews');
select table_privs_are('public', 'coach_folder_reviews', 'authenticated', array['SELECT', 'INSERT', 'DELETE'],
  'authenticated can only select, insert and delete (no update)');
select fk_ok('public', 'coach_folder_reviews', 'user_id', 'auth', 'users', 'id', 'user_id references auth.users');
select fk_ok('public', 'coach_folder_reviews', 'folder_id', 'public', 'routine_folders', 'id', 'folder_id references routine_folders');
select fk_ok('public', 'coach_folder_reviews', 'plan_id', 'public', 'coach_plans', 'id', 'plan_id references coach_plans');
select ok(not has_function_privilege('anon', 'public.apply_folder_review_copy(uuid,text[])', 'execute'), 'anon cannot apply a copy');

-- ---------------------------------------------------------------------------
-- A folder of user 1: Torso (press, row as a superset, lateral raise) and Pierna (leg extension)
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);
create temp table fx (folder uuid, torso uuid, pierna uuid, review uuid, block_review uuid, copy uuid);
grant select, insert, update on fx to authenticated;
insert into fx (folder) select public.create_routine_folder('Mi carpeta');
update fx set torso = public.save_routine(jsonb_build_object('name', 'Torso', 'track_as_routine', true, 'notes', 'rutina',
  'exercises', jsonb_build_array(pg_temp.ex('lateral-raise', 3), pg_temp.ex('seated-cable-row', 3, 1), pg_temp.ex('leg-extension', 3, 1))));
update fx set pierna = public.save_routine(jsonb_build_object('name', 'Pierna', 'track_as_routine', true,
  'exercises', jsonb_build_array(pg_temp.ex('leg-extension', 2))));
select public.move_routine_to_folder((select torso from fx), (select folder from fx));
select public.move_routine_to_folder((select pierna from fx), (select folder from fx));

select is(jsonb_array_length(public.coach_folder_for_review((select folder from fx)) -> 'routines'), 2,
  'coach_folder_for_review returns the folder routines');
select is(public.coach_folder_for_review((select folder from fx)) #>> '{routines,0,name}', 'Torso',
  'in the folder order');
select is(jsonb_array_length(public.coach_folder_for_review((select folder from fx)) #> '{routines,0,exercises}'), 3,
  'with their exercises');
select is(public.coach_folder_for_review((select folder from fx)) #>> '{routines,0,exercises,1,exercise_key}', 'seated-cable-row',
  'each exercise carries its catalogue key');
select is(public.coach_folder_for_review((select folder from fx)) #>> '{routines,0,exercises,1,superset_group}', '1',
  'and its superset group');
select is(jsonb_array_length(public.coach_folder_for_review((select folder from fx)) #> '{routines,0,exercises,0,sets}'), 3,
  'and its sets');

-- ---------------------------------------------------------------------------
-- save_folder_review and RLS
-- ---------------------------------------------------------------------------
update fx set review = public.save_folder_review(folder, 'review', null, 'hash-1', jsonb_build_object(
  'message', 'Mensaje', 'falta', '[]'::jsonb, 'notes', '[]'::jsonb, 'comments', '[]'::jsonb, 'changes', pg_temp.changes(),
  'folder_version', public.coach_folder_for_review(folder) ->> 'version'));
update fx set block_review = public.save_folder_review(folder, 'block', 'd7000000-0000-4000-8000-00000000a001', 'hash-2',
  jsonb_build_object('changes', '[]'::jsonb, 'folder_version', public.coach_folder_for_review(folder) ->> 'version'));
select is((select count(*)::int from public.coach_folder_reviews), 2, 'the owner stores and reads two reviews');
select throws_ok($$select public.save_folder_review((select folder from fx), 'block', null, 'h', '{}'::jsonb)$$, '22023', null,
  'a block review needs a plan');
select throws_ok($$select public.save_folder_review((select folder from fx), 'review', 'd7000000-0000-4000-8000-00000000a001', 'h', '{}'::jsonb)$$,
  '22023', null, 'and a plain review cannot carry one');
select throws_ok($$select public.save_folder_review((select folder from fx), 'other', null, 'h', '{}'::jsonb)$$, '22023', null,
  'an unknown purpose is refused');
select throws_ok($$update public.coach_folder_reviews set input_hash = 'x'$$, '42501', null, 'a review cannot be updated');

select pg_temp.as_user(2);
select is((select count(*)::int from public.coach_folder_reviews), 0, 'user B cannot read A''s reviews');
select is(public.coach_folder_for_review((select folder from fx)), null, 'user B cannot read A''s folder for review');
select throws_ok($$select public.save_folder_review((select folder from fx), 'review', null, 'h', '{}'::jsonb)$$, '42501', null,
  'user B cannot store a review for A''s folder');
select throws_ok($$insert into public.coach_folder_reviews (folder_id, purpose, input_hash, result)
  values ((select folder from fx), 'review', 'h', '{}'::jsonb)$$, '42501', null, 'nor insert one directly');
select throws_ok($$insert into public.coach_folder_reviews (user_id, folder_id, purpose, input_hash, result)
  values ('d7000000-0000-4000-8000-000000000001', (select folder from fx), 'review', 'h', '{}'::jsonb)$$, '42501', null,
  'nor impersonate A');
select throws_ok($$select public.apply_folder_review_copy((select review from fx), array['c1'])$$, '55000', null,
  'user B cannot apply A''s review');
delete from public.coach_folder_reviews;
select pg_temp.as_user(1);
select is((select count(*)::int from public.coach_folder_reviews), 2, 'user B''s delete removed nothing of A''s');

-- ---------------------------------------------------------------------------
-- apply_folder_review_copy
-- ---------------------------------------------------------------------------
create temp table before_snap as select pg_temp.snap((select folder from fx)) as s;
grant select on before_snap to authenticated;
update fx set copy = (public.apply_folder_review_copy(review, array['c1', 'c3']) ->> 'folder_id')::uuid;

select is((select name from public.routine_folders where id = (select copy from fx)), 'Mi carpeta (revisada)',
  'the copy folder is named after the source');
select is((select count(*)::int from public.routines where folder_id = (select copy from fx)), 2, 'it holds both routines');
select is((select count(*)::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.folder_id = (select copy from fx) and r.name like 'Torso%'), 3,
  'the rejected removal (c2) is not applied');
select is((select re.target_rpe from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.folder_id = (select copy from fx) and r.name like 'Torso%' and re.position = 2), 9::numeric,
  'the accepted RPE change (c1) is applied to the third exercise');
select is((select re.target_rpe from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.folder_id = (select copy from fx) and r.name like 'Torso%' and re.position = 0), 8::numeric,
  'and the others keep theirs');
select is((select array_agg(x.k order by x.position) from (
             select re.position, e.key as k from public.routine_exercises re
             join public.routines r on r.id = re.routine_id join public.exercises e on e.id = re.exercise_id
             where r.folder_id = (select copy from fx) and r.name like 'Pierna%') x),
  array['leg-extension', 'lateral-raise'], 'the accepted addition (c3) is appended to the second routine');
select is((select count(*)::int from public.routine_exercise_sets s join public.routine_exercises re on re.id = s.routine_exercise_id
           join public.routines r on r.id = re.routine_id join public.exercises e on e.id = re.exercise_id
           where r.folder_id = (select copy from fx) and r.name like 'Pierna%' and e.key = 'lateral-raise'), 2, 'with its two sets');
select is((select re.notes from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.folder_id = (select copy from fx) and r.name like 'Torso%' and re.position = 0), 'nota lateral-raise',
  'notes are copied');
select is(pg_temp.snap((select folder from fx)), (select s from before_snap),
  'the source folder rows (routines, exercises, sets, updated_at) are identical after the copy');

select is((select count(*)::int from public.routines r join fx on r.id = fx.torso where r.folder_id = fx.folder), 1,
  'the source routines stay in the source folder');
select is(public.apply_folder_review_copy((select review from fx), array[]::text[]) ->> 'folder_id' is not null, true,
  'an empty list makes a plain copy');
select ok((select count(*) from public.routine_folders where name = 'Mi carpeta (revisada) 2') = 1,
  'the second copy is named with a number');
select throws_ok($$select public.apply_folder_review_copy((select review from fx), array['c9'])$$, '22023', null,
  'an unknown change id is refused');
select throws_ok($$select public.apply_folder_review_copy((select block_review from fx), array[]::text[])$$, '55000', null,
  'a block review cannot be applied as a copy');

-- A folder edited after the review is stale; a fresh read shows the edit.
select public.save_routine(jsonb_build_object('id', (select torso from fx), 'name', 'Torso', 'track_as_routine', true,
  'exercises', jsonb_build_array(pg_temp.ex('lateral-raise', 4))));
select throws_ok($$select public.apply_folder_review_copy((select review from fx), array['c1'])$$, '55000', null,
  'a folder edited since the review is refused');
select is(jsonb_array_length(public.coach_folder_for_review((select folder from fx)) #> '{routines,0,exercises}'), 1,
  'and coach_folder_for_review returns the edited routine');

-- ---------------------------------------------------------------------------
-- The link trigger still counts a workout of an edited block routine (US-93 regression)
-- ---------------------------------------------------------------------------
reset role;
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
select 'd7000000-0000-4000-8000-00000000a001', pg_temp.u(1), 1, 1, 'Torso', '[]', torso from fx;
update public.coach_plans set status = 'accepted', folder_id = (select folder from fx)
where id = 'd7000000-0000-4000-8000-00000000a001';
set local role authenticated;
select pg_temp.as_user(1);
select public.save_routine(jsonb_build_object('id', (select torso from fx), 'name', 'Torso', 'track_as_routine', true,
  'exercises', jsonb_build_array(pg_temp.ex('leg-extension', 2))));
select public.save_completed_workout(jsonb_build_object('name', 'Torso', 'routine_id', (select torso from fx),
  'started_at', now(), 'duration_s', 3000,
  'exercises', jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.k('leg-extension'),
    'sets', jsonb_build_array(jsonb_build_object('weight_kg', 30, 'reps', 10))))));
select isnt((select session_id from public.coach_plan_sessions where plan_id = 'd7000000-0000-4000-8000-00000000a001'), null,
  'completing a workout of the edited routine links its planned session');

-- ---------------------------------------------------------------------------
-- Cascades
-- ---------------------------------------------------------------------------
reset role;
delete from public.coach_plans where id = 'd7000000-0000-4000-8000-00000000a001';
select is((select count(*)::int from public.coach_folder_reviews where purpose = 'block'), 0, 'deleting the plan deletes its block review');
delete from public.routine_folders where id = (select folder from fx);
select is((select count(*)::int from public.coach_folder_reviews), 0, 'deleting the folder deletes its reviews');
delete from auth.users where id = pg_temp.u(1);
select is((select count(*)::int from public.coach_folder_reviews), 0, 'deleting the user leaves no review');

select * from finish();
rollback;
