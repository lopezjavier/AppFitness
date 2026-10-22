-- T-089 (US-29, ADR-50/ADR-51): per-set routine targets. Each routine exercise owns an ordered list of planned
-- sets (weight, reps, type). routine_exercises.target_reps / target_weight_kg / planned_set_type stay as legacy
-- summaries (not dropped).

create table public.routine_exercise_sets (
  id                  uuid primary key default gen_random_uuid(),
  routine_exercise_id uuid not null references public.routine_exercises (id) on delete cascade,
  set_index           smallint not null check (set_index >= 1),
  target_weight_kg    numeric(6, 2) check (target_weight_kg >= 0),
  target_reps         smallint not null check (target_reps between 1 and 50),
  set_type            public.set_type not null default 'working',
  constraint routine_exercise_sets_index_key unique (routine_exercise_id, set_index)
);
create index routine_exercise_sets_exercise_idx on public.routine_exercise_sets (routine_exercise_id);

alter table public.routine_exercise_sets enable row level security;
alter table public.routine_exercise_sets force row level security;

-- Protected through the parent routine_exercise -> routine owner.
create policy routine_exercise_sets_select on public.routine_exercise_sets
  for select to authenticated
  using (exists (select 1 from public.routine_exercises re
                 join public.routines r on r.id = re.routine_id
                 where re.id = routine_exercise_id and r.user_id = (select auth.uid())));
create policy routine_exercise_sets_insert on public.routine_exercise_sets
  for insert to authenticated
  with check (exists (select 1 from public.routine_exercises re
                      join public.routines r on r.id = re.routine_id
                      where re.id = routine_exercise_id and r.user_id = (select auth.uid())));
create policy routine_exercise_sets_update on public.routine_exercise_sets
  for update to authenticated
  using (exists (select 1 from public.routine_exercises re
                 join public.routines r on r.id = re.routine_id
                 where re.id = routine_exercise_id and r.user_id = (select auth.uid())))
  with check (exists (select 1 from public.routine_exercises re
                      join public.routines r on r.id = re.routine_id
                      where re.id = routine_exercise_id and r.user_id = (select auth.uid())));
create policy routine_exercise_sets_delete on public.routine_exercise_sets
  for delete to authenticated
  using (exists (select 1 from public.routine_exercises re
                 join public.routines r on r.id = re.routine_id
                 where re.id = routine_exercise_id and r.user_id = (select auth.uid())));

revoke all on table public.routine_exercise_sets from anon, authenticated;
grant select, insert, update, delete on table public.routine_exercise_sets to authenticated;

