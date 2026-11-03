-- pgTAP: public.exercise_favorites RLS and constraints (T-204, US-96).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(19);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.exercises (id, name, muscle_group, equipment, is_custom, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'Remo de Ana', 'back', 'dumbbell', true, '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'Remo de Bea', 'back', 'dumbbell', true, '22222222-2222-4222-8222-222222222222');
create temp table cat on commit drop as
  select id from public.exercises where not is_custom order by key limit 2;
grant select on cat to authenticated;

-- Schema and security
select columns_are('public', 'exercise_favorites', array['user_id', 'exercise_id', 'created_at'],
  'exercise_favorites has exactly the planned columns');
select fk_ok('public', 'exercise_favorites', 'user_id', 'auth', 'users', 'id', 'user_id references auth.users.id');
select fk_ok('public', 'exercise_favorites', 'exercise_id', 'public', 'exercises', 'id', 'exercise_id references exercises.id');
select has_index('public', 'exercise_favorites', 'exercise_favorites_exercise_id_idx', 'index on exercise_id exists');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.exercise_favorites'::regclass),
  'RLS is enabled and forced on exercise_favorites');
select table_privs_are('public', 'exercise_favorites', 'anon', array[]::text[],
  'anon has no privileges on exercise_favorites');

-- RLS (as Ana)
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$insert into public.exercise_favorites (exercise_id) select id from cat order by id limit 1$$,
  'Ana stars a catalogue exercise; user_id defaults to the caller');
select lives_ok(
  $$insert into public.exercise_favorites (exercise_id) values ('aaaaaaaa-0000-4000-8000-000000000001')$$,
  'Ana stars her own custom exercise');
select throws_ok(
  $$insert into public.exercise_favorites (exercise_id) select id from cat order by id limit 1$$,
  '23505', null, 'a duplicate favourite is rejected');
select throws_ok(
  $$insert into public.exercise_favorites (exercise_id) values ('bbbbbbbb-0000-4000-8000-000000000001')$$,
  '42501', null, 'Ana cannot star Bea''s custom exercise');
select throws_ok(
  $$insert into public.exercise_favorites (user_id, exercise_id) values ('22222222-2222-4222-8222-222222222222', 'aaaaaaaa-0000-4000-8000-000000000001')$$,
  '42501', null, 'Ana cannot create a favourite for Bea');
select is((select count(*)::int from public.exercise_favorites), 2, 'Ana sees her two favourites');

-- RLS (as Bea)
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.exercise_favorites), 0, 'Bea does not see Ana''s favourites');
select results_eq(
  $$with d as (delete from public.exercise_favorites returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'Bea deleting Ana''s favourites affects 0 rows');

-- Ana removes a star
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select results_eq(
  $$with d as (delete from public.exercise_favorites where exercise_id = (select id from cat order by id limit 1) returning 1) select count(*)::int from d$$,
  $$values (1)$$, 'Ana removes her catalogue favourite');

reset role;
set local role anon;
select throws_ok('select * from public.exercise_favorites', '42501', null, 'anon cannot read exercise_favorites');
reset role;

-- Cascades: custom exercise deletion and user deletion (ADR-29)
delete from public.exercises where id = 'aaaaaaaa-0000-4000-8000-000000000001';
select is((select count(*)::int from public.exercise_favorites where user_id = '11111111-1111-4111-8111-111111111111'), 0,
  'deleting a custom exercise removes its favourite');

insert into public.exercise_favorites (user_id, exercise_id)
  select u, id from cat, (values ('11111111-1111-4111-8111-111111111111'::uuid), ('22222222-2222-4222-8222-222222222222'::uuid)) v(u);
select lives_ok(
  $$delete from auth.users where id = '11111111-1111-4111-8111-111111111111'$$,
  'deleting a user with favourites succeeds');
select is((select count(*)::int from public.exercise_favorites), 2,
  'only the other user''s favourites survive');

select * from finish();
rollback;
