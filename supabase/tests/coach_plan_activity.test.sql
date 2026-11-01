-- pgTAP: set_coach_plan_cardio (T-181, US-79; rules in planning/04 section 8.8).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(24);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c7000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.nut(with_activity boolean) returns jsonb language sql as $$
  select jsonb_build_object('target_kcal', 2200, 'calorie_adjustment', -400, 'protein_pct', 33, 'carbs_pct', 42,
    'fat_pct', 25, 'protein_g', 176, 'carbs_g', 231, 'fat_g', 61)
    || case when with_activity then jsonb_build_object('activity', jsonb_build_object(
      'cardio_min_week', 150, 'cardio_edited', false, 'cardio_band', jsonb_build_array(150, 300),
      'steps_day', 7000, 'steps_basis', 'floor', 'steps_avg', null)) else '{}'::jsonb end $$;
create function pg_temp.minutes(p uuid) returns int language sql as $$
  select (nutrition->'activity'->>'cardio_min_week')::int from public.coach_plans where id = p $$;
create function pg_temp.other_than_activity(p uuid) returns jsonb language sql as $$
  select nutrition - 'activity' from public.coach_plans where id = p $$;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'cardio' || n || '@example.com' from generate_series(1, 4) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status) values
  ('c7000000-0000-4000-8000-0000000000a1', pg_temp.u(1), current_date, 'fat_loss', pg_temp.nut(true), 'proposed'),
  ('c7000000-0000-4000-8000-0000000000a2', pg_temp.u(2), current_date, 'fat_loss', pg_temp.nut(true), 'accepted'),
  ('c7000000-0000-4000-8000-0000000000a3', pg_temp.u(3), current_date, 'fat_loss', pg_temp.nut(false), 'proposed'),
  ('c7000000-0000-4000-8000-0000000000a4', pg_temp.u(4), current_date - 30, 'fat_loss', pg_temp.nut(true), 'superseded'),
  ('c7000000-0000-4000-8000-0000000000a5', pg_temp.u(4), current_date - 60, 'fat_loss', pg_temp.nut(true), 'discarded');
insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, exercises)
values ('c7000000-0000-4000-8000-0000000000a1', pg_temp.u(1), 1, 1, 'Tren superior', '[]');
insert into public.routines (user_id, name) values (pg_temp.u(1), 'Rutina intacta');

select ok(not has_function_privilege('anon', 'public.set_coach_plan_cardio(uuid,int)', 'execute'), 'anon cannot execute the RPC');
select ok(has_function_privilege('authenticated', 'public.set_coach_plan_cardio(uuid,int)', 'execute'), 'authenticated can execute the RPC');

-- Unauthenticated
select set_config('request.jwt.claims', '', true);
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', 90)$$, '28000', null,
  'no session is refused');

-- Own proposed plan
set local role authenticated;
select pg_temp.as_user(1);
select is((select (public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', 90)->'activity'->>'cardio_min_week')::int),
  90, 'a proposed plan accepts 90 and returns the updated nutrition');
select is(pg_temp.minutes('c7000000-0000-4000-8000-0000000000a1'), 90, '90 is stored');
select is((select (nutrition->'activity'->>'cardio_edited')::boolean from public.coach_plans
  where id = 'c7000000-0000-4000-8000-0000000000a1'), true, 'the edit is flagged');
select is((select nutrition->'activity'->>'steps_day' from public.coach_plans where id = 'c7000000-0000-4000-8000-0000000000a1'),
  '7000', 'steps are untouched');
select is(pg_temp.other_than_activity('c7000000-0000-4000-8000-0000000000a1'), pg_temp.nut(false),
  'calories and macros are unchanged');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = 'c7000000-0000-4000-8000-0000000000a1'
  and name = 'Tren superior' and exercises = '[]'), 1, 'planned sessions are unchanged');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1) and name = 'Rutina intacta'), 1,
  'saved routines are unchanged');
select lives_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', 0)$$, '0 is accepted');
select lives_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', 600)$$, '600 is accepted');
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', -5)$$, '22023', null, '-5 is rejected');
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', 7)$$, '22023', null, '7 is rejected');
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', 601)$$, '22023', null, '601 is rejected');
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a1', null)$$, '22023', null, 'null is rejected');
select is(pg_temp.minutes('c7000000-0000-4000-8000-0000000000a1'), 600, 'a rejected value leaves the previous one');

-- Cross-user isolation: user 1 cannot edit user 2's plan
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a2', 60)$$, 'P0002', null,
  'another user plan is not found');
reset role;
select is(pg_temp.minutes('c7000000-0000-4000-8000-0000000000a2'), 150, 'the other user plan is unchanged');

-- Accepted plan
set local role authenticated;
select pg_temp.as_user(2);
select lives_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a2', 120)$$, 'an accepted plan can be edited');
select is(pg_temp.minutes('c7000000-0000-4000-8000-0000000000a2'), 120, '120 is stored on the accepted plan');

-- Pre-T-181 plan without activity
select pg_temp.as_user(3);
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a3', 120)$$, '22023', null,
  'a plan without activity is refused');

-- Closed plans
select pg_temp.as_user(4);
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a4', 120)$$, 'P0002', null,
  'a superseded plan is refused');
select throws_ok($$select public.set_coach_plan_cardio('c7000000-0000-4000-8000-0000000000a5', 120)$$, 'P0002', null,
  'a discarded plan is refused');

select * from finish();
rollback;
