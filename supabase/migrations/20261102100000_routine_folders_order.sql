-- T-098 (US-35): routine folders, next workout and manual order.
-- routines.position is the manual order inside a group (folder or the no-folder group). Exception to ADR-30:
-- the column is added now because the order is used right away. Gaps are fine; order is by position.

alter table public.routines
  add column position smallint not null default 0 check (position >= 0);

-- Backfill: A to Z inside each group, the same order "Mis rutinas" showed before (sortRoutinesByName).
update public.routines r
set position = o.pos
from (
  select id,
         (row_number() over (partition by user_id, folder_id order by lower(name), name, id) - 1)::smallint as pos
  from public.routines
) o
where r.id = o.id;

-- Folders keep their creation order.
update public.routine_folders f
set position = o.pos
from (
  select id, (row_number() over (partition by user_id order by created_at, id) - 1)::smallint as pos
  from public.routine_folders
) o
where f.id = o.id;

create index routines_user_folder_position_idx on public.routines (user_id, folder_id, position);

-- New (and duplicated) rows go to the end of their group, without touching save_routine.
create function public.routines_set_end_position()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  select coalesce(max(r.position), -1) + 1 into new.position
  from public.routines r
  where r.user_id = new.user_id and r.folder_id is not distinct from new.folder_id;
  return new;
end;
$$;

create trigger routines_set_end_position
  before insert on public.routines
  for each row execute function public.routines_set_end_position();

create function public.routine_folders_set_end_position()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  select coalesce(max(f.position), -1) + 1 into new.position
  from public.routine_folders f
  where f.user_id = new.user_id;
  return new;
end;
$$;

create trigger routine_folders_set_end_position
  before insert on public.routine_folders
  for each row execute function public.routine_folders_set_end_position();

revoke execute on function public.routines_set_end_position(), public.routine_folders_set_end_position()
  from public, anon;

-- ---------------------------------------------------------------------------
-- RPCs (security invoker: RLS isolates users; ownership of every id is validated)
-- ---------------------------------------------------------------------------

create function public.create_routine_folder(p_name text)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_name text := btrim(p_name, E' \t\n\r\f');
  v_id   uuid;
begin
  if v_name is null or char_length(v_name) not between 1 and 100 then
    raise exception 'invalid folder name' using errcode = '22023';
  end if;
  insert into public.routine_folders (name) values (v_name) returning id into v_id;
  return v_id;
end;
$$;

create function public.rename_routine_folder(p_id uuid, p_name text)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_name text := btrim(p_name, E' \t\n\r\f');
begin
  if v_name is null or char_length(v_name) not between 1 and 100 then
    raise exception 'invalid folder name' using errcode = '22023';
  end if;
  update public.routine_folders set name = v_name where id = p_id and user_id = (select auth.uid());
  if not found then
    raise exception 'folder not found' using errcode = 'P0002';
  end if;
end;
$$;

-- Deletes the folder and ALL its routines (archived included); sessions keep their data (routine_id set null).
create function public.delete_routine_folder(p_id uuid)
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_count integer;
begin
  if not exists (select 1 from public.routine_folders where id = p_id and user_id = (select auth.uid())) then
    raise exception 'folder not found' using errcode = 'P0002';
  end if;
  delete from public.routines where folder_id = p_id and user_id = (select auth.uid());
  get diagnostics v_count = row_count;
  delete from public.routine_folders where id = p_id and user_id = (select auth.uid());
  return v_count;
end;
$$;

-- Moves a routine to the end of the target group (null folder = no-folder group).
create function public.move_routine_to_folder(p_routine_id uuid, p_folder_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_pos smallint;
begin
  if not exists (select 1 from public.routines where id = p_routine_id and user_id = (select auth.uid())) then
    raise exception 'routine not found' using errcode = 'P0002';
  end if;
  if p_folder_id is not null
     and not exists (select 1 from public.routine_folders where id = p_folder_id and user_id = (select auth.uid())) then
    raise exception 'folder not found' using errcode = 'P0002';
  end if;
  select (coalesce(max(r.position), -1) + 1)::smallint into v_pos
  from public.routines r
  where r.user_id = (select auth.uid()) and r.folder_id is not distinct from p_folder_id and r.id <> p_routine_id;
  update public.routines set folder_id = p_folder_id, position = v_pos
  where id = p_routine_id and user_id = (select auth.uid());
end;
$$;

-- position = array index for exactly those routines of the group; foreign, foreign-group or duplicate ids fail.
create function public.save_routine_order(p_folder_id uuid, p_routine_ids uuid[])
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_ids uuid[] := coalesce(p_routine_ids, '{}'::uuid[]);
begin
  if (select count(distinct x) from unnest(v_ids) as x) <> cardinality(v_ids) then
    raise exception 'duplicate routine ids' using errcode = '22023';
  end if;
  if (select count(*) from public.routines r
      where r.id = any (v_ids) and r.user_id = (select auth.uid())
        and r.folder_id is not distinct from p_folder_id) <> cardinality(v_ids) then
    raise exception 'routine not in the group' using errcode = '22023';
  end if;
  update public.routines r
  set position = (o.ord - 1)::smallint
  from unnest(v_ids) with ordinality as o(id, ord)
  where r.id = o.id and r.user_id = (select auth.uid());
end;
$$;

-- Clears the caller's next routine, then sets the given one (null only clears). One transaction, so the
-- partial unique index routines_one_next_per_user is never violated.
create function public.set_next_routine(p_routine_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if p_routine_id is not null
     and not exists (select 1 from public.routines where id = p_routine_id and user_id = (select auth.uid())) then
    raise exception 'routine not found' using errcode = 'P0002';
  end if;
  update public.routines set is_next = false where user_id = (select auth.uid()) and is_next;
  if p_routine_id is not null then
    update public.routines set is_next = true where id = p_routine_id and user_id = (select auth.uid());
  end if;
end;
$$;

revoke execute on function public.create_routine_folder(text), public.rename_routine_folder(uuid, text),
  public.delete_routine_folder(uuid), public.move_routine_to_folder(uuid, uuid),
  public.save_routine_order(uuid, uuid[]), public.set_next_routine(uuid) from public, anon;
grant execute on function public.create_routine_folder(text), public.rename_routine_folder(uuid, text),
  public.delete_routine_folder(uuid), public.move_routine_to_folder(uuid, uuid),
  public.save_routine_order(uuid, uuid[]), public.set_next_routine(uuid) to authenticated;
