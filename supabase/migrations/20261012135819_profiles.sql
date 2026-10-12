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
  -- No default: the sign-up trigger always provides a non-blank value.
  display_name         text not null,
  avatar_url           text,
  birth_date           date,
  sex                  public.sex,
  height_cm            numeric(5, 1) constraint profiles_height_cm_range check (height_cm between 100 and 250),
  activity_level       public.activity_level,
  onboarding_completed boolean not null default false,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  -- Bounds on user-controlled text (03-data-model section 2). The whitespace set matches
  -- the one handle_new_user trims, so the trigger can never produce a rejected value.
  constraint profiles_display_name_length check (char_length(display_name) <= 100),
  constraint profiles_display_name_not_blank check (btrim(display_name, E' \t\n\r\f') <> ''),
  constraint profiles_avatar_url_https check (avatar_url like 'https://%'),
  constraint profiles_avatar_url_length check (char_length(avatar_url) <= 2048)
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

-- Only ever invoked as a trigger; not callable through the API.
revoke execute on function public.set_updated_at() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Sign-up trigger: creates the profile from the auth user metadata.
-- Security definer with a fixed empty search_path and fully qualified names.
-- raw_user_meta_data is user-controlled, so it is sanitized here and sign-up never fails
-- on a profiles constraint:
--   * keys are only read from a JSON object, and only when their value is a JSON string;
--   * display_name: full_name, then name (Google), then the email local part, then 'Usuario';
--     each candidate is trimmed and capped at 100 characters, blank candidates are skipped;
--   * avatar_url: avatar_url, then picture (Google); kept only if https and <= 2048 characters.
-- ---------------------------------------------------------------------------
create function public.handle_new_user() returns trigger
  language plpgsql
  security definer
  set search_path = ''
as $$
declare
  -- Same whitespace set as the profiles_display_name_not_blank constraint.
  ws constant text := E' \t\n\r\f';
  meta jsonb := case
    when jsonb_typeof(new.raw_user_meta_data) = 'object' then new.raw_user_meta_data
    else '{}'::jsonb
  end;
  key text;
  v_name text;
  v_avatar text;
begin
  foreach key in array array['full_name', 'name'] loop
    if jsonb_typeof(meta -> key) = 'string' then
      v_name := btrim(left(btrim(meta ->> key, ws), 100), ws);
      exit when v_name <> '';
    end if;
  end loop;

  if v_name is null or v_name = '' then
    v_name := btrim(left(btrim(split_part(coalesce(new.email, ''), '@', 1), ws), 100), ws);
  end if;
  if v_name = '' then
    v_name := 'Usuario';
  end if;

  foreach key in array array['avatar_url', 'picture'] loop
    if jsonb_typeof(meta -> key) = 'string' then
      v_avatar := btrim(meta ->> key, ws);
      exit when v_avatar like 'https://%' and char_length(v_avatar) <= 2048;
    end if;
    v_avatar := null;
  end loop;

  insert into public.profiles (id, display_name, avatar_url)
  values (new.id, v_name, v_avatar);
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
grant select on table public.profiles to authenticated;
-- Column-level UPDATE: only the user-editable columns (id, created_at and updated_at are not).
grant update (display_name, avatar_url, birth_date, sex, height_cm, activity_level, onboarding_completed)
  on table public.profiles to authenticated;
