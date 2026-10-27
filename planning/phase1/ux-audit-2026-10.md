# AppFitness — UX audit and consistency guardrails

> **Status (E9, ADR-69):** the rulebook, patterns and glossary of this audit were adopted into `05-design-system.md` §8–§10; the user accepted the §12 recommendations (D12, the "Entreno en curso" bar, was dropped). This file is the evidence and rationale; where it differs from `05`, `05` prevails. Sections 11 and 15 are proposals applied through the E9 workflow task.

> Expert UX audit for the E9 "UX consistency guardrails" work. Date: 2026-10-25. Code audited: `main` at 90573e3
> (v0.6.0 plus the E7 breakdown). Style (colours, fonts, visual look) is out of scope; layout, interaction, navigation,
> wording and process are in scope.

## 1. Purpose, scope and method

**Purpose.** This document:
- explains why the same UX defects keep coming back;
- gives a solution for every item reported at the checkpoints;
- lists the problems found in the code that nobody has reported yet;
- turns all of it into rules, components and checks, so a new screen cannot take "a different approach" again.

**Sources.**
- All checkpoint feedback in `tasks/todo.md` (E1–E7), the retrospectives E1–E6, ADR-01…ADR-62,
  `planning/05-design-system.md` and the E9 refinement table.
- The UI code in `composeApp/src/commonMain` (shell, `designsystem/`, every `feature/`), about 47k lines, and every
  UI string in `strings.xml`.
- The two screenshots in `design/reported/`.
- Current Android and Material 3 practice for modern apps.

**How to use it.**
- §7 is the E9 work list: every reported item, its real status today and its solution.
- §8 is the proposed text for `05-design-system.md`: a rewrite of §8, a new §9 for patterns and a new §10 for the
  glossary (Appendix A).
- §9 and §10 are the components and checks that make the rules stick.
- §11 is the proposed `ux-reviewer.md`, ready to paste.
- §12 lists the decisions only you can take.
- §14 is the deep dive on swipe-to-delete and vibration: how professional apps do it and how to build it in this
  project, step by step.
- §15 proposes the changes to the agent workflow (`CLAUDE.md`, `planning/06-agent-workflow.md` and the agent files)
  that make the guardrails part of every task.

File references are `path:line` under `composeApp/src/commonMain/kotlin/io/github/lopezjavier/appfitness/`, unless the
path says otherwise.

## 2. Executive summary

**Verdict.** Screen by screen, the app is built with care. It has accessibility semantics, state restoration, design
tokens, skeletons, and empty and error states. It feels like a patchwork for one reason. The design system provides
*atoms* (buttons, fields, a sheet container, a card) but not *patterns*. There is no screen template, no sheet template,
no shared row layout per object, no top bar that can hold actions, and no app-wide message channel. Every task combines
the atoms its own way. The rules meant to prevent this are prose spread over four documents that contradict each other.
They are checked one plan at a time, never against the screens that already exist and never after implementation. So
a convention you state at one checkpoint gets fixed on one screen and recreated differently on the next.

**The 12 most important issues** (details in §5):

| # | Issue | Evidence |
|---|---|---|
| 1 | No screen or sheet templates. | 24 bottom sheets each build their own header and footer: 2 header styles, 3 footer layouts, and the main button is sometimes below the fold. |
| 2 | The top bar cannot hold actions or contextual titles. | Actions for the object on screen live in 4 different places. Screens add an extra icon row under the bar. Titles are generic ("Ejercicio", "Rutina"). |
| 3 | Editing works differently per screen. | The meals editor deletes at once but stages everything else. Only 2 of 7 full-screen editors ask before discarding. Preferencias stays open after "Guardar". "Listo" vs "Guardar" is used ad hoc. |
| 4 | The same object is shown in different ways. | A food with a quantity has 3 row layouts and 3 different sheets. Tapping an exercise name opens its detail on some screens and does nothing on others. A routine card opens the editor, not a detail. |
| 5 | Wording collides. | "Comida" means 5 things. Two screens are titled "Historial". "Calorías" opens a screen called "Historial". Body-fat and basal-metabolism labels each have 3 variants. There are 14 separate "Guardar" strings. |
| 6 | Feedback channels are fragmented. | 15 snackbar hosts in different positions. A double "Sin conexión" banner on 4 screens. More than 6 ways to say "offline". Haptics are random. |
| 7 | Gestures do not feel modern. | Swipe-to-delete needs 90 % of the width, gives no haptics, and the row snaps back before vanishing. Drag-to-reorder needs a long press on the handle. Lists have no item animations. Full swipe and vibration guide in §14. |
| 8 | Forms add friction. | The keyboard only shows "Hecho". Name sheets do not focus the field. Numeric fields do not select their value on focus. Fields have no trailing actions (clear, ✓). Inputs use two focus colours. |
| 9 | One concept, two controls. | 2 help patterns, 2 time-range selectors, and a meal selector that changes control when a 5th meal is added. |
| 10 | Confirmation dialogs drift from the policy. | Deleting a custom exercise and archiving a routine ("Archivar") ask for confirmation. "Archivar" cannot be undone anywhere. |
| 11 | Visual regressions reach the checkpoints. | The stray horizontal line was reported 3 times. The "Objetivo" and "Peso actual" cards drift apart. |
| 12 | The review process cannot catch any of this. | ux-reviewer reads one plan in isolation. It has no inventory of decided patterns, no sweep tooling, and never looks at the implemented UI. |

**The fix, in five moves.**
1. **Patterns in code.** A small set of designsystem templates that encode the decisions, so the right thing is the
   easy thing (§9):
   - screen and editor scaffolds;
   - a top bar with actions;
   - four sheet archetypes;
   - row components per object;
   - `MetricCard`;
   - app-level messages, haptics and offline handling;
   - field upgrades.
2. **One rulebook and one glossary.** 40 testable rules, the decided answers and a Spanish glossary replace the
   scattered prose (§8, Appendix A).
3. **Guards.** A `check-ux-conventions.sh` script in CI fails when a feature reinvents a template (§10).
4. **A new ux-reviewer.** It works from:
   - a pattern map;
   - a rule checklist;
   - a mandatory sweep;
   - a "do not re-propose" list;
   - a second look at the implemented diff (§11).
5. **Migration waves in E9.** Every reported item is closed by a template, not by a one-off patch (§7, §13).

## 3. Why the same defects keep coming back (root causes)

| ID | Root cause | How it shows up |
|---|---|---|
| RC1 | **Atoms without templates.** `designsystem/` offers `AppBottomSheet`, `PrimaryButton`, `AppCard` and `AppTextField`, but no `EditSheet`, `EditorScreen`, `FoodEntryRow` or `MetricCard`. | Each of the 24 sheets draws its own title, close button and footer. Each list draws its own rows. "Objetivo" and "Peso actual" are two different card implementations, so they drift. |
| RC2 | **The shell owns the top bar, with a static title per route and no actions** (`designsystem/AppTopBar.kt:85`, `app/AppRoutes.kt:258-321`). | The live session draws its own bar. Detail screens add an icon row under the bar, or put edit and delete inside one tab. You see 4 places for "edit/delete this". |
| RC3 | **Rules are prose in four places and contradict each other.** | 05 §6.2 says Preferencias discards silently, yet 05 §6.3 says the defaults screen "asks to discard (same pattern as Preferencias)". ADR-41 deletes a meal at once, while the E4-B round 2 feedback asks for removal on "Guardar". ADR-45 allows dialogs only for account deletion, yet the app has 9 dialog call sites. With no single list of decided answers, a reviewer cannot tell whether a plan matches. |
| RC4 | **Reviews are per plan and forward-only.** | E6 created `HelpButton`, but the two E5 inline helps were never migrated. T-072 created `SwipeToDeleteRow`, but nobody fixed *where* delete lives. Nobody reviews the composables that were actually written. |
| RC5 | **No glossary.** | "Comida" covers the meal slot, the lunch slot, the saved comida, the search screen title and two different "Añadir comida" buttons. |
| RC6 | **Feedback is stored as one-off rows, not turned into rules.** | "Objetivo vs Peso actual alignment" is a table row, when the real lesson is "one card component". The E9 table records symptoms, so the same class of defect keeps coming back in new shapes. |
| RC7 | **No visual regression guard.** | Layout defects are only found by eye at checkpoints: the stray line, the card drift, the vertical labels of T-053. |

## 4. What already works (keep it)

- Design tokens and `check-design-tokens.sh`. This is exactly the model for the new UX guard.
- Accessibility discipline:
  - 48 dp touch targets;
  - `stateDescription` on rings, bars and toggles;
  - custom actions for swipe and reorder;
  - error messages announced politely by screen readers;
  - headings;
  - spoken timers throttled to 10 s steps.
- State restoration everywhere (ADR-40), including drafts and open sheets.
- Shared `SkeletonBox`, `EmptyState` and `ErrorState`, used on almost every screen.
- `HelpButton` for in-app explanations, and pickers that apply on tap (`SetTypePickerSheet`, `RpePickerSheet`).
- An explicit "Guardar" culture: edits never apply silently when a surface is closed (T-075 swept this).
- `SetRow`:
  - ghost data and planned placeholders;
  - fields per metric type;
  - keyboard "Siguiente" chaining;
  - the RPE cell.
- Two-column tablet layouts on Diario, Preferencias and Peso.
- Edge-to-edge drawing, and typed navigation with a back stack per tab.

## 5. Cross-screen findings

Priority:
- **P1:** you would see an inconsistency or confusion on a core journey, or it blocks the guardrails.
- **P2:** a clear UX improvement.
- **P3:** polish.

