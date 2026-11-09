-- pgTAP: public.food_energy_audit (T-251, US-117, ADR-102).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(14);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

-- Ana: two clearly wrong (gap -250 and +80), two mismatches (gap -68 and +28), one ok, one skipped, one flagged.
insert into public.foods (id, source, name, base_unit, kcal_100, protein_100, carbs_100, fat_100, micronutrients,
                          energy_check_saved_anyway, created_by) values
  ('a0000000-0000-4000-8000-000000000001', 'user', 'Z Epica', 'g', 500, 10, 30, 10, null, false, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000002', 'user', 'A Cero kcal', 'g', 0, 20, 0, 0, null, false, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000003', 'user', 'Crispbread', 'g', 350, 12, 45, 6, null, false, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000004', 'user', 'Pequeno', 'g', 100, 32, 0, 0, null, false, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000005', 'user', 'Bien', 'g', 100, 5, 10, 3, null, false, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000006', 'user', 'Cafe', 'ml', 2, 0.1, 0.3, 0, null, false, '11111111-1111-4111-8111-111111111111'),
  ('a0000000-0000-4000-8000-000000000007', 'user', 'Aceptado', 'g', 500, 10, 30, 10, null, true, '11111111-1111-4111-8111-111111111111'),
  ('b0000000-0000-4000-8000-000000000001', 'user', 'De Bea', 'g', 500, 10, 30, 10, null, false, '22222222-2222-4222-8222-222222222222');
insert into public.foods (id, source, barcode, name, kcal_100, protein_100, carbs_100, fat_100) values
  ('0ff00000-0000-4000-8000-000000000001', 'off', '1000000000001', 'Producto OFF', 500, 10, 30, 10);

create temp table snap (h text, n bigint);
insert into snap select md5(string_agg(to_jsonb(f)::text, '|' order by f.id)), count(*) from public.foods f;
grant all on snap to authenticated;

select ok(not has_function_privilege('anon', 'public.food_energy_audit()', 'execute'), 'anon cannot execute the audit');
select ok(has_function_privilege('authenticated', 'public.food_energy_audit()', 'execute'),
  'authenticated can execute the audit');
select ok(not (select prosecdef from pg_proc where proname = 'food_energy_audit'), 'the audit is security invoker');

set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq($$ select name, result from public.food_energy_audit() $$,
  $$ values ('Z Epica'::text, 'clearly_wrong'::text), ('A Cero kcal', 'clearly_wrong'),
            ('Crispbread', 'mismatch'), ('Pequeno', 'mismatch') $$,
  'clearly wrong first, then by gap size; ok, skipped, flagged, OFF and other users are excluded');
select results_eq($$ select kcal_100, computed, gap from public.food_energy_audit() where name = 'Crispbread' $$,
  $$ values (350::numeric, 282::numeric, -68::numeric) $$, 'rows carry declared kcal, computed and gap');
select is((select count(*) from public.food_energy_audit() where name = 'Aceptado'), 0::bigint,
  'a food saved anyway is not listed');
select is((select count(*) from public.food_energy_audit() where food_id = '0ff00000-0000-4000-8000-000000000001'), 0::bigint,
  'an OFF food is not listed');

reset role;
select is((select md5(string_agg(to_jsonb(f)::text, '|' order by f.id)) from public.foods f), (select h from snap),
  'the audit writes nothing (foods unchanged)');
select is((select count(*) from public.foods), (select n from snap), 'the audit adds and removes no rows');

-- A change of inputs clears the flag, so the food comes back
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
select public.update_personal_food('a0000000-0000-4000-8000-000000000007', 'Aceptado', null, 501, 10, 30, 10, '[]'::jsonb);
select is((select count(*) from public.food_energy_audit() where name = 'Aceptado'), 1::bigint,
  'a flagged food whose inputs changed and still fails is listed again');

-- Fixing a food removes it
select public.update_personal_food('a0000000-0000-4000-8000-000000000001', 'Z Epica', null, 250, 10, 30, 10, '[]'::jsonb);
select is((select count(*) from public.food_energy_audit() where name = 'Z Epica'), 0::bigint,
  'a corrected food leaves the list');

-- Cross-user isolation
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select results_eq($$ select name from public.food_energy_audit() $$, $$ values ('De Bea'::text) $$,
  'Bea only sees her own foods');

-- Unauthenticated
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok($$ select * from public.food_energy_audit() $$, '28000', 'not authenticated',
  'an unauthenticated call raises 28000');

reset role;
set local role anon;
select throws_ok($$ select * from public.food_energy_audit() $$, '42501', null, 'anon is denied');

select * from finish();
rollback;
