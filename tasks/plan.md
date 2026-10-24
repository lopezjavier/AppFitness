# Implementation Plan: E6 · Training depth → `v0.6.0`

## Overview
Turn the Phase 1 workout into a complete gym experience. Routines are planned set by set. Sets carry a type and an RPE, and every exercise metric is logged (bodyweight, duration, distance…) with realistic volume. Records are detected and shown in the summary, a full exercise detail and the workout detail. The live session gains pause, set deletion, a previous-values mode and the warm-up/plate calculators. Routines get folders, a manual order, a next workout and "Actualizar rutina". Finished workouts get a detail, a calendar of trained days, editing, deletion, "Guardar como rutina" and past-workout logging. The epic also adds editing manual foods with the full Spanish label (US-47).

Sources: `planning/epics/E6-training-depth.md` (US-28…US-38, US-47), `planning/01-phase1-scope.md` FR-2.14, FR-4.8 and §4.1, `planning/03-data-model.md` §3/§5/§6, `planning/04-business-rules.md` §2 and §4 (incl. §4.9 calculators), `planning/05-design-system.md` (§6 `SetRow`, `ReorderableColumn`, `MonthCalendarGrid`, `SwipeToDeleteRow`; §8 interaction rules), ADR-30, ADR-32, ADR-38, ADR-39, ADR-40, ADR-43, ADR-45, ADR-47, ADR-50…ADR-53, and the E5 retrospective actions. Tasks: `tasks/todo.md` (T-089…T-103). Earlier plans are in the git history.

Out of scope (epic file and `01` §4): advanced statistics, record charts and non-weight charts (E8); the program generator and trainer; microcycles; cardio plans; social features; background rest alerts (Phase 2). Deferred by the user at this breakdown (ADR-52, text kept in `01` §4.1): the live "¡Nuevo récord!" notification and its levels; the calendar streak, rest days and rewards; copying a workout. Editing Open Food Facts foods is not allowed (ADR-53).

## Architecture Decisions
- **Vertical slices, sequential.** One task per story. US-29 splits into planning (T-089) and logging (T-090). US-34 (PR catalogue, backend + summary marks) and US-28 (exercise detail UI) stay separate. US-37 splits into detail and calendar (T-100) and management (T-101). US-47 is one task (T-102); without past-entry recomputation its backend is one RPC and a key whitelist.
- **Product decisions of the breakdown** (user, 2026-10-22):
  - ADR-50: per-set planning; planned weight/reps are dim placeholders in the live session (E3 ghost pattern), the planned type is applied; target RPE per exercise (`routine_exercises.target_rpe`, no schema change).
  - ADR-51: deleting a folder deletes its routines after a confirmation that names how many (the one training exception to ADR-45); deleting a set in the live session is immediate with no "Deshacer".
  - ADR-52: deferred items above; "Total de repes" is the all-time total; calculator defaults in `04` §4.9 (bars 20/15/10 EZ/7,5 kg; plates 25…0,5 kg incl. 2 and 1 kg; rounding 2,5 kg plates, 2 kg dumbbells).
  - ADR-53: only manual foods are editable; edits apply to future entries only (ADR-32 snapshots unchanged); portions can be added, edited and removed; the full OFF nutrient list in Spanish label order (amends ADR-43).
- **Schema exceptions to ADR-30 are explicit.** The forward-compatible columns cover most of E6. The exceptions are one new migration each, with the task that needs it, recorded in a single E6 schema ADR that T-089 writes and later tasks amend:
  - planned sets per routine exercise (a child table of `routine_exercises`; T-089), with a data migration that keeps every existing routine's sets and reps;
  - per-user training preferences (T-090: warm-up inclusion; extended by T-093 double dumbbell if the planner stores it there, T-096 previous-values mode, T-097 bar/plates/rounding);
  - catalogue metric types (T-092, a new generated migration per ADR-39);
  - `routines.position` (T-098, epic technical note);
  - accumulated paused time (T-096) and per-session rests (T-099), if the planners confirm they are needed.
  - Food editing (T-102) needs no new column: `foods.micronutrients` already exists; only the RPC whitelist grows.
