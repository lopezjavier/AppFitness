-- pgTAP: update_personal_food and the full label whitelist of is_valid_micronutrients (T-102, US-47, ADR-53).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(70);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;

insert into public.foods (id, source, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100, barcode, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Yogur casero', 'Casa', 'g', 60, 3, 4, 3, '8400000000001', '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'user', 'Pan de Bea', null, 'g', 250, 8, 50, 2, null, '22222222-2222-4222-8222-222222222222');
insert into public.foods (id, source, barcode, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Producto OFF', 50);
insert into public.food_portions (food_id, label, amount, position) values
  ('aaaaaaaa-0000-4000-8000-000000000001', '1 tarrina', 125, 0),
  ('aaaaaaaa-0000-4000-8000-000000000001', '1 cucharada', 15, 1),
  ('bbbbbbbb-0000-4000-8000-000000000001', '1 rebanada', 30, 0);
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g)
  select 'e1000000-0000-4000-8000-000000000001', user_id, '2026-10-21', id, 'aaaaaaaa-0000-4000-8000-000000000001',
         125, '1 tarrina', 'Yogur casero', 75, 3.75, 5, 3.75
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select 'e1000000-0000-4000-8000-000000000002', user_id, '2020-01-02', id, 'aaaaaaaa-0000-4000-8000-000000000001',
         100, 'Yogur casero', 60, 3, 4, 3
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';

-- T-104 fixtures: 02 unused (g), 03 only in a comida (g), 04 favourite only (ml), 05 only an entry dated 2020 (g)
insert into public.foods (id, source, name, base_unit, kcal_100, protein_100, carbs_100, fat_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000002', 'user', 'Sin uso', 'g', 100, 1, 2, 3, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000003', 'user', 'Solo comida', 'g', 100, 1, 2, 3, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000004', 'user', 'Solo favorito', 'ml', 100, 1, 2, 3, '11111111-1111-4111-8111-111111111111'),
  ('aaaaaaaa-0000-4000-8000-000000000005', 'user', 'Solo antiguo', 'g', 100, 1, 2, 3, '11111111-1111-4111-8111-111111111111');
insert into public.food_portions (food_id, label, amount, position) values
  ('aaaaaaaa-0000-4000-8000-000000000002', '1 unidad', 40, 0),
  ('aaaaaaaa-0000-4000-8000-000000000003', '1 unidad', 40, 0);
insert into public.favorite_foods (user_id, food_id) values
  ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-0000-4000-8000-000000000004');
insert into public.meal_templates (id, user_id, name)
  values ('cccccccc-0000-4000-8000-0000000000a1', '11111111-1111-4111-8111-111111111111', 'Comida de prueba');
insert into public.meal_template_items (template_id, food_id, amount, position)
  values ('cccccccc-0000-4000-8000-0000000000a1', 'aaaaaaaa-0000-4000-8000-000000000003', 50, 0);
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select 'e1000000-0000-4000-8000-000000000003', user_id, '2020-01-03', id, 'aaaaaaaa-0000-4000-8000-000000000005',
         100, 'Solo antiguo', 100, 1, 2, 3
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';

create temp table snap (k text, h text);
insert into snap select 'before', md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e;
grant all on snap to authenticated;

select has_function('public', 'update_personal_food',
  array['uuid', 'text', 'text', 'numeric', 'numeric', 'numeric', 'numeric', 'jsonb', 'jsonb', 'text'], 'update_personal_food exists');
select hasnt_function('public', 'update_personal_food',
  array['uuid', 'text', 'text', 'numeric', 'numeric', 'numeric', 'numeric', 'jsonb', 'jsonb'], 'the 9-argument version is gone');
select has_function('public', 'personal_food_in_use', array['uuid'], 'personal_food_in_use exists');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Owner edit ---------------------------------------------------------------
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', '  Yogur casero 2 ', 'Nueva', 70, 3.5, 4.7, 3,
      '[{"label":"1 tarrina","amount":120},{"label":"1 vaso","amount":200}]'::jsonb,
      '{"sugars_100g":4.7,"vitamin-c_100g":0.0000015}'::jsonb)$$,
  'the owner edits own manual food');
