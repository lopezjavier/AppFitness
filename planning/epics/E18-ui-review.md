# E18 · UI review

> **Status: DEFINED (product-owner, 2026-11-08, ADR-101), not planned.** Stories US-120…US-123. The `epic-planner` breaks it into tasks. It runs after the functional epics so that every screen is reviewed once.

## Intent

**Problem.** The Trainer is crowded and a few everyday paths are long (finding a food or an exercise just to see it).

**Outcome.** Diario, Entreno, Trainer and Perfil are easier to read and use, with the same style.

**Non-goals.** A new look; new components; a Google Stitch redesign; changing behaviour of the functional epics.

**Approach chosen and why.** Review each main screen against `05-design-system.md` §8-§10 with `ux-reviewer`, move the nutrition plan and the block to a Details screen, and add two small shortcuts. Epics E15-E17 build their screens only from existing design-system components, so this pass does not repeat work.

## Rules
`05` §8-§10; `04`; ADR-27, ADR-80.

## US-120 · A compact Trainer
**As** a user opening the Trainer
**I want** the essentials first
**So that** I see what matters without scrolling a long page.

```gherkin
Feature: Compact Trainer

  Scenario: The Trainer shows the essentials
    When I open the Trainer tab
    Then I see the current block status, the weekly summary and any pending notice

  Scenario: Details hold the plan and the block
    When I tap "Detalles"
    Then I see the nutrition plan and the block with their full content

  Scenario: Nothing is lost
    Then every figure shown today is reachable from the Trainer or its Details

  Scenario: State survives leaving the app
    Given I opened Details
    When I leave the app and come back
    Then I am where I was
```

## US-121 · Quick search of a food or an exercise
**As** a user who wants to see a food or an exercise
**I want** a search shortcut
**So that** I do not have to start adding it.

```gherkin
Feature: Quick search

  Scenario: The icon in Diario
    When I open Diario
    Then a magnifier icon is at the top right, to the left of the calendar icon
    And tapping it opens the food search

  Scenario: The icon in Entreno
    When I open Entreno
    Then the same icon opens the exercise search

  Scenario: View only
    When I open a result from the quick search
    Then I see its details and no "Añadir" button
```

## US-122 · Favourites and my foods first
**As** a user searching a food
**I want** my favourites and my own foods immediately
**So that** I do not wait for Open Food Facts.

```gherkin
Feature: Fast food search

  Scenario: Favourites and my foods appear at once
    When I type in the food search
    Then matching favourites and my foods are listed immediately from my data

  Scenario: Open Food Facts follows
    Given my foods do not answer
    Then Open Food Facts results are added afterwards
```

## US-123 · Review of Diario, Entreno and Perfil
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
