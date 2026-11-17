# Premium visual redesign: report of changes and decisions

Date: 2026-11-17 · Branch: `feat/ui-premium-redesign` (from `docs/t-284-handoff`) · Follow-up task: [T-297](todo.md)

## 1. Origin and ground rules

- **Request (user):** review the 672 gallery screenshots (`composeApp/build/gallery/index.html`) screen by screen, find the
  visual and graphic gaps and turn the app into a professional, modern, "ultrapremium" one: enhance every existing graphic
  and add new graphics wherever information was only text or a plain list.
- **Waivers given by the user for this job:** ignore the project workflow (`CLAUDE.md` PM role, repo agents, `planning/`,
  `tasks/`); general-purpose agents allowed; tests and coverage out of scope; "just ensure the code compiles"; leave
  everything uncommitted.
- **Later directives:** agents must not compile at the same time; they finish the implementation and one compile is done at
  the end; let every agent finish; then "do not compile, provide status when everything is finished".
- **Honest state:** the shared design-system layer was compiled and checked in the gallery. **The per-area work of the seven
  agents (about 150 files) was written but never compiled** (see §6). Three compile/lint errors were fixed by hand
  afterwards (§7). Nothing in this change has run a test.

## 2. Method

1. Contact sheets of the gallery (`gallery-before/` backup kept in the scratchpad) reviewed area by area.
2. I built the foundation first (design tokens for paint, graphics kit) and upgraded the shared components, so every screen
   inherits the look.
3. Seven general-purpose agents, one per area, each with a shared brief (`premium-brief.md`): file ownership by area,
   new shared graphics only in new `designsystem/<Area>*.kt` files, per-area string files
   `strings_premium_<prefix>.xml` with names `px_<prefix>_*`, no edits to `strings.xml`, no new libraries, no blur APIs
   (min SDK 26), motion gated by `LocalAnimationsEnabled`, decorative graphics hidden from accessibility.
4. A lock-serialised build script was used so builds never ran at the same time.

## 3. Design decisions (the "Kinetic Dark" premium layer)

| Decision | Why |
|---|---|
| Light and depth through gradients and layered radial glows, no blur | min SDK 26 has no safe blur; keeps rendering cheap and Robolectric-friendly |
| Accent ramps (deep / base / bright) per colour (`AccentRamp`) | one source for gradient fills, rings, bars and tips |
| One "hero" card per screen (`AppCard(glow = ...)`) | hierarchy: not every card shines |
| Fills animate once (`rememberFillProgress()`), infinite motion only gated | respects reduced-motion; no new battery cost |
| Raw dp/sp/Color only inside `designsystem/` | existing token guard stays valid (it passes on the current tree) |
| Graphics only from data the state already holds | no fake production data; where data was missing the graphic was skipped (Fuerza trend, Actividad steps ring) |
| New state fields default to a value | existing call sites and tests keep compiling |
| Lime stays "selected / primary", cyan "informational", amber "over target / caution", gold only for records | keeps the C-20 / R-64 colour semantics |
| Every decorative graphic gets `clearAndSetSemantics {}` or a text alternative; testTags untouched | accessibility and tests |
| Spanish copy in per-area string files, never in `strings.xml` | avoids merge collisions between agents |

## 4. What changed

### 4.1 Design system (new files)
- `AppPaint.kt`: `AccentRamp`, brushes (`horizontal/vertical/diagonal`, `cardSurface`, `cardBorder`, `accentBorder`,
  `tileSurface`, `tileBorder`, `controlSurface`, `tintSurface`, `areaFade`, `Gold`), `Modifier.softGlow`,
  `drawRadialGlow`, `Modifier.ambientBackdrop`.
- `AppGraphics.kt`: `IconBadge`, `HeroStat`, `StatRing`, `Sparkline`, `MiniBarChart`, `WeekStrip`, `GaugeBar`,
  `DonutChart`, `LegendRow`.
- `CelebrationBurst.kt`: `CelebrationBurst`, `RecordBadge`, `ActivityHeatmap`.
- Area graphics: `StartGraphics.kt`, `DiaryGraphics.kt`, `DiaryEnergyScale.kt`, `DiaryAnalysing.kt`,
  `LiveStatusStrip.kt`, `TrainerGraphics.kt`.
- Other new sources: `feature/diary/DiaryMealStyle.kt`, `feature/routines/TrainingWeek.kt`,
  `feature/routines/RoutinesWeekHero.kt`, `feature/trainer/TrainerEmptyHero.kt`, `feature/weight/MetricGauge.kt`.
- Resources: drawables `ic_meal_breakfast/lunch/snack/dinner`, `ic_live_trophy`; strings `strings_premium_{start,diary,
  routines,live,body,stats,trainer}.xml`.

