-- T-097i (US-09, US-32, ADR-56): per-exercise hand count replaces the per-user double-dumbbell option.
-- per_hand: the logged weight is per hand/side, so volume counts it twice (1RM is never doubled).
-- exercise_options and set_exercise_double_dumbbell are dropped in a later migration of this task.

alter table public.exercises add column per_hand boolean not null default false;

-- Catalogue flags (hands = 2), keys from the maintainer's list.
update public.exercises set per_hand = true
where not is_custom and key in (
  'air-bike',
  'arnold-press',
  'band-pull-apart',
  'banded-adductor-stretch',
  'banded-ankle-stretch',
  'banded-calf-stretch',
  'banded-chest-stretch',
  'banded-figure-4-stretch',
  'banded-hamstring-stretch',
  'banded-lat-stretch',
  'banded-romanian-deadlift',
  'banded-shoulder-stretch',
  'banded-triceps-stretch',
  'battle-rope-double-slam',
  'battle-ropes',
  'behind-the-neck-lat-pulldown',
  'bent-over-db-row',
  'bicep-curl',
  'bulgarian-split-squat',
  'cable-bent-over-row',
  'cable-chest-press',
  'cable-crunch',
  'cable-curl',
  'cable-fly',
  'cable-hammer-curl',
  'cable-overhead-tricep-extension',
  'cable-pallof-press',
  'cable-pull-through',
  'cable-upright-row',
  'cable-wrist-curl',
  'chest-press-machine',
  'chest-supported-db-row',
  'chest-supported-dumbbell-shrug',
  'chest-supported-kettlebell-row',
  'chest-supported-machine-row',
  'chest-supported-smith-machine-row',
  'close-grip-db-bench-press',
  'close-grip-lat-pulldown',
  'cross-body-hammer-curl',
  'db-bench-press',
  'db-fly',
  'db-lunge',
  'db-preacher-curl',
  'db-pullover',
  'db-reverse-curl',
  'db-shrug',
  'db-skull-crusher',
  'db-squat',
  'db-sumo-squat',
  'db-svend-press',
  'decline-bench-press',
  'decline-db-fly',
  'double-db-kickstand-deadlift',
  'double-db-overhead-carry',
  'double-kettlebell-bicep-curl',
  'double-kettlebell-clean',
  'double-kettlebell-clean-and-press',
  'double-kettlebell-dead-clean',
  'double-kettlebell-dead-split-snatch',
  'double-kettlebell-jerk',
  'double-kettlebell-overhead-carry',
  'double-kettlebell-overhead-press',
  'double-kettlebell-push-press',
  'double-kettlebell-rear-delt-row',
  'double-kettlebell-row',
  'double-kettlebell-split-jerk',
  'double-kettlebell-swing-snatch',
  'dumbbell-bench-pull',
  'dumbbell-calf-raise',
  'dumbbell-clean-and-press',
  'dumbbell-curl-and-press',
  'dumbbell-deadlift',
  'dumbbell-face-pull',
  'dumbbell-farmers-walk',
  'dumbbell-floor-press',
  'dumbbell-front-raise',
  'dumbbell-front-squat',
  'dumbbell-hip-thrust',
  'dumbbell-pistol-squat',
  'dumbbell-push-press',
  'dumbbell-reverse-fly',
  'dumbbell-romanian-deadlift',
  'dumbbell-shoulder-press',
  'dumbbell-split-squat',
  'dumbbell-upright-row',
  'elliptical-trainer',
  'face-pull',
  'floor-kettlebell-pullover',
  'goblet-squat',
  'hammer-curl',
  'incline-db-curl',
  'incline-db-press',
  'incline-dumbbell-fly',
  'incline-hammer-curl',
  'jefferson-curl',
  'jump-rope',
  'kettlebell-close-grip-floor-press',
  'kettlebell-deadlift',
  'kettlebell-farmers-walk',
  'kettlebell-floor-press',
  'kettlebell-goblet-lunge',
  'kettlebell-halo',
  'kettlebell-hip-thrust',
  'kettlebell-overhead-tricep-extension',
  'kettlebell-pistol-squat',
  'kettlebell-pullover',
  'kettlebell-rotational-lunge',
  'kettlebell-russian-twist',
  'kettlebell-shrug',
  'kettlebell-skull-crusher',
  'kettlebell-squat',
  'kettlebell-sumo-deadlift',
  'kettlebell-sumo-high-pull',
  'kettlebell-svend-press',
  'kettlebell-swing',
  'kneeling-cable-row',
  'lat-pulldown',
  'lateral-raise',
  'lying-tricep-extension',
  'machine-bicep-curl',
  'machine-chest-fly',
  'machine-preacher-curl',
  'machine-reverse-fly',
  'machine-shoulder-press',
  'machine-triceps-extension',
  'medicine-ball-slam',
  'overhead-tricep-extension',
  'pec-deck',
  'plate-loaded-lateral-raise',
  'plate-loaded-seated-dip-machine',
  'plate-loaded-shrug',
  'plate-pullover',
  'preacher-hammer-curl',
  'rear-delt-fly',
  'renegade-row',
  'reverse-grip-lat-pulldown',
  'reverse-lunge',
  'rowing-machine',
  'seated-cable-row',
  'seated-db-press',
  'seated-dip-machine',
  'seated-dumbbell-curl',
  'seated-dumbbell-lateral-raise',
  'seated-dumbbell-tricep-extension',
  'seated-smith-machine-shoulder-press',
  'single-db-svend-press',
  'single-leg-romanian-deadlift',
  'ski-erg',
  'sled-row',
  'smith-machine-bench-press',
  'smith-machine-bent-over-row',
  'smith-machine-bulgarian-split',
  'smith-machine-decline-bench-press',
  'smith-machine-front-squat',
  'smith-machine-good-morning',
  'smith-machine-hip-thrust',
  'smith-machine-incline-bench-press',
  'smith-machine-rdl',
  'smith-machine-reverse-grip-bent-over-row',
  'smith-machine-reverse-lunge',
  'smith-machine-shoulder-press',
  'smith-machine-shrug',
  'smith-machine-split-squat',
  'smith-machine-squat',
  'smith-machine-upright-row',
  'spider-curl',
  'straight-arm-pulldown',
  'straight-bar-cable-front-raise',
  'strict-curl',
  'svend-press',
  'tricep-pushdown',
  'v-bar-lat-pulldown',
  'v-bar-tricep-pushdown',
  'weighted-wall-crunch',
  'wide-grip-seated-cable-row',
  'wrist-curl',
  'wrist-roller',
  'zottman-curl'
);

