# E10 · In-app reminders and weekly check-in → `v1.x` (version to be defined)

## Goal
Remind the user, inside the app, to log what the coach needs (weight and circumferences), and bring back the weekly check-in.

## Scope
- Includes: in-app notification center ("Centro de avisos"), weight and circumference reminders, silencing a reminder type, weekly check-in with the deterministic weekly summary (reactivates part of E8 US-44).
- Out of scope: Android system notifications for reminders (reminders appear the next time the user opens the app; there is no time window), muscle soreness prompts (ADR-73, `07-coach-design.md` §7), the training streak (still deferred, `09-open-questions.md`), water and voice (Phase 3).

## Decisions
ADR-73. Amends ADR-66 (weekly check-in returns) and E8 US-44 (the check-in part).

## Dependencies
E2 (Preferencias), E5 (weight and circumferences), E6 (sessions), E8 (weekly volume and records RPCs).

## Tables
- `notification_settings` (`user_id`, `kind` enum `weight` | `circumference` | `weekly_check_in`, `enabled`), unique per user and kind; a missing row means enabled.
- `weekly_check_ins` (`user_id`, `week_start` date (Monday), `energy` 1-5, `notes` nullable), unique per user and week.
- `profiles.check_in_weekday` (ISO weekday, default 7 = Sunday; design decision).

## Navigation
The entry point of "Centro de avisos" is defined by ux-reviewer in the plan pass, within the 3-tab navigation (ADR-37).

---

## US-57 · In-app notification center
**As** a user who wants to keep my data complete
**I want** a center inside the app that lists my pending reminders
**So that** I see what needs my attention.

```gherkin
Feature: Notification center

  Scenario: Pending reminders are listed
    Given the user has a weight reminder and a weekly check-in pending
    When they open "Centro de avisos"
    Then both reminders are listed with their date

  Scenario: Pending count is visible
    Given the user has 2 pending reminders
    When they open the app
    Then the entry point of "Centro de avisos" shows 2

  Scenario: Reminders appear on the next open
    Given the weight condition was met during the night
    When the user opens the app in the morning
    Then the weight reminder is listed

  Scenario: Opening a reminder takes the user to log it
    When the user taps the weight reminder
    Then the weight entry opens

  Scenario: A reminder type is silenced
    Given the user turns off "Recordatorio de peso" in Preferencias → "Avisos" and saves
    When the weight condition is met
    Then no weight reminder is listed

  Scenario: Centro de avisos has no switches
    When the user opens "Centro de avisos"
    Then it lists reminders only and shows no switch

  Scenario: Unsaved switch changes are discarded on back
    Given the user toggles "Recordatorio de peso" in Preferencias → "Avisos"
    When the user goes back and confirms discarding (R-14)
    Then the switch keeps its saved value
```

**Technical notes**
- Reminders are computed from the stored data each time the app opens or returns to the foreground; nothing is stored per reminder and there is no background job.

## US-58 · Weight and circumference reminders
**As** a user
**I want** a reminder when I miss my weigh-ins or my measurements
**So that** the coach's trend stays accurate.

```gherkin
Feature: Weight and circumference reminders

  Scenario: No weigh-in for 2 days in a row
    Given the last weight is from 2 days ago
    When the user opens the app
    Then the reminder "No te has pesado en 2 días" is listed

  Scenario: Weighed yesterday
    Given the last weight is from yesterday
    When the user opens the app
    Then no weight reminder is listed

  Scenario: No circumference for 14 days
    Given the last circumference is from 15 days ago
    When the user opens the app
    Then the reminder "No te has medido en 2 semanas" is listed

  Scenario: Logging clears the reminder
    Given the reminder "No te has pesado en 2 días" is listed
    When the user saves a weight
    Then the reminder is no longer listed

  Scenario: A Health Connect weight clears the reminder
    Given the reminder "No te has pesado en 2 días" is listed
    When a Health Connect sync stores today's weight
    Then the reminder is no longer listed
```

**Technical notes**
- Daily weigh-ins are recommended for the trend (`07-coach-design.md` §5); 2 days without one triggers the reminder.
- 14 days for circumferences is a design decision (`07-coach-design.md` §9).
- Days are calendar days in the device time zone, as `log_date`.

## US-59 · Weekly check-in (reactivates US-44 in part)
**As** a user
**I want** a short weekly check-in with how I felt and my week in figures
**So that** I review my week and the coach can read it with my training.

```gherkin
Feature: Weekly check-in

  Scenario: Check-in appears on the chosen day
    Given the check-in day is Sunday
    When the user opens the app on Sunday
    Then the weekly check-in is listed in "Centro de avisos"

  Scenario: Changing the check-in day
    When the user sets "Día del check-in" to Friday in Preferencias → "Avisos" and saves
    Then the check-in is listed from Friday

  Scenario: Answering the check-in
    When the user rates energy 4 out of 5 and writes "semana dura en el trabajo"
    Then the check-in is stored for the current week
    And the reminder is no longer listed

  Scenario: Weekly summary
    When the user opens the check-in
    Then they see this week's training volume compared with last week
    And the personal records of the week
    And the weight trend of the week

  Scenario: Missed check-in
    Given the user did not answer last week's check-in
    When a new week starts
    Then only the current week's check-in is listed
```

**Technical notes**
- Sleep is not asked: it comes from the watch (E11).
- The summary reuses the E8 RPCs (`weekly_set_counts`, `personal_records`) and the weight trend; E13 adds the coach's commentary to this screen (US-71).