### 4.2 Design system (edited)
`AppCard` (gradient, lit hairline, `glow` hero param), `RingArc` (sweep gradient, halo, inner light, lit tip, no tip on a
full turn), `MacroProgressBar`, `AppLinearProgress`, `BarListRow`, `MuscleSplitBar`, `MacroStackedBar`,
`SegmentedProgress` (gradient fills with lit tip), `AppLineChart` (smoothing + area), `AppBarChart` (rounded gradient bars,
`highlightIndex`), `RadarChart`, `WeightPaceChart`, buttons (lime gradient + glow), `MetricTile` (`accent`, `icon`),
`StateMessages` (lit medallion), `AppScreen`/`AppTopBar` (ambient backdrop, transparent headers), `Pill`,
`AppNavigation` (sliding spotlight), `TonalIconControls`, `CompactActions`, `AppTabRow` (3 dp rounded indicator),
`SelectableCard` (`leading` slot), `WheelPicker`, `SetCompletionCheckbox`, `SetRow`, `RestTimerBar`, `MonthCalendarGrid`,
`WorkoutRow`, `BodyHeatmap`, `DiaryHistoryRow`, `MetricInterpretationRow`, `SummaryCard` (+ `glow`),
`CoachProposalCard`, `CoachChangeRow`, `FoodSheet`, `StepScaffold`.

### 4.3 Per area
- **Inicio:** hero welcome with value props, step progress bar, intensity cards, goals result with donut and celebration
  burst, branded splash, premium offline banner. The empty onboarding-goals screen was a fixture bug (sex and activity
  unset), fixed in `InicioGallery.kt`.
- **Diario:** kcal split by meal with meal icons and share bars, macro bars on food rows, premium goals prompt, notices
  hero, energy scale graphic on the check-in, macro split on the add-food sheet, search rows with icons, analysing graphic,
  hero totals on saved meals.
- **Entreno routines/history:** week hero (`TrainingWeek`), routine cards, restyled calendar with year heatmap
  (`yearDayCounts` added to the calendar state), richer workout rows.
- **Live session:** summary with celebration burst, gold record badges, volume bars, live status strip, lit set checks,
  timer rings, muscle-split donut, amber finish warning.
- **Peso/Calorías:** weight hero with sparkline, zone gauges (IMC, body fat, waist-hip) via `MetricGauge`, calorie charts
  and macro donut (`weekKcal`, `monthKcal` added to the diary-history state), strategy gauge, summary on Historial de peso,
  restyled account group, badges on preference rows (`sex` added to `GoalUiState`).
- **Perfil root/stats:** hero profile card, badged rows, Fuerza hero, stats tiles, lit body map.
- **Trainer:** hero plan card, block progress ring, new empty states, weekly adherence ring, before/after lifts graphic,
  nutrition-plan donut, failure-state graphics, badges on reports and data rows.
- **Navigation/shell:** `AppNavigation` spotlight, `RootStates` splash.

### 4.4 Test-source edits (to keep fixtures meaningful)
`EntrenoFixtures.kt`, `InicioGallery.kt`, `PerfilStatsGallery.kt`, `PerfilWeightGallery.kt`.

## 5. Not done (candidates for a later task)
Diario: totals hero for "Guardar como comida" and "Crear alimento", extra search empty states. Entreno: discomfort, warm-up
and disc-loader sheets, body map in session detail. Trainer: per-day weekly graphics, muscle-group targets, hero ring and
sparkline on block review, builder/preview/folder-review polish. Perfil stats: Fuerza trend (no series in state),
Actividad steps ring (no step goal), Acerca de. Peso: preference-form polish, goal-section and weekly-rate-pill extras.

## 6. Known problems and risks (to be handled by T-297)
1. **Compilation:** area code was never compiled; more errors than the three fixed are likely.
2. **Unused resources:** `scripts/check-unused-resources.py` fails on 17 strings: `px_body_macro_split_title`,
   `px_routines_{best_month,day_sets,details_last,details_never,empty_hint,next_hint,year_total}`,
   `px_stats_{howto_step,sheet_exercises,sheet_share,sheet_total,strength_month}`, `px_trainer_report_adherence`, and the
   existing `report_lift_range`.
3. **Gallery coverage:** `GalleryCoverageTest.realDesignSystem_everyComponentIsInTheCatalogOrExempt` fails until the new
   components get a catalogue section or an exemption.
4. **Host tests likely broken:** workout row stats string, empty-meal macro line, goals prompt row click, `trainer_empty_*`
   tags, `plan_proposal_macro_bar`, `trainer_box_block_sessions_progress`; goldens that changed by design are stale.
5. **Rules to re-check:** R-64 / C-20 colour semantics, 48 dp targets and `AccessibilitySweepTest`, font scale 1.3 at 360 dp,
   ktlint and detekt (long functions, magic numbers, import order). `check-design-tokens.sh` (442 files),
   `check-text-encoding.sh` and `check-ux-conventions.sh` (594 files, 0 warnings) pass.
6. **Process:** this work skipped the normal pipeline (planner, ux-reviewer, pr-reviewer) by the user's order; T-297 puts it
   back through review before merge.

## 7. Fixes applied after the first compile attempts
- `designsystem/WorkoutRow.kt`: `ExperimentalLayoutApi` imported from `androidx.compose.foundation` (wrong package) →
  `androidx.compose.foundation.layout`.
- `feature/profile/AccountActions.kt`: 121-column line wrapped (delete-account label).
- `feature/food/FoodSearchScreen.kt`: missing `AppTheme` import added.
- `feature/diary/DiaryDashboard.kt`: a max-line-length finding was reported at line 111, but no line over 120 columns exists
  in the file; probably a stale linter run. Re-check on the next lint.
- A script scan of every changed feature file found no other missing design-system import.
- Earlier, during the build: ring tip drawn at a full turn (guard added), PreToolUse hook needing repo-root cwd, build lock
  hardened against stale locks.

## 8. Size
152 files: 126 modified and 26 new (about 3,800 lines added, 890 removed before this report).
