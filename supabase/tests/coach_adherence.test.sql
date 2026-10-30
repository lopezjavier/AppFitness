-- pgTAP: plan-session linking trigger and coach_plan_weeks (T-165, US-73; rules in planning/04 section 9.4).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(35);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
-- Block start used by most tests: today is day 9 of the block (week 2).
create function pg_temp.b() returns date language sql as $$ select pg_temp.today() - 8 $$;
create function pg_temp.rid(u int, i int) returns uuid language sql as $$
  select id from public.routines where user_id = pg_temp.u(u) and name = 'R' || u || '-' || i $$;
create function pg_temp.ex() returns uuid language sql as $$
  select id from public.exercises where not is_custom order by key limit 1 $$;
-- A plan with n sessions per week and 4 weeks, one routine per session index.
create function pg_temp.mkplan(u int, p_status text, p_start date, n int, with_folder boolean) returns uuid
language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); v_folder uuid; v_r uuid; i int; w int;
begin
  if with_folder then
    insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque ' || u) returning id into v_folder;
  end if;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id)
  values (v_plan, pg_temp.u(u), p_start, 'muscle', '{}', p_status, v_folder);
  for i in 1..n loop
    insert into public.routines (user_id, name) values (pg_temp.u(u), 'R' || u || '-' || i) returning id into v_r;
    for w in 1..4 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
      values (v_plan, pg_temp.u(u), w, i, 'Sesion ' || i, '[]', v_r);
    end loop;
  end loop;
  return v_plan;
end $$;
-- A completed workout of user u (optionally of a routine) started at a given instant.
create function pg_temp.wk(u int, p_routine uuid, p_start timestamptz) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), p_routine, 'W', 'completed', p_start, p_start + interval '1 hour');
  return v;
end $$;
create function pg_temp.linked(p_plan uuid, p_week int, p_idx int) returns uuid language sql as $$
  select session_id from public.coach_plan_sessions where plan_id = p_plan and week = p_week and session_index = p_idx $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'adh' || n || '@example.com' from generate_series(1, 7) n;

-- User 1: accepted plan, 2 sessions per week. User 2: proposed plan without a saved block. User 3: no plan.
create temp table plans (u int, id uuid);
grant select on plans to authenticated;
insert into plans values
  (1, pg_temp.mkplan(1, 'accepted', pg_temp.b(), 2, true)),
  (2, pg_temp.mkplan(2, 'proposed', pg_temp.b(), 2, false)),
  (4, pg_temp.mkplan(4, 'accepted', pg_temp.b(), 3, true)),
  (5, pg_temp.mkplan(5, 'accepted', pg_temp.b(), 4, true)),
  (6, pg_temp.mkplan(6, 'proposed', pg_temp.b(), 1, true));
insert into public.routines (user_id, name) values (pg_temp.u(3), 'R3-1');

-- ---------------------------------------------------------------------------
-- Structure
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('authenticated', 'public.coach_link_plan_session()', 'execute'),
  'the trigger function is not executable by authenticated');
select ok(not has_function_privilege('anon', 'public.coach_plan_weeks(uuid,date)', 'execute'), 'anon cannot read plan weeks');
select ok(has_function_privilege('authenticated', 'public.coach_plan_weeks(uuid,date)', 'execute'), 'authenticated can read plan weeks');

-- ---------------------------------------------------------------------------
-- Linking through the two finish paths
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);

-- Manual log (save_completed_workout inserts a completed workout) in week 1.
create temp table w1 as select public.save_completed_workout(jsonb_build_object(
  'name', 'Manual', 'routine_id', pg_temp.rid(1, 1), 'started_at', pg_temp.at(pg_temp.b() + 1),
  'duration_s', 3600,
  'exercises', jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.ex(),
    'sets', jsonb_build_array(jsonb_build_object('weight_kg', 50, 'reps', 8)))))) as id;
grant select on w1 to authenticated;
select is(pg_temp.linked((select id from plans where u = 1), 1, 1), (select id from w1),
  'a manual log of a planned routine links its session in that week');
select is(pg_temp.linked((select id from plans where u = 1), 1, 2), null, 'the other session of the week stays unlinked');

