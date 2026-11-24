-- pgTAP: session_comparison and its helper coach_user_sets_for (T-323, US-147).
-- Rules: planning/07-coach-design.md section 3 (F) and "Weekly report: progress first" (W1).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(47);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9323000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
-- Day k of every block (block_start = day 0, a Monday).
create function pg_temp.d(k int) returns date language sql as $$ select date '2026-03-02' + k $$;
create function pg_temp.ts(k int, h numeric default 10) returns timestamptz language sql as $$
  select (pg_temp.d(k)::timestamp + make_interval(secs => h * 3600)) at time zone 'UTC' $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.ex(i int) returns uuid language sql as $$
  select id from public.exercises where not is_custom order by key offset i - 1 limit 1 $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

-- A plan of 4 weeks with one plan session a week; `deload` weeks flagged.
create function pg_temp.mkplan(n int, deload int[] default '{}', st text default 'accepted',
                               acc timestamptz default now()) returns uuid language plpgsql as $$
declare v_plan uuid := gen_random_uuid();
begin
  insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d(0), 4, 'muscle', '{"rate_kg_week": 0}', st, acc);
  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, deload)
  select v_plan, pg_temp.u(n), k, 1, 'Sesion', '[]', k = any (deload) from generate_series(1, 4) k;
  return v_plan;
end $$;

-- A session on day k (status completed unless said); returns its id.
create function pg_temp.mksess(n int, k int, st text default 'completed') returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(n), 'W', st::public.session_status, pg_temp.ts(k, 10), case when st = 'completed' then pg_temp.ts(k, 11) end);
  return v;
end $$;

-- Sets of one exercise at one position: sets = [[kg, reps, rpe?, set_type?], ...], rep range on every set.
create function pg_temp.addsets(sid uuid, ex uuid, pos int, sets jsonb, rmin int default null, rmax int default null,
                                first_index int default 1) returns void language sql as $$
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe,
                                   rep_min, rep_max, completed_at)
  select sid, ex, pos, first_index + s.i - 1, coalesce(s.e->>3, 'working')::public.set_type, (s.e->>0)::numeric,
         (s.e->>1)::int, (s.e->>2)::numeric, rmin, rmax, now()
  from jsonb_array_elements(sets) with ordinality s(e, i) $$;

create function pg_temp.cmp(sid uuid) returns jsonb language sql as $$ select public.session_comparison(sid) $$;
create function pg_temp.el(sid uuid, ex uuid) returns jsonb language sql as $$
  select x from jsonb_array_elements(pg_temp.cmp(sid)) x where x->>'exercise_id' = ex::text $$;

insert into auth.users (id, email)
select pg_temp.u(n), 'compare' || n || '@example.com' from generate_series(1, 4) n;

-- ---------------------------------------------------------------------------
-- User 1 (A): plan with week 2 a deload week. S1 day 3 (week 1), S2 day 10 (deload week), S3 day 17 (week 3).
-- User 2 (B): same exercises, heavier, and her own discomfort.
-- ---------------------------------------------------------------------------
select pg_temp.mkplan(1, '{2}');
select pg_temp.mkplan(2);

create temp table s as
select pg_temp.mksess(1, 3) as s1, pg_temp.mksess(1, 10) as s2, pg_temp.mksess(1, 17) as s3,
       pg_temp.mksess(1, 24, 'in_progress') as live, pg_temp.mksess(2, 5) as b1, pg_temp.mksess(2, 12) as b2,
       pg_temp.mksess(1, -20) as early, pg_temp.mksess(1, 25) as s4;
grant select on s to public;

