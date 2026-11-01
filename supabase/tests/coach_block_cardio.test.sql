-- pgTAP: cardio as the last exercise of every routine of the coach block (T-182, US-72, ADR-85).
-- Rules: planning/04 section 9 (cardio), 07 section 3 "Cardio prescription".
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(52);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;
create function pg_temp.keys(p_equipment text[]) returns text[] language sql as $$
  select coalesce(array_agg(o.key order by o.key), '{}') from public.coach_cardio_options(p_equipment) o $$;

create function pg_temp.ex(p_pattern text, p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern::text = p_pattern
    and metric_type = 'weight_reps' and equipment = 'barbell' order by key offset p_nth limit 1 $$;
create function pg_temp.iso(p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key offset p_nth limit 1 $$;

create function pg_temp.lift(p_id uuid, p_role text, p_rmin int, p_rmax int, p_rest int) returns jsonb
language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', 3,
    'rep_min', p_rmin, 'rep_max', p_rmax, 'rest_seconds', p_rest, 'reason', 'Buen ejercicio base para tu objetivo.') $$;
create function pg_temp.cardio(p_key text, p_min int default 50) returns jsonb
language sql as $$
  select jsonb_build_object('exercise_id', pg_temp.k(p_key), 'exercise_key', p_key, 'role', 'cardio', 'sets', 1,
    'duration_min', p_min, 'pace', 'suave', 'reason', 'Cardio suave para sumar minutos semanales.') $$;

-- Two routines: main + isolation + cardio. p_cardio1 / p_cardio2 may be null (no cardio exercise).
create function pg_temp.sessions(p_cardio1 jsonb default null, p_cardio2 jsonb default null) returns jsonb
language sql as $$
  select jsonb_build_array(
    jsonb_build_object('session_index', 1, 'name', 'Tren superior', 'focus', jsonb_build_array('chest', 'biceps'),
      'exercises', jsonb_build_array(pg_temp.lift(pg_temp.ex('horizontal_push', 0), 'main', 6, 10, 150),
                                     pg_temp.lift(pg_temp.iso(0), 'isolation', 10, 15, 90))
                   || case when p_cardio1 is null then '[]'::jsonb else jsonb_build_array(p_cardio1) end),
    jsonb_build_object('session_index', 2, 'name', 'Tren inferior', 'focus', jsonb_build_array('quads'),
      'exercises', jsonb_build_array(pg_temp.lift(pg_temp.ex('squat', 0), 'main', 6, 10, 150),
                                     pg_temp.lift(pg_temp.iso(1), 'isolation', 10, 15, 90))
                   || case when p_cardio2 is null then '[]'::jsonb else jsonb_build_array(p_cardio2) end)) $$;

-- Training with a cardio plan: target t, planned p, on-your-own o, per-routine split.
create function pg_temp.training(p_target int default 100, p_planned int default 100, p_oyo int default 0,
                                 p_per int[] default array[50, 50], p_equipment jsonb default '[]') returns jsonb
language sql as $$
  select jsonb_build_object('days_per_week', 2, 'days_chosen', false, 'session_minutes', 60,
    'rep_scheme', 'single', 'block_weeks', 4, 'equipment', p_equipment,
    'cardio', jsonb_build_object('target_min_week', p_target, 'planned_min_week', p_planned, 'on_your_own_min', p_oyo,
                                 'per_routine_min', to_jsonb(p_per), 'pace', 'suave'),
    'routine_total_min', (select to_jsonb(array_agg(60 + x order by o)) from unnest(p_per) with ordinality t(x, o))) $$;

create function pg_temp.attach(p_plan uuid, p_sessions jsonb default null, p_training jsonb default null) returns jsonb
language sql as $$
  select public.attach_coach_block(p_plan, coalesce(p_training, pg_temp.training()),
    coalesce(p_sessions, pg_temp.sessions(pg_temp.cardio('stationary-bike'), pg_temp.cardio('stationary-bike'))),
    '{"model":"m","attempts":1}', '{}') $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email)
select pg_temp.u(n), 'cardio' || n || '@example.com' from generate_series(1, 6) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status)
select ('c9000000-0000-4000-8000-0000000000a' || n)::uuid, pg_temp.u(n), current_date, 'muscle', '{}', 'proposed'
from generate_series(1, 6) n;
insert into public.training_preferences (user_id, place, equipment) values
  (pg_temp.u(6), 'gym', array['cardio_bike', 'treadmill']::public.equipment[]),
  (pg_temp.u(5), 'home', array[]::public.equipment[]);

-- ---------------------------------------------------------------------------
-- coach_cardio_options
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_cardio_options(text[])', 'execute'),
  'anon cannot execute coach_cardio_options');
