-- pgTAP: coach derived values and their confirmation (T-157, US-68/US-69; rules in planning/04 section 7).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(115);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner; today is the UTC date, inside the RPC window)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- The user n as a uuid: c0000000-0000-4000-8000-0000000000NN.
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c0000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

-- Male, 26 years old, 178 cm, with the given activity level and (optionally) a weight.
create function pg_temp.profile(n int, lvl public.activity_level, kg numeric default 74.2) returns void
language plpgsql as $$
begin
  update public.profiles
  set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178,
      activity_level = lvl, onboarding_completed = true
  where id = pg_temp.u(n);
  if kg is not null then
    insert into public.body_measurements (user_id, weight_kg, source, measured_at)
    values (pg_temp.u(n), kg, 'manual', now());
  end if;
end $$;

create function pg_temp.steps(n int, days int, v int) returns void language sql as $$
  insert into public.daily_activity (user_id, day, steps)
  select pg_temp.u(n), pg_temp.d0() - i, v from generate_series(0, days - 1) i $$;

create function pg_temp.kcal(n int, days int, v numeric) returns void language sql as $$
  insert into public.daily_activity (user_id, day, active_kcal)
  select pg_temp.u(n), pg_temp.d0() - i, v from generate_series(0, days - 1) i
  on conflict (user_id, day) do update set active_kcal = excluded.active_kcal $$;

-- cnt completed sessions: one first_days_ago days back, the others spread over the last days.
create function pg_temp.sessions(n int, cnt int, first_days_ago int default null) returns void
language plpgsql as $$
begin
  if first_days_ago is not null then
    insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
    values (pg_temp.u(n), 's', 'completed', now() - make_interval(days => first_days_ago),
            now() - make_interval(days => first_days_ago) + interval '1 hour');
    cnt := cnt - 1;
  end if;
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 's', 'completed', now() - make_interval(mins => i * 30),
         now() - make_interval(mins => i * 30) + interval '10 minutes'
  from generate_series(1, cnt) i;
end $$;

-- One completed session every 2 days starting today (cnt of them): cnt = 12 gives 3 per week in the 28-day window.
create function pg_temp.weekly_sessions(n int, cnt int) returns void language sql as $$
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 's', 'completed', now() - make_interval(days => i * 2),
         now() - make_interval(days => i * 2) + interval '1 hour'
  from generate_series(0, cnt - 1) i $$;

-- One weigh-in on the Monday of the week that started weeks_ago weeks before the current one.
create function pg_temp.weigh(n int, weeks_ago int, kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg,
         'manual',
         ((pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) - 7 * weeks_ago)::timestamp + interval '30 minutes')
           at time zone 'UTC' $$;

-- Weekly weigh-ins, oldest first, the last one in the current week.
create function pg_temp.weights(n int, vals numeric[]) returns void language plpgsql as $$
begin
  for i in 1..array_length(vals, 1) loop
    perform pg_temp.weigh(n, array_length(vals, 1) - i, vals[i]);
  end loop;
end $$;

create function pg_temp.diary(n int, days int, kcal numeric) returns void language sql as $$
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - i,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000c1', 100, 'Alimento', kcal, 10, 10, 10
  from generate_series(0, days - 1) i $$;

-- Estimates / confirmation as user n (sets the JWT claims for the rest of the transaction).
create function pg_temp.est(n int) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.coach_derived_estimates(pg_temp.d0());
end $$;

create function pg_temp.cfm(n int, lvl public.activity_level default null) returns jsonb language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true);
  return public.confirm_derived_values(pg_temp.d0(), lvl);
end $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'coach' || n || '@example.com'
from unnest(array[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 20, 21, 22, 23, 24, 30, 31, 32, 33, 34, 35, 36,
                  40, 41, 42, 43, 44, 45, 46, 50, 51, 52, 54]) n;

insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c1', 'off', 'Alimento c', 100);

