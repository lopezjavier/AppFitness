-- pgTAP: the weekly progress builder, run A sections (T-313, US-141): strength, lifts, records, context, the composer,
-- isolation and grants. Rules: planning/07-coach-design.md section 3 "Weekly report: progress first" (1)-(3);
-- contract: tasks/todo.md "## T-313".
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(95);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; every builder function is internal)
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9313000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
-- Day k of every block (block_start = day 0, a Monday). Plan week w is days 7(w-1) .. 7w-1: every fixture trains in
-- every week up to its report week, so no window is paused.
create function pg_temp.d(k int) returns date language sql as $$ select date '2026-03-02' + k $$;
create function pg_temp.ts(k int, h numeric default 12) returns timestamptz language sql as $$
  select (pg_temp.d(k)::timestamp + make_interval(secs => h * 3600)) at time zone 'UTC' $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) $$;
create function pg_temp.iso(i int) returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and counts_as_strength and movement_pattern = 'isolation'
  order by key offset i - 1 limit 1 $$;
create function pg_temp.main(i int) returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and counts_as_strength
    and movement_pattern in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull')
  order by key offset i - 1 limit 1 $$;
create function pg_temp.non_h1() returns uuid language sql as $$
  select id from public.exercises where not is_custom and not counts_as_strength order by key limit 1 $$;

-- An accepted 4-week plan with one plan session a week (deload weeks given).
create function pg_temp.mkplan(n int, deload int[] default '{}') returns void language plpgsql as $$
declare v_plan uuid := gen_random_uuid();
begin
  insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d(0), 4, 'muscle', '{"rate_kg_week": 0}', 'accepted', now());
  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, deload)
  select v_plan, pg_temp.u(n), k, 1, 'Sesion', '[]', k = any (deload) from generate_series(1, 4) k;
end $$;

-- A completed workout on day k with one exercise; sets = [[kg, reps, rpe?, set_type?], ...] in set order; the rep range
-- is saved on every set.
create function pg_temp.sess(n int, k int, ex uuid, sets jsonb, rmin int default null, rmax int default null)
returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(n), 'W', 'completed', pg_temp.ts(k, 10), pg_temp.ts(k, 11));
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, rpe,
                                   rep_min, rep_max, completed_at)
  select v, ex, 0, s.i, coalesce(s.e->>3, 'working')::public.set_type, (s.e->>0)::numeric, (s.e->>1)::int,
         (s.e->>2)::numeric, rmin, rmax, pg_temp.ts(k, 10.1)
  from jsonb_array_elements(sets) with ordinality s(e, i);
  return v;
end $$;

create function pg_temp.disc(n int, ex uuid, lvl int, logged timestamptz, resolved timestamptz default null)
returns uuid language sql as $$
  insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at, resolved_at)
  values (pg_temp.u(n), ex, lvl, logged, resolved) returning id $$;

-- Section calls for plan week wk of user n.
create function pg_temp.str(n int, wk int) returns jsonb language sql as $$
  select public.coach_progress_strength_for(pg_temp.u(n), pg_temp.plan_of(n), wk, pg_temp.d(7 * (wk - 1)), pg_temp.d(7 * wk - 1)) $$;
create function pg_temp.ex_row(n int, wk int, ex uuid) returns jsonb language sql as $$
  select x from jsonb_array_elements(pg_temp.str(n, wk)->'groups') g, jsonb_array_elements(g->'exercises') x
  where x->>'exercise_id' = ex::text $$;
create function pg_temp.st(n int, wk int, ex uuid) returns text language sql as $$
  select pg_temp.ex_row(n, wk, ex)->>'state' $$;
create function pg_temp.lifts(n int, wk int) returns jsonb language sql as $$
  select public.coach_progress_lifts_for(pg_temp.u(n), pg_temp.plan_of(n), wk, pg_temp.d(7 * (wk - 1)), pg_temp.d(7 * wk - 1)) $$;
create function pg_temp.lift(n int, wk int, ex uuid) returns jsonb language sql as $$
  select x from jsonb_array_elements(pg_temp.lifts(n, wk)) x where x->>'exercise_id' = ex::text $$;
create function pg_temp.recs(n int, wk int) returns jsonb language sql as $$
  select public.coach_progress_records_for(pg_temp.u(n), pg_temp.plan_of(n), wk, pg_temp.d(7 * (wk - 1)), pg_temp.d(7 * wk - 1)) $$;
create function pg_temp.ctx(n int, wk int) returns jsonb language sql as $$
  select public.coach_progress_context_for(pg_temp.u(n), pg_temp.plan_of(n), wk, pg_temp.d(7 * (wk - 1)), pg_temp.d(7 * wk - 1)) $$;
create function pg_temp.prog(n int, plan uuid, wk int) returns jsonb language sql as $$
  select public.coach_week_progress_for(pg_temp.u(n), plan, wk, pg_temp.d(7 * (wk - 1)), pg_temp.d(7 * wk - 1)) $$;

insert into auth.users (id, email)
select pg_temp.u(n), 'progress' || n || '@example.com' from generate_series(1, 6) n;

-- ---------------------------------------------------------------------------
-- User 1: strength, report week 3 (days 14-20). Baselines on day 3 (week 1), compared sessions on day 15 unless said.
-- User 2: user B, same exercises and dates with heavier loads (200 kg) and her own discomforts.
-- ---------------------------------------------------------------------------
select pg_temp.mkplan(1);
select pg_temp.mkplan(2);

