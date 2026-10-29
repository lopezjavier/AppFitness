# Implementation Plan: E13 · Performance assistant → `v1.x` (proposed `v1.4.0`)

> **Status:** broken down on 2026-10-29 by the epic-planner and **agreed by the user the same day** with two changes: three checkpoints instead of five, and every recommended option of E13 Q1-Q13 approved (resolved below; amended Gherkin in the epic file; `planning/09-open-questions.md`). 14 tasks (T-157…T-170). No task is blocked.

## Overview
A coach that builds a four-week training and nutrition block from the user's intention and preferences, sets the outcomes it can justify, and adjusts the plan with the user's data. It proposes; the user accepts. Every calculation is deterministic (SQL or TypeScript); the model only selects within validated options and explains; nothing is applied without consent (ADR-76).

Sources: `planning/epics/E13-performance-assistant.md` (US-68…US-77 + evaluation), `planning/07-coach-design.md` (every rule and figure, incl. the rules added on 2026-10-29: rest between sets, exercise order, undulating periodization for "Ganar fuerza", range of motion, cardio, protein distribution), `planning/01-scope.md` §3-§4, ADR-76…ADR-82, ADR-25, ADR-29, ADR-40, ADR-73, `04` §1, §3, §4, §6.6, `05` §5, §8-§10. Tasks: `tasks/todo.md` (E13 section).

Carried over from "Notes for later epics": verification of sources, data policy and prices is done (2026-10-29); the 👤 Anthropic key is placed right before T-160, the first task that calls the model.

Out of scope (epic, `01` §5): medical advice or diagnosis, video analysis, meal plans (the protein distribution is guidance only), soreness as a signal, water and voice (Phase 3), background jobs (ADR-73), distribution to other users (written condition, `07` §8).

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR.
- **Model-free first:** US-68 (T-157 backend, T-158 UI) and US-70 (T-159) give Checkpoint E13-A with no paid call.
- **US-73 and US-75 move after US-72** (deviation from the initial guidance, justified by the epic): adherence links workouts to `coach_plan_sessions`, and the deload "changes the week" of a block; both need a saved block (US-72). They merge into T-165 (L): both are deterministic on the plan weeks, and each alone is M.
- **Splits (backend → UI):** US-68 (T-157 SQL math with heavy pgTAP; T-158 Trainer tab and card; ~20 files together). US-69 (T-161 function call, validators, RPCs; T-162 proposal screen and target-weight removal). US-72 (T-163 block call and validators; T-164 preview and "Guardar bloque").
- **US-76** rides with the Edge Function foundation (T-160): the budget check guards every call, so it must exist before the first one.
- **Evaluation** is its own task (T-168, not a story). **US-77** last (T-169), gated by it. **Closure** T-170 stays S: it waits for Checkpoint E13-C.

| Task | Story | Flow | Size | Agent runs |
|---|---|---|---|---|
| T-157: Coach summary and derived values in SQL | US-68 (+ US-69 trend, intake) | backend | L | 4 |
| T-158: Trainer tab and the derived-values card | US-68 | core + UI | M | 7 |
| T-159: Rep range and progression suggestion in the live session | US-70 | backend + core + UI | L | 8 |
| T-160: `coach` Edge Function, AI usage and the monthly budget | US-76 | backend + small UI | L | 8 |
| T-161: Plan nutrition and outcomes: generation, validation, acceptance | US-69 backend | backend | L | 4 |
| T-162: Plan proposal screen, nutrition acceptance, target weight retired | US-69 UI (+ US-68 gate) | core + UI + small migration | L | 8 |
| T-163: Block creator: training block generation and validation | US-72 backend | backend | L | 4 |
| T-164: Block preview and "Guardar bloque" | US-72 UI | core + UI | L | 7 |
| T-165: Plan weeks: adherence and the deload suggestion | US-73 + US-75 | backend + core + UI | L | 8 |
| T-166: Weekly coach report | US-71 (+ US-76 fallback) | backend + core + UI | L | 8 |
| T-167: Recommendation history, outcomes and deletion | US-74 | backend + core + UI | M | 8 |
| T-168: Coach evaluation: cases, runner and results | Evaluation | backend + 👤 | M | 4 + runs |
| T-169: Coach chat with tools | US-77 | backend + core + UI | L | 8 |
| T-170: E13 closure | DoD | PM | S | PM only |

