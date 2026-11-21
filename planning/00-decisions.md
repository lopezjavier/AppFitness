# Decisions (ADR index)

Live index of every closed decision, one line per ADR, with the numbers kept since Phase 1. Full text: Phase 1 (ADR-01..72) in [`phase1/00-decisions.md`](phase1/00-decisions.md) and Phase 2 (ADR-73..100) in [`phase2/00-decisions.md`](phase2/00-decisions.md), both frozen. A decision is only changed by a new ADR that supersedes it (mark the old row "superseded by ADR-N").

## New ADRs go here

Phase 3 ADRs continue the numbering (next: **ADR-111**) and are written **in full** in this file, below the index, with their one-line row in the index. Format: context -> decision -> consequences. A new decision also updates the affected live docs.

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
| 73 | In-app reminders computed on open (weight 2 days, circumferences 14 days), no time window; weekly check-in returns; soreness not collected | Amends 66; check-in removed by 110 |
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
| 87 | The user's free text reaches the model verbatim, capped, visible and excludable line by line; the model only describes features and data that exist | Amends 76 and `07` §8; free text removed by 107 |
| 88 | One behaviour "Entrenador": inside an accepted block, load progression, calorie changes in the safe band and deloads apply on their own with "Deshacer"/cancel; phases and new blocks need acceptance | Amends 76 |
| 89 | No generic health disclaimers; one data-triggered note on a persisting level 4-5 discomfort; train around an injured area with a graded return the user paces | Amends 81 and 85; reverses E13 Q29 |
| 90 | The per-user monthly AI cap rises from 2 € to 3 €; the 5 € global total stays | Superseded by ADR-103 |
| 91 | Minimal intake (years, maintenance, free text) and no "Recuperar" (deferred to Phase 3); no field extraction | Amends 86, 87; free text removed by 107 |
| 92 | Equipment split: "Barra Z" is the only new item, every other implement is "Otro"; one "Máquina de cardio"; same barbell / EZ bar / dumbbell exercise is a 1.00 alternative both ways; no alternative below 0.90 | Amends 83, 85 |
| 93 | The model builds the training block; one routine checker validates blocks and reviews folders | Superseded in part by 97 and 99; heading still says PROPOSED |
| 94 | Block lifecycle: two starting points, a block is a normal folder, "Eliminar bloque", favourite exercises | PROPOSED (as in its heading) |
| 95 | A routine folder with routines cannot be deleted: the user moves or deletes the routines first; no dialog | Supersedes 51; amends 45, 69 (R-08) |
| 96 | The coach never refuses a call because of money: no per-user or total cap; `coach_usage` records the spend and one non-blocking notice shows when the month passes 5 € | Supersedes 77's cap and 90 |
| 97 | The folder review is deterministic and runs on the device: a 0-10 score and a checklist, no model call, no "Aplicar en una copia"; "a block from a folder" keeps its model review | Supersedes 93's folder-review bullet; 94 and 95 unaffected; amended by 107 |
| 98 | The nutrition plan is deterministic: `plan_nutrition` drops the model call | Supersedes the model call of 76 for the nutrition plan; amended by 107 |
| 99 | Block creation is deterministic: a builder with no model call (split and type per day, 100 % gate) | Supersedes the model-built block of 93 and the block calls of 76; PROPOSED (as in its heading); amended by 107 |
| 100 | Product definition is a user-invoked `product-owner` skill; `epic-planner` no longer writes stories | — |

## Index (Phase 3, ADR-101..)

