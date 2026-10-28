-- T-136 (US-59): weekly check-in. Answers are stored per user and ISO week (Monday); the check-in day is a profile setting.
create table public.weekly_check_ins (
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  week_start date not null,
  energy     smallint not null,
  notes      text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, week_start),
  constraint weekly_check_ins_week_start_monday check (extract(isodow from week_start) = 1),
  constraint weekly_check_ins_energy_range check (energy between 1 and 5),
  constraint weekly_check_ins_notes_length check (char_length(notes) <= 1000)
);

create trigger weekly_check_ins_set_updated_at
  before update on public.weekly_check_ins
  for each row execute function public.set_updated_at();

alter table public.weekly_check_ins enable row level security;
alter table public.weekly_check_ins force row level security;

create policy weekly_check_ins_select_own on public.weekly_check_ins
  for select to authenticated
  using (user_id = (select auth.uid()));

create policy weekly_check_ins_insert_own on public.weekly_check_ins
  for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy weekly_check_ins_update_own on public.weekly_check_ins
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

revoke all on table public.weekly_check_ins from anon, authenticated;
grant select, insert, update on table public.weekly_check_ins to authenticated;

-- ISO weekday of the check-in day: 1 Monday ... 7 Sunday. Default Sunday.
alter table public.profiles
  add column check_in_weekday smallint not null default 7
  constraint profiles_check_in_weekday_range check (check_in_weekday between 1 and 7);

grant update (check_in_weekday) on public.profiles to authenticated;

-- Records set inside [p_from, p_to] (UTC session date, same rule as training_summary). Cumulative total_reps has no
-- single session and is excluded. Warm-up handling and tie-breaking are inherited from personal_records.
create function public.weekly_records(p_from date, p_to date)
returns table (exercise_id uuid, record_type text, value numeric, session_id uuid, session_date date)
language sql
stable
security invoker
set search_path = ''
as $$
  select e.id, pr.record_type, pr.value, pr.session_id, pr.session_date
  from public.exercises e
  cross join lateral public.personal_records(e.id) pr
  where pr.session_id is not null
    and pr.session_date between p_from and p_to
    and exists (
      select 1
      from public.workout_sessions s
      join public.workout_sets ws on ws.session_id = s.id
      where s.status = 'completed' and ws.exercise_id = e.id
    )
  order by e.id, pr.record_type;
$$;

revoke execute on function public.weekly_records(date, date) from public, anon;
grant execute on function public.weekly_records(date, date) to authenticated;
