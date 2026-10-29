-- pgTAP: rep range per routine exercise and progression_suggestions (T-159, US-70; rules in planning/04 section 4.14).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(43);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('d5900000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.as_user(n int) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
end $$;

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises where not is_custom limit 20;
create function pg_temp.x(n int) returns uuid language sql as $$ select id from cat where cat.n = $1 $$;

-- A completed session of user u started `days` days ago, with one working-type row per entry of `reps`.
create function pg_temp.hist(u int, ex int, days int, rmin int, rmax int, w numeric, reps int[],
                             rpes numeric[] default null) returns void language plpgsql as $$
declare
  v_sid uuid := gen_random_uuid();
  v_start timestamptz := now() - make_interval(days => days);
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v_sid, pg_temp.u(u), 'Hist', 'completed', v_start, v_start + interval '1 hour');
  insert into public.workout_sets
    (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe, completed_at,
     rep_min, rep_max)
  select v_sid, pg_temp.x(ex), 0, i, 'working', w, reps[i], case when rpes is null then null else rpes[i] end,
         v_start + interval '30 minutes', rmin, rmax
  from generate_subscripts(reps, 1) i;
end $$;

-- The suggestion of exercise n in user 1's current session, as user u.
create function pg_temp.sg(u int, n int) returns jsonb language plpgsql as $$
declare v jsonb;
begin
  perform pg_temp.as_user(u);
  select e into v
  from jsonb_array_elements(public.progression_suggestions('d5a00000-0000-4000-8000-000000000001')) e
  where (e ->> 'exercise_id')::uuid = pg_temp.x(n);
  return v;
end $$;

insert into auth.users (id, email)
select pg_temp.u(n), 'prog' || n || '@example.com' from generate_series(1, 3) n;

-- Security
select function_privs_are('public', 'progression_suggestions', array['uuid'], 'anon', array[]::text[],
  'anon cannot execute progression_suggestions');
select function_privs_are('public', 'progression_suggestions', array['uuid'], 'authenticated',
  array['EXECUTE'], 'authenticated can execute progression_suggestions');

-- Constraints
insert into public.routines (id, user_id, name)
values ('d5b00000-0000-4000-8000-000000000001', pg_temp.u(1), 'Check');
select throws_ok($$insert into public.routine_exercises
  (routine_id, exercise_id, position, target_sets, target_reps, rep_min, rep_max)
  values ('d5b00000-0000-4000-8000-000000000001', pg_temp.x(1), 0, 3, 8, 9, 6)$$,
  '23514', null, 'routine exercise range with min above max is rejected');
select throws_ok($$insert into public.routine_exercises
  (routine_id, exercise_id, position, target_sets, target_reps, rep_min)
  values ('d5b00000-0000-4000-8000-000000000001', pg_temp.x(1), 0, 3, 8, 6)$$,
  '23514', null, 'routine exercise range needs both ends or neither');
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
values ('d5b00000-0000-4000-8000-000000000002', pg_temp.u(1), 'C', 'completed', now() - interval '9 days',
        now() - interval '9 days' + interval '1 hour');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rep_min, rep_max)
  values ('d5b00000-0000-4000-8000-000000000002', pg_temp.x(1), 0, 1, 1, 51)$$,
  '23514', null, 'set snapshot range above 50 is rejected');

-- Range persistence (user 3)
select pg_temp.as_user(3);
create temp table saved as
  select public.save_routine(jsonb_build_object('name', 'Con rango', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', pg_temp.x(1), 'rep_min', 5, 'rep_max', 7,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 50, 'reps', 7), jsonb_build_object('weight_kg', 50, 'reps', 7))),
    jsonb_build_object('exercise_id', pg_temp.x(2),
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 30, 'reps', 10)))))) as id;

select results_eq(
  $$select rep_min::int, rep_max::int from public.routine_exercises
    where routine_id = (select id from saved) and position = 0$$,
  $$values (5, 7)$$, 'save_routine stores the rep range');
select results_eq(
  $$select rep_min::int, rep_max::int from public.routine_exercises
    where routine_id = (select id from saved) and position = 1$$,
  $$values (null::int, null::int)$$, 'save_routine without keys stores no range');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'X', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', pg_temp.x(1), 'rep_min', 6,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 50, 'reps', 7))))))$$,
  '22023', 'invalid rep range', 'save_routine rejects a range with only one end');
