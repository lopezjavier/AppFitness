-- T-274 (E18, US-134, ADR-106): extend the running block from the Trainer. The user picks a new last day; the difference N is
-- added to the plan week that holds today (window cur). Later weeks, the review and the block-end notice move N days;
-- nothing else changes (planning/04 section 9.9). Plan weeks stay plan weeks: only the real dates of the windows move.
--
-- Model: the "grid" is the unextended calendar (window k = days s0 + 7k .. s0 + 7k + 6, s0 = coalesce(training_start,
-- block_start)). coach_plan_extensions(plan_id, window_k, days) adds `days` to window k, so window k really starts at
-- s0 + 7k + (days of the windows before k) and ends at start + 6 + days(k). Every reader that did window arithmetic moves
-- to the helpers below; the pause rule and the week numbering keep working on the grid through coach_plan_norm_date_for
-- (a real date inside an extension falls on that window's last grid day, later dates shift back by the earlier days).
-- With no rows every helper returns exactly what it returned before (additive).
--
-- Readers that read window dates (checked against the latest definition of each function):
--   moved here: coach_plan_calendar_for, coach_plan_trained_weeks_for, coach_plan_week_of_date_for,
--     coach_plan_pause_state_for, coach_block_end(uuid, date), coach_block_pause, coach_plan_weeks (end_date, phase,
--     cardio and steps ranges), evaluate_coach_deload (weeks 1-3 e1RM windows, "7 trained weeks" windows),
--     coach_stalled_lifts (window end of week_starts), coach_stall_diagnosis (week_starts + 6), coach_block_review_build
--     (first-week weight window), coach_week_summary_build (paused week dates).
--   follow through the helpers, no change: coach_link_plan_session, coach_rederive_plan_links, deload_suggestions,
--     coach_session_in_deload, request_coach_deload, coach_block_counted_weeks, coach_plan_week(_of_date),
--     coach_block_end(uuid), open_coach_block_review, open_coach_block_new, apply_coach_diet_break,
--     set_coach_plan_diet_break, coach_daily_goals, coach_nutrition_diagnosis, coach_week_summary_week,
--     coach_week_summary, record_paused_week_report, coach_reports (they read the calendar, plan_weeks or week_of_date).
--   deliberately unchanged: "last 7 days ending at the week end" nutrition and weight gates (v_end - 6 .. v_end), which
--     are trailing windows and not block windows.

-- ---------------------------------------------------------------------------
-- Table. Writes only through set_coach_block_end.
-- ---------------------------------------------------------------------------
create table public.coach_plan_extensions (
  plan_id  uuid not null references public.coach_plans (id) on delete cascade,
  user_id  uuid not null default auth.uid() references auth.users (id) on delete cascade,
  window_k integer not null constraint coach_plan_extensions_window_range check (window_k >= 0),
  days     integer not null constraint coach_plan_extensions_days_range check (days between 1 and 14),
  primary key (plan_id, window_k)
);

create index coach_plan_extensions_user_idx on public.coach_plan_extensions (user_id);

alter table public.coach_plan_extensions enable row level security;
alter table public.coach_plan_extensions force row level security;

create policy coach_plan_extensions_select_own on public.coach_plan_extensions
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_plan_extensions from anon, authenticated;
grant select on table public.coach_plan_extensions to authenticated;

-- ---------------------------------------------------------------------------
-- Helpers. "_for" variants take the user explicitly (triggers and migrations run without a JWT); security invoker.
-- ---------------------------------------------------------------------------

-- A real date on the unextended grid: dates inside an extension fall on that window's last grid day; later dates shift
-- back by the extension days before them. Dates before the first window are unchanged. Null when the plan is not the user's.
create function public.coach_plan_norm_date_for(p_uid uuid, p_plan_id uuid, p_date date)
returns date
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  ext as (
    select e.window_k as k, e.days,
           coalesce(sum(e.days) over (order by e.window_k rows between unbounded preceding and 1 preceding), 0)::int as c
    from public.coach_plan_extensions e where e.plan_id = p_plan_id and e.user_id = p_uid
  )
  select case when p_date < plan.s0 then p_date
              else coalesce(
                (select plan.s0 + 7 * e.k + 6 from ext e
                 where p_date between plan.s0 + 7 * e.k + e.c + 7 and plan.s0 + 7 * e.k + e.c + 6 + e.days),
                p_date - coalesce((select sum(e.days)::int from ext e
                                   where plan.s0 + 7 * e.k + e.c + 6 + e.days < p_date), 0)) end
  from plan;
$$;

-- The real first and last day of window k.
create function public.coach_plan_window_for(p_uid uuid, p_plan_id uuid, p_k integer)
returns table (start_date date, end_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  pre as (
    select coalesce(sum(e.days), 0)::int as c from public.coach_plan_extensions e
    where e.plan_id = p_plan_id and e.user_id = p_uid and e.window_k < p_k
  ),
  own as (
    select coalesce(max(e.days), 0)::int as d from public.coach_plan_extensions e
    where e.plan_id = p_plan_id and e.user_id = p_uid and e.window_k = p_k
  )
  select plan.s0 + 7 * p_k + pre.c, plan.s0 + 7 * p_k + pre.c + 6 + own.d
  from plan cross join pre cross join own;
$$;

-- The real last day of the window that starts on p_start (a start returned by the calendar).
create function public.coach_plan_window_end_for(p_uid uuid, p_plan_id uuid, p_start date)
returns date
language sql
stable
security invoker
set search_path = ''
as $$
  with plan as (
    select coalesce(p.training_start, p.block_start) as s0
    from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid
  ),
  ext as (
    select e.window_k as k, e.days,
           coalesce(sum(e.days) over (order by e.window_k rows between unbounded preceding and 1 preceding), 0)::int as c
    from public.coach_plan_extensions e where e.plan_id = p_plan_id and e.user_id = p_uid
  )
  select p_start + 6 + coalesce((select e.days from ext e where plan.s0 + 7 * e.k + e.c = p_start), 0)
  from plan;
$$;

-- Today's calendar of a plan: weeks 1..max(plan week) with the real first and last day of each one's window.
create function public.coach_plan_windows_for(p_uid uuid, p_plan_id uuid, p_today date)
returns table (week integer, start_date date, end_date date)
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
    select plan.s0,
           greatest(0, floor((public.coach_plan_norm_date_for(p_uid, p_plan_id, p_today) - plan.s0) / 7.0))::int as ck
    from plan
  ),
  hit as (
    select distinct (public.coach_plan_norm_date_for(p_uid, p_plan_id, (w.started_at at time zone 'utc')::date) - cur.s0) / 7 as k
    from public.workout_sessions w cross join cur
    where w.user_id = p_uid and w.status = 'completed'
      and (w.started_at at time zone 'utc')::date >= cur.s0
      and (public.coach_plan_norm_date_for(p_uid, p_plan_id, (w.started_at at time zone 'utc')::date) - cur.s0) / 7 < cur.ck
  ),
  past as (select hit.k, row_number() over (order by hit.k)::int as n from hit),
  cnt as (select count(*)::int as m from hit),
  last_week as (
    select max(s.week)::int as n from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = p_uid
  )
  select g.n as week, win.start_date, win.end_date
  from last_week
  cross join cur
  cross join cnt
  cross join generate_series(1, last_week.n) g(n)
  left join past on past.n = g.n
  cross join lateral public.coach_plan_window_for(p_uid, p_plan_id, coalesce(past.k, cur.ck + g.n - cnt.m - 1)) win
  order by g.n;
$$;

-- Same signature and columns as before: the first day of each week's window.
create or replace function public.coach_plan_calendar_for(p_uid uuid, p_plan_id uuid, p_today date)
returns table (week integer, start_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  select w.week, w.start_date from public.coach_plan_windows_for(p_uid, p_plan_id, p_today) w order by w.week;
$$;

create or replace function public.coach_plan_trained_weeks_for(p_uid uuid, p_plan_id uuid)
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
    select distinct (public.coach_plan_norm_date_for(p_uid, p_plan_id, (w.started_at at time zone 'utc')::date) - plan.s0) / 7 as k
    from public.workout_sessions w cross join plan
    where w.user_id = p_uid and w.status = 'completed' and (w.started_at at time zone 'utc')::date >= plan.s0
  ),
  ranked as (select hit.k, row_number() over (order by hit.k)::int as n from hit)
  select r.n as week, win.start_date
  from ranked r
  cross join lateral public.coach_plan_window_for(p_uid, p_plan_id, r.k) win
  where r.n <= (select max(s.week) from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = p_uid)
  order by r.n;
$$;

create or replace function public.coach_plan_week_of_date_for(p_uid uuid, p_plan_id uuid, p_date date)
returns integer
language sql
stable
security invoker
set search_path = ''
as $$
  select case when p_date < plan.s0 then 0
              else 1 + (select count(distinct (public.coach_plan_norm_date_for(p_uid, p_plan_id, (w.started_at at time zone 'utc')::date) - plan.s0) / 7)::int
                        from public.workout_sessions w
                        where w.user_id = p_uid and w.status = 'completed'
                          and (w.started_at at time zone 'utc')::date >= plan.s0
                          and (public.coach_plan_norm_date_for(p_uid, p_plan_id, (w.started_at at time zone 'utc')::date) - plan.s0) / 7
                              < (public.coach_plan_norm_date_for(p_uid, p_plan_id, p_date) - plan.s0) / 7) end
  from (select coalesce(p.training_start, p.block_start) as s0
        from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid) plan;
$$;

create or replace function public.coach_plan_pause_state_for(p_uid uuid, p_plan_id uuid, p_today date)
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
    select plan.s0,
           greatest(0, floor((public.coach_plan_norm_date_for(p_uid, p_plan_id, p_today) - plan.s0) / 7.0))::int as ck
    from plan
  ),
  hit as (
    select distinct (public.coach_plan_norm_date_for(p_uid, p_plan_id, (w.started_at at time zone 'utc')::date) - cur.s0) / 7 as k
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

-- coach_block_end(plan, today): the real last day of the window of the plan's last week; null without sessions.
create or replace function public.coach_block_end(p_plan_id uuid, p_today date)
returns date
language sql
stable
security invoker
set search_path = ''
as $$
  select max(c.end_date) from public.coach_plan_windows_for((select auth.uid()), p_plan_id, p_today) c;
$$;

-- coach_block_pause_for: {paused, paused_weeks, block_end} plus the extension keys. Null when the plan is not the user's.
--   extension_days: total days added to this block's windows up to its last one.
--   min_end: the end the block would have without the current window's extension, or later when today falls beyond that
--     window's grid end (at least today for the last window); max_end: the end with no extension at all, plus 14 days.
--   extendable: an accepted plan with a saved block, today up to the block's last day and no review opened for it.
--   min_end and max_end are null when not extendable.
create function public.coach_block_pause_for(p_uid uuid, p_plan_id uuid, p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_st       record;
  v_end      date;
  v_last     date;
  v_klast    integer;
  v_ext      integer;
  v_curext   integer;
  v_curstart date;
  v_endwo    date;
  v_ok       boolean;
  v_extend   boolean;
begin
  select * into v_st from public.coach_plan_pause_state_for(p_uid, p_plan_id, p_today);
  if not found then
    return null;
  end if;

  select max(c.end_date), max(c.start_date) into v_end, v_last
  from public.coach_plan_windows_for(p_uid, p_plan_id, p_today) c;
  if v_end is null then
    return jsonb_build_object('paused', false, 'paused_weeks', 0, 'block_end', null, 'extension_days', 0,
                              'min_end', null, 'max_end', null, 'extendable', false);
  end if;

  v_klast := (public.coach_plan_norm_date_for(p_uid, p_plan_id, v_last) - v_st.start0) / 7;
  select coalesce(sum(e.days) filter (where e.window_k <= v_klast), 0)::int,
         coalesce(sum(e.days) filter (where e.window_k >= v_st.cur_k and e.window_k <= v_klast), 0)::int
  into v_ext, v_curext
  from public.coach_plan_extensions e where e.plan_id = p_plan_id and e.user_id = p_uid;

  select p.status = 'accepted' and p.folder_id is not null
         and not exists (select 1 from public.coach_block_reviews r where r.plan_id = p.id and r.user_id = p_uid)
  into v_ok
  from public.coach_plans p where p.id = p_plan_id and p.user_id = p_uid;
  v_extend := coalesce(v_ok, false) and p_today <= v_end;

  select w.start_date into v_curstart from public.coach_plan_window_for(p_uid, p_plan_id, v_st.cur_k) w;
  v_endwo := v_end - v_curext;

  return jsonb_build_object(
    'paused', v_st.last_week is not null and v_st.cur_k >= 1 and not v_st.prev_has and not v_st.cur_has,
    'paused_weeks', v_st.cur_k - v_st.past_hits,
    'block_end', v_end,
    'extension_days', v_ext,
    'min_end', case when v_extend then v_endwo + greatest(0, p_today - (v_curstart + 6)) end,
    'max_end', case when v_extend then v_st.start0 + 7 * v_klast + 6 + 14 end,
    'extendable', v_extend);
end;
$$;

create or replace function public.coach_block_pause(p_plan_id uuid, p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select public.coach_block_pause_for((select auth.uid()), p_plan_id, p_today);
$$;

revoke execute on function public.coach_plan_norm_date_for(uuid, uuid, date) from public, anon;
revoke execute on function public.coach_plan_window_for(uuid, uuid, integer) from public, anon;
revoke execute on function public.coach_plan_window_end_for(uuid, uuid, date) from public, anon;
revoke execute on function public.coach_plan_windows_for(uuid, uuid, date) from public, anon;
revoke execute on function public.coach_block_pause_for(uuid, uuid, date) from public, anon;
grant execute on function public.coach_plan_norm_date_for(uuid, uuid, date) to authenticated;
grant execute on function public.coach_plan_window_for(uuid, uuid, integer) to authenticated;
grant execute on function public.coach_plan_window_end_for(uuid, uuid, date) to authenticated;
grant execute on function public.coach_plan_windows_for(uuid, uuid, date) to authenticated;
grant execute on function public.coach_block_pause_for(uuid, uuid, date) to authenticated;

-- ---------------------------------------------------------------------------
-- Readers.
-- ---------------------------------------------------------------------------

-- coach_plan_weeks: the same JSON, with the real dates of each window (end_date, phase, cardio and steps ranges).
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
    select c.week, c.start_date, c.end_date
    from public.coach_plan_windows_for((select auth.uid()), (select p.id from plan p), p_today) c
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
    'end_date', cal.end_date,
    'phase', case when p_today < cal.start_date then 'upcoming'
                  when p_today > cal.end_date then 'past'
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
        and (ws.started_at at time zone 'utc')::date between cal.start_date and cal.end_date),
    'cardio_min_target', plan.cardio_target,
    'steps_avg', (
      select round(avg(a.steps))::int
      from public.daily_activity a
      where a.user_id = (select auth.uid()) and a.steps is not null
        and a.day between cal.start_date and cal.end_date),
    'steps_target', plan.steps_target) order by w.week), '[]'::jsonb)
  from weeks w
  cross join plan
  join cal on cal.week = w.week;
