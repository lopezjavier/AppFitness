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
| `borderSubtle` | `#FFFFFF` @ 8% | 1 dp border of tier 1 cards |
| `borderStrong` | `#FFFFFF` @ 12% | 1 dp border of tier 2 elements |
| `divider` | `#FFFFFF` @ 4% | Row separators inside cards |
| `textPrimary` | `#E0E2EA` | Primary text |
| `textSecondary` | `#C2CAB0` | Secondary text |
| `ghostData` | `#FFFFFF` @ 36% | Previous performance, placeholders |
| `scrim` | `#000000` @ 65% | Backdrop behind bottom sheets |
| `sheetHandle` | `#FFFFFF` @ 20% | Bottom sheet drag handle |

### 2.2 Accents
| Token | Value | Meaning |
|---|---|---|
| `neonLime` | `#A3E635` | Primary action, success, completed sets, active tab, kcal ring |
| `onNeonLime` | `#0B0F14` | Text/icons on lime |
| `athleticCyan` | `#38BDF8` | Training: timer, 1RM, volume, active filters, weight chart |
| `energyAmber` | `#F97316` | Warnings, thresholds, calorie excess |
| `electricGreen` | `#22C55E` | Favorable trend (weekly rate), "active/synced" states |
| `error` / `onError` | `#FFB4AB` / `#690005` | Errors and destructive actions (text, icons) |
| `errorContainer` / `onErrorContainer` | `#93000A` / `#FFDAD6` | Error containers |

### 2.3 Macros (fixed across the app)
| Macro | Token | Value |
|---|---|---|
| Protein | `macroProtein` | `#A3E635` (lime) |
| Carbohydrates | `macroCarbs` | `#38BDF8` (cyan) |
| Fat | `macroFat` | `#F97316` (amber) |

> `macro-purple` and the green-for-protein assignment of the original `DESIGN.md` are discarded: the Diary and Profile mockups consistently use lime/cyan/amber.

### 2.4 Mapping to Material 3 `darkColorScheme`
`primary = neonLime`, `onPrimary = onNeonLime`, `secondary = athleticCyan`, `tertiary = energyAmber`, `background = surface = surfaceBase`, `surfaceContainerLow = surfaceRaised`, `surfaceContainer/High = surfaceOverlay`, `onSurface = textPrimary`, `onSurfaceVariant = textSecondary`, `outline = borderStrong`, `outlineVariant = borderSubtle`, `error`/`onError`/`errorContainer`/`onErrorContainer` = the 2.2 tokens (set explicitly, not left to M3 defaults).

Roles not listed above are derived so no M3 baseline color leaks (T-020): `onSecondary`/`onTertiary` = `onNeonLime`; `onBackground` = `textPrimary`; `primaryContainer`/`onPrimaryContainer` = `neonLime`/`onNeonLime`; `secondaryContainer`, `tertiaryContainer`, `surfaceVariant` = `surfaceOverlay` (their `on*` = `textPrimary`); `surfaceContainerLowest`, `surfaceDim` = `surfaceBase`; `surfaceContainerHighest`, `surfaceBright` = `surfaceOverlay`; `surfaceTint` = transparent; `scrim` = `scrim`; `inverseSurface`/`inverseOnSurface`/`inversePrimary` = `surfaceOverlay`/`textPrimary`/`neonLime` (dark snackbar); `*Fixed`/`*FixedDim` = lime/cyan/amber with `on*Fixed*` = `onNeonLime`. Known exception: `outline` is about 1.3:1 on `surfaceBase`, so stock M3 outlined components are not used with default colors.

Tokens M3 does not cover (macros, ghostData, electricGreen…) are exposed through an `AppColors` `CompositionLocal` accessible via `AppTheme.colors`.

## 3. Typography

Font: **Plus Jakarta Sans** (OFL), bundled in Compose Multiplatform resources (weights 400, 600, 700, 800). Every number uses `fontFeatureSettings = "tnum, lnum"`, so it is set on every style (numbers also appear in body text). `labelTechnical` UPPERCASE is not a `TextStyle` property: callers pass `text.uppercase()` and keep the original casing in `contentDescription`.

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

