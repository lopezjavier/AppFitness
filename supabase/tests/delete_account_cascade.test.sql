-- pgTAP: deleting an auth user (what the delete-account function does) removes all their data (T-037, ADR-29).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(12);

-- The on_auth_user_created trigger creates each profile.
insert into auth.users (id, email) values
  ('d1000000-0000-4000-8000-000000000001', 'del-a@example.com'),
  ('d2000000-0000-4000-8000-000000000002', 'del-b@example.com');

insert into public.body_measurements (user_id, weight_kg, source) values
  ('d1000000-0000-4000-8000-000000000001', 80, 'manual'),
  ('d2000000-0000-4000-8000-000000000002', 60, 'manual');

insert into public.nutrition_goals (user_id, target_kcal) values
  ('d1000000-0000-4000-8000-000000000001', 2000),
  ('d2000000-0000-4000-8000-000000000002', 1800);

insert into public.health_sync_state (user_id, last_synced_at) values
  ('d1000000-0000-4000-8000-000000000001', now()),
  ('d2000000-0000-4000-8000-000000000002', now());

insert into public.diary_default_applications (user_id, log_date) values
  ('d1000000-0000-4000-8000-000000000001', date '2026-01-01'),
  ('d2000000-0000-4000-8000-000000000002', date '2026-01-01');

-- Default meals: a comida, a meal and the default linking them (the meal slots exist only after onboarding).
update public.profiles set onboarding_completed = true where id in
  ('d1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002');
insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-0000000000d1', 'off', 'Alimento d', 100);
insert into public.meal_templates (id, user_id, name) values
  ('d1000000-0000-4000-8000-0000000000a1', 'd1000000-0000-4000-8000-000000000001', 'Comida d1'),
  ('d2000000-0000-4000-8000-0000000000a2', 'd2000000-0000-4000-8000-000000000002', 'Comida d2');
insert into public.meal_template_items (template_id, food_id, amount, position) values
  ('d1000000-0000-4000-8000-0000000000a1', '0ff00000-0000-4000-8000-0000000000d1', 100, 0),
  ('d2000000-0000-4000-8000-0000000000a2', '0ff00000-0000-4000-8000-0000000000d1', 100, 0);
insert into public.meal_defaults (user_id, meal_slot_id, template_id, weekday)
  select s.user_id, s.id, t.id, 1
  from public.meal_slots s join public.meal_templates t on t.user_id = s.user_id
  where s.code = 'breakfast' and s.user_id in
    ('d1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000002');

delete from auth.users where id = 'd1000000-0000-4000-8000-000000000001';

select is(
  (select count(*)::int from public.meal_defaults where user_id = 'd1000000-0000-4000-8000-000000000001'),
  0, 'the deleted user''s meal_defaults are gone');
select is(
  (select count(*)::int from public.diary_default_applications where user_id = 'd1000000-0000-4000-8000-000000000001'),
  0, 'the deleted user''s diary_default_applications are gone');
select is(
  (select count(*)::int from public.meal_defaults where user_id = 'd2000000-0000-4000-8000-000000000002'),
  1, 'another user''s meal_defaults are untouched');
select is(
  (select count(*)::int from public.diary_default_applications where user_id = 'd2000000-0000-4000-8000-000000000002'),
  1, 'another user''s diary_default_applications are untouched');

select is(
  (select count(*)::int from public.profiles where id = 'd1000000-0000-4000-8000-000000000001'),
  0, 'the deleted user''s profile is gone');
select is(
  (select count(*)::int from public.body_measurements where user_id = 'd1000000-0000-4000-8000-000000000001'),
  0, 'the deleted user''s body_measurements are gone');
select is(
  (select count(*)::int from public.nutrition_goals where user_id = 'd1000000-0000-4000-8000-000000000001'),
  0, 'the deleted user''s nutrition_goals are gone');
select is(
  (select count(*)::int from public.profiles where id = 'd2000000-0000-4000-8000-000000000002'),
  1, 'another user''s profile is untouched');
select is(
  (select count(*)::int from public.body_measurements where user_id = 'd2000000-0000-4000-8000-000000000002'),
  1, 'another user''s body_measurements are untouched');
select is(
  (select count(*)::int from public.nutrition_goals where user_id = 'd2000000-0000-4000-8000-000000000002'),
  1, 'another user''s nutrition_goals are untouched');
select is(
  (select count(*)::int from public.health_sync_state where user_id = 'd1000000-0000-4000-8000-000000000001'),
  0, 'the deleted user''s health_sync_state is gone');
select is(
  (select count(*)::int from public.health_sync_state where user_id = 'd2000000-0000-4000-8000-000000000002'),
  1, 'another user''s health_sync_state is untouched');

select * from finish();
rollback;