## Checkpoints (user decision, 2026-10-29: three)
Collected test items:
- Trainer tab and derived values: 4 (tab and card with sources; provisional and confirm; edit; trend and diary reliability);
- progression: 3 (suggestion with reason; discomfort levels; ignoring keeps the routine);
- nutrition plan: 6 (create with progress; rules visible incl. protein guidance; outcomes; confirmation gate; Diario follows and manual edit; no target numbers) + usage 1;
- block: 4 (preview; frequency/volume; limits and structure: one day, equipment, order, rests, periodization; save or leave);
- plan weeks: 3 (adherence; missed day; deload);
- report: 4 (commentary after the check-in; real figures; once a day; sleep);
- history: 3 (decisions; outcomes; delete);
- chat: 3 (answer cites; proposal cards; cost) + final regression 2.

Grouped into three journeys (the user chose fewer, larger checkpoints; 7-9 items each, related items merged):
- **Checkpoint E13-A — The Trainer tab knows you, and the gym tells you what to lift next (7)**, after T-159: Trainer tab, derived values, rep range and progression. Why: the last model-free task, before plans build on the derived values. No paid call.
- **Checkpoint E13-B — Your plan from the coach: nutrition, the four-week block, adherence and deload (9)**, after T-165: nutrition plan and outcomes, target weight gone, AI usage, block preview and "Guardar bloque", adherence, deload. Why: before the report and history read the plan and its weeks.
- **Checkpoint E13-C — Your weekly report, the coach's track record, the chat, and the final E13 regression (9)**, after T-169, the last implementation task; its approval releases T-170. If the evaluation keeps US-77 disabled, it is presented right after T-168 without the chat items.

Each checkpoint also has "leave the app and come back: the same tab, screen and draft are restored" and an out-of-scope line. Full item lists are in `tasks/todo.md`. Non-checkpoint 👤 steps: the Anthropic key before T-160; the evaluation cases and the approval of every paid run in T-168. T-157, T-158, T-160…T-164 and T-166…T-168 merge with no checkpoint after them.

## Architecture Decisions
- **One `coach` Edge Function** (T-160): JWT, RLS through the user's token, `ANTHROPIC_API_KEY` only server-side (ADR-17/77); request kinds `plan_nutrition`, `plan_block`, `report`, `chat`; streamed (SSE) responses so the app shows progress; a call timeout below the 150 s wall clock (Free plan, `07` §11) with a clean error; structured JSON output validated server-side, retried once, then an error; the model never writes to the database, accepted proposals are written by RPCs.
- **150 s limit:** the plan is two calls (nutrition and outcomes in T-161; the training block in T-163). T-163's planner measures the block call and splits it further (per two weeks or per week) if it risks the limit.
- **One source for the math:** the derived values (BMR, activity, adaptive expenditure with intake completeness, weight trend with its interval, experience) and the compact summary (`coach_summary`, last 8 weeks, never raw rows) are computed in SQL (T-157), so the app and the function read the same numbers. Nutrition and block rules are TypeScript validators in the function; progression is SQL or TypeScript (T-159 planner).
- **Model picks from server-built candidates:** exercises come from a catalogue list filtered by place and equipment, never free names; outcomes from a validated option set.
- **Coach rules enforced by validators (`07` §3-§4):** frequency (≥ 2 per muscle group when days allow; 1 day = full body), ~10 weekly sets per muscle group, intensity per intention, 1-3 RIR, rest 2-3 min on main multi-joint lifts and 1-2 min on isolation (written into `rest_seconds`), priority multi-joint lifts first, undulating rep ranges within the week only for "Ganar fuerza", no reduction of cardio or steps, full range of motion only as a technique note; calorie floor (ADR-25), rate of loss/gain, protein ≥ 1.6 g/kg with distribution guidance (~0.4 g/kg per meal over ≥ 4 meals, not a meal plan), fat ≥ 20 %, in-block change ≤ 10 % after two weeks outside the trend's interval.
- **Budget:** `coach_usage` per user and month; the function refuses any call that would pass 2 € per user or 5 € in total (dollars counted as euros); the global total comes from an aggregate that exposes no row. Deterministic features never call the function. CI never calls the API (fake client); every real call (smoke test, evaluation) is approved by the user.
- **Suggestions never rewrite routines:** progression and deload are shown in the live session; "Actualizar automáticamente" (ADR-70) keeps copying what was performed.
- **Plan storage (epic §Tables):** `coach_derived_values` (T-157), `coach_usage` (T-160), `coach_plans` and `coach_recommendations` (T-161), `coach_plan_sessions` (T-163), adherence and deload columns (T-165), `coach_reports` (T-166), `coach_outcomes` (T-167), `coach_conversations` (T-169). Every table cascades on account deletion (ADR-29) and is covered by `delete_account_cascade.test.sql`.
- **Target weight retired (ADR-78)** in T-162: UI removed first, then `profiles.target_weight_kg` dropped in the same PR's migration; "Peso" shows the plan's weight outcome.
- **UI:** fourth tab "Trainer" (ADR-79) in T-158 (`05` §5 and the §8 "Decided" line updated). Shared pieces built once and reused (R-39): provisional label (T-158), `CoachProposalCard` and the streamed-progress state (T-162), reused by T-164/T-165/T-166/T-169. Screens on `AppScreen`, sheets on the four archetypes, `NavigationRow` "Informe semanal", history delete with the R-08 many-records dialog (G5 allow-list), a new chat pattern recorded in `05` §9 (T-169).

