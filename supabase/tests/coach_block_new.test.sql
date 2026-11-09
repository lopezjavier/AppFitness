-- pgTAP: "Bloque nuevo" and goal switching, backend (T-239, US-110, US-111; rules in planning/04 sections 8.5, 8.9, 8.10,
-- 9.15-9.17 and planning/07 section 12.9). Fixtures are written directly (as coach_block_review.test.sql does) except
-- where a function under test builds the rows.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(85);

-- ---------------------------------------------------------------------------
-- Fixture helpers
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('b9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.sq() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'squat' order by key limit 1 $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;
create function pg_temp.press() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'horizontal_push'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key limit 1 $$;

-- Persisted nutrition JSON at 80 kg, floor 1500 (as coach_block_review.test.sql).
create function pg_temp.nut(target int, adj int, rate numeric, ph text default 'cut', db boolean default false)
returns jsonb language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', target - adj, 'expenditure_source', 'formula',
    'rate_pct_week', rate, 'rate_kg_week', adj * 7 / 7700.0, 'target_rate_pct', 0.5, 'limited_by', null, 'floor_kcal', 1500,
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

-- A block with one session per week and a saved folder; the phase column mirrors the nutrition.
create function pg_temp.mk(u int, p_status text, p_start date, p_tag text, p_intention text default 'fat_loss',
                           p_nut jsonb default null, p_weeks int default 4, p_db boolean default false) returns uuid
language plpgsql as $$
declare
  v_plan uuid := gen_random_uuid();
  v_folder uuid;
  v_r uuid;
  v_n jsonb := coalesce(p_nut, pg_temp.nut(2500, -500, 0.568, 'cut', p_db));
  w int;
begin
  insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque ' || p_tag) returning id into v_folder;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id, phase, phase_options)
  values (v_plan, pg_temp.u(u), p_start, p_intention::public.training_intention, v_n, p_status, v_folder,
          (v_n->>'phase')::public.coach_phase, jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', p_db));
  insert into public.routines (user_id, name, folder_id) values (pg_temp.u(u), 'R' || u || p_tag, v_folder) returning id into v_r;
  for w in 1..p_weeks loop
    insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
    values (v_plan, pg_temp.u(u), w, 1, 'Sesion',
            jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.sq(), 'role', 'main', 'sets', 3)), v_r);
  end loop;
  -- T-242: plan weeks are the windows with a workout. The user trained in every window of the block so far
  -- (routine-less workouts), so the calendar numbering of the fixtures holds.
  insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
  select pg_temp.u(u), 'Filler', 'completed', (p_start + 7 * k + 1 + make_interval(hours => 10)) at time zone 'utc',
         (p_start + 7 * k + 1 + make_interval(hours => 11)) at time zone 'utc'
  from generate_series(0, greatest((pg_temp.d0() - p_start) / 7, 0) - 1) k;
  return v_plan;
end $$;

create function pg_temp.hist(u int, p_day date) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid(); s timestamptz := (p_day + make_interval(hours => 10)) at time zone 'utc';
begin
  insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), 'H', 'completed', s, s + interval '1 hour');
  return v;
end $$;

create function pg_temp.goal(u int, p_kcal int, p_adj int, p_strategy text) returns void language plpgsql as $$
begin
  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (pg_temp.u(u), p_strategy::public.goal_strategy, p_adj, p_kcal, 33, 42, 25);
  perform set_config('app.coach_write', 'off', true);
end $$;

create function pg_temp.pref(u int, p_intention text) returns void language sql as $$
  insert into public.training_preferences (user_id, place, equipment, intention)
  values (pg_temp.u(u), 'gym', '{}', p_intention::public.training_intention) $$;

create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;
create function pg_temp.proposed_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'proposed' $$;
create function pg_temp.snap(n int) returns text language sql as $$
  select md5(
    coalesce((select jsonb_agg(to_jsonb(g) order by g.user_id) from public.nutrition_goals g where g.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(p) - 'updated_at' order by p.id) from public.coach_plans p where p.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(s) order by s.id) from public.coach_plan_sessions s where s.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(f) order by f.id) from public.routine_folders f where f.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(r) order by r.id) from public.coach_block_reviews r where r.user_id = pg_temp.u(n))::text, '')) $$;
