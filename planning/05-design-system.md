# Design System - Kinetic Dark (Compose implementation)

Live summary of what is built in `composeApp/.../designsystem/`; full tables, component specs and swipe/haptics details are in [`phase1/05-design-system.md`](phase1/05-design-system.md) (frozen). §8 (rules), §9 (templates) and §10 (glossary) are the UX rulebook (ADR-69) and prevail. The Stitch mockups (style reference only) were removed after Phase 1; they exist in git history only. Section numbers are cited by code; do not renumber.

## 1. Principles
1. Dark mode only, high contrast. 2. Depth through tonal layers + micro-borders, not shadows. 3. Meaningful colour: each accent is a domain, never decorative. 4. One hand: primary actions in the lower 60 %, targets >= 48 dp. 5. Bottom sheets for contextual flows; dialogs only for destructive confirmations (R-08). 6. Immediate feedback: skeletons, haptics on set completion and goals. 7. Tabular numerals for all telemetry.

## 2. Color
### 2.1 Surfaces and text
`surfaceBase #0B0F14` (background, bars) · `surfaceRaised #121820` (cards) · `surfaceOverlay #1A222D` (sheets, inputs, chips) · `borderSubtle` white 8 % · `borderStrong` white 12 % · `divider` white 4 % · `textPrimary #E0E2EA` · `textSecondary #C2CAB0` · `ghostData` white 36 % (previous values, placeholders; never the only cue) · `scrim` black 65 %.
### 2.2 Accents
`neonLime #A3E635` (primary, success, active tab, kcal ring; `onNeonLime #0B0F14`) · `athleticCyan #38BDF8` (training: timer, 1RM, volume, filters, weight chart) · `energyAmber #F97316` (warnings, excess) · `electricGreen #22C55E` (favourable trend, synced) · `error #FFB4AB`/`onError #690005`, `errorContainer #93000A`/`onErrorContainer #FFDAD6`.
### 2.3 Macros (fixed everywhere)
Protein `macroProtein` lime `#A3E635` · Carbs `macroCarbs` cyan `#38BDF8` · Fat `macroFat` amber `#F97316`.
### 2.4 Mapping to Material 3 `darkColorScheme`
`primary = neonLime`, `secondary = athleticCyan`, `tertiary = energyAmber`, `background = surface = surfaceBase`, `surfaceContainerLow = surfaceRaised`, `surfaceContainer/High = surfaceOverlay`, `onSurface = textPrimary`, `onSurfaceVariant = textSecondary`, `outline = borderStrong`, `outlineVariant = borderSubtle`, error roles as above; every other role is set explicitly so no M3 baseline colour leaks (`ColorScheme.kt`). Non-M3 tokens (macros, ghostData, electricGreen...) are in `AppColors` via `AppTheme.colors`.
### 2.5 RPE scale (only inside `RpePickerSheet`, ADR-55)
`AppTheme.colors.rpeScale(value)`: nine stops green -> red (`#22C55E #65D04A #A3E635 #D4D93A #FACC15 #FB923C #EF4444 #E5383B #DC2626`) spread over RPE 6-10 (whole numbers, ADR-56); rows are `surfaceOverlay` tinted 16 % with an 8 dp marker bar. `confidenceScale(value)` (ADR-72) reuses the stops reversed for 1-10; it colours only a graphic >= 12 dp, text stays `textPrimary`.
### 2.6 Discomfort scale (ADR-81, T-152)
`AppTheme.colors.discomfortScale(level)`: five fixed stops taken from the RPE ramp, never the lime of "Guardar" (`#A3E635`): level 1 `#D4D93A`, 2 `#FACC15`, 3 `#FB923C`, 4 `#EF4444`, 5 `#DC2626` (all >= 3:1 on `surfaceOverlay` as a graphic). Marker bar and dot only (>= 12 dp); the label is `textPrimary` and the level is always also text (R-41).

