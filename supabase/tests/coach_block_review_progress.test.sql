-- pgTAP: the block review progress v2 (T-317, US-143; planning/07-coach-design.md section 3 "Other reports: progress
-- first" (A), ADR-111): schema_version 2 and `progress` built by the T-313 builders as of the block's last day, the
-- block weight section, the block records, the weight outcome, the frozen v1 keys, isolation and grants.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(85);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; the builders are internal)
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9317000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
-- Day k (day 0 is a Monday, so plan weeks of 7 days are ISO weeks). Every fixture trains in every plan week it has.
create function pg_temp.d(k int) returns date language sql as $$ select date '2026-03-02' + k $$;
create function pg_temp.ts(k int, h numeric default 12) returns timestamptz language sql as $$
  select (pg_temp.d(k)::timestamp + make_interval(secs => h * 3600)) at time zone 'UTC' $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;
create function pg_temp.iso(i int) returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and counts_as_strength and movement_pattern = 'isolation'
  order by key offset i - 1 limit 1 $$;
create function pg_temp.main(i int) returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and counts_as_strength
    and movement_pattern in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull')
  order by key offset i - 1 limit 1 $$;

-- An accepted plan of `weeks` plan weeks with one plan session a week (deload weeks given) in maintenance (the cut proposal reads today) with a -0.5 kg/week rate.
create function pg_temp.mkplan(n int, weeks int default 4, deload int[] default '{}', outcomes jsonb default '[]',
                               weeks_with_session int[] default null) returns void language plpgsql as $$
declare v_plan uuid := gen_random_uuid();
begin
  insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, phase, outcomes, status,
                                  accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d(0), weeks, 'fat_loss',
          '{"rate_kg_week": -0.5, "phase": "maintenance"}', 'maintenance', outcomes, 'accepted', now());
  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, deload)
  select v_plan, pg_temp.u(n), k, 1, 'Sesion', '[]', k = any (deload)
  from generate_series(1, weeks) k
  where weeks_with_session is null or k = any (weeks_with_session);
end $$;

-- A completed workout on day k with one exercise; sets = [[kg, reps, rpe?, set_type?], ...]; the rep range on every set.
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
-- Links the plan session of week w to a workout.
create function pg_temp.link(n int, w int, sid uuid) returns void language sql as $$
  update public.coach_plan_sessions set session_id = sid where plan_id = pg_temp.plan_of(n) and week = w $$;
-- A filler workout (no sets) on day k, so that the window counts as a trained one.
create function pg_temp.filler(n int, k int) returns void language sql as $$
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  values (pg_temp.u(n), 'F', 'completed', pg_temp.ts(k, 18), pg_temp.ts(k, 19)) $$;

create function pg_temp.weigh(n int, k int, kg numeric, h numeric default 8) returns void language sql as $$
  insert into public.body_measurements (user_id, measured_at, weight_kg, source)
  values (pg_temp.u(n), pg_temp.ts(k, h), kg, 'manual') $$;
create function pg_temp.girth(n int, k int, cm numeric) returns void language sql as $$
  insert into public.body_circumferences (user_id, measured_at, site, value_cm)
  values (pg_temp.u(n), pg_temp.ts(k, 8), 'waist', cm) $$;

-- The builder as user n (the live path runs it inside the definer RPCs, with the caller's claims).
create function pg_temp.build(n int, today date, p_end date default null) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.coach_block_review_build(pg_temp.plan_of(n), today, p_end);
end $$;
create function pg_temp.progress(n int, today date, p_end date default null) returns jsonb language sql as $$
  select pg_temp.build(n, today, p_end)->'inputs'->'progress' $$;

insert into auth.users (id, email)
select pg_temp.u(n), 'blockprogress' || n || '@example.com' from generate_series(1, 11) n;

-- ---------------------------------------------------------------------------
-- User 1: a 4-week block (days 0-27), deload week 4, trained in every week.
--   Main lifts (100 kg x 5 is the baseline in week 1): m1 +5 % (up), m2 +3.9 % (margin), m3 +4.5 % (up), m4 only two
--   counted weeks (provisional). Isolation: i1 up by load (the week-4 deload session is ignored, the week-3 session is
--   carried), i2 down, i3 one session (uncompared), i4 up by reps.
--   Weight: week 1 days 0-2 at 82 (3 days), week 2 day 8 at 81.5 (1 day), week 3 days 14-16 at 80.5, week 4 days 21-23
--   at 80.0, day 30 at 70 (after the block). Waist: 90 on day -5, 88 on day 25. Outcomes: weight 80 without a deadline,
--   e1RM of m1 100 with its own deadline.
-- User 2: user B, the same days with much heavier numbers.
-- ---------------------------------------------------------------------------
select pg_temp.mkplan(1, 4, array[4], jsonb_build_array(
  jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 80, 'unit', 'kg'),
  jsonb_build_object('kind', 'e1rm', 'key', (select key from public.exercises where id = pg_temp.main(1)),
                     'target_value', 100, 'unit', 'kg', 'deadline', '2026-04-15')));
