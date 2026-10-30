-- pgTAP: coach_plans, coach_recommendations and their RPCs (T-161, US-69/US-74; rules in planning/04 section 8).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(86);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- The user n as a uuid: c9000000-0000-4000-8000-0000000000NN.
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

-- Male, 26 years old, 178 cm, onboarding done (creates the meal slots).
create function pg_temp.profile(n int) returns void language sql as $$
  update public.profiles
  set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178,
      activity_level = 'moderate', onboarding_completed = true
  where id = pg_temp.u(n) $$;

-- One weigh-in on the Monday of the week that started weeks_ago weeks before the current one.
create function pg_temp.weigh(n int, weeks_ago int, kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg, 'manual',
         ((pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) - 7 * weeks_ago)::timestamp + interval '30 minutes')
           at time zone 'UTC' $$;

-- Weekly weigh-ins, oldest first, the last one in the current week.
create function pg_temp.weights(n int, vals numeric[]) returns void language plpgsql as $$
begin
  for i in 1..array_length(vals, 1) loop
    perform pg_temp.weigh(n, array_length(vals, 1) - i, vals[i]);
  end loop;
end $$;

create function pg_temp.diary(n int, days int) returns void language sql as $$
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - i,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000c9', 100, 'Alimento', 2000, 10, 10, 10
  from generate_series(0, days - 1) i $$;

-- Persisted nutrition JSON (data_version 1); rate is the signed expected kg per week (loss negative).
create function pg_temp.nut(target int, adj int, rate numeric, floor int, edited boolean default false) returns jsonb
language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', target - adj, 'expenditure_source', 'formula',
    'rate_pct_week', 0.5, 'rate_kg_week', rate, 'target_rate_pct', 0.5, 'limited_by', null, 'floor_kcal', floor,
    'target_kcal', target, 'calorie_adjustment', adj,
    'strategy', case when adj < 0 then 'deficit' when adj > 0 then 'surplus' else 'maintenance' end,
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', edited, 'calorie_changes', 0) $$;

-- A user with an accepted plan, a goal equal to it, flat weight for 8 weeks and diary_days of food.
create function pg_temp.mk(n int, diary_days int, rate numeric, target int default 2200, flr int default 1500,
                           edited boolean default false, adj int default -400, weeks int default 8)
returns void language plpgsql as $$
begin
  perform pg_temp.profile(n);
  perform pg_temp.weights(n, array_fill(80::numeric, array[weeks]));
  perform pg_temp.diary(n, diary_days);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (pg_temp.u(n), (case when adj < 0 then 'deficit' else 'surplus' end)::public.goal_strategy, adj, target, 33, 42, 25);
  insert into public.coach_plans (user_id, block_start, intention, nutrition, status, accepted_at)
  values (pg_temp.u(n), pg_temp.d0(), 'fat_loss', pg_temp.nut(target, adj, rate, flr, edited), 'accepted', now());
end $$;

create function pg_temp.edited_flag(n int) returns boolean language sql as $$
  select (nutrition->>'edited_by_user')::boolean from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'plans' || n || '@example.com' from generate_series(1, 13) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c9', 'off', 'Alimento c9', 100);

-- 1 (A): linear weight loss (a trend with a slope), an old accepted plan.
select pg_temp.profile(1);
select pg_temp.weights(1, array[80, 79.5, 79, 78.5, 78, 77.5, 77, 76.5]);
insert into public.coach_plans (user_id, block_start, intention, nutrition, status, accepted_at)
values (pg_temp.u(1), pg_temp.d0() - 28, 'fat_loss', pg_temp.nut(2300, -300, -0.4, 1500), 'accepted', now());
-- 2 (B): another user with an accepted plan and a plan recommendation.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, accepted_at)
values ('b0000000-0000-4000-8000-000000000001', pg_temp.u(2), pg_temp.d0(), 'muscle', pg_temp.nut(2900, 300, 0.3, 1500),
        'accepted', now());
insert into public.coach_recommendations (id, user_id, plan_id, kind, payload, input_summary, status, decided_at)
values ('b0000000-0000-4000-8000-0000000000a1', pg_temp.u(2), 'b0000000-0000-4000-8000-000000000001', 'plan', '{}', '{}',
        'accepted', now());
insert into public.coach_recommendations (id, user_id, plan_id, kind, payload, input_summary)
values ('b0000000-0000-4000-8000-0000000000a2', pg_temp.u(2), 'b0000000-0000-4000-8000-000000000001', 'calorie_change',
        '{"from_kcal":2900,"to_kcal":2800}', '{}');
