# UX critic ledger

One row per ux-critic finding. The PM has the dispositions recorded here as the user decides (rules mode of ux-reviewer). The critic reads it first and does not repeat a row in any status except `REWORK`, where it may only propose a different solution.

| ID | Pattern (keywords) | Round | Status | Link |
|---|---|---|---|---|
| C-01 | selected state: selectedContainer #354925 + 1dp lime border + lime W700 label (chips, toggle chips, weekday, portion, cooking, segmented, time range) | 0 | IN REVIEW | 05 §2.1a, §6.0, R-64 |
| C-02 | disabled PrimaryButton: lime 20 % container, lime 50 % label | 0 | IN REVIEW | 05 §2.1a, R-65 |
| C-03 | textTertiary #8C97A9 for previous-set cell, placeholders, wheel neighbours; ghostData only disabled | 0 | IN REVIEW | 05 §2.1a, R-65 |
| C-04 | SetRow header «ANTES», tracking 0.02 em, REPS weights, RPE variant inset 8 dp | 0 | IN REVIEW | 05 §3, §6.0, §10 |
| C-05 | borderControl white 36 % for input resting borders | 0 | IN REVIEW | 05 §2.1a, §6.0 |
| C-06 | surfaceControl #242E3B for inputs, steppers, pending set circle, SecondaryButton | 0 | IN REVIEW | 05 §2.1a, §6.0 |
| C-07 | textSecondary #A9B3C4 | 0 | IN REVIEW | 05 §2.1a |
| C-08 | GhostButton neutral, focus border lime 2 dp | 0 | IN REVIEW | 05 §6.0 |
| C-09 | tabs shared 12 sp label, scroll (min 72 dp) only if a word does not fit | 0 | REWORK (tried 12 sp; user found it too small in the search tabs: now 14 sp, 6/8 dp padding, full-bleed, indicator visible while scrolling) | 05 §9 (amended), §6.0 |
| C-10 | ring caption/status bodyMedium 14/20 W600 | 0 | IN REVIEW | 05 §3, §6.0 |
| C-11 | trackInactive white 16 % + 4 dp stop dot on linear bars | 0 | IN REVIEW | 05 §2.1a, §6.0 |
| C-12 | WeekdaySelector 7 weighted cells, no scroll, cells >= 40x48 dp | 0 | IN REVIEW | 05 §6.0, §7 exception |
| C-13 | titleSection 18/24 W600 for ListSectionHeader, top-bar title W700 | 0 | IN REVIEW | 05 §3, §6.0 |
| C-14 | AddItemButton leading ic_add 20 dp | 0 | IN REVIEW | 05 §6.0 |
| C-15 | WorkoutRow headline titleMedium, one bodyMedium supporting line, empty trailing | 0 | IN REVIEW | 05 §6.0 |
| C-16 | nav labels sentence case 12/16 W600, icons 24 dp | 0 | IN REVIEW | 05 §3, §6.0, §10 |
| C-17 | EmptyState 32 dp glyph in 72 dp circle, per-domain icon; error buttons min width 200 dp | 0 | IN REVIEW | 05 §6.0, R-66 |
| C-18 | sleep stages cyan ramp 100/75/50 % + amber Despierto | 0 | IN REVIEW | 05 §2.1a |
| C-19 | PrimaryMinHeight 56 dp, radius 16 dp | 0 | IN REVIEW | 05 §6.0 |
| V-1 | discomfort row: borderless text action, no mid-word break | 0 | IN REVIEW | 05 §6.0 (R-51) |
| V-2 | REPS field >= 48 dp | 0 | IN REVIEW | 05 §6.0 (R-58) |
| V-3 | legacy filled icons to Outlined (ic_info, ic_photo_camera, ic_photo_library, ic_label, ic_auto_awesome, ic_nav_trainer, ic_health_connect). ic_nav_trainer history: original filled `insights` (liked) > T-294 hand-drawn trend+sparkle (rejected, broken) > `insert_chart` bf5317ff (rejected, degradation) > decision 2026-11-13: Material Symbols Outlined `insights`, verbatim path | 0 | IN REVIEW | 05 §6.0 |
| V-4 | remove unused sheetHandle and glowLime/glowBlur tokens | 0 | IN REVIEW | 05 §2.1a |
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
| V-15b | V-15 follow-up: `compactMacrosFit` measures the real ring-column width; host test required; 360 dp header fallback (pill moves to the macros line) | 1 | PENDING (batch 5; part of V-15 and R-75) | 05 R-76, R-78 |
| U-1 | diary entry with ingredients: collapsed swipe removes the whole food (user request, explicit exception to "no behaviour change"; reconfirmed in batch 4, unchanged) | 1 | ADOPTED | 05 R-71 |
| C-66 | Rutinas: the `isNext` routine's "Empezar" is a PrimaryButton, others secondary | 2 | IN REVIEW | 05 R-79, §6.0b |
| C-67 | live set row: current set cyan ring + "Serie actual", done rows on surfaceOverlay without border | 2 | IN REVIEW | 05 R-80, §6.0b |
| C-68 | finish the workout only when all sets are done | 2 | REJECTED (user: "also in the three dots") | 05 §8 Decided |
| C-69 | live session phone: full-width AppLinearProgress under the top bar | 2 | IN REVIEW | 05 §6.0b |
| C-70 | set note button: one `sticky_note_2` glyph, tertiary / cyan by state | 2 | IN REVIEW | 05 R-80, §6.0b |
| C-71 | RpeCell reuses the SetTypeBadge container | 2 | IN REVIEW | 05 R-80, §6.0b |
| C-72 | one glyph per action (timer, calculate, fitness_center, drive_file_move, content_copy, flag, sticky_note_2); menu title = object | 2 | IN REVIEW | 05 R-81 |
| C-73 | Historial year: 3 x 4 MonthTiles, bare count, unit in the heading | 2 | IN REVIEW | 05 §6.0b |
| C-74 | AppListRow min height 56 / 72 / 88 dp by line count | 2 | IN REVIEW | 05 §6.0b |
| C-75 | StepperLine: sentence-case labels, label + ? flow, 48 dp buttons | 2 | IN REVIEW | 05 R-83, §6.0b |
| C-76 | set note reasons as wrapping toggle chips | 2 | IN REVIEW | 05 R-84 |
| C-77 | date field looks editable (surfaceControl + border + calendar icon); NumberInput start-aligned, unit trailing | 2 | IN REVIEW | 05 R-83, §6.0b |
| C-78 | set lines textPrimary titleMedium on history and session detail | 2 | IN REVIEW | 05 R-82 |
| C-79 | one set-summary grammar in routine details | 2 | IN REVIEW | 05 R-82 |
| C-80 | summary and session detail figures as a MetricTile row | 2 | IN REVIEW | 05 R-85 |
| C-81 | picker chip rows captioned, 8 dp apart | 2 | IN REVIEW | 05 R-86 |
| C-82 | saving indicator 20 dp before the label | 2 | IN REVIEW | 05 §6.0b |
| C-83 | sheet header grows with a two-line title | 2 | IN REVIEW | 05 §6.0b |
| C-84 | "Pesas por mano: No" hidden; true = pill "Una pesa en cada mano" | 2 | IN REVIEW | 05 R-87 |
| C-85 | Rutinas offline hint is the first list item | 2 | IN REVIEW | 05 R-88 |
| C-86 | exercise form chips wrap (low confidence; interacts with C-37) | 2 | IN REVIEW | 05 R-86 |
| C-87 | pinned footer capped at 480 dp on wide screens | 2 | IN REVIEW | 05 §6.0b |
| C-88 | calendar month, no day selected: "Toca un día para ver sus entrenos." | 2 | IN REVIEW | 05 §6.0b |
| C-89 | builder footer hidden until loaded | 2 | IN REVIEW | 05 R-88 |
| C-90 | cardio card shares the strength grid (weights 1 : 1, note on line 1) | 2 | IN REVIEW | 05 §6.0b |
| C-91 | TIEMPO drawn as a field (`StopwatchField.TimeText`, every caller) | 2 | IN REVIEW | 05 R-39 |
| C-92 | cardio start: tonal circle on line 1, "Iniciar" pill on line 2 when it does not fit | 2 | IN REVIEW | 05 §6.0b |
| C-93 | host-test assertions for the cardio grid alignment (Spec 7) | 2 | IN REVIEW | 05 §6.0b |
| C-94 | routine card: one 72 dp row, circular start, no "Empezar" button | 2 | IN REVIEW | 05 §6.0b |
| C-95 | folder as a card, whole row toggles | 2 | IN REVIEW | 05 §6.0b |
| C-96 | one add row: "Añadir rutina" + folder icon button | 2 | IN REVIEW | 05 §6.0b |
| C-97 | "Sin carpeta" is `ListSectionHeader(title, count)` | 2 | IN REVIEW | 05 §6.0b |
| C-98 | pencil removed from the routine card ("Editar" stays in the sheet and the details top bar) | 2 | IN REVIEW | 05 §6.0b |
| C-99 | the round start button is lime on every routine card; "Siguiente" is the cyan border, a flag and text | 2 | IN REVIEW | 05 R-79, R-64 |
| C-100 | routine cards lose the drag handle: touch-and-hold to reorder, "Subir"/"Bajar" in the ⋮ sheet | 2 | IN REVIEW | 05 R-22 |
| V-21 | live set row reaches 48 dp (KG/REPS 48 wide, TIPO/RPE 40 x 48); amends V-2 and C-04 (build gave 40 / 35 dp) | 2 | IN REVIEW | 05 R-80, §6.0b, §7 |
| V-22 | distance/time set row on two lines, no ANTES column; "Antes:" on line 2 | 2 | IN REVIEW | 05 §6.0b, R-80 |
| V-23 | stepper "+" and "Sin objetivo" fit (see C-75) | 2 | IN REVIEW | 05 R-83 |
| V-24 | year view breaks words (see C-73) | 2 | IN REVIEW | 05 §6.0b |
| V-25 | skeletons: box heights, 16 dp margins, fallback title "Rutina" | 2 | IN REVIEW | 05 §6.0b, R-88 |
| V-26 | durations "55 min", empty volume segment dropped | 2 | IN REVIEW | 05 R-82 |
| V-27 | "×" never "x" | 2 | IN REVIEW | 05 R-82 |
| V-28 | session menu titled with the routine name; sentences end with a period | 2 | IN REVIEW | 05 R-89 |
| V-29 | "Se eliminará este entreno con todas sus series." | 2 | IN REVIEW | 05 §10, R-89 |
| V-30 | AddItemButton for "Añadir serie" in builder and live card | 2 | IN REVIEW | 05 R-90 |
| V-31 | superset ? on the label's line | 2 | IN REVIEW | 05 R-90 |
| V-32 | "Ver progreso" is a NavigationRow | 2 | IN REVIEW | 05 R-90 |
| V-33 | exercise search `showLabel = false` | 2 | IN REVIEW | 05 R-86 |
| V-34 | picker chip rows full-bleed with content padding | 2 | IN REVIEW | 05 R-86 |
| V-35 | R-70 covers AppEditorScreen ("Editar entreno" Eliminar) | 2 | IN REVIEW | 05 R-90 |
| V-36 | date dialog selected day: Material 3 draws no border; lime fill accepted (user decision 2026-11-14) | 2 | RESOLVED | 05 R-64 e |
| V-37 | "Mover a carpeta" via OptionPickerSheet with lime check | 2 | IN REVIEW | 05 R-90 |
| V-38 | star marks "Siguiente entreno" (replaced by `flag`, see C-72) | 2 | IN REVIEW | 05 R-81 |
| V-39 | titles: Descanso, add picker, Entreno tab header = tab label (R-04 wins), detail error copy | 2 | IN REVIEW | 05 R-01, R-89 |
| V-40 | offline menus dim actions in both (low) | 2 | IN REVIEW | 05 R-88 |
| V-41 | offline favourite star carries the hint (low) | 2 | IN REVIEW | 05 R-88 |
| V-42 | cardio: note alone on line 2 (note back on line 1 after LISTO) | 2 | IN REVIEW | 05 §6.0b |
| V-43 | cardio: TIEMPO centred 24 dp off its header | 2 | IN REVIEW | 05 §6.0b |
| V-44 | cardio: TIEMPO had no field container | 2 | IN REVIEW | 05 R-39 |
| V-45 | "Sin carpeta" was a raw Text (now `ListSectionHeader` with count) | 2 | IN REVIEW | 05 §6.0b |
| V-46 | copy: routines error, offline menu and folder hints end with a period | 2 | IN REVIEW | 05 R-88 |
| V-47 | routine skeleton row height equals the card (72 dp) | 2 | IN REVIEW | 05 §6.0b |
| V-48 | cardio start description "Iniciar cardio" | 2 | IN REVIEW | 05 R-81 |
| V-49 | empty folder: no "0 rutinas", no chevron, toggle inert | 2 | IN REVIEW | 05 §6.0b |

