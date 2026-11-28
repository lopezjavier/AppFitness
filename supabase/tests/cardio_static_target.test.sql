-- pgTAP: the cardio target of a routine is static (T-332, US-151). Covers save_workout_as_routine and
-- update_routine_from_session for exercises with movement_pattern = 'cardio'.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(28);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select
    (select id from public.exercises where key = 'walking') as walk,
    (select id from public.exercises where key = 'running') as run,
    (select id from public.exercises where key = 'stationary-bike') as bike,
    (select id from public.exercises where metric_type = 'duration' and movement_pattern is distinct from 'cardio'
       and not is_custom order by key limit 1) as hold,
    (select id from public.exercises where metric_type = 'weight_reps' and not is_custom order by key limit 1) as lift,
    (select id from public.exercises where metric_type = 'weight_reps' and not is_custom order by key offset 1 limit 1) as lift2;
grant select on cat to authenticated;

-- Target seconds (set order) of one exercise inside the routine with that name.
create function public.t_sets(p_routine text, p_ex uuid, p_col text default 'd')
returns int[]
language sql
stable
as $$
  select array_agg(case p_col when 'd' then rs.target_duration_s else rs.target_reps end order by rs.set_index)
  from public.routines r
  join public.routine_exercises re on re.routine_id = r.id
  join public.routine_exercise_sets rs on rs.routine_exercise_id = re.id
  where r.name = p_routine and re.exercise_id = p_ex
$$;

select is((select movement_pattern::text from public.exercises where id = (select walk from cat)), 'cardio',
  'walking is a cardio exercise');
select isnt((select hold from cat), null, 'a non-cardio timed exercise exists for the hold cases');

-- ---------------------------------------------------------------------------
-- Fixtures (as postgres)
-- ---------------------------------------------------------------------------
-- RS: source routine planning walking 30 min. RU: routine for update (walking 30 min, bike legacy 3 x 15 min,
-- hold 60 s, lift).
insert into public.routines (id, user_id, name) values
  ('a0000000-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'RS'),
  ('a0000000-0000-4000-8000-0000000000a2', '11111111-1111-4111-8111-111111111111', 'RU');
insert into public.routine_exercises (id, routine_id, exercise_id, position, target_sets, target_reps) values
  ('e1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-0000000000a1', (select walk from cat), 0, 1, 1),
  ('e2000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-0000000000a2', (select walk from cat), 0, 1, 1),
  ('e2000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-0000000000a2', (select bike from cat), 1, 3, 1),
  ('e2000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-0000000000a2', (select hold from cat), 2, 1, 1),
  ('e2000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-0000000000a2', (select lift from cat), 3, 1, 8);
insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps, target_duration_s) values
  ('e1000000-0000-4000-8000-000000000001', 1, null, null, 1800),
  ('e2000000-0000-4000-8000-000000000001', 1, null, null, 1800),
  ('e2000000-0000-4000-8000-000000000002', 1, null, null, 900),
  ('e2000000-0000-4000-8000-000000000002', 2, null, null, 900),
  ('e2000000-0000-4000-8000-000000000002', 3, null, null, 900),
  ('e2000000-0000-4000-8000-000000000003', 1, null, null, 60),
  ('e2000000-0000-4000-8000-000000000004', 1, 40, 8, null);

insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at) values
  ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a1',
   'S planned', 'completed', '2026-02-01 10:00+00', '2026-02-01 11:00+00'),
  ('c0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', null,
   'S free 20:00', 'completed', '2026-02-02 10:00+00', '2026-02-02 11:00+00'),
  ('c0000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', null,
   'S free 20:29', 'completed', '2026-02-03 10:00+00', '2026-02-03 11:00+00'),
  ('c0000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', null,
   'S free 20:30', 'completed', '2026-02-04 10:00+00', '2026-02-04 11:00+00'),
  ('c0000000-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', null,
   'S free null', 'completed', '2026-02-05 10:00+00', '2026-02-05 11:00+00'),
  ('c0000000-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111111', null,
   'S free zero', 'completed', '2026-02-06 10:00+00', '2026-02-06 11:00+00'),
  ('c0000000-0000-4000-8000-000000000007', '11111111-1111-4111-8111-111111111111', null,
   'S free 5h', 'completed', '2026-02-07 10:00+00', '2026-02-07 11:00+00'),
  ('c0000000-0000-4000-8000-000000000008', '11111111-1111-4111-8111-111111111111', null,
   'S free mix', 'completed', '2026-02-08 10:00+00', '2026-02-08 11:00+00'),
  ('c0000000-0000-4000-8000-000000000009', '11111111-1111-4111-8111-111111111111', null,
   'S superset', 'completed', '2026-02-09 10:00+00', '2026-02-09 11:00+00'),
  ('c0000000-0000-4000-8000-00000000000a', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a2',
   'S update', 'completed', '2026-02-10 10:00+00', '2026-02-10 11:00+00');