create function pg_temp.deficit(n int) returns text language plpgsql as $$
begin
  perform pg_temp.as_user(n);
  return (public.coach_phase_history(pg_temp.d0()))->>'deficit_weeks_done';
end $$;

-- The builder payloads of save_built_coach_block.test.sql.
create function pg_temp.lift(p_id uuid, p_role text, p_extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'role', p_role, 'sets', 3,
    'rep_min', 8, 'rep_max', 12, 'rest_seconds', case when p_role = 'main' then 120 else 90 end,
    'target_rpe', 8) || p_extra $$;
create function pg_temp.tr(p_extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('days_per_week', 1, 'block_weeks', 4, 'intention', 'muscle', 'equipment', '[]'::jsonb,
    'session_minutes', 60, 'source', 'builder') || p_extra $$;
create function pg_temp.sess(p_exercises jsonb) returns jsonb language sql as $$
  select jsonb_build_array(jsonb_build_object('name', 'Cuerpo completo A', 'focus', jsonb_build_array('chest'),
    'exercises', p_exercises)) $$;
create function pg_temp.std() returns jsonb language sql as $$
  select jsonb_build_array(
    pg_temp.lift(pg_temp.press(), 'main', '{"planned_weight_kg":62.5,"superset_group":1}'),
    pg_temp.lift(pg_temp.k('seated-cable-row'), 'main', '{"superset_group":1}'),
    pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation'),
    pg_temp.lift(pg_temp.k('leg-extension'), 'isolation')) $$;

-- The goal-switch call the Edge function makes: a muscle gain from a cut.
create function pg_temp.switch(u int) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(u);
  return public.propose_goal_switch(pg_temp.d0(), pg_temp.nut(2900, 400, 0.4, 'gain'), '[]', '{"model":null}', '{}');
end $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data (owner)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'blocknew' || n || '@example.com' from generate_series(1, 30) n;
update public.profiles
set sex = 'male', birth_date = pg_temp.d0() - interval '30 years', height_cm = 178, onboarding_completed = true
where id in (select pg_temp.u(n) from generate_series(1, 30) n);

-- 1: week 2 of a 4-week cut; the saved goal is "muscle" (changed). Week 1 and week 3 sessions are done.
select pg_temp.mk(1, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(1, 'muscle');
select pg_temp.goal(1, 2500, -500, 'deficit');
update public.coach_plan_sessions set session_id = pg_temp.hist(1, pg_temp.d0() - 7) where plan_id = pg_temp.plan_of(1) and week = 1;
update public.coach_plan_sessions set session_id = pg_temp.hist(1, pg_temp.d0() - 1) where plan_id = pg_temp.plan_of(1) and week = 3;
-- 2: week 2, the saved goal equals the plan's; a goal-switch proposal from an earlier pick is still around.
select pg_temp.mk(2, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(2, 'fat_loss');
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, method)
values ('b9000000-0000-4000-8000-0000000002f1', pg_temp.u(2), pg_temp.d0() - 3, 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'),
        'proposed', '{"source":"goal_switch"}');
insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
values (pg_temp.u(2), 'b9000000-0000-4000-8000-0000000002f1', 'plan', '{}', '{}');
-- 3: an accepted plan with no saved block. 4: a finished block (the stored review path), saved goal "muscle".
select pg_temp.mk(4, 'accepted', pg_temp.d0() - 28, 'a');
select pg_temp.pref(4, 'muscle');
insert into public.coach_plans (user_id, block_start, intention, nutrition, status, phase)
values (pg_temp.u(3), pg_temp.d0() - 8, 'fat_loss', pg_temp.nut(2500, -500, 0.568), 'accepted', 'cut');
-- 5: week 2, goal changed, for propose_goal_switch. 6: three cut blocks that ended, for accept_coach_phase.
select pg_temp.mk(5, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(5, 'muscle');
select pg_temp.goal(5, 2500, -500, 'deficit');
select pg_temp.mk(6, 'superseded', pg_temp.d0() - 84, 'a');
select pg_temp.mk(6, 'superseded', pg_temp.d0() - 56, 'b');
select pg_temp.mk(6, 'accepted', pg_temp.d0() - 28, 'c');
select pg_temp.goal(6, 2500, -500, 'deficit');
-- 7 and 8: week 2 cut, goal changed, for the save (7 saves, 8 fails). 9: the save without a goal change.
select pg_temp.mk(7, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(7, 'muscle');
select pg_temp.goal(7, 2500, -500, 'deficit');
select pg_temp.mk(8, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(8, 'muscle');
select pg_temp.goal(8, 2500, -500, 'deficit');
select pg_temp.mk(9, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(9, 'fat_loss');
select pg_temp.goal(9, 2500, -500, 'deficit');
-- 10: two "Ahora no" and an applied change on the current plan. 11: an applied change on the current plan.
-- 12: an applied change on an earlier plan.
select pg_temp.mk(10, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.pref(10, 'fat_loss');
select pg_temp.mk(11, 'accepted', pg_temp.d0() - 8, 'a');
select pg_temp.mk(12, 'superseded', pg_temp.d0() - 40, 'a');
select pg_temp.mk(12, 'accepted', pg_temp.d0() - 8, 'b');
insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary, status, decided_at)
select pg_temp.u(10), pg_temp.plan_of(10), 'calorie_change', '{"change_kcal":-100}', 'rate_outside_interval', '{}', s, now()
from unnest(array['discarded', 'discarded', 'accepted']) s;
insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary, status, decided_at)
values (pg_temp.u(11), pg_temp.plan_of(11), 'calorie_change', '{"change_kcal":-100}', 'rate_outside_interval', '{}', 'accepted', now()),
       (pg_temp.u(12), (select id from public.coach_plans where user_id = pg_temp.u(12) and status = 'superseded'),
        'calorie_change', '{"change_kcal":-100}', 'rate_outside_interval', '{}', 'accepted', now());
-- 13 to 18: the deficit-week run. 13: cut(4), gain(4), cut: restarts. 14: cut(4), gain(2), cut: pauses.
-- 15: cut(4) with the diet break, gain(3), cut: restarts. 16: as 15 without the diet break: pauses.
select pg_temp.mk(13, 'superseded', pg_temp.d0() - 56, 'a');
select pg_temp.mk(13, 'superseded', pg_temp.d0() - 28, 'b', 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'));
select pg_temp.mk(13, 'accepted', pg_temp.d0(), 'c');
select pg_temp.mk(14, 'superseded', pg_temp.d0() - 42, 'a');
select pg_temp.mk(14, 'superseded', pg_temp.d0() - 14, 'b', 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'), 2);
select pg_temp.mk(14, 'accepted', pg_temp.d0(), 'c');
select pg_temp.mk(15, 'superseded', pg_temp.d0() - 49, 'a', 'fat_loss', null, 4, true);
select pg_temp.mk(15, 'superseded', pg_temp.d0() - 21, 'b', 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'), 3);
select pg_temp.mk(15, 'accepted', pg_temp.d0(), 'c');
select pg_temp.mk(16, 'superseded', pg_temp.d0() - 49, 'a');
select pg_temp.mk(16, 'superseded', pg_temp.d0() - 21, 'b', 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'), 3);
select pg_temp.mk(16, 'accepted', pg_temp.d0(), 'c');
-- 17: weeks in phase, a block ended early (4 planned weeks, 2 lived) followed by the same phase.
select pg_temp.mk(17, 'superseded', pg_temp.d0() - 20, 'a');
select pg_temp.mk(17, 'accepted', pg_temp.d0() - 6, 'b');
-- 18: two consecutive cut blocks. 19: a cut then a gain (only the gain counts). 20: one block in week 3.
select pg_temp.mk(18, 'superseded', pg_temp.d0() - 35, 'a');
select pg_temp.mk(18, 'accepted', pg_temp.d0() - 7, 'b');
select pg_temp.mk(19, 'superseded', pg_temp.d0() - 56, 'a');
select pg_temp.mk(19, 'accepted', pg_temp.d0() - 28, 'b', 'muscle', pg_temp.nut(2900, 400, 0.4, 'gain'));
select pg_temp.mk(20, 'accepted', pg_temp.d0() - 14, 'a');

-- ---------------------------------------------------------------------------
-- Structure and privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.open_coach_block_new(date)', 'execute'), 'anon cannot open Bloque nuevo');
select ok(not has_function_privilege('anon', 'public.propose_goal_switch(date,jsonb,jsonb,jsonb,jsonb)', 'execute'),
  'anon cannot propose a goal switch');
select ok(not has_function_privilege('anon', 'public.coach_weeks_in_phase(date)', 'execute'), 'anon cannot read the weeks in phase');
select ok(not has_function_privilege('anon', 'public.coach_block_counted_weeks(uuid,date)', 'execute'),
  'anon cannot read the counted weeks');
select ok(has_function_privilege('authenticated', 'public.open_coach_block_new(date)', 'execute'),
  'a signed-in user can open Bloque nuevo');
select ok(not has_function_privilege('authenticated', 'public.coach_block_review_build(uuid,date,date)', 'execute'),
  'the review builder stays internal');

set local role authenticated;
select throws_ok($$select public.open_coach_block_new(current_date)$$, '28000', null, 'opening needs a signed-in user');

-- ---------------------------------------------------------------------------
-- The early review (user 1): computed on every call, nothing is written
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
create temp table pre1 as select pg_temp.snap(1) as s;
grant select on pre1 to authenticated;
create temp table e1 as select public.open_coach_block_new(pg_temp.d0()) as r;
grant select on e1 to authenticated;
select is((select r->'review'->>'early' from e1), 'true', 'a block opened in its week 2 returns the early review');
select is((select r->'review'->>'id' is null from e1), true, 'the early review has no stored id');
select is((select r->'review'->>'ended_on' from e1), pg_temp.d0()::text, 'it ends today');
select is((select r->'review'->'phase_proposal' from e1), 'null'::jsonb, 'an early review proposes no phase change');
select is((select r->'review'->>'plan_id' from e1), pg_temp.plan_of(1)::text, 'it belongs to the accepted plan');
select is((select (r->'review'->'inputs'->'adherence'->>'completed') || '/' || (r->'review'->'inputs'->'adherence'->>'planned')
           from e1), '1/2', 'adherence counts only the plan weeks started (week 3''s session is out)');
select is((select r->'review'->'inputs'->'adherence'->>'pct' from e1), '50', 'and its percentage');
select ok((select r->'review'->'inputs' ?& array['adherence', 'lifts', 'groups', 'weight', 'outcomes', 'waist', 'stalls'] from e1),
  'the early review has the same inputs as the end-of-block review');
select is(pg_temp.snap(1), (select s from pre1), 'opening writes nothing (goals, plans, sessions, folders, reviews)');
select is(public.open_coach_block_new(pg_temp.d0()), (select r from e1), 'a second open returns the same facts');
select is((select count(*)::int from public.coach_block_reviews where user_id = pg_temp.u(1)), 0,
  'and no review row is stored, so repeated opens cannot stack');
select is((select r->'goal' from e1),
  jsonb_build_object('changed', true, 'plan_intention', 'fat_loss', 'saved_intention', 'muscle', 'current_kcal', 2500),
  'the goal block compares the plan''s intention with the saved one and shows the current calories');

-- ---------------------------------------------------------------------------
-- No goal change (user 2): a stale goal-switch proposal is dropped. No block (users 3, 29): null.
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is(public.open_coach_block_new(pg_temp.d0())->'goal'->>'changed', 'false', 'the same intention is not a change');
select is((select status from public.coach_plans where id = 'b9000000-0000-4000-8000-0000000002f1'), 'superseded',
  'reverting the choice supersedes the old goal-switch proposal');
select is((select status from public.coach_recommendations where plan_id = 'b9000000-0000-4000-8000-0000000002f1'), 'discarded',
  'and discards its pending recommendation');
select is((select status from public.coach_plans where id = pg_temp.plan_of(2)), 'accepted', 'while the accepted plan stays');
select pg_temp.as_user(3);
select is(public.open_coach_block_new(pg_temp.d0()), null, 'an accepted plan without a saved block returns null');
select pg_temp.as_user(29);
select is(public.open_coach_block_new(pg_temp.d0()), null, 'a user without a plan gets null');
select is(public.coach_weeks_in_phase(pg_temp.d0()), null, 'and no weeks in phase');

-- After the block ends it is the stored review (user 4).
select pg_temp.as_user(4);
create temp table s4 as select public.open_coach_block_new(pg_temp.d0()) as r;
grant select on s4 to authenticated;
select is((select r->'review'->>'early' from s4), 'false', 'after the block end the review is the stored one');
select ok((select (r->'review'->>'id') is not null from s4), 'with its id');
select is((select count(*)::int from public.coach_block_reviews where user_id = pg_temp.u(4)), 1, 'it is stored once');
select is((select r->'review'->>'id' from s4), public.open_coach_block_new(pg_temp.d0())->'review'->>'id',
  'and a second open returns the same review');
select is(public.open_coach_block_new(pg_temp.d0())->'goal'->>'saved_intention', 'muscle', 'the goal block is also there');

-- ---------------------------------------------------------------------------
-- propose_goal_switch (user 5)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(5);
create temp table pre5 as select md5(
  (select to_jsonb(g)::text from public.nutrition_goals g where g.user_id = pg_temp.u(5)) ||
  (select to_jsonb(p)::text from public.coach_plans p where p.id = pg_temp.plan_of(5))) as s;
grant select on pre5 to authenticated;
create temp table sw5 as select pg_temp.switch(5) as r;
grant select on sw5 to authenticated;
select is((select r->>'new_kcal' || '/' || (r->>'current_kcal') || '/' || (r->>'phase') from sw5), '2900/2500/gain',
  'the switch answers the calories before and after and the phase');
select is((select p.status || '/' || p.intention || '/' || (p.method->>'source') || '/' || p.phase
           from public.coach_plans p where p.id = (select (r->>'plan_id')::uuid from sw5)),
  'proposed/muscle/goal_switch/gain', 'one proposed plan with the saved intention, tagged goal_switch');
select is((select md5((select to_jsonb(g)::text from public.nutrition_goals g where g.user_id = pg_temp.u(5)) ||
                      (select to_jsonb(p)::text from public.coach_plans p where p.id = pg_temp.plan_of(5)))),
  (select s from pre5), 'nutrition_goals and the accepted plan are untouched');
create temp table sw5b as select pg_temp.switch(5) as r;
grant select on sw5b to authenticated;
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(5) and status = 'proposed'), 1,
  'a second preview leaves a single proposal');
select is((select status from public.coach_plans where id = (select (r->>'plan_id')::uuid from sw5)), 'superseded',
  'and supersedes the first one');
select is((select count(*)::int from public.coach_recommendations where plan_id = (select (r->>'plan_id')::uuid from sw5b)
           and status = 'pending'), 1, 'with one pending recommendation for the new proposal');
select pg_temp.as_user(2);
select throws_ok($$select pg_temp.switch(2)$$, '22023', 'no goal change', 'the same intention is not a goal switch');
select pg_temp.as_user(3);
select throws_ok($$select pg_temp.switch(3)$$, '55000', 'no saved block', 'a goal switch needs a saved block');
select pg_temp.as_user(5);
select is(public.open_coach_block_new(pg_temp.d0())->'goal'->>'changed', 'true', 'the goal is still changed for the opener');

-- ---------------------------------------------------------------------------
-- "Crear plan nuevo" is retired while a block exists (04 section 9.15)
-- ---------------------------------------------------------------------------
select throws_ok($$select public.propose_coach_plan(current_date, 'fat_loss', '{}', '[]', '{}', '{}')$$, '55000', 'coach_block_in_progress',
  'proposing a plan directly during a saved block is refused');
select pg_temp.as_user(3);
select lives_ok(
  $$select public.propose_coach_plan(current_date, 'maintenance', (select pg_temp.nut(2500, 0, 0, 'maintenance')), '[]', '{}', '{}')$$,
  'a plan without a saved block can still be created');
select pg_temp.as_user(6);
create temp table rv6 as select public.open_coach_block_review(pg_temp.d0()) as r;
grant select on rv6 to authenticated;
select lives_ok(format($f$select public.accept_coach_phase(%L, %L, '[]', '{}', '{}')$f$, (select r->>'id' from rv6),
  pg_temp.nut(2500, 0, 0, 'maintenance')), 'the end-of-block "Aceptar" still builds its plan');
select throws_ok($$select public.propose_coach_plan(current_date, 'fat_loss', '{}', '[]', '{}', '{}')$$, '55000', 'coach_block_in_progress',
  'and the permission does not outlive that call');

-- ---------------------------------------------------------------------------
-- The save (users 7, 8, 9)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(7);
select public.confirm_derived_values(pg_temp.d0());
create temp table sw7 as select pg_temp.switch(7) as r;
grant select on sw7 to authenticated;
create temp table old7 as select pg_temp.plan_of(7) as id;
grant select on old7 to authenticated;
create temp table sv7 as
  select public.save_built_coach_block((select (r->>'plan_id')::uuid from sw7), pg_temp.tr(), pg_temp.sess(pg_temp.std()), null,
                                       (select id from old7)) as r;
grant select on sv7 to authenticated;
select is((select status from public.coach_plans where id = (select id from old7)), 'superseded', 'saving ends the old block');
select is((select status from public.coach_plans where id = (select (r->>'plan_id')::uuid from sv7)), 'accepted',
  'and accepts the goal-switch plan');
select is((select count(*)::int from public.coach_plans where user_id = pg_temp.u(7)), 2,
  'the cut goes to the gain in one step: no intermediate plan');
select is((select target_kcal from public.nutrition_goals where user_id = pg_temp.u(7)), 2900,
  'nutrition_goals take the plan''s calories (maintenance + the gain rate)');
select is((select block_start || '/' || training_start from public.coach_plans where id = (select (r->>'plan_id')::uuid from sv7)),
  pg_temp.d0() || '/' || pg_temp.d0(), 'the new block starts on the save day');
select is(public.coach_plan_week((select (r->>'plan_id')::uuid from sv7), pg_temp.d0()), 1, 'its week 1 is today');
select is((select folder_id is not null from public.coach_plans where id = (select (r->>'plan_id')::uuid from sv7)), true,
  'the saved folder belongs to the new plan');
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase":"gain","weeks":1}'::jsonb,
  'the weeks in phase restart with the new phase');
select is(public.open_coach_block_new(pg_temp.d0())->'goal'->>'changed', 'false', 'after the save the goal is no longer "changed"');

-- A failed save changes nothing (one transaction).
select pg_temp.as_user(8);
select public.confirm_derived_values(pg_temp.d0());
create temp table sw8 as select pg_temp.switch(8) as r;
grant select on sw8 to authenticated;
create temp table old8 as select pg_temp.plan_of(8) as id, pg_temp.snap(8) as s;
grant select on old8 to authenticated;
select throws_ok(format($f$select public.save_built_coach_block(%L, %L, %L, null, %L)$f$, (select r->>'plan_id' from sw8),
  pg_temp.tr('{"equipment":["barbell"]}'), pg_temp.sess(jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main'))), (select id from old8)),
  '22023', 'block invariant: h6_equipment', 'an invalid builder payload is refused');
select is(pg_temp.snap(8), (select s from old8), 'and the old block, the plans and the goals stay as they were');
select is((select status from public.coach_plans where id = (select (r->>'plan_id')::uuid from sw8)), 'proposed',
  'the goal-switch plan is still only proposed');

-- No goal change: the same plan is passed twice, nutrition stays.
select pg_temp.as_user(9);
select public.confirm_derived_values(pg_temp.d0());
create temp table old9 as select pg_temp.plan_of(9) as id;
grant select on old9 to authenticated;
create temp table sv9 as
  select public.save_built_coach_block((select id from old9), pg_temp.tr(), pg_temp.sess(pg_temp.std()), null, (select id from old9)) as r;
grant select on sv9 to authenticated;
select is((select p.nutrition = o.nutrition from public.coach_plans p, public.coach_plans o
           where p.id = (select (r->>'plan_id')::uuid from sv9) and o.id = (select id from old9)), true,
  'a new block without a goal change keeps the nutrition');
select is((select target_kcal from public.nutrition_goals where user_id = pg_temp.u(9)), 2500, 'and the goals');
select is((select status from public.coach_plans where id = (select id from old9)), 'superseded', 'while the old block ends');

-- ---------------------------------------------------------------------------
-- Counters restart with the block (US-111)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'declined_twice', 'two "Ahora no" stop the proposals of the block');
select public.confirm_derived_values(pg_temp.d0());
create temp table sv10 as
  select public.save_built_coach_block(pg_temp.plan_of(10), pg_temp.tr(), pg_temp.sess(pg_temp.std()), null, pg_temp.plan_of(10)) as r;
grant select on sv10 to authenticated;
select is((select count(*)::int from public.coach_recommendations
           where plan_id = (select (r->>'plan_id')::uuid from sv10) and kind = 'calorie_change'), 0,
  'the new plan has no calorie decisions');
select isnt(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'declined_twice',
  'the two declines of the old plan do not count in the new block');
select isnt(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'recent_change',
  'and the applied change of the old plan does not pause the new block');
select pg_temp.as_user(11);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'recent_change',
  'a decision made on the current plan still pauses it for 14 days');
select pg_temp.as_user(12);
select isnt(public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason', 'recent_change',
  'a decision made on the previous plan does not pause the current one');

-- ---------------------------------------------------------------------------
-- The deficit-week run (07 section 12.9 rule 4)
-- ---------------------------------------------------------------------------
select is(pg_temp.deficit(13), '1', 'cut(4), gain(4 weeks), cut: the count restarts');
select is(pg_temp.deficit(14), '5', 'cut(4), gain(2 weeks), cut: the break only pauses the count (4 + 1)');
select is(pg_temp.deficit(15), '1', 'a cut with the diet-break week followed by 3 gain weeks restarts the count');
select is(pg_temp.deficit(16), '5', 'the same without the diet break only pauses it');
select is(pg_temp.deficit(20), '3', 'a single cut block counts the weeks lived, not four per block (week 3)');
select is(pg_temp.deficit(29), '0', 'no blocks, no deficit weeks');

-- ---------------------------------------------------------------------------
-- Counted weeks and the weeks in phase
-- ---------------------------------------------------------------------------
select pg_temp.as_user(17);
select is(public.coach_block_counted_weeks((select id from public.coach_plans where user_id = pg_temp.u(17) and status = 'superseded'),
  pg_temp.d0()), 2, 'a block ended early counts the weeks started before the next one began (not its 4 planned weeks)');
select is(public.coach_block_counted_weeks(pg_temp.plan_of(17), pg_temp.d0()), 1, 'the current block counts its week today');
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase":"cut","weeks":3}'::jsonb,
  'weeks in phase add an ended-early block and the current one (2 + 1)');
select pg_temp.as_user(18);
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase":"cut","weeks":6}'::jsonb, 'two consecutive blocks of one phase (4 + 2)');
select pg_temp.as_user(19);
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase":"gain","weeks":4}'::jsonb,
  'after a phase switch only the new phase counts');
select pg_temp.as_user(20);
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase":"cut","weeks":3}'::jsonb, 'one block in its week 3 is 3 weeks');
select pg_temp.as_user(1);
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase":"cut","weeks":2}'::jsonb,
  'a block created before this task (no method.source) works too: week 2');

-- ---------------------------------------------------------------------------
-- Isolation between users (user 28 has no data)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(28);
select is(public.open_coach_block_new(pg_temp.d0()), null, 'user B opening Bloque nuevo sees nothing of A''s block');
select is(public.coach_weeks_in_phase(pg_temp.d0()), null, 'nor A''s weeks in phase');
select throws_ok($$select pg_temp.switch(28)$$, '55000', 'no saved block', 'B cannot switch a goal on A''s block');
select is(public.coach_block_counted_weeks((select id from old7), pg_temp.d0()), null, 'B cannot count A''s weeks');
select throws_ok(format($f$select public.save_built_coach_block(%L, %L, %L, null, %L)$f$, (select r->>'plan_id' from sw8),
  pg_temp.tr(), pg_temp.sess(pg_temp.std()), (select id from old8)), 'P0002', 'plan not found',
  'B cannot save into A''s goal-switch plan or end A''s block');
select is((select count(*)::int from public.coach_plans where user_id <> pg_temp.u(28) and status = 'proposed'), 0,
  'B reads none of A''s proposals');
select pg_temp.as_user(8);
select is((select status from public.coach_plans where id = (select (r->>'plan_id')::uuid from sw8)), 'proposed',
  'and A''s proposal is untouched by B');

select * from finish();
rollback;
