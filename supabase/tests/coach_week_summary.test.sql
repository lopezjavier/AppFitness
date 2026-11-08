-- pgTAP: coach_week_summary and mark_coach_week_summary_seen (T-231, US-98; rules in planning/04 section 6.7).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(51);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9310000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) $$ security definer;
create function pg_temp.summary() returns jsonb language sql as $$ select public.coach_week_summary(pg_temp.d0()) $$;

-- A user with a plan whose block started start_off days ago (4 weeks, 2 sessions a week when with_sessions), a diary
-- on the day offsets given (0 = today) and weigh-ins on the day offsets given.
create function pg_temp.mk(n int, p_status text, start_off int, with_sessions boolean, diary int[], weigh int[])
returns void language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); w int; i int;
begin
  update public.profiles set onboarding_completed = true where id = pg_temp.u(n);
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d0() - start_off, 'fat_loss',
          jsonb_build_object('rate_kg_week', -0.4, 'target_kcal', 2200), p_status,
          case when p_status = 'accepted' then now() end);
  if with_sessions then
    for w in 1..4 loop
      for i in 1..2 loop
        insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
        values (v_plan, pg_temp.u(n), w, i, 'Sesion ' || i, '[]');
      end loop;
    end loop;
  end if;
  -- T-242: a window without a workout is a paused week. These fixtures train in every window of the block so far
  -- (routine-less workouts that link to nothing); the paused summary is covered by coach_plan_weeks_pause.test.sql.
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 'Filler', 'completed', ((pg_temp.d0() - start_off + 7 * k + 1)::timestamp + interval '15 hours') at time zone 'UTC',
         ((pg_temp.d0() - start_off + 7 * k + 1)::timestamp + interval '16 hours') at time zone 'UTC'
  from generate_series(0, start_off / 7 - 1) k;
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - o,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000e1', 100, 'Alimento', 2000, 10, 10, 10
  from unnest(coalesce(diary, '{}')) o;
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), 80 + 0.1 * (o % 5), 'manual', ((pg_temp.d0() - o)::timestamp + interval '45 minutes') at time zone 'UTC'
  from unnest(coalesce(weigh, '{}')) o;
end $$;

grant execute on all functions in schema pg_temp to authenticated, anon;

insert into auth.users (id, email)
select pg_temp.u(n), 'wsum' || n || '@example.com' from generate_series(1, 12) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000e1', 'off', 'Alimento e1', 100);

-- User 1: block started 10 days ago, so week 1 (days -10..-4) is the latest past week. Full figures.
select pg_temp.mk(1, 'accepted', 10, true, array[10, 9, 8, 7, 6], array[10, 9, 8, 17, 24, 31]);
-- Gate edges: 4 diary days / 2 weigh-ins; 4 / 3; 5 / 2.
select pg_temp.mk(2, 'accepted', 10, true, array[10, 9, 8, 7], array[10, 9]);
select pg_temp.mk(8, 'accepted', 10, true, array[10, 9, 8, 7], array[10, 9, 8]);
select pg_temp.mk(9, 'accepted', 10, true, array[10, 9, 8, 7, 6], array[10, 9]);
-- Empty past week.
select pg_temp.mk(3, 'accepted', 10, true, null, null);
-- Week 1 has data but week 2 (the latest past week) is empty: no fallback.
select pg_temp.mk(4, 'accepted', 17, true, array[17, 16, 15, 14, 13], array[17, 16, 15]);
-- Proposed (not accepted) plan; accepted plan without a block (no plan sessions); first week still running.
select pg_temp.mk(5, 'proposed', 10, true, array[10, 9, 8, 7, 6], array[10, 9, 8]);
select pg_temp.mk(10, 'accepted', 10, false, array[10, 9, 8, 7, 6], array[10, 9, 8]);
select pg_temp.mk(11, 'accepted', 3, true, array[3, 2, 1], array[3, 2]);
-- Only one weigh-in and nothing else.
select pg_temp.mk(12, 'accepted', 10, true, null, array[9]);
-- User 6 has no plan.

