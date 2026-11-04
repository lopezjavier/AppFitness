-- pgTAP: save_built_coach_block, coach_builder_inputs and the relaxed coach_assert_block (T-221, ADR-99).
-- The device builds the block; the server stays the gate for the structural invariants and saves the folder.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(39);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('d7000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.p(n int) returns uuid language sql as $$
  select ('d7000000-0000-4000-8000-00000000a0' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;
create function pg_temp.press() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'horizontal_push'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key limit 1 $$;
create function pg_temp.today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;

-- A builder exercise (no reason, as the device sends it).
create function pg_temp.lift(p_id uuid, p_role text, p_extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'role', p_role, 'sets', 3,
    'rep_min', 8, 'rep_max', 12, 'rest_seconds', case when p_role = 'main' then 120 else 90 end,
    'target_rpe', 8) || p_extra $$;
create function pg_temp.cardio(p_min int) returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', pg_temp.k('walking'), 'role', 'cardio', 'sets', 1,
    'duration_min', p_min, 'pace', 'suave') $$;
create function pg_temp.tr(p_extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('days_per_week', 1, 'block_weeks', 4, 'intention', 'muscle', 'equipment', '[]'::jsonb,
    'session_minutes', 60, 'source', 'builder') || p_extra $$;
create function pg_temp.cardio_tr(p_extra jsonb default '{}') returns jsonb language sql as $$
  select pg_temp.tr(jsonb_build_object('routine_total_min', jsonb_build_array(120),
    'cardio', jsonb_build_object('target_min_week', 150, 'planned_min_week', 60, 'on_your_own_min', 90,
                                 'per_routine_min', jsonb_build_array(60), 'pace', 'suave')) || p_extra) $$;
create function pg_temp.sess(p_exercises jsonb) returns jsonb language sql as $$
  select jsonb_build_array(jsonb_build_object('name', 'Cuerpo completo A', 'focus', jsonb_build_array('chest'),
    'exercises', p_exercises)) $$;
create function pg_temp.std() returns jsonb language sql as $$
  select jsonb_build_array(
    pg_temp.lift(pg_temp.press(), 'main', '{"planned_weight_kg":62.5,"superset_group":1}'),
    pg_temp.lift(pg_temp.k('seated-cable-row'), 'main', '{"superset_group":1}'),
    pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation'),
    pg_temp.lift(pg_temp.k('leg-extension'), 'isolation')) $$;
create function pg_temp.save(p_n int, p_training jsonb, p_exercises jsonb, p_name text default null) returns jsonb language sql as $$
  select public.save_built_coach_block(pg_temp.p(p_n), p_training, pg_temp.sess(p_exercises), p_name) $$;
create function pg_temp.code(p_n int, p_training jsonb, p_exercises jsonb) returns text language plpgsql as $$
begin
  perform pg_temp.save(p_n, p_training, p_exercises);
  return 'saved';
exception when others then
  return sqlstate || ' ' || sqlerrm;
end $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'builtsave' || n || '@example.com' from generate_series(1, 8) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status)
select pg_temp.p(n), pg_temp.u(n), current_date, 'muscle', '{}', case when n = 6 then 'superseded' else 'proposed' end
from generate_series(1, 8) n;
-- Open level 4 discomfort on the machine press (user 4).
insert into public.exercise_discomforts (user_id, exercise_id, level, note) values (pg_temp.u(4), pg_temp.press(), 4, 'hombro');
-- History for user 7 (builder inputs) and a victim row for user 2.
insert into public.training_preferences (user_id, place, equipment) values (pg_temp.u(7), 'gym', '{}');
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
select ('d7000000-0000-4000-8000-0000000001' || lpad(n::text, 2, '0'))::uuid, pg_temp.u(case when n = 9 then 2 else 7 end), 's', 'completed',
       now() - make_interval(days => d), now() - make_interval(days => d) + interval '1 hour'
from (values (1, 2), (2, 10), (3, 55), (4, 57), (9, 3)) v(n, d);
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at)
values
  ('d7000000-0000-4000-8000-000000000101', pg_temp.press(), 0, 1, 'working', 60, 8, now() - interval '2 days'),
  ('d7000000-0000-4000-8000-000000000101', pg_temp.press(), 0, 2, 'warm_up', 20, 10, now() - interval '2 days' + interval '5 minutes'),
  ('d7000000-0000-4000-8000-000000000102', pg_temp.press(), 0, 1, 'working', 70, 6, now() - interval '10 days'),
  ('d7000000-0000-4000-8000-000000000103', pg_temp.k('seated-cable-row'), 0, 1, 'working', 40, 10, now() - interval '55 days'),
  ('d7000000-0000-4000-8000-000000000104', pg_temp.k('lateral-raise'), 0, 1, 'working', 8, 12, now() - interval '57 days'),
  ('d7000000-0000-4000-8000-000000000109', pg_temp.k('leg-extension'), 0, 1, 'working', 30, 12, now() - interval '3 days');
insert into public.exercise_favorites (user_id, exercise_id) values (pg_temp.u(7), pg_temp.k('leg-extension'));

