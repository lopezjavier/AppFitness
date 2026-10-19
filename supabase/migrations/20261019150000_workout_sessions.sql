-- T-044 (US-11, US-15): workout sessions, sets, atomic session RPCs and exercise_history.
-- Forward-compatible (ADR-30): E6 columns exist now but are not used by E3.

create type public.session_status as enum ('in_progress', 'completed', 'discarded');

create table public.workout_sessions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users (id) on delete cascade default auth.uid(),
  routine_id  uuid references public.routines (id) on delete set null,
  name        text not null check (char_length(btrim(name)) between 1 and 100),
  status      public.session_status not null default 'in_progress',
  started_at  timestamptz not null default now(),
  ended_at    timestamptz,
  description text,
  gym         text,
  paused_at   timestamptz,
  constraint workout_sessions_ended_after_start check (ended_at is null or ended_at >= started_at),
  constraint workout_sessions_in_progress_open check (status <> 'in_progress' or ended_at is null)
);
create unique index workout_sessions_one_in_progress on public.workout_sessions (user_id)
  where status = 'in_progress';
create index workout_sessions_user_started_idx on public.workout_sessions (user_id, started_at desc);

create table public.workout_sets (
  id                uuid primary key default gen_random_uuid(),
  session_id        uuid not null references public.workout_sessions (id) on delete cascade,
  -- NO ACTION, deferred to commit: deleting a user cascades to sessions and custom exercises in any order
  -- (an immediate check would fire when the exercise is deleted, before the session cascade ran).
  exercise_id       uuid not null references public.exercises (id) deferrable initially deferred,
  exercise_position smallint not null check (exercise_position >= 0),
  superset_group    smallint check (superset_group >= 0),
  set_index         smallint not null check (set_index >= 1),
  set_type          public.set_type not null default 'working',
  rpe               numeric(3, 1) check (rpe between 1 and 10),
  weight_kg         numeric(6, 2) not null default 0 check (weight_kg >= 0),
  reps              smallint not null default 0 check (reps >= 0),
  duration_s        int,
  distance_m        numeric(7, 1),
  steps             int,
  floors            int,
  completed_at      timestamptz,
  -- Upsert key of the live session (not deferrable: ON CONFLICT needs an immediate unique index).
  constraint workout_sets_session_position_index_key unique (session_id, exercise_position, set_index)
);
create index workout_sets_exercise_idx on public.workout_sets (exercise_id);

alter table public.workout_sessions enable row level security;
alter table public.workout_sessions force row level security;
alter table public.workout_sets enable row level security;
alter table public.workout_sets force row level security;

-- workout_sessions (a routine, when set, must be the caller's)
create policy workout_sessions_select on public.workout_sessions
  for select to authenticated using (user_id = (select auth.uid()));
create policy workout_sessions_insert on public.workout_sessions
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and (routine_id is null
         or exists (select 1 from public.routines r
                    where r.id = routine_id and r.user_id = (select auth.uid())))
  );
create policy workout_sessions_update on public.workout_sessions
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (
    user_id = (select auth.uid())
    and (routine_id is null
         or exists (select 1 from public.routines r
                    where r.id = routine_id and r.user_id = (select auth.uid())))
  );
create policy workout_sessions_delete on public.workout_sessions
  for delete to authenticated using (user_id = (select auth.uid()));

-- workout_sets: protected through the parent session; the exercise must be visible to the caller.
create policy workout_sets_select on public.workout_sets
  for select to authenticated
  using (exists (select 1 from public.workout_sessions s
                 where s.id = session_id and s.user_id = (select auth.uid())));
create policy workout_sets_insert on public.workout_sets
  for insert to authenticated
  with check (
    exists (select 1 from public.workout_sessions s
            where s.id = session_id and s.user_id = (select auth.uid()))
    and exists (select 1 from public.exercises e where e.id = exercise_id)
  );
create policy workout_sets_update on public.workout_sets
  for update to authenticated
  using (exists (select 1 from public.workout_sessions s
                 where s.id = session_id and s.user_id = (select auth.uid())))
  with check (
    exists (select 1 from public.workout_sessions s
            where s.id = session_id and s.user_id = (select auth.uid()))
    and exists (select 1 from public.exercises e where e.id = exercise_id)
  );
