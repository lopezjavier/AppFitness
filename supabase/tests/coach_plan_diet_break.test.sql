-- pgTAP: the diet-break switch of an accepted cut (T-248, US-115). Rules: planning/04 section 8.9, planning/07 section 4.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(37);

-- ---------------------------------------------------------------------------
-- Fixture helpers
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c8000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.pid(n int) returns uuid language sql as $$
  select ('c8000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

-- Persisted nutrition JSON at 80 kg, floor 1500: the expenditure is target - adj; db adds the maintenance week.
create function pg_temp.nut(target int, adj int, rate numeric, ph text default 'cut', lim text default null,
                            db boolean default false, extra jsonb default '{}') returns jsonb
language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', target - adj, 'expenditure_source', 'formula',
    'rate_pct_week', rate, 'rate_kg_week', adj * 7 / 7700.0, 'target_rate_pct', 0.5, 'limited_by', lim, 'floor_kcal', 1500,
    'target_kcal', target, 'calorie_adjustment', adj,
    'strategy', case when adj < 0 then 'deficit' when adj > 0 then 'surplus' else 'maintenance' end,
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', false, 'calorie_changes', 0,
    'phase', ph, 'phase_reason', 'intention',
    'phase_options', jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', db))
  || case when db then jsonb_build_object('diet_break', jsonb_build_object(
       'target_kcal', target - adj, 'calorie_adjustment', 0, 'strategy', 'maintenance', 'protein_pct', 33,
       'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 198, 'carbs_g', 262, 'fat_g', 83, 'week', 4)) else '{}'::jsonb end
  || extra $$;

-- The week-4 figures the coach function would send for the base plan nut(2500, -500, ...).
create function pg_temp.wk() returns jsonb language sql as $$
  select pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true)->'diet_break' $$;


grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data (owner)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'diet-break' || n || '@example.com' from generate_series(1, 12) n;
update public.profiles
set sex = 'male', birth_date = pg_temp.d0() - interval '30 years', height_cm = 178, onboarding_completed = true
where id in (select pg_temp.u(n) from generate_series(1, 12) n);

insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
select pg_temp.u(n), 'deficit', -500, 2500, 33, 42, 25 from generate_series(1, 12) n;
-- User 1 sits at the week-4 maintenance figures already (the break was applied).
update public.nutrition_goals set strategy = 'maintenance', calorie_adjustment = 0, target_kcal = 3000
  where user_id = pg_temp.u(1);
insert into public.routine_folders (id, user_id, name)
select pg_temp.pid(n), pg_temp.u(n), 'Bloque' from generate_series(1, 4) n;

-- 1: break on, week 4, goal at maintenance. 2: no break, week 3. 3: break on, week 3 (goal at base).
-- 4: no break, week 4 (goal at base) but turned on/off. 5: edited by the user. 6: proposed. 7: maintenance phase.
-- 8: discarded. 9: break on, week 4, goal NOT at the break figures. 12: break on, week 3, to be turned off.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, outcomes, status, accepted_at, phase_options, folder_id)
values
  (pg_temp.pid(1), pg_temp.u(1), pg_temp.d0() - 21, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
   '[]', 'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', pg_temp.pid(1)),
  (pg_temp.pid(2), pg_temp.u(2), pg_temp.d0() - 14, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
   '[]', 'accepted', now(), default, pg_temp.pid(2)),
  (pg_temp.pid(3), pg_temp.u(3), pg_temp.d0() - 14, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
   '[]', 'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', pg_temp.pid(3)),
  (pg_temp.pid(4), pg_temp.u(4), pg_temp.d0() - 21, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
   '[]', 'accepted', now(), default, pg_temp.pid(4)),
  (pg_temp.pid(5), pg_temp.u(5), pg_temp.d0() - 14, 'fat_loss',
   pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', false, '{"edited_by_user":true}'),
   '[]', 'accepted', now(), default, null),
  (pg_temp.pid(6), pg_temp.u(6), pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
   '[]', 'proposed', null, default, null),
  (pg_temp.pid(7), pg_temp.u(7), pg_temp.d0() - 14, 'maintenance', pg_temp.nut(2500, 0, 0, 'maintenance'),
   '[]', 'accepted', now(), default, null),
  (pg_temp.pid(8), pg_temp.u(8), pg_temp.d0() - 14, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
   '[]', 'discarded', null, default, null),
  (pg_temp.pid(9), pg_temp.u(9), pg_temp.d0() - 21, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
   '[]', 'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', null),
  (pg_temp.pid(12), pg_temp.u(12), pg_temp.d0() - 14, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
   '[]', 'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', null);
-- Every cut carries a weight outcome whose deadline is 21 days into its block (the invariant of 04 section 8).
update public.coach_plans set outcomes = jsonb_build_array(jsonb_build_object('kind', 'weight', 'key', 'weight',
  'target_value', 78.4, 'unit', 'kg', 'reason', 'Para bajar de peso al ritmo elegido.',
  'deadline', (block_start + 21)::text, 'basis', '{}'::jsonb))
  where phase = 'cut' and user_id in (select pg_temp.u(n) from generate_series(1, 12) n);
-- Plan weeks are the windows with a workout (T-242): users 1, 2, 3, 4, 9 and 12 train every week.
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
select pg_temp.u(n), 'Filler', 'completed', pg_temp.d0() - d, pg_temp.d0() - d + interval '1 hour'
from unnest(array[1, 2, 3, 4, 9, 12]) n, unnest(array[27, 20, 13, 6]) d;
insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary, status, decided_at)
values (pg_temp.u(2), pg_temp.pid(2), 'plan', '{"nutrition":{},"outcomes":[]}', '{}', 'accepted', now());

