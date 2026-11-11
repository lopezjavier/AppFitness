-- pgTAP: public.food_energy_check (T-251, US-116, ADR-102; rule in 07 section 4).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(27);

-- Privileges
select ok(has_function_privilege('authenticated',
  'public.food_energy_check(numeric,numeric,numeric,numeric,numeric,numeric,numeric)', 'execute'),
  'authenticated can execute food_energy_check');
select ok(not has_function_privilege('anon',
  'public.food_energy_check(numeric,numeric,numeric,numeric,numeric,numeric,numeric)', 'execute'),
  'anon cannot execute food_energy_check');
select ok((select provolatile = 'i' and not prosecdef from pg_proc where proname = 'food_energy_check'),
  'food_energy_check is immutable and security invoker');

-- Worked examples of 07 section 4 (per 100 g)
select results_eq($$ select result, computed, gap from public.food_energy_check(500, 10, 30, 10, null, null, null) $$,
  $$ values ('clearly_wrong'::text, 250::numeric, -250::numeric) $$, 'epic case is clearly wrong, computed 250');
select results_eq($$ select result, computed from public.food_energy_check(100, 5, 10, 3, null, null, null) $$,
  $$ values ('ok'::text, 87::numeric) $$, 'small gap is ok');
select results_eq($$ select result, computed from public.food_energy_check(350, 12, 45, 6, 30, null, null) $$,
  $$ values ('ok'::text, 342::numeric) $$, 'crispbread with fibre is ok');
select results_eq($$ select result, computed from public.food_energy_check(350, 12, 45, 6, null, null, null) $$,
  $$ values ('mismatch'::text, 282::numeric) $$, 'crispbread without fibre is a mismatch (gap 68 > 57.5)');
select results_eq($$ select result, computed from public.food_energy_check(240, 0, 95, 0, null, 90, null) $$,
  $$ values ('ok'::text, 236::numeric) $$, 'sugar-free sweets with polyols are ok');
select results_eq($$ select result, computed from public.food_energy_check(240, 0, 95, 0, null, null, null) $$,
  $$ values ('clearly_wrong'::text, 380::numeric) $$, 'sugar-free sweets without polyols are clearly wrong');
select is((select result from public.food_energy_check(0, 20, 0, 0, null, null, null)), 'clearly_wrong',
  '0 kcal with 20 g protein is clearly wrong');
select is((select result from public.food_energy_check(2, 0.1, 0.3, 0, null, null, null)), 'skipped',
  'black coffee is skipped');
select results_eq($$ select result, computed from public.food_energy_check(83, 0.1, 2.6, 0, null, null, 9.5) $$,
  $$ values ('ok'::text, 77.3::numeric) $$, 'red wine with alcohol is ok');
select is((select result from public.food_energy_check(83, 0.1, 2.6, 0, null, null, null)), 'clearly_wrong',
  'red wine without alcohol is clearly wrong');

-- Boundaries: kcal 100 -> mismatch limit 20, clearly wrong limit 35 (computed = 4 * protein)
select is((select result from public.food_energy_check(100, 30, 0, 0, null, null, null)), 'ok',
  '|gap| exactly 0.15 * kcal + 5 is ok');
select is((select result from public.food_energy_check(100, 30.0025, 0, 0, null, null, null)), 'mismatch',
  '|gap| just above 0.15 * kcal + 5 is a mismatch');
select is((select result from public.food_energy_check(100, 33.75, 0, 0, null, null, null)), 'mismatch',
  '|gap| exactly 0.30 * kcal + 5 is still a mismatch');
select is((select result from public.food_energy_check(100, 33.7525, 0, 0, null, null, null)), 'clearly_wrong',
  '|gap| just above 0.30 * kcal + 5 is clearly wrong');
select is((select result from public.food_energy_check(100, 17.5, 0, 0, null, null, null)), 'mismatch',
  'a gap below the declared kcal also counts (computed 70, gap -30)');
select is((select result from public.food_energy_check(13, 3.25, 0, 0, null, null, null)), 'skipped',
  'kcal 13 and computed 13 is skipped');
select isnt((select result from public.food_energy_check(13, 3.26, 0, 0, null, null, null)), 'skipped',
  'computed just above 13 is not skipped');
select isnt((select result from public.food_energy_check(14, 0, 0, 0, null, null, null)), 'skipped',
  'kcal above 13 is not skipped even when computed is 0');
select is((select result from public.food_energy_check(0, 0, 0, 0, null, null, null)), 'skipped',
  'all zero is skipped');

-- Nulls count as 0; polyols above carbs are capped at carbs
select is((select computed from public.food_energy_check(100, 5, 10, 3, null, null, null)),
  (select computed from public.food_energy_check(100, 5, 10, 3, 0, 0, 0)), 'null fibre, polyols and alcohol count as 0');
select is((select computed from public.food_energy_check(100, 0, 10, 0, null, 50, null)), 24::numeric,
  'polyols above carbs are capped at carbs (10 x 2.4)');
select is((select gap from public.food_energy_check(100, 0, 0, 0, 5, null, null)), -90::numeric,
  'fibre counts 2 kcal/g and gap is computed minus kcal');
select is((select computed from public.food_energy_check(100, 0, 0, 0, null, null, 10)), 70::numeric,
  'alcohol counts 7 kcal/g');
select is((select computed from public.food_energy_check(100, 1.111, 0, 0, null, null, null)), 4.444::numeric,
  'computed is returned unrounded');

select * from finish();
rollback;
