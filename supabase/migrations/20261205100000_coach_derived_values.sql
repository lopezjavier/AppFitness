-- T-157 (E13, US-68/US-69): derived values of the coach, computed in SQL and confirmed by the user.
-- Rules: planning/04-business-rules.md section 7, planning/07-coach-design.md sections 2 and 9.
-- Reads profiles, body_measurements, daily_activity, food_log_entries, workout_sessions; writes only coach_confirmed_values.

create type public.coach_value_key as enum ('activity_level', 'total_expenditure', 'weight_trend', 'experience');

-- ---------------------------------------------------------------------------
-- coach_confirmed_values: the value the user confirmed, with the uncertainty it was confirmed with.
-- Survives "Eliminar historial del entrenador" (E13 Q13); removed with the account (cascade).
-- ---------------------------------------------------------------------------
create table public.coach_confirmed_values (
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  key          public.coach_value_key not null,
  -- Server estimate at confirmation: activity factor / kcal / kg per week / experience ordinal 0-2.
  estimate     numeric not null,
  -- Half-width in the same unit as the estimate.
  uncertainty  numeric not null constraint coach_confirmed_values_uncertainty_range check (uncertainty >= 0),
  -- Confirmed (possibly edited) activity level; only for key = activity_level.
  level_value  public.activity_level,
  edited       boolean not null default false,
  confirmed_at timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  primary key (user_id, key),
  constraint coach_confirmed_values_level_only_for_activity check (level_value is null or key = 'activity_level')
);

create trigger coach_confirmed_values_set_updated_at
  before update on public.coach_confirmed_values
  for each row execute function public.set_updated_at();

alter table public.coach_confirmed_values enable row level security;
alter table public.coach_confirmed_values force row level security;

create policy coach_confirmed_values_select_own on public.coach_confirmed_values
  for select to authenticated
  using (user_id = (select auth.uid()));

-- The RPCs are security invoker, so the owner needs insert/update. No delete: rows go with the account.
create policy coach_confirmed_values_insert_own on public.coach_confirmed_values
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy coach_confirmed_values_update_own on public.coach_confirmed_values
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.coach_confirmed_values from anon, authenticated;
grant select, insert, update on table public.coach_confirmed_values to authenticated;

-- ---------------------------------------------------------------------------
-- Helpers (security invoker, never granted to anon)
-- ---------------------------------------------------------------------------

-- Authenticated user id; p_today must be within one day of UTC today (same rule as apply_meal_defaults).
create function public.coach_guard(p_today date)
returns uuid
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_utc_date date := (now() at time zone 'utc')::date;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_today is null or p_today < v_utc_date - 1 or p_today > v_utc_date + 1 then
    raise exception 'today must be the current date' using errcode = '22023';
  end if;
  return v_uid;
end;
$$;

-- Activity factor of a level (04 section 1.3).
create function public.coach_activity_factor(p_level public.activity_level)
returns numeric
language sql
immutable
security invoker
set search_path = ''
as $$
  select (array[1.2, 1.375, 1.55, 1.725, 1.9]::numeric[])[array_position(enum_range(null::public.activity_level), p_level)];
$$;

-- Estimated 1RM (Epley, 04 section 4.1): reps 1..12 and weight > 0 count; rounded to 0.5 kg.
-- Same expression as exercise_history (coach-private copy; see T-157).
create function public.coach_e1rm(p_weight_kg numeric, p_reps integer)
returns numeric
language sql
immutable
security invoker
set search_path = ''
as $$
  select case
           when p_reps between 1 and 12 and p_weight_kg > 0 then
             round(case when p_reps = 1 then p_weight_kg else p_weight_kg * (1 + p_reps / 30.0) end * 2) / 2
         end;
$$;

-- Mifflin-St Jeor with the latest weight (04 section 1.2); null when sex, birth date, height or weight is missing.
create function public.coach_basal_kcal(p_today date)
returns numeric
language sql
stable
security invoker
set search_path = ''
as $$
  select case when p.sex is null or p.birth_date is null or p.height_cm is null or w.weight_kg is null then null
         else round(10 * w.weight_kg + 6.25 * p.height_cm
                    - 5 * date_part('year', age(p_today, p.birth_date))::numeric
                    + case p.sex when 'male' then 5 else -161 end, 1)
         end
  from public.profiles p
  left join lateral (
    select b.weight_kg from public.body_measurements b
    where b.user_id = p.id order by b.measured_at desc limit 1
  ) w on true
  where p.id = (select auth.uid());
