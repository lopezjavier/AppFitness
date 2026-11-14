# ux-critic round 2: Entreno (US-123), 2026-11-14

Source: ux-critic review of `composeApp/build/gallery/entreno/` (149 images). User decision (2026-11-14): **C-68 rejected** ("the button is also accessible in the three dots"); **everything else accepted** (C-66…C-67, C-69…C-89, V-21…V-41). Where a row says "PM decides", the PM's default is recorded in the rules step.

## Proposals

| ID | Screen | Problem | Change | Component / rule |
|---|---|---|---|---|
| C-66 | Rutinas | No primary action; every "Empezar" is a grey `SecondaryButton`. | The `isNext` routine gets `PrimaryButton` "Empezar"; others stay secondary. | §6.0 Buttons, `RoutineCard`; rule "one primary per screen" |
| C-67 | Live session | Next set not marked; done rows look editable. | First pending set: 1.5 dp `athleticCyan` ring on its check circle, `stateDescription` "Serie actual"; done rows draw KG/REPS on `surfaceOverlay` with no border. | `SetCompletionCheckbox`, `SetRow`, R-64 d |
| C-68 | Live session | Finish only when all sets are done. | **REJECTED by the user.** | — |
| C-69 | Live session (phone) | No session progress (tablet has it). | Full-width `AppLinearProgress` (4 dp, lime on `trackInactive`, stop dot per C-11) under the top bar; existing `live_progress_summary` as `stateDescription`. | `AppScreen` live header |
| C-70 | Live session | Note button is a `notes` glyph that reads as a menu; pencil once a note exists. | One glyph, Material Symbols Outlined `sticky_note_2` 24 dp; `textTertiary` without a note, `athleticCyan` with one. | `SetNoteButton`, `AppIconButton` |
| C-71 | Live session | RPE cell is bare text, no picker cue. | `RpeCell` reuses the `SetTypeBadge` container: "—" `textSecondary`, value `athleticCyan` W700. | `SetRow.RpeCell` |
| C-72 | Menus | One action has two glyphs; one glyph has several meanings. | One glyph per action, Outlined 24 dp: Descanso `timer`; Calculadora `calculate`; Cargador de discos `fitness_center`; Mover a carpeta `drive_file_move`; Duplicar `content_copy`; Siguiente entreno `flag`; notes `sticky_note_2`; group/ungroup and "Revisar tus rutinas" glyphs to be verified. Live menu title = exercise name. "Normal" row uses the "N" badge. | `AppSheetAction` icons, R-01, R-56; new rule "one glyph per action" |
| C-73 | Historial year | 12 tiles of ~118 dp repeating "0 entrenos". | 3 × 4 cells of at least 64 dp: month name `bodySmall`, count as bare `labelNumeric` (cyan above 0, `textTertiary` at 0), unit once in the heading "2026 · 3 entrenos". | `MonthTile` |
| C-74 | Añadir entreno, Historial | Two-line rows at 48 dp pitch. | `AppListRow` min height by line count: 56 / 72 / 88 dp. | `AppListRow`, `WorkoutRow` |
| C-75 | Routine builder | Stepper labels mix uppercase and sentence case; row cannot hold label, `?` and value. | Labels `bodyMedium` W600 `textSecondary`, sentence case ("Descanso"); `StepperLine` flows label + `?`, then the stepper (drops to its own line if needed); buttons 48 dp. | `StepperLine`, `NumberStepper` |
| C-76 | Set note sheet | Five categorical reasons drawn as scale rows, decorative cyan. | `AppToggleChip`s in a wrapping row (R-64 selected state, 8 dp gaps). | `NoteSheet`, R-33, R-45 |
| C-77 | Editar / Entreno pasado | "Fecha" looks disabled; "Duración" centred. | Date field `surfaceControl` + `borderControl` with trailing calendar icon; `NumberInput` start-aligned with unit in the trailing slot. | `WorkoutFormBody`, `NumberInput`, R-24 |
| C-78 | Exercise history, session detail | Same set line cyan in one, white in the other. | Set lines `textPrimary`, `titleMedium` 16/22 W600 on both; cyan only for summary figures. | `ExerciseHistoryTab.SetLine`, session set line |
| C-79 | Routine details | Two grammars for set summaries. | One grammar, equal sets grouped, one line with ellipsis: "1 × 10 · 40 kg, 3 × 8 · 60 kg"; "2 × 1 min". | `RoutineDetailsScreen` |
| C-80 | Summary, session detail | Label-left/value-right rows use 100-110 dp. | One row of `MetricTile`s (as C-24). | `SessionSummaryScreen`, `SessionDetailScreen` |
| C-81 | Exercise picker | Two chip rows without captions. | Captions "Grupo muscular" / "Equipamiento" in `labelTechnical`; 8 dp between rows. | `ExercisePickerScreen` |
| C-82 | Saving state | Progress arc at the far trailing edge. | 20 dp indicator (2 dp stroke) directly before the label, 8 dp gap, centred as a group. | `PrimaryButton` loading slot |
| C-83 | Warm-up sheets | Two-line title touches the first field label. | Sheet header grows with the title and keeps 12 dp below. | `AppBottomSheet` header |
| C-84 | Exercise detail | "Pesas por mano: No" states a negative. | Hide when false; when true a pill "Una pesa en cada mano". | `ExerciseSummaryTab.DetailHeader`, R-57, R-35 |
| C-85 | Rutinas offline | Offline hint is the last list item. | First list item. | `RoutinesScreen`, R-30, R-59 |
| C-86 | Exercise form | Required single choice among ~12 groups scrolls sideways. | Chips wrap in the two form sheets (low confidence; interacts with C-37). | `ExerciseFormSheet` |
| C-87 | Picker, tablet | Pinned footer ~808 dp wide. | Cap pinned footers at `FormContentMaxWidth` 480 dp, centred. | `AppScreen` footer, §5 |
| C-88 | Historial month | ~170 dp empty under the grid with no day selected. | Text-only line "Toca un día para ver sus entrenos." (`bodyMedium` `textSecondary`). | `WorkoutCalendarScreen` |
| C-89 | Routine builder error | Disabled "Guardar" under "Reintentar". | Hide the footer until the content has loaded. | `RoutineBuilderScreen`, R-31, R-34 |

