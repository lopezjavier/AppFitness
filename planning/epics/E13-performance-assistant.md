# E13 · Performance assistant → `v1.x` (version to be defined)

## Goal
A coach that designs a four-week training and nutrition block from the user's intention and preferences, sets the outcomes it can justify, and adjusts the plan with the user's data. It proposes; the user accepts. It never applies a change without consent.

## Scope
- Includes: confirmation of derived values, calorie and macro plan with outcomes, progression per exercise, weekly report, block creator with plan storage and adherence, recommendation history with outcome and deletion, deload suggestion, monthly AI budget, chat with tools (last, gated by the evaluation); weekly cardio and daily steps in the plan, and cardio as the last exercise of every routine of the block (US-79, ADR-85 proposed 2026-11-01; the coach plans no warm-up, E13 Q27); cardio machines as their own equipment group (US-80).
- Out of scope: medical advice or diagnosis, video analysis, meal plans, soreness as a signal, water and voice (Phase 3).

## Decisions
Base: `07-coach-design.md`. ADR-76 (coach rules), ADR-77 (provider and cost; changes NFR-02), ADR-78 (target weight retired as a user input; `activity_level` is the starting factor). ADR-79 (own tab). Amends ADR-09 (AI assistant no longer deferred), ADR-37 (tab set) and E5 (the target weight and distance to the goal become the coach's block outcome).

## Dependencies
E10 (reminders, weekly check-in), E11 (watch data), E12 (intention, preferences, notes, discomfort, `movement_pattern`).

## Before the breakdown
- [x] Verify the sources marked *verify* in `07-coach-design.md` §10, the Anthropic API data policy (retention, training use) and current prices (done 2026-10-29; results in `07` §3-§4, §10-§11).
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
    When I open "Antes de empezar"
    Then the details show the activity level, the source data and the data that is missing

  Scenario: Starting value without history
    Given there is no Health Connect data and no training history
    When I open "Antes de empezar"
    Then the activity level is the one in my profile
    And it is marked as provisional

  Scenario: Confirming
    When I tap "Crear plan" in "Antes de empezar"
    Then the values are used in the plan
    And the confirmation is stored

  Scenario: Editing a value
    When I change the activity level and confirm
    Then the edited value is used
    And the change is stored

  Scenario: Unconfirmed values block the plan
    Given a value was asked again after the plan was generated
    Then the plan shows a note that it is provisional
    And the plan cannot be accepted until the values are confirmed in "Antes de empezar", whose footer confirms them and requests the plan again

  Scenario: Re-asking when the data changes
    Given a confirmed value's new estimate is outside its uncertainty
    When the coach runs
    Then the card asks for confirmation again
```

**Technical notes**
- **Resolved 2026-10-29 (user, Q13):** an activity level edited on the card is stored only as the coach's confirmed value; Diario's calorie target keeps using `profiles.activity_level`.
- **Amended 2026-10-30 (user, E13 Q15/Q21, US-78):** the values are reviewed and confirmed in "Antes de empezar" (its "Crear plan" confirms them); the "Tus valores" card leaves the Trainer root and the plan proposal, and "Tus datos" (Trainer root row) shows them.
- **Labels (Q5):** "Confirmar" (accept the coach's derived values) and "Aceptar" / "Descartar" (decide on a coach proposal) get their own glossary entries in `05` §10; they are distinct from "Guardar" (commit an edit).

## US-69 · Plan calories, macros and outcomes

> **T-180 note (ADR-84):** the plan's expenditure is anchored on the capped 28-day intake and its target is checked against the intention's rate (`04` §8.7); the scenarios below hold for thin weight data too.
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

  Scenario: Protein distribution guidance
    Given the user weighs 75 kg
    When the coach proposes the plan
    Then the plan shows about 30 g of protein per meal over at least 4 meals
    And it is shown as guidance, not as a meal plan

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
- **Resolved 2026-10-29 (user):** any Preferencias save that changes the nutrition goal, including the recalculation after editing personal data, counts as a manual edit and marks the plan's nutrition "edited by the user"; it never silently restores the plan (Q8). Before the block exists a plan can be accepted for its nutrition alone; "Guardar bloque" (US-72) accepts the training part; both decisions are stored (Q9). The deterministic checks ("when the coach runs": re-asking derived values, the in-block calorie change, the deload) run on each open of the Trainer tab and after saving the weekly check-in, at most once a day, never in the background (Q6).

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

  Scenario: Each session uses its own rep range
    Given "Sentadilla" is planned at 3 to 5 reps on Monday and 8 to 10 reps on Thursday
    When the user opens "Sentadilla" on Thursday
    Then the suggestion uses the 8 to 10 range
    And it is based only on earlier sessions with that range

  Scenario: No rep range, no suggestion
    Given "Curl de bíceps" has no rep range in the routine
    When the user opens the exercise in the live session
    Then no progression suggestion is shown for it

  Scenario: Open discomfort blocks increases
    Given "Press banca" has an open discomfort of level 1 to 3
    When the coach suggests the next session
    Then no load increase is suggested for "Press banca"
    And the reason says the load is kept because of the open discomfort
    And no health message is shown

  Scenario: Severe discomfort blocks the exercise
    Given "Press banca" has an open discomfort of level 4 or 5
    When the coach suggests the next session
    Then no suggestion is made for "Press banca"
    And no health message is shown

  Scenario: Ignoring the suggestion
    When the user ignores the suggestion
    Then the routine's planned values stay the same
```

**Technical notes**
- Deterministic rules in SQL or TypeScript (no model call); they count sessions of the exercise, not calendar days.
- Reps in reserve are derived as 10 − RPE (ADR-76).
- **Rep range (E13 Q2, user 2026-10-29):** each routine exercise has an optional rep range (min-max), set in the routine editor with the same control as "Descanso" and "Objetivo RPE" (R-47) and filled by the block creator (US-72). It is per session: with undulating periodization ("Ganar fuerza", `07` §3) the same exercise can have different ranges on different days.
- **Discomfort (E13 Q1, user 2026-10-29; ADR-81):** levels 1-3 block load increases only (the reps rule and the 5 % drop still apply) and the reason states it neutrally; levels 4-5 give no suggestion. No health message.

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

  Scenario: Rest between sets
    When the block is shown
    Then the main multi-joint lifts rest 2 to 3 minutes
    And the isolation exercises rest 1 to 2 minutes
    And the saved routines keep those rests

  Scenario: Exercise order
    When the block is shown
    Then each session starts with its priority multi-joint lifts

  Scenario: Undulating periodization for strength
    Given the intention is "Ganar fuerza"
    When the block is shown
    Then the load and the reps of the main lifts vary between the sessions of a week

  Scenario: One rep range for other intentions
    Given the intention is "Ganar musculatura"
    When the block is shown
    Then each exercise keeps one rep range across the block

  Scenario: Cardio is not reduced
    Given the user logs cardio sessions or steps
    When the block is shown
    Then the block does not remove or shorten them
    And no text advises fewer steps

  # Cardio scenarios below: added 2026-11-01 (ADR-85 proposed, T-182/T-183); E13 Q26-Q30 resolved 2026-11-01.
  Scenario: Cardio closes every routine
    Given the plan has a weekly cardio target of 150 minutes
    And the block has 3 routines a week
    When the block is shown
    Then the last exercise of each routine is one cardio exercise of 50 minutes
    And it shows its minutes and its pace, such as "Bicicleta estática · 50 min · suave"
    And the cardio of each week adds up to 150 minutes
    And there is no day with only cardio

  Scenario: Minutes over the routine cap
    Given the plan has a weekly cardio target of 300 minutes
    And the block has 3 routines a week
    When the block is shown
    Then each routine ends with 60 minutes of cardio
    And the week shows the remaining 120 minutes as "por tu cuenta"

  Scenario: Cardio does not shorten the lifting
    Given "Duración de la sesión" is 60 minutes
    When the block is shown
    Then the lifting of each routine is the same as without cardio
    And each routine shows its total time with the cardio, such as "1 h 50 min"

  Scenario: Cardio uses the available equipment
    Given the place is "Casa" and the equipment is "Mancuernas" and "Peso corporal"
    When the block is shown
    Then every cardio exercise needs only that equipment, such as "Caminar a paso ligero"

  Scenario: Swapping the cardio keeps the minutes
    Given a routine ends with "Bicicleta estática · 50 min · suave"
    When the user swaps it
    Then the list shows only cardio exercises timed in minutes that fit the user's cardio equipment, plus walking and running
    And under the list it says "Cuentan los mismos minutos con cualquier cardio, a ritmo suave."
    When the user picks "Caminar a paso ligero"
    Then the routine ends with "Caminar a paso ligero · 50 min · suave" in every week

  Scenario: Any cardio swaps with any other
    Given the user's cardio equipment is "Bicicleta estática" and "Cinta"
    When the user opens the swap of any cardio exercise of the block
    Then the list has every other cardio exercise timed in minutes with that equipment, plus walking and running
    And the list is the same whichever cardio exercise it starts from
    And the minutes stay the same after any swap

  Scenario: A cardio set is minutes only
    When the block is shown
    Then each cardio exercise has one set with its planned minutes
    And it has no reps and no RPE

  Scenario: Training the cardio
    Given the user trains a routine of the block
    When the user reaches its cardio exercise
    Then the set shows the planned minutes
    And completing it starts no rest timer

  Scenario: The coach plans no warm-up
    When the block is shown
    Then no routine has a warm-up exercise or warm-up sets

  Scenario: Saving creates routines
    When the user taps "Guardar bloque"
    Then the routines are created with the existing save procedure in one folder for the block
    And each exercise keeps its rep range and rest
    And each cardio exercise keeps its planned minutes
    And the plan is linked to them

  Scenario: Nothing is saved without confirmation
    When the user leaves the preview
    Then no routine is created
```

**Technical notes**
- Range of motion stays a technique note in the exercise's reason (full range by default, `07` §3), not a computed rule.
- Main multi-joint lifts = exercises whose `movement_pattern` is squat, hinge, lunge, horizontal or vertical push or pull (Q3, user 2026-10-29).
- Leaving the preview keeps the proposal as `proposed`; it can be reopened from Trainer until a new one supersedes it; no discard dialog (Q10, user 2026-10-29).
- **Cardio (2026-11-01, ADR-85 proposed; E13 Q26-Q30):** the block reads the plan's weekly cardio target (US-79) and splits it evenly across the week's routines in whole 5-minute units (remainder to the first routines), at most 60 min per routine, the excess shown as "por tu cuenta". Cardio is the last exercise of every lifting routine; there are no cardio-only routines. It comes from the catalogue's `cardio` pattern within place and equipment, default bike, elliptical, incline treadmill walk, rower with machines and brisk walking without ("Caminar a paso ligero"), never running, air bike, ski erg, stair climber or burpee-style moves by default, and the most-logged cardio of the last 8 weeks when it fits the equipment; continuous "suave" only. Cardio candidates use the cardio equipment values of US-80. The cardio swap is any-to-any (user requirement, 2026-11-01): every cardio exercise timed in minutes within the user's cardio equipment, plus walking and running always, is an alternative of every other one at the same minutes, in every week of the routine; the alternatives are derived from category and equipment, never from the T-179 pairs table or a per-exercise list (`07` §3, Q28b). Cardio is outside "Duración de la sesión" (no lifting slot is removed); the preview shows each routine's total time. A cardio exercise has one planned set of minutes only (no reps, no RPE, no rest timer), stored in a new nullable planned duration on planned sets and shown with the existing duration metric types and set rows.
- **No warm-up (E13 Q27, user):** the coach does not plan, generate or mention warm-ups; the user adds warm-up sets himself.

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

  # Added 2026-11-01 (ADR-85 proposed, T-183).
  Scenario: Cardio minutes of the week
    Given week 1 plans 150 minutes of cardio
    And the user logged 100 minutes of cardio exercises that week
    Then week 1 shows "Cardio de la semana: 100 de 150 min"
    And only logged cardio counts
    And the missing 50 minutes are not moved to week 2

  Scenario: Steps of the week
    Given the steps target is 7000 a day
    And the user's watch reported an average of 7800 steps this week
    Then the week shows "Pasos: media 7.800 de 7.000"
    And a missed target moves nothing to the next week

  Scenario: No steps data
    Given there is no steps data this week
    Then the week shows "Sin datos de pasos"
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
    When the user taps "Eliminar historial del entrenador" and confirms
    Then the plans, recommendations, outcomes, reports and conversations are deleted
    And the account, the routines and the training data stay
    And the confirmed derived values and this month's AI usage stay

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
- **Resolved 2026-10-29 (user, Q3):** a main lift is an exercise whose `movement_pattern` is squat, hinge, lunge, horizontal or vertical push or pull, done in at least one session in each of the three weeks. A deload is suggested only inside an accepted block (it marks the next plan week). The 40 % cut rounds to the nearest whole set, at least 1 set per exercise. The deload is shown in the live session; the routines are never rewritten.

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
- The "coach settings" are a read-only "Uso de IA" screen opened from the Trainer root's ⋮ menu (Q7, user 2026-10-29).
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

## US-78 · "Antes de empezar": the data a plan or a block will use
*Added 2026-10-30 from the Checkpoint E13-B feedback (tasks T-175, T-176); E13 Q14-Q22 resolved by the user the same day. Runs before "Crear plan" (US-69) and "Crear bloque" (US-72) generate anything.*

**As** a user about to ask the coach for a plan or a block
**I want** a short summary of the data it will use, with anything wrong or missing flagged and where to fix it
**So that** I am never blind to what the coach reads, without an interview to fill in.

```gherkin
Feature: Before starting a plan or a block

  Scenario: A summary of the data the plan will use
    Given my profile has sex, birth date, height, a weigh-in and the training goal "Perder grasa"
    When I tap "Crear plan"
    Then "Antes de empezar" shows a summary of the data the plan will use
    And no AI call is made yet

  Scenario: Details on tap
    When I tap the summary in "Antes de empezar"
    Then the full details show each data with its value and where it comes from

  Scenario: Fixing a data in its own place
    When I tap "Objetivo de entrenamiento" in the details
    Then its existing editor in "Mi entrenamiento" opens
    And after saving I am back with the new value

  Scenario: Missing or wrong data is flagged
    Given my training goal is "Sin definir"
    When I open "Antes de empezar"
    Then the screen flags that the training goal is missing and where to fix it
    And the plan cannot start until it is fixed

  Scenario: Optional data left to the coach
    Given I did not set "Días por semana"
    When I open "Antes de empezar" for the block
    Then it says the coach will choose it
    And nothing is flagged

  Scenario: An old weigh-in is flagged, not blocking
    Given my last weigh-in is older than 14 days
    When I open "Antes de empezar"
    Then the screen shows its date with a caution note
    And the plan can start

  Scenario: Creating the plan confirms the values
    Given a derived value is provisional
    When I tap "Crear plan" in "Antes de empezar"
    Then the values shown are confirmed
    And the plan is requested with them

  Scenario: A new plan replaces the current one
    Given I have an accepted or pending plan
    When I open "Antes de empezar"
    Then the screen says the new plan will replace my current plan

  Scenario: The block has its own data
    Given a plan exists
    When I tap "Crear bloque"
    Then "Antes de empezar" summarises the place, the equipment, the days per week and the session length

  Scenario: Leaving changes nothing
    When I leave "Antes de empezar" without creating anything
    Then no plan or block is requested
    And no value is confirmed
```

**Technical notes**
- **Resolved 2026-10-30 (user, Q14-Q22):** a summary with details on tap, not an interview (Q14); the footer "Crear plan" / "Crear bloque" confirms the shown values and starts (Q15); a new plan over an accepted or pending one says it replaces it (Q15; copy such as "Sustituirá tu plan actual", ux-reviewer finalises); flag wording such as "Falta: elige tu objetivo en Mi entrenamiento" and "Sin definir · Lo elige el entrenador", not "complete it here" (Q16); detail rows open the existing editors and come back refreshed, the activity level keeps the coach sheet (Q17); a weigh-in older than 14 days shows a caution note, not blocking (Q18); two modes, before the plan and before the block (Q19); Trainer root option A (Q20); the derived-values card leaves the plan proposal (Q21).
- Edits stay in their existing places (Perfil → Mi entrenamiento, Preferencias → Datos personales, Peso, the coach's activity-level sheet of US-68). No new editor.
- A flagged item that the `coach` function would refuse (today's `missing` 422, T-174) keeps "Crear plan" from starting, so no paid call fails on it. One source: the screen and the function read the same server-side list (T-175).
- The Trainer root becomes a plan hub: pending proposals, "Tu plan" with its current week, the block, and a "Tus datos" row that replaces the "Tus valores" card (Q20 A).

## US-79 · Cardio and daily steps in the plan
*Added 2026-11-01 (user: "the app replaces a real trainer; if the trainer would plan cardio, the app must"). ADR-85 proposed; rules and sources in `07` §3 "Cardio prescription" and §10 (29-50, checked online 2026-11-01). E13 Q23-Q25 and Q29 resolved 2026-11-01. Task T-181; the block side is in US-72 (T-182, T-183) and the weekly tracking in US-73 (T-183).*

**As** a user with an intention
**I want** the coach to tell me how much cardio to do each week and how many steps to walk each day
**So that** my plan covers what a trainer would prescribe, not only lifting and food.

```gherkin
Feature: Cardio and daily steps in the plan

  Scenario: Targets next to the calories
    Given the intention is "Perder grasa"
    When the coach proposes the plan
    Then the plan shows "Actividad" with "Cardio · 150 min por semana · ritmo suave" and "Pasos · 7.000 al día" next to the calories and macros
    And each target shows its reason
    And the cardio reason explains "Ritmo suave: respiras más fuerte, pero puedes hablar con frases completas."
    And the cardio reason ends with "Si tienes una enfermedad del corazón, metabólica o renal, o notas síntomas al esforzarte, consulta antes con tu médico."

  Scenario Outline: The coach proposes 150 minutes for every intention
    Given the intention is "<intention>"
    When the coach proposes the plan
    Then the weekly cardio target is 150 minutes of moderate cardio

    Examples:
      | intention          |
      | Perder grasa       |
      | Ganar musculatura  |
      | Ganar fuerza       |
      | Recomposición      |
      | Mantenimiento      |

  Scenario: The user changes the cardio minutes
    Given the plan proposes 150 minutes of cardio a week
    When the user taps the "Cardio" row and changes it to 90 minutes
    Then the plan keeps 90 minutes
    And a note says "Está por debajo de lo que recomienda la OMS (150 min). Puedes dejarlo así."
    And nothing is blocked

  Scenario: Changing the cardio after the block is saved
    Given the block is saved with 50 minutes of cardio per routine
    When the user changes the cardio to 120 minutes from "Tu plan"
    Then the weekly cardio target is 120 minutes
    And the next block uses 120 minutes
    And the saved routines keep their 50 minutes

  Scenario: Steps never go down
    Given my average over the last 28 days is 11000 steps a day
    When the coach proposes the plan
    Then the steps target is 11000 a day
    And no text advises fewer steps

  Scenario: Steps floor without a watch
    Given there is no steps data
    When the coach proposes the plan
    Then the steps target is 7000 a day at any age

  Scenario: The steps target is not editable
    When the user opens the steps target
    Then it cannot be changed
    And a plain note says it comes from the user's average and the recommendation

  Scenario: Cardio does not change the calories
    Given the plan sets 150 minutes of cardio a week
    When the user changes it to 300 minutes
    Then the calories are the same as before
    And the plan says the expenditure will follow the logged data

  Scenario: Accepting keeps the targets
    When the user accepts the plan
    Then "Tu plan" in Trainer shows the weekly cardio target and the daily steps target
```

**Technical notes**
- Every figure is computed by the server's rules (`cardioRules`), never by the model; the model only explains (`07` §1). Cardio: 150 min/week proposed for every intention; for "Perder grasa" the sourced band is 150-300 (E13 Q23). Copy of the reasons: `07` §3 "What the user sees" (ux-reviewer finalises within the glossary).
- Steps target = max(28-day average rounded down to 500, 7 000) at every age (E13 Q24); the current average comes from `coach_summary` (`04` §7.8 steps per week). Not editable.
- The only new user input is the weekly cardio minutes (free, a plain note below 150, never blocks), edited in a form sheet from the "Cardio" row on the proposal and in "Tu plan" (E13 Q31); the steps target comes from the logged steps.
- The planned cardio does not change the calories (E13 Q25). The medical line closes the cardio help sheet (E13 Q29); no questionnaire, no blocking.

## US-80 · Cardio equipment
*Added 2026-11-01 (user decision: cardio machines leave `machine` and form their own category; details by the fitness-scientist, E13 Q28b; `07` §3 "Cardio equipment"). ADR-85 proposed. Task T-184, before the block uses it (T-182).*

**As** a user who trains where there are some cardio machines and not others
**I want** to say which cardio machines I have
**So that** the coach only proposes cardio I can do.

```gherkin
Feature: Cardio equipment

  Scenario: Cardio machines are their own group
    When the user opens "Equipamiento" in "Mi entrenamiento"
    Then a group "Cardio" under the lifting equipment shows "Bicicleta estática", "Cinta", "Elíptica", "Remo" and "Otra máquina de cardio"
    And it says "Sin máquinas de cardio, el entrenador te propone caminar a paso ligero."

  Scenario: Place seeds the cardio machines
    When the user picks "Gimnasio"
    Then the five cardio machines are selected
    When the user picks "Casa"
    Then no cardio machine is selected

  Scenario: Saved equipment gains nothing
    Given the user saved "Máquinas" before the cardio group existed
    When the user opens "Equipamiento"
    Then no cardio machine is selected
    And the coach does not treat "Máquinas" as cardio

  Scenario: "Antes de empezar" shows the cardio
    Given the user selected "Bicicleta estática" and "Cinta"
    When the user opens "Antes de empezar"
    Then the "Cardio" row reads "Bicicleta estática, Cinta"
    And tapping it opens "Mi entrenamiento"

  Scenario: No cardio machine means walking
    Given no cardio machine is selected
    When the user opens "Antes de empezar"
    Then the "Cardio" row reads "Sin máquinas · caminar"
    And the block proposes "Caminar a paso ligero" as cardio
```

**Technical notes**
- Five new `public.equipment` values `cardio_bike`, `treadmill`, `elliptical`, `rower`, `cardio_other` (precedent `band_long`, ADR-83); the catalogue's cardio machines are re-tagged from `machine`; `walking` and `running` stay `bodyweight`. `machine` means lifting machines only.
- The migration adds no cardio value to any saved equipment list; "a gym with none selected: everything" (`04` §9.4) is unchanged.

## Evaluation (not a user story)
- Cases written by the user, each with its correct answer: a plan for each intention, one training day, limited equipment, discomfort, missing data, rate inside and outside range, calorie floor, invented figure in a report.
- Acceptance criteria:
  - 100 % of discomfort cases: no load increase.
  - 100 % of report figures traceable to the summary.
  - Every plan meets the rules of `07-coach-design.md` §3-§4: frequency (when days allow), volume, intensity, rest between sets, exercise order, periodization (undulating only for "Ganar fuerza"), no reduction of cardio or steps, protein (incl. the distribution guidance), fat and the calorie floor.
  - Report and chat answer under 30 s at the 95th percentile; the plan shows progress and finishes within the Edge Function limit (150 s wall clock on the Free plan, checked 2026-10-29, `07` §11).
- Evaluation runs are paid calls: the user approves each run. A function is not enabled until it meets its criteria.
- **Resolved 2026-10-29 (user, Q4 B):** the plan and the report are usable at the checkpoints before the evaluation (the server validators enforce the hard rules on every output); the evaluation is the formal gate before the chat (US-77) and before the app is shared with other users.
