-- pgTAP: coach_stall_diagnosis, coach_stalled_lifts and the 'stalls' key of coach_week_summary
-- (T-232, US-105; rules in planning/07-coach-design.md section 3 "Deload" and (7)).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(43);

-- ---------------------------------------------------------------------------
-- Fixture helpers (run as the migration owner)
-- ---------------------------------------------------------------------------
create function pg_temp.d0() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9320000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.plan_of(n int) returns uuid language sql as $$
  select id from public.coach_plans where user_id = pg_temp.u(n) $$ security definer;
create function pg_temp.bench() returns uuid language sql as $$
  select id from public.exercises
  where not is_custom and movement_pattern = 'horizontal_push' and muscle_group = 'chest' order by key limit 1 $$;
create function pg_temp.diag(n int, wk int) returns jsonb language sql as $$
  select public.coach_stall_diagnosis(pg_temp.plan_of(n), wk) $$;
create function pg_temp.cause(n int, wk int) returns text language sql as $$
  select pg_temp.diag(n, wk)->0->>'cause' $$;
create function pg_temp.summary() returns jsonb language sql as $$ select public.coach_week_summary(pg_temp.d0()) $$;

-- A user with an accepted 4-week plan that started 31 days ago (weeks 1-4 are all past) and the bench press as the only lift.
-- Options (jsonb): weights = weekly top weight per plan week (null: no workout that week), sess = workouts per week,
-- sets = working sets per week, planned = sets the plan gives chest, intention, deload = deload weeks, prot_days,
-- prot_g (per day), nights, sleep_min. Defaults: three flat weeks, 2 workouts of 10 sets a week, 10 complete diary days at
-- 160 g with a weight of 100 kg (1,6 g/kg), 10 nights of 7 h: no check fires, so the cause is "none".
create function pg_temp.mk(n int, o jsonb) returns void language plpgsql as $$
declare
  v_plan    uuid := gen_random_uuid();
  v_start   date := pg_temp.d0() - 31;
  v_weights numeric[] := array(select jsonb_array_elements_text(coalesce(o->'weights', '[100,100,100]'))::numeric);
  v_sets    int := coalesce((o->>'sets')::int, 10);
  v_sess    int;
  v_sid     uuid;
  k int; j int; i int; v_n int;
begin
  update public.profiles set onboarding_completed = true where id = pg_temp.u(n);
  insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status, accepted_at)
  values (v_plan, pg_temp.u(n), v_start, coalesce(o->>'intention', 'muscle')::public.training_intention,
          jsonb_build_object('rate_kg_week', 0, 'target_kcal', 2500, 'protein_g', 160), 'accepted', now());
  for k in 1..4 loop
    for j in 1..2 loop
      insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises, deload)
      values (v_plan, pg_temp.u(n), k, j, 'Sesion ' || j,
              case when j = 1 then jsonb_build_array(jsonb_build_object('exercise_id', pg_temp.bench(),
                                                                         'sets', coalesce((o->>'planned')::int, 12)))
                   else '[]'::jsonb end,
              coalesce(o->'deload', '[]'::jsonb) @> to_jsonb(k));
    end loop;
  end loop;

  for k in 1..coalesce(array_length(v_weights, 1), 0) loop
    if v_weights[k] is not null then
      v_sess := coalesce((o->'sess'->>(k - 1))::int, 2);
      for j in 0..v_sess - 1 loop
        v_sid := gen_random_uuid();
        insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
          (v_sid, pg_temp.u(n), 'W' || k, 'completed',
           ((v_start + 7 * (k - 1) + j)::timestamp + interval '10 hours') at time zone 'UTC',
           ((v_start + 7 * (k - 1) + j)::timestamp + interval '11 hours') at time zone 'UTC');
        v_n := v_sets / v_sess + case when j < v_sets % v_sess then 1 else 0 end;
        insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
        select v_sid, pg_temp.bench(), 0, s, 'working', case when s = 1 and j = 0 then v_weights[k] else 50 end,
               case when s = 1 and j = 0 then 1 else 5 end,
               ((v_start + 7 * (k - 1) + j)::timestamp + interval '10 hours 5 minutes') at time zone 'UTC'
        from generate_series(1, v_n) s;
        if j = 0 then
          update public.coach_plan_sessions set session_id = v_sid
            where plan_id = v_plan and week = k and session_index = 1;
        end if;
      end loop;
    end if;
  end loop;

  insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select pg_temp.u(n), v_start + d,
         (select id from public.meal_slots where user_id = pg_temp.u(n) and code = 'breakfast'),
         '0ff00000-0000-4000-8000-0000000000e2', 100, 'Alimento', 2000, coalesce((o->>'prot_g')::numeric, 160), 10, 10
  from generate_series(0, coalesce((o->>'prot_days')::int, 10) - 1) d;
  insert into public.body_measurements (user_id, weight_kg, source, measured_at)
  select pg_temp.u(n), 100, 'manual', ((v_start + d)::timestamp + interval '12 hours') at time zone 'UTC'
  from unnest(array[0, 7, 14]) d;
  for i in 0..coalesce((o->>'nights')::int, 10) - 1 loop
    insert into public.sleep_sessions (user_id, started_at, ended_at, minutes, external_id)
    values (pg_temp.u(n), ((v_start + i)::timestamp - interval '1 hour') at time zone 'UTC',
            ((v_start + i)::timestamp + interval '7 hours') at time zone 'UTC',
            coalesce((o->>'sleep_min')::int, 420), 'sl-' || n || '-' || i);
  end loop;
