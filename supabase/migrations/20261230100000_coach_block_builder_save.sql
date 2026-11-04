-- T-221 (E13, US-72, US-93, US-87, ADR-99): the device builds the block, the server stays the gate for the structural
-- invariants and saves it.
--   (1) coach_assert_block: no h6_excluded_discomfort check (ADR-99: discomfort is not an input of the builder), the
--       exercise 'reason' is optional (10 characters minimum when present), and training.source = 'folder' (a block
--       made from the user's own folder) skips the H4 rep-range and H6 rest bounds, which the folder review does not
--       judge. All structural checks stay. Patched in place from the stored definition (as 20261229100300 does) and
--       the patch fails loudly if an expected text is missing.
--   (2) save_coach_block: optional folder name; the routine note is the reason only when there is one.
--   (3) save_built_coach_block: attach_coach_block + save_coach_block in one transaction.
--   (4) coach_builder_inputs: 8 weeks of history per exercise, cardio options and the default cardio exercise.
-- Bases (never edited): coach_assert_block 20261227100300 + 20261229100300, save_coach_block 20261227100300.

-- ---------------------------------------------------------------------------
-- (1) coach_assert_block
-- ---------------------------------------------------------------------------
create function pg_temp.patch_once(p_def text, p_from text, p_to text, p_all boolean default false)
returns text
language plpgsql
as $$
begin
  if position(p_from in p_def) = 0 then
    raise exception 'coach_assert_block patch did not apply: %', left(p_from, 60);
  end if;
  return replace(p_def, p_from, p_to);
end
$$;

do $patch$
declare
  v_def text := pg_get_functiondef('public.coach_assert_block(uuid, jsonb, jsonb)'::regprocedure);
  v_new text;
begin
  v_new := pg_temp.patch_once(v_def,
    E'      if v_id = any (v_unres) then\n        raise exception ''block invariant: h6_excluded_discomfort'' using errcode = ''22023'';\n      end if;\n',
    '');
  -- The unresolved-discomfort list is not read any more.
  v_new := pg_temp.patch_once(v_new, E'  v_unres     uuid[];
', '');
  v_new := pg_temp.patch_once(v_new,
    E'  select coalesce(array_agg(x.id), ''{}'') into v_unres from public.unresolved_severe_discomforts(p_uid) x(id);

',
    '');
  v_new := pg_temp.patch_once(v_new,
    'jsonb_typeof(v_ex.value->''reason'') is distinct from ''string''',
    '(v_ex.value ? ''reason'' and jsonb_typeof(v_ex.value->''reason'') is distinct from ''string'')');
  v_new := pg_temp.patch_once(v_new,
    'if char_length(v_reason) < 10 then',
    'if v_reason is not null and char_length(v_reason) < 10 then');
  v_new := pg_temp.patch_once(v_new,
    E'  v_group     text;\nbegin',
    E'  v_group     text;\n  v_folder    boolean := coalesce(p_training->>''source'' = ''folder'', false);\nbegin');
  v_new := pg_temp.patch_once(v_new,
    'if v_rest not between 120 and 180 then',
    'if (not v_folder and v_rest not between 120 and 180) or (v_folder and v_rest not between 0 and 600) then');
  v_new := pg_temp.patch_once(v_new,
    'if v_rest not between 60 and 120 then',
    'if (not v_folder and v_rest not between 60 and 120) or (v_folder and v_rest not between 0 and 600) then');
  -- A folder block keeps its own rep ranges: only the structural 1-50 bounds apply (null intention).
  v_new := pg_temp.patch_once(v_new,
    'public.coach_block_rep_ok(v_intention, v_main,',
    'public.coach_block_rep_ok(case when v_folder then null else v_intention end, v_main,');
  execute v_new;
end
$patch$;

revoke execute on function public.coach_assert_block(uuid, jsonb, jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- (2) save_coach_block(p_plan_id, p_edits, p_folder_name)
-- ---------------------------------------------------------------------------
drop function public.save_coach_block(uuid, jsonb);

create function public.save_coach_block(
  p_plan_id uuid,
  p_edits jsonb default '[]'::jsonb,
  p_folder_name text default null
)
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

  v_folder := public.create_routine_folder(
    coalesce(nullif(btrim(p_folder_name), ''), 'Bloque ' || to_char(v_plan.block_start, 'DD/MM/YYYY')));

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
             case when e.value->>'role' = 'cardio' then
               jsonb_build_object(
                 'exercise_id', e.value->>'exercise_id',
                 'rest_seconds', 0,
                 'notes', nullif(btrim(e.value->>'reason'), ''),
                 'note_kind', case when nullif(btrim(e.value->>'reason'), '') is not null then 'other' else null end,
                 'sets', jsonb_build_array(jsonb_build_object(
                           'duration_s', (e.value->>'duration_min')::int * 60, 'set_type', 'working')))
             else
               jsonb_build_object(
                 'exercise_id', e.value->>'exercise_id',
                 'rest_seconds', (e.value->>'rest_seconds')::int,
                 'rep_min', (e.value->>'rep_min')::int,
                 'rep_max', (e.value->>'rep_max')::int,
                 'target_rpe', e.value->'target_rpe',
                 'superset_group', e.value->'superset_group',
                 'notes', coalesce(nullif(btrim(e.value->>'technique_note'), ''), nullif(btrim(e.value->>'reason'), '')),
                 'note_kind', case when nullif(btrim(e.value->>'technique_note'), '') is not null then 'technique'
                                   when nullif(btrim(e.value->>'reason'), '') is not null then 'other'
                                   else null end,
                 -- One set per planned set, at the bottom of the rep range; a drop set has no planned weight.
                 'sets', case
                   when jsonb_typeof(e.value->'sets') = 'array' then
                     (select jsonb_agg(jsonb_build_object(
                               'reps', coalesce((s.value->>'rep_min')::int, (e.value->>'rep_min')::int),
                               'set_type', coalesce(s.value->>'type', 'working'),
                               'weight_kg', case when coalesce(s.value->>'type', 'working') = 'working'
                                                 then e.value->'planned_weight_kg' else null end)
                             order by s.ordinality)
                      from jsonb_array_elements(e.value->'sets') with ordinality s(value, ordinality))
                   else
                     to_jsonb(array_fill(
                       jsonb_build_object('reps', (e.value->>'rep_min')::int, 'set_type', 'working',
                                          'weight_kg', e.value->'planned_weight_kg'),
                       array[(e.value->>'sets')::int]))
                 end)
             end as obj
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

revoke execute on function public.save_coach_block(uuid, jsonb, text) from public, anon;
grant execute on function public.save_coach_block(uuid, jsonb, text) to authenticated;

-- ---------------------------------------------------------------------------
-- (3) save_built_coach_block: store the device-built block and save it as a folder, atomically. A second call on a
-- plan already saved returns the saved folder (same as save_coach_block).
-- ---------------------------------------------------------------------------
create function public.save_built_coach_block(
  p_plan_id uuid,
  p_training jsonb,
  p_sessions jsonb,
  p_folder_name text default null
)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not exists (select 1 from public.coach_plans p
                 where p.id = p_plan_id and p.user_id = v_uid
                   and (p.folder_id is not null
                        or exists (select 1 from public.coach_plan_sessions s
                                   where s.plan_id = p.id and s.routine_id is not null))) then
    perform public.attach_coach_block(
      p_plan_id, p_training, p_sessions,
      jsonb_build_object('source', case when jsonb_typeof(p_training) = 'object'
                                        then coalesce(p_training->>'source', 'builder') else 'builder' end),
      '{}'::jsonb);
  end if;
  return public.save_coach_block(p_plan_id, '[]'::jsonb, p_folder_name);
end;
$$;

revoke execute on function public.save_built_coach_block(uuid, jsonb, jsonb, text) from public, anon;
grant execute on function public.save_built_coach_block(uuid, jsonb, jsonb, text) to authenticated;

-- ---------------------------------------------------------------------------
-- (4) coach_builder_inputs: what the builder needs besides the catalogue. Cardio default: the most-logged cardio of
-- the last 8 weeks that is inside the options, else stationary bike, elliptical, incline walk, rowing machine,
-- walking (same ranking as supabase/functions/coach/candidates.ts, deleted in T-224).
-- ---------------------------------------------------------------------------
create function public.coach_builder_inputs(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_equip   text[];
  v_options uuid[];
  v_default uuid;
  v_history jsonb;
begin
  select coalesce(array_agg(e::text), '{}') into v_equip
  from public.training_preferences tp, unnest(tp.equipment) e
  where tp.user_id = v_uid;

  select coalesce(array_agg(o.exercise_id order by o.key), '{}') into v_options
  from public.coach_cardio_options(v_equip) o;

  with sets as (
    select s.id as sid, st.exercise_id as eid, st.completed_at, st.weight_kg, st.reps, st.duration_s,
           e.movement_pattern::text as mp
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and st.set_type <> 'warm_up'
      and (s.started_at at time zone 'UTC')::date > p_today - 56
      and (s.started_at at time zone 'UTC')::date <= p_today
  ),
  lifts as (select * from sets where mp is distinct from 'cardio'),
  hist as (select eid, count(distinct sid) as n from lifts group by eid),
  lastset as (
    select distinct on (eid) eid, weight_kg, reps from lifts order by eid, completed_at desc
  )
  select coalesce(jsonb_agg(jsonb_build_object(
           'exercise_id', h.eid, 'sessions_8w', h.n,
           'last_weight_kg', l.weight_kg, 'last_reps', l.reps) order by h.n desc, h.eid), '[]'::jsonb)
  into v_history
  from hist h join lastset l on l.eid = h.eid;

  -- Logged cardio inside the options first (most sessions, then minutes, then key).
  select c.eid into v_default
  from (
    select eid, count(distinct sid) as n, coalesce(sum(duration_s), 0) as m
    from (
      select s.id as sid, st.exercise_id as eid, st.duration_s
      from public.workout_sessions s
      join public.workout_sets st on st.session_id = s.id
      join public.exercises e on e.id = st.exercise_id
      where s.user_id = v_uid and s.status = 'completed' and st.completed_at is not null
        and st.set_type <> 'warm_up' and e.movement_pattern::text = 'cardio'
        and (s.started_at at time zone 'UTC')::date > p_today - 56
        and (s.started_at at time zone 'UTC')::date <= p_today
    ) cs
    group by eid
  ) c
  join public.exercises x on x.id = c.eid
  where c.eid = any (v_options)
  order by c.n desc, c.m desc, x.key, c.eid
  limit 1;

  if v_default is null then
    select o.exercise_id into v_default
    from public.coach_cardio_options(v_equip) o
    where o.key in ('stationary-bike', 'elliptical-trainer', 'incline-treadmill-walk', 'rowing-machine', 'walking')
    order by array_position(
      array['stationary-bike', 'elliptical-trainer', 'incline-treadmill-walk', 'rowing-machine', 'walking'], o.key)
    limit 1;
  end if;

  return jsonb_build_object(
    'history', v_history,
    'cardio_options', to_jsonb(v_options),
    'cardio_default', to_jsonb(v_default));
end;
$$;

revoke execute on function public.coach_builder_inputs(date) from public, anon;
grant execute on function public.coach_builder_inputs(date) to authenticated;
