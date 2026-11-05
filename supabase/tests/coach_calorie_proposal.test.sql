-- pgTAP: calorie proposals the user approves (T-229, US-99; rules in planning/04 section 8.5, 07 section 4).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(63);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9100000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;

create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

create function pg_temp.profile(n int) returns void language sql as $$
  update public.profiles
  set sex = 'male', birth_date = pg_temp.d0() - interval '26 years 100 days', height_cm = 178,
      activity_level = 'moderate', onboarding_completed = true
  where id = pg_temp.u(n) $$;

-- Diary of constant daily sums on the given day offsets (0 = today).
create function pg_temp.diary(n int, offs int[], kcal numeric) returns void language sql as $$
  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), pg_temp.d0() - i,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000c9', 100, 'Alimento', kcal, 10, 10, 10
  from unnest(offs) i $$;

create function pg_temp.weigh_days(n int, offs int[], kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg, 'manual', ((pg_temp.d0() - i)::timestamp + interval '45 minutes') at time zone 'UTC'
  from unnest(offs) i $$;

create function pg_temp.range_arr(a int, b int) returns int[] language sql as $$
  select coalesce(array_agg(i), '{}') from generate_series(a, b) i $$;

-- One weekly weigh-in on the Monday of the week that started weeks_ago weeks before the current one.
create function pg_temp.weigh_week(n int, weeks_ago int, kg numeric) returns void language sql as $$
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), kg, 'manual',
         ((pg_temp.d0() - (extract(isodow from pg_temp.d0())::int - 1) - 7 * weeks_ago)::timestamp + interval '30 minutes')
           at time zone 'UTC' $$;

create function pg_temp.nut(target int, adj int, rate numeric, floor int, phase text, limited text, edited boolean) returns jsonb
language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', target - adj, 'expenditure_source', 'formula',
    'rate_pct_week', 0.5, 'rate_kg_week', rate, 'target_rate_pct', 0.5, 'limited_by', limited, 'floor_kcal', floor,
    'target_kcal', target, 'calorie_adjustment', adj,
    'strategy', case when adj < 0 then 'deficit' when adj > 0 then 'surplus' else 'maintenance' end,
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', edited, 'calorie_changes', 0,
    'phase', phase, 'phase_reason', 'intention',
    'phase_options', jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', false)) $$;

-- A user with an accepted plan (no saved block), a goal equal to it, weekly weights wts (oldest first), daily
-- weigh-ins over 15 days, and a diary of diary_kcal per day on the offsets doff. The expenditure in use is diary_kcal
-- (flat weight, slope term 0), so the plan's own expenditure is only the label.
create function pg_temp.mk(
  n int, rate numeric,
  target int default 2200, adj int default -250, flr int default 1500,
  diary_kcal numeric default null, phase text default null, doff int[] default null,
  block_off int default 0, wts numeric[] default null, limited text default null, edited boolean default false,
  weigh_offs int[] default null
) returns void language plpgsql as $$
declare
  v_wts numeric[] := coalesce(wts, array_fill(80::numeric, array[8]));
begin
  perform pg_temp.profile(n);
  for i in 1..array_length(v_wts, 1) loop
    perform pg_temp.weigh_week(n, array_length(v_wts, 1) - i, v_wts[i]);
  end loop;
  perform pg_temp.weigh_days(n, coalesce(weigh_offs, pg_temp.range_arr(0, 14)), v_wts[array_length(v_wts, 1)]);
  perform pg_temp.diary(n, coalesce(doff, pg_temp.range_arr(0, 27)), coalesce(diary_kcal, target - adj));
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (pg_temp.u(n), (case when adj < 0 then 'deficit' when adj > 0 then 'surplus' else 'maintenance' end)::public.goal_strategy,
          adj, target, 33, 42, 25);
  insert into public.coach_plans (user_id, block_start, intention, nutrition, status, accepted_at)
  values (pg_temp.u(n), pg_temp.d0() - block_off, 'fat_loss',
          pg_temp.nut(target, adj, rate, flr, coalesce(phase, case when adj < 0 then 'cut' when adj > 0 then 'gain' else 'maintenance' end),
                      limited, edited),
          'accepted', now());
end $$;

-- A calorie_change recommendation of user n, created/decided the given number of days ago.
create function pg_temp.rec(n int, st text, change int, created_ago int, decided_ago int) returns void language sql as $$
  insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary, status, created_at, decided_at)
  select pg_temp.u(n), p.id, 'calorie_change',
         jsonb_build_object('from_kcal', 2200, 'to_kcal', 2200 + change, 'change_kcal', change),
         'rate_outside_interval', '{}', st, now() - make_interval(days => created_ago),
         case when st = 'pending' then null else now() - make_interval(days => decided_ago) end
  from public.coach_plans p where p.user_id = pg_temp.u(n) and p.status = 'accepted' $$;

