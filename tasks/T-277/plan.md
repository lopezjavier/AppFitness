# T-277 · Remove unused code, app side (US-123)

Scope: slices A, B, C of `tasks/T-277/removal-plan.md` (its S2, S3, S4). No migration, no pgTAP, no Deno, no docs (ADR-107, R-107, `planning/10-feature-inventory.md`, US-81/89 go to T-287; backend to T-286). Inputs: `removal-plan.md`, `audit_kotlin.md` (A1-A33, S1-S2), user decisions in `tasks/todo.md` T-277.

## Goal
The app no longer contains the free-text notes (screen, route, Trainer row, "Para conocerte" field, check-in note field, DTO/domain/repository fields, strings) nor any dead code the audit found. Behaviour of every remaining screen is unchanged. New unused-resource check wired in verify-local and CI. The new app must work against the CURRENT backend (T-286 not merged yet) and against the post-T-286 backend.

## Gherkin scenarios covered
US-123 "Behaviour does not change"; "A decision becomes a rule" (app side: no string claims the coach reads notes). "Para conocerte" keeps years training + maintenance kcal, save and restoration.

## Flow type: full UI. Gate `scripts/verify-local.sh --full` (navigation, DI, designsystem change).

## Compatibility with the backend during the window (must hold)
- Server still returns `free_text` (intake JSON, `coach_plan_inputs` entry) and `weekly_check_ins.notes`: DTOs must ignore the intake field and SKIP a `free_text` plan-input entry without crashing (check each DTO Json has `ignoreUnknownKeys`; the plan-input mapper must drop an unknown key, test it).
- App stops sending `free_text` to `save_coach_intake` and `notes` to the `weekly_check_ins` upsert, and selects `week_start,energy`. After T-286 drops the column nothing breaks.
- Other `weekly_check_ins` rows keep their stored `notes` untouched (we just stop writing; an upsert without `notes` does not null it).

## Slice A (kotlin-core-developer): shared data/domain, navigation, DI, view models, unit tests
Files (about 45). Build: `:shared` then `:composeApp:compileAndroidMain` and compile of test source sets must be green at the end of the slice; to keep the build green, Slice A also does the minimum Compose edits that compile requires only where they are pure deletions of state plumbing; screen layout edits stay in B. Rule: if a screen file cannot compile without removing its UI element, A removes the call and B tidies strings/tags and tests. (Where it is cleaner, A may leave deleted-file screens to B: see order.)
1. `shared` domain/data
   - `CoachIntake.kt`: remove `freeText`, `IntakeUpdate.FreeText`, `FreeTextKind`, `FreeTextStatus`, `FreeTextLine`.
   - `CoachIntakeDto.kt`: remove `freeText`, `FreeTextLineDto`, `FreeTextLinesParams`, `ExcludeLineParams`. `PlanInputsDto.kt`, `PlanInput.kt`: remove `PlanInputKey.FreeText`, `PlanFixTarget.FreeText`; unknown `free_text` entry key skipped.
   - `CoachIntakeRepository`, `SupabaseCoachIntakeRepository`, `UnconfiguredCoachIntakeRepository`: remove `freeTextLines`, `setLineExcluded`; `save` no longer sends `free_text`.
   - Check-in: `CheckIn.notes`, `WeeklyCheckInDto.notes`, `COLUMNS = "week_start,energy"`, repository mapping/upsert.
   - Dead code A1-A15: delete `ListCoachChangesUseCase`, `UndoCoachChangeUseCase`, `DecideProgressionFlagUseCase` (+ `SharedModule.kt` lines ~423-424), `CurrentWeight` (+ `SharedModule.kt` ~142 and ~517), `callCoachFunction` (`CoachCall.kt`), `CalorieEvaluationDto`, `validateRoutinePlannedSets`, `legacySummaryReps`, `BuildBlockUseCase.checkFolder`/`saveBuilt` (then prune ctor params now unused + `SharedModule` + block view model + `BuildBlockUseCaseTest` call sites: grep first), `FoodDiaryRepository.deleteEntries` (+ `SupabaseFoodDiaryRepository`, `UnconfiguredDiaryRepositories`, fakes), `DiscLoader.perSideText`, `carriesVolume`, `monthsWithTraining`, `sleepDay`. Keep `CoachChangesRepository`, RPCs untouched. Do NOT fix compiler warnings.
   - Unit tests edited/deleted: `CoachIntakeDtoTest`, `PlanInputsDtoTest` (add: server `free_text` entry skipped, intake JSON with `free_text` decodes), `SupabaseCoachIntakeRepositoryTest` (payload has no `free_text`), `SupabaseWeeklyCheckInRepositoryTest` (select columns, upsert without notes), `CoachChangeUseCasesTest` (delete cases of 3 use cases; delete file if empty), `MeasurementsByDayTest`, `PlannedSetsTest`, `DiscLoaderTest`, `SetEntryTextTest`, `TrainingCalendarTest`, `WatchDataShapingTest`, `SupabaseFoodDiaryRepositoryTest`, `AddTemplateToDiaryTest`, `FakeCoachIntakeRepository`.
