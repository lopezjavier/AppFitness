-- T-242 (E15, US-106): a missed week pauses the block. Plan week n stops being "the n-th 7-day window from the start" and
-- becomes "the n-th window that holds a completed workout" (planning/04 section 9.9). Windows are the existing 7-day
-- windows anchored at s = coalesce(training_start, block_start) (UTC date of started_at).
--   cur = max(0, floor((today - s) / 7)); P = the windows k < cur with a completed workout (ordered), m = |P|.
--   Plan week n lives in window P[n] when n <= m, else in window cur + (n - m - 1). The current plan week is m + 1.
--   Plan week of a date in window k = 1 + the windows j < k with a completed workout.
-- Part A: helpers. Part B: every reader moves to them. Part C: the user's existing block is re-linked (abort on a loss).

-- ---------------------------------------------------------------------------
-- Part A. Helpers. The "_for" variants take the user explicitly (the link trigger and the migration run without a JWT);
-- they are security invoker, so RLS still hides other users' rows from a caller. The public ones pass auth.uid().
-- ---------------------------------------------------------------------------

-- Calendar of a plan: weeks 1..max(plan week) and the first day of each one's window, as of p_today.
create function public.coach_plan_calendar_for(p_uid uuid, p_plan_id uuid, p_today date)
returns table (week integer, start_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  cur as (
    select plan.s0, greatest(0, floor((p_today - plan.s0) / 7.0))::int as ck from plan
  ),
  hit as (
    select distinct ((w.started_at at time zone 'utc')::date - cur.s0) / 7 as k
    from public.workout_sessions w cross join cur
    where w.user_id = p_uid and w.status = 'completed'
      and (w.started_at at time zone 'utc')::date >= cur.s0
      and ((w.started_at at time zone 'utc')::date - cur.s0) / 7 < cur.ck
  ),
  past as (select hit.k, row_number() over (order by hit.k)::int as n from hit),
  cnt as (select count(*)::int as m from hit),
  last_week as (
    select max(s.week)::int as n from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = p_uid
  )
  select g.n as week, cur.s0 + 7 * coalesce(past.k, cur.ck + g.n - cnt.m - 1) as start_date
  from last_week
  cross join cur
  cross join cnt
  cross join generate_series(1, last_week.n) g(n)
  left join past on past.n = g.n
  order by g.n;
$$;

-- The windows that hold a completed workout, as plan weeks 1..max(plan week): a today-free mapping, used where only
-- trained weeks matter (stalls). It equals the calendar for every week that has a workout.
create function public.coach_plan_trained_weeks_for(p_uid uuid, p_plan_id uuid)
returns table (week integer, start_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  hit as (
    select distinct ((w.started_at at time zone 'utc')::date - plan.s0) / 7 as k
    from public.workout_sessions w cross join plan
    where w.user_id = p_uid and w.status = 'completed' and (w.started_at at time zone 'utc')::date >= plan.s0
  ),
  ranked as (select hit.k, row_number() over (order by hit.k)::int as n from hit)
  select r.n as week, plan.s0 + 7 * r.k as start_date
  from ranked r cross join plan
  where r.n <= (select max(s.week) from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = p_uid)
  order by r.n;
$$;

-- Plan week of a date: 0 before the start, else 1 + the earlier windows with a completed workout.
create function public.coach_plan_week_of_date_for(p_uid uuid, p_plan_id uuid, p_date date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select case when p_date < plan.s0 then 0
              else 1 + (select count(distinct ((w.started_at at time zone 'utc')::date - plan.s0) / 7)::int
                        from public.workout_sessions w
                        where w.user_id = p_uid and w.status = 'completed'
                          and (w.started_at at time zone 'utc')::date >= plan.s0
                          and ((w.started_at at time zone 'utc')::date - plan.s0) / 7 < (p_date - plan.s0) / 7) end
  from (select coalesce(p.training_start, p.block_start) as s0
        from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid) plan;
$$;

-- What the pause rule needs: the first window start, cur, m, whether windows cur - 1 and cur hold a workout, and the
-- plan's last week (null: no saved block, so nothing can be paused).
create function public.coach_plan_pause_state_for(p_uid uuid, p_plan_id uuid, p_today date)
returns table (start0 date, cur_k integer, past_hits integer, prev_has boolean, cur_has boolean, last_week integer)
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  cur as (
    select plan.s0, greatest(0, floor((p_today - plan.s0) / 7.0))::int as ck from plan
  ),
  hit as (
    select distinct ((w.started_at at time zone 'utc')::date - cur.s0) / 7 as k
    from public.workout_sessions w cross join cur
    where w.user_id = p_uid and w.status = 'completed' and (w.started_at at time zone 'utc')::date >= cur.s0
  )
  select cur.s0, cur.ck,
         (select count(*)::int from hit where hit.k < cur.ck),
         cur.ck >= 1 and exists (select 1 from hit where hit.k = cur.ck - 1),
         exists (select 1 from hit where hit.k = cur.ck),
         (select max(s.week)::int from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = p_uid)
  from cur;
$$;

create function public.coach_plan_calendar(p_plan_id uuid, p_today date)
returns table (week integer, start_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  select c.week, c.start_date from public.coach_plan_calendar_for((select auth.uid()), p_plan_id, p_today) c;
$$;

create function public.coach_plan_week_of_date(p_plan_id uuid, p_date date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select public.coach_plan_week_of_date_for((select auth.uid()), p_plan_id, p_date);
$$;

-- coach_plan_week keeps its signature: the plan week of today, anchored at the training start like the plan weeks.
create or replace function public.coach_plan_week(p_plan_id uuid, p_today date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select public.coach_plan_week_of_date_for((select auth.uid()), p_plan_id, p_today);
$$;

-- coach_block_end(plan, today): the last day of the window of the plan's last week; null without sessions. The old
-- one-argument form reads today as the UTC date.
create function public.coach_block_end(p_plan_id uuid, p_today date)
returns date
language sql
stable
security invoker
set search_path = ''
as $$
  select max(c.start_date) + 6 from public.coach_plan_calendar_for((select auth.uid()), p_plan_id, p_today) c;
$$;

create or replace function public.coach_block_end(p_plan_id uuid)
returns date
language sql
stable
security invoker
set search_path = ''
as $$
  select public.coach_block_end(p_plan_id, (now() at time zone 'utc')::date);
$$;

-- coach_block_pause: {paused, paused_weeks, block_end}. paused: the window before the current one is empty and the
-- current one has no workout yet. paused_weeks (empty windows so far) stays after the block resumes.
create function public.coach_block_pause(p_plan_id uuid, p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select jsonb_build_object(
    'paused', st.last_week is not null and st.cur_k >= 1 and not st.prev_has and not st.cur_has,
    'paused_weeks', case when st.last_week is null then 0 else st.cur_k - st.past_hits end,
    'block_end', public.coach_block_end(p_plan_id, p_today))
  from public.coach_plan_pause_state_for((select auth.uid()), p_plan_id, p_today) st;
$$;

revoke execute on function public.coach_plan_calendar_for(uuid, uuid, date) from public, anon;
revoke execute on function public.coach_plan_trained_weeks_for(uuid, uuid) from public, anon;
revoke execute on function public.coach_plan_week_of_date_for(uuid, uuid, date) from public, anon;
revoke execute on function public.coach_plan_pause_state_for(uuid, uuid, date) from public, anon;
revoke execute on function public.coach_plan_calendar(uuid, date) from public, anon;
revoke execute on function public.coach_plan_week_of_date(uuid, date) from public, anon;
revoke execute on function public.coach_block_end(uuid, date) from public, anon;
revoke execute on function public.coach_block_pause(uuid, date) from public, anon;
grant execute on function public.coach_plan_calendar_for(uuid, uuid, date) to authenticated;
grant execute on function public.coach_plan_trained_weeks_for(uuid, uuid) to authenticated;
grant execute on function public.coach_plan_week_of_date_for(uuid, uuid, date) to authenticated;
grant execute on function public.coach_plan_pause_state_for(uuid, uuid, date) to authenticated;
grant execute on function public.coach_plan_calendar(uuid, date) to authenticated;
grant execute on function public.coach_plan_week_of_date(uuid, date) to authenticated;
grant execute on function public.coach_block_end(uuid, date) to authenticated;
grant execute on function public.coach_block_pause(uuid, date) to authenticated;

-- ---------------------------------------------------------------------------
-- Part B. Readers.
-- ---------------------------------------------------------------------------

-- The link trigger: the plan week of the workout date picks the row (lowest free session_index of the routine).
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
        and s.week = public.coach_plan_week_of_date_for(new.user_id, p.id, v_date)
        and not exists (select 1 from public.coach_plan_sessions x
                        where x.user_id = new.user_id and x.session_id = new.id)
      order by s.session_index
      limit 1
    )
    and t.user_id = new.user_id;
  return new;
end;
$$;

-- coach_plan_weeks: the same JSON, with the dates of the calendar.
create or replace function public.coach_plan_weeks(p_plan_id uuid, p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select p.id,
           nullif(coalesce(p.nutrition->'activity'->>'cardio_min_week',
                           p.training->'cardio'->>'target_min_week'), '')::int as cardio_target,
           nullif(p.nutrition->'activity'->>'steps_day', '')::int as steps_target
    from public.coach_plans p
    where p.id = p_plan_id and p.user_id = (select auth.uid())
  ),
  cal as (
    select c.week, c.start_date from public.coach_plan_calendar((select p.id from plan p), p_today) c
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
    'start_date', cal.start_date,
    'end_date', cal.start_date + 6,
    'phase', case when p_today < cal.start_date then 'upcoming'
                  when p_today > cal.start_date + 6 then 'past'
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
        and (ws.started_at at time zone 'utc')::date between cal.start_date and cal.start_date + 6),
    'cardio_min_target', plan.cardio_target,
    'steps_avg', (
      select round(avg(a.steps))::int
      from public.daily_activity a
      where a.user_id = (select auth.uid()) and a.steps is not null
        and a.day between cal.start_date and cal.start_date + 6),
    'steps_target', plan.steps_target) order by w.week), '[]'::jsonb)
  from weeks w
  cross join plan
  join cal on cal.week = w.week;
$$;

-- Deload mark of the session's plan week (the week of its date), for the "deload week" session screen.
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
    and ps.week = public.coach_plan_week_of_date_for(v_uid, p.id, (v_s.started_at at time zone 'utc')::date)
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

create or replace function public.coach_session_in_deload(p_uid uuid, p_session_id uuid)
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select exists (
    select 1
    from public.workout_sessions s
    join public.coach_plan_sessions ps on ps.routine_id = s.routine_id and ps.user_id = p_uid and ps.deload
    join public.coach_plans p on p.id = ps.plan_id and p.user_id = p_uid
    where s.id = p_session_id and s.user_id = p_uid
      and ps.week = public.coach_plan_week_of_date_for(p_uid, p.id, (s.started_at at time zone 'utc')::date)
  )
$$;

-- evaluate_coach_deload (20270103100100) on the calendar: plan week 4 is the 4th trained window (04 section 9.10).
create or replace function public.evaluate_coach_deload(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid       uuid := public.coach_guard(p_today);
  v_plan      public.coach_plans;
  v_weeks     integer;
  v_start     date;
  v_c         integer;
  v_wstart    date;
  v_lifts     jsonb;
  v_improved  boolean;
  v_last      date;
  v_trained   integer;
  v_kind      text;
  v_reason    text;
  v_figures   jsonb;
  v_change    public.coach_changes;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted' and folder_id is not null;
  if not found then
    return jsonb_build_object('status', 'none', 'reason', 'no_block');
  end if;
  select max(s.week) into v_weeks from public.coach_plan_sessions s where s.plan_id = v_plan.id and s.user_id = v_uid;
  if v_weeks is null then
    return jsonb_build_object('status', 'none', 'reason', 'no_block');
  end if;
  if v_weeks < 4 then
    return jsonb_build_object('status', 'none', 'reason', 'short_block');
  end if;

  v_start := coalesce(v_plan.training_start, v_plan.block_start);
  if p_today < v_start then
    return jsonb_build_object('status', 'none', 'reason', 'too_early');
  end if;
  v_c := public.coach_plan_week_of_date_for(v_uid, v_plan.id, p_today);
  if v_c < 4 then
    return jsonb_build_object('status', 'none', 'reason', 'too_early');
  end if;
  if v_c > 4 then
    return jsonb_build_object('status', 'none', 'reason', 'past_week_4');
  end if;
  select c.start_date into v_wstart from public.coach_plan_calendar_for(v_uid, v_plan.id, p_today) c where c.week = 4;

  -- One deload per block: any row of this plan (cancelled too) or a deload mark already there.
  if exists (select 1 from public.coach_changes c
             where c.user_id = v_uid and c.plan_id = v_plan.id
               and c.kind in ('deload_triggered', 'deload_scheduled', 'deload_user'))
     or exists (select 1 from public.coach_plan_sessions s
                where s.plan_id = v_plan.id and s.user_id = v_uid and s.deload) then
    return jsonb_build_object('status', 'none', 'reason', 'already_applied');
  end if;

  -- Weekly best e1RM of each main lift in plan weeks 1-3; a lift needs a value in every one of the three weeks.
  with cal as (
    select c.week, c.start_date from public.coach_plan_calendar_for(v_uid, v_plan.id, p_today) c where c.week <= 3
  ),
  best as (
    select st.exercise_id,
           cal.week as wk,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    join cal on (s.started_at at time zone 'utc')::date between cal.start_date and cal.start_date + 6
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or public.warm_up_included())
      and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                       'horizontal_pull', 'vertical_pull')
    group by st.exercise_id, cal.week
  ),
  lifts as (
    select b.exercise_id,
           max(b.e1rm) filter (where b.wk = 1) as w1,
           max(b.e1rm) filter (where b.wk = 2) as w2,
           max(b.e1rm) filter (where b.wk = 3) as w3
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

  if jsonb_array_length(v_lifts) > 0 and not v_improved then
    v_kind := 'deload_triggered';
    v_reason := 'no_e1rm_improvement';
    v_figures := jsonb_build_object('week', 4, 'lifts', v_lifts);
  else
    -- Training weeks since the last deload week (any block of the user): plan weeks with a completed workout.
    select max(x.wstart) into v_last
    from (select cal.start_date as wstart, bool_or(s.deload) as dl
          from public.coach_plans p
          cross join lateral public.coach_plan_calendar_for(v_uid, p.id, p_today) cal
          join public.coach_plan_sessions s on s.plan_id = p.id and s.user_id = v_uid and s.week = cal.week
          where p.user_id = v_uid
          group by p.id, cal.week, cal.start_date) x
    where x.dl and x.wstart < v_wstart;

    select count(distinct x.wstart)::int into v_trained
    from (select cal.start_date as wstart
          from public.coach_plans p
          cross join lateral public.coach_plan_calendar_for(v_uid, p.id, p_today) cal
          where p.user_id = v_uid) x
    where x.wstart < v_wstart and (v_last is null or x.wstart > v_last)
      and exists (select 1 from public.workout_sessions w
                  where w.user_id = v_uid and w.status = 'completed'
                    and (w.started_at at time zone 'utc')::date between x.wstart and x.wstart + 6);

    if v_trained >= 7 then
      v_kind := 'deload_scheduled';
      v_reason := 'seven_weeks_without_deload';
      v_figures := jsonb_build_object('week', 4, 'training_weeks', v_trained);
    else
      return jsonb_build_object('status', 'none',
                                'reason', case when jsonb_array_length(v_lifts) = 0 then 'no_main_lifts' else 'improved' end);
    end if;
  end if;

  update public.coach_plan_sessions set deload = true
    where plan_id = v_plan.id and user_id = v_uid and week = 4;
  insert into public.coach_changes (plan_id, kind, reason, figures, status)
  values (v_plan.id, v_kind, v_reason, v_figures, 'applied')
  returning * into v_change;

  return jsonb_build_object('status', 'applied', 'reason', v_reason, 'week', 4,
                            'change', public.coach_change_json(v_change));
end;
$$;

-- request_coach_deload: the next plan week is the one after the current plan week (a pause does not count).
create or replace function public.request_coach_deload()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_today  date := (now() at time zone 'utc')::date;
  v_uid    uuid := public.coach_guard(v_today);
  v_plan   public.coach_plans;
  v_weeks  integer;
  v_next   integer;
  v_change public.coach_changes;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted' and folder_id is not null
    for update;
  if not found then
    raise exception 'coach_deload_no_block' using errcode = 'P0001';
  end if;
  select max(s.week) into v_weeks from public.coach_plan_sessions s where s.plan_id = v_plan.id and s.user_id = v_uid;
  if v_weeks is null then
    raise exception 'coach_deload_no_block' using errcode = 'P0001';
  end if;

  if exists (select 1 from public.coach_plan_sessions s where s.plan_id = v_plan.id and s.user_id = v_uid and s.deload) then
    raise exception 'coach_deload_not_available' using errcode = 'P0001';
  end if;

  v_next := public.coach_plan_week_of_date_for(v_uid, v_plan.id, v_today) + 1;
  if v_next > v_weeks then
    raise exception 'coach_deload_no_next_week' using errcode = 'P0001';
  end if;

  update public.coach_plan_sessions set deload = true
    where plan_id = v_plan.id and user_id = v_uid and week = v_next;
  insert into public.coach_changes (plan_id, kind, reason, figures, status)
  values (v_plan.id, 'deload_user', 'user_request', jsonb_build_object('week', v_next), 'applied')
  returning * into v_change;
  return public.coach_change_json(v_change);
end;
$$;

-- coach_stalled_lifts (20270105100000): the 3 latest trained plan weeks up to p_week that are not deload weeks; their
-- windows come from the trained-weeks mapping (a plan week with a workout lives in the same window as in the calendar).
create or replace function public.coach_stalled_lifts(p_plan_id uuid, p_week integer)
returns table (lift_id uuid, week_starts date[])
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := (select auth.uid());
  v_ok    boolean;
  v_ws    date[];
begin
  select true into v_ok from public.coach_plans p where p.id = p_plan_id and p.user_id = v_uid;
  if v_ok is null or p_week is null or p_week < 1 then
    return;
  end if;

  select array_agg(x.ws order by x.ws) into v_ws
  from (select t.start_date as ws
        from public.coach_plan_trained_weeks_for(v_uid, p_plan_id) t
        where t.week <= p_week
          and not exists (select 1 from public.coach_plan_sessions s
                          where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = t.week and s.deload)
        order by t.week desc
        limit 3) x;
  if coalesce(cardinality(v_ws), 0) < 3 then
    return;
  end if;

  return query
  select b.eid, v_ws
  from (select st.exercise_id as eid,
               max(public.coach_e1rm(st.weight_kg, st.reps)) filter (where w.i = 1) as w1,
               max(public.coach_e1rm(st.weight_kg, st.reps)) filter (where w.i = 2) as w2,
               max(public.coach_e1rm(st.weight_kg, st.reps)) filter (where w.i = 3) as w3
        from public.workout_sessions s
        join public.workout_sets st on st.session_id = s.id
        join public.exercises e on e.id = st.exercise_id
        join unnest(v_ws) with ordinality as w(ws, i)
          on (s.started_at at time zone 'utc')::date between w.ws and w.ws + 6
        where s.user_id = v_uid and s.status = 'completed'
          and st.completed_at is not null
          and (st.set_type <> 'warm_up' or public.warm_up_included())
          and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                           'horizontal_pull', 'vertical_pull')
        group by st.exercise_id) b
  where b.w1 is not null and b.w2 is not null and b.w3 is not null
    and not (greatest(b.w2, b.w3) > b.w1)
  order by b.eid;
end;
$$;

-- coach_block_counted_weeks (20270108100000): an earlier block counts the plan week reached the day before the next
-- block began (a paused window is not a week); the newest block is unchanged.
create or replace function public.coach_block_counted_weeks(p_plan_id uuid, p_today date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select case
           when nx.next_start is null
             then least(m.max_week, greatest(public.coach_plan_week(p.id, p_today), 0))
           else least(m.max_week,
                      greatest(0, public.coach_plan_week_of_date_for(p.user_id, p.id, nx.next_start - 1)))
         end
  from public.coach_plans p
  cross join lateral (
    select max(s.week)::int as max_week
    from public.coach_plan_sessions s where s.plan_id = p.id and s.user_id = p.user_id
  ) m
  left join lateral (
    select coalesce(n.training_start, n.block_start) as next_start
    from public.coach_plans n
    where n.user_id = p.user_id and n.status in ('superseded', 'accepted') and n.id <> p.id
      and exists (select 1 from public.coach_plan_sessions s where s.plan_id = n.id and s.user_id = n.user_id)
      and (n.block_start, n.created_at) > (p.block_start, p.created_at)
    order by n.block_start, n.created_at
    limit 1
  ) nx on true
  where p.id = p_plan_id and p.user_id = (select auth.uid()) and m.max_week is not null;
$$;

-- coach_block_review_build (20270108100000): two lines move to the calendar, the rest of the 230-line body is untouched,
-- so the function is patched in place. (1) the last day of the block is read as of p_today; (2) the per-week e1RM of a
-- session is grouped by its plan week. The patch fails the migration if either line is not found.
do $patch$
declare
  v_def text := pg_get_functiondef('public.coach_block_review_build(uuid, date, date)'::regprocedure);
  v_new text;
begin
  v_new := replace(v_def, 'v_end := public.coach_block_end(p_plan_id);',
                   'v_end := public.coach_block_end(p_plan_id, p_today);');
  if v_new = v_def then
    raise exception 'coach_block_review_build: block end line not found';
  end if;
  v_def := v_new;
  v_new := replace(v_def, $o$((ws.started_at at time zone 'utc')::date - v_start) / 7 + 1 as wk,$o$,
                   $n$public.coach_plan_week_of_date_for(v_uid, p_plan_id, (ws.started_at at time zone 'utc')::date) as wk,$n$);
  if v_new = v_def then
    raise exception 'coach_block_review_build: plan week line not found';
  end if;
  execute v_new;
end
$patch$;

-- coach_week_summary: the body of 20270105100000 becomes coach_week_summary_week (unchanged); the public function adds
-- the paused shape. When the week that just ended (window cur - 1) holds no workout the summary is only
-- {plan_id, paused: true, start_date, end_date}: no gate, no changes, no stalls, no proposal, never marked seen.
alter function public.coach_week_summary(date) rename to coach_week_summary_week;

create function public.coach_week_summary(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_plan public.coach_plans;
  v_st   record;
  v_w    jsonb;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found then
    return null;
  end if;

  select * into v_st from public.coach_plan_pause_state_for(v_uid, v_plan.id, p_today);
  if v_st.last_week is not null and v_st.cur_k >= 1 and not v_st.prev_has then
    return jsonb_build_object('plan_id', v_plan.id, 'paused', true,
                              'start_date', v_st.start0 + 7 * (v_st.cur_k - 1),
                              'end_date', v_st.start0 + 7 * (v_st.cur_k - 1) + 6);
  end if;

  v_w := public.coach_week_summary_week(p_today);
  if v_w is null then
    return null;
  end if;
  return v_w || jsonb_build_object('paused', false);
end;
$$;

revoke execute on function public.coach_week_summary(date) from public, anon;
grant execute on function public.coach_week_summary(date) to authenticated;

-- ---------------------------------------------------------------------------
-- Part C. Existing blocks: re-derive the links with the new numbering. Internal: callable by the owner only.
-- Every linked workout of the plan is collected, the links are cleared and the workouts are linked again in date order
-- with the trigger's rule (plan week of the date, lowest free session_index of its routine). A workout with no free
-- row in that week goes back to its previous row when that row is still free, else it stays unlinked.
-- ---------------------------------------------------------------------------
create function public.coach_rederive_plan_links(p_plan_id uuid)
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_plan  public.coach_plans;
  v_links jsonb;
  v_l     jsonb;
  v_row   uuid;
begin
  select * into v_plan from public.coach_plans where id = p_plan_id;
  if not found or v_plan.folder_id is null then
    return;
  end if;

  select coalesce(jsonb_agg(jsonb_build_object('sid', s.session_id, 'rid', s.routine_id, 'old', s.id,
                                               'd', (w.started_at at time zone 'utc')::date)
                            order by w.started_at, s.session_id), '[]'::jsonb)
  into v_links
  from public.coach_plan_sessions s
  join public.workout_sessions w on w.id = s.session_id
  where s.plan_id = p_plan_id and s.session_id is not null;

  if jsonb_array_length(v_links) = 0 then
    return;
  end if;

  update public.coach_plan_sessions set session_id = null
    where plan_id = p_plan_id and session_id is not null;

  for v_l in select value from jsonb_array_elements(v_links) loop
    select t.id into v_row
    from public.coach_plan_sessions t
    where t.plan_id = p_plan_id and t.routine_id = (v_l->>'rid')::uuid and t.session_id is null
      and t.week = public.coach_plan_week_of_date_for(v_plan.user_id, p_plan_id, (v_l->>'d')::date)
    order by t.session_index
    limit 1;
    if v_row is null then
      select t.id into v_row from public.coach_plan_sessions t
        where t.id = (v_l->>'old')::uuid and t.session_id is null;
    end if;
    if v_row is not null then
      update public.coach_plan_sessions set session_id = (v_l->>'sid')::uuid where id = v_row;
    end if;
    v_row := null;
  end loop;
end;
$$;

revoke execute on function public.coach_rederive_plan_links(uuid) from public, anon, authenticated;

-- The deploy step: re-derive every plan with a saved block and linked workouts. Per user the linked rows are counted
-- before and after and reported; a user who would lose a link aborts the migration (nothing is committed).
do $rederive$
declare
  v_u      record;
  v_p      record;
  v_before integer;
  v_after  integer;
begin
  for v_u in
    select distinct s.user_id
    from public.coach_plan_sessions s
    join public.coach_plans p on p.id = s.plan_id and p.folder_id is not null
    where s.session_id is not null
  loop
    select count(*) into v_before from public.coach_plan_sessions s where s.user_id = v_u.user_id and s.session_id is not null;
    for v_p in
      select distinct s.plan_id
      from public.coach_plan_sessions s
      join public.coach_plans p on p.id = s.plan_id and p.folder_id is not null
      where s.user_id = v_u.user_id and s.session_id is not null
    loop
      perform public.coach_rederive_plan_links(v_p.plan_id);
    end loop;
    select count(*) into v_after from public.coach_plan_sessions s where s.user_id = v_u.user_id and s.session_id is not null;
    raise notice 'coach_plan_weeks_pause: user % linked rows before % after %', v_u.user_id, v_before, v_after;
    if v_after < v_before then
      raise exception 'coach_plan_weeks_pause: user % would lose linked sessions (% -> %)', v_u.user_id, v_before, v_after;
    end if;
  end loop;
end
$rederive$;