-- 3 (C): profile and a single weigh-in (expenditure known, trend without slope).
select pg_temp.profile(3);
select pg_temp.weights(3, array[80]);
-- 4 (D): main evaluation user (20 diary days, plan rate -0.4 while the weight is flat).
select pg_temp.mk(4, 20, -0.4);
-- 5 (E): 17 of 28 diary days. 6 (F): only 3 weeks of weights. 7 (G): rate on the interval edge. 8 (H): edited plan.
select pg_temp.mk(5, 17, -0.4);
select pg_temp.mk(6, 20, -0.4, weeks => 3);
select pg_temp.mk(7, 20, 0);
select pg_temp.mk(8, 20, -0.4, edited => true);
-- 9 (I): the change would go under the floor. 10 (J): out of the adjustment range. 11 (K): gain direction.
select pg_temp.mk(9, 20, -0.4, target => 1520, flr => 1500);
select pg_temp.mk(10, 20, -0.4, adj => -950, flr => 1000);
select pg_temp.mk(11, 20, 0.4, adj => 300);
-- 12 (L): edited and discarded decisions. 13 (M): no accepted plan.
select pg_temp.mk(12, 20, -0.4);
select pg_temp.profile(13);

-- ---------------------------------------------------------------------------
-- Structure and constraints
-- ---------------------------------------------------------------------------
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_plans'::regclass),
  'RLS is enabled and forced on coach_plans');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_recommendations'::regclass),
  'RLS is enabled and forced on coach_recommendations');

select throws_ok(
  $$insert into public.coach_plans (user_id, block_start, intention, nutrition, status)
    values (pg_temp.u(13), current_date, 'muscle', '{}', 'bogus')$$,
  '23514', null, 'a plan status outside the set is rejected');
select throws_ok(
  $$insert into public.coach_plans (user_id, block_start, intention, nutrition, block_weeks)
    values (pg_temp.u(13), current_date, 'muscle', '{}', 13)$$,
  '23514', null, 'block_weeks above 12 is rejected');
select throws_ok(
  $$insert into public.coach_plans (user_id, block_start, intention, nutrition)
    values (pg_temp.u(13), current_date, 'muscle', '[]')$$,
  '23514', null, 'nutrition must be a JSON object');
select throws_ok(
  $$insert into public.coach_plans (user_id, block_start, intention, nutrition, status)
    values (pg_temp.u(2), current_date, 'muscle', '{}', 'accepted')$$,
  '23505', null, 'a user cannot have two accepted plans');
insert into public.coach_plans (user_id, block_start, intention, nutrition)
values (pg_temp.u(13), current_date, 'muscle', '{}');
select throws_ok(
  $$insert into public.coach_plans (user_id, block_start, intention, nutrition)
    values (pg_temp.u(13), current_date, 'muscle', '{}')$$,
  '23505', null, 'a user cannot have two proposed plans');
delete from public.coach_plans where user_id = pg_temp.u(13);

select throws_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary)
    values (pg_temp.u(13), 'other', '{}', '{}')$$,
  '23514', null, 'a recommendation kind outside the set is rejected');
select throws_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary, decided_at)
    values (pg_temp.u(13), 'plan', '{}', '{}', now())$$,
  '23514', null, 'a pending recommendation cannot have decided_at');
select throws_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary, status)
    values (pg_temp.u(13), 'plan', '{}', '{}', 'accepted')$$,
  '23514', null, 'a decided recommendation needs decided_at');

-- ---------------------------------------------------------------------------
-- Isolation and direct writes (authenticated user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);

select is((select count(*)::int from public.coach_plans), 1, 'user A sees only their own plan');
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(2)), 0,
  'user A cannot read user B''s plans');
select is((select count(*)::int from public.coach_recommendations where user_id = pg_temp.u(2)), 0,
  'user A cannot read user B''s recommendations');
select throws_ok(
  $$insert into public.coach_plans (block_start, intention, nutrition) values (current_date, 'muscle', '{}')$$,
  '42501', null, 'an authenticated user cannot insert a plan directly');
select throws_ok(
  $$update public.coach_plans set status = 'discarded'$$,
  '42501', null, 'an authenticated user cannot update a plan directly');
select throws_ok(
  $$delete from public.coach_plans$$,
  '42501', null, 'an authenticated user cannot delete a plan directly');
select throws_ok(
  $$insert into public.coach_recommendations (kind, payload, input_summary) values ('plan', '{}', '{}')$$,
  '42501', null, 'an authenticated user cannot insert a recommendation directly');
select throws_ok(
  $$update public.coach_recommendations set status = 'discarded'$$,
  '42501', null, 'an authenticated user cannot update a recommendation directly');

