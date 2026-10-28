# Implementation Plan: E12 · Training data and preferences → `v1.x` (proposed `v1.3.0`)

> **Status:** broken down on 2026-10-28 by the epic-planner. Restructured the same day by user decision to **two checkpoints** (E12-A after T-147, E12-B after T-150). E12 Q1 and Q2 were resolved by the user on 2026-10-28. 6 tasks (T-146…T-151). No task is blocked.

## Overview
Collect what the coach (E13) cannot derive from the data, and make the rest-end alert work with the screen off. The epic covers:
- the rest alert, the only system notification (ADR-75);
- the discomfort log with the fixed strong-pain message (ADR-76);
- notes per set and per routine exercise;
- the training intention and static preferences ("Mi entrenamiento");
- a movement pattern for every catalogue exercise (ADR-78).

Nothing here plans or adjusts training; E13 consumes it.

Sources: `planning/epics/E12-training-data-and-preferences.md` (US-64…US-67 + data task), `planning/01-scope.md` §4, §6 and §7, ADR-75/76/78, ADR-39 (generated catalogue migration), ADR-29 (cascade), ADR-40 (restoration), ADR-80 (sheet dismissal), `planning/04-business-rules.md` §4.4 and §4.10, `planning/07-coach-design.md` §7, `planning/05-design-system.md` §8–§10. Tasks: `tasks/todo.md` (T-146…T-151).

Carried over from "Notes for later epics" (both in scope, ADR-75):
- verify the screen-off alert on Android 17 → T-146 plan step;
- 👤 the user picks the alert sound among the E9 point 16 candidates → Checkpoint E12-A item 5, wired in T-148.

Out of scope (epic, `01` §5): the coach and the plan storage (E13); an RIR field (ADR-76); removing the target weight (E13, ADR-78); system notifications for reminders (ADR-73); water and voice (Phase 3).

## Sizing
Every task pays 6–8 agent hand-offs, so the plan has one task per story:
- **T-146 (L)** = US-64: scheduling outside the app, the notification and its permission, the in-app/notification split, three sound candidates and a temporary chooser. No backend.
- **T-147 (L)** = US-67. It comes before US-66 because it builds the strong-pain matcher and the fixed message that the notes reuse.
- **T-148 (L)** = US-66: two columns, the routine and set write paths, the live session, the routine builder, "Editar entreno" and the workout detail. It also wires the sound chosen at Checkpoint E12-A, because it is the first task after that checkpoint and it touches the live session too. One PR; the planner may run backend + core and UI as developer slices.
- **T-149 (M)** = US-65: one migration and one new Preferencias section. It also moves the shared duration formatter into `designsystem/`.
- **T-150 (M)** = the movement-pattern data task: a reviewed mapping of every catalogue row plus an optional field in the custom exercise form. It stays alone: merged with T-149 it would exceed ~15 files across two unrelated forms and two migrations.
- **T-151 (S)** = closure. It cannot merge with another task because it waits for Checkpoint E12-B.

## Sound ordering (planner decision)
The user must choose the sound before it is wired, but Checkpoint E12-A comes after T-147. So:
- T-146 ships the three candidates and a temporary chooser (Perfil → "Acerca de" → "Sonido de fin de descanso").
- T-147 leaves the chooser untouched.
- At Checkpoint E12-A the user listens to the candidates and names one. The pick is recorded with the approval. It is a choice, not a bug, so it needs no fix task before approval.
- T-148, the next task, wires the chosen sound into the in-app alert and a final notification channel, then deletes the chooser and the other candidates.
- Checkpoint E12-B re-tests the sound.

No extra S task is needed.

## Checkpoints (user decision, 2026-10-28: two)
Collected test items:
- rest alert: 6 (screen off, background, foreground, skip/+30s, finish/discard, notifications denied);
- sound: choice 1 + wiring re-test 1;
- discomfort: 3 (log, strong pain, resolve; the 7-day expiry is covered by host tests);
- notes: 3 (set note, routine exercise note, limits/term message/offline);
- preferences: 4 (list, save, place/equipment, optional fields);
- movement pattern: 1;
- final regression: 1.