select throws_ok($$select public.save_routine(jsonb_build_object('name', 'X', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', pg_temp.x(1), 'rep_min', 9, 'rep_max', 6,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 50, 'reps', 7))))))$$,
  '22023', 'invalid rep range', 'save_routine rejects min above max');

create temp table started as select public.start_workout_session((select id from saved)) as id;
select is((select count(*)::int from public.workout_sets
           where session_id = (select id from started) and rep_min = 5 and rep_max = 7), 2,
  'start_workout_session copies the range to the sets of that exercise');

update public.workout_sets set weight_kg = 50, reps = 7, completed_at = now()
where session_id = (select id from started);
update public.workout_sessions set status = 'completed', ended_at = now() where id = (select id from started);
select public.update_routine_from_session((select id from started));
select results_eq(
  $$select rep_min::int, rep_max::int from public.routine_exercises
    where routine_id = (select id from saved) and exercise_id = pg_temp.x(1)$$,
  $$values (5, 7)$$, 'update_routine_from_session keeps the rep range');

-- ---------------------------------------------------------------------------
-- Progression fixtures. User 1 has an in-progress session with a range snapshot per exercise.
-- ---------------------------------------------------------------------------
insert into public.workout_sessions (id, user_id, name, status, started_at)
values ('d5a00000-0000-4000-8000-000000000001', pg_temp.u(1), 'Current', 'in_progress', now());
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rep_min, rep_max)
select 'd5a00000-0000-4000-8000-000000000001', pg_temp.x(n), n, 1,
       case when n = 9 then null when n = 7 then 8 else 6 end,
       case when n = 9 then null when n = 7 then 10 else 8 end
from generate_series(1, 18) n;

-- 1: all sets at the max, RPE <= 8 -> raise
select pg_temp.hist(1, 1, 3, 6, 8, 80, array[8, 8, 8], array[8, 8, 7]);
-- 2: 6, 6, 7 -> one more rep at the same weight
select pg_temp.hist(1, 2, 3, 6, 8, 80, array[6, 6, 7]);
-- 3: two sessions, most sets below the minimum -> lower
select pg_temp.hist(1, 3, 2, 6, 8, 80, array[5, 5, 6]);
select pg_temp.hist(1, 3, 4, 6, 8, 80, array[5, 5, 6]);
-- 4: only one session below the minimum -> keep trying
select pg_temp.hist(1, 4, 2, 6, 8, 80, array[5, 5, 5]);
-- 5: no RPE logged, reps at the max -> raise on reps alone
select pg_temp.hist(1, 5, 2, 6, 8, 80, array[8, 8, 8]);
-- 6: one set at RPE 9 blocks the raise
select pg_temp.hist(1, 6, 2, 6, 8, 80, array[8, 8, 8], array[8, 9, 8]);
-- 7: ranges 8-10 (older) and 3-5 (newer): only the 8-10 session matches
select pg_temp.hist(1, 7, 3, 8, 10, 80, array[10, 10, 10]);
select pg_temp.hist(1, 7, 1, 3, 5, 100, array[5, 5, 5]);
-- 8: history only under another range
select pg_temp.hist(1, 8, 2, 3, 5, 100, array[5, 5, 5]);
-- 9: history but the current session has no range
select pg_temp.hist(1, 9, 2, 6, 8, 80, array[8, 8, 8]);
-- 10: no history for user 1; user 2 has history and a current session of their own
select pg_temp.hist(2, 10, 2, 6, 8, 60, array[8, 8, 8]);
insert into public.workout_sessions (id, user_id, name, status, started_at)
values ('d5a00000-0000-4000-8000-000000000002', pg_temp.u(2), 'Current 2', 'in_progress', now());
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rep_min, rep_max)
values ('d5a00000-0000-4000-8000-000000000002', pg_temp.x(10), 0, 1, 6, 8);
-- 11-17: discomfort cases on top of a raise-worthy history
select pg_temp.hist(1, n, 2, 6, 8, 80, array[8, 8, 8]) from generate_series(11, 17) n;
insert into public.exercise_discomforts (user_id, exercise_id, logged_at, level, resolved_at) values
  (pg_temp.u(1), pg_temp.x(11), now() - interval '1 day', 1, null),
  (pg_temp.u(1), pg_temp.x(12), now() - interval '1 day', 2, null),
  (pg_temp.u(1), pg_temp.x(13), now() - interval '1 day', 3, null),
  (pg_temp.u(1), pg_temp.x(14), now() - interval '1 day', 4, null),
  (pg_temp.u(1), pg_temp.x(15), now() - interval '1 day', 5, null),
  (pg_temp.u(1), pg_temp.x(16), now() - interval '8 days', 3, null),
  (pg_temp.u(1), pg_temp.x(17), now() - interval '1 day', 3, now() - interval '1 hour');
