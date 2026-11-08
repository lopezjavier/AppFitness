-- pgTAP: the end-of-block review and the phase change (T-233, US-101, US-103; rules in planning/04 section 8.10).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(93);

-- ---------------------------------------------------------------------------
-- Fixture helpers
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('b7000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.sq() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'squat' order by key limit 1 $$;
create function pg_temp.sqkey() returns text language sql as $$
  select key from public.exercises where id = pg_temp.sq() $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

-- Persisted nutrition JSON at 80 kg, floor 1500 (as coach_phase.test.sql).
create function pg_temp.nut(target int, adj int, rate numeric, ph text default 'cut', lim text default null,
                            db boolean default false) returns jsonb
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
       'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 198, 'carbs_g', 262, 'fat_g', 83, 'week', 4)) else '{}'::jsonb end $$;

-- A block with one session per week (a main squat in each) and a saved folder; the phase column mirrors the nutrition.
create function pg_temp.mk(u int, p_status text, p_start date, p_tag text, p_intention text default 'fat_loss',
                           p_nut jsonb default null, p_weeks int default 4, p_folder boolean default true,
                           p_db boolean default false, p_outcomes jsonb default '[]') returns uuid
language plpgsql as $$
declare
  v_plan uuid := gen_random_uuid();
  v_folder uuid;
  v_r uuid;
  v_n jsonb := coalesce(p_nut, pg_temp.nut(2500, -500, 0.568));
  w int;
begin
  if p_folder then
    insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque ' || p_tag) returning id into v_folder;
  end if;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id, phase, phase_options,
                                  outcomes)
  values (v_plan, pg_temp.u(u), p_start, p_intention::public.training_intention, v_n, p_status, v_folder,
          (v_n->>'phase')::public.coach_phase, jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', p_db), p_outcomes);
  if p_folder then
    insert into public.routines (user_id, name, folder_id) values (pg_temp.u(u), 'R' || u || p_tag, v_folder)
    returning id into v_r;
    for w in 1..p_weeks loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
      values (v_plan, pg_temp.u(u), w, 1, 'Sesion',
              jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.sq(), 'role', 'main', 'sets', 3)), v_r);
    end loop;
  end if;
  return v_plan;
end $$;

create function pg_temp.hist(u int, p_day date, p_ex uuid, p_w numeric, p_reps int) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid(); s timestamptz := pg_temp.at(p_day);
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), 'H', 'completed', s, s + interval '1 hour');
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
  values (v, p_ex, 0, 1, 'working', p_w, p_reps, s + interval '30 minutes');
  return v;
end $$;

-- Squat in plan weeks 1..4 of a block that started d0 - 28 (start + 2, +9, +16, +23 days), the plan week marked done.
create function pg_temp.squats(u int, p_plan uuid, w1 numeric, w2 numeric, w3 numeric, w4 numeric) returns void
language plpgsql as $$
declare ws numeric[] := array[w1, w2, w3, w4]; i int;
begin
  for i in 1..4 loop
    if ws[i] is not null then
      update public.coach_plan_sessions set session_id = pg_temp.hist(u, pg_temp.d0() - 28 + 7 * (i - 1) + 2, pg_temp.sq(), ws[i], 5)
        where plan_id = p_plan and week = i and session_index = 1;
    end if;
  end loop;
end $$;

create function pg_temp.weigh(u int, p_day date, p_kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, measured_at, weight_kg, source)
  values (pg_temp.u(u), pg_temp.at(p_day), p_kg, 'manual') $$;
create function pg_temp.waist(u int, p_day date, p_cm numeric) returns void language sql as $$
  insert into public.body_circumferences (user_id, measured_at, site, value_cm)
  values (pg_temp.u(u), pg_temp.at(p_day), 'waist', p_cm) $$;
create function pg_temp.slow_weights(u int, p_step numeric) returns void language plpgsql as $$
declare i int;
begin
  for i in 0..3 loop
    perform pg_temp.weigh(u, pg_temp.d0() - 26 + 7 * i, 80 - p_step * i);
  end loop;
