-- pgTAP: the backfill of the stored weekly reports to snapshot v2 (T-314, US-141, ADR-110):
-- coach_week_reports_backfill_v2 backs up every v1 row in body_v1, adds schema_version 2 and, on normal weeks, progress
-- rebuilt as of the week's end; v1 keys stay byte-equal; only the user's newest stored row stays refreshable (T-313's
-- finalise rule); deleted sessions and weigh-ins are listed in progress.missing; a re-run is a no-op; the live path then
-- takes over. Rules: planning/07-coach-design.md section 3 "Weekly report: progress first" ("Stored weeks and late
-- data"); contract: tasks/todo.md "## T-314".
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(38);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; the summary is called with the user's claims)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9314200-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
-- o days ago at hour h (UTC).
create function pg_temp.at(o int, h numeric default 9) returns timestamptz language sql as $$
  select ((pg_temp.d0() - o)::timestamp + make_interval(secs => h * 3600)) at time zone 'UTC' $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;

-- A cut plan of `weeks` plan weeks whose block started start_off days ago (status given), one session a week, with a
-- routine-less workout on day 2 of each of its first `trained` windows (so those windows are not paused).
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
-- A completed workout o days ago with three working sets of the exercise; returns the workout id.
create function pg_temp.lift(n int, o int, ex uuid, kg numeric) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(n), 'W', 'completed', pg_temp.at(o, 10), pg_temp.at(o, 11));
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
  select v, ex, 0, i, 'working', kg, 5, pg_temp.at(o, 10.2) from generate_series(1, 3) i;
  return v;
end $$;
create function pg_temp.main_lift() returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and counts_as_strength and movement_pattern = 'horizontal_push' order by key limit 1 $$;
create function pg_temp.filler(n int, o int) returns uuid language sql as $$
  select id from public.workout_sessions where user_id = pg_temp.u(n) and name = 'Filler' and started_at = pg_temp.at(o, 15) $$;

-- A v1 row (no schema_version) of the 7-day window that started start_off days ago, stored as the v1 builder would:
-- the v1 keys used here plus `extra`.
create function pg_temp.v1(n int, plan uuid, wk int, start_off int, extra jsonb default '{}') returns void language sql as $$
  insert into public.coach_week_reports (user_id, plan_id, week, paused, period_start, period_end, snapshot)
  values (pg_temp.u(n), plan, wk, wk is null, pg_temp.d0() - start_off, pg_temp.d0() - start_off + 6,
          case when wk is null
               then jsonb_build_object('plan_id', plan, 'paused', true, 'start_date', pg_temp.d0() - start_off,
                                       'end_date', pg_temp.d0() - start_off + 6)
               else jsonb_build_object('plan_id', plan, 'week', wk, 'start_date', pg_temp.d0() - start_off,
                                       'end_date', pg_temp.d0() - start_off + 6, 'sessions_done', 0, 'weigh_in_days', 0,
                                       'changes', '[]'::jsonb, 'stalls', '[]'::jsonb, 'deload', false)
          end || extra) $$;
-- The row of user n whose window started start_off days ago.
create function pg_temp.r(n int, start_off int) returns public.coach_week_reports language sql as $$
  select * from public.coach_week_reports where user_id = pg_temp.u(n) and period_start = pg_temp.d0() - start_off $$;
create function pg_temp.p(n int, start_off int) returns jsonb language sql as $$
  select (pg_temp.r(n, start_off)).snapshot->'progress' $$;
create function pg_temp.summary(n int, ahead int default 0) returns jsonb language sql as $$
  select pg_temp.as_user(n);
  select public.coach_week_summary(pg_temp.d0() + ahead) $$;

create temp table res (k text primary key, j jsonb);
create temp table plans (n int, k text, id uuid);
grant all on res, plans to authenticated;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'v2-' || n || '@example.com' from generate_series(1, 10) n;