$$;

-- Patch helper: applies literal replacements to the latest definition of a function; a missing pattern fails the migration.
create function pg_temp.patch_fn(p_sig regprocedure, p_old text[], p_new text[])
returns void
language plpgsql
as $$
declare
  v_def text := pg_get_functiondef(p_sig);
  v_new text;
  i     integer;
begin
  for i in 1..cardinality(p_old) loop
    v_new := replace(v_def, p_old[i], p_new[i]);
    if v_new = v_def then
      raise exception '%: pattern % not found', p_sig, i;
    end if;
    v_def := v_new;
  end loop;
  execute v_def;
end
$$;

-- evaluate_coach_deload: the weeks 1-3 e1RM windows and the "7 trained weeks" windows use the real window ends.
select pg_temp.patch_fn('public.evaluate_coach_deload(date)'::regprocedure,
  array[
    'select c.week, c.start_date from public.coach_plan_calendar_for(v_uid, v_plan.id, p_today) c where c.week <= 3',
    'between cal.start_date and cal.start_date + 6',
    E'from (select cal.start_date as wstart\n',
    E'cross join lateral public.coach_plan_calendar_for(v_uid, p.id, p_today) cal\n          where p.user_id = v_uid) x',
    'between x.wstart and x.wstart + 6'],
  array[
    'select c.week, c.start_date, c.end_date from public.coach_plan_windows_for(v_uid, v_plan.id, p_today) c where c.week <= 3',
    'between cal.start_date and cal.end_date',
    E'from (select cal.start_date as wstart, cal.end_date as wend\n',
    E'cross join lateral public.coach_plan_windows_for(v_uid, p.id, p_today) cal\n          where p.user_id = v_uid) x',
    'between x.wstart and x.wend']);

