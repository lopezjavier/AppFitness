-- pgTAP: coach_plan_sessions, attach_coach_block, save_coach_block (T-163, US-72; rules in planning/04 section 9).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(56);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c8000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

-- Catalogue exercise ids by role.
create function pg_temp.ex(p_pattern text, p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = p_pattern
    and metric_type = 'weight_reps' and equipment = 'barbell' order by key offset p_nth limit 1 $$;
create function pg_temp.iso(p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key offset p_nth limit 1 $$;

create function pg_temp.exercise(p_id uuid, p_role text, p_sets int, p_rmin int, p_rmax int, p_rest int) returns jsonb
language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', p_sets,
    'rep_min', p_rmin, 'rep_max', p_rmax, 'rest_seconds', p_rest,
    'reason', 'Buen ejercicio base para tu objetivo.') $$;

-- Two sessions, each with a main lift followed by an isolation exercise.
create function pg_temp.sessions() returns jsonb language sql as $$
  select jsonb_build_array(
    jsonb_build_object('session_index', 1, 'name', 'Tren superior', 'focus', jsonb_build_array('chest', 'biceps'),
      'exercises', jsonb_build_array(pg_temp.exercise(pg_temp.ex('horizontal_push', 0), 'main', 4, 6, 10, 150),
                                     pg_temp.exercise(pg_temp.iso(0), 'isolation', 3, 10, 15, 90))),
    jsonb_build_object('session_index', 2, 'name', 'Tren inferior', 'focus', jsonb_build_array('quads'),
      'exercises', jsonb_build_array(pg_temp.exercise(pg_temp.ex('squat', 0), 'main', 4, 6, 10, 150),
                                     pg_temp.exercise(pg_temp.iso(1), 'isolation', 3, 10, 15, 90)))) $$;

create function pg_temp.training(p_equipment jsonb default '[]', p_days int default 2) returns jsonb language sql as $$
  select jsonb_build_object('days_per_week', p_days, 'days_chosen', false, 'session_minutes', 60,
    'rep_scheme', 'single', 'block_weeks', 4, 'equipment', p_equipment) $$;

create function pg_temp.attach(p_plan uuid, p_sessions jsonb default null, p_training jsonb default null) returns jsonb
language sql as $$
  select public.attach_coach_block(p_plan, coalesce(p_training, pg_temp.training()),
    coalesce(p_sessions, pg_temp.sessions()), '{"model":"m","attempts":1}', '{}') $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'blocks' || n || '@example.com' from generate_series(1, 4) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status) values
  ('c8000000-0000-4000-8000-0000000000a1', pg_temp.u(1), current_date, 'muscle', '{}', 'proposed'),
  ('c8000000-0000-4000-8000-0000000000a2', pg_temp.u(2), current_date, 'muscle', '{}', 'proposed'),
  ('c8000000-0000-4000-8000-0000000000a3', pg_temp.u(3), current_date, 'muscle', '{}', 'superseded'),
  ('c8000000-0000-4000-8000-0000000000a4', pg_temp.u(4), current_date, 'muscle', '{}', 'proposed');
-- User 4 already owns a routine named like the first session (routine names are unique).
insert into public.routines (user_id, name) values (pg_temp.u(4), 'Tren superior');
-- A custom exercise of user 2 (not visible to user 1).
insert into public.exercises (id, name, muscle_group, equipment, is_custom, created_by, movement_pattern)
values ('c8000000-0000-4000-8000-0000000000e2', 'Press raro', 'chest', 'barbell', true, pg_temp.u(2), 'horizontal_push');

-- ---------------------------------------------------------------------------
-- Structure
-- ---------------------------------------------------------------------------
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_plan_sessions'::regclass),
  'RLS is enabled and forced on coach_plan_sessions');
select ok(not has_table_privilege('authenticated', 'public.coach_plan_sessions', 'insert'), 'authenticated cannot insert sessions');
select ok(not has_table_privilege('authenticated', 'public.coach_plan_sessions', 'update'), 'authenticated cannot update sessions');
select ok(not has_table_privilege('authenticated', 'public.coach_plan_sessions', 'delete'), 'authenticated cannot delete sessions');
select ok(not has_table_privilege('anon', 'public.coach_plan_sessions', 'select'), 'anon cannot read sessions');
select ok(not has_function_privilege('anon', 'public.attach_coach_block(uuid,jsonb,jsonb,jsonb,jsonb)', 'execute'),
  'anon cannot execute attach_coach_block');
