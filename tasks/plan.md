# Implementation Plan: E13 · Performance assistant → `v1.x` (proposed `v1.4.0`)

> **Status:** broken down on 2026-10-29 by the epic-planner and **agreed by the user the same day** with two changes: three checkpoints instead of five, and every recommended option of E13 Q1-Q13 approved (resolved below; amended Gherkin in the epic file; `planning/09-open-questions.md`). 14 tasks (T-157…T-170). No task is blocked.
> **Amendment 2026-10-30 (epic-planner, from the Checkpoint E13-B feedback; E13 Q14-Q22 resolved by the user the same day):** new story US-78 "Antes de empezar" (epic file) as T-175 (backend, M) and T-176 (UI incl. the Trainer tab layout, L). Order: the fix T-177 (plan card missing after "Crear plan") first; then Checkpoint E13-B is re-presented (items 1-9); then T-175, T-176, T-179 (long band and exercise alternatives, user request 2026-11-01; it does not wait for the checkpoints) and the checkpoints E13-B1 and E13-B2 (Q22 B). Fix tasks added since the breakdown: T-171, T-172 (E13-A), T-173 (refinements), T-174, T-177 (E13-B). No task is blocked.

> **Amendment 2026-11-01 (epic-planner, cardio; needs the user's OK before its PR merges):** user request "the app replaces a real trainer; if the trainer would plan cardio, the app must", run before the E13-B1 re-test. New story US-79 (weekly cardio and daily steps targets in the plan) and amended US-72 (cardio as the last exercise of every routine) and US-73 (weekly cardio minutes and steps); ADR-85 proposed; rules and sources final in `07` §3 "Cardio prescription", §9, §10 refs. 29-50 (checked online 2026-11-01 by the fitness-scientist). **Reworked the same day** after the fitness-scientist and ux-reviewer passes: no warm-up anywhere, no cardio days, cardio last in every routine (5-min units, max 60 min, excess "por tu cuenta"), a cardio set is minutes only, 150 min for every intention (editable), steps floor 7 000, cardio swappable across modalities with the same minutes. Final pass the same day: new story US-80 (cardio equipment as its own category, E13 Q28b) as T-184 (M, runs between T-181 and T-182), and the cardio swap is any-to-any (user requirement). Tasks in order T-181 (M), T-184 (M), T-182 (L), T-183 (L), then the proposed **Checkpoint E13-B3** (E13-B1 re-test + cardio, 7 items). E13 Q23-Q31 resolved (Q30 and Q31 by the PM); no blocking question.

> **Amendment 2026-11-01 (epic-planner, replan "the trainer journey"; proposed, needs the user's OK before its PR merges):** the user said E13 was not going in the right direction: the app must replace a real trainer he can trust long-term, and the follow-up (phases over the year above all) is the gap. Design approved by the user and written by the fitness-scientist in `07` §1-§4, §7, §8, §12. New ADR-86 (intake, phases, end-of-block review, breaks), ADR-87 (free text, grounding), ADR-88 ("Entrenador": automatic changes with "Deshacer"; amends ADR-76), ADR-89 (no generic disclaimers, persistent-discomfort note, injury follow-up; amends ADR-81/85, reverses E13 Q29). User decisions E13 Q32-Q43; E13 Q44-Q49 open with recommendations the tasks use. New stories US-81…US-90; US-68…US-75, US-77, US-79 amended. Twelve new tasks T-185…T-194, T-196, T-197 (11 L, 1 M; no S) that also fix every mismatch of the PM's audit of the merged E13 code; T-166…T-169 kept and amended; T-195 and T-173 folded (T-188, T-194); T-170 is the PM closure step. Three new checkpoints (E13-D, E13-E, E13-F) and E13-C rewritten as the last one (9 items); the skipped E13-B3 items go into E13-D. **Aligned 2026-11-02** with the fitness-scientist's `07`/`04` pass (eight audit points resolved and written into the tasks; `07` §12.8 "Life changes" added as US-91/US-92, T-196/T-197; E13 Q44 settled as A, Q45 settled as an offered swap). **User feedback 2026-11-02:** per-user AI cap 3 € (ADR-90, code in T-185); no S tasks; Checkpoint E13-G removed, its items in E13-C.

> **Amendment 2026-11-02 (epic-planner, Checkpoint E13-D feedback; the checkpoint stays open):** the user found the intake too complex ("keep the free text and remove all this complexity. We have built something we don't understand"), a save error in "Para conocerte", rows that all open one big screen, an intake that opens on its own, and a third generic "Ejercicio" label. The same day the user also **removed "Recuperar" (regain, returning after a break) entirely, deferred to Phase 3**. Three new tasks before T-189, in order: **T-198** (L: a minimal intake of three inputs, "Tiempo entrenando", "Calorías de mantenimiento" and the free text; the extraction, situation, injured area, pre-break bests and cutting calories removed from the UI and the data model; one row one editor; the free text in the `plan_block` request too; the save bug reproduced and pinned), **T-200** (M: the regain phase removed from rules, validators, copy and the phase type, stored regain plans moved to maintenance with their calories unchanged) and **T-199** (M: one exercise-name resolver used by every screen, with a `check-ux-conventions.sh` guard). New `05` rules R-53 (ask only what is used), R-54 (one exercise-name resolver), R-55 (one row one editor, nothing opens on its own). E13 Q46 superseded by the user; Q50 and Q51 resolved by the removal; Q52 (injury without an area) and the `07` regain clean-up go to the fitness-scientist (before T-194 and T-190). US-81 rewritten; US-68, US-78, US-82, US-83, US-84, US-87, US-88, US-89, US-91 amended; T-185…T-188 superseded in part; T-190, T-193, T-194, T-196, T-197, T-166, T-168 and Checkpoints E13-D, E13-E, E13-F, E13-C adjusted. Checkpoint E13-D is re-presented after T-199 with its items rewritten (still 7).

> **Amendment 2026-11-03 (epic-planner, T-202 decision checkpoint "how the coach builds blocks"; proposed, needs the user's OK before its PR merges):** the user found blocks biased and template-like (4 days ⇒ always "Torso/Pierna" A/B, no lateral raise). Decided 2026-11-03 (E13 Q53): **the model builds the whole block and one server routine checker validates it** (science in `07` §1, §3 "Complete routine", §9, fitness-scientist), plus seven UX decisions. New **ADR-93** (model-built block, routine checker H1-H7, folder review, `shoulder_abduction`) and **ADR-94** (start "Desde cero" with 2-3 split options or "Desde una carpeta", a block is a normal folder once saved, "Eliminar bloque" without a new plan, favourite exercises), both proposed; ADR-76, ADR-83 and ADR-86 amended. New stories US-93…US-96; US-72 amended; US-83 note; US-92 "My swaps stay" void; `04` §9 rewritten. Six new tasks **T-203** (remove a block, M), **T-204** (favourites, M), **T-205** (checker and model-built block, backend, L), **T-206** (split options and the new preview, UI, L), **T-207** (folder review and block from a folder, backend, L), **T-208** (folder review screen and start choice, UI, L). **Checkpoint E13-D** is re-presented first with 6 items (block items moved); two new checkpoints **E13-H** (after T-206, 7 items) and **E13-I** (after T-208, 7 items) *(merged into one E13-H after T-208 by the user the same day)*; then T-189 onward *(moved to E14 the same day)*. T-190, T-191, T-192, T-194, T-196, T-197 and T-168 amended (notes in `tasks/todo.md`). E13 Q54-Q57 raised (answers in the next amendment).

> **Amendment 2026-11-03, later the same day (user answers):** E13 Q54 and Q57 accepted as recommended; Q56: "Tengo poco tiempo" and the minimal phase removed (folded into T-205/T-206); **one block checkpoint E13-H after T-208** (the proposed E13-H and E13-I merged: "no point testing partial plans"). **E14 split (user: "we're complicating things; we don't have a proper block yet"):** the block follow-ups (T-189…T-194, T-196, T-197, Checkpoints E13-E and E13-F, US-83…US-88 follow-up parts, US-90…US-92) moved to a new backlog epic **E14 · Block follow-up** (`planning/epics/E14-block-follow-up.md`), re-planned after the user approves the new block. E13 now ends: E13-D re-presented → T-203…T-208 → E13-H → T-166 → T-167 → T-168 → T-169 → E13-C (5 items) → T-170. T-166 builds the grounding validator and `has_week_data` (formerly T-190/T-196); the follow-up parts of T-166, T-167 and T-168 moved to E14.

> **Amendment 2026-11-04 (planner, T-216, Checkpoint E13-H feedback 4; user decisions; needs the user's OK before its PR merges):** "Revisar carpeta" (US-95) becomes a **deterministic client-side computation**: a 0-10 score plus a checklist, no language model, no Edge Function call, no tokens, no 502, no "Aplicar en una copia", no revised routine. Definition final in `07` §3 "Folder review". New **ADR-97** supersedes the review part of ADR-93 only. "Block from a folder" (T-207/T-208, `purpose: block`) is untouched and keeps the server review call, so the whole server model path (kind, `purpose` parsing, prompt, schema, validators, stored reviews) **stays untouched**; only review-screen-only code goes (apply path, UI, strings, tags, tests, and the SQL apply functions if a Grep proves no other caller). A data audit of what the checks read (muscles, `counts_as_strength` on the client, patterns, rep-range fallback to planned reps, custom defaults) is part of slice 1, with a fix migration only if gaps are found. Two slices: T-216a core (calculator, data flag, repository cleanup), T-216b UI (new screen, deletions). Detail: `.workflow/current_task.md`.

> **Amendment 2026-11-04 (epic-planner, the deterministic block builder; ADR-99 proposed; the tasks and checkpoints need the user's OK before the breakdown PR merges):** after T-219 the user decided that block creation has no model call: a split for the days, a routine type per day, sections with sets, reps, RPE and rest computed by rule, the user picks one exercise per slot until 100 %; "Desde una carpeta" reaches the same 100 % on a copy and saves a new folder, refused when its routine count differs from "Días por semana" (`07` §3 "Block builder", E13 Q60). Five tasks: **T-221** (L, builder core and save; rewrites US-72, US-93 and the US-87 discomfort scenario and marks Q55 first), **T-222** (L, "Desde cero" screens; retires the client model flow), **T-223** (M, "Desde una carpeta" + the R-57 `?` on the folder-review rows; retires the client `purpose: block`), **T-224** (M, server removal of the model-built block + docs sweep), **T-225** (M, R-57 sweep of the other screens; user option B), then one **Checkpoint E13-K** (9 items, user decision 2026-11-04: a single checkpoint at the end; closes E13-H, whose postponed items 3-5 and 7 are rewritten into it; releases T-167). No open question on the builder.

> **Amendment 2026-11-04, later (user):** the weekly report **T-166** (US-71, its US-76 report fallback, the US-90 note in the report, E13 Q49) moves to the **E14 backlog** like the other follow-ups; T-167, T-168 and T-169 stay. **Later the same day (user): E13 finishes at Checkpoint E13-K.** T-167 (history, US-74), T-168 (evaluation), T-169 (chat, US-77) and Checkpoint E13-C also move to the E14 backlog, where the user will evaluate the future of the AI trainer. E13 now ends: T-221 → T-222 → T-223 → T-224 → T-225 → E13-K (final E13 regression, closes E13-H) → T-170. The grounding validator, T-168's scope and E13-C's "3 €" cost wording are notes for the E14 re-plan, not open questions now.

## Overview
**Since ADR-99 (2026-11-04):** blocks are built by rules the user can read, with no model call and no cost: the app picks the split options and computes every section's sets, reps, RPE and rest; the user chooses the split, the routine type per day and every exercise, and saves at 100 %. The same bar applies to a block started from one of the user's folders, fixed on a copy. The T-202 overview below is superseded for block creation (the model no longer builds blocks; the checker no longer validates a model block).

**Since the T-202 replan (2026-11-03):** the coach designs blocks like a trainer who knows the user: from scratch (the user picks one of 2-3 routine types) or from one of his own folders, built by the model from his routines, history, previous blocks, favourites and discomforts, and checked by one deterministic routine checker (weekly sets, sessions, side deltoid, knee extension, rep ranges, target RPE, time, equipment, order). The same checker reviews any folder of the user. Once saved, a block's routines are his to edit, and he can remove a block without a new plan.

**Since the replan (2026-11-01):** a coach the user trusts for years: it gets to know him ("Para conocerte"), runs each four-week block in a phase of the year (cut, gain, maintenance, recomposition, minimal; regain removed 2026-11-02, Phase 3), applies small changes on its own inside an accepted block (loads, calories in a safe band, deloads) with "Deshacer", and at the end of every block reviews what happened with him and proposes the next block and any phase change, which he accepts. Breaks pause the block; an exercise with a severe discomfort is trained around with a graded return the user paces (since 2026-11-02 from logged discomforts only: the intake asks only what changes a figure, R-53). The original overview below stays valid for everything else.

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
| T-198: A minimal intake, one row one editor, the free text in the plan and the block, the save fix (E13-D feedback) | US-81 rewritten, US-78 am., US-89 am. | backend + core + UI | L | 8 |
| T-200: Remove the "Recuperar" phase (user, 2026-11-02; Phase 3) | US-82, US-88 am. | backend + core + UI | M | 8 |
| T-199: One exercise-name resolver and its guard (E13-D feedback; R-54) | cross-cutting fix | core + UI | M | 7 |
| T-201: Barra Z, one "Máquina de cardio", barbell/EZ/dumbbell alternatives | E13-D re-test fix (ADR-92) | backend + core + UI | L | 8 |
| T-202: 👤 Decision checkpoint: how the coach builds blocks | user decision | 👤 | — | — |
| T-203: "Eliminar bloque": remove a block without a new plan | US-94 | backend + core + UI | M | 8 |
| T-204: Favourite exercises: star, first in the picker and the swap list | US-96 | backend + core + UI | M | 8 |
| T-205: The model builds the block; one routine checker; `shoulder_abduction` | US-72 am., US-93 (scratch), US-96 (coach) | backend + core | L | 6 |
| T-206: Split options and the new block preview | US-93 (scratch), US-72 am. UI | core + UI | L | 7 |
| T-207: Folder review and a block from a folder (backend) | US-95, US-93 (folder) | backend + core | L | 5 |
| T-208: Folder review screen, start choice, block from a folder (UI) | US-95, US-93 UI | core + UI | L | 7 |
| T-189: Plan weeks follow the training (breaks pause the block) | US-73 am., US-88 | backend + core + small UI | M | 8 |
| T-190: End-of-block review and the next block (backend) | US-83, US-84, US-88 | backend + core | L | 5 |
| T-191: End-of-block review screen and the next block | US-83, US-84 UI | core + UI | L | 7 |
| T-192: Progression applies on its own, with "Deshacer" | US-70 am., US-85, US-88 | backend + core + UI | L | 8 |
| T-193: Calories and deloads on their own; scheduled deloads; data cleaning | US-69/US-75 am., US-85, US-86 | backend + core + UI | L | 8 |
| T-194: Injury: train around, graded return, persistent-discomfort note | US-87, US-90 | backend + core + UI | L | 8 |
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
- ~~**Model picks from server-built candidates:** exercises come from a catalogue list filtered by place and equipment, never free names; outcomes from a validated option set.~~ **T-202 (ADR-93):** for the training block the model builds everything (split, exercises from the whole eligible catalogue by key, sets, ranges, target RPE, rests, supersets and drop sets under time conditions) and the routine checker validates it (hard H1-H7, one retry); outcomes still come from a validated option set; cardio and the minimal block stay server-built.
- **ADR-94 (T-202):** "Crear bloque" → "Desde cero" (`block_splits` call, 2-3 options) or "Desde una carpeta" (`folder_review` call, accepted changes, checker pass); saved blocks are normal folders (links by routine id); `remove_coach_block` without a new plan; `exercise_favorites` sorted first in the picker and the swap list and preferred by the model after the hard checks.
- **Coach rules enforced by validators (`07` §3-§4):** frequency (≥ 2 per muscle group when days allow; 1 day = full body), ~10 weekly sets per muscle group, intensity per intention, 1-3 RIR, rest 2-3 min on main multi-joint lifts and 1-2 min on isolation (written into `rest_seconds`), priority multi-joint lifts first, undulating rep ranges within the week only for "Ganar fuerza", no reduction of cardio or steps, full range of motion only as a technique note; calorie floor (ADR-25), rate of loss/gain, protein ≥ 1.6 g/kg with distribution guidance (~0.4 g/kg per meal over ≥ 4 meals, not a meal plan), fat ≥ 20 %, in-block change ≤ 10 % after two weeks outside the trend's interval.
- **Budget:** `coach_usage` per user and month; the function refuses any call that would pass 3 € per user (ADR-90; 2 € until 2026-11-02, changed in T-185) or 5 € in total (dollars counted as euros); the global total comes from an aggregate that exposes no row. Deterministic features never call the function. CI never calls the API (fake client); every real call (smoke test, evaluation) is approved by the user.
- **Cardio (ADR-85 proposed, 2026-11-01):** a new deterministic `cardioRules.ts` sets 150 weekly cardio minutes for every intention (user-editable, plain note below 150) and the daily steps target max(28-day average rounded down to 500, 7 000) (T-181, plan JSON, no new table; one edit RPC). The block adds one cardio slot as the last exercise of every lifting routine, splitting the weekly minutes evenly in 5-min units, at most 60 min per routine, the excess "por tu cuenta"; cardio is outside "Duración de la sesión", so the lifting is untouched; catalogue `cardio` exercises with a duration metric, filtered by the five new cardio equipment values (T-184: `cardio_bike`, `treadmill`, `elliptical`, `rower`, `cardio_other`; `machine` is lifting only) plus `bodyweight`, default ranking by modality (`07` §3); the swap is any-to-any: alternatives derived from category + equipment, same minutes, never from the T-179 pairs table; the model only picks among candidates (T-182). Planned sets gain one nullable planned duration through `save_routine` (`target_reps` nullable only for duration sets); the cardio set reuses the duration metric types, `SetRow`, `SessionSetRow` and `MetricTypeLabels` (minutes only, no rest timer); `coach_plan_weeks` adds logged cardio minutes and steps per week (T-183). No warm-up anywhere (Q27). Calories ignore the planned cardio (Q25).
- ~~**Suggestions never rewrite routines:** progression and deload are shown in the live session; "Actualizar automáticamente" (ADR-70) keeps copying what was performed.~~ **Replan (ADR-88):** inside an accepted, not-ended block the progression (computed on the server, T-192), the calorie changes in the safe band and the deloads apply on their own; every one is a `coach_changes` row with its `before` value, reason and "Deshacer" / "Cancelar descarga" (one table, one `CoachChangeRow`, built in T-192); outside a block the suggestion stays as before; ADR-70 unchanged.
- **Replan: the trainer journey (ADR-86, ADR-87, ADR-89).** `coach_intake` and a free-text block in `coach_summary` with user exclusions (T-185); experience = max(self-reported, history), `situation` (intake or 28 days without a session), known maintenance as the expenditure prior. `coach_plans.phase` + options; phase calories (deficit cap 500) and phase training (minimal, regain, cut volume) in `nutritionRules`/`blockRules` (T-187). One SQL helper for plan weeks that skip calendar weeks without sessions, used by adherence, linking, deload and the block end (T-189). `coach_block_reviews`, a deterministic `coach_block_review_inputs`, phase triggers and next-block volume rules in code, a `block_review` model call that picks only eligible options, then the existing plan and block calls (T-190). One shared grounding validator (feature list + summary) for plan, block, review, report and chat reasons (T-190, reused by T-166/T-169). `coach_return_steps` and `coach_health_notes` (T-194). New kinds `intake_extract` and `block_review` (Sonnet 5.5), both budget-checked. *(2026-11-02: `intake_extract`, `situation` and the regain phase removed by T-198/T-200.)*
- **ADR-91 — Minimal intake and no "Recuperar" (user, Checkpoint E13-D feedback, 2026-11-02; amends ADR-86 and ADR-87; the PM holds it in `00-decisions.md`):** "Para conocerte" asks only what changes a figure or reaches the model in the same release (R-53): "Tiempo entrenando" (experience = max with the history), "Calorías de mantenimiento" (expenditure prior until the measured value takes over, with the handover note) and the free text. No model extracts fields from the text. The situation, injured area, pre-break bests and cutting calories are dropped from the UI and from `coach_user_intake` (new migration). The regain phase "Recuperar" (returning after a break) is removed entirely and deferred to Phase 3: no returning → regain rule, no regain calories, volume, cardio or exit triggers; stored regain plans become "Mantenimiento" with their calories unchanged (T-200). The free text reaches both `plan_nutrition` and `plan_block` as context, never instructions (ADR-87). Training around an injury comes from logged level 4-5 discomforts only (E13 Q52 for the fitness-scientist). Each intake row opens its own editor and nothing opens on its own (R-55).
- **One exercise-name resolver (R-54, T-199):** every screen gets exercise names from one shared resolver over the catalogue and the user's custom exercises; no generic "Ejercicio" fallback; `check-ux-conventions.sh` enforces it.
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
  - **Checkpoint E13-D 👤 "Getting to know you, your phase, and your plan with cardio" (7 items; carries E13-B3 and the E13-B1 re-test)** — presented 2026-11-02; **open** after the user's feedback
- [ ] T-198: A minimal intake, one row one editor, the free text in the plan and the block, the save fix — L
- [x] T-200: Remove the "Recuperar" phase (user, 2026-11-02; Phase 3) — M
- [ ] T-199: One exercise-name resolver and its guard — M
- [x] T-201: Barra Z, one "Máquina de cardio", barbell/EZ/dumbbell alternatives — L (merged #452)
- [x] T-202: 👤 Decision checkpoint: how the coach builds blocks (decided 2026-11-03, E13 Q53)
- [ ] 👤 The user's OK on the T-202 replan (ADR-93, ADR-94, US-93…US-96, T-203…T-208, one Checkpoint E13-H, the E14 split)
  - **Checkpoint E13-D 👤 re-presented** (rewritten 2026-11-03, 6 items: intake rows, "Para conocerte" and what the coach reads, exercise names, phase and options, calories and activity, equipment and alternatives with Perfil/Peso; the block items moved to E13-H)

### Phase 2c': How the coach builds blocks (US-72 am., US-93…US-96; T-202 replan 2026-11-03)
- [x] T-203: "Eliminar bloque" — M (E13 Q54 first)
- [x] T-204: Favourite exercises — M
- [ ] T-205: The model builds the block; one routine checker; `shoulder_abduction` (backend) — L
- [ ] T-206: Split options and the new block preview (UI) — L
- [x] T-207: Folder review and a block from a folder (backend) — L
- [x] T-208: Folder review screen, start choice, block from a folder (UI) — L
  - **Checkpoint E13-H 👤 "Blocks built for you, from scratch or from your own folders" (7 items)** — one checkpoint by the user's decision (2026-11-03; E13-I merged into it)

**Sizing (T-202 replan):** every task pays 6-8 hand-offs, so no S task. US-94 (T-203, about 10 files: one RPC, pgTAP, repository, ViewModel, one section and a dialog) and US-96 (T-204, about 13 files: one table, a sort helper shared by two lists, the detail star) are M vertical slices; merging them would pass 20 files across two domains. US-72 amended + US-93 "from scratch" is about 40 files with heavy backend (data migration for `shoulder_abduction`, the checker, two request kinds, a new model input, `save_coach_block`) and heavy UI (split options, a richer preview), so backend T-205 (developer slices data → checker → generator, as in T-201) → UI T-206. US-95 + US-93 "from a folder" share one review call and one screen (about 30 files), so backend T-207 → UI T-208; the start choice sheet ships in T-208 so T-206 never shows a one-option sheet. Collected test items (14) were first grouped into two checkpoints; the user merged them (2026-11-03), so they are condensed into one journey of 7: favourites 1, remove 1, from scratch with the preview 1, sets/RPE/rest and short sessions 1, save/train/edit 1, folder review with the copy 1, next block from your edits with a required change 1 → E13-H (7). The re-presented E13-D drops its block items (6).

### Phase 2c'': The deterministic block builder (ADR-99; US-72, US-93 rewritten, US-87 scenario, US-95 R-57; breakdown 2026-11-04, proposed)
- [ ] 👤 The user's OK on the breakdown (ADR-99, T-221…T-225, one Checkpoint E13-K). *(R-57 sweep scope resolved by the user 2026-11-04: option B, T-225; single checkpoint at the end, user 2026-11-04.)*
- [x] T-221: Block builder core: splits, per-slot prescription, time fit, score, "Desde una carpeta" check, saving; stories rewritten first — L (core + backend)
- [ ] T-222: Builder screens "Desde cero": split, type per day, the builder, "Guardar bloque"; retires the client model flow — L (UI)
- [ ] T-223: "Desde una carpeta" in the builder (refusal, fix on a copy, new folder) + R-57 `?` on the folder-review rows only; retires the client `purpose: block` — M (UI)
- [ ] T-224: Remove the model-built block from the server; final docs sweep — M (backend)
- [ ] T-225: R-57 sweep of the inline helper lines on the other screens (the ux-reviewer's list) + a `check-ux-conventions.sh` guard — M (UI)
  - **Checkpoint E13-K 👤 "Blocks you build yourself, from scratch or from your folder, help behind `?`, and the block rework regression" (9 items, the only checkpoint)** — proposed; the last E13 checkpoint and final regression; closes E13-H (replaces its items 3, 4, 5, 7); releases the closure T-170 (T-166…T-169 moved to E14)

**Architecture decisions (builder):**
- The builder rules (splits, prescription, time fit, score, eligibility, the folder check and its fix operations) live in the shared Kotlin core, pure and offline, like the folder review (ADR-97); the folder check reuses `FolderReviewCalculator`, so "Desde una carpeta" and "Revisar tus rutinas" can never disagree.
- The server keeps saving through `save_coach_block` / `save_routine` (plan weeks, links, cardio, lifecycle unchanged, ADR-94). The T-221 planner decides whether the (g) gate is re-checked in SQL (`coach_assert_block` reduced to equipment, no repeat, one routine per day) and where the cardio default comes from, with one implementation only (no second copy of `cardioRules.ts`).
- Old code goes in the task that replaces it (R-39): client parts in T-222/T-223, server parts in T-224 once no client calls them, so `main` never has a screen calling a removed kind.
- Stories are rewritten before the code (T-221 step 1) so T-222/T-223 inherit their Gherkin; `05` gets the builder spec from ux-reviewer in T-222 (today it only points to `07`).

**Sizing and checkpoint grouping:** one task per story slice; US-72 + US-93 is about 45 files with core rules, a save path, two screen modes and the removals, so core → "Desde cero" UI → "Desde una carpeta" UI → server removal. T-223 is M (it reuses T-222's screen; the R-57 row fix is folded in, same screen family). T-224 is M (deletions and one drop migration; merged into T-223 it would mix a heavy backend removal with UI). T-225 is M (the sweep of the ux-reviewer's list plus a guard; other domains than the block, so its own task, user option B). Collected test items (13), one checkpoint by the user's decision, condensed by journey: split and type per day 1, section values and time fit 1, picking and the score 1, save, train and edit 1, folder at 100 % and days refused 1, fix on a copy 1, strength fixes 1, review rows and sweep `?` 1, no-AI regression and data intact 1 → E13-K (9). Internal milestones (not 👤): `deploy-supabase` green after T-221 (if it has a migration) and T-224, data intact.

> **Phases 2d and 2e and the life-change part of Phase 3 moved to the E14 backlog (user, 2026-11-03).** Kept below for the E14 re-plan; not E13 work, their checkpoints are not gates. E13 continues after E13-H with T-166, T-167, T-168, T-169, Checkpoint E13-C (5 items) and T-170.

### Phase 2d → E14 backlog: The end-of-block review (US-73 am., US-83, US-84, US-88)
- [ ] T-189: Plan weeks follow the training — M
- [ ] T-190: End-of-block review and the next block (backend) — L
- [ ] T-191: End-of-block review screen and the next block — L
  - **Checkpoint E13-E 👤 "Your block review: what happened, the next block and a new phase" (6 items)** — proposed

### Phase 2e → E14 backlog: The coach adjusts on its own; injury (US-85, US-86, US-87, US-90; US-69/US-70/US-75 am.)
- [ ] T-192: Progression applies on its own, with "Deshacer" — L
- [ ] T-193: Calories and deloads on their own; scheduled deloads; data cleaning — L
- [ ] T-194: Injury: train around, graded return, persistent-discomfort note — L
  - **Checkpoint E13-F 👤 "The coach adjusts on its own, and trains around an injury" (6 items)** — proposed

### Phase 3: Life changes, report, history, evaluation, chat and closure (US-91, US-92, US-71, US-74, US-77; amended by the replan)
- [ ] ~~T-196: Life changes mid-block (backend) — L~~ → E14 backlog (pending E13 Q55)
- [ ] ~~T-197: "Revisar el bloque" and "Solo unos días" (UI) — L~~ → E14 backlog
- [ ] ~~T-166: Weekly coach report — L~~ → E14 backlog (user, 2026-11-04; with US-71, the US-76 report fallback, the US-90 note, Q49, the grounding validator and `has_week_data`)
- [ ] ~~T-167: Recommendation history, outcomes and deletion — M~~ → E14 backlog (user, 2026-11-04)
- [ ] ~~T-168: Coach evaluation — M~~ → E14 backlog (user, 2026-11-04; re-plan note: scope)
- [ ] ~~T-169: Coach chat with tools — L~~ → E14 backlog (user, 2026-11-04; re-plan note: grounding validator)
  - ~~Checkpoint E13-C~~ → E14 backlog, not a gate (re-plan note: the "3 €" cost wording; the final E13 regression moved to E13-K)
- [ ] T-170: E13 closure — PM closure step, released by Checkpoint E13-K

**Checkpoint grouping (replan, amended 2026-11-02):** collected items: intake 2, phases 2, carried plan and cardio re-test 2 (merged), Perfil and Peso fixes 1 → E13-D (7) *(after the E13-D feedback: intake rows 1, "Para conocerte" and what the coach reads 1, exercise names 1, phase and options 1 (the two phase items merged), carried plan and cardio 2, Perfil and Peso 1 → still 7)*; review 2, priorities 1, phase change 1, next block 1, paused week 1 → E13-E (6); loads 1, calories 1, deloads 1, injury 2, unresolved and no generic warnings 1 → E13-F (6); life changes 4 (grouped from 7), report 1, history and delete 1, cost 1, chat 1, final regression 1 → E13-C (9). E13-G was removed by the user (2026-11-02): its items are E13-C 1-4. Items whose trigger needs weeks of data say "if your data shows it" and are covered by host tests.

Internal milestones (not 👤 checkpoints): per task lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest`, pgTAP and `deno test` green; after each migration or function change, `deploy-supabase` green on `main` with data intact before the next task or checkpoint; T-160 smoke call approved by the user.

## T-201 technical plan: Barra Z, one "Máquina de cardio", barbell/EZ/dumbbell alternatives (ADR-92; planner, 2026-11-02)

Rules: `07` §3 "Equipment items" and "Barbell, EZ bar and dumbbell alternatives"; ADR-92. Not repeated here. Sized L, three slices, each its own PR-able developer run (backend -> core -> UI). No S slice: the percentage fix rides with the UI slice, which opens the same screens as the equipment list.

### Facts found in the code
- `public.equipment` enum (Postgres) already holds `barbell, ..., band_long, cardio_bike, treadmill, elliptical, rower, cardio_other`; values cannot be dropped. `ez_bar` and `cardio_machine` are new values and need their own migration (an enum value is unusable in the transaction that adds it; pattern of `20261216100000_cardio_equipment_enum.sql`).
- Saved equipment: `training_preferences.equipment public.equipment[]` (`20261201100000`). Stored blocks/plans: `coach_plans.training->'equipment'` (jsonb array of text; read by `save_coach_block`/`coach_cardio_options`, `20261217100200`); the developer greps every other jsonb or table holding an equipment list (plan input snapshots) and migrates it too.
- SQL functions listing the five cardio values literally, to be redefined: `coach_plan_inputs` (latest `20261221100000_coach_intake_minimal.sql`, lines 337-338); `coach_cardio_options` and `save_coach_block` (`20261217100200`) compare tags by text and need no literal change but are re-checked by pgTAP. Older definitions (`20261216100200`, `20261219100400`) are superseded: never edited.
- `exercise_alternatives` (`20261213100200`): check `similarity > 0 and <= 1`; symmetric lookup function returns both directions of one row; seed in `20261213100300`.
- Kotlin: `Equipment` enum in `shared/.../domain/model/Exercise.kt` (lines ~76-80 treadmill/elliptical; plus CardioBike, Rower, CardioOther); labels in `composeApp/.../feature/exercises/ExerciseLabels.kt`; strings `equipment_*` in `strings.xml`; `exercise_similar_percent` = `%1$s&#160;%%` (line 555) used by `ExerciseSimilarTab.kt:134`. Cause of "(100%%)": Compose resources do not collapse `%%`, so the literal shows twice. Fix: a single `%` (`%1$s&#160;%`).
- Edge Function: `supabase/functions/coach/candidates.ts` (equipment order list near lines 162-163, cardio lists) and its tests, `blockFixtures.ts`, `cardioBlock.test.ts`.
- Catalogue source: `scripts/generate-exercise-catalog.py` (importer mapping lines ~85-86 for cardio, 225-227 `CARDIO_EQUIPMENT`, the collapse of `ez_bar/trap_bar/landmine` into `barbell` and of pull-up bar/dip station/rings/TRX into `bodyweight`).

### Slice 1 - Backend (`database-developer`/backend developer; migrations, script, Edge Function, pgTAP, deno)
Files:
1. `scripts/generate-exercise-catalog.py`: map `ez_bar` -> `ez_bar`; `landmine`, `trap_bar`/hex bar, `pull_up_bar`, `dip_station`, `rings`, `suspension_trainer`, stability ball -> `other`; plus per-key overrides (`human-flag`, `back-extension`, `band-assisted-pull-ups`, `straight-bar-dips`, captain's chair, `toes-to-bar`, `muscle-ups` -> `other`; the two lat-pulldown variants -> `cable`; the two rows -> muscle group `back`); the five cardio values -> `cardio_machine` (drop the five-value set). Before retagging, check `skull-crusher`, `preacher-curl`, `strict-curl` against the bundle's `equipment` (`free.es.json`) as `07` says; unify "Barra EZ" -> "Barra Z" names (`ez-bar-pullover`, `ez-bar-reverse-grip-row`). Regenerate; commit the generated SQL/CSV outputs the script owns. A script self-check asserts counts: 20 EZ-bar keys, the 8 cardio keys, no `ez-bar` key outside `ez_bar`.
2. Migration A `2026122[3]100000_equipment_ez_bar_cardio_machine_enum.sql`: `alter type public.equipment add value if not exists 'ez_bar'` and `'cardio_machine'` (alone in the file).
3. Migration B `..100100_equipment_catalogue_retag.sql`: retag catalogue rows (`not is_custom` only; custom exercises keep their tag) with the key lists from `07` §3; muscle-group fixes; name unification (`name` columns or translation table the catalogue uses; follow `20261019130100`/`20261024100000`); DO-block asserts (EZ rows = 20, cardio rows = 8, no `ez-bar-*` left as `barbell`, no cardio row outside `cardio_machine`), idempotent.
4. Migration C `..100200_equipment_saved_lists_migrate.sql`: (a) `training_preferences.equipment`: replace any of the five cardio values by one `cardio_machine` (deduplicated, order kept); add `ez_bar` only where `place = 'gym'` or the list holds every old lifting value (the same set the app's "everything" means: barbell, dumbbell, machine, cable, kettlebell, bodyweight, band, band_long per the exact set in `07` §3 - confirm against that line), and never to an empty list; nobody gains `other`. (b) every stored jsonb equipment list (`coach_plans.training->'equipment'` and any other found by grep): same rule via `jsonb` rebuild, leaving other keys untouched. Adds a check on `training_preferences` is NOT added (old values must still parse); the unused five values stay in the type; a table check forbids the five in new writes (`equipment && array[...]` false) for `training_preferences` only if every earlier migration data passes after (a).
5. Migration D `..100300_coach_functions_cardio_machine.sql`: `create or replace` `coach_plan_inputs` (copy the `20261221100000` body, replace the two five-value `in (...)` lists by `'cardio_machine'`); re-check `coach_cardio_options`/`save_coach_block` need nothing (text compare); if a literal exists, redefine them too. Keep grants.
6. Migration E `..100400_exercise_alternatives_barbell_ez_dumbbell.sql`: insert the 71 pairs of `07` §3 groups by key (one row per pair, `exercise_id < alternative_id`-style ordering as the existing seed does; `on conflict do update set similarity = 1.00`), skipping a pair if a key is missing in a DO-block that raises (all 71 must exist: assert count of inserted-or-present pairs = 71). Delete or raise rows under 0.90 in `exercise_alternatives`; then replace the check by `similarity >= 0.90 and <= 1` (drop/add constraint; data first). Name the cross-existing pairs so symmetry is not duplicated.
7. `supabase/functions/coach/candidates.ts`: equipment order barbell, ez_bar, dumbbell, machine, cable, kettlebell, bodyweight, band, band_long, cardio_machine, other (`07` line 97); remove the five cardio values from every list; `blockFixtures.ts`, `candidates.test.ts`, `cardioBlock.test.ts` follow.
Docs same slice: `03-data-model.md` (equipment enum, alternatives check), `04-business-rules.md` (§9.7b alternatives, candidate order).
Tests:
- pgTAP `supabase/tests/equipment_ez_bar_cardio_machine.test.sql` (new; extend `exercises.test.sql`, `cardio_equipment.test.sql`, `coach_block_cardio.test.sql` where they assert the five values): catalogue facts (20 EZ keys; `landmine-press`, `t-bar-row`, `trx-*`, `ring-*`, pull-ups = `other`; `bodyweight` holds none of them; the 8 cardio keys = `cardio_machine`; lat-pulldown variants `cable`; the two rows `back`); migration outcomes on fixtures built by re-running the migration's UPDATE logic is not possible in pgTAP, so seed a user with each case (gym list, full old lifting list, empty gym list, home list, five cardio values with duplicates) and call the migration body as a reusable `public.migrate_equipment_list(public.equipment[], text)` helper function that Migration C defines and tests (immutable, no grants to anon; decision recorded in the PR); stored plan jsonb case; alternatives: all 71 pairs exist both ways via the lookup function, `barbell-row` <-> `bent-over-db-row` at 1.00, nothing under 0.90 (check rejects 0.85), flat/incline/decline not mixed; `coach_cardio_options(array['cardio_machine'])` returns the 8; `save_coach_block` accepts a cardio exercise with `cardio_machine` equipment and refuses a lifting slot outside the list (existing invariant); cross-user isolation: user B cannot read or change A's `training_preferences`, no new table so no new RLS.
- deno: `candidates.test.ts` (order, `cardio_machine` offers all 8 cardio, `ez_bar` only with Barra Z; Press Landmine only with `other`), `cardioBlock.test.ts` fixtures.
- Python: a small test or script self-check for the generator mapping (if tests exist in `scripts/`, extend; else the generator's asserts).
Exit: pgTAP and deno green, lint, `deploy-supabase` green on `main` after merge with data intact (user's saved equipment still has its values; the intake and plan survive). The Kotlin `Equipment` enum still lacks the new values until slice 2, so the app decoding `ez_bar`/`cardio_machine` must not crash between merges: slice 2 merges immediately after, and slice 1 is only deployed together (open question 1).

### Slice 2 - Core (`kotlin-core-developer`: shared domain/data, ViewModels, strings; no screens)
Files: `shared/.../domain/model/Exercise.kt` (`Equipment`: add `EzBar` `@SerialName("ez_bar")`, `CardioMachine` `@SerialName("cardio_machine")`; keep the five old values as unused entries only if the DTO must still decode legacy rows - they are migrated, so remove them from the enum and make a tolerant decode (`coerceInputValues`/unknown skip) in the preferences and exercise DTOs; verify the existing JSON config; decision in PR); a pure helper `EquipmentGroups` (or existing selection helper) exposing the ordered selectable list (barbell, ez_bar, dumbbell, machine, cable, kettlebell, bodyweight, band, band_long, cardio_machine, other) and the place defaults (cardio defaults unchanged: home/gym chips as before, with `cardio_machine` where the five were); `PreferencesMyTrainingViewModel` and the "Antes de empezar" equipment state (find with Grep `Equipment.` in `feature/profile`, `feature/trainer`); `ExerciseLabels.kt` (Equipment label mapping) and `MetricTypeLabels.kt` (only the cardio references); `strings.xml` (`equipment_ez_bar` = "Barra Z", `equipment_cardio_machine` = "Máquina de cardio"; remove `equipment_cardio_bike|treadmill|elliptical|rower|cardio_other`; remove, not rename, the old strings and their uses); ExerciseSimilar logic stays.
Tests (commonTest unit): `TrainingPreferencesDtoTest` (round trip of `ez_bar`, `cardio_machine`; a legacy value decodes without crash); `PreferencesMyTrainingViewModelTest` (toggle Barra Z on/off, toggle the cardio machine, place default, saved list persists; restoration: ViewModel recreated from the same `SavedStateHandle` keeps the draft, ADR-40); `BuildExerciseProgressTest` fixtures updated where equipment enum values are used; label coverage test: every `Equipment` has a label (existing glossary/labels test pattern). Alternatives ordering and the 0.90 floor are server-side; a unit test on the use case that merges similar exercises asserts it keeps sort by similarity desc and drops nothing it is given.
Exit: lint + `testLocalDebugUnitTest` green; the project builds.

### Slice 3 - UI (`compose-ui-developer`: composeApp screens and host tests)
- "Mi entrenamiento" (Preferencias, `MyTrainingSection`) and "Antes de empezar" equipment row: the chip group shows "Barra Z" in its position and one "Máquina de cardio" in the "Cardio" group instead of five; reuse the existing chip components and placement (design-system: `05` §8 equipment/chips rules; no new component); haptics, messages and help texts unchanged except any text naming the five machines (grep Spanish strings "cinta", "elíptica", "remo", "bicicleta" in `strings.xml` and the `?` help of the glossary: `GlossaryStringsTest`).
- Percentage fix: `exercise_similar_percent` -> `%1$s&#160;%` (one sign); no other string with `%%` unless grep (`%%` in `strings.xml`) shows a real doubled sign (each hit judged: `%%` in a Compose resource prints two). Add a `check-ux-conventions.sh` guard (the finding is a defect type likely to repeat): fail when `strings.xml` holds `%%` (fixture in the script's test). Accessible reading `exercise_similar_percent_cd` and the sort stay.
- Docs: `05-design-system.md` glossary/equipment wording if the equipment list is described; `04` §9.7b.
Host tests (androidHostTest, Robolectric), one per Gherkin scenario of the task:
- `MyTrainingSectionTest`: Barra Z chip appears, selects and deselects (tag `mytraining_equipment_ez_bar`-style, using the existing tag pattern `<screen>_<element>`; keep existing tags and add the two new ones `..._cardio_machine`); one "Máquina de cardio" chip, none of the five; selection survives a recreation (`StateRestorationTester`, ADR-40).
- "Antes de empezar" test (`BeforeStartScreenTest`): same chips, cardio machine saved and read back.
- `ExerciseSimilarTabTest` (new or in `ExerciseProgressScreenTest` neighbourhood, find the existing tab test): text "100 %" appears once (assert the visible text equals `100 %` with the no-break space and does not contain `%%`), content description "87 por ciento de similitud", sorted order most to least similar, "Remo con Mancuernas Inclinado" row lists "Remo con Barra Inclinado" at 100 % (fixture rows, the data is server-side).
- `PreferencesRestorationTest` update for the new chips; `GlossaryStringsTest` removes the five labels.
Exit: lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green.

### R-39 / sweep (Grep results to apply)
- Five cardio values: files found by Grep (`rowing_machine|treadmill|elliptical|stationary_bike|stair_climber|cardio_machine`, `Equipment.(CardioBike|Rower|CardioOther|Treadmill|Elliptical)`, `equipment_(cardio_bike|rower|cardio_other|treadmill|elliptical)`): `strings.xml`, `ExerciseLabels.kt`, `MetricTypeLabels.kt` (check, may be exercise keys only), `Exercise.kt`, `PreferencesRestorationTest`, `GlossaryStringsTest`, `MyTrainingSectionTest`, `PreferencesMyTrainingViewModelTest`, `TrainingPreferencesDtoTest`, `BeforeStartScreenTest`, `ExerciseProgressScreenTest`, `BuildExerciseProgressTest`; backend: `candidates.ts` + tests, `blockFixtures.ts`, `generate-exercise-catalog.py`, the pgTAP files above, `coach_plan_inputs` definition; docs `03`, `04`, `07`, `09-open-questions.md`, epic E13 and `planning/epics` mentions (only current-state descriptions; historical ADR-85 text is left as history, ADR-92 amends it). All migrated in this task; none deferred to E9.
- `%%` in `strings.xml` and format strings with literal `%`: Grep and fix every hit in slice 3.
- "Barra EZ" in strings, names and docs: unify to "Barra Z" (bundle in slice 1; app strings/docs in slice 3).
- Callers of the alternatives lookup (`exercise_alternatives` function, Edge Function swap, preview swap list, exercise details tab): none changes code; only data changes. Verified by the pgTAP lookup and the Edge Function swap test.

### UX notes (ux-reviewer, plan mode, 2026-11-02): PASSED with conditions
Pattern map: equipment chips in "Mi entrenamiento" (`MyTrainingSection.kt:168`, existing chip group, OK); the same row in "Antes de empezar" (reuses it, OK); the equipment filter chips in `ExercisePickerScreen.kt:158` (`Equipment.entries`, OK, gains the two chips automatically); the exercise create/edit equipment selector (`exercise_create_equipment_label`, OK); similar tab row `ExerciseSimilarTab.kt:134` (OK). No new pattern, no new component.
Rules: R-33/R-34 ✓ (same chip control and same label everywhere; one label per concept, R-35); R-37 ✓ ("100 %" with no-break space matches the `60 %`/`40 %` strings); R-40 ✓ (new `%%` guard); R-39 ✓ (sweep below); haptics/states unchanged ✓; 48 dp and contentDescription on the existing chips unchanged ✓.
Conditions: (1) The plan's tag `mytraining_equipment_ez_bar` is wrong: the real pattern is `preferences_equipment_<tagName>`, so the new tags are `preferences_equipment_ez_bar` and `preferences_equipment_cardio_machine` (`tagName()` of the new enum values must return exactly those); remove the five `preferences_equipment_rower|...` uses (`PreferencesRestorationTest:240` uses `rower`: change to `cardio_machine`). The picker chips become `exercise_picker_equipment_chip_ez_bar` and `..._cardio_machine`. (2) The picker and the exercise create selector also list every `Equipment` (so "Máquina de cardio" and "Barra Z" appear there): the UI slice adds them to the sweep and tests the picker's chip order (the enum order must equal the selectable order of `07` line 97, check `ExercisePickerScreen.kt:182` indexing by `Equipment.entries`). (3) The `%%` Grep found one hit (`exercise_similar_percent`, line 555); the guard must ignore nothing else. (4) The sweep line "Barra EZ": the Grep in `strings.xml` found none, only bundle names (slice 1).
Spanish copy (glossary 05 §10, add one row in slice 3): "Barra Z" (`equipment_ez_bar`; never "Barra EZ"), "Máquina de cardio" (`equipment_cardio_machine`; one chip, no hint text, like "Banda larga"), similarity "100 %" (`%1$s&#160;%`), cd "100 por ciento de similitud" unchanged. Remove `equipment_cardio_bike|treadmill|elliptical|rower|cardio_other`.
Sweep (Grep): `strings.xml` 555 and 596-600; `ExerciseLabels.kt` imports and the `when` (lines 9-17, 74+); `MyTrainingSection.kt`; `ExercisePickerScreen.kt:158-184`; exercise create selector; tests listed above. All migrated in this task, none deferred to E9.

### Save latency note (todo line 948)
While in the code of slice 3, the developer reads the "Guardar" free-text save path only if it touches the same ViewModel; no change planned (out of this task's listed scope; the todo line belongs to T-198).

## Dependencies
```
v1.3.0 ─► T-157 ─► T-158 ─► T-159 ─► [E13-A 👤]
       ─► [👤 key] ─► T-160 ─► T-161 ─► T-162 ─► T-163 ─► T-164 ─► T-165 ─► T-174
       ─► T-177 ─► [E13-B 👤 re-presented] ─► T-175 ─► T-176 ─► T-179 ─► T-180 ─► [E13-B1 👤] ─► [E13-B2 👤]
       ─► [user OK on the cardio breakdown] ─► T-181 ─► T-184 ─► T-182 ─► T-183 ─► [E13-B3 👤 = E13-B1 re-test + cardio]
       ─► [E13-B3 skipped] ─► [user OK on the replan] ─► T-185 ─► T-186 ─► T-187 ─► T-188 ─► [E13-D 👤 presented, open]
       ─► T-198 ─► T-200 ─► T-199 ─► T-201 ─► [T-202 👤 decided] ─► [user OK on the T-202 replan + E13 Q54] ─► [E13-D 👤 re-presented, 6 items]
       ─► T-203 ─► T-204 ─► T-205 ─► T-206 ─► T-207 ─► T-208 ─► [E13-H 👤 items 1-2, 6, 8 approved] ─► T-219 (decided, ADR-99)
       ─► [user OK on the builder breakdown] ─► T-221 ─► T-222 ─► T-223 ─► T-224 ─► T-225 ─► [E13-K 👤, final E13 regression, closes E13-H]
       ─► T-170 ─► v1.4.0
       E14 backlog (re-planned after E13-H): T-166…T-169 and E13-C (since 2026-11-04), T-189, T-190, T-191, T-192, T-193, T-194, T-196, T-197 (former E13-E, E13-F)
       (superseded sequence below kept for reference)
       ─► T-196 ─► T-197 ─► T-166 ─► T-167 ─► T-168 (👤 cases, paid runs) ─► T-169 ─► [E13-C 👤] ─► T-170 (PM closure) ─► v1.4.0
```
- Replan dependencies: T-187 needs T-185's `situation`; T-190 needs T-189's plan weeks (block end) and T-187's phases; T-193 needs T-192's `coach_changes` and `CoachChangeRow` and T-189's weeks; T-194 needs ~~T-185's injured area,~~ T-192's changes and T-190's summary in the block prompt (no injured area since 2026-11-02; E13 Q52 before it); T-200 needs T-198 (no `situation` left, so nothing selects regain); T-190 needs T-200 and the fitness-scientist's `07` clean-up without regain; T-199 runs after T-198 and T-200 so it never edits the files they delete; T-166 needs T-192/T-193 (changes listed), T-194 (note), T-190 (grounding validator) and T-196 (`has_week_data`); T-196 needs T-190 (early review) and T-192 (`coach_changes`).
- T-202 replan dependencies: T-203 needs E13 Q54; T-205 reads T-204's favourites and owns the "unresolved level 4-5" helper reused by T-194; T-206 needs T-205 deployed; T-207 reuses T-205's checker and input builder; T-208 needs T-207 deployed; T-190 builds the next block with the T-205 generator from the block folder; T-196/T-197 wait for E13 Q55; the fitness-scientist's `07` clean-up (stale intake, injury-area and regain lines; §12.8 rows 2-3) before T-205's planner.
- Builder dependencies (2026-11-04): T-221 needs ADR-99 approved; T-222 needs T-221 merged (deployed if it has a migration); T-223 needs T-222 merged and reuses its screen (no checkpoint in between); T-224 needs T-223 merged (no client calls `block_splits`, `plan_block` or `purpose: block`) and its `deploy-supabase` green before E13-K; T-225 needs T-224 merged; E13-K after T-225; T-170 waits for E13-K (T-166…T-169 moved to E14, 2026-11-04).
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
| (E13-D feedback) Dropping intake columns breaks the merged client or loses the user's intake | High | T-198 ships migration and client in one PR; the migration keeps years, maintenance and text; `deploy-supabase` checks the user's row; the DTO tolerates an old row |
| (E13-D feedback) Removing the regain phase breaks a stored plan in "Recuperar" | Medium | T-200 backfills it to "Mantenimiento" without touching its calories, macros or block; pgTAP fixture; the DTO maps an unknown phase to maintenance during rollout |
| (E13-D feedback) The save bug has another cause than the removed fields and survives the simplification | Medium | T-198 reproduces it first against the RPC with the exact client payload and pins it with pgTAP + a Kotlin serialization test |
| (E13-D feedback) A fourth generic exercise label appears in a new screen | Medium | R-54 + the `check-ux-conventions.sh` guard (T-199) fail CI on a generic label or a name fallback |
| (E13-D feedback) Questions asked "for a later task" come back | Medium | R-53: the plan of any task that adds an input names its reader in the same release; ux-reviewer rejects it otherwise |
| (T-202) The richer block input (folders, 8-week per-exercise history, whole catalogue) passes the token budget or the 150 s limit | High | T-205 measures the largest fixture in a test; compact encodings (keys, not names); the catalogue grouped by muscle and pattern within the equipment; the planner may move the history to its own SQL read or cut the catalogue to the user's equipment only (already the rule) |
| (T-202) The model fails the checker often (retries cost money, errors annoy) | Medium | The failures are fed back in the one retry; the checker's codes are in the prompt as rules; evaluation cases (T-168); the time estimate constant is shared by prompt and checker |
| (T-202) A free model makes blocks the user dislikes in new ways | Medium | Favourites, his own folders as a starting point, split options he picks, reasons per split and exercise, preview swaps, "Eliminar bloque" |
| (T-202) Removing a block loses plan weeks the history (T-167) wanted | Low | E13 Q54 decides; the recommendation keeps the folder and workouts; history of removed blocks is out of scope (keep it simple) |
| (T-202) Folder review proposes a change that breaks another rule | Medium | The server applies each change in memory and re-runs the checker; changes adding a hard failure are dropped (pgTAP/deno cases) |
| (Builder) Core, server save and folder review disagree on a count | High | One implementation per rule in the shared core (the folder check reuses `FolderReviewCalculator`); the 10,0 invariant test (builder block with 2+ days and no cut scores 10,0); the server re-checks only the (g) gate |
| (Builder) Cardio default computed twice (device and `cardioRules.ts`) | Medium | T-221 planner picks one source (a server read or a moved rule), never a copy; regression of the cardio tests |
| (Builder) The builder screen is too dense (sections, slots, picker, score, time line, extras, cardio) | Medium | ux-reviewer spec and diff passes in T-222; with one checkpoint at the end (user, 2026-11-04), builder feedback arrives after T-223 reuses the screen, so fixes may touch both modes |
| (Builder) Removing the model block breaks the saved block, plan weeks or the swaps | High | Removal last (T-224), after a Grep audit; regression suites kept; drop migration with pgTAP; `deploy-supabase` with data intact before E13-K |
| (Builder) "Desde una carpeta" writes the user's folder | High | The fix model works on a copy only; byte-identical source check in tests (T-221, T-223) |
| (Replan) Cost of the new model calls | Low | Intake extraction (rare) and one review call per block; ≈ 0,1 € per user and month extra (heavy user ≈ 3,1 €); the per-user cap is raised to 3 € (ADR-90, T-185) and still stops the chat first; the 5 € total binds first with two heavy users |

## Open Questions

**Block builder breakdown (2026-11-04), waiting for the user's OK with its PR:** ADR-99, T-221…T-225, one Checkpoint E13-K (9 items), and E13-H closed by E13-K (its postponed items 3-5 and 7 rewritten, not re-presented as written).
- **Resolved by the user 2026-11-04 · Scope of the R-57 sweep:** option B: its own M task T-225 after T-224, scope = the ux-reviewer's list of inline helper lines (`my_training_*_helper`, `preferences_*_helper`, `diary_*_hint`, `meals_*_hint`, …); T-223 stays M and fixes only the folder-review rows; E13-K gains the sweep item. (Rejected: A, in T-223; C, E9 table rows.)
**Resolved by the user 2026-11-04 · E13 ends at E13-K:** T-166…T-169 and E13-C moved to the E14 backlog. Notes for the E14 re-plan (no decision now): the shared grounding validator the chat needs (was T-166); T-168's scope now that plans, blocks and the folder review are deterministic (ADR-97/98/99); E13-C's "cost" item still says "against the 3 € limit", removed by ADR-96.

- **Resolved by the user 2026-11-04 · Checkpoints:** one checkpoint at the end, after T-225 (E13-J merged into E13-K; 9 items, above the usual 7 by that decision).
- Not for the user (decided inside the tasks): where the (g) gate and the cardio default live (T-221 planner); whether `unresolved_severe_discomforts` stays for the E14 injury task (T-224 planner, recorded in the PR); routine names, the save folder name, the builder layout and the strings (ux-reviewer in T-222/T-223, within the glossary).

**T-202 replan (2026-11-03), waiting for the user's OK with its PR:** ADR-93, ADR-94, US-93…US-96 and the US-72 amendments, T-203…T-208, one Checkpoint E13-H, the re-presented E13-D (6 items), the E14 split, E13-C with 5 items.
- **E13 Q54:** resolved by the user 2026-11-03 (the recommendation: plan, phase, cardio and steps stay; plan weeks and links deleted; folder and routines kept; no replace-in-one-step).
- **E13 Q55:** moved to E14 ("Adaptar el bloque" and T-196/T-197; the user leans to dropping it; dropping it needs `07` §12.8 and ADR-86 amended).
- **E13 Q56:** resolved by the user 2026-11-03: "Tengo poco tiempo" and the minimal phase "Mínimo" are removed (T-205 backend incl. a minimal → maintenance migration, T-206 UI and strings); E14 items that used the minimal phase are noted for the re-plan.
- **E13 Q57:** resolved by the user 2026-11-03 (no change request).
- **E13 Q58:** follow-ups without a block; facts recorded; the calorie part moves to E14.
- **For the fitness-scientist (E14 re-plan):** `07` §12.4 row "e1RM" still says "swap to a validated alternative of the same pattern" (ADR-93: the model chooses within the checker).
- **For ux-reviewer:** the review action label in the folder menu (must differ from "Revisar el bloque"), "Desde cero" / "Desde una carpeta", "Recomendada", "Aplicar en una copia" and the copy folder name, "Eliminar bloque" dialog copy, the favourite marker in both lists, the preview row format with RPE and rest.

**Replan (2026-11-01), waiting for the user's OK:** the replan as a whole (ADR-86…ADR-90, US-81…US-92, T-185…T-194, T-196, T-197) and the checkpoints E13-D, E13-E, E13-F and the last E13-C (E13-G removed by the user, 2026-11-02).

**Replan, open with a recommendation the tasks use (not blocking; `planning/09-open-questions.md`):**
- **E13 Q44** settled by `07` §3: A (server-computed, written to the block routines, "Deshacer"). T-192.
- **E13 Q45** settled by `07` §12.8 row 7: the swap is offered and applied on the user's tap. T-194.
- ~~**E13 Q46** when the intake is asked: recommended once before the first "Crear plan", every field optional, "Ahora no", editable from "Tus datos". T-186.~~ **Superseded by the user, 2026-11-02:** nothing opens on its own; each "Antes de empezar" / "Tus datos" row opens its own editor (R-55). T-198.
- **E13 Q50** ("Situación" values) and **E13 Q51** (regain exit): resolved by removal (user, 2026-11-02: "Recuperar" removed, deferred to Phase 3).
- **For the fitness-scientist (before T-190):** remove regain and the dropped intake inputs from `07` (§1 intake line, §2 "Situation" row, §4 regain calories, §12.1 handover line, §12.2 "returning lifter", §12.3 regain row and "situation returning" entries, §12.5 "regain proposed after 28 days", §12.7 "Recuperar" copy, §12.8 row 8).
- **E13 Q52** (new, for the fitness-scientist, before T-194): training around an injury without an injured area; proposed: exclusions from unresolved level 4-5 discomforts only; whether the model may use the free text to prefer exercises among validated candidates is the scientist's call (`07` §7).
- **ADR-91:** the minimal intake and the removal of "Recuperar" (Architecture Decisions above); held by the PM in `00-decisions.md`, amending ADR-86 (intake fields, extraction, regain) and ADR-87 (the block request reads the free text).
- **E13 Q47** a block ends without a review: recommended nothing starts on its own, a card stays, no automatic change past the block; "Revisar el bloque" also opens it early. T-190, T-191.
- **E13 Q48** what "Eliminar historial del entrenador" deletes: recommended plans, phases, reviews, recommendations, automatic changes, reports, conversations; keeps the intake, derived values, exclusions and AI usage. T-167.
- **E13 Q49** the report's "one change": recommended one user action from server-built candidates, never a plan change. T-166.
- Fitness-scientist pass on eight `07` points: done 2026-11-02, tasks aligned.
- **Needs ux-reviewer (not the user):** the T-198 labels ("Tiempo entrenando", "Calorías de mantenimiento", the "Para conocerte" `?` and the "Lo que el entrenador sabe de ti" intro) and the R-54 guard's allow-list (T-199); "Revisar el bloque" and its three choices, "Solo unos días" (T-197), the level 4-5 swap offer (T-194), the "Bloque nuevo" vs "Revisar el bloque" labels (T-191).

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
