-- pgTAP: extend the running block (T-274, US-134, ADR-106; rules in planning/04 section 9.9).
-- coach_plan_extensions(plan_id, window_k, days) adds days to one 7-day window; set_coach_block_end writes it.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(71);

-- ---------------------------------------------------------------------------
-- Fixture helpers
-- ---------------------------------------------------------------------------
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c2740000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.sq() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'squat' order by key limit 1 $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

create function pg_temp.nut() returns jsonb language sql as $$
  select jsonb_build_object(
    'intention', 'fat_loss', 'weight_kg', 80, 'expenditure_kcal', 3000, 'expenditure_source', 'formula',
    'rate_pct_week', 0.568, 'rate_kg_week', -0.45, 'target_rate_pct', 0.5, 'limited_by', null, 'floor_kcal', 1500,
    'target_kcal', 2500, 'calorie_adjustment', -500, 'strategy', 'deficit',
    'protein_pct', 33, 'carbs_pct', 42, 'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61,
    'protein_g_per_kg', 2.2, 'distribution', jsonb_build_object('per_meal_g', 32, 'g_per_kg', 0.4, 'min_meals', 4),
    'requires_confirmation', false, 'unconfirmed', '[]'::jsonb, 'edited_by_user', false, 'calorie_changes', 0,
    'phase', 'cut', 'phase_reason', 'intention',
    'phase_options', jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', false)) $$;

-- A block of 4 weeks, one session a week of one routine. p_folder false: an accepted plan with no saved block.
create function pg_temp.mk(u int, p_start date, p_folder boolean default true) returns uuid
language plpgsql as $$
declare
  v_plan uuid := gen_random_uuid();
  v_folder uuid;
  v_r uuid;
  w int;
begin
  if p_folder then
    insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque') returning id into v_folder;
  end if;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id, phase, phase_options)
  values (v_plan, pg_temp.u(u), p_start, 'fat_loss', pg_temp.nut(), 'accepted', v_folder, 'cut',
          jsonb_build_object('cut_rate_pct', 0.5, 'diet_break', false));
  if p_folder then
    insert into public.routines (user_id, name, folder_id) values (pg_temp.u(u), 'R' || u, v_folder) returning id into v_r;
    for w in 1..4 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
      values (v_plan, pg_temp.u(u), w, 1, 'Sesion',
              jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.sq(), 'role', 'main', 'sets', 3)), v_r);
    end loop;
  end if;
  return v_plan;
end $$;

create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans p where p.user_id = pg_temp.u(n) $$;

-- A completed workout of the block's routine on p_day.
create function pg_temp.wk(u int, p_day date) returns uuid language plpgsql as $$
declare v uuid := gen_random_uuid();
begin
  insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at)
  values (v, pg_temp.u(u), (select id from public.routines where user_id = pg_temp.u(u)), 'W', 'completed',
          pg_temp.at(p_day), pg_temp.at(p_day, 11));
  return v;
end $$;

-- The plan weeks as 'week:start..end' pairs, and the weeks that hold a link.
create function pg_temp.cal(n int, p_today date default null) returns text language sql as $$
  select string_agg(w->>'week' || ':' || (w->>'start_date') || '..' || (w->>'end_date'), ' ' order by (w->>'week')::int)
  from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(n), coalesce(p_today, pg_temp.d0()))) w $$;
create function pg_temp.linked_weeks(n int) returns text language sql as $$
  select coalesce(string_agg(s.week::text, ',' order by s.week), '')
  from public.coach_plan_sessions s where s.plan_id = pg_temp.plan_of(n) and s.session_id is not null $$;
-- Everything an extension must not touch: the plan row, its sessions (deload marks, links) and the nutrition goal.
create function pg_temp.snap(n int) returns text language sql as $$
  select md5(
    coalesce((select jsonb_agg(to_jsonb(g) - 'updated_at' order by g.user_id) from public.nutrition_goals g where g.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(p) - 'updated_at' order by p.id) from public.coach_plans p where p.user_id = pg_temp.u(n))::text, '') ||
    coalesce((select jsonb_agg(to_jsonb(s) - 'created_at' order by s.week, s.session_index) from public.coach_plan_sessions s where s.user_id = pg_temp.u(n))::text, '')) $$;