-- User 1 workouts: bench (chest; triceps, shoulders) 4 working + 1 warm-up, a timed custom hold filed under chest and a
-- catalogue exercise that does not count for H1. The session is linked to plan week 1, session 1.
insert into public.exercises (name, muscle_group, secondary_muscles, equipment, metric_type, is_custom, created_by) values
  ('Press test', 'chest', '{triceps,shoulders,chest}', 'barbell', 'weight_reps', true, pg_temp.u(1)),
  ('Plancha test', 'chest', '{}', 'bodyweight', 'duration', true, pg_temp.u(1));
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('c9310000-0000-4000-8000-00000000f001', pg_temp.u(1), 'W1', 'completed',
   ((pg_temp.d0() - 9)::timestamp + interval '10 hours') at time zone 'UTC',
   ((pg_temp.d0() - 9)::timestamp + interval '11 hours') at time zone 'UTC');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, duration_s, completed_at)
select 'c9310000-0000-4000-8000-00000000f001', (select id from public.exercises where name = 'Press test'), 0, s, 'working', 100, 5, null,
       ((pg_temp.d0() - 9)::timestamp + interval '10 hours 5 minutes') at time zone 'UTC'
from generate_series(1, 4) s;
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, duration_s, completed_at) values
  ('c9310000-0000-4000-8000-00000000f001', (select id from public.exercises where name = 'Press test'), 0, 5, 'warm_up', 40, 5, null,
   ((pg_temp.d0() - 9)::timestamp + interval '10 hours 1 minute') at time zone 'UTC'),
  ('c9310000-0000-4000-8000-00000000f001', (select id from public.exercises where name = 'Plancha test'), 1, 1, 'working', 0, 1, 60,
   ((pg_temp.d0() - 9)::timestamp + interval '10 hours 20 minutes') at time zone 'UTC'),
  ('c9310000-0000-4000-8000-00000000f001',
   (select id from public.exercises where not is_custom and not counts_as_strength and metric_type = 'reps_only' limit 1),
   2, 1, 'working', 0, 10, null, ((pg_temp.d0() - 9)::timestamp + interval '10 hours 30 minutes') at time zone 'UTC');
update public.coach_plan_sessions set session_id = 'c9310000-0000-4000-8000-00000000f001'
  where plan_id = pg_temp.plan_of(1) and week = 1 and session_index = 1;
update public.coach_plan_sessions set deload = true where plan_id = pg_temp.plan_of(1) and week = 1;

-- A change and a calorie proposal inside week 1 (8 days ago) and others outside it (today).
insert into public.coach_changes (user_id, plan_id, kind, exercise_id, reason, status, created_at) values
  (pg_temp.u(1), pg_temp.plan_of(1), 'load_kept', (select id from public.exercises where not is_custom order by key limit 1),
   'kept_discomfort', 'applied', ((pg_temp.d0() - 8)::timestamp + interval '12 hours') at time zone 'UTC'),
  (pg_temp.u(1), pg_temp.plan_of(1), 'load_kept', (select id from public.exercises where not is_custom order by key limit 1),
   'kept_discomfort', 'applied', now());
insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary, status, created_at, decided_at) values
  (pg_temp.u(1), pg_temp.plan_of(1), 'calorie_change', '{"change_kcal": -100, "from_kcal": 2200, "to_kcal": 2100}',
   'rate_outside_interval', '{}', 'accepted', ((pg_temp.d0() - 7)::timestamp + interval '9 hours') at time zone 'UTC',
   ((pg_temp.d0() - 7)::timestamp + interval '10 hours') at time zone 'UTC'),
  (pg_temp.u(1), pg_temp.plan_of(1), 'calorie_change', '{"change_kcal": 50, "from_kcal": 2100, "to_kcal": 2150}',
   'rate_outside_interval', '{}', 'pending', now(), null);