create function pg_temp.reason(n int) returns text language sql as $$
  select public.evaluate_coach_calorie_change(pg_temp.d0())->>'reason' $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'proposal' || n || '@example.com' from generate_series(1, 45) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000c9', 'off', 'Alimento c9', 100);

-- Weekly gate users (1-5): diary and weigh-in days only.
select pg_temp.profile(n) from generate_series(1, 5) n;
-- 1: 4 complete days this week (diary offsets 0-3), 3 weigh-ins.
select pg_temp.diary(1, pg_temp.range_arr(0, 3) || pg_temp.range_arr(7, 27), 2000);
select pg_temp.weigh_days(1, array[0, 1, 2], 80);
-- 2: 5 complete days, 3 weigh-ins.
select pg_temp.diary(2, pg_temp.range_arr(0, 4) || pg_temp.range_arr(7, 27), 2000);
select pg_temp.weigh_days(2, array[0, 1, 2], 80);
-- 3: 5 complete days, only 2 weigh-ins.
select pg_temp.diary(3, pg_temp.range_arr(0, 4) || pg_temp.range_arr(7, 27), 2000);
select pg_temp.weigh_days(3, array[0, 1], 80);
-- 4: 5 complete days and two 600 kcal days (under 40 % of the median 2 000), 3 weigh-ins.
select pg_temp.diary(4, pg_temp.range_arr(0, 4) || pg_temp.range_arr(7, 27), 2000);
select pg_temp.diary(4, array[5, 6], 600);
select pg_temp.weigh_days(4, array[0, 1, 2], 80);
-- 5: nothing logged.

-- Evaluation users.
select pg_temp.mk(10, -0.4);                                         -- happy path, applied
select pg_temp.mk(11, -0.4);                                         -- happy path, declined
select pg_temp.mk(12, -0.4, doff => pg_temp.range_arr(0, 27));       -- older week with partial days (set below)
select pg_temp.mk(13, -0.4, weigh_offs => array[0, 7, 8, 9, 10, 11]); -- at most 2 weigh-ins this week
select pg_temp.mk(14, -0.4, weigh_offs => array[0, 1, 2, 7, 8, 9, 10]); -- 3 weigh-ins in each week
select pg_temp.mk(15, -0.4, doff => pg_temp.range_arr(0, 3) || pg_temp.range_arr(7, 27)); -- 4 complete days this week
select pg_temp.mk(16, -0.4, doff => pg_temp.range_arr(0, 4) || pg_temp.range_arr(7, 27)); -- 5 complete days this week
select pg_temp.mk(17, -0.04, adj => -400);                           -- minimum size 50
select pg_temp.mk(18, -0.4, target => 2190, adj => -280);            -- 10 % of 2 190 rounded down to 210
select pg_temp.mk(19, -0.4, target => 1520, flr => 1500, adj => -400); -- floor
select pg_temp.mk(20, -0.4, adj => -450);                            -- 500 deficit cap exceeded
select pg_temp.mk(21, -0.4, adj => -280);                            -- exactly 500
select pg_temp.mk(22, -0.4, adj => -100, diary_kcal => 2871);        -- one kcal under the cut range minimum
select pg_temp.mk(23, -0.4, adj => -100, diary_kcal => 2870);        -- exactly the cut range minimum
select pg_temp.mk(24, -0.4, diary_kcal => 2124, wts => array[84, 83.4, 82.8, 82.2, 81.6, 81, 80.4, 79.8]); -- losing fast: raise ends 1 kcal over the cut range maximum
select pg_temp.mk(25, -0.4, diary_kcal => 2125, wts => array[84, 83.4, 82.8, 82.2, 81.6, 81, 80.4, 79.8]); -- exactly the cut range maximum
select pg_temp.mk(26, 0.4, adj => 300, diary_kcal => 1969);          -- one kcal over the gain range maximum
select pg_temp.mk(27, 0.4, adj => 300, diary_kcal => 1970);          -- exactly the gain range maximum
select pg_temp.mk(28, 0, target => 2450, adj => 0, wts => array[78, 78.5, 79, 79.5, 80, 80.5, 81, 81.5]); -- gaining maintenance
select pg_temp.mk(29, -0.4, block_off => 30);                        -- an applied change in the block: band exceeded
select pg_temp.mk(30, -0.4, block_off => 30);                        -- declined and superseded rows only
select pg_temp.mk(31, -0.4, block_off => 30);                        -- two "Ahora no"
select pg_temp.mk(32, -0.4, block_off => 30);                        -- one "Ahora no" 3 days ago
select pg_temp.mk(33, -0.4, block_off => 30);                        -- one "Ahora no" 15 days ago
select pg_temp.mk(34, -0.4, block_off => 30);                        -- a pending proposal from an older week
select pg_temp.mk(35, -0.4, edited => true);                         -- plan edited by the user
select pg_temp.mk(36, -0.4);                                         -- the second user for isolation
select pg_temp.mk(37, -0.4, wts => array[80, 80, 80]);               -- only 3 weeks: previous week has no interval
select pg_temp.profile(38);                                          -- no plan

