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
