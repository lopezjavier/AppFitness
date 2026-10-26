-- pgTAP: grouped comidas in the Diario (T-117, US-55, ADR-68): group columns, trigger, save_meal_as_comida, isolation.
-- apply_meal_defaults grouping is asserted in meal_defaults.test.sql.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(36);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100, protein_100, carbs_100, fat_100, base_unit) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Avena', 100, 10, 60, 7, 'g');

create temp table slots (k text primary key, v uuid);
grant all on slots to authenticated;
insert into slots
  select 'a_' || code, id from public.meal_slots
  where user_id = '11111111-1111-4111-8111-111111111111' and code in ('breakfast', 'lunch');
insert into slots
  select 'b_' || code, id from public.meal_slots
  where user_id = '22222222-2222-4222-8222-222222222222' and code = 'breakfast';

create temp table res (id uuid);
grant all on res to authenticated;

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select col_is_null('public', 'food_log_entries', 'group_id', 'group_id is nullable');
select col_is_null('public', 'food_log_entries', 'group_name', 'group_name is nullable');
select has_trigger('public', 'food_log_entries', 'food_log_entries_clear_group_on_move',
  'the clear-group-on-move trigger exists');
select ok(not has_function_privilege('anon', 'public.save_meal_as_comida(date, uuid, text, jsonb, uuid[])', 'execute'),
  'anon cannot execute save_meal_as_comida');
select is((select prosecdef from pg_proc where proname = 'save_meal_as_comida' and pronamespace = 'public'::regnamespace),
  false, 'save_meal_as_comida is security invoker');

-- ---------------------------------------------------------------------------
-- As Ana: constraints
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$insert into public.food_log_entries (id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
    values ('e0000000-0000-4000-8000-000000000001', date '2030-01-03', (select v from slots where k = 'a_breakfast'),
            '0ff00000-0000-4000-8000-000000000001', 100, 'Avena', 100, 10, 60, 7)$$,
  'an entry without group columns still inserts');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, group_id)
    values (date '2030-01-03', (select v from slots where k = 'a_breakfast'),
            '0ff00000-0000-4000-8000-000000000001', 100, 'Avena', 100, 10, 60, 7, gen_random_uuid())$$,
  '23514', null, 'a group id without a name is refused');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, group_name)
    values (date '2030-01-03', (select v from slots where k = 'a_breakfast'),
            '0ff00000-0000-4000-8000-000000000001', 100, 'Avena', 100, 10, 60, 7, 'Avena con platano')$$,
  '23514', null, 'a group name without an id is refused');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, group_id, group_name)
    values (date '2030-01-03', (select v from slots where k = 'a_breakfast'),
            '0ff00000-0000-4000-8000-000000000001', 100, 'Avena', 100, 10, 60, 7, gen_random_uuid(), '   ')$$,
  '23514', null, 'a blank group name is refused');
select throws_ok(
  $$insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, group_id, group_name)
    values (date '2030-01-03', (select v from slots where k = 'a_breakfast'),
            '0ff00000-0000-4000-8000-000000000001', 100, 'Avena', 100, 10, 60, 7, gen_random_uuid(), repeat('x', 61))$$,
  '23514', null, 'a 61-character group name is refused');
select lives_ok(
  $$insert into public.food_log_entries (id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, group_id, group_name)
    values ('e0000000-0000-4000-8000-000000000002', date '2030-01-03', (select v from slots where k = 'a_breakfast'),
            '0ff00000-0000-4000-8000-000000000001', 100, 'Avena', 100, 10, 60, 7,
            'c0000000-0000-4000-8000-0000000000a1', 'Avena con platano')$$,
  'a grouped entry inserts');

-- ---------------------------------------------------------------------------
-- Trigger: moving to another meal ungroups, editing the amount keeps the group
-- ---------------------------------------------------------------------------
update public.food_log_entries set amount = 150 where id = 'e0000000-0000-4000-8000-000000000002';
select is((select group_id from public.food_log_entries where id = 'e0000000-0000-4000-8000-000000000002'),
  'c0000000-0000-4000-8000-0000000000a1'::uuid, 'editing the amount keeps the group');
update public.food_log_entries set meal_slot_id = (select v from slots where k = 'a_lunch')
  where id = 'e0000000-0000-4000-8000-000000000002';
select results_eq(
  $$select group_id, group_name from public.food_log_entries where id = 'e0000000-0000-4000-8000-000000000002'$$,
  $$values (null::uuid, null::text)$$, 'moving the entry to another meal clears its group');

-- ---------------------------------------------------------------------------
-- save_meal_as_comida (day 2030-01-01 breakfast: e1..e4; e2 and e4 share an older group)
-- ---------------------------------------------------------------------------
insert into public.food_log_entries (id, log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g, group_id, group_name)
select ('e1000000-0000-4000-8000-00000000000' || n)::uuid, d, s, '0ff00000-0000-4000-8000-000000000001',
       100, 'Avena', 100 * n, 10, 60, 7, g, case when g is null then null else 'Vieja' end
from (values
  (1, date '2030-01-01', (select v from slots where k = 'a_breakfast'), null::uuid),
  (2, date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'c0000000-0000-4000-8000-0000000000b1'::uuid),
  (3, date '2030-01-01', (select v from slots where k = 'a_breakfast'), null::uuid),
  (4, date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'c0000000-0000-4000-8000-0000000000b1'::uuid),
  (5, date '2030-01-01', (select v from slots where k = 'a_lunch'), null::uuid),
  (6, date '2030-01-02', (select v from slots where k = 'a_breakfast'), null::uuid)
) as t(n, d, s, g);

