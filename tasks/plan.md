# Implementation Plan: E10 · In-app reminders and weekly check-in → `v1.x`

> **Status:** broken down on 2026-10-28 by the epic-planner and agreed with the user the same day: 3 tasks, **one checkpoint for the whole epic** (Checkpoint E10 after T-136), E10 Q1–Q3 resolved (see "Open Questions"). No task is blocked.

## Overview
Remind the user, inside the app, to log what the coach will need (weight and circumferences), and bring back the weekly check-in with a deterministic summary of the week. Reminders are computed from stored data on each open and return to the foreground and listed in a new "Centro de avisos"; each type can be silenced. The check-in (energy 1-5, notes) appears on a chosen weekday (default Sunday) and stays listed until answered or until the next check-in day; it shows this week's training against last week, the week's records and the weight trend, and stays reachable and editable from the center until its week ends. E13 later adds the coach's commentary to the check-in screen (US-71).

Sources: `planning/epics/E10-reminders-and-check-in.md` (US-57, US-58, US-59), `planning/01-scope.md` §4, ADR-73 (amends ADR-66 and E8 US-44), ADR-37 (3 tabs), ADR-40 (restoration), ADR-47/48 (Health Connect weight wins; today's weight via the pencil), `planning/07-coach-design.md` §5 and §9, `planning/04-business-rules.md` §3, §4.2, §4.6, §4.8, §4.11, §5, `planning/05-design-system.md` §6, §8–§10. Tasks: `tasks/todo.md` (T-135…T-137).

Out of scope (epic, `01` §5): Android system notifications for reminders and any background job; muscle soreness prompts; the training streak; water and voice; the automatic daily Health Connect sync (E11; the manual "Sincronizar" already covers US-58's Health Connect scenario).

## Sizing
Every task pays 6–8 agent hand-offs, so the epic has 3 tasks:
- **T-135 (L)** = US-57 + US-58. US-58 alone is S: a pure domain rule with no screen; the center (US-57) has nothing to list without it, and both ship one migration (`notification_settings`).
- **T-136 (L)** = US-59 on its own: one migration (table, profile column, records RPC), two use cases, one new screen and one Preferencias field. Not split into backend and UI: the backend is small and reviewable with its only consumer.
- **T-137 (S)** = closure; it cannot merge with T-136 because it waits for Checkpoint E10's approval.

Checkpoint (user decision, 2026-10-28): one checkpoint for the whole epic instead of two of 7 items. T-135 merges with no checkpoint after it; all 14 collected items are tested at Checkpoint E10 after T-136, which is also the final regression.

## Architecture Decisions
- **Reminders are derived, never stored (US-57 note, ADR-73).** A pure `ComputeReminders` use case in `shared/domain/usecase/` takes today's local date, the last weigh-in day, the last circumference day, the check-in state (T-136) and the silenced kinds, and returns the pending list. No table per reminder, no worker, no system notification.
- **One app-level reminder state.** Held above the navigation (e.g. `AppViewModel`), recomputed on app open, on each `ON_START` of the process lifecycle, and after any write that can clear a reminder (weight, circumference, Health Connect sync, check-in). The entry point's count and the center read the same state, so they never disagree.
- **Calendar days in the device time zone** for every threshold, consistent with `log_date` (`03` conventions); the weight's daily value follows `04` §3.1 and a Health Connect weight counts like a manual one (ADR-47).
- **`notification_settings` with "missing row = enabled".** The enum carries all three kinds from T-135, so T-136 adds no enum value. Owner RLS, cascade on delete (ADR-29), pgTAP.
- **Check-in data and window (T-136, Q1 B, Q3 B).** `weekly_check_ins` unique per user and `week_start` (Monday of the chosen day's ISO week); `profiles.check_in_weekday` 1-7 default 7 (Sunday) with a column-level UPDATE grant. A pure `CheckInWindow` use case: the check-in opens on the chosen weekday and stays listed until answered or until the next check-in day; once answered it stays reachable from the center as a non-counted row and editable (upsert of the same week) until the window closes. The rules go to `04-business-rules.md` with the task.
- **Weekly summary (T-136, Q2).** The summary always covers the Monday–Sunday week (ending Sunday 23:59, device time zone) that contains the check-in day, compared with the week before, whatever day it is opened; it is recomputed on each open, so opened on Monday it shows that week's closed figures. Volume in kg (§4.2) plus workouts and sets, this week vs last week with the difference, from `training_summary` (§4.11, two calls); the records first set by a session of this week, from a new owner-scoped `weekly_records(p_from, p_to)` RPC (the existing `personal_records` is per exercise), respecting §4.6; weight trend = this week's average daily weight (§3.1) minus last week's, "Sin datos suficientes" when either week has no weigh-in. New migrations only (a merged one is never edited).
- **Entry point inside the 3 tabs.** ux-reviewer picks it in T-135's plan pass (epic "Navigation"); it must be visible on the tab the app restores to and show the count. If it is an icon, a new `BadgedIconButton` designsystem component is built in T-135 (catalog, host test, `05` §6 row). The Trainer tab is not added in E10 (ADR-79).
- **Screens on the templates.** Center: `AppScreen` + `SectionHeader` + `AppListRow` + `AppSwitchRow` + `EmptyState`. Check-in: `AppEditorScreen` with `MetricCard`s, `SegmentedSelector` (energy 1-5) and a multi-line `AppTextField`; the summary loads independently and never blocks answering (R-31). "Día del check-in": `AppPickerSheet` inside a Preferencias section editor (`05` §6.2). Each UI task names its patterns for the ux-reviewer plan pass.
- **E13 hook.** The check-in screen leaves a slot under the summary for the coach's commentary (US-71); nothing is rendered there in E10.

## Task List

| Task | Flow | Size | Expected agent runs |
|---|---|---|---|
| T-135: "Centro de avisos" with weight and circumference reminders (US-57 + US-58) | backend + core + UI | L (merged) | 8 |
| T-136: Weekly check-in with the weekly summary (US-59) | backend + core + UI | L (merged) | 8 |
| T-137: E10 closure → `v1.x` | PM | S | PM only |

Full checkpoint item list in `tasks/todo.md`.

### Phase 1: Reminders (US-57, US-58)
- [x] T-135: "Centro de avisos" with weight and circumference reminders — backend + core + UI — L (no checkpoint after it)

### Phase 2: Weekly check-in (US-59)
- [ ] T-136: Weekly check-in with the weekly summary — backend + core + UI — L
  - **Checkpoint E10 👤 Reminders and your weekly check-in (14 items + leave and come back; agreed 2026-10-28):** entry point and count; weight reminder; circumference reminder; tap to log clears it; Health Connect sync clears it; silence a type; next open and states; check-in day; weekly summary; answering; reopen and edit; discard safety and silencing; check-in states; final regression of E10. Why here: after the last implementation task, so it is also the final regression; its approval releases T-137.

### Phase 3: Closure
- [x] T-137: E10 closure → `v1.x` — PM — S (no checkpoint; Checkpoint E10's approval releases it)

Internal milestones (not 👤 checkpoints):
- lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green per task; pgTAP green for T-135 and T-136;
- `deploy-supabase` green on `main` after T-135 (before T-136 starts) and after T-136 (before Checkpoint E10 is presented), data intact.

## Dependencies

```
v1.0.0 ─► T-135 ─► (deploy-supabase green) ─► T-136 ─► (deploy-supabase green) ─► [Checkpoint E10 👤] ─► T-137 (closure) ─► v1.x
```
- Strictly sequential (one active task at a time).
- T-136 builds on T-135's reminder state, center screen and `notification_settings` (the check-in kind and its switch).
- Data from earlier epics: weights and circumferences (E5), sessions (E3/E6), `training_summary` and the records catalogue (E8), Preferencias hub (E9 T-127).

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| One checkpoint for the whole epic (14 items): feedback on the center arrives after T-136 has built on it | Medium | User decision; T-136 reuses the center's rows and switches without new patterns, so a center fix sweeps both tasks in one `fix(T-###)`; ux-reviewer passes on both tasks catch pattern problems early |
| Reminder state goes stale after a write (Gherkin "Logging clears the reminder") | High | One shared state recomputed after every relevant write and on `ON_START`; ViewModel tests for the weight, circumference, sync and check-in paths |
| Day boundaries: local days vs UTC timestamps (`measured_at`, §4.11 uses UTC dates for statistics) | Medium | Thresholds use local calendar days from the device time zone; boundary cases in `ComputeRemindersTest` and `CheckInWindowTest`; the summary states its week range; a UTC/local mismatch in `training_summary` near midnight is accepted and noted in the PR if the planner finds it |
| A check-in window that crosses the ISO week boundary (e.g. Sunday → Saturday) is stored against the wrong week | Medium | `week_start` = Monday of the chosen day's week, fixed when the window opens; `CheckInWindowTest` covers windows spanning two ISO weeks and a change of day mid-window |
| A switch in "Centro de avisos" that applies on toggle conflicts with R-10 ("explicit Guardar") | Medium | ux-reviewer decides in T-135's plan pass (settings switch applied on toggle vs. a section with "Guardar") and writes a new convention to `05` §8 if needed |
| Entry point does not fit every tab root header (Diario's header is the date switcher) | Medium | ux-reviewer chooses the placement within ADR-37 in T-135's plan; the entry must be visible on the restored tab |
| `weekly_records` duplicates the §4.8 logic of `personal_records` and drifts from it | Medium | Built on the same per-session records (`exercise_session_records` logic), pgTAP cases shared with the existing ones, respects §4.6 |
| T-135/T-136 exceed ~25 files | Low | Developer slices within the same PR (backend + core, then UI), as in E9; no extra task |

## Open Questions

**Resolved (user, 2026-10-28):**
- **E10 Q1 · Check-in window → B.** The check-in is listed from the chosen day until it is answered or the next check-in day arrives; it is stored for the ISO week that contains the chosen day; only one check-in is ever listed. The default day stays Sunday. The week ends on Sunday 23:59 (device time zone), and the check-in's summary always covers the Monday–Sunday week containing its check-in day vs the week before: opened on Monday, it shows the closed figures of the week that just ended.
- **E10 Q2 · Summary figures → as recommended.** Volume in kg plus workouts and sets, this week vs last week; the records first set this week; weight trend = this week's average minus last week's; "Sin datos suficientes" when a week has no weigh-in.
- **E10 Q3 · Reopening → B.** An answered check-in stays reachable from "Centro de avisos" (not counted as pending) and is editable until its window closes on the next check-in day (confirmed by the user). The summary is recomputed on each open, so an edit on Monday sees the closed numbers of its week.

No task is blocked.

**Assumptions (non-blocking; applied unless the user objects at the checkpoint):**
- A1. Weight reminder when today − last weigh-in day ≥ 2; the text shows the real number of days ("No te has pesado en 5 días"); the listed date is the day it became due.
- A2. Circumference reminder when today − last circumference day ≥ 14 ("No te has medido en N semanas", N = whole weeks); with no circumference ever, it counts from the account's first day in the app (the onboarding weight's day).
- A3. Any circumference site counts as "measuring"; any `body_measurements` row with a weight counts as a weigh-in (manual or Health Connect).
- A4. Silencing a type is per user and stored server-side, so it survives a reinstall.
- A5. "Día del check-in" lives in a Preferencias section chosen by ux-reviewer; the weekday picker shows Spanish weekday names.
- A6. E10's release version is proposed as `v1.1.0` and agreed at the closure (`09-open-questions.md`).
- A7. Spanish copy beyond the Gherkin literals (entry label, check-in title, switch labels, answered-check-in row, empty state) is worded by ux-reviewer within the glossary and seen by the user at the checkpoint.
