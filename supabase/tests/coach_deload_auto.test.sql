-- pgTAP: deloads that apply on their own, cancel and request (T-230, US-100; rules in planning/04 section 9.10).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(51);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('cb000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.sq() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'squat' order by key limit 1 $$;
create function pg_temp.iso() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = 'isolation' order by key limit 1 $$;
create function pg_temp.at(p_day date, p_hour int default 10) returns timestamptz language sql as $$
  select (p_day + make_interval(hours => p_hour)) at time zone 'utc' $$;

-- A block of 4 weeks with one routine per week and a folder.
create function pg_temp.mkplan(u int, p_status text, p_start date, p_tag text default 'cur') returns uuid
language plpgsql as $$
declare v_plan uuid := gen_random_uuid(); v_folder uuid; v_r uuid; w int;
begin
  insert into public.routine_folders (user_id, name) values (pg_temp.u(u), 'Bloque ' || p_tag) returning id into v_folder;
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, folder_id)
  values (v_plan, pg_temp.u(u), p_start, 'muscle', '{}', p_status, v_folder);
  insert into public.routines (user_id, name) values (pg_temp.u(u), 'R' || u || p_tag) returning id into v_r;
  for w in 1..4 loop
    insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, routine_id)
    values (v_plan, pg_temp.u(u), w, 1, 'Sesion', '[]', v_r);
  end loop;
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
-- Squat in plan weeks 1-3 of the current block (starts 21 days ago).
create function pg_temp.weeks3(u int, w1 numeric, w2 numeric, w3 numeric) returns void language plpgsql as $$
begin
  perform pg_temp.hist(u, pg_temp.today() - 20, pg_temp.sq(), w1, 5);
  perform pg_temp.hist(u, pg_temp.today() - 13, pg_temp.sq(), w2, 5);
  perform pg_temp.hist(u, pg_temp.today() - 6, pg_temp.sq(), w3, 5);
end $$;
-- An earlier block of 4 training weeks (isolation work only, so it never feeds the e1RM trigger), starting 49 days ago.
create function pg_temp.prior(u int, p_skip int default 0, p_deload_week int default 0) returns void language plpgsql as $$
declare v_plan uuid := pg_temp.mkplan(u, 'superseded', pg_temp.today() - 49, 'old'); w int;
begin
  for w in 1..4 loop
    if w <> p_skip then
      perform pg_temp.hist(u, pg_temp.today() - 49 + 7 * (w - 1) + 1, pg_temp.iso(), 20, 10);
    end if;
  end loop;
  if p_deload_week > 0 then
    update public.coach_plan_sessions set deload = true where plan_id = v_plan and week = p_deload_week;
  end if;
end $$;
create function pg_temp.ev(n int) returns jsonb language plpgsql as $$
begin
  perform pg_temp.as_user(n);
  return public.evaluate_coach_deload(pg_temp.today());
end $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) and status = 'accepted' $$;
create function pg_temp.dl_weeks(n int) returns jsonb language sql as $$
  select coalesce(jsonb_agg(distinct week order by week), '[]'::jsonb)
  from public.coach_plan_sessions where plan_id = pg_temp.plan_of(n) and deload $$;
create function pg_temp.changes(n int) returns int language sql as $$
  select count(*)::int from public.coach_changes where user_id = pg_temp.u(n) and kind like 'deload%' $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'da' || n || '@example.com' from generate_series(1, 14) n;

-- Current blocks start 21 days ago, so today is plan week 4.
select pg_temp.mkplan(n, 'accepted', pg_temp.today() - 21) from unnest(array[1, 2, 3, 4, 5, 6, 9, 11, 14]) n;
select pg_temp.mkplan(7, 'accepted', pg_temp.today() - 28);    -- week 5
select pg_temp.mkplan(10, 'accepted', pg_temp.today() - 7);    -- week 2
select pg_temp.mkplan(12, 'accepted', pg_temp.today() - 14);   -- week 3