select pg_temp.sess(1, 3, pg_temp.iso(1), '[[80,8],[80,8],[80,8]]', 6, 10);                 -- up by load
select pg_temp.sess(1, 15, pg_temp.iso(1), '[[82.5,6],[82.5,6],[82.5,6]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(2), '[[80,8],[80,8],[80,8]]', 6, 10);                 -- more load, a set under rep_min
select pg_temp.sess(1, 15, pg_temp.iso(2), '[[82.5,6],[82.5,6],[82.5,5]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(3), '[[80,8],[80,7],[80,6]]');                        -- no range: floor 6
select pg_temp.sess(1, 15, pg_temp.iso(3), '[[82.5,6],[82.5,6],[82.5,6]]');
select pg_temp.sess(1, 3, pg_temp.iso(4), '[[80,8],[80,7],[80,6]]');                        -- no range, under the floor
select pg_temp.sess(1, 15, pg_temp.iso(4), '[[82.5,6],[82.5,5]]');
select pg_temp.sess(1, 3, pg_temp.iso(5), '[[80,8],[80,8],[80,8],[80,8]]', 6, 10);          -- 4 sets vs 3: 25 > 24
select pg_temp.sess(1, 15, pg_temp.iso(5), '[[80,9],[80,9],[80,7]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(6), '[[80,8],[80,8],[80,8]]', 6, 10);                 -- down by load
select pg_temp.sess(1, 15, pg_temp.iso(6), '[[77.5,10],[77.5,10],[77.5,10]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(7), '[[80,8],[80,8],[80,8]]', 6, 10);                 -- down by reps
select pg_temp.sess(1, 15, pg_temp.iso(7), '[[80,8],[80,8],[80,7]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(8), '[[80,8],[80,8],[80,8]]', 6, 10);                 -- same
select pg_temp.sess(1, 15, pg_temp.iso(8), '[[80,8],[80,8],[80,8]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(9), '[[80,8,7],[80,8,7]]', 6, 10);                    -- RPE +2
select pg_temp.sess(1, 15, pg_temp.iso(9), '[[82.5,8,9],[82.5,8,9]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(10), '[[80,8,7],[80,8,7]]', 6, 10);                   -- RPE +1
select pg_temp.sess(1, 15, pg_temp.iso(10), '[[82.5,8,8],[82.5,8,8]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(11), '[[80,8],[80,8]]', 6, 10);                       -- RPE missing on the baseline
select pg_temp.sess(1, 15, pg_temp.iso(11), '[[82.5,8,10],[82.5,8,10]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(12), '[[80,8],[80,8],[80,8]]', 6, 10);                -- best of three sessions
select pg_temp.sess(1, 15, pg_temp.iso(12), '[[82.5,8],[82.5,8],[82.5,8]]', 6, 10);
select pg_temp.sess(1, 16, pg_temp.iso(12), '[[82.5,6],[82.5,6],[82.5,6]]', 6, 10);
select pg_temp.sess(1, 17, pg_temp.iso(12), '[[80,10],[80,10],[80,10]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(13), '[[80,8]]', 6, 10);                              -- range change on day 8
select pg_temp.sess(1, 8, pg_temp.iso(13), '[[70,12]]', 8, 12);
select pg_temp.sess(1, 15, pg_temp.iso(13), '[[72.5,10]]', 8, 12);
select pg_temp.sess(1, 1, pg_temp.iso(14), '[[80,8]]');                                     -- 14-day gap: reset
select pg_temp.sess(1, 15, pg_temp.iso(14), '[[70,8]]');
select pg_temp.sess(1, 2, pg_temp.iso(15), '[[80,8]]');                                     -- 13-day gap: no reset
select pg_temp.sess(1, 15, pg_temp.iso(15), '[[70,8]]');
select pg_temp.sess(1, 8, pg_temp.iso(16), '[[50,10]]', 8, 12);                             -- swapped in on day 8
select pg_temp.sess(1, 15, pg_temp.iso(16), '[[52.5,10]]', 8, 12);
select pg_temp.sess(1, 3, pg_temp.iso(17), '[[80,8],[80,8],[80,8]]', 6, 10);                -- other set types ignored
select pg_temp.sess(1, 15, pg_temp.iso(17),
  '[[100,5,null,"warm_up"],[80,8],[80,8],[80,8],[90,8,null,"drop"],[85,10,null,"failure"]]', 6, 10);
select pg_temp.sess(1, 15, pg_temp.iso(18), '[[60,10]]');                                   -- only one session
select pg_temp.sess(1, 3, pg_temp.iso(19), '[[80,8]]');                                     -- not trained in week 3
select pg_temp.sess(1, 9, pg_temp.iso(19), '[[82.5,8]]');
select pg_temp.sess(1, 3, pg_temp.iso(28), '[[80,8]]');                                     -- carried, trained after p_end
select pg_temp.sess(1, 9, pg_temp.iso(28), '[[82.5,8]]');
select pg_temp.sess(1, 22, pg_temp.iso(28), '[[100,8]]');
select pg_temp.sess(1, 15, pg_temp.non_h1(), '[[20,10]]');                                  -- does not count for H1
-- Discomforts (the week ends at day 21 00:00 UTC).
select pg_temp.sess(1, 3, pg_temp.iso(20), '[[80,8],[80,8],[80,8]]', 6, 10);                -- same + level 2 within 7 days
select pg_temp.sess(1, 15, pg_temp.iso(20), '[[80,8],[80,8],[80,8]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(20), 2, pg_temp.ts(19));
select pg_temp.sess(1, 3, pg_temp.iso(21), '[[80,8],[80,8],[80,8]]', 6, 10);                -- down + level 5 unresolved, old
select pg_temp.sess(1, 15, pg_temp.iso(21), '[[77.5,8],[77.5,8],[77.5,8]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(21), 5, pg_temp.ts(5));
select pg_temp.sess(1, 3, pg_temp.iso(22), '[[80,8],[80,8],[80,8]]', 6, 10);                -- up + level 2
select pg_temp.sess(1, 15, pg_temp.iso(22), '[[82.5,6],[82.5,6],[82.5,6]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(22), 2, pg_temp.ts(19));
select pg_temp.sess(1, 3, pg_temp.iso(23), '[[80,8]]', 6, 10);                              -- level 2 exactly 7 days old
select pg_temp.sess(1, 15, pg_temp.iso(23), '[[80,8]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(23), 2, pg_temp.ts(14, 0));
select pg_temp.sess(1, 3, pg_temp.iso(24), '[[80,8]]', 6, 10);                              -- resolved after the week
select pg_temp.sess(1, 15, pg_temp.iso(24), '[[80,8]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(24), 2, pg_temp.ts(18), pg_temp.ts(22));
select pg_temp.sess(1, 3, pg_temp.iso(25), '[[80,8]]', 6, 10);                              -- two open rows
select pg_temp.sess(1, 15, pg_temp.iso(25), '[[80,8]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(25), 2, pg_temp.ts(16));
select pg_temp.disc(1, pg_temp.iso(25), 3, pg_temp.ts(18));
select pg_temp.sess(1, 15, pg_temp.iso(26), '[[60,10]]');                                   -- one session + level 1
select pg_temp.disc(1, pg_temp.iso(26), 1, pg_temp.ts(17));
select pg_temp.sess(1, 3, pg_temp.iso(27), '[[80,8]]', 6, 10);                              -- rows after the week
select pg_temp.sess(1, 15, pg_temp.iso(27), '[[80,8]]', 6, 10);
select pg_temp.sess(1, 22, pg_temp.iso(27), '[[100,8]]', 6, 10);
select pg_temp.disc(1, pg_temp.iso(27), 2, pg_temp.ts(22));
-- Main lifts: the stall test (weeks 1-3 flat or below week 1).
select pg_temp.sess(1, 3, pg_temp.main(1), '[[100,5]]');                                    -- flat: stalled
select pg_temp.sess(1, 9, pg_temp.main(1), '[[100,5]]');
select pg_temp.sess(1, 15, pg_temp.main(1), '[[100,5]]');
select pg_temp.sess(1, 3, pg_temp.main(2), '[[100,5]]');                                    -- up vs baseline, stalled
select pg_temp.sess(1, 4, pg_temp.main(2), '[[90,12]]');
select pg_temp.sess(1, 9, pg_temp.main(2), '[[100,5]]');
select pg_temp.sess(1, 15, pg_temp.main(2), '[[102.5,5]]');
select pg_temp.sess(1, 3, pg_temp.main(3), '[[100,5]]');                                    -- same, plus an open discomfort
select pg_temp.sess(1, 4, pg_temp.main(3), '[[90,12]]');
select pg_temp.sess(1, 9, pg_temp.main(3), '[[100,5]]');
select pg_temp.sess(1, 15, pg_temp.main(3), '[[102.5,5]]');
select pg_temp.disc(1, pg_temp.main(3), 2, pg_temp.ts(19));
select pg_temp.sess(1, 3, pg_temp.main(4), '[[100,5]]');                                    -- flat + an open discomfort
select pg_temp.sess(1, 9, pg_temp.main(4), '[[100,5]]');
select pg_temp.sess(1, 15, pg_temp.main(4), '[[100,5]]');
select pg_temp.disc(1, pg_temp.main(4), 2, pg_temp.ts(19));

-- User B: heavier sessions and her own discomforts on A's exercises, and an exercise only she trains.
select pg_temp.sess(2, 3, pg_temp.iso(8), '[[200,8],[200,8],[200,8]]', 6, 10);
select pg_temp.sess(2, 15, pg_temp.iso(8), '[[210,8],[210,8],[210,8]]', 6, 10);
select pg_temp.disc(2, pg_temp.iso(8), 5, pg_temp.ts(10));
select pg_temp.disc(2, pg_temp.iso(1), 2, pg_temp.ts(19));
select pg_temp.sess(2, 15, pg_temp.iso(30), '[[200,8]]');
select pg_temp.sess(2, 9, pg_temp.iso(1), '[[200,8]]');

-- ---------------------------------------------------------------------------
-- Strength: comparison
-- ---------------------------------------------------------------------------
select is(pg_temp.st(1, 3, pg_temp.iso(1)), 'up', 'more load with every set at or above rep_min is up');
select is(pg_temp.st(1, 3, pg_temp.iso(2)), 'same', 'more load with a set under rep_min is same');
select is(pg_temp.st(1, 3, pg_temp.iso(3)), 'up', 'without a range, more load at the baseline''s lowest reps is up');
select is(pg_temp.st(1, 3, pg_temp.iso(4)), 'same', 'without a range, more load under the baseline''s lowest reps is same');
select is(pg_temp.st(1, 3, pg_temp.iso(5)), 'up', 'same load: reps compared over the shared sets (25 > 24 over 3 sets) is up');
select is(pg_temp.st(1, 3, pg_temp.iso(6)), 'down', 'less load is down');
select is(pg_temp.st(1, 3, pg_temp.iso(7)), 'down', 'same load with fewer reps is down');
select is(pg_temp.st(1, 3, pg_temp.iso(8)), 'same', 'same load and reps is same');
select is(pg_temp.st(1, 3, pg_temp.iso(9)), 'same', 'a load increase at RPE +2 is same');
select is(pg_temp.st(1, 3, pg_temp.iso(10)), 'up', 'a load increase at RPE +1 is still up');
select is(pg_temp.st(1, 3, pg_temp.iso(11)), 'up', 'RPE missing on one side: judged on load and reps');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(12))->'best',
  jsonb_build_object('date', pg_temp.d(15), 'load_kg', 82.5, 'reps', '[8,8,8]'::jsonb, 'rpe', null),
  'the compared session is the week''s highest top load, ties broken by reps at it (not by recency)');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(1))->'baseline',
  jsonb_build_object('date', pg_temp.d(3), 'load_kg', 80, 'reps', '[8,8,8]'::jsonb, 'rpe', null),
  'the baseline carries date, top working load, the reps at it in set order and the RPE');
select is((pg_temp.ex_row(1, 3, pg_temp.iso(10))->'best'->>'rpe')::numeric, 8.0,'the RPE is the highest at the top load');

-- ---------------------------------------------------------------------------
-- Strength: baseline and compared session
-- ---------------------------------------------------------------------------
select is(pg_temp.ex_row(1, 3, pg_temp.iso(13))->'baseline'->>'date', pg_temp.d(8)::text,
  'a rep range change resets the baseline to that session');
select is(pg_temp.st(1, 3, pg_temp.iso(13)), 'up', 'and the week is compared with the new baseline');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(14))->'baseline'->>'date', pg_temp.d(15)::text,
  'a gap of exactly 14 days resets the baseline');
select is(pg_temp.st(1, 3, pg_temp.iso(14)), 'uncompared', 'so the reset session has nothing to compare with');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(15))->'baseline'->>'date', pg_temp.d(2)::text, 'a 13-day gap keeps the baseline');
select is(pg_temp.st(1, 3, pg_temp.iso(15)), 'down', 'and the week is compared with it');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(16))->'baseline'->>'date', pg_temp.d(8)::text,
  'an exercise swapped in mid-block has its own baseline');
select is(pg_temp.st(1, 3, pg_temp.iso(17)), 'same', 'warm-up, drop and failure sets are ignored');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(17))->'best'->'reps', '[8,8,8]'::jsonb, 'only the working sets carry the reps');
select is(pg_temp.st(1, 3, pg_temp.iso(18)), 'uncompared', 'one session in the block is uncompared');
select ok(pg_temp.ex_row(1, 3, pg_temp.iso(18))->'best' = 'null'::jsonb, 'an uncompared exercise has no best');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(19))->'best'->>'date', pg_temp.d(9)::text,
  'not trained in the report week: compared on its latest session of the block, with that date');
select is(pg_temp.st(1, 3, pg_temp.iso(19)), 'up', 'and the carried comparison gives a state');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(28))->'best'->>'date', pg_temp.d(9)::text,
  'a carried row never takes a session after the week''s end');
