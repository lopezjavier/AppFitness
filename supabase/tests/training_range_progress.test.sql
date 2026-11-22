-- pgTAP: training_range_progress and progress_records_in_range (T-320, US-145 data side).
-- Rules: planning/07-coach-design.md section 3 (D1) progress of the period, (D2) records, (D4) change of the principal
-- exercises; planning/04 sections 4.1, 4.5, 4.6; contract: tasks/todo.md "## T-320".
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(37);

-- ---------------------------------------------------------------------------
-- Fixtures (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9320000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
-- Day k of the fixture calendar (day 0 is a Monday).
create function pg_temp.d(k int) returns date language sql as $$ select date '2026-03-02' + k $$;

create temp table cat as
  select 'm' || i as k, (select id from public.exercises
                         where not is_custom
                           and movement_pattern in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                                    'horizontal_pull', 'vertical_pull')
                         order by key offset i - 1 limit 1) as id
  from generate_series(1, 5) i
  union all
  select 'i' || i, (select id from public.exercises
                    where not is_custom and movement_pattern = 'isolation'
                    order by key offset i - 1 limit 1)
  from generate_series(1, 4) i
  union all
  select 'i20', (select id from public.exercises
                 where not is_custom and movement_pattern = 'isolation' order by key offset 19 limit 1)
  union all
  select 'bw', (select id from public.exercises
                where not is_custom and metric_type = 'reps_only'
                  and movement_pattern::text not in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                                     'horizontal_pull', 'vertical_pull', 'isolation')
                order by key limit 1);
grant select on cat to authenticated;
create function pg_temp.x(k text) returns uuid language sql as $$ select id from cat where cat.k = $1 $$;

-- A completed workout on a date with one exercise; sets = [[kg, reps, rpe?, set_type?], ...].
create function pg_temp.sess(n int, day date, ex uuid, sets jsonb) returns void language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(n), 'W', 'completed', day + interval '10 hours', day + interval '11 hours');
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps,
                                   completed_at)
  select v, ex, 0, s.i, coalesce(s.e->>3, 'working')::public.set_type, (s.e->>0)::numeric, (s.e->>1)::int,
         day + interval '10 hours 5 minutes'
  from jsonb_array_elements(sets) with ordinality s(e, i);
end $$;

create function pg_temp.mkplan(n int) returns uuid language plpgsql as $$
declare v_plan uuid := gen_random_uuid();
begin
  insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d(0), 4, 'muscle', '{"rate_kg_week": 0}', 'accepted', now());
  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, deload)
  select v_plan, pg_temp.u(n), k, 1, 'Sesion', '[]', false from generate_series(1, 4) k;
  return v_plan;
end $$;

insert into auth.users (id, email) select pg_temp.u(n), 'range' || n || '@example.com' from generate_series(1, 7) n;

-- User 1: bounded progress. 1-rep sets make the e1RM equal to the load.
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('m1'), '[[500,1]]');
select pg_temp.sess(1, pg_temp.d(4), pg_temp.x('m1'), '[[510,1]]');
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('m1'), '[[519.5,1]]');   -- +3.9 %: margin, with 3 sessions
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('m2'), '[[500,1]]');
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('m2'), '[[520,1]]');     -- +4.0 %: up
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('m3'), '[[500,1]]');
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('m3'), '[[480,1]]');     -- -4.0 %: down
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('m4'), '[[500,1]]');
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('m4'), '[[480.5,1]]');   -- -3.9 %: margin
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('m5'), '[[500,1]]');     -- one session: uncompared
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('i1'), '[[100,1]]');
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('i1'), '[[106,1]]');     -- +6.0 %
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('i2'), '[[100,1]]');
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('i2'),
  '[[100,1],[200,1,null,"warm_up"],[300,1,null,"drop"],[300,1,null,"failure"]]'); -- only the working set counts
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('i3'), '[[100,13]]');    -- 13+ reps: no e1RM
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('i3'), '[[100,13]]');
select pg_temp.sess(1, pg_temp.d(1), pg_temp.x('bw'), '[[0,10]]');      -- no load: absent
select pg_temp.sess(1, pg_temp.d(8), pg_temp.x('bw'), '[[0,12]]');

-- User 2: other data, heavier loads and an exercise user 1 never trained.
select pg_temp.sess(2, pg_temp.d(1), pg_temp.x('m1'), '[[1000,1]]');
select pg_temp.sess(2, pg_temp.d(8), pg_temp.x('m1'), '[[1100,1]]');
select pg_temp.sess(2, pg_temp.d(1), pg_temp.x('i20'), '[[200,1]]');
select pg_temp.sess(2, pg_temp.d(8), pg_temp.x('i20'), '[[250,1]]');

