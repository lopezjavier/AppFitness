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

### 2.5 RPE scale (only inside `RpePickerSheet`, ADR-55)
`AppTheme.colors.rpeScale(value)` returns one colour per grid value, green (easy) to red (hard). The picker lists 10 at the top down to 6 (whole numbers, ADR-56). The nine stops below are spread over the five values and interpolated (RPE 6 = first stop, RPE 10 = last). Each row is `surfaceOverlay` tinted with the ramp colour at 16 %, with an 8 dp leading marker bar in the full colour; the number is always shown in `textPrimary` (colour is never the only cue) and the selected row adds a 1.5 dp border plus a check. Every colour is at least 3:1 against `surfaceOverlay`.

`AppTheme.colors.confidenceScale(value)` (ADR-72) reuses the same stops reversed for a whole value 1 to 10 (clamped): 1 = red end, 10 = green end. It colours only a graphic of at least 12 dp (the dot of the "Confianza 7/10" line, 3:1 against `surfaceOverlay`); the text is always `textPrimary`.

| Stop | Hex | Stop | Hex |
|---|---|---|---|
| 1 | `#22C55E` | 6 | `#FB923C` |
| 2 | `#65D04A` | 7 | `#EF4444` |
| 3 | `#A3E635` | 8 | `#E5383B` |
| 4 | `#D4D93A` | 9 | `#DC2626` |
| 5 | `#FACC15` | | |

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

> E9 (ADR-69): §8 (rules), §9 (templates and component specs) and §10 (glossary) prevail over any older row or paragraph of this file. Rows are updated as each component is built.

Each component: a `@Composable` with explicit parameters, `Modifier` as the first optional parameter, a propagatable `testTag`, a preview and an entry in the catalog screen (debug only).