create function pg_temp.pick(n int, p_day date, p_today date default null) returns jsonb language sql as $$
  select public.set_coach_block_end(pg_temp.plan_of(n), p_day, coalesce(p_today, pg_temp.d0())) $$;
create function pg_temp.rows_of(n int) returns text language sql as $$
  select coalesce(string_agg(e.window_k || ':' || e.days, ' ' order by e.window_k), '')
  from public.coach_plan_extensions e where e.plan_id = pg_temp.plan_of(n) $$;
create function pg_temp.pause_of(n int, p_today date default null) returns jsonb language sql as $$
  select public.coach_block_pause(pg_temp.plan_of(n), coalesce(p_today, pg_temp.d0())) $$;

grant execute on all functions in schema pg_temp to authenticated;

-- ---------------------------------------------------------------------------
-- Fixture data (owner). Windows are 7 days from the block start; "today in window k" is noted per user.
-- ---------------------------------------------------------------------------
insert into auth.users (id, email)
select pg_temp.u(n), 'ext' || n || '@example.com' from generate_series(1, 14) n;
update public.profiles set onboarding_completed = true
where id in (select pg_temp.u(n) from generate_series(1, 14) n);

-- Users 1, 3, 4, 13: started 21 days ago, trained in windows 0..2 (weeks 1-3 linked), today in window 3 (the last week 4).
-- The block ends in 6 days.
select pg_temp.mk(n, pg_temp.d0() - 21) from unnest(array[1, 3, 4, 13]) n;
select pg_temp.wk(n, pg_temp.d0() - 20 + 7 * i) from unnest(array[1, 3, 4, 13]) n cross join generate_series(0, 2) i;
-- 2: started 7 days ago, trained in window 0 (week 1 linked), today in window 1 (week 2 of 4); the block ends in 20 days.
select pg_temp.mk(2, pg_temp.d0() - 7);
select pg_temp.wk(2, pg_temp.d0() - 6);
-- 5: started 9 days ago; window 0 already carries 2 extra days (as an earlier pick would have left it); today in window 1.
select pg_temp.mk(5, pg_temp.d0() - 9);
select pg_temp.wk(5, pg_temp.d0() - 8);
insert into public.coach_plan_extensions (plan_id, user_id, window_k, days)
values (pg_temp.plan_of(5), pg_temp.u(5), 0, 2);
-- 6: started 28 days ago, trained in windows 0 and 2 only: two paused weeks, today in window 4 (plan week 3).
select pg_temp.mk(6, pg_temp.d0() - 28);
select pg_temp.wk(6, pg_temp.d0() - 27);
select pg_temp.wk(6, pg_temp.d0() - 13);
-- 7: an accepted plan with no saved block.
select pg_temp.mk(7, pg_temp.d0() - 7, false);
-- 8: a block whose four weeks were all trained and ended 8 days ago.
select pg_temp.mk(8, pg_temp.d0() - 35);
select pg_temp.wk(8, pg_temp.d0() - 34 + 7 * i) from generate_series(0, 3) i;
-- 9, 10: another user's block (10 gets an extension below).
select pg_temp.mk(9, pg_temp.d0() - 7);
select pg_temp.mk(10, pg_temp.d0() - 7);
-- 11: ends yesterday (windows 0..3 trained): the review opens tomorrow unless the last day moves.
select pg_temp.mk(11, pg_temp.d0() - 28);
select pg_temp.wk(11, pg_temp.d0() - 27 + 7 * i) from generate_series(0, 3) i;
-- 12: an extension row for the cascade test.
select pg_temp.mk(12, pg_temp.d0() - 7);

select set_config('app.coach_write', 'on', true);
insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
select pg_temp.u(n), 'deficit', -500, 2500, 33, 42, 25 from unnest(array[2, 5]) n;
select set_config('app.coach_write', 'off', true);
-- Deload mark on week 3 of user 2: it must stay on week 3 after the extension.
update public.coach_plan_sessions set deload = true where plan_id = pg_temp.plan_of(2) and week = 3;

-- ---------------------------------------------------------------------------
-- Structure and privileges
-- ---------------------------------------------------------------------------
select ok((select c.relrowsecurity and c.relforcerowsecurity from pg_class c where c.oid = 'public.coach_plan_extensions'::regclass),
  'RLS is enabled and forced on coach_plan_extensions');