select pg_temp.mkplan(2);
-- The plan's main lift (what the v1 lifts read) is m1.
update public.coach_plan_sessions
set exercises = jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.main(1), 'role', 'main', 'sets', 3))
where plan_id = pg_temp.plan_of(1) and week = 1;

select pg_temp.link(1, 1, pg_temp.sess(1, 3, pg_temp.main(1), '[[100,5]]'));
select pg_temp.link(1, 2, pg_temp.sess(1, 10, pg_temp.main(1), '[[100,5]]'));
select pg_temp.link(1, 3, pg_temp.sess(1, 15, pg_temp.main(1), '[[105,5]]'));
select pg_temp.link(1, 4, pg_temp.sess(1, 22, pg_temp.main(1), '[[80,5]]'));
select pg_temp.sess(1, 3, pg_temp.main(2), '[[100,5]]');
select pg_temp.sess(1, 10, pg_temp.main(2), '[[100,5]]');
select pg_temp.sess(1, 16, pg_temp.main(2), '[[103.9,5]]');
select pg_temp.sess(1, 3, pg_temp.main(3), '[[100,5]]');
select pg_temp.sess(1, 10, pg_temp.main(3), '[[100,5]]');
select pg_temp.sess(1, 17, pg_temp.main(3), '[[104.5,5]]');
select pg_temp.sess(1, 3, pg_temp.main(4), '[[100,5]]');
select pg_temp.sess(1, 10, pg_temp.main(4), '[[105,5]]');
select pg_temp.sess(1, 3, pg_temp.iso(1), '[[80,8],[80,8],[80,8]]', 6, 10);
select pg_temp.sess(1, 15, pg_temp.iso(1), '[[82.5,6],[82.5,6],[82.5,6]]', 6, 10);
select pg_temp.sess(1, 22, pg_temp.iso(1), '[[60,8],[60,8],[60,8]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(2), '[[80,8],[80,8],[80,8]]', 6, 10);
select pg_temp.sess(1, 10, pg_temp.iso(2), '[[77.5,10],[77.5,10],[77.5,10]]', 6, 10);
select pg_temp.sess(1, 10, pg_temp.iso(3), '[[60,10]]', 6, 10);
select pg_temp.sess(1, 3, pg_temp.iso(4), '[[80,8],[80,8]]', 6, 10);
select pg_temp.sess(1, 10, pg_temp.iso(4), '[[80,10],[80,10]]', 6, 10);
select pg_temp.weigh(1, 0, 82); select pg_temp.weigh(1, 1, 82); select pg_temp.weigh(1, 2, 82);
select pg_temp.weigh(1, 8, 81.5);
select pg_temp.weigh(1, 14, 80.5); select pg_temp.weigh(1, 15, 80.5); select pg_temp.weigh(1, 16, 80.5);
select pg_temp.weigh(1, 21, 80); select pg_temp.weigh(1, 22, 80); select pg_temp.weigh(1, 23, 80);
select pg_temp.weigh(1, 30, 70);
select pg_temp.girth(1, -5, 90);
select pg_temp.girth(1, 25, 88);

-- User B: the same exercises, heavier, with her own weigh-ins and waist; none of it may reach user 1.
select pg_temp.link(2, 1, pg_temp.sess(2, 3, pg_temp.main(1), '[[200,5]]'));
select pg_temp.link(2, 2, pg_temp.sess(2, 10, pg_temp.main(1), '[[210,5]]'));
select pg_temp.link(2, 3, pg_temp.sess(2, 15, pg_temp.main(1), '[[220,5]]'));
select pg_temp.link(2, 4, pg_temp.sess(2, 22, pg_temp.main(1), '[[230,5]]'));
select pg_temp.weigh(2, 0, 150); select pg_temp.weigh(2, 1, 150); select pg_temp.weigh(2, 2, 150);
select pg_temp.weigh(2, 21, 150); select pg_temp.weigh(2, 22, 150); select pg_temp.weigh(2, 23, 150);
select pg_temp.girth(2, -5, 130);
select pg_temp.girth(2, 25, 120);

create temp table p1 as select pg_temp.build(1, pg_temp.d(28)) as b;
create temp table pb as select pg_temp.build(2, pg_temp.d(28)) as b;

-- ---------------------------------------------------------------------------
-- Contract: version, keys, header
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'schema_version' from p1), '2'::jsonb, 'the review is built as schema version 2');
select is((select array_agg(k order by k) from p1, jsonb_object_keys(b->'inputs') k),
  array['adherence', 'groups', 'lifts', 'outcomes', 'progress', 'schema_version', 'stalls', 'waist', 'weight'],
  'the v1 keys stay and only schema_version and progress are new');
