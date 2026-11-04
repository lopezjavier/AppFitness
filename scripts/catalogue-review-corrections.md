# T-216 catalogue gaps: corrections for the backend developer

Rule source: `planning/07-coach-design.md` §3 "Folder review" > "Catalogue facts the review reads" (refs 4, 86, 91, 93, 94).
State audited: `20261019130100_exercise_catalog.sql` + later migrations (metric types, patterns, T-201 retag, T-205 hinge
secondary, T-211 leg filing, T-213 straight-knee hinge, counts_as_strength) + `20261213100100_band_long_catalog.sql`.

Root cause: the catalogue import wrote no secondary muscles. `secondary_muscles` is empty for every row except hinges
(hamstrings, T-205), knee-dominant compounds (glutes, T-211), straight-knee hinges (glutes, T-213) and
kettlebell-sumo-high-pull. So no press counts for shoulders and no pull counts for biceps.

Apply rules: **add** = append to `secondary_muscles` if not present (never duplicate). **primary** = new `muscle_group`.
Every correction to a cable source row also applies to its `band-long-*` copy (listed explicitly below).
Catalogue rows only (`not is_custom`). Keep the generator (`scripts/generate-exercise-catalog.py`,
`scripts/exercise-movement-patterns.csv`) in sync.

## A. Secondary muscles added (primary unchanged) — 155 rows

| # | Keys | Primary (unchanged) | Add secondary | Pattern | counts |
|---|---|---|---|---|---|
| A1 | archer-push-ups, bench-press, cable-chest-press, band-long-cable-chest-press, chest-press-machine, clap-push-ups, close-grip-push-ups, db-bench-press, decline-bench-press, decline-bench-press-barbell, decline-bench-press-ez-bar, decline-push-up, deficit-push-ups, diamond-push-ups, dumbbell-floor-press, ez-bar-bench-press, floor-ez-bar-press, floor-press, incline-bench-ez-bar-press, incline-bench-press, incline-db-press, incline-push-ups, kettlebell-floor-press, knee-push-ups, one-arm-kettlebell-floor-press, paused-bench-press, plyo-push-up, push-up, ring-push-up, single-arm-dumbbell-bench-press, smith-machine-bench-press, smith-machine-decline-bench-press, spiderman-push-up, spoto-press, stability-ball-push-up, stability-ball-push-up-hands-on-ball, trx-chest-press, wall-push-ups, wide-grip-bench-press, wide-grip-push-ups (40) | chest | shoulders, triceps | horizontal_push | true |
| A2 | dips, assisted-dips, ring-dips, straight-bar-dips, weighted-dips (5) | chest | shoulders, triceps | vertical_push | true |
| A3 | close-grip-bench-press, close-grip-db-bench-press, close-grip-ez-bar-bench-press, close-grip-incline-bench, kettlebell-close-grip-floor-press (5) | triceps | chest, shoulders | horizontal_push | true |
| A4 | bench-dips, crab-dips, seated-dip-machine, plate-loaded-seated-dip-machine, reverse-plank-dips (5) | triceps | chest, shoulders | vertical_push | true |
| A5 | pseudo-planche-push-ups (1) | shoulders | chest, triceps | horizontal_push | true |
| A6 | arnold-press, behind-the-neck-press, bodyweight-overhead-press, double-kettlebell-clean-and-press, double-kettlebell-jerk, double-kettlebell-overhead-press, double-kettlebell-push-press, dumbbell-push-press, dumbbell-shoulder-press, handstand-push-ups, landmine-press, machine-shoulder-press, ohp, one-arm-dumbbell-push-press, one-arm-dumbbell-shoulder-press, one-arm-kettlebell-bottoms-up-press, one-arm-kettlebell-push-press, one-arm-kettlebell-shoulder-press, one-arm-landmine-press, paused-ohp, pike-push-ups, push-press, seated-barbell-overhead-press, seated-db-press, seated-smith-machine-shoulder-press, single-arm-machine-shoulder-press, smith-machine-shoulder-press (27) | shoulders | triceps | vertical_push | true |
| A7 | thruster (1) | shoulders | triceps, quads, glutes | vertical_push | true |
| A8 | dumbbell-curl-and-press (1) | shoulders | triceps, biceps | vertical_push | true |
| A9 | archer-pull-ups, assisted-pull-ups, band-assisted-pull-ups, behind-the-neck-lat-pulldown, behind-the-neck-pull-ups, close-grip-lat-pulldown, band-long-close-grip-lat-pulldown, lat-pulldown, band-long-lat-pulldown, muscle-ups, negative-pull-ups, neutral-grip-pull-ups, one-arm-lat-pulldown, band-long-one-arm-lat-pulldown, pause-pull-up, pull-up, ring-muscle-up, rope-climb, v-bar-lat-pulldown, weighted-pull-up, wide-grip-pull-ups (21) | back | biceps | vertical_pull | true |
| A10 | barbell-row, bench-pull, bent-over-db-row, bent-over-ez-bar-row, cable-bent-over-row, band-long-cable-bent-over-row, chest-supported-db-row, chest-supported-kettlebell-row, chest-supported-machine-row, chest-supported-smith-machine-row, double-kettlebell-row, dumbbell-bench-pull, ez-bar-reverse-grip-row, inverted-row, kneeling-cable-row, band-long-kneeling-cable-row, one-arm-kettlebell-row, one-arm-seated-cable-row, band-long-one-arm-seated-cable-row, pendlay-row, renegade-row, reverse-grip-bent-over-row, ring-row, rings-inverted-row, seated-cable-row, band-long-seated-cable-row, single-arm-chest-supported-dumbbell-row, single-arm-db-row, sled-row, smith-machine-bent-over-row, smith-machine-reverse-grip-bent-over-row, t-bar-row, trx-row, wide-grip-seated-cable-row, band-long-wide-grip-seated-cable-row (35) | back | biceps | horizontal_pull | true |
| A11 | barbell-rear-delt-row, double-kettlebell-rear-delt-row (2) | shoulders | back, biceps | horizontal_pull | true |
| A12 | deadlift, deficit-deadlift, pause-deadlift, hex-bar-deadlift, dumbbell-deadlift, kettlebell-deadlift (6) | back | glutes, quads (hamstrings already present) | hinge | true |
| A13 | rack-pull, back-extension, machine-back-extension (3) | back | glutes (hamstrings already present) | hinge | true |
| A14 | cable-kickback, band-long-cable-kickback, glute-kickback (3) | glutes | hamstrings | isolation | true |