end $$;

-- Three consecutive blocks ending yesterday (the older two superseded). Returns the accepted plan.
create function pg_temp.three(u int, p_nut jsonb, p_db boolean default false) returns uuid language plpgsql as $$
begin
  perform pg_temp.mk(u, 'superseded', pg_temp.d0() - 84, 'a', 'fat_loss', p_nut, 4, true, p_db);
  perform pg_temp.mk(u, 'superseded', pg_temp.d0() - 56, 'b', 'fat_loss', p_nut, 4, true, p_db);
  return pg_temp.mk(u, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', p_nut, 4, true, p_db);
end $$;

create function pg_temp.goal(u int, p_kcal int, p_adj int, p_strategy text) returns void language plpgsql as $$
begin
  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (pg_temp.u(u), p_strategy::public.goal_strategy, p_adj, p_kcal, 33, 42, 25);
  perform set_config('app.coach_write', 'off', true);
end $$;

-- A session on the block's routine with a working set done without a rep range (a no-range flag when applied).
create function pg_temp.flag_session(u int, p_day date, p_routine uuid) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid(); s timestamptz := pg_temp.at(p_day);
begin
  insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps)
  values (p_routine, pg_temp.sq(), 0, 3, 5);
  insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), p_routine, 'F', 'completed', s, s + interval '1 hour');
  insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
  values (v, pg_temp.sq(), 0, 1, 'working', 60, 5, s + interval '30 minutes');
  return v;
end $$;

create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;
create function pg_temp.review_of(n int) returns uuid language sql as $$
  select id from public.coach_block_reviews where plan_id = pg_temp.plan_of(n) $$;
create function pg_temp.rv(n int) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(n);
  return public.open_coach_block_review(pg_temp.d0());
end $$;
create function pg_temp.snap(n int) returns text language sql as $$
  select md5(
    coalesce((select jsonb_agg(to_jsonb(g) order by g.user_id) from public.nutrition_goals g where g.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(p) - 'updated_at' order by p.id) from public.coach_plans p where p.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(s) order by s.id) from public.coach_plan_sessions s where s.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(c) order by c.id) from public.coach_changes c where c.user_id = pg_temp.u(n))::text, '')) $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data (owner)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'blockreview' || n || '@example.com' from generate_series(1, 26) n;
update public.profiles
set sex = 'male', birth_date = pg_temp.d0() - interval '30 years', height_cm = 178, onboarding_completed = true
where id in (select pg_temp.u(n) from generate_series(1, 26) n);

-- 1: a full block. e1RM rises each week, 3 of 4 plan weeks done, weigh-ins 80 -> 78, a waist pair 2.9 cm apart.
select pg_temp.mk(1, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', null, 4, true, false,
  jsonb_build_array(
    jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 78, 'unit', 'kg', 'deadline', (pg_temp.d0() - 1)::text),
    jsonb_build_object('kind', 'e1rm', 'key', pg_temp.sqkey(), 'target_value', 134, 'unit', 'kg', 'deadline', (pg_temp.d0() - 1)::text),
    jsonb_build_object('kind', 'e1rm', 'key', 'no-such-lift', 'target_value', 50, 'unit', 'kg', 'deadline', (pg_temp.d0() - 1)::text)));
