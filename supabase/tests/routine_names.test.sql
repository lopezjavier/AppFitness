-- pgTAP: unique routine names, routine delete and the dropped per-routine auto-update flag (T-124, US-56, ADR-70).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(13);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

create temp table cat as
  select id from public.exercises where not is_custom order by key limit 1;
grant select on cat to authenticated;

select hasnt_column('public', 'routines', 'auto_update_routine', 'routines no longer has auto_update_routine');
select has_index('public', 'routines', 'routines_user_name_key', 'routines has the unique name index');

-- Fixtures (as postgres): A has "Torso" with a completed session; B has "Pierna"; A has an archived "Legado".
insert into public.routines (id, user_id, name, archived) values
  ('a0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Torso', false),
  ('a0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Legado', true),
  ('b0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Pierna', false);
insert into public.workout_sessions (id, user_id, routine_id, name, status, started_at, ended_at) values
  ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111',
   'a0000000-0000-4000-8000-000000000001', 'Torso', 'completed', '2026-03-01 10:00+00', '2026-03-01 11:00+00');
insert into public.workout_sets (session_id, exercise_id, exercise_position, set_index, weight_kg, reps, completed_at)
values ('c0000000-0000-4000-8000-000000000001', (select id from cat), 0, 1, 10, 10, '2026-03-01 10:10+00');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Unique name
select throws_ok($$select public.save_routine('{"name":" torso ","exercises":[]}'::jsonb)$$,
  '23505', null, 'a name equal in another case and with spaces is rejected');
select throws_ok($$insert into public.routines (user_id, name) values ('11111111-1111-4111-8111-111111111111', 'TORSO')$$,
  '23505', null, 'a direct insert with the same name in another case is rejected');
select lives_ok($$select public.save_routine('{"id":"a0000000-0000-4000-8000-000000000001","name":"Torso","exercises":[]}'::jsonb)$$,
  'saving a routine with its own name works');
select lives_ok($$select public.save_routine('{"name":"Legado","exercises":[]}'::jsonb)$$,
  'an archived routine does not block its name');
select lives_ok($$select public.save_routine('{"name":"Pierna","exercises":[]}'::jsonb)$$,
  'another user may use the same name');

-- Old clients still sending the removed flag
select lives_ok($$select public.save_routine('{"name":"Viejo","auto_update_routine":true,"exercises":[]}'::jsonb)$$,
  'save_routine ignores the removed auto_update_routine key');

-- Delete
select is_empty($$delete from public.routines where id = 'b0000000-0000-4000-8000-000000000001' returning 1$$,
  'A cannot delete B''s routine');
select is((select count(*)::int from public.routines where id = 'b0000000-0000-4000-8000-000000000001'), 0,
  'A cannot read B''s routine');
delete from public.routines where id = 'a0000000-0000-4000-8000-000000000001';
select is((select count(*)::int from public.routines where id = 'a0000000-0000-4000-8000-000000000001'), 0,
  'A deletes their own routine');
select results_eq(
  $$select routine_id is null, (select count(*)::int from public.workout_sets where session_id = s.id)
    from public.workout_sessions s where id = 'c0000000-0000-4000-8000-000000000001'$$,
  $$values (true, 1)$$, 'the deleted routine''s session and sets remain with routine_id null');

reset role;
select is((select count(*)::int from public.routines where id = 'b0000000-0000-4000-8000-000000000001'), 1,
  'B''s routine survived A''s attempts');

select * from finish();
rollback;
