# Tasks — Phase 2

Plan of the current epic: [tasks/plan.md](plan.md) (created by the epic-planner) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-138 (E10 checkpoint fix). Numbering continues from here.

**Next:** T-144 ("Actividad" like "Peso", US-64), added from Checkpoint E11 feedback (2026-10-28). After it merges, Checkpoint E11 is re-presented; its approval releases T-143 (E11 closure).

---

## 👤 Checkpoints

E10 (agreed with the user, 2026-10-28): one **Checkpoint E10** after T-136 (whole epic, also the final regression; its approval releases T-137). Details under the E10 section.

E11 (agreed with the user, 2026-10-28): a 👤 spike report after T-139 (device result, not a functionality checkpoint; gates T-141) and **one Checkpoint E11** after T-142 (watch data + daily automatic sync, also the final regression; its approval releases T-143). Details under the E11 section.

## Notes for later epics

- **E11:** the `READ_HEALTH_DATA_HISTORY` pre-check is replaced by the spike T-139 (user decision, 2026-10-28; see the E11 section).
- **E12:** verify screen-off alert behaviour on Android 17; 👤 the user picks the new alert sound (E9 point 16 candidates).
- **E13:** before the breakdown verify the sources marked *verify* in `planning/07-coach-design.md` §10, the Anthropic API data policy and current prices; 👤 Anthropic account, monthly spending limit and `ANTHROPIC_API_KEY` Supabase secret.

---

# E11 · Health Connect daily sync → `v1.x`

Stories: `planning/epics/E11-health-connect-daily-sync.md` (US-60, US-61, US-62, US-63). Plan: [tasks/plan.md](plan.md). Decisions: ADR-74 (amends ADR-08; daily automatic trigger, new read types, writes stay weight and body fat), ADR-47/48 (a Health Connect weight wins), ADR-29 (cascade), ADR-40 (restoration), ADR-73 (no background job). Rules: `planning/phase1/04-business-rules.md` §3.7 (Health Connect import, dedup, own records skipped), `05-design-system.md` §8–§10. Device facts (user, 2026-10-28): the data source is Zepp (phone UI in Spanish) and Health Connect holds steps for at least 5 months.
Sizing: 5 tasks. The spike T-139 is S and stays alone (user decision: it is the first task, and its device report decides the first-sync window of T-141). US-61 + US-62 + US-63 are one functionality (watch data through "Sincronizar") but exceed ~15 files with heavy backend (3 tables, an import RPC, pgTAP) and UI that review apart, so they split into T-140 (backend, M) and T-141 (core + UI, L). US-60 is T-142 (M; no migration, its column ships in T-140). Closure T-143 stays S (it waits for Checkpoint E11-B).
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1); T-140 runs the backend flow (`06` §2.2). UI scenarios are covered by host (Robolectric) tests; the user verifies by hand with `python scripts/deliver-apk.py`.
Questions: E11 Q1 (first-sync window, decided by the spike result; **BLOCKING T-141**) in `tasks/plan.md`. E11 Q2 resolved by the user (2026-10-28): coming back from the background on a new local day counts as the first open. T-139, T-140 and T-142 are not blocked by questions.
Checkpoints (agreed with the user, 2026-10-28): 👤 spike report after T-139 (not a functionality checkpoint: one device result that gates T-141); **one Checkpoint E11** after T-142 (items of the watch data and the daily sync merged, also the final regression; releases T-143). T-141 merges with no checkpoint after it.

---

## T-139: Spike: `READ_HEALTH_DATA_HISTORY` on the device · S