-- ---------------------------------------------------------------------------
-- Structure and privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_week_summary(date)', 'execute')
      and not has_function_privilege('anon', 'public.mark_coach_week_summary_seen(uuid, integer)', 'execute'),
  'anon cannot execute the summary RPCs');
select ok(has_function_privilege('authenticated', 'public.coach_week_summary(date)', 'execute')
      and has_function_privilege('authenticated', 'public.mark_coach_week_summary_seen(uuid, integer)', 'execute'),
  'authenticated can execute the summary RPCs');
select ok((select relrowsecurity from pg_class where oid = 'public.coach_week_summary_seen'::regclass),
  'RLS is enabled on coach_week_summary_seen');
select ok('week_summary' = any (enum_range(null::public.notification_kind)::text[]), 'notification_kind has week_summary');

-- ---------------------------------------------------------------------------
-- User 1: every figure of the latest finished week
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);
create temp table s1 as select pg_temp.summary() as j;
grant select on s1 to authenticated;

select is((select j->>'plan_id' from s1), pg_temp.plan_of(1)::text, 'the summary is of the accepted plan');
select is((select (j->>'week')::int from s1), 1, 'the latest past week is week 1');
select is((select (j->>'start_date') || ' ' || (j->>'end_date') from s1),
  (pg_temp.d0() - 10)::text || ' ' || (pg_temp.d0() - 4)::text, 'the week range is the plan week');
select is((select (j->>'sessions_done')::int from s1), 1, 'one workout done');
select is((select (j->>'sessions_planned')::int from s1), 2, 'two workouts planned');
select is((select (e->>'sets')::numeric from s1, jsonb_array_elements(j->'groups') e where e->>'muscle_group' = 'chest'),
  4::numeric, 'chest gets 4 (primary 1 per working set; warm-up, timed hold and non-strength exercise excluded)');
select is((select (e->>'sets')::numeric from s1, jsonb_array_elements(j->'groups') e where e->>'muscle_group' = 'triceps'),
  2::numeric, 'triceps gets 2 (secondary counts half)');
select is((select (e->>'sets')::numeric from s1, jsonb_array_elements(j->'groups') e where e->>'muscle_group' = 'shoulders'),
  2::numeric, 'shoulders get 2 (secondary counts half)');
select is((select jsonb_array_length(j->'groups') from s1), 3, 'no other group appears');
select is((select (j->'weight'->>'avg_kg')::numeric from s1), 80.23, 'average weight of the week');
select isnt((select j->'weight'->'slope_kg_week' from s1), 'null'::jsonb, 'the trend slope is filled when there is data');
select is((select (j->'weight'->>'target_rate_kg_week')::numeric from s1), -0.4, 'target rate comes from the plan');
select is((select (j->>'complete_days')::int from s1), 5, 'five complete diary days');
select is((select (j->>'weigh_in_days')::int from s1), 3, 'three weigh-in days');
select is((select (j->>'missing_records')::boolean from s1), false, 'five diary days and three weigh-ins are enough');
select is((select jsonb_array_length(j->'changes') from s1), 1, 'only the change created inside the week is listed');
select is((select j->'changes'->0->>'reason' from s1), 'kept_discomfort', 'a change carries its table columns');
select is((select j->'calorie_proposal'->>'status' || ' ' || (j->'calorie_proposal'->'payload'->>'change_kcal') from s1),
  'accepted -100', 'the calorie proposal created in the week is included with its status');
select is((select (j->>'deload')::boolean from s1), true, 'the deload mark of the week');
select is((select (j->>'seen')::boolean from s1), false, 'not seen yet');

