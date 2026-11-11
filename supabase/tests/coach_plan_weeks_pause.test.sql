-- pgTAP: a missed week pauses the block (T-242, US-106; rules in planning/04 sections 6.7, 9.9 and 9.10).
-- Plan week n is the n-th 7-day window (from coalesce(training_start, block_start)) that holds a completed workout.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(50);

-- ---------------------------------------------------------------------------
-- Fixture helpers
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c2420000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.sq() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'squat' order by key limit 1 $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

-- Persisted nutrition JSON at 80 kg (as coach_block_review.test.sql), with the diet-break figures when db.
create function pg_temp.nut(target int, adj int, db boolean default false) returns jsonb language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', target - adj, 'expenditure_source', 'formula',
    'rate_pct_week', 0.568, 'rate_kg_week', adj * 7 / 7700.0, 'target_rate_pct', 0.5, 'limited_by', null, 'floor_kcal', 1500,
    'target_kcal', target, 'calorie_adjustment', adj,
    'strategy', case when adj < 0 then 'deficit' when adj > 0 then 'surplus' else 'maintenance' end,
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', false, 'calorie_changes', 0,
    'phase', 'cut', 'phase_reason', 'intention',
    'phase_options', jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', db))
  || case when db then jsonb_build_object('diet_break', jsonb_build_object(
       'target_kcal', target - adj, 'calorie_adjustment', 0, 'strategy', 'maintenance', 'protein_pct', 33,
       'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 198, 'carbs_g', 262, 'fat_g', 83, 'week', 4)) else '{}'::jsonb end $$;

-- A saved block of p_weeks weeks, one session a week (a main squat) of one routine. Returns the plan id.
create function pg_temp.mk(u int, p_status text, p_start date, p_tag text default 'c', p_weeks int default 4,
                           p_db boolean default false, p_training_start date default null) returns uuid
language plpgsql as $$
declare
  v_plan uuid := gen_random_uuid();
  v_folder uuid;
  v_r uuid;
  v_n jsonb := pg_temp.nut(2500, -500, p_db);
  w int;
begin
  insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque ' || p_tag) returning id into v_folder;
  insert into public.coach_plans (id, user_id, block_start, training_start, intention, nutrition, status, folder_id, phase,
                                  phase_options)
  values (v_plan, pg_temp.u(u), p_start, p_training_start, 'fat_loss', v_n, p_status, v_folder, 'cut',
          jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', p_db));
  insert into public.routines (user_id, name, folder_id) values (pg_temp.u(u), 'R' || u || p_tag, v_folder) returning id into v_r;
  for w in 1..p_weeks loop
    insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
    values (v_plan, pg_temp.u(u), w, 1, 'Sesion',
            jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.sq(), 'role', 'main', 'sets', 3)), v_r);
  end loop;
  return v_plan;
end $$;

create function pg_temp.plan_of(n int, p_tag text default 'c') returns uuid language sql as $$
  select id from public.coach_plans p where p.user_id = pg_temp.u(n)
    and p.folder_id = (select f.id from public.routine_folders f where f.user_id = pg_temp.u(n) and f.name = 'Bloque ' || p_tag) $$;
create function pg_temp.routine_of(n int, p_tag text default 'c') returns uuid language sql as $$
  select id from public.routines where user_id = pg_temp.u(n) and name = 'R' || n || p_tag $$;

-- A completed workout on p_day: of the block's routine when p_routine, with a squat working set when p_kg is given.
create function pg_temp.wk(u int, p_day date, p_routine boolean default false, p_kg numeric default null, p_tag text default 'c')
returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), case when p_routine then pg_temp.routine_of(u, p_tag) end, 'W', 'completed',
          pg_temp.at(p_day), pg_temp.at(p_day, 11));
  if p_kg is not null then
    insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
    values (v, pg_temp.sq(), 0, 1, 'working', p_kg, 5, pg_temp.at(p_day, 10) + interval '30 minutes');
  end if;
  return v;
end $$;

-- The plan weeks as 'week:start' pairs and the linked sessions as the weeks that hold a link.
create function pg_temp.cal(n int, p_today date default null) returns text language sql as $$
  select string_agg(c.week || ':' || c.start_date, ' ' order by c.week)
  from public.coach_plan_calendar_for(pg_temp.u(n), pg_temp.plan_of(n), coalesce(p_today, pg_temp.d0())) c $$;
