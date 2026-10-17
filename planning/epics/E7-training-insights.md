# E7 · Training insights → `v0.7.0`

## Goal
Show real progress: per-exercise evolution, advanced statistics, muscle distribution and periodic reports, so the user can see whether they are getting stronger.

## Scope
- Includes: exercise chart records and non-weight metrics, advanced statistics (time frames, set-count chart, sets per muscle group, main exercises), muscle distribution (radar and body heatmap), Monthly Report, Year in Review, streaks and weekly check-ins.
- Out of scope: the algorithmic program generator / trainer (Phase 2); social features, cardio plans and publishing/monetization limits (out of Phase 1, `01-phase1-scope.md` §4).

## Dependencies
E3 (training core, the strength charts of US-15) and E6 (training depth).

## Tables and RPC
Built on `workout_sessions`/`workout_sets` and the exercise catalog. New statistics RPCs (time frames, sets per muscle group, current-vs-previous distribution) in `03-data-model.md` §6.

## Reference screen
`design/stitch/progress/screen.png` and its statistics sections.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas). Exercise names are Spanish catalog data.

---

## US-36 · Exercise charts: records and non-weight metrics
**As** a user
**I want** the exercise charts to mark my records and to follow every metric family
**So that** I can follow each exercise, not only the loaded ones.

```gherkin
Feature: Exercise graphs

  Scenario: Record markers
    Given the per-exercise 1RM and volume charts (US-15)
    When the user opens the "Press de banca con barra" detail
    Then the sessions that set a record (US-31) are marked on the chart

  Scenario: All-time frame
    Then the frames add "Todo" alongside 1M, 3M, 6M and 1A

  Scenario: Non-weight metrics
    Given "Plancha" (duration) and "Cinta de correr" (distance)
    Then their charts follow their metric (time or distance) instead of 1RM and volume
```

**Technical notes**
- Extends the `exercise_history` RPC (US-15) with the all-time frame and the PR markers (US-31); Vico charts in `athleticCyan`.

---

## US-37 · Advanced statistics
**As** a user aiming for progress
**I want** a full statistics screen
**So that** I can review my training over any period.

```gherkin
Feature: Advanced statistics

  Scenario: Time frames
    When the user opens "Estadísticas" and picks "Todo"
    Then the key values (entrenos, series, volumen, duración) cover the whole history

  Scenario: Set-count chart
    Then they see the number of sets over time

  Scenario: Sets per muscle group
    Then they see the sets per muscle group, counting the primary muscle as 1 and the secondary as 0,5

  Scenario: Main exercises
    Then they see their most trained exercises
```

**Technical notes**
- New statistics RPC (`03-data-model.md` §6); respects the warm-up inclusion setting (US-26).

---

## US-38 · Muscle distribution (radar and body heatmap)
**As** a user
**I want** to see how my training is distributed across muscles
**So that** I can spot imbalances.

```gherkin
Feature: Muscle distribution

  Scenario: Radar
    Then a radar shows "Actual" vs "Periodo anterior" per muscle group

  Scenario: Body heatmap
    Then a body map highlights the trained muscles of the last 7 days

  Scenario: Tap for detail
    When the user taps a muscle on the map
    Then they see its number of sets and its exercises
```

**Technical notes**
- Primary = 1, secondary = 0,5 (`04-business-rules.md` §4.6 for the warm-up variant).

---

## US-39 · Monthly Report
**As** a user
**I want** a monthly summary
**So that** I can review the month at a glance.

```gherkin
Feature: Monthly Report

  Scenario: Summary
    When the user opens the "Informe mensual"
    Then they see the days trained, the number of workouts, the duration and the total volume

  Scenario: Ready notification
    When a month closes
    Then the "Informe mensual listo" notification is shown
```

**Technical notes**
- Aggregated with the statistics RPC; the notification follows the app's notification policy.

---

## US-40 · Year in Review
**As** a user
**I want** an annual summary
**So that** I can look back at the year.

```gherkin
Feature: Year in Review

  Scenario: Annual summary
    When the user opens "Tu año en el gimnasio"
    Then they see the total volume, the best month, the streak, the muscle breakdown and their PRs

  Scenario: Sharing
    Then they can export it as an image
```

**Technical notes**
- Year aggregation of the statistics RPC; the export reuses the sharing component.

---

## US-41 · Streaks and weekly check-ins
**As** a user
**I want** to see my consistency
**So that** I stay motivated.

```gherkin
Feature: Streaks

  Scenario: Streak
    Then the app shows the consecutive weeks trained

  Scenario: Weekly check-in
    When a week closes
    Then a check-in compares this week's volume with last week's and lists the PRs
```

**Technical notes**
- Streak = consecutive ISO weeks with at least one completed session; the check-in reuses the statistics RPC.

---

## Epic Definition of Done
- The statistics RPCs are covered by unit and pgTAP tests with the `04-business-rules.md` examples.
- Every screen has loading, empty and error states (`US-43`).
- Numbers use tabular numerals and Spanish formatting (`04-business-rules.md` §5).
- `prodDebug` APK validated by the user. Tag `v0.7.0`.