-- ex1: S1 normal, S2 deload, S3 normal -> previous of S3 skips the deload session.
select pg_temp.addsets((select s1 from s), pg_temp.ex(1), 0, '[[80,8,7],[80,8,7]]', 6, 10);
select pg_temp.addsets((select s2 from s), pg_temp.ex(1), 0, '[[60,8]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(1), 0, '[[82.5,6],[82.5,6]]', 6, 10);
-- B trains ex1 heavier and earlier than A's S3.
select pg_temp.addsets((select b1 from s), pg_temp.ex(1), 0, '[[200,8]]');
select pg_temp.addsets((select b2 from s), pg_temp.ex(1), 0, '[[210,8]]');
-- ex2: only in S3 -> no previous.
select pg_temp.addsets((select s3 from s), pg_temp.ex(2), 1, '[[50,10]]');
-- ex3: in S3 at positions 13 and 14 (merged in position order, set_index restarts per position).
select pg_temp.addsets((select s3 from s), pg_temp.ex(3), 14, '[[70,5],[70,4]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(3), 13, '[[60,10],[65,8]]');
-- ex4: warm-up, drop and failure sets are excluded; one working set remains. ex5: only a warm-up set.
select pg_temp.addsets((select s3 from s), pg_temp.ex(4), 3, '[[40,10,null,"warm_up"],[80,8],[70,8,null,"drop"],[75,6,null,"failure"]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(5), 4, '[[20,10,null,"warm_up"]]');
-- ex4 also in S1 with warm-up only: not a previous.
select pg_temp.addsets((select s1 from s), pg_temp.ex(4), 1, '[[30,10,null,"warm_up"]]');
-- Discomfort exercises, one working set each in S3.
select pg_temp.addsets((select s3 from s), pg_temp.ex(6), 5, '[[50,10]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(7), 6, '[[50,10]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(8), 7, '[[50,10]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(9), 8, '[[50,10]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(10), 9, '[[50,10]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(11), 10, '[[50,10]]');
-- An in-progress session never feeds a comparison.
select pg_temp.addsets((select live from s), pg_temp.ex(1), 0, '[[99,9]]');
-- A session before the plan starts has no plan week and is no deload.
select pg_temp.addsets((select early from s), pg_temp.ex(12), 0, '[[40,10]]');
select pg_temp.addsets((select s3 from s), pg_temp.ex(12), 11, '[[45,10]]');
-- A later session of A: the previous of S4 is S3, not S1.
select pg_temp.addsets((select s4 from s), pg_temp.ex(1), 0, '[[85,6]]');

-- Discomforts of A (today = UTC date of now(); levels 1-3 are open until the 7-day edge, 4-5 until resolved).
insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at, resolved_at) values
  (pg_temp.u(1), pg_temp.ex(6), 4, now() - interval '30 days', null),                                -- open, level 4 unresolved
  (pg_temp.u(1), pg_temp.ex(7), 2, now() - interval '3 days', null),                                 -- open, level 2 within 7 days
  (pg_temp.u(1), pg_temp.ex(8), 2, (pg_temp.today() - 6)::timestamp at time zone 'utc', null),       -- exactly 7 days: closed
  (pg_temp.u(1), pg_temp.ex(9), 4, now() - interval '30 days', now() - interval '1 day'),            -- resolved
  (pg_temp.u(1), pg_temp.ex(10), 2, (pg_temp.today() - 5)::timestamp at time zone 'utc', null),      -- 6 days: open
  (pg_temp.u(2), pg_temp.ex(1), 5, now() - interval '2 days', null);                                 -- B's discomfort

-- User 3: an older superseded plan flagging week 1 as deload, a newer accepted plan with no deload.
select pg_temp.mkplan(3, '{1}', 'superseded', now() - interval '10 days');
select pg_temp.mkplan(3);
select pg_temp.addsets(pg_temp.mksess(3, 3), pg_temp.ex(1), 0, '[[60,8]]');
-- User 4: only a proposed plan flagging week 1 as deload.
select pg_temp.mkplan(4, '{1}', 'proposed', now());
select pg_temp.addsets(pg_temp.mksess(4, 3), pg_temp.ex(1), 0, '[[60,8]]');

select pg_temp.as_user(1);

-- ---------------------------------------------------------------------------
-- Shape and previous-session choice
-- ---------------------------------------------------------------------------
select is((select array_agg(k order by k) from jsonb_object_keys(pg_temp.el((select s3 from s), pg_temp.ex(1))) k),
  array['current', 'exercise_id', 'open_discomfort', 'previous'], 'an element has exactly its four keys');
select is((select array_agg(k order by k) from jsonb_object_keys(pg_temp.el((select s3 from s), pg_temp.ex(1))->'current') k),
  array['day', 'deload', 'session_id', 'sets'], 'a session object has exactly its four keys');
select is((select array_agg(k order by k)
           from jsonb_object_keys(pg_temp.el((select s3 from s), pg_temp.ex(1))->'current'->'sets'->0) k),
  array['rep_max', 'rep_min', 'reps', 'rpe', 'weight_kg'], 'a set has exactly its five keys');
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'previous'->>'session_id', (select s1::text from s),
  'the previous is the latest non-deload session: the deload session between is skipped');
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'previous'->'sets',
  '[{"weight_kg": 80, "reps": 8, "rpe": 7, "rep_min": 6, "rep_max": 10}, {"weight_kg": 80, "reps": 8, "rpe": 7, "rep_min": 6, "rep_max": 10}]'::jsonb,
  'the previous sets carry load, reps, RPE and the rep range');
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'current'->'sets',
  '[{"weight_kg": 82.5, "reps": 6, "rpe": null, "rep_min": 6, "rep_max": 10}, {"weight_kg": 82.5, "reps": 6, "rpe": null, "rep_min": 6, "rep_max": 10}]'::jsonb,
  'the current sets have null RPE when none was logged');
select is(pg_temp.el((select s4 from s), pg_temp.ex(1))->'previous'->>'session_id', (select s3::text from s),
  'a later session compares with the latest earlier one');
select is(pg_temp.el((select s3 from s), pg_temp.ex(2))->'previous', 'null'::jsonb, 'no earlier session: previous is null');
select is(jsonb_typeof(pg_temp.el((select s3 from s), pg_temp.ex(2))->'previous'), 'null', 'previous is a JSON null, not missing');
select is(pg_temp.el((select s1 from s), pg_temp.ex(1))->'previous', 'null'::jsonb, 'the first session of an exercise has no previous');
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'current'->>'session_id', (select s3::text from s), 'current is the session asked for');

-- ---------------------------------------------------------------------------
-- Day, deload, merging and set filters
-- ---------------------------------------------------------------------------
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'current'->>'day', pg_temp.d(17)::text, 'day is the session''s UTC date');
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'current'->'deload', 'false'::jsonb, 'a session in a normal plan week is not a deload');
select is(pg_temp.el((select s2 from s), pg_temp.ex(1))->'current'->'deload', 'true'::jsonb, 'a session in a deload week of the plan is flagged');
select is(pg_temp.el((select s2 from s), pg_temp.ex(1))->'previous'->>'session_id', (select s1::text from s),
  'a deload session as current still gets the latest non-deload previous');
