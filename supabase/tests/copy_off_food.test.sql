-- pgTAP: public.copy_off_food / copy_off_food_core / off_micronutrients (T-254, US-118).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(23);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.foods (id, source, barcode, name, brand, base_unit, kcal_100, protein_100, carbs_100, fat_100,
                          alcohol_g_100, serving_label, serving_amount, image_url, off_data) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Galletas OFF', 'Marca', 'g', 275, 10, 30, 10,
   4.5, '1 galleta', 12.5, 'https://img.example/1.jpg',
   '{"nutriments":{"fiber_100g":2.5,"salt_100g":"0.5","vitamin-c_100ml":0.01,"iron_100g":10000,"zinc_100g":"abc","calcium_100g":-1,"sugars_100g":3.25}}'),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '1000000000002', 'Bebida OFF', null, 'ml', 40, 1, 8, 0.5,
   null, null, null, null,
   '{"nutriments":{"calcium_100ml":0.12,"calcium_100g":0.5,"potassium_100g":0.15}}'),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '1000000000003', 'Sin datos OFF', null, 'g', 100, 5, 10, 3,
   null, null, null, null, null),
  ('0ff00000-0000-4000-8000-000000000004', 'off', '1000000000004', 'Con codigo manual', null, 'g', 100, 5, 10, 3,
   null, null, null, null, null);
insert into public.foods (id, source, barcode, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', '1000000000004', 'Mi manual', 99, '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'user', null, 'De Bea', 10, '22222222-2222-4222-8222-222222222222');
insert into public.food_portions (food_id, label, amount, position) values
  ('0ff00000-0000-4000-8000-000000000001', '1 galleta', 12.5, 0),
  ('0ff00000-0000-4000-8000-000000000001', '3 galletas', 37.5, 1);

create temp table snap (h text);
insert into snap select md5(string_agg(to_jsonb(f)::text, '|' order by f.id))
  from public.foods f where f.source = 'off';
grant all on snap to authenticated;

-- Privileges
select ok(not has_function_privilege('anon', 'public.copy_off_food(uuid)', 'execute'), 'anon cannot execute copy_off_food');
select ok(has_function_privilege('authenticated', 'public.copy_off_food(uuid)', 'execute'),
  'authenticated can execute copy_off_food');
select ok(not has_function_privilege('authenticated', 'public.copy_off_food_core(uuid, uuid, boolean)', 'execute'),
  'authenticated cannot execute the core (a user cannot copy for someone else)');

-- off_micronutrients
select is(public.off_micronutrients((select off_data from public.foods where barcode = '1000000000001'), 'g'),
  '{"fiber_100g":2.5,"salt_100g":0.5,"vitamin-c_100g":0.01,"sugars_100g":3.25}'::jsonb,
  'g food: _100g keys, string numbers accepted, _100ml fallback, out-of-range, negative and non-numeric dropped');
select is(public.off_micronutrients((select off_data from public.foods where barcode = '1000000000002'), 'ml'),
  '{"calcium_100g":0.12,"potassium_100g":0.15}'::jsonb,
  'ml food: _100ml first, _100g fallback');
select is(public.off_micronutrients(null, 'g'), null, 'no off_data gives null');
select is(public.off_micronutrients('{"nutriments":{"sodium_100g":0.2}}', 'g'), null,
  'only unmapped nutrients (sodium) gives null');

-- Ana copies
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

create temp table ids (k text, id uuid);
grant all on ids to authenticated;
insert into ids select 'a1', public.copy_off_food('0ff00000-0000-4000-8000-000000000001');
insert into ids select 'a1b', public.copy_off_food('0ff00000-0000-4000-8000-000000000001');
insert into ids select 'a2', public.copy_off_food('0ff00000-0000-4000-8000-000000000002');
insert into ids select 'a3', public.copy_off_food('0ff00000-0000-4000-8000-000000000003');
insert into ids select 'a4', public.copy_off_food('0ff00000-0000-4000-8000-000000000004');

select results_eq(
  $$ select name, brand, barcode, base_unit::text, kcal_100, protein_100, carbs_100, fat_100, alcohol_g_100,
            serving_label, serving_amount, image_url
     from public.foods where id = (select id from ids where k = 'a1') $$,
  $$ values ('Galletas OFF'::text, 'Marca'::text, '1000000000001'::text, 'g'::text, 275::numeric, 10::numeric,
             30::numeric, 10::numeric, 4.5::numeric, '1 galleta'::text, 12.5::numeric, 'https://img.example/1.jpg'::text) $$,
  'the copy carries name, brand, barcode, macros, alcohol, serving and image');
select results_eq(
  $$ select source::text, created_by, off_data, off_id
     from public.foods where id = (select id from ids where k = 'a1') $$,
  $$ values ('user'::text, '11111111-1111-4111-8111-111111111111'::uuid, null::jsonb, null::text) $$,
  'the copy is a personal food of Ana, without off_data or off_id');
select is((select micronutrients from public.foods where id = (select id from ids where k = 'a1')),
  '{"fiber_100g":2.5,"salt_100g":0.5,"vitamin-c_100g":0.01,"sugars_100g":3.25}'::jsonb,
  'the copy carries the mapped micronutrients');
select is((select micronutrients from public.foods where id = (select id from ids where k = 'a3')), null,
  'an OFF food without nutriments gets null micronutrients');
select results_eq(
  $$ select label, amount, position from public.food_portions
     where food_id = (select id from ids where k = 'a1') order by position $$,
  $$ values ('1 galleta'::text, 12.5::numeric, 0::smallint), ('3 galletas', 37.5, 1::smallint) $$,
  'the portions are copied');

select is((select id from ids where k = 'a1b'), (select id from ids where k = 'a1'), 'a second call returns the same copy');
select is((select count(*) from public.foods where created_by = '11111111-1111-4111-8111-111111111111'
           and barcode = '1000000000001'), 1::bigint, 'two calls give one copy');
select is((select id from ids where k = 'a4'), 'aaaaaaaa-0000-4000-8000-000000000001'::uuid,
  'an own manual food with the same barcode is reused');

-- Rejections
select throws_ok($$ select public.copy_off_food('aaaaaaaa-0000-4000-8000-000000000001') $$, 'P0002', 'food not found',
  'a personal food id is rejected');
select throws_ok($$ select public.copy_off_food('99999999-9999-4999-8999-999999999999') $$, 'P0002', 'food not found',
  'an unknown id is rejected');
select throws_ok($$ select public.copy_off_food(null) $$, 'P0002', 'food not found', 'a null id is rejected');

-- Editable
select lives_ok(
  $$ select public.update_personal_food((select id from ids where k = 'a1'), 'Galletas', 'Marca', 250, 10, 30, 10,
       '[]'::jsonb, '{"fiber_100g":2.5}'::jsonb) $$,
  'the copy is editable with update_personal_food');

-- Bea
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select isnt((select public.copy_off_food('0ff00000-0000-4000-8000-000000000001')),
  (select id from ids where k = 'a1'), 'Bea gets her own separate copy');
select is((select count(*) from public.foods where id in (select id from ids where k in ('a1', 'a2', 'a3'))), 0::bigint,
  'Ana''s copies are invisible to Bea');

-- Unauthenticated
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$ select public.copy_off_food('0ff00000-0000-4000-8000-000000000001') $$, '28000', 'not authenticated',
  'an unauthenticated call raises 28000');

reset role;
select is((select md5(string_agg(to_jsonb(f)::text, '|' order by f.id)) from public.foods f where f.source = 'off'),
  (select h from snap), 'the OFF source rows are unchanged');

select * from finish();
rollback;