- 8 dp grid with a 4 dp sub-grid. Tokens: `none 0` (no space; default for optional extra padding), `xs 4`, `sm 8`, `md 12`, `lg 16`, `xl 24`.
- 16 dp screen side margin (`screenMargin`); 12 dp between cards in a group (`cardGap`); 24 dp between functional blocks (`blockGap`); 16 dp card inner padding (`cardPadding`).
- Radii: `sm 4`, `base 8` (inputs, rows), `md 12`, `lg 16` (cards), `xl 24` (bottom sheet top corners), `full` (chips, pills, round checkbox).
- Elevation through layers (2.1). Shadows only on bottom sheets (`0 12 32 −4`, black 70%) and the active element "glow" (lime 15%, blur 16). The notation is x/offsetY/blur/spread in dp: tokens `AppElevation.sheetShadowOffsetY 12`, `sheetShadowBlur 32`, `sheetShadowSpread −4`, `glowBlur 16`, with colors `shadowSheet` (black 70%) and `glowLime` (lime 15%) in `AppColors`.

## 5. Adaptive layout

| Width (WindowSizeClass) | Navigation | Content |
|---|---|---|
| Compact (< 600 dp) | Bottom Navigation (3 tabs: Diario, Entreno, Perfil; ADR-37) | Single column |
| Medium / Expanded (≥ 600 dp) | NavigationRail | Two asymmetric columns (summary/telemetry on the left, list/table on the right) |

## 6. Components to build (US-03)

Each component: a `@Composable` with explicit parameters, `Modifier` as the first optional parameter, a propagatable `testTag`, a preview and an entry in the catalog screen (debug only).

