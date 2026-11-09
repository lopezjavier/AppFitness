-- pgTAP: the food energy check is a hard block on write (T-257, US-116, US-117, ADR-104; rule in 07 section 4).
-- create_personal_food, update_personal_food and copy_off_food raise FD001 with the detail json; legacy rows are untouched.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(49);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;

-- Test helper: runs a statement and returns the DETAIL of the error it raises (null when it does not raise).
create function public.t_detail(p_sql text) returns text language plpgsql security invoker as $$
declare v_detail text;
begin
  execute p_sql;
  return null;
exception when others then
  get stacked diagnostics v_detail = pg_exception_detail;
  return v_detail;
end;
$$;
grant execute on function public.t_detail(text) to authenticated;

-- Legacy failing food of Ana with a diary snapshot (inserted directly: existing data is never rewritten).
insert into public.foods (id, source, name, base_unit, kcal_100, protein_100, carbs_100, fat_100, created_by) values
  ('f0000000-0000-4000-8000-000000000001', 'user', 'Mala', 'g', 500, 10, 30, 10, '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'user', 'Mala de Bea', 'g', 500, 10, 30, 10, '22222222-2222-4222-8222-222222222222'),
  ('aaaaaaaa-0000-4000-8000-000000000222', 'user', 'Copia mala', 'g', 500, 10, 30, 10, '11111111-1111-4111-8111-111111111111');
update public.foods set barcode = '2000000000002'
  where id = 'aaaaaaaa-0000-4000-8000-000000000222';
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select 'e1000000-0000-4000-8000-000000000001', user_id, '2027-01-20', id, 'f0000000-0000-4000-8000-000000000001',
         100, 'Mala', 500, 10, 30, 10
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';

insert into public.foods (id, source, barcode, name, base_unit, kcal_100, protein_100, carbs_100, fat_100, off_data) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '2000000000001', 'OFF mala', 'g', 500, 10, 30, 10, null),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '2000000000002', 'OFF con copia mala', 'g', 500, 10, 30, 10, null),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '2000000000003', 'OFF crujiente con fibra', 'g', 350, 12, 45, 6,
   '{"nutriments":{"fiber_100g":30}}'),
  ('0ff00000-0000-4000-8000-000000000004', 'off', '2000000000004', 'OFF crujiente sin fibra', 'g', 350, 12, 45, 6, null),
  ('0ff00000-0000-4000-8000-000000000005', 'off', '2000000000005', 'OFF caramelos con polioles', 'g', 240, 0, 95, 0,
   '{"nutriments":{"polyols_100g":90}}');

create temp table snap (k text, h text);
grant all on snap to authenticated;
insert into snap select 'entries', md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e;

-- ---------------------------------------------------------------------------
-- Schema: the "saved anyway" machinery is gone, the check stays
-- ---------------------------------------------------------------------------
select hasnt_column('public', 'foods', 'energy_check_saved_anyway', 'the saved-anyway column is gone');
select has_column('public', 'foods', 'alcohol_g_100', 'alcohol stays');
select hasnt_function('public', 'food_energy_audit', 'the audit RPC is gone');
select hasnt_function('public', 'foods_energy_flag', 'the flag trigger function is gone');
select is((select count(*) from pg_trigger where tgname = 'foods_energy_flag'), 0::bigint, 'the flag trigger is gone');
select hasnt_function('public', 'create_personal_food',
  array['text', 'text', 'food_base_unit', 'numeric', 'numeric', 'numeric', 'numeric', 'text', 'jsonb', 'jsonb', 'numeric', 'boolean'],
  'the 12-argument create is gone');
select hasnt_function('public', 'update_personal_food',
  array['uuid', 'text', 'text', 'numeric', 'numeric', 'numeric', 'numeric', 'jsonb', 'jsonb', 'text', 'numeric', 'boolean'],
  'the 12-argument update is gone');