-- coach_stalled_lifts: week_starts stay the window starts; each window ends on its real last day.
select pg_temp.patch_fn('public.coach_stalled_lifts(uuid, integer)'::regprocedure,
  array['between w.ws and w.ws + 6'],
  array['between w.ws and public.coach_plan_window_end_for(v_uid, p_plan_id, w.ws)']);

-- coach_stall_diagnosis: every "window start + 6" is the window's real last day.
select pg_temp.patch_fn('public.coach_stall_diagnosis(uuid, integer)'::regprocedure,
  array['v_to := v_ws[3] + 6;', ' u + 6'],
  array['v_to := public.coach_plan_window_end_for(v_uid, p_plan_id, v_ws[3]);',
        ' public.coach_plan_window_end_for(v_uid, p_plan_id, u)']);

-- coach_block_review_build: the first-week weight window is the real first window.
select pg_temp.patch_fn('public.coach_block_review_build(uuid, date, date)'::regprocedure,
  array['between v_start and v_start + 6'],
  array['between v_start and public.coach_plan_window_end_for(v_uid, p_plan_id, v_start)']);

-- coach_week_summary_build: the dates of a paused week come from the window helper.
select pg_temp.patch_fn('public.coach_week_summary_build(date)'::regprocedure,
  array['v_st.start0 + 7 * (v_st.cur_k - 1) + 6', 'v_st.start0 + 7 * (v_st.cur_k - 1)'],
  array['(select w.end_date from public.coach_plan_window_for(v_uid, v_plan.id, v_st.cur_k - 1) w)',
        '(select w.start_date from public.coach_plan_window_for(v_uid, v_plan.id, v_st.cur_k - 1) w)']);