-- User 12: three partial days in the older week window (offsets 9-11 become 600 kcal).
update public.food_log_entries set kcal = 600 where user_id = pg_temp.u(12) and log_date between pg_temp.d0() - 11 and pg_temp.d0() - 9;
-- User 29: an applied change of -120 earlier in the block (the plan already follows it).
select pg_temp.rec(29, 'accepted', -120, 25, 20);
-- User 30: declined and superseded proposals of -300 earlier in the block.
select pg_temp.rec(30, 'discarded', -300, 25, 20);
select pg_temp.rec(30, 'superseded', -300, 25, 20);
-- User 31: two "Ahora no".
select pg_temp.rec(31, 'discarded', -220, 28, 22);
select pg_temp.rec(31, 'discarded', -220, 25, 20);
select pg_temp.rec(32, 'discarded', -220, 5, 3);
select pg_temp.rec(33, 'discarded', -220, 20, 15);
select pg_temp.rec(34, 'pending', -220, 20, null);

-- ---------------------------------------------------------------------------
-- Structure
-- ---------------------------------------------------------------------------
select hasnt_function('public', 'decide_recommendation', 'the old decide_recommendation (with the edited path) is gone');
select throws_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary, status, decided_at)
    values (pg_temp.u(38), 'calorie_change', '{}', '{}', 'edited', now())$$,
  '23514', null, 'the edited status no longer exists');
select lives_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary, status, decided_at)
    values (pg_temp.u(38), 'calorie_change', '{}', '{}', 'superseded', now())$$,
  'the superseded status exists');
select lives_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary) values (pg_temp.u(38), 'calorie_change', '{}', '{}')$$,
  'one pending calorie proposal is stored');
select throws_ok(
  $$insert into public.coach_recommendations (user_id, kind, payload, input_summary) values (pg_temp.u(38), 'calorie_change', '{}', '{}')$$,
  '23505', null, 'a second pending calorie proposal of the same user is rejected');
delete from public.coach_recommendations where user_id = pg_temp.u(38);

-- ---------------------------------------------------------------------------
-- coach_week_gate
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);
select is(public.coach_week_gate(pg_temp.d0()), '{"ok": false, "complete_days": 4, "weigh_in_days": 3}'::jsonb,
  'four complete diary days are not enough');
select pg_temp.as_user(2);
select is(public.coach_week_gate(pg_temp.d0()), '{"ok": true, "complete_days": 5, "weigh_in_days": 3}'::jsonb,
  'five complete days and three weigh-ins pass');
select pg_temp.as_user(3);
select is((public.coach_week_gate(pg_temp.d0())->>'ok')::boolean, false, 'two weigh-ins are not enough');
select pg_temp.as_user(4);
select is(public.coach_week_gate(pg_temp.d0())->>'complete_days', '5',
  'a 600 kcal day under 40 percent of the median is not a complete day');
select pg_temp.as_user(5);
select is(public.coach_week_gate(pg_temp.d0()), '{"ok": false, "complete_days": 0, "weigh_in_days": 0}'::jsonb,
  'no records at all fail the gate');

-- ---------------------------------------------------------------------------
-- evaluate_coach_calorie_change: data gates and the interval
-- ---------------------------------------------------------------------------
select pg_temp.as_user(38);
select is(pg_temp.reason(38), 'edited', 'no accepted plan means no proposal');
select pg_temp.as_user(35);
select is(pg_temp.reason(35), 'edited', 'a plan edited by the user gets no proposal (Goals I edited)');
select pg_temp.as_user(37);
select is(pg_temp.reason(37), 'inside_interval', 'one week outside the interval is not enough');
select pg_temp.as_user(12);
select is(pg_temp.reason(12), 'missing_records', 'partial days in the older week block the proposal');
select pg_temp.as_user(13);
select is(pg_temp.reason(13), 'missing_records', 'two weigh-ins in a week block the proposal');
select pg_temp.as_user(15);
select is(pg_temp.reason(15), 'missing_records', 'four complete diary days in a week block the proposal');
select pg_temp.as_user(14);
select is(pg_temp.reason(14), 'rate_outside_interval', 'three weigh-ins in each week are enough');
select pg_temp.as_user(16);
select is(pg_temp.reason(16), 'rate_outside_interval', 'five complete diary days in each week are enough');

