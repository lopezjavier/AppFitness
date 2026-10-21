# E5 · Weight tracking → `v0.5.0`

## Goal
Log body weight, body fat, composition and body circumferences (manually or from the scale via Health Connect), track the real trend by filtering out daily fluctuations, and see how far the user is from their goal.

## Scope
- Includes: manual logging, editing and deletion of weight and body circumferences, manual Health Connect sync (reading and writing weight and body fat plus lean mass, body water, bone mass and BMR from smart scales), target weight and distance to the goal, metrics panel with BMI, measurement comparison and informative interpretation, chart with a 7-day moving average, time filters, weekly rate.
- Out of scope: background sync, other Health Connect data (steps, sleep…), target weight with a date or estimated time, photos.

## Dependencies
E2 (profile, strategy and the `body_measurements` table).

## Tables
`body_measurements`, `body_circumferences`, `health_sync_state`, `profiles.target_weight_kg` (`03-data-model.md` §2, §4).

## Navigation (ADR-37)
There is no Progreso tab. The weight chart, history and metrics are a full-screen destination (back arrow) reached from a statistics entry button on the Perfil root. Preferencias already edits today's weight (T-041); this epic adds the log, history and chart. The manual-row-per-day rule of `03-data-model.md` §4 applies to both entry points.

## Reference screen
`design/stitch/progress/screen.png` (Health Connect banner, weight panel and chart). The strength section belongs to E3; the photo log is out of scope.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas).

---

## US-25 · Manual weight and body measurements
**As** a user
**I want** to record my weight and, optionally, my body fat percentage and body circumferences
**So that** I can track my body composition even without a smart scale.

```gherkin
Feature: Manual weight and body measurements

  Scenario: Logging today's weight
    Given the user is on the weight screen opened from the "Perfil" tab (button "Peso", ADR-37)
    When they tap "Registrar peso de hoy"
    Then a Bottom Sheet opens with the weight prefilled with their last value (74,2 kg) and the current date and time
    When they enter 73,9 kg and tap "Guardar"
    Then a manual measurement of 73.9 kg is created
    And the panel shows "73,9 kg" as the current weight

  Scenario: Optional body fat
    When they also enter 15,1 % body fat and save
    Then the measurement includes body_fat_pct 15.1

  Scenario: One manual weight per day
    Given the user already logged a manual weight today
    When they tap "Registrar peso de hoy"
    Then today's weight opens to be edited instead of creating a second one

  Scenario: Logging a weight for a past day
    Given the weight calendar ("Historial de mediciones") with no weight yesterday
    When they tap yesterday, set the time to 07:30, enter 74,0 kg and save
    Then a manual measurement is stored for yesterday at 07:30
    And yesterday is marked in the calendar and the measurement appears on the chart on yesterday's date

  Scenario Outline: Validation
    When they enter <field> = <value>
    Then the "Guardar" button is disabled and the allowed range is shown

    Examples:
      | field  | value |
      | weight | 19,9  |
      | weight | 401   |
      | fat    | 80    |

  Scenario: Weight calendar, editing and deletion
    Given the weight calendar ("Historial de mediciones") on the weight screen, with the days that have a measurement marked
    When the user taps a day with a manual weight
    Then they can change its weight, body fat and time, but not its date, and save with "Guardar"
    When they delete it
    Then it is removed at once, with no confirmation and no "Deshacer" (ADR-45)
    And the chart and metrics are recalculated
    And Health Connect measurements are shown with their icon and can only be deleted

  Scenario: Logging a circumference
    Given the "Circunferencias" section in Progress
    When they tap "+ Medida", choose "Cintura" and enter 84,5 cm
    Then a circumference measurement of 84.5 cm for the waist is created
    And it appears in the waist history ordered by date

  Scenario: Waist-to-hip ratio
    Given a waist of 84,5 cm and a hip of 98,0 cm
    Then the section shows the ratio "0,86"

  Scenario: Editing and deleting a circumference
    When the user edits a circumference value or deletes it
    Then the ratio and the history are recalculated

  Scenario Outline: Circumference validation
    When they enter <value> cm
    Then the "Guardar" button is disabled and the allowed range is shown

    Examples:
      | value |
      | 19,9  |
      | 251   |
```