-- Live path: an in-progress workout finished with finish_workout_session.
insert into public.workout_sessions (id, routine_id, name, status, started_at)
values ('c9000000-0000-4000-8000-0000000000f1', pg_temp.rid(1, 2), 'Live', 'in_progress', pg_temp.at(pg_temp.b() + 3));
select is(pg_temp.linked((select id from plans where u = 1), 1, 2), null, 'an unfinished workout links nothing');
select public.finish_workout_session('c9000000-0000-4000-8000-0000000000f1');
select is(pg_temp.linked((select id from plans where u = 1), 1, 2), 'c9000000-0000-4000-8000-0000000000f1'::uuid,
  'finishing a live workout links its session in that week');

-- A second workout of the same routine in the same week does not overwrite the link.
select public.save_completed_workout(jsonb_build_object(
  'name', 'Again', 'routine_id', pg_temp.rid(1, 1), 'started_at', pg_temp.at(pg_temp.b() + 4), 'duration_s', 1800,
  'exercises', jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.ex(),
    'sets', jsonb_build_array(jsonb_build_object('weight_kg', 50, 'reps', 8))))));
select is(pg_temp.linked((select id from plans where u = 1), 1, 1), (select id from w1),
  'a second workout of the same routine in the week does not overwrite the link');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 1) and session_id is not null),
  2, 'the second workout links no other session');

-- Editing a completed workout does not relink anything.
select public.save_completed_workout(jsonb_build_object(
  'id', (select id from w1), 'name', 'Edited',
  'exercises', jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.ex(),
    'sets', jsonb_build_array(jsonb_build_object('weight_kg', 60, 'reps', 5))))));
select is(pg_temp.linked((select id from plans where u = 1), 1, 1), (select id from w1), 'editing keeps the link');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 1) and session_id is not null),
  2, 'editing links nothing new');

-- A workout without routine, before the block start, or in a later week with no free row links nothing.
reset role;
select pg_temp.wk(1, null, pg_temp.at(pg_temp.b() + 5));
select pg_temp.wk(1, pg_temp.rid(1, 1), pg_temp.at(pg_temp.b() - 1));
select pg_temp.wk(1, pg_temp.rid(1, 1), pg_temp.at(pg_temp.b() + 28));
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 1) and session_id is not null),
  2, 'no routine, before the block and after week 4 link nothing');

-- Boundary days: day 7 (b + 6) belongs to week 1, day 8 (b + 7) to week 2.
select pg_temp.wk(4, pg_temp.rid(4, 1), pg_temp.at(pg_temp.b() + 6, 23));
select ok(pg_temp.linked((select id from plans where u = 4), 1, 1) is not null, 'day 7 of the block links to week 1');
select pg_temp.wk(4, pg_temp.rid(4, 2), pg_temp.at(pg_temp.b() + 7, 0));
select ok(pg_temp.linked((select id from plans where u = 4), 2, 2) is not null
          and pg_temp.linked((select id from plans where u = 4), 1, 2) is null, 'day 8 of the block links to week 2');

-- Another routine of the user, a proposed plan without a saved block, and another user's workout.
select pg_temp.wk(2, pg_temp.rid(2, 1), pg_temp.at(pg_temp.b() + 1));
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 2) and session_id is not null),
  0, 'a proposed plan without a saved block links nothing');
select pg_temp.wk(6, pg_temp.rid(6, 1), pg_temp.at(pg_temp.b() + 1));
select ok(pg_temp.linked((select id from plans where u = 6), 1, 1) is not null, 'a proposed plan with a saved block links');
-- User 3 logs a workout with user 1's routine (superuser insert): the trigger filters by user.
select pg_temp.wk(3, pg_temp.rid(1, 2), pg_temp.at(pg_temp.b() + 9));
select is(pg_temp.linked((select id from plans where u = 1), 2, 2), null, 'a workout of user B never links a session of user A');

-- Deleting the workout nulls session_id and keeps the row.
delete from public.workout_sessions where id = (select id from w1);
select is(pg_temp.linked((select id from plans where u = 1), 1, 1), null, 'deleting the workout unlinks the session');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = (select id from plans where u = 1) and week = 1), 2,
  'the plan session row survives');

