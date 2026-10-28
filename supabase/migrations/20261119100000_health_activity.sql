-- T-140 (E11, US-60/61/62): watch data. Daily activity, sleep sessions and resting heart rate (owner-only),
-- the date of the last automatic sync, and two RPCs (idempotent import, mark automatic sync).

create table public.daily_activity (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  -- Calendar day in the device time zone.
  day         date not null,
  steps       integer constraint daily_activity_steps_range check (steps between 0 and 1000000),
  active_kcal numeric(8, 2) constraint daily_activity_active_kcal_range check (active_kcal between 0 and 100000),
  total_kcal  numeric(8, 2) constraint daily_activity_total_kcal_range check (total_kcal between 0 and 100000),
  updated_at  timestamptz not null default now(),
  constraint daily_activity_user_id_day_key unique (user_id, day)
);

create table public.sleep_sessions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  started_at  timestamptz not null,
  ended_at    timestamptz not null,
  -- Minutes actually asleep (computed by the client).
  minutes     integer not null constraint sleep_sessions_minutes_range check (minutes between 1 and 1440),
  stages      jsonb not null default '[]'::jsonb constraint sleep_sessions_stages_array check (jsonb_typeof(stages) = 'array'),
  -- Health Connect record id.
  external_id text not null constraint sleep_sessions_external_id_length check (char_length(external_id) <= 200),
  updated_at  timestamptz not null default now(),
  constraint sleep_sessions_ended_after_started check (ended_at > started_at),
  constraint sleep_sessions_user_id_external_id_key unique (user_id, external_id)
);

create table public.resting_heart_rate (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day         date not null,
  bpm         smallint not null constraint resting_heart_rate_bpm_range check (bpm between 20 and 250),
  -- Informational Health Connect record id.
  external_id text constraint resting_heart_rate_external_id_length check (char_length(external_id) <= 200),
  updated_at  timestamptz not null default now(),
  constraint resting_heart_rate_user_id_day_key unique (user_id, day)
);

create trigger daily_activity_set_updated_at
  before update on public.daily_activity
  for each row execute function public.set_updated_at();
create trigger sleep_sessions_set_updated_at
  before update on public.sleep_sessions
  for each row execute function public.set_updated_at();
create trigger resting_heart_rate_set_updated_at
  before update on public.resting_heart_rate
  for each row execute function public.set_updated_at();

alter table public.daily_activity enable row level security;
alter table public.daily_activity force row level security;
alter table public.sleep_sessions enable row level security;
alter table public.sleep_sessions force row level security;
alter table public.resting_heart_rate enable row level security;
alter table public.resting_heart_rate force row level security;

create policy daily_activity_select_own on public.daily_activity
  for select to authenticated using (user_id = (select auth.uid()));
create policy daily_activity_insert_own on public.daily_activity
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy daily_activity_update_own on public.daily_activity
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy daily_activity_delete_own on public.daily_activity
  for delete to authenticated using (user_id = (select auth.uid()));

create policy sleep_sessions_select_own on public.sleep_sessions
  for select to authenticated using (user_id = (select auth.uid()));
create policy sleep_sessions_insert_own on public.sleep_sessions
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy sleep_sessions_update_own on public.sleep_sessions
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy sleep_sessions_delete_own on public.sleep_sessions
  for delete to authenticated using (user_id = (select auth.uid()));

create policy resting_heart_rate_select_own on public.resting_heart_rate
  for select to authenticated using (user_id = (select auth.uid()));
create policy resting_heart_rate_insert_own on public.resting_heart_rate
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy resting_heart_rate_update_own on public.resting_heart_rate
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy resting_heart_rate_delete_own on public.resting_heart_rate
  for delete to authenticated using (user_id = (select auth.uid()));

revoke all on table public.daily_activity from anon, authenticated;
revoke all on table public.sleep_sessions from anon, authenticated;
revoke all on table public.resting_heart_rate from anon, authenticated;
grant select, insert, update, delete on table public.daily_activity to authenticated;
grant select, insert, update, delete on table public.sleep_sessions to authenticated;
grant select, insert, update, delete on table public.resting_heart_rate to authenticated;

-- Day of the last successful automatic sync (written only by mark_health_auto_sync).
alter table public.health_sync_state add column last_auto_sync_date date;

