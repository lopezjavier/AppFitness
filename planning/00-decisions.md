# Decisions (ADR index)

Live index of every closed decision, with the numbers frozen in Phase 1. The full text (context, decision, consequences, sources, approvals) of ADR-01..72 is in [`phase1/00-decisions.md`](phase1/00-decisions.md) and does not change. A decision is only changed by adding a new ADR that supersedes it (mark the old row "superseded by ADR-N").

## New ADRs go here

Phase 2 ADRs continue the numbering (next: **ADR-98**) and are written **in full** in this file (context, decision, consequences, approval), below the index. Add their one-line row to the table too. Format: context -> decision -> consequences. A new decision also updates the affected live docs.

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
| 51 | Deleting a routine folder deletes its routines, after a confirmation | Superseded by 95 |
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
| 76 | Evidence-based coach: deterministic calculations, the model explains, proposals need acceptance, derived values confirmed, coach sets outcomes, RIR derived from RPE | Supersedes the assistant part of 09; amended by 86, 87, 88 |
| 77 | Claude through a `coach` Edge Function (Opus 5.5 plan, Sonnet 5.5 report and chat); paid AI capped at 2 € per user and 5 € in total per month; NFR-02 changed | Per-user cap amended by 90 (3 €) |
| 78 | `activity_level` kept as the starting factor; `target_weight_kg` retired as a user input; intention, place, equipment, days and session length in `training_preferences`; `exercises.movement_pattern` | |
| 79 | 4 tabs Diario/Entreno/Trainer/Perfil: the coach gets its own tab between Entreno and Perfil, added with the first E13 UI task | Amends 37 |
| 80 | Pickers, read-only sheets and action menus close on an outside tap; sheets with editable input or unsaved changes keep ADR-71 | Amends 71 |
| 81 | Discomfort is a level chosen from an ordered scale; no strong-pain term matching or fixed message | Amends 76; amended by 89 |
| 82 | A note is a chosen option plus optional text; a live routine note is a routine change | Amends 81 |
| 83 | `band_long` is its own equipment with 30 cable counterparts and a curated symmetric alternatives graph; trainer replacements apply only on "Guardar bloque" | Amended by 92 |
| 84 | Expenditure anchors on the capped 28-day intake; weight slope only with enough data; plan target checked against the intention's rate | |
| 85 | The plan sets weekly cardio minutes and daily steps; cardio is the last exercise of every routine; cardio machines are their own equipment | Amends 76; amended by 89 (no medical line), 92 (one cardio machine item) |
| 86 | Long-term follow-up: intake priors, one phase per block, end-of-block review proposes the next block and any phase change, breaks pause the block | Amends 76 |
| 87 | The user's free text reaches the model verbatim, capped, visible and excludable line by line; the model only describes features and data that exist | Amends 76 and `07` §8 |
| 88 | One behaviour "Entrenador": inside an accepted block, load progression, calorie changes in the safe band and deloads apply on their own with "Deshacer"/cancel; phases and new blocks need acceptance | Amends 76 |
| 89 | No generic health disclaimers; one data-triggered note on a persisting level 4-5 discomfort; train around an injured area with a graded return the user paces | Amends 81 and 85; reverses E13 Q29 |
| 90 | The per-user monthly AI cap rises from 2 € to 3 €; the 5 € global total stays | Amends 77 |
| 91 | Minimal intake (years, maintenance, free text) and no "Recuperar" (deferred to Phase 3); no field extraction | Amends 86, 87 |
| 92 | Equipment split: "Barra Z" is the only new item, every other implement is "Otro"; one "Máquina de cardio"; same barbell / EZ bar / dumbbell exercise is a 1.00 alternative both ways; no alternative below 0.90 | Amends 83, 85 |
| 95 | A routine folder with routines cannot be deleted: the user moves or deletes the routines first; no dialog | Supersedes 51; amends 45, 69 (R-08) |
| 96 | The coach never refuses a call because of money: no per-user or total cap; `coach_usage` records the spend and one non-blocking notice shows when the month passes 5 € | Supersedes 77's cap and 90 |
| 97 | The folder review is deterministic and runs on the device: a 0-10 score and a checklist, no model call, no "Aplicar en una copia"; "a block from a folder" keeps its model review | Supersedes 93's folder-review bullet; 94 and 95 unaffected |

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
- **Amended 2026-11-04 (ADR-99):** the `block_splits` and `plan_block` model calls are dropped (block creation is deterministic); the rest of this ADR stands.
- **Context:** ADR-09 deferred the AI assistant. The coach must be trustworthy: no invented figures, no change without consent, rules backed by evidence.
- **Decision:**
  - Rules, figures and sources live in `07-coach-design.md`; every number is sourced, common practice or labelled a design decision.
  - Calculations (BMR, activity, adaptive expenditure, trend, progression, e1RM, adherence, deload) are deterministic; the model selects within validated options and explains; its output is structured and validated; the model never writes to the database.
  - Plans are 4-week blocks; derived values are shown with their source and confirmed before use; adjustments are proposals accepted one by one.
  - The coach sets outcomes; the user never enters target numbers. An accepted plan writes the nutrition goal; the calorie floor (ADR-25) always applies; a manual edit in Preferencias still works.
  - RIR is derived from RPE (10 − RPE); there is no RIR field. Soreness is not a signal.
  - Discomfort on an exercise blocks load increases for 7 days or until resolved; ~~strong-pain terms from a fixed list show a fixed message to consult a health professional~~ (void, ADR-81: the user picks a level); no diagnosis.
  - Coach suggestions are shown in the live session and never rewrite planned values; ADR-70 is unchanged.
  - Personal use: no consent screen; before distributing the app, every user must accept the data use (written condition).
- **Consequences:** `07-coach-design.md`, `epics/E12-training-data-and-preferences.md`, `epics/E13-performance-assistant.md`, `04-business-rules.md` when implemented.
- **Approval:** 2026-10-28, user (chat).
- **Amended 2026-11-01:** "adjustments are proposals accepted one by one" and "suggestions never rewrite planned values" are void inside an accepted block (ADR-88); "derived values confirmed before use" now also covers the intake priors (ADR-86); the summary carries the user's free text (ADR-87).
- **Amended 2026-11-03 (ADR-93):** "the model selects within validated options" no longer applies to the training block: the model builds the whole block (split, exercises, sets, rep ranges, target RPE, rests, supersets and drop sets under their time conditions) and one deterministic routine checker validates it. Every other calculation stays deterministic. *(Superseded 2026-11-04 by ADR-99, removed in T-224: no model builds the block.)*

### ADR-77 · AI provider and cost cap (E13; changes NFR-02)
- **Context:** NFR-02 required 100 % free. Gemini's free tier serves food photos (ADR-59). The coach needs stronger reasoning; the user accepts paying under 5 € a month.
- **Decision:**
  - Claude through one `coach` Edge Function; the API key only as a Supabase secret (`ANTHROPIC_API_KEY`). Opus 5.5 for the block plan; Sonnet 5.5 for the weekly report and the chat; no model for deterministic features. Gemini stays only in `food-photo`.
  - Cost is capped by money, not calls: each call records its usage in `coach_usage`; the function refuses calls beyond 2 € per user or 5 € in total per calendar month (dollars counted as euros). The Anthropic account spending limit is the backstop. Over the cap, deterministic features keep working.
  - Estimate: ≈ $3 per month for a heavy user, ≈ $1 for a light one (`07-coach-design.md` §11).
  - Before E13: verify the API data policy and current prices.
