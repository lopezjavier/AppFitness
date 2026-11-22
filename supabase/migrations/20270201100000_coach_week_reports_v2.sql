-- T-314 (US-141, ADR-110): rewrite every stored v1 weekly report (coach_week_reports.snapshot without schema_version)
-- to the snapshot v2 contract of T-313, keeping the original JSON in the new column body_v1.
-- Rules: planning/07-coach-design.md section 3 "Weekly report: progress first", paragraph "Stored weeks and late data";
-- planning/04-business-rules.md section 6.11 (the ADR-110 amendment at its end); contract: tasks/todo.md "## T-314" and
-- "### T-310 split" (PM decision Q1: only stored weeks are rebuilt); builder and live path:
-- 20270131100000_coach_week_progress.sql (coach_week_progress_for, coach_finalise_week_reports_for,
-- coach_week_summary).
--
-- What changes, per v1 row (rows already written as v2 by T-313 are not touched):
--   - body_v1 = the snapshot as stored;
--   - snapshot = the same JSON plus schema_version 2 and, on a normal (not paused) row, progress rebuilt by the T-313
--     builder as of the row's own week end (as_of = period_end). Every v1 key stays byte-equal.
--   - progress.final follows T-313's finalise rule (04 section 6.11): a row is final once a later week of the user,
--     normal or paused, any plan, is stored. So the user's newest stored row stays refreshable (Q17) and the live path
--     keeps refreshing it, then finalises it when the next week is stored.
--   - progress.missing also lists deleted_sessions / deleted_weigh_ins when fewer linked plan sessions or weigh-in days
--     exist now than the v1 row counted (only the backfill can see a deletion: it compares the frozen v1 counts).
-- Limits (R9 "as of the week's end"): rows dated after period_end are not counted; rows edited since are read as they
-- are now; a deleted free workout with no plan link, or deletions in earlier weeks (sparkline, previous-week average),
-- are not flagged. deleted_sessions also appears on a week of a block removed with remove_coach_block (it deletes the
-- plan's sessions and keeps the workouts), since the v1 count can no longer be matched.
--
-- Order: lock coach_week_reports against concurrent writes, snapshot the row counts and report what is about to change
-- (raise notice), add body_v1, create the internal backfill function, run it once (counts and elapsed time by notice),
-- then self-check, including a second run that must change nothing. The migration runner runs the file in one
-- transaction: any failed check rolls everything back. That guarantee rests on the pinned Supabase CLI (one batch per
-- file) and on this file holding no transaction-control, CONCURRENTLY, VACUUM, ALTER SYSTEM or CLUSTER statement, nor a
-- "pg-delta: transaction=false" first line. Reversal, if ever needed, is a new migration setting
-- snapshot = body_v1 (not planned).

-- 0. No report is read or stored while the rows are counted, rewritten and checked (coach_week_summary waits, then
-- sees v2). Access exclusive from the start: the add column below needs it anyway, and taking it first avoids a lock
-- upgrade that could deadlock with a concurrent read-then-write.
-- Inside a do block: the runner sends the file as one implicit transaction, where a top-level LOCK is rejected; the
-- lock is held until that transaction ends.
do $$
begin
  lock table public.coach_week_reports in access exclusive mode;
end $$;

-- 1. Snapshot: the row count of every public table (only coach_week_reports is checked hard, the rest is reported),
-- and every stored weekly report as it is now.
create temp table wr_counts (table_name text primary key, row_count bigint not null);

do $$
declare
  r   record;
  v_n bigint;
begin
  for r in
    select c.relname
    from pg_class c
    join pg_namespace s on s.oid = c.relnamespace
    where s.nspname = 'public' and c.relkind = 'r'
  loop
    execute format('select count(*) from public.%I', r.relname) into v_n;
    insert into pg_temp.wr_counts (table_name, row_count) values (r.relname, v_n);
  end loop;
end $$;

create temp table wr_before (id uuid primary key, snapshot jsonb not null, v1 boolean not null);

insert into pg_temp.wr_before (id, snapshot, v1)
select r.id, r.snapshot, r.snapshot->>'schema_version' is null
from public.coach_week_reports r;

do $$
declare
  v_normal bigint;
  v_paused bigint;
  v_v2     bigint;
  v_users  bigint;
begin
  select count(*) filter (where b.v1 and not r.paused),
         count(*) filter (where b.v1 and r.paused),
         count(*) filter (where not b.v1),
         count(distinct r.user_id) filter (where b.v1)
  into v_normal, v_paused, v_v2, v_users
  from pg_temp.wr_before b
  join public.coach_week_reports r on r.id = b.id;

  raise notice 'T-314: % v1 normal and % v1 paused weekly reports of % users will be rewritten to v2', v_normal, v_paused,
    v_users;
  raise notice 'T-314: % weekly reports are already v2 and stay as they are', v_v2;
end $$;

-- 2. The v1 backup column. RLS is unchanged: the owner select policy covers it and clients still cannot write.
alter table public.coach_week_reports add column body_v1 jsonb null;

comment on column public.coach_week_reports.body_v1 is
  'The v1 snapshot as stored before T-314 rewrote the row to snapshot v2 (ADR-110); null on rows written as v2.';

-- 3. The backfill. Internal (no client can call it) and kept after this migration: it selects only rows with no
-- schema_version and no backup, so a re-run is a no-op, and no producer of v1 rows remains. It runs as the migration
-- owner, which bypasses RLS; the self-check below still catches a row it did not rewrite.
-- Returns {normal, paused, refreshable, deleted_sessions, deleted_weigh_ins}: the rows rewritten by this call.
create function public.coach_week_reports_backfill_v2()
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_res jsonb;
begin
  with src as (
    select r.id,
           case when not r.paused then
             -- The builder's progress (as_of = period_end, refreshed_at = now()) with T-313's final rule and the
             -- deleted-input codes appended to the builder's gates. A null builder result stays null and fails below.
             b.progress
               || jsonb_build_object(
                    'final', exists (select 1 from public.coach_week_reports o
                                     where o.user_id = r.user_id and o.period_start > r.period_start),
                    'missing', coalesce(b.progress->'missing', '[]'::jsonb)
                                 || to_jsonb(array_remove(array[
                                      case when (r.snapshot->>'sessions_done')::int > d.sessions then 'deleted_sessions' end,
                                      case when (r.snapshot->>'weigh_in_days')::int > d.weigh_in_days
                                           then 'deleted_weigh_ins' end], null)))
           end as progress
    from public.coach_week_reports r
    -- Same arguments as T-313's refresh and finalise.
    left join lateral (select public.coach_week_progress_for(r.user_id, r.plan_id, r.week, r.period_start,
                                                              r.period_end) as progress) b on not r.paused
    -- What exists now, counted as the v1 snapshot counted it: linked plan sessions of the week (coach_plan_weeks
    -- "completed", no date filter) and weigh-in days in [period_end - 6, period_end] (coach_week_gate).
    cross join lateral (
      select (select count(*) from public.coach_plan_sessions s
              where s.plan_id = r.plan_id and s.user_id = r.user_id and s.week = r.week
                and s.session_id is not null)::int as sessions,
             (select count(distinct (m.measured_at at time zone 'utc')::date) from public.body_measurements m
              where m.user_id = r.user_id
                and (m.measured_at at time zone 'utc')::date between r.period_end - 6 and r.period_end)::int
               as weigh_in_days) d
    where r.snapshot->>'schema_version' is null and r.body_v1 is null
  ),
  upd as (
    update public.coach_week_reports r
    set body_v1 = r.snapshot,
        snapshot = r.snapshot || case when r.paused then jsonb_build_object('schema_version', 2)
                                      else jsonb_build_object('schema_version', 2, 'progress', src.progress) end
    from src
    where src.id = r.id
    returning r.id, r.paused, r.snapshot->'progress' as progress
  )
  select jsonb_build_object(
           'normal', count(*) filter (where not u.paused),
           'paused', count(*) filter (where u.paused),
           'refreshable', count(*) filter (where u.progress->>'final' = 'false'),
           'deleted_sessions', count(*) filter (where u.progress->'missing' ? 'deleted_sessions'),
           'deleted_weigh_ins', count(*) filter (where u.progress->'missing' ? 'deleted_weigh_ins'),
           'unbuilt', (array_agg(u.id order by u.id)
                         filter (where not u.paused and jsonb_typeof(u.progress) is distinct from 'object'))[1])
  into v_res
  from upd u;

  -- Never store a normal row without progress (the builder returns null only for a plan that is not the row's user's).
  if v_res->>'unbuilt' is not null then
    raise exception 'T-314 backfill: the progress of weekly report % could not be built', v_res->>'unbuilt';
  end if;
  return v_res - 'unbuilt';
end;
$$;

revoke execute on function public.coach_week_reports_backfill_v2() from public, anon, authenticated;

comment on function public.coach_week_reports_backfill_v2() is
  'T-314: rewrites stored v1 weekly reports to snapshot v2 (ADR-110), keeping body_v1. Internal and idempotent.';

-- 4. Run it once.
create temp table wr_result (result jsonb not null);

do $$
declare
  v_t0  timestamptz := clock_timestamp();
  v_res jsonb;
begin
  v_res := public.coach_week_reports_backfill_v2();
  insert into pg_temp.wr_result (result) values (v_res);
  raise notice 'T-314: rewrote % normal and % paused reports (% stay refreshable, % with deleted sessions, % with deleted weigh-ins) in % ms',
    v_res->>'normal', v_res->>'paused', v_res->>'refreshable', v_res->>'deleted_sessions', v_res->>'deleted_weigh_ins',
    round((extract(epoch from clock_timestamp() - v_t0) * 1000)::numeric, 1);
end $$;

-- 5. Self-check.
do $$
declare
  t        record;
  v_now    bigint;
  v_res    jsonb := (select w.result from pg_temp.wr_result w);
  v_v1     bigint := (select count(*) from pg_temp.wr_before b where b.v1);
  v_n      bigint;
  v_again  jsonb;
begin
  -- (1) coach_week_reports, the only table written (and locked), keeps its row count. Other tables are not locked, so
  -- a concurrent app write there is only reported.
  for t in select c.table_name, c.row_count from pg_temp.wr_counts c loop
    execute format('select count(*) from public.%I', t.table_name) into v_now;
    if v_now <> t.row_count and t.table_name = 'coach_week_reports' then
      raise exception 'T-314 self-check: public.% had % rows, now %', t.table_name, t.row_count, v_now;
    elsif v_now <> t.row_count then
      raise notice 'T-314: public.% had % rows, now % (not written by this migration)', t.table_name, t.row_count,
        v_now;
    end if;
  end loop;

  -- (2) Every row is v2.
  select count(*) into v_n from public.coach_week_reports r where r.snapshot->>'schema_version' is distinct from '2';
  if v_n > 0 then
    raise exception 'T-314 self-check: % weekly reports are not schema_version 2', v_n;
  end if;

  -- (3) A backup exactly on the v1 rows, equal to the snapshot they had.
  if (v_res->>'normal')::bigint + (v_res->>'paused')::bigint <> v_v1 then
    raise exception 'T-314 self-check: % v1 rows before, % rewritten', v_v1,
      (v_res->>'normal')::bigint + (v_res->>'paused')::bigint;
  end if;
  select count(*) into v_n
  from public.coach_week_reports r
  join pg_temp.wr_before b on b.id = r.id
  where (r.body_v1 is not null) <> b.v1 or (b.v1 and r.body_v1 is distinct from b.snapshot);
  if v_n > 0 then
    raise exception 'T-314 self-check: % rows have a body_v1 that is not their v1 snapshot', v_n;
  end if;

  -- (4) Every v1 key untouched.
  select count(*) into v_n
  from public.coach_week_reports r
  where r.body_v1 is not null and r.snapshot - 'progress' - 'schema_version' is distinct from r.body_v1;
  if v_n > 0 then
    raise exception 'T-314 self-check: % rewritten rows changed a v1 key', v_n;
  end if;

  -- (5) Normal rows carry progress as of their week end with a final flag; paused rows carry none.
  select count(*) into v_n
  from public.coach_week_reports r
  where r.body_v1 is not null
    and case when r.paused then r.snapshot ? 'progress'
             else jsonb_typeof(r.snapshot->'progress') is distinct from 'object'
                  or r.snapshot->'progress'->>'as_of' is distinct from r.period_end::text
                  or jsonb_typeof(r.snapshot->'progress'->'final') is distinct from 'boolean' end;
  if v_n > 0 then
    raise exception 'T-314 self-check: % rewritten rows have a missing or malformed progress', v_n;
  end if;

  -- (6) Rows that were already v2 are unchanged.
  select count(*) into v_n
  from public.coach_week_reports r
  join pg_temp.wr_before b on b.id = r.id
  where not b.v1 and (r.snapshot is distinct from b.snapshot or r.body_v1 is not null);
  if v_n > 0 then
    raise exception 'T-314 self-check: % rows that were already v2 changed', v_n;
  end if;

  -- (7) T-313's invariant: a non-final row has no later stored week of its user. Hard for the rows written here; rows
  -- already v2 belong to the live path and are only reported. Two plans of a user can store the same newest
  -- period_start (the unique key includes plan_id; T-313 finalises only strictly earlier rows), so more than one
  -- non-final row per user is valid and only reported.
  select count(*) into v_n
  from public.coach_week_reports r
  where r.body_v1 is not null
    and r.snapshot->'progress'->>'final' = 'false'
    and exists (select 1 from public.coach_week_reports o
                where o.user_id = r.user_id and o.period_start > r.period_start);
  if v_n > 0 then
    raise exception 'T-314 self-check: % rewritten non-final rows have a later stored week', v_n;
  end if;
  select count(*) into v_n
  from public.coach_week_reports r
  where r.body_v1 is null
    and r.snapshot->'progress'->>'final' = 'false'
    and exists (select 1 from public.coach_week_reports o
                where o.user_id = r.user_id and o.period_start > r.period_start);
  if v_n > 0 then
    raise warning 'T-314: % rows already v2 are non-final with a later stored week (live path, untouched)', v_n;
  end if;
  select count(*) into v_n
  from (select r.user_id from public.coach_week_reports r
        where r.snapshot->'progress'->>'final' = 'false'
        group by r.user_id having count(*) > 1) x;
  if v_n > 0 then
    raise warning 'T-314: % users have more than one non-final row (plans sharing their newest week start)', v_n;
  end if;

  -- (8) A second run changes nothing.
  v_again := public.coach_week_reports_backfill_v2();
  if v_again is distinct from jsonb_build_object('normal', 0, 'paused', 0, 'refreshable', 0, 'deleted_sessions', 0,
                                                 'deleted_weigh_ins', 0) then
    raise exception 'T-314 self-check: a second run returned %', v_again;
  end if;

  raise notice 'T-314: done; % v1 rows backed up in body_v1 and rewritten to v2, % v2 rows untouched, row count unchanged',
    v_v1, (select count(*) from pg_temp.wr_before b where not b.v1);
end $$;

drop table pg_temp.wr_counts;
drop table pg_temp.wr_before;
drop table pg_temp.wr_result;