| Component | Description / reference |
|---|---|
| `AppTheme` | Colors, typography, shapes, `AppColors` |
| `AppTopBar` | Section title, optional subtitle (`bodyMedium`, `textSecondary`, not a heading), no avatar (ADR-26, ADR-37); a variant with a back arrow for full-screen destinations; up to 2 trailing `AppTopBarAction` icon buttons (48 dp, required `contentDescription`), a 3rd or more go behind a ⋮ (`topbar_more_cd` "Más acciones") that opens an `AppActionSheet`. Tags `<prefix>_action_<n>`, `<prefix>_more`, `<prefix>_subtitle` (T-118) |
| `AppNavigation` | Adaptive bottom bar / rail with 3 tabs (Diario, Entreno, Perfil); active tab = lime icon and extra-bold lime label + filled icon where the glyph has one, no pill (ADR-22). The Perfil tab icon is the generic Stitch `person` glyph (`design/stitch/progress/code.html`), tinted like the other tabs (ADR-37, supersedes the photo icon of ADR-26) |
| `AppCard` | `surfaceRaised`, radius 16, subtle border, padding 16 |
| `PrimaryButton` / `SecondaryButton` / `GhostButton` | Per DESIGN.md §Buttons; min 52 dp (primary and secondary) / 48 dp (ghost). The secondary is a filled, non-outlined action that sits next to a full-width primary (e.g. onboarding "Atrás"; Checkpoint E2-A rejected both a transparent ghost and an outlined version). **Secondary spec:** `surfaceOverlay` fill, `textPrimary` `titleMedium` label, no border, shape `md`, min height 52 dp (same as primary), 0.97 press scale; disabled = same fill with `ghostData` label. Paired in a footer with a full-width primary: 12 dp gap, secondary wraps content (min 104 dp), primary takes the rest |
| `AppChip` / `FilterChipRow` / `AppToggleChip` | 32 dp visual height (48 dp target); active: cyan border and text. `AppChip`/`FilterChipRow` are single-select (`Role.RadioButton`); `AppToggleChip` is the multi-select variant (`Role.Checkbox`, `toggleable`, `stateDescription` "Activado"/"Desactivado", optional `contentDescription`, same look and targets), used for the available plates in Preferencias |
| `SegmentedSelector` | Déficit / Mantenimiento / Superávit (active in lime). All labels share one size (largest up to `bodyMedium` where the widest label, measured bold, fits on one line); never below 11 sp (`labelTechnical`); past that they wrap to 2 lines (hyphenated on API 33+) and all segments grow together |
| `AppTabRow` | Equal-weight section tabs under the top bar (Resumen / Historial / Cómo hacerlo). Selected = lime bold `titleMedium` label + 2 dp lime indicator; others `textSecondary` over a `divider` line. Each tab >= 48 dp (`Role.Tab`, `selected` semantics), label wraps to 2 lines at large font scales, never clipped. Stateless (`options`, `selectedIndex`, `onSelect`); tags come from each `AppOption` |
| `AppBottomSheet` | Top radius 24, 36×4 handle, 65% scrim, **top-only hairline** (`borderStrong`, 1 dp along the top edge and the two corner arcs, none on the sides or bottom; T-119). The hairline is drawn on the first child inside the sheet: a border or shadow passed through the sheet's own `modifier` sits outside its drag offset and was drawn away from the sheet (E9 items 27 and 38). The old drop shadow is gone for the same reason. Screens use the archetypes below, not this surface |
| `AppDatePickerDialog` | Platform calendar (Material3 `DatePicker`, opt-in confined to this wrapper) in the app colours, shown in a dialog (T-101, US-37). It only fills a draft: the screen that opened it saves with its own button. Stateless regarding the draft (`selected`, `onConfirm(date)`, `onDismiss`); `maxDate` makes later days unselectable (past-only fields pass today); no text-input mode. "Aceptar" is disabled until a day is selected, "Cancelar" and back dismiss. Tags `<tag>`, `<tag>_confirm`, `<tag>_dismiss`. Open state survives recreation. Catalog entry "Selector de fecha" |
| `ProgressRing` | kcal ring with normal / over-target (amber) states. The centre shows the consumed kcal and "de N kcal"; there is no "restantes" pill, and over target a pill reads "+N kcal en exceso" (T-121) |
| `MacroProgressBar` | Linear bar in the macro color, "142 g / 180 g" (grams only, no percent; T-121) |
| `MacroSlider` | Slider in the macro color, % and grams/kcal |
| `PortionSelector` | Amount field + portion dropdown ("100 g", "1 huevo"); the unit is the food's base unit (`g`/`ml`) |
| `CookingMethodSelector` | Four single-select chips (Crudo, A la plancha, Air fryer, Frito) under the amount in the add-to-meal sheet; only for `g` foods; Crudo selected by default; an impact line ("+27 kcal y +3 g de grasa" for 300 g at 1 %) updates with the method and the amount. In save-as-comida and the comida details, each solid food row shows a compact method chip that opens the same selector. 2×2 grid at large font scales; `Role.RadioButton`, full descriptions |
| `FoodPhotoAction` | `AppIconButton` (`photo_camera`, description "Añadir desde foto") next to the barcode icon, in the same row as the search field; opens a mode `AppBottomSheet` with "Estimar con IA", "Etiqueta nutricional" and "Código de barras", then a source sheet with "Hacer foto" and "Elegir de la galería". The system camera or the Android photo picker opens; the shared preview shows "Repetir" and "Usar foto", then "Estimando…" while the function responds |
| `WeekdaySelector` | Seven equal day chips "L M X J V S D" (48 dp targets, `Role.Checkbox`, full day names as `stateDescription`); every available day starts selected, assigned days are disabled and name the current comida. The row scrolls horizontally when the seven targets exceed the width (narrow phones or large font scales); it never wraps |
| `FavouriteToggle` | Star icon toggle (lime when starred) in food rows and food detail |
| `MealSlotEditor` | Rename, reorder, delete and add meals, opened from Perfil -> Preferencias (ADR-41). Editor on `AppEditorScreen` (T-126, R-11: nothing applies until "Guardar", removals included). Compact configuration rows (R-20), no card per meal: drag handle, name (tap opens the rename Name sheet) and a "Quitar" trash icon that only stages the removal, disabled with its reason in the row for a meal with entries and for the last meal; Subir/Bajar are accessibility actions only. "Añadir comida del día" (`AddItemButton`) at the end opens the create Name sheet (footer "Añadir"; rename "Listo"), then the "Comidas por defecto" `NavigationRow`, disabled with a visible reason while the draft is dirty. Rule for list rows that configure items: the name opens a sheet, the only icon buttons are the drag handle and trash, never a full button or card per item |
| `ReorderableColumn` / `DragHandle` | Plain `Column` whose items are reordered by dragging their `DragHandle` (T-120; 48 dp target, `drag_indicator` icon in `textSecondary`, labelled "Reordenar <item>"). Items keyed by a stable id; the handle drags at once after the touch slop (vertical only), the dragged item follows the finger (`zIndex` above the rest), the others make room, one Tick on pick-up and one per swap, and one `onMove(from, to)` is reported on drop. Inside a scroll container pass `onAutoScroll = { scrollState.scrollBy(it) }`: near the window edge it scrolls every frame. `Modifier.reorderActions()` of the item scope adds the TalkBack actions "Subir"/"Bajar" (screens with their own up/down keep them). First user: the routine builder (T-043) |
| `MonthCalendarGrid` | Calendar month grid with a per-day kcal indicator; tap opens that day |
| `DiaryHistoryRow` | Date + kcal + P/C/F mini bars for a day, with a weekly/monthly summary header |
| `DiaryGroupRow` | Comida group of a meal in the Diario (US-55): one merged `Role.Button` header (name `bodyLarge` max 2 lines, kcal `labelNumeric`, shared `MacroSummary`, rotating `ic_expand_more`), min height 48 dp, on `surfaceOverlay` with `AppShapes.base` and `Spacing.sm` padding; expanded ingredients are the usual `DiaryEntryRow`s indented `Spacing.md`. State via `diary_section_collapsed/expanded`. Feature component in its own file (`DiaryGroupRow.kt`) |
| `MacroStackedBar` | One horizontal bar split P/C/F by percentage in `macroProtein` / `macroCarbs` / `macroFat`; decorative segments, the whole bar exposes the split as `stateDescription` (T-037) |
| `AppBarChart` | Column chart of one series (T-115): `athleticCyan` bars with rounded tops, `divider` baseline and top guide, "0" and maximum labels on the value axis and about four evenly spaced labels below (`bodySmall`, `textSecondary`), no scroll, no animation. Canvas-based (no Vico column layer). The summary ("N series en el periodo, máximo N en <semana>") is the `stateDescription`; the caller tags it. Catalog entry "Gráfico de barras" |
| `RadarChart` / `RadarLegend` | Radar over >= 3 axes with up to two series (T-115): 4 `divider` guide rings and `borderSubtle` spokes, current = solid `athleticCyan` outline with 25 % fill, previous = dashed `textSecondary` outline (omitted when null), one shared scale (largest value), axis labels `bodySmall` wrapping at 72 dp, none drawn when every value is 0 (the screen adds the "Sin series en este periodo" text). One semantics node whose `stateDescription` lists each axis with its values. `RadarLegend` pairs each series with a line sample (solid / dashed), so it is never colour-only. Catalog entry "Radar" |
| `BodyHeatmap` / `BodyMapPaths` / `BodyHeatLegend` | Front and back body silhouettes side by side (T-115), drawn from in-house polygons (`BodyMapPaths`, 100 x 200 unit space, no external asset). Each muscle region is tinted `athleticCyan` in 4 alpha steps, one per quartile of the maximum (0 sets = `surfaceOverlay`), shows its value inside when wide enough (dark text from step 3 for AA), and is a `Role.Button` hit box of at least 48 dp described "<músculo>, N series" (a group drawn on both sides keeps the plain tag on the front and `_back` on the back). The screen always adds a visible per-muscle list as the reliable tap path. `BodyHeatLegend` shows the 4 swatches "Menos" to "Más series". Catalog entry "Mapa corporal" |
| `AppAvatar` | Circular Google photo (Coil) with the placeholder person icon until it loads or on error; used large on the Perfil root (96 dp, no ring; §6.2), no longer in the navigation (ADR-37); decorative (T-037) |
| `AppTextField` | Single-line text input (T-041, name): label `bodyMedium` `textSecondary` above, `surfaceOverlay` fill, shape `base` (8), min height 52 dp, `bodyLarge` `textPrimary` left-aligned, 1.5 dp `athleticCyan` border on focus (like `NumberInput`), `error` border + supporting text when invalid, `maxLength` enforced, ghost placeholder, label as `contentDescription`, `ImeAction.Done`; `BasicTextField` based, state updated synchronously (same note as `NumberInput`). Lives in `designsystem/`, not in the screen. **v2 (T-119):** optional `leading` / `trailing` 48 dp slots inside the box (clear, unit, icon), `imeAction` + `onImeAction` (null keeps "Hecho" = hide keyboard, clear focus, `onDone`), `keyboardType`, `capitalization` (default Sentences) and `autofocus` (focus + keyboard on open). The error line is a polite live region with `error()` semantics. Label style and focus colour come from the shared `AppInputStyle`, the same as `NumberInput` (R-24). |
| `NumberInput` | `surfaceOverlay` background, centered tabular numerals, 1.5 dp `athleticCyan` border on focus (same `AppInputStyle` as `AppTextField`: sentence-case `bodyMedium` `textSecondary` label above, not uppercase; the `showLabel = false` table cell keeps its compact form); placeholder in `textSecondary` (planned values are editable data, AA; entered text is `textPrimary`). **v2 (T-119):** the whole value is selected on focus (typing replaces it, R-25), `errorText` (+ `errorTestTag`) shows inline with `error()` semantics and an error border, `unit` is drawn at the end of the field, `onImeAction` overrides the keyboard action; `NumberInputDefaults.rangeError(value, min, max, unit, template)` builds "Entre 30 y 300 kg" (string `number_range_error`) |
| `AppSearchField` | Search input (T-119, R-26) on `AppTextField`: decorative `ic_search` leading, clear `AppIconButton` "Borrar búsqueda" (`<prefix>_clear`, or `clearTestTag`) trailing only while the text is not empty and the field is enabled, keyboard action "Buscar" (`ImeAction.Search`) hides the keyboard and calls `onSearch` (live searches pass an empty lambda), no auto-capitalization. Used by the food search and the exercise picker |
| `SetCompletionCheckbox` | 38 dp visual circle / 48 dp target; checked = lime fill + dark check + haptics |
| `HelpButton` | In-app help affordance (T-097d, ADR-55): a "?" `AppIconButton` (48 dp, `ic_help`, `textSecondary`) placed as an icon in a title/header row, never a full-width button. Opens an `AppInfoSheet` with a `titleLarge` heading, a `bodyLarge` body and the X "Cerrar"; the body is a `HelpContent(paragraphs, bullets)` (the `body: String` overload is one paragraph); closes with X, back, scrim or swipe. Description "Ayuda: <tema>". Tags `<tag>`, `<tag>_sheet`, `<tag>_sheet_close`, `<tag>_sheet_body`, `<tag>_sheet_bullet_<n>`. Open state survives recreation |
| `SetRow` | Index · previous (ghost) · KG · REPS · checkbox |
| `ExerciseThumbnail` | Exercise image in list rows and detail (the `start` pose, cropped), `surfaceOverlay` background, radius `md`; placeholder icon when there is no media (custom exercises) or the image fails to load |
| `ExerciseAnimation` | Start↔peak crossfade of the two poses, 3 s cycle, `ease-in-out`, a brief pause on each pose (see §6.1) |
| `RestTimerBar` | 48 dp, `surfaceOverlay`, cyan tabular countdown, bottom progress line, "+30s" and "Omitir" |
| `MetricTile` | Technical label + `headlineMetric` value + unit |
| `TrendPill` | Weekly rate pill (green/red/amber) |
| `AppLinearProgress` | Plain progress bar towards a goal (T-123): `athleticCyan` fill on a `divider` track, rounded, spoken through a state description ("Progreso hacia el objetivo, 47 %"). Fills the `progress` slot of the "Objetivo" `MetricCard` (target and distance wording by strategy, `04-business-rules.md` §3.5; progress from the first weight to the target) |
| `MetricInterpretationRow` | Metric value + status chip (low/normal/high) + short explanation; the list ends with the non-medical disclaimer |
| `MeasurementRow` | A measurement on `AppListRow` (T-123, R-16): value, date in the list format, optional leading icon, one merged node with the click label of its edit sheet; swipe-to-delete is added by the caller. Sites are entered all at once in the sheet, each with a `?` `HelpButton` |
| `SkeletonBox` | Animated placeholder (shimmer) |
| `EmptyState` / `ErrorState` | 48 dp decorative icon (`inbox` / `error`, Material Symbols, tinted `textSecondary` / `error`) + optional title + message + primary action (`Reintentar` is required on `ErrorState`); `ErrorState` takes an optional `secondaryAction` (`SecondaryButton` under the primary, 12 dp gap; e.g. "Crear manualmente" in the photo flow); max width 360 dp, centred; `ErrorState` text is a polite live region |
| `ActionRow` | Tappable row of a choice sheet (E7 photo mode and source sheets): decorative 24 dp icon + `titleMedium` title + one-line `bodyMedium` `textSecondary` hint, >= 48 dp, `Role.Button`, 16 dp side margins, read as one node ("title, hint") |
| `InlineNote` | Subtle form hint (E7 "Estimado por IA · revisa los valores"): decorative info icon + `bodyMedium` `textSecondary` text on `surfaceOverlay`, shape `md`, 12 dp padding; not interactive |
| `OfflineBanner` | Full-width `surfaceRaised` strip below the Top Bar, 1 dp `borderSubtle` bottom line, 20 dp decorative `cloud_off` icon in `energyAmber` + "Sin conexión" `bodyMedium`; wraps at large font scale; polite live region; shown on every destination and layout (on the rail layout it spans the full width above the rail); drawn only by the shell (single banner, R-30): screens never draw it, they show `OfflineState` or `OfflineHint` |
| `AppScreen` | Screen template (T-118): `AppTopBar` v2 (`testTagPrefix` gives `<prefix>`, `_title`, `_subtitle`, `_back`, `_footer`), body capped at `FormContentMaxWidth` (480 dp) and centred, optional footer pinned above the keyboard (capped the same way) that publishes its height so `AppMessageHost` floats above it. Pads only the system insets the shell has not consumed (the shell consumes them for `AppScreen` routes, so nothing is doubled). Optional `header` slot (T-121, tab roots, D1) replaces the bar with a compact row of the caller's content (min 48 dp, top inset, `borderSubtle` hairline, tag `<prefix>_header`); `title` stays the semantic name. The Diario root uses it for its date switcher with `ShellChrome.TabScreen` (own header, bottom bar and rail kept). |
| `AppEditorScreen` | `AppScreen` + pinned "Guardar" (`<prefix>_save`; enabled when dirty, not saving, online; offline: disabled + `OfflineHint`, a tap vibrates one Reject), optional destructive `GhostButton` (`<prefix>_delete`), discard dialog "¿Descartar los cambios?" (`<prefix>_discard_dialog`, `_discard_confirm`, `_keep_editing`; survives recreation), `saved = true` closes once with one Confirm. System back and the bar's arrow share one path. |
| `AppActionSheet` | Actions archetype of the sheet family (T-118): optional title, X "Cerrar" (`action_sheet_close`), one `ActionRow` per `AppSheetAction` (`action_sheet_row_<n>`; the hint is optional); a row runs its action then the sheet animates out. Its header is the shared `AppSheetHeader` of `AppSheets.kt`. |
| `AppEditSheet` | Edit archetype (T-119, R-07, R-09, R-10, R-12): shared header (title heading, optional "?" `help` slot left of the X "Cerrar", `<prefix>_close`), a body that scrolls, and a full-width `PrimaryButton` footer outside the scroll with `imePadding` + `navigationBarsPadding` so it stays visible with the keyboard open. `SheetCommit.Save` = "Guardar", `SheetCommit.Apply` = "Listo"; tag `<prefix>_commit`, disabled while `!commitEnabled` or `saving` (`loading`); optional `commitHint` (`offlineHint("guardar")`) as `bodySmall` under it (`<prefix>_commit_hint`); optional `secondaryAction` slot between the button and the hint (a destructive "Eliminar" `GhostButton`, T-121). The title is tagged `<prefix>_title`. X, back, scrim and swipe call only `onDismiss`, never `onCommit`. Content is capped to the form width (480 dp) on wide windows. **Caller contract:** the sheet neither closes itself nor vibrates. Save: set `saving` while writing; on success close the sheet, fire `HapticEvent.Confirm` and add a message only if the change is not visible (R-13); on failure stay open, `HapticEvent.Reject`, error message (R-37), clear `saving`. Apply: close at once and no haptic (the screen underneath owns the "Guardar"). The ux-reviewer checks this per sheet in T-121…T-127 |
| `AppPickerSheet`, `AppInfoSheet` | Picker and Info archetypes (T-119): same header (the picker accepts the `help` slot), a scrolling body and no footer. Picker content applies on tap and the caller closes; Info is read-only. Tags `<prefix>`, `<prefix>_close` |
| `NameSheet` | Name sheet (T-119, R-15): an `AppEditSheet` (footer `commit`, default `Save`; `Add`/`Apply` when the sheet only stages a change in a draft, T-126) with one `AppTextField` that is focused on open, `ImeAction.Done` and sentence capitalization. "Hecho" and the button share one save that trims the name, runs once per edit and never while `saving`. `NameSheetDefaults` compares names ignoring case and surrounding spaces; a rename passes its current name as `initialName` so keeping it is allowed. After the first edit a duplicate shows "Ya existe <itemLabel> con ese nombre" (`name_sheet_input_error`) and "Guardar" stays disabled; the check is client side only. The typed text is restored after recreation. Tags `name_sheet`, `name_sheet_input`, `name_sheet_commit`, `name_sheet_close` |
| `AppMessages` / `AppMessageHost` | Shell-level FIFO queue (one visible, 4 s, no button, R-28); the host draws a `surfaceOverlay` card (`app_message`, polite live region) above the bottom bar and any pinned footer. A message outlives the destination that sent it; it is not restored after process death. |
| `AppHaptics` | `LocalAppHaptics`: `armed/disarmed/confirm/reject/tick` (R-29); `SetCompletionCheckbox` fires Confirm on check only. |
| `OfflineState` | `cloud_off` + "Sin conexión" / "Conéctate para ver estos datos." in the look of `EmptyState`, no button, polite live region (`offline_state`); reloads by itself once per reconnection. `OfflineHint` (`offline_hint`, `bodySmall`, `textSecondary`) is "Sin conexión: no se puede <acción>". |
| `AppDateFormat` | Hoy / Ayer / "lun, 24 oct" in lists, "24 de octubre" as a title, "octubre 2026" for a month (R-38); pure Kotlin, no locale APIs. |
| `SwipeToDeleteRow` | Shared swipe-to-delete (T-120, own horizontal drag, right-to-left only, no Material `SwipeToDismissBox`): arms at 50 % of the row width (one Armed vibration, layer turns from muted to `error` with a 1.2x icon and a bold word), disarms below 40 % (one Disarmed), releasing armed slides the row out (~200 ms), collapses its height (~200 ms), then calls `onDelete` and fires one Confirm; releasing unarmed or any unarmed fling springs back (damping 0.8) without vibrating. `label` is "Eliminar" (saved records) or "Quitar" (editors, `action_remove`). TalkBack action "Eliminar <name>" runs the same removal with one Confirm; the Root only fires Reject on failure. Reduced motion snaps, vibrations stay. Row >= 48 dp, no undo; a row the caller keeps after `onDelete` shows itself again |
| `AppListRow` | Generic row (T-120): optional `leading`/`trailing` slots, `bodyLarge` headline (2 lines), optional `bodyMedium` `textSecondary` supporting line, >= 48 dp; with `onClick` the whole row is one `Role.Button` node. The caller passes the tag |
| `NavigationRow` | Row that opens another screen (T-120, R-03): label, optional value in `textSecondary` and the decorative `ic_chevron_right`; >= 48 dp, one `Role.Button` node. `enabled = false` (T-126) makes it non-clickable with reduced emphasis and disabled semantics; the caller shows the reason as visible text. Use sites migrate in T-127 |
| `ListSectionHeader` | Group title (T-120): `titleLarge`, heading semantics, optional count in `textSecondary` (no pill). Distinct from the feature-private `SectionHeader` copies, which migrate later |
| `FoodEntryRow` | A logged food on `AppListRow` (T-121, R-16): name, detail ("1 vaso (200 ml)"), kcal and whole-gram P/C/G in the macro colours. Input is the plain `FoodEntryRowModel`; with `onDelete` it is wrapped in `SwipeToDeleteRow` ("Eliminar <name>" TalkBack action). Tags `<prefix>_<id>`, `_detail_<id>`, `_delete_<id>` |
| `FoodSheet` | The food sheet on `AppEditSheet` (T-121): name as the title, brand, optional ★ and ✎ in the header, `quantity`, live `totals`, collapsible `details` (expanded state saved, declared above the sheet window) and `extra` slots; the footer comes from a sealed `FoodSheetContext`. Only `Edit` exists (Guardar + stacked destructive Eliminar, one offline hint `offlineHint("guardar ni eliminar")`); T-122 adds the add and comida contexts |
| `AddItemButton` | End-of-list add action (T-120, R-19): full-width `SecondaryButton` "Añadir <objeto>" (`add_item`) with the lower-case noun from the caller |
| `MetricCard` | `AppCard` with the `MetricTile` label/value styles (T-120), optional `supporting`, `trailingAction` and `progress` slots and optional `onClick` with an optional `clickLabel`. Without a `trailingAction` the whole card is one `Role.Button` node; with one, only the label and value block is the button and `supporting` and `trailingAction` are sibling nodes, so a pencil or a "Sincronizar" button may sit in the card (ADR-48, T-123) |
| `TimeRangeSelector` | Range chooser (T-120, R-33/R-34) on `SegmentedSelector`: enum `TimeRange` (1S 1M 3M 6M 1A Todo), each screen passes 2 to 5 of them in order (asserted), spoken as "1 semana", "1 mes", "3 meses", "6 meses", "1 año", "Todo"; tags `<tagPrefix>_<range>` |
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

