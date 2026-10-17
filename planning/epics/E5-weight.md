# E5 · Weight tracking → `v0.5.0`

## Goal
Log body weight (manually or from the scale via Health Connect) and see the real trend by filtering out daily fluctuations.

## Scope
- Includes: manual logging, editing and deletion, manual Health Connect sync (weight and body fat %), metrics panel, chart with a 7-day moving average, time filters, weekly rate.
- Out of scope: background sync, other Health Connect data (steps, sleep…), target weight with a date, photos.

## Dependencies
E2 (profile, strategy and the `body_measurements` table).

## Tables
`body_measurements`, `health_sync_state` (`03-data-model.md` §4).

## Reference screen
`design/stitch/progress/screen.png` (Health Connect banner, weight panel and chart). The strength section belongs to E3; the photo log is out of scope.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas).

---

## US-22 · Manual weight logging
**As** a user
**I want** to record my weight and, optionally, my body fat percentage
**So that** I can track it even without a smart scale.

```gherkin
Feature: Manual weight logging

  Scenario: Logging today's weight
    Given the user is on the "Progreso" tab
    When they tap "+ Registro"
    Then a Bottom Sheet opens with the weight prefilled with their last value (74,2 kg) and the current date and time
    When they enter 73,9 kg and tap "Guardar"
    Then a manual measurement of 73.9 kg is created
    And the panel shows "73,9 kg" as the current weight

  Scenario: Optional body fat
    When they also enter 15,1 % body fat and save
    Then the measurement includes body_fat_pct 15.1

  Scenario: Logging a past date
    When they change the date to yesterday at 07:30 and save
    Then the measurement appears on the chart on yesterday's date

  Scenario Outline: Validation
    When they enter <field> = <value>
    Then the "Guardar" button is disabled and the allowed range is shown

    Examples:
      | field  | value |
      | weight | 19,9  |
      | weight | 401   |
      | fat    | 80    |

  Scenario: History, editing and deletion
    Given the "Historial de mediciones" list in Progress
    When the user edits a manual measurement or deletes it (with "Deshacer")
    Then the chart and metrics are recalculated
    And Health Connect measurements are shown with their icon and can only be deleted
```

---

## US-23 · Manual Health Connect sync
**As** a user with a smart scale
**I want** to import with one button the weight and body fat readings my scale sends to Health Connect
**So that** I don't have to copy them by hand.

```gherkin
Feature: Health Connect sync

  Scenario: First sync and permissions
    Given the user has never granted the app permissions in Health Connect
    When they tap "Sincronizar" on the Health Connect banner
    Then the Health Connect permissions screen opens for reading weight and body fat
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
- `HealthDataSource` (domain) + `HealthConnectDataSource` (androidMain): `readRecords` of `WeightRecord` and `BodyFatRecord` since `health_sync_state.last_synced_at`.
- Upsert by `(user_id, external_id)` = the weight record's `metadata.id`. Weight/fat pairing by instant (± 2 min).
- Manifest: `READ_WEIGHT` and `READ_BODY_FAT` permissions, `<queries>` for the Health Connect package and a *privacy policy rationale* activity (`ACTION_SHOW_PERMISSIONS_RATIONALE`) with a short privacy text.
- The real Health Connect integration is not covered by automated tests: the ViewModel is tested with a `FakeHealthDataSource`, and the integration is tested manually on the user's phone.

---

## US-24 · Metrics panel and trend chart
**As** a user going through a body composition change
**I want** to see my smoothed weight trend and my weekly pace
**So that** daily fluctuations don't frustrate me.

```gherkin
Feature: Weight trend

  Background:
    Given the user has the "Déficit" strategy
    And daily weight entries for the last 90 days

  Scenario: Metrics panel
    When they open the "Progreso" tab
    Then they see the current weight "74,2 kg" with the measurement date and time
    And the most recent body fat if available
    And the pill "−0,45 kg esta semana" in green

  Scenario: Pace against the goal
    Given the moving average rose 0.30 kg in the last week
    Then the pill shows "+0,30 kg esta semana" in red

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
- `DailyWeights`, `MovingAverage` and `WeeklyRate` use cases with the examples from `04-business-rules.md` §3.
- Vico: dot layer + line; the range is loaded with 6 extra days before its start to compute the MA7 of the first day.
- Filter remembered with DataStore (local preference).

---

## Epic Definition of Done
- Manual and Health Connect weights coexist without duplicates; the weekly rate matches the reference calculation.
- pgTAP for `body_measurements` (including `external_id` uniqueness) and `health_sync_state`.
- Health Connect manual test documented in the PR.
- `prodDebug` APK validated with the user's real scale. Tag `v0.5.0`.
