-- pgTAP: remove_coach_block (T-203, US-94, ADR-94).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(24);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('cb000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- A saved block: folder, 2 routines, 2 sessions x 4 weeks, nutrition, training.
create function pg_temp.mkplan(u int, p_status text) returns uuid
language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); v_folder uuid; v_r uuid; i int; w int;
begin
  insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque') returning id into v_folder;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, training, status, folder_id)
  values (v_plan, pg_temp.u(u), pg_temp.today() - 14, 'muscle', '{"kcal":2500,"activity":{"cardio_min_week":60}}',
          '{"block_weeks":4}', p_status, v_folder);
  for i in 1..2 loop
    insert into public.routines (user_id, name) values (pg_temp.u(u), 'R' || u || '-' || i) returning id into v_r;
    for w in 1..4 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
      values (v_plan, pg_temp.u(u), w, i, 'Sesion ' || i, '[]', v_r);
    end loop;
  end loop;
  return v_plan;
end $$;
create function pg_temp.rid(u int, i int) returns uuid language sql as $$
  select id from public.routines where user_id = pg_temp.u(u) and name = 'R' || u || '-' || i $$;
create function pg_temp.workout(u int, i int, p_day date) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid(); s timestamptz := (p_day + make_interval(hours => 10)) at time zone 'utc';
begin
  insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), pg_temp.rid(u, i), 'W', 'completed', s, s + interval '1 hour');
  return v;
end $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'rb' || n || '@example.com' from generate_series(1, 5) n;
create temp table plans (u int, id uuid);
grant select on plans to authenticated;
insert into plans values
  (1, pg_temp.mkplan(1, 'accepted')),
  (2, pg_temp.mkplan(2, 'proposed')),
  (3, pg_temp.mkplan(3, 'accepted')),
  (4, pg_temp.mkplan(4, 'accepted'));
-- User 5: a plan without a block.
insert into plans values (5, gen_random_uuid());
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status)
  select id, pg_temp.u(5), pg_temp.today(), 'muscle', '{}', 'accepted' from plans where u = 5;
-- User 1: a finished workout of a former block routine, a pending deload suggestion, a decided block recommendation.
select pg_temp.workout(1, 1, pg_temp.today() - 10);
insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary)
  select pg_temp.u(1), id, 'deload', '{"week":4}', 'no_e1rm_improvement', '{}' from plans where u = 1;

select is((select relrowsecurity from pg_class where oid = 'public.coach_plan_sessions'::regclass), true,
  'coach_plan_sessions keeps RLS enabled');

-- anon cannot execute
set local role anon;
select throws_ok($$select public.remove_coach_block(gen_random_uuid())$$, '42501', null, 'anon cannot execute remove_coach_block');
reset role;

set local role authenticated;

-- ---------------------------------------------------------------------------
-- User 1 removes the block of an accepted plan
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select lives_ok($$select public.remove_coach_block((select id from plans where u = 1))$$, 'the owner removes the block');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 1)), 0,
  'the sessions of all weeks are deleted');
select is((select folder_id is null and training is null from public.coach_plans where id = (select id from plans where u = 1)), true,
  'folder_id and training are cleared');
select is((select nutrition->>'kcal' || '/' || (nutrition->'activity'->>'cardio_min_week') || '/' || intention || '/' || status
           || '/' || block_start from public.coach_plans where id = (select id from plans where u = 1)),
  '2500/60/muscle/accepted/' || (pg_temp.today() - 14), 'nutrition, activity, intention, status and block_start are kept');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1) and name like 'R1-%'), 2, 'the routines stay');
select is((select count(*)::int from public.routine_folders where user_id = pg_temp.u(1)), 1, 'the folder stays');
select is((select count(*)::int from public.workout_sessions where user_id = pg_temp.u(1)), 1, 'the workouts stay');
select is((select status from public.coach_recommendations where user_id = pg_temp.u(1) and kind = 'deload'), 'discarded',
  'the pending deload suggestion is discarded');
