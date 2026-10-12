# Design System — Kinetic Dark (Compose implementation)

Original source: `../design/stitch/kinetic-dark-performance/DESIGN.md`. This document **unifies and corrects** it (ADR-11) and defines what is built in `composeApp/.../designsystem/`. The `screen.png` mockups are a visual style reference, not a feature reference (ADR-03). The `code.html` files (Tailwind) are not ported: they are only used to check measurements and hierarchy.

## 1. Principles

1. **Dark mode only**, high contrast for gym lighting. No light theme in Phase 1.
2. **Depth through tonal layers** + micro-borders, not shadows.
3. **Meaningful color**: each accent represents a domain (see 2.2). Never decorative.
4. **One hand**: primary actions in the lower 60%; targets ≥ 48 dp.
5. **Bottom Sheets instead of dialogs** for contextual flows. Dialogs only for destructive confirmations.
6. **Immediate feedback**: skeletons while loading, haptics when completing sets and reaching goals.
7. **Tabular numerals** for all telemetry (kg, reps, kcal, timers).

## 2. Color

### 2.1 Surfaces and text
| Compose token | Value | Use |
|---|---|---|
| `surfaceBase` | `#0B0F14` | Screen background, Top Bar, Bottom Bar |
| `surfaceRaised` | `#121820` | Cards (tier 1) |
| `surfaceOverlay` | `#1A222D` | Bottom sheets, inputs, chips, active rows, rest bar (tier 2) |
| `borderSubtle` | `#FFFFFF` @ 8% | 1 px border of tier 1 cards |
| `borderStrong` | `#FFFFFF` @ 12% | 1 px border of tier 2 elements |
| `divider` | `#FFFFFF` @ 4% | Row separators inside cards |
| `textPrimary` | `#E0E2EA` | Primary text |
| `textSecondary` | `#C2CAB0` | Secondary text |
| `ghostData` | `#FFFFFF` @ 36% | Previous performance, placeholders |
| `scrim` | `#000000` @ 65% | Backdrop behind bottom sheets |

### 2.2 Accents
| Token | Value | Meaning |
|---|---|---|
| `neonLime` | `#A3E635` | Primary action, success, completed sets, active tab, FAB, kcal ring |
| `onNeonLime` | `#0B0F14` | Text/icons on lime |
| `athleticCyan` | `#38BDF8` | Training: timer, 1RM, volume, active filters, weight chart |
| `energyAmber` | `#F97316` | Warnings, thresholds, calorie excess |
| `electricGreen` | `#22C55E` | Favorable trend (weekly rate), "active/synced" states |
| `error` | `#FFB4AB` (text) / `#93000A` (container) | Errors and destructive actions |

### 2.3 Macros (fixed across the app)
| Macro | Token | Value |
|---|---|---|
| Protein | `macroProtein` | `#A3E635` (lime) |
| Carbohydrates | `macroCarbs` | `#38BDF8` (cyan) |
| Fat | `macroFat` | `#F97316` (amber) |

> `macro-purple` and the green-for-protein assignment of the original `DESIGN.md` are discarded: the Diary and Profile mockups consistently use lime/cyan/amber.

### 2.4 Mapping to Material 3 `darkColorScheme`
`primary = neonLime`, `onPrimary = onNeonLime`, `secondary = athleticCyan`, `tertiary = energyAmber`, `background = surface = surfaceBase`, `surfaceContainerLow = surfaceRaised`, `surfaceContainer/High = surfaceOverlay`, `onSurface = textPrimary`, `onSurfaceVariant = textSecondary`, `outline = borderStrong`, `outlineVariant = borderSubtle`, `error = #FFB4AB`, `errorContainer = #93000A`. Tokens M3 does not cover (macros, ghostData, electricGreen…) are exposed through an `AppColors` `CompositionLocal` accessible via `AppTheme.colors`.

## 3. Typography

Font: **Plus Jakarta Sans** (OFL), bundled in Compose Multiplatform resources (weights 400, 600, 700, 800). Every number uses `fontFeatureSettings = "tnum, lnum"`.