create function pg_temp.linked_weeks(n int) returns text language sql as $$
  select coalesce(string_agg(s.week::text, ',' order by s.week), '')
  from public.coach_plan_sessions s where s.plan_id = pg_temp.plan_of(n) and s.session_id is not null $$;
create function pg_temp.snap(n int) returns text language sql as $$
  select md5(
    coalesce((select jsonb_agg(to_jsonb(g) - 'updated_at' order by g.user_id) from public.nutrition_goals g where g.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(p) - 'updated_at' order by p.id) from public.coach_plans p where p.user_id = pg_temp.u(n))::text, '')) $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data (owner)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'pause' || n || '@example.com' from generate_series(1, 14) n;
update public.profiles set onboarding_completed = true
where id in (select pg_temp.u(n) from generate_series(1, 14) n);

-- 1: block started 28 days ago (windows 0..4, today in window 4); workouts in windows 0 and 2 only: two paused weeks.
select pg_temp.mk(1, 'accepted', pg_temp.d0() - 28);
select pg_temp.wk(1, pg_temp.d0() - 27, true);
select pg_temp.wk(1, pg_temp.d0() - 13, true);
-- 2: started 14 days ago, trained in windows 0 and 1: no pause.
select pg_temp.mk(2, 'accepted', pg_temp.d0() - 14);
select pg_temp.wk(2, pg_temp.d0() - 13, true);
select pg_temp.wk(2, pg_temp.d0() - 6, true);
-- 3: started 14 days ago, trained in window 0, empty window 1, and a workout today (window 2): resumed.
select pg_temp.mk(3, 'accepted', pg_temp.d0() - 14);
select pg_temp.wk(3, pg_temp.d0() - 13, true);
select pg_temp.wk(3, pg_temp.d0(), true);
-- 4: the block starts in 3 days.
select pg_temp.mk(4, 'accepted', pg_temp.d0() + 3);
-- 5: started 35 days ago, trained in windows 0, 1 and 2 (squat 100 x 5 each: no improvement), two paused weeks, a
-- deload-eligible week 4 today (window 5). Its workout today is of the block's routine.
select pg_temp.mk(5, 'accepted', pg_temp.d0() - 35);
select pg_temp.wk(5, pg_temp.d0() - 34, false, 100);
select pg_temp.wk(5, pg_temp.d0() - 27, false, 100);
select pg_temp.wk(5, pg_temp.d0() - 20, false, 100);
-- 6: as 5 with the diet break: week 4 is today.
select pg_temp.mk(6, 'accepted', pg_temp.d0() - 35, 'c', 4, true);
select pg_temp.wk(6, pg_temp.d0() - 34);
select pg_temp.wk(6, pg_temp.d0() - 27);
select pg_temp.wk(6, pg_temp.d0() - 20);
-- 7: started 28 days ago with the diet break, trained in windows 0 and 1: week 3 today (two paused weeks), no diary.
select pg_temp.mk(7, 'accepted', pg_temp.d0() - 28, 'c', 4, true);
select pg_temp.wk(7, pg_temp.d0() - 27);
select pg_temp.wk(7, pg_temp.d0() - 20);
-- 9: a finished block with a gap: started 35 days ago, squat in windows 0, 1, 3 and 4 (100, 105, 110, 115), window 2 empty.
select pg_temp.mk(9, 'accepted', pg_temp.d0() - 35);
select pg_temp.wk(9, pg_temp.d0() - 34, false, 100);
select pg_temp.wk(9, pg_temp.d0() - 27, false, 105);
select pg_temp.wk(9, pg_temp.d0() - 13, false, 110);
select pg_temp.wk(9, pg_temp.d0() - 6, false, 115);
-- 10: an earlier block (start 56 days ago, trained in windows 0 and 2) ended early by the current block (start 28 days
-- ago, trained in windows 0 and 1): both cut blocks.
select pg_temp.mk(10, 'superseded', pg_temp.d0() - 56, 'a');
select pg_temp.wk(10, pg_temp.d0() - 55, false, null, 'a');
select pg_temp.wk(10, pg_temp.d0() - 41, false, null, 'a');
select pg_temp.mk(10, 'accepted', pg_temp.d0() - 28, 'b');
select pg_temp.wk(10, pg_temp.d0() - 27, false, null, 'b');
select pg_temp.wk(10, pg_temp.d0() - 20, false, null, 'b');
-- 12: plan whose weeks start at training_start (14 days ago) while block_start is 70 days ago; one workout in window 0.
select pg_temp.mk(12, 'accepted', pg_temp.d0() - 70, 'c', 4, false, pg_temp.d0() - 14);
select pg_temp.wk(12, pg_temp.d0() - 13, true);
-- 13: the existing-block migration: workouts in windows 0, 2 and 3 of a block started 28 days ago, with the links
-- written the way the calendar numbering did (weeks 1, 3 and 4).
select pg_temp.mk(13, 'accepted', pg_temp.d0() - 28);
select pg_temp.wk(13, pg_temp.d0() - 27);
select pg_temp.wk(13, pg_temp.d0() - 13);
select pg_temp.wk(13, pg_temp.d0() - 6);
update public.coach_plan_sessions s set session_id = x.id
from (select w.id, row_number() over (order by w.started_at) as n from public.workout_sessions w where w.user_id = pg_temp.u(13)) x
where s.plan_id = pg_temp.plan_of(13) and s.week = case x.n when 1 then 1 when 2 then 3 else 4 end;
-- 14: a user with no plan and no diary.

