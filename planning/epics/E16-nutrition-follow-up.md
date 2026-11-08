# E16 · Nutrition follow-up

> **Status: DEFINED (product-owner, 2026-11-08, ADR-101), not planned.** Stories US-112…US-115. The `epic-planner` breaks it into tasks. Deterministic, no language model.

## Intent

**Problem.** The weekly summary is training-led. When weight does not move the user cannot see whether eating adherence is the cause, and a stable weight can look like failure.

**Outcome.** Each week the user sees calories and protein against the target, and when progress stalls, one clear cause and one action.

**Non-goals.** Meal plans; refeeds; reverse dieting; medical advice or diagnosis; fibre, alcohol and micronutrient targets; a fifth tab or a second weekly summary; any model call.

**Approach chosen and why.** Extend the E14 weekly summary (US-98) and the "¿Por qué no progreso?" engine (US-105) with a nutrition variant, instead of building a parallel screen. The diet break is a proposal inside the cut plan, off by default, never automatic (`07` §4; Poon 2025 meta-analysis: same fat loss and lean mass, less hunger). Rules that `07` does not hold are recorded there by `fitness-scientist` first.

## Rules
`07` §4, §12.3; `04` §8; E14 US-98, US-99, US-105; ADR-98.

## US-112 · The nutrition section of the weekly summary
**As** a user following a nutrition plan
**I want** to see how I ate against the plan
**So that** I know whether my diet or my training explains my progress.

```gherkin
Feature: Weekly nutrition section

  Scenario: The section shows the week
    Given I logged food on 5 of 7 days
    When I open the weekly summary
    Then I see the logged days, the average kcal and protein against my target
    And my weight trend against the target rate

  Scenario: Missing records are named
    Given I logged fewer days than the usual threshold
    Then I see the "missing records" line and no conclusion about calories

  Scenario: The calorie changes of the week are listed
    Given a calorie change was applied or proposed this week
    Then the section shows it with its reason

  Scenario: There is one summary
    Then the nutrition section is part of the existing weekly summary, not a second screen
```

## US-113 · "¿Por qué no avanzo?" for nutrition
**As** a user whose weight is not moving as planned
**I want** one likely cause and one action
**So that** I know what to fix.

```gherkin
Feature: Why am I not progressing (nutrition)

  Scenario: The first cause only
    Given my weight trend is outside the target rate range for two weeks
    Then I see only the first cause that applies, in this order: logging, average calories, protein, weekend pattern, sleep or steps
    And one line with one action

  Scenario: No clear cause
    Given none of the checks applies
    Then I see "Sin causa clara" and the advice to keep the plan

  Scenario: No health advice
    Then the text never diagnoses or mentions a medical condition
```

## US-114 · A plateau is not a failure
**As** a user whose weight stopped changing
**I want** an honest explanation
**So that** I do not panic or cut calories needlessly.

```gherkin
Feature: Plateau explanation

  Scenario: Two stable weeks with good logging
    Given my weight trend was flat for two weeks and I logged 6 or more days each week
    Then I see "Meseta" with the trend and its uncertainty range
    And the text says it is not a failure

  Scenario: A plateau with poor logging is not called a plateau
    Given I logged fewer days than the threshold
    Then I see the logging cause of US-113 instead
```

## US-115 · A diet break offered in a cut
**As** a user in a cut
**I want** to choose whether to include a diet break
**So that** I can ease hunger without losing progress.

```gherkin
Feature: Diet break

  Scenario: Offered, off by default
    Given my block is a cut
    When I see the plan proposal
    Then I see "Descanso de dieta" as an option that is off

  Scenario: Turning it on
    When I turn it on and save
    Then week 4 of the block runs at maintenance calories and the plan shows it

  Scenario: Never automatic
    Given I did not turn it on
    Then no diet break is applied

  Scenario: It explains itself
    When I tap its help
    Then I read one sentence: it eases hunger and does not speed up fat loss
```
