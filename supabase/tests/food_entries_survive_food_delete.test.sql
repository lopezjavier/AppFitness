-- pgTAP: a diary entry outlives its deleted food, and the cleanup job and function are gone (T-273, ADR-105).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(12);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100, created_by) values
  ('a0000000-0000-4000-8000-000000000001', 'user', 'Ana alimento borrado', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000004', 'user', 'Ana alimento usado', 100, '11111111-1111-4111-8111-111111111111');
insert into public.food_portions (food_id, label, amount, position) values
  ('a0000000-0000-4000-8000-000000000001', '1 racion', 150, 0);

insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g)
  select v.id::uuid, s.user_id, '2026-12-01', s.id, v.f::uuid, v.amt, v.pl, v.nm, v.kc, 1.5, 2.5, 0.5
  from public.meal_slots s
  join (values
    ('e1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111',
     'a0000000-0000-4000-8000-000000000001', 150, '1 racion', 'Ana alimento borrado', 150.0),
    ('e1000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111',
     'a0000000-0000-4000-8000-000000000004', 100, null, 'Ana alimento usado', 100.0)
  ) v (id, u, f, amt, pl, nm, kc) on v.u::uuid = s.user_id
  where s.code = 'breakfast';

create temp table before_totals on commit drop as
  select user_id, log_date, sum(kcal) as kcal, sum(protein_g) as protein_g, sum(carbs_g) as carbs_g,
         sum(fat_g) as fat_g, count(*) as n
  from public.food_log_entries group by user_id, log_date;
create temp table before_entry on commit drop as
  select id, user_id, log_date, food_name, amount, portion_label, kcal, protein_g, carbs_g, fat_g
  from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001';

-- Schema: nullable food_id, no cleanup function or job
select col_is_null('public', 'food_log_entries', 'food_id', 'food_id is nullable');
select hasnt_function('public', 'cleanup_unused_foods', 'the cleanup function is gone');
select is((select count(*)::int from cron.job where jobname = 'cleanup-unused-foods'), 0,
  'the cleanup job is not scheduled');

-- Deleting a food (as owner of the database) keeps the entry
delete from public.foods where id = 'a0000000-0000-4000-8000-000000000001';

select results_eq(
  $$select id, user_id, log_date, food_name, amount, portion_label, kcal, protein_g, carbs_g, fat_g
    from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'$$,
  $$select id, user_id, log_date, food_name, amount, portion_label, kcal, protein_g, carbs_g, fat_g from before_entry$$,
  'the entry of a deleted food keeps its name, amount, portion and snapshot');
select is((select food_id from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'),
  null, 'the entry of a deleted food has a null food_id');
select is((select count(*)::int from (
    (select * from before_totals except
     select user_id, log_date, sum(kcal), sum(protein_g), sum(carbs_g), sum(fat_g), count(*)
       from public.food_log_entries group by user_id, log_date)
    union all
    (select user_id, log_date, sum(kcal), sum(protein_g), sum(carbs_g), sum(fat_g), count(*)
       from public.food_log_entries group by user_id, log_date
     except select * from before_totals)) d),
  0, 'per-day totals and entry counts are identical before and after');

-- As Bea: cannot see or delete Ana's entry
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'),
  0, 'another account cannot see the entry whose food was deleted');
delete from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001';

-- As Ana
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'),
  1, 'another account''s delete did not remove the entry');
select is((select count(*)::int from public.search_user_food_ids('Ana alimento borrado')), 0,
  'a deleted food is gone from search');
select is((select count(*)::int from public.most_eaten_foods()), 1,
  'most_eaten_foods ignores entries without a food');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '2026-12-02', id, null, 100, 'Sin alimento', 10, 1, 1, 1
    from public.meal_slots where code = 'lunch'$$,
  '42501', null, 'a new entry without a food is rejected by RLS');
select lives_ok(
  $$delete from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'$$,
  'the owner can delete an entry whose food was deleted');

select * from finish();
rollback;
