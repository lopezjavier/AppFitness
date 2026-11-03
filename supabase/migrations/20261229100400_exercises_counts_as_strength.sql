-- T-211 (user decision 2026-11-03, planning/07 section 3 "What H1 counts"): the coach's H1 weekly-set check counts
-- only strength exercises. A stored per-key flag, set from the 07 lists; catalogue read-only data (no RLS change:
-- the exercises policies cover the whole row). Custom rows keep the default (true); the coach decides them by
-- metric type. Generated rule: scripts/generate-exercise-catalog.py (render_strength_sql).

alter table public.exercises
  add column if not exists counts_as_strength boolean not null default true;

comment on column public.exercises.counts_as_strength is
  'False for stretches, mobility, cardio, impact moves and timed holds: their sets do not add to the coach''s H1 count (07 section 3).';

-- Every timed or distance-based catalogue key (holds, planks, stretches, yoga poses, carries, cardio), every cardio
-- pattern or cardio machine key, and the reps-only mobility, flow and impact keys listed below.
update public.exercises
set counts_as_strength = false
where not is_custom
  and (metric_type in ('duration', 'weight_duration', 'distance_duration', 'floors_duration', 'steps_duration')
       or movement_pattern = 'cardio'
       or equipment = 'cardio_machine'
       or key in (
  'battle-rope-double-slam',
  'bear-crawl',
  'box-jump',
  'burpees',
  'cat-cow',
  'downward-dog-knee-tuck',
  'downward-dog-pedal',
  'downward-dog-to-knee-drive',
  'downward-dog-to-low-lunge',
  'downward-dog-to-plank',
  'downward-dog-to-upward-dog',
  'half-kneeling-hip-flexor-rock',
  'heel-flicks',
  'heel-to-toe-walk',
  'high-knees',
  'inchworm',
  'jump-squat',
  'lateral-leg-swing',
  'leg-swing-front-to-back',
  'plyo-lunge',
  'standing-side-bend',
  'standing-side-bend-flow',
  'thoracic-bridge',
  'thread-the-needle-flow',
  'upward-dog'
));

-- Guard: the named keys exist, the lifts stay counted, the holds and cardio rows do not.
do $$
declare
  v_named integer;
begin
  select count(*) into v_named from public.exercises
  where not is_custom and not counts_as_strength and key in (
    'battle-rope-double-slam', 'bear-crawl', 'box-jump', 'burpees', 'cat-cow', 'downward-dog-knee-tuck',
    'downward-dog-pedal', 'downward-dog-to-knee-drive', 'downward-dog-to-low-lunge', 'downward-dog-to-plank',
    'downward-dog-to-upward-dog', 'half-kneeling-hip-flexor-rock', 'heel-flicks', 'heel-to-toe-walk', 'high-knees',
    'inchworm', 'jump-squat', 'lateral-leg-swing', 'leg-swing-front-to-back', 'plyo-lunge', 'standing-side-bend',
    'standing-side-bend-flow', 'thoracic-bridge', 'thread-the-needle-flow', 'upward-dog');
  if v_named <> 25 then
    raise exception 'counts_as_strength: expected 25 named reps-only keys, found %', v_named;
  end if;
  if exists (select 1 from public.exercises where not is_custom and not counts_as_strength
             and key in ('squat', 'bench-press', 'deadlift', 'glute-bridge', 'clamshells', 'push-ups')) then
    raise exception 'counts_as_strength: a strength lift was excluded';
  end if;
  if exists (select 1 from public.exercises where not is_custom and counts_as_strength
             and key in ('deep-squat-hold', 'squat-hold', 'stationary-bike', 'plank', 'wall-sit', 'jump-rope')) then
    raise exception 'counts_as_strength: a hold or cardio row was counted';
  end if;
end
$$;
