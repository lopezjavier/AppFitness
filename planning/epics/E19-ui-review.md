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