-- 1: no main lift improved (a tie is not an improvement): triggered. 14: same plus 7 training weeks: triggered wins.
select pg_temp.weeks3(1, 100, 100, 100);
select pg_temp.weeks3(14, 100, 100, 100);
select pg_temp.prior(14);
-- 2: improved in week 2, only 3 training weeks: nothing.
select pg_temp.weeks3(2, 100, 105, 100);
-- 3: improving, 4 earlier training weeks + 3 = 7 without a deload: scheduled.
select pg_temp.weeks3(3, 100, 105, 110);
select pg_temp.prior(3);
-- 4: as 3 but the earlier block had a deload in its week 2: only 2 + 3 = 5 training weeks since.
select pg_temp.weeks3(4, 100, 105, 110);
select pg_temp.prior(4, 0, 2);
-- 5: as 3 but the earlier block has a week without any workout: 3 + 3 = 6.
select pg_temp.weeks3(5, 100, 105, 110);
select pg_temp.prior(5, 4);
-- 6: no main lifts at all, no history. 9: equal lifts but a deload mark already on week 4 (accepted before T-230).
select pg_temp.weeks3(9, 100, 100, 100);
update public.coach_plan_sessions set deload = true where plan_id = pg_temp.plan_of(9) and week = 4;
-- 11: equal lifts, to be cancelled and re-evaluated.
select pg_temp.weeks3(11, 100, 100, 100);

-- ---------------------------------------------------------------------------
-- Structure and security
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.evaluate_coach_deload(date)', 'execute'), 'anon cannot evaluate');
select ok(not has_function_privilege('anon', 'public.cancel_coach_deload(uuid)', 'execute'), 'anon cannot cancel');
select ok(not has_function_privilege('anon', 'public.request_coach_deload()', 'execute'), 'anon cannot request');
select ok(not exists (select 1 from pg_proc where proname = 'decide_coach_deload'), 'the accept / decline RPC is gone');
select throws_ok($$select public.evaluate_coach_deload(current_date)$$, '28000', null, 'evaluate needs a signed-in user');
select throws_ok($$select public.cancel_coach_deload(gen_random_uuid())$$, '28000', null, 'cancel needs a signed-in user');
select throws_ok($$select public.request_coach_deload()$$, '28000', null, 'request needs a signed-in user');
select throws_ok(
  format($f$insert into public.coach_changes (user_id, kind, reason, status) values (%L, 'load_raise', 'x', 'applied')$f$, pg_temp.u(1)),
  '23514', null, 'a load change still needs an exercise');

set local role authenticated;

-- ---------------------------------------------------------------------------
-- Triggered deload (user 1)
-- ---------------------------------------------------------------------------
create temp table ev1 as select pg_temp.ev(1) as r;
grant select on ev1 to authenticated;
select is((select r->>'status' from ev1), 'applied', 'no improvement in weeks 1-3 applies the deload on its own');
select is((select r->'change'->>'kind' from ev1), 'deload_triggered', 'the change is a triggered deload');
select is((select r->'change'->>'reason' from ev1), 'no_e1rm_improvement', 'reason code of the trigger');
select is((select r->'change'->>'status' from ev1), 'applied', 'the row starts applied');
select is((select (r->'change'->'figures'->>'week') from ev1), '4', 'the deload is week 4');
select is(pg_temp.dl_weeks(1), '[4]'::jsonb, 'only plan week 4 is marked as deload');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = pg_temp.plan_of(1) and deload), 1,
  'every session of that week is marked (one session per week here)');
select is((select exercise_id from public.coach_changes where user_id = pg_temp.u(1)), null, 'the row has no exercise');
select is(pg_temp.ev(1)->>'reason', 'already_applied', 'a second evaluation applies nothing');
select is(pg_temp.changes(1), 1, 'one row per block after two evaluations');
select throws_ok($$select public.request_coach_deload()$$, 'P0001', 'coach_deload_not_available',
  'a deload is already planned: no request');

-- ---------------------------------------------------------------------------
-- Cancelling (user 1)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(2);
select throws_ok(format($f$select public.cancel_coach_deload(%L)$f$, (select (r->'change'->>'id') from ev1)), 'P0002', null,
  'another user cannot cancel it');
