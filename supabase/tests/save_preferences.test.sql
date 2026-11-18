-- pgTAP: public.save_preferences atomicity, weight rule, security and isolation (T-041, US-06/07).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(50);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com'),
  ('44444444-4444-4444-8444-444444444444', 'dani@example.com');

-- A and B have a profile (sign-up trigger) and onboarding data; B also has goals and a weight today.
update public.profiles
set sex = 'female', birth_date = '1990-01-01', height_cm = 165, activity_level = 'light'
where id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222');
update public.profiles set display_name = 'Bea' where id = '22222222-2222-4222-8222-222222222222';
insert into public.nutrition_goals (user_id, target_kcal) values
  ('22222222-2222-4222-8222-222222222222', 2500);

-- A: a manual row on an earlier day and a Health Connect row today (both must survive the saves).
-- B: a manual row inside the very same day window A will send.
insert into public.body_measurements (user_id, measured_at, weight_kg, source, external_id) values
  ('11111111-1111-4111-8111-111111111111', '2026-10-18T09:00:00Z', 70, 'manual', null),
  ('11111111-1111-4111-8111-111111111111', '2026-10-19T10:00:00Z', 79, 'health_connect', 'hc-1'),
  ('22222222-2222-4222-8222-222222222222', '2026-10-19T08:00:00Z', 60, 'manual', null);

-- C loses the profile row to test the "no profile" error.
delete from public.profiles where id = '33333333-3333-4333-8333-333333333333';

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select has_function('public', 'save_preferences',
  array['text', 'sex', 'date', 'numeric', 'activity_level', 'goal_strategy', 'integer', 'integer',
        'smallint', 'smallint', 'smallint', 'numeric', 'timestamp with time zone',
        'timestamp with time zone', 'timestamp with time zone', 'boolean', 'previous_values_mode', 'numeric', 'numeric[]', 'numeric',
        'numeric', 'numeric', 'numeric', 'boolean'],
  'save_preferences exists');
select hasnt_function('public', 'save_preferences',
  array['text', 'sex', 'date', 'numeric', 'activity_level', 'goal_strategy', 'integer', 'integer',
        'smallint', 'smallint', 'smallint', 'numeric', 'timestamp with time zone',
        'timestamp with time zone', 'timestamp with time zone', 'boolean', 'previous_values_mode', 'numeric', 'numeric[]', 'numeric'],
  'the old 20-argument save_preferences is gone');
select is(
  (select prosecdef from pg_proc where oid = (
    select p.oid from pg_proc p where p.proname = 'save_preferences'
      and p.pronamespace = 'public'::regnamespace)),
  false, 'save_preferences is security invoker');
select ok(not has_function_privilege('anon',
  'public.save_preferences(text, public.sex, date, numeric, public.activity_level, public.goal_strategy, integer, integer, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean, public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric, boolean)',
  'execute'), 'anon cannot execute save_preferences');
select ok(has_function_privilege('authenticated',
  'public.save_preferences(text, public.sex, date, numeric, public.activity_level, public.goal_strategy, integer, integer, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean, public.previous_values_mode, numeric, numeric[], numeric, numeric, numeric, numeric, boolean)',
  'execute'), 'authenticated can execute save_preferences');
select hasnt_function('public', 'save_profile_settings', 'save_profile_settings was dropped');

-- ---------------------------------------------------------------------------
-- As user A, without weight
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select lives_ok(
  $$select public.save_preferences('  Ana López  ', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  'A saves without weight');
select results_eq(
  $$select display_name, sex::text, birth_date, height_cm, activity_level::text from public.profiles
    where id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('Ana López', 'male', date '1999-06-15', 180.0::numeric, 'moderate')$$,
  'A''s profile is updated and the name is trimmed');
select results_eq(
  $$select strategy::text, calorie_adjustment, target_kcal, protein_pct::int, carbs_pct::int, fat_pct::int
    from public.nutrition_goals where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('deficit', -400, 2292, 35, 42, 23)$$,
  'A''s goals row is created');
select is(
  (select count(*)::int from public.body_measurements where user_id = '11111111-1111-4111-8111-111111111111'),
  2, 'no weight arguments leave body_measurements untouched');

-- Second save updates the goals row (on conflict).
select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint)$$,
  'A saves again');
select results_eq(
  $$select strategy::text, target_kcal from public.nutrition_goals
    where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('surplus', 3000)$$,
  'the existing goals row is updated');

-- ---------------------------------------------------------------------------
-- Weight: insert, then same-day replace
-- ---------------------------------------------------------------------------
select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      80.5, '2026-10-19T12:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  'A saves with a weight');
select results_eq(
  $$select weight_kg, measured_at from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'
      and measured_at >= '2026-10-19T00:00:00Z'$$,
  $$values (80.5::numeric, timestamptz '2026-10-19T12:00:00Z')$$,
  'the weight is inserted as today''s manual row');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      81, '2026-10-19T15:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  'A saves a new weight the same day');
