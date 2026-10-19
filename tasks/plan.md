# Implementation Plan: E3 · Training core → `v0.3.0`

## Overview
Plan routines calmly and run them at the gym with a sweat-proof live session: a Spanish exercise catalog with the bundled RepDB assets, a routine builder with supersets, quick set logging with the screen always on, previous performance (ghost data), a non-blocking rest timer, session history and strength-progress charts reached from Perfil.

Status (2026-10-20): the build phase is done (T-042…T-048b merged). The user tested the whole epic in one go (Checkpoint E3-A was merged into the end-of-epic test by user decision) and reported findings. This plan now covers the **corrective phase**: five tasks, one 👤 checkpoint per user-visible functionality, then the final regression and approval (T-049 + Checkpoint E3-B) and the `v0.3.0` tag.

Sources: `planning/epics/E3-training-core.md` (US-09…US-15, updated 2026-10-20 for routine order, removal of the empty workout, "Finalizar entreno" in the ⋮ menu, the in-progress card buttons, the start-conflict message, the ~1 s rest sound, the muscle-volume start and the app state restoration feature), `planning/02-architecture.md`, `planning/04-business-rules.md` §4.4, ADR-24, ADR-30, ADR-36, ADR-37, ADR-38. Tasks: `tasks/todo.md` (T-042…T-054, T-049 last). Earlier plans are in the git history.

Out of scope for the corrective phase: per-set weight/reps/set type in routines (moved to E6 US-29) and rest alerts in the background (Phase 2, `01-phase1-scope.md`).

## Architecture Decisions
- **State restoration is an app-wide property (new ADR-40, written in T-050).** The selected tab, each tab's back stack and every unsaved form survive a background/foreground cycle and process death. Fixed centrally (root navigation, per-tab `saveState`/`restoreState`, `@Serializable` routes, `SavedStateHandle`/`rememberSaveable` for drafts), not per screen; auth routing wins over restoration. `02-architecture.md` and the planner agent gain the rule "every new screen with input ships with a restoration test", so later epics inherit it. T-050 runs first so T-051…T-054 build on it.
- **Exercise names:** catalog rows have no DB name (ADR-38) and take it from the bundle; custom rows use their DB `name`. One resolver used by every screen (T-051).
- **No empty workout:** sessions always start from a routine; the client path is removed, the DB keeps `workout_sessions.routine_id` nullable for E6 (ADR-30). No migration.
- **Start conflict:** the partial unique index on `in_progress` (T-044) is mapped to a typed domain error in the repository and shown as "Ya hay un entreno en curso"; the UI never matches error strings.
- **Rest alert sound:** the existing `AlertSound` port (T-046) plays ~1 s with the vibration, foreground only, silent when muted (`04` §4.4).
- **Muscle volume range:** from the ISO week of the first completed session, capped at 8 weeks; in the client unless the planner moves it into SQL (then a new migration, never an edit of the merged one).
- **Shared components:** before changing `NumberInput`, `AppBottomSheet` or `SetRow`, check every caller (E2 lesson, T-040).

## Task List

### Phase 1 (done): Plan and run a workout (US-09…US-13)
- [x] T-042 exercise catalog · T-043 routine builder · T-044 sessions backend · T-045 live session · T-046 ghost data and rest timer

### Phase 2 (done): Review and progress (US-14, US-15)
- [x] T-047 session history · T-048 strength progress · T-048b weekly muscle volume

### Phase 3: Corrective tasks (user feedback 2026-10-20)
- [ ] T-050: App-wide state restoration (tab, screen, drafts, process death) — UI
  - **Checkpoint E3-C 👤 State restoration:** sub-screens on every tab and a half-built "Nueva rutina" survive leaving the app, also with "No mantener actividades"; a running session still reopens.
- [ ] T-051: Routines and catalog fixes (custom name, detail on thumbnail/name tap, A→Z order, no "Empezar entreno vacío", 10 s rest steps "1 min 10 s") — UI
  - **Checkpoint E3-D 👤 Routines and catalog:** re-test each of the five fixes plus a quick routine regression.