Related scenarios are grouped into two journeys of 7 items each:
- **Checkpoint E12-A — Rest alert with the screen off, its sound, and the discomfort log (7 items)**, after T-147:
  1. screen off and background (tapping the notification opens the session);
  2. foreground: no notification;
  3. "Omitir", "+30s", finish or discard;
  4. notifications denied: explained once;
  5. choose the sound;
  6. "Registrar molestia" → "Molestia registrada" across sessions;
  7. "dolor fuerte" → the fixed message, then "Ya no me molesta".

  Why here: before T-148 wires the sound and builds on the live session.
- **Checkpoint E12-B — Notes, your training situation, and the final E12 regression (7 items)**, after T-150:
  1. the chosen sound plays and the chooser is gone;
  2. set note (workout detail, "Editar entreno") and routine exercise note (live session);
  3. note limits, term message and offline;
  4. the intention list, saving it and seeing it shown;
  5. place → default equipment, and optional days and session length (15 min to 3 h 0 min);
  6. movement pattern on an "ejercicio propio";
  7. final regression of E12.

  Why here: T-150 is the last implementation task, so this is also the final regression. Its approval releases T-151.

Each checkpoint also has "leave the app and come back: the same tab, screen and draft are restored". T-146, T-148 and T-149 merge with no checkpoint after them. Full item lists are in `tasks/todo.md`.

## Architecture Decisions
- **Rest alert technique chosen in T-146's plan after verifying Android 17 (ADR-75).** Candidates:
  - an exact alarm (`setExactAndAllowWhileIdle`/`setAlarmClock`). `SCHEDULE_EXACT_ALARM` is not granted by default on Android 14+; `USE_EXACT_ALARM` is possible because the APK is sideloaded;
  - a foreground service during the rest (`shortService` is capped at 3 min);
  - a scheduled notification, if its Doze delay is a few seconds at most.

  Timing comes from `endsAt` (§4.4). A permission the user must enable in Ajustes is raised as a product question before implementation. The choice is recorded in `02-architecture.md`.
  **Decided (T-146):** an exact alarm (`setExactAndAllowWhileIdle`, `USE_EXACT_ALARM`, no user action). Android 17 blocks background audio even from an exact-alarm receiver (see `02-architecture.md` §10), so the receiver only posts the notification and the notification channel plays the sound and the vibration.
- **One `RestAlertScheduler` port.** The live-session ViewModel schedules the alert, reschedules it ("+30s", new set) and cancels it ("Omitir", finish, discard, rest ending in the foreground). Foreground = a resumed activity (`CurrentActivityHolder`), so a locked screen counts as background. In the foreground only the in-app alert fires.
- **Notification permission.** `POST_NOTIFICATIONS` is requested at the first rest of a session (Android 13+). If denied, nothing is posted and the app explains it once (persisted flag).
- **Sound.** Three candidates and a temporary chooser in T-146, one notification channel per candidate (`rest_alert_system`, `rest_alert_chime`, `rest_alert_double_beep`; a channel sound is frozen after creation), each high importance with the `RestAlertPattern` vibration. The notification goes to the channel of the sound chosen in `RestAlertPreferences`; in the foreground `AndroidAlertSound` plays it. T-148 keeps only the chosen sound in a new channel ID and deletes the other channels, the chooser and the unused assets.
- **Strong-pain matcher in the domain.** A fixed, versioned term list lives in `shared/…/domain/rules/` (E12 Q1). Matching is case- and accent-insensitive on normalised text; no model reads the text (`07` §7). Discomfort notes use it to set `severe`. Set and routine-exercise notes use it only to show the message.
- **Discomfort open window on the client.** A discomfort is open while unresolved and `now < logged_at + 7 days`. The user never deletes stored rows: they are the history E13 US-70 needs.
- **Four migrations, one per story** (`02` §6): `exercise_discomforts` (T-147), the notes columns (T-148), the `training_preferences` fields + enums (T-149; `session_minutes` check 15-180, multiple of 5), and `movement_pattern` (T-150). Each has pgTAP, owner RLS where a table is new, and needs `deploy-supabase` green before the next task or checkpoint.
- **"Mi entrenamiento" is a new Preferencias hub section** (Gherkin literal) beside the existing "Entrenamiento" app-settings section, and it saves only its own fields. Default equipment per place is a pure domain rule.
- **One duration formatter.** The session length is shown with the existing `durationText(seconds: Long)` (`feature/analytics/stats/TrainingStatsFormat.kt`; "45 min", "1 h 0 min", "1 h 5 min", "1 h 30 min"). T-149 moves it into `designsystem/` without changing its output, because a third feature now uses it (R-39, R-44).
- **Movement pattern from the generator.** `scripts/generate-exercise-catalog.py` takes the column from a RepDB field if one is equivalent, otherwise from a reviewed mapping keyed by catalogue `key`. A coverage check fails on a missing key.
- **UI on existing templates.**
  - Notes and discomfort: Edit sheets (`AppEditSheet`); shown notes and "Molestia registrada" as `InlineNote`; exercise actions in `AppActionSheet`.
  - "Mi entrenamiento": `AppEditorScreen` with `SegmentedSelector`, `AppToggleChip` and `AppPickerSheet`.
  - Movement pattern: `AppPickerSheet`.

  No new designsystem component is expected. If the three note uses converge, they share one note sheet (R-39).