select ok(pg_temp.ex_row(1, 3, pg_temp.non_h1()) is null, 'an exercise that does not count for H1 has no row');
select is((select count(*)::int
           from jsonb_array_elements(pg_temp.str(1, 3)->'groups') g, jsonb_array_elements(g->'exercises') x
           where x->>'exercise_id' = pg_temp.main(1)::text
             and g->>'muscle_group' = (select muscle_group::text from public.exercises where id = pg_temp.main(1))), 1,
  'an exercise is listed once, under its main muscle only (secondary muscles carry nothing)');
select is(pg_temp.str(1, 3)->'block_week', '3'::jsonb, 'block_week is the report week');

-- ---------------------------------------------------------------------------
-- Strength: stalled and paused (state order)
-- ---------------------------------------------------------------------------
select is(pg_temp.st(1, 3, pg_temp.main(1)), 'stalled', 'a main lift with the stall test holding is stalled');
select is(pg_temp.st(1, 3, pg_temp.main(2)), 'stalled', 'the stall test overrides an up comparison');
select is(pg_temp.st(1, 3, pg_temp.main(3)), 'paused', 'up + stall + open discomfort is paused (a lift with an open discomfort never reads stalled)');
select is(pg_temp.ex_row(1, 3, pg_temp.main(3))->>'discomfort_id',
  (select id::text from public.exercise_discomforts where user_id = pg_temp.u(1) and exercise_id = pg_temp.main(3)
   order by logged_at desc limit 1),
  'and carries its discomfort_id');