-- 2 when the exercise's weight is per hand, else 1.
create or replace function public.volume_factor(p_exercise_id uuid)
returns numeric
language sql
stable
security invoker
set search_path = ''
as $$
  select case when e.per_hand then 2 else 1 end::numeric
  from public.exercises e
  where e.id = p_exercise_id
  union all
  select 1::numeric
  where not exists (select 1 from public.exercises e where e.id = p_exercise_id)
  limit 1;
$$;

revoke execute on function public.volume_factor(uuid) from public, anon;
grant execute on function public.volume_factor(uuid) to authenticated;

-- Manual exercises: edit (metric_type is not editable) and delete (blocked while referenced).
create function public.update_custom_exercise(
  p_id uuid, p_name text, p_muscle_group public.muscle_group, p_equipment public.equipment, p_per_hand boolean)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_name text := btrim(coalesce(p_name, ''));
  v_rows int;
begin
  if char_length(v_name) < 1 or char_length(v_name) > 100 then
    raise exception 'name must be 1-100 characters' using errcode = '22023';
  end if;
  update public.exercises
     set name = v_name, muscle_group = p_muscle_group, equipment = p_equipment,
         per_hand = coalesce(p_per_hand, false)
   where id = p_id and is_custom and created_by = (select auth.uid());
  get diagnostics v_rows = row_count;
  if v_rows = 0 then
    raise exception 'exercise not found' using errcode = 'P0002';
  end if;
end;
$$;

create function public.delete_custom_exercise(p_id uuid)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_sets int;
  v_routines int;
begin
  if not exists (select 1 from public.exercises
                 where id = p_id and is_custom and created_by = (select auth.uid())) then
    raise exception 'exercise not found' using errcode = 'P0002';
  end if;
  select count(*) into v_sets from public.workout_sets where exercise_id = p_id;
  select count(*) into v_routines from public.routine_exercises where exercise_id = p_id;
  if v_sets > 0 or v_routines > 0 then
    raise exception 'exercise in use (% sets, % routines)', v_sets, v_routines
      using errcode = '23503', detail = format('sets=%s;routines=%s', v_sets, v_routines);
  end if;
  delete from public.exercises where id = p_id and is_custom and created_by = (select auth.uid());
end;
$$;

revoke execute on function public.update_custom_exercise(uuid, text, public.muscle_group, public.equipment, boolean)
  from public, anon;
grant execute on function public.update_custom_exercise(uuid, text, public.muscle_group, public.equipment, boolean)
  to authenticated;
revoke execute on function public.delete_custom_exercise(uuid) from public, anon;
grant execute on function public.delete_custom_exercise(uuid) to authenticated;
