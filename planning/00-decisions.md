# Decisions (ADR index)

Live index of every closed decision, with the numbers frozen in Phase 1. The full text (context, decision, consequences, sources, approvals) of ADR-01..72 is in [`phase1/00-decisions.md`](phase1/00-decisions.md) and does not change. A decision is only changed by adding a new ADR that supersedes it (mark the old row "superseded by ADR-N").

## New ADRs go here

Phase 2 ADRs continue the numbering (next: **ADR-80**) and are written **in full** in this file (context, decision, consequences, approval), below the index. Add their one-line row to the table too. Format: context -> decision -> consequences. A new decision also updates the affected live docs.

## Index (Phase 1, ADR-01..72)

| ADR | Decision (one line) | Status |
|---|---|---|
| 01 | Online only: Supabase is the single source of truth, no Room/local DB; offline banner + disabled writes; DataStore for local preferences | |
| 02 | Google Sign-In via Credential Manager ID token; `local` flavor has a seeded "Test login"; email provider disabled in prod | |
| 03 | Original requirements only; Stitch mockups were a style reference, extra mockup features out of scope | |
| 04 | Adaptive workflow (UI tasks full flow, backend tasks pgTAP/CI); merge gated by required CI, not reviewer approval | QA step removed by 27 |
| 05 | Kotlin Multiplatform from day one, Android-only target; Android APIs behind interfaces in `androidMain` | |
| 06 | Open Food Facts only through cached Edge Functions (`food-search`, `food-barcode`) | |
| 07 | Barcode scanner = Google Code Scanner (no camera permission); unknown product offers a manual food | |
| 08 | Health Connect with a manual "Sincronizar" button (weight, body fat), dedup by record id, no background sync | Amended by 74 |
| 09 | 1RM estimated with Epley; AI assistant deferred | Assistant part superseded by 76 |
| 10 | Bottom navigation + adaptive rail; contextual FAB | Amended by 22, 26, 37; FAB removed by 36 |
| 11 | Single palette from the tokens in prose (`surfaceBase #0B0F14`, `neonLime #A3E635`...); macros lime/cyan/amber | |
| 12 | Keepalive workflow every 3 days calling the `health` function | |
| 13 | Public GitHub repo (branch protection, environments, secret scanning); all rights reserved; no secrets or personal data | Revisit if made private (67) |
| 14 | `applicationId = io.github.lopezjavier.appfitness`, `local` flavor adds `.local` | |
| 15 | `deploy-supabase` from E0; `T-###` tasks live in `tasks/todo.md` (plan in `tasks/plan.md`) | |
| 16 | English repository, Spanish UI through string resources; Gherkin UI literals stay Spanish | |
| 17 | Supabase publishable key in the app, secret key only server-side; no legacy anon/service_role keys | |
| 18 | JDK 21 runs the build (local and CI); Kotlin/Java bytecode target stays 17 | |
| 19 | Three modules: `androidApp` (application, flavors, BuildConfig), `composeApp` and `shared` (KMP libraries, single variant); `AppConfig` carries flavor values | |
| 20 | Required CI checks always report: no workflow-level path filter, a `changes` job (`dorny/paths-filter`) plus `if:` on the work job (fail-open) | |
| 21 | `minSdk` 26; `compileSdk`/`targetSdk` = highest stable supported by AGP (37) | |
| 22 | 3 tabs (Diario, Entreno, Progreso), Perfil from the avatar; active tab = lime icon + extra-bold label, filled glyph, no pill | Nav part superseded by 26, tab set by 37; indicator kept |
| 23 | Maestro QA in the background with findings as GitHub issues | Superseded by 27 |
| 24 | Onboarding resumes prefilled and updates (not duplicates) the onboarding weight; no sliders in onboarding; one "Guardar" on Profile storing the recalculated target | Weight amended by 37 |
| 25 | Calorie safety floor by sex: 1200 kcal women, 1500 kcal men | |
| 26 | Perfil as 4th tab with the Google photo as icon, avatar removed from top bars | Superseded by 37 |
| 27 | No Maestro QA; every Gherkin UI scenario has a host (Robolectric) test; manual verification with `scripts/deliver-apk.py`; defects become `fix(T-###)` | |
| 28 | One task per session (`/clear` between tasks, never `/compact`); local execution log holds only the current epic | |
| 29 | Account deletion from Profile: `delete-account` Edge Function (JWT, secret key), cascade deletes, confirmation dialog | |
| 30 | Forward-compatible training schema shipped with E3; RepDB free catalogue bundled in the APK (visible attribution); third-party credits in "Acerca de" | |
| 31 | Epic order: training first (E3 training core, E4 food diary, E5 weight, E6 training depth, E8 insights, E9 finalization); v1.0.0 only at the last epic | Amended by 58 |
| 32 | Food base unit `g`/`ml`, nutrition per 100 base units, named portions, entry snapshots | |
| 33 | Configurable meals via `meal_slots` (rename, reorder, extend) | Disable rule superseded by 41 |
| 34 | Favourites table plus derived "most eaten" RPC | |
| 35 | Body composition scope: extra metrics via Health Connect, bidirectional sync, circumferences table, target weight in profile, informative metrics only | |
| 36 | No universal FAB; each screen owns its actions | |
| 37 | 3 tabs Diario/Entreno/Perfil; Perfil root = photo, name, Preferencias, statistics entries; weight editable in Preferencias; name from Google or onboarding | Supersedes 26; tab set amended by 79 |
| 38 | RepDB catalogue text/images stay out of the repo and DB (client bundle only); superset rest model in `routine_exercises.rest_seconds` | |
| 39 | Exercise assets in `composeApp` Compose resources (gitignored); catalogue rows from a generated idempotent migration | |
| 40 | State restoration is a requirement of every stateful screen (`rememberSaveable`/`SavedStateHandle`, restoration tests); NavHostController above the root route | |
| 41 | Meals are deleted when unused (never disabled), at least one remains; edited from Preferencias -> Comidas | Amends 33; inside-editor timing by 69 |
| 42 | Saved templates are called "comidas"; no quick-add chips; edit ends in "Sobrescribir" / "Guardar como nueva" | |
| 43 | Store the full Open Food Facts product (`foods.off_data`); show micronutrients on demand | Manual nutrients amended by 53 |
| 44 | Diary history moves to Perfil -> Estadísticas; Diario always has the calendar button | |
| 45 | Shared interaction rules (delete immediate, single "Guardar", swipe-to-delete) | Rulebook/dialogs/swipe superseded by 69 (`05` §8-§10); folder exception 51 |
| 46 | Text food search limited to Spain with a Spanish name | |
| 47 | A Health Connect weight wins over manual weight for that day | |
| 48 | Drop "Comparar"; "Peso actual" box opens the composition; pencil edits today's weight | |
| 49 | Weight trend line and weekly pace work with sparse weigh-ins (MA7 over >= 1 day, last two weigh-in days) | |
| 50 | Routines planned set by set; planned values are placeholders; target RPE per exercise | |
| 51 | Deleting a routine folder deletes its routines, after a confirmation | |
| 52 | E6 scope cuts and defaults (live PR notification, calendar streaks, workout copy deferred; calculator defaults) | |
| 53 | Only manual foods are editable; edits apply to future entries; full label nutrients | |
| 54 | Planned sets table `routine_exercise_sets` and `training_preferences` (E6 schema exceptions) | |
| 55 | In-app "?" help, apply-on-tap pickers, mandatory RPE prompt, routine-level calculators | RPE steps amended by 56 |
| 56 | Whole-number RPE 6-10, derived rounding, disc loader, per-exercise hand count | |
| 57 | Protected accounts (`PROTECTED_EMAILS` secret) cannot be deleted from the app | |
| 58 | Epic renumbering with stable story IDs (E7 food photos/defaults/cooking, E8 insights, E9 finalization) | |
| 59 | Food photos use Gemini (free tier) through the `food-photo` Edge Function; photos never stored | |
| 60 | Cooking methods add only retained olive oil | Superseded by 65 |
| 61 | Default comidas = saved comidas applied once on the first open of a new day | |
| 62 | A manual food's unit is editable while unused; a comida row opens its details | |
| 63 | The comida details are the editor; "Añadir" adds what is shown | |
| 64 | Amount estimate from a photo for a chosen food; dish estimates zero-fill | Amended by 72 |
| 65 | Cooking adds fat as a percentage of raw weight (1 / 2 / 8 %) | Supersedes 60 |
| 66 | Periodic reports and streak check-ins move to Phase 2 | Check-in part taken up by 73 |
| 67 | No signed release build in Phase 1: `prodDebug` APK, v1.0.0 = tag + GitHub Release without APK | |
| 68 | Diary groups the entries added from a saved comida (`group_id`, `group_name` snapshot) | |
| 69 | UX rulebook (`05` §8-§10), designsystem templates and `check-ux-conventions.sh` guards | Amends 45 |
| 70 | "Actualizar automáticamente" is one global training setting | |
| 71 | Diario layout restored; sheets close only with X/back/"Cancelar" (R-38); icon for clear actions (R-39) | Amends 69 |
| 72 | Photo amount always gives a best estimate; confidence is a 1-10 scale (R-41) | Amends 64 |

