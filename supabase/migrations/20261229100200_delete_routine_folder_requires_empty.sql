-- ADR-95: a folder with at least one non-archived routine cannot be deleted.
-- Archived routines (never shown) do not block and are deleted with the folder.
create or replace function public.delete_routine_folder(p_id uuid)
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
  if exists (
    select 1 from public.routines
    where folder_id = p_id and user_id = (select auth.uid()) and not archived
  ) then
    raise exception 'folder not empty' using errcode = '23503';
  end if;
  delete from public.routines where folder_id = p_id and user_id = (select auth.uid());
  get diagnostics v_count = row_count;
  delete from public.routine_folders where id = p_id and user_id = (select auth.uid());
  return v_count;
end;
$$;

revoke execute on function public.delete_routine_folder(uuid) from public, anon;
grant execute on function public.delete_routine_folder(uuid) to authenticated;
