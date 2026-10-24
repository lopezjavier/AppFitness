-- T-097i (ADR-56): per_hand on exercises replaces the per-user double-dumbbell option.
-- Old per-user toggles are discarded; volume_factor no longer reads exercise_options (see 20261031100000).
drop function if exists public.set_exercise_double_dumbbell(uuid, boolean);
drop table if exists public.exercise_options;