select results_eq(
  $$select weight_kg, measured_at from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'
      and measured_at >= '2026-10-19T00:00:00Z'$$,
  $$values (81.0::numeric, timestamptz '2026-10-19T15:00:00Z')$$,
  'the same-day manual row is replaced (still one row)');
select results_eq(
  $$select weight_kg from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'
      and measured_at < '2026-10-19T00:00:00Z'$$,
  $$values (70.0::numeric)$$,
  'an earlier day''s manual row is untouched');
select results_eq(
  $$select weight_kg, external_id from public.body_measurements
    where user_id = '11111111-1111-4111-8111-111111111111' and source = 'health_connect'$$,
  $$values (79.0::numeric, 'hc-1')$$,
  'a same-day Health Connect row is untouched');

-- Weight arguments are all-or-none.
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      82, null, null, null)$$,
  '22023', null, 'a weight without its timestamps is rejected');
select throws_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      82, '2026-10-19T15:00:00Z', '2026-10-20T00:00:00Z', '2026-10-19T00:00:00Z')$$,
  '22023', null, 'a day range that ends before it starts is rejected');

-- ---------------------------------------------------------------------------
-- Invalid input rolls everything back
-- ---------------------------------------------------------------------------
select throws_ok(
  $$select public.save_preferences('   ', 'female', '1985-01-01', 170, 'light', 'deficit', -200, 2000, 30::smallint, 45::smallint, 25::smallint)$$,
  '23514', null, 'a blank name is rejected');
select results_eq(
  $$select display_name, height_cm from public.profiles where id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('Ana', 181.0::numeric)$$,
  'the rejected name leaves the profile unchanged');
select throws_ok(
  $$select public.save_preferences('Nuevo', 'female', '1985-01-01', 170, 'light', 'deficit', -200, 2000, 30::smallint, 45::smallint, 25::smallint,
      10, '2026-10-19T16:00:00Z', '2026-10-19T00:00:00Z', '2026-10-20T00:00:00Z')$$,
  '23514', null, 'a weight of 10 kg is rejected');
select ok(
  (select display_name = 'Ana' and height_cm = 181 from public.profiles where id = '11111111-1111-4111-8111-111111111111')
  and (select target_kcal = 3000 from public.nutrition_goals where user_id = '11111111-1111-4111-8111-111111111111')
  and (select weight_kg = 81 from public.body_measurements
       where user_id = '11111111-1111-4111-8111-111111111111' and source = 'manual'
         and measured_at >= '2026-10-19T00:00:00Z'),
  'a rejected weight leaves the profile, the goals and the weight unchanged (atomic)');

-- ---------------------------------------------------------------------------
-- Added-fat percentages (T-107 / T-112, US-50, ADR-65)
-- ---------------------------------------------------------------------------
select is(
  (select count(*)::int from public.food_preferences where user_id = '11111111-1111-4111-8111-111111111111'),
  0, 'saves without the percentages create no food_preferences row');
select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      p_fat_pct_griddle => 1.5, p_fat_pct_air_fryer => 3, p_fat_pct_fried => 12)$$,
  'A saves the three percentages');
select results_eq(
  $$select fat_pct_griddle, fat_pct_air_fryer, fat_pct_fried from public.food_preferences
    where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values (1.5::numeric, 3.0::numeric, 12.0::numeric)$$,
  'the three percentages are stored');
select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      p_fat_pct_fried => 20)$$,
  'A saves only the fried percentage');
select results_eq(
  $$select fat_pct_griddle, fat_pct_air_fryer, fat_pct_fried from public.food_preferences
    where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values (1.5::numeric, 3.0::numeric, 20.0::numeric)$$,
  'null percentages stay unchanged');
select throws_ok(
  $$select public.save_preferences('Cambiada', 'male', '1999-06-15', 181, 'high', 'surplus', 300, 3000, 30::smallint, 45::smallint, 25::smallint,
      p_fat_pct_griddle => 20.1)$$,
  '23514', null, 'a percentage over 20 is rejected');
select ok(
  (select display_name = 'Ana' from public.profiles where id = '11111111-1111-4111-8111-111111111111')
  and (select fat_pct_griddle = 1.5 from public.food_preferences
       where user_id = '11111111-1111-4111-8111-111111111111'),
  'a rejected percentage leaves the profile and the percentages unchanged (atomic)');

-- ---------------------------------------------------------------------------
-- Goals are optional (T-303, US-138, ADR-108): saving without them never touches nutrition_goals
-- and never flags the accepted plan as edited_by_user.
-- ---------------------------------------------------------------------------
reset role;
insert into public.coach_plans (user_id, block_start, intention, nutrition, status, accepted_at) values
  ('11111111-1111-4111-8111-111111111111', current_date, 'fat_loss',
   '{"target_kcal": 2507, "edited_by_user": false}'::jsonb, 'accepted', now()),
  ('22222222-2222-4222-8222-222222222222', current_date, 'fat_loss',
   '{"target_kcal": 2507, "edited_by_user": false}'::jsonb, 'accepted', now());
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is(
  (select pronargdefaults::int from pg_proc
   where proname = 'save_preferences' and pronamespace = 'public'::regnamespace),
  19, 'the six goal parameters and the thirteen optional ones all have a default');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 182, 'high')$$,
  'A saves without goals');
