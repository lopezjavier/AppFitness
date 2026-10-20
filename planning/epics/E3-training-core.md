# E3 · Training core → `v0.3.0`

## Goal
Plan routines calmly and run them at the gym with a "sweat-proof" interface: quick-to-log sets, previous performance visible, screen always on and an automatic rest timer. Then see the basic strength progress.

## Scope
- Includes: exercise catalog (with the RepDB asset bundle), a lightweight exercise detail, routines (with supersets), live session, ghost data, timer, history, and strength progress charts.
- Out of scope: RPE, set types (warm-up/working), per-exercise metric types beyond weight × reps, warm-up/plate calculators, microcycles or weekly programs, cardio, public routine templates, advanced statistics. These arrive in **E6/E7** (`07-roadmap.md`).
- The training tables ship **forward-compatible** (ADR-30): E3 renders weight × reps and `working` sets, but the model already carries the types and columns marked *(E6)* in `03-data-model.md` §5 and the live set row is built **polymorphic**, so E6 adds features without a migration.

## Dependencies
E2. Independent of E4 and E5.

## Tables and RPC
`exercises`, `routines`, `routine_exercises`, `workout_sessions`, `workout_sets`, RPCs `last_performance`, `exercise_history` and `weekly_muscle_volume` (`03-data-model.md` §5–6).

## Reference screen
`design/stitch/live-workout/screen.png` (rest bar, session header, exercise cards with a set table) and the "Fuerza y 1RM estimado" sections of `design/stitch/progress/screen.png`. Without RPE, CAL/EFEC labels or "Fase / Microciclo".

## Statistics placement (ADR-37)
- There is no Progreso tab. The "Fuerza y 1RM estimado" summary card lives on a full-screen "Fuerza" destination (back arrow) reached from a statistics entry button on the Perfil root (the weight screen joins the Perfil entries in E5).
- Per-exercise detail screen reachable from that card and from an exercise's detail in Workout/History.

## Workout tab navigation
- Main view: "Mis rutinas" (list sorted by name ascending, case- and accent-insensitive) + "Nueva rutina" + access to "Historial". There is no empty workout: every session starts from a routine (user feedback 2026-10-20, T-051).
- If there is an `in_progress` session, the tab opens the live session directly; on "Mis rutinas" the "Tienes un entreno en curso" card offers continue (play) and discard (trash) (T-052).

## App state restoration (user feedback 2026-10-20, T-050, ADR-40)
Applies to the whole app, not only E3.

```gherkin
Feature: App state restoration

  Scenario: Returning to the same tab and screen
    Given the user is on a sub-screen of a tab (e.g. Entreno → "Historial" → a session)
    When they leave the app and come back
    Then the same tab and the same screen are shown

  Scenario: Keeping a draft
    Given the user has typed a name and added 2 exercises in "Nueva rutina" without saving
    When they leave the app and come back
    Then the draft is shown exactly as they left it and is still unsaved

  Scenario: Restoring after process death
    Given the system closed the app process while it was in the background
    When the user opens the app again
    Then the selected tab, its screen stack and any unsaved draft are restored
    And a signed-out user is still sent to sign-in
```

## Exercise assets (RepDB free)
- The catalog ships the **RepDB free** set (643 exercises): `start`/`peak` poses (the thumbnail is the `start` pose) and Spanish instructions, in `composeApp/src/commonMain/composeResources/files/exercises/` (`library.json` generated from `free.es.json`, ≈16 MB; ADR-39). Catalog text and images live in the client, never in the DB or a CDN (ADR-30).
- The asset bundle is **provided by the user** (not committed to the repository): it is a manual prerequisite of the build. run `python scripts/import-repdb.py <bundle>` before delivering an APK (CI and unit tests build without it); the project's `.gitignore` excludes it so the RepDB assets are not redistributed in the repo.
- The bundle has no male/female variants. The animation is a **start↔peak crossfade** (3 s cycle, ease-in-out, a brief pause on each pose); the free tier has no GIF/video.
- Visible attribution "Datos de ejercicios por RepDB (repdb.co)".
- `exercises` gains `metric_type` (default `weight_reps`) and each catalog row carries a `key` that links to `library.json`; custom exercises have no media.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas). Exercise names are Spanish catalog data.

