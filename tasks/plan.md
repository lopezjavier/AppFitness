# Implementation Plan: E11 · Health Connect daily sync → `v1.x`

> **Status:** broken down on 2026-10-28 by the epic-planner and agreed with the user the same day: 5 tasks (T-139…T-143), a 👤 spike report after T-139 and **one Checkpoint E11** after T-142 (user decision: E11-A and E11-B merged). E11 Q2 resolved; E11 Q1 resolved by the spike. T-139…T-142 and T-144 merged; Checkpoint E11 open.

## Overview
Bring the watch data (sleep, steps, active and total calories, resting heart rate) and the scale data into the app automatically on the first open of each local day, while keeping the "Sincronizar" button for fresh weigh-ins (ADR-74, amends ADR-08). The new data is stored per user with owner RLS (`daily_activity`, `sleep_sessions`, `resting_heart_rate`), never duplicated, and shown where ux-reviewer places it; E13's coach consumes it later. The app keeps asking to write only weight and body fat. The data source on the user's phone is Zepp (phone UI in Spanish), and Health Connect holds at least 5 months of steps (user, 2026-10-28).

Sources: `planning/epics/E11-health-connect-daily-sync.md` (US-60…US-63), `planning/01-scope.md` §4 and §6, ADR-74, ADR-08/47/48 (Phase 1), ADR-29 (cascade), ADR-40 (restoration), ADR-73 (no background job), `planning/phase1/04-business-rules.md` §3.7, `planning/phase1/03-data-model.md` (`health_sync_state`), `planning/05-design-system.md` §8–§10. Tasks: `tasks/todo.md` (T-139…T-143).

Out of scope (epic, `01` §5): background sync; writing food or training to Health Connect; water (Phase 3); the coach using these values (E13).

## Sizing
Every task pays 5–8 agent hand-offs, so the epic has 5 tasks:
- **T-139 (S)** = the history spike. It stays alone by user decision (the first task of E11): its device report decides the first-sync window of T-141, and it ships only a manifest declaration, a diagnostic read and a temporary screen.
- **T-140 (M)** + **T-141 (L)** = US-61 + US-62 + US-63, one functionality ("watch data through Sincronizar") split in two because together they exceed ~25 files with heavy backend work (3 tables, a column, an import RPC, pgTAP, repositories) and platform/UI work that review apart. Backend first, consumer second (sizing rule).
- **T-142 (M)** = US-60: the daily trigger, its rule and the status-line outcomes; no migration (its column ships in T-140, so the epic has one production deploy).
- **T-143 (S)** = closure; it waits for Checkpoint E11.

Checkpoint (user decision, 2026-10-28): one checkpoint for the whole epic instead of E11-A (after T-141, 7 items) and E11-B (after T-142, 6 items). T-141 merges with no checkpoint after it; the merged 12 items are tested at Checkpoint E11 after T-142, which is also the final regression.

## Architecture Decisions
- **One migration for the epic (T-140).** `daily_activity` (unique per user and day, replaced on re-sync), `sleep_sessions` (unique per user and `external_id`), `resting_heart_rate` (unique per user and day), `health_sync_state.last_auto_sync_date`. Owner RLS with `(select auth.uid())`, explicit grants, cascade on account deletion (ADR-29), pgTAP. Import through an idempotent `security invoker` RPC modelled on `import_health_measurements` (input caps, `22023`).
- **Reads per granted type (T-141).** `HealthConnectDataSource` reads each new type only when its read permission is granted, so partial permission is never an error. Steps and calories are stored as daily totals per local day (aggregated by day so overlapping records are not double-counted); a touched day is re-read whole and replaced. Own-origin records stay skipped (US-62, `phase1/04` §3.7). The Changes token covers the new types.
- **Permissions (US-63).** Reads: the six E5 types + the five new ones (+ `READ_HEALTH_DATA_HISTORY` in branch A). Writes: weight and body fat only; the four unused `WRITE_*` declarations in the manifest are removed.
- **First-sync window decided by the spike (E11 Q1).** Branch A (history readable): 90 days, history asked with the read permissions. Branch B (not readable): about 30 days, no history declaration, US-63's second scenario rewritten with the user's OK.
- **Daily trigger above the navigation (T-142).** A pure `ShouldAutoSync` rule (local date vs `last_auto_sync_date`) and a trigger next to T-135's reminder refresh run the same sync as the button, once per local day, only in the foreground (ADR-74, ADR-73); a cold start and a return from the background (`ON_START`) on a new local day both count as the first open (E11 Q2). Success sets the date; failure or offline does not, so the next open retries. The automatic sync never opens the permission dialog. A weight it stores refreshes the reminder state.
- **UI on existing templates.** Values as `MetricCard`s in the surface ux-reviewer picks in T-141's plan pass (epic note; e.g. an "Actividad" section on Peso or a destination under Perfil → "Estadísticas"); the status line stays the Health Connect `MetricCard` on Peso (T-123). No new designsystem component is expected.
- **Local days.** Sleep belongs to the local day it ends on; daily totals and resting heart rate use the device time zone, consistent with `log_date`.

## Task List

| Task | Flow | Size | Expected agent runs |
|---|---|---|---|
| T-139: Spike: `READ_HEALTH_DATA_HISTORY` on the device | platform + minimal UI | S (alone, user decision) | 5 |
| T-140: Watch-data backend: daily activity, sleep, resting heart rate | backend + data | M | 5 |
| T-141: Read sleep, steps, calories and resting heart rate with "Sincronizar" (US-61, US-62, US-63) | platform + core + UI | L | 7 |
| T-142: Daily automatic sync on the first open (US-60) | core + UI | M | 7 |
| T-143: E11 closure → `v1.x` | PM | S | PM only |