### 5.1 Navigation and top bar (NAV)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| NAV-1 | Actions for the object on screen live in four places. | 1. An icon row under the bar: `feature/workout/SessionDetailScreen.kt:146-185`, `feature/workout/HistoryScreen.kt:124-137`. 2. The name row of one tab only: `feature/exercises/ExerciseDetailScreen.kt:231-249`, so edit and delete vanish on "Historial" and "Cómo hacerlo". 3. A ghost button in the footer: `feature/templates/TemplateDetailsScreen.kt:145-151`. 4. A custom top bar: `feature/workout/LiveSessionTopBar.kt`. | `AppTopBar` gets an `actions` slot: at most 2 icon actions plus ⋮, which opens an action sheet. Every screen about one object puts its actions there (R-02). | P1 |
| NAV-2 | Titles are generic or wrong. | "Ejercicio" for every exercise, "Detalle del entreno", "Rutina" for both new and edit, and "Editar comida" on what US-53 turns into a details screen (`app/AppRoutes.kt:258-321`). | Detail screens show the entity name ("Press de banca con barra", "Torso fuerza · lun, 24 oct"). Editors show verb + object ("Nueva rutina", "Editar rutina"). Screens own their title (R-01). | P1 |
| NAV-3 | Tab roots repeat the bottom-tab label as a 56 dp title. Entreno adds a second heading, "Mis rutinas" (`feature/routines/RoutinesScreen.kt:170-177`). You reported the bar as too tall. | `app/AppScaffold.kt:326-341` | Tab roots carry their own compact header instead of a title bar: the date switcher on Diario, "Rutinas" with history/calendar actions on Entreno, the profile header on Perfil. This recovers 56 dp on the three most used screens (D1). | P2 |
| NAV-4 | "History of X" is reached four different ways, and labels do not match titles. | A ghost text button "Historial" (`feature/workout/WorkoutEntry.kt:136-141`); a calendar icon on Diario (`feature/diary/DiaryDateHeader.kt:62-67`); an on-screen section on Peso; rows on Perfil (`feature/profile/ProfileRootScreen.kt:193-236`). The row "Calorías" opens a screen titled "Historial". | The history or calendar of X is a calendar icon in X's top bar (R-03). A row's label equals the title of the screen it opens (R-04). | P2 |
| NAV-5 | Tapping a routine card opens the editor (`feature/routines/RoutineCard.kt:67`). | 05 §8 says a row opens its details. | Add a light routine details view (exercises, planned sets, last done, "Empezar", ✎ in the top bar), or record an explicit exception. Today it is the only list where a tap edits. | P2 |
| NAV-6 | In the "Nueva rutina" picker (select mode), tapping a row adds the exercise, and there is no way to open its detail. | T-051 Q5 | In select mode, the thumbnail (or an ⓘ) opens the detail and the row selects (R-17). | P3 |

### 5.2 Sheets and dialogs (SHT)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| SHT-1 | Sheet headers differ. | `titleLarge` + X: `feature/diary/EditEntrySheet.kt:113-128`, `feature/routines/FolderSheet.kt:48-67`, `feature/templates/TemplateFoodSheet.kt:111-129`. `titleMedium` with no X, and only some are marked as headings for screen readers: `feature/weight/WeightDaySheet.kt:98-112`, `feature/diary/DiaryDayPickerSheet.kt:63-68`. | One sheet header: title, optional "?" and a close X described as "Cerrar". | P1 |
| SHT-2 | Sheet footers differ, so the main button can end up below the fold. | Stacked and scrolling with the content: `feature/food/AddFoodSheet.kt:171-177` (after the nutrition card) and `EditEntrySheet.kt:148-161`. A pinned row with "Eliminar" and "Guardar" side by side: `WeightDaySheet.kt:182-219`. | One pinned footer: the primary action full width, and the destructive action as a text action. It is always visible. | P1 |
| SHT-3 | A stray horizontal line, reported 3 times: on the quantity "30" (E4-C), in the weight sheet (E5-B) and on "Nueva carpeta" (E6-B). | The screenshot `design/reported/e4c-line-through-quantity-field.png` shows the line across the **whole sheet width**, beyond the field, so the field border is not the cause. All three cases are a bottom sheet containing a text field. | The prime suspect is `designsystem/AppBottomSheet.kt:84-101`, which puts `.dropShadow(shape)` and a **full outline** `.border(thin, borderStrong, sheetTop)` on the `ModalBottomSheet` modifier. The outline's bottom edge can be drawn mid-sheet when the sheet is measured shorter than its scrolling, keyboard-padded content. To diagnose: (1) reproduce it with a host screenshot test of `TemplateFoodSheet`; (2) remove the border, then the shadow; (3) replace the outline with a top-only hairline (`drawBehind` at y = 0). Keep the screenshot test as the guard (D14). | P1 |
| SHT-4 | Confirmation dialogs fall outside the policy. | 9 call sites: sign out and account deletion (`feature/profile/ProfileDialogs.kt:17,30`); folder deletion and **routine archive** (`feature/routines/RoutinesSheets.kt:54-75`); discarding the live workout ×2 (`feature/workout/LiveSessionDialogs.kt:100`, `feature/workout/WorkoutEntry.kt:144`); discarding changes ×2 (`feature/routines/RoutineBuilderScreen.kt:144`, `feature/meals/MealSlotsScreen.kt:81`); **custom exercise deletion** (`feature/exercises/ExerciseDetailScreen.kt:268-284`). ADR-45 and ADR-51 allow only account and folder deletion. "Archivar" removes the routine with no way to restore it: there is no archived list. | Write the real policy down (R-08). Delete a custom exercise at once; the server already refuses one in use, with counts. Make "Archivar" an immediate "Eliminar", or build an "Archivadas" list with restore (D4). | P2 |
| SHT-5 | Menus are always bottom sheets, never dropdowns. | `RoutineSheetActions.kt`, `ExerciseMenu.kt` | Good. Formalise it as one `AppActionSheet`: icon + label rows, destructive action last. | P2 |

### 5.3 Editing and saving (EDT)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| EDT-1 | The meals editor deletes at once on the server, while adding, renaming and reordering wait for "Guardar". | `feature/meals/MealSlotsEditorViewModel.kt:162-170` | Inside an editor, every change is staged and applied by "Guardar", removals included. Immediate delete applies only outside editors (R-11, D3; amends ADR-41). The comida editor, the routine builder and the workout editor already stage removals. | P1 |
| EDT-2 | Leaving with unsaved changes behaves differently per editor. | It asks in the routine builder and the meals editor, and in the planned defaults screen. It is silent in Preferencias, the comida editor, "Editar entreno", "Registrar entreno pasado" and "Crear"/"Editar alimento". 05 §6.2 and §6.3 contradict each other. | One rule (R-14, D2). A full-screen editor with unsaved changes asks "¿Descartar los cambios?" [Descartar] [Seguir editando]. With no changes, it just leaves. Sheets discard silently. Implement it once, in `AppEditorScreen`. | P1 |
| EDT-3 | Preferencias stays open after "Guardar". | `feature/profile/PreferencesScreen.kt:122-142` | Every editor closes after a successful save (R-13). Confirm with a haptic; show a message only when the change is not visible where you land. | P2 |
| EDT-4 | "Listo" and "Guardar" are mixed with no visible rule. | `template_food_save` = "Listo" (`TemplateFoodSheet.kt:93-98`); "Listo" in the Preferencias pickers. | Encode it in the sheet type (R-12). `commit = Save` shows "Guardar" and writes to the server. `commit = Apply` shows "Listo" and fills the unsaved changes of the screen underneath, which has its own "Guardar". Pickers apply on tap. | P2 |
| EDT-5 | Renaming uses four patterns. | An inline field in the row (meals, `feature/meals/MealSlotRow.kt:121-135`); a sheet (folders); a name field in an editor (comida, routine, workout); a form sheet (custom exercise). | Objects with their own editor keep the name field there. Named items without an editor (meals, folders, "Guardar como comida", "Guardar como rutina") use one **Name sheet**: one field that receives focus at once, the keyboard's "Hecho" saves, and the uniqueness check shows inline (R-15, D6). This closes meals-editor items 20, 22 and 25 together. | P2 |

### 5.4 Lists, rows, add and delete (LST)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| LST-1 | A food with a quantity has 3 row layouts and 3 sheets. | Rows: `feature/diary/DiaryEntryRow.kt` (name, amount, macros, kcal on the right) vs `feature/templates/TemplateDetailsScreen.kt:197-250` (name, portion, amount on the right, kcal and macros below) vs the search row. Sheets: `AddFoodSheet` (per-100 card, micronutrients, quantity, meal), `EditEntrySheet` (quantity and meal, no nutrition details), `TemplateFoodSheet` (quantity, micronutrients, "Listo"). | One `FoodEntryRow` for Diario, the comida editor, comida details and defaults. One **FoodSheet**: the same header (name, brand, ★, ✎ for manual foods), the quantity first, live totals, details (per 100, micronutrients) collapsed, and a footer that depends on context ("Añadir"; "Guardar" + "Eliminar"; or "Listo" + "Quitar de la comida"). This also delivers "tapping a logged food opens its details" (item 12). | P1 |
| LST-2 | Tapping an exercise name does different things per screen. | In the live card, the name opens the detail (`feature/workout/LiveExerciseCard.kt:106-135`). In the workout detail, the name does nothing and a separate "Ver progreso" ghost button is needed (`SessionDetailScreen.kt:257-295`). | A food or exercise name always opens its details (R-18). "Ver progreso" moves into the exercise detail, which already links to progress. One `ExerciseRow` (thumbnail, name, subtitle). | P1 |
| LST-3 | "Add" has four styles. | Ghost "Añadir ejercicio" (builder) and secondary "Añadir ejercicio" (live); secondary "Añadir a la cena" (Diario) and "Añadir comida" (meals); "+" in the totals row (comida editor, `TemplateDetailsScreen.kt:180-186`); "+" in the section (circumferences, `feature/weight/CircumferenceSection.kt:116`); "+" next to the search field (create food). | Read together, your feedback gives a two-level rule. T-067 #7 asked for "a single 'Añadir comida' action, no '+'" for the list, and T-069 asked for the per-row "Añadir" to become a "+". So: **actions on a row are icons; adding a new item to a list is one labelled `AddItemButton` at the end of the list** (R-19). Creating from the search screen is not a list add: it goes into the quick-actions row (§6.2). | P1 |
| LST-4 | No rule says *where* delete lives. | Swipe (diary entries, comida foods, live sets); a trash icon in the row (meals, Health Connect readings, folders); a ghost button in the footer (entry, weight, comida); an icon at the top (workout detail, exercise detail). | R-20 decides by object type. A record in a list: swipe, plus the same action in its sheet or details. A configuration row (meals, folders, plates): a trash icon in the row. The object on screen: the top bar. Never "Deshacer". | P1 |
| LST-5 | Swipe-to-delete does not feel modern. | `designsystem/SwipeToDeleteRow.kt:39` needs 90 % of the width. At `:61-67` the swipe is always rejected, so the row snaps back and only vanishes when the data reloads. No haptics, no exit animation, and the shared component uses the diary string `diary_entry_delete`. | Modern behaviour (R-21, D5): the background shows "Eliminar" from the first pixels. Past an **arming threshold** (about 50 % of the width), the icon grows, the background intensifies and you feel a haptic tick; dragging back disarms it with another tick. Releasing while armed slides the row out, fires a Confirm haptic, and the list closes the gap (`animateItem`). A fling deletes only if the row is already armed. This keeps your intent that a short or accidental drag never deletes, while reading like Gmail or Google Tasks. Full specification, vibration design and implementation steps: §14. | P1 |
| LST-6 | Reordering needs a long press on the handle. | `designsystem/ReorderableColumn.kt:148` (`detectDragGesturesAfterLongPress`); no haptics; the list does not scroll by itself near the edges. | The handle starts dragging at once; the long press stays for the rest of the row. Haptics on pick-up and on each swap; auto-scroll near the edges. Keep "Subir"/"Bajar" as TalkBack custom actions (R-22). | P2 |
| LST-7 | Nothing in the lists animates. | `animateItem()` is not used anywhere. The Diario meals sit in a plain `Column` (`feature/diary/DiaryScreen.kt:116-125`). | Lists are lazy and animate insertions, removals and moves (R-23). This is half of why deleting feels abrupt. | P2 |
| LST-8 | The same macro shows with different precision on one screen. | Rows show 1 decimal ("C 17,7 g", `amountText`); meal headers and bars show whole numbers ("C 18 g", `wholeHalfUp`), so the sums look wrong. | Macro grams are whole numbers everywhere except nutrition tables (per 100 g, micronutrients) (R-38). | P3 |

