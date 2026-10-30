-- pgTAP: evaluate_coach_deload, decide_coach_deload, deload_suggestions (T-165, US-75; rules in planning/04 section 9.5).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(40);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('ca000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.sq() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'squat' order by key limit 1 $$;
create function pg_temp.iso() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'isolation' order by key limit 1 $$;
create function pg_temp.exn(n int) returns uuid language sql as $$
  select id from public.exercises where not is_custom order by key offset n limit 1 $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

-- A plan (6 weeks window irrelevant: 4 weeks) with 2 routines per week; folder only when asked.
create function pg_temp.mkplan(u int, p_status text, p_start date, with_folder boolean) returns uuid
language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); v_folder uuid; v_r uuid; i int; w int;
begin
  if with_folder then
    insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque') returning id into v_folder;
  end if;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id)
  values (v_plan, pg_temp.u(u), p_start, 'muscle', '{}', p_status, v_folder);
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

-- A completed workout of user u on a given day with one set.
create function pg_temp.hist(u int, p_day date, p_ex uuid, p_w numeric, p_reps int, p_type public.set_type default 'working')
returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid(); s timestamptz := pg_temp.at(p_day);
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), 'H', 'completed', s, s + interval '1 hour');
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
  values (v, p_ex, 0, 1, p_type, p_w, p_reps, s + interval '30 minutes');
  return v;
end $$;
-- Squat in each of the three weeks (b = block start): week 1 = b+1, week 2 = b+8, week 3 = b+15.
create function pg_temp.weeks3(u int, p_start date, w1 numeric, w2 numeric, w3 numeric) returns void language plpgsql as $$
begin
  perform pg_temp.hist(u, p_start + 1, pg_temp.sq(), w1, 5);
  perform pg_temp.hist(u, p_start + 8, pg_temp.sq(), w2, 5);
  perform pg_temp.hist(u, p_start + 15, pg_temp.sq(), w3, 5);
end $$;
create function pg_temp.ev(n int) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(n);
  return public.evaluate_coach_deload(pg_temp.today());
end $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'dl' || n || '@example.com' from generate_series(1, 12) n;

create temp table plans (u int, id uuid);
grant select on plans to authenticated;
-- Block started 14 days ago: today is in week 3 of 4.
insert into plans select n, pg_temp.mkplan(n, 'accepted', pg_temp.today() - 14, true) from unnest(array[1, 2, 3, 4, 9, 10]) n;
insert into plans values
  (5, pg_temp.mkplan(5, 'proposed', pg_temp.today() - 14, true)),
  (6, pg_temp.mkplan(6, 'accepted', pg_temp.today() - 14, false)),
  (7, pg_temp.mkplan(7, 'accepted', pg_temp.today() - 7, true)),   -- week 2
  (8, pg_temp.mkplan(8, 'accepted', pg_temp.today() - 21, true));  -- week 4 = block_weeks

-- 1: equal e1RM in all three weeks (a tie is not an improvement), plus an improving isolation exercise (ignored).
select pg_temp.weeks3(1, pg_temp.today() - 14, 100, 100, 100);
select pg_temp.hist(1, pg_temp.today() - 13, pg_temp.iso(), 20, 10);
select pg_temp.hist(1, pg_temp.today() - 6, pg_temp.iso(), 30, 10);
select pg_temp.hist(1, pg_temp.today() + 1, pg_temp.iso(), 40, 10);
-- 2: improved only in week 2 (max of weeks 2 and 3 beats week 1).
select pg_temp.weeks3(2, pg_temp.today() - 14, 100, 105, 100);
-- 3: the main lift is missing in week 2.
select pg_temp.hist(3, pg_temp.today() - 13, pg_temp.sq(), 100, 5);
select pg_temp.hist(3, pg_temp.today() + 1, pg_temp.sq(), 100, 5);
-- 4: only isolation work.
select pg_temp.weeks3(4, pg_temp.today() - 14, 100, 100, 100);
update public.workout_sets set exercise_id = pg_temp.iso()
  where session_id in (select id from public.workout_sessions where user_id = pg_temp.u(4));
