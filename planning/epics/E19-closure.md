# E19 · Closure

> **Status: DEFINED (product-owner, 2026-11-08, ADR-101), not planned.** Stories US-124…US-127. The `epic-planner` breaks it into tasks. After E19 the app is in maintenance mode: only bugfixes and the user's feedback create tasks.

## Intent

**Problem.** The app needs a safe end state, and the project's lessons should not be lost.

**Outcome.** The backend stays alive, the user's data can be exported, the repository is private, and a retro exists for future apps.

**Non-goals.** New features; a Play Store release; backups of other users' data (there are none).

**Approach chosen and why.** Small operational tasks, in this order: keep-alive, export, private repository, retro. The repository goes private last-but-one because the retro reads it. GitHub Actions are not expected to be used after this phase, so private-repo limits are not a concern (user, 2026-11-08).

**Security and data protection.** The export contains only the signed-in user's own rows, no secrets, and is written to the device with no upload. No secret enters the retro or the repo.

## Rules
ADR-17 (secrets), ADR-101; `CLAUDE.md` "Secrets".

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

```gherkin
Feature: Data export

  Scenario: Exporting from Perfil
    When I choose "Exportar mis datos" in Perfil
    Then a file with my diary, training and weight data is saved on my device

  Scenario: Only my data
    Then the file contains only my rows and no secret or other account's data

  Scenario: Nothing is uploaded
    Then the file is not sent anywhere

  Scenario: A failed export says so
    Given I am offline
    Then I see a clear message and no partial file is left
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
