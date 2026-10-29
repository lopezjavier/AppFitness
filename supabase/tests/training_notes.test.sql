-- pgTAP: notes on workout sets and routine exercises (T-148, US-66, ADR-81).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(19);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id from public.exercises where not is_custom order by key limit 1;
grant select on cat to authenticated;

-- Fixtures (as postgres): A has a routine with a noted exercise and a completed session; B has a noted set.
insert into public.routines (id, user_id, name) values
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Torso'),
  ('b0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Pierna');
insert into public.routine_exercises (id, routine_id, exercise_id, position, target_sets, target_reps, notes)
values ('a1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
        (select id from cat), 0, 1, 10, 'bajar el peso si duele');
insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps)
values ('a1000000-0000-4000-8000-000000000001', 1, 10, 10);
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at) values
  ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111',
   'a0000000-0000-4000-8000-000000000001', 'Torso', 'completed', '2026-03-01 10:00+00', '2026-03-01 11:00+00'),
  ('c0000000-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222',
   'b0000000-0000-4000-8000-000000000001', 'Pierna', 'completed', '2026-03-01 10:00+00', '2026-03-01 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at, notes)
values ('c0000000-0000-4000-8000-000000000001', (select id from cat), 0, 1, 10, 10, '2026-03-01 10:10+00', null),
       ('c0000000-0000-4000-8000-000000000002', (select id from cat), 0, 1, 10, 10, '2026-03-01 10:10+00', 'secreto de Bea');

select has_column('public', 'workout_sets', 'notes', 'workout_sets has notes');
select has_column('public', 'routine_exercises', 'notes', 'routine_exercises has notes');

-- Length checks (as postgres, direct writes)
select throws_ok($$update public.workout_sets set notes = '' where session_id = 'c0000000-0000-4000-8000-000000000001'$$,
  '23514', null, 'an empty set note is rejected');
select throws_ok($$update public.workout_sets set notes = repeat('x', 201) where session_id = 'c0000000-0000-4000-8000-000000000001'$$,
  '23514', null, 'a 201-char set note is rejected');
select lives_ok($$update public.workout_sets set notes = repeat('x', 200) where session_id = 'c0000000-0000-4000-8000-000000000001'$$,
  'a 200-char set note is accepted');
select throws_ok($$update public.routine_exercises set notes = '' where id = 'a1000000-0000-4000-8000-000000000001'$$,
  '23514', null, 'an empty routine exercise note is rejected');
select throws_ok($$update public.routine_exercises set notes = repeat('x', 201) where id = 'a1000000-0000-4000-8000-000000000001'$$,
  '23514', null, 'a 201-char routine exercise note is rejected');
select lives_ok($$update public.routine_exercises set notes = 'x' where id = 'a1000000-0000-4000-8000-000000000001'$$,
  'a 1-char routine exercise note is accepted');
update public.workout_sets set notes = null where session_id = 'c0000000-0000-4000-8000-000000000001';
update public.routine_exercises set notes = 'bajar el peso si duele' where id = 'a1000000-0000-4000-8000-000000000001';

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- save_routine round trip: note kept, blank stored as null
select lives_ok(format($j$select public.save_routine('{"name":"Nueva","exercises":[
  {"exercise_id":"%1$s","notes":"  pausa al bajar  ","sets":[{"reps":8,"weight_kg":20}]},
  {"exercise_id":"%1$s","notes":"   ","sets":[{"reps":8,"weight_kg":20}]},
  {"exercise_id":"%1$s","sets":[{"reps":8,"weight_kg":20}]}]}'::jsonb)$j$, (select id from cat)),
  'save_routine accepts notes per exercise');
select results_eq($$select re.notes from public.routine_exercises re join public.routines r on r.id = re.routine_id
  where r.name = 'Nueva' order by re.position$$,
  $$values ('pausa al bajar'::text), (null::text), (null::text)$$,
  'routine exercise notes are trimmed and blank becomes null');
select throws_ok(format($j$select public.save_routine('{"name":"Larga","exercises":[
  {"exercise_id":"%s","notes":"%s","sets":[{"reps":8}]}]}'::jsonb)$j$, (select id from cat), repeat('x', 201)),
  '23514', null, 'save_routine rejects a 201-char note');

-- save_completed_workout: set note kept, trimmed, blank -> null; edit path rewrites it
select lives_ok(format($j$select public.save_completed_workout('{"name":"Pasado","started_at":"2026-03-02T10:00:00Z","duration_s":3600,
  "exercises":[{"exercise_id":"%s","sets":[{"reps":10,"weight_kg":10,"notes":" pausa larga "},{"reps":10,"weight_kg":10,"notes":"  "}]}]}'::jsonb)$j$,
  (select id from cat)), 'save_completed_workout accepts set notes');
select results_eq($$select ws.notes from public.workout_sets ws join public.workout_sessions s on s.id = ws.session_id
  where s.name = 'Pasado' order by ws.set_index$$,
  $$values ('pausa larga'::text), (null::text)$$,
  'set notes are trimmed and blank becomes null');
select lives_ok(format($j$select public.save_completed_workout(jsonb_build_object('id',
  (select id from public.workout_sessions where name = 'Pasado'), 'name', 'Pasado',
  'exercises', '[{"exercise_id":"%s","sets":[{"reps":10,"weight_kg":10,"notes":"editada"}]}]'::jsonb)) $j$,
  (select id from cat)), 'the edit path accepts set notes');
select results_eq($$select ws.notes from public.workout_sets ws join public.workout_sessions s on s.id = ws.session_id
  where s.name = 'Pasado'$$, $$values ('editada'::text)$$, 'the edit path rewrites the set note');

-- update_routine_from_session keeps the routine exercise notes
select lives_ok($$select public.update_routine_from_session('c0000000-0000-4000-8000-000000000001')$$,
  'update_routine_from_session runs');
select results_eq($$select notes from public.routine_exercises where routine_id = 'a0000000-0000-4000-8000-000000000001'$$,
  $$values ('bajar el peso si duele'::text)$$, 'update_routine_from_session leaves the routine note');

-- Cross-user isolation
select is_empty($$select 1 from public.workout_sets where notes = 'secreto de Bea'$$, 'A cannot read the notes of B');
select results_eq($$with u as (update public.workout_sets set notes = 'hack'
  where session_id = 'c0000000-0000-4000-8000-000000000002' returning 1) select count(*)::int from u$$,
  $$values (0)$$, 'A cannot write the notes of B');

select * from finish();
rollback;