-- 01: the 04 section 1.3 example (male, 26, 178 cm, 74.2 kg, moderate), no other data.
select pg_temp.profile(1, 'moderate');
-- 02: nothing at all (no profile data, no level).
-- 03..07: average steps at each edge of the bands, profile says sedentary.
select pg_temp.profile(n, 'sedentary') from generate_series(3, 7) n;
select pg_temp.steps(3, 10, 4999), pg_temp.steps(4, 10, 5000), pg_temp.steps(5, 10, 7500),
       pg_temp.steps(6, 10, 10000), pg_temp.steps(7, 10, 12500);
-- 08..10: sessions raise a measured level by one, capped at very_high.
select pg_temp.profile(n, 'sedentary') from generate_series(8, 10) n;
select pg_temp.steps(8, 10, 5000), pg_temp.steps(9, 10, 5000), pg_temp.steps(10, 10, 12500);
select pg_temp.weekly_sessions(8, 12), pg_temp.weekly_sessions(9, 8), pg_temp.weekly_sessions(10, 12);
-- 11: only 6 days of steps (not enough). 12: active kcal without steps. 13: both. 14: sessions without any data.
select pg_temp.profile(11, 'light'), pg_temp.profile(12, 'sedentary'), pg_temp.profile(13, 'sedentary'),
       pg_temp.profile(14, 'light');
select pg_temp.steps(11, 6, 10000), pg_temp.kcal(12, 10, 400), pg_temp.steps(13, 10, 5000);
select pg_temp.kcal(13, 10, 800), pg_temp.weekly_sessions(14, 12);
-- 20..24: weight trends. 20: 4 weeks, 21: 3 weeks (a perfect line), 22: 2 weeks, 23: 1 week,
-- 24: two weigh-ins on the same day count as their average.
select pg_temp.weights(20, array[80, 79, 79, 78]), pg_temp.weights(21, array[80, 79, 78]),
       pg_temp.weights(22, array[79, 78]), pg_temp.weights(23, array[78]);
select pg_temp.weigh(24, 1, 82), pg_temp.weigh(24, 0, 79), pg_temp.weigh(24, 0, 81);

-- Diary users need their meal slots (created when onboarding completes).
update public.profiles set onboarding_completed = true where id in (select pg_temp.u(n) from generate_series(30, 36) n);
-- 30: 17 days of 28 (60.7 %), 31: 20 (71.4 %), 32: 19 (67.9 %), 33: none.
select pg_temp.diary(30, 17, 2000), pg_temp.diary(31, 20, 2000), pg_temp.diary(32, 19, 2000);
-- 34: reliable diary and 4 weeks of weights; 35: unreliable diary, same weights; 36: reliable diary, 2 weeks.
select pg_temp.profile(34, 'sedentary', null), pg_temp.profile(35, 'sedentary', null), pg_temp.profile(36, 'sedentary', null);
select pg_temp.weights(34, array[80, 79, 79, 78]), pg_temp.weights(35, array[80, 79, 79, 78]), pg_temp.weights(36, array[79, 78]);
select pg_temp.diary(34, 20, 2000), pg_temp.diary(35, 19, 2000), pg_temp.diary(36, 20, 2000);
-- 40..46: experience boundaries.
select pg_temp.sessions(40, 23, 100), pg_temp.sessions(41, 24, 100), pg_temp.sessions(42, 24, 77),
       pg_temp.sessions(43, 24, 84), pg_temp.sessions(44, 150, 364), pg_temp.sessions(45, 150, 357);
-- 50: confirmation and re-ask. 51: edited level. 52: the edge of the uncertainty. 54: no data.
select pg_temp.profile(50, 'sedentary'), pg_temp.profile(51, 'light'), pg_temp.profile(52, 'sedentary');
select pg_temp.steps(50, 10, 7500), pg_temp.steps(52, 10, 7500);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'coach_value_key',
  array['activity_level', 'total_expenditure', 'weight_trend', 'experience'], 'coach_value_key has the four values');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_confirmed_values'::regclass),
  'RLS is enabled and forced on coach_confirmed_values');
select table_privs_are('public', 'coach_confirmed_values', 'anon', array[]::text[],
  'anon has no privileges on coach_confirmed_values');
select ok(not has_table_privilege('authenticated', 'public.coach_confirmed_values', 'delete'),
  'authenticated cannot delete confirmations directly');
