# Tasks — Phase 2

Plan of the current epic: [tasks/plan.md](plan.md) (created by the epic-planner) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-155 (E12-B feedback fixes, round 3). Numbering continues from here.

**Next:** E13 breakdown by the epic-planner in a fresh session (E12 closed, `v1.3.0`, 2026-10-29; the user agrees the task list and checkpoints first). Carry-over from T-155 (non-blocking): duration-based records show "kg" on weight×seconds (e.g. "Mejor serie 2560 kg"). T-154 review nits (non-blocking): the picker footer with the keyboard open has no host test (Robolectric cannot simulate the IME): manual check at the checkpoint. T-150 review nits (non-blocking): mapping rows `box-jump` (squat), `wall-sit`/`squat-hold` (squat), `handstand-hold` (vertical_push), `dead-hang` (vertical_pull) are debatable; the "picker open survives recreation" has no Robolectric test (manual check at the checkpoint). **T-153 review nits (non-blocking):** `applyRoutineNotes` does a second non-atomic `routineRepository.save` after `update_routine_from_session` and matches by `exerciseId` (repeated exercise gets the note on each occurrence); `LiveSet.previous` and the `showPrevious`/`onUsePrevious` path of `SetRow` are dead after dropping the Anterior column (remove; check the previous-values preference/help); `FormSetRow` KDoc still says "delete button"; SQL comment on empty-string notes becoming `other`. **Carry-overs:** chosen sound **"campana suave"** (T-148 wires it); T-148 no longer scans notes (ADR-81); (the padding screenshot was deleted at approval). T-152 review nits (non-blocking): KDoc of `DiscomfortSheet` should mention the required level picker; the migration-mapping pgTAP asserts use a scratch table that copies the CASE; narrow the `@Suppress("MagicNumber")` in `DiscomfortLevel`. T-147 carry-overs: `ActivityFlowTest.kt:86` uses `CoroutineScope(SupervisorJob())` like the flaky `HealthSyncRestorationTest` fixed in #342; no retry of unread discomforts on resume. T-146 carry-overs (non-blocking, for the checkpoint or T-148): rest ending while the user is on another tab posts no notification and no in-app alert (pre-existing); permission dialog interrupted by Activity recreation can show the explanation without a real denial; `FLAG_NO_CREATE` on cancel; own notification icon.

---

## 👤 Checkpoints

E10 (agreed with the user, 2026-10-28): one **Checkpoint E10** after T-136 (whole epic, also the final regression; its approval releases T-137). Details under the E10 section.

E11 (agreed with the user, 2026-10-28): a 👤 spike report after T-139 (device result, not a functionality checkpoint; gates T-141) and **one Checkpoint E11** after T-142 (watch data + daily automatic sync, also the final regression; its approval releases T-143). Details under the E11 section.

E12 (user decision, 2026-10-28: two checkpoints): **Checkpoint E12-A** after T-147 (rest alert with the screen off, choosing the sound, discomfort log; 7 items), **Checkpoint E12-B** after T-150 (chosen sound, notes, Mi entrenamiento, movement pattern, final regression; 7 items; its approval releases T-151). Details under the E12 section.

## Notes for later epics

- **E11:** the `READ_HEALTH_DATA_HISTORY` pre-check is replaced by the spike T-139 (user decision, 2026-10-28; see the E11 section).
- **E12:** consumed by the E12 breakdown: Android 17 verification → T-146 (plan step and Checkpoint E12-A item 1); sound pick → Checkpoint E12-A item 5, wired in T-148.
- **E13:** before the breakdown verify the sources marked *verify* in `planning/07-coach-design.md` §10, the Anthropic API data policy and current prices; 👤 Anthropic account, monthly spending limit and `ANTHROPIC_API_KEY` Supabase secret.

---

# E12 · Training data and preferences → `v1.x` (proposed `v1.3.0`)

Stories: `planning/epics/E12-training-data-and-preferences.md` (US-64, US-65, US-66, US-67 and the movement-pattern data task). Plan: [tasks/plan.md](plan.md). Decisions: ADR-75 (rest alert with the screen off; amends E3 US-13; reopens E9 points 15-16), ADR-76 (discomfort rule, RIR derived: no field; its fixed message was dropped by ADR-81), ADR-78 (preferences, intention, `movement_pattern`), ADR-39 (generated catalogue migration), ADR-29 (cascade), ADR-40 (restoration), ADR-80/R-38 (sheet dismissal). Rules: `04-business-rules.md` §4.4 (rest timer), `05-design-system.md` §8–§10, `07-coach-design.md` §7 (discomfort level and safety).
Carried over from "Notes for later epics": verify the screen-off alert on Android 17 (T-146 plan step, Checkpoint E12-A item 1); 👤 the user picks the new alert sound among the E9 point 16 candidates (Checkpoint E12-A item 5, wired in T-148). Both are inside the epic scope (ADR-75).
**Sound ordering (planner decision, recorded):** the user picks the sound at Checkpoint E12-A from T-146's temporary chooser, which T-147 leaves untouched. The pick is recorded with the checkpoint approval; it is a choice, not a bug, so it needs no fix task before approval. T-148, the first task after the checkpoint and also a live-session task, wires the chosen sound, removes the chooser and the other candidates, and Checkpoint E12-B re-tests it. No extra S task.
Sizing: 6 tasks, one per story. US-64 (T-146, L: platform scheduling, notification, permission, three sound candidates). US-67 (T-147, L) comes before US-66 because it builds the discomfort log (the strong-pain matcher it once reused was dropped by ADR-81). US-66 (T-148, L; backend + core + UI in one PR, the planner may run backend first as a developer slice; it also carries the sound wiring). US-65 (T-149, M). The movement-pattern data task (T-150, M: the reviewed mapping of every catalogue row plus an optional form field) stays alone: merging it with T-149 would exceed ~15 files across two unrelated forms and two migrations. Closure T-151 stays S (it waits for Checkpoint E12-B).
Flow: UI tasks run planner → ux-reviewer (plan) → developers → pr-creator → pr-reviewer ∥ ux-reviewer (diff) (`06` §2.1). UI scenarios are covered by host (Robolectric) tests; the user verifies by hand with `python scripts/deliver-apk.py`.
Questions (`tasks/plan.md`): E12 Q1 resolved by the user, 2026-10-28, then superseded by ADR-81 (no term list, no message; formerly 12 strong-pain terms, plain "dolor"/"molestia" excluded; message "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario."; T-147, T-148). E12 Q2 resolved, 2026-10-28 (session length 15-180 min in 5-min steps, shown with the existing duration formatter; T-149). No task is blocked.
Checkpoints (user decision, 2026-10-28: two): **Checkpoint E12-A** after T-147 and **Checkpoint E12-B** after T-150 (also the final regression; releases T-151). T-146, T-148 and T-149 merge with no checkpoint after them.

