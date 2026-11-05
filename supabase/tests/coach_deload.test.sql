-- pgTAP: deload_suggestions (T-165, US-75; rules in planning/04 section 9.10). The evaluate / cancel / request RPCs
-- are covered by coach_deload_auto.test.sql (T-230).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(9);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('ca000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.exn(n int) returns uuid language sql as $$
  select id from public.exercises where not is_custom order by key offset n limit 1 $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

-- A plan of 4 weeks with 2 routines per week.
create function pg_temp.mkplan(u int, p_start date) returns uuid
language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); v_folder uuid; v_r uuid; i int; w int;
begin
  insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque') returning id into v_folder;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id)
  values (v_plan, pg_temp.u(u), p_start, 'muscle', '{}', 'accepted', v_folder);
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

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'dl' || n || '@example.com' from generate_series(1, 2) n;

-- Block started 14 days ago, week 4 (days 21 to 27) marked as a deload week.
create temp table plans (u int, id uuid);
grant select on plans to authenticated;
insert into plans values (1, pg_temp.mkplan(1, pg_temp.today() - 14));
update public.coach_plan_sessions set deload = true where plan_id = (select id from plans where u = 1) and week = 4;

select ok(not has_function_privilege('anon', 'public.deload_suggestions(uuid)', 'execute'), 'anon cannot read suggestions');
select ok(not exists (select 1 from pg_proc where proname = 'decide_coach_deload'), 'the accept / decline RPC is gone');

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
select * from finish();
rollback;