select is(pg_temp.keys(array['cardio_bike', 'treadmill']),
  array['air-bike', 'incline-treadmill-walk', 'running', 'stationary-bike', 'treadmill-running', 'walking'],
  'bike + treadmill give the bikes, the treadmill moves, walking and running');
select is(pg_temp.keys(array['bodyweight']), array['running', 'walking'],
  'bodyweight only leaves walking and running (the non-continuous moves are excluded)');
select ok(not (pg_temp.keys('{}') && array['jumping-jacks', 'jump-rope', 'battle-ropes', 'mountain-climbers', 'burpees',
                                              'high-knees', 'heel-flicks', 'stair-climber', 'battle-rope-double-slam']),
  'with no restriction the non-continuous and non-duration cardio moves are still excluded');
select ok(pg_temp.keys('{}') @> array['elliptical-trainer', 'rowing-machine', 'ski-erg'],
  'an empty equipment list means no restriction');

-- ---------------------------------------------------------------------------
-- attach_coach_block with cardio (as user 1)
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user(1);

select lives_ok($$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1')$$,
  'a block with cardio last in every routine is accepted');
select is((select exercises->2->>'role' from public.coach_plan_sessions where week = 1 and session_index = 1),
  'cardio', 'the cardio exercise is stored last');
select is((select (exercises->2->>'duration_min')::int from public.coach_plan_sessions where week = 4 and session_index = 2),
  50, 'and carries its minutes in every week');

select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', jsonb_set(pg_temp.sessions(pg_temp.cardio('stationary-bike'),
      pg_temp.cardio('stationary-bike')), '{0,exercises}',
      jsonb_build_array(pg_temp.cardio('stationary-bike'), pg_temp.lift(pg_temp.ex('horizontal_push', 0), 'main', 6, 10, 150))))$$,
  '22023', null, 'cardio before lifting is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', jsonb_set(pg_temp.sessions(pg_temp.cardio('stationary-bike'),
      pg_temp.cardio('stationary-bike')), '{0,exercises}',
      jsonb_build_array(pg_temp.lift(pg_temp.ex('horizontal_push', 0), 'main', 6, 10, 150), pg_temp.cardio('stationary-bike'),
                        pg_temp.lift(pg_temp.iso(0), 'isolation', 10, 15, 90))))$$,
  '22023', 'block invariant: cardio_order', 'lifting after the cardio exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', jsonb_set(pg_temp.sessions(pg_temp.cardio('stationary-bike'),
      pg_temp.cardio('stationary-bike')), '{0,exercises}',
      jsonb_build_array(pg_temp.lift(pg_temp.ex('horizontal_push', 0), 'main', 6, 10, 150), pg_temp.cardio('stationary-bike'),
                        pg_temp.cardio('walking'))))$$,
  '22023', 'block invariant: cardio_count', 'two cardio exercises in a routine are rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('stationary-bike') || '{"rest_seconds": 60}', pg_temp.cardio('stationary-bike')))$$,
  '22023', 'block invariant: cardio_fields', 'rest on a cardio exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('stationary-bike') || '{"rep_min": 8}', pg_temp.cardio('stationary-bike')))$$,
  '22023', 'block invariant: cardio_fields', 'a rep range on a cardio exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('stationary-bike') || '{"rpe": 7}', pg_temp.cardio('stationary-bike')))$$,
  '22023', 'block invariant: cardio_fields', 'RPE on a cardio exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('elliptical-trainer'), pg_temp.cardio('stationary-bike')),
      pg_temp.training(100, 100, 0, array[50, 50], '["cardio_bike", "barbell", "machine"]'))$$,
  '22023', 'block invariant: cardio_equipment', 'a cardio exercise outside the plan equipment is rejected');
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('walking'), pg_temp.cardio('running')),
      pg_temp.training(100, 100, 0, array[50, 50], '["barbell", "machine"]'))$$,
  'walking and running are accepted whatever cardio equipment is selected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('jumping-jacks'), pg_temp.cardio('stationary-bike')))$$,
  '22023', 'block invariant: cardio_equipment', 'a non-continuous cardio move is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('stationary-bike', 55), pg_temp.cardio('stationary-bike')))$$,
  '22023', 'block invariant: cardio_minutes', 'minutes different from the routine share are rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('stationary-bike', 47), pg_temp.cardio('stationary-bike')))$$,
  '22023', 'block invariant: cardio_minutes', 'minutes that are not a multiple of 5 are rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', null,
      pg_temp.training(100, 100, 10, array[50, 50]))$$,
  '22023', 'block invariant: cardio_total', 'a wrong on_your_own value is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', null,
      pg_temp.training(100, 100, 0, array[60, 40]))$$,
  '22023', 'block invariant: cardio_total', 'a split that is not the even one is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', jsonb_set(pg_temp.sessions(pg_temp.cardio('stationary-bike'),
      pg_temp.cardio('stationary-bike')), '{0,exercises,2,role}', '"warm_up"'))$$,
  '22023', 'block invariant: warmup', 'a warm-up exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(
      pg_temp.cardio('stationary-bike'), null))$$,
  '22023', 'block invariant: cardio_total', 'a routine with cardio minutes but no cardio exercise is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', null,
      jsonb_set(pg_temp.training(), '{cardio}', 'null'::jsonb) - 'cardio' - 'routine_total_min')$$,
  '22023', 'block invariant: cardio_total', 'a cardio exercise without a cardio plan in the training is rejected');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', jsonb_build_array(
      jsonb_build_object('session_index', 1, 'name', 'Solo cardio', 'focus', '[]'::jsonb,
        'exercises', jsonb_build_array(pg_temp.cardio('stationary-bike'))),
      jsonb_build_object('session_index', 2, 'name', 'Otro', 'focus', '[]'::jsonb,
        'exercises', jsonb_build_array(pg_temp.cardio('stationary-bike')))))$$,
  '22023', 'block invariant: empty_session', 'a cardio-only routine is rejected');

