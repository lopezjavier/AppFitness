# Tasks — Phase 3

Plan of the current epic: [tasks/plan.md](plan.md) (E15) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md). Phase 2 history (frozen, with the full per-task log T-134…T-238): [tasks/phase2/](phase2/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-245 (T-239…T-244 reserved by the E15 breakdown)

**Next:** E15 is closed (`v1.6.0`, tag `v1.6.0`, `retrospectives/E15.md`). No task is active. The user tests with real data and reports errors; each report becomes the next `fix(T-###)`. The "Pending natural-trigger feedback (E15)" items and the follow-ups of T-240, T-241, T-242 and T-245 listed in the E15 breakdown stay open for real use. Next epic: E16, broken down by `epic-planner` in a fresh session.

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

E15 (epic-planner, 2026-11-08, after the user's Q1-Q5 answers; needs the user's OK): **Checkpoint E15-A** after T-240 ("Bloque nuevo" and switching goal; 6 items) and **Checkpoint E15-B** after T-241, the last implementation task ("Training around a discomfort", plus the final E15 regression; 6 items; its approval releases the closure T-244). **Only items the user can try right away are checkpoint items (user, 2026-11-08, E15 Q5):** paused weeks, welcome back, the block-end notice and the 14-day note fire only after real time passes, so they are in "Pending natural-trigger feedback (E15)" under the E15 section, not gates. Details under the E15 section. Phase 2 checkpoints are in tasks/phase2/todo.md.

## Notes for later epics

- **Out of scope (user, 2026-11-08, ADR-101):** "Recuperar" / returning after a break (regain phase) is out of scope for the app; the 14-day return rule stays as the only break rule.
- **R-59 sweep leftover (T-245):** `block_types_no_fit` (`BlockTypesScreen`, `BlockTargetsScreen`) names the three levers but has no tap to "Mi entrenamiento"; needs a route through the block builder slots and `AppRoot`. Owning task: to be added by the PM at the next epic planning.
- **UI review epic (user, 2026-11-08):** "Lo que el entrenador sabe de ti" (`free_text_*`, `trainer_free_text_row`) and the "Para conocerte" help text say the coach reads those notes when preparing the plan and blocks, but the coach is deterministic (ADR-97…99; `plan_*` kinds have `deterministic: true`, `report` and `chat` are unavailable, and no coach code reads the free text), so the "Enviar al entrenador" switches control nothing. The user will raise it in the detailed UI review epic (remove, hide until a model feature exists, or fix the copy).
- **E13 backlog (user request, 2026-10-30): consumed.** Items 1 → T-179, 2 → T-181…T-184, 3 and 4 → T-205/T-206. Nothing from it is open.

## Reminders

- Delete `design/reported/live-session-cardio-row-layout.png` once the user confirms the cardio fix (T-238).

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
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db`, lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (current plan, block, linked sessions and `nutrition_goals` unchanged except the week numbering). No model call.
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
