# Tasks — Phase 2

Plan of the current epic: [tasks/plan.md](plan.md) (created by the epic-planner) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-222 (T-220…T-222: Checkpoint E13-H feedback 4, 2026-11-04); T-219 (T-219: block creation evaluation, user 2026-11-04); T-218 (T-218: nutrition plan without the model, ADR-98, user 2026-11-04); T-217 (T-217: nutrition plan evaluation, user 2026-11-04); T-216 (T-216: folder review reworked into short feedback, user 2026-11-04); T-215 (T-215: flat max_tokens, 100k for plan_block and folder_review, 10k for block_splits and plan_nutrition, per-call `coach: usage` log; #481); T-214 (T-214: the coach never stops because of money, ADR-96, user decision 2026-11-04); T-213 (T-213 folder review 502 debug with COACH_DEBUG, RDL muscle filing, "Borrar carpeta" in the folder menu and "Revisar tus rutinas": Checkpoint E13-H feedback 3, 2026-11-03); T-212 (T-212: no length cap on the coach's text and debug logging, user decision 2026-11-03); T-211 (T-210 folder review invalid answer and T-211 leg muscle groups: fixes from Checkpoint E13-H feedback, 2026-11-03); T-209 (T-209: fix from the Checkpoint E13-D re-presentation, swap alternatives outside the selected equipment, 2026-11-03); T-208 (T-203…T-208: E13 replan from the T-202 decision checkpoint, epic-planner 2026-11-03: remove a block T-203, favourites T-204, the model-built block and the routine checker T-205 (backend) / T-206 (UI), folder review and a block from a folder T-207 (backend) / T-208 (UI); ADR-93, ADR-94 proposed; one Checkpoint E13-H after T-208 (user, 2026-11-03: E13-H and E13-I merged); E13-D re-presented first with 6 items; the block follow-ups T-189…T-197 moved to the E14 backlog the same day); T-202 (T-202: 👤 decision checkpoint on how blocks are built, 2026-11-02; decided by the user 2026-11-03); T-201 (T-201: fix from the Checkpoint E13-D re-test, equipment split and alternatives, 2026-11-02); T-200 (T-198…T-200: Checkpoint E13-D feedback, epic-planner 2026-11-02: minimal intake (years, maintenance, free text), the free text in the plan and block requests and the "Para conocerte" save fix T-198; "Recuperar" removed T-200 (user, deferred to Phase 3); one exercise-name resolver and its guard T-199; order T-198 → T-200 → T-199, all before T-189, gating the re-presentation of Checkpoint E13-D); T-197 (T-185…T-197: E13 replan "the trainer journey", epic-planner 2026-11-01, aligned 2026-11-02 with the fitness-scientist's `07` pass: intake and free text T-185/T-186, phases T-187/T-188 (T-188 also absorbs the folded T-195 disclaimers and two T-173 items), plan weeks follow the training T-189, end-of-block review T-190/T-191, automatic progression T-192, automatic calories and deloads T-193, injury and health note T-194, life changes T-196/T-197; T-195 and T-173 folded and not reused (user 2026-11-02: no S tasks); ADR-86…ADR-90; E13 Q32-Q43 user decisions, Q44-Q49 open with recommendations (Q44, Q45 settled by `07`); checkpoints E13-D/E/F and E13-C (last) proposed); T-184 (T-181…T-184: cardio in the plan and the block, US-79 + US-80 (cardio equipment, T-184, runs between T-181 and T-182) + US-72/US-73 amended, ADR-85 proposed; epic-planner, 2026-11-01, reworked the same day after the fitness-scientist and ux-reviewer passes; E13 Q23-Q30 resolved; waiting for the user's OK on the breakdown and ADR-85); T-180 (fix from Checkpoint E13-B1: unrealistic calorie surplus, 2026-11-01); T-179 (long band + exercise alternatives, user request 2026-11-01; runs after T-176 without waiting for Checkpoints E13-B1/E13-B2); T-178 (model selector in "Uso de IA", test aid, user request 2026-11-01; runs after T-175 and before T-176); T-177 (fix from Checkpoint E13-B: plan card missing after "Crear plan"; runs before T-175); T-176 (US-78 "Antes de empezar": T-175 backend, T-176 UI; E13 Q14-Q22 resolved 2026-10-30); T-174 (fix from Checkpoint E13-B: "Crear plan" fails); T-173 (E13 UI refinements, runs after T-169 and before Checkpoint E13-C; T-170 is the E13 closure). Numbering continues from here.

**Next (updated after the E13-H feedback 4, 2026-11-04):** **T-220** (folder review content, fitness-scientist first) in a fresh session, then T-221, T-222, then re-present Checkpoint E13-H items 6 and 8; T-219 after the approval. Older pointer: (1) re-present **Checkpoint E13-H 👤, only items 6 and 8** (items 1-2 approved, 3/4/5/7 postponed; after T-216 and T-218, with the reported effects; the user first runs `npx supabase secrets unset COACH_DEBUG`); (2) **T-219** (planning only, no code, like T-216/T-217: evaluate AI block creation the same way as the folder review and the nutrition plan: which parts are deterministic, what input is needed, where the LLM helps; fitness-scientist first, then discuss with the user) in a fresh session, after the checkpoint feedback. Previous pointer: **T-214** (ADR-96 no money cap, 5 € notice; M) in a fresh session, then re-present Checkpoint E13-H. T-218 merged in #486 (CI green; `deploy-supabase` must be green on `main`): e1RM outcome reasons no longer name the exercise (no server-side Spanish names); N1-N4 rows only in the Trainer plan screen; follow-up for E14: N1-N4 rows in the Diario goal editor. Previous pointer: **T-218** (nutrition plan deterministic, ADR-98); then T-214 and re-present Checkpoint E13-H. Previous pointer: **T-217** (user request 2026-11-04, planning only, no code: evaluate the AI trainer for the nutrition plan the same way as the folder review: which parts are deterministic, what input is needed, where the LLM helps; fitness-scientist first, then discuss with the user) in a fresh session. T-216 merged in #483 (CI green, `deploy-supabase` green): folder review is a deterministic score and checklist on the device; reported effects to tell the user at the checkpoint: "Series por grupo" statistics shift (catalogue secondaries corrected). Then **T-214** (ADR-96 no money cap, 5 € notice; M) → then **re-present Checkpoint E13-H 👤** (still an open gate; the user must first run `npx supabase secrets unset COACH_DEBUG`). T-215 merged in #481 (CI green, `deploy-supabase` green): the first folder review that finished was too slow and too detailed, see T-216. Previous pointer: **T-214** (ADR-96: no money cap, 5 € notice; M) in a fresh session → then **re-present Checkpoint E13-H 👤** (still an open gate; feedback 3 fixes merged in #479 with T-213, `deploy-supabase` green; the user must first run `npx supabase secrets unset COACH_DEBUG` and retry the folder review once; if it fails again, stop and decide a simplification, see T-213 feedback). Previous pointer kept below. **T-213** (folder review 502 debug, starts with the 👤 `COACH_DEBUG` step, the RDL muscle filing and the folder UI; size L) → then **re-present Checkpoint E13-H 👤** (still an open gate; feedback 3 recorded 2026-11-03 under "Checkpoint E13-H feedback 3"). Previously: (T-211 and T-212 merged together in #476 with the folder-delete rule (ADR-95) and H1 counting only strength exercises, CI green, `deploy-supabase` green; install the new APK first; enable `COACH_DEBUG` only if the 502 returns; T-210 merged in #472 and reverted by the user in #474, 2026-11-03: the clipping fix was a guess, cause unconfirmed; T-212 replaces it; T-208 merged in #468, CI green, no backend change; T-207 merged in #466, CI green, `deploy-supabase` green; T-206 merged in #464, CI green; T-205 merged in #462, CI green, `deploy-supabase` green; T-204 merged in #460, CI green; T-203 merged in #458; Checkpoint E13-D approved by the user 2026-11-03, not re-presented) → [E13-H 👤] → T-166 → T-167 → T-168 → T-169 → [E13-C 👤] → T-170 (closure). The block follow-ups (T-189…T-194, T-196, T-197) moved to the **E14 backlog** (user, 2026-11-03), re-planned after E13-H. *(T-202 decided by the user 2026-11-03; T-201 merged in #452, CI green, `deploy-supabase` green on `main`.)* *(Before the feedback: Checkpoint E13-D 👤 presented after T-188 merged in #441, 2026-11-02.)* Context: (E13 replan approved by the user on 2026-11-02 and merged in #433: ADR-86…ADR-90, US-81…US-92, tasks T-185…T-194, T-196, T-197, Checkpoints E13-D/E/F and the last E13-C agreed; see "E13 replan: the trainer journey" below) (2026-11-03: E13-E, E13-F, T-189…T-194, T-196, T-197 moved to the E14 backlog.) The skipped Checkpoint E13-B3 items are carried into Checkpoint E13-D.

---

## Known flaky tests (the user does not accept flakiness)

Rule: every CI failure that passes on re-run is logged here with date, run URL and test. A second occurrence of the same test turns into a `fix(T-###)` task to remove the cause (or, only if the cause is outside our code, the test is marked and documented as flaky here).

| Test | Date | Run | Count | Status |
|---|---|---|---|---|
| `SupabaseTrainingPreferencesRepositoryTest.get_rowWithoutCalculatorColumnsUsesTheDefaults` (`AppException` at line 95, no cause in the log; passes locally and on re-run) | 2026-10-29 (PR #371) | https://github.com/lopezjavier/AppFitness/actions/runs/40959762823 | 1 | watch: next failure → fix task |


## 👤 Checkpoints

E10 (agreed with the user, 2026-10-28): one **Checkpoint E10** after T-136 (whole epic, also the final regression; its approval releases T-137). Details under the E10 section.

E11 (agreed with the user, 2026-10-28): a 👤 spike report after T-139 (device result, not a functionality checkpoint; gates T-141) and **one Checkpoint E11** after T-142 (watch data + daily automatic sync, also the final regression; its approval releases T-143). Details under the E11 section.

E13 (agreed with the user, 2026-10-29: three checkpoints): **Checkpoint E13-A** after T-159 (Trainer tab, derived values, progression in the live session; no paid call; 7 items), **E13-B** after T-165 (nutrition plan and outcomes, target weight gone, AI usage, block preview and "Guardar bloque", adherence, deload; 9 items), **E13-C** after T-169 (weekly report, history and deletion, chat, final E13 regression; 9 items; releases T-170; if T-168 keeps the chat disabled, presented after T-168 without the chat items). Non-checkpoint 👤 steps: Anthropic key before T-160; evaluation cases and paid-run approvals in T-168. Details under the E13 section. **User decision, 2026-10-30 (E13 Q22 B):** the fix T-177 runs first and E13-B is re-presented after it (items 1-9); then T-175 and T-176 (US-78), followed by **Checkpoint E13-B1** (plan, 7 items) and **Checkpoint E13-B2** (block, 6 items); T-166 waits for both. Details under T-176. **Cardio (epic-planner proposal, 2026-11-01, needs the user's OK):** after T-181, T-184, T-182 and T-183, the E13-B1 re-test is presented together with the cardio items as **Checkpoint E13-B3 "Re-test and cardio in your plan"** (7 items + leave and come back); details under T-183. **Replan (epic-planner, 2026-11-01; proposed, needs the user's OK):** E13-B3 was skipped by the user and its items move into **Checkpoint E13-D** after T-188 ("Para conocerte", your phase, your plan with cardio, Perfil and Peso fixes; 7 items); then **E13-E** after T-191 (end-of-block review, next block, phase change; 6 items); **E13-F** after T-194 (automatic changes, deloads, injury; 6 items); **E13-C** after T-169, the last checkpoint (life changes, report, history, cost, chat, final regression; 9 items; releases the closure T-170). E13-G was removed by the user on 2026-11-02. Details under "E13 replan: the trainer journey". **Checkpoint E13-D feedback (user, 2026-11-02):** E13-D stays open; the fix tasks T-198 (minimal intake, save fix), T-200 ("Recuperar" removed, user decision) and T-199 (exercise names) run first and E13-D is re-presented after T-199 with its items rewritten (7 items); details under "Checkpoint E13-D feedback" right after T-188. **T-202 replan (epic-planner, 2026-11-03; proposed, needs the user's OK):** E13-D is re-presented right away with 6 items (its block item moves to E13-H); then one **Checkpoint E13-H** after T-208 ("blocks built for you, from scratch or from your own folders": favourites, remove a block, from scratch, the preview, train and edit, folder review, next block from your edits; 7 items; the user merged the proposed E13-H and E13-I on 2026-11-03); then T-166…T-169 and **E13-C** (5 items, the last). **E14 split (user, 2026-11-03):** E13-E and E13-F moved with the block follow-ups to the E14 backlog. Details under "E13 replan: how the coach builds blocks" right after T-202.

E12 (user decision, 2026-10-28: two checkpoints): **Checkpoint E12-A** after T-147 (rest alert with the screen off, choosing the sound, discomfort log; 7 items), **Checkpoint E12-B** after T-150 (chosen sound, notes, Mi entrenamiento, movement pattern, final regression; 7 items; its approval releases T-151). Details under the E12 section.

## Notes for later epics

- **E11:** the `READ_HEALTH_DATA_HISTORY` pre-check is replaced by the spike T-139 (user decision, 2026-10-28; see the E11 section).
- **E12:** consumed by the E12 breakdown: Android 17 verification → T-146 (plan step and Checkpoint E12-A item 1); sound pick → Checkpoint E12-A item 5, wired in T-148.
- **Phase 3 (user, 2026-11-02):** "Recuperar" / returning after a break (regain phase at maintenance, its exit triggers and the "Situación" intake question), removed from E13 by T-198/T-200; to be re-planned as a follow-up.
- **E13:** consumed by the E13 breakdown: source, data-policy and price verification done 2026-10-29 (`07` §3-§4, §10-§11); 👤 Anthropic account, spending limit and `ANTHROPIC_API_KEY` → the 👤 step before T-160.
- **E13 backlog (user request, 2026-10-30; for the epic-planner, not in T-177):** (1) the block offers alternatives per exercise (for example "Press banca con barra" or "Press banca con mancuernas" in place of "Press en suelo con barra") and the user picks one, instead of one fixed exercise per slot; (2) the block includes cardio, which the block model has no field for today (`blockValidators.ts` rejects `cardio`); the weight stays the user's own input during the session; (3) the block sets an RPE target per exercise, which can differ by plan (today "Objetivo RPE" reads "Sin objetivo" for every exercise; the block format has no RPE field); (4) the planner must not write exercise instructions into notes, since the app already shows them (the "Otro · …" text under each exercise, screenshot `design/reported/e13-b-block-notes-rpe.png`, delete once fixed). Items 3 and 4 came from the Checkpoint E13-B feedback, 2026-10-30. → Consumed: (1) T-179, (2) T-181…T-184, (3) and (4) **T-205/T-206** (target RPE per exercise; at most one short technique note, never instructions; T-202 replan, 2026-11-03).

---

# E14 · Block follow-up → `v1.x` (BACKLOG, not planned)

Epic: [`planning/epics/E14-block-follow-up.md`](../planning/epics/E14-block-follow-up.md). **Moved out of E13 by the user, 2026-11-03** ("we're complicating things; we don't have a proper block yet"). Nothing here is active or sequenced: the epic-planner re-plans E14 after the user approves the new block at Checkpoint E13-H, aligning the stories and tasks with ADR-93/ADR-94 then. Backlog (the detail of each task is kept, unaligned, inside the E13 section under "→ E14 backlog"):
- T-189: Plan weeks follow the training (breaks pause the block)
- T-190: End-of-block review and the next block (backend)
- T-191: End-of-block review screen and the next block
- T-192: Progression applies on its own inside a block, with "Deshacer"
- T-193: Calories and deloads on their own; scheduled deloads; data cleaning
- T-194: Training around an injury, graded return, persistent-discomfort note
- T-196, T-197: Life changes mid-block ("Revisar el bloque", "Adaptar el bloque", "Solo unos días"); pending E13 Q55
- The follow-up parts of T-166 (report lists automatic changes, the health note) and T-167 (history of automatic changes, reviews, return steps, notes; their deletion)
- Checkpoints E13-E and E13-F (to be re-proposed as E14 checkpoints)
- Open questions carried: E13 Q44, Q45, Q47, Q52, Q55, the calorie part of Q58
- Re-plan note: the minimal phase is removed (E13 Q56, T-205/T-206), so the 1-day proposal, "Solo unos días" with fewer days, the 65+ minimal rules and the minimal exceptions of deloads, H1 and cardio need a new answer (see the E14 epic file)

---

# E13 · Performance assistant → `v1.x` (proposed `v1.4.0`)

Stories: `planning/epics/E13-performance-assistant.md` (US-68…US-90 + the evaluation; US-81…US-90 and the trainer-journey amendments added by the replan of 2026-11-01, see "E13 replan: the trainer journey" right above T-166; ADR-86…ADR-89). Plan: [tasks/plan.md](plan.md). Decisions: ADR-76 (coach rules), ADR-77 (provider, cost cap; NFR-02), ADR-78 (target weight retired, `activity_level` starting factor), ADR-79 (Trainer tab), ADR-81 (discomfort level, no message), ADR-82 (note kind), ADR-25 (calorie floor), ADR-29 (cascade), ADR-40 (restoration), ADR-73 (no background job). Rules: `planning/07-coach-design.md` §2-§9 (every figure), §11 (prices, 150 s wall clock), `04` §1, §3, §4.1, §4.9, §4.12, §6.6, `05` §8-§10.
**User decision, 2026-10-30 (after T-160):** the residual budget gap is accepted: a call cut off by the timeout before the API reports its usage is not counted in `coach_usage`. In return, every model operation blocked by the budget (`coach_limit_reached`, `coach_no_credit`) keeps the user's input and offers "Reintentar" (prompt in the chat, Trainer operations such as plan, block, report), so no work is lost when the budget is exhausted. This applies from T-161 on; the limit message copy stays as in T-160.

Carried over from "Notes for later epics": the source/price/data-policy verification is done (2026-10-29); the 👤 Anthropic key is the step right before T-160 (the first task that calls the model).
**Status: agreed by the user, 2026-10-29** (task list as proposed; three checkpoints instead of five). Coach rules added to `07` §3-§4 on 2026-10-29 (rest, exercise order, undulating periodization for "Ganar fuerza", range of motion, cardio, protein distribution) are folded into T-159, T-161/T-162, T-163/T-164 and the T-168 criteria. **E13 Q1-Q13 resolved by the user, 2026-10-29, each with the recommended option** (`tasks/plan.md`, `planning/09-open-questions.md`); the amended Gherkin is in the epic file. No task is blocked. Key resolutions: Q1 B (no health message; the suggestion's reason states neutrally that the load is kept because of the open discomfort; levels 1-3 keep the reps rule and the 5 % drop); Q2 A (optional rep range per routine exercise, per session, in the routine editor; set by the block creator); Q3 (main lifts = `movement_pattern` squat, hinge, lunge, horizontal/vertical push or pull; deload only inside an accepted block; 40 % cut rounded to the nearest whole set, at least 1); Q4 B (T-168 is the formal gate for the chat); Q5 (glossary entries "Confirmar", "Aceptar"/"Descartar"; "Eliminar historial del entrenador").
Ordering: deterministic, model-free work first (US-68 derived values, US-70 progression), so Checkpoint E13-A gives value before any paid call. **US-73 (adherence) and US-75 (deload) move after US-72**, against the initial guidance, because both need plan weeks: US-73 links workouts to `coach_plan_sessions` and US-75 "changes the week" of a block, and plans only exist once US-72 saves one. Then the `coach` Edge Function with US-76, US-69 (nutrition part of the plan), US-72 (training block), US-73 + US-75, US-71, US-74, the evaluation, and US-77 last.
Sizing: 14 tasks. One task per story except: US-68 splits into backend (T-157: the SQL coach summary and derived-value math, heavy pgTAP) and UI (T-158: the Trainer tab and the card), together about 20 files; US-69 and US-72 each split into backend (Edge Function call, validators, RPCs) and UI (screens), each half about 12-15 files. US-73 and US-75 merge into one L task (T-165: both are deterministic and live on the plan weeks; each alone is M). US-76 rides with the Edge Function foundation (T-160). The evaluation is its own task (T-168, not a story). Closure T-170 stays S (it waits for Checkpoint E13-C).
**150 s wall clock (07 §11):** the block plan is generated in two calls: nutrition + outcomes (T-161) and the training block (T-163), each streamed to the app with visible progress; T-163's planner measures the block call and, if it risks 150 s, splits it further (for example one call per two weeks). The function never runs a call it cannot finish (timeout below 150 s, clean error with "Reintentar").
**Paid calls:** CI and `deno test` never call the Anthropic API (a fake client). Any real call during a task (smoke test, evaluation run) is approved by the user first; the cost lands in `coach_usage`.
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1); backend tasks run `06` §2.2. Every migration task needs `deploy-supabase` green on `main` before the next task or checkpoint; every Edge Function change is deployed by the same workflow.
**Shared UI built once (R-39):** `CoachProposalCard` ("Aceptar" / "Descartar", reason, data used) is built in T-162 (calorie change) and reused by T-165 (deload) and T-169 (chat proposals); `ProvisionalLabel`-style text + colour marker for provisional values (R-41) is built in T-158 and reused by T-162/T-164; the streamed-progress state ("Preparando tu plan…") is built in T-162 and reused by T-164/T-166/T-169.

---

## T-157: Coach summary and derived values in SQL (US-68 backend; US-69 trend and intake scenarios) · L

**Story:** US-68 (+ US-69 "Weight trend with weekly averages", "Incomplete diary") · **Flow:** backend · **Size:** L · **Scenarios:** Values are shown with their source (data); Starting value without history; Confirming (storage); Editing a value (storage); Re-asking when the data changes; Weight trend with weekly averages; Incomplete diary (estimate marked unreliable)

**Description:** (1) **Migration:** `coach_derived_values` (`user_id` cascade, `key` enum `coach_value_key`: `bmr`, `activity_level`, `tdee`, `weight_trend`, `experience`; `value` jsonb, `uncertainty` jsonb, `source` jsonb (data used and data missing), `confirmed` boolean, `confirmed_at`, unique `(user_id, key)`), owner RLS enabled and forced with `(select auth.uid())`, extend `delete_account_cascade.test.sql`. (2) **Deterministic math in SQL** (one source for the app and the `coach` function, `07` §1): BMR (Mifflin-St Jeor, `04` §1.2, same cases as the domain); activity level from `profiles.activity_level` as the starting value (ADR-78), with a proposed level from `daily_activity` steps and active kcal (E11) and the logged training volume (planner documents the mapping as a design decision in `07` §2 or raises it); TDEE = BMR × factor, then **adaptive expenditure** from logged intake and weight change, using only logged days and marking the estimate unreliable below 70 % logged days (`07` §2, §9); the 7 700 kcal/kg constant only without history (`07` §4); **weight trend** = slope of weekly averages of the daily values (`04` §3.1) with its confidence interval from the real spread (`07` §5); experience from training history (none = beginner). (3) **RPCs** (`security invoker`, `authenticated`): `coach_derived_estimates()` (current estimates with source and missing data; provisional when unconfirmed), `confirm_derived_values(p_values jsonb)` (confirm or edit; stores `confirmed_at`; whether an edited activity level also updates `profiles.activity_level`, and so Diario's TDEE, is E13 Q13, resolved 2026-10-29: no, it is stored only as the coach's confirmed value), re-ask rule: a confirmed value whose new estimate falls outside its stored uncertainty returns `confirmed = false` (`07` §2); `coach_summary(p_weeks int default 8)` (compact jsonb, last 8 weeks at most: derived values, weekly weight averages and trend, intake completeness, weekly sets per muscle group, e1RM per exercise, sleep 7-day average, open discomfort levels, intention and preferences, note kinds; never raw rows; consumed by T-160+). (4) **Docs:** `03` (table, RPCs), `04` new §7 "Coach" (derived values, trend, intake completeness), `07` §2 if the activity mapping is a new design decision.

**Acceptance criteria:**
- [x] With steps, active kcal and sessions, `coach_derived_estimates` returns the activity level, its source data and the missing data.
- [x] With no Health Connect data and no training history, the activity level is the profile's and it is provisional (unconfirmed).
- [x] `confirm_derived_values` stores the confirmation and an edited value; the edited value is returned afterwards.
- [x] A confirmed value whose new estimate is outside its uncertainty comes back unconfirmed; one inside stays confirmed.
- [x] Daily weigh-ins over 4+ weeks: the trend is the slope of the weekly averages with its interval; fewer weeks widen the interval, never block.
- [x] Diary logged on 60 % of the days: the expenditure estimate is flagged unreliable (and T-161 will not propose calorie changes from it).
- [x] `coach_summary` covers at most 8 weeks and contains no raw table rows; size is bounded (planner sets a cap and tests it).
- [x] pgTAP: RLS enabled and forced, cross-user isolation, cascade on account deletion, BMR/trend/intake cases shared with the domain tests where the domain has the formula.

**Host tests:** pgTAP `coach_derived_values.test.sql` (math cases: BMR example of `04` §1.3, trend slope and interval on a fixed series, 60 % vs 70 % logged days, re-ask inside/outside, no-history provisional) and `coach_summary.test.sql` (window, shape, isolation; the summary includes steps and any cardio sessions so later calls can respect the "never reduce cardio or steps" rule, `07` §3). Kotlin: DTO mapping tests for the estimates (if T-157 ships the repository; otherwise T-158).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-158 starts).

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4. About 8 files (SQL heavy).
**Dependencies:** E12 closed (`v1.3.0`); breakdown agreed by the user
**Files likely touched:** `supabase/migrations/<ts>_coach_derived_values.sql`, `supabase/migrations/<ts>_coach_summary.sql`, `supabase/tests/{coach_derived_values,coach_summary,delete_account_cascade}.test.sql`, `planning/03-data-model.md`, `planning/04-business-rules.md`, `planning/07-coach-design.md`
**Estimated scope:** L

---

## T-158: Trainer tab and the derived-values card (US-68 UI) · M

**Story:** US-68 · **Flow:** core + UI · **Size:** M · **Scenarios:** Values are shown with their source; Starting value without history; Confirming; Editing a value; Re-asking when the data changes (UI); US-69 trend and unreliable-intake display

**Description:** (1) **Navigation (ADR-79):** fourth tab "Trainer" between "Entreno" and "Perfil" (Diario, Entreno, Trainer, Perfil); label and icon checked by ux-reviewer; the rail on medium/expanded too; tab restored after process death (ADR-40); update `05` §5 (tabs) and the §8 "Decided" line ("three tabs" → four, ADR-79). (2) **Core:** `CoachRepository` port + supabase-kt implementation (estimates, confirm), `DerivedValue` models, `TrainerViewModel` (load, confirm, edit, offline, error). (3) **UI — templates and patterns:** Trainer root = `AppScreen` tab root with its own compact header (R-01, R-05); a derived-values card (`AppCard` with one row per value: activity level, basal expenditure, total expenditure, weight trend with its uncertainty, experience; each row shows the value, its source data and what is missing; provisional values carry a text label plus a marker, never colour alone, R-41; build the shared provisional label here); "Confirmar" (Gherkin literal; see E13 Q5 for the glossary entry) as the card's primary button; editing the activity level opens an Edit sheet (`AppEditSheet`, R-07/R-38) reusing the existing activity-level cards of Preferencias (title + description, no new selector, R-39); the unreliable intake estimate shows the reason as an `InlineNote`; `HelpButton` on each derived value explaining how it is computed (R-46); empty/loading/error/offline per R-30/R-31. The plan proposal does not exist yet: the card lives on the Trainer root and T-162 shows it inside the proposal too.

**Acceptance criteria:**
- [x] The bar shows Diario, Entreno, Trainer, Perfil; Trainer opens the root with the derived-values card.
- [x] With steps and sessions, the card shows the activity level, its source and the missing data.
- [x] Without Health Connect data or history, the activity level is the profile's, labelled provisional.
- [x] "Confirmar" stores the confirmation (provisional label disappears, survives reopening the app); editing the activity level and confirming stores and shows the edited value.
- [x] A value re-asked by the backend shows as provisional again.
- [x] The weight trend shows its uncertainty; an incomplete diary shows the unreliable-estimate note.
- [x] Trainer tab, card scroll and the edit sheet draft survive background and process death (ADR-40).

**Host tests:** repository/DTO mapping tests; `TrainerViewModel` tests (load, confirm, edit, re-ask, offline, error); Robolectric tests (four tabs in order, card rows with source and missing data, provisional label text, "Confirmar", edit sheet with the activity cards, unreliable note, help sheets); navigation restoration test; recreated-ViewModel test.

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 14 files.
**Dependencies:** T-157 merged and deployed; E13 Q5 resolved (2026-10-29: glossary entries "Confirmar", "Aceptar"/"Descartar", added in `05` §10 by this task)
**Files likely touched:** `shared/…/domain/model/Coach*.kt`, `shared/…/domain/repository/CoachRepository.kt`, `shared/…/data/{remote/CoachDto,repository/SupabaseCoachRepository}.kt`, `shared/…/di/`, `feature/trainer/{TrainerScreen,TrainerViewModel,TrainerUiState,DerivedValuesCard,ActivityLevelSheet}.kt`, `designsystem/` provisional label, `app/{AppRoutes,AppNavHost,AppBottomBar}.kt`, strings, `planning/05-design-system.md` §5/§8/§10, tests
**Estimated scope:** M

---

## T-159: Progression suggestion per exercise in the live session (US-70) · L

**Story:** US-70 (Gherkin amended per E13 Q1/Q2, resolved 2026-10-29) · **Flow:** backend + core + UI · **Size:** L (the planner may run backend + core and UI as developer slices of the same PR) · **Scenarios:** Rep range per routine exercise; Raising the weight; Raising the reps; Lowering after two failed sessions; Without RPE; Each session uses its own rep range; Open discomfort blocks increases; Severe discomfort blocks the exercise; Ignoring the suggestion; US-76 "Deterministic features keep working" (no model, no budget check)

**Rep range (E13 Q2 A):** an optional "Rango de repeticiones" (min-max) per routine exercise: one migration on `routine_exercises` (nullable min/max with checks 1-50 and min ≤ max; the planner decides whether to reuse the legacy `target_reps`/`target_reps_max` summary columns or add new ones, and records it in `03`), carried by `save_routine` and its reads; in the routine builder a range control on each exercise beside "Descanso" / "Objetivo RPE", with the same control type (R-47, no dropdown), "Sin definir" allowed; a `HelpButton` explaining what the range is for (R-46). Without a range, no progression suggestion is shown for that exercise. The block creator (T-163) fills it.

**Description:** (1) **Rule (deterministic, no model; `07` §3):** double progression over the exercise's last sessions (counted by sessions of the exercise, not calendar days): every working set at the top of the rep range with RPE ≤ 8 → next available increment (`04` §4.9: derived rounding = 2 × smallest enabled plate; dumbbells by the handle rules; machine/other per the planner); not all at the top → same weight, goal one more rep; most working sets below the bottom of the range in two consecutive sessions → 5 % lower rounded to the increment; no RPE on the sets → only the reps rule; RIR = 10 − RPE (ADR-76). Open discomfort (level 1-3) → no load increase (reps rule and the 5 % drop still apply) and the suggestion's reason states neutrally that the load is kept because of the open discomfort (E13 Q1 B; no health advice); open severe discomfort (level ≥ 4, ADR-81) → no suggestion line at all; no health message in either case (ADR-81, `07` §7). The rep range is the routine exercise's (above) and is **per session**: with "Ganar fuerza" a block varies load and reps within the week (undulating, `07` §3), so the rule uses the range of the session being opened and compares it only with earlier sessions of the exercise that had the same range (planner records the matching rule; other intentions keep one range per exercise). The planner picks SQL (RPC + pgTAP) or TypeScript per the US-70 technical note; the live session calls it once per session load for its exercises. (2) **Core:** `ProgressionSuggestion` model, repository method, `LiveSessionViewModel` loads suggestions with the session; works over the monthly AI limit (no Edge Function call). (3) **UI — templates and patterns:** under each exercise header in the live session, one suggestion line (e.g. "Sugerencia: 82,5 kg × 8"; copy by ux-reviewer, §10 glossary entry "Sugerencia") as an `InlineNote`-style row integrated in the exercise block (R-49), with a `HelpButton` opening an Info sheet with the reason and the sets used (dates, kg × reps @ RPE); the suggestion never fills or rewrites the planned values (architecture note; ADR-70 unchanged: "Actualizar automáticamente" keeps copying what was performed); it sits beside the open-discomfort block of T-147/T-148 without duplicating it (ux-reviewer defines the order). Offline: the suggestion loads with the session or is omitted silently.

**Acceptance criteria:**
- [x] The routine builder sets an optional rep range per exercise (same control as "Descanso"/"Objetivo RPE", "Sin definir" allowed, help sheet); it is saved, reloaded and survives process death; an exercise without a range shows no suggestion.
- [x] Range 6-8, every working set 8 reps at RPE ≤ 8 last time → suggested weight = next increment; the Info sheet lists the sets used.
- [x] 80 kg × 6, 6, 7 → stays 80 kg, goal one more rep.
- [x] Most sets under the minimum in the last two sessions → 5 % lower, rounded to the increment.
- [x] Sets without RPE → only the reps rule.
- [x] Per-session ranges: an exercise done at 3-5 reps on one day and 8-10 on another gets each day's suggestion from its own range and its own earlier sessions.
- [x] Open discomfort level 1-3 → no load increase, and the reason says neutrally that the load is kept because of the open discomfort; level 4-5 → no suggestion; no health message for any level.
- [x] Ignoring the suggestion → the routine's planned values are unchanged after finishing (with and without "Actualizar automáticamente").
- [x] With the monthly AI limit reached (fake usage), the suggestion still shows.
- [x] `04` §7 (progression rule, increments, discomfort effect) and `03` (RPC) updated.

**Host tests:** rule tests with the Gherkin cases (SQL pgTAP or `deno test`, plus Kotlin mapping); increment rounding cases (plates, dumbbells); discomfort levels 1-5 boundary; `LiveSessionViewModel` tests (suggestion loaded, ignored, planned values untouched, over-limit); Robolectric tests (suggestion line, help sheet with sets, no message with discomfort, severe = no line); live-session restoration with the help sheet open.

**Verification:**
- [x] pgTAP / `deno test` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main` (before Checkpoint E13-A is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 20 files (rep range added by E13 Q2: developer slices backend + core, then UI).
**Dependencies:** T-158 merged; E13 Q1 and Q2 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_routine_rep_range.sql`, `supabase/migrations/<ts>_coach_progression.sql` (or `supabase/functions/_shared/progression.ts`), `supabase/tests/{routines,coach_progression}.test.sql`, `shared/…/domain/model/{Routine,ProgressionSuggestion}.kt`, `shared/…/data/…` (`RoutineDto`, repositories), `feature/routines/{RoutineBuilderScreen,RoutineBuilderViewModel,RoutineDraft}.kt`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen,LiveSessionUiState}.kt` (+ `ProgressionHelpSheet.kt`), strings, `planning/03-data-model.md`, `planning/04-business-rules.md`, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

## T-171: Fixes from Checkpoint E13-A, Trainer tab (US-68) · M

**Source:** user feedback at Checkpoint E13-A, 2026-10-29. Screenshot: `design/reported/E13-A-trainer-weight-trend.png` (delete once the user confirms the fix).
- [x] **Notification bell missing on the Trainer root.** Every tab root shows the bell (`LocalNotificationsEntry`); Trainer does not. Fix and sweep every tab root and its top bar.
- [x] **"Nivel de actividad" sheet has "Guardar" on a single choice.** R-12 already says single-choice pickers apply on tap, no "Listo"/"Guardar". The sheet must apply (and confirm the values, as today) on tapping a card, with no button; sweep every sheet with one choice (a finding repeating is a candidate for a `check-ux-conventions.sh` guard).
- [x] **"Tendencia de peso" row:** "−0,3 kg/sem (±2,4)" is unreadable. ux-reviewer defines the rendering (plain sentence such as direction and rate, uncertainty explained or shown as a range, no raw "±"); sweep the other derived rows.
- [x] **Little diary data** (8 of 28 days): decided with the user, no change of the thresholds in `07`; the user keeps logging and reports the diary-dependent items (trend, "Diario incompleto") in about two weeks, outside this checkpoint's gate unless they say otherwise.
- [x] Spanish copy by ux-reviewer; host tests updated; `05` glossary/rules updated if a new rule is born.

**Flow:** core + UI · **Dependencies:** T-159 merged.

---

## T-172: Live session redesign: information density (US-70 and E12 additions) · L

**Source:** user feedback at Checkpoint E13-A, 2026-10-29. Screenshot: `design/reported/E13-A-live-session-density.png` (delete once the user confirms the fix).
The live session degraded across E12-E13: the exercise card now stacks the discomfort row with its "Ya no me molesta" button, the note row ("Otro · Pausa larga"), "Objetivo RPE" with `?`, the suggestion with `?`, the set table and "Añadir serie"; it looks scattered and "Ya no me molesta" is not integrated.
- [x] **ux-reviewer first** (plan mode, before the planner): think how to render all the per-exercise information in one coherent structure (what is always visible, what is collapsed or moved to the header ⋮ or a sheet, order, spacing, one visual grammar for status rows), reviewing the whole live session, not only the card.
- [x] "Ya no me molesta" integrated into the discomfort row (not a second segmented bar).
- [x] **Suggestion without its own `?`** (user asked "do we really need it?"): recommended default is to drop the separate `?` and let the suggestion line open the reason sheet (or fold the reason in the new structure); ux-reviewer decides within the redesign and records it.
- [x] **"Objetivo RPE" without its `?`** in the live session (user, 2026-10-29): the routine builder already explains it, so the live card repeats no help button for it; ux-reviewer checks that no other live-session row repeats a help the builder already gives.
- [x] Sweep: every element above kept functionally (E12 discomfort and notes, RPE target, suggestion), host tests updated, restoration kept.
- [x] The new structure becomes a rule/template in `05` §8-§9 in the same PR (a retired ad-hoc pattern is retired everywhere).

**Flow:** UX plan + core + UI · **Dependencies:** T-171 merged (or in parallel branches only if the user asks).

---

### Checkpoint E13-A — The Trainer tab knows you, and the gym tells you what to lift next 👤 (7 items + leave and come back) *(agreed with the user, 2026-10-29: three checkpoints)*
Journey: open the new Trainer tab, check what the coach estimated about you and confirm or fix it; then train and see, for each exercise, the weight and reps to aim for and why, also when an exercise bothers you. No paid AI is involved yet. Why here: right after T-159, the last model-free task, before the `coach` function and the plans build on the derived values.
- [x] T-157, T-158 and T-159 merged, CI green, `deploy-supabase` applied their migrations (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try:
  1. Trainer tab: the bar reads Diario, Entreno, Trainer, Perfil; Trainer opens with the card of estimated values (activity level, basal and total expenditure, weight trend, experience), each with where it comes from and what is missing.
  2. Provisional and confirm: values not yet confirmed are labelled provisional; tap "Confirmar": the label goes and stays after reopening the app.
  3. Edit: change the activity level in the card, confirm: the new value shows; Perfil → Preferencias still shows your profile data as before.
  4. Trend and diary: the weight trend shows a range (more weeks of weigh-ins = narrower); if your diary has gaps, the card says the expenditure estimate is not reliable.
  5. Rep range and progression: in a routine give an exercise a "Rango de repeticiones" (e.g. 6-8) and save; do that routine once with the range (history builds from the first entreno done with a range) and start it again: under the exercise a suggested weight and reps appears; tap its `?`: the reason lists the sets it used; an exercise without a range shows no suggestion.
  6. Discomfort: log a discomfort (level 1-3) on that exercise: no weight increase is suggested and the reason says the weight is kept because of the open discomfort; log a level 4-5 one on another: no suggestion at all; no health message anywhere.
  7. Ignore it: finish the workout without following the suggestion: the routine's planned values are unchanged (also with "Actualizar automáticamente" on, which keeps copying what you did).
  - Leave the app and come back on the Trainer tab with the activity-level sheet half changed, once in the routine editor with a rep range changed but not saved, and once in the live session with a suggestion's `?` open: the same tab, screen and draft are restored.
  - Out of scope: plans, calories and any AI text (from E13-B); "lowering after two failed sessions" and "without RPE" (host tests; try only if your data has them).

---

### 👤 Anthropic account and API key (before T-160; not a functionality checkpoint)
- [x] 👤 Create the Anthropic account, set its monthly spending limit (backstop of US-76; ADR-77) and enter the key as a Supabase secret in your terminal: `npx supabase secrets set ANTHROPIC_API_KEY=…` (never in the repo or the chat). The PM only verifies the name exists (`npx supabase secrets list`, via a subagent).
- [x] `planning/08-environment-setup.md` lists `ANTHROPIC_API_KEY` (T-160 added it; checked 2026-11-02).
- [x] **Key expiry** (a standing reminder for the user, not a task; kept here for reference): the console key expires 2 years after creation (set by the user, 2026-10-29; exact date in the console, about 2028-10-29). Rotate before it expires: new key, `npx supabase secrets set ANTHROPIC_API_KEY=…` again, revoke the old one. Set a yearly calendar reminder for the user. Revoke at once if the key is ever pasted anywhere public.

---

## T-174: Fix from Checkpoint E13-B: "Crear plan" shows "No se pudo usar la IA" (US-69) · M

**Source:** user report at Checkpoint E13-B, 2026-10-30. Tapping "Crear plan" in Trainer shows `coach_error` ("No se pudo usar la IA. Inténtalo de nuevo.", the generic `CoachFailure.Other`). The Supabase edge function log shows `POST /functions/v1/coach` answered **422** (no log lines, level warning, 2026-10-30 15:33 UTC). The dev-only ping button answers "ping correcto", so the function, the key and the JWT work: the 422 comes from the `plan_nutrition` request or its validation.
- [x] **Find the cause of the 422** (function logs, request payload shape vs the `plan_nutrition` validator, model output rejected after the retry) and fix it in every layer (function, app, tests).
- [x] **The app must say why:** map the failure to a specific `CoachFailure` and Spanish message where the user can act on it, instead of the generic one (ux-reviewer writes the copy); a rejected model output stays a clean "Inténtalo de nuevo" with a non-sensitive reason logged.
- [x] **Remove the dev-only "Probar coach (ping)" button** (T-160 decision: not part of the user-facing UI); keep the `ping` kind in the function only if tests or the evaluation need it.
- [x] Test with the real request shape (deno test for the handler, host test for the mapping); the user approves the one paid retry of "Crear plan".
- [x] After the merge and `deploy-supabase` green, Checkpoint E13-B is re-presented (items 1-9 to re-test). *(Done: E13-B re-presented and approved 2026-10-30.)*

**Flow:** backend + core + UI as the cause requires · **Dependencies:** T-165 merged.

**Open product gap (user feedback at Checkpoint E13-B, 2026-10-30; decision A: T-174 stays the error fix, this goes to the epic-planner in a fresh session after T-174 merges):** "Crear plan" has no interview. The coach reads the intention, profile and estimates itself and the user is blind to what is missing or how to fix it. Wanted: a new "Antes de empezar" step with an excellent UX that shows the user's profile data used by the plan, lets them confirm it or edit it (edits stay in the existing places, e.g. Perfil → Mi entrenamiento), and says what is missing and where to fix it, before the plan or block is generated. Also rethink the Trainer tab layout: "Tus valores" feels odd. Needs a story and Gherkin in E13, a checkpoint decision and a breakdown through the epic-planner; do not implement without it. Checkpoint E13-B stays gated until then.
→ Broken down 2026-10-30 (epic-planner): story US-78 in the epic file, tasks T-175 and T-176 (below T-165), open questions E13 Q14-Q22 in `planning/09-open-questions.md`, all resolved by the user on 2026-10-30. A second bug from the same checkpoint became T-177 (below), which runs first.

---

## T-177: Fix from Checkpoint E13-B: Trainer still shows "Crear plan" after a plan was created (US-69) · M

**Source:** user report at Checkpoint E13-B, 2026-10-30. Screenshot: `design/reported/e13-b-trainer-plan-missing-after-create.png` (delete once the user confirms the fix). After "Crear plan" succeeded (Diario's calorie and weight targets were set, so the plan was at least accepted for its nutrition or its goals were written), the Trainer tab still shows "Tu plan" with the "Crear plan" button: no pending or active plan card, and no "Crear bloque" (`BlockSection` returns nothing while `PlanSummary` is `None`). The user cannot reach the block.
- [x] **Find the cause:** the Trainer state is not refreshed after the plan is created or accepted (`TrainerViewModel` reload on return or on resume), or the open-plan lookup fails (repository query, status filter, RLS, DTO mapping), or the plan was never stored as accepted or proposed although `nutrition_goals` was written. Record the cause in the PR body.
- [x] **Fix in every layer** the cause touches (RPC or query, repository, ViewModel, screen); no workaround that hides the state.
- [x] **Host tests:** the Trainer tab right after a plan is created (pending card shown, "Crear bloque" reachable) and right after it is accepted (active plan card, "Crear bloque"); the same after leaving the app and coming back (recreated ViewModel and process death); pgTAP or repository test for the lookup if the cause is there.
- [x] After the merge (and `deploy-supabase` green if it touches a migration or the function), Checkpoint E13-B is re-presented (items 1-9 to re-test). US-78 (T-175, T-176) depends on this task.

**Flow:** core + UI as the cause requires (backend too if the lookup is server-side) · **Size:** M · **Agent runs:** planner, ux-reviewer (plan, if UI changes), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 6-7 · **Dependencies:** T-174 merged.

---

## T-160: `coach` Edge Function, AI usage and the monthly budget (US-76) · L

**Story:** US-76 · **Flow:** backend + small core/UI · **Size:** L · **Scenarios:** Usage is visible; User budget reached (refusal path; the report fallback is wired in T-166); Deterministic features keep working (already covered by T-159, re-asserted); Global budget

**Description:** (1) **Migration:** `coach_usage` (`user_id` cascade, `month` date (first day), `input_tokens`, `output_tokens`, `cost_eur` numeric, unique `(user_id, month)`), owner select only for the client; writes only by the function; a `security definer` aggregate (or the function's secret key, planner picks and records it in `02`) that returns only the global monthly total, never other users' rows; pgTAP. (2) **Edge Function `coach`** (Deno/TS, `verify_jwt = true`, RLS through the user's token, `ANTHROPIC_API_KEY` server-side only, ADR-17/77): request router by `kind` (`plan_nutrition`, `plan_block`, `report`, `chat`; T-160 ships the skeleton and a `ping`-style smoke kind only if needed), Anthropic client in `_shared/` with streaming (SSE to the app), a call timeout below the 150 s wall clock, JSON-schema structured output and a validator hook (retry once, then error; architecture), price table (Opus 5.5 $4/$20, Sonnet 5.5 $2/$10 per million, cache read/write factors, `07` §11; dollars counted as euros), usage recorded per call from the API's returned usage, **budget check before every call**: refuse when this user's month would pass 2 € or all users' would pass 5 € (`coach_limit_reached` error); when the Anthropic API rejects a call for lack of prepaid credit (balance at zero, auto-reload off), the function returns a clean `coach_no_credit` error and the app shows the same limit message ("sin saldo", copy by ux-reviewer), with no retry; never logs prompts, summaries or outputs; `deno test` with a fake Anthropic client (no paid call in CI). (3) **Core/UI:** `CoachUsage` model and repository; the usage view "this month's AI usage and the monthly limit" (e.g. "0,42 € de 2,00 €" with `AppLinearProgress`) in the place decided by **E13 Q7** (resolved 2026-10-29: a read-only "Uso de IA" entry from the Trainer root's ⋮ `AppActionSheet`, R-02, opening an `AppScreen`); the shared limit message (copy by ux-reviewer: the monthly limit was reached until next month; R-37) used by every later model feature. (4) **Docs:** `02` (function, streaming, timeout, budget), `03` §7 (function row, table), `08` (`ANTHROPIC_API_KEY`), `01` NFR-02 unchanged.

**Checkpoint E13-A approval changes (user, 2026-10-30; a correction of the T-171 mistake, not a rule):** (a) the "Tendencia de peso" row of T-171 is a regression (the user asked for the numbers to be rendered properly, not replaced by text such as "Sin tendencia clara"; the original "−0,3 kg/sem (±2,4)" had a unitless margin and no hierarchy): reuse what "Peso" already does (R-39), the `TrendPill` with its arrow and favorable/unfavorable/caution colour (`WeeklyRatePill` and its tone logic), showing the figure with its unit; the margin goes on the supporting line with its unit and a label ("Margen ±2,4 kg/sem · Según tus pesajes"), no `HelpButton` (the row must explain itself, user, 2026-10-30); the pill and margin must be integrated in the row's own layout, like the other derived rows, not a pasted-on element (user, 2026-10-30); ux-reviewer confirms the exact layout and sweeps the other derived rows; (b) remove the progression footer "Es solo una sugerencia: no cambia tu rutina ni tus series." (`progression_help_footer`, and its use in the sheet), host tests updated.

**Acceptance criteria:**
- [x] Checkpoint E13-A changes (a) and (b) above are done: trend shown as numbers, footer text gone.
- [x] Usage screen shows this month's usage and the 2 € limit; zero usage shows 0,00 €.
- [x] A user at 2 € gets `coach_limit_reached` for any model call; the app shows the limit message; deterministic features keep working.
- [x] All users at 5 € → any user's model call is refused with the same message.
- [x] Prepaid credit at zero (provider rejects the call) → `coach_no_credit` error and the app shows the "sin saldo" message; no retry; no usage recorded for the refused call.
- [x] Each call stores input/output tokens and cost from the API usage and the price table; dollars counted as euros.
- [x] A call that would exceed the wall clock is cut by the timeout and returns a clean error.
- [x] pgTAP: RLS, cross-user isolation, client cannot write usage, global total exposes no row, cascade on account deletion.
- [x] One real smoke call, approved by the user beforehand, succeeds after deployment and its cost appears in the usage view (done in T-161 via the dev-only button).

**Host tests:** `deno test` (budget boundaries for user and global, price math incl. cache factors, usage recording, timeout, retry-once on invalid JSON, streaming framing, JWT required); pgTAP `coach_usage.test.sql`; Kotlin repository mapping; usage ViewModel tests; Robolectric tests (usage screen, limit message, offline).

**Verification:**
- [x] `deno test --allow-all supabase/functions`, pgTAP, lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main` (migration + function); smoke call approved by the user.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** Checkpoint E13-A approved; 👤 Anthropic key step done; E13 Q7 resolved (2026-10-29: "Uso de IA" from the Trainer root's ⋮)
**Files likely touched:** `supabase/migrations/<ts>_coach_usage.sql`, `supabase/tests/{coach_usage,delete_account_cascade}.test.sql`, `supabase/functions/coach/{index,router,budget,prices}.ts` (+ tests), `supabase/functions/_shared/anthropic.ts`, `supabase/config.toml`, `.github/workflows/deploy-supabase.yml` (if functions are listed), `shared/…` (usage model, repository, DTO), `feature/trainer/{CoachUsageScreen,CoachUsageViewModel}.kt`, strings, `planning/02-architecture.md`, `planning/03-data-model.md`, `planning/08-environment-setup.md`, tests
**Estimated scope:** L

---

## T-161: Plan nutrition and outcomes: generation, validation and acceptance (US-69 backend) · L

**Story:** US-69 (+ US-74 "Every decision is stored" for plans and calorie proposals) · **Flow:** backend · **Size:** L · **Scenarios:** Weight loss plan; Calorie floor; Muscle gain plan; Outcomes are optional; Accepting writes the nutrition goal (RPC); Incomplete diary (no calorie change); Changing the calories within a block (trigger + proposal)

**Description:** (1) **Migration:** `coach_plans` (epic §Tables: `block_start`, `block_weeks` = 4, `intention`, `nutrition` jsonb, `outcomes` jsonb, `method` jsonb, `status` enum proposed | accepted | superseded | discarded, `data_version`; `nutrition` carries an `edited_by_user` flag), `coach_recommendations` (epic §Tables; `kind` enum starts with `plan`, `calorie_change`; `input_summary` = the `coach_summary` used), owner RLS, cascade, pgTAP. (2) **Deterministic nutrition** (TypeScript in the function or SQL; `07` §4): target calories from the confirmed expenditure and the intention's rate (loss 0.5-1 %/week, default 0.5 %; gain 0.25-0.5 %; recomposition/maintenance per `07`), calorie floor 1200/1500 kcal (ADR-25) with "the rate is slower because of the floor", protein ≥ 1.6 g/kg (≤ 2.2 plateau; deficit high-lean-mass upper end), fat ≥ 20 % (20-35 %), carbs the rest; 7 700 kcal/kg only without history; **protein distribution guidance** (`07` §4, user-approved 2026-10-29): about 0.4 g/kg per meal over at least 4 meals, stored in `nutrition` as guidance text and figures, never as a meal plan (no foods, no meal times; `01` §5 excludes meal plans). (3) **Model call `plan_nutrition`** (Opus 5.5, ADR-77; streamed; under the 150 s limit): the model selects outcomes within validated options (weight, body fat, circumferences, e1RM per main lift; none is allowed) and writes each outcome's reason and deadline (normally the end of the block, `07` §6); the server validator rejects any plan that breaks a rule (rates, floor, protein, fat, outcome without reason/deadline, a figure not derivable from the summary), retries once, then errors (architecture). Refused when derived values are unconfirmed? → the plan is generated but marked `requires_confirmation` (US-68 "Unconfirmed values block the plan": the **accept** is blocked, not the proposal). (4) **RPCs:** `accept_coach_plan(p_plan_id)` (rejects while any derived value is unconfirmed; supersedes the previous accepted plan; writes `nutrition_goals` so Diario's daily calorie and macro targets are the plan's, within the existing `nutrition_goals` columns, planner maps strategy/adjustment/percentages; stores the decision in `coach_recommendations`), `discard_coach_plan`, `decide_recommendation(p_id, p_status accepted|edited|discarded, p_payload)`; a Preferencias save that changes the nutrition goal marks the accepted plan's nutrition `edited_by_user` (E13 Q8). (5) **In-block calorie change (deterministic):** when the plan's target rate is outside the trend's confidence interval for two consecutive weeks and the intake estimate is reliable, the coach creates a `calorie_change` recommendation of at most 10 % of the calories (`07` §4); applied only through `decide_recommendation` accepted; evaluated when the coach runs (E13 Q6, resolved: on each Trainer open and after saving the weekly check-in, at most once a day; never in the background, ADR-73). (6) Docs: `03`, `04` §7, `07` if a rate default needs a label.

**User decision, 2026-10-30 (the T-160 smoke call, folded into this task):** add a dev-only button (hidden in release builds) that calls the deployed `coach` function once with kind `ping` from the signed-in app; the user taps it once for the T-160 smoke call and the cost appears in the usage view. The button is removed or kept hidden after the smoke call; it is not part of the user-facing UI. The T-160 criterion "One real smoke call" is ticked when this succeeds.

**Acceptance criteria:**
- [x] "Perder grasa", expenditure 2600 kcal → calories below expenditure, rate 0.5-1 %/week, protein ≥ 1.6 g/kg, fat ≥ 20 %.
- [x] Woman whose deficit gives 1100 kcal → 1200 kcal and the plan states the slower rate.
- [x] "Ganar musculatura" → surplus rate 0.25-0.5 %/week.
- [x] Every plan carries the protein distribution guidance (≈ 0.4 g/kg per meal, ≥ 4 meals) computed from the body weight; no foods or meal times (US-69 "Protein distribution guidance", E13 Q12).
- [x] "Ganar fuerza" → may set e1RM outcomes, may set no weight outcome; each outcome has reason and deadline.
- [x] Accepting writes `nutrition_goals`; accepting with an unconfirmed derived value is rejected.
- [x] Diary logged on 60 % of days → no calorie change is proposed.
- [x] Rate outside the interval two weeks running → one `calorie_change` ≤ 10 %, applied only when accepted.
- [x] Invalid model output → one retry, then a clean error; nothing stored as accepted.
- [x] Every accept/edit/discard is stored with its `input_summary`.
- [x] pgTAP: RLS, isolation, cascade; RPC paths; `deno test` for the validator and nutrition math.

**Host tests:** `deno test` (nutrition math per scenario, floor by sex, validator rejections, retry-once, streaming with a fake client, budget refusal path, calorie-change trigger boundaries: one week vs two, 10 % cap, unreliable intake); pgTAP `coach_plans.test.sql` (accept/discard/supersede, unconfirmed blocks accept, nutrition goal written, decisions stored, Preferencias edit flag).

**Verification:**
- [x] `deno test`, pgTAP green; after the merge `deploy-supabase` green on `main` (merged; tick drift fixed 2026-11-02).
- [x] E13 Q4 B (resolved): no evaluation gate here; one real plan call approved by the user as a smoke test (done at Checkpoint E13-B).

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4. About 12 files.
**Dependencies:** T-160 merged and deployed; E13 Q6, Q8, Q12 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_plans.sql`, `supabase/tests/{coach_plans,delete_account_cascade}.test.sql`, `supabase/functions/coach/{planNutrition,nutritionRules,validators,schemas}.ts` (+ tests), `supabase/functions/_shared/`, `planning/03-data-model.md`, `planning/04-business-rules.md`
**Estimated scope:** L

---

## T-162: Plan proposal screen, nutrition acceptance and the target weight retired (US-69 UI; US-68 gating; ADR-78) · L

**Story:** US-69 UI (+ US-68 "Unconfirmed values block the plan") · **Flow:** core + UI + small migration · **Size:** L · **Scenarios:** Weight loss plan (display); Calorie floor (display); Outcomes are optional (display); Accepting writes the nutrition goal; The user never enters target numbers; Changing the calories within a block (card); Unconfirmed values block the plan

**Description:** (1) **Core:** plan and recommendation models, repository (request a plan with streamed progress, accept, discard, decide recommendation), `PlanProposalViewModel`, Trainer root state (no plan / proposed / accepted / pending recommendation). (2) **UI — templates and patterns:** Trainer root gains the plan entry (e.g. "Crear plan" when none; the accepted plan's summary card otherwise; copy by ux-reviewer); the request shows streamed progress (R-31 "Preparando tu plan…"-style state; build the shared progress state here) then the **plan proposal** `AppScreen`: the derived-values card of T-158 on top (provisional values marked; the accept button disabled with the reason until "Confirmar", US-68), nutrition section (kcal, P/C/F grams with the fixed macro colours, weekly rate, the floor note when it applies, and the protein distribution guidance as an `InlineNote`-style line, e.g. "≈ 30 g de proteína por comida, en 4 comidas o más"; copy by ux-reviewer; guidance only, no meal plan), outcomes list (each with reason and deadline via `HelpButton`/Info sheet, R-46) and a pinned footer with the accept and discard actions (labels per E13 Q5); accept → Confirm haptic, Diario shows the new targets; discarding leaves Diario untouched. **`CoachProposalCard`** (new designsystem component: title, payload, reason, data used, "Aceptar" / "Descartar") on the Trainer root for the in-block calorie change; reused by T-165 and T-169. Limit reached → the US-76 message instead of the proposal. (3) **Target weight retired (ADR-78, US-69 technical note):** remove every UI that asks for a target weight (E5 goal card and sheets `GoalSheets.kt`/`GoalViewModel.kt`, onboarding if it asks, Preferencias if it asks), and "Peso" shows the accepted plan's weight outcome when there is one (otherwise nothing); migration drops `profiles.target_weight_kg` (and its column grant) after the UI no longer reads it; `04` §1.4 goal progress and §3.5 rewritten for the plan outcome; pgTAP `profiles.test.sql` updated. Sweep: no screen asks for a target weight, body fat or circumference target. (4) Glossary §10: plan, outcome ("Objetivo del plan"?), accept/discard labels (E13 Q5).

**Acceptance criteria:**
- [x] Creating a plan shows progress, then the proposal with calories, macros, rate, the protein distribution guidance and outcomes (reason and deadline each).
- [x] With an unconfirmed value the accept action is disabled with its reason; after "Confirmar" it is enabled.
- [x] Accepting → Diario's calorie and macro targets are the plan's; a later manual edit in Preferencias still works and marks the plan's nutrition as edited.
- [x] No screen asks for a target weight, body fat or circumference; "Peso" shows the plan's weight outcome when one exists.
- [x] A calorie-change proposal shows as a card; "Aceptar" applies it, "Descartar" leaves the calories.
- [x] Limit reached → the limit message, no proposal; offline → the standard hint.
- [x] The proposal screen, its scroll and the in-flight request survive background and process death (ADR-40; a finished stream is reloaded from the stored plan).

**Host tests:** repository/DTO tests (plan, recommendation, streamed events); ViewModel tests (progress, success, invalid → error with "Reintentar", limit, accept gated by confirmation, accept, discard, calorie card decisions); Robolectric tests (proposal sections, floor note, outcome help, disabled accept with reason, `CoachProposalCard`, "Peso" outcome, no target-weight inputs anywhere); restoration tests; pgTAP for the column drop.

**Verification:**
- [x] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main`, data intact (before T-163 starts) (merged; tick drift fixed 2026-11-02).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 18 files (the target-weight removal is mostly deletions; if the planner counts more than ~25, run core and UI as developer slices of the same PR).
**Dependencies:** T-161 merged and deployed (no checkpoint after T-162: its items are in Checkpoint E13-B); E13 Q5, Q8, Q9 resolved (2026-10-29)
**Files likely touched:** `shared/…` (plan models, repository, DTO), `feature/trainer/{TrainerScreen,TrainerViewModel,PlanProposalScreen,PlanProposalViewModel}.kt`, `designsystem/{CoachProposalCard,StreamProgressState}.kt`, `feature/weight/{WeightScreen,WeightTrendViewModel}.kt`, removal of `feature/weight/{GoalSheets,GoalViewModel}.kt` and their tests, onboarding/Preferencias target-weight bits, `shared/…/{ProfileRepository,SupabaseProfileRepository,NutritionGoalsDto}.kt`, `supabase/migrations/<ts>_drop_target_weight.sql`, `supabase/tests/profiles.test.sql`, strings, `planning/03`, `04` §1.4/§3.5, `05` §9/§10, tests
**Estimated scope:** L

---

## T-163: Block creator: training block generation and validation (US-72 backend) · L

**Story:** US-72 · **Flow:** backend · **Size:** L · **Scenarios:** Preview before saving (data); Frequency and volume are shown; One training day; Frequency chosen by the coach; Only available equipment; Saving creates routines (RPC); Nothing is saved without confirmation

**Description:** (1) **Migration:** `coach_plan_sessions` (`plan_id` cascade, `week` 1-4, `routine_id` nullable until saved, `session_id` nullable, `deload` boolean default false; plus the proposed routine content in the plan's `method` jsonb until saved), owner RLS via the plan, pgTAP. (2) **Model call `plan_block`** (Opus 5.5, streamed; **150 s limit**: the planner measures a realistic call against the fake and one approved real call; if it risks the limit, split per two weeks or per week, each a separate validated call): the model picks exercises **only from a candidate list built server-side** from the catalogue filtered by place and equipment (`07` §3; `movement_pattern` for substitutions), never free text names; the output is the week → routines → exercises → planned sets (rep range, sets, target RPE, rest) with a reason per exercise. (3) **Validators (`07` §3):** each muscle group at least twice a week when days ≥ 2; 1 day → one full-body routine; weekly sets per muscle group around the 10-set starting point (experience-adjusted, beginner lowest); intensity ranges per intention; 1-3 RIR on main work; only available equipment; session length respected when set; days unset → the coach chooses and states the frequency; **rules added to `07` §3 (user-approved 2026-10-29):** rest between sets written into each routine exercise's `rest_seconds` (2-3 min on main multi-joint lifts, 1-2 min on isolation; "main multi-joint" per E13 Q3), **exercise order** with the priority multi-joint lifts first in each session, **periodization**: "Ganar fuerza" varies load and reps within the week across the block (undulating, per-session rep ranges stored per planned exercise), every other intention keeps one rep range per exercise, **range of motion**: full range as a technique note on the exercise reason only (never a computed rule), **cardio**: the block never reduces cardio or steps (no output removes or shortens cardio exercises the user does, and no text advises fewer steps); retry once, then error. Weekly sets per muscle group are computed deterministically (`04` §4.11 weighting) and stored for display. (4) **RPC `save_coach_block(p_plan_id)`**: creates one routine folder for the block and the routines through the existing `save_routine` procedure (no parallel insert path), each routine exercise with its rep range (T-159's column, E13 Q2) and rest, links `coach_plan_sessions.routine_id`, and accepts the plan's training part (one plan acceptance per E13 Q9); nothing is created before this call. (5) Docs: `03`, `04` §7.

**Acceptance criteria:**
- [x] "Ganar musculatura", "Gimnasio" → a four-week block with routines and a reason per exercise.
- [x] 3 days → every muscle group at least twice a week; weekly sets per muscle group returned.
- [x] 1 day → one full-body routine per week.
- [x] No days or length set → the plan states the frequency it chose.
- [x] Equipment "Mancuernas" + "Peso corporal" → every exercise uses only that equipment (validator rejects otherwise).
- [x] Rests: main multi-joint lifts 120-180 s, isolation 60-120 s, written into the saved routines' `rest_seconds`.
- [x] Order: in every session the priority multi-joint lifts come before the isolation work.
- [x] "Ganar fuerza" → rep ranges vary across the sessions of a week; any other intention → one rep range per exercise in the block.
- [x] No output reduces cardio or steps; full range of motion appears only as a technique note.
- [x] `save_coach_block` creates one folder and the routines via `save_routine`, linked to the plan; without it no routine exists.
- [x] The block call finishes within the wall clock (measured, recorded in the PR body) or is split.

**Host tests:** `deno test` (candidate filtering, every validator rule with passing and failing fixtures incl. rest bounds, order, undulating vs single range by intention, cardio not reduced, frequency/volume math, one-day case, retry-once, split-call orchestration, timeout); pgTAP `coach_plan_sessions.test.sql` (RLS via plan, `save_coach_block` creates folder + routines through `save_routine`, idempotency, isolation, cascade).

**Verification:**
- [x] `deno test`, pgTAP green; after the merge `deploy-supabase` green on `main`.

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4. About 12 files.
**Dependencies:** T-162 merged and deployed (no checkpoint in between); E13 Q3, Q9 and Q11 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_plan_sessions.sql`, `supabase/tests/{coach_plan_sessions,delete_account_cascade}.test.sql`, `supabase/functions/coach/{planBlock,blockRules,candidates,schemas,validators}.ts` (+ tests), `planning/03-data-model.md`, `planning/04-business-rules.md`
**Estimated scope:** L

---

## T-164: Block preview and "Guardar bloque" (US-72 UI) · L

**Story:** US-72 · **Flow:** core + UI · **Size:** L · **Scenarios:** Preview before saving; Frequency and volume are shown; One training day; Frequency chosen by the coach; Only available equipment (display); Saving creates routines; Nothing is saved without confirmation

**Description:** (1) **Core:** block models, repository (request block with streamed progress, save block), `BlockPreviewViewModel`. (2) **UI — templates and patterns:** from the plan (proposal or accepted nutrition plan) the user requests the block; streamed progress (shared state from T-162); the **block preview** is an `AppScreen` with weeks as `AppTabRow` ("Semana 1"…"Semana 4", R-33 sibling views), each week a list of routine cards (`AppCard`) with `ExerciseRow`s in session order (tapping a name opens the exercise details, R-18) showing sets × rep range and the rest (shared duration formatter, R-47), the exercise's reason in an Info sheet (`HelpButton`, R-46) that also carries the full-range technique note; with "Ganar fuerza" the different rep ranges of each session are visible; a "Series por semana" section with the weekly sets per muscle group; the chosen frequency stated when the user set no days; pinned "Guardar bloque" (Gherkin literal, R-09); leaving the preview creates nothing (no discard dialog: it is not an editor, R-14; the proposal stays reopenable from Trainer, E13 Q10); after saving: Confirm haptic, the routines appear in Entreno in the block's folder, and the Trainer root shows the block. The new-block review at the end of a block or after preferences change (`07` §1) is the same entry ("Nuevo bloque"; copy by ux-reviewer). Limit reached → the US-76 message.

**Acceptance criteria:**
- [x] The preview shows each week with its routines and the exercises' reasons.
- [x] The weekly sets per muscle group are shown; with 3 days each group appears at least twice a week; with 1 day one full-body routine.
- [x] Without days set, the preview states the chosen frequency.
- [x] Each exercise shows its rest and rep range; multi-joint lifts come first; with "Ganar fuerza" the rep ranges differ between the sessions of a week; the saved routines keep those rests and that order.
- [x] With "Mancuernas" + "Peso corporal" only those exercises appear.
- [x] "Guardar bloque" → one folder with the block's routines in Entreno, linked to the plan; leaving the preview creates no routine.
- [x] Preview tab, scroll and help sheet survive background and process death (ADR-40).

**Host tests:** repository/DTO tests; ViewModel tests (progress, success, error/retry, limit, save, leave without saving); Robolectric tests (week tabs, routine cards, reason help, sets-per-group section, chosen-frequency line, "Guardar bloque", folder created via fake); restoration tests.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 13 files.
**Dependencies:** T-163 merged and deployed
**Files likely touched:** `shared/…` (block models, repository, DTO), `feature/trainer/{BlockPreviewScreen,BlockPreviewViewModel,BlockWeekTab,ExerciseReasonSheet}.kt`, `feature/trainer/TrainerScreen.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

## T-165: Plan weeks: adherence and the deload suggestion (US-73, US-75) · L

**Story:** US-73 + US-75 (merged: both deterministic on the plan weeks; each alone is M) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** US-73 Completing a planned routine; Weekly adherence; Missed sessions do not block the next week · US-75 No e1RM improvement for three weeks; The deload; Accepting or discarding

**Description:** (1) **Adherence (backend):** finishing a workout of a routine listed in the current plan week links it (`coach_plan_sessions.session_id`) in the same transaction as the finish path (`finish_workout_session` / `save_completed_workout`; planner picks the hook); week = block week by `block_start`; adherence % = completed / planned per week; a missed session is never moved; `coach_outcomes` gains `adherence_pct` per week if US-74 needs it (or T-167 computes it). (2) **Deload (deterministic, `07` §3/§9):** trigger = no **main lift** (definition per E13 Q3 (resolved: an exercise whose `movement_pattern` is squat, hinge, lunge, horizontal or vertical push or pull, done in at least one session in each of the three weeks; a deload is suggested only inside an accepted block)) improved its e1RM (`04` §4.1) over the last three weeks of sessions; it creates a `deload` recommendation for the next plan week; accepted → that week's `coach_plan_sessions.deload = true`; in the live session a deload week's exercises show the suggestion "same weights, 40 % fewer working sets" (rounded to the nearest whole set, at least 1 per exercise, E13 Q3) through the T-159 suggestion line, never rewriting the routine (architecture note); discarded → nothing changes. Evaluated when the coach runs (E13 Q6). (3) **UI — templates and patterns:** the Trainer root's block section shows each week with its sessions (done ✓ / pending), the week's adherence ("75 %") and the current week; the deload proposal is a `CoachProposalCard` (T-162) with "Aceptar" / "Descartar"; a deload week is labelled in the week list and in the live session (copy by ux-reviewer, glossary "Descarga"?). `04` §7 (adherence, deload).

**Acceptance criteria:**
- [x] Week 1 lists "Push"; completing "Push" that week links the workout to that plan session.
- [x] 4 planned, 3 completed → week 1 adherence 75 %.
- [x] A missed week-1 session: week 2 shows as planned; nothing is moved.
- [x] No main lift improved its e1RM in three weeks → a deload card for next week; none when one improved.
- [x] Accepted → next week marked deload; its exercises suggest the same weights and 40 % fewer working sets; routines unchanged. Discarded → week unchanged.
- [x] The Trainer root with the week list and the card survives background and process death (ADR-40).

**Host tests:** pgTAP (linking on finish within the week, other weeks/routines not linked, adherence math, isolation); deload trigger tests (three weeks boundary, main-lift filter per Q3, e1RM ties); set-reduction rounding cases; ViewModel tests (weeks, adherence, card accept/discard, live-session deload suggestion); Robolectric tests (week list, adherence text, card, deload label, live suggestion); restoration test.

**Verification:**
- [x] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` (before Checkpoint E13-B).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** T-164 merged; E13 Q3 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_adherence_deload.sql`, `supabase/tests/{coach_adherence,coach_deload}.test.sql`, `shared/…` (plan week, deload models; repository), `feature/trainer/{TrainerScreen,TrainerViewModel,PlanWeeksSection}.kt`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen}.kt`, strings, `planning/03`, `04` §7, `05` §10, tests
**Estimated scope:** L

---

## T-175: "Antes de empezar" inputs: one server list of the data a plan or block uses (US-78 backend) · M

**Story:** US-78 (added 2026-10-30 from the Checkpoint E13-B feedback; see the open product gap under T-174) · **Flow:** backend + core · **Size:** M · **Scenarios:** A summary of the data the plan will use (data); Details on tap (data); Missing or wrong data is flagged (data); Optional data left to the coach (data); An old weigh-in is flagged, not blocking (data); A new plan replaces the current one (data); The block has its own data (data)
**Status:** E13 Q14, Q18 and Q19 resolved by the user, 2026-10-30 (summary + details, flags only; weigh-in older than 14 days = caution, not blocking; two modes). Not blocked.

**Description:** (1) **RPC** `coach_plan_inputs(p_kind text)` (`plan` | `block`; `security invoker`, `authenticated`; planner records it in `03`): returns one entry per input of that kind (plan: training goal, sex, age, height, last weigh-in, activity level, total expenditure, weight trend, diary completeness; block: goal, place, equipment, days per week, session length, experience; the planner may adjust the list to what the function really reads and records it in `04` §7) with `key`, display value (raw, formatted by the app), `source` (profile, Mi entrenamiento, weigh-in with its date, coach estimate), `status` (`ok`, `missing`, `unset_optional`, `provisional`, `stale` = weigh-in older than 14 days), `blocking` (true when the `coach` function would refuse without it) and a `fix_target` enum (`my_training`, `personal_data`, `weight`, `activity_sheet`) that tells the app where the user fixes it; plus a top-level `current_plan` (`accepted` | `proposed` | none) so the screen can say the new plan replaces it (Q15); reads the existing tables only (`profiles`, `training_preferences`, weigh-ins, `coach_plans`, `coach_derived_estimates()`), no new table, no write. (2) **One source for "missing":** the `coach` function's `plan_nutrition` and `plan_block` precondition checks (the `missing` 422 mapped by T-174) read the same RPC (or a shared SQL helper both call), so the screen and the function never disagree; the `MissingField` set of T-174 is derived from it. (3) **Core:** `PlanInput` / `PlanInputs` models, `CoachRepository.planInputs(kind)` (supabase-kt), DTO mapping with unknown keys tolerated (as `MissingField.Unknown`). (4) Docs: `03` (RPC), `04` §7 (required inputs per kind).

**Acceptance criteria:**
- [x] Complete profile and "Perder grasa" → `coach_plan_inputs('plan')` lists every plan input with status `ok` and its source.
- [x] Goal "Sin definir" → the goal entry is `missing`, `blocking`, `fix_target = my_training`; same for any profile field the function needs, with `personal_data` or `weight`.
- [x] Days per week and session length unset → `unset_optional` for `block`, never `missing`.
- [x] Last weigh-in 15 days old → `stale`, not `blocking`; 14 days → `ok`.
- [x] A provisional derived value comes back `provisional` (from `coach_derived_estimates`).
- [x] An accepted or proposed plan → `current_plan` says which; none → absent.
- [x] With a required input missing, `plan_nutrition` / `plan_block` refuse with the same field the RPC reports (deno test against shared fixtures).
- [x] pgTAP: owner-only reads, cross-user isolation, `plan` vs `block` sets.

**Host tests:** pgTAP `coach_plan_inputs.test.sql` (each status, each `fix_target`, both kinds, isolation); `deno test` (precondition uses the shared list; missing goal / place / weight); Kotlin DTO and repository mapping tests (all statuses, unknown key).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` and `deno test --allow-all supabase/functions` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main` (migration + function) before T-178/T-176 start (tick drift fixed 2026-11-02).

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5. About 11 files. No paid call.
**Dependencies:** T-177 merged (and deployed if it changed the backend); Checkpoint E13-B re-presented after T-177 and released by the user (open 👤 gate, CLAUDE.md); E13 Q14, Q18, Q19 resolved (2026-10-30)
**Files likely touched:** `supabase/migrations/<ts>_coach_plan_inputs.sql`, `supabase/tests/coach_plan_inputs.test.sql`, `supabase/functions/coach/{planNutrition,planBlock,preconditions}.ts` (+ tests), `shared/…/domain/model/{PlanInput,MissingField}.kt`, `shared/…/domain/repository/CoachRepository.kt`, `shared/…/data/{remote/PlanInputsDto,repository/SupabaseCoachRepository}.kt` (+ tests), `planning/03-data-model.md`, `planning/04-business-rules.md`
**Estimated scope:** M

---

## T-176: "Antes de empezar" screen and the Trainer tab layout (US-78 UI) · L

**Story:** US-78 (+ US-68 confirmation moved per Q15, US-69/US-72 entries) · **Flow:** core + UI · **Size:** L · **Scenarios:** every US-78 scenario; US-68 Values are shown with their source, Starting value without history, Confirming, Editing a value, Unconfirmed values block the plan (amended 2026-10-30)
**Status:** E13 Q15-Q18, Q20, Q21 resolved by the user, 2026-10-30. Not blocked.

**Description:** (1) **Core:** `BeforeStartViewModel` (mode `plan` | `block`, loads `planInputs`, reloads on return from an editor, confirm-and-create per Q15, offline, error), `TrainerViewModel` adapted to the new root (Q20 A). (2) **UI — templates and patterns:** "Antes de empezar" is an `AppScreen` (read screen, back arrow, R-05/R-06) opened by "Crear plan" and "Crear bloque" (Q19: two modes). **Not an interview (Q14):** it shows a short **summary** of the data the plan or block uses (one `AppCard`, e.g. goal, sex and age, height and weight, activity level for the plan; goal, place, equipment, days and length for the block; ux-reviewer decides the summary lines) and, above it, the **flags** only: each missing, wrong or stale item as one row with a text marker and where to fix it (never colour alone, R-41; copy per Q16, e.g. "Falta: elige tu objetivo en Mi entrenamiento"), tapping a flag opens that place directly; tapping the summary opens the **details** (an `AppScreen` "Tus datos", the same one the Trainer root row opens) with sections by origin and one `NavigationRow` per input (R-03: label, value · source, chevron) that opens its **existing** place (Mi entrenamiento editor, Datos personales, Peso; R-04; Q17); the activity level opens the existing `ActivityLevelSheet` (`AppPickerSheet`, applies on tap, R-12); an optional unset input reads "Sin definir · Lo elige el entrenador" and is not a flag; provisional values reuse the T-158 "Provisional" `StatusChip`; a weigh-in older than 14 days is a caution `InlineNote` with its date, not blocking (Q18). **Replace notice (Q15):** with an accepted or pending plan, an `InlineNote` says the new plan replaces it ("Sustituirá tu plan actual" or similar; ux-reviewer finalises). Pinned footer (R-09) with `PrimaryButton` "Crear plan" / "Crear bloque": a line says that creating confirms the values shown; disabled with the reason above it only while a `blocking` flag exists (the function would refuse it, so no paid call fails); offline hint per R-30; tapping it confirms the shown values and goes to the existing streamed-progress state and proposal/preview (T-162/T-164 flows unchanged). (3) **Trainer root (Q20 A):** pending `CoachProposalCard`s (deload, calorie change) on top; "Tu plan" card (none: one line of what the plan is + "Crear plan"; pending; active with the current week and its adherence folded in from `PlanWeeksSection`); the block entry; the "Tus valores" card leaves the root and becomes a `NavigationRow` "Tus datos" (value = number of provisional values) opening the details screen above (no footer); leaves room for "Informe semanal" (T-166) and the chat entry (T-169). (4) **Plan proposal (Q21):** the derived-values card is removed; a value re-asked after generation shows one note that blocks "Aceptar" and links to "Tus datos". (5) **Sweep (R-39):** `DerivedValuesCard` retired from every screen (root and proposal) or reused inside the new screen only; `05` §6.2 (Trainer root) and §10 (glossary: "Antes de empezar", "Tus datos", the flag and optional texts, the replace notice; the "Confirmar" entry is retired or narrowed if no button uses it any more) updated; `GlossaryStringsTest` and `check-ux-conventions.sh` fixtures adjusted if a guard covers the root (G13 bell stays).

**Acceptance criteria:**
- [x] "Crear plan" opens "Antes de empezar" with the summary and no flags for a complete profile; no AI call until the footer is tapped.
- [x] Tapping the summary opens "Tus datos" with every input, its value and its source; tapping "Objetivo de entrenamiento" opens Mi entrenamiento and after "Guardar" the new value shows; same for Datos personales and Peso.
- [x] Goal "Sin definir" → a flag says it is missing and where; the footer is disabled with that reason; after fixing it from the flag, enabled.
- [x] Weigh-in older than 14 days → caution note with its date; "Crear plan" stays enabled.
- [x] With an accepted or pending plan, the screen says the new plan replaces it.
- [x] Days per week unset in the block mode → "Sin definir · Lo elige el entrenador", no flag, "Crear bloque" enabled.
- [x] A provisional value is confirmed by the footer and the plan is generated with it; leaving the screen confirms nothing and requests nothing.
- [x] The Trainer root follows the agreed layout (Q20): proposals, plan, block, "Tus datos"; the bell and ⋮ stay; "Uso de IA" still in ⋮.
- [x] The plan proposal has no derived-values card (Q21); a re-asked value blocks "Aceptar" with its note.
- [x] "Antes de empezar" (both modes), its scroll and the open activity sheet survive background and process death; returning from an editor restores the screen (ADR-40).

**Host tests:** `BeforeStartViewModel` tests (load both modes, missing → disabled with reason, reload after editor, confirm-and-create, leave without effects, offline, error); `TrainerViewModel` tests for the new root states; Robolectric tests (rows with value and source, missing and optional texts, provisional chip, footer enabled/disabled with reason, navigation to each existing editor via fakes, root order, "Tus datos" row value, proposal without the card); restoration tests (both modes, editor round-trip, process death).

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green. No paid call in CI; the user approves any paid plan/block call at the checkpoint.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 17 files (if the planner counts more than ~20, run core and UI as developer slices of the same PR; the root rework is not split into its own task: it depends on the same screen and on Q20).
**Dependencies:** T-175 merged and deployed; E13 Q14-Q21 resolved (2026-10-30)
**Files likely touched:** `feature/trainer/{BeforeStartScreen,BeforeStartViewModel,BeforeStartUiState,PlanInputRow}.kt`, `feature/trainer/{TrainerScreen,TrainerViewModel,TrainerUiState,TrainerPlanSection,PlanWeeksSection,DerivedValuesCard,PlanProposalScreen,PlanProposalViewModel}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** L

---

## T-178: Choose the Anthropic model from "Uso de IA" (test aid, E13) · M

**Source:** user request, 2026-11-01 (during T-175): the Trainer is still unstable and plans may need to be created repeatedly until it is finished, so the user wants to pick a cheaper model (e.g. "Haiku") for tests without changing code.
**Description:** (1) **Function:** the `coach` request accepts an optional `model` (whitelist: Opus 5.5, Sonnet 5.5, Haiku 5.5; anything else is rejected; absent = the current default per `kind`, ADR-77); the price table (`07` §11) gains Haiku 5.5 so `coach_usage` records its real cost (planner verifies the price); deno tests with a fake client. (2) **Core:** the chosen model is held in the app only, **no DB persistence** (user decision): process-lifetime state, planner decides whether it survives process death (ADR-40) or resets to the default; the coach repository sends it with every model call. (3) **UI:** a model selector in Trainer → ⋮ → "Uso de IA" (ux-reviewer: pattern, e.g. single-choice picker applying on tap, R-12, and Spanish copy; says plainly that it is for tests and that quality may be lower); default stays the current model. Plan validators and quality rules do not change per model.
**Acceptance criteria:**
- [x] "Uso de IA" shows the three models; the default is the current one; tapping another applies it with no save button.
- [x] With "Haiku" chosen, a plan, block or report request uses Haiku and its cost appears in the usage view with Haiku's price.
- [x] A request with an unknown `model` is refused cleanly; with none, the default per `kind` is used.
- [x] The choice is not stored in the database; closing the app resets it unless the planner records a process-death rule.
- [x] `07` §11 and `02` updated.

**Flow:** backend + core + UI · **Size:** M · **Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7 · **Dependencies:** T-175 merged and deployed. Runs before T-176 (user request). No paid call in CI.

---

## T-179: Long band and exercise alternatives (user request, E13) — L

**Source:** user request, 2026-11-01. Runs after T-176; it does not wait for Checkpoints E13-B1/E13-B2 (user decision, 2026-11-01). **Story:** none (training catalogue and trainer block editing). **Decision:** ADR-83.
**Flow:** backend + core + UI → **Size:** L → **Scenarios:** to be written by the planner (long-band catalogue, similar exercises, trainer replacement).
**Inputs (outside the repo, never commit):**
- `band-long-exercises.csv` (provided by the user, outside the repo, passed to `scripts/generate-band-long.py` as an argument): 30 rows, one per cable exercise, columns `key,name,equipment,muscle_group,hands,per_hand,media_source_key,description,instructions,tips`; instructions and tips separated by `|`.
- `exercise-with-alternatives.csv` (provided by the user, same way): 336 rows (30 long-band, 306 existing), one row per exercise, `alternative_exercises` as `key=score` entries separated by `;`; 804 entries / 402 unordered pairs, symmetric and score-matched.
- `library.json` and `images/*.webp` under `composeApp/src/commonMain/composeResources/files/exercises/` (gitignored). For each row, copy the media of `media_source_key` to the new `band-long-*` key; the new name, description, instructions and tips are Spanish long-band adaptations.

**Description:** (1) **Database:** a first migration adds `band_long` to `public.equipment` (its own file: `ALTER TYPE ... ADD VALUE` cannot be used in the same transaction that inserts rows with it); a later generated, idempotent migration inserts the 30 catalogue rows (key, muscle group, hands, `per_hand = false`, `movement_pattern` from the cable source) and the `exercise_alternatives` seed. `exercise_alternatives` stores one row per unordered pair (`exercise_id < alternative_id`, PK both columns, FKs to `exercises` on delete cascade, `similarity numeric(3,2)` with `0 < similarity <= 1`, check `exercise_id <> alternative_id`, index on `alternative_id`); RLS enabled and forced, select for `authenticated` only, no client writes; it holds catalogue data only, so no `user_id` and no account-deletion cascade. Deduplicate the CSV's 804 directed entries into 402 pairs and reject any score mismatch. Add RPC `exercise_alternatives(p_exercise_id uuid)` that returns both directions (other exercise id and similarity) so the client never queries the table directly. (2) **Library (outside repo):** add the 30 entries and copy the source media under the new keys; no RepDB text or images enter the repo or the database (ADR-38/39). (3) **App:** `Equipment.BandLong` (`band_long`) plus its Spanish label and hint; `DefaultEquipment.Home` adds it and Gym already selects every value; repository/DTO for the alternatives RPC; exercise detail gets a fourth tab "Similares" (after "Resumen", before "Historial"; ux-reviewer confirms the order) that lists thumbnail, name, equipment and similarity percentage, sorted descending, and opens that exercise's detail; loading/empty/error/offline per R-30/R-31. (4) **Trainer:** in the block preview, an exercise action opens a picker of its alternatives; the chosen replacement is kept in `BlockPreviewViewModel` state only until "Guardar bloque" (survives background/process death per ADR-40); leaving the screen discards it. Extend `save_coach_block` with an optional edits payload (`session_index`, exercise position or key, replacement exercise id/key); validate that the replacement is an alternative of the current exercise, keep the slot's sets, rep range and rest, update the exercise id/key for every week of that session, and only then create the routines. A second save stays idempotent. No model call and no other coach rule changes. (5) **Coach:** `candidates.ts` `EQUIPMENT_ORDER` adds `band_long` beside `band`; equipment filtering reads the new value. (6) **Docs:** `03` (enum, catalogue keys, table/RPC), `04` (alternatives and block edits), `05` (Similar tab, picker pattern, glossary), `07` (equipment order), ADR-83 consequences ticked.

**Acceptance criteria:**
- [x] `band_long` exists in `public.equipment` and the app; Home preselects it, Gym includes it, Other does not; the label reads "Banda larga" (label in `strings.xml`/`ExerciseLabels.kt` checked 2026-11-02; tick drift).
- [x] The catalogue has 30 `band-long-*` rows, each with its cable source's muscle group and hands, `per_hand = false`, and no cable row was removed.
- [x] The 30 library entries exist with copied media and the Spanish name, description, instructions and tips from the band-long CSV; no RepDB text or images are committed.
- [x] The alternatives table has 402 unordered pairs, symmetric by construction, with every cable/long-band pair at similarity 1 and inherited alternatives mapped to the long-band counterpart; the RPC returns both directions.
- [x] Exercise detail shows "Similares" with thumbnail, name, equipment and percentage, sorted descending; tapping a row opens that exercise.
- [x] In the block preview, an exercise offers only its alternatives; choosing one updates the local preview; leaving discards it; "Guardar bloque" applies it to that session in all weeks and the created routine uses the replacement.
- [x] A replacement that is not an alternative is rejected by `save_coach_block`; a second save returns the same folder and routines.
- [x] The block preview and its pending replacements survive background and process death; no database write happens before "Guardar bloque".
- [x] pgTAP: enum value, 30 rows, pair checks, RLS, RPC both directions, replacement validation, idempotency; Kotlin/Robolectric: enum/defaults, Similar tab sorted, picker local state, save payload, restoration.
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) and `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer × ux-reviewer (diff) = 8. About 20 files plus the outside-repo bundle.
**Dependencies:** T-176 merged; the two CSV files and the exercise bundle present; ADR-83 agreed (2026-11-01); database-first deployment accepted.
**Files likely touched:** `supabase/migrations/<ts>_band_long_enum.sql`, `supabase/migrations/<ts>_band_long_catalog.sql`, `supabase/migrations/<ts>_exercise_alternatives.sql`, `supabase/migrations/<ts>_save_coach_block_edits.sql`, `supabase/tests/{exercise_alternatives,save_coach_block}.test.sql`, `scripts/generate-exercise-catalog.py` (or a sibling generator for the two CSVs), `shared/.../domain/model/Exercise.kt`, `shared/.../domain/usecase/DefaultEquipment.kt`, `shared/.../domain/repository/ExerciseRepository.kt`, `shared/.../data/{remote/ExerciseDto,repository/SupabaseExerciseRepository}.kt`, `feature/exercises/{ExerciseDetailScreen,ExerciseDetailViewModel,ExerciseDetailUiState}.kt`, `feature/trainer/{BlockPreviewScreen,BlockPreviewViewModel,BlockPreviewUiState}.kt`, `supabase/functions/coach/candidates.ts` (+ tests), strings, `planning/{03-data-model,04-business-rules,05-design-system,07-coach-design}.md`, tests
**Estimated scope:** L

---

## T-180: Fix from Checkpoint E13-B1: the plan recommends an unrealistic calorie surplus (US-69) · M

**Source:** user report at Checkpoint E13-B1, 2026-11-01: the plan recommends about +800 kcal/day over what the user currently eats. The user wants no manual steps from them and trusts the fix; it is verified at the re-presented checkpoint. Second item (user request, 2026-11-01): the swap button of the block preview.
**Cause (confirmed, 2026-11-01, read-only study of the user's own data; nothing stored in the repo):** adaptive expenditure = mean intake − slope × 1100. The user's slope was −0.263 kg/week, `low_confidence`, 95 % interval [−2.56, +2.03], from 5 weigh-ins over about 3 weeks, so it added about +289 kcal (expenditure 2551 vs. a real maintenance of about 2200-2300; the user's own estimate is 2200). The +440 muscle surplus was then stacked on top: target 2972 = +710 over the mean intake of 2262 (28 of 28 days logged). The BMR path and the activity factor are not involved. Nothing compared the target with the real intake. A new user (little weigh-in history) hits the same failure.
- [x] **Slope guard:** ignore the weight-slope term of the adaptive expenditure when the trend is `low_confidence` or has fewer than 4 weeks / too few weigh-ins (the planner takes the minimum from `04`/`07` or proposes one); expenditure then anchors on the mean intake with zero weight change.
- [x] **Intake anchor:** the plan receives the last-28-days mean intake (new field in the coach summary, migration + pgTAP), with per-day outlier capping (the planner proposes the rule) so a 4446 kcal day or a partial day does not distort it.
- [x] **Target check:** a deterministic check in the rules and `assertNutritionInvariants` compares the target with recent intake and with the rate of the intention from `07` §3 (gain 0.25-0.5 % of body weight per week, loss 0.5-1 %), so a target outside the rate-derived range cannot ship. No fixed kcal cap. The model stays out of the arithmetic (`07` §1). Any product number not derivable from `planning/` gets a proposed default in the plan, not a blocking question.
- [x] **Tests:** regression with this user's shape (28 logged days, mean 2262, 5 flat low-confidence weigh-ins → maintenance about 2260-2300, plan about 2700-2740, never about 2972); tests per intention and for a brand-new user with few weigh-ins.
- [x] **Swap button:** in the trainer's block preview, the swap button is not shown on an exercise that has no similar exercise to replace it with (T-179 alternatives). Robolectric test.
- [x] **Docs:** `04` §7-§8, `07` §3-§4, ADR if the planner finds a new decision.
- [x] ~~After the merge (and `deploy-supabase` green), Checkpoint E13-B1 is re-presented~~ superseded: the re-test went to E13-B3 (skipped by the user) and is now Checkpoint E13-D item 5.

**Flow:** backend (Edge Function `coach` + validator) + docs, plus a small UI change (swap button): planner, ux-reviewer (plan), backend-developer, compose-ui-developer, pr-creator, pr-reviewer × ux-reviewer (diff). **Size:** M. The three-plan-options idea was dropped by the user (2026-11-01); decide after T-180 whether a "slow / standard" rate setting is needed.

---

### Cardio in the plan (epic-planner, 2026-11-01; ADR-85 proposed; the breakdown needs the user's OK before its PR merges)

User request, 2026-11-01: "the app replaces a real trainer; if the trainer would plan cardio, the app must". Runs before the E13-B1 re-test (user decision, 2026-11-01). Rules, grades and sources (final, verified by the fitness-scientist, checked online 2026-11-01): `07` §3 "Cardio prescription", §9, §10 refs. 29-50. Stories: US-79 (new), US-72 and US-73 amended (epic file). E13 Q23-Q30 resolved 2026-11-01 (`planning/09-open-questions.md`; Q30 by the PM: "Duración de la sesión" does not include the cardio).
**What changed in the rework (2026-11-01, fitness-scientist and ux-reviewer):** no warm-up anywhere (E13 Q27: the coach ignores warm-ups; the user adds warm-up sets himself); no separate cardio days or cardio-only routines; cardio is the last exercise of every lifting routine, the weekly minutes split evenly in 5-min units, at most 60 min per routine, the excess "por tu cuenta"; a cardio set is minutes only (no reps, RPE or rest timer); 150 min proposed for every intention, editable (Q31: form sheet on the "Cardio" row of the proposal and "Tu plan"); steps floor 7 000 at every age, not editable; cardio machines get their own equipment category (T-184, Q28b); cardio swap is any-to-any within the equipment plus walking and running, same minutes, no pairs table (user requirement).
**Order:** T-181 → T-184 → T-182 → T-183 (T-184 keeps its ID so the T-182/T-183 references in `07` and `09` stay valid).
**Sizing:** four tasks. US-79 is one vertical slice (T-181, M: deterministic rules in the function, the editable weekly minutes, two display spots, no new table). US-80 (cardio equipment, user decision 2026-11-01, E13 Q28b) is its own vertical slice (T-184, M, about 14 files: enum + re-tag migration, coach checks, `Equipment` enum and labels, the "Cardio" chip group and the "Antes de empezar" row); it cannot merge into T-181 (different domain, would pass ~25 files) or T-182 (already L). The block side of cardio (US-72 amended) splits backend → UI because it is about 25 files: T-182 (L: block rules, candidates, validators, a planned-duration column through `save_routine`, `start_workout_session` and `save_coach_block`, heavy `deno test` and pgTAP) and T-183 (L: block preview, routine editor and live session show the planned minutes, plus the US-73 weekly cardio and steps tracking, which reads the same block and cannot merge into T-182 without passing ~15 files). No S task.
**Shared UI built once (R-39):** every minute or total time ("50 min", "1 h 50 min") goes through the shared duration formatter (R-47) in the plan, the preview, the routine editor, the live session and the week; the cardio set reuses the existing duration metric types (`duration`, `distance_duration`) with `SetRow`, `SessionSetRow` and `MetricTypeLabels` (no new set row); the "Actividad" target rows reuse the plan proposal's value rows (T-162) and `HelpButton` (R-46).

## T-181: Weekly cardio and daily steps targets in the plan (US-79) · M

**Story:** US-79 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** Targets next to the calories; The coach proposes 150 minutes for every intention; The user changes the cardio minutes; Steps never go down; Steps floor without a watch; The steps target is not editable; Cardio does not change the calories; Accepting keeps the targets

**Description:** (1) **Rules (deterministic, `07` §3 "Cardio prescription", E13 Q23-Q25, Q29):** new `supabase/functions/coach/cardioRules.ts`: weekly cardio minutes = 150 for every intention (band 150-300 recorded for "Perder grasa"; the band is only the reason's context, not a limit on the user's edit); daily steps target = max(28-day average rounded down to 500, 7 000) at every age (no age input); reasons as server-built codes the model may only explain. Called from `planNutrition.ts` so the plan JSON (`coach_plans.nutrition` or `method`, planner picks and records in `03`) gains `activity: {cardio_min_week, steps_day, steps_basis}`; the calories are computed exactly as before (Q25: the planned cardio is never added to the expenditure) and `assertNutritionInvariants` (or a sibling `assertActivityInvariants`) checks the proposed 150, the 7 000 floor and "never below the current average". The output-text validator keeps rejecting phrases that reduce cardio or steps. (2) **Editable weekly minutes:** the user changes the cardio minutes freely (no lower limit; the planner proposes only a technical upper bound and the step, recorded in `03`/`04`); a value below 150 shows the plain note "Está por debajo de lo que recomienda la OMS (150 min). Puedes dejarlo así." and never blocks; the edited value is stored in the plan (new RPC or an extension of `accept_coach_plan`, new migration + pgTAP) and the calories do not change. The steps target is not editable: its row shows a plain note that it comes from the user's average and the recommendation. Where and what (E13 Q31, resolved by the PM 2026-11-01): the minutes are edited in a form sheet opened from the "Cardio" row, both on the proposal (before "Aceptar") and in "Tu plan"; an edit after the block is saved changes the weekly target (the week's tracking, T-183) and the next block (T-182 reads it), never the saved routines (their minutes are edited in the routine editor). (3) **Core:** `CoachPlan` model/DTO gain the activity targets (unknown keys tolerated; plans stored before T-181 have none and show nothing); repository call for the edit. (4) **UI — templates and patterns:** the plan proposal screen (`AppScreen`, T-162) gets an "Actividad" section under calories and macros with two value rows "Cardio · 150 min por semana · ritmo suave" (`plan_activity_cardio_row`) and "Pasos · 7.000 al día" (`plan_activity_steps_row`), numbers through the shared formatters (R-47, "7.000"); each row has a `HelpButton` Info sheet (`plan_activity_help`, R-46) with the reason in plain Spanish from `07` §3 "What the user sees" (base line incl. "Ritmo suave: respiras más fuerte, pero puedes hablar con frases completas.", the intention line, the calories line, and the cardio sheet ends with the medical line "Si tienes una enfermedad del corazón, metabólica o renal, o notas síntomas al esforzarte, consulta antes con tu médico."; steps: "Caminar unos 7.000 pasos al día se asocia con mejor salud a largo plazo. Si ya caminas más, mantenlo."); no tip lines, no mortality figure, never "previene lesiones". The minutes edit is the existing form-sheet archetype with the R-47 control (no new component), opened from `plan_activity_cardio_row` on the proposal and from the cardio line of "Tu plan". Without steps data the steps row still shows 7.000 (the "Sin datos de pasos" text belongs to the week, T-183). The Trainer root's "Tu plan" card (T-176) adds one summary line with both targets; the proposal's existing loading/error/offline states apply. (5) **Docs:** `04` §8.8 "Activity targets" with worked examples (11 000 average → 11 000; 7 740 → 7 500; 6 200 → 7 000; no data → 7 000; edit to 90 → note), `03` (plan JSON, edit RPC), `05` §10 glossary ("Cardio", "Pasos", "Ritmo suave").

**Acceptance criteria:**
- [x] Every intention → the plan proposes 150 min of cardio a week and a steps target, each with a reason; "Perder grasa" adds its intention line (Q23).
- [x] A 28-day average of 11 000 steps → 11 000; 6 740 → 6 500, below the floor → 7 000; 7 740 → 7 500; 8 260 → 8 000; no steps data → 7 000 at 35 and at 70 (Q24).
- [x] The calories, macros and rate are identical with any cardio minutes, proposed or edited (Q25); the T-180 regression cases still pass.
- [x] Changing the cardio to 90 min in the form sheet of the "Cardio" row keeps 90 and shows "Está por debajo de lo que recomienda la OMS (150 min). Puedes dejarlo así."; nothing blocks; the value survives reopening the plan. The same sheet opens from the proposal and from "Tu plan" (Q31).
- [x] An edit in "Tu plan" after a saved block changes the plan's weekly target (read by the week tracking and the next block) and leaves every saved routine's planned minutes unchanged.
- [x] The steps target cannot be edited and its plain note is shown.
- [x] A proposed cardio other than 150 or a steps target under the floor or the current average is rejected by the invariants (nothing stored).
- [x] The proposal shows "Actividad" with both rows and their help; the cardio sheet ends with the medical line; after "Aceptar", "Tu plan" in Trainer shows both; a plan stored before T-181 shows no activity line and no error.
- [x] The proposal scroll, an open help sheet and an open minutes edit with a changed value survive background and process death (ADR-40).
- [x] The sources of `07` §10 (29-50) are checked online and dated (fitness-scientist, 2026-11-01).

**Host tests:** `deno test` `cardioRules.test.ts` (150 for each intention, steps average vs floor incl. rounding down to 500, no steps data at any age, invariants pass and fail, calories unchanged with 0/90/150/300 min, text validator on "menos pasos"); pgTAP for the edit (bounds, ownership, isolation, calories untouched, saved routines untouched); Kotlin DTO mapping (with and without `activity`); `PlanProposalViewModel`/`TrainerViewModel` tests (edit, below-150 note, not-editable steps); Robolectric (`plan_activity_cardio_row` "Cardio · 150 min por semana · ritmo suave", `plan_activity_steps_row` "Pasos · 7.000 al día", `plan_activity_help` with the medical line last, the below-150 note, "Tu plan" line, old plan without the line); restoration tests.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` (function and migration deployed, data intact).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** T-180 merged and deployed (done); the user's OK on this breakdown and ADR-85; E13 Q23-Q25, Q29, Q31 resolved (done).
**Files likely touched:** `supabase/functions/coach/{cardioRules,cardioRules.test,planNutrition,nutritionRules,validators,schemas}.ts`, `supabase/migrations/<ts>_coach_plan_activity.sql` + `supabase/tests/coach_plan_activity.test.sql`, `shared/…/domain/model/CoachPlan*.kt`, `shared/…/data/remote/CoachDto.kt`, coach repository, `feature/trainer/{PlanProposalScreen,PlanProposalViewModel,TrainerScreen}.kt` (+ the plan card), strings, `planning/{03-data-model,04-business-rules,05-design-system}.md`, tests
**Estimated scope:** M

---

## T-184: Cardio equipment: its own category in "Mi entrenamiento" and "Antes de empezar" (US-80) · M

*Runs after T-181 and before T-182 (ID T-184 because 07 §3 and 09 Q26-Q30 already cite T-182/T-183 for the block; no renumbering).*

**Story:** US-80 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** Cardio machines are their own group; Place seeds the cardio machines; Saved equipment gains nothing; "Antes de empezar" shows the cardio; No cardio machine means walking

**Description:** (1) **Migration (new file, precedent `band_long`, ADR-83):** five new values of `public.equipment`: `cardio_bike`, `treadmill`, `elliptical`, `rower`, `cardio_other`; catalogue re-tag of the cardio machines from `machine` (`stationary-bike`, `air-bike` → `cardio_bike`; `incline-treadmill-walk`, `treadmill-running` → `treadmill`; `elliptical-trainer` → `elliptical`; `rowing-machine` → `rower`; `ski-erg`, `stair-climber` → `cardio_other`); `walking` and `running` stay `bodyweight`; `machine` then means lifting machines only. No saved `training_preferences` equipment list gains a cardio value (no silent gain) and an old `machine` is never read as cardio; "a gym with none selected: everything" (`04` §9.4) is unchanged. If the enum add and its use cannot share a transaction, two migration files (as T-179). pgTAP. (2) **Checks listed by the fitness-scientist (each verified or updated in this task):** coach `EQUIPMENT_ORDER` in `candidates.ts` (the five values placed so lifting candidates are unaffected), the candidate filter (a lifting slot never matches a cardio value), the block validators' equipment check, `coach_plan_sessions`, the live-session volume options (`VolumeOptionsSheets.kt`, if they group by equipment), `supabase/tests/exercises.test.sql` (catalogue counts and tags), and the generator `scripts/generate-exercise-catalog.py` if it carries equipment. (3) **Core:** `Equipment` enum + DTO mapping (unknown values tolerated), `DefaultEquipment` ("Gimnasio" selects all five cardio values, "Casa" none), labels in `ExerciseLabels` ("Bicicleta estática", "Cinta", "Elíptica", "Remo", "Otra máquina de cardio"), the exercise picker's equipment filter shows them. (4) **UI — templates and patterns:** "Mi entrenamiento" > "Equipamiento" (`MyTrainingSection`, existing chip pattern): a second chip group titled "Cardio" under the lifting chips with the five chips and the helper "Sin máquinas de cardio, el entrenador te propone caminar a paso ligero."; no new component. "Antes de empezar" (T-176, E13 Q17 detail-row pattern): a "Cardio" summary row ("Bicicleta estática, Cinta" or "Sin máquinas · caminar") that opens "Mi entrenamiento" and refreshes on return; it is never a flag. testTags per the ux conventions (e.g. `my_training_cardio_chip_<value>`, `before_start_cardio_row`; ux-reviewer confirms). (5) **Docs:** `03` (enum values, re-tag), `04` §9.4 (equipment filter incl. cardio), `05` §10 glossary ("Cinta", "Elíptica" if missing).

**Acceptance criteria:**
- [x] The five values exist; the eight cardio machines carry their new tag; `walking`/`running` stay `bodyweight`; no lifting exercise changed tag.
- [x] Existing saved equipment lists are unchanged (no cardio value added); a user with only `machine` reads "Sin máquinas · caminar".
- [x] "Gimnasio" preselects the five cardio chips, "Casa" none; the "Cardio" group sits under the lifting chips with its helper.
- [x] "Antes de empezar" shows "Bicicleta estática, Cinta" after picking those two, "Sin máquinas · caminar" with none; the row opens "Mi entrenamiento" and the summary updates on return.
- [x] Lifting candidates, block validators, `coach_plan_sessions` and the volume options behave exactly as before (regression).
- [x] "Mi entrenamiento" with changed cardio chips and "Antes de empezar" survive background and process death (ADR-40).

**Host tests:** pgTAP (enum values, re-tags, no saved list changed, `exercises.test.sql` updated, isolation); `deno test` (lifting candidates unchanged with cardio values present; `EQUIPMENT_ORDER`); Kotlin (enum/DTO with the new values and an unknown one, `DefaultEquipment` per place, labels); Robolectric (the "Cardio" group and helper, place seeding, the "Antes de empezar" row in both states and its navigation); restoration test.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (before T-182).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 14 files.
**Dependencies:** T-181 merged and deployed; the user's OK on this breakdown and ADR-85; E13 Q28b resolved (done).
**Files likely touched:** `supabase/migrations/<ts>_cardio_equipment_enum.sql`, `supabase/migrations/<ts>_cardio_equipment_catalog.sql`, `supabase/tests/exercises.test.sql` (+ a cardio-equipment pgTAP), `supabase/functions/coach/candidates.ts` (+ test), `shared/…/domain/model/{Exercise,TrainingPreferences}.kt`, `shared/…/domain/usecase/DefaultEquipment.kt`, `shared/…/data/remote/{ExerciseDto,TrainingPreferencesDto}.kt`, `feature/exercises/ExerciseLabels.kt`, `feature/profile/MyTrainingSection.kt`, `feature/trainer/{BeforeStartScreen,BeforeStartViewModel,BeforeStartUiState}.kt`, strings, `planning/{03-data-model,04-business-rules,05-design-system}.md`, tests
**Estimated scope:** M

---

## T-182: Cardio as the last exercise of every routine of the block (US-72 amended, backend) · L

**Story:** US-72 (cardio scenarios added 2026-11-01) · **Flow:** backend · **Size:** L · **Scenarios:** Cardio closes every routine (data); Minutes over the routine cap (data); Cardio does not shorten the lifting; Cardio uses the available equipment; Swapping the cardio keeps the minutes (data); A cardio set is minutes only (data); The coach plans no warm-up; Saving creates routines (planned minutes); Cardio is not reduced (kept)

**Description:** (1) **Migration (new file):** `routine_exercise_sets` gains a nullable planned duration (`target_duration_s`, bounds by the planner, at least up to 60 min, recorded in `03`) and `target_reps` becomes nullable only for a set that carries a planned duration (check: exactly one of reps or duration); `save_routine` accepts the duration per planned set only for duration metric types (`duration`, `distance_duration`) and rejects it with reps, or on a weight × reps exercise (`22023`); `start_workout_session` carries it into the pending set so the live session can show it; `update_routine_from_session` follows today's rules for planned values (planner checks ADR-70); pgTAP. (2) **Block rules (`blockRules.ts`, `04` §9, E13 Q26/Q30):** read the plan's `activity.cardio_min_week` (T-181, the user's edited value if any); split it evenly across the week's lifting routines in whole 5-min units, remainder to the first routines (150 over 3 → 50/50/50; 150 over 4 → 40/40/35/35), at most 60 min per routine; minutes over the cap become the week's `cardio_on_your_own_min` ("por tu cuenta", shown by T-183). Cardio is one slot, the **last** exercise of every lifting routine; there are no cardio-only routines and no extra plan sessions. Cardio is outside `session_minutes`: the lifting slots and volume of §9.3 are computed exactly as before (no lifting slot removed); the block carries each routine's total time (lifting + cardio) for the preview. (3) **Candidates (`candidates.ts`):** cardio candidates = catalogue `cardio` pattern with a duration metric type, inside the place and equipment like other slots; continuous "suave" only (no intervals, Q28); `EQUIPMENT_ORDER` unchanged. Cardio candidates filter on the T-184 cardio values (`cardio_bike`, `treadmill`, `elliptical`, `rower`, `cardio_other`) plus `bodyweight`; an old `machine` never yields cardio. **Default ranking (`07` §3, Q28b):** the most-logged cardio exercise of the last 8 weeks (`coach_summary`) if it is inside the equipment; else by the selected machines `stationary-bike` (`cardio_bike`), `elliptical-trainer` (`elliptical`), `incline-treadmill-walk` (`treadmill`; running only by swap), `rowing-machine` (`rower`); with no cardio machine (or only `cardio_other`), `walking` at a brisk pace ("Caminar a paso ligero"). Never by default: `running`, `treadmill-running`, `air-bike`, `ski-erg`, `stair-climber` and the non-duration moves (burpees, jumping jacks, jump rope, battle ropes, high knees, mountain climbers, heel flicks). The model picks one candidate per cardio slot exactly like lifting slots; minutes and pace come from the server. **Swap across modalities:** the user may swap the cardio for any catalogue cardio exercise timed in minutes within the equipment (he may lack a machine); the minutes never change with a swap (moderate is defined by effort, not modality, WHO 2020). **Any-to-any (user requirement, 2026-11-01):** every cardio exercise timed in minutes within the user's cardio equipment, plus walking and running always, is an alternative of every other one at the same minutes; there is no hand-curated pairs table and no per-exercise alternative list for cardio. The T-179 alternatives table is not used for cardio (it has only 2 cardio pairs, which are neither required nor consulted): the alternatives query and the validators derive the cardio alternatives from category (`cardio` pattern + duration metric) and equipment only; the query returns, for a cardio slot, every catalogue `cardio` exercise with a duration metric whose equipment is one of the user's cardio values or `bodyweight` (walking and running always; a treadmill adds incline walk and treadmill running; a bike adds stationary and air bike), cardio only, and `save_coach_block` accepts any exercise of that list on a cardio slot and rejects a cardio exercise on a lifting slot (and a lifting exercise on a cardio slot); the swap applies to every week of that routine. (4) **Validators:** `assertBlockInvariants` and SQL `coach_assert_block`: weekly cardio minutes equal min(target, 60 × routines) in 5-min units and the rest equals "por tu cuenta"; exactly one cardio exercise per routine and it is last (reject cardio not last); at most 60 min per routine; cardio inside the equipment; a cardio slot has one set with a planned duration and no reps, rep range or RPE (reject otherwise); no warm-up slot of any kind; the "never reduce cardio or steps" text rule stays. (5) **Save:** `save_coach_block` writes the cardio exercise with its planned duration through `save_routine` (rest not applicable); T-179 swaps keep working for lifting slots; a cardio swap follows the rule in (3) (minutes kept, every week of the routine). (6) **Remove from the earlier cardio draft:** the coach's warm-up slot, its type and its validators, and the non-lifting "Cardio" routines; the app's existing user-facing warm-up set type stays untouched (the user adds warm-up sets himself, Q27). (7) **Docs:** `04` §9.4 (cardio candidates), §9.6 rewritten (the block now has cardio fields), new §9.11 "Cardio in the block" with the split examples; `03` (column, nullable reps rule, RPC changes).

**Acceptance criteria:**
- [x] 150 min, 3 routines, "Gimnasio" → each routine ends with one cardio-pattern exercise of 50 min, low-impact machine first; the week adds up to 150; 150 over 4 routines → 40/40/35/35.
- [x] 300 min over 3 routines → 60/60/60 and 120 min "por tu cuenta"; 150 over 1 routine → 60 and 90 "por tu cuenta".
- [x] The lifting slots, sets and rests are identical with and without cardio for every day count 1-7 and session length; each routine carries its total time.
- [x] "Casa" + "Mancuernas" + "Peso corporal" (no cardio value) → only bodyweight cardio, default brisk walking; `cardio_bike` + `treadmill` → default `stationary-bike`, `treadmill` alone → `incline-treadmill-walk`, only `cardio_other` → walking; only an old `machine` → walking; running, treadmill running, air bike, ski erg, stair climber and the non-duration moves are never the default; the most-logged cardio of the last 8 weeks, if within the equipment, is the default.
- [x] Any-to-any: from every cardio exercise, the alternatives are all the other cardio exercises timed in minutes within the equipment plus walking and running, derived from category + equipment with no row of `exercise_alternatives` involved; a swap to any of them is saved with the same minutes in every week of that routine; a cardio exercise on a lifting slot, a lifting exercise on a cardio slot, or a cardio outside the equipment is rejected.
- [x] No routine has a warm-up exercise or warm-up sets; no cardio-only routine exists.
- [x] `save_coach_block` creates the routines with the cardio's planned duration and no reps; `start_workout_session` is unchanged (ADR-50: the live session reads the planned duration from the routine; the pending set carries only its type); a planned duration on a weight × reps exercise, or a duration set with reps, is rejected (`22023`).
- [x] Validators reject: cardio not last, two cardio exercises in a routine, more than 60 min in a routine, wrong weekly total, cardio outside the equipment, a cardio slot with reps or RPE, a reason that advises fewer steps.
- [x] Session count, rests, rep ranges, order and every existing §9 rule still pass; the block call still finishes within the wall clock (measured against the fake; a real call only with the user's approval).

**Host tests:** `deno test` (5-min split and remainder for 1-7 routines and 0/90/150/300/500 min, 60-min cap and "por tu cuenta", cardio last, lifting unchanged, equipment filter incl. home bodyweight, default ranking with and without machines, never-default list, most-logged default within and outside the equipment, no warm-up slot, invariants pass/fail incl. cardio not last and cardio with reps/RPE, cardio swap any-to-any (every pair of cardio exercises inside the equipment, none from the pairs table) with minutes kept in every week, regression of every existing block rule); pgTAP (`routine_exercise_sets` duration bounds, reps-or-duration check and metric-type check, `save_routine` round trip, `start_workout_session` copy, `coach_assert_block` new rules, `save_coach_block` with cardio and with a cardio swap (accepted across modalities, rejected on a lifting slot or outside the equipment), the cardio alternatives query, idempotency, isolation).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test` green; after the merge `deploy-supabase` green on `main` with data intact (before T-183) (merged #428; tick drift fixed 2026-11-02).

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4. About 12 files.
**Dependencies:** T-181 and T-184 (cardio equipment) merged and deployed; E13 Q26-Q28, Q28b and Q30 resolved (done).
**Files likely touched:** `supabase/migrations/<ts>_planned_duration.sql`, `supabase/migrations/<ts>_coach_block_cardio.sql`, `supabase/tests/{routine_planned_duration,coach_plan_sessions,save_coach_block,exercise_alternatives}.test.sql`, `supabase/functions/coach/{blockRules,candidates,blockValidators,planBlock,schemas}.ts` (+ tests; `coach_summary` read of the most-logged cardio, extended only if it lacks it), `planning/{03-data-model,04-business-rules}.md`. About 13 files.
**Estimated scope:** L

---

## T-183: Cardio in the preview, the routine and the live session; weekly cardio and steps (US-72 UI, US-73 amended) · L

**Story:** US-72 (cardio scenarios, display) + US-73 (Cardio minutes of the week; Steps of the week; No steps data) · **Flow:** small backend + core + UI · **Size:** L · **Scenarios:** Cardio closes every routine; Minutes over the routine cap; Cardio does not shorten the lifting (total time); Swapping the cardio keeps the minutes; A cardio set is minutes only; Training the cardio; Saving creates routines (planned minutes); Cardio minutes of the week; Steps of the week; No steps data

**Description:** (1) **Backend (small):** `coach_plan_weeks` (T-165) returns per week `cardio_min_done` (completed sets of `cardio`-pattern exercises logged that week, any cardio exercise counts, `04` §4.6), `cardio_min_target` (the plan's weekly minutes, incl. the "por tu cuenta" part) and `steps_avg` / `steps_target` (from `daily_activity`, days with data only; null without data); only logged cardio counts and nothing carries over to the next week. New migration + pgTAP. (2) **Core:** block models/DTO with the cardio slot, its planned minutes, each routine's total time and the week's "por tu cuenta" minutes; plan-week models with the cardio and steps figures; routine and live-session models carry the planned duration (T-182 column). (3) **UI — templates and patterns:** **block preview** (`AppScreen` + `AppTabRow` weeks, routine `AppCard`s with `ExerciseRow`s, T-164): the cardio row last in each routine (`block_preview_cardio_row`, "Bicicleta estática · 50 min · suave"; walking reads "Caminar a paso ligero"), each routine's total time with the cardio (`block_preview_cardio_total`, e.g. "1 h 50 min"), and, when the cap leaves minutes over, one week line with the "por tu cuenta" minutes (copy by ux-reviewer); no "Calentamiento" anywhere. **Cardio swap** (see T-182): the swap button on a cardio row opens a cardio-only list (the existing T-179 swap sheet archetype, no new component) with every catalogue cardio exercise timed in minutes that fits the user's cardio equipment, plus walking and running always (any-to-any: the same list from any cardio exercise, never from the pairs table; "Similares" pairs are not shown for cardio), and under the list the line "Cuentan los mismos minutos con cualquier cardio, a ritmo suave."; minutes, position and reason unchanged; the swap applies to every week of that routine. **Routine editor** (existing screen, migrated in this task): a cardio (duration metric type) set reuses `SetRow` with `MetricTypeLabels` and shows only the planned minutes, edited with the same duration control as "Descanso" (`routine_set_minutes_picker`, R-47, no dropdown), "Sin definir" allowed; no reps or RPE cells for that set; no other editor change. **Live session** (existing): the cardio set reuses `SessionSetRow` and shows only the planned minutes as the dim placeholder (`session_cardio_minutes_field`, `04` §4.3 pattern; no tap-to-fill), no reps or RPE; completing a cardio set starts no rest timer. **Trainer week section** (T-165): per week "Cardio de la semana: 100 de 150 min" (`plan_week_cardio_value`) with a progress bar whose `stateDescription` reads the same figures, and "Pasos: media 7.800 de 7.000" (`plan_week_steps_value`), "Sin datos de pasos" without data; status text, never colour alone. Every minute and total time goes through the shared duration formatter (R-47). (4) **Sweep:** `strings.xml` and the trainer, routine and workout features: no warm-up wording from the earlier cardio draft ("Calentamiento", warm-up lines or tips) is added or left; the user-facing warm-up set type of the routine editor is unchanged. (5) **Docs:** `04` §9.9 (cardio and steps per week), `05` §10 (glossary "Por tu cuenta" if ux-reviewer adds it), `03` (RPC fields).

**Acceptance criteria:**
- [x] The preview shows the cardio last in every routine with minutes and pace, each routine's total time, and the "por tu cuenta" line only when the cap leaves minutes over; no warm-up row.
- [x] Swapping the bike for walking shows the cardio-only list with the "Cuentan los mismos minutos…" line; the row becomes "Caminar a paso ligero" with the same minutes in every week of that routine.
- [x] Any-to-any: with "Bicicleta estática" and "Cinta" selected, the swap list from the bike, from the incline walk and from walking is the same set (stationary bike, air bike, incline walk, treadmill running, walking, running) minus the current one; the swap button always shows on a cardio row.
- [x] After "Guardar bloque" the routine editor shows the cardio set's planned minutes only (no reps, no RPE); changing and saving keeps them.
- [x] In the live session the cardio set shows only its planned minutes as the placeholder; completing it starts no rest timer.
- [x] Logging 100 of 150 cardio minutes in week 1 → "Cardio de la semana: 100 de 150 min" and the bar's `stateDescription` matches; the missing 50 do not move to week 2.
- [x] A 7 800 average against 7 000 → "Pasos: media 7.800 de 7.000"; without data "Sin datos de pasos".
- [x] Preview tab and scroll, an open cardio swap sheet, the routine editor draft with a changed planned minute, and the Trainer week survive background and process death (ADR-40).

**Host tests:** pgTAP (`coach_plan_weeks` cardio minutes from any logged cardio, nothing carried over, steps average over days with data, null without data, isolation); DTO tests; ViewModel tests (preview with cardio, total time and "por tu cuenta", cardio swap any-to-any (same list from any cardio exercise) keeps minutes in every week, routine editor planned minutes round trip, live placeholder and no rest timer after a cardio set, week figures); Robolectric (the nine testTags above with their formatted text, swap list line, no "Calentamiento", progress bar `stateDescription`, no-steps text); restoration tests.

**Verification:**
- [x] pgTAP, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (before Checkpoint E13-B3).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 16 files (the routine editor and the live session are touched only for the planned duration).
**Dependencies:** T-182 merged and deployed.
**Files likely touched:** `supabase/migrations/<ts>_coach_plan_weeks_cardio.sql`, `supabase/tests/coach_adherence.test.sql`, `shared/…` (block, plan-week, routine set models; DTOs; repositories), `feature/trainer/{BlockPreviewScreen,BlockPreviewViewModel,BlockWeekTab,PlanWeeksSection,TrainerViewModel}.kt` (+ the swap sheet), `feature/routines/` (`SetRow` for the cardio set), `feature/workout/` (`SessionSetRow` placeholder, rest timer start), `MetricTypeLabels`, strings, `planning/{03-data-model,04-business-rules,05-design-system}.md`, tests
**Estimated scope:** L

---

**Checkpoint E13-B3 — Re-test the plan, and cardio in your plan and block 👤 (7 items + leave and come back)** *(proposed by the epic-planner, 2026-11-01; needs the user's OK. It carries the pending E13-B1 re-test (user decision 2026-11-01: the re-test follows the cardio tasks), so the user installs one APK for both.)*
Journey: create a new plan and read its calories and its activity targets, accept it, create and save the block with cardio, train one routine with its cardio, and watch the week. Why here: right after T-183, the last cardio task, before T-166 (the weekly report) reads the plan weeks. T-181, T-184 and T-182 merge with no checkpoint after them.
- [x] T-181, T-184, T-182, T-183 merged, CI green, `deploy-supabase` green (data intact), every reported bug fixed.
- [x] 🛑 **Stop and rethink the next steps (user, 2026-11-01).** After this checkpoint, before T-166: the PM does not start T-166 and instead reopens the plan with the user: what happens when the block ends, how the plan adapts to what the user actually did (cardio, steps and sessions missed; the app never fails a plan), and what T-166 should be. `fitness-scientist` decides the adaptation rules first.
- [x] 👤 Skipped by the user, 2026-11-01 (functionality may change after the replan; the items below are re-presented in the next checkpoint). → Carried into **Checkpoint E13-D** items 5-7 (replan, 2026-11-01; the cardio `?` no longer has the medical line, ADR-89). Install the APK and try (a plan and a block request are paid calls of about 0,1-0,4 € each):
  1. E13-B1 item 5 re-test: "Ganar musculatura" gives a small surplus (about +440 over your real intake, not +800); "Perder grasa" below your expenditure.
  2. Activity targets: the proposal shows "Actividad" with "Cardio · 150 min por semana · ritmo suave" and "Pasos · 7.000 al día" (or your higher average), each with its `?` reason; the cardio `?` ends with the line about your doctor; 150 with any intention; change the cardio to 90: the note says it is under the WHO recommendation and nothing blocks; the calories do not change.
  3. Steps: your steps target is not below what you walk now (without watch data, 7.000) and cannot be edited; after "Aceptar", "Tu plan" shows both targets.
  4. Block with cardio: in "Mi entrenamiento" pick "Bicicleta estática" and "Cinta" in the new "Cardio" group ("Antes de empezar" shows them), then "Crear bloque": cardio is the last exercise of every routine, with its minutes and "suave", and no warm-up anywhere; the default is the bike or the incline walk (or your most-logged cardio); each routine shows its total time; the week's cardio adds up to the target (any minutes over 60 per routine show as "por tu cuenta"); swap the bike for walking: same minutes, and from any cardio the list offers every other one you have.
  5. Save and edit: "Guardar bloque", open a routine in Entreno: the cardio set shows only its planned minutes (no reps, no RPE); change them and save: kept.
  6. Train: start that routine: the lifting is as before, the cardio set shows only the planned minutes, completing it starts no rest timer; finish.
  7. The week: Trainer shows "Cardio de la semana: X de 150 min" from the cardio you logged and your average steps against the target (or "Sin datos de pasos").
  - Leave the app and come back on the block preview with a cardio row's `?` open, and once in the routine editor with a changed minute: the same tab, screen and draft are restored.
  - Out of scope: the weekly report and history (E13-C); invariant rejections (host tests).

---

### Checkpoints for US-78 (user decision, 2026-10-30: E13 Q22 B)
Collected test items after T-176: "Antes de empezar" for the plan; flags and where to fix; details and editing in place; confirmation and the replace notice; the new Trainer layout; plan rules and intentions; accept and Diario; no target numbers; AI usage; "Antes de empezar" for the block; block preview; structure and limits; save, train and adherence; deload (14). Grouped into two journeys of 7 and 6 (plus "leave and come back"), presented right after T-176 (and its `deploy-supabase`), E13-B1 first; T-166 waits for both approvals. The old E13-B list is re-presented earlier, right after T-177.

**Checkpoint E13-B1 — Before starting, and your nutrition plan 👤 (7 items + leave and come back)** *(user decision, 2026-10-30; moved after T-179 by the user, 2026-11-01)*
- [x] T-160…T-165, T-174, T-177, T-175, T-176 merged, CI green, `deploy-supabase` green (data intact), every reported bug fixed.
- [x] 👤 **(superseded: approved by the user 2026-11-01 except the T-180 re-test, which went to E13-B3 (skipped) and is now Checkpoint E13-D item 5; replan 2026-11-01)** Approved by the user on 2026-11-01 except the T-180 fixes: after T-180 merges only re-test item 5 (new calories: "Ganar musculatura" surplus about +440 over real intake, not +800) and leave and come back.** Install the APK and try (a plan request is a paid call of about 0,1-0,4 €):
  1. New Trainer layout: pending proposals on top, then your plan, the block and "Tus datos"; bell and ⋮ ("Uso de IA") as before.
  2. "Antes de empezar": tap "Crear plan": a short summary of your data shows; tap it: "Tus datos" lists each data with where it comes from; open one, change it in its usual place and come back: updated; go back from "Antes de empezar": no plan, nothing confirmed.
  3. Flags: set "Objetivo de entrenamiento" to "Sin definir": the screen flags it and says where to fix it, and "Crear plan" says why it cannot start; fix it from the flag and come back: the flag is gone. An old weigh-in (over 14 days) shows its date with a note but does not stop you.
  4. Confirm and create: with a plan already pending or active, the screen says the new one replaces it; with a provisional value, tap "Crear plan": the values are confirmed ("Tus datos" no longer shows provisionales), progress shows, then calories, macros, weekly rate, protein guidance and outcomes with reason and deadline.
  5. Rules and intentions: "Perder grasa" below expenditure, protein ≥ 1,6 g/kg, fat ≥ 20 % (floor note if it applies); "Ganar musculatura" small surplus; "Ganar fuerza" possibly e1RM outcomes.
  6. Accept: Diario's targets are the plan's; a manual change in Preferencias marks the plan as edited by you; the proposal shows no values card.
  7. No target numbers and cost: no screen asks for a target weight; Peso shows the plan's weight outcome; "Uso de IA" shows the usage and the 2 € limit.
  - Leave the app and come back on "Antes de empezar" with the activity sheet open, once while the plan is being prepared: the same tab, screen and state are restored.
  - Out of scope: the block (E13-B2); in-block calorie change and limit message (host tests).

**Checkpoint E13-B2 — Your four-week block, adherence and deload 👤 (6 items + leave and come back)** *(user decision, 2026-10-30)*
- [x] Checkpoint E13-B1 approved (except the T-180 re-test); fixes merged.
- [x] 👤 Approved by the user, 2026-11-01 (no feedback). Install the APK and try (a block request is a paid call):
  1. "Antes de empezar" for the block: tap "Crear bloque": the summary shows place, equipment, days per week and session length; an unset day count reads that the coach chooses it and is not flagged.
  2. Block preview: four weeks with routines, sets per muscle group, each group at least twice a week with 3 days (or the chosen frequency); an exercise's `?` shows its reason.
  3. Structure and limits: multi-joint lifts first with 2-3 min rest, isolation 1-2 min; "Ganar fuerza" varies reps within the week; 1 day = one full-body routine; "Mancuernas" + "Peso corporal" only those exercises.
  4. Save or leave: leave the preview: no routine; "Guardar bloque": one folder in Entreno with the rep ranges.
  5. Train and adherence: train one routine: the Trainer week shows it done and the percentage; skip one: next week stays as planned.
  6. Deload: if your data allows it, "Descartar" leaves the week; "Aceptar" marks next week as deload with 40 % fewer working sets; routines unchanged.
  - Leave the app and come back on the block preview in week 3 with a reason open: the same tab, screen and state are restored.
  - Out of scope: the weekly report, history and chat (E13-C).

---

### Checkpoint E13-B — Your plan from the coach: nutrition, the four-week block, adherence and deload 👤 (9 items + leave and come back) *(agreed with the user, 2026-10-29: three checkpoints)*
*(2026-10-30, user: re-presented right after T-177 (items 1-9 to re-test); its approval releases T-175. US-78 is then tested in E13-B1/E13-B2 above.)*
Journey: ask the coach for a plan, read the calories, macros, protein guidance and outcomes with their reasons, confirm your values and accept it so Diario follows it (the old target weight is gone); see what it cost; then ask for the training block, browse it, save it, train from it and watch adherence; accept or discard a deload. Why here: right after T-165, before the weekly report (T-166) and the history (T-167) read the plan and its weeks. T-160…T-164 merge with no checkpoint after them.
- [x] T-160…T-165 merged, CI green, `deploy-supabase` applied their migrations and the `coach` function (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try (each plan or block request is a paid call of about 0,1-0,4 €) — approved by the user, 2026-10-30; feedback logged in the E13 trainer backlog (RPE per exercise; notes repeating instructions, bug).
  1. Nutrition plan: in Trainer start a plan: progress shows while it is prepared, then calories, protein/carbs/fat, weekly rate, the protein guidance (about 0,4 g per kg per meal, 4 meals or more) and the outcomes, each with its reason and deadline (`?`).
  2. Rules and intentions: with "Perder grasa" the calories are below your expenditure, protein at least 1,6 g/kg and fat at least 20 % (if you hit the floor, the plan says why the rate is slower); with "Ganar musculatura" a small surplus; with "Ganar fuerza" possibly e1RM outcomes and no weight outcome.
  3. Confirm and accept: with a provisional value the accept action is disabled and says why; "Confirmar" the card, accept: Diario's calorie and macro targets are the plan's; a manual change in Preferencias saves and the plan shows its nutrition as edited by you.
  4. No target numbers: no screen asks for a target weight (Peso, onboarding data, Preferencias); Peso shows the plan's weight outcome when it has one.
  5. Cost: Trainer ⋮ → "Uso de IA" shows this month's usage and the 2 € limit, updated after your requests.
  6. Block preview: with "Gimnasio" ask for the block: four weeks ("Semana 1"…"Semana 4") with routines, weekly sets per muscle group, each muscle group at least twice a week with 3 days (or the frequency the coach chose when days are empty); an exercise's `?` shows its reason.
  7. Structure and limits: big multi-joint lifts first with 2-3 min rest, isolation 1-2 min; with "Ganar fuerza" the reps change between the days of a week; with 1 day one full-body routine; with "Mancuernas" + "Peso corporal" only those exercises; each exercise shows its rep range.
  8. Save or leave, then train: leave the preview: no new routine; reopen it and tap "Guardar bloque": one folder with the block's routines in Entreno (with the rep ranges in the routine editor); train one: the Trainer week shows it done and the week's percentage; skip one: next week stays as planned, nothing moved.
  9. Deload: if your data shows no main-lift e1RM improvement in three weeks a deload card appears (otherwise host tests cover it): "Descartar" leaves the week; "Aceptar" marks next week as deload and its exercises suggest the same weights with 40 % fewer working sets; routines unchanged.
  - Leave the app and come back while the plan is being prepared, and once on the block preview in week 3 with an exercise reason open: the same tab, screen and state are restored.
  - Out of scope: the in-block calorie change and the limit-reached message (host tests: two weeks of data or 2 € of usage); the weekly report, the history and the chat (E13-C); outcome measurement after four weeks (E13-C).

---

## E13 replan: the trainer journey (epic-planner, 2026-11-01; proposed, needs the user's OK before its PR merges)

**Why:** the user (2026-11-01): E13 is not going in the right direction; the app must replace a real trainer he can trust long-term, and the follow-up (how the plan adapts over months and years) is the big gap, phases over the year the biggest. Design approved by the user and written by the fitness-scientist: `07` §1-§4, §7, §8, §12 (sources §10 51-66). Decisions: ADR-86 (intake, phases, review, breaks), ADR-87 (free text, grounding), ADR-88 ("Entrenador": automatic changes with "Deshacer"; amends ADR-76), ADR-89 (no generic disclaimers, persistent-discomfort note, injury follow-up; amends ADR-81, ADR-85; reverses E13 Q29). User decisions E13 Q32-Q43; open questions E13 Q44-Q49 carry a recommendation the tasks use (not blocking). Stories: US-81…US-92 (new; US-91/US-92 from `07` §12.8 "Life changes", 2026-11-02), US-68, US-69, US-70, US-71, US-72, US-73, US-74, US-75, US-77, US-79 amended (epic file).
**Re-assessed pending tasks:** T-166 (report) **kept and amended** (3 parts, notes, automatic changes, missing-data line, health note, grounding); runs after Checkpoint E13-F because it lists the automatic changes. T-167 (history) **kept and amended** (automatic changes, undos, reviews, phases; deletion scope E13 Q48). T-168 (evaluation) **kept and amended** (intake, phase, review, injury and grounding cases). T-169 (chat) **kept and amended** (grounding, free text, 3 € cap). **T-173 folded** (2026-11-02, user: no S tasks): its items moved to T-188 and T-194. T-170 **kept as the PM closure step** after Checkpoint E13-C (not a developer task).
**Fixes of merged behaviour that is now wrong (folded into the new tasks, no separate S tasks):** experience = beginner without history and "no free text" in `coach_summary` (T-157) → T-185; the cardio help sheet's medical line (T-181) → T-188; week = calendar week from `block_start` (T-165) → T-189; progression only as a suggestion (T-159) → T-192; calorie change and deload need "Aceptar" (T-161/T-162, T-165) → T-193.
**Audit of the merged E13 code against the new `07` (PM, 2026-11-01, read-only, file:line per mismatch; kept outside the repo):** every mismatch is folded into the task that touches the same code, no separate fix task: T-157 experience, maintenance prior, situation, summary free text → T-185; T-157 partial-day rule (40 % of the median, now excluded from every count) and the weekly clean-data gate → T-193; T-157/E12 "open" discomfort window vs the 14-day note and the review → T-194; T-159 auto-apply (increment computed on the server, today client-side in `ProgressionWeight.kt`) and the 14-day gap → T-192; T-159 graded return → T-194; T-161 `DEFICIT_CAP` 1000 → 500, phases, cut rate, diet break → T-187; T-161 calorie change auto-apply in the safe band and the −1 000/+750 bounds → T-193; T-162/T-165 "Aceptar"/"Descartar" strings for calorie change and deload → T-193; T-163 beginner 8 sets (→ 10) and minimal phase → T-187 (strength 3-5/5-6/6-8 now matches `07`); T-163 carry-over of sets, "Priorizar"/"Mantener", adherence 80 % "high", block prompt context (summary + free text) and the grounding validator → T-190; T-163 discomfort and injured-area candidates → T-194; T-165 paused weeks → T-189; T-165 auto, scheduled, cancellable and user-started deload → T-193; T-181 medical line → T-188; T-175/T-176 intake rows in "Antes de empezar" → T-185 (inputs RPC) and T-186 (rows); generic disclaimers outside E13 (`strings.xml` `metric_disclaimer` and the underweight "consulta con un profesional", T-081) → T-188's disclaimer sweep (`07` §7: "no generic disclaimers anywhere in the app"; alone it would be an S task, so it rides with the same sweep).
**Eight `07` points resolved by the fitness-scientist (2026-11-01/02, `07` §2-§4, §7, §9; `04` amended) and written into the tasks below:** (a) a level 4-5 discomfort stays **unresolved**, whatever its age, until "Ya no me molesta" or graded-return step 3; the 7-day "open" window governs only the load block of levels 1-3 (T-194); (b) experience never changes volume: 10 weekly sets for every class (beginner 8 dropped; days by experience unchanged, `04` §9.1) (T-187); (c) cardio follows the intention in every phase, except minimal: 100 min, at most 30 per routine, back to 150 after 4 minimal blocks; unchanged in deload and diet-break weeks; only a new block, an accepted phase change or the user change the minutes (T-187); (d) partial day = under 40 % of the median of the 28-day logged days, excluded from the anchor and from every count (70 % reliability, weekly gate) (T-193); (e) reference expenditure = the one in use at the change; regain never below it; maintenance, recomposition and minimal within ±0.25 %/week of it (a decrease is allowed down to that range) (T-193); (f) the server computes the step from the user's plates and handle and writes it to the planned sets of every block routine holding that exercise with the same rep range, after ADR-70's update; on a raise the reps go to the bottom of the range; "Deshacer" restores the previous planned weight and reps (T-192); (g) one deload per block, always week 4, counted in training weeks ("Llevas 7 semanas entrenando sin descarga."); it may coincide with a diet-break week; the review runs after week 4 and its e1RM trend uses weeks 1-3 after a deload (T-190, T-193); (h) an undone calorie change does not count towards the ±10 % band and restarts the 14-day clock; after two undos in one block no more automatic calorie changes until the review (T-193). "Ganar fuerza" keeps 3-5 / 5-6 / 6-8 (`07` §3 now says so; no code change). Generic disclaimers app-wide (`07` §7): folded into **T-188** (user 2026-11-02: no S tasks).
**Life changes (`07` §12.8, user-approved 2026-11-01):** new stories US-91 ("Revisar el bloque": "Adaptar el bloque" / "Bloque nuevo" / "Solo unos días") and US-92 (the other life events: intention change, illness, new injury mid-block, goal reached, logging nothing, disliked exercise, turning 65) → tasks **T-196** (backend, L) and **T-197** (UI, L), after Checkpoint E13-F, tested at Checkpoint E13-C; row 10's "no weekly report for a week with no session, weigh-in or diary day" goes into T-166; row 7's level 4-5 swap is **offered** (a rule on the user's tap), which replaces the earlier E13 Q45 recommendation of an automatic swap (T-194). **Needs ux-reviewer** (pattern and copy, in the plan pass of the owning task): "Revisar el bloque" and its three choices (T-197), "Solo unos días" with its end date (T-197), and the level 4-5 swap offer (T-194).
**Order (value for the follow-up first, then dependencies):** T-185 → T-186 (getting to know the user: the regain default and the experience depend on it) → T-187 → T-188 (phases, disclaimers, Perfil/Peso fixes) → **Checkpoint E13-D** (feedback 2026-11-02: T-198 → T-200 → T-199, then E13-D re-presented) → T-189 (weeks follow the training: the end of the block and the deload depend on it) → T-190 → T-191 (end-of-block review, next block, phase changes, priorities) → **Checkpoint E13-E** → T-192 → T-193 (automatic changes) → T-194 (injury) → **Checkpoint E13-F** → T-196 → T-197 (life changes: they reuse the early review, `coach_changes` and the swap rules) → T-166 → T-167 → T-168 → T-169 → **Checkpoint E13-C** (last) → T-170 (PM closure step).
**Sizing:** one task per story except: US-81 + US-89 (same domain: what the coach knows about the user; ~30 files together) split backend T-185 → UI T-186; US-82 split backend T-187 (L) → UI T-188 (L: with the medical-line removal, the app-wide disclaimer sweep and the Perfil/Peso fixes, each S alone); US-83 + US-84 (same review screen and rules; ~34 files) split backend T-190 → UI T-191; US-85 splits by domain into T-192 (live session and routines) and T-193 (Trainer root; with US-86 and the US-75 amendment, same evaluation functions); US-73 amended + the US-88 block pause is T-189 (M; merging it into T-190 would pass ~25 files); US-87 + US-90 merge into T-194 (same discomfort row; US-90 alone is S). US-88 has no task of its own: its scenarios ride with T-185, T-187/T-188, T-189, T-190 and T-192. US-91 + US-92 (life changes, `07` §12.8; ~30 files together) split backend T-196 → UI T-197. **No S task** (user, 2026-11-02): T-195 and T-173 are folded; T-170 is the PM closure step.
**Shared UI built once (R-39):** `CoachChangeRow` (what changed, reason, "Deshacer" / "Cancelar descarga"; new glossary entry "Deshacer" for undoing an automatic coach change, R-20 unchanged for deletion) built in T-192 and reused by T-193, T-194; the phase row (label, reason, `?`) built in T-188 and reused by T-191; the review sections reuse `TrendPill` / `WeeklyRatePill` and the week rows of `PlanWeeksSection`; the phase proposal reuses `CoachProposalCard` with "Aceptar" / "Seguir como estoy" (glossary entry); the "Provisional" `StatusChip` (T-158) carries the "Según lo que nos contaste" text; streamed progress (T-162) for the extraction and the review.
**Cost (E13 Q43, `07` §11):** *(2026-11-02, T-198: the intake extraction is removed, so its cost line no longer applies; the review call and the free-text block stay.)* new paid calls: the intake extraction (Sonnet 5.5, about 0,01-0,03 € per edit, rare) and one review call per block (about 0,05 €); the free-text block adds about 600 input tokens per call (≈ 0,001-0,003 €). Heavy user ≈ 3,1 €/month estimated; the user raised the per-user cap to **3 €** on 2026-11-02 (ADR-90; the 5 € total stays), so the cap still stops a heavy user's chat first near the end of the month; the code change rides with T-185. T-185 and T-190 add their rows to the `07` §11 cost table (an implementation figure, not a science rule).
**Paid calls:** CI and `deno test` never call the API; the intake extraction and the review are tested by the user at the checkpoints (approved paid calls). *(The intake extraction is removed by T-198, 2026-11-02.)*

---

## T-185: "Para conocerte" and the free-text block: backend (US-81, US-89; US-68 amended; US-88 returning) · L

> **Superseded in part (user, Checkpoint E13-D feedback, 2026-11-02; done by T-198, history kept below):** the `intake_extract` kind, `injured_area`, `pre_break_bests` and `known_cut_kcal` are removed (they changed no figure in this release; R-53); `situation`, stored and derived (28 days → `returning_break`), is removed with "Recuperar" (user, 2026-11-02; deferred to Phase 3); the free-text block, `training_years` with experience = max, the known-maintenance prior and handover, the exclusions and the 3 € cap stay as built.

**Story:** US-81, US-89 (data side), US-68 amended, US-88 "A long break makes me returning" · **Flow:** backend + core · **Size:** L · **Scenarios:** US-81 The coach never guesses; Only valid values; What I said is provisional (data); My past bests never set my loads; Cutting calories are only context; Editing it later (data) · US-89 What the coach reads (data); Leaving a line out (data); Size limits; What is never sent; Words never change numbers · US-68 Experience is the higher of what I said and my history; My history takes over when it is higher; My known maintenance is used until my data is enough · US-88 A long break makes me "returning"

**Description:** (1) **Migration:** `coach_intake` (one row per user, cascade; training age (years, numeric), `situation` enum `first_time | continuous | returning_break | returning_injury`, injured area (the planner proposes a short area enum, e.g. shoulder, elbow, wrist, lower back, hip, knee, ankle, other, recorded in `03`; ux-reviewer finalises the Spanish labels in T-186), pre-break bests jsonb `[{exercise_id, weight_kg, reps}]`, `known_maintenance_kcal`, `known_cut_kcal`, `free_text` ≤ 500 characters, `saved_at`), owner RLS enabled and forced, pgTAP; `coach_free_text_exclusions` (user, source kind `intake | check_in | discomfort | review`, source id), owner RLS. (2) **Derived values (`coach_compute`, `04` §7.5 amended, `07` §2):** experience = max(history class, self-reported class: < 6 months beginner, 6 months to < 2 years intermediate, ≥ 2 years advanced) with `source = intake` while the self-report wins; a new `situation` value = the intake answer, or `returning_break` after 28 days without a completed session (ends when the regain phase ends, T-190); expenditure prior = the known maintenance instead of BMR × factor while the adaptive expenditure does not qualify (`source = intake`, provisional), with a one-time `handover` flag when the measured value takes over; cutting calories are returned as context only and enter no calculation. (3) **`coach_summary` free-text block** ("Lo que el entrenador sabe de ti", `04` §7.8 amended): the intake text and the check-in notes and discomfort notes of the last 8 weeks, newest first, verbatim, cut at 2 000 bytes, minus exclusions (T-190 adds the review answers through the same builder); set and routine notes, food names and custom exercise names never; the 16 384-byte cap still holds (the planner re-budgets and tests the worst case). RPCs `coach_free_text_lines(p_today)` (each line with origin, date, text and `sent | excluded | over_limit`) and `set_free_text_line_excluded(p_kind, p_id, p_excluded)`. (4) **Function kind `intake_extract`** (Sonnet 5.5, budget-checked, ADR-77; streamed per the T-162 shared state): input = the free text and any fields already filled; JSON-schema output; server validator: allowed enums, plausible ranges (planner records them in `04`), lifts only from a server-built list of catalogue main lifts, a field the text does not mention stays null (never guessed); retry once, then `coach_invalid_output`; nothing is stored by the call. RPC `save_coach_intake(p jsonb)` re-validates and stores. (5) **Core:** `CoachIntake`, `Situation`, `InjuredArea`, `FreeTextLine` models; `CoachRepository` (get/save intake, extract with streamed progress, list lines, exclude); DTO mapping tolerant of unknown values. (6) **Docs:** `03` (tables, RPCs, kind), `04` §7.5, §7.8 detail, `02` (new kind), `07` §11 cost row for the extraction.
**Per-user AI cap 3 € (ADR-90, user 2026-11-02; folded here because this task already changes the `coach` function and the shared coach models):** `supabase/functions/coach/budget.ts:2` `USER_LIMIT_EUR = 2` → 3 and its mirror `USER_MONTHLY_LIMIT_EUR` in `shared/src/commonMain/kotlin/io/github/lopezjavier/appfitness/domain/model/CoachUsage.kt` (read by `composeApp/…/feature/trainer/CoachUsageScreen.kt`, so "Uso de IA" shows "de 3,00 €" with no screen change); budget `deno test` boundaries (2,99 / 3,00 €) and the Kotlin usage tests updated; `02-architecture.md:71` and `03-data-model.md:87` say 3 EUR.
**Audit fixes folded in (merged code now wrong or missing):** `coach_experience` in `20261205100000_coach_derived_values.sql` (history class only) → max with the intake class; `coach_compute` in `20261214100000_coach_realistic_expenditure.sql` (BMR × factor prior) → known-maintenance prior and handover; `coach_summary` (`20261217100100_coach_summary_cardio.sql`, no free text, no situation or phase) → free-text block, `situation`, and the plan's phase once T-187 adds it (leave the key); `coach_plan_inputs` (`20261216100200_coach_plan_inputs_cardio.sql`) gains intake entries (`situation`, `training_age`, `injured_area`, never blocking, `fix_target = intake`) so "Antes de empezar" shows them (T-186). Extracted fields feed experience and the expenditure prior only after the user confirms them (gap (8) of the audit; free text itself never changes a figure).

**Acceptance criteria:**
- [x] `coach_plan_inputs('plan')` lists the intake entries with their status; none blocks.
- [x] A user at 2,99 € can still call the model; at 3,00 € gets `coach_limit_reached`; the 5 € total is unchanged; "Uso de IA" reads "… de 3,00 €".
- [x] Self-reported 3 years with 10 app sessions → experience advanced, `source = intake`, provisional; 4 months with an intermediate history → intermediate from history; no intake and no history → beginner (unchanged).
- [x] 28 days without a completed session → `situation = returning_break`; 27 days → unchanged.
- [x] Known maintenance 2 400 with an unreliable diary → total expenditure 2 400, `source = intake`, provisional; once the adaptive expenditure qualifies it replaces it and `handover` is true once; known cutting calories change no figure.
- [x] The extraction of the US-81 example text returns 4 years, `returning_injury`, shoulder, "Press banca" 90 × 5 and null calories; an unknown situation or a non-catalogue lift comes back null; invalid JSON gets one retry, then a clean error; nothing is stored until `save_coach_intake`.
- [x] The summary carries the free-text block, newest first, verbatim, ≤ 2 000 bytes, without excluded lines, set or routine notes or food names; the whole summary stays ≤ 16 384 bytes in the worst case.
- [x] `coach_free_text_lines` marks each line `sent`, `excluded` or `over_limit`; excluding a line removes it from the next summary and keeps the original note.
- [x] pgTAP: RLS enabled and forced, isolation, cascade on account deletion (`delete_account_cascade.test.sql`), every rule above; `deno test` for the extraction validator with a fake client.

**Host tests:** pgTAP `coach_intake.test.sql`, `coach_free_text.test.sql`, `coach_derived_values.test.sql` (experience max, situation 27/28 days, maintenance prior and handover), `coach_summary.test.sql` (block, cap, exclusions); `deno test` `intakeExtract.test.ts` (schema, ranges, catalogue lifts, never guessed, retry-once, budget refusal); Kotlin DTO and repository mapping tests.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test --allow-all supabase/functions`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (before T-186).

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5. About 18 files (SQL and function heavy; the planner may run backend and core as developer slices).
**Dependencies:** the user's OK on this replan (ADR-86, ADR-87); T-183 merged (done).
**Files likely touched:** `supabase/migrations/<ts>_coach_intake.sql`, `supabase/migrations/<ts>_coach_free_text.sql`, `supabase/tests/{coach_intake,coach_free_text,coach_derived_values,coach_summary,delete_account_cascade}.test.sql`, `supabase/functions/coach/{intakeExtract,validators,schemas,router,budget}.ts` (+ tests), `shared/…/domain/model/{CoachIntake,FreeTextLine,CoachUsage}.kt`, `shared/…/domain/repository/CoachRepository.kt`, `shared/…/data/{remote/CoachIntakeDto,repository/SupabaseCoachRepository}.kt` (+ tests), `planning/{02-architecture,03-data-model,04-business-rules,07-coach-design}.md` (§11 only)
**Estimated scope:** L

---

## T-186: "Para conocerte" screen and "Lo que el entrenador sabe de ti" (US-81, US-89 UI; US-68 display) · L

> **Superseded in part (user, Checkpoint E13-D feedback, 2026-11-02; done by T-198, history kept below):** "Para conocerte" no longer opens on its own before the first plan and "Ahora no" goes (E13 Q46 superseded); "Rellenar con mi texto" (the extraction) and its "Rellenado desde tu texto" markers, "Marcas de antes de parar", "Zona con molestia", "Situación" and the cutting-calories field are removed; the "Antes de empezar" intake rows ("Tiempo entrenando", "Calorías de mantenimiento", "Para conocerte") no longer all open the same full editor: each opens its own focused editor and "Para conocerte" is only the free text (R-55). "Lo que el entrenador sabe de ti", the provisional texts in "Tus datos", the handover note and the `StreamProgressState` sweep stay as built.

**Story:** US-81, US-89 (UI), US-68 amended (display) · **Flow:** core + UI · **Size:** L · **Scenarios:** US-81 every scenario (UI); US-89 What the coach reads; Leaving a line out; Size limits (marking) · US-68 Experience is the higher…; My known maintenance is used until my data is enough (display, handover note)

**Description:** (1) **Core:** `IntakeViewModel` (load, edit fields, extract with streamed progress, confirm field by field, save, skip, offline, budget reached = fill by hand), `FreeTextLinesViewModel` (load, toggle a line), `TrainerViewModel`/`BeforeStartViewModel` route the first "Crear plan" to the intake when none is saved (E13 Q46 recommendation). (2) **UI — templates and patterns:** "Para conocerte" is a full-screen **editor** (`AppEditorScreen`, pinned "Guardar", R-10/R-11, discard dialog on back with changes, R-14) opened before "Antes de empezar" on the first "Crear plan" (secondary "Ahora no" skips to "Antes de empezar") and from a `NavigationRow` "Para conocerte" in "Tus datos" (R-03/R-04). Fields: time lifting consistently (R-47 stepper or `OptionPickerSheet` bands, ux-reviewer picks), "Situación" and the injured area as `OptionPickerSheet` rows (R-12, apply on tap), best lifts as `ExerciseRow`s with weight × reps and one `AddItemButton` "Añadir marca" at the end (R-19; the existing exercise picker), maintenance and cutting calories as number fields, and the free text "Cuéntanos lo que quieras sobre tu entrenamiento" (`AppTextField`, multi-line, 500 counter). "Continuar" under the text runs the extraction (shared streamed-progress state, T-162) and fills the form; each filled field is marked until the user touches it (text + marker, never colour alone, R-41; ux-reviewer writes the copy); empty fields stay empty; "Guardar" stores. **"Tus datos"** (T-176): values from the intake show the "Provisional" `StatusChip` with the supporting text "Según lo que nos contaste (provisional). Lo cambiaremos por tus datos reales cuando haya suficientes."; a "Situación" row; the handover sentence once as an `InlineNote` ("Ahora usamos tus datos reales: tu gasto medido es de 2.450 kcal."). **"Antes de empezar"** (plan mode, audit fix of T-175/T-176): summary and detail rows for "Situación", training age and injured area from the intake (`coach_plan_inputs`, T-185), each opening "Para conocerte" (E13 Q17 detail-row pattern), never a flag. **"Lo que el entrenador sabe de ti"**: an `AppScreen` from a Trainer root `NavigationRow` (ux-reviewer confirms the place: root row or inside "Tus datos"), one section per origin, each line an `AppListRow` with the verbatim text and its date and an `AppSwitch` "Enviar al entrenador"; lines over the 2 000-byte limit say so in text; empty state per R-30; a short intro line says this is exactly what the coach reads. (3) **Sweep (R-39, E9 refinement table row E, owner moved here from the folded T-173):** the ad-hoc AI-wait spinners in `PhotoSteps.kt` (food photo analysis) and `AmountPhotoFlow.kt` ("Estimando…") migrate to `StreamProgressState`, the state this task reuses for the extraction; `05` §10 glossary ("Para conocerte", "Situación", "Según lo que nos contaste", "Lo que el entrenador sabe de ti", "Ahora no"), §6.2 Trainer root if a row is added.

**Acceptance criteria:**
- [x] With no intake, the first "Crear plan" opens "Para conocerte"; "Ahora no" goes to "Antes de empezar"; once saved, "Crear plan" goes straight to "Antes de empezar".
- [x] Writing the US-81 example and "Continuar" fills the four fields and leaves the calories empty; a corrected field keeps the correction; "Guardar" stores; back with changes asks to discard.
- [x] Budget reached or offline: the extraction shows the US-76 limit message or the offline hint and the fields stay editable by hand.
- [x] "Tus datos" shows the intake values with "Según lo que nos contaste (provisional)…", the "Situación" row and the "Para conocerte" row; the handover note appears once.
- [x] "Antes de empezar" (plan) shows the situation, training age and injured area from the intake; tapping one opens "Para conocerte" and the value refreshes on return.
- [x] "Lo que el entrenador sabe de ti" lists the lines exactly as sent; turning one off removes it from what is sent and survives reopening; over-limit lines are marked in text.
- [x] The editor (with a half-written text and an extracted form), the lines screen and the open pickers survive background and process death (ADR-40).
- [x] The food-photo analysis and "Estimando…" waits use `StreamProgressState`; no ad-hoc `CircularProgressIndicator` remains for an AI wait.

**Host tests:** `IntakeViewModel` tests (first-plan routing, skip, extract success / invalid / limit / offline, confirm and correct fields, save, discard); `FreeTextLinesViewModel` tests; Robolectric (fields and pickers, marker on extracted fields, "Añadir marca", counter, discard dialog, provisional texts in "Tus datos", handover note, line switches and over-limit text); restoration tests.

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green. No paid call in CI.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 16 files.
**Dependencies:** T-185 merged and deployed; E13 Q46 (recommendation used; the user may override at the plan review).
**Files likely touched:** `feature/trainer/{IntakeScreen,IntakeViewModel,IntakeUiState,FreeTextLinesScreen,FreeTextLinesViewModel}.kt`, `feature/trainer/{BeforeStartScreen,BeforeStartViewModel,TrainerScreen,TrainerViewModel}.kt` ("Tus datos" rows, routing), `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** L

---

## T-187: Phases: calories and training by phase (US-82 backend; US-69 deficit cap; US-88 minimal) · L

> **Superseded in part (user, 2026-11-02; history kept below):** the regain phase ("Recuperar": returning → regain, its calories, volume, cardio and invariant) is removed by T-198 (no `situation`) and T-200 (phase, rules, backfill to maintenance), deferred to Phase 3. Every other phase rule stays as built.

**Story:** US-82 (data side), US-69 amended "The deficit is at most 500 kcal", US-88 (minimal) · **Flow:** backend + core · **Size:** L · **Scenarios:** First phase from the intention; Returning after a break starts with "Recuperar" at maintenance; Regain with a muscle goal; Choosing how fast to cut (data); An optional diet break (data); The minimal phase for busy times (data); Older users in the minimal phase; The phase does not change inside a block · US-69 The deficit is at most 500 kcal

**Description:** (1) **Migration:** `coach_plans.phase` enum (`cut`, `gain`, `maintenance`, `recomposition`, `regain`, `minimal`) and `phase_options` jsonb (`cut_rate_pct` 0.5-1.0, `diet_break` boolean); existing plans backfilled from their intention (fat loss → cut, muscle → gain, recomposition, maintenance and strength → maintenance); pgTAP. (2) **First phase (`07` §12.3, E13 Q34):** intention → phase per the US-82 table; `situation` returning (T-185) → `regain`; stored on the plan with a reason code. (3) **Calories by phase (`nutritionRules.ts`, `07` §4):** cut at the chosen rate (default 0.5 %), deficit capped at 500 kcal/day (then the slower rate is kept, `limited_by = deficit_cap`), floor (ADR-25); gain by experience as today; maintenance, recomposition and minimal at rate 0; regain at rate 0, or +0.25 %/week for "Ganar musculatura" / "Ganar fuerza", never a deficit; diet break: week 4 of each block at maintenance (the deterministic coach run on Trainer open switches `nutrition_goals` at the start of plan week 4 and back at the next block, planner records the mechanism; only deficit weeks count towards the cut length, stored for T-190). `assertNutritionInvariants` per phase (cap; regain never below the expenditure in use; maintenance, recomposition and minimal within ±0.25 %/week of it, `07` §4 "Reference expenditure"). A non-model request (or RPC) recomputes the nutrition when the user changes `cut_rate_pct` or `diet_break` on a proposed plan, with no paid call. (4) **Training by phase (`blockRules.ts`, `04` §9.3 amended):** first block 10 sets per group for every experience class (beginner 8 dropped, `04` §9.3; days by experience unchanged, §9.1); cut keeps the previous block's sets per group (no increase); regain 10 sets per group, no increase; minimal: 1 full-body session a week (2 from 65 years), the previous block's main lifts only, 1 set each (2 from 65), same rep ranges; a `minimal` request flag on `plan_block` ("Tengo poco tiempo"); cardio by phase (`07` §3 "Cardio by phase", `04` §9.11 amended): the intention's target in every phase ("Perder grasa" keeps its 150-300 band, regain included), minimal 100 min with at most 30 per routine (1 routine = 30 + 70 "por tu cuenta") and back to 150 after 4 minimal blocks; unchanged in deload and diet-break weeks; never changed on its own. Validators per phase. (5) **Core:** `Phase`, phase options, plan DTO fields; repository call for the options. (6) **Docs:** `03`, `04` §8 (phase calories with worked examples: cut capped at 500, regain +0.25 %, diet-break week), §9.3 detail, `07` untouched (rules already there).
**Audit fixes folded in:** `nutritionRules.ts:10` `DEFICIT_CAP = 1000` → 500 for every loss plan (today 0.5 % already passes 500 above ~91 kg); the plan request input (`planNutrition.ts`, `{today}` only) gains the phase options; `blockRules.ts:59, 312-314` beginner 8 weekly sets → 10 for every class (experience never changes volume, `07` §2; the experience-chosen days of `04` §9.1 stay); `blockRules.ts:70-74` strength ranges 3-5 / 5-6 / 6-8 are now what `07` §3 says (no change; the audit line is closed); the cardio split (`blockRules.ts:22-53`) gains the minimal-phase target and 30-min cap above.

**Acceptance criteria:**
- [x] Each intention without a returning situation gives the US-82 phase; returning gives `regain`; the phase and its reason code are stored.
- [x] Cut at 0.5 % whose deficit would be 650 → 500 with `limited_by = deficit_cap`; the floor still wins; rate 1 % accepted, 1.2 % rejected.
- [x] Regain with "Perder grasa" → calories = expenditure; with "Ganar musculatura" → +0.25 %/week; a regain plan with a deficit is rejected by the invariants.
- [x] Diet break on → weeks 1-3 deficit and week 4 maintenance in the plan JSON; Diario's target switches at plan week 4 on the coach run.
- [x] Changing the cut rate or the diet break on a proposed plan recomputes the nutrition with no model call and no usage row.
- [x] Minimal: one full-body session (two from 65) with one set (two from 65) of each previous main lift, same rep ranges, plus the cardio slot; validators reject anything else.
- [x] Cut keeps the previous block's sets per group; regain 10 per group; existing plans read back with their backfilled phase; every T-161/T-163/T-180/T-182 regression still passes (the deficit-cap and strength-range expectations updated).
- [x] No loss plan has a deficit above 500 kcal/day (`DEFICIT_CAP` 500); a first block plans 10 weekly sets per group for a beginner too; a maintenance plan stays within ±0.25 %/week of the expenditure in use.
- [x] Minimal: 100 min of cardio, at most 30 per routine (1 routine → 30 + 70 "por tu cuenta"); after 4 minimal blocks the proposal is 150; regain with "Perder grasa" keeps the 150-300 band; deload and diet-break weeks keep the minutes.

**Host tests:** `deno test` (phase selection table, returning, cut cap and floor order, regain rates, diet-break weeks, recompute without a model call, minimal block by age, cut and regain volume, invariants pass and fail, regressions); pgTAP (`phase` column and backfill, options RPC bounds, isolation).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (before T-188).

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5. About 14 files.
**Dependencies:** T-186 merged (T-185's `situation` deployed).
**Files likely touched:** `supabase/migrations/<ts>_coach_phase.sql`, `supabase/tests/{coach_plans,coach_phase}.test.sql`, `supabase/functions/coach/{nutritionRules,blockRules,planNutrition,planBlock,validators,blockValidators,schemas}.ts` (+ tests), `shared/…/domain/model/{CoachPlan,Phase}.kt`, `shared/…/data/remote/CoachDto.kt`, coach repository, `planning/{03-data-model,04-business-rules}.md`
**Estimated scope:** L

---

## T-188: Phases in the plan and "Tu plan"; no generic disclaimers app-wide; Perfil and Peso fixes (US-82 UI; US-79 amended; US-90 app-wide; former T-195 and two T-173 items) · L

> **Superseded in part (user, 2026-11-02):** the "Recuperar" label, reason and `?` copy are removed by T-200 (deferred to Phase 3); the rest stays as built.

**Story:** US-82 (UI), US-79 amended (ADR-89), US-90 "No generic warnings" (app-wide) · **Flow:** core + UI · **Size:** L (was M; it absorbs the former S tasks T-195 and two T-173 items on the same Perfil and Peso screens, user rule 2026-11-02: no S tasks) · **Scenarios:** US-82 The phase is part of the plan; Returning after a break… (copy); Choosing how fast to cut; An optional diet break; The minimal phase for busy times (entry and copy) · US-79 Targets next to the calories (no medical line) · US-90 No generic warnings

**Description:** (1) **Core:** `PlanProposalViewModel` (phase, options change → recompute with progress, no paid call), `TrainerViewModel` ("Tu plan" phase line), `BeforeStartViewModel` (block mode "Tengo poco tiempo"). (2) **UI — templates and patterns:** plan proposal (`AppScreen`, T-162): a **phase row** at the top (phase label, one-sentence reason, `HelpButton` Info sheet with what the phase does to calories and training, `07` §12.7 copy; build the row here, reused by T-191); in a cut, a "Ritmo" row opening an `OptionPickerSheet` (0,5 / 0,75 / 1 % por semana; R-12 apply on tap; values from `04` §8 via T-187) and an `AppSwitch` row "Con semana de descanso" with its help line; the calories update in place. "Tu plan" card (T-176): one phase line. "Antes de empezar" in block mode: a secondary action "Tengo poco tiempo" (ux-reviewer confirms the pattern; R-09 keeps "Crear bloque" the primary) that requests the minimal block, and the preview shows the "Mínimo" phase copy. (3) **Medical line removed (ADR-89, E13 Q39):** drop the last line of `plan_activity_help` (`strings.xml:1877`, `ActivityUi.kt:25, 58, 66`) and its string; sweep `strings.xml` for any other generic health disclaimer ("médico", "profesional sanitario", "consulta") and remove it (the persistent-discomfort note of T-194 is the only health text). **App-wide (former T-195; `07` §7: a health line is allowed only when it comes from the user's own data and goes with a decision the app takes):** remove `metric_disclaimer` (`strings.xml:1264`, T-081 body metrics) and the underweight "consulta con un profesional" line (`strings.xml:1275`, used in `GoalSheets.kt:41`, or wherever it lives after T-162) with their uses and tests; the figures they qualified stay; one app-wide string-sweep test fails on a generic disclaimer ("consulta con", "profesional sanitario", "tu médico") outside the allow-listed persistent-discomfort note (T-194); `05` §8 rule line "No generic health disclaimers". **Perfil and Peso fixes (former T-173 items, user 2026-10-30, same screens as the sweep):** (a) Perfil → Preferencias: remove the "Cuenta" section and move its "Cerrar sesión" and "Borrar cuenta" into Preferencias, keeping their confirmations and behaviour (`05` §8-§9 if the placement is a rule; tests and help swept); (b) the weight reminder "No te has pesado en N días" opens "Peso" only, never the manual weigh-in form (weights come from Health Connect via "Sincronizar"; the form opens only from its own button). (4) `05` §10 glossary: the six phase labels ("Definición", "Volumen", "Mantenimiento", "Recomposición", "Recuperar", "Mínimo"), "Con semana de descanso", "Tengo poco tiempo".

**Acceptance criteria:**
- [x] The proposal and "Tu plan" show the phase with its reason and `?`; a returning user sees "Recuperar" with the `07` §12.7 sentence.
- [x] In a cut, picking 1 % or turning on "Con semana de descanso" updates the calories in place with progress, no new AI cost in "Uso de IA".
- [x] "Tengo poco tiempo" in "Antes de empezar" (block) produces a "Mínimo" block preview with one session.
- [x] The cardio `?` has no medical line; `metric_disclaimer` and the underweight line are gone from the body-metrics and weight screens (the figures still show); the app-wide sweep test passes and fails on a planted disclaimer fixture.
- [x] Preferencias has "Cerrar sesión" and "Borrar cuenta" with their confirmations; no "Cuenta" section remains.
- [x] Tapping the weight reminder opens "Peso" and never the manual weigh-in form (host test).
- [x] The proposal with an open phase `?`, the rate picker and the switch state survive background and process death (ADR-40).

**Host tests:** ViewModel tests (phase display, rate change, diet-break toggle, recompute progress, minimal request); Robolectric (phase row and help, rate picker, switch, "Tengo poco tiempo", cardio help without the line, "Tu plan" phase line, body metrics and goal sheet without the lines, Preferencias account actions, reminder target); the app-wide disclaimer sweep with its fixture; restoration tests.

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 20 files (the planner may run the trainer UI and the Perfil/Peso part as developer slices of the same PR).
**Dependencies:** T-187 merged and deployed.
**Files likely touched:** `feature/trainer/{PlanProposalScreen,PlanProposalViewModel,PhaseRow,ActivityUi,TrainerScreen,TrainerViewModel,BeforeStartScreen,BeforeStartViewModel}.kt`, the body-metrics screen (T-081) and the weight goal sheet, the Preferencias and Cuenta screens, the reminder routing in "Centro de avisos", strings, `planning/05-design-system.md` §8-§10, tests
**Estimated scope:** L

*T-195 (former S task, generic disclaimers app-wide) was folded into T-188 on 2026-11-02 (user: no S tasks); the ID is not reused.*

---

### Checkpoint E13-D feedback (user, 2026-11-02): a minimal intake, saving fixed, one exercise name (epic-planner, 2026-11-02)

**What the user reported at Checkpoint E13-D (the checkpoint stays open until T-198, T-200 and T-199 merge and it is re-presented):** (1) in "Para conocerte" → "Marcas de antes de parar" an added exercise shows as "Ejercicio", the third time a new UI shows a generic exercise label (check-in records T-155, `block_exercise_fallback`, `intake_best_unknown_exercise`, `exercise_unnamed`): fix the hole, not the instance; (2) "Rellenar con mi texto" goes: nothing is extracted from the text by a model, the user types the fields; (3) "Guardar" in "Para conocerte" shows "No se pudo guardar. Inténtalo de nuevo."; (4) "Situación", "Tiempo entrenando" and "Zona con molestia" in "Antes de empezar" all open the same big screen, and "Para conocerte" opens on its own: one row must open one UI, and "Para conocerte" opens only when tapped; (5) after the PM's audit (below): "keep the free text and remove all this complexity. We have built something we don't understand."
**Audit (PM, verified in code, 2026-11-02):** only `training_years`, `situation` and `known_maintenance_kcal` change a figure today; the free text reaches only the `plan_nutrition` prompt (`planNutrition.ts:222`), not `plan_block`; `known_cut_kcal` feeds nothing (by design); `pre_break_bests` and `injured_area` are stored and read by nothing (planned readers: T-190 regain exit, T-194 train-around, T-196 offer).
**User decision (2026-11-02, after the first version of this refinement): "Recuperar" (the regain phase, "returning after a break") is removed entirely and deferred to Phase 3** ("Notes for later epics"). Everything that existed only for it goes: the "Situación" intake row and `situation` (stored and derived after 28 days), the returning → regain rule, the regain calories, volume, cardio and exit rules, the "Recuperar" label and copy. The other phases stay as built (cut, gain, maintenance, recomposition, minimal). E13 Q50 and Q51 are resolved by the removal.
**Final intake (user, 2026-11-02; a "free text only" variant was considered the same day and withdrawn by the user): three inputs.** "Tiempo entrenando" (`training_years`) **kept**: it sets the experience class (max with the history: gain rate `07` §4, days `04` §9.1) for a user whose app history is shorter than his real one. "Calorías de mantenimiento" (`known_maintenance_kcal`) **kept**: the expenditure prior until the adaptive expenditure qualifies, then the measured value takes over with its handover note (US-68). The free text **kept**: it reaches both `plan_nutrition` and `plan_block` from T-198 (context, never instructions; ADR-87). Each row opens its own small editor; the free text opens only on tap (R-53, R-55). **Removed:** "Situación" and `situation` (only "returning" changed a figure, and regain is gone), cutting calories, "Marcas de antes de parar", "Zona con molestia", the extraction ("Rellenar con mi texto") and the automatic opening.
**Rules (`05` §8, this docs PR):** R-53 a question is asked only if its answer changes a figure or reaches the model in the same release; R-54 one exercise-name resolver, never a generic exercise label (guarded); R-55 one row opens one focused editor, and no editor or intake opens on its own. E13 Q46 superseded by the user (2026-11-02). Glossary (`05` §10) updated.
**Knock-on changes (marked in place, history kept):** T-185/T-186 superseded in part; T-187/T-188 (regain built) superseded in part by T-200; T-190 without any regain trigger or input and with the free text already in the block prompt; T-193 without the regain band; T-194 train-around from logged discomforts only; T-196/T-197 no injured-area trigger; T-166 no pre-break-best line; T-168 no extraction or regain cases; Checkpoints E13-D, E13-E, E13-F and E13-C items rewritten. **Needs the fitness-scientist** (the PM routes it; not blocking T-198/T-200, which only remove inputs and a phase): E13 Q52 (training around an injury without an injured area, `07` §7/§12.1/§12.8 row 7, before T-194) and the `07` clean-up for the removed inputs and regain (`07` §1 intake line: no situation, injury area, bests or cutting calories; §2 "Situation" row, §4 regain calories, §12.1 handover line, §12.2 the "returning lifter" wording, §12.3 regain row and the "situation returning" entries, §12.5 "regain proposed after 28 days", §12.7 "Recuperar" copy, §12.8 row 8 "pre-break bests end regain").
**Sizing:** three tasks, no S. T-198 (L) keeps backend and client in one PR on purpose: its migration drops columns the merged client still sends, so a backend-first split would ship a state where saving fails again; most of its ~24 files are deletions; it also removes `situation`, so from it on no plan is proposed in regain. T-200 (M) then removes the now-unreachable regain phase itself (enum, rules, validators, copy, tests, backfill); kept apart because T-198 plus the phase sweep would pass ~35 files. T-199 (M) is the cross-app name sweep with its guard; it runs last so it never edits files T-198 or T-200 delete.

---

## T-198: A minimal intake: three inputs, one row one editor, the free text in the plan and the block, and the "Para conocerte" save fix (US-81, US-78, US-89, US-88 amended; Checkpoint E13-D feedback 2-5) · L

**Story:** US-81 (rewritten 2026-11-02), US-78 amended, US-89 amended, US-88 amended (no "returning" situation), US-68 (regression) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** US-81 Nothing opens on its own; Each row opens its own editor; Only questions that change something; Writing in my own words; Saving works; What I said is provisional; Editing it later · US-78 The intake rows; The intake rows for the block · US-89 My words reach the plan and the block · US-68 Experience is the higher of what I said and my history; My history takes over when it is higher; My known maintenance is used until my data is enough (regression)

**Description:** (0) **Reproduce the save bug first** (feedback 3): replay the exact JSON the merged client sends from the form the user filled (free text, a best lift added with "Añadir marca", a situation, an injured area, calories) against `save_coach_intake` (`supabase/migrations/20261219100000_coach_user_intake.sql:60-210`) and the client DTO (`shared/…/data/remote/CoachIntakeDto.kt` or wherever T-185 put it). Hypotheses to verify, not assume: the "Añadir marca" picker offers any exercise (custom ones too) while the RPC accepts only catalogue main lifts (`'best exercise is not a catalogue main lift'`, 22023; the same unknown id is what shows "Ejercicio"); a number sent as a decimal or a string (`known_maintenance_kcal` must be a whole number, `training_years` a number); a `situation` / `injured_area` value or a `pre_break_bests` item shape the RPC rejects. Record the root cause in the PR body and pin it with one pgTAP case and one Kotlin serialization test on the exact payload, so a client/RPC mismatch fails CI from now on. (1) **Migration (new file; never edit a merged one):** drop `coach_user_intake.injured_area`, `situation`, `pre_break_bests` and `known_cut_kcal` and the `coach_injured_area` and `coach_situation` types (user decision 2026-11-02: "Recuperar" removed, so `situation` has no reader); the derived situation (`returning_break` after 28 days without a session, `coach_compute`/`coach_summary`, T-185) goes too, while T-189's paused block and T-192's 14-day return-load rule stay (they read session dates, not `situation`); `save_coach_intake` takes only `training_years`, `known_maintenance_kcal` and `free_text` and can save one of them without touching the others (the planner picks a partial-update RPC or a read-modify-write in one call; no lost update when two editors save in a row); `coach_plan_inputs` lists the intake entries "Tiempo entrenando", known maintenance (plan mode) and the free text, never blocking, without the injured area or the situation; every SQL reader of the removed columns updated (`20261219100200_coach_compute_intake.sql`, `20261220100000_coach_phase.sql` first-phase selection, the summary). (2) **`coach` function:** remove the `intake_extract` kind (`intakeExtract.ts` and its test, the router entry, `schemas.ts`, the kind in the budget and usage lists); `planBlock.ts` sends the summary's free-text block ("Lo que el entrenador sabe de ti", exclusions applied, 2 000-byte cap) with the same context-only sentence as `planNutrition.ts:222` (ADR-87: context, never instructions, never a figure), inside the 16 384-byte summary cap and the 150 s limit; the situation input leaves `schemas.ts` and the first-phase selection (`nutritionRules.ts:53` and its SQL mirror), so the first phase comes from the intention only and no plan is proposed in regain from this task on (the dormant regain phase is removed by T-200). (3) **Core:** `CoachIntake` keeps three fields; DTO and repository lose the extraction, the situation and the removed fields (tolerant of an old row during rollout); `IntakeViewModel` becomes the free-text editor only; one small editor state per row (years, maintenance) shared by `BeforeStartViewModel` and the "Tus datos" ViewModel; "Crear plan" always opens "Antes de empezar" (the first-plan routing to the intake and "Ahora no" removed). (4) **UI — templates and patterns (R-53, R-55; ux-reviewer finalises labels and copy):** "Antes de empezar" (plan mode) and "Tus datos" show three rows, each opening **its own** editor and nothing else: "Tiempo entrenando" → an Edit sheet with the R-47 stepper ("Sin indicar" lowest step) and "Guardar"; known maintenance (proposed label "Calorías de mantenimiento") → an Edit sheet with one numeric field (R-25, "Entre 1200 y 6000 kcal"), "Guardar"; "Para conocerte" → `AppEditorScreen` with one multi-line `AppTextField` (500 counter) and its `HelpButton` (R-46: what to write and that the coach reads it as written when it prepares the plan and the block), pinned "Guardar", R-14 discard dialog. Block mode shows "Tiempo entrenando" and "Para conocerte" only (both reach the block). Each row shows its value or "Sin indicar" and the "Provisional" chip where US-68 says so. **Removed (with strings and tests):** "Rellenar con mi texto", "Leyendo lo que nos cuentas…", the "Rellenado desde tu texto. Revísalo." markers, "Marcas de antes de parar" with "Añadir marca" and `intake_best_unknown_exercise`, "Zona con molestia" and its picker, the cutting-calories field, "Ahora no", the "Situación" row in "Antes de empezar" and "Tus datos" with its picker and labels. **"Lo que el entrenador sabe de ti":** its intro says the lines are read when the coach prepares the plan and the block (copy by ux-reviewer). (5) **Sweep (R-39):** grep every reader of the removed pieces (`injured_area`, `situation`, `returning_`, `pre_break`, `known_cut`, `intake_extract`, `IntakeExtract`, `Rellenado`, `intake_best`) across SQL, the function, `shared/` and `composeApp/`; nothing may keep a dead path. (6) **Docs:** `03` (columns, enum, RPC), `04` §7.5/§7.8 (intake inputs, free text in the block request), `02` (kind removed), `07` §11 cost row only (the science text of `07` §1, §2, §7, §12 goes to the fitness-scientist: E13 Q52 and the regain clean-up); `05` §10 labels as finalised by ux-reviewer. ADR-91 (written by the PM in `00-decisions.md`) covers the simplification and the regain removal.

**Acceptance criteria:**
- [x] The root cause of "No se pudo guardar" is recorded in the PR body; the exact payload the merged client produced and the one the new client produces are both covered by pgTAP and a Kotlin test; saving only the free text, each field alone, and all three together succeed.
- [x] "Crear plan" with no intake opens "Antes de empezar" directly; no screen or sheet of the intake opens on its own anywhere.
- [x] In "Antes de empezar" (plan) and "Tus datos", "Tiempo entrenando", "Calorías de mantenimiento" and "Para conocerte" each open their own editor showing only that value; after "Guardar" the row shows the new value and the other two are unchanged; block mode shows only "Tiempo entrenando" and "Para conocerte".
- [x] No "Rellenar con mi texto", "Rellenado desde tu texto", "Marcas de antes de parar", "Añadir marca", "Zona con molestia", "Situación", cutting-calories field or "Ahora no" remains (a test over `strings.xml` for the removed keys and labels).
- [x] After the migration every existing intake keeps its training years, maintenance and free text; `situation`, `injured_area`, `pre_break_bests`, `known_cut_kcal` and their types are gone; `coach_plan_inputs` lists neither the injured area nor the situation; the 28-day derived situation is gone from `coach_compute`/`coach_summary`; the first phase comes from the intention only (no new plan in regain); a request with kind `intake_extract` gets the unknown-kind error and no usage row.
- [x] The `plan_block` request carries the free-text block (an excluded line is not in it; no text → no block) with the context-only sentence; `plan_nutrition` unchanged; T-161/T-163/T-182/T-187 regressions pass (minus the returning → regain cases); the worst-case summary stays ≤ 16 384 bytes.
- [x] Experience = max(years, history), the maintenance prior and its handover note behave as before (regressions).
- [x] "Para conocerte" with a half-written text, and "Antes de empezar" with the years or the maintenance sheet open, survive background and process death (ADR-40).

**Host tests:** pgTAP (`coach_user_intake.test.sql`: the bug payload, partial saves, removed keys, RLS and isolation; `coach_intake_derived` (no derived situation), `coach_plan_inputs`, `coach_summary`, `coach_phase` (first phase from the intention only), `delete_account_cascade` updated); `deno test` (`planBlock` body with and without the free-text block and with an excluded line; unknown `intake_extract` kind; first phase without a situation in `nutritionRules`); Kotlin DTO serialization of the save payload and repository tests; ViewModel tests (routing without the intake, each row editor saves only its value, free-text editor, discard); Robolectric (three rows and their three editors, block mode rows, removed controls absent, "Lo que el entrenador sabe de ti" intro); restoration tests.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test --allow-all supabase/functions`, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (the user's intake row keeps its years, maintenance and text) before T-200.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 24 files, about half of them deletions (developer slices if the planner counts more).
**Dependencies:** this feedback recorded (docs PR) and ADR-91 (written by the PM). E13 Q50/Q51 resolved by the regain removal; E13 Q52 does not block it (it changes `07` text and T-194 only).
**Files likely touched:** `supabase/migrations/<ts>_coach_intake_minimal.sql`, `supabase/tests/{coach_user_intake,coach_intake_derived,coach_plan_inputs,coach_summary,coach_phase,delete_account_cascade}.test.sql`, `supabase/functions/coach/{intakeExtract,intakeExtract.test,planBlock,router,schemas,budget,nutritionRules}.ts` (+ tests), `shared/…/domain/model/CoachIntake.kt`, `shared/…/data/{remote/CoachIntakeDto,repository/SupabaseCoachRepository}.kt` (+ tests), `feature/trainer/{IntakeScreen,IntakeFields,IntakeViewModel,IntakeUiState,BeforeStartScreen,BeforeStartViewModel,TrainerScreen,TrainerViewModel,FreeTextLinesScreen}.kt`, `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/{02-architecture,03-data-model,04-business-rules,05-design-system,07-coach-design}.md` (`07` §11 only), tests
**Estimated scope:** L

---

## T-200: Remove the "Recuperar" phase (user decision 2026-11-02; deferred to Phase 3) (US-82, US-88 amended; ADR-91) · M

**Story:** US-82 amended (regain scenarios void; "Cardio follows my intention in every other phase" re-anchored on "Mantenimiento"), US-88 amended · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** US-82 First phase from the intention; The phase is part of the plan; Cardio follows my intention in every other phase; The phase does not change inside a block (regressions without regain)

**Description:** After T-198 nothing can select regain any more (no `situation`); this task removes the dormant phase built by T-187/T-188. (1) **Migration (new file; never edit a merged one):** backfill every `coach_plans` row with `phase = 'regain'` (any status) to `maintenance` with a reason code `regain_retired`, **without touching its stored nutrition or block** (a regain plan's calories are maintenance, or +0.25 %/week for a muscle or strength intention, so "Mantenimiento" is the closest phase that describes them; the next plan follows the intention); then remove `regain` from the phase type (the planner picks a recreated enum or a check constraint and records it in `03`) and from every SQL reader (`20261220100000_coach_phase.sql` and later). pgTAP: a fixture plan in regain reads back as maintenance with its calories unchanged; inserting `regain` fails. (2) **`coach` function:** remove the regain branch from the phase selection, `nutritionRules.ts` (regain rate 0 / +0.25 %/week, "never a deficit" invariant), `blockRules.ts` (regain 10 sets, no increase; regain cardio), validators, schemas and their tests; the cut, gain, maintenance, recomposition and minimal rules and the 500 kcal cap stay; "maintenance within ±0.25 %/week of the expenditure in use" stays. (3) **Core and UI:** `Phase` loses `REGAIN` (DTO tolerant: an unknown phase maps to maintenance during rollout); `PhaseText` / `PhaseRow` lose the "Recuperar" label, reason and `?` copy (the `07` §12.7 sentence "Vuelves después de un parón…"); strings and tests swept (grep `regain`, `Recuperar`, `REGAIN`, `returning`). (4) **Docs:** `03` (enum), `04` §8 and §9.3 (regain rows and worked example removed), `05` §10 glossary (already updated in this docs PR); `07` changes go to the fitness-scientist (regain clean-up), not this task.

**Acceptance criteria:**
- [x] No plan, rule, validator, string or test mentions regain or "Recuperar"; inserting `regain` is rejected.
- [x] A stored regain plan reads as "Mantenimiento" with its calories, macros and block unchanged; "Tu plan" and the proposal show the "Mantenimiento" phase row.
- [x] Each intention gives its US-82 phase; every cut, gain, maintenance, recomposition and minimal regression of T-187/T-188 passes; "Perder grasa" in "Mantenimiento" keeps the 150-300 cardio band.
- [x] The phase row with its `?` open survives background and process death (ADR-40, regression).

**Host tests:** pgTAP (`coach_phase.test.sql`: backfill, rejected value, isolation); `deno test` (phase selection table without regain, invariants, block volume, cardio by phase); Kotlin DTO mapping (unknown phase) and `PhaseText` tests; Robolectric (phase row labels without "Recuperar").

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test --allow-all supabase/functions`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (the user's current plan keeps its calories) before T-199.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 14 files, mostly deletions.
**Dependencies:** T-198 merged and deployed (no `situation` left); ADR-91.
**Files likely touched:** `supabase/migrations/<ts>_coach_phase_without_regain.sql`, `supabase/tests/{coach_phase,coach_plans}.test.sql`, `supabase/functions/coach/{nutritionRules,blockRules,planNutrition,planBlock,validators,blockValidators,schemas}.ts` (+ tests), `shared/…/domain/model/{Phase,CoachPlan}.kt`, `shared/…/data/remote/CoachDto.kt`, `feature/trainer/{PhaseText,PhaseRow}.kt`, strings, `planning/{03-data-model,04-business-rules}.md`, tests
**Estimated scope:** M

---

## T-199: One exercise-name resolver for every screen, and a guard against generic exercise labels (R-54; Checkpoint E13-D feedback 1) · M

**Story:** cross-cutting fix (Checkpoint E13-D feedback; earlier instances T-155 and the T-164 block fallback); R-18 and R-35 apply · **Flow:** core + UI (backend only if a read path drops custom names) · **Size:** M · **Scenarios:** none new; every screen that names an exercise keeps its stories' scenarios with the real name

**Description:** (1) **Root cause per site:** for each place that today falls back to a generic label, find why the name is missing (a lookup over the catalogue only that misses custom exercises, a join or DTO field not mapped, a deleted custom exercise, a name loaded after the first frame) and list it in the PR body; the third repeat makes this a hole, not a patch (CLAUDE.md "a finding that repeats becomes a check"). (2) **One resolver:** an `ExerciseNameResolver` (the planner picks the layer, e.g. a `shared/` use case over the catalogue and the user's custom exercises, cached per session and refreshed when a custom exercise is created or renamed) that every ViewModel uses to turn an exercise id into its name; screens receive a resolved name and never decide a fallback. (3) **No resolvable name:** the planner checks whether an id can really be unresolvable (custom exercise deleted while sets or routines still point to it: cascade, soft delete or a stored name snapshot); if it can, the resolver uses the stored snapshot; if no snapshot exists, the planner reports it and the PM asks the user for the wanted behaviour (no agent picks a generic label). (4) **Sweep (R-39), every current fallback:** `exercise_unnamed` in `feature/workout/{WorkoutFormExerciseCard,SessionSummaryScreen,SessionDetailScreen,LiveExerciseCard}.kt`, `feature/checkin/CheckInScreen.kt`, `feature/analytics/StrengthScreen.kt`, `feature/analytics/stats/TrainingStatsSections.kt`, `feature/catalog/ExerciseMediaCatalogSection.kt` (content description), `feature/exercises/{ExerciseSummaryTab,ExerciseDetailScreen}.kt`, `feature/routines/RoutineBuilderRow.kt`; `block_exercise_fallback` in `feature/trainer/{BlockPreviewScreen,BlockWeekTab}.kt`; `intake_best_unknown_exercise` if anything is left after T-198. The strings go; `exercise_detail_title` stays only if the planner shows it is a screen title and not a name fallback (ux-reviewer confirms). (5) **Guard (R-40, R-54):** `scripts/check-ux-conventions.sh` fails when (a) a `strings.xml` value is a generic exercise label ("Ejercicio", "Ejercicio sin nombre", "Ejercicio desconocido" and the like) outside an explicit allow-list of non-name uses, or (b) a Kotlin file under `composeApp/…/feature/` falls back from an exercise name to a string resource (`?: stringResource(`, `ifBlank {`, `orEmpty()` on a `name` of an exercise; the planner writes the exact pattern); a planted fixture proves it fails, following the existing guard pattern. (6) `05` §8 R-54 is written in this docs PR; `05` §9 gets a line for the resolver if it is a shared piece the UI calls.

**Acceptance criteria:**
- [x] The PR body lists the root cause of every null-name path found.
- [x] Check-in records, the block preview and its week tab, the routine editor, the live session, the session summary and details, the workout editor, the strength and training statistics and the exercise details show the real name of a catalogue exercise and of a custom exercise (host tests with a custom exercise on each).
- [x] A custom exercise renamed in the app shows its new name on those screens without restarting the app.
- [x] `exercise_unnamed`, `block_exercise_fallback` and `intake_best_unknown_exercise` are gone; `check-ux-conventions.sh` fails on a planted generic label and on a planted fallback, and passes on the tree.
- [x] The touched screens keep their restoration tests green (ADR-40).

**Host tests:** resolver unit tests (catalogue, custom, renamed custom, the unresolvable case per the planner's finding); ViewModel tests for each screen family (workout, check-in, statistics, exercise details, routines, trainer block); Robolectric with a custom exercise on each listed screen; the guard's planted fixtures.

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh` with the new check, ktlint, detekt) + `testLocalDebugUnitTest` green; pgTAP and `deploy-supabase` only if a SQL read path changes.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7 (+ backend-developer only if a SQL read drops custom names). About 16 files, most of them one-line call-site changes.
**Dependencies:** T-198 and T-200 merged (they delete the intake bests UI and the regain copy, so this sweep never edits removed files).
**Files likely touched:** `shared/…/domain/usecase/ExerciseNameResolver.kt` (or the planner's place) + tests, the ViewModels of the screens above, the 13 composables listed in (4), `strings.xml`, `scripts/check-ux-conventions.sh` (+ fixture), `planning/05-design-system.md` §9 (if a shared piece), tests
**Estimated scope:** M

---

### Checkpoint E13-D re-test feedback (user, 2026-11-02, on the `main` APK after T-198 and T-200)
1. "Para conocerte" works; "Guardar" of the free text takes a little time (not a blocker; cause unknown, T-201 checks the save latency while it is in the code). 2. The plan proposal offers only accept or discard: should the user be able to ask for changes? **Open product question (asked to the user).** 3. The "Tengo poco tiempo" button when creating the block is not understood: **open (copy/explanation), asked to the user.** 4. Why is the block not created together with the plan? **Open product question (asked to the user).** 5-6. Equipment and alternatives are wrong → **T-201**. Checkpoint E13-D stays **open**; it is re-presented after T-199 and T-201 (items 5-6 of the checkpoint are re-tested then).

## T-201: Barra Z equipment, one "Máquina de cardio" and barbell/EZ/dumbbell alternatives (ADR-92) (Checkpoint E13-D re-test feedback 5-6; scope set by the user, 2026-11-02) · L

**Story:** fix from Checkpoint E13-D (US-78 equipment, ADR-83 alternatives graph); R-39 sweep applies · **Flow:** `fitness-scientist` first (alternatives rules into `07`), then planner → backend + core + UI as the planner sizes it (split into slices; no S) · **Size:** L

**Root cause (user, 2026-11-02):** plans already limit exercises to the saved equipment. The problem is that "Press Landmine", "Curl de Isquiotibiales con TRX", "Press en banco inclinado con barra Z", "Remo en T" and the other implements all belong to the same equipment, "barra", so owning a barbell makes them look doable.

**Description (as decided with the user and recorded in `07` §3 and ADR-92, 2026-11-02):** (1) **Equipment:** the only new item is "Barra Z" (`ez_bar`), holding the 20 EZ-bar exercises; landmine, hexagonal bar, pull-up bar, dip bars, rings, TRX and the other lumped implements go to "Otro" (no item with one exercise); "Peso corporal" means floor or wall only; two lat-pulldown variants move from máquina to polea; two rows labelled biceps become back; the planner regenerates the tags from the catalogue source (`scripts/generate-exercise-catalog.py`). Saved equipment migration: `ez_bar` is added only for gym places or lists holding every old lifting value (an empty gym list stays empty); nobody else gains it. (2) **Cardio:** the five machine chips become one "Máquina de cardio" (`cardio_machine`) in the enum, exercise tags, saved equipment, stored blocks and plans, SQL functions, `candidates.ts`, the catalogue script, Kotlin, UI, strings, docs and tests; every cardio exercise and the default and swap logic stay as they are. (3) **Alternatives:** the same exercise with a barbell, an EZ bar or dumbbells is a 100 % alternative both ways (the 34 groups of `07` §3, 71 pairs; flat, incline and decline stay separate; "Remo con Barra Inclinado" ↔ "Remo con Mancuernas Inclinado" is the user's case); an alternative under 90 % is never offered or stored (the planner may tighten the table check to 0.90-1); unify the names "Barra EZ" and "Barra Z". An alternative is only offered when its equipment is saved.

(4) **Percentage display bug (user, 2026-11-02; moved here from T-194):** in the exercise details, the alternatives tab ("Similares") shows "(100%%)" instead of "100 %". Fix the string `exercise_similar_percent` (and any other place that doubles the sign) with a host test on the visible text; the accessible reading "87 por ciento de similitud" and the sort order stay.

**Out of scope (user, 2026-11-02):** a guard for alternatives outside the user's equipment (not needed once the equipment is split); what happens when a saved block routine is edited by hand; offering to adapt or recreate the current block; anything about how blocks are built (that is Checkpoint T-202).

**Acceptance criteria:**
- [x] "Barra Z" can be selected and deselected in the equipment list; the 20 EZ-bar exercises are offered only with it; existing selections follow the migration rule above.
- [x] "Press Landmine", "Curl de Isquiotibiales con TRX" and "Remo en T" are offered only when "Otro" is saved; "Peso corporal" no longer offers bar, ring or TRX exercises.
- [x] The equipment list shows one "Máquina de cardio" instead of five machines; cardio exercises, defaults and swaps work as before; saved equipment, blocks and plans are migrated.
- [x] "Remo con Barra Inclinado" and "Remo con Mancuernas Inclinado" list each other at 100 %; every barbell/EZ/dumbbell pair of `07` §3 exists both ways; no alternative is under 90 %.
- [x] The alternatives tab shows each percentage once ("100 %", never "(100%%)"), still read "87 por ciento de similitud" and sorted from most to least similar.

**Verification:** lint + `testLocalDebugUnitTest` + pgTAP + `deploy-supabase` green with data intact (the user's saved equipment).
**Dependencies:** T-199 merged (done, #448); `fitness-scientist` pass on `07` before the planner.

---

## T-202: 👤 Decision checkpoint: how the coach builds blocks (user, 2026-11-02, Checkpoint E13-D re-test)

The user is not satisfied with how blocks are built today. This is a checkpoint for the user to decide how blocks are built. The user decides the steps; nothing about it is planned or started until they do.

- [x] 👤 The user decides how blocks are built. *(Decided 2026-11-03: the model builds the whole block and one server checker validates it; seven UX decisions recorded as E13 Q53; science in `07` §1, §3 "Complete routine", §9 by the fitness-scientist; ADR-93, ADR-94 proposed; planned as T-203…T-208 in "E13 replan: how the coach builds blocks" below.)*

---

### Checkpoint E13-D re-presentation feedback (user, 2026-11-03)

1. "In Trainer → swaps, I still see recommendations of alternative exercises with equipment that I don't have. I shouldn't see 'Polea', 'Barra Z' or 'Máquina' if in my preferences I don't have them selected." → **T-209**. Everything else approved: **Checkpoint E13-D approved** (user, 2026-11-03); it is **not re-presented** after T-209 (user decision); T-209 is verified by its host tests and T-203 follows it.

## T-209: Swap alternatives only within the selected equipment (fix; Checkpoint E13-D re-presentation feedback 1) · M

**Story:** fix from Checkpoint E13-D (US-72 swaps, ADR-83 alternatives, ADR-92); rule `04` §9.4 (alternatives and server swaps stay inside the selected equipment; a gym with none selected: everything) and §9.7b; R-39 sweep applies · **Flow:** planner (locate every path that offers alternatives or swaps: the Trainer swap picker, the block preview "Cambiar ejercicio", "Similares" if it is meant to filter, `exercise_alternatives` / `coach_cardio_options` reads) → backend and/or core + UI as the planner sizes it · **Size:** M (the planner may resize)

- [x] Find why the Trainer swap list offers exercises whose equipment is not in the user's preferences (reported: "Polea", "Barra Z", "Máquina") and fix it at the source, so every path that offers an alternative or a swap applies the same equipment filter (one helper, not per-screen copies).
- [x] **The swap selector filters when it opens, against the current equipment preferences** (user, 2026-11-03): the reported block was created before the fix, so filtering only at block creation is not enough; a block already saved must also show only alternatives within the current equipment.
- [x] Decide in the plan whether the exercise detail "Similares" tab (catalogue browsing, T-179) filters by equipment too; if it is a product choice not covered by `planning/`, log it and ask the user. *(Decided by the user 2026-11-03, E13 Q59: "Similares" and the free pickers stay unfiltered; only Trainer swaps filter. Done in #456.)*
- [x] Tests: an alternative or swap needing unselected equipment is never offered; a gym with none selected still offers everything; bodyweight and the always-allowed cardio (`walking`, `running`) stay.
- [x] No checkpoint re-test (user, 2026-11-03: E13-D approved, not re-presented); host tests cover Trainer swaps with "Polea", "Barra Z" and machines unselected offering none of them.

---

## T-210: Folder review answers "La IA no dio una respuesta válida" (fix; Checkpoint E13-H feedback 2) · M

> Merged in #472 and reverted by the user in #474 (2026-11-03): it clipped texts to guess at an unconfirmed cause. **Superseded by T-212 below** (the 502 is still open there); the boxes record only that #472 was merged.

**Story:** fix from Checkpoint E13-H item 6 (US-95 folder review, ADR-93/ADR-94) · **Flow:** backend developer (diagnose in the Edge Function / checker: why the model output is rejected as invalid; reproduce with a recorded response) → core/UI only if the planner finds the cause there · **Size:** M

- [x] Reported by the user, 2026-11-03: "Revisar carpeta" shows "La IA no dio una respuesta válida. Inténtalo de nuevo." Find the cause (schema/validation of the review output vs what the model returns; the exercise-group fix of T-211 may also change what the checker accepts) and fix it at the source.
- [x] Tests: a recorded model response that failed now passes; a truly invalid response still shows the message and is not billed twice.

---

## T-211: Correct the leg exercise muscle groups and add "Aductores" (fix; Checkpoint E13-H feedback 1) · L

**Story:** fix from Checkpoint E13-H (catalogue taxonomy, `07` §3 "Taxonomy" rule written by the fitness-scientist 2026-11-03; checks H1 and H3b) · **Flow:** planner → backend (new migration + key overrides in `scripts/generate-exercise-catalog.py`; never edit a merged migration) → core/UI (the "Aductores" enum value, label and body-map entry) · **Size:** L (the planner may split it)

- [x] Reported by the user, 2026-11-03: leg exercises are mostly filed under "Glúteos". Cause: the generator takes the first RepDB primary muscle (squats and lunges come as `gluteus_maximus`), and `secondary_muscles` is empty except hinges.
- [x] Apply the rule in `07` §3 "Taxonomy": knee-dominant compounds (squats, leg press, hack squat, lunges, split squats, step-ups) → quads primary, glutes secondary; hip abduction (clamshells, fire hydrant, side-lying abduction) → glutes; leg raises and sit-ups → core; hip adduction → new "Aductores" group, outside H1. The full list of affected exercises is in `07`.
- [x] H3b counts only the knee-extension keys listed in `07`; update `04` §9 (the line that notes this filing).
- [x] Tests: a squat counts quads sets and glute sets at the secondary weight; hip adduction, clamshells and fire hydrants no longer satisfy H3b; sit-ups add no quads sets; deploy keeps the user's routines and block intact.

---

## T-212: The coach's text has no length cap, and the Edge Function logs why it rejects (fix; Checkpoint E13-H feedback 2, user decision 2026-11-03) · M

**Story:** fix from Checkpoint E13-H item 6 (US-95 folder review; same 502 "La IA no dio una respuesta válida") · **Flow:** planner → backend developer (Edge Function `coach/`; no migration, no UI unless the planner finds the cause there) · **Size:** M

- [x] **Remove the 10-300 character cap** on every text the model writes (`REASON_MAX` in `validators.ts`, the prompts' "10 a 300 caracteres", and `04` §9 where the cap is written): folder review, block plan, block from a folder and outcomes. A long text is never rejected, never clipped, always shown in full (user, 2026-11-03: "cutting the message is a no-go"; no written justification for the cap exists in `planning/`). Keep every other validation (structure, known exercise keys, the ban on advising less cardio). Check the UI cards show long text in full (wrap or expand, never truncate).
- [x] **Debug logging:** the Edge Function logs, when a debug env flag is set (same pattern as `FOOD_PHOTO_DEBUG` in `food-photo/index.ts`, e.g. `COACH_DEBUG`), which validation rule rejected an answer (codes and paths, never content or secrets) and the model's `stop_reason`. The user enables the flag only if the 502 comes back. The PM does not set secrets.
- [x] "Aplicar en una copia" stays as is (user, 2026-11-03: keep it if it works; it has not been tested yet and is covered by Checkpoint E13-H item 6).
- [x] Tests: an answer with a 500-character comment and reason is stored and returned whole; an invalid structure still gives one retry, then 502, one usage record; the debug flag off logs nothing, on logs the codes.
- [ ] Follow-up only if the 502 returns with the flag on: fix the cause the log names (truncation at `max_tokens` is the open suspect). **The 502 returned: continued in T-213.**
---

### Checkpoint E13-H feedback 3 (user, 2026-11-03, after #476)

1. Folder row: the trash icon goes away; "Borrar carpeta" in the folder's ⋮ menu (disabled with routines, message on tap; enabled when empty) → **T-213** (third part).
2. "Revisar carpeta" feels odd → "Revisar tus rutinas" → **T-213** (third part).
3. The folder review still answers "La IA no dio una respuesta válida. Inténtalo de nuevo." after T-212 → **T-213** (debug first).
4. Romanian deadlift filing (RDL variants in Glúteos, only the Smith one in Isquiotibiales): rule written by the fitness-scientist in `07` §3 → **T-213** (second part).
Order: T-213, then re-present Checkpoint E13-H (still open).

## T-213: Debug the folder review 502 with `COACH_DEBUG`, fix the RDL muscle filing, and move the folder delete to the ⋮ menu (fix; Checkpoint E13-H feedback 3.1-3.4; follows T-212) · L

**Story:** US-95 folder review, same 502 as T-210/T-212; muscle catalogue (RDL filing); US-35 and US-95 folder UI · **Flow:** planner → ux-reviewer plan pass → backend developer (parts 1 and 2: Edge Function `coach/`, new migration and catalogue script) + compose UI developer (part 3); starts with the 👤 `COACH_DEBUG` step · **Size:** L (three parts; the planner splits into slices: core/backend first, then UI)

**Part 1: folder review 502**

- [x] 👤 **First step:** the user sets the Supabase secret `COACH_DEBUG` (they enter it; the PM only verifies the name) and retries the folder review once in the app.
- [x] A subagent reads the function logs (`supabase functions logs`/dashboard export: codes, paths, `stop_reason`; never content) and names the cause.
- [x] Fix the named cause in the function (a truncation at `max_tokens` is the open suspect); no clipping of the coach's text (T-212 rule).
- [x] Tests: a recorded response reproducing the cause is accepted or fails with the right code; deno tests stay green.
- [ ] 👤 The user disables `COACH_DEBUG` afterwards: `npx supabase secrets unset COACH_DEBUG` (after the user's retry of the folder review; deploy is green).

**Part 2: RDL muscle filing** (rule in `07` §3 "Straight-knee hinge", fitness-scientist 2026-11-03; backend: new migration + `KEY_GROUP_OVERRIDES`/`KEY_SECONDARY_OVERRIDES` in `scripts/generate-exercise-catalog.py` and its CSV; never edit a merged migration)

- [x] Re-file the 18 keys listed in `07` §3 "Straight-knee hinge" (romanian-deadlift and its barbell/EZ/dumbbell/banded/single-leg/kettlebell variants, stiff-leg-deadlift, kickstand deadlifts, the four good mornings, smith-machine-rdl): hamstrings primary, glutes secondary. Decided by the PM 2026-11-03 (scientist's recommendation): kickstand and good-morning keys are re-filed now by joint action, for a consistent catalogue.
- [x] Bent-knee deadlifts (conventional, sumo, hex-bar, deficit, pause) are unchanged.
- [x] Tests: an RDL counts hamstring sets and glute sets at the secondary weight; a good morning no longer counts as back; deploy keeps the user's routines and block intact. Note in the PR body that glute volume now comes more from hip thrusts and bridges, and that existing stats shift.

**Part 3: folder "Borrar carpeta" in the ⋮ menu and "Revisar tus rutinas"** (Checkpoint E13-H feedback 3.1, 3.2; US-35 with ADR-95 and `04` §4.15, US-95; folded in here by the user's rule: a small UI request is added to the next task; ux-reviewer defines the pattern and the sweep)

- [x] Given a folder with routines, when the user opens its ⋮ menu, then "Borrar carpeta" is disabled; tapping it shows «Mueve o elimina las rutinas de la carpeta para poder eliminarla.» (R-28, Reject haptic, no action).
- [x] Given an empty folder, when the user taps "Borrar carpeta", then the folder is deleted at once, no dialog.
- [x] The trash icon is removed from the folder row. Rule already amended in `05` R-20, the glossary (§10) and ADR-95; the task amends the `04` §4.15 wording and sweeps every screen with a folder row or the same pattern, updating tests and testTags (ux-reviewer defines).
- [x] The folder review screen title (`folder_review_title`) and every mention become "Revisar tus rutinas"; check the menu action "Revisar con el entrenador" (`05` §216) with ux-reviewer

## T-214: The coach never stops because of money (ADR-96; user decision 2026-11-04) · M

**Story:** US-76 amended · **Flow:** backend developer (budget check, errors, tests) → kotlin core/compose UI developers (usage model, "Uso de IA", limit screens, the 5 € notice); ux-reviewer defines the notice · **Size:** M
- [ ] `budget.ts` no longer refuses: remove `USER_LIMIT_EUR`, `GLOBAL_LIMIT_EUR`, `checkBudget` and the `coach_limit_reached` / `coach_no_credit` paths raised by the budget (keep `coach_no_credit` only if the provider itself reports no credit); `coach_usage` keeps recording every finished call.
- [ ] The app: remove `USER_MONTHLY_LIMIT_EUR` and "de 3,00 €"; "Uso de IA" shows the month's total only; remove the limit screens and "Reintentar after limit" that only the budget produced.
- [ ] Given the month's total passes 5 €, when the user opens the Trainer tab or "Uso de IA", then a dismissible notice shows once per month and nothing is blocked (copy and place: ux-reviewer; Spanish copy in string resources).
- [ ] Sweep the docs the ADR lists (`01` NFR-02 and §4, `02`, `03`, `07` §8 and §11, E13 US-76) and the tests (deno, Kotlin host tests, pgTAP if any).
- [ ] CI green; `deploy-supabase` green if the function changes.
; update strings, glossary and tests.
- [x] Restoration and host tests updated; deploy has no migration.


**Result (2026-11-04, #479 merged, CI green, `deploy-supabase` green on `main`):** the user's `COACH_DEBUG` logs showed `stop_reason: max_tokens` at 2760 tokens on both attempts (budget was `1500 + 60 x exercises`). Now `min(8000, 2000 + 150 x exercises)`, and a `max_tokens` stop is no longer retried for any coach kind. No Kotlin H1 test: volume counting is SQL/TS only (pgTAP and deno cover it). E9 table row pending: `blocked` for disabled sheet actions without a reason (`ExerciseMenu.kt:144`, `LiveSessionDialogs.kt:132`).
**User feedback (2026-11-04):** the folder review (and the coach flows in general) is over-engineered for "review my routines and propose changes": a prompt with the whole catalogue, a schema, a deterministic checker, per-change validation and a retry loop. If the next folder review fails and costs money, **stop patching and decide a simplification** (smaller prompt, no per-change validation, no retry). Candidate follow-up already proposed to the user: keep what is valid instead of failing the whole review. Not planned until the user decides.


## T-216: Folder review as a deterministic score and checklist on the device (fix; Checkpoint E13-H feedback 4, user 2026-11-04) · M

**Story:** US-95 folder review (rewritten; ADR-97 supersedes the folder-review bullet of ADR-93) · **Flow:** planner + fitness-scientist (done: `07` §3 "Folder review") → ux-reviewer plan pass (done) → core developer (calculator, catalogue-corrections migration, data layer) then compose UI developer (screen, deletions). Plan: `.workflow/current_task.md`, `tasks/plan.md` amendment 2026-11-04. **Size:** M (two slices). Runs **before T-214**. "Block from a folder" is not touched (separate later task).

**User feedback, 2026-11-04 (first folder review that finished, after #481):** "This worked but this functionality is terrible. It took 2 minutes, and it's giving 40 nit changes that don't bring any value. It doesn't go to the basics of whether my routine is complete or I'm missing exercises/muscles to train / frequency. The changes go to the detail instead of the big picture, and the routine created revised is broken and crashes the app. I want this functionality off or revisited, I don't want nit comments or the functionality to create a routine from the feedback; it should be much simpler in terms of tokens and outcome: just feedback for the routine without nits, so we might need to summarise what we send and make clear what we want to receive: a simple text and potentially a note over 10 about the quality of the routine, with some changes proposed."

**Decision (user, 2026-11-04, supersedes the first wording of this task):** the review is deterministic and computed on the device: a 0-10 score plus a checklist (H1 groups, days, and for "Ganar fuerza" heavy reps and order), each unmet row with a short template recommendation. No language model, no Edge Function call, no tokens or cost, no 502, no "Aplicar en una copia", no revised routine, no exercise suggestions. "Block from a folder" and everything it uses (Edge Function `folder_review`, prompt, schema, validators, `coach_folder_reviews`) stay untouched.
- [x] Planner: ADR-97, US-95 rewrite, `04` §9.14, `03`, `07` §11, `05` §10 glossary (docs PR with the plan). `fitness-scientist`: `07` §3 "Folder review" and "Catalogue facts the review reads", `scripts/catalogue-review-corrections.md`. ux-reviewer plan pass passed.
- [x] Slice 1 (core): client `Exercise.countsAsStrength`; `FolderReviewCalculator` and `ReviewFolderUseCase` with the unit tests of every check, rescale, cap and rounding case; catalogue-corrections migration generated from the corrections table (idempotent, count guard, pgTAP `catalogue_review_corrections.test.sql`; update the existing pgTAP assertions it changes); remove `applyCopy`/`FolderCopy`/apply DTOs; drop `apply_folder_review_copy` and `coach_apply_change` only if a Grep proves no other caller.
- [x] Slice 2 (UI): `FolderReviewResultRoute` screen and ViewModel per the UX notes (offline-enabled menu item, `AppListRow` read-only rows, `MetricCard` score); strip the apply UI and review-only strings, tags and tests; host tests per Gherkin scenario; restoration tests (`StateRestorationTester` and ViewModel from the same `SavedStateHandle`).
- [x] Bug: the crash of the revised routine disappears with the apply path; record it in the PR (no other path creates routines from the review screen; the block builds a block, not a revised routine).
- [ ] Side effect to tell the user at the checkpoint: the statistics "Series por grupo" change by design (secondary muscles were missing in the catalogue).
- [x] CI green; `deploy-supabase` green after it merges (migrations).


## T-217: Evaluate the nutrition plan of the AI trainer: deterministic parts, input, and where the LLM helps (user request, 2026-11-04) · S (planning only)

**Story:** E13 nutrition plan (US-72 and related; no code) · **Flow:** `fitness-scientist` → discussion with the user (same exercise as the T-216 folder review) · **Scope:** decide, before any change, what a good nutrition plan is and how it is judged.
- [x] `fitness-scientist` defines, in `07` §4 "Nutrition plan evaluation": what a good plan is, input and output contracts, model audit, deterministic rules and templates, with sources opened in the session.
- [x] Discussed with the user; **agreed 2026-11-04**: drop the model from `plan_nutrition` (ADR-98); implementation is T-218.
---

## T-218: Nutrition plan without the model: `plan_nutrition` deterministic (ADR-98; user, 2026-11-04) · M

**Story:** US-72 (nutrition plan) · **Flow:** planner → backend-developer (Edge Function, tests) → compose-ui-developer only if the screen changes → pr-creator → pr-reviewer (+ ux-reviewer if UI). Definition: `07` §4 "Nutrition plan evaluation" (input contract, outcome rules, Spanish templates, N1-N4, underweight stop, bounds).
- [x] `plan_nutrition` is `deterministic: true`: no provider, budget check or usage row; the model prompt, schema and `validateOutcomesText` path are removed if a Grep shows no other caller.
- [x] Outcomes, deadlines and reasons come from the rules and templates in `07` §4; the same input gives the same output (unit tests with fixed inputs for cut, gain, maintenance, no data, underweight, out-of-bounds values, 0-3 e1RM outcomes).
- [x] Input is validated against the `07` §4 contract; implausible values count as missing.
- [x] BMI under 18.5: "Perder grasa" starts in maintenance. N1-N4 rows show after the user edits the goals. Screen shows protein as the headline, fat "mínimo X g", carbs "el resto" (ux-reviewer plan pass for the Spanish copy).
- [x] Docs: `04` §8, `03` coach line, `07` §11 cost row.
- [x] CI green; `deploy-supabase` green after merge.
---


### Checkpoint E13-D — Getting to know you, your phase, and your plan with cardio 👤 (7 items + leave and come back) *(proposed by the epic-planner, 2026-11-01; needs the user's OK; carries the skipped E13-B3 items and the E13-B1 re-test)*
Journey: tell the coach who you are, see what it reads, get a plan in the right phase, then the block with cardio, train it and watch the week. Why here: right after T-188 (which also carries the app-wide disclaimer removal and the Perfil/Peso fixes), before the review (T-190) builds the next block on the phase and the intake. T-185, T-186 and T-187 merge with no checkpoint after them.
**Status (2026-11-02):** presented after T-188; the user's feedback (intake, saving, exercise names; see "Checkpoint E13-D feedback" above T-198) keeps it **open**. It is re-presented after T-198, T-200 and T-199 with the list below (rewritten 2026-11-02: the first list's items 1-2 tested the extraction, the bests and the automatic opening, all removed; old items 3-4 are merged into item 4 without "Recuperar", removed by the user the same day; item 3 is new).
- [x] T-185…T-188 merged, CI green, `deploy-supabase` green (data intact). *(First presentation, 2026-11-02.)*
**Rewritten 2026-11-03 (T-202 replan, epic-planner):** block creation changes completely with T-205/T-206, so the block items (old item 4's "Tengo poco tiempo" part and old item 6) move to Checkpoint E13-H; the T-201 equipment and alternatives fix gets its own item. 6 items. Re-presented right after the user's OK on the replan PR, before T-203. **The list below supersedes items 1-7 further down** (kept for reference).
- [x] T-198, T-200 and T-199 merged (T-199 in #448) and T-201 merged (#452), CI green, `deploy-supabase` green after T-198, T-200 and T-201 (data intact), every reported bug fixed.
- [x] 👤 **Approved by the user 2026-11-03** except the Trainer swap equipment issue (→ T-209, not re-presented). Install the APK and try (a plan is a paid call of about 0,1-0,4 €):
  1. "Antes de empezar" and your data (T-198): "Crear plan" opens "Antes de empezar" (nothing opens on its own); "Tiempo entrenando", "Calorías de mantenimiento" and "Para conocerte" each open their own editor with only that value; change one: the row shows it and the others stay; no "Situación", "Rellenar con mi texto", "Marcas de antes de parar", "Zona con molestia" or cutting-calories field.
  2. "Para conocerte" and what the coach reads (T-198): write your text and "Guardar": it saves; "Tus datos" shows years and maintenance as "Según lo que nos contaste (provisional)…"; "Lo que el entrenador sabe de ti" shows your text and notes exactly as sent; turn one off: it is no longer sent and the note stays where you wrote it.
  3. Exercise names everywhere (T-199): a custom exercise of yours shows its real name (never "Ejercicio") in the routine editor, the live session, the summary, the check-in records, Estadísticas and the exercise details; rename it: the new name shows.
  4. Your phase and its options (T-200): the proposal shows the phase of your intention with its reason and `?`; no phase reads "Recuperar"; after "Aceptar", "Tu plan" shows it; with "Perder grasa", "Ritmo" 1 % and "Con semana de descanso": the calories update with no new cost in "Uso de IA" and the deficit never passes 500 kcal.
  5. Calories and activity (E13-B1 re-test): "Ganar musculatura" is a small surplus over your real intake (about +440, not +800); "Actividad" shows cardio 150 min and steps (your average or 7.000), their `?` with no medical line; cardio to 90: note, calories unchanged; steps not editable.
  6. Equipment and alternatives (T-201) and Perfil/Peso: "Mi entrenamiento" shows "Barra Z" and one "Máquina de cardio"; "Press Landmine", "Remo en T" and the TRX exercises appear only with "Otro"; "Remo con Mancuernas Inclinado" → "Similares" lists "Remo con Barra Inclinado" at "100 %"; Trainer swaps never offer an exercise whose equipment you have not selected (T-209); body measurements and Peso show no generic health warning; the "No te has pesado…" reminder opens Peso without the weigh-in form.
  - Leave the app and come back on "Para conocerte" with a half-written text, and once on "Antes de empezar" with the "Calorías de mantenimiento" editor open and a number typed: the same tab, screen and draft are restored.
  - Out of scope: creating, removing or reviewing blocks (E13-H: block creation is being rebuilt); the end-of-block review (E13-E); automatic changes and injury (E13-F); report, history, chat (E13-C).
- *(Superseded 2026-11-03, kept for reference:)* Install the APK and try (a plan and a block are paid calls of about 0,1-0,4 € each). **Re-test after the fixes: items 1-4 and leave and come back; items 5-7 unchanged since the first presentation: test whichever you did not reach.**
  1. "Antes de empezar" and your data (T-198): "Crear plan" opens "Antes de empezar" straight away (nothing opens on its own); "Tiempo entrenando", "Calorías de mantenimiento" and "Para conocerte" each open their own editor with only that value; change one: the row shows it and the others stay as they were; "Crear bloque" shows only "Tiempo entrenando" and "Para conocerte". There is no "Situación", "Rellenar con mi texto", "Marcas de antes de parar", "Zona con molestia" or cutting-calories field.
  2. "Para conocerte" and what the coach reads (T-198): tap "Para conocerte": only your text, with its `?`; write it and "Guardar": it saves (no "No se pudo guardar"); "Tus datos" shows your years and maintenance as "Según lo que nos contaste (provisional)…" and your experience as the higher of what you said and your history; "Lo que el entrenador sabe de ti" shows your text and your check-in and discomfort notes exactly as sent and says they are read for your plan and your block; turn one off: it is no longer sent and the note stays where you wrote it.
  3. Exercise names everywhere (T-199): with a custom exercise of yours in a routine, its real name (never "Ejercicio") shows in the routine editor, the live session, the session summary and details, the check-in records, the block preview and its weeks, Estadísticas and the exercise details; rename it: the new name shows.
  4. Your phase and its options (T-200 re-test): the proposal shows the phase of your intention with its reason and `?`, and no phase reads "Recuperar" anywhere (a plan you had in it now reads "Mantenimiento" with the same calories); after "Aceptar", "Tu plan" shows it; with "Perder grasa", change "Ritmo" to 1 % and turn on "Con semana de descanso": the calories update with no new cost in "Uso de IA" and the deficit never passes 500 kcal; in "Antes de empezar" for the block, "Tengo poco tiempo" gives one full-body session with one set of each main lift.
  5. Calories and activity (E13-B1 re-test, E13-B3 items 1-3): "Ganar musculatura" is a small surplus over your real intake (about +440, not +800), "Perder grasa" below your expenditure; "Actividad" shows cardio 150 min and steps (your average or 7.000), their `?` with no medical line; cardio to 90: note, calories unchanged; steps not editable.
  6. Block with cardio, train and the week (E13-B3 items 4-7): pick "Bicicleta estática" and "Cinta" in "Cardio", "Crear bloque": cardio last in every routine with minutes and "suave", each routine's total time, no warm-up; swap the bike for walking (same minutes, every cardio you have offered); "Guardar bloque"; the routine editor shows only the planned minutes; train that routine: the cardio set shows only its minutes and starts no rest timer; Trainer shows "Cardio de la semana: X de 150 min" and your steps against the target (or "Sin datos de pasos").
  7. Perfil and Peso: body measurements and Peso show no generic health warning; Preferencias has "Cerrar sesión" and "Borrar cuenta" (no "Cuenta" section); tap the "No te has pesado…" reminder: Peso opens without the weigh-in form.
  - Leave the app and come back on "Para conocerte" with a half-written text, once on "Antes de empezar" with the "Calorías de mantenimiento" editor open and a number typed, and once on the block preview with a cardio `?` open: the same tab, screen and draft are restored.
  - Out of scope: the end-of-block review and phase changes (E13-E); automatic changes, deloads and injury (E13-F); report, history, chat (E13-C); the free text inside the plan and block requests (host tests: you only see it in "Lo que el entrenador sabe de ti").

---

## E13 replan: how the coach builds blocks (epic-planner, 2026-11-03; from the T-202 decision checkpoint; proposed, needs the user's OK before its PR merges)

**Why (user, 2026-11-02/03):** E13 was built with too much bias and no freedom for the model to adapt to the user: 4 days always gave "Torso/Pierna" A/B with the same exercises and no lateral raise, worse than the user's own routine. **Decisions:** E13 Q53 (seven UX decisions), science in `07` §1 "Division of work", §3 "Complete routine", "Favourites", "Taxonomy", §9, §12.8 row 11 (fitness-scientist). **ADR-93** (the model builds the block; one routine checker; folder review; `shoulder_abduction`) and **ADR-94** (two starting points, a transparent block, "Eliminar bloque", favourites), both proposed. Stories: US-72 amended, new **US-93** (start from scratch or a folder), **US-94** (remove a block), **US-95** (folder review), **US-96** (favourites); US-83 note and US-92 "My swaps stay" void. Rules: `04` §9 rewritten (§9.2-§9.8, new §9.14, §9.15).

**Audit facts used (HEAD 3e485a2f):** `blockRules.ts` builds the skeleton (`TEMPLATES` :163, `floor(min/12)` exercises, fixed group cycle so A/B sessions are identical, 10 sets per group, reps 6-10/10-15, rests 150/90 s); `candidates.ts` sorts (built-in, barbell first, alphabetical) and caps at 12; `planBlock.ts:89-102` asks the model for one exercise per slot; the model never gets the user's routines, per-exercise history, previous blocks or discomforts; `save_coach_block` rejects RPE keys; a plan with a saved block refuses a new block; hand edits are never read back; lateral raises are `isolation`.

**Order and sizing (no S task; every task pays 6-8 hand-offs):**
1. **T-203** remove a block (US-94, M) and **T-204** favourites (US-96, M) first: both are small vertical slices the new generator and its checkpoint need (removing the current block is the only way to try a new block on the user's plan; T-205 reads favourites). Not merged together: different domains (Trainer plan vs exercise catalogue) and together about 22 files.
2. **T-205** backend (L, developer slices like T-201: data → checker → generator) and **T-206** UI (L): US-72 amended + US-93 "from scratch" is about 35 files with heavy backend and heavy UI, so backend → UI.
3. *(Checkpoint after T-206 removed: the user merged it into the one after T-208, 2026-11-03.)*
4. **T-207** backend (L) and **T-208** UI (L): US-95 + US-93 "from a folder" share one review call and one screen, about 28 files, so backend → UI. The start choice sheet ships in T-208 (no one-option sheet in T-206).
5. **Checkpoint E13-H** after T-208 (one checkpoint for the whole block rework), then T-189 as planned.

**Superseded the same day by the user's E14 split (2026-11-03):** T-189…T-194, T-196, T-197 and Checkpoints E13-E/E13-F moved to the **E14 backlog** (re-planned after Checkpoint E13-H; the notes below stay as input, not as a sequence); T-166…T-170 stay in E13 (see "Back to E13"). The list below is kept for the E14 re-plan.

**Not-started tasks re-sequenced (kept, changed or superseded):**
- **T-189** kept unchanged; runs after Checkpoint E13-H (dependency changed). Removal (T-203) deletes plan weeks, so T-189's helper must ignore a plan without a block (one acceptance line added below).
- **T-190** changed: the next block is built by the T-205 generator starting from the current block's folder (with the user's edits); the per-group volume and priority rules become checker bounds; the "eligible rep-range change or alternative" options and "the next block keeps the user's own swaps" are removed (the model chooses within the checker; ADR-94). The grounding validator stays here. Smaller but still L.
- **T-191** changed slightly: "Preparar el siguiente bloque" leads to the T-206 preview (target RPE, supersets, "Qué cambia").
- **T-192, T-193** kept; T-192 applies progression to the block routines as the user left them (transparent block).
- **T-194** changed: the "unresolved level 4-5" SQL helper and the exclusion from the block move to T-205 (checker H6); T-194 keeps the "Fuera del bloque por tu lesión" section with "Volver a incluir", the graded return, the swap offer and the health note.
- **T-196, T-197** changed pending **E13 Q55** (recommended: "Adaptar el bloque" only for place or equipment; days and session length offer "Bloque nuevo" from the current folder; "Solo unos días" kept; the swap memory of row 11 removed). Sizes revisited by their planner after the answer.
- **T-166, T-167, T-169** kept; **T-168** evaluation cases follow the checker (epic "Evaluation" amended).
- Supersedes nothing else; T-173 and T-195 stay folded.

**Checkpoints (collected test items, then grouped 5-7):** favourites 1, remove a block 1, split options 1, the new preview 2 (variety and reasons; sets, ranges, RPE, rests, lateral raise and knee extension), time-short block 1, save and train with cardio and RPE 1 → **E13-H (7)**; folder review 1, good folder 1, apply in a copy 1, block from a folder with accept/reject 1, a required change rejected 1, editing a block routine still counts 1, edits reach the next block 1 → **E13-I (7)** *(the user merged both into one 7-item E13-H after T-208, 2026-11-03; see that checkpoint)*. E13-D is re-presented first with 6 items (its block items moved to E13-H).

**Open questions:** E13 Q54 and Q57 answered by the user 2026-11-03 (the recommendations); Q56 resolved by the user 2026-11-03: "Tengo poco tiempo" and the minimal phase are removed (T-205 backend, T-206 UI); Q55 and the calorie part of Q58 moved to E14. The fitness-scientist's `07` clean-up (intake, regain, §12.8 rows 1-3) was done on 2026-11-03. Still for the fitness-scientist, at the E14 re-plan: `07` §12.4 row "e1RM" still says the model may "swap to a validated alternative of the same pattern" (ADR-93 lets the model choose within the checker).

## T-203: "Eliminar bloque": remove a block without a new plan (US-94) · M

**Story:** US-94 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** US-94 every scenario (Removing the block keeps the plan; My routines stay; Cancelling keeps everything; No automatic change after removal)

**Description:** (1) **Backend:** RPC `remove_coach_block(p_plan)` (security invoker, owner only): on an accepted or proposed plan with a saved block, deletes its `coach_plan_sessions` (links and deload marks), clears the block part of the plan (`folder_id`, `training` block JSON; the planner lists the columns from `save_coach_block`/`attach_coach_block`), keeps `nutrition`, phase, options and `activity`; the folder and routines stay (normal routines), workouts untouched; idempotent (a plan without a block: no-op); `22023` for another user's plan. `plan_block`/`save_coach_block`'s "plan already has a block" refusal stays and now clears after removal (pgTAP). Any trigger or function that reads "the plan's block" (`coach_link_plan_session`, `coach_plan_weeks`, `evaluate_coach_deload`, progression in T-192 later) treats a removed block as none. E13 Q54 decides the folder part (recommendation: keep). (2) **Core:** repository `removeBlock(planId)`; `TrainerViewModel` event, reload, offline and error states. (3) **UI — templates and patterns:** "Tu plan" block section (`TrainerPlanSection`): a plan with a block shows a secondary text action "Eliminar bloque" (R-19 placement for a destructive action on a section; ux-reviewer picks between the section's action row and the Trainer ⋮ menu) instead of "Crear bloque"; the destructive confirmation dialog of R-08 ("¿Eliminar el bloque?", body "Tus rutinas se quedan en Entreno. Tu plan de comida no cambia.", "Eliminar" / "Cancelar"; ux-reviewer finalises); after removal "Crear bloque" shows again and the weeks section (`PlanWeeksSection`) disappears. No new component. (4) **Docs:** `03` (RPC), `04` §9.15 (done by the replan; the planner aligns details), `05` §10 glossary "Eliminar bloque".

**Acceptance criteria:**
- [ ] With an accepted plan and a saved block, "Eliminar bloque" + confirm removes the block and its weeks; calories, macros, phase, cardio and steps are unchanged; "Crear bloque" shows again and a new block can be requested.
- [ ] The block's folder and routines stay in Entreno; workouts and their history are unchanged.
- [ ] "Cancelar" keeps everything; offline shows the existing offline hint and changes nothing.
- [ ] After removal, finishing a workout of a former block routine links to no plan week (and, once T-192 exists, applies no automatic change).
- [ ] Trainer with the confirmation dialog open survives background and process death (ADR-40).

**Host tests:** pgTAP `coach_remove_block.test.sql` (removal keeps nutrition and phase, deletes sessions, keeps folder/routines/workouts, idempotent, new block allowed after, link trigger ignores a removed block, isolation: user B cannot remove A's block); Kotlin repository and `TrainerViewModel` tests (remove, reload, offline, error); Robolectric (`TrainerPlanSection`: action shown only with a block, dialog copy, cancel, "Crear bloque" after removal); restoration test.

**Verification:**
- [ ] pgTAP, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (the user's current plan and block untouched until he removes it).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 10 files.
**Dependencies:** the user's OK on this replan; E13 Q54 (answered by the user 2026-11-03: the recommendation); Checkpoint E13-D approved (open 👤 gate).
**Files likely touched:** `supabase/migrations/<ts>_coach_remove_block.sql`, `supabase/tests/coach_remove_block.test.sql`, `shared/…/data/repository/SupabaseCoachRepository.kt` (+ test), `feature/trainer/{TrainerPlanSection,TrainerScreen,TrainerViewModel,TrainerUiState}.kt`, strings, `planning/{03-data-model,05-design-system}.md`, tests
**Estimated scope:** M

---

## T-204: Favourite exercises: a star in the exercise detail, first in the picker and the swap list (US-96) · M

**Story:** US-96 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** US-96 Starring an exercise; Favourites first in the picker; Favourites first in the swap list; My own exercises can be favourites (the coach scenario is T-205)

**Description:** (1) **Backend:** table `exercise_favorites` (`user_id`, `exercise_id` → `exercises` incl. custom, `created_at`, primary key (user, exercise)), owner RLS, cascade on account and on custom-exercise deletion (ADR-29; add to `delete_account_cascade.test.sql`); `coach_summary` (or the T-205 block input) gains the favourites list by catalogue key or custom id (within the 16 384-byte cap; the planner re-budgets). (2) **Core:** `FavoriteExercisesRepository` (list, toggle; offline-safe like the routine repository), `ExerciseDetailViewModel` (favourite state, toggle), picker and swap-list sorting through one shared helper (favourites first, then the existing order: the picker's current sort, the swap list's similarity) used by `ExercisePickerViewModel` and `BlockPreviewViewModel`/`BlockReplaceSheet` (R-39: one helper, no copy). (3) **UI — templates and patterns:** exercise detail (`AppScreen` top bar): a toggle icon action star (`ic_star` exists; filled vs outlined, contentDescription "Añadir a favoritos" / "Quitar de favoritos", 48 dp, testTag `exercise_detail_favorite`), the state also shown as text for accessibility (never colour alone, R-41); the picker (`ExercisePickerScreen`) and the block swap sheet (`BlockReplaceSheet`) show favourites first with a small star marker on the row (ux-reviewer: marker or a "Favoritos" sub-header; one choice for both lists). No new component unless ux-reviewer asks for a shared `FavoriteMarker` in `designsystem/`. (4) **Docs:** `03`, `04` §9.15, `05` §10 glossary "Favorito".

**Acceptance criteria:**
- [x] The star toggles a catalogue or custom exercise as a favourite and persists across app restarts.
- [x] The exercise picker lists favourites first within the active filters, then the existing order.
- [x] The block preview swap list lists favourite alternatives first, then by similarity; only alternatives are ever listed.
- [x] Deleting a custom exercise removes its favourite; another user never sees mine.
- [x] Exercise detail with a toggled star and the picker with favourites survive background and process death (ADR-40).

**Host tests:** pgTAP `exercise_favorites.test.sql` (RLS, isolation, cascade, custom exercises, summary field); Kotlin repository, sort-helper and ViewModel tests (toggle, picker order, swap order); Robolectric (star action and contentDescription, picker order with marker, swap sheet order); restoration tests.

**Verification:**
- [x] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 13 files.
**Dependencies:** T-203 merged (one active task at a time; no code dependency).
**Files likely touched:** `supabase/migrations/<ts>_exercise_favorites.sql`, `supabase/tests/{exercise_favorites,delete_account_cascade}.test.sql`, `shared/…` (favourites repository, DTO, sort helper + tests), `feature/exercises/{ExerciseDetailScreen,ExerciseDetailViewModel}.kt`, `feature/exercises/ExercisePickerScreen.kt` (+ ViewModel), `feature/trainer/{BlockReplaceSheet,BlockPreviewViewModel}.kt`, strings, `planning/{03-data-model,05-design-system}.md`, tests
**Estimated scope:** M

---

## T-205: The model builds the block; one routine checker; `shoulder_abduction` (US-72 amended, US-93 "from scratch", US-96 coach part; backend) · L

**Story:** US-72 amended, US-93 (from scratch, data side), US-96 (The coach prefers my favourites) · **Flow:** backend + core (DTOs) · **Size:** L (developer slices: data → checker → generator) · **Scenarios:** US-72 Frequency and volume; One training day; Only available equipment; Rest between sets; Exercise order; The block fits me, not a template; Side deltoid and knee extension; Rep ranges and target effort; Strength rep ranges; Supersets and drop sets only when time is short; Every routine fits the session time; Not enough time for the minimum volume; A block that breaks a hard rule is never shown; Planned weights come from my history; What changed since my last block; Exercises with a severe discomfort are left out; every cardio scenario (regression) · US-93 From scratch, I pick the type of routine (data) · US-96 The coach prefers my favourites

**Description:** Rules in `07` §3 "Complete routine", "Routine tools", "Favourites", "Taxonomy"; `04` §9 as amended. (1) **Data slice:** migration A adds `shoulder_abduction` to `movement_pattern` (alone in its file); migration B retags the 7 lateral-raise keys (`07` §3 "Taxonomy"), checks that every hinge row lists hamstrings as a secondary muscle and fixes the ones that do not (DO-block assert), and the catalogue script (`scripts/generate-exercise-catalog.py`) produces the same tags; Kotlin `MovementPattern` gains the value with its label (custom-exercise form shows it; R-54/R-35 label by ux-reviewer in the PR) so the app decodes it; one SQL helper `unresolved_severe_discomforts(user)` (`07` §7: level 4-5 until "Ya no me molesta" or return step 3) used by the block input and later by T-194. (2) **Checker slice:** `routineChecker.ts`: input = routines (exercises with pattern, primary and secondary groups, sets with type, rep range, target RPE, rest, superset pairs), intention, days, minutes, equipment, discomfort exclusions, optional per-group bounds (T-190), age; output = hard failures (H1-H7 with codes and the data that failed), notes (H1 not enforceable → "Por debajo de lo recomendado"; H3/H3b conditions not met → plain note); fractional sets as `04` §4 (primary 1, distinct secondary 0.5, warm-ups out); time estimate per routine (sets × (reps × the planner's seconds-per-rep constant + rest), superset and drop-set rules; the constant is a *design decision* recorded in `04` §9.6); the structural part mirrored in `coach_assert_block` (`22023`, same codes) so a direct RPC call cannot store an invalid block. (3) **Generator slice:** new request kind `block_splits` (2-3 split options for the days and minutes, each with name, sessions and reason, one `recommended`; validated: count, H2, sessions = days; Sonnet 5.5 unless the planner measures otherwise); `plan_block` takes `{plan_id, today, split}` and the model returns the **whole block** (routines, exercises, sets, rep ranges, target RPE, rests, superset pairs, drop sets, planned weight only when present in the user's history, at most one technique note for an exercise new to the user, reason per split and exercise, `changes_vs_last_block`); input per `07` §3 "Model input": intention, phase, days, minutes, equipment, the user's routine folders (compact), the last 8 weeks per exercise (sets, e1RM trend, RPE; a new SQL read or a `coach_summary` section, within the byte cap: the planner budgets tokens and the 150 s limit), previous blocks with adherence, favourites (T-204), discomfort exclusions, the free-text block, and the eligible catalogue by group and pattern, unranked and uncapped (`candidates.ts` rewritten: no cap, no ranking for the model; the equipment order kept for server swaps and alternatives); the checker validates, one retry with the failures fed back, then `coach_invalid_output` (all attempts charged, nothing stored); cardio is appended by the server exactly as today (§9.11). **Removal of the minimal phase (user, 2026-11-03, E13 Q56; done like T-200 did for "Recuperar"):** drop the `minimal` input of `plan_block`, the minimal block path (`blockRules.ts`, `planBlock.ts`, `candidates.ts`, `context.ts`), the minimal phase calories (`nutritionRules`/`validators`: maintenance-type list), the minimal cardio (`cardioRules.ts`: 100 min, 30 per routine, the 4-block return) and the minimal exceptions (deload schedule, H1); a migration moves stored `minimal` plans to `maintenance` (calories, macros and block unchanged; cardio target 150 unless the user edited it) and adds a check so no new plan stores `minimal` (the `coach_phase` enum value stays unused: Postgres cannot drop it); `coach_phase` functions (`20261222100000` list) redefined without it; Kotlin `Phase` loses `Minimal` and decodes a stray `minimal` as maintenance; the repository's `minimal` flag goes. Removed: `TEMPLATES`, the slot count, the group cycle, the fixed sets and rep bands, the mandatory undulation, the per-slot picks schema. `save_coach_block` accepts `target_rpe`, superset groups, drop set type and planned weights (it rejected RPE keys) and still writes through `save_routine`; preview swaps (§9.7b) keep sets, range, RPE and rest. (4) **Core:** DTOs for split options and the richer block (target RPE, superset group, set types, `changes_vs_last_block`, "Por debajo de lo recomendado" note); repository `requestSplitOptions`, `requestBlock(split)`. (5) **Docs:** `03` (pattern value, payload, kinds), `04` §9 worked checker examples, `02` (new kind), `07` §11 cost row (split call), `05` nothing.
**Audit fixes folded in:** E13 backlog items 3 (RPE target per exercise; "Objetivo RPE" read "Sin objetivo") and 4 (no instructions in notes).

**Acceptance criteria:**
- [x] `block_splits` returns 2-3 options for 4 days / 60 min, one recommended, each with a reason; an invalid answer is retried once.
- [x] A block built with the fake client for 4 days "Ganar musculatura" with full gym passes H1-H7: 10-20 fractional sets for the six groups, at least one `shoulder_abduction` and one quads isolation exercise, ranges 6-20, RPE 7-9 on every working set, rests in bounds, each routine inside "Duración de la sesión"; cardio identical to today's for the same plan.
- [x] Each hard rule has a failing fixture that is rejected with its code, retried once with the failure in the prompt, then `coach_invalid_output` with nothing stored; "Ganar fuerza" with main lifts at 10-15 fails H4; H3/H3b do not apply for "Ganar fuerza", 1 day or without equipment for them (plain note); 1 day / 45 min passes with "Por debajo de lo recomendado".
- [x] Supersets and drop sets are rejected when the routine fits without them, and accepted only under their rules when it does not.
- [x] An exercise with an unresolved level 4-5 discomfort is never in the model input or the block; a favourite is in the input marked; planned weights appear only for exercises with history.
- [x] The model input holds the user's folders, 8-week per-exercise history, previous blocks with adherence, favourites and the free-text block, within the byte cap and the 150 s limit (measured in a test with the largest fixture).
- [x] `save_coach_block` stores target RPE, superset groups, drop sets and planned weights through `save_routine`; `coach_assert_block` rejects a block that breaks a structural rule; the 7 lateral raises are `shoulder_abduction`; every hinge lists hamstrings as secondary.
- [x] Every cardio, phase and equipment regression (T-182, T-187, T-201) still passes, minus the minimal-phase cases, which are deleted; a stored `minimal` plan is `maintenance` after the migration with the same calories; `plan_block` rejects or ignores a `minimal` flag (planner picks, recorded); no phase list in SQL, TypeScript or Kotlin offers `minimal`.

**Host tests:** `deno test` (`routineChecker.test.ts` table-driven per hard rule, fractional counting, time estimate, conditions and notes; `blockSplits.test.ts`; `planBlock.test.ts` with the fake client: valid block, each failure → retry → error, input contents and size, cardio appended, no minimal path; `cardioRules`/`nutritionRules` minimal cases removed; `candidates.test.ts` unranked/uncapped for the model, order kept for swaps); pgTAP (minimal → maintenance migration and the new check in `coach_phase.test.sql`; `shoulder_abduction` retag, hinge secondary check, `unresolved_severe_discomforts`, `save_coach_block` new keys and `coach_assert_block` codes, isolation); Kotlin DTO tests for split options and the richer block.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint + `testLocalDebugUnitTest` green; no paid call in CI; after the merge `deploy-supabase` green on `main` with data intact (the user's saved block and routines unchanged).

**Agent runs:** planner, backend-developer (data and checker slices), backend-developer (generator slice), kotlin-core-developer, pr-creator, pr-reviewer = 6. About 24 files (slices as in T-201).
**Progress (paused 2026-11-03, branch `feat/T-205-model-built-block`, nothing committed or pushed yet; plan in `.workflow/current_task.md`):**
- [x] Planner done (4 slices A data, B checker, C generator, D core).
- [x] **Slice A (data) and B (checker) done, all green** (`db reset`, `db lint`, `supabase test db`, `deno test` 597, encoding check, Python catalogue tests): migrations `20261227100000…300` (enum `shoulder_abduction`; retag + hinge hamstrings; `minimal` → maintenance with `coach_phase` recreated without it; `unresolved_severe_discomforts`, `coach_block_model_input`, `coach_block_rep_ok`, `coach_assert_block`, `save_coach_block`), catalogue script + CSV, `routineChecker.ts` + tests, pgTAP (`coach_block_model_built`, `save_coach_block_model_built`, edited `coach_phase`, `exercises_movement_pattern`), docs `03` and `04` §9.6/§9.8.
- [x] **Slice C (generator) done (deno 566 green):** `blockSplits.ts`, `planBlock.ts` (whole block from the model, one retry, `coach_invalid_output`), `candidates.ts` unranked/uncapped, delete the skeleton (`TEMPLATES` etc.), `router`/`prices`/`schemas`, history read within the 48 000-byte prompt cap, 150 s limit test. Also the TypeScript removal of `minimal` (`blockPhase.test.ts`, `planBlock.test.ts`, `nutritionRules.ts`, `validators.ts`, `schemas.ts`, `cardioRules.ts`); `plan_block` rejects a `minimal` flag as `INVALID_INPUT`. **Slice C must put `intention` into `training` when it attaches a block** (SQL H4/H7 apply only when it is present).
- [x] **Slice D (core) done (unit tests green):** Kotlin `MovementPattern.ShoulderAbduction` + label, `Phase` without `Minimal` (a stray `minimal` decodes as maintenance), DTOs for split options and the richer block, repository `requestSplitOptions` / `requestBlock(split)`, compile-only removal of the "Tengo poco tiempo" wiring and the two Minimal host tests (T-206 owns the visible redesign; until then `BlockPreviewViewModel` sends the recommended split).
- [x] Then pr-creator → pr-reviewer → CI green → merge → `deploy-supabase` green on `main` (data intact). *(Merged in #462, CI green, `deploy-supabase` green on `main`, 2026-11-03. Carry to T-206: `split_reason`, `changes_vs_last_block` and `notes` are not stored with the block; remove the unused `phase_*_minimal`, `before_start_minimal*`, `block_preview_minimal_label` strings; first paid run re-measures the 85-token-per-exercise output budget.)*
- Decisions to confirm with the user or note in the PR: (1) the spec arithmetic in the planner text was wrong (6 × (30 + 3×44 + 3×90) = 2 592 s, not 2 952 s; `04` uses 2 592); (2) drop-set rule changed: "fits without it" means the drop set becomes a normal working set with the previous rest, accepted only if the routine fits with the drop set but not with that normal set (`04` §9.6); (3) an 8th lateral raise, `band-long-cable-lateral-raise`, is also `shoulder_abduction`; (4) SQL error codes renamed `h6_sessions`, `h6_rest`, …; stored `sets` accept the old integer or an array.
- Known unrelated flake: `meal_slots` / `delete_meal_slot` pgTAP row-ordering failure, once in a full run.

**Dependencies:** T-204 merged (favourites). (The `07` intake and injury-area clean-up is done, 2026-11-03.) The "unresolved level 4-5" helper built here is later reused by E14's injury task (former T-194).
**Files likely touched:** `supabase/migrations/<ts>_movement_pattern_shoulder_abduction.sql`, `<ts>_catalogue_shoulder_abduction_hinge_secondary.sql`, `<ts>_coach_block_model_built.sql` (`save_coach_block`, `coach_assert_block`, `unresolved_severe_discomforts`, history read), `scripts/generate-exercise-catalog.py`, `supabase/functions/coach/{routineChecker,blockSplits,planBlock,blockRules,candidates,blockValidators,schemas,router,prices}.ts` (+ tests, `blockFixtures.ts`), `supabase/tests/{coach_block_model_built,exercises,save_coach_block}.test.sql`, `shared/…/domain/model/{Exercise,CoachBlock}.kt`, `shared/…/data/remote/CoachBlockDto.kt`, repository (+ tests), `planning/{02-architecture,03-data-model,04-business-rules,07-coach-design}.md` (§11 only)
**Estimated scope:** L

---

## T-206: Start a block from scratch: split options and the new block preview (US-93 "from scratch", US-72 amended; UI) · L

**Story:** US-93 (Choosing how to start: only "Desde cero" here; From scratch, I pick the type of routine; Nothing is created without "Guardar bloque"), US-72 amended (UI) · **Flow:** core + UI · **Size:** L · **Scenarios:** US-93 From scratch, I pick the type of routine; Nothing is created without "Guardar bloque" · US-72 Preview before saving; Frequency and volume are shown; The block fits me, not a template (reasons); Rep ranges and target effort; Supersets and drop sets only when time is short; Not enough time for the minimum volume; A block that breaks a hard rule is never shown ("Reintentar"); What changed since my last block; Saving creates routines; Nothing is saved without confirmation; Training the cardio (regression)

**Description:** (1) **Core:** `BlockSplitsViewModel` (request options with streamed progress, pick one, request the block, hand-off to the preview, limit, offline, invalid output with "Reintentar" keeping the choice, US-76/T-160 rule), `BlockPreviewViewModel` (richer block: target RPE, superset pairs, drop sets, planned weight, notes, `changes_vs_last_block`, below-recommended note), `BeforeStartViewModel` ("Crear bloque" → split options instead of the direct block call). (2) **UI — templates and patterns:** **split options** = an `AppScreen` with a single-choice list of 2-3 option cards (name, sessions per week, the reason; "Recomendada" label as text on the recommended one, preselected; R-47 single-choice control chosen by ux-reviewer: radio rows or selectable `AppCard`s) and a pinned footer `PrimaryButton` "Crear bloque" (R-09) → the streamed-progress state (T-162) → the **block preview** (T-164/T-179/T-183) extended: each exercise row shows "3 × 8-12 · RPE 8 · 1 min 30 s" (format with ux-reviewer, R-37), superset pairs grouped with the existing routine-editor superset marker (reuse, no copy), a drop set labelled on its set, the planned weight when present, the per-split reason at the top, a "Qué cambia respecto al bloque anterior" section when there was a block (a `NavigationRow` or an expanded `InfoBlock`: ux-reviewer), and "Por debajo de lo recomendado" as an `InlineNote` with `?` (R-46); "Guardar bloque" unchanged. **Removal of "Tengo poco tiempo" and "Mínimo" (user, 2026-11-03, E13 Q56):** the option leaves "Antes de empezar" (`BeforeStartScreen`/`ViewModel`/`UiState`, the route parameter in `AppRoutes`/`AppNavHost`), the "Mínimo" label leaves the preview (`BlockPreviewScreen`/`ViewModel`/`UiState`) and the phase texts (`PhaseText`), and the strings `before_start_minimal`, `before_start_minimal_helper`, `block_preview_minimal_label`, `phase_label_minimal`, `phase_help_minimal` are deleted (not renamed) with `BeforeStartMinimalTest` and `BlockPreviewMinimalTest`; `05` §10 rows already marked removed. Live session: "Objetivo RPE" shows the routine's target RPE (verify the existing reader; fix if it reads only progression). (3) **Docs:** `05` §10 glossary ("Recomendada", "Qué cambia respecto al bloque anterior", "Por debajo de lo recomendado"), §6.2 if the Trainer flow order changes.

**Acceptance criteria:**
- [x] "Crear bloque" (after "Antes de empezar") shows progress, then 2-3 options with one "Recomendada" preselected and each reason; picking another and "Crear bloque" shows progress and the preview.
- [x] The preview shows per exercise the sets, rep range, target RPE, rest and planned weight when present; superset pairs and drop sets are marked; the split reason and "Qué cambia…" show when they exist; a short session shows "Por debajo de lo recomendado" with its help.
- [x] An invalid block shows the error with "Reintentar" keeping the chosen option; the limit and offline states follow T-160; leaving at any step creates nothing.
- [x] "Guardar bloque" creates the folder; the routine editor shows the target RPE and supersets; the live session shows "Objetivo RPE" from the routine and the cardio set as before.
- [x] "Antes de empezar" has no "Tengo poco tiempo"; no screen shows "Mínimo" (a former minimal plan reads "Mantenimiento"); `strings.xml` holds none of the removed keys.
- [x] Split options with a choice made, the progress state and the preview survive background and process death (ADR-40).

**Host tests:** `BlockSplitsViewModel` tests (load, pick, progress, hand-off, retry, limit, offline, leave); `BlockPreviewViewModel` tests (new fields, superset grouping, below-recommended note, changes section); Robolectric (`BlockSplitsScreen` options and recommended label, preview rows with RPE/rest/superset/drop set, note with `?`, live-session "Objetivo RPE"); restoration tests (split choice, preview).

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green. No paid call in CI.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 16 files.
**Dependencies:** T-205 merged and deployed.
**Files likely touched:** `feature/trainer/{BlockSplitsScreen,BlockSplitsViewModel,BlockSplitsUiState}.kt`, `feature/trainer/{BlockPreviewScreen,BlockPreviewViewModel,BlockPreviewUiState,BlockWeekTab,BeforeStartScreen,BeforeStartViewModel}.kt`, `feature/workout/LiveSessionScreen.kt` (RPE reader, if needed), `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

*(No checkpoint after T-206: the user merged E13-H and E13-I into one checkpoint after T-208, 2026-11-03: "no point testing partial plans".)*

---

## T-207: Folder review and a block from a folder (US-95, US-93 "from a folder"; backend) · L

**Story:** US-95 (data), US-93 (From my folder; A missing requirement must be fixed; My edits reach the next block; Edited block routines still count) · **Flow:** backend + core · **Size:** L · **Scenarios:** US-95 Reviewing a folder; A good folder; Applying the changes in a copy; Nothing is blocked; The proposed changes are checked · US-93 From my folder; A missing requirement must be fixed; My edits reach the next block (data); Edited block routines still count (regression of the routine-id links)

**Description:** (1) **Migration:** `coach_folder_reviews` (`user_id`, `folder_id` cascade, `purpose` `review | block`, `plan_id` nullable, `input_hash`, `result` jsonb: hard failures, notes, soft comments, proposed changes with ids, `created_at`), owner RLS, cascade on account (ADR-29). (2) **Request kind `folder_review`** (paid, budget-checked, streamed; Sonnet 5.5 unless the planner measures otherwise): reads the folder's routines (exercises, sets with types, rep ranges, target RPE, rests, superset groups), the intention, days and minutes from "Mi entrenamiento", equipment, the per-exercise history, favourites, discomfort exclusions and the free-text block (the same input builder as T-205); runs the T-205 checker → each hard failure is a "Falta" item; the model writes soft comments and proposed changes as typed operations (add, replace, remove an exercise; change sets, rep range, target RPE, rest; each with a reason and the "Falta" it fixes, if any); the server applies each change to a copy in memory and re-runs the checker, dropping a change that adds a hard failure; stores the review. (3) **RPC `apply_folder_review_copy(p_review, p_change_ids)`**: creates a new folder (name rule by ux-reviewer, e.g. "<nombre> (revisada)", unique under the existing routine and folder name rules) with the accepted changes through `save_routine`; the source folder is never written. (4) **Block from a folder:** `plan_block` with `{plan_id, today, start: 'folder', review_id, accepted_change_ids}` builds the block from the folder's routines plus the accepted changes (no new model call), runs the checker (any hard failure → `coach_block_needs_changes` with the failures, nothing stored), appends the server's cardio (§9.11), and returns the same block shape as T-205 for the preview; `save_coach_block` saves it as a new block folder (source untouched). A block folder edited in Entreno is a normal folder for this flow (decision 4). (5) **Core:** models and repository (start review with progress, read stored review, apply copy, block from review). (6) **Docs:** `03`, `04` §9.14 details, `02` (new kind), `07` §11 cost row.

**Acceptance criteria:**
- [x] Reviewing a folder with no lateral raise for "Ganar musculatura" 4 days returns an H3 "Falta" with a proposed change that adds one; a folder that passes every hard check returns no "Falta", only comments.
- [x] A proposed change that would add a hard failure is never returned; changes carry reasons.
- [x] "Apply in a copy" with two of three changes creates a new folder with exactly those changes; the source folder's routines are byte-identical before and after.
- [x] A block from a folder with the needed changes accepted passes the checker and saves as a new block folder with cardio appended; rejecting the H3 change returns `coach_block_needs_changes` with H3 and stores nothing.
- [x] A block whose routines were edited in Entreno: the review reads the edited routines; completed workouts of edited routines still link to their plan week.
- [x] Budget refusal, offline and invalid output follow T-160; pgTAP RLS, isolation (user B cannot review or copy A's folder), cascade.

**Host tests:** `deno test` (`folderReview.test.ts`: hard items, change re-check and drop, typed operations, fake client, retry, budget; block from folder pass and `coach_block_needs_changes`); pgTAP (`coach_folder_reviews` RLS/isolation/cascade, `apply_folder_review_copy` source untouched and names, block from folder save, link trigger with edited routines); Kotlin DTO and repository tests.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

*(Merged in #466, CI green, `deploy-supabase` green on `main`, 2026-11-03.)*

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5. About 16 files.
**Dependencies:** T-206 merged (no checkpoint in between since the user merged E13-H and E13-I, 2026-11-03); T-205 (checker and input builder).
**Files likely touched:** `supabase/migrations/<ts>_coach_folder_reviews.sql`, `supabase/tests/{coach_folder_reviews,delete_account_cascade}.test.sql`, `supabase/functions/coach/{folderReview,planBlock,routineChecker,schemas,router,prices}.ts` (+ tests), `shared/…/domain/model/FolderReview.kt`, `shared/…/data/{remote/FolderReviewDto,repository/SupabaseCoachRepository}.kt` (+ tests), `planning/{02-architecture,03-data-model,04-business-rules,07-coach-design}.md` (§11 only)
**Estimated scope:** L

---

## T-208: Folder review screen, the start choice and a block from a folder (US-95, US-93 UI) · L

**Story:** US-95 (UI), US-93 (UI) · **Flow:** core + UI · **Size:** L · **Scenarios:** US-95 every scenario (UI) · US-93 Choosing how to start; From my folder; A missing requirement must be fixed; My edits reach the next block; Nothing is created without "Guardar bloque"

**Description:** (1) **Core:** `FolderReviewViewModel` (two purposes: review only, or block; start with streamed progress; per-change accept/reject state; apply copy; continue to block; `coach_block_needs_changes` state; limit, offline, retry), `TrainerViewModel`/`BeforeStartViewModel` (start choice), `RoutinesViewModel` (folder menu action and navigation). (2) **UI — templates and patterns:** **Entreno folder menu** (the existing folder action sheet `AppActionSheet`, beside "Renombrar"/"Eliminar"): a new action to review the folder with the coach (label by ux-reviewer, e.g. "Revisar con el entrenador"; R-35: must not reuse "Revisar el bloque", reserved for life changes). **Folder review** = an `AppScreen`: streamed progress (T-162 state) → an audit message at the top (one short paragraph), a "Falta" section (one row per hard failure, text, never colour alone, R-41), a comments section, and the proposed changes as a checklist (rows with a checkbox, the change in one line and its reason; all checked by default; ux-reviewer picks the existing checkbox row); pinned footer `PrimaryButton` "Aplicar en una copia" (shown only when changes exist; review purpose) — after applying, a confirmation snackbar with the new folder's name and the folder appears in Entreno. **Start choice:** "Crear bloque" (after "Antes de empezar") opens an `OptionPickerSheet` (R-12) "Desde cero" / "Desde una carpeta"; "Desde cero" → T-206 split options; "Desde una carpeta" → an `AppPickerSheet` of the user's folders (the existing move-to-folder picker pattern) → the same review screen in block purpose with the footer "Crear bloque" → progress → the T-206 preview → "Guardar bloque"; a rejected required change shows the "Falta" items inline and keeps the footer disabled with the reason shown (the `05` §8 rule for disabled actions; ux-reviewer). (3) **Docs:** `05` §10 glossary ("Desde cero", "Desde una carpeta", the review action label, "Falta", "Aplicar en una copia"), §6.2 Trainer flow.

**Acceptance criteria:**
- [x] The folder menu offers the review; it shows progress, then the audit message, the "Falta" items, the comments and the proposed changes with reasons.
- [x] Unchecking one change and "Aplicar en una copia" creates the new folder in Entreno with only the checked changes; the original folder is unchanged; leaving changes nothing.
- [x] "Crear bloque" offers "Desde cero" and "Desde una carpeta"; from a folder, accepting the changes leads to the preview and "Guardar bloque" creates the block; unchecking a required change shows what is missing and does not create the block.
- [x] Creating a block from the folder of a block edited in Entreno shows the edited routines in the review and the preview.
- [x] The review with its checked changes, the start choice and the folder picker survive background and process death (ADR-40).

**Host tests:** `FolderReviewViewModel` tests (both purposes, progress, checks, apply, needs-changes, retry, limit, offline, leave); `RoutinesViewModel` test (menu action); Robolectric (folder menu action, review sections, checklist, footer visibility, start choice sheet, folder picker, needs-changes state); restoration tests.

**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green. No paid call in CI.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 15 files.
**Dependencies:** T-207 merged and deployed.
**Files likely touched:** `feature/trainer/{FolderReviewScreen,FolderReviewViewModel,FolderReviewUiState,BlockStartSheet}.kt`, `feature/trainer/{BeforeStartScreen,BeforeStartViewModel,TrainerViewModel}.kt`, `feature/routines/{RoutinesSheets,RoutinesViewModel,RoutinesUiState}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** L

---

### Checkpoint E13-H feedback 4 (user, 2026-11-04, items 6 and 8; everything else looked good)
Items 1-2 approved, 3/4/5/7 postponed (see the checkpoint). The checkpoint stays **open**; fixes first (T-220, T-221, T-222), then re-present items 6 and 8. T-219 (block creation evaluation) follows the checkpoint approval.
- **Folder review is incomplete (item 6).** (a) As a user I cannot tell **which movement pattern is missing**, nor whether adding more sets of already covered patterns is enough: needs a decision from `fitness-scientist` (what "covered" means per goal, when a pattern is missing, what the app says). (b) For each muscle show the **recommended minimum** and, when the user **exceeds** the recommendation, a message such as "12 series a la semana (la recomendación son 10)"; wording from `ux-reviewer`. (c) The `?` ("Cómo se calcula") explains rules the review does not check (for example at least 2 training days; for strength, main lifts at 8 reps or fewer and first in the session): **every rule the `?` states must be a validation of the review, per goal**, or leave the `?`; `fitness-scientist` defines the per-goal checks. (d) Remove the text under the note "Carpeta «…». Es una guía: no cambia ni bloquea tus rutinas" (`folder_review_result_summary`, `folder_review_result_summary_plain`); the folder name goes in the title: "Revisión de «carpeta»" instead of "Revisar tus rutinas".
- **"Similares" tab (item 6/exercise detail).** The note is not well integrated with the favourite star: revisit the UI (idea: the star next to the name, not next to the percentage). Question: why is the percentage blue and not green? (investigate; colour semantics per `05`).
- **Nutrition plan summary (item 8).** Question: why is protein in bold? (answer: the T-218 spec made protein the headline macro, `07` §4 / ux notes; the user questions it: revisit.) Question: why the banner "Reparte la proteína: unos 32 g por comida…" (`plan_proposal_distribution`)? (it comes from the protein-distribution rule in `07`, before T-218; the user does not want it unless the PM/ux-reviewer can justify it: decide with `ux-reviewer`.)
- **Tasks (proposed order):** **T-220** folder review content and wording (fitness-scientist first for (a) and (c) in `07`, ux-reviewer for (b) and (d); then backend/core/UI) · L · **T-221** "Similares" tab UI: star, note and percentage colour (ux-reviewer plan; sweep of other places with a star next to a metric) · M · **T-222** nutrition plan summary: protein emphasis and the distribution banner (ux-reviewer decision) · S/M. Each in its own fresh session.

### Checkpoint E13-H — Blocks built for you, from scratch or from your own folders 👤 (7 items + leave and come back) *(proposed by the epic-planner 2026-11-03; one checkpoint after T-208 by the user's decision the same day, merging the former E13-H and E13-I: "no point testing partial plans")*
Journey: star the exercises you like, remove your current block, build one from scratch and train it, edit it in Entreno, have the coach review a folder, and build the next block from your own (edited) routines. Why here: right after T-208, the last task of the block rework, before the end-of-block review (T-190) builds the next block from the block's folder. T-203…T-207 merge with no checkpoint after them.
- [x] T-203…T-208 merged, CI green, `deploy-supabase` green after T-203, T-204, T-205 and T-207 (data intact: your plan, block and routines unchanged until you remove the block), every reported bug fixed.
**User decision, 2026-11-04:** items 1 and 2 are already approved; items 3, 4, 5 and 7 are postponed until block creation is revisited (T-219 and what it decides); this checkpoint is only items 6 and 8 (+ leave and come back on the folder review). Items 3, 4, 5, 7 stay below, unchecked, for the later re-presentation.
- [ ] 👤 Install the APK and try (split options plus a block, and each folder review, are paid calls of about 0,05-0,4 €):
  1. *(Approved by the user 2026-11-04.)* Favourites: star an exercise (for example "Elevaciones laterales") in its detail: it is listed first, with its marker, in the exercise picker and in the swap list of the block preview; unstar it: it goes back to its place.
  2. *(Approved by the user 2026-11-04.)* Remove your block: "Tu plan" → "Eliminar bloque" → confirm: the block and its weeks are gone; your calories, phase, cardio and steps stay; the folder and routines are still in Entreno; "Crear bloque" is back ("Cancelar" changes nothing).
  3. *(Postponed until block creation is revisited.)* From scratch: "Crear bloque" → "Desde cero": 2-3 types of routine for your days and time, one "Recomendada" with its reason; pick one: the preview shows the split reason, a reason per exercise, at least one lateral raise and one knee extension (with "Ganar musculatura" and 2+ days) and your favourites where they fit; trying another type gives different exercises, not a template.
  4. *(Postponed.)* What each exercise asks: sets, a rep range (6-20; 3-8 on main lifts for "Ganar fuerza"), "RPE 7-9", its rest and your planned weight when you trained it; 10-20 weekly sets per muscle group; with a 30-minute session, supersets of different muscles or a drop set appear and, if the minimum does not fit, "Por debajo de lo recomendado" with its `?`.
  5. *(Postponed.)* Save, train and edit: "Guardar bloque"; train a routine: "Objetivo RPE" shows its target (never "Sin objetivo") and the cardio is last with its minutes; then in Entreno swap an exercise or change sets in a block routine and train it: Trainer still counts that session in its week.
  6. Review a folder (T-216: deterministic, short feedback): from a folder's menu in Entreno ask the coach to review it: a short audit, what is missing under "Falta" and comments with reasons (a folder that meets everything shows only comments); uncheck one change and "Aplicar en una copia": a new folder with only the checked changes; your original is exactly as before.
  7. *(Postponed.)* Next block from your edits: "Eliminar bloque", then "Crear bloque" → "Desde una carpeta" with the block's folder: the review starts from your edited routines; uncheck a change that fixes a "Falta": the block is not created and it says what is missing; check it again: preview with the cardio last, "Guardar bloque": a new block folder, the source folder untouched.
  8. Nutrition plan (T-218, ADR-98, no AI call): the proposal shows protein as the headline, fat "mínimo X g" and carbs "el resto"; after you edit the goals, "Tus objetivos" shows four rows "Cumple" / "No cumple" (or one line if all pass); a "Perder grasa" plan with BMI under 18,5 starts in maintenance with a note; "Series por grupo" statistics may shift (catalogue secondaries corrected, T-216).
  - Leave the app and come back on the folder review with one change unchecked: the same tab, screen and choices are restored. (The split-options and block-preview restores are postponed with items 3-5 and 7.)
  - Out of scope: the end-of-block review, automatic changes, injury and life changes (E14, backlog); report, history, chat (E13-C).

---

## → E14 backlog (moved 2026-11-03 by the user; not E13 work, not active)

Everything from here down to "Back to E13" (T-189…T-194, T-196, T-197 and Checkpoints E13-E and E13-F) belongs to the E14 backlog ([`planning/epics/E14-block-follow-up.md`](../planning/epics/E14-block-follow-up.md)). The text is kept as input for the E14 re-plan after Checkpoint E13-H; it is **not** aligned with ADR-93/ADR-94 beyond the 2026-11-03 notes, its 👤 checkpoints are **not** gates, and none of these tasks is started from here.

## T-189: Plan weeks follow the training: breaks pause the block (US-73 amended, US-88) · M

**Story:** US-73 amended, US-88 (pause) · **Flow:** backend + core + small UI · **Size:** M · **Scenarios:** US-73 A week without training does not count; Coming back resumes where I left off; Completing a planned routine, Weekly adherence, Missed sessions do not block the next week, Cardio minutes and Steps of the week (regression with the new weeks)

**Description:** (1) **Backend (`04` §9.9-§9.10 amended):** replace "week `n` = `block_start + 7(n-1)`" with plan weeks that follow the training: a calendar week (ISO, Monday-Sunday) with no completed session does not count; plan week `n` is the `n`-th calendar week with a completed session since `block_start`, the current week the next one; one shared SQL helper used by `coach_plan_weeks`, the link trigger `coach_link_plan_session`, `evaluate_coach_deload` (week `c`) and a new `block_end_estimate` (end date moved by the paused weeks; read by T-190 and the Trainer). Cardio minutes and steps per week use the calendar week that plan week maps to. In-progress blocks are recomputed by the migration with no link lost (pgTAP with a fixture of the user's shape: links kept, weeks renumbered). Audit: the calendar-week logic lives in `20261211100000_coach_adherence_deload.sql:144, 163`, `coach_link_plan_session` (:16-60) and `20261218100000_coach_plan_weeks_cardio.sql:35-38`; all of them move to the helper. (2) **Core:** plan-week model gains `paused` (the current calendar week has no session yet after a skipped one) and the estimated end. (3) **UI — templates and patterns:** Trainer week section (`PlanWeeksSection`, T-165/T-183): the current week stays the same after a skipped calendar week; a status line "En pausa desde el lun 10 nov · retomas donde lo dejaste" (text, never colour alone, R-41; ux-reviewer finalises) and the moved end ("Termina hacia el 14 dic") in "Tu plan"; no new component.

**Acceptance criteria:**
- [ ] In week 2, a calendar week with no session keeps the block in week 2 and moves the end one week; the next completed session links to week 2's free row.
- [ ] Two skipped calendar weeks: nothing is moved or marked failed; adherence counts only plan weeks.
- [ ] The deload evaluation uses plan weeks (a skipped week does not make week 3 come earlier).
- [ ] An in-progress block keeps every existing link after the migration.
- [ ] Cardio minutes and steps per week still match their calendar week (T-183 regression).
- [ ] The week section with the pause line survives background and process death (ADR-40).

**Host tests:** pgTAP (`coach_plan_weeks` with skipped weeks, link trigger with pauses, `block_end_estimate`, deload week `c` with pauses, migration of an in-progress block, isolation; existing adherence and deload tests updated); Kotlin DTO tests; `TrainerViewModel` tests (paused, end date); Robolectric (pause line, end date); restoration test.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (the user's block links checked).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 11 files.
**Dependencies:** ~~Checkpoint E13-D approved~~ **Checkpoint E13-H approved** (T-202 replan, 2026-11-03: T-203…T-208 run first). Added acceptance (T-202 replan): a plan whose block was removed (T-203) has no plan weeks and the helper returns none; edited block routines (same routine id) still link.
**Files likely touched:** `supabase/migrations/<ts>_coach_plan_weeks_follow_training.sql`, `supabase/tests/{coach_adherence,coach_deload,coach_plan_weeks}.test.sql`, `shared/…` (plan week model, DTO), `feature/trainer/{PlanWeeksSection,TrainerScreen,TrainerViewModel}.kt`, strings, `planning/{03-data-model,04-business-rules}.md`, tests
**Estimated scope:** M

---

## T-190: End-of-block review and the next block: backend (US-83, US-84; US-88 ~~regain and~~ minimal proposals) · L

> **Amended 2026-11-02 (Checkpoint E13-D feedback, T-198):** "Recuperar" is removed entirely (user decision, deferred to Phase 3; T-198, T-200), so every regain part below is void: the review inputs "stated bests reached (Epley) for regain", the regain exit trigger, "`situation` returning → regain", "cut and regain only redistribute" (now: cut only) and the US-88 scenario "The review after a long break proposes regain" (E13 Q50/Q51 resolved by the removal). The free-text block is already in the `plan_block` and `plan_nutrition` requests (T-198); this task adds the rest of the summary to the block prompt, the review answers to the free-text block and the grounding validator. US-83 "Regain ends when I am back" is void.

> **Amended 2026-11-03 (T-202 replan, ADR-93, ADR-94):** the next block is built by the **T-205 generator** (the model builds the whole block), starting from the current block's folder as the user left it (US-93 "My edits reach the next block"); the review passes the per-group targets of (4) to the routine checker as bounds (H1 per group), instead of feeding `blockRules` slots and overrides; the "eligible rep-range change or validated alternative" option list in (4) and the US-83 "A stalled lift" option ids are removed: the model chooses within the checker and explains (US-83 note amended); the "next block keeps the user's own swaps" line (row 11) is void. Phase triggers, review inputs, answers, priorities and the grounding validator are unchanged. The planner re-sizes (still L expected).

**Story:** US-83, US-84 (data side), US-88 (The review after a long break proposes regain; Ending a busy period; A long minimal phase) · **Flow:** backend + core · **Size:** L · **Scenarios:** US-83 The block has ended (data); What the review shows (data); "¿Cómo te ha ido?" (storage, free-text block); A phase change is proposed; Keeping my phase; Regain ends when I am back; Long gain asks before going on; A stalled lift; "Me ha costado" adds no volume; Low adherence; Starting the next block (data); Nothing starts without me · US-84 every scenario (rules)

**Description:** (1) **Migration:** `coach_block_reviews` (`plan_id` cascade, user, `feeling` enum `good | hard | easy` ("Bien", "Me ha costado", "Me ha sobrado"), `text` ≤ 500, `priorities` jsonb `{muscle_group: prioritize | maintain}`, eligible options and the model's choice, `phase_decision` `accepted | kept`, `next_plan_id`, timestamps), owner RLS, cascade; the review text joins the T-185 free-text block (kind `review`). (2) **RPC `coach_block_review_inputs(p_plan, p_today)`** (deterministic, one source for screen and function): sessions done vs planned per routine and adherence (plan weeks of T-189), e1RM per main lift at the start and end of the block and `improved`, weight trend vs the phase's target rate, complete diary days (`07` §2 cleaning), open discomforts, time in phase (blocks, deficit blocks without diet-break weeks), floor-limited weeks, deloads that ran, stated bests reached (Epley, `04` §4.1) for regain, age ≥ 65, `block_ended` and `can_review_early` (any time after the block is saved, E13 Q47). (3) **Phase triggers (`07` §12.3, rules only):** eligible next phases with reason codes: cut → maintenance after 3 deficit blocks or a whole floor-limited block with the trend slower than half the target rate; maintenance after ≥ 1 block following a cut → the intention's phase; regain exit (more than half the stated bests reached, a block with no main-lift improvement, or 3 blocks) → the intention's phase (a cut for "Perder grasa"); `situation` returning → regain; "¿Seguimos?" after 6 blocks of gain, maintenance or recomposition; minimal → normal when the user ends it or after 8 blocks; intention change. (4) **Next-block rules (`07` §12.4):** per muscle group target sets = previous block's; +2 for a group with a stalled main lift only when adherence ≥ 80 %, no open discomfort on it, phase gain / maintenance / recomposition and the answer is not "Me ha costado", never above 20; "Mantener" 4 sets (6 from 65); "Priorizar" +2 per block up to 20, at most 3 groups, freed sets first; cut and regain only redistribute freed sets; minimal ignores priorities; adherence < 80 % → no increase and the "fewer days / shorter sessions" option becomes eligible; a stalled lift → eligible rep-range change within the intention's range or a validated alternative of the same pattern (T-179 graph); a calorie change outside the safe band (T-193 stores it) is offered here. `blockRules` takes the per-group targets and the exercise overrides. (5) **Model call `block_review`** (Sonnet 5.5, streamed, budget-checked): input = review inputs, answers, the free-text block, eligible options; output = chosen option ids and Spanish reasons; validators: only eligible ids, grounding (no feature or figure outside the inputs), retry once; then the existing `plan_nutrition` and `plan_block` calls build the next plan with the chosen phase and targets (separate streamed calls, 150 s limit as today). (6) **RPCs:** `save_block_review_answers`, `decide_block_phase(p_review, p_accept)`; the next plan is a normal proposal; `save_coach_block` of the next block supersedes the previous plan and ends its block; Q47: no automatic change applies past an ended block (flag read by T-192/T-193). (7) **Core:** review models, repository (inputs, answers, start review with progress, decide phase). (8) **Docs:** `03`, `04` new §9.12 "End-of-block review" with worked examples (3 deficit blocks → maintenance; stalled squat +2 sets; "Mantener" legs 4 sets; 4th priority refused), `02` (new kind), `07` §11 cost row.
**Audit fixes folded in:** `blockRules.ts:288-379` always 10/8/6 sets with no previous block or phase → per-group targets carried over from the previous block and changed by the review (above); adherence per week (`coach_adherence_deload.sql:103`) gains the 80 % "high" flag used by the review; `planBlock.ts:230-270` and `planNutrition.ts` prompts carry only intention, experience, place and equipment → they also read `coach_summary` with the free-text block (T-185), within the 150 s limit; **grounding validator** (`07` §1, ADR-87): one shared server check (feature list + summary figures) applied to the reasons of `plan_nutrition`, `plan_block` and `block_review` (today `validators.ts`/`blockValidators.ts` check structure only), reused by T-166 and T-169. The review input "discomforts" lists **unresolved** level 4-5 discomforts (`07` §7: unresolved until "Ya no me molesta" or return step 3, whatever their age) and open levels 1-3; T-194 owns the exclusion. The review runs after week 4; when week 4 was a deload, the e1RM trend uses weeks 1-3 and adherence counts week 4 normally (`07` §3 "Deload"). The next block keeps the user's own swaps (row 11 of `07` §12.8: the model does not re-propose a swapped-out exercise) and applies the age rules from the next block after turning 65 (row 12).

**Acceptance criteria:**
- [ ] The inputs RPC returns every US-83 figure for a fixture block; `block_ended` follows the T-189 plan weeks; `can_review_early` is true once a block is saved.
- [ ] Every trigger of `07` §12.3 produces its eligible phase with its reason code, and none fires mid-block; "Seguir como estoy" keeps the phase and the same option is eligible at the next review.
- [ ] Volume: stalled group +2 only under all four conditions, never above 20; "Me ha costado" or adherence 60 % → no group increases; "Mantener" 4 (6 from 65); "Priorizar" +2 up to 20, a 4th priority refused (`22023`); cut and regain only redistribute.
- [ ] The review call picks only eligible ids (validator rejects others, one retry); a text naming a missing feature is rejected; the next plan is built with the chosen phase and targets; the review answer appears in the free-text block.
- [ ] Saving the next block supersedes the previous plan; leaving creates nothing; an ended block without a review gets no automatic change.
- [ ] The plan and block prompts include the summary and the free-text block; a nutrition, block or review reason that names a feature or figure that does not exist is rejected (one retry, then `coach_invalid_output`); every T-161/T-163/T-182 regression still passes.
- [ ] pgTAP: RLS, isolation, cascade; `deno test` for the triggers, volume rules and the review validator with a fake client.

**Host tests:** pgTAP `coach_block_reviews.test.sql` (inputs fixture, triggers table-driven, priorities and limits, supersede, ended-block flag, isolation); `deno test` (`blockReview.test.ts`: eligible options, validator, grounding, retry, budget refusal; `blockRules` with per-group targets and overrides; regression of every `04` §9 rule); Kotlin DTO and repository tests.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (before T-191).

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5. About 18 files (developer slices if the planner counts more than ~20).
**Dependencies:** T-189 merged and deployed; T-200 merged (no regain phase); E13 Q47 (recommendation used); the fitness-scientist's `07` §12.3 clean-up without regain.
**Files likely touched:** `supabase/migrations/<ts>_coach_block_reviews.sql`, `supabase/tests/{coach_block_reviews,delete_account_cascade}.test.sql`, `supabase/functions/coach/{blockReview,phaseTriggers,grounding,blockRules,planBlock,planNutrition,validators,blockValidators,schemas,router}.ts` (+ tests), `shared/…/domain/model/BlockReview.kt`, `shared/…/data/{remote/BlockReviewDto,repository/SupabaseCoachRepository}.kt` (+ tests), `planning/{02-architecture,03-data-model,04-business-rules,07-coach-design}.md` (§11 only)
**Estimated scope:** L

---

## T-191: End-of-block review screen and the next block (US-83, US-84 UI) · L

> **Amended 2026-11-03 (T-202 replan):** the proposal hands off to the T-206 block preview (target RPE, supersets, "Qué cambia respecto al bloque anterior"), not the T-164 one; "Bloque nuevo" opens the review; the review's next block starts from the current block's folder (no extra start choice here).

**Story:** US-83, US-84 (UI) · **Flow:** core + UI · **Size:** L · **Scenarios:** US-83 every scenario (UI); US-84 every scenario (UI)

**Description:** (1) **Core:** `BlockReviewViewModel` (load inputs, answer, priorities with the 3-limit, start review with streamed progress, phase decision, hand-off to the block preview, leave without effects, offline, limit), `TrainerViewModel` (ended-block card, early-review entry). (2) **UI — templates and patterns:** Trainer root: an `AppCard` "Tu bloque ha terminado: revísalo" with a `PrimaryButton` on top of the root when the block ended (pending cards first, Q20 A); "Tu plan": a `NavigationRow` "Bloque nuevo" for an early review. **Label clash (needs ux-reviewer):** `07` §12.8 reserves "Revisar el bloque" for the life-change offer (T-197), whose choice "Bloque nuevo" is this same early review, so the review entry uses "Bloque nuevo" (R-35, one label per concept); ux-reviewer confirms both labels and the card's button copy in this task's plan pass. **Review** = an `AppScreen` with a pinned footer `PrimaryButton` "Preparar el siguiente bloque" (R-09; ux-reviewer decides whether unsent answers make it an editor with the R-14 discard dialog): sections "Lo que hiciste" (done vs planned per routine, reusing the `PlanWeeksSection` rows), "Tus básicos" (e1RM start → end per main lift with `TrendPill`), "Peso" (trend vs the phase's rate, `WeeklyRatePill`), "Diario" (complete days), each with a `HelpButton` (R-46); "¿Cómo te ha ido?" as a single-choice control of three ("Bien", "Me ha costado", "Me ha sobrado"; ux-reviewer picks the existing segmented or chip control, R-47) plus an optional `AppTextField`; "Prioridades (opcional)": one row per muscle group opening an `OptionPickerSheet` ("Priorizar", "Normal", "Mantener"; R-12), the 3-limit message inline. Footer → streamed progress (T-162) → **proposal**: the phase proposal as a `CoachProposalCard` with "Aceptar" / "Seguir como estoy" (new glossary entry) and the reasons; then the existing **block preview** (T-164/T-179/T-183) whose "Series por semana" shows before → after per group; "Guardar bloque" starts the new block. The phase row of T-188 is reused for the proposed phase. Limit reached → US-76 message; offline → hint. (3) `05` §10 glossary ("Bloque nuevo", "¿Cómo te ha ido?", "Priorizar", "Mantener", "Seguir como estoy"), §6.2 Trainer root order.

**Acceptance criteria:**
- [ ] An ended block shows the card on top of Trainer; "Bloque nuevo" in "Tu plan" opens the review early.
- [ ] The review shows done vs planned, the main lifts' e1RM start → end, weight vs the phase's rate and the diary days, each with its help.
- [ ] Choosing an answer, writing a text, prioritising two groups and keeping one, then "Preparar el siguiente bloque" shows progress and the proposal; a fourth priority shows the limit message.
- [ ] A phase proposal offers "Aceptar" and "Seguir como estoy"; either leads to the block preview with sets before → after; "Guardar bloque" starts the new block and "Tu plan" shows it; leaving at any point changes nothing.
- [ ] The review with its answers and priorities, the proposal and the preview survive background and process death (ADR-40).

**Host tests:** `BlockReviewViewModel` tests (load, answers, priority limit, progress, phase accept/keep, hand-off, leave, limit, offline); `TrainerViewModel` tests (ended card, early entry); Robolectric (sections and help, three-answer control, priority rows and sheet, limit text, proposal card labels, before → after sets, root order); restoration tests.

**Verification:**
- [ ] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green. No paid call in CI.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 16 files.
**Dependencies:** T-190 merged and deployed.
**Files likely touched:** `feature/trainer/{BlockReviewScreen,BlockReviewViewModel,BlockReviewUiState,PriorityRow}.kt`, `feature/trainer/{TrainerScreen,TrainerViewModel,BlockPreviewScreen,BlockPreviewViewModel}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** L

---

### ~~Checkpoint E13-E~~ → E14 backlog, not a gate (2026-11-03) — Your block review: what happened, the next block and a new phase (6 items + leave and come back) *(proposed by the epic-planner, 2026-11-01; to be re-proposed in the E14 re-plan)*
Journey: review the block with the coach, say how it went and what to prioritise, see a phase change when your data calls for one, and start the next block; check that a week off does not eat your block. Why here: right after T-191, before the automatic changes (T-192, T-193) act inside blocks. T-189 and T-190 merge with no checkpoint after them.
- [ ] T-189…T-191 merged, CI green, `deploy-supabase` green (data intact; the current block's links kept), every reported bug fixed.
- [ ] (E14 backlog, not a gate) Install the APK and try (a review and the next block are paid calls of about 0,1-0,5 € together):
  1. Review: "Tu plan" → "Bloque nuevo" (early review; label confirmed by ux-reviewer in T-191): sessions done against planned, your main lifts' e1RM from start to now, your weight against the phase's rate and your diary days, each with its `?`.
  2. "¿Cómo te ha ido?": pick an answer and write a line; after preparing the block, the line shows in "Lo que el entrenador sabe de ti".
  3. Priorities: "Priorizar" two muscle groups and "Mantener" one; a fourth "Priorizar" is refused with the limit message; in the preview each group shows its weekly sets before → after.
  4. Phase: if your data calls for it (for example 3 blocks of "Definición"; "Recuperar" was removed 2026-11-02) the proposal shows the new phase with its reason and "Aceptar" / "Seguir como estoy"; otherwise the phase stays (each trigger is covered by host tests).
  5. Next block: from the preview, "Guardar bloque": a new folder in Entreno, "Tu plan" shows the new block and phase, the old block ends; leave the review or the preview instead: nothing changes.
  6. A week off: if you skip a whole calendar week, Trainer keeps the same block week, says it is paused and moves the end date (host tests if your data does not show it).
  - Leave the app and come back on the review with an answer and a priority set but not sent, and once on the proposal: the same tab, screen and draft are restored.
  - Out of scope: automatic loads, calories, deloads and injury (E13-F); report, history, chat (E13-C).

---

## T-192: Progression applies on its own inside a block, with "Deshacer" (US-70 amended, US-85; US-88 return loads) · L

> **Amended 2026-11-03 (T-202 replan, ADR-94):** block routines are the user's once saved: the progression applies to them as he left them (edited sets or swapped exercises included, matched by exercise and rep range); a plan whose block was removed (T-203) gets no automatic change.

**Story:** US-70 amended, US-85 (progression part), US-88 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** US-70 Outside a block the suggestion stays a suggestion; Inside a block the progression applies on its own; Undoing an automatic change; Coming back after 14 days or more; every other US-70 scenario (regression) · US-85 No mode to choose; Every automatic change is visible (loads); Outside a block

**Description:** (1) **Backend (`04` §4.14 amended, E13 Q44 recommendation A):** a `coach_changes` table (user, plan, kind `load | calories | deload | return_step`, target (routine exercise, plan week…), `before` / `after` jsonb, reason code, `applied_at`, `undone_at`; owner RLS, cascade; built here, reused by T-193/T-194). When a session of a routine of an accepted, not-ended block is finished (same transaction as the finish path, after ADR-70's "Actualizar automáticamente", planner documents the order), each exercise with a rep range gets the `progression_suggestions` result applied to the planned sets of **every block routine that holds that exercise with the same rep range** (`07` §3 "Where it lives": a routine serves every week, so the new weight is the baseline from the next session on; on a raise the planned reps go to the bottom of the range; on the 5 % drop the weight falls by the rounded step) through one RPC that reuses the `save_routine` validation (no parallel write path), storing a `load` change; `undo_coach_change(p_id)` restores the stored `before` values and marks it undone (later sessions recompute from there). Return rule (`07` §12.2): after 14 or more days without a completed session, the first session of each exercise keeps its load (no raise) and one failed session (instead of two) triggers the drop. Outside a block nothing is written (today's suggestion). **Audit fix (`07` §3 "Where it lives and who computes it"):** today the weight increment is computed on the client (`ProgressionWeight.kt:19-38`, plate and dumbbell settings) and `progression.sql:344-463` returns only the rule, so the server cannot apply a load; the **server** now computes the decision and the step from the user's plates and handle (`04` §4.9; the planner makes those settings readable by the RPC), one implementation shared by the live-session suggestion and the automatic change (the client formatter stays display-only); "Deshacer" restores the planned weight and reps from before the coach's write (this settles E13 Q44 as A). A returned exercise (T-194) needs one session without discomfort before it progresses (`07` §9). `progression.sql:437` (two failed sessions always) gains the 14-day gap rule. (2) **Core:** `CoachChange` model, repository (list this week's changes, undo), `LiveSessionViewModel` (applied change per exercise; the suggestion reads the server's weight), session summary. (3) **UI — templates and patterns:** build **`CoachChangeRow`** in `designsystem/` (what changed, the reason in one sentence, a text action "Deshacer"; never colour alone; reused by T-193, T-194); the live session's exercise block (T-172 structure) shows the applied change in place of the suggestion line inside a block ("Subimos a 62,5 kg: la última vez hiciste 12 repeticiones en todas las series." + "Deshacer"); outside a block the T-159 suggestion line is unchanged; the session summary lists the loads changed for next time; the routine editor shows the new planned values (marker text by ux-reviewer). New glossary entry **"Deshacer"** = undo an automatic coach change (R-20 still forbids it for deletion; G-guard allow-list if `check-ux-conventions.sh` checks the word).

**Acceptance criteria:**
- [ ] Inside an accepted block, 8, 8, 8 at RPE ≤ 8 on 6-8 → after finishing, the routine's next planned sets are the next increment × 6 and a `load` change is stored with its reason; the live session of the next workout shows them with "Deshacer".
- [ ] "Deshacer" restores the previous planned values and marks the change undone; nothing else changes.
- [ ] Most sets under the bottom in two sessions → 5 % lower applied; after a 14-day gap the first session keeps the load and one failed session applies the drop.
- [ ] Discomfort rules unchanged (levels 1-3 no raise, 4-5 nothing applied); outside a block only the suggestion shows and the routine is untouched (with and without "Actualizar automáticamente").
- [ ] No setting chooses how much the coach decides.
- [ ] The next weight (raise and drop, plates, dumbbell handles, machines) is computed on the server and the live-session suggestion shows the same number as the applied change (T-159 increment cases moved to pgTAP).
- [ ] The live session with an applied change and the summary survive background and process death (ADR-40).

**Host tests:** pgTAP (`coach_changes` RLS/isolation/cascade, apply on finish inside and outside a block, ended block, undo, 14-day gap, discomfort levels, ADR-70 order, `save_routine` path); Kotlin repository/ViewModel tests (applied change, undo, outside block); Robolectric (`CoachChangeRow`, live exercise block, summary list, routine editor values); restoration tests.

**Verification:**
- [ ] pgTAP, lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 16 files.
**Dependencies:** Checkpoint E13-E approved (open 👤 gate); E13 Q44 (recommendation A used; the user may override at the plan review).
**Files likely touched:** `supabase/migrations/<ts>_coach_changes.sql`, `supabase/migrations/<ts>_coach_auto_progression.sql`, `supabase/tests/{coach_changes,coach_progression,delete_account_cascade}.test.sql`, `shared/…` (`CoachChange`, repository, DTO), `designsystem/CoachChangeRow.kt`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen,SessionSummaryScreen}.kt`, `feature/routines/RoutineBuilderScreen.kt` (marker), `shared/…/ProgressionWeight.kt` (moved server-side or reduced to display), strings, `scripts/check-ux-conventions.sh` (if needed), `planning/{03-data-model,04-business-rules,05-design-system}.md`, tests
**Estimated scope:** L

---

## T-193: Calories and deloads on their own; scheduled deloads; data cleaning (US-69 amended, US-75 amended, US-85, US-86) · L

> **Amended 2026-11-02 (user: "Recuperar" removed, T-200):** "regain never below the expenditure in use" is void in the description and criteria below; the other band rules stay.

**Story:** US-69 amended, US-75 amended, US-85 (calories), US-86 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** US-69 Changing the calories within a block; Not enough data, no calorie change; A partial day is not counted; Automatic changes stay inside the safe band · US-75 every scenario · US-85 Undoing a calorie change; Changes that need my OK · US-86 every scenario

**Description:** (1) **Calories (`04` §8.5 amended, `07` §2, §4):** data cleaning before any change (days under 40 % of the median of the 28-day logged days excluded as partial from every count; a week with fewer than 5 complete diary days or fewer than 3 weigh-ins changes nothing and stores the reason `missing_records`); `evaluate_coach_calorie_change` applies the change on its own inside the safe band (cumulative ±10 % of the block's accepted calories, one change ≤ 10 %, 14 days apart, floor, 500 kcal deficit cap, rate range, regain never below the expenditure in use, maintenance, recomposition and minimal within ±0.25 %/week of it), writes `nutrition_goals` and a `calories` row of `coach_changes` (T-192); `undo_coach_change` restores the previous target, takes the change out of the band and restarts the 14-day clock; after two undos in one block, no more automatic calorie changes until the review; a change outside the band is stored for the review (T-190 reads it), not applied; no change past an ended block (Q47). (2) **Deloads (`04` §9.10 amended, `07` §3):** the triggered deload applies on its own (a `deload` change); **scheduled** in week 4 when no deload ran in the previous 7 training weeks, not in the minimal phase; at most one deload per block, always week 4; `cancel_coach_deload` and `start_coach_deload` (next plan week); plan weeks per T-189. (3) **Retire accept-first:** the pending `calorie_change` / `deload` recommendations and their "Aceptar" / "Descartar" cards (T-162, T-165) are removed for these kinds (existing pending rows closed by the migration; planner records how); `CoachProposalCard` stays only for proposals that need acceptance (phase, chat). (4) **UI — templates and patterns:** Trainer root top: applied changes as `CoachChangeRow`s (T-192): "Bajamos 150 kcal: llevas 2 semanas perdiendo menos de lo previsto." + "Deshacer"; "Semana de descarga: mismos pesos, menos series. Llevas 3 semanas sin mejorar en los básicos." (scheduled: "Llevas 7 semanas entrenando sin descarga.") + "Cancelar descarga"; the missing-records sentence as an `InlineNote` when a week was held; "Tu plan": `NavigationRow` or secondary action "Hacer descarga la próxima semana" (ux-reviewer picks; hidden during a deload); the live session's deload label unchanged (T-165). Diario reflects an applied or undone change. (5) **Docs:** `03`, `04` §8.5, §9.10 detail with worked examples (cumulative band, missing records, scheduled week 4), `05` §10 ("Cancelar descarga", "Hacer descarga la próxima semana").
**Audit fixes folded in:** `evaluate_coach_calorie_change` (`20261208100000_coach_plans.sql:397-483`) creates a pending recommendation, bounds the adjustment to −1 000/+750 (:469) and counts 14 days from the last *decided* change → auto-apply, the safe band and the 500 cap; the partial-day rule of `coach_intake` (`20261214100000_coach_realistic_expenditure.sql:63-89`: under 40 % of the median excluded from the anchor only) → the same 40 %-of-median day is also excluded from `logged_days`, the 70 % reliability and the weekly gate (`07` §2, one rule app-wide); reference expenditure = the one in use at the change: regain never below it, maintenance, recomposition and minimal within ±0.25 %/week of it (so a maintenance user gaining too fast does get a decrease down to that range); undo: an undone change does not count towards the ±10 % band and restarts the 14-day clock, and after two undos in one block no more automatic calorie changes until the review; `20261211100000_coach_adherence_deload.sql:112-253` (pending deload, e1RM trigger only) → auto, scheduled, cancel and start; **one deload per block, always week 4** (trigger and schedule land on the same week), counted in training weeks, scheduled when none ran in the previous 7 training weeks, copy "Llevas 7 semanas entrenando sin descarga."; it may coincide with a diet-break week (sets vs calories, no conflict); `strings.xml:1802-1819` accept/discard copy for calorie change and deload removed with `decide_coach_deload` and the calorie use of `decide_recommendation`. `edited_by_user` still disables automatic calorie changes (not in `07`; kept, recorded in `04` §8.5).

**Acceptance criteria:**
- [ ] Two weeks outside the trend with clean data → the calories change on their own (≤ 10 %), Diario shows the new target, Trainer shows the reason and "Deshacer"; "Deshacer" restores it, the change leaves the band and no automatic change follows for 14 days; a second undo in the block stops automatic calorie changes until the review.
- [ ] A week with 4 complete diary days or 2 weigh-ins → no change and the missing-records sentence; a day under 40 % of the 28-day median is excluded from the anchor, the logged-day counts and the weekly gate.
- [ ] After +10 % net in a block, a further change is not applied and is waiting for the review; regain never goes below the expenditure in use; a maintenance user gaining too fast gets a decrease only down to −0.25 %/week of it; the 500 cap and the floor hold.
- [ ] No main-lift improvement in 3 plan weeks → week 4 is a deload on its own with "Cancelar descarga"; cancelling restores the week; week 4 is scheduled when no deload ran in the previous 7 training weeks (paused weeks not counted), never in "Mínimo"; a block never has two deloads; a diet-break week 4 keeps maintenance calories during a deload; "Hacer descarga la próxima semana" marks next week.
- [ ] No "Aceptar" / "Descartar" card remains for calorie changes or deloads; no automatic change after an ended block.
- [ ] Trainer with the change rows survives background and process death (ADR-40).

**Host tests:** pgTAP (data cleaning, band boundaries incl. cumulative and phase rules, undo and 14-day block, outside-band storage, ended block, deload triggered/scheduled/cancel/start with T-189 weeks, migration of pending rows, isolation); ViewModel tests (rows, undo, cancel, start, missing-records note); Robolectric (rows with reasons and actions, note, "Hacer descarga la próxima semana", no proposal card for these kinds); restoration tests.

**Verification:**
- [ ] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** T-192 merged and deployed (`coach_changes`, `CoachChangeRow`).
**Files likely touched:** `supabase/migrations/<ts>_coach_auto_calories_deload.sql`, `supabase/tests/{coach_plans,coach_deload,coach_changes}.test.sql`, `supabase/functions/coach/nutritionRules.ts` (if the band math lives there; + tests), `shared/…` (change kinds, repository), `feature/trainer/{TrainerScreen,TrainerViewModel,TrainerPlanSection}.kt`, removal of the calorie/deload `CoachProposalCard` uses, strings, `planning/{03-data-model,04-business-rules,05-design-system}.md`, tests
**Estimated scope:** L

---

## T-194: Training around an injury, graded return and the persistent-discomfort note (US-87, US-90) · L

> **Amended 2026-11-02 (Checkpoint E13-D feedback, T-198):** the intake has no injured area any more, so training around an injury comes **only from logged discomforts**: the next block excludes every exercise with an unresolved level 4-5 discomfort (data) and refills the slot from validated alternatives; the model no longer marks candidates that "load the area", and the intake-area part of (1), the "Para conocerte" edit that offers "Revisar el bloque" and the first acceptance criterion are void (struck below). The "Fuera del bloque por tu lesión" section lists the discomfort exclusions with "Volver a incluir". The free text may mention an injury: it reaches the block request as context only (ADR-87; whether the model may use it to prefer exercises is E13 Q52 for the fitness-scientist, `07` §7). US-87 "Training around the injured area" is rewritten accordingly.

> **Amended 2026-11-03 (T-202 replan, ADR-93):** the SQL helper "unresolved level 4-5" and the exclusion of those exercises from the model input and the block (checker H6) are built in **T-205**; this task reuses them, lists the excluded exercises under "Fuera del bloque por tu lesión" with "Volver a incluir" (put-back edits accepted by `save_coach_block` and re-checked by the checker with that exercise allowed), and keeps the graded return, the swap offer and the health note. The model picks replacements freely within the checker (no "validated alternatives of the same pattern" list).

**Story:** US-87, US-90 (row; the report side is T-166) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** US-87 every scenario · US-90 Open for 14 days (row); Three sessions at a severe level; Dismissing the note; Milder levels; No generic warnings

**Description:** (1) **Train around (`07` §7, ADR-89):** the next block (first block or after a review) excludes every exercise with an **unresolved** level 4-5 discomfort (`07` §7: unresolved, whatever its age, until "Ya no me molesta" or return step 3; the 7-day "open" window governs only the load block of levels 1-3) and, when the intake has an injured area (T-185), the model marks which candidates load that area (validated: catalogue ids among the slot's candidates, listed in the plan JSON `excluded[]` with a reason); slots refill from validated alternatives of the same pattern, never a flagged exercise; `save_coach_block` accepts "Volver a incluir" edits (an excluded exercise put back on its slot). (2) **Graded return:** `coach_return_steps` (user, exercise, step 1-3, substitute exercise, `started_on`, `last_load_kg` before the discomfort, source discomfort), owner RLS, cascade; logging a level 4-5 discomfort on an exercise of an accepted block makes the Trainer tab **offer** to swap it for the rest of the block (`07` §12.8 row 7: same pattern, never a flagged exercise, a rule applied on the user's tap, no model call; this replaces the automatic swap of the E13 Q45 recommendation); accepting the offer swaps it in the block's routines as a `return_step` change of `coach_changes` (with "Deshacer") and starts step 1; the substitute's load comes from the user's own history or, without it, no planned weight (found by RPE 7-8 at the bottom of the range; never converted, `07` §12.8); an injured-area edit in "Para conocerte" during a block offers "Revisar el bloque" (T-196); `advance_return_step(p_exercise)` allowed after ≥ 7 days in the step with no new discomfort on the exercise; step 2 puts the original back at `last_load_kg` with a routine-exercise note "Rango sin molestia" (a new `note_kind` option or the planner's equivalent, ADR-82), step 3 removes the note and normal progression resumes; a new discomfort at the same level or higher sends it back one step, level 4-5 to step 1. (3) **Health note:** `coach_health_notes` (discomfort, `triggered_at`, `dismissed_at`); trigger = a level 4-5 discomfort still **unresolved** 14 days after logging, or logged at level 4-5 on the same exercise in 3 sessions; once per discomfort; RPCs list and dismiss; exposed for the report (T-166). Levels and dates only, never text (ADR-87). (4) **UI — templates and patterns:** the discomfort row of the live session and the exercise details (T-172 structure): "Vuelta progresiva · paso 1 de 3" and, when offered, the text action "Pasar al siguiente paso"; the health note as an `InlineNote` with a dismiss action on that row; an unresolved level 4-5 discomfort stays visible on the exercise with "Ya no me molesta" whatever its age; the **level 4-5 swap offer** on the Trainer tab (pattern and copy **need ux-reviewer**: e.g. a card "Cambiar <ejercicio> por <sustituto> el resto del bloque" with an accept action), and the applied swap shown with `CoachChangeRow` (T-192) and "Deshacer"; the block preview (T-164) gets a section "Fuera del bloque por tu lesión" listing excluded exercises with their reason and a row action "Volver a incluir" (R-19 row action; ux-reviewer finalises copy). (5) **Docs:** `03`, `04` §4.12 detail, `05` §10 ("Vuelta progresiva", "Pasar al siguiente paso", "Rango sin molestia", "Volver a incluir", "Fuera del bloque por tu lesión").
**Audit fixes folded in:** `candidates.ts` has no discomfort or injury filter and `planBlock.ts` gives the model no summary (T-190 adds the summary; this task adds the exclusions); the "open" discomfort of `04` §4.12 and the `disc` CTE of `coach_summary` (`20261217100100_coach_summary_cardio.sql:93-99`: unresolved and under 7 days) cannot reach the 14-day note or a level 4-5 "open at the review": the "unresolved level 4-5" definition of `07` §7 is implemented once (SQL helper) and used by the summary (the `disc` CTE lists unresolved level 4-5 whatever their age, plus open levels 1-3), the note trigger, the review input (T-190) and the exclusion; `04` §4.12 detail updated; "one session without discomfort before a returned exercise progresses" (`07` §9) is enforced in the progression of T-192.

**Acceptance criteria:**
- [ ] ~~Intake with a shoulder injury → the block leaves out the exercises the model marks as loading it, lists them with "Volver a incluir"; putting one back saves it in its slot.~~ *(void 2026-11-02: no injured area)* An exercise with an unresolved level 4-5 discomfort is listed under "Fuera del bloque por tu lesión" with "Volver a incluir"; putting it back saves it in its slot.
- [ ] A level 4-5 exercise is never in the next block and no flagged exercise is chosen as a replacement.
- [ ] A level 4 discomfort on a block exercise makes Trainer offer the swap; nothing changes until the user accepts; accepting swaps it in every block routine (step 1) with "Deshacer"; "Pasar al siguiente paso" appears only after 7 days with no new discomfort; step 2 restores the last load with "Rango sin molestia"; step 3 removes the note and resolves the discomfort; regressions per the rules.
- [ ] A level 4-5 discomfort logged 20 days ago and never resolved is still unresolved (summary, exclusion, note); a level 2 one expires its load block after 7 days as before.
- [ ] The note shows once at 14 days unresolved or after 3 level 4-5 sessions, dismisses for good, never for levels 1-3, never from text.
- [ ] No generic health disclaimer anywhere (T-188 sweep test still passes).
- [ ] The preview with the excluded section, the discomfort row with the step and the note survive background and process death (ADR-40).

**Host tests:** `deno test` (exclusion by level and area, validated replacements, put-back edits, validator); pgTAP (`coach_return_steps` transitions and timing, swap via `coach_changes` and undo, note triggers 13/14 days and 2/3 sessions, dismiss, isolation, cascade); ViewModel and Robolectric tests (step text, next-step action, note and dismiss, excluded section, "Volver a incluir"); restoration tests.

**Verification:**
- [ ] `deno test`, pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 18 files (developer slices if the planner counts more than ~20).
**Dependencies:** T-193 merged and deployed; E13 Q45 settled by `07` §12.8 row 7 (offered swap, user's tap); E13 Q52 answered by the fitness-scientist in `07` §7 (training around an injury without an injured area).
**Files likely touched:** `supabase/migrations/<ts>_coach_return_steps.sql`, `supabase/migrations/<ts>_coach_health_notes.sql`, `supabase/tests/{coach_return_steps,coach_health_notes,save_coach_block,delete_account_cascade}.test.sql`, `supabase/functions/coach/{blockRules,candidates,planBlock,blockValidators}.ts` (+ tests), `shared/…` (return step, health note, repository), `feature/workout/{LiveSessionScreen,LiveSessionViewModel}.kt` (discomfort row), `feature/exercises/ExerciseDetailScreen.kt` (discomfort row), the `exercise_similar_*` strings, `feature/trainer/{BlockPreviewScreen,BlockPreviewViewModel}.kt`, strings, `planning/{03-data-model,04-business-rules,05-design-system}.md`, tests
**Estimated scope:** L

---

### ~~Checkpoint E13-F~~ → E14 backlog, not a gate (2026-11-03) — The coach adjusts on its own, and trains around an injury (6 items + leave and come back) *(proposed by the epic-planner, 2026-11-01; to be re-proposed in the E14 re-plan)*
Journey: train inside your block and see the coach move your loads, calories and deloads by itself, each with its reason and a way back; tell it about an injury and see it train around it and bring the exercise back step by step. Why here: right after T-194, before the weekly report (T-166) lists these changes. T-192 and T-193 merge with no checkpoint after them.
- [ ] T-192…T-194 merged, CI green, `deploy-supabase` green (data intact), every reported bug fixed.
- [ ] (E14 backlog, not a gate) Install the APK and try:
  1. Loads on their own: in a block routine reach the top of a range at RPE 8 or less; after finishing, the next session shows the new weight with its reason and "Deshacer"; tap it: back to the old weight; in a routine outside the block it is only a suggestion, as before.
  2. Calories on their own: if your data allows it, Trainer shows a calorie change with its reason and "Deshacer" (Diario follows it, and "Deshacer" puts it back), or "Esta semana faltan registros: no cambiamos tus calorías." when the week is thin (host tests cover the band).
  3. Deloads: "Hacer descarga la próxima semana" in "Tu plan": next week is a deload (same weights, fewer sets in the live session); "Cancelar descarga" puts it back; a scheduled week-4 deload says "Llevas 7 semanas entrenando sin descarga." when it applies.
  4. Injury: with a level 4 or 5 discomfort still open on an exercise, "Crear bloque" (or the next block): that exercise is under "Fuera del bloque por tu lesión" and a similar one you have not flagged takes its slot; "Volver a incluir" puts it back. *(Rewritten 2026-11-02: the intake no longer has an injured area, T-198.)*
  5. Graded return: log a level 4 discomfort on a block exercise: Trainer offers to swap it for a similar one for the rest of the block; nothing changes until you accept; after accepting, the routine has the substitute with "Deshacer" and the discomfort row shows "Vuelta progresiva · paso 1 de 3"; "Pasar al siguiente paso" appears after a week without new discomfort (host tests cover the timing).
  6. Unresolved and no generic warnings: the level 4 discomfort stays on the exercise with "Ya no me molesta" after 7 days; no screen shows a medical disclaimer; the note about a persisting discomfort appears only after 14 days unresolved and can be dismissed (host tests cover the timing).
  - Leave the app and come back in the live session with an applied change shown, and once on "Tu plan" right after "Cancelar descarga": the same tab, screen and state are restored.
  - Out of scope: life changes, the weekly report, history and chat (E13-C).

---

## T-196: Life changes mid-block: "Revisar el bloque", adapt, new block or "Solo unos días" (US-91, US-92 backend) · L

**Story:** US-91, US-92 (data side) · **Flow:** backend + core · **Size:** L · **Scenarios:** US-91 every scenario (data); US-92 Intention change offers an early review; A new exercise starts from my own weights; My swaps stay in the next block; Turning 65; A goal reached changes nothing on its own (data); Illness is only missed days (regression)

> **Amended 2026-11-03 (T-202 replan; pending E13 Q55):** the days and session-length re-split of "Adaptar el bloque" depended on the server split table that ADR-93 supersedes. Recommended (Q55): "Adaptar el bloque" only for a place or equipment change (the deterministic swap rule, free); a days or session-length change offers "Bloque nuevo" (the T-190 review, from the current block's folder) and "Solo unos días"; row 11's swap memory is removed (void scenario "My swaps stay"). The planner re-sizes T-196/T-197 after the user's answer (they may merge into one L task).

*(Amended 2026-11-02, T-198: the intake has no injured area, so the "Para conocerte" trigger is void; a new injury mid-block is a logged level 4-5 discomfort, handled by T-194's offered swap. US-91 "A changed injured area" is marked void.)*
**Description (`07` §12.8, user-approved 2026-11-01):** (1) **Trigger:** saving place, equipment, days per week or session length in "Mi entrenamiento" ~~(or the injured area in "Para conocerte")~~ during an accepted, not-ended block sets a pending `block_review_offer` (reason codes per row 1-3, 5, 7; one at a time; cleared when acted on or when the block ends); an intention change sets an early-review offer (row 4: "Bloque nuevo" only; the current block runs unchanged until a new one is accepted). (2) **"Adaptar el bloque"** (rule, no model call, no cost): RPC `adapt_coach_block_preview(p_plan)` returns the adapted block keeping the block week, the phase, the sets per muscle group and the rep ranges: an exercise the new equipment cannot do is swapped to a candidate of the same muscle group and movement pattern in the `07` §3 order (T-179), else any candidate of the same group, else the slot is dropped and flagged; the cardio exercise re-picked by the §3 default ranking with the same minutes; days change: re-split by `04` §9.2 for the new days keeping sets per group where slots allow (dropped sets flagged), weekly cardio minutes re-split (`04` §9.11), at 1 day the next review proposes the minimal phase; session length: slots recomputed (`04` §9.3), sets kept where they fit, else cut evenly and flagged; `apply_coach_block_adaptation(p_plan, p_preview_hash)` rewrites the block's routines through `save_routine` (no parallel path) as one `adaptation` change of `coach_changes` with "Deshacer"; loads: same exercise and range keep their planned load; a new exercise takes the user's last working weight on it, else no planned weight (found by RPE 7-8 at the bottom of the range), never converted. (3) **"Bloque nuevo":** opens the T-190 early review (paid, needs acceptance). (4) **"Solo unos días":** `start_temporary_adaptation(p_plan, p_end_date, p_place, p_equipment, p_days)` stores a temporary overlay: the swaps (same rule as adapt) apply only to sessions until the end date; fewer days use the minimal-phase routine (1 full-body session with 1 set of the main lifts; 2 and 2 from 65); the original routines and their loads return untouched after the end date (paused weeks do not count, T-189); phase and calories unchanged; no model call. The planner picks overlay routines in a hidden folder vs per-session substitutions and records it in `03`. (5) **Other rows (`07` §12.8):** row 8 goal reached: a factual flag for the weekly report, nothing changes; row 10: `has_week_data(week)` (any session, weigh-in or diary day) for T-166; row 11: user swaps (preview or routine) are recorded and the next block's candidates exclude a swapped-out exercise for that slot (read by T-190); row 12: age rules (minimal 2 × 2, "Mantener" 6) apply from the next block after the 65th birthday, never mid-block; row 6 illness: no rule (regression only). (6) **Core:** models and repository (offer, preview, apply, temporary adaptation, end it). (7) **Docs:** `03`, `04` new §9.13 "Life changes" with worked examples (barbell → dumbbells swap; 4 → 3 days re-split; 60 → 45 min; travel 5 days at 1 session), `02`.

**Acceptance criteria:**
- [ ] Saving new equipment during a block creates one offer; outside a block or after it ends, none.
- [ ] Adapt with "Mancuernas" + "Peso corporal" replacing a gym: every barbell or machine exercise becomes a same-group same-pattern candidate (else same group, else dropped and flagged); week, phase, sets per group and ranges unchanged; cardio re-picked with the same minutes; no `coach_usage` row.
- [ ] 4 → 3 days re-splits per `04` §9.2 keeping sets per group where slots allow; 60 → 45 min recomputes slots and flags any cut; 1 day sets the minimal proposal for the next review.
- [ ] Applying writes the routines through `save_routine` as one change with "Deshacer", which restores the previous routines; the same exercise keeps its load; a new one takes the user's last working weight or none (never converted).
- [ ] "Solo unos días" until a date: sessions until then use the swaps (minimal routine with fewer days); after the date the original routines and loads are back; phase and calories unchanged; no model call.
- [ ] An intention change offers only "Bloque nuevo" and leaves the block unchanged; a user swap is not re-proposed in the next block; the 65 rules start with the next block; `has_week_data` is false for a week with no session, weigh-in or diary day.
- [ ] pgTAP: RLS, isolation, cascade, idempotent apply, undo; `deno test` for the adaptation rules.

**Host tests:** `deno test` (`adaptBlock.test.ts`: swaps by pattern / group / drop, re-split by days, slots by length, cardio re-pick, loads, temporary overlay windows); pgTAP (offer lifecycle, apply and undo via `coach_changes`, temporary adaptation start/end with paused weeks, swaps memory, 65 boundary, `has_week_data`); Kotlin DTO/repository tests.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db`, `deno test`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact.

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5. About 16 files.
**Dependencies:** Checkpoint E13-F approved (open 👤 gate); T-190 (early review) and T-192 (`coach_changes`) merged.
**Files likely touched:** `supabase/migrations/<ts>_coach_life_changes.sql`, `supabase/tests/{coach_life_changes,coach_changes}.test.sql`, `supabase/functions/coach/{adaptBlock,blockRules,candidates,router}.ts` (+ tests), `shared/…` (offer, adaptation models, repository, DTO), `planning/{02-architecture,03-data-model,04-business-rules}.md`
**Estimated scope:** L

---

## T-197: "Revisar el bloque" after a life change, and "Solo unos días" (US-91, US-92 UI) · L

> **Amended 2026-11-03 (T-202 replan; pending E13 Q55):** see the note on T-196; the adapted preview reuses the T-206 preview.

**Story:** US-91, US-92 (UI) · **Flow:** core + UI · **Size:** L · **Scenarios:** US-91 every scenario (UI); US-92 Intention change offers an early review (UI); A new exercise starts from my own weights (live-session placeholder)

**Description:** (1) **Core:** `BlockAdaptViewModel` (offer, preview, apply, undo, "Bloque nuevo" hand-off to T-191, temporary adaptation with an end date, end it early), `TrainerViewModel` (offer card, active "Solo unos días" status). (2) **UI — templates and patterns (needs ux-reviewer for the offer, the three choices and "Solo unos días"):** after "Guardar" in "Mi entrenamiento" ~~(or "Para conocerte" injured area; void 2026-11-02, T-198)~~ during a block, the Trainer root shows a card "Has cambiado tu equipamiento" (reason per change; ux-reviewer finalises) with the action "Revisar el bloque", opening an `AppScreen` with three choices: **"Adaptar el bloque"** (default; the adapted block preview reusing the T-164 block preview with markers on swapped, re-split and dropped slots and a pinned `PrimaryButton` applying it in one tap, then `CoachChangeRow` with "Deshacer"); **"Bloque nuevo"** (opens the T-191 review); **"Solo unos días"** (an Edit sheet `AppEditSheet` with the end date picker (existing date picker), place, equipment and days for those days, and "Guardar"; R-07, R-10); while active, "Tu plan" shows "Hasta el 14 nov: entrenas con lo que tienes de viaje" (copy by ux-reviewer) with "Terminar ahora". A new exercise without history shows no planned weight and the existing placeholder hint to pick a weight for the bottom of the range at RPE 7-8 (copy by ux-reviewer; no conversion). An intention change shows the card with only "Bloque nuevo". (3) `05` §10 glossary ("Revisar el bloque", "Adaptar el bloque", "Bloque nuevo", "Solo unos días", "Terminar ahora"), §6.2 Trainer root order.

**Acceptance criteria:**
- [ ] After saving new equipment during a block, Trainer shows the card; "Revisar el bloque" opens the three choices.
- [ ] "Adaptar el bloque" shows the preview with swapped, re-split and dropped slots marked; one tap applies it with "Deshacer"; no AI cost in "Uso de IA".
- [ ] "Bloque nuevo" opens the end-of-block review.
- [ ] "Solo unos días" with an end date: "Tu plan" shows the temporary state; the live session uses the travel routines; after the date (or "Terminar ahora") the originals return with their loads.
- [ ] An intention change shows only "Bloque nuevo"; a new exercise without history has no planned weight and shows the RPE hint.
- [ ] The choices screen, the adapted preview and the "Solo unos días" sheet with a picked date survive background and process death (ADR-40).

**Host tests:** ViewModel tests (offer, preview, apply, undo, hand-off, temporary start/end, intention-only offer); Robolectric (card, three choices, markers, sheet fields, temporary status line, RPE hint); restoration tests.

**Verification:**
- [ ] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 14 files.
**Dependencies:** T-196 merged and deployed.
**Files likely touched:** `feature/trainer/{BlockAdaptScreen,BlockAdaptViewModel,BlockAdaptUiState,TemporaryAdaptationSheet,TrainerScreen,TrainerViewModel,BlockPreviewScreen}.kt`, `feature/workout/LiveSessionScreen.kt` (RPE hint), `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** L

---

*Checkpoint E13-G (life changes) was removed on 2026-11-02 (user): its items moved into Checkpoint E13-C, which is now the last checkpoint and follows T-169.* *(2026-11-03: the life-change items left E13-C for E14.)*

---

## Back to E13 (after Checkpoint E13-H): report, history, evaluation, chat, closure

**Classification (epic-planner, 2026-11-03, user's E14 split):** T-166, T-167, T-168, T-169 and T-170 stay in E13: their core (weekly report from the check-in, plan history and deletion, the evaluation gate, the chat) does not need the block follow-ups. Only the parts that read follow-up data move to E14: the report's list of automatic changes and its US-90 note (T-166), the history and deletion of automatic changes, block reviews, return steps, health notes and life-change offers (T-167), and the phase-proposal, block-review and injury evaluation cases (T-168). Two pieces that T-190 and T-196 were going to build move into T-166 (their first user in E13): the shared grounding validator and `has_week_data`.

---

## T-166: Weekly coach report (US-71; US-76 report fallback; US-90 note) · L

> **Amended 2026-11-03 (E14 split, user):** stays in E13 without the follow-up parts: the scenarios "The report lists what the coach changed" and "A persisting severe discomfort" (US-90) and the acceptance lines on automatic changes and the note move to E14. This task now **builds the shared grounding validator** (feature list + summary figures; formerly T-190) and **`has_week_data`** (formerly T-196); "sessions done against planned" reads the block's plan weeks when a block exists and only sessions done otherwise (E13 Q58). The fitness-scientist step stays. Size still L.

**Story:** US-71 (+ US-76 "User budget reached" for the report, US-90 "Open for 14 days" in the report) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Report after the weekly check-in; Report cites real data; Invented figure is rejected; Short sleep is mentioned, not acted on; Report on demand, once a day; **added 2026-11-01:** Three parts; The report reads what I wrote; The report lists what the coach changed; Missing data is said, not guessed; Only what exists; A persisting severe discomfort; US-76 User budget reached

**Amended by the replan (2026-11-01; ADR-86, ADR-87, ADR-89, `07` §12.6, E13 Q49 recommendation):** the figures add the weekly average weight and trend against the phase's target rate, complete diary days, sessions done against planned (plan weeks of T-189) and the automatic changes of the week (`coach_changes`, T-192/T-193) with their reasons; the text has three parts (one thing that went well, one obstacle, one change for next week chosen by the model among server-built candidates, never a plan change); the model reads the free-text block (T-185); the shared grounding validator (T-190) runs beside the figure validator; a held calorie week shows "Esta semana faltan registros: no cambiamos tus calorías."; the persistent-discomfort note (T-194) shows once; **no report is generated for a week with no session, weigh-in or diary day** (`07` §12.8 row 10: nothing to report, no paid call; `has_week_data`, T-196); a reached outcome ~~or pre-break best~~ gets one factual line and changes nothing (row 8; pre-break bests removed 2026-11-02, T-198). The fitness-scientist first step stays only for what `07` does not yet say about judging cardio and steps in the week.

**Description:** (1) **Migration:** `coach_reports` (epic §Tables: `week_start` ISO Monday, `generated_on` date, `figures` jsonb = the stored weekly summary: volume, records, adherence, weekly cardio minutes vs target and steps average vs target (from `coach_plan_weeks`, T-183; the validator and tests cover them), weight trend, sleep 7-day average; `text` nullable), unique `(user_id, week_start, generated_on)` or the planner's equivalent for "once a day", owner RLS, cascade, pgTAP. (2) **Model call `report`** (Sonnet 5.5, streamed, < 30 s p95 target): input = the stored figures only; **figure validator** (US-71 technical note): every number in the text, after normalising decimals ("," / ".") and units, must be in `figures` (rounding to the shown precision allowed); any other number → text discarded, the report shows only tables and figures; sleep 7-day average < 6 h → the text mentions sleep and nothing changes load or calories (no recommendation created). Budget reached → figures and tables without text plus the limit message. (3) **UI — templates and patterns:** in "Check-in semanal" the coach commentary appears **below the weekly summary** after saving the check-in (E10 US-59 hook; text block in an `AppCard`, streamed progress state from T-162); a Trainer root `NavigationRow` "Informe semanal" (Gherkin literal; R-03/R-04: the screen title equals the label) opens the report `AppScreen` for the current week (figures tables reuse the check-in summary components, R-39); tapping it again the same day shows the stored report, no new call.

**Acceptance criteria:**
- [ ] After completing the check-in, the coach commentary shows below the weekly summary.
- [ ] Every figure in the text exists in the stored figures (validator tests with real-shaped summaries).
- [ ] A text with an invented figure is discarded; only tables and figures show.
- [ ] Sleep average < 6 h → the text mentions sleep; no load or calorie change is created.
- [ ] "Informe semanal" shows this week's report and does not generate again the same day.
- [ ] At 3 € (ADR-90) the report shows figures and tables without text plus the limit message.
- [ ] Report screen and check-in with the commentary survive background and process death (ADR-40).
- [ ] (Replan) The text has the three parts; a check-in note not left out can be used and yields no figure; the week's automatic changes are listed with reasons; a held week shows the missing-records sentence; a text naming a missing feature is discarded; a persisting level 4-5 discomfort shows the US-90 note once; a week with no session, weigh-in or diary day generates no report and makes no call.

**Host tests:** `deno test` (validator: decimals, units, rounding, percentages, dates not counted as figures per the planner's rule, invented figure; once-a-day reuse; sleep mention; budget fallback); pgTAP `coach_reports.test.sql`; ViewModel tests (check-in commentary, on-demand, same-day reuse, limit, error); Robolectric tests (commentary below the summary, report screen, figures-only state, limit message); restoration tests.

**Verification:**
- [ ] `deno test`, pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main`.

**Agent runs:** fitness-scientist first (decides how the weekly check and report judge the week, cardio and steps included: what counts as hit or missed, what the coach says about it; records it in `07` before the planner), planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 9. About 15 files.
**Dependencies:** **Checkpoint E13-H approved** (2026-11-03, E14 split; formerly T-197)
**Files likely touched:** `supabase/migrations/<ts>_coach_reports.sql`, `supabase/tests/{coach_reports,delete_account_cascade}.test.sql`, `supabase/functions/coach/{report,figureValidator}.ts` (+ tests), `shared/…` (report model, repository, DTO), `feature/checkin/{CheckInScreen,CheckInViewModel}.kt`, `feature/trainer/{WeeklyReportScreen,WeeklyReportViewModel}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/03`, `04` §6.6/§7, tests
**Estimated scope:** L

---

## T-167: Recommendation history, outcomes and deleting the coach history (US-74) · M

**Story:** US-74 (amended 2026-11-01) · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** Every decision is stored (already written by T-161/T-163/T-165; asserted end to end here; now also automatic changes and their undo, phase decisions and block reviews); Outcome after a block; Deleting the coach history; Deleting the account

**Amended by the replan (2026-11-01; ADR-88, E13 Q48 recommendation):** the history lists `coach_changes` (applied, undone), phase decisions ("Aceptar" / "Seguir como estoy") and block reviews with their answers, besides plans and recommendations; `delete_coach_history()` also deletes `coach_changes`, `coach_block_reviews`, `coach_return_steps`, `coach_health_notes` and T-196's offers and temporary adaptations, and keeps `coach_intake`, the derived values, the free-text exclusions and the AI usage; the deletion cascade test covers every new table.

**Amended 2026-11-03 (E14 split, user):** stays in E13 for plans, recommendations, phase choices made in the plan, outcomes, reports, conversations and folder reviews (E13 Q48 note); the history and deletion of `coach_changes`, block reviews, return steps, health notes and life-change offers move to E14 (those tables do not exist in E13; each E14 task adds its table to `delete_coach_history`). Favourites are kept (an exercise preference). Still M.

**Description:** (1) **Backend:** `coach_outcomes` (epic §Tables: `recommendation_id` cascade, `measured_at`, `weight_kg`, `e1rm_kg`, `adherence_pct`), filled deterministically when the history is read for plans older than their block (or on each coach run, planner picks); RPC `delete_coach_history()` deletes plans, plan sessions (routines stay; `routine_id` links only), recommendations, outcomes, reports (derived values and AI usage are not in the Gherkin list: kept, E13 Q13) and conversations (when T-169 adds them), never routines, workouts or the account; `delete_account_cascade.test.sql` covers every coach table. (2) **UI — templates and patterns:** Trainer root ⋮ `AppActionSheet` (R-02) → "Historial" (glossary entry added by this task) `AppScreen`: one `AppListRow` per plan and recommendation (date, kind, decision), tapping opens a details `AppScreen` with the data it was based on and, for a plan older than its block, the outcomes since then next to the plan's outcomes; the delete action ("Eliminar historial del entrenador", E13 Q5) in that screen's top bar ⋮, confirmed by the R-08 many-records dialog (extend the G5 allow-list); empty state after deleting.

**Acceptance criteria:**
- [ ] Accepting, editing or discarding any proposal shows in the history with the data it was based on.
- [ ] A plan accepted four weeks ago shows the measured outcomes (weight, e1RM, adherence) next to its outcomes.
- [ ] Deleting the coach history after confirming removes plans, recommendations, outcomes, reports and conversations; account, routines (including the block's) and training data stay.
- [ ] Deleting the account deletes every coach table's rows (pgTAP).
- [ ] History list and details survive background and process death (ADR-40).

**Host tests:** pgTAP (`coach_outcomes`, `delete_coach_history` keeps routines/workouts/account, isolation, cascade); repository/ViewModel tests (list, details, outcomes, delete, offline); Robolectric tests (rows, details with input summary, outcomes, delete dialog, empty state); `check-ux-conventions.sh` G5 fixture for the new dialog.

**Verification:**
- [ ] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` (before T-168 starts).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 13 files.
**Dependencies:** T-166 merged (no checkpoint in between); E13 Q5, Q13 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_history.sql`, `supabase/tests/{coach_history,delete_account_cascade}.test.sql`, `shared/…` (history models, repository), `feature/trainer/{CoachHistoryScreen,CoachHistoryDetailScreen,CoachHistoryViewModel}.kt`, `app/{AppRoutes,AppNavHost}.kt`, `scripts/check-ux-conventions.sh` + fixtures (G5 allow-list), strings, `planning/03`, `05` §8/§10, tests
**Estimated scope:** M

---


## T-168: Coach evaluation: cases, runner and results (not a story) · M

**Story:** Evaluation (epic §Evaluation) · **Flow:** backend + 👤 · **Size:** M · **Scenarios:** none (acceptance criteria of the epic's evaluation)

**Amended 2026-11-02 (T-198):** the intake extraction and the injured area are removed, so their cases and the `intake_extract` gate go; a free-text case is added instead (the plan and block reasons treat the user's text as context: no figure, trigger or instruction taken from it).
**Amended 2026-11-03 (T-202 replan, ADR-93):** block cases follow the routine checker (every block passes H1-H7 first time or after the retry; two users with the same days get different splits or exercises; favourites preferred when they fit) and folder-review cases (no proposed change adds a hard failure); "undulating only for Ganar fuerza" is no longer a criterion (epic "Evaluation" amended). **E14 split (same day):** the phase-proposal, block-review and injury cases and the `block_review` gate move to E14.
**Amended by the replan (2026-11-01):** the case set adds ~~intake extractions (unmentioned fields stay empty, only allowed values),~~ phase proposals (~~returning → regain, no deficit in regain,~~ only eligible options; regain removed 2026-11-02), block reviews (volume and priority limits of `07` §12.4), injury (no flagged exercise~~, none loading the injured area~~), grounding (report, chat and plan reasons name only existing features) and free text (never a figure, trigger or health note); the criteria are the epic's amended list. The `intake_extract` and `block_review` kinds can be disabled by the same gate.

**Description:** (1) **👤 Cases:** the user writes the cases, each with its correct answer: a plan for each intention, one training day, limited equipment, discomfort, missing data, rate inside and outside range, calorie floor, invented figure in a report (the planner provides a case template file; the user fills it; cases hold no personal data, public repo, or live gitignored under `design/reported/`-style local storage if they do; planner proposes, PM asks). (2) **Runner:** a script (`scripts/coach-eval.ts` or a `deno` task, never in CI) that runs each case through the deployed `coach` function's validators and model calls with a dedicated test account, records pass/fail per criterion and latency, and writes a results table (no prompt or output dumps in the repo beyond the verdicts). (3) **Criteria (epic):** 100 % of discomfort cases with no load increase; 100 % of report figures traceable; every plan meets the rules of `07` §3-§4: frequency (when days allow), volume, intensity per intention, rest between sets (2-3 min main multi-joint, 1-2 min isolation), exercise order (priority multi-joint first), periodization (undulating only for "Ganar fuerza", one range otherwise), no reduction of cardio or steps, protein (≥ 1.6 g/kg, distribution guidance ≈ 0.4 g/kg per meal over ≥ 4 meals), fat and the calorie floor; report and chat under 30 s at p95; the plan shows progress and finishes within 150 s; **T-180 regression (ADR-84):** a user with 28 logged days (mean about 2262 kcal) and 5 flat, low-confidence weigh-ins gets a "Ganar musculatura" target about 2700-2740, never about 2972 (target within the rate-derived range of the intake anchor). (4) **Gate:** a function that fails its criteria is disabled server-side (a `coach_feature_flags` row or a constant, planner picks) until fixed (fix tasks), and US-77 (T-169) starts only when every criterion is met. **Each run is a paid call: the PM asks the user before each run** and reports its cost.

**Acceptance criteria:**
- [ ] 👤 Cases written by the user and committed (or stored as agreed).
- [ ] 👤 Each run approved by the user; results table recorded in `07` (new §12 "Evaluation results") with date and cost.
- [ ] Every epic criterion met, or the failing function disabled and a `fix(T-###)` task recorded.

**Verification:**
- [ ] `deno test` for the runner's scoring (fake responses); results reviewed with the user.

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4 (+ one backend-developer run per approved evaluation run).
**Dependencies:** T-167 merged and deployed (no checkpoint in between)
**Files likely touched:** `scripts/coach-eval/{cases.template.json,run.ts,score.ts}` (+ tests), `supabase/functions/coach/flags.ts` (if a gate is needed), `planning/07-coach-design.md` §12
**Estimated scope:** M

---

## T-169: Coach chat with tools (US-77; enabled only after T-168 passes) · L

**Story:** US-77 (amended 2026-11-01) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Answer cites data; Proposals are cards, not changes; Only what exists; The chat reads what I told the coach

**Amended 2026-11-03 (T-202 replan, E14 split):** the grounding validator comes from T-166 (no longer T-190); a training proposal in a chat answer is validated by the routine checker (ADR-93); the "week's automatic changes" tool waits for E14 (read-only access to the phase stays).
**Amended by the replan (2026-11-01; ADR-87, ADR-88):** the chat context includes the free-text block (T-185) and the phase; its answers pass the shared grounding validator (T-190); chat proposals stay `CoachProposalCard`s that need "Aceptar" (they are not automatic changes); the tools add read-only access to the week's automatic changes and the current phase.

**Description:** (1) **Migration:** `coach_conversations` (and messages; owner RLS, cascade, included in `delete_coach_history`), pgTAP. (2) **Model call `chat`** (Sonnet 5.5, streamed, prompt caching of the summary per `07` §11; < 30 s p95): read-only tools over the user's data (sessions of an exercise, e1RM history, weekly sets, weight trend, adherence; each a parameterised SQL read through the user's token, capped rows); every answer cites the sessions/data it used (validated: cited ids exist in the tool results); a proposal in an answer goes through the same validators as US-69/US-72 and is stored as a pending `coach_recommendations` row; nothing is changed until accepted (`decide_recommendation`). Budget per US-76 (the 3 € per-user cap, ADR-90, stops chat first, `07` §11). (3) **UI — templates and patterns:** a "Chat" entry on the Trainer root (copy by ux-reviewer; a new chat screen pattern: `AppScreen` with a lazy message list, a pinned input row using `AppTextField` with a send icon, R-39, keyboard room as `AppEditorScreen`), streamed answer with progress, citations as tappable rows to the workout detail (R-17), proposals as `CoachProposalCard` (T-162) with "Aceptar" / "Descartar"; limit message (US-76); offline hint (R-30). Record the chat pattern in `05` §9.

**Acceptance criteria:**
- [ ] "¿Por qué estoy estancado en press banca?" → the answer cites the sessions it used.
- [ ] A proposal in an answer shows as a card with "Aceptar" and "Descartar"; nothing changes until "Aceptar".
- [ ] Limit reached → the limit message; the rest of the coach keeps working.
- [ ] Deleting the coach history deletes the conversations.
- [ ] The conversation, its scroll and the typed draft survive background and process death (ADR-40).

**Host tests:** `deno test` (tools: owner-only, row caps; citation validator; proposal validation reuse; budget); pgTAP `coach_conversations.test.sql`; ViewModel tests (send, stream, citation tap, proposal accept/discard, limit, offline); Robolectric tests (message list, input, cards, citations); restoration tests.

**Verification:**
- [ ] `deno test`, pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main`; the chat's evaluation cases re-run with the user's approval (T-168 runner).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 16 files.
**Dependencies:** T-168 criteria met for the other stories (epic)
**Files likely touched:** `supabase/migrations/<ts>_coach_conversations.sql`, `supabase/tests/{coach_conversations,coach_history,delete_account_cascade}.test.sql`, `supabase/functions/coach/{chat,tools,citations}.ts` (+ tests), `shared/…` (chat models, repository, streaming), `feature/trainer/{CoachChatScreen,CoachChatViewModel}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/03`, `05` §9/§10, tests
**Estimated scope:** L

---

## ~~T-173: E13 UI refinements~~ (folded 2026-11-02, user: no S tasks)

Its three items moved, unchanged in substance, into tasks that open the same screens: the Preferencias "Cuenta" move and the weight-reminder target → **T-188** (Perfil and Peso, with the disclaimer sweep; Checkpoint E13-D item 7); the "Similares" percentage → **T-194** (exercise details; the item was removed 2026-11-02, the `%%` string fixed in T-201). New small UI requests from the user become lines of the next M/L task that touches the same screen, not a new S task.

### E13 backlog (not yet a task; the epic-planner or planner sizes it)
- [ ] **Global test guards script** (user, 2026-11-03; T-208 CI failed on `AppModuleTest` and `GlossaryStringsTest`, tests the developers did not run): `scripts/run-global-guards.sh` runs the tests that scan the whole app (`AppModuleTest`, `GlossaryStringsTest`) and the developer agents call it before reporting done. Second defect of this type, so it becomes a check (CLAUDE.md). Prose rules added to rule 11 of both developer agents meanwhile.
- [x] **Cardio in the plan** → planned as T-181…T-184 (epic-planner, 2026-11-01, reworked the same day: no warm-up, no cardio days, cardio equipment T-184; ADR-85 proposed, E13 Q23-Q31). Original line kept for reference: **Cardio in the plan — NEXT, before the E13-B1 re-test (user decision, 2026-11-01; the epic-planner runs first, discovers and verifies the sources, then plans it) (user, 2026-11-01: "the app replaces a real trainer; if the trainer would plan cardio, the app must"):** the coach prescribes cardio like a trainer. Recommended shape (PM, to be verified against sources in `07` §10 before it becomes a rule): (1) a weekly cardio target (minutes and daily steps) by intention, next to the calories and macros, from the deterministic rules module, not the model; (2) cardio sessions in the block (e.g. "Bicicleta estática, 25 min, ritmo suave"), placed after lifting or on rest days, never before heavy legs; (3) a 5-minute warm-up line at the start of each routine; (4) adherence counts the cardio sessions; (5) the validator checks the weekly minutes are in range and the existing "never reduce cardio" rule stays (`07` §3). Needs `01-scope.md`, `07` §3-§4, US-72/US-69 Gherkin and an ADR; no implementation until planned.

---

### Checkpoint E13-C — Your weekly report, the coach's track record, the chat, and the final E13 regression 👤 (5 items + leave and come back) *(rewritten 2026-11-03 by the E14 split: the life-change items 1-4 moved to E14; proposed, needs the user's OK; the last E13 checkpoint)*
Journey: read the weekly report; look back at what the coach proposed and wipe it if you want; check the cost; ask the coach about your training; then one pass over the whole coach. Why here: right after T-169, the last implementation task, so it is also the final regression of E13; its approval releases the closure T-170. T-166, T-167 and T-168 merge with no checkpoint after them (all run after Checkpoint E13-H). **If T-168 keeps US-77 disabled**, this checkpoint is presented right after T-168 without item 4 (and T-169 waits for its fix tasks).
- [ ] T-166…T-169 merged (T-168 criteria met, results recorded), CI green, `deploy-supabase` applied their migrations and the function (data intact), every reported bug fixed.
- [ ] 👤 Install the APK and try (a report and a chat answer are paid calls):
  1. Weekly report: answer the check-in: the coach writes what went well, one obstacle and one change for next week (a check-in note can show up); every number is in the tables (weight against your phase's rate, diary days, sessions, sleep); "Informe semanal" again the same day: same report, no new cost; a thin week says "Esta semana faltan registros…".
  2. History and delete: Trainer ⋮ → "Historial" lists your plans, proposals and folder reviews, each with its data, and a past plan's outcomes against what was measured; "Eliminar historial del entrenador" empties it and keeps your account, routines, workouts, favourites, "Para conocerte", confirmed values and AI usage.
  3. Cost: "Uso de IA" shows this month's usage against the 3 € limit (ADR-90).
  4. Chat: ask "¿Por qué estoy estancado en press banca?": the answer cites the sessions it used (tap one: its workout opens) and talks only about things the app has; a proposal is a card: "Descartar" changes nothing, "Aceptar" applies it.
  5. Final regression: "Antes de empezar" with its intake rows and "Para conocerte", a plan in its phase, a block from scratch and one from a folder with cardio, "Eliminar bloque", a workout with the progression suggestion; Diario follows the plan, Peso shows the outcome, Actividad and "Centro de avisos" work as before.
  - Leave the app and come back on the report, and once in the chat with a question half typed: the same tab, screen and draft are restored.
  - Out of scope: the block follow-ups (E14: plan weeks that pause, the end-of-block review, automatic changes, injury, life changes); illness, logging nothing for weeks, turning 65, the invented-figure and grounding fallbacks and the limit-reached report (host tests); deleting the account (pgTAP); water and voice (Phase 3); medical advice, meal plans, video analysis (never); distribution to other users (`07` §8).
- *(The earlier 7- and 9-item lists of this checkpoint, 2026-10-29 and 2026-11-01, are superseded by the list above.)*

---

## T-170: E13 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E13 Definition of Done · **Flow:** PM closure step after Checkpoint E13-C's approval (no developer agent, no size class; it is not merged into any task because it must follow the last checkpoint)

**Description:** Closes E13 once Checkpoint E13-C is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR; minor per epic, proposed `1.4.0`), tag, retrospective, archive of the E13 log lines; `09-open-questions.md` already holds E13 Q1-Q49: close Q47-Q49 and Q52 (recommendation kept or the user's override; Q46 superseded and Q50/Q51 resolved by the removal of "Recuperar", user 2026-11-02) and add any question raised later. Rotation reminder of the Anthropic key stays with the user (👤 key section). No 👤 checkpoint of its own.

**Acceptance criteria:**
- [ ] T-157…T-169, T-171, T-172, T-174…T-188, T-198…T-201, T-203…T-208 (and any fix tasks) merged (T-189…T-194, T-196, T-197 moved to the E14 backlog, 2026-11-03) with green CI (T-173 and T-195 were folded, not built); `deploy-supabase` green after each migration and function change.
- [ ] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [ ] `02` (coach function, streaming, budget), `03` (coach tables, RPCs, function), `04` §7 (coach rules), `05` (Trainer tab, coach patterns, glossary) and `07` (evaluation results) are current.
- [ ] Checkpoints E13-A, E13-B (B1, B2), E13-D, E13-H and E13-C approved (E13-B3 skipped by the user, E13-G removed, E13-I merged into E13-H, E13-E and E13-F moved to E14); every reported bug fixed; `09-open-questions.md` E13 Q48, Q49 closed and Q44, Q45, Q47, Q52, Q55, Q58 handed to E14 (recommendation kept or the user's override).
- [ ] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [ ] `git tag` lists the E13 tag.

**Dependencies:** T-169 and Checkpoint E13-C approved. Closure scope (2026-11-03): E13 ends with the block, report, history, evaluation and chat; the E14 backlog is not part of the E13 DoD (the retrospective notes the split).
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E13.md`
**Estimated scope:** closure step (PM)

---

# E12 · Training data and preferences → `v1.x` (proposed `v1.3.0`)

Stories: `planning/epics/E12-training-data-and-preferences.md` (US-64, US-65, US-66, US-67 and the movement-pattern data task). Plan: [tasks/plan.md](plan.md). Decisions: ADR-75 (rest alert with the screen off; amends E3 US-13; reopens E9 points 15-16), ADR-76 (discomfort rule, RIR derived: no field; its fixed message was dropped by ADR-81), ADR-78 (preferences, intention, `movement_pattern`), ADR-39 (generated catalogue migration), ADR-29 (cascade), ADR-40 (restoration), ADR-80/R-38 (sheet dismissal). Rules: `04-business-rules.md` §4.4 (rest timer), `05-design-system.md` §8–§10, `07-coach-design.md` §7 (discomfort level and safety).
Carried over from "Notes for later epics": verify the screen-off alert on Android 17 (T-146 plan step, Checkpoint E12-A item 1); 👤 the user picks the new alert sound among the E9 point 16 candidates (Checkpoint E12-A item 5, wired in T-148). Both are inside the epic scope (ADR-75).
**Sound ordering (planner decision, recorded):** the user picks the sound at Checkpoint E12-A from T-146's temporary chooser, which T-147 leaves untouched. The pick is recorded with the checkpoint approval; it is a choice, not a bug, so it needs no fix task before approval. T-148, the first task after the checkpoint and also a live-session task, wires the chosen sound, removes the chooser and the other candidates, and Checkpoint E12-B re-tests it. No extra S task.
Sizing: 6 tasks, one per story. US-64 (T-146, L: platform scheduling, notification, permission, three sound candidates). US-67 (T-147, L) comes before US-66 because it builds the discomfort log (the strong-pain matcher it once reused was dropped by ADR-81). US-66 (T-148, L; backend + core + UI in one PR, the planner may run backend first as a developer slice; it also carries the sound wiring). US-65 (T-149, M). The movement-pattern data task (T-150, M: the reviewed mapping of every catalogue row plus an optional form field) stays alone: merging it with T-149 would exceed ~15 files across two unrelated forms and two migrations. Closure T-151 stays S (it waits for Checkpoint E12-B).
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1). UI scenarios are covered by host (Robolectric) tests; the user verifies by hand with `python scripts/deliver-apk.py`.
Questions (`tasks/plan.md`): E12 Q1 resolved by the user, 2026-10-28, then superseded by ADR-81 (no term list, no message; formerly 12 strong-pain terms, plain "dolor"/"molestia" excluded; message "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario."; T-147, T-148). E12 Q2 resolved, 2026-10-28 (session length 15-180 min in 5-min steps, shown with the existing duration formatter; T-149). No task is blocked.
Checkpoints (user decision, 2026-10-28: two): **Checkpoint E12-A** after T-147 and **Checkpoint E12-B** after T-150 (also the final regression; releases T-151). T-146, T-148 and T-149 merge with no checkpoint after them.

---

## T-146: Rest-end alert with the screen off and the sound candidates (US-64) · L

**Story:** US-64 · **Flow:** platform + core + UI · **Size:** L · **Scenarios:** Screen off when the rest ends; App in the background; App in the foreground; Skipping cancels the alert; Finishing the session cancels the alert; Notifications denied

**Description:** (1) **Technique (plan step, carried-over note):** the planner verifies on Android 17 (`targetSdk` 37) and picks how the alert fires on time with the screen off or the process killed: an exact alarm (`setExactAndAllowWhileIdle`/`setAlarmClock` with `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`; the APK is sideloaded) posting the notification from a receiver, a foreground service during the rest (`shortService` is capped at 3 min, so `specialUse` or another type if rests can be longer), or a scheduled notification if its delay under Doze is acceptable (a few seconds at most). The choice, its Android 17 sources and any permission the user must grant go in the PR body and `planning/02-architecture.md`; a permission the user must enable in Ajustes is raised to the PM as a product question before implementation. Timing stays based on `endsAt` (§4.4). (2) **Core:** a `RestAlertScheduler` port (`schedule(endsAt)`, `cancel()`) in `shared/…/domain/port/`; `LiveSessionViewModel` schedules when a rest starts, reschedules on "+30s" and when a new set restarts the rest, and cancels on "Omitir", "Finalizar entreno", discarding the workout and the rest reaching 0 with the app in the foreground (the in-app alert fires, nothing is posted). Foreground = process lifecycle `STARTED` (the same signal as T-135/T-142). Pausing the workout does not stop the rest (§4.10), so the alert stays scheduled. (3) **Platform:** a notification channel for the rest alert (high importance, vibration = `RestAlertPattern`, sound = the current candidate; channel sounds cannot change after creation, so the planner picks how the temporary chooser applies to the notification, e.g. a silent channel plus the app's own playback, or one channel per candidate, and T-147 settles one final channel); the notification "Descanso terminado" (auto-cancel; tapping it opens the live session, assumption A1); `POST_NOTIFICATIONS` requested at the first rest of a session on Android 13+ (assumption A2). (4) **Notifications denied:** nothing is posted; the app explains **once** (persisted flag, e.g. DataStore) that rest alerts need the notification permission; ux-reviewer picks the surface (`AppMessages` message or an `InlineNote` on the live session, R-28/R-37) and the Spanish copy; it never repeats after being shown. (5) **Sound candidates (E9 point 16):** the system notification sound, a soft chime and a double beep, each about 1 s, silent when the phone is muted; synthesised or licence-free assets only (public repo). A **temporary chooser** reachable from Perfil → "Acerca de" (one `AppListRow` "Sonido de fin de descanso" opening an `AppScreen` with one `AppListRow` per candidate: tap plays it and makes it the current one, stored locally), like T-139's diagnostic screen; the in-app alert and the notification use the current candidate. The chooser stays through T-147 (the user picks at Checkpoint E12-A) and is removed by T-148 (sweep item there); its copy is not reviewed beyond R-36/R-37.

**UI templates and patterns:** live session unchanged except the one-time explanation; the chooser is `AppScreen` + `AppListRow`s (temporary). No new designsystem component.

**Acceptance criteria:**
- [x] Rest of 90 s running, phone locked: at `endsAt` the phone vibrates with the rest pattern, plays the current sound and shows "Descanso terminado" (verified by hand at Checkpoint E12-A; host test: posted at `endsAt` when not in the foreground).
- [x] App in the background at `endsAt` → the same alert.
- [x] App in the foreground at `endsAt` → only the in-app alert (vibration + sound); no notification is posted and the scheduled one is cancelled.
- [x] "Omitir" in the app, "Finalizar entreno" or discarding the workout → nothing fires when the time would have ended; "+30s" moves the alert 30 s later; completing another set reschedules it.
- [x] Notifications denied → nothing is posted; the explanation appears once and never again (also after reopening the app); the in-app alert still works in the foreground.
- [x] The temporary chooser lists the three candidates, plays each on tap and the next alert uses the chosen one.
- [x] `planning/02-architecture.md` (technique, permissions) and `04-business-rules.md` §4.4 (alert outside the app, cancel rules) updated; ADR-75 consequences ticked.
- [x] The live session with a running rest survives background and process death (ADR-40); a rest scheduled before process death still alerts.

**Host tests:** `LiveSessionViewModel` tests with a fake `RestAlertScheduler` (schedule on set completion with `endsAt`, "+30s" reschedules, new set restarts, "Omitir"/finish/discard cancel, foreground end cancels and plays the in-app alert, pause keeps it); notification tests in Robolectric (channel, text "Descanso terminado", posted only when not in the foreground; `ShadowAlarmManager`/`ShadowNotificationManager` or the service equivalent per the chosen technique); permission-explanation-once test (denied → shown once, flag persisted, never again); chooser screen test (rows, tap plays and selects); restoration test with a running rest.

**Verification:**
- [x] lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.
- [x] Android 17 behaviour verified in the plan (sources recorded); the on-device check is Checkpoint E12-A items 1-2.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 17 files, no backend.
**Dependencies:** E11 closed (`v1.2.0`); breakdown agreed by the user
**Files likely touched:** `androidApp/src/main/AndroidManifest.xml`, `androidApp/…/di/AppModule.kt` (+ channel creation in the Application), `shared/…/domain/port/RestAlertScheduler.kt`, `composeApp/…/platform/{AndroidRestAlertScheduler,AndroidAlertSound,RestAlertPattern}.kt` (+ receiver or service), `composeApp/src/commonMain/composeResources/files/` or `androidApp/src/main/res/raw/` (sound candidates), `feature/workout/{LiveSessionViewModel,LiveSessionScreen,LiveSessionUiState}.kt`, new temporary `feature/profile/RestSoundChooserScreen.kt`, the "Acerca de" screen, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/02-architecture.md`, `planning/04-business-rules.md`, tests
**Estimated scope:** L

---

## T-147: Discomfort log per exercise (US-67; strong-pain message dropped by ADR-81) · L

**Story:** US-67 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Logging discomfort; Level and note (reworked by T-152); Resolving; Open discomforts expire

**Description:** (1) **Backend:** one migration: `exercise_discomforts` (`id`, `user_id` default `auth.uid()` cascade, `exercise_id` references `exercises` on delete cascade, `logged_at` timestamptz default now, `note` text nullable 1-200 chars, `severe` boolean not null default false, `resolved_at` timestamptz nullable, check `resolved_at >= logged_at`), index on `(user_id, exercise_id, logged_at desc)`; owner RLS enabled and forced with `(select auth.uid())`, select/insert/update grants to `authenticated` (no delete: history for E13), extend `delete_account_cascade.test.sql`; pgTAP. (2) **Domain:** `StrongPainTerms` (the fixed, versioned term list in `shared/…/domain/rules/`; matching case- and accent-insensitive on normalised text, whole words or phrases; no model interprets the text, `07` §7). **Term list (E12 Q1, resolved by the user 2026-10-28):** "dolor fuerte", "dolor agudo", "dolor intenso", "pinchazo", "chasquido", "hormigueo", "entumecimiento", "hinchazón", "lesión", "me he lesionado", "desgarro", "no puedo mover". Plain "dolor" and "molestia" are deliberately **not** terms. **Fixed message:** "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario.", `DiscomfortStatus` (open while not resolved and `now < logged_at + 7 days`; boundary tested), `LogDiscomfort` (sets `severe` from the term match; severe is not chosen by hand, assumption A3) and `ResolveDiscomfort` (sets `resolved_at` = now); `DiscomfortRepository` port + supabase-kt implementation with DTO mapping; a query of the open discomforts for the exercises of the live session. (3) **UI — templates and patterns:** in the live session, "Registrar molestia" is an action of the exercise's ⋮ `AppActionSheet` (R-02; ux-reviewer confirms the placement) opening an Edit sheet (`AppEditSheet`: optional multi-line `AppTextField` "Nota", "Máximo 200 caracteres", pinned "Guardar", R-09/R-10; closes only with X/back/Cancelar, R-38); after saving, the exercise shows "Molestia registrada" (e.g. an `InlineNote` under the exercise header; ux-reviewer picks) with the action "Ya no me molesta" (resolves, Confirm haptic, the label leaves); a severe log shows the fixed message (exact text above; `InlineNote` or `AppInfoSheet`, ux-reviewer picks) and no other suggestion; the open discomfort shows in later sessions until it expires or is resolved; offline: "Guardar" and "Ya no me molesta" disabled with the standard hint (R-30). Where else "Molestia registrada" appears (exercise detail) is not required (assumption A4). Spanish copy beyond the Gherkin literals by ux-reviewer per §10 (glossary entry "Molestia"). T-146's temporary sound chooser and its candidates stay untouched: the user picks the sound from them at Checkpoint E12-A, and T-148 wires the choice.

**Acceptance criteria:**
- [x] "Registrar molestia" on "Press banca" + "Guardar" → stored for "Press banca" with today's date; "Press banca" shows "Molestia registrada" in this and later sessions.
- [x] Note "dolor fuerte" (or "Dolor FUERTE", "HINCHAZON", "lesion" variants per the normalisation) → "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario." is shown and the row is stored with `severe = true`; a note with only "dolor" or "molestia" (or no listed term) is not severe and shows no message.
- [x] "Ya no me molesta" → `resolved_at` = now and "Molestia registrada" disappears.
- [x] A discomfort logged 8 days ago (unresolved) no longer shows "Molestia registrada"; one logged 6 days ago does.
- [x] pgTAP: RLS enabled and forced, user A cannot read or write user B's discomforts, note length check, `resolved_at` check, cascade on account deletion.
- [x] `planning/03-data-model.md` (table) and `04-business-rules.md` (new §4.12 discomfort: open window, severe rule, term list location) updated.
- [x] The discomfort sheet draft and the live session survive background and process death (ADR-40).

**Host tests:** `StrongPainTermsTest` (every one of the 12 terms, case, accents, phrase vs substring, plain "dolor"/"molestia" do not match, empty/null note); `DiscomfortStatusTest` (6/7/8 days, resolved, boundary); repository mapping test; live-session ViewModel tests (log, severe message, resolve, open discomfort loaded for a new session, offline); Robolectric tests of the action, the Edit sheet (length validation, Guardar), "Molestia registrada", "Ya no me molesta" and the fixed message; restoration test of the sheet draft; pgTAP `exercise_discomforts`.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before Checkpoint E12-A is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 16 files.
**Dependencies:** T-146 merged (no checkpoint in between); E12 Q1 resolved (2026-10-28, terms and message above)
**Files likely touched:** `supabase/migrations/<ts>_exercise_discomforts.sql`, `supabase/tests/exercise_discomforts.test.sql`, `supabase/tests/delete_account_cascade.test.sql`, `shared/…/domain/model/Discomfort.kt`, `shared/…/domain/rules/StrongPainTerms.kt`, `shared/…/domain/usecase/{DiscomfortStatus,LogDiscomfort,ResolveDiscomfort}.kt`, `shared/…/domain/repository/DiscomfortRepository.kt`, `shared/…/data/{remote/DiscomfortDto,repository/SupabaseDiscomfortRepository}.kt`, `shared/…/di/`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen,LiveSessionUiState}.kt` (+ a `DiscomfortSheet.kt`), strings, `planning/03-data-model.md`, `planning/04-business-rules.md`, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

### Checkpoint E12-A — Rest alert with the screen off, its sound, and the discomfort log 👤 (7 items + leave and come back) *(agreed with the user, 2026-10-28: two checkpoints)*
Journey: during a workout, lock the phone or switch apps while resting and still be told when the rest ends; choose the sound you want; flag an exercise that bothers you and clear it when it no longer does. Why here: right after T-147, before T-148 builds on the live session and wires the chosen sound.
- [x] T-146 and T-147 merged, CI green, `deploy-supabase` applied T-147's migration (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-29; sound chosen: "campana suave"; visual feedback → T-148 item 5)*:
  1. Screen off and background (US-64): start a workout, complete a set with a 90 s rest (or shorter) and lock the phone: when the rest ends the phone vibrates, plays the sound and shows "Descanso terminado" on the lock screen; repeat switching to another app: the same alert arrives on time; tap it: the live session opens.
  2. Foreground: stay in the app until the rest ends: only the in-app alert (vibration and sound), no notification in the shade.
  3. Cancel and move: during a rest in the background come back, tap "Omitir" and leave again: no alert; start another rest, tap "+30s" and lock: the alert comes 30 s later; with a rest running, finish the workout (or discard it): no alert arrives afterwards.
  4. Notifications denied: in Ajustes deny AppFitness notifications, start a rest: the app explains once that alerts need the permission; no notification with the screen off; the explanation does not come back on the next rest or after reopening the app; allow them again.
  5. Choose the sound (E9 point 16): Perfil → "Acerca de" → "Sonido de fin de descanso": listen to the three candidates (system notification sound, soft chime, double beep), pick one and tell the PM which (recorded with the approval; T-148 wires it); check it is silent with the phone muted.
  6. Log discomfort (US-67): in the live session tap "Registrar molestia" on "Press banca" and save: "Press banca" shows "Molestia registrada"; start another workout with it: it still shows.
  7. Level and resolve (US-67, ADR-81): log a discomfort picking a level (no message appears, even with the note "dolor fuerte"); then tap "Ya no me molesta": "Molestia registrada" disappears.
  - Leave the app and come back during a rest in the live session, with the discomfort sheet half typed: the same tab, screen, countdown and draft are restored.
  - Out of scope: the wiring of the chosen sound (T-148, re-tested at Checkpoint E12-B); the 7-day expiry over real days (host tests); the coach blocking load increases (E13, US-70); reminders as system notifications (never, ADR-73).

---

## T-152: Discomfort level scale and help on every rating scale (ADR-81 amends US-67; R-45) · L

**Story:** US-67 (amended by ADR-81, Checkpoint E12-A feedback 2026-10-28) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** to be rewritten in the epic file by the planner: Logging discomfort with a level; Levels are ordered and colour-coded; Resolving; Open discomforts expire.

**Description:** Replace T-147's free-text term matching with a level picker. (1) **Backend:** a migration adds `level` (smallint, check by the number of levels; existing rows mapped: `severe` true → highest, false → mild) and drops or derives `severe`; pgTAP updated. (2) **Core:** remove `StrongPainTerms`, its message and tests; `LogDiscomfort` takes a level; `severe` is derived from the level; the term-based wording leaves `04` §4.12, `07` §7, and the T-148 description (notes are not scanned). (3) **UI:** the discomfort sheet gets a required ordered level picker (least to most severe, colour chips from greenish yellow to red with a "Leve"–"Fuerte" guide) above the optional note; the fixed message and its `InlineNote` are removed; the open-discomfort row shows the level. ux-reviewer defines the levels, labels, colour tokens (distinct from the lime of "Guardar"), accessibility and copy; glossary and §8 updated. **Bug fixed here (reported at E12-A):** the note field has no top padding in the sheet (`design/reported/E12-A-discomfort-note-top-padding.png`) and the same defect is in the "Check-in semanal" UI (user, 2026-10-29): fix it in the shared `AppTextField`/multi-line field if that is the cause, and sweep every multi-line field on every screen and sheet. (4) **Rating-scale help (R-45, user feedback 2026-10-29):** the weekly check-in "Energía" asks for 1-5 with no explanation of what each value means. Per R-45 (new, `05` §8) every numeric or ordinal scale shows a `HelpButton` (R-32) opening an Info sheet that lists what each value means; sweep the check-in Energía and every other scale in the app (list them in the plan; RPE already has its help); ux-reviewer writes the Spanish copy per value and, if a scale repeats this finding, proposes a `check-ux-conventions.sh` guard.

**Acceptance criteria:** (the planner writes the final Gherkin)
- [x] Logging requires choosing a level; the note stays optional; the stored row keeps the level.
- [x] The list runs from least to most severe with colours from greenish yellow to red and a text label per level.
- [x] No strong-pain message appears for any note or level; no term matching remains in the code.
- [x] The note field shows its text fully (top padding), in the Molestia sheet, "Check-in semanal" and every other multi-line field.
- [x] Every rating scale (check-in "Energía" and the rest found in the sweep) has a help button whose sheet lists what each value means (R-45); host tests per scale.
- [x] pgTAP, host tests, lint green; `deploy-supabase` green after the merge; docs updated (`03`, `04` §4.12, `05`, `07` §7, ADR-81 ticked).

**Dependencies:** T-147 merged; ADR-81 agreed with the user (2026-10-28). Checkpoint E12-A is re-presented after this task (re-test items 6-7 and the sheet padding); T-148 follows its approval.
**Estimated scope:** L

---

## T-148: Notes per set and per routine exercise, and the chosen rest sound (US-66) · L

**Story:** US-66 (+ US-67 technical note: the term list applies to notes; + the sound choice of Checkpoint E12-A, US-64) · **Flow:** backend + core + UI · **Size:** L (the planner may run backend + core and UI as two developer slices of the same PR) · **Scenarios:** Note on a set; Note on an exercise in the routine

**Description:** (1) **Backend:** one migration: `workout_sets.notes` and `routine_exercises.notes` (text nullable, check 1-200 chars; blank stored as null); `save_routine` accepts and returns the per-exercise note; the paths that write sets (live set save, `save_completed_workout`, the "Editar entreno" save) carry the set note; `save_workout_as_routine` and `update_routine_from_session` leave routine notes untouched unless the planner finds a copy path needed; the reads used by the workout detail and the live session return the notes; pgTAP (length checks, RPC round trips, RLS unchanged). (2) **Domain:** `WorkoutSet.notes`, `RoutineExercise.notes`, DTO mapping; a note matching `StrongPainTerms` (T-147; the 12 terms of E12 Q1, plain "dolor"/"molestia" excluded) shows the same fixed message "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario." when saved (US-67 note); it does not create a discomfort (assumption A5). (3) **UI — templates and patterns:** **set note** in the live session: an action of the set (where the set's existing actions live, e.g. its set-type/RPE sheet or a row action; ux-reviewer picks per R-19/R-02) opens an Edit sheet (`AppEditSheet`, one multi-line `AppTextField` "Nota", "Máximo 200 caracteres", pinned "Guardar", R-38 editable-sheet dismissal), shown under the set as an `InlineNote`; the same note is editable in "Editar entreno" (assumption A6) and shown read-only in the workout detail (`SessionDetailScreen`/`PastWorkoutScreen`) under its set. **Routine exercise note** in the routine builder: an action of the exercise (its ⋮ or row action, ux-reviewer picks) opening the same Edit sheet, shown as an `InlineNote` under the exercise, saved with the routine's "Guardar" (R-11; "Listo" in the sheet if it applies to the builder draft, R-12); shown read-only in the live session under that exercise's header. Removing a note = clearing the field and saving. Spanish copy by ux-reviewer per §10. **Sweep:** the Edit sheet for a note is one shared composable used by sets, routine exercises and T-147's discomfort note if they converge (ux-reviewer decides; no ad-hoc copies, R-39). (5) **Checkpoint E12-A visual feedback (user, 2026-10-29; approved, no separate task):** in the discomfort sheet add clear vertical space between the last level ("Dolor agudo o lesión") and "Nota (opcional)" (today too tight); **remove the "Molestia registrada" label** (the level now says it all) and rework how the open discomfort shows in the live session: the level chip/dot (e.g. the orange dot with "Pinchazo o chasquido"), the level text and the "Ya no me molesta" action must read as one coherent block (ux-reviewer defines it; update the Gherkin literal "Molestia registrada" in the E12 epic file, `04` §4.12 and the glossary §10; tests that assert the label change accordingly). (4) **Sweep from Checkpoint E12-A (sound choice):** the sound the user chose becomes the only rest-alert sound (in-app and the final notification channel, with a new channel ID if T-146's channel had another sound); delete the other candidates, the temporary chooser screen and its "Acerca de" row; `04` §4.4 names the sound; the resolution of E9 point 16 is recorded there and in the PR body (the frozen `planning/phase1/` audit is not edited).

**Acceptance criteria:**
- [x] Writing "pausa larga" on a set and finishing the workout → the note is stored with the set and shown under that set in the workout detail; editable in "Editar entreno".
- [x] Routine with "bajar el peso si duele" on "Press banca" → starting the routine shows the note under "Press banca" in the live session.
- [x] A note longer than 200 characters shows "Máximo 200 caracteres" and "Guardar" stays disabled; a cleared note is removed.
- [x] Discomfort sheet: visible space between the last level and "Nota (opcional)"; "Molestia registrada" no longer appears anywhere; level, dot and "Ya no me molesta" are one coherent block.
- [x] A note shows no health message and is never scanned for terms (ADR-81); it uses the fixed `AppTextField` (top padding, T-152).
- [x] The sound chosen at Checkpoint E12-A plays in the foreground and with the screen off; the chooser, its "Acerca de" row and the other candidates are gone.
- [x] pgTAP: note length checks, `save_routine` round trip of notes, set-note write paths, RLS unchanged.
- [x] `planning/03-data-model.md` (columns, RPC contracts) updated.
- [x] The note sheet draft, the routine builder draft and the live session survive background and process death (ADR-40).

**Host tests:** repository/DTO mapping tests for both notes; `save_routine` payload test; live-session ViewModel tests (add, edit, clear a set note; routine note loaded; no message for any wording); routine builder ViewModel tests (note in the draft, saved with the routine, discard dialog when dirty); Robolectric tests (set note sheet and `InlineNote`, routine note in the live session, workout detail shows the note, length validation); alert-sound test for the final channel and sound; restoration tests; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-149 starts).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 22 files (if the planner counts more than ~25, backend + core and UI run as developer slices of the same PR).
**Dependencies:** Checkpoint E12-A approved with the sound chosen; T-147's matcher and fixed message (E12 Q1 resolved 2026-10-28)
**Files likely touched:** `supabase/migrations/<ts>_training_notes.sql`, `supabase/tests/training_notes.test.sql`, `shared/…/domain/model/{Workout,Routine}.kt`, `shared/…/data/remote/*Dto.kt`, `shared/…/data/repository/{SupabaseWorkoutRepository,SupabaseRoutineRepository}.kt`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen,EditWorkoutScreen,SessionDetailScreen,PastWorkoutScreen}.kt`, `feature/routines/{RoutineBuilderScreen,RoutineBuilderViewModel}.kt`, a shared note sheet composable, `composeApp/…/platform/AndroidAlertSound.kt` and the notification channel, removal of the temporary chooser and its "Acerca de" row, strings, `planning/03-data-model.md`, `planning/04-business-rules.md` §4.4, tests
**Estimated scope:** L

---

## T-149: "Mi entrenamiento": training intention and static preferences (US-65) · M

**Story:** US-65 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** Intentions are a fixed list; Choosing the intention; Place defines the default equipment; Days and session length are optional

**Description:** (1) **Backend:** one migration: enums `training_intention` (`strength`, `muscle`, `fat_loss`, `recomposition`, `maintenance`) and `training_place` (`home`, `gym`, `other`); `training_preferences` gains `intention` (nullable until chosen), `place` (nullable until chosen), `equipment equipment[]` (not null default `'{}'`), `days_per_week` smallint nullable check 1-7, `session_minutes` smallint nullable, check 15-180 and a multiple of 5 (E12 Q2, resolved by the user 2026-10-28); the save path the Preferencias sections already use for `training_preferences` carries the new fields, and a section saves only its own fields (as the hub's other sections); pgTAP (enum values, checks, RLS unchanged, saving this section leaves the other `training_preferences` fields intact). (2) **Domain:** `TrainingIntention`, `TrainingPlace`, `TrainingPreferences` gains the five fields; a pure `DefaultEquipment.forPlace` (home = dumbbell, band, bodyweight; gym = every `equipment` value; other = bodyweight; epic design decision); DTO mapping. The intention does not touch the nutrition goal (epic note; E13). (3) **UI — templates and patterns:** a new Preferencias hub `NavigationRow` "Mi entrenamiento" (Gherkin literal; value = the chosen intention label, or none) next to the existing "Entrenamiento" section, which keeps the app settings (warm-up, calculators; assumption A7); it opens an `AppEditorScreen` "Mi entrenamiento" (R-01/R-04): intention among "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición", "Mantenimiento" (the labels are long, so not a `SegmentedSelector` per R-33: a `NavigationRow`-style field opening an `AppPickerSheet` that applies on tap, or `AppToggleChip`s; ux-reviewer picks); place "Casa" / "Gimnasio" / "Otro" as a `SegmentedSelector` (3 short options; labels beyond "Casa" by ux-reviewer); equipment as multi-select `AppToggleChip`s with the catalogue's existing Spanish equipment labels ("Mancuernas", "Banda", "Peso corporal", …); choosing a place replaces the draft's equipment with that place's defaults, which the user can then change (assumption A8); days per week (optional, 1-7) and session length (optional, 15-180 min in 5-min steps, e.g. an `AppPickerSheet` or stepper, ux-reviewer picks) with a way to leave them empty. **Session-length text:** the picker options and the shown value reuse the app's existing duration formatter `durationText(seconds: Long)` in `feature/analytics/stats/TrainingStatsFormat.kt` (strings `stats_duration_m` "%1$d min", `stats_duration_hm` "%1$d h %2$d min"), which yields "15 min", "45 min", "1 h 0 min", "1 h 5 min", "1 h 30 min", "3 h 0 min"; no ad-hoc copy (R-39): since a third feature now uses it (statistics, Actividad, Preferencias), move it into `designsystem/` (next to `AppNumberFormat`/`AppDateFormat`, R-44) and repoint the existing callers, with no change to its output; pinned "Guardar" (R-09/R-10), Confirm haptic and close (R-13), "¿Descartar los cambios?" when dirty (R-14), offline hint (R-30); loading skeleton and `ErrorState` "Reintentar". Glossary (§10) gains the intention and place labels. **Sweep:** if the hub or the "Entrenamiento" section renders equipment labels ad hoc, reuse one equipment-label source.

**Acceptance criteria:**
- [x] "Mi entrenamiento" offers exactly "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición" and "Mantenimiento".
- [x] Choosing "Ganar fuerza" + "Guardar" → stored as `strength`; the hub row and the screen show "Ganar fuerza" after reopening the app.
- [x] Choosing "Casa" → equipment "Mancuernas", "Banda", "Peso corporal" selected; the user can add or remove equipment before saving; "Gimnasio" selects every value; "Otro" selects "Peso corporal".
- [x] Saving without days or session length stores them as null; the session length offers only 15-180 min in 5-min steps, each shown by the shared duration formatter ("45 min", "1 h 0 min", "1 h 30 min"), and the saved value is shown the same way; the database rejects other values (check).
- [x] Saving "Mi entrenamiento" does not change warm-up, calculator or previous-values settings, nor the nutrition goal.
- [x] pgTAP: enums, `days_per_week` and `session_minutes` checks, owner RLS, other fields untouched.
- [x] `planning/03-data-model.md` and `04-business-rules.md` (default equipment per place; intention is static) updated.
- [x] The editor draft and scroll survive background and process death (ADR-40).

**Host tests:** `DefaultEquipmentTest` (three places); duration-formatter test after the move (15, 45, 60, 65, 90, 180 min, and the existing statistics/Actividad cases unchanged); repository/DTO mapping tests (nulls, enum mapping, equipment array); Preferencias ViewModel tests for the section (load, choose intention, place resets equipment, optional fields, validation, save only own fields, offline, error); Robolectric tests (hub row value, editor controls, picker applies on tap, discard dialog); restoration test; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-150 starts).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 14 files.
**Dependencies:** T-148 merged and `deploy-supabase` green (no checkpoint in between); E12 Q2 resolved (2026-10-28: 15-180 min, 5-min steps)
**Files likely touched:** `supabase/migrations/<ts>_training_intention_preferences.sql`, `supabase/tests/training_preferences.test.sql`, `shared/…/domain/model/TrainingPreferences.kt`, `shared/…/domain/usecase/DefaultEquipment.kt`, `shared/…/data/remote/TrainingPreferencesDto.kt`, `shared/…/data/repository/SupabaseTrainingPreferencesRepository.kt`, `feature/profile/{PreferencesHubScreen,PreferencesSection,PreferencesSectionScreen,PreferencesViewModel,PreferencesDraft,PreferencesUiState}.kt` (+ a `MyTrainingSection.kt`), `feature/analytics/stats/TrainingStatsFormat.kt` → `designsystem/` (duration formatter move) and its callers (`TrainingStatsSections.kt`, `feature/activity/ActivityMetricUi.kt`), strings, `planning/03-data-model.md`, `planning/04-business-rules.md`, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** M

---

## T-150: Movement pattern in the exercise catalogue and the custom exercise form (data task) · M

**Story:** E12 data task "Movement pattern in the catalogue" (ADR-78, ADR-39) · **Flow:** backend + data + small UI · **Size:** M (stays alone: the reviewed mapping covers every catalogue row; merging with T-149 would put two unrelated forms and two migrations in one PR)

**Description:** (1) **Mapping:** the planner checks whether the RepDB bundle (`library.json`, generated by `scripts/import-repdb.py`) has a field equivalent to the movement pattern; where it does, `scripts/generate-exercise-catalog.py` derives it; otherwise a reviewed mapping keyed by catalogue `key` (e.g. `scripts/exercise-movement-patterns.csv`, versioned, no third-party text) gives each catalogue row one of `squat`, `hinge`, `lunge`, `horizontal_push`, `vertical_push`, `horizontal_pull`, `vertical_pull`, `carry`, `core`, `isolation`, `cardio`, `other`; the generator emits the column for future regenerations. 👤 Prerequisite only if the generator must be re-run against the bundle: the user's local RepDB bundle (as in E4). (2) **Backend:** one migration: enum `movement_pattern`, `exercises.movement_pattern` nullable, an update that fills every catalogue row (`is_custom = false`) from the mapping; the custom exercise create/edit path accepts it (optional); pgTAP: every catalogue row has a pattern, custom rows may be null, owner rules on custom rows unchanged. (3) **Domain/data:** `MovementPattern` enum and `Exercise.movementPattern` with DTO mapping. (4) **UI — templates and patterns:** the custom exercise form ("Nuevo ejercicio" sheet and the custom exercise edit) gains an optional "Patrón de movimiento" field opening an `AppPickerSheet` (single choice applied on tap, R-12; an option to leave it empty; closes on an outside tap, R-38) with Spanish labels by ux-reviewer (§10 glossary entries); not shown for catalogue exercises (assumption A9). Used by the coach in E13; no other screen shows it.

**Acceptance criteria:**
- [x] After the migration every catalogue exercise has a non-null `movement_pattern`; spot checks: a back squat is `squat`, a bench press `horizontal_push`, a pull-up `vertical_pull`, a biceps curl `isolation` (pgTAP by key).
- [x] Creating a custom exercise without a pattern saves it with none; with "Patrón de movimiento" chosen, it is stored and shown again when editing.
- [x] The mapping covers every catalogue key (a test or script check fails on a missing key).
- [x] `planning/03-data-model.md` (enum, column) updated.
- [x] The custom exercise form draft survives background and process death (ADR-40).

**Host tests:** DTO mapping test (pattern present/null); custom-exercise ViewModel tests (optional pattern, save, edit); Robolectric tests of the field and the picker sheet; a mapping-coverage check (script or test); pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before Checkpoint E12-B is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 11 files (the mapping file is long but mechanical).
**Dependencies:** T-149 merged and `deploy-supabase` green
**Files likely touched:** `scripts/generate-exercise-catalog.py`, `scripts/exercise-movement-patterns.csv` (or derived from the bundle), `supabase/migrations/<ts>_movement_pattern.sql`, `supabase/tests/exercises_movement_pattern.test.sql`, `shared/…/domain/model/Exercise.kt`, `shared/…/data/remote/ExerciseDto.kt`, `shared/…/data/repository/SupabaseExerciseRepository.kt`, `feature/exercises/` custom exercise form + ViewModel, strings, `planning/03-data-model.md`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-153: Checkpoint E12-B feedback fixes (notes, "Mi entrenamiento", pickers, check-in names) · L

**Source:** user feedback at Checkpoint E12-B, 2026-10-29 (screenshots: `design/reported/E12-B-live-set-note-button.png`, `design/reported/E12-B-checkin-records-ejercicio-name.png`; delete them once the user confirms the fix). New rules R-46…R-49 and the Perfil decision are already in `05` §8 (same docs PR); the task sweeps every screen with those patterns (R-39). **Flow:** UI + small core; planner may split into two PRs if it exceeds ~15 files (then T-154).

1. **Set-note button (R-49) — the live session layout is broken since T-148 added it** (`design/reported/E12-B-live-set-note-button.png`): the note icon sits alone on a line under each set row;  integrate it into the set row (trailing slot or inside the row layout) in the live session and every other place that shows set notes.
2. **Notes redesigned (R-46, ADR-82, written in the feedback PR, user decisions 2026-10-29):** a note is a chosen option plus an optional free text ("Otro", max 200 chars), like the discomfort levels. Set options (only what kg, reps, RPE and rest do not say): "Técnica mejorable", "Rango incompleto", "Agarre flojo", "Con ayuda", "Otro". Routine-exercise options: "Subir peso", "Bajar peso", "Cuidar la técnica", "Ajustar máquina o asiento", "Otro". No pain option (that is "Registrar molestia"). Each list has a `?` Info sheet saying what each option means and which note is for the workout and which for the routine. Backend: `note_kind` enum column on `workout_sets` and `routine_exercises` (migration + pgTAP; existing free-text notes become `other`); the same decision is in `03`, `04`, `05` §10 and US-66 (amended in this PR). A routine-exercise note edited in the live session counts as a routine change and triggers the existing "update the routine?" prompt; declined, it stays only in that workout. Discomforts never count as a routine change.
3. **"Mi entrenamiento" in the Perfil root (decided):** its own row in the Perfil root (not inside Preferencias); update `05` §6.2, navigation, tests and the Gherkin/UI literals that say "Preferencias → Mi entrenamiento".
4. **Non-obvious options (R-46):** `HelpButton` with an Info sheet on the training intention (what "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición", "Mantenimiento" mean and how they differ), and a sweep of every other option whose meaning is not obvious (list them in the plan). Consider a `check-ux-conventions.sh` guard if feasible.
5. **No dropdowns (R-47):** "Días por semana" and "Duración del entreno" use the "Descanso" / "Objetivo RPE" control of the routine editor; the shared duration formatter drops zero parts ("1 h", not "1 h 0 min"); sweep statistics and every caller.
6. **"Añadir ejercicio propio" pinned (R-48):** fixed at the bottom of the exercise selection; the list scrolls above it.
7. **Bug: Check-in semanal "Récords de la semana" shows "Ejercicio" instead of the exercise name** for catalogue exercises (only the custom "Hand grip" shows its name; the name lookup apparently covers custom exercises only). Show the real name for every exercise; add a regression test; sweep other places that resolve exercise names (Diario, statistics, coach notes).

**Acceptance criteria:** the planner derives the Gherkin from items 1-7; host tests per item; the bug has a regression test; lint + `testLocalDebugUnitTest` green.
**Dependencies:** none · **Estimated scope:** L

---

## T-154: Checkpoint E12-B feedback fixes, part 2 ("Mi entrenamiento" row, help, steppers, pinned create, check-in names) · L

**Source:** split of T-153 by the planner (user feedback at Checkpoint E12-B, 2026-10-29); plan in `.workflow/current_task.md`. Covers T-153 items 3-7: "Mi entrenamiento" row in the Perfil root (R-46 help on the training intention); `NumberStepper` for "Días por semana" and "Duración del entreno" (R-47; "Sin definir" is the lowest step, session step 5 min; `durationText` drops zero parts); "Añadir ejercicio propio" pinned (R-48); check-in records show real exercise names (inject `ExerciseLibrary` into `CheckInViewModel`, regression test; sweep other name lookups).
**Dependencies:** T-153 · **Estimated scope:** L

---

## T-155: Checkpoint E12-B feedback fixes, round 3 (live set table alignment, check-in "Ejercicio") · M

**Source:** user feedback at Checkpoint E12-B, 2026-10-29 (screenshots: `design/reported/E12-B-live-set-table-alignment.png`, `design/reported/E12-B-checkin-records-ejercicio-name-2.png`; delete them once the user confirms the fix). New rule R-50 in `05` §8 (same docs PR); the task sweeps every table-like screen (R-39).
1. **Live set table (R-50):** remove the "Nº" column and the per-set numbers; keep "Tipo" (set type badge). Header labels and every row share the same column widths; "KG" (and REPS, RPE, LISTO) centred over their fields. Sweep the workout editor and any other set table.
2. **Dropped (user, 2026-10-29):** the "Ejercicio" name in the check-in records came from a manually created exercise in the user's test account and is already fixed; new accounts are not affected. No code change.
**Dependencies:** T-154 · **Estimated scope:** M

---

### Checkpoint E12-B — Notes, your training situation, and the final E12 regression 👤 (7 items + leave and come back) *(agreed with the user, 2026-10-28: two checkpoints)*
Journey: the rest alert now plays the sound you chose; write what happened on a set and read the note you left on a routine exercise; tell the app your goal, where and with what you train, and how many days and minutes you have; give a movement pattern to your own exercises; then one pass over the whole epic. Why here: right after T-150, the last implementation task, so it is also the final regression of E12; its approval releases T-151.
- [x] T-148, T-149 and T-150 merged, CI green, `deploy-supabase` applied their three migrations (data intact), every reported bug fixed.
- [x] T-153 and T-154 (checkpoint feedback fixes) merged, CI green.
- [x] T-155 (feedback round 3: live set table; check-in names item dropped) merged, CI green.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-29; feedback → T-153; re-test every item, plus: the note button inside the set row, the `?` help on notes and training intention, "Mi entrenamiento" row in Perfil, durations without dropdowns, "Añadir ejercicio propio" pinned, real exercise names in the check-in records)*:
  1. Chosen sound (T-148, from Checkpoint E12-A): a rest ending in the foreground and one with the screen off both play the sound you chose; "Sonido de fin de descanso" is gone from "Acerca de".
  2. Notes (US-66): write "pausa larga" on a set, finish the workout: the note shows under that set in the workout detail; edit it in "Editar entreno"; in a routine add "bajar el peso si duele" to "Press banca" and save; start the routine: the note shows under "Press banca".
  3. Note limits: a note over 200 characters is refused with "Máximo 200 caracteres"; a note with "dolor fuerte" shows no message (ADR-81); airplane mode disables "Guardar" with the standard hint.
  4. Intention (US-65): Preferencias → "Mi entrenamiento" offers "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición" and "Mantenimiento"; choose "Ganar fuerza", "Guardar": the hub row shows it, also after reopening the app; "Entrenamiento" (warm-up, calculators) is unchanged.
  5. Place, equipment, days and length (US-65): choose "Casa": "Mancuernas", "Banda" and "Peso corporal" get selected; add or remove one; try "Gimnasio" (everything) and "Otro" (peso corporal); save with no days and no session length: it saves; set 4 days and a session length: the options run from "15 min" to "3 h 0 min" in 5-min steps, shown like the statistics durations ("45 min", "1 h 30 min"), and the saved value reads the same way.
  6. Movement pattern (data task): create an "ejercicio propio" with "Patrón de movimiento" and one without: both save; editing shows the chosen pattern.
  7. Final regression of E12: a full workout with the rest alert (screen off), a set note and a discomfort ("Molestia registrada", "Ya no me molesta"); Diario, Peso, Actividad and "Centro de avisos" work as before.
  - Leave the app and come back with "Mi entrenamiento" half edited, and once with a set-note sheet half typed: the same tab, screen and draft are restored.
  - Out of scope: the coach using the intention, preferences, discomfort or movement patterns (E13); removing the target weight (E13, ADR-78); an RIR field (never, ADR-76); water and voice (Phase 3).

---

## T-151: E12 closure → `v1.x` · S

**Story:** E12 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E12-B's approval)

**Description:** Closes E12 once Checkpoint E12-B is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) per the versioning rule (`06` §6; minor per epic, proposed `1.3.0`), tag, retrospective, archive of the E12 log lines. No 👤 checkpoint of its own.

**Acceptance criteria:**
- [x] T-146…T-150 (and any fix tasks) merged with green CI; `deploy-supabase` green after each of the four migrations.
- [x] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [x] `planning/02-architecture.md` (rest-alert technique), `03-data-model.md` and `04-business-rules.md` describe the alert, `exercise_discomforts`, the notes, the new `training_preferences` fields and `movement_pattern`.
- [x] Checkpoints E12-A and E12-B approved; every reported bug fixed.
- [x] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [x] `git tag` lists the E12 tag.

**Dependencies:** T-150 and Checkpoint E12-B approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E12.md`
**Estimated scope:** S


## T-156: Wrist roller takes a weight (fix) · S

**Source:** user report, 2026-10-29: "Rodillo de Muñeca" shows only reps in the live session; it must take a weight (a plate hangs from the rope). **Flow:** backend + data · **Size:** S

**Description:** remove `wrist-roller` from `REPS_ONLY_KEYS` in `scripts/generate-exercise-catalog.py` (it is listed twice) so its metric type is weight × reps, and ship the regenerated catalogue migration (ADR-39) with pgTAP; check no other catalogue key with a hung or added load is wrongly reps-only. Not in scope: hiding the weight field in the routine builder for reps-only exercises.

**Acceptance criteria:**
- [x] `wrist-roller` has the weight × reps metric type in the catalogue; the live session shows the kg column for it and records weight with reps.
- [x] Generator, migration and pgTAP agree; `deploy-supabase` green on `main` after the merge.

---

# E11 · Health Connect daily sync → `v1.x`

Stories: `planning/epics/E11-health-connect-daily-sync.md` (US-60, US-61, US-62, US-63). Plan: [tasks/plan.md](plan.md). Decisions: ADR-74 (amends ADR-08; daily automatic trigger, new read types, writes stay weight and body fat), ADR-47/48 (a Health Connect weight wins), ADR-29 (cascade), ADR-40 (restoration), ADR-73 (no background job). Rules: `planning/phase1/04-business-rules.md` §3.7 (Health Connect import, dedup, own records skipped), `05-design-system.md` §8–§10. Device facts (user, 2026-10-28): the data source is Zepp (phone UI in Spanish) and Health Connect holds steps for at least 5 months.
Sizing: 5 tasks. The spike T-139 is S and stays alone (user decision: it is the first task, and its device report decides the first-sync window of T-141). US-61 + US-62 + US-63 are one functionality (watch data through "Sincronizar") but exceed ~15 files with heavy backend (3 tables, an import RPC, pgTAP) and UI that review apart, so they split into T-140 (backend, M) and T-141 (core + UI, L). US-60 is T-142 (M; no migration, its column ships in T-140). Closure T-143 stays S (it waits for Checkpoint E11-B).
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1); T-140 runs the backend flow (`06` §2.2). UI scenarios are covered by host (Robolectric) tests; the user verifies by hand with `python scripts/deliver-apk.py`.
Questions: E11 Q1 (first-sync window, decided by the spike result; **BLOCKING T-141**) in `tasks/plan.md`. E11 Q2 resolved by the user (2026-10-28): coming back from the background on a new local day counts as the first open. T-139, T-140 and T-142 are not blocked by questions.
Checkpoints (agreed with the user, 2026-10-28): 👤 spike report after T-139 (not a functionality checkpoint: one device result that gates T-141); **one Checkpoint E11** after T-142 (items of the watch data and the daily sync merged, also the final regression; releases T-143). T-141 merges with no checkpoint after it.

---

## T-139: Spike: `READ_HEALTH_DATA_HISTORY` on the device · S

**Story:** US-63 (History older than 30 days; technical note) · **Flow:** platform + minimal UI · **Size:** S (cannot merge: the user's device report decides the first-sync window of T-141; user decision that it is the first task of E11)

**Description:** Find out on the user's phone (Zepp → Health Connect, at least 5 months of steps) whether the app can read data older than 30 days. (1) Declare `android.permission.health.READ_HEALTH_DATA_HISTORY` and `android.permission.health.READ_STEPS` in `androidApp/src/main/AndroidManifest.xml` (steps is the type the user knows has 5 months of history; T-141 keeps or removes the history declaration according to the result). (2) Check `HealthConnectFeatures.getFeatureStatus(FEATURE_READ_HEALTH_DATA_HISTORY)` (available / unavailable) through the existing `HealthConnectDataSource` (new port method or a diagnostic helper; the planner chooses, keeping the port pure). (3) Request the steps read permission plus the history permission with the existing permission launcher. (4) Read steps (and weights, for comparison) in two windows, 60–90 days ago and 0–30 days ago, and count the records and the oldest record's date in each. (5) Show the result in a **temporary diagnostic** screen reachable from Perfil → "Acerca de" (one `AppListRow` "Diagnóstico de Health Connect" opening an `AppScreen` with plain `AppListRow`s: feature status, history permission granted yes/no, records 60–90 days ago, records 0–30 days ago, oldest record date; a "Comprobar" button that requests the permissions and runs the read). The screen and its row are removed by T-141 (sweep item there); its copy is not reviewed beyond R-36/R-37. Nothing is stored server-side.

**Acceptance criteria:**
- [x] The manifest declares `READ_HEALTH_DATA_HISTORY` and `READ_STEPS`; no new write permission.
- [x] "Comprobar" shows the feature status, whether the history permission was granted, and the record counts and oldest date for 60–90 and 0–30 days ago.
- [x] Denying the permissions shows them as not granted and reads nothing; Health Connect missing shows the existing unavailable state; no crash.
- [x] 👤 Spike report: the user runs "Comprobar" on the phone and reports the five values; the PM records them and the resulting branch of E11 Q1 in `tasks/plan.md` (docs PR) before T-141 starts.

**Host tests:** a diagnostic ViewModel test (granted, denied, feature unavailable, read failure) with a fake data source; a Robolectric test of the diagnostic screen (rows and "Comprobar"). The real Health Connect behaviour is verified only on the device (the existing data source is "verified by hand on a phone only").

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.
- [x] 👤 Spike report received and recorded (gates T-141).

**Agent runs:** planner, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer = 5 (no ux-reviewer passes: temporary screen removed in T-141).
**Dependencies:** E10 closed (`v1.1.0`); breakdown agreed by the user
**Files likely touched:** `androidApp/src/main/AndroidManifest.xml`, `composeApp/…/platform/HealthConnectDataSource.kt` (or a `HealthConnectDiagnostics.kt`), `composeApp/…/platform/HealthConnectLauncher.android.kt`, new `composeApp/…/feature/profile/HealthDiagnosticsScreen.kt` + ViewModel, the "Acerca de" screen, `app/{AppRoutes,AppNavHost}.kt`, strings, tests
**Estimated scope:** S

---

## T-140: Watch-data backend: daily activity, sleep, resting heart rate (US-61, US-62 backend) · M

**Story:** US-61, US-62 (server side), US-60 (`last_auto_sync_date` column) · **Flow:** backend + data · **Size:** M (split from T-141: together they exceed ~25 files with heavy backend and UI that review apart)

**Description:** One migration for the epic (so only one production deploy): (1) `daily_activity` (`user_id`, `day` date in the device time zone, `steps` integer, `active_kcal`, `total_kcal` numeric, nullable each, range checks), `unique (user_id, day)`; a re-sync of a day **replaces** its values. (2) `sleep_sessions` (`user_id`, `started_at`, `ended_at`, `minutes` integer, `stages` jsonb, `external_id` text not null), `unique (user_id, external_id)`, check `ended_at > started_at`. (3) `resting_heart_rate` (`user_id`, `day`, `bpm` smallint with a range check, `external_id`), `unique (user_id, day)`. (4) `health_sync_state.last_auto_sync_date date null`. Owner RLS enabled and forced with `(select auth.uid())`, explicit grants to `authenticated`, `on delete cascade` from `auth.users` (ADR-29; extend `delete_account_cascade.test.sql`). (5) An idempotent import path: one RPC `import_health_activity(p_daily jsonb, p_sleep jsonb, p_resting_hr jsonb)` (or one per type, planner's choice), `security invoker`, `set search_path = ''`, input caps and `22023` on bad input like `import_health_measurements`; daily rows and resting heart rate upsert per day (replace), sleep upserts per `external_id`; returns the counts. (6) An RPC or column update that sets `last_auto_sync_date` only on success (US-60 note: a failed automatic sync never sets it). (7) **Data layer:** domain models (`DailyActivity`, `SleepSession`, `RestingHeartRate`), the repository port(s) and the supabase-kt implementation with DTO mapping; `HealthSyncState` gains `lastAutoSyncDate`. No UI and no Health Connect reads here (T-141).

**Acceptance criteria:**
- [x] Importing the same day's steps twice leaves one row with the last values (US-62 "A second sync the same day adds nothing"); importing the same sleep `external_id` twice leaves one session.
- [x] A payload without resting heart rate stores none and returns no error (US-61 "A type the watch does not provide").
- [x] User A cannot read or write user B's rows in the three tables; deleting the account removes them.
- [x] `last_auto_sync_date` is set only by the success path.
- [x] `planning/03-data-model.md` describes the three tables and the column; `04-business-rules.md` gets the daily-total rule (one row per user and day, replaced on re-sync; day = device time zone).

**Host tests:** repository mapping tests (DTO ↔ model for the three types, nulls, `lastAutoSyncDate`); pgTAP: RLS enabled and forced and cross-user isolation per table, uniqueness, replace-on-resync for `daily_activity` and `resting_heart_rate`, sleep dedup by `external_id`, range checks, RPC input validation (`22023`), cascade on account deletion.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-141 starts).

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5.
**Dependencies:** T-139 merged (not its report: the backend does not depend on the window)
**Files likely touched:** `supabase/migrations/<ts>_health_activity.sql`, `supabase/tests/{daily_activity,sleep_sessions,resting_heart_rate}.test.sql`, `supabase/tests/delete_account_cascade.test.sql`, `shared/…/domain/model/Health.kt` (or new `HealthActivity.kt`), `shared/…/domain/repository/HealthSyncRepository.kt` (or a new `HealthActivityRepository.kt`), `shared/…/data/remote/HealthSyncDto.kt`, `shared/…/data/repository/SupabaseHealthSyncRepository.kt`, `shared/…/di/`, `planning/03-data-model.md`, `planning/04-business-rules.md`, tests
**Estimated scope:** M

---

## T-141: Read sleep, steps, calories and resting heart rate with "Sincronizar" (US-61, US-62, US-63) · L

**Story:** US-61, US-62, US-63 · **Flow:** platform + core + UI · **Size:** L · **Scenarios:** US-61 Last night's sleep is stored; Yesterday's steps are stored; A type the watch does not provide; Partial permission · US-62 A second sync the same day adds nothing; Records written by AppFitness are not imported · US-63 Write permissions are limited to weight and body fat; History older than 30 days (per the spike branch)

**First-sync window (E11 Q1, decided by the T-139 report; both branches planned):**
- **Branch A (history readable):** the feature is available, the permission is granted and records 60–90 days ago are returned. Keep `READ_HEALTH_DATA_HISTORY` declared; request it together with the read permissions ("the app asks for it before reading older data"); the first sync of the new types reads the last **90 days** (same window as the weight sync); US-63 "History older than 30 days" stays as written.
- **Branch B (history not readable):** the feature is unavailable or nothing older than 30 days is returned. Remove the `READ_HEALTH_DATA_HISTORY` declaration; the first sync of the new types reads the last **30 days** (what Health Connect allows); the weight sync keeps its 90-day request (Health Connect returns what it allows). US-63's second scenario and the E11 scope line are rewritten in the epic and `01-scope.md` with the user's OK when the report is recorded (product wording, not decided here).

**Description:** (1) **Platform:** `HealthConnectDataSource` reads `SleepSessionRecord` (with stages), `StepsRecord`, `ActiveCaloriesBurnedRecord`, `TotalCaloriesBurnedRecord` and `RestingHeartRateRecord`, each only when its read permission is granted (partial permission: a missing type is skipped, never an error); steps and calories as daily totals per local day (aggregation by day so overlapping records are not double-counted; planner confirms the API); own-origin records skipped as today (US-62). The Changes token covers the new types; touched days are re-read whole so a day total is replaced, never added to. (2) **Permissions (US-63):** the requested set = reads of the existing six types plus the five new ones (+ history per branch); writes stay `WRITE_WEIGHT` and `WRITE_BODY_FAT` only, and the manifest's unused `WRITE_LEAN_BODY_MASS`, `WRITE_BODY_WATER_MASS`, `WRITE_BONE_MASS`, `WRITE_BASAL_METABOLIC_RATE` declarations are removed; the rationale activity text covers the new types. A user who granted only the E5 types is asked again for the new ones from "Sincronizar". (3) **Core:** the sync use case (extend `SyncHealthMeasurements` or a `SyncHealthData` that composes it) imports every granted type in one "Sincronizar" and returns a per-type outcome; the weight path and its reminder refresh (T-135) are unchanged. Sleep belongs to the local day it ends on; several resting-heart-rate readings in a day keep the latest (assumptions A1–A2 in the plan). (4) **UI — templates and patterns:** where the values are shown is chosen by ux-reviewer in the plan pass (epic note; candidates: an "Actividad" `SectionHeader` on Peso under the Health Connect `MetricCard`, or a Perfil → "Estadísticas" destination). Expected patterns: `MetricCard`s (sueño de anoche with duration, pasos de ayer, calorías activas/totales, frecuencia cardiaca en reposo) with `AppNumberFormat`/`AppDateFormat`; a type with no data shows no card and no error; loading skeleton, `ErrorState` "Reintentar", offline per R-30. The existing "Sincronizar" button and status line on Peso stay where they are. Spanish copy by ux-reviewer per the glossary (§10). (5) **Sweep:** remove T-139's temporary diagnostic screen and its "Acerca de" row.

**Acceptance criteria:**
- [x] Granting every read permission and tapping "Sincronizar" stores last night's sleep with its duration, yesterday's steps (e.g. 8500 shows as 8500) and calories, and the resting heart rate when the watch writes it; the values are visible where ux-reviewer placed them.
- [x] No resting heart rate in Health Connect → none stored, nothing shown for it, no error.
- [x] Steps granted and sleep denied → steps stored, sleep not read, no error.
- [x] Tapping "Sincronizar" twice → yesterday's steps appear once with the same total; a weight written by AppFitness is not imported.
- [x] The app requests write access only for weight and body fat (manifest and requested set); the four unused write declarations are gone.
- [x] Branch A: the history permission is asked before older data is read and the first sync brings 90 days; Branch B: the first sync brings 30 days and no history permission is declared.
- [x] The diagnostic screen of T-139 is gone.
- [x] Empty, loading, error and offline states; the screen showing the values and its scroll survive background and process death (ADR-40).

**Host tests:** sync use-case tests with a fake data source (all types, partial permission, missing type, second sync idempotent, own-origin skipped, touched day re-read and replaced, first window 90/30 by branch, local-day assignment of sleep and daily totals across midnight); requested-permission-set test (reads, writes only weight and fat, history per branch); `HealthSyncViewModel` tests (per-type outcome, permission re-request for new types); Robolectric tests of the values surface (cards, no card for a missing type, states) and restoration.

**Verification:**
- [x] lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 20 files (no backend: T-140). No checkpoint after it: its items are tested at Checkpoint E11 after T-142.
**Dependencies:** T-140 merged and `deploy-supabase` green; T-139 spike report recorded (E11 Q1 resolved)
**Files likely touched:** `androidApp/src/main/AndroidManifest.xml`, `composeApp/…/platform/{HealthConnectDataSource,HealthConnectLauncher.android}.kt`, `shared/…/domain/port/HealthDataSource.kt`, `shared/…/domain/model/Health.kt`, `shared/…/domain/usecase/SyncHealthMeasurements.kt` (+ new `SyncHealthData.kt`), `feature/weight/{HealthSyncViewModel,WeightScreen}.kt` or the surface ux-reviewer picks, removal of `feature/profile/HealthDiagnosticsScreen.kt`, strings, `planning/phase1/04-business-rules.md` §3.7 pointer → `04-business-rules.md` (Health Connect read types), tests
**Estimated scope:** L

---

## T-142: Daily automatic sync on the first open (US-60) · M

**Story:** US-60 · **Flow:** core + UI · **Size:** M · **Scenarios:** First open of the day syncs; Second open the same day does not sync automatically; The button still syncs on demand; No connection keeps the last data; Permission missing

**Description:** (1) **Core:** a pure `ShouldAutoSync` rule (today's local date vs `last_auto_sync_date`, Health Connect available, online) and an app-level trigger (next to T-135's reminder refresh in `AppViewModel` or a shared holder) that runs the T-141 sync for every granted type on the first open of each local day, where a cold start and a return from the background (process `ON_START`) on a new local day both count as "the first open" (E11 Q2, resolved by the user 2026-10-28), never in the background (ADR-74). Success sets `last_auto_sync_date` (T-140); failure or offline leaves it, so the next open retries. The automatic sync **never** launches the permission dialog: with no read permission it reads nothing and the status line asks for permissions. One sync at a time: a tap on "Sincronizar" while the automatic sync runs is ignored or joins it (planner's choice, tested). After a sync that stores a weight, the reminder state refreshes (T-135 hook). (2) **UI — templates and patterns:** the existing Health Connect status line on Peso (`MetricCard` supporting text, T-123) gains the automatic outcomes: synced with its time ("the status line shows the time of the sync"), "the last sync was not possible" when offline or failed, and the permission request; copy by ux-reviewer per R-37 and the glossary (§10). No new screen and no new component. The "Sincronizar" button keeps reading on demand.

**Acceptance criteria:**
- [x] Last automatic sync yesterday → opening the app, or bringing it back from the background on the new day, reads every granted type and the status line shows the sync time.
- [x] Already synced automatically today → opening again reads nothing.
- [x] After today's automatic sync, a fresh weigh-in + "Sincronizar" → Health Connect is read and today's weight is shown (and the weight reminder clears).
- [x] Offline on open → stored data shown, the status line says the last sync was not possible, `last_auto_sync_date` unchanged; the next open online syncs.
- [x] No read permission → the status line asks for the permissions, no dialog opens by itself, nothing is read.
- [x] Status line and screen survive background and process death (ADR-40).

**Host tests:** `ShouldAutoSyncTest` (yesterday / today / never, local-midnight boundary, offline, unavailable); trigger tests (cold start and `ON_START` on a new day both trigger, runs once per day, failure does not set the date and retries on the next open, no permission → no read and no dialog, button during an automatic sync, reminder refresh after a weight); `HealthSyncViewModel` status-line tests for the automatic outcomes; Robolectric status-line test and restoration.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 12 files.
**Dependencies:** T-141 merged (no checkpoint in between; E11 Q2 resolved)
**Files likely touched:** `shared/…/domain/usecase/ShouldAutoSync.kt`, `shared/…/domain/usecase/SyncHealthData.kt`, `composeApp/…/app/AppViewModel.kt` (or the shared holder), `feature/weight/{HealthSyncViewModel,WeightScreen}.kt`, strings, `planning/04-business-rules.md` (daily trigger rule; `phase1` §3.7 "Sync is manual" superseded), tests
**Estimated scope:** M

---

## T-144: "Actividad" like "Peso": summary, Sincronizar, metric chart and calendar (US-64) · L

**Story:** US-64 · **Flow:** core + UI · **Size:** L (the planner may propose splitting it) · **Scenarios:** all of US-64 · **Origin:** user feedback at Checkpoint E11 (2026-10-28)

**Description:** The user expects "Actividad" to give the same experience as "Peso". (1) **Core:** queries of `daily_activity`, `sleep_sessions` and `resting_heart_rate` by range (1S, 1M, 3M, 6M, 1A) and by day; sleep stages only if the planner finds them already stored or a small migration is justified. (2) **UI:** a summary of the day with an icon per metric, the Health Connect status line with "Sincronizar" (reusing `HealthStatusLine` and the sync of T-141/T-142), a chart with a metric selector (default "Pasos") and range chips, and a calendar icon top right to pick a day. ux-reviewer decides the rendering of each metric (feet for steps, sleep distribution or a sleep figure, calorie and flame imagery, a heart for resting heart rate, animations), the Spanish copy and the testTags, reuses the Peso components (a new component retires ad-hoc copies, per the UX rule) and proposes the §8 rule "every screen with watch data has the status line and button"; the empty message of "Actividad" no longer sends the user to Peso.

**Acceptance criteria:**
- [x] Summary of today with the five metrics and their icons.
- [x] "Sincronizar" on "Actividad" syncs like on Peso and shows the status line.
- [x] Chart defaults to Pasos; the metric and the range (1S, 1M, 3M, 6M, 1A) switch.
- [x] Sleep shows the stage distribution when available, otherwise the duration.
- [x] The calendar picks another day; a day or metric without data says so.
- [x] Tab, metric, range, day and scroll survive leaving the app and process death (ADR-40).

**Host tests:** range and day query tests; ViewModel tests (default metric, switching, calendar, empty states, sync); Robolectric screen and restoration tests.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-142 merged
**Files likely touched:** to be defined by the planner (`feature/activity/…`, shared repository and queries, strings, `05-design-system.md` §8)
**Estimated scope:** L

---

## T-145: Checkpoint E11 fixes: missing watch data, sleep axis format, outside-tap dismissal (fix) · M

**Story:** US-61, US-62, US-64 · **Flow:** investigation + core + UI · **Origin:** user feedback at Checkpoint E11 (2026-10-28); the planner may propose splitting it

**Description:** (1) **Missing watch data (bug, confirmed on the device by the user, 2026-10-28):** Actividad shows only the last week of sleep, active and total calories and resting heart rate, but Health Connect (Zepp) holds them back to: sleep 18 September, active calories 27 September, total calories 20 September, resting heart rate 13 September (Pasos is correct, from about 1 August). The expectation is each type from the oldest date Health Connect holds within the 90-day first window (branch A) up to yesterday, nothing ignored. Working hypothesis (unverified): `SyncHealthData` reads 90 days only while `last_auto_sync_date` is null and 7 days afterwards (`LATER_WINDOW`); the date can be set by an automatic sync that ran before those types were granted, so once granted only 7 days were ever read. The developer confirms or refutes the cause and fixes it so a type granted or read for the first time gets its 90-day read regardless of the global date (for example a per-type "first read done" state, or a first-read check against the stored data), with a regression test; days already missed are backfilled by the next sync, with no manual reset by the user. (2) **Sleep chart axis:** hour labels show one decimal at most (`7.0h`, never `7.0000000001h`); sweep every axis and value label that formats a float (`AppNumberFormat`). (3) **Outside-tap dismissal (ADR-80):** pickers and read-only sheets close on an outside tap; sheets with editable input keep X/back/Cancelar. Update the sheet templates in `designsystem/`, sweep every sheet in the app, update guard G11 and its fixtures, and `05` R-38 is already amended.

**Acceptance criteria:**
- [x] After "Sincronizar", Pasos, sleep, active and total calories and resting heart rate appear for every day Health Connect holds, from the first-sync window up to yesterday.
- [x] A regression test reproduces the cause of the gaps.
- [x] No chart axis or label shows more than one decimal where one is intended (sleep hours `X.Xh`).
- [x] Tapping outside the calendar and the other pickers or read-only sheets closes them; editor sheets do not close on an outside tap.
- [x] `check-ux-conventions.sh` and its fixtures reflect ADR-80.

**Host tests:** sync/data tests for the root cause; number-format tests; Robolectric outside-tap tests per sheet archetype; guard fixtures.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-144 merged
**Estimated scope:** M

---

### Checkpoint E11 — Your watch data, synced by itself each day 👤 (13 items + leave and come back) *(agreed with the user, 2026-10-28: one checkpoint for the whole epic)*
Journey: grant the new permissions once and see last night's sleep, yesterday's steps, calories and resting heart rate, never duplicated; from then on the app brings them on the first open of each day, and the button still brings a fresh weigh-in. Why here: right after T-142, the last implementation task, so it is also the final regression of E11; its approval releases T-143. The user chose one checkpoint over two (E11-A after T-141 and E11-B after T-142).
- [x] T-140, T-141, T-142, T-144 and T-145 merged, CI green, `deploy-supabase` applied T-140's migration (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-28)*:
  1. Permissions (US-63): tap "Sincronizar": Health Connect asks to read sleep, steps, active and total calories and resting heart rate (and, in branch A, older history); in Ajustes → Health Connect → AppFitness, write access lists only weight and body fat.
  2. Sleep (US-61): after the sync, last night's sleep appears with its duration; compare it with Zepp.
  3. Steps and calories (US-61): yesterday's steps and calories match Zepp's figures for yesterday.
  4. Resting heart rate (US-61): if Zepp writes it, it appears; if not, nothing is shown for it and there is no error.
  5. Partial permission (US-61): in Health Connect revoke sleep only, tap "Sincronizar": steps still update, no sleep is read, no error; grant it again.
  6. No duplicates (US-62): tap "Sincronizar" twice: yesterday's steps appear once with the same total; log a weight in the app, sync: it is not imported a second time.
  7. History (US-63): the first sync brings older days (90 days in branch A, about 30 in branch B).
  8. First open of the day (US-60, Q2): open the app in the morning, or bring it back from the background on a new day, without touching anything: Peso's status line shows this morning's sync time and last night's sleep and yesterday's steps are there.
  9. Second open (US-60): close the app and open it again the same day: the status line keeps the same time (no new automatic sync).
  10. Fresh weigh-in (US-60): weigh yourself on the scale, then "Sincronizar": today's weight appears and the weight reminder in "Centro de avisos" disappears.
  11. Offline and permission missing (US-60): on a new day open the app in airplane mode: your data is shown and the status line says the last sync was not possible; reconnect and reopen: it syncs. Revoke the read permissions and open on a new day: the status line asks for them, no dialog opens by itself, nothing new is read.
  12. Actividad like Peso (US-64, T-144): summary of today with icons, "Sincronizar" button, chart defaulting to Pasos with 1S/1M/3M/6M/1A, metric switch, calendar icon to pick a day.
  13. Final regression of E11: Peso, its chart and weight history, "Centro de avisos" and the check-in, Diario and Entreno work as before; no value appears twice.
  - Leave the app and come back on the screen with the watch data scrolled down: the same tab, screen and scroll are restored.
  - Out of scope: background sync (never, ADR-74); writing food or training to Health Connect; water (Phase 3); the coach reading these values (E13).

---

## T-143: E11 closure → `v1.x` · S

**Story:** E11 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E11's approval)

**Description:** Closes E11 once Checkpoint E11 is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) per the versioning rule (`06` §6; minor per epic, proposed `1.2.0`), tag, retrospective, archive of the E11 log lines. No 👤 checkpoint of its own.

**Acceptance criteria:**
- [x] T-139…T-142 (and any fix tasks) merged with green CI; `deploy-supabase` green after T-140's migration.
- [x] ux-reviewer ran in plan and diff mode on T-141 and T-142; `check-ux-conventions.sh` passes.
- [x] `planning/03-data-model.md` and `04-business-rules.md` describe the three tables, `last_auto_sync_date`, the read types and the daily trigger.
- [x] Checkpoint E11 approved; every reported bug fixed.
- [x] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [x] `git tag` lists the E11 tag.

**Dependencies:** T-145 and Checkpoint E11 approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E11.md`
**Estimated scope:** S

---

# E10 · In-app reminders and weekly check-in → `v1.x`

Stories: `planning/epics/E10-reminders-and-check-in.md` (US-57, US-58, US-59). Plan: [tasks/plan.md](plan.md). Decisions: ADR-73 (amends ADR-66 and the check-in part of E8 US-44); navigation stays 3 tabs (ADR-37; the Trainer tab arrives with E13, ADR-79). Rules: `planning/05-design-system.md` §8–§10 (R-##), ADR-40 restoration, `04` §3 (weight daily value), §4.2/§4.8/§4.11 (volume, records, statistics), §5 formatting. No note for E10 in "Notes for later epics".
Sizing: 3 tasks. US-58 alone is S (a pure domain rule with no screen of its own), so it is merged into US-57 (T-135, L): the center has nothing to list without it. US-59 is L on its own (T-136). The closure T-137 stays S because it cannot merge: it waits for Checkpoint E10's approval.
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1). UI scenarios are covered by host (Robolectric) tests; the user verifies by hand with `python scripts/deliver-apk.py` at the 👤 checkpoint.
Questions: E10 Q1–Q3 resolved by the user on 2026-10-28 (`tasks/plan.md`): Q1 B (the check-in is listed from the chosen day until answered or until the next check-in day), Q2 as recommended (volume kg + workouts + sets; records first set this week; weight trend = this week's average − last week's, "Sin datos suficientes" when a week has no weigh-in), Q3 B (an answered check-in stays reachable from "Centro de avisos" and editable until its week ends). Default check-in day stays Sunday. No task is blocked.
Checkpoints (agreed with the user, 2026-10-28): **one Checkpoint E10** after T-136 with all 14 collected items (user decision, overriding the 5–7 grouping), also the final regression of E10; its approval releases T-137. T-135 merges with no checkpoint after it.

---

## T-135: "Centro de avisos" with weight and circumference reminders (US-57 + US-58) · L

**Story:** US-57, US-58 · **Flow:** backend + core + UI · **Size:** L (US-58 S merged into US-57) · **Scenarios:** US-57 Pending reminders are listed; Pending count is visible; Reminders appear on the next open; Opening a reminder takes the user to log it; A reminder type is silenced · US-58 No weigh-in for 2 days in a row; Weighed yesterday; No circumference for 14 days; Logging clears the reminder; A Health Connect weight clears the reminder

**Description:** (1) **Backend:** migration with the enum `notification_kind` (`weight`, `circumference`, `weekly_check_in`; all three values now, so T-136 needs no enum change) and `notification_settings` (`user_id`, `kind`, `enabled`; unique per user and kind; a missing row means enabled), owner RLS, grants to `authenticated`, cascade on user delete (ADR-29); pgTAP (RLS isolation, uniqueness, default-enabled semantics documented). (2) **Domain:** `Reminder` model (kind, due date, payload such as days without a weigh-in) and a pure `ComputeReminders` use case: weight reminder when today − last weigh-in day ≥ 2 calendar days (device time zone, `log_date` semantics; any `body_measurements` row with a weight counts, manual or Health Connect); circumference reminder when today − last circumference day ≥ 14 (assumption A2 in the plan for "never measured"); silenced kinds are skipped. Nothing is stored per reminder (US-57 note). (3) **Data:** `NotificationSettingsRepository` (read all kinds, upsert one) on supabase-kt; last weigh-in and last circumference dates from the existing weight repositories (no new RPC unless the planner needs a light one). (4) **Refresh:** one app-level reminder state (in `AppViewModel` or a shared holder) recomputed on app open, on each return to the foreground (process lifecycle `ON_START`) and after any weight, circumference or Health Connect sync write, so logging clears the reminder without reopening the app. (5) **UI — templates and patterns:** "Centro de avisos" is a non-tab destination on `AppScreen` (back arrow, R-05; title "Centro de avisos"); a "Pendientes" `SectionHeader` with each reminder as an `AppListRow` (leading icon, headline "No te has pesado en 2 días" / "No te has medido en 2 semanas", supporting date via `AppDateFormat`, chevron; tap opens the entry, R-17); `EmptyState` with no action when nothing is pending; a settings card of `AppSwitchRow`s ("Recordatorio de peso", "Recordatorio de medidas"; the check-in switch comes with T-136); loading skeleton, `ErrorState` "Reintentar", offline per R-30 (switches disabled with the standard hint). **Entry point:** chosen by ux-reviewer in the plan pass within the 3 tabs (epic "Navigation"); it must be visible on whichever tab the app opens on (restored tab, ADR-40) and show the pending count; a new designsystem `BadgedIconButton` (`AppIconButton` + count badge, content description with the count, ≥ 48 dp, catalog entry, `05` §6 row) if the entry is an icon. **Tap targets:** weight reminder → the existing today's-weight entry of Peso (pencil, ADR-48); circumference reminder → the existing `CircumferenceSheet`. Switch semantics (applied on toggle vs. a "Guardar") are settled by ux-reviewer against R-10/R-12 and written to `05` §8 if it is a new convention. Spanish copy beyond the Gherkin literals by ux-reviewer per the glossary (§10).

**Sweep:** none expected (no existing reminder or badge pattern); if ux-reviewer finds an ad-hoc count badge, it is retired here (R-39).

**Acceptance criteria:**
- [x] Last weight 2 days ago → "No te has pesado en 2 días" listed with its date; last weight yesterday → no weight reminder; last circumference 15 days ago → "No te has medido en 2 semanas".
- [x] A weight reminder and a circumference reminder pending → the entry point shows 2; none pending → no count; the content description reads the count.
- [x] A condition met while the app was closed or in the background is listed on the next open / return to the foreground, with no background job.
- [x] Tapping the weight reminder opens the weight entry; saving a weight returns and the reminder and count are gone; same for the circumference reminder and the circumference entry.
- [x] A Health Connect sync ("Sincronizar") that stores today's weight clears the weight reminder.
- [x] Turning off "Recordatorio de peso" removes the weight reminder and lowers the count; turning it back on restores it; the setting persists across reinstall/sign-in (server-side).
- [x] pgTAP: RLS enabled and forced, user A cannot read or write user B's settings, one row per user and kind.
- [x] Empty, loading, error and offline states per R-30/R-31; open screen and scroll survive background and process death (ADR-40).

**Host tests:** `ComputeRemindersTest` (0/1/2/5 days without weight, 13/14/15 days and never measured for circumferences, time-zone day boundary, silenced kinds, Health Connect weight counts); `NotificationSettingsRepository` mapping test; reminder refresh test (recompute on foreground and after a weight save); `NotificationCenterViewModelTest` (list, toggle, offline, error); `NotificationCenterScreen` Robolectric tests (rows with dates, empty state, switches, tap navigates to the weight entry / circumference sheet); entry-point count test (0, 2) and `BadgedIconButton` test; restoration test; pgTAP `notification_settings`.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green on `main`, data intact (before T-136 starts; no checkpoint after T-135).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 20 files (size note: one small table; if the planner counts more than ~25 files, the core and UI run as two developer slices, not two tasks).
**Dependencies:** E9 (`v1.0.0`); breakdown agreed by the user
**Files likely touched:** `supabase/migrations/<ts>_notification_settings.sql`, `supabase/tests/notification_settings_test.sql`, `shared/…/domain/model/Reminder.kt`, `shared/…/domain/usecase/ComputeReminders.kt`, `shared/…/domain/port/NotificationSettingsRepository.kt`, `shared/…/data/repository/SupabaseNotificationSettingsRepository.kt`, `shared/…/di/`, `composeApp/…/app/{AppViewModel,AppRoutes,AppNavHost,AppScaffold}.kt`, new `composeApp/…/feature/notifications/{NotificationCenterScreen,NotificationCenterViewModel,NotificationCenterUiState}.kt`, `composeApp/…/designsystem/BadgedIconButton.kt`, `feature/weight/{WeightViewModel,CircumferencesViewModel,HealthSyncViewModel}.kt` (refresh after writes), catalog, `composeResources/values/strings.xml`, `planning/03-data-model.md`, `planning/05-design-system.md` §6, tests
**Estimated scope:** L

---

## T-136: Weekly check-in with the weekly summary (US-59) · L

**Story:** US-59 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Check-in appears on the chosen day; Changing the check-in day; Answering the check-in; Weekly summary; Missed check-in · plus US-57 Pending count / A reminder type is silenced for the check-in kind

**Resolved product rules (user, 2026-10-28; E10 Q1–Q3 in `tasks/plan.md`):**
- **Window (Q1 B):** the check-in is listed from the chosen weekday (default Sunday) until it is answered or the next check-in day arrives; it is stored for the ISO week (`week_start` Monday) that contains the chosen day; only one check-in is ever listed.
- **Summary (Q2):** training = total volume in kg (§4.2) plus workouts and sets, this week vs last week with the difference (`training_summary`, §4.11); records = the §4.8 records first set by a session of this week; weight trend = this week's average daily weight (§3.1) minus last week's, "Sin datos suficientes" when either week has no weigh-in.
- **Summary week (user, 2026-10-28):** a check-in's summary always covers the Monday–Sunday week (ending Sunday 23:59, device time zone) that contains its check-in day, compared with the week before; opened on Monday or later, it shows the closed figures of that week, never the new week's.
- **Reopening (Q3 B):** an answered check-in stays reachable from "Centro de avisos" (a row that is not a reminder and not counted) and is editable (upsert of the same week) until its window closes on the next check-in day. The summary is recomputed on each open, so an edit on Monday sees the closed numbers.

**Description:** (1) **Backend:** one migration: `weekly_check_ins` (`user_id`, `week_start` date = Monday, `energy` smallint 1-5 check, `notes` text nullable with a length cap, unique per user and week; owner RLS, grants, cascade); `profiles.check_in_weekday` smallint 1-7 default 7 (Sunday) with a check and the column-level UPDATE grant (the Preferencias section that holds it saves only its own field, as the hub's other sections); a save path for the check-in (upsert per user and week, or an RPC `save_weekly_check_in` if the planner needs validation, `22023` on bad input); a `weekly_records(p_from date, p_to date)` RPC returning the §4.8 records first set by sessions inside the range (the existing `personal_records` is per exercise), respecting "Incluir calentamiento" (§4.6); pgTAP for all of it. (2) **Domain:** `CheckInWindow` (is the check-in pending today for the current week, given the weekday and the stored answer, per the window rule above) feeding `ComputeReminders` as the `weekly_check_in` kind (silenceable through `notification_settings`, T-135); `WeeklySummary` use case: training figures this week vs last week from `training_summary` (§4.11), the week's records from `weekly_records`, and the weight trend of the week from the daily values (§3.1), as in the summary rule above. (3) **UI — templates and patterns:** the check-in reminder row in "Centro de avisos" (same `AppListRow` as T-135, headline by ux-reviewer, e.g. "Check-in semanal") opens the check-in screen, an `AppEditorScreen` (title by ux-reviewer per R-01, e.g. "Check-in semanal"): summary first as `MetricCard`s (volume in kg, workouts and sets this vs last week with the difference, records of the week as a short list, weight trend or "Sin datos suficientes"), then the form: energy 1-5 as a `SegmentedSelector` (R-33, 5 short options), notes as a multi-line `AppTextField` (R-24); pinned "Guardar" (R-09, R-10), Confirm haptic and close on success (R-13), "¿Descartar los cambios?" when dirty (R-14), offline hint (R-30); the summary loads independently (skeleton, `ErrorState` "Reintentar", R-31) and never blocks answering; a slot below the summary for the coach's commentary in E13 (US-71), empty in E10. "Día del check-in" in Preferencias: a `NavigationRow`/value in the section ux-reviewer picks (hub, `05` §6.2), choosing the weekday with an `AppPickerSheet` (applies on tap, R-12) inside that section's `AppEditorScreen`; weekday names in Spanish ("lunes" … "domingo"). The check-in switch joins the center's settings card ("Check-in semanal", wording by ux-reviewer). Once answered, the center shows a non-counted row for this week's check-in that opens the same screen prefilled (Q3 B); ux-reviewer places it (e.g. its own `SectionHeader`) so it is never mistaken for a pending reminder.

**Acceptance criteria:**
- [x] Default day Sunday: on Sunday the check-in is listed in "Centro de avisos" and counted at the entry point; unanswered, it stays listed on Monday…Saturday until the next Sunday; before the first Sunday it is not listed.
- [x] Setting "Día del check-in" to Friday in Preferencias lists the check-in from Friday until it is answered or the next Friday arrives.
- [x] Energy 4 and notes "semana dura en el trabajo" + "Guardar" → stored for the check-in's week (`week_start` = the Monday of the chosen day's week); the reminder and its count disappear; the screen closes with one Confirm.
- [x] After answering, the center shows this week's check-in as a non-counted row; opening it shows the saved energy and notes, and saving a change updates the same week; after the next check-in day it opens the new week's check-in instead.
- [x] The check-in shows volume in kg, workouts and sets this week vs last week with the difference, the records first set this week and the weight trend (this week's average − last week's), matching "Entrenamiento" and Peso for the same data; a week with no weigh-in gives "Sin datos suficientes"; a week without workouts shows zeros.
- [x] The summary covers the Monday–Sunday week containing the check-in day vs the week before: a Sunday check-in opened on the next Monday (or Saturday) shows the closed figures of the week that just ended, not the new week's; it is recomputed on each open, so a session or weight added late on that Sunday appears when reopened on Monday.
- [x] Last week unanswered → once the next check-in day arrives only the current check-in is listed (never two).
- [x] Turning off the check-in switch hides it; turning it on restores it.
- [x] pgTAP: `weekly_check_ins` RLS and uniqueness, energy range, `check_in_weekday` range and default, `weekly_records` owner-scoped with and without warm-ups.
- [x] The open check-in draft (energy, notes) and the visible screen survive background and process death (ADR-40); offline: "Guardar" disabled with the standard hint.

**Host tests:** `CheckInWindowTest` (each weekday, answered/unanswered, window spanning the ISO week boundary, missed check-in, change of day mid-window, editable-until rule, time-zone boundary); `ComputeRemindersTest` additions (check-in kind, silenced); `WeeklySummaryTest` (volume difference, records in range, weight trend with 0/1/many weigh-ins; range = Monday–Sunday of the check-in day's week vs the previous week whatever the open date: opened on the check-in day, on the next Monday and later in the window; Sunday 23:59 vs Monday 00:00 boundary in the device time zone; recomputed on each open); repository mapping tests; `CheckInViewModelTest` (save, validation, offline, summary error independent of the form, reopening on Monday loads the saved answer and the closed figures of its week); `CheckInScreen` Robolectric tests (summary cards, selector, notes, save closes, discard dialog, restoration); Preferencias "Día del check-in" test (picker applies, section saves only the weekday); center test with the check-in row and switch; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before Checkpoint E10 is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 22 files (size note: the backend is one migration with one table, one column and one RPC; if the planner counts more than ~25 files, it runs backend + core first and UI second as slices of the same PR).
**Dependencies:** T-135 merged and `deploy-supabase` green
**Files likely touched:** `supabase/migrations/<ts>_weekly_check_ins.sql`, `supabase/tests/weekly_check_ins_test.sql`, `shared/…/domain/model/{WeeklyCheckIn,WeeklySummary}.kt`, `shared/…/domain/usecase/{CheckInWindow,WeeklySummary,ComputeReminders}.kt`, `shared/…/domain/port/WeeklyCheckInRepository.kt`, `shared/…/data/repository/{SupabaseWeeklyCheckInRepository,…Profile…}.kt`, `shared/…/di/`, new `composeApp/…/feature/checkin/{CheckInScreen,CheckInViewModel,CheckInUiState}.kt`, `feature/notifications/NotificationCenterScreen.kt`, `feature/profile/{PreferencesSectionScreen,PreferencesViewModel,…}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/03-data-model.md`, `planning/04-business-rules.md` (check-in window and summary rules), tests
**Estimated scope:** L

---

## T-138: Move the reminder settings from "Centro de avisos" to Preferencias · S

**Origin:** Checkpoint E10 feedback (user, 2026-10-28): "move avisos preferences from avisos center to users preferences". Convention written to `05` §8 "Decided".
**Description:** The three switches ("Recordatorio de peso", "Recordatorio de medidas", "Check-in semanal") leave the center's settings card; the center only lists reminders and the answered check-in. Preferencias gets one hub section "Avisos" (replaces T-136's "Check-in semanal" section) holding the three switches and "Día del check-in", with one "Guardar" (explicit-save rule, offline hint R-30). Sweep every screen with a setting inside a feature screen (R-39). Same data (`notification_settings`, `profiles.check_in_weekday`), no migration; update US-57 Gherkin wording, `05` §6.2 and the host tests (center, Preferencias, restoration).
**Decision taken (PM, recorded):** one "Avisos" section instead of switches spread over sections; the center keeps no settings link.
**Verification:** [x] lint + `testLocalDebugUnitTest` green (CI, #313); [ ] re-test at Checkpoint E10: silence/restore each type from Preferencias, center has no switches.
**Dependencies:** Checkpoint E10 open · **Estimated scope:** S

---

### Checkpoint E10 — Reminders and your weekly check-in 👤 (15 items + leave and come back) *(agreed with the user, 2026-10-28: one checkpoint for the whole epic)*
Journey: the reminders find you when you open the app, take you to log the missing data and go away once it is logged; on your chosen day the check-in appears, you review your week in figures, rate your energy and write a note. Why here: right after T-136, the last implementation task, so it is also the final regression of E10; its approval releases the closure T-137. The user chose one checkpoint over two of 7 items.
- [x] T-135 and T-136 merged, CI green, `deploy-supabase` applied both migrations (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-28)*:
  1. Entry point (T-135): on the tab the app opens on, the "Centro de avisos" entry shows how many reminders are pending; with none, it shows no number.
  2. Weight reminder (US-58): if your last weigh-in is 2 or more days old (or delete your last weights in "Historial de peso" so it is), open the app: "No te has pesado en N días" is listed with its date; with a weight from yesterday there is no weight reminder.
  3. Circumference reminder (US-58): with no circumference in the last 14 days, "No te has medido en 2 semanas" is listed; after logging one, it is not.
  4. Tap to log (US-57): tap the weight reminder → the weight entry opens; save a weight → back in the center the reminder is gone and the count dropped; do the same with the circumference reminder.
  5. Health Connect (US-58): with the weight reminder listed, weigh yourself on a Health Connect scale (or add today's weight in Health Connect), then Peso → "Sincronizar": the reminder disappears.
  6. Silence a type (US-57): turn off "Recordatorio de peso" in Preferencias → "Avisos" (Guardar): the weight reminder and its count disappear; close and reopen the app: still off; turn it back on: the reminder returns.
  7. Next open and states (T-135): leave the app in the background overnight without weighing in and open it in the morning: the reminder is listed without doing anything; turn on airplane mode: in Preferencias → Avisos "Guardar" is disabled with "Sin conexión: no se puede …"; with nothing pending the center shows its empty state.
  8. Check-in day (T-136): Preferencias → Avisos → "Día del check-in" shows "domingo"; set it to today's weekday → "Centro de avisos" lists the check-in and the count goes up; leave it unanswered and open the app the next day: it is still listed (Q1 B).
  9. Weekly summary: open the check-in: volume in kg, workouts and sets this week vs last week, the records you set this week and the weight trend (this week's average − last week's, or "Sin datos suficientes"); compare them with "Entrenamiento" and Peso.
  10. Answer: energy 4 out of 5, notes "semana dura en el trabajo", "Guardar": it closes with one vibration, the reminder and its count are gone and stay gone after reopening the app.
  11. Reopen and edit (Q3 B): in "Centro de avisos" this week's answered check-in is still there, not counted; open it: energy and notes as saved; change the note and "Guardar": the change is kept.
  12. Editing safety and silencing: change the energy and go back: "¿Descartar los cambios?" ("Seguir editando" keeps the draft, "Descartar" leaves); turn off the check-in switch in Preferencias → "Avisos" (Guardar): it disappears; turn it on: it returns.
  13. Check-in states: in airplane mode "Guardar" is disabled with "Sin conexión: no se puede …"; if the summary fails to load, its "Reintentar" works and you can still answer; a week with no workouts shows zeros.
  14. Final regression of E10: with a missing weigh-in, an old circumference and the check-in due, the entry shows 3 and each row opens its entry; log a workout with a record and a weight, reopen the check-in: the summary includes them; Diario, Peso and Entreno work as before.
  15. Licencias (bug fix bundled in T-138): Perfil → Acerca de → "Licencias de código abierto" opens the licences list without crashing; open one entry and go back.
  - Leave the app and come back on "Centro de avisos" scrolled down and with the check-in half answered (energy chosen, a note typed): the same tab, screen, scroll and draft are restored.
  - Out of scope: the missed-check-in rollover over a real week (covered by host tests); the coach's commentary on the check-in (E13, US-71); the training streak (deferred); system notifications (never, ADR-73); the automatic daily Health Connect sync (E11).

---

## T-137: E10 closure → `v1.x` · S

**Story:** E10 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E10's approval)

**Description:** Closes E10 once Checkpoint E10 is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) to `1.1.0` (versioning rule, `06` §6), tag, retrospective, archive of the E10 log lines (`06` §6). No 👤 checkpoint of its own.

**Acceptance criteria:**
- [x] T-135, T-136 (and any fix tasks) merged with green CI; `deploy-supabase` green after the last migration.
- [x] ux-reviewer ran in plan and diff mode on both UI tasks; `check-ux-conventions.sh` passes.
- [x] `planning/03-data-model.md` and `04-business-rules.md` describe `notification_settings`, `weekly_check_ins`, `profiles.check_in_weekday`, `weekly_records` and the reminder and check-in rules.
- [x] Checkpoint E10 approved; every reported bug fixed.
- [x] Version agreed and bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [x] `git tag` lists the E10 tag.

**Dependencies:** T-136 and Checkpoint E10 approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E10.md`
**Estimated scope:** S
