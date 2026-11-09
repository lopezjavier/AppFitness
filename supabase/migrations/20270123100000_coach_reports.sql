-- T-261 (E18, US-129): reports are stored. The weekly summary becomes a snapshot written when it is first produced and
-- frozen afterwards; weekly and block reports are listed newest first and read by id; opening the block-end notice marks
-- the review seen. No backfill (rows exist only for summaries built after this migration) and reminders are never stored.
-- Rules: planning/04-business-rules.md 6.7, 6.9 and 6.11; data model: planning/03-data-model.md 3.

-- ---------------------------------------------------------------------------
-- coach_week_reports: one row per (user, plan, plan week). Read-only for the owner; written only by the security
-- definer coach_week_summary below. Block reviews are not copied: they are listed live from coach_block_reviews.
-- ---------------------------------------------------------------------------
create table public.coach_week_reports (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  plan_id      uuid not null references public.coach_plans (id) on delete cascade,
  -- Null for a paused window: no plan week is derived for it.
  week         integer null constraint coach_week_reports_week_positive check (week >= 1),
  paused       boolean not null default false,
  period_start date not null,
  period_end   date not null,
  -- The summary JSON as built, without the live `seen` state.
  snapshot     jsonb not null constraint coach_week_reports_snapshot_object check (jsonb_typeof(snapshot) = 'object'),
  created_at   timestamptz not null default now(),
  -- The 7-day window is the identity of every row, paused or not.
  constraint coach_week_reports_window_key unique (user_id, plan_id, period_start),
  constraint coach_week_reports_week_or_paused check (paused or week is not null)
);

create index coach_week_reports_user_created_idx on public.coach_week_reports (user_id, created_at desc);
create index coach_week_reports_plan_idx on public.coach_week_reports (plan_id);

alter table public.coach_week_reports enable row level security;
alter table public.coach_week_reports force row level security;

create policy coach_week_reports_select_own on public.coach_week_reports
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_week_reports from anon, authenticated;
grant select on table public.coach_week_reports to authenticated;

-- ---------------------------------------------------------------------------
-- coach_block_reviews.seen_at: set when the block-end notice is opened. Existing reviews stay unseen (no backfill).
-- ---------------------------------------------------------------------------
alter table public.coach_block_reviews add column seen_at timestamptz null;

create or replace function public.coach_block_review_json(r public.coach_block_reviews)
returns jsonb
language sql
immutable
security invoker
set search_path = ''
as $$
  select jsonb_build_object(
    'id', r.id, 'plan_id', r.plan_id, 'ended_on', r.ended_on, 'inputs', r.inputs,
    'phase_proposal', r.phase_proposal, 'phase_decision', r.phase_decision,
    'decided_at', r.decided_at, 'next_plan_id', r.next_plan_id, 'seen_at', r.seen_at);
$$;