create temp table before_sum as
  select log_date, kcal, protein_g, carbs_g, fat_g from public.food_diary_summary(date '2030-01-01', date '2030-01-01');

insert into res
select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'),
  '  Mi desayuno ',
  '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100,"portion_label":null}]'::jsonb,
  array['e1000000-0000-4000-8000-000000000001', 'e1000000-0000-4000-8000-000000000002']::uuid[]);
select is((select count(*)::int from public.meal_templates where id = (select id from res) and name = 'Mi desayuno'),
  1, 'save_meal_as_comida creates the comida with the trimmed name');
select results_eq(
  $$select count(distinct group_id)::int, count(group_id)::int, min(group_name)
    from public.food_log_entries
    where id in ('e1000000-0000-4000-8000-000000000001', 'e1000000-0000-4000-8000-000000000002')$$,
  $$values (1, 2, 'Mi desayuno'::text)$$, 'the listed entries share one new group named after the comida');
select is((select count(*)::int from public.food_log_entries
           where id in ('e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000005',
                        'e1000000-0000-4000-8000-000000000006')
             and group_id is not null),
  0, 'entries not listed, of another meal or of another day stay ungrouped');
select results_eq(
  $$select id, group_id from public.food_log_entries
    where id in ('e1000000-0000-4000-8000-000000000002', 'e1000000-0000-4000-8000-000000000004')
    order by id$$,
  $$select 'e1000000-0000-4000-8000-000000000002'::uuid,
           (select group_id from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000001')
    union all select 'e1000000-0000-4000-8000-000000000004'::uuid, 'c0000000-0000-4000-8000-0000000000b1'::uuid
    order by 1$$,
  'a listed entry leaves its older group while an unlisted one keeps it');

select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'),
      'Mi desayuno',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100,"portion_label":null}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003']::uuid[])$$,
  '23505', null, 'a duplicate comida name is refused');
select is((select group_id from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000003'),
  null::uuid, 'the refused duplicate left the entry ungrouped');

select results_eq(
  $$select log_date, kcal, protein_g, carbs_g, fat_g from public.food_diary_summary(date '2030-01-01', date '2030-01-01')$$,
  $$select log_date, kcal, protein_g, carbs_g, fat_g from before_sum$$,
  'grouping does not change the day totals');

select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'X',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003']::uuid[])$$,
  '28000', null, 'save_meal_as_comida requires a user');
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'X',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb, array[]::uuid[])$$,
  '22023', null, 'empty entry ids are refused');
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'X',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb, null)$$,
  '22023', null, 'null entry ids are refused');

-- ---------------------------------------------------------------------------
-- Isolation: Bea cannot see, update or group Ana's entries
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.food_log_entries), 0, 'Bea does not see Ana''s grouped entries');
with u as (update public.food_log_entries set group_name = 'Hack' returning 1)
select is((select count(*)::int from u), 0, 'Bea cannot update Ana''s grouped entries');
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'b_breakfast'), 'Comida de Bea',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000004']::uuid[])$$,
  '22023', 'unknown or foreign entry id', 'Bea saving a comida with Ana''s entry ids is refused');
select is((select count(*)::int from public.meal_templates where name = 'Comida de Bea'),
  0, 'the refused call did not keep Bea''s comida');
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select results_eq(
  $$select id, group_name from public.food_log_entries
    where id in ('e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000004') order by id$$,
  $$values ('e1000000-0000-4000-8000-000000000003'::uuid, null::text),
           ('e1000000-0000-4000-8000-000000000004'::uuid, 'Vieja'::text)$$,
  'Ana''s entries are unchanged after Bea''s attempt');

-- ---------------------------------------------------------------------------
-- Strictness (T-122): unknown ids, other day or meal raise and roll everything back
-- ---------------------------------------------------------------------------
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'Estricta',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003', 'deadbeef-0000-4000-8000-000000000000']::uuid[])$$,
  '22023', 'unknown or foreign entry id', 'an unknown entry id is refused');
select is((select count(*)::int from public.meal_templates where name = 'Estricta'),
  0, 'the refused call did not keep the comida');
select is((select group_id from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000003'),
  null::uuid, 'the valid entry in a refused call stays ungrouped');
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'Estricta',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000005']::uuid[])$$,
  '22023', 'unknown or foreign entry id', 'an entry id of another meal is refused');
select throws_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'Estricta',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000006']::uuid[])$$,
  '22023', 'unknown or foreign entry id', 'an entry id of another day is refused');
select is((select count(*)::int from public.food_log_entries
           where id in ('e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000005',
                        'e1000000-0000-4000-8000-000000000006')
             and group_id is not null),
  0, 'no entry was grouped by the refused calls');
select lives_ok(
  $$select public.save_meal_as_comida(date '2030-01-01', (select v from slots where k = 'a_breakfast'), 'Con duplicados',
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100}]'::jsonb,
      array['e1000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000003']::uuid[])$$,
  'duplicate ids in the array are accepted');
select isnt((select group_id from public.food_log_entries where id = 'e1000000-0000-4000-8000-000000000003'),
  null::uuid, 'the entry listed twice is grouped');

select * from finish();
rollback;
