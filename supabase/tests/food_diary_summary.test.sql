-- pgTAP: food_diary_summary (T-062, US-24): per-day sums, inclusive bounds, absent empty days,
-- cross-user isolation, argument guards and execute privileges.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(11);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Leche entera', 64);

create temp table slot_ids on commit drop as
  select user_id, code, id from public.meal_slots;

-- Ana: 10-18 has two entries (breakfast + lunch), 10-01 and 10-31 are the range bounds, 09-30 and 11-01 are outside.
-- Bea: one entry on 10-18.
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
select s.user_id, d.log_date, s.id, '0ff00000-0000-4000-8000-000000000001', 100, 'Leche entera', d.kcal, d.p, d.c, d.f
from slot_ids s
join (values
  ('11111111-1111-4111-8111-111111111111'::uuid, 'breakfast', date '2026-10-18', 400.1, 30.1, 40.1, 10.1),
  ('11111111-1111-4111-8111-111111111111'::uuid, 'lunch',     date '2026-10-18', 800.2, 50.2, 60.2, 20.2),
  ('11111111-1111-4111-8111-111111111111'::uuid, 'lunch',     date '2026-10-01', 500, 20, 50, 10),
  ('11111111-1111-4111-8111-111111111111'::uuid, 'lunch',     date '2026-10-31', 600, 25, 60, 15),
  ('11111111-1111-4111-8111-111111111111'::uuid, 'lunch',     date '2026-09-30', 700, 1, 1, 1),
  ('11111111-1111-4111-8111-111111111111'::uuid, 'lunch',     date '2026-11-01', 700, 1, 1, 1),
  ('22222222-2222-4222-8222-222222222222'::uuid, 'lunch',     date '2026-10-18', 999, 9, 9, 9)
) as d(user_id, code, log_date, kcal, p, c, f)
  on d.user_id = s.user_id and d.code = s.code::text;

-- ---------------------------------------------------------------------------
-- Ana
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  $$select log_date, kcal, protein_g, carbs_g, fat_g from public.food_diary_summary('2026-10-01', '2026-10-31')$$,
  $$values (date '2026-10-01', 500::numeric, 20::numeric, 50::numeric, 10::numeric),
           (date '2026-10-18', 1200.3::numeric, 80.3::numeric, 100.3::numeric, 30.3::numeric),
           (date '2026-10-31', 600::numeric, 25::numeric, 60::numeric, 15::numeric)$$,
  'rows are per day, ordered, with summed snapshot columns; bounds are inclusive and outer days are ignored');

select is((select count(*)::int from public.food_diary_summary('2026-10-02', '2026-10-30')), 1,
  'a narrower range excludes the bound days');
select is((select count(*)::int from public.food_diary_summary('2026-10-19', '2026-10-30')), 0,
  'days without entries are absent');
select is((select count(*)::int from public.food_diary_summary('2026-10-18', '2026-10-18')), 1,
  'a single-day range works');

select throws_ok($$select * from public.food_diary_summary('2026-10-31', '2026-10-01')$$,
  '22023', null, 'p_from after p_to raises');
select throws_ok($$select * from public.food_diary_summary(null, '2026-10-01')$$,
  '22023', null, 'null p_from raises');
select throws_ok($$select * from public.food_diary_summary('2026-10-01', null)$$,
  '22023', null, 'null p_to raises');
select throws_ok($$select * from public.food_diary_summary('2025-01-01', '2026-10-01')$$,
  '22023', null, 'a range over 400 days raises');

-- ---------------------------------------------------------------------------
-- Bea only sees her own totals
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select results_eq(
  $$select log_date, kcal from public.food_diary_summary('2026-10-01', '2026-10-31')$$,
  $$values (date '2026-10-18', 999::numeric)$$,
  'another user never sees Ana''s totals');

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
reset role;
select ok(not has_function_privilege('anon', 'public.food_diary_summary(date, date)', 'execute'),
  'anon cannot execute food_diary_summary');
select ok(has_function_privilege('authenticated', 'public.food_diary_summary(date, date)', 'execute'),
  'authenticated can execute food_diary_summary');

select * from finish();
rollback;