select pg_temp.squats(1, pg_temp.plan_of(1), 100, 105, 110, 115);
update public.coach_plan_sessions set session_id = null where plan_id = pg_temp.plan_of(1) and week = 4;
select pg_temp.weigh(1, pg_temp.d0() - 26, 80);
select pg_temp.weigh(1, pg_temp.d0() - 3, 78);
select pg_temp.waist(1, pg_temp.d0() - 60, 100.0);
select pg_temp.waist(1, pg_temp.d0() - 10, 97.1);
-- 2: week 4 of 4 (not finished). 3: today is the last day.
select pg_temp.mk(2, 'accepted', pg_temp.d0() - 21, 'c');
select pg_temp.mk(3, 'accepted', pg_temp.d0() - 27, 'c');
-- 4: a deload in week 4 (done and lighter), a single waist reading.
select pg_temp.mk(4, 'accepted', pg_temp.d0() - 28, 'c');
update public.coach_plan_sessions set deload = true where plan_id = pg_temp.plan_of(4) and week = 4;
select pg_temp.squats(4, pg_temp.plan_of(4), 100, 105, 110, 90);
select pg_temp.waist(4, pg_temp.d0() - 10, 97.0);
-- 6: an accepted plan with no saved block.
select pg_temp.mk(6, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', null, 4, false);
-- 7: three cut blocks (12 weeks). 8: two. 9: three with the diet break (9 weeks).
select pg_temp.three(7, null);
select pg_temp.mk(8, 'superseded', pg_temp.d0() - 56, 'b');
select pg_temp.mk(8, 'accepted', pg_temp.d0() - 28, 'c');
select pg_temp.three(9, pg_temp.nut(2500, -500, 0.568, 'cut', null, true), true);
-- 10: limited by the floor, slope -0.1 against a target of -0.4545 (slower than half). 11: slope -0.3 (faster).
-- 12: as 10, but an accepted calorie change raised it above the floor.
select pg_temp.mk(10, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', pg_temp.nut(1500, -500, 0.568, 'cut', 'floor'));
select pg_temp.slow_weights(10, 0.1);
select pg_temp.mk(11, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', pg_temp.nut(1500, -500, 0.568, 'cut', 'floor'));
select pg_temp.slow_weights(11, 0.3);
select pg_temp.mk(12, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', pg_temp.nut(1500, -500, 0.568, 'cut', 'floor'));
select pg_temp.slow_weights(12, 0.1);
insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary, status, decided_at)
values (pg_temp.u(12), pg_temp.plan_of(12), 'calorie_change', '{"from_kcal":1500,"to_kcal":1600,"change_kcal":100}', 'rate_outside_interval',
        '{}', 'accepted', now());
-- 13: a maintenance block after a cut, intention fat_loss. 14: the same with intention maintenance.
select pg_temp.mk(13, 'superseded', pg_temp.d0() - 56, 'b', 'fat_loss');
select pg_temp.mk(13, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', pg_temp.nut(2500, 0, 0, 'maintenance'));
select pg_temp.mk(14, 'superseded', pg_temp.d0() - 56, 'b', 'maintenance');
select pg_temp.mk(14, 'accepted', pg_temp.d0() - 28, 'c', 'maintenance', pg_temp.nut(2500, 0, 0, 'maintenance'));
-- 15: the first block of a plain gain.
select pg_temp.mk(15, 'accepted', pg_temp.d0() - 28, 'c', 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'));
-- 16: three cut blocks, each limited by the floor, slow: both rules fire, the length wins.
select pg_temp.three(16, pg_temp.nut(1500, -500, 0.568, 'cut', 'floor'));
select pg_temp.slow_weights(16, 0.1);
-- 17: week 2 of a block, the weight target already reached. 22: not reached outcomes. 23: a waist pair of 3.0 cm.
select pg_temp.mk(17, 'accepted', pg_temp.d0() - 7, 'c', 'fat_loss', null, 4, true, false,
  jsonb_build_array(jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 80, 'unit', 'kg',
                                        'deadline', (pg_temp.d0() + 20)::text)));
select pg_temp.weigh(17, pg_temp.d0(), 79);
select pg_temp.goal(17, 2500, -500, 'deficit');
select pg_temp.mk(22, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', null, 4, true, false,
  jsonb_build_array(
    jsonb_build_object('kind', 'weight', 'key', 'weight', 'target_value', 77, 'unit', 'kg', 'deadline', (pg_temp.d0() - 1)::text),
    jsonb_build_object('kind', 'e1rm', 'key', pg_temp.sqkey(), 'target_value', 140, 'unit', 'kg', 'deadline', (pg_temp.d0() - 1)::text)));
select pg_temp.squats(22, pg_temp.plan_of(22), 100, 105, 110, 115);
select pg_temp.weigh(22, pg_temp.d0() - 3, 78);
select pg_temp.mk(23, 'accepted', pg_temp.d0() - 28, 'c');
select pg_temp.waist(23, pg_temp.d0() - 60, 100.0);
select pg_temp.waist(23, pg_temp.d0() - 10, 97.0);
-- 18: a finished block with a diet break, goals on the diet-break figures, a session after the last day.
select pg_temp.mk(18, 'accepted', pg_temp.d0() - 28, 'c', 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', null, true), 4, true, true);
select pg_temp.goal(18, 3000, 0, 'maintenance');
select pg_temp.flag_session(18, pg_temp.d0(), (select routine_id from public.coach_plan_sessions where plan_id = pg_temp.plan_of(18) limit 1));
-- 19: the control, week 4 of 4 with the same data.
select pg_temp.mk(19, 'accepted', pg_temp.d0() - 21, 'c', 'fat_loss', pg_temp.nut(2500, -500, 0.568, 'cut', null, true), 4, true, true);
select pg_temp.goal(19, 2500, -500, 'deficit');
select pg_temp.flag_session(19, pg_temp.d0(), (select routine_id from public.coach_plan_sessions where plan_id = pg_temp.plan_of(19) limit 1));
-- 20: three cut blocks and an older proposed plan, for "Aceptar". 21: three cut blocks for a wrong phase.
select pg_temp.three(20, null);
select pg_temp.goal(20, 2500, -500, 'deficit');
insert into public.coach_plans (user_id, block_start, intention, nutrition, status)
values (pg_temp.u(20), pg_temp.d0() - 2, 'fat_loss', pg_temp.nut(2400, -500, 0.568), 'proposed');
select pg_temp.three(21, null);

-- ---------------------------------------------------------------------------
-- Structure and security
-- ---------------------------------------------------------------------------
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.coach_block_reviews'::regclass),
  'RLS is enabled and forced on coach_block_reviews');
select ok(not has_function_privilege('anon', 'public.open_coach_block_review(date)', 'execute'), 'anon cannot open a review');
select ok(not has_function_privilege('anon', 'public.keep_coach_phase(uuid)', 'execute'), 'anon cannot keep the phase');
select ok(not has_function_privilege('anon', 'public.accept_coach_phase(uuid,jsonb,jsonb,jsonb,jsonb,date)', 'execute'),
  'anon cannot accept the phase');
select ok(not has_function_privilege('authenticated', 'public.coach_block_review_build(uuid,date,date)', 'execute'),
  'the inputs builder is internal');
select throws_ok($$select public.open_coach_block_review(current_date)$$, '28000', null, 'opening needs a signed-in user');
select throws_ok($$select public.keep_coach_phase(gen_random_uuid())$$, '28000', null, 'keeping needs a signed-in user');

-- ---------------------------------------------------------------------------
-- Lifecycle (users 2, 3, 5, 6, then 1)
-- ---------------------------------------------------------------------------
set local role authenticated;
select is(pg_temp.rv(2), null, 'before the last day there is no review');
select is(pg_temp.rv(3), null, 'on the last day there is no review');
select is(pg_temp.rv(5), null, 'no accepted plan: no review');
select is(pg_temp.rv(6), null, 'an accepted plan with no saved block: no review');
select pg_temp.as_user(3);
select is(public.coach_block_end(pg_temp.plan_of(3)), pg_temp.d0(), 'the last day of a block is start + 7 * weeks - 1');
select is((select count(*)::int from public.coach_block_reviews), 0, 'nothing was stored by the early calls');

create temp table r1 as select pg_temp.rv(1) as r;
grant select on r1 to authenticated;
select is((select r->>'ended_on' from r1), (pg_temp.d0() - 1)::text, 'the day after the last day the review opens');
select is((select r->>'phase_decision' from r1), null, 'it starts undecided');

-- ---------------------------------------------------------------------------
-- Inputs of user 1 (full block) and user 4 (deload)
-- ---------------------------------------------------------------------------
select is((select r->'inputs'->'adherence' from r1), '{"completed": 3, "planned": 4, "pct": 75}'::jsonb,
  'adherence sums completed and planned over all weeks');
select is((select jsonb_array_length(r->'inputs'->'lifts') from r1), 1, 'one main lift in the block');
select is((select jsonb_array_length(r->'inputs'->'lifts'->0->'weeks') from r1), 4,
  'a lift has a value for each week with a logged set');
select is((select (r->'inputs'->'lifts'->0->>'first_e1rm')::numeric from r1), public.coach_e1rm(100, 5), 'first e1RM is week 1');
select is((select (r->'inputs'->'lifts'->0->>'last_e1rm')::numeric from r1), public.coach_e1rm(115, 5), 'last e1RM is the last week with data');
select is((select (r->'inputs'->'lifts'->0->>'change_kg')::numeric from r1), public.coach_e1rm(115, 5) - public.coach_e1rm(100, 5),
  'change is last minus first');
select is((select (r->'inputs'->'lifts'->0->>'stalled')::boolean from r1), false, 'a rising lift is not stalled');

create temp table r4 as select pg_temp.rv(4) as r;
grant select on r4 to authenticated;
select is((select jsonb_array_length(r->'inputs'->'lifts'->0->'weeks') from r4), 3, 'a deload week is left out of the e1RM weeks');
select is((select r->'inputs'->'adherence'->>'completed' from r4), '4', 'but the deload week counts in adherence');
select is((select (r->'inputs'->'lifts'->0->>'last_e1rm')::numeric from r4), public.coach_e1rm(110, 5),
  'the lighter deload week does not become the last e1RM');
select is((select (r->'inputs'->'lifts'->0->>'stalled')::boolean from r4), false, 'and does not stall the lift');

select is((select (g->>'sets')::numeric from jsonb_array_elements((select r->'inputs'->'groups' from r1)) g
           where g->>'muscle_group' = (select muscle_group::text from public.exercises where id = pg_temp.sq())),
  4.0, 'sets per group count the four logged squat sets of the block');
select is((select r->'inputs'->'groups' from r1),
  (select coalesce(jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets) order by g.sets desc, g.muscle_group), '[]'::jsonb)
   from public.muscle_group_sets(pg_temp.d0() - 28, pg_temp.d0() - 1, true) g),
  'groups are the strength-only sets over the block, indirect sets at half');

select is((select (r->'inputs'->'weight'->>'first_avg_kg')::numeric from r1), 80.0, 'first weekly weight average');
select is((select (r->'inputs'->'weight'->>'last_avg_kg')::numeric from r1), 78.0, 'last weekly weight average');
select ok((select (r->'inputs'->'weight'->>'slope_kg_week')::numeric < 0 from r1), 'the trend slope is negative');
select is((select (r->'inputs'->'weight'->>'target_rate_kg_week')::numeric from r1), -500 * 7 / 7700.0,
  'the target rate comes from the plan');

select is((select r->'inputs'->'waist'->>'delta_cm' from r1), '-2.9', 'waist delta against a reading at least 28 days older');
select is((select (r->'inputs'->'waist'->>'within_margin')::boolean from r1), true, '2.9 cm is within the margin');
select is((select (pg_temp.rv(23)->'inputs'->'waist'->>'within_margin')::boolean), false, '3.0 cm is not within the margin');
select is((select r->'inputs'->'waist' from r4), 'null'::jsonb, 'a single waist reading gives no pair');

-- ---------------------------------------------------------------------------
-- Outcomes
-- ---------------------------------------------------------------------------
select is((select (o->>'reached')::boolean from jsonb_array_elements((select r->'inputs'->'outcomes' from r1)) o where o->>'kind' = 'weight'),
  true, 'a weight loss at the target counts as reached (boundary)');
select is((select (o->>'reached')::boolean from jsonb_array_elements((select r->'inputs'->'outcomes' from r1)) o
           where o->>'key' = pg_temp.sqkey()), true, 'an e1RM at the target counts as reached (boundary)');
select is((select o->>'current_value' from jsonb_array_elements((select r->'inputs'->'outcomes' from r1)) o where o->>'key' = 'no-such-lift'),
  null, 'an outcome without data has no current value');
select is((select (o->>'reached')::boolean from jsonb_array_elements((select r->'inputs'->'outcomes' from r1)) o where o->>'key' = 'no-such-lift'),
  false, 'and is not reached');
create temp table r22 as select pg_temp.rv(22) as r;
grant select on r22 to authenticated;
select is((select (o->>'reached')::boolean from jsonb_array_elements((select r->'inputs'->'outcomes' from r22)) o where o->>'kind' = 'weight'),
  false, 'a weight above the target is not reached');
select is((select (o->>'reached')::boolean from jsonb_array_elements((select r->'inputs'->'outcomes' from r22)) o where o->>'kind' = 'e1rm'),
  false, 'an e1RM below the target is not reached');
select is((select (o->>'current_value')::numeric from jsonb_array_elements((select r->'inputs'->'outcomes' from r22)) o where o->>'kind' = 'e1rm'),
  public.coach_e1rm(115, 5), 'the current e1RM is the best of the block');

-- ---------------------------------------------------------------------------
-- A stored review is never recomputed; one row per plan
-- ---------------------------------------------------------------------------
reset role;
select pg_temp.hist(1, pg_temp.d0() - 2, pg_temp.sq(), 200, 5);
select pg_temp.weigh(1, pg_temp.d0() - 1, 70);
set local role authenticated;
select is(pg_temp.rv(1), (select r from r1), 'a second call returns the stored review, even after new logs');
reset role;
select is((select count(*)::int from public.coach_block_reviews), 4, 'one row per plan (users 1, 4, 22 and 23)');
set local role authenticated;

-- ---------------------------------------------------------------------------
-- Phase triggers
-- ---------------------------------------------------------------------------
select is(pg_temp.rv(7)->'phase_proposal', jsonb_build_object('to_phase', 'maintenance', 'reason', 'cut_length',
  'figures', jsonb_build_object('deficit_weeks', 12)), 'three cut blocks (12 deficit weeks) propose maintenance');
select is(pg_temp.rv(8)->'phase_proposal', 'null'::jsonb, 'two cut blocks propose nothing');
select is(pg_temp.rv(9)->'phase_proposal', 'null'::jsonb, 'three cut blocks with the diet break (9 weeks) propose nothing');
select is(pg_temp.rv(10)->'phase_proposal'->>'reason', 'floor_limited', 'a slow cut held at the floor proposes maintenance');
select is(pg_temp.rv(10)->'phase_proposal'->>'to_phase', 'maintenance', 'to maintenance');
select is(pg_temp.rv(11)->'phase_proposal', 'null'::jsonb, 'a floor-limited cut losing faster than half the target proposes nothing');
select is(pg_temp.rv(12)->'phase_proposal', 'null'::jsonb, 'a floor-limited cut raised above the floor by an accepted change proposes nothing');
select is(pg_temp.rv(13)->'phase_proposal', jsonb_build_object('to_phase', 'cut', 'reason', 'back_to_goal',
  'figures', jsonb_build_object('blocks_in_maintenance', 1)), 'a maintenance block after a cut goes back to the fat-loss goal');
select is(pg_temp.rv(14)->'phase_proposal', 'null'::jsonb, 'a maintenance intention never gets a back-to-goal proposal');
select is(pg_temp.rv(15)->'phase_proposal', 'null'::jsonb, 'the first block of a plain gain proposes nothing');
select is(pg_temp.rv(16)->'phase_proposal'->>'reason', 'cut_length', 'when two rules fire the first one wins');

-- ---------------------------------------------------------------------------
-- A reached outcome and the ended block change nothing by themselves (user 17: week 2; user 18: after the last day)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(17);
create temp table s17 as select pg_temp.snap(17) as s;
grant select on s17 to authenticated;
select pg_temp.as_user(17);
select public.open_coach_block_review(pg_temp.d0());
select public.evaluate_coach_calorie_change(pg_temp.d0());
select public.evaluate_coach_deload(pg_temp.d0());
select public.apply_coach_diet_break(pg_temp.d0());
select is(pg_temp.snap(17), (select s from s17), 'a reached weight outcome in week 2 changes goals, plan, block and changes not at all');

-- ---------------------------------------------------------------------------
-- Guards after the last day (18) against the control inside the block (19)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(18);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'block_ended', 'no calorie proposal after the last day');
select is(public.apply_coach_progression((select id from public.workout_sessions where user_id = pg_temp.u(18))), '[]'::jsonb,
  'a session after the last day writes no progression');
select is((select count(*)::int from public.coach_changes where user_id = pg_temp.u(18)), 0, 'and creates no change row');
select is(public.evaluate_coach_deload(pg_temp.d0())->>'status', 'none', 'no deload is applied after the last day');
select throws_ok($$select public.request_coach_deload()$$, 'P0001', 'coach_deload_no_next_week', 'no deload can be requested after the last day');
select is((select count(*)::int from public.coach_changes where user_id = pg_temp.u(18)), 0, 'no deload row exists');
select is(public.apply_coach_diet_break(pg_temp.d0())->>'applied', 'false', 'the diet break writes nothing after the last day');
select is((select target_kcal from public.nutrition_goals where user_id = pg_temp.u(18)), 3000, 'the goal is not reverted to the base figures');

select pg_temp.as_user(19);
select isnt(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'block_ended', 'inside the block the calorie evaluation is not blocked');
select is(jsonb_array_length(public.apply_coach_progression((select id from public.workout_sessions where user_id = pg_temp.u(19)))), 1,
  'inside the block a session still writes its change (the no-range flag)');
select is(public.apply_coach_diet_break(pg_temp.d0())->>'applied', 'true', 'inside the block the diet break still applies');

-- ---------------------------------------------------------------------------
-- Decisions
-- ---------------------------------------------------------------------------
select pg_temp.as_user(7);
select is(public.keep_coach_phase(pg_temp.review_of(7))->>'phase_decision', 'kept', 'keeping stores kept');
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(7) and status = 'proposed'), 0, 'keeping builds no plan');
select throws_ok(format($f$select public.keep_coach_phase(%L)$f$, pg_temp.review_of(7)), '55000', null, 'a second decision is refused');
select throws_ok(format($f$select public.accept_coach_phase(%L, %L, '[]', '{}', '{}')$f$, pg_temp.review_of(7), pg_temp.nut(2500, 0, 0, 'maintenance')),
  '55000', null, 'accepting after keeping is refused');