-- ---------------------------------------------------------------------------
-- User 1 (A): block started 20 days ago; week 1 is days -20..-14, week 2 days -13..-7, week 3 days -6..0.
-- Week 1: a v1 row with frozen fixture decisions; its session is linked, plus a week-1 session done after the window.
-- Week 2: stored by the live path, then stripped to v1; a late weigh-in inside it and data after its end come later.
-- ---------------------------------------------------------------------------
insert into plans select 1, 'p', pg_temp.mk(1, 20, 3);
select pg_temp.weigh(1, o) from unnest(array[19, 18, 12, 11]) o;
select pg_temp.lift(1, 17, pg_temp.main_lift(), 90);
insert into plans select 1, 'late', pg_temp.lift(1, 10, pg_temp.main_lift(), 100);
update public.coach_plan_sessions set session_id = pg_temp.filler(1, 19)
where plan_id = pg_temp.plan_of(1) and week = 1;
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, session_id)
select pg_temp.plan_of(1), pg_temp.u(1), 1, 2, 'Sesion 2', '[]', p.id from plans p where p.n = 1 and p.k = 'late';
select pg_temp.v1(1, pg_temp.plan_of(1), 1, 20,
  jsonb_build_object('sessions_done', 1, 'weigh_in_days', 2, 'deload', true,
                     'changes', '[{"id": "fixture-change"}]'::jsonb,
                     'calorie_proposal', '{"id": "fixture-proposal", "status": "pending"}'::jsonb,
                     'stalls', '[{"exercise_id": "fixture-stall"}]'::jsonb,
                     'nutrition', '{"decisions": [{"id": "fixture-decision"}], "diagnosis": {"kind": "fixture"}}'::jsonb));
select pg_temp.summary(1);
update public.coach_week_reports set snapshot = snapshot - 'progress' - 'schema_version'
where user_id = pg_temp.u(1) and period_start = pg_temp.d0() - 13;
select pg_temp.weigh(1, 9);
select pg_temp.weigh(1, 5);
select pg_temp.lift(1, 5, pg_temp.main_lift(), 120);

-- User 2: a finished block (days -40..-13) and a new block started 12 days ago; v1 rows of the old block's last week
-- and of the new block's first week.
insert into plans select 2, 'old', pg_temp.mk(2, 40, 4, 'superseded');
select pg_temp.mk(2, 12, 2);
select pg_temp.v1(2, (select id from plans where n = 2 and k = 'old'), 4, 19);
select pg_temp.v1(2, pg_temp.plan_of(2), 1, 12);

-- User 3: as user 2, but the new block (started 3 days ago) has stored no week yet.
insert into plans select 3, 'old', pg_temp.mk(3, 40, 4, 'superseded');
select pg_temp.mk(3, 3, 0);
select pg_temp.v1(3, (select id from plans where n = 3 and k = 'old'), 4, 19);

-- User 4: block started 15 days ago; week 1 (days -15..-9) is a v1 normal row, the next window (days -8..-2) a v1
-- paused row.
select pg_temp.mk(4, 15, 1);
select pg_temp.v1(4, pg_temp.plan_of(4), 1, 15);
select pg_temp.v1(4, pg_temp.plan_of(4), null, 8);

-- User 5: block started 20 days ago; week 1 is a v1 row, week 2 is stored as v2 by the live path (T-313).
select pg_temp.mk(5, 20, 3);
select pg_temp.v1(5, pg_temp.plan_of(5), 1, 20);
select pg_temp.weigh(5, 12);
select pg_temp.summary(5);

-- User 6: block started 13 days ago; week 1's session was done and linked, then the workout was deleted.
select pg_temp.mk(6, 13, 2);
update public.coach_plan_sessions set session_id = pg_temp.filler(6, 12) where plan_id = pg_temp.plan_of(6) and week = 1;
select pg_temp.v1(6, pg_temp.plan_of(6), 1, 13, '{"sessions_done": 1}');
delete from public.workout_sessions where id = pg_temp.filler(6, 12);