-- Data migration: every existing routine exercise becomes target_sets identical planned sets.
insert into public.routine_exercise_sets (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
select re.id, gs.n, re.target_weight_kg, re.target_reps, re.planned_set_type
from public.routine_exercises re
cross join lateral generate_series(1, re.target_sets) as gs(n)
on conflict (routine_exercise_id, set_index) do nothing;

-- Creates (no "id") or updates (name/notes) a routine and replaces its exercises and their planned sets, all in
-- one transaction. Each exercise carries sets: [{weight_kg?, reps, set_type}] (1-10). Positions and set indexes
-- come from the array order. Superset members must have the same set count (22023, ADR-38).
-- security invoker: RLS applies as for direct writes.
create or replace function public.save_routine(p_routine jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_id      uuid;
  v_name    text := btrim(p_routine ->> 'name', E' \t\n\r\f');
  v_ex      record;
  v_set     record;
  v_re_id   uuid;
  v_count   int;
  v_reps    smallint;
  v_weight  numeric;
  v_type    public.set_type;
begin
  if p_routine is null or jsonb_typeof(p_routine) <> 'object'
     or jsonb_typeof(coalesce(p_routine -> 'exercises', 'null'::jsonb)) <> 'array' then
    raise exception 'invalid routine payload' using errcode = '22023';
  end if;

  -- Superset members must agree on the number of sets.
  if exists (
    select 1
    from jsonb_array_elements(p_routine -> 'exercises') as e(value)
    where e.value ->> 'superset_group' is not null
    group by e.value ->> 'superset_group'
    having count(distinct coalesce(jsonb_array_length(e.value -> 'sets'), -1)) > 1
  ) then
    raise exception 'superset members must have the same number of sets' using errcode = '22023';
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

  for v_ex in
    select e.value, e.ordinality
    from jsonb_array_elements(p_routine -> 'exercises') with ordinality as e(value, ordinality)
    order by e.ordinality
  loop
    if jsonb_typeof(coalesce(v_ex.value -> 'sets', 'null'::jsonb)) <> 'array' then
      raise exception 'invalid sets' using errcode = '22023';
    end if;
    v_count := jsonb_array_length(v_ex.value -> 'sets');
    if v_count not between 1 and 10 then
      raise exception 'an exercise needs 1 to 10 sets' using errcode = '22023';
    end if;

    -- Legacy summary: first working set's reps, else the first set's.
    select (s.value ->> 'reps')::smallint into v_reps
    from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
    order by (coalesce(s.value ->> 'set_type', 'working') <> 'working'), s.ordinality
    limit 1;
    if v_reps is null or v_reps not between 1 and 50 then
      raise exception 'reps must be between 1 and 50' using errcode = '22023';
    end if;

    insert into public.routine_exercises
      (routine_id, exercise_id, position, superset_group, target_sets, target_reps, target_rpe, rest_seconds)
    values (v_id,
            (v_ex.value ->> 'exercise_id')::uuid,
            (v_ex.ordinality - 1)::smallint,
            (v_ex.value ->> 'superset_group')::smallint,
            v_count,
            v_reps,
            (v_ex.value ->> 'target_rpe')::numeric,
            coalesce((v_ex.value ->> 'rest_seconds')::smallint, 120::smallint))
    returning id into v_re_id;

    for v_set in
      select s.value, s.ordinality
      from jsonb_array_elements(v_ex.value -> 'sets') with ordinality as s(value, ordinality)
      order by s.ordinality
    loop
      begin
        v_type := coalesce(v_set.value ->> 'set_type', 'working')::public.set_type;
        v_weight := (v_set.value ->> 'weight_kg')::numeric;
        v_reps := (v_set.value ->> 'reps')::smallint;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'invalid set' using errcode = '22023';
      end;
      if v_reps is null or v_reps not between 1 and 50 or coalesce(v_weight, 0) < 0 then
        raise exception 'invalid set' using errcode = '22023';
      end if;
      insert into public.routine_exercise_sets
        (routine_exercise_id, set_index, target_weight_kg, target_reps, set_type)
      values (v_re_id, v_set.ordinality::smallint, v_weight, v_reps, v_type);
    end loop;
  end loop;

  return v_id;
end;
$$;

-- Starts a session, optionally from a routine: snapshots the routine name and creates one pending set
-- (weight 0, reps 0, completed_at null) per planned set, carrying its type. Planned weight/reps are read from the
-- routine at runtime (ADR-50). A routine exercise without planned sets falls back to target_sets generic sets.
-- A second in-progress session raises 23505.
create or replace function public.start_workout_session(p_routine_id uuid default null)
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
    insert into public.workout_sets
      (session_id, exercise_id, exercise_position, superset_group, set_index, set_type)
    select v_id, re.exercise_id, re.position, re.superset_group, rs.set_index, rs.set_type
    from public.routine_exercises re
    join public.routine_exercise_sets rs on rs.routine_exercise_id = re.id
    where re.routine_id = p_routine_id;

    insert into public.workout_sets (session_id, exercise_id, exercise_position, superset_group, set_index)
    select v_id, re.exercise_id, re.position, re.superset_group, gs.n
    from public.routine_exercises re
    cross join lateral generate_series(1, re.target_sets) as gs(n)
    where re.routine_id = p_routine_id
      and not exists (select 1 from public.routine_exercise_sets rs where rs.routine_exercise_id = re.id);
  end if;

  return v_id;
end;
$$;
