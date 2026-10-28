# E12 · Training data and preferences → `v1.x` (version to be defined)

## Goal
Collect what the coach cannot derive from the data, and make the rest-end alert work with the screen off: the user's intention, where and with what they train, notes, and discomfort per exercise.

## Scope
- Includes: rest-end alert with the screen off (with the new alert sound), training intention, static training preferences (place, equipment, optional days per week and session length), notes per set and per routine exercise, discomfort log per exercise with the fixed safety message, movement pattern in the exercise catalogue.
- Out of scope: the coach itself and the training plan storage (E13), an RIR field (derived from RPE, ADR-76), removal of the target weight (E13, ADR-78), water and voice (Phase 3).

## Decisions
ADR-75 (rest alert), ADR-76 (RIR derived from RPE; discomfort rule), ADR-78 (preferences, intention, `movement_pattern`; `activity_level` kept). Amends E3 US-13: its "End of rest" scenario (foreground only) and its note "no foreground service or notifications in Phase 1". Reopens the alert sound choice of E9 (points 15 and 16, T-059).

## Dependencies
E2 (Preferencias), E3 and E6 (live session, routines, RPE).

## Tables
- `training_preferences` (existing, E6): add `intention` enum (`strength` | `muscle` | `fat_loss` | `recomposition` | `maintenance`, nullable until chosen), `place` enum (`home` | `gym` | `other`), `equipment` `equipment[]`, `days_per_week` nullable 1-7, `session_minutes` nullable.
- `workout_sets.notes` (nullable text, 1-200 chars).
- `routine_exercises.notes` (nullable text, 1-200 chars).
- `exercise_discomforts` (`user_id`, `exercise_id`, `logged_at`, `level` smallint 1..5 (ADR-81; replaces `severe`, which is derived), `note` nullable, `resolved_at` nullable).
- `exercises.movement_pattern` enum (`squat` | `hinge` | `lunge` | `horizontal_push` | `vertical_push` | `horizontal_pull` | `vertical_pull` | `carry` | `core` | `isolation` | `cardio` | `other`); nullable for custom exercises.

---

## US-64 · Rest-end alert with the screen off
**As** an athlete training
**I want** to be alerted when my rest ends, even if I locked the phone
**So that** I start my next set on time.

```gherkin
Feature: Rest-end alert outside the app

  Scenario: Screen off when the rest ends
    Given the rest timer is running for 90 s
    When the user locks the phone and the rest ends
    Then the phone vibrates and plays the alert sound
    And a notification shows "Descanso terminado"

  Scenario: App in the background
    Given the rest timer is running and the app is in the background
    When the rest ends
    Then the same alert is shown

  Scenario: App in the foreground
    Given the rest timer is running and the app is in the foreground
    When the rest ends
    Then only the in-app alert is shown
    And no notification is posted

  Scenario: Skipping cancels the alert
    Given the rest timer is running in the background
    When the user taps "Omitir" in the app
    Then no alert is shown when the time would have ended

  Scenario: Finishing the session cancels the alert
    Given the rest timer is running in the background
    When the user finishes the session
    Then no alert is shown

  Scenario: Notifications denied
    Given the user denied notifications
    When the rest ends with the screen off
    Then the alert is not shown
    And the app explains once that alerts need the notification permission
```

**Technical notes**
- The technique (scheduled notification, exact alarm or foreground service) is chosen in the plan, after verifying on Android 17. Timing stays based on `endsAt`, as in US-13.
- The alert sound is chosen by the user at a 👤 checkpoint among the E9 point 16 candidates.

## US-65 · Training intention and preferences
**As** a user
**I want** to tell the coach my goal and where and with what I train
**So that** it plans for my real situation.

```gherkin
Feature: Training intention and preferences

  Scenario: Intentions are a fixed list
    When the user opens "Mi entrenamiento" in Preferencias
    Then they can choose among "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición" and "Mantenimiento"

  Scenario: Choosing the intention
    When the user chooses "Ganar fuerza" and taps "Guardar"
    Then the intention is stored
    And it is shown in "Mi entrenamiento"

  Scenario: Place defines the default equipment
    When the user chooses the place "Casa"
    Then the equipment defaults to "Mancuernas", "Banda" and "Peso corporal"
    And the user can change the equipment

  Scenario: Days and session length are optional
    Given the user has not set days or session length
    When they save "Mi entrenamiento"
    Then the preferences are stored without them
```