## 3. Typography
Plus Jakarta Sans (OFL, weights 400/600/700/800); every style sets `fontFeatureSettings = "tnum, lnum"`. Styles (size/line, weight): `displayHero` 36/40 (tablet 48/52) 800 · `headlineMetric` 26/30 (32/36) 700 · `titleLarge` 20/26 600 · `titleMedium` 16/22 600 · `bodyLarge` 16/24 · `bodyMedium` 14/20 · `bodySmall` 12/16 · `labelNumeric` 18/22 700 · `labelTechnical` 11/14 600 UPPERCASE (callers pass `text.uppercase()`).

## 4. Spacing, shape and elevation
8 dp grid, 4 dp sub-grid: `none 0, xs 4, sm 8, md 12, lg 16, xl 24`. `screenMargin` 16, `cardGap` 12, `blockGap` 24, `cardPadding` 16. Radii: `sm 4`, `base 8` (inputs, rows), `md 12`, `lg 16` (cards), `xl 24` (sheet top), `full`. Elevation through layers; shadows only on sheets and the active-element lime glow (`AppElevation`).

## 5. Adaptive layout
Compact (< 600 dp): bottom navigation with 3 tabs (Diario, Entreno, Perfil; ADR-37), single column. Medium/Expanded (>= 600 dp): `NavigationRail`, two asymmetric columns; forms and root screens are centred at `FormContentMaxWidth` (480 dp). Active tab: lime icon + extra-bold lime label, filled glyph where one exists, no pill (ADR-22).

## 6. Components (all in `designsystem/`, each with a testTag, preview and debug catalog entry)
- **Foundations:** `AppTheme`, `AppCard`, `AppIconButton` (tooltip), `PrimaryButton`/`SecondaryButton`/`GhostButton`, `AppChip`/`FilterChipRow`/`AppToggleChip`, `SegmentedSelector`, `AppTabRow`, `AppTextField`, `NumberInput`, `AppSearchField`, `AppDatePickerDialog`, `HelpButton`, `AppAvatar`.
- **Templates (§9):** `AppScreen`, `AppEditorScreen`, `StepScaffold`, `AppTopBar` v2, sheet archetypes (`AppEditSheet`, `AppPickerSheet`, `AppActionSheet`, `AppInfoSheet`) on `AppBottomSheet` (top-only hairline, no drag handle), `NameSheet`, `FoodSheet`, `AppMessages`/`AppMessageHost`, `AppHaptics`, `OfflineState`/`OfflineHint`/`OfflineBanner`, `EmptyState`/`ErrorState`/`SkeletonBox`.
- **Rows and lists:** `AppListRow`, `NavigationRow`, `FoodEntryRow`, `ExerciseRow`, `WorkoutRow`, `MeasurementRow`, `DiaryGroupRow`, `ListSectionHeader`, `AddItemButton`, `SwipeToDeleteRow`, `ReorderableColumn`/`DragHandle`, `ActionRow`, `InlineNote`.
- **Diary/nutrition:** `ProgressRing`, `MacroProgressBar`, `MacroStackedBar`, `MacroSlider`, `PortionSelector`, `CookingMethodSelector`, `FavouriteToggle`, `MealSlotEditor`, `WeekdaySelector`, `FoodPhotoAction`, `MonthCalendarGrid`, `DiaryHistoryRow`.
- **Training/weight:** `SetRow`, `SetCompletionCheckbox` (38 dp visual / 48 dp target), `RestTimerBar`, `ExerciseThumbnail`, `ExerciseAnimation`, `RpePickerSheet`/`RpeOptionRow`, `MetricTile`, `MetricCard`, `TrendPill`, `AppLinearProgress`, `MetricInterpretationRow`, `TimeRangeSelector`, `AppBarChart`, `RadarChart`, `BodyHeatmap`, `AppDateFormat`/`AppNumberFormat`.
- Icons: Material Symbols (Outlined) with a `contentDescription` on every actionable icon.
### 6.1 Exercise media
Bundled RepDB free assets (`start`/`peak` poses; thumbnail = `start`), gitignored under `composeResources/files/exercises/`. `ExerciseAnimation` crossfades start <-> peak (3 s, ease-in-out) and respects the system "remove animations" setting. Attribution "Datos de ejercicios por RepDB (repdb.co)" appears where assets show. Placeholder icon for custom exercises or load failures.
### 6.2 Perfil root and Preferencias
Perfil tab root: gear action "Preferencias" in the top bar, 96 dp avatar, name, then rows (statistics entries, "Acerca de", "Cuenta"); no FAB, no editable state. Preferencias hub: cards of `NavigationRow`s (Datos personales, Objetivo calórico, Macros, Comidas del día, Cocina, Entrenamiento, Avisos, ...), each section an `AppEditorScreen` with its own "Guardar". Cuenta screen: "Cerrar sesión" and "Borrar cuenta" (disabled with a note for protected accounts, ADR-57).
### 6.3 Food capture and default comidas
Photo action directly below the scanner icon opens a mode sheet (Estimar con IA, Etiqueta nutricional, Código de barras); states idle/picking/"Estimando…"/prefilled form/`ErrorState` with "Reintentar" + "Crear manualmente". Cooking selector under the amount (only `g` foods). "Comidas por defecto" is a full-screen editor with a card per meal and a `WeekdaySelector`.

