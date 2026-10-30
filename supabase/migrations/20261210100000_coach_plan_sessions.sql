-- T-163 (E13, US-72): the training block attached to a coach plan, and its confirmation into routines.
-- Rules: planning/04-business-rules.md section 9, planning/07-coach-design.md section 3.
-- coach_plan_sessions is read-only for the owner; the proposal is written by attach_coach_block (security definer,
-- every statement filtered by auth.uid()) and turned into routines only by save_coach_block.

alter table public.coach_plans
  add column training  jsonb null
    constraint coach_plans_training_object check (jsonb_typeof(training) = 'object'),
  -- The folder created when the block is saved; set null if the user deletes the folder.
  add column folder_id uuid null references public.routine_folders (id) on delete set null;

alter table public.coach_recommendations drop constraint coach_recommendations_kind_values;
alter table public.coach_recommendations
  add constraint coach_recommendations_kind_values check (kind in ('plan', 'calorie_change', 'block'));

create table public.coach_plan_sessions (
  id            uuid primary key default gen_random_uuid(),
  plan_id       uuid not null references public.coach_plans (id) on delete cascade,
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  -- One row per week and session: the weekly template repeated block_weeks times (T-165 sets deload per week).
  week          smallint not null constraint coach_plan_sessions_week_range check (week between 1 and 4),
  deload        boolean not null default false,
  session_index smallint not null constraint coach_plan_sessions_index_range check (session_index between 1 and 7),
  name          text not null constraint coach_plan_sessions_name_length check (char_length(name) between 1 and 100),
  -- Muscle groups trained in the session.
  focus         text[] not null default '{}',
  -- Planned exercises in order: exercise_id, role, sets, rep_min, rep_max, rest_seconds, reason.
  exercises     jsonb not null constraint coach_plan_sessions_exercises_array check (jsonb_typeof(exercises) = 'array'),
  -- Null until save_coach_block; set null if the user deletes the routine.
  -- The same routine serves every week of a session_index. Null until save_coach_block; set null if deleted.
  routine_id    uuid null references public.routines (id) on delete set null,
  -- The completed workout of that week (filled by later tasks).
  session_id    uuid null references public.workout_sessions (id) on delete set null,
  created_at    timestamptz not null default now(),
  constraint coach_plan_sessions_plan_index_key unique (plan_id, week, session_index)
);

create index coach_plan_sessions_plan_idx on public.coach_plan_sessions (plan_id);
create index coach_plan_sessions_user_idx on public.coach_plan_sessions (user_id);
create index coach_plan_sessions_routine_idx on public.coach_plan_sessions (routine_id);
create index coach_plan_sessions_session_idx on public.coach_plan_sessions (session_id);

alter table public.coach_plan_sessions enable row level security;
alter table public.coach_plan_sessions force row level security;

create policy coach_plan_sessions_select_own on public.coach_plan_sessions
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_plan_sessions from anon, authenticated;
grant select on table public.coach_plan_sessions to authenticated;

-- ---------------------------------------------------------------------------
-- coach_assert_block: the server-side invariants of a block (04 section 9), mirroring the coach function's
-- validator so no caller can store a block that breaks them. Raises 22023.
-- ---------------------------------------------------------------------------
create function public.coach_assert_block(
  p_uid uuid,
  p_training jsonb,
  p_sessions jsonb
)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_days      integer;
  v_equipment text[];
  v_session   record;
  v_ex        record;
  v_row       record;
  v_role      text;
  v_rest      integer;
  v_sets      integer;
  v_rmin      integer;
  v_rmax      integer;
  v_reason    text;
  v_seen_iso  boolean;
  v_ids       uuid[];
  v_id        uuid;