## Rule violations (all accepted)

| ID | Rule | Evidence | Fix direction |
|---|---|---|---|
| V-21 | §7 (48 dp), R-58; V-2, C-04 IN REVIEW | Live set row at 360 dp: KG 40 dp, REPS 35 dp; header "REP" at font 1.3. | Reach 48 dp: inset 0, TIPO and RPE cells 40 × 48 dp, live card padding 12 dp. |
| V-22 | §7, R-58 | Distance and time rows: KM 28 dp shows ",2", time "20:0". | Two-line row for these exercises (PM decides the layout with ux-reviewer). |
| V-23 | §7 | Builder "Objetivo RPE": "+" about 11 dp wide with "Sin objetivo". | See C-75. |
| V-24 | §7, WCAG 1.4.4 | Year view breaks "entren/os", clips "Septiemb". | See C-73. |
| V-25 | R-31 | Exercise detail loading is blank (`SkeletonBox` no height); routine details skeleton full-bleed, bars without title. | Give boxes a height; 16 dp margins; fallback title "Rutina". |
| V-26 | R-47 | Duration "55:00" in history rows, chooser, summary, detail; empty volume prints a dash. | "55 min"; drop the empty segment. |
| V-27 | R-35, R-44 | "x" vs "×". | "×" everywhere. |
| V-28 | R-37 | Session menu sheet titled "EN VIVO"; sentences without periods; `calendar_error` with a period. | Title = routine name; add periods; one form for error messages. |
| V-29 | R-35 | "Se borrará este entreno…" ("Borrar" reserved). | "Se eliminará este entreno con todas sus series." |
| V-30 | R-19, R-34 (C-14) | Builder "Añadir serie" is a `GhostButton` without icon. | `AddItemButton` in both. |
| V-31 | R-49, R-57 | Superset `?` alone on its own line. | Attach to the "Superserie A1" label. |
| V-32 | R-03 (V-8) | "Ver progreso" is a full-width `SecondaryButton` that navigates. | `NavigationRow` labelled with the destination title. |
| V-33 | R-39 sweep of C-21 | Exercise search still shows the "Buscar" label. | `showLabel = false`. |
| V-34 | R-39 sweep of C-37 | Picker chip rows padded, clip at the 16 dp margin. | Content padding, full-bleed row. |
| V-35 | R-39 sweep of R-70 / C-22 | "Editar entreno": "Eliminar" is a bordered 56 dp button 8 dp under "Guardar"; footer ~144 dp. | PM decision: R-70 covers `AppEditorScreen`; sweep it. |
| V-36 | R-64 e (C-20) | Date dialog: selected day without visible 1 dp lime border (medium-low). | Check on a Spanish and an English device. |
| V-37 | R-07, R-64 b | "Mover a carpeta" shows the current folder as a disabled row. | Lime check on the current folder; use `OptionPickerSheet`. |
| V-38 | R-56 | Star marks "Siguiente entreno". | See C-72 (`flag`). |
| V-39 | R-04, R-01 | "Descanso" opens "Descanso de este ejercicio"; add-mode picker titled "Ejercicios"; tab header "Rutinas" (R-01 vs R-04); detail error "No se pudo cargar el catálogo de ejercicios". | Align titles; decide tab header; "No se pudo cargar el ejercicio". |
| V-40 | R-34 (low) | Offline routine menu hides actions; folder menu dims them. | Dim in both. |
| V-41 | R-30 (low) | Exercise detail offline: favourite star disabled with no hint. | Standard hint or tooltip. |

