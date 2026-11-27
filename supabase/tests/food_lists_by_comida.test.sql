-- pgTAP: habitual_foods and ranked_favourite_foods (T-330, US-149): order by use in the comida.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(23);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

-- Food ids: 01 Leche, 02 Pan, 03 Huevo, 04 Mango, 05 Árbol, 06 arroz, 07 Zumo, 08 Queso, 09 Naranja, 10 Ñame.
insert into public.foods (id, source, name, kcal_100)
  select ('f0000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid, 'off', name, 100
  from (values (1, 'Leche'), (2, 'Pan'), (3, 'Huevo'), (4, 'Mango'), (5, 'Árbol'), (6, 'arroz'),
               (7, 'Zumo'), (8, 'Queso'), (9, 'Naranja'), (10, 'Ñame')) v (n, name);

-- Fixed "today" for every call: 2027-01-25. days_ago 89 counts, 91 does not.
create temp table seed_entries (uid uuid, fid uuid, slot text, days_ago int, n int, hh int) on commit drop;
insert into seed_entries values
  -- Ana, Desayuno
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000001', 'breakfast', 1, 3, 9),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000002', 'breakfast', 1, 2, 10),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000003', 'breakfast', 1, 2, 11),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000004', 'breakfast', 3, 1, 10),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000005', 'breakfast', 5, 1, 10),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000006', 'breakfast', 5, 1, 10),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000007', 'breakfast', 89, 1, 10),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000008', 'breakfast', 90, 2, 10),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000008', 'breakfast', 91, 5, 10),
  ('11111111-1111-4111-8111-111111111111', null, 'breakfast', 1, 3, 12),
  -- Ana, Cena
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000002', 'dinner', 2, 4, 20),
  ('11111111-1111-4111-8111-111111111111', 'f0000000-0000-4000-8000-000000000001', 'dinner', 2, 1, 20),
  -- Bea, Desayuno
  ('22222222-2222-4222-8222-222222222222', 'f0000000-0000-4000-8000-000000000002', 'breakfast', 1, 9, 9);
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, created_at)
  select s.uid, date '2027-01-25' - s.days_ago, m.id, s.fid, 100, 'x', 1, 0, 0, 0,
    (date '2027-01-25' - s.days_ago)::timestamp + make_interval(hours => s.hh)
  from seed_entries s
  cross join generate_series(1, 9) g
  join public.meal_slots m on m.user_id = s.uid and m.code = s.slot::public.meal_type
  where g <= s.n;