select is(pg_temp.el((select s3 from s), pg_temp.ex(12))->'previous'->'deload', 'false'::jsonb,
  'a session outside every plan window is not a deload');
select is((select count(*)::int from jsonb_array_elements(pg_temp.cmp((select s3 from s))) x where x->>'exercise_id' = pg_temp.ex(3)::text),
  1, 'an exercise at two positions gives one element');
select is((select jsonb_agg(x->'reps') from jsonb_array_elements(pg_temp.el((select s3 from s), pg_temp.ex(3))->'current'->'sets') x),
  '[10, 8, 5, 4]'::jsonb, 'sets are merged in exercise_position then set_index order');
select is(pg_temp.el((select s3 from s), pg_temp.ex(4))->'current'->'sets',
  '[{"weight_kg": 80, "reps": 8, "rpe": null, "rep_min": null, "rep_max": null}]'::jsonb,
  'warm-up, drop and failure sets are excluded');
select is(pg_temp.el((select s3 from s), pg_temp.ex(4))->'previous', 'null'::jsonb, 'a previous session with only warm-up sets does not count');
select is(pg_temp.el((select s3 from s), pg_temp.ex(5)), null::jsonb, 'an exercise with only warm-up sets has no element');
select is(jsonb_array_length(pg_temp.cmp((select s3 from s))), 11, 'one element per exercise with a working set');
select is(pg_temp.cmp((select s3 from s))->0->>'exercise_id', pg_temp.ex(1)::text, 'elements follow the first exercise position');

-- ---------------------------------------------------------------------------
-- Open discomfort
-- ---------------------------------------------------------------------------
select is(pg_temp.el((select s3 from s), pg_temp.ex(6))->'open_discomfort', 'true'::jsonb, 'level 4 unresolved is open');
select is(pg_temp.el((select s3 from s), pg_temp.ex(7))->'open_discomfort', 'true'::jsonb, 'level 2 within 7 days is open');
select is(pg_temp.el((select s3 from s), pg_temp.ex(8))->'open_discomfort', 'false'::jsonb, 'level 2 at exactly 7 days is closed');
select is(pg_temp.el((select s3 from s), pg_temp.ex(9))->'open_discomfort', 'false'::jsonb, 'a resolved discomfort is closed');
select is(pg_temp.el((select s3 from s), pg_temp.ex(10))->'open_discomfort', 'true'::jsonb, 'level 2 at 6 days is open');
select is(pg_temp.el((select s3 from s), pg_temp.ex(11))->'open_discomfort', 'false'::jsonb, 'no discomfort is closed');
select is(pg_temp.el((select s3 from s), pg_temp.ex(1))->'open_discomfort', 'false'::jsonb, 'B''s discomfort is not A''s');

