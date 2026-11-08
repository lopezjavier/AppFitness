-- pgTAP: the shared blocking set of accept_coach_plan and coach_plan_inputs('plan').blocks_accept (T-245, US-68, R-59).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(18);

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

insert into auth.users (id, email) select pg_temp.u(n), 'accblock' || n || '@example.com' from generate_series(1, 3) n;
-- 1 fresh account. 2 fresh account whose expenditure comes from the stated maintenance. 3 another user.
select pg_temp.fresh(1), pg_temp.fresh(2), pg_temp.fresh(3);
insert into public.coach_user_intake (user_id, known_maintenance_kcal) values (pg_temp.u(2), 2600);

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
