# T-284 Perfil: test and golden changes caused by the build batches

Appended by each batch; the test task works from this list (CI only).

## B1 design-system components

- `NavigationRow` (C-161, C-162): value is `bodyLarge` trailing (was `bodyMedium`); label is one line and, when it overflows, the row stacks (value becomes the supporting line). Affects `ListComponentsTest`, `ListComponentsScreenshotTest` goldens and any screen test that reads a NavigationRow value (Perfil, Preferencias, Trainer). Add a test for the stacked case (long value, large font scale).
- `MetricTile` / `MetricCard` (C-160): unit is now a `bodyMedium` span inside the value text joined by U+00A0 (was a separate `bodySmall` Text); value steps down once to `titleLarge`. `MetricTileTest` and tile goldens change; tests that find the unit as its own node must read the merged value text.
- `MetricTileRow` (new, C-160): tiles stack in one column from font scale 1.3; used by `TrainingStatsSections` (stats_tile_*), `WorkoutMetricTiles` (`*_duration`, `_sets`, `_volume`), `CheckInScreen` (`checkin_summary_*`) and `ReportCards` (`ReportTileGrid`). Tags unchanged. Add a font-scale test.
- Volume from 100.000 kg is written in tonnes ("1.250,5 t") in `stats_tile_volume` and `*_volume` tiles via `AppNumberFormat.volume`; new strings `unit_tonne`, `stats_unit_tonne`. Add an `AppNumberFormat` test (99.999 kg stays kg, 100.000 kg becomes "100 t", 1.250.500 kg becomes "1.250,5 t").
- Actividad tile (C-160, V-93): icon moved from `leading` to the new `MetricCard.labelLeading` (16 dp, inside the label row) and the four glyphs are the new `ic_*_outlined` drawables; `ActivityScreen` / `ActivitySummary` goldens change; the empty-state `ic_steps` in `ActivityScreen` is untouched.
- `AppBarChart` (C-166, R-112): bar width is min(60 % of the slot, 32 dp) (`BarGap` removed) and a zero value draws a 2 dp stub. `AppBarChartTest` and every chart golden (Series por semana, activity bars, stats) change.
- `AppLineChart` (C-159, R-112): padded domain for every chart with data (`plotRange` now floors/ceils to `domainStep`, default 5, margin max(10 %, step)), four gridlines on the value axis, new param `domainStep` (sleep passes 1.0). `AppLineChartBandTest` expectations on the range change; line-chart goldens (Peso, Fuerza, Actividad, catalog) change.
- `HealthStatusLine` (C-164, R-114): dot and text at the start, action at the end, one row of at least 56 dp (was a column). Tags unchanged. `HealthStatusLineTest` layout assertions and the Peso/Actividad goldens change.
- Day sheet (V-90): the Health Connect row in `WeightDaySheet` sits on `surfaceControl` (was `surfaceBase`). Peso day-sheet golden changes.
