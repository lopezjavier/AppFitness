# UI test and golden changes, area Trainer (input for the repair task)

Visual-first flow: each batch changes only code; the repair task fixes the tests and re-records the goldens. Each batch appends here what it breaks or leaves uncovered.

## Batch 1 (designsystem components)

New components with no tests yet (write in the repair task): `SegmentedProgress`, `BarListRow` (+ `BarListRowDefaults.fraction`), `DecisionButtonRow`, `SectionCard`, `rememberFillProgress` (`FillMotion.kt`), `NonBreaking`, `MacroStackedBarDefaults.kcalShares`; gallery entries for each.

Behaviour tests that will fail or need an update:
- `SummaryCardTest`, `SummaryCardScreenshotTest`: chevron is top-aligned (was centred); new `headline`, `headlineUnit`, `facts`, `progress`, `highlighted` params (cyan 1.5 dp border). Golden changes for the chevron position.
- `CoachProposalCard` callers (3) and their tests/goldens: the buttons are now `DecisionButtonRow` (ghost 56 dp, 12 dp gap, 12 dp extra space above, stacks when a label does not fit); tags `<prefix>_accept` / `_discard` unchanged. New `highlighted` param.
- `AppTopBarTest`, `AppTopBarScreenshotTest`, `AppTopBarBadgeTest`: the bar root is now a `Box` wrapping the `Row` (tag unchanged); new `maxContentWidth` param, `AppScreen` passes its own, so the bar is capped at `maxContentWidth` (480 dp by default): wide-window goldens of every `AppScreen` change if the bar content was previously full width.
- `SkeletonBox` goldens and `ShimmerTest`-adjacent screenshots: base white 8 %, peak white 16 % (were `surfaceRaised` / `surfaceOverlay`).
- `StatusChip`: `StatusTone.Info` (cyan, no icon) and `Neutral` (`textSecondary`, no icon) added; `StatusChipDefaults.icon` now returns nullable. Tests calling it need a null check.
- `InlineNote`: new `tone` (`InlineNoteTone.Info` default, `Error`); goldens unchanged for the default.
- `ExerciseRow`: new `supportingSecondary` second line (row 72 dp when set).
- `MacroStackedBar`: new `barHeight` (default 12 dp unchanged; plan nutricional will pass `SlimHeight` 8 dp).
- Animations: segments and bars fill over 300 ms on first composition; Robolectric tests that screenshot right after composition must advance the clock or set `LocalAnimationsEnabled` to false.

Not done in this batch: V-67 / R-99 footer double padding. `PinnedFooter` already owns its padding and cap; the double padding comes from screen-level footer content (batches 2-4).

## Batch 2 (Trainer root, Tu plan, Tu bloque, Plan nutricional)

Tests/goldens that will fail or need an update:
- `TrainerScreen` tests: the body is now a `HubGrid` (`LazyVerticalGrid`, `HubContentMaxWidth`), so off-screen items are not composed (scroll or `performScrollToNode` before asserting). Without a plan the root shows `EmptyState` (tags `trainer_empty`, `trainer_empty_title`, `trainer_empty_message`, `trainer_empty_action`) instead of the `trainer_create_plan` button; offline the action is absent and the offline hint shows. The old `trainer_create_plan` tag is gone.
- `BlockSummaryBox` signature: `hasAppliedChanges: Boolean` became `appliedChangesCount: Int`; the changes-only box now shows "N cambio(s) aplicado(s)" (V-50). The saved-block box now carries `SegmentedProgress` (`trainer_box_block_progress`).
- "Tu plan" box (`trainer_box_plan`): headline "2.000" + "kcal al día", the phase and the diet break as lines, Cardio / Pasos as facts (was one "Definición · 2.000 kcal al día" line plus a cardio line). New copy keys `trainer_plan_fact_*`.
- Resumen semanal box: shows the last finished week's "x de y entrenos · z %" line (V-50).
- Highlighted cards (cyan border): `trainer_plan_card` (proposal), `trainer_block_card` (proposed block), `trainer_review_card`, the current week card. `trainer_review_card` is now a `SummaryCard` (V-51).
- "Tu plan" screen (`PlanDetailsSection` became `LazyGridScope.planDetails`): no "Tu plan" section header (V-53); the card shows the kcal headline, a rate `MetricTile` (`trainer_plan_rate_tile`) and the objectives via `OutcomesSection` (no more count plural `trainer_plan_outcomes`).
- "Tu bloque" screen (`PlanWeeksSection` became `LazyGridScope.planWeeks`): "Eliminar bloque" button (`trainer_remove_block`) moved to the top bar ⋮ (`trainer_block_more`) -> `AppActionSheet` (`trainer_block_menu`, row tag `trainer_remove_block`); the dialog tags are unchanged. The block card shows "Semana 2 de 4" + `SegmentedProgress` (`trainer_block_weeks_progress`). Week cards: current week highlighted, chips Info (Actual, Descanso de dieta, Descarga) and Neutral (paused), a `SegmentedProgress` per non-upcoming week (`trainer_week_<n>_progress`), the "?" and the pencil in the title row. Empty copy per box ("Aún no tienes un plan activo." / "Aún no tienes un bloque guardado."), centred.
- `TrainerBox` enum gained `emptyMessage`. `trainer_box_empty` string is now unused.
- `PlanProposalScreen`: kcal in `displayHero` + "kcal al día" (tag `plan_proposal_kcal` is now a merged row), `MacroStackedBar` `plan_proposal_macro_bar` 8 dp, footer uses `DecisionButtonRow` (ghost left, primary right; tags unchanged). Offline "Descartar" is no longer disabled (the ViewModel reports the offline message); a test asserting it disabled offline must change. The footer no longer pads twice (V-67 for this screen).
- `CoachFailureContent`: new `onLeave`; "Ya tienes un bloque en marcha" (block preview) shows "Volver a Trainer" (`block_preview_leave`) first and "Reintentar" second (V-69). New test needed.
- New `WindowWidthClassDefaults.HubGridCellMinWidth` (300 dp). Host tests: grid columns at 360 dp (1) and 900 dp (2).