- **Settings live in Postgres, not DataStore.** Warm-up inclusion, double dumbbell and the previous-values mode change SQL aggregates and RPC results, so they must be visible to the database (NFR-04, ADR-01). They are edited in Preferencias under an "Entrenamiento" block placed by ux-reviewer, with the screen's single "Guardar" (ADR-45).
- **Aggregates are computed on read.** "Recalculating" on a toggle means the aggregate RPCs (`exercise_history`, `weekly_muscle_volume`, `personal_records`, `last_performance`) read the caller's settings. Nothing is rewritten. Each change is a new `create or replace` migration with pgTAP for both states. Domain functions (`CalculateVolume`, `EstimateOneRepMax`, `DetectPersonalRecords`, `PersonalRecordCatalogue`) use the same test cases as pgTAP (`03` §6 note).
- **The polymorphic set row (ADR-30) is extended, not rewritten.** T-092 implements every metric type the seeded catalogue uses. `SetRow` changes are additive (check every caller). New design-system pieces are built in the first task that needs them: `SetTypeBadge` and the set-type picker (T-089), the RPE picker (T-091), the stopwatch (T-092), each with a catalog entry.
- **RPC signatures** (names are proposals; the planners finalise them and update `03` §6): `save_routine` v2 and `start_workout_session` v2 (T-089), `last_performance` with RPE and metric columns (T-091, T-092) and an optional routine filter (T-096), `personal_records(p_exercise_id)` (T-094), folder/order/next RPCs (T-098), `update_routine_from_session(p_session_id)` (T-099), `log_past_workout(p_payload jsonb)` and the workout-edit RPC (T-101), `update_personal_food` and `create_personal_food` with the full nutrient keys (T-102). The calculators are pure client-side domain (T-097).
- **Interaction rules (ADR-45, `05` §8).** One explicit "Guardar" on every edit surface, sheets included, and nothing applied on dismiss. Delete at once through `SwipeToDeleteRow`; the only E6 exception is the non-empty folder delete (ADR-51). One label per concept.
- **Restoration (ADR-40).** Builder drafts, pickers, the stopwatch, pause state, calculator inputs, folder names, the calendar view, edit and past-workout drafts and the food edit draft all survive background and process death. Each UI task ships `StateRestorationTester` + recreated-ViewModel tests.
- **E5 retrospective actions.** Every task that edits or deletes records has a test with a record outside the screen's loaded range (T-090, T-093, T-096, T-098, T-100, T-101, T-102). Rules over user data state the assumed usage frequency for the user to confirm at the checkpoint.

## Task List

Checkpoint rule: every user-testable item of the epic's tasks is collected first, then grouped by journey into checkpoints of 5–7 items, each placed right after the last task of its journey; the count follows from that. An item is one coherent thing to try on the phone, not one per field, variant or sub-step, and the same behaviour tested by several tasks is one item. "Leave the app and come back" is in every checkpoint but not counted. After the scope cuts (ADR-52) E6 yields 19 items in three journeys (user, 2026-10-22: 3 checkpoints). The full item lists are in `tasks/todo.md`.

### Phase 1: The gym session (plan, log, control, records)
- [ ] T-089: Per-set routine targets (US-29 planning) — UI + backend — L
- [ ] T-090: Set types in the live session and "Incluir calentamiento" (US-29 logging) — UI + backend — M–L
- [ ] T-091: RPE per set, target RPE per exercise (US-30) — UI + backend — M
- [ ] T-092: Exercise metric types (US-31) — UI + backend — L
- [ ] T-093: Volume options (US-32) — UI + backend — M
- [ ] T-094: Personal-record catalogue (US-34) — UI + backend — M
- [ ] T-095: Exercise detail: Resumen, Historial, Cómo hacerlo (US-28) — UI — M
- [ ] T-096: Session controls (US-38) — UI + backend — M–L
- [ ] T-097: Warm-up and plate calculators (US-33) — UI + backend — M
  - **Checkpoint E6-A 👤 Plan a routine, train it and see your records (7 items):** per-set planning; training it (placeholders, types, RPE); every kind of exercise; volume settings (warm-up inclusion, double dumbbell, body weight); session controls (finish, pause, delete set, previous values); calculators; records and the exercise detail. Why here: one or two real workouts try the whole session; T-098…T-101 build on planned sets, deleted sets and records.