select throws_ok(
  $$select public.accept_coach_plan('b0000000-0000-4000-8000-000000000001', pg_temp.d0())$$,
  'P0002', null, 'accepting another user''s plan is not found');
select throws_ok(
  $$select public.discard_coach_plan('b0000000-0000-4000-8000-000000000001')$$,
  'P0002', null, 'discarding another user''s plan is not found');
select throws_ok(
  $$select public.decide_recommendation('b0000000-0000-4000-8000-0000000000a2', 'accepted')$$,
  'P0002', null, 'deciding another user''s recommendation is not found');

-- ---------------------------------------------------------------------------
-- Unauthenticated callers and privileges
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{}', true);
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'muscle', '{}', '[]', '{}', '{}')$$,
  '28000', null, 'propose_coach_plan rejects an unauthenticated caller');
select throws_ok(
  $$select public.accept_coach_plan(gen_random_uuid(), pg_temp.d0())$$,
  '28000', null, 'accept_coach_plan rejects an unauthenticated caller');
select throws_ok(
  $$select public.discard_coach_plan(gen_random_uuid())$$,
  '28000', null, 'discard_coach_plan rejects an unauthenticated caller');
select throws_ok(
  $$select public.evaluate_coach_calorie_change(pg_temp.d0())$$,
  '28000', null, 'evaluate_coach_calorie_change rejects an unauthenticated caller');
select throws_ok(
  $$select public.decide_recommendation(gen_random_uuid(), 'discarded')$$,
  '28000', null, 'decide_recommendation rejects an unauthenticated caller');
reset role;

select ok(not has_function_privilege('anon', 'public.propose_coach_plan(date,public.training_intention,jsonb,jsonb,jsonb,jsonb)', 'execute'),
  'anon cannot execute propose_coach_plan');
select ok(not has_function_privilege('anon', 'public.decide_recommendation(uuid,text,jsonb,date)', 'execute'),
  'anon cannot execute decide_recommendation');
select ok(has_function_privilege('authenticated', 'public.accept_coach_plan(uuid,date)', 'execute'),
  'authenticated can execute accept_coach_plan');

-- ---------------------------------------------------------------------------
-- propose_coach_plan
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);

select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', '[]', '[]', '{}', '{}')$$,
  '22023', null, 'propose_coach_plan rejects a nutrition that is not an object');

select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2200, -400, -0.4, 1500), '[]', '{"model":"x"}', '{"k":1}');
select is((select count(*)::int from public.coach_plans where status = 'proposed'), 1, 'propose stores one proposed plan');
select is((select count(*)::int from public.coach_recommendations where kind = 'plan' and status = 'pending'), 1,
  'propose stores one pending plan recommendation');

select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2200, -400, -0.4, 1500), '[]', '{"model":"y"}', '{"k":2}');
select is((select count(*)::int from public.coach_plans where status = 'proposed'), 1,
  'a second proposal leaves a single proposed plan');
select is((select count(*)::int from public.coach_plans where status = 'superseded'), 1,
  'the older proposal is superseded');
select is((select count(*)::int from public.coach_recommendations where kind = 'plan' and status = 'discarded'), 1,
  'the older proposal''s recommendation is discarded');
select is((select count(*)::int from public.coach_recommendations where kind = 'plan' and status = 'pending'), 1,
  'only the new recommendation is pending');

-- ---------------------------------------------------------------------------
-- accept_coach_plan: the confirmation gate
-- ---------------------------------------------------------------------------
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'accept is blocked while nothing is confirmed');

select public.confirm_derived_values(pg_temp.d0());

reset role;
delete from public.coach_confirmed_values where user_id = pg_temp.u(1) and key = 'activity_level';
set local role authenticated;
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'accept is blocked by an unconfirmed activity level');
select public.confirm_derived_values(pg_temp.d0());

reset role;
delete from public.coach_confirmed_values where user_id = pg_temp.u(1) and key = 'total_expenditure';
set local role authenticated;
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'accept is blocked by an unconfirmed expenditure');
select public.confirm_derived_values(pg_temp.d0());

reset role;
delete from public.coach_confirmed_values where user_id = pg_temp.u(1) and key = 'experience';
set local role authenticated;
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'accept is blocked by an unconfirmed experience');
select public.confirm_derived_values(pg_temp.d0());

reset role;
delete from public.coach_confirmed_values where user_id = pg_temp.u(1) and key = 'weight_trend';
set local role authenticated;
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())$$,
  '55000', 'derived values unconfirmed', 'accept is blocked by an unconfirmed weight trend with a slope');
select public.confirm_derived_values(pg_temp.d0());