## Phases
### Phase 1: Model-free coach (US-68, US-70)
- [x] T-157: Coach summary and derived values in SQL — L
- [ ] T-158: Trainer tab and the derived-values card — M
- [ ] T-159: Rep range and progression suggestion in the live session — L
  - **Checkpoint E13-A 👤 (7 items)**

### Phase 2: The coach's plan: function, nutrition, block, plan weeks (US-76, US-69, US-72, US-73, US-75)
- [ ] 👤 Anthropic account, spending limit, `ANTHROPIC_API_KEY` secret
- [ ] T-160: `coach` Edge Function, AI usage and the monthly budget — L
- [ ] T-161: Plan nutrition and outcomes (backend) — L
- [ ] T-162: Plan proposal screen, nutrition acceptance, target weight retired — L
- [ ] T-163: Block creator (backend) — L
- [ ] T-164: Block preview and "Guardar bloque" — L
- [ ] T-165: Plan weeks: adherence and the deload suggestion — L
  - **Checkpoint E13-B 👤 (9 items)**

### Phase 3: Report, history, evaluation, chat and closure (US-71, US-74, US-77)
- [ ] T-166: Weekly coach report — L
- [ ] T-167: Recommendation history, outcomes and deletion — M
- [ ] T-168: Coach evaluation — M (👤 cases and each paid run)
- [ ] T-169: Coach chat with tools — L (only after T-168 passes)
  - **Checkpoint E13-C 👤 (9 items, final regression; releases T-170)**
- [ ] T-170: E13 closure — PM — S

Internal milestones (not 👤 checkpoints): per task lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest`, pgTAP and `deno test` green; after each migration or function change, `deploy-supabase` green on `main` with data intact before the next task or checkpoint; T-160 smoke call approved by the user.

## Dependencies
```
v1.3.0 ─► T-157 ─► T-158 ─► T-159 ─► [E13-A 👤]
       ─► [👤 key] ─► T-160 ─► T-161 ─► T-162 ─► T-163 ─► T-164 ─► T-165 ─► [E13-B 👤]
       ─► T-166 ─► T-167 ─► T-168 (👤 cases, paid runs) ─► T-169 ─► [E13-C 👤] ─► T-170 ─► v1.4.0
