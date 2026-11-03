-- T-207 (E13, US-95 data side, US-93 "from a folder"): the review of a routine folder and a copy of it with the
-- accepted changes.
--   (1) coach_folder_reviews: one stored review per call (purpose 'review' or 'block'), owner-only through RLS.
--   (2) coach_folder_for_review: the folder's current routines in the save_routine shape plus a content version.
--   (3) save_folder_review: stores a review (the Edge Function calls it with the user's token).
--   (4) coach_apply_change: applies ONE typed change to a routine (mirrored by applyChange in
--       supabase/functions/coach/folderChanges.ts; both are covered by the same before/ops/after fixture).
--   (5) apply_folder_review_copy: builds a COPY of the folder with the chosen changes. The source rows are only read.
-- Change ops (closed set), all addressed by routine_index (1-based) and position (1-based ordinal of the exercise in
-- the routine AS STORED IN THE FOLDER, stable while several changes are applied):
--   add_exercise {routine_index, exercise_key, sets: [{n, type, rep_min, rep_max, rpe, rest_seconds}]} (appended)
--   replace_exercise {routine_index, position, exercise_key} (keeps sets and range; clears the weights and the note)
--   remove_exercise {routine_index, position} (a superset partner left alone loses the group)
--   set_sets {routine_index, position, n}, set_rep_range {.., rep_min, rep_max}, set_rpe {.., rpe}, set_rest {.., rest_seconds}
-- A change whose target no longer exists (an earlier change removed it) is skipped.
-- The suffix of the copy folder name is the constant in apply_folder_review_copy (v_suffix).

-- ---------------------------------------------------------------------------
-- (1) Table
-- ---------------------------------------------------------------------------
create table public.coach_folder_reviews (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users (id) on delete cascade default auth.uid(),
  folder_id  uuid not null references public.routine_folders (id) on delete cascade,
  purpose    text not null check (purpose in ('review', 'block')),
  plan_id    uuid references public.coach_plans (id) on delete cascade,
  input_hash text not null,
  result     jsonb not null check (jsonb_typeof(result) = 'object'),
  created_at timestamptz not null default now(),
  constraint coach_folder_reviews_plan_purpose check ((purpose = 'block') = (plan_id is not null))
);

create index coach_folder_reviews_user_folder_idx
  on public.coach_folder_reviews (user_id, folder_id, created_at desc);
create index coach_folder_reviews_folder_idx on public.coach_folder_reviews (folder_id);
create index coach_folder_reviews_plan_idx on public.coach_folder_reviews (plan_id) where plan_id is not null;

alter table public.coach_folder_reviews enable row level security;
alter table public.coach_folder_reviews force row level security;

create policy coach_folder_reviews_select_own on public.coach_folder_reviews
  for select to authenticated
  using (user_id = (select auth.uid()));

-- The folder and plan subqueries run under RLS: another user's folder or plan cannot be referenced.
create policy coach_folder_reviews_insert_own on public.coach_folder_reviews
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.routine_folders f where f.id = folder_id)
    and (plan_id is null or exists (select 1 from public.coach_plans p where p.id = plan_id)));

create policy coach_folder_reviews_delete_own on public.coach_folder_reviews
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_folder_reviews from anon, authenticated;
grant select, insert, delete on table public.coach_folder_reviews to authenticated;

-- ---------------------------------------------------------------------------
-- (2) coach_folder_for_review
-- ---------------------------------------------------------------------------
-- {folder_id, name, routines: [{id, name, notes, track_as_routine, exercises: [...]}], version}. Every exercise is
-- already a save_routine exercise (exercise_id, superset_group, rest_seconds, rep_min, rep_max, target_rpe, notes,
-- note_kind, sets[{reps, set_type, weight_kg, duration_s}]) plus position (1-based) and exercise_key. `version` is
-- the md5 of the routines: it changes when the user edits the folder, so a stored review can tell it is stale.
create function public.coach_folder_for_review(p_folder uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := (select auth.uid());
  v_name     text;
  v_routines jsonb;
begin
  if v_uid is null or p_folder is null then
    return null;
  end if;
  select f.name into v_name from public.routine_folders f where f.id = p_folder and f.user_id = v_uid;
  if not found then
    return null;
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
      'id', r.id,
      'name', r.name,
      'notes', r.notes,
      'track_as_routine', r.track_as_routine,
      'exercises', coalesce((
        select jsonb_agg(jsonb_build_object(
            'position', x.pos,
            'exercise_id', x.exercise_id,
            'exercise_key', x.xkey,
            'superset_group', x.superset_group,
            'rest_seconds', x.rest_seconds,
            'rep_min', x.rep_min,
            'rep_max', x.rep_max,
            'target_rpe', x.target_rpe,
            'notes', x.notes,
            'note_kind', x.note_kind,
            'sets', coalesce((
              select jsonb_agg(jsonb_build_object(
                  'reps', s.target_reps,
                  'set_type', s.set_type,
                  'weight_kg', s.target_weight_kg,
                  'duration_s', s.target_duration_s) order by s.set_index)
              from public.routine_exercise_sets s
              where s.routine_exercise_id = x.id), '[]'::jsonb)) order by x.pos)
        from (
          select re.id, re.exercise_id, re.superset_group, re.rest_seconds, re.rep_min, re.rep_max, re.target_rpe,
                 re.notes, re.note_kind, coalesce(e.key, e.id::text) as xkey,
                 (row_number() over (order by re.position))::int as pos
          from public.routine_exercises re
          join public.exercises e on e.id = re.exercise_id
          where re.routine_id = r.id) x), '[]'::jsonb)) order by r.position, r.name), '[]'::jsonb)
  into v_routines
  from public.routines r
  where r.folder_id = p_folder and r.user_id = v_uid and not r.archived;

  return jsonb_build_object(
    'folder_id', p_folder,
    'name', v_name,
    'routines', v_routines,
    'version', md5(v_routines::text));
