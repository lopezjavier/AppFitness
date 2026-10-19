# Implementation Plan: E3 · Training core → `v0.3.0`

## Overview
Plan routines calmly and run them at the gym with a sweat-proof live session: a Spanish exercise catalog with the bundled RepDB assets, a routine builder with supersets, quick set logging with the screen always on, previous performance (ghost data), a non-blocking rest timer, session history and strength-progress charts reached from Perfil. E3 is done when the user logs a full real workout (4-exercise routine with a superset) on the `prodDebug` APK, the five training tables and the three RPCs have pgTAP tests, a host test covers a full session, and the schema is forward-compatible with E6 (ADR-30).

Sources: `planning/epics/E3-training-core.md` (US-09…US-15), `planning/03-data-model.md` §1, §5, §6, `planning/04-business-rules.md` §4–§5, `planning/02-architecture.md`, `planning/05-design-system.md` (`SetRow`, `RestTimerBar`, `NumberInput`, `ExerciseAnimation`, `ghostData`, `athleticCyan`), ADR-01, ADR-09, ADR-29, ADR-30, ADR-36, ADR-37, mockups `design/stitch/live-workout/screen.png` and the "Fuerza y 1RM estimado" part of `design/stitch/progress/screen.png`. Tasks: `tasks/todo.md` (T-042…T-049). Earlier plans are in the git history.

Sizing: one task per story as a vertical slice. US-11 is split into backend (T-044) and UI (T-045) because both halves are heavy and reviewable apart; US-12 and US-13 are merged (T-046) because both layer onto the live screen. 6 implementation tasks + closure, 2 👤 checkpoints.

## Architecture Decisions
- **Forward-compatible schema (ADR-30).** Each migration creates its tables with every *(E6)* column and enum and its default. `routine_folders` is created in T-043 (it is the target of `routines.folder_id`), so E6 needs no migration. The live set row is a polymorphic entry keyed by `metric_type`; E3 maps only `weight_reps` / `working`.
- **One migration per task:** T-042 `exercises` (+ catalog rows per Q1), T-043 `routine_folders`/`routines`/`routine_exercises` + `save_routine(jsonb)`, T-044 `workout_sessions`/`workout_sets` + `exercise_history`, T-046 `last_performance`, T-048 `weekly_muscle_volume`; T-047 only if the planner puts the list aggregates in SQL.
- **`exercise_history` ships in T-044, not T-048:** the summary's "PR" (US-11) and the history's PRs (US-14) need the previous best estimated 1RM (§4.5). Its pgTAP uses the §4.1 examples, the same cases as `EstimateOneRepMax`.
- **Account deletion keeps working (ADR-29):** `exercises.created_by` cascades; every new user-owned table cascades from `auth.users`; pgTAP in T-042/T-043/T-044 deletes a user with training data.
- **RepDB bundle (ADR-30):** not committed; `.gitignore` excludes it. The epic path `app/assets/exercises/` predates ADR-19, so the T-042 planner picks the module (Android assets in `androidApp`, or Compose resources in `composeApp`) and updates the docs. CI builds and tests without it (synthetic fixture); `scripts/deliver-apk.py` refuses to build without it so a delivered APK always carries it. Search is in memory in `shared` (accent- and case-insensitive).
- **Reusable exercise picker:** built in T-042 as a destination returning the selected exercise; reused by the routine builder (T-043) and "Añadir ejercicio" in the session (T-045). Until T-043, a provisional Entreno entry reaches it.
- **Per-screen actions (ADR-36):** "Nueva rutina" (T-043), "Empezar" / "Empezar entreno vacío" (T-045), "Historial" (T-047) live on the Entreno main view; "Fuerza" is the first statistics entry on the Perfil root (ADR-37, T-048). This consumes the E1 "FAB question" note.
- **Live session:** session created in the DB on start, every set upserted by `(session_id, exercise_position, set_index)` when checked; offline sets queue in the ViewModel's memory only while the session is open (ADR-01 exception). `keepScreenOn` via `DisposableEffect`, never a `WakeLock`. Haptics through the `Haptics` port; the rest-end alert through a new sound port in `androidMain`.
- **Rest timer:** state from an `endsAt` instant and the injected `Clock` (§4.4), tested with virtual time; the alert fires only with the app visible (no foreground service or notifications in Phase 1).
- **Existing design-system components are reused:** `SetRow`, `SetCompletionCheckbox`, `NumberInput`, `RestTimerBar`, `AppBottomSheet`, `TrendPill`, `EmptyState`/`ErrorState`. New ones are built inside the first task that needs them: `ExerciseAnimation` (T-042), a reorderable-list helper if needed (T-043), the Vico chart wrapper (T-048). Before changing a shared component, check every caller (E2 lesson, T-040).
- **Charts:** Vico (multiplatform), added in T-048 at the latest stable version, athleticCyan.
- **Notes for later epics:** the "E3 (food diary)" and "E2 (food diary)" notes in `tasks/todo.md` predate ADR-31's renumbering and target the food diary, now **E4**; left in place.

## Task List