$$;

-- Activity level estimate from the last 28 days ending p_today (04 section 7.1).
create function public.coach_activity_estimate(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid       uuid := (select auth.uid());
  v_from      date := p_today - 27;
  v_levels    public.activity_level[] := enum_range(null::public.activity_level);
  v_steps     numeric;
  v_steps_n   integer;
  v_kcal      numeric;
  v_kcal_n    integer;
  v_sessions  integer;
  v_per_week  numeric;
  v_profile   public.activity_level;
  v_ord       integer;
  v_source    text;
  v_missing   text[] := array[]::text[];
begin
  select avg(steps), count(steps) into v_steps, v_steps_n
  from public.daily_activity where user_id = v_uid and day between v_from and p_today and steps is not null;
  select avg(active_kcal), count(active_kcal) into v_kcal, v_kcal_n
  from public.daily_activity where user_id = v_uid and day between v_from and p_today and active_kcal is not null;
  select count(*) into v_sessions
  from public.workout_sessions
  where user_id = v_uid and status = 'completed'
    and (started_at at time zone 'UTC')::date between v_from and p_today;
  v_per_week := v_sessions / 4.0;
  select activity_level into v_profile from public.profiles where id = v_uid;

  if v_steps_n < 7 then v_missing := v_missing || 'steps'::text; end if;
  if v_kcal_n < 7 then v_missing := v_missing || 'active_kcal'::text; end if;
  if v_sessions = 0 then v_missing := v_missing || 'training_history'::text; end if;

  if v_steps_n >= 7 then
    v_ord := case when v_steps < 5000 then 0 when v_steps < 7500 then 1 when v_steps < 10000 then 2
                  when v_steps < 12500 then 3 else 4 end;
    v_source := 'data';
  elsif v_kcal_n >= 7 then
    v_ord := case when v_kcal < 150 then 0 when v_kcal < 300 then 1 when v_kcal < 500 then 2
                  when v_kcal < 750 then 3 else 4 end;
    v_source := 'data';
  else
    v_ord := array_position(v_levels, coalesce(v_profile, 'sedentary')) - 1;
    v_source := 'profile';
  end if;

  -- Sessions raise a measured level only; the profile level already includes training.
  if v_source = 'data' and v_per_week >= 3 then
    v_ord := least(v_ord + 1, 4);
  end if;

  return jsonb_build_object(
    'level', v_levels[v_ord + 1],
    'source', v_source,
    'steps_avg', case when v_steps_n >= 7 then round(v_steps, 0) end,
    'steps_days', v_steps_n,
    'active_kcal_avg', case when v_kcal_n > 0 then round(v_kcal, 0) end,
    'active_kcal_days', v_kcal_n,
    'sessions_per_week', round(v_per_week, 2),
    'missing', to_jsonb(v_missing));
end;
$$;

-- Weight trend: OLS slope of weekly averages over the last 8 ISO weeks with a 95 % Student-t interval (04 section 7.4).
create function public.coach_weight_trend(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid   uuid := (select auth.uid());
  v_start date := (p_today - (extract(isodow from p_today)::int - 1)) - 49;
  v_t     numeric[] := array[12.706, 4.303, 3.182, 2.776, 2.571, 2.447];
  v_n     integer;
  v_slope double precision;
  v_sxx   double precision;
  v_syy   double precision;
  v_sxy   double precision;
  v_half  numeric;
  v_low   numeric;
  v_high  numeric;
  v_se    double precision;
begin
  with daily as (
    select (measured_at at time zone 'UTC')::date as d, avg(weight_kg) as w
    from public.body_measurements
    where user_id = v_uid and (measured_at at time zone 'UTC')::date between v_start and p_today
    group by 1
  ), weekly as (
    select (d - (extract(isodow from d)::int - 1) - v_start) / 7 as x, avg(w)::double precision as y
    from daily group by 1
  )
  select count(*), regr_slope(y, x), regr_sxx(y, x), regr_syy(y, x), regr_sxy(y, x)
  into v_n, v_slope, v_sxx, v_syy, v_sxy
  from weekly;

  if v_n < 2 then
    return jsonb_build_object('slope', null, 'low', null, 'high', null, 'weeks', v_n,
                              'low_confidence', true, 'missing', jsonb_build_array('weigh_ins'));
  end if;
  if v_n = 2 then
    return jsonb_build_object('slope', round(v_slope::numeric, 3), 'low', null, 'high', null, 'weeks', v_n,
                              'low_confidence', true, 'missing', '[]'::jsonb);
  end if;

  v_se := sqrt(greatest(0, v_syy - v_sxy * v_sxy / v_sxx) / (v_n - 2)) / sqrt(v_sxx);
  v_half := v_t[v_n - 2] * v_se::numeric;
  v_low := round(v_slope::numeric - v_half, 3);
  v_high := round(v_slope::numeric + v_half, 3);
  return jsonb_build_object('slope', round(v_slope::numeric, 3), 'low', v_low, 'high', v_high, 'weeks', v_n,
                            'low_confidence', v_n < 4, 'missing', '[]'::jsonb);
end;
$$;

-- Intake completeness over the 28 days ending p_today (04 section 7.5): reliable from 70 % logged days.
create function public.coach_intake(p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with d as (
    select log_date, sum(kcal) as kcal
    from public.food_log_entries
    where user_id = (select auth.uid()) and log_date between p_today - 27 and p_today
    group by log_date
  )
  select jsonb_build_object(
    'logged_days', count(*),
    'window_days', 28,
    'ratio', round(count(*) / 28.0, 3),
    'reliable', count(*) * 10 >= 28 * 7,
    'mean_kcal', case when count(*) > 0 then round(avg(kcal), 0) end)
  from d;
$$;

-- Experience from completed sessions and weeks of history (04 section 7.6). Ordinal 0 beginner, 1 intermediate, 2 advanced.
create function public.coach_experience(p_today date)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  with s as (
    select count(*)::int as sessions,
           coalesce((p_today - min((started_at at time zone 'UTC')::date)) / 7, 0)::int as weeks
    from public.workout_sessions
    where user_id = (select auth.uid()) and status = 'completed'
      and (started_at at time zone 'UTC')::date <= p_today
  )
  select jsonb_build_object(
    'ordinal', case when sessions >= 150 and weeks >= 52 then 2
                    when sessions >= 24 and weeks >= 12 then 1 else 0 end,
    'sessions', sessions,
    'weeks', weeks)
  from s;
$$;

-- All derived values with the confirmations applied. p_level_override is only used by confirm_derived_values
-- (the level being confirmed, so the expenditure estimate stored with it is consistent).
create function public.coach_compute(p_today date, p_level_override public.activity_level default null)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid        uuid := (select auth.uid());
  v_act        jsonb := public.coach_activity_estimate(p_today);
  v_trend      jsonb := public.coach_weight_trend(p_today);
  v_intake     jsonb := public.coach_intake(p_today);
  v_exp        jsonb := public.coach_experience(p_today);
  v_basal      numeric := public.coach_basal_kcal(p_today);
  r            public.coach_confirmed_values;
  v_est_level  public.activity_level := (v_act->>'level')::public.activity_level;
  v_est_factor numeric := public.coach_activity_factor(v_est_level);
  v_act_unc    numeric := case when v_act->>'source' = 'data' then 0.1 else 0.2 end;
  v_act_reask  boolean := false;
  v_act_conf   boolean := false;
  v_act_edited boolean := false;
  v_eff_level  public.activity_level;
  v_eff_factor numeric;
  v_reliable   boolean := (v_intake->>'reliable')::boolean;
  v_weeks      integer := (v_trend->>'weeks')::int;
  v_slope      numeric := (v_trend->>'slope')::numeric;
  v_adaptive   boolean;
  v_tdee       numeric;
  v_tdee_unc   numeric;
  v_tdee_miss  text[] := array[]::text[];
  v_tdee_reask boolean := false;
  v_tdee_conf  boolean := false;
  v_tr_unc     numeric;
  v_tr_reask   boolean := false;
  v_tr_conf    boolean := false;
  v_tr_miss    jsonb := v_trend->'missing';
  v_ex_ord     integer := (v_exp->>'ordinal')::int;
  v_ex_reask   boolean := false;
  v_ex_conf    boolean := false;
begin
  -- Activity level.
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'activity_level';
  if found then
    v_act_reask := abs(v_est_factor - r.estimate) > r.uncertainty;
    v_act_conf := not v_act_reask;
    v_act_edited := v_act_conf and r.edited;
  end if;
  v_eff_level := coalesce(p_level_override,
                          case when v_act_conf then coalesce(r.level_value, v_est_level) else v_est_level end);
  v_eff_factor := public.coach_activity_factor(v_eff_level);

  -- Total expenditure.
  v_adaptive := v_weeks >= 3 and v_reliable and v_slope is not null and v_intake->>'mean_kcal' is not null;
  if v_adaptive then
    v_tdee := round((v_intake->>'mean_kcal')::numeric - v_slope * 7700 / 7);
  elsif v_basal is not null then
    v_tdee := round(v_basal * v_eff_factor);
  else
    v_tdee_miss := v_tdee_miss || 'profile_data'::text;
  end if;
  if not v_reliable then v_tdee_miss := v_tdee_miss || 'diary'::text; end if;
  v_tdee_unc := case when v_tdee is not null then round(v_tdee * 0.1, 1) end;
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'total_expenditure';
  if found and v_tdee is not null then
    v_tdee_reask := abs(v_tdee - r.estimate) > r.uncertainty;
    v_tdee_conf := not v_tdee_reask;
  end if;

  -- Weight trend.
  v_tr_unc := coalesce(((v_trend->>'high')::numeric - v_slope), 0.5);
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'weight_trend';
  if found and v_slope is not null then
    v_tr_reask := abs(v_slope - r.estimate) > r.uncertainty;
    v_tr_conf := not v_tr_reask;
  end if;

  -- Experience.
  select * into r from public.coach_confirmed_values where user_id = v_uid and key = 'experience';
  if found then
    v_ex_reask := abs(v_ex_ord - r.estimate) > r.uncertainty;
    v_ex_conf := not v_ex_reask;
  end if;

  return jsonb_build_object(
    'as_of', p_today,
    'activity_level', jsonb_build_object(
      'value', v_eff_level,
      'estimate', v_est_level,
      'factor', v_eff_factor,
      'source', v_act->>'source',
      'provisional', not v_act_conf,
      'confirmed', v_act_conf,
      'edited', v_act_edited,
      'reask', v_act_reask,
      'uncertainty', v_act_unc,
      'sources', jsonb_build_object(
        'steps_avg', v_act->'steps_avg',
        'steps_days', v_act->'steps_days',
        'active_kcal_avg', v_act->'active_kcal_avg',
        'active_kcal_days', v_act->'active_kcal_days',
        'sessions_per_week', v_act->'sessions_per_week'),
      'missing', v_act->'missing'),
    'basal_kcal', jsonb_build_object(
      'value', v_basal,
      'missing', case when v_basal is null then jsonb_build_array('profile_data') else '[]'::jsonb end),
    'total_expenditure', jsonb_build_object(
      'value', v_tdee,
      'source', case when v_adaptive then 'adaptive' else 'formula' end,
      'unreliable', not v_reliable,
      'allow_calorie_changes', v_reliable,
      'provisional', not v_tdee_conf,
      'confirmed', v_tdee_conf,
      'reask', v_tdee_reask,
      'uncertainty', v_tdee_unc,
      'missing', to_jsonb(v_tdee_miss)),
    'weight_trend', jsonb_build_object(
      'slope_kg_week', v_trend->'slope',
      'interval_low', v_trend->'low',
      'interval_high', v_trend->'high',
      'weeks', v_weeks,
      'low_confidence', (v_trend->>'low_confidence')::boolean,
      'provisional', not v_tr_conf,
      'confirmed', v_tr_conf,
      'reask', v_tr_reask,
      'uncertainty', v_tr_unc,
      'missing', v_tr_miss),
    'intake', jsonb_build_object(
      'logged_days', v_intake->'logged_days',
      'window_days', v_intake->'window_days',
      'ratio', v_intake->'ratio',
      'reliable', v_reliable),
    'experience', jsonb_build_object(
      'value', (array['beginner', 'intermediate', 'advanced'])[v_ex_ord + 1],
      'sessions', v_exp->'sessions',
      'weeks', v_exp->'weeks',
      'provisional', not v_ex_conf,
      'confirmed', v_ex_conf,
      'reask', v_ex_reask,
      'uncertainty', 0.5,
      'missing', case when (v_exp->>'sessions')::int = 0 then jsonb_build_array('training_history') else '[]'::jsonb end));
end;
$$;

-- ---------------------------------------------------------------------------
-- Public RPCs
-- ---------------------------------------------------------------------------
create function public.coach_derived_estimates(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  perform public.coach_guard(p_today);
  return public.coach_compute(p_today, null);
end;
$$;

-- Stores the confirmation of every value that has an estimate (recomputed here, client numbers are never trusted).
-- p_activity_level, when given, is the user's edited level. Returns the same object as coach_derived_estimates.
create function public.confirm_derived_values(p_today date, p_activity_level public.activity_level default null)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := public.coach_guard(p_today);
  v_raw jsonb := public.coach_compute(p_today, p_activity_level);
  v_est public.activity_level := (v_raw->'activity_level'->>'estimate')::public.activity_level;
begin
  insert into public.coach_confirmed_values as c (user_id, key, estimate, uncertainty, level_value, edited, confirmed_at)
  values (v_uid, 'activity_level', public.coach_activity_factor(v_est),
          (v_raw->'activity_level'->>'uncertainty')::numeric,
          coalesce(p_activity_level, v_est), p_activity_level is not null, now())
  on conflict (user_id, key) do update
    set estimate = excluded.estimate, uncertainty = excluded.uncertainty, level_value = excluded.level_value,
        edited = excluded.edited, confirmed_at = excluded.confirmed_at;

  if v_raw->'total_expenditure'->>'value' is not null then
    insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty)
    values (v_uid, 'total_expenditure', (v_raw->'total_expenditure'->>'value')::numeric,
            (v_raw->'total_expenditure'->>'uncertainty')::numeric)
    on conflict (user_id, key) do update
      set estimate = excluded.estimate, uncertainty = excluded.uncertainty, confirmed_at = now();
  end if;

  if v_raw->'weight_trend'->>'slope_kg_week' is not null then
    insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty)
    values (v_uid, 'weight_trend', (v_raw->'weight_trend'->>'slope_kg_week')::numeric,
            (v_raw->'weight_trend'->>'uncertainty')::numeric)
    on conflict (user_id, key) do update
      set estimate = excluded.estimate, uncertainty = excluded.uncertainty, confirmed_at = now();
  end if;

  insert into public.coach_confirmed_values (user_id, key, estimate, uncertainty)
  values (v_uid, 'experience',
          array_position(array['beginner', 'intermediate', 'advanced'], v_raw->'experience'->>'value') - 1,
          0.5)
  on conflict (user_id, key) do update
    set estimate = excluded.estimate, uncertainty = excluded.uncertainty, confirmed_at = now();

  return public.coach_compute(p_today, null);
end;
$$;

revoke execute on function public.coach_guard(date) from public, anon;
revoke execute on function public.coach_activity_factor(public.activity_level) from public, anon;
revoke execute on function public.coach_e1rm(numeric, integer) from public, anon;
revoke execute on function public.coach_basal_kcal(date) from public, anon;
revoke execute on function public.coach_activity_estimate(date) from public, anon;
revoke execute on function public.coach_weight_trend(date) from public, anon;
revoke execute on function public.coach_intake(date) from public, anon;
revoke execute on function public.coach_experience(date) from public, anon;
revoke execute on function public.coach_compute(date, public.activity_level) from public, anon;
revoke execute on function public.coach_derived_estimates(date) from public, anon;
revoke execute on function public.confirm_derived_values(date, public.activity_level) from public, anon;
grant execute on function public.coach_guard(date) to authenticated;
grant execute on function public.coach_activity_factor(public.activity_level) to authenticated;
grant execute on function public.coach_e1rm(numeric, integer) to authenticated;
grant execute on function public.coach_basal_kcal(date) to authenticated;
grant execute on function public.coach_activity_estimate(date) to authenticated;
grant execute on function public.coach_weight_trend(date) to authenticated;
grant execute on function public.coach_intake(date) to authenticated;
grant execute on function public.coach_experience(date) to authenticated;
grant execute on function public.coach_compute(date, public.activity_level) to authenticated;
grant execute on function public.coach_derived_estimates(date) to authenticated;
grant execute on function public.confirm_derived_values(date, public.activity_level) to authenticated;
