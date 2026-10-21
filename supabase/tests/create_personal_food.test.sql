-- pgTAP: public.create_personal_food atomic RPC (T-058, US-18, US-22).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(20);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
select ok(
  has_function_privilege('authenticated',
    'public.create_personal_food(text,text,public.food_base_unit,numeric,numeric,numeric,numeric,text,jsonb,jsonb)',
    'execute'),
  'authenticated can execute create_personal_food');
select ok(
  not has_function_privilege('anon',
    'public.create_personal_food(text,text,public.food_base_unit,numeric,numeric,numeric,numeric,text,jsonb,jsonb)',
    'execute'),
  'anon cannot execute create_personal_food');
select ok(
  not (select prosecdef from pg_proc where proname = 'create_personal_food'),
  'create_personal_food is security invoker');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

create temp table result (id uuid);
grant all on result to authenticated;

insert into result
select public.create_personal_food(
  'Tortilla de patatas casera', 'Casa', 'g', 190, 7, 15, 11, '8412345678905',
  '[{"label":"1 ración","amount":250},{"label":"1 trozo","amount":80.5}]'::jsonb);

select is((select count(*)::int from result), 1, 'the RPC returns the new food id');
select results_eq(
  $$select source::text, created_by, name, brand, base_unit::text, kcal_100, protein_100, carbs_100, fat_100, barcode
    from public.foods where id = (select id from result)$$,
  $$values ('user', '11111111-1111-4111-8111-111111111111'::uuid, 'Tortilla de patatas casera', 'Casa', 'g',
    190::numeric, 7::numeric, 15::numeric, 11::numeric, '8412345678905')$$,
  'food is stored as a user food owned by the caller with its values and barcode');
select results_eq(
  $$select label, amount, position::int from public.food_portions
    where food_id = (select id from result) order by position$$,
  $$values ('1 ración', 250.0::numeric, 0), ('1 trozo', 80.5::numeric, 1)$$,
  'portions are stored in array order with positions 0..n-1');

select lives_ok(
  $$select public.create_personal_food('Agua con gas', null, 'ml', 0, 0, 0, 0, null, '[]'::jsonb)$$,
  'an empty portions array works with zero kcal');
select lives_ok(
  $$select public.create_personal_food('Pan', null, 'g', 250, 8, 49, 3, null, null)$$,
  'null portions work');

-- Validation (each leaves no row behind)
select throws_ok(
  $$select public.create_personal_food('   ', null, 'g', 100, 0, 0, 0, null, '[]'::jsonb)$$,
  '22023', null, 'a blank name is rejected');
select throws_ok(
  $$select public.create_personal_food('Negativo', null, 'g', -1, 0, 0, 0, null, '[]'::jsonb)$$,
  '22023', null, 'a negative kcal is rejected');
select throws_ok(
  $$select public.create_personal_food('Cero', null, 'g', 100, 0, 0, 0, null, '[{"label":"x","amount":0}]'::jsonb)$$,
  '22023', null, 'a zero portion amount is rejected');
select throws_ok(
  $$select public.create_personal_food('Etiqueta', null, 'g', 100, 0, 0, 0, null, '[{"label":"  ","amount":10}]'::jsonb)$$,
  '22023', null, 'a blank portion label is rejected');
select throws_ok(
  $$select public.create_personal_food('Muchas', null, 'g', 100, 0, 0, 0, null,
    (select jsonb_agg(jsonb_build_object('label', 'p' || i, 'amount', 10)) from generate_series(1, 11) i))$$,
  '22023', null, 'more than 10 portions are rejected');
select throws_ok(
  $$select public.create_personal_food('Atomica', null, 'g', 100, 0, 0, 0, null,
    '[{"label":"ok","amount":10},{"label":"mala","amount":-5}]'::jsonb)$$,
  '22023', null, 'a bad second portion is rejected');
select is(
  (select count(*)::int from public.foods where name in ('   ', 'Negativo', 'Cero', 'Etiqueta', 'Muchas', 'Atomica')),
  0, 'rejected calls leave no food row (atomic)');

-- ---------------------------------------------------------------------------
-- As user B
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

select is((select count(*)::int from public.foods where name = 'Tortilla de patatas casera'), 0,
  'B cannot read A''s created food');
select is((select count(*)::int from public.food_portions), 0, 'B cannot read A''s portions');

-- ---------------------------------------------------------------------------
-- Unauthenticated and anon
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.create_personal_food('Sin sesion', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb)$$,
  '28000', null, 'a caller without a user id is rejected');

reset role;
set local role anon;
select throws_ok(
  $$select public.create_personal_food('Anon', null, 'g', 1, 0, 0, 0, null, '[]'::jsonb)$$,
  '42501', null, 'anon is denied');
reset role;

-- ---------------------------------------------------------------------------
-- Cascade (ADR-29)
-- ---------------------------------------------------------------------------
delete from auth.users where id = '11111111-1111-4111-8111-111111111111';
select is((select count(*)::int from public.foods where created_by is not null), 0,
  'deleting the user cascades their created foods');

select * from finish();
rollback;