-- Edge cases of the split (B2): fewer 5-minute units than routines, and a zero target.
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(pg_temp.cardio('walking', 5), null),
      pg_temp.training(5, 5, 0, array[5, 0]))$$,
  'with fewer 5-minute units than routines the first routine gets the cardio and the second has none');
select throws_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(pg_temp.cardio('walking', 5), pg_temp.cardio('walking', 5)),
      pg_temp.training(5, 5, 0, array[5, 0]))$$,
  '22023', null, 'a routine whose share is 0 cannot carry a cardio exercise');
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(),
      pg_temp.training(0, 0, 0, array[0, 0]))$$,
  'a target of 0 means no cardio anywhere');
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(pg_temp.cardio('walking', 60), pg_temp.cardio('walking', 60)),
      pg_temp.training(300, 120, 180, array[60, 60]))$$,
  'above 60 minutes per routine the surplus is "por tu cuenta"');
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(pg_temp.cardio('walking', 40), pg_temp.cardio('walking', 35)),
      pg_temp.training(75, 75, 0, array[40, 35]))$$,
  'an odd number of units gives the remainder to the first routine');
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(pg_temp.cardio('walking', 40), pg_temp.cardio('walking', 35)),
      pg_temp.training(78, 75, 3, array[40, 35]))$$,
  'a target that is not a multiple of 5 leaves the loose minutes on the user''s own');

-- A block stored before this task (no cardio plan, no cardio exercise) is still valid.
select lives_ok(
  $$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1', pg_temp.sessions(),
      '{"days_per_week":2,"days_chosen":false,"session_minutes":60,"rep_scheme":"single","block_weeks":4,"equipment":[]}')$$,
  'a block without cardio fields keeps working');

-- Another user cannot attach to this plan.
select pg_temp.as_user(2);
select throws_ok($$select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1')$$, 'P0002', null,
  'user B cannot attach a block to user A''s plan');

-- ---------------------------------------------------------------------------
-- save_coach_block: cardio becomes a duration set (user 3, no edits)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(3);
select pg_temp.attach('c9000000-0000-4000-8000-0000000000a3');
select lives_ok($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a3')$$, 'the block with cardio is saved');
select results_eq(
  $$select e.position::int, e.rest_seconds::int, e.rep_min::int, s.target_reps::int, s.target_duration_s
    from public.routine_exercises e join public.routine_exercise_sets s on s.routine_exercise_id = e.id
    join public.routines r on r.id = e.routine_id where r.name = 'Tren superior' and e.position = 2$$,
  $$values (2, 0, null::int, null::int, 3000)$$,
  'the cardio is the last exercise with a 50-minute duration set, no reps, rest 0 and no rep range');
select is((select count(*)::int from public.routine_exercises e join public.routines r on r.id = e.routine_id
           where r.name in ('Tren superior', 'Tren inferior')), 6, 'each routine has two lifting exercises and the cardio');
select is(public.save_coach_block('c9000000-0000-4000-8000-0000000000a3')->'routine_ids',
  (select jsonb_agg(routine_id order by session_index) from public.coach_plan_sessions where week = 1),
  'saving again is idempotent');

-- ---------------------------------------------------------------------------
-- Swapping the cardio at save time (user 1: swap bike -> walking; walking is inside any equipment)
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select pg_temp.attach('c9000000-0000-4000-8000-0000000000a1');
select lives_ok(format($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a1',
    '[{"session_index":1,"position":3,"exercise_id":"%s"}]'::jsonb)$$, pg_temp.k('walking')),
  'a cardio exercise can be swapped for another cardio exercise');
