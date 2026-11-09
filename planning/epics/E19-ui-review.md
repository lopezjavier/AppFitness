# E19 · UI review

> **Status: DEFINED (product-owner, 2026-11-09, ADR-103), not planned.** Stories US-131 and US-123. The `epic-planner` breaks it into tasks. It runs after E18 so that every screen, including the ones E18 adds, is reviewed once. US-120…US-122 moved to E18.

## Intent

**Problem.** Reviewing a UI change today means building and installing the APK each time, which makes a screen-by-screen review with `ux-reviewer` slow. A few everyday frictions on Diario, Entreno, Trainer and Perfil have not been reviewed together.

**Outcome.** Any screen can be seen without installing the APK, and Diario, Entreno, Trainer and Perfil have been reviewed with the user, with the fix list agreed before any task is built.

**Non-goals.** A new look; new components; a Google Stitch redesign; changing the behaviour of the functional epics; a second UI-automation harness that needs debugging (`CLAUDE.md`, ADR-27).

**Approach chosen and why.** First a short spike to choose the tool, then the paired review using it. Candidate to try first: screenshots of every screen produced by the existing Robolectric host tests (for example Roborazzi), so `ux-reviewer` and the user look at pictures with no device; the Android Studio emulator is the fallback for flows that need real data. Compose `@Preview` and Maestro are considered and likely dropped (previews need fake data per screen; Maestro adds a harness that conflicts with ADR-27). The spike records its choice as an ADR. Epics E15-E18 build their screens only from existing design-system components, so this pass does not repeat work.

## Rules
`05` §8-§10; `04`; ADR-27, ADR-80, ADR-103.

## US-131 · See UI changes without installing the APK
**As** the user reviewing a screen
**I want** to see it without building and installing the app
**So that** a review is fast.

```gherkin
Feature: Review tooling

  Scenario: A tool is chosen
    Given the candidates were tried on at least one real screen
    Then the chosen tool and the reason are recorded, and the others are listed as dropped

  Scenario: Any main screen can be seen
    When I ask to see Diario, Entreno, Trainer or Perfil
    Then I get an image or a running view of it without installing the APK

  Scenario: It uses no personal data
    Then the tool uses fake data and no real account or secret

  Scenario: It is cheap to keep
    Then one command produces the images
```

## US-123 · Review of Diario, Entreno, Trainer and Perfil
**As** the user
**I want** the main screens reviewed
**So that** small frictions are removed.

```gherkin
Feature: Screen review

  Scenario: A list of fixes is agreed first
    Given ux-reviewer has reviewed Diario, Entreno, Trainer and Perfil
    Then the list of fixes is shown to me before any task is built

  Scenario: Fixes keep the style
    Then every fix uses existing design-system components and rules
```