select is(pg_temp.st(1, 3, pg_temp.main(4)), 'paused', 'an open discomfort beats the stall test when the comparison is not up');
select is(pg_temp.st(1, 3, pg_temp.iso(20)), 'paused', 'a level 2 discomfort within 7 days turns same into paused');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(20))->>'discomfort_id',
  (select id::text from public.exercise_discomforts where user_id = pg_temp.u(1) and exercise_id = pg_temp.iso(20)),
  'the paused row carries the discomfort id');
select is(pg_temp.st(1, 3, pg_temp.iso(21)), 'paused', 'an unresolved level 5 older than 7 days turns down into paused');
select is(pg_temp.st(1, 3, pg_temp.iso(22)), 'up', 'an open discomfort does not replace up');
select ok(pg_temp.ex_row(1, 3, pg_temp.iso(22))->'discomfort_id' = 'null'::jsonb, 'an up row carries no discomfort_id');
select is(pg_temp.st(1, 3, pg_temp.iso(23)), 'same', 'a level 2 discomfort exactly 7 days old is no longer open');
select is(pg_temp.st(1, 3, pg_temp.iso(24)), 'paused', 'a discomfort resolved after the week''s end is open as of it');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(25))->>'discomfort_id',
  (select id::text from public.exercise_discomforts where user_id = pg_temp.u(1) and exercise_id = pg_temp.iso(25)
   order by logged_at desc limit 1),
  'with two open rows the newest one is carried');
