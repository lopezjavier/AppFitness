# Tasks — Phase 3

Plan of the current epic: [tasks/plan.md](plan.md) (E19) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md). Phase 2 history (frozen, with the full per-task log T-134…T-238): [tasks/phase2/](phase2/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-287 (T-286, T-287 = split of T-277 by layer, user 2026-11-11; T-277…T-285 reserved by the E19 breakdown, epic-planner 2026-11-11; T-276 = Checkpoint E18-C feedback fix, user 2026-11-11; T-274, T-275 = US-134, US-135, added to E18 by ADR-106, epic-planner 2026-11-10; T-273 = revert of the unused-food cleanup, user decision 2026-11-10; T-271, T-272 = US-132, US-133, added to E18 by ADR-105, epic-planner 2026-11-10; T-268, T-269, T-270 = Checkpoint E18 feedback fixes; T-259…T-267 reserved by the E18 breakdown; T-257…T-258 = ADR-104 hard energy check, Checkpoint E17 feedback; T-251…T-256 reserved by the E17 breakdown; T-246…T-249 by E16; T-239…T-244 by E15; T-250 = user-reported fix below)

**Done in the T-246 branch (user report, 2026-11-08; the user ordered it into the current branch, so it ships in T-246's PR as a separate `fix(T-250)` commit):** `fix(T-250)`: the `food-photo` dish prompt (`supabase/functions/food-photo/prompts.ts`) never says whether the per-100 g values are for the food as served; a 400 g cooked plate came back with raw-food kcal/100 g. Fix: the dish prompt states that values and `estimated_grams` describe the food as shown in the photo (cooked when cooked), with a handler/prompt test and a redeploy of `food-photo`. Backend only, no model-call change elsewhere.

**Next (2026-11-11, E19, latest):** T-278 merged (PR #654, CI green; backend files only: `supabase/seed-review.sql`, `supabase/review/seed-review.check.sql`, `scripts/review-emulator.py`; no migration, no deploy to wait for). The dataset checks run by `review-emulator.py --check` and the supabase-ci "Rich dataset" step, not `supabase test db` (`\ir` does not resolve there). The emulator step was not run yet (no emulator in the agent environment): the user runs `python scripts/review-emulator.py`. **Next: T-279** (US-131, screen gallery part 1). **Earlier:** T-287 merged (PR #652, docs only, user OK in chat; ADR-107, R-63, US-81/89 amended, `planning/10-feature-inventory.md`; docs drafted by haiku at the user's request and reviewed by ux-reviewer and pr-reviewer). **Next: T-278** (US-131, rich dummy user and one command for the live flow). **Earlier:** T-286 merged (PR #650, CI green, `deploy-supabase` green on `main` run 43108523640; the user had installed the T-277 APK first; the migration's self-check passed, so the dropped free-text/notes data and unused objects are gone and all row counts matched). **Earlier:** T-277 (app side) merged (PR #648, CI green; no backend, no deploy to wait for; the new app sends no `free_text`/`notes` and tolerates a server `free_text` entry; the whole "Para conocerte" screen was deleted, years and kcal are edited through the `IntakeRowEditor` sheets; one drifted test in `test_repdb_tools.py` deleted). **Next: T-286** (backend migration, the last step before the old-app window closes: the user installs the new APK from T-277 first), then T-287 (docs, ADR-107, feature inventory), then T-278. E19 has no 👤 checkpoint. **Earlier (2026-11-11, E18 closed `v1.9.0`):** T-267 done (version bumped to 1.9.0, retrospective `retrospectives/E18.md`, tag `v1.9.0` after merge). E18 is closed; no task is open. The next epic needs the product owner/epic-planner in a fresh session, only when the user asks. (Previous pointer follows.) **Earlier (2026-11-11, E18, closure):** the user **approved the Checkpoint E18-C re-test** (block items; items 4-6 approved earlier), so Checkpoint E18-C is closed. **Next: T-267 (E18 closure, PM step).** Earlier note (re-test): T-276 merged (PR #640, CI green; app only, no backend, no deploy to wait for). **Next: 👤 Checkpoint E18-C re-test** (only the block items, rewritten for the week: 4 items under the T-276 section); its approval releases T-267 (closure). Earlier note (T-276): Checkpoint E18-C was presented: the food form corrections are **approved** (items 4-6 and the food part of 7); the block extension is **rejected** (the pencil must extend the current week, not the block). **Next: `fix(T-276)`**, then only items 1-3 and the block part of item 7 are re-presented (rewritten for the week); their approval releases T-267. Earlier note (Checkpoint E18-C): T-275 merged (PR #637, CI green; no backend). T-274 `deploy-supabase` is green on `main` (run 42913495118). **Next: 👤 Checkpoint E18-C** (user tests, then T-267 closure). Earlier note (T-275): T-274 merged (PR #635, CI green; `deploy-supabase` must be green on `main` with the block's dates intact before Checkpoint E18-C). The next task is **T-275** (US-135, the food form corrections), then **Checkpoint E18-C** and T-267. Earlier note (T-272): T-271 merged (PR #627, `deploy-supabase` green, `food-photo` redeployed); the next task is **T-272** (US-133, the unused-food cleanup; planner starts with the `pg_cron` free-plan check), then **Checkpoint E18-B**. Earlier note: (T-270): T-269 merged (PR #619) and the Checkpoint E18 re-test is **approved by the user except one bug**: opening the Trainer and tapping "Tu plan" / "Tu bloque" each take 2-3 s of loading. Diagnosis (code read, nothing timed): our code, not Supabase. `TrainerViewModel.fetch()` runs ~18-22 sequential round trips on every open, and the box screens (T-268) get a new `TrainerViewModel` that repeats the whole fetch. **T-270 merged (PR #624, 2026-11-10); the user approved the Trainer re-test (2026-11-10), so the Checkpoint E18 re-test is closed and the next task is T-271 (US-132).** Re-present only the Trainer open and the two boxes; its approval releases T-271 (US-132), then T-272 (US-133) and **Checkpoint E18-B**, whose approval releases T-267 (closure). Follow-ups: chat has no follow-up context (each question stands alone; user may want a task); week-summary box has no date range; old non-cardio `no_range_flag` rows lack `figures.routine_id`; `BlockReviewEntry` hand-rolled clickable card; `WeekSummaryScreen` internal `SummaryCard` name clash; duplicate "Tu plan" header.

**Added to E18 (product-owner, 2026-11-10, ADR-105): broken down → T-271 (US-132), T-272 (US-133), Checkpoint E18-B** (epic-planner, 2026-11-10; E18 section, "Text meals and food cleanup").

**Added to E18 (product-owner, 2026-11-10, ADR-106): broken down → T-274 (US-134), T-275 (US-135), Checkpoint E18-C (approved by the user, 2026-11-10; Q-E18-6 limits approved)** (epic-planner, 2026-11-10; E18 section, "Extend the block and food form corrections"). They run after the approved Checkpoint E18-B and before the closure T-267, which now waits for Checkpoint E18-C.

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

E19 (epic-planner, 2026-11-11; user decision 2026-11-11): **no 👤 checkpoint.** The epic adds no functionality to test and says "no gates" (US-123 "Early feedback, no gates"): the user decides each round's findings in chat before the fix batch and reviews the updated gallery images and the emulator himself. The closure T-285 waits only for the user's normal epic-closure approval. Details under the E19 section.

E18 (epic-planner, 2026-11-09; one checkpoint only, user decision 2026-11-09): **Checkpoint E18** after T-266, the last implementation task ("Uso de IA" gone, past reports in "Informes", quick search and my foods at once, the compact Trainer, the exercise chat, plus the final E18 regression; 16 items grouped by functionality; its approval, after the T-268…T-270 fixes, releases T-271). **Checkpoint E18-B** after T-272 (ADR-105: a meal from typed text and the unused-food cleanup, with past days unchanged; 7 items; the only checkpoint for US-132 and US-133, user decision 2026-11-10; its approval releases the closure T-267). **Checkpoint E18-C** (proposed, epic-planner 2026-11-10; ADR-106: extend the block and the food form corrections; 7 items; the only checkpoint for US-134 and US-135, user decision 2026-11-10) after T-275; its approval releases the closure T-267 instead of E18-B's. The 👤 **Gemini terms check** (a decision, not a test) gates T-265. Details under the E18 section.

E17 (epic-planner, 2026-11-09; one checkpoint only, user decision 2026-11-09): **Checkpoint E17** after T-255, the last implementation task (the kcal/macro warning, "Alcohol (g)", the food audit, Open Food Facts copies and the conversion, plus the final E17 regression; 10 items; its approval releases the closure T-256). Details under the E17 section.

E16 (epic-planner, 2026-11-08; one checkpoint only, user decision 2026-11-08): **Checkpoint E16** after T-248, the last implementation task (the nutrition section of "Resumen semanal", "Descanso de dieta" and the final E16 regression; 7 items; its approval releases the closure T-249). As in E15 (Q5), only items the user can try right away are checkpoint items; the diagnosis branches that need two real weeks of data are in "Pending natural-trigger feedback (E16)" under the E16 section, outside the gate. Details under the E16 section.

E15 (epic-planner, 2026-11-08, after the user's Q1-Q5 answers; needs the user's OK): **Checkpoint E15-A** after T-240 ("Bloque nuevo" and switching goal; 6 items) and **Checkpoint E15-B** after T-241, the last implementation task ("Training around a discomfort", plus the final E15 regression; 6 items; its approval releases the closure T-244). **Only items the user can try right away are checkpoint items (user, 2026-11-08, E15 Q5):** paused weeks, welcome back, the block-end notice and the 14-day note fire only after real time passes, so they are in "Pending natural-trigger feedback (E15)" under the E15 section, not gates. Details under the E15 section. Phase 2 checkpoints are in tasks/phase2/todo.md.

## Notes for later epics

- **Out of scope (user, 2026-11-08, ADR-101):** "Recuperar" / returning after a break (regain phase) is out of scope for the app; the 14-day return rule stays as the only break rule.
- **R-59 sweep leftover (T-245): consumed → T-248** (E16 breakdown, 2026-11-08).
- **UI review epic, "Lo que el entrenador sabe de ti" (user, 2026-11-08): consumed → T-277** (E19 breakdown, 2026-11-11; the user decided to remove it in every layer).
- **E13 backlog (user request, 2026-10-30): consumed.** Items 1 → T-179, 2 → T-181…T-184, 3 and 4 → T-205/T-206. Nothing from it is open.

## Reminders

- Delete `design/reported/live-session-cardio-row-layout.png` once the user confirms the cardio fix (T-238).

---

# E19 · UI review → `v1.x` (proposed `v1.10.0`)

Epic: [`planning/epics/E19-ui-review.md`](../planning/epics/E19-ui-review.md) (stories US-131, US-123). Plan: [tasks/plan.md](plan.md).
Rules: `05` §8-§10 (R-35…R-37, R-39 sweep, R-40 guards, R-44, "Decided - do not re-propose"), `04` (no behaviour change), ADR-27 (host tests, no UI-automation harness), ADR-40 (state survives leaving the app), ADR-80, ADR-103 (E19 scope; its "spike" bullet is superseded by the epic update of 2026-11-11: Roborazzi is the tool, recorded by T-279), ADR-2/ADR-14 (the `local` flavor and its "Test login"), ADR-97…99 (the deterministic coach: the reason for removing the free-text notes, T-277); the T-259/T-242 row-count self-check precedent for production drops.
"Notes for later epics": the "Lo que el entrenador sabe de ti" note targets E19 → moved into **T-277** (pointer left in place; decided by the user 2026-11-11: remove it in every layer if nothing reads it, Q-E19-2, and use E19 to remove everything else that is no longer used, Q-E19-5). The "Recuperar" note is a non-goal; nothing else moved. Carried Phase 3 follow-ups that are pure UI polish (the `FormContentMaxWidth` cap and themed "puedes cambiarlo" text on the block targets step, T-235; the `BlockReviewEntry` hand-rolled clickable card, the `WeekSummaryScreen` internal `SummaryCard` name clash and the duplicate "Tu plan" header, E18 pointer) are handed to the ux-reviewer of the Trainer round as pre-seeded findings (T-283); the list above is left as is and the PM ticks them when the round merges.

**Status (epic-planner, 2026-11-11, revised the same day with the user's feedback): PROPOSED, needs the user's OK on the breakdown PR.** **No 👤 checkpoint in E19 (user decision 2026-11-11):** the epic has no new functionality to test and says "no gates"; the user reviews the gallery images and the emulator himself during the rounds, and the closure T-285 waits only for the normal epic-closure approval. **Cleanup first (user decision 2026-11-11, Q-E19-2 and Q-E19-5):** before the gallery, E19 removes everything that is no longer used: the free-text notes in every layer (if nothing reads them) and any other dead code found by an audit. The audit list (what is unused, with evidence) is the first deliverable, shown to the user before any deletion; every production table or column to drop is listed for the user's explicit OK before the migration is written.

**Order and sizing (every task pays 6-8 hand-offs; no S task):**
1. **T-277** · **Size L (by the audit)** · audit + backend + core + UI: one task, one PR (user decision 2026-11-11). The dead-code audit comes first and is shown to the user; the user approves each production drop by name. Then the removal in every layer: the free-text notes ("Lo que el entrenador sabe de ti", the switches, the "Para conocerte" free-text field and help) and every unused screen, route, composable, string, resource, domain/data class, port, DI binding, feature flag, script, Edge Function or field, RPC, table, column and stale doc, with one migration that drops the approved objects (row-count self-check, the T-259/T-242 precedent). First of all, so the dataset and the gallery are built on the cleaned app and schema.
2. **T-278** · **Size M** · backend + script: US-131 live flow: the rich dummy user on Docker Supabase and the one command that starts the backend and installs the `local` app on the emulator. It follows T-277 because it seeds the post-cleanup schema. It goes before the gallery because nothing ties it to the gallery (the gallery uses fake UI states, no backend and no user), and this way the user can use the emulator while the gallery is built.
3. **T-279** · **Size L** · UI (host tests): US-131 gallery part 1: the gallery harness, the command, the index page, the missing-screen and missing-overlay check, and every screen of the Diario and Perfil areas (plus Inicio: sign-in, onboarding, the root loading and profile-error states, and Centro de avisos). US-131 as one task is far above ~15 files (46 routes, 5 root states, about 35 sheet and dialog composables, each in several states; inventory in `tasks/plan.md`), so it is split; the split follows the review areas, not components, and the harness is built once here.
4. **T-280** · **Size L** · UI (host tests): US-131 gallery part 2: every screen of the Entreno and Trainer areas; the check's pending list becomes empty.
5. **T-281** · **Size M (estimate)** · review + UI: US-123 round 1, Diario.
6. **T-282** · **Size M (estimate)** · review + UI: US-123 round 2, Entreno.
7. **T-283** · **Size M (estimate)** · review + UI: US-123 round 3, Trainer.
8. **T-284** · **Size M (estimate)** · review + UI: US-123 round 4, Perfil. Last implementation task.
9. **T-285** · closure step (PM, no size class; it waits for the user's epic-closure approval, so it cannot merge into a task).

Round sizes are estimates: the work is the accepted findings, known only after the user decides them in chat. If a round's accepted findings exceed ~15 files, the round's planner splits the fix into batches with new IDs (one designsystem/rule batch first, then the screen sweep), never one task per finding. A round with very few accepted findings (S) merges into the next round instead of shipping alone (the PM records it).

---

## T-277: Remove everything unused: the audit, then the free-text notes and all dead code in every layer (Q-E19-2, Q-E19-5, user decisions 2026-11-11) · L

**Story:** US-123 (cleanup decided before the rounds; the free-text note moved from "Notes for later epics") · **Flow:** planner (audit plan) → kotlin-core-developer ∥ backend-developer (audit run, read-only) → **PM presents the audit list to the user in chat (first deliverable); the user approves each production drop by name** → planner (removal plan) → ux-reviewer (plan: strings and screens removed or reworded) → backend-developer (migration, readers, Edge Functions, pgTAP) → kotlin-core-developer (navigation, DI, domain/data, view models) → compose-ui-developer (screens, strings, resources) → pr-reviewer ∥ ux-reviewer (diff) → pr-creator (`scripts/verify-local.sh --full`: navigation, DI and designsystem may change) · **Size:** L (by the audit; mostly deletions; one task and one PR across every layer by user decision; split by layer with a new ID only if the planner counts clearly more than ~30 files with real edits)
**Scenarios:** US-123 Behaviour does not change (only unused things go; the free-text switches control nothing today, ADR-97…99: `plan_*` kinds are `deterministic: true`); A decision becomes a rule (no text claims the coach reads something it does not).
**Description:** **Step 1, the audit (first deliverable, read-only):** a list of everything no longer used, one row per item: layer, item, evidence (the command or search and its result), proposed action, and whether it holds production data. Sources: Kotlin compiler and lint warnings for unused symbols and parameters, Android lint `UnusedResources` (strings, drawables, raws), a search for unreferenced screens, routes, composables, domain/data classes, ports, repositories, DTOs, DI bindings and feature flags (`AppFeatures`, `AppConfig`), a search of every SQL function/RPC, table and column in `supabase/migrations` against the app, the Edge Functions and the other SQL (a function only called by pgTAP counts as unused), Edge Functions and request fields with no caller, scripts in `scripts/` and workflows no longer run, and docs that describe removed features. Each finding is grep-verified (no dynamic call by name, e.g. `rpc("...")` strings, routes by serialization, resources by name). **Free-text notes, verified first:** the planner shows by search that no coach logic reads the free text (known places where it appears: `CoachIntake`, `PlanInput`, `CoachIntakeDto`, `PlanInputsDto`, the intake repositories, `coach_free_text`, `coach_summary_free_text`, `coach_plan_inputs`, `coach_summary`, the `coach` function's `planNutrition` test). If a reader only passes it through to the removed screens, it counts as unused; if anything really uses it, the planner stops and reports to the PM. The notes typed in other features (check-in, discomfort, the block review's "¿Cómo te ha ido?") are judged one by one: they go only if nothing shows or uses them. The list is written under this task, and the PM shows it to the user before any deletion. The user approves each production table or column by name. For every backend item the list also says how the installed app (`v1.9.0`) uses it ("old-app impact", see the risk below). **Step 2, the backend removal (same PR):** the free-text storage (`coach_free_text` and its send switch, the intake `free_text` column, `coach_summary_free_text`, the RPCs `coach_free_text_lines` and `set_free_text_line_excluded`, and the free-text fields in `coach_plan_inputs`, `coach_summary` and the `coach` Edge Function inputs, as the audit confirmed), plus every approved backend item of the audit: unused RPCs and functions, tables, columns, Edge Functions or request fields, and their grants. **Only what the user approved by name.** A new migration (merged migrations are never edited) drops them, recreates the readers without the dropped fields, and carries a row-count self-check as in T-259/T-242: every table it does not drop keeps its row count, otherwise it raises. Edge Function fields are removed and the functions redeployed. A function with no caller is deleted; the planner checks that `deploy-supabase` undeploys it, otherwise it is a 👤 console step for the user. pgTAP: the tests of dropped objects are removed, the readers' tests updated, plus a test that the dropped objects are gone; `delete_account_cascade` stays green. **Step 3, the app-side removal:** the Trainer row `trainer_free_text_row`, `FreeTextLinesRoute` with its screen, view model, UI state, nav-host and `fullScreenRoutes` entries, DI and strings; the "Para conocerte" free-text field and its help (`intake_free_text_*`; the screen stays for years training and maintenance kcal, which the coach reads; Q-E19-4 is settled by Q-E19-5); the free-text fields in the domain models, DTOs and repositories; every other app-side item of the audit (screens, composables, strings, resources, classes, ports, bindings, flags, scripts, stale docs). Any remaining text that says the coach reads the user's notes is reworded (ux-reviewer). **Docs (the planning rules require a decision record for a feature removal, CLAUDE.md "a new decision = new ADR + update affected docs"):** a new ADR (next number, ADR-107) "Free-text notes and unused code removed; the coach is deterministic" that amends the US-81/US-89 decisions and lists what E19 removes, including the dropped backend objects; the US-81 and US-89 story files and ADRs marked as amended; `05` §10 glossary (the term removed) and §8 a rule "no copy claims the coach reads or uses something it does not" (R-##, ux-reviewer words it); `03` (tables, columns, RPCs), `04` (any rule that mentions the notes), `02` if an Edge Function goes.
**Risk of one PR (the PM tells the user before the merge):** the migration deploys to production when the PR merges, while the phone still runs the old APK (`v1.9.0`) until the user installs the new one. The old app's behaviour against the dropped free-text objects, from its code:
- Reading "Para conocerte" keeps working: it selects `coach_user_intake` with `select()` (all columns), and its DTO's `free_text` is nullable with a default.
- "Lo que el entrenador sabe de ti" calls `coach_free_text_lines` and `set_free_text_line_excluded`. Once those RPCs are dropped, that screen shows its load error and its switches fail ("No se pudo cambiar…").
- Saving the free-text field in "Para conocerte" sends a `free_text` key to `save_coach_intake`. It fails unless the recreated function ignores unknown keys.
- The plan-input readers only map a `free_text` key when present, so an absent key is tolerated.

Mitigations:
1. The recreated `save_coach_intake` ignores a `free_text` key instead of failing.
2. The PM delivers the new APK (`deliver-apk.py`) right after `deploy-supabase` is green, and asks the user to install it before using the Trainer.
3. For every other dropped object, the audit's "old-app impact" column says whether the old app calls it. Anything the old app calls on a main screen gets a tolerant stub or waits for a later cleanup, and the user decides which when approving the list.
4. The migration is the last commit of the PR, so a review fix never redeploys a half-done drop.
**Added by the user (2026-11-11): a feature inventory.** The audit also produces `planning/10-feature-inventory.md`: a Markdown list of every functionality of the app (by area: Diario, Peso, Entreno, Trainer, Perfil, Inicio, Centro de avisos, backend jobs/Edge Functions), one line each, with the route/screen and the story (`US-##`) that defines it. It is built from the audit evidence (routes, screens, RPCs, Edge Functions), after the removal so it lists only what remains, and is linked from `planning/README.md`.
**User decisions on the audit (2026-11-11; audit rows in `.workflow/audit_backend.md` B1-B23 and `.workflow/audit_kotlin.md`, drafts of the inventory in `.workflow/feature_inventory*_draft.md`):** (1) drop table `coach_free_text_exclusions` and column `coach_user_intake.free_text` (approved by name), plus RPCs `coach_free_text_lines` and `set_free_text_line_excluded` and the free-text fields of `coach_plan_inputs`, `coach_summary` and `save_coach_intake` (the last ignores the key); (2) `exercise_discomforts.note` **stays** (the user wants it to track discomforts across sessions; it is not shown outside its own form today, a possible later task); `weekly_check_ins.notes` is **dropped** (approved by name; the user believes there is no stored data, the migration's row-count self-check must report how many non-empty notes it removes); (3) scrub the `free_text` key from every existing `coach_recommendations.input_summary` (production UPDATE approved by the user); (4) **drop every unused item, no "keep for later"**: `save_meal_slots`, `delete_meal_slot`, `coach_plan_week_of_date` and the four one-shot helpers (`assert_unique_meal_labels`, `convert_used_off_foods`, `coach_rederive_plan_links`, `migrate_equipment_list`; the planner checks `off_alcohol_g`, `coach_plan_calendar` and `copy_off_food_core` the same way), with their pgTAP, and all the audit's Kotlin, resource and Python-test items (the planner decides the resource check, since Android lint `UnusedResources` does not run on the KMP modules).
**SPLIT (user, 2026-11-11, after the removal plan counted ~90 files with real edits):** T-277 is split by layer into three tasks, each its own PR and session. Audit, user decisions and plan are kept in `tasks/T-277/` (`audit_backend.md`, `audit_kotlin.md`, `removal-plan.md`, inventory drafts); each task's planner reads the plan's matching slices. **T-277** = app side: slices A (kotlin-core: data, nav, DI, view models), B (compose-ui: screens, strings, host tests) and C (`scripts/check-unused-resources.py` + its Python test, wiring of the two orphan Python tests, in `verify-local.sh`, `android-ci.yml`, `security.yml`); no migration, merged first so the old-app window shrinks. **T-286** = backend slice E (the migration as the last commit, pgTAP, Deno, `deploy-supabase` green with data intact, then deliver the new APK; the old v1.9.0 Check-in screen also breaks once `weekly_check_ins.notes` is dropped, so T-277's app must be installed first). **T-287** = docs slice D (ADR-107, US-81/89 amended, R-63, `03`/`04`/`07`/`02`, and `planning/10-feature-inventory.md` listing what remains, linked from `planning/README.md`). Order: T-277 → T-286 → T-287 → T-278. The Acceptance criteria and Verification below apply to the task that owns each layer.
**Acceptance criteria:**
- [ ] `planning/10-feature-inventory.md` exists, covers every area and every remaining route, and is linked from `planning/README.md`.
- [ ] The audit list (item, layer, evidence, proposed action, production data yes/no, old-app impact) is recorded under this task and was shown to the user before any deletion; every production table or column dropped was approved by name.
- [ ] Every object dropped is in the approved list; the row-count self-check passes; no reader, RPC or Edge Function still mentions a dropped field (search recorded in the PR).
- [ ] The Trainer has no "Lo que el entrenador sabe de ti" row; no route, screen, switch, field, string or app-side class of the free-text notes remains; no text says the coach reads the user's notes.
- [ ] "Para conocerte" keeps years training and maintenance kcal, their save and their restoration.
- [ ] Every other app-side item of the audit is removed (or listed with the reason it stays); every remaining screen behaves as before; host tests of removed code are deleted, the rest stay green.
- [ ] The new app calls no dropped object; the old app's impact is as described in the risk above, with its mitigations in place, and the PM told the user before the merge.
- [ ] ADR-107 and the affected docs updated in the same PR; `check-ux-conventions.sh` passes.
**Host tests:** Trainer root without the row; the route table has no free-text route; "Para conocerte" without the free-text field and help, its other fields saved and restored (`StateRestorationTester`); repository/DTO tests updated for the removed fields; a guard (string test or `check-ux-conventions.sh`) that no string claims the coach reads notes, if ux-reviewer finds it checkable; pgTAP (dropped objects absent; readers return their other fields unchanged; `save_coach_intake` ignores a `free_text` key; cross-user tests still green); Deno tests of any changed Edge Function.
**Verification:**
- [ ] lint (including the unused-resources check, now clean or with an allowlist that names each kept item) + host tests green (`verify-local.sh --full`); `npx supabase db reset && npx supabase db lint && npx supabase test db` + Deno tests green.
- [ ] After the merge: `deploy-supabase` green on `main` with data intact (row counts from the self-check); the new APK delivered to the user right after.
**Agent runs:** 9 (audit step included).
**Dependencies:** breakdown approved; the user's OK on the drop list (inside the task, after the audit).
**Files likely touched:** `supabase/migrations/<ts>_drop_unused.sql`, `supabase/tests/{coach_free_text,coach_plan_inputs,coach_summary,coach_user_intake,delete_account_cascade}.test.sql`, `supabase/functions/coach/*`, `app/{AppRoutes,AppNavHost,AppFeatures,AppScaffold,AppRoot,App}.kt`, `di/UiModule.kt`, `feature/trainer/{FreeTextLines*,TrainerScreen,Intake*,YourData*,BeforeStartUiState}.kt`, `shared/.../{CoachIntake,PlanInput,CoachIntakeDto,PlanInputsDto,CoachIntakeRepository,SupabaseCoachIntakeRepository,UnconfiguredCoachIntakeRepository}.kt`, `strings.xml`, plus the resources, scripts, SQL, functions and docs found by the audit, their tests, `planning/{00-decisions,02-architecture,03-data-model,04-business-rules,05-design-system}.md`, the US-81/US-89 epic files
**Estimated scope:** L (by the audit)

---

## T-278: A rich dummy user and one command for the live flow on the emulator (US-131) · M

**Story:** US-131 · **Flow:** planner → backend-developer (dataset, seed test) → pr-reviewer → pr-creator (no gate script: SQL and script only; `06` §2.1) · **Size:** M (dataset SQL with dates relative to today, its pgTAP check, the command script, docs; about 6-8 files)
**Scenarios:** US-131 A rich dummy user for real flows; One command starts the live flow.
**Description:** **Dataset (local only, no personal data):** the seeded test user `test@appfitness.local` (`08` §3.2, fixed id) gets weeks of diary (several comidas a day, personal and catalogue foods, at least one entry whose food is gone, ADR-105), weeks of weight history, a training block in progress with finished sessions (including a cardio set, a paused week and an extension, so the E18 screens have content), an accepted trainer plan, stored weekly reports and a block review in "Informes", and open notices in Centro de avisos. It is written against the post-cleanup schema (T-277), with no free-text notes. Dates are relative to the day the command runs, so the data is always "current". The data is written through the same tables and RPCs the app uses where possible, so it is valid for the coach readers. **Where it lives (planner decides, recorded in the PR):** a separate local-only SQL file applied by the command after `supabase db reset`, so `seed.sql` and every existing pgTAP test that relies on the minimal test user stay unchanged; never applied to production (`deploy-supabase` runs `db push` without seed). **Command:** one command (Python, like `deliver-apk.py`, runnable from PowerShell) that starts Docker Supabase, resets it, applies the dataset, builds and installs the `local` flavor on the running emulator (`10.0.2.2`), and prints the "Test login" credentials it uses; it stops with a clear message when Docker or the emulator is missing. Docs: `08-environment-setup.md` (the command and the dataset), `02-architecture.md` §"seed" line.
**Acceptance criteria:**
- [x] One command starts the local backend with the dummy data and installs the app on the emulator.
- [x] After "Test login", Diario, Peso, Entreno, Trainer, "Informes" and Centro de avisos show the dummy history, block, plan, reports and notices.
- [x] The dataset is local only (never in `db push`), holds no personal data, and `seed.sql` and the existing pgTAP tests are unchanged and green.
**Host tests:** a pgTAP test of the dataset (applied after reset: the user has N diary days, weight rows, an accepted plan with a saved block and finished sessions, at least one stored report and one notice; coach readers such as `coach_plan_weeks` return without error); the script's dry-run test if the planner adds one (as `test_deliver_apk.py`).
**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; the command run once by a subagent against the local Docker stack (emulator step optional in the agent's environment).
**Agent runs:** 5. About 6-8 files.
**Dependencies:** T-277 merged (it seeds the post-cleanup schema). It does not depend on the gallery and the gallery does not depend on it (the gallery renders fake UI states with no backend); it goes first so the user can use the emulator while the gallery is built.
**Files likely touched:** `supabase/seed-review.sql` (name by planner), `supabase/tests/seed_review.test.sql`, `scripts/review-emulator.py` (name by planner), `scripts/test_review_emulator.py`, `planning/08-environment-setup.md`, `planning/02-architecture.md`
**Estimated scope:** M

---

## T-279: Screen gallery, part 1: harness, index, missing-screen check, Diario and Perfil (US-131) · L

**Story:** US-131 · **Flow:** planner → ux-reviewer (plan: the area map and the state list per screen) → compose-ui-developer (gallery entries and fixtures; kotlin-core-developer only if a screen needs a stateless content split) → pr-reviewer ∥ ux-reviewer (diff) → pr-creator (`scripts/verify-local.sh --full`) · **Size:** L (harness plus about 25 screens; split from T-280 by area)
**Scenarios:** US-131 One command renders every screen (Diario, Perfil, Inicio, Centro de avisos); No screen is missing (the check itself, with a named pending list for T-280); States are shown, not only the happy path; Images are easy to browse.
**Description:** **Tool (epic, recorded here):** Roborazzi on the Robolectric host tests, already in the repo (`composeApp/build.gradle.kts`, 360x640 dp device as the designsystem screenshot tests, `06` §"Screenshot tests"). No `@Preview`, no Maestro, no device, no backend, no Koin graph with real clients, no account, no secret. **Gallery, not goldens (planner confirms):** the gallery images are written on demand by the gallery command to a build output folder (gitignored) and uploaded as a CI artifact (`android-ci.yml` already uploads `**/build/outputs/roborazzi/`); they are not verified goldens, so a UI change does not need re-recorded PNGs; the existing designsystem goldens stay as they are. **Fixtures:** each screen is rendered from fixed fake UI states (dummy user, no personal data, fixed clock and locale `es-ES`, dark theme as the app), through its stateless content composable; where a screen has no stateless split, the planner chooses between the existing host-test fakes and a stateless extraction (a pure refactor; behaviour and existing host tests unchanged). **States:** for each screen, with data, empty, loading and error where they exist, plus every sheet and dialog it opens (ux-reviewer lists them per screen in the plan). **Index:** one generated HTML page (or markdown, planner's choice) grouping the images by area (Diario, Entreno, Trainer, Perfil, and "Inicio" for sign-in and onboarding), one row per screen with its states. Each screen belongs to exactly one area; ux-reviewer assigns the shared ones (Centro de avisos, "Informes", the food search reached from several places) in the plan. **Command:** one command (for example a Gradle task or `scripts/gallery.sh` / `.py`, usable from PowerShell as `deliver-apk.py` is) that renders the gallery and prints the index path. **Coverage (the inventory in `tasks/plan.md` "What the gallery must cover" is the starting list; the planner re-derives it from the code):** every `AppRoute` (the `AppDestinations.fullScreenRoutes` list plus the four tab roots), every `RootRoute` state (Loading, Welcome, Onboarding with each step, ProfileError), every sheet and dialog, and the in-screen flows (the live session's summary, the photo/"Describir comida" AI sheets and the AI draft, the barcode result paths, the date pickers). **Missing-screen and missing-overlay check (two parts, both fail naming what is missing):** (1) routes: a host test reads the `AppRoute` subclasses (sealed-class reflection or `fullScreenRoutes` plus the tab roots) and the `RootRoute` states and fails for each one without a gallery entry; (2) sheets and dialogs, which are not routes: a source scan (a host test reading `commonMain`, or a `check-ux-conventions.sh`-style script run in CI; planner's choice) finds every composable in `feature/` that calls an overlay primitive (`AppBottomSheet`, `AppActionSheet`, `FoodSheet`, `OptionPickerSheet`, `NameSheet`, `NoteSheet`, `DayPickerSheet`, `ChartMetricPickerSheet`, `RpePickerSheet`, `SetTypePickerSheet`, `AppConfirmDialog`, `DiscardChangesDialog`, `AppDatePickerDialog`, and any new overlay primitive added to `designsystem/`) and fails for each one not listed in the gallery's overlay manifest. Exempt by name, with the reason in the manifest: the debug component catalog (`CatalogRoute`, local only); system UIs the app does not draw (Google sign-in's Credential Manager sheet, the system camera and photo picker, the ML Kit barcode scanner, Android permission dialogs); the rest-alert system notification (`RestAlertNotifier`, an Android notification, not Compose; its text is listed in the index as text). There are no home-screen widgets. The Entreno and Trainer screens and overlays are in an explicit pending list that T-280 empties (the list cannot grow). Docs: `06-agent-workflow.md` (the gallery command, where the images and the index are, record vs gallery), ADR-103's spike bullet marked superseded (Roborazzi chosen, epic 2026-11-11), `02-architecture.md` testing line if the planner adds a source set or folder.
**Acceptance criteria:**
- [ ] One command renders every Diario, Perfil, Inicio (sign-in, onboarding, loading, profile error) and Centro de avisos screen with its states, sheets and dialogs, with no device, backend, account or secret.
- [ ] The index page groups the images by area and opens each one.
- [ ] The check fails and names a route or a sheet/dialog without an entry (proved by a test for each part); only the named T-280 pending list and the documented exemptions are exempt.
- [ ] No production behaviour changes; every existing host test and golden stays green; `check-ux-conventions.sh` passes.
**Host tests:** the gallery tests themselves (one per screen, all states); the route check and the overlay check, each with a test that it names a missing entry; existing tests unchanged.
**Verification:**
- [ ] lint + `testLocalDebugUnitTest`/host tests green (`verify-local.sh --full`); the gallery artifact is attached to the PR's CI run and the PM links the index in the PR body.
**Agent runs:** 6. About 15-20 files (harness, index generator, the two checks, fixtures and entries for two areas).
**Dependencies:** T-278 merged (no build dependency on the dummy user; T-277 before it means the gallery never captures removed screens).
**Files likely touched:** `composeApp/src/androidHostTest/.../gallery/` (harness, index, check, `DiarioGallery`, `PerfilGallery`, fixtures), `composeApp/build.gradle.kts` (gallery task or property), `scripts/gallery.*`, `.gitignore`, `planning/06-agent-workflow.md`, `planning/00-decisions.md` (ADR-103 note)
**Estimated scope:** L

---

## T-280: Screen gallery, part 2: Entreno and Trainer (US-131) · L

**Story:** US-131 · **Flow:** planner → ux-reviewer (plan: state list per screen) → compose-ui-developer (kotlin-core-developer only for a stateless content split) → pr-reviewer ∥ ux-reviewer (diff) → pr-creator (`scripts/verify-local.sh --full`) · **Size:** L (about 30 screens, many with several states: the live session, the block builder flow, the plan proposal, reports)
**Scenarios:** US-131 One command renders every screen (all areas); No screen is missing (pending list empty); States are shown, not only the happy path.
**Description:** Gallery entries, with the T-279 harness and conventions, for the Entreno area (routines with folders and their sheets, the routine builder with its calculator sheets, exercise picker, exercise details with its tabs and the chat tab, create/edit exercise sheets, live session including rest and cardio timers, RPE, set type, disc loader, warm-up, discomfort, progression help, volume options and finish sheets and its dialogs, the session summary inside it, save-as-routine and update-routine sheets, session detail, edit workout, past workout with its chooser sheet, the calendar) and the Trainer area (Trainer root and "Detalles", "Tu plan", "Tu bloque" with the "Actual" week pencil and its date picker, "Para conocerte", "Tus datos", "Antes de empezar" with the activity-level sheet, plan proposal, block preview, the builder with split, day type, slot picker, create-exercise and exercise-reason sheets, block start and replace sheets, folder block and review result, weekly summary, block review, "Informes" and a past report). States per screen as listed by ux-reviewer (with data, empty, loading, error, sheets, dialogs; the plan proposal's blocked state, a paused week, an extended week). The T-279 pending list is emptied, so from here every new route or overlay needs a gallery entry or the check fails.
**Acceptance criteria:**
- [ ] The gallery command renders every screen of the app (all areas) with its states, sheets and dialogs, and the index lists them by area.
- [ ] The check has no pending list; only the documented exemptions (debug catalog, system UIs, the system notification) remain.
- [ ] (user request, 2026-11-12) The gallery also renders a "Design system" showcase page: a dummy UI with every `designsystem/` component and its variants (states, sizes, dark theme), as its own index area, so the design system can be reviewed in one image set. It sits next to the debug catalog exemption (`CatalogRoute` stays exempt); the planner decides whether the showcase is a new gallery entry group or reuses the catalog's composables.
- [ ] No production behaviour changes; existing host tests and goldens green; `check-ux-conventions.sh` passes.
**Host tests:** the gallery tests of both areas; the missing-screen check green with an empty pending list.
**Verification:**
- [ ] lint + host tests green (`verify-local.sh --full`); the full gallery artifact on the PR's CI run, index linked in the PR body.
**Agent runs:** 6. About 12-18 files.
**Dependencies:** T-279 merged.
**Files likely touched:** `composeApp/src/androidHostTest/.../gallery/{EntrenoGallery,TrainerGallery}*.kt`, fixtures, the check's pending list
**Estimated scope:** L

---

## The review rounds (US-123) *(T-281…T-284; no 👤 gate in E19, user decision 2026-11-11)*

Before round 1 the PM sends the user the gallery command, the index path and the live-flow command (no gate): the user browses them himself, and anything missing or wrong in the tools becomes a `fix(T-###)` before the round starts.

Every round follows the same steps (US-123 "Reviewed in rounds", "Every change goes through the design system", "A decision becomes a rule", "Early feedback, no gates", "Behaviour does not change"):
1. **Review (ux-reviewer, review mode on the gallery images and, when needed, the emulator):** the area's screens and every flow, sheet and dialog reachable from them. Output: a findings list, one row per finding: screen, problem, proposed change, and the component or rule it affects (`05` §6/§8-§10, R-##). It respects "Decided - do not re-propose" (`05` §8) and proposes no behaviour change.
2. **Decision (PM ↔ user, in chat):** the PM presents the list; the user accepts, rejects or changes each item. Nothing is planned or built before that. The decisions are recorded in the round's task section (accepted, rejected, changed).
3. **Rules first (same branch, first commit):** every accepted change is written into `05` (§6 component, §8 rule, §9 pattern, §10 glossary) before code; a finding that states how something should look or work everywhere becomes an R-## rule and, when it can be checked, a guard in `check-ux-conventions.sh` (CLAUDE.md: a repeated finding becomes a check).
4. **Fix batch:** planner → ux-reviewer (plan: pattern map, sweep list of every screen in any area with the pattern, Spanish copy) → compose-ui-developer (kotlin-core-developer only when a UI state must change shape) → pr-reviewer ∥ ux-reviewer (diff) → pr-creator (`verify-local.sh`, `--full` when the designsystem changes). The sweep covers every screen of the app with the pattern, not only the round's area (R-39); later rounds review the updated gallery.
5. **Early feedback:** after the merge the PM sends the user the updated gallery images of the changed screens (the CI artifact or the gallery command), with no 👤 gate; any comment becomes a finding of the next round, or a `fix(T-###)` if it is a defect.
Common acceptance criteria for every round: the findings list was decided by the user before planning; every accepted change is in `05` first and swept everywhere it applies; no change alters what the app does (the existing host tests stay green and are only adjusted for testTags or layout, never for behaviour); new or changed UI has host tests and gallery entries; `check-ux-conventions.sh` passes; state survives leaving the app (ADR-40).

## T-281: Review round 1: Diario (US-123) · M (estimate)

**Story:** US-123 · **Flow:** ux-reviewer (review) → user decisions in chat → planner → ux-reviewer (plan) → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M (estimate; resized after the decisions, split into batches with new IDs above ~15 files)
**Scenarios:** US-123 Reviewed in rounds (Diario); Every change goes through the design system; A decision becomes a rule; Early feedback, no gates; Behaviour does not change.
**Known finding from T-279 (ux-reviewer, 2026-11-12):** Centro de avisos copy is not plural-aware: `notifications_weight_reminder` and `notifications_circumference_reminder` (`strings.xml` ~1726) give "1 días" / "1 semanas" and "0 días" / "0 semanas"; make them `<plurals>` like `notifications_bell_cd`, and the user decides the wording for the 0 case ("Hoy toca pesarte"?). The gallery already has the 1-day, 1-week and 0 entries.
**Description:** Area: Diario and everything reachable from it: the day view, comidas and their entries, the add-food flow (search, the AI sheet with photo and "Describir comida", the AI draft, create/edit food with its sections and the energy check), barcode, diary history, check-in and activity if reached from Diario, Centro de avisos if ux-reviewer assigned it here (T-279), and every sheet and dialog on the way. Steps as in "The review rounds".
**Acceptance criteria:**
- [ ] The Diario findings list (screen, problem, proposed change, component/rule) was presented and each item decided by the user before planning; decisions recorded here.
- [ ] The common round criteria above.
**Host tests:** per accepted change, the Robolectric test of each changed screen (and of every swept screen), gallery entries updated; a `check-ux-conventions.sh` self-test for any new guard.
**Agent runs:** 7 plus the review step.
**Dependencies:** T-280 merged.
**Files likely touched:** decided by the findings; `planning/05-design-system.md`, `scripts/check-ux-conventions.sh`, `designsystem/`, `feature/{diary,food,meals,checkin,activity,notifications}/`, gallery entries
**Estimated scope:** M (estimate)

---

## T-282: Review round 2: Entreno (US-123) · M (estimate)

**Story:** US-123 · **Flow:** as T-281 · **Size:** M (estimate; same resizing rule)
**Scenarios:** US-123 Reviewed in rounds (Entreno); Every change goes through the design system; A decision becomes a rule; Early feedback, no gates; Behaviour does not change.
**Description:** Area: Entreno and everything reachable from it: routines and the routine builder, templates, the exercise picker and exercise details (with the chat tab), the live session (set rows, the timer slot R-58, rest and cardio, RPE and set-type pickers, discomfort), session summary and detail, past and edited workouts, the calendar, quick search, and every sheet and dialog on the way. Changes already swept by round 1 are not re-proposed. Steps as in "The review rounds".
**Acceptance criteria:**
- [ ] The Entreno findings list was decided by the user before planning; decisions recorded here.
- [ ] The common round criteria above; the live session keeps its timers, haptics and restoration behaviour (existing tests green).
**Host tests:** as T-281, for the changed and swept screens.
**Agent runs:** 7 plus the review step.
**Dependencies:** T-281 merged.
**Files likely touched:** decided by the findings; `planning/05-design-system.md`, `scripts/check-ux-conventions.sh`, `designsystem/`, `feature/{routines,templates,exercises,workout}/`, gallery entries
**Estimated scope:** M (estimate)

---

## T-283: Review round 3: Trainer (US-123) · M (estimate)

**Story:** US-123 · **Flow:** as T-281 · **Size:** M (estimate; same resizing rule)
**Scenarios:** US-123 Reviewed in rounds (Trainer); Every change goes through the design system; A decision becomes a rule; Early feedback, no gates; Behaviour does not change.
**Description:** Area: the Trainer and everything reachable from it: the compact Trainer and "Detalles", "Tu plan", "Tu bloque" with "Semanas del bloque" and the "Actual" week pencil, "Para conocerte" (as left by T-277), "Tus datos", the plan proposal, the block flow (types, split, targets, builder, preview, before start), folder block and its review result, the weekly summary, the block review, "Informes" and past reports, and every sheet and dialog on the way. Steps as in "The review rounds". ("Lo que el entrenador sabe de ti" is already removed by T-277.) **Pre-seeded findings (the carried follow-ups):** the `BlockReviewEntry` hand-rolled clickable card, the `WeekSummaryScreen` internal `SummaryCard` name clash, the duplicate "Tu plan" header, the `FormContentMaxWidth` cap and the themed "puedes cambiarlo" text on the block targets step (T-235).
**Acceptance criteria:**
- [ ] The Trainer findings list, including the pre-seeded ones, was decided by the user before planning; decisions recorded here.
- [ ] The common round criteria above.
**Host tests:** as T-281, for the changed and swept screens.
**Agent runs:** 7 plus the review step.
**Dependencies:** T-282 merged.
**Files likely touched:** decided by the findings; `planning/05-design-system.md`, `scripts/check-ux-conventions.sh`, `designsystem/`, `feature/trainer/`, `strings.xml`, gallery entries
**Estimated scope:** M (estimate)

---

## T-284: Review round 4: Perfil (US-123) · M (estimate)

**Story:** US-123 · **Flow:** as T-281 · **Size:** M (estimate; same resizing rule)
**Scenarios:** US-123 Reviewed in rounds (Perfil); Every change goes through the design system; A decision becomes a rule; Early feedback, no gates; Behaviour does not change.
**Description:** Area: Perfil and everything reachable from it: the Perfil root, "Mi entrenamiento", Preferencias (hub and sections, meal structure and default comidas), Peso and its history, statistics (training stats, strength, exercise progress, muscle volume), "Acerca de", and the "Inicio" screens (sign-in, onboarding), with every sheet and dialog on the way. Steps as in "The review rounds". Last implementation task: after its merge the PM checks that no finding from the four rounds is left without a decision or an owning task (rejected items stay recorded as rejected).
**Acceptance criteria:**
- [ ] The Perfil findings list was decided by the user before planning; decisions recorded here.
- [ ] The common round criteria above.
**Host tests:** as T-281, for the changed and swept screens.
**Agent runs:** 7 plus the review step.
**Dependencies:** T-283 merged.
**Files likely touched:** decided by the findings; `planning/05-design-system.md`, `scripts/check-ux-conventions.sh`, `designsystem/`, `feature/{profile,weight,analytics,about,auth,onboarding,meals}/`, gallery entries
**Estimated scope:** M (estimate)

---

## T-285: E19 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E19 Definition of Done · **Flow:** PM closure step after T-284 merges and the user approves the epic closure (the normal closure approval; E19 has no 👤 checkpoint, user decision 2026-11-11). No developer agent, no size class; it cannot merge into a task because it waits for that approval.
**Description:** Version bump (`versionName`/`versionCode`, minor per epic, proposed `1.10.0`), tag, retrospective `retrospectives/E19.md`, archive of the E19 log lines; `09-open-questions.md` records Q-E19-1 (no checkpoints), Q-E19-2 (free text removed in every layer), Q-E19-5 (dead-code cleanup, with the approved drop list) and their answers; `01-scope.md` E19 row marked done; the epic status set to done.
**Acceptance criteria:**
- [ ] T-277…T-284 (and any batch or fix tasks) merged with green CI; `deploy-supabase` green after T-277 with data intact.
- [ ] Every round's findings decided and recorded; `05` §8-§10 hold every accepted rule; `check-ux-conventions.sh` passes.
- [ ] `00`, `02`, `05`, `06`, `08` and the epic file are current; the user approved the epic closure.
- [ ] Version bumped; `tasks/todo.md` pointer updated through a PR.
**Verification:**
- [ ] `git tag` lists the E19 tag.
**Dependencies:** T-284 merged; the user's epic-closure approval.
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `planning/01-scope.md`, `planning/epics/E19-ui-review.md`, `retrospectives/E19.md`
**Estimated scope:** closure step (PM)

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
10. **T-267** · closure step (PM, no size class; it waits for the checkpoint approval, so it cannot merge into a task): E18 closure. *(Moved after Checkpoint E18-B by the 2026-11-10 breakdown, see below.)*

**Added 2026-11-10 (ADR-105; order after the T-268…T-270 fixes):** 11. **T-271** · **Size M** · backend + core + UI: US-132, the `food-photo` text mode and "Describir comida" (one task: the backend change is about 5 files and alone would be S). 12. **T-272** · **Size M** (upper end) · backend + core: US-133, the `pg_cron` cleanup, the diary entries that survive a deleted food. → **👤 Checkpoint E18-B**. 13. **T-267** closure.

**Added 2026-11-10 (ADR-106; after the approved Checkpoint E18-B; T-267 moves from 13 to 15):** 13. **T-274** · **Size L** · backend + core + UI: US-134, extend the block (one vertical slice; the SQL is the heavy part, the app part is a pencil, a date picker and one call, too small to stand alone). 14. **T-275** · **Size M** · core + UI: US-135, the food form corrections. → **👤 Checkpoint E18-C** (proposed; the only one for both stories, user decision 2026-11-10). 15. **T-267** closure.

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

### Checkpoint E18 — Functional improvements, with the final E18 regression 👤 (16 items, leave and come back included) *(epic-planner, 2026-11-09; user decision 2026-11-09; after the ADR-105 breakdown (2026-11-10) its approval releases T-271, and T-267 waits for Checkpoint E18-B)*

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
- [x] Opening "Tu plan" or "Tu bloque" shows its content at once (no skeleton) when the Trainer is already loaded; state survives leaving the app.
- [x] The Trainer open issues the independent reads in parallel; a host test asserts the sequential depth dropped and the evaluate/apply order is unchanged.
- [x] Behaviour and figures shown are identical to today; `check-ux-conventions.sh` passes; lint + tests green.
**Dependencies:** T-269 merged. Then the Trainer open and the two boxes are re-presented; their approval releases T-271.

---

## Text meals and food cleanup (US-132, US-133, ADR-105) *(epic-planner, 2026-11-10)*

Order: T-270 → Trainer re-test approved → **T-271** → **T-272** → **👤 Checkpoint E18-B** → T-267 (closure). Why: the backlog note asked for these stories after the Checkpoint E18 path and before the closure, so the open re-test stays small and is not mixed with new screens; US-132 goes first because US-133's "I can create or describe it again" uses it, and the cleanup also removes the foods typed meals create; the checkpoint follows the last implementation task, with the closure after it. One checkpoint for both stories (user decision 2026-11-10). If T-272's open questions are answered before T-271's PR is ready nothing changes; if they are still open when T-271 merges, the PM asks them before planning T-272.

## T-271: Log a meal by typing it: `food-photo` text mode and "Describir comida" (US-132) · M

**Story:** US-132 · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** M
**Scenarios:** US-132 Describe a meal; Drinks count; The draft is an estimate and editable; The energy check still applies; Saving; No answer; Empty or unusable text; Privacy; plus "leave the app and come back with the text typed and with the draft open".
**Description:** **User decision (2026-11-10): reuse `food-photo` and its dish flow; no new function, no new schema file, no new prompt file.** **Backend (`food-photo`):** a `text` mode that is the dish flow with the typed sentence as the main input and no image: `image_base64` becomes optional for `text` only (still required for `dish`, `label`, `barcode`, `weight`); the comment limit (`MAX_COMMENT_CHARS = 200`) is raised for `text` only (new cap chosen by the planner, enforced in the existing schema); `previous` (the ADR-104 retry) is accepted for `text` as for `dish`. The dish prompt gains one sentence for this mode: the user text describes what was eaten; list the portion assumptions in the name or note; Gemini estimates the quantities. The response is the existing dish draft (name, nutrition per 100 g, `estimated_grams` for the whole meal, `alcohol_g`, confidence) with the existing energy-check `issue`; a text with no food gives the existing `422 NO_FOOD_RECOGNIZED`; an empty text is `400 INVALID_INPUT`. The text is neither stored nor logged (same rule as the image, ADR-59). `food-photo` redeployed. **Core:** the existing food-photo repository call gains the text mode (no image); the input ViewModel keeps the typed text in `SavedStateHandle` (restored after background and process death, ADR-40) and on every error; offline detected before sending. **UI — templates and patterns (user decision 2026-11-10; no new button):** in "Añadir alimento" the photo icon next to the barcode icon is replaced by an AI icon (icon only, no visible text, like the barcode icon; "Añadir con IA" is only its contentDescription; ux-reviewer picks the icon, AI/sparkle style, and the testTag) that opens the existing mode sheet; the sheet's title changes from "Añadir desde foto" to "Añadir con IA", and it keeps its three options ("Estimar con IA", "Etiqueta nutricional", "Código de barras") plus a fourth, "Describir comida", same row pattern as the other three (R-39 sweep: every place that shows the old icon, title or strings is updated; test tags defined by ux-reviewer, and every test using the old photo icon tag is updated); `05` line about the photo action updated. The text input uses the archetype ux-reviewer picks in the plan (Form sheet or pushed `AppScreen`, R-06/R-07) with a multi-line `AppTextField`, the primary action pinned (copy by ux-reviewer, glossary §10), progress per R-31; no answer or offline: the standard message with "Reintentar" (R-30/R-37) and the text kept; empty text or no food: told so, nothing created. The draft opens in the existing AI dish draft form (`CreateFoodScreen` AI mode): marked as an estimate, name, amounts and nutrients editable, `EnergyWarningNotice` with "Guardar" blocked until fixed or retried (T-258 flow), one portion = the whole meal (`estimated_grams`); saving uses the existing create-personal-food + add-to-meal path, so the food is in later searches. The photo dish flow is unchanged. Docs: `03` §7 `food-photo` row; `05` the photo-action line ("Añadir con IA" icon and sheet), §8/§10 ("Añadir con IA", "Describir comida").
**Acceptance criteria:**
- [x] `food-photo` accepts `{mode: "text", comment}` without an image; every other mode still requires one; the higher comment cap applies to `text` only; the dish prompt carries the new sentence only in text mode.
- [x] Typing the Gherkin sentence gives one editable draft whose name lists everything typed, with total kcal and macros (alcohol included for drinks) and one portion equal to the whole meal, marked as an estimate.
- [x] A disagreeing draft blocks "Guardar" until fixed or retried; saving adds the entry to that comida and creates a personal food found by later searches.
- [x] Offline or no answer: message with "Reintentar", text kept; empty or no-food text: told so, nothing created; the text is not stored or logged.
- [x] Leaving the app and coming back (and process death) with the text typed or the draft open restores it; `check-ux-conventions.sh` passes.
**Host tests:** Deno (`food-photo` handler and schema: text without image accepted, other modes without image rejected, per-mode comment cap, `previous` in text mode, energy-check issue, `NO_FOOD_RECOGNIZED`, empty text, the prompt sentence present only in text mode, no logging of the text); repository/DTO (body has `mode: text` and no image); ViewModel (send, draft, issue, retry with previous, offline, no answer keeps the text, empty, no food, save creates the food and the entry, `SavedStateHandle` restore); Robolectric (the AI icon, with no visible text and contentDescription "Añadir con IA", replaces the photo icon beside the barcode icon, the sheet titled "Añadir con IA" with four options incl. "Describir comida", the three existing options unchanged, input, progress, error with "Reintentar", draft marked as estimate, "Guardar" blocked, restoration with `StateRestorationTester`).
**Verification:**
- [x] `deno test --allow-all supabase/functions` + lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with `food-photo` redeployed.
**Agent runs:** 8. About 13 files.
**Dependencies:** T-270 merged and the Trainer re-test approved.
**Files likely touched:** `supabase/functions/food-photo/{schema,handler,prompts}.ts` (+ `schema.test.ts`, `handler.test.ts`), the shared food-photo repository and DTO, `feature/food/{AddFoodSheet,AddFoodUiState,AddFoodViewModel,CreateFoodViewModel,CreateFoodScreen,FoodRoots}.kt` (+ a text-input screen or sheet), `app/{AppRoutes,AppNavHost}.kt`, `strings.xml`, `planning/03-data-model.md` §7, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-272: Unused personal foods clean themselves up (US-133) · M (upper end)

**Story:** US-133 · **Flow:** planner → backend-developer → kotlin-core-developer → compose-ui-developer only if the entry details screen changes (then ux-reviewer plan and diff) → pr-reviewer → pr-creator · **Size:** M (upper end; one vertical slice: backend about 7 files, app about 5; split backend → app only if the planner counts more than ~15 files)
**Scenarios:** US-133 A food unused for a month is removed; Used, favourite or grouped foods stay; Past days do not change; A removed food is gone from search; It needs no open app or secret; Other accounts are untouched.
**Description:** **User decisions (`tasks/plan.md` "Open questions"): Q-E18-3 the first run is at deploy, Q-E18-4 the 30 days count from the later of the food's creation and its newest entry's creation time, Q-E18-5 an entry whose food was removed is view and delete only.** **Planning precondition (planner, before any code):** confirm from Supabase's current docs that `pg_cron` (Supabase Cron) is available on the free plan and can be enabled from a migration, and that it runs in the local stack (`supabase db reset`); the job runs SQL inside the database, so there is no GitHub Actions workflow, no Edge Function call and no secret in any versioned file (ADR-105, E20). If `pg_cron` is not available on the free plan, stop and report BLOCKED to the user (no fallback is chosen here). **Migration:** `food_log_entries.food_id` must survive a food delete: the value is kept (the `on delete no action` FK to `foods` is dropped; no `set null`, which would lose the value); snapshots (name, amount, portion label, kcal, macros) are never touched; the planner checks the RLS insert/update policies and every reader that joins `foods` (`food_diary_summary`, `most_eaten_foods`, `personal_food_in_use`, `cooking_impact`) so none drops or changes an entry whose food is gone. A cleanup function (e.g. `cleanup_unused_personal_foods(p_now timestamptz default now()) -> int`, security definer, `search_path = ''`, execute revoked from `public`, `anon` and `authenticated`) deletes `source = 'user'` foods (Open Food Facts copies included; OFF cache rows untouched) whose owner has added none to the diary in the last 30 days (clock per Q-E18-4), that are not in the owner's `favorite_foods` and in no `meal_template_items` of the owner; each food is judged only on its owner's rows; `food_portions` go by cascade. `pg_cron` schedules it monthly (ADR-105) under a fixed job name; the migration calls the function once when applied to production (Q-E18-3), then the schedule takes over. **App (core):** opening a diary entry whose food is gone shows its details from the snapshot (no "No se pudo cargar"); what else the entry allows (amount edit, copy or repeat to another day, save as comida) is view and delete only (Q-E18-5: no amount edit, no repeat or copy, "save as comida" skips it); search needs no change (the food is gone). Docs: `03` §3 (FK change, the function, the job), `04` (the cleanup rule), `08-environment-setup.md` (`pg_cron` enabled), `02` (scheduled job).
**Acceptance criteria:**
- [ ] A personal food unused for 30 days, not a favourite and in no saved comida is deleted by the function; a food used within 30 days, a favourite, a food in a saved comida and every OFF cache row stay.
- [ ] For every day that held a deleted food, the per-day totals (`food_diary_summary`), entry count, names, amounts and `food_id` values are equal before and after the cleanup.
- [ ] A deleted food is not returned by `search_user_food_ids` or `food-search`; a new food with the same name can be created.
- [ ] One account's cleanup never deletes or keeps another account's food because of the other account's rows.
- [ ] `cron.job` holds the monthly job; no client can execute the function; no secret or Actions workflow is added.
- [ ] An entry whose food is gone opens with its details from the snapshot (host test).
**Host tests:** pgTAP `cleanup_unused_personal_foods.test.sql` (each kept and deleted case with a fixed `p_now`, the 29/30/31-day boundary per Q-E18-4, OFF copies deleted and OFF cache kept, per-day totals and entries identical before and after, `food_id` kept, two users, search no longer finds it, function not executable by `authenticated`/`anon`, the job exists in `cron.job`); `food_log_entries.test.sql` and `delete_account_cascade.test.sql` updated for the FK change; app: repository/ViewModel tests (entry with a missing food opens from the snapshot and offers view and delete only), Robolectric only if the details screen changes.
**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` + lint + `testLocalDebugUnitTest` green.
- [ ] Before the merge, a subagent runs a read-only count on production of the foods the first run would delete (per account) and the PM shows it to the user for information (Q-E18-3: the first run is at deploy, no extra approval).
- [ ] After the merge `deploy-supabase` green on `main` with data intact; a subagent confirms read-only that `cron.job` lists the job on production.
**Agent runs:** 6-8. About 12 files.
**Dependencies:** T-271 merged and deployed.
**Files likely touched:** `supabase/migrations/<ts>_cleanup_unused_personal_foods.sql`, `supabase/tests/{cleanup_unused_personal_foods,food_log_entries,delete_account_cascade}.test.sql`, `shared/…/data/repository/{SupabaseFoodDiaryRepository,SupabaseFoodRepository}.kt`, the diary entry details ViewModel/screen, tests, `planning/{02-architecture,03-data-model,04-business-rules,08-environment-setup}.md`
**Estimated scope:** M

**Status 2026-11-10:** T-272 merged (PR #629, CI green, `deploy-supabase` green). Review found it deviates from the spec: the FK was `on delete set null` (the spec said keep `food_id`), and two verification items were skipped (read-only pre-merge count shown to the user; read-only `cron.job` check on production). The first cleanup already ran on production at deploy. User decision 2026-11-10: the storage saved is negligible (about 100 KB a year for 100 foods), so the automatic cleanup is dropped → T-273.

## T-273: Revert the unused-food cleanup (T-272), keep the read-only entry sheet · S

**Story:** user decision 2026-11-10 (no new US; US-133 is withdrawn) · **Flow:** planner → backend-developer → pr-reviewer → pr-creator (no UI change expected; the planner confirms) · **Size:** S
**Description:** Revert PR #629's cleanup as a revert PR (`git revert` of the squash commit), with one constraint: migrations `20270125100000` and `20270125100100` are already applied on production, so their files must NOT be deleted (that would break the `deploy-supabase` migration history). The planner decides the minimal safe form: a new migration that unschedules the `cleanup-unused-foods` job and drops `cleanup_unused_foods()`, while `food_log_entries.food_id` stays nullable (entries may already hold null; harmless), and the read-only edit sheet for an entry whose food is gone stays as a safeguard. Update `planning/03`, `08`, US-133/ADR-105/E18 to say the cleanup is dropped (no new ADR text beyond marking ADR-105's cleanup part superseded by this user decision; the planner proposes the wording). Remove the pgTAP file for the dropped function; adjust the tests that expect it.
**Acceptance criteria:**
- [x] No `cron.job` row `cleanup-unused-foods` and no `cleanup_unused_foods()` function after the migrations run; no job runs on production.
- [x] Diary entries, totals and the read-only sheet behave as before; `most_eaten_foods` still ignores entries without a food.
- [x] Docs and US-133 mark the cleanup as dropped.
- [x] After the merge `deploy-supabase` green on `main`; a subagent confirms read-only that production `cron.job` has no such job.
**Dependencies:** T-272 merged (done). Checkpoint E18-B (typed meal only) is presented after T-273.

**Status 2026-11-10:** T-273 merged (PR #631, CI green, `deploy-supabase` green). Production verified read-only by the user: no `cleanup-unused-foods` job, no `cleanup_unused_foods()`, 669 entries, 0 without a food. **Next: 👤 Checkpoint E18-B**, then T-267.

---

### Checkpoint E18-B — A meal from typed text 👤 (5 items, leave and come back included) *(epic-planner, 2026-11-10; the only checkpoint for US-132, user decision 2026-11-10; the cleanup items were dropped with US-133 by T-273; its approval releases T-267)*

After T-273 merges (the last implementation task), CI green, `deploy-supabase` green on `main` with data intact (production checked read-only by the user: no cleanup job, no cleanup function, 669 entries, 0 without a food) and `food-photo` redeployed; install the new APK right after. Main account. What to try on the phone (`python scripts/deliver-apk.py`):

*Meal from typed text (T-271):*
- [x] 1. Diario → add food to a comida: the AI icon next to the barcode icon (an icon only, no text) opens a sheet "Añadir con IA" with "Estimar con IA", "Etiqueta nutricional", "Código de barras" and "Describir comida"; choose "Describir comida" and type "pechuga de pollo con ensalada, 2 cervezas y 4 calamares": one draft whose name lists everything, total kcal and macros with alcohol from the beers, one portion = the whole meal, marked as an estimate.
- [x] 2. Change the name, the amount and a nutrient; make the kcal disagree with the macros: the issue shows and "Guardar" is blocked until you fix it or retry.
- [x] 3. Save: the entry is in that comida, and searching its name later finds it as your food.
- [x] 4. Airplane mode, then describe a meal: a clear message with "Reintentar" and your text still there; send an empty text and "hola": you are told so and nothing is created.

*Leave and come back (final regression of these stories):*
- [x] 5. Leave the app and come back (and after the system kills it) with a text typed in "Describir comida" and with the draft open: the same tab, screen and draft are restored; adding by search and by photo still works as before.
Out of scope here (pgTAP, Deno and host tests only): the text is not stored or logged; another account's foods are untouched.

**Approved by the user 2026-11-10 (all 5 items).** Next: T-267 (E18 closure), in a fresh session. *(Superseded the same day by ADR-106: next is T-274, then T-275, Checkpoint E18-C and T-267.)*

---

## Extend the block and food form corrections (US-134, US-135, ADR-106) *(epic-planner, 2026-11-10)*

Order: Checkpoint E18-B (approved) → **T-274** → **T-275** → **👤 Checkpoint E18-C** (proposed) → T-267 (closure). Why: both stories were added after Checkpoint E18-B was approved and before the closure, so they go between them. US-134 goes first because it is the larger, riskier change (a production migration that moves the block calendar); it deploys early and T-275 runs while it settles, so any calendar regression is visible before the checkpoint. US-135 is app-only and independent. One checkpoint after the last of the two (user decision 2026-11-10). Neither story needs `fitness-scientist`: US-134 changes no load, deload or calorie rule, and US-135 uses the factor already in `07` §4.

## T-274: Extend my block: a new last day from the Trainer (US-134) · L

**Story:** US-134 · **Flow:** planner → ux-reviewer (plan) → backend-developer → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator · **Size:** L (one vertical slice: backend about 8 files, app about 7; the app part alone would be S, so it is not split; split backend → app with a new ID only if the planner counts clearly more than ~15 files)
**Scenarios:** US-134 Choosing the new last day; A session on the last day counts; My sessions stay to do; Later weeks and schedules move too; Changing my mind; The extension survives leaving the app; No extension without a block.
**Description:** **Rule (ADR-106, `04` §9.9):** the user picks the new last day of the running block; the difference `N` in days from the block's current computed end (`coach_block_end`, which already includes paused weeks) is added to the plan week that holds today: its window becomes `7 + N` days long, every later plan week, the end-of-block review and the block-end notice move `N` days later, and windows already past never change (no back-dating). A later pick replaces the earlier one ("Changing my mind": picking the original day restores `N = 0`). Nothing else changes: plan sessions, links, loads, deloads (week numbers stay plan weeks), calories and the diet break are untouched (ADR-106 non-goals: no pause, no back-dating). **Proposed limits (flag, needs the user's OK before planning, see `tasks/plan.md` "Open questions" Q-E18-6): at most 14 days later than the end the block would have without any extension (the 14-day return rule of `04` §9.9 is the only break rule, ADR-101, so a longer extension would overlap it); the earliest day is the later of today and that unextended end, so the picker cannot shorten the block below its own end or end it in the past; the pencil shows only while the block is running (up to its last day), not once the review is due.** **Backend:** a migration that stores the extension per plan (the planner picks the form: for example the plan week it applies to and `N`, on `coach_plans` or a small owner-scoped table with RLS) and a writer RPC (for example `set_coach_block_end(p_plan_id, p_last_day date)`, security definer, `search_path = ''`, owner check, `22023` outside the limits or without an accepted plan with a saved block); every reader that does the 7-day window arithmetic uses the extended windows: the planner lists them (at least `coach_plan_weeks`, `coach_block_end`, `coach_plan_week`, `coach_plan_calendar`, the link trigger `coach_link_plan_session`, `coach_block_counted_weeks`, `open_coach_block_review`, `open_coach_block_new`, the block-end notice, the weekly summary week, `evaluate_coach_deload`'s week-4 check and §9.16's "after the last day" guards) and keeps one shared helper so the arithmetic lives in one place. **Core:** the Trainer repository gains the writer call; the ViewModel validates the limits, sends the pick, re-reads the block and keeps the dialog state and a pending pick in `SavedStateHandle` (ADR-40); offline detected before sending. **UI — templates and patterns:** in the Trainer's "Tu bloque" box/screen (T-268), an `AppIconButton` pencil (tooltip and contentDescription by ux-reviewer, e.g. "Cambiar el último día del bloque"; testTag by ux-reviewer) right after the block end day; it opens the existing `AppDatePickerDialog` with the allowed range only (days outside it disabled); on confirm the end day updates in place (no extra confirmation dialog, no snackbar unless ux-reviewer requires one per R-31); the week list and the review date show the moved dates. No pencil without an active block. No new designsystem component. Docs: `04` §9.9 and §9.15 (the extension rule, limits, readers), `03` §3 (the stored extension and RPC), `05` §10 glossary if ux-reviewer adds a term.
**Acceptance criteria:**
- [ ] Picking a later last day shows it as the block end in the Trainer; a session completed on that day is linked to the extended current week and counts in its adherence and in the block.
- [ ] The current week's sessions not done stay to do; every later week, the review and the block-end notice move by the same `N` days; past weeks keep their dates and adherence.
- [ ] Picking the original day again gives the original dates; picks outside the limits are refused by the picker and by the RPC.
- [ ] No pencil and no writer access without an accepted plan with a saved block; another account's plan cannot be changed (RLS/owner check).
- [ ] Loads, deloads, calories and plan sessions are unchanged by an extension (pgTAP compares them before and after).
- [ ] Leaving the app and coming back (and process death) with the picker open or after a pick shows the same state; `check-ux-conventions.sh` passes.
**Host tests:** pgTAP `coach_block_extension.test.sql` (extend in the last week and in an earlier week; `coach_plan_weeks` dates and adherence; `coach_block_end`; a workout on the new last day linked and counted; a workout on the day after not linked; later weeks shifted by `N`; past weeks unchanged; re-pick to the original; limits refused with `22023`; paused weeks plus an extension; no block; another user refused; deload, sessions and nutrition goals unchanged; review and block-end notice dates moved), existing `coach_plan_weeks*`, `coach_block_review*`, `coach_deload*` tests still green; repository/DTO test; ViewModel tests (pick, re-pick, limits, offline, error, `SavedStateHandle` restore); Robolectric (pencil present with a block and absent without, picker range, end day updated, restoration with `StateRestorationTester`).
**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` + lint + `testLocalDebugUnitTest` green; after the merge `deploy-supabase` green on `main` with data intact (the current block's dates unchanged until the user picks a day).
- [ ] The E18-B APK still reads the Trainer against the new backend (the change is additive; with no extension stored every reader returns what it returned before).
**Agent runs:** 8. About 15 files.
**Dependencies:** Checkpoint E18-B approved (done); Q-E18-6 (limits) confirmed by the user.
**Files likely touched:** `supabase/migrations/<ts>_coach_block_extension.sql`, `supabase/tests/{coach_block_extension,coach_plan_weeks_pause}.test.sql` (+ any `coach_*` test the planner finds), the shared Trainer/coach repository and DTO, `feature/trainer/{TrainerViewModel,TrainerUiState}.kt` and the "Tu bloque" screen/box (T-268), `strings.xml`, tests, `planning/{03-data-model,04-business-rules}.md`
**Estimated scope:** L

---

## T-275: Food form corrections: the "Describir comida" example and Alcohol by volume (US-135) · M

**Story:** US-135 · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer ∥ ux-reviewer (diff) → pr-creator (no backend: the stored value stays grams per 100 g/ml) · **Size:** M
**Scenarios:** US-135 A simple example in "Qué has comido"; Alcohol lives in its own section; Alcohol by volume, no formula; A food measured in grams.
**Description:** **Example:** the placeholder/example of the "Describir comida" input (T-271) becomes "Ej.: pechuga de pollo con patatas fritas" (string change only). **Alcohol section:** in the create/edit food form (`CreateFoodScreen`, every mode that shows the nutrient fields: manual create, edit, the AI draft and an OFF copy edit; the planner confirms the list) "Alcohol" leaves the macro rows between "Fibra" and "Proteínas" and becomes a new collapsible section after "Minerales", built with the existing `SectionBlock` pattern of `CreateFoodSections.kt` (same toggle, "filled" count and testTag scheme, `create_food_section_alcohol_*`), collapsed by default and expanded when the food already has alcohol (ux-reviewer confirms). **Input by unit:** for a food whose base unit is ml the section offers grams per 100 ml or "grados" (% vol) through a `SegmentedSelector` (ux-reviewer picks the labels, e.g. "g/100 ml" / "Grados (% vol)"); grados are converted to grams with the factor already in `07` §4 (`alcohol_g_100 = %vol × 0.789`, the same as the OFF import), the converted grams are shown under the field ("= 3,9 g por 100 ml", copy by ux-reviewer, number via `AppNumberFormat`), and only grams are stored and fed to the energy check (ADR-104, so a grados entry updates the check live). For a g food only grams are offered (no selector). The help (`HelpButton`) explains what to type without any formula: the current line "Si solo sabes los grados: alcohol (g) por 100 ml = grados × 0,79." is replaced (copy by ux-reviewer, glossary §10). An edited food always opens in grams. The unit choice, the typed value and the section's expanded state survive leaving the app (`SavedStateHandle`, ADR-40). The AI estimate still returns grams (ADR-106, no `food-photo` change). **Shared helper:** the % vol → g conversion lives in one shared function used by the form; the planner checks whether the OFF import's Kotlin side already has one and reuses it (one place for the factor). Docs: `07` §4 "Manual entry and edit" bullet (grams or grados for ml foods, converted with the same factor; the help line without a formula; the factor itself unchanged), `05` §8/§10 (the Alcohol section and the grados wording).
**Acceptance criteria:**
- [x] "Describir comida" shows "Ej.: pechuga de pollo con patatas fritas".
- [x] In every create/edit food form "Alcohol" is not between "Fibra" and "Proteínas" and is a collapsible section below "Minerales".
- [x] An ml food accepts grams or grados; grados show the converted grams, the stored value is grams (`%vol × 0.789`), and the energy check uses it; the help states no formula.
- [x] A g food accepts grams only.
- [x] Leaving the app and coming back (and process death) with the section open and grados typed restores them; `check-ux-conventions.sh` passes.
**Host tests:** unit tests for the conversion (e.g. 5 % vol → 3,9 g, 40 % vol → 31,6 g, 0, empty, rounding as the planner fixes it); ViewModel tests (unit selector shown only for ml, grados converted and stored as grams, energy check fed with the grams, editing a food opens in grams, `SavedStateHandle` restore); Robolectric (Alcohol not among the macros, section below "Minerales" collapsible, selector only for ml, converted line, help text without a formula, the new example in "Describir comida", restoration with `StateRestorationTester`).
**Verification:**
- [x] lint + `testLocalDebugUnitTest` green (`scripts/verify-local.sh`; `--full` only if the planner touches the designsystem).
**Agent runs:** 7. About 10 files.
**Dependencies:** T-274 merged (no build dependency; the order keeps one task at a time).
**Files likely touched:** `feature/food/{CreateFoodScreen,CreateFoodSections,CreateFoodUiState,CreateFoodViewModel}.kt`, the "Describir comida" input screen or sheet (T-271), a shared alcohol conversion helper (or the OFF import's existing one), `strings.xml`, tests, `planning/07-coach-design.md` §4, `planning/05-design-system.md` §8/§10
**Estimated scope:** M

---

### Checkpoint E18-C — Extend the block and the food form corrections 👤 (7 items, leave and come back included) *(PROPOSED by the epic-planner, 2026-11-10; needs the user's OK; the only checkpoint for US-134 and US-135, user decision 2026-11-10; its approval releases T-267)*

After T-275 merges (the last implementation task), CI green, `deploy-supabase` green on `main` after T-274 with data intact (the block's dates unchanged until a day is picked); install the new APK right after. Main account (the block extension is undone in item 3, so the real block ends where it did). What to try on the phone (`python scripts/deliver-apk.py`):

*Extend the block (T-274):*
- [ ] 1. Entrenador → "Tu bloque": a pencil sits right after the block's end day; tap it: a date picker opens that only lets you pick from the current end (or today) up to 14 days later (per Q-E18-6).
- [ ] 2. Pick a day 3 days later: the block now ends on that day, and the later weeks and the review date are 3 days later; this week's sessions you have not done are still to do; your loads, deloads and calories are as before.
- [ ] 3. Tap the pencil again and pick the original day: every date is back to what it was.

*Food form corrections (T-275):*
- [x] 4. Diario → add food → "Añadir con IA" → "Describir comida": the example reads "Ej.: pechuga de pollo con patatas fritas".
- [x] 5. Create a food, and edit an existing one: "Alcohol" is not between "Fibra" and "Proteínas"; it is a collapsible section below "Minerales".
- [x] 6. A food measured in ml: in Alcohol choose grados and type 5: the grams per 100 ml appear, the help asks you to calculate nothing, and after saving and reopening it shows grams; a food measured in g only takes grams.

*Leave and come back (final E18 regression of these stories):*
- [ ] 7. Leave the app and come back (and after the system kills it) with the date picker open, after extending, and with the Alcohol section open and grados typed: the same tab, screen and values are restored; the Trainer, "Describir comida", photo and search adding still work as before.
Out of scope here (pgTAP and host tests only): a session on the new last day counting in the week; past weeks never changing; another account's block; refusals outside the limits by the server.

**Checkpoint E18-C feedback (user, 2026-11-11):**
- **Food form corrections: approved** (items 4-6 and the food part of item 7).
- **Extend the block: rejected** (items 1-3 and the block part of item 7). The user's words: "the block extension is not what I asked. I asked to extend the current week of the block, not a block. Why would I want to extend a block when its first week is in progress? The pencil should go to the active week block. Change this. Also, remove text added 'El bloque termina el 3 de diciembre' if that was added in the last change." Checked by the PM: the string `trainer_block_end` is from T-242 and was shown only once a week had been paused; T-274 made it always visible (`PlanWeeksSection.BlockPauseLines`), so for the user it is new and it goes back to the T-242 condition. → `fix(T-276)`; then only items 1-3 and the block part of item 7 are re-presented, rewritten for the week.

---

## T-276: fix(T-276) The pencil extends the current week, not the block · fix (core + UI)

**Story:** Checkpoint E18-C feedback (US-134 rework) · **Flow:** planner → ux-reviewer (plan) → kotlin-core-developer → compose-ui-developer → pr-reviewer + ux-reviewer (diff) → pr-creator (backend-developer only if the planner shows the existing RPC cannot express the pick).
**Description:** T-274 put the pencil next to "El bloque termina el …" and its picker chose the block's last day. The user wants to extend the week in progress: the pencil sits on the active week (the week marked "Actual" in "Semanas del bloque"; exact spot by ux-reviewer) and the picker chooses that week's new last day, from its end without any extension up to 14 days later (Q-E18-6 unchanged). The rule underneath does not change (ADR-106, `04` §9.9: `N` days are added to the plan week that holds today, and every later week, the review and the block-end notice move `N` days), so the planner first checks that the existing `set_coach_block_end` can be called with the block end moved by the same `N` (an app-only fix with no migration is preferred). Picking the week's original last day restores every date. Copy, help and contentDescription speak of the week, not the block (ux-reviewer; glossary §10). "El bloque termina el …" goes back to the T-242 condition (shown only once a week was paused). Same pass, every layer: ADR-106, US-134 (text and Gherkin), `04` §9.9, `05` §8/§10, strings, comments, tests.
**Acceptance criteria:**
- [x] The pencil is on the active week and no longer next to the block end day; its picker picks that week's last day within the limits; the week's dates, the later weeks and the review move by the same days; picking the original day restores every date.
- [x] "El bloque termina el …" is shown only once a week was paused, as before T-274.
- [x] No pencil without an active block or once the block is over; the picker open and a pick survive leaving the app (ADR-40); host tests cover it; `check-ux-conventions.sh` passes; lint + tests green.
**Dependencies:** none (T-275 merged). Then Checkpoint E18-C items 1-3 and the block part of item 7 are re-presented; their approval releases T-267.
**Merged:** PR #640 (2026-11-11, CI green). App only: the app sends the block end moved by the same days as the week to the existing `set_coach_block_end`. Decisions recorded in the PR: the 14-day limit stays a total for the block (Q-E18-6), so a week extended after an earlier extension gets fewer days and the help says so; the pencil and "?" sit at the end of the date row of the "Actual" week card. Follow-up (pr-reviewer, non-blocking): a one-line comment at `ActiveWeekExtension.kt:46-47` on the `minDay` assumption.

### Checkpoint E18-C re-test — Extend the current week 👤 (4 items, leave and come back included) *(replaces items 1-3 and the block part of item 7 above; items 4-6 are approved; its approval releases T-267)*

After T-276 merges, CI green (no backend change, no deploy). Main account (the extension is undone in item 3, so the real block ends where it did). What to try on the phone (`python scripts/deliver-apk.py`):
- [x] 1. Entrenador → "Tu bloque" → "Semanas del bloque": the week marked "Actual" has a "?" and a pencil at the end of its date row; no other week has them, and there is no "El bloque termina el …" line (it shows only after a paused week). Tap the pencil: the date picker starts on the week's last day and lets you pick up to 14 days later.
- [x] 2. Pick a day 3 days later: the current week now ends on that day, and the later weeks and the review date are 3 days later; this week's sessions you have not done are still to do; your loads, deloads and calories are as before.
- [x] 3. Tap the pencil again and pick the week's original last day: every date is back to what it was.
- [x] 4. Leave the app and come back (and after the system kills it) with the date picker open and after extending: the same screen and dates are restored.
Out of scope here (pgTAP and host tests only): a session on the new last day counting in the week; past weeks never changing; another account's block; refusals outside the limits by the server; the smaller range left after an earlier extension.

---

## T-267: E18 closure → `v1.x` · closure step (PM only, not a developer task)

**Story:** E18 Definition of Done · **Flow:** PM closure step after Checkpoint E18 is approved (its approval releases this step; no checkpoint of its own). No developer agent, no size class; it cannot merge into a task because it waits for the checkpoint approval.
**Description:** Version bump (`versionName`/`versionCode`, minor per epic, proposed `1.9.0`), tag, retrospective `retrospectives/E18.md`, archive of the E18 log lines; `09-open-questions.md` records Q1, Q2 (user, 2026-11-09) and the Gemini terms decision with their answers; `01-scope.md` E18 row marked done; the epic status set to done.
**Acceptance criteria:**
- [x] T-259…T-266 (and any fix tasks) merged with green CI; `deploy-supabase` green after each migration and function change.
- [x] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [x] `00`, `02`, `03`, `04`, `05`, `07`, `phase2/01-scope.md` NFR-02 and the epic file are current; Checkpoint E18 approved by the user.
- [x] Version bumped; `tasks/todo.md` pointer updated through a PR.
**Verification:**
- [x] `git tag` lists the E18 tag.
**Dependencies:** Checkpoint E18 approved (with the T-268…T-270 re-test) and Checkpoint E18-B approved (T-271, T-272 merged); since ADR-106 (2026-11-10) also T-274, T-275 merged and Checkpoint E18-C approved; since 2026-11-11 also T-276 merged and the Checkpoint E18-C re-test (block items) approved.
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
