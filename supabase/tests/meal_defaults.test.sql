-- pgTAP: meal_defaults / diary_default_applications, save_meal_defaults and apply_meal_defaults (T-109, US-51, ADR-61).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(78);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100, protein_100, carbs_100, fat_100, base_unit) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Huevos revueltos', 150, 10, 5, 8, 'g'),
  ('0ff00000-0000-4000-8000-000000000002', 'off', 'Zumo', 40, 0.5, 9, 0.1, 'ml');

-- Ana: T1 is suggested for lunch on purpose (the default's meal must win); T2 has a drink marked as fried.
insert into public.meal_templates (id, user_id, name, meal_slot_id) values
  ('a1000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Desayuno A',
   (select id from public.meal_slots where user_id = '11111111-1111-4111-8111-111111111111' and code = 'lunch')),
  ('a2000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Comida A', null),
  ('a3000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'Vacia A', null),
  ('b1000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Desayuno B', null),
  ('b2000000-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'Cena B', null);
insert into public.meal_template_items (template_id, food_id, amount, portion_label, position, cooking_method) values
  ('a1000000-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000001', 120, '1 plato', 0, 'raw'),
  ('a1000000-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000001', 100, null, 1, 'griddle'),
  ('a2000000-0000-4000-8000-000000000002', '0ff00000-0000-4000-8000-000000000002', 200, null, 0, 'fried'),
  ('a2000000-0000-4000-8000-000000000002', '0ff00000-0000-4000-8000-000000000001', 50, null, 1, 'raw'),
  ('b1000000-0000-4000-8000-000000000001', '0ff00000-0000-4000-8000-000000000001', 100, null, 0, 'raw'),
  ('b2000000-0000-4000-8000-000000000002', '0ff00000-0000-4000-8000-000000000001', 100, null, 0, 'raw');

-- Fixed slot ids per user for readability.
create temp table slots (k text primary key, v uuid);
grant all on slots to authenticated;
insert into slots
  select 'a_' || code, id from public.meal_slots
  where user_id = '11111111-1111-4111-8111-111111111111' and code in ('breakfast', 'lunch');
insert into slots
  select 'b_' || code, id from public.meal_slots
  where user_id = '22222222-2222-4222-8222-222222222222' and code in ('breakfast', 'dinner');

-- Days: UTC yesterday / today / tomorrow and their ISO weekdays (never hard-coded).
create temp table days (k text primary key, v date);
grant all on days to authenticated;
insert into days values
  ('ty', ((now() at time zone 'utc')::date) - 1),
  ('t',  ((now() at time zone 'utc')::date)),
  ('t1', ((now() at time zone 'utc')::date) + 1);

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select columns_are('public', 'meal_defaults',
  array['user_id', 'meal_slot_id', 'template_id', 'weekday', 'created_at'], 'meal_defaults has the planned columns');
select columns_are('public', 'diary_default_applications',
  array['user_id', 'log_date', 'applied_at'], 'diary_default_applications has the planned columns');
select ok(
  (select bool_and(relrowsecurity and relforcerowsecurity) from pg_class
   where oid in ('public.meal_defaults'::regclass, 'public.diary_default_applications'::regclass)),
  'RLS is enabled and forced on both tables');
select table_privs_are('public', 'meal_defaults', 'anon', array[]::text[], 'anon has no privileges on meal_defaults');
select table_privs_are('public', 'diary_default_applications', 'anon', array[]::text[],
  'anon has no privileges on diary_default_applications');
select has_index('public', 'meal_defaults', 'meal_defaults_template_idx', 'index on template exists');
select is((select prosecdef from pg_proc where proname = 'save_meal_defaults' and pronamespace = 'public'::regnamespace),
  false, 'save_meal_defaults is security invoker');
select is((select prosecdef from pg_proc where proname = 'apply_meal_defaults' and pronamespace = 'public'::regnamespace),
  false, 'apply_meal_defaults is security invoker');
select ok(not has_function_privilege('anon', 'public.save_meal_defaults(jsonb)', 'execute'),
  'anon cannot execute save_meal_defaults');
select ok(not has_function_privilege('anon', 'public.apply_meal_defaults(date)', 'execute'),
  'anon cannot execute apply_meal_defaults');

-- ---------------------------------------------------------------------------
-- Bea's own rows (for isolation), written directly
-- ---------------------------------------------------------------------------
insert into public.meal_defaults (user_id, meal_slot_id, template_id, weekday) values
  ('22222222-2222-4222-8222-222222222222', (select v from slots where k = 'b_breakfast'),
   'b1000000-0000-4000-8000-000000000001', 2);
insert into public.diary_default_applications (user_id, log_date) values
  ('22222222-2222-4222-8222-222222222222', date '2020-01-01');

-- ---------------------------------------------------------------------------
-- Anon is denied
-- ---------------------------------------------------------------------------
set local role anon;
select throws_ok($$select * from public.meal_defaults$$, '42501', null, 'anon cannot read meal_defaults');
select throws_ok($$select * from public.diary_default_applications$$, '42501', null,
  'anon cannot read diary_default_applications');

-- ---------------------------------------------------------------------------
-- As Ana: direct constraints and isolation
-- ---------------------------------------------------------------------------
reset role;
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.meal_defaults), 0, 'Ana does not see Bea''s default');
select is((select count(*)::int from public.diary_default_applications), 0, 'Ana does not see Bea''s marker');
select lives_ok(
  $$insert into public.meal_defaults (meal_slot_id, template_id, weekday)
    values ((select v from slots where k = 'a_breakfast'), 'a1000000-0000-4000-8000-000000000001', 3)$$,
  'Ana inserts a default; user_id defaults to the caller');
select throws_ok(
  $$insert into public.meal_defaults (meal_slot_id, template_id, weekday)
    values ((select v from slots where k = 'a_breakfast'), 'a2000000-0000-4000-8000-000000000002', 3)$$,
  '23505', null, 'a second default for the same meal and weekday is refused');
select throws_ok(
  $$insert into public.meal_defaults (meal_slot_id, template_id, weekday)
    values ((select v from slots where k = 'a_breakfast'), 'a1000000-0000-4000-8000-000000000001', 0)$$,
  '23514', null, 'weekday 0 is refused');
select throws_ok(
  $$insert into public.meal_defaults (meal_slot_id, template_id, weekday)
    values ((select v from slots where k = 'a_breakfast'), 'a1000000-0000-4000-8000-000000000001', 8)$$,
  '23514', null, 'weekday 8 is refused');
select throws_ok(
  $$insert into public.meal_defaults (user_id, meal_slot_id, template_id, weekday)
    values ('22222222-2222-4222-8222-222222222222', (select v from slots where k = 'a_breakfast'),
            'a1000000-0000-4000-8000-000000000001', 4)$$,
  '42501', null, 'Ana cannot write a default for Bea');
select throws_ok(
  $$insert into public.meal_defaults (meal_slot_id, template_id, weekday)
    values ((select v from slots where k = 'a_lunch'), 'b1000000-0000-4000-8000-000000000001', 4)$$,
  '23503', null, 'a default cannot point at another user''s comida');
select throws_ok(
  $$insert into public.meal_defaults (meal_slot_id, template_id, weekday)
    values ((select v from slots where k = 'b_breakfast'), 'a1000000-0000-4000-8000-000000000001', 4)$$,
  '23503', null, 'a default cannot point at another user''s meal');
delete from public.meal_defaults;

-- ---------------------------------------------------------------------------
-- save_meal_defaults
-- ---------------------------------------------------------------------------
select lives_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_breakfast'),
      'template_id', 'a1000000-0000-4000-8000-000000000001',
      'weekdays', jsonb_build_array(2, 4))))$$,
  'a valid save succeeds');
