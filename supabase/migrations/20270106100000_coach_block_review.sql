-- T-233 (E14, US-101, US-103): the end-of-block review and the phase change. After the last plan week of the accepted
-- block the server freezes the review inputs once per block, evaluates the phase triggers of 07 section 12.3 and stores
-- at most one proposal; "Aceptar" builds a proposed plan (the vehicle of the next block) and "Seguir como estoy" stores
-- the choice. Nothing applies by itself after the last week (guards at the end of this file). Rules: planning/04 8.10.

-- ---------------------------------------------------------------------------
-- coach_block_reviews: one row per plan. Read-only for the owner; written by the security definer RPCs below.
-- ---------------------------------------------------------------------------
create table public.coach_block_reviews (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  plan_id        uuid not null references public.coach_plans (id) on delete cascade,
  ended_on       date not null,
  -- {adherence, lifts, groups, weight, outcomes, waist, stalls}, frozen when the review opens.
  inputs         jsonb not null constraint coach_block_reviews_inputs_object check (jsonb_typeof(inputs) = 'object'),
  -- {to_phase, reason, figures} or null.
  phase_proposal jsonb null constraint coach_block_reviews_proposal_object
    check (phase_proposal is null or jsonb_typeof(phase_proposal) = 'object'),
  phase_decision text null constraint coach_block_reviews_decision_values check (phase_decision in ('accepted', 'kept')),
  decided_at     timestamptz null,
  next_plan_id   uuid null references public.coach_plans (id) on delete set null,
  created_at     timestamptz not null default now(),
  constraint coach_block_reviews_plan_key unique (plan_id),
  constraint coach_block_reviews_decided_iff_decision check ((decided_at is null) = (phase_decision is null)),
  constraint coach_block_reviews_decision_needs_proposal check (phase_decision is null or phase_proposal is not null)
);

create index coach_block_reviews_user_created_idx on public.coach_block_reviews (user_id, created_at desc);
create index coach_block_reviews_next_plan_idx on public.coach_block_reviews (next_plan_id);

alter table public.coach_block_reviews enable row level security;
alter table public.coach_block_reviews force row level security;

create policy coach_block_reviews_select_own on public.coach_block_reviews
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_block_reviews from anon, authenticated;
grant select on table public.coach_block_reviews to authenticated;

-- ---------------------------------------------------------------------------
-- muscle_group_sets (20270104100100) plus an explicit owner filter, so it is also correct when called from a security
-- definer function (where RLS does not narrow the rows). Behaviour for the caller is unchanged.
-- ---------------------------------------------------------------------------
create or replace function public.muscle_group_sets(p_from date, p_to date, p_strength_only boolean)
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
    where s.user_id = (select auth.uid())
      and s.status = 'completed'
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

-- ---------------------------------------------------------------------------
-- coach_block_end: the last day of the plan's block (start + 7 * last plan week - 1); null without sessions.
-- One definition for every "after the block" guard.
-- ---------------------------------------------------------------------------
create function public.coach_block_end(p_plan_id uuid)
returns date
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(p.training_start, p.block_start) + 7 * max(s.week)::int - 1
  from public.coach_plans p
  join public.coach_plan_sessions s on s.plan_id = p.id and s.user_id = p.user_id
  where p.id = p_plan_id and p.user_id = (select auth.uid())
  group by p.id, p.training_start, p.block_start;
$$;

revoke execute on function public.coach_block_end(uuid) from public, anon;
grant execute on function public.coach_block_end(uuid) to authenticated;

-- The review row as the clients read it.
create function public.coach_block_review_json(r public.coach_block_reviews)
returns jsonb
language sql
immutable
security invoker
set search_path = ''
as $$
  select jsonb_build_object(
    'id', r.id, 'plan_id', r.plan_id, 'ended_on', r.ended_on, 'inputs', r.inputs,
    'phase_proposal', r.phase_proposal, 'phase_decision', r.phase_decision,
    'decided_at', r.decided_at, 'next_plan_id', r.next_plan_id);
$$;

revoke execute on function public.coach_block_review_json(public.coach_block_reviews) from public, anon;
grant execute on function public.coach_block_review_json(public.coach_block_reviews) to authenticated;

