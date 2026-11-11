# T-277 · Remove everything unused: free-text notes and all dead code, every layer (US-123)

Step 2/3: removal plan. Inputs: `tasks/todo.md` T-277 (incl. "User decisions on the audit"), `.workflow/audit_backend.md` (B#), `.workflow/audit_kotlin.md` (A#, S#, W#, D#), `.workflow/feature_inventory*_draft.md`. Mostly deletions; no new UI.

## Goal
Remove the free-text notes (storage, readers, screens, strings), `weekly_check_ins.notes` and the check-in note field, every dead RPC/helper/Kotlin item/string/drawable the audit found, then document it (ADR-107) and publish `planning/10-feature-inventory.md`. Behaviour of every remaining screen is unchanged.

## Gherkin scenarios covered
US-123 "Behaviour does not change"; "A decision becomes a rule" (no copy claims the coach reads something it does not).

## Flow type: full UI (navigation, DI, designsystem may change) + backend. `verify-local.sh --full`.

## SIZE FLAG (PM decision needed)
Real-edit count is about 90 files: migration 1 + pgTAP ~12 + TS 1 + Kotlin main ~40 (6 deleted, ~34 edited) + Kotlin/host tests ~28 (4 deleted) + strings/drawable 2 + scripts/CI 3 + docs ~12. That is far over ~30. By the task's own rule, recommend splitting into three tasks with new IDs, in this order (it also shrinks the old-app risk, because the new app stops calling dropped objects before they are dropped):
- **T-277a (app-side, S2-S4 below)**: Kotlin, strings, drawable, Kotlin tests, resource check, Python-test wiring. Can merge and be installed first.
- **T-277b (backend, S1)**: migration (last commit), pgTAP, Edge test, 03/04 docs, ADR-107 backend list. After it merges, `deploy-supabase` green, data intact.
- **T-277c (docs)**: ADR-107, US-81/89, 05, 10-feature-inventory, README link. Needs a and b merged to list only what remains.
If the PM keeps one PR (user decision), implement in the slice order below; the migration is still the last commit.

## Changes

### S1 Backend (backend-developer). One new migration `supabase/migrations/20270127100000_remove_unused.sql` (check the next free timestamp after `20270126100000`)
Order inside the file, in one transaction:
1. Snapshot row counts of every table not dropped (a `pg_temp` table or `do` block variable), plus `count(*) filter (where btrim(notes) <> '')` of `weekly_check_ins` and `count(*)` of `coach_free_text_exclusions`; `raise notice` both (the self-check must REPORT non-empty notes removed).
2. Scrub: `update public.coach_recommendations set input_summary = input_summary - 'free_text' where input_summary ? 'free_text'`. Developer also greps a nested key: after the update assert `count(*) where input_summary::text like '%free_text%'` = 0, else extend the scrub (e.g. `coach_plan_inputs` entries with `key = 'free_text'`) before raising. This UPDATE must not fire `updated_at` side effects that change counts (count only).
3. Recreate (full `create or replace`, body copied from the live definition via `db reset` + `pg_get_functiondef`, not from old migration text, since 20270110/20270126 used `pg_temp.patch_fn`): `coach_summary(date)` without the `free_text` key (last definition `20261219100300`, only user of `coach_free_text_lines`), `coach_plan_inputs(text,date)` without the two `coach_input_entry('free_text', ...)` lines (last `20270109100000:111`), `save_coach_intake(jsonb)` ignoring a `free_text` key and not returning it (last `20261221100000:19`; drop the length validation on it). Re-issue `revoke ... from public, anon` and `grant ... to authenticated` after each (R-02 pattern of the repo).
4. Drop: functions `coach_free_text_lines(date)`, `set_free_text_line_excluded(text,text,boolean)`, `save_meal_slots(jsonb)`, `delete_meal_slot(uuid)`, `coach_plan_week_of_date(uuid,date)`, `assert_unique_meal_labels`, `convert_used_off_foods()`, `coach_rederive_plan_links(uuid)`, `migrate_equipment_list` (look up exact signatures in their migrations); table `coach_free_text_exclusions`; columns `coach_user_intake.free_text` (+ constraint `coach_user_intake_text_length`) and `weekly_check_ins.notes` (+ `weekly_check_ins_notes_length`). `exercise_discomforts.note` STAYS.
   Verification results of the three "planner checks" (decide with evidence):
   - `off_alcohol_g`: called by `20270116100000:364-368` (backfill, ran once) and `food_energy_check` is said to use it ("stay unchanged", `20270121100000:4`): the developer greps the LIVE bodies (`pg_get_functiondef` of `food_energy_check`, `copy_off_food_core`, food RPCs) for `off_alcohol_g`; drop only if no live body calls it, else keep (it is then used). Same for pgTAP `food_energy_check.test.sql` (10 refs): remove calls to it if dropped.
   - `coach_plan_calendar(uuid,date)`: its only SQL caller `coach_plan_weeks` was recreated in `20270126100000:346` using `coach_plan_calendar_for`; no app/TS caller. Drop it (and its pgTAP lines in `coach_plan_weeks_pause.test.sql:87,172,328`; rewrite the helper at :87 onto `coach_plan_calendar_for` or delete the assertions that need it). Confirm against the live bodies after `db reset` (grep `pg_proc.prosrc`) before dropping.
   - `copy_off_food_core`: called by `copy_off_food` (live, app calls it). KEEP. Because `convert_used_off_foods` is dropped, the core's `p_enforce` default param stays as is (do not touch).
   - Before each drop, a `do` block checks `pg_depend`/`prosrc ilike '%name(%'` over `public` functions and raises if a live function still references it (belt and braces against `patch_fn` ghosts).
5. Final self-check: each snapshotted table's count is unchanged (raise otherwise); `to_regprocedure`/`to_regclass`/`information_schema.columns` prove every dropped object is absent; the 24 `coach_recommendations.input_summary` shapes stay valid jsonb.
Kept explicitly (list in the PR): `coach_plan_calendar_for`, `copy_off_food_core`, `exercise_discomforts.note`, B14-B18 items, all 8 Edge Functions (none has no caller, so no undeploy step; `deploy-supabase` only runs `functions deploy`, which never deletes, but nothing is deleted).
Old-app impact (v1.9.0), tell the user before the merge: free-text screen load error and failing switches (B1/B2); "Para conocerte" tolerant, its save works because `save_coach_intake` ignores the key; **NEW finding: the old Check-in screen breaks** because `WeeklyCheckInDto.COLUMNS = "week_start,energy,notes"` selects a dropped column and its upsert sends `notes`. Mitigation: deliver the new APK right after `deploy-supabase` is green; if the user wants the old app's check-in alive meanwhile, split a/b as recommended and merge the app first.

**pgTAP** (`supabase/tests`):
- Delete: `coach_free_text.test.sql`, `save_meal_slots.test.sql`, `delete_meal_slot.test.sql`, `off_food_conversion.test.sql` (convert_used_off_foods, 7 refs; keep any non-convert assertion by moving it, check before deleting), the `migrate_equipment_list` parts of `equipment_ez_bar_cardio_machine.test.sql` (14 refs) and `meal_slots.test.sql` (1), `coach_plan_weeks_pause.test.sql` assertions for `coach_plan_week_of_date`/`coach_rederive_plan_links`/`coach_plan_calendar`. Adjust every `plan(n)`.
- Edit: `coach_summary.test.sql` (lines ~206-225 free_text, worst-case 16 384-byte test), `coach_plan_inputs.test.sql` (8), `coach_user_intake.test.sql` (15; also `save_coach_intake` ignores `free_text`, returns the other fields unchanged), `delete_account_cascade.test.sql` (remove insert at ~101 and its count assertion; stays green), `weekly_check_in.test.sql` (22 refs; drop notes assertions, keep energy), `copy_off_food.test.sql`, `meal_structure_save.test.sql`, `food_energy_*` (name references only).
- New `removed_objects.test.sql`: every dropped function/table/column absent (`hasnt_function`, `hasnt_table`, `hasnt_column`), `exercise_discomforts.note` present, readers return their remaining keys, `coach_summary` has no `free_text` key, cross-user isolation untouched (existing tests stay).
- If `coach_recommendations` input_summary scrub is testable: insert a row with `free_text` before running the scrub SQL in the test (copy the `update` statement) and assert removal.

**Edge Functions**: `supabase/functions/coach/planNutrition.test.ts` delete the test "free text in the summary never changes a figure" (~519-531) and any fixture `free_text`; no production TS change; no redeploy needed besides the normal `deploy-supabase`. Run `deno test` for `coach`.

### S2 Domain/data (kotlin-core-developer), `shared`
- `CoachIntake.kt`: remove `freeText`, `IntakeUpdate.FreeText`, `FreeTextKind`, `FreeTextStatus`, `FreeTextLine`.
- `CoachIntakeDto.kt`: remove `freeText`, `FreeTextLineDto`, `FreeTextLinesParams`, `ExcludeLineParams`; `PlanInputsDto.kt` and `PlanInput.kt`: remove `PlanInputKey.FreeText`/`PlanFixTarget.FreeText` (an old server entry key `free_text` must not crash: keep the DTO mapping tolerant of unknown keys, i.e. skip it; test it).
- `CoachIntakeRepository` + `SupabaseCoachIntakeRepository` + `UnconfiguredCoachIntakeRepository`: remove `freeTextLines`, `setLineExcluded`; `save` stops sending `free_text`.
- Check-in: `CheckIn.notes`, `WeeklyCheckInDto.notes` and `COLUMNS = "week_start,energy"`, repository mapping.
- Dead code (A1-A18): delete `ListCoachChangesUseCase`, `UndoCoachChangeUseCase`, `DecideProgressionFlagUseCase` (+ `SharedModule.kt:423-424` lines), `CurrentWeight` (+ DI `SharedModule.kt:142,517`), `callCoachFunction`, `CalorieEvaluationDto`, `validateRoutinePlannedSets`, `legacySummaryReps`, `BuildBlockUseCase.checkFolder`/`saveBuilt` (then prune unused ctor params and its DI/test call sites), `FoodDiaryRepository.deleteEntries` (3 impls), `DiscLoader.perSideText`, `carriesVolume`, `monthsWithTraining`, `sleepDay`. Each with its test cases. Keep `CoachChangesRepository` and the `undo_coach_change`/`decide_progression_flag` RPCs (repository still used).
- Do NOT fix the optional compiler warnings.
- Tests: `CoachIntakeDtoTest`, `PlanInputsDtoTest`, `SupabaseCoachIntakeRepositoryTest`, `SupabaseWeeklyCheckInRepositoryTest`, `CoachChangeUseCasesTest`, `MeasurementsByDayTest`, `PlannedSetsTest`, `DiscLoaderTest`, `SetEntryTextTest`, `TrainingCalendarTest`, `WatchDataShapingTest`, `SupabaseFoodDiaryRepositoryTest`, `AddTemplateToDiaryTest`, `FakeCoachIntakeRepository`.

### S3 UI (compose-ui-developer), `composeApp`
- Delete files: `FreeTextLinesScreen.kt`, `FreeTextLinesViewModel.kt`, `FreeTextLinesUiState.kt`, `CoachLimitMessage.kt`; delete `drawable/ic_visibility.xml`.
- Navigation/DI: `AppRoutes.kt` (route at 99/457), `AppNavHost.kt` (slot `FreeTextLinesSlot`/`DefaultFreeTextLinesSlot`, `fullScreenRoutes`, trainer `onOpenFreeText`, 879), `App.kt` params and `NAV_STATE_VERSION` bump 1 -> 2 (a saved stack with the removed route must be dropped, ADR-40), `AppScaffold.kt`, `AppRoot.kt`, `AppFeatures.kt`, `UiModule.kt` (48,520).
- Trainer: remove row `trainer_free_text_row` and `onOpenFreeText` (`TrainerScreen.kt`).
- "Para conocerte" (`IntakeScreen.kt`, `IntakeUiState.kt`, `IntakeViewModel.kt`, `IntakeSummary.kt`, `IntakeText.kt`, `IntakeFields.kt`, `BeforeStartUiState.kt`, `BeforeStartScreen.kt`, `PlanInputRows.kt`, `PlanInputText.kt`): remove the free-text field, `IntakeEvent.EditFreeText`, `IntakeRowKind.FreeText`, help `intake_free_text_*`. Years training and maintenance kcal, their save and `rememberSaveable`/SavedStateHandle restoration stay (use the same design-system components as today; no new component, no template change).
- Check-in: remove the note field (`CheckInScreen.kt` 343-360: `AppTextField`/counter, tags `checkin_notes*`), `CheckInUiState.notes/notesTooLong`, `CheckInViewModel` (state, save, reload, `canSave`), strings `checkin_notes_label/counter/too_long`. The screen keeps energy + summary + pinned "Guardar".
- `GoalFormat.isZeroTenths`, `HelpButton.helpTopBarAction` (R-39 sweep: confirm nothing else bypasses `HelpButton`).
- Strings (`values/strings.xml`): delete A32 free-text strings (2079-2116 + comment at 2081, includes `trainer_free_text_row`, `intake_free_text_*`, `free_text_*`), A19 (26 unused strings), the 8 `coach_failure_*`/`coach_error` strings of `CoachLimitMessage` (keep `coach_timeout`), check-in notes strings. Reword any text that says the coach reads/uses notes (grep `lee|usa|tiene en cuenta` near coach in strings; ux-reviewer approves). `before_start_note_missing` stays.
- ux allowlist: re-run the stale-entry loop over `scripts/ux-inline-text-allowlist.txt`/`ux-conventions-warnings.txt` after the string removal.
- Host tests: delete `FreeTextLinesRestorationTest`, `FreeTextLinesScreenTest`, `FreeTextLinesViewModelTest`; edit `IntakeScreenTest`, `IntakeRestorationTest`, `IntakeViewModelTest`, `IntakeRowSheetsTest`, `IntakeRowEditorTest`, `IntakeFlowNavigationTest`, `YourDataScreenTest`, `YourDataViewModelTest`, `BeforeStartScreenTest`, `BeforeStartViewModelTest`, `TrainerFlowTest` (row absent), `ShellChromeTest`, `CheckInViewModelTest`, `CheckInScreenTest` (11 note refs), `RemovedRouteRestorationTest`.
- `RemovedRouteRestorationTest`: change the sample destination from the free-text route to another existing full-screen action of the same `actions` object that stays (use the real `IntakeRoute`/"Para conocerte" action, tag `intake_screen` or whatever its root tag is; read `AppNavHost.kt` to pick), keep both tests (same version restores; version+1 drops and starts at `nav_tab_diary`), update the KDoc ("a destination removed by T-277"). Add a third test: a stack saved with `NAV_STATE_VERSION - 1`-style older version (use `navStateVersion = 1` explicitly) is dropped under the current version, which is the real T-277 scenario.

### S4 Platform / scripts
- `androidMain`: none.
- Resource check (Android lint does not see KMP Compose resources): new `scripts/check-unused-resources.py` (stdlib, run with `python -I`): lists every `<string|plurals|string-array name>` of `composeResources/values/*.xml` and every `drawable*/font/files` basename (excluding `files/exercises/images/` and `files/licenses/`), counts word-boundary hits in all `*.kt` under `shared/src`, `composeApp/src`, `androidApp/src` plus `ux-inline-text-allowlist.txt` (only wildcards expanded), and reports ids with zero uses; also fails on `allStringResources|Res.getString|StringResource("` dynamic use. Wire it into `scripts/verify-local.sh` and `android-ci.yml` next to `check-ux-conventions.sh` (after the cleanup it must exit 0). Test: `scripts/tests/test_check_unused_resources.py` with a temp tree (used string passes, unused fails, plural/array handled, dynamic use detected). Add the new test to the Python step of `.github/workflows/security.yml:43`.
- Python tests S1/S2 of the audit: `scripts/test_repdb_tools.py` and `scripts/tests/test_generate_band_long.py` never run in CI while their tools are kept for reproducibility. User said no "keep for later": plan = wire both into `security.yml:43` (they become used). Fallback if either is broken or slow: delete that test with the tool's note in ADR-107. Non-blocking; PM may overrule.
- Workflows/scripts otherwise kept (S3-S5, W1). Stale `.claude/worktrees/agent-a4021d6e810fd2143` leftover is outside the task: PM only.

### S5 Docs (one developer: the docs developer = kotlin-core-developer after S2, or the PM-assigned doc agent) and `planning/10-feature-inventory.md`
- `planning/00-decisions.md`: ADR-107 "Free-text notes and unused code removed; the coach is deterministic" (index row + full text: context ADR-97..99/103, decision, list of dropped backend objects and kept ones, old-app impact, mention of the scrub, `weekly_check_ins.notes` drop with the count reported, `exercise_discomforts.note` kept for cross-session discomfort tracking), update "next: ADR-106" sentence to ADR-108; mark amended rows for ADR-97..99 where they cover US-81/US-89 ("Amended by 107").
- `planning/epics/E13-performance-assistant.md` (20 hits) and `E12-training-data-and-preferences.md` (3): mark US-81 and US-89 "Amended by ADR-107", strike or reword the scenarios about the notes screen/switches. Frozen `phase1/` and `phase2/` are untouched.
- `05-design-system.md`: §10 glossary remove the free-text terms ("Lo que el entrenador sabe de ti", etc.); §8 add rule R-107 (next free number after R-106; confirm with grep) "No copy claims the coach reads or uses something it does not" worded by ux-reviewer (in this plan: ux-reviewer words it); fix the 7 hits.
- `03-data-model.md` (3 hits: drop table/column/RPC rows, notes column, RPC list) and `04-business-rules.md` (1 hit) and `07-coach-design.md` (20 hits: reword only mentions that the coach reads notes; ADR-87 "words never change numbers" stays true, owner `fitness-scientist` only if a prescription changes: it does not); `02-architecture.md`: no Edge Function removed, so no change except if it lists `FreeTextLinesRoute`/screens (grep).
- `tasks/plan.md`/`tasks/todo.md`: not touched here (PM handoff).
- **`planning/10-feature-inventory.md`** (single author, written LAST, after S1-S4 so it lists only what remains): areas Diario, Peso, Entreno, Trainer, Perfil, Inicio, Centro de avisos, Backend jobs and Edge Functions; one line per functionality: name, route/screen (`AppRoutes.kt` type name, verify all 48 routes remain: 49 minus `FreeTextLinesRoute`), story `US-##`. Start from `.workflow/feature_inventory_draft.md` and `.workflow/feature_inventory_backend_draft.md`, drop removed items (free-text screen, check-in note, dropped RPCs), cross-check with Grep of `composable<`/`dialog<` and `supabase/functions` folders (8) and `cron.schedule` entries. No code, no copied planning text. Linked from `planning/README.md` (one table row). Assign to kotlin-core-developer in the final slice.

## Design-system templates used
None new. Screens only lose elements (Trainer row, a field in "Para conocerte", the check-in note field). `designsystem/HelpButton.kt` loses `helpTopBarAction` (unused). No haptic/message change. New rule R-107 in §8.

## Sweep (patterns this task changes)
- Free text: Grep `free_?text|FreeText|coach_free_text` over `*.kt *.ts *.sql *.xml` (64 files today) must end with only history (old migrations, `planning/phase*`) and ADR-107; run it in the PR body.
- Check-in note: Grep `checkin_notes|notes` in `feature/checkin`, `CheckIn*`, `WeeklyCheckIn*` -> 0 (other `notes` such as routine/set notes `NoteSheet` stay).
- Strings: re-run `scripts/check-unused-resources.py` and the allowlist loop.
- Copy claiming the coach reads notes: Grep `entrenador (lee|usa|tiene)` in strings.

## Contracts
- `save_coach_intake(p jsonb)`: accepted keys unchanged except `free_text` which is ignored (no error, not stored, not returned). Returned jsonb has no `free_text`.
- `coach_plan_inputs`: list without a `free_text` entry. `coach_summary`: no `free_text` key.
- `weekly_check_ins(user_id, week_start, energy, ...)` without `notes`; the app selects `week_start,energy`.
- Dropped: see S1.4. No new objects.

## Tests (summary)
- pgTAP: as in S1 (about 1 new file, 11 edited, 6 deleted; roughly 25 new assertions in `removed_objects.test.sql`). `delete_account_cascade` green.
- deno: `deno test` of `coach` after the deletion.
- Kotlin unit: updated DTO/repository tests (tolerates a server `free_text` entry; check-in DTO without notes), deleted tests of removed code.
- Host (Robolectric): Trainer root without the row (`TrainerFlowTest`); route table has no free-text route (a test that lists `AppRoutes` is not needed, compile proves it); "Para conocerte" without field/help, years training + maintenance kcal saved and restored (`IntakeRestorationTest` via `StateRestorationTester` + ViewModel from the same `SavedStateHandle`); check-in saves energy only and restores (`CheckInScreenTest`, ViewModel restoration); `RemovedRouteRestorationTest` with 3 cases.
- testTags: no new interactive element; removed tags `trainer_free_text_row`, `intake_free_text*`, `checkin_notes*`, `free_text_*` disappear with their tests.
- Python: `test_check_unused_resources.py` (4-5 cases); wired existing tests.
- Guard: no new script check for copy (R-107 is judged by ux-reviewer); if ux-reviewer finds it checkable add one line to `check-ux-conventions.sh` with its test in `scripts/tests/test-check-ux-conventions.sh`.

## Implementation order (each step leaves the build green)
1. S2 data/domain + tests (kotlin-core-developer), build `:shared`. Compile errors in `composeApp` are expected only if done before S3, so do S2 and S3's compile-fixing minimum in one slice: **Slice A (kotlin-core-developer)**: S2 plus nav/DI/ViewModel edits (`AppRoutes`, `AppNavHost`, `App`, `AppScaffold`, `AppRoot`, `AppFeatures`, `UiModule`, `IntakeViewModel`, `CheckInViewModel`, `FreeText*` ViewModel/UiState deletion) and their unit tests. Files: about 45.
2. **Slice B (compose-ui-developer)**: screens (`TrainerScreen`, `IntakeScreen`, `BeforeStart*`, `CheckInScreen`, `PlanInput*`), strings, drawable, `GoalFormat`, `HelpButton`, host tests, `RemovedRouteRestorationTest`; `check-ux-conventions.sh` and allowlist re-run. Files: about 30.
3. **Slice C (kotlin-core-developer)**: `check-unused-resources.py` + test + wiring, Python-test wiring; run it and prune whatever it still finds.
4. **Slice D (doc developer)**: S5 docs, `10-feature-inventory.md`, README link.
5. **Slice E (backend-developer)**: S1 pgTAP and TS test edits first (red against old schema is fine locally), then the migration as THE LAST COMMIT; `supabase db reset` + `supabase test db` + `deno test` green. The row-count self-check output (non-empty notes count) goes in the PR body.
6. pr-reviewer and ux-reviewer in parallel, then pr-creator `scripts/verify-local.sh --full`.
Recommended PR order if split: T-277a (slices A-C), T-277b (E), T-277c (D).

## Risks and open questions
- Size: about 90 files (see flag). Decide split or single PR before developers start.
- Old app (v1.9.0) check-in screen breaks once `weekly_check_ins.notes` is dropped (new finding vs the todo's risk list, which only listed the free-text objects). PM tells the user; deliver the new APK right after `deploy-supabase`, or merge the app slice first.
- `coach_plan_calendar` and `off_alcohol_g` drop are conditional on a live-body check by the backend developer (see S1.4); if still referenced they stay and the audit row moves to "kept, used".
- Python tests S1/S2: wired into CI by default (user wording "drop unused" is ambiguous); PM may choose deletion. Non-blocking.
- Every caller of shared pieces changed: `IntakeUpdate`/`CoachIntake` used only by `SupabaseCoachIntakeRepository`, Intake/BeforeStart/YourData view models and tests; `PlanInputKey` by `PlanInputRows/Text`, `BeforeStartScreen`, `AppNavHost:879`; `BuildBlockUseCase` ctor change touches `SharedModule`, the block view models and `BuildBlockUseCaseTest` (grep before editing); `HelpButton` has other callers, only `helpTopBarAction` is removed.
- Scrub `input_summary` is a production UPDATE (user approved); keep it idempotent and inside the migration.
- BLOCKING: none.

> Deviation (Slice A): the whole "Para conocerte" `IntakeScreen`/`IntakeRoute`/`IntakeViewModel` was deleted; years training and maintenance kcal are edited through the `IntakeRowEditor` sheets (unchanged). Wherever this document says `IntakeScreen`, `IntakeRoute`, `IntakeViewModel` or `IntakeRestorationTest`, read it as deleted; `RemovedRouteRestorationTest` uses "Tus datos" as its sample destination. `coach_error`, `coach_failure_*` and `coach_timeout` stay (used by `coachFailureMessage()`).
