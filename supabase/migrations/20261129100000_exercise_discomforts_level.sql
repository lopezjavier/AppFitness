-- T-152 (US-67, ADR-81): replace the derived `severe` flag with a required level 1..5.
-- Old rows: severe -> 5 (highest), not severe -> 2 ("Molestia leve"); level 1 is only for new rows.
-- `severe` is derived in the domain (level >= 4), so the column is dropped.
-- Grants are untouched: table-level select/insert covers the new column and
-- the update grant stays on resolved_at only, so level is immutable after insert.
alter table public.exercise_discomforts add column level smallint;

update public.exercise_discomforts set level = case when severe then 5 else 2 end;

alter table public.exercise_discomforts alter column level set not null;
alter table public.exercise_discomforts
  add constraint exercise_discomforts_level_range check (level between 1 and 5);

alter table public.exercise_discomforts drop column severe;