| Style | Size / line height | Weight | Tracking | Use |
|---|---|---|---|---|
| `displayHero` | 36/40 sp (phone), 48/52 sp (tablet) | 800 | −0.02 em | Ring kcal, current weight |
| `headlineMetric` | 26/30 sp (phone), 32/36 sp (tablet) | 700 | −0.01 em | Card values, timer |
| `titleLarge` | 20/26 sp | 600 | −0.01 em | Section/card titles |
| `titleMedium` | 16/22 sp | 600 | 0 | Exercise/food names, buttons |
| `bodyLarge` | 16/24 sp | 400 | 0 | Reading text |
| `bodyMedium` | 14/20 sp | 400 | 0 | Default text |
| `bodySmall` | 12/16 sp | 400 | 0 | Details, subtitles |
| `labelNumeric` | 18/22 sp | 700 | 0.02 em | KG/REPS inputs |
| `labelTechnical` | 11/14 sp, UPPERCASE | 600 | 0.06 em | Units and headers (KG, REPS, KCAL, PROT) |

## 4. Spacing, shape and elevation

- 8 dp grid with a 4 dp sub-grid. Tokens: `xs 4`, `sm 8`, `md 12`, `lg 16`, `xl 24`.
- 16 dp screen side margin; 12 dp between cards in a group; 24 dp between functional blocks; 16 dp card inner padding.
- Radii: `sm 4`, `base 8` (inputs, rows), `md 12`, `lg 16` (cards), `xl 24` (bottom sheet top corners), `full` (chips, pills, round checkbox, FAB).
- Elevation through layers (2.1). Shadows only on bottom sheets (`0 12 32 −4`, black 70%) and the active element "glow" (lime 15%, blur 16).

## 5. Adaptive layout

| Width (WindowSizeClass) | Navigation | Content |
|---|---|---|
| Compact (< 600 dp) | Bottom Navigation (4 tabs) + FAB | Single column |
| Medium / Expanded (≥ 600 dp) | NavigationRail with the FAB on top | Two asymmetric columns (summary/telemetry on the left, list/table on the right) |

## 6. Components to build (US-03)

Each component: a `@Composable` with explicit parameters, `Modifier` as the first optional parameter, a propagatable `testTag`, a preview and an entry in the catalog screen (debug only).

| Component | Description / reference |
|---|---|
| `AppTheme` | Colors, typography, shapes, `AppColors` |
| `AppTopBar` | Section title + avatar (opens Profile) |
| `AppNavigation` | Adaptive bottom bar / rail; active indicator in lime |
| `UniversalFab` | Lime pill "+ Registro", 52 dp tall, 0.97 scale on press |
| `AppCard` | `surfaceRaised`, radius 16, subtle border, padding 16 |
| `PrimaryButton` / `SecondaryButton` / `GhostButton` | Per DESIGN.md §Buttons; min 52 dp (primary) / 48 dp |
| `AppChip` / `FilterChipRow` | 32 dp visual height (48 dp target); active: cyan border and text |
| `SegmentedSelector` | Déficit / Mantenimiento / Superávit (active in lime) |
| `AppBottomSheet` | Top radius 24, 36×4 handle, 65% scrim |
| `ProgressRing` | kcal ring with normal / over-target (amber) states |
| `MacroProgressBar` | Linear bar in the macro color, "142 g / 180 g · 79 %" |
| `MacroSlider` | Slider in the macro color, % and grams/kcal |
| `NumberInput` | `surfaceOverlay` background, centered tabular numerals, 1.5 dp border on focus; ghost placeholder |
| `SetCompletionCheckbox` | 38 dp visual circle / 48 dp target; checked = lime fill + dark check + haptics |
| `SetRow` | Index · previous (ghost) · KG · REPS · checkbox |
| `RestTimerBar` | 48 dp, `surfaceOverlay`, cyan tabular countdown, bottom progress line, "+30s" and "Omitir" |
| `MetricTile` | Technical label + `headlineMetric` value + unit |
| `TrendPill` | Weekly rate pill (green/red/amber) |
| `SkeletonBox` | Animated placeholder (shimmer) |
| `EmptyState` / `ErrorState` | Icon + message + action |
| `OfflineBanner` | Banner below the Top Bar |
| `WipPlaceholder` | Work-in-progress screen container (E1 only) |

Icons: Material Symbols (Outlined) via `material-icons-extended` or imported vectors, with a `contentDescription` on every actionable icon.

## 7. Accessibility

- Minimum AA contrast (4.5:1 for normal text) on `surfaceBase`/`surfaceRaised`; the palette above meets it except `ghostData`, which is only used for supporting data, never as the sole information.
- Targets ≥ 48×48 dp.
- `contentDescription` on actionable icons; bars and rings expose their value as `stateDescription`.
- Font scaling up to 130% supported without clipping key numbers.