(Numbers below 10 are zero-padded as in the frozen text and in code comments.)

## Index (Phase 2, ADR-73..)

| ADR | Decision (one line) | Status |
|---|---|---|
| 73 | In-app reminders computed on open (weight 2 days, circumferences 14 days), no time window; weekly check-in returns; soreness not collected | Amends 66 |
| 74 | Health Connect: automatic sync on the first open of the day plus the manual button; reads sleep, steps, calories, resting heart rate | Amends 08 |
| 75 | Rest-end alert with the screen off (the only notification the app posts); alert sound reopened | Amends E3 US-13 |
| 76 | Evidence-based coach: deterministic calculations, the model explains, proposals need acceptance, derived values confirmed, coach sets outcomes, RIR derived from RPE | Supersedes the assistant part of 09 |
| 77 | Claude through a `coach` Edge Function (Opus 5.5 plan, Sonnet 5.5 report and chat); paid AI capped at 2 € per user and 5 € in total per month; NFR-02 changed | |
| 78 | `activity_level` kept as the starting factor; `target_weight_kg` retired as a user input; intention, place, equipment, days and session length in `training_preferences`; `exercises.movement_pattern` | |
| 79 | 4 tabs Diario/Entreno/Trainer/Perfil: the coach gets its own tab between Entreno and Perfil, added with the first E13 UI task | Amends 37 |

