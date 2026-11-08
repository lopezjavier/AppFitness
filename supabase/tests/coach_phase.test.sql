-- pgTAP: phases of the coach plan (T-187, US-82 data side, US-69 deficit cap; the minimal phase was removed in T-205).
-- Rules: planning/07 sections 3, 4 and 12.3; planning/04 sections 8.2, 8.9 and 9.3.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(92);

-- ---------------------------------------------------------------------------
-- Fixture helpers
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c7000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
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

create function pg_temp.outcomes() returns jsonb language sql as $$
  select jsonb_build_array(
    jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 78.4, 'unit', 'kg',
      'reason', 'Para bajar de peso al ritmo elegido.', 'deadline', (pg_temp.d0() + 28)::text, 'basis', '{}'::jsonb),
    jsonb_build_object('kind', 'e1rm', 'key', 'e1rm:squat', 'target_value', 100, 'unit', 'kg',
      'reason', 'Tu sentadilla viene subiendo cada semana.', 'deadline', (pg_temp.d0() + 21)::text, 'basis', '{}'::jsonb)) $$;

create function pg_temp.ex(p_pattern text, p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = p_pattern
    and metric_type = 'weight_reps' and equipment = 'barbell' order by key offset p_nth limit 1 $$;
create function pg_temp.iso(p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key offset p_nth limit 1 $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;

create function pg_temp.lift(p_id uuid, p_role text, p_sets int, p_rmin int default 6, p_rmax int default 10,
                             p_rest int default 150) returns jsonb
language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', p_sets,
    'rep_min', p_rmin, 'rep_max', p_rmax, 'rest_seconds', p_rest, 'reason', 'Buen ejercicio base para tu objetivo.') $$;
create function pg_temp.cardio(p_min int) returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', pg_temp.k('walking'), 'exercise_key', 'walking', 'role', 'cardio', 'sets', 1,
    'duration_min', p_min, 'pace', 'suave', 'reason', 'Cardio suave para sumar minutos semanales.') $$;

-- n sessions of one main lift (+ an isolation when with_iso, + a cardio exercise when cardio_min is given).
create function pg_temp.ses(n int, p_sets int, with_iso boolean default false, cardio_min int default null) returns jsonb
language sql as $$
  select jsonb_agg(jsonb_build_object('session_index', i, 'name', 'Cuerpo completo ' || i, 'focus', jsonb_build_array('quads'),
    'exercises', jsonb_build_array(pg_temp.lift(pg_temp.ex(case when i = 1 then 'squat' else 'horizontal_push' end, 0), 'main', p_sets))
       || case when with_iso then jsonb_build_array(pg_temp.lift(pg_temp.iso(i), 'isolation', p_sets, 10, 15, 90)) else '[]'::jsonb end
       || case when cardio_min is null then '[]'::jsonb else jsonb_build_array(pg_temp.cardio(cardio_min)) end)
    order by i)
  from generate_series(1, n) i $$;

create function pg_temp.tr(p_days int, p_phase text default null, p_target int default null, p_per int[] default null)
returns jsonb language sql as $$
  select jsonb_build_object('days_per_week', p_days, 'days_chosen', false, 'session_minutes', 60, 'rep_scheme', 'single',
    'block_weeks', 4, 'equipment', '[]'::jsonb)
  || case when p_phase is null then '{}'::jsonb else jsonb_build_object('phase', p_phase) end
  || case when p_target is null then '{}'::jsonb else jsonb_build_object(
       'cardio', jsonb_build_object('target_min_week', p_target, 'planned_min_week', (select sum(x) from unnest(p_per) x),
         'on_your_own_min', p_target - (select sum(x) from unnest(p_per) x), 'per_routine_min', to_jsonb(p_per), 'pace', 'suave'),
       'routine_total_min', (select to_jsonb(array_agg(60 + x order by o)) from unnest(p_per) with ordinality t(x, o))) end $$;