select ok(not has_table_privilege('authenticated', 'public.coach_plan_extensions', 'insert')
      and not has_table_privilege('authenticated', 'public.coach_plan_extensions', 'update')
      and not has_table_privilege('authenticated', 'public.coach_plan_extensions', 'delete')
      and has_table_privilege('authenticated', 'public.coach_plan_extensions', 'select')
      and not has_table_privilege('anon', 'public.coach_plan_extensions', 'select'),
  'clients can only read the table; anon cannot');
select ok(not has_function_privilege('anon', 'public.set_coach_block_end(uuid, date, date)', 'execute')
      and has_function_privilege('authenticated', 'public.set_coach_block_end(uuid, date, date)', 'execute'),
  'only authenticated can execute set_coach_block_end');
select ok(not has_function_privilege('anon', 'public.coach_plan_windows_for(uuid, uuid, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_plan_norm_date_for(uuid, uuid, date)', 'execute')
      and not has_function_privilege('anon', 'public.coach_block_pause_for(uuid, uuid, date)', 'execute'),
  'anon cannot execute the new helpers');

-- ---------------------------------------------------------------------------
-- Empty table: the E18-B answers (user 1: the last week of a block, nothing stored)
-- ---------------------------------------------------------------------------
create temp table ids as select pg_temp.plan_of(9) as p9, pg_temp.plan_of(10) as p10;
grant select on ids to authenticated;
set local role authenticated;
select pg_temp.as_user(1);
select is(pg_temp.pause_of(1),
  jsonb_build_object('paused', false, 'paused_weeks', 0, 'block_end', pg_temp.d0() + 6, 'extension_days', 0,
                     'min_end', pg_temp.d0() + 6, 'max_end', pg_temp.d0() + 20, 'extendable', true),
  'no rows: the block ends where it did, and it can be extended up to 14 days');
select is(pg_temp.cal(1),
  '1:' || (pg_temp.d0() - 21) || '..' || (pg_temp.d0() - 15) || ' 2:' || (pg_temp.d0() - 14) || '..' || (pg_temp.d0() - 8)
  || ' 3:' || (pg_temp.d0() - 7) || '..' || (pg_temp.d0() - 1) || ' 4:' || pg_temp.d0() || '..' || (pg_temp.d0() + 6),
  'no rows: the weeks are plain 7-day windows');
select is(public.coach_plan_norm_date_for((select auth.uid()), pg_temp.plan_of(1), pg_temp.d0() + 3), pg_temp.d0() + 3,
  'no rows: a date maps to itself');

-- ---------------------------------------------------------------------------
-- Extending in the last week (user 1)
-- ---------------------------------------------------------------------------
select is(pg_temp.pick(1, pg_temp.d0() + 9),
  jsonb_build_object('paused', false, 'paused_weeks', 0, 'block_end', pg_temp.d0() + 9, 'extension_days', 3,
                     'min_end', pg_temp.d0() + 6, 'max_end', pg_temp.d0() + 20, 'extendable', true),
  'picking 3 days later returns the new pause state');
select is(pg_temp.rows_of(1), '3:3', 'one row: the current window (3) with 3 days');
select is(public.coach_block_end(pg_temp.plan_of(1), pg_temp.d0()), pg_temp.d0() + 9, 'the block end is the new last day');
select is(pg_temp.cal(1),
  '1:' || (pg_temp.d0() - 21) || '..' || (pg_temp.d0() - 15) || ' 2:' || (pg_temp.d0() - 14) || '..' || (pg_temp.d0() - 8)
  || ' 3:' || (pg_temp.d0() - 7) || '..' || (pg_temp.d0() - 1) || ' 4:' || pg_temp.d0() || '..' || (pg_temp.d0() + 9),
  'only the current week grows; past weeks keep their dates');
select is((select w->>'phase' from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(1), pg_temp.d0() + 8)) w
           where w->>'week' = '4'), 'current', 'a day inside the extension is still in the current week');
select is(public.coach_plan_week(pg_temp.plan_of(1), pg_temp.d0() + 8), 4, 'the plan week of an extension day is the current week');
select is(public.coach_plan_norm_date_for((select auth.uid()), pg_temp.plan_of(1), pg_temp.d0() + 8), pg_temp.d0() + 6,
  'a date inside the extension falls on the last grid day');
