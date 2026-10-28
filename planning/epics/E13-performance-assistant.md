# E13 · Performance assistant → `v1.x` (version to be defined)

## Goal
A coach that designs a four-week training and nutrition block from the user's intention and preferences, sets the outcomes it can justify, and adjusts the plan with the user's data. It proposes; the user accepts. It never applies a change without consent.

## Scope
- Includes: confirmation of derived values, calorie and macro plan with outcomes, progression per exercise, weekly report, block creator with plan storage and adherence, recommendation history with outcome and deletion, deload suggestion, monthly AI budget, chat with tools (last, gated by the evaluation).
- Out of scope: medical advice or diagnosis, video analysis, meal plans, soreness as a signal, water and voice (Phase 3).

## Decisions
Base: `07-coach-design.md`. ADR-76 (coach rules), ADR-77 (provider and cost; changes NFR-02), ADR-78 (target weight retired as a user input; `activity_level` is the starting factor). ADR-79 (own tab). Amends ADR-09 (AI assistant no longer deferred), ADR-37 (tab set) and E5 (the target weight and distance to the goal become the coach's block outcome).

## Dependencies
E10 (reminders, weekly check-in), E11 (watch data), E12 (intention, preferences, notes, discomfort, `movement_pattern`).

## Before the breakdown
- Verify the sources marked *verify* in `07-coach-design.md` §10, the Anthropic API data policy (retention, training use) and current prices.
- 👤 The user creates the Anthropic account, sets its monthly spending limit and enters `ANTHROPIC_API_KEY` as a Supabase secret (never in the repo or the chat).

## Navigation
The coach has its own tab "Trainer" between "Entreno" and "Perfil" (ADR-79), added with the first E13 UI task.

## Architecture
- One Edge Function `coach` (JWT, RLS through the user's token; the API key only server-side). It receives a compact summary built in SQL (last 8 weeks at most), never raw tables.
- Models (ADR-77): Opus 5.5 for the block plan; Sonnet 5.5 for the weekly report and the chat. No model for derived values, progression, deload or trend: they are deterministic.
- Structured output (JSON schema), validated server-side against the rules of `07-coach-design.md` before it reaches the app; an invalid plan is retried once, then an error is shown. The model never writes to the database; accepted proposals are written by RPCs.
- Every call records its token usage and cost in `coach_usage`; the function refuses a call when the user's monthly budget (2 €) or the global one (5 €) would be exceeded.
- Coach suggestions are shown in the live session; they never rewrite the routine's planned values. "Actualizar automáticamente" (ADR-70) keeps copying what was performed.

## Tables
- `coach_derived_values` (`user_id`, `key`, `value`, `uncertainty`, `source` jsonb, `confirmed` boolean, `confirmed_at`).
- `coach_plans` (`user_id`, `block_start`, `block_weeks` = 4, `intention`, `nutrition` jsonb, `outcomes` jsonb, `method` jsonb, `status` = proposed | accepted | superseded | discarded, `data_version`).
- `coach_plan_sessions` (`plan_id`, `week` 1-4, `routine_id`, `session_id` nullable, `deload` boolean).
- `coach_recommendations` (`user_id`, `plan_id` nullable, `kind`, `payload` jsonb, `reason`, `input_summary` jsonb, `status` = pending | accepted | edited | discarded, `created_at`, `decided_at`).
- `coach_outcomes` (`recommendation_id`, `measured_at`, `weight_kg`, `e1rm_kg`, `adherence_pct`).
- `coach_reports` (`user_id`, `week_start`, `generated_on`, `figures` jsonb, `text` nullable).
- `coach_usage` (`user_id`, `month`, `input_tokens`, `output_tokens`, `cost_eur`).
- `coach_conversations` only with US-77.
- Every table cascades on account deletion (ADR-29).

---

## US-68 · Derived values confirmed by the user
**As** a user
**I want** the coach to show what it estimated about me and to ask me to confirm it
**So that** it never works from wrong data.

```gherkin
Feature: Confirmation of derived values

  Scenario: Values are shown with their source
    Given the coach estimated my activity level from my steps and my sessions
    When I open the plan proposal
    Then the card shows the activity level, the source data and the data that is missing

  Scenario: Starting value without history
    Given there is no Health Connect data and no training history
    When I open the plan proposal
    Then the activity level is the one in my profile
    And it is marked as provisional

  Scenario: Confirming
    When I tap "Confirmar" on the card
    Then the values are used in the plan
    And the confirmation is stored

  Scenario: Editing a value
    When I change the activity level and confirm
    Then the edited value is used
    And the change is stored

  Scenario: Unconfirmed values block the plan
    Given a value is not confirmed
    Then the plan shows it as provisional
    And the plan cannot be accepted until the card is confirmed

  Scenario: Re-asking when the data changes
    Given a confirmed value's new estimate is outside its uncertainty
    When the coach runs
    Then the card asks for confirmation again
```

## US-69 · Plan calories, macros and outcomes
**As** a user with an intention
**I want** the coach to set the calories, macros and the outcomes it can justify
**So that** the plan has a clear direction.

```gherkin
Feature: Plan calories, macros and outcomes

  Scenario: Weight loss plan
    Given the intention is "Perder grasa" and the estimated expenditure is 2600 kcal
    When the coach proposes the plan
    Then the calories are below expenditure
    And the weekly rate is between 0.5 % and 1 % of body weight
    And the protein is at least 1.6 g per kg of body weight
    And the fat is at least 20 % of the calories

  Scenario: Calorie floor
    Given the user is a woman and the deficit would give 1100 kcal
    When the coach proposes the plan
    Then the calories are 1200 kcal
    And the plan says the rate is slower because of the floor

  Scenario: Muscle gain plan
    Given the intention is "Ganar musculatura"
    When the coach proposes the plan
    Then the surplus rate is between 0.25 % and 0.5 % of body weight per week

  Scenario: Outcomes are optional
    Given the intention is "Ganar fuerza"
    When the coach proposes the plan
    Then the plan may set e1RM targets for the main lifts
    And it may set no body weight target
    And each outcome shows its reason and its deadline

  Scenario: Accepting writes the nutrition goal
    When the user accepts the plan
    Then the daily calorie and macro targets of Diario are the plan's

  Scenario: The user never enters target numbers
    Then no screen asks for a target weight, body fat or circumference
    And the weight screen shows the plan's weight outcome when there is one

  Scenario: Weight trend with weekly averages
    Given the user weighs daily
    When the coach estimates the trend
    Then it uses the slope of the weekly averages
    And it shows the uncertainty of that slope

  Scenario: Incomplete diary
    Given the diary is logged on 60 % of the days
    When the coach estimates the expenditure
    Then it says the estimate is not reliable
    And it does not propose a calorie change from it

  Scenario: Changing the calories within a block
    Given the plan's rate has been outside the trend's confidence interval for two weeks
    When the coach runs
    Then it proposes a change of at most 10 % of the calories
    And the change is applied only if accepted
```

**Technical notes**
- The 7 700 kcal/kg constant is used only without history (`07-coach-design.md` §4).
- A manual edit of the nutrition goal in Preferencias keeps working; it marks the plan's nutrition as edited by the user.
- `profiles.target_weight_kg` and its UI are removed in this story's task (ADR-78).

## US-70 · Progression per exercise
**As** a user in the gym
**I want** a suggested weight and reps for the next session of each exercise
**So that** I progress without guessing.

```gherkin
Feature: Progression suggestion

  Scenario: Raising the weight
    Given "Press banca" has a target of 6 to 8 reps
    And every working set reached 8 reps with RPE 8 or less last time
    When the user opens the exercise in the next session
    Then the suggested weight is the next available increment
    And the reason is shown with the sets used

  Scenario: Raising the reps
    Given "Press banca" was done at 80 kg with 6, 6 and 7 reps
    When the user opens the exercise in the next session
    Then the weight stays at 80 kg
    And the goal is one more rep

  Scenario: Lowering after two failed sessions
    Given the user missed the minimum reps in most sets in the last two sessions
    When the user opens the exercise
    Then the suggested weight is 5 % lower, rounded to the available increment

  Scenario: Without RPE
    Given the sets have no RPE
    When the coach suggests the next session
    Then only the reps rule applies

  Scenario: Open discomfort blocks increases
    Given "Press banca" has an open discomfort
    When the coach suggests the next session
    Then no load increase is suggested for "Press banca"
    And the fixed message asks the user to consult a professional if the pain persists or is strong

  Scenario: Severe discomfort blocks the exercise
    Given "Press banca" has an open severe discomfort
    When the coach suggests the next session
    Then no suggestion is made for "Press banca"
    And the fixed message is shown

  Scenario: Ignoring the suggestion
    When the user ignores the suggestion
    Then the routine's planned values stay the same
```

**Technical notes**
- Deterministic rules in SQL or TypeScript (no model call); they count sessions of the exercise, not calendar days.
- Reps in reserve are derived as 10 − RPE (ADR-76).

## US-71 · Weekly coach report
**As** a user
**I want** a weekly report that reads my week
**So that** I understand what happened.

```gherkin
Feature: Weekly coach report

  Scenario: Report after the weekly check-in
    Given the user completed the weekly check-in (E10)
    Then the coach's commentary is shown below the weekly summary

  Scenario: Report cites real data
    Given the week has volume, personal records, adherence, a weight trend and sleep data
    When the report is generated
    Then every figure in the text is in the stored summary

  Scenario: Invented figure is rejected
    Given the model writes a figure that is not in the summary
    When the output is validated
    Then the text is discarded
    And the report shows only the tables and figures

  Scenario: Short sleep is mentioned, not acted on
    Given the seven-day sleep average is below 6 hours
    When the report is generated
    Then the report mentions the sleep
    And no load or calorie is changed

  Scenario: Report on demand, once a day
    When the user taps "Informe semanal"
    Then the report for the current week is shown
    And it is not generated again the same day
```

**Technical notes**
- Figure validation compares every number in the text with the summary after normalising decimals and units; rounding to the shown precision is allowed.

## US-72 · Block creator
**As** a user
**I want** the coach to create my four-week block from my intention and preferences
**So that** I do not build routines by hand.

```gherkin
Feature: Block creator

  Scenario: Preview before saving
    Given the intention is "Ganar musculatura" and the place is "Gimnasio"
    When the coach proposes a four-week block
    Then the user sees each week with its routines and the exercises' reasons

  Scenario: Frequency and volume are shown
    Given the user trains 3 days a week
    When the block is shown
    Then each muscle group appears at least twice a week
    And the weekly sets per muscle group are shown

  Scenario: One training day
    Given the user set 1 day per week
    When the block is shown
    Then the week has one full-body routine

  Scenario: Frequency chosen by the coach
    Given the user did not set days or session length
    When the block is shown
    Then the plan shows the frequency it chose

  Scenario: Only available equipment
    Given the equipment is "Mancuernas" and "Peso corporal"
    When the block is shown
    Then every exercise uses only that equipment

  Scenario: Saving creates routines
    When the user taps "Guardar bloque"
    Then the routines are created with the existing save procedure in one folder for the block
    And the plan is linked to them

  Scenario: Nothing is saved without confirmation
    When the user leaves the preview
    Then no routine is created
```

## US-73 · Plan adherence
**As** a user
**I want** to see how much of the block I completed
**So that** I follow the plan and the coach can adjust it.

```gherkin
Feature: Plan adherence

  Scenario: Completing a planned routine
    Given week 1 of the plan lists "Push"
    When the user completes "Push" that week
    Then the workout is linked to that plan session

  Scenario: Weekly adherence
    Given week 1 has 4 planned sessions and 3 are completed
    Then the adherence of week 1 is 75 %

  Scenario: Missed sessions do not block the next week
    Given a planned session of week 1 was not done
    When week 2 starts
    Then week 2 is shown as planned
    And the missed session is not moved automatically
```

## US-74 · Recommendation history, outcome and deletion
**As** a user
**I want** to see whether the coach's proposals worked, and to delete that history
**So that** I trust it and control my data.

```gherkin
Feature: Recommendation history

  Scenario: Every decision is stored
    When the user accepts, edits or discards a proposal
    Then the decision is stored with the data it was based on

  Scenario: Outcome after a block
    Given a plan was accepted four weeks ago
    When the user opens the history
    Then the outcomes since then are shown next to the plan

  Scenario: Deleting the coach history
    When the user taps "Borrar historial del asistente" and confirms
    Then the plans, recommendations, outcomes, reports and conversations are deleted
    And the account, the routines and the training data stay

  Scenario: Deleting the account
    When the user deletes the account
    Then the coach data is deleted with it
```

## US-75 · Deload suggestion from strength data
**As** a user whose strength stopped improving
**I want** the coach to suggest an easier week
**So that** I recover and keep progressing.

```gherkin
Feature: Deload suggestion

  Scenario: No e1RM improvement for three weeks
    Given no main lift improved its e1RM in the last three weeks
    When the coach runs
    Then a deload is suggested for the next week

  Scenario: The deload
    Given a deload is suggested
    Then it keeps the same weights
    And it uses 40 % fewer working sets

  Scenario: Accepting or discarding
    When the user taps "Aceptar" or "Descartar"
    Then the week is changed only if accepted
```

**Technical notes**
- Deterministic trigger; thresholds are design decisions (`07-coach-design.md` §9).

## US-76 · Monthly AI budget
**As** the owner of the app
**I want** the coach's AI spending capped every month
**So that** it never costs more than I accepted.

```gherkin
Feature: Monthly AI budget

  Scenario: Usage is visible
    When the user opens the coach settings
    Then they see this month's AI usage and the monthly limit

  Scenario: User budget reached
    Given the user's AI usage this month reached 2 €
    When they ask for the weekly report
    Then the figures and tables are shown without the coach's text
    And a message says the monthly limit was reached until the next month

  Scenario: Deterministic features keep working
    Given the monthly limit was reached
    When the user opens an exercise in the live session
    Then the progression suggestion is shown

  Scenario: Global budget
    Given the AI usage of all users this month reached 5 €
    When any user asks for a model call
    Then the call is refused with the limit message
```

**Technical notes**
- Cost is computed from the usage returned by the API and the price table in the function; dollars are counted as euros (conservative).
- The Anthropic account spending limit is the backstop (👤).

## US-77 · Coach chat with tools (last, after the evaluation)
**As** a user
**I want** to ask the coach about my data
**So that** I understand my progress.

```gherkin
Feature: Coach chat

  Scenario: Answer cites data
    When the user asks "¿Por qué estoy estancado en press banca?"
    Then the answer cites the sessions it used

  Scenario: Proposals are cards, not changes
    When the answer contains a proposal
    Then it is shown as a card with "Aceptar" and "Descartar"
    And nothing is changed until the user accepts
```

**Technical notes**
- Enabled only after the evaluation below is met for the other stories. Tools are read-only queries over the user's data; proposals go through the same validators as US-69/US-72.

## Evaluation (not a user story)
- Cases written by the user, each with its correct answer: a plan for each intention, one training day, limited equipment, discomfort, missing data, rate inside and outside range, calorie floor, invented figure in a report.
- Acceptance criteria:
  - 100 % of discomfort cases: no load increase.
  - 100 % of report figures traceable to the summary.
  - Every plan meets the frequency (when days allow), volume, protein, fat and calorie-floor rules of `07-coach-design.md` §3-§4.
  - Report and chat answer under 30 s at the 95th percentile; the plan shows progress and finishes within the Edge Function limit (verify the limit).
- Evaluation runs are paid calls: the user approves each run. A function is not enabled until it meets its criteria.
