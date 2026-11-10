-- pgTAP: public.cleanup_unused_foods, the monthly cron job and entries that outlive their food (T-272, US-133, ADR-105).
-- The migration ran on an empty DB, so the same function is run here; "time travel" updates created_at.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(22);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100, created_by) values
  ('a0000000-0000-4000-8000-000000000001', 'user', 'Ana vieja sin uso', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000002', 'user', 'Ana justo 30 dias', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000003', 'user', 'Ana 31 dias', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000004', 'user', 'Ana vieja usada ayer', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000005', 'user', 'Ana vieja favorita', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000006', 'user', 'Ana vieja en comida', 100, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000007', 'user', 'Ana vieja que Bea marca', 100, '11111111-1111-4111-8111-111111111111'),
  ('b0000000-0000-4000-8000-000000000001', 'user', 'Bea vieja sin uso', 100, '22222222-2222-4222-8222-222222222222');
insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche OFF', 64);
insert into public.food_portions (food_id, label, amount, position) values
  ('a0000000-0000-4000-8000-000000000001', '1 racion', 150, 0);

-- Time travel: creation dates.
update public.foods set created_at = now() - interval '60 days' where id in (
  'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000004',
  'a0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000006',
  'a0000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000001',
  '0ff00000-0000-4000-8000-000000000001');
update public.foods set created_at = now() - interval '30 days' where id = 'a0000000-0000-4000-8000-000000000002';
update public.foods set created_at = now() - interval '31 days' where id = 'a0000000-0000-4000-8000-000000000003';

-- Entries: Ana used food 1 long ago and food 4 yesterday; Bea used her food long ago.
insert into public.food_log_entries (id, user_id, log_date, meal_slot_id, food_id, amount, portion_label, food_name, kcal, protein_g, carbs_g, fat_g, created_at)
  select v.id::uuid, s.user_id, v.d::date, s.id, v.f::uuid, v.amt, v.pl, v.nm, v.kc, 1.5, 2.5, 0.5, v.at::timestamptz
  from public.meal_slots s
  join (values
    ('e1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', '2026-12-01',
     'a0000000-0000-4000-8000-000000000001', 150, '1 racion', 'Ana vieja sin uso', 150.0, now() - interval '40 days'),
    ('e1000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', '2026-12-01',
     'a0000000-0000-4000-8000-000000000004', 100, null, 'Ana vieja usada ayer', 100.0, now() - interval '1 day'),
    ('e2000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', '2026-12-01',
     'b0000000-0000-4000-8000-000000000001', 100, null, 'Bea vieja sin uso', 100.0, now() - interval '40 days')
  ) v (id, u, d, f, amt, pl, nm, kc, at) on v.u::uuid = s.user_id
  where s.code = 'breakfast';

-- Ana: a favourite and a comida; Bea marks Ana's food 7 as her own favourite and comida item (must not keep it:
-- the policies forbid it for real users, so it is inserted as postgres to prove the owner-keyed rule).
insert into public.favorite_foods (user_id, food_id) values
  ('11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-000000000005'),
  ('22222222-2222-4222-8222-222222222222', 'a0000000-0000-4000-8000-000000000007');
