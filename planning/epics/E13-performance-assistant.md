# E13 · Performance assistant → `v1.x` (version to be defined)

## Goal
A coach the user can trust for years, like a real trainer: it gets to know the user (intake), designs four-week training and nutrition blocks from the intention, the preferences and the current phase of the year, sets the outcomes it can justify, adjusts the small things on its own (load, calories inside a safe band, deloads) visibly and reversibly, and at the end of every block reviews what happened with the user and proposes the next block and any phase change. The user accepts new blocks and phase changes. *(Rewritten 2026-11-01, user: E13 was not going in the right direction; the long-term follow-up is the gap. ADR-86…ADR-89; design `07` §1-§4, §7, §8, §12.)*

## Scope
> **Moved to E14 (user, 2026-11-03: "we don't have a proper block yet"):** the block follow-ups — plan weeks that follow the training, the end-of-block review and phase changes, the automatic changes ("Entrenador"), injury follow-up and the health note, life changes (US-83…US-88 follow-up parts, US-90, US-91, US-92 and the automatic parts of US-69, US-70, US-73, US-75). Their headings below say "→ E14 (backlog)"; [E14-block-follow-up.md](E14-block-follow-up.md) holds the backlog. E13 finishes with the block (US-72, US-93…US-96), the report (US-71), history (US-74), the evaluation and the chat (US-77). **Removed 2026-11-03 (user, E13 Q56):** the minimal phase "Mínimo" and "Tengo poco tiempo".

- Includes: intake "Para conocerte" and the free text the coach reads (US-81, US-89); confirmation of derived values; calorie and macro plan with outcomes; phases over the year (cut, gain, maintenance, recomposition, minimal; US-82; regain removed 2026-11-02, deferred to Phase 3); the end-of-block review and the next block, with optional per-muscle priorities (US-83, US-84); one "Entrenador" behaviour: progression, calorie changes in the safe band and deloads applied on their own with "Deshacer" (US-85, US-86); progression per exercise; weekly report; block creator with plan storage and adherence; breaks, the minimal plan and coming back (US-88); training around an injury, graded return and one data-triggered health note (US-87, US-90); life changes mid-block: "Revisar el bloque" with "Adaptar el bloque", "Bloque nuevo" or "Solo unos días", goal change, new exercises, kept swaps, turning 65 (US-91, US-92, `07` §12.8); recommendation history with outcome and deletion; monthly AI budget; chat with tools (last, gated by the evaluation); weekly cardio and daily steps in the plan, and cardio as the last exercise of every routine of the block (US-79, ADR-85; the coach plans no warm-up, E13 Q27); cardio machines as their own equipment group (US-80). **Added 2026-11-03 (T-202, ADR-93, ADR-94):** the model builds the whole block and one routine checker validates it (US-72 amended); starting a block from scratch (2-3 split options) or from a routine folder (US-93); removing a block without a new plan (US-94); reviewing any routine folder with the coach (US-95); favourite exercises (US-96).
- Out of scope: medical advice, diagnosis or treatment, generic health disclaimers, video analysis, meal plans, reverse dieting and refeeds (`07` §4), soreness, pump or readiness questionnaires, illness rules (`07` §12.5), a coaching-mode selector (ADR-88), water and voice (Phase 3).

