-- pgTAP: save_meal_structure, list_meal_slots_in_use, unique meal names (T-126, US-56).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(33);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64);

create temp table slot_ids on commit drop as
  select user_id, code, id from public.meal_slots;
grant select on slot_ids to authenticated;

-- Ana: one entry in breakfast.
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select user_id, '2026-10-20', id, '0ff00000-0000-4000-8000-000000000001', 200, 'Leche entera', 128, 6.6, 9.6, 7
from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';

create temp table bea_before on commit drop as
  select id, label, position, enabled, code from public.meal_slots
  where user_id = '22222222-2222-4222-8222-222222222222';
grant select on bea_before to authenticated;

create function pg_temp.sl(c text) returns uuid language sql as
  $$ select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = c::public.meal_type $$;
create function pg_temp.nl(l text) returns uuid language sql as
  $$ select id from public.meal_slots where label = l $$;
create function pg_temp.el(i uuid, l text) returns jsonb language sql as
  $$ select jsonb_build_object('id', i, 'label', l) $$;
create function pg_temp.snap() returns text language sql as
  $$ select string_agg(label || ':' || position, ',' order by position) from public.meal_slots $$;
grant execute on function pg_temp.sl(text), pg_temp.nl(text), pg_temp.el(uuid, text), pg_temp.snap()
  to authenticated;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
select ok(has_function_privilege('authenticated', 'public.save_meal_structure(jsonb, uuid[])', 'execute'),
  'authenticated can execute save_meal_structure');
select ok(not has_function_privilege('anon', 'public.save_meal_structure(jsonb, uuid[])', 'execute'),
  'anon cannot execute save_meal_structure');
select ok(not (select prosecdef from pg_proc where proname = 'save_meal_structure'),
  'save_meal_structure is security invoker');
select ok(has_function_privilege('authenticated', 'public.list_meal_slots_in_use()', 'execute'),
  'authenticated can execute list_meal_slots_in_use');
select ok(not has_function_privilege('anon', 'public.list_meal_slots_in_use()', 'execute'),
  'anon cannot execute list_meal_slots_in_use');

-- Unique index backstop and per-user scope (as the table owner).
select throws_ok(
  $$insert into public.meal_slots (user_id, label, position)
    values ('11111111-1111-4111-8111-111111111111', '  DESAYUNO ', 9)$$,
  '23505', null, 'the unique index blocks a direct insert of a duplicate name (case and spaces)');
select lives_ok(
  $$insert into public.meal_slots (user_id, label, position)
    select '22222222-2222-4222-8222-222222222222', 'Extra', 9$$,
  'a name is unique only per user');
select lives_ok(
  $$insert into public.meal_slots (user_id, label, position)
    select '11111111-1111-4111-8111-111111111111', 'Extra', 9$$,
  'the same name for two different users is allowed');
delete from public.meal_slots where label = 'Extra';

-- ---------------------------------------------------------------------------
-- Unauthenticated
-- ---------------------------------------------------------------------------
select throws_ok(
  $$select public.save_meal_structure('[{"id":null,"label":"X"}]'::jsonb, array[]::uuid[])$$,
  '28000', null, 'unauthenticated save is rejected');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  (select array_agg(i) from unnest(public.list_meal_slots_in_use()) i), array[pg_temp.sl('breakfast')],
  'list_meal_slots_in_use returns only the caller''s meals with entries');

-- One call: rename + reorder + add + remove.
select lives_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Cena temprana'), pg_temp.el(pg_temp.sl('breakfast'), 'Desayuno'),
      pg_temp.el(null, 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Comida')),
    array[pg_temp.sl('snack')])$$,
  'rename + reorder + add + remove in one call succeeds');
select is(pg_temp.snap(), 'Cena temprana:0,Desayuno:1,Brunch:2,Comida:3',
  'final order, names and positions are 0..n-1');
select is((select code from public.meal_slots where label = 'Brunch'), null, 'the new meal has a null code');
select is((select code::text from public.meal_slots where id = pg_temp.sl('dinner')), 'dinner',
  'a renamed default keeps its code');
select is((select count(*)::int from public.meal_slots where id = pg_temp.sl('snack')), 0, 'the removed meal is gone');

-- Swap of two names works (no mid-way unique collision).
select lives_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Comida'), pg_temp.el(pg_temp.sl('breakfast'), 'Desayuno'),
      pg_temp.el(pg_temp.nl('Brunch'), 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Cena temprana')),
    array[]::uuid[])$$,
  'swapping two names succeeds');
