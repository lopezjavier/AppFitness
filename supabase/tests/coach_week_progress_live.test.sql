-- pgTAP: the live path of the weekly report snapshot v2 (T-313, US-141): coach_week_summary stores schema_version 2
-- with progress, refreshes the latest finished week (10-minute throttle) and finalises it when the next week is
-- stored; record_paused_week_report stores schema_version 2; v1 rows are never touched; the stall card skips a lift
-- with an open discomfort. Rules: planning/07-coach-design.md section 3 "Weekly report: progress first" ("Stored weeks
-- and late data", (7)); contract: tasks/todo.md "## T-313".
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(35);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; the summary is called with the user's claims)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9313200-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
-- o days ago at hour h (UTC).
create function pg_temp.at(o int, h numeric default 9) returns timestamptz language sql as $$
  select ((pg_temp.d0() - o)::timestamp + make_interval(secs => h * 3600)) at time zone 'UTC' $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;

-- A cut plan of `weeks` plan weeks whose block started start_off days ago (status given), one session a week, with a
-- routine-less workout on day 1 of each of its first `trained` windows (so those windows are not paused).
create function pg_temp.mk(n int, start_off int, trained int, st text default 'accepted', weeks int default 4) returns uuid
language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  update public.profiles set onboarding_completed = true where id = pg_temp.u(n);
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, accepted_at)
  values (v, pg_temp.u(n), pg_temp.d0() - start_off, 'fat_loss', '{"rate_kg_week": -0.5, "target_kcal": 2200}', st, now());
  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
  select v, pg_temp.u(n), w, 1, 'Sesion', '[]' from generate_series(1, weeks) w;
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 'Filler', 'completed', pg_temp.at(start_off - 7 * k - 1, 15), pg_temp.at(start_off - 7 * k - 1, 16)
  from generate_series(0, trained - 1) k;
  return v;
end $$;
create function pg_temp.weigh(n int, o int, kg numeric default 80) returns void language sql as $$
  insert into public.body_measurements (user_id, measured_at, weight_kg, source) values (pg_temp.u(n), pg_temp.at(o, 7), kg, 'manual') $$;
-- A completed workout o days ago with three working sets of the exercise.
create function pg_temp.lift(n int, o int, ex uuid, kg numeric) returns void language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(n), 'W', 'completed', pg_temp.at(o, 10), pg_temp.at(o, 11));
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
  select v, ex, 0, i, 'working', kg, 5, pg_temp.at(o, 10.2) from generate_series(1, 3) i;
end $$;
create function pg_temp.main_lift() returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and counts_as_strength and movement_pattern = 'horizontal_push' order by key limit 1 $$;

create function pg_temp.summary(n int, ahead int default 0) returns jsonb language sql as $$
  select pg_temp.as_user(n);
  select public.coach_week_summary(pg_temp.d0() + ahead) $$;
create function pg_temp.snap(n int, wk int) returns jsonb language sql as $$
  select r.snapshot from public.coach_week_reports r
  where r.user_id = pg_temp.u(n) and r.plan_id = pg_temp.plan_of(n) and r.week = wk $$;
-- Backdates the stored progress of a week so the next call finds it stale.
create function pg_temp.backdate(n int, wk int) returns void language sql as $$
  update public.coach_week_reports r
  set snapshot = jsonb_set(r.snapshot, '{progress,refreshed_at}', to_jsonb(now() - interval '11 minutes'))
  where r.user_id = pg_temp.u(n) and r.plan_id = pg_temp.plan_of(n) and r.week = wk $$;

create temp table res (k text primary key, j jsonb);
grant all on res to authenticated;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'live' || n || '@example.com' from generate_series(1, 14) n;

-- ---------------------------------------------------------------------------
-- User 1: block started 10 days ago; plan week 1 (days -10..-4) is the latest finished week, with 2 weigh-in days.
-- ---------------------------------------------------------------------------
select pg_temp.mk(1, 10, 2);
select pg_temp.weigh(1, 10);
select pg_temp.weigh(1, 9);

