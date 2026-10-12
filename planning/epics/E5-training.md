# E5 · Training → `v0.5.0`

## Goal
Plan routines calmly and run them at the gym with a "sweat-proof" interface: quick-to-log sets, previous performance visible, screen always on and an automatic rest timer.

## Scope
- Includes: exercise catalog, routines (with supersets), live session, ghost data, timer, history.
- Out of scope: RPE, set types (warm-up/working), microcycles or weekly programs, cardio, public routine templates.

## Dependencies
E2. Independent of E3 and E4.

## Tables and RPC
`exercises`, `routines`, `routine_exercises`, `workout_sessions`, `workout_sets`, RPC `last_performance` (`03-data-model.md` §5–6).

## Reference screen
`design/stitch/live-workout/screen.png` (rest bar, session header, exercise cards with a set table). Without RPE, CAL/EFEC labels or "Fase / Microciclo".

## Workout tab navigation
- Main view: "Mis rutinas" (list) + "Empezar entreno vacío" button + access to "Historial".
- If there is an `in_progress` session, the tab opens the live session directly.
- "+ Registro" FAB → Bottom Sheet: "Empezar entreno" (choose a routine or empty) / "Nueva rutina".

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas). Exercise names are Spanish catalog data.

---

## US-18 · Exercise catalog
**As** a user
**I want** to pick exercises from a Spanish catalog and create the missing ones
**So that** I can build my routines without typing every exercise from scratch.

```gherkin
Feature: Exercise catalog

  Scenario: Search and filter
    Given the exercise picker
    When the user types "press" and filters by the muscle group "Pecho"
    Then they see "Press de banca con barra", "Press inclinado con mancuernas" and other chest exercises containing "press"

  Scenario: Accent-insensitive search
    When they type "dominadas" or "Dominádas"
    Then they get the same results

  Scenario: Creating a custom exercise
    When they don't find "Press Hammer unilateral" and tap "Crear ejercicio"
    And they choose the group "Pecho" and the equipment "Máquina"
    Then the exercise is created as custom and stays selected

  Scenario: Custom exercise privacy
    Given user A created "Press Hammer unilateral"
    When user B searches for "Hammer"
    Then they do not find it
```

**Technical notes**
- `exercises` migration + seed of ~80 exercises (Spanish name, group, equipment) + pgTAP.
- In-memory local search after loading the catalog once (it is small), with accent normalization.

---

## US-19 · Routine builder
**As** a user
**I want** to create routines with their exercises, order, supersets and targets
**So that** they are ready before I go to the gym.

```gherkin
Feature: Creating and editing routines

  Scenario: Creating a routine
    Given the user taps "Nueva rutina"
    When they name it "Torso fuerza" and add "Press de banca con barra" and "Dominadas"
    And they set 3 sets of 8 reps and 120 s rest for each
    And they tap "Guardar"
    Then the routine appears in "Mis rutinas" with 2 exercises

  Scenario: Reordering exercises
    Given a routine with exercises A, B and C
    When the user drags C to the first position
    Then the saved order is C, A, B

  Scenario: Grouping into a superset
    When the user selects "Agrupar con el siguiente" on "Press de banca con barra"
    Then "Press de banca con barra" and "Dominadas" are shown as "Superserie A1" and "A2"

  Scenario: Validation
    When they try to save a routine without a name or without exercises
    Then the "Guardar" button is disabled

  Scenario: Edit, duplicate and archive
    When the user edits, duplicates or archives a routine
    Then the changes are reflected in "Mis rutinas"
    And sessions already performed with that routine do not change
```

**Technical notes**
- `routines` + `routine_exercises` migration + RLS via parent + pgTAP.
- Drag and drop with a reorderable Compose list (multiplatform library or own implementation); accessible alternative with "Subir/Bajar" (move up/down) in the exercise menu.
- Transactional save of the routine and its exercises (RPC `save_routine(jsonb)` or delete+insert inside a SQL function) so routines are never left half-saved.

---

## US-20 · Live workout session
**As** a lifter at the gym
**I want** to log each set quickly while I train
**So that** my real performance is saved without interrupting the workout.

```gherkin
Feature: Live workout session

  Scenario: Starting from a routine
    Given the routine "Torso fuerza" with Press de banca (3 × 8) and Dominadas (3 × 8)
    When the user taps "Empezar" on that routine
    Then an in-progress session is created
    And the session screen is shown with the "EN VIVO" stopwatch, the "Pantalla activa" indicator and the "Finalizar" button
    And each exercise shows 3 set rows with the columns SERIE, ANTERIOR, KG, REPS and LISTO

  Scenario: Screen always on
    Given the session screen is visible
    Then the device does not turn the screen off due to inactivity
    When the user leaves the session screen
    Then the normal screen timeout behavior is restored

  Scenario: Completing a set
    When the user enters 60 kg and 12 reps in set 1 of Press de banca
    And taps the round LISTO checkbox
    Then the checkbox fills in neonLime with a check
    And the device vibrates briefly
    And the set is saved to the server immediately

  Scenario: Unchecking a set
    When the user taps the checkbox of a completed set again
    Then the set becomes pending again and can be edited

  Scenario: Adding sets and exercises
    When they tap "Añadir serie" on Press de banca
    Then set 4 appears with set 3's values as a placeholder
    When they tap "Añadir ejercicio" and choose "Press militar con mancuernas"
    Then it is added at the end of the session with one empty set

  Scenario: Continuing after closing the app
    Given an in-progress session with 2 completed sets
    When the user closes the app and opens it again
    Then the Workout tab opens the in-progress session with the 2 completed sets

  Scenario: Finishing and summary
    Given the user completed Press de banca 60 kg × 12, 87.5 kg × 8 and 87.5 kg × 8
    When they tap "Finalizar" and confirm in the Bottom Sheet
    Then the session becomes completed and the screen is no longer kept on
    And the summary shows the duration, the completed sets and the Press de banca volume "2.120 kg"
    And exercises with a new estimated 1RM record are marked as "PR"

  Scenario: Finishing with incomplete sets
    Given pending sets in the session
    When the user finishes
    Then the Bottom Sheet warns "Hay 3 series sin completar; no se guardarán"
    And on confirming only the completed sets are kept

  Scenario: Discarding the session
    When the user chooses "Descartar entreno" and confirms
    Then the session is marked as discarded and does not appear in the history

  Scenario: Network error when saving a set
    Given the device loses its connection
    When the user checks a set as LISTO
    Then the "Sin conexión" banner is shown and the set is marked as "pendiente de guardar"
    And it is saved automatically when the connection returns while the session is still open
```

