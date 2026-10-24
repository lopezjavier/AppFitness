-- RPE is a whole number 1-10 (ADR-56, T-097g). Stored half values are rounded
-- (numeric round is half away from zero: 7.5 -> 8, 6.5 -> 7). Column types stay numeric(3,1).

update public.workout_sets set rpe = round(rpe) where rpe <> round(rpe);
update public.routine_exercises set target_rpe = round(target_rpe) where target_rpe <> round(target_rpe);

do $$
declare
  c record;
begin
  -- Drop the existing range checks, looking their names up instead of assuming them.
  for c in
    select con.conrelid::regclass as tbl, con.conname
    from pg_constraint con
    join pg_attribute a on a.attrelid = con.conrelid and a.attnum = any (con.conkey)
    where con.contype = 'c'
      and con.conrelid in ('public.workout_sets'::regclass, 'public.routine_exercises'::regclass)
      and array_length(con.conkey, 1) = 1
      and ((con.conrelid = 'public.workout_sets'::regclass and a.attname = 'rpe')
        or (con.conrelid = 'public.routine_exercises'::regclass and a.attname = 'target_rpe'))
  loop
    execute format('alter table %s drop constraint %I', c.tbl, c.conname);
  end loop;
end
$$;

alter table public.workout_sets
  add constraint workout_sets_rpe_whole_check check (rpe = trunc(rpe) and rpe between 1 and 10);
alter table public.routine_exercises
  add constraint routine_exercises_target_rpe_whole_check
  check (target_rpe = trunc(target_rpe) and target_rpe between 1 and 10);
