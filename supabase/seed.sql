-- Local-only seed data (applied by `supabase db reset`; never in production:
-- deploy-supabase runs `supabase db push` without --include-seed).
--
-- Test user for local development, E2 "Test login" and Maestro:
--   email:    test@appfitness.local
--   password: appfitness-local-test   (local-only, not a secret; see planning/08-environment-setup.md section 3.2)
--   id:       00000000-0000-4000-8000-000000000001 (fixed)
-- Its public.profiles row is created by the on_auth_user_created trigger, not inserted here.
-- If anything here changes, update supabase/tests/seed.test.sql as well.

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  -- GoTrue fails password login when these are NULL.
  confirmation_token, recovery_token, email_change_token_new, email_change
) values (
  '00000000-0000-0000-0000-000000000000',
  '00000000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'test@appfitness.local',
  extensions.crypt('appfitness-local-test', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"full_name":"Usuario de prueba"}',
  now(),
  now(),
  '', '', '', ''
)
on conflict do nothing;

insert into auth.identities (
  provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at
) values (
  '00000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000001',
  '{"sub":"00000000-0000-4000-8000-000000000001","email":"test@appfitness.local","email_verified":true}',
  'email',
  now(),
  now(),
  now()
)
on conflict do nothing;
