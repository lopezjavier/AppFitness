-- pgTAP: public.profiles schema, security, sign-up trigger, constraints and RLS isolation (T-018, US-02).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(65);

-- Test users (inserted as postgres; the on_auth_user_created trigger creates their profiles).
--   A: Google-style metadata with full_name/avatar_url
--   B: Google-style fallback keys name/picture only
--   C: no metadata (display_name falls back to the email local part); deleted to test the cascade
--   D: used for constraint and updated_at checks
insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-4111-8111-111111111111', 'ana@example.com',
   '{"full_name":"Ana","avatar_url":"https://example.com/a.png"}'),
  ('22222222-2222-4222-8222-222222222222', 'bea@example.com',
   '{"name":"Bea","picture":"https://example.com/b.png"}'),
  ('33333333-3333-4333-8333-333333333333', 'carl@example.com', '{}'),
  ('44444444-4444-4444-8444-444444444444', 'dan@example.com', '{"full_name":"Dan"}');

-- Metadata sanitization users (hostile or malformed raw_user_meta_data must never break sign-up).
insert into auth.users (id, email, raw_user_meta_data) values
  -- E: oversized name with surrounding whitespace
  ('a0000000-0000-4000-8000-00000000000e', 'eve@example.com',
   jsonb_build_object('full_name', '  ' || repeat('e', 100000) || '  ')),
  -- F: whitespace-only names
  ('a0000000-0000-4000-8000-00000000000f', 'fay@example.com',
   jsonb_build_object('full_name', E' \t ', 'name', '   ')),
  -- G: non-string values everywhere
  ('a0000000-0000-4000-8000-000000000001', 'gus@example.com',
   '{"full_name":123,"name":{"x":1},"avatar_url":true,"picture":["https://example.com/g.png"]}'),
  -- H: null metadata
  ('a0000000-0000-4000-8000-000000000002', 'hal@example.com', null),
  -- I: array metadata
  ('a0000000-0000-4000-8000-000000000003', 'ivy@example.com', '["full_name","Ivy"]'),
  -- J: javascript: avatar
  ('a0000000-0000-4000-8000-000000000004', 'jo@example.com',
   '{"full_name":"Jo","avatar_url":"javascript:alert(1)"}'),
  -- K: oversized https avatar, then an http fallback: both dropped
  ('a0000000-0000-4000-8000-000000000005', 'kim@example.com',
   jsonb_build_object('avatar_url', 'https://example.com/' || repeat('k', 2100),
                      'picture', 'http://example.com/k.png')),
  -- L: no email and no metadata (e.g. a phone sign-up)
  ('a0000000-0000-4000-8000-000000000006', null, null);

-- ---------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------
select has_extension('extensions', 'unaccent', 'unaccent is installed in the extensions schema');
select enum_has_labels('public', 'sex', array['male', 'female'], 'sex enum labels');
select enum_has_labels('public', 'activity_level',
  array['sedentary', 'light', 'moderate', 'high', 'very_high'], 'activity_level enum labels');
select has_table('public', 'profiles', 'public.profiles exists');
select columns_are('public', 'profiles', array[
  'id', 'display_name', 'avatar_url', 'birth_date', 'sex', 'height_cm', 'activity_level',
  'onboarding_completed', 'created_at', 'updated_at'
], 'profiles has exactly the planned columns');
select col_type_is('public', 'profiles', 'height_cm', 'numeric(5,1)', 'height_cm is numeric(5,1)');
select col_not_null('public', 'profiles', 'onboarding_completed', 'onboarding_completed is not null');
select col_default_is('public', 'profiles', 'onboarding_completed', 'false',
  'onboarding_completed defaults to false');
select fk_ok('public', 'profiles', 'id', 'auth', 'users', 'id', 'profiles.id references auth.users.id');

-- ---------------------------------------------------------------------------
-- Security
-- ---------------------------------------------------------------------------
select ok(
  (select relrowsecurity and relforcerowsecurity from pg_class where oid = 'public.profiles'::regclass),
  'RLS is enabled and forced on profiles'
);
select policies_are('public', 'profiles', array['profiles_select_own', 'profiles_update_own'],
  'profiles only has the select/update owner policies (no insert/delete)');
select policy_cmd_is('public', 'profiles', 'profiles_select_own', 'select', 'select policy command');
select policy_cmd_is('public', 'profiles', 'profiles_update_own', 'update', 'update policy command');
select policy_roles_are('public', 'profiles', 'profiles_select_own', array['authenticated'],
  'select policy applies to authenticated only');
select policy_roles_are('public', 'profiles', 'profiles_update_own', array['authenticated'],
  'update policy applies to authenticated only');
select table_privs_are('public', 'profiles', 'anon', array[]::text[], 'anon has no privileges on profiles');
select table_privs_are('public', 'profiles', 'authenticated', array['SELECT'],
  'authenticated only has table-level SELECT on profiles (UPDATE is column-level)');