select is((select array_agg(k order by k) from p1, jsonb_object_keys(b->'inputs'->'progress') k),
  array['as_of', 'context', 'headline', 'intention', 'lifts', 'missing', 'phase', 'records', 'strength', 'waist', 'weight'],
  'progress has the contract keys and no refresh keys');
select is((select b->'inputs'->'progress'->>'as_of' from p1), pg_temp.d(27)::text, 'progress is as of the block''s last day');
select is((select b->'inputs'->'progress'->>'intention' from p1), 'fat_loss', 'progress carries the intention');
select is((select b->'inputs'->'progress'->>'phase' from p1), 'maintenance', 'and the calorie phase');
select is((select b->'inputs'->'progress'->'missing' from p1), '["diary"]'::jsonb,
  'missing: no diary, but the weigh-in gate passed');

-- ---------------------------------------------------------------------------
-- Strength, lifts: the block to date at the last plan week (a deload week 4 uses the carried session)
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'progress'->'strength' from p1),
  public.coach_progress_strength_for(pg_temp.u(1), pg_temp.plan_of(1), 4, pg_temp.d(21), pg_temp.d(27)),
  'strength equals the weekly builder at the last plan week');
select is((select b->'inputs'->'progress'->'lifts' from p1),
  public.coach_progress_lifts_for(pg_temp.u(1), pg_temp.plan_of(1), 4, pg_temp.d(21), pg_temp.d(27)),
  'lifts equal the weekly builder at the last plan week');
create function pg_temp.ex_row(ex uuid) returns jsonb language sql as $$
  select x from p1, jsonb_array_elements(b->'inputs'->'progress'->'strength'->'groups') g,
                jsonb_array_elements(g->'exercises') x where x->>'exercise_id' = ex::text $$;
select is(pg_temp.ex_row(pg_temp.iso(1))->>'state', 'up', 'more load at the compared session is up');
select is(pg_temp.ex_row(pg_temp.iso(1))->'best'->>'date', pg_temp.d(15)::text,
  'the deload week''s session is skipped: the compared session is the carried week-3 one');
select is(pg_temp.ex_row(pg_temp.iso(2))->>'state', 'down', 'less load is down');
select is(pg_temp.ex_row(pg_temp.iso(3))->>'state', 'uncompared', 'one session in the block reads uncompared');
select is(pg_temp.ex_row(pg_temp.iso(4))->>'state', 'up', 'more reps at the same load is up');
select is(pg_temp.ex_row(pg_temp.iso(1))->'baseline'->>'date', pg_temp.d(3)::text,
  'the baseline is the block''s first non-deload session');
select is((select l->>'state' from p1, jsonb_array_elements(b->'inputs'->'progress'->'lifts') l
           where l->>'exercise_id' = pg_temp.main(1)::text), 'up', 'a lift 5 % above its first week is up');
select is((select l->>'state' from p1, jsonb_array_elements(b->'inputs'->'progress'->'lifts') l
           where l->>'exercise_id' = pg_temp.main(2)::text), 'margin', 'a lift 3.9 % above its first week is within the margin');
select is((select l->>'state' from p1, jsonb_array_elements(b->'inputs'->'progress'->'lifts') l
           where l->>'exercise_id' = pg_temp.main(3)::text), 'up', 'a lift 4.5 % above its first week is up');
