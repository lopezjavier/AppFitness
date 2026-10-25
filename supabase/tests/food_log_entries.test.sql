-- pgTAP: public.food_log_entries constraints, RLS, cross-user references and the user-deletion cascade
-- (T-055, US-16, US-23, ADR-29).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(34);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64);
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de mi madre', 190, '11111111-1111-4111-8111-111111111111');
insert into public.food_portions (food_id, label, amount, position) values
  ('aaaaaaaa-0000-4000-8000-000000000001', '1 racion', 150, 0);

create temp table slot_ids on commit drop as
  select user_id, code, id from public.meal_slots;
grant select on slot_ids to authenticated;

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select columns_are('public', 'food_log_entries', array[
  'id', 'user_id', 'log_date', 'meal_slot_id', 'food_id', 'amount', 'portion_label', 'food_name', 'kcal',
  'protein_g', 'carbs_g', 'fat_g', 'created_at', 'cooking_method', 'cooking_oil_retained_g', 'group_id', 'group_name'
], 'food_log_entries has exactly the planned columns');
select fk_ok('public', 'food_log_entries', 'user_id', 'auth', 'users', 'id', 'user_id references auth.users.id');
select fk_ok('public', 'food_log_entries', 'food_id', 'public', 'foods', 'id', 'food_id references foods.id');
select has_index('public', 'food_log_entries', 'food_log_entries_user_date_idx', 'index on (user_id, log_date) exists');
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.food_log_entries'::regclass),
  'RLS is enabled and forced on food_log_entries');
select table_privs_are('public', 'food_log_entries', 'anon', array[]::text[],
  'anon has no privileges on food_log_entries');
select ok(
  (select bool_and(confdeltype = 'a') from pg_constraint
    where conrelid = 'public.food_log_entries'::regclass and contype = 'f'
      and confrelid in ('public.foods'::regclass, 'public.meal_slots'::regclass)),
  'the food and meal slot FKs use on delete no action');

-- ---------------------------------------------------------------------------
-- Constraints (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '11111111-1111-4111-8111-111111111111', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 0, 'Leche', 0, 0, 0, 0
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'$$,
  '23514', null, 'amount 0 is rejected');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '11111111-1111-4111-8111-111111111111', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 5001, 'Leche', 0, 0, 0, 0
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'$$,
  '23514', null, 'amount 5001 is rejected');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '11111111-1111-4111-8111-111111111111', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', -1, 0, 0, 0
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'$$,
  '23514', null, 'a negative snapshot nutrient is rejected');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '11111111-1111-4111-8111-111111111111', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 100, '  ', 0, 0, 0, 0
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'$$,
  '23514', null, 'a blank food_name is rejected');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '11111111-1111-4111-8111-111111111111', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  '23503', null, 'an entry cannot point at another user''s meal slot');

-- ---------------------------------------------------------------------------
-- RLS (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$insert into public.food_log_entries (id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g)
    select 'e1000000-0000-4000-8000-000000000001', '2026-10-20', id, 'aaaaaaaa-0000-4000-8000-000000000001', 150, '1 racion',
      'Tortilla de mi madre', 285, 12, 3, 20
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'lunch'$$,
  'A logs their personal food; user_id defaults to the caller');
select lives_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 200, 'Leche entera', 128, 6.6, 9.6, 7
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'$$,
  'A logs an OFF food');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '22222222-2222-4222-8222-222222222222', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  '42501', null, 'A cannot create an entry for B');
select is((select count(*)::int from public.food_log_entries), 2, 'A sees their two entries');

-- B (as user)
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.food_log_entries), 0, 'B does not see A''s entries');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '2026-10-20', id, 'aaaaaaaa-0000-4000-8000-000000000001', 100, 'Tortilla', 127, 8, 2, 13
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  '42501', null, 'B cannot log A''s personal food');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4
    from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'$$,
  '23503', null, 'B cannot log into A''s meal slot (composite FK)');
select results_eq(
  $$with u as (update public.food_log_entries set amount = 1 returning 1) select count(*)::int from u$$,
  $$values (0)$$, 'B updating A''s entries affects 0 rows');
select results_eq(
  $$with d as (delete from public.food_log_entries returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'B deleting A''s entries affects 0 rows');

reset role;
set local role anon;
select throws_ok('select * from public.food_log_entries', '42501', null, 'anon cannot read food_log_entries');
reset role;

-- ---------------------------------------------------------------------------
-- User deletion cascade (ADR-29) with the no-action FKs
-- ---------------------------------------------------------------------------
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select '22222222-2222-4222-8222-222222222222', '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4
  from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast';

select lives_ok(
  $$delete from auth.users where id = '11111111-1111-4111-8111-111111111111'$$,
  'deleting a user with a personal food, portions, slots and entries succeeds');
select is(
  (select count(*)::int from public.food_log_entries where user_id = '11111111-1111-4111-8111-111111111111'),
  0, 'the deleted user''s entries are gone');
select is(
  (select count(*)::int from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111')
  + (select count(*)::int from public.foods where created_by = '11111111-1111-4111-8111-111111111111')
  + (select count(*)::int from public.food_portions where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  0, 'the deleted user''s slots, personal foods and portions are gone');
select is(
  (select count(*)::int from public.food_log_entries where user_id = '22222222-2222-4222-8222-222222222222')
  + (select count(*)::int from public.meal_slots where user_id = '22222222-2222-4222-8222-222222222222'),
  5, 'the other user''s entry and four slots survive');
select is(
  (select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000001'),
  1, 'the shared OFF food is untouched');

-- ---------------------------------------------------------------------------
-- Cooking method and retained oil (T-107, US-50, ADR-60)
-- ---------------------------------------------------------------------------
select enum_has_labels('public', 'food_cooking_method', array['raw', 'griddle', 'air_fryer', 'fried'],
  'food_cooking_method has the four methods');
select results_eq(
  $$select cooking_method::text, cooking_oil_retained_g from public.food_log_entries
    where user_id = '22222222-2222-4222-8222-222222222222'$$,
  $$values ('raw', null::numeric)$$,
  'an entry inserted without cooking columns is raw with no oil');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, cooking_oil_retained_g)
    select '22222222-2222-4222-8222-222222222222', '2026-10-21', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4, 2.5
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  '23514', null, 'oil on a raw entry is rejected');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, cooking_method, cooking_oil_retained_g)
    select '22222222-2222-4222-8222-222222222222', '2026-10-21', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4, 'griddle', -1
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  '23514', null, 'a negative retained oil is rejected');
select lives_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, cooking_method, cooking_oil_retained_g)
    select '22222222-2222-4222-8222-222222222222', '2026-10-21', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4, 'raw', 0
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  'a raw entry with 0 oil is accepted');
select lives_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, cooking_method, cooking_oil_retained_g)
    select '22222222-2222-4222-8222-222222222222', '2026-10-21', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 86.5, 3, 5, 6.5, 'griddle', 2.5
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  'a griddle entry with 2.5 g of oil is accepted');
select throws_ok(
  $$insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, cooking_method)
    select '22222222-2222-4222-8222-222222222222', '2026-10-21', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche', 64, 3, 5, 4, 'boiled'
    from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'$$,
  '22P02', null, 'an unknown cooking method is rejected');

select * from finish();
rollback;
