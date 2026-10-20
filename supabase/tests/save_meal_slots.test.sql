-- pgTAP: public.save_meal_slots atomic RPC (T-059, US-23).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(29);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64);

create temp table slot_ids on commit drop as
  select user_id, code, id from public.meal_slots;
grant select on slot_ids to authenticated;

-- Ana: one entry in breakfast on 2026-10-20 and one in lunch on 2026-10-19.
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select user_id, '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 200, 'Leche entera', 128, 6.6, 9.6, 7
from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select user_id, '2026-10-19', id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche entera', 64, 3.3, 4.8, 3.5
from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'lunch';

create temp table bea_before on commit drop as
  select id, label, position, enabled, code from public.meal_slots
  where user_id = '22222222-2222-4222-8222-222222222222';
grant select on bea_before to authenticated;

-- Helpers: id of a default slot of Ana, id of a slot by label, one array element, and a snapshot of her slots.
create function pg_temp.sl(c text) returns uuid language sql as
  $$ select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = c::public.meal_type $$;
create function pg_temp.nl(l text) returns uuid language sql as
  $$ select id from public.meal_slots where label = l $$;
create function pg_temp.el(i uuid, l text, e boolean default true) returns jsonb language sql as
  $$ select jsonb_build_object('id', i, 'label', l, 'enabled', e) $$;
create function pg_temp.snap() returns text language sql as
  $$ select string_agg(label || ':' || enabled || ':' || position, ',' order by position) from public.meal_slots $$;
grant execute on function pg_temp.sl(text), pg_temp.nl(text), pg_temp.el(uuid, text, boolean), pg_temp.snap()
  to authenticated;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
select ok(
  has_function_privilege('authenticated', 'public.save_meal_slots(jsonb,date)', 'execute'),
  'authenticated can execute save_meal_slots');
select ok(
  not has_function_privilege('anon', 'public.save_meal_slots(jsonb,date)', 'execute'),
  'anon cannot execute save_meal_slots');
select ok(
  not (select prosecdef from pg_proc where proname = 'save_meal_slots'),
  'save_meal_slots is security invoker');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Rename keeps the row id and the entries' snapshot totals.
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), '  Post-entreno '), pg_temp.el(pg_temp.sl('lunch'), 'Comida'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda'), pg_temp.el(pg_temp.sl('dinner'), 'Cena')), '2026-10-20')$$,
  'renaming a meal succeeds');
select is((select label from public.meal_slots where id = pg_temp.sl('breakfast')), 'Post-entreno',
  'the rename keeps the same row id and stores the trimmed label');
select is((select kcal from public.food_log_entries where meal_slot_id = pg_temp.sl('breakfast')), 128.0::numeric,
  'the entries keep their snapshot totals after the rename');

-- Insert in the middle: contiguous positions, null code.
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(null, 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('snack'), 'Merienda'),
      pg_temp.el(pg_temp.sl('dinner'), 'Cena')), '2026-10-20')$$,
  'adding a custom meal in the middle succeeds');
select is(pg_temp.snap(),
  'Post-entreno:true:0,Segundo desayuno:true:1,Comida:true:2,Merienda:true:3,Cena:true:4',
  'the new meal sits in the middle and positions are contiguous 0..n-1');
select is((select code from public.meal_slots where label = 'Segundo desayuno'), null,
  'the custom meal has a null code');
select is((select code::text from public.meal_slots where id = pg_temp.sl('lunch')), 'lunch',
  'default meals keep their code');

-- Reorder: Cena above Merienda.
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-20')$$,
  'reordering succeeds');
select is(pg_temp.snap(),
  'Post-entreno:true:0,Segundo desayuno:true:1,Comida:true:2,Cena:true:3,Merienda:true:4',
  'Cena is now above Merienda');

-- Disable an empty slot and re-enable it keeping the label.
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda', false)), '2026-10-20')$$,
  'disabling an empty meal succeeds');
select is((select enabled from public.meal_slots where id = pg_temp.sl('snack')), false, 'Merienda is disabled');
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda', true)), '2026-10-20')$$,
  're-enabling succeeds');
select is((select label || ':' || enabled from public.meal_slots where id = pg_temp.sl('snack')), 'Merienda:true',
  'the re-enabled meal keeps its label');

-- Disabling a slot with entries on the viewed day is blocked and changes nothing.
create temp table before_snap as select pg_temp.snap() as s;
grant select on before_snap to authenticated;
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Renombrado', false), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-20')$$,
  '23514', null, 'disabling a meal with entries on the viewed day is rejected');
select is(pg_temp.snap(), (select s from before_snap), 'the rejected save leaves everything unchanged (atomic)');

-- Entries only on another day do not block disabling.
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida', false), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-20')$$,
  'a meal with entries only on another day can be disabled');

-- Validation errors raise and save nothing.
create temp table before_snap2 as select pg_temp.snap() as s;
grant select on before_snap2 to authenticated;
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Cambiado'), pg_temp.el(pg_temp.nl('Segundo desayuno'), '   '),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida', false), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-20')$$,
  '22023', null, 'a blank label is rejected');
select is(pg_temp.snap(), (select s from before_snap2), 'a valid first row plus a blank second saves nothing');
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno', false), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno', false),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida', false), pg_temp.el(pg_temp.sl('dinner'), 'Cena', false),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda', false)), '2026-10-21')$$,
  '22023', null, 'having no enabled meal is rejected');
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(pg_temp.sl('lunch'), 'Comida')), '2026-10-21')$$,
  '22023', null, 'omitting existing meals is rejected (nothing is deleted)');
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), 'Post-entreno'), pg_temp.el(pg_temp.sl('breakfast'), 'Otra vez'),
      pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'), pg_temp.el(pg_temp.sl('lunch'), 'Comida'),
      pg_temp.el(pg_temp.sl('dinner'), 'Cena'), pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-21')$$,
  '22023', null, 'duplicate ids are rejected');
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), repeat('x', 51)), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-21')$$,
  '22023', null, 'a 51-character label is rejected');
select lives_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el(pg_temp.sl('breakfast'), repeat('x', 50)), pg_temp.el(pg_temp.nl('Segundo desayuno'), 'Segundo desayuno'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida'), pg_temp.el(pg_temp.sl('dinner'), 'Cena'),
      pg_temp.el(pg_temp.sl('snack'), 'Merienda')), '2026-10-21')$$,
  'a 50-character label is accepted');
select throws_ok(
  $$select public.save_meal_slots('{"a":1}'::jsonb, '2026-10-21')$$,
  '22023', null, 'a non-array input is rejected');

-- Cross-user: Ana passes Bea's slot id.
select throws_ok(
  $$select public.save_meal_slots(jsonb_build_array(
      pg_temp.el((select id from bea_before where code = 'breakfast'), 'Hackeado'),
      pg_temp.el(pg_temp.sl('lunch'), 'Comida')), '2026-10-21')$$,
  'P0002', null, 'using another user''s slot id is rejected');

reset role;
select results_eq(
  $$select id, label, position, enabled, code from public.meal_slots
    where user_id = '22222222-2222-4222-8222-222222222222'$$,
  $$select id, label, position, enabled, code from bea_before$$,
  'Bea''s meals are untouched');

select * from finish();
rollback;