select is((select l->>'state' from p1, jsonb_array_elements(b->'inputs'->'progress'->'lifts') l
           where l->>'exercise_id' = pg_temp.main(4)::text), 'provisional', 'a lift with two counted weeks is provisional');
select is((select jsonb_array_length(l->'weeks') from p1, jsonb_array_elements(b->'inputs'->'progress'->'lifts') l
           where l->>'exercise_id' = pg_temp.main(1)::text), 4, 'the deload week stays in the lift''s weeks, flagged');

-- ---------------------------------------------------------------------------
-- Headline and context
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'progress'->'headline'->'ups' from p1), '6'::jsonb,
  'ups: the exercises that went up (the main lifts and two isolation lifts)');
select is((select b->'inputs'->'progress'->'headline'->'total' from p1), '7'::jsonb,
  'total: the compared exercises (the one with a single session is out)');
select is((select b->'inputs'->'progress'->'headline'->'main_lift'->>'exercise_id' from p1), pg_temp.main(1)::text,
  'the headline main lift is the one with the largest change among the ups');
select is((select (b->'inputs'->'progress'->'headline'->'main_lift'->>'change_pct')::numeric from p1),
  round((public.coach_e1rm(105, 5) / public.coach_e1rm(100, 5) - 1) * 100, 1), 'with its percentage');
select is((select b->'inputs'->'progress'->'headline'->'weight_change_kg' from p1), '-2.0'::jsonb,
  'and the block weight change');
select is((select b->'inputs'->'progress'->'context' from p1),
  '{"sessions_done": 4, "sessions_planned": 4, "sets_done": 4, "sets_planned": null}'::jsonb,
  'the context line sums every plan week; no routine, no planned sets');

-- ---------------------------------------------------------------------------
-- Records: the whole block
-- ---------------------------------------------------------------------------
select is((select jsonb_array_length(b->'inputs'->'progress'->'records'->'items') from p1), 3, 'at most 3 record items');
-- m1..m4: 2 each (e1RM and heaviest); i1: 1 (heaviest, the e1RM is lower); i2: 1 (e1RM, the load is lower); i4: 1 (e1RM).
-- i3 has one session and every baseline session is no record, so none of them adds to the total.
select is((select b->'inputs'->'progress'->'records'->'total' from p1), '11'::jsonb,
  'the total counts every record of the block; each exercise''s first session is a baseline, not a record');
select is((select bool_and((r->>'main_lift')::boolean) from p1, jsonb_array_elements(b->'inputs'->'progress'->'records'->'items') r),
  true, 'main lifts come first');
select is(public.coach_progress_records_for(pg_temp.u(1), pg_temp.plan_of(1), 4, pg_temp.d(21), pg_temp.d(27)), '[]'::jsonb,
  'the last week alone has no record: the block''s records are not the week''s');

-- ---------------------------------------------------------------------------
-- Weight: gate, change, series, outcome, trend
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'progress'->'weight'->'change_kg' from p1), '-2.0'::jsonb,
  'the change is the last qualifying week minus the first one (week 2, one day, is no week)');
select is((select jsonb_build_array(b->'inputs'->'progress'->'weight'->'avg_first_kg',
                                    b->'inputs'->'progress'->'weight'->'avg_last_kg',
                                    b->'inputs'->'progress'->'weight'->'days_first',
                                    b->'inputs'->'progress'->'weight'->'days_last') from p1),
  '[82.00, 80.00, 3, 3]'::jsonb, 'the first and last plan week averages and their weigh-in days');
select is((select b->'inputs'->'progress'->'weight'->'series' from p1),
  jsonb_build_array(jsonb_build_object('start_date', pg_temp.d(0), 'avg_kg', 82.00, 'days', 3),
                    jsonb_build_object('start_date', pg_temp.d(7), 'avg_kg', 81.50, 'days', 1),
                    jsonb_build_object('start_date', pg_temp.d(14), 'avg_kg', 80.50, 'days', 3),
                    jsonb_build_object('start_date', pg_temp.d(21), 'avg_kg', 80.00, 'days', 3)),
  'the series holds the block''s plan-week averages; the weigh-in after the block is left out');
select is((select b->'inputs'->'progress'->'weight'->'outcome' from p1),
  jsonb_build_object('kg', 80, 'deadline', pg_temp.d(27)), 'the weight outcome and its deadline, the block''s last day');
