# UI test and golden changes, area Entreno (input for the repair task)

Visual-first flow: each batch changes only code; the repair task fixes the tests and re-records the goldens. Each batch appends here what it breaks or leaves uncovered.

## Batch 1 (components, icons and menus)

Behaviour tests that will fail or need an update:
- `LiveSessionTypePickerTest` (line ~97): `set_type_picker_normal_icon` is gone; the "Normal" row now shows the "N" `SetTypeBadge` (`set_type_badge`) like the other types. `SetTypePickerSheet` no longer uses `ic_set_normal` (drawable kept, now unused).
- Routine menu (`routines_menu`): offline, "Duplicar", "Mover a carpeta" and "Siguiente entreno" are now listed but disabled (dimmed), not hidden; tests asserting they do not exist offline fail. Icons changed (`ic_content_copy`, `ic_drive_file_move`, `ic_flag`); folder menu "Revisar tus rutinas" uses `ic_fact_check`.
- Move-to-folder sheet is an `OptionPickerSheet`: rows are tagged `routines_move_option_none` and `routines_move_option_<folderId>` (were `routines_move_target_none|<id>`); the current folder shows a lime check and a tap on it only closes (was a disabled row). Sheet tag `routines_move_sheet` unchanged.
- Live exercise menu (`live_exercise_<n>_menu_sheet`): the title is the exercise name (string `live_exercise_menu_title` removed); new glyphs (timer, calculate, fitness_center, sticky_note_2, monitor_weight). Builder row menu: new glyphs (link, link_off, sticky_note_2, calculate, fitness_center). Tests asserting "Opciones del ejercicio" fail.
- "Añadir serie" (routine builder `routine_builder_add_set_<i>`, past-workout form `<prefix>_add_set_<i>`) and "Añadir ejercicio" (form `<prefix>_add_exercise`) are now `AddItemButton`s (SecondaryButton with a plus icon, label "Añadir serie" / "Añadir ejercicio" from `live_object_*`); strings `builder_add_set` and `builder_add_exercise` removed. Tests that look for a ghost role or tone must be re-checked.
- Exercise detail: "Ver progreso" (`exercise_detail_progress_link`) is a `NavigationRow` (chevron) instead of a `SecondaryButton`. The offline hint (`exercise_detail_offline_hint`) now shows for every exercise when offline ("Sin conexión: no se puede marcar favoritos" for non-manageable ones; unchanged "eliminar" for own exercises).
- Workout forms (`WorkoutFormBody`): "Duración" label lost "(min)"; the unit "min" is the field's trailing text and the value is start-aligned (`NumberInput.startAligned`, new, default unchanged). Date field is `surfaceControl` + `borderControl` with a trailing calendar icon when tappable. Label-based finders for "Duración (min)" need a re-check.
- `SetNoteButton`: always the `ic_sticky_note_2` glyph (tint `textTertiary` / `athleticCyan`); the pencil is gone. `RpeCell` now draws its value inside a `surfaceOverlay` rounded badge (`labelTechnical` W700). `SetRowRpeTest` assertions on text style or position may need a re-check.
- `PrimaryButton` loading: the indicator is before the label (20 dp, 8 dp gap, `colors.content`), not at the trailing edge; `ButtonsHostTest` bounds assertions on the indicator.
- `AppListRow`: minimum height 56 dp with one supporting line and 72 dp with two (`AppListRowDefaults.minHeight`); rows with no supporting line stay 48 dp. `ListComponentsTest` / `ListComponentsScreenshotTest` height assertions and goldens.
- `AppSheetHeader` (every sheet archetype) has 12 dp below the header row: all sheet goldens shift by 12 dp.
- `AppScreen` pinned footer is capped at `FormContentMaxWidth` (480 dp) even when the body is wider (picker, live tablet): a tablet golden or width assertion on a footer changes.

Uncovered (needs a new test in the repair task): one glyph per action in the routine menu, builder menu and live exercise menu; `AppListRowDefaults.minHeight`; `NumberInput.startAligned`; footer cap on a wide window; the offline hint on a non-manageable exercise detail; move sheet current-folder check.

Shared components changed (callers to re-check): `AppListRow` (all callers with supporting lines: ~20 screens), `AppSheetHeader`, `AppScreen` footer, `PrimaryButton`/`GhostButton` loading, `NumberInput`, `SetTypePickerSheet`, `SetNoteButton`, `ic_flag` and `ic_fact_check` redrawn as Material Symbols Outlined (used by the Diario goals prompt too).

Glyph verification (R-81): Material Symbols Outlined names confirmed to exist and used: `timer`, `calculate`, `fitness_center`, `drive_file_move`, `content_copy`, `flag`, `sticky_note_2`; group = `link`, ungroup = `link_off`, "Revisar tus rutinas" = `fact_check`.