-- (h) a table and function tour first: privileges.
select is((select has_function_privilege('anon', 'public.save_built_coach_block(uuid,jsonb,jsonb,text)', 'execute')), false,
  'anon cannot call save_built_coach_block');
select is((select has_function_privilege('anon', 'public.coach_builder_inputs(date)', 'execute')), false,
  'anon cannot call coach_builder_inputs');

set local role authenticated;

-- (a) a builder block with cardio saves.
select pg_temp.as_user(1);
create temp table r1 as
  select public.save_built_coach_block(pg_temp.p(1), pg_temp.cardio_tr(),
    pg_temp.sess(pg_temp.std() || jsonb_build_array(pg_temp.cardio(60)))) as res;
grant select on r1 to authenticated;
select is((select f.name from public.routine_folders f where f.id = (select (res->>'folder_id')::uuid from r1)),
  'Bloque ' || to_char(current_date, 'DD/MM/YYYY'), 'the folder is named Bloque DD/MM/YYYY');
select is((select jsonb_array_length(res->'routine_ids') from r1), 1, 'one routine per day');
select is((select array_agg(re.exercise_id order by re.position) from public.routine_exercises re
           where re.routine_id = (select (res->'routine_ids'->>0)::uuid from r1)),
  array[pg_temp.press(), pg_temp.k('seated-cable-row'), pg_temp.k('lateral-raise'), pg_temp.k('leg-extension'), pg_temp.k('walking')],
  'the routine keeps the order and the cardio is last');
select is((select array_agg(re.target_rpe order by re.position) from public.routine_exercises re
           where re.routine_id = (select (res->'routine_ids'->>0)::uuid from r1) and re.position < 4),
  array[8, 8, 8, 8]::numeric[], 'the target RPE is 8 on every lift');
select is((select re.rep_min::text || '-' || re.rep_max::text || '/' || re.rest_seconds::text || '/' || re.target_sets::text
           from public.routine_exercises re where re.routine_id = (select (res->'routine_ids'->>0)::uuid from r1) and re.position = 0),
  '8-12/120/3', 'range, rest and sets are stored');
select is((select s.target_weight_kg from public.routine_exercise_sets s join public.routine_exercises re on re.id = s.routine_exercise_id
           where re.routine_id = (select (res->'routine_ids'->>0)::uuid from r1) and re.position = 0 and s.set_index = 1),
  62.50, 'the planned weight reaches the sets');
select is((select array_agg(re.superset_group order by re.position) from public.routine_exercises re
           where re.routine_id = (select (res->'routine_ids'->>0)::uuid from r1)),
  array[1, 1, null, null, null]::smallint[], 'the superset pair shares a group');
select is((select re.notes from public.routine_exercises re
           where re.routine_id = (select (res->'routine_ids'->>0)::uuid from r1) and re.position = 0),
  null, 'without a reason there is no note');
select is((select p.folder_id from public.coach_plans p where p.id = pg_temp.p(1)), (select (res->>'folder_id')::uuid from r1),
  'the plan is linked to the folder');
select is((select count(*)::int || '/' || count(distinct s.exercises::text)::int from public.coach_plan_sessions s
           where s.plan_id = pg_temp.p(1)), '4/1', 'weeks 1 to 4 hold the same routine');
select is((select count(*)::int from public.coach_plan_sessions s where s.plan_id = pg_temp.p(1) and s.routine_id is not null), 4,
  'and every week is linked to the saved routine');

-- (g) idempotent second call; plan not open.
select is(public.save_built_coach_block(pg_temp.p(1), pg_temp.cardio_tr(), pg_temp.sess(pg_temp.std())), (select res from r1),
  'a second call returns the same folder');
select is((select count(*)::int from public.routines where name like 'Cuerpo completo A%'), 1, 'and creates nothing');
select pg_temp.as_user(6);
select throws_ok($$select pg_temp.save(6, pg_temp.tr(), pg_temp.std())$$, '55000', 'plan is not open', 'a closed plan is refused');

-- (b) custom folder name.
select pg_temp.as_user(3);
create temp table r3 as select pg_temp.save(3, pg_temp.tr(), pg_temp.std(), 'Mi bloque') as res;
grant select on r3 to authenticated;
select is((select f.name from public.routine_folders f where f.id = (select (res->>'folder_id')::uuid from r3)),
  'Mi bloque', 'a given folder name is used');

-- (c) and (d) discomfort is no longer an input; the reason is optional but not tiny.
select pg_temp.as_user(4);
select lives_ok($$select pg_temp.save(4, pg_temp.tr(), pg_temp.std())$$,
  'an exercise with an open level 4 discomfort saves (ADR-99)');