select is((select b->'inputs'->'progress'->'weight'->'trend' from p1),
  '{"slope": -0.700, "low": -1.004, "high": -0.396, "weeks": 4}'::jsonb, 'the trend over the 4 ISO weeks of the block');
select is((select b->'inputs'->'progress'->'weight'->'rate_kg_week' from p1), '-0.5'::jsonb, 'and the plan''s rate');
select is((select b->'inputs'->'progress'->'waist' from p1),
  jsonb_build_object('cm', 88, 'date', pg_temp.d(25), 'ref_cm', 90, 'ref_date', pg_temp.d(-5), 'change_cm', -2),
  'the waist pair of the block');

-- ---------------------------------------------------------------------------
-- Outcomes: the v1 keys, the weight one from the last plan week
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'outcomes'->0 from p1),
  jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 80, 'unit', 'kg', 'deadline', pg_temp.d(27)::text,
                     'current_value', 80.00, 'reached', true),
  'a weight outcome without a deadline takes the block''s last day; current is the last week''s average');
select is((select b->'inputs'->'outcomes'->1 from p1),
  jsonb_build_object('kind', 'e1rm', 'key', (select key from public.exercises where id = pg_temp.main(1)), 'target_value', 100,
                     'unit', 'kg', 'deadline', '2026-04-15', 'current_value', public.coach_e1rm(105, 5), 'reached', true),
  'an outcome with its own deadline keeps it');

-- Weight outcome gate: the last plan week's average is shown, but with under 3 weigh-in days it is no verdict.
-- User 8: last week has 2 days at 79 (target 80, a loss); user 9: no weigh-in at all.
select pg_temp.mkplan(8, 4, '{}', '[{"kind": "weight", "key": "weight", "target_value": 80, "unit": "kg"}]');
select pg_temp.mkplan(9, 4, '{}', '[{"kind": "weight", "key": "weight", "target_value": 80, "unit": "kg"}]');
select pg_temp.filler(8, 3); select pg_temp.filler(8, 10); select pg_temp.filler(8, 15); select pg_temp.filler(8, 22);
select pg_temp.filler(9, 3); select pg_temp.filler(9, 10); select pg_temp.filler(9, 15); select pg_temp.filler(9, 22);
select pg_temp.weigh(8, 21, 79); select pg_temp.weigh(8, 22, 79);
-- User 11: the weight outcome stores its own deadline (day 18), not the block's end (day 27).
select pg_temp.mkplan(11, 4, '{}', jsonb_build_array(jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 80,
                                                                         'unit', 'kg', 'deadline', pg_temp.d(18)::text)));
select pg_temp.filler(11, 3); select pg_temp.filler(11, 10); select pg_temp.filler(11, 15); select pg_temp.filler(11, 22);
select pg_temp.weigh(11, 21, 79); select pg_temp.weigh(11, 22, 79); select pg_temp.weigh(11, 23, 79);
create temp table p11 as select pg_temp.build(11, pg_temp.d(28)) as b;
select is((select b->'inputs'->'outcomes'->0->>'deadline' from p11), pg_temp.d(18)::text,
  'a weight outcome''s own deadline is kept in outcomes');
select is((select b->'inputs'->'progress'->'weight'->'outcome'->>'deadline' from p11), pg_temp.d(18)::text,
  'and progress.weight.outcome carries that same deadline, not the block''s end');
select is((select pg_temp.build(8, pg_temp.d(28))->'inputs'->'outcomes'->0->'current_value'), '79.00'::jsonb,
  'under 3 weigh-in days the outcome still shows the last week''s average');
select is((select pg_temp.build(8, pg_temp.d(28))->'inputs'->'outcomes'->0->'reached'), 'null'::jsonb,
  'but reached is null, not true: no chip below the weight gate');
select is((select pg_temp.build(9, pg_temp.d(28))->'inputs'->'outcomes'->0->'reached'), 'null'::jsonb,
  'with no weigh-in at all reached is null too');

-- ---------------------------------------------------------------------------
-- The frozen v1 keys
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'adherence' from p1), '{"completed": 4, "planned": 4, "pct": 100}'::jsonb, 'adherence is unchanged');
select is((select array_agg(k order by k) from p1, jsonb_object_keys(b->'inputs'->'weight') k),
  array['first_avg_kg', 'interval_high', 'interval_low', 'last_avg_kg', 'slope_kg_week', 'target_rate_kg_week'],
  'the v1 weight object keeps its keys');