set local role authenticated;
select pg_temp.as_user(1);
insert into res select 'first', public.coach_week_summary(pg_temp.d0());
reset role;
insert into res select 'build', public.coach_week_summary_build(pg_temp.d0());

select ok(pg_temp.snap(1, 1)->>'schema_version' = '2' and pg_temp.snap(1, 1) ? 'progress',
  'a new normal weekly report is stored with schema_version 2 and progress');
select is(pg_temp.snap(1, 1) - 'progress' - 'schema_version', (select j - 'seen' from res where k = 'build'),
  'every v1 key is stored exactly as built');
select is((select j from res where k = 'first'), jsonb_set(pg_temp.snap(1, 1), '{seen}', 'false'),
  'the first call returns the stored snapshot with the live seen flag');
select is(jsonb_build_array(pg_temp.snap(1, 1)->'progress'->'final', pg_temp.snap(1, 1)->'progress'->'as_of'),
  jsonb_build_array(false, pg_temp.d0() - 4), 'the stored progress is as of the week''s end and not final');

-- Late data within 10 minutes: no rebuild.
select pg_temp.weigh(1, 5);
insert into res select 'within', pg_temp.summary(1);
select is((select j->'progress'->'weight'->'days' from res where k = 'within'), '2'::jsonb,
  'within 10 minutes the stored progress is returned as is');

-- Stale: rebuilt, and the late weigh-in now counts (Late data).
select pg_temp.backdate(1, 1);
insert into res select 'stale', pg_temp.summary(1);
select is((select j->'progress'->'weight'->'days' from res where k = 'stale'), '3'::jsonb,
  'older than 10 minutes the progress is rebuilt and a late weigh-in counts');
select is((pg_temp.snap(1, 1)->'progress'->>'refreshed_at')::timestamptz, now(), 'the refresh is stored with its time');
select is(pg_temp.snap(1, 1) - 'progress', (select j - 'seen' - 'progress' from res where k = 'first'),
  'a refresh leaves every v1 key byte-equal');
select pg_temp.as_user(1);
select is(public.get_coach_report((select id from public.coach_week_reports where user_id = pg_temp.u(1)))->'snapshot',
  pg_temp.snap(1, 1), 'get_coach_report returns the v2 snapshot');

-- ---------------------------------------------------------------------------
-- User 2: block started 13 days ago; week 1 is days -13..-7, week 2 days -6..0 (finished tomorrow).
-- User 5: user B, with a non-final, stale v2 row of an earlier week.
-- ---------------------------------------------------------------------------
select pg_temp.mk(2, 13, 2);
select pg_temp.weigh(2, 13);
select pg_temp.weigh(2, 12);
select pg_temp.weigh(2, 6);
select pg_temp.mk(5, 10, 2);
insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
values (pg_temp.u(5), pg_temp.plan_of(5), 1, pg_temp.d0() - 40, pg_temp.d0() - 34,
        jsonb_build_object('schema_version', 2,
                           'progress', jsonb_build_object('final', false, 'refreshed_at', now() - interval '1 hour')));
insert into res select 'b_before', (select snapshot from public.coach_week_reports where user_id = pg_temp.u(5));

select pg_temp.summary(2);
select pg_temp.weigh(2, 8);
select pg_temp.summary(2, 1);

select ok(pg_temp.snap(2, 2) ? 'progress', 'tomorrow the next week is stored');
select is(pg_temp.snap(2, 1)->'progress'->'final', 'true'::jsonb, 'storing week N+1 finalises week N');
select is(pg_temp.snap(2, 1)->'progress'->'weight'->'days', '3'::jsonb, 'the final rebuild includes the late weigh-in');
select is(pg_temp.snap(2, 2)->'progress'->'final', 'false'::jsonb, 'the new week stays refreshable');