-- 18: warm-ups are ignored (a heavy, low-rep warm-up must not change the top weight or the reps)
select pg_temp.hist(1, 18, 2, 6, 8, 80, array[8, 8, 8]);
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at, rep_min, rep_max)
select session_id, exercise_id, 0, 10, 'warm_up', 100, 2, completed_at, 6, 8
from public.workout_sets where exercise_id = pg_temp.x(18) and set_index = 1
  and session_id in (select id from public.workout_sessions where user_id = pg_temp.u(1) and status = 'completed');

-- Exercise 1
select is(pg_temp.sg(1, 1) ->> 'action', 'raise_weight', 'all sets at the max with RPE <= 8 raises the weight');
select is((pg_temp.sg(1, 1) ->> 'base_weight_kg')::numeric, 80::numeric, 'the base weight is the top working weight');
select is((pg_temp.sg(1, 1) ->> 'goal_reps')::int, 6, 'a raise restarts at the range minimum');
select is((pg_temp.sg(1, 1) ->> 'used_rpe')::boolean, true, 'RPE was used when sets carry it');
select is(jsonb_array_length(pg_temp.sg(1, 1) -> 'sets_used'), 3, 'the sets used are listed');
-- Exercise 2
select is(pg_temp.sg(1, 2) ->> 'action', 'add_rep', '80 x 6,6,7 suggests one more rep');
select is((pg_temp.sg(1, 2) ->> 'base_weight_kg')::numeric, 80::numeric, 'adding a rep keeps the weight');
select is((pg_temp.sg(1, 2) ->> 'goal_reps')::int, 8, 'the goal is the best reps plus one');
-- Exercise 3
select is(pg_temp.sg(1, 3) ->> 'action', 'lower_weight', 'two sessions mostly below the minimum lower the weight');
select is(jsonb_array_length(pg_temp.sg(1, 3) -> 'sets_used'), 6, 'both sessions are listed when lowering');
select is((pg_temp.sg(1, 3) ->> 'base_weight_kg')::numeric, 80::numeric, 'lowering starts from the top working weight');
-- Exercise 4
select is(pg_temp.sg(1, 4) ->> 'action', 'add_rep', 'one session below the minimum does not lower yet');
-- Exercise 5
select is(pg_temp.sg(1, 5) ->> 'action', 'raise_weight', 'without RPE the reps rule alone raises');
select is((pg_temp.sg(1, 5) ->> 'used_rpe')::boolean, false, 'used_rpe is false without RPE');
-- Exercise 6
select is(pg_temp.sg(1, 6) ->> 'action', 'add_rep', 'a set at RPE 9 blocks the raise');
-- Exercise 7
select is(pg_temp.sg(1, 7) ->> 'action', 'raise_weight', 'only the session with the same range counts');
select is((pg_temp.sg(1, 7) ->> 'base_weight_kg')::numeric, 80::numeric, 'the matching session sets the weight');
-- Exercises 8, 9, 10
select is(pg_temp.sg(1, 8), null::jsonb, 'history under another range gives no suggestion');
select is(pg_temp.sg(1, 9), null::jsonb, 'no range in the session gives no suggestion');
select is(pg_temp.sg(1, 10), null::jsonb, 'no own history gives no suggestion');
-- Discomfort
select is(pg_temp.sg(1, 11) ->> 'reason', 'kept_discomfort', 'open level 1 keeps the weight');
select is(pg_temp.sg(1, 12) ->> 'reason', 'kept_discomfort', 'open level 2 keeps the weight');
select is(pg_temp.sg(1, 13) ->> 'reason', 'kept_discomfort', 'open level 3 keeps the weight');
select is((pg_temp.sg(1, 11) ->> 'goal_reps')::int, 8, 'a kept suggestion aims at the range maximum');
select is(pg_temp.sg(1, 14), null::jsonb, 'open level 4 blocks the exercise');
select is(pg_temp.sg(1, 15), null::jsonb, 'open level 5 blocks the exercise');
select is(pg_temp.sg(1, 16) ->> 'action', 'raise_weight', 'an expired level 3 no longer blocks the raise');
select is(pg_temp.sg(1, 17) ->> 'action', 'raise_weight', 'a resolved level 3 no longer blocks the raise');
-- Warm-ups
select is(pg_temp.sg(1, 18) ->> 'action', 'raise_weight', 'warm-up sets are ignored by the rule');
select is((pg_temp.sg(1, 18) ->> 'base_weight_kg')::numeric, 80::numeric, 'warm-up weight is not the base weight');
-- Isolation
select pg_temp.as_user(2);
select is(public.progression_suggestions('d5a00000-0000-4000-8000-000000000001'), '[]'::jsonb,
  'another user''s session id returns an empty array');
select is(public.progression_suggestions('d5a00000-0000-4000-8000-000000000002') -> 0 ->> 'action', 'raise_weight',
  'a user gets suggestions from their own history only');

select * from finish();
rollback;