select ok(not has_function_privilege('anon', 'public.coach_derived_estimates(date)', 'execute'),
  'anon cannot execute coach_derived_estimates');
select ok(not has_function_privilege('anon', 'public.confirm_derived_values(date, public.activity_level)', 'execute'),
  'anon cannot execute confirm_derived_values');
select ok(has_function_privilege('authenticated', 'public.coach_derived_estimates(date)', 'execute'),
  'authenticated can execute coach_derived_estimates');
select is(public.coach_e1rm(100, 5), 116.5::numeric, 'e1RM of 100 x 5 is 116.5 (Epley, 04 section 4.1)');
select is(public.coach_e1rm(85, 8), 107.5::numeric, 'e1RM of 85 x 8 is 107.5');
select is(public.coach_e1rm(100, 1), 100::numeric, 'e1RM of a single is the weight');
select is(public.coach_e1rm(100, 13), null, 'more than 12 reps do not count for e1RM');

set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$select public.coach_derived_estimates(current_date)$$, '28000', null,
  'coach_derived_estimates requires a user');
select throws_ok($$select public.confirm_derived_values(current_date)$$, '28000', null,
  'confirm_derived_values requires a user');
select set_config('request.jwt.claims',
  json_build_object('sub', pg_temp.u(54), 'role', 'authenticated')::text, true);
select throws_ok(format($$select public.coach_derived_estimates(%L)$$, (now() at time zone 'utc')::date + 2),
  '22023', null, 'a date two days ahead of UTC today is refused');
select throws_ok(format($$select public.confirm_derived_values(%L)$$, (now() at time zone 'utc')::date - 2),
  '22023', null, 'confirm refuses a date two days before UTC today');

-- ---------------------------------------------------------------------------
-- Basal and total expenditure (04 section 1.3 example)
-- ---------------------------------------------------------------------------
select is((pg_temp.est(1)->'basal_kcal'->>'value')::numeric, 1729.5::numeric, 'basal expenditure of the example is 1729.5');
select is((pg_temp.est(1)->'total_expenditure'->>'value')::numeric, 2681::numeric, 'total expenditure of the example is 2681');
select is(pg_temp.est(1)->'total_expenditure'->>'source', 'formula', 'without history the expenditure comes from the formula');
select is(pg_temp.est(1)->'activity_level'->>'value', 'moderate', 'with no data the profile level is the activity level');
select is(pg_temp.est(1)->'activity_level'->>'source', 'profile', 'and its source is the profile');
select is((pg_temp.est(1)->'activity_level'->>'provisional')::boolean, true, 'an unconfirmed value is provisional');
select is((pg_temp.est(1)->'activity_level'->>'confirmed')::boolean, false, 'and not confirmed');
select is(pg_temp.est(1)->'activity_level'->'missing', '["steps", "active_kcal", "training_history"]'::jsonb,
  'the missing data is listed');
select is((pg_temp.est(1)->'activity_level'->>'uncertainty')::numeric, 0.2::numeric, 'a profile-only level has uncertainty 0.2');
select is((pg_temp.est(1)->'weight_trend'->>'weeks')::int, 1, 'one weigh-in is one week of data');

select is(pg_temp.est(2)->'activity_level'->>'value', 'sedentary', 'a null profile level defaults to sedentary');
select is(pg_temp.est(2)->'basal_kcal'->>'value', null, 'no profile data gives no basal expenditure');
select is(pg_temp.est(2)->'basal_kcal'->'missing', '["profile_data"]'::jsonb, 'and says which data is missing');
select is(pg_temp.est(2)->'total_expenditure'->>'value', null, 'and no total expenditure');
select is(pg_temp.est(2)->'weight_trend'->>'slope_kg_week', null, 'and no trend, without an error');

