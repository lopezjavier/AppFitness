-- pgTAP: profiles.avatar_url follows the Google photo on auth.users metadata updates (T-134, US-06).
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(13);

-- Inserted as postgres; the on_auth_user_created trigger creates the profiles.
insert into auth.users (id, email, raw_user_meta_data) values
  ('b0000000-0000-4000-8000-000000000001', 'old@example.com',
   '{"full_name":"Old Name","avatar_url":"https://example.com/old.png"}'),
  ('b0000000-0000-4000-8000-000000000002', 'fallback@example.com',
   '{"name":"Fallback","picture":"https://example.com/picture-old.png"}'),
  ('b0000000-0000-4000-8000-000000000003', 'invalid@example.com',
   '{"full_name":"Invalid","avatar_url":"https://example.com/keep.png"}');

-- ---------------------------------------------------------------------------
-- Trigger and function contract
-- ---------------------------------------------------------------------------
select has_trigger('auth', 'users', 'on_auth_user_metadata_updated',
  'the metadata trigger exists on auth.users');
select is_definer('public', 'sync_profile_avatar', array[]::name[],
  'sync_profile_avatar is security definer');
select ok(
  (select 'search_path=""' = any(proconfig) from pg_proc
    where oid = 'public.sync_profile_avatar()'::regprocedure),
  'sync_profile_avatar has a fixed empty search_path'
);
select function_privs_are('public', 'sync_profile_avatar', array[]::name[], 'anon', array[]::text[],
  'anon cannot execute sync_profile_avatar');
select function_privs_are('public', 'sync_profile_avatar', array[]::name[], 'authenticated', array[]::text[],
  'authenticated cannot execute sync_profile_avatar');

-- ---------------------------------------------------------------------------
-- Sign-in metadata updates
-- ---------------------------------------------------------------------------
update auth.users
set raw_user_meta_data = '{"full_name":"Old Name","avatar_url":"https://example.com/new.png"}'
where id = 'b0000000-0000-4000-8000-000000000001';
select is(
  (select avatar_url from public.profiles where id = 'b0000000-0000-4000-8000-000000000001'),
  'https://example.com/new.png',
  'a fresh Google avatar updates the stored profile URL'
);

update auth.users
set raw_user_meta_data = '{"full_name":"Google Renamed","avatar_url":"https://example.com/newer.png"}'
where id = 'b0000000-0000-4000-8000-000000000001';
select results_eq(
  $$select display_name, avatar_url from public.profiles
    where id = 'b0000000-0000-4000-8000-000000000001'$$,
  $$values ('Old Name'::text, 'https://example.com/newer.png'::text)$$,
  'the avatar syncs while an app-edited display_name stays unchanged'
);

update auth.users
set raw_user_meta_data = '{"name":"Fallback","picture":"https://example.com/picture-new.png"}'
where id = 'b0000000-0000-4000-8000-000000000002';
select is(
  (select avatar_url from public.profiles where id = 'b0000000-0000-4000-8000-000000000002'),
  'https://example.com/picture-new.png',
  'picture is used when avatar_url is absent'
);

update auth.users
set raw_user_meta_data = '{"avatar_url":"https://example.com/primary.png","picture":"https://example.com/fallback.png"}'
where id = 'b0000000-0000-4000-8000-000000000002';
select is(
  (select avatar_url from public.profiles where id = 'b0000000-0000-4000-8000-000000000002'),
  'https://example.com/primary.png',
  'avatar_url wins over picture'
);

update auth.users
set raw_user_meta_data = '{"avatar_url":"http://example.com/not-https.png","picture":"javascript:alert(1)"}'
where id = 'b0000000-0000-4000-8000-000000000003';
select is(
  (select avatar_url from public.profiles where id = 'b0000000-0000-4000-8000-000000000003'),
  'https://example.com/keep.png',
  'invalid or non-https avatars are ignored'
);

update auth.users
set raw_user_meta_data = jsonb_build_object('avatar_url', 'https://example.com/' || repeat('x', 2100))
where id = 'b0000000-0000-4000-8000-000000000003';
select is(
  (select avatar_url from public.profiles where id = 'b0000000-0000-4000-8000-000000000003'),
  'https://example.com/keep.png',
  'oversized avatars are ignored'
);

-- A sentinel proves the trigger wrote nothing: updated_at is constant within a transaction,
-- so comparing two now() values would pass even after an update.
alter table public.profiles disable trigger profiles_set_updated_at;
update public.profiles
set updated_at = '2000-01-01 00:00:00+00'
where id = 'b0000000-0000-4000-8000-000000000003';
alter table public.profiles enable trigger profiles_set_updated_at;

update auth.users
set raw_user_meta_data = jsonb_set(raw_user_meta_data, '{locale}', '"es"')
where id = 'b0000000-0000-4000-8000-000000000003';
select is(
  (select updated_at from public.profiles where id = 'b0000000-0000-4000-8000-000000000003'),
  '2000-01-01 00:00:00+00'::timestamptz,
  'unrelated metadata does not touch the profile'
);

update auth.users
set raw_user_meta_data = '{}'
where id = 'b0000000-0000-4000-8000-000000000003';
select is(
  (select avatar_url from public.profiles where id = 'b0000000-0000-4000-8000-000000000003'),
  'https://example.com/keep.png',
  'metadata without a usable photo keeps the stored avatar'
);

select * from finish();
rollback;