-- ---------------------------------------------------------------------------
-- accept_coach_plan: success
-- ---------------------------------------------------------------------------
select is(
  public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())->>'status',
  'accepted', 'accept returns the accepted plan');
select is((select count(*)::int from public.coach_plans where status = 'accepted'), 1, 'one accepted plan remains');
select ok((select accepted_at is not null from public.coach_plans where status = 'accepted'), 'accept stores accepted_at');
select is((select count(*)::int from public.coach_plans where status = 'superseded'), 2,
  'the previous accepted plan is superseded (plus the earlier superseded proposal)');
select is(
  (select (strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)::text
   from public.nutrition_goals),
  '(deficit,-400,2200,33,42,25)', 'accept writes the nutrition goal equal to the plan');
select is(
  (select (status, decided_at is not null, input_summary->>'k')::text
   from public.coach_recommendations where kind = 'plan' and status <> 'discarded'),
  '(accepted,t,2)', 'accept decides the recommendation and keeps its input summary');
select is(pg_temp.edited_flag(1), false, 'accepting a plan does not count as a manual edit');
select throws_ok(
  $$select public.accept_coach_plan((select id from public.coach_plans where status = 'accepted'), pg_temp.d0())$$,
  '55000', null, 'accepting a plan that is not proposed is a wrong state');

-- Manual edits of the goal (Preferencias) mark the plan as edited; an unchanged save does not.
update public.nutrition_goals set target_kcal = 2200, fat_pct = 25;
select is(pg_temp.edited_flag(1), false, 'saving the same goal values does not mark the plan as edited');
update public.nutrition_goals set target_kcal = 2100, calorie_adjustment = -500;
select is(pg_temp.edited_flag(1), true, 'a manual change of the goal marks the plan as edited');

-- ---------------------------------------------------------------------------
-- discard_coach_plan
-- ---------------------------------------------------------------------------
select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2200, -400, -0.4, 1500), '[]', '{}', '{}');
select public.discard_coach_plan((select id from public.coach_plans where status = 'proposed'));
select is((select count(*)::int from public.coach_plans where status = 'discarded'), 1, 'discard marks the plan discarded');
select is((select count(*)::int from public.coach_recommendations where kind = 'plan' and status = 'discarded'
           and decided_at is not null), 2, 'discard marks the plan recommendation discarded with decided_at');
select throws_ok(
  $$select public.discard_coach_plan((select id from public.coach_plans where status = 'discarded'))$$,
  '55000', null, 'discarding a plan that is not proposed is a wrong state');
select is((select count(*)::int from public.nutrition_goals where target_kcal = 2100), 1,
  'discarding leaves the nutrition goal untouched');

-- User C: only a slope-less trend is unconfirmed, so the gate lets the plan through.
select pg_temp.as_user(3);
select public.propose_coach_plan(pg_temp.d0(), 'maintenance', pg_temp.nut(2500, 0, 0, 1500), '[]', '{}', '{}');
select public.confirm_derived_values(pg_temp.d0());
select is(
  public.accept_coach_plan((select id from public.coach_plans where status = 'proposed'), pg_temp.d0())->>'status',
  'accepted', 'a trend without slope does not block the acceptance');

-- ---------------------------------------------------------------------------
-- evaluate_coach_calorie_change
-- ---------------------------------------------------------------------------
select pg_temp.as_user(13);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'edited',
  'no accepted plan means no calorie change');

select pg_temp.as_user(6);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'none',
  'one week with an interval is not enough (the previous week has none)');
select pg_temp.as_user(7);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'inside_interval',
  'a rate on the edge of the interval counts as inside');
select pg_temp.as_user(5);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'unreliable_intake',
  '17 of 28 logged days is not enough');
select pg_temp.as_user(8);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'edited',
  'an edited plan gets no calorie change');
select pg_temp.as_user(9);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'floor',
  'a change under the calorie floor is not proposed');
select pg_temp.as_user(10);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'range',
  'a change outside the adjustment range is not proposed');

select pg_temp.as_user(11);
select public.evaluate_coach_calorie_change(pg_temp.d0());
select is(
  (select (payload->>'change_kcal', payload->>'to_kcal')::text from public.coach_recommendations),
  '(220,2420)', 'a rate above the interval adds calories (10 percent cap)');

select pg_temp.as_user(4);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'created',
  '20 of 28 logged days and two weeks outside the interval create a recommendation');
select is(
  (select (kind, status, reason, plan_id is not null, input_summary ? 'generated_for')::text
   from public.coach_recommendations where kind = 'calorie_change'),
  '(calorie_change,pending,rate_outside_interval,t,t)', 'the recommendation is pending and keeps its input summary');