select results_eq(
  $$select strategy::text, calorie_adjustment, target_kcal, protein_pct::int, carbs_pct::int, fat_pct::int
    from public.nutrition_goals where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('surplus', 300, 3000, 30, 45, 25)$$,
  'a save without goals leaves the stored goals unchanged');
select is(
  (select height_cm from public.profiles where id = '11111111-1111-4111-8111-111111111111'),
  182.0::numeric, 'a save without goals still updates the profile');
select is(
  (select (nutrition->>'edited_by_user')::boolean from public.coach_plans
   where user_id = '11111111-1111-4111-8111-111111111111' and status = 'accepted'),
  false, 'a save without goals does not flag the accepted plan as edited');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 182, 'high', 'deficit', -300, 2800, 30::smallint, 40::smallint, 30::smallint)$$,
  'A saves with the full goal set');
select results_eq(
  $$select strategy::text, calorie_adjustment, target_kcal, protein_pct::int, carbs_pct::int, fat_pct::int
    from public.nutrition_goals where user_id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('deficit', -300, 2800, 30, 40, 30)$$,
  'the full goal set is written');
select is(
  (select (nutrition->>'edited_by_user')::boolean from public.coach_plans
   where user_id = '11111111-1111-4111-8111-111111111111' and status = 'accepted'),
  true, 'writing goals still flags the accepted plan as edited');

select throws_ok(
  $$select public.save_preferences('Parcial', 'male', '1999-06-15', 190, 'high',
      p_strategy => 'surplus', p_target_kcal => 3500)$$,
  '22023', null, 'a partial goal set is rejected');
select ok(
  (select display_name = 'Ana' and height_cm = 182 from public.profiles
   where id = '11111111-1111-4111-8111-111111111111')
  and (select target_kcal = 2800 and strategy = 'deficit' from public.nutrition_goals
       where user_id = '11111111-1111-4111-8111-111111111111'),
  'a rejected partial goal set writes nothing');

-- D has no goals row: a save without goals does not create one.
select set_config('request.jwt.claims',
  '{"sub":"44444444-4444-4444-8444-444444444444","role":"authenticated"}', true);
select lives_ok(
  $$select public.save_preferences('Dani', 'male', '1999-06-15', 175, 'light')$$,
  'D saves without goals and without a goals row');
select is(
  (select count(*)::int from public.nutrition_goals where user_id = '44444444-4444-4444-8444-444444444444'),
  0, 'a save without goals creates no goals row');

-- B saves without goals: neither B's nor A's goals or plan flag change.
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);
select lives_ok(
  $$select public.save_preferences('Bea', 'female', '1990-01-01', 165, 'light')$$,
  'B saves without goals');
select ok(
  (select target_kcal = 2500 from public.nutrition_goals where user_id = '22222222-2222-4222-8222-222222222222')
  and (select (nutrition->>'edited_by_user')::boolean = false from public.coach_plans
       where user_id = '22222222-2222-4222-8222-222222222222' and status = 'accepted'),
  'B''s goals and plan flag are untouched by B''s save without goals');
reset role;
select ok(
  (select target_kcal = 2800 from public.nutrition_goals where user_id = '11111111-1111-4111-8111-111111111111')
  and (select height_cm = 182 from public.profiles where id = '11111111-1111-4111-8111-111111111111'),
  'A''s goals and profile are untouched by B''s save');
set local role authenticated;

-- ---------------------------------------------------------------------------
-- No profile row / no JWT
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims',
  '{"sub":"33333333-3333-4333-8333-333333333333","role":"authenticated"}', true);
select throws_ok(
  $$select public.save_preferences('Carl', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  'P0002', null, 'a caller without a profile row gets no_data_found');

select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
select throws_ok(
  $$select public.save_preferences('Nadie', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  'P0002', null, 'no auth.uid() updates nothing and raises');

-- ---------------------------------------------------------------------------
-- Isolation and anon
-- ---------------------------------------------------------------------------
reset role;
select ok(
  (select height_cm = 165 and sex = 'female' and display_name = 'Bea'
     from public.profiles where id = '22222222-2222-4222-8222-222222222222')
  and (select target_kcal = 2500 from public.nutrition_goals where user_id = '22222222-2222-4222-8222-222222222222')
  and (select count(*) = 1 and min(weight_kg) = 60 and min(measured_at) = '2026-10-19T08:00:00Z'
         from public.body_measurements where user_id = '22222222-2222-4222-8222-222222222222'),
  'B''s profile, goals and weight rows are untouched by A''s saves (same day window)');

set local role anon;
select throws_ok(
  $$select public.save_preferences('Anon', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292, 35::smallint, 42::smallint, 23::smallint)$$,
  '42501', null, 'anon cannot call save_preferences');
reset role;

select * from finish();
rollback;