---

## US-09 · Exercise catalog
**As** a user
**I want** to pick exercises from a Spanish catalog and create the missing ones
**So that** I can build my routines without typing every exercise from scratch.

```gherkin
Feature: Exercise catalog

  Scenario: Search and filter
    Given the exercise picker
    When the user types "press" and filters by the muscle group "Pecho"
    Then they see "Press de banca con barra", "Press inclinado con mancuernas" and other chest exercises containing "press"

  Scenario: Filter by equipment
    When the user filters by the equipment "Mancuerna"
    Then only dumbbell exercises are shown

  Scenario: Accent-insensitive search
    When they type "dominadas" or "Dominádas"
    Then they get the same results

  Scenario: Catalog row with thumbnail
    Given the exercise picker
    Then each exercise row shows its thumbnail (the start pose)
    And an exercise without media (custom) or whose image fails to load shows the placeholder

  Scenario: Exercise detail with animation and how-to
    When the user taps "Press de banca con barra"
    Then the detail shows the start↔peak animation and the "Cómo hacerlo" instructions in Spanish

  Scenario: Attribution
    Given the exercise catalog
    Then the attribution "Datos de ejercicios por RepDB (repdb.co)" is visible

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
- `exercises` migration + a generated catalog migration (`key`, `muscle_group`, `equipment`, `metric_type`; no name, ADR-38/ADR-39) + pgTAP. Names, instructions and images are **not** in the DB: they live in `library.json` in the client (ADR-30).
- Assets bundled in `composeApp/src/commonMain/composeResources/files/exercises/` (`start`/`peak` poses), ≈16 MB; the animation is a 3 s start↔peak crossfade.
- In-memory local search after loading the catalog once (it is small), with accent normalization.
- The `key` links a catalog row to `library.json`; custom exercises have no `key` and no media.

---

## US-10 · Routine builder
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

  Scenario: Routines sorted by name
    Given the routines "Torso fuerza", "brazos" and "Abdominales"
    When the user opens "Mis rutinas"
    Then they are listed as "Abdominales", "brazos", "Torso fuerza"

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
- Columns for E6 (`folder_id`, `pinned_note`, `track_as_routine`, `auto_update_routine`, `is_next`, `target_reps_max`, `target_rpe`, `planned_set_type`, `target_weight_kg`) ship already (ADR-30); E3 does not render them.

---

## US-11 · Live workout session
**As** a lifter at the gym
**I want** to log each set quickly while I train
**So that** my real performance is saved without interrupting the workout.

```gherkin
Feature: Live workout session

  Scenario: Starting from a routine
    Given the routine "Torso fuerza" with Press de banca (3 × 8) and Dominadas (3 × 8)
    When the user taps "Empezar" on that routine
    Then an in-progress session is created
    And the session screen is shown with the "EN VIVO" stopwatch (the screen stays on while it is open, with no on-screen indicator)
    And its ⋮ menu offers "Finalizar entreno" above "Descartar entreno" (there is no fixed bottom button)
    And each exercise shows 3 set rows with the columns SERIE, ANTERIOR, KG, REPS and LISTO

  Scenario: Starting while a session is in progress
    Given a session is in progress
    When the user taps "Empezar" on another routine
    Then the message "Ya hay un entreno en curso" is shown
    And no new session is created

  Scenario: Resuming or discarding from "Mis rutinas"
    Given a session is in progress
    When the user opens "Mis rutinas"
    Then the "Tienes un entreno en curso" card shows a play button and a trash button
    When they tap play
    Then the live session opens
    When they tap trash and confirm
    Then the session is marked as discarded and the card disappears

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
    When they choose "Finalizar entreno" in the ⋮ menu and confirm in the Bottom Sheet
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
- `workout_sets` already carries `set_type`, `rpe` and the per-metric columns, and `workout_sessions` carries `description`, `gym` and `paused_at` (ADR-30); E3 writes only `weight_kg`, `reps` and `set_type = working`. The set row is built **polymorphic** so E6 extends it without a rewrite.