create function pg_temp.attach(p_plan uuid, p_training jsonb, p_sessions jsonb) returns jsonb
language sql as $$
  select public.attach_coach_block(p_plan, p_training, p_sessions, '{"model":"m","attempts":1}', '{}') $$;

create function pg_temp.pid(n int) returns uuid language sql as $$
  select ('c7000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data (owner)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'phase' || n || '@example.com' from generate_series(1, 16) n;
update public.profiles
set sex = 'male', birth_date = pg_temp.d0() - interval '30 years', height_cm = 178, onboarding_completed = true
where id in (select pg_temp.u(n) from generate_series(1, 16) n);
-- 13: exactly 65 today; 14: one day short of 65.
update public.profiles set birth_date = pg_temp.d0() - interval '65 years' where id = pg_temp.u(13);
update public.profiles set birth_date = (pg_temp.d0() - interval '65 years')::date + 1 where id = pg_temp.u(14);

-- ---------------------------------------------------------------------------
-- Structure, defaults and constraints
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'coach_phase', array['cut', 'gain', 'maintenance', 'recomposition'],
  'the coach_phase enum has the four phases, without regain and minimal');
select col_not_null('public', 'coach_plans', 'phase', 'coach_plans.phase is not null');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_plans'::regclass),
  'RLS is still enabled and forced on coach_plans');

-- The five intentions map to their first phase when a row carries none (the same mapping as the migration backfill).
insert into public.coach_plans (user_id, block_start, intention, nutrition, status)
select pg_temp.u(15), pg_temp.d0() - 10 * i::int, i_name::public.training_intention, '{}', 'discarded'
from unnest(array['fat_loss', 'muscle', 'recomposition', 'maintenance', 'strength']) with ordinality t(i_name, i);
select is(
  (select array_agg(phase::text order by block_start desc) from public.coach_plans where user_id = pg_temp.u(15)),
  array['cut', 'gain', 'recomposition', 'maintenance', 'maintenance'],
  'a plan without a phase takes the one its intention implies (5 rows)');
select is(
  (select count(*)::int from public.coach_plans where user_id = pg_temp.u(15) and phase_reason = 'intention'),
  5, 'and the reason is intention');
select is(
  (select phase_options from public.coach_plans where user_id = pg_temp.u(15) limit 1),
  '{"cut_rate_pct": 0.5, "diet_break": false}'::jsonb, 'phase_options defaults to 0.5 and no diet break');

select throws_ok($$update public.coach_plans set phase_options = '{"cut_rate_pct":0.4,"diet_break":false}' where user_id = pg_temp.u(15)$$,
  '23514', null, 'a cut rate of 0.4 is rejected');
select throws_ok($$update public.coach_plans set phase_options = '{"cut_rate_pct":1.1,"diet_break":false}' where user_id = pg_temp.u(15)$$,
  '23514', null, 'a cut rate of 1.1 is rejected');
select lives_ok($$update public.coach_plans set phase_options = '{"cut_rate_pct":1.0,"diet_break":true}' where user_id = pg_temp.u(15)$$,
  'a cut rate of 1.0 is accepted');
select throws_ok($$update public.coach_plans set phase_options = '{"cut_rate_pct":0.5,"diet_break":"yes"}' where user_id = pg_temp.u(15)$$,
  '23514', null, 'diet_break must be a boolean');
select throws_ok($$update public.coach_plans set phase_options = '[]' where user_id = pg_temp.u(15)$$,
  '23514', null, 'phase_options must be an object');
select throws_ok($$update public.coach_plans set phase_reason = 'bogus' where user_id = pg_temp.u(15)$$,
  '23514', null, 'a phase reason outside the known codes is rejected');
select lives_ok($$update public.coach_plans set phase_reason = 'underweight' where user_id = pg_temp.u(15)$$,
  'the underweight phase reason is accepted by the column');

-- Function privileges.
select ok(not has_function_privilege('anon', 'public.update_coach_plan_options(uuid,jsonb,numeric,boolean)', 'execute'),
  'anon cannot execute update_coach_plan_options');
