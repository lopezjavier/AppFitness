# E15 · Real life and goal switching

> **Status: DEFINED (product-owner, 2026-11-08, ADR-101), not planned.** Stories US-106…US-111. The `epic-planner` breaks it into tasks. Merged from "Real life" and "Goal switching" by the user on 2026-11-08 because both rest on "Bloque nuevo". No language model anywhere.

## Intent

**Problem.** The block assumes a perfect month. After a missed week or a break nothing tells the user what happened to the block, nobody welcomes them back, and a block that ends while the app is closed is easy to miss. Changing goal (for example from gaining muscle to cutting) is designed on paper (`07` §12.8 row 4) but has no flow.

**Outcome.** After any gap the user opens the app and sees what happened to the block and what to do, without repairing anything by hand. A finished block shows up in "Centro de avisos". The user switches goal in one confirmed step and gets a consistent new plan.

**Non-goals.** A "Recuperar" phase or any maintenance bridge; injury, illness or travel rules; "Adaptar el bloque" (life changes use "Bloque nuevo"); Android system notifications (ADR-73 stays: in-app only); a minimum phase length or a limit on switches; an optional maintenance block when going from a cut to a gain.

**Approach chosen and why.** Use the rules that exist (week counting `04` §9.9, the 14-day return rule `07` §12.5, discomfort levels `07` §7, the end-of-block review E14) and add only what the user sees: a pause label, a welcome-back notice, one in-app alert and one entry, "Bloque nuevo". The simplest option for goal switching is an immediate early review that opens the pre-filled builder; the evidence found no bridge or minimum length (`fitness-scientist`, 2026-11-08; Carbon and MacroFactor switch immediately). Evidence and open points are recorded in `07` by `fitness-scientist` before any task uses them.

## Rules
`07` §4, §7, §12.3, §12.5, §12.8 row 4; `04` §8.9, §8.10, §9.9; E10 (Centro de avisos); E14 (US-101, US-102, US-103); ADR-73, ADR-98, ADR-99, ADR-101.

## US-106 · A missed week pauses the block
**As** a user who could not train for a week
**I want** the block to wait for me
**So that** the plan does not count a week I did not live.

```gherkin
Feature: Missed weeks pause the block

  Scenario: A week with no sessions does not count
    Given my block is in week 2 and I did no session that week
    When the week ends
    Then the week does not count as a block week
    And the weekly summary says "Semana en pausa"
    And the block end date moves one week later

  Scenario: The block resumes at the next session
    Given my block is paused
    When I finish a session
    Then the block continues from the paused week

  Scenario: A pause does not change my nutrition plan
    Given my block is paused for a week
    Then my calories and macros stay as they were
    And no calorie proposal is generated from that empty week

  Scenario: The pause survives leaving the app
    Given my block is paused
    When I close the app and open it again
    Then the Trainer still shows the block as paused
```

## US-107 · Welcome back after a break
**As** a user who trained again after a long gap
**I want** a short explanation and a clear way forward
**So that** I return safely and without guessing.

```gherkin
Feature: Welcome back

  Scenario: The notice after 14 days
    Given I did no session for 14 days or more
    When I open the Trainer tab
    Then I see "Bienvenido de nuevo" with one line explaining that the first session does not raise the loads
    And I see the action "Bloque nuevo"

  Scenario: Loads follow the existing return rule
    Given I did no session for 14 days or more
    When I start my next session
    Then the suggested loads follow the 14-day return rule

  Scenario: No notice after a short gap
    Given I did no session for 6 days
    Then no welcome-back notice is shown

  Scenario: The notice goes away once handled
    Given the welcome-back notice is shown
    When I finish a session or start a new block
    Then the notice is no longer shown
```

## US-108 · A discomfort that does not go away
**As** a user with a logged discomfort
**I want** the coach to keep following it
**So that** I train around it without a medical judgement.

```gherkin
Feature: Discomfort follow-up

  Scenario: A mild discomfort stops load increases for a while
    Given I logged a discomfort of level 1-3 on an exercise
    Then the load of that exercise does not increase for 7 days
    And the exercise stays in my routine

  Scenario: A severe discomfort offers a swap
    Given I logged a discomfort of level 4-5 on an exercise
    Then no load suggestion appears for that exercise
    And a swap to a same-pattern exercise is offered and applied only when I tap

  Scenario: A discomfort that lasts 14 days shows a note
    Given a level 4-5 discomfort has been open for 14 days
    Then a note suggests seeing a professional
```

## US-109 · A finished block shows in "Centro de avisos"
**As** a user who forgot to open the app at the end of the block
**I want** the finished block to be waiting for me in the notice center
**So that** I do not miss starting the next one.

```gherkin
Feature: Block-end notice

  Scenario: The notice appears when the block ends
    Given the last plan week of my block has ended
    When I open the app
    Then "Centro de avisos" lists "Tu bloque ha terminado: revísalo"
    And its entry point shows the pending count

  Scenario: Opening the notice goes to the review
    When I tap the block-end notice
    Then the end-of-block review opens

  Scenario: The notice stays until the next block is saved
    Given I did not review my block
    When I close the app and open it again days later
    Then the notice is still listed

  Scenario: The notice leaves once I start the next block
    When I save the next block
    Then the notice is no longer listed

  Scenario: The notice can be silenced
    Given I turn off the block-end notice in Preferencias → "Avisos"
    Then it is not listed, and the Trainer card still shows the review
```

## US-110 · Switching goal with "Bloque nuevo"
**As** a user whose goal changed
**I want** to start a new block with a new goal in one confirmed step
**So that** my calories, protein and training follow the new goal.

```gherkin
Feature: Switch goal

  Scenario: The confirmation shows the calorie change
    Given I am in a block and I choose a different goal
    When I choose "Bloque nuevo"
    Then I see "Tus calorías pasan de {a} a {b} kcal: {fase}. El bloque actual sigue hasta que guardes el nuevo."

  Scenario: Nothing changes until I save the new block
    Given I confirmed the goal change
    Then my current block and calories stay as they were
    And the new calories apply only when I save the new block

  Scenario: Figures are recomputed from the new goal
    When I save the new block
    Then calories, rate, protein and outcomes come from the new goal
    And a cut to a gain starts from my measured maintenance plus the gain rate in one step

  Scenario: No forced bridge
    Given I switch from a cut to a gain
    Then no maintenance block is required or offered

  Scenario: The same entry serves any life change
    Given my days per week changed
    When I choose "Bloque nuevo"
    Then the pre-filled builder opens so I can adapt the block
```

## US-111 · A clean restart and weeks in phase
**As** a user who switched goal
**I want** the new phase to start fresh
**So that** old counters do not interfere.

```gherkin
Feature: Clean restart

  Scenario: Proposal counters restart
    Given I saved a new block with a new goal
    Then the calorie-proposal band, the 14-day clock and the "Ahora no" count start from zero

  Scenario: The deficit-week count restarts after 4 consecutive non-deficit weeks
    Given I cut, then spent 4 or more consecutive counted weeks in a non-cut block, then started a cut
    Then the deficit-week count starts again at the new cut

  Scenario: A short break from the cut only pauses the deficit-week count
    Given I cut, then spent fewer than 4 consecutive counted weeks in a non-cut block, then started a cut
    Then the deficit-week count continues from where it was (07 §12.9)

  Scenario: Weeks in the current phase
    When I open the plan details
    Then I see "Llevas {n} semanas en esta fase"
    And no warning and no block follow, whatever n is
```