### Phase 2: Around the session (routines and past workouts)
- [ ] T-098: Routine folders, next workout and manual order (US-35) — UI + backend — L
- [ ] T-099: Routine tracking and "Actualizar rutina" (US-36) — UI + backend — L
- [x] T-100: Workout detail and training calendar (US-37) — UI — M–L
- [ ] T-101: Workout editing, "Guardar como rutina" and past workouts (US-37) — UI + backend — L
  - **Checkpoint E6-B 👤 Organise routines and manage past workouts (7 items):** folders incl. delete with confirmation; next workout and order; routine tracking; workout detail and calendar; edit and delete; "Guardar como rutina"; past workouts. Why here: the whole "Mis rutinas" + "Historial" journey outside the gym; "Guardar como rutina" and past workouts from a routine feed the routine tools.

### Phase 3: Food editing and closure
- [ ] T-102: Editing manual foods (US-47) — UI + backend — L
- [x] T-102b: Edit a comida (US-47, user request 2026-10-23) — UI — M
- [ ] T-103: E6 closure — PM — S
  - **Checkpoint E6-C 👤 Food editing, final regression and approval (5 items):** edit a manual food (future entries only; no edit for OFF); portions; the full label in label order; one real workout end to end; the exercise detail, workout detail, calendar and Fuerza with warm-ups on and off. Approval → `v0.6.0` and the retrospective. Why here: food editing alone gives 3 items and only the closure follows it.

Merges from the earlier 5-checkpoint draft (33 counted items → 19): leave-and-come-back no longer counted (−5); set types + RPE + planned placeholders → "train it"; warm-up inclusion + double dumbbell + body weight → "volume settings"; pause + finish/delete + previous values → "session controls"; records + exercise detail → one; folder create/rename + folder delete → "folders"; workout detail + calendar → one; edit + delete → one; "Actualizar automáticamente" folded into routine tracking.

Every checkpoint ends with "leave the app and come back: the same tab, screen and draft are restored". Internal milestones are not 👤 checkpoints:
- pgTAP + lint + `testLocalDebugUnitTest` green per task;
- `deploy-supabase` green after each migration (every task except possibly T-095 and T-100);
- production routines keep their sets after T-089 and their order after T-098; the production catalogue has the mapped metric types after T-092.

## Dependencies