-- User 7: block started 13 days ago; week 1 had 3 weigh-in days, one was deleted.
select pg_temp.mk(7, 13, 2);
select pg_temp.weigh(7, o) from unnest(array[12, 11, 10]) o;
select pg_temp.v1(7, pg_temp.plan_of(7), 1, 13, '{"weigh_in_days": 3}');
delete from public.body_measurements where user_id = pg_temp.u(7) and measured_at = pg_temp.at(10, 7);

-- User 8 (B): block started 13 days ago, no weigh-ins nor lifts; week 1 is a v1 row.
select pg_temp.mk(8, 13, 2);
select pg_temp.v1(8, pg_temp.plan_of(8), 1, 13);

-- User 9: a row with a backup but no schema_version (outside the selection: never rewritten again).
select pg_temp.mk(9, 13, 2);
select pg_temp.v1(9, pg_temp.plan_of(9), 1, 13);
update public.coach_week_reports set body_v1 = '{"fixture": "backup"}' where user_id = pg_temp.u(9);

-- User 10: two plans with the same block start, each with a v1 row of week 1 (the same newest period_start).
insert into plans select 10, 'old', pg_temp.mk(10, 13, 2, 'superseded');
select pg_temp.mk(10, 13, 2);
select pg_temp.v1(10, (select id from plans where n = 10 and k = 'old'), 1, 13);
select pg_temp.v1(10, pg_temp.plan_of(10), 1, 13);

select has_column('public', 'coach_week_reports', 'body_v1', 'coach_week_reports has body_v1');
select ok((select data_type = 'jsonb' and is_nullable = 'YES' from information_schema.columns
           where table_schema = 'public' and table_name = 'coach_week_reports' and column_name = 'body_v1'),
  'body_v1 is a nullable jsonb');

-- A normal row whose plan is not its user's fails loudly (never stored without progress), and changes nothing.
insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
values (pg_temp.u(8), pg_temp.plan_of(1), 1, pg_temp.d0() - 100, pg_temp.d0() - 94, '{}');
select throws_like('select public.coach_week_reports_backfill_v2()', '%could not be built%',
  'a normal row whose progress cannot be built makes the backfill raise');
delete from public.coach_week_reports where user_id = pg_temp.u(8) and period_start = pg_temp.d0() - 100;

-- ---------------------------------------------------------------------------
-- The backfill
-- ---------------------------------------------------------------------------
create temp table before as select id, snapshot, body_v1 from public.coach_week_reports;
insert into res select 'run', public.coach_week_reports_backfill_v2();
create temp table after1 as select id, snapshot, body_v1 from public.coach_week_reports;

select is((select j from res where k = 'run'),
  '{"normal": 12, "paused": 1, "refreshable": 8, "deleted_sessions": 1, "deleted_weigh_ins": 1}'::jsonb,
  'the call returns the counts of the rows it rewrote');
select is((select count(*) from after1), (select count(*) from before), 'the row count is unchanged');

-- Past weeks remade.
select is((pg_temp.r(1, 20)).body_v1, (select b.snapshot from before b where b.id = (pg_temp.r(1, 20)).id),
  'a v1 normal row keeps its old snapshot in body_v1');
select ok((pg_temp.r(1, 20)).snapshot->>'schema_version' = '2' and jsonb_typeof(pg_temp.p(1, 20)) = 'object',
  'and becomes schema_version 2 with progress');
select is((select count(*)::int from public.coach_week_reports
           where body_v1 is not null and user_id <> pg_temp.u(9)
             and snapshot - 'progress' - 'schema_version' <> body_v1), 0,
  'on every rewritten row the v1 keys are byte-equal to body_v1');