select is(
  (select (payload->>'from_kcal', payload->>'to_kcal', payload->>'change_kcal', payload->>'change_pct')::text
   from public.coach_recommendations where kind = 'calorie_change'),
  '(2200,1980,-220,-10.0)', 'the change is capped at 10 percent and rounded to 10 kcal');
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'recent_change',
  'a pending calorie change blocks a new one (idempotent)');
select is((select count(*)::int from public.coach_recommendations where kind = 'calorie_change'), 1,
  'evaluating twice creates a single recommendation');

-- ---------------------------------------------------------------------------
-- decide_recommendation
-- ---------------------------------------------------------------------------
select throws_ok(
  $$select public.decide_recommendation((select id from public.coach_recommendations where kind = 'calorie_change'), 'maybe')$$,
  '22023', null, 'an unknown decision is rejected');

select public.decide_recommendation((select id from public.coach_recommendations where kind = 'calorie_change'), 'accepted');
select is(
  (select (target_kcal, calorie_adjustment, strategy, protein_pct, carbs_pct, fat_pct)::text from public.nutrition_goals),
  '(1980,-620,deficit,36,39,25)', 'accepting writes the new target and keeps the protein grams and the fat share');
select is(
  (select (nutrition->>'target_kcal', nutrition->>'protein_g', nutrition->>'protein_pct', nutrition->>'calorie_changes')::text
   from public.coach_plans where status = 'accepted'),
  '(1980,176,36,1)', 'the plan JSON follows the applied change');
select is(pg_temp.edited_flag(4), false, 'an applied calorie change does not count as a manual edit');
select is(
  (select (status, decided_at is not null)::text from public.coach_recommendations where kind = 'calorie_change'),
  '(accepted,t)', 'the recommendation is stored as accepted');
select throws_ok(
  $$select public.decide_recommendation((select id from public.coach_recommendations where kind = 'calorie_change'), 'discarded')$$,
  '55000', null, 'a decided recommendation cannot be decided again');
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'recent_change',
  'a new change waits 14 days after a decision');

reset role;
update public.coach_recommendations set decided_at = now() - interval '15 days' where user_id = pg_temp.u(4);
set local role authenticated;
select pg_temp.as_user(4);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'created',
  'after 14 days a new change can be proposed');
select is(
  (select (payload->>'from_kcal', payload->>'to_kcal')::text from public.coach_recommendations where status = 'pending'),
  '(1980,1790)', 'the next change starts from the applied target (10 percent of 1980 is rounded down to 190)');
select throws_ok(
  $$select public.decide_recommendation((select id from public.coach_recommendations where status = 'pending'), 'edited',
      '{"target_kcal": 1700}')$$,
  '22023', null, 'an edited value beyond 10 percent is rejected');
select throws_ok(
  $$select public.decide_recommendation((select id from public.coach_recommendations where status = 'pending'), 'edited', '{}')$$,
  '22023', null, 'an edited decision needs target_kcal');
select public.decide_recommendation((select id from public.coach_recommendations where status = 'pending'), 'edited',
  '{"target_kcal": 1800}');
select is(
  (select (status, payload->'edited'->>'target_kcal')::text from public.coach_recommendations
   where decided_at > now() - interval '1 minute'),
  '(edited,1800)', 'an edited decision stores the user''s value');
select is((select target_kcal from public.nutrition_goals), 1800, 'an edited decision writes the user''s target');
select is(pg_temp.edited_flag(4), false, 'applying an edited calorie change does not count as a manual edit');

-- Discard changes nothing; a plan recommendation cannot be decided here.
select pg_temp.as_user(12);
select public.evaluate_coach_calorie_change(pg_temp.d0());
select public.decide_recommendation((select id from public.coach_recommendations where status = 'pending'), 'discarded');
select is(
  (select (status, decided_at is not null)::text from public.coach_recommendations where kind = 'calorie_change'),
  '(discarded,t)', 'discarding stores the decision');
select is((select target_kcal from public.nutrition_goals), 2200, 'discarding leaves the goal untouched');

select pg_temp.as_user(2);
reset role;
insert into public.coach_recommendations (id, user_id, plan_id, kind, payload, input_summary)
values ('b0000000-0000-4000-8000-0000000000a3', pg_temp.u(2), 'b0000000-0000-4000-8000-000000000001', 'plan', '{}', '{}');
set local role authenticated;
select pg_temp.as_user(2);
select throws_ok(
  $$select public.decide_recommendation('b0000000-0000-4000-8000-0000000000a3', 'accepted')$$,
  '22023', null, 'a plan recommendation is decided through accept_coach_plan, not here');

reset role;

select * from finish();
rollback;
