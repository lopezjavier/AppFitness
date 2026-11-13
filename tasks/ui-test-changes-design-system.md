# UI test and golden changes, design system round 0 (input for T-296)

Visual-first flow: T-292 to T-295 change only code; T-296 repairs the tests and re-records the goldens. Each batch appends here what it breaks or leaves uncovered.

## T-292 (navigation, headers and rows)

Goldens expected to change (re-record):
- `AppTopBarScreenshotTest` x3 (title W700).
- `AppEditorScreenScreenshotTest` dirty variant (top bar title).
- `ListComponentsScreenshotTest` `listRows` (`titleSection` headers, WorkoutRow layout).

Tests changed or added in commit e4ce774b (check they still pass after later batches):
- `AppNavigationLabelsTest` (new)
- `AppTabRowTest`
- `AppTopBarTest`
- `ListComponentsTest`
- `WorkoutRowTest`
- `AppNavigationTest`

## T-293 (progress, states and sleep)

- `EmptyState` has a required `icon` parameter (second position, after `message`); every call site now passes `Res.drawable.ic_inbox` explicitly (per-domain icons come in T-294/T-295). `ComponentSemanticsTest.emptyState_icon_notInSemantics` was adjusted only to compile.
- Goldens expected to change: `ProgressRing` and `ScoreRing` (captions and status 14 sp W600), `AppLinearProgress` and `MacroProgressBar` (`trackInactive` track and 4 dp `textTertiary` stop dot), `SleepStagesBar` (cyan ramp, Unknown now `textTertiary`), `EmptyState` and `ErrorState` (32 dp glyph in a 72 dp `surfaceOverlay` circle; error buttons min width 200 dp), every gallery entry showing them (state messages, rings, macro bars, sleep).
- Tests to review: `ProgressRingTest` (caption and status style assertions, `labelTechnical` expected before), any test asserting `SleepStagesBarDefaults.color` per kind, any test of `AppStateAction` button widths or `EmptyStateDefaults.IconSize` (48 dp before, now 32 dp), `StateMessages` tests asserting the icon size.
- New coverage to add: stop dot present on `AppLinearProgress`/`MacroProgressBar`; ramp alphas (Deep 100 %, REM 75 %, Light 50 %); both error buttons at least 200 dp wide.

## T-294 (EmptyState icons sweep A and Outlined icons V-3)

- EmptyState icons now per domain: Trainer (`ic_nav_trainer`, `ic_swap`, `ic_folder`, `ic_description`, `ic_calendar`), notifications (`ic_notifications`), weight circumferences (`ic_straighten`), gallery state messages (`ic_folder`, `ic_search`). New drawables: `ic_folder`, `ic_description`, `ic_straighten`. Sweep B (Diario, Entreno, analytics, activity, exercises, food, routines, meals) still passes `ic_inbox`.
- Seven drawables converted to Outlined: `ic_info`, `ic_photo_camera`, `ic_photo_library`, `ic_label`, `ic_auto_awesome` (hand-drawn stroked), `ic_nav_trainer` (hand-drawn stroked), `ic_health_connect` (outlined heart).
- Goldens expected to change: every golden showing those icons (info, camera, library, label, sparkle, Trainer tab, Health Connect source rows), the gallery state-messages entries, and any screen golden of the Trainer, notifications and circumference empty states.
- New test: `OutlinedIconsTest` (reads the 7 drawable files, asserts the Outlined header; not run yet). Not run: no tests executed in this batch.

## T-295 (final sweep)

- EmptyState sweep B: no call site passes `ic_inbox` any more except `ComponentSemanticsTest` and the gallery preview. New drawables `ic_bar_chart`, `ic_chat`. Icons: activity `ic_steps`; exercise progress `ic_trending_up`; muscle volume and training stats `ic_bar_chart`; strength, routines, exercise picker (no exercises) and past-workout routines `ic_nav_workout`; exercise picker (search) and food search `ic_search`; exercise history and past-workout sessions `ic_calendar`; exercise how-to `ic_description`; similar `ic_swap`; exercise chat `ic_chat`; food templates and meal defaults `ic_save_meal`.
- GhostButton audit: `GhostTone.Neutral` removed (identical to `Action`); callers drop the `tone` argument. `ButtonsTest` lost its 2 `Neutral` assertions (compile fix; the `Action` assertions cover the same colours).
- `ghostData` replaced by `textTertiary` in `CardioTimeCell` (empty cell) and `ChecklistRow` (unchecked glyph). Goldens expected to change: cardio time cell empty state, checklist rows unchecked, plus every golden with an EmptyState (new icons).
- `MonthCalendarGrid` selected day and `AppDatePickerDialog` selected day/year follow R-64 (selectedContainer, 1 dp lime border for the grid, lime W700 label; mark dot always lime). Goldens expected to change: month calendar grid, date picker dialog, diary history calendar screens. Not done: ActivitySummary:168 and AppCard `highlighted` (shared cyan highlight used by routine, folder review, workout calendar and metric cards; needs a product call).
