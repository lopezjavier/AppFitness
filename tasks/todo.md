# Tasks — Phase 2

Plan of the current epic: [tasks/plan.md](plan.md) (created by the epic-planner) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-172 (E13-A feedback fixes; T-170 is the E13 closure). Numbering continues from here.

**Next:** T-171, then T-172 (Checkpoint E13-A feedback, 2026-10-29; fixes first), then re-present Checkpoint E13-A. T-159 merged in #371, `deploy-supabase` green. T-159 review nits (non-blocking): `ProgressionDto` maps a null `weight_kg` (bodyweight sets) to 0.0, so the line could read "0 kg × N" (omit the weight or the suggestion); `progression_suggestions` takes only the first range if an exercise repeats in a session with different ranges (add a comment); `05` should say when to use `HelpTriggerButton` instead of `HelpButton`; set dates in `ProgressionHelpSheet` use `AppDateFormat.weekdayDayMonth` ("sáb, 24 oct"); dumbbell increment uses `dumbbellHandleKg`, machines/other `roundingKg`; the flaky `SupabaseTrainingPreferencesRepositoryTest.get_rowWithoutCalculatorColumnsUsesTheDefaults` failed once in CI (passed on re-run; fix the sign-in setup if it repeats). T-158 review nits (non-blocking): `TrainerValueRow` puts the Provisional chip on its own line (task said the supporting line); `TrainerViewModel` `Load` resets Content to Loading and drops `confirming` (make it a no-op while Content is shown); clear the `activity_draft` key on every successful confirm, not only from the sheet; `TrainerContent` has no `FormContentMaxWidth` centering on tablets; `DerivedValuesCard.kt` is at the ~200-line limit; the tab icon is the legacy Material `insights` glyph (swap for Symbols Outlined if wanted); card scroll restoration is untested; `GlossaryStringsTest` no longer forbids "Confirmar" (T-162 handles "Aceptar"). T-157 review nits (non-blocking): `coach_weight_trend` gives a 0 uncertainty on a perfect-fit series so any later change re-asks (add a floor, e.g. 0.05 kg/week); `coach_e1rm` copies the Epley expression still inlined in `workout_sessions`, `personal_records`, `volume_by_metric` (point the older callers at it). Carry-over from T-155 (non-blocking): duration-based records show "kg" on weight×seconds (e.g. "Mejor serie 2560 kg"). T-154 review nits (non-blocking): the picker footer with the keyboard open has no host test (Robolectric cannot simulate the IME): manual check at the checkpoint. T-150 review nits (non-blocking): mapping rows `box-jump` (squat), `wall-sit`/`squat-hold` (squat), `handstand-hold` (vertical_push), `dead-hang` (vertical_pull) are debatable; the "picker open survives recreation" has no Robolectric test (manual check at the checkpoint). **T-153 review nits (non-blocking):** `applyRoutineNotes` does a second non-atomic `routineRepository.save` after `update_routine_from_session` and matches by `exerciseId` (repeated exercise gets the note on each occurrence); `LiveSet.previous` and the `showPrevious`/`onUsePrevious` path of `SetRow` are dead after dropping the Anterior column (remove; check the previous-values preference/help); `FormSetRow` KDoc still says "delete button"; SQL comment on empty-string notes becoming `other`. **Carry-overs:** chosen sound **"campana suave"** (T-148 wires it); T-148 no longer scans notes (ADR-81); (the padding screenshot was deleted at approval). T-152 review nits (non-blocking): KDoc of `DiscomfortSheet` should mention the required level picker; the migration-mapping pgTAP asserts use a scratch table that copies the CASE; narrow the `@Suppress("MagicNumber")` in `DiscomfortLevel`. T-147 carry-overs: `ActivityFlowTest.kt:86` uses `CoroutineScope(SupervisorJob())` like the flaky `HealthSyncRestorationTest` fixed in #342; no retry of unread discomforts on resume. T-146 carry-overs (non-blocking, for the checkpoint or T-148): rest ending while the user is on another tab posts no notification and no in-app alert (pre-existing); permission dialog interrupted by Activity recreation can show the explanation without a real denial; `FLAG_NO_CREATE` on cancel; own notification icon.

---

## Known flaky tests (the user does not accept flakiness)

Rule: every CI failure that passes on re-run is logged here with date, run URL and test. A second occurrence of the same test turns into a `fix(T-###)` task to remove the cause (or, only if the cause is outside our code, the test is marked and documented as flaky here).

