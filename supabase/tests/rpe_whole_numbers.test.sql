-- pgTAP: RPE is a whole number 1-10 (T-097g, ADR-56, US-30).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(16);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id, row_number() over (order by key) as n from public.exercises where not is_custom limit 2;
grant select on cat to authenticated;

insert into public.routines (id, user_id, name) values
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Rutina A');
insert into public.workout_sessions (id, user_id, name) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Sesion A');

-- Rounding rule used by the migration (numeric round is half away from zero).
select is(round(7.5::numeric), 8::numeric, 'a stored 7.5 is migrated to 8');
select is(round(6.5::numeric), 7::numeric, 'a stored 6.5 is migrated to 7');

-- workout_sets.rpe
select lives_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 1, 1)$$,
  'set rpe 1 accepted');
select lives_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 2, 10)$$,
  'set rpe 10 accepted');
select lives_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, null)$$,
  'set rpe null accepted');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 4, 7.5)$$,
  '23514', null, 'set rpe 7.5 rejected on insert');
select throws_ok($$update public.workout_sets set rpe = 6.5
  where session_id = 'a1000000-0000-4000-8000-000000000001' and set_index = 1$$,
  '23514', null, 'set rpe 6.5 rejected on update');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 5, 0)$$,
  '23514', null, 'set rpe 0 rejected');
select throws_ok($$insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, rpe)
  values ('a1000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 6, 11)$$,
  '23514', null, 'set rpe 11 rejected');

-- routine_exercises.target_rpe
select lives_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps, target_rpe)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 0, 3, 8, 10)$$,
  'target_rpe 10 accepted');
select lives_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps, target_rpe)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 2), 1, 3, 8, null)$$,
  'target_rpe null accepted');
select throws_ok($$insert into public.routine_exercises (routine_id, exercise_id, position, target_sets, target_reps, target_rpe)
  values ('a0000000-0000-4000-8000-000000000001', (select id from cat where n = 1), 2, 3, 8, 8.5)$$,
  '23514', null, 'target_rpe 8.5 rejected');
select throws_ok($$update public.routine_exercises set target_rpe = 0
  where routine_id = 'a0000000-0000-4000-8000-000000000001' and position = 0$$,
  '23514', null, 'target_rpe 0 rejected');
select throws_ok($$update public.routine_exercises set target_rpe = 11
  where routine_id = 'a0000000-0000-4000-8000-000000000001' and position = 0$$,
  '23514', null, 'target_rpe 11 rejected');

-- RLS unchanged: Bea cannot read or update Ana's RPE rows.
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.workout_sets where rpe is not null), 0,
  'B cannot read A rpe rows');
update public.workout_sets set rpe = 5 where session_id = 'a1000000-0000-4000-8000-000000000001';
reset role;
select is((select rpe from public.workout_sets
  where session_id = 'a1000000-0000-4000-8000-000000000001' and set_index = 1), 1::numeric,
  'B cannot update A rpe rows');

select * from finish();
rollback;