select ok(not has_function_privilege('anon', 'public.apply_coach_diet_break(date)', 'execute'),
  'anon cannot execute apply_coach_diet_break');
select ok(not has_function_privilege('anon', 'public.coach_phase_history(date)', 'execute'),
  'anon cannot execute coach_phase_history');
select ok(has_function_privilege('authenticated', 'public.coach_phase_history(date)', 'execute'),
  'authenticated can execute coach_phase_history');

-- ---------------------------------------------------------------------------
-- coach_assert_plan through propose_coach_plan (user 1)
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);

select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap') - 'phase', '[]', '{}', '{}')$$,
  '22023', 'invalid plan: phase keys are required', 'a plan without the phase keys is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'bogus'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: phase', 'an unknown phase is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'bogus'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: limited_by', 'an unknown limiter is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568) || '{"phase_reason":"bogus"}', '[]', '{}', '{}')$$,
  '22023', 'plan invariant: phase_reason', 'an unknown phase reason is rejected');

-- The deficit cap.
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2499, -501, 0.57), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: adjustment', 'a deficit of 501 kcal is rejected');
select lives_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), '[]', '{}', '{}')$$,
  'a deficit of 500 kcal flagged deficit_cap is accepted');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2600, -400, 0.45, 'cut', 'deficit_cap'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: deficit_cap_flag', 'the deficit_cap flag needs a deficit of exactly 500');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(3000, -200, 0.3), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: rate_out_of_range', 'a cut slower than 0.5 without a limiter is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, 0, 0, 'cut'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: loss_needs_deficit', 'a cut without a deficit is rejected');

-- Regain is retired (T-200, ADR-91): the phase and the retired reason codes are rejected.
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(3000, 0, 0, 'regain'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: phase', 'the retired regain phase is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap') || '{"phase_reason":"returning_break"}', '[]', '{}', '{}')$$,
  '22023', 'plan invariant: phase_reason', 'the retired returning_break reason is rejected');
select lives_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, 0, 0, 'maintenance') || '{"phase_reason":"underweight"}', '[]', '{}', '{}')$$,
  'an underweight maintenance plan of a fat-loss user is accepted');

-- Maintenance and recomposition: the no-change range (220 + 10 kcal at 80 kg).
select lives_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'maintenance', pg_temp.nut(3200, 200, 0.23, 'maintenance'), '[]', '{}', '{}')$$,
  'maintenance inside the no-change range is accepted');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'maintenance', pg_temp.nut(3240, 240, 0.27, 'maintenance'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: no_change_range', 'maintenance outside the no-change range is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'recomposition', pg_temp.nut(2740, -260, 0.3, 'recomposition'), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: no_change_range', 'recomposition with a deficit outside the range is rejected');
select lives_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'muscle', pg_temp.nut(3300, 300, 0.34, 'gain'), '[]', '{}', '{}')$$,
  'a gain with a surplus is accepted');

-- The diet break.
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'maintenance', pg_temp.nut(3000, 0, 0, 'maintenance', null, true), '[]', '{}', '{}')$$,
  '22023', 'plan invariant: diet_break_phase', 'a diet break on a phase other than cut is rejected');
select throws_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss',
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', false, '{"phase_options":{"cut_rate_pct":0.5,"diet_break":true}}'),
      '[]', '{}', '{}')$$,
  '22023', 'plan invariant: diet_break', 'the diet-break option without its maintenance week is rejected');

-- A valid cut with a diet break and both outcomes: the columns mirror the JSON.
select lives_ok(
  $$select public.propose_coach_plan(pg_temp.d0(), 'fat_loss',
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true), pg_temp.outcomes(), '{}', '{}')$$,
  'a cut with a diet-break week is accepted');
select is((select phase::text from public.coach_plans where status = 'proposed'), 'cut', 'the phase column mirrors the plan JSON');
select is((select phase_reason from public.coach_plans where status = 'proposed'), 'intention', 'and the reason');
select is((select (phase_options->>'diet_break')::boolean from public.coach_plans where status = 'proposed'), true,
  'and the options');