**Preferencias** (full-screen destination: `AppTopBar` back-arrow variant titled "Preferencias", no bottom bar, no FAB; pinned "Guardar" `PrimaryButton` footer as in T-037). Order of cards (12 dp apart, 16 dp margins): "Datos personales" (first row = name `AppTextField`, label "Nombre"; then Sexo, Edad, Altura, Nivel de actividad, **Peso actual** as `EditableRow` with chevron that opens the existing `PickerSheet`), energy/strategy card, macro split card, the E7 **"Cocina"** card, then the existing "Comidas" card. "Peso actual" sheet = two `WheelPicker`s (kg · decimal, range 20,0..400,0 as in onboarding; extract `WeightWheels` to `feature/biometrics` next to `HeightWheel`, parameterised by `tagPrefix`), "Listo" applies. The old read-only hint (`profile_weight_hint`) is removed. From 600 dp: personal data + energy left, macros and Cocina right (unchanged layout). Back with unsaved changes follows R-14 (ADR-69); the screen becomes a settings hub in E9 (D9).

**Cocina (E7, US-50):** three numeric fields "Grasa añadida a la plancha (% del peso crudo)", "Grasa añadida en air fryer (%)" and "Grasa añadida al freír (%)", each 0–20 % with the food's `NumberInput`; helper "Porcentaje del peso crudo que se añade como grasa." Saves with the single Preferencias "Guardar".

