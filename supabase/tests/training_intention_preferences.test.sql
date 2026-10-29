-- pgTAP: training intention, place, equipment, days and session length on training_preferences
-- (T-149, US-65).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(19);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com');

insert into public.training_preferences
  (user_id, warm_up_included, bar_weight_kg, intention, place, equipment, days_per_week, session_minutes) values
  ('11111111-1111-4111-8111-111111111111', true, 15, null, null, '{}', null, null),
  ('22222222-2222-4222-8222-222222222222', false, 20, 'muscle', 'gym', '{barbell,machine}', 4, 60);

-- Enums
select enum_has_labels('public', 'training_intention',
  array['strength', 'muscle', 'fat_loss', 'recomposition', 'maintenance'], 'training_intention values');
select enum_has_labels('public', 'training_place', array['home', 'gym', 'other'], 'training_place values');

-- Defaults
select is(
  (select equipment from public.training_preferences where user_id = '11111111-1111-4111-8111-111111111111'),
  '{}'::public.equipment[], 'equipment defaults to an empty array');
select ok(
  (select intention is null and place is null and days_per_week is null and session_minutes is null
     from public.training_preferences where user_id = '11111111-1111-4111-8111-111111111111'),
  'intention, place, days and session length default to null');

-- Constraints (as owner role)
select throws_ok($$update public.training_preferences set days_per_week = 0
  where user_id = '11111111-1111-4111-8111-111111111111'$$, '23514', null, 'days_per_week 0 is rejected');
select throws_ok($$update public.training_preferences set days_per_week = 8
  where user_id = '11111111-1111-4111-8111-111111111111'$$, '23514', null, 'days_per_week 8 is rejected');
select throws_ok($$update public.training_preferences set session_minutes = 10
  where user_id = '11111111-1111-4111-8111-111111111111'$$, '23514', null, 'session_minutes 10 is rejected');
select throws_ok($$update public.training_preferences set session_minutes = 185
  where user_id = '11111111-1111-4111-8111-111111111111'$$, '23514', null, 'session_minutes 185 is rejected');
select throws_ok($$update public.training_preferences set session_minutes = 47
  where user_id = '11111111-1111-4111-8111-111111111111'$$, '23514', null, 'session_minutes 47 is rejected');
select lives_ok($$update public.training_preferences set days_per_week = 1, session_minutes = 15
  where user_id = '11111111-1111-4111-8111-111111111111'$$, 'lower boundaries 1 day and 15 min pass');
select lives_ok($$update public.training_preferences set days_per_week = 7, session_minutes = 180
  where user_id = '11111111-1111-4111-8111-111111111111'$$, 'upper boundaries 7 days and 180 min pass');
select lives_ok($$update public.training_preferences set days_per_week = null, session_minutes = null
  where user_id = '11111111-1111-4111-8111-111111111111'$$, 'null days and session length pass');

-- RLS isolation and the section upsert (as user A)
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.training_preferences where intention is not null), 0,
  'A cannot read B''s new columns');
select results_eq(
  $$with u as (update public.training_preferences set intention = 'strength'
               where user_id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$, 'A updating B''s intention affects 0 rows');

select lives_ok(
  $$insert into public.training_preferences (user_id, intention, place, equipment, days_per_week, session_minutes)
    values ('11111111-1111-4111-8111-111111111111', 'fat_loss', 'home', '{dumbbell,bodyweight}', 3, 45)
    on conflict (user_id) do update set intention = excluded.intention, place = excluded.place,
      equipment = excluded.equipment, days_per_week = excluded.days_per_week,
      session_minutes = excluded.session_minutes$$,
  'A upserts the five columns');
select ok(
  (select warm_up_included and bar_weight_kg = 15 and intention = 'fat_loss' and days_per_week = 3
     from public.training_preferences),
  'the upsert leaves warm-up and bar weight intact');

select lives_ok(
  $$select public.save_preferences('Ana', 'male', '1999-06-15', 180, 'moderate', 'deficit', -400, 2292,
      35::smallint, 42::smallint, 23::smallint)$$,
  'save_preferences still works');
select ok(
  (select intention = 'fat_loss' and place = 'home' and equipment = '{dumbbell,bodyweight}'
     and days_per_week = 3 and session_minutes = 45 from public.training_preferences),
  'save_preferences leaves the new columns intact');

reset role;
select ok(
  (select intention = 'muscle' and days_per_week = 4 and session_minutes = 60
     from public.training_preferences where user_id = '22222222-2222-4222-8222-222222222222'),
  'B''s new columns are unchanged after A''s attempts');

select * from finish();
rollback;
