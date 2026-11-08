-- pgTAP: the `nutrition` object of coach_week_summary (T-246, US-112; planning/04 section 6.7, 07 section 4 (a)).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(29);

create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9460000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) $$ security definer;
create function pg_temp.nut() returns jsonb language sql as $$ select (public.coach_week_summary(pg_temp.d0()))->'nutrition' $$;
create function pg_temp.at_day(off int, hh text) returns timestamptz language sql as $$
  select ((pg_temp.d0() + off)::timestamp + hh::interval) at time zone 'UTC' $$;

-- A user with an accepted 4-week block that started start_off days ago, 2 plan sessions a week, a routine-less workout in
-- each window that has begun when with_workouts (otherwise the last week is paused), a diary of 2000 kcal / 100 g protein
-- on the given day offsets (0 = today), a goals row of 2100 kcal / 30 % protein, and one weigh-in of 80 kg 8 days ago.
create function pg_temp.mk(n int, start_off int, with_workouts boolean, diary int[], p_opts jsonb, p_nutrition jsonb)
returns void language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); w int; i int;
begin
  update public.profiles set onboarding_completed = true where id = pg_temp.u(n);
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, phase_options, status, accepted_at)
  values (v_plan, pg_temp.u(n), pg_temp.d0() - start_off, 'fat_loss', p_nutrition, p_opts, 'accepted', now());
  for w in 1..4 loop
    for i in 1..2 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
      values (v_plan, pg_temp.u(n), w, i, 'Sesion ' || i, '[]');
    end loop;
  end loop;
  if with_workouts then
    insert into public.workout_sessions (user_id, name, status, started_at, ended_at)
    select pg_temp.u(n), 'Filler', 'completed', pg_temp.at_day(-start_off + 7 * k + 1, '15 hours'),
           pg_temp.at_day(-start_off + 7 * k + 1, '16 hours')
    from generate_series(0, start_off / 7 - 1) k;
  end if;
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - o,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000e2', 100, 'Alimento', 2000, 100, 10, 10
  from unnest(diary) o;
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  values (pg_temp.u(n), 80, 'manual', pg_temp.at_day(-8, '45 minutes'));
  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (pg_temp.u(n), 'deficit', -300, 2100, 30, 45, 25)
  on conflict (user_id) do update set target_kcal = 2100, protein_pct = 30, carbs_pct = 45, fat_pct = 25;
  perform set_config('app.coach_write', 'off', true);
end $$;

grant execute on all functions in schema pg_temp to authenticated, anon;

insert into auth.users (id, email)
select pg_temp.u(n), 'wnut' || n || '@example.com' from generate_series(1, 8) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000e2', 'off', 'Alimento e2', 100);

-- Block started 10 days ago: week 1 = offsets -10..-4 is the latest finished week.
-- User 1: 5 complete days, goal 2200 before a calorie change accepted 8 days ago (to 2100, the current goal).
select pg_temp.mk(1, 10, true, array[10, 9, 8, 7, 6], '{"cut_rate_pct":0.5,"diet_break":false}',
  '{"rate_kg_week":-0.4,"target_kcal":2100,"protein_pct":30}');
-- User 2: 5 full days plus a partial day (5 % of the median) that must not count.
select pg_temp.mk(2, 10, true, array[10, 9, 8, 7, 6, 5], '{"cut_rate_pct":0.5,"diet_break":false}',
  '{"rate_kg_week":-0.4,"target_kcal":2100,"protein_pct":30}');
update public.food_log_entries set kcal = 100, protein_g = 5 where user_id = pg_temp.u(2) and log_date = pg_temp.d0() - 5;
-- User 3: 4 complete days only.
select pg_temp.mk(3, 10, true, array[10, 9, 8, 7], '{"cut_rate_pct":0.5,"diet_break":false}',
  '{"rate_kg_week":-0.4,"target_kcal":2100,"protein_pct":30}');
-- User 4: paused week (no workout in the window that just ended), diary present.
select pg_temp.mk(4, 10, false, array[10, 9, 8, 7, 6], '{"cut_rate_pct":0.5,"diet_break":false}',
  '{"rate_kg_week":-0.4,"target_kcal":2100,"protein_pct":30}');
-- User 5: week 4 (offsets -10..-4 of a block that started 31 days ago) is a diet-break week.
select pg_temp.mk(5, 31, true, array[10, 9, 8, 7, 6], '{"cut_rate_pct":0.5,"diet_break":true}',
  '{"rate_kg_week":-0.4,"target_kcal":2100,"protein_pct":30,"diet_break":{"target_kcal":1900,"protein_pct":35}}');
-- User 6: another user whose diary and calorie decisions must never enter user 1's numbers.
select pg_temp.mk(6, 10, true, array[10, 9, 8, 7, 6, 5, 4], '{"cut_rate_pct":0.5,"diet_break":false}',
  '{"rate_kg_week":-0.4,"target_kcal":2100,"protein_pct":30}');
update public.food_log_entries set kcal = 5000, protein_g = 300 where user_id = pg_temp.u(6);

insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary, status, created_at, decided_at) values
  -- accepted: created 9 days ago, decided 8 days ago (2200 -> 2100)
  (pg_temp.u(1), pg_temp.plan_of(1), 'calorie_change', '{"change_kcal": -100, "from_kcal": 2200, "to_kcal": 2100}',
   'rate_outside_interval', '{}', 'accepted', pg_temp.at_day(-9, '9 hours'), pg_temp.at_day(-8, '12 hours')),
  -- declined inside the week, created before it
  (pg_temp.u(1), pg_temp.plan_of(1), 'calorie_change', '{"change_kcal": -50, "from_kcal": 2100, "to_kcal": 2050}',
   'rate_outside_interval', '{}', 'discarded', pg_temp.at_day(-12, '9 hours'), pg_temp.at_day(-6, '9 hours')),
  -- pending created in the week
  (pg_temp.u(1), pg_temp.plan_of(1), 'calorie_change', '{"change_kcal": 50, "from_kcal": 2100, "to_kcal": 2150}',
   'rate_outside_interval', '{}', 'pending', pg_temp.at_day(-5, '9 hours'), null),
  -- outside the week both ways
  (pg_temp.u(1), pg_temp.plan_of(1), 'calorie_change', '{"change_kcal": 100, "from_kcal": 2100, "to_kcal": 2200}',
   'rate_outside_interval', '{}', 'accepted', pg_temp.at_day(-20, '9 hours'), pg_temp.at_day(-15, '9 hours')),
  -- another user's decision
  (pg_temp.u(6), pg_temp.plan_of(6), 'calorie_change', '{"change_kcal": -100, "from_kcal": 2200, "to_kcal": 2100}',
   'rate_outside_interval', '{}', 'accepted', pg_temp.at_day(-9, '9 hours'), pg_temp.at_day(-8, '12 hours'));

set local role authenticated;

select pg_temp.as_user(1);
select ok(has_function_privilege('authenticated', 'public.coach_complete_days(uuid, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_complete_days(uuid, date)', 'execute'),
  'coach_complete_days is for authenticated only');
select is((pg_temp.nut()->>'complete_days')::int, 5, '5 complete days');
select is((pg_temp.nut()->>'gate_ok')::boolean, true, '5 complete days open the gate');
select is((pg_temp.nut()->>'avg_kcal')::int, 2000, 'average kcal over the complete days');
select is((pg_temp.nut()->>'avg_protein_g')::numeric, 100.0, 'average protein');
select is((pg_temp.nut()->>'protein_g_per_kg')::numeric, 1.25, 'protein per kg of the week average weight (80 kg)');
select is((pg_temp.nut()->>'target_kcal')::int, 2140,
  'target is the mean of the day goals: 2200 for the 2 days before the accepted change, 2100 after');
select is((pg_temp.nut()->>'target_protein_g')::int, 158, 'protein target: 2100 kcal x 30 % / 4 = 157.5, kept across the change');
select is(jsonb_array_length(pg_temp.nut()->'decisions'), 3,
  'decisions: created or decided inside the week; the one outside both ways is excluded');
select is((select string_agg(e->>'status', ',') from jsonb_array_elements(pg_temp.nut()->'decisions') e),
  'pending,accepted,discarded', 'decisions are newest first with their status');
select is((pg_temp.nut()->'decisions'->1->>'from_kcal')::int || '>' || (pg_temp.nut()->'decisions'->1->>'to_kcal')::int,
  '2200>2100', 'a decision carries from_kcal and to_kcal');
select is(pg_temp.nut()->'decisions'->1->>'reason', 'rate_outside_interval', 'a decision carries its reason');
select is((pg_temp.nut()->'decisions'->1->'payload'->>'to_kcal')::int, 2100, 'a decision carries its payload');
select is(jsonb_typeof(public.coach_week_summary(pg_temp.d0())->'stalls'), 'array', 'stalls are still emitted');
select is((public.coach_week_summary(pg_temp.d0())->>'complete_days')::int, 5, 'legacy complete_days key stays');
select ok(public.coach_week_summary(pg_temp.d0()) ? 'calorie_proposal' and public.coach_week_summary(pg_temp.d0()) ? 'missing_records',
  'legacy keys stay for the installed app');

select pg_temp.as_user(2);
select is((pg_temp.nut()->>'complete_days')::int, 5, 'a partial day (under 40 % of the median) is not a complete day');
select is((pg_temp.nut()->>'avg_kcal')::int, 2000, 'the partial day is left out of the average');
select is((pg_temp.nut()->'decisions'), '[]'::jsonb, 'no decisions: empty array');

select pg_temp.as_user(3);
select is((pg_temp.nut()->>'gate_ok')::boolean, false, '4 complete days: gate off');
select is((pg_temp.nut()->>'complete_days')::int, 4, 'the count is still reported');
select is((select count(*)::int from jsonb_object_keys(pg_temp.nut()) k where k in ('avg_kcal', 'avg_protein_g', 'target_kcal', 'target_protein_g', 'protein_g_per_kg')),
  0, 'gate off: no averages or targets');

select pg_temp.as_user(4);
select is((public.coach_week_summary(pg_temp.d0())->>'paused')::boolean, true, 'user 4 is in a paused week');
select ok(not (public.coach_week_summary(pg_temp.d0()) ? 'nutrition'), 'a paused summary has no nutrition key');

select pg_temp.as_user(5);
select is((pg_temp.nut()->>'target_kcal')::int, 1900, 'diet-break week: the goal in force is the diet-break figure');
select is((pg_temp.nut()->>'target_protein_g')::int, 166, 'diet-break week: protein target from the break figures (1900 x 35 % / 4)');

select pg_temp.as_user(6);
select is((pg_temp.nut()->>'avg_kcal')::int, 5000, 'user 6 sees its own diary');
select pg_temp.as_user(1);
select is((pg_temp.nut()->>'avg_kcal')::int, 2000, 'user 1 numbers are untouched by user 6 diary');
select is(jsonb_array_length(pg_temp.nut()->'decisions'), 3, 'user 6 decision does not enter user 1 decisions');

select * from finish();
rollback;