select is((select jsonb_build_array(s->'changes', s->'calorie_proposal', s->'nutrition'->'decisions',
                                    s->'nutrition'->'diagnosis', s->'stalls', s->'deload', s->'sessions_done')
           from (select (pg_temp.r(1, 20)).snapshot as s) x),
  '[[{"id": "fixture-change"}], {"id": "fixture-proposal", "status": "pending"}, [{"id": "fixture-decision"}],
    {"kind": "fixture"}, [{"exercise_id": "fixture-stall"}], true, 1]'::jsonb,
  'the frozen decisions stay as stored, not rebuilt from today''s data');
select is(pg_temp.p(1, 13) - 'refreshed_at' - 'final' - 'missing',
  public.coach_week_progress_for(pg_temp.u(1), pg_temp.plan_of(1), 2, pg_temp.d0() - 13, pg_temp.d0() - 7)
    - 'refreshed_at' - 'final' - 'missing',
  'the progress is the T-313 builder''s output for the row''s own week');
select is(jsonb_build_array(pg_temp.p(1, 13)->'as_of', pg_temp.p(1, 13)->'refreshed_at'),
  jsonb_build_array(pg_temp.d0() - 7, now()), 'it is as of the week''s end, refreshed now');
select is(jsonb_build_array(pg_temp.p(1, 13)->'weight'->'days', (pg_temp.r(1, 13)).snapshot->'weigh_in_days'),
  '[3, 2]'::jsonb, 'a late weigh-in inside the week counts, one after its end does not; the v1 count stays');
select is((select max((x->>'value_kg')::numeric) from jsonb_array_elements(pg_temp.p(1, 13)->'records') x
           where x->>'kind' = 'heaviest'), 100::numeric,
  'a heavier lift after the week''s end is not a record of the week');
select ok(not ((pg_temp.r(4, 8)).snapshot ? 'progress')
          and (pg_temp.r(4, 8)).snapshot->>'schema_version' = '2'
          and (pg_temp.r(4, 8)).body_v1 = (select b.snapshot from before b where b.id = (pg_temp.r(4, 8)).id),
  'a v1 paused row gets body_v1 and schema_version 2, no progress');