**Technical notes**
- Weight calendar (user decision 2026-10-21): a month grid (reusing `MonthCalendarGrid`) marks the days with measurements. An empty day opens a sheet to set that day's weight; a day with a weight opens it to edit or delete. The date of a measurement is never edited (it is fixed by the day tapped), so the one-manual-weight-per-day rule (`03-data-model.md` §4) cannot be broken by an edit. A day with only Health Connect readings can still get a manual weight.
- `body_circumferences` migration + RLS + pgTAP; the waist-to-hip ratio is derived in the domain (`04-business-rules.md` §3.9), never stored.
- The circumference site picker offers neck, chest, waist, hip, arm, thigh and calf; value in cm with 1 decimal.

---

## US-26 · Manual Health Connect sync
**As** a user with a smart scale
**I want** to import with one button the readings my scale sends to Health Connect and have my own entries written back
**So that** my weight, body composition and other apps stay in sync without copying them by hand.

```gherkin
Feature: Health Connect sync

  Scenario: First sync and permissions
    Given the user has never granted the app permissions in Health Connect
    When they tap "Sincronizar" on the Health Connect banner
    Then the Health Connect permissions screen opens for reading and writing weight and body fat
    When they grant them
    Then the app imports the readings from the last 90 days
    And the banner shows "Sincronizado ahora" with a green indicator

  Scenario: Incremental sync without duplicates
    Given the last sync was 2 days ago
    And the scale recorded 2 new readings in Health Connect
    When the user taps "Sincronizar"
    Then exactly those 2 readings are imported
    And tapping "Sincronizar" again does not create any new measurement

  Scenario: Weight and fat from the same weigh-in
    Given a weight reading and a body fat reading with the same instant
    When they are synced
    Then they are stored as a single measurement with weight and fat percentage

  Scenario: Extra composition metrics
    Given a weigh-in with weight, body fat, lean mass, body water, bone mass and BMR in Health Connect
    When it is synced
    Then the measurement stores all of them in the same row

  Scenario: Writing back a manual entry
    Given the user has granted the write permission
    When they log a weight of 73,9 kg and 15,1 % body fat manually
    Then the app writes both values to Health Connect

  Scenario: Editing or deleting a written-back entry
    Given a manual weight of 73,9 kg that was written to Health Connect
    When the user changes it to 73,5 kg and saves
    Then the copy in Health Connect is updated to 73,5 kg
    When the user deletes it
    Then the copy in Health Connect is deleted too

  Scenario: Writing back without permission
    Given the user denied the write permission
    When they log a weight manually
    Then the measurement is saved in the app and nothing is written to Health Connect

  Scenario: Permissions denied
    When the user denies the permissions
    Then the banner shows "Permiso necesario" with a shortcut to request them again
    And nothing is imported

  Scenario: Health Connect unavailable
    Given a device without Health Connect installed or requiring an update
    When the user taps "Sincronizar"
    Then they are offered to install or update Health Connect from Google Play

  Scenario: Last sync status
    Given the last sync was 12 minutes ago
    Then the banner shows "Sincronizado hace 12 min"
```

**Technical notes**
- `HealthDataSource` (domain) + `HealthConnectDataSource` (androidMain): `readRecords` of `WeightRecord`, `BodyFatRecord`, `LeanBodyMassRecord`, `BodyWaterMassRecord`, `BoneMassRecord` and `BasalMetabolicRateRecord` since `health_sync_state.last_synced_at`, and `insertRecords` for the write-back (`04-business-rules.md` §3.7).
- Upsert by `(user_id, external_id)` = the weight record's `metadata.id`. Weight and the composition records are paired by instant (± 2 min).
- Manifest: `READ/WRITE_WEIGHT`, `READ/WRITE_BODY_FAT`, `READ/WRITE_LEAN_BODY_MASS`, `READ/WRITE_BODY_WATER_MASS`, `READ/WRITE_BONE_MASS`, `READ/WRITE_BASAL_METABOLIC_RATE` permissions, `<queries>` for the Health Connect package and a *privacy policy rationale* activity (`ACTION_SHOW_PERMISSIONS_RATIONALE`) with a short privacy text.
- Manual rows keep the id of their Health Connect copy, so edits update it and deletes remove it (user decision 2026-10-21). An entry saved without the permission has no copy and is not written later. Deleting an imported reading removes it from the app only (an app can only delete its own Health Connect records), and it is not re-imported.
- The write-back never fails the save: a denied or revoked permission skips it. The real Health Connect integration is not covered by automated tests: the ViewModel is tested with a `FakeHealthDataSource`, and the integration is tested manually on the user's phone.

