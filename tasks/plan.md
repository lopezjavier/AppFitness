# Implementation Plan: E5 · Weight tracking → `v0.5.0`

## Overview
Log body weight, body fat and body circumferences by hand or from a smart scale through Health Connect. See the real trend with a 7-day moving average and a weekly rate. Know how far the user is from their goal and how their metrics read. Everything lives on one full-screen weight destination opened from the Perfil root "Estadísticas" section ("Peso", next to "Fuerza" and "Calorías"; ADR-37, ADR-44). There is no Progreso tab. Weights are revisited in a weight calendar by day (user decision 2026-10-21).

Sources: `planning/epics/E5-weight.md` (US-25…US-27), `planning/01-phase1-scope.md` FR-3, `planning/03-data-model.md` §2/§4/§6, `planning/04-business-rules.md` §3 and §5, `planning/05-design-system.md` (§6 `MetricTile`, `TrendPill`, `GoalCard`, `ComparisonDeltaRow`, `MetricInterpretationRow`, `CircumferenceRow`, `MonthCalendarGrid`, `SwipeToDeleteRow`; §8 interaction rules), ADR-01, ADR-08, ADR-21, ADR-29, ADR-35, ADR-37, ADR-40, ADR-45. Tasks: `tasks/todo.md` (T-076…T-082). Earlier plans are in the git history.

Out of scope (epic file): background sync, other Health Connect data (steps, sleep…), a target date or estimated time to reach the target, progress photos.

## Architecture Decisions
- **One weight destination, built up task by task.** T-077 creates the route, the current-weight panel, the "Registrar peso de hoy" sheet and the weight calendar ("Historial de mediciones"). T-078 adds "Circunferencias", T-079 the Health Connect banner, T-080 the chart, filters and weekly-rate pill, and T-081 the goal card, BMI, comparison and metric details. Tablet: panel and chart on the left, calendar and lists on the right (`05` §5).
- **Weight calendar (Q1).** A month grid reusing `MonthCalendarGrid` (T-062) marks the days with measurements. An empty day opens a sheet to set that day's weight; a day with a weight opens it to edit (weight, fat, time) or delete. The date is never edited, so the one-manual-weight-per-day rule (`03` §4) cannot be broken by an edit; the RPC rejects a `measured_at` outside the row's day.
- **One migration per task, with the task that needs it.** T-076: the manual-measurement RPC; T-078: `circumference_site` + `body_circumferences`; T-079: the composition columns, the Health Connect copy id on manual rows (`hc_record_id`) and `health_sync_state` (+ an idempotent import RPC if the planner wants one transaction); T-081: `profiles.target_weight_kg` and its column grant. A merged migration is never edited; `body_measurements` (T-035) and `save_preferences` (T-041) are extended by new migrations only.
- **US-25 split for size.** Backend + shared domain/data (T-076, no UI), then the weight screen with the calendar (T-077); circumferences are their own vertical slice (T-078).
- **One manual row per day, enforced in SQL.** The rule applies to the weight screen and to Preferencias. The client sends its local-day bounds (as `save_preferences` does); both paths share the same SQL logic and pgTAP covers both.
- **Health Connect behind a port (ADR-05).** `HealthDataSource` lives in `shared/domain/port`, `HealthConnectDataSource` in `composeApp` androidMain, and `FakeHealthDataSource` in tests. Pairing and merging records (`04` §3.7, ± 2 min) is a pure domain function. Records written by this app are skipped on import. Import idempotency relies on `unique (user_id, external_id)`. Write-back stores the written record id, so edits update the copy and deletes remove it (Q2). Write-back never fails the app's change. The real integration is tested by hand only (epic note, ADR-27).
- **Pure domain rules.** `DailyWeights`, `MovingAverage`, `WeeklyRate`, `Bmi`, `TargetDistance` (by strategy), `GoalProgress` (from the first weight in the app), `InterpretMetric`, `WaistHipRatio`, `CompareMeasurements`, `MeasurementsByDay` and the validators live in `shared`, with the `04` §3 examples as tests.
- **Chart.** Extends the existing `AppLineChart` (Vico 3.3.1, pinned) with a dot layer and a thick `athleticCyan` MA7 line. Each range is loaded with 6 extra days before its start. The time filter is the only local preference (DataStore, already pinned; ADR-01).
- **Shared components.** `MetricTile` and `TrendPill` exist; they get a container-colour parameter in T-080 (consumes the E4 note). `MonthCalendarGrid` is reused in T-077. New: `CircumferenceRow` (T-078), `GoalCard`, `ComparisonDeltaRow` and `MetricInterpretationRow` (T-081), each with a catalog entry. Check every caller before changing a shared component.
- **Interaction rules (ADR-45, `05` §8).** Delete at once with no "Deshacer" (the US-25 Gherkin now says so). Swipes use `SwipeToDeleteRow`. Each sheet has one explicit "Guardar" and nothing is applied on dismiss. A row opens its details.
- **Restoration (ADR-40).** The visible month, the open day, every sheet draft, the comparison selection and the selected chart point survive background and process death. Each UI task ships `StateRestorationTester` + recreated-ViewModel tests.

