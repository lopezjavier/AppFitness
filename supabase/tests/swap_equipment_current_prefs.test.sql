-- pgTAP: Trainer swaps validate against the user's CURRENT equipment preferences; "Similares" stays unfiltered
-- (T-209, US-72, ADR-83, ADR-92; planning/04 section 9.4 and 9.7b).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(17);

create function pg_temp.u(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.p(n int) returns uuid language sql as $$
  select ('c9000000-0000-4000-8000-00000000a0' || lpad(n::text, 2, '0'))::uuid $$;
create function pg_temp.as_user(n int) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', pg_temp.u(n), 'role', 'authenticated')::text, true) $$;

-- Main lift with curated alternatives (a machine exercise, as in save_coach_block_edits.test.sql).
create function pg_temp.main_ex() returns uuid language sql as $$
  select id from public.exercises e where not is_custom and movement_pattern = 'horizontal_push'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key limit 1 $$;
create function pg_temp.iso(p_nth int default 0) returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'isolation'
    and metric_type = 'weight_reps' and equipment = 'machine' order by key offset p_nth limit 1 $$;
create function pg_temp.squat() returns uuid language sql as $$
  select id from public.exercises where not is_custom and movement_pattern = 'squat'
    and metric_type = 'weight_reps' and equipment = 'barbell' order by key limit 1 $$;
-- An alternative of the main lift whose equipment is neither dumbbell nor bodyweight.
create function pg_temp.alt_out() returns uuid language sql as $$
  select e.id from (
    select alternative_id as id from public.exercise_alternatives where exercise_id = pg_temp.main_ex()
    union all select exercise_id from public.exercise_alternatives where alternative_id = pg_temp.main_ex()) x
  join public.exercises e on e.id = x.id
  where e.equipment::text not in ('dumbbell', 'bodyweight') and e.movement_pattern <> 'cardio'
  order by e.key limit 1 $$;
create function pg_temp.alt_out_equipment() returns text language sql as $$
  select equipment::text from public.exercises where id = pg_temp.alt_out() $$;

create function pg_temp.exercise(p_id uuid, p_role text, p_sets int, p_rmin int, p_rmax int, p_rest int) returns jsonb
language sql as $$
  select jsonb_build_object('exercise_id', p_id, 'exercise_key', 'k', 'role', p_role, 'sets', p_sets,
    'rep_min', p_rmin, 'rep_max', p_rmax, 'rest_seconds', p_rest, 'reason', 'Buen ejercicio base para tu objetivo.') $$;
create function pg_temp.sessions() returns jsonb language sql as $$
  select jsonb_build_array(
    jsonb_build_object('session_index', 1, 'name', 'Tren superior', 'focus', jsonb_build_array('chest', 'biceps'),
      'exercises', jsonb_build_array(pg_temp.exercise(pg_temp.main_ex(), 'main', 4, 6, 10, 150),
                                     pg_temp.exercise(pg_temp.iso(0), 'isolation', 3, 10, 15, 90))),
    jsonb_build_object('session_index', 2, 'name', 'Tren inferior', 'focus', jsonb_build_array('quads'),
      'exercises', jsonb_build_array(pg_temp.exercise(pg_temp.squat(), 'main', 4, 6, 10, 150),
                                     pg_temp.exercise(pg_temp.iso(1), 'isolation', 3, 10, 15, 90)))) $$;
create function pg_temp.attach(p_plan uuid) returns jsonb language sql as $$
  select public.attach_coach_block(p_plan,
    jsonb_build_object('days_per_week', 2, 'days_chosen', false, 'session_minutes', 60, 'rep_scheme', 'single',
                       'block_weeks', 4, 'equipment', '[]'::jsonb),
    pg_temp.sessions(), '{"model":"m","attempts":1}', '{}') $$;
create function pg_temp.swap_out(p_plan uuid) returns jsonb language sql as $$
  select public.save_coach_block(p_plan,
    jsonb_build_array(jsonb_build_object('session_index', 1, 'position', 1, 'exercise_id', pg_temp.alt_out()))) $$;

grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) select pg_temp.u(n), 'swapeq' || n || '@example.com' from generate_series(1, 5) n;
insert into public.coach_plans (id, user_id, block_start, intention, nutrition, status)
select pg_temp.p(n), pg_temp.u(n), current_date, 'muscle', '{}', 'proposed' from generate_series(1, 4) n;
-- U1 dumbbell only; U2 exactly the equipment of the alternative; U3 home with nothing selected; U4 no row; U5 dumbbell.
insert into public.training_preferences (user_id, place, equipment) values
  (pg_temp.u(1), 'gym', array['dumbbell']::public.equipment[]),
  (pg_temp.u(2), 'gym', array[pg_temp.alt_out_equipment()]::public.equipment[]),
  (pg_temp.u(3), 'home', array[]::public.equipment[]),
  (pg_temp.u(5), 'gym', array['dumbbell']::public.equipment[]);

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
select ok(not has_function_privilege('anon', 'public.coach_user_equipment()', 'execute'),
  'anon cannot execute coach_user_equipment');