-- exercise_position, set_index, duration_s per session (walking unless noted).
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, reps, duration_s, completed_at) values
  ('c0000000-0000-4000-8000-000000000001', (select walk from cat), 0, 1, 0, 2490, now()),
  ('c0000000-0000-4000-8000-000000000001', (select run from cat), 1, 1, 0, 1200, now()),
  ('c0000000-0000-4000-8000-000000000002', (select walk from cat), 0, 1, 0, 1200, now()),
  ('c0000000-0000-4000-8000-000000000003', (select walk from cat), 0, 1, 0, 1229, now()),
  ('c0000000-0000-4000-8000-000000000004', (select walk from cat), 0, 1, 0, 1230, now()),
  ('c0000000-0000-4000-8000-000000000005', (select walk from cat), 0, 1, 0, null, now()),
  ('c0000000-0000-4000-8000-000000000006', (select walk from cat), 0, 1, 0, 0, now()),
  ('c0000000-0000-4000-8000-000000000007', (select walk from cat), 0, 1, 0, 18000, now()),
  ('c0000000-0000-4000-8000-000000000008', (select lift from cat), 0, 1, 10, null, now()),
  ('c0000000-0000-4000-8000-000000000008', (select lift from cat), 0, 2, 12, null, now()),
  ('c0000000-0000-4000-8000-000000000008', (select hold from cat), 1, 1, 0, 45, now()),
  ('c0000000-0000-4000-8000-00000000000a', (select walk from cat), 0, 1, 0, 2490, now()),
  ('c0000000-0000-4000-8000-00000000000a', (select bike from cat), 1, 1, 0, 1000, now()),
  ('c0000000-0000-4000-8000-00000000000a', (select run from cat), 2, 1, 0, 1230, now()),
  ('c0000000-0000-4000-8000-00000000000a', (select hold from cat), 3, 1, 0, 90, now()),
  ('c0000000-0000-4000-8000-00000000000a', (select lift from cat), 4, 1, 6, null, now());
insert into public.workout_sets (session_id, exercise_id, exercise_position, superset_group, set_index, reps, completed_at) values
  ('c0000000-0000-4000-8000-000000000009', (select lift from cat), 0, 1, 1, 10, now()),
  ('c0000000-0000-4000-8000-000000000009', (select lift from cat), 0, 1, 2, 10, now()),
  ('c0000000-0000-4000-8000-000000000009', (select lift2 from cat), 1, 1, 1, 10, now());

insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at) values
  ('c0000000-0000-4000-8000-00000000000b', '11111111-1111-4111-8111-111111111111', null,
   'S superset cardio', 'completed', '2026-02-11 10:00+00', '2026-02-11 11:00+00'),
  ('c0000000-0000-4000-8000-00000000000c', '11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-0000000000a2',
   'S update superset cardio', 'completed', '2026-02-12 10:00+00', '2026-02-12 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, superset_group, set_index, reps, duration_s, completed_at) values
  ('c0000000-0000-4000-8000-00000000000b', (select lift from cat), 0, 1, 1, 10, null, now()),
  ('c0000000-0000-4000-8000-00000000000b', (select lift from cat), 0, 1, 2, 10, null, now()),
  ('c0000000-0000-4000-8000-00000000000b', (select walk from cat), 1, 1, 1, 0, 1800, now()),
  ('c0000000-0000-4000-8000-00000000000c', (select lift from cat), 0, 1, 1, 10, null, now()),
  ('c0000000-0000-4000-8000-00000000000c', (select lift from cat), 0, 1, 2, 10, null, now()),
  ('c0000000-0000-4000-8000-00000000000c', (select walk from cat), 1, 1, 1, 0, 1800, now());

-- ---------------------------------------------------------------------------
-- Cross-user isolation: B cannot read A's cardio session through either RPC
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select throws_ok($$select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000001', 'Robo')$$,
  'P0002', null, 'B cannot save A''s cardio session as a routine');
select throws_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-00000000000a')$$,
  'P0002', null, 'B cannot update A''s routine from A''s cardio session');
select is((select count(*)::int from public.routines), 0, 'B sees no routine after the rejected calls');