IN REVIEW rows whose build differs from `05`: V-2 (see V-21); C-15 says `WorkoutRow` has one supporting line, the built row has two.

## Gallery defects (tooling, not UX)

- No sheet rendered in `sesi_n_en_vivo/set_type_picker.png`, `editar_entreno/set_type_picker.png`, `editar_entreno/rpe_picker.png`.
- `rutinas/*` captured on a white canvas; `entreno/default.png` shows only the header.
- Historial captured without a back arrow; exercise media absent; `no_session.png` equals the loading state.

## Re-review after the emulator look (ux-critic, 2026-11-14): routines list and cardio live row

The user was not happy with the Entreno root (wanted a redesign) and the cardio live row (misaligned). Reference images (gitignored): `design/reported/e19-entreno-routines-list.png`, `e19-entreno-cardio-live.png`, `e19-entreno-routines-reference.png`. **The user approved all findings with the ux-critic's recommendations (2026-11-14), including C-98 (pencil removed) and the rebuild scope (state, events, tags and sheets unchanged). "~55 min", muscle chips and "Crear rutina aquí" are new behaviour: not built, not added to the backlog.** Supersedes V-22 in part (note leaves line 2; weights 1:1; header keeps the note column), C-66 in form only (circular start button; R-79 stays). All dp at 360 dp.

| ID | Change |
|---|---|
| C-90 | Cardio card uses one grid shared with strength rows (Spec 1). |
| C-91 | TIEMPO drawn as a field (Spec 2); sweep `StopwatchField.TimeText` (R-39). |
| C-92 | Start control as a tonal circle, "Iniciar" pill on line 2 when it does not fit (Spec 3). |
| C-93 | Host-test assertions (Spec 7), tests batch only. |
| C-94 | Routine card is one compact row with a circular start button (Spec 4). |
| C-95 | Folder as a card, whole row toggles (Spec 5). |
| C-96 | One add row: "Añadir rutina" + folder icon button (Spec 6). |
| C-97 | "Sin carpeta" is `ListSectionHeader(title, count = n)`. |
| C-98 | Pencil removed from the routine card ("Editar" stays in the ⋮ sheet and the details top bar). |
| V-42..V-45 | Rule violations fixed by the above (note alone on line 2; TIEMPO 24 dp off; no TIEMPO container; "Sin carpeta" raw Text). |
| V-46 | Copy: `routines_error` → "No se pudieron cargar tus rutinas. Inténtalo de nuevo."; offline menu hint → "Sin conexión: no se puede duplicar, mover ni cambiar el siguiente entreno."; folder hint → "Sin conexión: no se puede renombrar ni borrar la carpeta." |
| V-47 | Routine skeleton row height equals the new card (72 dp). |
| V-48 | Cardio start description "Iniciar cardio" (parallel to "Pausar cardio" / "Reanudar cardio"). |
| V-49 | Empty folder: no "0 rutinas", no chevron (Spec 5). |

**Dropped from the reference:** status dot, "+ Carpeta" in the header, "MIS CARPETAS", "2 activas", "Rutinas sueltas", lime on every play button and on "Añadir rutina", muscle chips, "~55 min", "Crear rutina aquí".

