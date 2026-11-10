# Tasks — Phase 3

Plan of the current epic: [tasks/plan.md](plan.md) (E18) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md). Phase 2 history (frozen, with the full per-task log T-134…T-238): [tasks/phase2/](phase2/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-270 (T-268, T-269, T-270 = Checkpoint E18 feedback fixes; T-259…T-267 reserved by the E18 breakdown; T-257…T-258 = ADR-104 hard energy check, Checkpoint E17 feedback; T-251…T-256 reserved by the E17 breakdown; T-246…T-249 by E16; T-239…T-244 by E15; T-250 = user-reported fix below)

**Done in the T-246 branch (user report, 2026-11-08; the user ordered it into the current branch, so it ships in T-246's PR as a separate `fix(T-250)` commit):** `fix(T-250)`: the `food-photo` dish prompt (`supabase/functions/food-photo/prompts.ts`) never says whether the per-100 g values are for the food as served; a 400 g cooked plate came back with raw-food kcal/100 g. Fix: the dish prompt states that values and `estimated_grams` describe the food as shown in the photo (cooked when cooked), with a handler/prompt test and a redeploy of `food-photo`. Backend only, no model-call change elsewhere.

**Next (2026-11-10, E18, T-270):** T-269 merged (PR #619) and the Checkpoint E18 re-test is **approved by the user except one bug**: opening the Trainer and tapping "Tu plan" / "Tu bloque" each take 2-3 s of loading. Diagnosis (code read, nothing timed): our code, not Supabase. `TrainerViewModel.fetch()` runs ~18-22 sequential round trips on every open, and the box screens (T-268) get a new `TrainerViewModel` that repeats the whole fetch. Next is **T-270** (fix). After T-270 re-present only the Trainer open and the two boxes; its approval releases T-267 (closure). Follow-ups: chat has no follow-up context (each question stands alone; user may want a task); week-summary box has no date range; old non-cardio `no_range_flag` rows lack `figures.routine_id`; `BlockReviewEntry` hand-rolled clickable card; `WeekSummaryScreen` internal `SummaryCard` name clash; duplicate "Tu plan" header.

## Open follow-ups carried into Phase 3

- T-238 (pr-reviewer, non-blocking): extract local vals for the ktlint-wrapped `kind == RestAlertKind.Cardio` in `RestAlertNotifier.post` and `minutes != null` in `SessionSetRow.cardioPlaceholder`.
- T-238: `LiveSessionViewModel.timerBarUi` uses `names.getValue(...)`, which can throw (use a fallback).
- T-238: missing ViewModel tests: deleting the active cardio set, starting a second cardio run, a refused session pause (`restoreCardio`), a target reached while a rest owns the bar.
- T-235 (pr-reviewer/ux-reviewer): comment in the migration's "different plan already accepted" branch that accept already superseded the previous plan; confirm with the user that low adherence still lets freed Mantener sets flow to prioritised groups; show the low-adherence/cut reasons on every row (optional); `FormContentMaxWidth` cap on the targets step and an explicit themed style on the "puedes cambiarlo" text; `05` §8 builder spec text, SegmentedSelector `disabledIndices` catalog entry, haptics and restoration host tests.
- T-234 (pr-reviewer suggestions): extract `CalorieChoice.toEvent()` in `BlockReviewScreen.kt`; add a `BlockReviewViewModel` test for the failed re-read after a decision; "No conseguido" uses `StatusTone.Caution` (no neutral tone exists; ask ux-reviewer whether R-41 wants a neutral one); the review subtitle (date range) is missing because `BlockReview` has no block start date.
- T-232: `coach_stalled_lifts` runs 2-3 times per summary call (a temp CTE would avoid it); add a pgTAP case for a deload week at W-1 in the "first found" comparison; `WeekSummaryViewModel.today()` uses UTC while `LoadReminders` uses the local-zone date (pass one consistent date); `BuilderSlotPickerSheet.kt:55` and `FolderBlockPickers.kt:93` use accent-sensitive `contains(ignoreCase = true)` (use `normalizeText` as `SearchExercises` does); a cancelled deload can be requested again, and the automatic path never re-applies one in the same block (document in `04` §9.10).
- Carried over from #522: `coach_week_gate` should return `{ok:false}` itself on an empty diary; align the -500 cap / +750 limit with `04` §8.5; document that `recent_change` is not scoped to `plan_id`; rename the `change` parameter of `CalorieProposalCard` to `proposal`; merge `acceptOffline`/`discardOffline` in `TrainerScreen.kt`.

---

## Known flaky tests (the user does not accept flakiness)

Rule: every CI failure that passes on re-run is logged here with date, run URL and test. A second occurrence of the same test turns into a `fix(T-###)` task to remove the cause (or, only if the cause is outside our code, the test is marked and documented as flaky here).

| Test | Date | Run | Count | Status |
|---|---|---|---|---|
| `SupabaseTrainingPreferencesRepositoryTest.get_rowWithoutCalculatorColumnsUsesTheDefaults` (`AppException` at line 95, no cause in the log; passes locally and on re-run) | 2026-10-29 (PR #371) | https://github.com/lopezjavier/AppFitness/actions/runs/40959762823 | 1 | watch: next failure → fix task |


## 👤 Checkpoints

E18 (epic-planner, 2026-11-09; one checkpoint only, user decision 2026-11-09): **Checkpoint E18** after T-266, the last implementation task ("Uso de IA" gone, past reports in "Informes", quick search and my foods at once, the compact Trainer, the exercise chat, plus the final E18 regression; 16 items grouped by functionality; its approval releases the closure T-267). The 👤 **Gemini terms check** (a decision, not a test) gates T-265. Details under the E18 section.

E17 (epic-planner, 2026-11-09; one checkpoint only, user decision 2026-11-09): **Checkpoint E17** after T-255, the last implementation task (the kcal/macro warning, "Alcohol (g)", the food audit, Open Food Facts copies and the conversion, plus the final E17 regression; 10 items; its approval releases the closure T-256). Details under the E17 section.

E16 (epic-planner, 2026-11-08; one checkpoint only, user decision 2026-11-08): **Checkpoint E16** after T-248, the last implementation task (the nutrition section of "Resumen semanal", "Descanso de dieta" and the final E16 regression; 7 items; its approval releases the closure T-249). As in E15 (Q5), only items the user can try right away are checkpoint items; the diagnosis branches that need two real weeks of data are in "Pending natural-trigger feedback (E16)" under the E16 section, outside the gate. Details under the E16 section.

E15 (epic-planner, 2026-11-08, after the user's Q1-Q5 answers; needs the user's OK): **Checkpoint E15-A** after T-240 ("Bloque nuevo" and switching goal; 6 items) and **Checkpoint E15-B** after T-241, the last implementation task ("Training around a discomfort", plus the final E15 regression; 6 items; its approval releases the closure T-244). **Only items the user can try right away are checkpoint items (user, 2026-11-08, E15 Q5):** paused weeks, welcome back, the block-end notice and the 14-day note fire only after real time passes, so they are in "Pending natural-trigger feedback (E15)" under the E15 section, not gates. Details under the E15 section. Phase 2 checkpoints are in tasks/phase2/todo.md.

## Notes for later epics

- **Out of scope (user, 2026-11-08, ADR-101):** "Recuperar" / returning after a break (regain phase) is out of scope for the app; the 14-day return rule stays as the only break rule.
- **R-59 sweep leftover (T-245): consumed → T-248** (E16 breakdown, 2026-11-08).
- **UI review epic (user, 2026-11-08):** "Lo que el entrenador sabe de ti" (`free_text_*`, `trainer_free_text_row`) and the "Para conocerte" help text say the coach reads those notes when preparing the plan and blocks, but the coach is deterministic (ADR-97…99; `plan_*` kinds have `deterministic: true`, `report` and `chat` are unavailable, and no coach code reads the free text), so the "Enviar al entrenador" switches control nothing. The user will raise it in the detailed UI review epic (remove, hide until a model feature exists, or fix the copy).
- **E13 backlog (user request, 2026-10-30): consumed.** Items 1 → T-179, 2 → T-181…T-184, 3 and 4 → T-205/T-206. Nothing from it is open.

## Reminders

- Delete `design/reported/live-session-cardio-row-layout.png` once the user confirms the cardio fix (T-238).

---

# E18 · Functional improvements → `v1.x` (proposed `v1.9.0`)

Epic: [`planning/epics/E18-functional-improvements.md`](../planning/epics/E18-functional-improvements.md) (stories US-128, US-129, US-130, US-120…US-122). Plan: [tasks/plan.md](plan.md).
Rules: ADR-103 (Anthropic removed, reports stored, the Gemini exercise chat), ADR-17 (keys server-side), ADR-59 (`food-photo`, Gemini), ADR-66 (no monthly report), ADR-98 (no model in the numbers), ADR-101, ADR-104; `04` §6.1 (reminders computed, never stored), §6.7 (weekly summary notice), §6.9 (block-end notice); `03` §3 `coach_block_reviews`, `coach_week_summary_seen`; `05` §8-§10 (R-02, R-04, R-06, R-07, R-26, R-30, R-31, R-33, R-52, R-56, R-57); `01` §4 (no medical advice, the only chat is US-130); ADR-27, ADR-40, ADR-80.
"Notes for later epics": no item targets E18 (the "Recuperar" note is a non-goal; the "Lo que el entrenador sabe de ti" note targets E19 UI review; T-259 must not change that copy). Nothing moved. Carried follow-ups folded in (the list above is left as is; the PM ticks them when the task merges): `CalorieChoice.toEvent()` extraction and the missing review subtitle of `BlockReviewScreen` (T-234) → T-262; renaming the `change` parameter of `CalorieProposalCard` and merging `acceptOffline`/`discardOffline` in `TrainerScreen.kt` (#522) → T-264; the T-255 leftover "`FoodSearchViewModel.emitTextSearch` sorts favourites first only from the favourites already loaded" → T-263 (it is US-122).

**Status (epic-planner, 2026-11-09; updated the same day with the user's feedback): PROPOSED, needs the user's OK on the breakdown PR; one checkpoint only, at the end (user decision 2026-11-09).** Q1 and Q2 are resolved by the user (2026-11-09, below and in `tasks/plan.md` "Decisions"). The only open user item is the 👤 **Gemini terms check** (free or paid tier, epic and ADR-103), which gates T-265.

**User decisions (2026-11-09):**
- **Q1 · Report history (T-261, T-262).** (a) No backfill: only the summaries produced from T-261 on are stored; older weeks are not rebuilt. (b) The block-end notice is a report notice: opening it from Centro de avisos clears it there and the review is kept in "Informes" (`04` §6.9 "opening never clears it" is amended by T-261). (c) A past report opened from "Informes" is read only: it shows the figures and the decision taken as text, with no buttons to decide again; the current report opened from its notice keeps its buttons. (d) A paused week ("Semana en pausa") is listed in "Informes" only if its summary was actually produced and shown to the user; a paused week nobody opened leaves no report.
- **Q2 · Exercise chat (T-265, T-266).** (a) No follow-up context: each question is answered on its own and sends only the exercise (name, muscles, instructions) and that question, as the Gherkin says. Multi-turn context is an idea for a later epic, not E18. (b) "Daily limit" means only Gemini's own quota: the Edge Function catches the quota or provider error and the app shows a short reason; there is no app-side cap or counter.

**Order and sizing (every task pays 6-8 hand-offs; no S task):**
1. **T-259** · **Size M** · backend: US-128 backend. US-128 is about 45 files (Edge Function, shared client, migration, pgTAP, docs, then the app's screen, models, repository and tests), so it is split backend then app. Goes first because it drops data in production (epic).
2. **T-260** · **Size M** · core + UI: US-128 in the app: "Uso de IA", the monthly limit and the model selector removed.
3. **T-261** · **Size M** · backend: US-129 backend: report snapshots and the list. Split from the UI because the story is about 20 files (migration, RPCs, pgTAP, then repository, list, read-only viewer of two report types, Centro de avisos entry, routes, tests).
4. **T-262** · **Size M** · core + UI: US-129 in the app: "Informes" and the past-report viewer. No checkpoint here (user decision: one checkpoint at the end).
5. **T-263** · **Size M** · core + UI: US-121 + US-122. US-122 alone is S (T-255 already orders the list; the story adds the immediate local block), and it is the same food-search screen and ViewModel as US-121's Diario path, so they are one task.
6. **T-264** · **Size M** · UI + core: US-120, the compact Trainer and "Detalles".
7. 👤 **Gemini terms check** (user decision, not a task, no size): free tier or paid tier with a low limit, for the chat and `food-photo`. Can be done any time before T-265 (for example while T-263 or T-264 runs), so T-265 does not wait.
8. **T-265** · **Size M** · backend: US-130 backend: the `exercise-chat` Edge Function. Split from the UI because together they are about 16 files and the backend (prompt, Gemini call, error mapping, data minimisation) is reviewed apart from the chat tab.
9. **T-266** · **Size M** · core + UI: US-130 in the app: the chat tab. Last implementation task. → **👤 Checkpoint E18** (the only one; final regression).
10. **T-267** · closure step (PM, no size class; it waits for the checkpoint approval, so it cannot merge into a task): E18 closure.

---

## T-259: Anthropic removed: Edge Functions, `coach_usage` dropped, decisions updated (US-128 backend) · M

**Story:** US-128 (backend and docs) · **Flow:** planner → backend-developer → pr-reviewer → pr-creator (§2.2, no UI) · **Size:** M
**Scenarios:** US-128 The key is gone (repository and Edge Functions); Usage data is dropped; Nothing I use changes (server side: every deterministic `coach` kind, `food-photo`); The decisions say so.
**Description:** **Edge Functions:** delete `_shared/anthropic.ts` (+ test), `coach/budget.ts`, `coach/prices.ts` (+ tests) and the streaming/model path of `coach/handler.ts`, `index.ts`, `context.ts`, `router.ts`, `debugLog.ts`; every remaining kind is deterministic (`plan_nutrition`, `plan_recompute`, `plan_diet_break`, `plan_goal_switch`, `plan_phase_change` and the rest the planner lists from `router.ts`) and keeps its exact response; kinds that needed a model (`report`, `chat`, any other) are removed. An old installed app still sends `model` in the body: the handler ignores the field (no `INVALID_INPUT`), so the Trainer keeps working until T-260's APK. Errors tied to the budget or the provider (`_shared/errors.ts`) go. `supabase/.env.example` loses `ANTHROPIC_API_KEY`. **Migration:** drop `coach_usage` (table, policies, functions or views that read it) and any other budget-only object the planner finds; no other table is touched; pgTAP: `coach_usage.test.sql` removed, `delete_account_cascade.test.sql` and the `coach_*` tests that mention it updated; a check that the other tables' row counts are unchanged (T-242 precedent). **Docs ("The decisions say so"):** ADR-77 and ADR-90 marked superseded by ADR-103 in their index rows (`phase2/00-decisions.md`) and the Phase 2 NFR-02 restored to "100 % free" (`phase2/01-scope.md`, as ADR-103 asks); `07` §8 "Data, privacy and cost" and §11 "Cost estimate" rows updated (no Anthropic, no caps; Gemini only for `food-photo` and the chat); `02` (providers, secrets), `03` (`coach_usage` gone), `08-environment-setup.md` (Anthropic account and key steps removed). The "Lo que el entrenador sabe de ti" copy is not changed (E19 note). **👤 step after the merge:** the user removes the secret (`npx supabase secrets unset ANTHROPIC_API_KEY`) and revokes the key in the Anthropic console; the PM only verifies that the name is gone from `npx supabase secrets list` (through a subagent).
**Acceptance criteria:**
- [x] No file in the repository references Anthropic, `ANTHROPIC_API_KEY`, prices or the monthly budget (outside frozen history: `planning/phase1/`, `planning/phase2/` text kept as history, `tasks/phase*/`, retrospectives); `rg -i anthropic supabase/` is empty.
- [x] Every deterministic `coach` kind returns the same response as before for the same input (Deno tests unchanged or adapted, none deleted except model/budget ones); a body with `model` is accepted and the field ignored.
- [x] `coach_usage` no longer exists after the migration; every other table keeps its rows (migration self-check).
- [x] ADR-77 and ADR-90 read "Superseded by ADR-103"; NFR-02 reads "100 % free"; `07` §8/§11 show no paid provider.
**Host tests:** Deno tests for `coach` (each deterministic kind, `model` ignored, removed kinds answer the existing unknown-kind error); pgTAP (`coach_usage` absent, `delete_account_cascade` without it, row counts unchanged).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` and `deno test --allow-all supabase/functions` green; after the merge `deploy-supabase` green on `main` with data intact and `coach` redeployed.
- [x] 👤 `ANTHROPIC_API_KEY` unset (PM verified the name is absent, 2026-11-09); key revocation in the Anthropic console is the user's remaining step.
**Agent runs:** 5. About 22 files (half deletions).
**Dependencies:** the breakdown approved by the user.
**Files likely touched:** `supabase/functions/_shared/{anthropic,anthropic.test,errors}.ts`, `supabase/functions/coach/{handler,index,context,router,debugLog,budget,prices}.ts` (+ tests), `supabase/migrations/<ts>_drop_coach_usage.sql`, `supabase/tests/{coach_usage,delete_account_cascade,coach_*}.test.sql`, `supabase/.env.example`, `planning/{02-architecture,03-data-model,07-coach-design,08-environment-setup}.md`, `planning/phase2/{00-decisions,01-scope}.md`
**Estimated scope:** M

---

## T-260: "Uso de IA", the monthly limit and the model selector removed from the app (US-128) · M

**Story:** US-128 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-128 The cost screen is gone; The key is gone (the app contains no Anthropic client); Nothing I use changes (app side); plus "leave the app and come back on the Trainer and Perfil".
**Description:** **Core:** delete `CoachUsage`, `CoachBudget`, `CoachModelChoice`, the `CoachModelPreference` port and its implementation, `CoachUsageDto`, `SupabaseCoachUsageRepository` and their DI bindings and tests; `CoachFailure` loses the budget and provider variants (keep the ones the deterministic kinds still return); the coach client stops sending `model`; `CoachClientTimeout` and any streaming-only code are kept only if a deterministic kind still uses them (planner checks; dead code goes). **UI — templates and patterns:** `CoachUsageScreen`/`ViewModel`/`UiState` and their route in `AppRoutes`/`AppNavHost` deleted; the Trainer header ⋮ action sheet (R-02, Actions archetype) loses "Uso de IA" (if the menu is left with no item, the ⋮ goes and the header keeps only the bell, R-04/G13); any Perfil or Preferencias row to it removed; strings of the screen, the limit and the model selector removed; a progress text that names the AI on a deterministic path ("Preparando tu plan…", `StreamProgressState`) is checked by ux-reviewer and kept only if still accurate. Glossary §10 rows for "Uso de IA", the limit and the model selector removed; `05` §6/§8 references updated. A restored back stack that pointed to the removed route is dropped by a versioned guard (`NAV_STATE_VERSION`; bump it whenever a route is removed) and the app lands on the start tab, Diario (ADR-40; decided in T-260, one-time, only for a stack saved by the old build).
**Acceptance criteria:**
- [x] Perfil and the Trainer show no "Uso de IA", no monthly limit and no model selector; no string, route or class for them remains.
- [x] The plan proposal, "Bloque nuevo", the block builder, the folder review and the weekly summary behave as before (existing host tests green, none deleted except usage/selector ones).
- [x] `rg -i "anthropic|coach_usage|CoachBudget|CoachModel" shared composeApp androidApp` is empty.
- [x] Leaving the app and coming back (and process death) on the Trainer and Perfil restores them; `check-ux-conventions.sh` passes.
**Host tests:** coach client tests (no `model` field); `TrainerFlowTest`/`TrainerRestorationTest` adapted (no ⋮ entry); deleted: `CoachUsageScreenTest`, `CoachUsageRestorationTest`, `CoachUsageModelSelectorTest`, `CoachBudgetTest`, `SupabaseCoachUsageRepositoryTest`; a restoration test from a saved stack with the removed route.
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change.
**Agent runs:** 7. About 20 files (mostly deletions).
**Dependencies:** T-259 merged and deployed.
**Files likely touched:** `shared/…/domain/model/{CoachUsage,CoachBudget,CoachModelChoice,CoachFailure}.kt`, `shared/…/domain/port/CoachModelPreference.kt`, `shared/…/data/remote/CoachUsageDto.kt`, `shared/…/data/repository/{SupabaseCoachUsageRepository,CoachClientTimeout}.kt` and the coach client, DI module, `feature/trainer/{CoachUsageScreen,CoachUsageUiState,CoachUsageViewModel,TrainerScreen,PlanProposalUiState}.kt`, `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/05-design-system.md`, tests
**Estimated scope:** M

---

## T-261: Reports are stored: weekly and block summary snapshots and their list (US-129 backend) · M

**Story:** US-129 (backend) · **Flow:** planner → backend-developer → pr-reviewer → pr-creator (§2.2, no UI) · **Size:** M
**Scenarios:** US-129 A report is kept; Opening past reports (server: list newest first, one report by id, exactly the stored figures); Reminders are not stored; Opening a report removes its notice (server side, both notices, Q1(b)).
**Description:** **User decisions Q1 (2026-11-09) apply.** A new owner-scoped table (e.g. `coach_reports`: `id`, `user_id` cascade, `kind` `week_summary | block_review`, `plan_id`, `week` (null for a block), `period_start`, `period_end`, `snapshot jsonb` = the exact JSON the screen rendered, `created_at`; unique `(user_id, kind, plan_id, week)`; RLS enabled and forced, owner select only, no client write). **Weekly summary:** stored when it is produced, i.e. the first time the app builds the summary of a finished week (`coach_week_summary` path; a security definer writer, idempotent, as `open_coach_block_review`); once stored, later reads of that week return the stored snapshot (same figures as the list). **No backfill (Q1(a)):** nothing is rebuilt for weeks before T-261 is deployed, and no week is built later just to fill a gap. **Paused weeks (Q1(d)):** a "Semana en pausa" summary is stored only when it was actually produced and shown to the user; one nobody opened leaves no report. **Block summary:** `coach_block_reviews` already freezes `inputs` when the review opens; the list includes those rows (view or one-time copy, planner's choice) with the decision taken. Reviews stored before T-261 are listed because they were frozen when shown, not rebuilt (not a backfill). RPCs: `list_coach_reports()` (id, kind, period, newest first) and `get_coach_report(id)` (snapshot), invoker, `authenticated` only, `anon` revoked. **Block-end notice (Q1(b)):** it is a report notice: a `seen` mark on the review (e.g. `coach_block_reviews.seen_at` + `mark_coach_block_review_seen`) clears the Centro de avisos item "Tu bloque ha terminado: revísalo" once it is opened, and the review stays in "Informes". The Trainer's "Revisión del bloque" card and the review footer are not changed by this (they still follow `04` §6.9 until the next block is saved). `04` §6.9 "opening never clears it" is amended. Reminders (weight, circumference, check-in, welcome back) get no row (`04` §6.1). **Docs:** `03` §3 (table, RPCs, `seen_at`), `04` §6.7/§6.9 (stored reports, the amended notice rule) and a new `04` §6 paragraph "Informes"; ADR-103 consequence line for the §6.9 change.
**Acceptance criteria:**
- [x] Building a finished week's summary stores one snapshot; reading it again returns the same snapshot even after the underlying diary or weights change; a second build stores nothing new; no week before the deploy is stored.
- [x] The list returns the caller's weekly and block reports newest first; a report of another user is neither listed nor readable.
- [x] No reminder kind is ever stored; a paused week is stored only once its summary was produced and shown.
- [x] Opening the block-end notice marks the review seen: the notice is no longer listed, the review is in the list, and the Trainer card still shows while the review is due.
**Host tests:** pgTAP `coach_reports.test.sql` (store on first build, idempotent, frozen after data changes, nothing stored for earlier weeks, paused week only when built, block reviews listed with their decision, order, RLS with two users, no reminder rows) and the block-end seen mark (notice gone, Trainer review due unchanged).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; after the merge `deploy-supabase` green on `main` with data intact.
**Agent runs:** 5. About 8 files.
**Dependencies:** T-260 merged (Q1 resolved by the user, 2026-11-09).
**Files likely touched:** `supabase/migrations/<ts>_coach_reports.sql`, `supabase/tests/{coach_reports,coach_summary,coach_block_review}.test.sql`, `planning/03-data-model.md`, `planning/04-business-rules.md` §6
**Estimated scope:** M

---

## T-262: "Informes": past reports reopened from Centro de avisos (US-129) · M

**Story:** US-129 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-129 Opening past reports; One after another; Reminders are not stored (no reminder in the list); Opening a report removes its notice; State survives leaving the app.
**Description:** **Core:** `ReportRepository` over T-261's RPCs, a report-list ViewModel and a past-report ViewModel that renders a stored snapshot through the same mappers as `WeekSummaryViewModel` and `BlockReviewViewModel` (one mapper per report type, no second copy of the rendering); opening a report notice marks it seen: the weekly summary per `04` §6.7, the block-end notice through T-261's seen mark (Q1(b)). **UI — templates and patterns:** "Informes" entry in Centro de avisos (`NotificationCenterRoot`): a `NavigationRow` labelled "Informes" (R-03/R-04: the screen title equals the label), placement by ux-reviewer (top or bottom of the notice list, always visible, also when no notice is active); the list is a pushed `AppScreen` (R-06) with rows "Resumen semanal" / "Revisión del bloque" and the period via `AppDateFormat` (R-44), newest first, lazy (R-23), `EmptyState` when none, skeleton, `ErrorState` with "Reintentar", `OfflineState` (R-30/R-31); a row opens the report on a pushed `AppScreen` that reuses the existing `WeekSummaryScreen` and `BlockReviewScreen` content composables in a read-only mode (Q1(c): no "Aplicar"/"Ahora no", "Aceptar"/"Seguir como estoy" or "Crear el siguiente bloque"; the decision taken shown as text); back returns to the list at its scroll position. The current report opened from its notice keeps today's screen and actions. Folded follow-ups (T-234): extract `CalorieChoice.toEvent()` in `BlockReviewScreen.kt`; add the review subtitle (the block's date range, now available from the report period). Glossary §10: "Informes" (the glossary lists "Informe" as a forbidden alternative for the weekly summary: ux-reviewer amends that row so "Informes" names the list only). Docs: `05` §8/§10.
**Acceptance criteria:**
- [x] "Informes" opens from Centro de avisos and lists my weekly and block reports newest first; no reminder appears in it.
- [x] A past report shows exactly the stored figures (same text as when it was first shown), read-only with the decision as text (Q1(c)); back returns to the list and another opens.
- [x] Opening a report notice (the weekly summary or the block-end notice, Q1(b)) removes it from the active list and the report stays in "Informes".
- [x] The list and an open report survive background and process death (ADR-40); offline shows the standard state (R-30); `check-ux-conventions.sh` passes.
**Host tests:** repository/DTO tests; ViewModel tests (order, empty, error and retry, offline, snapshot → same UI model as the live mapper for the same JSON, notice marked seen); Robolectric (entry row in Centro de avisos, list rows and copy, read-only report without actions, back to list, restoration with `StateRestorationTester`).
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change.
**Agent runs:** 7. About 14 files.
**Dependencies:** T-261 merged and deployed.
**Files likely touched:** `shared/…/domain/{model/Report,repository/ReportRepository}.kt`, `shared/…/data/{remote/ReportDto,repository/SupabaseReportRepository}.kt`, `feature/notifications/{NotificationCenterRoot,NotificationCenterUiState}.kt`, `feature/trainer/{ReportsScreen,ReportsViewModel,ReportsUiState,PastReportScreen,PastReportViewModel,WeekSummaryScreen,BlockReviewScreen,BlockReviewSections}.kt`, `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-263: Quick search in Diario and Entreno, and my foods at once (US-121, US-122) · M

**Story:** US-121, US-122 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M (upper end)
**Scenarios:** US-121 The icon in Diario; The icon in Entreno; View only · US-122 Favourites and my foods appear at once; Open Food Facts follows; plus "leave and come back with a quick search typed and a result open".
**Description:** **Core:** `FoodSearchViewModel` emits the matching favourites and my foods first from my data (`search_user_food_ids`/own foods and the favourites, local or one fast query; no wait for `food-search`), then appends the `food-search` OFF rows below when they arrive (deduped by id; the server already hides copied OFF rows, T-254); the T-255 leftover (favourites sorted first only from the favourites already loaded) is fixed here; a slow or failed OFF call leaves my block on screen with the existing error/offline handling for the OFF part. A view-only mode flag for the food search and the exercise search (no target meal, no workout). **UI — templates and patterns:** Diario header (tab root, R-01/R-04): a magnifier `AppTopBarAction` "Buscar alimento" at the top right, left of the calendar icon, bell kept (G13); ux-reviewer checks the R-02 icon count for a tab root header with bell + calendar + magnifier. Entreno header: the same icon "Buscar ejercicio", same position rule. The food search opened from it is the existing `FoodSearchScreen` (`AppSearchField`, R-26; `FoodSearchTemplates` rows; favourites star R-56) in view-only mode: tapping a result opens the existing food details sheet (Info archetype in view-only, R-07) with no "Añadir" footer (barcode and photo entries per ux-reviewer: hidden in view-only since they lead to adding). The exercise search is the existing exercise library/picker list in view-only mode: a row opens the existing exercise details screen (tabs Resumen, Similares, Historial, Cómo hacerlo) without "Añadir". Back returns to the search with the query. Sweep: every caller of the food search and exercise picker keeps its add flow unchanged. Glossary §10 rows for the two icons' descriptions. Docs: `05` §8/§10.
**Acceptance criteria:**
- [x] Diario shows a magnifier at the top right, left of the calendar; it opens the food search. Entreno shows the same icon; it opens the exercise search.
- [x] A result opened from a quick search shows its details and no "Añadir"; the same screens opened from a meal or a workout still add.
- [x] Typing in the food search lists matching favourites and my foods before any OFF response (a test with a delayed `food-search`); OFF results are appended afterwards; with no own match, OFF results appear.
- [x] The quick searches with their query and an open result survive background and process death (ADR-40); offline keeps the standard behaviour (R-30); `check-ux-conventions.sh` passes.
**Host tests:** `FoodSearchViewModelTest` (own block emitted before a delayed OFF response, favourites not yet loaded, dedupe, OFF failure keeps own results, view-only mode); exercise search ViewModel (view-only); Robolectric (Diario and Entreno header icon order and descriptions, details without "Añadir" in view-only, with "Añadir" from a meal, back keeps the query, restoration).
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change (if the immediate block needs an RPC, pgTAP and `deploy-supabase` apply).
**Agent runs:** 7. About 14 files.
**Dependencies:** T-262 merged (no build dependency).
**Files likely touched:** `feature/diary/{DiaryScreen,DiaryDateHeader}.kt`, the Entreno root screen, `feature/food/{FoodSearchViewModel,FoodSearchScreen,FoodSearchTemplates,AddFoodSheet,AddFoodUiState}.kt`, the exercise picker/library screen and ViewModel, `feature/exercises/` details entry, `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-264: A compact Trainer with "Detalles" (US-120) · M

**Story:** US-120 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-120 The Trainer shows the essentials; Details hold the plan and the block; Nothing is lost; State survives leaving the app.
**Description:** **Content map (ux-reviewer in the plan, from today's `TrainerContent`):** the Trainer root keeps the current block status (block, current week, its adherence line), the weekly summary entry, and every pending notice that asks for a decision or a look (calorie proposal, coach changes to undo, block review due, welcome back, discomfort follow-ups); "Detalles" holds the nutrition plan (`PlanSection` with its targets, cardio, diet break, weeks in phase) and the block with its full content (`BlockSection`, `PlanWeeksSection` with deload request and pause); the "Tus datos" and "Lo que el entrenador sabe de ti" rows and the "Bloque nuevo"/"Crear plan nuevo" actions are placed by ux-reviewer on one of the two (each stays exactly where one tap from the Trainer reaches it). The plan lists every figure and action shown today and where it lands ("Nothing is lost" is checked against that list). **UI — templates and patterns:** Trainer stays a tab root with its header and bell (R-01/R-04, G13); "Detalles" is a `NavigationRow` (R-03; label = the pushed screen's title "Detalles") opening a pushed `AppScreen` (R-06) with back (R-05); cards and rows move unchanged (no new component); live switches keep R-42; R-57 unchanged. Both screens share `TrainerViewModel` state or a split ViewModel (planner), with one load. Folded follow-ups (#522): rename `CalorieProposalCard`'s `change` parameter to `proposal`; merge `acceptOffline`/`discardOffline` in `TrainerScreen.kt`. Glossary §10: "Detalles". Docs: `05` §8/§10.
**Acceptance criteria:**
- [x] The Trainer shows the block status, the weekly summary and any pending notice, without the plan and block bodies.
- [x] "Detalles" shows the nutrition plan and the block with their full content and actions.
- [x] Every figure and action listed in the plan's content map is on the Trainer or in "Detalles" (a host test per figure group).
- [x] "Detalles" survives background and process death (ADR-40); offline and error states as today; `check-ux-conventions.sh` passes.
**Host tests:** `TrainerViewModelTest` adapted; Robolectric `TrainerFlowTest` (root content, each notice type, "Detalles" navigation, every figure group reachable), `TrainerRestorationTest` (Detalles open, process death), existing plan/block action tests moved to "Detalles".
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change.
**Agent runs:** 7. About 10 files.
**Dependencies:** T-263 merged.
**Files likely touched:** `feature/trainer/{TrainerScreen,TrainerUiState,TrainerViewModel,TrainerPlanSection,PlanWeeksSection,TrainerDetailsScreen}.kt`, `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

### 👤 Gemini terms check (decision gate for T-265; not a test checkpoint; the only open user item of E18) *(epic US-130, ADR-103)*

- [ ] **Decision (user, 2026-11-09): free tier** (about one question a month; no reason to pay). **RESOLVED, no check needed:** `food-photo` already runs on the free tier from the user's account and works, so the chat uses the same key and tier; the Spain/EEA terms check below is dropped and T-265 no longer waits on it. (Old text, superseded:) The user checks Google's current Gemini API terms for Spain/EEA (whether the free tier may be used there or paid services are required) and decides: free tier, or paid tier with a low spending limit (money, the user's console). The same answer applies to `food-photo` (ADR-59); a change of tier for `food-photo` is a user action in the console, not a task, unless the key or model changes. The PM records the decision in `09-open-questions.md` and ADR-103's consequences (docs PR). It can be done any time before T-265 starts.

---

## T-265: The exercise chat Edge Function (US-130 backend) · M

**Story:** US-130 (backend) · **Flow:** planner → backend-developer → pr-reviewer → pr-creator (§2.2, no UI) · **Size:** M
**Scenarios:** US-130 Asking a question (server: a trainer-expert answer); Only the exercise is shared; The app takes no actions; No answer (busy, daily limit: distinct error codes); The key stays server-side; No history (nothing stored).
**Description:** **Needs the Gemini gate decided; user decisions Q2 (2026-11-09) apply.** New Edge Function `exercise-chat` (auth required, `authenticated` users only): input `{exercise: {name, muscles, instructions}, question}` and nothing else. Each question is answered on its own: no earlier questions or answers are sent (Q2(a); multi-turn context is a possible later-epic idea, not E18). Strict schema with length caps, unknown fields rejected; calls Gemini with `GEMINI_API_KEY` (server-side, ADR-17) through a shared client extracted from `food-photo` into `_shared/gemini.ts` (`food-photo` migrated to it, its tests unchanged: sweep R-39 analogue); a system prompt that answers as a trainer expert on that exercise, in Spanish, only about the exercise (technique, muscles, variations, common mistakes), declines medical diagnosis (`01` §4) without a generic disclaimer (R-52, epic non-goal), and has no tools/function calling, so it cannot read or change anything; no database read or write besides auth, no logging of the question or the answer (ADR-59 precedent). Errors mapped to stable codes the app shows: provider busy/overloaded, Gemini's own quota or daily limit reached (`429`/`RESOURCE_EXHAUSTED`), invalid input, timeout. "Daily limit" means only Gemini's quota (Q2(b)): no app-side cap, counter or usage table. `fitness-scientist` reviews the system prompt's scope wording (PM step, no new `07` rule unless it asks for one). **Docs:** `02` (the function, providers), `03` §7 (contract), ADR-103 consequence line; `supabase/config.toml` entry; deploy workflow includes the function.
**Acceptance criteria:**
- [x] A valid request returns an answer; the Gemini request body carries only the exercise's name, muscles, instructions and that one question (no earlier turns), and no tools.
- [x] Extra fields (diary, weight, health, account, earlier turns) are rejected; nothing is stored, counted or logged.
- [x] Busy, daily limit, timeout and invalid input return their own codes; no key in the response or the repository.
- [x] `food-photo` behaves exactly as before on the shared client.
**Host tests:** Deno tests for `exercise-chat` (schema, extra fields rejected, request body contents, no tools, error mapping per provider status, auth required, no logging of content) and `food-photo` on the shared client.
**Verification:**
- [x] `deno test --allow-all supabase/functions` green; after the merge `deploy-supabase` green on `main`, `exercise-chat` and `food-photo` deployed.
**Agent runs:** 6 (incl. `fitness-scientist` prompt review). About 9 files.
**Dependencies:** T-264 merged; 👤 Gemini terms check decided (Q2 resolved by the user, 2026-11-09).
**Files likely touched:** `supabase/functions/exercise-chat/{index,handler,prompt,schema}.ts` (+ tests), `supabase/functions/_shared/gemini.ts` (+ test), `supabase/functions/food-photo/handler.ts`, `supabase/config.toml`, `.github/workflows/deploy-supabase.yml`, `planning/{02-architecture,03-data-model}.md`
**Estimated scope:** M

---

## T-266: Ask about an exercise: the chat tab (US-130) · M

**Story:** US-130 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-130 The chat is next to "Cómo hacerlo"; Asking a question; Only the exercise is shared (app side); No history; The app takes no actions; No answer; The key stays server-side (app side); plus "leave the app and come back with a question typed and an answer shown".
**Description:** **Core:** `ExerciseChatRepository` over `exercise-chat` (sends the exercise's resolved name, R-54, muscles and instructions from the catalogue or the custom exercise, and the current question; never earlier questions or answers, Q2(a)); a chat ViewModel holding the conversation in memory and `SavedStateHandle` only (restored after background and process death, ADR-40; cleared when the user leaves the exercise, "No history"; never persisted); offline detected before sending. **UI — templates and patterns:** a new tab in the exercise details `AppTabRow` (R-33) right after "Cómo hacerlo" (label by ux-reviewer, glossary §10); the tab is a message list (lazy, R-23; question and answer bubbles from design-system tokens; a shared component only if ux-reviewer asks, built in `designsystem/` with a catalog entry) with an input field pinned at the bottom (R-24, keyboard "Enviar") and a send icon button (R-39, `contentDescription`); "Respondiendo…" style progress while waiting (R-31, copy by ux-reviewer); no answer: one short message in the conversation naming why (busy, Gemini's daily limit, offline per R-37 "Sin conexión: no se puede …"; no app-side counter, Q2(b)) with "Reintentar" where retry makes sense; empty state with one line on what to ask (R-57 checked); no disclaimer (R-52). The rest of the details screen is unchanged. Docs: `05` §8/§10.
**Acceptance criteria:**
- [x] The exercise details show the chat tab next to "Cómo hacerlo"; a question gets an answer in the tab.
- [x] The request carries only the exercise's name, muscles, instructions and the question (repository test).
- [x] Leaving the exercise and opening it again starts an empty conversation; leaving the app and coming back (and process death) keeps it.
- [x] Busy, daily limit and offline each show their short message and the rest of the app works; no key in the app.
- [x] `check-ux-conventions.sh` passes.
**Host tests:** repository/DTO tests (body contents, error codes); ViewModel tests (send, answer, each error, offline, cleared on leaving, restored from `SavedStateHandle`); Robolectric (tab order, send, progress, messages per error, empty state, restoration with `StateRestorationTester`, empty after leaving and reopening).
**Verification:**
- [ ] lint + `testLocalDebugUnitTest` green. No backend change.
**Agent runs:** 7. About 10 files.
**Dependencies:** T-265 merged and deployed.
**Files likely touched:** `shared/…/domain/{model/ExerciseChat,repository/ExerciseChatRepository}.kt`, `shared/…/data/{remote/ExerciseChatDto,repository/SupabaseExerciseChatRepository}.kt`, DI, `feature/exercises/{ExerciseChatTab,ExerciseChatViewModel,ExerciseChatUiState}.kt` and the details screen with its tabs, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

**Checkpoint E18 feedback (user, 2026-11-10; screenshots `design/reported/E18-trainer-cambios-del-entrenador.png`, `design/reported/E18-preguntar-useless-answer.png`; not yet planned, fixes first):**
1. **Compact Trainer (T-264) is not what was asked.** The requirement was: a summary in a box on the Trainer, and tapping the box shows its information; not moving everything to a new "Detalles" section. The user misses plenty of information to understand what is going on.
2. **"Cambios del entrenador" (Bicicleta Estática):** shows "Este ejercicio no tiene un rango de repeticiones, así que el entrenador no puede subirlo solo." for a cardio exercise: why would cardio need a rep range? And the "Añadir un rango" button does nothing when tapped (bug).
3. **"Preguntar" chat (T-266) is of no value as built:** asked for safer replacement exercises for Hip thrust con barra (injury mentioned), the answer was only "Consulta con un profesional sanitario y, si te molesta en este ejercicio, puedes usar Registrar molestia en la app." The user asks why this chat is being built at all. Open product question for the user: keep, rework or drop US-130.

**User decisions on the feedback (2026-11-10):**
- **Chat is kept and reworked (not dropped).** No "consult a professional" / health-professional message, and no bias, in the chat: it answers the question asked (e.g. safer replacement exercises for an injury) like an expert trainer. This includes the `Registrar molestia` pointer the T-265 prompt added to its medical line: remove it. `fitness-scientist` revises the prompt and `07` §3 for the chat; **the user reviews the exact prompt wording before it is implemented** (a hard gate in the fix task).
- **Trainer rework goes through `ux-reviewer`** (plan and diff) to build a proper Trainer section: summary boxes on the Trainer that expand/open their information (feedback 1), a sensible "Cambios del entrenador" (no rep-range prompt for cardio; the "Añadir un rango" tap fixed).
- Fix tasks (next sessions, one per session, `/clear` between): trainer layout and "Cambios del entrenador" first, chat prompt second; then Checkpoint E18 is re-presented.

### Checkpoint E18 — Functional improvements, with the final E18 regression 👤 (16 items, leave and come back included) *(epic-planner, 2026-11-09; the only E18 checkpoint, user decision 2026-11-09; its approval releases T-267)*

After T-266 merges (the last implementation task), CI green, `deploy-supabase` green on `main` after T-259, T-261 and T-265 with data intact, `coach`, `exercise-chat` and `food-photo` deployed, `ANTHROPIC_API_KEY` unset. Main account. **Before installing, take screenshots of your Trainer** (for item 12). What to try on the phone (`python scripts/deliver-apk.py`):

*Anthropic removed (T-259, T-260):*
- [ ] 1. Open Perfil and the Trainer (and its ⋮ menu, if any): there is no "Uso de IA", no monthly limit and no model selector.
- [ ] 2. Everything you use works as before: your nutrition plan and block, the block builder and the folder review (without saving), the weekly summary, a food from a photo; past Diario days and your history are intact.

*Past reports (T-261, T-262):*
- [ ] 3. Centro de avisos → "Informes": your weekly summaries and block reviews, newest first (only the ones produced since the update, plus the block reviews already stored); open one: the same figures as when you first saw it, and no buttons to decide again.
- [ ] 4. Go back from a report: you are on the list where you left it; open another.
- [ ] 5. With a report notice listed ("Tu resumen semanal está listo" or "Tu bloque ha terminado: revísalo"), open it: it leaves the active list and is in "Informes". (If no report notice is pending that day, this item moves to "Pending natural-trigger feedback", as in E15.)
- [ ] 6. Weight, measurement and check-in reminders still appear in Centro de avisos when due and never appear in "Informes".

*Quick search and my foods at once (T-263):*
- [ ] 7. Diario: a magnifier at the top right, left of the calendar icon, opens the food search; in Entreno the same icon opens the exercise search.
- [ ] 8. Open a food and an exercise from these searches: their details with no "Añadir"; back returns to the search with your text. Adding from a meal or a workout still works.
- [ ] 9. Type the name of a favourite or one of your foods (from the quick search and when adding to a meal): yours appear at once and Open Food Facts results are added below afterwards; a name you never added shows OFF results.

*Compact Trainer (T-264):*
- [ ] 10. Open the Trainer: the block status, the weekly summary and any pending notice are at the top without a long scroll.
- [ ] 11. Tap "Detalles": the nutrition plan and the block with their full content and actions.
- [ ] 12. Compare with your screenshots: every figure is on the Trainer or in "Detalles".

*Ask about an exercise (T-265, T-266):*
- [ ] 13. Open an exercise's details: a chat tab next to "Cómo hacerlo"; ask about the exercise (technique, muscles, a common mistake): the answer reads as a trainer expert on that exercise, with no disclaimer. Each question is answered on its own.
- [ ] 14. Ask it to change your routine or to tell you your weight: it only answers; nothing in the app changed.
- [ ] 15. Turn on airplane mode and ask: a short message says why there is no answer and Diario and Entreno still work; leave the exercise and open it again: the conversation is empty.

*Leave and come back (final regression):*
- [ ] 16. Leave the app and come back (and after the system kills it) with a past report open, with "Detalles" open, with a quick search typed and a result open, and with a question typed and an answer shown in the chat: the same tab, screen and draft are restored each time.
Out of scope here (pgTAP, Deno and host tests only): no Anthropic code or key remains; `coach_usage` is gone; another account's reports are not readable; only the exercise and the question are sent; the busy and Gemini daily-limit messages.

---

## T-268: fix(T-268) Trainer summary boxes and "Cambios del entrenador" · fix (UI)

**Story:** Checkpoint E18 feedback 1 and 2 (US-120 rework) · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer if needed → compose-ui-developer → pr-reviewer + ux-reviewer (diff) → pr-creator.
**Description:** Replace the "Detalles" split of T-264: the Trainer shows summary boxes (nutrition plan, block, weekly summary, notices) and tapping a box expands or opens its full information, so nothing the user needs is hidden behind a new section. "Cambios del entrenador": no rep-range prompt for cardio exercises, and fix the "Añadir un rango" button that does nothing. Screenshot: `design/reported/E18-trainer-cambios-del-entrenador.png`.
**Acceptance criteria:**
- [x] Each Trainer summary box opens its full information (all figures and actions that existed before T-264); the Trainer has no "Detalles" entry.
- [x] A cardio exercise never shows the rep-range message; "Añadir un rango" works for strength exercises.
- [x] Host tests cover both; state survives leaving the app; `check-ux-conventions.sh` passes; lint + tests green.
**Dependencies:** none (T-266 merged).

## T-269: fix(T-269) Exercise chat prompt rework · fix (backend + gate)

**Story:** Checkpoint E18 feedback 3 (US-130 rework) · **Flow:** fitness-scientist (prompt + `07` §3) → **user reviews the exact prompt wording** (hard gate) → backend-developer → pr-reviewer → pr-creator.
**Description:** The chat answers the question asked like an expert trainer (e.g. safer replacement exercises for an injury): no "consult a professional" message, no bias, and remove the `Registrar molestia` pointer from the T-265 medical line. Screenshot: `design/reported/E18-preguntar-useless-answer.png`.
**Acceptance criteria:**
- [x] `07` §3 and the prompt revised by `fitness-scientist`; the user approved the exact wording before implementation.
- [x] Handler/prompt tests updated; `exercise-chat` redeployed green on `main`.
**Dependencies:** T-268 merged. Then Checkpoint E18 is re-presented.


## T-270: fix(T-270) Trainer loads slowly on open and in the "Tu plan" / "Tu bloque" boxes · fix (core + UI)

**Story:** Checkpoint E18 feedback 4 (US-120 rework) · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer + ux-reviewer (diff) → pr-creator.
**Description:** Opening the Trainer and opening "Tu plan" or "Tu bloque" each show 2-3 s of loading. **Regression (user, 2026-11-10): the Trainer was fast before E18, so the planner first finds which E18 task (T-259…T-268) added the extra sequential calls or the per-box ViewModel (`git log -p -- feature/trainer`) and removes or parallelises exactly those, rather than only adding parallelism.** Cause (code read, not timed): `TrainerViewModel.fetch()` (`feature/trainer/TrainerViewModel.kt`) is one sequential chain of ~18-22 Postgrest round trips, run on every open and again on every resume; the T-268 box screens (`TrainerBoxRoot`, `AppNavHost` `TrainerPlanRoute`/`TrainerBlockRoute`) create a new `TrainerViewModel` that repeats the whole fetch although the Trainer root already holds the data. Fix in two steps: (1) the box screens reuse the Trainer root's state (scope the ViewModel to the Trainer graph entry or hoist the content state): opening a box costs no fetch; (2) `fetch()` runs the plan-independent reads in parallel (derived estimates, follow-ups, week summary, block review, goals checklist, weeks in phase) and keeps the plan-dependent order (`currentPlan` → diet break → deload / calorie proposal → weeks / block pause). The planner measures the result against a round-trip count in a test; a `trainer_home_snapshot` RPC is out of scope unless the planner shows steps 1-2 are not enough.
**Acceptance criteria:**
- [ ] Opening "Tu plan" or "Tu bloque" shows its content at once (no skeleton) when the Trainer is already loaded; state survives leaving the app.
- [ ] The Trainer open issues the independent reads in parallel; a host test asserts the sequential depth dropped and the evaluate/apply order is unchanged.
- [ ] Behaviour and figures shown are identical to today; `check-ux-conventions.sh` passes; lint + tests green.
**Dependencies:** T-269 merged. Then the Trainer open and the two boxes are re-presented.

---

## T-267: E18 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E18 Definition of Done · **Flow:** PM closure step after Checkpoint E18 is approved (its approval releases this step; no checkpoint of its own). No developer agent, no size class; it cannot merge into a task because it waits for the checkpoint approval.
**Description:** Version bump (`versionName`/`versionCode`, minor per epic, proposed `1.9.0`), tag, retrospective `retrospectives/E18.md`, archive of the E18 log lines; `09-open-questions.md` records Q1, Q2 (user, 2026-11-09) and the Gemini terms decision with their answers; `01-scope.md` E18 row marked done; the epic status set to done.
**Acceptance criteria:**
- [ ] T-259…T-266 (and any fix tasks) merged with green CI; `deploy-supabase` green after each migration and function change.
- [ ] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [ ] `00`, `02`, `03`, `04`, `05`, `07`, `phase2/01-scope.md` NFR-02 and the epic file are current; Checkpoint E18 approved by the user.
- [ ] Version bumped; `tasks/todo.md` pointer updated through a PR.
**Verification:**
- [ ] `git tag` lists the E18 tag.
**Dependencies:** Checkpoint E18 approved.
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `planning/01-scope.md`, `planning/epics/E18-functional-improvements.md`, `retrospectives/E18.md`
**Estimated scope:** closure step (PM)

---

# E17 · Food data quality → `v1.x` (proposed `v1.8.0`)

Epic: [`planning/epics/E17-food-data-quality.md`](../planning/epics/E17-food-data-quality.md) (stories US-116…US-119, with "Decisions (user, 2026-11-09)"). Plan: [tasks/plan.md](plan.md).
Rules: **`07` §4 "Food energy check (E17, US-116/US-117)"** (the source for every E17 factor, threshold, ordering and text: `computed = 4·protein + 4·(carbs − p) + 2.4·p + 9·fat + 2·fibre + 7·alcohol` with `p = min(polyols, carbs)`; skip when `kcal_100 ≤ 13` and `computed ≤ 13`; `mismatch` when `|gap| > 0.15 × kcal_100 + 5`; `clearly_wrong` when `|gap| > 0.30 × kcal_100 + 5`; the worked-examples table; the "saved anyway" flag; alcohol from manual entry, AI and OFF); `04` §2 (food rules, ADR-53 snapshots); `03` §3 `foods`, §6 Food RPCs, §7 `food-search` / `food-barcode` / `food-photo`; ADR-43, ADR-53 (amended by ADR-101), ADR-59, ADR-64, ADR-72, ADR-101; `05` §8-§10 (R-08: no dialog for the warning). Deterministic: the check is code, no extra model call; the only model change is the `food-photo` response schema (alcohol), "Uso de IA" is not involved (Gemini, ADR-59).
**User decisions (2026-11-09, also in the epic file):** Q-E17-1 optional "Alcohol (g)" (7 kcal/g, grams per 100 g/ml): typed by hand, returned by the AI photo estimate (0 when none, never % vol, never omitted), converted by the OFF import from % vol × 0.789. Q-E17-2 a per-food "saved anyway" flag set by "Guardar igualmente", cleared whenever kcal, protein, carbs, fat, fibre, polyols or alcohol change; a flagged food is not in the US-117 audit; edits that touch none of those inputs keep it and show no warning. Both land in the T-251 migration (alcohol column + flag) with a new ADR (proposed ADR-102). Q-E17-3 every reference (diary entries, favourites, saved comida items) moves to the personal copy, in the T-254 conversion and for every new add in T-255.
"Notes for later epics": no item targets E17 (the "Recuperar" note is a non-goal; the "Lo que el entrenador sabe de ti" note targets E19); nothing moved. No carried Phase 3 follow-up is about foods.

**Status (epic-planner, 2026-11-09): PROPOSED, needs the user's OK on the breakdown PR; one checkpoint only, at the end (user decision 2026-11-09).** Q1 resolved by the user (2026-11-09, Q-E17-3 in the epic file; US-119 Gherkin amended): diary entries, favourites and saved comida items all move to the personal copy, in the conversion and for every new add. No question blocks a task. Overlap noted: E18 US-122 ("Favourites and my foods first", listed immediately) builds on T-255's ordering; T-255 orders the list, E18 adds the immediacy.

**Order and sizing (every task pays 6-8 hand-offs; no S task):**
1. **T-251** (M, backend): US-116 + US-117 backend. US-116 is split backend/UI because the whole story is about 28 files (migration, SQL check, audit RPC, `food-photo` schema and prompts, OFF import, then the shared Kotlin check and every form path). The audit RPC rides here: it is the same SQL function over the user's foods, and alone it would be S.
2. **T-252** (M, UI + core): US-116 in the app: the shared Kotlin check, "Alcohol (g)" and the warning on every form path (manual, edit, AI dish, label scan).
3. ✅ **T-253** (M, UI): US-117, the food audit screen. No checkpoint here (user decision: one checkpoint at the end).
4. **T-254** (M, backend): US-118 + US-119 backend. The copy function is shared by "add an OFF food" (US-118) and the conversion (US-119); the conversion alone is S (one data migration), so both go in one backend task; split from the UI for the same size reason as US-116 (about 20 files together).
5. **T-255** (M, UI + core): US-118 in the app: my foods and favourites first, adding an OFF food creates or reuses my copy, the copy is editable, the energy check on the copy. Last implementation task. → **Checkpoint E17** (the only one; final regression).
6. **T-256**: E17 closure (PM closure step).

---

## T-251: Food energy check, "Alcohol (g)", the "saved anyway" flag and the audit: backend (US-116, US-117 backend) · M

**Story:** US-116, US-117 (backend) · **Flow:** planner → backend-developer → pr-reviewer → pr-creator (§2.2, no UI) · **Size:** M
**Scenarios:** US-116 The check covers every way a food is created (server side: the AI estimate returns alcohol; the OFF import stores grams); A small gap is accepted; Saving anyway is allowed (the flag) · US-117 The list shows foods that fail the check (RPC); Nothing changes on its own; A fixed food leaves the list (flag and recompute).
**Description:** **Migration:** `foods` gains an optional alcohol column in grams per 100 base units (e.g. `alcohol_g_100 numeric(5,1) null`, check 0-100; never % vol) and the "saved anyway" flag (e.g. `energy_check_saved_anyway boolean not null default false`; names by the planner). A trigger (or the RPCs, planner's choice; a trigger is safer because it covers every write path) clears the flag whenever `kcal_100`, `protein_100`, `carbs_100`, `fat_100`, `micronutrients` fibre or polyols, or alcohol change, unless the same write sets it explicitly with "Guardar igualmente"; name, brand, portions and other micronutrients keep it (Q-E17-2). One deterministic SQL function `food_energy_check(kcal, protein, carbs, fat, fibre, polyols, alcohol)` → `{result: ok | skipped | mismatch | clearly_wrong, computed, gap}` exactly as `07` §4 "Food energy check" (factors, `p = min(polyols, carbs)`, the skip rule, 15 % + 5 and 30 % + 5; unrounded `computed`; missing fibre, polyols and alcohol count as 0). `create_personal_food` and `update_personal_food` accept the alcohol value and the flag. **Backfill:** OFF rows already cached get the alcohol in grams from `off_data` (`nutriments.alcohol` / `alcohol_100g` in % vol × 0.789), so T-254's copies carry it. RPC `food_energy_audit()` (invoker, `authenticated` only, owner-scoped: the caller's `source = 'user'` foods) lists foods with `mismatch` or `clearly_wrong` and no flag, `clearly_wrong` first, then by `|gap|` descending, with `kcal_100`, `computed` and `gap`; it writes nothing. **Edge Functions:** `food-photo` (`schema.ts`, `prompts.ts`, `handler.ts`): every draft mode (dish, label, barcode) must return alcohol in grams per the same base as the other macros, `0` when the dish or drink has none, never % vol and never omitted; a response without it is invalid (`07` §4 "AI photo estimate"); the prompt states it (label: convert the % vol printed on a drink label to grams, × 0.789). No other model call is added; redeploy `food-photo`. `_shared/off-client.ts` / `food-store.ts`: the OFF normalisation converts `alcohol` % vol × 0.789 into the grams column (a g-based food uses the same figure, `07` design decision). **Docs:** new ADR (proposed ADR-102: the alcohol field and the "saved anyway" flag, user decisions Q-E17-1/Q-E17-2), `03` §3 (`foods` columns), §6 (`food_energy_check`, `food_energy_audit`, the two RPC changes), §7 (`food-photo` alcohol); `04` §2 (a new "Food energy check" paragraph pointing to `07`).
**Acceptance criteria:**
- [x] `food_energy_check` gives the results of every row of the `07` worked-examples table (500 kcal 10/30/10 → `clearly_wrong`, computed 250; 100 kcal 5/10/3 → ok; crispbread with and without fibre; sugar-free sweets with and without polyols; "0 kcal" with 20 g protein → `clearly_wrong`; black coffee → skipped; red wine 83 kcal with 9.5 g alcohol → ok, without → `clearly_wrong`) and the boundaries at exactly 15 % + 5 and 30 % + 5.
- [x] A food saved with the flag keeps it after a name, brand or portion edit and loses it after any change to kcal, protein, carbs, fat, fibre, polyols or alcohol; the stored numbers and past diary snapshots never change.
- [x] `food_energy_audit()` lists only the caller's failing, unflagged foods in the `07` order, and writes nothing.
- [x] `food-photo` returns alcohol in grams in every mode (0 when none) and rejects a model response without it; the OFF import and the backfill store `% vol × 0.789` grams.
- [x] RLS stays on; no other user's food is read or written.
**Host tests:** pgTAP `food_energy_check.test.sql` (the `07` table, both boundaries, missing optionals as 0, polyols above carbs), `food_energy_flag.test.sql` (set by "save anyway", kept on name/brand/portion edits, cleared by each of the seven inputs, a passing food carries none), `food_energy_audit.test.sql` (order, flagged excluded, OFF rows and other users excluded, RLS), the backfill (a cached OFF wine row); Deno tests for `food-photo` schema/handler (alcohol present, 0, missing → invalid, per mode) and the OFF normaliser (12 % vol → 9.5 g; absent → null).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` and `deno test --allow-all supabase/functions` green; after the merge `deploy-supabase` green on `main` with data intact and `food-photo` redeployed.
**Agent runs:** 5. About 13 files.
**Dependencies:** the breakdown approved by the user (the `07` rule is recorded).
**Files likely touched:** `supabase/migrations/<ts>_food_energy_check.sql`, `supabase/tests/food_energy_{check,flag,audit}.test.sql`, `supabase/functions/food-photo/{schema,prompts,handler}.ts` (+ tests), `supabase/functions/_shared/{off-client,food-store}.ts` (+ tests), `planning/00-decisions.md`, `planning/03-data-model.md`, `planning/04-business-rules.md` §2
**Estimated scope:** M

---

## T-252: The kcal/macro warning and "Alcohol (g)" in every food form (US-116) · M

**Story:** US-116 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M (upper end)
**Scenarios:** US-116 A mismatch warns before saving; The check covers every way a food is created (manual, edit, AI dish estimate, label scan here; the OFF copy in T-255); A small gap is accepted; Saving anyway is allowed; plus "leave and come back with the form and the warning open".
**Description:** **Core:** a shared Kotlin `FoodEnergyCheck` (domain use case) identical to the T-251 SQL function, tested with the same `07` table (the 1RM precedent: domain and pgTAP share the cases); `Food`, `NewFood`, `UpdatedFood`, `EditableFood`, the DTOs and `SupabaseFoodRepository` carry alcohol and the flag; `FoodPhotoDraft` / `toNewFoodDraft` carry the AI's alcohol into the prefilled form. `CreateFoodViewModel` runs the check on "Guardar" for a new food, an edit, an AI dish estimate and a label scan (all open `CreateFoodScreen`); a pass saves at once; a failure shows the warning instead of saving; "Corregir" returns to the form with every value kept; "Guardar igualmente" saves the food as entered with the flag. An edit of a flagged food that changes no check input saves with no warning (Q-E17-2). **UI — templates and patterns:** `CreateFoodScreen` / `CreateFoodSections`: "Alcohol (g)" as one more optional number field in the label-order nutrient section next to fibre and polyols (ux-reviewer places it; `04` §2 label order), with the one-line help "Si solo sabes los grados: alcohol (g) por 100 ml = grados × 0,79." (`07`; ux-reviewer finalises, `HelpButton` → Info sheet if it does not fit as a supporting line, R-57). The warning: **no dialog (R-08)**; ux-reviewer picks between an inline warning note above the form footer (the `InlineNote` pattern) with the two actions "Corregir" / "Guardar igualmente", or an action-archetype sheet; copy from `07`: "Las calorías y los macros no cuadran: {kcal} kcal declaradas, los macros suman {computed} kcal.", the `gap < 0` line "Si lleva fibra o alcohol, añádelos: también tienen calorías.", the `gap > 0` line "Si lleva polialcoholes (edulcorantes), añádelos: cuentan menos calorías.", and the `clearly_wrong` prefix "Parece un error:"; numbers via `AppNumberFormat` (R-44); text, not colour alone (R-41). The same component serves T-253 (editor opened from the audit) and T-255 (OFF copy); if it is a new designsystem component it is built here and any ad-hoc copy is retired (R-39). Glossary §10 rows: "Alcohol (g)", the warning, "Guardar igualmente". Docs: `05` §8/§10.
**Acceptance criteria:**
- [x] A food with 500 kcal and 10/30/10 g shows "Parece un error:" with 500 and 250 kcal before saving; "Corregir" keeps every value; "Guardar igualmente" saves it exactly as entered.
- [x] 100 kcal with 5/10/3 g, black coffee and red wine (83 kcal, 9.5 g alcohol) save with no warning; the same wine without alcohol warns, and the warning's help shows the fibre/alcohol line; sugar-free sweets without polyols warn, and the help shows the polyols line (R-57).
- [x] The check runs on a new food, an edit, an AI dish estimate and a label scan; the AI's alcohol appears pre-filled.
- [x] A flagged food edited only in name, brand or portions saves with no warning; changing its kcal warns again.
- [x] The form with values and the open warning survives background and process death (ADR-40); offline keeps the existing form behaviour; `check-ux-conventions.sh` passes.
**Host tests:** `FoodEnergyCheckTest` (the `07` table and boundaries, shared with pgTAP); DTO/repository tests (alcohol, flag); `CreateFoodViewModelTest` (pass saves, fail shows the warning, correct keeps values, save anyway sets the flag, flagged edit without input change, each entry path: manual, edit, dish draft, label draft); Robolectric `CreateFoodScreen` tests (field, help, warning copy per sign and per level, actions, restoration with `StateRestorationTester`).
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change.
**Agent runs:** 7. About 15 files.
**Dependencies:** T-251 merged and deployed.
**Files likely touched:** `shared/…/domain/usecase/FoodEnergyCheck.kt`, `shared/…/domain/model/{Food,NewFood,EditableFood,FoodPhotoDraft}.kt`, `shared/…/data/remote/FoodDto.kt` (and the photo DTO), `shared/…/data/repository/{SupabaseFoodRepository,SupabaseFoodPhotoRepository}.kt`, `feature/food/{CreateFoodDraft,CreateFoodUiState,CreateFoodViewModel,CreateFoodScreen,CreateFoodSections}.kt`, maybe a `designsystem/` warning note, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-253: The food audit (US-117) · M ✅ merged (#580)

**Story:** US-117 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-117 The list shows foods that fail the check; Each food opens its editor; Nothing changes on its own; A fixed food leaves the list; plus "leave and come back with the audit open and with a food's editor open from it".
**Description:** **Core:** repository method over `food_energy_audit()` (T-251), domain model of a row (`kcal`, `computed`, `gap`, level), an audit ViewModel that reloads when the user returns from the editor (a fixed or saved-anyway food leaves the list). **UI — templates and patterns:** a new pushed screen built from the standard list screen template (top bar with back, `ListSectionHeader` if two levels are grouped, list rows with the food name and the `07` line "{kcal} kcal declaradas, los macros suman {computed} kcal ({±gap} kcal)", `clearly_wrong` rows first with the "Parece un error" wording as text, R-41; numbers via `AppNumberFormat`); empty state when nothing fails (copy by ux-reviewer); a row tap opens the existing `CreateFoodScreen` in edit mode with its numbers (US-117 "Each food opens its editor"), where the T-252 warning and "Guardar igualmente" apply; no bulk action, no automatic fix (non-goal). **Entry point:** ux-reviewer picks it from the existing patterns (candidates: a row in Perfil → Preferencias next to the food preferences, or a row in the food search's "Mis alimentos" area) and records it in `05` §10; the screen name too (e.g. "Revisar alimentos"). Navigation routes in `AppRoutes`/`AppNavHost` with state restored (ADR-40). Docs: `05` §8/§10, `04` §2 (the audit).
**Acceptance criteria:**
- [x] The audit lists my foods that fail the check, `clearly_wrong` first, then by gap, each with the declared kcal, the macro kcal and the gap; flagged and passing foods are not listed.
- [x] A row opens the food's editor with its numbers; saving a corrected food or "Guardar igualmente" removes it from the list on return.
- [x] Opening the audit changes no food (no write).
- [x] The audit and the editor opened from it survive background and process death (ADR-40); offline shows the standard hint (R-30); `check-ux-conventions.sh` passes.
**Host tests:** repository/DTO tests; ViewModel tests (order, empty, reload after an edit, a flagged food gone, offline, error and retry); Robolectric (rows and copy, empty state, tap → editor event, entry row, restoration).
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change (if the planner needs an RPC change, pgTAP and `deploy-supabase` apply).
**Agent runs:** 7. About 10 files.
**Dependencies:** T-252 merged.
**Files likely touched:** `shared/…/domain/model/FoodAuditRow.kt`, `shared/…/domain/repository/FoodRepository.kt`, `shared/…/data/repository/SupabaseFoodRepository.kt`, `feature/food/{FoodAuditScreen,FoodAuditUiState,FoodAuditViewModel}.kt`, the entry screen (Preferencias or food search), `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-254: Personal copies of Open Food Facts foods and the conversion of the ones I use: backend (US-118, US-119 backend) · M

**Story:** US-118 (backend), US-119 · **Flow:** planner → backend-developer → pr-reviewer → pr-creator (§2.2, no UI) · **Size:** M
**Scenarios:** US-118 My foods come first (server ordering); Open Food Facts is queried when my foods do not answer; Adding an OFF food creates my copy (RPC); No duplicate · US-119 Used OFF foods become personal copies; Totals do not change; Only my data is touched.
**Description:** **Q-E17-3 (user, 2026-11-09):** every reference moves to the copy: diary entries, favourites and saved comida items (hence default comidas). **Copy RPC:** `copy_off_food(p_food_id) -> food_id` (name by the planner; `security definer` only if RLS on `foods` requires it, every statement scoped to `auth.uid()`): for an OFF row, returns the caller's existing copy when one has the same barcode or `off_id` (US-118 "No duplicate"; a partial unique index per user on barcode and on `off_id` for `source = 'user'`, planner checks existing manual duplicates before adding it), else inserts a `source = 'user'` row owned by the caller with name, brand, barcode, `off_id` (provenance), base unit, per-100 values, `micronutrients`, alcohol grams (T-251) and the OFF portions; never `off_data` beyond what the copy needs (epic security note); the "saved anyway" flag starts false. **Search and barcode:** `food-search` lists the caller's own foods (and favourites) before any OFF row and drops an OFF row the caller already has a copy of; it still queries OFF when the user's foods do not answer (the existing < 10 rule; planner keeps OFF results appended below own results when both exist unless Q2 says otherwise); `food-barcode` returns the caller's copy first when one has the code. **Conversion (US-119), one migration:** for each user with diary entries on OFF foods, create (or reuse) one copy per OFF food and repoint `food_log_entries.food_id`, `favorite_foods.food_id` and `meal_template_items.food_id` to it (Q-E17-3; an OFF food used only in a favourite or a comida also gets its copy; a favourite that would duplicate an existing one on the copy is merged); kcal/macro snapshots, `portion_label`, `group_id` are untouched, so every past day's totals are the same; per-user scoping, no cross-user read or write; the migration counts entries and the per-day kcal and macro sums before and after and aborts on any difference (T-242 precedent). The shared OFF cache rows stay (they serve searches). **Docs:** `03` §3 (copies, the unique indexes, OFF rows no longer referenced by the user's data), §6 (`copy_off_food`), §7 (`food-search`, `food-barcode` ordering); `04` §2 (OFF as a fallback, ADR-101); ADR-53 consequence noted under ADR-101 if needed.
**Acceptance criteria:**
- [x] `copy_off_food` creates one personal copy with name, brand, barcode, macros, micronutrients and alcohol, and returns the same copy on a second call or for another OFF row with the same barcode.
- [x] `food-search` returns my foods and favourites before OFF rows and never an OFF row I already copied; with no own match it returns OFF results; `food-barcode` returns my copy first.
- [x] After the conversion every diary entry, favourite and saved comida item that pointed to an OFF food points to my copy (no duplicate favourite); the kcal and macros of every past day are identical (migration self-check and pgTAP).
- [x] No other account's rows are read or changed; RLS stays on.
**Host tests:** pgTAP `copy_off_food.test.sql` (copy fields, dedupe by barcode and by `off_id`, a manual food with the same barcode, foreign user, RLS), `off_food_conversion.test.sql` (two users, entries repointed, day totals before/after equal, a user without OFF entries untouched, favourites and comida items repointed, an OFF food used only in a comida, a favourite on both the OFF row and the copy); Deno tests for `food-search` (own first, copied OFF row hidden, OFF when no own match) and `food-barcode` (copy first).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` and `deno test --allow-all supabase/functions` green; after the merge `deploy-supabase` green on `main` and the production self-check passes with data intact (day totals unchanged); `food-search` and `food-barcode` redeployed.
**Agent runs:** 5. About 12 files.
**Dependencies:** T-253 merged (no build dependency beyond T-251; one task at a time).
**Files likely touched:** `supabase/migrations/<ts>_off_personal_copies.sql`, `supabase/migrations/<ts>_convert_used_off_foods.sql`, `supabase/tests/{copy_off_food,off_food_conversion}.test.sql`, `supabase/functions/food-search/handler.ts` (+ test), `supabase/functions/food-barcode/handler.ts` (+ test), `supabase/functions/_shared/food-store.ts`, `planning/03-data-model.md`, `planning/04-business-rules.md` §2
**Estimated scope:** M

---

## T-255: An Open Food Facts food becomes my food in the app (US-118) · M ✅ merged (#585)

**Story:** US-118 (+ the US-116 OFF-copy path) · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-118 My foods come first; Open Food Facts is queried when my foods do not answer; Adding an OFF food creates my copy; No duplicate; The copy is editable · US-116 The check covers … OFF copies; plus "leave and come back with the search and a copy's editor open".
**Description:** **Core:** `FoodRepository.copyOffFood` over T-254's RPC; `FoodSearchViewModel.emitTextSearch` keeps own results first and adds the matching favourites in the first block (deduped by id), OFF results below (the server already hides copied OFF rows); `AddFoodViewModel` and every other path that stores a food reference (Q-E17-3: the favourite star, adding an item to a saved comida, the barcode scan result) resolve an OFF food to my copy before writing, so no new reference to a shared OFF row is written; the energy check (T-252's `FoodEnergyCheck`) runs on the copy: a failing OFF food shows the T-252 warning, "Corregir" opens the copy's editor, "Guardar igualmente" keeps it as it came from OFF with the flag. **UI — templates and patterns:** `FoodSearchScreen` / `FoodSearchTemplates` rows unchanged (an own copy is an own row; ux-reviewer checks whether OFF rows need their existing source marker and that a copy shows no OFF marker); the food details sheet of a copy offers the same "Editar" as a manual food and opens `CreateFoodScreen` in edit mode (ADR-53 amended by ADR-101: edits apply to future entries only); the warning component of T-252 (no dialog, R-08). Sweep: every place that hides "Editar" for `source = 'off'` (details, portions) is checked so a copy behaves exactly like a manual food. Glossary §10 if a label changes. Docs: `05` §8/§10.
**Acceptance criteria:**
- [x] Searching shows my foods and favourites before any OFF result; with no own match, OFF results are listed.
- [x] Adding an OFF food to the diary, starring it or adding it to a saved comida creates (or reuses) my copy and stores the copy's id; the next search lists it among my foods and no OFF duplicate; adding the same product again (search or barcode) creates no second copy.
- [x] The copy opens with "Editar" like a manual food; an edit applies to future entries only.
- [x] An OFF food whose kcal and macros do not match shows the warning before it is added; "Guardar igualmente" keeps the OFF values.
- [x] The search with its query, and a copy's editor, survive background and process death (ADR-40); offline keeps the standard behaviour (R-30); `check-ux-conventions.sh` passes.
- [x] Final E17 regression (folded from the closure): the E17 flows make no `coach` model call and leave "Uso de IA" unchanged; `food-photo` is the only model call and is called once per photo.
**Host tests:** repository tests (copy, reuse); `FoodSearchViewModelTest` (own + favourites first, dedupe, OFF below, no own match, restoration); `AddFoodViewModelTest` (OFF add → copy id written, second add reuses, warning on a failing copy, save anyway); favourite-star and comida-item tests (copy id stored); Robolectric (rows, "Editar" on a copy, warning, restoration).
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green. No backend change.
**Agent runs:** 7. About 12 files.
**Dependencies:** T-254 merged and deployed.
**Files likely touched:** `shared/…/domain/repository/FoodRepository.kt`, `shared/…/data/repository/SupabaseFoodRepository.kt`, `feature/food/{FoodSearchViewModel,FoodSearchScreen,FoodSearchTemplates,AddFoodViewModel,AddFoodSheet,AddFoodUiState}.kt`, the barcode scan path, `strings.xml`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

### Checkpoint E17 — Food data quality: wrong foods caught and audited, Open Food Facts foods become mine, with the final E17 regression 👤 (10 items, leave and come back included) *(epic-planner, 2026-11-09; the only E17 checkpoint, user decision 2026-11-09; its approval releases T-256)*

After T-255 merges (the last implementation task), CI green, `deploy-supabase` green on `main` after T-251 and T-254 with the conversion self-check passed and data intact, `food-photo`, `food-search` and `food-barcode` redeployed. Everything is tried on the main account; delete the test foods you create afterwards. What to try on the phone (`python scripts/deliver-apk.py`):

*Catching wrong foods (T-251…T-253):*
- [ ] 1. Create a food with 500 kcal and 10 g protein, 30 g carbohydrate, 10 g fat, then "Guardar": a warning (no dialog) says "Parece un error:" with 500 kcal declared and 250 kcal from the macros; "Corregir" keeps every value.
- [ ] 2. A food with 100 kcal and 5/10/3 g and a black coffee (2 kcal) save with no warning; a red wine (83 kcal, 0,1/2,6/0 g per 100 ml) without alcohol warns, and the warning's help suggests fibre or alcohol; read the "Alcohol (g)" help, enter 9,5 g: it saves with no warning.
- [ ] 3. Save a failing food with "Guardar igualmente": it is saved exactly as typed; edit only its name: no warning; change its kcal: the warning comes back.
- [ ] 4. Add a food from a dish photo and from a label photo: the form arrives pre-filled (alcohol included, 0 when none) and a mismatch warns before saving; "Uso de IA" did not change during E17.
- [ ] 5. Open the food audit: your failing foods (converted OFF foods too) are listed with the gap, the clearly wrong ones first; tap one: its editor opens; fix it (or "Guardar igualmente"): back in the list it is gone; nothing else changed.

*Open Food Facts foods become mine (T-254, T-255):*
- [ ] 6. Open "Diario" on a few past days that had Open Food Facts foods: every day's kcal and macros are the same as before E17, and those foods (also in your favourites and saved comidas) now open with "Editar".
- [ ] 7. Search a name that matches one of your foods or favourites and also OFF products: yours are listed first, OFF below; search something you never added: OFF results appear.
- [ ] 8. Add an OFF product you never used: search it again: it is now one of your foods, with its name, brand, macros and micronutrients (a drink with alcohol shows its grams); scan its barcode or add it again: no second copy.
- [ ] 9. Open the copy, "Editar", change a portion or a value and save: it behaves like a food you created and past entries keep their numbers; an OFF product whose kcal and macros do not match warns before it is added.

*Leave and come back:*
- [ ] 10. Leave the app and come back (and after the system kills it) with a food form and its warning open, with the audit and an editor opened from it, and with a search typed: the same tab, screen and draft are restored.
Out of scope here (host tests and pgTAP only): no other account's data is read or changed by the conversion.

---

## Follow-up to Checkpoint E17: hard energy check (ADR-104) *(user feedback at Checkpoint E17, 2026-11-09)*

The user approved the rest of Checkpoint E17 (items 6-10 and the OFF copy flow); items 1-5 are superseded by ADR-104. Order: T-257 → T-258 → **👤 Checkpoint E17-fix** → T-256 (closure). Each is a separate session (`/clear` between them).

## T-257: Energy check as a hard block: backend and AI prompt (ADR-104) · M

**Story:** US-116/US-117 amended (ADR-104) · **Flow:** fitness-scientist (confirms `07` section 4: blocking level, flag and audit removed, retry contract) → planner → backend-developer → pr-reviewer → pr-creator · **Size:** M
**Description:** Migration: `create_personal_food`, `update_personal_food` and `copy_off_food` reject a food that fails `food_energy_check` (error code for the app); drop `energy_check_saved_anyway`, trigger `foods_energy_flag`, the `p_saved_anyway` parameters and `food_energy_audit()`. `food-photo`: dish and label prompts require consistent kcal and macros; the handler runs the check and returns the issue (declared kcal, macro kcal, gap) when it still fails; it accepts the previous estimate and the failure reason to retry. Update `03`, `04` section 2, `07` section 4 and the tests (pgTAP, Deno). **Last step, after the deploy (data fix):** the backend-developer lists the user's failing foods read-only (`food_energy_check`) and proposes a correction for each from the label/portion data and sources; the PM asks the user for the ones that cannot be derived; the approved corrections are applied by a one-off script (no personal data in the repo; the user enters any credential), then a final read shows none fails and diary totals did not change (snapshots untouched). The PR body records counts only; T-257 is closed after this step.
**Acceptance criteria:**
- [x] No RPC stores or updates a failing food; existing failing rows and diary snapshots are untouched.
- [x] The flag, trigger, parameters and audit RPC no longer exist; `food_energy_check` is unchanged.
- [x] A `food-photo` response that fails the check carries the issue; a retry call with the previous estimate and the reason is accepted and the prompt includes both.
- [x] `deploy-supabase` green on `main`; `food-photo`, `food-search`, `food-barcode` redeployed.
- [x] Every food of the user passes `food_energy_check`; nothing else changed.
**Dependencies:** none (T-255 merged). **Estimated scope:** M

## T-258: Energy check as a hard block: app, AI retry, audit UI removed (ADR-104) · M

**Story:** US-116/US-117 amended (ADR-104) · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Description:** Remove the audit (screen, route, hub row "Revisar alimentos", strings, tests, `05` section 10 entry) and "Guardar igualmente"/the flag from every form. Every path (manual, edit, AI dish, label scan, OFF product) shows the issue and blocks saving until consistent; an OFF product is corrected before its copy is created. AI forms show the issue with a retry icon button that re-calls the estimate with the previous estimate and the reason. Update `05` section 8/10.
**Acceptance criteria:**
- [x] A failing food cannot be saved from any form; the warning shows declared kcal, macro kcal and the gap, and no button saves it.
- [x] An AI estimate that fails shows the issue and the retry button; retry sends the previous estimate and the reason and fills the form with the new result.
- [x] "Revisar alimentos" and "Guardar igualmente" appear nowhere; `check-ux-conventions.sh` passes.
- [x] The form, the issue and the retry state survive background and process death (ADR-40).
**User-reported bug (2026-11-09):** "Editar alimento" on an OFF food shows "No se pudo cargar el alimento" and "Reintentar" fails every time. The user does not think it relates to T-258 (not seen at Checkpoint E17). Not assumed: the core developer diagnoses the root cause. User decision: a trivial fix goes in this branch (with a test); a non-trivial one becomes a separate `fix(T-###)` task (next session, after T-258).
**Dependencies:** T-257 merged and deployed. **Estimated scope:** M

### Checkpoint E17-fix — Hard energy check 👤 (7 items, leave and come back included) *(only these tests; its approval releases T-256)*

- [x] 1. Create a food with 500 kcal and 10/30/10 g: "Guardar" is blocked, the issue shows 500 vs 250 kcal and there is no "Guardar igualmente"; correct it and it saves.
- [x] 2. Edit an existing food into a mismatch: blocked. A red wine without "Alcohol (g)" is blocked; with 9,5 g it saves. A black coffee saves.
- [x] 3. Add a food from a dish photo and from a label photo: the form arrives consistent, or shows the issue with a retry icon button; tap it and the estimate is redone and corrected.
- [x] 4. An Open Food Facts product whose numbers do not match cannot be added until you correct it.
- [x] 5. "Revisar alimentos" is gone from Preferencias; none of your foods fails; the Diario totals of past days are unchanged.
- [x] 6. The foods the PM asked you about are as you told them.
- [x] 7. Leave the app and come back (and after the system kills it) with a blocked form and with the retry state open: the same screen and draft are restored.

---

## T-256: E17 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E17 Definition of Done · **Flow:** PM closure step after Checkpoint E17 is approved (its approval releases this step; no checkpoint of its own). No developer agent, no size class; it cannot merge into a task because it waits for the checkpoint approval.
**Description:** Version bump (`versionName`/`versionCode`, minor per epic, proposed `1.8.0`), tag, retrospective `retrospectives/E17.md`, archive of the E17 log lines; `09-open-questions.md` records Q-E17-1, Q-E17-2 (user, 2026-11-09) and the E17 plan questions with their answers; `01-scope.md` E17 row marked done; the epic status set to done.
**Acceptance criteria:**
- [x] T-251…T-255 (and any fix tasks) merged with green CI; `deploy-supabase` green after each migration, the conversion self-check passed in production.
- [x] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [x] `00`, `03`, `04`, `05`, `07` and the epic file are current; Checkpoint E17 approved by the user.
- [x] Version bumped; `tasks/todo.md` pointer updated through a PR.
**Verification:**
- [ ] `git tag` lists the E17 tag.
**Dependencies:** Checkpoint E17 approved (done, items 6-10) and Checkpoint E17-fix approved.
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `planning/01-scope.md`, `planning/epics/E17-food-data-quality.md`, `retrospectives/E17.md`
**Estimated scope:** closure step (PM)

---

# E16 · Nutrition follow-up → `v1.x` (proposed `v1.7.0`)

Epic: [`planning/epics/E16-nutrition-follow-up.md`](../planning/epics/E16-nutrition-follow-up.md) (stories US-112…US-115). Plan: [tasks/plan.md](plan.md).
Rules: `07` §4 "Nutrition follow-up (E16)" (a)-(d) (the source for every E16 figure, order and text), §2 (data cleaning, the 5-complete-day gate), §3 "Block follow-up" (3) and (7), §4 (calorie proposal, diet breaks), §5 (trend and interval), §9, §10, §12.3, §12.6, §12.7; `04` §6.7, §6.8, §7.3, §7.4, §8.5, §8.9; E14 US-98, US-99, US-105; ADR-73, ADR-89, ADR-98, ADR-101; `05` §8-§10. Deterministic: no task calls the `coach` Edge Function with a model kind, "Uso de IA" never changes.
"Notes for later epics": the R-59 sweep leftover (`block_types_no_fit` has no tap to "Mi entrenamiento") is folded into T-248 (one-line pointer left there); the "Recuperar" note is a non-goal and the "Lo que el entrenador sabe de ti" note targets E19, both left in place. Carried Phase 2 follow-ups folded in (the list above is left as is; the PM ticks them when the task merges): `coach_stalled_lifts` running 2-3 times per summary call and renaming the `change` parameter of `CalorieProposalCard` to `proposal` → T-246.

**Status (epic-planner, 2026-11-08; updated the same day): PROPOSED, needs the user's OK on the PR.** The user chose one checkpoint at the end; the experts resolved the former Q1-Q3 (recorded in `07` §4 "Nutrition follow-up (E16)" (c) and in `tasks/plan.md` "Decisions"): Q1 both show, "Meseta" first with the calorie proposal as its one action; Q2 the cause wins, "Meseta" replaces only "Sin causa clara" (US-114 Gherkin amended: "And no cause of US-113 applies"); Q3 "Descanso de dieta" is offered only in the plan proposal's cut options, no switch on the "Bloque nuevo" confirmation or the review. **PM step done (2026-11-08):** `fitness-scientist` recorded the E16 rules in `07` §4 "Nutrition follow-up (E16)", §9, §12.6-§12.7 and §10; they are the source for every E16 task. No question blocks a task. **Epic Gherkin aligned with `07` (2026-11-08):** US-113 "The first cause only" is amended to the order logging > average calories (weekend variant) > "Meseta" > "Sin causa clara"; protein, sleep and steps are dropped (`07` (b)).

**Code check for Q3 (epic-planner, 2026-11-08):** a goal switch does **not** reuse the proposal's cut options. `planGoalSwitch.ts` (T-239) and `planPhaseChange.ts` (T-233) build the proposed cut with `cutRatePct: 0.5, dietBreak: false` fixed, `BeforeStartViewModel` previews it without `PlanProposalScreen`, and `plan_recompute` (`planRecompute.ts`) refuses any plan that is not `proposed`. "Crear plan nuevo" is retired during a block (E15 Q1), so a cut started by "Bloque nuevo" or by the review never reaches the switch. Hence T-248 makes "Descanso de dieta" editable afterwards in "Tu plan" for the accepted cut plan (backend included); if the planner finds that cannot fit the task, it logs an E9 refinement row with T-248's follow-up as owner instead, and says so in the PR.

**Order and sizing (every task pays 6-8 hand-offs; no S task):**
1. **T-246** (M, backend + UI): US-112, one vertical slice: the nutrition fields of `coach_week_summary` and the section of `WeekSummaryScreen` (`07` (a)).
2. **T-247** (L, backend + UI): US-113 + US-114. The plateau is one outcome of the same weekly nutrition diagnosis (same trigger, same logging check, "Meseta" sits in the same ordered chain), so splitting would build the engine twice. Not split into backend and UI: the UI is one card reusing the US-105 stall-card pattern (about 3 files), so a UI task would be S.
3. **T-248** (M, backend + UI): US-115. The diet break is built (T-188); the story adds the label "Descanso de dieta", the one-sentence `?`, week 4 shown in the plan and the switch in "Tu plan" for an accepted cut (code check above). The R-59 leftover from "Notes for later epics" (a Trainer-tab route in the same plan and block flows) is merged in. Last implementation task. → **Checkpoint E16** (the only one; final regression).
4. **T-249**: E16 closure (PM closure step).

### Pending natural-trigger feedback (E16; not gates, as E15 Q5)

The user does not force time-based triggers. These items are covered by host tests and pgTAP in their tasks; the user reports on them from the main account when each one happens in real use, and each report becomes a `fix(T-###)` task. They block no checkpoint and no closure.
- [x] T-246: in a week when a calorie change was applied or proposed, the nutrition section lists it with its reason.
- [x] T-247: after two weeks with the target rate outside the weight trend's interval, the summary shows only the first cause that applies (logging, then average calories with its weekend variant) and one action line; with the calorie cause, no calorie proposal that week; with none, "Sin causa clara" and the action (the calorie proposal when one is made, else "Sigue con el plan: lo revisamos la semana que viene.").
- [x] T-247: after two flat weeks with 6 or more complete days each and no cause, "Meseta" with the trend, its range and "No es un fracaso", and the calorie proposal (when made) as its action; with fewer logged days, the logging cause instead.
- [ ] T-248: with "Descanso de dieta" on, when week 4 of the cut block arrives the Diario goals move to maintenance calories, and back in week 1 of the next block.

---

## T-246: The nutrition section of the weekly summary (US-112) · M

**Story:** US-112 · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-112 The section shows the week; Missing records are named; The calorie changes of the week are listed; There is one summary; plus "leave and come back with the summary open".
**Description:** **Backend:** `coach_week_summary` (T-231, T-242) gains a `nutrition` object for a normal (not paused) week: complete diary days of the plan week (the §7.3 40 % rule, the same count as `coach_week_gate`), the average kcal and protein of those days only, the target they are compared with (`07` (a): the mean of the daily Diario goals in force on those same days: plan goal, an applied proposal from its day on, the diet-break week's maintenance, or the user's own edit; protein also per kg of the week's average weight), the weekly average weight and trend against the plan's target rate (already in the summary: moved into the section, not duplicated), and every calorie decision of the week with its reason (`coach_recommendations` of kind `calorie_change` created, accepted or declined in the week, with `from_kcal`, `to_kcal` and the reason of `04` §8.5). Under the gate (`complete_days < 5`, `07` §2) the averages are not shown and the section reads "Faltan registros: {n} de 7 días completos. Con menos de 5 no sacamos conclusiones sobre tus calorías." (`07` (a); the planner reconciles it with the existing summary line "Esta semana faltan registros: no cambiamos tus calorías." so one concept has one line, R-35). A paused week keeps `{paused: true}` with no nutrition object. Fold in the carried follow-up: `coach_stalled_lifts` is evaluated once per summary call (temp CTE or local variable). Docs: `04` §6.7 (the nutrition object), `03` if a column or type changes. **UI — templates and patterns:** inside the existing `WeekSummaryScreen` (US-112 "There is one summary": no new route, no new "Centro de avisos" kind; the T-231 notice is unchanged); one more section with `ListSectionHeader` (title by ux-reviewer, glossary §10, e.g. "Nutrición") and fact lines in the style of the existing weight line, with the `07` (a) templates ("Días registrados: {n} de 7", "Calorías: {x} kcal de media ({±d} respecto a tu objetivo de {o})", "Proteína: {p} g de media ({g} g por kg; objetivo {o} g)"; numbers via `AppNumberFormat`, R-44; no verdict label or colour, `07` (a) "No verdict band", R-41; "registraste", never "comiste"); the existing missing-records line and weight line move into this section (one place, R-35); calorie decisions as one line each with their reason, using the wording of the existing `CalorieProposalCard` reason (fold in: rename its `change` parameter to `proposal`); nothing explanatory inline, a `?` (`HelpButton`) only if ux-reviewer asks (R-57). Glossary rows for the section title and the average lines.
**Acceptance criteria:**
- [x] With 5 of 7 complete diary days, the summary's nutrition section shows the logged days, the average kcal and protein against the mean of the daily goals in force those days (a mid-week change compared day by day), and the weight trend against the target rate.
- [x] With fewer than 5 complete days, it shows the `07` (a) missing-records line and no averages.
- [x] A calorie change applied, declined or proposed in the week is listed with its reason; none, no line.
- [x] The section is part of "Resumen semanal" (no new screen or notice); a paused week still reads only "Semana en pausa".
- [x] The summary with the section survives background and process death (ADR-40); `HealthDisclaimerSweepTest` and `check-ux-conventions.sh` pass.
**Host tests:** pgTAP `coach_week_summary_nutrition.test.sql` (5/7 and 4/7 complete days, a partial day excluded, averages, the daily-goal mean with a mid-week applied proposal and in a diet-break week, calorie decisions accepted/declined/pending in and outside the week, paused week, RLS, a summary of a block created before the migration); repository and DTO tests; `WeekSummaryTextTest` (lines, gate, decimal comma); ViewModel and Robolectric tests (section present, gate line, decision lines, restoration with `StateRestorationTester`).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact. No model call.
**Agent runs:** 8. About 13 files.
**Dependencies:** the breakdown approved by the user (the `07` rules are recorded).
**Files likely touched:** `supabase/migrations/<ts>_coach_week_summary_nutrition.sql`, `supabase/tests/coach_week_summary_nutrition.test.sql`, `shared/…/domain/model/WeekSummary.kt`, `shared/…/data/remote/*Dto.kt`, `shared/…/data/repository/SupabaseCoachSummaryRepository.kt`, `feature/trainer/{WeekSummaryScreen,WeekSummaryUiState,WeekSummaryViewModel,WeekSummaryText,CalorieProposalCard}.kt`, `strings.xml`, `planning/04-business-rules.md` §6.7, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-247: "¿Por qué no avanzo?" for nutrition and the plateau explanation (US-113, US-114) · L

**Story:** US-113, US-114 · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** L (backend first, then one card; the planner may run it as two slices)
**Scenarios:** US-113 The first cause only; No clear cause; No health advice · US-114 Two stable weeks with good logging; A plateau with poor logging is not called a plateau; plus "leave and come back with the summary open".
**Description:** **Backend:** a deterministic `coach_nutrition_diagnosis(plan, week)` (name by the planner), called by `coach_week_summary` for a normal week, returning at most one result, exactly as `07` §4 "Nutrition follow-up (E16)" (b)-(c): (1) **Trigger:** the §4 calorie trigger read the same way (the plan's target rate outside the weight trend's interval in two consecutive weekly summaries; any phase, rate 0 for maintenance and recomposition); shown in **every** summary where it holds; a window containing a diet-break week does not trigger; otherwise nothing. (2) **Chain, first that fires wins (Q2 decided: the cause wins):** logging (either week under 5 complete diary days or under 3 weigh-ins; day and weigh-in variants), then average calories (`|logged − goal| ≥ 0.5 × |target rate − trend| × 7 700 / 7` in the direction that explains the miss, with the weekend variant when Saturday and Sunday carry at least half the excess), then **"Meseta"** (US-114: interval contains 0 and excludes the target rate in both weeks, a cut or gain phase, 6 or more complete days in each week; `{kind: plateau, slope, interval_low, interval_high}`), then `{kind: no_clear_cause}` ("Sin causa clara"). Protein, sleep, steps and a separate weekend check are **not built** (`07` (b) "Dropped"). (3) **Calorie proposal (`07` (b), amends `04` §8.5):** no proposal in a week where the calorie cause fires (the 14-day clock is not restarted); with "Meseta" or "Sin causa clara" (Q1 decided: both show) the week's proposal, when made, is the card's one action, otherwise "Sigue con el plan: lo revisamos la semana que viene."; the proposal keeps its own copy (§12.7). Docs: `04` §6 new §6.10 (the nutrition diagnosis, its order and gates) and §8.5 (no proposal in a calorie-cause week), `07` only through `fitness-scientist`. **UI — templates and patterns:** in the T-246 nutrition section of `WeekSummaryScreen`, one card reusing the US-105 `WeekSummaryStallCard` pattern (a cause line and one action line, texts from `07` (b)-(c); title "¿Por qué no avanzo?" or the existing "¿Por qué no progreso?" heading: ux-reviewer picks one label per concept, R-35, and records it in the glossary §10); "Meseta" as the same card with the `07` (c) text (trend and its range via `AppNumberFormat`, "No es un fracaso…"); when the action is the calorie proposal, the card shows the Meseta (or "Sin causa clara") line first and the existing `CalorieProposalCard` actions "Aplicar" / "Ahora no" as its one action (no second proposal card on the screen); no colour-only status (R-41), one line each, no advice block (R-57). **No health advice:** every new string goes through `HealthDisclaimerSweepTest` (no diagnosis, no medical condition; R-52 allow-list unchanged).
**Acceptance criteria:**
- [ ] With the trigger on two consecutive summaries, only the first result of logging > average calories (weekend variant) > "Meseta" > "Sin causa clara" is shown, with one action line; a window with a diet-break week shows nothing.
- [ ] Two flat weeks (interval contains 0 and excludes the target rate) with 6 or more complete days each and no cause show "Meseta" with the trend, its range and "No es un fracaso"; with exactly 5 days the chain ends at "Sin causa clara"; below 5, the logging cause; a rate-0 phase never shows "Meseta".
- [ ] In a calorie-cause week no calorie proposal is made and the 14-day clock is not restarted; with "Meseta" or "Sin causa clara" the week's proposal is the card's one action.
- [ ] No text diagnoses or names a medical condition (`HealthDisclaimerSweepTest`).
- [ ] The summary with the card survives background and process death (ADR-40); `check-ux-conventions.sh` passes.
**Host tests:** pgTAP `coach_nutrition_diagnosis.test.sql` (trigger on one and two weeks, diet-break window, logging day and weigh-in variants, calorie cause at and under the half-gap threshold in both directions, weekend variant at and under half the excess, logging and calorie cause together (only logging), calorie cause with a flat trend (cause wins), plateau with 6/6, 6/5 and 5/6 days, rate-0 phase, paused week, no proposal in a calorie-cause week and the clock untouched, proposal as the Meseta action, RLS); repository and DTO tests; text tests for every cause, plateau and none; ViewModel and Robolectric tests (card per kind, restoration).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact. No model call.
**Agent runs:** 8. About 13 files.
**Dependencies:** T-246 merged and deployed.
**Files likely touched:** `supabase/migrations/<ts>_coach_nutrition_diagnosis.sql` (with the `evaluate_coach_calorie_change` amendment), `supabase/tests/coach_nutrition_diagnosis.test.sql`, `shared/…/domain/model/{WeekSummary,NutritionDiagnosis}.kt`, `shared/…/data/remote/*Dto.kt`, `shared/…/data/repository/SupabaseCoachSummaryRepository.kt`, `feature/trainer/{WeekSummaryScreen,WeekSummaryText,WeekSummaryStallCard}.kt` (or a sibling card), `HealthDisclaimerSweepTest.kt`, `strings.xml`, `planning/04-business-rules.md` §6.10, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

## T-248: "Descanso de dieta" in a cut, editable in "Tu plan", and the R-59 route from the block types note (US-115) · M

**Story:** US-115 (+ the R-59 leftover of T-245) · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-115 Offered, off by default; Turning it on; Never automatic; It explains itself; plus "leave and come back with the proposal open".
**Description:** **Built (T-188, `04` §8.9):** the `diet_break` option of a proposed cut (`plan_recompute`, `update_coach_plan_options`), `nutrition.diet_break` with the week-4 figures at rate 0, `apply_coach_diet_break` moving the Diario goals in plan week 4 (weeks as T-242), off by default. This task verifies it end to end and adds what the story asks: (1) the label "Descanso de dieta" replaces "Con semana de descanso" everywhere (`plan_proposal_diet_break_*`, glossary §10 row "Optional easy week in a cut", R-35); (2) the `?` reads the `07` §12.7 / §4 "Nutrition follow-up" (d) sentence "Opcional: la semana 4 del bloque comes lo que gastas; en los estudios se pasa menos hambre, pero no se pierde grasa más rápido." (`phase_diet_break_help`); (3) "the plan shows it": with the option on, "Tu plan" (`TrainerPlanSection`) shows the week-4 maintenance calories as one fact line and the plan weeks (`PlanWeeksSection`) mark week 4 with the label (text, not colour alone, R-41); off, nothing is shown and no week changes (Never automatic); (4) **Where it is offered (Q3 decided):** only in the plan proposal's cut options; no switch on the "Bloque nuevo" goal confirmation (T-240) or the review sheet (T-234). **(5) Editable afterwards in "Tu plan" (code check in the E16 header: the goal switch and the review build their cut with `dietBreak: false` and never reach the proposal):** for the accepted cut plan, a "Descanso de dieta" switch in "Tu plan" turns the option on or off; backend: `plan_recompute` (or a sibling deterministic kind, planner's choice) accepts the accepted cut plan for the diet-break option only, recomputes `nutrition.diet_break` without touching the current week's goals, the calories, the band or `edited_by_user`, and `apply_coach_diet_break` picks it up from the next Trainer open (the planner defines turning it on or off inside week 4, `04` §8.9). If this cannot fit the task, the planner logs an E9 refinement row with an owning follow-up task instead and says so in the PR. **Folded in (Notes for later epics, R-59 leftover of T-245):** `block_types_no_fit` (`BlockTypesScreen`, `BlockTargetsScreen`) names the three levers but has no tap; add the R-55 tap to "Mi entrenamiento" (the editor of the days and session length) through the builder routes and `AppRoot`, returning to the same builder step with its draft (ADR-40). **Templates and patterns:** the existing `CutOptions` switch row with `HelpButton` → Info sheet (R-46); on the proposal the switch saves with "Aceptar"; in "Tu plan" it is the existing live-in-editor switch row pattern (R-42; ux-reviewer decides between a switch row and a row opening a one-field editor, R-55); fact line in "Tu plan" as the phase line (`05` §9.0b); the week label as the existing `PlanWeeksSection` week state chip ("Semana en pausa" precedent); the R-59 tap as the existing tappable note row used by T-245.
**Acceptance criteria:**
- [x] On a cut plan proposal, "Descanso de dieta" is an option that is off; its `?` reads the one sentence of `07` §12.7.
- [x] Turned on and accepted, "Tu plan" and the plan weeks show week 4 at maintenance calories, and in plan week 4 the Diario goals are the maintenance figures (existing `apply_coach_diet_break`, end-to-end test); off, no week shows a break and the goals never change by week.
- [x] No "Descanso de dieta" switch on the "Bloque nuevo" confirmation or the review; for an accepted cut (also one started by a goal switch or the review) it can be turned on and off in "Tu plan", and nothing else in the plan changes.
- [x] `block_types_no_fit` has a tap that opens "Mi entrenamiento" and returns to the builder step with the draft kept (R-59).
- [x] The proposal with the option changed, and the builder after the round trip, survive background and process death (ADR-40); offline disables the switch with the standard hint (R-30); `check-ux-conventions.sh` passes.
- [x] Final E16 regression (folded from the closure): the E16 flows (summary section, diagnosis, plateau, diet break) make no `coach` model call and leave "Uso de IA" unchanged (fake `coach` client test).
**Host tests:** Deno tests for the accepted-plan diet-break recompute (on, off, not a cut, not the owner, other fields unchanged); pgTAP if SQL changes (`apply_coach_diet_break` after a toggle inside and outside week 4, RLS); ViewModel tests (option default off, toggle and recompute on the proposal and in "Tu plan", accepted plan with and without the break, no switch on the goal confirmation and review, R-59 navigation event, restoration); Robolectric (label, help sheet, "Tu plan" switch and line, week-4 label, the no-fit tap); a no-model regression test over the E16 repositories.
**Verification:**
- [x] Deno tests, lint + `testLocalDebugUnitTest` green (+ `npx supabase db reset && npx supabase db lint && npx supabase test db` if SQL changes); after the merge the `coach` function deploy and `deploy-supabase` green on `main` with data intact. No model call.
**Agent runs:** 8. About 13 files.
**Dependencies:** T-247 merged and deployed.
**Files likely touched:** `supabase/functions/coach/planRecompute.ts` (+ test), a migration only if `update_coach_plan_options` is status-guarded in SQL, `shared/…/data/repository/SupabaseCoachPlanRepository.kt`, `feature/trainer/{PlanProposalScreen,PlanProposalViewModel,CutOptions,TrainerPlanSection,TrainerViewModel,PlanWeeksSection,BlockTypesScreen,BlockTargetsScreen}.kt`, `app/{AppRoutes,AppNavHost}.kt` (or `AppRoot`), `strings.xml`, `planning/05-design-system.md` §10, `planning/04-business-rules.md` §8.9, tests
**Estimated scope:** M

---

### Checkpoint E16 — Nutrition follow-up: "Resumen semanal" and "Descanso de dieta", with the final E16 regression 👤 (7 items, leave and come back included) *(epic-planner, 2026-11-08; the only E16 checkpoint, user decision 2026-11-08; its approval releases T-249)*

After T-248 merges (the last implementation task), CI green, `deploy-supabase` green with data intact (T-246, T-247, and T-248 if it changed SQL). Everything here can be tried at once with the data each account already has; the branches that need two real weeks stay in "Pending natural-trigger feedback (E16)", outside this gate. The test account is discarded (user, 2026-11-09): everything is tried on the **main account**, and nothing is accepted there (accepting a cut plan would change real calories). Items 2 and 5 cannot be tried by hand; they stay covered by host tests, pgTAP and Deno tests. What to try on the phone (`python scripts/deliver-apk.py`):
- [x] 1. Main account, Trainer → "Resumen semanal" of the last week: the nutrition section shows your logged days, average kcal and protein against your goals, and your weight trend against the target rate, inside the same summary (no second screen, no new item in "Centro de avisos").
- [x] 2. ~~Test account (few diary days last week)~~ not tried by hand (no test account; host-test covered): the section says "Faltan registros: solo {n} de 7 días completos. Con menos de 5 no sacamos conclusiones sobre tus calorías." and shows no averages.
- [x] 3. Read the diagnosis card your data gives on each account (logging or calorie cause with one action, "Meseta", "Sin causa clara", or nothing when the weight is on target): one result at most, no medical wording, and "Uso de IA" did not change during E16.
- [x] 4. Main account, open a "Perder grasa" plan proposal and do **not** accept it: "Descanso de dieta" is off, and its `?` is one sentence (less hunger, fat is not lost faster).
- [x] 5. ~~Turn it on and accept~~ not tried by hand (nothing is accepted on the main account; host-test covered): "Tu plan" and the plan weeks show week 4 at maintenance calories; turn it off in "Tu plan": the week-4 break is gone and nothing else changes.
- [x] 6. In the block builder, with more types than days, the "no fit" note opens "Mi entrenamiento"; change the days and come back to the same step with your choices kept.
- [x] 7. Leave the app and come back (and after the system kills it) with the summary open, with the proposal changed, and with the builder after the round trip: the same tab, screen and draft are restored.
Out of scope here (natural triggers): the calorie change of a week listed with its reason, each diagnosis branch after two real weeks, and week 4 actually moving the Diario goals.

---

## T-249: E16 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E16 Definition of Done · **Flow:** PM closure step after Checkpoint E16 is approved (its approval releases this step; no checkpoint of its own). No developer agent, no size class; it cannot merge into a task because it waits for the checkpoint approval.
**Description:** Version bump (`versionName`/`versionCode`, minor per epic, proposed `1.7.0`), tag, retrospective `retrospectives/E16.md`, archive of the E16 log lines; `09-open-questions.md` records E16 Q1-Q3 with the experts' answers (`07` §4 "Nutrition follow-up (E16)" (c), user decision on one checkpoint); `01-scope.md` E16 row marked done. The "Pending natural-trigger feedback (E16)" items do not gate the closure; any still unchecked stay listed for real use.
**Acceptance criteria:**
- [x] T-246…T-248 (and any fix tasks) merged with green CI; `deploy-supabase` green after each migration.
- [x] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [x] `03`, `04`, `05`, `07` and the epic file are current; Checkpoint E16 approved by the user.
- [x] Version bumped; `tasks/todo.md` pointer updated through a PR.
**Verification:**
- [x] `git tag` lists the E16 tag.
**Dependencies:** Checkpoint E16 approved.
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `planning/01-scope.md`, `retrospectives/E16.md`
**Estimated scope:** closure step (PM)

---

# E15 · Real life and goal switching → `v1.x` (proposed `v1.6.0`)

Epic: [`planning/epics/E15-real-life-and-goal-switching.md`](../planning/epics/E15-real-life-and-goal-switching.md) (stories US-106…US-111). Plan: [tasks/plan.md](plan.md).
Rules: `07` §4 ("After a cut", phase calories, the calorie proposal), §7 (discomfort, swap offer, health note), §12.2 and §12.5 (breaks, the 14-day return rule), §12.3 (phases, consecutive cuts), §12.8 rows 2-4, 7 and 9; `04` §4.12, §4.14, §6, §8.5, §8.9, §8.10, §9.9, §9.10, §9.15-§9.17; E10 ("Centro de avisos"); E14 (US-101, US-102, US-103); ADR-40, ADR-73, ADR-88, ADR-89, ADR-98, ADR-99, ADR-101; `05` §8-§10. No language model anywhere in E15: no task calls the `coach` Edge Function with a model kind, "Uso de IA" never changes.
"Notes for later epics": the only item that targets E15 is the ADR-101 note that "Recuperar" is out of scope; it is an epic non-goal, not a task, so it stays in place. Carried Phase 2 follow-ups folded in here (the list above is left as is; the PM ticks them when the task merges): `recent_change` not scoped to `plan_id` → T-239; `coach_week_gate` returning `{ok:false}` on an empty diary and the UTC `today()` of `WeekSummaryViewModel` → T-242.

**Status (epic-planner, 2026-11-08): E15 Q1-Q5 resolved by the user the same day** (`tasks/plan.md` "Open questions"): Q1 "Bloque nuevo" is always on the Trainer during a block and replaces "Crear plan nuevo"; Q2 the Trainer gets a button to change the goal that opens "Mi entrenamiento" (the goal is still picked there, no goal picker in the Trainer); Q3 the graded return stays out of US-108; Q4 a paused week shows only "Semana en pausa", with no notice, and calorie proposals keep their own data gate; Q5 the user does not force time-based triggers: checkpoints hold only what can be tried right away, the rest goes to "Pending natural-trigger feedback (E15)" below. No question blocks a task. The breakdown PR still needs the user's OK. **Before T-239 (PM step, DONE 2026-11-08, `07` §12.9; the user delegated rule 4 to `fitness-scientist`):** `fitness-scientist` records in `07` the goal-switching evidence of 2026-11-08 the epic cites (immediate switch, no bridge and no minimum phase length, Carbon and MacroFactor; how the cut limit counts only consecutive cut blocks; that "weeks in this phase" carries no limit), with sources opened in its session (`CLAUDE.md`: no task uses a rule before it is in `07`).

**Order and sizing (every task pays 6-8 hand-offs; no S task):**
1. **T-239** (L, backend) and **T-240** (M, UI): US-110 + US-111. "Bloque nuevo" is the shared entry of the epic (US-107 reuses it), so it comes first. US-111 rides with US-110: it is the save of the same new block (counters, cut count) and one line in "Tu plan". Backend and UI split because the backend alone (early review, goal preview, save that switches plan and block, counter scoping, pgTAP and Deno tests) is about 12 files of heavy SQL/TypeScript. → **Checkpoint E15-A.**
2. **T-242** (L): US-106, one task: the backend change is heavy (plan weeks follow the training in every week-reading function) and the UI is two labels, so it is not split. No checkpoint item (time-based, Q5).
3. **T-243** (M): US-107 + US-109. Each alone is S (a Trainer notice computed from the last session; a "Centro de avisos" kind with its silencing switch); both are "coming back to the app after an absence" and touch the same notice and Trainer code. No checkpoint item (time-based, Q5).
4. **T-241** (M): US-108, one vertical slice (swap RPC + note trigger + Trainer offer + note row), last because its swap offer and level 1-3 check can be tried right away, so its checkpoint can also carry the final regression after T-242/T-243. → **Checkpoint E15-B** (final regression).
5. **T-244**: E15 closure (PM closure step).

Epic order changed (US-106 is listed first in the epic): goal switching goes first because "Bloque nuevo" is the action of the welcome-back notice (US-107) and the pause (US-106) changes how every later week is counted; the discomfort follow-up goes last so the last checkpoint (the final regression) sits right after the last implementation task with items the user can try at once (Q5).

### Pending natural-trigger feedback (E15; not gates, user 2026-11-08, E15 Q5)

The user does not force time-based triggers. These items are covered by host tests and pgTAP in their tasks; the user reports on them from the main account when each one happens in real use, and each report becomes a `fix(T-###)` task. They block no checkpoint and no closure.
- [ ] T-242: after a calendar week with no session, the weekly summary reads only "Semana en pausa" (no "Centro de avisos" item), the Trainer shows the block as paused and the end date one week later; calories and macros do not change.
- [ ] T-242: the next finished session continues the block from the paused week.
- [ ] T-243: after 14 or more days without a session, the Trainer shows "Bienvenido de nuevo" with one line and "Bloque nuevo"; the first session's loads are not raised; it is gone after a finished session or a saved new block; a shorter gap shows nothing.
- [ ] T-243: when the block's last plan week has ended, "Centro de avisos" lists "Tu bloque ha terminado: revísalo" with the count; tapping opens the review; it stays until the next block is saved; silenced in Preferencias → "Avisos", it is not listed and the Trainer card still shows the review.
- [ ] T-241: a level 4-5 discomfort still open after 14 days (or logged at level 4-5 in 3 sessions) shows the note once; closed, it stays closed.
- [ ] T-241 (deferred from Checkpoint E15-B items 1-4, user has no injury): in real use, a level 1-3 discomfort raises no load; a level 4-5 one shows the swap offer, "Cambiar" lists same-movement exercises in the equipment and swaps the block routines, "Ahora no" hides the offer, "Ya no me molesta" removes the offer.
- [ ] T-239: after a new block with a new goal, the first calorie proposal comes only after its own two weeks and data gate (the band, the 14-day clock and the "Ahora no" count started from zero).

---

## T-239: "Bloque nuevo" and goal switching: backend (US-110, US-111 backend) · L

**Story:** US-110, US-111 · **Flow:** backend: planner → backend-developer (migrations, RLS, pgTAP, Edge Function deterministic kind) → kotlin-core-developer (DTOs, repository, use cases) → pr-reviewer → pr-creator (`06` §2.2; L, run as slices) · **Size:** L
**Scenarios:** US-110 Nothing changes until I save the new block; Figures are recomputed from the new goal; No forced bridge; The same entry serves any life change (backend side); The confirmation shows the calorie change (the `{a}`, `{b}` and `{fase}` figures) · US-111 Proposal counters restart; The deficit-week count restarts after 4 consecutive non-deficit weeks; A short break from the cut only pauses the deficit-week count; Weeks in the current phase (the `n`).
**Description:** (1) **Early review ("Bloque nuevo", `07` §12.8 rows 2-4):** a call that opens, for the accepted plan with a saved block and before its last week ends, the review facts the next-block builder reads (T-233 inputs frozen at that day, `04` §8.10; the T-235 pre-fill `04` §9.17), without ending the current block; the current block, its weeks and the plan keep running until the new block is saved (no change applies on open). Repeated opens before saving do not stack reviews (planner defines the rule; one open early review per plan). (2) **Goal preview (E15 Q2, user 2026-11-08):** the goal is still picked in "Mi entrenamiento" (reached from a Trainer button, T-240; saving the intention there changes nothing in the plan, `04` §4.13); US-110 "I choose a different goal" is that pick. When the saved intention differs from the plan's, a deterministic preview (no model, like `plan_phase_change` and `plan_recompute`, ADR-98) returns the current and new calories and the new phase for the confirmation line (`{a}` = current `nutrition_goals` kcal, `{b}` = the new plan's target, `{fase}` = the new phase, labels from `PhaseText`); it stores a **proposed** plan with the new intention's first phase (`04` §8.9) as the vehicle of the new block (supersedes an older proposal); `nutrition_goals` and the accepted plan are untouched. (3) **Save:** saving the new block through `save_built_coach_block` (T-235 path) ends the previous block, accepts the proposed plan when there is one (calories, rate, protein, outcomes from the new goal, `04` §8.5 acceptance; a cut → gain starts from the measured maintenance plus the gain rate in one step, `07` §4 "After a cut"), and starts week 1 that day; with no goal change (days or session length changed) the plan is kept and only the block is replaced. No maintenance block is required or proposed between phases (non-goal). (4) **Clean restart (US-111):** after the save, the calorie-proposal band, the 14-day clock and the "Ahora no" count read only the new plan and block (`04` §8.5: `declined_twice` and the band already read since `block_start`; `recent_change` is scoped to the plan, folding in the carried follow-up "recent_change is not scoped to plan_id"); `coach_phase_history.deficit_weeks_done` restarts only after 4 or more consecutive counted non-deficit weeks; shorter stretches only pause it, like a diet-break week (`07` §12.9 rule 4; check the T-187 "latest run" rule and fix it if it does not hold across a goal switch). (5) **Weeks in phase:** one read returns `n` = plan weeks done in the current phase across consecutive blocks of the same phase (planner defines it with `04` §9.9 plan weeks, so paused weeks from T-242 will not count; until T-242 calendar plan weeks); no warning, no limit (`07` §12.3, non-goal). (6) Docs: `04` §8.10/§9.15-§9.17 (early review, goal switch, save), `03` (any new column or RPC), and `07` only through `fitness-scientist` (PM step above). (7) **"Crear plan nuevo" retired during a block (E15 Q1):** a new nutrition plan for a plan with a saved block comes only through "Bloque nuevo"; the planner removes or guards the server path the old button used for that case (without a block, "Crear plan" stays).
**Acceptance criteria:**
- [x] With an accepted plan and a saved block, opening "Bloque nuevo" before the block ends returns the pre-fill facts and changes nothing in the current block, its weeks, `nutrition_goals` or the accepted plan.
- [x] With a different intention, the preview returns current and new kcal and the new phase, stores one proposed plan, and nothing else changes until the new block is saved.
- [x] Saving the new block ends the previous block, accepts the proposed plan (calories, rate, protein, outcomes from the new goal; a cut → gain = measured maintenance + gain rate in one step) and starts week 1 that day; without a goal change the plan is kept.
- [x] After the save, the band, the 14-day clock and the "Ahora no" count start from zero; a cut → gain → cut sequence restarts the deficit-week count at the new cut; no maintenance block is required.
- [x] Weeks in phase returns the right `n` for one block, two consecutive blocks of one phase, and after a phase switch.
- [x] RLS: user B can neither open nor save user A's early review or preview (pgTAP); every new table or column cascades on account deletion. No model call ("Uso de IA" unchanged).
**Host tests:** pgTAP `coach_block_new.test.sql` (early review mid-block, no change on open, repeated opens, preview with and without a goal change, save ending the old block and accepting the plan, counters restarting incl. `recent_change` per plan, cut → gain → cut count, weeks in phase, RLS, cascade, a block and plan created with the E13/E14 functions before the migration); Deno unit tests for the deterministic preview (cut → gain one step, gain → cut rate and cap, maintenance); repository and use-case unit tests (DTO decoding, offline error).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, Deno tests, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (the current block, plan and `nutrition_goals` unchanged until the user saves a new block). No model call.
**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-reviewer, pr-creator = 5. About 12 files.
**Dependencies:** the breakdown approved by the user; `fitness-scientist` has recorded the goal-switching evidence in `07`.
**Files likely touched:** `supabase/migrations/<ts>_coach_block_new.sql`, `supabase/tests/coach_block_new.test.sql`, `supabase/tests/delete_account_cascade.test.sql`, `supabase/functions/coach/` (deterministic preview kind and its test), `shared/…/data/repository/SupabaseCoachReviewRepository.kt`, `shared/…/data/remote/*Dto.kt`, `shared/…/domain/usecase/{OpenNewBlock,PreviewGoalSwitch}UseCase.kt` (names by the planner), `planning/03-data-model.md`, `planning/04-business-rules.md` §8.5, §8.10, §9.15-§9.17, tests
**Estimated scope:** L

---

## T-245: fix(T-245): a plan proposal blocked by unconfirmed values says which value and lets the user fix it (bug, Checkpoint E15-A feedback, user 2026-11-08) · M

**Report (user, test account, creating a nutrition plan):** the proposal shows "Hay valores sin confirmar. Revísalos y vuelve a crear el plan para poder aceptar." The user cannot see which value is unconfirmed nor where to fix it: "this is a terrible UX and must be fixed".
**Where:** string `plan_proposal_reask_note` (`strings.xml`), `PlanProposalScreen`/`PlanProposalUiState`, `05` §8 R-59 (new, written with this entry), `05` §10 glossary row "A coach value not yet confirmed".
**Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → (pr-reviewer ∥ ux-reviewer) → pr-creator. The planner first finds why a fresh plan in the test account has unconfirmed values (a data problem or a missing path) and fixes the cause, not only the copy.
**Acceptance criteria:**
- [x] The proposal names each unconfirmed value (for example the weight or the activity level) and each has a tap target that opens the editor of that value (R-55); after fixing it the user comes back and can accept without re-creating the plan from scratch.
- [x] No state of this screen blocks acceptance without a visible fix (R-59); sweep the other blocking notes of the Trainer and Diario goals.
- [x] The test account's case is reproduced in a host test; lint + `testLocalDebugUnitTest` green.
**Dependencies:** Checkpoint E15-A feedback released by the user. Not started: a fresh session is needed (`/clear`).
**Estimated scope:** M

---

## T-240: "Bloque nuevo" in the app: the entry, the goal confirmation, the pre-filled builder and weeks in phase (US-110, US-111 UI) · M

**Story:** US-110, US-111 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer (ViewModels) → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** every US-110 scenario (UI side) and US-111 Weeks in the current phase; plus "leave and come back with the confirmation or the builder open".
**Description — templates and patterns (ux-reviewer finalises in the plan pass):** **Entry (E15 Q1):** "Bloque nuevo" always on the Trainer root while an accepted plan has a saved block, in the place of "Crear plan nuevo" (the existing Trainer card pattern, R-03/R-12; glossary §10 row "Bloque nuevo"). **Goal button (E15 Q2, user 2026-11-08):** a Trainer action to change the goal (label by ux-reviewer, glossary) that opens the existing "Mi entrenamiento" editor (Perfil, `05` §8 "Decided"; no goal picker inside the Trainer, R-55 same value → same editor); after "Guardar" there and back on the Trainer, a different intention from the plan's makes "Bloque nuevo" show the goal confirmation below. In US-110 "I choose a different goal" is this pick in "Mi entrenamiento". **Goal confirmation** (only when the intention differs from the plan's): not a dialog (R-08); an `AppScreen` step or the "Antes de empezar" footer line (ux-reviewer picks; pinned `PrimaryButton`, R-09) with the exact line "Tus calorías pasan de {a} a {b} kcal: {fase}. El bloque actual sigue hasta que guardes el nuevo." (numbers via `AppNumberFormat`, phase label via `PhaseText`/`PhaseRow`, R-44), then the builder. **Builder:** the T-235 next-block flow (`BlockTargetsScreen` "Objetivo por grupo", then `BlockBuilderScreen`, `05` §6.2 "Block builder") pre-filled from the current block; a days-per-week or session-length change opens it with the new preferences (the builder's split options for the new days, `07` §12.8 rows 2-3); "Guardar bloque" saves through T-239 and returns to the Trainer root (R-13). Back from the builder before saving leaves the current block and calories as they were. **Weeks in phase:** "Llevas {n} semanas en esta fase" as one fact line in "Tu plan" next to the phase line (`trainer_plan_phase`, `05` §9.0b), plural forms, no warning, no colour (R-41, R-57). **Migrate with it (E15 Q1):** "Crear plan nuevo" is retired during a block in every place it shows (strings, tests, glossary, `05` §6.2 Trainer root), so "one concept, one label" holds (R-35); "Crear plan" without a plan is unchanged; the review footer button of T-234/T-235 ("Crear el siguiente bloque") stays the end-of-block path.
**Acceptance criteria:**
- [x] During a block the Trainer shows "Bloque nuevo" and no "Crear plan nuevo"; the goal button opens "Mi entrenamiento"; after a different goal is saved there, "Bloque nuevo" shows the confirmation line with the real figures; nothing changes in the Trainer, Diario goals or the block until "Guardar bloque".
- [x] After saving, "Tu plan" shows the new phase, calories, protein and outcomes, and no maintenance block is offered; going back before saving leaves everything as it was.
- [x] With only the days per week changed, "Bloque nuevo" opens the pre-filled builder with the split options for the new days.
- [x] "Tu plan" shows "Llevas {n} semanas en esta fase" (singular and plural) with no warning.
- [x] The confirmation and the builder with partial choices survive background and process death (ADR-40); offline disables "Bloque nuevo" with the standard hint (R-30).
- [x] `check-ux-conventions.sh` passes; the glossary has "Bloque nuevo" and the weeks-in-phase line.
**Host tests:** ViewModel tests (entry visibility, goal button navigation event, "Crear plan nuevo" absent during a block, preview success/error/offline, back without saving, save, restoration from the same `SavedStateHandle`); Robolectric (entry, confirmation copy with and without a goal change, builder opened with the new days, weeks-in-phase line singular/plural); `StateRestorationTester`.
**Verification:**
- [x] lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green. No model call.
**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-reviewer ∥ ux-reviewer (diff), pr-creator = 7. About 12 files.
**Dependencies:** T-239 merged and deployed (`deploy-supabase` green).
**Files likely touched:** `feature/trainer/{TrainerScreen,TrainerViewModel,TrainerUiState,TrainerPlanSection,PhaseText}.kt`, `feature/trainer/{BlockTargetsScreen,BlockBuilderRoot,BlockBuilderViewModel,BlockBuilderUiState}.kt`, `feature/trainer/BeforeStartScreen.kt` (if the confirmation lives there), `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/05-design-system.md` (§6.2 Trainer root, §10 glossary), tests
**Estimated scope:** M

---

### Checkpoint E15-A — "Bloque nuevo" and switching goal 👤 (6 items, leave and come back included) *(epic-planner, 2026-11-08, after the user's Q1-Q5 answers; needs the user's OK)*

After T-240 merges, CI green, `deploy-supabase` green with data intact (T-239). Everything here can be tried at once; nothing waits for time to pass (Q5). What to try on the phone (`python scripts/deliver-apk.py`):
- [x] 1. On Trainer, during your block: "Bloque nuevo" is there and "Crear plan nuevo" is gone; the goal button opens "Mi entrenamiento".
- [x] 2. Change your goal there (for example "Perder grasa" → "Ganar musculatura"), save, back on Trainer tap "Bloque nuevo": you read "Tus calorías pasan de {a} a {b} kcal: {fase}. El bloque actual sigue hasta que guardes el nuevo."; go back without saving and check the block, calories and Diario goals did not change.
- [x] 3. Do it again and save the new block: "Tu plan" shows the new phase, calories, protein and outcomes; from a cut to a gain the calories jump in one step, and no maintenance block is asked or offered.
- [x] 4. Change only your days per week, tap "Bloque nuevo": the builder opens pre-filled with the split options for the new days.
- [x] 5. "Tu plan" shows "Llevas {n} semanas en esta fase", with no warning.
- [x] 6. Leave the app and come back (and after the system kills it) with the goal confirmation or the builder half done: the same tab, screen and draft are restored.
Out of scope here: the discomfort follow-up (Checkpoint E15-B) and every time-based item ("Pending natural-trigger feedback (E15)").

---

## T-242: A missed week pauses the block (US-106) · L

**Story:** US-106 · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** L (backend first, then the two UI labels; the planner may run it as two slices)
**Scenarios:** US-106 A week with no sessions does not count; The block resumes at the next session; A pause does not change my nutrition plan; The pause survives leaving the app.
**Description:** `04` §9.9 "Amended 2026-11-01" becomes the built rule (it was never implemented: the T-189 of the old backlog was discarded): plan week `n` is the `n`-th calendar week (ISO, Monday-Sunday) with a completed session since `block_start`, a calendar week with no completed session does not count, and the current week is the next plan week. **Backend:** one week-mapping helper used by every reader that today counts calendar weeks from `block_start`: `coach_plan_weeks`, `coach_plan_week`, the `coach_link_plan_session` trigger (links to the week the session falls in under the new mapping), `coach_block_end` (the end moves one week later per paused week), the deload evaluation and scheduling (`04` §9.10: paused weeks are not training weeks), `apply_coach_diet_break` (week 4 moves with it), `coach_week_summary` (a paused week has no plan week), the review (`04` §8.10, §9.16) and the T-239 weeks in phase. Existing blocks: the migration re-derives the week of already linked sessions so the user's current block keeps its done sessions (planner defines it; risk in `tasks/plan.md`). **Nutrition (E15 Q4, user 2026-11-08):** calories, macros and the diet break stay as they were in a paused week; training pauses, nutrition does not: `evaluate_coach_calorie_change` keeps its own data gate and gets no pause reason (a week with no diary or weigh-ins already makes no proposal, which is the Gherkin's "no calorie proposal is generated from that empty week"); fold in the carried follow-up "`coach_week_gate` should return `{ok:false}` itself on an empty diary". **UI — templates and patterns:** the weekly summary (`WeekSummaryScreen`, T-231) for a paused week shows only "Semana en pausa" (no other lines) and adds **no** "Centro de avisos" item (E15 Q4); the task rewrites `04` §6.7, which today says "an empty or paused week has no summary and no item", to: a paused week has a summary reading only "Semana en pausa" and no item; the Trainer's block entry and plan weeks (`PlanWeeksSection`) show the block as paused ("Semana en pausa" as the week's state, R-41 text not colour alone) and the moved end date (`AppDateFormat`, R-44); no new component. Fold in the carried follow-up: `WeekSummaryViewModel.today()` uses the local-zone date like `LoadReminders`.
**Acceptance criteria:**
- [x] A calendar week with no completed session does not count as a block week: the plan weeks, the end date (one week later per paused week), the deload week, the diet-break week and the review move with it (pgTAP on seeded blocks with one and two paused weeks, and a paused week at week 4).
- [x] The next completed session continues from the paused week and links to it.
- [x] Calories, macros and the diet break are unchanged by a pause; the calorie proposal keeps its own data gate (no pause reason added).
- [x] The weekly summary of a paused week reads only "Semana en pausa" and lists no "Centro de avisos" item; `04` §6.7 is rewritten to match; the Trainer shows the block as paused and the moved end date; both survive background and process death (ADR-40).
- [x] The user's existing block keeps its done sessions after the migration (pgTAP with a block created by the E13/E14 functions before it).
**Host tests:** pgTAP `coach_plan_weeks_pause.test.sql` (mapping with 0, 1 and 2 paused weeks, link trigger, block end, deload at a moved week 4, diet break, summary of a paused week, review date, weeks in phase, existing-block migration, RLS); ViewModel tests (paused state, summary label, local date, restoration); Robolectric (Trainer paused state and end date, summary "Semana en pausa").
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (current plan, block, linked sessions and `nutrition_goals` unchanged except the week numbering). No model call.
**Agent runs:** 8. About 14 files.
**Dependencies:** Checkpoint E15-A approved (it builds on the T-239 save and weeks in phase). User check: none at a checkpoint (time-based, Q5); its items are in "Pending natural-trigger feedback (E15)".
**Files likely touched:** `supabase/migrations/<ts>_coach_plan_weeks_pause.sql`, `supabase/tests/coach_plan_weeks_pause.test.sql` (and the existing week tests it changes), `shared/…/domain/model/{PlanWeek,WeekSummary}.kt`, `shared/…/data/repository/SupabaseCoachSummaryRepository.kt`, `feature/trainer/{PlanWeeksSection,TrainerPlanSection,TrainerViewModel,TrainerUiState,WeekSummaryScreen,WeekSummaryViewModel,WeekSummaryText}.kt`, `strings.xml`, `planning/04-business-rules.md` §6.7, §8.9, §9.9, §9.10, `planning/03-data-model.md`, tests
**Estimated scope:** L

---

## T-243: Welcome back after a break and the block-end notice in "Centro de avisos" (US-107, US-109) · M

**Story:** US-107, US-109 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer (backend-developer only if the notice state needs SQL) → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-107 The notice after 14 days; Loads follow the existing return rule; No notice after a short gap; The notice goes away once handled · US-109 The notice appears when the block ends; Opening the notice goes to the review; The notice stays until the next block is saved; The notice leaves once I start the next block; The notice can be silenced.
**Description:** **Welcome back (US-107, `07` §12.2, §12.5, §12.8 row 9):** shown on the Trainer root when the latest completed session is 14 or more days old (6 days: none; the same 14-day threshold and `Clock` as `04` §4.14 "After 14 or more days"); it goes away when a session is finished or a new block is saved after that gap (planner defines the condition from the last completed session and the block start). Content: the title "Bienvenido de nuevo", one fact line saying the first loads are lighter (exact copy by ux-reviewer, glossary; R-57: one line, no advice block), and the action "Bloque nuevo" (the T-240 entry). The loads already follow the return rule on the server (T-227, `04` §4.14); this task adds an end-to-end test that the first session after the gap keeps the last load and one failed session drops 5 %. **Block-end notice (US-109, ADR-73, `04` §6):** a "Centro de avisos" kind `block_end` computed on open: listed (and counted in the bell badge) from the day after the block's last plan week (`coach_block_end`, which T-242 moved with the pauses) until the next block is saved, whether or not the review was opened; text "Tu bloque ha terminado: revísalo"; tapping opens the end-of-block review (T-234). Silenced by its own switch in Preferencias → "Avisos" (`notification_settings`, a missing row = on, `04` §6.3); the Trainer card "Tu bloque ha terminado: revísalo" (T-234) stays when silenced. **Templates and patterns:** welcome back = the existing Trainer card pattern (`AppCard` with title, one line and a text action; not `CoachProposalCard`, it is not a proposal); the notice = the existing notice row of `NotificationCenterScreen` like the T-231 weekly summary item; the switch = the existing live-in-editor switch row of Preferencias → "Avisos" (R-42, saved with its "Guardar"). Docs: `04` §6 new §6.9 (block-end notice) and the welcome-back rule line.
**Acceptance criteria:**
- [x] With the last completed session 14+ days old, the Trainer shows "Bienvenido de nuevo", the one line and "Bloque nuevo"; at 6 days nothing; after a finished session or a saved new block it is gone.
- [x] The first session after a 14+ day gap keeps the last loads and one failed session drops 5 % (end-to-end test of the built rule).
- [x] After the block's last plan week the "Centro de avisos" lists "Tu bloque ha terminado: revísalo" and the bell counts it; tapping opens the review; it stays across days and reopens until the next block is saved, then leaves.
- [x] Turning the block-end switch off in Preferencias → "Avisos" removes the item and its count; the Trainer card still shows the review.
- [x] The notice center and the Trainer with the welcome-back card survive background and process death (ADR-40).
**Host tests:** unit tests (welcome-back condition at 6/13/14 days, after a session, after a saved block; block-end notice listing, count, silencing, clearing on save); ViewModel tests (Trainer card, notice tap navigation, restoration); Robolectric (card copy and action, notice row, Avisos switch); pgTAP only if SQL changes.
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green (+ pgTAP and `deploy-supabase` if SQL changes). No model call.
**Agent runs:** 7. About 11 files.
**Dependencies:** T-242 merged (the block end moves with the pauses; welcome back reads the same weeks). User check: none at a checkpoint (time-based, Q5); its items are in "Pending natural-trigger feedback (E15)".
**Files likely touched:** `shared/…/domain/usecase/ComputeNoticesUseCase.kt` (or the `LoadReminders` path), `shared/…/domain/model/Notice*.kt`, `feature/notifications/{NotificationCenterScreen,NotificationCenterViewModel,NotificationCenterUiState}.kt`, `feature/trainer/{TrainerScreen,TrainerViewModel,TrainerUiState}.kt`, the Preferencias → "Avisos" editor, `strings.xml`, `planning/04-business-rules.md` §6, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-241: Discomfort follow-up: the swap offer for a severe discomfort and the 14-day note (US-108) · M

**Story:** US-108 · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M (the graded return is out of scope, E15 Q3)
**Scenarios:** US-108 A mild discomfort stops load increases for a while; A severe discomfort offers a swap; A discomfort that lasts 14 days shows a note.
**Description:** Levels 1-3 and the "no suggestion" of levels 4-5 are built (`04` §4.12, §4.14, T-147/T-152/T-159/T-227); this task verifies them end to end and adds what the user sees for a severe one. Last implementation task of E15 (order: its checkpoint can be tried at once and carries the final regression). **Backend:** (1) **Swap offer (`07` §7 (b), §12.8 row 7):** for an unresolved level 4-5 discomfort on an exercise of the accepted block, the candidates are exercises of the same movement pattern, inside the user's current equipment (`coach_user_equipment()`, T-209), never one with its own unresolved level 4-5 discomfort, favourites first then the similarity order (`04` §9.7b); an RPC applies the swap on the user's tap to every routine of the block folder holding the exercise, for the rest of the block, keeping sets, range, rest and RPE; the new exercise's planned weight is the user's own last working weight, else empty (`07` §12.8 "Loads"); "Ahora no" stores the decline so the offer is not repeated for that discomfort (planner defines the state). Outside a block nothing is offered. (2) **Health note (`07` §7 "Health note", ADR-89):** shown once per discomfort when a level 4-5 discomfort is still unresolved 14 days after it was logged, or was logged at level 4-5 on the same exercise in 3 sessions; dismissible, never blocking; the dismissal is stored per discomfort (so it does not come back on another device). (3) **No graded return (E15 Q3, user 2026-11-08):** the swap lasts for the rest of the block and the next block is the user's choice in the builder; the 3-step return of `07` §7 stays documented and unbuilt (the planner notes it in `04` §4.12, which today says the exercise "follows the 3-step graded return"). **UI — templates and patterns:** the swap offer on the Trainer root as the existing `CoachProposalCard` (title, reason line "Esta molestia es fuerte. ¿Cambiamos {ejercicio} por otro del mismo movimiento hasta que acabe el bloque?", actions "Cambiar" / "Ahora no"; exercise names through `ExerciseNameResolver`, R-54); "Cambiar" opens an `AppPickerSheet` of `ExerciseRow`s (tap applies and closes with the Confirm haptic, R-12, R-17; empty state when no candidate exists); the applied swap is visible in the block routine and in the live session (ADR-50); the note as the existing discomfort status row (`ExerciseStatusRow`, R-51) supporting line or an `InlineNote` on the Trainer (ux-reviewer places it; R-52: the first allowed health line, added to `HealthDisclaimerSweepTest`'s allow-list with its trigger) with the `07` §7 text "Esta molestia sigue ahí desde hace 14 días. Seguimos entrenando el resto sin cargar esta zona. Puede merecer la pena que te lo revise un profesional." and a close action; glossary rows "Cambiar" (swap) if missing.
**Acceptance criteria:**
- [x] A level 1-3 discomfort keeps the exercise in the routine and writes no load increase for 7 days (end-to-end check of the built rule, inside and outside a block).
- [x] A level 4-5 discomfort shows no load suggestion and the Trainer offers the swap; the candidate list holds only same-pattern exercises inside the equipment and without a severe discomfort; nothing changes until the user picks one; the pick changes every block routine with that exercise for the rest of the block; "Ahora no" hides the offer for that discomfort.
- [x] A level 4-5 discomfort unresolved for 14 days (or logged at level 4-5 in 3 sessions) shows the note once; closing it keeps it closed on every device; "Ya no me molesta" removes the offer and the note.
- [x] The Trainer with the offer and the picker open survives background and process death (ADR-40); offline disables "Cambiar" with the standard hint.
- [x] RLS on every new table or column (pgTAP); `HealthDisclaimerSweepTest` and `check-ux-conventions.sh` pass.
- [x] Checkpoint E15-A feedback (user, 2026-11-08; `05` R-46 now covers action buttons and step titles): a `?` (`HelpButton`, Info sheet in Spanish for a user with no training knowledge) on the "Objetivo por grupo" step (what the groups and "Priorizar" / "Mantener" do) and on the "Hacer descarga la próxima semana" button (what a deload week is, when to use it and when not); sweep every Trainer screen for other action buttons, step titles and sections with training jargon and give each a `?` or record it in the task PR.
- [x] Final E15 regression (folded from the closure): the E15 flows (pause, welcome back, block-end notice, "Bloque nuevo", goal switch, swap, note) make no `coach` model call and leave "Uso de IA" unchanged.
**Host tests:** pgTAP `coach_discomfort_followup.test.sql` (candidates by pattern, equipment and flagged exercises, swap across routines, outside a block, decline, note trigger at 13/14 days and at 3 sessions, dismissal, "Ya no me molesta", RLS, cascade); ViewModel tests (offer, picker, apply, decline, note dismiss, error, offline, restoration); Robolectric (card copy, picker states, note row); a no-model regression test over the E15 repositories (fake `coach` client asserts zero model calls).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db`, lint + `testLocalDebugUnitTest` green; `deploy-supabase` green on `main` after the merge with data intact (block routines unchanged until a swap is tapped). No model call.
**Agent runs:** 8. About 12 files.
**Dependencies:** T-243 merged (order).
**Files likely touched:** `supabase/migrations/<ts>_coach_discomfort_followup.sql`, `supabase/tests/coach_discomfort_followup.test.sql`, `shared/…/data/repository/SupabaseCoachChangesRepository.kt` (or a discomfort repository), `shared/…/domain/usecase/{SwapFlaggedExercise,DismissDiscomfortNote}UseCase.kt` (names by the planner), `feature/trainer/{TrainerScreen,TrainerViewModel,TrainerUiState}.kt`, the discomfort status row in `feature/workout`, `HealthDisclaimerSweepTest.kt`, `strings.xml`, `planning/04-business-rules.md` §4.12, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

### Checkpoint E15-B — Training around a discomfort, and the final E15 regression 👤 (6 items, leave and come back included) *(epic-planner, 2026-11-08, after the user's Q1-Q5 answers; needs the user's OK; its approval releases T-244)*


**Approved by the user on 2026-11-08, without testing:** the user trusts the implementation and has no discomfort to log, so items 1-4 and the discomfort part of 6 are deferred to real use (listed below under "Pending natural-trigger feedback (E15)"); each report becomes a `fix(T-###)` task. Releases T-244.
After T-241 merges (the last implementation task), CI green, `deploy-supabase` green with data intact (T-241, and T-242 earlier). Everything here can be tried at once; nothing waits for time to pass (Q5). What to try on the phone (`python scripts/deliver-apk.py`):
- [x] 1. Log a discomfort of level 1-3 on a block exercise and finish the session meeting the top of its range: the exercise stays in the routine and its load is not raised.
- [x] 2. Log a discomfort of level 4-5 on a block exercise: no load suggestion for it, and Trainer offers "Esta molestia es fuerte. ¿Cambiamos {ejercicio} por otro del mismo movimiento hasta que acabe el bloque?" with "Cambiar" / "Ahora no".
- [x] 3. Tap "Cambiar": the list holds only exercises of the same movement inside your equipment; nothing changes until you pick one, then the block routines and the next live session show the new exercise. On another level 4-5 discomfort, "Ahora no" hides the offer.
- [x] 4. Tap "Ya no me molesta" on a severe discomfort with the offer open: the offer goes away.
- [x] 5. Final regression: "Uso de IA" did not change during E15, and the Checkpoint E15-A flows ("Bloque nuevo", the goal button, the calorie line, weeks in phase) still work after the pause change (T-242).
- [x] 6. Leave the app and come back (and after the system kills it) with the swap picker open, on Trainer and on "Centro de avisos": the same tab, screen and state are restored.
Out of scope here: every time-based item (paused week, welcome back, block-end notice, the 14-day note, counters restarting), which the user reports from real use ("Pending natural-trigger feedback (E15)").

---

## T-244: E15 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E15 Definition of Done · **Flow:** PM closure step after Checkpoint E15-B is approved (its approval releases this step; no checkpoint of its own). No developer agent, no size class; it cannot merge into a task because it waits for the checkpoint approval.
**Description:** Version bump (`versionName`/`versionCode`, minor per epic, proposed `1.6.0`), tag, retrospective `retrospectives/E15.md`, archive of the E15 log lines; `09-open-questions.md` records E15 Q1-Q5 with their answers; `01-scope.md` E15 row marked done. The "Pending natural-trigger feedback (E15)" items do not gate the closure (Q5); any still unchecked stay listed for real use, and each report becomes a `fix(T-###)` task.
**Acceptance criteria:**
- [x] T-239…T-243 (and any fix tasks) merged with green CI; `deploy-supabase` green after each migration.
- [x] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [x] `03`, `04`, `05`, `07` and the epic file are current; Checkpoints E15-A and E15-B approved by the user.
- [x] Version bumped; `tasks/todo.md` pointer updated through a PR.
**Verification:**
- [x] `git tag` lists the E15 tag.
**Dependencies:** Checkpoint E15-B approved.
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `planning/01-scope.md`, `retrospectives/E15.md`
**Estimated scope:** closure step (PM)