```
v0.5.0 ─► T-089 ─► T-090 ─► T-091 ─► T-092 ─► T-093 ─► T-094 ─► T-095 ─► T-096 ─► T-097 ─► [E6-A 👤]
       ─► T-098 ─► T-099 ─► T-100 ─► T-101 ─► [E6-B 👤]
       ─► T-102 ─► T-102b ─► T-103 ─► [E6-C 👤] ─► v0.6.0
```
- Strictly sequential; each checkpoint gates the next task.
- Trade-off of fewer checkpoints: T-090…T-097 build on T-089's planned sets before the user tries them (nine tasks before E6-A), and the destructive folder delete (T-098) is tried only at E6-B. Mitigation: the T-089 data migration is verified on production routines (internal milestone) and host tests cover every scenario; a defect found at a checkpoint becomes a `fix(T-###)` task before the next phase.
- T-090 reuses T-089's badge and picker. T-091's target RPE uses the existing per-exercise column (ADR-50).
- T-094 needs set types and warm-up inclusion (T-090), the metric families (T-092) and the volume options (T-093). T-095 and T-100 show T-094's records.
- T-097 inserts `warm_up` sets (T-090). T-099 compares planned sets (T-089) with sessions that may have deleted sets (T-096) and changed rests. T-101's "Guardar como rutina" and "from a routine" past workouts use planned sets (T-089) and the routines of T-098.
- T-102 is independent of training; it sits last and is tested at E6-C.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| The per-set planned-set migration breaks existing routines or `start_workout_session` | High | Data migration with pgTAP proving every routine keeps its sets/reps; tried on the user's real routines at E6-A |
| Deleting a folder deletes routines the user wanted to keep | High | Confirmation naming the count (ADR-51); pgTAP proves only that folder's routines go and past sessions stay; tried on a test folder at E6-B |
| Settings-dependent aggregates diverge between the domain and SQL (warm-ups, double dumbbell, bodyweight) | High | Shared test cases in domain tests and pgTAP; both settings states tested |
| The catalogue metric mapping is wrong for some exercises (RepDB has no metric field) | Medium | Mapping listed in the T-092 PR; the user checks it at E6-A; later fixes are a new generated migration |
| Bodyweight volume depends on the latest body weight, which changes over time | Medium | Rule from `04` §4.7 (latest weight); the T-092 planner states whether past sessions use the latest or the then-current weight and asks if it is not clear |
| Per-session rests are not stored today, so "Actualizar rutina" cannot see rest changes | Medium | T-099 stores them (new migration) before building the diff |
| Live-session screen grows with every task (types, RPE, metrics, pause, calculators) and becomes hard to change | Medium | Additive set-row variants; planners extract sheets and keep `LiveSessionViewModel` slices small; E5 lesson of UI consistency (E9 guardrails) |
| The full nutrient list makes the food form very long | Medium | ux-reviewer groups it (label rows first, vitamins and minerals collapsible) without breaking the label order |
| UI inconsistencies between new sheets and existing ones (E4/E5 retrospectives) | Medium | ux-reviewer sweep per ADR-45; reuse existing sheets and components first |
| A delete or edit outside the loaded range crashes (T-086 class) | Medium | Required out-of-range test in every editing/deleting task |
| L tasks (T-089, T-092, T-098, T-099, T-101, T-102) overflow one dev run | Medium | The planner slices them (backend + domain, then UI), one dev run per slice |

## Open Questions
None blocking: Q1–Q8 were answered by the user on 2026-10-22 (`planning/09-open-questions.md`, ADR-50…ADR-53).

**Assumptions (non-blocking; applied unless the user objects at the checkpoints):**
- "Contar mancuerna doble" is the user's setting per exercise and applies to all its sessions, past and future (T-093, E6-A).
- The previous-values mode is one global training preference (T-096, E6-A).
- Pausing a session also pauses the rest countdown; "Listo" on a set while paused resumes both (ADR-56, T-097g).
- An empty folder is deleted at once, without the confirmation (T-098, E6-B).
- The workout muscle split is the share of completed sets per primary muscle group (T-100, E6-B).
- The E3 rule that an exercise's first session counts as a PR in the summary is kept (T-094, E6-A).
- A manual food's base unit (`g`/`ml`) is not editable; editing an old entry whose portion was removed falls back to the amount in base units (T-102, E6-C).
- The "Incluir calentamiento", previous-values and calculator settings live in Preferencias under an "Entrenamiento" block (placement by ux-reviewer).
- ux-reviewer aligns the set-type labels ("Al fallo" in the Gherkin vs "Fallo" in `03`) and the badge letters, proposes the metric-type labels and per-equipment hints, and words the folder-delete confirmation.
- E8 US-44 (streaks and weekly check-ins) is not changed by ADR-52; the E8 breakdown confirms it with the user.

---

# T-097h · Disc loader; derived rounding (US-33, ADR-56)

## Goal
Replace the plate calculator with a disc loader (user picks how many discs of each weight go on one side; total = bar or handle + 2 x sum). Remove "Redondeo de discos" and "Redondeo de mancuernas": rounding is derived as 2 x the smallest available plate for barbells and dumbbells alike. Bar, handle and plates come from Preferencias.

