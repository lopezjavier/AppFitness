-- T-203 (E13, US-94, ADR-94): remove a plan's block without a new plan.
-- Security definer because coach_plan_sessions is select-only for authenticated (20261210100000).
-- The folder, routines and workouts are never touched (E13 Q54). Nutrition, intention, phase, outcomes and activity
-- stay; block_start stays too (it anchors the nutrition phase, E13 Q58). Idempotent: a plan without a block is a no-op.
-- training_start: first day of the plan weeks of a block re-created after a removal. Null for every existing plan, whose
-- weeks keep starting at block_start. remove_coach_block sets it (marker), coach_link_block re-bases it at "Guardar bloque".
alter table public.coach_plans add column training_start date;

create function public.remove_coach_block(p_plan_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_plan public.coach_plans;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_plan_id is null then
    raise exception 'plan not found' using errcode = '22023';
  end if;
  -- Same lock as save_coach_block: a removal never interleaves with a save of the same plan.
  perform pg_advisory_xact_lock(hashtextextended(p_plan_id::text, 0));

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = '22023';
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;

  delete from public.coach_plan_sessions where plan_id = p_plan_id and user_id = v_uid;

  -- No orphaned "softer week" or block suggestion for a block that no longer exists.
  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind in ('deload', 'block') and status = 'pending';

  update public.coach_plans set folder_id = null, training = null,
      training_start = (now() at time zone 'utc')::date where id = p_plan_id and user_id = v_uid;
end;
$$;

revoke execute on function public.remove_coach_block(uuid) from public, anon;
grant execute on function public.remove_coach_block(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Plan weeks start at training_start when set (a block re-created after a removal starts the day it is attached),
-- else at block_start. block_start keeps anchoring the nutrition phase (E13 Q58). Bodies as before, with that start.
-- ---------------------------------------------------------------------------

create or replace function public.coach_link_plan_session()
returns trigger
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_date date;
begin
  if new.status <> 'completed' or new.routine_id is null then
    return new;
  end if;
  if tg_op = 'UPDATE' and old.status = 'completed' then
    return new;
  end if;

  v_date := (new.started_at at time zone 'utc')::date;

  update public.coach_plan_sessions t
    set session_id = new.id
    where t.id = (
      select s.id
      from public.coach_plan_sessions s
      join public.coach_plans p on p.id = s.plan_id and p.user_id = new.user_id
      where s.user_id = new.user_id
        and s.routine_id = new.routine_id
        and s.session_id is null
        and p.status in ('proposed', 'accepted')
        and p.folder_id is not null
        and v_date >= coalesce(p.training_start, p.block_start) + 7 * (s.week - 1)
        and v_date <= coalesce(p.training_start, p.block_start) + 7 * (s.week - 1) + 6
        and not exists (select 1 from public.coach_plan_sessions x
                        where x.user_id = new.user_id and x.session_id = new.id)
      order by s.session_index
      limit 1
    )
    and t.user_id = new.user_id;
  return new;
end;
$$;

create or replace function public.coach_plan_weeks(p_plan_id uuid, p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select p.id, coalesce(p.training_start, p.block_start) as block_start,
           nullif(coalesce(p.nutrition->'activity'->>'cardio_min_week',
                           p.training->'cardio'->>'target_min_week'), '')::int as cardio_target,
           nullif(p.nutrition->'activity'->>'steps_day', '')::int as steps_target
    from public.coach_plans p
    where p.id = p_plan_id and p.user_id = (select auth.uid())
  ),
  weeks as (
    select s.week,
           bool_or(s.deload) as deload,
           count(*)::int as planned,
           count(*) filter (where s.session_id is not null)::int as completed,
           jsonb_agg(jsonb_build_object(
             'session_index', s.session_index, 'name', s.name, 'routine_id', s.routine_id,
             'session_id', s.session_id, 'done', s.session_id is not null) order by s.session_index) as sessions
    from public.coach_plan_sessions s
    join plan on plan.id = s.plan_id
    where s.user_id = (select auth.uid())
    group by s.week
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'week', w.week,
    'start_date', plan.block_start + 7 * (w.week - 1),
    'end_date', plan.block_start + 7 * (w.week - 1) + 6,
    'phase', case when p_today < plan.block_start + 7 * (w.week - 1) then 'upcoming'
                  when p_today > plan.block_start + 7 * (w.week - 1) + 6 then 'past'
                  else 'current' end,
    'deload', w.deload,
    'planned', w.planned,
    'completed', w.completed,
    'adherence_pct', round(w.completed * 100.0 / w.planned)::int,
    'sessions', w.sessions,
    'cardio_min_done', (
      select round(coalesce(sum(st.duration_s), 0) / 60.0)::int
      from public.workout_sessions ws
      join public.workout_sets st on st.session_id = ws.id
      join public.exercises e on e.id = st.exercise_id
      where ws.user_id = (select auth.uid()) and ws.status = 'completed'
        and st.completed_at is not null and e.movement_pattern = 'cardio'
        and (ws.started_at at time zone 'utc')::date
            between plan.block_start + 7 * (w.week - 1) and plan.block_start + 7 * (w.week - 1) + 6),
    'cardio_min_target', plan.cardio_target,
    'steps_avg', (
      select round(avg(a.steps))::int
      from public.daily_activity a
      where a.user_id = (select auth.uid()) and a.steps is not null
        and a.day between plan.block_start + 7 * (w.week - 1) and plan.block_start + 7 * (w.week - 1) + 6),
    'steps_target', plan.steps_target) order by w.week), '[]'::jsonb)
  from weeks w cross join plan;
$$;