select hasnt_function('public', 'copy_off_food_core', array['uuid', 'uuid'], 'the 2-argument core is gone');
select has_function('public', 'food_energy_check',
  array['numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'numeric'], 'food_energy_check stays');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- ---------------------------------------------------------------------------
-- create_personal_food
-- ---------------------------------------------------------------------------
select throws_ok(
  $$ select public.create_personal_food('Epica', null, 'g', 500, 10, 30, 10, null, '[]'::jsonb) $$,
  'FD001', 'food energy check failed', 'create rejects the epic case (500 kcal, 10/30/10 g)');
select is(public.t_detail($$ select public.create_personal_food('Epica', null, 'g', 500, 10, 30, 10, null, '[]'::jsonb) $$)::jsonb,
  '{"result":"clearly_wrong","kcal":500,"computed":250,"gap":-250}'::jsonb,
  'the detail carries result, declared kcal, computed and gap');
select is((select count(*) from public.foods where name = 'Epica'), 0::bigint, 'a rejected create leaves no food row');
select is((select count(*) from public.food_portions where label = 'porcion fantasma'), 0::bigint, 'and no portion');
select throws_ok(
  $$ select public.create_personal_food('Epica', null, 'g', 500, 10, 30, 10, null, '[{"label":"porcion fantasma","amount":10}]'::jsonb) $$,
  'FD001', null, 'create with portions is rejected too');
select is((select count(*) from public.food_portions where label = 'porcion fantasma'), 0::bigint, 'no portion left behind');

select lives_ok($$ select public.create_personal_food('Limite', null, 'g', 100, 30, 0, 0, null, '[]'::jsonb) $$,
  'create accepts a gap exactly on 0.15 * kcal + 5');
select is(public.t_detail($$ select public.create_personal_food('Sobre limite', null, 'g', 100, 30.0025, 0, 0, null, '[]'::jsonb) $$)::jsonb ->> 'result',
  'mismatch', 'create rejects a gap just above 0.15 * kcal + 5 as mismatch');
select is(public.t_detail($$ select public.create_personal_food('Limite 2', null, 'g', 100, 33.75, 0, 0, null, '[]'::jsonb) $$)::jsonb ->> 'result',
  'mismatch', 'create rejects exactly 0.30 * kcal + 5 as mismatch');
select is(public.t_detail($$ select public.create_personal_food('Sobre 2', null, 'g', 100, 33.7525, 0, 0, null, '[]'::jsonb) $$)::jsonb ->> 'result',
  'clearly_wrong', 'create rejects just above 0.30 * kcal + 5 as clearly wrong');
select lives_ok($$ select public.create_personal_food('Cafe negro', null, 'ml', 2, 0.1, 0.3, 0, null, '[]'::jsonb) $$,
  'create accepts a skipped food (black coffee)');
select lives_ok(
  $$ select public.create_personal_food('Vino', null, 'ml', 83, 0.1, 2.6, 0, null, '[]'::jsonb, null, 9.5) $$,
  'create accepts red wine with its alcohol');
select throws_ok($$ select public.create_personal_food('Vino sin alcohol', null, 'ml', 83, 0.1, 2.6, 0, null, '[]'::jsonb) $$,
  'FD001', null, 'create rejects red wine without alcohol');
select lives_ok(
  $$ select public.create_personal_food('Crujiente', null, 'g', 350, 12, 45, 6, null, '[]'::jsonb, '{"fiber_100g":30}'::jsonb) $$,
  'create accepts crispbread with fibre');
select throws_ok($$ select public.create_personal_food('Crujiente sin', null, 'g', 350, 12, 45, 6, null, '[]'::jsonb) $$,
  'FD001', null, 'create rejects crispbread without fibre');
select lives_ok(
  $$ select public.create_personal_food('Caramelos', null, 'g', 240, 0, 95, 0, null, '[]'::jsonb, '{"polyols_100g":90}'::jsonb) $$,
  'create accepts sugar-free sweets with polyols');
select throws_ok($$ select public.create_personal_food('Caramelos sin', null, 'g', 240, 0, 95, 0, null, '[]'::jsonb) $$,
  'FD001', null, 'create rejects sugar-free sweets without polyols');

-- ---------------------------------------------------------------------------
-- update_personal_food: the check runs on the post-write values, before the write
-- ---------------------------------------------------------------------------
select throws_ok(
  $$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 480, 10, 30, 10, '[]'::jsonb) $$,
  'FD001', 'food energy check failed', 'update from a failing state into another failing state is rejected');
select throws_ok(
  $$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Solo cambio el nombre', null, 500, 10, 30, 10, '[]'::jsonb) $$,
  'FD001', null, 'editing only the name of a legacy failing food is rejected (values after the write are checked)');
select is((select name || '|' || kcal_100::int from public.foods where id = 'f0000000-0000-4000-8000-000000000001'), 'Mala|500',
  'a rejected update leaves the old values');
select is(public.t_detail($$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10, '[]'::jsonb) $$)::jsonb,
  '{"result":"clearly_wrong","kcal":500,"computed":250,"gap":-250}'::jsonb, 'the update detail has the same shape');