| Component | Description / reference |
|---|---|
| `AppTheme` | Colors, typography, shapes, `AppColors` |
| `AppTopBar` | Section title only, no avatar (ADR-26, ADR-37); a variant with a back arrow for full-screen destinations |
| `AppNavigation` | Adaptive bottom bar / rail with 3 tabs (Diario, Entreno, Perfil); active tab = lime icon and extra-bold lime label + filled icon where the glyph has one, no pill (ADR-22). The Perfil tab icon is the generic Stitch `person` glyph (`design/stitch/progress/code.html`), tinted like the other tabs (ADR-37, supersedes the photo icon of ADR-26) |
| `AppCard` | `surfaceRaised`, radius 16, subtle border, padding 16 |
| `PrimaryButton` / `SecondaryButton` / `GhostButton` | Per DESIGN.md §Buttons; min 52 dp (primary and secondary) / 48 dp (ghost). The secondary is a filled, non-outlined action that sits next to a full-width primary (e.g. onboarding "Atrás"; Checkpoint E2-A rejected both a transparent ghost and an outlined version). **Secondary spec:** `surfaceOverlay` fill, `textPrimary` `titleMedium` label, no border, shape `md`, min height 52 dp (same as primary), 0.97 press scale; disabled = same fill with `ghostData` label. Paired in a footer with a full-width primary: 12 dp gap, secondary wraps content (min 104 dp), primary takes the rest |
| `AppChip` / `FilterChipRow` | 32 dp visual height (48 dp target); active: cyan border and text |
| `SegmentedSelector` | Déficit / Mantenimiento / Superávit (active in lime). All labels share one size (largest up to `bodyMedium` where the widest label, measured bold, fits on one line); never below 11 sp (`labelTechnical`); past that they wrap to 2 lines (hyphenated on API 33+) and all segments grow together |
| `AppBottomSheet` | Top radius 24, 36×4 handle, 65% scrim |
| `ProgressRing` | kcal ring with normal / over-target (amber) states |
| `MacroProgressBar` | Linear bar in the macro color, "142 g / 180 g · 79 %" |
| `MacroSlider` | Slider in the macro color, % and grams/kcal |
| `ViewModeSelector` | Detail / List / Summary segmented control for the diary (reuses `SegmentedSelector`; the chosen mode persists) |
| `PortionSelector` | Amount field + portion dropdown ("100 g", "1 huevo"); the unit is the food's base unit (`g`/`ml`) |
| `FavouriteToggle` | Star icon toggle (lime when starred) in food rows and food detail |
| `MealSlotEditor` | Rename, reorder, enable/disable and add meals (drag handle, switch, add row) |
| `MonthCalendarGrid` | Calendar month grid with a per-day kcal indicator; tap opens that day |
| `DiaryHistoryRow` | Date + kcal + P/C/F mini bars for a day, with a weekly/monthly summary header |
| `MacroStackedBar` | One horizontal bar split P/C/F by percentage in `macroProtein` / `macroCarbs` / `macroFat`; decorative segments, the whole bar exposes the split as `stateDescription` (T-037) |
| `AppAvatar` | Circular Google photo (Coil) with the placeholder person icon until it loads or on error; used large on the Perfil root (96 dp, no ring; §6.2), no longer in the navigation (ADR-37); decorative (T-037) |
| `AppTextField` | Single-line text input (T-041, name): label `bodyMedium` `textSecondary` above, `surfaceOverlay` fill, shape `base` (8), min height 52 dp, `bodyLarge` `textPrimary` left-aligned, 1.5 dp `athleticCyan` border on focus (like `NumberInput`), `error` border + supporting text when invalid, `maxLength` enforced, ghost placeholder, label as `contentDescription`, `ImeAction.Done`; `BasicTextField` based, state updated synchronously (same note as `NumberInput`). Lives in `designsystem/`, not in the screen |
| `NumberInput` | `surfaceOverlay` background, centered tabular numerals, 1.5 dp border on focus; ghost placeholder |
| `SetCompletionCheckbox` | 38 dp visual circle / 48 dp target; checked = lime fill + dark check + haptics |
| `SetRow` | Index · previous (ghost) · KG · REPS · checkbox |
| `ExerciseThumbnail` | Exercise image in list rows and detail (the `start` pose, cropped), `surfaceOverlay` background, radius `md`; placeholder icon when there is no media (custom exercises) or the image fails to load |
| `ExerciseAnimation` | Start↔peak crossfade of the two poses, 3 s cycle, `ease-in-out`, a brief pause on each pose (see §6.1) |
| `RestTimerBar` | 48 dp, `surfaceOverlay`, cyan tabular countdown, bottom progress line, "+30s" and "Omitir" |
| `MetricTile` | Technical label + `headlineMetric` value + unit |
| `TrendPill` | Weekly rate pill (green/red/amber) |
| `GoalCard` | Target weight and distance ("faltan 4,2 kg" / "objetivo alcanzado") with a progress bar |
| `ComparisonDeltaRow` | Two measurements compared: metric label + signed delta in color ("−1,4 kg") |
| `MetricInterpretationRow` | Metric value + status chip (low/normal/high) + short explanation; the list ends with the non-medical disclaimer |
| `CircumferenceRow` | Body-site label + value in cm + date, with edit/delete; the site picker is a dropdown |
| `SkeletonBox` | Animated placeholder (shimmer) |
| `EmptyState` / `ErrorState` | 48 dp decorative icon (`inbox` / `error`, Material Symbols, tinted `textSecondary` / `error`) + optional title + message + primary action (`Reintentar` is required on `ErrorState`); max width 360 dp, centred; `ErrorState` text is a polite live region |
| `OfflineBanner` | Full-width `surfaceRaised` strip below the Top Bar, 1 dp `borderSubtle` bottom line, 20 dp decorative `cloud_off` icon in `energyAmber` + "Sin conexión" `bodyMedium`; wraps at large font scale; polite live region; shown on every destination and layout (on the rail layout it spans the full width above the rail) |
| `WipPlaceholder` | Work-in-progress screen container (E1 only) |

Icons: Material Symbols (Outlined) via `material-icons-extended` or imported vectors, with a `contentDescription` on every actionable icon.