select results_eq(
  $$select weekday::int from public.meal_defaults order by weekday$$,
  $$values (2), (4)$$, 'one row per meal and weekday is stored');

-- Invalid payloads leave the old rows intact (atomic) and use the right codes.
select throws_ok($$select public.save_meal_defaults('{}'::jsonb)$$, '22023', null, 'a non-array is refused');
select throws_ok($$select public.save_meal_defaults(null)$$, '22023', null, 'null is refused');
select throws_ok($$select public.save_meal_defaults('[1]'::jsonb)$$, '22023', null, 'a non-object element is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array(8))))$$,
  '22023', null, 'weekday 8 is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array(1.5))))$$,
  '22023', null, 'a fractional weekday is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array('1'))))$$,
  '22023', null, 'a text weekday is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array(1, 1))))$$,
  '22023', null, 'duplicate weekdays in one element are refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_lunch'),
        'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array(1)),
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_lunch'),
        'template_id', 'a1000000-0000-4000-8000-000000000001', 'weekdays', jsonb_build_array(1))))$$,
  '22023', null, 'the same meal and weekday in two elements is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', 'not-a-uuid', 'template_id', 'a2000000-0000-4000-8000-000000000002',
      'weekdays', jsonb_build_array(1))))$$,
  '22023', null, 'a malformed id is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'b_breakfast'),
      'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array(1))))$$,
  'P0002', null, 'another user''s meal is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'b1000000-0000-4000-8000-000000000001', 'weekdays', jsonb_build_array(1))))$$,
  'P0002', null, 'another user''s comida is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'ffffffff-ffff-4fff-8fff-ffffffffffff', 'weekdays', jsonb_build_array(1))))$$,
  'P0002', null, 'an unknown comida is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_lunch'),
      'template_id', 'a3000000-0000-4000-8000-000000000003', 'weekdays', jsonb_build_array(1))))$$,
  '23514', null, 'a comida without items is refused');
