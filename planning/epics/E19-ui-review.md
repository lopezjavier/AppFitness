# E19 · UI review

> **Status: DEFINED (product-owner, updated 2026-11-11, ADR-103), not planned.** Stories US-131 and US-123. The `epic-planner` breaks it into tasks. It runs after E18 so that every screen, including the ones E18 adds, is reviewed once. US-120…US-122 moved to E18.

## Intent

**Problem.** Reviewing a UI change today means building and installing the APK each time, and only about 17 design-system components have images (Roborazzi goldens). Nobody has reviewed the whole app together, so the same style feedback gets repeated screen by screen.

**Outcome.**
- Every screen and state can be seen on the computer, with a dummy user and dummy data, without touching the phone.
- All of the app's UI has been reviewed with the user.
- Each decision is recorded once as a design-system change or UX rule and applied everywhere, so the user never has to say it twice.

**Non-goals.** Changing behaviour or adding features; a second UI-automation harness that needs debugging (`CLAUDE.md`, ADR-27); rebuilding screens from scratch: each change goes through the design system and is swept across every screen that uses the pattern.

**Approach chosen and why.** Tools first, then the review in rounds.
1. Build the tools: a screen gallery (Roborazzi, already in the repo, on the Robolectric host tests with fake data; no device, no backend) and a rich emulator dataset (local flavor, Docker Supabase, the seeded test user). The tool-choice spike was dropped: Roborazzi exists and Maestro conflicts with ADR-27. `@Preview` needs fake data per screen and is not used.
2. Review in rounds by area, with early feedback and no 👤 checkpoint gates (there is no new functionality to test; the user reviews images or the emulator by themselves).
3. Every accepted change is written to `05-design-system.md` §8-§10 first and applied to every screen with the pattern in the same pass.

## Rules
`05` §8-§10; `04`; ADR-27, ADR-80, ADR-103.

## US-131 · See every screen without installing the APK
**As** the user reviewing the app
**I want** to see every screen without building and installing it
**So that** a review is fast and complete.

```gherkin
Feature: Review tooling

  Scenario: One command renders every screen
    When I run the gallery command
    Then I get one image per screen and state, in all of the app's UI
    And no device, no backend, no real account and no secret is needed

  Scenario: No screen is missing
    Given a screen exists in the navigation
    When it has no gallery entry
    Then the gallery check fails and names it

  Scenario: States are shown, not only the happy path
    Then each screen appears with data, empty, loading, and error states where they exist, and its sheets and dialogs

  Scenario: Images are easy to browse
    Then the images are grouped by area and opened from one index page

  Scenario: A rich dummy user for real flows
    Given the local flavor on the emulator
    When I log in with the test login
    Then the user has weeks of diary and weight history, a training block with finished sessions, a trainer plan, and reports and notices to open
    And the dataset is local only and holds no personal data

  Scenario: One command starts the live flow
    Then one command starts the local backend with the dummy data and installs the app on the emulator
```

## US-137 · See each exercise's picture and pick a lift in Fuerza (T-301)
**As** the user
**I want** every exercise listed or headed by name to show its picture, and Fuerza to list the lifts as rows
**So that** I recognise an exercise at a glance.

```gherkin
Feature: Exercise pictures

  Scenario: The live session header shows the picture
    Given a catalogue exercise in the live session
    Then its header shows the exercise picture at 32 dp, not a dumbbell badge
    And a custom exercise shows the placeholder

  Scenario: A finished exercise is marked by a check and text
    Given every set of an exercise is done
    Then its header shows a check described "Ejercicio completado"

  Scenario: Estadísticas > Entreno and the muscle sheet show pictures
    Then each exercise row shows its picture

  Scenario: The workout form and the session detail show pictures
    Then each exercise card in the new, edit and past workout form, and in the session detail, shows its picture

  Scenario: Fuerza lists one row per exercise
    Then each lift is a row with its picture and the chosen one has a lime check
    When I select another row
    Then "Récord 1RM estimado" shows that lift
    And the selection survives leaving the app
```

## US-138 · Preferencias keeps the plan's calories (T-303)
**As** the user
**I want** saving a Preferencias section never to change my calories or macros, and numbers with a unit never to overlap it
**So that** the Trainer plan's target stays the one I accepted and every field is readable.

