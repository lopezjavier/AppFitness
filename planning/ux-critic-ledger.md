# UX critic ledger

One row per ux-critic finding. The PM has the dispositions recorded here as the user decides (rules mode of ux-reviewer). The critic reads it first and does not repeat a row in any status except `REWORK`, where it may only propose a different solution.

| ID | Pattern (keywords) | Round | Status | Link |
|---|---|---|---|---|
| C-01 | selected state: selectedContainer #354925 + 1dp lime border + lime W700 label (chips, toggle chips, weekday, portion, cooking, segmented, time range) | 0 | ADOPTED | 05 §2.1a, §6.0, R-64 |
| C-02 | disabled PrimaryButton: lime 20 % container, lime 50 % label | 0 | ADOPTED | 05 §2.1a, R-65 |
| C-03 | textTertiary #8C97A9 for previous-set cell, placeholders, wheel neighbours; ghostData only disabled | 0 | ADOPTED | 05 §2.1a, R-65 |
| C-04 | SetRow header «ANTES», tracking 0.02 em, REPS weights, RPE variant inset 8 dp | 0 | ADOPTED | 05 §3, §6.0, §10 |
| C-05 | borderControl white 36 % for input resting borders | 0 | ADOPTED | 05 §2.1a, §6.0 |
| C-06 | surfaceControl #242E3B for inputs, steppers, pending set circle, SecondaryButton | 0 | ADOPTED | 05 §2.1a, §6.0 |
| C-07 | textSecondary #A9B3C4 | 0 | ADOPTED | 05 §2.1a |
| C-08 | GhostButton neutral, focus border lime 2 dp | 0 | ADOPTED | 05 §6.0 |
| C-09 | tabs shared 12 sp label, scroll (min 72 dp) only if a word does not fit | 0 | ADOPTED (reworked; tried 12 sp; user found it too small in the search tabs: now 14 sp, 6/8 dp padding, full-bleed, indicator visible while scrolling) | 05 §9 (amended), §6.0 |
| C-10 | ring caption/status bodyMedium 14/20 W600 | 0 | ADOPTED | 05 §3, §6.0 |
| C-11 | trackInactive white 16 % + 4 dp stop dot on linear bars | 0 | ADOPTED | 05 §2.1a, §6.0 |
| C-12 | WeekdaySelector 7 weighted cells, no scroll, cells >= 40x48 dp | 0 | ADOPTED | 05 §6.0, §7 exception |
| C-13 | titleSection 18/24 W600 for ListSectionHeader, top-bar title W700 | 0 | ADOPTED | 05 §3, §6.0 |
| C-14 | AddItemButton leading ic_add 20 dp | 0 | ADOPTED | 05 §6.0 |
| C-15 | WorkoutRow headline titleMedium, one bodyMedium supporting line, empty trailing | 0 | ADOPTED (layout amended by C-176) | 05 §6.0 |
| C-16 | nav labels sentence case 12/16 W600, icons 24 dp | 0 | ADOPTED | 05 §3, §6.0, §10 |
| C-17 | EmptyState 32 dp glyph in 72 dp circle, per-domain icon; error buttons min width 200 dp | 0 | ADOPTED | 05 §6.0, R-66 |
| C-18 | sleep stages cyan ramp 100/75/50 % + amber Despierto | 0 | ADOPTED | 05 §2.1a |
| C-19 | PrimaryMinHeight 56 dp, radius 16 dp | 0 | ADOPTED | 05 §6.0 |
| V-1 | discomfort row: borderless text action, no mid-word break | 0 | ADOPTED | 05 §6.0 (R-51) |
| V-2 | REPS field >= 48 dp | 0 | ADOPTED | 05 §6.0 (R-58) |
| V-3 | legacy filled icons to Outlined (ic_info, ic_photo_camera, ic_photo_library, ic_label, ic_auto_awesome, ic_nav_trainer, ic_health_connect). ic_nav_trainer history: original filled `insights` (liked) > T-294 hand-drawn trend+sparkle (rejected, broken) > `insert_chart` bf5317ff (rejected, degradation) > decision 2026-11-13: Material Symbols Outlined `insights`, verbatim path | 0 | ADOPTED | 05 §6.0 |
| V-4 | remove unused sheetHandle and glowLime/glowBlur tokens | 0 | ADOPTED | 05 §2.1a |
| C-20 | R-64 extended to leftovers: SelectableCard (fill, 1 dp border, lime title), OptionPickerSheet check lime, calendar today = 1 dp cyan ring (selected+today = R-64 plus a 1.5 dp (`AppBorder.focus`) cyan outer ring with a 2 dp gap to the lime border), AppCard `selected` param for ActivitySummary, `highlighted` stays informational cyan, sleep swatch 12 dp (alphas kept) | 0 | ADOPTED | 05 R-64 (b)-(f), §6.0 |
| C-21 | add-food screen: search field full width, scan in trailing slot, "Añadir con IA" and "Crear alimento" as top-bar actions | 1 | ADOPTED | 05 R-69 |
| C-22 | edit-entry sheet: "Eliminar" text-style danger action >= 16 dp below "Guardar", no dialog | 1 | ADOPTED | 05 R-70 |
| C-23 | remaining kcal only in the ring status pill ("Te quedan N kcal" / "Objetivo cumplido" / "Quedaron N kcal"), removed from empty meal cards | 1 | ADOPTED | 05 R-67 |
| C-24 | check-in: 3 MetricTiles in one row | 1 | ADOPTED | 05 visual items |
| C-25 | token `macroFat` #A78BFA (orange is the over-target colour) | 1 | ADOPTED (reworked: #A78BFA violet disliked; final #F472B6 pink, approved 2026-11-13) | 05 §2.3, visual items |
| C-26 | nested containers on sheets use `surfaceControl` | 1 | ADOPTED (build waits for round 0 verdict) | 05 visual items |
| C-27 | pinned footer: hairline plus fade | 1 | ADOPTED (build waits for round 0 verdict) | 05 visual items, §9 |
| C-28 | meal rows inside `AppCard` | 1 | ADOPTED (reworked: card kept; header redone by R-75/R-78, approved 2026-11-13) | 05 visual items |
| C-29 | compact hero: ring 132 dp with stacked macros, full ring at font scale >= 1.15 | 1 | ADOPTED (build waits for round 0 verdict) | 05 R-68 |
| C-30 | entry typography | 1 | ADOPTED (reworked: entry kcal 14 sp W600 in `FoodEntryRow` and `DiaryGroupRow`, approved 2026-11-13) | 05 visual items |
| C-31 | plural-aware reminders ("Toca pesarte", "Llevas N días sin pesarte"; measures likewise) | 1 | ADOPTED | 05 R-72, guard G21 |
| C-32 | per-type notice icons, hide "Pendientes" at 0 | 1 | ADOPTED | 05 R-74 |
| C-33 | calendar resting cells without fill | 1 | ADOPTED (build waits for round 0 verdict) | 05 visual items |
| C-34 | energy notice icon and copy | 1 | ADOPTED | 05 R-74 |
| C-35 | inline partial-failure notice | 1 | ADOPTED | 05 R-74 |
| C-36 | nutrient table rows | 1 | ADOPTED | 05 visual items |
| C-37 | chip row runs to the sheet edge | 1 | ADOPTED (build waits for round 0 verdict) | 05 visual items |
| C-38 | meal chips labelled "Comida del día" | 1 | ADOPTED | 05 visual items, §10 |
| C-39 | add button names its destination "Añadir a <comida>" (meal name as typed, no article) | 1 | ADOPTED (reworked: wording kept; the text button became the description of the compact "+" icon button; later superseded by the lime "+Añadir" of C-46, approved 2026-11-13) | 05 R-69 |
| C-40 | tablet navigator capped at 320 dp | 1 | ADOPTED (build waits for round 0 verdict) | 05 visual items |
| C-41 | lime caret in text fields | 1 | ADOPTED (build waits for round 0 verdict) | 05 visual items |
| C-42 | goals prompt is a NavigationRow with icon and chevron, no "?" | 1 | ADOPTED | 05 R-73 |
| C-43 | photo hint | 1 | ADOPTED | 05 visual items |
| V-5 | R-30 offline: only the shell says "Sin conexión" (rule violation fixed) | 1 | ADOPTED | 05 R-30 |
| V-6 | R-37 message templates and punctuation | 1 | ADOPTED | 05 R-37 |
| V-7 | R-27 validation under the field after the first edit | 1 | ADOPTED | 05 R-27 |
| V-8 | R-03 navigation entries are `NavigationRow` | 1 | ADOPTED | 05 R-03, R-73 |
| V-9 | R-31 loading, empty, error and progress states | 1 | ADOPTED | 05 R-31 |
| V-10 | C-09 tab label evidence (swept Diario tab rows) | 1 | ADOPTED | 05 §9 |
| V-11 | C-17 empty and error evidence (swept Diario states) | 1 | ADOPTED | 05 R-66 |
| V-12 | R-35 glossary wording | 1 | ADOPTED | 05 R-35, §10 |
| V-13 | R-44 dates and numbers; fix only if the sweep confirms a violation | 1 | PROPOSED | 05 R-44 |
| C-44 | meal card header redesign: name + chevron toggle row, buttons at the trailing edge | 1 | ADOPTED (batch 3; adjusted: exactly two buttons, save icon (not the bookmark-plus) and lime text button "+Añadir", no ⋮) | 05 R-75 |
| C-57 | meal card header compact layout (batch 4 feedback + final decisions): kcal pill right of name + chevron, macros under the name, both buttons on the right, no top padding, two-line name wrap, same collapsed and expanded | 1 | ADOPTED (batch 4, approved in the emulator 2026-11-13; supersedes the pill-under-the-name layout of C-44/C-47) | 05 R-75, §6.0, §9 |
| C-58 | both buttons always visible collapsed and expanded; only the save button (and kcal pill) hidden on an empty meal | 1 | ADOPTED (batch 4; supersedes C-49) | 05 R-75, §10 |
| C-59 | save icon = outlined rounded bookmark with a plus inside in a circular tonal button (`ic_save_meal`); replaces the floppy `ic_save` | 1 | ADOPTED (batch 4; supersedes the "no plus" wording of C-46/C-48) | 05 §6.0, §10 |
| C-45 | critic's original meal card layout variant | 1 | SUPERSEDED by C-55 as adjusted by the user | 05 R-75 |
| C-46 | compact tonal icon button (48 dp target, 36 dp circle) for the save icon only; the "+" becomes a lime compact text button "+Añadir" (48 dp target) | 1 | ADOPTED (batch 3; final user decision 2026-11-13) | 05 §6.0, R-75 |
| C-47 | compact kcal pill under the meal name, no border; none for an empty meal | 1 | ADOPTED (batch 3) | 05 §6.0, R-75 |
| C-48 | "Guardar como comida" icon button (new icon chosen at build, not the bookmark-plus) only with entries; empty meal has none | 1 | ADOPTED (batch 3; final user decision: no ⋮ menu, no separate saved-comida icon) | 05 R-75 |
| C-49 | "+" shown only while the card is expanded | 1 | SUPERSEDED by C-58 (user, 2026-11-13, batch 4: both buttons always visible) | 05 R-75 |
| C-50 | no ⋮ on a food row (swipe and edit sheet carry delete) | 1 | ADOPTED (batch 3) | 05 R-75 |
| C-51 | saved-comida icon in the meal card header | 1 | REJECTED (user, 2026-11-13): saved comidas stay in the "Guardadas" tab | 05 §8 Decided |
| C-52 | search tab row 14 sp, 6/8 dp padding, full-bleed; "Más comidos" -> "Habituales" | 1 | ADOPTED (batch 3) | 05 R-77, §6.0, §10 |
| C-53 | restaurant icon on the meal card | 1 | REJECTED (user, 2026-11-13) | 05 §8 Decided |
| C-54 | macros line hidden when the card is collapsed | 1 | REJECTED (user, 2026-11-13): shown expanded and collapsed, as today | 05 §8 Decided |
| C-55 | meal card per the critic, with the user's corrections: exactly two buttons (save icon, not the bookmark-plus; lime text button "+Añadir"), no ⋮, macros line always visible, collapsed card about 100 dp | 1 | ADOPTED (batch 3; as adjusted) | 05 R-75 |
| C-56 | critic's third header variant (extra header buttons) | 1 | SUPERSEDED / REJECTED: at most two buttons | 05 §8 Decided |
| V-14 | tab row shows no indicator while scrolling: indicator stays visible | 1 | ADOPTED (batch 3) | 05 §6.0, R-77 |
| V-15 | compact hero macro labels break mid-word: never | 1 | ADOPTED (reworked: failed on device after batch 3; fixed by batch 4/5 single-line labels, ring-over-bars fallback and V-15b, approved 2026-11-13) | 05 R-76 |
| V-16 | search tab row full-bleed edge to edge | 1 | ADOPTED (batch 3) | 05 R-77 |
| V-17 | ⋮ "Más acciones de %1$s" wording on the meal card | 1 | WITHDRAWN (user, 2026-11-13): the meal card has no ⋮; its bookmark-plus keeps "Guardar como comida" | 05 §10 |
| C-60 | `DiaryGroupRow` uses `FoodEntryRow`'s slots: name + 24 dp chevron as headline, kcal 14 sp W600 over `MacroText` as trailing; overlay box starts 8 dp from the card edge | 1 | ADOPTED (batch 5) | 05 R-78 |
| C-61 | collapsed or empty meal card bottom padding 4 dp (expanded keeps 16) | 1 | ADOPTED (batch 5) | 05 R-78 |
| C-62 | header macros: letter textPrimary W700, figure macro colour W600, bodySmall, " · " in textTertiary attached to the preceding item | 1 | ADOPTED (batch 5) | 05 R-78 |
| C-63 | 4 dp between the name row and the macros line | 1 | ADOPTED (batch 5) | 05 R-78 |
| C-64 | 1 dp borderSubtle hairline in the 8 dp gap, only expanded with entries | 1 | ADOPTED (batch 5) | 05 R-78 |
| C-65 | meal with no entries: whole macros line in textTertiary | 1 | ADOPTED (batch 5) | 05 R-78 |
| V-18 | retire `MealSummaryText` in favour of `MacroText` (R-39) | 1 | ADOPTED (batch 5) | 05 R-78 |
| V-19 | retire `MacroSummary` in favour of `MacroText` (R-39) | 1 | ADOPTED (batch 5) | 05 R-78 |
| V-20 | short forms Prot./Carb./Grasa and P/C/G added to the glossary | 1 | ADOPTED (batch 5) | 05 §10 |
| V-15b | V-15 follow-up: `compactMacrosFit` measures the real ring-column width; host test required; 360 dp header fallback (pill moves to the macros line) | 1 | ADOPTED (user verdict 2026-11-14: Diario approved) | 05 R-76, R-78 |
| U-1 | diary entry with ingredients: collapsed swipe removes the whole food (user request, explicit exception to "no behaviour change"; reconfirmed in batch 4, unchanged) | 1 | ADOPTED | 05 R-71 |
| C-66 | Rutinas: the `isNext` routine's "Empezar" is a PrimaryButton, others secondary | 2 | ADOPTED | 05 R-79, §6.0b |
| C-67 | live set row: current set cyan ring + "Serie actual", done rows on surfaceOverlay without border | 2 | ADOPTED | 05 R-80, §6.0b |
| C-68 | finish the workout only when all sets are done | 2 | REJECTED (user: "also in the three dots") | 05 §8 Decided |
| C-69 | live session phone: full-width AppLinearProgress under the top bar | 2 | ADOPTED | 05 §6.0b |
| C-70 | set note button: one `sticky_note_2` glyph, tertiary / cyan by state | 2 | ADOPTED | 05 R-80, §6.0b |
| C-71 | RpeCell reuses the SetTypeBadge container | 2 | ADOPTED | 05 R-80, §6.0b |
| C-72 | one glyph per action (timer, calculate, fitness_center, drive_file_move, content_copy, flag, sticky_note_2); menu title = object | 2 | ADOPTED | 05 R-81 |
| C-73 | Historial year: 3 x 4 MonthTiles, bare count, unit in the heading | 2 | ADOPTED | 05 §6.0b |
| C-74 | AppListRow min height 56 / 72 / 88 dp by line count | 2 | ADOPTED | 05 §6.0b |
| C-75 | StepperLine: sentence-case labels, label + ? flow, 48 dp buttons | 2 | ADOPTED | 05 R-83, §6.0b |
| C-76 | set note reasons as wrapping toggle chips | 2 | ADOPTED | 05 R-84 |
| C-77 | date field looks editable (surfaceControl + border + calendar icon); NumberInput start-aligned, unit trailing | 2 | ADOPTED | 05 R-83, §6.0b |
| C-78 | set lines textPrimary titleMedium on history and session detail | 2 | ADOPTED | 05 R-82 |
| C-79 | one set-summary grammar in routine details | 2 | ADOPTED | 05 R-82 |
| C-80 | summary and session detail figures as a MetricTile row | 2 | ADOPTED | 05 R-85 |
| C-81 | picker chip rows captioned, 8 dp apart | 2 | ADOPTED | 05 R-86 |
| C-82 | saving indicator 20 dp before the label | 2 | ADOPTED | 05 §6.0b |
| C-83 | sheet header grows with a two-line title | 2 | ADOPTED | 05 §6.0b |
| C-84 | "Pesas por mano: No" hidden; true = pill "Una pesa en cada mano" | 2 | ADOPTED | 05 R-87 |
| C-85 | Rutinas offline hint is the first list item | 2 | ADOPTED | 05 R-88 |
| C-86 | exercise form chips wrap (low confidence; interacts with C-37) | 2 | ADOPTED | 05 R-86 |
| C-87 | pinned footer capped at 480 dp on wide screens | 2 | ADOPTED | 05 §6.0b |
| C-88 | calendar month, no day selected: "Toca un día para ver sus entrenos." | 2 | ADOPTED | 05 §6.0b |
| C-89 | builder footer hidden until loaded | 2 | ADOPTED | 05 R-88 |
| C-90 | cardio card shares the strength grid (weights 1 : 1, note on line 1) | 2 | ADOPTED | 05 §6.0b |
| C-91 | TIEMPO drawn as a field (`StopwatchField.TimeText`, every caller) | 2 | ADOPTED | 05 R-39 |
| C-92 | cardio start: tonal circle on line 1, "Iniciar" pill on line 2 when it does not fit | 2 | ADOPTED | 05 §6.0b |
| C-93 | host-test assertions for the cardio grid alignment (Spec 7) | 2 | ADOPTED | 05 §6.0b |
| C-94 | routine card: one 72 dp row, circular start, no "Empezar" button | 2 | ADOPTED | 05 §6.0b |
| C-95 | folder as a card, whole row toggles | 2 | ADOPTED | 05 §6.0b |
| C-96 | one add row: "Añadir rutina" + folder icon button | 2 | ADOPTED | 05 §6.0b |
| C-97 | "Sin carpeta" is `ListSectionHeader(title, count)` | 2 | ADOPTED | 05 §6.0b |
| C-98 | pencil removed from the routine card ("Editar" stays in the sheet and the details top bar) | 2 | ADOPTED | 05 §6.0b |
| C-99 | the round start button is lime on every routine card; "Siguiente" is the cyan border, a flag and text | 2 | ADOPTED | 05 R-79, R-64 |
| C-100 | routine cards lose the drag handle: touch-and-hold to reorder, "Subir"/"Bajar" in the ⋮ sheet | 2 | ADOPTED | 05 R-22 |
| V-21 | live set row reaches 48 dp (KG/REPS 48 wide, TIPO/RPE 40 x 48); amends V-2 and C-04 (build gave 40 / 35 dp). Note: the type/RPE cells stay 40 x 48 dp with an accessibility-sweep exemption, kept (ux-reviewer decision, 2026-11-14) | 2 | ADOPTED | 05 R-80, §6.0b, §7 |
| V-22 | distance/time set row on two lines, no ANTES column; line 2 only the "Iniciar" pill fallback (no "Antes", user decision) | 2 | ADOPTED | 05 §6.0b, R-80 |
| V-23 | stepper "+" and "Sin objetivo" fit (see C-75) | 2 | ADOPTED | 05 R-83 |
| V-24 | year view breaks words (see C-73) | 2 | ADOPTED | 05 §6.0b |
| V-25 | skeletons: box heights, 16 dp margins, fallback title "Rutina" | 2 | ADOPTED | 05 §6.0b, R-88 |
| V-26 | durations "55 min", empty volume segment dropped | 2 | ADOPTED | 05 R-82 |
| V-27 | "×" never "x" | 2 | ADOPTED | 05 R-82 |
| V-28 | session menu titled with the routine name; sentences end with a period | 2 | ADOPTED | 05 R-89 |
| V-29 | "Se eliminará este entreno con todas sus series." | 2 | ADOPTED | 05 §10, R-89 |
| V-30 | AddItemButton for "Añadir serie" in builder and live card | 2 | ADOPTED | 05 R-90 |
| V-31 | superset ? on the label's line | 2 | ADOPTED | 05 R-90 |
| V-32 | "Ver progreso" is a NavigationRow | 2 | ADOPTED | 05 R-90 |
| V-33 | exercise search `showLabel = false` | 2 | ADOPTED | 05 R-86 |
| V-34 | picker chip rows full-bleed with content padding | 2 | ADOPTED | 05 R-86 |
| V-35 | R-70 covers AppEditorScreen ("Editar entreno" Eliminar) | 2 | ADOPTED | 05 R-90 |
| V-36 | date dialog selected day: Material 3 draws no border; lime fill accepted (user decision 2026-11-14) | 2 | RESOLVED | 05 R-64 e |
| V-37 | "Mover a carpeta" via OptionPickerSheet with lime check | 2 | ADOPTED | 05 R-90 |
| V-38 | star marks "Siguiente entreno" (replaced by `flag`, see C-72) | 2 | ADOPTED | 05 R-81 |
| V-39 | titles: Descanso, add picker, Entreno tab header = tab label (R-04 wins), detail error copy | 2 | ADOPTED | 05 R-01, R-89 |
| V-40 | offline menus dim actions in both (low) | 2 | ADOPTED | 05 R-88 |
| V-41 | offline favourite star carries the hint (low) | 2 | ADOPTED | 05 R-88 |
| V-42 | cardio: note alone on line 2 (note back on line 1 after LISTO) | 2 | ADOPTED | 05 §6.0b |
| V-43 | cardio: TIEMPO centred 24 dp off its header | 2 | ADOPTED | 05 §6.0b |
| V-44 | cardio: TIEMPO had no field container | 2 | ADOPTED | 05 R-39 |
| V-45 | "Sin carpeta" was a raw Text (now `ListSectionHeader` with count) | 2 | ADOPTED | 05 §6.0b |
| V-46 | copy: routines error, offline menu and folder hints end with a period | 2 | ADOPTED | 05 R-88 |
| V-47 | routine skeleton row height equals the card (72 dp) | 2 | ADOPTED | 05 §6.0b |
| V-48 | cardio start description "Iniciar cardio" | 2 | ADOPTED | 05 R-81 |
| V-49 | empty folder: no "0 rutinas", no chevron, toggle inert | 2 | ADOPTED | 05 §6.0b |
| C-101 | `SummaryCard` headline + unit, facts, progress slot; new `SegmentedProgress` (Tu bloque, Tu plan) | 3 | REWORK (root part, batch 5) | 05 §6.0c, R-95 |
| C-102 | Entrenos card headline + `SegmentedProgress`; new `BarListRow` "Series por grupo"; lifts with `TrendPill` | 3 | ACCEPTED (not built) | 05 §6.0c, R-95 |
| C-103 | `highlighted` on every decision-waiting card | 3 | ACCEPTED (not built) | 05 R-94 |
| C-104 | one `DecisionButtonRow` (ghost left, primary right, 56 dp) | 3 | ACCEPTED (not built; designsystem) | 05 §6.0c, R-94 |
| C-105 | Plan nutricional: `displayHero` kcal + `MacroStackedBar`, rows stay as legend | 3 | ACCEPTED (not built) | 05 §6.0c |
| C-106 | `HubContentMaxWidth` 840 dp adaptive grid for the six hub screens | 3 | ACCEPTED (not built) | 05 R-91 |
| C-107 | `SkeletonBox` white 8 % / shimmer 16 % | 3 | ACCEPTED (not built; designsystem) | 05 §6.0c |
| C-108 | `AppScreen.subtitle` "Paso N de M" in the block flow | 3 | ACCEPTED (not built) | 05 §6.0c, §10 |
| C-109 | `StatusTone.Info` and `Neutral` for Actual / Descanso de dieta / Descarga / Semana en pausa | 3 | ACCEPTED (not built; designsystem) | 05 §6.0c, R-98 |
| C-110 | a fact never breaks (U+00A0), " · " only when it fits at 360 dp x 1.3 | 3 | ACCEPTED (not built) | 05 R-93 |
| C-111 | centred `EmptyState`/`ErrorState`; Trainer root without a plan | 3 | ACCEPTED (not built) | 05 R-92, §10 |
| C-112 | Tus datos / Antes de empezar rows grouped in cards, "· origen" dropped, "Tus respuestas"; amends §6.2 | 3 | ACCEPTED (not built) | 05 §6.0c, §6.2, R-96 |
| C-113 | Tu bloque header card, current week highlighted, adherence `SegmentedProgress`, `?` and pencil in the title row | 3 | ACCEPTED (not built) | 05 §6.0c |
| C-114 | Tu plan card: kcal headline, figure tiles, objectives list | 3 | REWORK (root part, batch 5) | 05 §6.0c |
| C-115 | builder muscle header and targets spacing | 3 | ACCEPTED (not built) | 05 §6.0c |
| C-116 | "puedes cambiarlo" as `ExerciseRow` second supporting line | 3 | ACCEPTED (not built; designsystem) | 05 §6.0c |
| C-117 | `InlineNote(tone = Info or Error)` | 3 | ACCEPTED (not built; designsystem) | 05 §6.0c |
| C-118 | folder review intro, amber unmet icon, grouped check rows, `?` top end | 3 | ACCEPTED (not built) | 05 §6.0c |
| C-119 | fill motion for segments and bars | 3 | ACCEPTED (not built) | 05 R-97 |
| C-120 | Informes leading icons, rows in one card | 3 | ACCEPTED (not built) | 05 §6.0c, R-96 |
| C-121 | one `SectionCard(title, help?)` folds the feature-local `SummaryCard`s | 3 | ACCEPTED (not built; designsystem) | 05 §6.0c, R-96 |
| C-122 | "Provisional" chip inside the row's supporting slot | 3 | ACCEPTED (not built) | 05 §6.0c |
| C-123 | Trainer root: Tu plan about 218 dp, Tu bloque about 206 dp, 12 dp apart, 24 dp before "Tus datos" | 3 | ADOPTED (main Trainer page approved by the user, 2026-11-15) | 05 §6.0c |
| C-124 | Tu bloque: week chip, "2 de 4 entrenos esta semana", session segments, "Por hacer", Cardio and Pasos al día progress facts | 3 | ADOPTED (user, 2026-11-15; week selector reworked by C-144) | 05 §6.0c, R-93 |
| C-125 | Tu plan: full-width goal band (OBJETIVO, goal name, rate, graphic), kcal headline, macro bar and text | 3 | REWORK (user mockup 2026-11-15: replaced by C-131; the band keeps eyebrow, name and rate, loses the graphic, kcal headline and macro bar) | 05 §6.0c, §10 |
| C-126 | `GoalGraphic(phase)`: decorative 96 x 48 dp sketch per goal | 3 | REWORK (user mockup 2026-11-15: removed, the weight chart replaces it; delete the component and its catalogue entry) | 05 §6.0c |
| C-127 | Resumen semanal card: headline, segments, Cardio and Pasos facts, mean weight and calories tiles | 3 | REWORK (user mockup 2026-11-15: the box is replaced by the row "Informes y resumen semanal", C-137) | 05 §6.0c |
| C-128 | Trainer root at two columns: Series por grupo muscular card, 840 dp header cap, 220 dp minimum row-1 cards | 3 | REWORK (user mockup 2026-11-15: the sets card goes with the summary box; the 840 dp cap stays, C-139) | 05 §6.0c, R-91 |
| C-129 | Tu bloque states: no block (glyph card with "Crear bloque"), not started, paused, ended, weeks read failed | 3 | ADOPTED (user, 2026-11-15) | 05 §6.0c |
| C-130 | Trainer root motion: stagger macro bar, goal stroke, segments, cardio, steps, summary; no replay on refresh | 3 | ADOPTED (user, 2026-11-15) | 05 §8 R-97 |
| V-50 | "Resumen semanal" and changes-only "Tu bloque" boxes get figure lines | 3 | REWORK (root part, batch 5) | 05 §6.0c, R-95 |
| V-51 | `BlockReviewEntry` becomes a `SummaryCard` | 3 | ACCEPTED (not built) | 05 R-95 |
| V-52 | rename/fold the feature-local `SummaryCard`s | 3 | ACCEPTED (not built) | 05 R-96 |
| V-53 | duplicate "Tu plan" header dropped | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-54 | "Eliminar bloque" moves to the top-bar ⋮ sheet | 3 | REWORK (built in batch 2; user mockup 2026-11-15: it lives inside the "Tu bloque" card as a destructive text link, same dialog; the ⋮ and its sheet go, V-73) | 05 §6.0c |
| V-55 | nutrition figures as tiles; 2 x 2 grid allowed (PM decision on R-85) | 3 | ACCEPTED (not built) | 05 R-85 amendment |
| V-56 | one card per box/row list | 3 | ACCEPTED (not built) | 05 R-96 |
| V-57 | lime check on the current day type | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-58 | search field label/inset | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-59 | "Equipamiento" chips wrap | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-60 | week tabs full-bleed | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-61 | kcal field `startAligned`, "Minutos por semana" style | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-62 | plural "rutina(s)" (+ G21 word list) | 3 | ACCEPTED (not built) | 05 R-100 |
| V-63 | folder refused/empty states: tap target for the fix, no dead "Guardar bloque" | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-64 | "Mi entrenamiento" is a text link (R-90 wins; §6.2/§10 corrected) | 3 | ACCEPTED (not built) | 05 §6.2, R-100 |
| V-65 | "coach" becomes "entrenador" | 3 | ACCEPTED (not built) | 05 R-100, §10 |
| V-66 | final periods, "Inténtalo de nuevo.", «» quotes | 3 | ACCEPTED (not built) | 05 R-100 |
| V-67 | footers stop double padding | 3 | ACCEPTED (not built) | 05 R-99 |
| V-68 | `ic_description` Outlined | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-69 | "Ya tienes un bloque en marcha" offers a way out | 3 | ACCEPTED (not built) | 05 §6.0c |
| V-70 | offline hint placement (low confidence) | 3 | ACCEPTED (not built) | 05 §6.0c, R-88 |
| V-71 | "Objetivo" eyebrow and goal name in the Tu plan band | 3 | ADOPTED (user, 2026-11-15) | 05 §10 |
| V-72 | "Tus datos" root row as a one-row `RowGroupCard`, 56 dp | 3 | ADOPTED (user, 2026-11-15; batch 6 makes it the second row of a two-row group, C-137) | 05 §6.0c |
| C-131 | Tu plan card (user mockup 2026-11-15): goal header, `WeightPaceChart` (dashed plan line to "Meta", solid trend, weekly dots, faint daily dots, "rango probable" band), figures line "Ritmo / Objetivo", verdict, "Llevas N semanas", three tiles, "Crear plan nuevo" inside | 3 | ACCEPTED (not built; user, 2026-11-15) | 05 §6.0d, 07 §5 |
| C-132 | `WeightPaceChart` (designsystem, new): neutral verdict only, no corridor, no error colour, no forward projection; the verdict line is the text alternative | 3 | ACCEPTED (not built; goal line amended by C-178) | 05 §6.0d, R-101 |
| C-133 | Verdict wording from 07 §5 (5); the mockup's "Ritmo óptimo" and "En pasillo" chips and the "Fase activa" chip are dropped | 3 | ACCEPTED (not built) | 05 §10, R-101 |
| C-134 | Tu plan tiles: Calorías, Cardio, Pasos (kcal eaten against the plan target; calories IN, macros OUT; no macro bar on the root) | 3 | ACCEPTED (not built) | 05 §6.0d, R-102 |
| C-135 | Tu bloque card: week chip with pencil, segmented progress, "2 de 4 entrenos esta semana" with percent chip, four session cards (Listo / Pendiente), Cardio and Pasos al día bars | 3 | ACCEPTED (not built) | 05 §6.0d |
| C-136 | In-card actions: "Crear plan nuevo" (Tu plan), "Mi entrenamiento" and "Eliminar bloque" (Tu bloque), outside the card's tap target | 3 | REWORK (superseded 2026-11-15 by C-143: actions are in each card's ⋮, details screens deleted) | 05 R-103, R-105 |
| C-137 | Root row "Informes y resumen semanal" (leading Outlined icon) in one `RowGroupCard` with "Tus datos" | 3 | ACCEPTED (not built) | 05 §6.0d, §10 |
| C-138 | Root states: no plan, no block, not started, paused, ended, too few weigh-ins, offline | 3 | ACCEPTED (not built) | 05 §6.0d |
| C-139 | Wide layout: two columns (Tu plan, Tu bloque) at 300 dp minimum cells under the 840 dp cap | 3 | ACCEPTED (not built) | 05 R-91 |
| C-140 | Motion: the plan line draws first, then the trend, then the dots; tiles and bars fill with the R-97 stagger | 3 | ACCEPTED (not built) | 05 R-97 |
| V-73 | Top-bar ⋮ and its sheet on the Trainer root removed (nothing left in it) | 3 | ACCEPTED (not built) | 05 §6.0d |
| C-141 | Block R (T-283, commit 3014b11e): `GoalRing` / `GoalRingTile` / `GoalProgressState` for Entrenos, Cardio, Pasos al día in "Tu bloque"; supersedes the headline, percent chip and bars of C-135 | 3 | REJECTED by the user 2026-11-15 (rings removed, C-135 card restored) | 05 §6.0e, R-104 |
| C-142 | R-104 state words Por detrás / En camino / Cumplido / Superado / Sin datos / Por empezar, never colour alone | 3 | REJECTED by the user 2026-11-15 (went with the rings) | 05 R-104, §10 |
| V-74 | Steps tile: "Pasos al día" plus "de 7.000 al día" repeats the period and wraps to four lines at 360 dp (uneven tile heights); target becomes "de 7.000" | 3 | REJECTED (moot: the rings it fixed were removed, C-141) | 05 §6.0e |
| V-75 | Over-target colour: ring/status green (R-104) vs live cardio timer amber past target (R-58); fitness-scientist is answering | 3 | REJECTED (moot: R-104 rings removed, C-141; R-58 stays as is) | 05 R-58, R-104 |
| C-143 | Trainer root cards have their own ⋮ menu (`CardMenu` + `AppActionSheet`): Tu plan (Crear plan nuevo, Descanso de dieta, Editar cardio), Tu bloque (Bloque nuevo, Extender semana, Hacer descarga, Eliminar bloque); cards open nothing. Supersedes C-136 and the "no actions" half of R-103 (G22/G27 keep their patterns) | 3 | ADOPTED (user approved the main Trainer page, 2026-11-15) | 05 R-103, R-105, §6.0h |
| C-144 | Week selector: `SegmentedProgressPicker` tabs "S1".."SN" (tick on done, outline on current, upcoming tappable as "Planificado", chosen = bold + 3 dp bar, "Semana N" title above the detail, no fill animation); replaces the round segments of C-135 | 3 | ADOPTED (user, 2026-11-15) | 05 R-106, §6.0h |
| C-145 | Root card "Cambios del entrenador" (`CoachChangesCard`): rows with "Revertido" state and "Deshacer" action, under "Tu bloque", only with applied changes | 3 | ADOPTED (user, 2026-11-15) | 05 R-107, §10 |
| C-146 | A failed plan read never shows the no-plan screen: skeleton while retrying, then `ErrorState` with "Reintentar" | 3 | ADOPTED (user, 2026-11-15) | 05 R-108 |
| C-147 | The "Tu plan" and "Tu bloque" screens are deleted; "Mi entrenamiento" is a text link below "Tus datos" on the root | 3 | ADOPTED (user, 2026-11-15; screens deleted) | 05 §6.0h, R-90 |
| V-76 | "Eliminar bloque" text link inside the "Tu bloque" card (V-73 / V-54 REWORK) becomes the last entry of the card's ⋮ sheet (destructive, R-08 dialog unchanged) | 3 | ADOPTED (user, 2026-11-15) | 05 R-105 |
| C-149 | Rename "Entrenamiento" stats row/title to "Entrenos" (`profile_training_stats_row`, `stats_title`); Preferencias row/title to "Registro de series" (`preferences_training_title`); glossary row 420 amended | 4 | ACCEPTED (not built) | 05 §10 |
| C-150 | Perfil root: 72 dp header (56 dp avatar, name titleLarge), grouped `AppCard`s (Mi entrenamiento; "Estadísticas" with five Outlined 24 dp icons; Acerca de), Fuerza value names the lift | 4 | ACCEPTED (not built) | 05 §6.0i, R-109 |
| C-151 | "Volumen" row leaves Fuerza; "Kilos por grupo muscular" `NavigationRow` last in "Series por grupo muscular" | 4 | ACCEPTED (not built) | 05 §6.0i, §10 |
| C-152 | Fuerza: 1RM `AppLineChart` of the chosen lift (3M, `TimeRangeSelector`); `TrendPill` glyph only | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-153 | Preferencias hub grouped (General, Nutrición, Entreno, Cuenta) with value supporting lines | 4 | ACCEPTED (not built) | 05 §6.0i, R-109 |
| C-154 | "Comidas por defecto" becomes a hub row after "Comidas del día" | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-155 | Option A: "Cuenta" row/screen with "Cerrar sesión" and danger text "Borrar cuenta" (>= 16 dp apart); outlined buttons leave Preferencias | 4 | REWORK (as C-172: a group inside the hub, not a screen) | 05 §6.0i, R-113 |
| C-156 | Avisos order | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-157 | Calorías order (segmented, summary card, calendar); Semana band with 1 dp lime outline; Mes no selected day, month in card title. Diario calendar-icon question | 4 | ACCEPTED (order and Semana band; not built); Diario calendar-icon question OPEN (deferred product question, no rule) | 05 §6.0i |
| C-158 | Deferred product question (user, round 4) | 4 | OPEN (deferred, no rule) | - |
| C-159 | Line chart padded domain, 4 gridlines, zero baseline only on bars | 4 | ACCEPTED (not built; padding formula amended by C-177) | 05 R-112 |
| C-160 | MetricTile value/unit fit, tonnes from 100.000 kg, Actividad icon 16 dp, one column at font scale >= 1.3 | 4 | ACCEPTED (not built) | 05 §6.0i, R-111, §10 |
| C-161 | NavigationRow two lines when label and value do not fit; label never ellipsized | 4 | ACCEPTED (not built) | 05 R-110 |
| C-162 | NavigationRow value one style (bodyLarge textSecondary, trailing); its use as the trailing value on the Perfil root stat rows is reversed by the user (chevron only) | 4 | ACCEPTED (not built) | 05 R-110 |
| C-163 | Peso order: HealthStatusLine, Peso actual + rate, WeightChart, tiles, Circunferencias | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-164 | HealthStatusLine and "Sincronizar" in one 56 dp row | 4 | ACCEPTED (not built) | 05 R-114 |
| C-165 | Peso `MeasurementRow` two lines (72 dp); composition sheet date once | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-166 | Kilos per group cards: 8-bar chart, headline, caption; bar width rule; check empty plot is not a capture artefact | 4 | ACCEPTED (not built) | 05 §6.0i, R-112 |
| C-167 | Drop headings repeating the screen title; Cocina "?" to top-bar `HelpButton` | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-168 | Registro de series: "Series" and "Calculadoras" `SectionCard`s; label style; stepper own line | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-169 | Objetivo calórico headline "Objetivo: 2.788 kcal al día", chip removed | 4 | ACCEPTED (not built) | 05 §6.0i |
| C-170 | Default-meal days "Lunes a viernes" / "L, M, X"; picker rows 16 dp leading space | 4 | ACCEPTED (not built) | 05 §6.0i, §10 |
| C-171 | "¿Cerrar sesión?" confirm neutral; error colour only on "Borrar cuenta" | 4 | ACCEPTED (not built) | 05 R-113 |
| C-172 | Perfil root: bell only (gear removed), "Preferencias" row then "Acerca de", stat rows chevron only; "Cuenta" is the last group of the Preferencias hub ("Cerrar sesión" row, danger "Borrar cuenta" 16 dp below), no Cuenta screen | 4 | ACCEPTED (built, user emulator look) | 05 §6.0i, R-109, R-113 |
| C-173 | Each training intention gets a distinct stroke-only icon (lime when selected) in the intention picker, the Mi entrenamiento row and the Perfil root value row; gallery lists all | 4 | ACCEPTED (built, user request) | 05 §6.0j |
| V-77 | R-37 error copy and missing periods on the Perfil screens | 4 | ACCEPTED (not built) | 05 R-100, R-115 |
| V-78 | R-70 "Eliminar" in the Peso sheet must be a text danger action | 4 | ACCEPTED (not built) | 05 R-70 |
| V-79 | R-24/R-83/C-77 uppercase labels, centred numeric fields, Cocina error per R-25 | 4 | ACCEPTED (not built) | 05 §6.0i |
| V-80 | R-64(b)/C-20 cyan check marks in "Métrica del gráfico" and "Día del check-in" pickers | 4 | ACCEPTED (not built) | 05 R-64 |
| V-81 | R-12 activity-level sheet applies on tap, no "Listo" footer | 4 | ACCEPTED (not built) | 05 R-12 |
| V-82 | R-04 "Actividad" to "Nivel de actividad"; "Añadir circunferencia" opens "Añadir medida" | 4 | ACCEPTED (not built) | 05 §10 |
| V-83 | R-33 "Sexo" as `SegmentedSelector` | 4 | ACCEPTED (not built) | 05 §6.0i |
| V-84 | R-79/R-31/R-74 all sections failed shows one `ErrorState` | 4 | ACCEPTED (not built) | 05 R-114 |
| V-85 | R-83 "?" stays with its label (Avisos, Registro de series, Mi entrenamiento) | 4 | ACCEPTED (not built) | 05 R-83 |
| V-86 | R-78 Calorías without goals uses `MacroText` | 4 | ACCEPTED (not built) | 05 R-78 |
| V-87 | R-35 glossary words: "Volumen", "Pecho: kg por semana", "Barra Z" (row 435 already correct; code string "10 kg EZ" to fix), one word for medida/mediciones/circunferencia | 4 | ACCEPTED (not built) | 05 §10 |
| V-88 | R-44 "1810 kcal" thousands separator; "Último entreno: 100,0" needs "kg" | 4 | ACCEPTED (not built) | 05 R-44 |
| V-89 | R-43 Peso `HealthStatusLine` directly under the top bar (`WeightScreen.kt:281`) | 4 | ACCEPTED (not built) | 05 R-114 |
| V-90 | C-26 Health Connect row in the day sheet on `surfaceControl` | 4 | ACCEPTED (not built) | 05 C-26 |
| V-91 | R-31 empty training stats show `stats_body_empty` | 4 | ACCEPTED (not built) | 05 R-31 |
| V-92 | R-39 "Historial de peso": "Toca un día para ver su pesaje." and empty-month message | 4 | ACCEPTED (not built) | 05 §6.0i |
| V-93 | V-3 Actividad tile glyphs Outlined | 4 | ACCEPTED (not built) | 05 §6.0i |
| V-94 | R-85 2 x 2 tile grid: resolved by C-160 (one column rules) | 4 | ACCEPTED (not built; resolved by C-160) | 05 R-111 |
| V-95 | R-54 chip "Press banca" vs title "Press de banca": use the name resolver | 4 | ACCEPTED (not built) | 05 §9.0a |
| V-96 | R-14b "Comidas del día" save-rejected footer notice: developer verifies; if missing, R-14b applies as written | 4 | ACCEPTED (not built; verify) | 05 R-14 |
| C-174 | T-297 premium layer: gradients, lit hairlines and radial glows on premium surfaces (alpha shapes, no blur), `Gold` token for records; documents the code as built | 5 | ACCEPTED (user order 2026-11-17: premium design built ignoring the rules; documented as kept deliberately; pending the user's emulator verdict) | 05 §1, §2.2, §4, §6 |
| C-175 | T-297 calendars as `MonthCalendarGrid` draws them: selected = lit lime disc, today = 1.5 dp cyan ring, marked = lime-tint disc with a lime border (lime marks both "has data" and "selected"); amends C-01 / R-64(e) | 5 | PREMIUM EXCEPTION (user order 2026-11-17), kept deliberately; revisit when the design system absorbs it | 05 R-64 |
| C-176 | T-297 `WorkoutRow`: name, date and three glyph + text stats, chevron; amends C-15 and C-74 | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 §6.0, §6.0b, R-16 |
| C-177 | T-297 line chart domain fits the data (12 % margin, min spread 2 kg / 0.4 h, three "nice" intervals as `niceUnit` has them: 1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8 x 10^n); amends C-159 | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 §6.0i, R-112 |
| C-178 | T-297 `WeightPaceChart`: flat dashed goal line keyed "Meta: X kg" only with a plan line, band hidden when taller than half the domain; "Tu plan" figures strip (Ahora, Meta, Fecha); amends C-132 | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 §6.0d, R-101, §10 |
| C-179 | T-297 `DiaryEnergyScale`: amber-to-lime ramp, the chosen cell filled with its ramp tone, not the R-64 selected state | 5 | PREMIUM EXCEPTION (user order 2026-11-17), kept deliberately; revisit when the design system absorbs it | 05 R-64 |
| C-180 | T-297 `TrainerEmptyHero`: hand-built Trainer empty hero with benefit bullets, instead of `EmptyState` | 5 | PREMIUM EXCEPTION (user order 2026-11-17) to R-31, R-57, R-39, kept deliberately; revisit when the design system absorbs it | 05 §6.0 (C-17) |
| C-181 | T-297 a graphic whose labels are hidden from semantics needs a description or the same figures as text in the same card (`TrainerBeforeAfter`, `WeightCard`) | 5 | ACCEPTED (user order 2026-11-17) | 05 R-116 |
| C-182 | T-297 glossary as built: "Meta" = weight goal, "Objetivo" = the plan's objective or rate, "Ahora" = latest weekly average, "Hoy · 79,3 kg" = chart today marker, "Fecha" = the deadline | 5 | ACCEPTED (user order 2026-11-17; matches the strings) | 05 §10 |
| C-183 | T-297 Perfil root: statistics rows show a trailing value (Peso, Fuerza, Calorías) and the hero shows the same figures; exception to R-109 / C-162 | 5 | PREMIUM EXCEPTION (user order 2026-11-17), kept deliberately; revisit when the design system absorbs it | 05 R-109 |
| C-184 | T-297 colour in the premium layer: accents used decoratively (Perfil badges incl. `macroFat`, `macroCarbs`, `electricGreen`, `energyAmber`; hero stats; `DiaryMealStyle` palette); exception to §2.2/§2.3 and principle 3 | 5 | PREMIUM EXCEPTION (user order 2026-11-17), kept deliberately; revisit when the design system absorbs it | 05 R-117 |
| C-185 | T-297 Diario goals prompt as built: hero `AppCard` with lime glow, large lit `IconBadge` (flag), title, line, full-width `PrimaryButton` "Configurar"; the card is not tappable, the button is the single target; replaces the `NavigationRow` of C-42 | 5 | PREMIUM EXCEPTION (user order 2026-11-17) to R-73 / C-42, kept deliberately; revisit when the design system absorbs it | 05 R-73 |
| C-186 | T-297 Diario meal split: "Reparto por comida" toggle row (48 dp, `Role.Button`, Expandida/Contraída, `diary_meal_split_toggle`) under the hero, `DiarySplitBar` always visible, per-meal legend when expanded, hidden until something is logged; `DiaryShareBar` per meal card | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 R-118 |
| C-187 | T-297 Entreno "Esta semana" hero (`RoutinesWeekHero`): cyan-glow card, `WeekStrip`, three `HeroStat`s (Entrenos, Días, Racha), empty-week line; read-only | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 R-119 |
| C-188 | T-297 session-summary celebration (`SummaryHero`): `CelebrationBurst` (Gold when a record was beaten), `displayHero` title, subtitle, `RecordBadge`, Gold glow and bar on record exercises | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict); the subtitle lacks a final period (R-100, open ux finding) | 05 R-120 |
| C-189 | T-297 Welcome value props: `ambientBackdrop`, `StartHeroStage` logo, `displayHero` brand title, tagline, three `StartValueProp` rows | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 R-121 |
| C-190 | T-297 onboarding goals donut (`GoalsResult`): lime-glow card, `DonutChart` 1.3x with kcal in the centre, three `LegendRow`s; legend words ("Proteína", "Hidratos", "Grasas") differ from the glossary and are to be replaced by the `macro_*` strings (open ux finding) | 5 | ACCEPTED (user order 2026-11-17; built, pending the user's emulator verdict) | 05 R-121, §10 |

Status values:
- `PROPOSED`: reported by the critic, not decided yet.
- `IN REVIEW`: accepted in chat, built and merged, not yet approved in the emulator. Its `05` entry is marked pending.
- `ACCEPTED (not built)`: accepted by the user in chat, written into `05` as PENDING, not built yet (becomes `IN REVIEW` when merged).
- `ADOPTED`: approved by the user in the emulator (link: R-## or the `05` section).
- `REWORK`: seen in the emulator and not approved; the row records what was tried and what the user disliked.
- `REJECTED`: refused by the user (also copied to `05` §8 "Decided — do not re-propose").
- `DEFERRED`: postponed (link: E9 row or round).
- `PREMIUM EXCEPTION`: the T-297 premium design was ordered by the user on 2026-11-17 ignoring the rules; the code is kept as built and `05` documents it as a deliberate exception, to revisit when the design system absorbs it.

A round that changes tokens or components does not start while the previous such round has rows `IN REVIEW`.

Round 1 (T-281, Diario) verdict: the user approved the Diario visual result in the emulator on 2026-11-13; every round 1 row is `ADOPTED`. Earlier state: all rows C-21…C-43, V-5…V-13 and U-1 were accepted by the user on 2026-11-13 and are written into `05` §8 as PENDING (R-67…R-74). Rows marked "build waits for round 0 verdict" touch tokens or components and are not built until the round 0 rows are `ADOPTED` or `REWORK`. Placement double-check (C-21, C-22, C-39, C-42) and calorie graphic (C-23, C-29): agreed with conditions recorded in R-68…R-70.

Round 2 (T-282, Entreno): the user decided on 2026-11-14: C-68 REJECTED, every other row accepted; they were written into `05` §6.0b and §8 R-79…R-90 (rules mode). Verdict: the user approved the Entreno look in the emulator on 2026-11-14 (T-282 merged, PR #675), including the redesigned routines list and cardio row; every round 2 row is `ADOPTED` (C-68 stays `REJECTED`, V-36 `RESOLVED`) and the PENDING tags are removed from `05`. V-21's type/RPE cells stay 40 x 48 dp with an accessibility-sweep exemption (ux-reviewer decision, 2026-11-14). Rows whose build differs from `05`: V-2 (superseded by V-21) and C-15 (the built `WorkoutRow` has two supporting lines; `05` §6.0b corrects it).

Round 0 build state (T-295): all rows C-01…C-19 and V-1…V-4 are built on `feat/ui-design-system` and the PENDING tags are removed from `05-design-system.md`. Verdict (user, chat, 2026-11-14): round 0, Diario and Entreno are all approved; every row C-01…C-19, V-1…V-4 and V-15b is `ADOPTED` (C-09 as reworked).
