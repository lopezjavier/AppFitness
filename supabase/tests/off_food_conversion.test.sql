-- pgTAP: public.convert_used_off_foods (T-254, US-119). The migration ran on an empty DB, so the same function is run here.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(14);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'cleo@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, barcode, name, kcal_100, protein_100, carbs_100, fat_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Leche OFF', 64, 3, 5, 3),
  ('0ff00000-0000-4000-8000-000000000002', 'off', '1000000000002', 'Pan OFF', 250, 8, 50, 2),
  ('0ff00000-0000-4000-8000-000000000003', 'off', '1000000000003', 'Sin uso OFF', 100, 1, 1, 1);
insert into public.foods (id, source, barcode, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', '1000000000001', 'Leche de Ana', 70, '11111111-1111-4111-8111-111111111111'),
  ('cccccccc-0000-4000-8000-000000000001', 'user', null, 'Tortilla de Cleo', 190, '33333333-3333-4333-8333-333333333333');

-- Entries: snapshots are what the diary adds up, whatever the food row says.
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select s.user_id, v.d::date, s.id, v.f::uuid, v.amt, v.nm, v.kc, 1.5, 2.5, 0.5
  from public.meal_slots s
  join (values
    ('11111111-1111-4111-8111-111111111111', '2027-01-10', '0ff00000-0000-4000-8000-000000000001', 200, 'Leche OFF', 128.0),
    ('11111111-1111-4111-8111-111111111111', '2027-01-10', '0ff00000-0000-4000-8000-000000000001', 100, 'Leche OFF', 64.0),
    ('11111111-1111-4111-8111-111111111111', '2027-01-11', '0ff00000-0000-4000-8000-000000000001', 100, 'Leche OFF', 64.0),
    ('11111111-1111-4111-8111-111111111111', '2027-01-11', '0ff00000-0000-4000-8000-000000000002', 50, 'Pan OFF', 125.0),
    ('22222222-2222-4222-8222-222222222222', '2027-01-10', '0ff00000-0000-4000-8000-000000000001', 100, 'Leche OFF', 64.0),
    ('33333333-3333-4333-8333-333333333333', '2027-01-10', 'cccccccc-0000-4000-8000-000000000001', 100, 'Tortilla de Cleo', 190.0)
  ) v (u, d, f, amt, nm, kc) on v.u::uuid = s.user_id
  where s.code = 'breakfast';

insert into public.favorite_foods (user_id, food_id) values
  ('11111111-1111-4111-8111-111111111111', '0ff00000-0000-4000-8000-000000000001'),
  ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-0000-4000-8000-000000000001'),
  ('11111111-1111-4111-8111-111111111111', '0ff00000-0000-4000-8000-000000000002'),
  ('22222222-2222-4222-8222-222222222222', '0ff00000-0000-4000-8000-000000000001'),
  ('33333333-3333-4333-8333-333333333333', 'cccccccc-0000-4000-8000-000000000001');
insert into public.meal_templates (id, user_id, name) values
  ('dddddddd-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Desayuno de Ana');
insert into public.meal_template_items (template_id, food_id, amount, position) values
  ('dddddddd-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000002', 80, 0);

create temp table snap as
  select
    (select md5(string_agg(concat_ws('|', user_id, log_date, sum_kcal, sum_p, sum_c, sum_f, n), ';' order by user_id, log_date))
     from (select user_id, log_date, sum(kcal) sum_kcal, sum(protein_g) sum_p, sum(carbs_g) sum_c, sum(fat_g) sum_f, count(*) n
           from public.food_log_entries group by user_id, log_date) t) as totals,
    (select md5(string_agg(to_jsonb(l)::text, '|' order by l.id)) from public.food_log_entries l
      where l.user_id = '33333333-3333-4333-8333-333333333333') as cleo_entries,
    (select md5(string_agg(to_jsonb(f)::text, '|' order by f.food_id)) from public.favorite_foods f
      where f.user_id = '33333333-3333-4333-8333-333333333333') as cleo_favs,
    (select count(*) from public.foods where source = 'off') as off_rows;

select ok(not has_function_privilege('authenticated', 'public.convert_used_off_foods()', 'execute')
  and not has_function_privilege('anon', 'public.convert_used_off_foods()', 'execute'),
  'users cannot execute the conversion');

select public.convert_used_off_foods();

select is((select count(*) from public.food_log_entries l join public.foods f on f.id = l.food_id where f.source = 'off')
  + (select count(*) from public.favorite_foods l join public.foods f on f.id = l.food_id where f.source = 'off')
  + (select count(*) from public.meal_template_items l join public.foods f on f.id = l.food_id where f.source = 'off'),
  0::bigint, 'no entry, favourite or comida item references an OFF food any more');

select is((select count(*) from public.food_log_entries
           where user_id = '11111111-1111-4111-8111-111111111111' and food_id = 'aaaaaaaa-0000-4000-8000-000000000001'),
  3::bigint, 'Ana''s entries of the milk point to her existing copy of that barcode');
select is((select count(*) from public.food_log_entries l join public.foods f on f.id = l.food_id
           where l.food_name = 'Pan OFF' and f.source = 'user' and f.created_by = l.user_id and f.barcode = '1000000000002'),
  1::bigint, 'the bread entry points to a new copy owned by Ana');

select results_eq(
  $$ select food_id from public.favorite_foods where user_id = '11111111-1111-4111-8111-111111111111' order by 1 $$,
  $$ select id from public.foods where created_by = '11111111-1111-4111-8111-111111111111' order by 1 $$,
  'Ana''s favourites are her two copies, with no duplicate favourite');
select is((select i.food_id from public.meal_template_items i),
  (select l.food_id from public.food_log_entries l where l.food_name = 'Pan OFF'),
  'the comida item points to the same copy as the diary entry');

select is((select md5(string_agg(concat_ws('|', user_id, log_date, sum_kcal, sum_p, sum_c, sum_f, n), ';' order by user_id, log_date))
           from (select user_id, log_date, sum(kcal) sum_kcal, sum(protein_g) sum_p, sum(carbs_g) sum_c, sum(fat_g) sum_f, count(*) n
                 from public.food_log_entries group by user_id, log_date) t),
  (select totals from snap), 'every per-day total and entry count is identical');

select is((select count(distinct l.food_id) from public.food_log_entries l
           where l.food_name = 'Leche OFF' and l.user_id = '22222222-2222-4222-8222-222222222222'
             and l.food_id in (select id from public.foods where created_by = l.user_id)), 1::bigint,
  'Bea got her own copy');
select isnt((select food_id from public.food_log_entries where user_id = '22222222-2222-4222-8222-222222222222'),
  'aaaaaaaa-0000-4000-8000-000000000001'::uuid, 'Bea''s copy is not Ana''s');

select is((select md5(string_agg(to_jsonb(l)::text, '|' order by l.id)) from public.food_log_entries l
           where l.user_id = '33333333-3333-4333-8333-333333333333'), (select cleo_entries from snap),
  'Cleo''s entries are byte-identical');
select is((select md5(string_agg(to_jsonb(f)::text, '|' order by f.food_id)) from public.favorite_foods f
           where f.user_id = '33333333-3333-4333-8333-333333333333'), (select cleo_favs from snap),
  'Cleo''s favourites are byte-identical');
select is((select count(*) from public.foods where source = 'off'), (select off_rows from snap),
  'no OFF row is deleted, and an unused one is untouched');

-- Idempotent
create temp table snap2 as
  select (select md5(string_agg(to_jsonb(l)::text, '|' order by l.id)) from public.food_log_entries l) as e,
         (select md5(string_agg(to_jsonb(f)::text, '|' order by f.id)) from public.foods f) as f;
select public.convert_used_off_foods();
select is((select md5(string_agg(to_jsonb(l)::text, '|' order by l.id)) from public.food_log_entries l)
          || (select md5(string_agg(to_jsonb(f)::text, '|' order by f.id)) from public.foods f),
  (select e || f from snap2), 'running it twice changes nothing');

-- Failure branch: a write that moves a total aborts the conversion.
create function public.tamper_kcal() returns trigger language plpgsql as $$
begin
  new.kcal := new.kcal + 1;
  return new;
end;
$$;
create trigger tamper_kcal before update on public.food_log_entries
  for each row execute function public.tamper_kcal();
insert into public.food_log_entries (user_id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
  select user_id, '2027-01-12', id, '0ff00000-0000-4000-8000-000000000003', 100, 'Sin uso OFF', 100, 1, 1, 1
  from public.meal_slots where user_id = '33333333-3333-4333-8333-333333333333' and code = 'breakfast';
select throws_ok($$ select public.convert_used_off_foods() $$, 'P0001',
  'convert_used_off_foods: a diary total or entry count changed', 'a changed total aborts the conversion');

select * from finish();
rollback;