-- User 3: records of the range [day 5, day 14]; every earlier baseline is on day 0.
select pg_temp.sess(3, pg_temp.d(0), pg_temp.x('m' || i), '[[100,1]]') from generate_series(1, 4) i;
select pg_temp.sess(3, pg_temp.d(10), pg_temp.x('m' || i), '[[110,1]]') from generate_series(1, 4) i;  -- 4 x 2 kinds = 8
select pg_temp.sess(3, pg_temp.d(0), pg_temp.x('i1'), '[[100,1]]');
select pg_temp.sess(3, pg_temp.d(10), pg_temp.x('i1'), '[[200,1]]');    -- the biggest gain, but not a main lift: 2 more
select pg_temp.sess(3, pg_temp.d(0), pg_temp.x('i2'), '[[100,1]]');
select pg_temp.sess(3, pg_temp.d(10), pg_temp.x('i2'), '[[100,1]]');    -- a tie is no record
select pg_temp.sess(3, pg_temp.d(10), pg_temp.x('i3'), '[[100,1]]');    -- the first session is never a record

-- User 4: "Todo" versus a bounded range.
select pg_temp.sess(4, date '2025-01-10', pg_temp.x('m1'), '[[100,1]]');
select pg_temp.sess(4, date '2025-01-20', pg_temp.x('m1'), '[[110,1]]');
select pg_temp.sess(4, date '2026-03-05', pg_temp.x('m1'), '[[120,1]]');
select pg_temp.sess(4, date '2026-03-20', pg_temp.x('m1'), '[[115,1]]');
select pg_temp.sess(4, date '2026-03-05', pg_temp.x('m2'), '[[100,1]]');
select pg_temp.sess(4, date '2026-03-20', pg_temp.x('m2'), '[[130,1]]');

-- Users 5 (warm-up sets off) and 7 (on): the same data, a heavier warm-up set on day 8.
insert into public.training_preferences (user_id, warm_up_included) values (pg_temp.u(7), true);
select pg_temp.sess(n, pg_temp.d(1), pg_temp.x('m1'), '[[100,1]]') from unnest(array[5, 7]) n;
select pg_temp.sess(n, pg_temp.d(8), pg_temp.x('m1'), '[[120,1,null,"warm_up"],[100,1]]') from unnest(array[5, 7]) n;

-- User 6: parity with the weekly and block record functions.
select pg_temp.mkplan(6);
select pg_temp.sess(6, pg_temp.d(1), pg_temp.x('m1'), '[[100,1]]');
select pg_temp.sess(6, pg_temp.d(8), pg_temp.x('m1'), '[[110,1]]');
select pg_temp.sess(6, pg_temp.d(15), pg_temp.x('m1'), '[[120,1]]');
select pg_temp.sess(6, pg_temp.d(2), pg_temp.x('m2'), '[[100,5]]');
select pg_temp.sess(6, pg_temp.d(9), pg_temp.x('m2'), '[[105,5]]');
select pg_temp.sess(6, pg_temp.d(3), pg_temp.x('i1'), '[[50,10]]');
select pg_temp.sess(6, pg_temp.d(10), pg_temp.x('i1'), '[[60,10]]');
select pg_temp.sess(6, pg_temp.d(11), pg_temp.x('i2'), '[[60,10]]');
select pg_temp.sess(6, pg_temp.d(22), pg_temp.x('i2'), '[[70,10]]');

-- Helper: the exercise row of the bounded or open range, as the caller.
create function pg_temp.row_of(k text, f date default null, t date default null) returns jsonb language sql as $$
  select e from jsonb_array_elements(public.training_range_progress(f, t)->'exercises') e
  where e->>'exercise_id' = pg_temp.x(k)::text $$;
grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Parity (as the owner with user 6's claims; the record path filters by user explicitly)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(6) || '","role":"authenticated"}', true);

select ok(jsonb_array_length(public.coach_progress_records_for(pg_temp.u(6), (select id from public.coach_plans where user_id = pg_temp.u(6)), 2, pg_temp.d(7), pg_temp.d(13))) > 0,
  'parity fixture: the week has records');
select is(public.training_range_progress(pg_temp.d(7), pg_temp.d(13))->'records'->'items',
  public.coach_progress_records_for(pg_temp.u(6), (select id from public.coach_plans where user_id = pg_temp.u(6)), 2, pg_temp.d(7), pg_temp.d(13)),
  'the range records items equal the weekly report records');
select ok((public.coach_progress_block_records_for(pg_temp.u(6), pg_temp.d(0), pg_temp.d(27))->>'total')::int > 3,
  'parity fixture: the block has more than 3 records');
select is(public.training_range_progress(pg_temp.d(0), pg_temp.d(27))->'records',
  public.coach_progress_block_records_for(pg_temp.u(6), pg_temp.d(0), pg_temp.d(27)),
  'the range records equal the block review records (items and total)');

-- Grants (anon none, authenticated yes) for the three new functions.
select ok(has_function_privilege('authenticated', 'public.training_range_progress(date, date)', 'execute')
      and not has_function_privilege('anon', 'public.training_range_progress(date, date)', 'execute'),
  'training_range_progress: authenticated yes, anon no');
select ok(has_function_privilege('authenticated', 'public.weekly_muscle_sets(integer)', 'execute')
      and not has_function_privilege('anon', 'public.weekly_muscle_sets(integer)', 'execute'),
  'weekly_muscle_sets: authenticated yes, anon no');
select ok(has_function_privilege('authenticated', 'public.progress_records_in_range(uuid, date, date, boolean)', 'execute')
      and not has_function_privilege('anon', 'public.progress_records_in_range(uuid, date, date, boolean)', 'execute'),
  'progress_records_in_range: authenticated yes, anon no');
select ok(not has_function_privilege('authenticated', 'public.coach_progress_records_for(uuid, uuid, integer, date, date)', 'execute')
      and not has_function_privilege('authenticated', 'public.coach_progress_block_records_for(uuid, date, date)', 'execute'),
  'the two coach record functions stay internal');

-- ---------------------------------------------------------------------------
-- As user 1: bounded progress
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(1) || '","role":"authenticated"}', true);

select is(pg_temp.row_of('i1', pg_temp.d(0), pg_temp.d(30)) - 'exercise_id',
  '{"main_lift":false,"sessions":2,"first":{"date":"2026-03-03","e1rm_kg":100},"last":{"date":"2026-03-10","e1rm_kg":106},"change_kg":6,"change_pct":6.0,"state":"up"}'::jsonb,
  'e1RM 100 to 106 is up, +6 kg, +6.0 %');
select is(pg_temp.row_of('m1', pg_temp.d(0), pg_temp.d(30)) - 'exercise_id',
  '{"main_lift":true,"sessions":3,"first":{"date":"2026-03-03","e1rm_kg":500},"last":{"date":"2026-03-10","e1rm_kg":519.5},"change_kg":19.5,"change_pct":3.9,"state":"margin"}'::jsonb,
  '+3.9 % is margin; first is the earliest session and last the latest of three');
select is(pg_temp.row_of('m2', pg_temp.d(0), pg_temp.d(30))->>'state', 'up', '+4.0 % is up');
select is(pg_temp.row_of('m3', pg_temp.d(0), pg_temp.d(30))->>'state', 'down', '-4.0 % is down');
select is(pg_temp.row_of('m3', pg_temp.d(0), pg_temp.d(30))->'change_pct', '-4.0'::jsonb, '-4.0 % is reported as -4.0');
select is(pg_temp.row_of('m4', pg_temp.d(0), pg_temp.d(30))->>'state', 'margin', '-3.9 % is margin');
select is(pg_temp.row_of('m5', pg_temp.d(0), pg_temp.d(30)) - 'exercise_id',
  '{"main_lift":true,"sessions":1,"first":{"date":"2026-03-03","e1rm_kg":500},"last":null,"change_kg":null,"change_pct":null,"state":"uncompared"}'::jsonb,
  'a single session is uncompared with no last and no change');
select is(pg_temp.row_of('i2', pg_temp.d(0), pg_temp.d(30))->'change_kg', '0'::jsonb,
  'warm-up, drop and failure sets do not count for the progress e1RM');
select is(pg_temp.row_of('i3', pg_temp.d(0), pg_temp.d(30)), null::jsonb, 'sets over 12 reps give no e1RM: the exercise is absent');
select is(pg_temp.row_of('bw', pg_temp.d(0), pg_temp.d(30)), null::jsonb, 'an exercise without a load is absent');
select is((select jsonb_agg(e->>'exercise_id' order by o) from jsonb_array_elements(public.training_range_progress(pg_temp.d(0), pg_temp.d(30))->'exercises') with ordinality t(e, o)),
  (select jsonb_agg(pg_temp.x(k)::text order by o) from unnest(array['m1', 'm2', 'm3', 'm4', 'm5', 'i1', 'i2']) with ordinality t(k, o)),
  'main lifts first, then most sessions, then key');
select is(public.training_range_progress(pg_temp.d(100), pg_temp.d(110)),
  '{"exercises": [], "records": {"items": [], "total": 0}}'::jsonb, 'a range without sessions returns empty lists, never null');

