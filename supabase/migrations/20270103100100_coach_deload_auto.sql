-- T-230 (E14, US-100): deloads apply on their own, can be cancelled, and the user can ask for one.
-- Rules: planning/04-business-rules.md section 9.10, planning/07-coach-design.md section 3 "Deload" and (4).
-- A deload is a coach_changes row (kinds deload_triggered | deload_scheduled | deload_user) plus deload = true on that
-- week's coach_plan_sessions; deload_suggestions (unchanged) keeps reading the marks, so the live session needs no change.
-- All writes are security definer RPCs scoped to auth.uid() (coach_changes and coach_plan_sessions are select-only).

-- A deload is plan-level: no exercise.
alter table public.coach_changes alter column exercise_id drop not null;
alter table public.coach_changes drop constraint coach_changes_kind_values;
alter table public.coach_changes add constraint coach_changes_kind_values check (kind in
  ('load_raise', 'load_drop', 'load_kept', 'load_flag', 'no_range_flag',
   'deload_triggered', 'deload_scheduled', 'deload_user'));
alter table public.coach_changes add constraint coach_changes_exercise_unless_deload
  check (exercise_id is not null or kind in ('deload_triggered', 'deload_scheduled', 'deload_user'));

-- At most one active (not cancelled) deload per plan.
create unique index coach_changes_one_active_deload
  on public.coach_changes (user_id, plan_id)
  where kind in ('deload_triggered', 'deload_scheduled', 'deload_user') and status = 'applied';

-- The accept-first path is gone: close what was waiting for an answer (applied deloads and their marks stay).
update public.coach_recommendations
  set status = 'discarded', decided_at = now()
  where kind = 'deload' and status = 'pending';

drop function public.decide_coach_deload(uuid, boolean);

