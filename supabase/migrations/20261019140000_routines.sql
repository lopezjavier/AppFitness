-- T-043 (US-10): routine folders, routines and routine exercises plus the transactional save_routine RPC.
-- Forward-compatible (ADR-30): the E6 columns exist now with defaults but are not used by E3.

create type public.set_type as enum ('working', 'warm_up', 'failure', 'drop');

create table public.routine_folders (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users (id) on delete cascade default auth.uid(),
  name       text not null check (char_length(btrim(name)) between 1 and 100),
  position   smallint not null default 0 check (position >= 0),
  created_at timestamptz not null default now()
);
create index routine_folders_user_idx on public.routine_folders (user_id);

create table public.routines (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users (id) on delete cascade default auth.uid(),
  name                text not null check (char_length(btrim(name)) between 1 and 100),
  notes               text,
  archived            boolean not null default false,
  pinned_note         text,
  folder_id           uuid references public.routine_folders (id) on delete set null,
  track_as_routine    boolean not null default true,
  auto_update_routine boolean not null default false,
  is_next             boolean not null default false,
  created_at          timestamptz not null default now()
);
create unique index routines_one_next_per_user on public.routines (user_id) where is_next;
create index routines_active_idx on public.routines (user_id) where not archived;

create table public.routine_exercises (
  id               uuid primary key default gen_random_uuid(),
  routine_id       uuid not null references public.routines (id) on delete cascade,
  -- NO ACTION, deferred to commit: deleting a user cascades to routines and custom exercises in any order
  -- (an immediate check would fire when the exercise is deleted, before the routine cascade ran).
  exercise_id      uuid not null references public.exercises (id) deferrable initially deferred,
  position         smallint not null check (position >= 0),
  superset_group   smallint check (superset_group >= 0),
  target_sets      smallint not null check (target_sets between 1 and 10),
  target_reps      smallint not null check (target_reps between 1 and 50),
  target_reps_max  smallint,
  target_rpe       numeric(3, 1) check (target_rpe between 1 and 10),
  planned_set_type public.set_type not null default 'working',
  target_weight_kg numeric(6, 2),
  rest_seconds     smallint not null default 120 check (rest_seconds between 0 and 600),
  constraint routine_exercises_reps_max check (target_reps_max is null or target_reps_max >= target_reps),
  constraint routine_exercises_position_key unique (routine_id, position) deferrable initially deferred
);
create index routine_exercises_routine_position_idx on public.routine_exercises (routine_id, position);
create index routine_exercises_exercise_idx on public.routine_exercises (exercise_id);

alter table public.routine_folders enable row level security;
alter table public.routine_folders force row level security;
alter table public.routines enable row level security;
alter table public.routines force row level security;
alter table public.routine_exercises enable row level security;
alter table public.routine_exercises force row level security;

-- routine_folders
create policy routine_folders_select on public.routine_folders
  for select to authenticated using (user_id = (select auth.uid()));
create policy routine_folders_insert on public.routine_folders
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy routine_folders_update on public.routine_folders
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy routine_folders_delete on public.routine_folders
  for delete to authenticated using (user_id = (select auth.uid()));

-- routines (a folder, when set, must be the caller's)
create policy routines_select on public.routines
  for select to authenticated using (user_id = (select auth.uid()));
create policy routines_insert on public.routines
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and (folder_id is null
         or exists (select 1 from public.routine_folders f
                    where f.id = folder_id and f.user_id = (select auth.uid())))
  );
create policy routines_update on public.routines
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (
    user_id = (select auth.uid())
    and (folder_id is null
         or exists (select 1 from public.routine_folders f
                    where f.id = folder_id and f.user_id = (select auth.uid())))
  );
create policy routines_delete on public.routines
  for delete to authenticated using (user_id = (select auth.uid()));

-- routine_exercises: protected through the parent routine; the exercise must be visible to the caller.
create policy routine_exercises_select on public.routine_exercises
  for select to authenticated
  using (exists (select 1 from public.routines r
                 where r.id = routine_id and r.user_id = (select auth.uid())));
create policy routine_exercises_insert on public.routine_exercises
  for insert to authenticated
  with check (
    exists (select 1 from public.routines r
            where r.id = routine_id and r.user_id = (select auth.uid()))
    and exists (select 1 from public.exercises e where e.id = exercise_id)
  );
create policy routine_exercises_update on public.routine_exercises
  for update to authenticated
  using (exists (select 1 from public.routines r
                 where r.id = routine_id and r.user_id = (select auth.uid())))
  with check (
    exists (select 1 from public.routines r
            where r.id = routine_id and r.user_id = (select auth.uid()))
    and exists (select 1 from public.exercises e where e.id = exercise_id)
  );
create policy routine_exercises_delete on public.routine_exercises
  for delete to authenticated
  using (exists (select 1 from public.routines r
                 where r.id = routine_id and r.user_id = (select auth.uid())));

-- Supabase default privileges grant ALL (incl. TRUNCATE, not covered by RLS) to anon/authenticated.
revoke all on table public.routine_folders, public.routines, public.routine_exercises from anon, authenticated;
grant select, insert, update, delete on table
  public.routine_folders, public.routines, public.routine_exercises to authenticated;

-- Creates (no "id") or updates (name/notes) a routine and replaces its exercises, all in one transaction.
-- Positions are re-assigned from the array order. security invoker: RLS applies as for direct writes.
create function public.save_routine(p_routine jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id   uuid;
  v_name text := btrim(p_routine ->> 'name', E' \t\n\r\f');
begin
  if p_routine is null or jsonb_typeof(p_routine) <> 'object'
     or jsonb_typeof(coalesce(p_routine -> 'exercises', 'null'::jsonb)) <> 'array' then
    raise exception 'invalid routine payload' using errcode = '22023';
  end if;

  if p_routine ->> 'id' is null then
    insert into public.routines (name, notes)
    values (v_name, p_routine ->> 'notes')
    returning id into v_id;
  else
    v_id := (p_routine ->> 'id')::uuid;
    update public.routines
    set name = v_name, notes = p_routine ->> 'notes'
    where id = v_id;
    if not found then
      raise exception 'routine not found' using errcode = 'P0002';
    end if;
    delete from public.routine_exercises where routine_id = v_id;
  end if;

  insert into public.routine_exercises
    (routine_id, exercise_id, position, superset_group, target_sets, target_reps, rest_seconds)
  select v_id,
         (e.value ->> 'exercise_id')::uuid,
         (e.ordinality - 1)::smallint,
         (e.value ->> 'superset_group')::smallint,
         (e.value ->> 'target_sets')::smallint,
         (e.value ->> 'target_reps')::smallint,
         coalesce((e.value ->> 'rest_seconds')::smallint, 120::smallint)
  from jsonb_array_elements(p_routine -> 'exercises') with ordinality as e(value, ordinality);

  return v_id;
end;
$$;

revoke execute on function public.save_routine(jsonb) from public, anon;
grant execute on function public.save_routine(jsonb) to authenticated;
