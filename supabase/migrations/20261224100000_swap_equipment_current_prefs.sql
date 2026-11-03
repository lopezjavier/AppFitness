-- T-209 (US-72, ADR-83, ADR-92): Trainer swaps stay inside the user's CURRENT equipment preferences.
-- "Similares" (exercise_alternatives) is unfiltered catalogue browsing (user decision, 2026-11-03).
-- Bases: exercise_alternatives and coach_apply_block_edits 20261217100200 (save_coach_block is unchanged: it
-- delegates the edit validation to coach_apply_block_edits).

-- ---------------------------------------------------------------------------
-- coach_user_equipment: the caller's selected equipment, resolved the way the plan does.
-- Returns null when everything is allowed (gym or no place with none selected, or no preferences row).
-- Nothing selected at home / elsewhere means bodyweight only.
-- ---------------------------------------------------------------------------
create function public.coach_user_equipment()
returns text[]
language sql
stable
security invoker
set search_path = ''
as $$
  select case
           when cardinality(t.equipment) > 0 then t.equipment::text[]
           when t.place in ('home', 'other') then array['bodyweight']
           else null
         end
  from public.training_preferences t
  where t.user_id = (select auth.uid())
$$;

-- equipment_allowed: bodyweight and the keys walking / running are always allowed; a null selection allows all.
create function public.equipment_allowed(p_equipment text, p_key text, p_selected text[])
returns boolean
language sql
immutable
security invoker
set search_path = ''
as $$
  select p_selected is null
      or p_equipment = 'bodyweight'
      or coalesce(p_key, '') in ('walking', 'running')
      or p_equipment = any (p_selected)
$$;

revoke execute on function public.coach_user_equipment() from public, anon;
revoke execute on function public.equipment_allowed(text, text, text[]) from public, anon;
grant execute on function public.coach_user_equipment() to authenticated;
grant execute on function public.equipment_allowed(text, text, text[]) to authenticated;

-- ---------------------------------------------------------------------------
-- exercise_alternatives: no equipment filter. A cardio-duration exercise has every eligible cardio exercise as
-- alternative (any to any); the curated pairs are read for the rest.
-- ---------------------------------------------------------------------------
create or replace function public.exercise_alternatives(p_exercise_id uuid)
returns table (exercise_id uuid, similarity numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with me as (
    select 1 as one
    from public.exercises e
    where e.id = p_exercise_id and e.movement_pattern = 'cardio'
      and e.metric_type in ('duration', 'distance_duration')
  ),
  cardio as (
    select o.exercise_id as id, 1::numeric as similarity, o.key
    from me
    cross join public.coach_cardio_options('{}'::text[]) o
    where o.exercise_id <> p_exercise_id
  ),
  pairs as (
    select x.id, x.similarity, e.key
    from (
      select a.alternative_id as id, a.similarity
        from public.exercise_alternatives a where a.exercise_id = p_exercise_id
      union all
      select a.exercise_id as id, a.similarity
        from public.exercise_alternatives a where a.alternative_id = p_exercise_id
    ) x
    join public.exercises e on e.id = x.id
    where not exists (select 1 from me)
  )
  select u.id, u.similarity
  from (select * from cardio union all select * from pairs) u
  order by u.similarity desc, u.key
$$;

revoke execute on function public.exercise_alternatives(uuid) from public, anon;
grant execute on function public.exercise_alternatives(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_apply_block_edits: as before, but every replacement (cardio or lifting) must also be inside the caller's
-- CURRENT equipment preferences (coach_user_equipment), not the equipment stored in the plan.
-- ---------------------------------------------------------------------------
create or replace function public.coach_apply_block_edits(p_plan_id uuid, p_edits jsonb)
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
  v_equipment text[];
  v_new_eq text;
  v_new_key text;
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
  -- Current preferences (null = everything allowed).
  v_equipment := public.coach_user_equipment();
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
    select e.equipment::text, e.key into v_new_eq, v_new_key from public.exercises e where e.id = v_edit.exercise_id;
    if v_exs->(v_edit.position - 1)->>'role' = 'cardio' then
      if not exists (select 1 from public.coach_cardio_options('{}'::text[]) o where o.exercise_id = v_edit.exercise_id) then
        raise exception 'replacement is not a cardio option' using errcode = '22023';
      end if;
    else
      if exists (select 1 from public.exercises e where e.id = v_edit.exercise_id and e.movement_pattern = 'cardio') then
        raise exception 'cardio cannot replace a lifting exercise' using errcode = '22023';
      end if;
      if not exists (select 1 from public.exercise_alternatives a
                     where (a.exercise_id = v_cur and a.alternative_id = v_edit.exercise_id)
                        or (a.alternative_id = v_cur and a.exercise_id = v_edit.exercise_id)) then
        raise exception 'replacement is not an alternative' using errcode = '22023';
      end if;
    end if;
    if not public.equipment_allowed(v_new_eq, v_new_key, v_equipment) then
      raise exception 'replacement is outside the equipment' using errcode = '22023';
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

revoke execute on function public.coach_apply_block_edits(uuid, jsonb) from public, anon;
grant execute on function public.coach_apply_block_edits(uuid, jsonb) to authenticated;