-- 5, 6, 7, 8: no improvement but the plan conditions fail.
select pg_temp.weeks3(5, pg_temp.today() - 14, 100, 100, 100);
select pg_temp.weeks3(6, pg_temp.today() - 14, 100, 100, 100);
select pg_temp.weeks3(7, pg_temp.today() - 7, 100, 100, 100);
select pg_temp.weeks3(8, pg_temp.today() - 21, 100, 100, 100);
-- 9: same as 1, to be discarded. 10: improved by a warm-up set only (warm-ups do not count) -> created.
select pg_temp.weeks3(9, pg_temp.today() - 14, 100, 100, 100);
select pg_temp.weeks3(10, pg_temp.today() - 14, 100, 100, 100);
select pg_temp.hist(10, pg_temp.today() - 6, pg_temp.sq(), 200, 5, 'warm_up');

-- ---------------------------------------------------------------------------
-- Structure
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.evaluate_coach_deload(date)', 'execute'), 'anon cannot evaluate');
select ok(not has_function_privilege('anon', 'public.decide_coach_deload(uuid,boolean)', 'execute'), 'anon cannot decide');
select ok(not has_function_privilege('anon', 'public.deload_suggestions(uuid)', 'execute'), 'anon cannot read suggestions');
select throws_ok($$select public.evaluate_coach_deload(current_date)$$, '28000', null, 'evaluate needs a signed-in user');

set local role authenticated;

-- ---------------------------------------------------------------------------
-- evaluate_coach_deload
-- ---------------------------------------------------------------------------
select is(pg_temp.ev(1)->>'status', 'created', 'no improvement (ties) in three weeks creates a deload');
select is((pg_temp.ev(1))->>'week', '4', 'the deload targets the next week');
create temp table rec1 as
  select id from public.coach_recommendations where user_id = pg_temp.u(1) and kind = 'deload';
grant select on rec1 to authenticated;
select is((select count(*)::int from rec1), 1, 'a second evaluation creates no duplicate');
select is(pg_temp.ev(1)->>'status', 'pending', 'a pending deload is returned as pending');
select is((select payload->>'week' from public.coach_recommendations where id = (select id from rec1)), '4', 'payload carries the week');
select is((select jsonb_array_length(payload->'lifts') from public.coach_recommendations where id = (select id from rec1)), 1,
  'the isolation exercise is not a main lift');
select is((select reason from public.coach_recommendations where id = (select id from rec1)), 'no_e1rm_improvement', 'reason code stored');
select is(pg_temp.ev(2)->>'reason', 'improved', 'one main lift that improved (week 2) means no deload');
select is(pg_temp.ev(3)->>'reason', 'no_main_lifts', 'a lift missing in one week is not considered');
select is(pg_temp.ev(4)->>'reason', 'no_main_lifts', 'isolation work alone is not considered');
select is(pg_temp.ev(5)->>'reason', 'no_block', 'a plan that is not accepted gets no deload');
select is(pg_temp.ev(6)->>'reason', 'no_block', 'a plan without a saved block gets no deload');
select is(pg_temp.ev(7)->>'reason', 'too_early', 'before week 3 there is no deload');
select is(pg_temp.ev(8)->>'reason', 'too_early', 'in the last week of the block there is no deload');
select is(pg_temp.ev(10)->>'status', 'created', 'a warm-up set does not count as improvement');
select is(pg_temp.ev(11)->>'reason', 'no_block', 'a user without a plan gets none');

-- ---------------------------------------------------------------------------
-- decide_coach_deload
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select throws_ok($$select public.decide_coach_deload((select id from rec1), true)$$, 'P0002', null,
  'another user cannot decide the recommendation');
select pg_temp.as_user(1);
select is(public.decide_coach_deload((select id from rec1), true), '{"status":"accepted","week":4}'::jsonb, 'accept returns the week');
select is((select jsonb_agg(distinct week order by week) from public.coach_plan_sessions
           where plan_id = (select id from plans where u = 1) and deload), '[4]'::jsonb, 'only the payload week is marked as deload');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 1) and deload), 2,
  'every session of that week is marked');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1)), 2, 'routines are untouched');
select is((select status from public.coach_recommendations where id = (select id from rec1)), 'accepted', 'recommendation accepted');
select throws_ok($$select public.decide_coach_deload((select id from rec1), false)$$, '55000', null, 'a decided recommendation cannot be decided again');
select is(pg_temp.ev(1)->>'reason', 'already_decided', 'after a decision it is not recreated');