## 7. Accessibility
AA contrast (4.5:1) on `surfaceBase`/`surfaceRaised` (except supporting `ghostData`); targets >= 48 dp; `contentDescription` on actionable icons; bars/rings expose `stateDescription`; font scaling to 130 % without clipping key numbers; auto-sized labels >= 11 sp. Verified by Robolectric + Compose tests (`androidHostTest`: touch targets, labels, exact descriptions); only haptics and TalkBack are manual.

## 8. Interaction rules and UX rulebook (ADR-45, amended by ADR-69)
Generic rules cited by number; ux-reviewer rejects plans and diffs that break one. A convention stated by the user at a checkpoint is written here.
### Navigation
- **R-01 Titles.** Tab roots show their own compact header, never a bar repeating the tab label. A screen about one object is titled with its name; an editor with verb + object ("Nueva rutina", "Editar rutina").
- **R-02 Object actions** go in the top bar: at most 2 icons plus a ⋮ action sheet; never an extra icon row under the bar, never inside one tab/section.
- **R-03** A navigation entry is a `NavigationRow` (label, optional value, chevron). The history or calendar of X is a calendar icon in X's top bar.
- **R-04** An entry's label equals the title of the screen it opens.
- **R-05** Bottom navigation appears only on tab roots; every other screen has a back arrow.
### Templates
- **R-06** Every screen is built on `AppScreen` (read) or `AppEditorScreen` (edit); none draws its own top bar, footer, message host or offline banner.
- **R-07** Every bottom sheet is one of four archetypes: Edit (form + pinned footer), Picker (one choice applied on tap, no footer), Actions (menu), Info (help/read-only). A plain single-choice Picker list uses `OptionPickerSheet` (`designsystem/`, R-39). Each has a header (title, optional "?", close X "Cerrar"), scrolling body, optional pinned footer.
- **R-08 Dialogs** only to: delete the account; sign out; delete many records at once (a folder with its routines); discard an in-progress workout; discard unsaved changes (R-14). Nothing else.
- **R-09** The primary action of a sheet or editor is pinned and always visible.
### Editing
- **R-10** Every edit surface has an explicit "Guardar"; closing (X, back) never applies anything.
- **R-11** In an editor every change, removals included, waits for "Guardar". Immediate deletion exists only outside editors (list or details).
- **R-12** "Guardar" writes to the server. "Listo" applies to the unsaved changes of the screen underneath, which has its own "Guardar". Single-choice pickers apply on tap, no "Listo".
- **R-13** After a successful "Guardar" the editor closes and returns, with a Confirm haptic; a message only if the change is not visible there.
- **R-42 Live settings.** A switch on a read screen (`AppScreen`, no other fields, no "Guardar") applies on toggle: optimistic update, Reject haptic + message and rollback on failure, disabled offline with the standard hint (R-30). Its effect must be visible on the same screen. A switch inside an editor (Preferencias) is a draft until "Guardar" (R-10/R-11). No screen uses it today (the reminder switches live in Preferencias → "Avisos", T-138); the rule stays for a future read-screen switch.
- **R-45 Scales explain themselves.** A numeric or ordinal scale (for example the 1-5 "Energía" of the weekly check-in, the discomfort level, RPE, the photo "Confianza") shows a `HelpButton` (R-32) next to its label that opens an Info sheet listing what each value means (one bullet per value; a 1-10 scale may group values in bands). Exempt: options that already carry their own description in place (activity-level cards), categorical choices (strategy, set type, goal, units) and continuous quantities (macro sliders, durations). A coloured scale of long labels is a list of `ScaleOptionRow`s (RPE, discomfort level), not chips (R-33 exception). Added at Checkpoint E12-A (user feedback, 2026-10-29); T-152 sweeps every existing scale.
- **R-14** A full-screen editor with unsaved changes asks "¿Descartar los cambios?" [Descartar] [Seguir editando] on back/X; without changes it leaves silently. Sheets discard silently.
- **R-15** Creating/renaming a named item without its own editor (meal, folder, "Guardar como comida", "Guardar como rutina") uses the Name sheet: one autofocused field, keyboard "Hecho" saves when valid, names unique per user ignoring case and surrounding spaces.
### Lists, rows, add and delete
- **R-16** One row component per object type: `FoodEntryRow`, `ExerciseRow`, `WorkoutRow`, `MeasurementRow`, `NavigationRow`.
- **R-17** Tapping a row opens its details; editing starts there. In a picker, tapping picks; the thumbnail or ⓘ opens details.
- **R-18** Tapping a food or exercise name opens its details on every screen.
- **R-19** Row actions are icon buttons on one line. Adding to a list is one labelled `AddItemButton` ("Añadir <objeto>") at the end.
- **R-20** Delete lives: record in a list -> swipe plus the same action in its sheet/details; configuration row (meals, folders, plates) -> trash icon in the row; object on screen -> top bar. Never "Deshacer".
- **R-21** Swipe-to-delete uses the shared component: right to left only; "Eliminar" for saved records, "Quitar" inside editors; arms at 50 % of row width (visual change + Armed haptic), disarms below 40 % (Disarmed haptic); releasing armed deletes (Confirm haptic, row slides out, gap closes, item leaves at once); releasing early or an unarmed fling springs back; stable row keys.
- **R-22** Reordering uses the shared component: handle drags at once, Tick haptics on pick-up and each pass, auto-scroll near edges, TalkBack actions "Subir"/"Bajar".
- **R-23** Lists are lazy and animate insertions, removals and moves (`appAnimateItem()`, stable keys; snaps with animations disabled).
### Forms
- **R-24 Text fields:** label above in sentence case; one label style and focus treatment; optional trailing slot (clear, ✓, unit); names start with a capital; keyboard "Siguiente" between fields, "Hecho" on the last (in a one-field sheet "Hecho" saves).
- **R-25 Numeric fields** select all on focus, use the decimal keyboard, check ranges inline ("Entre X y Y <unidad>").
- **R-26 Search fields** use `AppSearchField` (search icon, clear button, keyboard "Buscar").
- **R-27 Validation** appears under the field after the first edit while "Guardar" stays disabled; messages follow the templates in §10.
### Feedback and states
- **R-28 Messages:** one app-level message host above the bottom bar and any pinned footer; messages survive navigation and have no action buttons.
- **R-29 Haptics** only through `AppHaptics`, one vibration per action: Confirm (saved, added, deleted, set completed), Reject (refused/failed), Armed/Disarmed (gesture threshold), Tick (stepper/reorder step). Alerts such as the end of rest use the `Haptics` port alert pattern.
- **R-30 Offline:** only the shell shows "Sin conexión"; a write disabled offline shows the standard hint; a failed offline load shows `OfflineState`, which retries on reconnect.
- **R-31 States:** loading = skeleton shaped like the layout; empty = `EmptyState` with at most one action; error = `ErrorState` with "Reintentar"; an action waiting for server/AI shows progress ("Estimando…") and then its result or an error with "Reintentar". Every outcome is visible.
- **R-32** Help is always `HelpButton`, never text expanding in place.
- **R-43 Watch data.** Every screen that shows watch data (Health Connect: weight, sleep, steps, calories, resting heart rate) shows the Health Connect status line and its "Sincronizar" button (`HealthStatusLine`) directly under the top bar, in every state (loading, empty, error, content). Today: Peso and Actividad.
### Selection controls
- **R-33** Tabs (`AppTabRow`) switch sibling views of one screen; a segmented control (`SegmentedSelector`) picks one of 2-5 short options; chips filter a list or pick among many/long options. A control never changes type with the number of options.
- **R-34** The same concept uses the same control and labels. Chart ranges use `TimeRangeSelector`: "1S 1M 3M 6M 1A".
- **R-38** A picker, read-only sheet or action menu (calendar, day, metric and range pickers, info sheets, action menus) closes with its X, system back or an outside tap (ADR-80). A sheet with editable input or unsaved changes (R-11) closes only with its X, system back or "Cancelar" (ADR-71). Drag-down never closes a sheet and no sheet shows a drag handle.
- **R-39** Where an icon clearly represents an action (photo, add, remove, retry, scan) the control is an icon with a `contentDescription`; text buttons are for actions with no clear icon (ADR-71).
- **R-41** A quality/confidence level is never colour alone: a colour from the design-system scale plus text ("Confianza 7/10"), with the same text as `contentDescription`/`stateDescription` (ADR-72).
### Wording
- **R-35** The glossary (§10) is binding: one label per concept and one concept per label.
- **R-36** Generic actions use the shared `action_*` strings (Guardar, Cancelar, Cerrar, Eliminar, Quitar, Añadir, Editar, Listo, Descartar, Seguir editando, Reintentar).
- **R-37** Messages follow fixed templates (§10): errors "No se pudo <acción>. Inténtalo de nuevo."; offline "Sin conexión: no se puede <acción>". Sentences end with a period; titles and buttons do not; use "…" not "..."; titles capitalize the first word only.
- **R-44 Dates and numbers:** dates via `AppDateFormat` (list "Hoy"/"Ayer"/"lun, 24 oct"; title "24 de octubre"; month "octubre 2026"); numbers via `AppNumberFormat`; macro grams are whole numbers outside nutrition tables.
### Process
- **R-39 Sweep.** A task that adds or changes a pattern updates every other instance (found by search) or records each in the E9 table with an owning task; a new designsystem component retires its ad-hoc copies within the same epic.
- **R-40 Guards.** `scripts/check-ux-conventions.sh` passes (CI).
### Decided - do not re-propose
No "Deshacer" anywhere (ADR-45) · no dialogs outside R-08 · explicit "Guardar" on every edit surface, nothing applies on close · single-choice pickers apply on tap, "Normal" resets the set type (ADR-55) · RPE whole 6-10 (ADR-56) · three tabs and no FAB (ADR-36/37) · Preferencias and statistics hang off Perfil · meal structure edited from Preferencias, never the Diario (ADR-41) · every setting (reminder switches, check-in day, ...) lives in Preferencias and is saved with its "Guardar", never in the screen it affects (checkpoint E10 feedback) · a Health Connect weight wins, today's weight is edited with the pencil (ADR-47/48) · help is explained in the app (ADR-55) · state survives leaving the app (ADR-40) · meals are deleted, never disabled, at least one remains (ADR-41) · saved meals are "comidas" (ADR-42) · no "Comparar" (ADR-48).