select is(pg_temp.st(1, 3, pg_temp.iso(26)), 'paused', 'an open discomfort replaces uncompared');

-- ---------------------------------------------------------------------------
-- Strength: week 1 and deload weeks (user 3: week 2 is a deload week)
-- ---------------------------------------------------------------------------
select is((select count(*)::int
           from jsonb_array_elements(pg_temp.str(1, 1)->'groups') g, jsonb_array_elements(g->'exercises') x
           where x->>'state' not in ('uncompared', 'paused') or x->'best' <> 'null'::jsonb), 0,
  'week 1 is the base: nothing is compared');
select is(pg_temp.ex_row(1, 1, pg_temp.iso(1))->'baseline'->>'date', pg_temp.d(3)::text, 'and week 1 shows the baseline');

select pg_temp.mkplan(3, '{2}');
select pg_temp.sess(3, 9, pg_temp.iso(1), '[[60,8]]');                                      -- first logged in the deload
select pg_temp.sess(3, 15, pg_temp.iso(1), '[[80,8]]');
select pg_temp.sess(3, 3, pg_temp.iso(2), '[[80,8]]');                                      -- deload session not compared
select pg_temp.sess(3, 5, pg_temp.iso(2), '[[82.5,8]]');
select pg_temp.sess(3, 10, pg_temp.iso(2), '[[100,8]]');
select pg_temp.sess(3, 9, pg_temp.iso(3), '[[60,8]]');                                      -- only deload sessions
select pg_temp.sess(3, 15, pg_temp.iso(4), '[[60,8]]');                                     -- week 3 trained
select pg_temp.sess(3, 1, pg_temp.main(1), '[[100,1]]');                                    -- lift in weeks 1, 2 (deload), 3
select pg_temp.sess(3, 8, pg_temp.main(1), '[[110,1]]');
select pg_temp.sess(3, 15, pg_temp.main(1), '[[120,1]]');

select is(pg_temp.ex_row(3, 3, pg_temp.iso(1))->'baseline'->>'date', pg_temp.d(15)::text, 'a deload session is never the baseline');
select is(pg_temp.ex_row(3, 3, pg_temp.iso(2))->'best'->>'date', pg_temp.d(5)::text,
  'a deload session is never compared: the latest non-deload session is');
select ok(pg_temp.ex_row(3, 3, pg_temp.iso(3)) is null, 'an exercise with only deload sessions has no row');
select is(pg_temp.ex_row(3, 2, pg_temp.iso(2))->'best'->>'date', pg_temp.d(5)::text,
  'in a deload report week the latest non-deload session of the block is compared');

-- ---------------------------------------------------------------------------
-- Main lifts (user 4: week 2 is a deload week, report week 4; reps 1, so e1RM = load)
-- ---------------------------------------------------------------------------
select pg_temp.mkplan(4, '{2}');
select pg_temp.sess(4, 1, pg_temp.main(1), '[[100,1]]');                                    -- up: +4 %
select pg_temp.sess(4, 8, pg_temp.main(1), '[[80,1]]');
select pg_temp.sess(4, 15, pg_temp.main(1), '[[102,1]]');
select pg_temp.sess(4, 22, pg_temp.main(1), '[[104,1]]');
select pg_temp.sess(4, 1, pg_temp.main(2), '[[128,1]]');                                    -- margin: +3,9 %
select pg_temp.sess(4, 15, pg_temp.main(2), '[[130,1]]');
select pg_temp.sess(4, 22, pg_temp.main(2), '[[133,1]]');
select pg_temp.sess(4, 1, pg_temp.main(3), '[[100,1]]');                                    -- stalled
select pg_temp.sess(4, 15, pg_temp.main(3), '[[100,1]]');
select pg_temp.sess(4, 22, pg_temp.main(3), '[[100,1]]');
select pg_temp.sess(4, 15, pg_temp.main(4), '[[100,1]]');                                   -- 2 counted weeks
select pg_temp.sess(4, 22, pg_temp.main(4), '[[105,1]]');
select pg_temp.sess(4, 1, pg_temp.main(5), '[[90,1]]');
select pg_temp.sess(4, 15, pg_temp.main(5), '[[90,1]]');
select pg_temp.sess(4, 22, pg_temp.main(5), '[[90,1]]');
select pg_temp.sess(4, 15, pg_temp.main(6), '[[90,1]]');
select pg_temp.sess(4, 22, pg_temp.main(6), '[[90,1]]');
select pg_temp.sess(4, 22, pg_temp.main(7), '[[90,1]]');                                    -- fewest sessions: 7th