## Task List

| Task | Flow | Size | Expected agent runs |
|---|---|---|---|
| T-146: Rest-end alert with the screen off and the sound candidates (US-64) | platform + core + UI | L | 7 |
| T-147: Discomfort log per exercise and the strong-pain message (US-67) | backend + core + UI | L | 8 |
| T-148: Notes per set and per routine exercise, and the chosen rest sound (US-66) | backend + core + UI | L | 8 |
| T-149: "Mi entrenamiento": training intention and static preferences (US-65) | backend + core + UI | M | 8 |
| T-150: Movement pattern in the exercise catalogue and the custom exercise form | backend + data + small UI | M | 8 |
| T-151: E12 closure → `v1.x` | PM | S | PM only |

### Phase 1: Rest alert and discomfort in the live session (US-64, US-67)
- [ ] T-146: Rest-end alert with the screen off and the sound candidates — L (no checkpoint after it)
- [ ] T-147: Discomfort log and the strong-pain message — L
  - **Checkpoint E12-A 👤 (7 items):** screen off and background; foreground; skip, +30s, finish/discard; notifications denied; choose the sound; log discomfort; strong pain and resolve.

### Phase 2: Notes and static inputs for the coach (US-66, US-65, data task)
- [ ] T-148: Notes per set and per routine exercise, and the chosen rest sound — L (no checkpoint after it; `deploy-supabase` green before T-149)
- [ ] T-149: "Mi entrenamiento" — M (no checkpoint after it; `deploy-supabase` green before T-150)
- [ ] T-150: Movement pattern — M
  - **Checkpoint E12-B 👤 (7 items):** chosen sound; notes; note limits; intention; place, equipment, days and length; movement pattern; final regression of E12. Releases T-151.

