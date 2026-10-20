-- pgTAP: public.favorite_foods RLS and constraints, and the most_eaten_foods RPC (T-057, US-17).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(20);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64),
  ('0ff00000-0000-4000-8000-000000000002', 'off', 'Arroz', 130);
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de Ana', 190, '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'user', 'Pan de Bea', 250, '22222222-2222-4222-8222-222222222222');

-- Ana: Leche x3, Arroz x1, Tortilla x1 (later than Arroz). Bea: Arroz x5.
create temp table seed_entries (uid uuid, fid uuid, n int, at timestamptz) on commit drop;
insert into seed_entries values
  ('11111111-1111-4111-8111-111111111111', '0ff00000-0000-4000-8000-000000000001', 3, '2026-10-18 10:00+00'),
  ('11111111-1111-4111-8111-111111111111', '0ff00000-0000-4000-8000-000000000002', 1, '2026-10-17 10:00+00'),
  ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-0000-4000-8000-000000000001', 1, '2026-10-19 10:00+00'),
  ('22222222-2222-4222-8222-222222222222', '0ff00000-0000-4000-8000-000000000002', 5, '2026-10-19 10:00+00');
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, created_at)
  select s.uid, '2026-10-20', m.id, s.fid, 100, 'x', 1, 0, 0, 0, s.at
  from seed_entries s
  cross join generate_series(1, 5) g
  join public.meal_slots m on m.user_id = s.uid and m.code = 'lunch'
  where g <= s.n;

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select columns_are('public', 'favorite_foods', array['user_id', 'food_id', 'created_at'],
  'favorite_foods has exactly the planned columns');
select fk_ok('public', 'favorite_foods', 'user_id', 'auth', 'users', 'id', 'user_id references auth.users.id');
select fk_ok('public', 'favorite_foods', 'food_id', 'public', 'foods', 'id', 'food_id references foods.id');
select has_index('public', 'favorite_foods', 'favorite_foods_food_id_idx', 'index on food_id exists');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.favorite_foods'::regclass),
  'RLS is enabled and forced on favorite_foods');
select table_privs_are('public', 'favorite_foods', 'anon', array[]::text[],
  'anon has no privileges on favorite_foods');

-- ---------------------------------------------------------------------------
-- RLS (as Ana)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$insert into public.favorite_foods (food_id) values ('0ff00000-0000-4000-8000-000000000001')$$,
  'Ana favourites an OFF food; user_id defaults to the caller');
select lives_ok(
  $$insert into public.favorite_foods (food_id) values ('aaaaaaaa-0000-4000-8000-000000000001')$$,
  'Ana favourites her personal food');
select throws_ok(
  $$insert into public.favorite_foods (food_id) values ('0ff00000-0000-4000-8000-000000000001')$$,
  '23505', null, 'a duplicate favourite is rejected');
select throws_ok(
  $$insert into public.favorite_foods (food_id) values ('bbbbbbbb-0000-4000-8000-000000000001')$$,
  '42501', null, 'Ana cannot favourite Bea''s personal food');
select throws_ok(
  $$insert into public.favorite_foods (user_id, food_id) values ('22222222-2222-4222-8222-222222222222', '0ff00000-0000-4000-8000-000000000001')$$,
  '42501', null, 'Ana cannot create a favourite for Bea');

-- ---------------------------------------------------------------------------
-- most_eaten_foods (as Ana)
-- ---------------------------------------------------------------------------
select results_eq(
  $$select food_id, times_logged from public.most_eaten_foods()$$,
  $$values ('0ff00000-0000-4000-8000-000000000001'::uuid, 3::bigint),
           ('aaaaaaaa-0000-4000-8000-000000000001'::uuid, 1::bigint),
           ('0ff00000-0000-4000-8000-000000000002'::uuid, 1::bigint)$$,
  'most_eaten_foods counts only the caller''s entries, by count desc then most recent');
select results_eq(
  $$select food_id from public.most_eaten_foods(1)$$,
  $$values ('0ff00000-0000-4000-8000-000000000001'::uuid)$$,
  'most_eaten_foods respects the limit');
select is((select count(*)::int from public.most_eaten_foods(0)), 1,
  'a limit below 1 is clamped to 1');

-- ---------------------------------------------------------------------------
-- RLS (as Bea)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.favorite_foods), 0, 'Bea does not see Ana''s favourites');
select results_eq(
  $$with d as (delete from public.favorite_foods returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'Bea deleting Ana''s favourites affects 0 rows');
select results_eq(
  $$select food_id, times_logged from public.most_eaten_foods()$$,
  $$values ('0ff00000-0000-4000-8000-000000000002'::uuid, 5::bigint)$$,
  'Bea''s most eaten ignores Ana''s entries');

reset role;
set local role anon;
select throws_ok('select * from public.favorite_foods', '42501', null, 'anon cannot read favorite_foods');
reset role;

-- ---------------------------------------------------------------------------
-- User deletion cascade (ADR-29): favourites of the user and of their personal food
-- ---------------------------------------------------------------------------
insert into public.favorite_foods (user_id, food_id) values
  ('22222222-2222-4222-8222-222222222222', '0ff00000-0000-4000-8000-000000000001');
select lives_ok(
  $$delete from auth.users where id = '11111111-1111-4111-8111-111111111111'$$,
  'deleting a user with favourites and entries succeeds');
select is(
  (select count(*)::int from public.favorite_foods),
  1, 'only the other user''s favourite survives');

select * from finish();
rollback;
