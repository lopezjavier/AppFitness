-- T-241 (US-108): follow-up of a level 4-5 discomfort: a swap offer for an exercise of the accepted block and the
-- one-time health note (14 days unresolved, or 3 level 4-5 rows). Rules: planning/04 section 4.12, planning/07 section 7.
-- The database is the source of truth for the 14-day clock (now()) and for "declined" / "dismissed" per discomfort row.
-- The swap is not undoable and writes no coach_changes row (its kind check is a closed list).

alter table public.exercise_discomforts
  add column swap_declined_at  timestamptz,
  add column note_dismissed_at timestamptz;

-- `level` stays immutable after insert; only these columns and resolved_at can change.
grant update (swap_declined_at, note_dismissed_at) on table public.exercise_discomforts to authenticated;

-- ---------------------------------------------------------------------------
-- coach_discomfort_followups: one row per exercise with an unresolved level >= 4 discomfort.
-- offer_swap: the exercise is in a routine of the accepted block's folder and the newest unresolved level >= 4 row
-- was not declined (a new level 4-5 log is a new discomfort and is offered again). show_note: 14 days since the oldest unresolved level >= 4 row (note_reason 'days14', wins when both
-- hold) or at least 3 such rows ('sessions3'), and none of them was dismissed. note_reason is null without a note.
-- ---------------------------------------------------------------------------
create function public.coach_discomfort_followups()
returns table (exercise_id uuid, discomfort_id uuid, logged_at timestamptz,
               offer_swap boolean, show_note boolean, note_reason text)
language sql
stable
security invoker
set search_path = ''
as $$
  with open_rows as (
    select d.id, d.exercise_id, d.logged_at, d.swap_declined_at, d.note_dismissed_at
    from public.exercise_discomforts d
    where d.user_id = (select auth.uid()) and d.resolved_at is null and d.level >= 4
  ),
  g as (
    select o.exercise_id,
           (array_agg(o.id order by o.logged_at desc, o.id desc))[1] as discomfort_id,
           min(o.logged_at) as logged_at,
           count(*) as n,
           (array_agg(o.swap_declined_at is not null order by o.logged_at desc, o.id desc))[1] as declined,
           bool_or(o.note_dismissed_at is not null) as dismissed
    from open_rows o
    group by o.exercise_id
  ),
  r as (
    select g.*,
           case when now() >= g.logged_at + interval '14 days' then 'days14'
                when g.n >= 3 then 'sessions3'
           end as reason
    from g
  )
  select r.exercise_id, r.discomfort_id, r.logged_at,
         (not r.declined and exists (
            select 1
            from public.coach_plans p
            join public.routines rt on rt.folder_id = p.folder_id and rt.user_id = p.user_id
            join public.routine_exercises re on re.routine_id = rt.id
            where p.user_id = (select auth.uid()) and p.status = 'accepted' and p.folder_id is not null
              and re.exercise_id = r.exercise_id)) as offer_swap,
         (r.reason is not null and not r.dismissed) as show_note,
         case when not r.dismissed then r.reason end as note_reason
  from r
  order by r.logged_at, r.exercise_id
$$;