select is(pg_temp.snap(), 'Comida:0,Desayuno:1,Brunch:2,Cena temprana:3', 'the swap is applied');

-- All-or-nothing: a failing removal leaves renames and order untouched.
create temp table before_snap as select pg_temp.snap() as s;
grant select on before_snap to authenticated;
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.nl('Brunch'), 'Renombrada'), pg_temp.el(pg_temp.sl('lunch'), 'Otra'),
      pg_temp.el(pg_temp.sl('dinner'), 'Comida')),
    array[pg_temp.sl('breakfast')])$$,
  '23514', 'a meal with logged entries cannot be deleted', 'removing a meal with entries is rejected');
select is(pg_temp.snap(), (select s from before_snap), 'a failing removal leaves renames and order untouched');

-- Duplicates.
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Comida'), pg_temp.el(pg_temp.sl('breakfast'), ' comida '),
      pg_temp.el(pg_temp.nl('Brunch'), 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Cena temprana')),
    array[]::uuid[])$$,
  '23505', 'duplicate meal name', 'a duplicate name in the payload (case and spaces) is rejected');
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Comida'), pg_temp.el(pg_temp.sl('breakfast'), 'Desayuno'),
      pg_temp.el(pg_temp.nl('Brunch'), 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Cena temprana'),
      pg_temp.el(null, 'BRUNCH')),
    array[]::uuid[])$$,
  '23505', null, 'a new meal that duplicates an existing name is rejected');
select is(pg_temp.snap(), (select s from before_snap), 'duplicates save nothing');

-- Validation.
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(pg_temp.el(null, '   ')), array[]::uuid[])$$,
  '22023', null, 'a blank label is rejected');
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(pg_temp.el(null, repeat('x', 51))), array[]::uuid[])$$,
  '22023', null, 'a 51-character label is rejected');
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Comida')), array[]::uuid[])$$,
  '22023', null, 'omitting an existing meal that is not in the removed list is rejected');
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Comida'), pg_temp.el(pg_temp.sl('breakfast'), 'Desayuno'),
      pg_temp.el(pg_temp.nl('Brunch'), 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Cena temprana')),
    array[pg_temp.sl('lunch')])$$,
  '22023', null, 'an id both kept and removed is rejected');
select throws_ok(
  $$select public.save_meal_structure('[]'::jsonb, array[]::uuid[])$$,
  '22023', null, 'an empty p_slots is rejected');
select throws_ok(
  $$select public.save_meal_structure('[]'::jsonb,
      (select array_agg(id) from public.meal_slots))$$,
  '23514', 'at least one meal must remain', 'removing every meal is rejected');

-- Cross-user.
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el((select id from bea_before where code = 'breakfast'), 'Hackeado'),
      pg_temp.el(pg_temp.sl('dinner'), 'Comida'), pg_temp.el(pg_temp.sl('breakfast'), 'Desayuno'),
      pg_temp.el(pg_temp.nl('Brunch'), 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Cena temprana')),
    array[]::uuid[])$$,
  'P0002', null, 'a foreign id in p_slots is rejected');
select throws_ok(
  $$select public.save_meal_structure(jsonb_build_array(
      pg_temp.el(pg_temp.sl('dinner'), 'Comida'), pg_temp.el(pg_temp.sl('breakfast'), 'Desayuno'),
      pg_temp.el(pg_temp.nl('Brunch'), 'Brunch'), pg_temp.el(pg_temp.sl('lunch'), 'Cena temprana')),
    array[(select id from bea_before where code = 'snack')])$$,
  'P0002', null, 'a foreign id in p_removed_ids is rejected');

reset role;
select results_eq(
  $$select id, label, position, enabled, code from public.meal_slots
    where user_id = '22222222-2222-4222-8222-222222222222'$$,
  $$select id, label, position, enabled, code from bea_before$$,
  'Bea''s meals are untouched');

-- ---------------------------------------------------------------------------
-- Pre-check guard (index dropped inside the transaction to seed duplicates)
-- ---------------------------------------------------------------------------
select lives_ok($$select public.assert_unique_meal_labels()$$, 'the guard passes without duplicates');
drop index public.meal_slots_user_label_key;
update public.meal_slots set label = 'Cena temprana '
  where user_id = '11111111-1111-4111-8111-111111111111' and label = 'Comida';
update public.meal_slots set label = 'cena temprana'
  where user_id = '11111111-1111-4111-8111-111111111111' and label = 'Brunch';
select throws_ok($$select public.assert_unique_meal_labels()$$, '23505', null,
  'the guard fails loudly when a user has colliding labels');

select * from finish();
rollback;