select is((select name from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'Yogur casero 2', 'name is trimmed and stored');
select is((select brand from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'Nueva', 'brand stored');
select is((select kcal_100 from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 70::numeric, 'kcal stored');
select is((select protein_100 from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 3.5::numeric, 'protein stored');
select is((select carbs_100 from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 4.7::numeric, 'carbs stored');
select is((select fat_100 from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 3::numeric, 'fat stored');
select is((select (micronutrients ->> 'vitamin-c_100g')::numeric from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  0.0000015::numeric, 'a tiny value is stored without loss');
select is(
  (select jsonb_agg(jsonb_build_array(label, amount, position) order by position) from public.food_portions
   where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  '[["1 tarrina",120,0],["1 vaso",200,1]]'::jsonb, 'portions are replaced, in order');
select is(
  (select base_unit::text || '|' || barcode || '|' || source || '|' || created_by::text from public.foods
   where id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  'g|8400000000001|user|11111111-1111-4111-8111-111111111111', 'base unit, barcode, source and created_by are unchanged');

-- Past entries unchanged ----------------------------------------------------
insert into snap select 'after', md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e;
select is((select h from snap where k = 'after'), (select h from snap where k = 'before'),
  'log entries (including one on an old date) are byte-identical after an edit');
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'Yogur casero', null, 70, 3.5, 4.7, 3, '[]'::jsonb, null)$$,
  'clearing all portions works');
select is((select count(*)::int from public.food_portions where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 0, 'portions removed');
select is((select micronutrients from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), null, 'null micronutrients clears them');
insert into snap select 'after2', md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e;
select is((select h from snap where k = 'after2'), (select h from snap where k = 'before'),
  'log entries are unchanged after removing a portion');

-- Atomicity -----------------------------------------------------------------
select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'Estable', null, 10, 1, 1, 1,
  '[{"label":"A","amount":1}]'::jsonb, '{"salt_100g":1}'::jsonb);
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'Roto', null, 99, 9, 9, 9,
      '[{"label":"B","amount":2},{"label":"C","amount":0}]'::jsonb, null)$$,
  '22023', 'invalid portion', 'an invalid second portion raises');
select is((select name || kcal_100::int::text from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'Estable10',
  'the food keeps its previous values after a failed edit');
select is((select string_agg(label, ',') from public.food_portions where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'A',
  'the portions keep their previous values after a failed edit');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1,
      '[{"label":"","amount":1}]'::jsonb, null)$$,
  '22023', 'invalid portion', 'an empty portion label raises');
select throws_ok(
  format($$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, %L::jsonb, null)$$,
    (select jsonb_agg(jsonb_build_object('label', 'p' || i, 'amount', 1))::text from generate_series(1, 11) i)),
  '22023', 'invalid portions', '11 portions raise');
select is((select name from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'Estable', 'still unchanged after the rejected calls');

-- Refusals --------------------------------------------------------------------
select throws_ok(
  $$select public.update_personal_food('0ff00000-0000-4000-8000-000000000001', 'Hack', null, 1, 1, 1, 1, '[]'::jsonb, null)$$,
  'P0002', 'food not found', 'an OFF food cannot be edited');
select throws_ok(
  $$select public.update_personal_food('bbbbbbbb-0000-4000-8000-000000000001', 'Hack', null, 1, 1, 1, 1, '[]'::jsonb, null)$$,
  'P0002', 'food not found', 'another user''s food cannot be edited');
select throws_ok(
  $$select public.update_personal_food('cccccccc-0000-4000-8000-000000000001', 'Hack', null, 1, 1, 1, 1, '[]'::jsonb, null)$$,
  'P0002', 'food not found', 'a missing food raises not found');

-- Whitelist, in both RPCs -----------------------------------------------------
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, '{"vitamin-zz_100g":1}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'update rejects an unknown key');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, '{"salt_100g":-1}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'update rejects a negative value');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, '{"salt_100g":"a"}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'update rejects a string value');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, '{"salt_100g":10000}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'update rejects a value above 9999.99');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, '[1]'::jsonb)$$,
  '22023', 'invalid micronutrients', 'update rejects a non-object');
select throws_ok(
  $$select public.create_personal_food('Mala', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"vitamin-zz_100g":1}'::jsonb)$$,
  '22023', 'invalid micronutrients', 'create rejects an unknown key');

create temp table keys (k text);
insert into keys values
  ('saturated-fat'), ('monounsaturated-fat'), ('polyunsaturated-fat'), ('sugars'), ('polyols'), ('starch'), ('fiber'), ('salt'),
  ('vitamin-a'), ('vitamin-d'), ('vitamin-e'), ('vitamin-k'), ('vitamin-c'), ('vitamin-b1'), ('vitamin-b2'), ('vitamin-pp'),
  ('vitamin-b6'), ('vitamin-b9'), ('vitamin-b12'), ('biotin'), ('pantothenic-acid'),
  ('potassium'), ('chloride'), ('calcium'), ('phosphorus'), ('magnesium'), ('iron'), ('zinc'), ('copper'), ('manganese'),
  ('fluoride'), ('selenium'), ('chromium'), ('molybdenum'), ('iodine');
grant all on keys to authenticated;
select is(
  (select count(*)::int from keys where public.is_valid_micronutrients(jsonb_build_object(k || '_100g', 1.5))), 35,
  'every whitelisted label key is accepted');
select is(public.is_valid_micronutrients((select jsonb_object_agg(k || '_100g', 0.5) from keys)), true,
  'all 35 keys together are accepted');
select lives_ok(
  $$select public.create_personal_food('Completo', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb, '{"vitamin-b12_100g":0.000002,"iodine_100g":0.00015}'::jsonb)$$,
  'create accepts the extended list');

-- Unit change (US-52) -------------------------------------------------------------
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000002', 'Sin uso', null, 100, 1, 2, 3,
      '[{"label":"1 unidad","amount":40}]'::jsonb, null, 'ml')$$,
  'an unreferenced food changes g to ml');