-- Which row stays refreshable (T-313's finalise rule).
select is(pg_temp.p(1, 13)->'final', 'false'::jsonb, 'the user''s newest stored week stays refreshable');
select is(pg_temp.p(1, 20)->'final', 'true'::jsonb, 'an older week of the same plan is final');
select is(jsonb_build_array(pg_temp.p(2, 19)->'final', pg_temp.p(2, 12)->'final'), '[true, false]'::jsonb,
  'a finished block''s last week is final once the next block stored its first week');
select is(pg_temp.p(3, 19)->'final', 'false'::jsonb,
  'a finished block''s last week stays refreshable while the next block has stored no week (as T-313)');
select is(pg_temp.p(4, 15)->'final', 'true'::jsonb, 'a week followed by a stored paused week is final');
select is(pg_temp.p(5, 20)->'final', 'true'::jsonb, 'a week followed by a stored v2 week is final');
select ok((pg_temp.r(5, 13)).snapshot = (select b.snapshot from before b where b.id = (pg_temp.r(5, 13)).id)
          and (pg_temp.r(5, 13)).body_v1 is null, 'a row already written as v2 is not touched');
select is((select count(*)::int from (select user_id from public.coach_week_reports
                                      where snapshot->'progress'->>'final' = 'false' and user_id <> pg_temp.u(10)
                                      group by user_id having count(*) > 1) x), 0,
  'a user whose newest week start is held by one row has one refreshable row');
select is((select jsonb_agg(snapshot->'progress'->'final') from public.coach_week_reports where user_id = pg_temp.u(10)),
  '[false, false]'::jsonb, 'two plans sharing the newest week start both stay refreshable (as T-313)');
select ok((select r.snapshot = b.snapshot and r.body_v1 = '{"fixture": "backup"}' and not r.snapshot ? 'schema_version'
           from public.coach_week_reports r join before b using (id) where r.user_id = pg_temp.u(9)),
  'a row that already has a backup is never rewritten, even without schema_version');

-- Deleted inputs.
select ok(pg_temp.p(6, 13)->'missing' ? 'deleted_sessions' and pg_temp.p(6, 13)->'context'->'sessions_done' = '0',
  'a deleted session is listed as missing and not counted');
select ok(pg_temp.p(7, 13)->'missing' ? 'deleted_weigh_ins' and pg_temp.p(7, 13)->'weight'->'days' = '2',
  'a deleted weigh-in is listed as missing and not counted');
select ok(not (pg_temp.p(1, 20)->'missing' ?| array['deleted_sessions', 'deleted_weigh_ins'])
          and not (pg_temp.p(1, 13)->'missing' ?| array['deleted_sessions', 'deleted_weigh_ins']),
  'with nothing deleted (and a week-1 session done after its window) no deletion is listed');

-- Isolation of the rebuild.
select is(jsonb_build_array(pg_temp.p(8, 13)->'weight'->'days', pg_temp.p(8, 13)->'records', pg_temp.p(8, 13)->'final'),
  '[0, [], false]'::jsonb, 'user B''s progress holds none of A''s data and B''s newest week stays refreshable');

-- Idempotent.
insert into res select 'again', public.coach_week_reports_backfill_v2();
select is((select j from res where k = 'again'),
  '{"normal": 0, "paused": 0, "refreshable": 0, "deleted_sessions": 0, "deleted_weigh_ins": 0}'::jsonb,
  'a second run rewrites nothing');
select is((select count(*)::int from after1 a join public.coach_week_reports r on r.id = a.id
           where r.snapshot is distinct from a.snapshot or r.body_v1 is distinct from a.body_v1), 0,
  'and leaves every snapshot and backup as they were');

-- Privileges and RLS.
select ok(not has_function_privilege('authenticated', 'public.coach_week_reports_backfill_v2()', 'execute')
          and not has_function_privilege('anon', 'public.coach_week_reports_backfill_v2()', 'execute')
          and not has_function_privilege('public', 'public.coach_week_reports_backfill_v2()', 'execute'),
  'the backfill is internal');
set local role authenticated;
select pg_temp.as_user(8);
select is((select count(*)::int from public.coach_week_reports where user_id = pg_temp.u(1)), 0,
  'user B cannot read A''s reports nor their backup');
select ok((select bool_and(body_v1 is not null) from public.coach_week_reports), 'user B reads the backup of its own rows');
select throws_ok('update public.coach_week_reports set body_v1 = null', '42501', null, 'a client cannot write body_v1');
select throws_ok($$select public.coach_week_reports_backfill_v2()$$, '42501', null, 'a client cannot call the backfill');
reset role;

-- ---------------------------------------------------------------------------
-- Late data: the live path takes over the refreshable row.
-- ---------------------------------------------------------------------------
update public.coach_week_reports r
set snapshot = jsonb_set(r.snapshot, '{progress,refreshed_at}', to_jsonb(now() - interval '11 minutes'))
where r.id = (pg_temp.r(1, 13)).id;
select pg_temp.weigh(1, 8);
select pg_temp.summary(1);
select is(jsonb_build_array(pg_temp.p(1, 13)->'weight'->'days', pg_temp.p(1, 13)->'refreshed_at'),
  jsonb_build_array(4, now()), 'coach_week_summary refreshes the backfilled row with late data');
select is((pg_temp.r(1, 13)).snapshot - 'progress' - 'schema_version', (pg_temp.r(1, 13)).body_v1,
  'the refresh leaves its v1 keys as stored');
select pg_temp.summary(1, 1);
select is(jsonb_build_array(pg_temp.p(1, 13)->'final', pg_temp.p(1, 6)->'final'), '[true, false]'::jsonb,
  'storing the next week finalises it');

select * from finish();
rollback;
