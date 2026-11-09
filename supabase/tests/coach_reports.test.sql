-- pgTAP: stored reports (T-261, US-129; rules in planning/04 sections 6.7, 6.9 and 6.11).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(56);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9610000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) $$ security definer;
create function pg_temp.rows_of(n int) returns int language sql as $$
  select count(*)::int from public.coach_week_reports where user_id = pg_temp.u(n) $$ security definer;
create function pg_temp.summary() returns jsonb language sql as $$ select public.coach_week_summary(pg_temp.d0()) $$;
create function pg_temp.report_id(p_kind text) returns uuid language sql as $$
  select (e->>'id')::uuid from jsonb_array_elements(public.list_coach_reports()) e where e->>'kind' = p_kind $$;

-- A user with a plan whose block started start_off days ago (4 weeks, 2 sessions a week), a diary on the day offsets
-- given (0 = today) and weigh-ins on the day offsets given. The user trains in every window so far (routine-less
-- workouts), so no week is paused.
create function pg_temp.mk(n int, start_off int, diary int[], weigh int[], kg numeric default 80)
returns void language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); w int; i int;
begin
  update public.profiles set onboarding_completed = true where id = pg_temp.u(n);
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d0() - start_off, 'fat_loss',
          jsonb_build_object('rate_kg_week', -0.4, 'target_kcal', 2200), 'accepted', now());
  for w in 1..4 loop
    for i in 1..2 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
      values (v_plan, pg_temp.u(n), w, i, 'Sesion ' || i, '[]');
    end loop;
  end loop;
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 'Filler', 'completed',
         ((pg_temp.d0() - start_off + 7 * k + 1)::timestamp + interval '15 hours') at time zone 'UTC',
         ((pg_temp.d0() - start_off + 7 * k + 1)::timestamp + interval '16 hours') at time zone 'UTC'
  from generate_series(0, start_off / 7 - 1) k;
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - o,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000e1', 100, 'Alimento', 2000, 10, 10, 10
  from unnest(coalesce(diary, '{}')) o;
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg + 0.1 * (o % 5), 'manual', ((pg_temp.d0() - o)::timestamp + interval '45 minutes') at time zone 'UTC'
  from unnest(coalesce(weigh, '{}')) o;
end $$;

create temp table res (k text primary key, j jsonb);
grant all on res to authenticated;
grant execute on all functions in schema pg_temp to authenticated, anon;

insert into auth.users (id, email)
select pg_temp.u(n), 'rep' || n || '@example.com' from generate_series(1, 7) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000e1', 'off', 'Alimento e1', 100);

-- User 1 (A): block started 10 days ago, plan week 1 (days -10..-4) is the latest finished week.
select pg_temp.mk(1, 10, array[10, 9, 8, 7, 6], array[10, 9, 8, 17, 24, 31]);
-- User 2 (B): same window, very different figures.
select pg_temp.mk(2, 10, array[10, 9, 8, 7, 6, 5, 4], array[10, 9, 8, 7, 6, 5, 4], 120);
-- User 3: the window before the current one is empty (a paused week).
select pg_temp.mk(3, 15, array[15, 14], array[15, 14]);
delete from public.workout_sessions where user_id = pg_temp.u(3) and started_at >= (pg_temp.d0() - 8)::timestamp at time zone 'UTC';
-- User 7: windows 1 and 2 are empty (two paused windows in a row; today is in window 3).
select pg_temp.mk(7, 22, array[22], array[22]);
delete from public.workout_sessions where user_id = pg_temp.u(7) and started_at >= (pg_temp.d0() - 15)::timestamp at time zone 'UTC'
  and started_at < (pg_temp.d0() - 1)::timestamp at time zone 'UTC';
-- User 5: same window as A and B but a single diary day and no weigh-in (leak probe).
select pg_temp.mk(5, 10, array[9], null);
-- User 6: a block that ended yesterday (4 plan weeks, all windows trained), saved folder, for the Trainer review.
select pg_temp.mk(6, 28, null, null);
insert into public.routine_folders (user_id, name) values (pg_temp.u(6), 'Bloque 6');
update public.coach_plans set folder_id = (select id from public.routine_folders where user_id = pg_temp.u(6))
  where user_id = pg_temp.u(6);