select is((select base_unit::text from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000002'), 'ml', 'the new unit is stored');
select is(
  (select kcal_100::int || '|' || protein_100::int || '|' || carbs_100::int || '|' || fat_100::int || '|'
     || (select string_agg(label || amount::int, ',') from public.food_portions where food_id = f.id)
   from public.foods f where f.id = 'aaaaaaaa-0000-4000-8000-000000000002'),
  '100|1|2|3|1 unidad40', 'values and portions are kept as sent when the unit changes');
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000002', 'Sin uso', null, 100, 1, 2, 3, '[]'::jsonb, null, null)$$,
  'a null unit is accepted');
select is((select base_unit::text from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000002'), 'ml', 'a null unit keeps the stored one');
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000002', 'Sin uso', null, 100, 1, 2, 3, '[]'::jsonb, null, 'g')$$,
  'an unreferenced food changes ml to g');
select is((select base_unit::text from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000002'), 'g', 'ml to g is stored');
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000004', 'Solo favorito', null, 100, 1, 2, 3, '[]'::jsonb, null, 'g')$$,
  'a favourite-only food changes its unit');
select is((select base_unit::text from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000004'), 'g', 'favourites do not block the unit');

select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'Cambiado', null, 99, 1, 1, 1,
      '[{"label":"Z","amount":9}]'::jsonb, null, 'ml')$$,
  '23514', 'food unit locked: referenced', 'a food with a diary entry refuses a unit change');
select is(
  (select name || '|' || kcal_100::int || '|' || base_unit::text || '|' || (select string_agg(label, ',') from public.food_portions where food_id = f.id)
   from public.foods f where f.id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  'Estable|10|g|A', 'the refused call rolled back name, values, portions and unit');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000005', 'Cambiado', null, 99, 1, 1, 1, '[]'::jsonb, null, 'ml')$$,
  '23514', 'food unit locked: referenced', 'an entry dated in 2020 also blocks the unit change');
select is((select name || '|' || base_unit::text from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000005'),
  'Solo antiguo|g', 'the old-entry food is unchanged after the refusal');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000003', 'Cambiado', null, 99, 1, 1, 1, '[]'::jsonb, null, 'ml')$$,
  '23514', 'food unit locked: referenced', 'a food in a comida refuses a unit change');
select is(
  (select name || '|' || kcal_100::int || '|' || base_unit::text || '|' || (select count(*) from public.food_portions where food_id = f.id)
   from public.foods f where f.id = 'aaaaaaaa-0000-4000-8000-000000000003'),
  'Solo comida|100|g|1', 'the comida food keeps name, values, portions and unit after the refusal');
select lives_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'Estable 2', null, 11, 1, 1, 1, '[]'::jsonb, null, 'g')$$,
  'a referenced food can be edited when the unit stays the same');
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000002', 'X', null, 1, 1, 1, 1, '[]'::jsonb, null, 'kg')$$,
  '22023', 'invalid base unit', 'a unit other than g or ml is rejected');
