# E21 · Closure

> **Status: DEFINED (product-owner, 2026-11-08, ADR-101), not planned.** Stories US-124…US-127, plus US-149 (user request 2026-11-27, T-330). The `epic-planner` breaks it into tasks (breakdown PROPOSED 2026-11-27: T-327…T-329, `tasks/todo.md`; US-125 amended by ADR-114). After E21 the app is in maintenance mode: only bugfixes and the user's feedback create tasks.

## Intent

**Problem.** The app needs a safe end state, and the project's lessons should not be lost.

**Outcome.** The backend stays alive, the user's data can be exported, the repository is private, and a retro exists for future apps.

**Non-goals.** New features; a Play Store release; backups of other users' data (there are none).

**Approach chosen and why.** Small operational tasks. GitHub Actions are not expected to be used after this phase, so private-repo limits are not a concern (user, 2026-11-08). **Amended 2026-11-27 (user, ADR-114):** the order is keep-alive, private repository, export, retro. The export is a weekly GitHub Actions artifact, not an app feature. Artifacts of a public repository can be downloaded by any signed-in GitHub user, so the export ships only after the repository is private.

**Security and data protection.** The export contains only the user's own rows and no secrets. It goes only to a run artifact of the private repository (90 days), and the run log shows no row or secret. No secret enters the retro or the repo.

## Rules
ADR-17 (secrets), ADR-101, ADR-114; `CLAUDE.md` "Secrets".

## US-124 · The backend does not pause from inactivity
**As** the user
**I want** the backend to stay available
**So that** the app works when I come back after weeks.

```gherkin
Feature: Keep-alive

  Scenario: The project stays active
    Given I do not open the app for several weeks
    Then the Supabase project is not paused for inactivity

  Scenario: It needs no secret in the repo
    Then the mechanism stores no secret in versioned files
```

## US-125 · Export my data
**As** the user
**I want** a file with my data
**So that** I own it even if the app stops.

> Amended 2026-11-27 (user, ADR-114): the export is a GitHub Actions workflow of the private repository, not an app screen.

```gherkin
Feature: Data export

  Scenario: A weekly export
    Given the repository is private
    When the export workflow runs on its weekly schedule or is started by hand
    Then one JSON file with every row I own, including my diary, training and weight data, is attached to the run as an artifact kept for 90 days

  Scenario: Only my data
    Then the file contains only my rows and no secret, token or other account's data

  Scenario: Nothing is uploaded elsewhere
    Then the file goes only to the private repository's run artifact
    And the run log shows no row and no secret

  Scenario: A failed export says so
    Given the database cannot be read or the file is incomplete
    Then the run fails visibly and no partial artifact is left
```

## US-126 · The repository is private
**As** the user
**I want** the repository private
**So that** my project is not public.

```gherkin
Feature: Private repository

  Scenario: The repository is private
    Then the GitHub repository visibility is private

  Scenario: Nothing sensitive was ever public
    Given the repository was public
    Then the secret scanner passes and any exposed secret is rotated by the user before the switch

  Scenario: The docs say so
    Then CLAUDE.md and the README no longer say "Public repo"
```

## US-127 · Project retro
**As** the user
**I want** a retro of the whole project
**So that** I reuse what worked in future apps.

```gherkin
Feature: Retro

  Scenario: The retro exists
    Then a retro document covers the whole project: process, agents, planning, testing, tooling and decisions

  Scenario: It is reusable
    Then it lists what to repeat, what to avoid and a starter checklist for a new app

  Scenario: It contains no secrets
    Then it contains no secret, token or personal data
```

## US-149 · Food lists ordered by use
*(user request 2026-11-27, recorded by the PM; task T-330)*
**As** the user
**I want** "Habituales" and "Favoritos" ordered by how often I use each food in the comida I am adding to
**So that** the foods I usually eat there come first instead of an order that feels random.

```gherkin
Feature: Food lists ordered by use

  Scenario: Habituales follows the comida
    Given I logged some foods in "Desayuno" more often than others in the last 90 days
    When I add a food to "Desayuno" and open "Habituales"
    Then the foods I logged most often in "Desayuno" come first
    And a tie goes to the one I used most recently there, then to the name

  Scenario: Another comida has its own order
    When I add a food to "Cena" and open "Habituales"
    Then the list is ordered by my uses in "Cena", not in "Desayuno"

  Scenario: Favoritos has a fixed order
    When I open "Favoritos" while adding to a comida
    Then my favourites are ordered by uses in that comida, then uses overall, then the most recent use, then the name
    And a favourite I never logged comes last, by name

  Scenario: Old habits fade
    Given I have not logged a food for more than 90 days
    Then it is not in "Habituales"

  Scenario: Leaving the app keeps the list
    Given "Habituales" is open while adding to "Desayuno"
    When I leave the app and come back
    Then the same tab and the same order are shown
```
