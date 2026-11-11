# E18 · Functional improvements

> **Status: DEFINED (product-owner, 2026-11-09, ADR-103), not planned.** Stories US-128, US-129, US-130, US-120…US-122 (moved from the former "E18 UI review"), US-132 (added by product-owner, 2026-11-10, ADR-105), and US-134 and US-135 (added by product-owner, 2026-11-10, ADR-106); US-133 was added and then withdrawn by T-273 (user decision, 2026-11-10). The `epic-planner` breaks it into tasks. It runs before E19 (UI review), so the screens it adds are reviewed once.

## Intent

**Problem.** The app still carries an AI provider it no longer uses and shows its cost. Past reports vanish once the next one arrives. The Trainer is crowded, and a food or an exercise cannot be looked up without starting to add it. Doubts about an exercise have nowhere to go.

**Outcome.** No Anthropic code, key, table or "Uso de IA" screen remains. Any past report can be opened. Diario and Entreno have a quick search. The Trainer shows the essentials with a Details screen. Favourites and my foods appear before Open Food Facts. Exercise details have a chat with a trainer-expert. A meal can be logged by typing a sentence, and personal foods unused for 30 days remove themselves.

**Non-goals.** Splitting a described meal into one food per item; saving a described meal without my review; voice input; deleting favourites, foods in a saved comida or foods used in the last 30 days; comparison views of reports; storing reminders; saving the chat; a disclaimer in the chat; chats outside an exercise (app help, "explain my data": not wanted, user 2026-11-09); a monthly report (ADR-66); any model in the numbers (ADR-98); a new look.

**Approach chosen and why.** US-128 goes first because it drops data in production. Reports history, quick search and the compact Trainer follow. The chat goes last, behind a 👤 check of Google's current terms for the Gemini API in Spain (the EEA may require paid services; if so, the paid tier with a low spending limit is the fallback, and the user decides). One Edge Function receives the exercise context and the question and returns the answer; the key stays server-side (ADR-17).

## Text meals (US-132, ADR-105; US-133 withdrawn by T-273)

**Problem.** Logging a meal when I am out means searching foods one by one or taking a photo, and the foods created that way are used once and pile up.

**Outcome.** I type what I ate in one sentence and get an editable draft with total kcal and macros. (US-133, automatic cleanup of unused foods, was withdrawn by T-273: the storage saved is negligible.) Past diary days do not change, and an entry whose food is gone opens read-only.

**Approach chosen and why.** Text reuses the photo flow: a new mode of the Gemini free-tier function returns one combined food as a draft in the usual form, with the energy check of ADR-104; the text is not stored or logged. The cleanup originally planned (US-133) was dropped by T-273. **Risk accepted:** Gemini estimates can be off (`07` ref 102), so the draft is always editable and shown as an estimate.

## Rules
`04` §6.1 (reminders stay computed, never stored); ADR-17, ADR-59, ADR-66, ADR-98, ADR-101, ADR-103, ADR-104, ADR-105, ADR-106; `05` §8-§10; ADR-27, ADR-80.

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

## US-132 · Log a meal by typing it
**As** the user
**I want** to describe a meal in a sentence
**So that** I can log it quickly when I am out.

```gherkin
Feature: Meal from text

  Scenario: Describe a meal
    Given I am adding food to a comida in Diario
    When I tap the AI icon next to the barcode icon, choose "Describir comida" in the "Añadir con IA" sheet and type "pechuga de pollo con ensalada, 2 cervezas y 4 calamares"
    Then I see a draft food whose name lists everything I typed
    And the draft has total kcal and macros for the whole meal, with one portion equal to the whole meal

  Scenario: Drinks count
    Given the text includes "2 cervezas"
    Then the draft includes their alcohol and energy

  Scenario: The draft is an estimate and editable
    Then it is marked as an estimate
    And I can change the name, amounts and nutrients before saving

  Scenario: The energy check still applies
    Given the draft's kcal and macros disagree
    Then I see the issue and "Guardar" is blocked until it is fixed or retried

  Scenario: Saving
    When I save the draft
    Then the food is added to my diary entry for that comida
    And it is also a personal food I can find in later searches

  Scenario: No answer
    Given I am offline or the service has no answer
    Then I see a clear message with "Reintentar"
    And the text I typed is kept

  Scenario: Empty or unusable text
    When the text is empty or contains no food
    Then I am told so and nothing is created

  Scenario: Privacy
    Then the text is sent only to the estimate service, and is not stored or logged
```

