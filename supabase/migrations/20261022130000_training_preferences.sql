-- T-090 (US-29, ADR-54 amendment): per-user training preferences. First setting: "Incluir calentamiento".
-- Aggregates are computed on read, so toggling the flag recalculates the whole history with no rewrite.
-- Later E6 tasks add columns to this table; warm_up_included() stays the single SQL entry point for the flag.

create table public.training_preferences (
  user_id          uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  warm_up_included boolean not null default false,
  updated_at       timestamptz not null default now()
);

create trigger training_preferences_set_updated_at
  before update on public.training_preferences
  for each row execute function public.set_updated_at();

alter table public.training_preferences enable row level security;
alter table public.training_preferences force row level security;

create policy training_preferences_select_own on public.training_preferences
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy training_preferences_insert_own on public.training_preferences
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy training_preferences_update_own on public.training_preferences
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy training_preferences_delete_own on public.training_preferences
  for delete to authenticated
  using (user_id = (select auth.uid()));

-- Supabase default privileges grant ALL (incl. TRUNCATE, not covered by RLS) to anon/authenticated.
revoke all on table public.training_preferences from anon, authenticated;
grant select, insert, update, delete on table public.training_preferences to authenticated;

-- Whether the caller counts warm-up sets in volume, 1RM, PRs and muscle volume (default: no).
create function public.warm_up_included()
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce(
    (select tp.warm_up_included from public.training_preferences tp where tp.user_id = (select auth.uid())),
    false);
$$;

revoke execute on function public.warm_up_included() from public, anon;
grant execute on function public.warm_up_included() to authenticated;

-- Warm-up sets now follow the preference instead of being always ignored.
create or replace function public.exercise_history(p_exercise_id uuid, p_from date default null)
returns table (session_id uuid, session_date date, best_one_rm numeric, total_volume numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select s.id,
         (s.started_at at time zone 'UTC')::date,
         max(case
               when ws.reps between 1 and 12 and ws.weight_kg > 0 then
                 round(case when ws.reps = 1 then ws.weight_kg
                            else ws.weight_kg * (1 + ws.reps / 30.0) end * 2) / 2
             end),
         coalesce(sum(ws.weight_kg * ws.reps), 0)
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  where s.status = 'completed'
    and ws.exercise_id = p_exercise_id
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
    and (p_from is null or (s.started_at at time zone 'UTC')::date >= p_from)
  group by s.id, s.started_at
  order by s.started_at;
$$;

revoke execute on function public.exercise_history(uuid, date) from public, anon;
grant execute on function public.exercise_history(uuid, date) to authenticated;

create or replace function public.weekly_muscle_volume(p_from date)
returns table (week_start date, muscle_group public.muscle_group, total_volume numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select date_trunc('week', s.started_at at time zone 'UTC')::date as week_start,
         e.muscle_group,
         sum(ws.weight_kg * ws.reps) as total_volume
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  where s.status = 'completed'
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
    and (s.started_at at time zone 'UTC')::date >= p_from
  group by 1, e.muscle_group
  order by 1, 2;
$$;

revoke execute on function public.weekly_muscle_volume(date) from public, anon;
grant execute on function public.weekly_muscle_volume(date) to authenticated;

-- save_preferences gains p_warm_up_included (null = leave unchanged) so "Guardar" stays atomic.
drop function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz
);

create function public.save_preferences(
  p_display_name       text,
  p_sex                public.sex,
  p_birth_date         date,
  p_height_cm          numeric,
  p_activity_level     public.activity_level,
  p_strategy           public.goal_strategy,
  p_calorie_adjustment int,
  p_target_kcal        int,
  p_protein_pct        smallint,
  p_carbs_pct          smallint,
  p_fat_pct            smallint,
  p_weight_kg          numeric default null,
  p_measured_at        timestamptz default null,
  p_day_start          timestamptz default null,
  p_day_end            timestamptz default null,
  p_warm_up_included   boolean default null
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

  if p_warm_up_included is not null then
    insert into public.training_preferences (user_id, warm_up_included)
    values ((select auth.uid()), p_warm_up_included)
    on conflict (user_id) do update
    set warm_up_included = excluded.warm_up_included;
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
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean
) from public, anon;
grant execute on function public.save_preferences(
  text, public.sex, date, numeric, public.activity_level, public.goal_strategy,
  int, int, smallint, smallint, smallint, numeric, timestamptz, timestamptz, timestamptz, boolean
) to authenticated;
