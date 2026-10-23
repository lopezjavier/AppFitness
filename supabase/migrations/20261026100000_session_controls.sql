-- T-096 (US-38, ADR-54 amendment): session controls. Pause/resume (paused time excluded from the duration),
-- immediate set deletion with re-indexing, and the "previous values" mode (last time in this routine / overall).

alter table public.workout_sessions
  add column paused_total_s int not null default 0 check (paused_total_s >= 0);

-- Pauses an in-progress session. Idempotent: pausing an already paused session keeps the first pause instant.
create function public.pause_workout_session(p_session_id uuid, p_at timestamptz)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.workout_sessions
  set paused_at = p_at
  where id = p_session_id and status = 'in_progress' and paused_at is null;
  if not found and not exists (
    select 1 from public.workout_sessions where id = p_session_id and status = 'in_progress'
  ) then
    raise exception 'session not found or not in progress' using errcode = 'P0002';
  end if;
end;
$$;

-- Resumes a paused session, adding the whole seconds spent paused to paused_total_s (negative spans, caused by
-- clock skew, count as zero). Idempotent: resuming a session that is not paused is a no-op.
create function public.resume_workout_session(p_session_id uuid, p_at timestamptz)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.workout_sessions
  set paused_total_s = paused_total_s + greatest(0, floor(extract(epoch from (p_at - paused_at))))::int,
      paused_at = null
  where id = p_session_id and status = 'in_progress' and paused_at is not null;
  if not found and not exists (
    select 1 from public.workout_sessions where id = p_session_id and status = 'in_progress'
  ) then
    raise exception 'session not found or not in progress' using errcode = 'P0002';
  end if;
end;
$$;