-- ---------------------------------------------------------------------------
-- save_workout_as_routine as A
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok($$select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000001', 'N planned')$$,
  'saving a cardio session planned at 30 min works');
select is(public.t_sets('N planned', (select walk from cat)), array[1800],
  'a cardio planned at 30 min keeps 30 min as one timed set after 41:30 done');
select is(public.t_sets('N planned', (select run from cat)), array[1200],
  'a cardio not in the source routine takes the time done');

select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000002', 'N 20:00');
select is(public.t_sets('N 20:00', (select walk from cat)), array[1200], 'free session 20:00 gives 20 min');
select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000003', 'N 20:29');
select is(public.t_sets('N 20:29', (select walk from cat)), array[1200], '20:29 rounds down to 20 min');
select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000004', 'N 20:30');
select is(public.t_sets('N 20:30', (select walk from cat)), array[1260], '20:30 rounds up to 21 min');
select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000005', 'N null');
select is(public.t_sets('N null', (select walk from cat)), array[60], 'a null time gives 1 min');
select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000006', 'N zero');
select is(public.t_sets('N zero', (select walk from cat)), array[60], 'a zero time gives 1 min');
select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000007', 'N 5h');
select is(public.t_sets('N 5h', (select walk from cat)), array[14400], '5 h is clamped to 240 min');

select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000008', 'N mix');
select is(public.t_sets('N mix', (select lift from cat), 'r'), array[10, 12], 'strength keeps its reps sets');
select is(public.t_sets('N mix', (select hold from cat), 'r'), array[1], 'a hold keeps the reps-set behaviour (clamped to 1)');

select public.save_workout_as_routine('c0000000-0000-4000-8000-000000000009', 'N superset');
select is((select count(*)::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'N superset' and re.superset_group is not null), 0,
  'a superset with different set counts still drops the group');

-- ---------------------------------------------------------------------------
-- update_routine_from_session as A
-- ---------------------------------------------------------------------------
select lives_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-00000000000a')$$,
  'updating a routine from a cardio session works');
select is(public.t_sets('RU', (select walk from cat)), array[1800],
  'the cardio target stays 30 min after 41:30 done');
select is(public.t_sets('RU', (select bike from cat)), array[900, 900, 900],
  'a legacy multi-set cardio keeps its sets as they were');
select is(public.t_sets('RU', (select run from cat)), array[1260],
  'a cardio new to the routine takes the time done (20:30 -> 21 min)');
select is(public.t_sets('RU', (select hold from cat)), array[90], 'a hold still takes the performed duration');
select is(public.t_sets('RU', (select lift from cat), 'r'), array[6], 'a strength exercise is updated as before');

-- Superset with a cardio row: cardio is excluded from the same-set-count rule and keeps its group.
select lives_ok(format($$select public.save_routine(jsonb_build_object('name', 'SR superset cardio', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'superset_group', 1,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 20, 'reps', 10), jsonb_build_object('weight_kg', 20, 'reps', 10))),
    jsonb_build_object('exercise_id', %L, 'superset_group', 1,
      'sets', jsonb_build_array(jsonb_build_object('duration_s', 1800))))))$$,
  (select lift from cat), (select walk from cat)), 'save_routine accepts a one-set cardio in a superset with a two-set lift');
select is((select count(*)::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'SR superset cardio' and re.superset_group = 1), 2,
  'save_routine keeps the superset group on both the lift and the cardio');
select throws_ok(format($$select public.save_routine(jsonb_build_object('name', 'SR bad superset', 'exercises', jsonb_build_array(
    jsonb_build_object('exercise_id', %L, 'superset_group', 1,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 20, 'reps', 10), jsonb_build_object('weight_kg', 20, 'reps', 10))),
    jsonb_build_object('exercise_id', %L, 'superset_group', 1,
      'sets', jsonb_build_array(jsonb_build_object('weight_kg', 20, 'reps', 10))))))$$,
  (select lift from cat), (select lift2 from cat)), '22023', null,
  'save_routine still rejects a superset of strength exercises with different set counts');
select public.save_workout_as_routine('c0000000-0000-4000-8000-00000000000b', 'N superset cardio');
select is((select count(*)::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'N superset cardio' and re.superset_group = 1), 2,
  'save_workout_as_routine keeps the group of a cardio next to a two-set lift');
select public.update_routine_from_session('c0000000-0000-4000-8000-00000000000c');
select is((select count(*)::int from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'RU' and re.superset_group = 1), 2,
  'update_routine_from_session keeps the group of a cardio next to a two-set lift');

select * from finish();
rollback;
