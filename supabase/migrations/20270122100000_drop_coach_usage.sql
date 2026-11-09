-- T-259 (ADR-103): no AI provider is used any more, so the usage ledger and its two budget RPCs go.
-- No CASCADE: a dependency error must fail loudly. Drops the two functions first, then the table (its policies and
-- constraints go with it). The self-check aborts the migration if any other public table changes its row count.
do $$
declare
  t record;
  v_before jsonb := '{}'::jsonb;
  v_after jsonb := '{}'::jsonb;
begin
  for t in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('r', 'p') and c.relname <> 'coach_usage'
  loop
    v_before := v_before || jsonb_build_object(
      t.relname,
      (xpath('/row/n/text()', query_to_xml(format('select count(*) as n from public.%I', t.relname), false, true, '')))[1]::text::bigint);
  end loop;

  drop function public.coach_record_usage(uuid, date, bigint, bigint, numeric);
  drop function public.coach_global_month_cost(date);
  drop table public.coach_usage;

  for t in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('r', 'p')
  loop
    v_after := v_after || jsonb_build_object(
      t.relname,
      (xpath('/row/n/text()', query_to_xml(format('select count(*) as n from public.%I', t.relname), false, true, '')))[1]::text::bigint);
  end loop;

  if v_before is distinct from v_after then
    raise exception 'drop_coach_usage changed the row count of another table';
  end if;
  if to_regclass('public.coach_usage') is not null then
    raise exception 'coach_usage still exists';
  end if;
end
$$;
