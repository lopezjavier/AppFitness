-- T-312 (US-142, ADR-110): remove the weekly check-in from the database. The user approved these five drops by name:
-- (1) the table weekly_check_ins, (2) the column profiles.check_in_weekday, (3) the function weekly_records,
-- (4) the notification_kind label weekly_check_in with its notification_settings rows, (5) the check_in_energy key of
-- coach_summary and of every stored coach_recommendations.input_summary. Nothing else is dropped.
-- Order: snapshot the row counts and report what is about to go (raise notice), strip check_in_energy from the stored
-- summaries, scan every other JSON column for a stray copy, recreate coach_summary without it, check that no surviving
-- function still uses the dropped objects, drop them (no cascade: a hidden dependency must fail loudly), recreate the
-- enum without the label, then self-check. The migration runner runs the file in one transaction: any failed check
-- rolls everything back.

-- 1. Snapshot: the row count of every public table, kept to prove the drops did not touch any other row.
create temp table ci_snapshot (table_name text primary key, row_count bigint not null);

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
    insert into pg_temp.ci_snapshot (table_name, row_count) values (r.relname, v_n);
  end loop;
end $$;

-- 2. The data that is about to be removed, reported so the production run can be checked against it.
do $$
declare
  v_checkins bigint;
  v_users    bigint;
  v_weekdays bigint;
  v_settings bigint;
  v_summary  bigint;
begin
  select count(*), count(distinct user_id) into v_checkins, v_users from public.weekly_check_ins;
  select count(*) into v_weekdays from public.profiles where check_in_weekday <> 7;
  select count(*) into v_settings from public.notification_settings where kind = 'weekly_check_in';
  select count(*) into v_summary
  from public.coach_recommendations
  where input_summary::text like '%check_in_energy%';

  raise notice 'T-312: % weekly_check_ins rows of % users will be dropped', v_checkins, v_users;
  raise notice 'T-312: % profiles have a check_in_weekday other than Sunday (7)', v_weekdays;
  raise notice 'T-312: % notification_settings rows of kind weekly_check_in will be deleted', v_settings;
  raise notice 'T-312: % coach_recommendations.input_summary mention check_in_energy', v_summary;
end $$;

-- 3. Strip check_in_energy from coach_recommendations.input_summary. The key lives inside each element of the
-- top-level weeks array (the whole coach_summary is stored). Only the key goes: element order, the other keys of each
-- week and every other top-level key stay as they are.
create temp table ci_stripped (id uuid primary key, rest jsonb not null, weeks_length integer not null);

do $$
declare
  v_stripped bigint;
  v_bad      bigint;
begin
  insert into pg_temp.ci_stripped (id, rest, weeks_length)
  select r.id, r.input_summary - 'weeks', jsonb_array_length(r.input_summary->'weeks')
  from public.coach_recommendations r
  where jsonb_typeof(r.input_summary->'weeks') = 'array'
    and exists (select 1 from jsonb_array_elements(r.input_summary->'weeks') t(e)
                where jsonb_typeof(t.e) = 'object' and t.e ? 'check_in_energy');

  update public.coach_recommendations r
  set input_summary = jsonb_set(r.input_summary, '{weeks}', (
    select jsonb_agg(case when jsonb_typeof(t.e) = 'object' then t.e - 'check_in_energy' else t.e end order by t.o)
    from jsonb_array_elements(r.input_summary->'weeks') with ordinality t(e, o)))
  where jsonb_typeof(r.input_summary->'weeks') = 'array'
    and exists (select 1 from jsonb_array_elements(r.input_summary->'weeks') t(e)
                where jsonb_typeof(t.e) = 'object' and t.e ? 'check_in_energy');
  get diagnostics v_stripped = row_count;

  select count(*) into v_bad
  from pg_temp.ci_stripped s
  join public.coach_recommendations r on r.id = s.id
  where r.input_summary - 'weeks' is distinct from s.rest
     or jsonb_typeof(r.input_summary->'weeks') is distinct from 'array'
     or jsonb_array_length(r.input_summary->'weeks') <> s.weeks_length;
  if v_bad > 0 or v_stripped <> (select count(*) from pg_temp.ci_stripped) then
    raise exception 'T-312: the strip changed more than check_in_energy in % coach_recommendations rows', v_bad;
  end if;

  select count(*) into v_bad from public.coach_recommendations where input_summary::text like '%check_in_energy%';
  if v_bad > 0 then
    raise exception 'T-312: % coach_recommendations.input_summary still mention check_in_energy', v_bad;
  end if;

  if exists (select 1 from public.coach_recommendations where jsonb_typeof(input_summary) <> 'object') then
    raise exception 'T-312: a coach_recommendations.input_summary is not a JSON object';
  end if;

  raise notice 'T-312: % coach_recommendations.input_summary stripped of check_in_energy', v_stripped;