select throws_ok(
  $$select public.update_personal_food('0ff00000-0000-4000-8000-000000000001', 'Hack', null, 1, 1, 1, 1, '[]'::jsonb, null, 'ml')$$,
  'P0002', 'food not found', 'an OFF food unit cannot be changed');
select throws_ok(
  $$select public.update_personal_food('bbbbbbbb-0000-4000-8000-000000000001', 'Hack', null, 1, 1, 1, 1, '[]'::jsonb, null, 'ml')$$,
  'P0002', 'food not found', 'another user''s food unit cannot be changed');
insert into snap select 'after3', md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e;
select is((select h from snap where k = 'after3'), (select h from snap where k = 'before'),
  'log entries are byte-identical after all the unit attempts');

-- personal_food_in_use ----------------------------------------------------------------
select is(public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000002'), false, 'an unused food is not in use');
select is(public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000004'), false, 'a favourite-only food is not in use');
select is(public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000001'), true, 'a food with an entry is in use');
select is(public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000003'), true, 'a food in a comida is in use');
select throws_ok($$select public.personal_food_in_use('0ff00000-0000-4000-8000-000000000001')$$,
  'P0002', 'food not found', 'an OFF food raises not found');
select throws_ok($$select public.personal_food_in_use('bbbbbbbb-0000-4000-8000-000000000001')$$,
  'P0002', 'food not found', 'another user''s food raises not found');
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is(public.personal_food_in_use('bbbbbbbb-0000-4000-8000-000000000001'), false, 'user B sees false for own unreferenced food');
select throws_ok($$select public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000001')$$,
  'P0002', 'food not found', 'user B cannot probe user A''s food');
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Cross-user isolation ----------------------------------------------------------
select is((select name from public.foods where id = 'bbbbbbbb-0000-4000-8000-000000000001'), null,
  'user A cannot even read user B''s manual food');
reset role;
select is((select (select name from public.foods where id = 'bbbbbbbb-0000-4000-8000-000000000001') || '|'
  || (select string_agg(label, ',') from public.food_portions where food_id = 'bbbbbbbb-0000-4000-8000-000000000001')),
  'Pan de Bea|1 rebanada',
  'user B''s food and portions are untouched after A''s attempts');

-- Unauthenticated / anon ----------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, null)$$,
  '28000', 'not authenticated', 'an unauthenticated call is rejected');
reset role;
set local role anon;
select throws_ok(
  $$select public.update_personal_food('aaaaaaaa-0000-4000-8000-000000000001', 'X', null, 1, 1, 1, 1, '[]'::jsonb, null)$$,
  '42501', null, 'anon cannot execute update_personal_food');
select throws_ok(
  $$select public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000001')$$,
  '42501', null, 'anon cannot execute personal_food_in_use');
reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.personal_food_in_use('aaaaaaaa-0000-4000-8000-000000000001')$$,
  '28000', 'not authenticated', 'an unauthenticated personal_food_in_use call is rejected');
reset role;

select * from finish();
rollback;