## B. Primary group changed — 11 rows

| Keys | Primary now -> correct | Secondary (final) | Pattern | counts |
|---|---|---|---|---|
| paused-incline-bench-press, smith-machine-incline-bench-press | shoulders -> **chest** | shoulders, triceps | horizontal_push | true |
| chin-ups, close-grip-pull-ups, commando-pull-up, reverse-grip-lat-pulldown, band-long-reverse-grip-lat-pulldown | biceps -> **back** | biceps | vertical_pull | true |
| one-arm-kettlebell-swing, one-arm-dumbbell-swing | full_body -> **glutes** (same as kettlebell-swing) | hamstrings | hinge | true |
| kettlebell-offset-reverse-lunge-and-press | full_body -> **quads** (07 knee-dominant rule) | glutes, shoulders | lunge | true |
| kettlebell-lunge-press | full_body -> **quads** (07 knee-dominant rule) | glutes, shoulders | vertical_push -> **lunge** | true |

## C. Movement pattern changed (not a main lift) — 23 rows

| Keys | Pattern now -> correct | Primary / secondary | counts |
|---|---|---|---|
| straight-arm-pulldown, band-long-straight-arm-pulldown, barbell-pullover, bent-arm-barbell-pullover, bent-arm-ez-bar-pullover, db-pullover, ez-bar-pullover, floor-kettlebell-pullover, kettlebell-pullover, plate-pullover | vertical_pull -> **isolation** | back / none | true |
| scapular-pull-ups | vertical_pull -> **isolation** | back / none | true |
| band-pull-apart | horizontal_pull -> **isolation** | shoulders / none | true |
| face-pull, band-long-face-pull, dumbbell-face-pull, ring-face-pull, trx-face-pull (PM 2026-11-04) | horizontal_pull -> **isolation** | shoulders / none | true |
| svend-press, db-svend-press, kettlebell-svend-press, single-db-svend-press | horizontal_push -> **isolation** | chest / none | true |
| jefferson-curl | hinge -> **other** | back / hamstrings (keep) | true |
| kettlebell-sumo-high-pull | vertical_pull -> **hinge** (07 "Keys, not names" already says hinge) | glutes / hamstrings | true |

## D. counts_as_strength false — 10 rows

pilates-roll-down (back), pilates-saw (back), pilates-spine-stretch-forward (back), pilates-leg-pull-back (back),
pilates-leg-pull-front (glutes), bird-dog (back), superman (back), pilates-spine-twist, pilates-roll-over,
pilates-side-bend (core). Groups and patterns unchanged. Add the keys to `NON_STRENGTH_KEYS` in the generator.
`pilates-kneeling-side-kick` (hip abduction, like fire-hydrant) keeps counting.

## E. Checked, no change (for the record)

- Lower body after T-211/T-213: consistent with 07 (61 knee-dominant, 18 straight-knee hinges, abduction, adduction, hip flexion).
- Lateral raises (8 incl. band-long) `shoulder_abduction`; leg-extension family quads isolation; leg curls hamstrings.
- Shrugs: back (trapezius maps to back). Upright rows: shoulders, no secondary, pattern kept. Deadlift primary stays back (PM 2026-11-04).
- `full_body` olympic lifts and complexes (clean, snatch, jerks, dead-clean, swing-snatch, swing-clean, dumbbell-snatch,
  dumbbell-clean-and-press, get-ups, windmills): no H1 primary, unchanged (no growth source).
- Non-counting rows with odd groups (isometric-neck-side and neck-side-stretch = back, tibialis raise = calves): no H1 effect.

## F. Custom exercises

Stored: one primary group, no secondaries (the form has none), optional pattern. Review: primary counts 1 if it is an H1
group, else 0; `full_body` 0; no inferred secondaries; no pattern = not a main lift; counting by metric type
(`countsAsStrength` in `routineChecker.ts` already does this).

## Suggested guards (pgTAP)

- every catalogue row with pattern horizontal_push or vertical_push and primary chest has shoulders in secondary;
- every catalogue row with pattern horizontal_pull or vertical_pull and primary back has biceps in secondary;
- no catalogue row with a main-lift pattern has primary biceps;
- each band-long row equals its cable source in muscle_group, secondary_muscles, movement_pattern.

Side effect to note in the PR: statistics "Series por grupo" (E8) shift (shoulders, triceps, biceps gain fractional sets).