-- mark_coach_block_review_seen: idempotent; touches neither the decision nor the next plan.
create function public.mark_coach_block_review_seen(p_review_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_rev public.coach_block_reviews;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  update public.coach_block_reviews set seen_at = coalesce(seen_at, now())
    where id = p_review_id and user_id = v_uid
    returning * into v_rev;
  if not found then
    raise exception 'review not found' using errcode = 'P0002';
  end if;
  return public.coach_block_review_json(v_rev);
end;
$$;

-- ---------------------------------------------------------------------------
-- list_coach_reports: weekly and block reports of the caller, newest first.
-- ---------------------------------------------------------------------------
create function public.list_coach_reports()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(jsonb_agg(x.j order by x.created_at desc, x.id), '[]'::jsonb)
  from (
    select w.id, w.created_at,
           jsonb_build_object('id', w.id, 'kind', 'week_summary', 'plan_id', w.plan_id, 'week', w.week, 'paused', w.paused,
                              'period_start', w.period_start, 'period_end', w.period_end,
                              'created_at', w.created_at, 'phase_decision', null::text) as j
    from public.coach_week_reports w
    where w.user_id = (select auth.uid())
    union all
    select r.id, r.created_at,
           jsonb_build_object('id', r.id, 'kind', 'block_review', 'plan_id', r.plan_id, 'week', null::int, 'paused', false,
                              'period_start', coalesce(p.training_start, p.block_start), 'period_end', r.ended_on,
                              'created_at', r.created_at, 'phase_decision', r.phase_decision)
    from public.coach_block_reviews r
    join public.coach_plans p on p.id = r.plan_id and p.user_id = r.user_id
    where r.user_id = (select auth.uid())
  ) x;
$$;

-- get_coach_report: one report by id with its stored figures; null when absent or not the caller's.
create function public.get_coach_report(p_id uuid)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_w   public.coach_week_reports;
  v_r   public.coach_block_reviews;
  v_p   public.coach_plans;
begin
  select * into v_w from public.coach_week_reports where id = p_id and user_id = v_uid;
  if found then
    return jsonb_build_object('id', v_w.id, 'kind', 'week_summary', 'plan_id', v_w.plan_id, 'week', v_w.week, 'paused', v_w.paused,
                              'period_start', v_w.period_start, 'period_end', v_w.period_end,
                              'created_at', v_w.created_at, 'snapshot', v_w.snapshot);
  end if;
  select * into v_r from public.coach_block_reviews where id = p_id and user_id = v_uid;
  if found then
    select * into v_p from public.coach_plans where id = v_r.plan_id and user_id = v_uid;
    return jsonb_build_object('id', v_r.id, 'kind', 'block_review', 'plan_id', v_r.plan_id, 'week', null::int, 'paused', false,
                              'period_start', coalesce(v_p.training_start, v_p.block_start), 'period_end', v_r.ended_on,
                              'created_at', v_r.created_at, 'snapshot', public.coach_block_review_json(v_r));
  end if;
  return null;
end;
$$;

-- ---------------------------------------------------------------------------
-- coach_week_summary: the body of 20270110100000 becomes coach_week_summary_build (unchanged, internal). The public
-- function stores the snapshot of a finished plan week on its first build and returns the stored figures afterwards
-- (only `seen` is re-read). A paused week is returned and never stored here (record_paused_week_report does that).
-- Security definer audit: the build chain (coach_week_summary_week, coach_stall_diagnosis, coach_nutrition_diagnosis,
-- coach_daily_goals, coach_week_gate, coach_complete_days, coach_weight_trend, coach_plan_weeks, coach_stalled_lifts,
-- coach_plan_calendar_for, coach_plan_trained_weeks_for, coach_plan_week_of_date_for, coach_plan_pause_state_for,
-- muscle_group_sets, warm_up_included, coach_e1rm) was checked statement by statement: every read of a user table already
-- filters by user_id = auth.uid() (or the explicit p_uid they receive from it), so running as owner exposes no row of
-- another user. coach_reports.test.sql proves it with a second user's data.
-- ---------------------------------------------------------------------------
alter function public.coach_week_summary(date) rename to coach_week_summary_build;
revoke execute on function public.coach_week_summary_build(date) from public, anon, authenticated;

create function public.coach_week_summary(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_json jsonb;
  v_plan uuid;
  v_start date;
  v_row  public.coach_week_reports;
begin
  v_json := public.coach_week_summary_build(p_today);
  if v_json is null or coalesce((v_json->>'paused')::boolean, false) then
    return v_json;
  end if;

  v_plan := (v_json->>'plan_id')::uuid;
  v_start := (v_json->>'start_date')::date;

  select * into v_row from public.coach_week_reports where user_id = v_uid and plan_id = v_plan and period_start = v_start;
  if found then
    return jsonb_set(v_row.snapshot, '{seen}',
                     to_jsonb(exists (select 1 from public.coach_week_summary_seen s
                                      where s.user_id = v_uid and s.plan_id = v_plan
                                        and s.week = (v_json->>'week')::int)));
  end if;

  insert into public.coach_week_reports (user_id, plan_id, week, period_start, period_end, snapshot)
  values (v_uid, v_plan, (v_json->>'week')::int, v_start, (v_json->>'end_date')::date, v_json - 'seen')
  on conflict (user_id, plan_id, period_start) do nothing;
  return v_json;
end;
$$;

-- record_paused_week_report: called when "Semana en pausa" is shown. Stores the paused window (week null, paused true),
-- idempotent, frozen like any other row. Returns the stored snapshot, or null when the build is not a paused week.
create function public.record_paused_week_report(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_json jsonb;
  v_row  public.coach_week_reports;
begin
  v_json := public.coach_week_summary_build(p_today);
  if v_json is null or not coalesce((v_json->>'paused')::boolean, false) then
    return null;
  end if;
  insert into public.coach_week_reports (user_id, plan_id, week, paused, period_start, period_end, snapshot)
  values (v_uid, (v_json->>'plan_id')::uuid, null, true, (v_json->>'start_date')::date, (v_json->>'end_date')::date, v_json)
  on conflict (user_id, plan_id, period_start) do nothing;
  select * into v_row from public.coach_week_reports
    where user_id = v_uid and plan_id = (v_json->>'plan_id')::uuid and period_start = (v_json->>'start_date')::date;
  return v_row.snapshot;
end;
$$;

revoke execute on function public.coach_week_summary(date) from public, anon;
revoke execute on function public.record_paused_week_report(date) from public, anon;
revoke execute on function public.mark_coach_block_review_seen(uuid) from public, anon;
revoke execute on function public.list_coach_reports() from public, anon;
revoke execute on function public.get_coach_report(uuid) from public, anon;
grant execute on function public.coach_week_summary(date) to authenticated;
grant execute on function public.record_paused_week_report(date) to authenticated;
grant execute on function public.mark_coach_block_review_seen(uuid) to authenticated;
grant execute on function public.list_coach_reports() to authenticated;
grant execute on function public.get_coach_report(uuid) to authenticated;
