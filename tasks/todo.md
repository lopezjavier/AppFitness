# Tasks — Phase 2

Plan of the current epic: [tasks/plan.md](plan.md) (created by the epic-planner) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-137 (E10 breakdown). Numbering continues from here.

**Next:** present Checkpoint E10 (T-135 merged in #308, T-136 in #310; `deploy-supabase` green on `main` after both migrations). T-137 (closure) waits for its approval. Phase 2 scope: `planning/01-scope.md`; epics E10 → E11 → E12 → E13.

---

## 👤 Checkpoints

E10 (agreed with the user, 2026-10-28): one **Checkpoint E10** after T-136 (whole epic, also the final regression; its approval releases T-137). Details under the E10 section.

## Notes for later epics

- **E11:** verify `READ_HEALTH_DATA_HISTORY` on the device before the breakdown (first read covers 90 days).
- **E12:** verify screen-off alert behaviour on Android 17; 👤 the user picks the new alert sound (E9 point 16 candidates).
- **E13:** before the breakdown verify the sources marked *verify* in `planning/07-coach-design.md` §10, the Anthropic API data policy and current prices; 👤 Anthropic account, monthly spending limit and `ANTHROPIC_API_KEY` Supabase secret.

---

# E10 · In-app reminders and weekly check-in → `v1.x`

Stories: `planning/epics/E10-reminders-and-check-in.md` (US-57, US-58, US-59). Plan: [tasks/plan.md](plan.md). Decisions: ADR-73 (amends ADR-66 and the check-in part of E8 US-44); navigation stays 3 tabs (ADR-37; the Trainer tab arrives with E13, ADR-79). Rules: `planning/05-design-system.md` §8–§10 (R-##), ADR-40 restoration, `04` §3 (weight daily value), §4.2/§4.8/§4.11 (volume, records, statistics), §5 formatting. No note for E10 in "Notes for later epics".
Sizing: 3 tasks. US-58 alone is S (a pure domain rule with no screen of its own), so it is merged into US-57 (T-135, L): the center has nothing to list without it. US-59 is L on its own (T-136). The closure T-137 stays S because it cannot merge: it waits for Checkpoint E10's approval.
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1). UI scenarios are covered by host (Robolectric) tests; the user verifies by hand with `python scripts/deliver-apk.py` at the 👤 checkpoint.
Questions: E10 Q1–Q3 resolved by the user on 2026-10-28 (`tasks/plan.md`): Q1 B (the check-in is listed from the chosen day until answered or until the next check-in day), Q2 as recommended (volume kg + workouts + sets; records first set this week; weight trend = this week's average − last week's, "Sin datos suficientes" when a week has no weigh-in), Q3 B (an answered check-in stays reachable from "Centro de avisos" and editable until its week ends). Default check-in day stays Sunday. No task is blocked.
Checkpoints (agreed with the user, 2026-10-28): **one Checkpoint E10** after T-136 with all 14 collected items (user decision, overriding the 5–7 grouping), also the final regression of E10; its approval releases T-137. T-135 merges with no checkpoint after it.

---

## T-135: "Centro de avisos" with weight and circumference reminders (US-57 + US-58) · L

**Story:** US-57, US-58 · **Flow:** backend + core + UI · **Size:** L (US-58 S merged into US-57) · **Scenarios:** US-57 Pending reminders are listed; Pending count is visible; Reminders appear on the next open; Opening a reminder takes the user to log it; A reminder type is silenced · US-58 No weigh-in for 2 days in a row; Weighed yesterday; No circumference for 14 days; Logging clears the reminder; A Health Connect weight clears the reminder

**Description:** (1) **Backend:** migration with the enum `notification_kind` (`weight`, `circumference`, `weekly_check_in`; all three values now, so T-136 needs no enum change) and `notification_settings` (`user_id`, `kind`, `enabled`; unique per user and kind; a missing row means enabled), owner RLS, grants to `authenticated`, cascade on user delete (ADR-29); pgTAP (RLS isolation, uniqueness, default-enabled semantics documented). (2) **Domain:** `Reminder` model (kind, due date, payload such as days without a weigh-in) and a pure `ComputeReminders` use case: weight reminder when today − last weigh-in day ≥ 2 calendar days (device time zone, `log_date` semantics; any `body_measurements` row with a weight counts, manual or Health Connect); circumference reminder when today − last circumference day ≥ 14 (assumption A2 in the plan for "never measured"); silenced kinds are skipped. Nothing is stored per reminder (US-57 note). (3) **Data:** `NotificationSettingsRepository` (read all kinds, upsert one) on supabase-kt; last weigh-in and last circumference dates from the existing weight repositories (no new RPC unless the planner needs a light one). (4) **Refresh:** one app-level reminder state (in `AppViewModel` or a shared holder) recomputed on app open, on each return to the foreground (process lifecycle `ON_START`) and after any weight, circumference or Health Connect sync write, so logging clears the reminder without reopening the app. (5) **UI — templates and patterns:** "Centro de avisos" is a non-tab destination on `AppScreen` (back arrow, R-05; title "Centro de avisos"); a "Pendientes" `SectionHeader` with each reminder as an `AppListRow` (leading icon, headline "No te has pesado en 2 días" / "No te has medido en 2 semanas", supporting date via `AppDateFormat`, chevron; tap opens the entry, R-17); `EmptyState` with no action when nothing is pending; a settings card of `AppSwitchRow`s ("Recordatorio de peso", "Recordatorio de medidas"; the check-in switch comes with T-136); loading skeleton, `ErrorState` "Reintentar", offline per R-30 (switches disabled with the standard hint). **Entry point:** chosen by ux-reviewer in the plan pass within the 3 tabs (epic "Navigation"); it must be visible on whichever tab the app opens on (restored tab, ADR-40) and show the pending count; a new designsystem `BadgedIconButton` (`AppIconButton` + count badge, content description with the count, ≥ 48 dp, catalog entry, `05` §6 row) if the entry is an icon. **Tap targets:** weight reminder → the existing today's-weight entry of Peso (pencil, ADR-48); circumference reminder → the existing `CircumferenceSheet`. Switch semantics (applied on toggle vs. a "Guardar") are settled by ux-reviewer against R-10/R-12 and written to `05` §8 if it is a new convention. Spanish copy beyond the Gherkin literals by ux-reviewer per the glossary (§10).

**Sweep:** none expected (no existing reminder or badge pattern); if ux-reviewer finds an ad-hoc count badge, it is retired here (R-39).

**Acceptance criteria:**
- [x] Last weight 2 days ago → "No te has pesado en 2 días" listed with its date; last weight yesterday → no weight reminder; last circumference 15 days ago → "No te has medido en 2 semanas".
- [x] A weight reminder and a circumference reminder pending → the entry point shows 2; none pending → no count; the content description reads the count.
- [x] A condition met while the app was closed or in the background is listed on the next open / return to the foreground, with no background job.
- [x] Tapping the weight reminder opens the weight entry; saving a weight returns and the reminder and count are gone; same for the circumference reminder and the circumference entry.
- [x] A Health Connect sync ("Sincronizar") that stores today's weight clears the weight reminder.
- [x] Turning off "Recordatorio de peso" removes the weight reminder and lowers the count; turning it back on restores it; the setting persists across reinstall/sign-in (server-side).
- [x] pgTAP: RLS enabled and forced, user A cannot read or write user B's settings, one row per user and kind.
- [x] Empty, loading, error and offline states per R-30/R-31; open screen and scroll survive background and process death (ADR-40).

**Host tests:** `ComputeRemindersTest` (0/1/2/5 days without weight, 13/14/15 days and never measured for circumferences, time-zone day boundary, silenced kinds, Health Connect weight counts); `NotificationSettingsRepository` mapping test; reminder refresh test (recompute on foreground and after a weight save); `NotificationCenterViewModelTest` (list, toggle, offline, error); `NotificationCenterScreen` Robolectric tests (rows with dates, empty state, switches, tap navigates to the weight entry / circumference sheet); entry-point count test (0, 2) and `BadgedIconButton` test; restoration test; pgTAP `notification_settings`.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green on `main`, data intact (before T-136 starts; no checkpoint after T-135).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 20 files (size note: one small table; if the planner counts more than ~25 files, the core and UI run as two developer slices, not two tasks).
**Dependencies:** E9 (`v1.0.0`); breakdown agreed by the user
**Files likely touched:** `supabase/migrations/<ts>_notification_settings.sql`, `supabase/tests/notification_settings_test.sql`, `shared/…/domain/model/Reminder.kt`, `shared/…/domain/usecase/ComputeReminders.kt`, `shared/…/domain/port/NotificationSettingsRepository.kt`, `shared/…/data/repository/SupabaseNotificationSettingsRepository.kt`, `shared/…/di/`, `composeApp/…/app/{AppViewModel,AppRoutes,AppNavHost,AppScaffold}.kt`, new `composeApp/…/feature/notifications/{NotificationCenterScreen,NotificationCenterViewModel,NotificationCenterUiState}.kt`, `composeApp/…/designsystem/BadgedIconButton.kt`, `feature/weight/{WeightViewModel,CircumferencesViewModel,HealthSyncViewModel}.kt` (refresh after writes), catalog, `composeResources/values/strings.xml`, `planning/03-data-model.md`, `planning/05-design-system.md` §6, tests
**Estimated scope:** L

---

## T-136: Weekly check-in with the weekly summary (US-59) · L

**Story:** US-59 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Check-in appears on the chosen day; Changing the check-in day; Answering the check-in; Weekly summary; Missed check-in · plus US-57 Pending count / A reminder type is silenced for the check-in kind

**Resolved product rules (user, 2026-10-28; E10 Q1–Q3 in `tasks/plan.md`):**
- **Window (Q1 B):** the check-in is listed from the chosen weekday (default Sunday) until it is answered or the next check-in day arrives; it is stored for the ISO week (`week_start` Monday) that contains the chosen day; only one check-in is ever listed.
- **Summary (Q2):** training = total volume in kg (§4.2) plus workouts and sets, this week vs last week with the difference (`training_summary`, §4.11); records = the §4.8 records first set by a session of this week; weight trend = this week's average daily weight (§3.1) minus last week's, "Sin datos suficientes" when either week has no weigh-in.
- **Summary week (user, 2026-10-28):** a check-in's summary always covers the Monday–Sunday week (ending Sunday 23:59, device time zone) that contains its check-in day, compared with the week before; opened on Monday or later, it shows the closed figures of that week, never the new week's.
- **Reopening (Q3 B):** an answered check-in stays reachable from "Centro de avisos" (a row that is not a reminder and not counted) and is editable (upsert of the same week) until its window closes on the next check-in day. The summary is recomputed on each open, so an edit on Monday sees the closed numbers.

**Description:** (1) **Backend:** one migration: `weekly_check_ins` (`user_id`, `week_start` date = Monday, `energy` smallint 1-5 check, `notes` text nullable with a length cap, unique per user and week; owner RLS, grants, cascade); `profiles.check_in_weekday` smallint 1-7 default 7 (Sunday) with a check and the column-level UPDATE grant (the Preferencias section that holds it saves only its own field, as the hub's other sections); a save path for the check-in (upsert per user and week, or an RPC `save_weekly_check_in` if the planner needs validation, `22023` on bad input); a `weekly_records(p_from date, p_to date)` RPC returning the §4.8 records first set by sessions inside the range (the existing `personal_records` is per exercise), respecting "Incluir calentamiento" (§4.6); pgTAP for all of it. (2) **Domain:** `CheckInWindow` (is the check-in pending today for the current week, given the weekday and the stored answer, per the window rule above) feeding `ComputeReminders` as the `weekly_check_in` kind (silenceable through `notification_settings`, T-135); `WeeklySummary` use case: training figures this week vs last week from `training_summary` (§4.11), the week's records from `weekly_records`, and the weight trend of the week from the daily values (§3.1), as in the summary rule above. (3) **UI — templates and patterns:** the check-in reminder row in "Centro de avisos" (same `AppListRow` as T-135, headline by ux-reviewer, e.g. "Check-in semanal") opens the check-in screen, an `AppEditorScreen` (title by ux-reviewer per R-01, e.g. "Check-in semanal"): summary first as `MetricCard`s (volume in kg, workouts and sets this vs last week with the difference, records of the week as a short list, weight trend or "Sin datos suficientes"), then the form: energy 1-5 as a `SegmentedSelector` (R-33, 5 short options), notes as a multi-line `AppTextField` (R-24); pinned "Guardar" (R-09, R-10), Confirm haptic and close on success (R-13), "¿Descartar los cambios?" when dirty (R-14), offline hint (R-30); the summary loads independently (skeleton, `ErrorState` "Reintentar", R-31) and never blocks answering; a slot below the summary for the coach's commentary in E13 (US-71), empty in E10. "Día del check-in" in Preferencias: a `NavigationRow`/value in the section ux-reviewer picks (hub, `05` §6.2), choosing the weekday with an `AppPickerSheet` (applies on tap, R-12) inside that section's `AppEditorScreen`; weekday names in Spanish ("lunes" … "domingo"). The check-in switch joins the center's settings card ("Check-in semanal", wording by ux-reviewer). Once answered, the center shows a non-counted row for this week's check-in that opens the same screen prefilled (Q3 B); ux-reviewer places it (e.g. its own `SectionHeader`) so it is never mistaken for a pending reminder.

**Acceptance criteria:**
- [x] Default day Sunday: on Sunday the check-in is listed in "Centro de avisos" and counted at the entry point; unanswered, it stays listed on Monday…Saturday until the next Sunday; before the first Sunday it is not listed.
- [x] Setting "Día del check-in" to Friday in Preferencias lists the check-in from Friday until it is answered or the next Friday arrives.
- [x] Energy 4 and notes "semana dura en el trabajo" + "Guardar" → stored for the check-in's week (`week_start` = the Monday of the chosen day's week); the reminder and its count disappear; the screen closes with one Confirm.
- [x] After answering, the center shows this week's check-in as a non-counted row; opening it shows the saved energy and notes, and saving a change updates the same week; after the next check-in day it opens the new week's check-in instead.
- [x] The check-in shows volume in kg, workouts and sets this week vs last week with the difference, the records first set this week and the weight trend (this week's average − last week's), matching "Entrenamiento" and Peso for the same data; a week with no weigh-in gives "Sin datos suficientes"; a week without workouts shows zeros.
- [x] The summary covers the Monday–Sunday week containing the check-in day vs the week before: a Sunday check-in opened on the next Monday (or Saturday) shows the closed figures of the week that just ended, not the new week's; it is recomputed on each open, so a session or weight added late on that Sunday appears when reopened on Monday.
- [x] Last week unanswered → once the next check-in day arrives only the current check-in is listed (never two).
- [x] Turning off the check-in switch hides it; turning it on restores it.
- [x] pgTAP: `weekly_check_ins` RLS and uniqueness, energy range, `check_in_weekday` range and default, `weekly_records` owner-scoped with and without warm-ups.
- [x] The open check-in draft (energy, notes) and the visible screen survive background and process death (ADR-40); offline: "Guardar" disabled with the standard hint.

**Host tests:** `CheckInWindowTest` (each weekday, answered/unanswered, window spanning the ISO week boundary, missed check-in, change of day mid-window, editable-until rule, time-zone boundary); `ComputeRemindersTest` additions (check-in kind, silenced); `WeeklySummaryTest` (volume difference, records in range, weight trend with 0/1/many weigh-ins; range = Monday–Sunday of the check-in day's week vs the previous week whatever the open date: opened on the check-in day, on the next Monday and later in the window; Sunday 23:59 vs Monday 00:00 boundary in the device time zone; recomputed on each open); repository mapping tests; `CheckInViewModelTest` (save, validation, offline, summary error independent of the form, reopening on Monday loads the saved answer and the closed figures of its week); `CheckInScreen` Robolectric tests (summary cards, selector, notes, save closes, discard dialog, restoration); Preferencias "Día del check-in" test (picker applies, section saves only the weekday); center test with the check-in row and switch; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before Checkpoint E10 is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 22 files (size note: the backend is one migration with one table, one column and one RPC; if the planner counts more than ~25 files, it runs backend + core first and UI second as slices of the same PR).
**Dependencies:** T-135 merged and `deploy-supabase` green
**Files likely touched:** `supabase/migrations/<ts>_weekly_check_ins.sql`, `supabase/tests/weekly_check_ins_test.sql`, `shared/…/domain/model/{WeeklyCheckIn,WeeklySummary}.kt`, `shared/…/domain/usecase/{CheckInWindow,WeeklySummary,ComputeReminders}.kt`, `shared/…/domain/port/WeeklyCheckInRepository.kt`, `shared/…/data/repository/{SupabaseWeeklyCheckInRepository,…Profile…}.kt`, `shared/…/di/`, new `composeApp/…/feature/checkin/{CheckInScreen,CheckInViewModel,CheckInUiState}.kt`, `feature/notifications/NotificationCenterScreen.kt`, `feature/profile/{PreferencesSectionScreen,PreferencesViewModel,…}.kt`, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/03-data-model.md`, `planning/04-business-rules.md` (check-in window and summary rules), tests
**Estimated scope:** L

---

### Checkpoint E10 — Reminders and your weekly check-in 👤 (14 items + leave and come back) *(agreed with the user, 2026-10-28: one checkpoint for the whole epic)*
Journey: the reminders find you when you open the app, take you to log the missing data and go away once it is logged; on your chosen day the check-in appears, you review your week in figures, rate your energy and write a note. Why here: right after T-136, the last implementation task, so it is also the final regression of E10; its approval releases the closure T-137. The user chose one checkpoint over two of 7 items.
- [x] T-135 and T-136 merged, CI green, `deploy-supabase` applied both migrations (data intact), every reported bug fixed.
- [ ] 👤 Install the APK and try:
  1. Entry point (T-135): on the tab the app opens on, the "Centro de avisos" entry shows how many reminders are pending; with none, it shows no number.
  2. Weight reminder (US-58): if your last weigh-in is 2 or more days old (or delete your last weights in "Historial de peso" so it is), open the app: "No te has pesado en N días" is listed with its date; with a weight from yesterday there is no weight reminder.
  3. Circumference reminder (US-58): with no circumference in the last 14 days, "No te has medido en 2 semanas" is listed; after logging one, it is not.
  4. Tap to log (US-57): tap the weight reminder → the weight entry opens; save a weight → back in the center the reminder is gone and the count dropped; do the same with the circumference reminder.
  5. Health Connect (US-58): with the weight reminder listed, weigh yourself on a Health Connect scale (or add today's weight in Health Connect), then Peso → "Sincronizar": the reminder disappears.
  6. Silence a type (US-57): turn off "Recordatorio de peso" in "Centro de avisos": the weight reminder and its count disappear; close and reopen the app: still off; turn it back on: the reminder returns.
  7. Next open and states (T-135): leave the app in the background overnight without weighing in and open it in the morning: the reminder is listed without doing anything; turn on airplane mode: the switches are disabled with "Sin conexión: no se puede …"; with nothing pending the center shows its empty state.
  8. Check-in day (T-136): Preferencias → "Día del check-in" shows "domingo"; set it to today's weekday → "Centro de avisos" lists the check-in and the count goes up; leave it unanswered and open the app the next day: it is still listed (Q1 B).
  9. Weekly summary: open the check-in: volume in kg, workouts and sets this week vs last week, the records you set this week and the weight trend (this week's average − last week's, or "Sin datos suficientes"); compare them with "Entrenamiento" and Peso.
  10. Answer: energy 4 out of 5, notes "semana dura en el trabajo", "Guardar": it closes with one vibration, the reminder and its count are gone and stay gone after reopening the app.
  11. Reopen and edit (Q3 B): in "Centro de avisos" this week's answered check-in is still there, not counted; open it: energy and notes as saved; change the note and "Guardar": the change is kept.
  12. Editing safety and silencing: change the energy and go back: "¿Descartar los cambios?" ("Seguir editando" keeps the draft, "Descartar" leaves); turn off the check-in switch in "Centro de avisos": it disappears; turn it on: it returns.
  13. Check-in states: in airplane mode "Guardar" is disabled with "Sin conexión: no se puede …"; if the summary fails to load, its "Reintentar" works and you can still answer; a week with no workouts shows zeros.
  14. Final regression of E10: with a missing weigh-in, an old circumference and the check-in due, the entry shows 3 and each row opens its entry; log a workout with a record and a weight, reopen the check-in: the summary includes them; Diario, Peso and Entreno work as before.
  - Leave the app and come back on "Centro de avisos" scrolled down and with the check-in half answered (energy chosen, a note typed): the same tab, screen, scroll and draft are restored.
  - Out of scope: the missed-check-in rollover over a real week (covered by host tests); the coach's commentary on the check-in (E13, US-71); the training streak (deferred); system notifications (never, ADR-73); the automatic daily Health Connect sync (E11).

---

## T-137: E10 closure → `v1.x` · S

**Story:** E10 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E10's approval)

**Description:** Closes E10 once Checkpoint E10 is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) to the version agreed with the user (`09-open-questions.md`, "Release version of each Phase 2 epic"; proposed `v1.1.0`), tag, retrospective, archive of the E10 log lines (`06` §6). No 👤 checkpoint of its own.

**Acceptance criteria:**
- [ ] T-135, T-136 (and any fix tasks) merged with green CI; `deploy-supabase` green after the last migration.
- [ ] ux-reviewer ran in plan and diff mode on both UI tasks; `check-ux-conventions.sh` passes.
- [ ] `planning/03-data-model.md` and `04-business-rules.md` describe `notification_settings`, `weekly_check_ins`, `profiles.check_in_weekday`, `weekly_records` and the reminder and check-in rules.
- [ ] Checkpoint E10 approved; every reported bug fixed.
- [ ] Version agreed and bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [ ] `git tag` lists the E10 tag.

**Dependencies:** T-136 and Checkpoint E10 approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E10.md`
**Estimated scope:** S