-- Goals of users 6 and 7 at the base figures.
select set_config('app.coach_write', 'on', true);
insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
select pg_temp.u(n), 'deficit', -500, 2500, 33, 42, 25 from unnest(array[6, 7, 13]) n;
select set_config('app.coach_write', 'off', true);

-- ---------------------------------------------------------------------------
-- Structure and privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_block_pause(uuid, date)', 'execute'),
  'anon cannot execute coach_block_pause');
select ok(not has_function_privilege('anon', 'public.coach_plan_calendar_for(uuid, uuid, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_plan_week_of_date_for(uuid, uuid, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_plan_pause_state_for(uuid, uuid, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_plan_trained_weeks_for(uuid, uuid)', 'execute')
      and not has_function_privilege('anon', 'public.coach_block_end(uuid, date)', 'execute'),
  'anon cannot execute the helpers');
select ok(has_function_privilege('authenticated', 'public.coach_block_pause(uuid, date)', 'execute'),
  'authenticated can execute coach_block_pause');

-- ---------------------------------------------------------------------------
-- Mapping: windows 0 and 2 trained, windows 1 and 3 empty (user 1; today in window 4)
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);
select is(pg_temp.cal(1),
  '1:' || (pg_temp.d0() - 28) || ' 2:' || (pg_temp.d0() - 14) || ' 3:' || pg_temp.d0() || ' 4:' || (pg_temp.d0() + 7),
  'two paused weeks: plan weeks 1 and 2 are the trained windows, week 3 is the current window, week 4 follows');
select is(public.coach_plan_week(pg_temp.plan_of(1), pg_temp.d0()), 3, 'the current plan week is the trained weeks plus one');
select is(public.coach_block_pause(pg_temp.plan_of(1), pg_temp.d0()) - array['extension_days', 'min_end', 'max_end', 'extendable'],
  jsonb_build_object('paused', true, 'paused_weeks', 2, 'block_end', pg_temp.d0() + 13),
  'the block is paused, two weeks are paused and the end moved two weeks later');
select is(public.coach_block_end(pg_temp.plan_of(1), pg_temp.d0()), pg_temp.d0() + 13, 'the block end is the end of the last plan week window');
select is((select jsonb_agg(w->>'start_date' order by (w->>'week')::int) from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(1), pg_temp.d0())) w),
  jsonb_build_array((pg_temp.d0() - 28)::text, (pg_temp.d0() - 14)::text, pg_temp.d0()::text, (pg_temp.d0() + 7)::text),
  'coach_plan_weeks reads the dates of the calendar');
select is((select jsonb_agg(w->>'phase' order by (w->>'week')::int) from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(1), pg_temp.d0())) w),
  '["past", "past", "current", "upcoming"]'::jsonb, 'weeks 1 and 2 are past, week 3 is current');
select is(pg_temp.linked_weeks(1), '1,2', 'the workouts linked to the paused-adjusted weeks 1 and 2');

-- No pause (user 2), resumed (user 3), before the start (user 4).
select pg_temp.as_user(2);
select is(public.coach_block_pause(pg_temp.plan_of(2), pg_temp.d0()) - array['extension_days', 'min_end', 'max_end', 'extendable'],
  jsonb_build_object('paused', false, 'paused_weeks', 0, 'block_end', pg_temp.d0() + 13),
  'a block trained every week is not paused and ends 4 weeks after the start');
