-- pgTAP: coach_nutrition_diagnosis, the calorie_cause refusal and the summary key (T-247, US-113/US-114;
-- planning/04 section 6.7, 07 section 4 "Nutrition follow-up (E16)" (b), (c)).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(37);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9470000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) $$ security definer;
create function pg_temp.at_day(off int, hh text) returns timestamptz language sql as $$
  select ((pg_temp.d0() + off)::timestamp + hh::interval) at time zone 'UTC' $$;
create function pg_temp.mk(n int, rate numeric, start_off int, opts jsonb, wslope numeric, noise numeric)
returns void language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); w int; i int;
begin
  update public.profiles set onboarding_completed = true where id = pg_temp.u(n);
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, phase_options, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d0() - start_off, 'fat_loss',
          jsonb_build_object('rate_kg_week', rate, 'target_kcal', 2100, 'protein_pct', 30, 'calorie_adjustment', -200,
                             'floor_kcal', 1200, 'phase', 'cut', 'diet_break', '{"target_kcal":1900,"protein_pct":35}'::jsonb),
          opts, 'accepted', now());
  for w in 1..4 loop
    for i in 1..2 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
      values (v_plan, pg_temp.u(n), w, i, 'Sesion ' || i, '[]');
    end loop;
  end loop;
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(n), 'Filler', 'completed', pg_temp.at_day(-start_off + 7 * k + 1, '15 hours'),
         pg_temp.at_day(-start_off + 7 * k + 1, '16 hours')
  from generate_series(0, start_off / 7 - 1) k;
  -- A daily diary of 2100 kcal for the last 36 days and a daily weigh-in for the last 51 days.
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - o,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000e3', 100, 'Alimento', 2100, 100, 10, 10
  from generate_series(0, 35) o;
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), round(80 + wslope * (-o) / 7.0
           + case when extract(week from pg_temp.d0() - o)::int % 2 = 0 then noise else -noise end, 2),
         'manual', pg_temp.at_day(-o, '7 hours')
  from generate_series(0, 50) o;
  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (pg_temp.u(n), 'deficit', -300, 2100, 30, 45, 25)
  on conflict (user_id) do update set target_kcal = 2100, protein_pct = 30, carbs_pct = 45, fat_pct = 25;
  perform set_config('app.coach_write', 'off', true);
end $$;
create function pg_temp.diag_of(n int) returns jsonb language sql as $$
  select public.coach_nutrition_diagnosis(pg_temp.u(n), pg_temp.plan_of(n), pg_temp.d0() - 4, pg_temp.d0()) $$;
create function pg_temp.trend(off int) returns jsonb language sql as $$
  select public.coach_weight_trend(pg_temp.d0() + off) $$;

grant execute on all functions in schema pg_temp to authenticated, anon;

insert into auth.users (id, email)
select pg_temp.u(n), 'ndiag' || n || '@example.com' from generate_series(1, 30) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000e3', 'off', 'Alimento e3', 100);

create function pg_temp.rate_to(n int, r numeric) returns void language sql as $$
  update public.coach_plans set nutrition = nutrition || jsonb_build_object('rate_kg_week', r) where user_id = pg_temp.u(n) $$;
create function pg_temp.kcal_all(n int, k numeric) returns void language sql as $$
  update public.food_log_entries set kcal = k where user_id = pg_temp.u(n) $$;
create function pg_temp.drop_food(n int, from_off int, to_off int) returns void language sql as $$
  delete from public.food_log_entries where user_id = pg_temp.u(n)
    and log_date between pg_temp.d0() - from_off and pg_temp.d0() - to_off $$;
create function pg_temp.drop_weight(n int, from_off int, to_off int) returns void language sql as $$
  delete from public.body_measurements where user_id = pg_temp.u(n)
    and (measured_at at time zone 'UTC')::date between pg_temp.d0() - from_off and pg_temp.d0() - to_off $$;
-- Weekday and weekend kcal inside the 14-day window E-13..E (E = today - 4); other days stay at the goal.
create function pg_temp.weekend_kcal(n int, weekday numeric, weekend numeric) returns void language sql as $$
  update public.food_log_entries set kcal = case when extract(dow from log_date) in (0, 6) then weekend else weekday end
  where user_id = pg_temp.u(n) and log_date between pg_temp.d0() - 17 and pg_temp.d0() - 4 $$;
create function pg_temp.eval(n int) returns jsonb language sql as $$
  select public.evaluate_coach_calorie_change(pg_temp.d0()) $$;
create function pg_temp.kind(n int) returns text language sql as $$ select pg_temp.diag_of(n)->>'kind' $$;
create function pg_temp.nrecs(n int) returns int language sql security definer as $$
  select count(*)::int from public.coach_recommendations where user_id = pg_temp.u(n) $$;

