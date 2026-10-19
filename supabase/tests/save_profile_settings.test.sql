-- pgTAP: public.save_profile_settings atomicity, security and isolation (T-037, US-06/07/08).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(17);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com');

-- A and B have a profile (sign-up trigger) and onboarding data; B also has a goals row.
update public.profiles
set sex = 'female', birth_date = '1990-01-01', height_cm = 165, activity_level = 'light'
where id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222');
insert into public.nutrition_goals (user_id, target_kcal) values
  ('22222222-2222-4222-8222-222222222222', 2500);

-- C loses the profile row to test the "no profile" error.
delete from public.profiles where id = '33333333-3333-4333-8333-333333333333';

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select has_function('public', 'save_profile_settings',
  array['sex', 'date', 'numeric', 'activity_level', 'goal_strategy', 'integer', 'integer',
        'smallint', 'smallint', 'smallint'],
  'save_profile_settings exists');
select is(
  (select prosecdef from pg_proc where oid = (
    select p.oid from pg_proc p where p.proname = 'save_profile_settings'
      and p.pronamespace = 'public'::regnamespace)),
  false, 'save_profile_settings is security invoker');
select ok(not has_function_privilege('anon',
  'public.save_profile_settings(public.sex, date, numeric, public.activity_level, public.goal_strategy, integer, integer, smallint, smallint, smallint)',
  'execute'), 'anon cannot execute save_profile_settings');
select ok(has_function_privilege('authenticated',
  'public.save_profile_settings(public.sex, date, numeric, public.activity_level, public.goal_strategy, integer, integer, smallint, smallint, smallint)',
  'execute'), 'authenticated can execute save_profile_settings');

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

-- Upsert creates A's goals row (none yet) and updates the profile.
select lives_ok(
  $$select public.save_profile_settings('male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  'A saves settings');
select results_eq(
  $$select sex::text, birth_date, height_cm, activity_level::text from public.profiles
    where id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('male', date '1999-06-15', 180.0::numeric, 'moderate')$$,
  'A''s profile is updated');
select results_eq(
  $$select strategy::text, calorie_adjustment, target_kcal, protein_pct::int, carbs_pct::int, fat_pct::int
    from public.nutrition_goals where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('deficit', -400, 2292, 35, 42, 23)$$,
  'A''s goals row is created');

-- Second save updates the existing row (on conflict) instead of failing.
select lives_ok(
  $$select public.save_profile_settings('male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint)$$,
  'A saves again');
select results_eq(
  $$select strategy::text, target_kcal from public.nutrition_goals
    where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('surplus', 3000)$$,
  'the existing goals row is updated');

-- Invalid input: nothing changes (profile written first, then goals fail -> both roll back).
select throws_ok(
  $$select public.save_profile_settings('female', '1985-01-01', 170, 'light', 'deficit', -200, 2000, 30::smallint, 30::smallint, 30::smallint)$$,
  '23514', null, 'a split that does not add up to 100 is rejected');
select results_eq(
  $$select sex::text, height_cm from public.profiles where id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('male', 181.0::numeric)$$,
  'rejected goals leave the profile unchanged (atomic)');
select throws_ok(
  $$select public.save_profile_settings('female', '1985-01-01', 260, 'light', 'deficit', -200, 2000, 30::smallint, 45::smallint, 25::smallint)$$,
  '23514', null, 'height 260 is rejected');
select results_eq(
  $$select strategy::text, target_kcal from public.nutrition_goals
    where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('surplus', 3000)$$,
  'rejected profile leaves the goals unchanged');

-- ---------------------------------------------------------------------------
-- No profile row / no JWT
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}', true);
select throws_ok(
  $$select public.save_profile_settings('male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  'P0002', null, 'a caller without a profile row gets no_data_found');

select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.save_profile_settings('male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  'P0002', null, 'no auth.uid() updates nothing and raises');

-- ---------------------------------------------------------------------------
-- Isolation and anon
-- ---------------------------------------------------------------------------
reset role;
select ok(
  (select height_cm = 165 and sex = 'female' from public.profiles where id = '22222222-2222-4222-8222-222222222222')
  and (select target_kcal = 2500 from public.nutrition_goals where user_id = '22222222-2222-4222-8222-222222222222'),
  'B''s profile and goals are untouched by A''s saves');

set local role anon;
select throws_ok(
  $$select public.save_profile_settings('male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  '42501', null, 'anon cannot call save_profile_settings');
reset role;

select * from finish();
rollback;