## Gherkin scenarios covered
`planning/epics/E6-training-depth.md` US-33 still carries the old "Plate calculator" and "Rounding settings" scenarios (and `04` §4.9, `03` training_preferences too): slice 0 rewrites them. Proposed scenarios (UI literals in Spanish):
- Disc loader: barbell exercise, bar 20 kg, plates 25/15 added once each -> total "100 kg"; "Cargador de discos".
- Dumbbell: handle 1 kg, one 10 kg disc -> "21 kg".
- Only enabled plates are offered; no bar selector in the sheet.
- Derived rounding: smallest enabled plate 0,5 -> warm-up weights round to multiples of 1 kg; with only 1,25 as smallest -> 2,5 kg. No rounding settings in Preferencias.
- Existing: default warm-up scheme (100 kg, 20 kg bar -> 20x10, 40x5, 60x3, 80x2) still holds.

## Flow type: full UI (+ backend migration)

## Changes
**Slice 0 (docs, PM/planner PR commit):** `planning/04-business-rules.md` §4.9 (loader rule, derived rounding, drop the "Rounding" bullet and plate greedy), `03-data-model.md` (columns, `save_preferences` signature), E6 epic US-33 Gherkin, `tasks/plan.md` assumptions.

**Slice 1 - backend (kotlin-core / supabase dev):**
- New migration `supabase/migrations/<ts>_drop_rounding_columns.sql` (later than 20261028100000, never edit merged ones): `alter table public.training_preferences drop column plate_rounding_kg, drop column dumbbell_rounding_kg` (their CHECKs go with them); `drop function public.save_preferences(<the 22-arg signature of 20261028100000>)`, recreate without `p_plate_rounding_kg` / `p_dumbbell_rounding_kg` (20 args: ... `p_bar_weight_kg, p_plate_kg, p_dumbbell_handle_kg`), same body minus those columns/conditions, revoke from public/anon, grant to authenticated. Keep `security invoker`, `search_path = ''`.
- Callers of the old signature: only the Kotlin DTO (`NutritionGoalsDto.kt`); grep `supabase/` for other uses.
- pgTAP: update `supabase/tests/training_preferences.test.sql` (columns_are line 37 without the two columns; drop the rounding assertions at lines ~132-205; positional-arg test for the new signature; keep defaults, handle range, plate list checks, cross-user isolation, anon denied; old 22-arg function no longer exists via `hasnt_function`).

**Slice 2 - core (kotlin-core-developer):**
- `shared/.../domain/model/TrainingPreferences.kt`: remove `plateRoundingKg`, `dumbbellRoundingKg` and their defaults from `TrainingPreferences` and `CalculatorSettings`; add `roundingKg` derived (= 2 x min(plateKg); fallback to 2 x smallest of catalogue if empty) as a property on `CalculatorSettings`/`TrainingPreferences`.
- `TrainingPreferencesDto.kt` (select list line 48, fields 31-42) and `NutritionGoalsDto.kt` (94-95, 130-131): drop the two fields.
- `domain/usecase`: new `DiscLoader` (pure): `total(barKg, discsPerSide: Map<Double,Int>) = bar + 2 * sum(kg*count)`; `perSide` sum; helper to format the per-side list. Replace `PlateCalculator` (and `PlateResult`, `perSideText` if unused; `Calculators.kt`) once the UI moves - delete it and `PlateCalculatorTest`. `WarmUpCalculator` unchanged (still takes `roundingKg`).
- `CalculatorDraft.kt` (composeApp/feature/workout): `CalculatorKind.Plates` becomes `Discs`; draft holds `discs: Map<Double,Int>` (replaces `weightText`/`barKg` for this kind; warm-up keeps `weightText`), `CalculatorUiState` exposes `roundingKg` derived and `totalKg`; bar = Preferencias bar, handle for dumbbells (`barFor` loses the chosen-bar path; `CalculatorBase.forEquipment`). SavedStateHandle persist/restore of the disc counts (encode as string "25:1,15:2" or two arrays); remove `onBar` from `CalculatorActions`, `CalculatorBarChanged` in `LiveSessionEvent` and `RoutineBuilderEvent`, and the matching ViewModel branches (`LiveSessionViewModel` ~393-397, `RoutineBuilderViewModel` ~150-153); add `DiscCountChanged(weightKg, count)` events.
- `PreferencesDraft`, `PreferencesViewModel` events `SetPlateRounding`/`SetDumbbellRounding`, `CalculatorSettingsEdits.kt` (`withPlateRounding`, `withDumbbellRounding`, `MIN/MAX_ROUNDING_KG` stay only if the handle stepper still uses them) removed.
- Plates still need at least one enabled (existing rule).