**Story:** US-63 (History older than 30 days; technical note) · **Flow:** platform + minimal UI · **Size:** S (cannot merge: the user's device report decides the first-sync window of T-141; user decision that it is the first task of E11)

**Description:** Find out on the user's phone (Zepp → Health Connect, at least 5 months of steps) whether the app can read data older than 30 days. (1) Declare `android.permission.health.READ_HEALTH_DATA_HISTORY` and `android.permission.health.READ_STEPS` in `androidApp/src/main/AndroidManifest.xml` (steps is the type the user knows has 5 months of history; T-141 keeps or removes the history declaration according to the result). (2) Check `HealthConnectFeatures.getFeatureStatus(FEATURE_READ_HEALTH_DATA_HISTORY)` (available / unavailable) through the existing `HealthConnectDataSource` (new port method or a diagnostic helper; the planner chooses, keeping the port pure). (3) Request the steps read permission plus the history permission with the existing permission launcher. (4) Read steps (and weights, for comparison) in two windows, 60–90 days ago and 0–30 days ago, and count the records and the oldest record's date in each. (5) Show the result in a **temporary diagnostic** screen reachable from Perfil → "Acerca de" (one `AppListRow` "Diagnóstico de Health Connect" opening an `AppScreen` with plain `AppListRow`s: feature status, history permission granted yes/no, records 60–90 days ago, records 0–30 days ago, oldest record date; a "Comprobar" button that requests the permissions and runs the read). The screen and its row are removed by T-141 (sweep item there); its copy is not reviewed beyond R-36/R-37. Nothing is stored server-side.

**Acceptance criteria:**
- [x] The manifest declares `READ_HEALTH_DATA_HISTORY` and `READ_STEPS`; no new write permission.
- [x] "Comprobar" shows the feature status, whether the history permission was granted, and the record counts and oldest date for 60–90 and 0–30 days ago.
- [x] Denying the permissions shows them as not granted and reads nothing; Health Connect missing shows the existing unavailable state; no crash.
- [x] 👤 Spike report: the user runs "Comprobar" on the phone and reports the five values; the PM records them and the resulting branch of E11 Q1 in `tasks/plan.md` (docs PR) before T-141 starts.

**Host tests:** a diagnostic ViewModel test (granted, denied, feature unavailable, read failure) with a fake data source; a Robolectric test of the diagnostic screen (rows and "Comprobar"). The real Health Connect behaviour is verified only on the device (the existing data source is "verified by hand on a phone only").

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.
- [x] 👤 Spike report received and recorded (gates T-141).

**Agent runs:** planner, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer = 5 (no ux-reviewer passes: temporary screen removed in T-141).
**Dependencies:** E10 closed (`v1.1.0`); breakdown agreed by the user
**Files likely touched:** `androidApp/src/main/AndroidManifest.xml`, `composeApp/…/platform/HealthConnectDataSource.kt` (or a `HealthConnectDiagnostics.kt`), `composeApp/…/platform/HealthConnectLauncher.android.kt`, new `composeApp/…/feature/profile/HealthDiagnosticsScreen.kt` + ViewModel, the "Acerca de" screen, `app/{AppRoutes,AppNavHost}.kt`, strings, tests
**Estimated scope:** S

---

## T-140: Watch-data backend: daily activity, sleep, resting heart rate (US-61, US-62 backend) · M

**Story:** US-61, US-62 (server side), US-60 (`last_auto_sync_date` column) · **Flow:** backend + data · **Size:** M (split from T-141: together they exceed ~25 files with heavy backend and UI that review apart)

**Description:** One migration for the epic (so only one production deploy): (1) `daily_activity` (`user_id`, `day` date in the device time zone, `steps` integer, `active_kcal`, `total_kcal` numeric, nullable each, range checks), `unique (user_id, day)`; a re-sync of a day **replaces** its values. (2) `sleep_sessions` (`user_id`, `started_at`, `ended_at`, `minutes` integer, `stages` jsonb, `external_id` text not null), `unique (user_id, external_id)`, check `ended_at > started_at`. (3) `resting_heart_rate` (`user_id`, `day`, `bpm` smallint with a range check, `external_id`), `unique (user_id, day)`. (4) `health_sync_state.last_auto_sync_date date null`. Owner RLS enabled and forced with `(select auth.uid())`, explicit grants to `authenticated`, `on delete cascade` from `auth.users` (ADR-29; extend `delete_account_cascade.test.sql`). (5) An idempotent import path: one RPC `import_health_activity(p_daily jsonb, p_sleep jsonb, p_resting_hr jsonb)` (or one per type, planner's choice), `security invoker`, `set search_path = ''`, input caps and `22023` on bad input like `import_health_measurements`; daily rows and resting heart rate upsert per day (replace), sleep upserts per `external_id`; returns the counts. (6) An RPC or column update that sets `last_auto_sync_date` only on success (US-60 note: a failed automatic sync never sets it). (7) **Data layer:** domain models (`DailyActivity`, `SleepSession`, `RestingHeartRate`), the repository port(s) and the supabase-kt implementation with DTO mapping; `HealthSyncState` gains `lastAutoSyncDate`. No UI and no Health Connect reads here (T-141).

**Acceptance criteria:**
- [x] Importing the same day's steps twice leaves one row with the last values (US-62 "A second sync the same day adds nothing"); importing the same sleep `external_id` twice leaves one session.
- [x] A payload without resting heart rate stores none and returns no error (US-61 "A type the watch does not provide").
- [x] User A cannot read or write user B's rows in the three tables; deleting the account removes them.
- [x] `last_auto_sync_date` is set only by the success path.
- [x] `planning/03-data-model.md` describes the three tables and the column; `04-business-rules.md` gets the daily-total rule (one row per user and day, replaced on re-sync; day = device time zone).

**Host tests:** repository mapping tests (DTO ↔ model for the three types, nulls, `lastAutoSyncDate`); pgTAP: RLS enabled and forced and cross-user isolation per table, uniqueness, replace-on-resync for `daily_activity` and `resting_heart_rate`, sleep dedup by `external_id`, range checks, RPC input validation (`22023`), cascade on account deletion.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-141 starts).

**Agent runs:** planner, backend-developer, kotlin-core-developer, pr-creator, pr-reviewer = 5.
**Dependencies:** T-139 merged (not its report: the backend does not depend on the window)
**Files likely touched:** `supabase/migrations/<ts>_health_activity.sql`, `supabase/tests/{daily_activity,sleep_sessions,resting_heart_rate}.test.sql`, `supabase/tests/delete_account_cascade.test.sql`, `shared/…/domain/model/Health.kt` (or new `HealthActivity.kt`), `shared/…/domain/repository/HealthSyncRepository.kt` (or a new `HealthActivityRepository.kt`), `shared/…/data/remote/HealthSyncDto.kt`, `shared/…/data/repository/SupabaseHealthSyncRepository.kt`, `shared/…/di/`, `planning/03-data-model.md`, `planning/04-business-rules.md`, tests
**Estimated scope:** M

---

## T-141: Read sleep, steps, calories and resting heart rate with "Sincronizar" (US-61, US-62, US-63) · L

**Story:** US-61, US-62, US-63 · **Flow:** platform + core + UI · **Size:** L · **Scenarios:** US-61 Last night's sleep is stored; Yesterday's steps are stored; A type the watch does not provide; Partial permission · US-62 A second sync the same day adds nothing; Records written by AppFitness are not imported · US-63 Write permissions are limited to weight and body fat; History older than 30 days (per the spike branch)

**First-sync window (E11 Q1, decided by the T-139 report; both branches planned):**
- **Branch A (history readable):** the feature is available, the permission is granted and records 60–90 days ago are returned. Keep `READ_HEALTH_DATA_HISTORY` declared; request it together with the read permissions ("the app asks for it before reading older data"); the first sync of the new types reads the last **90 days** (same window as the weight sync); US-63 "History older than 30 days" stays as written.
- **Branch B (history not readable):** the feature is unavailable or nothing older than 30 days is returned. Remove the `READ_HEALTH_DATA_HISTORY` declaration; the first sync of the new types reads the last **30 days** (what Health Connect allows); the weight sync keeps its 90-day request (Health Connect returns what it allows). US-63's second scenario and the E11 scope line are rewritten in the epic and `01-scope.md` with the user's OK when the report is recorded (product wording, not decided here).

**Description:** (1) **Platform:** `HealthConnectDataSource` reads `SleepSessionRecord` (with stages), `StepsRecord`, `ActiveCaloriesBurnedRecord`, `TotalCaloriesBurnedRecord` and `RestingHeartRateRecord`, each only when its read permission is granted (partial permission: a missing type is skipped, never an error); steps and calories as daily totals per local day (aggregation by day so overlapping records are not double-counted; planner confirms the API); own-origin records skipped as today (US-62). The Changes token covers the new types; touched days are re-read whole so a day total is replaced, never added to. (2) **Permissions (US-63):** the requested set = reads of the existing six types plus the five new ones (+ history per branch); writes stay `WRITE_WEIGHT` and `WRITE_BODY_FAT` only, and the manifest's unused `WRITE_LEAN_BODY_MASS`, `WRITE_BODY_WATER_MASS`, `WRITE_BONE_MASS`, `WRITE_BASAL_METABOLIC_RATE` declarations are removed; the rationale activity text covers the new types. A user who granted only the E5 types is asked again for the new ones from "Sincronizar". (3) **Core:** the sync use case (extend `SyncHealthMeasurements` or a `SyncHealthData` that composes it) imports every granted type in one "Sincronizar" and returns a per-type outcome; the weight path and its reminder refresh (T-135) are unchanged. Sleep belongs to the local day it ends on; several resting-heart-rate readings in a day keep the latest (assumptions A1–A2 in the plan). (4) **UI — templates and patterns:** where the values are shown is chosen by ux-reviewer in the plan pass (epic note; candidates: an "Actividad" `SectionHeader` on Peso under the Health Connect `MetricCard`, or a Perfil → "Estadísticas" destination). Expected patterns: `MetricCard`s (sueño de anoche with duration, pasos de ayer, calorías activas/totales, frecuencia cardiaca en reposo) with `AppNumberFormat`/`AppDateFormat`; a type with no data shows no card and no error; loading skeleton, `ErrorState` "Reintentar", offline per R-30. The existing "Sincronizar" button and status line on Peso stay where they are. Spanish copy by ux-reviewer per the glossary (§10). (5) **Sweep:** remove T-139's temporary diagnostic screen and its "Acerca de" row.

**Acceptance criteria:**
- [x] Granting every read permission and tapping "Sincronizar" stores last night's sleep with its duration, yesterday's steps (e.g. 8500 shows as 8500) and calories, and the resting heart rate when the watch writes it; the values are visible where ux-reviewer placed them.
- [x] No resting heart rate in Health Connect → none stored, nothing shown for it, no error.
- [x] Steps granted and sleep denied → steps stored, sleep not read, no error.
- [x] Tapping "Sincronizar" twice → yesterday's steps appear once with the same total; a weight written by AppFitness is not imported.
- [x] The app requests write access only for weight and body fat (manifest and requested set); the four unused write declarations are gone.
- [x] Branch A: the history permission is asked before older data is read and the first sync brings 90 days; Branch B: the first sync brings 30 days and no history permission is declared.
- [x] The diagnostic screen of T-139 is gone.
- [x] Empty, loading, error and offline states; the screen showing the values and its scroll survive background and process death (ADR-40).

**Host tests:** sync use-case tests with a fake data source (all types, partial permission, missing type, second sync idempotent, own-origin skipped, touched day re-read and replaced, first window 90/30 by branch, local-day assignment of sleep and daily totals across midnight); requested-permission-set test (reads, writes only weight and fat, history per branch); `HealthSyncViewModel` tests (per-type outcome, permission re-request for new types); Robolectric tests of the values surface (cards, no card for a missing type, states) and restoration.

**Verification:**
- [x] lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 20 files (no backend: T-140). No checkpoint after it: its items are tested at Checkpoint E11 after T-142.
**Dependencies:** T-140 merged and `deploy-supabase` green; T-139 spike report recorded (E11 Q1 resolved)
**Files likely touched:** `androidApp/src/main/AndroidManifest.xml`, `composeApp/…/platform/{HealthConnectDataSource,HealthConnectLauncher.android}.kt`, `shared/…/domain/port/HealthDataSource.kt`, `shared/…/domain/model/Health.kt`, `shared/…/domain/usecase/SyncHealthMeasurements.kt` (+ new `SyncHealthData.kt`), `feature/weight/{HealthSyncViewModel,WeightScreen}.kt` or the surface ux-reviewer picks, removal of `feature/profile/HealthDiagnosticsScreen.kt`, strings, `planning/phase1/04-business-rules.md` §3.7 pointer → `04-business-rules.md` (Health Connect read types), tests
**Estimated scope:** L

---

## T-142: Daily automatic sync on the first open (US-60) · M

**Story:** US-60 · **Flow:** core + UI · **Size:** M · **Scenarios:** First open of the day syncs; Second open the same day does not sync automatically; The button still syncs on demand; No connection keeps the last data; Permission missing

**Description:** (1) **Core:** a pure `ShouldAutoSync` rule (today's local date vs `last_auto_sync_date`, Health Connect available, online) and an app-level trigger (next to T-135's reminder refresh in `AppViewModel` or a shared holder) that runs the T-141 sync for every granted type on the first open of each local day, where a cold start and a return from the background (process `ON_START`) on a new local day both count as "the first open" (E11 Q2, resolved by the user 2026-10-28), never in the background (ADR-74). Success sets `last_auto_sync_date` (T-140); failure or offline leaves it, so the next open retries. The automatic sync **never** launches the permission dialog: with no read permission it reads nothing and the status line asks for permissions. One sync at a time: a tap on "Sincronizar" while the automatic sync runs is ignored or joins it (planner's choice, tested). After a sync that stores a weight, the reminder state refreshes (T-135 hook). (2) **UI — templates and patterns:** the existing Health Connect status line on Peso (`MetricCard` supporting text, T-123) gains the automatic outcomes: synced with its time ("the status line shows the time of the sync"), "the last sync was not possible" when offline or failed, and the permission request; copy by ux-reviewer per R-37 and the glossary (§10). No new screen and no new component. The "Sincronizar" button keeps reading on demand.

**Acceptance criteria:**
- [x] Last automatic sync yesterday → opening the app, or bringing it back from the background on the new day, reads every granted type and the status line shows the sync time.
- [x] Already synced automatically today → opening again reads nothing.
- [x] After today's automatic sync, a fresh weigh-in + "Sincronizar" → Health Connect is read and today's weight is shown (and the weight reminder clears).
- [x] Offline on open → stored data shown, the status line says the last sync was not possible, `last_auto_sync_date` unchanged; the next open online syncs.
- [x] No read permission → the status line asks for the permissions, no dialog opens by itself, nothing is read.
- [x] Status line and screen survive background and process death (ADR-40).

**Host tests:** `ShouldAutoSyncTest` (yesterday / today / never, local-midnight boundary, offline, unavailable); trigger tests (cold start and `ON_START` on a new day both trigger, runs once per day, failure does not set the date and retries on the next open, no permission → no read and no dialog, button during an automatic sync, reminder refresh after a weight); `HealthSyncViewModel` status-line tests for the automatic outcomes; Robolectric status-line test and restoration.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 12 files.
**Dependencies:** T-141 merged (no checkpoint in between; E11 Q2 resolved)
**Files likely touched:** `shared/…/domain/usecase/ShouldAutoSync.kt`, `shared/…/domain/usecase/SyncHealthData.kt`, `composeApp/…/app/AppViewModel.kt` (or the shared holder), `feature/weight/{HealthSyncViewModel,WeightScreen}.kt`, strings, `planning/04-business-rules.md` (daily trigger rule; `phase1` §3.7 "Sync is manual" superseded), tests
**Estimated scope:** M

---

## T-144: "Actividad" like "Peso": summary, Sincronizar, metric chart and calendar (US-64) · L

**Story:** US-64 · **Flow:** core + UI · **Size:** L (the planner may propose splitting it) · **Scenarios:** all of US-64 · **Origin:** user feedback at Checkpoint E11 (2026-10-28)

**Description:** The user expects "Actividad" to give the same experience as "Peso". (1) **Core:** queries of `daily_activity`, `sleep_sessions` and `resting_heart_rate` by range (1S, 1M, 3M, 6M, 1A) and by day; sleep stages only if the planner finds them already stored or a small migration is justified. (2) **UI:** a summary of the day with an icon per metric, the Health Connect status line with "Sincronizar" (reusing `HealthStatusLine` and the sync of T-141/T-142), a chart with a metric selector (default "Pasos") and range chips, and a calendar icon top right to pick a day. ux-reviewer decides the rendering of each metric (feet for steps, sleep distribution or a sleep figure, calorie and flame imagery, a heart for resting heart rate, animations), the Spanish copy and the testTags, reuses the Peso components (a new component retires ad-hoc copies, per the UX rule) and proposes the §8 rule "every screen with watch data has the status line and button"; the empty message of "Actividad" no longer sends the user to Peso.

**Acceptance criteria:**
- [ ] Summary of today with the five metrics and their icons.
- [ ] "Sincronizar" on "Actividad" syncs like on Peso and shows the status line.
- [ ] Chart defaults to Pasos; the metric and the range (1S, 1M, 3M, 6M, 1A) switch.
- [ ] Sleep shows the stage distribution when available, otherwise the duration.
- [ ] The calendar picks another day; a day or metric without data says so.
- [ ] Tab, metric, range, day and scroll survive leaving the app and process death (ADR-40).

**Host tests:** range and day query tests; ViewModel tests (default metric, switching, calendar, empty states, sync); Robolectric screen and restoration tests.

**Verification:**
- [ ] lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-142 merged
**Files likely touched:** to be defined by the planner (`feature/activity/…`, shared repository and queries, strings, `05-design-system.md` §8)
**Estimated scope:** L

---

### Checkpoint E11 — Your watch data, synced by itself each day 👤 (13 items + leave and come back) *(agreed with the user, 2026-10-28: one checkpoint for the whole epic)*
Journey: grant the new permissions once and see last night's sleep, yesterday's steps, calories and resting heart rate, never duplicated; from then on the app brings them on the first open of each day, and the button still brings a fresh weigh-in. Why here: right after T-142, the last implementation task, so it is also the final regression of E11; its approval releases T-143. The user chose one checkpoint over two (E11-A after T-141 and E11-B after T-142).
- [ ] T-140, T-141, T-142 and T-144 merged, CI green, `deploy-supabase` applied T-140's migration (data intact), every reported bug fixed.
- [ ] 👤 Install the APK and try:
  1. Permissions (US-63): tap "Sincronizar": Health Connect asks to read sleep, steps, active and total calories and resting heart rate (and, in branch A, older history); in Ajustes → Health Connect → AppFitness, write access lists only weight and body fat.
  2. Sleep (US-61): after the sync, last night's sleep appears with its duration; compare it with Zepp.
  3. Steps and calories (US-61): yesterday's steps and calories match Zepp's figures for yesterday.
  4. Resting heart rate (US-61): if Zepp writes it, it appears; if not, nothing is shown for it and there is no error.
  5. Partial permission (US-61): in Health Connect revoke sleep only, tap "Sincronizar": steps still update, no sleep is read, no error; grant it again.
  6. No duplicates (US-62): tap "Sincronizar" twice: yesterday's steps appear once with the same total; log a weight in the app, sync: it is not imported a second time.
  7. History (US-63): the first sync brings older days (90 days in branch A, about 30 in branch B).
  8. First open of the day (US-60, Q2): open the app in the morning, or bring it back from the background on a new day, without touching anything: Peso's status line shows this morning's sync time and last night's sleep and yesterday's steps are there.
  9. Second open (US-60): close the app and open it again the same day: the status line keeps the same time (no new automatic sync).
  10. Fresh weigh-in (US-60): weigh yourself on the scale, then "Sincronizar": today's weight appears and the weight reminder in "Centro de avisos" disappears.
  11. Offline and permission missing (US-60): on a new day open the app in airplane mode: your data is shown and the status line says the last sync was not possible; reconnect and reopen: it syncs. Revoke the read permissions and open on a new day: the status line asks for them, no dialog opens by itself, nothing new is read.
  12. Actividad like Peso (US-64, T-144): summary of today with icons, "Sincronizar" button, chart defaulting to Pasos with 1S/1M/3M/6M/1A, metric switch, calendar icon to pick a day.
  13. Final regression of E11: Peso, its chart and weight history, "Centro de avisos" and the check-in, Diario and Entreno work as before; no value appears twice.
  - Leave the app and come back on the screen with the watch data scrolled down: the same tab, screen and scroll are restored.
  - Out of scope: background sync (never, ADR-74); writing food or training to Health Connect; water (Phase 3); the coach reading these values (E13).

---

## T-143: E11 closure → `v1.x` · S

**Story:** E11 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E11's approval)

**Description:** Closes E11 once Checkpoint E11 is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) per the versioning rule (`06` §6; minor per epic, proposed `1.2.0`), tag, retrospective, archive of the E11 log lines. No 👤 checkpoint of its own.

**Acceptance criteria:**
- [ ] T-139…T-142 (and any fix tasks) merged with green CI; `deploy-supabase` green after T-140's migration.
- [ ] ux-reviewer ran in plan and diff mode on T-141 and T-142; `check-ux-conventions.sh` passes.
- [ ] `planning/03-data-model.md` and `04-business-rules.md` describe the three tables, `last_auto_sync_date`, the read types and the daily trigger.
- [ ] Checkpoint E11 approved; every reported bug fixed.
- [ ] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [ ] `git tag` lists the E11 tag.

**Dependencies:** T-144 and Checkpoint E11 approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E11.md`
**Estimated scope:** S

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

## T-138: Move the reminder settings from "Centro de avisos" to Preferencias · S

**Origin:** Checkpoint E10 feedback (user, 2026-10-28): "move avisos preferences from avisos center to users preferences". Convention written to `05` §8 "Decided".
**Description:** The three switches ("Recordatorio de peso", "Recordatorio de medidas", "Check-in semanal") leave the center's settings card; the center only lists reminders and the answered check-in. Preferencias gets one hub section "Avisos" (replaces T-136's "Check-in semanal" section) holding the three switches and "Día del check-in", with one "Guardar" (explicit-save rule, offline hint R-30). Sweep every screen with a setting inside a feature screen (R-39). Same data (`notification_settings`, `profiles.check_in_weekday`), no migration; update US-57 Gherkin wording, `05` §6.2 and the host tests (center, Preferencias, restoration).
**Decision taken (PM, recorded):** one "Avisos" section instead of switches spread over sections; the center keeps no settings link.
**Verification:** [x] lint + `testLocalDebugUnitTest` green (CI, #313); [ ] re-test at Checkpoint E10: silence/restore each type from Preferencias, center has no switches.
**Dependencies:** Checkpoint E10 open · **Estimated scope:** S

---

### Checkpoint E10 — Reminders and your weekly check-in 👤 (15 items + leave and come back) *(agreed with the user, 2026-10-28: one checkpoint for the whole epic)*
Journey: the reminders find you when you open the app, take you to log the missing data and go away once it is logged; on your chosen day the check-in appears, you review your week in figures, rate your energy and write a note. Why here: right after T-136, the last implementation task, so it is also the final regression of E10; its approval releases the closure T-137. The user chose one checkpoint over two of 7 items.
- [x] T-135 and T-136 merged, CI green, `deploy-supabase` applied both migrations (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-28)*:
  1. Entry point (T-135): on the tab the app opens on, the "Centro de avisos" entry shows how many reminders are pending; with none, it shows no number.
  2. Weight reminder (US-58): if your last weigh-in is 2 or more days old (or delete your last weights in "Historial de peso" so it is), open the app: "No te has pesado en N días" is listed with its date; with a weight from yesterday there is no weight reminder.
  3. Circumference reminder (US-58): with no circumference in the last 14 days, "No te has medido en 2 semanas" is listed; after logging one, it is not.
  4. Tap to log (US-57): tap the weight reminder → the weight entry opens; save a weight → back in the center the reminder is gone and the count dropped; do the same with the circumference reminder.
  5. Health Connect (US-58): with the weight reminder listed, weigh yourself on a Health Connect scale (or add today's weight in Health Connect), then Peso → "Sincronizar": the reminder disappears.
  6. Silence a type (US-57): turn off "Recordatorio de peso" in Preferencias → "Avisos" (Guardar): the weight reminder and its count disappear; close and reopen the app: still off; turn it back on: the reminder returns.
  7. Next open and states (T-135): leave the app in the background overnight without weighing in and open it in the morning: the reminder is listed without doing anything; turn on airplane mode: in Preferencias → Avisos "Guardar" is disabled with "Sin conexión: no se puede …"; with nothing pending the center shows its empty state.
  8. Check-in day (T-136): Preferencias → Avisos → "Día del check-in" shows "domingo"; set it to today's weekday → "Centro de avisos" lists the check-in and the count goes up; leave it unanswered and open the app the next day: it is still listed (Q1 B).
  9. Weekly summary: open the check-in: volume in kg, workouts and sets this week vs last week, the records you set this week and the weight trend (this week's average − last week's, or "Sin datos suficientes"); compare them with "Entrenamiento" and Peso.
  10. Answer: energy 4 out of 5, notes "semana dura en el trabajo", "Guardar": it closes with one vibration, the reminder and its count are gone and stay gone after reopening the app.
  11. Reopen and edit (Q3 B): in "Centro de avisos" this week's answered check-in is still there, not counted; open it: energy and notes as saved; change the note and "Guardar": the change is kept.
  12. Editing safety and silencing: change the energy and go back: "¿Descartar los cambios?" ("Seguir editando" keeps the draft, "Descartar" leaves); turn off the check-in switch in Preferencias → "Avisos" (Guardar): it disappears; turn it on: it returns.
  13. Check-in states: in airplane mode "Guardar" is disabled with "Sin conexión: no se puede …"; if the summary fails to load, its "Reintentar" works and you can still answer; a week with no workouts shows zeros.
  14. Final regression of E10: with a missing weigh-in, an old circumference and the check-in due, the entry shows 3 and each row opens its entry; log a workout with a record and a weight, reopen the check-in: the summary includes them; Diario, Peso and Entreno work as before.
  15. Licencias (bug fix bundled in T-138): Perfil → Acerca de → "Licencias de código abierto" opens the licences list without crashing; open one entry and go back.
  - Leave the app and come back on "Centro de avisos" scrolled down and with the check-in half answered (energy chosen, a note typed): the same tab, screen, scroll and draft are restored.
  - Out of scope: the missed-check-in rollover over a real week (covered by host tests); the coach's commentary on the check-in (E13, US-71); the training streak (deferred); system notifications (never, ADR-73); the automatic daily Health Connect sync (E11).

---

## T-137: E10 closure → `v1.x` · S

**Story:** E10 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E10's approval)

**Description:** Closes E10 once Checkpoint E10 is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) to `1.1.0` (versioning rule, `06` §6), tag, retrospective, archive of the E10 log lines (`06` §6). No 👤 checkpoint of its own.

**Acceptance criteria:**
- [x] T-135, T-136 (and any fix tasks) merged with green CI; `deploy-supabase` green after the last migration.
- [x] ux-reviewer ran in plan and diff mode on both UI tasks; `check-ux-conventions.sh` passes.
- [x] `planning/03-data-model.md` and `04-business-rules.md` describe `notification_settings`, `weekly_check_ins`, `profiles.check_in_weekday`, `weekly_records` and the reminder and check-in rules.
- [x] Checkpoint E10 approved; every reported bug fixed.
- [x] Version agreed and bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [x] `git tag` lists the E10 tag.

**Dependencies:** T-136 and Checkpoint E10 approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E10.md`
**Estimated scope:** S
