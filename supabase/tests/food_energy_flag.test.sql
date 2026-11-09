-- pgTAP: "saved anyway" flag and alcohol in create_personal_food / update_personal_food (T-251, US-116, ADR-102).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(21);

insert into auth.users (id, email) values ('11111111-1111-4111-8111-111111111111', 'ana@example.com');
update public.profiles set onboarding_completed = true;

-- Test helper: puts food F back to its failing state (kcal 500, computed 250) with the flag set.
create function public.t_reset_f() returns void language plpgsql security invoker as $$
begin
  update public.foods
  set name = 'Mala', brand = null, kcal_100 = 500, protein_100 = 10, carbs_100 = 30, fat_100 = 10,
      alcohol_g_100 = null, micronutrients = null, base_unit = 'g'
  where id = 'f0000000-0000-4000-8000-000000000001';
  update public.foods set energy_check_saved_anyway = true where id = 'f0000000-0000-4000-8000-000000000001';
end;
$$;
grant execute on function public.t_reset_f() to authenticated;

create function public.t_flag() returns boolean language sql security invoker as $$
  select energy_check_saved_anyway from public.foods where id = 'f0000000-0000-4000-8000-000000000001';
$$;
grant execute on function public.t_flag() to authenticated;

insert into public.foods (id, source, name, base_unit, kcal_100, protein_100, carbs_100, fat_100, created_by) values
  ('f0000000-0000-4000-8000-000000000001', 'user', 'Mala', 'g', 500, 10, 30, 10, '11111111-1111-4111-8111-111111111111'),
  ('f0000000-0000-4000-8000-000000000002', 'user', 'Con diario', 'g', 500, 10, 30, 10, '11111111-1111-4111-8111-111111111111');
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select 'e1000000-0000-4000-8000-000000000001', user_id, '2026-10-21', id, 'f0000000-0000-4000-8000-000000000002',
         100, 'Con diario', 500, 10, 30, 10
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';

create temp table snap (h text);
insert into snap select md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e;
grant all on snap to authenticated;
create temp table made (k text, id uuid);
grant all on made to authenticated;

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Create
insert into made select 'failing_flagged', public.create_personal_food('Falla', null, 'g', 500, 10, 30, 10, null, '[]'::jsonb,
  null, null, true);
insert into made select 'passing_flagged', public.create_personal_food('Pasa', null, 'g', 250, 10, 30, 10, null, '[]'::jsonb,
  null, null, true);
insert into made select 'failing_plain', public.create_personal_food('Falla sin marca', null, 'g', 500, 10, 30, 10, null,
  '[]'::jsonb);
select is((select energy_check_saved_anyway from public.foods where id = (select id from made where k = 'failing_flagged')),
  true, 'create with saved_anyway on a failing food stores the flag');
select is((select energy_check_saved_anyway from public.foods where id = (select id from made where k = 'passing_flagged')),
  false, 'create with saved_anyway on a passing food stores no flag');
select is((select energy_check_saved_anyway from public.foods where id = (select id from made where k = 'failing_plain')),
  false, 'create without saved_anyway stores no flag');

-- Edits that touch no input keep the flag
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Otro nombre', 'Marca', 500, 10, 30, 10, '[]'::jsonb);
select is(public.t_flag(), true, 'renaming and setting a brand keeps the flag');
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10,
  '[{"label":"1 ración","amount":50}]'::jsonb);
select is(public.t_flag(), true, 'changing portions keeps the flag');
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10,
  '[]'::jsonb, null, 'ml');
select is(public.t_flag(), true, 'changing the base unit keeps the flag');
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10,
  '[]'::jsonb, '{"sugars_100g": 5}'::jsonb, 'ml');
select is(public.t_flag(), true, 'changing a micronutrient other than fibre and polyols keeps the flag');

-- Each input of the check clears it
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 501, 10, 30, 10, '[]'::jsonb);
select is(public.t_flag(), false, 'changing kcal clears the flag');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 11, 30, 10, '[]'::jsonb);
select is(public.t_flag(), false, 'changing protein clears the flag');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 31, 10, '[]'::jsonb);
select is(public.t_flag(), false, 'changing carbs clears the flag');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 11, '[]'::jsonb);
select is(public.t_flag(), false, 'changing fat clears the flag');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10, '[]'::jsonb,
  '{"fiber_100g": 1}'::jsonb);
select is(public.t_flag(), false, 'changing fibre clears the flag');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10, '[]'::jsonb,
  '{"polyols_100g": 1}'::jsonb);
select is(public.t_flag(), false, 'changing polyols clears the flag');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 500, 10, 30, 10, '[]'::jsonb,
  null, null, 1);
select is(public.t_flag(), false, 'changing alcohol clears the flag');

-- Explicit save anyway after an input change
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 501, 10, 30, 10, '[]'::jsonb,
  null, null, null, true);
select is(public.t_flag(), true, 'an input change saved anyway on a still-failing food ends true');
select public.t_reset_f();
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 250, 10, 30, 10, '[]'::jsonb,
  null, null, null, true);
select is(public.t_flag(), false, 'an input change saved anyway on a now-passing food ends false');

-- Stored values and alcohol
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 123.45, 1.5, 2.25, 3.75, '[]'::jsonb,
  null, null, 4.5);
select results_eq($$ select kcal_100, protein_100, carbs_100, fat_100, alcohol_g_100 from public.foods
  where id = 'f0000000-0000-4000-8000-000000000001' $$,
  $$ values (123.45::numeric, 1.50::numeric, 2.25::numeric, 3.75::numeric, 4.5::numeric) $$,
  'stored numbers are exactly as sent');
select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 123.45, 1.5, 2.25, 3.75, '[]'::jsonb);
select is((select alcohol_g_100 from public.foods where id = 'f0000000-0000-4000-8000-000000000001'), null,
  'a null alcohol on update clears the column');
select throws_ok($$ select public.update_personal_food('f0000000-0000-4000-8000-000000000001', 'Mala', null, 100, 1, 1, 1,
  '[]'::jsonb, null, null, -1) $$, '22023', 'invalid nutrition values', 'negative alcohol on update raises 22023');
select throws_ok($$ select public.create_personal_food('X', null, 'g', 100, 1, 1, 1, null, '[]'::jsonb, null, 100.1) $$,
  '22023', 'invalid nutrition values', 'alcohol above 100 on create raises 22023');

-- Diary snapshots are not touched by an edit
select public.update_personal_food('f0000000-0000-4000-8000-000000000002', 'Con diario', null, 100, 1, 1, 1, '[]'::jsonb);
select is((select md5(string_agg(to_jsonb(e)::text, '|' order by e.id)) from public.food_log_entries e),
  (select h from snap), 'existing diary entries are unchanged after editing the food');

select * from finish();
rollback;