**Onboarding name field** (step 1 "Sobre ti", only when Google gave no name): above the sex selector, label "¿Cómo te llamas?", `AppTextField`, `maxLength` 100, autofocus off, `ImeAction.Done` hides the keyboard; "Continuar" disabled while blank.

### 6.3 Food capture and default comidas (E7)

- **Add food from a photo (US-48/US-49):** the photo `AppIconButton` sits directly below the scanner icon, same size and alignment, description "Añadir desde foto". The mode sheet lists three rows with icon + title + one-line hint; closing it returns to the search. Choosing a mode opens a small source sheet with "Hacer foto" and "Elegir de la galería" (the Android photo picker, no media permission). Both sources use the same preview with "Repetir" and "Usar foto" (no in-app CameraX preview or gallery browser). Dish mode adds an optional comment field ("Añade un detalle: ingredientes, aliño…", max 200) before "Usar foto"; label and barcode modes do not. Every AI result, including barcode, opens the normal `CreateFoodScreen` with a subtle "Estimado por IA · revisa los valores" note (removed as soon as the user edits any field). The scanner icon and its live flow are untouched.
- **States:** idle, mode/source sheets, capturing or picking, "Estimando…" (progress + cancel disabled), prefilled form, and error `ErrorState` with "Reintentar" + "Crear manualmente" for every photo mode, including barcode ("No hemos podido identificar este producto"). Cancelling the camera or the picker returns to the source sheet without error. Offline disables the action with the standard offline message. A photo is discarded on every outcome; no thumbnail is kept in the app.
- **Cooking selector (US-50):** under the amount/portion in the add-to-meal sheet, a four-option selector; only for `g` foods. The impact line uses the macro colour for fat and `textSecondary` for the kcal part, e.g. "+22,5 kcal y +2,5 g de grasa"; Crudo shows no line. The selector is hidden for `ml`. Changing it recalculates the totals live without touching the food. When saving a meal as a comida or editing a comida, each solid food row shows a compact method chip ("Crudo" by default) that opens the same selector; the comida total kcal includes the cooking impact with the current Preferencias.
- **Default comidas (US-51):** entry is a `SecondaryButton` "Comidas por defecto" at the end of the Comidas card, disabled while the meal structure has unsaved changes. The destination is full-screen ("Comidas por defecto", back arrow, pinned "Guardar"), with one card per meal in its saved order; each card shows the assigned saved comidas as rows (name, food count, kcal) and an "Añadir comida" action. The picker is a bottom sheet listing saved comidas with name, food count and kcal; no saved comidas → `EmptyState` "No tienes comidas guardadas" and a hint to create one from "Añadir comida" → "Comidas". Each assignment row has a remove icon (no confirmation); if every day is assigned, "Añadir comida" is disabled with "Todos los días ya tienen una comida". The weekday selector sits inside the assignment sheet: all available days selected, conflicts disabled with the current comida named below. Empty selection disables "Guardar" with "Selecciona al menos un día". Leaving with unsaved changes asks to discard (same pattern as Comidas (MealSlots)). The note "Se añadirán al abrir el Diario de cada día seleccionado. Los cambios se aplican a partir del próximo día." sits under the meal list. Applying a default uses the methods saved in the comida and the current added-fat percentages.

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