**Slice 3 - UI (compose-ui-developer):**
- Rename/rewrite `PlateCalculatorSheet.kt` as `DiscLoaderSheet`: title "Cargador de discos"; one row per enabled plate (heaviest first) using the existing `NumberStepper` (design system) with count 0..N, labelled "<kg> kg"; total shown with `AppTheme.typography.headlineMetric` ("Total: 100 kg"); a line "Barra 20 kg" / "Mango: 1 kg" via existing `BarLabel`; pinned `SecondaryButton` "Cerrar". No weight field, no bar selector (frame: new `showWeightField = false` or a separate frame param; `barSelectable` false). Live region on the total for accessibility (as `PlateResults`).
- `CalculatorSheetFrame` (`CalculatorSheetParts.kt`): callers are `PlateCalculatorSheet`, `WarmUpCalculatorSheet`, routine builder sheets (`RoutineBuilderCalculatorSheets.kt`) and the live `LiveCalculator`; the internal `BarSelector` and `onBar` become unused and are deleted.
- `PreferencesCalculatorsBlock.kt`: remove the two `RoundingRow`s and their strings; keep handle stepper, bar selector, plate chips; show a read-only hint line "Redondeo: X kg (el doble del disco más pequeño)" with testTag `preferences_derived_rounding`; update `help_rounding_*` text.
- `strings.xml`: remove `preferences_calc_plate_rounding*`, `preferences_calc_dumbbell_rounding*`, `live_calc_not_exact`, `live_calc_plates_*` if unused; add loader strings; rewrite `help_plate_calc_body` (loader, same discs on both sides) and `help_rounding_body` (derived).
- Menu label in `ExerciseMenu.kt` and routine builder (`RoutineBuilderRow.kt` `CalculatorMenuRows`): "Calculadora de discos" -> "Cargador de discos" if the product answer is yes (see questions).

## Contracts
- `training_preferences` after migration: `user_id, warm_up_included, updated_at, previous_values_mode, bar_weight_kg, plate_kg, dumbbell_handle_kg`.
- `save_preferences(p_display_name, p_sex, p_birth_date, p_height_cm, p_activity_level, p_strategy, p_calorie_adjustment, p_target_kcal, p_protein_pct, p_carbs_pct, p_fat_pct, p_weight_kg, p_measured_at, p_day_start, p_day_end, p_warm_up_included, p_previous_values_mode, p_bar_weight_kg, p_plate_kg, p_dumbbell_handle_kg)`; all trailing ones nullable-defaulted.
- Domain: `roundingKg = 2 * plateKg.min()`; `DiscLoader.total = barKg + 2 * sum(kg * count)`.
- Edge cases: plates {25 only} -> rounding 50 kg (warm-up collapses to the bar; accepted, see question 3); the dumbbell warm-up now rounds like barbells (default plates -> 1 kg, was 2 kg).