## Task List

### Phase 1: Manual weight and body measurements
- [ ] T-076: Manual weight backend: one weight per day, locked date and the repository (US-25 data) — backend — M
- [ ] T-077: Weight screen with the weight calendar (US-25 weight UI) — UI — M–L
- [ ] T-078: Body circumferences and the waist-to-hip ratio (US-25 circumferences) — UI + backend — M
- [ ] T-083: User feedback: day-only forms, aligned weight sheet, all circumferences at once — UI — M
  - No checkpoint here (user decision 2026-10-22): the manual weight and circumference tests move to Checkpoint E5-B.

### Phase 2: Health Connect and trend
- [ ] T-079: Manual Health Connect sync, read and write-back (US-26) — UI + backend + platform — L
- [ ] T-080: Weight trend chart, time filters and weekly rate (US-27 trend + US-25 chart) — UI — M–L

### Phase 3: Goal, metrics and closure
- [ ] T-081: Target weight, BMI, measurement comparison and metric interpretation (US-27 goal/metrics) — UI + backend — L
  - **Checkpoint E5-B 👤 Health Connect, trend, goal, metrics and final regression:**
    - first sync and permissions (90 days); incremental sync without duplicates; write-back and its edit/delete; "Permiso necesario"; dots + "MA 7D" line, filters, weekly pill;
    - set and change the target; direction-aware distance and progress from the first weight; BMI with status; metric details with the disclaimer; composition values; "Comparar";
    - final regression over a few real days with the scale, manual entries via the calendar, Health Connect edit/delete, circumferences, chart, goal and Preferencias;
    - leave the app and come back;
    - approval → T-082.
- [ ] T-082: E5 closure — PM — S (tag `v0.5.0` and the retrospective, after E5-B is approved)

There are two checkpoints (user decision 2026-10-21; the first breakdown had five): manual measurements, then everything else (Health Connect, trend, goal, metrics) with the final regression. Every checkpoint ends with "leave the app and come back: the same tab, screen and draft are restored". Internal milestones are not 👤 checkpoints:
- pgTAP + lint + `testLocalDebugUnitTest` green per task;
- `deploy-supabase` green after each migration (T-076, T-078, T-079, T-081);
- the T-079 merged-manifest check.

## Dependencies

