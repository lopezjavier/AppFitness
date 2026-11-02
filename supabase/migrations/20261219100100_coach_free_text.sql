-- T-185 (US-89, ADR-87): the free-text block the coach model reads. The user's own words (intake text, check-in notes,
-- discomfort notes) go to the model only inside a visible, excludable, size-limited block. Words never change numbers.

create table public.coach_free_text_exclusions (
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind       text not null,
  -- intake = 'intake'; check_in = week_start (ISO date); discomfort = exercise_discomforts.id; review = reserved (T-190).
  source_id  text not null,
  created_at timestamptz not null default now(),
  primary key (user_id, kind, source_id),
  constraint coach_free_text_exclusions_kind check (kind in ('intake', 'check_in', 'discomfort', 'review')),
  constraint coach_free_text_exclusions_source_id check (char_length(source_id) between 1 and 64)
);

alter table public.coach_free_text_exclusions enable row level security;
alter table public.coach_free_text_exclusions force row level security;

create policy coach_free_text_exclusions_select_own on public.coach_free_text_exclusions
  for select to authenticated using (user_id = (select auth.uid()));
create policy coach_free_text_exclusions_insert_own on public.coach_free_text_exclusions
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy coach_free_text_exclusions_delete_own on public.coach_free_text_exclusions
  for delete to authenticated using (user_id = (select auth.uid()));

revoke all on table public.coach_free_text_exclusions from anon, authenticated;
grant select, insert, delete on table public.coach_free_text_exclusions to authenticated;

-- Every free-text line of the caller, newest first (ties: kind, source_id), with its status:
-- excluded (the user left it out), sent (inside the 2 000-byte budget) or over_limit (the budget or the 20-line
-- maximum is exhausted; a line is never truncated, the cut is a strict prefix). The byte count is the JSON-escaped
-- text, i.e. what the model payload carries. Never read: set/routine notes, food names, custom exercise names.
create function public.coach_free_text_lines(p_today date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid  uuid := public.coach_guard(p_today);
  v_from date := public.coach_week_start(p_today) - 49;
  v_out  jsonb;
begin
  with src as (
    select 'intake'::text as kind, 'intake'::text as source_id, (i.saved_at at time zone 'UTC')::date as d,
           btrim(i.free_text) as txt
    from public.coach_user_intake i where i.user_id = v_uid
    union all
    select 'check_in', c.week_start::text, c.week_start, btrim(c.notes)
    from public.weekly_check_ins c
    where c.user_id = v_uid and c.week_start between v_from and p_today
    union all
    select 'discomfort', x.id::text, (x.logged_at at time zone 'UTC')::date, btrim(x.note)
    from public.exercise_discomforts x
    where x.user_id = v_uid and (x.logged_at at time zone 'UTC')::date between v_from and p_today
  ), ordered as (
    select s.*, (e.user_id is not null) as excluded,
           row_number() over (order by s.d desc, s.kind, s.source_id) as pos
    from src s
    left join public.coach_free_text_exclusions e
      on e.user_id = v_uid and e.kind = s.kind and e.source_id = s.source_id
    where s.txt is not null and s.txt <> ''
  ), counted as (
    select o.*,
           case when o.excluded then null else row_number() over (partition by o.excluded order by o.pos) end as nth,
           case when o.excluded then 0 else octet_length(to_jsonb(o.txt)::text) - 2 end as bytes
    from ordered o
  ), cum as (
    select c.*, sum(c.bytes) over (order by c.pos) as total
    from counted c
  )
  select coalesce(jsonb_agg(jsonb_build_object(
           'kind', kind, 'source_id', source_id, 'date', d, 'text', txt,
           'status', case when excluded then 'excluded'
                          when nth <= 20 and total <= 2000 then 'sent'
                          else 'over_limit' end) order by pos), '[]'::jsonb)
  into v_out
  from cum;
  return v_out;
end;
$$;

create function public.set_free_text_line_excluded(p_kind text, p_id text, p_excluded boolean)
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_kind is null or p_kind not in ('intake', 'check_in', 'discomfort', 'review') then
    raise exception 'invalid kind' using errcode = '22023';
  end if;
  if p_id is null or char_length(p_id) not between 1 and 64 then
    raise exception 'invalid id' using errcode = '22023';
  end if;
  if p_excluded is null then
    raise exception 'excluded must be true or false' using errcode = '22023';
  end if;
  if p_excluded then
    insert into public.coach_free_text_exclusions (user_id, kind, source_id)
    values (v_uid, p_kind, p_id)
    on conflict do nothing;
  else
    delete from public.coach_free_text_exclusions
    where user_id = v_uid and kind = p_kind and source_id = p_id;
  end if;
end;
$$;

revoke execute on function public.coach_free_text_lines(date) from public, anon;
revoke execute on function public.set_free_text_line_excluded(text, text, boolean) from public, anon;
grant execute on function public.coach_free_text_lines(date) to authenticated;
grant execute on function public.set_free_text_line_excluded(text, text, boolean) to authenticated;
