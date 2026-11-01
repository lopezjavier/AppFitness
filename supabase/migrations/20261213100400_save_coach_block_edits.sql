-- T-179 (ADR-83): save_coach_block takes the replacements chosen in the block preview.
-- p_edits = [{"session_index": 2, "position": 3, "exercise_id": "<uuid>"}], position 1-based in the session's
-- exercises. Edits are applied to every week of the session before the routines are created, in the same
-- transaction, so an invalid edit creates nothing. The one-argument call keeps working through the default.
-- Base body: 20261210100000_coach_plan_sessions.sql (save_coach_block).

drop function public.save_coach_block(uuid);

-- ---------------------------------------------------------------------------
-- coach_apply_block_edits: the write save_coach_block cannot do as the caller (coach_plan_sessions is read-only for
-- them). Filters by auth.uid(). Granted to authenticated only because save_coach_block (security invoker) must call
-- it; it refuses any call that does not come from save_coach_block (transaction-local flag).
-- Rules: the target slot exists; the replacement is an alternative of the exercise currently in that slot and is not
-- already in the session; for several edits of one slot the last wins. Sets, rep range, rest and reason are kept.
-- ---------------------------------------------------------------------------
create function public.coach_apply_block_edits(p_plan_id uuid, p_edits jsonb)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_edit record;
  v_cur  uuid;
  v_exs  jsonb;
  v_dedup jsonb;
begin
  if coalesce(current_setting('app.coach_block_save', true), '') <> 'on' then
    raise exception 'coach_apply_block_edits is internal' using errcode = '42501';
  end if;
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_edits is null or jsonb_typeof(p_edits) <> 'array' then
    raise exception 'invalid edits' using errcode = '22023';
  end if;
  if jsonb_array_length(p_edits) = 0 then
    return;
  end if;
  if not exists (select 1 from public.coach_plans p where p.id = p_plan_id and p.user_id = v_uid) then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_edits) e(value)
    where jsonb_typeof(e.value) <> 'object'
       or jsonb_typeof(e.value->'session_index') is distinct from 'number'
       or jsonb_typeof(e.value->'position') is distinct from 'number'
       or jsonb_typeof(e.value->'exercise_id') is distinct from 'string'
       or (e.value->>'exercise_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       or (e.value->>'session_index') !~ '^[0-9]{1,3}$'
       or (e.value->>'position') !~ '^[0-9]{1,3}$') then
    raise exception 'invalid edit' using errcode = '22023';
  end if;

  -- Last edit of each slot wins.
  select coalesce(jsonb_agg(jsonb_build_object('s', q.session_index, 'p', q.position, 'x', q.exercise_id)), '[]'::jsonb)
  into v_dedup
  from (
    select distinct on (session_index, position) session_index, position, exercise_id
    from (
      select (e.value->>'session_index')::int as session_index,
             (e.value->>'position')::int as position,
             (e.value->>'exercise_id')::uuid as exercise_id,
             e.ordinality as ord
      from jsonb_array_elements(p_edits) with ordinality e(value, ordinality)
    ) q0
    order by session_index, position, ord desc
  ) q;

  if exists (select 1 from jsonb_array_elements(v_dedup) d(value)
             group by d.value->>'s', d.value->>'x' having count(*) > 1) then
    raise exception 'a replacement is used twice in a session' using errcode = '22023';
  end if;

  -- Validate every edit against the stored (unedited) week 1 before changing anything.
  for v_edit in select (d.value->>'s')::int as session_index, (d.value->>'p')::int as position, (d.value->>'x')::uuid as exercise_id
                from jsonb_array_elements(v_dedup) d(value) loop
    select s.exercises into v_exs from public.coach_plan_sessions s
      where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = 1 and s.session_index = v_edit.session_index;
    if not found or v_edit.position < 1 or v_edit.position > jsonb_array_length(v_exs) then
      raise exception 'edit targets a missing slot' using errcode = '22023';
    end if;
    v_cur := (v_exs->(v_edit.position - 1)->>'exercise_id')::uuid;
    if not exists (select 1 from public.exercise_alternatives a
                   where (a.exercise_id = v_cur and a.alternative_id = v_edit.exercise_id)
                      or (a.alternative_id = v_cur and a.exercise_id = v_edit.exercise_id)) then
      raise exception 'replacement is not an alternative' using errcode = '22023';
    end if;
    if exists (select 1 from jsonb_array_elements(v_exs) x(value) where (x.value->>'exercise_id')::uuid = v_edit.exercise_id) then
      raise exception 'replacement already in the session' using errcode = '22023';
    end if;
  end loop;

  -- Change only the exercise (id and key) in every week of the session.
  for v_edit in select (d.value->>'s')::int as session_index, (d.value->>'p')::int as position, (d.value->>'x')::uuid as exercise_id
                from jsonb_array_elements(v_dedup) d(value) loop
    update public.coach_plan_sessions s
      set exercises = jsonb_set(
            jsonb_set(s.exercises, array[(v_edit.position - 1)::text, 'exercise_id'], to_jsonb(v_edit.exercise_id::text)),
            array[(v_edit.position - 1)::text, 'exercise_key'],
            to_jsonb((select e.key from public.exercises e where e.id = v_edit.exercise_id)))
      where s.plan_id = p_plan_id and s.user_id = v_uid and s.session_index = v_edit.session_index;
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- save_coach_block: confirmation of the block. One folder and one routine per session, created through the existing
-- save_routine (caller's RLS applies). Idempotent: a second call returns the same folder and routines and ignores
-- the edits. Edits are applied (all weeks) before the routines are created.
-- ---------------------------------------------------------------------------
create function public.save_coach_block(p_plan_id uuid, p_edits jsonb default '[]'::jsonb)
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

  perform set_config('app.coach_block_save', 'on', true);
  perform public.coach_apply_block_edits(p_plan_id, coalesce(p_edits, '[]'::jsonb));
  perform set_config('app.coach_block_save', 'off', true);

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

revoke execute on function public.coach_apply_block_edits(uuid, jsonb) from public, anon;
revoke execute on function public.save_coach_block(uuid, jsonb) from public, anon;
grant execute on function public.coach_apply_block_edits(uuid, jsonb) to authenticated;
grant execute on function public.save_coach_block(uuid, jsonb) to authenticated;
