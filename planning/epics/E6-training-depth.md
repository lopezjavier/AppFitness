# E6 · Training depth → `v0.6.0`

## Goal
Turn the Phase 1 workout into a complete gym experience: typed sets and RPE, every exercise metric (duration, distance, bodyweight…), warm-up and plate calculators, the full personal-record catalogue, and routine management (folders, tracking, "Actualizar rutina"). Also editing manual foods (US-47).

## Scope
- Includes: per-set routine targets (weight, reps and set type per planned set, moved from E3 by the user, 2026-10-19; first task of the epic; ADR-50), exercise detail (history and PR tabs), set types, RPE, per-exercise metric types, volume options, warm-up/plate calculators, PR catalogue, routine folders and next workout, routine tracking and "Actualizar rutina", workout detail/calendar and editing, past workouts, session controls, and **food editing** (US-47; user request 2026-10-22; ADR-53): edit manual foods and their portions, carry the full label nutrient list, and show nutrients in label order.
- Out of scope: advanced statistics and reports (E7); the algorithmic program generator and trainer; social features, cardio plans and publishing/monetization limits (all out of Phase 1, `01-phase1-scope.md` §4).
- Deferred by the user at the E6 breakdown (2026-10-22, ADR-52; scenario text kept in `01-phase1-scope.md` §4.1): the live "¡Nuevo récord!" notification and its strength levels; the streak and rest days in the training calendar; copying a workout. Editing Open Food Facts foods is not allowed (ADR-53).

## Dependencies
E3 (training tables, live session, catalog assets and the forward-compatible model, ADR-30).

## Tables and RPC
The *(E6)* enums and columns added in `03-data-model.md` §5 (now used, not migrated) and the new `routine_folders` table. Schema exceptions (planned sets, training preferences, `routines.position`…) are listed in `tasks/plan.md` and recorded in an ADR by T-089. New RPCs: the PR catalogue, `Update Routine`, folders/order, workout editing and past workouts, and the manual-food edit (`03-data-model.md` §6). The warm-up/plate calculators are client-side domain (their settings are stored).

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

  Scenario: Planned targets in the live session
    Given a routine whose "Press de banca con barra" set 2 is planned as "Normal" 80 kg × 8
    When the user starts the routine
    Then set 2 shows 80 and 8 as a dim placeholder in KG and REPS, not as values
    And the set keeps its planned type badge
    And the ANTERIOR column still shows the previous performance

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
```

**Technical notes**
- `workout_sets.set_type` and `04-business-rules.md` §4.6; the pgTAP covers the recalculation on toggle.
- Per-set planning holds (ADR-50). The earlier scenario "Routine asks only sets and rest" (user request 2026-10-20) contradicted it and was removed by the user on 2026-10-22.
- Planned weight and reps are placeholders in the live session (same pattern as the ghost data, `04-business-rules.md` §4.3); the planned type is applied to the pending set as its value. When a set has no planned weight, the ghost placeholder of E3 applies.

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
- The target RPE is set **per exercise** in the routine (`routine_exercises.target_rpe`, already in the forward-compatible schema) and shown on every working set of that exercise (ADR-50).

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

  Scenario: Default warm-up scheme
    Given a working weight of 100 kg, the 20 kg bar and the default plates
    When the user opens the warm-up calculator
    Then it proposes 20 kg × 10, 40 kg × 5, 60 kg × 3 and 80 kg × 2

  Scenario: Default plates
    Given a target of 100 kg with the 20 kg bar
    Then it shows "25 + 15" per side
```

**Technical notes**
- Warm-up inserts `set_type = warm_up` sets (US-29); defaults in `04-business-rules.md` §4.9 (user decision 2026-10-22, ADR-52) and the user settings.

---

## US-34 · Personal-record catalogue
**As** a user pursuing progress
**I want** the app to detect my records
**So that** I know when I beat my best.

```gherkin
Feature: Personal records

  Scenario: Record types
    Given the user's history for "Sentadilla"
    Then the app tracks "Récord de peso", "Mejor serie", "Volumen máximo", "1RM estimado" and "Total de repes"

  Scenario: Records in the summary and the history
    When a finished session beats a record of an exercise
    Then the session summary and the workout detail mark that record

  Scenario: Duration/distance family
    Given a cardio-style exercise
    Then the records include "Mejor tiempo", "Mayor distancia", "Más escalones"
```

**Technical notes**
- `04-business-rules.md` §4.8; the PR RPC (`03-data-model.md` §6). PRs respect the warm-up setting (US-29).
- "Total de repes" is the all-time total of completed reps of the exercise (ADR-52).
- The scenario "Live PR" (a "¡Nuevo récord!" notification during the session, with an off/low/normal/high strength setting) is deferred (ADR-52; text kept in `01-phase1-scope.md` §4.1).

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
    Given the folder "Push/Pull/Legs" holds 3 routines
    When the user deletes the folder
    Then a confirmation says the folder and its 3 routines will be deleted
    And on confirming, the folder and its 3 routines are deleted
    And the past workouts of those routines stay in the history

  Scenario: Cancelling a folder deletion
    When the user deletes a folder and cancels the confirmation
    Then nothing is deleted

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
- Deleting a folder deletes its routines (user decision 2026-10-22, ADR-51). It is the one training delete with a confirmation dialog, an explicit exception to ADR-45 because it removes many routines at once; the dialog names how many routines will be deleted (wording by ux-reviewer, e.g. "Se eliminarán la carpeta y sus 3 rutinas"). Past sessions keep their data (`workout_sessions.routine_id` is set null, as for a single routine delete). Assumption: an empty folder is deleted at once without the dialog (ADR-45), confirmed at the checkpoint. This supersedes the earlier "its routines move to Mis rutinas".

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
    Then they see the trained days (month, year, multi-year)
    And tapping a trained day opens its workout

  Scenario: Editing
    When they edit a workout and tap "Guardar"
    Then the change is reflected in the history, the calendar and the metrics

  Scenario: Deleting
    When they delete a workout
    Then it disappears from the history, the calendar and the metrics

  Scenario: Save as routine
    When they tap "Guardar como rutina"
    Then a new routine is created from the workout

  Scenario: Logging a past workout (user request 2026-10-20)
    When the user creates a workout for a past date with exercises, weights and reps
    Then it appears in the history, the calendar and the metrics on that date
    And it never goes through the live session

  Scenario: Starting a past workout from a routine or a finished workout
    When the user creates a past workout from a routine or from a finished workout
    Then the form starts with its exercises and sets, which the user can change before "Guardar"