select is(pg_temp.lift(4, 4, pg_temp.main(1))->>'state', 'up', 'improving with the latest week 4 % above the first is up');
select is(pg_temp.lift(4, 4, pg_temp.main(1))->'change_kg', '4'::jsonb, 'change_kg = latest - first counted week');
select is(pg_temp.lift(4, 4, pg_temp.main(1))->'change_pct', '4.0'::jsonb, 'change_pct has one decimal');
select is(pg_temp.lift(4, 4, pg_temp.main(1))->'weeks'->1, '{"week": 2, "e1rm_kg": 80, "deload": true}'::jsonb,
  'a deload week is marked and keeps its point');
select is(pg_temp.lift(4, 4, pg_temp.main(2))->>'state', 'margin', 'improving by 3,9 % is margin');
select is(pg_temp.lift(4, 4, pg_temp.main(2))->'change_pct', '3.9'::jsonb, 'and the margin carries its amount');
select is(pg_temp.lift(4, 4, pg_temp.main(3))->>'state', 'stalled', 'the stall test gives stalled');
select ok(pg_temp.lift(4, 4, pg_temp.main(3))->'change_kg' = 'null'::jsonb, 'a stalled lift has no change');
select is(pg_temp.lift(4, 4, pg_temp.main(4))->>'state', 'provisional', 'fewer than 3 counted weeks is provisional');
select is(pg_temp.lift(3, 3, pg_temp.main(1))->>'state', 'provisional', 'a deload week is not a counted week');
select is(pg_temp.lift(1, 3, pg_temp.main(1))->>'state', 'stalled', 'a stalled lift with no open discomfort is stalled');
select is(pg_temp.lift(1, 3, pg_temp.main(3))->>'state', 'margin',
  'a lift with an open discomfort never reads stalled: the stall test result falls back to margin');
select is(pg_temp.lift(4, 4, pg_temp.main(4))->'weeks', '[{"week": 1, "e1rm_kg": null, "deload": false},
  {"week": 2, "e1rm_kg": null, "deload": true}, {"week": 3, "e1rm_kg": 100, "deload": false},
  {"week": 4, "e1rm_kg": 105, "deload": false}]'::jsonb, 'weeks 1..p_week, null where the lift was not done');
select is(jsonb_array_length(pg_temp.lifts(4, 4)), 6, 'at most 6 lifts');
select is((select array_agg((x->>'exercise_id')::uuid order by o) from jsonb_array_elements(pg_temp.lifts(4, 4)) with ordinality t(x, o)),
  array[pg_temp.main(1), pg_temp.main(2), pg_temp.main(3), pg_temp.main(5), pg_temp.main(4), pg_temp.main(6)],
  'ordered by sessions in the block, then key; the lift with fewest sessions is left out');
select is(pg_temp.lift(4, 2, pg_temp.main(1))->'weeks', '[{"week": 1, "e1rm_kg": 100, "deload": false},
  {"week": 2, "e1rm_kg": 80, "deload": true}]'::jsonb, 'an earlier report week sees only its weeks');

-- ---------------------------------------------------------------------------
-- Records (user 5, report week 3 = days 14-20 and week 4 = days 21-27)
-- ---------------------------------------------------------------------------
select pg_temp.mkplan(5);
select pg_temp.sess(5, -30, pg_temp.main(1), '[[100,1]]');                                  -- history before the block
select pg_temp.sess(5, 3, pg_temp.main(1), '[[105,1]]');
select pg_temp.sess(5, 15, pg_temp.main(1), '[[110,1]]');
select pg_temp.sess(5, 22, pg_temp.main(1), '[[120,1]]');                                   -- beats week 3's record later
select pg_temp.sess(5, 3, pg_temp.main(2), '[[85,1]]');
select pg_temp.sess(5, 15, pg_temp.main(2), '[[90,1]]');
select pg_temp.sess(5, 17, pg_temp.main(2), '[[95,1]]');                                    -- two record sessions in the week
select pg_temp.sess(5, 3, pg_temp.iso(2), '[[100,1]]');                                     -- tie in week 2
select pg_temp.sess(5, 10, pg_temp.iso(2), '[[100,1]]');
select pg_temp.sess(5, 3, pg_temp.iso(3), '[[50,1]]');                                      -- largest gain, not a main lift
select pg_temp.sess(5, 16, pg_temp.iso(3), '[[60,1]]');
select pg_temp.sess(5, 9, pg_temp.iso(4), '[[80,10]]');                                     -- e1RM 106,5
select pg_temp.sess(5, 22, pg_temp.iso(4), '[[85,3]]');                                     -- heaviest only (e1RM 93,5)