| ADR | Decision (one line) | Status |
|---|---|---|
| 101 | Phase 3 is the last phase: personal-use scope (one person plus an optional test account), items left out are out of scope for the app, Open Food Facts becomes a fallback that copies into editable personal foods, then maintenance mode | Amends 53, 90 |
| 103 | Phase 3 epics re-split: E18 functional improvements, E19 UI review, E20 Closure; Anthropic removed (restores "100 % free"), reports stored, a Gemini exercise chat. E19 screen gallery (T-279, US-131): one Roborazzi image per screen state rendered on the host, a review gallery and not goldens (never compared, outside `src/androidHostTest/snapshots/`) | Supersedes 77, 90 (cost caps); amends 101 |
| 102 | Food energy check (E17): optional `alcohol_g_100` in grams per 100 base units, per-food "saved anyway" flag cleared by a trigger when an input changes, one SQL check function and an owner-scoped audit RPC (rule in `07` section 4) | — |
| 104 | Food energy check becomes a hard block (E17 checkpoint feedback): a food whose kcal and macros disagree cannot be saved by any path (manual, edit, AI photo, OFF copy); the AI estimate must be consistent or the app shows the issue with a retry that feeds the failed estimate back; the "saved anyway" flag, "Guardar igualmente" and the food audit are removed | Supersedes parts of 102 |
| 105 | E18 gains a meal from typed text (US-132, Gemini free tier, editable draft); the monthly cleanup of unused foods (US-133) was dropped on 2026-11-10 (T-273); diary snapshots stay | Amends 103 |
| 106 | Extend the block by date (a pencil on the week in progress, within the 14-day limit); alcohol by volume in the food form (E18 US-134, US-135) | Amends 101 and Q-E17-1 |
| 107 | Free-text notes and unused code removed in every layer (E19 US-123): the coach reads only structured data; one migration drops the free-text storage and unused SQL objects; rule R-63 | Amends 87 and 91 (free text), 97, 98, 99 and the US-81 and US-89 decisions |
| 110 | Weekly check-in removed in every layer (it never showed without a manual weigh-in); the Trainer's weekly report becomes progress-first (`07` §3) and every stored weekly report is rebuilt to snapshot v2 with its decisions frozen and the original kept; the latest finished week refreshes until the next week ends (Q17) (E19 US-141, US-142) | Amends 73 (check-in) and `04` §6.11 |

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
- **Amendment 2026-11-15 (user, chat):** the Closure epic is renumbered E21; the new E20 is Illness (`epics/E20-illness.md`, draft). References to "E20 Closure" in the ADRs above mean E21.

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

### ADR-106 — Extend the block by date; alcohol by volume in the food form (E18 US-134, US-135; amends ADR-101 and Q-E17-1)
- **Context:** an illness in the last week of a block forces two sessions on consecutive days or a missed session. A pause with its own state was proposed and rejected as too complex. Typing alcohol grams means doing a formula by hand.
- **Decision:** a pencil button next to the block end day in the Trainer opens a date picker for the new last day of the block (the day counts); the block, its weeks, the review and the next block move by the same number of days; nothing else changes (loads, deloads and calories are out of scope). The Alcohol field moves to a collapsible section below "Minerales" and accepts grams or % vol for foods measured in ml (converted with the factor already in `07` §4); the AI estimate still returns grams. The example of "Describir comida" becomes "pechuga de pollo con patatas fritas".
- **Consequences:** E15's "illness" non-goal is relaxed for this action only; `04` §9.9/§9.15 and `07` §4 are updated by the tasks. Open for the planner: a maximum extension (the user gave none).
- **Approval:** user, 2026-11-10 (chat, product-owner).
- **Amendment 2026-11-11 (user, Checkpoint E18-C):** the pencil sits on the week in progress (the "Actual" card of "Semanas del bloque"), not next to a block end line, and the picker picks that week's new last day. The rule is unchanged: the N days added to the week that holds today move the later weeks, the review and the block-end notice by the same N, within the 14-day total limit of the block. The "El bloque termina el …" line is shown again only once a week was paused.