-- Snapshots to prove what stays untouched.
create temp table snap as
select p.id, p.outcomes, p.status, (p.nutrition - 'diet_break' - 'phase_options') as base,
       (select r.payload from public.coach_recommendations r where r.plan_id = p.id) as rec
from public.coach_plans p where p.id = pg_temp.pid(2);
grant select on snap to authenticated;

-- ---------------------------------------------------------------------------
-- Structure and privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.set_coach_plan_diet_break(uuid,boolean,jsonb)', 'execute'),
  'anon cannot execute set_coach_plan_diet_break');
select ok(has_function_privilege('authenticated', 'public.set_coach_plan_diet_break(uuid,boolean,jsonb)', 'execute'),
  'authenticated can execute set_coach_plan_diet_break');
select throws_ok($$select public.set_coach_plan_diet_break(gen_random_uuid(), true, pg_temp.wk())$$, '28000', null,
  'an unauthenticated caller is rejected');

-- ---------------------------------------------------------------------------
-- Turning ON (user 2, week 3): only the break and its flag change
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(2);
select is(
  public.set_coach_plan_diet_break(pg_temp.pid(2), true, pg_temp.wk())->'nutrition'->'diet_break'->>'target_kcal', '3000',
  'turning the break on returns the maintenance week');
select is((select (phase_options->>'diet_break')::boolean from public.coach_plans where id = pg_temp.pid(2)), true,
  'the phase_options column carries the break flag');
select is((select (nutrition->'phase_options'->>'diet_break')::boolean from public.coach_plans where id = pg_temp.pid(2)), true,
  'and so does nutrition.phase_options');
select is((select (phase_options->>'cut_rate_pct')::numeric from public.coach_plans where id = pg_temp.pid(2)), 0.5,
  'the cut rate is untouched');
select is((select nutrition->'diet_break'->>'week' from public.coach_plans where id = pg_temp.pid(2)), '4',
  'the stored week is week 4');
select is((select (p.nutrition - 'diet_break' - 'phase_options') from public.coach_plans p where p.id = pg_temp.pid(2)),
  (select base from snap), 'calories, band, macros, edited_by_user and activity are identical');
select is((select outcomes from public.coach_plans where id = pg_temp.pid(2)), (select outcomes from snap),
  'the outcomes are identical (the weight outcome is kept)');
select is((select payload from public.coach_recommendations where plan_id = pg_temp.pid(2)), (select rec from snap),
  'the recommendation is untouched');
select is((select target_kcal from public.nutrition_goals), 2500, 'turning it on in week 3 leaves the goal alone');

-- apply_coach_diet_break after the toggle: week 3, 4, 5
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": false, "week": 3, "target_kcal": 2500}'::jsonb,
  'week 3 after the toggle: nothing to apply');