end $$;

-- 4. No other JSON column may hold a copy of check_in_energy (no other producer exists; this guards an unknown copy).
do $$
declare
  r    record;
  v_hit boolean;
begin
  for r in
    select c.table_name, c.column_name
    from information_schema.columns c
    join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name
    where c.table_schema = 'public'
      and t.table_type = 'BASE TABLE'
      and c.data_type in ('json', 'jsonb')
      and not (c.table_name = 'coach_recommendations' and c.column_name = 'input_summary')
  loop
    execute format('select exists (select 1 from public.%I where %I::text like %L)',
                   r.table_name, r.column_name, '%check_in_energy%')
    into v_hit;
    if v_hit then
      raise exception 'T-312: public.%.% holds check_in_energy', r.table_name, r.column_name;
    end if;
  end loop;
end $$;

-- 5. coach_summary without the check-in energy: the live definition (20270127100000_remove_unused.sql) minus the ci
-- CTE, its join and the check_in_energy pair of each week.
create or replace function public.coach_summary(p_today date)
returns jsonb
language plpgsql
stable
set search_path to ''
as $function$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_from date := public.coach_week_start(p_today) - 49;
  v_out  jsonb;
begin
  with
  w as (select v_from + 7 * i as ws from generate_series(0, 7) i),
  wt_daily as (
    select (measured_at at time zone 'UTC')::date as d, avg(weight_kg) as a
    from public.body_measurements
    where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_from and p_today
    group by 1
  ),
  wt as (
    select public.coach_week_start(d) as ws, avg(a) as a, count(*) as n from wt_daily group by 1
  ),
  diary_daily as (
    select log_date, sum(kcal) as k, sum(protein_g) as p, sum(carbs_g) as c, sum(fat_g) as f
    from public.food_log_entries
    where user_id = v_uid and log_date between v_from and p_today
    group by log_date
  ),
  dw as (
    select public.coach_week_start(log_date) as ws, count(*) as n, avg(k) as k, avg(p) as p, avg(c) as c, avg(f) as f
    from diary_daily group by 1
  ),
  ac as (
    select public.coach_week_start(day) as ws, avg(steps) as s, avg(active_kcal) as k
    from public.daily_activity
    where user_id = v_uid and day between v_from and p_today
    group by 1
  ),
  sl_daily as (
    select (ended_at at time zone 'UTC')::date as d, sum(minutes) as m
    from public.sleep_sessions
    where user_id = v_uid and (ended_at at time zone 'UTC')::date between v_from and p_today
    group by 1
  ),
  sl as (select public.coach_week_start(d) as ws, avg(m) as m from sl_daily group by 1),
  se as (
    select public.coach_week_start((started_at at time zone 'UTC')::date) as ws, count(*) as n
    from public.workout_sessions
    where user_id = v_uid and status = 'completed'
      and (started_at at time zone 'UTC')::date between v_from and p_today
    group by 1
  ),
  sets as (
    select public.coach_week_start((s.started_at at time zone 'UTC')::date) as ws, s.id as sid,
           st.weight_kg, st.reps, st.duration_s, e.id as eid, coalesce(e.key, e.id::text) as xid,
           e.muscle_group as mg, e.movement_pattern as mp
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or public.warm_up_included())
      and (s.started_at at time zone 'UTC')::date between v_from and p_today
  ),
  mus as (
    select ws, mg, count(*) as n, row_number() over (partition by ws order by count(*) desc, mg) as rn
    from sets where mp is distinct from 'cardio' group by ws, mg
  ),
  ca as (
    select ws, count(distinct sid) as n, round(coalesce(sum(duration_s), 0) / 60.0, 0) as m
    from sets where mp = 'cardio' group by ws
  ),
  cf as (
    select xid, count(distinct sid) as n, round(coalesce(sum(duration_s), 0) / 60.0, 0) as m
    from sets where mp = 'cardio' group by xid
    order by n desc, m desc, xid
    limit 3
  ),
  ml as (
    select eid, xid, count(distinct sid) as n
    from sets
    where mp in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push', 'horizontal_pull', 'vertical_pull')
    group by eid, xid
    order by n desc, xid
    limit 12
  ),
  disc as (
    select coalesce(e.key, e.id::text) as xid, d.level, (d.logged_at at time zone 'UTC')::date as logged_on, d.logged_at
    from public.exercise_discomforts d
    join public.exercises e on e.id = d.exercise_id
    where d.user_id = v_uid and d.resolved_at is null and now() < d.logged_at + interval '7 days'
    order by d.logged_at desc
    limit 10
  )
  select jsonb_build_object(
    'generated_for', p_today,
    'window', jsonb_build_object('from', v_from, 'to', p_today),
    'profile', (
      select jsonb_build_object(
        'sex', p.sex,
        'age', case when p.birth_date is not null then date_part('year', age(p_today, p.birth_date))::int end,
        'height_cm', p.height_cm,
        'weight_kg', (select b.weight_kg from public.body_measurements b
                      where b.user_id = v_uid order by b.measured_at desc limit 1))
      from public.profiles p where p.id = v_uid),
    'training_preferences', (
      select jsonb_build_object(
        'intention', tp.intention, 'place', tp.place, 'equipment', coalesce(to_jsonb(tp.equipment), '[]'::jsonb),
        'days_per_week', tp.days_per_week, 'session_minutes', tp.session_minutes)
      from (select 1) one left join public.training_preferences tp on tp.user_id = v_uid),
    'estimates', public.coach_compute(p_today, null),
    'weeks', (
      select jsonb_agg(jsonb_build_object(
        'week_start', w.ws,
        'weigh_in_avg', round(wt.a, 2),
        'weigh_in_days', coalesce(wt.n, 0),
        'diary_days', coalesce(dw.n, 0),
        'kcal_avg', round(dw.k, 0),
        'protein_g_avg', round(dw.p, 0),
        'carbs_g_avg', round(dw.c, 0),
        'fat_g_avg', round(dw.f, 0),
        'steps_avg', round(ac.s, 0),
        'active_kcal_avg', round(ac.k, 0),
        'sleep_minutes_avg', round(sl.m, 0),
        'sessions', coalesce(se.n, 0),
        'sets_by_muscle', coalesce((
          select jsonb_agg(jsonb_build_object('muscle', mr.mg, 'sets', mr.n) order by mr.rn)
          from mus mr where mr.ws = w.ws and mr.rn <= 8), '[]'::jsonb),
        'cardio', jsonb_build_object('sessions', coalesce(ca.n, 0), 'minutes', coalesce(ca.m, 0))) order by w.ws)
      from w
      left join wt on wt.ws = w.ws
      left join dw on dw.ws = w.ws
      left join ac on ac.ws = w.ws
      left join sl on sl.ws = w.ws
      left join se on se.ws = w.ws
      left join ca on ca.ws = w.ws),
    'cardio_favorites', coalesce((
      select jsonb_agg(jsonb_build_object('exercise', cf.xid, 'sessions', cf.n, 'minutes', cf.m)
                       order by cf.n desc, cf.m desc, cf.xid)
      from cf), '[]'::jsonb),
    'main_lifts', coalesce((
      select jsonb_agg(jsonb_build_object(
        'exercise', ml.xid,
        'sessions', ml.n,
        'weekly_e1rm', (
          select jsonb_agg(x.e order by w.ws)
          from w left join (
            select s2.ws, max(public.coach_e1rm(s2.weight_kg, s2.reps)) as e
            from sets s2 where s2.eid = ml.eid group by s2.ws) x on x.ws = w.ws)
      ) order by ml.n desc, ml.xid)
      from ml), '[]'::jsonb),
    'open_discomforts', coalesce((
      select jsonb_agg(jsonb_build_object('exercise', disc.xid, 'level', disc.level, 'logged_on', disc.logged_on)
                       order by disc.logged_at desc)
      from disc), '[]'::jsonb)
  ) into v_out;

  return v_out;