### ADR-107 — Free-text notes and unused code removed; the coach reads only structured data (E19 US-123; amends ADR-87 and ADR-91 for the free text, ADR-97, ADR-98, ADR-99 and the US-81 and US-89 decisions)
- **Context:** every `coach` kind is model-free (ADR-97..99, ADR-103), so no code reads a user's free text. The "Enviar al entrenador" switches of ADR-87 and the "Lo que el entrenador sabe de ti" screen controlled nothing, and the copy said the coach used them. The E19 audit of the whole codebase also found dead code in every layer. At the E19 breakdown (2026-11-11) the user asked for the free-text notes to be removed in every layer and for every unused item to go, with no "keep for later".
- **Decision (user, 2026-11-11, T-277 audit decisions, chat):**
  - **Free text is removed everywhere:** the "Lo que el entrenador sabe de ti" screen, its Trainer row and the per-line switches; the free-text field of "Para conocerte"; the check-in note field; the free text in `coach_summary`, `coach_plan_inputs` and `save_coach_intake`. A `free_text` key sent by an older app is ignored by `save_coach_intake`: not validated, not stored, not returned.
  - **Backend, one migration (`20270127100000_remove_unused.sql`, T-286), one transaction with a row-count self-check:** dropped table `coach_free_text_exclusions`; columns `coach_user_intake.free_text` and `weekly_check_ins.notes`, with their length checks; functions `coach_free_text_lines`, `set_free_text_line_excluded`, `save_meal_slots`, `delete_meal_slot`, `coach_plan_week_of_date`, `assert_unique_meal_labels`, `convert_used_off_foods`, `coach_rederive_plan_links`, `migrate_equipment_list`, `off_alcohol_g` and `coach_plan_calendar`. Before each drop, a guard checks that no surviving public function still refers to it. `coach_summary`, `coach_plan_inputs` and `save_coach_intake` are recreated without free text, and the `free_text` key is removed from every `coach_recommendations.input_summary`.
  - **Production data:** the removed `weekly_check_ins.notes` and `coach_user_intake.free_text` values were deleted by that migration on production. It reports the counts with `raise notice`; the exact figures are not stored in the repository (the `deploy-supabase` CI log does not show notices), so the record is the migration notice of the production run, see the T-286 PR (#650).
  - **Kept, with the reason:** `exercise_discomforts.note` (the user wants discomfort notes tracked across sessions; it is shown only in its own form, a later task may use it); `coach_plan_calendar_for` (the per-user calendar every coach reader uses); `copy_off_food_core` (called by the live `copy_off_food`); the `coach_recommendations.input_summary` column; all 8 Edge Functions (`health`, `food-barcode`, `account-status`, `delete-account`, `food-photo`, `food-search`, `coach`, `exercise-chat`).
  - **App (T-277, PR #648):** the free-text screen, route and ViewModel; the "Para conocerte" screen, route and ViewModel (years training and maintenance kcal are still edited in "Tus datos" through the row-editor sheets); the Trainer row; the check-in note field; the coach-limit message part used only by the free-text screen; dead use cases, DTO fields and repository methods; 26 string resources and one drawable that nothing referenced. The navigation state version moves from 1 to 2, so a saved stack with removed routes is discarded (ADR-40).
  - **Resource guard (T-277):** `scripts/check-unused-resources.py` (stdlib, with `scripts/tests/test_check_unused_resources.py`) fails on a string, plural, array or drawable that no Kotlin source uses; it runs in `android-ci.yml`. The Python tests `scripts/test_repdb_tools.py` and `scripts/tests/test_generate_band_long.py` run in `security.yml`; one drifted test of `test_repdb_tools.py` that compared against an immutable migration was deleted.
- **Consequences:**
  - Rule R-63 in `05` §8: no copy claims that the coach reads or uses something it does not.
  - Old app v1.9.0: once the migration is deployed its Check-in screen fails (it selects and writes `notes`), and its free-text screen and switches fail too. The mitigation used: the user installed the T-277 APK before the migration was merged, and `deploy-supabase` ran green right after (run 43108523640 on `main`). The data removal is irreversible; the counts above are the proof.
  - No "keep for later": an unused item is removed by the task that finds it; a later need adds code with its own task and story.
  - `07` §8 ("words never change numbers", ADR-87) stays true: free text never changed a figure and is no longer sent at all.
  - Live docs updated by T-287: `02`, `03`, `04` (§6.5, §7.5, §7.8, §7.9), `05`, `07`, `10-feature-inventory.md`, the `README`, E4 and E10, and the stories US-81 and US-89 in E13 (marked "Amended by ADR-107").
- **Approval:** user, 2026-11-11 (chat: the T-277 audit decisions and the split into T-277, T-286 and T-287).

### ADR-108 — Preferencias no longer edits calories or macros; the Trainer plan owns them (E19 US-138; amends ADR-24 and `04` §1.4)
- **Context:** every Preferencias section save recomputed `target_kcal` client-side (Mifflin × profile activity + stored adjustment) and upserted `nutrition_goals` through `save_preferences`, replacing the target the Trainer plan had accepted and flagging the plan `edited_by_user`. The plan's expenditure comes from `coach_compute` and its adjustment is rate-based, so the two numbers disagreed (the user saw it in the Diary and in the hub).
- **Decision (user, 2026-11-17 D1, chat; implementation approach approved 2026-11-17):** "Objetivo calórico" and "Macros" are removed from Preferencias; the Trainer plan owns calories and macros and Preferencias never writes `nutrition_goals`. Onboarding keeps its first write (it uses `CompleteOnboarding` and `NutritionGoalsRepository`, never `save_preferences`).
  - **Backend, migration `20270128100000_save_preferences_goals_optional.sql` (T-303):** same 24-parameter signature, the six goal parameters `default null`; the `nutrition_goals` upsert runs only when all six are non-null, a partial set raises 22023 before any write. Old app versions that still send the goals keep working.
  - **App:** `SavePreferences`, `ProfileRepository` and `SavePreferencesParams` carry no goals; `PreferencesViewModel`, the hub and the section screen have no Energy or Macros; old saved drafts and the `energy`/`macros` section keys fall back to Datos personales.
- **Consequences:** a user without an accepted Trainer plan can no longer change calories or macros after onboarding. Calories and macros are read in the Trainer's "Tu plan". The migration is deployed to production with the merge; `deploy-supabase` must be green on `main` with the data intact before the next task.
- **Approval:** user, 2026-11-17 (D1) and 2026-11-18 (approach and `05` R-124).

### ADR-109 — The trainer owns the measured activity level; the lifecycle of the provisional values (E19 US-140; amends `04` §7.1, §7.2, §7.6, §8.5 and ADR-86 for the chip)
- **Context:** the level enters the calories only on the formula path (BMR × factor), yet a provisional level blocked "Aceptar" whatever the expenditure source, and the user could edit a level the app already measures from steps. Evidence and rules: `07` §2 "Who owns the activity level…".
- **Decision (user, 2026-11-17 D3, chat; lifecycle rules decided by fitness-scientist 2026-11-18):**
  - **Rule A (Q-A):** with 7 or more days of steps (else of active kcal) in the last 28 days the level is **measured**: read-only, no chip, no sheet, `provisional = false`, any stored confirmed or edited level ignored while the data exists. Without such data it is the user's answer: editable and provisional as built.
  - **Q-C:** the level blocks "Aceptar" only on the formula path (expenditure source `formula`, or no expenditure value). With a stated maintenance (`intake`) or an adaptive expenditure it keeps its chip if still an answer, but is not a blocker and is not listed in "Valores por confirmar".
  - **Rule B:** a data-backed value is not provisional (chip off and no block, one flag): maintenance when `slope_used = true` (the chip follows `07` §2's weight-trend rule, not the reliable diary alone; the expenditure figure itself is computed as in `04` §7.2); Experiencia and Tiempo entrenando together when the history class reaches the self-reported class; a diary-only adaptive value stays provisional.
  - **Q-B: the measured level does not write `profiles.activity_level`.** After T-303 no Diary or Preferencias screen computes a target from it; `profiles.activity_level` stays the user's own answer (onboarding, Preferencias "Datos personales", fallback when no data). `04` §7 keeps "nothing here writes `profiles`".
- **Consequences:** one migration (`coach_compute`, `coach_unconfirmed_keys`, `coach_plan_inputs`), the Edge `unconfirmedKeys` mirror and the Trainer screens (Tus datos, Plan, Antes de empezar). The Preferencias picker keeps editing the answer and gains a "?" explaining it. The migration deploys with the merge; `deploy-supabase` must be green on `main` before the next checkpoint.
- **Approval:** user, 2026-11-17 (D3); the consequence that Rule B also stops blocking is flagged to the user in the T-308 plan.

### ADR-110 — Weekly check-in removed; the weekly report is progress-first and stored reports are rebuilt to v2 (E19 US-141, US-142; amends ADR-73 for the check-in and `04` §6.11)
- **Context:** in a month of use the "Check-in semanal" never appeared, while the Trainer's weekly summary did; the user found both weekly summaries of little value and then rejected a compliance report ("we should show progress, not empty data", 2026-11-21). **Why the check-in never showed (code read, T-310 plan):** it is listed only in "Centro de avisos", with no Inicio or Trainer entry. `LoadReminders` builds its window with `ResolveCheckInDay(today, weekday, firstDay)`, where `firstDay` is the date of the earliest `source = 'manual'` row of `body_measurements`. With no manual weigh-in (weights only from Health Connect, or the onboarding row deleted from Peso) `firstDay` is null and the window never opens; with a window, an unanswered check-in is listed every day. So "never in a month" fits only no manual weigh-in (most likely), the `weekly_check_in` kind silenced, or the bell never opened. The circumference reminder falls back to the same `firstDay` when no circumference exists, so it was affected too.
- **Decision (user, 2026-11-21, chat: the `07` §3 design accepted in full, then the T-310 split, scope and production drops approved):**
  - **Check-in removed in every layer (amends ADR-73):** screen, route, reminder kind, the Preferencias switch and "Día del check-in", strings, tests, gallery and docs (T-311, app); production drops (T-312, approved by name, irreversible): table `weekly_check_ins` with its trigger and policies, column `profiles.check_in_weekday` with its constraint, function `weekly_records(date, date)`, the `weekly_check_in` rows of `notification_settings` and the enum label (`notification_kind` recreated with `weight, circumference, week_summary, block_end`), and the key `check_in_energy` from `coach_summary` and every stored `coach_recommendations.input_summary`. The old app reads `check_in_weekday`, so the T-311 APK is installed before T-312 merges.
  - **Circumference anchor (PM decision within `04` §6.2):** with no circumference yet, the 14-day reminder counts from the earliest weigh-in of any source, not the earliest manual one.
  - **The weekly report is progress-first, per `07` §3 "Weekly report: progress first":** a coach card (one sentence, one action), Strength per muscle group, Main lifts, Records, Weight, Waist, nutrition only through the diagnosis and the proposal, cards ordered by the training intention; adherence counts are at most a context line or a gate. The persistent-discomfort health note shows under the paused exercise row on the live report only.
  - **Stored weekly reports rebuilt to schema v2 (amends `04` §6.11, which kept snapshots as built):** new reports carry `schema_version: 2` and a `progress` object built by an internal, owner-filtered builder as of the week's end (T-313). One idempotent migration (T-314, production data, the user's explicit OK on the file before merge) copies each stored v1 snapshot into `body_v1`, rebuilds `progress` for every stored v1 normal week (final, except the active plan's latest finished week) and sets `schema_version: 2`; the frozen keys (coach changes, calorie decisions and proposals, deload, stalls, nutrition diagnosis, paused flag: every v1 key) stay exactly as stored, checked in the migration. Only stored weeks are rebuilt; weeks never stored stay missing. A deleted input hides its line and is listed as missing.
  - **Late data (Q17):** the latest finished week's `progress` refreshes (throttled to 10 minutes) until the next week ends, then it is final.
- **Consequences:** `03` (dropped objects, `coach_week_reports.body_v1`, snapshot v2), `04` §6 (title, §6.3-§6.6 removed, §6.7 and §6.11 v2 and refresh, §7.8 no check-in energy), `05` (R-74 icon list, new report components, glossary rows), `10-feature-inventory.md` row 108 and E10 US-59 ("Removed by ADR-110") are updated by T-312 and T-315; `07` §1/§7 check-in mentions by `fitness-scientist`. The v1 keys stay in v2, so the Trainer root "Tu plan" Calorías tile and an app that ignores `progress` keep working. E19 gains **👤 Checkpoint E19-R** (weekly report), which the closure T-285 waits for. The same audit of the other summaries ("Revisión del bloque", "Informes", the diary history, Estadísticas) follows the user's approval as new tasks.
- **Approval:** user, 2026-11-21 (chat: report design, split, scope and the 5 production drops of T-312); T-314's migration file needs the user's explicit OK before merge.