### 5.5 Forms and keyboard (FRM)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| FRM-1 | `AppTextField` cannot do what forms need. | The keyboard action is fixed to Done, with no capitalization (`designsystem/AppTextField.kt:135`) and no trailing slot. The clear "×" is therefore overlaid with a `Box` workaround, duplicated in `feature/food/FoodSearchScreen.kt:162-181` and `feature/exercises/ExercisePickerScreen.kt:206-213`. The "✓ inside the field" you asked for is impossible. | `AppTextField` v2: a `trailing` slot (clear, ✓, unit), `imeAction` + `onImeAction`, `capitalization = Sentences` for names, and `autofocus`. Plus `AppSearchField`: search icon, clear, keyboard "Buscar" (R-24, R-26). | P1 |
| FRM-2 | Long forms close the keyboard after every field. | In "Crear"/"Editar alimento" every `NumberInput` uses Done; `SetRow` and the workout form do chain "Siguiente". | "Siguiente" between fields and "Hecho" on the last one (R-24). | P2 |
| FRM-3 | One-field sheets need extra taps. | "Nueva carpeta", "Guardar como comida" and "Guardar como rutina" do not focus the field, and "Hecho" does not save. | Name sheets focus the field at once, and "Hecho" saves when the name is valid (R-15). | P2 |
| FRM-4 | Numeric fields put the cursor at the end. | `designsystem/NumberInput.kt:170-171` | On focus, **select the whole value**, so typing replaces it (grams, kg, reps, oil amounts). This is the biggest speed-up for logging food and sets (R-25). | P2 |
| FRM-5 | Inputs look and behave differently. | The focus border is cyan in `AppTextField` (`:62`) and lime in `NumberInput` (`:124`). Labels: UPPERCASE in the small technical style in `NumberInput` (`:190-195`, "CANTIDAD (G)"), sentence-case body text in `AppTextField` (`:110-115`), and a third custom label in "Crear alimento" (`feature/food/CreateFoodScreen.kt:250-266`). | One focus treatment and one label style for every input (R-24). Pick one: this is about consistency, not taste. | P2 |
| FRM-6 | Validation copy and limits vary. | "Escribe un nombre" / "Este campo es obligatorio" / "Escribe tu nombre"; length limits of 50, 60 and 100 with three phrasings; four ways to word a range; "Introduce una cantidad entre 0 y 5000", where 0 is not valid. | Message templates (R-27, Appendix A). | P3 |

### 5.6 Feedback, system states and motion (FBK)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| FBK-1 | 15 screens have their own `SnackbarHost`, placed differently: bottom-centre with a margin, in a column above the footer, or unaligned. | Diario, food search, Rutinas, builder, live session, "Editar"/"Registrar entreno", workout detail, Preferencias, Perfil, Peso, exercise picker and detail, onboarding, Entreno. A message sent just before leaving a screen is lost, which is why the `ExerciseDeletedNotice` workaround exists (`feature/exercises/ExerciseDetailScreen.kt:97`). | One app-level message host in the shell, above the bottom bar and any pinned footer, fed by `AppMessages.show(...)`. Messages survive navigation and never carry action buttons (R-28). | P1 |
| FBK-2 | Offline is shown twice, and phrased many ways. | The shell banner (`app/AppScaffold.kt:178-180`) plus a second banner inside Peso (`feature/weight/WeightScreen.kt:152-154`), the comida editor (`TemplateDetailsScreen.kt:113`), the meals editor (`MealSlotsScreen.kt:125-131`) and the routine builder (`RoutineBuilderScreen.kt:248-253`). More than 6 phrasings, some in error red. When a screen fails to load while offline, it offers "Reintentar", which cannot work. | Only the shell banner. A write action disabled offline shows one standard hint under it ("Sin conexión: no se puede guardar"). A load that fails offline shows `OfflineState`, which retries by itself when the connection returns (R-30). | P1 |
| FBK-3 | Haptics are random, and sometimes doubled. | Present: set completion, the Preferencias save, adding and editing a diary entry, saving a comida, the comida editor, onboarding, the end of a rest. Absent: weight, circumferences, routine builder, meals, folders, workout forms, swipe, drag. Two mechanisms coexist: Compose haptics, and raw `Vibrator` pulses in `platform/AndroidHaptics.kt`. Completing a set vibrates twice: the checkbox fires `Confirm` on the tap, and `LiveSessionViewModel.kt:690` adds a raw 15 ms pulse. | One semantic `AppHaptics` (§14.4) and one map (R-29): **Confirm** (saved, added, deleted, set completed), **Reject** (refused or failed), **Armed/Disarmed** (a gesture crosses its threshold), **Tick** (a stepper or reorder step). One action, one vibration. | P1 |
| FBK-4 | Two help patterns. | `HelpButton` (a sheet) in 8 places, vs help text that expands in place on Peso: the 7-day average (`feature/weight/WeightChart.kt:209-246`) and the measuring instructions (`feature/weight/CircumferenceSheet.kt:141-164`). | Help is always `HelpButton` (R-32). Let it take structured content (paragraphs and a list). That also covers item 37, "RPE as a list", and the step-by-step measuring texts. | P2 |
| FBK-5 | Icon-only buttons give no hint of what they do. | No tooltips (`TooltipBox`/`PlainTooltip`) anywhere in the code. | You prefer icons for actions, so every `AppIconButton` shows its label on long press (the M3 tooltip). The icons stay and their meaning becomes discoverable. | P3 |

### 5.7 Selection and view controls (SEL)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| SEL-1 | One concept, the chart time range, has two controls and three string sets. | A segmented control with 1M/3M/6M/1A on exercise progress (`feature/analytics/ExerciseProgressScreen.kt:171`) vs chips with 1S/1M/3M/6M/1A on the weight chart (`feature/weight/WeightChart.kt:176`). Strings `timeframe_*`, `exercise_progress_range_*` and `weight_chart_filter_*`. | One `TimeRangeSelector` with one string set (R-34). | P2 |
| SEL-2 | The meal selector changes control type with the number of meals. | `feature/food/AddFoodSheet.kt:333-337`: a segmented control up to 4 meals, chips from 5. | Always the same control: single-select chips (R-33). | P2 |
| SEL-3 | Views that work like tabs are shown as filter chips, and two chip rows are stacked. | Food search "Recientes / Más comidos / Favoritos / Comidas" (`FoodSearchScreen.kt:186-199`); on the weight chart, metric chips sit above range chips (screenshot E5-B round 3). | Views are tabs (`AppTabRow`, scrollable). On a chart, the metric is chosen from the title and the range from a segmented control (R-33). | P2 |
| SEL-4 | No rule says which control to use. | `AppTabRow` is used only by the exercise detail. | Tabs switch between sibling views of one screen. A segmented control picks one of 2–5 short options (a setting, a view mode, a range). Chips filter a list or pick among many or long options (R-33). | P3 |

### 5.8 Wording (WRD)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| WRD-1 | "Comida" is overloaded. | 1. The meal slot (Desayuno…). 2. The lunch slot "Comida". 3. The saved comida (ADR-42). 4. The search title "Añadir comida". 5. "Añadir comida" in the meals editor (adds a slot). 6. "Añadir comida" on the defaults screen (assigns a saved comida). 7. The add-sheet field "Comida" (the slot). 8. "Nombre de la comida" for both a slot and a saved comida. | Glossary (Appendix A, D7): "Comidas del día" for the structure, "Comidas guardadas" for templates, and the search title equal to the button that opened it ("Añadir al desayuno"). | P1 |
| WRD-2 | One concept drifts across several labels. | Body fat: "Grasa (%)", "Grasa", "Grasa corporal". Basal metabolism: "TMB", "Metabolismo basal", "Tasa metabólica basal". Bone: "Masa ósea" and "Hueso". Two screens titled "Historial". "Registrar entreno pasado" is both a screen title and a chooser title. | One label per concept (R-35, Appendix A). | P1 |
| WRD-3 | Generic actions are copied per screen. | "Guardar" ×14, "Cancelar" ×8, "Cerrar" ×6, "Eliminar" ×6, "Listo" ×4, "Descartar" ×3. | Shared `action_*` strings (R-36), enforced by a guard (G9). | P2 |
| WRD-4 | Small inconsistencies everywhere. | "¿Descartar los cambios?" vs "¿Descartar cambios?". Close buttons described as "Cerrar", "Cerrar sin guardar" or "Cerrar sin mover". 4+ date formats. "Sincronizando..." vs "…". Final periods vary. "Distribución de Macros" capitalised like an English title. The leftover "No tienes ninguna comida activa", although meals can no longer be disabled (ADR-41). | Templates and punctuation rules (R-37), `AppDateFormat` (R-38). | P2 |

### 5.9 Ergonomics and accessibility (A11Y)

| ID | Finding | Evidence | Recommendation | P |
|---|---|---|---|---|
| A11Y-1 | The rest timer and its "+30s" and "Omitir" buttons sit at the **top** of the live session. | `feature/workout/LiveSessionScreen.kt:206-222,249`. Design principle 4 puts primary actions in the lower 60 % of the screen. | Pin the rest bar to the bottom, above the system bar (D10). You use it between every set, one-handed and sweaty. | P2 |
| A11Y-2 | The "Peso actual" card nests two tap targets: the card opens the body composition, and a pencil on top of it edits today's weight. | `feature/weight/WeightScreen.kt:312-345` | Keep the behaviour, but build it with `MetricCard`'s trailing action, so the pencil aligns like on every other card (item 24). | P3 |
| A11Y-3 | Reorderable rows offer "Subir"/"Bajar" both as visible arrows and as TalkBack actions. | `MealSlotRow.kt:137-187` | Fine for TalkBack. Once dragging is immediate (LST-6), the arrows could become optional (your call; see D6). | P3 |

## 6. Screen-by-screen notes

Short notes; the IDs point to §5. "→" marks the recommendation.

**6.1 Diario**
- The date header is the real header; the "Diario" title bar repeats the tab (NAV-3). → The date switcher becomes the
  screen header.
- The day picker marks no days (`feature/diary/DiaryDayPickerSheet.kt:71`, `markedDays = emptySet()`) and offers no
  way back to today. → Mark the logged days, and show a "Hoy" chip whenever the visible day is not today.
- Do not add horizontal swipe between days: it would fight swipe-to-delete on the rows.
- There are four full-width "Añadir a …" buttons plus a fifth "Añadir comida" (`DiaryScreen.kt:149-157`,
  `MealSection.kt:136-140`). → Remove the fifth (item 9). Each meal keeps one `AddItemButton` (LST-3).