-- ---------------------------------------------------------------------------
-- update_coach_plan_options (user 1, proposed cut)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'proposed'),
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true), 0.4, true)$$,
  '22023', 'cut rate must be between 0.5 and 1.0', 'a cut rate of 0.4 is rejected');
select throws_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'proposed'),
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true), 1.1, true)$$,
  '22023', 'cut rate must be between 0.5 and 1.0', 'a cut rate of 1.1 is rejected');
select throws_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'proposed'),
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), 1.0, true)$$,
  '22023', 'plan invariant: diet_break', 'the diet break without its maintenance week is rejected');

reset role;
select set_config('t.plan1', (select id::text from public.coach_plans where user_id = pg_temp.u(1) and status = 'proposed'), true);
set local role authenticated;
select pg_temp.as_user(2);
select throws_ok(
  $$select public.update_coach_plan_options(current_setting('t.plan1')::uuid,
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), 1.0, false)$$,
  'P0002', null, 'user B cannot change the options of user A (the plan is not visible)');
reset role;
select is((select phase_options->>'cut_rate_pct' from public.coach_plans where user_id = pg_temp.u(1) and status = 'proposed'),
  '0.5', 'and the plan of user A is untouched');
set local role authenticated;
select pg_temp.as_user(2);
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(1)), 0,
  'user B cannot read the plans (and the new columns) of user A');
select throws_ok($$update public.coach_plans set phase = 'maintenance'$$, '42501', null,
  'the owner cannot write the phase directly');

select pg_temp.as_user(1);
select lives_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'proposed'),
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), 1.0, false)$$,
  'the owner recomputes the options of a proposed cut');
select is((select phase_options from public.coach_plans where status = 'proposed'),
  '{"cut_rate_pct": 1.0, "diet_break": false}'::jsonb, 'the options are stored');
select is((select nutrition->'phase_options' from public.coach_plans where status = 'proposed'),
  '{"cut_rate_pct": 1.0, "diet_break": false}'::jsonb, 'and in the plan JSON');
select is((select jsonb_array_length(outcomes) from public.coach_plans where status = 'proposed'), 1,
  'the weight outcome is dropped');
select is((select outcomes->0->>'kind' from public.coach_plans where status = 'proposed'), 'e1rm',
  'and the strength outcome is kept');
select is((select count(*)::int from public.coach_usage), 0, 'a recompute leaves no usage row');
select is((select payload->'outcomes'->0->>'kind' from public.coach_recommendations
           where kind = 'plan' and status = 'pending'), 'e1rm', 'the pending recommendation follows the new plan');

select lives_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'proposed'),
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true), 1.0, true)$$,
  'the diet break can be switched on with its maintenance week');

select public.propose_coach_plan(pg_temp.d0(), 'maintenance', pg_temp.nut(3000, 0, 0, 'maintenance'), '[]', '{}', '{}');
select throws_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'proposed'),
      pg_temp.nut(3000, 0, 0, 'maintenance'), 0.5, false)$$,
  '22023', 'only a cut has options', 'a plan that is not a cut has no options');

select public.propose_coach_plan(pg_temp.d0(), 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), '[]', '{}', '{}');
select public.discard_coach_plan((select id from public.coach_plans where status = 'proposed'));
select throws_ok(
  $$select public.update_coach_plan_options((select id from public.coach_plans where status = 'discarded' order by created_at desc limit 1),
      pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), 0.8, false)$$,
  '55000', 'plan is not proposed', 'a plan that is not proposed cannot be recomputed');