## Phase 2 ADRs (full text)

### ADR-73 · In-app reminders, weekly check-in, no soreness prompt (E10; amends ADR-66)
- **Context:** The coach needs regular weigh-ins and measurements. Phase 1 has no reminders and deferred the weekly check-in (ADR-66). The drafts proposed allowed hours and a per-muscle soreness pop-up.
- **Decision:**
  - Reminders live only inside the app: computed from the stored data on each open or return to the foreground and listed in "Centro de avisos". No system notifications and no time window: a reminder whose condition was met while the app was closed is listed on the next open.
  - Weight reminder after 2 days in a row without a weigh-in; circumference reminder after 14 days (design decision). Each type can be silenced.
  - The weekly check-in returns (energy 1-5 and notes, on a chosen weekday, default Sunday) with the deterministic weekly summary; the coach adds its commentary in E13. Sleep is not asked (it comes from the watch). The streak stays deferred.
  - Muscle soreness is not collected: it is a poor indicator of recovery and adaptation, and no coach rule would use it.
- **Consequences:** `epics/E10-reminders-and-check-in.md`, `01-scope.md`, `07-coach-design.md` §5 and §7.
- **Approval:** 2026-10-28, user (chat).

### ADR-74 · Daily automatic Health Connect sync (E11; amends ADR-08)
- **Context:** ADR-08 syncs weight and body fat with a manual button. Phase 2 reads watch data (sleep, steps, calories, resting heart rate) whose granularity is daily; the user knows when a fresh weigh-in exists.
- **Decision:** On the first app open of each local day the app reads every granted type automatically; the "Sincronizar" button stays and reads every type on demand. A failed automatic sync is retried on the next open. Still no background sync. Writes remain weight and body fat only. Dedup by record id as in ADR-08; one row per user and day for daily totals.
- **Consequences:** `epics/E11-health-connect-daily-sync.md`; new tables `daily_activity`, `sleep_sessions`, `resting_heart_rate`; `health_sync_state.last_auto_sync_date`; `03-data-model.md` when implemented.
- **Approval:** 2026-10-28, user (chat; the trigger was delegated to the PM).

