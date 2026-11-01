-- pgTAP: cardio minutes and steps per week in coach_plan_weeks (T-183, US-73; rules in planning/04 section 9.9).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(14);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9100000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.b() returns date language sql as $$ select (now() at time zone 'utc')::date - 8 $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;
create function pg_temp.cardio(n int) returns uuid language sql as $$
  select id from public.exercises where movement_pattern = 'cardio' order by key offset n limit 1 $$;
create function pg_temp.lift() returns uuid language sql as $$
  select id from public.exercises where movement_pattern is distinct from 'cardio' and not is_custom order by key limit 1 $$;
-- A plan with one session in each of the 4 weeks.
create function pg_temp.mkplan(u int, p_nutrition jsonb, p_training jsonb) returns uuid language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); w int;
begin
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, training, status)
  values (v_plan, pg_temp.u(u), pg_temp.b(), 'muscle', p_nutrition, p_training, 'accepted');
  for w in 1..4 loop
    insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
    values (v_plan, pg_temp.u(u), w, 1, 'Sesion', '[]');
  end loop;
  return v_plan;
end $$;
-- One set of an exercise in a new session of the given status; completed sets carry completed_at.
create function pg_temp.setx(u int, p_day date, p_ex uuid, p_dur int, p_status text default 'completed', p_done boolean default true)
returns void language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), 'W', p_status::public.session_status, pg_temp.at(p_day),
          case when p_status = 'completed' then pg_temp.at(p_day) + interval '1 hour' end);
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, duration_s, completed_at)
  values (v, p_ex, 0, 1, p_dur, case when p_done then pg_temp.at(p_day) end);
end $$;
create function pg_temp.wk(p_plan uuid, p_week int, p_key text) returns text language sql as $$
  select w->>p_key from jsonb_array_elements(public.coach_plan_weeks(p_plan, pg_temp.b() + 8)) w where (w->>'week')::int = p_week $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'cwc' || n || '@example.com' from generate_series(1, 4) n;

create temp table plans (u int, id uuid);
grant select on plans to authenticated;
insert into plans values
  (1, pg_temp.mkplan(1, '{"activity":{"cardio_min_week":150,"steps_day":7000}}', '{"cardio":{"target_min_week":120}}')),
  (2, pg_temp.mkplan(2, '{}', '{"cardio":{"target_min_week":80}}')),
  (3, pg_temp.mkplan(3, '{}', null)),
  (4, pg_temp.mkplan(4, '{"activity":{"cardio_min_week":90,"steps_day":9000}}', null));

-- User 1, week 1 (days 1-7): 50 min on a bike + 50 min on the last day of the week = 100.
select pg_temp.setx(1, pg_temp.b() + 1, pg_temp.cardio(0), 3000);
select pg_temp.setx(1, pg_temp.b() + 6, pg_temp.cardio(1), 3000);
-- Not counted: a lifting set, an uncompleted set, an unfinished session and a day before the block.
select pg_temp.setx(1, pg_temp.b() + 2, pg_temp.lift(), 3000);
select pg_temp.setx(1, pg_temp.b() + 2, pg_temp.cardio(0), 3000, 'completed', false);
select pg_temp.setx(1, pg_temp.b() + 2, pg_temp.cardio(0), 3000, 'in_progress', true);
select pg_temp.setx(1, pg_temp.b() - 1, pg_temp.cardio(0), 3000);
-- Week 2 starts on day 8: 20 min, nothing carried over from week 1.
select pg_temp.setx(1, pg_temp.b() + 7, pg_temp.cardio(0), 1200);
-- Week 3: two sets of 90 s add up to 3 min over the week total (per-set rounding would give 4).
select pg_temp.setx(1, pg_temp.b() + 14, pg_temp.cardio(0), 90);
select pg_temp.setx(1, pg_temp.b() + 15, pg_temp.cardio(0), 90);
-- Week 4: a single 90 s set rounds half up to 2.
select pg_temp.setx(1, pg_temp.b() + 21, pg_temp.cardio(0), 90);
-- User 4 trains and walks in the same days: never leaks into user 1.
select pg_temp.setx(4, pg_temp.b() + 1, pg_temp.cardio(0), 6000);
insert into public.daily_activity (user_id, day, steps) values
  (pg_temp.u(1), pg_temp.b(), 1000), (pg_temp.u(1), pg_temp.b() + 1, 2000), (pg_temp.u(1), pg_temp.b() + 2, 2000),
  (pg_temp.u(1), pg_temp.b() + 3, null),
  (pg_temp.u(4), pg_temp.b(), 20000);

set local role authenticated;
select pg_temp.as_user(1);

select is(pg_temp.wk((select id from plans where u = 1), 1, 'cardio_min_done'), '100',
  'week 1 counts the completed cardio sets of completed sessions, including the last day of the week');
select is(pg_temp.wk((select id from plans where u = 1), 2, 'cardio_min_done'), '20',
  'the first day of week 2 counts in week 2 only, nothing is carried over');
select is(pg_temp.wk((select id from plans where u = 1), 3, 'cardio_min_done'), '3',
  'minutes are rounded over the week total, not per set');
select is(pg_temp.wk((select id from plans where u = 1), 4, 'cardio_min_done'), '2', 'half a minute rounds up');

select is(pg_temp.wk((select id from plans where u = 1), 1, 'cardio_min_target'), '150',
  'the cardio target is the current nutrition activity target');
select is((select (w->>'adherence_pct')::int from jsonb_array_elements(public.coach_plan_weeks((select id from plans where u = 1), pg_temp.b() + 8)) w
           where (w->>'week')::int = 1), 0, 'the existing keys are unchanged');

select is(pg_temp.wk((select id from plans where u = 1), 1, 'steps_avg'), '1667', 'steps average only counts days with steps');
select is(pg_temp.wk((select id from plans where u = 1), 2, 'steps_avg'), null, 'a week without steps has a null average');
select is(pg_temp.wk((select id from plans where u = 1), 1, 'steps_target'), '7000', 'the steps target comes from the activity target');

select pg_temp.as_user(2);
select is(pg_temp.wk((select id from plans where u = 2), 1, 'cardio_min_target'), '80',
  'the target falls back to the training cardio target');
select is(pg_temp.wk((select id from plans where u = 2), 1, 'steps_target'), null, 'no steps target without activity');

select pg_temp.as_user(3);
select is(pg_temp.wk((select id from plans where u = 3), 1, 'cardio_min_target'), null, 'a plan without cardio target has a null target');

-- Isolation
select pg_temp.as_user(1);
select is(public.coach_plan_weeks((select id from plans where u = 4), pg_temp.b()), '[]'::jsonb, 'user A cannot read the weeks of user B''s plan');
select pg_temp.as_user(4);
select is(pg_temp.wk((select id from plans where u = 4), 1, 'cardio_min_done'), '100', 'user B only sees their own cardio minutes');

select * from finish();
rollback;