---

## US-27 · Metrics panel, goal and trend chart
**As** a user going through a body composition change
**I want** to see my smoothed weight trend, my weekly pace, how far I am from my goal and how my metrics read
**So that** daily fluctuations don't frustrate me and I understand my numbers.

```gherkin
Feature: Weight trend, goal and interpretation

  Background:
    Given the user has the "Déficit" strategy
    And daily weight entries for the last 90 days

  Scenario: Metrics panel
    When they open the weight screen from the "Perfil" tab
    Then they see the current weight "74,2 kg" with the measurement date and time
    And the most recent body fat if available
    And the pill "−0,45 kg esta semana" in green

  Scenario: BMI
    Given a height of 178 cm and a current weight of 74,2 kg
    Then the panel shows "IMC 23,4" with the status "Normal"

  Scenario: Pace against the goal
    Given the moving average rose 0.30 kg in the last week
    Then the pill shows "+0,30 kg esta semana" in red

  Scenario: Target weight and distance
    Given a target weight of 70,0 kg, a current weight of 74,2 kg and a first weight in the app of 78,0 kg
    Then the goal card shows "faltan 4,2 kg" and a progress bar at 48 %
    When the user reaches 70,0 kg or goes below it
    Then the goal card shows "objetivo alcanzado"

  Scenario: Target distance on maintenance
    Given the "Mantenimiento" strategy, a target of 70,0 kg and a current weight of 71,0 kg
    Then the goal card shows "1,0 kg por encima del objetivo"

  Scenario: Setting the target weight
    Given no target weight is set
    When the user taps "Fijar objetivo" and enters 70,0 kg
    Then the target is saved in their profile and the goal card shows the distance

  Scenario: Compare two measurements
    Given a measurement of 74,2 kg on today and 75,6 kg one month ago
    When the user selects the older measurement and taps "Comparar"
    Then they see the difference "−1,4 kg" and the body fat difference

  Scenario: Metric interpretation
    When the user opens a metric detail
    Then they see the value, a status and a short explanation
    And the disclaimer "Este producto no es un dispositivo médico; los resultados no sirven para diagnóstico"

  Scenario: Two-layer chart
    Then the chart shows daily readings as low-opacity dots
    And a thick athleticCyan line with the 7-day moving average
    And the legend "MA 7D"

  Scenario: Time filter
    Given the default filter is "3M"
    When the user taps the "1M" chip
    Then the horizontal axis shows only the last 30 days with an animated transition
    And the chosen filter is remembered the next time they open Progress

  Scenario: Not enough data
    Given a user with a single measurement
    Then the pill shows "Sin datos suficientes"
    And the chart shows the dot with the message "Registra tu peso varios días para ver la tendencia"

  Scenario: Point detail
    When the user long-presses a point on the chart
    Then they see the date, that day's weight and that day's moving average
```

**Technical notes**
- `DailyWeights`, `MovingAverage`, `WeeklyRate`, `BMI`, `TargetDistance` and interpretation use cases with the examples from `04-business-rules.md` §3 (the interpretation ranges and the disclaimer are in §3.8).
- The target weight is stored in `profiles.target_weight_kg` (E2 profile, edited from the weight screen). The distance wording depends on the strategy and the goal card's progress bar starts at the user's first weight in the app (the onboarding row) (`04-business-rules.md` §3.5, user decision 2026-10-21).
- Vico: dot layer + line; the range is loaded with 6 extra days before its start to compute the MA7 of the first day.
- Filter remembered with DataStore (local preference).

---

## Epic Definition of Done
- Manual and Health Connect weights coexist without duplicates; the weekly rate matches the reference calculation.
- The BMI, target distance and waist-to-hip ratio match the examples in `04-business-rules.md` §3.
- pgTAP for `body_measurements` (including `external_id` uniqueness), `body_circumferences`, `health_sync_state` and `profiles.target_weight_kg`.
- Health Connect read and write manual test documented in the PR.
- `prodDebug` APK validated with the user's real scale. Tag `v0.5.0`.