-- ---------------------------------------------------------------------------
-- Activity level from steps, sessions and active calories
-- ---------------------------------------------------------------------------
select is(pg_temp.est(3)->'activity_level'->>'value', 'sedentary', '4 999 steps a day is sedentary');
select is(pg_temp.est(4)->'activity_level'->>'value', 'light', '5 000 steps a day is light');
select is(pg_temp.est(5)->'activity_level'->>'value', 'moderate', '7 500 steps a day is moderate');
select is(pg_temp.est(6)->'activity_level'->>'value', 'high', '10 000 steps a day is high');
select is(pg_temp.est(7)->'activity_level'->>'value', 'very_high', '12 500 steps a day is very_high');
select is(pg_temp.est(4)->'activity_level'->>'source', 'data', 'a level from steps has source data');
select is((pg_temp.est(4)->'activity_level'->>'uncertainty')::numeric, 0.1::numeric, 'and uncertainty 0.1');
select is((pg_temp.est(4)->'activity_level'->'sources'->>'steps_avg')::numeric, 5000::numeric, 'the steps average is a source');
select is(pg_temp.est(8)->'activity_level'->>'value', 'moderate', '3 sessions a week raise light by one level');
select is(pg_temp.est(9)->'activity_level'->>'value', 'light', '2 sessions a week change nothing');
select is(pg_temp.est(10)->'activity_level'->>'value', 'very_high', 'the session bump stops at very_high');
select is((pg_temp.est(8)->'activity_level'->'sources'->>'sessions_per_week')::numeric, 3::numeric,
  'sessions per week are a source');
select is(pg_temp.est(11)->'activity_level'->>'value', 'light', 'fewer than 7 days of steps count as missing: the profile level stays');
select is(pg_temp.est(11)->'activity_level'->>'source', 'profile', 'and the source is the profile');
select ok(pg_temp.est(11)->'activity_level'->'missing' ? 'steps', 'and steps are listed as missing');
select is(pg_temp.est(12)->'activity_level'->>'value', 'moderate', '400 kcal of active energy a day, without steps, is moderate');
select ok(pg_temp.est(12)->'activity_level'->'missing' ? 'steps', 'steps are listed as missing in that case');
select is(pg_temp.est(13)->'activity_level'->>'value', 'light', 'steps win over active calories');
select is((pg_temp.est(13)->'activity_level'->'sources'->>'active_kcal_avg')::numeric, 800::numeric,
  'but the active calories are still listed as a source');
select is(pg_temp.est(14)->'activity_level'->>'value', 'light', 'sessions alone never change the profile level');

-- ---------------------------------------------------------------------------
-- Weight trend
-- ---------------------------------------------------------------------------
select is((pg_temp.est(20)->'weight_trend'->>'slope_kg_week')::numeric, -0.6::numeric, 'slope of 80, 79, 79, 78 is -0.6 kg/week');
select is((pg_temp.est(20)->'weight_trend'->>'interval_low')::numeric, -1.209::numeric, 'the 95 % interval low (t = 4.303, 2 df)');
select is((pg_temp.est(20)->'weight_trend'->>'interval_high')::numeric, 0.009::numeric, 'the 95 % interval high');
select is((pg_temp.est(20)->'weight_trend'->>'low_confidence')::boolean, false, '4 weeks are not low confidence');
select is((pg_temp.est(21)->'weight_trend'->>'slope_kg_week')::numeric, -1::numeric, '3 weeks give a slope');
select is((pg_temp.est(21)->'weight_trend'->>'weeks')::int, 3, 'and count 3 weeks');
select is((pg_temp.est(21)->'weight_trend'->>'low_confidence')::boolean, true, 'below 4 weeks the trend is low confidence');
select is((pg_temp.est(22)->'weight_trend'->>'slope_kg_week')::numeric, -1::numeric, '2 weeks give a slope');
select is(pg_temp.est(22)->'weight_trend'->'interval_low', 'null'::jsonb, 'without an interval');
select is((pg_temp.est(22)->'weight_trend'->>'low_confidence')::boolean, true, 'and marked low confidence');
select is(pg_temp.est(23)->'weight_trend'->>'slope_kg_week', null, '1 week gives no slope');
select is(pg_temp.est(23)->'weight_trend'->'missing', '["weigh_ins"]'::jsonb, 'and lists the weigh-ins as missing');
select is((pg_temp.est(24)->'weight_trend'->>'slope_kg_week')::numeric, -2::numeric,
  'two weigh-ins on one day count as their average (82 -> 80)');

