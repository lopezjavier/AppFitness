-- RPE is a whole number 6-10 (ADR-56 correction, T-097j). Stored values below 6 are raised to 6.
-- No RPC validates the RPE range; the table checks are the single source of truth.

alter table public.workout_sets drop constraint workout_sets_rpe_whole_check;
alter table public.routine_exercises drop constraint routine_exercises_target_rpe_whole_check;

update public.workout_sets set rpe = 6 where rpe < 6;
update public.routine_exercises set target_rpe = 6 where target_rpe < 6;

alter table public.workout_sets
  add constraint workout_sets_rpe_whole_check check (rpe = trunc(rpe) and rpe between 6 and 10);
alter table public.routine_exercises
  add constraint routine_exercises_target_rpe_whole_check
  check (target_rpe = trunc(target_rpe) and target_rpe between 6 and 10);
