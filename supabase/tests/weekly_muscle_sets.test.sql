-- pgTAP: weekly_muscle_sets (T-320, US-145 data side). Rules: planning/07-coach-design.md section 3 (D6) sets per muscle
-- group by week; planning/04 section 4.5; contract: tasks/todo.md "## T-320".
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(18);

-- ---------------------------------------------------------------------------
-- Fixtures (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9321000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
-- Monday of the ISO week i weeks ago (UTC); 0 = the current week.
create function pg_temp.w(i int) returns date language sql as $$
  select date_trunc('week', (now() at time zone 'utc')::date)::date - 7 * i $$;

insert into auth.users (id, email) select pg_temp.u(n), 'weekly' || n || '@example.com' from generate_series(1, 3) n;
-- Custom exercises are visible to their creator only, so each user gets her own press.
insert into public.exercises (name, muscle_group, secondary_muscles, equipment, metric_type, is_custom, created_by)
select 'Press semanal test', 'chest', '{triceps,shoulders,chest}', 'barbell', 'weight_reps', true, pg_temp.u(n)
from generate_series(1, 3) n;
insert into public.exercises (name, muscle_group, secondary_muscles, equipment, metric_type, is_custom, created_by) values
  ('Plancha semanal test', 'core', '{}', 'bodyweight', 'duration', true, pg_temp.u(1));

-- A completed workout at the given UTC instant; sets = [[type, kg, reps, duration_s], ...] on one exercise.
create function pg_temp.sess(n int, t0 timestamptz, ex_name text, sets jsonb) returns void language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(n), 'W', 'completed', t0, t0 + interval '1 hour');
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps,
                                   duration_s, completed_at)
  select v, (select id from public.exercises where name = ex_name and created_by = pg_temp.u(n)), 0, s.i, (s.e->>0)::public.set_type,
         (s.e->>1)::numeric, (s.e->>2)::int, (s.e->>3)::int, t0 + interval '5 minutes'
  from jsonb_array_elements(sets) with ordinality s(e, i);
end $$;
create function pg_temp.ts(i int, extra interval) returns timestamptz language sql as $$
  select (pg_temp.w(i)::timestamp + extra) at time zone 'utc' $$;

-- User 1
select pg_temp.sess(1, pg_temp.ts(2, interval '1 day 10 hours'), 'Press semanal test',
  '[["working",100,5,null],["working",100,5,null],["working",100,5,null]]');
select pg_temp.sess(1, pg_temp.ts(5, interval '1 day 10 hours'), 'Plancha semanal test',
  '[["working",0,0,60],["working",0,0,60]]');
select pg_temp.sess(1, pg_temp.ts(0, interval '0'), 'Press semanal test', '[["working",100,5,null]]');
select pg_temp.sess(1, pg_temp.ts(1, interval '1 day 10 hours'), 'Press semanal test',
  '[["working",100,5,null],["warm_up",60,5,null]]');
select pg_temp.sess(1, pg_temp.ts(10, interval '1 day 10 hours'), 'Press semanal test', '[["working",100,5,null]]');
-- User 2 trains in week 1 only; user 3 counts warm-up sets and has user 1's week-1 data.
select pg_temp.sess(2, pg_temp.ts(1, interval '1 day 10 hours'), 'Press semanal test', '[["working",100,5,null]]');
insert into public.training_preferences (user_id, warm_up_included) values (pg_temp.u(3), true);
select pg_temp.sess(3, pg_temp.ts(1, interval '1 day 10 hours'), 'Press semanal test',
  '[["working",100,5,null],["warm_up",60,5,null]]');

-- The groups of the week i weeks ago in weekly_muscle_sets(p), as the caller.
create function pg_temp.wk(i int, p int default 8) returns jsonb language sql as $$
  select e->'groups' from jsonb_array_elements(public.weekly_muscle_sets(p)) e
  where e->>'week_start' = pg_temp.w(i)::text $$;
grant execute on all functions in schema pg_temp to authenticated;

select ok(has_function_privilege('authenticated', 'public.weekly_muscle_sets(integer)', 'execute'), 'authenticated can execute');
select ok(not has_function_privilege('anon', 'public.weekly_muscle_sets(integer)', 'execute')
      and not has_function_privilege('public', 'public.weekly_muscle_sets(integer)', 'execute'), 'anon and public cannot');

-- ---------------------------------------------------------------------------
-- As user 1
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(1) || '","role":"authenticated"}', true);

select is(jsonb_array_length(public.weekly_muscle_sets()), 8, 'the default is 8 weeks');
select is(jsonb_array_length(public.weekly_muscle_sets(3)), 3, 'p_weeks sets the number of entries');
select is(jsonb_build_array(public.weekly_muscle_sets()->0->'week_start', public.weekly_muscle_sets()->7->'week_start'),
  jsonb_build_array(pg_temp.w(7), pg_temp.w(0)), 'oldest week first, the last one is the current ISO week');
select is(pg_temp.wk(3), '[]'::jsonb, 'a week without sessions is present with no groups');
select is(pg_temp.wk(2),
  '[{"muscle_group":"chest","sets":3},{"muscle_group":"shoulders","sets":1.5},{"muscle_group":"triceps","sets":1.5}]'::jsonb,
  'primary counts 1 per set, secondary 0.5; ties in enum order');
select is(pg_temp.wk(5), '[{"muscle_group":"core","sets":2}]'::jsonb, 'not strength-only: a duration exercise counts');
select is(pg_temp.wk(2),
  (select jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets) order by g.sets desc, g.muscle_group)
   from public.muscle_group_sets(pg_temp.w(2), pg_temp.w(2) + 6) g),
  'same as muscle_group_sets for the same dates');
select is(pg_temp.wk(0), '[{"muscle_group":"chest","sets":1},{"muscle_group":"shoulders","sets":0.5},{"muscle_group":"triceps","sets":0.5}]'::jsonb,
  'the current week is partial but present');
select is(pg_temp.wk(1)->0, '{"muscle_group":"chest","sets":1}'::jsonb, 'warm-up sets off: only the working set counts');
select is(pg_temp.wk(10, 8), null::jsonb, 'a session older than the requested weeks is excluded');
select is(pg_temp.wk(10, 12)->0->>'muscle_group', 'chest', 'the same session counts when the weeks reach it');
select throws_ok($$select public.weekly_muscle_sets(0)$$, '22023', null, 'p_weeks 0 is invalid');
select throws_ok($$select public.weekly_muscle_sets(53)$$, '22023', null, 'p_weeks 53 is invalid');

-- ---------------------------------------------------------------------------
-- Isolation and the warm-up preference
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(2) || '","role":"authenticated"}', true);
select is(pg_temp.wk(2), '[]'::jsonb, 'user 2 does not see user 1''s sessions');
select is(pg_temp.wk(1)->0, '{"muscle_group":"chest","sets":1}'::jsonb, 'user 2 sees her own week');

select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(3) || '","role":"authenticated"}', true);
select is(pg_temp.wk(1)->0, '{"muscle_group":"chest","sets":2}'::jsonb, 'warm-up sets on: the warm-up set counts');

select * from finish();
rollback;