## Tests
- Unit (shared): `DiscLoaderTest` (100 kg = 20 bar + 25 + 15 per side; 21 kg = 1 handle + 10; no discs -> bar only; repeated weights 25x2; fractional 1,25 -> 2,5 total step; handle vs bar); `CalculatorSettings.roundingKg` (default plates -> 1,0; without 0,5 and 1 -> 2,0; only 25 -> 50; unordered input); `WarmUpCalculatorTest` examples from `04` §4.9 still green with derived rounding (100 kg -> 20/40/60/80); `TrainingPreferencesDto` / repository tests updated (no rounding fields; request body for `save_preferences` has 20 named params); delete `PlateCalculatorTest`.
- Unit (composeApp): `CalculatorSheetViewModelTest`, `RoutineBuilderCalculatorsViewModelTest`, `PreferencesViewModelTest` updated: disc counts change total, counts never negative / capped, disabling a plate in Preferencias drops it, bar is never user-selectable, dumbbell uses the handle, Preferencias save sends no rounding.
- pgTAP: as in slice 1 (RLS/cross-user isolation retained; migration applies on a row that had custom rounding values: row survives with other columns intact).
- deno: none (no Edge Function).
- testTags: `<prefix>_disc_loader_sheet`, `<prefix>_disc_loader_total`, `<prefix>_disc_loader_results`, per plate `<prefix>_disc_loader_plate_<kg with _>` (stepper uses `testTagPrefix`), `<prefix>_disc_loader_close`, `<prefix>_disc_loader_help` (existing helper `plate_calc_help` renamed), `<prefix>_disc_loader_bar_label` / `_handle_label`; `preferences_derived_rounding`; remove `preferences_plate_rounding*`, `preferences_dumbbell_rounding*`, `*_plate_calc_bar`.
- Host (Robolectric, `androidHostTest`): update `LiveSessionCalculatorsTest` and `PreferencesTrainingCardTest`; scenarios: loader total from steppers (barbell and dumbbell), only enabled plates offered, no bar selector, Preferencias without rounding rows and with the derived rounding line, warm-up sheet rounds by the derived value after switching off the 0,5 plate, routine-builder menu opens the loader.
- Restoration (ADR-40): `StateRestorationTester` host test of the open loader with counts set (live session and routine builder), plus ViewModel recreated from the same `SavedStateHandle` restoring the counts and the position; old saved `live_calc_kind = Plates` / bar key must restore as `Discs` or null without crashing.

## Implementation order
0. Docs: `04` §4.9, `03`, epic US-33 Gherkin (no code).
1. Backend: migration + pgTAP; `supabase db reset` + pgTAP green. Deploy order note: the new Kotlin must ship with or after the migration; `deploy-supabase` applies it on merge, and an app calling the 22-arg RPC would fail, so migration and app go in the same PR (1 user, acceptable).
2. Core: models, DTOs, `DiscLoader`, derived rounding, draft/ViewModels in both screens, Preferencias VM; tests; project builds with the old sheet temporarily adapted or the UI slice in the same PR branch (commits kept green: do step 2 and 3 together if the old `PlateCalculatorSheet` cannot compile without `onBar`).
3. UI: `DiscLoaderSheet`, frame cleanup, Preferencias block, strings, help texts, catalog entry if any (check `feature/catalog` for the plate sheet); Robolectric + restoration tests; `scripts/check-design-tokens.sh`, detekt/lint green.
Each step leaves tests green; total estimate M-L (~400 lines), one PR, developers: kotlin-core-developer for 1-2, compose-ui-developer for 3.

## Risks and open questions
- Callers affected: `LiveSessionViewModel`/`Screen`/`UiState`, `RoutineBuilderViewModel`/`UiState`/`CalculatorSheets`/`Row`, `ExerciseMenu`, `LiveExerciseCard`, `PreferencesDraft/UiState/ViewModel/CalculatorsBlock/CalculatorSettingsEdits`, DTOs, `WarmUpCalculatorSheet` (rounding source and bar selector removal).
- Production rows: existing rounding values are discarded; users with custom rounding see the derived value (behaviour change intended by ADR-56).
- Questions not answered by `planning/` (no ADR text on them):
  1. Initial disc state: empty (bar only) or prefilled with the greedy plates for the exercise's weight (old calculator behaviour)? Proposed default: empty, user loads discs.
  2. Does the loader only compute the total (read-only, "Cerrar", as today) or can it apply the total to a set / the planned weight? Proposed default: read-only.
  3. Rounding when the user enables a single large plate (e.g. only 25 -> 50 kg step): accept as written, or enforce a minimum/cap? Proposed default: accept, as ADR-56 states.
  4. Maximum discs of one weight per side: proposed 10 (stepper bound).
  5. Rename the menu entry "Calculadora de discos" to "Cargador de discos"? Proposed default: yes (title and menu).
