# Implementation Plan: E6 · Training depth → `v0.6.0`

## Overview
Turn the Phase 1 workout into a complete gym experience. Routines are planned set by set. Sets carry a type and an RPE, and every exercise metric is logged (bodyweight, duration, distance…) with realistic volume. Records are detected and shown in the summary, a full exercise detail and the workout detail. The live session gains pause, set deletion, a previous-values mode and the warm-up/plate calculators. Routines get folders, a manual order, a next workout and "Actualizar rutina". Finished workouts get a detail, a calendar of trained days, editing, deletion, "Guardar como rutina" and past-workout logging. The epic also adds editing manual foods with the full Spanish label (US-47).

Sources: `planning/epics/E6-training-depth.md` (US-28…US-38, US-47), `planning/01-phase1-scope.md` FR-2.14, FR-4.8 and §4.1, `planning/03-data-model.md` §3/§5/§6, `planning/04-business-rules.md` §2 and §4 (incl. §4.9 calculators), `planning/05-design-system.md` (§6 `SetRow`, `ReorderableColumn`, `MonthCalendarGrid`, `SwipeToDeleteRow`; §8 interaction rules), ADR-30, ADR-32, ADR-38, ADR-39, ADR-40, ADR-43, ADR-45, ADR-47, ADR-50…ADR-53, and the E5 retrospective actions. Tasks: `tasks/todo.md` (T-089…T-103). Earlier plans are in the git history.

Out of scope (epic file and `01` §4): advanced statistics, record charts and non-weight charts (E7); the program generator and trainer; microcycles; cardio plans; social features; background rest alerts (Phase 2). Deferred by the user at this breakdown (ADR-52, text kept in `01` §4.1): the live "¡Nuevo récord!" notification and its levels; the calendar streak, rest days and rewards; copying a workout. Editing Open Food Facts foods is not allowed (ADR-53).

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
- [ ] T-100: Workout detail and training calendar (US-37) — UI — M–L
- [ ] T-101: Workout editing, "Guardar como rutina" and past workouts (US-37) — UI + backend — L
  - **Checkpoint E6-B 👤 Organise routines and manage past workouts (7 items):** folders incl. delete with confirmation; next workout and order; routine tracking; workout detail and calendar; edit and delete; "Guardar como rutina"; past workouts. Why here: the whole "Mis rutinas" + "Historial" journey outside the gym; "Guardar como rutina" and past workouts from a routine feed the routine tools.

### Phase 3: Food editing and closure
- [ ] T-102: Editing manual foods (US-47) — UI + backend — L
- [ ] T-102b: Edit a comida (US-47, user request 2026-10-23) — UI — M
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
| Live-session screen grows with every task (types, RPE, metrics, pause, calculators) and becomes hard to change | Medium | Additive set-row variants; planners extract sheets and keep `LiveSessionViewModel` slices small; E5 lesson of UI consistency (E8 guardrails) |
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
- E7 US-44 (streaks and weekly check-ins) is not changed by ADR-52; the E7 breakdown confirms it with the user.
