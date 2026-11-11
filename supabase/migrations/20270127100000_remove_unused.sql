-- T-286 (US-123, ADR-107): remove the free-text storage and the unused SQL objects found by the T-277 audit.
-- Order: snapshot the row counts and the user text about to go (reported with raise notice), scrub free_text from the
-- coach snapshots, recreate the three readers without it, check that no surviving function still uses the dropped
-- objects, drop them, then self-check. The migration runner runs the file in one transaction: any failed check rolls
-- everything back. Bodies are the live definitions (pg_get_functiondef) minus the free-text parts.

-- 1. Snapshot: the row count of every public table, kept to prove the drops did not touch any other row.
create temp table rm_snapshot (table_name text primary key, row_count bigint not null);

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
    insert into pg_temp.rm_snapshot (table_name, row_count) values (r.relname, v_n);
  end loop;
end $$;

-- The user text that is about to be removed, reported so the production run can be checked against the data.
do $$
declare
  v_checkins   bigint;
  v_notes      bigint;
  v_exclusions bigint;
  v_intake     bigint;
begin
  select count(*), count(*) filter (where btrim(notes) <> '')
  into v_checkins, v_notes
  from public.weekly_check_ins;
  select count(*) into v_exclusions from public.coach_free_text_exclusions;
  select count(*) filter (where free_text is not null) into v_intake from public.coach_user_intake;

  raise notice 'T-286: % non-empty weekly_check_ins.notes removed (% check-ins in total)', v_notes, v_checkins;
  raise notice 'T-286: % coach_free_text_exclusions rows removed', v_exclusions;
  raise notice 'T-286: % coach_user_intake.free_text values removed', v_intake;
end $$;

-- 2. Scrub: coach_recommendations.input_summary keeps a copy of the coach summary, which used to hold free_text.
-- Only the top-level key exists today; the check below stops the migration if any other copy is found.
do $$
declare
  v_scrubbed bigint;
  v_left     bigint;
begin
  update public.coach_recommendations
  set input_summary = input_summary - 'free_text'
  where input_summary ? 'free_text';
  get diagnostics v_scrubbed = row_count;

  select count(*) into v_left
  from public.coach_recommendations
  where input_summary::text like '%free_text%';
  if v_left > 0 then
    raise exception 'T-286: % coach_recommendations.input_summary still mention free_text', v_left;
  end if;

  raise notice 'T-286: % coach_recommendations.input_summary snapshots scrubbed', v_scrubbed;
end $$;

-- 3. Readers without free_text. coach_summary drops the free_text key (and with it its only caller of
-- coach_free_text_lines); coach_plan_inputs drops both free_text entries; save_coach_intake ignores a free_text key.
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
  ci as (select week_start as ws, energy from public.weekly_check_ins where user_id = v_uid),
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
        'cardio', jsonb_build_object('sessions', coalesce(ca.n, 0), 'minutes', coalesce(ca.m, 0)),
        'check_in_energy', ci.energy) order by w.ws)
      from w
      left join wt on wt.ws = w.ws
      left join dw on dw.ws = w.ws
      left join ac on ac.ws = w.ws
      left join sl on sl.ws = w.ws
      left join se on se.ws = w.ws
      left join ca on ca.ws = w.ws
      left join ci on ci.ws = w.ws),
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

create or replace function public.coach_plan_inputs(p_kind text, p_today date)
returns jsonb
language plpgsql
stable
set search_path to ''
as $function$
declare
  v_uid       uuid := public.coach_guard(p_today);
  v_est       jsonb;
  v_prof      public.profiles;
  v_tp        public.training_preferences;
  v_weigh_at  date;
  v_weigh_kg  numeric;
  v_current   text;
  v_tdee      jsonb;
  v_trend     jsonb;
  v_act       jsonb;
  v_age       integer;
  v_has_tdee  boolean;
  v_entries   jsonb := '[]'::jsonb;
  v_intention jsonb;
  v_cardio    public.equipment[];
  v_lifting   public.equipment[];
  v_intake    public.coach_user_intake;
  v_blockers  text[];
