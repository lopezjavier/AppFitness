# E11 · Health Connect daily sync → `v1.x` (version to be defined)

## Goal
Once a day, on the first app open, bring the watch data (sleep, steps, calories, resting heart rate) and the scale data into the app, while keeping the "Sincronizar" button for fresh weigh-ins.

## Scope
- Includes: automatic sync on the first open of the day for every read type; the existing "Sincronizar" button syncs every read type on demand; reads sleep, steps, active and total calories and resting heart rate; write permissions limited to what the app writes.
- Out of scope: background sync, writing food or training to Health Connect, water (Phase 3).

## Decisions
ADR-74 (amends ADR-08: adds the daily automatic trigger and the new read types; the manual button stays). Amends the E5 scope line "Out of scope: background sync, other Health Connect data (steps, sleep...)": the new types are in, background sync stays out.

## Dependencies
E2 (profile), E5 (weight, `body_measurements`, `health_sync_state`).

## Tables
- `health_sync_state` (existing): `last_synced_at`, `changes_token`; add `last_auto_sync_date` (local date).
- `daily_activity` (`user_id`, `day`, `steps`, `active_kcal`, `total_kcal`), unique per user and day; a re-sync of a day replaces its values.
- `sleep_sessions` (`user_id`, `started_at`, `ended_at`, `minutes`, `stages` jsonb, `external_id`), unique per user and `external_id`.
- `resting_heart_rate` (`user_id`, `day`, `bpm`, `external_id`), unique per user and day.
- RLS as the other tables (owner only); account deletion cascades (ADR-29).

---

## US-60 · Daily automatic sync on the first open
**As** a user with a smart scale and a watch
**I want** the app to bring my Health Connect data when I first open it each day
**So that** I do not have to remember a button for my watch data.

```gherkin
Feature: Daily sync on app open

  Scenario: First open of the day syncs
    Given the last automatic sync was yesterday
    When the user opens the app
    Then Health Connect is read for every granted type
    And the status line shows the time of the sync

  Scenario: Second open the same day does not sync automatically
    Given the app already synced automatically today
    When the user opens the app again
    Then Health Connect is not read

  Scenario: The button still syncs on demand
    Given the app already synced automatically today
    And the user has just weighed themselves
    When they tap "Sincronizar"
    Then Health Connect is read
    And today's weight is shown

  Scenario: No connection keeps the last data
    Given the device is offline
    When the user opens the app
    Then the stored data is shown
    And the status line says that the last sync was not possible
    And the next open retries the automatic sync

  Scenario: Permission missing
    Given the user has not granted the read permissions
    When the user opens the app
    Then the status line asks for the permissions
    And nothing is read
```

**Technical notes**
- An automatic sync that fails does not set `last_auto_sync_date`.
- Watch data has daily granularity (last night's sleep, yesterday's steps), so one automatic read a day is enough; the button covers fresh weigh-ins (ADR-74).

## US-61 · Read sleep, steps, calories and resting heart rate
**As** a user with a watch
**I want** my sleep, steps, calories and resting heart rate in the app
**So that** the coach can see my recovery and my activity.

```gherkin
Feature: Watch data

  Scenario: Last night's sleep is stored
    Given the watch wrote a sleep session last night
    When a sync runs
    Then the sleep session is stored with its duration

  Scenario: Yesterday's steps are stored
    Given the watch wrote 8500 steps yesterday
    When a sync runs
    Then the day shows 8500 steps

  Scenario: A type the watch does not provide
    Given the watch does not write resting heart rate
    When a sync runs
    Then no resting heart rate is stored
    And no error is shown

  Scenario: Partial permission
    Given the user granted steps but not sleep
    When a sync runs
    Then the steps are stored
    And sleep is not read
```

**Technical notes**
- Where the values are shown (weight screen, statistics) is defined in the plan pass by ux-reviewer; E13 consumes them through the coach summary.

## US-62 · No duplicates
**As** a user
**I want** the same reading never to appear twice
**So that** my totals are right.

```gherkin
Feature: No duplicates

  Scenario: A second sync the same day adds nothing
    Given a sync already stored yesterday's steps
    When the user taps "Sincronizar"
    Then yesterday's steps appear once

  Scenario: Records written by AppFitness are not imported
    Given AppFitness wrote a weight to Health Connect
    When a sync runs
    Then the weight is not imported a second time
```

## US-63 · Only the permissions the app uses
**As** a user
**I want** the app to ask only for the Health Connect permissions it needs
**So that** I trust what it reads and writes.

```gherkin
Feature: Permissions

  Scenario: Write permissions are limited to weight and body fat
    Given the app is installed
    Then it requests write access only for weight and body fat

  Scenario: History older than 30 days
    Given the first sync reads the last 90 days
    When the history permission is not granted
    Then the app asks for it before reading older data
```

**Technical notes**
- Verify `READ_HEALTH_DATA_HISTORY` behaviour on the device before the breakdown (`tasks/todo.md`, notes for later epics).