begin
  if p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array' then
    raise exception 'block invariant: shape' using errcode = '22023';
  end if;

  begin
    v_days := (p_training->>'days_per_week')::numeric::integer;
    if jsonb_typeof(coalesce(p_training->'equipment', '[]'::jsonb)) <> 'array' then
      raise exception 'block invariant: equipment_shape' using errcode = '22023';
    end if;
    select coalesce(array_agg(e.value), '{}') into v_equipment
    from jsonb_array_elements_text(coalesce(p_training->'equipment', '[]'::jsonb)) e(value);
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception 'block invariant: malformed_training' using errcode = '22023';
  end;

  if jsonb_typeof(p_training->'block_weeks') is distinct from 'number'
     or (p_training->>'block_weeks')::numeric not between 1 and 4
     or (p_training->>'block_weeks')::numeric <> trunc((p_training->>'block_weeks')::numeric) then
    raise exception 'block invariant: block_weeks' using errcode = '22023';
  end if;
  if v_days is null or v_days not between 1 and 7 or jsonb_array_length(p_sessions) <> v_days then
    raise exception 'block invariant: session_count' using errcode = '22023';
  end if;

  for v_session in
    select s.value, s.ordinality from jsonb_array_elements(p_sessions) with ordinality s(value, ordinality)
  loop
    if jsonb_typeof(v_session.value) <> 'object'
       or jsonb_typeof(v_session.value->'name') is distinct from 'string'
       or char_length(btrim(v_session.value->>'name')) not between 1 and 100
       or jsonb_typeof(coalesce(v_session.value->'focus', '[]'::jsonb)) <> 'array'
       or jsonb_typeof(v_session.value->'exercises') is distinct from 'array'
       or jsonb_array_length(v_session.value->'exercises') = 0 then
      raise exception 'block invariant: session_shape' using errcode = '22023';
    end if;

    v_seen_iso := false;
    v_ids := '{}';
    for v_ex in
      select e.value from jsonb_array_elements(v_session.value->'exercises') with ordinality e(value, ordinality)
      order by e.ordinality
    loop
      if jsonb_typeof(v_ex.value) <> 'object' then
        raise exception 'block invariant: exercise_shape' using errcode = '22023';
      end if;
      begin
        v_id := (v_ex.value->>'exercise_id')::uuid;
        v_role := v_ex.value->>'role';
        v_rest := (v_ex.value->>'rest_seconds')::numeric::integer;
        v_sets := (v_ex.value->>'sets')::numeric::integer;
        v_rmin := (v_ex.value->>'rep_min')::numeric::integer;
        v_rmax := (v_ex.value->>'rep_max')::numeric::integer;
      exception when invalid_text_representation or numeric_value_out_of_range then
        raise exception 'block invariant: malformed_exercise' using errcode = '22023';
      end;
      if v_id is null or v_role is null or v_rest is null or v_sets is null or v_rmin is null or v_rmax is null
         or jsonb_typeof(v_ex.value->'reason') is distinct from 'string' then
        raise exception 'block invariant: missing_field' using errcode = '22023';
      end if;

      -- Visible to the caller: a catalogue row or one of their own custom exercises.
      select e.equipment::text as equipment, e.movement_pattern::text as pattern into v_row
      from public.exercises e
      where e.id = v_id and (not e.is_custom or e.created_by = p_uid);
      if not found then
        raise exception 'block invariant: unknown_exercise' using errcode = '22023';
      end if;
      if cardinality(v_equipment) > 0 and not (v_row.equipment = any (v_equipment)) then
        raise exception 'block invariant: equipment' using errcode = '22023';
      end if;
      if v_id = any (v_ids) then
        raise exception 'block invariant: duplicate_exercise' using errcode = '22023';
      end if;
      v_ids := v_ids || v_id;

      if v_role = 'main' then
        if v_seen_iso then
          raise exception 'block invariant: order' using errcode = '22023';
        end if;
        if v_row.pattern is null or v_row.pattern not in
           ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 120 and 180 then
          raise exception 'block invariant: rest' using errcode = '22023';
        end if;
      elsif v_role = 'isolation' then
        v_seen_iso := true;
        if v_row.pattern is null or v_row.pattern not in ('isolation', 'core') then
          raise exception 'block invariant: role_pattern' using errcode = '22023';
        end if;
        if v_rest not between 60 and 120 then
          raise exception 'block invariant: rest' using errcode = '22023';
        end if;
      else
        raise exception 'block invariant: role' using errcode = '22023';
      end if;

      if v_sets not between 1 and 10 then
        raise exception 'block invariant: sets' using errcode = '22023';
      end if;
      if v_rmin not between 1 and 50 or v_rmax not between 1 and 50 or v_rmin > v_rmax then
        raise exception 'block invariant: rep_range' using errcode = '22023';
      end if;
      v_reason := btrim(v_ex.value->>'reason');
      if char_length(v_reason) < 10 or char_length(v_reason) > 300 then
        raise exception 'block invariant: reason' using errcode = '22023';
      end if;
    end loop;
  end loop;
