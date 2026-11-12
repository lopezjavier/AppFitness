# Implementation Plan: E19 · UI review → `v1.x` (proposed `v1.10.0`)

> **Status: PROPOSED by the epic-planner, 2026-11-11; revised twice the same day with the user's feedback; needs the user's OK on the breakdown PR.** Tasks T-277…T-285 (revised a third time: the cleanup is one task, and the dummy user runs before the gallery). **No 👤 checkpoint** (user decision 2026-11-11, Q-E19-1). E19 starts by removing everything that is no longer used (user decisions 2026-11-11, Q-E19-2 and Q-E19-5). The free-text notes go in every layer if nothing reads them, and a dead-code audit covers the rest. The audit list is shown to the user before any deletion, and every production table or column is approved by name before the migration. The E18 plan that lived here is in git history (E18 closed, `v1.9.0`; `tasks/todo.md` E18 section).

## Overview
E19 first cleans the app in one task. An audit lists everything no longer used, with evidence, and the user sees it first. Then everything on the list goes, in every layer and one PR: the free-text notes ("Lo que el entrenador sabe de ti", the "Enviar al entrenador" switches, the "Para conocerte" free-text field and help); every unused screen, string, resource, class, binding, flag, script and stale doc; and, through one migration, the free-text storage and every unused RPC, table and column the user approved. Then E19 builds the tools to see the whole app without installing it (the dummy user first, then the gallery), and reviews all the UI with the user, area by area.

The tools are a screen gallery and a rich dummy user on the emulator. The gallery is made of Roborazzi images from the Robolectric host tests with fake data: every screen, state, sheet and dialog, grouped by area in one index page, with a check that fails when a screen or an overlay has no entry. The dummy user runs on the local flavor and Docker Supabase, started with one command.

Then come four review rounds (Diario, Entreno, Trainer, Perfil). ux-reviewer proposes findings, the user decides each one in chat, and every accepted change goes into `05` first and is swept across every screen with the pattern. Nothing changes what the app does: the cleanup removes only unused things.

Sources: `planning/epics/E19-ui-review.md` (intent, non-goals, US-131, US-123), ADR-103, ADR-97…99, ADR-27, ADR-40, ADR-80, ADR-2/ADR-14 (local flavor, "Test login"), `05` §6, §8-§10, `06` (screenshot tests), `08` §3.2 (seed user), `02` §5, `03`, the T-259/T-242 production-drop precedent. Tasks: `tasks/todo.md` (E19 section).

Out of scope (epic non-goals, `01-scope.md`): changing behaviour or adding features; a second UI-automation harness (ADR-27; no Maestro, no `@Preview` fixtures); rebuilding screens from scratch; re-proposing anything in `05` §8 "Decided - do not re-propose"; dropping anything with production data that the user did not approve by name.

## Decisions (user, 2026-11-11)
- **No 👤 checkpoint in E19 (Q-E19-1).** There is no new functionality to test, and the epic says "no gates". The user reviews the gallery images and the emulator himself during the rounds. The closure T-285 waits only for the normal epic-closure approval.
- **One cleanup task (user, 2026-11-11).** The audit, the app removal and the backend migration form one task and one PR (T-277). The audit-first gate stays inside it, and production drops are approved by name.
- **The dummy user before the gallery (user, 2026-11-11).** Nothing technical ties the dummy user to the gallery. It must follow the cleanup because it seeds the post-cleanup schema.
- **The free-text notes go in every layer if nothing reads them (Q-E19-2, revised).** This covers the UI, strings, domain/data code, repositories, DTOs, DI, tests, Edge Function fields, a migration that drops the unused columns and tables (with a row-count self-check), pgTAP and docs (`03`, `04`, `05`, ADR-107). The audit proves by search that nothing reads them before the drop is planned. The "Para conocerte" free-text field goes with them; its other fields stay (this replaces Q-E19-4).
- **E19 removes everything else that is no longer used (Q-E19-5).** The candidates are unused screens and routes, composables, strings and resources, domain/data classes, ports, DI bindings, feature flags, scripts, Edge Functions, RPCs, tables and columns, and stale docs, found by static checks and verified by search. The audit list, with evidence, is the first deliverable, and the user sees it before any data is deleted. Anything holding production data is listed for the user's OK before the migration.