-- ---------------------------------------------------------------------------
-- coach_block_review_build: {inputs, phase_proposal} of a finished block, from recorded numbers only. Internal: called by
-- open_coach_block_review (security definer); every statement filters by auth.uid().
-- ---------------------------------------------------------------------------
create function public.coach_block_review_build(p_plan_id uuid, p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid       uuid := (select auth.uid());
  v_plan      public.coach_plans;
  v_start     date;
  v_end       date;
  v_weeks     integer;
  v_pweeks    jsonb;
  v_adh       jsonb;
  v_lifts     jsonb := '[]'::jsonb;
  v_lift      record;
  v_wk        jsonb;
  v_groups    jsonb;
  v_first_avg numeric;
  v_last_avg  numeric;
  v_trend     jsonb;
  v_rate      numeric;
  v_slope     numeric;
  v_weight    jsonb;
  v_outcomes  jsonb := '[]'::jsonb;
  v_o         jsonb;
  v_kind      text;
  v_key       text;
  v_target    numeric;
  v_current   numeric;
  v_reached   boolean;
  v_wl        numeric;
  v_wd        date;
  v_wp        numeric;
  v_wpd       date;
  v_waist     jsonb := null;
  v_stalled   uuid[];
  v_stalls    jsonb;
  v_calph     text;
  v_deficit   integer;
  v_floor     integer;
  v_prop      jsonb := null;
  v_row       record;
  v_blocks    integer := 0;
  v_after_cut boolean := false;
  v_goal      text;
begin
  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid;
  v_start := coalesce(v_plan.training_start, v_plan.block_start);
  v_end := public.coach_block_end(p_plan_id);
  select max(s.week) into v_weeks from public.coach_plan_sessions s where s.plan_id = p_plan_id and s.user_id = v_uid;

  -- Adherence over every plan week, a deload week included.
  v_pweeks := public.coach_plan_weeks(p_plan_id, p_today);
  select jsonb_build_object('completed', coalesce(sum((w->>'completed')::int), 0),
                            'planned', coalesce(sum((w->>'planned')::int), 0),
                            'pct', case when coalesce(sum((w->>'planned')::int), 0) = 0 then null
                                        else round(sum((w->>'completed')::int) * 100.0 / sum((w->>'planned')::int)) end)
  into v_adh
  from jsonb_array_elements(v_pweeks) w;

  select coalesce(array_agg(l.lift_id), '{}') into v_stalled from public.coach_stalled_lifts(p_plan_id, v_weeks) l;

  -- Main lifts of the block (role main in week 1, in order of appearance); weekly best e1RM without deload weeks.
  for v_lift in
    select x.id::uuid as eid
    from (select e.value->>'exercise_id' as id, min(s.session_index * 100 + e.ordinality) as first_pos
          from public.coach_plan_sessions s
          cross join lateral jsonb_array_elements(s.exercises) with ordinality e(value, ordinality)
          where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = 1 and e.value->>'role' = 'main'
          group by e.value->>'exercise_id') x
    order by x.first_pos
  loop
    select coalesce(jsonb_agg(jsonb_build_object('week', b.wk, 'e1rm', b.e) order by b.wk), '[]'::jsonb)
    into v_wk
    from (select ((ws.started_at at time zone 'utc')::date - v_start) / 7 + 1 as wk,
                 max(public.coach_e1rm(st.weight_kg, st.reps)) as e
          from public.workout_sessions ws
          join public.workout_sets st on st.session_id = ws.id
          where ws.user_id = v_uid and ws.status = 'completed' and st.exercise_id = v_lift.eid
            and st.completed_at is not null
            and (st.set_type <> 'warm_up' or public.warm_up_included())
            and (ws.started_at at time zone 'utc')::date between v_start and v_end
          group by 1) b
    where b.e is not null
      and not exists (select 1 from public.coach_plan_sessions s
                      where s.plan_id = p_plan_id and s.user_id = v_uid and s.week = b.wk and s.deload);
    v_lifts := v_lifts || jsonb_build_array(jsonb_build_object(
      'exercise_id', v_lift.eid,
      'weeks', v_wk,
      'first_e1rm', v_wk->0->'e1rm',
      'last_e1rm', v_wk->(jsonb_array_length(v_wk) - 1)->'e1rm',
      'change_kg', case when jsonb_array_length(v_wk) = 0 then null
                        else to_jsonb((v_wk->(jsonb_array_length(v_wk) - 1)->>'e1rm')::numeric
                                      - (v_wk->0->>'e1rm')::numeric) end,
      'stalled', v_lift.eid = any (v_stalled)));
  end loop;

  select coalesce(jsonb_agg(jsonb_build_object('muscle_group', g.muscle_group, 'sets', g.sets)
                            order by g.sets desc, g.muscle_group), '[]'::jsonb)
  into v_groups
  from public.muscle_group_sets(v_start, v_end, true) g;

  -- Weight: average of the daily means in plan week 1 and in the last plan week; trend up to the last day.
  select round(avg(d.w), 2) into v_first_avg
  from (select avg(weight_kg) as w from public.body_measurements
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and v_start + 6
        group by (measured_at at time zone 'UTC')::date) d;
  select round(avg(d.w), 2) into v_last_avg
  from (select avg(weight_kg) as w from public.body_measurements
        where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_end - 6 and v_end
        group by (measured_at at time zone 'UTC')::date) d;
  v_trend := public.coach_weight_trend(v_end);
  v_rate := nullif(v_plan.nutrition->>'rate_kg_week', '')::numeric;
  v_slope := (v_trend->>'slope')::numeric;
  v_weight := jsonb_build_object('first_avg_kg', v_first_avg, 'last_avg_kg', v_last_avg,
                                 'slope_kg_week', v_trend->'slope', 'interval_low', v_trend->'low',
                                 'interval_high', v_trend->'high', 'target_rate_kg_week', to_jsonb(v_rate));

  -- Outcomes: informational only (nothing reads "reached" as a trigger).
  for v_o in select value from jsonb_array_elements(v_plan.outcomes) loop
    v_kind := v_o->>'kind';
    v_key := v_o->>'key';
    v_target := nullif(v_o->>'target_value', '')::numeric;
    v_current := null;
    v_reached := false;
    if v_kind = 'weight' then
      v_current := v_last_avg;
      v_reached := v_current is not null and v_target is not null and v_rate is not null
                   and case when v_rate < 0 then v_current <= v_target
                            when v_rate > 0 then v_current >= v_target
                            else false end;
    elsif v_kind = 'e1rm' then
      select max(public.coach_e1rm(st.weight_kg, st.reps)) into v_current
      from public.workout_sessions ws
      join public.workout_sets st on st.session_id = ws.id
      join public.exercises e on e.id = st.exercise_id
      where ws.user_id = v_uid and ws.status = 'completed' and coalesce(e.key, e.id::text) = v_key
        and st.completed_at is not null
        and (st.set_type <> 'warm_up' or public.warm_up_included())
        and (ws.started_at at time zone 'utc')::date between v_start and v_end;
      v_reached := v_current is not null and v_target is not null and v_current >= v_target;
    end if;
    v_outcomes := v_outcomes || jsonb_build_array(jsonb_build_object(
      'kind', v_kind, 'key', v_key, 'target_value', v_target, 'unit', v_o->>'unit', 'deadline', v_o->>'deadline',
      'current_value', v_current, 'reached', v_reached));
  end loop;

  -- Waist: the latest on or before the last day against the latest one at least 28 days older. Never a trigger.
  select c.value_cm, (c.measured_at at time zone 'UTC')::date into v_wl, v_wd
  from public.body_circumferences c
  where c.user_id = v_uid and c.site = 'waist' and (c.measured_at at time zone 'UTC')::date <= v_end
  order by c.measured_at desc limit 1;
  if v_wd is not null then
    select c.value_cm, (c.measured_at at time zone 'UTC')::date into v_wp, v_wpd
    from public.body_circumferences c
    where c.user_id = v_uid and c.site = 'waist' and (c.measured_at at time zone 'UTC')::date <= v_wd - 28
    order by c.measured_at desc limit 1;
    if v_wpd is not null then
      v_waist := jsonb_build_object('delta_cm', v_wl - v_wp, 'from_date', v_wpd, 'to_date', v_wd,
                                    'within_margin', abs(v_wl - v_wp) < 3.0);
    end if;
  end if;

  v_stalls := public.coach_stall_diagnosis(p_plan_id, v_weeks);

  -- Phase proposal: the first rule that fires, on measured data only (07 section 12.3).
  v_calph := coalesce(v_plan.nutrition->>'phase', v_plan.phase::text);
  if v_calph = 'cut' then
    v_deficit := (public.coach_phase_history(p_today)->>'deficit_weeks_done')::int;
    v_floor := nullif(v_plan.nutrition->>'floor_kcal', '')::int;
    if v_deficit >= 12 then
      v_prop := jsonb_build_object('to_phase', 'maintenance', 'reason', 'cut_length',
                                   'figures', jsonb_build_object('deficit_weeks', v_deficit));
    elsif v_plan.nutrition->>'limited_by' = 'floor' and v_rate is not null and v_rate < 0 and v_slope is not null
          and v_slope > 0.5 * v_rate
          and not exists (select 1 from public.coach_recommendations r
                          where r.user_id = v_uid and r.plan_id = p_plan_id and r.kind = 'calorie_change'
                            and r.status = 'accepted' and v_floor is not null
                            and (r.payload->>'to_kcal')::int > v_floor) then
      v_prop := jsonb_build_object('to_phase', 'maintenance', 'reason', 'floor_limited',
                                   'figures', jsonb_build_object('slope_kg_week', v_slope,
                                                                 'target_rate_kg_week', v_rate));
    end if;
  elsif v_calph = 'maintenance' then
    v_goal := case v_plan.intention when 'fat_loss' then 'cut' when 'muscle' then 'gain'
                                    when 'recomposition' then 'recomposition' else null end;
    if v_goal is not null then
      for v_row in
        select coalesce(p.nutrition->>'phase', p.phase::text) as cp
        from public.coach_plans p
        where p.user_id = v_uid and p.status in ('superseded', 'accepted')
          and exists (select 1 from public.coach_plan_sessions s where s.plan_id = p.id)
          and (p.block_start, p.created_at) <= (v_plan.block_start, v_plan.created_at)
        order by p.block_start desc, p.created_at desc
      loop
        if v_row.cp = 'maintenance' then
          v_blocks := v_blocks + 1;
        else
          v_after_cut := v_row.cp = 'cut';
          exit;
        end if;
      end loop;
      if v_after_cut and v_blocks >= 1 then
        v_prop := jsonb_build_object('to_phase', v_goal, 'reason', 'back_to_goal',
                                     'figures', jsonb_build_object('blocks_in_maintenance', v_blocks));
      end if;
    end if;
  end if;

  return jsonb_build_object(
    'inputs', jsonb_build_object('adherence', v_adh, 'lifts', v_lifts, 'groups', v_groups, 'weight', v_weight,
                                 'outcomes', v_outcomes, 'waist', v_waist, 'stalls', v_stalls),
    'phase_proposal', v_prop);
end;
$$;

revoke execute on function public.coach_block_review_build(uuid, date) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- open_coach_block_review: null until the day after the accepted block's last day; then the stored review (created on
-- the first call, never recomputed, so later logs do not rewrite it).
-- ---------------------------------------------------------------------------
create function public.open_coach_block_review(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_end   date;
  v_rev   public.coach_block_reviews;
  v_built jsonb;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted' and folder_id is not null;
  if not found then
    return null;
  end if;
  v_end := public.coach_block_end(v_plan.id);
  if v_end is null or p_today <= v_end then
    return null;
  end if;

  select * into v_rev from public.coach_block_reviews where plan_id = v_plan.id and user_id = v_uid;
  if not found then
    v_built := public.coach_block_review_build(v_plan.id, p_today);
    insert into public.coach_block_reviews (user_id, plan_id, ended_on, inputs, phase_proposal)
    values (v_uid, v_plan.id, v_end, v_built->'inputs',
            case when jsonb_typeof(v_built->'phase_proposal') = 'object' then v_built->'phase_proposal' end)
    on conflict (plan_id) do nothing;
    select * into v_rev from public.coach_block_reviews where plan_id = v_plan.id and user_id = v_uid;
  end if;
  return public.coach_block_review_json(v_rev);
end;
$$;

-- ---------------------------------------------------------------------------
-- keep_coach_phase: "Seguir como estoy". Stores the choice; builds no plan.
-- ---------------------------------------------------------------------------
create function public.keep_coach_phase(p_review_id uuid)
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
  select * into v_rev from public.coach_block_reviews where id = p_review_id and user_id = v_uid for update;
  if not found then
    raise exception 'review not found' using errcode = 'P0002';
  end if;
  if v_rev.phase_proposal is null or v_rev.phase_decision is not null then
    raise exception 'no open phase proposal' using errcode = '55000';
  end if;
  update public.coach_block_reviews set phase_decision = 'kept', decided_at = now()
    where id = v_rev.id returning * into v_rev;
  return public.coach_block_review_json(v_rev);
end;
$$;

-- ---------------------------------------------------------------------------
-- accept_coach_phase: "Aceptar". The figures come from the coach function (plan_phase_change, no model, no usage);
-- propose_coach_plan re-checks every invariant and stores the new PROPOSED plan (the vehicle of the next block). Nothing
-- touches nutrition_goals or the accepted plan. p_block_start defaults to today (UTC).
-- ---------------------------------------------------------------------------
create function public.accept_coach_phase(
  p_review_id uuid,
  p_nutrition jsonb,
  p_outcomes jsonb,
  p_method jsonb,
  p_input_summary jsonb,
  p_block_start date default null
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(coalesce(p_block_start, (now() at time zone 'utc')::date));
  v_rev   public.coach_block_reviews;
  v_plan  public.coach_plans;
  v_res   jsonb;
begin
  select * into v_rev from public.coach_block_reviews where id = p_review_id and user_id = v_uid for update;
  if not found then
    raise exception 'review not found' using errcode = 'P0002';
  end if;
  if v_rev.phase_proposal is null or v_rev.phase_decision is not null then
    raise exception 'no open phase proposal' using errcode = '55000';
  end if;
  if p_nutrition is null or jsonb_typeof(p_nutrition) <> 'object'
     or p_nutrition->>'phase' is distinct from v_rev.phase_proposal->>'to_phase' then
    raise exception 'phase does not match the proposal' using errcode = '22023';
  end if;
  select * into v_plan from public.coach_plans where id = v_rev.plan_id and user_id = v_uid;

  v_res := public.propose_coach_plan(coalesce(p_block_start, (now() at time zone 'utc')::date), v_plan.intention,
                                     p_nutrition, p_outcomes, p_method, p_input_summary);

  update public.coach_block_reviews
    set phase_decision = 'accepted', decided_at = now(), next_plan_id = (v_res->>'plan_id')::uuid
    where id = v_rev.id returning * into v_rev;
  return jsonb_build_object('review', public.coach_block_review_json(v_rev), 'plan_id', v_res->'plan_id');
end;
$$;

revoke execute on function public.open_coach_block_review(date) from public, anon;
revoke execute on function public.keep_coach_phase(uuid) from public, anon;
revoke execute on function public.accept_coach_phase(uuid, jsonb, jsonb, jsonb, jsonb, date) from public, anon;
grant execute on function public.open_coach_block_review(date) to authenticated;
grant execute on function public.keep_coach_phase(uuid) to authenticated;
grant execute on function public.accept_coach_phase(uuid, jsonb, jsonb, jsonb, jsonb, date) to authenticated;

-- ---------------------------------------------------------------------------
-- Guards: nothing applies by itself after the block's last day. Each function below is its latest body plus one early
-- exit. evaluate_coach_deload (reason past_week_4 / short_block) and request_coach_deload (coach_deload_no_next_week)
-- already refuse after the last day. What the user taps (decide_*, undo_coach_change) stays allowed.
-- ---------------------------------------------------------------------------

create or replace function public.evaluate_coach_calorie_change(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := public.coach_guard(p_today);
  v_plan    public.coach_plans;
  v_pending uuid;
  v_rate    numeric;
  v_target  integer;
  v_floor   integer;
  v_adj     integer;
  v_t0      jsonb := public.coach_weight_trend(p_today);
  v_t1      jsonb := public.coach_weight_trend(p_today - 7);
  v_slope   numeric;
  v_low0    numeric;
  v_high0   numeric;
  v_low1    numeric;
  v_high1   numeric;
  v_below   boolean;
  v_above   boolean;
  v_size    integer;
  v_cap     integer;
  v_change  integer;
  v_new     integer;
  v_applied integer;
  v_base    integer;
  v_phase   text;
  v_limited text;
  v_exp     numeric;
  v_w       numeric;
  v_delta   numeric;
  v_min     numeric;
  v_max     numeric;
  v_rec     uuid;
begin
  select * into v_plan from public.coach_plans where user_id = v_uid and status = 'accepted';
  if not found or coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'edited');
  end if;

  select id into v_pending from public.coach_recommendations
    where user_id = v_uid and kind = 'calorie_change' and status = 'pending'
      and created_at >= public.coach_week_start(p_today)::timestamp at time zone 'UTC';
  if v_pending is not null then
    return jsonb_build_object('status', 'pending', 'reason', 'already_pending_this_week', 'recommendation_id', v_pending);
  end if;

  -- After the block's last day nothing is proposed until the review (a plan with no saved block still proposes).
  if v_plan.folder_id is not null and p_today > coalesce(public.coach_block_end(v_plan.id), 'infinity'::date) then
    return jsonb_build_object('status', 'none', 'reason', 'block_ended');
  end if;

  -- Two "Ahora no" in the block: nothing more until the review.
  if (select count(*) from public.coach_recommendations
      where user_id = v_uid and kind = 'calorie_change' and plan_id = v_plan.id and status = 'discarded'
        and created_at >= v_plan.block_start::timestamp at time zone 'UTC') >= 2 then
    return jsonb_build_object('status', 'none', 'reason', 'declined_twice');
  end if;

  if exists (select 1 from public.coach_recommendations
             where user_id = v_uid and kind = 'calorie_change' and status in ('accepted', 'discarded')
               and decided_at > now() - interval '14 days') then
    return jsonb_build_object('status', 'none', 'reason', 'recent_change');
  end if;

  if not coalesce((public.coach_week_gate(p_today)->>'ok')::boolean, false)
     or not coalesce((public.coach_week_gate(p_today - 7)->>'ok')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'missing_records');
  end if;

  if not coalesce((public.coach_intake(p_today)->>'reliable')::boolean, false) then
    return jsonb_build_object('status', 'none', 'reason', 'unreliable_intake');
  end if;

  v_rate := (v_plan.nutrition->>'rate_kg_week')::numeric;
  v_slope := (v_t0->>'slope')::numeric;
  v_low0 := (v_t0->>'low')::numeric;
  v_high0 := (v_t0->>'high')::numeric;
  v_low1 := (v_t1->>'low')::numeric;
  v_high1 := (v_t1->>'high')::numeric;
  if v_low0 is null or v_high0 is null or v_low1 is null or v_high1 is null or v_rate is null then
    return jsonb_build_object('status', 'none', 'reason', 'inside_interval');
  end if;
  -- Strictly outside on both weeks and on the same side; on the edge counts as inside (04 section 7.6).
  v_below := v_rate < v_low0 and v_rate < v_low1;
  v_above := v_rate > v_high0 and v_rate > v_high1;
  if not v_below and not v_above then
    return jsonb_build_object('status', 'none', 'reason', 'inside_interval');
  end if;

  v_target := (v_plan.nutrition->>'target_kcal')::int;
  v_floor := coalesce((v_plan.nutrition->>'floor_kcal')::int, 0);
  v_adj := (v_plan.nutrition->>'calorie_adjustment')::int;
  -- 7 700 kcal/kg over 7 days = 1 100 kcal/day per kg/week; rounded to 10, at least 50, at most 10 % of the target.
  v_size := greatest(50, (round(abs(v_slope - v_rate) * 1100 / 10) * 10)::int);
  v_cap := (floor(v_target * 0.10 / 10) * 10)::int;
  v_size := least(v_size, v_cap);
  v_change := case when v_below then -v_size else v_size end;
  v_new := v_target + v_change;

  -- Phase range (04 section 8.7 / 8.9) against the expenditure in use, by the phase and never by the intention.
  v_phase := coalesce(v_plan.nutrition->>'phase',
                      case v_plan.intention when 'fat_loss' then 'cut' when 'muscle' then 'gain' else 'maintenance' end);
  v_limited := v_plan.nutrition->>'limited_by';
  v_exp := (public.coach_compute(p_today)->'total_expenditure'->>'value')::numeric;
  select weight_kg into v_w from public.body_measurements
    where user_id = v_uid order by measured_at desc limit 1;

  if v_exp is not null and v_w is not null then
    if v_phase = 'gain' then
      v_min := 0.25 * v_w * 11;
      v_max := 0.5 * v_w * 11 + 10;
    elsif v_phase = 'cut' then
      v_min := -1.0 * v_w * 11 - 10;
      v_max := -0.5 * v_w * 11;
    else
      v_min := -0.25 * v_w * 11;
      v_max := 0.25 * v_w * 11;
    end if;
  end if;

  if v_new < v_floor then
    return jsonb_build_object('status', 'none', 'reason', 'floor');
  end if;
  if v_adj + v_change < -500 then
    return jsonb_build_object('status', 'none', 'reason', 'deficit_cap');
  end if;
  if v_adj + v_change > 750 then
    return jsonb_build_object('status', 'none', 'reason', 'rate_range');
  end if;

  if v_exp is not null and v_w is not null then
    v_delta := v_new - v_exp;
    -- Mirrors intakeRangeViolation (nutritionRules.ts): a limiter may make a plan slower than the range, never faster.
    -- A change that moves the target toward the range is never refused for being outside it (a gaining maintenance
    -- user does get a decrease, down to the range bottom): only a step past an edge, away from the range, is.
    if v_delta > v_max + 1e-9 and v_change > 0 and not (case when v_phase = 'cut' then v_limited is not null
                                                              else coalesce(v_limited = 'floor', false) end) then
      return jsonb_build_object('status', 'none', 'reason', 'rate_range');
    end if;
    if v_delta < v_min - 1e-9 and v_change < 0 and not (v_phase = 'gain' and v_limited is not null) then
      return jsonb_build_object('status', 'none', 'reason', 'rate_range');
    end if;
  end if;

  -- Band: the changes applied in this block plus this one stay within 10 % of the block's accepted calories.
  select coalesce(sum((payload->>'change_kcal')::int), 0)::int into v_applied
    from public.coach_recommendations
    where user_id = v_uid and kind = 'calorie_change' and plan_id = v_plan.id and status = 'accepted'
      and created_at >= v_plan.block_start::timestamp at time zone 'UTC';
  v_base := v_target - v_applied;
  if abs(v_applied + v_change) > v_base * 0.10 then
    return jsonb_build_object('status', 'none', 'reason', 'band');
  end if;

  -- A pending proposal from an earlier week is replaced; never two pending.
  update public.coach_recommendations set status = 'superseded', decided_at = now()
    where user_id = v_uid and kind = 'calorie_change' and status = 'pending';

  insert into public.coach_recommendations (user_id, plan_id, kind, payload, reason, input_summary)
  values (v_uid, v_plan.id, 'calorie_change',
          jsonb_build_object('from_kcal', v_target, 'to_kcal', v_new, 'change_kcal', v_change,
                             'change_pct', round(v_change * 100.0 / v_target, 1),
                             'slope_kg_week', v_slope, 'interval_low', v_low0, 'interval_high', v_high0,
                             'rate_kg_week', v_rate, 'base_kcal', v_base, 'band_used_kcal', v_applied, 'weeks', 2),
          'rate_outside_interval', public.coach_summary(p_today))
  returning id into v_rec;

  return jsonb_build_object('status', 'created', 'reason', 'rate_outside_interval', 'recommendation_id', v_rec);
end;
$$;

create or replace function public.apply_coach_progression(p_session_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_s       public.workout_sessions;
  v_plan    public.coach_plans;
  v_item    jsonb;
  v_ex      record;
  v_exid    uuid;
  v_rmin    smallint;
  v_rmax    smallint;
  v_base    numeric;
  v_step    numeric;
  v_new     numeric;
  v_before  jsonb;
  v_after   jsonb;
  v_target  numeric;
begin
  if p_session_id is null then
    raise exception 'session id is required' using errcode = '22023';
  end if;
  if v_uid is null then
    return '[]'::jsonb;
  end if;

  select * into v_s from public.workout_sessions s
  where s.id = p_session_id and s.user_id = v_uid and s.status = 'completed';
  if not found or v_s.routine_id is null then
    return '[]'::jsonb;
  end if;

  select p.* into v_plan
  from public.coach_plans p
  join public.routines r on r.id = v_s.routine_id and r.user_id = v_uid and r.folder_id = p.folder_id
  where p.user_id = v_uid and p.status = 'accepted' and p.folder_id is not null;
  if not found then
    return '[]'::jsonb;
  end if;

  -- A session started after the block's last day writes nothing (the review comes first).
  if (v_s.started_at at time zone 'utc')::date > coalesce(public.coach_block_end(v_plan.id), 'infinity'::date) then
    return '[]'::jsonb;
  end if;

  -- A no-range flag closes by itself once the exercise has a range in a routine of the block.
  update public.coach_changes c
  set status = 'decided', decision = 'range_added', decided_at = now()
  where c.user_id = v_uid and c.plan_id = v_plan.id and c.kind = 'no_range_flag' and c.status = 'pending'
    and exists (select 1 from public.routines r
                join public.routine_exercises re on re.routine_id = r.id
                where r.user_id = v_uid and r.folder_id = v_plan.folder_id
                  and re.exercise_id = c.exercise_id and re.rep_min is not null);

  if not exists (select 1 from public.coach_changes c
                 where c.user_id = v_uid and c.source_session_id = p_session_id)
     and not public.coach_session_in_deload(v_uid, p_session_id) then

    for v_item in
      select e from jsonb_array_elements(public.progression_decision_core(v_uid, p_session_id, true)) e
    loop
      v_exid := (v_item ->> 'exercise_id')::uuid;
      v_rmin := (v_item ->> 'rep_min')::smallint;
      v_rmax := (v_item ->> 'rep_max')::smallint;
      v_base := (v_item ->> 'base_weight_kg')::numeric;
      v_before := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax);
      if v_before = '[]'::jsonb or v_base is null or v_base <= 0 then
        continue;
      end if;

      if v_item ->> 'action' = 'raise_weight' then
        -- A flag waiting for the user blocks the next raise of that exercise.
        if exists (select 1 from public.coach_changes c
                   where c.user_id = v_uid and c.plan_id = v_plan.id and c.exercise_id = v_exid
                     and c.kind in ('load_flag', 'no_range_flag') and c.status = 'pending') then
          continue;
        end if;
        v_step := public.coach_load_step(v_uid, v_exid);
        if v_step / v_base > 0.10 then
          insert into public.coach_changes
            (plan_id, kind, exercise_id, source_session_id, before_values, reason, figures, status)
          values (v_plan.id, 'load_flag', v_exid, p_session_id, v_before, 'step_over_10_percent',
                  jsonb_build_object('current_kg', v_base, 'step_kg', v_step, 'next_kg', v_base + v_step,
                                     'percent', round(v_step / v_base * 100, 1),
                                     'rep_min', v_rmin, 'rep_max', v_rmax),
                  'pending')
          on conflict do nothing;
        else
          v_new := v_base + v_step;
          -- The user already planned this weight or more: the coach never lowers a plan on a raise.
          if (select max(public.coach_top_weight(b)) from jsonb_array_elements(v_before) b) >= v_new then
            continue;
          end if;
          perform public.coach_write_load(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax, v_new, true);
          v_after := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax);
          insert into public.coach_changes
            (plan_id, kind, exercise_id, source_session_id, before_values, after_values, reason, figures, status)
          values (v_plan.id, 'load_raise', v_exid, p_session_id, v_before, v_after, 'all_sets_at_max',
                  jsonb_build_object('weight_from_kg', v_base, 'weight_to_kg', v_new,
                                     'reps_best', v_item -> 'reps_best', 'rpe_max', v_item -> 'rpe_max',
                                     'sessions', v_item -> 'sessions', 'step_kg', v_step),
                  'applied')
          on conflict do nothing;
        end if;

      elsif v_item ->> 'action' = 'lower_weight' then
        v_step := public.coach_load_step(v_uid, v_exid);
        v_target := v_base * 0.95;
        v_new := case when v_step <= 0 then v_target else round(v_target / v_step) * v_step end;
        if v_new >= v_base then
          v_new := v_base - v_step;
        end if;
        v_new := greatest(v_new, 0);
        if v_new >= v_base then
          continue;
        end if;
        perform public.coach_write_load(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax, v_new, false);
        v_after := public.coach_targets_snapshot(v_uid, v_plan.folder_id, v_exid, v_rmin, v_rmax);
        insert into public.coach_changes
          (plan_id, kind, exercise_id, source_session_id, before_values, after_values, reason, figures, status)
        values (v_plan.id, 'load_drop', v_exid, p_session_id, v_before, v_after, v_item ->> 'reason',
                jsonb_build_object('weight_from_kg', v_base, 'weight_to_kg', v_new,
                                   'reps_best', v_item -> 'reps_best', 'rpe_max', v_item -> 'rpe_max',
                                   'sessions', v_item -> 'sessions', 'step_kg', v_step, 'percent', 5),
                'applied')
        on conflict do nothing;

      elsif v_item ->> 'reason' = 'kept_discomfort' then
        insert into public.coach_changes
          (plan_id, kind, exercise_id, source_session_id, before_values, after_values, reason, figures, status)
        values (v_plan.id, 'load_kept', v_exid, p_session_id, v_before, v_before, 'kept_discomfort',
                jsonb_build_object('weight_kg', v_base, 'reps_best', v_item -> 'reps_best',
                                   'rpe_max', v_item -> 'rpe_max', 'level', v_item -> 'discomfort_level'),
                'applied')
        on conflict do nothing;
      end if;
    end loop;

    -- Exercises of the block done without a rep range: flag only, nothing is written.
    for v_ex in
      select distinct ws.exercise_id
      from public.workout_sets ws
      where ws.session_id = p_session_id and ws.rep_min is null
        and ws.set_type = 'working' and ws.completed_at is not null
        and exists (select 1 from public.routines r
                    join public.routine_exercises re on re.routine_id = r.id
                    where r.user_id = v_uid and r.folder_id = v_plan.folder_id
                      and re.exercise_id = ws.exercise_id and re.rep_min is null)
    loop
      insert into public.coach_changes (plan_id, kind, exercise_id, source_session_id, reason, status)
      values (v_plan.id, 'no_range_flag', v_ex.exercise_id, p_session_id, 'no_rep_range', 'pending')
      on conflict do nothing;
    end loop;
  end if;

  return coalesce((select jsonb_agg(public.coach_change_json(c) order by c.created_at, c.id)
                   from public.coach_changes c
                   where c.user_id = v_uid and c.source_session_id = p_session_id), '[]'::jsonb);
end;
$$;

create or replace function public.apply_coach_diet_break(p_today date)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := public.coach_guard(p_today);
  v_plan  public.coach_plans;
  v_week  integer;
  v_src   jsonb;
  v_goal  public.nutrition_goals;
  v_same  boolean;
begin
  select * into v_plan from public.coach_plans
    where user_id = v_uid and status = 'accepted';
  if not found
     or coalesce(v_plan.phase_options->>'diet_break', 'false') <> 'true'
     or jsonb_typeof(v_plan.nutrition->'diet_break') is distinct from 'object'
     or coalesce((v_plan.nutrition->>'edited_by_user')::boolean, false)
     or not (v_plan.folder_id is not null
             or exists (select 1 from public.coach_plan_sessions s
                        where s.plan_id = v_plan.id and s.user_id = v_uid and s.routine_id is not null)) then
    return jsonb_build_object('applied', false, 'week', 0, 'target_kcal', null);
  end if;

  v_week := public.coach_plan_week(v_plan.id, p_today);
  -- After the block's last day the goal stays as it is (no revert to the base figures by itself).
  if p_today > coalesce(public.coach_block_end(v_plan.id), 'infinity'::date) then
    return jsonb_build_object('applied', false, 'week', v_week, 'target_kcal', null);
  end if;
  v_src := case when v_week = 4 then v_plan.nutrition->'diet_break' else v_plan.nutrition end;

  select * into v_goal from public.nutrition_goals where user_id = v_uid;
  v_same := found
    and v_goal.target_kcal = (v_src->>'target_kcal')::int
    and v_goal.calorie_adjustment = (v_src->>'calorie_adjustment')::int
    and v_goal.strategy::text = v_src->>'strategy'
    and v_goal.protein_pct = (v_src->>'protein_pct')::smallint
    and v_goal.carbs_pct = (v_src->>'carbs_pct')::smallint
    and v_goal.fat_pct = (v_src->>'fat_pct')::smallint;
  if v_same then
    return jsonb_build_object('applied', false, 'week', v_week, 'target_kcal', (v_src->>'target_kcal')::int);
  end if;

  perform set_config('app.coach_write', 'on', true);
  insert into public.nutrition_goals (user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct)
  values (v_uid, (v_src->>'strategy')::public.goal_strategy, (v_src->>'calorie_adjustment')::int,
          (v_src->>'target_kcal')::int, (v_src->>'protein_pct')::smallint, (v_src->>'carbs_pct')::smallint,
          (v_src->>'fat_pct')::smallint)
  on conflict (user_id) do update
    set strategy = excluded.strategy, calorie_adjustment = excluded.calorie_adjustment,
        target_kcal = excluded.target_kcal, protein_pct = excluded.protein_pct,
        carbs_pct = excluded.carbs_pct, fat_pct = excluded.fat_pct;
  perform set_config('app.coach_write', 'off', true);

  return jsonb_build_object('applied', true, 'week', v_week, 'target_kcal', (v_src->>'target_kcal')::int);
end;
$$;

revoke execute on function public.evaluate_coach_calorie_change(date) from public, anon;
revoke execute on function public.apply_coach_progression(uuid) from public, anon;
revoke execute on function public.apply_coach_diet_break(date) from public, anon;
grant execute on function public.evaluate_coach_calorie_change(date) to authenticated;
grant execute on function public.apply_coach_progression(uuid) to authenticated;
grant execute on function public.apply_coach_diet_break(date) to authenticated;