-- ---------------------------------------------------------------------------
-- Sizing and the safe band
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'created',
  'two weeks outside the interval on clean data create a proposal');
select is(
  (select (payload->>'from_kcal', payload->>'to_kcal', payload->>'change_kcal', payload->>'change_pct')::text
   from public.coach_recommendations where kind = 'calorie_change'),
  '(2200,1980,-220,-10.0)', 'the change is capped at 10 percent (04 section 8.6 g)');
select is(
  (select (payload->>'base_kcal', payload->>'band_used_kcal', payload->>'weeks', payload->>'rate_kg_week')::text
   from public.coach_recommendations where kind = 'calorie_change'),
  '(2200,0,2,-0.4)', 'the payload carries the base, the band used, the weeks and the rate');

select pg_temp.as_user(17);
select public.evaluate_coach_calorie_change(pg_temp.d0());
select is((select payload->>'change_kcal' from public.coach_recommendations where kind = 'calorie_change'), '-50',
  'the minimum change is 50 kcal');
select pg_temp.as_user(18);
select public.evaluate_coach_calorie_change(pg_temp.d0());
select is((select payload->>'change_kcal' from public.coach_recommendations where kind = 'calorie_change'), '-210',
  '10 percent of 2190 is rounded down to 210');

select pg_temp.as_user(19);
select is(pg_temp.reason(19), 'floor', 'a change under the calorie floor is not proposed');
select pg_temp.as_user(20);
select is(pg_temp.reason(20), 'deficit_cap', 'a deficit beyond 500 kcal is not proposed');
select pg_temp.as_user(21);
select is(pg_temp.reason(21), 'rate_outside_interval', 'a deficit of exactly 500 kcal is allowed');
select pg_temp.as_user(22);
select is(pg_temp.reason(22), 'rate_range', 'one kcal under the cut rate range is not proposed');
select pg_temp.as_user(23);
select is(pg_temp.reason(23), 'rate_outside_interval', 'exactly the cut range minimum is allowed (1.0 percent plus rounding)');
select pg_temp.as_user(24);
select is(pg_temp.reason(24), 'rate_range', 'a raise ending one kcal over the cut range maximum is not proposed');
select pg_temp.as_user(25);
select is(pg_temp.reason(25), 'rate_outside_interval', 'a raise ending exactly at the cut range maximum (0.5 percent) is allowed');
select pg_temp.as_user(26);
select is(pg_temp.reason(26), 'rate_range', 'one kcal over the gain range maximum is not proposed');
select pg_temp.as_user(27);
select is(pg_temp.reason(27), 'rate_outside_interval', 'exactly the gain range maximum is allowed');

select pg_temp.as_user(28);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'created',
  'a gaining maintenance user does get a decrease');
select ok((select (payload->>'change_kcal')::int < 0 and (payload->>'to_kcal')::int >= 1500 from public.coach_recommendations
           where kind = 'calorie_change'), 'the maintenance decrease lowers the target and stays above the floor');

select pg_temp.as_user(29);
select is(pg_temp.reason(29), 'band', 'applied changes plus the new one beyond 10 percent of the block calories are not proposed');
select pg_temp.as_user(30);
select is(pg_temp.reason(30), 'rate_outside_interval', 'declined and superseded proposals do not count in the band');

-- ---------------------------------------------------------------------------
-- "Ahora no", the 14-day clock and one proposal at a time
-- ---------------------------------------------------------------------------
select pg_temp.as_user(31);
select is(pg_temp.reason(31), 'declined_twice', 'two "Ahora no" in the block stop the proposals');
select pg_temp.as_user(32);
select is(pg_temp.reason(32), 'recent_change', 'a "Ahora no" 3 days ago restarts the 14-day clock');
select pg_temp.as_user(33);
select is(pg_temp.reason(33), 'rate_outside_interval', 'a "Ahora no" 15 days ago no longer blocks');