2. `composeApp` navigation/DI/view models
   - Delete `FreeTextLinesViewModel.kt`, `FreeTextLinesUiState.kt`.
   - `AppRoutes.kt` (route ~99/457), `AppNavHost.kt` (`FreeTextLinesSlot`/`DefaultFreeTextLinesSlot`, `fullScreenRoutes`, trainer `onOpenFreeText` ~852, `PlanFixTarget.FreeText` mapping ~879), `App.kt` (slot params; `NAV_STATE_VERSION` 1 -> 2), `AppScaffold.kt`, `AppRoot.kt`, `AppFeatures.kt`, `UiModule.kt` (~48, ~520).
   - `IntakeViewModel`, `IntakeUiState` (`IntakeEvent.EditFreeText`), `CheckInViewModel` + `CheckInUiState` (notes, notesTooLong, `canSave`, save, reload; SavedStateHandle keys), `BeforeStartUiState`/ViewModel where it carries `PlanInputKey.FreeText`.
   - ViewModel tests: `FreeTextLinesViewModelTest` (delete), `IntakeViewModelTest`, `BeforeStartViewModelTest`, `YourDataViewModelTest`, `CheckInViewModelTest`.
   - Restoration (ADR-40): `IntakeViewModel` recreated from the same `SavedStateHandle` keeps years training + maintenance kcal; `CheckInViewModel` recreated keeps energy (no notes key).