-- ---------------------------------------------------------------------------
-- Intake completeness and adaptive expenditure
-- ---------------------------------------------------------------------------
select is((pg_temp.est(30)->'intake'->>'logged_days')::int, 17, '17 logged days are counted');
select is((pg_temp.est(30)->'intake'->>'reliable')::boolean, false, '17 of 28 days (about 60 %) is unreliable');
select is((pg_temp.est(30)->'total_expenditure'->>'unreliable')::boolean, true, 'and marks the expenditure unreliable');
select is((pg_temp.est(30)->'total_expenditure'->>'allow_calorie_changes')::boolean, false, 'and forbids calorie changes');
select is((pg_temp.est(31)->'intake'->>'reliable')::boolean, true, '20 of 28 days (71 %) is reliable');
select is((pg_temp.est(31)->'total_expenditure'->>'allow_calorie_changes')::boolean, true, 'and allows calorie changes');
select is((pg_temp.est(32)->'intake'->>'reliable')::boolean, false, '19 of 28 days (68 %) is unreliable');
select is((pg_temp.est(33)->'intake'->>'logged_days')::int, 0, 'no logged days is 0');
select is((pg_temp.est(33)->'intake'->>'reliable')::boolean, false, 'and unreliable, without an error');
select is(pg_temp.est(34)->'total_expenditure'->>'source', 'adaptive', 'reliable intake and 4 weeks of weights give the adaptive estimate');
select is((pg_temp.est(34)->'total_expenditure'->>'value')::numeric, 2660::numeric,
  'adaptive = mean intake 2000 - (-0.6 x 7700 / 7) = 2660');
select is(pg_temp.est(35)->'total_expenditure'->>'source', 'formula', 'an unreliable diary falls back to the formula');
select is((pg_temp.est(35)->'total_expenditure'->>'value')::numeric, 2121::numeric, 'formula: 1767.5 x 1.2 = 2121');
select is(pg_temp.est(35)->'total_expenditure'->'missing', '["diary"]'::jsonb, 'and lists the diary as missing');
select is(pg_temp.est(36)->'total_expenditure'->>'source', 'formula', 'fewer than 3 weeks of weights use the formula');

-- ---------------------------------------------------------------------------
-- Experience
-- ---------------------------------------------------------------------------
select is(pg_temp.est(40)->'experience'->>'value', 'beginner', '23 sessions are beginner');
select is(pg_temp.est(41)->'experience'->>'value', 'intermediate', '24 sessions over 14 weeks are intermediate');
select is(pg_temp.est(42)->'experience'->>'value', 'beginner', '24 sessions over 11 weeks are beginner');
select is(pg_temp.est(43)->'experience'->>'value', 'intermediate', '24 sessions over 12 weeks are intermediate');
select is(pg_temp.est(44)->'experience'->>'value', 'advanced', '150 sessions over 52 weeks are advanced');
select is(pg_temp.est(45)->'experience'->>'value', 'intermediate', '150 sessions over 51 weeks are intermediate');
select is(pg_temp.est(46)->'experience'->>'value', 'beginner', 'no history is beginner');
select is(pg_temp.est(46)->'experience'->'missing', '["training_history"]'::jsonb, 'and says the history is missing');

-- ---------------------------------------------------------------------------
-- Confirmation and re-ask
-- ---------------------------------------------------------------------------
select is((pg_temp.cfm(50)->'activity_level'->>'confirmed')::boolean, true, 'confirming stores the activity level');
select is((pg_temp.est(50)->'activity_level'->>'provisional')::boolean, false, 'a confirmed value is no longer provisional');
select is((pg_temp.est(50)->'total_expenditure'->>'confirmed')::boolean, true, 'the expenditure is confirmed too');
select is((pg_temp.est(50)->'experience'->>'confirmed')::boolean, true, 'and the experience');
select is((pg_temp.est(50)->'weight_trend'->>'confirmed')::boolean, false, 'a trend without a slope is not stored as confirmed');
select is((select count(*)::int from public.coach_confirmed_values), 3, 'three values were stored for the user');