| Test | Date | Run | Count | Status |
|---|---|---|---|---|
| `SupabaseTrainingPreferencesRepositoryTest.get_rowWithoutCalculatorColumnsUsesTheDefaults` (`AppException` at line 95, no cause in the log; passes locally and on re-run) | 2026-10-29 (PR #371) | https://github.com/lopezjavier/AppFitness/actions/runs/40959762823 | 1 | watch: next failure → fix task |


## 👤 Checkpoints

E10 (agreed with the user, 2026-10-28): one **Checkpoint E10** after T-136 (whole epic, also the final regression; its approval releases T-137). Details under the E10 section.

E11 (agreed with the user, 2026-10-28): a 👤 spike report after T-139 (device result, not a functionality checkpoint; gates T-141) and **one Checkpoint E11** after T-142 (watch data + daily automatic sync, also the final regression; its approval releases T-143). Details under the E11 section.

E13 (agreed with the user, 2026-10-29: three checkpoints): **Checkpoint E13-A** after T-159 (Trainer tab, derived values, progression in the live session; no paid call; 7 items), **E13-B** after T-165 (nutrition plan and outcomes, target weight gone, AI usage, block preview and "Guardar bloque", adherence, deload; 9 items), **E13-C** after T-169 (weekly report, history and deletion, chat, final E13 regression; 9 items; releases T-170; if T-168 keeps the chat disabled, presented after T-168 without the chat items). Non-checkpoint 👤 steps: Anthropic key before T-160; evaluation cases and paid-run approvals in T-168. Details under the E13 section.

E12 (user decision, 2026-10-28: two checkpoints): **Checkpoint E12-A** after T-147 (rest alert with the screen off, choosing the sound, discomfort log; 7 items), **Checkpoint E12-B** after T-150 (chosen sound, notes, Mi entrenamiento, movement pattern, final regression; 7 items; its approval releases T-151). Details under the E12 section.

## Notes for later epics

- **E11:** the `READ_HEALTH_DATA_HISTORY` pre-check is replaced by the spike T-139 (user decision, 2026-10-28; see the E11 section).
- **E12:** consumed by the E12 breakdown: Android 17 verification → T-146 (plan step and Checkpoint E12-A item 1); sound pick → Checkpoint E12-A item 5, wired in T-148.
- **E13:** consumed by the E13 breakdown: source, data-policy and price verification done 2026-10-29 (`07` §3-§4, §10-§11); 👤 Anthropic account, spending limit and `ANTHROPIC_API_KEY` → the 👤 step before T-160.

---

# E13 · Performance assistant → `v1.x` (proposed `v1.4.0`)

Stories: `planning/epics/E13-performance-assistant.md` (US-68…US-77 + the evaluation). Plan: [tasks/plan.md](plan.md). Decisions: ADR-76 (coach rules), ADR-77 (provider, cost cap; NFR-02), ADR-78 (target weight retired, `activity_level` starting factor), ADR-79 (Trainer tab), ADR-81 (discomfort level, no message), ADR-82 (note kind), ADR-25 (calorie floor), ADR-29 (cascade), ADR-40 (restoration), ADR-73 (no background job). Rules: `planning/07-coach-design.md` §2-§9 (every figure), §11 (prices, 150 s wall clock), `04` §1, §3, §4.1, §4.9, §4.12, §6.6, `05` §8-§10.
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
- [ ] **Notification bell missing on the Trainer root.** Every tab root shows the bell (`LocalNotificationsEntry`); Trainer does not. Fix and sweep every tab root and its top bar.
- [ ] **"Nivel de actividad" sheet has "Guardar" on a single choice.** R-12 already says single-choice pickers apply on tap, no "Listo"/"Guardar". The sheet must apply (and confirm the values, as today) on tapping a card, with no button; sweep every sheet with one choice (a finding repeating is a candidate for a `check-ux-conventions.sh` guard).
- [ ] **"Tendencia de peso" row:** "−0,3 kg/sem (±2,4)" is unreadable. ux-reviewer defines the rendering (plain sentence such as direction and rate, uncertainty explained or shown as a range, no raw "±"); sweep the other derived rows.
- [ ] **Little diary data** (8 of 28 days): decided with the user, no change of the thresholds in `07`; the user keeps logging and reports the diary-dependent items (trend, "Diario incompleto") in about two weeks, outside this checkpoint's gate unless they say otherwise.
- [ ] Spanish copy by ux-reviewer; host tests updated; `05` glossary/rules updated if a new rule is born.

**Flow:** core + UI · **Dependencies:** T-159 merged.

---

## T-172: Live session redesign: information density (US-70 and E12 additions) · L

**Source:** user feedback at Checkpoint E13-A, 2026-10-29. Screenshot: `design/reported/E13-A-live-session-density.png` (delete once the user confirms the fix).
The live session degraded across E12-E13: the exercise card now stacks the discomfort row with its "Ya no me molesta" button, the note row ("Otro · Pausa larga"), "Objetivo RPE" with `?`, the suggestion with `?`, the set table and "Añadir serie"; it looks scattered and "Ya no me molesta" is not integrated.
- [ ] **ux-reviewer first** (plan mode, before the planner): think how to render all the per-exercise information in one coherent structure (what is always visible, what is collapsed or moved to the header ⋮ or a sheet, order, spacing, one visual grammar for status rows), reviewing the whole live session, not only the card.
- [ ] "Ya no me molesta" integrated into the discomfort row (not a second segmented bar).
- [ ] **Suggestion without its own `?`** (user asked "do we really need it?"): recommended default is to drop the separate `?` and let the suggestion line open the reason sheet (or fold the reason in the new structure); ux-reviewer decides within the redesign and records it.
- [ ] **"Objetivo RPE" without its `?`** in the live session (user, 2026-10-29): the routine builder already explains it, so the live card repeats no help button for it; ux-reviewer checks that no other live-session row repeats a help the builder already gives.
- [ ] Sweep: every element above kept functionally (E12 discomfort and notes, RPE target, suggestion), host tests updated, restoration kept.
- [ ] The new structure becomes a rule/template in `05` §8-§9 in the same PR (a retired ad-hoc pattern is retired everywhere).

**Flow:** UX plan + core + UI · **Dependencies:** T-171 merged (or in parallel branches only if the user asks).

---

### Checkpoint E13-A — The Trainer tab knows you, and the gym tells you what to lift next 👤 (7 items + leave and come back) *(agreed with the user, 2026-10-29: three checkpoints)*
Journey: open the new Trainer tab, check what the coach estimated about you and confirm or fix it; then train and see, for each exercise, the weight and reps to aim for and why, also when an exercise bothers you. No paid AI is involved yet. Why here: right after T-159, the last model-free task, before the `coach` function and the plans build on the derived values.
- [ ] T-157, T-158 and T-159 merged, CI green, `deploy-supabase` applied their migrations (data intact), every reported bug fixed.
- [ ] 👤 Install the APK and try:
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
- [ ] 👤 Create the Anthropic account, set its monthly spending limit (backstop of US-76; ADR-77) and enter the key as a Supabase secret in your terminal: `npx supabase secrets set ANTHROPIC_API_KEY=…` (never in the repo or the chat). The PM only verifies the name exists (`npx supabase secrets list`, via a subagent).
- [ ] `planning/08-environment-setup.md` lists `ANTHROPIC_API_KEY` (T-160 adds it).
- [ ] **Key expiry:** the console key expires 2 years after creation (set by the user, 2026-10-29; exact date in the console, about 2028-10-29). Rotate before it expires: new key, `npx supabase secrets set ANTHROPIC_API_KEY=…` again, revoke the old one. Set a yearly calendar reminder for the user. Revoke at once if the key is ever pasted anywhere public.

---

## T-160: `coach` Edge Function, AI usage and the monthly budget (US-76) · L

**Story:** US-76 · **Flow:** backend + small core/UI · **Size:** L · **Scenarios:** Usage is visible; User budget reached (refusal path; the report fallback is wired in T-166); Deterministic features keep working (already covered by T-159, re-asserted); Global budget

**Description:** (1) **Migration:** `coach_usage` (`user_id` cascade, `month` date (first day), `input_tokens`, `output_tokens`, `cost_eur` numeric, unique `(user_id, month)`), owner select only for the client; writes only by the function; a `security definer` aggregate (or the function's secret key, planner picks and records it in `02`) that returns only the global monthly total, never other users' rows; pgTAP. (2) **Edge Function `coach`** (Deno/TS, `verify_jwt = true`, RLS through the user's token, `ANTHROPIC_API_KEY` server-side only, ADR-17/77): request router by `kind` (`plan_nutrition`, `plan_block`, `report`, `chat`; T-160 ships the skeleton and a `ping`-style smoke kind only if needed), Anthropic client in `_shared/` with streaming (SSE to the app), a call timeout below the 150 s wall clock, JSON-schema structured output and a validator hook (retry once, then error; architecture), price table (Opus 5.5 $4/$20, Sonnet 5.5 $2/$10 per million, cache read/write factors, `07` §11; dollars counted as euros), usage recorded per call from the API's returned usage, **budget check before every call**: refuse when this user's month would pass 2 € or all users' would pass 5 € (`coach_limit_reached` error); when the Anthropic API rejects a call for lack of prepaid credit (balance at zero, auto-reload off), the function returns a clean `coach_no_credit` error and the app shows the same limit message ("sin saldo", copy by ux-reviewer), with no retry; never logs prompts, summaries or outputs; `deno test` with a fake Anthropic client (no paid call in CI). (3) **Core/UI:** `CoachUsage` model and repository; the usage view "this month's AI usage and the monthly limit" (e.g. "0,42 € de 2,00 €" with `AppLinearProgress`) in the place decided by **E13 Q7** (resolved 2026-10-29: a read-only "Uso de IA" entry from the Trainer root's ⋮ `AppActionSheet`, R-02, opening an `AppScreen`); the shared limit message (copy by ux-reviewer: the monthly limit was reached until next month; R-37) used by every later model feature. (4) **Docs:** `02` (function, streaming, timeout, budget), `03` §7 (function row, table), `08` (`ANTHROPIC_API_KEY`), `01` NFR-02 unchanged.

**Acceptance criteria:**
- [ ] Usage screen shows this month's usage and the 2 € limit; zero usage shows 0,00 €.
- [ ] A user at 2 € gets `coach_limit_reached` for any model call; the app shows the limit message; deterministic features keep working.
- [ ] All users at 5 € → any user's model call is refused with the same message.
- [ ] Prepaid credit at zero (provider rejects the call) → `coach_no_credit` error and the app shows the "sin saldo" message; no retry; no usage recorded for the refused call.
- [ ] Each call stores input/output tokens and cost from the API usage and the price table; dollars counted as euros.
- [ ] A call that would exceed the wall clock is cut by the timeout and returns a clean error.
- [ ] pgTAP: RLS, cross-user isolation, client cannot write usage, global total exposes no row, cascade on account deletion.
- [ ] One real smoke call, approved by the user beforehand, succeeds after deployment and its cost appears in the usage view.

**Host tests:** `deno test` (budget boundaries for user and global, price math incl. cache factors, usage recording, timeout, retry-once on invalid JSON, streaming framing, JWT required); pgTAP `coach_usage.test.sql`; Kotlin repository mapping; usage ViewModel tests; Robolectric tests (usage screen, limit message, offline).

**Verification:**
- [ ] `deno test --allow-all supabase/functions`, pgTAP, lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green on `main` (migration + function); smoke call approved by the user.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** Checkpoint E13-A approved; 👤 Anthropic key step done; E13 Q7 resolved (2026-10-29: "Uso de IA" from the Trainer root's ⋮)
**Files likely touched:** `supabase/migrations/<ts>_coach_usage.sql`, `supabase/tests/{coach_usage,delete_account_cascade}.test.sql`, `supabase/functions/coach/{index,router,budget,prices}.ts` (+ tests), `supabase/functions/_shared/anthropic.ts`, `supabase/config.toml`, `.github/workflows/deploy-supabase.yml` (if functions are listed), `shared/…` (usage model, repository, DTO), `feature/trainer/{CoachUsageScreen,CoachUsageViewModel}.kt`, strings, `planning/02-architecture.md`, `planning/03-data-model.md`, `planning/08-environment-setup.md`, tests
**Estimated scope:** L

---

## T-161: Plan nutrition and outcomes: generation, validation and acceptance (US-69 backend) · L

**Story:** US-69 (+ US-74 "Every decision is stored" for plans and calorie proposals) · **Flow:** backend · **Size:** L · **Scenarios:** Weight loss plan; Calorie floor; Muscle gain plan; Outcomes are optional; Accepting writes the nutrition goal (RPC); Incomplete diary (no calorie change); Changing the calories within a block (trigger + proposal)

**Description:** (1) **Migration:** `coach_plans` (epic §Tables: `block_start`, `block_weeks` = 4, `intention`, `nutrition` jsonb, `outcomes` jsonb, `method` jsonb, `status` enum proposed | accepted | superseded | discarded, `data_version`; `nutrition` carries an `edited_by_user` flag), `coach_recommendations` (epic §Tables; `kind` enum starts with `plan`, `calorie_change`; `input_summary` = the `coach_summary` used), owner RLS, cascade, pgTAP. (2) **Deterministic nutrition** (TypeScript in the function or SQL; `07` §4): target calories from the confirmed expenditure and the intention's rate (loss 0.5-1 %/week, default 0.5 %; gain 0.25-0.5 %; recomposition/maintenance per `07`), calorie floor 1200/1500 kcal (ADR-25) with "the rate is slower because of the floor", protein ≥ 1.6 g/kg (≤ 2.2 plateau; deficit high-lean-mass upper end), fat ≥ 20 % (20-35 %), carbs the rest; 7 700 kcal/kg only without history; **protein distribution guidance** (`07` §4, user-approved 2026-10-29): about 0.4 g/kg per meal over at least 4 meals, stored in `nutrition` as guidance text and figures, never as a meal plan (no foods, no meal times; `01` §5 excludes meal plans). (3) **Model call `plan_nutrition`** (Opus 5.5, ADR-77; streamed; under the 150 s limit): the model selects outcomes within validated options (weight, body fat, circumferences, e1RM per main lift; none is allowed) and writes each outcome's reason and deadline (normally the end of the block, `07` §6); the server validator rejects any plan that breaks a rule (rates, floor, protein, fat, outcome without reason/deadline, a figure not derivable from the summary), retries once, then errors (architecture). Refused when derived values are unconfirmed? → the plan is generated but marked `requires_confirmation` (US-68 "Unconfirmed values block the plan": the **accept** is blocked, not the proposal). (4) **RPCs:** `accept_coach_plan(p_plan_id)` (rejects while any derived value is unconfirmed; supersedes the previous accepted plan; writes `nutrition_goals` so Diario's daily calorie and macro targets are the plan's, within the existing `nutrition_goals` columns, planner maps strategy/adjustment/percentages; stores the decision in `coach_recommendations`), `discard_coach_plan`, `decide_recommendation(p_id, p_status accepted|edited|discarded, p_payload)`; a Preferencias save that changes the nutrition goal marks the accepted plan's nutrition `edited_by_user` (E13 Q8). (5) **In-block calorie change (deterministic):** when the plan's target rate is outside the trend's confidence interval for two consecutive weeks and the intake estimate is reliable, the coach creates a `calorie_change` recommendation of at most 10 % of the calories (`07` §4); applied only through `decide_recommendation` accepted; evaluated when the coach runs (E13 Q6, resolved: on each Trainer open and after saving the weekly check-in, at most once a day; never in the background, ADR-73). (6) Docs: `03`, `04` §7, `07` if a rate default needs a label.

**Acceptance criteria:**
- [ ] "Perder grasa", expenditure 2600 kcal → calories below expenditure, rate 0.5-1 %/week, protein ≥ 1.6 g/kg, fat ≥ 20 %.
- [ ] Woman whose deficit gives 1100 kcal → 1200 kcal and the plan states the slower rate.
- [ ] "Ganar musculatura" → surplus rate 0.25-0.5 %/week.
- [ ] Every plan carries the protein distribution guidance (≈ 0.4 g/kg per meal, ≥ 4 meals) computed from the body weight; no foods or meal times (US-69 "Protein distribution guidance", E13 Q12).
- [ ] "Ganar fuerza" → may set e1RM outcomes, may set no weight outcome; each outcome has reason and deadline.
- [ ] Accepting writes `nutrition_goals`; accepting with an unconfirmed derived value is rejected.
- [ ] Diary logged on 60 % of days → no calorie change is proposed.
- [ ] Rate outside the interval two weeks running → one `calorie_change` ≤ 10 %, applied only when accepted.
- [ ] Invalid model output → one retry, then a clean error; nothing stored as accepted.
- [ ] Every accept/edit/discard is stored with its `input_summary`.
- [ ] pgTAP: RLS, isolation, cascade; RPC paths; `deno test` for the validator and nutrition math.

**Host tests:** `deno test` (nutrition math per scenario, floor by sex, validator rejections, retry-once, streaming with a fake client, budget refusal path, calorie-change trigger boundaries: one week vs two, 10 % cap, unreliable intake); pgTAP `coach_plans.test.sql` (accept/discard/supersede, unconfirmed blocks accept, nutrition goal written, decisions stored, Preferencias edit flag).

**Verification:**
- [ ] `deno test`, pgTAP green; after the merge `deploy-supabase` green on `main`.
- [ ] E13 Q4 B (resolved): no evaluation gate here; one real plan call approved by the user as a smoke test.

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4. About 12 files.
**Dependencies:** T-160 merged and deployed; E13 Q6, Q8, Q12 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_plans.sql`, `supabase/tests/{coach_plans,delete_account_cascade}.test.sql`, `supabase/functions/coach/{planNutrition,nutritionRules,validators,schemas}.ts` (+ tests), `supabase/functions/_shared/`, `planning/03-data-model.md`, `planning/04-business-rules.md`
**Estimated scope:** L

---

## T-162: Plan proposal screen, nutrition acceptance and the target weight retired (US-69 UI; US-68 gating; ADR-78) · L

**Story:** US-69 UI (+ US-68 "Unconfirmed values block the plan") · **Flow:** core + UI + small migration · **Size:** L · **Scenarios:** Weight loss plan (display); Calorie floor (display); Outcomes are optional (display); Accepting writes the nutrition goal; The user never enters target numbers; Changing the calories within a block (card); Unconfirmed values block the plan

**Description:** (1) **Core:** plan and recommendation models, repository (request a plan with streamed progress, accept, discard, decide recommendation), `PlanProposalViewModel`, Trainer root state (no plan / proposed / accepted / pending recommendation). (2) **UI — templates and patterns:** Trainer root gains the plan entry (e.g. "Crear plan" when none; the accepted plan's summary card otherwise; copy by ux-reviewer); the request shows streamed progress (R-31 "Preparando tu plan…"-style state; build the shared progress state here) then the **plan proposal** `AppScreen`: the derived-values card of T-158 on top (provisional values marked; the accept button disabled with the reason until "Confirmar", US-68), nutrition section (kcal, P/C/F grams with the fixed macro colours, weekly rate, the floor note when it applies, and the protein distribution guidance as an `InlineNote`-style line, e.g. "≈ 30 g de proteína por comida, en 4 comidas o más"; copy by ux-reviewer; guidance only, no meal plan), outcomes list (each with reason and deadline via `HelpButton`/Info sheet, R-46) and a pinned footer with the accept and discard actions (labels per E13 Q5); accept → Confirm haptic, Diario shows the new targets; discarding leaves Diario untouched. **`CoachProposalCard`** (new designsystem component: title, payload, reason, data used, "Aceptar" / "Descartar") on the Trainer root for the in-block calorie change; reused by T-165 and T-169. Limit reached → the US-76 message instead of the proposal. (3) **Target weight retired (ADR-78, US-69 technical note):** remove every UI that asks for a target weight (E5 goal card and sheets `GoalSheets.kt`/`GoalViewModel.kt`, onboarding if it asks, Preferencias if it asks), and "Peso" shows the accepted plan's weight outcome when there is one (otherwise nothing); migration drops `profiles.target_weight_kg` (and its column grant) after the UI no longer reads it; `04` §1.4 goal progress and §3.5 rewritten for the plan outcome; pgTAP `profiles.test.sql` updated. Sweep: no screen asks for a target weight, body fat or circumference target. (4) Glossary §10: plan, outcome ("Objetivo del plan"?), accept/discard labels (E13 Q5).

**Acceptance criteria:**
- [ ] Creating a plan shows progress, then the proposal with calories, macros, rate, the protein distribution guidance and outcomes (reason and deadline each).
- [ ] With an unconfirmed value the accept action is disabled with its reason; after "Confirmar" it is enabled.
- [ ] Accepting → Diario's calorie and macro targets are the plan's; a later manual edit in Preferencias still works and marks the plan's nutrition as edited.
- [ ] No screen asks for a target weight, body fat or circumference; "Peso" shows the plan's weight outcome when one exists.
- [ ] A calorie-change proposal shows as a card; "Aceptar" applies it, "Descartar" leaves the calories.
- [ ] Limit reached → the limit message, no proposal; offline → the standard hint.
- [ ] The proposal screen, its scroll and the in-flight request survive background and process death (ADR-40; a finished stream is reloaded from the stored plan).

**Host tests:** repository/DTO tests (plan, recommendation, streamed events); ViewModel tests (progress, success, invalid → error with "Reintentar", limit, accept gated by confirmation, accept, discard, calorie card decisions); Robolectric tests (proposal sections, floor note, outcome help, disabled accept with reason, `CoachProposalCard`, "Peso" outcome, no target-weight inputs anywhere); restoration tests; pgTAP for the column drop.

**Verification:**
- [ ] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main`, data intact (before T-163 starts).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 18 files (the target-weight removal is mostly deletions; if the planner counts more than ~25, run core and UI as developer slices of the same PR).
**Dependencies:** T-161 merged and deployed (no checkpoint after T-162: its items are in Checkpoint E13-B); E13 Q5, Q8, Q9 resolved (2026-10-29)
**Files likely touched:** `shared/…` (plan models, repository, DTO), `feature/trainer/{TrainerScreen,TrainerViewModel,PlanProposalScreen,PlanProposalViewModel}.kt`, `designsystem/{CoachProposalCard,StreamProgressState}.kt`, `feature/weight/{WeightScreen,WeightTrendViewModel}.kt`, removal of `feature/weight/{GoalSheets,GoalViewModel}.kt` and their tests, onboarding/Preferencias target-weight bits, `shared/…/{ProfileRepository,SupabaseProfileRepository,NutritionGoalsDto}.kt`, `supabase/migrations/<ts>_drop_target_weight.sql`, `supabase/tests/profiles.test.sql`, strings, `planning/03`, `04` §1.4/§3.5, `05` §9/§10, tests
**Estimated scope:** L

---

## T-163: Block creator: training block generation and validation (US-72 backend) · L

**Story:** US-72 · **Flow:** backend · **Size:** L · **Scenarios:** Preview before saving (data); Frequency and volume are shown; One training day; Frequency chosen by the coach; Only available equipment; Saving creates routines (RPC); Nothing is saved without confirmation

**Description:** (1) **Migration:** `coach_plan_sessions` (`plan_id` cascade, `week` 1-4, `routine_id` nullable until saved, `session_id` nullable, `deload` boolean default false; plus the proposed routine content in the plan's `method` jsonb until saved), owner RLS via the plan, pgTAP. (2) **Model call `plan_block`** (Opus 5.5, streamed; **150 s limit**: the planner measures a realistic call against the fake and one approved real call; if it risks the limit, split per two weeks or per week, each a separate validated call): the model picks exercises **only from a candidate list built server-side** from the catalogue filtered by place and equipment (`07` §3; `movement_pattern` for substitutions), never free text names; the output is the week → routines → exercises → planned sets (rep range, sets, target RPE, rest) with a reason per exercise. (3) **Validators (`07` §3):** each muscle group at least twice a week when days ≥ 2; 1 day → one full-body routine; weekly sets per muscle group around the 10-set starting point (experience-adjusted, beginner lowest); intensity ranges per intention; 1-3 RIR on main work; only available equipment; session length respected when set; days unset → the coach chooses and states the frequency; **rules added to `07` §3 (user-approved 2026-10-29):** rest between sets written into each routine exercise's `rest_seconds` (2-3 min on main multi-joint lifts, 1-2 min on isolation; "main multi-joint" per E13 Q3), **exercise order** with the priority multi-joint lifts first in each session, **periodization**: "Ganar fuerza" varies load and reps within the week across the block (undulating, per-session rep ranges stored per planned exercise), every other intention keeps one rep range per exercise, **range of motion**: full range as a technique note on the exercise reason only (never a computed rule), **cardio**: the block never reduces cardio or steps (no output removes or shortens cardio exercises the user does, and no text advises fewer steps); retry once, then error. Weekly sets per muscle group are computed deterministically (`04` §4.11 weighting) and stored for display. (4) **RPC `save_coach_block(p_plan_id)`**: creates one routine folder for the block and the routines through the existing `save_routine` procedure (no parallel insert path), each routine exercise with its rep range (T-159's column, E13 Q2) and rest, links `coach_plan_sessions.routine_id`, and accepts the plan's training part (one plan acceptance per E13 Q9); nothing is created before this call. (5) Docs: `03`, `04` §7.

**Acceptance criteria:**
- [ ] "Ganar musculatura", "Gimnasio" → a four-week block with routines and a reason per exercise.
- [ ] 3 days → every muscle group at least twice a week; weekly sets per muscle group returned.
- [ ] 1 day → one full-body routine per week.
- [ ] No days or length set → the plan states the frequency it chose.
- [ ] Equipment "Mancuernas" + "Peso corporal" → every exercise uses only that equipment (validator rejects otherwise).
- [ ] Rests: main multi-joint lifts 120-180 s, isolation 60-120 s, written into the saved routines' `rest_seconds`.
- [ ] Order: in every session the priority multi-joint lifts come before the isolation work.
- [ ] "Ganar fuerza" → rep ranges vary across the sessions of a week; any other intention → one rep range per exercise in the block.
- [ ] No output reduces cardio or steps; full range of motion appears only as a technique note.
- [ ] `save_coach_block` creates one folder and the routines via `save_routine`, linked to the plan; without it no routine exists.
- [ ] The block call finishes within the wall clock (measured, recorded in the PR body) or is split.

**Host tests:** `deno test` (candidate filtering, every validator rule with passing and failing fixtures incl. rest bounds, order, undulating vs single range by intention, cardio not reduced, frequency/volume math, one-day case, retry-once, split-call orchestration, timeout); pgTAP `coach_plan_sessions.test.sql` (RLS via plan, `save_coach_block` creates folder + routines through `save_routine`, idempotency, isolation, cascade).

**Verification:**
- [ ] `deno test`, pgTAP green; after the merge `deploy-supabase` green on `main`.

**Agent runs:** planner, backend-developer, pr-creator, pr-reviewer = 4. About 12 files.
**Dependencies:** T-162 merged and deployed (no checkpoint in between); E13 Q3, Q9 and Q11 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_plan_sessions.sql`, `supabase/tests/{coach_plan_sessions,delete_account_cascade}.test.sql`, `supabase/functions/coach/{planBlock,blockRules,candidates,schemas,validators}.ts` (+ tests), `planning/03-data-model.md`, `planning/04-business-rules.md`
**Estimated scope:** L

---

## T-164: Block preview and "Guardar bloque" (US-72 UI) · L

**Story:** US-72 · **Flow:** core + UI · **Size:** L · **Scenarios:** Preview before saving; Frequency and volume are shown; One training day; Frequency chosen by the coach; Only available equipment (display); Saving creates routines; Nothing is saved without confirmation

**Description:** (1) **Core:** block models, repository (request block with streamed progress, save block), `BlockPreviewViewModel`. (2) **UI — templates and patterns:** from the plan (proposal or accepted nutrition plan) the user requests the block; streamed progress (shared state from T-162); the **block preview** is an `AppScreen` with weeks as `AppTabRow` ("Semana 1"…"Semana 4", R-33 sibling views), each week a list of routine cards (`AppCard`) with `ExerciseRow`s in session order (tapping a name opens the exercise details, R-18) showing sets × rep range and the rest (shared duration formatter, R-47), the exercise's reason in an Info sheet (`HelpButton`, R-46) that also carries the full-range technique note; with "Ganar fuerza" the different rep ranges of each session are visible; a "Series por semana" section with the weekly sets per muscle group; the chosen frequency stated when the user set no days; pinned "Guardar bloque" (Gherkin literal, R-09); leaving the preview creates nothing (no discard dialog: it is not an editor, R-14; the proposal stays reopenable from Trainer, E13 Q10); after saving: Confirm haptic, the routines appear in Entreno in the block's folder, and the Trainer root shows the block. The new-block review at the end of a block or after preferences change (`07` §1) is the same entry ("Nuevo bloque"; copy by ux-reviewer). Limit reached → the US-76 message.

**Acceptance criteria:**
- [ ] The preview shows each week with its routines and the exercises' reasons.
- [ ] The weekly sets per muscle group are shown; with 3 days each group appears at least twice a week; with 1 day one full-body routine.
- [ ] Without days set, the preview states the chosen frequency.
- [ ] Each exercise shows its rest and rep range; multi-joint lifts come first; with "Ganar fuerza" the rep ranges differ between the sessions of a week; the saved routines keep those rests and that order.
- [ ] With "Mancuernas" + "Peso corporal" only those exercises appear.
- [ ] "Guardar bloque" → one folder with the block's routines in Entreno, linked to the plan; leaving the preview creates no routine.
- [ ] Preview tab, scroll and help sheet survive background and process death (ADR-40).

**Host tests:** repository/DTO tests; ViewModel tests (progress, success, error/retry, limit, save, leave without saving); Robolectric tests (week tabs, routine cards, reason help, sets-per-group section, chosen-frequency line, "Guardar bloque", folder created via fake); restoration tests.

**Verification:**
- [ ] lint + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 13 files.
**Dependencies:** T-163 merged and deployed
**Files likely touched:** `shared/…` (block models, repository, DTO), `feature/trainer/{BlockPreviewScreen,BlockPreviewViewModel,BlockWeekTab,ExerciseReasonSheet}.kt`, `feature/trainer/TrainerScreen.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

## T-165: Plan weeks: adherence and the deload suggestion (US-73, US-75) · L

**Story:** US-73 + US-75 (merged: both deterministic on the plan weeks; each alone is M) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** US-73 Completing a planned routine; Weekly adherence; Missed sessions do not block the next week · US-75 No e1RM improvement for three weeks; The deload; Accepting or discarding

**Description:** (1) **Adherence (backend):** finishing a workout of a routine listed in the current plan week links it (`coach_plan_sessions.session_id`) in the same transaction as the finish path (`finish_workout_session` / `save_completed_workout`; planner picks the hook); week = block week by `block_start`; adherence % = completed / planned per week; a missed session is never moved; `coach_outcomes` gains `adherence_pct` per week if US-74 needs it (or T-167 computes it). (2) **Deload (deterministic, `07` §3/§9):** trigger = no **main lift** (definition per E13 Q3 (resolved: an exercise whose `movement_pattern` is squat, hinge, lunge, horizontal or vertical push or pull, done in at least one session in each of the three weeks; a deload is suggested only inside an accepted block)) improved its e1RM (`04` §4.1) over the last three weeks of sessions; it creates a `deload` recommendation for the next plan week; accepted → that week's `coach_plan_sessions.deload = true`; in the live session a deload week's exercises show the suggestion "same weights, 40 % fewer working sets" (rounded to the nearest whole set, at least 1 per exercise, E13 Q3) through the T-159 suggestion line, never rewriting the routine (architecture note); discarded → nothing changes. Evaluated when the coach runs (E13 Q6). (3) **UI — templates and patterns:** the Trainer root's block section shows each week with its sessions (done ✓ / pending), the week's adherence ("75 %") and the current week; the deload proposal is a `CoachProposalCard` (T-162) with "Aceptar" / "Descartar"; a deload week is labelled in the week list and in the live session (copy by ux-reviewer, glossary "Descarga"?). `04` §7 (adherence, deload).

**Acceptance criteria:**
- [ ] Week 1 lists "Push"; completing "Push" that week links the workout to that plan session.
- [ ] 4 planned, 3 completed → week 1 adherence 75 %.
- [ ] A missed week-1 session: week 2 shows as planned; nothing is moved.
- [ ] No main lift improved its e1RM in three weeks → a deload card for next week; none when one improved.
- [ ] Accepted → next week marked deload; its exercises suggest the same weights and 40 % fewer working sets; routines unchanged. Discarded → week unchanged.
- [ ] The Trainer root with the week list and the card survives background and process death (ADR-40).

**Host tests:** pgTAP (linking on finish within the week, other weeks/routines not linked, adherence math, isolation); deload trigger tests (three weeks boundary, main-lift filter per Q3, e1RM ties); set-reduction rounding cases; ViewModel tests (weeks, adherence, card accept/discard, live-session deload suggestion); Robolectric tests (week list, adherence text, card, deload label, live suggestion); restoration test.

**Verification:**
- [ ] pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` (before Checkpoint E13-B).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** T-164 merged; E13 Q3 resolved (2026-10-29)
**Files likely touched:** `supabase/migrations/<ts>_coach_adherence_deload.sql`, `supabase/tests/{coach_adherence,coach_deload}.test.sql`, `shared/…` (plan week, deload models; repository), `feature/trainer/{TrainerScreen,TrainerViewModel,PlanWeeksSection}.kt`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen}.kt`, strings, `planning/03`, `04` §7, `05` §10, tests
**Estimated scope:** L

---

### Checkpoint E13-B — Your plan from the coach: nutrition, the four-week block, adherence and deload 👤 (9 items + leave and come back) *(agreed with the user, 2026-10-29: three checkpoints)*
Journey: ask the coach for a plan, read the calories, macros, protein guidance and outcomes with their reasons, confirm your values and accept it so Diario follows it (the old target weight is gone); see what it cost; then ask for the training block, browse it, save it, train from it and watch adherence; accept or discard a deload. Why here: right after T-165, before the weekly report (T-166) and the history (T-167) read the plan and its weeks. T-160…T-164 merge with no checkpoint after them.
- [ ] T-160…T-165 merged, CI green, `deploy-supabase` applied their migrations and the `coach` function (data intact), every reported bug fixed.
- [ ] 👤 Install the APK and try (each plan or block request is a paid call of about 0,1-0,4 €):
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

## T-166: Weekly coach report (US-71; US-76 report fallback) · L

**Story:** US-71 (+ US-76 "User budget reached" for the report) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Report after the weekly check-in; Report cites real data; Invented figure is rejected; Short sleep is mentioned, not acted on; Report on demand, once a day; US-76 User budget reached

**Description:** (1) **Migration:** `coach_reports` (epic §Tables: `week_start` ISO Monday, `generated_on` date, `figures` jsonb = the stored weekly summary: volume, records, adherence, weight trend, sleep 7-day average; `text` nullable), unique `(user_id, week_start, generated_on)` or the planner's equivalent for "once a day", owner RLS, cascade, pgTAP. (2) **Model call `report`** (Sonnet 5.5, streamed, < 30 s p95 target): input = the stored figures only; **figure validator** (US-71 technical note): every number in the text, after normalising decimals ("," / ".") and units, must be in `figures` (rounding to the shown precision allowed); any other number → text discarded, the report shows only tables and figures; sleep 7-day average < 6 h → the text mentions sleep and nothing changes load or calories (no recommendation created). Budget reached → figures and tables without text plus the limit message. (3) **UI — templates and patterns:** in "Check-in semanal" the coach commentary appears **below the weekly summary** after saving the check-in (E10 US-59 hook; text block in an `AppCard`, streamed progress state from T-162); a Trainer root `NavigationRow` "Informe semanal" (Gherkin literal; R-03/R-04: the screen title equals the label) opens the report `AppScreen` for the current week (figures tables reuse the check-in summary components, R-39); tapping it again the same day shows the stored report, no new call.

**Acceptance criteria:**
- [ ] After completing the check-in, the coach commentary shows below the weekly summary.
- [ ] Every figure in the text exists in the stored figures (validator tests with real-shaped summaries).
- [ ] A text with an invented figure is discarded; only tables and figures show.
- [ ] Sleep average < 6 h → the text mentions sleep; no load or calorie change is created.
- [ ] "Informe semanal" shows this week's report and does not generate again the same day.
- [ ] At 2 € the report shows figures and tables without text plus the limit message.
- [ ] Report screen and check-in with the commentary survive background and process death (ADR-40).

**Host tests:** `deno test` (validator: decimals, units, rounding, percentages, dates not counted as figures per the planner's rule, invented figure; once-a-day reuse; sleep mention; budget fallback); pgTAP `coach_reports.test.sql`; ViewModel tests (check-in commentary, on-demand, same-day reuse, limit, error); Robolectric tests (commentary below the summary, report screen, figures-only state, limit message); restoration tests.

**Verification:**
- [ ] `deno test`, pgTAP, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main`.

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 15 files.
**Dependencies:** Checkpoint E13-B approved
**Files likely touched:** `supabase/migrations/<ts>_coach_reports.sql`, `supabase/tests/{coach_reports,delete_account_cascade}.test.sql`, `supabase/functions/coach/{report,figureValidator}.ts` (+ tests), `shared/…` (report model, repository, DTO), `feature/checkin/{CheckInScreen,CheckInViewModel}.kt`, `feature/trainer/{WeeklyReportScreen,WeeklyReportViewModel}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/03`, `04` §6.6/§7, tests
**Estimated scope:** L

---

## T-167: Recommendation history, outcomes and deleting the coach history (US-74) · M

**Story:** US-74 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** Every decision is stored (already written by T-161/T-163/T-165; asserted end to end here); Outcome after a block; Deleting the coach history; Deleting the account

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

**Description:** (1) **👤 Cases:** the user writes the cases, each with its correct answer: a plan for each intention, one training day, limited equipment, discomfort, missing data, rate inside and outside range, calorie floor, invented figure in a report (the planner provides a case template file; the user fills it; cases hold no personal data, public repo, or live gitignored under `design/reported/`-style local storage if they do; planner proposes, PM asks). (2) **Runner:** a script (`scripts/coach-eval.ts` or a `deno` task, never in CI) that runs each case through the deployed `coach` function's validators and model calls with a dedicated test account, records pass/fail per criterion and latency, and writes a results table (no prompt or output dumps in the repo beyond the verdicts). (3) **Criteria (epic):** 100 % of discomfort cases with no load increase; 100 % of report figures traceable; every plan meets the rules of `07` §3-§4: frequency (when days allow), volume, intensity per intention, rest between sets (2-3 min main multi-joint, 1-2 min isolation), exercise order (priority multi-joint first), periodization (undulating only for "Ganar fuerza", one range otherwise), no reduction of cardio or steps, protein (≥ 1.6 g/kg, distribution guidance ≈ 0.4 g/kg per meal over ≥ 4 meals), fat and the calorie floor; report and chat under 30 s at p95; the plan shows progress and finishes within 150 s. (4) **Gate:** a function that fails its criteria is disabled server-side (a `coach_feature_flags` row or a constant, planner picks) until fixed (fix tasks), and US-77 (T-169) starts only when every criterion is met. **Each run is a paid call: the PM asks the user before each run** and reports its cost.

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

**Story:** US-77 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Answer cites data; Proposals are cards, not changes

**Description:** (1) **Migration:** `coach_conversations` (and messages; owner RLS, cascade, included in `delete_coach_history`), pgTAP. (2) **Model call `chat`** (Sonnet 5.5, streamed, prompt caching of the summary per `07` §11; < 30 s p95): read-only tools over the user's data (sessions of an exercise, e1RM history, weekly sets, weight trend, adherence; each a parameterised SQL read through the user's token, capped rows); every answer cites the sessions/data it used (validated: cited ids exist in the tool results); a proposal in an answer goes through the same validators as US-69/US-72 and is stored as a pending `coach_recommendations` row; nothing is changed until accepted (`decide_recommendation`). Budget per US-76 (the 2 € cap stops chat first, `07` §11). (3) **UI — templates and patterns:** a "Chat" entry on the Trainer root (copy by ux-reviewer; a new chat screen pattern: `AppScreen` with a lazy message list, a pinned input row using `AppTextField` with a send icon, R-39, keyboard room as `AppEditorScreen`), streamed answer with progress, citations as tappable rows to the workout detail (R-17), proposals as `CoachProposalCard` (T-162) with "Aceptar" / "Descartar"; limit message (US-76); offline hint (R-30). Record the chat pattern in `05` §9.

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

### Checkpoint E13-C — Your weekly report, the coach's track record, the chat, and the final E13 regression 👤 (9 items + leave and come back) *(agreed with the user, 2026-10-29: three checkpoints)*
Journey: finish the weekly check-in and read the coach's commentary, ask for the weekly report, look back at everything the coach proposed and how it turned out and wipe it if you want, ask the coach about your training, then one pass over the whole coach. Why here: right after T-169, the last implementation task, so it is also the final regression of E13; its approval releases T-170. T-166, T-167 and T-168 merge with no checkpoint after them. **If T-168 keeps US-77 disabled**, this checkpoint is presented right after T-168 without items 7-8 (and T-169 waits for its fix tasks).
- [ ] T-166…T-169 merged (T-168 criteria met, results recorded), CI green, `deploy-supabase` applied their migrations and the function (data intact), every reported bug fixed.
- [ ] 👤 Install the APK and try:
  1. Check-in commentary: answer the weekly check-in: the coach's text appears below the weekly summary.
  2. Real figures: every number in the text (volume, records, adherence, weight trend, sleep) appears in the report's tables.
  3. On demand: Trainer → "Informe semanal" shows this week's report; open it again the same day: the same report, no new cost in "Uso de IA". If your watch shows under 6 h of sleep on average, the report mentions it and nothing in the plan changes.
  4. History: Trainer ⋮ → "Historial" lists the plan, the block and every proposal you accepted, edited or discarded, each with the data it used.
  5. Outcomes: open a plan: its outcomes are shown next to what was measured since (weight, e1RM, adherence so far).
  6. Delete: "Eliminar historial del entrenador" and confirm: the history is empty; your account, routines (also the block's), workouts, confirmed values and AI usage stay.
  7. Ask: in the Trainer chat ask "¿Por qué estoy estancado en press banca?": the answer cites the sessions it used; tap one: its workout opens.
  8. Proposals: a proposal in an answer is a card: "Descartar" changes nothing; "Aceptar" applies it; "Uso de IA" shows the chat calls.
  9. Final regression: a fresh block (plan + "Guardar bloque"), a workout from it with the progression suggestion and a discomfort; Diario follows the plan's targets, Peso shows the plan outcome, Actividad and "Centro de avisos" work as before.
  - Leave the app and come back on the report, once on a history detail, and once in the chat with a question half typed: the same tab, screen and draft are restored.
  - Out of scope: the invented-figure fallback and the limit-reached report (host tests); deleting the account (pgTAP; destructive); water and voice (Phase 3); medical advice, meal plans, video analysis (never); distribution to other users (written consent condition, `07` §8).

---

## T-170: E13 closure → `v1.x` · S

**Story:** E13 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E13-C's approval)

**Description:** Closes E13 once Checkpoint E13-C is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR; minor per epic, proposed `1.4.0`), tag, retrospective, archive of the E13 log lines; `09-open-questions.md` already holds E13 Q1-Q13 (resolved 2026-10-29): only add any question raised during the epic. No 👤 checkpoint of its own.

**Acceptance criteria:**
- [ ] T-157…T-169 (and any fix tasks) merged with green CI; `deploy-supabase` green after each migration and function change.
- [ ] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [ ] `02` (coach function, streaming, budget), `03` (coach tables, RPCs, function), `04` §7 (coach rules), `05` (Trainer tab, coach patterns, glossary) and `07` (evaluation results) are current.
- [ ] Checkpoints E13-A, E13-B and E13-C approved; every reported bug fixed.
- [ ] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [ ] `git tag` lists the E13 tag.

**Dependencies:** T-169 and Checkpoint E13-C approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E13.md`
**Estimated scope:** S

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
- [ ] Rest of 90 s running, phone locked: at `endsAt` the phone vibrates with the rest pattern, plays the current sound and shows "Descanso terminado" (verified by hand at Checkpoint E12-A; host test: posted at `endsAt` when not in the foreground).
- [ ] App in the background at `endsAt` → the same alert.
- [ ] App in the foreground at `endsAt` → only the in-app alert (vibration + sound); no notification is posted and the scheduled one is cancelled.
- [ ] "Omitir" in the app, "Finalizar entreno" or discarding the workout → nothing fires when the time would have ended; "+30s" moves the alert 30 s later; completing another set reschedules it.
- [ ] Notifications denied → nothing is posted; the explanation appears once and never again (also after reopening the app); the in-app alert still works in the foreground.
- [ ] The temporary chooser lists the three candidates, plays each on tap and the next alert uses the chosen one.
- [x] `planning/02-architecture.md` (technique, permissions) and `04-business-rules.md` §4.4 (alert outside the app, cancel rules) updated; ADR-75 consequences ticked.
- [ ] The live session with a running rest survives background and process death (ADR-40); a rest scheduled before process death still alerts.

**Host tests:** `LiveSessionViewModel` tests with a fake `RestAlertScheduler` (schedule on set completion with `endsAt`, "+30s" reschedules, new set restarts, "Omitir"/finish/discard cancel, foreground end cancels and plays the in-app alert, pause keeps it); notification tests in Robolectric (channel, text "Descanso terminado", posted only when not in the foreground; `ShadowAlarmManager`/`ShadowNotificationManager` or the service equivalent per the chosen technique); permission-explanation-once test (denied → shown once, flag persisted, never again); chooser screen test (rows, tap plays and selects); restoration test with a running rest.

**Verification:**
- [x] lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.
- [ ] Android 17 behaviour verified in the plan (sources recorded); the on-device check is Checkpoint E12-A items 1-2.

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