### Phase 3: Closure
- [ ] T-151: E12 closure → `v1.x` — PM — S (no checkpoint of its own; Checkpoint E12-B's approval releases it)

Internal milestones (not 👤 checkpoints):
- per task: lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest` green; pgTAP green for T-147…T-150;
- after each migration task: `deploy-supabase` green on `main`, data intact, before the next task or checkpoint;
- T-146's plan: Android 17 technique verified and recorded.

## Dependencies

```
v1.2.0 ─► T-146 ─► T-147 ─► (deploy) ─► [Checkpoint E12-A 👤: sound chosen]
       ─► T-148 ─► (deploy) ─► T-149 ─► (deploy) ─► T-150 ─► (deploy) ─► [Checkpoint E12-B 👤] ─► T-151 ─► v1.3.0
```
- Strictly sequential (one active task at a time).
- T-148 needs Checkpoint E12-A's approval (the sound choice) and T-147's matcher and message.
- Earlier work used:
  - `RestTimer`, `AlertSound`, the `Haptics` alert pattern and `RestAlertPattern` (E3, E9);
  - the live session and its ⋮ actions, `save_routine`, "Editar entreno" and the workout detail (E3, E6);
  - `training_preferences` and the Preferencias hub (E2, E6, T-138);
  - the custom exercise form (E4, E6);
  - the duration formatter `durationText` (E6 statistics, T-144 Actividad);
  - the process-lifecycle hook (T-135, T-142);
  - T-139's temporary-screen precedent.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Android 17 delays or blocks the alert with the screen off (Doze, exact-alarm permission, foreground-service limits) | High | Verified in T-146's plan with sources; tested on the user's phone at Checkpoint E12-A items 1-2; a permission the user must grant is asked before implementation |
| `shortService`'s 3-minute cap is shorter than some rests | Medium | The planner checks the longest `rest_seconds`; another technique or service type if needed |
| A notification channel's sound cannot change after creation | Medium | The temporary chooser plays the sound itself or uses one channel per candidate; T-148 creates the final channel with a new ID |
| The sound is tested at Checkpoint E12-A from a chooser that T-148 later removes | Low | The pick is recorded with the approval; Checkpoint E12-B item 1 re-tests the wired sound |
| Sound candidates carry a licence problem in a public repo | Low | Synthesised or licence-free assets only; the system sound is referenced, not bundled |
| The strong-pain list misses or over-matches terms | Medium | Fixed list agreed by the user (Q1; plain "dolor"/"molestia" excluded on purpose); whole-word normalised matching; host tests for every term and its variants; the message never diagnoses |
| Moving `durationText` changes statistics or Actividad output | Low | Moved with no change to its output; existing tests keep passing, plus new cases |
| The movement-pattern mapping is wrong for some rows | Medium | Reviewed mapping by key, pgTAP spot checks, coverage check; E13 uses it only for substitutions |
| Regenerating the catalogue migration needs the RepDB bundle | Low | Only if the generator must re-run (the user's local bundle, as in E4); otherwise an update migration from the mapping |
| T-148 exceeds ~25 files (notes + sound wiring) | Low | Developer slices (backend + core, then UI) within the same PR; no extra task |
| "Mi entrenamiento" next to "Entrenamiento" in the hub is confusing | Low | Gherkin literal kept; ux-reviewer checks the hub wording against R-04/§10; the user sees it at Checkpoint E12-B |

## Open Questions

**Resolved (user, 2026-10-28):**
- **E12 Q1 · Strong-pain term list and fixed message → option B, approved as drafted.**
  - Terms: "dolor fuerte", "dolor agudo", "dolor intenso", "pinchazo", "chasquido", "hormigueo", "entumecimiento", "hinchazón", "lesión", "me he lesionado", "desgarro", "no puedo mover". Plain "dolor" and "molestia" are deliberately excluded.
  - Message: "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario."
  - Used by T-147 (discomfort, severe) and T-148 (notes, message only). Recorded in `planning/09-open-questions.md` and the epic's US-67 technical notes.
- **E12 Q2 · Session length → option A: 15-180 min in 5-min steps, human-readable.**
  - Shown with the existing duration formatter `durationText` ("15 min", "45 min", "1 h 0 min", "1 h 5 min", "1 h 30 min", "3 h 0 min"), reused with no ad-hoc copy (T-149).
  - Recorded in `planning/09-open-questions.md` and the epic's US-65 technical notes.
  - Note: the existing pattern writes whole hours as "1 h 0 min", not "1 h". T-149 keeps that output unchanged. Dropping " 0 min" everywhere would be a separate product decision.

**Assumptions (non-blocking; applied unless the user objects at the checkpoints):**
- A1. Tapping "Descanso terminado" opens the app on the live session.
- A2. The notification permission is requested at the first rest of a session (Android 13+). The sound follows the phone's mute/ringer mode (E9 point 16: silent when muted).
- A3. `severe` is set only by the term match; the user does not mark severity by hand.
- A4. "Molestia registrada" and "Ya no me molesta" live in the live session, where the discomfort is logged. The exercise detail does not show it in E12.
- A5. A strong-pain term in a set or routine note shows the fixed message but does not create a discomfort.
- A6. Set notes are editable in "Editar entreno" and read-only in the workout detail.
- A7. "Mi entrenamiento" is a new hub section. The existing "Entrenamiento" section keeps warm-up, previous values, calculators and auto-update.
- A8. Choosing a place replaces the draft's equipment with that place's defaults; the user then edits it.
- A9. The movement pattern is editable only on custom exercises and shown nowhere else in E12.
- A10. E12's release version is proposed as `v1.3.0` (minor per epic) and confirmed at the closure.
- A11. ux-reviewer writes the Spanish copy beyond the Gherkin literals (sound chooser, permission explanation, place labels, movement-pattern labels) within the glossary, and the user sees it at the checkpoints.
