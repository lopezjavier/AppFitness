-- T-160 (E13, US-76): monthly AI usage per user, written only by the `coach` Edge Function.
-- Budget rules: planning/07-coach-design.md section 11.

create table public.coach_usage (
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  -- First day of the UTC month.
  month         date not null constraint coach_usage_month_first_day check (month = date_trunc('month', month)::date),
  input_tokens  bigint not null default 0 constraint coach_usage_input_tokens_range check (input_tokens >= 0),
  output_tokens bigint not null default 0 constraint coach_usage_output_tokens_range check (output_tokens >= 0),
  cost_eur      numeric(12, 6) not null default 0 constraint coach_usage_cost_range check (cost_eur >= 0),
  updated_at    timestamptz not null default now(),
  primary key (user_id, month)
);

alter table public.coach_usage enable row level security;
alter table public.coach_usage force row level security;

-- Read-only for the owner: no insert/update/delete policy, and no such privilege either.
create policy coach_usage_select_own on public.coach_usage
  for select to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.coach_usage from anon, authenticated;
grant select on table public.coach_usage to authenticated;

-- ---------------------------------------------------------------------------
-- Service-role RPCs. They exist because the function's per-user token client cannot write the table
-- nor read other users' rows (the global budget needs the total across users).
-- ---------------------------------------------------------------------------

-- Atomically adds one call's usage to the (user, month) row.
create function public.coach_record_usage(
  p_user uuid, p_month date, p_input bigint, p_output bigint, p_cost numeric
)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.coach_usage (user_id, month, input_tokens, output_tokens, cost_eur)
  values (p_user, p_month, p_input, p_output, p_cost)
  on conflict (user_id, month) do update
    set input_tokens  = public.coach_usage.input_tokens + excluded.input_tokens,
        output_tokens = public.coach_usage.output_tokens + excluded.output_tokens,
        cost_eur      = public.coach_usage.cost_eur + excluded.cost_eur,
        updated_at    = now();
$$;

-- Total cost of every user in a month: one number, no rows exposed.
create function public.coach_global_month_cost(p_month date)
returns numeric
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(sum(cost_eur), 0) from public.coach_usage where month = p_month;
$$;

revoke all on function public.coach_record_usage(uuid, date, bigint, bigint, numeric) from public, anon, authenticated;
grant execute on function public.coach_record_usage(uuid, date, bigint, bigint, numeric) to service_role;
revoke all on function public.coach_global_month_cost(date) from public, anon, authenticated;
grant execute on function public.coach_global_month_cost(date) to service_role;