insert into public.favorite_foods (user_id, food_id)
  select '11111111-1111-4111-8111-111111111111', ('f0000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid
  from generate_series(1, 10) n where n in (1, 2, 3, 4, 7, 8, 9, 10);
insert into public.favorite_foods (user_id, food_id) values
  ('22222222-2222-4222-8222-222222222222', 'f0000000-0000-4000-8000-000000000002');

create temp table slot_ids as
  select user_id, code::text as code, id from public.meal_slots;
grant select on slot_ids to authenticated;

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- ---------------------------------------------------------------------------
-- Habituales (as Ana)
-- ---------------------------------------------------------------------------
-- Leche 3; Huevo and Pan 2 each, Huevo later created_at; Mango 1 on day -3; Arbol and arroz 1 on day -5 (name
-- ties: "arbol" before "arroz"); Zumo 1 on day -89 (counts). Queso (days -90 and -91) and the null food are out.
select results_eq(
  $$select food_id, times_logged from public.habitual_foods(
      (select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'),
      20, date '2027-01-25')$$,
  $$values ('f0000000-0000-4000-8000-000000000001'::uuid, 3::bigint),
           ('f0000000-0000-4000-8000-000000000003'::uuid, 2::bigint),
           ('f0000000-0000-4000-8000-000000000002'::uuid, 2::bigint),
           ('f0000000-0000-4000-8000-000000000004'::uuid, 1::bigint),
           ('f0000000-0000-4000-8000-000000000005'::uuid, 1::bigint),
           ('f0000000-0000-4000-8000-000000000006'::uuid, 1::bigint),
           ('f0000000-0000-4000-8000-000000000007'::uuid, 1::bigint)$$,
  'Habituales of Desayuno: uses, then log_date, then created_at, then unaccented name; day -89 counts');
select results_eq(
  $$select food_id, times_logged from public.habitual_foods(
      (select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'dinner'),
      20, date '2027-01-25')$$,
  $$values ('f0000000-0000-4000-8000-000000000002'::uuid, 4::bigint),
           ('f0000000-0000-4000-8000-000000000001'::uuid, 1::bigint)$$,
  'Habituales of Cena has its own order and counts for the same foods');
select results_eq(
  $$select food_id, times_logged from public.habitual_foods(null, 20, date '2027-01-25')$$,
  $$values ('f0000000-0000-4000-8000-000000000002'::uuid, 6::bigint),
           ('f0000000-0000-4000-8000-000000000001'::uuid, 4::bigint),
           ('f0000000-0000-4000-8000-000000000003'::uuid, 2::bigint),
           ('f0000000-0000-4000-8000-000000000004'::uuid, 1::bigint),
           ('f0000000-0000-4000-8000-000000000005'::uuid, 1::bigint),
           ('f0000000-0000-4000-8000-000000000006'::uuid, 1::bigint),
           ('f0000000-0000-4000-8000-000000000007'::uuid, 1::bigint)$$,
  'Habituales without a comida counts every comida');
select is(
  (select count(*)::int from public.habitual_foods(null, 100, date '2027-01-25')
    where food_id = 'f0000000-0000-4000-8000-000000000008'),
  0, 'a food whose only uses are 90 and 91 days old is not a habitual');
select is(
  (select count(*)::int from public.habitual_foods(null, 100, date '2027-01-25') where food_id is null),
  0, 'entries without a food are ignored');
select is(
  (select count(*)::int from public.habitual_foods('99999999-9999-4999-8999-999999999999', 20, date '2027-01-25')),
  0, 'an unknown comida returns no habituales');
select is(
  (select count(*)::int from public.habitual_foods(
    (select id from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'),
    20, date '2027-01-25')),
  0, 'Bea''s comida id returns no habituales for Ana');
select is(
  (select count(*)::int from public.habitual_foods(null, 0, date '2027-01-25')), 1,
  'a limit below 1 is clamped to 1');
select is(
  (select count(*)::int from public.habitual_foods(null, 1000, date '2027-01-25')), 7,
  'a large limit returns every habitual (clamped to 100)');
select results_eq(
  $$select food_id from public.habitual_foods(null, 2, date '2027-01-25')$$,
  $$values ('f0000000-0000-4000-8000-000000000002'::uuid), ('f0000000-0000-4000-8000-000000000001'::uuid)$$,
  'the limit keeps the top of the ranking');

-- ---------------------------------------------------------------------------
-- Favoritos (as Ana)
-- ---------------------------------------------------------------------------
-- Desayuno: per-comida uses (Leche 3, Pan 2, Huevo 2 with fewer overall uses than Pan, Mango 1 and Zumo 1 tie on
-- uses and Mango is more recent); never used in the window (Queso day -91, Naranja, Ñame) last by name.
select is(
  array(select food_id from public.ranked_favourite_foods(
    (select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'breakfast'),
    date '2027-01-25')),
  array['f0000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000002',
        'f0000000-0000-4000-8000-000000000003', 'f0000000-0000-4000-8000-000000000004',
        'f0000000-0000-4000-8000-000000000007', 'f0000000-0000-4000-8000-000000000010',
        'f0000000-0000-4000-8000-000000000009', 'f0000000-0000-4000-8000-000000000008']::uuid[],
  'Favoritos of Desayuno: comida uses, overall uses, recency, then unaccented name for never-used ones');
select is(
  array(select food_id from public.ranked_favourite_foods(
    (select id from slot_ids where user_id = '11111111-1111-4111-8111-111111111111' and code = 'dinner'),
    date '2027-01-25')),
  array['f0000000-0000-4000-8000-000000000002', 'f0000000-0000-4000-8000-000000000001',
        'f0000000-0000-4000-8000-000000000003', 'f0000000-0000-4000-8000-000000000004',
        'f0000000-0000-4000-8000-000000000007', 'f0000000-0000-4000-8000-000000000010',
        'f0000000-0000-4000-8000-000000000009', 'f0000000-0000-4000-8000-000000000008']::uuid[],
  'Favoritos of Cena has its own order; foods unused there fall back to overall uses');
select is(
  array(select food_id from public.ranked_favourite_foods(null, date '2027-01-25')),
  array['f0000000-0000-4000-8000-000000000002', 'f0000000-0000-4000-8000-000000000001',
        'f0000000-0000-4000-8000-000000000003', 'f0000000-0000-4000-8000-000000000004',
        'f0000000-0000-4000-8000-000000000007', 'f0000000-0000-4000-8000-000000000010',
        'f0000000-0000-4000-8000-000000000009', 'f0000000-0000-4000-8000-000000000008']::uuid[],
  'Favoritos without a comida ranks by overall uses');
select is(
  array(select food_id from public.ranked_favourite_foods('99999999-9999-4999-8999-999999999999', date '2027-01-25')),
  array(select food_id from public.ranked_favourite_foods(null, date '2027-01-25')),
  'an unknown comida falls back to the overall order and keeps every favourite');
select is(
  array(select food_id from public.ranked_favourite_foods(
    (select id from slot_ids where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast'),
    date '2027-01-25')),
  array(select food_id from public.ranked_favourite_foods(null, date '2027-01-25')),
  'Bea''s comida id gives Ana the overall order');
select results_eq(
  $$select count(*)::int, count(distinct food_id)::int from public.ranked_favourite_foods(null, date '2027-01-25')$$,
  $$values (8, 8)$$,
  'every favourite appears exactly once');

-- ---------------------------------------------------------------------------
-- Isolation (as Bea)
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select results_eq(
  $$select food_id, times_logged from public.habitual_foods(null, 20, date '2027-01-25')$$,
  $$values ('f0000000-0000-4000-8000-000000000002'::uuid, 9::bigint)$$,
  'Bea''s habituales ignore Ana''s entries');
select is(
  array(select food_id from public.ranked_favourite_foods(null, date '2027-01-25')),
  array['f0000000-0000-4000-8000-000000000002']::uuid[],
  'Bea''s favourites ignore Ana''s favourites');

-- ---------------------------------------------------------------------------
-- Privileges and legacy function
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select cmp_ok((select count(*)::int from public.most_eaten_foods(20)), '>', 0,
  'the legacy most_eaten_foods(int) still works for the installed app');
reset role;
select ok(not has_function_privilege('anon', 'public.habitual_foods(uuid,int,date)', 'execute'),
  'anon cannot execute habitual_foods');
select ok(not has_function_privilege('anon', 'public.ranked_favourite_foods(uuid,date)', 'execute'),
  'anon cannot execute ranked_favourite_foods');
select ok(has_function_privilege('authenticated', 'public.habitual_foods(uuid,int,date)', 'execute'),
  'authenticated can execute habitual_foods');
select ok(has_function_privilege('authenticated', 'public.ranked_favourite_foods(uuid,date)', 'execute'),
  'authenticated can execute ranked_favourite_foods');

select * from finish();
rollback;