select is(
  (select array_agg(a.attname::text order by a.attname::text)
   from pg_attribute a
   where a.attrelid = 'public.profiles'::regclass and a.attnum > 0 and not a.attisdropped
     and has_column_privilege('authenticated', a.attrelid, a.attnum, 'UPDATE')),
  array['activity_level', 'avatar_url', 'birth_date', 'display_name',
        'height_cm', 'onboarding_completed', 'sex'],
  'authenticated can only update the user-editable columns'
);
select has_trigger('auth', 'users', 'on_auth_user_created', 'sign-up trigger exists on auth.users');
select has_trigger('public', 'profiles', 'profiles_set_updated_at', 'updated_at trigger exists on profiles');
select is_definer('public', 'handle_new_user', array[]::name[], 'handle_new_user is security definer');
select isnt_definer('public', 'set_updated_at', array[]::name[], 'set_updated_at is security invoker');
select ok(
  (select 'search_path=""' = any(proconfig) from pg_proc where oid = 'public.handle_new_user()'::regprocedure),
  'handle_new_user has a fixed empty search_path'
);
select ok(
  (select 'search_path=""' = any(proconfig) from pg_proc where oid = 'public.set_updated_at()'::regprocedure),
  'set_updated_at has a fixed empty search_path'
);
select function_privs_are('public', 'handle_new_user', array[]::name[], 'anon', array[]::text[],
  'anon cannot execute handle_new_user');
select function_privs_are('public', 'handle_new_user', array[]::name[], 'authenticated', array[]::text[],
  'authenticated cannot execute handle_new_user');
select function_privs_are('public', 'set_updated_at', array[]::name[], 'anon', array[]::text[],
  'anon cannot execute set_updated_at');
select function_privs_are('public', 'set_updated_at', array[]::name[], 'authenticated', array[]::text[],
  'authenticated cannot execute set_updated_at');

-- ---------------------------------------------------------------------------
-- Sign-up trigger behavior (also proves FORCE RLS does not block the definer trigger)
-- ---------------------------------------------------------------------------
select results_eq(
  $$select display_name, avatar_url, onboarding_completed from public.profiles
    where id = '11111111-1111-4111-8111-111111111111'$$,
  $$values ('Ana'::text, 'https://example.com/a.png'::text, false)$$,
  'trigger copies full_name and avatar_url from the metadata'
);
select results_eq(
  $$select display_name, avatar_url from public.profiles where id = '22222222-2222-4222-8222-222222222222'$$,
  $$values ('Bea'::text, 'https://example.com/b.png'::text)$$,
  'trigger falls back to name and picture'
);
select results_eq(
  $$select display_name, avatar_url from public.profiles where id = '33333333-3333-4333-8333-333333333333'$$,
  $$values ('carl'::text, null::text)$$,
  'trigger falls back to the email local part and a null avatar'
);
select results_eq(
  $$select char_length(display_name), left(display_name, 1) from public.profiles
    where id = 'a0000000-0000-4000-8000-00000000000e'$$,
  $$values (100, 'e')$$,
  'an oversized name is trimmed and truncated to 100 characters'
);
select is(
  (select display_name from public.profiles where id = 'a0000000-0000-4000-8000-00000000000f'),
  'fay',
  'a whitespace-only name falls back to the email local part'
);
select results_eq(
  $$select display_name, avatar_url from public.profiles where id = 'a0000000-0000-4000-8000-000000000001'$$,
  $$values ('gus'::text, null::text)$$,
  'non-string metadata values are ignored'
);
select is(
  (select display_name from public.profiles where id = 'a0000000-0000-4000-8000-000000000002'),
  'hal',
  'null metadata still creates the profile'
);
select is(
  (select display_name from public.profiles where id = 'a0000000-0000-4000-8000-000000000003'),
  'ivy',
  'array metadata still creates the profile (keys are only read from objects)'
);
select results_eq(
  $$select display_name, avatar_url from public.profiles where id = 'a0000000-0000-4000-8000-000000000004'$$,
  $$values ('Jo'::text, null::text)$$,
  'a javascript: avatar is dropped'
);
select is(
  (select avatar_url from public.profiles where id = 'a0000000-0000-4000-8000-000000000005'),
  null,
  'avatars over 2048 characters or not https are dropped'
);
select is(
  (select display_name from public.profiles where id = 'a0000000-0000-4000-8000-000000000006'),
  'Usuario',
  'no email and no metadata falls back to a generic name'
);
delete from auth.users where id = '33333333-3333-4333-8333-333333333333';
select is(
  (select count(*)::int from public.profiles where id = '33333333-3333-4333-8333-333333333333'),
  0,
  'deleting the auth user cascades to its profile'
);

