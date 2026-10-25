# E8 · Training insights → `v0.8.0`

## Goal
Show real progress: per-exercise evolution, advanced statistics, muscle distribution, so the user can see whether they are getting stronger.

## Scope
- Includes: grouped comidas in the Diario (US-55, ADR-68), exercise chart records and non-weight metrics, advanced statistics (time frames, set-count chart, sets per muscle group, main exercises), muscle distribution (radar and body heatmap).
- Out of scope: periodic reports (Monthly Report, Year in Review) and streaks with weekly check-ins (Phase 2, ADR-66); the algorithmic program generator / trainer (Phase 2); social features, cardio plans and publishing/monetization limits (out of Phase 1, `01-phase1-scope.md` §4).

## Dependencies
E3 (training core, the strength charts of US-15) and E6 (training depth). E7 precedes it in the roadmap but is not a functional dependency.

## Tables and RPC
Built on `workout_sessions`/`workout_sets` and the exercise catalog. New statistics RPCs (time frames, sets per muscle group, current-vs-previous distribution) in `03-data-model.md` §6.

## Navigation (ADR-37)
There is no Progreso tab: the statistics and other insights of this epic are full-screen destinations (back arrow) reached from statistics entry buttons on the Perfil root.

## Reference screen
`design/stitch/progress/screen.png` and its statistics sections (style reference only; the screen is not a tab).

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas). Exercise names are Spanish catalog data.

---

## US-39 · Exercise charts: records and non-weight metrics
**As** a user
**I want** the exercise charts to mark my records and to follow every metric family
**So that** I can follow each exercise, not only the loaded ones.

```gherkin
Feature: Exercise graphs

  Scenario: Record markers
    Given the per-exercise 1RM and volume charts (US-15)
    When the user opens the "Press de banca con barra" detail
    Then the sessions that set a record (US-34) are marked on the chart

  Scenario: All-time frame
    Then the frames add "Todo" alongside 1M, 3M, 6M and 1A

  Scenario: Non-weight metrics
    Given "Plancha" (duration) and "Cinta de correr" (distance)
    Then their charts follow their metric (time or distance) instead of 1RM and volume
```

**Technical notes**
- Extends the `exercise_history` RPC (US-15) with the all-time frame and the PR markers (US-34); Vico charts in `athleticCyan`.

---

## US-40 · Advanced statistics
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
- New statistics RPC (`03-data-model.md` §6); respects the warm-up inclusion setting (US-29).

---

## US-41 · Muscle distribution (radar and body heatmap)
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

## US-55 · Comidas grouped in the Diario
**As** a user
**I want** a saved comida added to a meal to appear as one collapsible group in the Diario
**So that** I see its total at a glance and still adjust each ingredient.

> Snapshot grouping (ADR-68): only entries created from now on are grouped; no link to the comida afterwards.

### Scenario: A comida added to a meal appears as a collapsed group
  Given the saved comida "Avena con plátano" with oats and a banana
  When I add it to "Desayuno"
  Then "Desayuno" shows one row "Avena con plátano" with its total kcal and macros
  And the row is collapsed and the ingredients are not listed

### Scenario: Expanding a group
  Given a collapsed group "Avena con plátano" in "Desayuno"
  When I expand it
  Then I see its ingredients as regular food rows that I can edit or delete
  And when I leave the app and come back the group keeps its expanded or collapsed state

### Scenario: A food added later stays outside the group
  Given a group "Avena con plátano" in "Desayuno"
  When I add "Café con leche" to "Desayuno"
  Then "Café con leche" appears as an individual row outside the group

### Scenario: Removing ingredients
  Given a group "Avena con plátano" with two ingredients
  When I delete one ingredient
  Then the group stays with the other one and its totals update
  When I delete the last ingredient
  Then the group disappears from "Desayuno"

### Scenario: Saving a meal as a comida groups it
  Given "Desayuno" has individual foods
  When I save the meal as the comida "Mi desayuno"
  Then those entries now appear as one group "Mi desayuno"

### Scenario: Past entries and the saved comida are unaffected
  Given entries created before this feature or a comida that I later edit or delete
  Then past entries keep appearing as individual foods
  And the existing groups do not change

### Scenario: Default comidas are grouped
  Given "Desayuno" has a default comida for today
  When the new day opens and the default is applied
  Then it appears as a collapsed group

---

## Epic Definition of Done
- The statistics RPCs are covered by unit and pgTAP tests with the `04-business-rules.md` examples.
- Every screen has loading, empty and error states (`US-46`).
- Numbers use tabular numerals and Spanish formatting (`04-business-rules.md` §5).
- `prodDebug` APK validated by the user. Tag `v0.8.0`.
