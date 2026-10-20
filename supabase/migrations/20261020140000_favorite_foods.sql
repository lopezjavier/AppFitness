-- T-057 (US-17): favourite foods per user and the "most eaten" ranking of the caller's own diary entries.

create table public.favorite_foods (
  user_id    uuid not null references auth.users (id) on delete cascade default auth.uid(),
  food_id    uuid not null references public.foods (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, food_id)
);

create index favorite_foods_food_id_idx on public.favorite_foods (food_id);

alter table public.favorite_foods enable row level security;
alter table public.favorite_foods force row level security;

create policy favorite_foods_select_own on public.favorite_foods
  for select to authenticated
  using (user_id = (select auth.uid()));

-- The food subquery runs under RLS: another user's personal food cannot be favourited.
create policy favorite_foods_insert_own on public.favorite_foods
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.foods f where f.id = food_id));

create policy favorite_foods_delete_own on public.favorite_foods
  for delete to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.favorite_foods from anon, authenticated;
grant select, insert, delete on table public.favorite_foods to authenticated;

-- Foods the caller logs most often (ties: most recently logged first).
create function public.most_eaten_foods(p_limit int default 20)
  returns table (food_id uuid, times_logged bigint)
  language sql
  stable
  security invoker
  set search_path = ''
as $$
  select e.food_id, count(*) as times_logged
  from public.food_log_entries e
  where e.user_id = (select auth.uid())
  group by e.food_id
  order by count(*) desc, max(e.created_at) desc
  limit least(greatest(coalesce(p_limit, 20), 1), 100)
$$;

revoke execute on function public.most_eaten_foods(int) from public, anon;
grant execute on function public.most_eaten_foods(int) to authenticated;