```gherkin
Feature: Preferencias and the plan's calories

  Scenario: Saving a section leaves the goals untouched
    Given an accepted plan with a calorie target
    When I save "Cocina" in Preferencias
    Then the stored calorie target and macros do not change
    And the plan is not marked as edited by me

  Scenario: Nutrición lists no calories or macros
    When I open Preferencias
    Then Nutrición lists "Comidas del día", "Comidas por defecto" and "Cocina" only

  Scenario: An old saved draft still opens
    Given a saved draft that has energy or macros keys
    When I come back to Preferencias
    Then it opens without error and the other values are restored

  Scenario: A unit never overlaps its number
    Given a field with a unit such as "kcal", "g" or "min"
    When the value is up to four digits with a decimal, at any font size
    Then the unit is drawn beside the number and never over it
```

## US-140 · The trainer owns the activity level (T-308)
**As** the user
**I want** my activity level measured from my steps or watch when they exist, and never to block my plan when it changes nothing
**So that** I only answer what the app cannot measure (ADR-109, `07` §2, `04` §7).

```gherkin
Feature: Activity level owned by the trainer

  Scenario: Measured level is read-only
    Given 7 or more days of steps in the last 28 days
    When I open "Tus datos"
    Then "Nivel de actividad" shows the level and "Según tus pasos: 8.200 al día" with no chip and no sheet

  Scenario: Watch calories as basis
    Given no steps but 7 or more days of active kcal
    Then the row says "Según tu reloj: N kcal activas al día" and is read-only

  Scenario: Without data it is my answer
    Given fewer than 7 days of steps and of active kcal
    Then the row is editable, has the "Provisional" chip and says "Según lo que nos contaste"

  Scenario: The level does not block Aceptar when calories come from maintenance or measured data
    Given a provisional level and the expenditure source is my stated maintenance or the adaptive one
    Then "Aceptar" is enabled and "Valores por confirmar" does not list "Nivel de actividad"
    And the activity row keeps its "Provisional" chip (it is still my answer; the chip does not block)
    And a "?" on the row (Plan and Antes de empezar) opens a sheet "Nivel de actividad" saying it does not change my calories

  Scenario: The level blocks on the formula path
    Given a provisional level and the expenditure is BMR x factor
    Then "Valores por confirmar" lists "Nivel de actividad" and "Aceptar" is disabled

  Scenario: Provisional ends with data
    Given the weight trend is used in the expenditure (20 diary days, 4 weekly averages, 8 weigh-in days)
    Then "Calorías de mantenimiento" has no chip and does not block; with a diary alone it keeps "Provisional"
    And Experiencia and Tiempo entrenando lose the chip together when my history reaches the class I reported

  Scenario: Preferencias explains the answer
    When I open the "Nivel de actividad" picker in Preferencias
    Then its header has a "?" that says it is used only without step or watch data

  Scenario: Leaving the app
    Given the activity sheet is open on a level I picked but not saved
    When I leave the app and come back, or data makes the level measured
    Then the sheet is restored, or closed silently with no stale draft if the level became measured
```

## US-123 · Review of all the UI
**As** the user
**I want** all the UI reviewed
**So that** frictions are removed once and for all, through the design system.

```gherkin
Feature: Screen review

  Scenario: Reviewed in rounds
    Given the gallery exists
    When ux-reviewer and I review an area (Diario, Entreno, Trainer, Perfil, and every flow, sheet and dialog reachable from them)
    Then I receive a findings list: screen, problem, proposed change, and the component or rule it affects
    And I accept, reject or change each item in chat before any task is built

  Scenario: Every change goes through the design system
    Given I accept a finding
    Then the component or the visual decision is changed in 05-design-system.md first
    And the change is applied to every screen that uses the pattern in the same pass

  Scenario: A decision becomes a rule
    When a finding states how something should look or work everywhere
    Then it is written as a UX rule in 05 §8-§10 and, when it can be checked, as a check in check-ux-conventions.sh

  Scenario: Early feedback, no gates
    When a fix batch is done
    Then I see the updated images right away, and no 👤 checkpoint blocks the next round

  Scenario: Behaviour does not change
    Then no change alters what the app does, only how it looks or is arranged
    And the existing host tests stay green
```
