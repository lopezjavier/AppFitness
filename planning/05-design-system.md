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

Each component: a `@Composable` with explicit parameters, `Modifier` as the first optional parameter, a propagatable `testTag`, a preview and an entry in the catalog screen (debug only).

| Component | Description / reference |
|---|---|
| `AppTheme` | Colors, typography, shapes, `AppColors` |
| `AppTopBar` | Section title only, no avatar (ADR-26, ADR-37); a variant with a back arrow for full-screen destinations |
| `AppNavigation` | Adaptive bottom bar / rail with 3 tabs (Diario, Entreno, Perfil); active tab = lime icon and extra-bold lime label + filled icon where the glyph has one, no pill (ADR-22). The Perfil tab icon is the generic Stitch `person` glyph (`design/stitch/progress/code.html`), tinted like the other tabs (ADR-37, supersedes the photo icon of ADR-26) |
| `AppCard` | `surfaceRaised`, radius 16, subtle border, padding 16 |
| `PrimaryButton` / `SecondaryButton` / `GhostButton` | Per DESIGN.md §Buttons; min 52 dp (primary and secondary) / 48 dp (ghost). The secondary is a filled, non-outlined action that sits next to a full-width primary (e.g. onboarding "Atrás"; Checkpoint E2-A rejected both a transparent ghost and an outlined version). **Secondary spec:** `surfaceOverlay` fill, `textPrimary` `titleMedium` label, no border, shape `md`, min height 52 dp (same as primary), 0.97 press scale; disabled = same fill with `ghostData` label. Paired in a footer with a full-width primary: 12 dp gap, secondary wraps content (min 104 dp), primary takes the rest |
| `AppChip` / `FilterChipRow` / `AppToggleChip` | 32 dp visual height (48 dp target); active: cyan border and text. `AppChip`/`FilterChipRow` are single-select (`Role.RadioButton`); `AppToggleChip` is the multi-select variant (`Role.Checkbox`, `toggleable`, `stateDescription` "Activado"/"Desactivado", optional `contentDescription`, same look and targets), used for the available plates in Preferencias |
| `SegmentedSelector` | Déficit / Mantenimiento / Superávit (active in lime). All labels share one size (largest up to `bodyMedium` where the widest label, measured bold, fits on one line); never below 11 sp (`labelTechnical`); past that they wrap to 2 lines (hyphenated on API 33+) and all segments grow together |
| `AppTabRow` | Equal-weight section tabs under the top bar (Resumen / Historial / Cómo hacerlo). Selected = lime bold `titleMedium` label + 2 dp lime indicator; others `textSecondary` over a `divider` line. Each tab >= 48 dp (`Role.Tab`, `selected` semantics), label wraps to 2 lines at large font scales, never clipped. Stateless (`options`, `selectedIndex`, `onSelect`); tags come from each `AppOption` |
| `AppBottomSheet` | Top radius 24, 36×4 handle, 65% scrim |
| `AppDatePickerDialog` | Platform calendar (Material3 `DatePicker`, opt-in confined to this wrapper) in the app colours, shown in a dialog (T-101, US-37). It only fills a draft: the screen that opened it saves with its own button. Stateless regarding the draft (`selected`, `onConfirm(date)`, `onDismiss`); `maxDate` makes later days unselectable (past-only fields pass today); no text-input mode. "Aceptar" is disabled until a day is selected, "Cancelar" and back dismiss. Tags `<tag>`, `<tag>_confirm`, `<tag>_dismiss`. Open state survives recreation. Catalog entry "Selector de fecha" |
| `ProgressRing` | kcal ring with normal / over-target (amber) states |
| `MacroProgressBar` | Linear bar in the macro color, "142 g / 180 g · 79 %" |
| `MacroSlider` | Slider in the macro color, % and grams/kcal |
| `PortionSelector` | Amount field + portion dropdown ("100 g", "1 huevo"); the unit is the food's base unit (`g`/`ml`) |
| `CookingMethodSelector` | Four single-select chips (Crudo, A la plancha, Air fryer, Frito) under the amount in the add-to-meal sheet; only for `g` foods; Crudo selected by default; an impact line ("+22,5 kcal y +2,5 g de grasa") updates with the choice. In save-as-comida and "Editar comida", each solid food row shows a compact method chip that opens the same selector. 2×2 grid at large font scales; `Role.RadioButton`, full descriptions |
| `FoodPhotoAction` | `AppIconButton` (`photo_camera`, description "Añadir desde foto") directly below the barcode icon; opens a mode `AppBottomSheet` with "Estimar con IA", "Etiqueta nutricional" and "Código de barras", then a source sheet with "Hacer foto" and "Elegir de la galería". The system camera or the Android photo picker opens; the shared preview shows "Repetir" and "Usar foto", then "Estimando…" while the function responds |
| `WeekdaySelector` | Seven equal day chips "L M X J V S D" (48 dp targets, `Role.Checkbox`, full day names as `stateDescription`); every available day starts selected, assigned days are disabled and name the current comida. The row scrolls horizontally when the seven targets exceed the width (narrow phones or large font scales); it never wraps |
| `FavouriteToggle` | Star icon toggle (lime when starred) in food rows and food detail |
| `MealSlotEditor` | Rename, reorder, delete and add meals, opened from Perfil -> Preferencias (ADR-41). Compact rows, no card per meal: drag handle, label and icon buttons (edit, up, down, trash), one "Añadir comida" at the end. Rule for list rows that configure items: edit actions are icon buttons in one line, never a full button or card per item |
| `ReorderableColumn` / `DragHandle` | Plain `Column` whose items are reordered by long-pressing and dragging their `DragHandle` (48 dp target, `drag_indicator` icon in `textSecondary`, labelled "Reordenar <item>"). Items keyed by a stable id; the dragged item follows the finger (`zIndex` above the rest), the others make room, and one `onMove(from, to)` is reported on drop. No auto-scroll and no haptics; every screen that uses it also offers a non-drag path ("Subir"/"Bajar"). First user: the routine builder (T-043); `MealSlotEditor` can reuse it |
| `MonthCalendarGrid` | Calendar month grid with a per-day kcal indicator; tap opens that day |
| `DiaryHistoryRow` | Date + kcal + P/C/F mini bars for a day, with a weekly/monthly summary header |
| `MacroStackedBar` | One horizontal bar split P/C/F by percentage in `macroProtein` / `macroCarbs` / `macroFat`; decorative segments, the whole bar exposes the split as `stateDescription` (T-037) |
| `AppAvatar` | Circular Google photo (Coil) with the placeholder person icon until it loads or on error; used large on the Perfil root (96 dp, no ring; §6.2), no longer in the navigation (ADR-37); decorative (T-037) |
| `AppTextField` | Single-line text input (T-041, name): label `bodyMedium` `textSecondary` above, `surfaceOverlay` fill, shape `base` (8), min height 52 dp, `bodyLarge` `textPrimary` left-aligned, 1.5 dp `athleticCyan` border on focus (like `NumberInput`), `error` border + supporting text when invalid, `maxLength` enforced, ghost placeholder, label as `contentDescription`, `ImeAction.Done`; `BasicTextField` based, state updated synchronously (same note as `NumberInput`). Lives in `designsystem/`, not in the screen |
| `NumberInput` | `surfaceOverlay` background, centered tabular numerals, 1.5 dp border on focus; placeholder in `textSecondary` (planned values are editable data, AA; entered text is `textPrimary`) |
| `SetCompletionCheckbox` | 38 dp visual circle / 48 dp target; checked = lime fill + dark check + haptics |
| `HelpButton` | In-app help affordance (T-097d, ADR-55): a "?" `AppIconButton` (48 dp, `ic_help`, `textSecondary`) placed as an icon in a title/header row, never a full-width button. Opens a read-only `AppBottomSheet` with a `titleLarge` heading, a short `bodyLarge` body (<= 3 lines) and one close icon; closes with X, back, scrim or swipe. Description "Ayuda: <tema>". Tags `<tag>`, `<tag>_sheet`, `<tag>_sheet_close`. Open state survives recreation |
| `SetRow` | Index · previous (ghost) · KG · REPS · checkbox |
| `ExerciseThumbnail` | Exercise image in list rows and detail (the `start` pose, cropped), `surfaceOverlay` background, radius `md`; placeholder icon when there is no media (custom exercises) or the image fails to load |
| `ExerciseAnimation` | Start↔peak crossfade of the two poses, 3 s cycle, `ease-in-out`, a brief pause on each pose (see §6.1) |
| `RestTimerBar` | 48 dp, `surfaceOverlay`, cyan tabular countdown, bottom progress line, "+30s" and "Omitir" |
| `MetricTile` | Technical label + `headlineMetric` value + unit |
| `TrendPill` | Weekly rate pill (green/red/amber) |
| `GoalCard` | Target weight and distance ("faltan 4,2 kg" / "objetivo alcanzado", wording by strategy, `04-business-rules.md` §3.5) with a progress bar from the first weight in the app to the target |
| `MetricInterpretationRow` | Metric value + status chip (low/normal/high) + short explanation; the list ends with the non-medical disclaimer |
| `CircumferenceRow` | Body-site label + value in cm + date, with edit/delete; sites are entered all at once in the sheet, each with a `?` text help |
| `SkeletonBox` | Animated placeholder (shimmer) |
| `EmptyState` / `ErrorState` | 48 dp decorative icon (`inbox` / `error`, Material Symbols, tinted `textSecondary` / `error`) + optional title + message + primary action (`Reintentar` is required on `ErrorState`); `ErrorState` takes an optional `secondaryAction` (`SecondaryButton` under the primary, 12 dp gap; e.g. "Crear manualmente" in the photo flow); max width 360 dp, centred; `ErrorState` text is a polite live region |
| `ActionRow` | Tappable row of a choice sheet (E7 photo mode and source sheets): decorative 24 dp icon + `titleMedium` title + one-line `bodyMedium` `textSecondary` hint, >= 48 dp, `Role.Button`, 16 dp side margins, read as one node ("title, hint") |
| `InlineNote` | Subtle form hint (E7 "Estimado por IA · revisa los valores"): decorative info icon + `bodyMedium` `textSecondary` text on `surfaceOverlay`, shape `md`, 12 dp padding; not interactive |
| `OfflineBanner` | Full-width `surfaceRaised` strip below the Top Bar, 1 dp `borderSubtle` bottom line, 20 dp decorative `cloud_off` icon in `energyAmber` + "Sin conexión" `bodyMedium`; wraps at large font scale; polite live region; shown on every destination and layout (on the rail layout it spans the full width above the rail) |
| `SwipeToDeleteRow` | Shared slow/heavy swipe-to-delete (ADR-45): `SwipeToDismissBox` end-to-start, confirms only at >= 90% of the travel (shorter drag or fling snaps back), error background with `ic_delete` + "Eliminar", custom accessibility action "Eliminar <name>", row >= 48 dp; no undo, no dialog |
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

