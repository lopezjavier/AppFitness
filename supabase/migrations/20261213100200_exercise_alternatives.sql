-- T-179 (ADR-83): curated alternatives between catalogue exercises.
-- One row per unordered pair (exercise_id < alternative_id); the RPC returns both directions.
-- Catalogue data shared by every user: no user_id, so nothing to cascade on account deletion. Rows are removed only
-- when one of the two exercises is deleted. Written by migrations only; clients can read.

create table public.exercise_alternatives (
  exercise_id    uuid not null references public.exercises (id) on delete cascade,
  alternative_id uuid not null references public.exercises (id) on delete cascade,
  similarity     numeric(3, 2) not null,
  primary key (exercise_id, alternative_id),
  constraint exercise_alternatives_ordered check (exercise_id < alternative_id),
  constraint exercise_alternatives_similarity_range check (similarity > 0 and similarity <= 1)
);

create index exercise_alternatives_alternative_idx on public.exercise_alternatives (alternative_id);

alter table public.exercise_alternatives enable row level security;
alter table public.exercise_alternatives force row level security;

create policy exercise_alternatives_select on public.exercise_alternatives
  for select to authenticated using (true);

revoke all on table public.exercise_alternatives from public, anon, authenticated;
grant select on table public.exercise_alternatives to authenticated;

-- The alternatives of one exercise, both directions, most similar first (ties by key).
create function public.exercise_alternatives(p_exercise_id uuid)
returns table (exercise_id uuid, similarity numeric)
language sql
stable
security invoker
set search_path = ''
as $$
  select x.id, x.similarity
  from (
    select a.alternative_id as id, a.similarity
      from public.exercise_alternatives a where a.exercise_id = p_exercise_id
    union all
    select a.exercise_id as id, a.similarity
      from public.exercise_alternatives a where a.alternative_id = p_exercise_id
  ) x
  join public.exercises e on e.id = x.id
  order by x.similarity desc, e.key
$$;

revoke execute on function public.exercise_alternatives(uuid) from public, anon;
grant execute on function public.exercise_alternatives(uuid) to authenticated;