select set_config('request.jwt.claims', '', true);
select throws_ok(
  $$select public.update_coach_plan_options(gen_random_uuid(), pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'), 0.8, false)$$,
  '28000', null, 'update_coach_plan_options rejects an unauthenticated caller');
select throws_ok($$select public.apply_coach_diet_break(pg_temp.d0())$$, '28000', null,
  'apply_coach_diet_break rejects an unauthenticated caller');
select throws_ok($$select public.coach_phase_history(pg_temp.d0())$$, '28000', null,
  'coach_phase_history rejects an unauthenticated caller');

-- ---------------------------------------------------------------------------
-- apply_coach_diet_break (users 5-8)
-- ---------------------------------------------------------------------------
reset role;
insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
select pg_temp.u(n), 'deficit', -500, 2500, 33, 42, 25 from generate_series(5, 8) n;
insert into public.routine_folders (id, user_id, name)
select pg_temp.pid(n), pg_temp.u(n), 'Bloque' from generate_series(5, 7) n;
-- 5: accepted cut with the diet break, saved block, week 3 today. 6: edited by the user. 7: no diet break option.
-- 8: diet break but no saved block.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, accepted_at, phase_options, folder_id)
values (pg_temp.pid(5), pg_temp.u(5), pg_temp.d0() - 14, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
        'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', pg_temp.pid(5)),
       (pg_temp.pid(6), pg_temp.u(6), pg_temp.d0() - 21, 'fat_loss',
        pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true, '{"edited_by_user":true}'),
        'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', pg_temp.pid(6)),
       (pg_temp.pid(7), pg_temp.u(7), pg_temp.d0() - 21, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
        'accepted', now(), default, pg_temp.pid(7)),
       (pg_temp.pid(8), pg_temp.u(8), pg_temp.d0() - 21, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
        'accepted', now(), '{"cut_rate_pct":0.5,"diet_break":true}', null);
-- T-242: plan weeks are the windows with a workout. User 5 trains every week (routine-less workouts), so the block
-- start moves below keep the calendar numbering (week 3, 4 and 5 today).
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
select pg_temp.u(5), 'Filler', 'completed', pg_temp.d0() - d, pg_temp.d0() - d + interval '1 hour'
from unnest(array[27, 20, 13, 6]) d;

set local role authenticated;
select pg_temp.as_user(5);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": false, "week": 3, "target_kcal": 2500}'::jsonb,
  'week 3: the goal already has the base figures, nothing changes');

reset role;
update public.coach_plans set block_start = pg_temp.d0() - 21 where id = pg_temp.pid(5);
set local role authenticated;
select pg_temp.as_user(5);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": true, "week": 4, "target_kcal": 3000}'::jsonb,
  'week 4: the goal takes the maintenance figures');
select is((select target_kcal from public.nutrition_goals), 3000, 'the nutrition goal is at maintenance');
select is((select strategy::text from public.nutrition_goals), 'maintenance', 'with the maintenance strategy');
select ok(coalesce((select (nutrition->>'edited_by_user')::boolean from public.coach_plans where id = pg_temp.pid(5)), false) = false,
  'the plan is not marked as edited by the user');
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": false, "week": 4, "target_kcal": 3000}'::jsonb,
  'a second call in week 4 changes nothing');

reset role;
update public.coach_plans set block_start = pg_temp.d0() - 28 where id = pg_temp.pid(5);
set local role authenticated;
select pg_temp.as_user(5);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": true, "week": 5, "target_kcal": 2500}'::jsonb,
  'after week 4 the goal goes back to the base figures');
select is((select target_kcal from public.nutrition_goals), 2500, 'the goal is back at the deficit');
select is((select (nutrition->>'edited_by_user')::boolean from public.coach_plans where id = pg_temp.pid(5)), false,
  'and the plan is still not edited');

select pg_temp.as_user(6);
select is((public.apply_coach_diet_break(pg_temp.d0()))->>'applied', 'false', 'a plan edited by the user is skipped');
select is((select target_kcal from public.nutrition_goals), 2500, 'and the goal of that user is untouched');
select pg_temp.as_user(7);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": false, "week": 0, "target_kcal": null}'::jsonb,
  'a plan without the diet-break option is skipped');
select pg_temp.as_user(8);
select is((public.apply_coach_diet_break(pg_temp.d0()))->>'applied', 'false', 'a plan without a saved block is skipped');
select pg_temp.as_user(9);
select is((public.apply_coach_diet_break(pg_temp.d0()))->>'applied', 'false', 'a user without a plan is skipped');

