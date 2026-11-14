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

## Batch 3 (reports: Resumen semanal, Revision del bloque, Informes, Informe pasado, Vista previa)

Tests/goldens that will fail or need an update:
- `EmptyState`/`ErrorState` (designsystem): the root `Box` is now `contentAlignment = Center` (was `TopCenter`); identical when the caller does not pass a height, centred when it passes `Modifier.fillMaxSize()`. Callers in other areas are unchanged; the C-111 sweep (pass `fillMaxSize()` and drop the wrapping `Box`) is still open for every `EmptyState`/`ErrorState` outside batch 2/3 screens. New KDoc line; no test for the alignment yet.
- New `RowGroupCard` (designsystem): one `AppCard` with 1 dp `borderSubtle` hairlines inset 16 dp between rows. No test or gallery entry yet. Batch 4 (C-112) should reuse it.
- Resumen semanal / Informe pasado (week) / Revision del bloque / Informes are `HubGrid`s (`LazyVerticalGrid`, `HubContentMaxWidth`, `maxContentWidth` passed to `AppScreen`): off-screen cards are not composed, scroll before asserting. `ReportsScreen` param `listState: LazyListState` became `gridState: LazyGridState`; `HubGrid` gained a `state` param.
- `WeekSummaryScreen`: the feature-local `SummaryCard(tag, content)` is gone (V-52); cards are `SectionCard` with a title (tags land on the card as before). `SummaryLine` stays.
- Entrenos card (both reports, `SessionsCard(tag, completed, planned, percent)`): "3 de 4" headline (`<tag>_headline`), `<tag>_percent` (block review only, "88 %"), `SegmentedProgress` (`<tag>_progress`; linear bar above 12 planned), spoken "3 de 4 entrenos". The old sentence lines (`week_summary_sessions`, `block_review_sessions`, `block_review_sessions_pct`) are unused now. Block review: `SessionsCard(state)` was renamed `BlockSessionsCard(state)`.
- Series por grupo (`GroupsCard`, weekly summary and block review): `BarListRow` per group (tag `<prefix>_group_<MUSCLE_GROUP_NAME>`), no more `<prefix>_groups_line` single spoken line, no `week_summary_group_cd_*` spoken sentences (the row reads "label, value"). Strings `week_summary_group_line` / `_cd_one` / `_cd_other` unused.
- Vista previa "Series por grupo muscular": `SectionCard` (`block_preview_sets_section`) with `BarListRow` rows (tag `block_preview_sets_<muscle>` on the row); the row label is "Pecho · 2 dias/semana" and the value is the plain set count (was a trailing "10 series · 2 dias a la semana" text; `block_sets_count` plural unused here).
- Block review lifts: each row shows "100 -> 105 kg" (`report_lift_range`) and a `TrendPill` "+5 kg" (`block_review_lift_<id>_change`); the stalled chip stays. `block_review_trend` and `HeaderWithHelp` are no longer used (`HeaderWithHelp` deleted). Weight, outcomes, waist, lifts, stalls, diagnosis and changes cards are `SectionCard`s with the "?" in the header slot (tags unchanged).
- Weekly summary nutrition: four `MetricTile`s in a 2 x 2 `ReportTileGrid` (V-55), tags `week_summary_nutrition_days|kcal|protein|weight` now on the tiles (were `SummaryLine`s with sentences). New copy keys `report_tile_*`; old `week_summary_nutrition_days|kcal|kcal_avg_only|protein|protein_no_kg|protein_avg_only` unused. The weight sentence tag `week_summary_nutrition_weight` survives only when there is no weight average. The gate-failed card shows the "Faltan registros" line and, when present, only the weight tile.
- Decision/status cards: `StatusCard` and the stored-review proposal use `ReportNoteCard` (a plain `AppCard`, same tags).
- "Informes": rows in one `RowGroupCard` (`reports_group`), `reports_row_<id>` tags and click behaviour unchanged, a 24 dp Outlined leading icon per type (`ic_calendar` for weeks, `ic_fact_check` for blocks). `appAnimateItem()` no longer applies to the rows.
- Vista previa: the week tab row is full-bleed (the list pads each item instead of the container); the footer no longer pads twice (V-67 for this screen). The preview screen keeps its 480 dp width (wizard/flow).
- V-68: `ic_description` already is the Outlined glyph (the vector's own header says so); no change was needed.
- Pending, no new component: Informe pasado inherits the grid from the two reports; nothing else changed there.
