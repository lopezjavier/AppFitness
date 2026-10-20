# E6 · Training depth → `v0.6.0`

## Goal
Turn the Phase 1 workout into a complete gym experience: typed sets and RPE, every exercise metric (duration, distance, bodyweight…), warm-up and plate calculators, the full personal-record catalogue, and routine management (folders, tracking, "Actualizar rutina").

## Scope
- Includes: per-set routine targets (weight, reps and set type per planned set, moved from E3 by the user, 2026-10-19; first task of the epic), exercise detail (history and PR tabs), set types, RPE, per-exercise metric types, volume options, warm-up/plate calculators, PR catalogue and live PR, routine folders and next workout, routine tracking and "Actualizar rutina", workout detail/calendar and editing, session controls.
- Out of scope: advanced statistics and reports (E7); the algorithmic program generator and trainer; social features, cardio plans and publishing/monetization limits (all out of Phase 1, `01-phase1-scope.md` §4).

## Dependencies
E3 (training tables, live session, catalog assets and the forward-compatible model, ADR-30).

## Tables and RPC
The *(E6)* enums and columns added in `03-data-model.md` §5 (now used, not migrated) and the new `routine_folders` table. New RPCs: the PR catalogue, the warm-up/plate calculators and `Update Routine` (`03-data-model.md` §6).

## Reference screen
`design/stitch/live-workout/screen.png` (set-type badges, RPE) and the routine-editor mockups. The exercise detail follows the catalog card style.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas). Exercise names are Spanish catalog data.

---

## US-28 · Exercise detail (history and personal records)
**As** a user
**I want** a full detail for each exercise
**So that** I can review how I have performed it and what my records are.

```gherkin
Feature: Exercise detail

  Scenario: Tabs
    When the user opens the detail of "Press de banca con barra"
    Then they see the tabs "Resumen", "Historial" and "Cómo hacerlo"

  Scenario: History
    When they open "Historial"
    Then they see the past sessions with that exercise, from newest to oldest, with their sets

  Scenario: Records
    When they open "Resumen"
    Then they see the best marks ("Récord de peso", "Mejor serie", "1RM estimado", "Volumen máximo")

  Scenario: No history
    Given an exercise the user has never logged
    Then "Historial" shows the empty state
```

**Technical notes**
- Reuses the catalog assets (thumbnail + start↔peak animation) and the PR catalogue (US-34).
- The "Cómo hacerlo" tab shows the Spanish instructions from `library.json`.

---

## US-29 · Set types (warm-up, failure, drop)
**As** a lifter
**I want** to classify my sets
**So that** warm-up sets do not distort my volume and records.

```gherkin
Feature: Set types

  Scenario: Assigning a type
    Given a set of "Press de banca con barra"
    When the user chooses the type "Calentamiento"
    Then the set shows the "C" badge

  Scenario: Planning sets in a routine
    Given a routine exercise being edited
    When the user adds sets with their own weight, repetitions and type ("Calentamiento", "Normal", "Al fallo", "Drop")
    Then each planned set is saved with its target and the live session starts from them

  Scenario: Removing a type
    When the user chooses "Quitar tipo"
    Then the set goes back to "Normal"

  Scenario: Warm-up excluded by default
    Given "Calentamiento" sets in a session
    Then the volume, 1RM and PRs ignore them
    And the total sets follow the "Incluir calentamiento" setting

  Scenario: Toggling inclusion
    When the user turns on "Incluir calentamiento"
    Then the past statistics and PRs are recalculated

  Scenario: Routine asks only sets and rest (user request 2026-10-20)
    When the user adds an exercise in the routine builder
    Then they configure only the number of sets and the rest
    And the reps are entered in the live session
```


**Technical notes**
- `workout_sets.set_type` and `04-business-rules.md` §4.6; the pgTAP covers the recalculation on toggle.

---

## US-30 · RPE per set
**As** a lifter
**I want** to log the RPE of each set
**So that** I can track effort, not only load.