- The meal header combines collapse, "save as comida" and add. → Fine, but use the standard icon colour and
  descriptions.
- Rows: a tap opens the FoodSheet (LST-1); swipe deletes with the new feel (LST-5). Fix the macro precision (LST-8)
  and add the portion label "1 vaso (200 ml)" (item 33).
- Layout: meals first, plus a compact summary strip (D11).

**6.2 Añadir (food search) and the FoodSheet**
- The title says "Añadir comida" although you came from "Añadir a la cena" (WRD-1). → The title names the destination
  meal.
- The search row holds the field, the scanner and a "+" to create a food. E7 plans a camera icon *below* the scanner
  (05 §6.3), which breaks the row. → One **quick-actions row** under the field: "Escanear", "Foto", "Crear alimento",
  each with an icon and a short label. These are ways to create, not generic actions, so a label saves guessing.
- The clear "×" workaround (FRM-1).
- The view chips should be tabs (SEL-3).
- An offline error offers "Reintentar" (FBK-2).
- AddFoodSheet puts the quantity below the per-100 card, and "Añadir" scrolls off screen (SHT-2). → Quantity first,
  live totals, details collapsed, "Añadir" pinned (LST-1).
- The meal selector changes type (SEL-2).

**6.3 Comidas guardadas (templates)**
- Details vs editing (US-53, T-104): build the details screen on the shared templates:
  - the title is the comida's name;
  - ✎ "Editar" and 🗑 in the top bar;
  - a totals card;
  - a `SectionHeader` "Alimentos en esta comida" (item 28);
  - the items as `FoodEntryRow`s;
  - "Añadir al <comida>" pinned.
- Editor:
  - the name field;
  - items removed by swipe, applied on "Guardar";
  - an `AddItemButton` "Añadir alimento" at the end of the list, instead of the "+" in the totals row;
  - "Guardar" pinned;
  - the discard rule (EDT-2).
- Duplicate offline banner (FBK-2).

**6.4 Comidas del día (meals editor)**
- Deletes at once inside the editor (EDT-1).
- Inline rename with no ✓ (EDT-5, items 20, 22 and 25).
- Names must be unique (item 21).
- Duplicate offline banner (FBK-2).
- Dragging needs a long press (LST-6).
- E7 plans "Comidas por defecto" as a `SecondaryButton` inside this editor, disabled while there are unsaved changes.
  → Make it a `NavigationRow` at the end ("Comidas por defecto ›"). It still blocks while there are unsaved changes, and
  then it says why.

**6.5 Perfil (root)**
- The big lime "Preferencias" button acts as the screen's main action, with an empty band above it (item 7).
- "Cerrar sesión" and "Borrar cuenta" sit on the main profile screen.
- The "Estadísticas" rows show no values.
- → A gear icon in the profile header opens Preferencias (D8).
- → The rows become `NavigationRow`s with the current value ("Peso · 79,4 kg", "Fuerza · Récord 1RM 113,5 kg",
  "Calorías · media 1.950 kcal").
- → The account actions (sign out, delete, the protected-account note) move to Preferencias → "Cuenta".
- → "Acerca de" (US-45) is another `NavigationRow`.
- The entry label "Calorías" must equal the destination title (NAV-4).

**6.6 Preferencias**
- One long form with a single "Guardar" mixes:
  - personal data;
  - energy;
  - macros;
  - a row that navigates to the meals editor;
  - training;
  - calculators.

  E7 adds "Cocina".
- It does not close after saving (EDT-3), and back silently discards everything (EDT-2); see items 5, 6 and 18.
- → D9: turn it into a settings **hub** of grouped `NavigationRow`s, each opening a focused editor with its own
  "Guardar" that returns to the hub:
  - "Datos personales";
  - "Objetivo calórico";
  - "Macros";
  - "Comidas del día";
  - "Cocina";
  - "Entrenamiento";
  - "Cuenta".

  This is how Android Settings and modern apps scale. It removes the ordering question (item 18) and keeps every save
  small and explicit. It amends ADR-24 (one save for the whole screen). If you keep the single form, apply at least
  R-13 and R-14.

**6.7 Peso**
- A very long single column: "Peso actual", "Objetivo", the IMC and body-fat tiles, the Health Connect card,
  "Tendencia", "Historial de mediciones" (a calendar) and "Circunferencias".
- A duplicate offline banner.
- Body fat appears twice: "Grasa corporal: 18,0 %" in "Peso actual" and again in its own tile.
- → `MetricCard` for "Peso actual", "Objetivo" and the tiles (item 24).
- → The Health Connect status moves into "Peso actual" as a supporting line, with "Sincronizar" as the card's action
  (item 30). Today it is a `GhostButton` in its own card.
- → The measurement calendar becomes a calendar icon in the top bar (item 31).
- → The chart picks the metric from its title and the range from a segmented control (SEL-3), and adds the goal
  overlay (item 32).
- → The inline helps become `HelpButton`s (FBK-4).
- → Remove the repeated body fat from "Peso actual"; the tile already shows it.

**6.8 Entreno (Rutinas)**
- Double heading (NAV-3).
- "Historial" is a ghost button (NAV-4).
- Tapping a card opens the editor (NAV-5).
- "Archivar" asks for confirmation and cannot be undone (SHT-4).
- The folder header shows ✎ and 🗑: fine as configuration rows.
- The in-progress card (play and trash) exists only on this tab. → See D12.

**6.9 Rutina (builder)**
- Generic title "Rutina" (NAV-2).
- Duplicate offline banner (FBK-2).
- "Añadir ejercicio" is a ghost button here but a secondary button in the live session (LST-3).
- The "Actualizar automáticamente" switch is per routine, while you want a global setting (open question E6-B Q1). →
  Switches that affect one object live in its editor; behaviour that applies to everything lives in Preferencias.
  Option A (global only) is the simpler, consistent choice.

**6.10 Entreno en vivo**
- Custom top bar: fine, but build it from the `AppTopBar` v2 actions.
- The rest bar sits at the top (A11Y-1).
- The name in the live card opens the exercise detail. That is correct; the rest of the app should do the same
  (LST-2).
- "Añadir serie" and "Añadir ejercicio" use different button styles (LST-3).
- Rest-end vibration and sound: items 15 and 16.
- The "N" badge for normal sets and the placeholder contrast are visual (items 35 and 36).

**6.11 Historial, workout detail, calendar and workout forms**
- Historial: an icon row under the bar for "+" and the calendar (NAV-1). → Move them to the top bar.
- Workout detail:
  - an icon row (NAV-1);
  - the exercise names cannot be tapped (LST-2);
  - an outdated code comment ("Eliminar sesión" at the end of the list) while the code puts the icon at the top.
- "Editar entreno" and "Registrar entreno pasado" discard silently (EDT-2).
- "Registrar entreno pasado" is both the screen title and the chooser title (WRD-2). → Chooser "¿Desde dónde
  empiezas?", screen "Entreno pasado".

**6.12 Ejercicios (picker and detail)**
- The picker creates an exercise with a full-width `PrimaryButton`, while foods use a "+" icon (LST-3). → Both use the
  quick-actions pattern.
- Detail:
  - edit and delete appear only on the "Resumen" tab (NAV-1);
  - deleting asks for confirmation (SHT-4);
  - the `ExerciseDeletedNotice` workaround (FBK-1).

**6.13 Fuerza, Volumen and Progreso**
- The range selector (SEL-1).
- Exercise chips: fine as filters.
- "Ver progreso" links → Keep them inside the exercise detail only.

**6.14 Welcome and onboarding**
- Items 1 and 2 (from E2-A) are mostly visual.
- The UX part:
  - one step template (title, helper text, content, "Atrás" and "Continuar" pinned at the bottom);
  - a horizontal slide forward and back between steps;
  - "Sexo" as two equal cards side by side, with the birth date in the same view.

## 7. Every reported item: status today and solution

The status was checked in the code at 90573e3. "Drop" means remove the row from the E9 table: it is done, obsolete
or superseded.

### 7.1 E9 refinement table

