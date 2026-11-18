-- pgTAP: the shared blocking set of accept_coach_plan and coach_plan_inputs('plan').blocks_accept (T-245, US-68, R-59).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(31);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- The user n as a uuid: c9500000-0000-4000-8000-0000000000NN.
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9500000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

-- Fresh account: profile and one weigh-in today, no diary, no weight trend, nothing confirmed.
create function pg_temp.fresh(n int) returns void language sql as $$
  update public.profiles
  set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178,
      activity_level = 'moderate', onboarding_completed = true
  where id = pg_temp.u(n);
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  values (pg_temp.u(n), 80, 'manual', now()) $$;

create function pg_temp.nut() returns jsonb language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', 2600, 'expenditure_source', 'formula',
    'rate_pct_week', 0.5, 'rate_kg_week', -0.4, 'target_rate_pct', 0.5, 'limited_by', null, 'floor_kcal', 1500,
    'target_kcal', 2200, 'calorie_adjustment', -400, 'strategy', 'deficit',
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', false, 'calorie_changes', 0,
    'phase', 'cut', 'phase_reason', 'intention',
    'phase_options', jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', false)) $$;

-- "blocks_accept" of one plan-kind entry as text.
create function pg_temp.ba(k text) returns text language sql as $$
  select x->>'blocks_accept' from jsonb_array_elements(public.coach_plan_inputs('plan', pg_temp.d0())->'inputs') x
  where x->>'key' = k $$;

create function pg_temp.blockers() returns text[] language sql as $$
  select coalesce(array_agg(x->>'key' order by x->>'key'), '{}')
  from jsonb_array_elements(public.coach_plan_inputs('plan', pg_temp.d0())->'inputs') x
  where (x->>'blocks_accept')::boolean $$;

grant execute on all functions in schema pg_temp to authenticated;

-- One weigh-in on the day_offset-th day after the Monday of the week that started weeks_ago weeks ago.
create function pg_temp.wd(n int, weeks_ago int, day_offset int, kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg, 'manual',
         ((pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) - 7 * weeks_ago + day_offset)::timestamp
           + interval '30 minutes') at time zone 'UTC' $$;

create function pg_temp.diary(n int, days int, kcal numeric) returns void language sql as $$
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - i,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000c1', 100, 'Alimento', kcal, 10, 10, 10
  from generate_series(0, days - 1) i $$;

insert into auth.users (id, email) select pg_temp.u(n), 'accblock' || n || '@example.com' from generate_series(1, 7) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c1', 'off', 'Alimento c', 100);
-- 1 fresh account. 2 fresh account whose expenditure comes from the stated maintenance. 3 another user.
select pg_temp.fresh(1), pg_temp.fresh(2), pg_temp.fresh(3);
insert into public.coach_user_intake (user_id, known_maintenance_kcal) values (pg_temp.u(2), 2600);
-- T-308 (provisional activity level, experience already backed by a 0.2 year report). 4: stated maintenance.
-- 5: reliable diary and weight slope (adaptive, slope used). 6: formula. 7: reliable diary without enough weights.
select pg_temp.fresh(4), pg_temp.fresh(5), pg_temp.fresh(6), pg_temp.fresh(7);
insert into public.coach_user_intake (user_id, training_years, known_maintenance_kcal)
values (pg_temp.u(4), 0.2, 2600), (pg_temp.u(5), 0.2, null), (pg_temp.u(6), 0.2, null), (pg_temp.u(7), 0.2, null);
select pg_temp.diary(5, 20, 2000), pg_temp.diary(7, 20, 2000);
select pg_temp.wd(5, w, o, k)
from (values (3, 0, 80), (2, 0, 79), (1, 0, 79), (1, 1, 79), (1, 2, 79), (2, 1, 79), (2, 2, 79), (3, 1, 80), (3, 2, 80)) t(w, o, k);

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('authenticated', 'public.coach_plan_unconfirmed(date)', 'execute'),
  'authenticated cannot call coach_plan_unconfirmed');
select ok(not has_function_privilege('anon', 'public.coach_plan_unconfirmed(date)', 'execute'),
  'anon cannot call coach_plan_unconfirmed');

-- ---------------------------------------------------------------------------
-- Fresh account (the T-245 reproduction): the estimates block until confirmed; the diary and a trend without a slope never do
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');

