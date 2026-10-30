-- T-165 (E13, US-73 + US-75): plan weeks with adherence, and the deload suggestion.
-- Rules: planning/04-business-rules.md section 9.4-9.5, planning/07-coach-design.md section 9.
-- No new table. coach_plan_sessions stays owner-select only; writes go through security definer functions that
-- filter every statement by the owner.

alter table public.coach_recommendations drop constraint coach_recommendations_kind_values;
alter table public.coach_recommendations
  add constraint coach_recommendations_kind_values
  check (kind in ('plan', 'calorie_change', 'block', 'deload'));

-- ---------------------------------------------------------------------------
-- coach_link_plan_session: when a workout of a planned routine is completed (live finish, or a manual log inserted as
-- completed), it fills session_id of the first unlinked plan session of that routine in the week of the workout.
-- Never raises: no match is a no-op, so it can never break a finish. A missed session is never moved or relinked.
-- ---------------------------------------------------------------------------
create function public.coach_link_plan_session()
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
        and v_date >= p.block_start + 7 * (s.week - 1)
        and v_date <= p.block_start + 7 * (s.week - 1) + 6
        and not exists (select 1 from public.coach_plan_sessions x
                        where x.user_id = new.user_id and x.session_id = new.id)
      order by s.session_index
      limit 1
    )
    and t.user_id = new.user_id;
  return new;
end;
$$;

create trigger workout_sessions_link_plan_session
  after insert or update of status on public.workout_sessions
  for each row execute function public.coach_link_plan_session();

revoke execute on function public.coach_link_plan_session() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- coach_plan_weeks: the weeks of a plan's block with done / pending sessions and adherence (04 section 9.4).
-- Security invoker: RLS shows only the caller's own plan, so a foreign plan id returns an empty list.
-- ---------------------------------------------------------------------------
create function public.coach_plan_weeks(p_plan_id uuid, p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select p.id, p.block_start
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
    'sessions', w.sessions) order by w.week), '[]'::jsonb)
  from weeks w cross join plan;
$$;

-- ---------------------------------------------------------------------------
-- evaluate_coach_deload: deterministic and idempotent (04 section 9.5). Reason codes in order: no_block, too_early,
-- already_decided, no_main_lifts, improved. A pending recommendation is returned as 'pending'.
-- ---------------------------------------------------------------------------
create function public.evaluate_coach_deload(p_today date)
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

  if p_today < v_plan.block_start then
    return jsonb_build_object('status', 'none', 'reason', 'too_early');
  end if;
  v_c := (p_today - v_plan.block_start) / 7 + 1;
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
           ((s.started_at at time zone 'utc')::date - v_plan.block_start) / 7 + 1 as wk,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or public.warm_up_included())
      and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                       'horizontal_pull', 'vertical_pull')
      and (s.started_at at time zone 'utc')::date >= v_plan.block_start + 7 * (v_c - 3)
      and (s.started_at at time zone 'utc')::date <= v_plan.block_start + 7 * (v_c - 1) + 6
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

-- ---------------------------------------------------------------------------
-- decide_coach_deload: accepting marks that week's plan sessions as deload (routines are never rewritten).
-- ---------------------------------------------------------------------------
create function public.decide_coach_deload(p_id uuid, p_accept boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard((now() at time zone 'utc')::date);
  v_rec  public.coach_recommendations;
  v_plan public.coach_plans;
  v_week integer;
begin
  if p_accept is null then
    raise exception 'invalid decision' using errcode = '22023';
  end if;
  select * into v_rec from public.coach_recommendations
    where id = p_id and user_id = v_uid and kind = 'deload' for update;
  if not found then
    raise exception 'recommendation not found' using errcode = 'P0002';
  end if;
  if v_rec.status <> 'pending' then
    raise exception 'recommendation already decided' using errcode = '55000';
  end if;
  v_week := (v_rec.payload->>'week')::int;

  if p_accept then
    select * into v_plan from public.coach_plans where id = v_rec.plan_id and user_id = v_uid for update;
    if not found or v_plan.status <> 'accepted' then
      raise exception 'plan is not accepted' using errcode = '55000';
    end if;
    update public.coach_plan_sessions set deload = true
      where plan_id = v_plan.id and user_id = v_uid and week = v_week;
    update public.coach_recommendations set status = 'accepted', decided_at = now() where id = p_id;
  else
    update public.coach_recommendations set status = 'discarded', decided_at = now() where id = p_id;
  end if;

  return jsonb_build_object('status', case when p_accept then 'accepted' else 'discarded' end, 'week', v_week);
end;
$$;

-- ---------------------------------------------------------------------------
-- deload_suggestions: in a deload week, the working sets per exercise are cut by 40 % (same weights).
-- Fails quiet like progression_suggestions: [] for anything that does not match.
-- ---------------------------------------------------------------------------
create function public.deload_suggestions(p_session_id uuid)
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
    and (v_s.started_at at time zone 'utc')::date >= p.block_start + 7 * (ps.week - 1)
    and (v_s.started_at at time zone 'utc')::date <= p.block_start + 7 * (ps.week - 1) + 6
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

revoke execute on function public.coach_plan_weeks(uuid, date) from public, anon;
revoke execute on function public.evaluate_coach_deload(date) from public, anon;
revoke execute on function public.decide_coach_deload(uuid, boolean) from public, anon;
revoke execute on function public.deload_suggestions(uuid) from public, anon;
grant execute on function public.coach_plan_weeks(uuid, date) to authenticated;
grant execute on function public.evaluate_coach_deload(date) to authenticated;
grant execute on function public.decide_coach_deload(uuid, boolean) to authenticated;
grant execute on function public.deload_suggestions(uuid) to authenticated;