select is(public.coach_plan_week(pg_temp.plan_of(2), pg_temp.d0()), 3, 'user 2: week 3 today');
select pg_temp.as_user(3);
select is(public.coach_block_pause(pg_temp.plan_of(3), pg_temp.d0()) - array['extension_days', 'min_end', 'max_end', 'extendable'],
  jsonb_build_object('paused', false, 'paused_weeks', 1, 'block_end', pg_temp.d0() + 20),
  'a workout in the current window resumes the block; the paused week stays counted and the end stays moved');
select is(pg_temp.linked_weeks(3), '1,2', 'the first workout after the pause continues from the paused week and links to it');
select pg_temp.as_user(4);
select is(public.coach_plan_week(pg_temp.plan_of(4), pg_temp.d0()), 0, 'before the block starts the plan week is 0');
select is(public.coach_block_pause(pg_temp.plan_of(4), pg_temp.d0()) - array['extension_days', 'min_end', 'max_end', 'extendable'],
  jsonb_build_object('paused', false, 'paused_weeks', 0, 'block_end', pg_temp.d0() + 30),
  'before the start nothing is paused');
select is(pg_temp.cal(4), '1:' || (pg_temp.d0() + 3) || ' 2:' || (pg_temp.d0() + 10) || ' 3:' || (pg_temp.d0() + 17) || ' 4:' || (pg_temp.d0() + 24),
  'before the start week n is the n-th window from the start');

-- ---------------------------------------------------------------------------
-- Plan with training_start (user 12)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(12);
select is(public.coach_plan_week(pg_temp.plan_of(12), pg_temp.d0()), 2,
  'plan weeks are anchored at training_start, not block_start (one paused window, week 2)');
select is(pg_temp.cal(12), '1:' || (pg_temp.d0() - 14) || ' 2:' || pg_temp.d0() || ' 3:' || (pg_temp.d0() + 7) || ' 4:' || (pg_temp.d0() + 14),
  'the calendar starts at the training start');

-- ---------------------------------------------------------------------------
-- Deload at a moved week 4 (user 5, today in window 5; weeks 1-3 are windows 0-2)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(5);
select is(public.coach_plan_week(pg_temp.plan_of(5), pg_temp.d0()), 4, 'user 5: the current plan week is 4 although 6 windows have passed');
select is(public.evaluate_coach_deload(pg_temp.d0())->>'status', 'applied', 'the deload evaluates in the moved week 4');
select is((select string_agg(distinct s.week::text, ',') from public.coach_plan_sessions s
           where s.plan_id = pg_temp.plan_of(5) and s.deload), '4', 'the deload mark is on plan week 4');
select is((select (w->>'deload')::boolean from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(5), pg_temp.d0())) w
           where w->>'week' = '4'), true, 'coach_plan_weeks shows week 4 as the deload week');
reset role;
select pg_temp.wk(5, pg_temp.d0(), true);
set local role authenticated;
select pg_temp.as_user(5);
select is(public.coach_session_in_deload(pg_temp.u(5), (select id from public.workout_sessions
          where user_id = pg_temp.u(5) and routine_id is not null)), true,
  'a workout of the routine in the moved week 4 is in the deload');
select is(pg_temp.linked_weeks(5), '4', 'it linked to plan week 4');

-- ---------------------------------------------------------------------------
-- Diet break moves with the pause; calories are not touched by a pause (users 6 and 7)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(6);
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": true, "week": 4, "target_kcal": 3000}'::jsonb,
  'the diet break lands in plan week 4 (today) after two paused weeks');
select pg_temp.as_user(7);
create temp table g7 as select md5(to_jsonb(g)::text) as h from public.nutrition_goals g where g.user_id = pg_temp.u(7);
grant select on g7 to authenticated;
select is(public.apply_coach_diet_break(pg_temp.d0()), '{"applied": false, "week": 3, "target_kcal": 2500}'::jsonb,
  'two paused weeks keep the diet break out of plan week 3: base figures');
select is(public.evaluate_coach_calorie_change(pg_temp.d0())->>'status', 'none',
  'an empty week proposes no calorie change');
select is((select count(*)::int from public.coach_recommendations where user_id = pg_temp.u(7) and kind = 'calorie_change'), 0,
  'no calorie proposal row exists');
select is((select md5(to_jsonb(g)::text) from public.nutrition_goals g where g.user_id = pg_temp.u(7)), (select h from g7),
  'the nutrition goal is unchanged by the pause');

-- coach_week_gate with an empty diary
select pg_temp.as_user(14);
select is(public.coach_week_gate(pg_temp.d0()), '{"ok": false, "complete_days": 0, "weigh_in_days": 0}'::jsonb,
  'an empty diary gives ok false with zero counts, never null');