| # | Item (source) | Status today | Solution |
|---|---|---|---|
| 1 | Onboarding polish: spacing, step layout, transitions (E2-A) | Open | Step template and a slide transition. Spacing is visual and belongs to the UI pass. |
| 2 | Onboarding "Sexo": empty space (E2-A) | Open | Two equal `SelectableCard`s side by side, with the birth date in the same view. |
| 3 | Typography review (E2-A) | Open; visual | Out of scope here. The templates fix the title *levels*: one style each for screen titles, section headers and sheet titles. |
| 4 | Tablet: move the primary action out of the rail (E1) | **Obsolete**: ADR-36 removed the floating action button, and nothing uses the rail's header slot (`app/AppScaffold.kt:206-220`). | Drop. |
| 5 | Preferencias: remove the "Calorías que quemas…" helper (E2-B) | Open | Delete `energy_tdee_helper` from both screens. |
| 6 | Preferencias: "Guardar" closes the screen (E2-B) | Open (`PreferencesScreen.kt:122-142`) | R-13, through `AppEditorScreen`. |
| 7 | Perfil: gear or pencil next to the name (E2-B) | Open | D8: a gear in the profile header; the account actions move into Preferencias. |
| 8 | Diario: remove "restantes" and the macro % (E4-A) | Open | The ring shows only what you ate and "de N kcal", plus "+N kcal en exceso" when you go over. `MacroProgressBar` reads "9 g / 233 g". |
| 9 | Diario: remove the full-width "Añadir comida" (E4-A) | Open (`DiaryScreen.kt:149-157`) | Remove it; each meal keeps its own `AddItemButton`. |
| 10 | Diario: meals first, ring at the bottom (E4-A) | Open | D11: meals first and the full dashboard at the bottom. A one-line summary strip on top ("1.240 / 2.100 kcal · P 80 · C 150 · G 40") keeps the remaining budget visible without scrolling. |
| 11 | Diario: P/C/G for each food (E4-A) | **Done** (`DiaryEntryRow.kt:103-107`) | Drop; fix the precision (LST-8). |
| 12 | Diario: tapping a logged food opens its details (E4-A) | Open: the tap opens the edit sheet, which has no nutrition details | One FoodSheet (LST-1). |
| 13 | Diario: "Almuerzo" as a default meal (E4-A) | **Resolved** in `09-open-questions.md`: you create it yourself | Drop. |
| 14 | Diario: calendar button next to the day (E4-A) | **Done** (T-071, `DiaryDateHeader.kt:62-67`) | Drop. Improvement: mark the logged days and add a "Hoy" chip (§6.1). |
| 15 | End of rest: the vibration is too short (T-059) | Open | A longer pattern through the `Haptics` port, for example three strong pulses over about 1.2 s, tested on your phone (§14.4). Treat it as a functional fix. |
| 16 | End of rest: the sound is odd (T-059) | Open | You choose among 2–3 short candidates: the system notification sound, a soft chime or a double beep. Still silent when the phone is muted. |
| 17 | Live session: remove "Pantalla activa" (user report) | **Done**: `LiveSessionScreenTest` asserts it is gone | Drop. |
| 18 | Preferencias: meals below macros (E4-B round 2) | Open (`PreferencesScreen.kt:201-216`) | Reorder, or moot with the hub (D9). |
| 19 | Meals editor: a removal is saved by "Guardar" (E4-B round 2) | Open (`MealSlotsEditorViewModel.kt:162-170`) | R-11 (everything inside an editor waits for "Guardar"), amending ADR-41 (D3). The server still checks "in use" and "last meal" on save, and the row shows the hint beforehand. |
| 20 | Meals editor: a ✓ inside the name field (E4-B round 2) | Open | Name sheet (R-15, D6). If you keep inline rows, use the ✓ in the `AppTextField` v2 trailing slot. |
| 21 | Meals editor: unique names, ignoring case (E4-B round 2) | Open (folders already do it, T-101a) | One rule for every collection you name: meals, folders, saved comidas, routines. One message template: "Ya existe <una comida / una carpeta…> con ese nombre". |
| 22 | Meals editor: "Añadir comida" disabled while a meal is being created (E4-B round 2) | Open | Moot with the Name sheet, which blocks the screen behind it. |
| 23 | Top bar too tall (E4-C round 1) | Open | D1: tab roots without a title bar, compact screen headers, entity titles. |
| 24 | Peso: "Objetivo" and "Peso actual" drift apart (E5-B round 3) | Open: two implementations (`WeightScreen.kt:312-345`, `designsystem/GoalCard.kt`) | One `MetricCard` (§9); `GoalCard` becomes a `MetricCard` with a progress slot. |
| 25 | Meals editor: ✓ and ✕ for a meal being created (E4-B round 2) | Open | Name sheet (D6). |
| 26 | Swipe like modern apps, with haptics (E4-C round 3) | Open (`SwipeToDeleteRow.kt`) | R-21 and D5 (LST-5). The full specification, vibration design and implementation steps are in §14. |
| 27 | Stray line across text fields (E4-C round 3) | Open | SHT-3: diagnose with a screenshot test, then use a top-only hairline. |
| 28 | Comida details: a divider "Alimentos en esta comida" (E4-C) | Open | `SectionHeader` (§9). |
| 29 | Peso: a pencil replaces "Registrar peso de hoy" (E5-B) | **Done** (ADR-48, T-087, `WeightScreen.kt:273-286`) | Drop. |
| 30 | Peso: Health Connect status inside "Peso actual", and "Sincronizar" as a real button (E5-B) | Partly done: "Sincronizar" is a `GhostButton` in a separate Health Connect card | The status becomes a supporting line of the "Peso actual" `MetricCard`, with "Sincronizar" as its action. |
| 31 | Peso: measurement history opened from a calendar button (E5-B) | Open | R-03: a calendar icon in the top bar. |
| 32 | Weight trend: show goal progress visually (E5-B) | Open | A chart overlay: a marker on the first weight, the target line (already there), and a shaded band between them. |
| 33 | Diario: "1 vaso (200 ml)" (user, 2026-10-22) | Open | `FoodEntryRow` shows the portion label plus the amount in base units. |
| 34 | Comidas: a row tap adds it, a pencil edits it (user, 2026-10-23) | **Superseded** by ADR-62 / US-53 (T-104) | Drop. |
| 35 | Normal set: an "N" instead of the number (E6-A) | Open; visual | `SetTypeBadge` everywhere: builder, live session, history, detail. |
| 36 | Live session: planned placeholders darker (E6-A) | Open; visual | Planned values use `textSecondary` today, chosen for contrast. Make them clearly different from typed values without relying on colour alone (for example a lighter font weight), or switch to `ghostData` if you accept lower contrast for planned values. |
| 37 | RPE help as a list (E6-A) | Open | `HelpButton` with structured content (FBK-4). |
| 38 | Folders: stray line on "Nueva carpeta" (E6-B) | Open | Same as 27. |

### 7.2 UX consistency guardrail bullets (E4 and E5 retrospectives)

| Item | Solution |
|---|---|
| Icons vs text for add and remove | The two-level rule R-19 (LST-3), derived from T-067 #7 and T-069. |
| No "Deshacer" after swipe-delete | Decided (ADR-45, T-066, E6 Q2). It is on the "do not re-propose" list, and guard G10 fails if the word appears in `strings.xml`. |
| Tapping a food or exercise opens its detail | R-17 and R-18. Current violations: NAV-5 (routine card), LST-2 (workout detail), and the diary row opening an editor without details (LST-1). |
| Consolidate the conventions into 05 | §8 is the proposed text. |
| Update ux-reviewer | §11, ready to paste. |
| Cross-screen consistency audit | §5 and §6. |
| Success criterion: no new screen takes a different approach | Templates (§9), guards (§10), plus the pattern map, mandatory sweep and post-implementation review (§11). |
| E5 retro: "every new UI follows a different approach" | Root causes RC1–RC4 and the same three measures. |
| E6-B Q1: where "Actualizar automáticamente" lives (open) | Settings that apply to everything live in Preferencias; switches that affect one object live in its editor. Option A (global only) matches what you asked for. |

## 8. The rulebook (proposed text for `05-design-system.md`)

These rules replace today's §8 and add §9 (patterns) and §10 (glossary, Appendix A). Each rule is short and testable.
ux-reviewer cites them by number.

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
- **R-10** Every edit surface has an explicit "Guardar". Closing it (X, back, swipe, tapping outside) never applies
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
- **R-32 Help** is always `HelpButton`, never text that expands in place.

### Selection controls
- **R-33** Each control has one use:
  - **Tabs** (`AppTabRow`) switch between sibling views of one screen.
  - **A segmented control** (`SegmentedSelector`) picks one of 2–5 short options: a setting, a view mode, a range.
  - **Chips** filter a list or pick among many or long options.

  A control never changes type with the number of options.
- **R-34** The same concept always uses the same control and the same labels. Chart ranges use `TimeRangeSelector`:
  "1S 1M 3M 6M 1A".

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

## 9. Design-system additions (component specs)

Each component gets a catalog entry, host tests and a short spec in 05 §6. Together they make the rules the default.

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

## 10. Guards (checks that make the rules stick)

Add `scripts/check-ux-conventions.sh`, modelled on `check-design-tokens.sh`. It scans production Kotlin outside
`designsystem/`, exits with 1 on a match, and runs in `android-ci`.

| Guard | Fails when |
|---|---|
| G1 | `ModalBottomSheet(` appears outside `designsystem/AppBottomSheet.kt`. |
| G2 | Feature code calls `AppBottomSheet(` directly instead of an archetype (once Wave 1 has migrated the sheets). |
| G3 | `SnackbarHost(` appears outside `app/`. |
| G4 | `OfflineBanner(` appears outside `app/AppScaffold.kt` and the catalog. |
| G5 | `AppConfirmDialog(` appears outside an allow-list: account deletion, sign out, folder deletion, discarding the live workout, `AppEditorScreen`. |
| G6 | `ic_help` is used outside `designsystem/HelpButton.kt`. |
| G7 | `SwipeToDismissBox` is used outside `designsystem/SwipeToDeleteRow.kt`. |
| G8 | `detectDragGestures` is used outside `designsystem/ReorderableColumn.kt`. |
| G9 | A string outside the `action_*` keys has exactly the value of a generic action (Guardar, Cancelar, Cerrar, Eliminar, Listo, Descartar, Reintentar, Editar). |
| G10 | `strings.xml` contains "..." or the word "Deshacer". |

Optional (D14): Roborazzi screenshot tests of the templates (sheet header and footer, `MetricCard`, the rows), run
with the existing Robolectric host tests. They catch the stray-line and card-drift kind of bug before a checkpoint,
without sending you any screenshots.

## 11. Proposed `ux-reviewer.md` (ready to paste)

The `model:` line stays your decision. The text assumes §8 has been moved into `05-design-system.md` §8–§10.

