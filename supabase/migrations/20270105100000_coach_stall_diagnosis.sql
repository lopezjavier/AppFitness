-- T-232 (E14, US-105): "Por que no progreso?". Deterministic diagnosis of main lifts that stalled over the last 3 plan
-- weeks done. Rules: planning/07-coach-design.md section 3 "Deload" (stall test) and (7) (four checks, order, thresholds).
-- No new table: everything is read from sets, sessions, the diary, weigh-ins and Health Connect sleep.

-- ---------------------------------------------------------------------------
-- coach_stalled_lifts: the main lifts whose weekly best e1RM did not improve over the last 3 plan weeks done up to
-- p_week. A plan week counts when it is not a deload week and has a completed workout; the weeks need not be contiguous.
-- Improved iff max(w2, w3) > w1 (the deload-trigger test of evaluate_coach_deload; a tie is a stall). A lift needs a value
-- in each of the 3 weeks. Fewer than 3 weeks done: nothing. week_starts are the 3 week start dates, ascending.
-- ---------------------------------------------------------------------------
create function public.coach_stalled_lifts(p_plan_id uuid, p_week integer)
returns table (lift_id uuid, week_starts date[])
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := (select auth.uid());
  v_start date;
  v_ws    date[];
begin
  select coalesce(p.training_start, p.block_start) into v_start
  from public.coach_plans p where p.id = p_plan_id and p.user_id = v_uid;
  if v_start is null or p_week is null or p_week < 1 then
    return;
  end if;

  select array_agg(x.ws order by x.ws) into v_ws
  from (select v_start + 7 * (s.week - 1) as ws
        from public.coach_plan_sessions s
        where s.plan_id = p_plan_id and s.user_id = v_uid and s.week <= p_week
        group by s.week
        having not bool_or(s.deload)
           and exists (select 1 from public.workout_sessions w
                       where w.user_id = v_uid and w.status = 'completed'
                         and (w.started_at at time zone 'utc')::date between v_start + 7 * (s.week - 1)
                                                                          and v_start + 7 * (s.week - 1) + 6)
        order by s.week desc
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

-- ---------------------------------------------------------------------------
-- coach_stall_diagnosis: one element per stalled main lift, with the first cause that fires, in the order sessions,
-- sets (or maintain), protein, sleep, none. Figures are those of the cause only; plan_days is added to sessions.
-- ---------------------------------------------------------------------------
create function public.coach_stall_diagnosis(p_plan_id uuid, p_week integer)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid       uuid := (select auth.uid());
  v_plan      public.coach_plans;
  v_ws        date[];
  v_to        date;
  v_sessions  numeric;
  v_plan_days integer;
  v_pwk       integer;
  v_days      integer;
  v_prot      numeric;
  v_weight    numeric;
  v_target    integer;
  v_gkg       numeric;
  v_prot_ok   boolean := false;
  v_nights    integer;
  v_sleep     numeric;
  v_out       jsonb := '[]'::jsonb;
  r           record;
  v_group     public.muscle_group;
  v_sets      numeric;
  v_planned   numeric;
  v_el        jsonb;
begin
  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid;
  if not found then
    return '[]'::jsonb;
  end if;
  select l.week_starts into v_ws from public.coach_stalled_lifts(p_plan_id, p_week) l limit 1;
  if v_ws is null then
    return '[]'::jsonb;
  end if;
  v_to := v_ws[3] + 6;

  -- Check 1: completed lifting sessions per week in the window.
  select count(*)::numeric / 3 into v_sessions
  from public.workout_sessions s
  where s.user_id = v_uid and s.status = 'completed'
    and exists (select 1 from unnest(v_ws) u where (s.started_at at time zone 'utc')::date between u and u + 6)
    and exists (select 1 from public.workout_sets st
                join public.exercises e on e.id = st.exercise_id
                where st.session_id = s.id and st.completed_at is not null
                  and e.movement_pattern::text is distinct from 'cardio');

  -- What the plan gives per week: its first non-deload week (days and sets per group).
  select min(s.week) into v_pwk
  from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid and not s.deload;
  select count(*)::int into v_plan_days
  from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = v_pwk;

  -- Check 3: protein on complete diary days (at least 40 % of the median daily kcal of the 28 days ending with the window).
  with d as (
    select f.log_date, sum(f.kcal) as kcal, sum(f.protein_g) as p
    from public.food_log_entries f
    where f.user_id = v_uid and f.log_date between v_to - 27 and v_to
    group by f.log_date
  ), m as (
    select (percentile_cont(0.5) within group (order by d.kcal::double precision))::numeric as med from d
  ), c as (
    select d.p from d cross join m
    where d.kcal >= m.med * 0.4
      and exists (select 1 from unnest(v_ws) u where d.log_date between u and u + 6)
  )
  select count(*)::int, avg(c.p) into v_days, v_prot from c;
  select avg(x.w) into v_weight
  from (select avg(b.weight_kg) as w
        from public.body_measurements b
        where b.user_id = v_uid
          and exists (select 1 from unnest(v_ws) u where (b.measured_at at time zone 'utc')::date between u and u + 6)
        group by (b.measured_at at time zone 'utc')::date) x;
  if v_days >= 10 and v_weight is not null and v_weight > 0 then
    v_prot_ok := true;
    v_gkg := v_prot / v_weight;
    v_target := coalesce(nullif(v_plan.nutrition->>'protein_g', '')::numeric::int, ceil(1.6 * v_weight)::int);
  end if;

  -- Check 4: nights with a sleep session (minutes summed per day the session ended).
  select count(*)::int, avg(x.m) / 60 into v_nights, v_sleep
  from (select sum(sl.minutes) as m
        from public.sleep_sessions sl
        where sl.user_id = v_uid
          and exists (select 1 from unnest(v_ws) u where (sl.ended_at at time zone 'utc')::date between u and u + 6)
        group by (sl.ended_at at time zone 'utc')::date) x;

  for r in select l.lift_id from public.coach_stalled_lifts(p_plan_id, p_week) l order by l.lift_id loop
    v_el := null;
    if v_sessions < 2 then
      v_el := jsonb_build_object('cause', 'sessions', 'avg_sessions', round(v_sessions, 1), 'plan_days', v_plan_days);
    else
      select e.muscle_group into v_group from public.exercises e where e.id = r.lift_id;
      if v_plan.intention <> 'strength' then
        select coalesce(sum(g.sets), 0) / 3 into v_sets
        from unnest(v_ws) u
        cross join lateral public.muscle_group_sets(u, u + 6, true) g
        where g.muscle_group = v_group;
        if v_sets < 10 then
          select coalesce(sum((x.ex->>'sets')::numeric), 0) into v_planned
          from public.coach_plan_sessions s
          cross join lateral jsonb_array_elements(s.exercises) x(ex)
          join public.exercises e on e.id = (x.ex->>'exercise_id')::uuid
          where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = v_pwk and e.muscle_group = v_group;
          if v_planned < 10 then
            v_el := jsonb_build_object('cause', 'maintain', 'group', v_group);
          else
            v_el := jsonb_build_object('cause', 'sets', 'avg_sets', round(v_sets, 1), 'group', v_group);
          end if;
        end if;
      end if;
      if v_el is null and v_prot_ok and v_gkg < 1.6 then
        v_el := jsonb_build_object('cause', 'protein', 'avg_protein_g', round(v_prot, 0),
                                   'protein_g_per_kg', round(v_gkg, 2), 'target_g', v_target);
      end if;
      if v_el is null and v_nights >= 10 and v_sleep < 7 then
        v_el := jsonb_build_object('cause', 'sleep', 'avg_sleep_h', round(v_sleep, 2));
      end if;
      if v_el is null then
        v_el := jsonb_build_object('cause', 'none', 'protein_gate_missing', not v_prot_ok);
      end if;
    end if;
    v_out := v_out || jsonb_build_array(jsonb_build_object('exercise_id', r.lift_id) || v_el);
  end loop;
  return v_out;
end;
$$;

revoke execute on function public.coach_stalled_lifts(uuid, integer) from public, anon;
revoke execute on function public.coach_stall_diagnosis(uuid, integer) from public, anon;
grant execute on function public.coach_stalled_lifts(uuid, integer) to authenticated;
grant execute on function public.coach_stall_diagnosis(uuid, integer) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_week_summary (20270104100100) plus 'stalls': the diagnosis of lifts that were not already stalled one plan week
-- earlier, so a stall is shown once, in the summary of the week it is first found. Every other key is unchanged.
-- ---------------------------------------------------------------------------
create or replace function public.coach_week_summary(p_today date)
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
  v_stalls  jsonb;
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

  -- First found: stalled now and not stalled one plan week earlier (fewer than 3 weeks done there: not stalled).
  select coalesce(jsonb_agg(s.e order by s.ord), '[]'::jsonb) into v_stalls
  from jsonb_array_elements(public.coach_stall_diagnosis(v_plan.id, v_week)) with ordinality s(e, ord)
  where not exists (select 1 from public.coach_stalled_lifts(v_plan.id, v_week - 1) p
                    where p.lift_id = (s.e->>'exercise_id')::uuid);

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
    'deload', coalesce((v_w->>'deload')::boolean, false),
    'stalls', v_stalls);
end;
$$;

revoke execute on function public.coach_week_summary(date) from public, anon;
grant execute on function public.coach_week_summary(date) to authenticated;
