-- T-134 (US-06): keep profiles.avatar_url in sync with the Google photo on later sign-ins.
-- GoTrue rewrites auth.users.raw_user_meta_data on every Google ID-token sign-in; this trigger copies only
-- the avatar. display_name stays app-owned (editable in Preferencias), and absent/invalid metadata never
-- clears the stored photo.

create function public.sync_profile_avatar() returns trigger
  language plpgsql
  security definer
  set search_path = ''
as $$
declare
  ws constant text := E' \t\n\r\f';
  meta jsonb := case
    when jsonb_typeof(new.raw_user_meta_data) = 'object' then new.raw_user_meta_data
    else '{}'::jsonb
  end;
  key text;
  v_avatar text;
begin
  -- Same precedence and sanitization as handle_new_user: avatar_url, then picture (Google).
  foreach key in array array['avatar_url', 'picture'] loop
    if jsonb_typeof(meta -> key) = 'string' then
      v_avatar := btrim(meta ->> key, ws);
      exit when v_avatar like 'https://%' and char_length(v_avatar) <= 2048;
    end if;
    v_avatar := null;
  end loop;

  -- No new usable photo: keep the stored one (Google may omit the claim on some sign-ins).
  if v_avatar is null then
    return new;
  end if;

  update public.profiles
  set avatar_url = v_avatar
  where id = new.id
    and avatar_url is distinct from v_avatar;

  return new;
end;
$$;

comment on function public.sync_profile_avatar() is
  'Copies a fresh Google avatar from auth.users.raw_user_meta_data to public.profiles.';

revoke execute on function public.sync_profile_avatar() from public, anon, authenticated;

create trigger on_auth_user_metadata_updated
  after update of raw_user_meta_data on auth.users
  for each row
  when (old.raw_user_meta_data is distinct from new.raw_user_meta_data)
  execute function public.sync_profile_avatar();