select pg_temp.as_user(1);
select throws_ok(format($f$select public.keep_coach_phase(%L)$f$, pg_temp.review_of(1)), '55000', null, 'a review without a proposal cannot be decided');
select throws_ok(format($f$select public.accept_coach_phase(%L, %L, '[]', '{}', '{}')$f$, pg_temp.review_of(1), pg_temp.nut(2500, 0, 0, 'maintenance')),
  '55000', null, 'nor accepted');

select pg_temp.as_user(21);
create temp table rev21 as select (pg_temp.rv(21)->>'id')::uuid as id;
grant select on rev21 to authenticated;
select throws_ok(format($f$select public.accept_coach_phase(%L, %L, '[]', '{}', '{}')$f$, (select id from rev21), pg_temp.nut(2500, -500, 0.568, 'cut')),
  '22023', null, 'a nutrition whose phase differs from the proposal is refused');

select pg_temp.as_user(20);
create temp table sn20 as select pg_temp.snap(20) as s;
grant select on sn20 to authenticated;
select pg_temp.rv(20);
create temp table ac20 as
  select public.accept_coach_phase(pg_temp.review_of(20), pg_temp.nut(2500, 0, 0, 'maintenance'), '[]', '{}', '{}') as r;
grant select on ac20 to authenticated;
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(20) and status = 'proposed'), 1,
  'accepting creates exactly one proposed plan (the older proposal is superseded)');