select pg_temp.as_user(5);
select is(pg_temp.code(5, pg_temp.tr(), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main', '{"reason":"corto"}'))),
  '22023 block invariant: reason', 'a 5-character reason is refused when present');
select is(pg_temp.code(5, pg_temp.tr(), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main', '{"reason":"Buen ejercicio base."}'))),
  'saved', 'a 10+ character reason is accepted');

-- (e) the invariants still raise (user 5's plan stays unsaved until the last call below).
select pg_temp.as_user(8);
select is(pg_temp.code(8, pg_temp.tr('{"equipment":["barbell"]}'), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main'))),
  '22023 block invariant: h6_equipment', 'equipment outside the saved list is refused');
select is(pg_temp.code(8, pg_temp.tr(), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main'), pg_temp.lift(pg_temp.press(), 'main'))),
  '22023 block invariant: duplicate_exercise', 'a repeat in a routine is refused');
select is(pg_temp.code(8, pg_temp.tr('{"days_per_week":2}'), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main'))),
  '22023 block invariant: h6_sessions', 'routines different from days per week are refused');
select is(pg_temp.code(8, pg_temp.cardio_tr('{"cardio":{"target_min_week":150,"planned_min_week":60,"on_your_own_min":0,"per_routine_min":[60],"pace":"suave"}}'),
  pg_temp.std() || jsonb_build_array(pg_temp.cardio(60))), '22023 block invariant: cardio_total', 'a cardio total that does not add up is refused');
select is(pg_temp.code(8, pg_temp.tr(), jsonb_build_array(pg_temp.lift(pg_temp.k('lateral-raise'), 'main'))),
  '22023 block invariant: role_pattern', 'a main role on an isolation exercise is refused');
select is(pg_temp.code(8, pg_temp.tr('{"intention":"strength"}'), jsonb_build_array(
    pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation'),
    pg_temp.lift(pg_temp.press(), 'main', '{"rep_min":4,"rep_max":6,"rest_seconds":180}'))),
  '22023 block invariant: h7_order', 'strength needs the main lifts first');
select is(pg_temp.code(8, pg_temp.tr(), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main', '{"rep_min":3,"rep_max":5}'))),
  '22023 block invariant: h4_rep_range', 'a builder block refuses a 3-5 range outside strength');
select is(pg_temp.code(8, pg_temp.tr(), jsonb_build_array(pg_temp.lift(pg_temp.press(), 'main', '{"target_rpe":10}'))),
  '22023 block invariant: h5_rpe', 'an RPE outside 7-9 is refused');
select is((select count(*)::int from public.coach_plan_sessions where plan_id = pg_temp.p(8)), 0,
  'a refused block stores nothing');

-- (f) a folder block keeps its own rep range and rest.
select is(pg_temp.code(8, pg_temp.tr('{"source":"folder"}'), jsonb_build_array(
    pg_temp.lift(pg_temp.press(), 'main', '{"rep_min":3,"rep_max":5,"rest_seconds":90}'))),
  'saved', 'a folder block accepts a 3-5 range and a 90 s rest');

-- (h) isolation between users.
select pg_temp.as_user(2);
select throws_ok($$select public.save_built_coach_block(pg_temp.p(7), pg_temp.tr(), pg_temp.sess(pg_temp.std()))$$,
  'P0002', 'plan not found', 'user B cannot save into user A''s plan');
select pg_temp.as_user(7);
select is((select count(*)::int from public.coach_plan_sessions where plan_id = pg_temp.p(7)), 0, 'and A''s plan has no rows');

-- coach_builder_inputs.
select is((select jsonb_array_length(public.coach_builder_inputs(pg_temp.today())->'history')), 2,
  'history: only the caller''s lifts of the last 8 weeks (press, cable row; not another user''s leg extension)');
select is((select h->>'sessions_8w' from jsonb_array_elements(public.coach_builder_inputs(pg_temp.today())->'history') h
           where (h->>'exercise_id')::uuid = pg_temp.press()), '2', 'sessions are distinct completed sessions');
select is((select h->>'last_weight_kg' || 'x' || (h->>'last_reps') from jsonb_array_elements(public.coach_builder_inputs(pg_temp.today())->'history') h
           where (h->>'exercise_id')::uuid = pg_temp.press()), '60.00x8', 'the last working set wins and a warm-up is ignored');
select ok(not exists (select 1 from jsonb_array_elements(public.coach_builder_inputs(pg_temp.today())->'history') h
                      where (h->>'exercise_id')::uuid = pg_temp.k('lateral-raise')), 'a session older than 56 days is out');
select is((select (public.coach_builder_inputs(pg_temp.today())->>'cardio_default')::uuid), pg_temp.k('stationary-bike'),
  'with nothing logged the default cardio is the stationary bike');
select is((select count(*)::int from public.exercise_favorites where user_id = pg_temp.u(7)), 1, 'favourites are untouched');

reset role;
insert into public.workout_sessions (id, user_id, name, status, started_at, ended_at)
values ('d7000000-0000-4000-8000-000000000110', pg_temp.u(7), 'walk', 'completed', now() - interval '4 days', now() - interval '4 days' + interval '30 minutes');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, set_type, weight_kg, reps, completed_at, duration_s)
values ('d7000000-0000-4000-8000-000000000110', pg_temp.k('walking'), 0, 1, 'working', 0, 0, now() - interval '4 days', 1800);
set local role authenticated;
select pg_temp.as_user(7);
select is((select (public.coach_builder_inputs(pg_temp.today())->>'cardio_default')::uuid), pg_temp.k('walking'),
  'the most-logged cardio of the last 8 weeks becomes the default');

select * from finish();
rollback;