select is(public.evaluate_coach_deload(pg_temp.today())->>'reason', 'no_block', 'no deload is evaluated without a block');
select is(public.coach_plan_weeks((select id from plans where u = 1), pg_temp.today()), '[]'::jsonb, 'the plan has no weeks');

-- A new workout of a former block routine links to no plan week.
reset role;
select pg_temp.workout(1, 2, pg_temp.today() - 3);
select is((select count(*)::int from public.coach_plan_sessions where user_id = pg_temp.u(1) and session_id is not null), 0,
  'a workout of a former block routine links to nothing');
set local role authenticated;
select pg_temp.as_user(1);

-- Idempotent
select lives_ok($$select public.remove_coach_block((select id from plans where u = 1))$$, 'a second removal is a no-op');
-- A new block can be saved on the same plan (the "already has a block" refusal is cleared).
select lives_ok($$select public.attach_coach_block((select id from plans where u = 1),
    '{"days_per_week":1,"days_chosen":false,"session_minutes":60,"rep_scheme":"single","block_weeks":2,"equipment":[]}'::jsonb,
    (select jsonb_build_array(jsonb_build_object('session_index', 1, 'name', 'Nueva', 'focus', jsonb_build_array('chest'),
       'exercises', jsonb_build_array(jsonb_build_object('exercise_id', e.id, 'exercise_key', e.key, 'role', 'main', 'sets', 3,
         'rep_min', 6, 'rep_max', 10, 'rest_seconds', 120, 'reason', 'Buen ejercicio base para tu objetivo.'))))
       from public.exercises e where not e.is_custom and e.metric_type = 'weight_reps' order by e.key limit 1),
    '{"model":"m","attempts":1}', '{}')$$, 'a new block is attached after the removal');
select lives_ok($$select public.save_coach_block((select id from plans where u = 1))$$, 'the new block is saved');
select is(public.coach_plan_weeks((select id from plans where u = 1), pg_temp.today())->0->>'start_date', pg_temp.today()::text,
  'the first week of the new block starts the day it is saved');
select is((select block_start from public.coach_plans where id = (select id from plans where u = 1)), pg_temp.today() - 14,
  'block_start (nutrition phase anchor) is not shifted');
select pg_temp.as_user(3);
-- T-242: a window without a workout is a paused week; two routine-less workouts keep windows 0 and 1 as weeks 1 and 2.
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
select pg_temp.u(3), 'Filler', 'completed', (pg_temp.today() - 14 + d)::timestamp at time zone 'utc' + interval '10 hours',
       (pg_temp.today() - 14 + d)::timestamp at time zone 'utc' + interval '11 hours'
from unnest(array[1, 8]) d;
select is(public.coach_plan_weeks((select id from plans where u = 3), pg_temp.today())->0->>'start_date', (pg_temp.today() - 14)::text,
  'a plan whose block was never removed keeps its weeks at block_start');
select pg_temp.as_user(1);

-- ---------------------------------------------------------------------------
-- A proposed plan, a plan without block, a closed plan
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select lives_ok($$select public.remove_coach_block((select id from plans where u = 2))$$, 'a proposed plan with a saved block can be cleared');
select pg_temp.as_user(5);
select lives_ok($$select public.remove_coach_block((select id from plans where u = 5))$$, 'a plan without a block is a no-op');
reset role;
update public.coach_plans set status = 'superseded' where id = (select id from plans where u = 4);
set local role authenticated;
select pg_temp.as_user(4);
select throws_ok($$select public.remove_coach_block((select id from plans where u = 4))$$, '55000', 'plan is not open', 'a closed plan is refused');

-- ---------------------------------------------------------------------------
-- Cross-user
-- ---------------------------------------------------------------------------
select pg_temp.as_user(3);
select throws_ok($$select public.remove_coach_block((select id from plans where u = 4))$$, '22023', 'plan not found',
  'user B cannot remove the block of user A');
reset role;
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 4)), 8,
  'the block of the other user is untouched');

select * from finish();
rollback;