select is((select phase::text from public.coach_plans where id = (select (r->>'plan_id')::uuid from ac20)), 'maintenance',
  'with the proposal''s phase');
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(20) and status = 'superseded' and block_start = pg_temp.d0() - 2),
  1, 'the older proposed plan is superseded');
select is((select status from public.coach_plans where id = pg_temp.plan_of(20)), 'accepted', 'the current plan stays accepted');
select is((select target_kcal from public.nutrition_goals where user_id = pg_temp.u(20)), 2500, 'nutrition_goals is untouched');
select is((select next_plan_id from public.coach_block_reviews where id = pg_temp.review_of(20)), (select (r->>'plan_id')::uuid from ac20),
  'the review keeps the new plan');
select is((select phase_decision from public.coach_block_reviews where id = pg_temp.review_of(20)), 'accepted', 'and the decision');
select throws_ok(format($f$select public.accept_coach_phase(%L, %L, '[]', '{}', '{}')$f$, pg_temp.review_of(20), pg_temp.nut(2500, 0, 0, 'maintenance')),
  '55000', null, 'a second accept is refused');

-- After "Seguir como estoy" the proposal is offered again at the next review (a new block, the same data).
reset role;
update public.coach_plans set status = 'superseded' where id = pg_temp.plan_of(7);
select pg_temp.mk(7, 'accepted', pg_temp.d0() - 28, 'd');
set local role authenticated;
select is(pg_temp.rv(7)->'phase_proposal'->>'reason', 'cut_length', 'the next review offers the proposal again');