- **Consequences:** NFR-02 changed (`01-scope.md` §3); `epics/E13-performance-assistant.md` US-76; `08-environment-setup.md` (new secret) when implemented.
- **Approval:** 2026-10-28, user (chat; budget figures proposed by the PM and approved with the planning PR).
- **Amended 2026-11-02 (ADR-90):** the per-user cap is 3 €; the 5 € total is unchanged.

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
- **Decision:** A fourth tab "Trainer" between "Entreno" and "Perfil" (order: Diario, Entreno, Trainer, Perfil). It arrives with T-158 (the derived-values card is its first root); before that the bar kept 3 tabs. Active-tab styling stays as ADR-22. The label and icon are checked by ux-reviewer in that task's plan pass.
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
  - The coach (E13) uses the level to block load increases (ADR-76); nothing else is shown to the user. *(Amended by ADR-89, 2026-11-01: one data-triggered note when a level 4-5 discomfort persists.)*
- **Consequences:** T-152 reworks T-147 (migration `level`, core, UI, tests, `03`, `04` §4.12, `05` §8/§10, `07` §7, US-67 and the T-148 note scenarios); ADR-76's "fixed message" clause is void.
  - [x] Migration `level` (`severe` dropped, derived as level >= 4) and pgTAP.
  - [x] Core: `DiscomfortLevel`, `LogDiscomfort(exerciseId, level, note)`, `StrongPainTerms` deleted.
  - [x] UI: required coloured level picker with help, open row with the level, `AppTextField` v4, help on every scale (R-45).
  - [x] Docs: `03`, `04` §4.12, `05` §2.6/§8 (R-45)/§10, `07` §7, `09` Q1.

### ADR-82 · Notes are a chosen option plus optional text; a live routine note is a routine change (E12; amends US-66 and ADR-81's note clause)
- **Context:** At Checkpoint E12-B (2026-10-29) the user found free-text notes unexplained ("what do I write?") and pointless in the live session: a set note was seen only in that workout's detail, and a note edited live never reached the routine unless the routine was updated.
- **Decision:**
  - A note is one chosen option plus an optional free text ("Otro", 1-200 chars), like the discomfort levels. Set options carry only what kg, reps, RPE and rest do not say: "Técnica mejorable", "Rango incompleto", "Agarre flojo", "Con ayuda", "Otro". Routine-exercise options: "Subir peso", "Bajar peso", "Cuidar la técnica", "Ajustar máquina o asiento", "Otro". Pain is never a note option (it is the discomfort log). Storage: `note_kind` enum beside `notes` on `workout_sets` and `routine_exercises`; existing free-text notes become `other`. Notes are still never interpreted (ADR-81).
  - A routine-exercise note edited in the live session counts as a routine change and triggers the existing "update the routine?" choice; declined, it stays only in that workout. Discomforts never count (they belong to the exercise, not the routine).
  - Every option list has a `?` Info sheet (R-46).
- **Consequences:** T-153 (migration `note_kind` + pgTAP, core, UI, `03`, `04`, `05` §10, glossary). The coach (E13) reads the kind plus the text.
- **Approval:** the user, 2026-10-29, in the chat.

### ADR-83 — Long band as its own equipment and curated exercise alternatives (training catalogue; user request 2026-11-01)
- **Context:** The catalogue's `band` is a short loop band. A long elastic band can replace many cable exercises at home, but it is a different tool for equipment filtering, weights and instructions. The catalogue also has no way to offer a similar exercise when the selected one is unavailable or the user prefers an alternative; the coach and the trainer need the same curated graph.
- **Decision:**
  - `band_long` is added to `public.equipment` and to the app's `Equipment` enum. It never replaces `band`. Both Home and Gym select it by default; Gym already selects every value.
  - The catalogue gets 30 long-band exercises, one per cable exercise: key `band-long-<cable-key>`, same muscle group and hand count, `per_hand = false`, and its own Spanish name and instructions. The cable exercise stays in the catalogue.
  - Each cable exercise and its long-band counterpart have similarity 1. The alternatives are a curated, symmetric graph in `exercise_alternatives` (one row per unordered pair, similarity greater than 0 and at most 1); a long-band row inherits its cable source's alternatives, mapped to the long-band counterpart when one exists. The graph is data, not a model call.
  - The exercise detail shows a "Similares" tab sorted by similarity; the trainer block preview can replace an exercise only with one of its alternatives. A replacement is local until "Guardar bloque" and, once saved, applies to that session in every week of the block. Leaving discards it.
  - The 30 rows, their source media keys and the alternatives come from the two outside-repo CSVs. Names, descriptions, instructions, tips and media stay in the gitignored exercise bundle; no RepDB text or images enter the repo or the database (ADR-38/39). The database migration stores only keys and catalogue metadata.
- **Consequences:** T-179 (migration of the enum, catalogue rows and `exercise_alternatives`; generated library entries and copied media outside the repo; `Equipment.BandLong`, defaults, labels and hints; Similar tab; trainer picker and `save_coach_block` edit payload; coach equipment order; `03`, `04`, `05`, `07`; pgTAP, Kotlin and Robolectric tests). The old APK may fail to read `band_long` until the new APK is installed after T-179; the database-first deployment is accepted.
- **Approval:** 2026-11-01, user (chat).
- **Amended 2026-11-03 (ADR-93, ADR-94):** the block preview swap stays limited to curated alternatives, but the model's own exercise choice is not: it picks from the whole eligible catalogue (equipment, group, pattern), unranked and uncapped. Favourite exercises are listed first in the preview swap list and in the exercise picker (ADR-94); the similarity order follows inside each part.

### ADR-84 — Expenditure anchors on capped intake; slope only with enough data; target checked against the rate (E13, T-180; Checkpoint E13-B1 feedback)
- **Context:** A reported plan recommended 2 972 kcal (+710 over a mean intake of 2 262). A slope of −0.263 kg/week from 5 weigh-ins (`low_confidence`) added +289 kcal to the adaptive expenditure, then the +440 surplus of the rate stacked on it.
- **Decision:**
  - A reliable diary (20 of 28 days) makes the expenditure `adaptive` with no minimum of weight weeks; the base is `anchor_kcal`, the 28-day mean with partial days (under 40 % of the median) excluded and big days capped at 150 % of the median.
  - The weight-slope term is used only with 4 weekly averages, 8 weigh-in days and a reliable diary; the plan also clamps it to ±0.5 % × weight × 11 kcal. Otherwise weight is assumed stable (expenditure = anchor).
  - `computeNutrition` and `assertNutritionInvariants` check `target − anchor` against the intention's rate range (`04` §8.7). No fixed kcal cap.
  - Calorie changes (`evaluate_coach_calorie_change`, at most ±10 % of the accepted target) are not migrated; revisit only if reported.