-- ---------------------------------------------------------------------------
-- coach_phase_history (users 10-12)
-- ---------------------------------------------------------------------------
reset role;
-- 10: four consecutive cut blocks of four weeks (the last one in its fourth week), one with a diet break.
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, phase, phase_options, training)
values (pg_temp.pid(21), pg_temp.u(10), pg_temp.d0() - 105, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
        'superseded', 'cut', default, '{"weekly_sets":{"quads":10}}'),
       (pg_temp.pid(22), pg_temp.u(10), pg_temp.d0() - 77, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', true),
        'superseded', 'cut', '{"cut_rate_pct":0.5,"diet_break":true}', '{"weekly_sets":{"quads":10}}'),
       (pg_temp.pid(23), pg_temp.u(10), pg_temp.d0() - 49, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
        'superseded', 'cut', default, '{"weekly_sets":{"quads":2}}'),
       (pg_temp.pid(24), pg_temp.u(10), pg_temp.d0() - 21, 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap'),
        'accepted', 'cut', default, '{"weekly_sets":{"quads":2}}'),
-- 11: one maintenance block followed by a proposed plan that must not count.
       (pg_temp.pid(25), pg_temp.u(11), pg_temp.d0() - 28, 'maintenance', pg_temp.nut(3000, 0, 0, 'maintenance'),
        'accepted', 'maintenance', default, '{"weekly_sets":{"chest":10,"quads":10}}'),
       (pg_temp.pid(26), pg_temp.u(11), pg_temp.d0(), 'maintenance', pg_temp.nut(3000, 0, 0, 'maintenance'),
        'proposed', 'maintenance', default, '{"weekly_sets":{"chest":4}}');
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, focus, exercises)
select p, u, w, s, 'Sesion ' || s, '{quads}',
       jsonb_build_array(pg_temp.lift(pg_temp.ex('horizontal_push', 0), 'main', 4, 6, 10),
                         pg_temp.lift(pg_temp.iso(0), 'isolation', 3, 10, 15, 90))
         || case when w = 1 and s = 2 then jsonb_build_array(pg_temp.lift(pg_temp.ex('squat', 0), 'main', 4, 3, 5)) else '[]'::jsonb end
from (values (pg_temp.pid(21), pg_temp.u(10)), (pg_temp.pid(22), pg_temp.u(10)), (pg_temp.pid(23), pg_temp.u(10)),
             (pg_temp.pid(24), pg_temp.u(10)), (pg_temp.pid(25), pg_temp.u(11)), (pg_temp.pid(26), pg_temp.u(11))) t(p, u)
cross join generate_series(1, 4) w
cross join generate_series(1, 2) s;
-- T-242: users 10 and 11 train every week since their first block (routine-less workouts), so the weeks lived per
-- block keep the calendar numbering.
insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
select pg_temp.u(u), 'Filler', 'completed', pg_temp.d0() - 105 + 7 * i + 1, pg_temp.d0() - 105 + 7 * i + 1 + interval '1 hour'
from generate_series(10, 11) u cross join generate_series(0, 14) i;

set local role authenticated;
select pg_temp.as_user(10);
select is((public.coach_phase_history(pg_temp.d0()))->'previous'->>'phase', 'cut', 'the previous block is the newest saved one');
select is((public.coach_phase_history(pg_temp.d0()))->>'minimal_blocks_in_a_row', '0',
  'the legacy minimal counter stays at 0 for old clients');
select is((public.coach_phase_history(pg_temp.d0()))->>'deficit_weeks_done', '15',
  'deficit weeks: the weeks lived per cut block, minus the diet-break week (4 + 3 + 4 + week 4 of the current block)');
select is((public.coach_phase_history(pg_temp.d0()))->'previous'->'sets_per_group', '{"quads": 2}'::jsonb,
  'the previous sets per group come from the block');