-- ---------------------------------------------------------------------------
-- RLS and cascade
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is((select count(*)::int from public.coach_block_reviews), 0, 'user B does not see other users'' reviews');
select throws_ok(format($f$select public.keep_coach_phase(%L)$f$, (select id from rev21)),
  'P0002', null, 'user B cannot keep A''s review');
select throws_ok(format($f$select public.accept_coach_phase(%L, %L, '[]', '{}', '{}')$f$, (select id from rev21), pg_temp.nut(2500, 0, 0, 'maintenance')),
  'P0002', null, 'user B cannot accept A''s review');
select pg_temp.as_user(21);
select throws_ok($$insert into public.coach_block_reviews (plan_id, ended_on, inputs) values (gen_random_uuid(), current_date, '{}')$$,
  '42501', null, 'direct insert is denied');
select throws_ok($$update public.coach_block_reviews set phase_decision = 'kept'$$, '42501', null, 'direct update is denied');
select throws_ok($$delete from public.coach_block_reviews$$, '42501', null, 'direct delete is denied');
reset role;
select throws_ok(format($f$update public.coach_block_reviews set phase_decision = 'kept' where id = %L$f$, (select id from rev21)),
  '23514', null, 'a decision needs a decided_at');
select throws_ok(format($f$update public.coach_block_reviews set phase_decision = 'kept', decided_at = now(), phase_proposal = null where id = %L$f$,
  pg_temp.review_of(8)), '23514', null, 'a decision needs a proposal');

delete from public.coach_plans where id = pg_temp.plan_of(8);
select is((select count(*)::int from public.coach_block_reviews where user_id = pg_temp.u(8)), 0, 'deleting the plan removes its review');

select * from finish();
rollback;