-- ---------------------------------------------------------------------------
-- Isolation
-- ---------------------------------------------------------------------------
select is(pg_temp.row_of('i20'), null::jsonb, 'user 2''s exercises never appear for user 1');
select is(pg_temp.row_of('m1', pg_temp.d(0), pg_temp.d(30))->'last'->'e1rm_kg', '519.5'::jsonb, 'user 2''s heavier loads never reach user 1');

select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(2) || '","role":"authenticated"}', true);
select is(public.progress_records_in_range(pg_temp.u(1), pg_temp.d(0), pg_temp.d(30), false), '{"items": [], "total": 0}'::jsonb,
  'records of a foreign user id are empty (invoker plus RLS)');
select is((select jsonb_agg(e->>'exercise_id' order by e->>'exercise_id') from jsonb_array_elements(public.training_range_progress()->'exercises') e),
  (select jsonb_agg(id::text order by id::text) from cat where k in ('m1', 'i20')), 'user 2 sees only her own exercises');

-- ---------------------------------------------------------------------------
-- Records of the range (user 3)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(3) || '","role":"authenticated"}', true);
select is((public.training_range_progress(pg_temp.d(5), pg_temp.d(14))->'records'->>'total')::int, 10,
  'total counts every record: 4 main lifts x 2 kinds + 1 isolation x 2; no tie, no first session');
select is(jsonb_array_length(public.training_range_progress(pg_temp.d(5), pg_temp.d(14))->'records'->'items'), 3,
  'items are capped at 3');
select ok((select bool_and((i->>'main_lift')::boolean) from jsonb_array_elements(public.training_range_progress(pg_temp.d(5), pg_temp.d(14))->'records'->'items') i),
  'main lifts come before a larger relative gain on an isolation');
select is((public.training_range_progress(pg_temp.d(11), pg_temp.d(14))->'records'->>'total')::int, 0,
  'a window after the sessions has no records (earlier days are only the baseline)');

-- ---------------------------------------------------------------------------
-- "Todo" and range edges (user 4)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(4) || '","role":"authenticated"}', true);
select is(pg_temp.row_of('m1') - 'exercise_id',
  '{"main_lift":true,"sessions":4,"first":{"date":"2025-01-20","e1rm_kg":110},"last":{"date":"2026-03-05","e1rm_kg":120},"change_kg":10,"change_pct":9.1,"state":"up"}'::jsonb,
  'Todo compares the best of the first and of the last month, including sessions over a year old');
select is(pg_temp.row_of('m2') - 'exercise_id',
  '{"main_lift":true,"sessions":2,"first":{"date":"2026-03-20","e1rm_kg":130},"last":null,"change_kg":null,"change_pct":null,"state":"uncompared"}'::jsonb,
  'Todo: an exercise whose sessions fall in one month is uncompared');
select is(pg_temp.row_of('m1', date '2026-03-01', date '2026-03-31') - 'exercise_id',
  '{"main_lift":true,"sessions":2,"first":{"date":"2026-03-05","e1rm_kg":120},"last":{"date":"2026-03-20","e1rm_kg":115},"change_kg":-5,"change_pct":-4.2,"state":"down"}'::jsonb,
  'a bounded range compares the first and the last session');
select is(pg_temp.row_of('m2', date '2026-03-01', date '2026-03-31')->>'state', 'up', 'a bounded range splits a month into sessions');
select is(pg_temp.row_of('m1', date '2026-03-05', date '2026-03-05')->>'sessions', '1', 'the range start day is inclusive');
select is(pg_temp.row_of('m1', date '2026-03-20', date '2026-03-20')->>'sessions', '1', 'the range end day is inclusive');
select is(public.training_range_progress(date '2026-03-06', date '2026-03-19'),
  '{"exercises": [], "records": {"items": [], "total": 0}}'::jsonb, 'a range between sessions is empty');

-- ---------------------------------------------------------------------------
-- Warm-up preference changes the records (users 5 and 7)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(5) || '","role":"authenticated"}', true);
select is((public.training_range_progress(pg_temp.d(5), pg_temp.d(14))->'records'->>'total')::int, 0,
  'warm-up sets off: a heavier warm-up set is no record');
select set_config('request.jwt.claims', '{"sub":"' || pg_temp.u(7) || '","role":"authenticated"}', true);
select is((public.training_range_progress(pg_temp.d(5), pg_temp.d(14))->'records'->>'total')::int, 2,
  'warm-up sets on: the heavier warm-up set is a record (e1RM and heaviest)');

select * from finish();
rollback;