-- ---------------------------------------------------------------------------
-- Seen state
-- ---------------------------------------------------------------------------
select lives_ok($$select public.mark_coach_week_summary_seen(pg_temp.plan_of(1), 1)$$, 'mark seen works');
select is((pg_temp.summary())->>'seen', 'true', 'the summary reports seen after it (state is stored per user)');
select lives_ok($$select public.mark_coach_week_summary_seen(pg_temp.plan_of(1), 1)$$, 'marking twice is allowed');
select is((select count(*)::int from public.coach_week_summary_seen), 1, 'marking twice stores one row');
select lives_ok($$select public.mark_coach_week_summary_seen(pg_temp.plan_of(1), 2)$$, 'another week of the plan can be marked');
select is((select count(*)::int from public.coach_week_summary_seen), 2, 'seen is kept per plan and week');
select is((pg_temp.summary())->>'week', '1', 'week 1 stays the summary week');
select throws_ok($$select public.mark_coach_week_summary_seen(pg_temp.plan_of(1), 0)$$, '22023', null, 'week 0 is refused');
select throws_ok($$insert into public.coach_week_summary_seen (plan_id, week) values (pg_temp.plan_of(1), 3)$$,
  '42501', null, 'clients cannot insert seen rows directly');
select throws_ok($$update public.coach_week_summary_seen set week = 9$$, '42501', null, 'clients cannot update seen rows');

-- The statistics function counts the hold and the non-strength exercise; the strength-only variant does not.
select cmp_ok((select sets from public.muscle_group_sets(pg_temp.d0() - 10, pg_temp.d0() - 4) where muscle_group = 'chest'), '>',
  (select sets from public.muscle_group_sets(pg_temp.d0() - 10, pg_temp.d0() - 4, true) where muscle_group = 'chest'),
  'the strength-only counting drops non-strength sets that the statistics keep');

-- ---------------------------------------------------------------------------
-- Data gate edges and empty cases
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is((pg_temp.summary())->>'missing_records', 'true', '4 diary days and 2 weigh-ins: missing records');
select pg_temp.as_user(8);
select is((pg_temp.summary())->>'missing_records', 'true', '4 diary days and 3 weigh-ins: missing records');
select pg_temp.as_user(9);
select is((pg_temp.summary())->>'missing_records', 'true', '5 diary days and 2 weigh-ins: missing records');

select pg_temp.as_user(3);
select is(pg_temp.summary(), null, 'a past week with no workout, weigh-in or diary day has no summary');
select pg_temp.as_user(4);
select is(pg_temp.summary(), null, 'the latest past week is empty and older weeks are not used');
select pg_temp.as_user(5);
select is(pg_temp.summary(), null, 'a proposed plan has no summary');
select pg_temp.as_user(10);
select is(pg_temp.summary(), null, 'an accepted plan without a block has no summary');
select pg_temp.as_user(11);
select is(pg_temp.summary(), null, 'no finished week yet: no summary');
select pg_temp.as_user(12);
select is((pg_temp.summary())->>'weigh_in_days', '1', 'one weigh-in alone makes the week non-empty');
select is((pg_temp.summary())->>'sessions_done', '0', 'a week with only a weigh-in has no workouts done');
select is((pg_temp.summary())->'groups', '[]'::jsonb, 'and no sets by group');
select is((pg_temp.summary())->>'deload', 'false', 'and no deload');

-- ---------------------------------------------------------------------------
-- Isolation
-- ---------------------------------------------------------------------------
select pg_temp.as_user(6);
select is(pg_temp.summary(), null, 'a user with no plan has no summary');
select throws_ok($$select public.mark_coach_week_summary_seen(pg_temp.plan_of(1), 1)$$, 'P0002', null,
  'user B cannot mark user A''s plan');
select is((select count(*)::int from public.coach_week_summary_seen), 0, 'user B sees none of user A''s seen rows');
select set_config('request.jwt.claims', '{}', true);
select throws_ok($$select pg_temp.summary()$$, '28000', null, 'no authenticated user: refused');

select * from finish();
rollback;