select is(public.coach_week_gate(pg_temp.d0() - 28), '{"ok": false, "complete_days": 0, "weigh_in_days": 0}'::jsonb,
  '28 empty days give the same');

-- ---------------------------------------------------------------------------
-- Weekly summary of a paused week (user 1) and a normal one (user 2)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select is(public.coach_week_summary(pg_temp.d0()),
  jsonb_build_object('plan_id', pg_temp.plan_of(1), 'paused', true,
                     'start_date', pg_temp.d0() - 7, 'end_date', pg_temp.d0() - 1),
  'the week that just ended without a workout reads only {plan_id, paused, start_date, end_date}');
select is((select count(*)::int from public.coach_week_summary_seen where user_id = pg_temp.u(1)), 0,
  'a paused summary is never marked seen');
select pg_temp.as_user(2);
select is((public.coach_week_summary(pg_temp.d0()))->>'paused', 'false', 'a summary of a trained week says paused false');
select is((public.coach_week_summary(pg_temp.d0()))->>'week', '2', 'and keeps its plan week');
select ok(public.coach_week_summary(pg_temp.d0()) ? 'groups' and public.coach_week_summary(pg_temp.d0()) ? 'stalls',
  'and keeps the rest of its keys');
select pg_temp.as_user(3);
select is((public.coach_week_summary(pg_temp.d0()))->>'paused', 'true',
  'after resuming, the summary of the empty week before still reads paused');

-- ---------------------------------------------------------------------------
-- Review of a block with a gap (user 9: end moved to yesterday, weeks follow the plan weeks)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(9);
select is(public.coach_block_end(pg_temp.plan_of(9)), pg_temp.d0() - 1, 'the block of user 9 ended yesterday (one paused week)');
create temp table rv9 as select public.open_coach_block_review(pg_temp.d0()) as j;
grant select on rv9 to authenticated;
select is((select (j->>'ended_on')::date from rv9), pg_temp.d0() - 1, 'the review ended_on is the moved block end');
select is((select jsonb_agg(w->'week' order by (w->>'week')::int) from rv9, jsonb_array_elements(j->'inputs'->'lifts'->0->'weeks') w),
  '[1, 2, 3, 4]'::jsonb, 'the weekly e1RM of the review is grouped by plan week (1, 2, 3, 4), not by calendar week');
select is((select j->'inputs'->'adherence'->>'planned' from rv9), '4', 'the review reads the four plan weeks');

-- ---------------------------------------------------------------------------
-- Weeks in phase (user 10): an earlier block ended early counts the plan week reached before the next one began
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
select is(public.coach_block_counted_weeks(pg_temp.plan_of(10, 'a'), pg_temp.d0()), 3,
  'the earlier block counts 3 weeks (windows 0, 2 and the one holding the day before the next block)');
select is(public.coach_block_counted_weeks(pg_temp.plan_of(10, 'b'), pg_temp.d0()), 3,
  'the newest block counts its plan week today (2 trained windows + 1)');
select is(public.coach_weeks_in_phase(pg_temp.d0()), '{"phase": "cut", "weeks": 6}'::jsonb, 'weeks in phase add both blocks');

-- ---------------------------------------------------------------------------
-- RLS: user B cannot read user A's plan through the new functions
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is(public.coach_block_pause(pg_temp.plan_of(1), pg_temp.d0()), null, 'user B gets no pause state of user A');
select is(public.coach_block_end(pg_temp.plan_of(1), pg_temp.d0()), null, 'user B gets no block end of user A');
select is((select count(*)::int from public.coach_plan_calendar_for(pg_temp.u(1), pg_temp.plan_of(1), pg_temp.d0())), 0,
  'passing user A''s id to the explicit helper still shows nothing (RLS)');

-- ---------------------------------------------------------------------------
-- Backdated workout: a workout added to an empty window re-maps the later weeks (user 1), links stay
-- ---------------------------------------------------------------------------
reset role;
select pg_temp.wk(1, pg_temp.d0() - 20);
set local role authenticated;
select pg_temp.as_user(1);
select is(public.coach_block_pause(pg_temp.plan_of(1), pg_temp.d0())->>'paused_weeks', '1',
  'a backdated workout in window 1 removes one paused week');
select is(pg_temp.linked_weeks(1), '1,2', 'the existing links keep their rows');

select * from finish();
rollback;