select is(jsonb_array_length(pg_temp.recs(5, 3)), 3, 'at most 3 records');
select is(pg_temp.recs(5, 3)->0,
  jsonb_build_object('exercise_id', pg_temp.main(2), 'kind', 'e1rm', 'value_kg', 95, 'previous_kg', 85, 'main_lift', true),
  'value = the week''s best record, previous = the best before the week''s first record session');
select is((select array_agg((x->>'exercise_id')::uuid || ':' || (x->>'kind') order by o)
           from jsonb_array_elements(pg_temp.recs(5, 3)) with ordinality t(x, o)),
  array[pg_temp.main(2) || ':e1rm', pg_temp.main(2) || ':heaviest', pg_temp.main(1) || ':e1rm'],
  'main lifts first, then the largest relative gain (the +20 % isolation record is left out)');
select is(pg_temp.recs(5, 3)->2->'value_kg', '110'::jsonb, 'a record beaten after the week''s end is still listed');
select is(pg_temp.recs(5, 4)->0,
  jsonb_build_object('exercise_id', pg_temp.main(1), 'kind', 'e1rm', 'value_kg', 120, 'previous_kg', 110, 'main_lift', true),
  'each session is judged against the history before it, earlier records included');
select is((select array_agg(x->>'kind') from jsonb_array_elements(pg_temp.recs(5, 4)) x
           where x->>'exercise_id' = pg_temp.iso(4)::text), array['heaviest'],
  'the heaviest load is a record of its own kind when the e1RM is not');
select is(pg_temp.recs(5, 2), '[]'::jsonb,
  'the first session ever of an exercise and a tie are not records (week 2 has only those)');

-- ---------------------------------------------------------------------------
-- Context (user 6, plan weeks 1-3)
-- ---------------------------------------------------------------------------
select pg_temp.mkplan(6);
insert into public.routines (id, user_id, name) values
  ('c9313000-0000-4000-8000-0000000000a1', pg_temp.u(6), 'Rutina A'),
  ('c9313000-0000-4000-8000-0000000000a2', pg_temp.u(6), 'Rutina B');
insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps) values
  ('c9313000-0000-4000-8000-0000000000a1', pg_temp.iso(1), 0, 3, 8),
  ('c9313000-0000-4000-8000-0000000000a1', pg_temp.iso(2), 1, 3, 8),
  ('c9313000-0000-4000-8000-0000000000a2', pg_temp.iso(1), 0, 3, 8),
  ('c9313000-0000-4000-8000-0000000000a2', pg_temp.iso(3), 1, 3, 8);
-- Week 3: two plan sessions on routine A, one done (3 + 2 working, 1 drop, 1 warm-up). Week 2: routine B differs from
-- the plan. Week 1: no routine.
update public.coach_plan_sessions
  set routine_id = case week when 1 then null when 2 then 'c9313000-0000-4000-8000-0000000000a2'::uuid
                             else 'c9313000-0000-4000-8000-0000000000a1'::uuid end,
      exercises = jsonb_build_array(
        jsonb_build_object('exercise_id', pg_temp.iso(1), 'sets', 3),
        jsonb_build_object('exercise_id', pg_temp.iso(2), 'sets',
                           '[{"type":"working"},{"type":"working"},{"type":"warm_up"},{"type":"drop"}]'::jsonb))
  where user_id = pg_temp.u(6);
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
select plan_id, user_id, week, 2, 'Sesion 2', exercises, routine_id
from public.coach_plan_sessions where user_id = pg_temp.u(6) and week = 3;
-- Week 4: one session on routine A (matches) and one on routine B (differs).
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
select plan_id, user_id, week, 2, 'Sesion 2', exercises, 'c9313000-0000-4000-8000-0000000000a2'::uuid
from public.coach_plan_sessions where user_id = pg_temp.u(6) and week = 4;
-- Week 2's plan session is linked to a workout logged after week 2 (day 23).
update public.coach_plan_sessions set session_id = pg_temp.sess(6, 23, pg_temp.iso(1), '[[80,8]]')
where user_id = pg_temp.u(6) and week = 2;
create function pg_temp.mkctx() returns void language plpgsql as $$
declare v uuid := pg_temp.sess(6, 15, pg_temp.iso(1), '[[80,8],[80,8],[80,8],[40,10,null,"warm_up"]]');
begin
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
  values (v, pg_temp.iso(2), 1, 1, 'working', 30, 10, pg_temp.ts(15, 10.2)),
         (v, pg_temp.iso(2), 1, 2, 'working', 30, 10, pg_temp.ts(15, 10.2)),
         (v, pg_temp.iso(2), 1, 3, 'drop', 20, 10, pg_temp.ts(15, 10.2)),
         (v, pg_temp.iso(2), 1, 4, 'working', 30, 10, null);
  update public.coach_plan_sessions set session_id = v where user_id = pg_temp.u(6) and week = 3 and session_index = 1;
end $$;
select pg_temp.mkctx();
select pg_temp.sess(6, 3, pg_temp.iso(3), '[[50,8]]');
select pg_temp.sess(6, 9, pg_temp.iso(3), '[[50,8]]');
select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(6), 'role', 'authenticated')::text, true);

select is(jsonb_build_array(pg_temp.ctx(6, 3)->'sessions_done', pg_temp.ctx(6, 3)->'sessions_planned'),
  (select jsonb_build_array(w->'completed', w->'planned') from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(6), pg_temp.d(30))) w
   where (w->>'week')::int = 3),
  'sessions done and planned equal the v1 values');