create policy workout_sets_delete on public.workout_sets
  for delete to authenticated
  using (exists (select 1 from public.workout_sessions s
                 where s.id = session_id and s.user_id = (select auth.uid())));

-- Supabase default privileges grant ALL (incl. TRUNCATE, not covered by RLS) to anon/authenticated.
revoke all on table public.workout_sessions, public.workout_sets from anon, authenticated;
grant select, insert, update, delete on table
  public.workout_sessions, public.workout_sets to authenticated;

-- Starts a session, optionally from a routine: snapshots the routine name and creates one pending set
-- (weight 0, reps 0, completed_at null) per target set. A second in-progress session raises 23505.
create function public.start_workout_session(p_routine_id uuid default null)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id   uuid;
  v_name text := 'Entreno libre';
begin
  if p_routine_id is not null then
    select r.name into v_name from public.routines r where r.id = p_routine_id;
    if not found then
      raise exception 'routine not found' using errcode = 'P0002';
    end if;
  end if;

  insert into public.workout_sessions (routine_id, name)
  values (p_routine_id, v_name)
  returning id into v_id;

  if p_routine_id is not null then
    insert into public.workout_sets (session_id, exercise_id, exercise_position, superset_group, set_index)
    select v_id, re.exercise_id, re.position, re.superset_group, gs.n
    from public.routine_exercises re
    cross join lateral generate_series(1, re.target_sets) as gs(n)
    where re.routine_id = p_routine_id;
  end if;

  return v_id;
end;
$$;

-- Finishes an in-progress session: drops the pending sets and closes it, in one transaction.
create function public.finish_workout_session(p_session_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.workout_sessions
  set status = 'completed', ended_at = now()
  where id = p_session_id and status = 'in_progress';
  if not found then
    raise exception 'session not found or not in progress' using errcode = 'P0002';
  end if;

  delete from public.workout_sets where session_id = p_session_id and completed_at is null;
end;
$$;

-- Discards an in-progress session. Its sets are kept; history queries only read completed sessions.
create function public.discard_workout_session(p_session_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.workout_sessions
  set status = 'discarded', ended_at = now()
  where id = p_session_id and status = 'in_progress';
  if not found then
    raise exception 'session not found or not in progress' using errcode = 'P0002';
  end if;
end;
$$;

-- Per completed session (UTC date of started_at) with completed sets of the exercise: best estimated 1RM
-- (Epley, reps 1..12, weight > 0, rounded to 0.5 kg; null when no eligible set) and total volume.
-- Warm-up sets are ignored. RLS limits it to the caller's sessions.
create function public.exercise_history(p_exercise_id uuid, p_from date default null)
returns table (session_id uuid, session_date date, best_one_rm numeric, total_volume numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select s.id,
         (s.started_at at time zone 'UTC')::date,
         max(case
               when ws.reps between 1 and 12 and ws.weight_kg > 0 then
                 round(case when ws.reps = 1 then ws.weight_kg
                            else ws.weight_kg * (1 + ws.reps / 30.0) end * 2) / 2
             end),
         coalesce(sum(ws.weight_kg * ws.reps), 0)
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  where s.status = 'completed'
    and ws.exercise_id = p_exercise_id
    and ws.completed_at is not null
    and ws.set_type <> 'warm_up'
    and (p_from is null or (s.started_at at time zone 'UTC')::date >= p_from)
  group by s.id, s.started_at
  order by s.started_at;
$$;

revoke execute on function public.start_workout_session(uuid) from public, anon;
revoke execute on function public.finish_workout_session(uuid) from public, anon;
revoke execute on function public.discard_workout_session(uuid) from public, anon;
revoke execute on function public.exercise_history(uuid, date) from public, anon;
grant execute on function public.start_workout_session(uuid) to authenticated;
grant execute on function public.finish_workout_session(uuid) to authenticated;
grant execute on function public.discard_workout_session(uuid) to authenticated;
grant execute on function public.exercise_history(uuid, date) to authenticated;
