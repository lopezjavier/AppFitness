# Decisions (ADR index)

Live index of every closed decision, one line per ADR, with the numbers kept since Phase 1. Full text: Phase 1 (ADR-01..72) in [`phase1/00-decisions.md`](phase1/00-decisions.md) and Phase 2 (ADR-73..100) in [`phase2/00-decisions.md`](phase2/00-decisions.md), both frozen. A decision is only changed by a new ADR that supersedes it (mark the old row "superseded by ADR-N").

## New ADRs go here

Phase 3 ADRs continue the numbering (next: **ADR-106**) and are written **in full** in this file, below the index, with their one-line row in the index. Format: context -> decision -> consequences. A new decision also updates the affected live docs.

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

## Index (Phase 2, ADR-73..100)

The rows of ADR-93, 94, 98 and 99 are added here: the Phase 2 archive index lacked them; their full text is in `phase2/00-decisions.md`.

| ADR | Decision (one line) | Status |
|---|---|---|
| 73 | In-app reminders computed on open (weight 2 days, circumferences 14 days), no time window; weekly check-in returns; soreness not collected | Amends 66 |
| 74 | Health Connect: automatic sync on the first open of the day plus the manual button; reads sleep, steps, calories, resting heart rate | Amends 08 |
| 75 | Rest-end alert with the screen off (the only notification the app posts); alert sound reopened | Amends E3 US-13 |
| 76 | Evidence-based coach: deterministic calculations, the model explains, proposals need acceptance, derived values confirmed, coach sets outcomes, RIR derived from RPE | Supersedes the assistant part of 09; amended by 86, 87, 88 |
| 77 | Claude through a `coach` Edge Function (Opus 5.5 plan, Sonnet 5.5 report and chat); paid AI capped at 2 € per user and 5 € in total per month; NFR-02 changed | Superseded by ADR-103 (Anthropic removed) |
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
| 90 | The per-user monthly AI cap rises from 2 € to 3 €; the 5 € global total stays | Superseded by ADR-103 |
| 91 | Minimal intake (years, maintenance, free text) and no "Recuperar" (deferred to Phase 3); no field extraction | Amends 86, 87 |
| 92 | Equipment split: "Barra Z" is the only new item, every other implement is "Otro"; one "Máquina de cardio"; same barbell / EZ bar / dumbbell exercise is a 1.00 alternative both ways; no alternative below 0.90 | Amends 83, 85 |
| 93 | The model builds the training block; one routine checker validates blocks and reviews folders | Superseded in part by 97 and 99; heading still says PROPOSED |
| 94 | Block lifecycle: two starting points, a block is a normal folder, "Eliminar bloque", favourite exercises | PROPOSED (as in its heading) |
| 95 | A routine folder with routines cannot be deleted: the user moves or deletes the routines first; no dialog | Supersedes 51; amends 45, 69 (R-08) |
| 96 | The coach never refuses a call because of money: no per-user or total cap; `coach_usage` records the spend and one non-blocking notice shows when the month passes 5 € | Supersedes 77's cap and 90 |
| 97 | The folder review is deterministic and runs on the device: a 0-10 score and a checklist, no model call, no "Aplicar en una copia"; "a block from a folder" keeps its model review | Supersedes 93's folder-review bullet; 94 and 95 unaffected |
| 98 | The nutrition plan is deterministic: `plan_nutrition` drops the model call | Supersedes the model call of 76 for the nutrition plan |
| 99 | Block creation is deterministic: a builder with no model call (split and type per day, 100 % gate) | Supersedes the model-built block of 93 and the block calls of 76; PROPOSED (as in its heading) |
| 100 | Product definition is a user-invoked `product-owner` skill; `epic-planner` no longer writes stories | — |

## Index (Phase 3, ADR-101..)