---

## T-146: Rest-end alert with the screen off and the sound candidates (US-64) · L

**Story:** US-64 · **Flow:** platform + core + UI · **Size:** L · **Scenarios:** Screen off when the rest ends; App in the background; App in the foreground; Skipping cancels the alert; Finishing the session cancels the alert; Notifications denied

**Description:** (1) **Technique (plan step, carried-over note):** the planner verifies on Android 17 (`targetSdk` 37) and picks how the alert fires on time with the screen off or the process killed: an exact alarm (`setExactAndAllowWhileIdle`/`setAlarmClock` with `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`; the APK is sideloaded) posting the notification from a receiver, a foreground service during the rest (`shortService` is capped at 3 min, so `specialUse` or another type if rests can be longer), or a scheduled notification if its delay under Doze is acceptable (a few seconds at most). The choice, its Android 17 sources and any permission the user must grant go in the PR body and `planning/02-architecture.md`; a permission the user must enable in Ajustes is raised to the PM as a product question before implementation. Timing stays based on `endsAt` (§4.4). (2) **Core:** a `RestAlertScheduler` port (`schedule(endsAt)`, `cancel()`) in `shared/…/domain/port/`; `LiveSessionViewModel` schedules when a rest starts, reschedules on "+30s" and when a new set restarts the rest, and cancels on "Omitir", "Finalizar entreno", discarding the workout and the rest reaching 0 with the app in the foreground (the in-app alert fires, nothing is posted). Foreground = process lifecycle `STARTED` (the same signal as T-135/T-142). Pausing the workout does not stop the rest (§4.10), so the alert stays scheduled. (3) **Platform:** a notification channel for the rest alert (high importance, vibration = `RestAlertPattern`, sound = the current candidate; channel sounds cannot change after creation, so the planner picks how the temporary chooser applies to the notification, e.g. a silent channel plus the app's own playback, or one channel per candidate, and T-147 settles one final channel); the notification "Descanso terminado" (auto-cancel; tapping it opens the live session, assumption A1); `POST_NOTIFICATIONS` requested at the first rest of a session on Android 13+ (assumption A2). (4) **Notifications denied:** nothing is posted; the app explains **once** (persisted flag, e.g. DataStore) that rest alerts need the notification permission; ux-reviewer picks the surface (`AppMessages` message or an `InlineNote` on the live session, R-28/R-37) and the Spanish copy; it never repeats after being shown. (5) **Sound candidates (E9 point 16):** the system notification sound, a soft chime and a double beep, each about 1 s, silent when the phone is muted; synthesised or licence-free assets only (public repo). A **temporary chooser** reachable from Perfil → "Acerca de" (one `AppListRow` "Sonido de fin de descanso" opening an `AppScreen` with one `AppListRow` per candidate: tap plays it and makes it the current one, stored locally), like T-139's diagnostic screen; the in-app alert and the notification use the current candidate. The chooser stays through T-147 (the user picks at Checkpoint E12-A) and is removed by T-148 (sweep item there); its copy is not reviewed beyond R-36/R-37.

**UI templates and patterns:** live session unchanged except the one-time explanation; the chooser is `AppScreen` + `AppListRow`s (temporary). No new designsystem component.

**Acceptance criteria:**
- [ ] Rest of 90 s running, phone locked: at `endsAt` the phone vibrates with the rest pattern, plays the current sound and shows "Descanso terminado" (verified by hand at Checkpoint E12-A; host test: posted at `endsAt` when not in the foreground).
- [ ] App in the background at `endsAt` → the same alert.
- [ ] App in the foreground at `endsAt` → only the in-app alert (vibration + sound); no notification is posted and the scheduled one is cancelled.
- [ ] "Omitir" in the app, "Finalizar entreno" or discarding the workout → nothing fires when the time would have ended; "+30s" moves the alert 30 s later; completing another set reschedules it.
- [ ] Notifications denied → nothing is posted; the explanation appears once and never again (also after reopening the app); the in-app alert still works in the foreground.
- [ ] The temporary chooser lists the three candidates, plays each on tap and the next alert uses the chosen one.
- [x] `planning/02-architecture.md` (technique, permissions) and `04-business-rules.md` §4.4 (alert outside the app, cancel rules) updated; ADR-75 consequences ticked.
- [ ] The live session with a running rest survives background and process death (ADR-40); a rest scheduled before process death still alerts.

**Host tests:** `LiveSessionViewModel` tests with a fake `RestAlertScheduler` (schedule on set completion with `endsAt`, "+30s" reschedules, new set restarts, "Omitir"/finish/discard cancel, foreground end cancels and plays the in-app alert, pause keeps it); notification tests in Robolectric (channel, text "Descanso terminado", posted only when not in the foreground; `ShadowAlarmManager`/`ShadowNotificationManager` or the service equivalent per the chosen technique); permission-explanation-once test (denied → shown once, flag persisted, never again); chooser screen test (rows, tap plays and selects); restoration test with a running rest.

**Verification:**
- [x] lint (incl. `check-design-tokens.sh`, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green.
- [ ] Android 17 behaviour verified in the plan (sources recorded); the on-device check is Checkpoint E12-A items 1-2.

**Agent runs:** planner, ux-reviewer (plan), kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 7. About 17 files, no backend.
**Dependencies:** E11 closed (`v1.2.0`); breakdown agreed by the user
**Files likely touched:** `androidApp/src/main/AndroidManifest.xml`, `androidApp/…/di/AppModule.kt` (+ channel creation in the Application), `shared/…/domain/port/RestAlertScheduler.kt`, `composeApp/…/platform/{AndroidRestAlertScheduler,AndroidAlertSound,RestAlertPattern}.kt` (+ receiver or service), `composeApp/src/commonMain/composeResources/files/` or `androidApp/src/main/res/raw/` (sound candidates), `feature/workout/{LiveSessionViewModel,LiveSessionScreen,LiveSessionUiState}.kt`, new temporary `feature/profile/RestSoundChooserScreen.kt`, the "Acerca de" screen, `app/{AppRoutes,AppNavHost}.kt`, strings, `planning/02-architecture.md`, `planning/04-business-rules.md`, tests
**Estimated scope:** L

---

## T-147: Discomfort log per exercise (US-67; strong-pain message dropped by ADR-81) · L

**Story:** US-67 · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** Logging discomfort; Level and note (reworked by T-152); Resolving; Open discomforts expire

**Description:** (1) **Backend:** one migration: `exercise_discomforts` (`id`, `user_id` default `auth.uid()` cascade, `exercise_id` references `exercises` on delete cascade, `logged_at` timestamptz default now, `note` text nullable 1-200 chars, `severe` boolean not null default false, `resolved_at` timestamptz nullable, check `resolved_at >= logged_at`), index on `(user_id, exercise_id, logged_at desc)`; owner RLS enabled and forced with `(select auth.uid())`, select/insert/update grants to `authenticated` (no delete: history for E13), extend `delete_account_cascade.test.sql`; pgTAP. (2) **Domain:** `StrongPainTerms` (the fixed, versioned term list in `shared/…/domain/rules/`; matching case- and accent-insensitive on normalised text, whole words or phrases; no model interprets the text, `07` §7). **Term list (E12 Q1, resolved by the user 2026-10-28):** "dolor fuerte", "dolor agudo", "dolor intenso", "pinchazo", "chasquido", "hormigueo", "entumecimiento", "hinchazón", "lesión", "me he lesionado", "desgarro", "no puedo mover". Plain "dolor" and "molestia" are deliberately **not** terms. **Fixed message:** "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario.", `DiscomfortStatus` (open while not resolved and `now < logged_at + 7 days`; boundary tested), `LogDiscomfort` (sets `severe` from the term match; severe is not chosen by hand, assumption A3) and `ResolveDiscomfort` (sets `resolved_at` = now); `DiscomfortRepository` port + supabase-kt implementation with DTO mapping; a query of the open discomforts for the exercises of the live session. (3) **UI — templates and patterns:** in the live session, "Registrar molestia" is an action of the exercise's ⋮ `AppActionSheet` (R-02; ux-reviewer confirms the placement) opening an Edit sheet (`AppEditSheet`: optional multi-line `AppTextField` "Nota", "Máximo 200 caracteres", pinned "Guardar", R-09/R-10; closes only with X/back/Cancelar, R-38); after saving, the exercise shows "Molestia registrada" (e.g. an `InlineNote` under the exercise header; ux-reviewer picks) with the action "Ya no me molesta" (resolves, Confirm haptic, the label leaves); a severe log shows the fixed message (exact text above; `InlineNote` or `AppInfoSheet`, ux-reviewer picks) and no other suggestion; the open discomfort shows in later sessions until it expires or is resolved; offline: "Guardar" and "Ya no me molesta" disabled with the standard hint (R-30). Where else "Molestia registrada" appears (exercise detail) is not required (assumption A4). Spanish copy beyond the Gherkin literals by ux-reviewer per §10 (glossary entry "Molestia"). T-146's temporary sound chooser and its candidates stay untouched: the user picks the sound from them at Checkpoint E12-A, and T-148 wires the choice.

**Acceptance criteria:**
- [x] "Registrar molestia" on "Press banca" + "Guardar" → stored for "Press banca" with today's date; "Press banca" shows "Molestia registrada" in this and later sessions.
- [x] Note "dolor fuerte" (or "Dolor FUERTE", "HINCHAZON", "lesion" variants per the normalisation) → "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario." is shown and the row is stored with `severe = true`; a note with only "dolor" or "molestia" (or no listed term) is not severe and shows no message.
- [x] "Ya no me molesta" → `resolved_at` = now and "Molestia registrada" disappears.
- [x] A discomfort logged 8 days ago (unresolved) no longer shows "Molestia registrada"; one logged 6 days ago does.
- [x] pgTAP: RLS enabled and forced, user A cannot read or write user B's discomforts, note length check, `resolved_at` check, cascade on account deletion.
- [x] `planning/03-data-model.md` (table) and `04-business-rules.md` (new §4.12 discomfort: open window, severe rule, term list location) updated.
- [x] The discomfort sheet draft and the live session survive background and process death (ADR-40).

**Host tests:** `StrongPainTermsTest` (every one of the 12 terms, case, accents, phrase vs substring, plain "dolor"/"molestia" do not match, empty/null note); `DiscomfortStatusTest` (6/7/8 days, resolved, boundary); repository mapping test; live-session ViewModel tests (log, severe message, resolve, open discomfort loaded for a new session, offline); Robolectric tests of the action, the Edit sheet (length validation, Guardar), "Molestia registrada", "Ya no me molesta" and the fixed message; restoration test of the sheet draft; pgTAP `exercise_discomforts`.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before Checkpoint E12-A is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 16 files.
**Dependencies:** T-146 merged (no checkpoint in between); E12 Q1 resolved (2026-10-28, terms and message above)
**Files likely touched:** `supabase/migrations/<ts>_exercise_discomforts.sql`, `supabase/tests/exercise_discomforts.test.sql`, `supabase/tests/delete_account_cascade.test.sql`, `shared/…/domain/model/Discomfort.kt`, `shared/…/domain/rules/StrongPainTerms.kt`, `shared/…/domain/usecase/{DiscomfortStatus,LogDiscomfort,ResolveDiscomfort}.kt`, `shared/…/domain/repository/DiscomfortRepository.kt`, `shared/…/data/{remote/DiscomfortDto,repository/SupabaseDiscomfortRepository}.kt`, `shared/…/di/`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen,LiveSessionUiState}.kt` (+ a `DiscomfortSheet.kt`), strings, `planning/03-data-model.md`, `planning/04-business-rules.md`, `planning/05-design-system.md` §10, tests
**Estimated scope:** L

---

### Checkpoint E12-A — Rest alert with the screen off, its sound, and the discomfort log 👤 (7 items + leave and come back) *(agreed with the user, 2026-10-28: two checkpoints)*
Journey: during a workout, lock the phone or switch apps while resting and still be told when the rest ends; choose the sound you want; flag an exercise that bothers you and clear it when it no longer does. Why here: right after T-147, before T-148 builds on the live session and wires the chosen sound.
- [x] T-146 and T-147 merged, CI green, `deploy-supabase` applied T-147's migration (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-29; sound chosen: "campana suave"; visual feedback → T-148 item 5)*:
  1. Screen off and background (US-64): start a workout, complete a set with a 90 s rest (or shorter) and lock the phone: when the rest ends the phone vibrates, plays the sound and shows "Descanso terminado" on the lock screen; repeat switching to another app: the same alert arrives on time; tap it: the live session opens.
  2. Foreground: stay in the app until the rest ends: only the in-app alert (vibration and sound), no notification in the shade.
  3. Cancel and move: during a rest in the background come back, tap "Omitir" and leave again: no alert; start another rest, tap "+30s" and lock: the alert comes 30 s later; with a rest running, finish the workout (or discard it): no alert arrives afterwards.
  4. Notifications denied: in Ajustes deny AppFitness notifications, start a rest: the app explains once that alerts need the permission; no notification with the screen off; the explanation does not come back on the next rest or after reopening the app; allow them again.
  5. Choose the sound (E9 point 16): Perfil → "Acerca de" → "Sonido de fin de descanso": listen to the three candidates (system notification sound, soft chime, double beep), pick one and tell the PM which (recorded with the approval; T-148 wires it); check it is silent with the phone muted.
  6. Log discomfort (US-67): in the live session tap "Registrar molestia" on "Press banca" and save: "Press banca" shows "Molestia registrada"; start another workout with it: it still shows.
  7. Level and resolve (US-67, ADR-81): log a discomfort picking a level (no message appears, even with the note "dolor fuerte"); then tap "Ya no me molesta": "Molestia registrada" disappears.
  - Leave the app and come back during a rest in the live session, with the discomfort sheet half typed: the same tab, screen, countdown and draft are restored.
  - Out of scope: the wiring of the chosen sound (T-148, re-tested at Checkpoint E12-B); the 7-day expiry over real days (host tests); the coach blocking load increases (E13, US-70); reminders as system notifications (never, ADR-73).

---

## T-152: Discomfort level scale and help on every rating scale (ADR-81 amends US-67; R-45) · L

**Story:** US-67 (amended by ADR-81, Checkpoint E12-A feedback 2026-10-28) · **Flow:** backend + core + UI · **Size:** L · **Scenarios:** to be rewritten in the epic file by the planner: Logging discomfort with a level; Levels are ordered and colour-coded; Resolving; Open discomforts expire.

**Description:** Replace T-147's free-text term matching with a level picker. (1) **Backend:** a migration adds `level` (smallint, check by the number of levels; existing rows mapped: `severe` true → highest, false → mild) and drops or derives `severe`; pgTAP updated. (2) **Core:** remove `StrongPainTerms`, its message and tests; `LogDiscomfort` takes a level; `severe` is derived from the level; the term-based wording leaves `04` §4.12, `07` §7, and the T-148 description (notes are not scanned). (3) **UI:** the discomfort sheet gets a required ordered level picker (least to most severe, colour chips from greenish yellow to red with a "Leve"–"Fuerte" guide) above the optional note; the fixed message and its `InlineNote` are removed; the open-discomfort row shows the level. ux-reviewer defines the levels, labels, colour tokens (distinct from the lime of "Guardar"), accessibility and copy; glossary and §8 updated. **Bug fixed here (reported at E12-A):** the note field has no top padding in the sheet (`design/reported/E12-A-discomfort-note-top-padding.png`) and the same defect is in the "Check-in semanal" UI (user, 2026-10-29): fix it in the shared `AppTextField`/multi-line field if that is the cause, and sweep every multi-line field on every screen and sheet. (4) **Rating-scale help (R-45, user feedback 2026-10-29):** the weekly check-in "Energía" asks for 1-5 with no explanation of what each value means. Per R-45 (new, `05` §8) every numeric or ordinal scale shows a `HelpButton` (R-32) opening an Info sheet that lists what each value means; sweep the check-in Energía and every other scale in the app (list them in the plan; RPE already has its help); ux-reviewer writes the Spanish copy per value and, if a scale repeats this finding, proposes a `check-ux-conventions.sh` guard.

**Acceptance criteria:** (the planner writes the final Gherkin)
- [x] Logging requires choosing a level; the note stays optional; the stored row keeps the level.
- [x] The list runs from least to most severe with colours from greenish yellow to red and a text label per level.
- [x] No strong-pain message appears for any note or level; no term matching remains in the code.
- [x] The note field shows its text fully (top padding), in the Molestia sheet, "Check-in semanal" and every other multi-line field.
- [x] Every rating scale (check-in "Energía" and the rest found in the sweep) has a help button whose sheet lists what each value means (R-45); host tests per scale.
- [x] pgTAP, host tests, lint green; `deploy-supabase` green after the merge; docs updated (`03`, `04` §4.12, `05`, `07` §7, ADR-81 ticked).

**Dependencies:** T-147 merged; ADR-81 agreed with the user (2026-10-28). Checkpoint E12-A is re-presented after this task (re-test items 6-7 and the sheet padding); T-148 follows its approval.
**Estimated scope:** L

---

## T-148: Notes per set and per routine exercise, and the chosen rest sound (US-66) · L

**Story:** US-66 (+ US-67 technical note: the term list applies to notes; + the sound choice of Checkpoint E12-A, US-64) · **Flow:** backend + core + UI · **Size:** L (the planner may run backend + core and UI as two developer slices of the same PR) · **Scenarios:** Note on a set; Note on an exercise in the routine

**Description:** (1) **Backend:** one migration: `workout_sets.notes` and `routine_exercises.notes` (text nullable, check 1-200 chars; blank stored as null); `save_routine` accepts and returns the per-exercise note; the paths that write sets (live set save, `save_completed_workout`, the "Editar entreno" save) carry the set note; `save_workout_as_routine` and `update_routine_from_session` leave routine notes untouched unless the planner finds a copy path needed; the reads used by the workout detail and the live session return the notes; pgTAP (length checks, RPC round trips, RLS unchanged). (2) **Domain:** `WorkoutSet.notes`, `RoutineExercise.notes`, DTO mapping; a note matching `StrongPainTerms` (T-147; the 12 terms of E12 Q1, plain "dolor"/"molestia" excluded) shows the same fixed message "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario." when saved (US-67 note); it does not create a discomfort (assumption A5). (3) **UI — templates and patterns:** **set note** in the live session: an action of the set (where the set's existing actions live, e.g. its set-type/RPE sheet or a row action; ux-reviewer picks per R-19/R-02) opens an Edit sheet (`AppEditSheet`, one multi-line `AppTextField` "Nota", "Máximo 200 caracteres", pinned "Guardar", R-38 editable-sheet dismissal), shown under the set as an `InlineNote`; the same note is editable in "Editar entreno" (assumption A6) and shown read-only in the workout detail (`SessionDetailScreen`/`PastWorkoutScreen`) under its set. **Routine exercise note** in the routine builder: an action of the exercise (its ⋮ or row action, ux-reviewer picks) opening the same Edit sheet, shown as an `InlineNote` under the exercise, saved with the routine's "Guardar" (R-11; "Listo" in the sheet if it applies to the builder draft, R-12); shown read-only in the live session under that exercise's header. Removing a note = clearing the field and saving. Spanish copy by ux-reviewer per §10. **Sweep:** the Edit sheet for a note is one shared composable used by sets, routine exercises and T-147's discomfort note if they converge (ux-reviewer decides; no ad-hoc copies, R-39). (5) **Checkpoint E12-A visual feedback (user, 2026-10-29; approved, no separate task):** in the discomfort sheet add clear vertical space between the last level ("Dolor agudo o lesión") and "Nota (opcional)" (today too tight); **remove the "Molestia registrada" label** (the level now says it all) and rework how the open discomfort shows in the live session: the level chip/dot (e.g. the orange dot with "Pinchazo o chasquido"), the level text and the "Ya no me molesta" action must read as one coherent block (ux-reviewer defines it; update the Gherkin literal "Molestia registrada" in the E12 epic file, `04` §4.12 and the glossary §10; tests that assert the label change accordingly). (4) **Sweep from Checkpoint E12-A (sound choice):** the sound the user chose becomes the only rest-alert sound (in-app and the final notification channel, with a new channel ID if T-146's channel had another sound); delete the other candidates, the temporary chooser screen and its "Acerca de" row; `04` §4.4 names the sound; the resolution of E9 point 16 is recorded there and in the PR body (the frozen `planning/phase1/` audit is not edited).

**Acceptance criteria:**
- [x] Writing "pausa larga" on a set and finishing the workout → the note is stored with the set and shown under that set in the workout detail; editable in "Editar entreno".
- [x] Routine with "bajar el peso si duele" on "Press banca" → starting the routine shows the note under "Press banca" in the live session.
- [x] A note longer than 200 characters shows "Máximo 200 caracteres" and "Guardar" stays disabled; a cleared note is removed.
- [x] Discomfort sheet: visible space between the last level and "Nota (opcional)"; "Molestia registrada" no longer appears anywhere; level, dot and "Ya no me molesta" are one coherent block.
- [x] A note shows no health message and is never scanned for terms (ADR-81); it uses the fixed `AppTextField` (top padding, T-152).
- [x] The sound chosen at Checkpoint E12-A plays in the foreground and with the screen off; the chooser, its "Acerca de" row and the other candidates are gone.
- [x] pgTAP: note length checks, `save_routine` round trip of notes, set-note write paths, RLS unchanged.
- [x] `planning/03-data-model.md` (columns, RPC contracts) updated.
- [x] The note sheet draft, the routine builder draft and the live session survive background and process death (ADR-40).

**Host tests:** repository/DTO mapping tests for both notes; `save_routine` payload test; live-session ViewModel tests (add, edit, clear a set note; routine note loaded; no message for any wording); routine builder ViewModel tests (note in the draft, saved with the routine, discard dialog when dirty); Robolectric tests (set note sheet and `InlineNote`, routine note in the live session, workout detail shows the note, length validation); alert-sound test for the final channel and sound; restoration tests; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-149 starts).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 22 files (if the planner counts more than ~25, backend + core and UI run as developer slices of the same PR).
**Dependencies:** Checkpoint E12-A approved with the sound chosen; T-147's matcher and fixed message (E12 Q1 resolved 2026-10-28)
**Files likely touched:** `supabase/migrations/<ts>_training_notes.sql`, `supabase/tests/training_notes.test.sql`, `shared/…/domain/model/{Workout,Routine}.kt`, `shared/…/data/remote/*Dto.kt`, `shared/…/data/repository/{SupabaseWorkoutRepository,SupabaseRoutineRepository}.kt`, `feature/workout/{LiveSessionViewModel,LiveSessionScreen,EditWorkoutScreen,SessionDetailScreen,PastWorkoutScreen}.kt`, `feature/routines/{RoutineBuilderScreen,RoutineBuilderViewModel}.kt`, a shared note sheet composable, `composeApp/…/platform/AndroidAlertSound.kt` and the notification channel, removal of the temporary chooser and its "Acerca de" row, strings, `planning/03-data-model.md`, `planning/04-business-rules.md` §4.4, tests
**Estimated scope:** L

---

## T-149: "Mi entrenamiento": training intention and static preferences (US-65) · M

**Story:** US-65 · **Flow:** backend + core + UI · **Size:** M · **Scenarios:** Intentions are a fixed list; Choosing the intention; Place defines the default equipment; Days and session length are optional

**Description:** (1) **Backend:** one migration: enums `training_intention` (`strength`, `muscle`, `fat_loss`, `recomposition`, `maintenance`) and `training_place` (`home`, `gym`, `other`); `training_preferences` gains `intention` (nullable until chosen), `place` (nullable until chosen), `equipment equipment[]` (not null default `'{}'`), `days_per_week` smallint nullable check 1-7, `session_minutes` smallint nullable, check 15-180 and a multiple of 5 (E12 Q2, resolved by the user 2026-10-28); the save path the Preferencias sections already use for `training_preferences` carries the new fields, and a section saves only its own fields (as the hub's other sections); pgTAP (enum values, checks, RLS unchanged, saving this section leaves the other `training_preferences` fields intact). (2) **Domain:** `TrainingIntention`, `TrainingPlace`, `TrainingPreferences` gains the five fields; a pure `DefaultEquipment.forPlace` (home = dumbbell, band, bodyweight; gym = every `equipment` value; other = bodyweight; epic design decision); DTO mapping. The intention does not touch the nutrition goal (epic note; E13). (3) **UI — templates and patterns:** a new Preferencias hub `NavigationRow` "Mi entrenamiento" (Gherkin literal; value = the chosen intention label, or none) next to the existing "Entrenamiento" section, which keeps the app settings (warm-up, calculators; assumption A7); it opens an `AppEditorScreen` "Mi entrenamiento" (R-01/R-04): intention among "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición", "Mantenimiento" (the labels are long, so not a `SegmentedSelector` per R-33: a `NavigationRow`-style field opening an `AppPickerSheet` that applies on tap, or `AppToggleChip`s; ux-reviewer picks); place "Casa" / "Gimnasio" / "Otro" as a `SegmentedSelector` (3 short options; labels beyond "Casa" by ux-reviewer); equipment as multi-select `AppToggleChip`s with the catalogue's existing Spanish equipment labels ("Mancuernas", "Banda", "Peso corporal", …); choosing a place replaces the draft's equipment with that place's defaults, which the user can then change (assumption A8); days per week (optional, 1-7) and session length (optional, 15-180 min in 5-min steps, e.g. an `AppPickerSheet` or stepper, ux-reviewer picks) with a way to leave them empty. **Session-length text:** the picker options and the shown value reuse the app's existing duration formatter `durationText(seconds: Long)` in `feature/analytics/stats/TrainingStatsFormat.kt` (strings `stats_duration_m` "%1$d min", `stats_duration_hm` "%1$d h %2$d min"), which yields "15 min", "45 min", "1 h 0 min", "1 h 5 min", "1 h 30 min", "3 h 0 min"; no ad-hoc copy (R-39): since a third feature now uses it (statistics, Actividad, Preferencias), move it into `designsystem/` (next to `AppNumberFormat`/`AppDateFormat`, R-44) and repoint the existing callers, with no change to its output; pinned "Guardar" (R-09/R-10), Confirm haptic and close (R-13), "¿Descartar los cambios?" when dirty (R-14), offline hint (R-30); loading skeleton and `ErrorState` "Reintentar". Glossary (§10) gains the intention and place labels. **Sweep:** if the hub or the "Entrenamiento" section renders equipment labels ad hoc, reuse one equipment-label source.

**Acceptance criteria:**
- [x] "Mi entrenamiento" offers exactly "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición" and "Mantenimiento".
- [x] Choosing "Ganar fuerza" + "Guardar" → stored as `strength`; the hub row and the screen show "Ganar fuerza" after reopening the app.
- [x] Choosing "Casa" → equipment "Mancuernas", "Banda", "Peso corporal" selected; the user can add or remove equipment before saving; "Gimnasio" selects every value; "Otro" selects "Peso corporal".
- [x] Saving without days or session length stores them as null; the session length offers only 15-180 min in 5-min steps, each shown by the shared duration formatter ("45 min", "1 h 0 min", "1 h 30 min"), and the saved value is shown the same way; the database rejects other values (check).
- [x] Saving "Mi entrenamiento" does not change warm-up, calculator or previous-values settings, nor the nutrition goal.
- [x] pgTAP: enums, `days_per_week` and `session_minutes` checks, owner RLS, other fields untouched.
- [x] `planning/03-data-model.md` and `04-business-rules.md` (default equipment per place; intention is static) updated.
- [x] The editor draft and scroll survive background and process death (ADR-40).

**Host tests:** `DefaultEquipmentTest` (three places); duration-formatter test after the move (15, 45, 60, 65, 90, 180 min, and the existing statistics/Actividad cases unchanged); repository/DTO mapping tests (nulls, enum mapping, equipment array); Preferencias ViewModel tests for the section (load, choose intention, place resets equipment, optional fields, validation, save only own fields, offline, error); Robolectric tests (hub row value, editor controls, picker applies on tap, discard dialog); restoration test; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before T-150 starts).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 14 files.
**Dependencies:** T-148 merged and `deploy-supabase` green (no checkpoint in between); E12 Q2 resolved (2026-10-28: 15-180 min, 5-min steps)
**Files likely touched:** `supabase/migrations/<ts>_training_intention_preferences.sql`, `supabase/tests/training_preferences.test.sql`, `shared/…/domain/model/TrainingPreferences.kt`, `shared/…/domain/usecase/DefaultEquipment.kt`, `shared/…/data/remote/TrainingPreferencesDto.kt`, `shared/…/data/repository/SupabaseTrainingPreferencesRepository.kt`, `feature/profile/{PreferencesHubScreen,PreferencesSection,PreferencesSectionScreen,PreferencesViewModel,PreferencesDraft,PreferencesUiState}.kt` (+ a `MyTrainingSection.kt`), `feature/analytics/stats/TrainingStatsFormat.kt` → `designsystem/` (duration formatter move) and its callers (`TrainingStatsSections.kt`, `feature/activity/ActivityMetricUi.kt`), strings, `planning/03-data-model.md`, `planning/04-business-rules.md`, `planning/05-design-system.md` §6.2/§10, tests
**Estimated scope:** M

---

## T-150: Movement pattern in the exercise catalogue and the custom exercise form (data task) · M

**Story:** E12 data task "Movement pattern in the catalogue" (ADR-78, ADR-39) · **Flow:** backend + data + small UI · **Size:** M (stays alone: the reviewed mapping covers every catalogue row; merging with T-149 would put two unrelated forms and two migrations in one PR)

**Description:** (1) **Mapping:** the planner checks whether the RepDB bundle (`library.json`, generated by `scripts/import-repdb.py`) has a field equivalent to the movement pattern; where it does, `scripts/generate-exercise-catalog.py` derives it; otherwise a reviewed mapping keyed by catalogue `key` (e.g. `scripts/exercise-movement-patterns.csv`, versioned, no third-party text) gives each catalogue row one of `squat`, `hinge`, `lunge`, `horizontal_push`, `vertical_push`, `horizontal_pull`, `vertical_pull`, `carry`, `core`, `isolation`, `cardio`, `other`; the generator emits the column for future regenerations. 👤 Prerequisite only if the generator must be re-run against the bundle: the user's local RepDB bundle (as in E4). (2) **Backend:** one migration: enum `movement_pattern`, `exercises.movement_pattern` nullable, an update that fills every catalogue row (`is_custom = false`) from the mapping; the custom exercise create/edit path accepts it (optional); pgTAP: every catalogue row has a pattern, custom rows may be null, owner rules on custom rows unchanged. (3) **Domain/data:** `MovementPattern` enum and `Exercise.movementPattern` with DTO mapping. (4) **UI — templates and patterns:** the custom exercise form ("Nuevo ejercicio" sheet and the custom exercise edit) gains an optional "Patrón de movimiento" field opening an `AppPickerSheet` (single choice applied on tap, R-12; an option to leave it empty; closes on an outside tap, R-38) with Spanish labels by ux-reviewer (§10 glossary entries); not shown for catalogue exercises (assumption A9). Used by the coach in E13; no other screen shows it.

**Acceptance criteria:**
- [x] After the migration every catalogue exercise has a non-null `movement_pattern`; spot checks: a back squat is `squat`, a bench press `horizontal_push`, a pull-up `vertical_pull`, a biceps curl `isolation` (pgTAP by key).
- [x] Creating a custom exercise without a pattern saves it with none; with "Patrón de movimiento" chosen, it is stored and shown again when editing.
- [x] The mapping covers every catalogue key (a test or script check fails on a missing key).
- [x] `planning/03-data-model.md` (enum, column) updated.
- [x] The custom exercise form draft survives background and process death (ADR-40).

**Host tests:** DTO mapping test (pattern present/null); custom-exercise ViewModel tests (optional pattern, save, edit); Robolectric tests of the field and the picker sheet; a mapping-coverage check (script or test); pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green on `main`, data intact (before Checkpoint E12-B is presented).

**Agent runs:** planner, ux-reviewer (plan), backend-developer, kotlin-core-developer, compose-ui-developer, pr-creator, pr-reviewer ∥ ux-reviewer (diff) = 8. About 11 files (the mapping file is long but mechanical).
**Dependencies:** T-149 merged and `deploy-supabase` green
**Files likely touched:** `scripts/generate-exercise-catalog.py`, `scripts/exercise-movement-patterns.csv` (or derived from the bundle), `supabase/migrations/<ts>_movement_pattern.sql`, `supabase/tests/exercises_movement_pattern.test.sql`, `shared/…/domain/model/Exercise.kt`, `shared/…/data/remote/ExerciseDto.kt`, `shared/…/data/repository/SupabaseExerciseRepository.kt`, `feature/exercises/` custom exercise form + ViewModel, strings, `planning/03-data-model.md`, `planning/05-design-system.md` §10, tests
**Estimated scope:** M

---

## T-153: Checkpoint E12-B feedback fixes (notes, "Mi entrenamiento", pickers, check-in names) · L

**Source:** user feedback at Checkpoint E12-B, 2026-10-29 (screenshots: `design/reported/E12-B-live-set-note-button.png`, `design/reported/E12-B-checkin-records-ejercicio-name.png`; delete them once the user confirms the fix). New rules R-46…R-49 and the Perfil decision are already in `05` §8 (same docs PR); the task sweeps every screen with those patterns (R-39). **Flow:** UI + small core; planner may split into two PRs if it exceeds ~15 files (then T-154).

1. **Set-note button (R-49) — the live session layout is broken since T-148 added it** (`design/reported/E12-B-live-set-note-button.png`): the note icon sits alone on a line under each set row;  integrate it into the set row (trailing slot or inside the row layout) in the live session and every other place that shows set notes.
2. **Notes redesigned (R-46, ADR-82, written in the feedback PR, user decisions 2026-10-29):** a note is a chosen option plus an optional free text ("Otro", max 200 chars), like the discomfort levels. Set options (only what kg, reps, RPE and rest do not say): "Técnica mejorable", "Rango incompleto", "Agarre flojo", "Con ayuda", "Otro". Routine-exercise options: "Subir peso", "Bajar peso", "Cuidar la técnica", "Ajustar máquina o asiento", "Otro". No pain option (that is "Registrar molestia"). Each list has a `?` Info sheet saying what each option means and which note is for the workout and which for the routine. Backend: `note_kind` enum column on `workout_sets` and `routine_exercises` (migration + pgTAP; existing free-text notes become `other`); the same decision is in `03`, `04`, `05` §10 and US-66 (amended in this PR). A routine-exercise note edited in the live session counts as a routine change and triggers the existing "update the routine?" prompt; declined, it stays only in that workout. Discomforts never count as a routine change.
3. **"Mi entrenamiento" in the Perfil root (decided):** its own row in the Perfil root (not inside Preferencias); update `05` §6.2, navigation, tests and the Gherkin/UI literals that say "Preferencias → Mi entrenamiento".
4. **Non-obvious options (R-46):** `HelpButton` with an Info sheet on the training intention (what "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición", "Mantenimiento" mean and how they differ), and a sweep of every other option whose meaning is not obvious (list them in the plan). Consider a `check-ux-conventions.sh` guard if feasible.
5. **No dropdowns (R-47):** "Días por semana" and "Duración del entreno" use the "Descanso" / "Objetivo RPE" control of the routine editor; the shared duration formatter drops zero parts ("1 h", not "1 h 0 min"); sweep statistics and every caller.
6. **"Añadir ejercicio propio" pinned (R-48):** fixed at the bottom of the exercise selection; the list scrolls above it.
7. **Bug: Check-in semanal "Récords de la semana" shows "Ejercicio" instead of the exercise name** for catalogue exercises (only the custom "Hand grip" shows its name; the name lookup apparently covers custom exercises only). Show the real name for every exercise; add a regression test; sweep other places that resolve exercise names (Diario, statistics, coach notes).

**Acceptance criteria:** the planner derives the Gherkin from items 1-7; host tests per item; the bug has a regression test; lint + `testLocalDebugUnitTest` green.
**Dependencies:** none · **Estimated scope:** L

---

## T-154: Checkpoint E12-B feedback fixes, part 2 ("Mi entrenamiento" row, help, steppers, pinned create, check-in names) · L

**Source:** split of T-153 by the planner (user feedback at Checkpoint E12-B, 2026-10-29); plan in `.workflow/current_task.md`. Covers T-153 items 3-7: "Mi entrenamiento" row in the Perfil root (R-46 help on the training intention); `NumberStepper` for "Días por semana" and "Duración del entreno" (R-47; "Sin definir" is the lowest step, session step 5 min; `durationText` drops zero parts); "Añadir ejercicio propio" pinned (R-48); check-in records show real exercise names (inject `ExerciseLibrary` into `CheckInViewModel`, regression test; sweep other name lookups).
**Dependencies:** T-153 · **Estimated scope:** L

---

## T-155: Checkpoint E12-B feedback fixes, round 3 (live set table alignment, check-in "Ejercicio") · M

**Source:** user feedback at Checkpoint E12-B, 2026-10-29 (screenshots: `design/reported/E12-B-live-set-table-alignment.png`, `design/reported/E12-B-checkin-records-ejercicio-name-2.png`; delete them once the user confirms the fix). New rule R-50 in `05` §8 (same docs PR); the task sweeps every table-like screen (R-39).
1. **Live set table (R-50):** remove the "Nº" column and the per-set numbers; keep "Tipo" (set type badge). Header labels and every row share the same column widths; "KG" (and REPS, RPE, LISTO) centred over their fields. Sweep the workout editor and any other set table.
2. **Dropped (user, 2026-10-29):** the "Ejercicio" name in the check-in records came from a manually created exercise in the user's test account and is already fixed; new accounts are not affected. No code change.
**Dependencies:** T-154 · **Estimated scope:** M

---

### Checkpoint E12-B — Notes, your training situation, and the final E12 regression 👤 (7 items + leave and come back) *(agreed with the user, 2026-10-28: two checkpoints)*
Journey: the rest alert now plays the sound you chose; write what happened on a set and read the note you left on a routine exercise; tell the app your goal, where and with what you train, and how many days and minutes you have; give a movement pattern to your own exercises; then one pass over the whole epic. Why here: right after T-150, the last implementation task, so it is also the final regression of E12; its approval releases T-151.
- [x] T-148, T-149 and T-150 merged, CI green, `deploy-supabase` applied their three migrations (data intact), every reported bug fixed.
- [x] T-153 and T-154 (checkpoint feedback fixes) merged, CI green.
- [x] T-155 (feedback round 3: live set table; check-in names item dropped) merged, CI green.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-29; feedback → T-153; re-test every item, plus: the note button inside the set row, the `?` help on notes and training intention, "Mi entrenamiento" row in Perfil, durations without dropdowns, "Añadir ejercicio propio" pinned, real exercise names in the check-in records)*:
  1. Chosen sound (T-148, from Checkpoint E12-A): a rest ending in the foreground and one with the screen off both play the sound you chose; "Sonido de fin de descanso" is gone from "Acerca de".
  2. Notes (US-66): write "pausa larga" on a set, finish the workout: the note shows under that set in the workout detail; edit it in "Editar entreno"; in a routine add "bajar el peso si duele" to "Press banca" and save; start the routine: the note shows under "Press banca".
  3. Note limits: a note over 200 characters is refused with "Máximo 200 caracteres"; a note with "dolor fuerte" shows no message (ADR-81); airplane mode disables "Guardar" with the standard hint.
  4. Intention (US-65): Preferencias → "Mi entrenamiento" offers "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición" and "Mantenimiento"; choose "Ganar fuerza", "Guardar": the hub row shows it, also after reopening the app; "Entrenamiento" (warm-up, calculators) is unchanged.
  5. Place, equipment, days and length (US-65): choose "Casa": "Mancuernas", "Banda" and "Peso corporal" get selected; add or remove one; try "Gimnasio" (everything) and "Otro" (peso corporal); save with no days and no session length: it saves; set 4 days and a session length: the options run from "15 min" to "3 h 0 min" in 5-min steps, shown like the statistics durations ("45 min", "1 h 30 min"), and the saved value reads the same way.
  6. Movement pattern (data task): create an "ejercicio propio" with "Patrón de movimiento" and one without: both save; editing shows the chosen pattern.
  7. Final regression of E12: a full workout with the rest alert (screen off), a set note and a discomfort ("Molestia registrada", "Ya no me molesta"); Diario, Peso, Actividad and "Centro de avisos" work as before.
  - Leave the app and come back with "Mi entrenamiento" half edited, and once with a set-note sheet half typed: the same tab, screen and draft are restored.
  - Out of scope: the coach using the intention, preferences, discomfort or movement patterns (E13); removing the target weight (E13, ADR-78); an RIR field (never, ADR-76); water and voice (Phase 3).

---

## T-151: E12 closure → `v1.x` · S

**Story:** E12 Definition of Done · **Flow:** PM · **Size:** S (cannot merge: it waits for Checkpoint E12-B's approval)

**Description:** Closes E12 once Checkpoint E12-B is approved: version bump (`versionName`/`versionCode` in `androidApp/build.gradle.kts`, PR) per the versioning rule (`06` §6; minor per epic, proposed `1.3.0`), tag, retrospective, archive of the E12 log lines. No 👤 checkpoint of its own.

**Acceptance criteria:**
- [x] T-146…T-150 (and any fix tasks) merged with green CI; `deploy-supabase` green after each of the four migrations.
- [x] ux-reviewer ran in plan and diff mode on every UI task; `check-ux-conventions.sh` passes.
- [x] `planning/02-architecture.md` (rest-alert technique), `03-data-model.md` and `04-business-rules.md` describe the alert, `exercise_discomforts`, the notes, the new `training_preferences` fields and `movement_pattern`.
- [x] Checkpoints E12-A and E12-B approved; every reported bug fixed.
- [x] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [x] `git tag` lists the E12 tag.

**Dependencies:** T-150 and Checkpoint E12-B approved
**Files likely touched:** `androidApp/build.gradle.kts`, `tasks/todo.md`, `tasks/plan.md`, `planning/09-open-questions.md`, `retrospectives/E12.md`
**Estimated scope:** S

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
- [x] Summary of today with the five metrics and their icons.
- [x] "Sincronizar" on "Actividad" syncs like on Peso and shows the status line.
- [x] Chart defaults to Pasos; the metric and the range (1S, 1M, 3M, 6M, 1A) switch.
- [x] Sleep shows the stage distribution when available, otherwise the duration.
- [x] The calendar picks another day; a day or metric without data says so.
- [x] Tab, metric, range, day and scroll survive leaving the app and process death (ADR-40).

**Host tests:** range and day query tests; ViewModel tests (default metric, switching, calendar, empty states, sync); Robolectric screen and restoration tests.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-142 merged
**Files likely touched:** to be defined by the planner (`feature/activity/…`, shared repository and queries, strings, `05-design-system.md` §8)
**Estimated scope:** L

---

## T-145: Checkpoint E11 fixes: missing watch data, sleep axis format, outside-tap dismissal (fix) · M

**Story:** US-61, US-62, US-64 · **Flow:** investigation + core + UI · **Origin:** user feedback at Checkpoint E11 (2026-10-28); the planner may propose splitting it

**Description:** (1) **Missing watch data (bug, confirmed on the device by the user, 2026-10-28):** Actividad shows only the last week of sleep, active and total calories and resting heart rate, but Health Connect (Zepp) holds them back to: sleep 18 September, active calories 27 September, total calories 20 September, resting heart rate 13 September (Pasos is correct, from about 1 August). The expectation is each type from the oldest date Health Connect holds within the 90-day first window (branch A) up to yesterday, nothing ignored. Working hypothesis (unverified): `SyncHealthData` reads 90 days only while `last_auto_sync_date` is null and 7 days afterwards (`LATER_WINDOW`); the date can be set by an automatic sync that ran before those types were granted, so once granted only 7 days were ever read. The developer confirms or refutes the cause and fixes it so a type granted or read for the first time gets its 90-day read regardless of the global date (for example a per-type "first read done" state, or a first-read check against the stored data), with a regression test; days already missed are backfilled by the next sync, with no manual reset by the user. (2) **Sleep chart axis:** hour labels show one decimal at most (`7.0h`, never `7.0000000001h`); sweep every axis and value label that formats a float (`AppNumberFormat`). (3) **Outside-tap dismissal (ADR-80):** pickers and read-only sheets close on an outside tap; sheets with editable input keep X/back/Cancelar. Update the sheet templates in `designsystem/`, sweep every sheet in the app, update guard G11 and its fixtures, and `05` R-38 is already amended.

**Acceptance criteria:**
- [x] After "Sincronizar", Pasos, sleep, active and total calories and resting heart rate appear for every day Health Connect holds, from the first-sync window up to yesterday.
- [x] A regression test reproduces the cause of the gaps.
- [x] No chart axis or label shows more than one decimal where one is intended (sleep hours `X.Xh`).
- [x] Tapping outside the calendar and the other pickers or read-only sheets closes them; editor sheets do not close on an outside tap.
- [x] `check-ux-conventions.sh` and its fixtures reflect ADR-80.

**Host tests:** sync/data tests for the root cause; number-format tests; Robolectric outside-tap tests per sheet archetype; guard fixtures.

**Verification:**
- [x] lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-144 merged
**Estimated scope:** M

---

### Checkpoint E11 — Your watch data, synced by itself each day 👤 (13 items + leave and come back) *(agreed with the user, 2026-10-28: one checkpoint for the whole epic)*
Journey: grant the new permissions once and see last night's sleep, yesterday's steps, calories and resting heart rate, never duplicated; from then on the app brings them on the first open of each day, and the button still brings a fresh weigh-in. Why here: right after T-142, the last implementation task, so it is also the final regression of E11; its approval releases T-143. The user chose one checkpoint over two (E11-A after T-141 and E11-B after T-142).
- [x] T-140, T-141, T-142, T-144 and T-145 merged, CI green, `deploy-supabase` applied T-140's migration (data intact), every reported bug fixed.
- [x] 👤 Install the APK and try *(approved by the user, 2026-10-28)*:
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
- [x] T-139…T-142 (and any fix tasks) merged with green CI; `deploy-supabase` green after T-140's migration.
- [x] ux-reviewer ran in plan and diff mode on T-141 and T-142; `check-ux-conventions.sh` passes.
- [x] `planning/03-data-model.md` and `04-business-rules.md` describe the three tables, `last_auto_sync_date`, the read types and the daily trigger.
- [x] Checkpoint E11 approved; every reported bug fixed.
- [x] Version bumped; `tasks/todo.md` (next-task pointer) updated through a PR.

**Verification:**
- [x] `git tag` lists the E11 tag.

**Dependencies:** T-145 and Checkpoint E11 approved
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