select throws_ok(
  $$select public.save_meal_defaults(jsonb_build_array(
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_breakfast'),
        'template_id', 'a1000000-0000-4000-8000-000000000001', 'weekdays', jsonb_build_array(1, 2)),
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_lunch'),
        'template_id', 'a2000000-0000-4000-8000-000000000002', 'weekdays', jsonb_build_array(3)),
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_lunch'),
        'template_id', 'b1000000-0000-4000-8000-000000000001', 'weekdays', jsonb_build_array(4))))$$,
  'P0002', null, 'one comida not owned among several valid elements refuses the whole save');
select results_eq($$select weekday::int from public.meal_defaults order by weekday$$,
  $$values (2), (4)$$, 'the refused saves left the previous defaults intact');

select lives_ok($$select public.save_meal_defaults('[]'::jsonb)$$, 'an empty array is accepted');
select is((select count(*)::int from public.meal_defaults), 0, 'an empty array clears the defaults');

-- Defaults for the apply tests: breakfast T1 on yesterday + today, lunch T2 on today; tomorrow has nothing.
select lives_ok(
  $$select public.save_meal_defaults(jsonb_build_array(
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_breakfast'),
        'template_id', 'a1000000-0000-4000-8000-000000000001',
        'weekdays', jsonb_build_array(extract(isodow from (select v from days where k = 'ty'))::int,
                                      extract(isodow from (select v from days where k = 't'))::int)),
      jsonb_build_object('meal_slot_id', (select v from slots where k = 'a_lunch'),
        'template_id', 'a2000000-0000-4000-8000-000000000002',
        'weekdays', jsonb_build_array(extract(isodow from (select v from days where k = 't'))::int))))$$,
  'defaults for the apply tests are saved');

-- ---------------------------------------------------------------------------
-- apply_meal_defaults
-- ---------------------------------------------------------------------------
select is(public.apply_meal_defaults((select v from days where k = 't1')), 0,
  'a weekday without a default adds nothing');
select is((select count(*)::int from public.diary_default_applications where log_date = (select v from days where k = 't1')),
  1, 'the day without a default is still marked');

select is(public.apply_meal_defaults((select v from days where k = 't')), 4,
  'today applies the four items of the two default comidas');
select results_eq(
  $$select s.code::text, e.food_name, e.amount, e.portion_label, e.kcal, e.protein_g, e.carbs_g, e.fat_g,
           e.cooking_method::text, e.cooking_oil_retained_g
    from public.food_log_entries e join public.meal_slots s on s.id = e.meal_slot_id
    where e.log_date = (select v from days where k = 't')
    order by s.position, e.created_at, e.amount desc, e.cooking_method$$,
  $$values
    ('breakfast'::text, 'Huevos revueltos'::text, 120.0::numeric, '1 plato'::text, 180.0::numeric, 12.0::numeric, 6.0::numeric, 9.6::numeric, 'raw'::text, null::numeric),
    ('breakfast', 'Huevos revueltos', 100.0, null, 159.0, 10.0, 5.0, 9.0, 'griddle', 1.00),
    ('lunch', 'Zumo', 200.0, null, 80.0, 1.0, 18.0, 0.2, 'raw', null),
    ('lunch', 'Huevos revueltos', 50.0, null, 75.0, 5.0, 2.5, 4.0, 'raw', null)$$,
  'entries go to the default''s meal (not the comida''s suggestion) with scaled nutrition, griddle fat from the 1 % default of the raw weight and the drink forced raw');