select is(pg_temp.ctx(6, 3)->'sets_done', '6'::jsonb, 'sets done: completed non-warm-up sets of the linked sessions');
select is(pg_temp.ctx(6, 3)->'sets_planned', '12'::jsonb, 'sets planned: integer sets plus working and drop elements');
select ok(pg_temp.ctx(6, 2)->'sets_planned' = 'null'::jsonb, 'a routine whose exercises differ from the plan gives null');
select ok(pg_temp.ctx(6, 1)->'sets_planned' = 'null'::jsonb, 'a plan session without a routine gives null');
select ok(pg_temp.ctx(6, 4)->'sets_planned' = 'null'::jsonb, 'one non-matching session among matching ones gives null');
select is(jsonb_build_array(pg_temp.ctx(6, 2)->'sessions_done', pg_temp.ctx(6, 2)->'sets_done'), '[0, 0]'::jsonb,
  'a session linked to the week but logged after its end is not counted');

-- ---------------------------------------------------------------------------
-- Composer, isolation and grants
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '', true);
select is((select array_agg(k order by k) from jsonb_object_keys(pg_temp.prog(1, pg_temp.plan_of(1), 3)) k),
  array['as_of', 'context', 'final', 'intention', 'lifts', 'missing', 'phase', 'records', 'refreshed_at', 'strength',
        'waist', 'weight'],
  'the composer returns the progress keys, with no JWT');
select is(pg_temp.prog(1, pg_temp.plan_of(1), 3) - 'refreshed_at' - 'context' - 'strength' - 'lifts' - 'records' - 'weight',
  jsonb_build_object('as_of', pg_temp.d(20), 'final', false, 'intention', 'muscle',
                     'phase', (select coalesce(nutrition->>'phase', phase::text) from public.coach_plans where user_id = pg_temp.u(1)),
                     'missing', '["weigh_ins", "diary"]'::jsonb, 'waist', null),
  'the header: as of the week''s end, not final, the plan''s intention and calorie phase (no weigh-ins, no diary)');
select is(pg_temp.prog(1, pg_temp.plan_of(1), 3)->'strength', pg_temp.str(1, 3), 'the composer embeds the strength section');
select ok(pg_temp.prog(1, pg_temp.plan_of(2), 3) is null, 'another user''s plan gives null');
select ok(pg_temp.ex_row(1, 3, pg_temp.iso(30)) is null, 'an exercise only user B trains is not in A''s progress');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(1))->'best'->'load_kg', '82.5'::jsonb, 'B''s sessions are never A''s');
select is((select count(*)::int from jsonb_array_elements(pg_temp.recs(1, 3)) x where (x->>'value_kg')::numeric >= 200), 0,
  'B''s loads are never A''s records');
select is(pg_temp.ex_row(1, 3, pg_temp.iso(27))->'best'->>'date', pg_temp.d(15)::text, 'sessions after the week''s end are excluded');
select is(pg_temp.st(1, 3, pg_temp.iso(27)), 'same', 'and so are discomforts logged after it');

select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(1), 'role', 'authenticated')::text, true);
select is(array(select lift_id from public.coach_stalled_lifts_for(pg_temp.u(1), pg_temp.plan_of(1), 3) order by 1),
          array(select lift_id from public.coach_stalled_lifts(pg_temp.plan_of(1), 3) order by 1),
  'coach_stalled_lifts_for matches coach_stalled_lifts for the same user');
select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(4), 'role', 'authenticated')::text, true);
select is(array(select lift_id from public.coach_stalled_lifts_for(pg_temp.u(4), pg_temp.plan_of(4), 4) order by 1),
          array(select lift_id from public.coach_stalled_lifts(pg_temp.plan_of(4), 4) order by 1),
  'and with a deload week in the window');

select is((select array_agg(f order by f) from unnest(array[
             'public.coach_warm_up_included_for(uuid)',
             'public.coach_open_discomforts_for(uuid, date)',
             'public.coach_stalled_lifts_for(uuid, uuid, integer)',
             'public.coach_progress_sessions_for(uuid, uuid, date)',
             'public.coach_progress_strength_for(uuid, uuid, integer, date, date)',
             'public.coach_progress_lifts_for(uuid, uuid, integer, date, date)',
             'public.coach_progress_records_for(uuid, uuid, integer, date, date)',
             'public.coach_progress_context_for(uuid, uuid, integer, date, date)',
             'public.coach_week_progress_for(uuid, uuid, integer, date, date)']) f
           where has_function_privilege('authenticated', f, 'execute')
              or has_function_privilege('anon', f, 'execute')
              or has_function_privilege('public', f, 'execute')), null,
  'the builder and every new helper are not executable by authenticated, anon or public');
select is((select prosecdef from pg_proc where oid = 'public.coach_week_progress_for(uuid, uuid, integer, date, date)'::regprocedure),
  true, 'the composer is security definer');
select is((select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.prosecdef
             and p.proname in ('coach_warm_up_included_for', 'coach_open_discomforts_for', 'coach_stalled_lifts_for',
                               'coach_progress_sessions_for',
                               'coach_progress_strength_for', 'coach_progress_lifts_for', 'coach_progress_records_for',
                               'coach_progress_context_for')), 0,
  'every helper is security invoker');

select * from finish();
rollback;
