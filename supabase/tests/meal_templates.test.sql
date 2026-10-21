-- pgTAP: meal_templates / meal_template_items RLS and constraints, and save_meal_template (T-061, US-21).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(33);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');
update public.profiles set onboarding_completed = true;  -- seeds the default meal slots

insert into public.foods (id, source, name, kcal_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', 'Huevos revueltos', 150),
  ('0ff00000-0000-4000-8000-000000000002', 'off', 'Batido de proteina', 80);
insert into public.foods (id, source, name, kcal_100, created_by) values
  ('aaaaaaaa-0000-4000-8000-000000000001', 'user', 'Tortilla de Ana', 190, '11111111-1111-4111-8111-111111111111'),
  ('bbbbbbbb-0000-4000-8000-000000000001', 'user', 'Pan de Bea', 250, '22222222-2222-4222-8222-222222222222');

-- ---------------------------------------------------------------------------
-- Schema and security
-- ---------------------------------------------------------------------------
select columns_are('public', 'meal_templates',
  array['id', 'user_id', 'name', 'meal_slot_id', 'created_at'], 'meal_templates has the planned columns');
select columns_are('public', 'meal_template_items',
  array['id', 'template_id', 'food_id', 'amount', 'portion_label', 'position'],
  'meal_template_items has the planned columns');
select ok(
  (select bool_and(relrowsecurity and relforcerowsecurity) from pg_class
   where oid in ('public.meal_templates'::regclass, 'public.meal_template_items'::regclass)),
  'RLS is enabled and forced on both tables');
select table_privs_are('public', 'meal_templates', 'anon', array[]::text[], 'anon has no privileges on meal_templates');
select table_privs_are('public', 'meal_template_items', 'anon', array[]::text[],
  'anon has no privileges on meal_template_items');
select has_index('public', 'meal_template_items', 'meal_template_items_food_id_idx', 'index on food_id exists');

-- ---------------------------------------------------------------------------
-- As Ana: RPC and constraints
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

create temp table ids (k text primary key, v uuid);
grant all on ids to authenticated;

insert into ids
  select 'desayuno_a', public.save_meal_template(
    null, '  Desayuno A ', (select id from public.meal_slots where code = 'breakfast'),
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":120.0,"portion_label":"1 plato"},
      {"food_id":"0ff00000-0000-4000-8000-000000000002","amount":300,"portion_label":null}]'::jsonb);

select is((select name from public.meal_templates where id = (select v from ids where k = 'desayuno_a')),
  'Desayuno A', 'save_meal_template creates a template with a trimmed name owned by the caller');
select results_eq(
  $$select food_id, amount, portion_label, position::int from public.meal_template_items
    where template_id = (select v from ids where k = 'desayuno_a') order by position$$,
  $$values ('0ff00000-0000-4000-8000-000000000001'::uuid, 120.0::numeric, '1 plato'::text, 0),
           ('0ff00000-0000-4000-8000-000000000002'::uuid, 300.0::numeric, null::text, 1)$$,
  'items are stored in order with amount and portion label');

select throws_ok(
  $$select public.save_meal_template(null, 'desayuno a', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  '23505', null, 'a name differing only by case is rejected');
select throws_ok(
  $$select public.save_meal_template(null, ' DESAYUNO A  ', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  '23505', null, 'a name differing only by case and whitespace is rejected');
select throws_ok(
  $$select public.save_meal_template(null, '   ', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  '22023', null, 'a blank name is rejected');
select throws_ok(
  $$select public.save_meal_template(null, repeat('x', 61), null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  '22023', null, 'a name over 60 characters is rejected');
select throws_ok(
  $$select public.save_meal_template(null, 'Vacia', null, '[]'::jsonb)$$,
  '22023', null, 'an empty item list is rejected');
select throws_ok(
  $$select public.save_meal_template(null, 'Mala', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":0}]'::jsonb)$$,
  '22023', null, 'an amount of 0 is rejected');
select throws_ok(
  $$select public.save_meal_template(null, 'Mala', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":5001}]'::jsonb)$$,
  '22023', null, 'an amount over 5000 is rejected');
select throws_ok(
  $$select public.save_meal_template(null, 'Ajena', null,
    '[{"food_id":"bbbbbbbb-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  '42501', null, 'Bea''s personal food cannot be used');
select is((select count(*)::int from public.meal_templates where name = 'Ajena'), 0,
  'a failed save leaves no template behind');

select throws_ok(
  $$select public.save_meal_template(null, 'Parcial', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10},
      {"food_id":"bbbbbbbb-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  '42501', null, 'a bad second item fails the whole save');
select is((select count(*)::int from public.meal_templates where name = 'Parcial'), 0,
  'the template of a failed save is rolled back');

select lives_ok(
  $$select public.save_meal_template((select v from ids where k = 'desayuno_a'), 'Desayuno A', null,
    '[{"food_id":"aaaaaaaa-0000-4000-8000-000000000001","amount":200,"portion_label":null}]'::jsonb)$$,
  'overwriting an own template succeeds');
select results_eq(
  $$select food_id, amount from public.meal_template_items
    where template_id = (select v from ids where k = 'desayuno_a')$$,
  $$values ('aaaaaaaa-0000-4000-8000-000000000001'::uuid, 200.0::numeric)$$,
  'overwrite replaces the items');
select throws_ok(
  $$select public.save_meal_template((select v from ids where k = 'desayuno_a'), 'Desayuno A', null,
    '[{"food_id":"bbbbbbbb-0000-4000-8000-000000000001","amount":200}]'::jsonb)$$,
  '42501', null, 'overwrite with a bad item fails');
select is((select count(*)::int from public.meal_template_items
    where template_id = (select v from ids where k = 'desayuno_a')), 1,
  'a failed overwrite keeps the previous items (atomic)');

-- ---------------------------------------------------------------------------
-- As Bea: isolation
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select is((select count(*)::int from public.meal_templates), 0, 'Bea does not see Ana''s templates');
select is((select count(*)::int from public.meal_template_items), 0, 'Bea does not see Ana''s items');
select throws_ok(
  $$select public.save_meal_template((select v from ids where k = 'desayuno_a'), 'Robada', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10}]'::jsonb)$$,
  'P0002', null, 'Bea cannot overwrite Ana''s template');
select throws_ok(
  $$insert into public.meal_template_items (template_id, food_id, amount, position)
    values ((select v from ids where k = 'desayuno_a'), '0ff00000-0000-4000-8000-000000000001', 10, 5)$$,
  '42501', null, 'Bea cannot insert an item into Ana''s template');
select results_eq(
  $$with d as (delete from public.meal_templates returning 1) select count(*)::int from d$$,
  $$values (0)$$, 'Bea deleting templates affects 0 rows');
select lives_ok(
  $$select public.save_meal_template(null, 'Desayuno A', null,
    '[{"food_id":"0ff00000-0000-4000-8000-000000000001","amount":10},
      {"food_id":"bbbbbbbb-0000-4000-8000-000000000001","amount":50}]'::jsonb)$$,
  'Bea can reuse the name and reference an OFF food and her own personal food');

-- ---------------------------------------------------------------------------
-- Cascades and anon
-- ---------------------------------------------------------------------------
reset role;
set local role anon;
select throws_ok('select * from public.meal_templates', '42501', null, 'anon cannot read meal_templates');
reset role;

insert into public.meal_templates (id, user_id, name, meal_slot_id)
  select 'cccccccc-0000-4000-8000-000000000001', user_id, 'Con comida', id
  from public.meal_slots where code = 'dinner' and user_id = '11111111-1111-4111-8111-111111111111';
delete from public.meal_slots where code = 'dinner' and user_id = '11111111-1111-4111-8111-111111111111';
select is((select meal_slot_id from public.meal_templates where id = 'cccccccc-0000-4000-8000-000000000001'),
  null, 'deleting a meal keeps the template and clears its suggested meal');

delete from public.meal_templates where id = (select v from ids where k = 'desayuno_a');
select is((select count(*)::int from public.meal_template_items
  where template_id = (select v from ids where k = 'desayuno_a')), 0, 'deleting a template deletes its items');

-- The food FK is deferred to commit; force the check now to prove the final state is consistent.
select lives_ok($t$do $body$ begin
    delete from auth.users where id = '22222222-2222-4222-8222-222222222222';
    set constraints all immediate;
  end $body$$t$,
  'deleting a user with templates referencing their personal food succeeds');

select * from finish();
rollback;