Full checkpoint item lists in `tasks/todo.md`.

### Phase 1: History spike
- [x] T-139: Spike: `READ_HEALTH_DATA_HISTORY` on the device — S
  - **👤 Spike report (not a functionality checkpoint):** the user taps "Comprobar" in the temporary diagnostic screen and reports feature status, history permission, record counts 60–90 and 0–30 days ago and the oldest date. The PM records the result and the E11 Q1 branch here (docs PR). Gates T-141 only.

### Phase 2: Watch data through "Sincronizar" (US-61, US-62, US-63)
- [x] T-140: Watch-data backend — M (no checkpoint; internal milestone `deploy-supabase` green before T-141)
- [x] T-141: Read the watch data with "Sincronizar" — L (no checkpoint after it; its items are tested at Checkpoint E11)

### Phase 3: Daily automatic sync (US-60)
- [ ] T-142: Daily automatic sync on the first open — M
  - **Checkpoint E11 👤 Your watch data, synced by itself each day (12 items + leave and come back; agreed 2026-10-28):** permissions (reads asked, writes only weight and fat); last night's sleep; yesterday's steps and calories; resting heart rate present or silently absent; partial permission; no duplicates (double sync, own weight); history window; first open of the day (cold start or return from the background); second open; fresh weigh-in with the button (reminder clears); offline and missing permission; final regression of E11. Why here: last implementation task, so it is also the final regression; its approval releases T-143.

### Phase 4: Closure
- [ ] T-143: E11 closure → `v1.x` — PM — S (no checkpoint; Checkpoint E11's approval releases it)

Internal milestones (not 👤 checkpoints):
- lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green per task; pgTAP green for T-140;
- `deploy-supabase` green on `main` after T-140 (before T-141 starts), data intact.

## Dependencies

```
v1.1.0 ─► T-139 ─► [👤 spike report → E11 Q1] ─┐
              └─► T-140 ─► (deploy-supabase green) ─┴─► T-141 ─► T-142 ─► [Checkpoint E11 👤] ─► T-143 ─► v1.x
```
- Strictly sequential (one active task at a time). T-140 may start while the spike report is pending (it does not depend on the window); T-141 needs both.
- Earlier work used: `health_sync_state`, `import_health_measurements`, `SyncHealthMeasurements`, `HealthSyncViewModel` and the permission launcher (E5, T-079); the Health Connect `MetricCard` on Peso (T-123); the reminder state and refresh hook (T-135).

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Without the history permission Health Connect returns nothing older than about 30 days before the first grant | Medium | The spike measures it on the user's phone first; both window branches are planned in T-141 |
| Zepp writes overlapping or partial step/calorie records, so summing records double-counts | High | Daily totals through Health Connect aggregation per local day; a touched day is re-read whole and replaced; host tests with overlapping records; compared with Zepp at Checkpoint E11 |
| Zepp does not write some types (resting heart rate, total calories) | Low | Gherkin "A type the watch does not provide": no row, no card, no error |
| The automatic sync runs while the user taps "Sincronizar", or twice on a fast reopen | Medium | One sync at a time in the shared holder; host tests for the overlap |
| A first sync of 90 days of five types is slow or exceeds the RPC row cap | Medium | Daily totals are at most ~90 rows per type; sleep paged; the planner batches under the RPC caps |
| The values surface does not fit Peso | Low | ux-reviewer picks the surface in T-141's plan pass (epic note) |
| The temporary diagnostic screen of T-139 lingers | Low | Its removal is an acceptance criterion of T-141 |
| T-141 exceeds ~25 files | Low | Developer slices (core, then UI) within the same PR; no extra task |

## Open Questions

- **E11 Q1 · First-sync window (BLOCKING T-141; answered by the T-139 spike report).** Branch A: history readable → 90 days, `READ_HEALTH_DATA_HISTORY` asked with the read permissions, US-63 as written. Branch B: not readable → about 30 days, no history declaration; US-63's "History older than 30 days" scenario and the `01-scope.md`/epic wording are rewritten with the user's OK. *Result (user device report, 2026-10-28): Branch A.* Feature `READ_HEALTH_DATA_HISTORY` available, permission granted; 60–90 days ago: 1884 step records and 0 weights; last 30 days: 4115 step records and 4 weights; oldest step record 07/08/2026 (82 days ago, inside the 60–90 window), oldest weight 14/10/2026. Data older than 30 days is readable, so T-141 uses a 90-day first sync and asks the history permission with the read permissions.

**Resolved (user, 2026-10-28):**
- **E11 Q2 · What counts as "the first open of the day" → yes, a return from the background counts.** A cold start and the first return to the foreground (`ON_START`) of a new local day both trigger the automatic sync, the same moment the reminders are recomputed (T-135).

**Assumptions (non-blocking; applied unless the user objects at the checkpoints):**
- A1. A sleep session belongs to the local day it ends on ("last night's sleep" = the session ending this morning).
- A2. Several resting-heart-rate readings in one day keep the latest (one row per user and day, as the epic table says).
- A3. The automatic sync never opens the Health Connect permission dialog by itself; it only asks through the status line (Gherkin "Permission missing").
- A4. Users who granted only the E5 weight types are asked for the new types the next time they tap "Sincronizar".
- A5. E11's release version is proposed as `v1.2.0` (minor per epic) and confirmed at the closure.
- A6. Spanish copy beyond the Gherkin literals (card labels, status-line outcomes) is worded by ux-reviewer within the glossary and seen by the user at the checkpoints.