## 9. Patterns and templates (ADR-69)
Components that encode §8, each with a catalog entry and host tests: `AppTopBar` v2 (title, subtitle, back, <= 2 `AppIconButton` + ⋮ `AppActionSheet`, 56 dp on full-screen; `AppTopBarAction.badgeCount` / `AppIconButton.badgeCount` draw a count badge, no number at 0, "9+" past nine, exact count in the content description) · `AppScreen` (content width capped, optional pinned footer, wired to `AppMessages`) · `AppEditorScreen` (pinned "Guardar" with optional destructive action, discard dialog R-14, closes with haptic R-13, offline hint R-30, keyboard room) · sheet archetypes (shared header and pinned footer; keyboard "Hecho" saves when valid; top-only hairline) · Name sheet · `FoodSheet` (name, brand, ★, ✎ for manual foods; quantity/portion; live totals; collapsible details; context footer) · rows (`AppListRow` >= 48 dp with leading/headline/supporting/trailing, plus the per-object rows) · `SectionHeader`/`AddItemButton` · `MetricCard` · field upgrades (trailing slot, `imeAction`, capitalization, autofocus, select-on-focus) · `SwipeToDeleteRow` v2 · `ReorderableColumn` v2 · `TimeRangeSelector` · `AppMessages` · `AppHaptics` · `OfflineState` · `AppDateFormat` · `StepScaffold` (onboarding: fixed indicator, per-step title/helper/content, footer pinned above the keyboard, slide animation) · `AppIconButton` long-press tooltip.
### 9.1 Swipe-to-delete
Drag shows a muted layer with icon + label; passing 50 % arms it (layer active, icon grows 1.0 -> 1.2, label bold, Armed tick); falling below 40 % disarms (lighter tick); release armed -> slide out ~200 ms then collapse ~200 ms (Confirm); release early -> spring back (damping ~0.8). Fling never deletes unless armed; right to left only; touch slop + horizontal lock so scrolling never swipes. Server failure: row reappears with "No se pudo eliminar. Inténtalo de nuevo." and Reject. TalkBack action "Eliminar <nombre>" gives the same removal.
### 9.2 Haptics
Feature code calls only `AppHaptics` meanings (`armed`, `disarmed`, `confirm`, `reject`, `tick`), implemented with `HapticFeedbackType` (Android 14: `GestureThresholdActivate`, `SegmentFrequentTick`/`SegmentTick`; Android 11: `Confirm`, `Reject`; fallbacks `LongPress`/`VirtualKey`/`KeyboardTap` for `minSdk` 26). The component that knows the outcome fires it; ViewModels only trigger `reject()`. `LocalAppHaptics` defaults to a no-op; tests provide a recording fake.
### 9.3 Swipe checklist for ux-reviewer
Uses `SwipeToDeleteRow` only (guard G7) · right-to-left, "Eliminar"/"Quitar" · arms 50 % / disarms 40 %, deletes only when released armed · haptics via `AppHaptics` · row leaves at once, failure brings it back with a message · stable keys + animation · same action in details and as TalkBack action · snaps with reduced motion, haptics stay.