select is((select count(*)::int from public.coach_changes where user_id = pg_temp.u(1)), 0, 'another user does not see the row');
select pg_temp.as_user(1);
select is(public.cancel_coach_deload((select (r->'change'->>'id')::uuid from ev1))->>'status', 'undone', 'cancel marks the row undone');
select is(pg_temp.dl_weeks(1), '[]'::jsonb, 'the week goes back to the normal sets');
select ok((select decided_at is not null from public.coach_changes where user_id = pg_temp.u(1)), 'cancel stamps decided_at');
select throws_ok(format($f$select public.cancel_coach_deload(%L)$f$, (select (r->'change'->>'id') from ev1)), 'P0001',
  'coach_change_not_undoable', 'a second cancel is refused');
select is(pg_temp.ev(1)->>'reason', 'already_applied', 'a cancelled deload is not applied again');
select is(pg_temp.dl_weeks(1), '[]'::jsonb, 'the week stays normal after re-evaluating');
select throws_ok($$select public.request_coach_deload()$$, 'P0001', 'coach_deload_no_next_week',
  'week 4 of 4 has no next week to ask for');

-- ---------------------------------------------------------------------------
-- Scheduled deload and the 7 training weeks
-- ---------------------------------------------------------------------------
select is(pg_temp.ev(3)->'change'->>'kind', 'deload_scheduled', 'improving but 7 training weeks without a deload: scheduled');
select is((select reason from public.coach_changes where user_id = pg_temp.u(3)), 'seven_weeks_without_deload', 'reason code of the schedule');
select is((select figures->>'training_weeks' from public.coach_changes where user_id = pg_temp.u(3)), '7', 'figures hold the training weeks');
select is(pg_temp.dl_weeks(3), '[4]'::jsonb, 'the scheduled deload marks week 4');
select is(pg_temp.ev(14)->'change'->>'kind', 'deload_triggered', 'the trigger wins over the schedule');
select is(pg_temp.ev(4)->>'reason', 'improved', 'a deload in the earlier block resets the count');
select is(pg_temp.ev(5)->>'reason', 'improved', 'a week without workouts is not a training week');
select is(pg_temp.ev(2)->>'reason', 'improved', 'one main lift that improved means no deload');
select is(pg_temp.ev(6)->>'reason', 'no_main_lifts', 'no main lift and no history: nothing');
select is(pg_temp.changes(2) + pg_temp.changes(4) + pg_temp.changes(5) + pg_temp.changes(6), 0, 'no row when nothing was applied');
select is(pg_temp.ev(9)->>'reason', 'already_applied', 'a deload mark already in the block counts as the block''s deload');
select is(pg_temp.ev(7)->>'reason', 'past_week_4', 'after week 4 nothing is applied');
select is(pg_temp.ev(8)->>'reason', 'no_block', 'a user without a plan gets none');
select is(pg_temp.ev(10)->>'reason', 'too_early', 'before week 4 nothing is applied');

-- ---------------------------------------------------------------------------
-- Asking for a deload (user 10 is in week 2, user 12 in week 3)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
create temp table rq as select public.request_coach_deload() as r;
grant select on rq to authenticated;
select is((select r->>'kind' from rq), 'deload_user', 'the request is a user deload');
select is(pg_temp.dl_weeks(10), '[3]'::jsonb, 'the next plan week becomes the deload week');
select throws_ok($$select public.request_coach_deload()$$, 'P0001', 'coach_deload_not_available', 'only one deload per block');
select is(public.cancel_coach_deload((select (r->>'id')::uuid from rq))->>'status', 'undone', 'a requested deload can be cancelled');
select is(public.request_coach_deload()->'figures'->>'week', '3', 'after cancelling the user can ask again');
select pg_temp.as_user(12);
select is(public.request_coach_deload()->'figures'->>'week', '4', 'in week 3 the request targets week 4');
select throws_ok($$select public.request_coach_deload()$$, 'P0001', 'coach_deload_not_available', 'a second request is refused');
select pg_temp.as_user(13);
select throws_ok($$select public.request_coach_deload()$$, 'P0001', 'coach_deload_no_block', 'no accepted block: no request');

-- ---------------------------------------------------------------------------
-- Removing the block detaches the deload rows (user 10)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(10);
select public.remove_coach_block(pg_temp.plan_of(10));
select is((select count(*)::int from public.coach_changes where user_id = pg_temp.u(10) and plan_id is null and kind = 'deload_user'), 2,
  'the removed block''s deload rows stay as history without a plan');

select * from finish();
rollback;