### 6.2 Perfil root and Preferencias (ADR-37, T-041)
**Perfil root** (tab destination: Top App Bar "Perfil" without back arrow, bottom bar/rail visible, no FAB). Single column, content max width `FormContentMaxWidth` (480 dp) centred on tablets; 16 dp side margins.
- Upper block (scrollable, centred horizontally, starts 24 dp below the bar): `AppAvatar` **96 dp** (`AppAvatarDefaults.RootSize`), no ring, decorative; 16 dp; display name in `titleLarge` `textPrimary`, centred, max 2 lines, polite-free (static text). Missing name shows nothing extra (the placeholder avatar stays).
- Action block (pinned to the bottom of the content area, above the bottom bar, 16 dp margins, full-width buttons):
  1. "Preferencias": `PrimaryButton` (the only forward action; lime), tag `profile_root_preferences_button`.
  2. 12 dp, then local flavor only: "Catálogo de componentes" `SecondaryButton`.
  3. 24 dp (`blockGap`) separator, then "Cerrar sesión": `GhostButton` tone `Neutral` (it is reversible, so not red; keeps the confirm dialog).
  4. 12 dp, "Borrar cuenta": `GhostButton` tone `Destructive` (red), disabled/loading rules of ADR-29. Both confirmations stay `AppConfirmDialog` (destructive-confirmation exception).
- States: loading = circle skeleton (96 dp, `AppShapes.full`) + name-line skeleton + the action block; load failure = `ErrorState` with "Reintentar" above the sign-out button (as today); offline banner is global.
- Tablet (>= 600 dp): same single column, centred (no two columns: the screen has no dense content).

**Preferencias** (full-screen destination: `AppTopBar` back-arrow variant titled "Preferencias", no bottom bar, no FAB; pinned "Guardar" `PrimaryButton` footer as in T-037). Order of cards (12 dp apart, 16 dp margins): "Datos personales" (first row = name `AppTextField`, label "Nombre"; then Sexo, Edad, Altura, Nivel de actividad, **Peso actual** as `EditableRow` with chevron that opens the existing `PickerSheet`), energy/strategy card, macro split card. "Peso actual" sheet = two `WheelPicker`s (kg · decimal, range 20,0..400,0 as in onboarding; extract `WeightWheels` to `feature/biometrics` next to `HeightWheel`, parameterised by `tagPrefix`), "Listo" applies. The old read-only hint (`profile_weight_hint`) is removed. From 600 dp: personal data + energy left, macros right (unchanged). Back with unsaved changes discards silently (no dialog). 

**Onboarding name field** (step 1 "Sobre ti", only when Google gave no name): above the sex selector, label "¿Cómo te llamas?", `AppTextField`, `maxLength` 100, autofocus off, `ImeAction.Done` hides the keyboard; "Continuar" disabled while blank.

### 6.1 Exercise media
- The catalog bundles the RepDB free assets: two poses (`start`/`peak`) per exercise (a single `start` image for exercises that have one pose); the thumbnail is the `start` pose. They live in `composeApp/src/commonMain/composeResources/files/exercises/` (ADR-30, ADR-39).
- `ExerciseAnimation` renders the poses with a **start↔peak crossfade**: a 3 s cycle, `ease-in-out`, and a brief pause on each pose (the free tier has no GIF/video). It honours the system "remove animations" setting by showing the `start` pose only.
- Attribution "Datos de ejercicios por RepDB (repdb.co)" is shown where the assets appear (catalog and exercise detail).

## 7. Accessibility

- Minimum AA contrast (4.5:1 for normal text) on `surfaceBase`/`surfaceRaised`; the palette above meets it except `ghostData`, which is only used for supporting data, never as the sole information.
- Targets ≥ 48×48 dp.
- `contentDescription` on actionable icons; bars and rings expose their value as `stateDescription`.
- Font scaling up to 130% supported without clipping key numbers.
- Auto-sized labels never go below 11 sp; past that they wrap to 2 lines.
- How it is verified: Robolectric + Compose UI tests (`androidHostTest`, run by `testLocalDebugUnitTest`) assert touch targets of every interactive node of the catalog (>= 48 dp), accessible labels, and the exact `stateDescription`/`contentDescription` strings; token and geometry values stay as unit tests in `commonTest`. Only haptics and listening with TalkBack remain manual (checkpoint E1-D).