## Architecture decisions
- **One PR across every layer, and its risk (T-277).** The migration deploys to production when the PR merges, while the phone still runs the old APK (`v1.9.0`). From the old app's code:
  - Reading "Para conocerte" keeps working: `select()` on `coach_user_intake`, and the DTO's `free_text` is nullable with a default.
  - "Lo que el entrenador sabe de ti" shows its load error, and its switches fail, once `coach_free_text_lines` and `set_free_text_line_excluded` are dropped.
  - Saving the free-text field fails unless `save_coach_intake` ignores the `free_text` key.
  - The plan-input readers tolerate an absent `free_text` key.

  Mitigations:
  1. The recreated `save_coach_intake` ignores a `free_text` key.
  2. The PM delivers the new APK right after `deploy-supabase` is green.
  3. The audit's "old-app impact" column flags any other dropped object the old app calls; for those the user chooses a tolerant stub or a later drop when approving the list.
  4. The migration is the PR's last commit.

  The PM tells the user all this before the merge.
- **One migration for all approved drops (T-277).** A new migration (merged migrations are never edited) drops the approved objects and recreates the readers without the dropped fields. It carries a row-count self-check (T-259/T-242 precedent): every table it does not drop keeps its row count, otherwise it raises. Edge Function fields are removed and the functions redeployed. A function with no caller is deleted.
- **The audit is evidence-based.** For each item it records the command or search and its result. It looks for dynamic references before calling anything unused (`rpc("...")` strings, routes by serialization, resources by name). An RPC called only by pgTAP counts as unused.
- **The gallery does not need the dummy user.** It renders each screen from fixed fake UI states on Robolectric, with no backend, no account and no network (the epic). The dummy user (T-278) is only for the live flow on the emulator. **The two tasks are independent. T-278 runs right after the cleanup (it seeds the post-cleanup schema) and before the gallery, so the user can use the emulator while the gallery is built.**
- **Roborazzi is the tool (epic 2026-11-11; supersedes ADR-103's "spike" bullet, recorded by T-279).**
- **Gallery images are generated, not goldens.** The command writes the images and the index to a gitignored build folder, and CI uploads them as an artifact. The existing designsystem goldens stay. (The T-279 planner confirms this.)
- **Screens are rendered from fixed fake UI states** through their stateless content composables, with a fixed clock and `es-ES`. Where a screen has no stateless split, the planner uses the existing host-test fakes or a pure stateless extraction (no behaviour change).
- **The coverage check has two parts.**
  - (1) **Routes:** every `AppRoute` (the `AppDestinations.fullScreenRoutes` list plus the tab roots) and every `RootRoute` state needs a gallery entry.
  - (2) **Sheets and dialogs:** these are not routes, so a source scan of `feature/` finds every composable that calls an overlay primitive (`AppBottomSheet`, `AppEditSheet`, `AppPickerSheet`, `AppInfoSheet`, `AppActionSheet`, `FoodSheet`, `OptionPickerSheet`, `NameSheet`, `NoteSheet`, `DayPickerSheet`, `ChartMetricPickerSheet`, `RpePickerSheet`, `SetTypePickerSheet`, `AppConfirmDialog`, `DiscardChangesDialog`, `AppDatePickerDialog`, plus any new primitive) and requires it in the gallery's overlay manifest. The primitive list is derived from the `designsystem/` sources (every `*Sheet(` / `*Dialog(` declaration) plus `ModalBottomSheet`, `AlertDialog`, `Dialog`, and `feature/catalog/*` is exempt (T-279).
  - Both parts fail and name what is missing. T-279 has a pending list (Entreno, Trainer) that T-280 empties.
  - Help sheets (`HelpButton`) are covered as part of each screen that has them.
- **The rich dummy user is a separate local-only dataset.** The live-flow command applies it after `supabase db reset`, with dates relative to today. `seed.sql` and the pgTAP tests stay unchanged, and production never receives it.
- **Rounds change the design system first.** Each accepted finding goes into `05` in the round's first commit, then it is swept app-wide (R-39). A rule that can be checked becomes a guard in `check-ux-conventions.sh`.

## What the gallery must cover (inventory from the code, 2026-11-11)
Taken from `app/AppRoutes.kt` (`AppDestinations.fullScreenRoutes`, the tab roots), `app/RootRoute.kt` and the sheet and dialog composables in `feature/` and `designsystem/`. The T-279 planner re-derives it after the T-277 removals, and the check keeps it complete. Each screen gets its states (with data, empty, loading and error where they exist) and its overlays.
- **Inicio (root states):** `Loading` (skeleton), `Welcome` (Google sign-in button, and its error), `Onboarding` (each step: biometrics and goals), `ProfileError`.
- **Diario:**
  - Screens: `DiaryHome` (the day, comidas, entries, the header bell, calendar and magnifier) and `DiaryDayRoute` (a past day).
  - Food search (`FoodSearchRoute`) in its normal, pick (comida draft) and view-only modes, with its tabs including Comidas.
  - `AddFoodRoute` (`AddFoodSheet`, normal and view-only).
  - `CreateFoodRoute`: manual, from barcode, AI prefill, Open Food Facts copy, the energy-check block, and the Alcohol section with grados.
  - `EditFoodRoute`, `EditEntryRoute` (`EditEntrySheet`, including a removed-food entry, read only), `SaveTemplateRoute` (`SaveTemplateSheet`), `TemplateDetailsRoute` (+ `TemplateFoodSheet`).
  - `NotificationCenterRoute` (Centro de avisos, with its "Informes" entry) and `CheckInRoute`.
  - Photo and AI flows (`photo/PhotoSheets`): the AI sheet with photo and "Describir comida", the text input, the draft, the error with retry and offline.
  - Barcode result paths: found, not found leading to create, and OFF.
- **Entreno:**
  - `WorkoutHome` (routines and folders, `RoutinesSheets`, `FolderSheet`), `RoutineDetailsRoute`, `RoutineBuilderRoute` (+ `RoutineBuilderCalculatorSheets`, the row dialog).
  - `ExercisePickerRoute` (normal and select mode), `ExerciseDetailRoute` (its tabs including the chat; `CreateExerciseSheet`, `ExerciseFormSheet`).
  - `LiveSessionRoute`: set rows, the rest and cardio timer slot, the summary inside it, `LiveSessionDialogs`, `ExerciseMenu`, `RpePickerSheet`, `SetTypePickerSheet`, `DiscLoaderSheet`, `WarmUpCalculatorSheet`, `DiscomfortSheet`, `ProgressionHelpSheet`, `VolumeOptionsSheets`, `FinishSessionSheet`, `SaveAsRoutineSheet`, `UpdateRoutineSheet`.
  - `HistoryRoute` (calendar), `SessionDetailRoute`, `EditWorkoutRoute` (`WorkoutFormBody` sheets), `PastWorkoutRoute` (+ `PastWorkoutChooserSheet`, the `WorkoutEntry` dialog).
- **Trainer:**
  - `TrainerHome` (the compact root and "Detalles"), `TrainerPlanRoute` ("Tu plan"), `TrainerBlockRoute` ("Tu bloque", the "Actual" week pencil and its date picker, the block section dialog).
  - `PlanProposalRoute` (including the blocked state), `BeforeStartRoute` (plan and block modes, `ActivityLevelSheet`), `YourDataRoute`. The "Para conocerte" screen was deleted by T-277 (years training and maintenance kcal are edited through the `IntakeRowEditor` sheets).
  - `BlockPreviewRoute`, `BlockBuilderRoute`: its steps `BlockTypesScreen`, `BlockSplitChoiceScreen`, `BlockTargetsScreen`, the builder, `DayTypeSheet`, `BuilderSlotPickerSheet`, `BuilderCreateExerciseSheet`, `ExerciseReasonSheet` and its dialog.
  - `BlockStartSheets`, `BlockReplaceSheet`, `FolderBlockRoute` (+ dialog), `FolderReviewResultRoute`.
  - `WeekSummaryRoute` (including a paused week), `BlockReviewRoute`, `ReportsRoute`, `PastReportRoute` (a weekly and a block report).
  - `FreeTextLinesRoute` and anything else the T-277 audit removes are not covered.
- **Perfil:**
  - `ProfileHome` (root, "Mi entrenamiento" sheet, `ProfileDialogs`, the account-action dialogs), `PreferencesRoute`, and `PreferencesSectionRoute` for each of `personal`, `energy`, `macros`, `cooking`, `training`, `checkin`.
  - `MealSlotsRoute` (+ its sheet), `MealDefaultsRoute` (+ `MealDefaultsSheets`).
  - `WeightRoute` (`WeightDaySheet`, `CircumferenceSheet`, `CompositionSheet`, `GoalSheets`, the chart metric picker; opened on the weight and circumference targets), `WeightHistoryRoute`, `DiaryHistoryRoute`.
  - `StrengthRoute`, `MuscleVolumeRoute`, `StatsRoute` (+ `MuscleDetailSheet`), `ActivityRoute` (+ the chart picker), `ExerciseProgressRoute`, `AboutRoute`.
- **Shared, once:** the offline banner, the shell's bottom bar and its tabs.
- **Not in the gallery (exempt by name, with the reason in the manifest):**
  - `CatalogRoute`: the debug catalog, local only, unless the audit removes it.
  - System UIs the app does not draw: the Credential Manager sign-in sheet, the system camera and photo picker, the ML Kit barcode scanner, permission dialogs.
  - The rest-alert notification (`RestAlertNotifier`): an Android notification, not Compose. Its texts are listed in the index.
  - There are no home-screen widgets.

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR. There is no S task.

| Order | Task | Size | Story | Flow | Agent runs |
|---|---|---|---|---|---|
| 1 | T-277: Remove everything unused: the audit, then the free-text notes and all dead code in every layer (one PR, with the migration) | **L** (by the audit) | US-123 cleanup (Q-E19-2, Q-E19-5) | audit + backend + core + UI | 9 |
| 2 | T-278: A rich dummy user and one command for the live flow on the emulator | **M** | US-131 | backend + script | 5 |
| 3 | T-279: Screen gallery, part 1: harness, index, coverage check, Diario, Perfil, Inicio | **L** | US-131 | UI (host tests) | 6 |
| 4 | T-280: Screen gallery, part 2: Entreno and Trainer | **L** | US-131 | UI (host tests) | 6 |
| 5 | T-281: Review round 1: Diario | **M** (estimate) | US-123 | review + UI | 7 + review |
| 6 | T-282: Review round 2: Entreno | **M** (estimate) | US-123 | review + UI | 7 + review |
| 7 | T-283: Review round 3: Trainer | **M** (estimate) | US-123 | review + UI | 7 + review |
| 8 | T-284: Review round 4: Perfil | **M** (estimate) | US-123 | review + UI | 7 + review |
| 9 | T-285: E19 closure (after the user's closure approval) | closure step (no size class) | DoD | PM | PM only |

Why these splits and merges:
- **Cleanup → one task, T-277 (user decision).** The audit, the app removal and the backend migration go in one PR. The audit-first gate and the by-name approval of production drops stay inside it. The deploy-order risk is handled as described in "Architecture decisions". It is mostly deletions; it splits by layer with a new ID only above ~30 files with real edits.
- **Cleanup before everything else:** the dataset seeds the cleaned schema, and the gallery never captures screens that go away.
- **US-131 → T-278 + T-279 + T-280:** the dataset and its script are a different domain (about 6-8 files, M). It goes first because nothing ties it to the gallery and the emulator is useful while the gallery is built. The gallery covers about 48 screens, 4 root states and about 35 sheet and dialog composables, each in several states, so it is split by review area, not by component. The harness, index and checks are built once, in T-279.
- **US-123 → one task per area (T-281…T-284):** the story defines the rounds by area and requires the user's decision before each fix batch. Sizes are estimates. Above ~15 files a round splits into batches with new IDs (design system and rules first, then the sweep). A round with very few accepted findings (S) merges into the next one.
- **Closure T-285** waits for the user's epic-closure approval and has no checkpoint.

## Phases
No 👤 checkpoint (user decision 2026-11-11). The user's feedback comes through the audit list, the drop approvals, the per-round decisions in chat and the updated images after each merge.

### Phase 0: Remove what is no longer used
- T-277, one task and one PR:
  1. The audit list (item, layer, evidence, proposed action, production data yes/no, old-app impact) goes to the user in chat before any deletion.
  2. The user approves each production table or column by name.
  3. The removal follows in every layer, with the migration as the last commit.
  4. Before the merge, the PM tells the user how the old APK behaves after the drop.
  5. After `deploy-supabase` is green, the PM delivers the new APK right away.
- Verification items:
  - `deploy-supabase` green on `main` after T-277, with data intact (the self-check row counts).
  - lint, including unused resources, clean or with a named allowlist.

### Phase 1: The review tools (US-131)
- T-278 (dummy user and live-flow command; the PM sends the user the command when it merges) → T-279 → T-280. When T-280 merges, the PM sends the gallery command and the index path, with no gate. Anything missing or wrong becomes a `fix(T-###)` before round 1.
- Internal milestones:
  - The dataset pgTAP test green after T-278.
  - The gallery artifact attached to the T-279 and T-280 CI runs.
  - Both coverage checks green with an empty pending list after T-280.

### Phase 2: The review rounds (US-123)
- T-281 (Diario) → T-282 (Entreno) → T-283 (Trainer, with the carried UI follow-ups as pre-seeded findings) → T-284 (Perfil) → the user's closure approval → T-285.
- Each round:
  1. ux-reviewer writes findings from the gallery.
  2. The user decides each finding in chat.
  3. The accepted changes go into `05` first.
  4. The planner plans the fix batch, which includes the app-wide sweep.
  5. The PM sends the updated images to the user (no gate).

## Dependencies
- T-277 needs the breakdown approved. Inside it, the removal waits for the user's OK on the drop list.
- T-278 needs T-277 merged and deployed, because it seeds the post-cleanup schema.
- T-279 needs T-278 merged. There is no build dependency: the gallery does not use the dummy user. T-280 needs T-279 merged.
- T-281 needs T-280 merged. T-282 needs T-281, T-283 needs T-282 and T-284 needs T-283 merged, because each round reviews the gallery as the previous round left it.
- T-285 needs T-284 merged and the user's closure approval.
- Built pieces E19 relies on:
  - Roborazzi and the host-test setup, and the CI artifact upload.
  - The `local` flavor and "Test login".
  - The existing host-test fakes and `check-ux-conventions.sh`.
  - `deploy-supabase` and the T-259/T-242 self-check pattern.
  - Every screen built through E18.

## Risks
- **Dropping something that is used (T-277).** Mitigation:
  - Every item carries evidence and a search for dynamic references.
  - The free-text readers (`coach_summary`, `coach_plan_inputs`, the `coach` function inputs) are checked to only pass the text through.
  - The user approves each production object by name.
  - The row-count self-check runs, and the full pgTAP and Deno suites stay green.
  - Notes typed in other features (check-in, discomfort, "¿Cómo te ha ido?") are judged one by one, not dropped as a group.
- **Old APK against the dropped backend (T-277, one PR).** The migration deploys on merge while the phone still has `v1.9.0`. The old "Lo que el entrenador sabe de ti" screen and the free-text save would fail; the rest of the old app tolerates the drop. Mitigation: as described in "Architecture decisions" (`save_coach_intake` tolerant, an old-app impact column in the audit, the migration as the last commit, the new APK delivered right after the deploy, the user told before the merge).
- **A removed Edge Function stays deployed.** Mitigation: the T-277 planner checks that `deploy-supabase` removes it; otherwise it is a 👤 console step listed for the user.
- **The audit grows the task.** Mitigation: deletions are easy to review. The task splits by layer only above ~30 files with real edits. Doubtful items are listed for the user instead of removed.
- **Gallery effort per screen (T-279, T-280).** Mitigation: the planner decides per screen; any extraction is a pure refactor checked by the existing host tests.
- **Overlays the scan cannot see.** Mitigation: one primitive list; ux-reviewer checks the per-screen overlay list in the T-279 and T-280 plans; a new overlay primitive in `designsystem/` must join the list (pr-reviewer checks).
- **Robolectric rendering differs from the phone.** Mitigation: the emulator flow covers real rendering.
- **Dataset drift (T-278).** Mitigation: the dataset goes through the app's tables and RPCs, its dates are relative, and a pgTAP test runs it after every reset.
- **A round grows.** Mitigation: design system first, sweep by search, split into batches above ~15 files.
- **A look change that changes behaviour.** Mitigation: a common acceptance criterion; pr-reviewer rejects behaviour changes.
- **No phone check before closure.** Mitigation: the user reviews the emulator during the rounds and gives the closure approval himself.

## Open questions
- **Q-E19-1: RESOLVED (user, 2026-11-11):** no 👤 checkpoint in E19.
- **Q-E19-2: RESOLVED (user, 2026-11-11, revised):** the free-text notes are removed in every layer, including a migration that drops their storage, provided the audit shows nothing reads them; ADR-107.
- **Q-E19-4: superseded by Q-E19-5:** the "Para conocerte" free-text field goes with the notes; its other fields stay.
- **Q-E19-5: RESOLVED (user, 2026-11-11):** E19 removes everything no longer used. The audit list comes first, and production tables and columns need the user's OK by name. **Pending inside T-277 (not blocking the breakdown):** the user's OK on the drop list once the audit exists.
- **Q-E19-3 (non-blocking, delegated to ux-reviewer in the T-279 plan):** which area owns the shared screens (Centro de avisos, "Informes", the food search reached from several places) and the exact list of states per screen.

> **Update (user, 2026-11-11):** T-277 is split by layer into T-277 (app side), T-286 (backend migration) and T-287 (docs and feature inventory); order T-277 → T-286 → T-287 → T-278. Details and the audit are in `tasks/todo.md` (T-277) and `tasks/T-277/`.
