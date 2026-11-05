-- pgTAP: search_user_food_ids ignores case and accents, keeps substring semantics, isolates users (T-230).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(17);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'ben@example.com');

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Plátano de Canarias', 90);
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Plátano', 90, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000002', 'user', 'Platano macho', 120, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'user', 'Piña', 50, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'user', '100% zumo', 40, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'user', 'a_b', 40, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000006', 'user', 'axb', 40, '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'user', 'Plátano de Ben', 90, '22222222-2222-4222-8222-222222222222');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.search_user_food_ids('platano')), 2,
  'unaccented query finds accented and unaccented names');
select is((select count(*)::int from public.search_user_food_ids('PLATANO')), 2, 'upper case query matches');
select is((select count(*)::int from public.search_user_food_ids('plátano')), 2, 'accented query matches both spellings');
select is((select count(*)::int from public.search_user_food_ids('pláta')), 2, 'partial accented query matches');
select results_eq(
  $$select food_id from public.search_user_food_ids('PLÁTANO') order by 1$$,
  $$values ('aaaaaaaa-0000-4000-8000-000000000001'::uuid), ('aaaaaaaa-0000-4000-8000-000000000002'::uuid)$$,
  'only Ana''s user foods are returned (no OFF, no Ben)');
select results_eq(
  $$select food_id from public.search_user_food_ids('pina')$$,
  $$values ('aaaaaaaa-0000-4000-8000-000000000003'::uuid)$$,
  'n with tilde is found without it');
select is((select count(*)::int from public.search_user_food_ids('anan')), 0, 'a query without match is empty');
select results_eq(
  $$select food_id from public.search_user_food_ids('%')$$,
  $$values ('aaaaaaaa-0000-4000-8000-000000000004'::uuid)$$,
  'percent is matched literally');
select results_eq(
  $$select food_id from public.search_user_food_ids('a_b')$$,
  $$values ('aaaaaaaa-0000-4000-8000-000000000005'::uuid)$$,
  'underscore is matched literally');
select is((select count(*)::int from public.search_user_food_ids('\')), 0, 'backslash is matched literally');
select results_eq(
  $$select food_id from public.search_user_food_ids('pl', 1)$$,
  $$values ('aaaaaaaa-0000-4000-8000-000000000001'::uuid)$$,
  'limit is respected and results are ordered by name');
select is((select count(*)::int from public.search_user_food_ids('')), 6, 'empty query lists all of the caller''s foods');

select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select results_eq(
  $$select food_id from public.search_user_food_ids('platano')$$,
  $$values ('bbbbbbbb-0000-4000-8000-000000000001'::uuid)$$,
  'Ben only sees his own food');
reset role;

select ok(has_function_privilege('authenticated', 'public.search_user_food_ids(text, int)', 'execute'),
  'authenticated can execute');
select ok(not has_function_privilege('anon', 'public.search_user_food_ids(text, int)', 'execute'),
  'anon cannot execute');
select ok((select not p.prosecdef from pg_proc p where p.oid = 'public.search_user_food_ids(text, int)'::regprocedure),
  'function is security invoker');
select ok((select p.provolatile = 's' from pg_proc p where p.oid = 'public.search_user_food_ids(text, int)'::regprocedure),
  'function is stable');

select * from finish();
rollback;