-- ---------------------------------------------------------------------------
-- coach_plan_weeks
-- ---------------------------------------------------------------------------
-- User 5: 4 sessions per week, week 1 with 3 done (75), week 2 with 4 done (100), week 3 none (0).
update public.coach_plan_sessions set session_id = pg_temp.wk(5, null, pg_temp.at(pg_temp.b()))
  where plan_id = (select id from plans where u = 5) and week = 1 and session_index <= 3;
update public.coach_plan_sessions set session_id = pg_temp.wk(5, null, pg_temp.at(pg_temp.b()))
  where plan_id = (select id from plans where u = 5) and week = 2;
-- User 4: 3 sessions per week; week 1 has 1 done (33) from the boundary test, week 2 has 1 done (33).
update public.coach_plan_sessions set session_id = pg_temp.wk(4, null, pg_temp.at(pg_temp.b()))
  where plan_id = (select id from plans where u = 4) and week = 2 and session_index = 1;
update public.coach_plan_sessions set session_id = pg_temp.wk(4, null, pg_temp.at(pg_temp.b()))
  where plan_id = (select id from plans where u = 4) and week = 3 and session_index <= 2;

set local role authenticated;
select pg_temp.as_user(5);
select is((select (w->>'adherence_pct')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 8)) w
           where (w->>'week')::int = 1), 75, '3 of 4 done is 75 %');
select is((select (w->>'adherence_pct')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 8)) w
           where (w->>'week')::int = 2), 100, 'all done is 100 %');
select is((select (w->>'adherence_pct')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 8)) w
           where (w->>'week')::int = 3), 0, 'none done is 0 %');
select is((select (w->>'planned')::int || '/' || (w->>'completed')::int
           from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 8)) w
           where (w->>'week')::int = 1), '4/3', 'planned and completed counts are returned');
select is((select jsonb_agg(w->>'phase' order by (w->>'week')::int)
           from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 8)) w),
  '["past","current","upcoming","upcoming"]'::jsonb, 'phases follow today (day 9 of the block)');
select is((select jsonb_agg(w->>'phase' order by (w->>'week')::int)
           from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 6)) w),
  '["current","upcoming","upcoming","upcoming"]'::jsonb, 'day 7 is still week 1');
select is((select jsonb_agg(w->>'phase' order by (w->>'week')::int)
           from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b() + 7)) w),
  '["past","current","upcoming","upcoming"]'::jsonb, 'day 8 is week 2');
select is((select w->>'start_date' || ' ' || (w->>'end_date') from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b())) w
           where (w->>'week')::int = 2), (pg_temp.b() + 7)::text || ' ' || (pg_temp.b() + 13)::text, 'the window of week 2 is days 8 to 14');

select pg_temp.as_user(4);
select is((select (w->>'adherence_pct')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 4), pg_temp.b() + 8)) w
           where (w->>'week')::int = 1), 33, '1 of 3 done is 33 %');
select is((select (w->>'adherence_pct')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 4), pg_temp.b() + 8)) w
           where (w->>'week')::int = 3), 67, '2 of 3 done is 67 %');
-- A missed session of week 1 stays pending and week 2 is untouched (day 8 workout linked to week 2 only).
select is((select (s->>'done')::boolean from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 4), pg_temp.b() + 8)) w,
             jsonb_array_elements(w->'sessions') s where (w->>'week')::int = 1 and (s->>'session_index')::int = 3), false,
  'a missed session of a past week stays not done');
select is((select (w->>'completed')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 4), pg_temp.b() + 8)) w
           where (w->>'week')::int = 2), 2, 'a missed session is never moved into the next week');
select is((select jsonb_array_length(public.coach_plan_weeks((select id from plans where u = 4), pg_temp.b()))), 4, 'one entry per week with sessions');

-- Isolation
select pg_temp.as_user(1);
select is(public.coach_plan_weeks((select id from plans where u = 5), pg_temp.b()), '[]'::jsonb, 'user B cannot read the weeks of user A''s plan');
select is(public.coach_plan_weeks(gen_random_uuid(), pg_temp.b()), '[]'::jsonb, 'an unknown plan has no weeks');
select is((select count(*)::int from public.coach_plan_sessions where user_id <> pg_temp.u(1)), 0, 'user A cannot read other users'' plan sessions');

select * from finish();
rollback;
