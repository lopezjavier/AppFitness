-- T-093 (US-32, ADR-54 amendment): per-exercise volume options. First option: "Contar mancuerna doble"
-- (volume = 2 x weight x reps for dumbbell exercises). Computed on read, so toggling recalculates the whole history.
-- 1RM is NOT doubled (it stays per-dumbbell weight).

create table public.exercise_options (
  user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
  exercise_id     uuid not null references public.exercises (id) on delete cascade,
  double_dumbbell boolean not null default false,
  updated_at      timestamptz not null default now(),
  primary key (user_id, exercise_id)
);

create trigger exercise_options_set_updated_at
  before update on public.exercise_options
  for each row execute function public.set_updated_at();

alter table public.exercise_options enable row level security;
alter table public.exercise_options force row level security;

create policy exercise_options_select_own on public.exercise_options
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy exercise_options_insert_own on public.exercise_options
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy exercise_options_update_own on public.exercise_options
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy exercise_options_delete_own on public.exercise_options
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.exercise_options from anon, authenticated;
grant select, insert, update, delete on table public.exercise_options to authenticated;

-- 2 when the caller counts this dumbbell exercise twice, else 1.
create function public.volume_factor(p_exercise_id uuid)
returns numeric
language sql
stable
security invoker
set search_path = ''
as $$
  select case
           when exists (
             select 1
             from public.exercise_options o
             join public.exercises e on e.id = o.exercise_id
             where o.user_id = (select auth.uid())
               and o.exercise_id = p_exercise_id
               and o.double_dumbbell
               and e.equipment = 'dumbbell')
           then 2 else 1
         end;
$$;

revoke execute on function public.volume_factor(uuid) from public, anon;
grant execute on function public.volume_factor(uuid) to authenticated;

create function public.set_exercise_double_dumbbell(p_exercise_id uuid, p_enabled boolean)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_equipment public.equipment;
begin
  select e.equipment into v_equipment from public.exercises e where e.id = p_exercise_id;
  if not found then
    raise exception 'exercise not found' using errcode = 'P0002';
  end if;
  if v_equipment <> 'dumbbell' then
    raise exception 'double dumbbell applies to dumbbell exercises only' using errcode = '22023';
  end if;

  insert into public.exercise_options (user_id, exercise_id, double_dumbbell)
  values ((select auth.uid()), p_exercise_id, p_enabled)
  on conflict (user_id, exercise_id) do update
  set double_dumbbell = excluded.double_dumbbell;
end;
$$;

revoke execute on function public.set_exercise_double_dumbbell(uuid, boolean) from public, anon;
grant execute on function public.set_exercise_double_dumbbell(uuid, boolean) to authenticated;

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
               when e.metric_type in ('weight_reps', 'weighted')
                    and ws.reps between 1 and 12 and ws.weight_kg > 0 then
                 round(case when ws.reps = 1 then ws.weight_kg
                            else ws.weight_kg * (1 + ws.reps / 30.0) end * 2) / 2
             end),
         coalesce(sum(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg) * public.volume_factor(e.id)), 0)
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  cross join (select public.latest_body_weight_kg() as kg) bw
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
         sum(public.set_volume(e.metric_type, ws.weight_kg, ws.reps, bw.kg) * public.volume_factor(e.id)) as total_volume
  from public.workout_sessions s
  join public.workout_sets ws on ws.session_id = s.id
  join public.exercises e on e.id = ws.exercise_id
  cross join (select public.latest_body_weight_kg() as kg) bw
  where s.status = 'completed'
    and ws.completed_at is not null
    and (ws.set_type <> 'warm_up' or public.warm_up_included())
    and (s.started_at at time zone 'UTC')::date >= p_from
  group by 1, e.muscle_group
  order by 1, 2;
$$;

revoke execute on function public.weekly_muscle_volume(date) from public, anon;
grant execute on function public.weekly_muscle_volume(date) to authenticated;
