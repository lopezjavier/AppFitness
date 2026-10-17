# E6 · Strength analytics and Phase 1 wrap-up → `v0.6.0` (`v1.0.0` after E7, the UI refinement epic: `07-roadmap.md` §1.1)

## Goal
Show strength progress per exercise and per muscle group, and polish the app into a stable Phase 1 release on phone and tablet.

## Scope
- Includes: estimated 1RM and volume charts per exercise, weekly volume per muscle group, accessibility review, empty/error states, tablet, release signing.
- Out of scope: AI training assistant and adaptive calorie adjustment (Phase 2).

## Dependencies
E5 (training data) for US-24; E3, E4 and E5 for US-25.

## Reference screen
"Fuerza y 1RM Estimado" section of `design/stitch/progress/screen.png`.

## Placement
- "Fuerza y 1RM estimado" summary card on the **Progress** tab, below the weight chart (as in the mockup).
- Per-exercise detail screen reachable from that card and from an exercise's detail in Workout/History.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas).

---

## US-24 · Strength analytics per exercise and muscle group
**As** a user aiming to get stronger
**I want** to see how my estimated 1RM and my volume evolve
**So that** I can measure my real progress for each exercise and muscle group.

```gherkin
Feature: Strength analytics

  Scenario: Summary card on Progress
    Given the user has trained "Press de banca con barra", "Sentadilla" and "Peso muerto" in the last 6 weeks
    When they open the "Progreso" tab
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
- Vico charts in athleticCyan (training domain).

---

## US-25 · Polish, accessibility, tablet and release build
**As** a user
**I want** a stable, accessible app that is comfortable on my phone and my tablet
**So that** I can use it every day with confidence.

```gherkin
Feature: Version 1.0 quality

  Scenario: Empty and error states on every screen
    Given any screen with remote data
    When there is no data, the network fails or the server responds with an error
    Then the design system's empty or error state is shown with a retry action
    And never a blank screen or a crash

  Scenario: Accessibility
    Given the Android accessibility scanner is enabled
    When the main screens are walked through
    Then every interactive element is at least 48 dp and has an accessible description
    And the primary text contrast meets WCAG AA

  Scenario: Font scaling
    Given the system font size at 130%
    Then key figures (kcal, weight, kg, reps, timer) are fully readable without clipping

  Scenario: Tablet
    Given a tablet in landscape
    Then Diary, Progress and the Workout session use the two-column layout
    And no element is stretched across the full width without a limit

  Scenario: Signed release build
    When the release build of the prod flavor is run
    Then a signed APK with R8 enabled is produced
    And the app works the same as in debug (login, diary, weight and workout)

  Scenario: Rotation and configuration changes
    Given the user is editing a quantity or a set
    When they rotate the device
    Then they do not lose what they were typing
```

**Technical notes**
- Release keystore outside the repo; release SHA-1 registered in Google Cloud (👤). R8 rules for kotlinx-serialization, Ktor and supabase-kt.
- Optional: `release.yml` workflow that builds the signed APK when a `v*` tag is created and attaches it to a GitHub Release (keystore as a base64 secret).
- Performance review: no unnecessary recompositions in the live session (Layout Inspector) and cold start < 2 s on the user's phone.

---

## Epic Definition of Done
- All stories US-01…US-25 closed; the app is verified manually at the checkpoints.
- Security checklist from `02-architecture.md` §7 reviewed and documented in the final PR.
- Signed release APK installed on the user's phone and tablet; positive feedback.
- Tag `v0.6.0`. The Phase 1 release (`v1.0.0` and GitHub Release) moves to E7, the UI refinement epic (`07-roadmap.md` §1.1).