-- ---------------------------------------------------------------------------
-- coach_swap_candidates: replacements for a flagged exercise of the accepted block. Same movement pattern, not cardio,
-- inside the CURRENT equipment (T-209), no unresolved level >= 4 discomfort of its own, not already in the block
-- folder. Favourites first, then curated similarity (0 without a pair), then name key. Empty when the flagged
-- exercise is not in the accepted block. Every statement filters by auth.uid() explicitly (also used by the swap).
-- ---------------------------------------------------------------------------
create function public.coach_swap_candidates(p_exercise_id uuid)
returns table (exercise_id uuid, is_favorite boolean, similarity numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select p.folder_id
    from public.coach_plans p
    where p.user_id = (select auth.uid()) and p.status = 'accepted' and p.folder_id is not null
      and exists (select 1
                  from public.routines rt
                  join public.routine_exercises re on re.routine_id = rt.id
                  where rt.user_id = p.user_id and rt.folder_id = p.folder_id and re.exercise_id = p_exercise_id)
    limit 1
  ),
  me as (
    select e.movement_pattern
    from public.exercises e
    where e.id = p_exercise_id and (not e.is_custom or e.created_by = (select auth.uid()))
  ),
  equipment as (select public.coach_user_equipment() as selected)
  select e.id,
         exists (select 1 from public.exercise_favorites f
                 where f.user_id = (select auth.uid()) and f.exercise_id = e.id),
         coalesce((select max(a.similarity)
                   from public.exercise_alternatives a
                   where (a.exercise_id = p_exercise_id and a.alternative_id = e.id)
                      or (a.alternative_id = p_exercise_id and a.exercise_id = e.id)), 0)::numeric
  from plan
  cross join me
  cross join equipment q
  join public.exercises e on e.movement_pattern = me.movement_pattern
  where e.id <> p_exercise_id
    and (not e.is_custom or e.created_by = (select auth.uid()))
    and e.movement_pattern <> 'cardio'
    and public.equipment_allowed(e.equipment::text, e.key, q.selected)
    and not exists (select 1 from public.exercise_discomforts d
                    where d.user_id = (select auth.uid()) and d.exercise_id = e.id
                      and d.resolved_at is null and d.level >= 4)
    and not exists (select 1
                    from public.routines rt
                    join public.routine_exercises re on re.routine_id = rt.id
                    where rt.user_id = (select auth.uid()) and rt.folder_id = plan.folder_id
                      and re.exercise_id = e.id)
  order by 2 desc, 3 desc, coalesce(e.name, e.key), e.id
$$;