end;
$$;

revoke execute on function public.coach_folder_for_review(uuid) from public, anon;
grant execute on function public.coach_folder_for_review(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- (3) save_folder_review
-- ---------------------------------------------------------------------------
create function public.save_folder_review(
  p_folder  uuid,
  p_purpose text,
  p_plan    uuid,
  p_hash    text,
  p_result  jsonb
)
returns uuid
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_id  uuid;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_purpose is null or p_purpose not in ('review', 'block')
     or (p_purpose = 'block') <> (p_plan is not null)
     or p_hash is null or btrim(p_hash) = ''
     or p_result is null or jsonb_typeof(p_result) <> 'object' then
    raise exception 'invalid folder review' using errcode = '22023';
  end if;
  if p_folder is null
     or not exists (select 1 from public.routine_folders f where f.id = p_folder and f.user_id = v_uid) then
    raise exception 'folder not found' using errcode = '42501';
  end if;
  if p_plan is not null
     and not exists (select 1 from public.coach_plans p where p.id = p_plan and p.user_id = v_uid) then
    raise exception 'plan not found' using errcode = '42501';
  end if;

  insert into public.coach_folder_reviews (folder_id, purpose, plan_id, input_hash, result)
  values (p_folder, p_purpose, p_plan, p_hash, p_result)
  returning id into v_id;
  return v_id;
end;
$$;

revoke execute on function public.save_folder_review(uuid, text, uuid, text, jsonb) from public, anon;
grant execute on function public.save_folder_review(uuid, text, uuid, text, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- (4) coach_apply_change
-- ---------------------------------------------------------------------------
create function public.coach_apply_change(p_routine jsonb, p_change jsonb)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_op     text := p_change ->> 'op';
  v_exs    jsonb := coalesce(p_routine -> 'exercises', '[]'::jsonb);
  v_pos    int;
  v_target jsonb;
  v_new    jsonb;
  v_rest   jsonb;
  v_id     uuid;
  v_sets   jsonb;
  v_base   jsonb;
  v_first  jsonb;
  v_len    int;
  v_n      int;
  v_rmin   int;
  v_rmax   int;
  v_group  jsonb;
begin
  begin
    v_pos := nullif(p_change ->> 'position', '')::int;
  exception when invalid_text_representation or numeric_value_out_of_range then
    return p_routine;
  end;

  if v_op = 'add_exercise' then
    if jsonb_typeof(p_change -> 'sets') is distinct from 'array' or jsonb_array_length(p_change -> 'sets') = 0 then
      return p_routine;
    end if;
    select e.id into v_id from public.exercises e where coalesce(e.key, e.id::text) = p_change ->> 'exercise_key' limit 1;
    if v_id is null then
      return p_routine;
    end if;
    select min((s.value ->> 'rep_min')::int), max((s.value ->> 'rep_max')::int) into v_rmin, v_rmax
    from jsonb_array_elements(p_change -> 'sets') s;
    select s.value into v_first
    from jsonb_array_elements(p_change -> 'sets') with ordinality s(value, o)
    where coalesce(s.value ->> 'type', 'working') = 'working'
    order by s.o limit 1;
    v_first := coalesce(v_first, p_change -> 'sets' -> 0);
    select jsonb_agg(jsonb_build_object('reps', (s.value ->> 'rep_min')::int,
                                        'set_type', coalesce(s.value ->> 'type', 'working'))
                     order by s.o, g.i)
    into v_sets
    from jsonb_array_elements(p_change -> 'sets') with ordinality s(value, o),
         generate_series(1, (s.value ->> 'n')::int) g(i);
    v_new := jsonb_build_object(
      'exercise_id', v_id,
      'exercise_key', p_change ->> 'exercise_key',
      'rest_seconds', (v_first ->> 'rest_seconds')::int,
      'rep_min', v_rmin,
      'rep_max', v_rmax,
      'target_rpe', (v_first ->> 'rpe')::numeric,
      'sets', v_sets);
    return p_routine || jsonb_build_object('exercises', v_exs || jsonb_build_array(v_new));
  end if;

  select e.value into v_target from jsonb_array_elements(v_exs) e(value) where (e.value ->> 'position')::int = v_pos;
  if v_target is null then
    return p_routine;
  end if;

  if v_op = 'remove_exercise' then
    select coalesce(jsonb_agg(e.value order by e.o), '[]'::jsonb) into v_rest
    from jsonb_array_elements(v_exs) with ordinality e(value, o)
    where (e.value ->> 'position')::int <> v_pos;
    v_group := v_target -> 'superset_group';
    if jsonb_typeof(v_group) = 'number'
       and (select count(*) from jsonb_array_elements(v_rest) e where e.value -> 'superset_group' = v_group) = 1 then
      select coalesce(jsonb_agg(case when e.value -> 'superset_group' = v_group
                                     then e.value || '{"superset_group":null}'::jsonb else e.value end order by e.o),
                      '[]'::jsonb)
      into v_rest
      from jsonb_array_elements(v_rest) with ordinality e(value, o);
    end if;
    return p_routine || jsonb_build_object('exercises', v_rest);
  end if;

  if v_op = 'replace_exercise' then
    select e.id into v_id from public.exercises e where coalesce(e.key, e.id::text) = p_change ->> 'exercise_key' limit 1;
    if v_id is null then
      return p_routine;
    end if;
    select coalesce(jsonb_agg(s.value || '{"weight_kg":null}'::jsonb order by s.o), '[]'::jsonb) into v_sets
    from jsonb_array_elements(v_target -> 'sets') with ordinality s(value, o);
    v_new := v_target || jsonb_build_object(
      'exercise_id', v_id, 'exercise_key', p_change ->> 'exercise_key',
      'notes', null, 'note_kind', null, 'sets', v_sets);
  elsif v_op = 'set_sets' then
    v_n := (p_change ->> 'n')::int;
    v_sets := v_target -> 'sets';
    v_len := jsonb_array_length(v_sets);
    if v_n is null or v_n not between 1 and 10 then
      return p_routine;
    end if;
    if v_n < v_len then
      select jsonb_agg(s.value order by s.o) into v_sets
      from jsonb_array_elements(v_sets) with ordinality s(value, o) where s.o <= v_n;
    elsif v_n > v_len then
      -- New sets copy the first working set (or the first set).
      select s.value into v_base
      from jsonb_array_elements(v_sets) with ordinality s(value, o)
      where s.value ->> 'set_type' = 'working' order by s.o limit 1;
      v_base := coalesce(v_base, v_sets -> 0);
      if v_base is null then
        return p_routine;
      end if;
      v_sets := v_sets || (select jsonb_agg(v_base || '{"set_type":"working"}'::jsonb)
                           from generate_series(1, v_n - v_len));
    end if;
    v_new := v_target || jsonb_build_object('sets', v_sets);
  elsif v_op = 'set_rep_range' then
    v_rmin := (p_change ->> 'rep_min')::int;
    v_rmax := (p_change ->> 'rep_max')::int;
    if v_rmin is null or v_rmax is null or v_rmin > v_rmax or v_rmin not between 1 and 50 or v_rmax not between 1 and 50 then
      return p_routine;
    end if;
    select coalesce(jsonb_agg(case when jsonb_typeof(s.value -> 'reps') = 'number'
                                   then s.value || jsonb_build_object('reps', v_rmin) else s.value end order by s.o),
                    '[]'::jsonb)
    into v_sets
    from jsonb_array_elements(v_target -> 'sets') with ordinality s(value, o);
    v_new := v_target || jsonb_build_object('rep_min', v_rmin, 'rep_max', v_rmax, 'sets', v_sets);
  elsif v_op = 'set_rpe' then
    if (p_change ->> 'rpe') is null then
      return p_routine;
    end if;
    v_new := v_target || jsonb_build_object('target_rpe', (p_change ->> 'rpe')::numeric);
  elsif v_op = 'set_rest' then
    if (p_change ->> 'rest_seconds') is null then
      return p_routine;
    end if;
    v_new := v_target || jsonb_build_object('rest_seconds', (p_change ->> 'rest_seconds')::int);
  else
    return p_routine;
  end if;

  select coalesce(jsonb_agg(case when (e.value ->> 'position')::int = v_pos then v_new else e.value end order by e.o),
                  '[]'::jsonb)
  into v_rest
  from jsonb_array_elements(v_exs) with ordinality e(value, o);
  return p_routine || jsonb_build_object('exercises', v_rest);
end;
$$;

revoke execute on function public.coach_apply_change(jsonb, jsonb) from public, anon;
grant execute on function public.coach_apply_change(jsonb, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- (5) apply_folder_review_copy
-- ---------------------------------------------------------------------------
-- A review of purpose 'review' + the chosen change ids -> a new folder "<name> (revisada)" (de-duplicated with " 2",
-- " 3") whose routines are the source routines with those changes. An empty list is a plain copy. Errors: 55000 when
-- the review is not the caller's, is not a 'review' or the source folder changed since; 22023 for an unknown id.
create function public.apply_folder_review_copy(p_review uuid, p_change_ids text[])
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_suffix  constant text := ' (revisada)';
  v_rev     public.coach_folder_reviews;
  v_src     jsonb;
  v_ids     text[] := coalesce(p_change_ids, '{}');
  v_changes jsonb;
  v_r       record;
  v_c       record;
  v_routine jsonb;
  v_base    text;
  v_fname   text;
  v_name    text;
  v_n       integer;
  v_folder  uuid;
  v_rid     uuid;
  v_rids    uuid[] := array[]::uuid[];
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  select * into v_rev from public.coach_folder_reviews r
  where r.id = p_review and r.user_id = v_uid and r.purpose = 'review';
  if not found then
    raise exception 'review not found' using errcode = '55000';
  end if;
  v_changes := coalesce(v_rev.result -> 'changes', '[]'::jsonb);
  if exists (select 1 from unnest(v_ids) i(id)
             where not exists (select 1 from jsonb_array_elements(v_changes) c(value) where c.value ->> 'id' = i.id)) then
    raise exception 'unknown change id' using errcode = '22023';
  end if;

  v_src := public.coach_folder_for_review(v_rev.folder_id);
  if v_src is null or v_src ->> 'version' is distinct from v_rev.result ->> 'folder_version' then
    raise exception 'folder changed since the review' using errcode = '55000';
  end if;

  v_base := left(v_src ->> 'name', 80) || v_suffix;
  v_fname := v_base;
  v_n := 1;
  while exists (select 1 from public.routine_folders f
                where f.user_id = v_uid and lower(btrim(f.name)) = lower(v_fname)) loop
    v_n := v_n + 1;
    v_fname := v_base || ' ' || v_n;
  end loop;
  v_folder := public.create_routine_folder(v_fname);

  for v_r in
    select r.value, r.ordinality from jsonb_array_elements(v_src -> 'routines') with ordinality r(value, ordinality)
    order by r.ordinality
  loop
    v_routine := v_r.value;
    for v_c in
      select c.value from jsonb_array_elements(v_changes) with ordinality c(value, ordinality)
      where c.value ->> 'id' = any (v_ids) and (c.value ->> 'routine_index')::int = v_r.ordinality
      order by c.ordinality
    loop
      v_routine := public.coach_apply_change(v_routine, v_c.value);
    end loop;

    -- Routine names are unique per user: add a number when the name is taken (as save_coach_block).
    v_name := v_r.value ->> 'name';
    v_n := 1;
    while exists (select 1 from public.routines x
                  where x.user_id = v_uid and not x.archived and lower(btrim(x.name)) = lower(btrim(v_name))) loop
      v_n := v_n + 1;
      v_name := left(v_r.value ->> 'name', 90) || ' ' || v_n;
    end loop;

    v_rid := public.save_routine(jsonb_build_object(
      'name', v_name,
      'notes', v_routine -> 'notes',
      'track_as_routine', v_routine -> 'track_as_routine',
      'exercises', coalesce((
        select jsonb_agg(e.value - 'position' - 'exercise_key' order by e.ordinality)
        from jsonb_array_elements(v_routine -> 'exercises') with ordinality e(value, ordinality)), '[]'::jsonb)));
    perform public.move_routine_to_folder(v_rid, v_folder);
    v_rids := v_rids || v_rid;
  end loop;

  return jsonb_build_object('folder_id', v_folder, 'routine_ids', to_jsonb(v_rids));
end;
$$;

revoke execute on function public.apply_folder_review_copy(uuid, text[]) from public, anon;
grant execute on function public.apply_folder_review_copy(uuid, text[]) to authenticated;