## US-133 · ~~Unused personal foods clean themselves up~~ WITHDRAWN (T-273, user decision 2026-11-10)
> Withdrawn: the storage saved is negligible. Kept for history, do not implement. Only "Past days do not change" remains as a safeguard (entry shown from its snapshot, view or delete only).

**As** the user
**I want** foods I no longer use to disappear
**So that** my food list stays small.

```gherkin
Feature: Unused food cleanup

  Scenario: A food unused for a month is removed
    Given a personal food has not been added to the diary for 30 days
    And it is not a favourite and is in no saved comida
    Then it is deleted automatically

  Scenario: Used, favourite or grouped foods stay
    Given a personal food was added in the last 30 days, or is a favourite, or is in a saved comida
    Then it is not deleted

  Scenario: Past days do not change
    Given a deleted food was in a diary entry from an earlier day
    Then that day's totals, entry names and amounts are unchanged
    And opening the entry still shows its details, and I can only view or delete it

  Scenario: A removed food is gone from search
    When a food is removed
    Then it no longer appears in food search
    And I can create or describe it again

  Scenario: It needs no open app or secret
    Then the cleanup runs on a schedule with the app closed
    And the mechanism stores no secret in versioned files

  Scenario: Other accounts are untouched
    Then each account's cleanup deletes only that account's own unused foods
```

## US-134 · Extend my current week
*Added by product-owner, 2026-11-10 (ADR-106); reworked 2026-11-11 (user, Checkpoint E18-C: the pencil belongs to the week in progress, not to the block). Reverses the "illness" non-goal of E15 for this one action; "Bloque nuevo" stays the way to change plan.*

**As** a user who is ill or busy
**I want** to say the day my current week now ends
**So that** I can finish my sessions without doubling up.

The action is a pencil button on the date row of the "Actual" week card in "Semanas del bloque" (a date picker opens; no separate "Ampliar bloque" entry). The days added to the week move the later weeks, the review and the block end by the same number, up to 14 days in total for the block. The line "El bloque termina el …" shows only once a week was paused.

Non-goals: pausing, back-dating, any change to loads, deloads or calories.

```gherkin
Feature: Extend the current week

  Scenario: Choosing the new last day of the week
    Given my current week ends on 12 Nov
    When I tap the pencil on the "Actual" week and pick 15 Nov
    Then the week shows its dates ending on 15 Nov

  Scenario: A session on the last day counts
    Given I extended my current week to 15 Nov
    When I finish a session on 15 Nov
    Then it counts in the week and in my week's adherence

  Scenario: My sessions stay to do
    Given my week has 4 sessions and I did 2
    When I extend the week
    Then the other 2 are still to do

  Scenario: Later weeks and schedules move too
    Given my block has weeks after the current one, a review or a next block planned
    When I extend the current week by 3 days
    Then each of them moves 3 days later

  Scenario: Changing my mind
    Given I extended my current week to 15 Nov
    When I tap the pencil and pick 12 Nov
    Then the week ends on 12 Nov again

  Scenario: The extension survives leaving the app
    Given I extended my current week
    When I close the app and open it again
    Then the Trainer still shows the new dates

  Scenario: No extension without a block
    Given I have no active block
    Then there is no pencil and no way to extend

  Scenario: The block end line is not shown while no week was paused
    Given I never paused a week
    Then the Trainer does not show "El bloque termina el …"
```

## US-135 · Small corrections to the food forms
*Added by product-owner, 2026-11-10 (ADR-106). Amends Q-E17-1 of E17 (alcohol typed in grams only).*

```gherkin
Feature: Food form corrections

  Scenario: A simple example in "Qué has comido"
    When I open "Describir comida"
    Then the example reads "Ej.: pechuga de pollo con patatas fritas"

  Scenario: Alcohol lives in its own section
    When I open the form to create or edit a food
    Then "Alcohol" is not between "Fibra" and "Proteínas"
    And it is a collapsible section below "Minerales"

  Scenario: Alcohol by volume, no formula
    Given the food is measured in ml
    When I open the Alcohol section
    Then I can enter grams per 100 ml or "grados" (% vol)
    And the app converts grados to grams (07 §4) and shows the result
    And the help does not ask me to calculate anything

  Scenario: A food measured in grams
    Given the food is measured in g
    Then Alcohol accepts grams only
```