## 10. Glossary (Spanish UI)

| Concept | Label to use | Never use for it | Notes |
|---|---|---|---|
| A free-text note on a set, a routine exercise or a discomfort | "Nota" ("Añadir nota", "Editar nota", sheet field "Nota", error "Máximo 200 caracteres") | "Comentario", "Observaciones" | 1-200 chars, shown with `InlineNote`; clearing and "Guardar" removes it; never interpreted (ADR-81) |
| A meal of the day (Desayuno...) | The meal's name; in settings "Comidas del día" | "Comidas" alone in settings | "Añadir comida del día"; Name sheet "Nueva comida del día" |
| A saved template | "Comida guardada" / "Comidas guardadas" | "Plantilla" | Search tab "Guardadas"; "Guardar como comida" stays |
| A catalogue food | "Alimento" | "Comida" | "Crear alimento", "Editar alimento" |
| Search screen title | "Añadir al desayuno" / "Añadir a <comida>" (same as the opening button) | "Añadir comida" | Choosing a food for a comida: "Añadir alimento" |
| Delete a saved record | "Eliminar" | "Borrar", "Archivar" | Immediate; no "Deshacer" |
| Take out of a draft/container | "Quitar" | "Eliminar" | Comida item, routine exercise, portion |
| Abandon unsaved work or a live workout | "Descartar" | "Cancelar" | "¿Descartar los cambios?" [Descartar] [Seguir editando] |
| Delete the account | "Borrar cuenta" (ADR-29) | - | Only other "Borrar": clearing text ("Borrar búsqueda") |
| Commit an edit | "Guardar" | "Aceptar", "Confirmar" | |
| Apply to the screen underneath | "Listo" | "Guardar" | Only when that screen has its own "Guardar" |
| Close a sheet | X described as "Cerrar" | "Cerrar sin guardar" | |
| List of past records | "Historial" | - | "Historial de entrenos", "Historial de peso" |
| Month grid | "Calendario" | - | Opened by the calendar icon |
| Diary statistics | "Calorías" | "Historial" | Entry and screen title equal |
| Training statistics | "Entrenamiento" (Perfil row and title) | "Estadísticas" | Perfil group heading is "Estadísticas"; the Preferencias section "Entrenamiento" is a different destination (allowed by R-04) |
| Wearable activity data (sleep, steps, kcal, HR) | "Actividad" (Perfil row and title) | "Salud", "Reloj" | Cards "Sueño", "Pasos", "Calorías activas", "Calorías totales", "Frecuencia cardiaca en reposo"; the Perfil row "Calorías" stays the diary intake, so "Calorías activas/totales" always carry the qualifier |
| Body fat | "Grasa corporal" (chip "Grasa") | "Grasa (%)" | Field "Grasa corporal (%)" |
| Basal metabolism | "Metabolismo basal" (chip "TMB") | "Tasa metabólica basal" | |
| Bone | "Masa ósea" (chip "Hueso" acceptable) | - | |
| Workout | "Entreno" | "Sesión" in UI text | "Entreno pasado", "Editar entreno" |
| Chart ranges | "1S 1M 3M 6M 1A, Todo" (spoken "1 semana"..., "Todo el historial") | "Último mes" etc. | One string set |
| Muscle on the radar axis | "Isquios" (radar only) | - | Elsewhere "Isquiotibiales" |
| User-created exercise | "ejercicio propio"; "Añadir ejercicio propio"; sheet "Nuevo ejercicio" | "Crear ejercicio", "Añadir ejercicio" for it | "Añadir ejercicio" is only the routine/workout picker button |
| App information screen | "Acerca de" (last Perfil row and title) | "Información", "Sobre la app" | Not "Sobre ti" (onboarding step); no value on the row |
| Discomfort logged on an exercise | "Molestia" ("Registrar molestia", "Ya no me molesta"; the open one shows the level text, never a "Molestia registrada" label) | "Lesión", "Dolor" | Required level 1-5 (ADR-81): "Tensión o fatiga", "Molestia leve", "Pinchazo o chasquido", "Hinchazón, hormigueo o entumecimiento", "Dolor agudo o lesión"; section "Nivel". Exception: "Dolor"/"lesión" appear only inside the top level label, which must be explicit. No term matching, no health message |
| Training intention (US-65) | Section and hub row "Mi entrenamiento"; field "Objetivo de entrenamiento"; values "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición", "Mantenimiento"; unset "Sin definir" | "Intención", "Objetivo calórico" for it | Never the nutrition goal ("Objetivo calórico", E13); the hub row shows the chosen value, nothing when unset |
| Training place (US-65) | "Lugar": "Casa", "Gimnasio", "Otro" | "Ubicación" | Seeds the equipment ("Equipamiento"); equipment chips use `Equipment.label` ("Mancuernas") |
| Movement pattern of an own exercise (E12/E13) | Field and picker title "Patrón de movimiento"; values "Sentadilla", "Bisagra de cadera", "Zancada", "Empuje horizontal", "Empuje vertical", "Tirón horizontal", "Tirón vertical", "Acarreo", "Core", "Aislamiento", "Cardio", "Otro"; unset "Sin definir" | "Tipo de movimiento", "Categoría" | Optional, custom exercises only, never shown on catalogue exercises or details; "Otro" is a real value, "Sin definir" means none |
| System licences list | "Licencias de código abierto" | "Licencias", "Open source" | Credits heading "Créditos" |

Message templates: required "Escribe un nombre" · too long "Máximo N caracteres" · range "Entre X y Y <unidad>" · duplicate "Ya existe <una comida / una carpeta / una rutina> con ese nombre" · error "No se pudo <acción>. Inténtalo de nuevo." · offline "Sin conexión: no se puede <acción>" · saved "Cambios guardados".
