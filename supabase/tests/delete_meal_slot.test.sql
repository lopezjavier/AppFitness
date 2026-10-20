-- pgTAP: public.delete_meal_slot RPC (T-067, US-23, ADR-41).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(16);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'eva@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64);

create temp table slot_ids on commit drop as
  select user_id, code, id from public.meal_slots;
grant select on slot_ids to authenticated;

-- Ana: an entry in lunch on an old day.
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select user_id, '2026-01-05', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche entera', 64, 3.3, 4.8, 3.5
from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'lunch';

create temp table bea_before on commit drop as
  select id, label, position, enabled, code from public.meal_slots
  where user_id = '22222222-2222-4222-8222-222222222222';
grant select on bea_before to authenticated;

create function pg_temp.sl(c text) returns uuid language sql as
  $$ select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = c::public.meal_type $$;
create function pg_temp.snap() returns text language sql as
  $$ select string_agg(coalesce(code::text, label) || ':' || position, ',' order by position) from public.meal_slots $$;
grant execute on function pg_temp.sl(text), pg_temp.snap() to authenticated;

-- Privileges
select ok(has_function_privilege('authenticated', 'public.delete_meal_slot(uuid)', 'execute'),
  'authenticated can execute delete_meal_slot');
select ok(not has_function_privilege('anon', 'public.delete_meal_slot(uuid)', 'execute'),
  'anon cannot execute delete_meal_slot');
select ok(not (select prosecdef from pg_proc where proname = 'delete_meal_slot'),
  'delete_meal_slot is security invoker');

-- Unauthenticated call
select throws_ok($$select public.delete_meal_slot('00000000-0000-4000-8000-000000000000')$$,
  '28000', null, 'a call without a session is rejected');

-- As user A
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select throws_ok($$select public.delete_meal_slot(gen_random_uuid())$$,
  'P0002', null, 'a random uuid is not found');

select throws_ok(
  $$select public.delete_meal_slot((select id from bea_before where code = 'breakfast'))$$,
  'P0002', null, 'another user''s slot is not found');

select throws_ok($$select public.delete_meal_slot(pg_temp.sl('lunch'))$$,
  '23514', null, 'a meal with entries on any day cannot be deleted');
select is((select count(*)::int from public.meal_slots), 4, 'the rejected delete removed nothing');

select lives_ok($$select public.delete_meal_slot(pg_temp.sl('snack'))$$, 'an unused meal is deleted');
select is(pg_temp.snap(), 'breakfast:0,lunch:1,dinner:2',
  'the remaining meals are re-packed to 0..n-1 in order');

select lives_ok($$select public.delete_meal_slot(pg_temp.sl('breakfast'))$$, 'deleting the first meal succeeds');
select is(pg_temp.snap(), 'lunch:0,dinner:1', 'positions are re-packed after deleting the first meal');

select lives_ok($$select public.delete_meal_slot(pg_temp.sl('dinner'))$$, 'deleting down to one meal succeeds');
select throws_ok($$select public.delete_meal_slot(pg_temp.sl('lunch'))$$,
  '23514', null, 'the only remaining meal cannot be deleted');

reset role;
select results_eq(
  $$select id, label, position, enabled, code from public.meal_slots
    where user_id = '22222222-2222-4222-8222-222222222222'$$,
  $$select id, label, position, enabled, code from bea_before$$,
  'Bea''s meals are untouched');
select is((select count(*)::int from public.meal_slots where user_id = '33333333-3333-4333-8333-333333333333'), 4,
  'Eva''s meals are untouched');

select * from finish();
rollback;