| ADR | Decision (one line) | Status |
|---|---|---|
| 101 | Phase 3 is the last phase: personal-use scope (one person plus an optional test account), items left out are out of scope for the app, Open Food Facts becomes a fallback that copies into editable personal foods, then maintenance mode | Amends 53, 90 |
| 103 | Phase 3 epics re-split: E18 functional improvements, E19 UI review, E20 Closure; Anthropic removed (restores "100 % free"), reports stored, a Gemini exercise chat | Supersedes 77, 90 (cost caps); amends 101 |
| 102 | Food energy check (E17): optional `alcohol_g_100` in grams per 100 base units, per-food "saved anyway" flag cleared by a trigger when an input changes, one SQL check function and an owner-scoped audit RPC (rule in `07` section 4) | — |
| 104 | Food energy check becomes a hard block (E17 checkpoint feedback): a food whose kcal and macros disagree cannot be saved by any path (manual, edit, AI photo, OFF copy); the AI estimate must be consistent or the app shows the issue with a retry that feeds the failed estimate back; the "saved anyway" flag, "Guardar igualmente" and the food audit are removed | Supersedes parts of 102 |
| 105 | E18 gains a meal from typed text (US-132, Gemini free tier, editable draft); the monthly cleanup of unused foods (US-133) was dropped on 2026-11-10 (T-273); diary snapshots stay | Amends 103 |

## Phase 3 ADRs (full text)

### ADR-101 — Phase 3 is the last phase; personal-use scope; OFF as a fallback that copies into personal foods (amends ADR-53 and the user count of ADR-90)
- **Context:** the app is close to finished. The user wants a closing phase with real-impact work only, and a clear end state.
- **Decision:**
  - Phase 3 is the last phase. Epics E15 Real life and goal switching, E16 Nutrition follow-up, E17 Food data quality, E18 UI review, E19 Closure, in that order (`01-scope.md`; re-split by ADR-103 into E18 Functional improvements, E19 UI review, E20 Closure). After E20 the app is in maintenance mode: only bugfixes and user feedback create tasks.
  - Scope is personal use: one person, with a second account allowed for testing. This replaces "1-5 users" (ADR-90 text stays as history).
  - Everything listed under "Out of scope for the app" in `01-scope.md` is final, not deferred; this includes the former "Phase 3" items (water, voice, "Recuperar").
  - Open Food Facts is a fallback for finding foods. Adding an OFF food creates the user's personal copy (no duplicate per barcode or OFF id), found first in later searches and editable like a manual food. This amends ADR-53 (only manual foods editable). Existing OFF foods in the diary are converted to personal copies in E17.
  - Macros are checked against kcal deterministically when a food is created, edited or estimated by AI (tolerance owned by `fitness-scientist` in `07`); existing foods are audited and fixed by the user from a list.
  - Closure (E20): Supabase keep-alive, secured data export of the user's own rows, repository made private, project retro.
- **Consequences:** `01-scope.md` rewritten, `CLAUDE.md` line 3, "Phase 3" references in `05`, `07`, E10-E13 and `tasks/todo.md` now say out of scope for the app. Epic files E15-E19 written 2026-11-08; no Android system notifications (ADR-73 stays), a block-end alert is added to "Centro de avisos".
- **Approval:** user, 2026-11-08 (chat).