-- ---------------------------------------------------------------------------
-- Writer RPC. Deletes the rows of the current window and the later ones, then stores N = p_last_day - the end the block
-- would have without them (nothing when N = 0). Errors: 22023 outside the limits, with no saved block or past its last
-- day; P0001 for a plan that is not the caller's or not accepted. Returns the new coach_block_pause JSON.
-- ---------------------------------------------------------------------------
create function public.set_coach_block_end(p_plan_id uuid, p_last_day date, p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := public.coach_guard(p_today);
  v_plan   public.coach_plans;
  v_pause  jsonb;
  v_st     record;
  v_curext integer;
  v_endwo  date;
  v_n      integer;
begin
  if p_plan_id is null or p_last_day is null then
    raise exception 'invalid block end' using errcode = '22023';
  end if;

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = 'P0001';
  end if;
  if v_plan.status <> 'accepted' then
    raise exception 'plan is not accepted' using errcode = 'P0001';
  end if;
  if v_plan.folder_id is null then
    raise exception 'plan has no saved block' using errcode = '22023';
  end if;

  v_pause := public.coach_block_pause_for(v_uid, p_plan_id, p_today);
  if v_pause is null or v_pause->'block_end' = 'null'::jsonb then
    raise exception 'plan has no saved block' using errcode = '22023';
  end if;
  if not (v_pause->>'extendable')::boolean then
    raise exception 'the block is past its last day' using errcode = '22023';
  end if;
  if p_last_day < (v_pause->>'min_end')::date or p_last_day > (v_pause->>'max_end')::date then
    raise exception 'last day outside the allowed range' using errcode = '22023';
  end if;

  select * into v_st from public.coach_plan_pause_state_for(v_uid, p_plan_id, p_today);
  select coalesce(sum(e.days), 0)::int into v_curext
  from public.coach_plan_extensions e
  where e.plan_id = p_plan_id and e.user_id = v_uid and e.window_k >= v_st.cur_k;
  v_endwo := (v_pause->>'block_end')::date - v_curext;
  v_n := p_last_day - v_endwo;

  delete from public.coach_plan_extensions e
  where e.plan_id = p_plan_id and e.user_id = v_uid and e.window_k >= v_st.cur_k;
  if v_n > 0 then
    insert into public.coach_plan_extensions (plan_id, user_id, window_k, days)
    values (p_plan_id, v_uid, v_st.cur_k, v_n);
  end if;

  return public.coach_block_pause_for(v_uid, p_plan_id, p_today);
end;
$$;

revoke execute on function public.set_coach_block_end(uuid, date, date) from public, anon;
grant execute on function public.set_coach_block_end(uuid, date, date) to authenticated;