**Preferencias** (full-screen destination: `AppTopBar` back-arrow variant titled "Preferencias", no bottom bar, no FAB; pinned "Guardar" `PrimaryButton` footer as in T-037). Order of cards (12 dp apart, 16 dp margins): "Datos personales" (first row = name `AppTextField`, label "Nombre"; then Sexo, Edad, Altura, Nivel de actividad, **Peso actual** as `EditableRow` with chevron that opens the existing `PickerSheet`), energy/strategy card, macro split card, the E7 **"Cocina"** card, then the existing "Comidas" card. "Peso actual" sheet = two `WheelPicker`s (kg · decimal, range 20,0..400,0 as in onboarding; extract `WeightWheels` to `feature/biometrics` next to `HeightWheel`, parameterised by `tagPrefix`), "Listo" applies. The old read-only hint (`profile_weight_hint`) is removed. From 600 dp: personal data + energy left, macros and Cocina right (unchanged layout). Back with unsaved changes discards silently (no dialog).

**Cocina (E7, US-50):** three numeric fields "Aceite a la plancha (g)", "Aceite en air fryer (g)" and "Aceite al freír (g)", each 0–500 g with the food's `NumberInput`; helper "El aceite que no se retiene no cuenta." Saves with the single Preferencias "Guardar". Fixed retention percentages are shown as supporting text, not editable.

