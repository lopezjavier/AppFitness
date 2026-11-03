-- T-211 (E13-H feedback, 07 section 3 "Taxonomy" > "Lower-body filing", supersedes 04 section 9): re-files the
-- lower-body catalogue so H1 counts the muscles that grow and H3b counts only knee extension.
--   1. knee-dominant compounds: quads primary, glutes secondary;
--   2. hip abduction: glutes;
--   3. hip flexion: core, never quads;
--   4. hip adduction (incl. inner-thigh mobility): the new group adductors (outside H1);
--   5. single keys by joint action: kettlebell-sumo-high-pull glutes, wide-legged-forward-fold hamstrings.
-- Only exercises.muscle_group / secondary_muscles change; routines, blocks and logged sets are not touched.
-- scripts/generate-exercise-catalog.py (KEY_GROUP_OVERRIDES, KEY_SECONDARY_OVERRIDES) produces the same tags for a
-- fresh bundle import; this migration is the source for the live database. Idempotent.

-- 1. Knee-dominant compounds (squat and lunge patterns, leg press, hack squat, step-up, wall sit, jump squat, box jump).
do $$
declare
  v_rows integer;
begin
  update public.exercises
  set muscle_group = 'quads',
      secondary_muscles = case when 'glutes' = any (secondary_muscles) then secondary_muscles
                               else secondary_muscles || array['glutes']::public.muscle_group[] end
  where not is_custom
    and key in (
      'banded-squat', 'barbell-bulgarian-split-squat', 'barbell-lunge', 'barbell-reverse-lunge',
      'bodyweight-reverse-lunge', 'bodyweight-squat', 'box-jump', 'box-squat', 'bulgarian-split-squat',
      'close-stance-leg-press', 'cossack-squat', 'db-lunge', 'db-somersault-squat', 'db-squat', 'db-sumo-squat',
      'deep-squat-hold', 'dumbbell-front-squat', 'dumbbell-pistol-squat', 'dumbbell-split-squat', 'front-squat',
      'goblet-squat', 'hack-squat', 'heel-elevated-squat', 'high-foot-leg-press', 'horizontal-leg-press',
      'jump-squat', 'kettlebell-bulgarian-split-squat', 'kettlebell-goblet-lunge', 'kettlebell-pistol-squat',
      'kettlebell-reverse-lunge', 'kettlebell-rotational-lunge', 'kettlebell-squat', 'leg-press', 'lunge',
      'one-arm-kettlebell-front-squat', 'overhead-squat', 'pause-squat', 'pistol-squat', 'plyo-lunge',
      'reverse-lunge', 'side-lunge', 'single-leg-press', 'smith-machine-bulgarian-split',
      'smith-machine-front-squat', 'smith-machine-reverse-lunge', 'smith-machine-split-squat',
      'smith-machine-squat', 'split-squat', 'squat', 'squat-hold', 'squat-pulse', 'stability-ball-wall-squat',
      'step-ups', 'sumo-squat', 'trx-lunge', 'trx-pistol-squat', 'trx-squat', 'walking-lunge', 'wall-sit',
      'wide-stance-leg-press', 'zercher-squat');
  get diagnostics v_rows = row_count;
  if v_rows <> 61 then
    raise exception 'expected 61 knee-dominant rows, got %', v_rows;
  end if;
end
$$;

-- 2. Hip abduction: glutes.
do $$
declare
  v_rows integer;
begin
  update public.exercises
  set muscle_group = 'glutes'
  where not is_custom
    and key in ('hip-abduction', 'banded-clamshell', 'banded-fire-hydrant', 'banded-seated-hip-abduction',
                'banded-standing-hip-abduction', 'clamshells', 'clamshells-hold', 'fire-hydrant',
                'side-lying-hip-abduction', 'side-lying-hip-abduction-hold');
  get diagnostics v_rows = row_count;
  if v_rows <> 10 then
    raise exception 'expected 10 hip-abduction rows, got %', v_rows;
  end if;
