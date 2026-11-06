-- T-231 (E14, US-98): the weekly summary of the latest finished plan week (planning/04 section 6.7, 07 section 3
-- "Block follow-up" (3)). Built from recorded numbers only; no model call.

-- ---------------------------------------------------------------------------
-- Which summaries the user has opened, per user, plan and week, so a notice clears on every device. Written only by
-- mark_coach_week_summary_seen (clients can read their own rows and nothing else).
-- ---------------------------------------------------------------------------
create table public.coach_week_summary_seen (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  plan_id uuid not null references public.coach_plans (id) on delete cascade,
  week    integer not null constraint coach_week_summary_seen_week_positive check (week >= 1),
  seen_at timestamptz not null default now(),
  primary key (user_id, plan_id, week)
);

create index coach_week_summary_seen_plan_idx on public.coach_week_summary_seen (plan_id);

alter table public.coach_week_summary_seen enable row level security;

create policy coach_week_summary_seen_select_own on public.coach_week_summary_seen
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_week_summary_seen from anon, authenticated;
grant select on table public.coach_week_summary_seen to authenticated;

-- ---------------------------------------------------------------------------
-- Set counting: one implementation of "primary 1, each distinct secondary 0.5" (04 section 4.11).
-- p_strength_only restricts it to the exercises that count for H1 (07 section 3 "What H1 counts": the stored
-- counts_as_strength flag; custom exercises count unless their metric type is time- or distance-based).
-- The two-argument statistics function keeps its signature and calls this one.
-- ---------------------------------------------------------------------------
create function public.muscle_group_sets(p_from date, p_to date, p_strength_only boolean)
returns table (muscle_group public.muscle_group, sets numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  with el as (
    select e.muscle_group as mg, e.secondary_muscles as sec
    from public.workout_sessions s
    join public.workout_sets ws on ws.session_id = s.id
    join public.exercises e on e.id = ws.exercise_id
    where s.status = 'completed'
      and (s.started_at at time zone 'UTC')::date between coalesce(p_from, '-infinity'::date) and coalesce(p_to, 'infinity'::date)
      and ws.completed_at is not null
      and (ws.set_type <> 'warm_up' or public.warm_up_included())
      and (not coalesce(p_strength_only, false)
           or (e.counts_as_strength
               and (not e.is_custom
                    or e.metric_type not in ('duration', 'weight_duration', 'distance_duration', 'floors_duration',
                                             'steps_duration'))))
  ),
  contrib as (
    select el.mg as g, 1::numeric as w from el
    union all
    select d.u, 0.5::numeric
    from el
    cross join lateral (select distinct u from unnest(el.sec) as u where u <> el.mg) d
  )
  select c.g as muscle_group, sum(c.w) as sets
  from contrib c
  group by c.g
  having sum(c.w) > 0
  order by sum(c.w) desc, c.g;
$$;

create or replace function public.muscle_group_sets(p_from date default null, p_to date default null)
returns table (muscle_group public.muscle_group, sets numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select g.muscle_group, g.sets from public.muscle_group_sets(p_from, p_to, false) g;
$$;

revoke execute on function public.muscle_group_sets(date, date, boolean) from public, anon;
grant execute on function public.muscle_group_sets(date, date, boolean) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_week_summary: the latest finished plan week of the accepted plan, or null (no block, no finished week, or a
-- finished week with no completed workout, no weigh-in and no diary day). No fallback to older weeks.
-- ---------------------------------------------------------------------------
create function public.coach_week_summary(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_plan    public.coach_plans;
  v_w       jsonb;
  v_week    integer;
  v_start   date;
  v_end     date;
  v_gate    jsonb;
  v_trend   jsonb;
  v_avg     numeric;
  v_diary   boolean;
  v_from_ts timestamptz;
  v_to_ts   timestamptz;
  v_groups  jsonb;
  v_changes jsonb;
  v_rec     jsonb;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found then
    return null;
  end if;

  select w into v_w
  from jsonb_array_elements(public.coach_plan_weeks(v_plan.id, p_today)) w
  where w->>'phase' = 'past'
  order by (w->>'week')::int desc
  limit 1;
  if v_w is null then
    return null;
  end if;

  v_week := (v_w->>'week')::int;
  v_start := (v_w->>'start_date')::date;
  v_end := (v_w->>'end_date')::date;
  v_gate := public.coach_week_gate(v_end);
  select exists (select 1 from public.food_log_entries
                 where user_id = v_uid and log_date between v_start and v_end) into v_diary;

  if (v_w->>'completed')::int = 0 and (v_gate->>'weigh_in_days')::int = 0 and not v_diary then
    return null;
  end if;

  select round(avg(d.w), 2) into v_avg
  from (select avg(weight_kg) as w
        from public.body_measurements
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and v_end
        group by (measured_at at time zone 'UTC')::date) d;
  v_trend := public.coach_weight_trend(v_end);

  select coalesce(jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets)
                            order by g.sets desc, g.muscle_group), '[]'::jsonb)
  into v_groups
  from public.muscle_group_sets(v_start, v_end, true) g;

  v_from_ts := v_start::timestamp at time zone 'UTC';
  v_to_ts := (v_end + 1)::timestamp at time zone 'UTC';

  -- Same rows the Trainer list reads (select * from coach_changes), newest first.
  select coalesce(jsonb_agg(to_jsonb(c) order by c.created_at desc, c.id), '[]'::jsonb)
  into v_changes
  from public.coach_changes c
  where c.user_id = v_uid and c.plan_id = v_plan.id and c.created_at >= v_from_ts and c.created_at < v_to_ts;

  select jsonb_build_object('id', r.id, 'status', r.status, 'reason', r.reason, 'payload', r.payload,
                            'created_at', r.created_at, 'decided_at', r.decided_at)
  into v_rec
  from public.coach_recommendations r
  where r.user_id = v_uid and r.plan_id = v_plan.id and r.kind = 'calorie_change'
    and r.created_at >= v_from_ts and r.created_at < v_to_ts
  order by r.created_at desc, r.id
  limit 1;

  return jsonb_build_object(
    'plan_id', v_plan.id,
    'week', v_week,
    'start_date', v_start,
    'end_date', v_end,
    'seen', exists (select 1 from public.coach_week_summary_seen s
                    where s.user_id = v_uid and s.plan_id = v_plan.id and s.week = v_week),
    'sessions_done', (v_w->>'completed')::int,
    'sessions_planned', (v_w->>'planned')::int,
    'groups', v_groups,
    'weight', jsonb_build_object(
      'avg_kg', v_avg,
      'slope_kg_week', v_trend->'slope',
      'interval_low', v_trend->'low',
      'interval_high', v_trend->'high',
      'target_rate_kg_week', to_jsonb(nullif(v_plan.nutrition->>'rate_kg_week', '')::numeric)),
    'complete_days', (v_gate->>'complete_days')::int,
    'weigh_in_days', (v_gate->>'weigh_in_days')::int,
    'missing_records', not coalesce((v_gate->>'ok')::boolean, false),
    'changes', v_changes,
    'calorie_proposal', v_rec,
    'deload', coalesce((v_w->>'deload')::boolean, false));
end;
$$;

-- ---------------------------------------------------------------------------
-- mark_coach_week_summary_seen: idempotent; only for a plan of the caller.
-- ---------------------------------------------------------------------------
create function public.mark_coach_week_summary_seen(p_plan_id uuid, p_week integer)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_plan_id is null or p_week is null or p_week < 1 then
    raise exception 'invalid plan or week' using errcode = '22023';
  end if;
  if not exists (select 1 from public.coach_plans where id = p_plan_id and user_id = v_uid) then
    raise exception 'plan not found' using errcode = 'P0002';
  end if;
  insert into public.coach_week_summary_seen (user_id, plan_id, week)
  values (v_uid, p_plan_id, p_week)
  on conflict (user_id, plan_id, week) do nothing;
end;
$$;

revoke execute on function public.coach_week_summary(date) from public, anon;
revoke execute on function public.mark_coach_week_summary_seen(uuid, integer) from public, anon;
grant execute on function public.coach_week_summary(date) to authenticated;
grant execute on function public.mark_coach_week_summary_seen(uuid, integer) to authenticated;
