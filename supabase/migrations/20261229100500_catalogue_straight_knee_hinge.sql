-- T-213 (E13-H feedback 3, 07 section 3 "Taxonomy" > "Straight-knee hinge"): the Romanian and stiff-leg deadlift family,
-- kickstand deadlifts and good mornings are filed hamstrings primary, glutes secondary (a good morning no longer counts
-- as back). Bent-knee deadlifts (conventional, sumo, hex-bar, deficit, pause) are not touched.
-- Only exercises.muscle_group / secondary_muscles change; routines, blocks and logged sets are not touched.
-- scripts/generate-exercise-catalog.py (STRAIGHT_KNEE_HINGE_KEYS) produces the same tags for a fresh bundle import.
-- Idempotent. A group is never both primary and secondary: hamstrings is removed from secondary.
do $$
declare
  v_rows integer;
begin
  update public.exercises
  set muscle_group = 'hamstrings',
      secondary_muscles = case
        when 'glutes' = any (secondary_muscles)
          then array_remove(secondary_muscles, 'hamstrings'::public.muscle_group)
        else array_remove(secondary_muscles, 'hamstrings'::public.muscle_group)
             || array['glutes']::public.muscle_group[]
      end
  where not is_custom
    and key in (
      'romanian-deadlift', 'ez-bar-romanian-deadlift', 'dumbbell-romanian-deadlift', 'banded-romanian-deadlift',
      'smith-machine-rdl', 'stiff-leg-deadlift', 'single-leg-romanian-deadlift',
      'one-arm-single-leg-dumbbell-romanian-deadlift', 'one-arm-single-leg-kettlebell-romanian-deadlift',
      'kettlebell-single-leg-deadlift', 'single-leg-rdl-hold', 'db-kickstand-deadlift',
      'double-db-kickstand-deadlift', 'kettlebell-kickstand-deadlift', 'good-morning', 'banded-good-morning',
      'bodyweight-good-morning', 'smith-machine-good-morning');
  get diagnostics v_rows = row_count;
  if v_rows <> 18 then
    raise exception 'expected 18 straight-knee hinge rows, got %', v_rows;
  end if;
  if exists (select 1 from public.exercises
             where not is_custom and muscle_group = 'hamstrings' and 'hamstrings' = any (secondary_muscles)) then
    raise exception 'a hamstrings row lists hamstrings as secondary';
  end if;
end
$$;