-- ---------------------------------------------------------------------------
-- 1. A report is kept: stored once, on the first build.
-- ---------------------------------------------------------------------------
select is(pg_temp.rows_of(1), 0, 'no report exists before any summary is built (nothing is created by the migration)');

set local role authenticated;
select pg_temp.as_user(1);
insert into res select 'first', pg_temp.summary();
select is((select j->>'week' from res where k = 'first'), '1', 'the first call returns the finished plan week');
reset role;
select is(pg_temp.rows_of(1), 1, 'the first build stores exactly one report');

set local role authenticated;
select pg_temp.as_user(1);
select pg_temp.summary();
reset role;
select is(pg_temp.rows_of(1), 1, 'a second call stores nothing new');
select is((select count(*)::int from public.coach_week_reports where user_id = pg_temp.u(1) and snapshot ? 'seen'), 0,
  'the stored snapshot holds figures only, not the seen state');
select is((select period_end - period_start from public.coach_week_reports where user_id = pg_temp.u(1)), 6,
  'the report covers the seven days of the plan week');

-- ---------------------------------------------------------------------------
-- 2. Frozen: data logged afterwards does not change the figures.
-- ---------------------------------------------------------------------------
insert into public.body_measurements (user_id, weight_kg, source, measured_at)
values (pg_temp.u(1), 99, 'manual', ((pg_temp.d0() - 5)::timestamp + interval '1 hour') at time zone 'UTC');
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
values (pg_temp.u(1), pg_temp.d0() - 5, (select id from public.meal_slots where user_id = pg_temp.u(1) and code = 'dinner'),
        '0ff00000-0000-4000-8000-0000000000e1', 100, 'Cena', 900, 10, 10, 10);
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
values (pg_temp.u(1), 'Late', 'completed', ((pg_temp.d0() - 5)::timestamp + interval '9 hours') at time zone 'UTC',
        ((pg_temp.d0() - 5)::timestamp + interval '10 hours') at time zone 'UTC');

set local role authenticated;
select pg_temp.as_user(1);
insert into res select 'second', pg_temp.summary();
select is((select j - 'seen' from res where k = 'second'), (select j - 'seen' from res where k = 'first'),
  'the second call returns the same figures after weights, diary and a workout changed');
select is((select (public.get_coach_report(pg_temp.report_id('week_summary')))->'snapshot'),
          (select j - 'seen' from res where k = 'first'),
  'get_coach_report returns exactly the stored figures');
select is((select j->>'seen' from res where k = 'first'), 'false', 'the first call reports the week as not seen');
select public.mark_coach_week_summary_seen(pg_temp.plan_of(1), 1);
select is((pg_temp.summary())->>'seen', 'true', 'seen is read live: marking it later shows on the stored report');
select is((pg_temp.summary()) - 'seen', (select j - 'seen' from res where k = 'first'),
  'marking seen does not alter the figures');

-- ---------------------------------------------------------------------------
-- 3. Leak probe: B has data in the same window; user 5 has none, so nothing of B can appear in user 5's report.
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select pg_temp.summary();
select pg_temp.as_user(5);
insert into res select 'empty', pg_temp.summary();
reset role;
select is((select j->'weight'->>'avg_kg' from res where k = 'empty'), null, 'another user''s weights never reach a report');
select is((select j->>'complete_days' from res where k = 'empty'), '1', 'another user''s diary never reaches a report');
select is(pg_temp.rows_of(5), 1, 'each user gets their own report row');

-- ---------------------------------------------------------------------------
-- 4. A paused week is returned and not stored.
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(3);
select is((pg_temp.summary())->>'paused', 'true', 'a paused week still returns the paused summary');
reset role;
select is(pg_temp.rows_of(3), 0, 'a paused week writes no report');