### Spec 1 (C-90): cardio grid
Root cause: `SetRow.kt:199` `twoLine = values.cardioPlaceholder != null` forces two lines; `:222` `trailing.takeUnless { twoLine }` removes the note from line 1; `:249-267` `CardioPreviousLine` draws the note alone after a `Spacer(weight 1)`; `:531` `if (showNote && !cardioTime)` drops the note column from the cardio header (LISTO 52 dp off); `CardioTimeCell.kt:94-100` the cell is `Row { Box(weight 1) { time }; StartButton }` and the header centres over the whole cell (24 dp off the time).
- **Line 1:** one `Row`, `heightIn(min = 48.dp)`, `spacedBy(4.dp)`, `CenterVertically`; header and row share the `SetRowDefaults` constants. Columns: TIPO 40 | fields `weight(1f)` each, `widthIn(min = 72.dp)` | START 48 (new `StartCellWidth`, only when it fits, Spec 3) | LISTO 48 | NOTA 48. One field (Cinta): TIPO 0-40, TIEMPO 44-148, START 152-200, LISTO 204-252, NOTA 256-304. Two fields (KM/PASOS/PISOS + TIEMPO): field 44-120, TIEMPO 124-200, LISTO 204-252, NOTA 256-304, START on line 2. LISTO and NOTA land on the same x as strength rows. Weights 1:1; drop `CARDIO_TIME = 1.4f`.
- **Header:** "TIPO" | field labels centred over the field containers | 48 dp spacer (START, when on line 1) | "LISTO" | 48 dp spacer (NOTA). Remove the `!cardioTime` exception.
- **Line 2:** only when `previous` is not blank: "Antes: 5 km · 30:00", `bodySmall` 12/16 `textTertiary`, `padding(start = 44.dp)`, 20 dp high, no trailing action.

### Spec 2 (C-91): TIEMPO field
Container as `NumberInput`: `surfaceControl` fill, 1 dp `borderControl`, `AppShapes.base`, min height 48 dp, fills its column, text centred, 4 dp horizontal padding. Placeholder "20 min": `labelNumeric` 18/22 at `NumberInputDefaults.PlaceholderWeight`, `textTertiary`, one line (steps down to `bodyMedium` 14/20 if it does not fit). Typed time: unchanged `StopwatchFieldDefaults.timeStyle`, `textPrimary`. Done set: `surfaceOverlay`, no border (as KG/REPS, C-67). Same container in `StopwatchField.TimeText`.

### Spec 3 (C-92): start control
- **On line 1:** 48 x 48 target, 38 dp circle (`SetCompletionCheckboxDefaults.VisualSize`), `surfaceControl`, `ic_play_arrow` 24 dp `textPrimary`. Run in the bar = disabled, glyph `neonLime`, existing description "En marcha en la barra"; done set = disabled, glyph `ghostData`. Tag `session_cardio_start_<p>_<s>` unchanged.
- **Fit rule** (replaces the `BoxWithConstraints` branch): START stays on line 1 while every field keeps >= 72 dp x font scale (one field at 360 dp: up to font scale 1.44; two fields: from 412 dp wide).
- **Fallback on line 2:** `Row(padding(start = 44.dp), spacedBy(8.dp))` with a pill 40 dp high in a 48 dp target, `surfaceControl`, `AppShapes.full`, 16 dp horizontal padding, `ic_play_arrow` 20 dp + "Iniciar" `bodyMedium` W600 `textPrimary`; then "Antes: …" with `weight(1f)`, max 2 lines.

### Spec 4 (C-94, C-98): routine card
`AppCard(highlighted = isNext, contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp))`, one `Row(CenterVertically)`. Order: `DragHandle` 48 | 12 dp | name block `weight(1f)`, min height 56 dp | start 48 | ⋮ 48 `textSecondary` (no pencil). Name block: name `titleMedium` max 2 lines; 4 dp; `bodyMedium` `textSecondary` "6 ejercicios". Next routine: "Siguiente · 6 ejercicios", "Siguiente" in `athleticCyan` W600; replaces the separate `labelTechnical` line. Start button: 40 dp circle, `ic_play_arrow` 24 dp; `isNext`: `neonLime` fill, `onNeonLime` glyph; others: `surfaceControl` fill, `textPrimary` glyph; disabled: next = lime 20 % fill, lime 50 % glyph (C-02), others = `ghostData` glyph. `contentDescription` "Empezar <rutina>" (`routines_start_cd`); tag `routines_start_<id>` unchanged. Card 72 dp. The card tap still opens details with its pinned full-width "Empezar".

### Spec 5 (C-95): folder card
`AppCard(contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp))`, `Row(CenterVertically)`, min height 56 dp. Order: tile | 12 dp | text block `weight(1f)` | ⋮ 48. Tile: 40 x 40, `AppShapes.md`, `surfaceControl`, Outlined `folder` (collapsed) / `folder_open` (expanded, new drawable) 24 dp `textSecondary`. Line 1: name `titleMedium` + `expand_more` 24 dp `textSecondary` right after it (4 dp gap, rotated when expanded, as R-75). Line 2: `bodyMedium` `textSecondary`, one line, ellipsis: "2 rutinas · Torso A, Pierna A"; collapsed and holding the next routine: "4 rutinas · Siguiente: Torso A" (second part `athleticCyan`); empty: "Carpeta vacía" `textTertiary`, no "0 rutinas", no chevron, no body. New strings `routines_folder_summary` "%1$s · %2$s", `routines_folder_next` "Siguiente: %1$s". Tile + text block are one toggle with the existing descriptions; ⋮ separate. Expanded: routine cards follow at 12 dp, full width; 24 dp between groups. No lime on tile or chevron (R-64).

