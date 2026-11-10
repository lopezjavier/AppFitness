-- T-272 (E18, US-133, ADR-105): a diary entry outlives its food. Entries keep their own snapshot
-- (food_name, kcal, macros, amount, portion_label), so food_id becomes nullable and is set to null when the
-- food is deleted. Insert/update RLS still requires an existing food, so a null food_id is view/delete only.

alter table public.food_log_entries alter column food_id drop not null;

alter table public.food_log_entries drop constraint food_log_entries_food_id_fkey;
alter table public.food_log_entries
  add constraint food_log_entries_food_id_fkey
  foreign key (food_id) references public.foods (id) on delete set null;

-- Entries without a food would otherwise form one bogus group.
create or replace function public.most_eaten_foods(p_limit int default 20)
  returns table (food_id uuid, times_logged bigint)
  language sql
  stable
  security invoker
  set search_path = ''
as $$
  select e.food_id, count(*) as times_logged
  from public.food_log_entries e
  where e.user_id = (select auth.uid())
    and e.food_id is not null
  group by e.food_id
  order by count(*) desc, max(e.created_at) desc
  limit least(greatest(coalesce(p_limit, 20), 1), 100)
$$;