Done when: `bash scripts/gradle-quiet.sh :shared:allTests` and `:composeApp:compileAndroidMain :composeApp:compileAndroidHostTest` (or the repo's equivalents) are green, and Grep `FreeText|free_text` in `shared/src` returns only the tolerance test fixtures.

## Slice B (compose-ui-developer): screens, strings, resources, host tests
Files (about 30). Read `05-design-system.md` §8/§10 before touching; no new component and no template change (screens only lose elements).
- Delete `FreeTextLinesScreen.kt`, `CoachLimitMessage.kt`, `drawable/ic_visibility.xml`.
- `TrainerScreen.kt`: remove row `trainer_free_text_row` and `onOpenFreeText` param (the list keeps the same row template for the remaining rows; no divider/spacing gap left).
- "Para conocerte": `IntakeScreen.kt`, `IntakeSummary.kt`, `IntakeText.kt`, `IntakeFields.kt`, `PlanInputRows.kt`, `PlanInputText.kt`, `BeforeStartScreen.kt`: remove the free-text field, `IntakeRowKind.FreeText`, help `intake_free_text_*`, the `PlanInputKey.FreeText` row label/fix. Remaining fields use the same components as today.
- `CheckInScreen.kt` ~343-360: remove the `AppTextField`/counter and tags `checkin_notes*`; keeps energy, summary, pinned "Guardar".
- `GoalFormat.isZeroTenths`, `designsystem/HelpButton.kt` `helpTopBarAction` (R-39 sweep: Grep other TopBar help bypassing `HelpButton`; report, do not migrate unless trivial).
- `values/strings.xml`: delete A32 (all `trainer_free_text_row`, `intake_free_text_*`, `free_text_*`, comment ~2081), A19 (26 ids listed in `audit_kotlin.md`), the 8 strings of `CoachLimitMessage` (`coach_error`, `coach_failure_*`; KEEP `coach_timeout`), `checkin_notes_label/counter/too_long`. KEEP `before_start_note_missing`. Grep `entrenador (lee|usa|tiene)` -> no string claims the coach reads notes. Re-run the loop over `scripts/ux-inline-text-allowlist.txt` and `scripts/ux-conventions-warnings.txt` (no stale key).
- Host tests: delete `FreeTextLinesRestorationTest`, `FreeTextLinesScreenTest`; edit `IntakeScreenTest`, `IntakeRestorationTest`, `IntakeRowSheetsTest`, `IntakeRowEditorTest`, `IntakeFlowNavigationTest`, `YourDataScreenTest`, `BeforeStartScreenTest`, `TrainerFlowTest` (row absent: `trainer_free_text_row` does not exist), `ShellChromeTest`, `CheckInScreenTest` (11 note refs: energy-only save and restore), `RemovedRouteRestorationTest`.
- `RemovedRouteRestorationTest`: sample destination becomes another full-screen action that stays (read `AppNavHost.kt`, e.g. the "Para conocerte" action with its root tag); keep the two existing tests (same version restores; `NAV_STATE_VERSION + 1` drops to `nav_tab_diary`); add a third: a stack saved with `navStateVersion = 1` is dropped under the current version 2 (real T-277 scenario); update the KDoc.
- testTags: no new interactive element; removed: `trainer_free_text_row`, `intake_free_text*`, `checkin_notes*`, `free_text_*`.
- Host test per Gherkin: Trainer root without row; "Para conocerte" without field/help, years training + maintenance kcal saved (via fake repo) and restored with `StateRestorationTester`; check-in saves energy only and restores; removed-route restoration (3 cases).

## Slice C (kotlin-core-developer): scripts
- New `scripts/check-unused-resources.py` (stdlib, run as `python -I`): ids of `<string|plurals|string-array name>` in `composeResources/values/*.xml` and basenames of `drawable*`, `font`, `files` (excluding `files/exercises/images/` and `files/licenses/`); counts word-boundary hits in all `*.kt` under `shared/src`, `composeApp/src`, `androidApp/src` plus `ux-inline-text-allowlist.txt` (wildcards expanded); reports ids with zero uses (exit 1); also fails on dynamic use (`allStringResources`, `Res.getString`, `StringResource("`). Must exit 0 on the cleaned tree: run it and prune whatever is still reported (then the removed ids are only those; list in the PR).
- Test `scripts/tests/test_check_unused_resources.py` on a temp tree: used string passes, unused fails, plurals/array handled, dynamic use detected, allowlist wildcard counts as use.
- Wire into `scripts/verify-local.sh` and `.github/workflows/android-ci.yml` next to `check-ux-conventions.sh`; add the test to the Python step in `.github/workflows/security.yml` (~line 43), together with `scripts/test_repdb_tools.py` and `scripts/tests/test_generate_band_long.py` (user: no "keep for later"; fallback if either is broken or slow: delete that test and its tool note, report to PM).
- Stay unchanged: other scripts/workflows (S3-S5, W1). Stale `.claude/worktrees` leftover is PM's.

## Sweep (must end in the PR body)
- Grep `free_?text|FreeText|coach_free_text` over `shared/src composeApp/src androidApp/src scripts`: only the tolerance fixtures/tests remain (SQL/TS/docs belong to T-286/T-287).
- Grep `checkin_notes|\.notes` in `feature/checkin`, `CheckIn*`, `WeeklyCheckIn*`: 0 (routine/set `NoteSheet` notes stay).
- Grep `entrenador (lee|usa|tiene)` in strings.xml: 0 claims about notes.
- `check-unused-resources.py` exit 0; `check-ux-conventions.sh` exit 0; `helpTopBarAction` sweep result.

## Contracts
- `save_coach_intake` payload: same keys minus `free_text`. `coach_user_intake` read: DTO ignores a `free_text` field if present.
- Plan inputs: no `FreeText` key; a server entry `free_text` is skipped.
- `weekly_check_ins`: app selects `week_start,energy`; upsert sends no `notes`.
- No RPC, table or column of the DB is changed here.

## Tests (summary)
- Unit: about 14 edited files, 1 deleted use-case test portion; new cases: tolerance of `free_text` (2), repo payload without `free_text` (1), check-in select/upsert (2); removal of tests of deleted code.
- Host: about 15 edited, 2 deleted, `RemovedRouteRestorationTest` 3 cases; restoration tests for Intake and CheckIn.
- Python: `test_check_unused_resources.py` (5 cases) + 2 wired existing.
- No pgTAP, no Deno.

## Implementation order (each step leaves build + tests green)
1. Slice A (kotlin-core-developer): `shared` data/domain + tests; then composeApp nav/DI/view models. To keep composeApp compiling, A also deletes `FreeTextLinesScreen.kt` and `CoachLimitMessage.kt` and drops the removed arguments/call sites in screens with the smallest edit; B then finishes layout, strings, host tests. Run `:shared:allTests`, compile of composeApp main + host tests (host tests that still reference removed things may be fixed by B: if so A leaves `androidHostTest` compiling by deleting the free-text host tests and stubbing nothing, and lists the ones B must update; A's gate is main compile + unit tests).
2. Slice B (compose-ui-developer): items above; gate `bash scripts/check-ux-conventions.sh` + host tests of touched classes.
3. Slice C (kotlin-core-developer): scripts; run the new check, prune findings (new deletions get their own tests).
4. pr-reviewer in parallel with ux-reviewer on the working-tree diff, then pr-creator with `verify-local.sh --full`.

## What ux-reviewer must check
- Plan: nothing here adds UI; confirm each removal leaves the screens consistent.
- Trainer: row "Lo que el entrenador sabe de ti" gone; remaining rows keep order, spacing, dividers, `R-` row template; no orphan help/intro text.
- "Para conocerte": free-text field and its help (`intake_free_text_help_*`) gone; summary line/row of the intake no longer mentions it; years training and maintenance kcal rows and sheets unchanged; "Antes de empezar" list has no free-text row and its "fix" navigation still works.
- Check-in: note field and counter ("checkin_notes_*") gone; energy + summary + pinned "Guardar" unchanged; save enabled rules unchanged.
- Strings removed: A32 list, A19 list (26), `coach_error`/`coach_failure_*` (8), `checkin_notes_*`; kept `coach_timeout`, `before_start_note_missing`. No remaining copy claims the coach reads/uses notes (Grep `entrenador (lee|usa|tiene)`; `before_start_note_missing` is about estimates: judge).
- Glossary §10 terms tied to the free text are T-287's, but flag any in-app text using them.
- `HelpButton.helpTopBarAction` removal: confirm no screen bypasses `HelpButton` (R-39).
- Navigation: a saved stack with the removed route is dropped (NAV_STATE_VERSION 2), no crash on "leave the app and come back".

## UX notes (ux-reviewer, plan mode) - PASSED, with conditions
Pattern map: every element is a removal; no new pattern. Trainer rows -> existing row template (`TrainerScreen.kt`); "Para conocerte" -> `IntakeFields`/`IntakeRowKind` rows (unchanged); Check-in -> energy + summary + pinned "Guardar" (R-09/R-10 unchanged). Rules R-01..R-34 untouched; no dialog, sheet, haptic or message change. No decided item returns.
Conditions for the developers:
1. Strings (verified in `strings.xml`): the only copy claiming the coach reads notes is `free_text_intro` (l.2104, "El entrenador lee estas líneas...") and is deleted with A32. `before_start_note_missing` ("El entrenador usará una estimación") is about missing data: KEEP. `intake_years_help_body` ("el entrenador usa el mayor") is about years of training: KEEP, but confirm it does not mention notes. After deleting, Grep `free_text_|checkin_notes|trainer_free_text|intake_free_text` in `strings.xml` = 0; also Grep `Otras notas|lo que el entrenador sabe|líneas` for stragglers (keep `note_help_*`, routine/set notes).
2. Check-in: after removing the note field keep vertical rhythm (no empty gap or orphan spacer where the field was); the energy help (`CheckInEnergyHelpButton`) stays; the save-enabled rule stays "energy chosen"; draft restore keeps only the energy key.
3. Trainer: removing the row must leave no orphan divider/spacer; if the section it belonged to becomes empty, remove the section header too (R-39 template consistency).
4. "Para conocerte": the intake summary text (`IntakeSummary/IntakeText`) must not mention notes; the "Antes de empezar" fix for a missing item still lands on the right row.
5. R-39 HelpButton sweep: Grep shows ~45 callers using `HelpButton(`/`CalculatorHelpButton`/`RpeHelpButton`; `helpTopBarAction` has no remaining caller in `commonMain` outside its definition (confirm with Grep over `composeApp/src` incl. tests) before deleting. No ad-hoc help icon found. Report the result in the PR body.
6. Sweep list for the PR: `trainer_free_text_row`, `intake_free_text*`, `free_text_*`, `checkin_notes*`, `coach_failure_*`/`coach_error`, `ic_visibility`, `entrenador (lee|usa|tiene)`.
Spanish copy: no new strings. Wording rule for T-287 (R-107, proposed): "No copy claims the coach reads or uses something it does not."
testTags: none added. Removed: `trainer_free_text_row`, `intake_free_text*`, `checkin_notes*`, `free_text_*`. Remaining tags unchanged (`checkin_*` energy/save, `intake_*` years/kcal).

## Risks and open questions
- Old v1.9.0 impact is unchanged by this task (no backend change); the new app must be installed BEFORE T-286 merges (check-in screen of v1.9.0 breaks once `notes` is dropped). PM to tell the user.
- `NAV_STATE_VERSION` bump drops a saved back stack once for every user: intended.
- `BuildBlockUseCase` ctor pruning touches `SharedModule`, block view models and `BuildBlockUseCaseTest`: grep before editing.
- `HelpButton` has other callers: only `helpTopBarAction` is removed.
- If `check-unused-resources.py` finds extra unused ids beyond the audit, delete them (user: no keep-for-later) and list them in the PR.
- Size: about 80 file touches (many pure deletions) across three developer runs; accepted by the user's split. No BLOCKING items.

> Deviation (Slice A): the whole "Para conocerte" `IntakeScreen`/`IntakeRoute`/`IntakeViewModel` was deleted; years training and maintenance kcal are edited through the `IntakeRowEditor` sheets (unchanged). Wherever this document says `IntakeScreen`, `IntakeRoute`, `IntakeViewModel` or `IntakeRestorationTest`, read it as deleted; `RemovedRouteRestorationTest` uses "Tus datos" as its sample destination. `coach_error`, `coach_failure_*` and `coach_timeout` stay (used by `coachFailureMessage()`).