select ok(not has_function_privilege('anon', 'public.save_coach_block(uuid)', 'execute'), 'anon cannot execute save_coach_block');
select ok(not has_function_privilege('authenticated', 'public.coach_assert_block(uuid,jsonb,jsonb)', 'execute'),
  'coach_assert_block is internal');
select throws_ok(
  $$insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
    values ('c8000000-0000-4000-8000-0000000000a3', pg_temp.u(3), 1, 8, 'x', '[]')$$,
  '23514', null, 'session_index above 7 is rejected');
select throws_ok(
  $$insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
    values ('c8000000-0000-4000-8000-0000000000a3', pg_temp.u(3), 1, 1, 'x', '{}')$$,
  '23514', null, 'exercises must be a JSON array');
select throws_ok(
  $$insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
    values ('c8000000-0000-4000-8000-0000000000a3', pg_temp.u(3), 5, 1, 'x', '[]')$$,
  '23514', null, 'week above 4 is rejected');
select throws_ok(
  $$insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
    values (pg_temp.u(3), 'c8000000-0000-4000-8000-0000000000a3', 'bogus', '{}', '{}')$$,
  '23514', null, 'a recommendation kind outside the set is rejected');

-- ---------------------------------------------------------------------------
-- attach_coach_block
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);

select is(jsonb_array_length(pg_temp.attach('c8000000-0000-4000-8000-0000000000a1')->'sessions'), 8,
  'attach returns the ids of both sessions in each of the 4 weeks');
select is((select count(*)::int from public.coach_plan_sessions), 8, 'the owner sees 2 sessions x 4 weeks');
select is((select count(distinct week)::int from public.coach_plan_sessions where not deload), 4, 'weeks 1 to 4 exist and none is a deload');
select ok((select training is not null from public.coach_plans where id = 'c8000000-0000-4000-8000-0000000000a1'),
  'the plan carries the training summary');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1)), 0,
  'attaching a block creates no routine');
select is((select count(*)::int from public.coach_recommendations where kind = 'block' and status = 'pending'), 1,
  'a pending block recommendation is written');

-- Re-proposing replaces the unsaved sessions.
select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1');
select is((select count(*)::int from public.coach_plan_sessions), 8, 'a second proposal replaces the unsaved sessions');
select is((select count(*)::int from public.coach_recommendations where kind = 'block' and status = 'discarded'), 1,
  'the previous block recommendation is discarded');

select throws_ok($$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a2')$$, 'P0002', null,
  'a foreign plan is not found');
select throws_ok($$select pg_temp.attach('c8000000-0000-4000-8000-0000000000ff')$$, 'P0002', null, 'a missing plan is not found');

-- Invariants (each one is rejected with 22023).
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(pg_temp.sessions(), '{0,exercises,1,rest_seconds}', '59'))$$,
  '22023', null, 'isolation rest below 60 s is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(pg_temp.sessions(), '{0,exercises,0,rest_seconds}', '181'))$$,
  '22023', null, 'main rest above 180 s is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(pg_temp.sessions(), '{0,exercises,0,sets}', '11'))$$,
  '22023', null, 'more than 10 sets is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(jsonb_set(pg_temp.sessions(), '{0,exercises,0,rep_min}', '12'), '{0,exercises,0,rep_max}', '8'))$$,
  '22023', null, 'a reversed rep range is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(pg_temp.sessions(), '{0,exercises,0,rep_max}', '51'))$$,
  '22023', null, 'a rep range above 50 is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(jsonb_set(pg_temp.sessions(), '{0,exercises,0,role}', '"isolation"'), '{0,exercises,1,role}', '"main"'))$$,
  '22023', null, 'a main lift after isolation work is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1', pg_temp.sessions(), pg_temp.training('["dumbbell"]'))$$,
  '22023', null, 'equipment outside the training equipment is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1', pg_temp.sessions(), pg_temp.training('[]', 3))$$,
  '22023', null, 'a session count different from days_per_week is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(pg_temp.sessions(), '{0,exercises,0,exercise_id}', '"c8000000-0000-4000-8000-0000000000e2"'))$$,
  '22023', null, 'another user''s custom exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1',
      jsonb_set(pg_temp.sessions(), '{0,exercises,0,reason}', '"corto"'))$$,
  '22023', null, 'a reason shorter than 10 characters is rejected');