select is((select jsonb_build_array(b->'inputs'->'weight'->'first_avg_kg', b->'inputs'->'weight'->'last_avg_kg') from p1),
  '[82.00, 80.00]'::jsonb, 'and its ungated first and last averages');
select is((select jsonb_array_length(b->'inputs'->'lifts') from p1), 1, 'the v1 lifts (the plan''s main lifts) are unchanged');
select is((select b->'inputs'->'lifts'->0->'weeks' from p1),
  jsonb_build_array(jsonb_build_object('week', 1, 'e1rm', public.coach_e1rm(100, 5)),
                    jsonb_build_object('week', 2, 'e1rm', public.coach_e1rm(100, 5)),
                    jsonb_build_object('week', 3, 'e1rm', public.coach_e1rm(105, 5))),
  'with their weekly e1RM, the deload week left out');
select is((select b->'inputs'->'lifts'->0->'stalled' from p1), 'false'::jsonb, 'and their stalled flag');
select is((select jsonb_typeof(b->'inputs'->'groups') from p1), 'array', 'the v1 groups are still built');
select is((select jsonb_typeof(b->'inputs'->'stalls') from p1), 'array', 'and the v1 stalls');

-- ---------------------------------------------------------------------------
-- Isolation: user B's data never reaches user 1
-- ---------------------------------------------------------------------------
select is((select b->'inputs'->'progress'->'weight'->'avg_first_kg' from pb), '150.00'::jsonb, 'B''s weight is B''s own');
select isnt((select b->'inputs'->'progress'->'weight'->'avg_first_kg' from p1), '150.00'::jsonb, 'user 1 never sees B''s weight');
select is((select b->'inputs'->'progress'->'waist'->'cm' from pb), '120.0'::jsonb, 'B''s waist is B''s own');
select is((select count(*)::int from p1, jsonb_array_elements(b->'inputs'->'progress'->'lifts') l
           where (l->'weeks'->0->>'e1rm_kg')::numeric > 200), 0, 'none of B''s 200 kg lifts is in user 1''s lifts');
select ok(public.coach_progress_block_weight_for(pg_temp.u(1), pg_temp.plan_of(2), pg_temp.d(0), pg_temp.d(27)) is null,
  'the block weight of B''s plan read for user 1 is null (owner filter)');
select ok(public.coach_progress_block_weight_for(pg_temp.u(2), pg_temp.plan_of(1), pg_temp.d(0), pg_temp.d(27)) is null,
  'and the other way round');

-- ---------------------------------------------------------------------------
-- Block weight gate (users 3-5), a first block with one session per exercise, a one-week block
-- ---------------------------------------------------------------------------
-- User 3: no weigh-in at all; one session of one exercise (first block: its first session is the baseline).
select pg_temp.mkplan(3);
select pg_temp.link(3, 1, pg_temp.sess(3, 3, pg_temp.iso(1), '[[80,8]]'));
select pg_temp.filler(3, 10); select pg_temp.filler(3, 15); select pg_temp.filler(3, 22);
create temp table p3 as select pg_temp.progress(3, pg_temp.d(28)) as p;
select is((select p->'weight' from p3), 'null'::jsonb, 'no weigh-in at all: weight is null');
select is((select p->'missing' from p3), '["weigh_ins", "diary"]'::jsonb, 'and missing says weigh-ins');
select is((select p->'strength'->'groups'->0->'exercises'->0->>'state' from p3), 'uncompared',
  'a first block with one session: the exercise is its own baseline, uncompared');
select is((select p->'headline'->>'ups' from p3), '0', 'no up when nothing is compared');

-- User 4: week 1 has 2 weigh-in days, week 4 has 3: one qualifying week, no change, the averages stay.
select pg_temp.mkplan(4);
select pg_temp.filler(4, 3); select pg_temp.filler(4, 10); select pg_temp.filler(4, 15); select pg_temp.filler(4, 22);
select pg_temp.weigh(4, 0, 81); select pg_temp.weigh(4, 1, 83);
select pg_temp.weigh(4, 21, 80); select pg_temp.weigh(4, 22, 80); select pg_temp.weigh(4, 23, 80);
create temp table p4 as select pg_temp.progress(4, pg_temp.d(28)) as p;
select is((select p->'weight'->'change_kg' from p4), 'null'::jsonb, 'one qualifying week: no change');
select is((select jsonb_build_array(p->'weight'->'avg_first_kg', p->'weight'->'days_first', p->'weight'->'avg_last_kg',
                                    p->'weight'->'days_last') from p4),
  '[82.00, 2, 80.00, 3]'::jsonb, 'but the first and last plan week averages stay');