### Spec 6 (C-96): add row
`Row(spacedBy(8.dp))`: `AddItemButton("rutina")` `weight(1f)` (tonal, unchanged) + a 56 x 56 icon button, radius 16 dp, `surfaceControl`, Outlined `create_new_folder` 24 dp `textPrimary` (new drawable), `contentDescription` and tooltip "Añadir carpeta", tag `routines_add_folder`, disabled offline. Last list item, not pinned.

### Spec 7 (C-93): assertions (tests batch only)
For every `ExerciseMetricType`: note and check centres differ <= 1 dp vertically; header centre compared with the field container, not the composite cell; LISTO and NOTA left edges equal between a strength and a cardio card; at 360 dp and font scale 1.0 / 1.3 / 2.0.

## Follow-up after the second emulator look (ux-critic, 2026-11-14): lime start and the drag handle

**User decisions (2026-11-14):** the round start button is lime on EVERY routine card (C-99); the drag handle is removed and reordering becomes touch-and-hold plus "Subir"/"Bajar" in the ⋮ sheet (C-100, recommended option b + d).

### C-99: lime start on every card
- `CircleIconButton` on every `RoutineCard`: `neonLime` fill, `onNeonLime` glyph (`accent = true` always). Disabled (offline or starting): lime 20 % fill, lime 50 % glyph on all cards.
- **R-79 reworded in `05`:** "A screen has at most one `PrimaryButton` in its content. Exception (user, 2026-11-14): the round start button of every routine card (`CircleIconButton`, `neonLime` fill, `onNeonLime` glyph) is lime. It is the card's own action, not a `PrimaryButton`, and no other repeated per-row control may be lime. The Entreno root has no `PrimaryButton`; 'Añadir rutina' stays tonal. Lime never means 'next'."
- **"Siguiente" cue (cyan only, R-64 d):** keep the 1.5 dp `athleticCyan` card border (`AppCard(highlighted = true)`). Supporting line: a 16 dp Outlined `flag` glyph in `athleticCyan`, 4 dp gap, then "Siguiente · 6 ejercicios" with "Siguiente" in `athleticCyan` W600 (R-81 assigns `flag` to "Siguiente entreno"; verify the drawable exists, else add it as Material Symbols Outlined). `stateDescription` "Siguiente entreno" and the collapsed-folder line "4 rutinas · Siguiente: Torso A" unchanged.

### C-100: no drag handle; touch-and-hold to drag + "Subir"/"Bajar" rows
- **Card:** remove `DragHandle` and its 12 dp spacer from `RoutineCard`; `contentPadding` start 12 dp (end 4, top and bottom 8 unchanged). The name edge lands at 28 dp (the folder tile's left edge); name width 216 dp at 360 dp.
- **Gesture:** touch-and-hold on the name block picks the card up (`detectDragGesturesAfterLongPress`, system touch-and-hold delay); a tap still opens details; swipe-left delete and list scroll unchanged. Tick haptic on pick-up and on each swap, and auto-scroll, as in R-22. A swap needs half a slot of travel (about 42 dp) after the hold and can be dragged back. Same `RoutinesEvent.Reorder(folderId, orderedIds)`, within one group; the TalkBack "Subir"/"Bajar" actions stay.
- **Picked-up look** (`RoutineCard` currently ignores `dragging`): scale 1.02, container `surfaceOverlay`, 1 dp `borderControl`, no shadow (§4); no scale when animations are off.
- **⋮ sheet order:** Editar, Duplicar, Mover a carpeta, Siguiente entreno, Subir, Bajar, Eliminar. "Subir"/"Bajar" (`ic_arrow_upward`/`ic_arrow_downward`, existing `action_move_up`/`action_move_down`, events `RoutinesEvent.MoveUp`/`MoveDown` already in the ViewModel) each shown only when possible (as the routine builder's row menu); dimmed offline (V-40). Retire `routines_drag_cd` and the tag `routines_drag_<id>`.
- **Scope:** the builder and "Comidas del día" keep their handles.
- **R-22 reworded in `05`:** "Reordering uses the shared component. A row with a visible `DragHandle` drags at once from the handle; a card list without handles (Rutinas) picks the card up on touch-and-hold. Both give Tick haptics on pick-up and each pass, auto-scroll near edges, the TalkBack actions 'Subir'/'Bajar', and 'Subir'/'Bajar' rows in the item's ⋮ sheet."
