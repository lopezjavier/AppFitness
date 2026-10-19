-- pgTAP: deleting an auth user (what the delete-account function does) removes all their data (T-037, ADR-29).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(6);

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

delete from auth.users where id = 'd1000000-0000-4000-8000-000000000001';

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

select * from finish();
rollback;