select is((select jsonb_agg(w->>'adherence_pct' order by (w->>'week')::int)
           from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(1), pg_temp.d0())) w),
  '["100", "100", "100", "0"]'::jsonb, 'adherence of the past weeks is unchanged');

-- A workout on the new last day counts in the extended week (user 1), as the trigger links it.
reset role;
select pg_temp.wk(1, pg_temp.d0() + 9);
set local role authenticated;
select pg_temp.as_user(1);
select is(pg_temp.linked_weeks(1), '1,2,3,4', 'a session on the new last day links to the extended current week');
select is((select w->>'adherence_pct' from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(1), pg_temp.d0())) w
           where w->>'week' = '4'), '100', 'and it counts in the adherence of that week');

-- A workout the day after the new last day does not (user 3).
select pg_temp.as_user(3);
select pg_temp.pick(3, pg_temp.d0() + 9);
reset role;
select pg_temp.wk(3, pg_temp.d0());
create temp table late3 as select pg_temp.wk(3, pg_temp.d0() + 10) as id;
grant select on late3 to authenticated;
set local role authenticated;
select pg_temp.as_user(3);
select is(pg_temp.linked_weeks(3), '1,2,3,4', 'user 3: this week''s session on the extended week is linked');
select is((select count(*)::int from public.coach_plan_sessions s where s.session_id = (select id from late3)), 0,
  'a session the day after the last day is not linked to the block');

-- ---------------------------------------------------------------------------
-- Changing my mind (user 4): back to the original day removes the row; dates are the original ones
-- ---------------------------------------------------------------------------
select pg_temp.as_user(4);
select is(pg_temp.cal(4), (select string_agg(w->>'week' || ':' || (w->>'start_date') || '..' || (w->>'end_date'), ' ' order by (w->>'week')::int)
                           from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(4), pg_temp.d0())) w), 'self-check of the helper');
create temp table before4 as select pg_temp.cal(4) as c, pg_temp.snap(4) as s;
grant select on before4 to authenticated;
select pg_temp.pick(4, pg_temp.d0() + 12);
select is(pg_temp.rows_of(4), '3:6', 'a second user extended by 6 days');
select pg_temp.pick(4, pg_temp.d0() + 6);
select is(pg_temp.rows_of(4), '', 'picking the original day stores nothing');
select is(pg_temp.cal(4), (select c from before4), 'and every date is back to what it was');
select is(pg_temp.snap(4), (select s from before4), 'the plan, its sessions and the goal are the same after the round trip');

-- ---------------------------------------------------------------------------
-- Extending in an earlier week (user 2): later weeks move, nothing else does
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select is(pg_temp.pause_of(2)->>'block_end', (pg_temp.d0() + 20)::text, 'user 2: the block ends in 20 days');
create temp table before2 as select pg_temp.snap(2) as s;
grant select on before2 to authenticated;
select pg_temp.pick(2, pg_temp.d0() + 23);
select is(pg_temp.rows_of(2), '1:3', 'user 2: the extension sits on window 1');
select is(pg_temp.cal(2),
  '1:' || (pg_temp.d0() - 7) || '..' || (pg_temp.d0() - 1) || ' 2:' || pg_temp.d0() || '..' || (pg_temp.d0() + 9)
  || ' 3:' || (pg_temp.d0() + 10) || '..' || (pg_temp.d0() + 16) || ' 4:' || (pg_temp.d0() + 17) || '..' || (pg_temp.d0() + 23),
  'user 2: week 2 grows by 3 days; weeks 3 and 4 and the block end move 3 days');
select is((select w->>'completed' from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(2), pg_temp.d0())) w where w->>'week' = '2'),
  '0', 'user 2: this week''s session is still to do');
select is(pg_temp.snap(2), (select s from before2), 'user 2: deload marks, plan sessions, links and goals are identical');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = pg_temp.plan_of(2) and deload and week = 3), 1,
  'user 2: the deload mark is still on plan week 3');