### ADR-75 · Rest-end alert with the screen off (E12; amends E3 US-13)
- **Context:** E3 US-13 alerts only in the foreground ("no foreground service or notifications in Phase 1"); a locked phone misses the end of rest.
- **Decision:** The end of rest vibrates, plays the alert sound and posts "Descanso terminado" when the app is in the background or the screen is off; in the foreground only the in-app alert shows. Skipping or finishing cancels it. This is the only system notification of Phase 2. The technique is chosen in the plan after verifying on Android 17. The alert sound is replaced, chosen by the user among the E9 point 16 candidates.
- **Consequences:** `epics/E12-training-data-and-preferences.md` US-64; notification permission handling; `01-scope.md` §6.
  - [x] Technique (T-146): exact alarm (`USE_EXACT_ALARM`, no user action) -> receiver -> notification. `02-architecture.md` §10.
  - [x] Android 17 verified (T-146): background audio is blocked without a visible activity or foreground service, also for exact alarms. So the sound and the vibration come from the notification channel (one channel per sound candidate, PM decision 2026-10-28); the in-app alert keeps playing in the foreground. T-148 creates the final channel under a new ID and deletes the others.
  - [x] `04-business-rules.md` §4.4 updated; permission handled at the first rest of a session, explained once.
- **Approval:** 2026-10-28, user (chat).

### ADR-76 · Evidence-based coach (E13; supersedes the assistant part of ADR-09)
- **Context:** ADR-09 deferred the AI assistant. The coach must be trustworthy: no invented figures, no change without consent, rules backed by evidence.
- **Decision:**
  - Rules, figures and sources live in `07-coach-design.md`; every number is sourced, common practice or labelled a design decision.
  - Calculations (BMR, activity, adaptive expenditure, trend, progression, e1RM, adherence, deload) are deterministic; the model selects within validated options and explains; its output is structured and validated; the model never writes to the database.
  - Plans are 4-week blocks; derived values are shown with their source and confirmed before use; adjustments are proposals accepted one by one.
  - The coach sets outcomes; the user never enters target numbers. An accepted plan writes the nutrition goal; the calorie floor (ADR-25) always applies; a manual edit in Preferencias still works.
  - RIR is derived from RPE (10 − RPE); there is no RIR field. Soreness is not a signal.
  - Discomfort on an exercise blocks load increases for 7 days or until resolved; strong-pain terms from a fixed list show a fixed message to consult a health professional; no diagnosis.
  - Coach suggestions are shown in the live session and never rewrite planned values; ADR-70 is unchanged.
  - Personal use: no consent screen; before distributing the app, every user must accept the data use (written condition).
- **Consequences:** `07-coach-design.md`, `epics/E12-training-data-and-preferences.md`, `epics/E13-performance-assistant.md`, `04-business-rules.md` when implemented.
- **Approval:** 2026-10-28, user (chat).

### ADR-77 · AI provider and cost cap (E13; changes NFR-02)
- **Context:** NFR-02 required 100 % free. Gemini's free tier serves food photos (ADR-59). The coach needs stronger reasoning; the user accepts paying under 5 € a month.
- **Decision:**
  - Claude through one `coach` Edge Function; the API key only as a Supabase secret (`ANTHROPIC_API_KEY`). Opus 5.5 for the block plan; Sonnet 5.5 for the weekly report and the chat; no model for deterministic features. Gemini stays only in `food-photo`.
  - Cost is capped by money, not calls: each call records its usage in `coach_usage`; the function refuses calls beyond 2 € per user or 5 € in total per calendar month (dollars counted as euros). The Anthropic account spending limit is the backstop. Over the cap, deterministic features keep working.
  - Estimate: ≈ $3 per month for a heavy user, ≈ $1 for a light one (`07-coach-design.md` §11).
  - Before E13: verify the API data policy and current prices.