-- Finishes an in-progress session: folds an open pause into paused_total_s, drops the pending sets and closes it.
create or replace function public.finish_workout_session(p_session_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.workout_sessions
  set status = 'completed',
      ended_at = now(),
      paused_total_s = paused_total_s
        + case when paused_at is null then 0
               else greatest(0, floor(extract(epoch from (now() - paused_at))))::int end,
      paused_at = null
  where id = p_session_id and status = 'in_progress';
  if not found then
    raise exception 'session not found or not in progress' using errcode = 'P0002';
  end if;

  delete from public.workout_sets where session_id = p_session_id and completed_at is null;
end;
$$;

-- Deletes one set of an in-progress session and shifts the later sets of that exercise down by one, atomically.
-- Idempotent when the row does not exist (the later ones are still shifted). The upsert key is a non-deferrable
-- unique, so the shift goes through an offset range first.
create function public.delete_workout_set(
  p_session_id uuid, p_exercise_position smallint, p_set_index smallint
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if not exists (
    select 1 from public.workout_sessions where id = p_session_id and status = 'in_progress'
  ) then
    raise exception 'session not found or not in progress' using errcode = 'P0002';
  end if;

  delete from public.workout_sets
  where session_id = p_session_id and exercise_position = p_exercise_position and set_index = p_set_index;

  update public.workout_sets
  set set_index = set_index + 1000
  where session_id = p_session_id and exercise_position = p_exercise_position and set_index > p_set_index;

  update public.workout_sets
  set set_index = set_index - 1001
  where session_id = p_session_id and exercise_position = p_exercise_position and set_index > 1000;
end;
$$;

revoke execute on function public.pause_workout_session(uuid, timestamptz) from public, anon;
revoke execute on function public.resume_workout_session(uuid, timestamptz) from public, anon;
revoke execute on function public.delete_workout_set(uuid, smallint, smallint) from public, anon;
grant execute on function public.pause_workout_session(uuid, timestamptz) to authenticated;
grant execute on function public.resume_workout_session(uuid, timestamptz) to authenticated;
grant execute on function public.delete_workout_set(uuid, smallint, smallint) to authenticated;

-- Previous-values mode (training preference).
create type public.previous_values_mode as enum ('overall', 'routine');

alter table public.training_preferences
  add column previous_values_mode public.previous_values_mode not null default 'overall';

-- last_performance gains an optional routine filter (null = any session). The signature changes, so drop+create.
drop function public.last_performance(uuid);

create function public.last_performance(p_exercise_id uuid, p_routine_id uuid default null)
returns table (
  set_index int, weight_kg numeric, reps int, rpe numeric,
  duration_s int, distance_m numeric, steps int, floors int
)
language sql
stable
security invoker
set search_path = ''
as $$
  with last_session as (
    select s.id
    from public.workout_sessions s
    where s.status = 'completed'
      and (p_routine_id is null or s.routine_id = p_routine_id)
      and exists (select 1 from public.workout_sets x
                  where x.session_id = s.id
                    and x.exercise_id = p_exercise_id
                    and x.completed_at is not null)
    order by s.ended_at desc, s.started_at desc
    limit 1
  ),
  first_position as (
    select min(ws.exercise_position) as pos
    from public.workout_sets ws
    where ws.session_id = (select id from last_session)
      and ws.exercise_id = p_exercise_id
      and ws.completed_at is not null
  )
  select ws.set_index::int, ws.weight_kg, ws.reps::int, ws.rpe,
         ws.duration_s::int, ws.distance_m, ws.steps::int, ws.floors::int
  from public.workout_sets ws
  where ws.session_id = (select id from last_session)
    and ws.exercise_id = p_exercise_id
    and ws.exercise_position = (select pos from first_position)
    and ws.completed_at is not null
  order by ws.set_index;
$$;

revoke execute on function public.last_performance(uuid, uuid) from public, anon;
grant execute on function public.last_performance(uuid, uuid) to authenticated;

-- save_preferences gains p_previous_values_mode (null = leave unchanged).
drop function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean
);

create function public.save_preferences(
  p_display_name         text,
  p_sex                  public.sex,
  p_birth_date           date,
  p_height_cm            numeric,
  p_activity_level       public.activity_level,
  p_strategy             public.goal_strategy,
  p_calorie_adjustment   int,
  p_target_kcal          int,
  p_protein_pct          smallint,
  p_carbs_pct            smallint,
  p_fat_pct              smallint,
  p_weight_kg            numeric default null,
  p_measured_at          timestamptz default null,
  p_day_start            timestamptz default null,
  p_day_end              timestamptz default null,
  p_warm_up_included     boolean default null,
  p_previous_values_mode public.previous_values_mode default null
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_weight_id uuid;
begin
  -- Weight arguments are all-or-none; validate before writing anything.
  if p_weight_kg is not null then
    if p_measured_at is null or p_day_start is null or p_day_end is null or p_day_end <= p_day_start then
      raise exception 'weight requires measured_at and a valid day range' using errcode = '22023';
    end if;
  end if;

  update public.profiles
  set display_name = btrim(p_display_name, E' \t\n\r\f'),
      sex = p_sex,
      birth_date = p_birth_date,
      height_cm = p_height_cm,
      activity_level = p_activity_level
  where id = (select auth.uid());

  if not found then
    raise exception 'profile not found' using errcode = 'P0002';
  end if;

  insert into public.nutrition_goals (
    user_id, strategy, calorie_adjustment, target_kcal, protein_pct, carbs_pct, fat_pct
  )
  values (
    (select auth.uid()), p_strategy, p_calorie_adjustment, p_target_kcal,
    p_protein_pct, p_carbs_pct, p_fat_pct
  )
  on conflict (user_id) do update
  set strategy = excluded.strategy,
      calorie_adjustment = excluded.calorie_adjustment,
      target_kcal = excluded.target_kcal,
      protein_pct = excluded.protein_pct,
      carbs_pct = excluded.carbs_pct,
      fat_pct = excluded.fat_pct;

  if p_warm_up_included is not null or p_previous_values_mode is not null then
    insert into public.training_preferences (user_id, warm_up_included, previous_values_mode)
    values ((select auth.uid()), coalesce(p_warm_up_included, false), coalesce(p_previous_values_mode, 'overall'))
    on conflict (user_id) do update
    set warm_up_included = coalesce(p_warm_up_included, public.training_preferences.warm_up_included),
        previous_values_mode = coalesce(p_previous_values_mode, public.training_preferences.previous_values_mode);
  end if;

  if p_weight_kg is not null then
    select m.id into v_weight_id
    from public.body_measurements m
    where m.user_id = (select auth.uid())
      and m.source = 'manual'
      and m.measured_at >= p_day_start
      and m.measured_at < p_day_end
    order by m.measured_at desc
    limit 1;

    if v_weight_id is not null then
      update public.body_measurements
      set weight_kg = p_weight_kg, measured_at = p_measured_at
      where id = v_weight_id;
    else
      insert into public.body_measurements (user_id, measured_at, weight_kg, source)
      values ((select auth.uid()), p_measured_at, p_weight_kg, 'manual');
    end if;
  end if;
end;
$$;

revoke execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode
) from public, anon;
grant execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean,
  public.previous_values_mode
) to authenticated;
