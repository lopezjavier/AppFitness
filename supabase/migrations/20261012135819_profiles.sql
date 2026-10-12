-- T-018 (US-02): profiles table, sign-up trigger and RLS.
-- Everything is schema-qualified; nothing relies on the caller's search_path.

-- ---------------------------------------------------------------------------
-- Extensions (Supabase convention: never in public; avoids advisor 0014 extension_in_public)
-- ---------------------------------------------------------------------------
create extension if not exists unaccent with schema extensions;

-- ---------------------------------------------------------------------------
-- Enums (03-data-model section 1, only those needed by profiles)
-- ---------------------------------------------------------------------------
create type public.sex as enum ('male', 'female');
create type public.activity_level as enum ('sedentary', 'light', 'moderate', 'high', 'very_high');

-- ---------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------
create table public.profiles (
  id                   uuid primary key references auth.users (id) on delete cascade,
  display_name         text not null default '',
  avatar_url           text,
  birth_date           date,
  sex                  public.sex,
  height_cm            numeric(5, 1) constraint profiles_height_cm_range check (height_cm between 100 and 250),
  activity_level       public.activity_level,
  onboarding_completed boolean not null default false,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.profiles is 'One row per auth user, created by the on_auth_user_created trigger.';

-- ---------------------------------------------------------------------------
-- Reusable updated_at maintenance (security invoker, the default)
-- ---------------------------------------------------------------------------
create function public.set_updated_at() returns trigger
  language plpgsql
  set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Sign-up trigger: creates the profile from the auth user metadata.
-- Security definer with a fixed empty search_path and fully qualified names.
-- Google provides full_name/name and avatar_url/picture; email-only users fall back
-- to the local part of their email.
-- ---------------------------------------------------------------------------
create function public.handle_new_user() returns trigger
  language plpgsql
  security definer
  set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(
      nullif(new.raw_user_meta_data ->> 'full_name', ''),
      nullif(new.raw_user_meta_data ->> 'name', ''),
      split_part(coalesce(new.email, ''), '@', 1)
    ),
    coalesce(
      nullif(new.raw_user_meta_data ->> 'avatar_url', ''),
      nullif(new.raw_user_meta_data ->> 'picture', '')
    )
  );
  return new;
end;
$$;

-- Not callable by API roles (advisors 0028/0029). Postgres only checks EXECUTE when the
-- trigger is created, not when it fires, so the trigger keeps working.
revoke execute on function public.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- RLS and privileges
-- FORCE applies RLS to the table owner too. The definer trigger still inserts because
-- Supabase's postgres role has BYPASSRLS (proven by supabase/tests/profiles.test.sql).
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.profiles force row level security;

create policy profiles_select_own on public.profiles
  for select to authenticated
  using (id = (select auth.uid()));

create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- No insert/delete policies: the trigger inserts and clients never delete.
-- Defense in depth: Supabase's default privileges grant ALL (incl. TRUNCATE, which RLS does
-- not cover) on new public tables to anon/authenticated.
revoke all on table public.profiles from anon, authenticated;
grant select, update on table public.profiles to authenticated;