### ADR-102 — Food energy check: alcohol field, "saved anyway" flag, SQL check function and audit RPC
- **Context:** E17 (US-116, US-117) warns when a food's declared kcal and its macros disagree and lists the foods that fail. The rule, factors, thresholds and sources are owned by `fitness-scientist` in `07-coach-design.md` section 4 "Food energy check".
- **Decision:**
  - `foods.alcohol_g_100 numeric(5,1)` (null, 0-100): grams of alcohol per 100 base units, never % vol; allowed on `user` and `off` rows. The OFF import and a backfill of cached OFF rows store `% vol x 0.789`. The `food-photo` estimate returns `nutrition.alcohol_g` (always a number, `0` when none) and a response without it is rejected as `422 NO_FOOD_RECOGNIZED`.
  - `foods.energy_check_saved_anyway boolean not null default false`: the user kept a failing food as entered. Trigger `foods_energy_flag` clears it whenever kcal, protein, carbs, fat, alcohol, fibre or polyols change in a write that does not change the flag itself. `create_personal_food` and `update_personal_food` take `p_alcohol` and `p_saved_anyway` and store the flag only on a food that fails the check. On update, a null `p_alcohol` clears the column (the form sends the full state).
  - `public.food_energy_check(kcal, protein, carbs, fat, fibre, polyols, alcohol)` is the single SQL implementation (immutable, invoker); `public.food_energy_audit()` lists the caller's own failing, unflagged foods, `clearly_wrong` first. The audit writes nothing.
- **Consequences:** `foods_searchable` is unchanged (alcohol is not in search results; the personal-copy function of T-254/T-255 reads `foods`). The Kotlin check (T-252) must give the same result as the SQL function. Redeploy `food-photo`, `food-search` and `food-barcode` with the migration.
- **Approval:** user decisions Q-E17-1 and Q-E17-2, 2026-11-09 (chat).

### ADR-103 — Phase 3 epics re-split; Anthropic removed; reports stored; Gemini exercise chat (supersedes the cost caps of ADR-77 and ADR-90; amends ADR-101)
- **Context:** the coach is deterministic (ADR-97..99) and `report` and `chat` were never built, so the Anthropic provider has no remaining use, yet its key, cost caps and "Uso de IA" screen remain. The user also wants past reports kept, an exercise chat, and a UI review that no longer needs an APK install each time.
- **Decision:**
  - Epics: E18 Functional improvements (US-128..130 plus US-120..122), E19 UI review (US-131, US-123), E20 Closure (the former E19). The UI review runs last-but-one so it covers the screens E18 adds.
  - Anthropic is removed: client, `ANTHROPIC_API_KEY` secret, prices, budget code, `coach_usage` data, model selector and "Uso de IA". NFR-02 returns to "100 % free"; the 3 € / 5 € caps (ADR-77, ADR-90) are superseded. The drop of `coach_usage` is a production migration (`deploy-supabase` green before the next checkpoint).
  - Reports (the weekly summary and the block summary) are stored as snapshots, kept without a limit, and listed in "Informes" from Centro de avisos. Reminders stay computed and are never stored (`04` §6.1 unchanged). No comparison view and no monthly report (ADR-66).
  - The exercise chat (US-130) is the only chat: Gemini through an Edge Function, context limited to the exercise and the question, no actions, no history, no disclaimer, a plain message when there is no answer. Chats about the app or about the user's data are not built. The Gemini terms check was resolved on 2026-11-09: free tier, same `GEMINI_API_KEY` as `food-photo` (ADR-59).
  - E19 starts with a spike on how to see UI without installing the APK (candidate: screenshots from the Robolectric tests; the emulator as fallback) and records its choice.
- **Consequences:** `01-scope.md`, E18, E19, E20 (renumbered), `09-open-questions.md` and `tasks/todo.md` references; `phase2/01-scope.md` NFR-02 and the cost rows of `07` §11 are updated by the US-128 task. T-261: weekly summaries are stored as snapshots (`coach_week_reports`) and block reviews are listed live; `04` §6.9 is amended (opening the block-end notice marks the review seen, `seen_at`, and clears the notice; the review stays in "Informes") and `04` §6.11 describes the reports.
- **Approval:** user, 2026-11-09 (chat).

Older ADRs: [`phase2/00-decisions.md`](phase2/00-decisions.md) (73-100) and [`phase1/00-decisions.md`](phase1/00-decisions.md) (01-72).