## Decisions
Base: `07-coach-design.md`. ADR-76 (coach rules), ADR-77 (provider and cost; changes NFR-02), ADR-78 (target weight retired as a user input; `activity_level` is the starting factor). ADR-79 (own tab). ADR-81 (discomfort level), ADR-84 (expenditure anchor), ADR-85 (cardio). **2026-11-01:** ADR-86 (intake, phases, end-of-block review, breaks), ADR-87 (free text to the model, grounding), ADR-88 ("Entrenador": automatic changes with "Deshacer"; amends ADR-76), ADR-89 (no generic disclaimers, persistent-discomfort note, injury follow-up; amends ADR-81 and ADR-85). **2026-11-02:** ADR-90 (per-user AI cap 3 €; amends ADR-77), ADR-91 (minimal intake, no "Recuperar"), ADR-92 (equipment split, alternatives). **2026-11-03 (T-202, proposed):** ADR-93 (the model builds the block; one routine checker; folder review), ADR-94 (block lifecycle: two starting points, transparent block, "Eliminar bloque", favourites). Amends ADR-09 (AI assistant no longer deferred), ADR-37 (tab set) and E5 (the target weight and distance to the goal become the coach's block outcome). User decisions E13 Q32-Q43 (`09-open-questions.md`).

## Dependencies
E10 (reminders, weekly check-in), E11 (watch data), E12 (intention, preferences, notes, discomfort, `movement_pattern`).

## Before the breakdown
- [x] Verify the sources marked *verify* in `07-coach-design.md` §10, the Anthropic API data policy (retention, training use) and current prices (done 2026-10-29; results in `07` §3-§4, §10-§11).
- 👤 The user creates the Anthropic account, sets its monthly spending limit and enters `ANTHROPIC_API_KEY` as a Supabase secret (never in the repo or the chat).

## Navigation
The coach has its own tab "Trainer" between "Entreno" and "Perfil" (ADR-79), added with the first E13 UI task.

## Architecture
- One Edge Function `coach` (JWT, RLS through the user's token; the API key only server-side). It receives a compact summary built in SQL (last 8 weeks at most), never raw tables.
- Models (ADR-77): *(2026-11-04, ADR-99: no model for the block or the split; the block plan no longer uses Opus.)* Opus 5.5 for the block plan; Sonnet 5.5 for the weekly report and the chat. No model for derived values, progression, deload or trend: they are deterministic. **2026-11-01 (ADR-86, ADR-87):** the model also extracts the intake profile from the user's free text and writes the end-of-block review proposal (choosing among server-eligible phase and block options); phase triggers, the safe band and every number stay deterministic; the model reads the free-text block "Lo que el entrenador sabe de ti" and only describes features and data that exist (server-validated).
- **2026-11-03 (ADR-93):** *(Superseded 2026-11-04, ADR-99, T-219, user: block creation is deterministic with no model call; see `07` §3 "Block builder".)* the training block is built by the model (split, exercises, sets, rep ranges, target RPE, rests, supersets and drop sets under time conditions) from the user's folders, history, previous blocks, favourites, discomforts and free text; one deterministic routine checker (`04` §9.6) validates it and also reviews any routine folder. Cardio stays server-computed. The minimal phase ("Mínimo", "Tengo poco tiempo") is removed (user, 2026-11-03, E13 Q56).
- Structured output (JSON schema), validated server-side against the rules of `07-coach-design.md` before it reaches the app; an invalid plan is retried once, then an error is shown. The model never writes to the database; accepted proposals are written by RPCs.
- Every call records its token usage and cost in `coach_usage`; the function refuses a call when the user's monthly budget (3 €, ADR-90; 2 € until 2026-11-02) or the global one (5 €) would be exceeded.
- ~~Coach suggestions are shown in the live session; they never rewrite the routine's planned values.~~ **Amended 2026-11-01 (ADR-88):** inside an accepted block the load progression, the calorie changes in the safe band and the deloads apply on their own, each with its reason and "Deshacer" / "Cancelar descarga"; outside a block the progression stays a suggestion in the live session. "Actualizar automáticamente" (ADR-70) keeps copying what was performed.

## Tables
- `coach_derived_values` (`user_id`, `key`, `value`, `uncertainty`, `source` jsonb, `confirmed` boolean, `confirmed_at`).
- `coach_plans` (`user_id`, `block_start`, `block_weeks` = 4, `intention`, `nutrition` jsonb, `outcomes` jsonb, `method` jsonb, `status` = proposed | accepted | superseded | discarded, `data_version`).
- `coach_plan_sessions` (`plan_id`, `week` 1-4, `routine_id`, `session_id` nullable, `deload` boolean).
- `coach_recommendations` (`user_id`, `plan_id` nullable, `kind`, `payload` jsonb, `reason`, `input_summary` jsonb, `status` = pending | accepted | edited | discarded, `created_at`, `decided_at`).
- `coach_outcomes` (`recommendation_id`, `measured_at`, `weight_kg`, `e1rm_kg`, `adherence_pct`).
- `coach_reports` (`user_id`, `week_start`, `generated_on`, `figures` jsonb, `text` nullable).
- `coach_usage` (`user_id`, `month`, `input_tokens`, `output_tokens`, `cost_eur`).
- `coach_conversations` only with US-77.
- **Added 2026-11-03 (T-202):** `exercise_favorites` (US-96), the folder reviews with their proposed changes (US-95); a new `movement_pattern` value `shoulder_abduction`.
- **Added 2026-11-01 (names final in each task's plan):** the intake (US-81: fields, free text, confirmed flags), the free-text exclusions (US-89), the phase and its options on `coach_plans` (US-82), the block reviews with "¿Cómo te ha ido?" and the per-muscle priorities (US-83, US-84), the automatic changes with their previous value for "Deshacer" (US-85, US-86), the graded-return state per exercise and the dismissed health notes (US-87, US-90).
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

  # Added 2026-11-01 (ADR-86, E13 Q32; replaces "without history: beginner"). T-185, T-186.
  Scenario: Experience is the higher of what I said and my history
    Given I said in "Para conocerte" that I have lifted consistently for 3 years
    And the app has 10 of my sessions
    When I open "Tus datos"
    Then my experience is "Avanzado"
    And it reads "Según lo que nos contaste (provisional)"

  Scenario: My history takes over when it is higher
    Given I said I have lifted for 4 months
    And the app history classes me as intermediate
    Then my experience is "Intermedio"
    And it is not marked as "Según lo que nos contaste"

  Scenario: My known maintenance is used until my data is enough
    Given I said my maintenance is 2400 kcal
    And my diary does not yet have 20 logged days in the last 28
    When the coach estimates my total expenditure
    Then it uses 2400 kcal marked "Según lo que nos contaste (provisional)"
    When my diary and weigh-ins reach the thresholds of the measured estimate
    Then the measured value replaces it
    And "Tus datos" says once "Ahora usamos tus datos reales: tu gasto medido es de 2.450 kcal."
```

**Technical notes**
- **Resolved 2026-10-29 (user, Q13):** an activity level edited on the card is stored only as the coach's confirmed value; Diario's calorie target keeps using `profiles.activity_level`.
- **Amended 2026-10-30 (user, E13 Q15/Q21, US-78):** the values are reviewed and confirmed in "Antes de empezar" (its "Crear plan" confirms them); the "Tus valores" card leaves the Trainer root and the plan proposal, and "Tus datos" (Trainer root row) shows them.
- **Labels (Q5):** "Confirmar" (accept the coach's derived values) and "Aceptar" / "Descartar" (decide on a coach proposal) get their own glossary entries in `05` §10; they are distinct from "Guardar" (commit an edit).
- **Amended 2026-11-01 (ADR-86, `07` §2):** intake answers are provisional priors shown as "Según lo que nos contaste (provisional)" until measured data replaces them; a returning lifter keeps the experience class; the self-reported classes are under 6 months beginner, 6 months to under 2 years intermediate, 2 years or more advanced; known cutting calories are context only, never used in a calculation. A new "Situación" value (first time, continuous, returning after a break, returning after an injury) is shown in "Tus datos"; the app sets "returning after a break" after 28 days without a completed session.
- **Amended 2026-11-02 (user, Checkpoint E13-D feedback; T-198, R-53):** the intake keeps only what changes a figure or reaches the model: training years ("Tiempo entrenando"), the known maintenance ("Calorías de mantenimiento") and the free text. The situation (with "Recuperar", deferred to Phase 3), cutting calories, the injured area and the pre-break bests are removed; the scenarios above are unchanged.

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

  # Amended 2026-11-01 (ADR-88, E13 Q36): the change applies on its own inside the safe band. T-193.
  Scenario: Changing the calories within a block
    Given the plan's rate has been outside the trend's confidence interval for two weeks
    And the last two weeks have clean data
    When the coach runs
    Then the calories change by at most 10 % on their own
    And the Trainer tab shows the change with its reason, such as "Bajamos 150 kcal: llevas 2 semanas perdiendo menos de lo previsto."
    And "Deshacer" restores the previous calories

  # Added 2026-11-01 (ADR-86, E13 Q41). T-193.
  Scenario: Not enough data, no calorie change
    Given this week has 4 complete diary days or 2 weigh-ins
    When the coach runs
    Then the calories do not change
    And the coach says "Esta semana faltan registros: no cambiamos tus calorías."

  Scenario: A partial day is not counted
    Given a diary day has less than 40 % of the median of my logged days of the last 28 days
    When the coach estimates the intake
    Then that day is left out, not counted as eaten nor as a logged day

  Scenario: Automatic changes stay inside the safe band
    Given the calories of this block were already changed by 10 % on their own
    When the rate is still outside the trend
    Then no further change applies on its own
    And the change waits for the end-of-block review

  Scenario: The deficit is at most 500 kcal
    Given the phase is "Definición"
    And the rate of 0.5 % would need a deficit of 650 kcal
    When the coach proposes the plan
    Then the deficit is 500 kcal
    And the plan says the rate is slower to protect muscle
```

**Technical notes**
- The 7 700 kcal/kg constant is used only without history (`07-coach-design.md` §4).
- A manual edit of the nutrition goal in Preferencias keeps working; it marks the plan's nutrition as edited by the user.
- `profiles.target_weight_kg` and its UI are removed in this story's task (ADR-78).
- **Resolved 2026-10-29 (user):** any Preferencias save that changes the nutrition goal, including the recalculation after editing personal data, counts as a manual edit and marks the plan's nutrition "edited by the user"; it never silently restores the plan (Q8). Before the block exists a plan can be accepted for its nutrition alone; "Guardar bloque" (US-72) accepts the training part; both decisions are stored (Q9). The deterministic checks ("when the coach runs": re-asking derived values, the in-block calorie change, the deload) run on each open of the Trainer tab and after saving the weekly check-in, at most once a day, never in the background (Q6).
- **Amended 2026-11-01 (ADR-86, ADR-88):** the plan's calories follow its phase (US-82, `07` §4 "Phase calories", 500 kcal deficit cap in a cut); the in-block change applies on its own inside the safe band of `07` §4 with "Deshacer" and only on clean data (`07` §2); a change outside the band waits for the end-of-block review (US-83). The "Aceptar" / "Descartar" calorie-change card of T-162 is retired for these changes (T-193).

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

  # Amended 2026-11-01 (ADR-88, E13 Q36, Q44 recommendation): inside an accepted block the change applies on its own. T-192.
  Scenario: Outside a block the suggestion stays a suggestion
    Given the routine is not part of an accepted block
    When the user ignores the suggestion
    Then the routine's planned values stay the same

  Scenario: Inside a block the progression applies on its own
    Given "Press banca" belongs to a routine of my accepted block
    And every working set reached the top of its range at RPE 8 or less
    When I finish the session
    Then the next session of "Press banca" is planned at the next available increment
    And it shows the reason, such as "Subimos a 62,5 kg: la última vez hiciste 12 repeticiones en todas las series."

  Scenario: Undoing an automatic change
    Given the coach raised "Press banca" to 62,5 kg on its own
    When I tap "Deshacer"
    Then the next session of "Press banca" goes back to 60 kg
    And the undo is stored

  Scenario: Coming back after 14 days or more
    Given I did not complete any session for 14 days or more
    When I train "Press banca" again
    Then the first session keeps the last load, with no increase
    And one session that misses the bottom of the range lowers the load 5 %
```

**Technical notes**
- Deterministic rules in SQL or TypeScript (no model call); they count sessions of the exercise, not calendar days.
- Reps in reserve are derived as 10 − RPE (ADR-76).
- **Rep range (E13 Q2, user 2026-10-29):** each routine exercise has an optional rep range (min-max), set in the routine editor with the same control as "Descanso" and "Objetivo RPE" (R-47) and filled by the block creator (US-72). It is per session: with undulating periodization ("Ganar fuerza", `07` §3) the same exercise can have different ranges on different days.
- **Discomfort (E13 Q1, user 2026-10-29; ADR-81):** levels 1-3 block load increases only (the reps rule and the 5 % drop still apply) and the reason states it neutrally; levels 4-5 give no suggestion. No health message (except the persistent-discomfort note of US-90, ADR-89).
- **Amended 2026-11-01 (ADR-88):** "the suggestion never rewrites the routine" now holds only outside an accepted block. Inside one, the change is written to the next session of that exercise (E13 Q44 recommendation A: the block routine's planned sets, marked with the reason) and "Deshacer" restores the previous values; ADR-70 is unchanged for the user's own edits. The 14-day return rule is `07` §12.2.

## US-71 · Weekly coach report → E14 (backlog)

> **Moved to E14 (user, 2026-11-04):** the weekly report, with its US-76 "User budget reached" report fallback, the US-90 note in the report and E13 Q49, leaves E13 like the other follow-ups (task T-166, E14 backlog). The Gherkin below is kept unaligned for the E14 re-plan (it still assumes a model-written report).
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

  # Added 2026-11-01 (ADR-86, ADR-87, ADR-89; `07` §12.6; E13 Q49 recommendation). T-166.
  Scenario: Three parts: what went well, what got in the way, one change
    When the report is generated
    Then the text has three parts: one thing that went well, one obstacle, and one change for next week
    And the change is one action for the user, never a change of the plan

  Scenario: The report reads what I wrote
    Given my check-in note says "Esta semana he dormido fatal por el trabajo"
    And the line is not left out in "Lo que el entrenador sabe de ti"
    When the report is generated
    Then the coach can take that note into account
    And no figure or trigger comes from the note

  Scenario: The report lists what the coach changed
    Given the coach raised two loads and lowered the calories this week on its own
    When the report is generated
    Then it lists each automatic change with its reason

  Scenario: Missing data is said, not guessed
    Given this week has fewer than 5 complete diary days
    When the report is generated
    Then it says "Esta semana faltan registros: no cambiamos tus calorías."

  Scenario: Only what exists
    Given the model writes about a feature or a screen the app does not have
    When the output is validated
    Then that text is discarded

  Scenario: A persisting severe discomfort
    Given a level 4 discomfort on "Sentadilla" is still unresolved after 14 days
    When the report is generated
    Then it shows the persistent-discomfort note of US-90 once

  # Added 2026-11-02 (`07` §12.8 rows 8 and 10). T-166.
  Scenario: No report for an empty week
    Given a week with no session, no weigh-in and no diary day
    Then no weekly report is generated for it
    And no AI call is made

  Scenario: A goal reached
    Given my weight outcome was reached this week
    When the report is generated
    Then it says so in one factual line
    And nothing in the plan changes until the end-of-block review
```

**Technical notes**
- Figure validation compares every number in the text with the summary after normalising decimals and units; rounding to the shown precision is allowed.
- **Amended 2026-11-01:** the report shows the weekly average weight and trend against the target rate, logged diary days, sessions done against planned, open discomforts and the loads changed automatically that week (`07` §12.6). Its text reads the free-text block (US-89) and is grounded (ADR-87): every figure is in the summary, every feature it names exists. The "one change" is chosen among server-built candidates (E13 Q49 recommendation).

## US-72 · Block creator
> **Superseded 2026-11-04 (ADR-99, T-219, user):** the coach no longer builds the block; the user picks the split and a routine type per day and fills slots in a builder until 100 % (`07` §3 "Block builder"). Scenarios below that rely on a model-built block (what changed since the last block, exercises left out for discomfort, reasons per exercise, lateral raise or knee extension as required, the checker retry) are void once the builder task merges; cardio, planned weights from history, no warm-up and saving stand. Gherkin to be rewritten by the epic-planner with the builder task.
**As** a user
**I want** the coach to create my four-week block from my intention and preferences
**So that** I do not build routines by hand.

```gherkin
Feature: Block creator

  Scenario: Preview before saving
    Given the intention is "Ganar musculatura" and the place is "Gimnasio"
    When the coach proposes a four-week block
    Then the user sees each week with its routines and the exercises' reasons

  # Amended 2026-11-03 (T-202, ADR-93): per-muscle frequency is soft; H2 and H1 are the hard rules.
  Scenario: Frequency and volume are shown
    Given the user trains 3 days a week
    When the block is shown
    Then the week has at least 2 lifting routines
    And the weekly sets per muscle group are shown
    And chest, back, shoulders, quads, hamstrings and glutes each have 10 to 20 weekly sets

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

  # Amended 2026-11-03 (T-202, ADR-93, `07` §3 H7): main lifts first only for "Ganar fuerza".
  Scenario: Exercise order
    Given the intention is "Ganar fuerza"
    When the block is shown
    Then each session starts with its priority multi-joint lifts

  # Void 2026-11-03 (T-202, ADR-93): "Undulating periodization for strength" (now an option the model may use,
  # never required) and "One rep range for other intentions" (the model chooses ranges within H4) are removed.

  # Scenarios below added 2026-11-03 (T-202, ADR-93; `07` §3 "Complete routine"). Tasks T-205 (backend), T-206 (UI).
  Scenario: The block fits me, not a template
    Given two users with 4 days a week and different routines, history and favourites
    When the coach builds each block
    Then the splits and exercises can differ
    And each split and each exercise has its reason

  Scenario: Side deltoid and knee extension
    Given the intention is "Ganar musculatura" and I train 2 or more days a week
    And my equipment allows a lateral raise and a knee extension
    When the block is shown
    Then the week has at least one lateral raise exercise
    And at least one knee extension exercise

  Scenario: Rep ranges and target effort
    Given the intention is "Ganar musculatura"
    When the block is shown
    Then every rep range is between 6 and 20
    And every exercise shows a target RPE of 7, 8 or 9
    And no set is planned to failure

  Scenario: Strength rep ranges
    Given the intention is "Ganar fuerza"
    When the block is shown
    Then the main lifts use ranges between 3 and 8 reps
    And the other exercises use ranges between 6 and 20

  Scenario: Supersets and drop sets only when time is short
    Given "Duración de la sesión" fits the routine without them
    When the block is shown
    Then no routine has supersets or drop sets
    Given "Duración de la sesión" is too short for the planned sets
    When the block is shown
    Then supersets pair exercises of different muscles
    And a drop set is only the last set of an isolation exercise

  Scenario: Every routine fits the session time
    Given "Duración de la sesión" is 60 minutes
    When the block is shown
    Then the lifting of each routine, with its sets, reps and rests, fits in 60 minutes

  Scenario: Not enough time for the minimum volume
    Given I train 1 day a week for 45 minutes
    When the block is shown
    Then it says "Por debajo de lo recomendado"
    And the block is still created

  Scenario: A block that breaks a hard rule is never shown
    Given the coach's first answer has no lateral raise where one is required
    When the server checks it
    Then the coach is asked once more
    And if it fails again the user sees an error with "Reintentar" and nothing is stored

  Scenario: Planned weights come from my history
    Given I trained "Press banca" with 60 kg last week
    When the block includes "Press banca"
    Then its planned weight comes from my own sets
    And an exercise I never trained has no planned weight

  # Superseded 2026-11-04 (ADR-99): no model prose; the builder shows no "changes vs last block".
  Scenario: What changed since my last block
    Given I had a block before
    When the new block is shown
    Then it says what changed against the last block and why

  # Superseded 2026-11-04 (ADR-99, user): discomfort is not an input of block creation.
  Scenario: Exercises with a severe discomfort are left out
    Given "Press militar" has an unresolved level 4 discomfort
    When the block is shown
    Then "Press militar" is not in the block

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
- **Amended 2026-11-01 (ADR-86, ADR-89):** the first block follows the cold-start rules of `07` §12.2 and its phase (US-82); every later block is proposed by the end-of-block review (US-83) with the volume and priority rules of `07` §12.4 (US-84); exercises flagged at level 4-5, or loading the intake's injured area, are left out and can be put back (US-87). "Guardar bloque" of a new block ends the previous one.
- **Amended 2026-11-03 (T-202, ADR-93, ADR-94):** *(Superseded 2026-11-04, ADR-99, T-219, user: block creation is deterministic with no model call; see `07` §3 "Block builder".)* the model builds the whole block (split, exercises, sets, rep ranges, target RPE, rests, supersets and drop sets under time conditions) and the routine checker (`04` §9.6, hard H1-H7) validates it; the server keeps cardio (§9.11, unchanged by the model) and the minimal-phase block (§9.3). The intake injured area no longer exists (ADR-91): exclusions come from unresolved level 4-5 discomforts. The block starts from scratch or from a folder (US-93). Preview swaps stay limited to alternatives, with favourites first (US-96).

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

  # Added 2026-11-01 (ADR-86, E13 Q40): breaks follow the plan, not the calendar. T-189.
  Scenario: A week without training does not count
    Given I am in week 2 of my block
    And I completed no session from Monday to Sunday of the next calendar week
    When I open Trainer
    Then my block is still in week 2
    And the end of the block moves one week later

  Scenario: Coming back resumes where I left off
    Given my block was paused for two calendar weeks
    When I complete a session of the block
    Then that session counts for the week where I left off
    And no missed session is moved or counted as failed
```

**Technical notes**
- **Amended 2026-11-01 (ADR-86):** the earlier rule "week `n` = days `block_start + 7(n-1)` to `+6`" (T-165) is replaced: plan week `n` is the `n`-th calendar week with a completed session since `block_start` (`04` §9.9). The end-of-block review (US-83) and the deload evaluation (US-75, US-86) use these weeks.

## US-74 · Recommendation history, outcome and deletion → E14 (backlog)

> **Moved to E14 (user, 2026-11-04):** E13 ends at Checkpoint E13-K; the history and its deletion (T-167) are re-planned in E14. Gherkin kept unaligned.
**As** a user
**I want** to see whether the coach's proposals worked, and to delete that history
**So that** I trust it and control my data.

```gherkin
Feature: Recommendation history

  # Amended 2026-11-01 (ADR-88): automatic changes and their undo are stored too.
  Scenario: Every decision is stored
    When the user accepts, edits or discards a proposal
    Or the coach applies a change on its own and the user keeps it or taps "Deshacer"
    Or the user accepts a phase change or keeps the current phase
    Then the decision is stored with the data it was based on

  Scenario: Outcome after a block
    Given a plan was accepted four weeks ago
    When the user opens the history
    Then the outcomes since then are shown next to the plan

  Scenario: Deleting the coach history
    When the user taps "Eliminar historial del entrenador" and confirms
    Then the plans, recommendations, automatic changes, block reviews, outcomes, reports and conversations are deleted
    And the account, the routines and the training data stay
    And the confirmed derived values, "Para conocerte" and this month's AI usage stay

  Scenario: Deleting the account
    When the user deletes the account
    Then the coach data is deleted with it
```

## US-75 · Deload from strength data
*Amended 2026-11-01 (ADR-88, E13 Q36-Q37): the triggered deload applies on its own and can be cancelled; the scheduled deload and starting one are US-86. Task T-193 (fix of the accept-first behaviour of T-165).*
**As** a user whose strength stopped improving
**I want** the coach to give me an easier week
**So that** I recover and keep progressing.

```gherkin
Feature: Deload from strength data

  Scenario: No e1RM improvement for three weeks
    Given no main lift improved its e1RM in the last three weeks of my block
    When the coach runs
    Then the next week becomes a deload week on its own
    And Trainer shows "Semana de descarga: mismos pesos, menos series. Llevas 3 semanas sin mejorar en los básicos." with "Cancelar descarga"

  Scenario: The deload
    Given the next week is a deload week
    Then it keeps the same weights
    And it uses 40 % fewer working sets

  Scenario: Cancelling the deload
    Given the next week is a deload week
    When I tap "Cancelar descarga"
    Then the week goes back to normal
    And the cancellation is stored
```

**Technical notes**
- Deterministic trigger; thresholds are design decisions (`07-coach-design.md` §9).
- **Resolved 2026-10-29 (user, Q3):** a main lift is an exercise whose `movement_pattern` is squat, hinge, lunge, horizontal or vertical push or pull, done in at least one session in each of the three weeks. A deload is suggested only inside an accepted block (it marks the next plan week). The 40 % cut rounds to the nearest whole set, at least 1 set per exercise. The deload is shown in the live session; the routines are never rewritten.
- **Amended 2026-11-01:** the three weeks are plan weeks (paused calendar weeks do not count, US-73); no "Aceptar" / "Descartar": the deload applies on its own and "Cancelar descarga" reverts it (ADR-88).

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
    # Amended 2026-11-02 (ADR-90): the per-user cap is 3 €. T-185.
    Given the user's AI usage this month reached 3 €
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

## US-77 · Coach chat with tools (last, after the evaluation) → E14 (backlog)

> **Moved to E14 (user, 2026-11-04):** with the evaluation and the history (T-169); the user evaluates the future of the AI trainer there. Re-plan note: the shared grounding validator was to come from T-166 (also E14). Gherkin kept unaligned.
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

  # Added 2026-11-01 (ADR-87, E13 Q42).
  Scenario: Only what exists
    When the user asks "¿Puedes registrar mi frecuencia cardiaca en la sesión?"
    Then the answer describes only features and data the app has
    And any text about a feature the app does not have is discarded before it is shown

  Scenario: The chat reads what I told the coach
    Given "Para conocerte" says I am coming back from a shoulder injury
    When I ask "¿Por qué no tengo press militar en el bloque?"
    Then the answer can use that context
```

**Technical notes**
- Enabled only after the evaluation below is met for the other stories. Tools are read-only queries over the user's data; proposals go through the same validators as US-69/US-72.
- **Amended 2026-11-01 (ADR-87, ADR-88):** the chat reads the free-text block (US-89) and its output passes the same grounding validator as the report. Chat proposals stay cards that need "Aceptar" (they are not among the automatic changes of ADR-88).

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

  # Added 2026-11-02 (user, Checkpoint E13-D feedback; R-53, R-55). T-198.
  Scenario: The intake rows
    When I open "Antes de empezar" for the plan
    Then it shows the rows "Tiempo entrenando", "Calorías de mantenimiento" and "Para conocerte"
    And each row opens its own editor with only that value
    When I save one of them
    Then I am back with the new value on that row and the other rows unchanged

  Scenario: The intake rows for the block
    When I open "Antes de empezar" for the block
    Then it shows "Tiempo entrenando" and "Para conocerte" only
```

**Technical notes**
- **Resolved 2026-10-30 (user, Q14-Q22):** a summary with details on tap, not an interview (Q14); the footer "Crear plan" / "Crear bloque" confirms the shown values and starts (Q15); a new plan over an accepted or pending one says it replaces it (Q15; copy such as "Sustituirá tu plan actual", ux-reviewer finalises); flag wording such as "Falta: elige tu objetivo en Mi entrenamiento" and "Sin definir · Lo elige el entrenador", not "complete it here" (Q16); detail rows open the existing editors and come back refreshed, the activity level keeps the coach sheet (Q17); a weigh-in older than 14 days shows a caution note, not blocking (Q18); two modes, before the plan and before the block (Q19); Trainer root option A (Q20); the derived-values card leaves the plan proposal (Q21).
- Edits stay in their existing places (Perfil → Mi entrenamiento, Preferencias → Datos personales, Peso, the coach's activity-level sheet of US-68). No new editor. *(Amended 2026-11-02: the intake values have no other place, so each intake row opens its own small editor, the same one "Tus datos" opens; R-55.)*
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
    # Amended 2026-11-01 (ADR-89, E13 Q39 reverses Q29): no medical line. T-188.
    And the cardio reason has no medical or health disclaimer

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
- The planned cardio does not change the calories (E13 Q25). ~~The medical line closes the cardio help sheet (E13 Q29)~~ (removed 2026-11-01, ADR-89, E13 Q39); no questionnaire, no blocking.

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

---

*US-81…US-90 added 2026-11-01 (replan, user: "the app must replace a real trainer I can trust long-term; the follow-up is the gap, phases over the year the biggest"). Design: `07` §1-§4, §7, §8, §12 (fitness-scientist, sources §10 51-66). Decisions: ADR-86…ADR-89, E13 Q32-Q43 (user); E13 Q44-Q49 open with the recommendation the tasks use.*

## US-81 · "Para conocerte": the coach gets to know me
*Tasks T-185 (backend), T-186 (UI); **rewritten 2026-11-02** (user, Checkpoint E13-D feedback) and built by T-198. ADR-86 (amendment proposed: ADR-91 in `tasks/plan.md`), E13 Q32, Q46 (superseded by the user 2026-11-02), Q50 (recommendation), R-53, R-55.*

**As** a user starting with the coach
**I want** to tell the coach the few things it cannot know from my data, and anything else in my own words
**So that** my first plan starts from who I am, without answering questions nothing uses.

```gherkin
Feature: Getting to know the user

  # Superseded 2026-11-02 (user, Checkpoint E13-D): "Asked once before the first plan" (it opened on its own, with "Ahora no"),
  # "The fields" (injured area, pre-break bests and cutting calories removed), "Writing in my own words" with "Continuar"
  # filling the form, "The coach never guesses" and "Only valid values" (no extraction any more), "My past bests never set
  # my loads" and "Cutting calories are only context" (fields removed). The 2026-11-01 text is in git history (#433).

  Scenario: Nothing opens on its own
    Given I have no "Para conocerte" saved
    When I tap "Crear plan" for the first time
    Then "Antes de empezar" opens directly
    And "Para conocerte" opens only when I tap it

  Scenario: Each row opens its own editor
    When I tap "Tiempo entrenando", "Calorías de mantenimiento" or "Para conocerte" in "Antes de empezar" or "Tus datos"
    Then only that value's editor opens
    And every value is optional

  # "Situación" (returning after a break) was removed with "Recuperar" on 2026-11-02 (user; deferred to Phase 3).
  Scenario: Only questions that change something
    Then the coach asks only my time training, my maintenance calories if I know them, and a free text
    And it asks nothing that no figure or coach request uses

  Scenario: Writing in my own words
    When I tap "Para conocerte"
    Then I see a free text "Cuéntanos lo que quieras sobre tu entrenamiento" of up to 500 characters with its "?"
    When I write "Entrené 4 años, lo dejé hace 8 meses por una lesión de hombro" and tap "Guardar"
    Then my text is saved as I wrote it
    And no field is filled from it

  Scenario: Saving works
    Given I filled any of the values
    When I tap "Guardar"
    Then it is saved and I am back with the new value

  Scenario: What I said is provisional
    Given I saved my time training or my maintenance calories
    When I open "Tus datos"
    Then the values that come from it read "Según lo que nos contaste (provisional). Lo cambiaremos por tus datos reales cuando haya suficientes."

  Scenario: Editing it later
    When I change my time training from "Tus datos"
    Then the change is saved and used by the next plan or block
```

**Technical notes**
- **Rewritten 2026-11-02 (user):** nothing is extracted from the text by a model (the `intake_extract` kind is removed); the user types the values. Three inputs and what they change: training years → the experience class (max with the history, `07` §2: gain rate, days); known maintenance → the expenditure prior until the measured value qualifies, then the handover note (US-68); the free text → sent verbatim, as context, with the plan and block requests (US-89). Removed: the situation (with "Recuperar", deferred to Phase 3), injured area, pre-break bests, cutting calories (R-53).
- *(Superseded 2026-11-02:)* ~~One model call (`intake` kind, ADR-77 budget) extracts the structured profile from the free text as JSON; the server validates allowed values, plausible ranges and catalogue lifts and returns a form; the user confirms.~~ ~~The extraction costs about 0,01-0,03 € once per intake edit.~~

## US-82 · Phases over the year
*Tasks T-187 (backend), T-188 (UI). ADR-86, E13 Q34-Q35. Rules: `07` §4 "Phase calories", §12.3.*

**As** a user who trains for years
**I want** each block to run in a clear phase (definición, volumen, mantenimiento…) that fits my goal and my situation
**So that** my plan has a direction over the year, like with a real trainer.

```gherkin
Feature: Phases

  Scenario Outline: First phase from the intention
    # Amended 2026-11-02 (user: "Recuperar" removed, deferred to Phase 3; T-198, T-200): "and I am not returning after a break" dropped.
    Given my intention is "<intention>"
    When the coach proposes my first plan
    Then the phase is "<phase>"

    Examples:
      | intention          | phase          |
      | Perder grasa       | Definición     |
      | Ganar musculatura  | Volumen        |
      | Recomposición      | Recomposición  |
      | Mantenimiento      | Mantenimiento  |
      | Ganar fuerza       | Mantenimiento  |

  # Void 2026-11-02 (user: "Recuperar" removed entirely, deferred to Phase 3; T-200): the scenarios
  # "Returning after a break starts with 'Recuperar' at maintenance" and "Regain with a muscle goal" are removed
  # (2026-11-01 text in git history, #433).

  Scenario: The phase is part of the plan
    When I open the plan proposal or "Tu plan"
    Then the phase is shown with a one-sentence reason from my own data
    And its "?" explains what the phase does with my calories and my training

  Scenario: Choosing how fast to cut
    Given the phase is "Definición"
    When I change the rate in the plan proposal
    Then I can pick from 0.5 % to 1 % of body weight per week
    And the deficit never goes above 500 kcal a day

  Scenario: An optional diet break
    Given the phase is "Definición"
    When I turn on "Con semana de descanso"
    Then weeks 1 to 3 of each block are in deficit and week 4 is at maintenance
    And it says "Opcional: la última semana de cada bloque comes lo que gastas. En los estudios se pierde la misma grasa y se pasa menos hambre, pero la definición dura más."
    And only the deficit weeks count towards the length of the cut

  # Void 2026-11-03 (user, E13 Q56: "how would I know I won't have time for a full month? no real usage, drop it"):
  # "The minimal phase for busy times", "Older users in the minimal phase" and "Cardio in the minimal phase" are removed
  # with "Tengo poco tiempo" and the "Mínimo" phase (T-205 backend, T-206 UI). A stored "Mínimo" plan reads as
  # "Mantenimiento" with its calories unchanged.

  Scenario: No minimal phase
    When I open "Antes de empezar" for a block
    Then there is no "Tengo poco tiempo" option
    And no plan or phase reads "Mínimo"

  # Amended 2026-11-02: re-anchored on "Mantenimiento" ("Recuperar" removed).
  Scenario: Cardio follows my intention in every other phase
    Given my intention is "Perder grasa" and the phase is "Mantenimiento"
    Then the cardio proposal is 150 minutes, editable up to 300 inside the band

  Scenario: Every experience starts at the same volume
    Given I am a beginner
    When the coach builds my first block
    Then each muscle group gets 10 weekly sets

  Scenario: The phase does not change inside a block
    Given my block is in "Definición"
    When my data would justify another phase
    Then the phase stays until the end-of-block review
```

**Technical notes**
- Phase calories and training: `07` §4, §12.3 (cut volume kept at the previous block's). The phase is stored on the plan; the Spanish labels are finalised by ux-reviewer within the glossary ("Definición", "Volumen", "Mantenimiento", "Recomposición"; "Mínimo" removed 2026-11-03, E13 Q56).
- **Amended 2026-11-02 (user):** "Recuperar" (regain) is removed entirely and deferred to Phase 3; T-200 removes the phase and moves any stored regain plan to "Mantenimiento" with its calories unchanged.
- Phase triggers (when a change is proposed) belong to the end-of-block review (US-83).

## US-83 · End-of-block review and the next block → E14 (backlog)
*Tasks T-190 (backend), T-191 (UI). ADR-86, E13 Q35, Q47 (recommendation). Rules: `07` §12.3-§12.4.*

**As** a user who finished a block
**I want** the coach to look back with me and propose the next block and, when my data says so, a new phase
**So that** the plan keeps adapting to what I actually did, month after month.

```gherkin
Feature: End-of-block review

  Scenario: The block has ended
    Given I completed the last plan week of my block
    When I open Trainer
    Then a card says "Tu bloque ha terminado: revísalo"
    And no new block starts on its own

  # Amended 2026-11-02: "Revisar el bloque" is the life-change offer of US-91 (`07` §12.8), whose "Bloque nuevo" is this early review; one label per concept (R-35), ux-reviewer confirms in T-191/T-197.
  Scenario: Reviewing early
    Given my block is saved
    When I tap "Bloque nuevo" in "Tu plan"
    Then the review opens with the data so far

  Scenario: What the review shows
    When the review opens
    Then it shows the sessions done against planned per routine
    And the e1RM of each main lift over the block
    And the weight trend against the phase's target rate
    And the complete diary days

  Scenario: "¿Cómo te ha ido?"
    When the review asks "¿Cómo te ha ido?"
    Then I can pick "Bien", "Me ha costado" or "Me ha sobrado" and add a text
    And my answer reaches the coach as I wrote it

  Scenario: A phase change is proposed
    Given I completed 3 deficit blocks of "Definición"
    When the coach prepares the next block
    Then it proposes "Mantenimiento" with the reason "Llevas 12 semanas de definición. Te proponemos 4 semanas comiendo lo que gastas antes de seguir."
    And I can tap "Aceptar" or "Seguir como estoy"

  Scenario: Keeping my phase
    When I tap "Seguir como estoy"
    Then the next block keeps the current phase
    And the proposal comes back at the next review

  # Void 2026-11-02 (user: "Recuperar" removed entirely, deferred to Phase 3; T-200). The scenario
  # "Regain ends when I am back" is removed (E13 Q51 resolved by the removal).

  Scenario: Long gain asks before going on
    Given I completed 6 blocks of "Volumen"
    When the coach prepares the next block
    Then it asks "¿Seguimos?"

  Scenario: A stalled lift
    Given "Sentadilla" did not improve its e1RM during the block
    And I completed 80 % or more of the planned sessions
    And I did not answer "Me ha costado"
    When the coach prepares the next block
    Then it may change the rep range or swap "Sentadilla" for a similar exercise
    And it may add 2 weekly sets for that muscle group, never above 20

  Scenario: "Me ha costado" adds no volume
    Given I answered "Me ha costado"
    When the coach prepares the next block
    Then no muscle group gets more weekly sets

  Scenario: Low adherence
    Given I completed 60 % of the planned sessions
    When the coach prepares the next block
    Then no muscle group gets more weekly sets
    And the coach may propose fewer days or shorter sessions, matching what I did

  Scenario: Starting the next block
    When I accept the proposal
    Then the block preview shows the next block with the sets per muscle group before and after
    And "Guardar bloque" starts it and ends the previous block

  Scenario: Nothing starts without me
    When I leave the review or the preview without saving
    Then my current block and phase stay as they are
```

**Technical notes**
- *(Block part superseded 2026-11-04, ADR-99: rep-range changes, swaps and the next block's picks are the user's in the builder; phase triggers stay rules.)* The model chooses among server-eligible options (phase, rep-range change, alternative exercise, fewer days) and writes the reasons; every trigger and number is a rule (`07` §12.3-§12.4). A calorie change outside the safe band is decided here with the user's OK (ADR-88).
- A block that ended without a review: the routines stay usable and no automatic change applies past its last week (E13 Q47 recommendation).
- Cost: one review call per block (about 0,05 € with Sonnet 5.5) plus the existing plan and block calls.
- **Amended 2026-11-03 (T-202, ADR-93):** *(Superseded 2026-11-04, ADR-99, T-219, user: block creation is deterministic with no model call; see `07` §3 "Block builder".)* the next block is built by the model like any block (US-72), starting from the current block's folder with the user's edits (US-93); the per-group volume and priority rules (`07` §12.4) are bounds of the routine checker; rep-range changes and swaps are the model's choice inside the checker, not a server option list. The phase triggers stay rules.

## US-84 · "Priorizar" or "Mantener" a muscle group → E14 (backlog)
*Tasks T-190 (backend), T-191 (UI). ADR-86, E13 Q38. Rules: `07` §12.4.*

**As** a user with a body part I want to grow and another I only want to keep
**I want** to mark muscle groups at the end-of-block review
**So that** the next block puts the work where I care.

```gherkin
Feature: Per-muscle priority

  Scenario: Neither by default
    When the review opens
    Then every muscle group is neither "Priorizar" nor "Mantener"

  Scenario: Keeping a group with less work
    Given I mark "Pierna" as "Mantener"
    When the coach prepares the next block
    Then that group gets 4 weekly sets
    And 6 weekly sets if I am 65 or older

  Scenario: Prioritising a group
    Given I mark "Espalda" as "Priorizar"
    When the coach prepares the next block
    Then that group gets 2 more weekly sets than in the last block, never above 20
    And the sets freed by "Mantener" go to the prioritised groups first

  Scenario: At most three priorities
    Given three groups are marked "Priorizar"
    When I try to mark a fourth
    Then the app says that at most 3 groups can be prioritised

  # Amended 2026-11-02: "or a regain" / "or 'Recuperar'" dropped ("Recuperar" removed, deferred to Phase 3).
  Scenario: No extra volume in a cut
    Given the phase of the next block is "Definición"
    When I prioritise a group
    Then it only receives the sets freed by "Mantener"

  Scenario: The preview shows the effect
    When the next block is shown
    Then each group shows its weekly sets before and after
```

**Technical notes**
- The +2 follows the adherence and "Me ha costado" limits of US-83; the minimal phase ignores priorities. ux-reviewer finalises the copy ("Priorizar" adds sets to that muscle each block; "Mantener" keeps it with fewer sets, enough to not lose it).

## US-85 · "Entrenador": small changes apply on their own → E14 (backlog)
*Tasks T-192 (progression), T-193 (calories). ADR-88, E13 Q36, Q41. Progression scenarios in US-70, calorie scenarios in US-69.*

**As** a user who trusts the coach
**I want** the small adjustments made for me, visibly and reversibly
**So that** I do not approve every week by hand and I can still say no.

```gherkin
Feature: Automatic changes

  Scenario: No mode to choose
    When I open Trainer
    Then there is no setting to choose how much the coach decides

  Scenario: Every automatic change is visible
    Given the coach changed a load or my calories on its own this week
    When I open Trainer
    Then each change shows what changed, why and "Deshacer"

  Scenario: Undoing a calorie change
    Given the coach lowered my calories by 150 kcal on its own
    When I tap "Deshacer"
    Then Diario's target goes back to the previous calories
    And the coach does not change the calories again on its own for 14 days
    And the undone change does not count towards the block's ±10 %

  Scenario: Two undos stop automatic calorie changes
    Given I undid two calorie changes in this block
    Then the calories do not change on their own again until the end-of-block review

  Scenario: Maintenance can still go down a little
    Given my phase is "Mantenimiento" and I am gaining faster than my trend allows
    When the coach runs
    Then the calories can go down, but never more than the energy of 0.25 % of my weight a week below my expenditure

  Scenario: Changes that need my OK
    Then a new block, a phase change and a calorie change outside the safe band are never applied without my acceptance

  Scenario: Outside a block
    Given I have no accepted block
    Then nothing is changed on its own
```

**Technical notes**
- The "Deshacer" label gets its own glossary entry (undo an automatic coach change); R-20 still forbids it for deletion (ux-reviewer, T-192). Every automatic change and its undo is stored (US-74).
- "14 days" after an undo reuses the 14 days between calorie changes (`07` §4, §9). Reference expenditure for every limit: the one in use at the change (`07` §4).
- Progressed loads (`07` §3): the server computes the step from my plates and handle and writes it to every block routine with that exercise and rep range (settles E13 Q44 as A).

## US-86 · Scheduled deloads, cancelling one or asking for one → E14 (backlog)
*Task T-193. ADR-88, E13 Q37. Rules: `07` §3 "Deload".*

**As** a user training block after block
**I want** a deload every so often even if I am still progressing, and to cancel or ask for one
**So that** fatigue does not pile up and I stay in charge.

```gherkin
Feature: Scheduled deloads

  Scenario: A scheduled deload
    Given no deload ran in the previous 7 training weeks
    When week 4 of my block starts
    Then it is a deload week on its own
    And Trainer shows "Semana de descarga: mismos pesos, menos series. Llevas 7 semanas entrenando sin descarga." with "Cancelar descarga"

  # Added 2026-11-02 (`07` §3 "Deload").
  Scenario: One deload per block, in week 4
    Given the deload was triggered by a stall and also scheduled
    Then the block has one deload, in week 4
    And a diet-break week 4 keeps its maintenance calories during the deload

  Scenario: Paused weeks do not count
    Given two calendar weeks without sessions
    Then they do not count towards the 7 training weeks

  Scenario: No scheduled deload in the minimal phase
    Given my phase is "Mínimo"
    Then no deload is scheduled

  Scenario: Asking for a deload
    Given next week is not a deload week
    When I tap "Hacer descarga la próxima semana"
    Then next week becomes a deload week

  Scenario: Cancelling a scheduled deload
    When I tap "Cancelar descarga"
    Then that week goes back to normal
```

## US-87 · Training around an injury and coming back step by step → E14 (backlog)
*Task T-194. ADR-89, E13 Q40, Q45 (recommendation). Rules: `07` §7.*

**As** a user with an injured or painful area
**I want** the coach to keep training the rest of my body and bring the hurt exercise back gradually
**So that** I do not lose what I built while I recover, and I decide the pace.

```gherkin
Feature: Injury follow-up

  # Amended 2026-11-02 (user, Checkpoint E13-D: the intake has no injured area any more, T-198). The former scenario
  # "Given 'Para conocerte' says I have a shoulder injury … the exercises that load the shoulder are left out" is void.
  # Training around an injury comes from logged discomforts only. E13 Q52: the fitness-scientist confirms `07` §7 before T-194.
  Scenario: Training around a severe discomfort
    Given "Press militar" has an unresolved discomfort of level 4 or 5
    When the coach proposes my block
    Then "Press militar" is left out
    And the preview lists it under "Fuera del bloque por tu lesión" with "Volver a incluir"

  Scenario: Putting an exercise back
    When I tap "Volver a incluir" on "Press militar"
    Then "Press militar" is in the block

  # Superseded 2026-11-04 (ADR-99, user): block creation ignores discomfort; the rest of US-87 (load blocks, graded return) is unchanged.
  Scenario: A severe discomfort leaves the next block
    Given "Press banca" has a discomfort of level 4 or 5
    When the coach prepares the next block
    Then "Press banca" is left out and a similar exercise I have not flagged replaces it

  # Amended 2026-11-02 (`07` §12.8 row 7): the swap is offered, applied on my tap. The offer's pattern and copy need ux-reviewer (T-194).
  Scenario: Step 1 inside my block
    Given "Press banca" belongs to my accepted block
    When I log a discomfort of level 4 or 5 on it
    Then Trainer offers to swap it for the rest of the block for a similar exercise I have not flagged
    And nothing changes until I accept
    When I accept
    Then the block's routines use the substitute and the change shows its reason and "Deshacer"

  Scenario: A severe discomfort stays until I resolve it
    Given I logged a level 4 discomfort on "Press banca" 20 days ago
    And I did not tap "Ya no me molesta" or finish step 3
    Then it is still shown on "Press banca" with "Ya no me molesta"
    And "Press banca" stays out of the next block

  Scenario: The next step is offered, I decide
    Given "Press banca" is in step 1 for at least a week with no new discomfort on it
    When I open its discomfort row
    Then it offers "Pasar al siguiente paso"
    And nothing changes until I tap it

  Scenario: Step 2
    When I move "Press banca" to step 2
    Then it comes back at the last load before the discomfort
    And its routine note says "Rango sin molestia"

  Scenario: Step 3
    Given "Press banca" was in step 2 for at least a week with no new discomfort
    When I move it to step 3
    Then it trains in full range with the normal progression

  Scenario: Going back a step
    Given "Press banca" is in step 3 and its last discomfort was level 2
    When I log a new discomfort of level 2 or 3 on it
    Then it goes back to step 2

  Scenario: Back to the start
    Given "Press banca" is in step 2 or 3
    When I log a new discomfort of level 4 or 5 on it
    Then it goes back to step 1

  Scenario: No diagnosis
    Then the coach never names an injury, a cause or a treatment
```

**Technical notes**
- ~~Which exercises "load the area" is model judgement over the catalogue, shown to the user, who can put any back (`07` §7);~~ *(void 2026-11-02: no injured area; exclusions come from logged level 4-5 discomforts, a rule.)* Replacements come from the validated alternatives of the same movement pattern (T-179 graph), never an exercise the user flagged.
- A move to the next step needs at least one week in the current step and no new discomfort on the exercise; the level is the only signal.
- **Unresolved (`07` §7, 2026-11-02):** a level 4-5 discomfort is unresolved, whatever its age, until "Ya no me molesta" or step 3; the 7-day "open" window governs only the load block of levels 1-3. E13 Q45 is settled by `07` §12.8 row 7: the step-1 swap is offered, not automatic.

## US-88 · Breaks, busy periods and coming back → E14 (backlog; the minimal phase built by T-187 is removed by T-205/T-206, E13 Q56: its scenarios here are void for the E14 re-plan)
*Tasks T-189 (block pause), ~~T-185 (returning after 28 days)~~, T-187/T-188 (minimal), ~~T-190 (regain proposal)~~, T-192 (loads after 14 days); "returning" and regain removed 2026-11-02 (T-198, T-200; Phase 3). ADR-86, E13 Q40. Rules: `07` §12.5. Week counting scenarios in US-73; return loads in US-70.*

**As** a user whose life gets in the way
**I want** the plan to wait for me and pick up sensibly when I come back
**So that** a holiday or a busy month does not break the plan.

```gherkin
Feature: Breaks and coming back

  # Amended 2026-11-02 (user: "Recuperar" and the "returning" situation removed, deferred to Phase 3; T-198, T-200).
  # "A long break makes me 'returning'" keeps only its pause part; "The review after a long break proposes regain" is void.
  Scenario: A long break pauses my block
    Given I completed no session for 28 days
    When I open Trainer
    Then my paused block still resumes where I left off

  Scenario: Ending a busy period
    Given my phase is "Mínimo"
    When I say the busy period is over and create a new block
    Then the review proposes the phase of my intention

  Scenario: A long minimal phase
    Given I completed 8 blocks in "Mínimo"
    When the coach prepares the next block
    Then it proposes going back to normal training
```

## US-89 · "Lo que el entrenador sabe de ti": my words reach the coach
*Tasks T-185 (backend), T-186 (UI); the block request reads the lines from T-198 (2026-11-02). ADR-87, E13 Q33.*

**As** a user who writes notes
**I want** the coach to read what I write and to see exactly what it reads
**So that** it understands my context and I stay in control of my data.

```gherkin
Feature: Free text sent to the coach

  Scenario: What the coach reads
    When I open "Lo que el entrenador sabe de ti" in Trainer
    Then I see my "Para conocerte" text, my check-in notes, my discomfort notes and my answers to "¿Cómo te ha ido?" of the last 8 weeks
    And each line exactly as it is sent, newest first

  Scenario: Leaving a line out
    When I turn off a line
    Then that line is no longer sent to the coach
    And my original note is kept where I wrote it

  Scenario: Size limits
    Given my notes add up to more than 2000 bytes
    Then only the newest lines up to 2000 bytes are sent
    And the screen marks the lines that are not sent because of the limit

  Scenario: What is never sent
    Then set notes, routine notes, food names and custom exercise names are never sent

  Scenario: Words never change numbers
    Given a note says "me duele mucho la rodilla"
    Then no figure, trigger, phase or health note comes from that text

  # Added 2026-11-02 (user, Checkpoint E13-D feedback: "keep the free text"). T-198; until then only the plan request read it.
  Scenario: My words reach the plan and the block
    Given my "Para conocerte" text and my notes are in "Lo que el entrenador sabe de ti"
    When the coach prepares my plan or my block
    Then both requests carry those lines, minus the ones I turned off, as context and never as instructions
    And the screen says the lines are read when the coach prepares my plan and my block
```

## US-90 · A note when a severe discomfort does not go away → E14 (backlog)
*Tasks T-194 (row), T-166 (report). ADR-89, E13 Q39. Rules: `07` §7.*

**As** a user with a discomfort that persists
**I want** the coach to tell me once, plainly, when it may be worth getting it checked
**So that** I am not left alone with it, without generic warnings everywhere.

```gherkin
Feature: Persistent discomfort note

  Scenario: Unresolved for 14 days
    Given a level 4 or 5 discomfort on "Sentadilla" is still unresolved 14 days after I logged it
    When I open the exercise or the weekly report
    Then it shows once "Esta molestia sigue ahí desde hace 14 días. Seguimos entrenando el resto sin cargar esta zona. Puede merecer la pena que te lo revise un profesional."

  Scenario: Three sessions at a severe level
    Given I logged a level 4 or 5 discomfort on "Sentadilla" in 3 sessions
    Then the same note is shown once

  Scenario: Dismissing the note
    When I dismiss the note
    Then it does not show again for that discomfort
    And nothing is blocked

  Scenario: Milder levels
    Given the discomfort is level 1 to 3
    Then no note is shown

  Scenario: No generic warnings
    Then no screen of the app shows a generic health disclaimer
    And the body measurements and the weight screens show no "consulta con un profesional" line
```

## US-91 · "Revisar el bloque" when my training situation changes → E14 (backlog)
*Added 2026-11-02 (user-approved design, `07` §12.8 "Revisar el bloque", rows 1-3, 5, 7). Tasks T-196 (backend), T-197 (UI). The offer, the three choices and "Solo unos días" need ux-reviewer (pattern and copy).*

**As** a user whose gym, days or time change in the middle of a block
**I want** to adapt the block in one tap, start a new one, or change it only for a few days
**So that** the plan keeps fitting my life without starting over.

```gherkin
Feature: Adapting the block to a change

  # Amended 2026-11-03 (T-202, ADR-93; `07` §12.8 rows 1-3): there is no server split any more, so "Adaptar el bloque"
  # is offered only for a place or equipment change. Whether it stays at all is E13 Q55 (open).
  Scenario: The coach offers to review the block
    Given I have an accepted block
    When I save a new place or equipment in "Mi entrenamiento"
    Then Trainer offers "Revisar el bloque"
    And it offers "Adaptar el bloque", "Bloque nuevo" and "Solo unos días"

  Scenario: Days or session length change
    Given I have an accepted block
    When I save new days per week or a new session length in "Mi entrenamiento"
    Then Trainer offers "Revisar el bloque" with "Bloque nuevo" and "Solo unos días"
    And my current block runs unchanged until I accept a new one

  Scenario: Adapting keeps the block
    Given I switched from "Gimnasio" to "Casa" with "Mancuernas" and "Peso corporal"
    When I tap "Adaptar el bloque"
    Then the preview keeps my block week, phase, weekly sets per muscle group and rep ranges
    And each exercise I cannot do is swapped for one of the same muscle group and movement pattern
    And an exercise with no possible replacement is dropped and the preview says so
    And the cardio exercise is re-picked for my equipment with the same minutes
    And no AI call is made

  Scenario: Applying and undoing the adaptation
    When I apply the adapted block
    Then my block's routines change in one step with "Deshacer"
    When I tap "Deshacer"
    Then my previous routines come back

  # Void 2026-11-03 (ADR-93; `07` §12.8 rows 2-3): "Fewer days" and "Shorter sessions" (the deterministic re-split and
  # slot recompute) are removed; a days or length change offers "Bloque nuevo" (scenario above).

  Scenario: A new block instead
    When I tap "Bloque nuevo"
    Then the end-of-block review opens early
    And the new block needs my acceptance

  Scenario: Only for a few days
    When I tap "Solo unos días" and pick an end date, a place and 1 day a week
    Then the sessions until that date use swapped exercises and the "Mínimo" routine
    And my phase and calories do not change
    And after the end date my original routines and loads come back

  # Void 2026-11-02 (user, Checkpoint E13-D: the intake has no injured area, T-198). The former scenario
  # "A changed injured area … Trainer offers 'Revisar el bloque'" is removed; a new injury mid-block is a logged
  # level 4-5 discomfort (US-87 "Step 1 inside my block").
```

**Technical notes**
- "Adaptar el bloque" and "Solo unos días" are rules (no model call, free); "Bloque nuevo" is the early review of US-83 and opens the block builder (ADR-99, free; *superseded 2026-11-04:* "paid", "the model builds from the current block's folder" (ADR-93)). Swap order: same group and pattern in the `07` §3 order (T-179), else same group, else dropped; cardio by §9.11. *(Re-split by `04` §9.2 and slots by §9.3: superseded 2026-11-03.)*
- Loads for an exercise new to the block: US-92.

## US-92 · Other life changes → E14 (backlog)
*Added 2026-11-02 (`07` §12.8 rows 4, 6, 8-12). Tasks T-196, T-197; the report rows in T-166.*

**As** a user over months and years
**I want** the coach to react sensibly to changes in my goal, my health, my logging and my age
**So that** the plan stays right without surprises.

```gherkin
Feature: Life changes

  Scenario: Changing my goal offers a new block
    Given I have an accepted block
    When I change my intention in "Mi entrenamiento"
    Then Trainer offers "Bloque nuevo" only
    And my current block keeps running until I accept a new one

  Scenario: A new exercise starts from my own weights
    Given a block swap gives me "Press con mancuernas"
    When I have trained it before
    Then its planned weight is my last working weight on it
    When I have never trained it
    Then it has no planned weight and I pick one I can lift for the bottom of the range at RPE 7-8
    And no weight is converted from another exercise

  Scenario: Illness is only missed days
    Given I write in a check-in that I was ill
    Then nothing changes because of that text
    And the break rules apply only to the sessions I missed

  # Void 2026-11-03 (user, T-202; `07` §12.8 row 11, ADR-94): "My swaps stay" is removed. The next block does not
  # remember swapped-out exercises; favourites (US-96) and starting from my own folder (US-93) carry my preferences.

  Scenario: Turning 65
    Given I turn 65 in the middle of a block
    Then my current block does not change
    And the rules for 65 and older apply from my next block
```

**Technical notes**
- Goal reached and empty weeks are report rules (US-71); coming back after weeks off is US-88 / US-70; a new injury mid-block is US-87.

## US-93 · Starting a block from scratch or from my folder
> **Superseded 2026-11-04 (ADR-99, T-219, user):** both starts are deterministic. "Desde cero": split, type per day, slots, 100 %. "Desde una carpeta": the same 100 % (folder review at 10,0), fixed on a copy, saved as a new folder, original untouched; refused when its routine count differs from "Días por semana" (`07` §3 (h)). The scenarios "From scratch, I pick the type of routine", "From my folder" and "A missing requirement must be fixed" (model checks, accept or reject proposed changes, lateral raise) are void once the builder task merges; the others stand. Gherkin to be rewritten by the epic-planner.
*Added 2026-11-03 (T-202, user UX decisions 1 and 4; ADR-93, ADR-94; `07` §3 "Starting points"). Tasks T-205 (from scratch, backend), T-206 (from scratch, UI), T-207 (from a folder, backend), T-208 (from a folder and the start choice, UI).*

**As** a user creating a block
**I want** to choose between a block designed from scratch and one built from my own routines
**So that** the block fits how I already train, or gives me a good new start.

```gherkin
Feature: Starting a block

  Scenario: Choosing how to start
    Given I have an accepted plan without a block
    When I tap "Crear bloque"
    Then I can choose "Desde cero" or "Desde una carpeta"

  # Superseded 2026-11-04 (ADR-99): see the note above.
  Scenario: From scratch, I pick the type of routine
    When I choose "Desde cero"
    Then the coach proposes 2 or 3 types of routine for my days and session time
    And one is marked "Recomendada" with its reason
    When I pick one
    Then the coach builds the block with that type of routine
    And the block preview opens

  # Superseded 2026-11-04 (ADR-99): see the note above.
  Scenario: From my folder
    Given I have a routine folder "Mi rutina"
    When I choose "Desde una carpeta" and pick "Mi rutina"
    Then the coach checks it and lists what is missing and the changes it proposes, each with its reason
    And I can accept or reject each change
    When I continue
    Then the block preview shows my routines with the accepted changes
    And "Mi rutina" itself does not change

  # Superseded 2026-11-04 (ADR-99): H3 is no special case; see the note above.
  Scenario: A missing requirement must be fixed
    Given my folder has no lateral raise where one is required
    When I reject the change that adds one
    Then the block is not created
    And it shows what is missing

  Scenario: My edits reach the next block
    Given I saved a block and later edited its routines in Entreno
    When I create a new block "Desde una carpeta" with the block's folder
    Then the coach starts from my edited routines

  Scenario: Edited block routines still count
    Given I swapped an exercise in a routine of my block in Entreno
    When I complete that routine
    Then the plan week counts it as done

  Scenario: Nothing is created without "Guardar bloque"
    When I leave at any step before "Guardar bloque"
    Then no block and no routine are created
```

**Technical notes**
- *(Superseded 2026-11-04, ADR-99, T-219, user: block creation is deterministic with no model call; see `07` §3 "Block builder".)* From scratch is two paid calls (split options, then the block); from a folder is one review call plus the block save (`04` §9.14). Both pass the hard checks before the block is stored. Now: no call; the builder gate is 100 % and a folder whose routine count differs from "Días por semana" is refused.
- "Tengo poco tiempo" and the minimal phase are removed (user, 2026-11-03, E13 Q56): T-205 drops the backend path, T-206 the option and its strings.
- The start choice ships with T-208; until then "Crear bloque" goes straight to the split options (T-206).

## US-94 · Removing a block without a new plan
*Added 2026-11-03 (T-202, user UX decisions 1 and 3; ADR-94). Task T-203. What removal does: E13 Q54 (user, 2026-11-03); the follow-ups without a block: E13 Q58.*

**As** a user with a block I no longer want
**I want** to remove it and keep my nutrition plan
**So that** I can create a different block without starting a new plan.

```gherkin
Feature: Removing a block

  Scenario: Removing the block keeps the plan
    Given my accepted plan has a saved block
    When I tap "Eliminar bloque" in "Tu plan" and confirm
    Then the plan keeps its calories, macros, phase, cardio and steps
    And "Tu plan" no longer shows the block or its weeks
    And "Crear bloque" is offered again

  Scenario: My routines stay
    When I remove the block
    Then the block's folder and routines stay in Entreno
    And my workouts are not changed

  Scenario: Cancelling keeps everything
    When I tap "Eliminar bloque" and cancel the confirmation
    Then the block stays as it was

  Scenario: No automatic change after removal
    Given I removed my block
    When I finish a workout of one of its routines
    Then the coach does not change its planned weights on its own
    And the workout counts for no plan week

  Scenario: A new block on the same plan
    Given I removed my block
    When I save a new block
    Then its first week starts on the day I saved it
```

**Technical notes**
- One RPC removes the block from the plan (plan weeks and links deleted, folder kept; E13 Q54, user 2026-11-03). Without a block: no deload and no end-of-block review; calories and the weekly report keep running (E13 Q58 recommendation). A plan with a saved block offers "Eliminar bloque" instead of "Crear bloque" (`04` §9.15). Destructive dialog per R-08; ux-reviewer finalises the copy ("Tus rutinas se quedan en Entreno").

## US-95 · Reviewing a routine folder with the coach
*Added 2026-11-03 (T-202, ADR-93); **rewritten 2026-11-04 (T-216, ADR-97, user feedback 4 of Checkpoint E13-H):** the review is deterministic and runs on the device; definition in `07` §3 "Folder review". Tasks T-207/T-208 built the old model review (still used only by "a block from a folder"); T-216 replaces the review screen.*

**As** a user with my own routines
**I want** a quick score and checklist of my routine folder
**So that** I see what is missing in the big picture without giving up my routine.

```gherkin
Feature: Folder review

  Scenario: Reviewing a folder
    Given I have a routine folder "Mi rutina"
    When I open its menu in Entreno and choose "Revisar tus rutinas"
    Then I see the "Puntuación" out of 10, for example "7,5 / 10"
    And one row for each of chest, back, shoulders, quads, hamstrings and glutes with its "Series a la semana"
    And a row with my training days

  Scenario: An unmet row explains what to do
    Given my folder has 6 weekly sets of quads
    When the review opens
    Then the row "Cuádriceps" is marked "Por mejorar"
    And it says how many sets to add

  Scenario: A group with no sets limits the score
    Given my folder has no sets for a main group
    When the review opens
    Then the score is at most "6,0 / 10"
    And that group says it has no weekly sets

  Scenario: Strength rows
    Given my intention is "Ganar fuerza"
    When the review opens
    Then there are also rows for heavy repetitions and for main lifts first
    And the exercises that break them are named

  Scenario: A good folder
    Given every group has 10 or more weekly sets and I have 2 or more training days
    When the review opens
    Then every row is "Cumplido"
    And the score is "10,0 / 10"

  Scenario: Nothing is written or blocked
    When I leave the review
    Then nothing changes in my folders
    And there is no action to apply changes or to add exercises

  Scenario: Without a connection
    Given I am offline
    When I choose "Revisar tus rutinas"
    Then the review opens and shows its result

  Scenario: Coming back to the app
    Given the review is open
    When I leave the app and come back
    Then the review shows the same result
```

**Technical notes**
- Computed on the device (no model, no cost) from the folder's routines, catalogue facts and the intention in "Mi entrenamiento"; the rep range falls back to the planned reps; the Spanish copy and testTags are the T-216 UX notes. "A block from a folder" is not part of this story's rewrite (US-93, ADR-94) and keeps its model review.

## US-96 · Favourite exercises
*Added 2026-11-03 (T-202, user UX decision 5; ADR-94; `07` §3 "Favourites"). Task T-204; the coach reads favourites from T-205.*

**As** a user with exercises I like
**I want** to star them
**So that** I find them first and the coach prefers them.

```gherkin
Feature: Favourite exercises

  Scenario: Starring an exercise
    When I tap the star in the detail of "Elevaciones laterales"
    Then it is a favourite and the star shows it
    When I tap it again
    Then it is no longer a favourite

  Scenario: Favourites first in the picker
    Given "Elevaciones laterales" is a favourite
    When I open the exercise picker filtered by "Hombros"
    Then "Elevaciones laterales" is listed first

  Scenario: Favourites first in the swap list
    Given a favourite is an alternative of a block exercise
    When I open the swap list of that exercise in the block preview
    Then the favourite is listed first

  Scenario: The coach prefers my favourites
    Given "Press con mancuernas" is a favourite and fits a chest exercise of my block
    When the coach builds the block
    Then it prefers "Press con mancuernas" over other chest presses
    And a favourite never adds sets or breaks a rule

  Scenario: My own exercises can be favourites
    Given I created "Remo en máquina del gimnasio"
    When I star it
    Then it is a favourite like any other
```

## Evaluation (not a user story) → E14 (backlog)

> **Moved to E14 (user, 2026-11-04):** T-168 is re-planned in E14. Re-plan note: plans (ADR-98), blocks (ADR-99) and the folder review (ADR-97) are deterministic, so the evaluation mainly gates the chat. Kept unaligned.
- Cases written by the user, each with its correct answer: a plan for each intention, one training day, limited equipment, discomfort, missing data, rate inside and outside range, calorie floor, invented figure in a report.
- Acceptance criteria:
  - 100 % of discomfort cases: no load increase.
  - 100 % of report figures traceable to the summary.
  - Every plan meets the rules of `07-coach-design.md` §3-§4: frequency (when days allow), volume, intensity, rest between sets, exercise order, periodization (undulating only for "Ganar fuerza"), no reduction of cardio or steps, protein (incl. the distribution guidance), fat and the calorie floor. **Amended 2026-11-03 (ADR-93):** every block passes the routine checker's hard checks H1-H7 on the first or the retried answer (100 %); blocks for different users with the same days differ in split or exercises; folder reviews propose no change that adds a hard failure (100 %); periodization is optional.
  - Report and chat answer under 30 s at the 95th percentile; the plan shows progress and finishes within the Edge Function limit (150 s wall clock on the Free plan, checked 2026-10-29, `07` §11).
  - **Added 2026-11-01 (ADR-86…ADR-89):** ~~100 % of intake extractions leave unmentioned fields empty and return only allowed values;~~ 100 % of phase proposals are among the server-eligible options~~ (a returning lifter starts in "Recuperar"; no deficit in regain)~~; 100 % of block-review proposals respect the volume and priority limits of `07` §12.4; 100 % of report and chat texts mention only existing features (grounding); free text never yields a figure, a trigger or a health note; injury cases never keep a flagged exercise~~ or one loading the injured area~~. *(Struck 2026-11-02: no extraction, no injured area, no "Recuperar"; T-198, T-200.)*
- *(2026-11-04, ADR-99: block cases of the evaluation test the deterministic builder, not a model checker.)* **2026-11-03 (E14 split):** the phase-proposal, block-review and injury cases move to E14 with their stories; E13's evaluation covers plans, blocks (checker), folder reviews, discomfort (no load increase), report figures, grounding, free text and the chat.
- Evaluation runs are paid calls: the user approves each run. A function is not enabled until it meets its criteria.
- **Resolved 2026-10-29 (user, Q4 B):** the plan and the report are usable at the checkpoints before the evaluation (the server validators enforce the hard rules on every output); the evaluation is the formal gate before the chat (US-77) and before the app is shared with other users.