**Technical notes**
- Intention and preferences are static: they change only when the user edits them.
- Default equipment per place (design decision): home = dumbbell, band, bodyweight; gym = every value; other = bodyweight.
- The intention does not change the nutrition goal by itself; an accepted coach plan does (E13, ADR-76).
- Session length (user, 2026-10-28): 15-180 min in 5-min steps, shown with the app's existing duration format ("45 min", "1 h 0 min", "1 h 30 min").

## US-66 · Notes per set and per routine exercise
**As** a user
**I want** to add a note to a set or to an exercise of a routine
**So that** I remember what happened.

```gherkin
Feature: Notes

  Scenario: Note on a set
    When the user writes "pausa larga" on a set
    Then the note is saved with the workout
    And it is shown in the workout detail

  Scenario: Note on an exercise in the routine
    Given the routine has the note "bajar el peso si duele" on "Press banca"
    When the user starts the routine
    Then the note is shown in the live session for "Press banca"
```

## US-67 · Discomfort log per exercise
**As** a user
**I want** to record that an exercise bothered me and how much
**So that** the coach does not push its load.

Amended by ADR-81 (Checkpoint E12-A feedback, 2026-10-28): the user picks a level from an ordered scale; the app no longer infers severity from the note and shows no health message. T-147's term matching is removed by T-152.

```gherkin
Feature: Discomfort log

  Scenario: Logging discomfort with a level
    When the user taps "Registrar molestia" on "Press banca" in the live session
    And picks the level "Molestia leve"
    And saves it
    Then the discomfort is stored for "Press banca" with today's date and the level "Molestia leve"
    And "Press banca" shows "Molestia registrada" with the level "Molestia leve"

  Scenario: A level is required
    When the user opens "Registrar molestia" on "Press banca"
    Then no level is selected and "Guardar" is disabled
    When the user picks any level
    Then "Guardar" is enabled

  Scenario: The note is optional and never interpreted
    When the user logs discomfort on "Press banca" with the level "Molestia leve" and the note "dolor fuerte"
    Then the discomfort is stored with that level and that note
    And no health-professional message is shown
    And the discomfort is not marked severe

  Scenario: Levels are ordered and colour-coded
    When the user opens "Registrar molestia"
    Then the levels are listed from least to most severe:
      | Tensión o fatiga | Molestia leve | Pinchazo o chasquido | Hinchazón, hormigueo o entumecimiento | Dolor agudo o lesión |
    And each level shows its text label and a colour from greenish yellow to red
    And the lime of "Guardar" is not used by any level
    And a help button next to the title opens a sheet that explains each level

  Scenario: The two highest levels are severe
    When the user logs a discomfort with the level "Hinchazón, hormigueo o entumecimiento" or "Dolor agudo o lesión"
    Then the stored discomfort is severe
    When the user logs one with any lower level
    Then it is not severe

  Scenario: Resolving
    Given "Press banca" has an open discomfort
    When the user taps "Ya no me molesta"
    Then the discomfort is closed with the current date

  Scenario: Open discomforts expire
    Given a discomfort on "Press banca" was logged 8 days ago
    Then "Press banca" no longer shows "Molestia registrada"

  Scenario: Existing discomforts keep a level
    Given discomforts stored before the level existed
    Then those stored as severe now have the highest level and the others "Molestia leve"
```

**Technical notes**
- Level = `smallint` 1..5 in `exercise_discomforts.level` (1 "Tensión o fatiga", 2 "Molestia leve", 3 "Pinchazo o chasquido", 4 "Hinchazón, hormigueo o entumecimiento", 5 "Dolor agudo o lesión"). Labels, colours and help copy are finalised by ux-reviewer in T-152 (ADR-81). `severe` is derived (level >= 4), not stored. No term list, no matching and no fixed health message exist (the 12-term list of 2026-10-28 and E12 Q1 are void).
- A discomfort is open for 7 days or until resolved. E13 (US-70) blocks load increases while it is open, using the level.
- Notes (exercise, set, discomfort) are plain text for the user and the coach; nothing scans them.
- The open row shows the level next to "Molestia registrada" (text, plus the scale colour as reinforcement only).

## Data task · Movement pattern in the catalogue
Not a user story. `exercises.movement_pattern` is filled for every catalogue row by the generated catalogue migration (ADR-39), from the RepDB data where it has an equivalent field and otherwise from a reviewed mapping by catalogue key. Custom exercises get it as an optional field in the custom exercise form. Used by the coach to substitute exercises (E13).