select is((select p->'missing' from p4), '["weigh_ins", "diary"]'::jsonb, 'and the gate shows in missing');
select is((select p->'headline'->'weight_change_kg' from p4), 'null'::jsonb, 'the headline carries no weight change');

-- User 5: a one-week block: nothing is compared in week 1.
select pg_temp.mkplan(5, 1);
select pg_temp.sess(5, 1, pg_temp.iso(1), '[[80,8]]');
select pg_temp.sess(5, 3, pg_temp.iso(1), '[[85,8]]');
create temp table p5 as select pg_temp.progress(5, pg_temp.d(7)) as p;
select is((select p->'headline'->'ups' from p5), 'null'::jsonb, 'a one-week block has no ups');
select is((select p->'headline'->'total' from p5), 'null'::jsonb, 'and no total');
select is((select p->'strength'->'block_week' from p5), '1'::jsonb, 'strength is the base week');

-- User 6: week 2 of 4 is paused (no workout in days 7-13): not a plan week, its weigh-ins are no week's.
select pg_temp.mkplan(6, 3);
select pg_temp.filler(6, 3); select pg_temp.filler(6, 15); select pg_temp.filler(6, 22);
select pg_temp.weigh(6, 8, 99); select pg_temp.weigh(6, 9, 99); select pg_temp.weigh(6, 10, 99);
select pg_temp.weigh(6, 0, 82); select pg_temp.weigh(6, 1, 82); select pg_temp.weigh(6, 2, 82);
select pg_temp.weigh(6, 14, 81); select pg_temp.weigh(6, 15, 81); select pg_temp.weigh(6, 16, 81);
select pg_temp.weigh(6, 21, 80); select pg_temp.weigh(6, 22, 80); select pg_temp.weigh(6, 23, 80);
create temp table p6 as select pg_temp.progress(6, pg_temp.d(28)) as p;
select is((select jsonb_agg(s->>'start_date' order by s->>'start_date') from p6, jsonb_array_elements(p->'weight'->'series') s),
  jsonb_build_array(pg_temp.d(0)::text, pg_temp.d(14)::text, pg_temp.d(21)::text),
  'a paused week is not a plan week: its weigh-ins are not in the series');
select is((select p->'weight'->'change_kg' from p6), '-2.0'::jsonb, 'and the change spans the plan weeks only');
select is((select p->'as_of' from p6), to_jsonb(pg_temp.d(27)), 'the block ends at the last plan week''s window');

-- User 7: week 2's window is extended by 3 days (days 7-16): the last plan week is days 24-30.
select pg_temp.mkplan(7);
insert into public.coach_plan_extensions (plan_id, user_id, window_k, days) values (pg_temp.plan_of(7), pg_temp.u(7), 1, 3);
select pg_temp.filler(7, 3); select pg_temp.filler(7, 10); select pg_temp.filler(7, 18); select pg_temp.filler(7, 25);
select pg_temp.weigh(7, 24, 79); select pg_temp.weigh(7, 25, 79); select pg_temp.weigh(7, 26, 79);
select pg_temp.weigh(7, 0, 81); select pg_temp.weigh(7, 1, 81); select pg_temp.weigh(7, 2, 81);
select pg_temp.weigh(7, 17, 120);
create temp table p7 as select pg_temp.progress(7, pg_temp.d(31)) as p;
select is((select p->'as_of' from p7), to_jsonb(pg_temp.d(30)), 'an extension moves the block''s last day');
select is((select jsonb_build_array(p->'weight'->'avg_last_kg', p->'weight'->'days_last') from p7), '[79.00, 3]'::jsonb,
  'the last plan week is the shifted one');
select is((select s->'start_date' from p7, jsonb_array_elements(p->'weight'->'series') s where s->>'avg_kg' = '120.00'),
  to_jsonb(pg_temp.d(17)), 'and week 3 starts after the extended window');