begin
  if p_kind is null or p_kind not in ('plan', 'block') then
    raise exception 'kind must be plan or block' using errcode = '22023';
  end if;

  select * into v_prof from public.profiles where id = v_uid;
  select * into v_tp from public.training_preferences where user_id = v_uid;
  select * into v_intake from public.coach_user_intake where user_id = v_uid;
  v_est := public.coach_compute(p_today, null);

  -- Cardio machines are a group of their own (T-184): the lifting list never carries them, an empty cardio list is
  -- a valid answer (the coach proposes walking), never missing.
  select coalesce(array_agg(e order by o) filter (where e = 'cardio_machine'), '{}'),
         coalesce(array_agg(e order by o) filter (where e <> 'cardio_machine'), '{}')
  into v_cardio, v_lifting
  from unnest(coalesce(v_tp.equipment, '{}'::public.equipment[])) with ordinality t(e, o);

  select case when exists (select 1 from public.coach_plans where user_id = v_uid and status = 'accepted') then 'accepted'
              when exists (select 1 from public.coach_plans where user_id = v_uid and status = 'proposed') then 'proposed'
         end
  into v_current;

  v_intention := public.coach_input_entry(
    'intention', to_jsonb(v_tp.intention), 'my_training',
    case when v_tp.intention is null then 'missing' else 'ok' end,
    v_tp.intention is null, 'my_training');

  if p_kind = 'plan' then
    v_tdee := v_est->'total_expenditure';
    v_trend := v_est->'weight_trend';
    v_act := v_est->'activity_level';
    v_has_tdee := v_tdee->>'value' is not null;
    v_age := case when v_prof.birth_date is null then null
                  else date_part('year', age(p_today, v_prof.birth_date))::integer end;
    v_blockers := public.coach_unconfirmed_keys(v_est);

    select (b.measured_at at time zone 'UTC')::date, b.weight_kg into v_weigh_at, v_weigh_kg
    from public.body_measurements b
    where b.user_id = v_uid
    order by b.measured_at desc limit 1;

    v_entries := jsonb_build_array(
      v_intention,
      public.coach_input_entry('sex', to_jsonb(v_prof.sex), 'profile',
        case when v_prof.sex is null then 'missing' else 'ok' end, v_prof.sex is null, 'personal_data'),
      public.coach_input_entry('age', to_jsonb(v_age), 'profile',
        case when v_age is null then 'missing' else 'ok' end, v_age is null and not v_has_tdee, 'personal_data'),
      public.coach_input_entry('height', to_jsonb(v_prof.height_cm), 'profile',
        case when v_prof.height_cm is null then 'missing' else 'ok' end,
        v_prof.height_cm is null and not v_has_tdee, 'personal_data'),
      public.coach_input_entry('weight', to_jsonb(v_weigh_kg), 'weigh_in',
        case when v_weigh_at is null then 'missing' when p_today - v_weigh_at > 14 then 'stale' else 'ok' end,
        v_weigh_at is null, 'weight', v_weigh_at),
      public.coach_input_entry('activity_level', v_act->'value', 'coach_estimate',
        case when (v_act->>'provisional')::boolean then 'provisional' else 'ok' end, false, 'activity_sheet'),
      public.coach_input_entry('total_expenditure', v_tdee->'value', 'coach_estimate',
        case when not v_has_tdee then 'missing'
             when (v_tdee->>'provisional')::boolean then 'provisional' else 'ok' end, false, null),
      public.coach_input_entry('weight_trend', v_trend->'slope_kg_week', 'coach_estimate',
        case when v_trend->>'slope_kg_week' is null or (v_trend->>'provisional')::boolean then 'provisional'
             else 'ok' end, false, null),
      public.coach_input_entry('diary', v_est->'intake'->'ratio', 'coach_estimate',
        case when (v_est->'intake'->>'reliable')::boolean then 'ok' else 'provisional' end, false, null),
      public.coach_input_entry('experience', v_est->'experience'->'value', 'coach_estimate',
        case when (v_est->'experience'->>'provisional')::boolean then 'provisional' else 'ok' end, false,
        case when v_est->'experience'->>'source' = 'intake' then 'intake' end),
      public.coach_input_entry('training_years', to_jsonb(v_intake.training_years), 'intake',
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'),
      public.coach_input_entry('known_maintenance', to_jsonb(v_intake.known_maintenance_kcal), 'intake',
        case when v_intake.known_maintenance_kcal is null then 'unset_optional' else 'ok' end, false, 'intake'));

    select jsonb_agg(x || jsonb_build_object('blocks_accept',
                                             (x->>'key') = any (v_blockers) and x->>'status' = 'provisional')
                     order by o)
    into v_entries
    from jsonb_array_elements(v_entries) with ordinality t(x, o);
  else
    v_entries := jsonb_build_array(
      v_intention,
      public.coach_input_entry('place', to_jsonb(v_tp.place), 'my_training',
        case when v_tp.place is null then 'missing' else 'ok' end, v_tp.place is null, 'my_training'),
      public.coach_input_entry('equipment', to_jsonb(v_lifting), 'my_training',
        case when coalesce(cardinality(v_lifting), 0) = 0 then 'unset_optional' else 'ok' end,
        false, 'my_training'),
      public.coach_input_entry('cardio', to_jsonb(v_cardio), 'my_training', 'ok', false, 'my_training'),
      public.coach_input_entry('days_per_week', to_jsonb(v_tp.days_per_week), 'my_training',
        case when v_tp.days_per_week is null then 'unset_optional' else 'ok' end, false, 'my_training'),
      public.coach_input_entry('session_minutes', to_jsonb(v_tp.session_minutes), 'my_training',
        case when v_tp.session_minutes is null then 'unset_optional' else 'ok' end, false, 'my_training'),
      public.coach_input_entry('experience', v_est->'experience'->'value', 'coach_estimate',
        case when (v_est->'experience'->>'provisional')::boolean then 'provisional' else 'ok' end, false, null),
      public.coach_input_entry('training_years', to_jsonb(v_intake.training_years), 'intake',
        case when v_intake.training_years is null then 'unset_optional' else 'ok' end, false, 'intake'));
  end if;

  return jsonb_build_object('kind', p_kind, 'as_of', p_today)
         || case when v_current is not null then jsonb_build_object('current_plan', v_current) else '{}'::jsonb end
         || jsonb_build_object('inputs', v_entries);
end;
$function$;

revoke execute on function public.coach_plan_inputs(text, date) from public, anon;
grant execute on function public.coach_plan_inputs(text, date) to authenticated;

create or replace function public.save_coach_intake(p jsonb)
returns jsonb
language plpgsql
set search_path to ''
as $function$
declare
  v_uid       uuid := auth.uid();
  v_years     numeric;
  v_maint     integer;
  v_old       public.coach_user_intake;
  v_found     boolean;
  v_handover  timestamptz;
  v_adaptive  boolean := false;
  v_row       public.coach_user_intake;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p is null or jsonb_typeof(p) <> 'object' then
    raise exception 'intake must be a JSON object' using errcode = '22023';
  end if;

  if jsonb_typeof(p->'training_years') = 'number' then
    v_years := (p->>'training_years')::numeric;
    if v_years < 0 or v_years > 60 then
      raise exception 'training_years out of range' using errcode = '22023';
    end if;
  elsif p ? 'training_years' and jsonb_typeof(p->'training_years') <> 'null' then
    raise exception 'training_years must be a number' using errcode = '22023';
  end if;

  if p ? 'known_maintenance_kcal' and jsonb_typeof(p->'known_maintenance_kcal') <> 'null' then
    if jsonb_typeof(p->'known_maintenance_kcal') <> 'number'
       or (p->>'known_maintenance_kcal')::numeric <> trunc((p->>'known_maintenance_kcal')::numeric)
       or (p->>'known_maintenance_kcal')::numeric not between 1200 and 6000 then
      raise exception 'invalid known_maintenance_kcal' using errcode = '22023';
    end if;
    v_maint := (p->>'known_maintenance_kcal')::numeric::integer;
  end if;

  -- A key for the removed free text sent by an older client is ignored: not validated, not stored, not returned (T-286).

  select * into v_old from public.coach_user_intake where user_id = v_uid;
  v_found := found;

  -- Handover: only when this call changes the maintenance to a new value.
  v_handover := v_old.maintenance_handover_at;
  if p ? 'known_maintenance_kcal' then
    if v_maint is null then
      v_handover := null;
    elsif not (v_found and v_old.known_maintenance_kcal is not distinct from v_maint) then
      v_adaptive := public.coach_compute((now() at time zone 'utc')::date, null)
                      ->'total_expenditure'->>'source' = 'adaptive';
      v_handover := case when v_adaptive then now() end;
    end if;
  end if;

  insert into public.coach_user_intake as c (
    user_id, training_years, known_maintenance_kcal, maintenance_handover_at, saved_at)
  values (
    v_uid,
    case when p ? 'training_years' then v_years end,
    case when p ? 'known_maintenance_kcal' then v_maint end,
    v_handover, now())
  on conflict (user_id) do update
    set training_years = case when p ? 'training_years' then v_years else c.training_years end,
        known_maintenance_kcal = case when p ? 'known_maintenance_kcal' then v_maint else c.known_maintenance_kcal end,
        maintenance_handover_at = v_handover,
        saved_at = now()
  returning * into v_row;

  return jsonb_build_object(
    'training_years', v_row.training_years,
    'known_maintenance_kcal', v_row.known_maintenance_kcal,
    'maintenance_handover_at', v_row.maintenance_handover_at,
    'saved_at', v_row.saved_at);
end;
$function$;

revoke execute on function public.save_coach_intake(jsonb) from public, anon;
grant execute on function public.save_coach_intake(jsonb) to authenticated;

-- 4. Drop the unused objects. Before each group, no surviving public function may still call or read them.
do $$
declare
  v_dropped text[] := array[
    'coach_free_text_lines', 'set_free_text_line_excluded', 'save_meal_slots', 'delete_meal_slot',
    'coach_plan_week_of_date', 'assert_unique_meal_labels', 'convert_used_off_foods', 'coach_rederive_plan_links',
    'migrate_equipment_list', 'off_alcohol_g', 'coach_plan_calendar'];
  v_name text;
  v_hit  text;
begin
  foreach v_name in array v_dropped loop
    select p.proname into v_hit
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname <> all (v_dropped)
      and p.prosrc ~* ('\m' || v_name || '\s*\(')
    limit 1;
    if v_hit is not null then
      raise exception 'T-286: public.% is still called by public.%', v_name, v_hit;
    end if;
  end loop;

  select p.proname into v_hit
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.proname <> all (v_dropped)
    and (p.prosrc ~* 'coach_free_text_exclusions' or p.prosrc ~* '\mfree_text\M'
         or (p.prosrc ~* 'weekly_check_ins' and p.prosrc ~* '\mnotes\M'))
  limit 1;
  if v_hit is not null then
    raise exception 'T-286: public.% still reads the free text or the check-in notes', v_hit;
  end if;
end $$;

drop function public.coach_free_text_lines(date);
drop function public.set_free_text_line_excluded(text, text, boolean);
drop function public.save_meal_slots(jsonb);
drop function public.delete_meal_slot(uuid);
drop function public.coach_plan_week_of_date(uuid, date);
drop function public.assert_unique_meal_labels();
drop function public.convert_used_off_foods();
drop function public.coach_rederive_plan_links(uuid);
drop function public.migrate_equipment_list(text[], text);
drop function public.off_alcohol_g(jsonb);
drop function public.coach_plan_calendar(uuid, date);

drop table public.coach_free_text_exclusions;

alter table public.coach_user_intake
  drop constraint coach_user_intake_text_length,
  drop column free_text;

alter table public.weekly_check_ins
  drop constraint weekly_check_ins_notes_length,
  drop column notes;

-- 5. Self-check: every other table keeps its row count, the dropped objects are gone, the kept ones are present, and
-- every coach snapshot is still a JSON object.
do $$
declare
  r         record;
  v_now     bigint;
  v_name    text;
  v_dropped text[] := array[
    'coach_free_text_lines', 'set_free_text_line_excluded', 'save_meal_slots', 'delete_meal_slot',
    'coach_plan_week_of_date', 'assert_unique_meal_labels', 'convert_used_off_foods', 'coach_rederive_plan_links',
    'migrate_equipment_list', 'off_alcohol_g', 'coach_plan_calendar'];
begin
  for r in select table_name, row_count from pg_temp.rm_snapshot loop
    if to_regclass('public.' || quote_ident(r.table_name)) is null then
      continue;
    end if;
    execute format('select count(*) from public.%I', r.table_name) into v_now;
    if v_now <> r.row_count then
      raise exception 'T-286 self-check: public.% had % rows, now %', r.table_name, r.row_count, v_now;
    end if;
  end loop;

  foreach v_name in array v_dropped loop
    if exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
               where n.nspname = 'public' and p.proname = v_name) then
      raise exception 'T-286 self-check: public.% still exists', v_name;
    end if;
  end loop;

  if to_regclass('public.coach_free_text_exclusions') is not null then
    raise exception 'T-286 self-check: public.coach_free_text_exclusions still exists';
  end if;
  if exists (select 1 from information_schema.columns
             where table_schema = 'public' and table_name = 'coach_user_intake' and column_name = 'free_text') then
    raise exception 'T-286 self-check: coach_user_intake.free_text still exists';
  end if;
  if exists (select 1 from information_schema.columns
             where table_schema = 'public' and table_name = 'weekly_check_ins' and column_name = 'notes') then
    raise exception 'T-286 self-check: weekly_check_ins.notes still exists';
  end if;
  if not exists (select 1 from information_schema.columns
                 where table_schema = 'public' and table_name = 'exercise_discomforts' and column_name = 'note') then
    raise exception 'T-286 self-check: exercise_discomforts.note must stay';
  end if;
  if not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                 where n.nspname = 'public' and p.proname = 'coach_plan_calendar_for') then
    raise exception 'T-286 self-check: coach_plan_calendar_for must stay';
  end if;

  if exists (select 1 from public.coach_recommendations where jsonb_typeof(input_summary) <> 'object') then
    raise exception 'T-286 self-check: a coach_recommendations.input_summary is not a JSON object';
  end if;
end $$;

drop table pg_temp.rm_snapshot;