```markdown
---
name: ux-reviewer
description: Reviews the plan of a UI task against the AppFitness UX rulebook (05 §8–§10), the existing screens and the design-system templates, and defines the testTags. Use it after the planner and only on tasks with UI.
tools: Read, Grep, Glob, Edit
model: sonnet
---
You are the **UX** reviewer for AppFitness. Your job is consistency: every new UI must look and behave like the screens that already exist and follow the rulebook. Most rejected work in this project came from a plan that solved an already-solved problem in a new way.

## Read
1. `.workflow/current_task.md` (the plan).
2. `planning/05-design-system.md`: §6 components, §8 rules R-01…R-40 and "Decided — do not re-propose", §9 patterns, §10 glossary.
3. The designsystem templates the plan touches (`composeApp/**/designsystem/`), and the existing screen most similar to the new UI (find it with Grep).

## Procedure
1. **Pattern map.** For every UI element in the plan, write one row: element → template or component (AppScreen, AppEditorScreen, the four sheet archetypes, Name sheet, FoodSheet, rows, MetricCard, SectionHeader, AddItemButton, TimeRangeSelector…) → the existing file that already uses it → OK or new. A new pattern needs a reason; prefer extending a template.
2. **Rules.** Check every applicable rule R-01…R-40 and mark each ✓ or ✗ with the fix. Always check:
   - titles and top-bar actions (R-01…R-04);
   - sheet type and pinned footer (R-07, R-09);
   - dialogs (R-08);
   - the editing rules (R-10…R-15);
   - rows, tap → details, where add and delete live, swipe and reorder (R-16…R-23);
   - forms (R-24…R-27);
   - messages, haptics, offline and states (R-28…R-32);
   - controls (R-33, R-34).
3. **Sweep (mandatory).** For every pattern the task adds or changes, grep the app for its other instances:
   - the template calls, e.g. `AppBottomSheet\(|AppConfirmDialog\(|SnackbarHost\(|OfflineBanner\(|SwipeToDeleteRow\(|FilterChipRow\(|SegmentedSelector\(` in `composeApp/src/commonMain`;
   - the name of the old component;
   - the Spanish label in `strings.xml`.

   List them in the UX notes. The plan migrates each one, or it goes into the E9 refinement table with an owner. A new component retires its ad-hoc copies (R-39).
4. **Wording.** Check every new or changed string against the glossary (05 §10) and the `action_*` strings (R-35…R-37). Propose the exact Spanish copy in the UX notes so the user can approve it at the checkpoint.
5. **Decided items.** Reject any plan that brings back an item from "Decided — do not re-propose" (for example "Deshacer", a confirmation dialog outside R-08, applying a change when a sheet closes).
6. **Quality.** Check:
   - loading, empty, error and offline states come from the templates;
   - haptics follow R-29;
   - touch targets are 48 dp;
   - actionable icons have a `contentDescription`, and rings and bars a `stateDescription`;
   - AA contrast;
   - tokens only;
   - a tablet layout (≥ 600 dp) where the screen needs one;
   - components stay under ~200 lines.
7. **testTags.** snake_case `<screen>_<element>` on every interactive element; reuse the template's tags.
8. **After implementation** (when the PM asks): review the composables in the PR diff against the same pattern map and rules before the merge. Report deviations; do not edit code.

## Result
Add a `## UX notes` section at the end of the plan with:
- the pattern map;
- the ✗ rules with their fix;
- the sweep list;
- the Spanish copy;
- the testTags.

Return **REJECTED** when any rule fails, a template is reinvented or a decided item comes back; otherwise return **PASSED**. Do not modify code. Write in English. Reply to the PM in ≤ 10 lines: verdict, conditions for the developer, sweep items, confirmed testTags.
```

Notes for adopting it:
- Step 8 needs one line added to the PM workflow (`06-agent-workflow.md`): run ux-reviewer on the PR of every UI task
  that adds or changes a pattern.
- Until the Wave 1 templates exist, the pattern map names the rule and the reference screen instead of a component.

## 12. Decisions only you can take

Each comes with a recommendation. Until you decide, the current ADRs stand.

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | Top bar layout | Tab roots without a title bar, since their own header carries the context. Full-screen bars show entity titles and actions. | Fixes "too tall" and NAV-1…3 together. |
| D2 | Leaving with unsaved changes | Full-screen editors ask when there are unsaved changes; sheets discard silently (R-14). | Protects long forms without slowing small edits. Removes the 05 §6.2 / §6.3 contradiction. |
| D3 | Removals inside editors | They wait for "Guardar" (R-11), amending ADR-41. | You asked for this on meals, and the other editors already work this way. |
| D4 | Dialog policy | R-08. A custom exercise is deleted at once. "Archivar" becomes an immediate "Eliminar", or gets a restore feature. | Matches ADR-45 and ADR-51; today "Archivar" is a delete that asks first. |
| D5 | Swipe threshold | About 50 %, with an armed cue and haptics; a fling never deletes unless armed. Specified in §14. | Hard to trigger by accident because of the armed cue, not because of the distance. |
| D6 | Renaming named items | A Name sheet for meals, folders, "Guardar como comida" and "Guardar como rutina". | One pattern; closes 4 E9 rows. |
| D7 | Glossary terms | "Comidas del día", "Comidas guardadas", search title "Añadir al desayuno", the diary history titled "Calorías", plus the rest of Appendix A. | Ends the overloading of "comida". |
| D8 | Perfil root | A gear icon in the header; statistics rows with values; "Cerrar sesión" and "Borrar cuenta" move to Preferencias → "Cuenta". | Gives Perfil room for metrics and takes the destructive account actions off the main screen. |
| D9 | Preferencias structure | A hub of sections, each with its own "Guardar" (amends ADR-24). | It keeps growing (E7 adds Cocina and defaults); small, explicit saves; how Android settings work. |
| D10 | Rest bar position | At the bottom of the live session. | Within reach of the thumb (design principle 4). |
| D11 | Diario layout | Meals first; a one-line summary strip on top; the full dashboard at the bottom. | Does what you asked without hiding the remaining budget. |
| D12 | "Entreno en curso" bar on every tab | Add it: elapsed time and rest countdown; a tap takes you back. It goes beyond the current stories. | The biggest "modern app" gain for using the app at the gym. |
| D13 | Timing | Put the rulebook and the new ux-reviewer in place before the E7 UI tasks (T-106, T-108, T-110); build the components in E9. | E7 adds about 6 sheets and screens; without the rules they become more migration work. |
| D14 | Screenshot tests | Adopt them, for the designsystem templates only. | Catches visual regressions cheaply; nothing is sent to you. |

## 13. Suggested E9 breakdown

- **Wave 0: decisions.** You answer D1–D14. The adopted rules go into `05-design-system.md`, and a new ADR amends
  ADR-24, ADR-41 and ADR-45.
- **Wave 1: foundations.** In `designsystem/`, each with catalog entries and tests:
  - `AppTopBar` v2, `AppScreen` and `AppEditorScreen`;
  - the sheet fix, the sheet archetypes and the Name sheet;
  - `AppMessages`, `AppHaptics` and `OfflineState`;
  - the v2 fields and `AppSearchField`;
  - `SwipeToDeleteRow` v2 and `ReorderableColumn` v2;
  - `MetricCard`, the rows, `SectionHeader`, `AddItemButton` and `TimeRangeSelector`;
  - the `action_*` strings and `AppDateFormat`;
  - `check-ux-conventions.sh`, with G1–G10 first as warnings, then as failures once each domain is migrated.
- **Wave 2: migrations by domain.** Each task closes its §7 rows and turns its guards into failures:
  1. Diario, food search, FoodSheet and comidas: items 8–12, 14, 28 and 33; LST-1, SEL-2, SEL-3 and WRD-1.
  2. Peso: items 24 and 30–32; FBK-4 and SEL-1.
  3. Training (rutinas, builder, live session, history, exercises): items 15, 16 and 35–37; NAV-1, NAV-4, NAV-5,
     LST-2, A11Y-1 and SHT-4.
  4. Perfil, Preferencias and the meals editor: items 5–7, 18–22 and 25; EDT-1…3.
- **Wave 3: wording and motion.**
  - A glossary sweep of `strings.xml`.
  - `animateItem` everywhere.
  - Tooltips.
  - Select-on-focus.
  - The day-picker improvements.
- **Wave 4: checkpoint.** One journey per domain, as usual, plus "leave the app and come back".

## 14. Deep dive: swipe-to-delete with vibration

Swipe is the interaction you find most annoying, and it has been "fixed" twice without fixing how it feels. This
section covers:
- what went wrong;
- how professional apps do it;
- the exact behaviour and numbers;
- how the vibration should work;
- a step-by-step implementation for this codebase.

§14.2–§14.4 are also the proposed specification of `SwipeToDeleteRow` and `AppHaptics` for 05 (§6 components,
§9 patterns), so ux-reviewer can check plans against them.

### 14.1 What went wrong, twice

| Version | Behaviour | What you felt |
|---|---|---|
| First (T-060) | Material's default swipe settings, where a quick flick can complete the swipe after a short drag. | It fired too easily: a slightly diagonal scroll could delete a food (E4-C round 2). |
| Today (T-072) | Safety by distance (`designsystem/SwipeToDeleteRow.kt:39,61-67`): <ul><li>you must drag 90 % of the width;</li><li>flings are ignored;</li><li>nothing changes visually and nothing vibrates;</li><li>on release, the row snaps back and only vanishes when the server answers.</li></ul> | It still feels unlike modern apps (E4-C round 3): <ul><li>you drag almost to the edge without knowing when it will work;</li><li>nothing tells you it worked;</li><li>the row jumps.</li></ul> |

The lesson: **safety does not come from distance.** It comes from an explicit **armed state** that you can see and
feel, plus one rule: the row is deleted only if you let go while it is armed. Professional apps get both safety and
speed this way.

### 14.2 How professional apps do it

Mail, task and workout-logging apps (Gmail, Google Tasks, Apple Mail, Outlook, Todoist and similar) share the same
model:

1. **The swipe only starts when the finger clearly moves sideways.** Once the finger passes the system touch slop (a
   few millimetres), the direction is locked: mostly vertical means scroll, mostly horizontal means swipe. Scrolling
   never deletes.
2. **The row follows the finger 1:1.** No lag, no resistance. Underneath, the action layer appears from the edge you
   swipe towards: the trash icon and the word "Eliminar", in a muted (not yet active) state.
3. **One clear point arms the action.** At about half the row width, the layer switches to its active state: full
   colour, and the icon "pops" (grows slightly with a spring). At the same moment you feel **one crisp tick**, like a
   detent. From now on, letting go deletes.
4. **You can change your mind.** Dragging back below the line returns the layer to the muted state, with a lighter
   tick. The disarm point is a little before the arm point, so it never flickers. Letting go there does nothing.
5. **Letting go while armed completes it at once.**
   - The row keeps moving out in the same direction, at your finger's speed.
   - You feel a firm **confirm** vibration.
   - The rows below slide up to close the gap.
   - The item is gone from the screen immediately; the server call happens in the background.
6. **Letting go before the line springs the row back.** No vibration; nothing changed.
7. **If the server fails,** the row slides back into its place, a short message says "No se pudo eliminar. Inténtalo
   de nuevo.", and you feel a **reject** vibration.

Always true:
- One direction (right to left) and one action (delete or remove), with the same look everywhere.
- Exactly one vibration per state change; never a continuous buzz while dragging.
- The swipe is a shortcut, never the only way. The same action exists in the item's details and as a TalkBack action.
- With "remove animations" on, the row disappears without sliding, and the vibrations stay.
- On phones with gesture navigation, a swipe that starts at the very right edge is the system back gesture. Rows do
  not fight it (people naturally start mid-row), so no gesture-exclusion workarounds on list rows.

### 14.3 Specification

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

### 14.4 The vibration part: haptics done right

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

// platform/ViewAppHaptics.kt (androidMain): the best constant for the running Android version
class ViewAppHaptics(private val view: View) : AppHaptics {
    override fun armed() = perform(if (SDK_INT >= UPSIDE_DOWN_CAKE) GESTURE_THRESHOLD_ACTIVATE else LONG_PRESS)
    override fun disarmed() = perform(if (SDK_INT >= UPSIDE_DOWN_CAKE) GESTURE_THRESHOLD_DEACTIVATE else CLOCK_TICK)
    override fun confirm() = perform(if (SDK_INT >= R) CONFIRM else LONG_PRESS)
    override fun reject() = perform(if (SDK_INT >= R) REJECT else LONG_PRESS)
    override fun tick() = perform(if (SDK_INT >= UPSIDE_DOWN_CAKE) SEGMENT_TICK else CLOCK_TICK)
    private fun perform(constant: Int) { view.performHapticFeedback(constant) }  // respects the system setting
}
```

Provide it once at the root. A composable `expect fun rememberAppHaptics(): AppHaptics` has an Android `actual` that
returns `remember(view) { ViewAppHaptics(LocalView.current) }`, and the root uses
`CompositionLocalProvider(LocalAppHaptics provides rememberAppHaptics())`.

**One action, one vibration, from the layer that knows the outcome.** Today, completing a set can vibrate twice, and at
the wrong moment:
1. `SetCompletionCheckbox` fires `Confirm` on the tap (`designsystem/SetCompletionCheckbox.kt:71-74`), even when the
   tap only opens the mandatory RPE picker.
2. `LiveSessionViewModel.completeSet` then adds the raw 15 ms tick (`:689-690`) when the set really completes.

Fire one `confirm()` when the set becomes completed, and remove the other. Swipe follows the same rule: the component
fires `confirm()` on commit. The ViewModel never vibrates again for the same delete; it only triggers `reject()` if
the delete fails.

**Alerts are not UI feedback.** The end of a rest (E9 item 15, "too short to notice") is an alert, so it keeps its own
path: the existing `Haptics` port in `AndroidHaptics.alert()`. Replace the single 400 ms buzz with a pattern:

```kotlin
// AndroidHaptics.alert(): three strong pulses instead of one 400 ms buzz
val timings = longArrayOf(0, 250, 150, 250, 150, 400)
val effect = if (vibrator.hasAmplitudeControl()) {
    VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255), -1)
} else {
    VibrationEffect.createWaveform(timings, -1)
}
if (SDK_INT >= TIRAMISU) {
    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)) // felt like a timer
} else {
    vibrator.vibrate(effect)
}
```

`Haptics.tick()` disappears; UI ticks go through `AppHaptics`.

### 14.5 How to build it in this project

**Step 1. Rewrite `designsystem/SwipeToDeleteRow.kt`.**
- Keep the public API and add `action: SwipeAction = SwipeAction.Delete`. `Delete` shows "Eliminar"; `Remove` shows
  "Quitar" inside editors. The component gets its own strings instead of reusing `diary_entry_delete`.
- Drop the experimental `SwipeToDismissBox`. Build the row on `Modifier.draggable` plus an `Animatable` offset, which
  gives exact control of arming, hysteresis, vibration and the exit.

Sketch of the gesture core (the action layer, tags and previews are left out):

```kotlin
private const val ARM_AT = 0.5f      // arms at 50 % of the row width
private const val DISARM_AT = 0.4f   // disarms below 40 % (hysteresis)
private const val EXIT_MILLIS = 200

@Composable
fun SwipeToDeleteRow(
    deleteLabel: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    backgroundTag: String,
    modifier: Modifier = Modifier,
    action: SwipeAction = SwipeAction.Delete,
    containerColor: Color = AppTheme.colors.surfaceRaised,
    content: @Composable () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val animations = LocalAnimationsEnabled.current
    val currentOnDelete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var fingerX by remember { mutableFloatStateOf(0f) }   // updated synchronously, so no drag delta is lost
    var width by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    val drag = rememberDraggableState { delta ->
        fingerX = (fingerX + delta).coerceIn(-width, 0f)
        scope.launch { offset.snapTo(fingerX) }
        val travel = if (width > 0f) -fingerX / width else 0f
        val nowArmed = if (armed) travel >= DISARM_AT else travel >= ARM_AT
        if (nowArmed != armed) {
            armed = nowArmed
            if (nowArmed) haptics.armed() else haptics.disarmed()
        }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .clipToBounds()
            .semantics {
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) { haptics.confirm(); currentOnDelete(); true },
                    )
                }
            },
    ) {
        if (offset.value < 0f) SwipeActionLayer(action, armed, Modifier.matchParentSize().testTag(backgroundTag))
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(containerColor)                      // opaque, so the layer never shows through
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,         // direction lock: vertical drags stay scrolls
                    enabled = enabled,
                    onDragStarted = { offset.stop(); fingerX = offset.value },   // catch a row that is still moving
                    onDragStopped = { velocity ->
                        if (armed) {
                            haptics.confirm()
                            if (animations) {
                                offset.animateTo(-width, tween(EXIT_MILLIS, easing = FastOutLinearInEasing), velocity)
                            } else {
                                offset.snapTo(-width)
                            }
                            currentOnDelete()
                        } else if (animations) {
                            offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), velocity)
                        } else {
                            offset.snapTo(0f)
                        }
                        armed = false
                    },
                ),
        ) { content() }
    }
}
```

`SwipeActionLayer` draws the muted or active layer, anchored to the right edge. The icon scale comes from
`animateFloatAsState(if (armed) 1.2f else 1f, spring())`, and the label from `action`.

**Step 2. Add `AppHaptics`** (code in §14.4), provide it at the root, and fix the double vibration on set completion.

**Step 3. Remove the row at once (optimistic) and restore it if the server fails.** This applies to the ViewModels that
delete saved records by swipe:
- `DiaryViewModel` (entries);
- `CircumferencesViewModel`;
- `LiveSessionViewModel` (sets);
- the saved-comidas list in the food search.

```kotlin
// DiaryViewModel (sketch): pendingDeletes is part of the state the screen renders
is DiaryEvent.DeleteEntry -> {
    pendingDeletes.update { it + event.id }            // the row leaves the screen now
    viewModelScope.launch {
        runCatching { repository.deleteEntry(event.id) }
            .onSuccess { reload() }                    // the server state confirms it
            .onFailure {
                pendingDeletes.update { it - event.id } // the row comes back
                effects.send(DiaryEffect.DeleteFailed)   // the UI shows the message and calls haptics.reject()
            }
    }
}
// The screen renders entries.filterNot { it.id in pendingDeletes }.
```

Inside editors (comida editor, routine builder, workout form), removal is already local to the draft, so it is
immediate by nature.

**Step 4. Close the gap smoothly, with stable keys.**
- `LazyColumn` lists (comida editor, saved comidas): add `Modifier.animateItem()` to each item.
- Rows inside a `Column` (Diario meal card, live exercise card, circumference history): wrap each row in `key(id)`, and
  add `Modifier.animateContentSize()` to the card's content column so its height animates when a row leaves.
- **Stable keys are mandatory.** Without them, the row below inherits the slid-out offset of the deleted row and seems
  to vanish too, or the wrong row animates. Today four lists key rows by position:

  | List | Problem |
  |---|---|
  | `feature/diary/MealSection.kt:117` (Diario entries) | No key |
  | `feature/workout/LiveExerciseCard.kt:82` (sets) | No key, and `LiveSetRow` has no stable id: `setIndex` is renumbered after a delete, so add one |
  | `feature/templates/TemplateDetailsScreen.kt:128` (comida editor) | Keyed by `index`, the position in the draft |
  | `feature/weight/CircumferenceSection.kt:201` | No key |

  `RoutineBuilderSets.kt:44`, `WorkoutFormExerciseCard.kt:101` and `FoodSearchTemplates.kt:117` already use stable
  keys.

**Step 5. Apply it to all 7 callers at once.**
- Saved records (`Delete`):
  - `feature/diary/DiaryEntryRow.kt:69`;
  - `feature/food/FoodSearchTemplates.kt:118`;
  - `feature/weight/CircumferenceSection.kt:216`;
  - `feature/workout/SessionSetRow.kt:74`.
- Inside editors (`Remove`, "Quitar"):
  - `feature/templates/TemplateDetailsScreen.kt:199`;
  - `feature/routines/RoutineBuilderSets.kt:93`;
  - `feature/workout/WorkoutFormSetRow.kt:127`.

Set rows need special care. Most of their width is the KG and REPS fields, and a text field can consume horizontal
drags. Add a test that starts the swipe on the weight field. If the field steals the gesture, intercept the
horizontal drag in the row before the field receives it.

**Step 6. Tests** (host, Robolectric, with a recording `AppHaptics` provided through `LocalAppHaptics`):

| Gesture or case | Expected |
|---|---|
| Drag to 30 %, release | No delete, no haptic; back at rest |
| Drag to 60 %, release | `onDelete` once; haptics `[armed, confirm]` |
| Drag to 60 %, back to 30 %, release | Haptics `[armed, disarmed]`; no delete |
| Drag to 60 %, back to 45 % (inside the hysteresis band) | Still armed; no second tick |
| Fast fling covering 25 % | No delete |
| TalkBack custom action | `onDelete` once; `confirm` |
| Reduced motion (`LocalAnimationsEnabled` false) | Deleted without the exit animation |
| A list of three rows; delete the first | The other two stay visible with their own content (catches the key bug) |
| A swipe that starts on the weight field of a set row | The row moves |
| ViewModel: a failed delete | The row comes back, plus the error effect |

**Step 7. Check it on your phone** (what tests cannot feel):
1. Scroll the Diario fast and diagonally: nothing is deleted.
2. Swipe slowly: the tick arrives at about half the row. Go back: a lighter tick. Release: nothing happens.
3. Swipe past the tick and release: one firm vibration, the row leaves, and the rows below slide up.
4. Try a short, fast flick: nothing is deleted.
5. Swipe a set in the live session, starting on the KG field: it works the same way.
6. With TalkBack on, "Eliminar …" appears in the actions menu.
7. Compare with Gmail on the same phone: it should feel like the same family.

### 14.6 Swipe checklist for ux-reviewer

- [ ] Uses `SwipeToDeleteRow`; there is no other swipe code (guard G7).
- [ ] Right to left only, labelled "Eliminar" or "Quitar" per the glossary.
- [ ] Arms at 50 % and disarms at 40 %; deletes only when released while armed.
- [ ] Haptics through `AppHaptics`: armed, disarmed, confirm, reject. Nothing else vibrates for the same action.
- [ ] The row leaves at once (optimistic) and the gap closes; a failure brings it back with a message.
- [ ] Stable keys, plus `animateItem` or `animateContentSize`.
- [ ] The same action exists in the details and as a TalkBack action.
- [ ] With reduced motion it snaps; the haptics stay.

## 15. Proposed changes to the agent workflow

The rules, templates and guards above only stop the drift if the workflow uses them on every task. This section
reviews the current workflow (`CLAUDE.md`, `planning/06-agent-workflow.md` §2.1, `.claude/agents/`) from a UX point of
view and proposes concrete changes. Per `CLAUDE.md`, changes to `CLAUDE.md` and to the agents need your approval;
nothing here has been applied.

### 15.1 What the current workflow does well

- The planner → ux-reviewer → developers order is right. A UX finding on a plan costs nothing; the same finding after
  the code exists costs a developer run and a review round. ux-reviewer stays before development.
- "A finding that repeats becomes a check" is exactly the right instinct. It produced `check-design-tokens.sh` and
  `check-text-encoding.sh`, and it is the model for the UX guards (§10).
- 👤 checkpoints per functionality, with "leave the app and come back", catch what tests cannot feel.

### 15.2 Where the workflow lets UX drift through

| # | Gap | Evidence | Effect |
|---|---|---|---|
| W1 | ux-reviewer sees only the plan, never the code. | `06` §2.1: it runs once, before development. It has no Bash, so it cannot read a diff. pr-reviewer's UI check is one line: tokens, Spanish strings, `testTag`. | Everything decided between plan and code goes unchecked: the CTA inside the scroll, a second offline banner, a different row layout, the double vibration. |
| W2 | "A finding that repeats becomes a check" applies only to pr-reviewer findings. | `CLAUDE.md` § Rules. | Your repeated UX feedback (Deshacer, add icons, tap → detail, swipe) never became a check. |
| W3 | Checkpoint UX feedback becomes a row in the E9 table, not a rule. | E4 retro: "the PM keeps appending UI feedback to the E9 table". Your auto-memory "style requests go to the UI epic". | Symptoms are recorded and conventions are lost, so the next screen repeats them (RC6). |
| W4 | No step migrates older screens when a new component or rule appears. | `HelpButton` (E6) never reached Peso (E5); `SwipeToDeleteRow` (T-072) did not fix *where* delete lives. | Two or more patterns live side by side for the same thing. |
| W5 | The epic breakdown does not check UX patterns. | E7's breakdown puts the photo icon "below the scanner icon" and "Comidas por defecto" as a button inside an editor (§6.2, §6.4). | Pattern problems are decided at breakdown and inherited by every task plan. |
| W6 | Spanish copy has no owner before the checkpoint. | Your auto-memory "propose before applying feedback"; `CLAUDE.md` has no wording step. | Wording drifts (WRD-1…WRD-4) and you meet it for the first time at the checkpoint. |
| W7 | No regression check for the UI rules. | Only the token and encoding guards run in CI. | Mechanical violations (a stray `SnackbarHost`, a new dialog, "Deshacer") need an agent to notice them. |

### 15.3 Proposed flow for a task with UI (`06` §2.1)

```
[PM] start: git checkout main && git pull && git checkout -b feature/T-###-slug
  → planner            plan in .workflow/current_task.md
  → ux-reviewer (plan)  pattern map, rules ✓/✗, sweep of existing screens, Spanish copy, testTags
        └─ REJECTED → planner fixes the plan (no code written yet)
  → kotlin-core-developer, then compose-ui-developer (and/or backend-developer)
  → pr-creator         commit(s), push, gh pr create
  → pr-reviewer          ┐ in parallel on the same PR
  → ux-reviewer (diff)   ┘ only if UI files changed; the PM passes `git diff --name-only main...HEAD`
        └─ either finds an issue → developer, one combined fix round
[PM] waits for green CI (incl. check-ux-conventions.sh) → merge → COMPLETED
```

Cost control:
- Diff mode reads only the changed composables and `strings.xml`, and checks them against the pattern map approved in
  plan mode. It does not re-review the design.
- It is skipped when no file under `composeApp/**/feature/`, `composeApp/**/designsystem/` or `composeResources/`
  changed, and for minor corrective tasks (§2.3).
- Running in parallel with pr-reviewer adds no wall-clock time.

### 15.4 Proposed text for `CLAUDE.md`

Three edits in § Rules, and one in § Your role. They keep the file short; the detail lives in `05` and `06`.

1. Replace "**A finding that repeats becomes a check.** The second time pr-reviewer reports the same defect type, add a
   script or CI guard in the epic instead of another prose rule." with:

   > **A finding that repeats becomes a check.** The second time pr-reviewer, ux-reviewer or the user at a checkpoint
   > reports the same defect type, add a script or CI guard (`check-ux-conventions.sh` for UI patterns) in the epic
   > instead of another prose rule.

2. Add after it:

   > **Checkpoint feedback becomes a rule, not a row.** When the user's feedback states a convention (how something
   > should work everywhere), the PM writes it into `05-design-system.md` §8 or the glossary (§10) in the same docs
   > PR that records the feedback, and the fix task sweeps every screen with that pattern. Only one-off visual details
   > go to the E9 refinement table.

3. Add:

   > **UI tasks get two UX passes.** ux-reviewer checks the plan (pattern map, rules, sweep, Spanish copy) and, when
   > UI files changed, the PR diff in parallel with pr-reviewer. A new designsystem component or rule retires every
   > ad-hoc copy in the same epic, or each one is logged in the E9 table with an owning task.

4. In § Your role, in the checkpoint bullet, add after "never just point to the APK":

   > ; list the new or changed Spanish texts so the user approves the wording at the same time

### 15.5 Agent files

| Agent | Change |
|---|---|
| `ux-reviewer.md` | Replace with §11, and add two things. **Plan and diff modes:** in diff mode, the input is the changed-file list from the PM and the output is a ✓/✗ list against the plan's pattern map. **The three contradictions with your decisions fixed:** dialogs only per R-08; removals inside editors wait for "Guardar"; "add a new item" is a labelled button, not an icon. Drop the Stitch screenshots from its reading list, and read the most similar existing screen instead. Keep the `model:` line as you set it. |
| `epic-planner.md` | For each UI task in a breakdown, name the templates and patterns it uses (screen, sheet archetype, rows, add/delete placement) and any existing screen that must be migrated with it. This catches W5 before any plan exists. |
| `planner.md` | In the plan's "Changes" section, list the designsystem templates used, and add a "Sweep" line: other instances of each changed pattern, found with Grep. |
| `compose-ui-developer.md` | One rule: use the designsystem templates (`AppScreen`, `AppEditorScreen`, sheet archetypes, rows, `AppHaptics`, `AppMessages`); never draw a top bar, footer, message host, offline banner or haptic call directly in a screen. `check-ux-conventions.sh` must pass before finishing. |
| `pr-reviewer.md` | No change. Consistency is ux-reviewer's job in diff mode, so pr-reviewer stays focused on correctness, architecture, security and tests. |

### 15.6 Other process changes

- **CI:** add `bash scripts/check-ux-conventions.sh` to `android-ci`, next to `check-design-tokens.sh`. Start with
  warnings, and turn each guard into a failure as Wave 2 migrates its domain (§13).
- **Retrospectives:** add one fixed question per epic: "Which UX feedback did the user give twice?" Each answer
  becomes a rule or a guard (W2, W3).
- **Your auto-memory:** "Style requests go to the UI epic" stays true for one-off visual details. Conventions, though,
  now go into `05` at once (15.4, edit 2). If you agree, the memory note should say so, so the PM does not keep filing
  conventions as table rows.

### 15.7 Expected effect

| Gap | Closed by |
|---|---|
| W1 | Diff mode (15.3) and the guards (§10) |
| W2 | `CLAUDE.md` edit 1 |
| W3 | `CLAUDE.md` edit 2 and the retrospective question |
| W4 | `CLAUDE.md` edit 3, R-39 and the planner's "Sweep" line |
| W5 | The epic-planner change |
| W6 | Spanish copy in plan mode, and `CLAUDE.md` edit 4 |
| W7 | `check-ux-conventions.sh` in CI |

The cost is one extra ux-reviewer run on UI tasks, in parallel with pr-reviewer and reading only the diff. In return,
fewer fix rounds after checkpoints. In E4–E6, most rounds were UX consistency rounds (T-066, T-067, T-072, T-073,
T-075, T-084, T-087, T-097d, T-097g, T-101a).

## Appendix A — Glossary (Spanish UI)

Proposed; you approve the wording (D7).

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
| Chart ranges | "1S 1M 3M 6M 1A" (spoken "1 semana"…) | "Último mes" and similar variants | One string set |

Message templates:
- Required: "Escribe un nombre".
- Too long: "Máximo N caracteres".
- Range: "Entre X y Y <unidad>".
- Duplicate: "Ya existe <una comida / una carpeta / una rutina> con ese nombre".
- Error: "No se pudo <acción>. Inténtalo de nuevo."
- Offline action: "Sin conexión: no se puede <acción>".
- Saved: "Cambios guardados".

## Appendix B — Evidence index

Paths are relative to `composeApp/src/commonMain/kotlin/io/github/lopezjavier/appfitness/`.

- **Shell and top bar:**
  - `app/AppScaffold.kt:166-183` (global offline banner), `:326-341` (top bar per screen type);
  - `app/AppRoutes.kt:258-321` (static titles);
  - `designsystem/AppTopBar.kt:85-124` (no actions slot).
- **Sheets:**
  - `designsystem/AppBottomSheet.kt:67-102`;
  - `feature/diary/EditEntrySheet.kt`;
  - `feature/templates/TemplateFoodSheet.kt`;
  - `feature/weight/WeightDaySheet.kt`;
  - `feature/diary/DiaryDayPickerSheet.kt`;
  - `feature/routines/FolderSheet.kt`.
- **Dialogs:**
  - `feature/profile/ProfileDialogs.kt:17,30`;
  - `feature/routines/RoutinesSheets.kt:54-75`;
  - `feature/workout/LiveSessionDialogs.kt:100`;
  - `feature/workout/WorkoutEntry.kt:144`;
  - `feature/routines/RoutineBuilderScreen.kt:144`;
  - `feature/meals/MealSlotsScreen.kt:81`;
  - `feature/exercises/ExerciseDetailScreen.kt:268`.
- **Swipe and reorder:**
  - `designsystem/SwipeToDeleteRow.kt:39,61-67,103-126`;
  - `designsystem/ReorderableColumn.kt:148`.
- **Rows:**
  - `feature/diary/DiaryEntryRow.kt`;
  - `feature/templates/TemplateDetailsScreen.kt:197-250`;
  - `feature/food/FoodSearchScreen.kt:298-354`;
  - `feature/routines/RoutineCard.kt:47-107`.
- **Fields:**
  - `designsystem/AppTextField.kt:60-152`;
  - `designsystem/NumberInput.kt:122-218`;
  - the clear-button workaround in `feature/food/FoodSearchScreen.kt:162-181` and
    `feature/exercises/ExercisePickerScreen.kt:206-213`.
- **Feedback:**
  - snackbar hosts listed in FBK-1;
  - in-screen offline banners in `feature/weight/WeightScreen.kt:152`, `feature/templates/TemplateDetailsScreen.kt:113`,
    `feature/meals/MealSlotsScreen.kt:125` and `feature/routines/RoutineBuilderScreen.kt:248`;
  - inline help in `feature/weight/WeightChart.kt:209-246` and `feature/weight/CircumferenceSheet.kt:141-164`.
- **Editing:**
  - `feature/meals/MealSlotsEditorViewModel.kt:162-170`;
  - `feature/profile/PreferencesScreen.kt:122-142`;
  - back is intercepted only in `feature/routines/RoutineBuilderScreen.kt:123-128` and
    `feature/meals/MealSlotsScreen.kt:63-68`.
- **Screens:**
  - `feature/diary/DiaryScreen.kt:149-157`;
  - `feature/diary/MealSection.kt:136-140`;
  - `feature/profile/ProfileRootScreen.kt:303-362`;
  - `feature/weight/WeightScreen.kt:312-345`;
  - `feature/workout/LiveSessionScreen.kt:206-222`;
  - `feature/workout/SessionDetailScreen.kt:146-185,257-295`;
  - `feature/workout/HistoryScreen.kt:124-137`;
  - `feature/exercises/ExerciseDetailScreen.kt:231-249`;
  - `feature/workout/WorkoutEntry.kt:136-141`;
  - `feature/routines/RoutinesScreen.kt:170-177`.
- **Strings:** `composeApp/src/commonMain/composeResources/values/strings.xml`; the counts are in WRD-3 and the
  overloaded terms in WRD-1 and WRD-2.
- **Screenshots:**
  - `design/reported/e4c-line-through-quantity-field.png`;
  - `design/reported/e5b-r3-objetivo-vs-peso-actual-and-trend-two-dots.png`.