select pg_temp.as_user(34);
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'created', 'a new proposal replaces the older pending one');
select is((select count(*)::int from public.coach_recommendations where status = 'pending'), 1, 'exactly one proposal is pending');
select is((select count(*)::int from public.coach_recommendations where status = 'superseded'), 1, 'the older proposal is superseded');
select is(
  public.evaluate_coach_calorie_change(pg_temp.d0()),
  jsonb_build_object('status', 'pending', 'reason', 'already_pending_this_week',
                     'recommendation_id', (select id from public.coach_recommendations where status = 'pending')),
  're-evaluating in the same week returns the same proposal');
select is((select count(*)::int from public.coach_recommendations where kind = 'calorie_change'), 2,
  're-evaluating creates no new row');

-- ---------------------------------------------------------------------------
-- decide_calorie_proposal
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
select public.decide_calorie_proposal((select id from public.coach_recommendations where status = 'pending'), true);
select is(
  (select (target_kcal, calorie_adjustment, strategy, protein_pct, carbs_pct, fat_pct)::text from public.nutrition_goals),
  '(1980,-470,deficit,36,39,25)', 'Aplicar writes the new target and keeps the protein grams and the fat share');
select is(
  (select (nutrition->>'target_kcal', nutrition->>'protein_g', nutrition->>'protein_pct', nutrition->>'calorie_changes',
           nutrition->>'edited_by_user')::text from public.coach_plans where status = 'accepted'),
  '(1980,176,36,1,false)', 'the plan JSON follows the applied change without marking an edit');
select is((select status from public.coach_recommendations where kind = 'calorie_change'), 'accepted',
  'the applied proposal is stored as accepted');
select throws_ok(
  $$select public.decide_calorie_proposal((select id from public.coach_recommendations where kind = 'calorie_change'), false)$$,
  '55000', null, 'a decided proposal cannot be decided again');
select is(pg_temp.reason(10), 'recent_change', 'after applying, the next proposal waits 14 days');

select pg_temp.as_user(11);
select public.evaluate_coach_calorie_change(pg_temp.d0());
select public.decide_calorie_proposal((select id from public.coach_recommendations where status = 'pending'), false);
select is((select status from public.coach_recommendations where kind = 'calorie_change'), 'discarded',
  'Ahora no stores the proposal as discarded');
select is((select target_kcal from public.nutrition_goals), 2200, 'Ahora no leaves the goal untouched');
select is((select (nutrition->>'target_kcal', nutrition->>'calorie_changes')::text from public.coach_plans where status = 'accepted'),
  '(2200,0)', 'Ahora no leaves the plan untouched');
select throws_ok(
  $$select public.decide_calorie_proposal((select id from public.coach_recommendations where kind = 'calorie_change'), true)$$,
  '55000', null, 'a declined proposal cannot be applied afterwards');
select is(pg_temp.reason(11), 'recent_change', 'after Ahora no, the next proposal waits 14 days');

-- ---------------------------------------------------------------------------
-- Isolation and privileges
-- ---------------------------------------------------------------------------
select pg_temp.as_user(36);
select is((select count(*)::int from public.coach_recommendations), 0, 'user B cannot read other users'' proposals');
reset role;
create temp table t_ids as select id from public.coach_recommendations where user_id = pg_temp.u(34) and status = 'pending';
grant select on t_ids to authenticated;
set local role authenticated;
select pg_temp.as_user(36);
select throws_ok(
  $$select public.decide_calorie_proposal((select id from t_ids), true)$$,
  'P0002', null, 'deciding another user''s proposal is not found');
select throws_ok(
  $$update public.coach_recommendations set status = 'accepted'$$,
  '42501', null, 'a user cannot write a recommendation directly');

select set_config('request.jwt.claims', '{}', true);
select throws_ok(
  $$select public.decide_calorie_proposal(gen_random_uuid(), true)$$,
  '28000', null, 'deciding without a session is rejected');
select throws_ok(
  $$select public.evaluate_coach_calorie_change(pg_temp.d0())$$,
  '28000', null, 'evaluating without a session is rejected');
reset role;

select ok(not has_function_privilege('anon', 'public.decide_calorie_proposal(uuid,boolean,date)', 'execute'),
  'anon cannot execute decide_calorie_proposal');
select ok(not has_function_privilege('anon', 'public.evaluate_coach_calorie_change(date)', 'execute'),
  'anon cannot execute evaluate_coach_calorie_change');
select ok(not has_function_privilege('anon', 'public.coach_week_gate(date)', 'execute'), 'anon cannot execute coach_week_gate');
select ok(has_function_privilege('authenticated', 'public.decide_calorie_proposal(uuid,boolean,date)', 'execute'),
  'authenticated can execute decide_calorie_proposal');

select * from finish();
rollback;