```gherkin
Feature: RPE

  Scenario: Logging the RPE
    When the user taps the RPE cell of a set
    Then they pick a value from 6 to 10 in half points ("7,5")
    And the cell shows "7,5"

  Scenario: Previous RPE
    Given the previous session logged RPE 8 for that set
    Then the ghost shows "RPE 8"

  Scenario: Target RPE
    Given a planned exercise with a target RPE
    Then the set shows "Objetivo RPE 8"
```

**Technical notes**
- `workout_sets.rpe` and `routine_exercises.target_rpe` with a 1–10 check.

---

## US-31 · Exercise metric types
**As** a user
**I want** to log exercises that are not weight × reps
**So that** bodyweight, duration, distance and machine exercises are all tracked.

```gherkin
Feature: Exercise metrics

  Scenario: Bodyweight exercise
    Given "Dominadas" (bodyweight)
    Then the set row shows REPS and an added-weight field
    And the volume uses the latest body weight

  Scenario: Duration exercise
    Given "Plancha" (duration)
    Then the set row shows a stopwatch and the duration in mm:ss

  Scenario: Distance exercise
    Given "Cinta de correr" (distance and duration)
    Then the set row shows distance and duration

  Scenario: Per-equipment weight hint
    Given a barbell exercise
    Then the weight field hints "barra + discos"
```

**Technical notes**
- `exercises.metric_type` and the per-metric columns in `workout_sets`; the set row is already polymorphic (ADR-30), E6 implements the variants.

---

## US-32 · Volume options
**As** a user
**I want** the volume to count correctly for dumbbells and bodyweight
**So that** my numbers are realistic.

```gherkin
Feature: Volume options

  Scenario: Double dumbbell
    When the user enables "Contar mancuerna doble" for a dumbbell exercise
    Then the volume doubles the logged weight

  Scenario: Bodyweight update
    When the user logs a bodyweight exercise
    Then they can update their body weight, which is used for the volume
```

**Technical notes**
- `04-business-rules.md` §4.7.

---

## US-33 · Warm-up and plate calculators
**As** a lifter
**I want** help choosing warm-up sets and plates
**So that** I do not calculate them by hand.

```gherkin
Feature: Calculators

  Scenario: Warm-up calculator
    Given a working weight of 100 kg
    When the user opens the warm-up calculator
    Then it proposes a block of warm-up sets as percentages, rounded to the available plates

  Scenario: Plate calculator
    Given a barbell exercise
    When the user enters a target weight
    Then it shows the plates needed per side for the selected bar

  Scenario: Rounding settings
    When the user sets the plate and dumbbell rounding
    Then the calculators use those values
```

**Technical notes**
- Warm-up inserts `set_type = warm_up` sets (US-29); plate defaults from `04-business-rules.md` and the user settings.

---

## US-34 · Personal-record catalogue and live PR
**As** a user pursuing progress
**I want** the app to detect and announce my records
**So that** I know when I beat my best.

```gherkin
Feature: Personal records

  Scenario: Record types
    Given the user's history for "Sentadilla"
    Then the app tracks "Récord de peso", "Mejor serie", "Volumen máximo", "1RM estimado" and "Total de repes"

  Scenario: Live PR
    When a completed set beats the current best of that exercise
    Then a "¡Nuevo récord!" notification appears
    And the strength of the notification follows the user's setting

  Scenario: Duration/distance family
    Given a cardio-style exercise
    Then the records include "Mejor tiempo", "Mayor distancia", "Más escalones"
```

**Technical notes**
- `04-business-rules.md` §4.8; the PR RPC (`03-data-model.md` §6). PRs respect the warm-up setting (US-29).

---

## US-35 · Routine folders and next workout
**As** a user with several routines
**I want** to organise them
**So that** I find them quickly.