- [ ] T-052: Live session fixes ("Finalizar entreno" in ⋮, card play/trash, "Ya hay un entreno en curso", ~1 s rest sound) — UI
  - **Checkpoint E3-E 👤 Live session:** re-test the four fixes plus a quick gym regression.
- [ ] T-053: Fix the "Corregir serie" dialog — UI
  - **Checkpoint E3-F 👤 History:** edit reps in "Corregir serie", labels horizontal, volume updates.
- [ ] T-054: Weekly muscle volume starts at the first recorded session — UI (+ optional SQL)
  - **Checkpoint E3-G 👤 Strength:** chart starts at the week of the first session; quick Fuerza regression.

Every checkpoint ends with "leave the app and come back: the same tab, screen and draft are restored".

### Closure
- [ ] T-049: E3 closure (final regression and approval)
  - **Checkpoint E3-B 👤 final regression:** one real workout end to end on the delivered APK; approval → `v0.3.0` tag and retrospective.

Internal milestones (not 👤): lint + `testLocalDebugUnitTest` green per task; `deploy-supabase` green only if T-054 adds a migration.

## Dependencies

```
T-048b ─► T-050 ─► [E3-C 👤] ─► T-051 (Q5, Q6) ─► [E3-D 👤] ─► T-052 ─► [E3-E 👤] ─► T-053 ─► [E3-F 👤] ─► T-054 ─► [E3-G 👤] ─► T-049 ─► [E3-B 👤] ─► v0.3.0
```
- Strictly sequential: each checkpoint gates the next task (the user's feedback can still change it), and T-050 goes first so every later fix inherits restoration.
- Q5 and Q6 are answered (see below).

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| State restoration touches the root navigation and auth routing of the whole app | High | Root cause first (in the PR body); `StateRestorationTester` tests on the scaffold; auth-wins rule tested; the planner may slice T-050 (navigation, then drafts). |
| Restored drafts saved by accident, or stale drafts reappearing after a save | Medium | Drafts stay unsaved until the explicit save; cleared on save/discard; tested per form. |
| Process death cannot be fully reproduced on the host | Medium | `SavedStateHandle` recreation in ViewModel tests; manual check with "No mantener actividades" at E3-C. |
| Removing the empty-workout path breaks tests or the in-progress resume | Low | Remove with its tests; `WorkoutTabRouteTest` and resume tests stay green. |
| Fixing the edit dialog through a shared component regresses other screens | Medium | Check every caller of `NumberInput`/`AppBottomSheet`; screen tests at 360 dp and font scale 2.0. |
| The alert sound is inaudible or too long on real devices | Low | ~1 s asset, emulator check by the developer, re-test at E3-E. |

## Open Questions
- **Q1–Q4 · RESOLVED** (ADR-38 and the user, 2026-10-19): RepDB rows without names; first session of an exercise is a "PR"; superset rest per member; "Borrar cuenta" wording unchanged.
- **Q5 · RESOLVED 2026-10-20 (user): the "Nueva rutina" exercise picker is unchanged (tapping a row adds the exercise); in an opened routine and wherever an exercise is listed outside the picker, tapping the thumbnail or name opens the detail. No checkbox or "+" is added.** *(was BLOCKING)* — **Q5 (T-051) · Opening the exercise detail from the picker.** Today tapping a picker row selects the exercise. In the builder and the session cards a thumbnail/name tap will open the detail. In the picker, should (a) the thumbnail open the detail and the rest of the row select, (b) both thumbnail and name open the detail with a separate select control, or (c) the picker stay as it is (detail only from the builder and session)?
- **Q6 · RESOLVED 2026-10-20 (user): under 60 s "50 s"; from 60 s "1 min", "1 min 10 s", "2 min" (never "2 min 0 s").** *(was BLOCKING)* — **Q6 (T-051) · Rest format edge cases.** With "1 min 10 s" above 60 s: does exactly 60 s read "60 s" or "1 min"? Do whole minutes read "2 min" or "2 min 0 s"?
