# Implementation Plan: E8 · Training insights → `v0.8.0`

> **Status:** restructured on 2026-10-25 per the user's decisions (three implementation tasks, one 👤 checkpoint, cheapest pipeline). E8 Q1–Q3 were answered by the user on 2026-10-25 (see Open Questions).

## Overview
Show real progress from the training data E3 and E6 already store: per-exercise charts that mark records, add an all-time frame and follow every metric family (US-39); a full "Estadísticas" screen with key values, the set-count chart, sets per muscle group and the main exercises over any frame (US-40); and the muscle distribution as a radar (current vs previous period) and a body map of the last 7 days with a per-muscle detail (US-41). The first task also closes the non-blocking review suggestions still open from E7.

Sources: `planning/epics/E8-training-insights.md` (US-39…US-41), `planning/01-phase1-scope.md` FR-4.9 and FR-5.1, `planning/02-architecture.md` (Vico charts), `planning/03-data-model.md` §5 (`exercises.secondary_muscles`, `workout_sessions`, `workout_sets`) and §6 (`exercise_session_records`, `personal_records`, `weekly_muscle_volume`), `planning/04-business-rules.md` §4.2, §4.6–§4.8, §4.10, §5, `planning/05-design-system.md` §5–§6, ADR-30, ADR-37, ADR-52, ADR-56, ADR-66, ADR-67. Tasks: `tasks/todo.md` (T-113…T-116). Earlier plans are in the git history.