### ADR-104 — Food energy check is a hard block; AI estimates are validated and retried; no "saved anyway", no audit UI (supersedes parts of ADR-102)
- **Context:** at Checkpoint E17 (2026-11-09) the user found about 10 AI-estimated foods in their account whose kcal and macros disagree, the audit screen is a chore they do not want, and a warning that can be ignored lets wrong foods in. The root cause is the `food-photo` prompt, which never required the estimate to be self-consistent.
- **Decision (user, 2026-11-09):**
  - **No incorrect food, by any path.** A food that fails the energy check (`mismatch` level of `07` section 4, unless `fitness-scientist` justifies another level in T-257) cannot be created or edited into that state: manual create, edit, AI dish estimate, label scan and the OFF copy. The form shows the issue and blocks "Guardar"; there is no "Guardar igualmente".
  - **AI estimates.** The `food-photo` prompts state that kcal must agree with the macros, fibre, polyols and alcohol (the `07` formula) and that an inconsistent estimate is not allowed. The Edge Function runs the check on the result; when it still fails, the app shows the warning with the issue (declared kcal, macro kcal, gap) and a retry button with an icon. Retry re-calls the model with the previous estimate and the reason it failed appended to the prompt.
  - **Removed:** `foods.energy_check_saved_anyway`, trigger `foods_energy_flag`, the `p_saved_anyway` parameters, `food_energy_audit()` and the audit UI (route, "Revisar alimentos" hub row, screen, strings, tests). `food_energy_check` stays and becomes the enforcement function (`create_personal_food`, `update_personal_food`, `copy_off_food` reject a failing food).
  - **Existing wrong foods** are fixed by the PM through a backend-developer task (one-off, nothing personal committed); foods whose true values cannot be derived are asked about to the user.
  - **Open Food Facts product that fails the check (PM recommendation, recorded in the PR):** it is not added as is; the user corrects it first, and the copy is created only with consistent values.
- **Consequences:** a CHECK constraint is not used, so existing rows and diary snapshots stay readable; the rule is enforced on write. Redeploy `food-photo`, `food-search` and `food-barcode` with the migration. Docs `03`, `04` section 2, `05` section 8 and 10, `07` section 4 and the E17 epic are updated in T-257/T-258.
- **Approval:** user, 2026-11-09 (chat, Checkpoint E17 feedback).

### ADR-105 — Meal from typed text (E18 US-132; amends ADR-103). The monthly cleanup (US-133) was dropped by T-273
- **Context:** logging a meal when out of the house is slow, and AI-estimated foods are used once and accumulate. Diary entries already store snapshots of kcal, macros and name (ADR-53, `03`).
- **Decision (user, 2026-11-10, chat):**
  - A typed sentence is sent to the Gemini free tier (same function family as `food-photo`, ADR-59) and returns one combined food as an editable draft (name lists everything, total kcal and macros, one portion is the whole meal). It is saved as a personal food and added to the diary. The ADR-104 energy check applies; the text is neither stored nor logged. It reuses the `food-photo` dish flow (draft, energy check, retry) with the typed sentence as input and no image; Gemini decides the portions, with no fixed defaults and no `fitness-scientist` task. In "Añadir alimento" the photo icon becomes an icon-only AI icon that opens the existing sheet, which gains "Describir comida".
  - **SUPERSEDED (user, 2026-11-10, T-273):** the scheduled cleanup of personal foods unused for 30 days (and its pg_cron job) is dropped; the storage saved is negligible (about 100 KB a year for 100 foods). The first run at deploy (T-272) already removed the foods then unused; the migration `20270125100200` removes the job and function. What stays: diary entries keep their snapshot, `food_log_entries.food_id` is nullable (`on delete set null`), and an entry whose food is gone opens read-only (view or delete).
- **Consequences:** `food_log_entries.food_id` must survive a food delete (diary days and totals unchanged, entry details open from the snapshot); `03` and `08` are updated by the tasks. Accepted: the foods removed by the single T-272 run are not restored.
- **Approval:** user, 2026-11-10 (chat, product-owner).