```
- Strictly sequential (one active task at a time).
- Earlier work used: E10 weekly check-in and summary (`weekly_set_counts`, `weekly_records`, weight trend); E11 `daily_activity`, `sleep_sessions`; E12 `training_preferences` (intention, place, equipment, days, length), `exercise_discomforts.level`, `note_kind`, `exercises.movement_pattern`; E3/E6 `save_routine`, routine folders, the live session, e1RM (Epley, `04` §4.1), plate and dumbbell rounding (`04` §4.9); E5 weight screen and `nutrition_goals`.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| The block call exceeds the 150 s wall clock | High | Two calls (nutrition, block), streaming, a timeout below 150 s; T-163 measures and splits further |
| The model invents figures or breaks a rule | High | Server validators for every `07` rule, retry once then error; candidate lists; figure validator for reports; evaluation gate (T-168) |
| Paid calls during development exceed the budget | Medium | Fake client in CI and `deno test`; every real call approved by the user; the function's budget check; Anthropic spending limit (👤) |
| Global budget needs a cross-user read | Medium | An aggregate that returns only the total (or the secret key inside the function), pgTAP proves no row leaks |
| SQL statistics (trend interval, adaptive expenditure) are hard to test | Medium | Fixed series with known slopes and intervals in pgTAP; cases shared with domain tests where a formula exists |
| No rep range exists on routines today (planned weight/reps per set, ADR-50; only a legacy `target_reps_max`) | High | Q2 resolved: T-159 adds an optional per-exercise range to the routine editor (more files: developer slices) |
| E13-B and E13-C are large (9 items) | Medium | Items grouped by journey; fixes become `fix(T-###)` and the checkpoint is re-presented with the list to re-test |
| Undulating ranges make progression compare unlike sessions | Medium | Per-session range matching (T-159), Q2 Gherkin |
| Removing `target_weight_kg` breaks screens or onboarding | Medium | UI removed before the column drop in the same PR; sweep for every reader; pgTAP |
| Plan targets overwritten by a later Preferencias save (`04` §1.4) | Medium | Q8; the save marks the plan's nutrition as edited |
| Deload and adherence untestable by hand without weeks of data | Low | Host tests and pgTAP cover the triggers; checkpoint items say "if your data shows it" |

## Open Questions

**All resolved by the user on 2026-10-29, each with the recommended option and every sub-choice** (recorded in `planning/09-open-questions.md`; amended Gherkin written into `planning/epics/E13-performance-assistant.md`; durable rules in `07` §3, §7, §8). Resolutions: Q1 B (neutral reason, no health message; levels 1-3 keep the reps rule and the 5 % drop) · Q2 A (optional per-exercise rep range in the routine editor, per session; scenario added) · Q3 (main lifts by `movement_pattern`; deload only inside an accepted block; 40 % cut to the nearest whole set, at least 1) · Q4 B · Q5 (glossary entries; "Eliminar historial del entrenador") · Q6-Q10 and Q13 the defaults below · Q11 and Q12 the scenarios below. The original wording is kept for reference.

**Formerly BLOCKING (resolved 2026-10-29):**

- **E13 Q1 · US-70 discomfort scenarios conflict with ADR-81 / `07` §7 (before T-159).** Both scenarios still say "the fixed message asks the user to consult a professional"; ADR-81 removed that message. Proposed Gherkin:
  ```gherkin
  Scenario: Open discomfort blocks increases
    Given "Press banca" has an open discomfort of level 1 to 3
    When the coach suggests the next session
    Then no load increase is suggested for "Press banca"
    And no health message is shown

  Scenario: Severe discomfort blocks the exercise
    Given "Press banca" has an open discomfort of level 4 or 5
    When the coach suggests the next session
    Then no suggestion is made for "Press banca"
    And no health message is shown
  ```
  Also decide: (A) the suggestion says nothing about the discomfort (strict ADR-81 "nothing else is shown"), or (B) the suggestion's reason states neutrally that the load is kept because of the open discomfort (no health advice). Recommended: B (the user sees why). Levels 1-3 still allow the reps rule and the 5 % drop (proposed).

- **E13 Q2 · Where does an exercise's rep range come from, and per-session ranges (before T-159).** US-70 says "has a target of 6 to 8 reps", but routines store planned weight and reps per set (ADR-50); only a legacy `target_reps_max` exists, not shown in the editor. Options: (A) add an optional rep range ("Rango de repeticiones", min-max) per routine exercise in the routine editor, set by the block creator for coach routines; (B) progression only for routines created by the coach, whose plan carries the range; (C) derive the range from the planned reps (planned reps = bottom, +2 = top). Recommended: A. With "Ganar fuerza" the block varies ranges within the week (`07` §3), so the range is per routine exercise (session). Proposed extra scenario:
  ```gherkin
  Scenario: Each session uses its own rep range
    Given "Sentadilla" is planned at 3 to 5 reps on Monday and 8 to 10 reps on Thursday
    When the user opens "Sentadilla" on Thursday
    Then the suggestion uses the 8 to 10 range
    And it is based only on earlier sessions with that range
  ```

- **E13 Q3 · "Main lifts" and "main multi-joint lifts"; deload without a plan (before T-163 and T-165).** Neither `07` nor the epic defines them. Proposed: an exercise whose `movement_pattern` is squat, hinge, lunge, horizontal/vertical push or pull (not isolation, core, carry, cardio, other), and for the deload trigger only those done in at least one session in each of the three weeks. Also: (A) a deload is suggested only inside an accepted block (it marks the next plan week), or (B) also without a block (then it marks the next 7 days and shows in the live session). Recommended: A. The 40 % cut rounds to the nearest whole set, at least 1 per exercise (proposed).

- **E13 Q4 · Does the evaluation gate the plan and report before the user tries them (before Checkpoint E13-B is presented)?** The epic says "a function is not enabled until it meets its criteria", but the plan and report are tested at E13-B…E13-D, before T-168. Options: (A) each model task runs its share of the cases (user-approved paid runs) before its checkpoint, and T-168 consolidates and gates US-77; (B) the plan and report are usable at the checkpoints (the validators already enforce the hard rules on every output), and T-168 is the formal gate before US-77 and before the app is shared with other users. Recommended: B (the tasks are written for it).

- **E13 Q5 · Labels that clash with the glossary (before T-158's ux pass).** §10 says commit = "Guardar", never "Aceptar"/"Confirmar"; "Descartar" = abandon unsaved work; deleting saved records = "Eliminar", "Borrar" only for the account and clearing text; R-35 one label per concept, and the tab is "Trainer" while the Gherkin says "asistente". Proposed: add glossary entries "Confirmar" (accept the coach's derived values), "Aceptar" / "Descartar" (decide on a coach proposal: a distinct concept from editing), and rename "Borrar historial del asistente" to "Eliminar historial del entrenador" (or keep the Gherkin literal and add the exception). Recommended: the three entries plus the rename; the Gherkin literals of US-68, US-74, US-75, US-77 updated accordingly.

- **E13 Q11 · US-72 Gherkin for the new `07` §3 rules (before T-163).** Proposed scenarios:
  ```gherkin
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
  ```
  Range of motion stays a technique note in the exercise reason (no scenario proposed).

- **E13 Q12 · US-69 Gherkin for the protein distribution (before T-161).** Proposed:
  ```gherkin
  Scenario: Protein distribution guidance
    Given the user weighs 75 kg
    When the coach proposes the plan
    Then the plan shows about 30 g of protein per meal over at least 4 meals
    And it is shown as guidance, not as a meal plan
  ```

**Non-blocking (resolved 2026-10-29: the default below was approved):**
- **E13 Q6 · When does "the coach run"?** (US-68 re-ask, US-69 calorie change, US-75 deload). Default: the deterministic checks run on each open of the Trainer tab and after saving the weekly check-in, at most once a day; never in the background (ADR-73).
- **E13 Q7 · Where are the "coach settings" (US-76)?** §8 puts every setting in Preferencias, but usage is read-only information. Default: "Uso de IA" from the Trainer root's ⋮ menu (an `AppScreen` with this month's usage and the 2 € limit); alternative: a read-only row in a new Preferencias section.
- **E13 Q8 · A Preferencias save recalculates the calorie target (`04` §1.4).** Default: a save that changes the nutrition goal (including the recalculation after editing personal data) counts as a manual edit and marks the plan's nutrition "edited by you"; it never silently restores the plan.
- **E13 Q9 · One acceptance or two?** `07` §1 says the user accepts the whole plan; US-69 "accepts the plan" and US-72 "Guardar bloque". Default: after T-162 a plan can be accepted for nutrition alone; once the block exists (T-164), "Guardar bloque" accepts the training part and creates the routines; both decisions are stored.
- **E13 Q10 · Leaving the block preview.** Default: no routine is created, the proposal stays `proposed` and can be reopened from Trainer until a new one supersedes it (no discard dialog: it is not an editor).
- **E13 Q13 · Edited activity level and deletion scope.** Default: an activity level edited on the coach card is stored only as the coach's confirmed value (Diario's TDEE keeps `profiles.activity_level`); deleting the coach history ("Eliminar historial del entrenador", Q5) keeps the derived values and the AI usage (not in the Gherkin list; the usage must stay for the budget).

**Assumptions:**
- A1. Proposed release version `v1.4.0` (minor per epic, `06` §6).
- A2. The Trainer tab label "Trainer" (ADR-79) and its icon are checked by ux-reviewer in T-158.
- A3. ux-reviewer writes the Spanish copy beyond the Gherkin literals (suggestion line, progress texts, limit message, outcome and deload labels) within the glossary; the user sees it at the checkpoints.
- A4. Evaluation cases contain no personal data, so they can live in the public repo; otherwise they stay local (planner of T-168 proposes, the PM asks).