reset role;
update public.coach_plans set block_start = pg_temp.d0() - 21 where id = pg_temp.pid(2);
set local role authenticated;
select pg_temp.as_user(2);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": true, "week": 4, "target_kcal": 3000}'::jsonb,
  'week 4 after the toggle: the goal takes the maintenance figures');
reset role;
update public.coach_plans set block_start = pg_temp.d0() - 28 where id = pg_temp.pid(2);
set local role authenticated;
select pg_temp.as_user(2);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": true, "week": 5, "target_kcal": 2500}'::jsonb,
  'week 5 after the toggle: back to the base figures');

-- ---------------------------------------------------------------------------
-- Turning OFF
-- ---------------------------------------------------------------------------
-- User 1: week 4, goal at maintenance -> the base figures come back at once.
select pg_temp.as_user(1);
select is(public.set_coach_plan_diet_break(pg_temp.pid(1), false, null)->'nutrition'->'diet_break', 'null'::jsonb,
  'turning the break off clears the stored week');
select is((select target_kcal from public.nutrition_goals), 2500, 'off in week 4: the base calories are restored');
select is((select strategy::text from public.nutrition_goals), 'deficit', 'and the base strategy');
select is((select (phase_options->>'diet_break')::boolean from public.coach_plans where id = pg_temp.pid(1)), false,
  'the option flag is off');
select is((select (nutrition->>'edited_by_user')::boolean from public.coach_plans where id = pg_temp.pid(1)), false,
  'the plan is not marked as edited');
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": false, "week": 0, "target_kcal": null}'::jsonb,
  'apply_coach_diet_break has nothing left to do (restored once)');

-- User 12: week 3, goal at base -> the goal is untouched.
select pg_temp.as_user(12);
select public.set_coach_plan_diet_break(pg_temp.pid(12), false, null);
select is((select target_kcal from public.nutrition_goals), 2500, 'off outside week 4: the goal is untouched');

-- User 9: week 4, goal at the BASE figures (the break was not applied yet) -> untouched.
select pg_temp.as_user(9);
select public.set_coach_plan_diet_break(pg_temp.pid(9), false, null);
select is((select target_kcal from public.nutrition_goals), 2500, 'off in week 4 with the goal already at base: untouched');

-- ---------------------------------------------------------------------------
-- Refusals
-- ---------------------------------------------------------------------------
select pg_temp.as_user(5);
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(5), true, pg_temp.wk())$$, '22023', null,
  'a plan edited by the user is refused');
select pg_temp.as_user(6);
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(6), true, pg_temp.wk())$$, '55000', null,
  'a proposed plan is refused (plan_recompute owns it)');
select pg_temp.as_user(8);
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(8), true, pg_temp.wk())$$, '55000', null,
  'a discarded plan is refused');
select pg_temp.as_user(7);
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(7), true, pg_temp.wk())$$, '22023', null,
  'a plan that is not a cut is refused');

select pg_temp.as_user(4);
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), true, null)$$, '22023', null,
  'on without a week is refused');
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), false, pg_temp.wk())$$, '22023', null,
  'off with a week is refused');
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), true, pg_temp.wk() - 'fat_g')$$, '22023', null,
  'a week with a missing key is refused');
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), true, pg_temp.wk() || '{"week":5}')$$, '22023', null,
  'a week that is not week 4 is refused');
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), true, pg_temp.wk() || '{"calorie_adjustment":50}')$$,
  '22023', null, 'a week inconsistent with the stored expenditure is refused');
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), true, '[1]'::jsonb)$$, '22023', null,
  'a week that is not an object is refused');
select throws_ok($$select public.set_coach_plan_diet_break(null, true, pg_temp.wk())$$, '22023', null,
  'a null plan id is refused');
select is((select (phase_options->>'diet_break')::boolean from public.coach_plans where id = pg_temp.pid(4)), false,
  'a refused call changes nothing');

-- ---------------------------------------------------------------------------
-- Cross-user isolation
-- ---------------------------------------------------------------------------
select pg_temp.as_user(3);
select throws_ok($$select public.set_coach_plan_diet_break(pg_temp.pid(4), true, pg_temp.wk())$$, 'P0002', null,
  'user 3 cannot switch the break of the plan of user 4');
select is((select count(*)::int from public.coach_plans where id = pg_temp.pid(4)), 0, 'and cannot even read it');

select * from finish();
rollback;