- **Consequences:** NFR-02 changed (`01-scope.md` §3); `epics/E13-performance-assistant.md` US-76; `08-environment-setup.md` (new secret) when implemented.
- **Approval:** 2026-10-28, user (chat; budget figures proposed by the PM and approved with the planning PR).

### ADR-78 · Profile and catalogue for the coach (E12, E13)
- **Context:** The drafts made `activity_level` and `target_weight_kg` obsolete. `activity_level` feeds the Diario calorie target (`04` §1.3) and is the coach's starting factor before there is history. The coach needs the intention, place and equipment, and a movement pattern to substitute exercises.
- **Decision:**
  - `profiles.activity_level` stays; the coach proposes changes as a derived value (ADR-76).
  - `profiles.target_weight_kg` stops being a user input; it and its UI are removed in E13 when the coach's outcomes replace the E5 goal card.
  - `training_preferences` gains `intention`, `place`, `equipment[]`, `days_per_week`, `session_minutes`.
  - `exercises.movement_pattern` is filled by the generated catalogue migration (ADR-39) and is optional on custom exercises.
- **Consequences:** `epics/E12-training-data-and-preferences.md`, `epics/E13-performance-assistant.md`, `03-data-model.md` when implemented.
- **Approval:** 2026-10-28, user (chat).

### ADR-79 · The coach gets its own tab (E13; amends ADR-37)
- **Context:** ADR-37 fixed 3 tabs (Diario, Entreno, Perfil). The coach (E13) has a plan, a weekly report, a history and a chat, and needs a stable home.
- **Decision:** A fourth tab "Trainer" between "Entreno" and "Perfil" (order: Diario, Entreno, Trainer, Perfil). It appears with the first E13 UI task; before that the bar keeps 3 tabs. Active-tab styling stays as ADR-22. The label and icon are checked by ux-reviewer in that task's plan pass.
- **Consequences:** `epics/E13-performance-assistant.md` (navigation), `05-design-system.md` (navigation section) when implemented, `09-open-questions.md` (resolved).
- **Approval:** 2026-10-28, user (chat).

### ADR-80 · Pickers, read-only sheets and action menus close on an outside tap (amends ADR-71)
- **Context:** ADR-71 made every bottom sheet close only with its X, system back or "Cancelar". At Checkpoint E11 (2026-10-28) the user, with the calendar of Actividad open, expected a tap on the dimmed area outside to close it too.
- **Decision:** Pickers (calendar, day, metric and range pickers), read-only sheets and action menus (`AppActionSheet`, user decision 2026-10-28) close on an outside tap, besides X and system back. Sheets with editable input or unsaved changes (R-11) keep ADR-71: only X, system back or "Cancelar". Drag-down stays disabled and no sheet shows a drag handle.
- **Consequences:** `05-design-system.md` R-38, the sheet templates in `designsystem/` and the guard G11 in `scripts/check-ux-conventions.sh` (with its fixtures); the fix task sweeps every sheet.
- **Approval:** 2026-10-28, user (chat).

### ADR-81 · Discomfort level chosen from a scale; no strong-pain term matching or message (E12, E13; amends ADR-76, supersedes assumption A3 and E12 Q1)
- **Context:** At Checkpoint E12-A (2026-10-28) the user found the hidden 12-term list and the fixed "consult a health professional" message unclear: the app inferred severity from free text the user could not see.
- **Decision:**
  - Logging a discomfort asks the user to pick a **level** from an ordered scale, from least to most severe, each with a colour from greenish yellow to red (colour is never the only signal). The optional note stays as free-text context for the coach. The levels, their order, labels and colour tokens are defined by ux-reviewer in T-152; the proposal is five levels: tension or fatigue, mild discomfort, pinch or snap, swelling/tingling/numbness, sharp pain or injury. Related symptoms stay grouped.
  - `severe` is derived from the chosen level (the two highest), not from text. `StrongPainTerms`, term matching and the fixed message "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario." are **removed**; notes (T-148, E13) are never scanned for terms.
  - The coach (E13) uses the level to block load increases (ADR-76); nothing else is shown to the user.
- **Consequences:** T-152 reworks T-147 (migration `level`, core, UI, tests, `03`, `04` §4.12, `05` §8/§10, `07` §7, US-67 and the T-148 note scenarios); ADR-76's "fixed message" clause is void.