-- Imports a watch-data batch idempotently. Returns the rows written (inserted or replaced) per type after
-- de-duplication (last element wins). Does not touch health_sync_state.
create or replace function public.import_health_activity(
  p_daily      jsonb,
  p_sleep      jsonb,
  p_resting_hr jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_daily integer := 0;
  v_sleep integer := 0;
  v_hr integer := 0;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if p_daily is null or jsonb_typeof(p_daily) <> 'array' or jsonb_array_length(p_daily) > 400 then
    raise exception 'p_daily must be a json array of at most 400 rows' using errcode = '22023';
  end if;
  if p_sleep is null or jsonb_typeof(p_sleep) <> 'array' or jsonb_array_length(p_sleep) > 400 then
    raise exception 'p_sleep must be a json array of at most 400 rows' using errcode = '22023';
  end if;
  if p_resting_hr is null or jsonb_typeof(p_resting_hr) <> 'array' or jsonb_array_length(p_resting_hr) > 400 then
    raise exception 'p_resting_hr must be a json array of at most 400 rows' using errcode = '22023';
  end if;

  with incoming as (
    select distinct on (r.day) r.*
    from jsonb_array_elements(p_daily) with ordinality as e(v, ord)
         cross join lateral jsonb_to_record(e.v) as r(
           day date, steps integer, active_kcal numeric, total_kcal numeric)
    where r.day is not null
    order by r.day, e.ord desc
  ),
  upserted as (
    insert into public.daily_activity as d (user_id, day, steps, active_kcal, total_kcal)
    select v_user, i.day, i.steps, i.active_kcal, i.total_kcal from incoming i
    on conflict (user_id, day) do update set
      steps = excluded.steps,
      active_kcal = excluded.active_kcal,
      total_kcal = excluded.total_kcal
    returning 1
  )
  select count(*)::integer into v_daily from upserted;

  with incoming as (
    select distinct on (r.external_id) r.*
    from jsonb_array_elements(p_sleep) with ordinality as e(v, ord)
         cross join lateral jsonb_to_record(e.v) as r(
           external_id text, started_at timestamptz, ended_at timestamptz, minutes integer, stages jsonb)
    where r.external_id is not null and r.started_at is not null and r.ended_at is not null
      and r.minutes is not null
    order by r.external_id, e.ord desc
  ),
  upserted as (
    insert into public.sleep_sessions as s (user_id, external_id, started_at, ended_at, minutes, stages)
    select v_user, i.external_id, i.started_at, i.ended_at, i.minutes, coalesce(i.stages, '[]'::jsonb)
    from incoming i
    on conflict (user_id, external_id) do update set
      started_at = excluded.started_at,
      ended_at = excluded.ended_at,
      minutes = excluded.minutes,
      stages = excluded.stages
    returning 1
  )
  select count(*)::integer into v_sleep from upserted;

  with incoming as (
    select distinct on (r.day) r.*
    from jsonb_array_elements(p_resting_hr) with ordinality as e(v, ord)
         cross join lateral jsonb_to_record(e.v) as r(day date, bpm integer, external_id text)
    where r.day is not null and r.bpm is not null
    order by r.day, e.ord desc
  ),
  upserted as (
    insert into public.resting_heart_rate as h (user_id, day, bpm, external_id)
    select v_user, i.day, i.bpm, i.external_id from incoming i
    on conflict (user_id, day) do update set
      bpm = excluded.bpm,
      external_id = excluded.external_id
    returning 1
  )
  select count(*)::integer into v_hr from upserted;

  return jsonb_build_object('daily', v_daily, 'sleep', v_sleep, 'resting_hr', v_hr);
end;
$$;

-- Records the day of a successful automatic sync. Never lowers last_synced_at.
create or replace function public.mark_health_auto_sync(p_day date)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_day is null then
    raise exception 'p_day is required' using errcode = '22023';
  end if;

  insert into public.health_sync_state (user_id, last_synced_at, last_auto_sync_date)
  values (v_user, now(), p_day)
  on conflict (user_id) do update set
    last_auto_sync_date = excluded.last_auto_sync_date;
end;
$$;

revoke execute on function public.import_health_activity(jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.import_health_activity(jsonb, jsonb, jsonb) to authenticated;
revoke execute on function public.mark_health_auto_sync(date) from public, anon;
grant execute on function public.mark_health_auto_sync(date) to authenticated;