---

## US-12 · Previous performance (ghost data)
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
- E6 adds the "last time within the routine" mode; E3 always uses the last completed session regardless of routine.

---

## US-13 · Non-blocking rest timer
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
    Then the device vibrates and plays an alert sound of about 1 s (only with the app in the foreground)
    And the bar disappears

  Scenario: Accuracy with the app in the background
    Given 60 s of rest remain
    When the user leaves the app for 30 s and comes back
    Then the timer shows "00:30"

  Scenario: Superset
    Given the superset Press de banca (A1, rest 0 s) and Dominadas (A2)
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

## US-14 · Session history
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

**Technical notes**
- Pagination by `started_at desc` (`limit`/`offset`). E6 adds the workout detail with muscle split, the calendar view and edit/copy/delete/save-as-routine.

---

## US-15 · Strength progress charts
**As** a user aiming to get stronger
**I want** to see how my estimated 1RM and my volume evolve
**So that** I can measure my real progress for each exercise and muscle group.

```gherkin
Feature: Strength analytics

  Scenario: Summary card on the strength screen
    Given the user has trained "Press de banca con barra", "Sentadilla" and "Peso muerto" in the last 6 weeks
    When they open "Fuerza" from the "Perfil" tab
    Then the "Fuerza y 1RM estimado" card shows chips with their 3 most frequent exercises
    And "Press de banca con barra" is selected by default

  Scenario: Estimated 1RM record
    Given their best historical Press de banca set is 100 kg × 4
    When they select the "Press de banca con barra" chip
    Then "Récord 1RM estimado: 113,5 kg" is shown
    And if their best 1RM 30 days ago was 108.5 kg, "+5,0 kg este mes" is shown in green

  Scenario: Exercise chart
    When they open the "Press de banca con barra" detail
    Then they see a chart of the best estimated 1RM per session
    And a chart of the total volume per session
    And they can filter by 1M, 3M, 6M and 1A

  Scenario: Weekly volume per muscle group
    When they open "Volumen por grupo muscular"
    Then they see the total volume per muscle group for each of the last 8 weeks
    And the first week shown is never earlier than the week of their first completed session
    And they can select a group to see only its evolution

  Scenario: Exercise without enough data
    Given an exercise with a single session
    Then the detail shows that session's value and the message "Necesitas al menos 2 sesiones para ver la evolución"

  Scenario: Exercises without load
    Given "Dominadas" logged with 0 kg
    Then the detail shows the evolution of max reps instead of 1RM and volume
```

**Technical notes**
- `exercise_history` and `weekly_muscle_volume` RPCs + pgTAP with the examples from `04-business-rules.md` §4 (same values as the `EstimateOneRepMax` domain tests).
- Vico charts in athleticCyan (training domain). E7 extends these charts with record markers and the non-weight metric families (US-39).

---

## Epic Definition of Done
- A full workout (4-exercise routine with a superset) is logged without screen locks, with correct ghost data and rest periods.
- pgTAP for the 5 tables and for `last_performance`, `exercise_history` and `weekly_muscle_volume`.
- Host (Robolectric) tests cover a full session (timer with short durations configured for testing).
- The catalog loads from the in-app assets: rows show the thumbnail (start pose), the exercise detail plays the start↔peak animation and shows the Spanish instructions, and the RepDB attribution is visible.
- The "Fuerza y 1RM estimado" card and the per-exercise charts work with real session data.
- The training schema is forward-compatible (ADR-30): the *(E6)* columns/enums exist and the set row is polymorphic, so E6 needs no migration.
- `prodDebug` APK validated during a real workout by the user. Tag `v0.3.0`.
