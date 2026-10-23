-- pgTAP: weekly_muscle_volume (T-048b, US-15): week boundaries (Mon-Sun, UTC), volume per muscle group,
-- excluded sets/sessions, the p_from window, cross-user isolation and execute privileges.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(13);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- Two chest exercises (c1, c2) and one back exercise (b1) from the catalog.
create temp table cat as
  select 'c1' as k, (select id from public.exercises where not is_custom and metric_type = 'weight_reps' and muscle_group = 'chest' order by key limit 1) as id
  union all
  select 'c2', (select id from public.exercises where not is_custom and metric_type = 'weight_reps' and muscle_group = 'chest' order by key offset 1 limit 1)
  union all
  select 'b1', (select id from public.exercises where not is_custom and metric_type = 'weight_reps' and muscle_group = 'back' order by key limit 1);
grant select on cat to authenticated;

-- 2026-10-12 is a Monday. Ana:
--   s1 Sun 2026-10-11 23:59 UTC (week of 10-05), s2 Mon 2026-10-12 00:00 UTC (week of 10-12),
--   s3 old (2026-08-03, before p_from), s4 discarded, s5 in progress (both week of 10-12).
-- Bea: one completed session in the week of 10-12.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a4000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Sun', 'completed',
   '2026-10-11 23:59+00', '2026-10-12 00:30+00'),
  ('a4000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Mon', 'completed',
   '2026-10-12 00:00+00', '2026-10-12 01:00+00'),
  ('a4000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'Old', 'completed',
   '2026-08-03 10:00+00', '2026-08-03 11:00+00'),
  ('a4000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'Discarded', 'discarded',
   '2026-10-13 10:00+00', '2026-10-13 11:00+00'),
  ('a4000000-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'Open', 'in_progress',
   '2026-10-14 10:00+00', null),
  ('b4000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00');

insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  -- s1: chest 100 x 5 (500) -> week 10-05
  ('a4000000-0000-4000-8000-000000000001', (select id from cat where k = 'c1'), 0, 1, 'working', 100, 5, '2026-10-12 00:10+00'),
  -- s2: chest c1 3 x 100 x 5 (1500) + warm-up (ignored) + uncompleted set (ignored); chest c2 60 x 10 (600); back 80 x 8 (640)
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'c1'), 0, 1, 'working', 100, 5, '2026-10-12 00:10+00'),
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'c1'), 0, 2, 'working', 100, 5, '2026-10-12 00:15+00'),
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'c1'), 0, 3, 'working', 100, 5, '2026-10-12 00:20+00'),
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'c1'), 0, 4, 'warm_up', 40, 10, '2026-10-12 00:05+00'),
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'c1'), 0, 5, 'working', 100, 5, null),
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'c2'), 1, 1, 'working', 60, 10, '2026-10-12 00:30+00'),
  ('a4000000-0000-4000-8000-000000000002', (select id from cat where k = 'b1'), 2, 1, 'working', 80, 8, '2026-10-12 00:40+00'),
  -- s3 (before p_from), s4 (discarded), s5 (in progress): all with a completed chest set
  ('a4000000-0000-4000-8000-000000000003', (select id from cat where k = 'c1'), 0, 1, 'working', 100, 5, '2026-08-03 10:10+00'),
  ('a4000000-0000-4000-8000-000000000004', (select id from cat where k = 'c1'), 0, 1, 'working', 100, 5, '2026-10-13 10:10+00'),
  ('a4000000-0000-4000-8000-000000000005', (select id from cat where k = 'c1'), 0, 1, 'working', 100, 5, '2026-10-14 10:10+00'),
  -- Bea: chest 200 x 5
  ('b4000000-0000-4000-8000-000000000001', (select id from cat where k = 'c1'), 0, 1, 'working', 200, 5, '2026-10-12 10:10+00');

-- ---------------------------------------------------------------------------
-- Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  $$select week_start, muscle_group::text, total_volume from public.weekly_muscle_volume('2026-09-01') order by 1, 2$$,
  $$values (date '2026-10-05', 'chest', 500::numeric),
           (date '2026-10-12', 'back', 640::numeric),
           (date '2026-10-12', 'chest', 2100::numeric)$$,
  'rows are per Monday week and muscle group; Sunday 23:59 UTC and Monday 00:00 UTC fall in different weeks');

select is((select total_volume from public.weekly_muscle_volume('2026-09-01')
  where week_start = '2026-10-12' and muscle_group = 'chest'), 2100::numeric,
  'two chest exercises sum into one row, ignoring warm-up and uncompleted sets (1500 + 600)');
select is((select total_volume from public.weekly_muscle_volume('2026-09-01')
  where week_start = '2026-10-12' and muscle_group = 'back'), 640::numeric,
  'a different muscle group gets its own row (80 x 8)');
select is((select count(*)::int from public.weekly_muscle_volume('2026-09-01')), 3,
  'discarded and in-progress sessions add no rows');
select is((select count(*)::int from public.weekly_muscle_volume('2026-08-01')
  where week_start = '2026-08-03'), 1,
  'an earlier p_from includes the older session');
select is((select count(*)::int from public.weekly_muscle_volume('2026-09-01')
  where week_start = '2026-08-03'), 0,
  'p_from excludes sessions before it');
select is((select count(*)::int from public.weekly_muscle_volume('2026-10-12')), 2,
  'p_from equal to a session date includes that day');

-- ---------------------------------------------------------------------------
-- "Incluir calentamiento" (T-090): the warm-up 40 x 10 (400) counts when the flag is on, for any week
-- ---------------------------------------------------------------------------
reset role;
insert into public.training_preferences (user_id, warm_up_included) values
  ('11111111-1111-4111-8111-111111111111', true);
insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at) values
  ('a4000000-0000-4000-8000-000000000003', (select id from cat where k = 'c1'), 1, 1, 'warm_up', 50, 10, '2026-08-03 10:05+00');
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select total_volume from public.weekly_muscle_volume('2026-09-01')
  where week_start = '2026-10-12' and muscle_group = 'chest'), 2500::numeric,
  'on: the warm-up joins the chest volume (2100 + 400)');
select is((select total_volume from public.weekly_muscle_volume('2026-08-01')
  where week_start = '2026-08-03'), 1000::numeric,
  'on: an old session also changes (500 + warm-up 500)');

update public.training_preferences set warm_up_included = false
  where user_id = '11111111-1111-4111-8111-111111111111';
select is((select total_volume from public.weekly_muscle_volume('2026-08-01')
  where week_start = '2026-08-03'), 500::numeric,
  'off again: old session back to 500 (recalculated on read)');

-- ---------------------------------------------------------------------------
-- Bea only sees her own volume
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select results_eq(
  $$select week_start, muscle_group::text, total_volume from public.weekly_muscle_volume('2026-09-01')$$,
  $$values (date '2026-10-12', 'chest', 1000::numeric)$$,
  'another user never sees Ana''s volume');

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
reset role;
select ok(not has_function_privilege('anon', 'public.weekly_muscle_volume(date)', 'execute'),
  'anon cannot execute weekly_muscle_volume');
select ok(has_function_privilege('authenticated', 'public.weekly_muscle_volume(date)', 'execute'),
  'authenticated can execute weekly_muscle_volume');

select * from finish();
rollback;
