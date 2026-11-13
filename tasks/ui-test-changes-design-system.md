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