select lives_ok(
  $$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Arreglada', null, 250, 10, 30, 10, '[]'::jsonb) $$,
  'update into a consistent state is accepted');
select is((select name || '|' || kcal_100::int from public.foods where id = 'f0000000-0000-4000-8000-000000000001'), 'Arreglada|250',
  'and stored');
select throws_ok(
  $$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Arreglada', null, 350, 12, 45, 6, '[]'::jsonb) $$,
  'FD001', null, 'a consistent food edited into crispbread-like values without fibre is rejected');
select lives_ok(
  $$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Arreglada', null, 350, 12, 45, 6, '[]'::jsonb,
       '{"fiber_100g":30}'::jsonb) $$,
  'the same values with fibre are accepted');

-- ---------------------------------------------------------------------------
-- copy_off_food
-- ---------------------------------------------------------------------------
select throws_ok($$ select public.copy_off_food('0ff00000-0000-4000-8000-000000000001') $$,
  'FD001', 'food energy check failed', 'copy rejects a failing OFF food');
select is(public.t_detail($$ select public.copy_off_food('0ff00000-0000-4000-8000-000000000001') $$)::jsonb,
  '{"result":"clearly_wrong","kcal":500,"computed":250,"gap":-250}'::jsonb, 'the copy detail has the same shape');
select is((select count(*) from public.foods where barcode = '2000000000001' and source = 'user'), 0::bigint,
  'a rejected copy creates nothing');
select is((select public.copy_off_food('0ff00000-0000-4000-8000-000000000002')), 'aaaaaaaa-0000-4000-8000-000000000222'::uuid,
  'an existing own copy is returned as is, never re-checked');
select is((select kcal_100 from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000222'), 500::numeric,
  'and left untouched');
create temp table copies (id uuid);
grant all on copies to authenticated;
insert into copies select public.copy_off_food('0ff00000-0000-4000-8000-000000000003');
select is((select micronutrients from public.foods where id = (select id from copies)),
  '{"fiber_100g":30}'::jsonb, 'a passing OFF food is copied with its fibre taken from off_micronutrients');
select throws_ok($$ select public.copy_off_food('0ff00000-0000-4000-8000-000000000004') $$,
  'FD001', null, 'the same product without fibre data is rejected');
select lives_ok($$ select public.copy_off_food('0ff00000-0000-4000-8000-000000000005') $$,
  'OFF sweets pass with polyols from off_micronutrients');

-- ---------------------------------------------------------------------------
-- Cross-user isolation (user B)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select throws_ok(
  $$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Hack', null, 500, 10, 30, 10, '[]'::jsonb) $$,
  'P0002', 'food not found', 'B cannot update A''s food (not found comes before the check)');
select throws_ok($$ select public.copy_off_food('0ff00000-0000-4000-8000-000000000004') $$,
  'FD001', null, 'B gets the same rejection for a failing OFF food');
select is((select count(*) from public.foods where source = 'user' and barcode = '2000000000003'), 0::bigint,
  'B''s read never sees A''s copy of the passing OFF food');

reset role;

-- ---------------------------------------------------------------------------
-- Untouched data and the data migration path
-- ---------------------------------------------------------------------------
select is((select md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e),
  (select h from snap where k = 'entries'), 'diary snapshots are untouched');
select is((select kcal_100 from public.foods where id = 'bbbbbbbb-0000-4000-8000-000000000001'), 500::numeric,
  'B''s legacy failing food keeps its values');
select isnt(public.copy_off_food_core('11111111-1111-4111-8111-111111111111', '0ff00000-0000-4000-8000-000000000001'), null,
  'the core with the default (no enforcement) still copies a failing OFF row for the data migration');
select is((select kcal_100 from public.foods f where f.source = 'user' and f.barcode = '2000000000001'), 500::numeric,
  'and the copy keeps the failing values untouched');

select * from finish();
rollback;