- None blocking if the proposed defaults are accepted; 1 and 2 are visible-behaviour choices the user may want to confirm.

## UX notes
**Verdict: PASSED** with conditions (defaults accepted: empty start, read-only + "Cerrar", max 10 per weight per side, menu "Cargador de discos").

Conditions for the developer:
1. Reuse only: `AppBottomSheet`, `NumberStepper`, `SecondaryButton`, `HelpButton`, `BarLabel`, tokens. No new component, no hardcoded dp/color/font. If the plate row needs a new composable (label + stepper), put it in `designsystem/` as `DiscStepperRow`, not in the screen.
2. Layout of the sheet (one-handed): header row (title `titleLarge` "Cargador de discos" + `HelpButton`), then the bar/handle line (`BarLabel`), then the plate rows (heaviest first, 12 dp apart, label "<kg> kg" `bodyLarge` left, stepper right; each row >= 48 dp, stepper buttons >= 48 dp). The total (`headlineMetric`, tabular numerals, cyan as a training value) and the "Cerrar" `SecondaryButton` (full width, 52 dp) are pinned at the bottom of the sheet in the thumb zone; only the plate list scrolls (up to ~8 plates must not push the total or "Cerrar" off screen). 16 dp margins, 24 dp between the list and the pinned footer.
3. Accessibility: each stepper `stateDescription` "<n> discos de <kg> kg por lado" (string resource with plural), decrement/increment `contentDescription` "Quitar disco de <kg> kg" / "Añadir disco de <kg> kg"; decrement disabled at 0, increment disabled at 10 (never negative / above cap). Total is a polite live region with text "Total: 100 kg" and the per-side sum in a secondary `bodyMedium` `textSecondary` line ("Por lado: 40 kg"; AA contrast). Weight decimals use the app's Spanish format (1,25).
4. Guardar: the loader is a read-only calculator whose counts are a transient draft that is never persisted as data, so no "Guardar" is needed; X/back/scrim/swipe/"Cerrar" all just close (nothing applied, nothing saved). The count draft must still survive recreation (ADR-40, already in plan). Empty state: no enabled plate is impossible (existing rule), so only bar-only total is shown when all counts are 0.
5. Preferencias: the derived-rounding hint is a static `bodyMedium` `textSecondary` line under the plate chips, inside the "Entrenamiento" block; no button. Keep wording "Redondeo" identical in the hint, help text and warm-up sheet (rule §8 wording).
6. Wording sweep (§8): rename every "Calculadora de discos" (menu in `ExerciseMenu.kt`, routine builder `CalculatorMenuRows`, help titles, strings, content descriptions, catalog entry, tests) to "Cargador de discos"; grep `strings.xml` and `feature/` for the old text and for "plate"/"discos" literals so none remain. The warm-up calculator keeps its own name.
7. Tablet (>= 600 dp): the sheet content is capped at `FormContentMaxWidth` (480 dp) and centred; same single column.
8. Fonts at 130%: plate label and stepper may wrap/stack, never clip the total.

Confirmed testTags (snake_case, `<prefix>` = `live_calc` / `routine_calc` as today):
- `<prefix>_disc_loader_sheet`, `<prefix>_disc_loader_help` (+ `_sheet`, `_sheet_close` from `HelpButton`)
- `<prefix>_disc_loader_bar_label` / `<prefix>_disc_loader_handle_label`
- `<prefix>_disc_loader_plate_<kg_with_underscore>` (row), with `testTagPrefix` of the stepper giving `..._minus` / `..._plus` / `..._value`
- `<prefix>_disc_loader_per_side`, `<prefix>_disc_loader_total`, `<prefix>_disc_loader_results`
- `<prefix>_disc_loader_close`
- `preferences_derived_rounding`
- Menu rows keep their existing tags (value renamed only if the tag contains "plate_calc": use `*_disc_loader` for new ones).