end;
$function$;

revoke execute on function public.coach_summary(date) from public, anon;
grant execute on function public.coach_summary(date) to authenticated;

-- 6. Guard: no surviving public function may still use the check-in objects.
do $$
declare
  v_hit text;
begin
  select p.proname into v_hit
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname <> 'weekly_records'
    and p.prosrc ~* 'weekly_check_in|check_in_weekday|\mweekly_records\M|check_in_energy'
  limit 1;
  if v_hit is not null then
    raise exception 'T-312: public.% still uses the weekly check-in objects', v_hit;
  end if;
end $$;

-- 7. Drops: the five approved objects, nothing else.
-- (3) The records reader of the check-in summary.
drop function public.weekly_records(date, date);

-- (1) The check-in answers, with their trigger, policies, checks and grants (no foreign key points to the table).
drop table public.weekly_check_ins;

-- (2) The check-in weekday and its range check.
alter table public.profiles
  drop constraint profiles_check_in_weekday_range,
  drop column check_in_weekday;

-- (4) The weekly_check_in reminder rows, then the enum recreated without the label (Postgres cannot drop a label).
create temp table ci_deleted (row_count bigint not null);

do $$
declare
  v_deleted bigint;
begin
  delete from public.notification_settings where kind = 'weekly_check_in';
  get diagnostics v_deleted = row_count;
  insert into pg_temp.ci_deleted (row_count) values (v_deleted);
  raise notice 'T-312: % notification_settings rows of kind weekly_check_in deleted', v_deleted;
