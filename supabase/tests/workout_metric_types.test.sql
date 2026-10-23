-- pgTAP: exercise metric types in the workout data (T-092, US-31): custom exercises accept every metric type,
-- last_performance returns the metric columns, volume follows the metric (body weight at read time) and
-- another user's data never leaks.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(16);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- Ana weighs 80 kg, then 74 kg (newest wins). Bea weighs 100 kg. Carl has no measurement.
insert into public.body_measurements (user_id, measured_at, weight_kg, source) values
  ('11111111-1111-4111-8111-111111111111', '2026-09-01 08:00+00', 80, 'manual'),
  ('11111111-1111-4111-8111-111111111111', '2026-10-01 08:00+00', 74, 'manual'),
  ('22222222-2222-4222-8222-222222222222', '2026-10-02 08:00+00', 100, 'manual');

create temp table cat as
  select key as k, id from public.exercises
  where key in ('pull-up', 'assisted-pull-ups', 'weighted-dips', 'plank', 'treadmill-running', 'bench-press');
grant select on cat to authenticated;

-- One completed session per user, same sets:
--   pull-up (bodyweight) 10 kg added x 8, assisted-pull-ups (assisted) 20 kg x 10, weighted-dips 20 x 5,
--   plank 90 s, treadmill-running 5000 m in 1800 s, bench-press 100 x 5.
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at) values
  ('a6000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Ana', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00'),
  ('b6000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Bea', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00'),
  ('c6000000-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'Carl', 'completed',
   '2026-10-12 10:00+00', '2026-10-12 11:00+00');

insert into public.workout_sets
  (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, duration_s, distance_m, completed_at)
select s.id, (select id from cat where k = v.k), v.pos, 1, v.w, v.r, v.dur, v.dist, '2026-10-12 10:30+00'
from (values
  ('pull-up', 0, 10, 8, null::int, null::numeric),
  ('assisted-pull-ups', 1, 20, 10, null, null),
  ('weighted-dips', 2, 20, 5, null, null),
  ('plank', 3, 0, 0, 90, null),
  ('treadmill-running', 4, 0, 0, 1800, 5000.0),
  ('bench-press', 5, 100, 5, null, null)
) as v (k, pos, w, r, dur, dist)
cross join public.workout_sessions s;

-- ---------------------------------------------------------------------------
-- Ana (body weight 74 kg)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(public.latest_body_weight_kg(), 74::numeric, 'the newest body weight of the caller is used');

select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'pull-up'))),
  672::numeric, 'bodyweight volume is (74 + 10) x 8');
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'assisted-pull-ups'))),
  540::numeric, 'assisted volume is (74 - 20) x 10');
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'weighted-dips'))),
  100::numeric, 'weighted volume is the added load x reps');
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'plank'))),
  0::numeric, 'a duration set has no volume');
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'treadmill-running'))),
  0::numeric, 'a distance set has no volume');
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'bench-press'))),
  500::numeric, 'weight_reps volume is unchanged');
select is(
  (select best_one_rm from public.exercise_history((select id from cat where k = 'plank'))),
  null::numeric, 'a duration set has no estimated 1RM');
select is(
  (select sum(total_volume) from public.weekly_muscle_volume('2026-10-01')),
  672 + 540 + 100 + 500::numeric, 'weekly muscle volume sums the metric-aware volume');

select results_eq(
  $$select set_index, duration_s, distance_m, weight_kg, reps
    from public.last_performance((select id from cat where k = 'plank'))$$,
  $$values (1, 90, null::numeric, 0::numeric, 0)$$,
  'last_performance returns the duration of a duration set');
select results_eq(
  $$select set_index, duration_s, distance_m, steps, floors
    from public.last_performance((select id from cat where k = 'treadmill-running'))$$,
  $$values (1, 1800, 5000.0::numeric, null::int, null::int)$$,
  'last_performance returns distance and duration of a distance set');

-- Custom exercises accept every metric type.
select lives_ok(
  $$insert into public.exercises (name, muscle_group, equipment, metric_type, is_custom, created_by)
    select 'Custom ' || l, 'core', 'other', l::public.exercise_metric_type, true, '11111111-1111-4111-8111-111111111111'
    from unnest(enum_range(null::public.exercise_metric_type)::text[]) as l$$,
  'a custom exercise can be created with any metric type');
select is(
  (select count(distinct metric_type)::int from public.exercises where is_custom and name like 'Custom %'),
  10, 'all ten metric types were stored');

-- ---------------------------------------------------------------------------
-- Bea (100 kg) sees her own body weight, never Ana's.
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'pull-up'))),
  (100 + 10) * 8::numeric, 'another user volume uses their own body weight');

-- ---------------------------------------------------------------------------
-- Carl has no measurement: body weight counts as 0.
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}', true);
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'pull-up'))),
  80::numeric, 'without a body weight a bodyweight set counts the added load only');
select is(
  (select total_volume from public.exercise_history((select id from cat where k = 'assisted-pull-ups'))),
  0::numeric, 'without a body weight an assisted set counts 0 (floored)');

select * from finish();
rollback;