**Onboarding name field** (step 1 "Sobre ti", only when Google gave no name): above the sex selector, label "¿Cómo te llamas?", `AppTextField`, `maxLength` 100, autofocus off, `ImeAction.Done` hides the keyboard; "Continuar" disabled while blank.

### 6.3 Food capture and default comidas (E7)

- **Add food from a photo (US-48/US-49):** the photo `AppIconButton` sits directly below the scanner icon, same size and alignment, description "Añadir desde foto". The mode sheet lists three rows with icon + title + one-line hint; closing it returns to the search. Choosing a mode opens a small source sheet with "Hacer foto" and "Elegir de la galería" (the Android photo picker, no media permission). Both sources use the same preview with "Repetir" and "Usar foto" (no in-app CameraX preview or gallery browser). Dish mode adds an optional comment field ("Añade un detalle: ingredientes, aliño…", max 200) before "Usar foto"; label and barcode modes do not. Every AI result, including barcode, opens the normal `CreateFoodScreen` with a subtle "Estimado por IA · revisa los valores" note (removed as soon as the user edits any field). The scanner icon and its live flow are untouched.
- **States:** idle, mode/source sheets, capturing or picking, "Estimando…" (progress + cancel disabled), prefilled form, and error `ErrorState` with "Reintentar" + "Crear manualmente" for every photo mode, including barcode ("No hemos podido identificar este producto"). Cancelling the camera or the picker returns to the source sheet without error. Offline disables the action with the standard offline message. A photo is discarded on every outcome; no thumbnail is kept in the app.
- **Cooking selector (US-50):** under the amount/portion in the add-to-meal sheet, a four-option selector; only for `g` foods. The impact line uses the macro colour for fat and `textSecondary` for the kcal part, e.g. "+22,5 kcal y +2,5 g de grasa"; Crudo shows no line. The selector is hidden for `ml`. Changing it recalculates the totals live without touching the food. When saving a meal as a comida or editing a comida, each solid food row shows a compact method chip ("Crudo" by default) that opens the same selector; the comida total kcal includes the cooking impact with the current Preferencias.
- **Default comidas (US-51):** entry is a `SecondaryButton` "Comidas por defecto" at the end of the Comidas card, disabled while the meal structure has unsaved changes. The destination is full-screen ("Comidas por defecto", back arrow, pinned "Guardar"), with one card per meal in its saved order; each card shows the assigned saved comidas as rows (name, food count, kcal) and an "Añadir comida" action. The picker is a bottom sheet listing saved comidas with name, food count and kcal; no saved comidas → `EmptyState` "No tienes comidas guardadas" and a hint to create one from "Añadir comida" → "Comidas". Each assignment row has a remove icon (no confirmation); if every day is assigned, "Añadir comida" is disabled with "Todos los días ya tienen una comida". The weekday selector sits inside the assignment sheet: all available days selected, conflicts disabled with the current comida named below. Empty selection disables "Guardar" with "Selecciona al menos un día". Leaving with unsaved changes asks to discard (same pattern as Preferencias). The note "Se añadirán al abrir el Diario de cada día seleccionado. Los cambios se aplican a partir del próximo día." sits under the meal list. Applying a default uses the methods saved in the comida and the current oil amounts.

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

## 8. Interaction rules (ADR-45)
Generic rules for every screen; ux-reviewer rejects a plan that breaks them.
- **Delete** runs at once: no confirmation dialog, no "Deshacer". Deleting from a details screen closes it. Exceptions with a confirmation dialog: "Borrar cuenta" (ADR-29) and deleting a non-empty routine folder, which deletes its routines and names how many (ADR-51).
- **Swipe-to-delete** uses the shared designsystem component: slow, heavy, fires only at the end of the travel; a shorter drag snaps back.
- **Editing** screens have one "Guardar" that saves all changes together; nothing is saved per action (delete excepted). This includes bottom sheets and modals that edit a value: they have their own "Guardar", and closing them (X, back, swipe down, scrim) discards the change; nothing is applied on dismiss.
- **Lists:** a row opens its details; editing starts from the details, and a row has no pencil or add button next to its tap. This holds for the Comidas tab of "Añadir comida" too (US-53): a row opens a read-only comida screen with "Añadir" and "Editar" (secondary + primary footer); there is no exception left. Rows of the same kind show the same information everywhere.
- **Wording:** the same concept has the same label on every screen and period.
- **Sweep:** before approving, search the app for other instances of the pattern the task touches.