## 8. Interaction rules and UX rulebook (ADR-45, amended by ADR-69)
Generic rules for every screen, cited by number. ux-reviewer rejects a plan that breaks one of them, and checks the implemented diff too. Each rule is testable; a convention stated by the user at a checkpoint is written here, not in the E9 table.

### Navigation
- **R-01 Titles.** Tab roots show their own compact header, never a bar that repeats the tab label. A screen about one
  object is titled with the object's name. An editor is titled with verb + object ("Nueva rutina", "Editar rutina").
- **R-02 Object actions.** Actions for the object on screen go in the top bar: at most 2 icons (for example ✎, 🗑)
  plus ⋮, which opens an action sheet with the rest. Never an extra icon row under the bar; never inside one tab or
  section.
- **R-03 Same kind of destination, same affordance.** A navigation entry is a `NavigationRow` (label, optional value,
  chevron). The history or calendar of X is a calendar icon in X's top bar.
- **R-04 Labels match titles.** An entry's label equals the title of the screen it opens.
- **R-05 Bottom navigation** appears only on tab roots; every other screen has a back arrow.

### Templates
- **R-06** Every screen is built on `AppScreen` (to read) or `AppEditorScreen` (to edit). No screen draws its own top
  bar, footer, message host or offline banner.
- **R-07** Every bottom sheet is one of four archetypes:
  - **Edit:** a form with a pinned footer;
  - **Picker:** one choice that applies on tap, no footer;
  - **Actions:** a menu;
  - **Info:** help or read-only content.

  Each has a header (title, optional "?", a close X described as "Cerrar"), a scrolling body and, where it applies, a
  pinned footer.
- **R-08 Dialogs** appear only to:
  1. delete the account;
  2. sign out;
  3. delete many records at once (a folder with its routines);
  4. discard an in-progress workout;
  5. discard unsaved changes (R-14).

  Nothing else gets a dialog.
- **R-09** The primary action of a sheet or editor is pinned and always visible.

### Editing
- **R-10** Every edit surface has an explicit "Guardar". Closing it (X, back) never applies
  anything.
- **R-11** Inside an editor, every change waits for "Guardar" and is applied together, removals included. Immediate
  deletion exists only outside editors (in a list or on a details screen).