-- ---------------------------------------------------------------------------
-- coach_swap_flagged_exercise: replaces the flagged exercise in every routine of the accepted block's folder,
-- keeping position, superset, set count and types, rep range, rest and RPE. Planned weights become the user's last
-- completed working weight of the new exercise, else null (never converted). The plan weeks snapshot is updated too.
-- Past workouts and logged sets are never rewritten. Security definer because coach_plan_sessions is read-only for
-- clients; every statement filters by auth.uid(). A second call finds nothing to swap and returns 0.
-- ---------------------------------------------------------------------------
create function public.coach_swap_flagged_exercise(p_exercise_id uuid, p_new_exercise_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_plan    public.coach_plans;
  v_weight  numeric;
  v_key     text;
  v_re      record;
  v_changed int := 0;
  v_last    uuid;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_exercise_id is null or p_new_exercise_id is null then
    raise exception 'exercise ids are required' using errcode = '22023';
  end if;

  select * into v_plan from public.coach_plans p
  where p.user_id = v_uid and p.status = 'accepted' and p.folder_id is not null;
  if not found then
    raise exception 'no accepted block' using errcode = 'P0002';
  end if;

  if not exists (select 1 from public.routines rt
                 join public.routine_exercises re on re.routine_id = rt.id
                 where rt.user_id = v_uid and rt.folder_id = v_plan.folder_id and re.exercise_id = p_exercise_id) then
    -- Already swapped: the replacement is in the block and the flagged one is gone.
    if exists (select 1 from public.routines rt
               join public.routine_exercises re on re.routine_id = rt.id
               where rt.user_id = v_uid and rt.folder_id = v_plan.folder_id and re.exercise_id = p_new_exercise_id) then
      return jsonb_build_object('routines_changed', 0);
    end if;
    raise exception 'exercise is not in the block' using errcode = 'P0002';
  end if;

  if not exists (select 1 from public.coach_swap_candidates(p_exercise_id) c where c.exercise_id = p_new_exercise_id) then
    raise exception 'replacement is not a candidate' using errcode = '22023';
  end if;

  select ws.weight_kg into v_weight
  from public.workout_sets ws
  join public.workout_sessions s on s.id = ws.session_id
  where s.user_id = v_uid and ws.exercise_id = p_new_exercise_id and ws.set_type = 'working'
    and ws.completed_at is not null and ws.weight_kg > 0
  order by ws.completed_at desc, ws.set_index desc
  limit 1;

  for v_re in
    select re.id, re.routine_id
    from public.routine_exercises re
    join public.routines rt on rt.id = re.routine_id
    where rt.user_id = v_uid and rt.folder_id = v_plan.folder_id and re.exercise_id = p_exercise_id
    order by re.routine_id, re.position
  loop
    update public.routine_exercises set exercise_id = p_new_exercise_id where id = v_re.id;
    -- Weights of the old exercise mean nothing for the new one: working sets get the known weight, the rest none.
    update public.routine_exercise_sets
    set target_weight_kg = case when set_type = 'working' then v_weight else null end
    where routine_exercise_id = v_re.id;
    if v_last is distinct from v_re.routine_id then
      v_changed := v_changed + 1;
      v_last := v_re.routine_id;
    end if;
  end loop;

  -- Plan weeks: the same slot in every week reads the new exercise (as coach_apply_block_edits does).
  select e.key into v_key from public.exercises e where e.id = p_new_exercise_id;
  update public.coach_plan_sessions s
  set exercises = (
    select jsonb_agg(
             case when x.value ->> 'exercise_id' = p_exercise_id::text
                  then x.value || jsonb_build_object('exercise_id', p_new_exercise_id::text,
                                                     'exercise_key', coalesce(to_jsonb(v_key), 'null'::jsonb))
                  else x.value end
             order by x.ordinality)
    from jsonb_array_elements(s.exercises) with ordinality x(value, ordinality))
  where s.plan_id = v_plan.id and s.user_id = v_uid
    and exists (select 1 from jsonb_array_elements(s.exercises) y(value)
                where y.value ->> 'exercise_id' = p_exercise_id::text);

  return jsonb_build_object('routines_changed', v_changed);
end;
$$;

-- ---------------------------------------------------------------------------
-- decline_discomfort_swap / dismiss_discomfort_note: stamp the user's unresolved level >= 4 rows of the exercise.
-- Idempotent; nothing matching is not an error. A new level 4-5 row later is a new discomfort and starts clean.
-- ---------------------------------------------------------------------------
create function public.decline_discomfort_swap(p_exercise_id uuid)
returns void
language sql
volatile
security invoker
set search_path = ''
as $$
  update public.exercise_discomforts d
  set swap_declined_at = now()
  where d.user_id = (select auth.uid()) and d.exercise_id = p_exercise_id
    and d.resolved_at is null and d.level >= 4 and d.swap_declined_at is null
$$;

create function public.dismiss_discomfort_note(p_exercise_id uuid)
returns void
language sql
volatile
security invoker
set search_path = ''
as $$
  update public.exercise_discomforts d
  set note_dismissed_at = now()
  where d.user_id = (select auth.uid()) and d.exercise_id = p_exercise_id
    and d.resolved_at is null and d.level >= 4 and d.note_dismissed_at is null
$$;

revoke execute on function public.coach_discomfort_followups() from public, anon;
revoke execute on function public.coach_swap_candidates(uuid) from public, anon;
revoke execute on function public.coach_swap_flagged_exercise(uuid, uuid) from public, anon;
revoke execute on function public.decline_discomfort_swap(uuid) from public, anon;
revoke execute on function public.dismiss_discomfort_note(uuid) from public, anon;
grant execute on function public.coach_discomfort_followups() to authenticated;
grant execute on function public.coach_swap_candidates(uuid) to authenticated;
grant execute on function public.coach_swap_flagged_exercise(uuid, uuid) to authenticated;
grant execute on function public.decline_discomfort_swap(uuid) to authenticated;
grant execute on function public.dismiss_discomfort_note(uuid) to authenticated;