Out of scope (epic file, `01` §4, ADR-66, ADR-67): Monthly Report, Year in Review, streaks and weekly check-ins (Phase 2); the program generator / trainer; social features and cardio plans; any release build, R8 or keystore work; every item of the E9 UI-refinement table and the ux-reviewer rewrite (E9's first task).

## Architecture Decisions
- **Three implementation tasks, cheapest pipeline (user decision, 2026-10-25).** T-113 = E7 carry-over cleanup + US-39 charts; T-114 = statistics backend for US-40/US-41; T-115 = the whole "Estadísticas" screen with the radar and the body map (US-40 + US-41 UI). Each runs the standard pipeline once, with no extra slices. T-113 (about 28 files, many one-line cleanup changes) and T-115 (about 22 files) exceed the ~15-file guideline; they stay single tasks by the user's decision, since each extra task costs 6–7 hand-offs. If pr-reviewer finds T-115 too large to review, the fallback (moving `RadarChart`/`BodyHeatmap` to a follow-up) is decided then, not up front.
- **No training-table migration (ADR-30).** Everything reads `workout_sessions`, `workout_sets` and `exercises` (`metric_type`, `secondary_muscles`, `per_hand`) as they are. The only migrations are a function replacement (`save_meal_defaults`, T-113) and the four statistics RPCs (T-114).
- **US-39 reuses `exercise_session_records`.** It already returns, per completed session and warm-up aware, every family (1RM, session volume, duration, distance, steps, floors). The chart metric follows `metric_type` through one pure domain function; record markers reuse `PersonalRecordCatalogue.beatenBy` over the whole history (the same rule as the session summary, `04` §4.5/§4.8). `exercise_history` is unchanged; `03` §6 records that US-39 adds no RPC (the epic's technical note "extends the `exercise_history` RPC" is an implementation detail superseded here).
- **Statistics RPC signatures (fixed here, written into `03` §6 by T-114).** All `security invoker`, `stable`, `authenticated` only; the shared filter of `exercise_history` (caller's `completed` sessions, completed sets, warm-up only when `warm_up_included()`, UTC date of `started_at` in `[p_from, p_to]`, nulls = unbounded):
  - `training_summary(p_from date default null, p_to date default null) returns table(workouts bigint, sets bigint, volume numeric, duration_s bigint)`: one row; volume as `exercise_session_records` (`set_volume × volume_factor`, latest body weight for bodyweight types); duration per `04` §4.10.
  - `weekly_set_counts(p_from date default null, p_to date default null) returns table(week_start date, sets bigint)`: Monday-based UTC weeks, only weeks with data.
  - `muscle_group_sets(p_from date default null, p_to date default null) returns table(muscle_group muscle_group, sets numeric)`: primary 1, each distinct secondary 0,5.
  - `exercise_set_counts(p_from date default null, p_to date default null, p_muscle_group muscle_group default null) returns table(exercise_id uuid, sets numeric, sessions bigint)`: main exercises, or the weighted per-muscle detail when a group is given.
- **Windows live in the domain.** Frame → `[from, to]`, the previous equal-length window, week→month bucketing and the last-7-days window are pure, tested domain helpers (T-114), so the answers to E8 Q2 can change without touching SQL.
- **One statistics destination (ADR-37).** "Estadísticas" is a full-screen destination from the Perfil root's statistics section; with E8 Q1's proposed default the muscle distribution sections live on the same screen. Main-exercise rows open the existing exercise progress route.
- **New design-system components inside the story that needs them.** `RadarChart` and `BodyHeatmap` are built in T-115 with catalog entries and `05` §6 rows; bars and tiles reuse existing components and Vico.
- **Shared examples.** `04` gains §4.11 "Statistics" with worked examples used by both pgTAP and the domain tests (epic DoD).
- **Interaction and restoration.** Read-only screens: no "Guardar". The selected frame (charts and statistics), the scroll position and an open muscle sheet survive background and process death (ADR-40, `StateRestorationTester` + recreated-ViewModel tests). Every screen has loading, empty and error states (US-46) and uses tabular numerals with Spanish formatting (`04` §5).
- **Retrospective actions still in force.** E5 (b): statistics state their data assumption (only `completed` sessions, UTC session dates, same as `exercise_history`), confirmed by the user at the checkpoint. E6: keep host-test time in check (no new slow suites). E7: none pending.

## Task List

Checkpoint rule for E8: a single 👤 checkpoint for the whole epic (user decision, 2026-10-25), right after the last implementation task, so it is also the final regression. All 12 user-testable items (charts 3, statistics 4, muscle distribution 3, states 1, closing regression 1) are grouped into 7 by journey; "leave the app and come back" is listed but not counted. Full item list in `tasks/todo.md`.

| Task | Flow | Scope | Expected agent runs |
|---|---|---|---|
| T-113: E7 carry-over cleanup and exercise charts (US-39) | backend + core + UI | L | 7: planner, ux-reviewer, backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer |
| T-114: Training statistics backend (US-40/US-41 backend) | backend + core | M | 5: planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer |
| T-115: "Estadísticas" screen with the radar and body map (US-40/US-41 UI) | UI + core | L | 6: planner, ux-reviewer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer |
| T-116: E8 closure | PM | S | PM only |

### Phase 1: Implementation
- [x] T-113: E7 carry-over cleanup and exercise charts — backend + core + UI — L
- [ ] T-114: Training statistics backend — backend + core — M
- [ ] T-115: "Estadísticas" screen with the muscle distribution — UI + core — L
  - **Checkpoint E8-A 👤 Follow your progress and spot imbalances (7 items):** exercise charts (record markers, "Todo"); non-weight charts; key values per frame and warm-up toggle; sets over time and per muscle; main exercises; muscle distribution (radar, body map, tap detail); states and the final E8 regression (incl. the T-113 photo comment check). Why here: the single checkpoint of the epic, after the last implementation task.

### Phase 2: Closure
- [ ] T-116: E8 closure — PM — S (no checkpoint; E8-A's approval releases it)

Internal milestones (not 👤 checkpoints):
- pgTAP + `deno test` + lint + `testLocalDebugUnitTest` green per task;
- `deploy-supabase` green after each migration (T-113, T-114), and before E8-A is presented.

## Dependencies

```
v0.7.0 ─► T-113 ─► T-114 ─► T-115 ─► [E8-A 👤] ─► T-116 (closure) ─► v0.8.0
```
- Strictly sequential (one active task at a time).
- E8 Q1–Q3 are resolved; no task is blocked.
- T-115 needs T-114 deployed.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| T-113 and T-115 exceed the ~15-file guideline, so one dev run may overflow or the review gets harder | Medium | Many T-113 changes are one-liners in separate files; the planner orders the work inside each run; T-115 fallback (components to a follow-up) only if pr-reviewer finds it unreviewable |
| One checkpoint for the whole epic collects many findings at once (E3 had 15) | Medium | The 7 items are explicit and grouped by journey; fix tasks follow the usual one-task-per-session rule |
| SQL and Kotlin disagree on set counting, volume or warm-up inclusion | High | One rule in `04` §4.11 with shared examples used by pgTAP and domain tests; volume reuses `set_volume`/`volume_factor`/`latest_body_weight_kg` like `exercise_session_records` |
| Statistics over the whole history are slow on a phone or the free DB | Medium | Aggregation in SQL (one round trip per section), existing session indexes checked in T-114; 1–5 users, small data |
| UTC session dates put a late-evening workout on the next day in a window | Low | Same convention as every training RPC since E3 (stated assumption, E5 retro b); user confirms at E8-A |
| The body map needs a silhouette asset with an unclear licence | Medium | Draw the regions in-house as vector paths, or use a permissively licensed asset with its licence recorded for the E9 credits |
| Radar and body map are hard to make accessible (small regions, colour only) | Medium | ≥ 48 dp effective targets or an accessible per-muscle list, descriptions with the set count, values in the sheet; ux-reviewer checks the plan |
| Vico cannot draw record markers or a radar | Medium | Markers as a Vico decoration/point layer; the radar is a custom Canvas component (`RadarChart`), not Vico |
| Changing the progress screen's data source (T-113) regresses the Fuerza and session-detail entry points | Medium | Existing `ExerciseProgress*` tests kept green; the reps-only fallback preserved; all three entry routes tested |
| A changed `save_meal_defaults` breaks the installed app | Low | Same signature and error codes; existing pgTAP unchanged |

## Open Questions

**Resolved (user, 2026-10-25):**
- **E8 Q1 · Entry and placement.** The Perfil "Estadísticas" section already holds "Fuerza" (1RM card, exercise charts, "Volumen por grupo muscular"), "Peso" and "Calorías". Decision: keep "Fuerza" as it is and add a separate row "Entrenamiento" in that section opens a full-screen "Estadísticas" (back arrow) that holds the US-40 sections and, below them, the radar and the body map (US-41), rather than a separate muscle screen.
- **E8 Q2 · Frames and periods.** Decision: "Estadísticas" uses the chart frames 1M, 3M, 6M, 1A and "Todo" (default 1M; the existing exercise-chart default is unchanged); "Periodo anterior" is the window of equal length right before the selected one, and with "Todo" the radar shows only "Actual"; the set-count chart has one bar per week up to 6M and per month for 1A and "Todo"; "main exercises" are the top 5 by completed sets in the frame; the body map and its tap detail always cover the last 7 days (today and the 6 previous days), whatever frame is selected.
- **E8 Q3 · Record markers and "full body".** Decision (accepted as proposed): a chart marks a session when it beat every earlier session for that chart's metric (the 1RM chart marks 1RM records, the volume chart volume records, time/distance charts their own), as in the session summary; exercises whose group is `full_body` count as "Cuerpo completo" in the radar and the per-group list but are not drawn on the body map.

**Assumptions (non-blocking; applied unless the user objects at the checkpoint):**
- Statistics count only `completed` sessions and completed sets, with the UTC date of the session start (like every training chart since E3); warm-up sets only with "Incluir calentamiento" (`04` §4.6).
- "duración" is the sum of session durations net of pauses (`04` §4.10); "volumen" follows `04` §4.2/§4.7 (per-hand ×2, bodyweight with the latest body weight, duration/distance exercises 0).
- `weight_duration` exercises chart their best duration; `reps_only` keeps the reps chart; steps and floors exercises chart steps and floors.
- A secondary muscle equal to the exercise's primary group is not counted twice.
- Labels, legends, empty-state wording and the marker style are worded by ux-reviewer within `05`, and the user sees them at the checkpoint.