Status values:
- `PROPOSED`: reported by the critic, not decided yet.
- `IN REVIEW`: accepted in chat, built and merged, not yet approved in the emulator. Its `05` entry is marked pending.
- `ADOPTED`: approved by the user in the emulator (link: R-## or the `05` section).
- `REWORK`: seen in the emulator and not approved; the row records what was tried and what the user disliked.
- `REJECTED`: refused by the user (also copied to `05` §8 "Decided — do not re-propose").
- `DEFERRED`: postponed (link: E9 row or round).

A round that changes tokens or components does not start while the previous such round has rows `IN REVIEW`.

Round 1 (T-281, Diario) verdict: the user approved the Diario visual result in the emulator on 2026-11-13; every round 1 row is `ADOPTED`. Earlier state: all rows C-21…C-43, V-5…V-13 and U-1 were accepted by the user on 2026-11-13 and are written into `05` §8 as PENDING (R-67…R-74). Rows marked "build waits for round 0 verdict" touch tokens or components and are not built until the round 0 rows are `ADOPTED` or `REWORK`. Placement double-check (C-21, C-22, C-39, C-42) and calorie graphic (C-23, C-29): agreed with conditions recorded in R-68…R-70.

Round 2 (T-282, Entreno): the user decided on 2026-11-14: C-68 REJECTED, every other row accepted; they are written into `05` §6.0b and §8 R-79…R-90 as PENDING (rules mode). IN REVIEW rows whose build differs from `05`: V-2 (superseded by V-21) and C-15 (the built `WorkoutRow` has two supporting lines; `05` §6.0b corrects it).

Round 0 build state (T-295): all rows C-01…C-19 and V-1…V-4 are built on `feat/ui-design-system` and the PENDING tags are removed from `05-design-system.md`. Every row stays `IN REVIEW` until the user's emulator verdict sets `ADOPTED` or `REWORK`.