select is((select count(*)::int from public.coach_plan_sessions), 8, 'rejected proposals leave the stored sessions untouched');

-- ---------------------------------------------------------------------------
-- save_coach_block
-- ---------------------------------------------------------------------------
select is(jsonb_array_length(public.save_coach_block('c8000000-0000-4000-8000-0000000000a1')->'routine_ids'), 2,
  'saving the block returns two routines');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1)), 2, 'two routines exist after saving');
select is((select count(*)::int from public.routine_folders where user_id = pg_temp.u(1)), 1, 'one folder is created');
select is((select count(*)::int from public.routines r join public.routine_folders f on f.id = r.folder_id
           where r.user_id = pg_temp.u(1)), 2, 'both routines are inside the block folder');
select is((select count(*)::int from public.coach_plan_sessions where routine_id is not null), 8,
  'every week of the sessions links to its routine');
select is((select count(distinct routine_id)::int from public.coach_plan_sessions), 2, 'the weeks share the same two routines');
select throws_ok($$select public.coach_link_block('c8000000-0000-4000-8000-0000000000a1', gen_random_uuid(), '{}')$$, '42501', null,
  'coach_link_block refuses a direct call');
select ok((select folder_id is not null from public.coach_plans where id = 'c8000000-0000-4000-8000-0000000000a1'),
  'the plan links to the folder');
select is((select re.rest_seconds::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Tren superior' and re.position = 0), 150, 'the main rest is written to the routine');
select is((select re.rep_min::int || '-' || re.rep_max::int from public.routine_exercises re
           join public.routines r on r.id = re.routine_id where r.name = 'Tren superior' and re.position = 1),
  '10-15', 'the rep range is written to the routine exercise');
select is((select re.target_sets::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Tren superior' and re.position = 0), 4, 'the sets are written and the order is kept');
select ok((select re.notes is not null and re.note_kind = 'other' from public.routine_exercises re
           join public.routines r on r.id = re.routine_id where r.name = 'Tren superior' and re.position = 0),
  'the reason is kept as the exercise note');

select is(public.save_coach_block('c8000000-0000-4000-8000-0000000000a1')->'routine_ids',
  (select jsonb_agg(routine_id order by session_index) from public.coach_plan_sessions where week = 1),
  'a second save returns the same routines');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1)), 2, 'a second save creates no extra routine');
select throws_ok($$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a1')$$, '55000', null,
  'a saved block cannot be replaced');
select throws_ok($$select public.save_coach_block('c8000000-0000-4000-8000-0000000000a2')$$, 'P0002', null,
  'another user cannot save the block');

-- ---------------------------------------------------------------------------
-- Isolation between users, name clashes, closed plans
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is((select count(*)::int from public.coach_plan_sessions), 0, 'user B sees none of user A''s sessions');
select throws_ok($$select public.save_coach_block('c8000000-0000-4000-8000-0000000000a2')$$, '55000', null,
  'a plan without a block cannot be saved');

select pg_temp.as_user(3);
select throws_ok($$select pg_temp.attach('c8000000-0000-4000-8000-0000000000a3')$$, '55000', null,
  'a closed plan cannot take a block');

select pg_temp.as_user(4);
select pg_temp.attach('c8000000-0000-4000-8000-0000000000a4');
select public.save_coach_block('c8000000-0000-4000-8000-0000000000a4');
select ok(exists (select 1 from public.routines where user_id = pg_temp.u(4) and name = 'Tren superior 2'),
  'a taken routine name gets a number instead of failing');

reset role;
select is((select count(*)::int from public.routines r join public.coach_plan_sessions s on s.routine_id = r.id
           where s.plan_id = 'c8000000-0000-4000-8000-0000000000a1' and s.week = 1), 2, 'routines are reachable from the sessions (as owner)');
-- Deleting the plan removes the sessions and keeps the routines.
delete from public.coach_plans where id = 'c8000000-0000-4000-8000-0000000000a1';
select is((select count(*)::int from public.coach_plan_sessions where plan_id = 'c8000000-0000-4000-8000-0000000000a1'), 0,
  'deleting the plan removes its sessions');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1)), 2, 'deleting the plan keeps the routines');

select * from finish();
rollback;
