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