select is((select count(*)::int from public.coach_plan_sessions
           where session_index = 1 and exercises->2->>'exercise_key' = 'walking' and (exercises->2->>'duration_min')::int = 50), 4,
  'the swap applies to the 4 weeks and keeps the minutes');
select is((select count(*)::int from public.coach_plan_sessions
           where session_index = 2 and exercises->2->>'exercise_key' = 'stationary-bike'), 4, 'the other routine keeps its cardio');

-- ---------------------------------------------------------------------------
-- Rejected swaps (user 4: equipment cardio_bike only) and nothing is created
-- ---------------------------------------------------------------------------
select pg_temp.as_user(4);
select pg_temp.attach('c9000000-0000-4000-8000-0000000000a4',
  pg_temp.sessions(pg_temp.cardio('stationary-bike'), pg_temp.cardio('stationary-bike')),
  pg_temp.training(100, 100, 0, array[50, 50], '["cardio_bike", "barbell", "machine"]'));
select throws_ok(format($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a4',
    '[{"session_index":1,"position":1,"exercise_id":"%s"}]'::jsonb)$$, pg_temp.k('walking')),
  '22023', null, 'a cardio exercise cannot replace a lifting exercise');
select throws_ok(format($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a4',
    '[{"session_index":1,"position":3,"exercise_id":"%s"}]'::jsonb)$$, pg_temp.ex('squat', 0)),
  '22023', null, 'a lifting exercise cannot replace the cardio exercise');
select throws_ok(format($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a4',
    '[{"session_index":1,"position":3,"exercise_id":"%s"}]'::jsonb)$$, pg_temp.k('elliptical-trainer')),
  '22023', null, 'a cardio swap outside the plan equipment is rejected');
select throws_ok(format($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a4',
    '[{"session_index":1,"position":3,"exercise_id":"%s"}]'::jsonb)$$, pg_temp.k('stationary-bike')),
  '22023', null, 'a replacement already in the session is rejected');
select throws_ok(format($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a4',
    '[{"session_index":1,"position":3,"exercise_id":"%s"}]'::jsonb)$$, pg_temp.k('jumping-jacks')),
  '22023', null, 'a non-continuous cardio move is not a valid swap');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(4)), 0, 'rejected edits create nothing');
select pg_temp.as_user(2);
select throws_ok($$select public.save_coach_block('c9000000-0000-4000-8000-0000000000a4')$$, 'P0002', null,
  'user B cannot save user A''s block');

-- ---------------------------------------------------------------------------
-- exercise_alternatives of a cardio exercise
-- ---------------------------------------------------------------------------
select pg_temp.as_user(6);
select is((select array_agg(e.key order by e.key) from public.exercise_alternatives(pg_temp.k('stationary-bike')) a
           join public.exercises e on e.id = a.exercise_id),
  array['air-bike', 'incline-treadmill-walk', 'running', 'treadmill-running', 'walking'],
  'the alternatives of a cardio exercise are all the cardio options of the user''s equipment, except itself');
select is((select array_agg(e.key order by e.key) from public.exercise_alternatives(pg_temp.k('walking')) a
           join public.exercises e on e.id = a.exercise_id),
  array['air-bike', 'incline-treadmill-walk', 'running', 'stationary-bike', 'treadmill-running'],
  'any cardio exercise swaps with any other: the same set from walking');
select is((select count(*)::int from public.exercise_alternatives(pg_temp.k('stationary-bike')) a where a.similarity <> 1), 0,
  'every cardio alternative has similarity 1');
select pg_temp.as_user(5);
select is((select array_agg(e.key order by e.key) from public.exercise_alternatives(pg_temp.k('walking')) a
           join public.exercises e on e.id = a.exercise_id), array['running'],
  'at home with nothing selected only the bodyweight cardio is offered');
select ok((select count(*) from public.exercise_alternatives((select a2.exercise_id from public.exercise_alternatives a2 join public.exercises e2 on e2.id = a2.exercise_id where e2.movement_pattern <> 'cardio' order by a2.exercise_id limit 1))) > 0
          and not exists (select 1 from public.exercise_alternatives((select a2.exercise_id from public.exercise_alternatives a2 join public.exercises e2 on e2.id = a2.exercise_id where e2.movement_pattern <> 'cardio' order by a2.exercise_id limit 1)) a
                          join public.exercises e on e.id = a.exercise_id where e.movement_pattern = 'cardio'),
  'a lifting exercise keeps its curated alternatives and none is cardio');

select * from finish();
rollback;