- **R-12** "Guardar" writes to the server. "Listo" applies to the unsaved changes of the screen underneath, which has
  its own "Guardar". Single-choice pickers apply on tap, with no "Listo".
- **R-13** After a successful "Guardar", the editor closes and returns to where it was opened, with a Confirm haptic.
  A message appears only if the change is not visible there.
- **R-14** A full-screen editor with unsaved changes asks "¿Descartar los cambios?" [Descartar] [Seguir editando] on
  back or X. With no changes, it leaves silently. Sheets always discard silently.
- **R-15** Creating or renaming a named item that has no editor of its own (meal, folder, "Guardar como comida",
  "Guardar como rutina") uses the **Name sheet**:
  - one field that receives focus at once;
  - the keyboard's "Hecho" saves when the name is valid;
  - names are unique per user, ignoring case and surrounding spaces.

### Lists, rows, add and delete
- **R-16** Each object type has one row component, showing the same information everywhere:
  - `FoodEntryRow`;
  - `ExerciseRow`;
  - `WorkoutRow`;
  - `MeasurementRow`;
  - `NavigationRow`.
- **R-17** Tapping a row opens its details (a sheet or a screen); editing starts from the details. In a picker
  (select mode), tapping a row picks it, and the thumbnail or ⓘ opens the details.
- **R-18** Tapping the name of a food or an exercise opens its details, on every screen.
- **R-19** Actions on a row are icon buttons on one line. Adding a new item to a list is one labelled `AddItemButton`
  at the end of the list ("Añadir <objeto>").
- **R-20** Where delete lives:
  - a record in a list: swipe, plus the same action in its sheet or details;
  - a configuration row (meals, folders, plates): a trash icon in the row;
  - the object on screen: the top bar.

  Deleting never offers "Deshacer".
- **R-21** Swipe-to-delete uses the shared component and behaves as specified in §14:
  - right to left only; "Eliminar" for saved records, "Quitar" inside editors;
  - it arms at 50 % of the row width, with a visual change and an Armed haptic;
  - it disarms below 40 %, with a Disarmed haptic;
  - releasing while armed deletes, with a Confirm haptic: the row slides out, the list closes the gap and the item
    leaves the screen at once;
  - releasing before the line, or a fling that is not armed, springs back;
  - rows have stable keys.
- **R-22** Reordering uses the shared component:
  - the handle drags at once;
  - Tick haptics on pick-up and each time the row moves past another;
  - the list scrolls by itself near the edges;
  - TalkBack custom actions "Subir" / "Bajar".
- **R-23** Lists are lazy and animate insertions, removals and moves.

### Forms
- **R-24 Text fields.**
  - The label sits above the field, in sentence case.
  - All inputs share one label style and one focus treatment.
  - An optional trailing slot holds clear, ✓ or a unit.
  - Names start with a capital letter (sentence capitalization).
  - The keyboard shows "Siguiente" between fields and "Hecho" on the last one; in a one-field sheet, "Hecho" saves.
- **R-25 Numeric fields** select their whole value on focus, use the decimal keyboard and check ranges inline:
  "Entre X y Y <unidad>".
- **R-26 Search fields** use `AppSearchField`: search icon, clear button, keyboard "Buscar".
- **R-27 Validation** appears under the field after the first edit, while "Guardar" stays disabled. Messages follow
  the templates in Appendix A.

### Feedback and states
- **R-28 Messages.** There is one app-level message host, above the bottom bar and any pinned footer. Messages
  survive navigation and have no action buttons.
- **R-29 Haptics** go through `AppHaptics` (§14.4), with one vibration per action:
  - Confirm: saved, added, deleted, set completed.
  - Reject: an action was refused or failed.
  - Armed / Disarmed: a gesture crosses its threshold (swipe, drag pick-up).
  - Tick: a stepper step, a reorder step.
  - Nothing else vibrates. Alerts such as the end of a rest are not UI feedback; they use the alert pattern in
    §14.4.
- **R-30 Offline.**
  - Only the shell shows the "Sin conexión" banner.
  - A write action disabled offline shows the standard hint under it.
  - A load that fails offline shows `OfflineState`, which retries by itself when the connection returns.
- **R-31 States.**
  - Loading is a skeleton shaped like the final layout.
  - Empty is an `EmptyState` with at most one action.
  - Error is an `ErrorState` with "Reintentar".
  - An action that waits for the server or the AI (for example a photo estimate) never ends silently: it shows
    progress ("Estimando…"), then its result in place or an error message with "Reintentar". Every outcome is visible.
- **R-32 Help** is always `HelpButton`, never text that expands in place.

### Selection controls
- **R-33** Each control has one use:
  - **Tabs** (`AppTabRow`) switch between sibling views of one screen.
  - **A segmented control** (`SegmentedSelector`) picks one of 2–5 short options: a setting, a view mode, a range.
  - **Chips** filter a list or pick among many or long options.

  A control never changes type with the number of options.
- **R-34** The same concept always uses the same control and the same labels. Chart ranges use `TimeRangeSelector`:
  "1S 1M 3M 6M 1A".
- **R-38** A bottom sheet closes only with its X, the system back button or, where it has one, "Cancelar" (ADR-71).
  Dragging it down or tapping outside does not close it, and no sheet shows the drag handle (the small horizontal
  line).
- **R-39** Where an icon clearly represents an action (photo, add, remove, retry, scan), the control is an icon with a
  contentDescription; text buttons are for actions with no clear icon (ADR-71).