select pg_temp.weigh(2, 10);
select pg_temp.backdate(2, 1);
select pg_temp.summary(2);
select is(pg_temp.snap(2, 1)->'progress'->'weight'->'days', '3'::jsonb, 'a final week is never refreshed again');
select is((select snapshot from public.coach_week_reports where user_id = pg_temp.u(5)), (select j from res where k = 'b_before'),
  'another user''s calls never touch B''s rows');

-- ---------------------------------------------------------------------------
-- User 3: as user 2, but week 1 is stored as a v1 row (stored before this migration).
-- ---------------------------------------------------------------------------
select pg_temp.mk(3, 13, 2);
select pg_temp.weigh(3, 13);
select pg_temp.weigh(3, 6);
select pg_temp.summary(3);
update public.coach_week_reports set snapshot = snapshot - 'progress' - 'schema_version' where user_id = pg_temp.u(3);
insert into res select 'v1', pg_temp.snap(3, 1);
select pg_temp.weigh(3, 12);

select is(pg_temp.summary(3) - 'seen', (select j from res where k = 'v1'), 'a v1 row is returned as stored, never refreshed');
select pg_temp.summary(3, 1);
select is(pg_temp.snap(3, 1), (select j from res where k = 'v1'), 'a v1 row is never finalised');

-- ---------------------------------------------------------------------------
-- User 4: a finished block (days -40..-13) whose last week is a non-final v2 row; the new block started 12 days ago.
-- ---------------------------------------------------------------------------
create temp table old4 (id uuid);
insert into old4 select pg_temp.mk(4, 40, 4, 'superseded');
select pg_temp.mk(4, 12, 2);
select pg_temp.weigh(4, 10);
insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
select pg_temp.u(4), o.id, 4, pg_temp.d0() - 19, pg_temp.d0() - 13,
       jsonb_build_object('schema_version', 2,
                          'progress', public.coach_week_progress_for(pg_temp.u(4), o.id, 4, pg_temp.d0() - 19, pg_temp.d0() - 13))
from old4 o;
select pg_temp.summary(4);
select is((select snapshot->'progress'->'final' from public.coach_week_reports where plan_id = (select id from old4)), 'true'::jsonb,
  'the first stored week of a new block finalises the previous block''s last week');

-- ---------------------------------------------------------------------------
-- User 8: block started 15 days ago, window 1 (days -8..-2) has no workout: a paused week. Week 1 (days -15..-9) is
-- stored as a non-final v2 row.
-- ---------------------------------------------------------------------------
select pg_temp.mk(8, 15, 1);
select pg_temp.weigh(8, 14);
insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
values (pg_temp.u(8), pg_temp.plan_of(8), 1, pg_temp.d0() - 15, pg_temp.d0() - 9,
        jsonb_build_object('schema_version', 2,
                           'progress', public.coach_week_progress_for(pg_temp.u(8), pg_temp.plan_of(8), 1, pg_temp.d0() - 15,
                                                                      pg_temp.d0() - 9)));
select pg_temp.as_user(8);
insert into res select 'paused', public.record_paused_week_report(pg_temp.d0());
select is((select j->'schema_version' from res where k = 'paused'), '2'::jsonb, 'a paused week is stored with schema_version 2');
select ok(not ((select j from res where k = 'paused') ? 'progress'), 'and without progress');
select is(pg_temp.snap(8, 1)->'progress'->'final', 'true'::jsonb, 'storing a paused week finalises the week before it');