- **Consequences:** migration `coach_realistic_expenditure`, `coach` function (`nutritionRules.ts`, `validators.ts`, `planNutrition.ts`), `04` §7-§8, `07` §2 and §4. Users with a reliable diary and fewer than 3 weeks of weigh-ins move from `formula` to `adaptive` (their confirmed expenditure may be re-asked once) and are no longer blocked by missing age or height (`04` §7.9). A user who has been dieting or bulking without weigh-ins gets a maintenance equal to their intake (assumption: stable weight). T-168's evaluation set must include this shape.
- **Approval:** user, 2026-11-01 (scope and checklist agreed in the chat; numbers are proposed defaults).

### ADR-85 — The coach prescribes weekly cardio and daily steps; cardio is the last exercise of every routine (E13; amends ADR-76's block content and `04` §9.6) · ACCEPTED
- **Context:** The user (2026-11-01): "the app replaces a real trainer; if the trainer would plan cardio, the app must". Today the plan has only calories and macros, and the block only lifting: `04` §9.6 says the block has no cardio or steps fields and `candidates.ts` excludes the `cardio` pattern. The only cardio rule is "never reduce cardio or steps" (`07` §3). Planned sets store weight and reps only (`target_reps` is required; no planned duration). Rules, grades and sources: `07` §3 "Cardio prescription", §9, §10 refs. 29-50 (checked online 2026-11-01); E13 Q23-Q30 resolved 2026-11-01.
- **Decision (proposed):**
  - **Plan targets (Q23-Q25, Q29).** The plan carries a weekly cardio target (minutes of moderate, continuous work, shown as "ritmo suave") and a daily steps target, computed by the server's deterministic rules, never by the model. Cardio: 150 min/week proposed for every intention (for "Perder grasa" inside the band 150-300); the user edits the minutes freely, and a value below 150 shows a plain note, never a block. Steps: max(28-day average rounded down to 500, 7 000) at every age, not editable, with a plain note on where it comes from. The planned cardio does not change the calories (the adaptive expenditure absorbs the cardio actually done). ~~The cardio help sheet ends with one static medical line (Q29)~~ (void, ADR-89: no generic disclaimers; the line is removed); no questionnaire, no blocking.
  - **Block placement (Q26, Q30).** No separate cardio days and no non-lifting routines: cardio is the **last exercise of every lifting routine**. The weekly minutes are split evenly across the week's routines in whole 5-minute units (remainder to the first routines), at most 60 min per routine; minutes over the cap are shown as "por tu cuenta" (any cardio exercise logged that week counts). Cardio is outside "Duración de la sesión": the lifting keeps its time and volume, no lifting slot is removed, and the preview shows each routine's total time. The exercise comes from the catalogue's `cardio` pattern within place and equipment (low-impact machines first, walking without equipment); no intervals, no heart-rate zones (Q28).
  - **Cardio equipment (`07` §3, Q28b; user decision 2026-11-01).** Cardio machines leave `machine` and form their own category: five new `public.equipment` values `cardio_bike` ("Bicicleta estática"), `treadmill` ("Cinta"), `elliptical` ("Elíptica"), `rower` ("Remo"), `cardio_other` ("Otra máquina de cardio"); the catalogue's cardio machines are re-tagged; `walking` and `running` stay `bodyweight`; `machine` means lifting machines only. "Mi entrenamiento" > "Equipamiento" shows them as a "Cardio" chip group under the lifting chips with the helper "Sin máquinas de cardio, el entrenador te propone caminar a paso ligero."; "Gimnasio" preselects the five, "Casa" none; "Antes de empezar" shows a "Cardio" row ("Bicicleta estática, Cinta" or "Sin máquinas · caminar"). Saved equipment lists gain no cardio value and an old `machine` is never cardio (task T-184, US-80).
  - **Default order (`07` §3, Q28b).** The most-logged cardio exercise of the last 8 weeks if it is inside the equipment; else by the selected machines: stationary bike, elliptical, incline treadmill walk, rower; with no cardio machine (or only `cardio_other`), brisk walking, which always reads "a paso ligero" ("Caminar a paso ligero"). Never by default: running, treadmill running, air bike, ski erg, stair climber and the non-duration moves.
  - **Swap filter: any-to-any (user requirement, 2026-11-01).** Every catalogue cardio exercise timed in minutes whose equipment is one of the user's cardio values or `bodyweight` (so walking and running always) is an alternative of every other cardio exercise, at the same minutes. There is no hand-curated pairs table and no per-exercise alternative list for cardio: the alternatives query and the validators derive them from category (`cardio` pattern + duration metric) and equipment, never from the T-179 `exercise_alternatives` pairs. The minutes never change with a swap, because WHO 2020 defines moderate by effort, not modality; the minutes, the position (last) and the reason stay, and the swap applies to every week of that routine. Only cardio is offered on a cardio slot, and a cardio exercise is never accepted on a lifting slot.
  - **No warm-up (Q27, user).** The coach does not plan, generate or mention warm-ups (no warm-up exercise, ramp-up sets, tip or copy); the user adds warm-up sets himself.
  - **A cardio set is minutes only.** One cardio exercise per routine with one planned set of minutes: no reps, no RPE, no rest timer. It reuses the existing duration metric types (`duration`, `distance_duration`) and the shared set rows and labels (`SetRow`, `SessionSetRow`, `MetricTypeLabels`); `routine_exercise_sets` gains one nullable planned duration, accepted by `save_routine` only for duration metric types (with no planned reps), and the live session shows it as the placeholder. Nothing else in the routine model changes.
  - **Adherence (US-73).** Each plan week shows the logged cardio minutes against the target and the average steps against the steps target; nothing carries over to the next week.
  - The validators check the weekly minutes, cardio last in every routine, the per-routine cap, the equipment, and that a cardio slot has no reps or RPE; "never reduce cardio or steps" stays.
- **Consequences:** `07` §3, §9, §10 (final); `04` §8.8 (activity targets), §9 (cardio slots, §9.6 rewritten), §9.9 (weekly cardio and steps); `03` (planned duration column, `target_reps` nullable for duration sets, plan JSON, five equipment values); `05` §10 (glossary: "Cardio", "Pasos", "Ritmo suave", "Por tu cuenta"); `01-scope.md` §4 row; E13 US-79 and US-80 (new), US-72 and US-73 amended; tasks T-181 (targets in the plan), T-184 (cardio equipment), T-182 (block backend), T-183 (preview, routine editor, live session and weekly tracking). Weekly minutes edit: E13 Q31 (form sheet on the "Cardio" row, proposal and "Tu plan"; an edit after saving changes the target and the next block, not the saved routines).
- **Approval:** approved by the user, 2026-11-01 (proposed by the epic-planner, 2026-11-01, reworked the same day after the fitness-scientist and ux-reviewer passes; approved with the cardio breakdown).
- **Amended 2026-11-01 (ADR-89):** the static medical line of the cardio help sheet is removed (E13 Q29 reversed).