end $$;

grant execute on all functions in schema pg_temp to authenticated, anon;

insert into auth.users (id, email)
select pg_temp.u(n), 'stall' || n || '@example.com' from generate_series(1, 19) n;
insert into public.foods (id, source, name, kcal_100) values ('0ff00000-0000-4000-8000-0000000000e2', 'off', 'Alimento e2', 100);

select pg_temp.mk(1, '{}');                                                          -- base: nothing fires
select pg_temp.mk(2, '{"sess":[1,1,2]}');                                            -- 4 workouts in 3 weeks
select pg_temp.mk(3, '{"sets":7}');
select pg_temp.mk(4, '{"sets":7,"intention":"strength"}');
select pg_temp.mk(5, '{"sets":7,"planned":8}');                                      -- the plan gives chest 8 sets
select pg_temp.mk(6, '{"prot_days":9}');
select pg_temp.mk(7, '{"prot_g":159}');
select pg_temp.mk(8, '{"nights":9,"sleep_min":300}');
select pg_temp.mk(9, '{"sleep_min":414}');
select pg_temp.mk(10, '{"sess":[1,1,2],"sleep_min":300}');
select pg_temp.mk(11, '{"prot_days":9,"sleep_min":300}');
select pg_temp.mk(12, '{"prot_g":100,"sleep_min":300}');
select pg_temp.mk(13, '{"weights":[100,100,100.5]}');
select pg_temp.mk(14, '{"weights":[100,100.5,100]}');
select pg_temp.mk(15, '{"weights":[100,100,200,100],"deload":[3]}');
select pg_temp.mk(16, '{"weights":[100,100]}');
select pg_temp.mk(17, '{"weights":[100,100,100,100]}');
select pg_temp.mk(18, '{"weights":[100,110,100,100]}');
-- User 19 has no plan.

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_stall_diagnosis(uuid, integer)', 'execute')
      and not has_function_privilege('anon', 'public.coach_stalled_lifts(uuid, integer)', 'execute'),
  'anon cannot execute the stall functions');
select ok(has_function_privilege('authenticated', 'public.coach_stall_diagnosis(uuid, integer)', 'execute')
      and has_function_privilege('authenticated', 'public.coach_stalled_lifts(uuid, integer)', 'execute'),
  'authenticated can execute the stall functions');

set local role authenticated;

-- ---------------------------------------------------------------------------
-- Causes: each fires only its own check; the rest of the data is fine
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select is(pg_temp.cause(1, 3), 'none', 'a flat lift with enough data and no low figure has no clear cause');
select is(pg_temp.diag(1, 3)->0->>'protein_gate_missing', 'false', 'with 10 complete diary days the protein gate is not missing');
select is(pg_temp.diag(1, 3)->0->>'exercise_id', pg_temp.bench()::text, 'the element names the stalled lift');

select pg_temp.as_user(2);
select is(pg_temp.cause(2, 3), 'sessions', '4 workouts in 3 weeks (1,3 a week) fire the sessions check');
select is((pg_temp.diag(2, 3)->0->>'avg_sessions')::numeric, 1.3, 'the sessions average is carried');
select is((pg_temp.diag(2, 3)->0->>'plan_days')::int, 2, 'the days the plan gives are carried');

select pg_temp.as_user(3);
select is(pg_temp.cause(3, 3), 'sets', '7 sets a week of the lift''s group fire the sets check (10 does not: see the base user)');
select is((pg_temp.diag(3, 3)->0->>'avg_sets')::numeric, 7.0, 'the sets average is carried');
select is(pg_temp.diag(3, 3)->0->>'group', 'chest', 'the group of the lift is carried');

select pg_temp.as_user(4);
select is(pg_temp.cause(4, 3), 'none', '"Ganar fuerza" skips the sets check');