```gherkin
Feature: Folders and next workout

  Scenario: Creating a folder
    When the user creates "Push/Pull/Legs" and moves routines into it
    Then "Mis rutinas" groups them by folder

  Scenario: Deleting a folder
    When the user deletes a folder and confirms
    Then its routines move to "Mis rutinas" and none is deleted

  Scenario: Next workout
    When the user sets "Torso fuerza" as the next workout
    Then it is highlighted in "Mis rutinas"

  Scenario: Ordering routines manually (user request 2026-10-20)
    When the user drags a routine to another position in "Mis rutinas" (or inside a folder)
    Then the order is saved and kept after leaving the app
    And a new or duplicated routine is added at the end
    And until the user reorders, routines keep the A→Z order of E3
```

**Technical notes**
- `routine_folders` and `routines.folder_id`/`is_next` (`03-data-model.md` §5).
- Manual order needs a new `routines.position` column (new migration + pgTAP, an exception to the forward-compatible schema of ADR-30); drag-to-reorder reuses the routine-builder pattern. Replaces the A→Z sort of T-051.

---

## US-36 · Routine tracking and "Actualizar rutina"
**As** a user who trains to a routine
**I want** the app to update the routine from a session when I change it
**So that** the plan reflects what I actually do.

```gherkin
Feature: Routine tracking

  Scenario: Same values
    Given a session whose values match the routine
    When the user finishes
    Then the routine is left unchanged

  Scenario: Changed values
    Given the user added a set and changed a rest in the session
    When they finish
    Then a sheet offers "Actualizar rutina" or "Mantener rutina original"
    And "Actualizar" replaces the routine values (sets, inputs, rests, supersets)

  Scenario: Auto update
    Given "Actualizar automáticamente" is on
    Then the routine is updated without asking
```

**Technical notes**
- `routines.track_as_routine`/`auto_update_routine`; the `Update Routine` RPC (`03-data-model.md` §6), transactional.

---

## US-37 · Workout detail, calendar and editing
**As** a user
**I want** to review and manage a finished workout
**So that** I can fix it or reuse it.

```gherkin
Feature: Workout detail

  Scenario: Detail
    When the user opens a finished workout
    Then they see its exercises, sets, muscle split and PRs

  Scenario: Calendar
    When they open the calendar
    Then they see the trained days, the streak and the rest days (month, year, multi-year)

  Scenario: Editing
    When they edit, copy or delete a workout
    Then the change is reflected

  Scenario: Save as routine
    When they tap "Guardar como rutina"
    Then a new routine is created from the workout

  Scenario: Logging a past workout (user request 2026-10-20)
    When the user creates a workout for a past date with exercises, weights and reps
    Then it appears in the history, the calendar and the metrics on that date
    And it never goes through the live session
```

**Technical notes**
- `workout_sessions.description`/`gym` are editable; delete uses the existing cascade; "save as routine" creates `routines` + `routine_exercises`.

---

## US-38 · Session controls
**As** a lifter
**I want** to control the session while I train
**So that** it adapts to the gym.

```gherkin
Feature: Session controls

  Scenario: Pause and resume
    When the user pauses the workout
    Then the duration stops counting
    And resuming continues from there

  Scenario: Finishing an empty workout (user request 2026-10-20)
    Given no set of any exercise is completed
    Then "Finalizar entreno" is disabled
    And it is enabled once one set is completed

  Scenario: Deleting a set
    When the user swipes a set to delete it
    Then it is removed with an "Deshacer" option

  Scenario: Previous-values mode
    When the user chooses "Última vez en esta rutina"
    Then the ghost shows the last time within the routine instead of the last overall
```

**Technical notes**
- `workout_sessions.paused_at`; the delete is local with an undo window; the ghost mode extends US-12.

---

## Epic Definition of Done
- The full `04-business-rules.md` §4 (set types, volume, PR families) is covered by unit and pgTAP tests.
- The set row covers every `exercise_metric_type` used by the seeded catalog.
- A real workout uses set types, RPE, a calculator and routine update without a migration (ADR-30).
- `prodDebug` APK validated by the user. Tag `v0.6.0`.
