-- pgTAP: one-time purge of OFF foods and their dependants (T-070, US-25).
-- Migrations run before fixtures exist, so the migration's statements are re-run here.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(17);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, barcode, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Sin referencias', 50),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '1000000000002', 'En el diario de Ana', 50),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '1000000000003', 'Favorito de Bea', 50),
  ('0ff00000-0000-4000-8000-000000000004', 'off', '1000000000004', 'En una comida', 50);
insert into public.foods (id, source, barcode, name, kcal_100, off_data) values
  ('0ff00000-0000-4000-8000-000000000005', 'off', '1000000000005', 'Con datos', 50,
   '{"code":"1000000000005","nutriments":{"fiber_100g":2.5}}');
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de Ana', 190, '11111111-1111-4111-8111-111111111111');
insert into public.food_portions (food_id, label, amount, position) values
  ('0ff00000-0000-4000-8000-000000000001', '1 racion', 30, 0),
  ('0ff00000-0000-4000-8000-000000000005', '1 racion', 30, 0),
  ('aaaaaaaa-0000-4000-8000-000000000001', '1 trozo', 150, 0);

insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select user_id, '2026-10-21', id, '0ff00000-0000-4000-8000-000000000002', 100, 'En el diario de Ana', 50, 0, 0, 0
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select user_id, '2026-10-21', id, 'aaaaaaaa-0000-4000-8000-000000000001', 150, 'Tortilla de Ana', 285, 0, 0, 0
  from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast';
insert into public.favorite_foods (user_id, food_id) values
  ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-0000-4000-8000-000000000001'),
  ('22222222-2222-4222-8222-222222222222', '0ff00000-0000-4000-8000-000000000003');

-- T1: only OFF items; T2: OFF + personal; T3: already empty; T4: only OFF (user B).
insert into public.meal_templates (id, user_id, name) values
  ('cccccccc-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Solo OFF'),
  ('cccccccc-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Mixta'),
  ('cccccccc-0000-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222', 'Vacia'),
  ('cccccccc-0000-4000-8000-000000000004', '22222222-2222-4222-8222-222222222222', 'Solo OFF de Bea');
insert into public.meal_template_items (template_id, food_id, amount, position) values
  ('cccccccc-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000004', 100, 0),
  ('cccccccc-0000-4000-8000-000000000002', '0ff00000-0000-4000-8000-000000000002', 100, 0),
  ('cccccccc-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-000000000001', 150, 1),
  ('cccccccc-0000-4000-8000-000000000004', '0ff00000-0000-4000-8000-000000000005', 100, 0);

-- Unrelated data that must survive.
insert into public.routines (id, user_id, name) values
  ('dddddddd-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Rutina');
insert into public.workout_sessions (id, user_id, name) values
  ('dddddddd-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Sesion');

create temp table before_counts as
select (select count(*) from public.exercises) as exercises,
       (select count(*) from public.routines) as routines,
       (select count(*) from public.workout_sessions) as sessions,
       (select count(*) from public.meal_slots) as slots;

-- ---------------------------------------------------------------------------
-- Purge (same statements as the migration), run twice to check idempotency
-- ---------------------------------------------------------------------------
create temp table run_log (run int, deleted bigint);

create temporary table purge_off_templates as
select distinct i.template_id
from public.meal_template_items i
join public.foods f on f.id = i.food_id
where f.source = 'off';
delete from public.food_log_entries where food_id in (select id from public.foods where source = 'off');
delete from public.favorite_foods where food_id in (select id from public.foods where source = 'off');
delete from public.meal_template_items where food_id in (select id from public.foods where source = 'off');
delete from public.meal_templates t
where t.id in (select template_id from purge_off_templates)
  and not exists (select 1 from public.meal_template_items i where i.template_id = t.id);
delete from public.foods where source = 'off';
drop table purge_off_templates;

create temp table after_first as
select (select count(*) from public.foods) as foods,
       (select count(*) from public.food_log_entries) as entries,
       (select count(*) from public.favorite_foods) as favs,
       (select count(*) from public.meal_template_items) as items,
       (select count(*) from public.meal_templates) as templates;

create temporary table purge_off_templates as
select distinct i.template_id
from public.meal_template_items i
join public.foods f on f.id = i.food_id
where f.source = 'off';
delete from public.food_log_entries where food_id in (select id from public.foods where source = 'off');
delete from public.favorite_foods where food_id in (select id from public.foods where source = 'off');
delete from public.meal_template_items where food_id in (select id from public.foods where source = 'off');
delete from public.meal_templates t
where t.id in (select template_id from purge_off_templates)
  and not exists (select 1 from public.meal_template_items i where i.template_id = t.id);
delete from public.foods where source = 'off';
drop table purge_off_templates;

select is((select count(*)::int from public.foods where source = 'off'), 0, 'no OFF food remains');
select is((select count(*)::int from public.food_portions where food_id::text like '0ff00000-%'), 0,
  'portions of OFF foods are gone');
select is((select count(*)::int from public.food_log_entries where food_id::text like '0ff00000-%'), 0,
  'diary entries of OFF foods are gone');
select is((select count(*)::int from public.favorite_foods where user_id = '22222222-2222-4222-8222-222222222222'), 0,
  'user B favourite of an OFF food is gone (cross-user)');
select is((select count(*)::int from public.meal_template_items where food_id::text like '0ff00000-%'), 0,
  'template items of OFF foods are gone');
select is((select count(*)::int from public.meal_templates where id = 'cccccccc-0000-4000-8000-000000000001'), 0,
  'a template with only OFF items is deleted');
select is((select count(*)::int from public.meal_templates where id = 'cccccccc-0000-4000-8000-000000000004'), 0,
  'a template with only OFF items of user B is deleted');
select is((select count(*)::int from public.meal_template_items where template_id = 'cccccccc-0000-4000-8000-000000000002'), 1,
  'a mixed template keeps only its personal item');
select is((select count(*)::int from public.meal_templates where id = 'cccccccc-0000-4000-8000-000000000003'), 1,
  'a pre-existing empty template is untouched');
select is((select count(*)::int from public.foods where id = 'aaaaaaaa-0000-4000-8000-000000000001'), 1,
  'personal foods survive');
select is((select count(*)::int from public.food_portions where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 1,
  'portions of personal foods survive');
select is((select count(*)::int from public.food_log_entries where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 1,
  'diary entries on personal foods survive');
select is((select count(*)::int from public.favorite_foods where food_id = 'aaaaaaaa-0000-4000-8000-000000000001'), 1,
  'favourites of personal foods survive');
select is(
  (select count(*) from public.exercises) + (select count(*) from public.routines)
    + (select count(*) from public.workout_sessions) + (select count(*) from public.meal_slots),
  (select exercises + routines + sessions + slots from before_counts),
  'workout tables and meal slots are untouched');
select is((select count(*)::int from public.routines where id = 'dddddddd-0000-4000-8000-000000000001')
        + (select count(*)::int from public.workout_sessions where id = 'dddddddd-0000-4000-8000-000000000002'), 2,
  'the routine and the session fixtures survive');
select is(
  (select row(foods, entries, favs, items, templates)::text from after_first),
  (select row(
     (select count(*) from public.foods),
     (select count(*) from public.food_log_entries),
     (select count(*) from public.favorite_foods),
     (select count(*) from public.meal_template_items),
     (select count(*) from public.meal_templates))::text),
  'running the purge a second time deletes nothing');
select is((select count(*)::int from public.meal_templates), 2,
  'only the mixed and the pre-existing empty templates remain');

select * from finish();
rollback;