-- ---------------------------------------------------------------------------
-- A second pick in a later window keeps the earlier window's row (user 5: window 0 has 2 extra days)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(5);
select is(pg_temp.cal(5),
  '1:' || (pg_temp.d0() - 9) || '..' || (pg_temp.d0() - 1) || ' 2:' || pg_temp.d0() || '..' || (pg_temp.d0() + 6)
  || ' 3:' || (pg_temp.d0() + 7) || '..' || (pg_temp.d0() + 13) || ' 4:' || (pg_temp.d0() + 14) || '..' || (pg_temp.d0() + 20),
  'user 5: window 0 carries 2 days, the later weeks are 2 days later');
select is(public.coach_plan_norm_date_for((select auth.uid()), pg_temp.plan_of(5), pg_temp.d0() - 1), pg_temp.d0() - 3,
  'user 5: a date in the earlier extension falls on that window''s last grid day');
select is(public.coach_plan_norm_date_for((select auth.uid()), pg_temp.plan_of(5), pg_temp.d0()), pg_temp.d0() - 2,
  'user 5: the date after it shifts back by 2 days');
select is(pg_temp.pause_of(5)->'min_end', to_jsonb(pg_temp.d0() + 20), 'user 5: the minimum is the end without the current extension');
select pg_temp.pick(5, pg_temp.d0() + 23);
select is(pg_temp.rows_of(5), '0:2 1:3', 'user 5: the earlier window keeps its row and the current one gets its own');
select is((pg_temp.pause_of(5)->>'extension_days')::int, 5, 'user 5: the extension days add up');
select is(pg_temp.pause_of(5)->>'block_end', (pg_temp.d0() + 23)::text, 'user 5: the block ends on the picked day');
select is((select w->>'end_date' from jsonb_array_elements(public.coach_plan_weeks(pg_temp.plan_of(5), pg_temp.d0())) w where w->>'week' = '1'),
  (pg_temp.d0() - 1)::text, 'user 5: the past week keeps its extension');
select pg_temp.pick(5, pg_temp.d0() + 20);
select is(pg_temp.rows_of(5), '0:2', 'user 5: re-picking the end without the current extension removes only the current row');

-- ---------------------------------------------------------------------------
-- Limits (user 5: window 0 carries 2 of the 14 days; the end without extensions is today + 18)
-- ---------------------------------------------------------------------------
select throws_ok($$select pg_temp.pick(5, pg_temp.d0() + 19)$$, '22023', null, 'below the minimum (the end it would have) is refused');
select throws_ok($$select pg_temp.pick(5, pg_temp.d0() + 35)$$, '22023', null, 'more than 14 days past the unextended end is refused (the total counts)');
select is(pg_temp.pause_of(5)->>'max_end', (pg_temp.d0() + 32)::text, 'the maximum is the unextended end + 14');
select lives_ok($$select pg_temp.pick(5, pg_temp.d0() + 32)$$, 'exactly 14 days in total is allowed');
select is(pg_temp.rows_of(5), '0:2 1:12', 'the total is 14');
select pg_temp.pick(5, pg_temp.d0() + 20);
select throws_ok($$select pg_temp.pick(1, null)$$, '22023', null, 'no day is refused');
select throws_ok($$select pg_temp.pick(1, pg_temp.d0() + 5)$$, '22023', null, 'user 1: a day before the end it would have is refused');
select throws_ok($$select public.set_coach_block_end(pg_temp.plan_of(1), pg_temp.d0() + 9, pg_temp.d0() + 5)$$, '22023', null,
  'a today far from the real date is refused (as every coach RPC)');

-- ---------------------------------------------------------------------------
-- Paused weeks plus an extension (user 6: windows 1 and 3 empty, today in window 4)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(6);
select is(pg_temp.pause_of(6),
  jsonb_build_object('paused', true, 'paused_weeks', 2, 'block_end', pg_temp.d0() + 13, 'extension_days', 0,
                     'min_end', pg_temp.d0() + 13, 'max_end', pg_temp.d0() + 27, 'extendable', true),
  'user 6: paused two weeks, block ends in 13 days');
select pg_temp.pick(6, pg_temp.d0() + 16);
select is(pg_temp.pause_of(6),
  jsonb_build_object('paused', true, 'paused_weeks', 2, 'block_end', pg_temp.d0() + 16, 'extension_days', 3,
                     'min_end', pg_temp.d0() + 13, 'max_end', pg_temp.d0() + 27, 'extendable', true),
  'user 6: the pause rule is unchanged and the end moved 3 days');