select pg_temp.as_user(9);
select pg_temp.ev(9);
select is(public.decide_coach_deload((select id from public.coach_recommendations where user_id = pg_temp.u(9)), false)->>'status',
  'discarded', 'discard returns discarded');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 9) and deload), 0,
  'discard changes no session');
select is(pg_temp.ev(9)->>'reason', 'already_decided', 'after a discard it is not recreated');
select throws_ok($$select public.decide_coach_deload(gen_random_uuid(), true)$$, 'P0002', null, 'unknown recommendation');

-- ---------------------------------------------------------------------------
-- deload_suggestions (user 1: week 4 is the deload week; week 4 = days 22 to 28 of the block)
-- ---------------------------------------------------------------------------
reset role;
-- A workout of routine 1 in week 4 with 1, 2, 3, 4, 5, 6, 8 and 10 working sets (plus a warm-up on the first exercise).
create temp table dl as select gen_random_uuid() as id;
grant select on dl to authenticated;
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
select id, pg_temp.u(1), pg_temp.rid(1, 1), 'Deload', 'completed', pg_temp.at(pg_temp.today() - 14 + 21 + 1), pg_temp.at(pg_temp.today() - 14 + 21 + 1, 11) from dl;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps)
select (select id from dl), pg_temp.exn(p::int), p, i, 'working', 50, 8
from unnest(array[1, 2, 3, 4, 5, 6, 8, 10]) with ordinality t(n, p), generate_series(1, 10) i where i <= n;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps)
values ((select id from dl), pg_temp.exn(0), 0, 11, 'warm_up', 20, 10);
-- Same routine, week 3 (not a deload week), and another routine in week 4.
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at) values
  ('ca000000-0000-4000-8000-0000000000d3', pg_temp.u(1), pg_temp.rid(1, 1), 'Wk3', 'completed', pg_temp.at(pg_temp.today() - 14 + 15), pg_temp.at(pg_temp.today() - 14 + 15, 11)),
  ('ca000000-0000-4000-8000-0000000000d4', pg_temp.u(1), pg_temp.rid(1, 2), 'Other', 'completed', pg_temp.at(pg_temp.today() - 14 + 22), pg_temp.at(pg_temp.today() - 14 + 22, 11));
-- The other routine's session is in the deload week, so it also qualifies; give it one set to assert the line.
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps)
values ('ca000000-0000-4000-8000-0000000000d4', pg_temp.exn(0), 0, 1, 'working', 50, 8);
-- A workout with no routine.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
values ('ca000000-0000-4000-8000-0000000000d5', pg_temp.u(1), 'Free', 'completed', pg_temp.at(pg_temp.today() - 14 + 22), pg_temp.at(pg_temp.today() - 14 + 22, 11));

set local role authenticated;
select pg_temp.as_user(1);
select is((public.deload_suggestions((select id from dl)))->>'week', '4', 'a session of a deload routine in that week returns the week');
select is((select jsonb_agg((e->>'deload_sets')::int order by (e->>'planned_sets')::int)
           from jsonb_array_elements(public.deload_suggestions((select id from dl))->'exercises') e),
  '[1,1,2,2,3,4,5,6]'::jsonb, '1, 2, 3, 4, 5, 6, 8 and 10 working sets become 1, 1, 2, 2, 3, 4, 5, 6');
select is((select jsonb_agg((e->>'planned_sets')::int order by (e->>'planned_sets')::int)
           from jsonb_array_elements(public.deload_suggestions((select id from dl))->'exercises') e),
  '[1,2,3,4,5,6,8,10]'::jsonb, 'warm-up sets are not counted as planned sets');
select is(public.deload_suggestions('ca000000-0000-4000-8000-0000000000d3'), '[]'::jsonb, 'outside the deload week there is nothing');
select is(public.deload_suggestions('ca000000-0000-4000-8000-0000000000d5'), '[]'::jsonb, 'a session without routine has nothing');
select is(public.deload_suggestions(gen_random_uuid()), '[]'::jsonb, 'an unknown session has nothing');
select pg_temp.as_user(2);
select is(public.deload_suggestions((select id from dl)), '[]'::jsonb, 'a foreign session has nothing');
select is((select count(*)::int from public.coach_recommendations where user_id <> pg_temp.u(2)), 0, 'user B sees none of the recommendations of others');

select * from finish();
rollback;