-- ---------------------------------------------------------------------------
-- Users 6 and 7: the same stalled main lift (100 kg x 5 in weeks 1-3; block started 22 days ago, week 3 is days
-- -8..-2). User 6 logged a level-2 discomfort on it 4 days ago (open at week 3's end).
-- ---------------------------------------------------------------------------
select pg_temp.mk(n, 22, 0) from generate_series(6, 7) n;
select pg_temp.lift(n, o, pg_temp.main_lift(), 100) from generate_series(6, 7) n, unnest(array[21, 14, 7]) o;
select pg_temp.weigh(n, 5) from generate_series(6, 7) n;
insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at)
values (pg_temp.u(6), pg_temp.main_lift(), 2, pg_temp.at(4, 12));
select pg_temp.summary(6);
select pg_temp.summary(7);

select is((select jsonb_agg(s->>'exercise_id') from jsonb_array_elements(pg_temp.snap(7, 3)->'stalls') s),
  jsonb_build_array(pg_temp.main_lift()), 'a stalled main lift gets the stall card');
select is(pg_temp.snap(6, 3)->'stalls', '[]'::jsonb, 'the stall card skips a lift with an open discomfort');
select is((select x->>'state' from jsonb_array_elements(pg_temp.snap(6, 3)->'progress'->'strength'->'groups') g,
                                   jsonb_array_elements(g->'exercises') x
           where x->>'exercise_id' = pg_temp.main_lift()::text), 'paused',
  'it shows only as paused by discomfort');
select is((select x->>'state' from jsonb_array_elements(pg_temp.snap(7, 3)->'progress'->'strength'->'groups') g,
                                   jsonb_array_elements(g->'exercises') x
           where x->>'exercise_id' = pg_temp.main_lift()::text), 'stalled',
  'without a discomfort the same lift is stalled');

-- User 9: as user 7, with a level-2 discomfort logged 12 days ago (closed at week 3's end).
select pg_temp.mk(9, 22, 0);
select pg_temp.lift(9, o, pg_temp.main_lift(), 100) from unnest(array[21, 14, 7]) o;
select pg_temp.weigh(9, 5);
insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at)
values (pg_temp.u(9), pg_temp.main_lift(), 2, pg_temp.at(12, 12));
select pg_temp.summary(9);
select is((select jsonb_agg(s->>'exercise_id') from jsonb_array_elements(pg_temp.snap(9, 3)->'stalls') s),
  jsonb_build_array(pg_temp.main_lift()), 'a level-2 discomfort older than 7 days no longer skips the stall card');

-- ---------------------------------------------------------------------------
-- Users 10-12: the same lift flat in weeks 1-4 (block started 29 days ago; week 3 is days -15..-9, week 4 days -8..-2),
-- so it stalls in weeks 3 and 4. User 10: a level-2 discomfort logged 13 days ago (open at week 3's end, closed at
-- week 4's). User 11: no discomfort. User 12: a level-2 discomfort logged 4 days ago (open at the block's end).
-- ---------------------------------------------------------------------------
select pg_temp.mk(n, 29, 0) from generate_series(10, 12) n;
select pg_temp.lift(n, o, pg_temp.main_lift(), 100) from generate_series(10, 12) n, unnest(array[28, 21, 14, 7]) o;
select pg_temp.weigh(n, 5) from generate_series(10, 12) n;
insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at)
values (pg_temp.u(10), pg_temp.main_lift(), 2, pg_temp.at(13, 12)),
       (pg_temp.u(12), pg_temp.main_lift(), 2, pg_temp.at(4, 12));
select pg_temp.summary(n) from generate_series(10, 12) n;

select is((select jsonb_agg(s->>'exercise_id') from jsonb_array_elements(pg_temp.snap(10, 4)->'stalls') s),
  jsonb_build_array(pg_temp.main_lift()), 'a stall that started during a discomfort gets its card the week after it closes');
select is(pg_temp.snap(11, 4)->'stalls', '[]'::jsonb, 'a stall run that already had its card does not repeat it');
select pg_temp.as_user(11);
select is((select jsonb_agg(s->>'exercise_id')
           from jsonb_array_elements(public.coach_block_review_build(pg_temp.plan_of(11), pg_temp.d0())->'inputs'->'stalls') s),
  jsonb_build_array(pg_temp.main_lift()), 'the block review lists a lift stalled at the block end');