end;
$$;

revoke execute on function public.coach_assert_block(uuid, jsonb, jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- attach_coach_block: stores the proposed block of one of the caller's open plans. Idempotent while unsaved:
-- a new proposal replaces the previous unsaved sessions. Nothing is created in routines here.
-- ---------------------------------------------------------------------------
create function public.attach_coach_block(
  p_plan_id uuid,
  p_training jsonb,
  p_sessions jsonb,
  p_method jsonb,
  p_input_summary jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_plan  public.coach_plans;
  v_ids   jsonb;
begin
  if p_plan_id is null or p_training is null or jsonb_typeof(p_training) <> 'object'
     or p_sessions is null or jsonb_typeof(p_sessions) <> 'array'
     or p_method is null or jsonb_typeof(p_method) <> 'object'
     or p_input_summary is null then
    raise exception 'invalid block' using errcode = '22023';
  end if;

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;
  if v_plan.folder_id is not null
     or exists (select 1 from public.coach_plan_sessions s
                where s.plan_id = p_plan_id and s.user_id = v_uid and s.routine_id is not null) then
    raise exception 'block already saved' using errcode = '55000';
  end if;

  perform public.coach_assert_block(v_uid, p_training, p_sessions);

  delete from public.coach_plan_sessions where plan_id = p_plan_id and user_id = v_uid;
  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind = 'block' and status = 'pending';

  update public.coach_plans set training = p_training where id = p_plan_id and user_id = v_uid;

  insert into public.coach_plan_sessions (plan_id, user_id, week, session_index, name, focus, exercises)
  select p_plan_id, v_uid, w.n::smallint, s.ordinality::smallint, btrim(s.value->>'name'),
         coalesce((select array_agg(f.value) from jsonb_array_elements_text(coalesce(s.value->'focus', '[]'::jsonb)) f(value)), '{}'),
         s.value->'exercises'
  from jsonb_array_elements(p_sessions) with ordinality s(value, ordinality)
  cross join generate_series(1, (p_training->>'block_weeks')::int) w(n);

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, input_summary)
  values (v_uid, p_plan_id, 'block',
          jsonb_build_object('training', p_training, 'sessions', p_sessions, 'method', p_method),
          p_input_summary);

  select coalesce(jsonb_agg(s.id order by s.week, s.session_index), '[]'::jsonb) into v_ids
  from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid;

  return jsonb_build_object('plan_id', p_plan_id, 'sessions', v_ids);
end;
$$;

-- ---------------------------------------------------------------------------
-- coach_link_block: the writes save_coach_block cannot do as the caller (the tables are read-only for them).
-- Filters by auth.uid() and checks that the folder and the routines are the caller's own.
-- Granted to authenticated only because save_coach_block (security invoker) must call it; it refuses any call that
-- does not come from save_coach_block (transaction-local flag), so it is not a usable entry point on its own.
-- ---------------------------------------------------------------------------
create function public.coach_link_block(p_plan_id uuid, p_folder_id uuid, p_routine_ids uuid[])
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_n   integer;
begin
  if coalesce(current_setting('app.coach_block_save', true), '') <> 'on' then
    raise exception 'coach_link_block is internal' using errcode = '42501';
  end if;
  if p_plan_id is null or p_folder_id is null or p_routine_ids is null then
    raise exception 'invalid link' using errcode = '22023';
  end if;
  if not exists (select 1 from public.routine_folders f where f.id = p_folder_id and f.user_id = v_uid) then
    raise exception 'folder not found' using errcode = 'P0002';
  end if;
  select count(*) into v_n from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = 1;
  if v_n = 0 or v_n <> coalesce(cardinality(p_routine_ids), 0) then
    raise exception 'routines do not match the sessions' using errcode = '22023';
  end if;
  if exists (select 1 from unnest(p_routine_ids) r(id)
             where not exists (select 1 from public.routines x where x.id = r.id and x.user_id = v_uid)) then
    raise exception 'routine not found' using errcode = 'P0002';
  end if;

  update public.coach_plans set folder_id = p_folder_id where id = p_plan_id and user_id = v_uid;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  update public.coach_plan_sessions s set routine_id = r.id
    from unnest(p_routine_ids) with ordinality r(id, ord)
    where s.plan_id = p_plan_id and s.user_id = v_uid and s.session_index = r.ord; -- every week
  update public.coach_recommendations set status = 'accepted', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind = 'block' and status = 'pending';
end;
$$;

-- ---------------------------------------------------------------------------
-- save_coach_block: confirmation of the block. One folder and one routine per session, created through the existing
-- save_routine (caller's RLS applies). Idempotent: a second call returns the same folder and routines.
-- ---------------------------------------------------------------------------
create function public.save_coach_block(p_plan_id uuid)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_plan    public.coach_plans;
  v_folder  uuid;
  v_session record;
  v_name    text;
  v_n       integer;
  v_rid     uuid;
  v_rids    uuid[] := array[]::uuid[];
  v_payload jsonb;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  -- Serialises concurrent confirmations of the same plan.
  perform pg_advisory_xact_lock(hashtextextended(p_plan_id::text, 0));

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid;
  if not found then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;

  if v_plan.folder_id is not null
     or exists (select 1 from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.routine_id is not null) then
    return jsonb_build_object(
      'folder_id', v_plan.folder_id,
      'routine_ids', coalesce((select jsonb_agg(s.routine_id order by s.session_index)
                               from public.coach_plan_sessions s
                               where s.plan_id = p_plan_id and s.week = 1 and s.routine_id is not null), '[]'::jsonb));
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;
  if v_plan.training is null or not exists (select 1 from public.coach_plan_sessions s where s.plan_id = p_plan_id) then
    raise exception 'plan has no block' using errcode = '55000';
  end if;

  v_folder := public.create_routine_folder('Bloque ' || to_char(v_plan.block_start, 'DD/MM/YYYY'));

  for v_session in
    select s.name, s.exercises from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.week = 1 order by s.session_index
  loop
    -- Routine names are unique per user: add a number when the name is taken.
    v_name := v_session.name;
    v_n := 1;
    while exists (select 1 from public.routines r
                  where r.user_id = v_uid and not r.archived and lower(btrim(r.name)) = lower(btrim(v_name))) loop
      v_n := v_n + 1;
      v_name := left(v_session.name, 90) || ' ' || v_n;
    end loop;

    select jsonb_build_object(
      'name', v_name,
      'track_as_routine', true,
      'exercises', coalesce(jsonb_agg(x.obj order by x.pos), '[]'::jsonb))
    into v_payload
    from (
      select e.ordinality as pos,
             jsonb_build_object(
               'exercise_id', e.value->>'exercise_id',
               'rest_seconds', (e.value->>'rest_seconds')::int,
               'rep_min', (e.value->>'rep_min')::int,
               'rep_max', (e.value->>'rep_max')::int,
               'notes', btrim(e.value->>'reason'),
               'note_kind', 'other',
               -- One working set per planned set, at the bottom of the rep range.
               'sets', to_jsonb(array_fill(
                          jsonb_build_object('reps', (e.value->>'rep_min')::int, 'set_type', 'working'),
                          array[(e.value->>'sets')::int]))) as obj
      from jsonb_array_elements(v_session.exercises) with ordinality e(value, ordinality)
    ) x;

    v_rid := public.save_routine(v_payload);
    perform public.move_routine_to_folder(v_rid, v_folder);
    v_rids := v_rids || v_rid;
  end loop;

  perform set_config('app.coach_block_save', 'on', true);
  perform public.coach_link_block(p_plan_id, v_folder, v_rids);
  perform set_config('app.coach_block_save', 'off', true);

  return jsonb_build_object('folder_id', v_folder, 'routine_ids', to_jsonb(v_rids));
end;
$$;

revoke execute on function public.attach_coach_block(uuid, jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke execute on function public.coach_link_block(uuid, uuid, uuid[]) from public, anon;
revoke execute on function public.save_coach_block(uuid) from public, anon;
grant execute on function public.attach_coach_block(uuid, jsonb, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.coach_link_block(uuid, uuid, uuid[]) to authenticated;
grant execute on function public.save_coach_block(uuid) to authenticated;