### ADR-86 — Long-term follow-up: intake, phases over the year and the end-of-block review (E13; amends ADR-76)
- **Context:** The user (2026-11-01): E13 is not going in the right direction; the app must replace a trainer he can trust for years, and the follow-up (how the plan adapts over months, phases over the year) is the biggest gap. Today a plan is one 4-week block from the intention; nothing happens when the block ends, the coach does not know the user's past (a returning lifter is a "beginner"), and calendar weeks run on during a break. Design (user-approved direction, written by the fitness-scientist): `07` §1, §2, §4, §12 (sources §10 51-66).
- **Decision:**
  - **Intake "Para conocerte" (E13 Q32):** structured fields (training age, situation: first time / continuous / returning after a break / returning after an injury, injured area, best lifts before the break as weight × reps, known maintenance and cutting calories) plus free text. The model extracts a structured profile from the free text; the server validates it; the user confirms or corrects each field; nothing extracted is used until confirmed. Every answer is a provisional prior ("Según lo que nos contaste") replaced by measured data at the thresholds of `07` §2. Experience = the higher of the self-reported class and the app history class; a returning lifter keeps the class. "Returning" is also set by the app after 28 days without a completed session.
  - **Phases (E13 Q34-Q35):** each block runs in one phase: cut ("Definición"), gain ("Volumen"), maintenance, recomposition, regain ("Recuperar"), minimal ("Mínimo"), with the calories and training of `07` §12.3 (cut: deficit ≤ 500 kcal/day; regain: never a deficit; minimal: 1 session and 1 set of the main lifts). A returning lifter starts in regain at maintenance (rate +0.25 %/week for "Ganar musculatura" / "Ganar fuerza"). Phase triggers are deterministic and evaluated **only at the end-of-block review**; the user accepts the change or keeps the current phase ("Seguir como estoy"). The intention stays the long-term goal.
  - **End-of-block review (E13 Q35, Q38):** at the end of each block (or earlier when the user asks or the preferences change) the coach shows plan vs done and the measured data, asks "¿Cómo te ha ido?" (Bien / Me ha costado / Me ha sobrado + optional text) and optional "Priorizar" / "Mantener" per muscle group, and proposes the next block and any phase change by the rules of `07` §12.4 (the model chooses among eligible options and explains). The user accepts or edits the new block; nothing starts without acceptance.
  - **Breaks follow the plan, not the calendar (E13 Q40):** a calendar week with no completed session does not count as a block week; the block pauses and resumes at the next planned session, and the review date and the deload evaluation move with it.
  - **Life changes (`07` §12.8, user-approved 2026-11-01):** saving a new place, equipment, days or session length (or injured area) during a block offers "Revisar el bloque": "Adaptar el bloque" (a rule, no model call: same week, phase, sets and ranges, exercises swapped by group and pattern), "Bloque nuevo" (the early review, needs acceptance) or "Solo unos días" (temporary swaps until a date, the minimal routine if fewer days); an intention change offers only "Bloque nuevo"; a new exercise loads from the user's own history or by RPE 7-8, never converted; the user's swaps are kept in the next block; age rules apply from the next block after turning 65.
  - **Data cleaning (E13 Q41):** partial diary days (under 40 % of the median of the 28-day logged days, the anchor rule of `04` §7.3, now excluded from every count) are excluded; a week with fewer than 5 complete diary days or 3 weigh-ins changes no calories.
  - **Cost (E13 Q43):** every number comes from deterministic rules; the model is used for the intake extraction, the weekly report, the block review and next block, and the chat, inside the 2 € per user and month cap (ADR-77).
- **Consequences:** `07` §1-§4, §12 (final, fitness-scientist); `04` §7.5, §8.5, §9.9, §9.10 (rule changes, detail in the tasks); `03` (intake, phase, review tables) when implemented; E13 US-81 (intake), US-82 (phases), US-83 (review), US-84 (priorities), US-88 (breaks, minimal, return), US-91, US-92 (life changes), US-68, US-69, US-73 amended; tasks T-185…T-191, T-196, T-197.
- **Approval:** user, 2026-11-01 (chat; direction and every decision approved, recorded as E13 Q32-Q43).
- **Amended 2026-11-03 (ADR-93, ADR-94; user, T-202):** "the user's swaps are kept in the next block" is void: the next block does not remember swapped-out exercises; favourites and the user's own routine folders carry the preference (`07` §12.8 row 11). The next block is built by the model like any block (ADR-93); the review's volume and priority rules become per-group bounds of the routine checker. A block is a normal routine folder once saved, and it can be removed without a new plan (ADR-94). Life changes: "Adaptar el bloque" only for a place or equipment change; a days or session-length change offers "Bloque nuevo" or "Solo unos días" (`07` §12.8 rows 1-3; whether "Adaptar el bloque" stays at all is E13 Q55).

### ADR-87 — The user's free text reaches the model; the model is grounded in what exists (E13; amends ADR-76 and `07` §8)
- **Context:** `07` §8 and `04` §7.8 excluded all free text from the coach summary, so the coach never read what the user wrote (check-in notes, discomfort notes) and could not read an intake in the user's own words. Leading AI coaches also invent features and data (benchmark, `07` §1).
- **Decision (E13 Q33, Q42):**
  - The summary carries one block "Lo que el entrenador sabe de ti": the intake free text (up to 500 characters) and the check-in notes, discomfort notes and "¿Cómo te ha ido?" answers of the last 8 weeks, newest first, verbatim, cut at 2 000 bytes in total. Set and routine notes, food names and custom exercise names stay excluded.
  - The user sees that block exactly as sent (Trainer) and can leave any line out of what is sent; the original note is kept.
  - Free text is never matched against terms, never triggers a health message and never changes a figure or a trigger by itself (`07` §7).
  - **Grounding:** the model only describes app features, screens and data that exist; the server validates outputs against a feature list and the summary and drops any text that mentions anything else.
- **Consequences:** `04` §7.8 (summary block and cap), `07` §1, §7, §8; E13 US-89 (new), US-71 and US-77 amended; tasks T-185, T-186 (block and screen), T-166 and T-169 (grounding validator).
- **Approval:** user, 2026-11-01 (chat).

