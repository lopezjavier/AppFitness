-- pgTAP: save_coach_block with the model-built block (T-205): target RPE, supersets, drop sets and planned weights
-- reach the routines; replacements keep the sets, range, RPE and rest. Base behaviour: save_coach_block_edits.test.sql.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(17);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('d6000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.p(n int) returns uuid language sql as $$
  select ('d6000000-0000-4000-8000-00000000a0' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;
create function pg_temp.k(p_key text) returns uuid language sql as $$
  select id from public.exercises where not is_custom and key = p_key $$;
-- A machine press has curated alternatives (no barbell exercise has any).
create function pg_temp.press() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'horizontal_push'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key limit 1 $$;
create function pg_temp.alt(p_id uuid) returns uuid language sql as $$
  select x.id from (select alternative_id as id, similarity from public.exercise_alternatives where exercise_id = p_id
                    union all select exercise_id, similarity from public.exercise_alternatives where alternative_id = p_id) x
  order by x.similarity desc, x.id limit 1 $$;

create function pg_temp.lift(p_id uuid, p_role text, p_extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', 3,
    'rep_min', case when p_role = 'main' then 6 else 10 end, 'rep_max', case when p_role = 'main' then 10 else 15 end,
    'rest_seconds', case when p_role = 'main' then 150 else 90 end, 'target_rpe', 8,
    'reason', 'Buen ejercicio base para tu objetivo.') || p_extra $$;
create function pg_temp.attach(p_plan uuid) returns jsonb language sql as $$
  select public.attach_coach_block(p_plan,
    jsonb_build_object('days_per_week', 1, 'days_chosen', false, 'session_minutes', 60, 'block_weeks', 4, 'equipment', '[]'::jsonb),
    jsonb_build_array(jsonb_build_object('session_index', 1, 'name', 'Torso', 'focus', jsonb_build_array('chest'),
      'exercises', jsonb_build_array(
        pg_temp.lift(pg_temp.press(), 'main', '{"target_rpe":7,"planned_weight_kg":62.5,"superset_group":1}'),
        pg_temp.lift(pg_temp.k('seated-cable-row'), 'main', '{"superset_group":1}'),
        pg_temp.lift(pg_temp.k('lateral-raise'), 'isolation',
          '{"target_rpe":9,"sets":[{"type":"working"},{"type":"working"},{"type":"drop"}],"technique_note":"Codo ligeramente flexionado."}'),
        pg_temp.lift(pg_temp.k('leg-extension'), 'isolation')))),
    '{"model":"m","attempts":1}', '{}') $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'savebuilt' || n || '@example.com' from generate_series(1, 2) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status) values
  (pg_temp.p(1), pg_temp.u(1), current_date, 'muscle', '{}', 'proposed'),
  (pg_temp.p(2), pg_temp.u(2), current_date, 'muscle', '{}', 'proposed');

set local role authenticated;
select pg_temp.as_user(1);
select lives_ok($$select pg_temp.attach(pg_temp.p(1))$$, 'the model-built block is stored');
select lives_ok($$select public.save_coach_block(pg_temp.p(1), jsonb_build_array(
  jsonb_build_object('session_index', 1, 'position', 1, 'exercise_id', pg_temp.alt(pg_temp.press()))))$$,
  'it is saved with one replacement');

select is((select re.target_rpe from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 0), 7::numeric, 'the target RPE is stored on the routine exercise');
select is((select re.target_rpe from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 3), 8::numeric, 'and every exercise keeps its own');
select is((select array_agg(re.superset_group order by re.position) from public.routine_exercises re
           join public.routines r on r.id = re.routine_id where r.name = 'Torso'),
  array[1, 1, null, null]::smallint[], 'the superset pair shares a group');
select is((select re.exercise_id from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 0), pg_temp.alt(pg_temp.press()), 'the replacement changed the exercise');
select is((select re.target_sets from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 0), 3::smallint, 'and the sets stayed');
select is((select re.rest_seconds from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 0), 150::smallint, 'and the rest');
select is((select re.rep_min::text || '-' || re.rep_max::text from public.routine_exercises re
           join public.routines r on r.id = re.routine_id where r.name = 'Torso' and re.position = 0), '6-10', 'and the rep range');
select is((select s.target_weight_kg from public.routine_exercise_sets s join public.routine_exercises re on re.id = s.routine_exercise_id
           join public.routines r on r.id = re.routine_id where r.name = 'Torso' and re.position = 0 and s.set_index = 1),
  62.50, 'the planned weight reaches the sets');
select is((select array_agg(s.set_type::text order by s.set_index) from public.routine_exercise_sets s
           join public.routine_exercises re on re.id = s.routine_exercise_id
           join public.routines r on r.id = re.routine_id where r.name = 'Torso' and re.position = 2),
  array['working', 'working', 'drop'], 'the last set of the lateral raise is a drop set');
select is((select s.target_weight_kg from public.routine_exercise_sets s join public.routine_exercises re on re.id = s.routine_exercise_id
           join public.routines r on r.id = re.routine_id where r.name = 'Torso' and re.position = 2 and s.set_index = 3),
  null, 'and a drop set carries no planned weight');
select is((select re.note_kind::text || ': ' || re.notes from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 2), 'technique: Codo ligeramente flexionado.',
  'a technique note becomes the exercise note');
select is((select re.note_kind::text from public.routine_exercises re join public.routines r on r.id = re.routine_id
           where r.name = 'Torso' and re.position = 3), 'other', 'otherwise the reason is the note');

-- Idempotent: a second save returns the same folder and creates nothing.
select is((select count(*)::int from public.routines where name like 'Torso%'), 1, 'one routine so far');
select lives_ok($$select public.save_coach_block(pg_temp.p(1))$$, 'a second save does not fail');
select is((select count(*)::int from public.routines where name like 'Torso%'), 1, 'and creates no second routine');

select * from finish();
rollback;