-- ---------------------------------------------------------------------------
-- Constraints and updated_at (as postgres)
-- ---------------------------------------------------------------------------
select throws_ok(
  $$update public.profiles set height_cm = 99.9 where id = '44444444-4444-4444-8444-444444444444'$$,
  '23514', null, 'height_cm below 100 is rejected'
);
select throws_ok(
  $$update public.profiles set height_cm = 250.1 where id = '44444444-4444-4444-8444-444444444444'$$,
  '23514', null, 'height_cm above 250 is rejected'
);
select lives_ok(
  $$update public.profiles set height_cm = 100.0 where id = '44444444-4444-4444-8444-444444444444'$$,
  'height_cm = 100 is accepted (inclusive lower bound)'
);
select lives_ok(
  $$update public.profiles set height_cm = 250.0 where id = '44444444-4444-4444-8444-444444444444'$$,
  'height_cm = 250 is accepted (inclusive upper bound)'
);
select lives_ok(
  $$update public.profiles set height_cm = null where id = '44444444-4444-4444-8444-444444444444'$$,
  'height_cm can be null'
);
update public.profiles set display_name = 'x', updated_at = '2000-01-01'
  where id = '44444444-4444-4444-8444-444444444444';
select is(
  (select updated_at from public.profiles where id = '44444444-4444-4444-8444-444444444444'),
  now(),
  'the trigger sets updated_at on update, overriding the supplied value'
);

-- ---------------------------------------------------------------------------
-- RLS isolation (as user A)
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}', true);

select results_eq(
  'select id from public.profiles',
  $$values ('11111111-1111-4111-8111-111111111111'::uuid)$$,
  'A only sees their own profile'
);
select is(
  (select count(*)::int from public.profiles where id = '22222222-2222-4222-8222-222222222222'),
  0,
  'A cannot read B''s profile'
);
select results_eq(
  $$with u as (update public.profiles set height_cm = 178
               where id = '11111111-1111-4111-8111-111111111111' returning 1)
    select count(*)::int from u$$,
  $$values (1)$$,
  'A can update their own profile'
);
select results_eq(
  $$with u as (update public.profiles set display_name = 'hacked'
               where id = '22222222-2222-4222-8222-222222222222' returning 1)
    select count(*)::int from u$$,
  $$values (0)$$,
  'A updating B''s profile affects 0 rows'
);
select throws_ok(
  $$update public.profiles set id = '55555555-5555-4555-8555-555555555555'
    where id = '11111111-1111-4111-8111-111111111111'$$,
  '42501', null, 'A cannot change their profile id (no column privilege)'
);
select throws_ok(
  $$update public.profiles set created_at = '2000-01-01' where id = '11111111-1111-4111-8111-111111111111'$$,
  '42501', null, 'A cannot update created_at (no column privilege)'
);
select throws_ok(
  $$update public.profiles set display_name = repeat('x', 101) where id = '11111111-1111-4111-8111-111111111111'$$,
  '23514', null, 'A cannot set a display_name over 100 characters'
);
select throws_ok(
  $$update public.profiles set display_name = '   ' where id = '11111111-1111-4111-8111-111111111111'$$,
  '23514', null, 'A cannot set a blank display_name'
);
select throws_ok(
  $$update public.profiles set avatar_url = 'javascript:alert(1)' where id = '11111111-1111-4111-8111-111111111111'$$,
  '23514', null, 'A cannot set a javascript: avatar_url'
);
select throws_ok(
  $$update public.profiles set avatar_url = 'http://example.com/a.png' where id = '11111111-1111-4111-8111-111111111111'$$,
  '23514', null, 'A cannot set a non-https avatar_url'
);
select throws_ok(
  $$update public.profiles set avatar_url = 'https://example.com/' || repeat('a', 2029) where id = '11111111-1111-4111-8111-111111111111'$$,
  '23514', null, 'A cannot set an avatar_url over 2048 characters'
);
select lives_ok(
  $$update public.profiles set avatar_url = null where id = '11111111-1111-4111-8111-111111111111'$$,
  'A can clear their avatar_url (nullable)'
);
select throws_ok(
  $$insert into public.profiles (id) values ('66666666-6666-4666-8666-666666666666')$$,
  '42501', null, 'A cannot insert profiles'
);
select throws_ok(
  $$delete from public.profiles where id = '11111111-1111-4111-8111-111111111111'$$,
  '42501', null, 'A cannot delete profiles'
);
select throws_ok(
  'truncate public.profiles',
  '42501', null, 'A cannot truncate profiles'
);

-- authenticated without JWT claims: auth.uid() is null, so no row matches.
select set_config('request.jwt.claims', '', true);
select is(
  (select count(*)::int from public.profiles),
  0,
  'authenticated with no JWT claims sees no profiles'
);

reset role;
select is(
  (select display_name from public.profiles where id = '22222222-2222-4222-8222-222222222222'),
  'Bea',
  'B''s profile is unchanged after A''s update attempt'
);
select is(
  (select count(*)::int from public.profiles where id = '11111111-1111-4111-8111-111111111111'),
  1,
  'A''s profile still exists after the delete attempt'
);

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
set local role anon;
select throws_ok(
  'select * from public.profiles',
  '42501', null, 'anon cannot read profiles'
);
reset role;

select * from finish();
rollback;
