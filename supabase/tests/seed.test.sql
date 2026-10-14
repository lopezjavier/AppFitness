-- pgTAP: the local-only seeded test user from supabase/seed.sql (T-018, US-02).
-- Coupled to seed.sql on purpose: if the seed user changes, update this file and
-- planning/08-environment-setup.md section 3.2.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(6);

select ok(
  exists (
    select 1 from auth.users
    where id = '00000000-0000-4000-8000-000000000001'
      and email = 'test@appfitness.local'
      and email_confirmed_at is not null
  ),
  'the seed user exists with its fixed id and a confirmed email'
);
select ok(
  exists (
    select 1 from auth.identities
    where user_id = '00000000-0000-4000-8000-000000000001'
      and provider = 'email'
      and provider_id = '00000000-0000-4000-8000-000000000001'
  ),
  'the seed user has an email identity'
);
select is(
  (select display_name from public.profiles where id = '00000000-0000-4000-8000-000000000001'),
  'Usuario de prueba',
  'the seed user profile was created by the sign-up trigger'
);
select ok(
  (select encrypted_password = extensions.crypt('appfitness-local-test', encrypted_password)
   from auth.users where id = '00000000-0000-4000-8000-000000000001'),
  'the documented local password matches the seed user'
);

select results_eq(
  $$ select onboarding_completed, sex::text, birth_date, height_cm::text, activity_level::text
     from public.profiles where id = '00000000-0000-4000-8000-000000000001' $$,
  $$ values (true, 'male', date '1999-06-15', '178.0', 'moderate') $$,
  'the seed user profile is already onboarded'
);

select results_eq(
  $$ select source::text, weight_kg::text from public.body_measurements
     where user_id = '00000000-0000-4000-8000-000000000001' $$,
  $$ values ('manual', '74.20') $$,
  'the seed user has exactly one manual weight row of 74.20 kg'
);

select * from finish();
rollback;
