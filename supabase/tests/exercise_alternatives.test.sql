-- pgTAP: band_long equipment, the 30 band-long catalogue rows and exercise_alternatives (T-179, ADR-83).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(31);

create function pg_temp.id(p_key text) returns uuid language sql as $$ select id from public.exercises where key = p_key $$;
grant execute on all functions in schema pg_temp to authenticated;

insert into auth.users (id, email) values
  ('a9000000-0000-4000-8000-000000000001', 'alt-a@example.com'),
  ('a9000000-0000-4000-8000-000000000002', 'alt-b@example.com');

-- ---------------------------------------------------------------------------
-- Enum and catalogue
-- ---------------------------------------------------------------------------
select ok(exists (select 1 from pg_enum where enumtypid = 'public.equipment'::regtype and enumlabel = 'band_long'),
  'equipment has band_long');
select is((select count(*)::int from public.exercises where key like 'band-long-%' and equipment = 'band_long' and not is_custom),
  30, '30 band-long catalogue rows exist');
select is((select count(*)::int from public.exercises where equipment = 'band_long'), 30, 'only the 30 rows use band_long');
select is((select count(*)::int from public.exercises l join public.exercises c on c.key = substr(l.key, 11)
           where l.equipment = 'band_long' and l.muscle_group = c.muscle_group and l.metric_type = c.metric_type
             and l.movement_pattern is not distinct from c.movement_pattern and not l.per_hand and l.name is null),
  30, 'each band-long row copies muscle group, metric and pattern from its source, per_hand false, no name');
select is((select count(*)::int from public.exercises l join public.exercises c on c.key = substr(l.key, 11)
           where l.equipment = 'band_long' and c.equipment = 'cable'), 30, 'every source is a cable exercise that is still in the catalogue');

-- ---------------------------------------------------------------------------
-- Structure and seed
-- ---------------------------------------------------------------------------
select is((select count(*)::int from public.exercise_alternatives), 473, 'the seed holds 473 unordered pairs (402 + 71 barbell / EZ bar / dumbbell, T-201)');
select is((select count(*)::int from public.exercise_alternatives where exercise_id >= alternative_id), 0, 'every pair is ordered');
select is((select count(*)::int from public.exercises l join public.exercises c on c.key = substr(l.key, 11)
           join public.exercise_alternatives a on (a.exercise_id = least(l.id, c.id) and a.alternative_id = greatest(l.id, c.id))
           where l.equipment = 'band_long' and a.similarity = 1.00), 30, 'each cable / band-long pair is at 1.00');
select ok((select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.exercise_alternatives'::regclass),
  'RLS is enabled and forced');
select ok(not has_table_privilege('anon', 'public.exercise_alternatives', 'select'), 'anon cannot read');
select ok(has_table_privilege('authenticated', 'public.exercise_alternatives', 'select'), 'authenticated can read');
select ok(not has_table_privilege('authenticated', 'public.exercise_alternatives', 'insert')
      and not has_table_privilege('authenticated', 'public.exercise_alternatives', 'update')
      and not has_table_privilege('authenticated', 'public.exercise_alternatives', 'delete'),
  'authenticated cannot write');
select ok(not has_function_privilege('anon', 'public.exercise_alternatives(uuid)', 'execute'), 'anon cannot call the RPC');
select ok(exists (select 1 from pg_indexes where tablename = 'exercise_alternatives' and indexdef ilike '%(alternative_id)%'),
  'alternative_id is indexed');

-- Constraints (as owner).
insert into public.exercises (id, name, muscle_group, equipment, is_custom, created_by) values
  ('a9000000-0000-4000-8000-0000000000e1', 'Prueba 1', 'back', 'cable', true, 'a9000000-0000-4000-8000-000000000001'),
  ('a9000000-0000-4000-8000-0000000000e2', 'Prueba 2', 'back', 'cable', true, 'a9000000-0000-4000-8000-000000000001');
select throws_ok($$insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e1', 'a9000000-0000-4000-8000-0000000000e1', 0.5)$$, '23514', null, 'a self pair is rejected');
select throws_ok($$insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e2', 'a9000000-0000-4000-8000-0000000000e1', 0.5)$$, '23514', null, 'a reversed pair is rejected');
select throws_ok($$insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e1', 'a9000000-0000-4000-8000-0000000000e2', 0)$$, '23514', null, 'similarity 0 is rejected');
select throws_ok($$insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e1', 'a9000000-0000-4000-8000-0000000000e2', 1.01)$$, '23514', null, 'similarity above 1 is rejected');
select throws_ok($$insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e1', 'ffffffff-ffff-4fff-8fff-ffffffffffff', 0.95)$$, '23503', null, 'an unknown exercise is rejected');
insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e1', 'a9000000-0000-4000-8000-0000000000e2', 0.95);
select lives_ok($$delete from public.exercises where id = 'a9000000-0000-4000-8000-0000000000e2'$$, 'deleting an exercise works');
select is((select count(*)::int from public.exercise_alternatives where alternative_id = 'a9000000-0000-4000-8000-0000000000e2'),
  0, 'deleting an exercise cascades to its pairs');

-- ---------------------------------------------------------------------------
-- RPC and isolation
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', 'a9000000-0000-4000-8000-000000000001', 'role', 'authenticated')::text, true);

select throws_ok($$insert into public.exercise_alternatives values
  ('a9000000-0000-4000-8000-0000000000e1', 'a9000000-0000-4000-8000-0000000000e1', 0.5)$$, '42501', null,
  'authenticated cannot insert');
select is((select count(*)::int from public.exercise_alternatives), 473, 'authenticated reads the whole catalogue graph');

select is((select similarity from public.exercise_alternatives(pg_temp.id('cable-fly')) where exercise_id = pg_temp.id('band-long-cable-fly')),
  1.00, 'a cable exercise lists its band-long counterpart at 1.00');
select is((select similarity from public.exercise_alternatives(pg_temp.id('band-long-cable-fly')) where exercise_id = pg_temp.id('cable-fly')),
  1.00, 'the reverse direction is returned too');
select is((select array_agg(similarity) from public.exercise_alternatives(pg_temp.id('db-fly'))),
  (select array_agg(similarity order by similarity desc) from public.exercise_alternatives(pg_temp.id('db-fly'))),
  'rows come most similar first');
select ok((select count(*) from public.exercise_alternatives(pg_temp.id('db-fly'))) >= 5, 'a mid exercise has its alternatives');
select ok(not exists (select 1 from public.exercise_alternatives(pg_temp.id('db-fly')) where exercise_id = pg_temp.id('db-fly')),
  'an exercise is never its own alternative');
select is((select count(*)::int from public.exercise_alternatives(gen_random_uuid())), 0, 'an unknown id returns nothing');

select is((select count(*)::int from public.exercise_alternatives(pg_temp.id('cable-fly'))),
  (select count(*)::int from public.exercise_alternatives a where pg_temp.id('cable-fly') in (a.exercise_id, a.alternative_id)),
  'the RPC returns every pair of the exercise once');

reset role;
set local role anon;
select throws_ok($$select * from public.exercise_alternatives(gen_random_uuid())$$, '42501', null, 'anon cannot execute the RPC');

select * from finish();
rollback;