select is(public.apply_meal_defaults((select v from days where k = 't')), 0, 'a second call adds nothing');
select is((select count(*)::int from public.food_log_entries where log_date = (select v from days where k = 't')),
  4, 'the second call did not duplicate entries');

-- T-117 (US-55, ADR-68): each default is one group named after its comida.
select results_eq(
  $$select count(distinct e.group_id)::int, count(e.group_id)::int, min(e.group_name)
    from public.food_log_entries e
    where e.log_date = (select v from days where k = 't')
      and e.meal_slot_id = (select v from slots where k = 'a_breakfast')$$,
  $$values (1, 2, 'Desayuno A'::text)$$, 'the breakfast default entries share one non-null group named after the comida');
select is((select min(e.group_name) from public.food_log_entries e
           where e.log_date = (select v from days where k = 't')
             and e.meal_slot_id = (select v from slots where k = 'a_lunch')),
  'Comida A', 'the lunch default entries are grouped under their own comida name');
select is((select count(distinct e.group_id)::int from public.food_log_entries e
           where e.log_date = (select v from days where k = 't')),
  2, 'two meals with defaults give two different group ids');
select is((select count(*)::int from public.food_log_entries e
           where e.log_date = (select v from days where k = 't') and e.group_id is null),
  0, 'no default entry is left ungrouped');

-- Changes after an application only affect the next one: edit T1, raise the griddle percentage, apply yesterday.
select lives_ok(
  $$select public.save_meal_template('a1000000-0000-4000-8000-000000000001', 'Desayuno A',
      (select v from slots where k = 'a_breakfast'),
      '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":200,"portion_label":null},
        {"food_id":"0ff00000-0000-4000-8000-000000000001","amount":100,"portion_label":null,"cooking_method":"griddle"}]'::jsonb)$$,
  'the comida is edited after today was applied');
insert into public.food_preferences (fat_pct_griddle) values (10);
select is(public.apply_meal_defaults((select v from days where k = 'ty')), 2, 'yesterday (an allowed date) applies the edited comida');
select results_eq(
  $$select e.amount, e.kcal, e.fat_g, e.cooking_method::text, e.cooking_oil_retained_g
    from public.food_log_entries e where e.log_date = (select v from days where k = 'ty')
    order by e.amount desc$$,
  $$values (200.0::numeric, 300.0::numeric, 16.0::numeric, 'raw'::text, null::numeric),
           (100.0, 240.0, 18.0, 'griddle', 10.00)$$,
  'the new application uses the current items and the current preference (10 % of 100 g adds 10 g of fat)');
select results_eq(
  $$select kcal from public.food_log_entries where log_date = (select v from days where k = 't')
    and meal_slot_id = (select v from slots where k = 'a_breakfast') order by kcal$$,
  $$values (159.0::numeric), (180.0::numeric)$$, 'entries of the earlier application keep their snapshots');

-- Deleting a comida removes its default but not entries already logged on an earlier day.
delete from public.meal_templates where id = 'a2000000-0000-4000-8000-000000000002';
select is((select count(*)::int from public.meal_defaults where template_id = 'a2000000-0000-4000-8000-000000000002'),
  0, 'deleting a comida removes its defaults');
select is((select count(*)::int from public.food_log_entries
           where log_date = (select v from days where k = 't')
             and meal_slot_id = (select v from slots where k = 'a_lunch')),
  2, 'deleting a comida leaves the entries it produced');

-- Deleted entries do not come back.
delete from public.food_log_entries where log_date = (select v from days where k = 't');
select is(public.apply_meal_defaults((select v from days where k = 't')), 0, 'deleting the entries does not re-add them');
select is((select count(*)::int from public.food_log_entries where log_date = (select v from days where k = 't')),
  0, 'the day stays empty');

-- A day that already has entries is only marked.
select lives_ok(
  $$select public.save_meal_defaults(jsonb_build_array(jsonb_build_object(
      'meal_slot_id', (select v from slots where k = 'a_breakfast'),
      'template_id', 'a1000000-0000-4000-8000-000000000001',
      'weekdays', jsonb_build_array(extract(isodow from (select v from days where k = 't1'))::int))))$$,
  'a default for tomorrow is saved');