end
$$;

-- 3. Hip flexion: core, never quads.
do $$
declare
  v_rows integer;
begin
  update public.exercises
  set muscle_group = 'core',
      secondary_muscles = array_remove(secondary_muscles, 'quads'::public.muscle_group)
  where not is_custom
    and key in ('captains-chair-knee-raise', 'captains-chair-leg-raise', 'hanging-knee-raise', 'hanging-l-sit',
                'hanging-leg-raise', 'hanging-tuck-hold', 'lying-leg-raise', 'ring-l-sit', 'v-sit', 'l-sit',
                'sit-ups', 'v-ups', 'toes-to-bar', 'flutter-kicks', 'scissor-kicks', 'bench-hollow-hold',
                'hollow-body-hold', 'boat-pose', 'six-inch-hold', 'plank-knee-tuck', 'stability-ball-knee-tuck',
                'bench-leg-pull-in', 'mountain-climbers', 'pilates-side-bend',
                'downward-dog-to-knee-drive', 'downward-dog-knee-tuck');
  get diagnostics v_rows = row_count;
  if v_rows <> 26 then
    raise exception 'expected 26 hip-flexion rows, got %', v_rows;
  end if;
end
$$;

-- 4. Hip adduction: adductors (the enum value was added by the previous migration).
do $$
declare
  v_rows integer;
begin
  update public.exercises
  set muscle_group = 'adductors'
  where not is_custom
    and key in ('hip-adduction', 'banded-standing-hip-adduction', 'side-lying-hip-adduction',
                'side-lying-hip-adduction-hold', 'banded-adductor-stretch', 'bench-adductor-stretch',
                'butterfly-stretch',
                'seated-straddle-stretch', 'lateral-leg-swing', 'happy-baby', 'garland-pose');
  get diagnostics v_rows = row_count;
  if v_rows <> 11 then
    raise exception 'expected 11 hip-adduction rows, got %', v_rows;
  end if;
end
$$;

-- 5. Keys filed by the joint action (07 section 3 "Keys, not names"): a hinge and a hamstring stretch.
do $$
declare
  v_rows integer;
begin
  update public.exercises
  set muscle_group = 'glutes',
      secondary_muscles = case when 'hamstrings' = any (secondary_muscles) then secondary_muscles
                               else secondary_muscles || array['hamstrings']::public.muscle_group[] end
  where not is_custom and key = 'kettlebell-sumo-high-pull';
  get diagnostics v_rows = row_count;
  if v_rows <> 1 then
    raise exception 'expected 1 sumo high pull row, got %', v_rows;
  end if;
  update public.exercises set muscle_group = 'hamstrings'
  where not is_custom and key = 'wide-legged-forward-fold';
  get diagnostics v_rows = row_count;
  if v_rows <> 1 then
    raise exception 'expected 1 wide-legged-forward-fold row, got %', v_rows;
  end if;
end
$$;

-- 6. Final assertions.
do $$
begin
  if exists (select 1 from public.exercises
             where not is_custom and key in ('hip-adduction', 'side-lying-hip-adduction', 'banded-standing-hip-adduction')
               and muscle_group <> 'adductors') then
    raise exception 'a hip-adduction key is not filed under adductors';
  end if;
  if exists (select 1 from public.exercises
             where not is_custom and key in ('squat', 'leg-press', 'walking-lunge', 'step-ups', 'wall-sit')
               and (muscle_group <> 'quads' or not ('glutes' = any (secondary_muscles)))) then
    raise exception 'a knee-dominant exercise is not quads with glutes secondary';
  end if;
  if exists (select 1 from public.exercises
             where not is_custom and muscle_group = 'quads' and not ('glutes' = any (secondary_muscles))
               and movement_pattern in ('squat', 'lunge')) then
    raise exception 'a squat or lunge row has no glutes secondary';
  end if;
end
$$;