-- ---------------------------------------------------------------------------
-- Isolation and not-found
-- ---------------------------------------------------------------------------
select is(pg_temp.cmp((select live from s)), '[]'::jsonb, 'an in-progress session gives []');
select is(pg_temp.cmp(gen_random_uuid()), '[]'::jsonb, 'an unknown session gives []');
select is(pg_temp.cmp((select b2 from s)), '[]'::jsonb, 'another user''s session gives [] to the caller');
select pg_temp.as_user(2);
select is(pg_temp.cmp((select s3 from s)), '[]'::jsonb, 'user B gets [] for user A''s session');
select is(pg_temp.el((select b2 from s), pg_temp.ex(1))->'previous'->'sets'->0->>'weight_kg', '200.00', 'B''s previous is B''s own session');
select set_config('request.jwt.claims', '', true);
select is(pg_temp.cmp((select s3 from s)), '[]'::jsonb, 'no JWT gives []');

-- ---------------------------------------------------------------------------
-- Helper: parity with the weekly report rows, several plans
-- ---------------------------------------------------------------------------
create temp table pids as select (select id from public.coach_plans where user_id = pg_temp.u(1)) as p1;
grant select on pids to public;
select is_empty($$
  (select * from public.coach_progress_sessions_for(pg_temp.u(1), (select p1 from pids), date '2026-03-30')
   except select * from public.coach_user_sets_for(pg_temp.u(1), date '2026-03-02', date '2026-03-30', (select p1 from pids)))
  union all
  (select * from public.coach_user_sets_for(pg_temp.u(1), date '2026-03-02', date '2026-03-30', (select p1 from pids))
   except select * from public.coach_progress_sessions_for(pg_temp.u(1), (select p1 from pids), date '2026-03-30'))
$$, 'the weekly report rows equal the helper rows for the same plan and range');
select ok((select count(*) from public.coach_progress_sessions_for(pg_temp.u(1), (select p1 from pids), date '2026-03-30')) > 0,
  'the parity fixture is not empty');
select is((select count(*)::int from public.coach_progress_sessions_for(pg_temp.u(1), (select id from public.coach_plans where user_id = pg_temp.u(2)), date '2026-03-30')),
  0, 'the weekly report rows of another user''s plan stay empty');
select is((select count(*)::int from public.coach_user_sets_for(pg_temp.u(1), date '2026-03-02', date '2026-03-30')),
  (select count(*)::int from public.coach_user_sets_for(pg_temp.u(1), date '2026-03-02', date '2026-03-30', (select p1 from pids))),
  'with no plan given the single plan of the user decides, as with it given');
select is((select deload from public.coach_user_sets_for(pg_temp.u(3), date '2026-03-02', date '2026-03-30') limit 1), false,
  'several plans: the most recently accepted plan decides the deload flag (the older plan flagged week 1)');
select is((select week from public.coach_user_sets_for(pg_temp.u(3), date '2026-03-02', date '2026-03-30') limit 1), 1,
  'and the plan week');
select is((select deload from public.coach_user_sets_for(pg_temp.u(4), date '2026-03-02', date '2026-03-30') limit 1), false,
  'a proposed plan never decides a deload');
select is((select week from public.coach_user_sets_for(pg_temp.u(4), date '2026-03-02', date '2026-03-30') limit 1), null::int,
  'and gives no plan week');

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
select is(has_function_privilege('anon', 'public.session_comparison(uuid)', 'execute'), false, 'anon cannot execute session_comparison');
select is(has_function_privilege('authenticated', 'public.session_comparison(uuid)', 'execute'), true, 'authenticated can execute it');
select is((select array_agg(f order by f) from unnest(array[
             'public.coach_user_sets_for(uuid, date, date, uuid)',
             'public.coach_progress_sessions_for(uuid, uuid, date)']) f
           where has_function_privilege('authenticated', f, 'execute')
              or has_function_privilege('anon', f, 'execute')
              or has_function_privilege('public', f, 'execute')), null::text[],
  'the helper and the weekly-report twin are executable by nobody');

select * from finish();
rollback;