**Technical notes**
- `keepScreenOn` as a `DisposableEffect` on the session screen (`View.keepScreenOn` / `FLAG_KEEP_SCREEN_ON`), never a CPU `WakeLock`.
- Every set change is persisted with an upsert by `(session_id, exercise_position, set_index)`; the retry queue lives only in the ViewModel's memory while the session is open (consistent with ADR-01).
- Inputs with a decimal numeric keyboard; the KG column supports 0.25 kg precision.
- Haptics through the `Haptics` interface (androidMain).

---

## US-21 · Previous performance (ghost data)
**As** a user pursuing progressive overload
**I want** to see on each set what I did last time
**So that** I know which weight and reps I need to beat.

```gherkin
Feature: Previous performance in the session

  Scenario: Showing the previous performance
    Given in the last completed session with "Press de banca con barra" set 2 was 85 kg × 8
    When the user looks at set 2 of that exercise in today's session
    Then the ANTERIOR column shows "85 kg × 8" in the ghostData color
    And the KG and REPS fields show 85 and 8 as a dim placeholder

  Scenario: No previous performance
    Given an exercise the user has never completed
    Then the ANTERIOR column shows "—"

  Scenario: More sets than last time
    Given last time they did 3 sets
    When they add a fourth set today
    Then the ANTERIOR column of set 4 shows "—"

  Scenario: Using the previous performance
    When the user taps the text "85 kg × 8" in the ANTERIOR column
    Then the KG and REPS fields are filled with 85 and 8
```

**Technical notes**
- `last_performance` RPC (one call per exercise when starting the session or adding an exercise) + pgTAP verifying it ignores other users' sessions and discarded or in-progress sessions.

---

## US-22 · Non-blocking rest timer
**As** an athlete training
**I want** an automatic rest timer that doesn't stop me from using the app
**So that** I respect my rest periods while I review or edit other exercises.

```gherkin
Feature: Rest timer

  Scenario: Automatic start
    Given "Press de banca con barra" has 120 s rest in the routine
    When the user checks a set as LISTO
    Then the rest bar appears pinned below the Top App Bar showing "02:00"
    And a decreasing progress line in athleticCyan
    And the "+30s" and "Omitir" buttons

  Scenario: Quick adjustments
    Given the timer shows "01:12"
    When the user taps "+30s"
    Then it changes to "01:42"
    When they tap "Omitir"
    Then the bar disappears

  Scenario: Free navigation
    Given the timer is running
    When the user scrolls or edits another set
    Then the bar stays visible without covering the set rows or the controls

  Scenario: End of rest
    When the timer reaches "00:00"
    Then the device vibrates and plays a short alert
    And the bar disappears

  Scenario: Accuracy with the app in the background
    Given 60 s of rest remain
    When the user leaves the app for 30 s and comes back
    Then the timer shows "00:30"

  Scenario: Superset
    Given the superset Press de banca (A1) and Dominadas (A2)
    When the user completes a set of A1
    Then the timer does not start
    When they complete the corresponding set of A2
    Then the timer starts with the superset's rest

  Scenario: Rest configurable during the session
    When the user changes an exercise's rest to 90 s from its menu
    Then the following sets of that exercise use 90 s
```

**Technical notes**
- State based on `endsAt` (an instant) and an injectable `Clock` for tests (`04-business-rules.md` §4.4). ViewModel tests with virtual time (`runTest` + `advanceTimeBy`).
- The alert at 0 only fires with the app visible in Phase 1 (no foreground service or notifications).

---

## US-23 · Session history
**As** a user
**I want** to browse my previous workouts
**So that** I can review what I did and fix mistakes.

```gherkin
Feature: Workout history

  Scenario: Session list
    Given the user has completed sessions
    When they open "Historial" on the Workout tab
    Then they see the sessions from newest to oldest with date, name, duration, total volume and number of sets

  Scenario: Session detail
    When they tap a session
    Then they see each exercise with its sets (kg × reps) and the PRs achieved

  Scenario: Correcting a set
    When in the detail they edit a set from 87.5 kg × 8 to 87.5 kg × 7
    Then the change is saved and the session volume is recalculated

  Scenario: Deleting a session
    When they delete a session and confirm
    Then it disappears from the history and is no longer used as previous performance

  Scenario: Pagination
    Given more than 20 sessions
    When the user reaches the end of the list
    Then the next 20 are loaded
```

---

## Epic Definition of Done
- A full workout (4-exercise routine with a superset) is logged without screen locks, with correct ghost data and rest periods.
- pgTAP for the 5 tables and for `last_performance`.
- Maestro regression green (full session, timer with short durations configured for testing).
- `prodDebug` APK validated during a real workout by the user. Tag `v0.5.0`.