### ADR-88 — One behaviour "Entrenador": small changes apply on their own, visibly and reversibly (E13; amends ADR-76)
- **Context:** ADR-76 made every adjustment a proposal accepted one by one and kept suggestions out of the planned values. The user (2026-11-01) wants a trainer, not a form to approve every week; leading apps apply load progression automatically and let the user cancel deloads (benchmark). Autonomy support (explaining and offering choice) is associated with better health behaviour [Ntoumanis 2021, `07` §1].
- **Decision (E13 Q36-Q37):**
  - No mode selector. **Inside an accepted block** these apply on their own, each visible with its reason: load progression (the increase and the 5 % drop, written to the next session of that exercise) with "Deshacer"; weekly calorie changes inside the safe band of `07` §4 (at most ±10 % of the block's calories in total, one change ≤ 10 %, 14 days apart, floor, 500 kcal deficit cap, intention rate range, never a deficit in regain or maintenance) with "Deshacer"; deloads, triggered or scheduled (week 4 when none ran in the previous 7 weeks, not in minimal), with "Cancelar descarga"; the user can also start one ("Hacer descarga la próxima semana").
  - **Needs acceptance:** a new block, a phase change, and a calorie change that would leave the safe band (it waits for the end-of-block review).
  - Outside an accepted block the progression stays a suggestion in the live session, as today.
  - ADR-70 ("Actualizar automáticamente") is unchanged for the user's own changes.
- **Consequences:** `04` §4.14, §8.5, §9.10 (rule changes); `05` §10 glossary entry "Deshacer" for undoing an automatic coach change (R-20 still forbids it for deletion); E13 US-85, US-86 (new), US-69, US-70, US-75, US-74 amended; tasks T-192, T-193 (fix of the merged accept-first behaviour of T-159, T-161, T-165).
- **Approval:** user, 2026-11-01 (chat).

### ADR-89 — Health messages from the user's data only; training around an injury (E13; amends ADR-81 and ADR-85; reverses E13 Q29)
- **Context:** ADR-81 removed every health message; ADR-85 (E13 Q29) then added a static medical line to the cardio help sheet. The user (2026-11-01) wants no generic disclaimers, but a trainer would say something when a severe discomfort does not go away, and would keep training the rest of the body around an injury.
- **Decision (E13 Q39, Q40):**
  - No generic disclaimers anywhere in the app: the cardio help sheet's medical line is removed (T-188), and so are the pre-E13 `metric_disclaimer` and the underweight "consulta con un profesional" line (also T-188). A health line is allowed only when it comes from the user's own data and goes with a decision the app takes (`07` §7).
  - One specific note, **once per discomfort**, when a level 4-5 discomfort persists (still unresolved 14 days after it was logged, or logged at level 4-5 on the same exercise in 3 sessions): "Esta molestia sigue ahí desde hace 14 días. Seguimos entrenando el resto sin cargar esta zona. Puede merecer la pena que te lo revise un profesional." Shown on the discomfort row and in the weekly report, dismissible, never blocking. Triggered by levels and dates only, never by text.
  - **Train around the area:** the next block keeps every exercise that does not load the injured area (intake) or a level 4-5 exercise and leaves those out; the user can put any exercise back.
  - A level 4-5 discomfort stays **unresolved**, whatever its age, until "Ya no me molesta" or graded-return step 3 (the 7-day "open" window governs only the load block of levels 1-3). Inside a block the coach **offers** to swap the exercise for the rest of the block; the swap applies on the user's tap (`07` §12.8 row 7).
  - **Graded return** for a flagged exercise, about 6 weeks: (1) a non-flagged substitute of the same movement pattern, (2) the original at the last load before the discomfort with the note "Rango sin molestia", (3) full range with normal progression; each step at least one week; the app offers the next step after a week with no new discomfort and **the user decides** when to move on; a new discomfort sends it back one step (level 4-5: to step 1). No diagnosis, no treatment.
- **Consequences:** `07` §3 (medical line removed), §7 (final); `04` §4.12 (rule change); E13 US-87, US-90 (new), US-79 amended; tasks T-188 (line removal), T-194 (train around, graded return, note), T-166 (note in the report).
- **Approval:** user, 2026-11-01 (chat).

### ADR-90 — The per-user monthly AI cap is 3 € (E13; amends ADR-77)
- **Context:** ADR-77 capped paid AI at 2 € per user and 5 € in total per calendar month. The trainer-journey replan (ADR-86, ADR-87) adds the intake extraction, one review call per block and a free-text block in every call; the epic-planner's estimate for a heavy user is ≈ 3,1 € a month (`07` §11), so 2 € would cut a heavy user's chat early every month.
- **Decision:** the per-user cap rises to **3 €** per calendar month; the **5 €** total for all users stays (dollars still counted as euros, the Anthropic spending limit stays the backstop). With the cost table (heavy ≈ 3,1 €, light ≈ 1 €), one heavy user plus two light users reach the 5 € total; with more heavy users the total, not the per-user cap, stops calls first. That is accepted for personal use (1-5 users); revisit the total only before the app is shared (`07` §8).
- **Consequences:** `01-scope.md` NFR-02 and §4; `07` §8, §11; E13 US-76; the built code, folded into T-185: `supabase/functions/coach/budget.ts:2` (`USER_LIMIT_EUR = 2`) and its mirror `shared/src/commonMain/kotlin/io/github/lopezjavier/appfitness/domain/model/CoachUsage.kt` (`USER_MONTHLY_LIMIT_EUR`, read by `composeApp/…/feature/trainer/CoachUsageScreen.kt`, which then shows "de 3,00 €"), their `deno test` and Kotlin tests, and the `02`/`03` lines stating 2 EUR (`02-architecture.md:71`, `03-data-model.md:87`).
- **Approval:** user, 2026-11-02 (chat, after the 3,1 € heavy-user estimate).

### ADR-91 — Minimal intake: ask only what is used (E13; amends ADR-86, ADR-87)
- **Context:** Checkpoint E13-D (user, 2026-11-02): "Para conocerte" asked for data nothing read (`known_cut_kcal`, `pre_break_bests`, `injured_area` were stored only), a model extracted fields from the text, the form opened automatically and every row opened the same screen.
- **Decision:** the intake asks only what changes a figure or reaches the model in the same release: training years ("Tiempo entrenando", experience = max with the history), known maintenance ("Calorías de mantenimiento", the expenditure prior until measured data takes over) and the free text. No model extracts fields from the text. The situation, injured area, pre-break bests and cutting calories are dropped from the UI and from `coach_user_intake` (new migration). The regain phase "Recuperar" (returning after a break) is removed entirely and deferred to Phase 3; stored regain plans become "Mantenimiento" with their calories unchanged. The free text reaches `plan_nutrition` and `plan_block` as context, never instructions. Each row opens its own editor and nothing opens on its own.
- **Consequences:** T-198, T-200, T-199; rules R-53, R-54, R-55 in `05` §8; E13 Q46 superseded, Q50/Q51 resolved by the removal; T-190 without regain triggers, T-193 without the regain band, T-194 injury from logged discomforts only (E13 Q52 for the fitness-scientist); `07` regain clean-up by the fitness-scientist; Phase 3 follow-up "Recuperar".
- **Approval:** user, 2026-11-02 (chat: keep the free text and remove the complexity).

### ADR-92 — Equipment split, one cardio machine item and barbell / EZ bar / dumbbell alternatives (T-201; amends ADR-83 and ADR-85)
- **Context:** Checkpoint E13-D re-test (user, 2026-11-02): owning a barbell made "Press Landmine", "Press en banco inclinado con barra Z" and "Remo en T" look doable, and "Peso corporal" offered the TRX, rings and pull-up bar exercises, because the catalogue importer collapsed the bundle's `ez_bar`, `trap_bar` and `landmine` into `barbell` and `pull_up_bar`, `dip_station`, `rings` and `suspension_trainer` into `bodyweight`. The user also expects one "Máquina de cardio" instead of five machine chips. The curated alternatives graph (ADR-83) holds no barbell or EZ-bar exercise, so "Remo con Mancuernas Inclinado" has no barbell alternative.
- **Decision:**
  - `ez_bar` "Barra Z" is the only new equipment item; the EZ-bar exercises move to it. Landmine, T-bar, hexagonal bar, pull-up bar, dip station, rings, TRX, stability ball, `human-flag`, `back-extension` and `band-assisted-pull-ups` are `other` "Otro" (no item for a single exercise or a small implement). `barbell` is the straight bar only; `bodyweight` means floor or wall. No bench or rack requirement; no helper line under "Otro". `behind-the-neck-lat-pulldown` and `v-bar-lat-pulldown` move to `cable`; `ez-bar-reverse-grip-row` and `smith-machine-reverse-grip-bent-over-row` change muscle group to back. Full list: `07` §3 "Equipment items".
  - Saved equipment: `ez_bar` is added only to gym lists or lists holding every old lifting value; an empty gym list stays empty; nobody else gains it. Custom exercises keep their tag.
  - The five cardio machine values (ADR-85) merge into one `cardio_machine` "Máquina de cardio": the 8 machine cardio exercises are retagged and keep their keys, metrics and names; saved lists and stored blocks' `training.equipment` holding any of the five hold `cardio_machine`; place defaults unchanged. The tag only decides which cardio exercises are offered; the cardio default and swap logic is unchanged. The five old enum values stay in the Postgres type unused (an enum value cannot be dropped), guarded by a check.
  - Alternatives: the same exercise (same angle, grip and movement) with a barbell, an EZ bar or dumbbells is an alternative at 1.00 in both directions; flat, incline and decline are separate groups; 34 groups listed in `07` §3 (including `barbell-row` ↔ `bent-over-db-row`, the user's case). Other curated pairs keep their scores. No alternative below 0.90 is offered or stored. A swapped exercise takes its load from its own history, never converted. Evidence and grades in `07` §3 and §10 69-70 (Haugen 2023 *strong* for equal hypertrophy across tools; Marcolin 2018 *weak*; no barbell-vs-dumbbell growth trial found): the 1.00 rule and the floor are design decisions.
- **Consequences:** T-201 (sliced by the planner): migrations (new enum values in their own migration, catalogue retag and muscle fixes, saved-equipment and stored-block migration, redefined SQL functions that list the cardio values, alternatives seed, optional table check [0.90, 1]); `scripts/generate-exercise-catalog.py`; the `coach` Edge Function equipment order; `Equipment` enum, labels, "Mi entrenamiento" chips, strings; the EZ-bar naming fix in the bundle ("Barra EZ" → "Barra Z"); pgTAP, `deno test`, Kotlin and Robolectric tests; `03`, `04`, `05` updated by the planner and implementers; `07` §3, §9, §10 (done with this ADR).
- **Approval:** user, 2026-11-02 (chat, after the fitness-scientist proposal and three rounds of corrections).

### ADR-93 — The model builds the training block; one routine checker validates blocks and reviews folders (T-202; amends ADR-76, ADR-83, ADR-86) · PROPOSED
- **Superseded 2026-11-04 by ADR-99** for block creation: the model builds neither the block nor the split, and the routine checker (H1-H7) no longer validates a model block; the model-input, discomfort-exclusion, H3/H3b and model-change-proposal parts below are void. Kept as history.
- **Context:** T-202 decision checkpoint (user, 2026-11-02/03): blocks always come out the same (4 days ⇒ always "Torso/Pierna" A/B with the same exercises), with no lateral raise, worse than the user's own routine. Audit (HEAD 3e485a2f): the server builds the whole skeleton (`blockRules.ts` split lookup by days, `floor(minutes / 12)` exercises, a fixed group cycle that makes A/B sessions identical, 10 sets per group, fixed rep bands and rests), `candidates.ts` ranks deterministically and caps at 12, and the model only picks one exercise per slot. The model never sees the user's routines, logged history, previous blocks or open discomforts; `save_coach_block` rejects RPE keys; the `movement_pattern` enum lumps lateral raises into `isolation`. Science: `07` §1 "Division of work", §3 "Complete routine", §9 (fitness-scientist, sources §10 71-83).
- **Decision:**
  - **The model builds the whole block:** split, exercises, sets, rep ranges, a target RPE of 7-9 on every working set, rests, and supersets or drop sets only when the routine cannot otherwise fit "Duración de la sesión" (`07` §3 "Routine tools the coach sets"). Planned weights come only from the user's own history. It also writes a reason per split and per exercise and a "what changed versus the last block and why" field. Cardio stays computed by the server (ADR-85) and is appended unchanged. **The minimal phase "Mínimo" and its "Tengo poco tiempo" input are removed** (user, 2026-11-03, E13 Q56; amends ADR-86): no minimal block, phase calories, cardio (100/30) or exceptions; stored "Mínimo" plans become "Mantenimiento" with their calories unchanged; the Postgres enum value stays unused.
  - **Model input** (`07` §3 "Model input"): intention, phase, days, minutes, equipment; the user's routine folders; the last 8 weeks of logged performance per exercise (sets, e1RM trend, RPE); previous blocks with adherence (and "¿Cómo te ha ido?" once T-190 stores it); favourites (ADR-94); unresolved level 4-5 and open discomforts; the free-text block (ADR-87); the eligible catalogue by muscle group and pattern, unranked and uncapped.
  - **One routine checker** (server, deterministic, `04` §9.6): hard checks H1-H7 of `07` §3 (weekly fractional sets 10-20 per major group, at least 2 sessions with 2+ days, side deltoid and knee extension under their conditions, rep ranges and target RPE by intention, safety and fit incl. time, equipment and discomforts, main lifts first for "Ganar fuerza"); soft and free lists are model judgement and never block. A block that fails a hard check gets one retry, then the call fails and nothing is stored.
  - **Folder review (superseded by ADR-97, 2026-11-04: the review is now deterministic, on the device, with no model and no copy; the text below stays as the history and still describes the review behind "a block from a folder"):** the same checker runs on any routine folder of the user from the folder's menu; hard failures show as "Falta" items with a proposed change, the model adds soft comments and proposed changes, the server re-checks every proposed change before it is shown; nothing is blocked; an optional action applies the accepted changes into a **copy** of the folder (the original is never touched).
  - **Taxonomy:** one new `movement_pattern` value `shoulder_abduction` for the 7 lateral-raise keys (`07` §3 "Taxonomy"); it counts as isolation, not a main lift. Hinge exercises must list hamstrings as a secondary muscle (data check) so H1 counts them.
  - **Superseded:** the split lookup by days, the slot count by minutes, the fixed group cycle, 10 sets per group as a fixed figure, the fixed rep bands (main 6-10, isolation 10-15) and the mandatory strength undulation (now an option), "each muscle group at least twice a week" as a hard rule (now soft), the 12-candidate cap and ranking as the model's input (the equipment order stays for server swaps and alternatives).
- **Consequences:** `04` §9 rewritten (§9.1-§9.8, new §9.14); `07` §1, §3, §9 (done by the fitness-scientist); `03` (new pattern value, `save_coach_block` payload, folder reviews); E13 US-72 amended, new US-93 (start a block) and US-95 (folder review); tasks T-205 (checker, taxonomy, model-built block, backend), T-206 (split choice and preview, UI), T-207 (folder review and block from a folder, backend), T-208 (UI); the end-of-block review (E14 backlog, former T-190) will build the next block with this generator; T-168 evaluation criteria follow the checker. Cost: from scratch adds one short split-options call before the block call (`07` §11 row updated by T-205).
- **Approval:** user decisions 2026-11-03 (chat, T-202 checkpoint); the wording waits for the user's OK on the breakdown PR.

### ADR-94 — Block lifecycle: two starting points, a block is a normal folder, "Eliminar bloque", favourite exercises (T-202) · PROPOSED
- **Context:** T-202 (user, 2026-11-03): today a plan with a saved block refuses a new block, so the only way to remove a block is to create a new plan; hand edits to block routines are never read back; the coach has no way to know which exercises the user prefers. The user asked to keep it simple (decision 7).
- **Decision:**
  - **What a block is** stays as today: a 4-week block with its preview and "Guardar bloque" (decision 6). There is no "Tengo poco tiempo" / minimal block (E13 Q56, ADR-93). Block and nutrition plan stay separate requests and steps (decision 3).
  - **Two starting points** (decision 1): "Crear bloque" asks first **"Desde cero"** (the model proposes 2-3 routine types/splits for the user's days and minutes and recommends one with its reason; the user picks one; the model builds it) or **"Desde una carpeta"** (the user picks one of his routine folders; the model reviews it with the checker and proposes changes the user accepts or rejects; the result becomes the block). Both pass the hard checks before the block is stored. A block from a folder is saved as a new block folder; the user's source folder is not touched.
  - **A block is transparent once saved** (decision 4): its routines are normal routines the user edits in Entreno; the plan keeps its links by routine id, so edited routines still count for the plan weeks. The next block can start from that folder (or any other) and so picks up the user's edits.
  - **"Eliminar bloque"** (decisions 1 and 3): removes the block from the plan without creating a new plan; the nutrition plan, its phase and activity targets stay. After removal "Crear bloque" is offered again. Plan weeks, links and deload marks are deleted; the folder and routines stay as normal routines; workouts are untouched; no replace-in-one-step (E13 Q54, user 2026-11-03). Without a block there are no plan weeks, deloads, automatic load changes or end-of-block review; calories keep applying (E13 Q58).
  - **Favourite exercises** (decision 5): a star in the exercise detail marks an exercise (catalogue or custom) as a favourite; favourites show first in the block preview swap list and in the exercise picker; the coach prefers a favourite within its muscle group or pattern **after** the hard checks pass, never adds volume for it and never forces it in; ties go to logged history (`07` §3 "Favourites").
  - **No memory of swapped-out exercises** (`07` §12.8 row 11): the next block does not avoid an exercise the user swapped out; favourites and the user's folders carry his preferences.
- **Consequences:** `04` §9.7, §9.7b, §9.9, new §9.15; `03` (`exercise_favorites`, the block removal RPC); `05` §10 glossary ("Desde cero", "Desde una carpeta", "Eliminar bloque", "Favorito"); E13 US-72 amended, new US-93, US-94 (remove a block), US-96 (favourites); US-92 "My swaps stay" void; tasks T-203 (remove a block), T-204 (favourites), T-206/T-208 (start choice).
- **Approval:** user decisions 2026-11-03 (chat, T-202 checkpoint; E13 Q54 and Q57 answered the same day); the wording waits for the user's OK on the breakdown PR.

### ADR-95 — A routine folder with routines cannot be deleted (T-211; supersedes ADR-51, amends ADR-45 and ADR-69's R-08)
- **Context:** ADR-51 made "delete folder" remove the folder and all its routines after a confirmation dialog (the one R-08 dialog for "many records at once"). The user (chat, 2026-11-03, with T-211) no longer wants a single tap to be able to erase routines through their folder.
- **Decision:**
  - A folder that holds at least one non-archived routine cannot be deleted. Its "Borrar carpeta" item in the folder's ⋮ menu is disabled; tapping it changes nothing (amended 2026-11-03, T-213: no trash icon in the row) and shows an app-level message (R-28, Reject haptic, no action button): «Mueve o elimina las rutinas de la carpeta para poder eliminarla.» The user deletes the routines one by one (swipe or sheet) or moves them with the existing "Mover a carpeta" (routine sheet, one routine at a time).
  - An empty folder is deleted at once, with no dialog (R-20, "Borrar carpeta" in the ⋮ menu).
  - The server enforces it: `delete_routine_folder` raises `23503` when a non-archived routine remains. Archived routines are not listed anywhere, so they never block and are deleted with the folder (otherwise a folder could become undeletable). The UI also checks the loaded list first, so the normal path needs no round trip.
  - Block folders (ADR-94) are normal folders: same rule. "Eliminar bloque" keeps the folder and its routines; deleting the routines later detaches them from the plan weeks as before (`coach_plan_sessions.routine_id` set null).
  - R-08 loses the "delete many records at once" dialog; the allow-list is: delete the account, sign out, discard a live workout, remove a training block, discard unsaved changes.
- **Consequences:** `04` §4.15; `03` (`delete_routine_folder`); `05` R-08 and §10 glossary; E6 US-35 scenarios replaced; code in T-211: new migration, `RoutinesViewModel`/`RoutinesSheets`/strings, guard G5 allow-list without `RoutinesSheets.kt`. Phase 1 text (`phase1/`) stays frozen.
- **Approval:** user decision 2026-11-03 (chat).

### ADR-96 — The coach never stops because of money; spend is recorded and shown (T-214; supersedes the cap of ADR-77 and ADR-90)
- **Context:** ADR-77 and ADR-90 refuse a model call when the month's spend reaches 3 € for the user or 5 € in total (`budget.ts`, `coach_limit_reached`, `coach_no_credit`). The app has one real user, who runs the paid flows to test them. On 2026-11-04, with the month's spend about 1 € on the 3rd (calls that failed for our own reasons, for example the T-213 `max_tokens` truncation), the user ruled that the app must never stop working because it was used a lot ("it's my app, I want it to work well").
- **Decision:**
  - No call is refused because of money: the per-user cap and the total cap are removed, with the refusal check, the codes `coach_limit_reached` and `coach_no_credit` raised by the budget, and the "Reintentar after limit" screens that only exist for them.
  - `coach_usage` keeps recording the real cost of every finished call; "Uso de IA" keeps showing the month's total.
  - When the month's total passes **5 €**, the app shows one notice per month that never blocks and can be dismissed ("Este mes llevas gastados X € en IA"; exact Spanish copy and place decided by ux-reviewer).
  - The Anthropic account's own spending limit is the user's console setting; it is not part of the repo.
  - A failed or truncated call is not retried when a retry cannot help (T-213: no retry after `max_tokens`).
- **Consequences:** `01-scope.md` NFR-02 and §4; `07` §8, §11; `02` and `03` lines about the cap; E13 US-76; code (`budget.ts`, `CoachUsage.kt`, `CoachUsageScreen.kt`, the limit screens and their tests) in T-214.
- **Approval:** user, 2026-11-04 (chat: "agreed this as a new task, also remove the complexity of 3€ per user and 5€ total").

### ADR-97 — The folder review is deterministic and runs on the device (T-216; supersedes the folder-review bullet of ADR-93)
- **Context:** the first folder review that finished (after T-213/T-215) took 2 minutes, returned about 40 per-exercise nit changes, missed the big picture (complete routine, muscles missing, frequency) and the revised routine created by "Aplicar en una copia" crashed the app (user, 2026-11-04, Checkpoint E13-H feedback 4). Everything a big-picture review needs is already on the device (routines, catalogue facts, intention), and every check is a rule with a number, not a judgement.
- **Decision:**
  - "Revisar tus rutinas" in a folder's menu is computed by shared Kotlin code on the device: a score 0-10 and a checklist (one row per H1 group, training days and, for "Ganar fuerza", heavy reps and main lifts first), each unmet row with a short template recommendation. The definition (inputs, counting, checks C1-C4, score weights, cap, findings) is `07` §3 "Folder review" (fitness-scientist), final.
  - No language model, no Edge Function call, no tokens or cost, no network (works offline), no 502 or limit path. No "Aplicar en una copia", no revised routine, no per-exercise comments, no exercise suggestions, nothing tappable to add. Nothing is written or blocked; the user edits the folder and reviews again.
  - Catalogue facts the review reads are corrected once by a data migration (`07` §3 "Catalogue facts the review reads"); a side effect is that the statistics "Series por grupo" shift by design.
  - **Not changed:** "a block from a folder" (ADR-94 "Desde una carpeta", T-207/T-208) keeps its model review, stored review and accept/reject changes until its own task; so the Edge Function `folder_review` call, prompt, schema, validators and `coach_folder_reviews` stay. Only code exclusive to the review screen is removed (apply path, its UI, strings, tags, tests, and the SQL apply functions if no other caller exists).
- **Consequences:** `04` §9.14, `03` (`apply_folder_review_copy` dropped), `07` §3 and §11 (cost row split), `05` §10 glossary; US-95 rewritten; ADR-93 is superseded only in its "Folder review" bullet (its checker for the block stays); ADR-94 and ADR-95 unaffected; ADR-96 unaffected.
- **Approval:** user, 2026-11-04 (chat: deterministic score and checklist, no model).

### ADR-98 — The nutrition plan is deterministic: `plan_nutrition` drops the model (T-217; supersedes the model call of ADR-76 for the nutrition plan)
- **Context:** `plan_nutrition` is the only model call in the nutrition plan. It picks 0-4 outcomes from candidates the server already computes and writes their reasons; it adds no figure and no judgement (`07` §4 "Nutrition plan evaluation", fitness-scientist, 2026-11-04). Calories and macros are already computed by rules, shared with `plan_recompute`, which is model-free.
- **Decision:**
  - `plan_nutrition` becomes `deterministic: true` like `plan_recompute`: no model, no provider, no usage row, no cost. The outcomes, deadlines and reasons are chosen by the rules and Spanish templates in `07` §4 (input contract, output contract, outcome rules). The same input always gives the same plan.
  - Checks N1-N4 (calorie floor, rate inside the phase range with a deficit of at most 500 kcal, protein at least 1.6 g/kg, fat at least 20 %) show on the plan only after the user edits the goals; no score.
  - With BMI under 18.5, "Perder grasa" starts in maintenance. The 750 kcal gain cap stays as a labelled design bound.
  - Plausibility bounds (age 18-100, height 120-230 cm, weight 30-300 kg): outside them a value counts as missing.
  - Not changed: `food-photo`, `plan_block`, `block_splits`, the report and the chat.
- **Consequences:** `07` §4, §9, §10, §11 (cost row), `04` §8, `03` line of the `coach` function; code in T-218.
- **Approval:** user, 2026-11-04 (chat: "agree with the plan, make sure we drop the model and we get the exact outcome by the deterministic flow").
- **Amended T-220 (user, 2026-11-04):** protein is no longer the bold headline macro (the three macros are equal rows); the protein-distribution banner is removed.

### ADR-99 — Block creation is deterministic: a builder with no model call (T-219; supersedes the model-built block of ADR-93 and the `block_splits` / `plan_block` / `folder_review purpose: block` calls of ADR-76) · PROPOSED
- **Context:** the model-built block (ADR-93) costs about $0.15-0.20 per block, adds latency, retries and the failure modes of T-210/T-213/T-215, and its remaining contribution is the pick among rule-valid candidates and prose (`07` §3 "Block builder", fitness-scientist, 2026-11-04; model-written plans rated weak by coaches, Havers 2025a/b). The folder review (ADR-97) and the nutrition plan (ADR-98) are already deterministic.
- **Decision:**
  - No model call in block creation. The user picks a split for the days per week, then a routine type per day (one marked "Recomendado"), then fills the slots of a routine builder; the app computes sets, reps, RPE and rest per section (`07` §3 "Block builder" (a)-(b)). "Guardar bloque" is enabled only at a score of 100 % (every required slot filled; (c)).
  - Discomfort is not an input of the builder; lateral raise (H3) and knee extension (H3b) are no special case. Discomfort keeps acting inside a saved block as in `07` §7.
  - "Desde una carpeta" needs the same 100 % (folder review at 10,0), is fixed on a copy in the builder screen and saves a **new folder**; the original is never modified (`07` §3 (h)).
  - A folder whose lifting-routine count differs from "Días por semana" is **refused** until the two match (Q-B5).
  - Q-B1 (1 day: Cuerpo completo x1) and Q-B2 ("Continuar" disabled when 2 sets per slot do not fit the session length) as written in `07` §3 "Block builder".
  - Superseded: the `block_splits` and `plan_block` model calls, `folder_review purpose: block`, the model input and model change proposals, the routine checker H1-H7 as a validator of a model block, H3/H3b as hard checks, the discomfort exclusion from the proposal (ADR-76 and ADR-93 marked accordingly). Cardio, weeks, deloads, saving and lifecycle (ADR-94) are unchanged.
- **Consequences:** `02` (Coach AI paragraph), `03` (`coach` function line), `04` §9, `05` (builder UI spec pending), `09`, E13/E14 marked; `07` §11 cost rows. **Implemented:** the builder in T-221 to T-223; the server removal in T-224 (kinds `block_splits`, `plan_block`, `folder_review` and their code deleted; migration `20261231100000_drop_model_block.sql` drops `coach_block_model_input`, `save_folder_review`, `coach_folder_for_review` and `coach_folder_reviews`; `coach_assert_block`, `coach_block_rep_ok` and `unresolved_severe_discomforts` stay; the error `coach_block_needs_changes` is no longer emitted).
- **Approval:** proposed; user decision 2026-11-04 (chat: deterministic builder, split and type per day, 100 % gate, "Desde una carpeta" at the same bar, Q-B5 refused until matching). Becomes final when the PM records the ADR PR as approved.