select is(public.coach_plan_week(pg_temp.plan_of(6), pg_temp.d0()), 3, 'user 6: the current plan week is still 3');

-- ---------------------------------------------------------------------------
-- No block, a block past its last day
-- ---------------------------------------------------------------------------
select pg_temp.as_user(7);
select is(pg_temp.pause_of(7)->'extendable', 'false'::jsonb, 'no saved block: not extendable');
select throws_ok($$select pg_temp.pick(7, pg_temp.d0() + 30)$$, '22023', null, 'no saved block: refused');
select pg_temp.as_user(8);
select is(pg_temp.pause_of(8)->'extendable', 'false'::jsonb, 'past the last day: not extendable');
select is(pg_temp.pause_of(8)->'min_end', 'null'::jsonb, 'past the last day: no limits');
select throws_ok($$select pg_temp.pick(8, pg_temp.d0() + 3)$$, '22023', null, 'past the last day: refused');
select is(pg_temp.rows_of(8), '', 'past the last day: nothing stored');

-- ---------------------------------------------------------------------------
-- The review and the block-end date move (user 11: the last day was yesterday; p_today = yesterday)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(11);
select pg_temp.pick(11, pg_temp.d0(), pg_temp.d0() - 1);
select is(public.coach_block_end(pg_temp.plan_of(11), pg_temp.d0()), pg_temp.d0(), 'user 11: the block now ends today');
select is(public.open_coach_block_review(pg_temp.d0()), null, 'user 11: no review is due on the new last day');
select is(public.open_coach_block_review(pg_temp.d0() + 1)->>'ended_on', pg_temp.d0()::text,
  'user 11: the review opens the day after the new last day and carries it');

-- ---------------------------------------------------------------------------
-- Other users
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
select pg_temp.pick(10, pg_temp.d0() + 30);
select is(pg_temp.rows_of(10), '1:3', 'user 10 extended their block');
select pg_temp.as_user(9);
select is((select count(*)::int from public.coach_plan_extensions), 0, 'user 9 sees no extension rows of other users');
select throws_ok($$select public.set_coach_block_end((select p10 from ids), pg_temp.d0() + 24, pg_temp.d0())$$, 'P0001', null,
  'user 9 cannot change the block of user 10');
select is(public.coach_block_pause((select p10 from ids), pg_temp.d0()), null, 'user 9 gets no pause state of the block of user 10');
select is((select count(*)::int from public.coach_plan_extensions where plan_id = (select p10 from ids)), 0,
  'user 9 reads none of the rows of user 10');
select throws_ok($$insert into public.coach_plan_extensions (plan_id, window_k, days) values ((select p9 from ids), 0, 1)$$,
  '42501', null, 'a client cannot write the table directly');
select throws_ok($$delete from public.coach_plan_extensions$$, '42501', null, 'a client cannot delete from the table');
reset role;
select is((select count(*)::int from public.coach_plan_extensions where plan_id = pg_temp.plan_of(10)), 1,
  'user 10''s row is untouched by user 9');
set local role anon;
select throws_ok($$select public.set_coach_block_end(gen_random_uuid(), current_date, current_date)$$, '42501', null,
  'anon cannot call the writer');
reset role;

-- ---------------------------------------------------------------------------
-- Rows go with the plan and with the account
-- ---------------------------------------------------------------------------
insert into public.coach_plan_extensions (plan_id, user_id, window_k, days) values (pg_temp.plan_of(12), pg_temp.u(12), 1, 2);
delete from public.coach_plans where id = pg_temp.plan_of(12);
select is((select count(*)::int from public.coach_plan_extensions where user_id = pg_temp.u(12)), 0, 'rows cascade with the plan');
delete from auth.users where id = pg_temp.u(10);
select is((select count(*)::int from public.coach_plan_extensions where user_id = pg_temp.u(10)), 0, 'rows cascade with the account');
select throws_ok($$insert into public.coach_plan_extensions (plan_id, user_id, window_k, days)
                    values (gen_random_uuid(), pg_temp.u(1), 0, 15)$$, '23514', null, 'days above 14 are rejected by the table');

select * from finish();
rollback;