select pg_temp.as_user(12);
select is(public.coach_block_review_build(pg_temp.plan_of(12), pg_temp.d0())->'inputs'->'stalls', '[]'::jsonb,
  'the block review skips a stalled lift with an open discomfort at the block end');

-- ---------------------------------------------------------------------------
-- Users 13 and 14: runs longer than one step back. Plans hold at most 4 weeks today
-- (coach_plan_sessions_week_range), so the check is dropped inside this rolled-back test to exercise the general walk.
-- User 13: 5 weeks (block started 36 days ago), the lift flat in weeks 1-5, so it stalls in weeks 3, 4 and 5; a
-- level-2 discomfort logged 11 days ago is closed at week 3's end (not logged yet), open at week 4's and closed at
-- week 5's. User 14: 6 weeks (block started 43 days ago), 100 kg in weeks 1-3 and 105 kg in weeks 4-6: stalled in week
-- 3, not in weeks 4 and 5, stalled again in week 6.
-- ---------------------------------------------------------------------------
alter table public.coach_plan_sessions drop constraint coach_plan_sessions_week_range;
select pg_temp.mk(13, 36, 0, 'accepted', 5);
select pg_temp.lift(13, o, pg_temp.main_lift(), 100) from unnest(array[35, 28, 21, 14, 7]) o;
select pg_temp.weigh(13, 5);
insert into public.exercise_discomforts (user_id, exercise_id, level, logged_at)
values (pg_temp.u(13), pg_temp.main_lift(), 2, pg_temp.at(11, 12));
select pg_temp.summary(13);
select pg_temp.mk(14, 43, 0, 'accepted', 6);
select pg_temp.lift(14, 42, pg_temp.main_lift(), 100);
select pg_temp.lift(14, 35, pg_temp.main_lift(), 100);
select pg_temp.lift(14, 28, pg_temp.main_lift(), 100);
select pg_temp.lift(14, 21, pg_temp.main_lift(), 105);
select pg_temp.lift(14, 14, pg_temp.main_lift(), 105);
select pg_temp.lift(14, 7, pg_temp.main_lift(), 105);
select pg_temp.weigh(14, 5);
select pg_temp.summary(14);

select is(jsonb_build_array(pg_temp.snap(13, 5)->'week', pg_temp.snap(13, 5)->'stalls'), '[5, []]'::jsonb,
  'the walk goes past a week with an open discomfort: the run''s card was shown in week 3, so week 5 has none');
select is((select jsonb_agg(s->>'exercise_id') from jsonb_array_elements(pg_temp.snap(14, 6)->'stalls') s),
  jsonb_build_array(pg_temp.main_lift()), 'a broken run starts again: stalled, not stalled, stalled gives a new card');

select ok(not has_function_privilege('authenticated', 'public.coach_week_summary_week(date)', 'execute')
          and not has_function_privilege('anon', 'public.coach_week_summary_week(date)', 'execute')
          and not has_function_privilege('public', 'public.coach_week_summary_week(date)', 'execute'),
  'the v1 week builder is internal (it reads the revoked discomfort helper)');
select ok(not has_function_privilege('authenticated', 'public.coach_finalise_week_reports_for(uuid, date)', 'execute')
          and not has_function_privilege('anon', 'public.coach_finalise_week_reports_for(uuid, date)', 'execute'),
  'the finalise helper is internal');
select ok(has_function_privilege('authenticated', 'public.coach_week_summary(date)', 'execute')
          and has_function_privilege('authenticated', 'public.record_paused_week_report(date)', 'execute')
          and not has_function_privilege('anon', 'public.coach_week_summary(date)', 'execute')
          and not has_function_privilege('anon', 'public.record_paused_week_report(date)', 'execute'),
  'the live functions stay executable by authenticated only');

select * from finish();
rollback;