```

**Technical notes**
- `workout_sessions.description`/`gym` are editable; delete uses the existing cascade; "save as routine" creates `routines` + `routine_exercises` + planned sets (ADR-50).
- A past workout starts empty, from a routine or from a finished workout used as a template; it is saved as a `completed` session on the chosen date and never creates an `in_progress` row. Routines can already be duplicated (E3).
- Deferred (ADR-52, text in `01-phase1-scope.md` §4.1): the streak and rest days in the calendar, and copying a workout.

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
    When the user swipes set 2 of 4 to delete it
    Then it is removed at once, with no "Deshacer"
    And sets 3 and 4 become sets 2 and 3

  Scenario: Previous-values mode
    When the user chooses "Última vez en esta rutina"
    Then the ghost shows the last time within the routine instead of the last overall
```

**Technical notes**
- `workout_sessions.paused_at`; the set delete follows ADR-45 (immediate, no undo; user decision 2026-10-22) through `SwipeToDeleteRow`; the ghost mode extends US-12.

---

## US-47 · Editing manual foods
**As** a user who creates their own foods
**I want** to edit a manual food, its portions and every value of its label
**So that** my foods match the Spanish nutrition label without creating them again.

```gherkin
Feature: Editing manual foods

  Scenario: Editing a manual food
    Given a manual food "Yogur casero" with 60 kcal per 100 g
    And a diary entry of it yesterday
    When the user changes it to 70 kcal and taps "Guardar"
    Then new diary entries use 70 kcal per 100 g
    And yesterday's entry keeps its values

  Scenario: Open Food Facts foods are not editable
    Given a food that comes from Open Food Facts
    When the user opens its details
    Then there is no edit action

  Scenario: Adding a portion
    When the user adds the portion "1 tarrina" = 125 g to a manual food and taps "Guardar"
    Then it can be chosen when logging the food

  Scenario: Editing a portion
    Given the portion "1 tarrina" = 125 g was used in past entries
    When the user changes it to 120 g and taps "Guardar"
    Then new entries with "1 tarrina" use 120 g
    And past entries keep their amount

  Scenario: Removing a portion
    When the user removes a portion of a manual food and taps "Guardar"
    Then it is no longer offered when logging
    And past entries logged with it keep their portion and amount

  Scenario: Full label
    When the user creates or edits a manual food
    Then they can enter every value of a Spanish nutrition label: energy, fat, saturates, carbohydrates, sugars, fibre, protein, salt, and vitamins and minerals
    And the fields and the food details follow the label order

  Scenario: Label order for every food
    When the user opens the details of any food (manual or Open Food Facts)
    Then the nutrients are listed in the label order and absent values are hidden
```

**Technical notes**
- ADR-53. Only `source = 'user'` foods owned by the caller are editable (the existing `foods` RLS already restricts writes to them). Edits apply to **future** entries only: past `food_log_entries` keep their snapshots (ADR-32), including `food_name` and `portion_label`.
- Removing a portion is allowed even when past entries used it: entries and comida items store the resolved amount and the label as a snapshot and have no link to the portion row, so nothing changes for them (user rule of 2026-10-22 dropped as pointless under the snapshots; see ADR-53). Editing an old entry whose portion no longer exists falls back to the amount in base units.
- Manual foods accept the full Open Food Facts nutrient list in `foods.micronutrients` (OFF keys, values ≥ 0, a known-keys whitelist; amends ADR-43, which allowed four keys). Label order follows EU Regulation 1169/2011 Annex XV: energy (kJ / kcal), fat, saturates, mono-unsaturates, polyunsaturates, carbohydrates, sugars, polyols, starch, fibre, protein, salt, then vitamins and minerals in the Annex XIII order. The base unit (`g`/`ml`) is not editable (assumption: changing it would invalidate the portions).
- A new edit RPC (e.g. `update_personal_food(p_food_id, …, p_portions jsonb, p_micronutrients jsonb)`, atomic with the portions) and `create_personal_food` accepting the full key list; `03-data-model.md` §3/§6.

---

## Epic Definition of Done
- The full `04-business-rules.md` §4 (set types, volume, PR families) is covered by unit and pgTAP tests.
- The set row covers every `exercise_metric_type` used by the seeded catalog.
- A real workout uses set types, RPE, a calculator and routine update without a migration (ADR-30).
- `prodDebug` APK validated by the user. Tag `v0.6.0`.