### Phase 1: Plan and run a workout (US-09…US-13)
- [ ] T-042: Exercise catalog: RepDB assets, search and filters, detail, custom exercises (US-09) — UI + backend
- [ ] T-043: Routine builder and "Mis rutinas" (US-10) — UI + backend
- [ ] T-044: Workout sessions backend and training domain (US-11 backend half + `exercise_history`) — backend
- [ ] T-045: Live workout session (US-11 UI half) — UI
- [ ] T-046: Ghost data and rest timer in the live session (US-12 + US-13) — UI + backend (`last_performance`)

### Checkpoint E3-A 👤: a real workout on the phone
- [ ] Build a routine with a superset from the catalog, run it at the gym (screen on, LISTO, ghost data, rest bar, resume after closing, offline set), finish with the summary. Placed here because the live session is the riskiest user-visible part of E3, and feedback can still shape history and charts.

### Phase 2: Review and progress (US-14, US-15)
- [ ] T-047: Session history (US-14) — UI (+ optional SQL aggregates)
- [ ] T-048: Strength progress: "Fuerza" screen, exercise charts, weekly muscle volume (US-15) — UI + backend (`weekly_muscle_volume`)

### Checkpoint E3-B 👤: epic closure
- [ ] T-049: E3 closure — history (edit, delete, pagination) and the Fuerza screen with real session data; tag `v0.3.0` and retrospective.

Internal milestones (not 👤): `deploy-supabase` green after each migration (T-042, T-043, T-044, T-046, T-048); production catalog row count matches the bundle (T-042); pgTAP for every training table and RPC green in `supabase-ci`.

## Dependencies

```
E2 (v0.2.0) + Q1 + RepDB bundle ─► T-042 ─► T-043 ─► T-044 ─► T-045 ─► T-046 ─► [E3-A 👤] ─► T-047 ─► T-048 ─► [E3-B 👤] ─► T-049
                                                  Q2 ─┘──────────┘   Q3 ─┘
```
- Strictly sequential: each task builds on the previous one's tables, repositories and screens.
- Inside T-042, T-043 and T-046, backend (migration + pgTAP) and frontend can run in parallel once the planner fixes the repository contract. T-044's domain use cases (1RM, volume) can start before Q2 is answered; only `DetectPersonalRecords` waits for it.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| RepDB data in a public repo (catalog names/keys in a migration) vs. the in-app-only licence | High | Q1 asked before T-042; no RepDB file is committed; the fixture is synthetic. |
| The bundle is missing on a build machine (CI, a fresh clone) | Medium | Build and tests tolerate its absence; `deliver-apk.py` blocks delivery without it; path documented in `08-environment-setup.md`. |
| APK size grows by ≈16 MB; loading `library.json` on the main thread | Medium | Parse once off the main thread and cache in memory; thumbnails loaded lazily (Coil or bitmap decode, planner's choice). |
| Live session loses data (offline, process death, double in-progress) | High | Upsert per set, partial unique index on `in_progress`, in-memory retry queue with "pendiente de guardar", resume test from a fresh process state. |
| Timer drift in the background; timer and ghost tests flaky | Medium | `endsAt` + injected `Clock`, virtual-time tests; no real delays in host tests. |
| `keepScreenOn` leaking after the session | Medium | `DisposableEffect` scoped to the session screen; host test for on/off; checked at E3-A. |
| Drag-and-drop reorder in Compose Multiplatform (no stock component) | Medium | Small own implementation or a stable multiplatform library; "Subir"/"Bajar" as the accessible fallback and the main test path. |
| Account deletion fails once training tables reference `auth.users` or custom exercises | Medium | Cascade FKs; pgTAP deletes a user with training data in T-042…T-044. |
| Large slices (T-042, T-043, T-045, T-046, T-048 are L) | Medium | If a plan exceeds ~15 non-trivial files, the planner splits it into backend then UI (never per component). |
| Haptics, the rest alert sound and the always-on screen cannot be fully checked on the host | Low | Ports with fakes in host tests; manual check at E3-A. |

## Open Questions
- **Q1 · RESOLVED (ADR-38) · RepDB catalog rows.** Option (b): the migration stores only `key`, muscle group and equipment; `name` is nullable for catalog rows and the app shows the Spanish name from the bundle. No RepDB text or asset is committed (bundle gitignored, pgTAP fixtures synthetic). The user's bundle is at `C:/Users/javie/Documents/AppFitness/hevy-zip/Biblioteca_Ejercicios_Free` (includes `ATRIBUCION_RepDB.md`, `free.es.json`, `images/`); T-042 copies what it needs from there.
- **Q2 · OPEN, awaiting the user's answer · First session of an exercise.** "PR" = personal record: a session whose estimated 1RM for an exercise exceeds the best of all previous sessions (§4.5), shown as a "PR" badge in the summary and history. Question: with no previous session, is the first-ever session marked "PR"? Planner default if unanswered: no (a record needs something to beat).
- **Q3 · RESOLVED (ADR-38) · Superset rest.** The user configures, per superset: number of rounds (sets), reps per exercise, the rest between exercises within a round (e.g. 1 min biceps → triceps) and the rest between rounds (e.g. 3 min). Mapping onto `routine_exercises.rest_seconds` (no schema change): for non-last members it is the transition rest after that exercise; for the last member it is the rest between rounds. All members share the same set count. T-043 builder UI and T-046 timer implement it.
- **Q4 · RESOLVED · "Borrar cuenta" wording.** No change: it is a testing aid; the real account is never deleted.