insert into public.meal_templates (id, user_id, name) values
  ('dddddddd-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Comida de Ana'),
  ('dddddddd-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'Comida de Bea');
insert into public.meal_template_items (template_id, food_id, amount, position) values
  ('dddddddd-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000006', 80, 0),
  ('dddddddd-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000007', 80, 0);

create temp table before_totals on commit drop as
  select user_id, log_date, sum(kcal) as kcal, sum(protein_g) as protein_g, sum(carbs_g) as carbs_g,
         sum(fat_g) as fat_g, count(*) as n
  from public.food_log_entries group by user_id, log_date;
create temp table before_entry on commit drop as
  select id, user_id, log_date, food_name, amount, portion_label, kcal, protein_g, carbs_g, fat_g
  from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001';

-- ---------------------------------------------------------------------------
-- Schema, privileges, schedule
-- ---------------------------------------------------------------------------
select col_is_null('public', 'food_log_entries', 'food_id', 'food_id is nullable');
select ok(not has_function_privilege('authenticated', 'public.cleanup_unused_foods()', 'execute')
  and not has_function_privilege('anon', 'public.cleanup_unused_foods()', 'execute'),
  'anon and authenticated cannot execute the cleanup');
select is((select count(*)::int from cron.job
           where jobname = 'cleanup-unused-foods' and schedule = '0 3 1 * *'
             and command = 'select public.cleanup_unused_foods()'),
  1, 'one monthly job named cleanup-unused-foods is scheduled');

-- ---------------------------------------------------------------------------
-- Run
-- ---------------------------------------------------------------------------
select is(public.cleanup_unused_foods(), 4, 'the cleanup reports the four removed foods');

select is((select count(*)::int from public.foods
           where id in ('a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000003')),
  0, 'old unused foods (including 31 days) are removed');
select is((select count(*)::int from public.foods where id = 'a0000000-0000-4000-8000-000000000002'),
  1, 'a food exactly 30 days old stays');
select is((select count(*)::int from public.foods where id = 'a0000000-0000-4000-8000-000000000004'),
  1, 'an old food with an entry yesterday stays');
select is((select count(*)::int from public.foods where id = 'a0000000-0000-4000-8000-000000000005'),
  1, 'an old favourite stays');
select is((select count(*)::int from public.foods where id = 'a0000000-0000-4000-8000-000000000006'),
  1, 'an old food in a saved comida stays');
select is((select count(*)::int from public.foods where id = '0ff00000-0000-4000-8000-000000000001'),
  1, 'OFF cache rows are never touched');
select is((select count(*)::int from public.foods where id = 'a0000000-0000-4000-8000-000000000007'),
  0, 'another account''s favourite and comida never keep this owner''s food');
select is((select count(*)::int from public.foods where id = 'b0000000-0000-4000-8000-000000000001'),
  0, 'the other account''s own unused food is removed by the same rule');
select is((select count(*)::int from public.food_portions where food_id = 'a0000000-0000-4000-8000-000000000001'),
  0, 'portions of a removed food are removed');

-- ---------------------------------------------------------------------------
-- The diary does not change
-- ---------------------------------------------------------------------------
select results_eq(
  $$select id, user_id, log_date, food_name, amount, portion_label, kcal, protein_g, carbs_g, fat_g
    from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'$$,
  $$select id, user_id, log_date, food_name, amount, portion_label, kcal, protein_g, carbs_g, fat_g from before_entry$$,
  'the entry of a removed food keeps its name, amount, portion and snapshot');
select is((select food_id from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'),
  null, 'the entry of a removed food has a null food_id');
select is((select count(*)::int from (
    (select * from before_totals except
     select user_id, log_date, sum(kcal), sum(protein_g), sum(carbs_g), sum(fat_g), count(*)
       from public.food_log_entries group by user_id, log_date)
    union all
    (select user_id, log_date, sum(kcal), sum(protein_g), sum(carbs_g), sum(fat_g), count(*)
       from public.food_log_entries group by user_id, log_date
     except select * from before_totals)) d),
  0, 'per-day totals and entry counts are identical before and after');
select is(public.cleanup_unused_foods(), 0, 'a second run removes nothing');

-- ---------------------------------------------------------------------------
-- As Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.search_user_food_ids('Ana vieja sin uso')), 0,
  'a removed food is gone from search');
select lives_ok(
  $$insert into public.foods (name, kcal_100, source) values ('Ana vieja sin uso', 100, 'user')$$,
  'a food with the same name can be created again');
select is((select count(*)::int from public.most_eaten_foods()), 1,
  'most_eaten_foods ignores entries without a food');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    select '2026-12-02', id, null, 100, 'Sin alimento', 10, 1, 1, 1
    from public.meal_slots where code = 'lunch'$$,
  '42501', null, 'a new entry without a food is rejected by RLS');
select lives_ok(
  $$delete from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001'$$,
  'the owner can delete an entry whose food was removed');

select * from finish();
rollback;
