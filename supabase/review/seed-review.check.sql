-- pgTAP checks of the local-only rich dataset of the test user (supabase/seed-review.sql; T-278, US-131).
-- Run AFTER the dataset was applied: `python scripts/review-emulator.py --check` and the supabase-ci step "Rich dataset".
-- It cannot live in supabase/tests: `supabase test db` mounts only that folder, so an include of ../seed-review.sql
-- does not resolve there. It is also the drift guard of the RPCs the dataset calls: a changed RPC makes the dataset fail
-- to load or one of these checks fail. Everything runs in a transaction that is rolled back; finish(true) raises on a failure.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(28);

create function pg_temp.plan() returns uuid language sql as $$
  select id from public.coach_plans where user_id = '00000000-0000-4000-8000-000000000001' and status = 'accepted' $$;
grant execute on all functions in schema pg_temp to authenticated;

-- Another user, with no rows at all.
insert into auth.users (id, email) values ('b7000000-0000-4000-8000-000000000099', 'seedreview-other@example.com');

-- ---------------------------------------------------------------------------
-- Content, as the test user
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-000000000001","role":"authenticated"}', true);

select cmp_ok((select count(distinct log_date)::int from public.food_log_entries), '>=', 25, 'the diary has at least 25 days');
select cmp_ok((select count(distinct meal_slot_id)::int from public.food_log_entries
               where log_date = (now() at time zone 'utc')::date - 3), '>=', 3, 'a sample day has at least 3 comidas');
select cmp_ok((select count(*)::int from public.food_log_entries where food_id is null and food_name <> '' and kcal > 0),
  '>=', 1, 'an entry keeps its name and macros after its personal food was deleted');
select is((select count(*)::int from public.foods where name = 'Ensalada de pasta'), 0, 'the deleted personal food is gone');
select is((select count(*)::int from public.foods where source = 'user'), 3, 'three personal foods remain');
select cmp_ok((select count(*)::int from public.body_measurements), '>=', 30, 'there are at least 30 weight rows');
select is((select max(measured_at at time zone 'utc')::date from public.body_measurements), (now() at time zone 'utc')::date - 3,
  'the latest weigh-in is 3 days ago (the weight reminder is due)');
select is((select max(measured_at at time zone 'utc')::date from public.body_circumferences), (now() at time zone 'utc')::date - 20,
  'the circumferences were measured 20 days ago (the reminder is due)');
select is((select check_in_weekday::int from public.profiles where id = '00000000-0000-4000-8000-000000000001'),
  extract(isodow from (now() at time zone 'utc')::date)::int, 'today is the check-in day');
select is((select count(*)::int from public.weekly_check_ins), 3, 'the last 3 weeks are answered');
select is((select count(*)::int from public.weekly_check_ins
           where week_start >= date_trunc('week', (now() at time zone 'utc')::timestamp)::date), 0, 'the current week is not answered');

select is((select count(*)::int from public.coach_plans), 2, 'two plans: the finished block and the accepted one');
select is((select count(*)::int from public.coach_plans where status = 'accepted'), 1, 'one accepted plan');
select ok((select folder_id is not null from public.coach_plans where status = 'accepted'), 'the accepted plan has a saved block');
select cmp_ok((select count(*)::int from public.coach_plan_sessions s join public.coach_plans p on p.id = s.plan_id
               where p.status = 'accepted'), '>', 0, 'the accepted plan has sessions');
select cmp_ok((select count(*)::int from public.coach_plan_sessions s join public.coach_plans p on p.id = s.plan_id
               where p.status = 'accepted' and s.session_id is not null), '>=', 1, 'a finished session is linked to the plan');
select cmp_ok((select count(*)::int from public.workout_sets where duration_s is not null), '>=', 1, 'a cardio set exists');
select cmp_ok((select count(*)::int from public.coach_plan_extensions), '>=', 1, 'the block has an extension');
select cmp_ok((select count(*)::int from public.coach_week_reports where paused), '>=', 1, 'a paused week report is stored');
select cmp_ok((select count(*)::int from public.coach_week_reports where not paused), '>=', 1, 'a weekly report is stored');
select cmp_ok((select count(*)::int from public.coach_block_reviews), '>=', 1, 'a block review is stored');

select lives_ok($$select public.coach_plan_weeks(pg_temp.plan(), (now() at time zone 'utc')::date)$$, 'coach_plan_weeks reads the block');
select is((public.coach_week_summary((now() at time zone 'utc')::date))->>'seen', 'false', 'the weekly summary is unseen');
select cmp_ok(jsonb_array_length(public.list_coach_reports()), '>=', 3, 'the reports list holds the summaries and the review');

select is((select count(*)::int from public.workout_sessions where started_at > now()), 0, 'no session is in the future');
select is((select count(*)::int from public.body_measurements where measured_at > now()), 0, 'no weight is in the future');
select is((select count(*)::int from public.food_log_entries where log_date > (now() at time zone 'utc')::date), 0,
  'no diary entry is in the future');

-- ---------------------------------------------------------------------------
-- Isolation
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"b7000000-0000-4000-8000-000000000099","role":"authenticated"}', true);
select is((select count(*)::int from public.food_log_entries) + (select count(*)::int from public.body_measurements)
          + (select count(*)::int from public.coach_plans) + (select count(*)::int from public.workout_sessions)
          + (select count(*)::int from public.coach_week_reports) + (select count(*)::int from public.coach_block_reviews),
  0, 'another user sees none of the dataset');

select * from finish(true);
rollback;