- **R-41** A quality or confidence level is never shown by colour alone: it is a colour from a design-system scale
  (low = red to high = green, `AppColors.confidenceScale`, built on the RPE ramp stops, §2.5) plus text ("Confianza
  7/10"), and the node's contentDescription/`stateDescription` carries the same text for TalkBack (ADR-72).

### Wording
- **R-35** The glossary (Appendix A) is binding: one label per concept, and one concept per label.
- **R-36** Generic actions use the shared `action_*` strings.
- **R-37** Messages follow fixed templates:
  - errors: "No se pudo <acción>. Inténtalo de nuevo.";
  - offline: "Sin conexión: no se puede <acción>".

  Sentences end with a period; titles and buttons do not. Use "…", never "...". Titles take a capital letter only on
  the first word.
- **R-38 Dates and numbers.**
  - Dates come from `AppDateFormat` in three forms:
    - in a list: "Hoy", "Ayer", "lun, 24 oct";
    - in a title: "24 de octubre";
    - a month: "octubre 2026".
  - Numbers come from `AppNumberFormat`.
  - Macro grams are whole numbers outside nutrition tables.

### Process
- **R-39 Sweep.** A task that adds or changes a pattern updates every other instance of it (found with the searches in
  §11), or records each one in the E9 table with an owning task. A new designsystem component retires its ad-hoc
  copies within the same epic.
- **R-40 Guards.** `check-ux-conventions.sh` passes.

### Decided — do not re-propose

ux-reviewer rejects any plan that brings these back:
- No "Deshacer" anywhere (ADR-45, T-066, E6 Q2).
- No confirmation dialogs outside R-08.
- An explicit "Guardar" on every edit surface; nothing applies on close (ADR-45, T-075).
- Pickers with one choice apply on tap, with no "Quitar tipo" or "Quitar RPE"; "Normal" resets the type (ADR-55).
- RPE is a whole number from 6 to 10 (ADR-56, T-097j).
- Three tabs (Diario, Entreno, Perfil) and no floating action button (ADR-36, ADR-37).
- Preferencias and the statistics hang off Perfil.
- The meal structure is edited from Preferencias, never from the Diario (ADR-41).
- A Health Connect weight wins; today's weight is edited with the pencil, not a button (ADR-47, ADR-48).
- Help is explained in the app, not in the chat (ADR-55).
- State survives leaving the app (ADR-40).
- Meals are deleted, never disabled, and at least one remains (ADR-41).
- Saved meals are called "comidas" (ADR-42), told apart from meal slots by Appendix A.
- No "Comparar" (ADR-48).

## 9. Patterns and templates (ADR-69)
Each component gets a catalog entry, host tests and a short spec in §6. Together they make the rules the default; until a template exists, plans cite the rule and the reference screen instead.

Audit IDs in the last column refer to [`ux-audit-2026-10.md`](ux-audit-2026-10.md).

| Component | Purpose and spec | Replaces or fixes |
|---|---|---|
| `AppTopBar` v2 | Title, optional subtitle, back, `actions` (≤ 2 `AppIconButton` + ⋮ → `AppActionSheet`), optional collapse on scroll for long lists. 56 dp on full-screen destinations. | NAV-1, NAV-2, the custom live bar |
| `AppScreen` | Screen template: `AppTopBar` v2, content width capped by window size, optional pinned footer, wired to `AppMessages`. Screens own their bar; the shell keeps the bottom navigation, the offline banner and the message host. | RC2, NAV-3 |
| `AppEditorScreen` | `AppScreen` plus: <ul><li>"Guardar" pinned, with an optional destructive action;</li><li>the discard dialog when leaving with unsaved changes (R-14);</li><li>closes on success with a haptic (R-13);</li><li>the standard offline hint (R-30);</li><li>room for the keyboard.</li></ul> | EDT-2, EDT-3, FBK-2 |
| Sheet archetypes | `AppEditSheet(commit = Save or Apply)`, `AppPickerSheet`, `AppActionSheet`, `AppInfoSheet`. All share the header and the pinned footer; the keyboard's "Hecho" saves when valid. Built on `AppBottomSheet` with a **top-only hairline** instead of the full outline. | SHT-1…3, EDT-4 |
| Name sheet | An `AppEditSheet` with one `AppTextField` that receives focus at once, and a uniqueness check passed in by the caller. | EDT-5, items 20–22 and 25 |
| `FoodSheet` | One sheet for a food with a quantity: <ul><li>header with name, brand, ★, and ✎ for manual foods;</li><li>quantity and portion;</li><li>live totals;</li><li>collapsible details (per 100, micronutrients);</li><li>a footer that depends on context.</li></ul> | LST-1, item 12 |
| Rows | <ul><li>`AppListRow`: leading element, headline, supporting text, trailing element; 48 dp or more.</li><li>`FoodEntryRow`.</li><li>`ExerciseRow`: thumbnail; the name opens the detail.</li><li>`WorkoutRow`.</li><li>`MeasurementRow`.</li><li>`NavigationRow`: label, value, chevron.</li></ul> | LST-1, LST-2, NAV-4 |
| `SectionHeader` / `AddItemButton` | A section title with an optional count, and one labelled "add to this list" button. | Item 28, LST-3 |
| `MetricCard` | Label, value, supporting slot, trailing action, `onClick`, optional progress slot. | Item 24, A11Y-2, `GoalCard` |
| `AppTextField` v2, `NumberInput` v2, `AppSearchField` | Trailing slot, `imeAction`, capitalization, autofocus, select-on-focus (numeric), one focus style and one label style. | FRM-1…5 |
| `SwipeToDeleteRow` v2 | Arms at 50 % and disarms at 40 %, haptics, exit animation, "Eliminar" or "Quitar" label (§14). | LST-5, item 26 |
| `ReorderableColumn` v2 | Immediate drag from the handle, haptics, scrolls by itself near the edges. | LST-6 |
| `TimeRangeSelector` | One range control with one string set. | SEL-1 |
| `AppMessages` | Shell-level message host plus `LocalAppMessages`. | FBK-1 |
| `AppHaptics` | Named haptic calls: `armed()`, `disarmed()`, `confirm()`, `reject()`, `tick()`. The Android implementation uses `View.performHapticFeedback`, with fallbacks per API level (§14.4). | FBK-3 |
| `OfflineState` | Offline load state that retries by itself on reconnect. | FBK-2 |
| `AppDateFormat` | The three date forms of R-38. | WRD-4 |
| `action_*` strings | Guardar, Cancelar, Cerrar, Eliminar, Quitar, Añadir, Editar, Listo, Descartar, Seguir editando, Reintentar. | WRD-3 |

### 9.1 Swipe-to-delete

| Moment | What you see | What you feel |
|---|---|---|
| The drag starts | The row follows the finger. A muted layer shows the icon and "Eliminar" ("Quitar" inside an editor). | — |
| It passes 50 % of the row | The layer becomes fully active, the icon grows 1.0 → 1.2 with a spring, and the label turns bold. | Armed: one crisp tick |
| It falls back below 40 % | The layer returns to muted. | Disarmed: a lighter tick |
| You let go while armed | The row slides out (≈ 200 ms, at your finger's speed), then the rows below move up (≈ 200 ms). | Confirm |
| You let go before the line | The row springs back, without wobbling. | — |
| The server fails | The row reappears in place, with the message "No se pudo eliminar. Inténtalo de nuevo." | Reject |
| TalkBack action "Eliminar <nombre>" | The same removal and gap animation. | Confirm |

The muted and active colours are a style decision. The behaviour only requires two clearly different states.

Starting values (tune them on your phone):

| Parameter | Value | Why |
|---|---|---|
| Direction | Right to left only | One meaning everywhere |
| Gesture start | System touch slop + horizontal lock | Scrolling never swipes |
| Arm at | 50 % of the row width | Clearly deliberate, still reachable one-handed |
| Disarm at | 40 % | A 10 % gap (hysteresis) avoids flicker and double ticks |
| Fling | Never deletes unless already armed | This caused the first version's accidents |
| Exit | Slide out ≈ 200 ms, then collapse ≈ 200 ms | Fast, but you can see what happened |
| Return | Spring: damping ≈ 0.8, medium-low stiffness | Settles without bouncing |
| Label | "Eliminar" for saved records; "Quitar" inside an editor, where the removal waits for "Guardar" | Glossary (Appendix A) |


### 9.2 Haptics

**Use the platform's semantic haptics, not raw vibrator pulses.** Compose's `LocalHapticFeedback` maps to Android's
`HapticFeedbackConstants`. Those effects:
- are tuned for each phone's motor (crisp on a Pixel);
- respect your system touch-feedback setting;
- feel like the rest of Android.

A raw `Vibrator` one-shot, which is what `platform/AndroidHaptics.kt` does today (15 ms for a tick), is a blunt buzz.
It feels different on every phone and ignores that setting.

**Which constant, from which Android version.** Checked in this project's Compose UI 1.12.1 (`HapticFeedbackType`).
Compose passes each type straight to Android, with no fallback, so the newer types are silent on older versions:

| Compose type | Use it for | Needs |
|---|---|---|
| `GestureThresholdActivate` | Swipe armed; a dragged row picked up | Android 14 (API 34) |
| `SegmentFrequentTick`, `SegmentTick` | Swipe disarmed; a reorder step; a stepper step | Android 14 (API 34) |
| `Confirm` | Delete committed; saved; set completed | Android 11 (API 30) |
| `Reject` | Action refused or failed | Android 11 (API 30) |
| `LongPress`, `VirtualKey`, `KeyboardTap` | Fallbacks | Every version |

Android 14 also has `GESTURE_THRESHOLD_DEACTIVATE` (the natural disarm effect) and `DRAG_START`, but Compose does not
expose them. A View-based wrapper can use them. Your Pixel 9 supports everything above. Because `minSdk` is 26, the
wrapper needs fallbacks.

**One semantic wrapper: `AppHaptics`.** Feature code only calls meanings, never constants:

```kotlin
// designsystem/AppHaptics.kt (commonMain)
interface AppHaptics {
    fun armed()     // a gesture crossed its threshold
    fun disarmed()  // it went back below it
    fun confirm()   // the action happened: saved, added, deleted, set completed
    fun reject()    // the action was refused or failed
    fun tick()      // a small step: stepper, reorder
}
val LocalAppHaptics = staticCompositionLocalOf<AppHaptics> { NoAppHaptics }   // tests provide a recording fake
```

One action, one vibration, from the layer that knows the outcome (the component fires `confirm()` on commit; the ViewModel only triggers `reject()` on failure). Alerts such as the end of a rest are not UI feedback: they keep the `Haptics` port (`alert()`), with a longer pattern.

### 9.3 Swipe checklist for ux-reviewer
- [ ] Uses `SwipeToDeleteRow`; there is no other swipe code (guard G7).
- [ ] Right to left only, labelled "Eliminar" or "Quitar" per the glossary.
- [ ] Arms at 50 % and disarms at 40 %; deletes only when released while armed.
- [ ] Haptics through `AppHaptics`: armed, disarmed, confirm, reject. Nothing else vibrates for the same action.
- [ ] The row leaves at once (optimistic) and the gap closes; a failure brings it back with a message.
- [ ] Stable keys, plus `animateItem` or `animateContentSize`.
- [ ] The same action exists in the details and as a TalkBack action.
- [ ] With reduced motion it snaps; the haptics stay.

## 10. Glossary (Spanish UI)

| Concept | Label to use | Never use for it | Notes |
|---|---|---|---|
| A meal of the day (Desayuno, Comida…) | The meal's name; in settings, "Comidas del día" | "Comidas" on its own in settings | Adding one: "Añadir comida del día"; the Name sheet title: "Nueva comida del día" |
| A saved template | "Comida guardada" / "Comidas guardadas" | "Plantilla" | Search tab "Guardadas"; "Guardar como comida" stays |
| A food from the catalogue | "Alimento" | "Comida" | "Crear alimento", "Editar alimento" |
| The search screen title | "Añadir al desayuno" / "Añadir a <comida>", the same label as the button that opened it | "Añadir comida" | When choosing a food for a comida: "Añadir alimento" |
| Delete a saved record | "Eliminar" | "Borrar", "Archivar" | Immediate; no "Deshacer" |
| Take out of a draft or container | "Quitar" | "Eliminar" | A comida item, a routine exercise, a portion |
| Abandon unsaved work or a live workout | "Descartar" | "Cancelar" | Dialog: "¿Descartar los cambios?" [Descartar] [Seguir editando] |
| Delete the account | "Borrar cuenta" (approved, ADR-29) | — | The only use of "Borrar" besides clearing text ("Borrar búsqueda") |
| Commit an edit | "Guardar" | "Aceptar", "Confirmar" | |
| Apply to the screen underneath | "Listo" | "Guardar" | Only when that screen has its own "Guardar" |
| Close a sheet | X described as "Cerrar" | "Cerrar sin guardar" | The sheet title gives the context |
| List of past records | "Historial" | — | The title says whose: "Historial de entrenos", "Historial de peso" |
| Month grid | "Calendario" | — | Opened by the calendar icon |
| Diary statistics | "Calorías" | "Historial" | The entry and the screen title are the same |
| Body fat | "Grasa corporal" (chip: "Grasa") | "Grasa (%)" | Field: "Grasa corporal (%)" |
| Basal metabolism | "Metabolismo basal" (chip: "TMB") | "Tasa metabólica basal" | |
| Bone | "Masa ósea" (chip: "Hueso" acceptable) | — | |
| Workout | "Entreno" | "Sesión" in UI text | "Entreno pasado", "Editar entreno" |
| Chart ranges | "1S 1M 3M 6M 1A, Todo" (spoken "1 semana"…, "Todo el historial") | "Último mes" and similar variants | One string set |
| Muscle on the radar axis | "Isquios" (radar only) | - | Everywhere else (list, chips, sheet, body map): "Isquiotibiales" |
| An exercise created by the user | "ejercicio propio"; button "Añadir ejercicio propio"; sheet "Nuevo ejercicio" | "Crear ejercicio", "Añadir ejercicio" for it | "Añadir ejercicio" is only the routine/workout button that opens the exercise picker |

Message templates:
- Required: "Escribe un nombre".
- Too long: "Máximo N caracteres".
- Range: "Entre X y Y <unidad>".
- Duplicate: "Ya existe <una comida / una carpeta / una rutina> con ese nombre".
- Error: "No se pudo <acción>. Inténtalo de nuevo."
- Offline action: "Sin conexión: no se puede <acción>".
- Saved: "Cambios guardados".