set local role authenticated;
select pg_temp.as_user(3);
insert into res select 'paused1', public.record_paused_week_report(pg_temp.d0());
select public.record_paused_week_report(pg_temp.d0());
reset role;
select is(pg_temp.rows_of(3), 1, 'recording a paused week stores one row, idempotently');
select is((select j->>'paused' from res where k = 'paused1'), 'true', 'the stored paused snapshot is returned');
select ok((select week is null and paused from public.coach_week_reports where user_id = pg_temp.u(3)),
  'a paused row has no plan week');
set local role authenticated;
select pg_temp.as_user(1);
select is(public.record_paused_week_report(pg_temp.d0()), null, 'a normal week records no paused report');
reset role;
select is(pg_temp.rows_of(1), 1, 'and writes nothing');

-- Two paused windows in a row, then a real week: no collision.
insert into public.coach_week_reports (user_id, plan_id, week, paused, period_start, period_end, snapshot)
values (pg_temp.u(7), pg_temp.plan_of(7), null, true, pg_temp.d0() - 22 + 7, pg_temp.d0() - 22 + 13, '{}');
set local role authenticated;
select pg_temp.as_user(7);
select public.record_paused_week_report(pg_temp.d0());
reset role;
select is(pg_temp.rows_of(7), 2, 'two consecutive paused windows give two rows');
insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
values (pg_temp.u(7), pg_temp.plan_of(7), 1, pg_temp.d0() - 22, pg_temp.d0() - 16, '{}');
select is(pg_temp.rows_of(7), 3, 'a real week next to paused windows is stored without collision');
select throws_ok(format('insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot) values (%L, %L, null, current_date, current_date, ''{}'')', pg_temp.u(7), pg_temp.plan_of(7)),
  '23514', null, 'a non-paused row needs a plan week');
set local role authenticated;
select pg_temp.as_user(7);
select is((select count(*)::int from jsonb_array_elements(public.list_coach_reports()) e where e->>'paused' = 'true' and e->>'week' is null), 2,
  'the list shows paused rows with a null week and paused true');
reset role;

-- ---------------------------------------------------------------------------
-- 5. The list, newest first, with the block review listed live.
-- ---------------------------------------------------------------------------
insert into public.coach_block_reviews (user_id, plan_id, ended_on, inputs, phase_proposal, phase_decision, decided_at)
values (pg_temp.u(1), pg_temp.plan_of(1), pg_temp.d0() - 4, '{}',
        '{"to_phase":"maintenance","reason":"cut_length","figures":{}}', 'kept', now());
update public.coach_week_reports set created_at = now() - interval '2 days' where user_id = pg_temp.u(1);
update public.coach_block_reviews set created_at = now() - interval '1 day' where user_id = pg_temp.u(1);

set local role authenticated;
select pg_temp.as_user(1);
select is(jsonb_array_length(public.list_coach_reports()), 2, 'the list holds the weekly and the block report');
select is((public.list_coach_reports())->0->>'kind', 'block_review', 'the newest report comes first');
select is((public.list_coach_reports())->1->>'kind', 'week_summary', 'the older report comes last');
select is((public.list_coach_reports())->0->>'week', null, 'a block report has no week number');
select is((public.list_coach_reports())->0->>'phase_decision', 'kept', 'a block report carries the decision taken');
select is((public.list_coach_reports())->1->>'week', '1', 'a weekly report carries its plan week');
select ok((public.list_coach_reports())->0->>'period_end' = (pg_temp.d0() - 4)::text,
  'a block report ends on the last day of the block');

-- ---------------------------------------------------------------------------
-- 6. One report by id.
-- ---------------------------------------------------------------------------
select is((public.get_coach_report(pg_temp.report_id('week_summary')))->>'kind', 'week_summary', 'a weekly report is read by id');
select is((public.get_coach_report(pg_temp.report_id('block_review')))->'snapshot'->>'phase_decision', 'kept',
  'a block report body carries the decision taken');
select is((public.get_coach_report(pg_temp.report_id('block_review')))->'snapshot'->'seen_at', 'null'::jsonb,
  'a block report is unseen until it is marked');
