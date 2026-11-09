# E17 · Food data quality

> **Status: DEFINED (product-owner, 2026-11-08, ADR-101); breakdown PROPOSED (epic-planner, 2026-11-09: T-251…T-256 in `tasks/todo.md`).** Stories US-116…US-119. The macro/kcal tolerance is recorded in `07` §4 "Food energy check" by `fitness-scientist` (2026-11-09).

## Intent

**Problem.** Foods created by hand or estimated by AI are sometimes saved with macros that do not match their kcal, and Open Food Facts (OFF) foods behave differently from the user's own (only manual foods are editable, ADR-53).

**Outcome.** A wrong food is caught before it is saved, the foods already stored are audited and fixed, and every food the user uses is editable.

**Non-goals.** A verified or curated food database; changing the AI model; automatic corrections; changing past diary totals.

**Approach chosen and why.** A deterministic energy check (kcal against 4/4/9 kcal per gram of protein/carbohydrate/fat, with a tolerance owned by `fitness-scientist`) replaces another AI call. The audit is a list the user fixes by hand. OFF becomes a fallback for finding foods: adding one creates the user's personal copy, found first from then on and editable (ADR-101).

**Security and data protection.** Copies carry only the food's nutrition data and the user's own ownership; no other user's data is read or written; RLS stays on.

## Decisions (user, 2026-11-09)
- **Q-E17-1 · Alcohol.** Foods get an optional "Alcohol (g)" field (grams per 100 g/ml, 7 kcal/g) that the energy check counts. The user types grams by hand; the AI photo estimate must return it (0 when there is none); the Open Food Facts import converts OFF's % vol into grams (× 0.789). Rule and figures: `07` §4 "Food energy check".
- **Q-E17-2 · "Saved anyway".** A food saved with "Guardar igualmente" carries a per-food flag that keeps it out of the US-117 audit until its kcal, protein, carbohydrate, fat, fibre, polyols or alcohol change; the next save then checks it again. Rule: `07` §4 "Food energy check".
- **Q-E17-3 · Every reference moves to the copy.** Diary entries, favourites and saved comida items (hence default comidas) point to the personal copy: in the US-119 conversion and for every new add from US-118 on (diary, favourite star, comida item, barcode scan). No new reference to a shared OFF row is written.
- **Q-E17-4 · Hard block (supersedes Q-E17-2 and the audit of US-117, ADR-104).** Checkpoint E17 feedback (2026-11-09): no failing food may be saved by any path; the "saved anyway" flag, "Guardar igualmente" and the audit UI are removed; the AI estimate must be consistent or show the issue with a retry that sends the previous estimate and the reason; the PM fixes the user's existing wrong foods (asking when unsure). Tasks T-257…T-258 and a follow-up checkpoint.

## Rules
`07` §4 "Food energy check (E17, US-116/US-117)" (recorded 2026-11-09); `04` food rules; `03` `foods`; ADR-53 (amended by ADR-101), ADR-59.

> **Amended by ADR-104 (2026-11-09):** US-116's warning becomes a block (no "Guardar igualmente"), US-117's audit UI and the flag are removed; scenarios on them are superseded by T-257/T-258.

## US-116 · A food whose macros do not match its kcal is flagged
**As** a user adding a food
**I want** a warning before saving
**So that** I do not log wrong data.

```gherkin
Feature: Macro and kcal check

  Scenario: A mismatch warns before saving
    Given I create a food with 500 kcal and macros that add up to about 200 kcal
    When I save
    Then I see a warning that the kcal and the macros do not match
    And I can fix the numbers or save anyway

  Scenario: The check covers every way a food is created
    Then it applies to manual foods, edits, AI dish estimates, label scans and OFF copies

  Scenario: A small gap is accepted
    Given the gap is within the tolerance (alcohol or fibre can explain it)
    Then no warning is shown

  Scenario: Saving anyway is allowed
    When I choose to save despite the warning
    Then the food is saved as I entered it
```

## US-117 · An audit of the foods I already have
**As** a user with many foods
**I want** a list of the ones that look wrong
**So that** I fix them once.

```gherkin
Feature: Food audit

  Scenario: The list shows foods that fail the check
    When I open the food audit
    Then I see my foods whose macros and kcal do not match, with the gap

  Scenario: Each food opens its editor
    When I tap a food in the list
    Then its editor opens with the numbers

  Scenario: Nothing changes on its own
    Then no food is modified until I save an edit

  Scenario: A fixed food leaves the list
    When I save a corrected food
    Then it is no longer listed
```

## US-118 · An OFF food becomes my food
**As** a user adding a food found in Open Food Facts
**I want** my own copy
**So that** I find it quickly and can edit it.

```gherkin
Feature: OFF as a fallback

  Scenario: My foods come first
    When I search a food
    Then my foods and favourites are listed before any Open Food Facts result

  Scenario: Open Food Facts is queried when my foods do not answer
    Given none of my foods match
    Then Open Food Facts results are listed

  Scenario: Adding an OFF food creates my copy
    When I add an OFF food
    Then a personal copy is created with its name, brand, barcode, macros and micronutrients
    And the next search finds it from my foods

  Scenario: No duplicate
    Given I already have a copy of that barcode
    Then no second copy is created

  Scenario: The copy is editable
    When I open the copy
    Then I can edit it like a manual food
```

## US-119 · The OFF foods I already use
**As** a user with past diary entries
**I want** old OFF foods converted without changing my history
**So that** everything is editable and totals stay the same.

```gherkin
Feature: Existing OFF foods

  Scenario: Used OFF foods become personal copies
    Given my diary has entries with OFF foods
    When the conversion runs
    Then each used OFF food has a personal copy and my entries point to it
    And my favourites and saved comida items that used it point to the copy too (amended 2026-11-09, Q-E17-3)

  Scenario: Totals do not change
    Then the kcal and macros of every past day are the same as before

  Scenario: Only my data is touched
    Then no other account's rows are read or changed
```