select ok(pg_temp.blockers() @> array['activity_level'], 'a fresh account: the unconfirmed activity level blocks accept');
select is(pg_temp.ba('activity_level'), 'true', 'the activity level entry has blocks_accept');
select is(pg_temp.ba('diary'), 'false', 'the unreliable diary never blocks accept');
select is(pg_temp.ba('weight_trend'), 'false', 'a weight trend without a slope never blocks accept');
select is(pg_temp.ba('weight'), 'false', 'an ok input does not block accept');
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'a fresh account cannot accept before confirming');
select ok(exists (
    select 1 from jsonb_array_elements(public.coach_plan_inputs('plan', pg_temp.d0())->'inputs') x
    where x->>'key' = 'experience'),
  'the plan list names the experience the server checks');

select public.confirm_derived_values(pg_temp.d0());

select is(pg_temp.blockers(), array[]::text[], 'after confirming, nothing blocks accept');
select is(pg_temp.ba('diary'), 'false', 'the diary is still provisional but not blocking');
select is(public.coach_plan_inputs('plan', pg_temp.d0())->'inputs' @> '[{"key":"diary","status":"provisional"}]', true,
  'the diary keeps its provisional status');
select lives_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  'a fresh account accepts once the estimates are confirmed, without a new plan');

-- ---------------------------------------------------------------------------
-- Q1: an expenditure from the stated maintenance does not block
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');
select public.confirm_derived_values(pg_temp.d0());
select is(pg_temp.ba('total_expenditure'), 'false', 'an intake-sourced expenditure has blocks_accept false');
select is(public.coach_plan_inputs('plan', pg_temp.d0())->'inputs' @> '[{"key":"total_expenditure","status":"provisional"}]',
  true, 'an intake-sourced expenditure stays provisional in the list');
select lives_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  'an intake-sourced expenditure does not block accept');

-- ---------------------------------------------------------------------------
-- T-308: a provisional activity level blocks only on the formula path
-- ---------------------------------------------------------------------------
select pg_temp.as_user(4);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');
select is(pg_temp.blockers(), array[]::text[], 'a provisional level and a stated maintenance: nothing blocks');
select is(public.coach_plan_inputs('plan', pg_temp.d0())->'inputs' @> '[{"key":"activity_level","status":"provisional"}]', true,
  'the level keeps its provisional status');
select is(pg_temp.ba('activity_level'), 'false', 'but does not block accept');
select lives_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  'a provisional level does not block when the expenditure is the stated maintenance');

-- Adaptive with the weight slope: the weight trend is the only thing left to confirm.
select pg_temp.as_user(5);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');
insert into public.coach_confirmed_values (key, estimate, uncertainty)
select 'weight_trend', (e->'weight_trend'->>'slope_kg_week')::numeric, 0.5
from (select public.coach_compute(pg_temp.d0(), null) as e) x;
select is(pg_temp.ba('total_expenditure'), 'false', 'an adaptive expenditure backed by the slope does not block');
select is(pg_temp.ba('activity_level'), 'false', 'a provisional level does not block on the adaptive path');
select is(pg_temp.blockers(), array[]::text[], 'nothing blocks on the adaptive path');
select lives_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  'a provisional level does not block when the expenditure is adaptive');

-- Formula path: the level changes a figure, so it blocks.
select pg_temp.as_user(6);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');
select ok(pg_temp.blockers() @> array['activity_level'], 'a provisional level blocks on the formula path');
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'and accept is refused');

-- Adaptive from the diary alone: the expenditure itself is still provisional, the level is not what blocks.
select pg_temp.as_user(7);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');
select is(pg_temp.ba('total_expenditure'), 'true', 'an adaptive expenditure without the slope still blocks');
select is(pg_temp.ba('activity_level'), 'false', 'while the level does not');
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'and accept is refused');

-- ---------------------------------------------------------------------------
-- Isolation: user B confirming does not unblock user A
-- ---------------------------------------------------------------------------
select pg_temp.as_user(3);
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(), '[]', '{}', '{}');
select pg_temp.as_user(1);
reset role;
delete from public.coach_confirmed_values where user_id = pg_temp.u(1);
set local role authenticated;
select pg_temp.as_user(3);
select public.confirm_derived_values(pg_temp.d0());
select pg_temp.as_user(1);
select ok(pg_temp.blockers() @> array['activity_level'], 'another user''s confirmation does not unblock user A');
select throws_ok(
  $$select public.accept_coach_plan(gen_random_uuid(), pg_temp.d0())$$,
  'P0002', null, 'accepting an unknown plan is not found');

select * from finish();
rollback;