delete from public.diary_default_applications where log_date = (select v from days where k = 't1');
insert into public.food_log_entries (log_date, meal_slot_id, food_id, amount, food_name, kcal, protein_g, carbs_g, fat_g)
values ((select v from days where k = 't1'), (select v from slots where k = 'a_lunch'),
        '0ff00000-0000-4000-8000-000000000001', 10, 'Huevos revueltos', 15, 1, 0.5, 0.8);
select is(public.apply_meal_defaults((select v from days where k = 't1')), 0, 'a day with entries is not filled');
select is((select count(*)::int from public.food_log_entries where log_date = (select v from days where k = 't1')),
  1, 'the existing entry is the only one');
select is((select count(*)::int from public.diary_default_applications where log_date = (select v from days where k = 't1')),
  1, 'the day with entries is still marked');

-- Date guard: more than one day from UTC today is refused and leaves no marker.
select throws_ok($$select public.apply_meal_defaults(((now() at time zone 'utc')::date) - 2)$$, '22023', null,
  'two days ago is refused');
select throws_ok($$select public.apply_meal_defaults(((now() at time zone 'utc')::date) + 2)$$, '22023', null,
  'two days ahead is refused');
select throws_ok($$select public.apply_meal_defaults(null)$$, '22023', null, 'a null date is refused');
select is((select count(*)::int from public.diary_default_applications
           where log_date in (((now() at time zone 'utc')::date) - 2, ((now() at time zone 'utc')::date) + 2)),
  0, 'a refused date leaves no marker');

-- ---------------------------------------------------------------------------
-- Isolation as Bea, and unauthenticated calls
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.meal_defaults), 1, 'Bea sees only her own default');
select is((select count(*)::int from public.diary_default_applications), 1, 'Bea sees only her own marker');
delete from public.meal_defaults where weekday <> 2;
update public.meal_defaults set weekday = 5 where weekday <> 2;
select is((select count(*)::int from public.meal_defaults where weekday = 2), 1, 'Bea''s default is untouched by that');
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select is((select count(*)::int from public.meal_defaults), 1, 'Ana''s default survives Bea''s delete and update');

select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$select public.save_meal_defaults('[]'::jsonb)$$, '28000', null,
  'save_meal_defaults requires a user');
select throws_ok($$select public.apply_meal_defaults(current_date)$$, '28000', null,
  'apply_meal_defaults requires a user');

-- ---------------------------------------------------------------------------
-- Cascades (as the table owner: authenticated cannot delete meal slots)
-- ---------------------------------------------------------------------------
reset role;
insert into public.meal_slots (id, user_id, code, label, position) values
  ('c0000000-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', null, 'Extra', 99);
insert into public.meal_defaults (user_id, meal_slot_id, template_id, weekday) values
  ('22222222-2222-4222-8222-222222222222', 'c0000000-0000-4000-8000-000000000001',
   'b2000000-0000-4000-8000-000000000002', 6),
  ('22222222-2222-4222-8222-222222222222', (select v from slots where k = 'b_dinner'),
   'b2000000-0000-4000-8000-000000000002', 7);
delete from public.meal_slots where id = 'c0000000-0000-4000-8000-000000000001';
select is((select count(*)::int from public.meal_defaults
           where user_id = '22222222-2222-4222-8222-222222222222' and weekday = 6), 0,
  'deleting a meal removes its defaults');
delete from public.meal_templates where id = 'b2000000-0000-4000-8000-000000000002';
select is((select count(*)::int from public.meal_defaults
           where user_id = '22222222-2222-4222-8222-222222222222' and weekday = 7), 0,
  'deleting a comida removes its defaults for every meal');
select is((select count(*)::int from public.meal_defaults where user_id = '22222222-2222-4222-8222-222222222222'), 1,
  'other defaults of the user remain');
delete from auth.users where id = '22222222-2222-4222-8222-222222222222';
select is((select count(*)::int from public.meal_defaults where user_id = '22222222-2222-4222-8222-222222222222'), 0,
  'deleting the user removes their defaults');
select is((select count(*)::int from public.diary_default_applications
           where user_id = '22222222-2222-4222-8222-222222222222'), 0,
  'deleting the user removes their markers');

select * from finish();
rollback;