select pg_temp.as_user(5);
select is(pg_temp.cause(5, 3), 'maintain', 'a plan that gives the group fewer than 10 sets says so (Mantener)');
select is(pg_temp.diag(5, 3)->0->>'group', 'chest', 'the Mantener variant carries the group');

select pg_temp.as_user(6);
select is(pg_temp.cause(6, 3), 'none', '9 complete diary days: the protein check does not run');
select is(pg_temp.diag(6, 3)->0->>'protein_gate_missing', 'true', 'and the missing food records are flagged');

select pg_temp.as_user(7);
select is(pg_temp.cause(7, 3), 'protein', '10 days at 1,59 g/kg fire the protein check (1,60 does not: see the base user)');
select is((pg_temp.diag(7, 3)->0->>'protein_g_per_kg')::numeric, 1.59, 'the g/kg figure is carried');
select is((pg_temp.diag(7, 3)->0->>'target_g')::int, 160, 'the plan''s protein target is carried');
select is((pg_temp.diag(7, 3)->0->>'avg_protein_g')::int, 159, 'the logged average is carried');

select pg_temp.as_user(8);
select is(pg_temp.cause(8, 3), 'none', '9 nights of short sleep: the sleep check does not run');

select pg_temp.as_user(9);
select is(pg_temp.cause(9, 3), 'sleep', '10 nights at 6,9 h fire the sleep check (7,0 does not: see the base user)');
select is((pg_temp.diag(9, 3)->0->>'avg_sleep_h')::numeric, 6.9, 'the sleep average is carried');

select pg_temp.as_user(10);
select is(pg_temp.cause(10, 3), 'sessions', 'sessions and sleep both low: only the sessions cause is shown');
select pg_temp.as_user(11);
select is(pg_temp.cause(11, 3), 'sleep', 'a missing protein gate does not stop the sleep check');
select pg_temp.as_user(12);
select is(pg_temp.cause(12, 3), 'protein', 'protein and sleep both low: protein comes first');

-- ---------------------------------------------------------------------------
-- The stall test
-- ---------------------------------------------------------------------------
select pg_temp.as_user(13);
select is(pg_temp.diag(13, 3), '[]'::jsonb, 'w3 above w1 by the smallest step is an improvement');
select pg_temp.as_user(14);
select is(pg_temp.diag(14, 3), '[]'::jsonb, 'w2 above w1 is an improvement');
select pg_temp.as_user(15);
select is(jsonb_array_length(pg_temp.diag(15, 4)), 1, 'a deload week is left out: weeks 1, 2 and 4 are the window and are flat');
select is(pg_temp.diag(15, 3), '[]'::jsonb, 'and with the deload week left out only two weeks are done by week 3');
select pg_temp.as_user(16);
select is(pg_temp.diag(16, 3), '[]'::jsonb, 'fewer than 3 weeks done: no stall');

-- ---------------------------------------------------------------------------
-- First found: the summary shows a stall once
-- ---------------------------------------------------------------------------
select pg_temp.as_user(17);
select is(jsonb_array_length(pg_temp.diag(17, 3)), 1, 'flat for weeks 1-3: stalled at week 3');
select is(jsonb_array_length(pg_temp.diag(17, 4)), 1, 'and still stalled at week 4');
select is((pg_temp.summary())->'stalls', '[]'::jsonb, 'the week 4 summary does not repeat a stall found at week 3');
select is(jsonb_typeof((pg_temp.summary())->'stalls'), 'array', 'the summary always carries the stalls key');

select pg_temp.as_user(18);
select is(pg_temp.diag(18, 3), '[]'::jsonb, 'improved at week 3');
select is(jsonb_array_length(pg_temp.diag(18, 4)), 1, 'stalled at week 4 (weeks 2-4: 110, 100, 100)');
select is(jsonb_array_length((pg_temp.summary())->'stalls'), 1, 'the week 4 summary shows the stall first found there');
select is((pg_temp.summary())->'stalls', pg_temp.diag(18, 4), 'the summary element is the diagnosis element');
select is(pg_temp.diag(18, 4), pg_temp.diag(18, 4), 'the same input twice gives the same output');

-- ---------------------------------------------------------------------------
-- Isolation
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select is(pg_temp.diag(17, 4), '[]'::jsonb, 'another user''s plan returns nothing');
select is((select count(*)::int from public.coach_stalled_lifts(pg_temp.plan_of(17), 4)), 0, 'and no lifts');
select set_config('request.jwt.claims', '{}', true);
select is(public.coach_stall_diagnosis(pg_temp.plan_of(17), 4), '[]'::jsonb, 'without an authenticated user nothing is read');

select * from finish();
rollback;