select ok(not has_function_privilege('anon', 'public.equipment_allowed(text, text, text[])', 'execute'),
  'anon cannot execute equipment_allowed');
select ok(public.equipment_allowed('cable', 'x', null), 'no selection allows everything');
select ok(public.equipment_allowed('bodyweight', 'x', array['dumbbell']), 'bodyweight is always allowed');
select ok(public.equipment_allowed('cardio_machine', 'walking', array['dumbbell'])
          and public.equipment_allowed('cardio_machine', 'running', array['dumbbell']),
  'walking and running are always allowed');
select ok(not public.equipment_allowed('cable', 'x', array['dumbbell'])
          and public.equipment_allowed('dumbbell', 'x', array['dumbbell']),
  'other equipment must be selected');
select isnt(pg_temp.alt_out(), null, 'fixture: the main lift has an alternative outside dumbbell and bodyweight');

set local role authenticated;
select pg_temp.as_user(5);
select is(public.coach_user_equipment(), array['dumbbell'], 'the selected equipment is returned');
select pg_temp.as_user(4);
select is(public.coach_user_equipment(), null, 'user B''s preferences never reach user A: no row means everything');
select pg_temp.as_user(3);
select is(public.coach_user_equipment(), array['bodyweight'], 'home with nothing selected means bodyweight only');

-- ---------------------------------------------------------------------------
-- exercise_alternatives is unfiltered (Similares); the swap is validated against the CURRENT preferences
-- ---------------------------------------------------------------------------
select pg_temp.as_user(1);
select pg_temp.attach(pg_temp.p(1));
select ok(exists (select 1 from public.exercise_alternatives(pg_temp.main_ex()) a where a.exercise_id = pg_temp.alt_out()),
  'Similares lists an alternative whose equipment the user did not select');
select throws_ok($$select pg_temp.swap_out(pg_temp.p(1))$$, '22023', 'replacement is outside the equipment',
  'a swap outside the current equipment is rejected even though the stored plan allows everything');
select is((select count(*)::int from public.routines where user_id = pg_temp.u(1)), 0, 'a rejected swap creates nothing');

select pg_temp.as_user(2);
select pg_temp.attach(pg_temp.p(2));
select lives_ok($$select pg_temp.swap_out(pg_temp.p(2))$$, 'a swap inside the selected equipment is accepted');
select is((select exercises->0->>'exercise_id' from public.coach_plan_sessions
           where plan_id = pg_temp.p(2) and week = 1 and session_index = 1), pg_temp.alt_out()::text,
  'the accepted swap is stored');

select pg_temp.as_user(3);
select pg_temp.attach(pg_temp.p(3));
select throws_ok($$select pg_temp.swap_out(pg_temp.p(3))$$, '22023', 'replacement is outside the equipment',
  'at home with nothing selected only bodyweight swaps are accepted');

select pg_temp.as_user(4);
select pg_temp.attach(pg_temp.p(4));
select lives_ok($$select pg_temp.swap_out(pg_temp.p(4))$$, 'with no preferences row (everything) the swap is accepted');

select * from finish();
rollback;
