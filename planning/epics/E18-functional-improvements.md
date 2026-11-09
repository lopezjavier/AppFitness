# E18 · Functional improvements

> **Status: DEFINED (product-owner, 2026-11-09, ADR-103), not planned.** Stories US-128, US-129, US-130 and US-120…US-122 (moved from the former "E18 UI review"). The `epic-planner` breaks it into tasks. It runs before E19 (UI review), so the screens it adds are reviewed once.

## Intent

**Problem.** The app still carries an AI provider it no longer uses and shows its cost. Past reports vanish once the next one arrives. The Trainer is crowded, and a food or an exercise cannot be looked up without starting to add it. Doubts about an exercise have nowhere to go.

**Outcome.** No Anthropic code, key, table or "Uso de IA" screen remains. Any past report can be opened. Diario and Entreno have a quick search. The Trainer shows the essentials with a Details screen. Favourites and my foods appear before Open Food Facts. Exercise details have a chat with a trainer-expert.

**Non-goals.** Comparison views of reports; storing reminders; saving the chat; a disclaimer in the chat; chats outside an exercise (app help, "explain my data": not wanted, user 2026-11-09); a monthly report (ADR-66); any model in the numbers (ADR-98); a new look.

**Approach chosen and why.** US-128 goes first because it drops data in production. Reports history, quick search and the compact Trainer follow. The chat goes last, behind a 👤 check of Google's current terms for the Gemini API in Spain (the EEA may require paid services; if so, the paid tier with a low spending limit is the fallback, and the user decides). One Edge Function receives the exercise context and the question and returns the answer; the key stays server-side (ADR-17).

## Rules
`04` §6.1 (reminders stay computed, never stored); ADR-17, ADR-59, ADR-66, ADR-98, ADR-101, ADR-103; `05` §8-§10; ADR-27, ADR-80.

## US-128 · The app no longer uses Anthropic
**As** the user
**I want** every trace of the Anthropic model removed
**So that** the app is free again and shows no cost.

```gherkin
Feature: Remove Anthropic

  Scenario: The cost screen is gone
    When I open Perfil and the Trainer
    Then there is no "Uso de IA" entry, no monthly limit and no model selector

  Scenario: The key is gone
    Then no Anthropic key exists in Supabase or in the repository
    And the app and the Edge Functions contain no Anthropic client

  Scenario: Usage data is dropped
    Then the AI usage and budget data no longer exists in the database
    And the rest of my data is intact

  Scenario: Nothing I use changes
    Then the nutrition plan, the block builder, the folder review, the weekly summary and the food photo work as before

  Scenario: The decisions say so
    Then the free-only rule is restored in the scope, and the cost-cap decisions are marked superseded
```

## US-129 · Past reports can be reopened
**As** the user
**I want** to open previous reports
**So that** I can see how I progressed.

```gherkin
Feature: Report history

  Scenario: A report is kept
    Given a weekly summary or a block summary is produced
    Then it is stored with the figures it showed

  Scenario: Opening past reports
    When I open "Informes" from Centro de avisos
    Then I see my reports, newest first
    And opening one shows it exactly as it was shown then

  Scenario: One after another
    When I go back from a report
    Then I can open another one from the list

  Scenario: Reminders are not stored
    Then weight and measurement reminders and every other reminder stay computed and appear in no history

  Scenario: Opening a report removes its notice
    Given a report notice is listed in Centro de avisos
    When I open it
    Then it leaves the active list and stays in "Informes"

  Scenario: State survives leaving the app
    Given I opened a past report
    When I leave the app and come back
    Then I am where I was
```

## US-130 · Ask about an exercise
**As** a user looking at an exercise
**I want** to ask a trainer-expert my doubts
**So that** I understand it without searching elsewhere.

```gherkin
Feature: Exercise chat

  Scenario: The chat is next to "Cómo hacerlo"
    When I open an exercise's details
    Then I see a chat tab next to "Cómo hacerlo"

  Scenario: Asking a question
    When I send a question about the exercise
    Then I get an answer written as a trainer expert on that exercise

  Scenario: Only the exercise is shared
    Then only the exercise's name, muscles and instructions and my question are sent
    And no diary, weight, health or account data is sent

  Scenario: No history
    When I leave the exercise and come back
    Then the conversation starts empty

  Scenario: The app takes no actions
    Then the chat can only answer: it cannot change or read anything in the app

  Scenario: No answer
    Given the service is busy, its daily limit is reached, or I am offline
    Then I see a short message saying why there is no answer
    And the rest of the app works

  Scenario: The key stays server-side
    Then no model key is in the app or the repository
```

- 👤 Before the chat is built, the user checks Google's current terms for the Gemini API in Spain and decides free tier or paid tier with a low limit. The same check applies to `food-photo` (ADR-59).

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