reset role;
update public.daily_activity set steps = 10000 where user_id = pg_temp.u(50);
set local role authenticated;
select is((pg_temp.est(50)->'activity_level'->>'reask')::boolean, true, 'a new estimate outside the uncertainty asks again');
select is((pg_temp.est(50)->'activity_level'->>'confirmed')::boolean, false, 'and the value is no longer confirmed');
select is(pg_temp.est(50)->'activity_level'->>'value', 'high', 'and shows the new estimate');
select is((pg_temp.est(50)->'total_expenditure'->>'reask')::boolean, true, 'the expenditure moves outside its uncertainty too');

reset role;
update public.daily_activity set steps = 7500 where user_id = pg_temp.u(50);
set local role authenticated;
select is((pg_temp.est(50)->'activity_level'->>'confirmed')::boolean, true, 'when the estimate returns inside the uncertainty it stays confirmed');

-- Edited level.
select is(pg_temp.cfm(51, 'very_high')->'activity_level'->>'value', 'very_high', 'the edited level is the activity level');
select is((pg_temp.est(51)->'activity_level'->>'edited')::boolean, true, 'and is marked edited');
select is(pg_temp.est(51)->'activity_level'->>'estimate', 'light', 'while the estimate keeps its own value');
select is((pg_temp.est(51)->'activity_level'->>'factor')::numeric, 1.9::numeric, 'the factor is the edited level''s');
select is((pg_temp.est(51)->'total_expenditure'->>'value')::numeric, 3286::numeric, 'the expenditure uses the edited level (1729.5 x 1.9)');
select is((select activity_level::text from public.profiles where id = pg_temp.u(51)), 'light',
  'editing does not change profiles.activity_level');

reset role;
select pg_temp.steps(51, 10, 7500);
set local role authenticated;
select is((pg_temp.est(51)->'activity_level'->>'confirmed')::boolean, true,
  'a one-band move inside the confirmed 0.2 uncertainty keeps the edit');
reset role;
update public.daily_activity set steps = 10000 where user_id = pg_temp.u(51);
set local role authenticated;
select is((pg_temp.est(51)->'activity_level'->>'reask')::boolean, true, 'a move outside it asks again');
select is((pg_temp.est(51)->'activity_level'->>'edited')::boolean, false, 'and drops the edit');

-- Exactly on the edge of the uncertainty stays confirmed; just outside asks again.
select pg_temp.est(52);
select lives_ok($$insert into public.coach_confirmed_values (key, estimate, uncertainty) values ('activity_level', 1.45, 0.1)$$,
  'a user can store a confirmation for themselves');
select is((pg_temp.est(52)->'activity_level'->>'reask')::boolean, false, 'a difference equal to the uncertainty stays confirmed');
update public.coach_confirmed_values set estimate = 1.449 where key = 'activity_level';
select is((pg_temp.est(52)->'activity_level'->>'reask')::boolean, true, 'a difference just above it asks again');

-- ---------------------------------------------------------------------------
-- Isolation between users
-- ---------------------------------------------------------------------------
select pg_temp.est(54);
select is((select count(*)::int from public.coach_confirmed_values), 0, 'a user does not see the confirmations of others');
select throws_ok(
  format($$insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty) values (%L, 'experience', 0, 0.5)$$,
    pg_temp.u(50)),
  '42501', null, 'a user cannot write a confirmation for another user');
update public.coach_confirmed_values set estimate = 99;
reset role;
select is((select count(*)::int from public.coach_confirmed_values where estimate = 99), 0,
  'a user cannot change the confirmations of others');
set local role authenticated;
select is((pg_temp.est(54)->'activity_level'->'sources'->>'steps_days')::int, 0, 'estimates ignore the data of other users');
select is(pg_temp.est(54)->'activity_level'->>'source', 'profile', 'and fall back to the profile');
select is((pg_temp.est(54)->'intake'->>'logged_days')::int, 0, 'the diary of others is not counted');

select * from finish();
rollback;