select is(public.get_coach_report(gen_random_uuid()), null, 'an unknown id returns null');

-- ---------------------------------------------------------------------------
-- 7. Seen mark of the block review.
-- ---------------------------------------------------------------------------
insert into res select 'mark1', public.mark_coach_block_review_seen(pg_temp.report_id('block_review'));
select isnt((select j->>'seen_at' from res where k = 'mark1'), null, 'opening the notice sets seen_at');
select is(public.mark_coach_block_review_seen(pg_temp.report_id('block_review'))->>'seen_at',
          (select j->>'seen_at' from res where k = 'mark1'), 'marking twice keeps the first timestamp');
select is((select j->>'phase_decision' from res where k = 'mark1'), 'kept', 'marking seen leaves the decision untouched');
select is(jsonb_array_length(public.list_coach_reports()), 2, 'a seen review stays in the list');

-- ---------------------------------------------------------------------------
-- 8. Two users.
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is((select count(*)::int from jsonb_array_elements(public.list_coach_reports()) e where e->>'plan_id' <> pg_temp.plan_of(2)::text), 0,
  'B sees none of A''s reports in the list');
select is((select count(*)::int from public.coach_week_reports where user_id = pg_temp.u(1)), 0, 'B cannot select A''s report rows');
select is(public.get_coach_report((select id from public.coach_week_reports where user_id = pg_temp.u(5))), null,
  'B reads no weekly report of another user by id');
select throws_ok(format('select public.mark_coach_block_review_seen(%L)', (select id from public.coach_block_reviews limit 1)),
  'P0002', 'review not found', 'B cannot mark A''s review');
select throws_ok(
  format('insert into public.coach_week_reports (plan_id, week, period_start, period_end, snapshot) values (%L, 9, current_date, current_date, ''{}'')',
         pg_temp.plan_of(2)),
  '42501', null, 'a user cannot insert reports directly');
select throws_ok('update public.coach_week_reports set snapshot = ''{}''', '42501', null, 'a user cannot update reports');
select throws_ok('delete from public.coach_week_reports', '42501', null, 'a user cannot delete reports');

reset role;
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_week_reports'::regclass),
  'RLS is enabled and forced on coach_week_reports');
select ok(not has_function_privilege('anon', 'public.coach_week_summary(date)', 'execute')
          and not has_function_privilege('anon', 'public.list_coach_reports()', 'execute')
          and not has_function_privilege('anon', 'public.get_coach_report(uuid)', 'execute')
          and not has_function_privilege('anon', 'public.mark_coach_block_review_seen(uuid)', 'execute'),
  'anon cannot execute any report function');
select ok(not has_function_privilege('authenticated', 'public.coach_week_summary_build(date)', 'execute'),
  'the internal builder is not callable by clients');

-- ---------------------------------------------------------------------------
-- 9. The Trainer review is unchanged by the mark.
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(6);
insert into res select 'open1', public.open_coach_block_review(pg_temp.d0());
select isnt((select j->>'id' from res where k = 'open1'), null, 'the review opens for a finished block');
select public.mark_coach_block_review_seen((select (j->>'id')::uuid from res where k = 'open1'));
select is(public.open_coach_block_review(pg_temp.d0())->>'id', (select j->>'id' from res where k = 'open1'),
  'after the mark the Trainer still returns the review');
select isnt(public.open_coach_block_review(pg_temp.d0())->>'seen_at', null, 'the review JSON carries seen_at');
select is(public.open_coach_block_review(pg_temp.d0())->'phase_decision', 'null'::jsonb, 'the open proposal is still open');
reset role;

-- ---------------------------------------------------------------------------
-- 10. Reminders are not stored.
-- ---------------------------------------------------------------------------
select hasnt_column('public', 'coach_week_reports', 'kind', 'the table has no reminder kind: only weekly summaries are stored');
select is((select count(*)::int from public.coach_week_reports), 7, 'rows exist only for the summaries that were built');

select * from finish();
rollback;