-- ---------------------------------------------------------------------------
-- evaluate_coach_deload: in plan week 4 of the accepted block, applies the deload on its own (once per block).
-- Triggered: no main lift done in each of weeks 1-3 improved (max(w2, w3) > w1). Scheduled: otherwise, when at least
-- 7 training weeks (plan weeks with a completed workout, earlier blocks included) passed since the last deload week.
-- Returns {status: applied|none, reason, change?, week?}. Idempotent: a second call finds the first one's row.
-- ---------------------------------------------------------------------------
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
  v_c := (p_today - v_start) / 7 + 1;
  if v_c < 4 then
    return jsonb_build_object('status', 'none', 'reason', 'too_early');
  end if;
  if v_c > 4 then
    return jsonb_build_object('status', 'none', 'reason', 'past_week_4');
  end if;
  v_wstart := v_start + 21;

  -- One deload per block: any row of this plan (cancelled too) or a deload mark already there.
  if exists (select 1 from public.coach_changes c
             where c.user_id = v_uid and c.plan_id = v_plan.id
               and c.kind in ('deload_triggered', 'deload_scheduled', 'deload_user'))
     or exists (select 1 from public.coach_plan_sessions s
                where s.plan_id = v_plan.id and s.user_id = v_uid and s.deload) then
    return jsonb_build_object('status', 'none', 'reason', 'already_applied');
  end if;

  -- Weekly best e1RM of each main lift in plan weeks 1-3; a lift needs a value in every one of the three weeks.
  with best as (
    select st.exercise_id,
           ((s.started_at at time zone 'utc')::date - v_start) / 7 + 1 as wk,
           max(public.coach_e1rm(st.weight_kg, st.reps)) as e1rm
    from public.workout_sessions s
    join public.workout_sets st on st.session_id = s.id
    join public.exercises e on e.id = st.exercise_id
    where s.user_id = v_uid and s.status = 'completed'
      and st.completed_at is not null
      and (st.set_type <> 'warm_up' or public.warm_up_included())
      and e.movement_pattern::text in ('squat', 'hinge', 'lunge', 'horizontal_push', 'vertical_push',
                                       'horizontal_pull', 'vertical_pull')
      and (s.started_at at time zone 'utc')::date >= v_start
      and (s.started_at at time zone 'utc')::date <= v_start + 20
    group by st.exercise_id, wk
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
    from (select p.id, s.week, coalesce(p.training_start, p.block_start) + 7 * (s.week - 1) as wstart, bool_or(s.deload) as dl
          from public.coach_plan_sessions s
          join public.coach_plans p on p.id = s.plan_id and p.user_id = v_uid
          where s.user_id = v_uid
          group by p.id, s.week, coalesce(p.training_start, p.block_start)) x
    where x.dl and x.wstart < v_wstart;

    select count(distinct x.wstart)::int into v_trained
    from (select coalesce(p.training_start, p.block_start) + 7 * (s.week - 1) as wstart
          from public.coach_plan_sessions s
          join public.coach_plans p on p.id = s.plan_id and p.user_id = v_uid
          where s.user_id = v_uid) x
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

-- ---------------------------------------------------------------------------
-- cancel_coach_deload: the week goes back to the normal sets. A second cancel (or any cancel of something that is not an
-- applied deload) is refused with P0001 coach_change_not_undoable.
-- ---------------------------------------------------------------------------
create function public.cancel_coach_deload(p_change_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_c   public.coach_changes;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  select * into v_c from public.coach_changes c where c.id = p_change_id and c.user_id = v_uid for update;
  if not found then
    raise exception 'coach change not found' using errcode = 'P0002';
  end if;
  if v_c.kind not in ('deload_triggered', 'deload_scheduled', 'deload_user') or v_c.status <> 'applied' then
    raise exception 'coach_change_not_undoable' using errcode = 'P0001';
  end if;

  if v_c.plan_id is not null then
    update public.coach_plan_sessions set deload = false
      where plan_id = v_c.plan_id and user_id = v_uid and week = (v_c.figures ->> 'week')::int;
  end if;
  update public.coach_changes set status = 'undone', decided_at = now()
    where id = v_c.id
    returning * into v_c;
  return public.coach_change_json(v_c);
end;
$$;

-- ---------------------------------------------------------------------------
-- request_coach_deload: the next plan week becomes a deload week. Refused with P0001 coach_deload_not_available when a
-- deload is planned or ran in the block, coach_deload_no_block without an accepted block, coach_deload_no_next_week when
-- the block has no next week.
-- ---------------------------------------------------------------------------
create function public.request_coach_deload()
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
  v_start  date;
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

  v_start := coalesce(v_plan.training_start, v_plan.block_start);
  v_next := case when v_today < v_start then 1 else (v_today - v_start) / 7 + 2 end;
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

-- remove_coach_block (20261225100000) plus: the block's deload rows stay as history but detach from the plan, so a block
-- created later on the same plan is not blocked by a deload of the removed one.
create or replace function public.remove_coach_block(p_plan_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_plan public.coach_plans;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_plan_id is null then
    raise exception 'plan not found' using errcode = '22023';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_plan_id::text, 0));

  select * into v_plan from public.coach_plans where id = p_plan_id and user_id = v_uid for update;
  if not found then
    raise exception 'plan not found' using errcode = '22023';
  end if;
  if v_plan.status not in ('proposed', 'accepted') then
    raise exception 'plan is not open' using errcode = '55000';
  end if;

  delete from public.coach_plan_sessions where plan_id = p_plan_id and user_id = v_uid;

  update public.coach_recommendations
    set status = 'discarded', decided_at = now()
    where plan_id = p_plan_id and user_id = v_uid and kind in ('deload', 'block') and status = 'pending';

  update public.coach_changes set plan_id = null
    where plan_id = p_plan_id and user_id = v_uid
      and kind in ('deload_triggered', 'deload_scheduled', 'deload_user');

  update public.coach_plans set folder_id = null, training = null,
      training_start = (now() at time zone 'utc')::date where id = p_plan_id and user_id = v_uid;
end;
$$;

revoke execute on function public.evaluate_coach_deload(date) from public, anon;
revoke execute on function public.cancel_coach_deload(uuid) from public, anon;
revoke execute on function public.request_coach_deload() from public, anon;
revoke execute on function public.remove_coach_block(uuid) from public, anon;
grant execute on function public.evaluate_coach_deload(date) to authenticated;
grant execute on function public.cancel_coach_deload(uuid) to authenticated;
grant execute on function public.request_coach_deload() to authenticated;
grant execute on function public.remove_coach_block(uuid) to authenticated;