end $$;

create type public.notification_kind_v2 as enum ('weight', 'circumference', 'week_summary', 'block_end');
alter table public.notification_settings
  alter column kind type public.notification_kind_v2 using kind::text::public.notification_kind_v2;
drop type public.notification_kind;
alter type public.notification_kind_v2 rename to notification_kind;

-- 8. Self-check: every other table keeps its row count, the dropped objects are gone, the kept ones are present, and
-- no summary holds the check-in energy.
do $$
declare
  r         record;
  v_now     bigint;
  v_deleted bigint := (select row_count from pg_temp.ci_deleted);
  v_expect  bigint;
begin
  for r in select table_name, row_count from pg_temp.ci_snapshot loop
    if to_regclass('public.' || quote_ident(r.table_name)) is null then
      continue;
    end if;
    execute format('select count(*) from public.%I', r.table_name) into v_now;
    v_expect := case when r.table_name = 'notification_settings' then r.row_count - v_deleted else r.row_count end;
    if v_now <> v_expect then
      raise exception 'T-312 self-check: public.% had % rows, expected %, now %', r.table_name, r.row_count, v_expect, v_now;
    end if;
  end loop;

  if to_regclass('public.weekly_check_ins') is not null then
    raise exception 'T-312 self-check: public.weekly_check_ins still exists';
  end if;
  if exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'weekly_check_ins') then
    raise exception 'T-312 self-check: a weekly_check_ins policy still exists';
  end if;
  if exists (select 1 from pg_trigger where tgname = 'weekly_check_ins_set_updated_at') then
    raise exception 'T-312 self-check: the weekly_check_ins trigger still exists';
  end if;
  if exists (select 1 from information_schema.columns
             where table_schema = 'public' and table_name = 'profiles' and column_name = 'check_in_weekday') then
    raise exception 'T-312 self-check: profiles.check_in_weekday still exists';
  end if;
  if exists (select 1 from pg_constraint where conname = 'profiles_check_in_weekday_range') then
    raise exception 'T-312 self-check: profiles_check_in_weekday_range still exists';
  end if;
  if exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.proname = 'weekly_records') then
    raise exception 'T-312 self-check: public.weekly_records still exists';
  end if;

  if enum_range(null::public.notification_kind)::text[]
     is distinct from array['weight', 'circumference', 'week_summary', 'block_end'] then
    raise exception 'T-312 self-check: notification_kind is %', enum_range(null::public.notification_kind)::text[];
  end if;
  if to_regtype('public.notification_kind_v2') is not null then
    raise exception 'T-312 self-check: public.notification_kind_v2 still exists';
  end if;
  if (select a.atttypid from pg_attribute a
      where a.attrelid = 'public.notification_settings'::regclass and a.attname = 'kind' and not a.attisdropped)
     is distinct from 'public.notification_kind'::regtype then
    raise exception 'T-312 self-check: notification_settings.kind is not public.notification_kind';
  end if;

  if exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.proname = 'coach_summary' and p.prosrc like '%check_in_energy%') then
    raise exception 'T-312 self-check: coach_summary still builds check_in_energy';
  end if;
  if exists (select 1 from public.coach_recommendations where input_summary::text like '%check_in_energy%') then
    raise exception 'T-312 self-check: a coach_recommendations.input_summary still holds check_in_energy';
  end if;

  if to_regclass('public.notification_settings') is null
     or (select count(*) from pg_policies
         where schemaname = 'public' and tablename = 'notification_settings'
           and policyname in ('notification_settings_select_own', 'notification_settings_insert_own',
                              'notification_settings_update_own')) <> 3 then
    raise exception 'T-312 self-check: notification_settings and its three policies must stay';
  end if;
  if to_regprocedure('public.coach_summary(date)') is null
     or to_regprocedure('public.personal_records(uuid)') is null
     or to_regprocedure('public.set_updated_at()') is null then
    raise exception 'T-312 self-check: coach_summary, personal_records and set_updated_at must stay';
  end if;

  raise notice 'T-312: done; % weekly_check_ins rows dropped with the table, % notification_settings rows deleted, % summaries stripped, every other row count unchanged',
    (select row_count from pg_temp.ci_snapshot where table_name = 'weekly_check_ins'), v_deleted,
    (select count(*) from pg_temp.ci_stripped);
end $$;

drop table pg_temp.ci_snapshot;
drop table pg_temp.ci_stripped;
drop table pg_temp.ci_deleted;