```
v0.4.0 ─► T-076 ─► T-077 ─► T-078 ─► T-083 ─► T-079 ─► T-080 ─► T-081 ─► [E5-B 👤]
       ─► T-082 ─► v0.5.0
```
- Strictly sequential; each checkpoint gates the next task. T-079, T-080 and T-081 run back to back (no checkpoint between).
- Health Connect (T-079) comes before the chart (T-080) on purpose: the 90-day import gives the chart and the weekly rate real data at E5-B.
- T-077 needs T-076's RPC and repository. T-080 needs the measurements from T-077. T-081 needs T-078's ratio (interpretation), T-077's calendar (comparison) and T-080's panel.
- T-079 adds write-back (create, edit, delete) to T-077's sheet and write-back on save to the Preferencias weight.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Incremental sync by measurement time misses scale readings that reach Health Connect late (the scale app syncs hours after the weigh-in) | High | The T-079 planner detects changes with Health Connect's changes token or `lastModifiedTime` instead of the record time. The import is idempotent, so overlap is safe. Tried at E5-B. |
| Write-backs read back as new measurements (duplicates) | High | Skip records whose data origin is this app (both flavor packages); `PairHealthReadingsTest`. |
| An update or delete of a written-back copy fails (permission revoked, record removed in Health Connect) | Medium | Health Connect failures never block the app's change; tests with a failing fake. |
| Android 14+ refuses the permission request without the rationale / `VIEW_PERMISSION_USAGE` activity-alias; Android ≤ 13 needs the Health Connect app | High | Merged-manifest check in T-079; `getSdkStatus` for unavailable / update required; tried on the user's phones at E5-B. |
| Health Connect cannot be tested in CI | Medium | `FakeHealthDataSource` for every scenario; manual read/write test documented in the T-079 PR (epic DoD). |
| The one-manual-row-per-day rule diverges between the weight screen and Preferencias | Medium | One SQL path; pgTAP for both; an E5-A step compares both screens. |
| Local day vs UTC around midnight (per-day rule, calendar days, daily values, filters) | Medium | The client sends local-day bounds; injected `Clock`; tests at 23:59/00:00. |
| Changing `MonthCalendarGrid`, `AppLineChart`, `MetricTile` or `TrendPill` breaks the diary history, the strength charts, the catalog or the panel | Medium | Additive parameters with defaults; check every caller; existing tests stay green. |
| Vico 3.3.1 lacks a needed API (dot layer, long-press marker) | Low | The planner checks the pinned API first; a stable upgrade only if needed (stable-only rule). |
| L tasks (T-079, T-081) overflow one dev run | Medium | The planner slices them (named in each task), one dev run per slice. |

## Open Questions
- **Q1 (T-076, T-077) · RESOLVED 2026-10-21 (user): a weight calendar; the date is locked when editing.** *(was BLOCKING)* The user does not want to move a manual weight's date. Weights are revisited in a calendar by day: an empty day lets the user set that day's weight, and a day with a weight opens it to edit or delete. One manual weight per day stays. Recorded in `03-data-model.md` §4 and the US-25 Gherkin.
- **Q2 (T-079) · RESOLVED 2026-10-21 (user): option (a).** *(was BLOCKING)* Editing or deleting a manual entry also updates or deletes its Health Connect copy (the app stores the written record id). Recorded in `04-business-rules.md` §3.7, `03` §4 (`hc_record_id`) and the US-26 Gherkin.
- **Q3 (T-081) · RESOLVED 2026-10-21 (user): direction-aware wording (a); progress from the first weight (i).** *(was BLOCKING)* Deficit/Surplus: "faltan X kg" until the target is reached in the strategy's direction, then "objetivo alcanzado"; Maintenance: "X kg por encima/por debajo del objetivo", or "objetivo alcanzado" at 0,0. The progress bar starts at the user's first weight in the app (the onboarding row). Recorded in `04` §3.5, `05` §6 (`GoalCard`) and the US-27 Gherkin.
- **Story ID collision · RESOLVED 2026-10-21:** the E4 micronutrient story is now US-16b (`planning/epics/E4-food-diary.md`, ADR-43 consequences, T-068 and T-070). Code comments and merged migrations still say "US-25" for it. Merged migrations are never edited; the code comments can be fixed in passing by a later E4-area change.
- **Assumptions (non-blocking; applied unless the user objects at the checkpoints):**
  - The Preferencias weight save is a manual log, so it is also written back to Health Connect (T-079).
  - A calendar day with only Health Connect readings can still get a manual weight.
  - "Comparar" compares the latest measurement against the one the user opens from an older day; the fat difference is hidden when either has no fat.
  - Interpreted metrics (status + explanation) are BMI, body fat % and the waist-to-hip ratio (`04` §3.8). Lean mass, water, bone and BMR are shown as plain values without a status (no ranges are defined).
  - Circumferences are always manual (`source = 'manual'`); Health Connect has no circumference data in scope.
  - A new measurement's time defaults to now for today; future instants are not allowed.
  - ux-reviewer proposes the Spanish labels for the circumference sites and the privacy-rationale text. The privacy text is shown to the user before building; the labels at E5-A.
