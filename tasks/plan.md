# Implementation Plan: E13 · Performance assistant → `v1.x` (proposed `v1.4.0`)

> **Status:** broken down on 2026-10-29 by the epic-planner and **agreed by the user the same day** with two changes: three checkpoints instead of five, and every recommended option of E13 Q1-Q13 approved (resolved below; amended Gherkin in the epic file; `planning/09-open-questions.md`). 14 tasks (T-157…T-170). No task is blocked.
> **Amendment 2026-10-30 (epic-planner, from the Checkpoint E13-B feedback; E13 Q14-Q22 resolved by the user the same day):** new story US-78 "Antes de empezar" (epic file) as T-175 (backend, M) and T-176 (UI incl. the Trainer tab layout, L). Order: the fix T-177 (plan card missing after "Crear plan") first; then Checkpoint E13-B is re-presented (items 1-9); then T-175, T-176, T-179 (long band and exercise alternatives, user request 2026-11-01; it does not wait for the checkpoints) and the checkpoints E13-B1 and E13-B2 (Q22 B). Fix tasks added since the breakdown: T-171, T-172 (E13-A), T-173 (refinements), T-174, T-177 (E13-B). No task is blocked.

> **Amendment 2026-11-01 (epic-planner, cardio; needs the user's OK before its PR merges):** user request "the app replaces a real trainer; if the trainer would plan cardio, the app must", run before the E13-B1 re-test. New story US-79 (weekly cardio and daily steps targets in the plan) and amended US-72 (cardio as the last exercise of every routine) and US-73 (weekly cardio minutes and steps); ADR-85 proposed; rules and sources final in `07` §3 "Cardio prescription", §9, §10 refs. 29-50 (checked online 2026-11-01 by the fitness-scientist). **Reworked the same day** after the fitness-scientist and ux-reviewer passes: no warm-up anywhere, no cardio days, cardio last in every routine (5-min units, max 60 min, excess "por tu cuenta"), a cardio set is minutes only, 150 min for every intention (editable), steps floor 7 000, cardio swappable across modalities with the same minutes. Final pass the same day: new story US-80 (cardio equipment as its own category, E13 Q28b) as T-184 (M, runs between T-181 and T-182), and the cardio swap is any-to-any (user requirement). Tasks in order T-181 (M), T-184 (M), T-182 (L), T-183 (L), then the proposed **Checkpoint E13-B3** (E13-B1 re-test + cardio, 7 items). E13 Q23-Q31 resolved (Q30 and Q31 by the PM); no blocking question.

> **Amendment 2026-11-01 (epic-planner, replan "the trainer journey"; proposed, needs the user's OK before its PR merges):** the user said E13 was not going in the right direction: the app must replace a real trainer he can trust long-term, and the follow-up (phases over the year above all) is the gap. Design approved by the user and written by the fitness-scientist in `07` §1-§4, §7, §8, §12. New ADR-86 (intake, phases, end-of-block review, breaks), ADR-87 (free text, grounding), ADR-88 ("Entrenador": automatic changes with "Deshacer"; amends ADR-76), ADR-89 (no generic disclaimers, persistent-discomfort note, injury follow-up; amends ADR-81/85, reverses E13 Q29). User decisions E13 Q32-Q43; E13 Q44-Q49 open with recommendations the tasks use. New stories US-81…US-90; US-68…US-75, US-77, US-79 amended. Twelve new tasks T-185…T-194, T-196, T-197 (11 L, 1 M; no S) that also fix every mismatch of the PM's audit of the merged E13 code; T-166…T-169 kept and amended; T-195 and T-173 folded (T-188, T-194); T-170 is the PM closure step. Three new checkpoints (E13-D, E13-E, E13-F) and E13-C rewritten as the last one (9 items); the skipped E13-B3 items go into E13-D. **Aligned 2026-11-02** with the fitness-scientist's `07`/`04` pass (eight audit points resolved and written into the tasks; `07` §12.8 "Life changes" added as US-91/US-92, T-196/T-197; E13 Q44 settled as A, Q45 settled as an offered swap). **User feedback 2026-11-02:** per-user AI cap 3 € (ADR-90, code in T-185); no S tasks; Checkpoint E13-G removed, its items in E13-C.

## Overview
**Since the replan (2026-11-01):** a coach the user trusts for years: it gets to know him ("Para conocerte"), runs each four-week block in a phase of the year (cut, gain, maintenance, recomposition, regain, minimal), applies small changes on its own inside an accepted block (loads, calories in a safe band, deloads) with "Deshacer", and at the end of every block reviews what happened with him and proposes the next block and any phase change, which he accepts. Breaks pause the block; an injured area is trained around with a graded return the user paces. The original overview below stays valid for everything else.

*(Original overview, 2026-10-29; "nothing is applied without consent" is superseded by ADR-88 inside an accepted block.)* A coach that builds a four-week training and nutrition block from the user's intention and preferences, sets the outcomes it can justify, and adjusts the plan with the user's data. Every calculation is deterministic (SQL or TypeScript); the model only selects within validated options and explains.

Sources: `planning/epics/E13-performance-assistant.md` (US-68…US-77 + evaluation), `planning/07-coach-design.md` (every rule and figure, incl. the rules added on 2026-10-29: rest between sets, exercise order, undulating periodization for "Ganar fuerza", range of motion, cardio, protein distribution), `planning/01-scope.md` §3-§4, ADR-76…ADR-82, ADR-25, ADR-29, ADR-40, ADR-73, `04` §1, §3, §4, §6.6, `05` §5, §8-§10. Tasks: `tasks/todo.md` (E13 section).

Carried over from "Notes for later epics": verification of sources, data policy and prices is done (2026-10-29); the 👤 Anthropic key is placed right before T-160, the first task that calls the model.

Out of scope (epic, `01` §5): medical advice or diagnosis, video analysis, meal plans (the protein distribution is guidance only), soreness as a signal, water and voice (Phase 3), background jobs (ADR-73), distribution to other users (written condition, `07` §8).

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR.
- **Model-free first:** US-68 (T-157 backend, T-158 UI) and US-70 (T-159) give Checkpoint E13-A with no paid call.
- **US-73 and US-75 move after US-72** (deviation from the initial guidance, justified by the epic): adherence links workouts to `coach_plan_sessions`, and the deload "changes the week" of a block; both need a saved block (US-72). They merge into T-165 (L): both are deterministic on the plan weeks, and each alone is M.
- **Splits (backend → UI):** US-68 (T-157 SQL math with heavy pgTAP; T-158 Trainer tab and card; ~20 files together). US-69 (T-161 function call, validators, RPCs; T-162 proposal screen and target-weight removal). US-72 (T-163 block call and validators; T-164 preview and "Guardar bloque").
- **US-76** rides with the Edge Function foundation (T-160): the budget check guards every call, so it must exist before the first one.
- **Evaluation** is its own task (T-168, not a story). **US-77** last (T-169), gated by it. **Closure** T-170 is the PM closure step after Checkpoint E13-C (not a developer task).

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
| T-177: Fix: Trainer shows "Crear plan" after a plan was created | E13-B bug (US-69) | core + UI | M | 6-7 |
| T-175: "Antes de empezar" inputs | US-78 backend | backend + core | M | 5 |
| T-176: "Antes de empezar" screen and the Trainer tab layout | US-78 UI (+ US-68 confirmation) | core + UI | L | 7 |
| T-179: Long band and exercise alternatives | User request (training catalogue) | backend + core + UI | L | 8 |
| T-180: Fix: unrealistic calorie surplus in the plan | E13-B1 feedback (US-69) | backend | M | 4 |
| T-181: Weekly cardio and daily steps targets in the plan | US-79 | backend + core + UI | M | 7-8 |
| T-184: Cardio equipment in "Mi entrenamiento" and "Antes de empezar" | US-80 | backend + core + UI | M | 8 |
| T-182: Cardio as the last exercise of every routine of the block | US-72 amended (backend) | backend | L | 4 |
| T-183: Cardio in preview, routine, live session; weekly cardio and steps | US-72 UI + US-73 amended | small backend + core + UI | L | 8 |
| T-185: "Para conocerte" and the free-text block (backend) | US-81, US-89, US-68 am., US-88 | backend + core | L | 5 |
| T-186: "Para conocerte" screen and "Lo que el entrenador sabe de ti" | US-81, US-89 UI | core + UI | L | 7 |
| T-187: Phases: calories and training by phase | US-82 backend, US-69 am. | backend + core | L | 5 |
| T-188: Phases in the plan; generic disclaimers removed app-wide; Perfil and Peso fixes (former T-195, T-173 items) | US-82 UI, US-79 am., US-90 | core + UI | L | 7 |
| T-189: Plan weeks follow the training (breaks pause the block) | US-73 am., US-88 | backend + core + small UI | M | 8 |
| T-190: End-of-block review and the next block (backend) | US-83, US-84, US-88 | backend + core | L | 5 |
| T-191: End-of-block review screen and the next block | US-83, US-84 UI | core + UI | L | 7 |
| T-192: Progression applies on its own, with "Deshacer" | US-70 am., US-85, US-88 | backend + core + UI | L | 8 |
| T-193: Calories and deloads on their own; scheduled deloads; data cleaning | US-69/US-75 am., US-85, US-86 | backend + core + UI | L | 8 |
| T-194: Injury: train around, graded return, persistent-discomfort note; "Similares" fix (former T-173 item) | US-87, US-90 | backend + core + UI | L | 8 |
| T-196: Life changes mid-block (backend) | US-91, US-92 | backend + core | L | 5 |
| T-197: "Revisar el bloque" and "Solo unos días" (UI) | US-91, US-92 UI | core + UI | L | 7 |
| T-166: Weekly coach report (amended: 3 parts, notes, automatic changes, grounding, note) | US-71 am. (+ US-76, US-90) | backend + core + UI | L | 8-9 |
| T-167: Recommendation history, outcomes and deletion (amended) | US-74 am. | backend + core + UI | M | 8 |
| T-168: Coach evaluation: cases, runner and results (amended cases) | Evaluation | backend + 👤 | M | 4 + runs |
| T-169: Coach chat with tools (amended: grounding, free text) | US-77 am. | backend + core + UI | L | 8 |
| T-170: E13 closure | DoD | PM | closure step | PM only |

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
- **Checkpoint E13-C** — rewritten by the replan (see "Checkpoint grouping (replan)" below).

Each checkpoint also has "leave the app and come back: the same tab, screen and draft are restored" and an out-of-scope line. Full item lists are in `tasks/todo.md`. Non-checkpoint 👤 steps: the Anthropic key before T-160; the evaluation cases and the approval of every paid run in T-168. T-157, T-158, T-160…T-164 and T-166…T-168 merge with no checkpoint after them.

## Architecture Decisions
- **One `coach` Edge Function** (T-160): JWT, RLS through the user's token, `ANTHROPIC_API_KEY` only server-side (ADR-17/77); request kinds `plan_nutrition`, `plan_block`, `report`, `chat`; streamed (SSE) responses so the app shows progress; a call timeout below the 150 s wall clock (Free plan, `07` §11) with a clean error; structured JSON output validated server-side, retried once, then an error; the model never writes to the database, accepted proposals are written by RPCs.
- **150 s limit:** the plan is two calls (nutrition and outcomes in T-161; the training block in T-163). T-163's planner measures the block call and splits it further (per two weeks or per week) if it risks the limit.
- **One source for the math:** the derived values (BMR, activity, adaptive expenditure with intake completeness, weight trend with its interval, experience) and the compact summary (`coach_summary`, last 8 weeks, never raw rows) are computed in SQL (T-157), so the app and the function read the same numbers. Nutrition and block rules are TypeScript validators in the function; progression is SQL or TypeScript (T-159 planner).
- **Model picks from server-built candidates:** exercises come from a catalogue list filtered by place and equipment, never free names; outcomes from a validated option set.
- **Coach rules enforced by validators (`07` §3-§4):** frequency (≥ 2 per muscle group when days allow; 1 day = full body), ~10 weekly sets per muscle group, intensity per intention, 1-3 RIR, rest 2-3 min on main multi-joint lifts and 1-2 min on isolation (written into `rest_seconds`), priority multi-joint lifts first, undulating rep ranges within the week only for "Ganar fuerza", no reduction of cardio or steps, full range of motion only as a technique note; calorie floor (ADR-25), rate of loss/gain, protein ≥ 1.6 g/kg with distribution guidance (~0.4 g/kg per meal over ≥ 4 meals, not a meal plan), fat ≥ 20 %, in-block change ≤ 10 % after two weeks outside the trend's interval.
- **Budget:** `coach_usage` per user and month; the function refuses any call that would pass 3 € per user (ADR-90; 2 € until 2026-11-02, changed in T-185) or 5 € in total (dollars counted as euros); the global total comes from an aggregate that exposes no row. Deterministic features never call the function. CI never calls the API (fake client); every real call (smoke test, evaluation) is approved by the user.
- **Cardio (ADR-85 proposed, 2026-11-01):** a new deterministic `cardioRules.ts` sets 150 weekly cardio minutes for every intention (user-editable, plain note below 150) and the daily steps target max(28-day average rounded down to 500, 7 000) (T-181, plan JSON, no new table; one edit RPC). The block adds one cardio slot as the last exercise of every lifting routine, splitting the weekly minutes evenly in 5-min units, at most 60 min per routine, the excess "por tu cuenta"; cardio is outside "Duración de la sesión", so the lifting is untouched; catalogue `cardio` exercises with a duration metric, filtered by the five new cardio equipment values (T-184: `cardio_bike`, `treadmill`, `elliptical`, `rower`, `cardio_other`; `machine` is lifting only) plus `bodyweight`, default ranking by modality (`07` §3); the swap is any-to-any: alternatives derived from category + equipment, same minutes, never from the T-179 pairs table; the model only picks among candidates (T-182). Planned sets gain one nullable planned duration through `save_routine` (`target_reps` nullable only for duration sets); the cardio set reuses the duration metric types, `SetRow`, `SessionSetRow` and `MetricTypeLabels` (minutes only, no rest timer); `coach_plan_weeks` adds logged cardio minutes and steps per week (T-183). No warm-up anywhere (Q27). Calories ignore the planned cardio (Q25).
- ~~**Suggestions never rewrite routines:** progression and deload are shown in the live session; "Actualizar automáticamente" (ADR-70) keeps copying what was performed.~~ **Replan (ADR-88):** inside an accepted, not-ended block the progression (computed on the server, T-192), the calorie changes in the safe band and the deloads apply on their own; every one is a `coach_changes` row with its `before` value, reason and "Deshacer" / "Cancelar descarga" (one table, one `CoachChangeRow`, built in T-192); outside a block the suggestion stays as before; ADR-70 unchanged.
- **Replan: the trainer journey (ADR-86, ADR-87, ADR-89).** `coach_intake` and a free-text block in `coach_summary` with user exclusions (T-185); experience = max(self-reported, history), `situation` (intake or 28 days without a session), known maintenance as the expenditure prior. `coach_plans.phase` + options; phase calories (deficit cap 500) and phase training (minimal, regain, cut volume) in `nutritionRules`/`blockRules` (T-187). One SQL helper for plan weeks that skip calendar weeks without sessions, used by adherence, linking, deload and the block end (T-189). `coach_block_reviews`, a deterministic `coach_block_review_inputs`, phase triggers and next-block volume rules in code, a `block_review` model call that picks only eligible options, then the existing plan and block calls (T-190). One shared grounding validator (feature list + summary) for plan, block, review, report and chat reasons (T-190, reused by T-166/T-169). `coach_return_steps` and `coach_health_notes` (T-194). New kinds `intake_extract` and `block_review` (Sonnet 5.5), both budget-checked.
- **Plan storage (epic §Tables):** `coach_derived_values` (T-157), `coach_usage` (T-160), `coach_plans` and `coach_recommendations` (T-161), `coach_plan_sessions` (T-163), adherence and deload columns (T-165), `coach_reports` (T-166), `coach_outcomes` (T-167), `coach_conversations` (T-169). Every table cascades on account deletion (ADR-29) and is covered by `delete_account_cascade.test.sql`.
- **Target weight retired (ADR-78)** in T-162: UI removed first, then `profiles.target_weight_kg` dropped in the same PR's migration; "Peso" shows the plan's weight outcome.
- **UI:** fourth tab "Trainer" (ADR-79) in T-158 (`05` §5 and the §8 "Decided" line updated). Shared pieces built once and reused (R-39): provisional label (T-158), `CoachProposalCard` and the streamed-progress state (T-162), reused by T-164/T-165/T-166/T-169. Screens on `AppScreen`, sheets on the four archetypes, `NavigationRow` "Informe semanal", history delete with the R-08 many-records dialog (G5 allow-list), a new chat pattern recorded in `05` §9 (T-169).

## Phases
### Phase 1: Model-free coach (US-68, US-70)
- [x] T-157: Coach summary and derived values in SQL — L
- [x] T-158: Trainer tab and the derived-values card — M
- [x] T-159: Rep range and progression suggestion in the live session — L
  - **Checkpoint E13-A 👤 (7 items)**

### Phase 2: The coach's plan: function, nutrition, block, plan weeks (US-76, US-69, US-72, US-73, US-75)
*(Ticks fixed 2026-11-02: every task of this phase is merged. Behaviour of T-160 (2 € cap), T-161/T-162 (accept-first calorie change), T-163 (beginner sets, no carry-over) and T-165 (calendar weeks, accept-first deload) is superseded by the replan tasks noted in brackets.)*
- [x] 👤 Anthropic account, spending limit, `ANTHROPIC_API_KEY` secret
- [x] T-160: `coach` Edge Function, AI usage and the monthly budget — L (cap → 3 € in T-185, ADR-90)
- [x] T-161: Plan nutrition and outcomes (backend) — L (deficit cap 500 and phases → T-187; auto calorie change → T-193)
- [x] T-162: Plan proposal screen, nutrition acceptance, target weight retired — L (calorie-change card → T-193)
- [x] T-163: Block creator (backend) — L (volume, carry-over, exclusions, grounding → T-187, T-190, T-194)
- [x] T-164: Block preview and "Guardar bloque" — L
- [x] T-165: Plan weeks: adherence and the deload suggestion — L (weeks → T-189; auto and scheduled deload → T-193)
- [x] T-174: Fix: "Crear plan" 422 explained — M (merged #401)
- [x] T-177: Fix: Trainer shows "Crear plan" after a plan was created — M
  - **Checkpoint E13-B 👤 (9 items) re-presented** after T-177 — approved 2026-10-30
- [x] T-175: "Antes de empezar" inputs (US-78 backend) — M (intake rows → T-185/T-186)
- [x] T-176: "Antes de empezar" screen and the Trainer tab layout (US-78 UI) — L
- [x] T-179: Long band and exercise alternatives — L
- [x] T-180: Fix from Checkpoint E13-B1: unrealistic calorie surplus in the plan — M
  - **Checkpoint E13-B1 👤** approved 2026-11-01 except the T-180 re-test (now E13-D item 5); **Checkpoint E13-B2 👤** approved 2026-11-01.

### Phase 2b: Cardio in the plan (US-79, US-72/US-73 amended; ADR-85 proposed, 2026-11-01)
- [x] Sources 29-50 of `07` §10 verified and dated (fitness-scientist, 2026-11-01)
- [x] E13 Q23-Q31 resolved (2026-11-01; Q30 and Q31 by the PM)
- [x] T-181: Weekly cardio and daily steps targets in the plan — M
- [x] T-184: Cardio equipment in "Mi entrenamiento" and "Antes de empezar" — M
- [x] T-182: Cardio as the last exercise of every routine of the block (backend) — L
- [x] T-183: Cardio in the preview, routine editor and live session; weekly cardio and steps — L (merged #430; medical line → T-188)
  - **Checkpoint E13-B3 👤** skipped by the user 2026-11-01; its items are Checkpoint E13-D items 5-6.

**Sizing of cardio:** US-79 is one vertical slice (T-181, M, about 15 files: a deterministic rules module, plan JSON, the minutes edit, two display spots, no new table). The block side (US-72 amended) is about 25 files with heavy backend (block rules, candidates and the cross-modality swap, validators, a planned-duration column through `save_routine`/`start_workout_session`/`save_coach_block`) and heavy UI (preview, swap list, routine editor, live session), so it splits backend (T-182) → UI (T-183); the US-73 weekly cardio and steps rides with T-183 (same screens, a small RPC extension) instead of an S task. US-80 is its own vertical slice (T-184, M, about 14 files: enum + re-tag migration, coach regression checks, `Equipment` enum and labels, the "Cardio" chip group, the "Antes de empezar" row); merging it into T-181 would pass ~25 files across two domains and T-182 is already L. It keeps the next free ID (T-184) so the T-182/T-183 references in `07` and `09` stay valid. Collected test items: plan re-test 1, activity targets and editing 1, steps 1, cardio equipment + block cardio and any-to-any swap 1, save/edit 1, train 1, week 1 = 7, one journey, one checkpoint.

**Sizing of US-78:** one story, split backend → UI because the whole slice is about 28 files: T-175 (one read-only RPC shared by the screen and the `coach` function's precondition, plus the repository; M, about 11 files) and T-176 (the screen in two modes, the Trainer root rework and the proposal clean-up; L, about 17 files). The root rework stays inside T-176: it moves the "Tus valores" card into the new screen, so splitting it would make an S task touching the same files.

### Phase 2c: Getting to know you and phases (US-81, US-89, US-82; replan 2026-11-01)
- [ ] 👤 The user's OK on the replan (ADR-86…ADR-90, stories, tasks, checkpoints D/E/F and the last E13-C)
- [ ] T-185: "Para conocerte" and the free-text block (backend; also the 3 € cap, ADR-90) — L
- [ ] T-186: "Para conocerte" screen and "Lo que el entrenador sabe de ti" — L
- [x] T-187: Phases: calories and training by phase — L
- [x] T-188: Phases in the plan; generic disclaimers removed app-wide; Perfil and Peso fixes — L
  - **Checkpoint E13-D 👤 "Getting to know you, your phase, and your plan with cardio" (7 items; carries E13-B3 and the E13-B1 re-test)** — proposed

### Phase 2d: The end-of-block review (US-73 am., US-83, US-84, US-88)
- [ ] T-189: Plan weeks follow the training — M
- [ ] T-190: End-of-block review and the next block (backend) — L
- [ ] T-191: End-of-block review screen and the next block — L
  - **Checkpoint E13-E 👤 "Your block review: what happened, the next block and a new phase" (6 items)** — proposed

### Phase 2e: The coach adjusts on its own; injury (US-85, US-86, US-87, US-90; US-69/US-70/US-75 am.)
- [ ] T-192: Progression applies on its own, with "Deshacer" — L
- [ ] T-193: Calories and deloads on their own; scheduled deloads; data cleaning — L
- [ ] T-194: Injury: train around, graded return, persistent-discomfort note; "Similares" fix — L
  - **Checkpoint E13-F 👤 "The coach adjusts on its own, and trains around an injury" (7 items)** — proposed

### Phase 3: Life changes, report, history, evaluation, chat and closure (US-91, US-92, US-71, US-74, US-77; amended by the replan)
- [ ] T-196: Life changes mid-block (backend) — L
- [ ] T-197: "Revisar el bloque" and "Solo unos días" (UI) — L (ux-reviewer: the offer, the three choices, "Solo unos días")
- [ ] T-166: Weekly coach report — L (no report for a week without data, `07` §12.8 row 10)
- [ ] T-167: Recommendation history, outcomes and deletion — M
- [ ] T-168: Coach evaluation — M (👤 cases and each paid run)
- [ ] T-169: Coach chat with tools — L (only after T-168 passes)
  - **Checkpoint E13-C 👤 — the last one (9 items: life changes 4, report 1, history and delete 1, cost 1, chat 1, final regression 1); releases T-170** — proposed
- [ ] T-170: E13 closure — PM closure step

**Checkpoint grouping (replan, amended 2026-11-02):** collected items: intake 2, phases 2, carried plan and cardio re-test 2 (merged), Perfil and Peso fixes 1 → E13-D (7); review 2, priorities 1, phase change 1, next block 1, paused week 1 → E13-E (6); loads 1, calories 1, deloads 1, injury 2, unresolved and no generic warnings 1, "Similares" 1 → E13-F (7); life changes 4 (grouped from 7), report 1, history and delete 1, cost 1, chat 1, final regression 1 → E13-C (9). E13-G was removed by the user (2026-11-02): its items are E13-C 1-4. Items whose trigger needs weeks of data say "if your data shows it" and are covered by host tests.

Internal milestones (not 👤 checkpoints): per task lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest`, pgTAP and `deno test` green; after each migration or function change, `deploy-supabase` green on `main` with data intact before the next task or checkpoint; T-160 smoke call approved by the user.

## Dependencies
```
v1.3.0 ─► T-157 ─► T-158 ─► T-159 ─► [E13-A 👤]
       ─► [👤 key] ─► T-160 ─► T-161 ─► T-162 ─► T-163 ─► T-164 ─► T-165 ─► T-174
       ─► T-177 ─► [E13-B 👤 re-presented] ─► T-175 ─► T-176 ─► T-179 ─► T-180 ─► [E13-B1 👤] ─► [E13-B2 👤]
       ─► [user OK on the cardio breakdown] ─► T-181 ─► T-184 ─► T-182 ─► T-183 ─► [E13-B3 👤 = E13-B1 re-test + cardio]
       ─► [E13-B3 skipped] ─► [user OK on the replan] ─► T-185 ─► T-186 ─► T-187 ─► T-188 ─► [E13-D 👤]
       ─► T-189 ─► T-190 ─► T-191 ─► [E13-E 👤] ─► T-192 ─► T-193 ─► T-194 ─► [E13-F 👤]
       ─► T-196 ─► T-197 ─► T-166 ─► T-167 ─► T-168 (👤 cases, paid runs) ─► T-169 ─► [E13-C 👤] ─► T-170 (PM closure) ─► v1.4.0
```
- Replan dependencies: T-187 needs T-185's `situation`; T-190 needs T-189's plan weeks (block end) and T-187's phases; T-193 needs T-192's `coach_changes` and `CoachChangeRow` and T-189's weeks; T-194 needs T-185's injured area, T-192's changes and T-190's summary in the block prompt; T-166 needs T-192/T-193 (changes listed), T-194 (note), T-190 (grounding validator) and T-196 (`has_week_data`); T-196 needs T-190 (early review) and T-192 (`coach_changes`).
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
| "Antes de empezar" says complete while the function refuses (or the reverse) | High | One server list (T-175 RPC) read by both the screen and the function precondition; shared fixtures in pgTAP and `deno test` |
| Returning from an existing editor does not refresh the screen | Medium | Reload on resume in `BeforeStartViewModel`; restoration test of the editor round-trip (T-176) |
| Moving the derived values off the root breaks the US-68 gate or its tests | Medium | Q15/Q21 decide where confirmation lives; US-68 Gherkin amended in the same docs PR; T-176 sweeps `DerivedValuesCard` users |
| Planned cardio stacks a deficit on top of the intake anchor (the T-180 shape) | High | Q25: calories ignore the planned cardio, proposed or edited; the adaptive expenditure and the in-block change follow the logged data; T-181 regression keeps the T-180 cases |
| Cardio shrinks the lifting slots (`04` §9.3) | Medium | Q30: cardio is outside "Duración de la sesión"; T-182 asserts identical lifting with and without cardio for every day count and length; the preview shows the total time so longer sessions are visible |
| Planned sets have no duration today and `target_reps` is required | Medium | One nullable column plus a reps-or-duration check through `save_routine` (T-182); T-183 reuses `SetRow`/`SessionSetRow` with the R-47 control and the placeholder, nothing else in the editor |
| A cardio swap reuses the T-179 alternatives table (only 2 cardio pairs) and leaves the user without a usable machine | Medium | Any-to-any: T-182 derives cardio alternatives from category + equipment (plus walking and running), `save_coach_block` accepts any of them and rejects cardio on lifting slots; tests for swap and defaults |
| Re-tagging cardio machines changes lifting candidates or gives saved users cardio they did not pick | Medium | T-184: no saved list gains a value, an old `machine` is never cardio; regression of lifting candidates, validators, `coach_plan_sessions`, volume options and `exercises.test.sql` |
| The rest timer starts after a cardio set | Low | T-183 acceptance and a ViewModel test: no rest timer after a cardio set |
| (Replan) Eight `07` points under-specified (open level 4-5 discomfort, experience vs sets, cardio per phase, partial days, deficit reference, where progressed loads live, week-4 collisions, undo vs band) | High | Resolved by the fitness-scientist (2026-11-02) and written into T-187, T-190, T-192, T-193, T-194 |
| (Replan) "Revisar el bloque" (life changes) and the early review share a concept with "Bloque nuevo" | Medium | One label per concept (R-35): "Revisar el bloque" = the life-change offer, "Bloque nuevo" = the early review; ux-reviewer confirms in T-191 and T-197 |
| (Replan) "Adaptar el bloque" rewrites routines mid-block | Medium | A rule with a preview, applied on the user's tap through `save_routine`, one `coach_changes` row with "Deshacer"; pgTAP undo |
| (Replan) An automatic change the user did not expect erodes trust | High | Small and bounded changes only inside an accepted block, each with its reason and "Deshacer"; new blocks and phase changes always need acceptance; every change stored and listed in the report and the history |
| (Replan) Progressed loads need the plate and dumbbell settings on the server (today client-side) | Medium | T-192 moves the increment server-side with one shared implementation; increment cases moved to pgTAP |
| (Replan) Recounting plan weeks breaks the user's in-progress block | Medium | T-189 migration keeps every link; pgTAP fixture of the user's shape; `deploy-supabase` checks the data |
| (Replan) Phase triggers and review rules are only testable after weeks of data | Medium | Table-driven pgTAP and `deno test`; "Revisar el bloque" opens the review early so the user can try it at E13-E |
| (Replan) The model invents features or reads too much into free text | High | Shared grounding validator (T-190), free text never changes a figure or trigger, extracted intake fields used only after confirmation, evaluation cases (T-168) |
| (Replan) "Deshacer" collides with R-20 ("never Deshacer") | Low | A distinct glossary entry for undoing an automatic coach change (T-192, ux-reviewer); R-20 unchanged for deletion |
| (Replan) The summary passes 16 384 bytes with the free-text block | Medium | 2 000-byte block cap; T-185 re-budgets and tests the worst case |
| (Replan) Cost of the new model calls | Low | Intake extraction (rare) and one review call per block; ≈ 0,1 € per user and month extra (heavy user ≈ 3,1 €); the per-user cap is raised to 3 € (ADR-90, T-185) and still stops the chat first; the 5 € total binds first with two heavy users |

## Open Questions

**Replan (2026-11-01), waiting for the user's OK:** the replan as a whole (ADR-86…ADR-90, US-81…US-92, T-185…T-194, T-196, T-197) and the checkpoints E13-D, E13-E, E13-F and the last E13-C (E13-G removed by the user, 2026-11-02).

**Replan, open with a recommendation the tasks use (not blocking; `planning/09-open-questions.md`):**
- **E13 Q44** settled by `07` §3: A (server-computed, written to the block routines, "Deshacer"). T-192.
- **E13 Q45** settled by `07` §12.8 row 7: the swap is offered and applied on the user's tap. T-194.
- **E13 Q46** when the intake is asked: recommended once before the first "Crear plan", every field optional, "Ahora no", editable from "Tus datos". T-186.
- **E13 Q47** a block ends without a review: recommended nothing starts on its own, a card stays, no automatic change past the block; "Revisar el bloque" also opens it early. T-190, T-191.
- **E13 Q48** what "Eliminar historial del entrenador" deletes: recommended plans, phases, reviews, recommendations, automatic changes, reports, conversations; keeps the intake, derived values, exclusions and AI usage. T-167.
- **E13 Q49** the report's "one change": recommended one user action from server-built candidates, never a plan change. T-166.
- Fitness-scientist pass on eight `07` points: done 2026-11-02, tasks aligned.
- **Needs ux-reviewer (not the user):** "Revisar el bloque" and its three choices, "Solo unos días" (T-197), the level 4-5 swap offer (T-194), the "Bloque nuevo" vs "Revisar el bloque" labels (T-191).

**Resolved by the user 2026-11-01 (replan):** E13 Q32-Q43 (intake, free text, regain default, phases, autonomy, deloads, priorities, health messages (Q29 reversed), injury and breaks, data cleaning, grounding, cost).

**Formerly waiting for the user's OK (cardio breakdown, 2026-11-01):**
- **Checkpoint E13-B3 (proposal):** the E13-B1 re-test presented together with the cardio items after T-183 (7 items), instead of a separate re-test. *(Skipped by the user; items carried into E13-D.)*

**Resolved 2026-11-01 (`planning/09-open-questions.md`; rules in `07` §3):** Q23 150 min for every intention, band 150-300 for "Perder grasa", editable, note below 150 · Q24 steps max(28-day average rounded down to 500, 7 000), every age, not editable · Q25 the planned cardio does not change the calories · Q26 cardio last in every lifting routine, 5-min units, max 60 min, excess "por tu cuenta", no cardio days (user) · Q27 no warm-up anywhere (user) · Q28 continuous "suave" only, no intervals or heart-rate zones; Q28b default modality ranking and cross-modality swap with the same minutes · Q29 static medical line at the end of the cardio help sheet · Q28b cardio equipment as its own category (five values), default order, swap within the cardio equipment plus walking and running; made any-to-any by the user · Q30 "Duración de la sesión" does not include the cardio; the preview shows each routine's total time (PM) · Q31 the weekly minutes are edited in a form sheet on the "Cardio" row, on the proposal and in "Tu plan"; an edit after saving changes the target and the next block, not the saved routines (PM, on the ux-reviewer and fitness-scientist recommendation).

**Resolved by the user on 2026-10-30 (raised with US-78; recorded in `planning/09-open-questions.md`; US-78 and US-68 amended in the epic file):**
- **E13 Q14 · What the screen shows.** A summary of the data the plan or block uses; tap opens the full details. Not an interview: the screen only flags what is wrong or missing and where to fix it.
- **E13 Q15 · Confirmation.** "Crear plan" confirms the shown values and starts. With an accepted or pending plan, the screen says the new plan replaces it ("Sustituirá tu plan actual" or similar; ux-reviewer finalises).
- **E13 Q16 · Copy.** As recommended, with flag wording ("Falta: elige tu objetivo en Mi entrenamiento"), not "complete it here".
- **E13 Q17-Q18, Q21 · As recommended.** Detail rows open the existing editors; a weigh-in older than 14 days gets a caution note, not blocking; the derived-values card leaves the proposal.
- **E13 Q19 · Two modes.** **E13 Q20 · Option A** (plan hub, "Tus datos" row). **E13 Q22 · Option B** (T-175, T-176, then E13-B1 and E13-B2), with the fix T-177 first and E13-B re-presented after it.

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