select is((public.coach_phase_history(pg_temp.d0()))->'previous'->'main_lifts',
  jsonb_build_array(pg_temp.ex('horizontal_push', 0)::text, pg_temp.ex('squat', 0)::text),
  'the main lifts of week 1 in session order');
select is((public.coach_phase_history(pg_temp.d0()))->'previous'->'rep_ranges'->(pg_temp.ex('horizontal_push', 0)::text),
  '[6, 10]'::jsonb, 'the rep range of a main lift is kept');
select is((public.coach_phase_history(pg_temp.d0()))->'previous'->'rep_ranges'->(pg_temp.iso(0)::text),
  '[10, 15]'::jsonb, 'and the isolation ranges');
select pg_temp.as_user(11);
select is((public.coach_phase_history(pg_temp.d0()))->'previous'->>'phase', 'maintenance', 'a proposed plan is not history');
select is((public.coach_phase_history(pg_temp.d0()))->>'deficit_weeks_done', '0', 'no cut, no deficit weeks');
select pg_temp.as_user(12);
select is(public.coach_phase_history(pg_temp.d0()), '{"previous": null, "minimal_blocks_in_a_row": 0, "deficit_weeks_done": 0}'::jsonb,
  'a user without blocks has no history (and sees none of the others'' blocks)');

-- ---------------------------------------------------------------------------
-- attach_coach_block without the minimal phase (users 1 and 14)
-- ---------------------------------------------------------------------------
reset role;
delete from public.coach_plans where user_id in (pg_temp.u(1), pg_temp.u(13), pg_temp.u(14));
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status)
select pg_temp.pid(n), pg_temp.u(n), pg_temp.d0(), 'fat_loss',
       pg_temp.nut(2500, -500, 0.568, 'cut', 'deficit_cap', false,
         '{"activity":{"cardio_min_week":150,"cardio_edited":false,"cardio_band":[150,300],"steps_day":7000,"steps_basis":"floor","steps_avg":null}}'),
       'proposed'
from unnest(array[1, 14]) n;

set local role authenticated;
select pg_temp.as_user(1);
select throws_ok(
  $$select pg_temp.attach(pg_temp.pid(1), pg_temp.tr(1, 'bogus'), pg_temp.ses(1, 1))$$,
  '22023', 'block invariant: phase', 'an unknown block phase is rejected');
select throws_ok(
  $$select pg_temp.attach(pg_temp.pid(1), pg_temp.tr(1, 'minimal'), pg_temp.ses(1, 1))$$,
  '22023', 'block invariant: phase', 'the removed minimal phase is rejected');
select lives_ok(
  $$select pg_temp.attach(pg_temp.pid(1), pg_temp.tr(2, null, 150, array[60, 60]), pg_temp.ses(2, 3, true, 60))$$,
  'a block without a phase key is accepted');
select is((select phase::text from public.coach_plans where id = pg_temp.pid(1)), 'cut', 'the phase of the plan is kept');
select is((select phase_reason from public.coach_plans where id = pg_temp.pid(1)), 'intention', 'and its reason');
select is((select nutrition->'activity'->>'cardio_min_week' from public.coach_plans where id = pg_temp.pid(1)), '150',
  'and the cardio target follows the block');

select pg_temp.as_user(2);
select throws_ok(
  $$select pg_temp.attach(pg_temp.pid(14), pg_temp.tr(1), pg_temp.ses(1, 1))$$,
  'P0002', null, 'user B cannot attach a block to the plan of user A');

reset role;
select throws_ok(
  $$insert into public.coach_plans (user_id, block_start, intention, nutrition, status, phase)
    values (pg_temp.u(15), pg_temp.d0() + 400, 'fat_loss', '{}', 'discarded', 'minimal')$$,
  '22P02', null, 'a plan cannot store the minimal phase');
select is((select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname <> 'coach_phase_history' and p.prosrc ilike '%minimal%'), 0,
  'no public function mentions the minimal phase (only coach_phase_history keeps the legacy counter field)');

select * from finish();
rollback;