-- A = steady loss of 0.1 kg/week (narrow interval, excludes 0); P = flat with week-to-week noise (interval contains 0).
select pg_temp.mk(1, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);   -- A, nothing explains the miss
select pg_temp.mk(2, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', 0, 0.4);    -- P, full logs: plateau
select pg_temp.mk(3, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);   -- rate inside the interval
select pg_temp.mk(4, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);   -- rate on the interval edge
select pg_temp.mk(5, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);   -- rate between the two highs
select pg_temp.mk(6, -0.5, 31, '{"cut_rate_pct":0.5,"diet_break":true}', -0.1, 0);    -- window holds the diet-break week
select pg_temp.mk(7, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);   -- 4 food days in W1
select pg_temp.mk(8, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', 0, 0.4);    -- P with exactly 5 food days in W1
select pg_temp.mk(9, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);   -- 4 food days in W0
select pg_temp.mk(10, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- 2 weigh-ins in W1
select pg_temp.mk(11, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- 4 food days and eating above the goal
select pg_temp.mk(12, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- 300 kcal above the goal
select pg_temp.mk(13, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- exactly half the gap
select pg_temp.mk(14, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- 0.1 kcal under half the gap
select pg_temp.mk(15, 0.2, 10, '{"cut_rate_pct":0.5,"diet_break":false}', 0.6, 0);    -- gain faster than planned
select pg_temp.mk(16, -0.2, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.6, 0);  -- loss faster than planned
select pg_temp.mk(17, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- slow loss but eating below the goal
select pg_temp.mk(18, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- weekend carries exactly half
select pg_temp.mk(19, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- weekend carries just under half
select pg_temp.mk(20, 0, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);     -- maintenance rate 0 on a falling trend
select pg_temp.mk(21, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', 0, 0.4);   -- P but eating above the goal
select pg_temp.mk(22, -0.5, 10, '{"cut_rate_pct":0.5,"diet_break":false}', -0.1, 0);  -- weigh-ins only in the last 12 days

-- Dynamic rates: they depend on the weekday the tests run, so they are read from the user's own trend.
select pg_temp.as_user(3);
select pg_temp.rate_to(3, (pg_temp.trend(-4)->>'slope')::numeric);
select pg_temp.as_user(4);
select pg_temp.rate_to(4, (pg_temp.trend(-4)->>'high')::numeric);
select pg_temp.as_user(5);
select pg_temp.rate_to(5, round(((pg_temp.trend(-4)->>'high')::numeric + (pg_temp.trend(-11)->>'high')::numeric) / 2, 4));
select pg_temp.as_user(13);
select pg_temp.rate_to(13, (pg_temp.trend(-4)->>'slope')::numeric - 0.4);
select pg_temp.as_user(14);
select pg_temp.rate_to(14, (pg_temp.trend(-4)->>'slope')::numeric - 0.4);

select pg_temp.drop_food(7, 6, 4);
select pg_temp.drop_food(8, 5, 4);
select pg_temp.drop_food(9, 13, 11);
select pg_temp.drop_weight(10, 8, 4);
select pg_temp.drop_food(11, 6, 4);
select pg_temp.kcal_all(11, 2400);
select pg_temp.kcal_all(12, 2400);
select pg_temp.kcal_all(13, 2320);
select pg_temp.kcal_all(14, 2319.9);
select pg_temp.kcal_all(15, 2400);
select pg_temp.kcal_all(16, 1800);
select pg_temp.kcal_all(17, 1800);
select pg_temp.weekend_kcal(18, 2300, 2600);
select pg_temp.weekend_kcal(19, 2300, 2590);
select pg_temp.kcal_all(21, 2400);
select pg_temp.drop_weight(22, 50, 13);

grant execute on all functions in schema pg_temp to authenticated, anon;
set local role authenticated;

-- Trigger
select pg_temp.as_user(1);
select is(pg_temp.diag_of(1), '{"kind":"none"}'::jsonb, 'rate far outside the interval, logs complete, nothing explains it: no clear cause');
select pg_temp.as_user(3);
select ok(pg_temp.diag_of(3) is null, 'rate inside the interval: no card');
select pg_temp.as_user(4);
select ok(pg_temp.diag_of(4) is null, 'rate exactly on the interval edge counts as inside: no card');
select pg_temp.as_user(5);
select ok(pg_temp.diag_of(5) is null, 'rate outside in one summary only: no card');
select pg_temp.as_user(22);
select ok(pg_temp.diag_of(22) is null, 'no interval one week earlier (too few weigh-ins): no card');
select pg_temp.as_user(6);
select ok(pg_temp.diag_of(6) is null, 'a window with the diet-break week never triggers');
select pg_temp.as_user(20);
select is(pg_temp.kind(20), 'none', 'rate 0 on a falling trend: no clear cause, never a plateau');

-- Check 1: logging
select pg_temp.as_user(7);
select is(pg_temp.diag_of(7), jsonb_build_object('kind', 'logging_food', 'n', 4, 'week_start', pg_temp.d0() - 10),
  '4 complete food days in the latest week: logging_food with that week start');
select pg_temp.as_user(9);
select is(pg_temp.diag_of(9), jsonb_build_object('kind', 'logging_food', 'n', 4, 'week_start', pg_temp.d0() - 17),
  '4 complete food days in the earlier week: logging_food names that week');
select pg_temp.as_user(10);
select is(pg_temp.diag_of(10), jsonb_build_object('kind', 'logging_weigh_in', 'n', 2, 'week_start', pg_temp.d0() - 10),
  'food days fine but 2 weigh-ins in the latest week: logging_weigh_in');
select pg_temp.as_user(11);
select is(pg_temp.kind(11), 'logging_food', 'logging beats calories');
select pg_temp.as_user(8);
select is(pg_temp.kind(8), 'none', 'exactly 5 complete days passes the logging gate but is not a plateau');

-- Check 2: calories
select pg_temp.as_user(12);
select is(pg_temp.diag_of(12),
  '{"kind":"calories","avg_kcal":2400,"goal_kcal":2100,"diff_kcal":300,"direction":"above","weekend":false}'::jsonb,
  'slow loss and 300 kcal above the goal: calories, above, not a weekend pattern');
select pg_temp.as_user(13);
select is(pg_temp.kind(13), 'calories', 'a gap of exactly half the daily energy of the miss (220 kcal) fires');
select pg_temp.as_user(14);
select is(pg_temp.kind(14), 'none', '0.1 kcal under half the gap does not fire');
select pg_temp.as_user(15);
select is(pg_temp.diag_of(15)->>'direction', 'above', 'gaining faster than planned and eating above the goal: above');
select pg_temp.as_user(16);
select is(pg_temp.diag_of(16)->>'direction', 'below', 'losing faster than planned and eating below the goal: below');
select pg_temp.as_user(17);
select is(pg_temp.kind(17), 'none', 'slow loss but eating below the goal: the wrong direction does not explain the miss');
select pg_temp.as_user(18);
select is(pg_temp.diag_of(18)->>'kind' || ':' || (pg_temp.diag_of(18)->>'weekend'), 'calories:true',
  'Saturday and Sunday carry exactly half of the excess: weekend variant');
select pg_temp.as_user(19);
select is(pg_temp.diag_of(19)->>'kind' || ':' || (pg_temp.diag_of(19)->>'weekend'), 'calories:false',
  'just under half of the excess on the weekend: no weekend variant');

-- Check 3: plateau
select pg_temp.as_user(2);
select is(pg_temp.kind(2), 'plateau', 'flat trend, interval holds 0 and excludes the rate, 7 complete days: plateau');
select ok((pg_temp.diag_of(2)->>'low')::numeric <= 0 and (pg_temp.diag_of(2)->>'high')::numeric >= 0
      and pg_temp.diag_of(2) ? 'slope', 'plateau carries slope, low and high around 0');
select pg_temp.as_user(21);
select is(pg_temp.kind(21), 'calories', 'calories beat the plateau');

-- Isolation and privileges
select pg_temp.as_user(1);
select ok(public.coach_nutrition_diagnosis(pg_temp.u(12), pg_temp.plan_of(12), pg_temp.d0() - 4, pg_temp.d0()) is null,
  'user 1 cannot read the diagnosis of user 12');
select is(pg_temp.diag_of(1), '{"kind":"none"}'::jsonb, 'user 12 data does not change user 1 diagnosis');
select ok(has_function_privilege('authenticated', 'public.coach_nutrition_diagnosis(uuid, uuid, date, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_nutrition_diagnosis(uuid, uuid, date, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_daily_goals(uuid, uuid, date, date, date)', 'execute'),
  'both new functions are for authenticated only');

-- Summary key
select pg_temp.as_user(12);
select is(public.coach_week_summary(pg_temp.d0())->'nutrition'->'diagnosis'->>'kind', 'calories', 'the summary carries nutrition.diagnosis');
select is((public.coach_week_summary(pg_temp.d0())->'nutrition'->'diagnosis'->>'diff_kcal')::int, 300, 'the summary diagnosis carries its figures');
select pg_temp.as_user(3);
select ok(not ((public.coach_week_summary(pg_temp.d0())->'nutrition') ? 'diagnosis'), 'no card: the diagnosis key is absent');
select pg_temp.as_user(1);
select is(public.coach_week_summary(pg_temp.d0())->'nutrition'->'diagnosis'->>'kind', 'none', 'no clear cause is a card of kind none');

-- evaluate_coach_calorie_change
select pg_temp.as_user(12);
select is(pg_temp.eval(12), '{"status":"none","reason":"calorie_cause"}'::jsonb, 'calorie cause: no proposal');
select is(pg_temp.nrecs(12), 0, 'calorie cause writes nothing, the 14-day clock is not restarted');
select pg_temp.as_user(21);
select is(pg_temp.eval(21)->>'reason', 'calorie_cause', 'calories beat the plateau in the proposal too');
select pg_temp.as_user(2);
select is(pg_temp.eval(2)->>'status', 'created', 'plateau: the proposal is still made');
select is(pg_temp.nrecs(2), 1, 'plateau: the proposal row exists');
select pg_temp.as_user(1);
select is(pg_temp.eval(1)->>'status', 'created', 'no clear cause: the proposal is still made');

reset role;
set local role anon;
select throws_ok($$ select public.coach_nutrition_diagnosis(null, null, current_date) $$, '42501', null,
  'anon cannot execute the diagnosis');

select * from finish();
rollback;