-- ---------------------------------------------------------------------------
-- The early review path (Bloque nuevo): live, progress as of the given day
-- ---------------------------------------------------------------------------
create temp table e1 as select pg_temp.build(1, pg_temp.d(13), pg_temp.d(13)) as b;
select is((select b->'inputs'->'progress'->>'as_of' from e1), pg_temp.d(13)::text, 'an early review is as of its own day');
select is((select b->'inputs'->'schema_version' from e1), '2'::jsonb, 'and built as v2');
select ok((select b->'inputs'->'progress'->'weight' is not null from e1), 'with the weight read up to that day');
select is((select b->'inputs'->'outcomes'->0->>'deadline' from e1), pg_temp.d(27)::text,
  'an early review''s default deadline is the block''s real last day, not the day of the review');
select is((select b->'inputs'->'progress'->'weight'->'outcome'->>'deadline' from e1), pg_temp.d(27)::text,
  'and progress.weight.outcome carries the same deadline');

-- ---------------------------------------------------------------------------
-- Grants, security, the stored review
-- ---------------------------------------------------------------------------
select is((select array_agg(f order by f) from unnest(array[
             'public.coach_block_review_build(uuid, date, date)',
             'public.coach_progress_block_weight_for(uuid, uuid, date, date)',
             'public.coach_progress_block_records_for(uuid, date, date)']) f
           where has_function_privilege('authenticated', f, 'execute')
              or has_function_privilege('anon', f, 'execute')
              or has_function_privilege('public', f, 'execute')), null,
  'the builder and the block helpers are not executable by authenticated, anon or public');
select is((select p.prosecdef and p.proconfig = array['search_path=""']
           from pg_proc p where p.oid = 'public.coach_progress_block_weight_for(uuid, uuid, date, date)'::regprocedure), true,
  'the block weight builder is security definer with an empty search_path');

-- A stored review: the progress is built once and never recomputed. User 10: a block of 4 trained weeks that ended
-- yesterday (real dates, as the RPC checks today), saved in a folder.
insert into public.routine_folders (id, user_id, name) values ('c9317000-0000-4000-8000-0000000000f1', pg_temp.u(10), 'Bloque');
insert into public.coach_plans (id, user_id, block_start, block_weeks, intention, nutrition, phase, status, folder_id, accepted_at)
values ('c9317000-0000-4000-8000-0000000000a1', pg_temp.u(10), current_date - 28, 4, 'fat_loss',
        '{"rate_kg_week": -0.5, "phase": "maintenance"}', 'maintenance', 'accepted', 'c9317000-0000-4000-8000-0000000000f1', now());
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
select 'c9317000-0000-4000-8000-0000000000a1', pg_temp.u(10), k, 1, 'Sesion', '[]' from generate_series(1, 4) k;
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
select pg_temp.u(10), 'F', 'completed', ((current_date - 28 + 7 * k + 1)::timestamp + interval '10 hours') at time zone 'UTC',
       ((current_date - 28 + 7 * k + 1)::timestamp + interval '11 hours') at time zone 'UTC'
from generate_series(0, 3) k;
insert into public.body_measurements (user_id, measured_at, weight_kg, source)
select pg_temp.u(10), ((current_date - 28 + k)::timestamp + interval '8 hours') at time zone 'UTC', 80, 'manual'
from generate_series(0, 2) k;

grant execute on all functions in schema pg_temp to authenticated;
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(10), 'role', 'authenticated')::text, true);
create temp table o1 as select public.open_coach_block_review(current_date) as r;
grant select on o1 to authenticated;
select is((select r->'inputs'->'schema_version' from o1), '2'::jsonb, 'the stored review is v2');
select is((select r->'inputs'->'progress'->>'as_of' from o1), (current_date - 1)::text, 'with progress as of ended_on');
reset role;
insert into public.body_measurements (user_id, measured_at, weight_kg, source)
select pg_temp.u(10), ((current_date - 8 + k)::timestamp + interval '8 hours') at time zone 'UTC', 70, 'manual'
from generate_series(0, 2) k;
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(10), 'role', 'authenticated')::text, true);
select is((select public.open_coach_block_review(current_date)->'inputs'), (select r->'inputs' from o1),
  'a second open after late data returns the stored inputs, nothing recomputed');
reset role;

select * from finish();
rollback;