create or replace function public.evaluate_coach_deload(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_plan    public.coach_plans;
  v_weeks   integer;
  v_c       integer;
  v_rec     public.coach_recommendations;
  v_lifts   jsonb;
  v_improved boolean;
  v_id      uuid;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted' and folder_id is not null;
  if not found then
    return jsonb_build_object('status', 'none', 'reason', 'no_block');
  end if;

  -- The real length of the saved block (a block can be shorter than the plan's default).
  select max(s.week) into v_weeks from public.coach_plan_sessions s where s.plan_id = v_plan.id and s.user_id = v_uid;
  if v_weeks is null then
    return jsonb_build_object('status', 'none', 'reason', 'no_block');
  end if;

  if p_today < coalesce(v_plan.training_start, v_plan.block_start) then
    return jsonb_build_object('status', 'none', 'reason', 'too_early');
  end if;
  v_c := (p_today - coalesce(v_plan.training_start, v_plan.block_start)) / 7 + 1;
  if v_c < 3 or v_c >= v_weeks then
    return jsonb_build_object('status', 'none', 'reason', 'too_early');
  end if;

  select * into v_rec from public.coach_recommendations r
    where r.user_id = v_uid and r.plan_id = v_plan.id and r.kind = 'deload' and (r.payload->>'week')::int = v_c + 1
    order by r.created_at desc limit 1;
  if found then
    if v_rec.status = 'pending' then
      return jsonb_build_object('status', 'pending', 'reason', v_rec.reason, 'recommendation_id', v_rec.id,
                                'plan_id', v_plan.id, 'week', v_c + 1);
    end if;
    return jsonb_build_object('status', 'none', 'reason', 'already_decided');
  end if;

  -- Weekly best e1RM of each main lift in plan weeks c-2..c; a lift needs a value in every one of the three weeks.
  with best as (
    select st.exercise_id,
           ((s.started_at at time zone 'utc')::date - coalesce(v_plan.training_start, v_plan.block_start)) / 7 + 1 as wk,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or public.warm_up_included())
      and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                       'horizontal_pull', 'vertical_pull')
      and (s.started_at at time zone 'utc')::date >= coalesce(v_plan.training_start, v_plan.block_start) + 7 * (v_c - 3)
      and (s.started_at at time zone 'utc')::date <= coalesce(v_plan.training_start, v_plan.block_start) + 7 * (v_c - 1) + 6
    group by st.exercise_id, wk
  ),
  lifts as (
    select b.exercise_id,
           max(b.e1rm) filter (where b.wk = v_c - 2) as w1,
           max(b.e1rm) filter (where b.wk = v_c - 1) as w2,
           max(b.e1rm) filter (where b.wk = v_c)     as w3
    from best b
    where b.e1rm is not null
    group by b.exercise_id
  )
  select coalesce(jsonb_agg(jsonb_build_object('exercise_id', l.exercise_id, 'e1rm', jsonb_build_array(l.w1, l.w2, l.w3))
                            order by l.exercise_id), '[]'::jsonb),
         coalesce(bool_or(greatest(l.w2, l.w3) > l.w1), false)
  into v_lifts, v_improved
  from lifts l
  where l.w1 is not null and l.w2 is not null and l.w3 is not null;

  if jsonb_array_length(v_lifts) = 0 then
    return jsonb_build_object('status', 'none', 'reason', 'no_main_lifts');
  end if;
  if v_improved then
    return jsonb_build_object('status', 'none', 'reason', 'improved');
  end if;

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary)
  values (v_uid, v_plan.id, 'deload',
          jsonb_build_object('week', v_c + 1, 'plan_id', v_plan.id, 'lifts', v_lifts),
          'no_e1rm_improvement', public.coach_summary(p_today))
  returning id into v_id;

  return jsonb_build_object('status', 'created', 'reason', 'no_e1rm_improvement', 'recommendation_id', v_id,
                            'plan_id', v_plan.id, 'week', v_c + 1);
end;
$$;

create or replace function public.deload_suggestions(p_session_id uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_s    public.workout_sessions;
  v_week integer;
  v_ex   jsonb;
begin
  if v_uid is null then
    return '[]'::jsonb;
  end if;
  select * into v_s from public.workout_sessions s where s.id = p_session_id and s.user_id = v_uid;
  if not found or v_s.routine_id is null then
    return '[]'::jsonb;
  end if;

  select ps.week into v_week
  from public.coach_plan_sessions ps
  join public.coach_plans p on p.id = ps.plan_id and p.user_id = v_uid
  where ps.user_id = v_uid and ps.routine_id = v_s.routine_id and ps.deload
    and (v_s.started_at at time zone 'utc')::date >= coalesce(p.training_start, p.block_start) + 7 * (ps.week - 1)
    and (v_s.started_at at time zone 'utc')::date <= coalesce(p.training_start, p.block_start) + 7 * (ps.week - 1) + 6
  order by ps.week
  limit 1;
  if v_week is null then
    return '[]'::jsonb;
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
           'exercise_id', x.exercise_id, 'planned_sets', x.n, 'deload_sets', greatest(1, round(x.n * 0.6)::int))
           order by x.pos), '[]'::jsonb)
  into v_ex
  from (
    select st.exercise_id, count(*)::int as n, min(st.exercise_position) as pos
    from public.workout_sets st
    where st.session_id = p_session_id and st.set_type = 'working'
    group by st.exercise_id
  ) x;

  return jsonb_build_object('week', v_week, 'exercises', v_ex);
end;
$$;

-- coach_link_block (the single step of "Guardar bloque"): a plan whose block was removed re-bases its weeks to today.

create or replace function public.coach_link_block(p_plan_id uuid, p_folder_id uuid, p_routine_ids uuid[])
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

  update public.coach_plans
    set folder_id = p_folder_id,
        training_start = case when training_start is not null then (now() at time zone 'utc')::date end
    where id = p_plan_id and user_id = v_uid;
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
