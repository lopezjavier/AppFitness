-- T-205 (E13, ADR-93, 07 section 3 "Taxonomy"): (1) the 7 lateral-raise keys move from isolation to
-- shoulder_abduction (hard check H3 counts them); (2) every catalogue hinge lists hamstrings as primary or secondary,
-- so the fractional volume count (04 section 4) does not undercount hamstrings (hinges are filed under glutes or back).
-- scripts/exercise-movement-patterns.csv and scripts/generate-exercise-catalog.py produce the same tags for a fresh
-- bundle import; this migration is the source for the live database.

do $$
declare
  v_rows integer;
begin
  update public.exercises
  set movement_pattern = 'shoulder_abduction'
  where not is_custom
    and key in ('lateral-raise', 'cable-lateral-raise', 'seated-dumbbell-lateral-raise', 'side-lying-lateral-raise',
                'bodyweight-lateral-raise', 'plate-loaded-lateral-raise', 'single-arm-plate-loaded-lateral-raise');
  get diagnostics v_rows = row_count;
  if v_rows <> 7 then
    raise exception 'expected 7 shoulder_abduction rows, got %', v_rows;
  end if;
end
$$;

-- The band-long copy of the cable lateral raise (T-179) follows its source pattern.
update public.exercises set movement_pattern = 'shoulder_abduction'
where not is_custom and key = 'band-long-cable-lateral-raise';

update public.exercises
set secondary_muscles = secondary_muscles || array['hamstrings']::public.muscle_group[]
where not is_custom
  and movement_pattern = 'hinge'
  and muscle_group <> 'hamstrings'
  and not ('hamstrings' = any (secondary_muscles));

do $$
begin
  if exists (select 1 from public.exercises e
             where not e.is_custom and e.movement_pattern = 'hinge'
               and e.muscle_group <> 'hamstrings' and not ('hamstrings' = any (e.secondary_muscles))) then
    raise exception 'a hinge exercise has no hamstrings (primary or secondary)';
  end if;
end
$$;
