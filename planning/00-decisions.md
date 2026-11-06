# Decisions (ADR index)

Live index of every closed decision, one line per ADR, with the numbers kept since Phase 1. Full text: Phase 1 (ADR-01..72) in [`phase1/00-decisions.md`](phase1/00-decisions.md) and Phase 2 (ADR-73..100) in [`phase2/00-decisions.md`](phase2/00-decisions.md), both frozen. A decision is only changed by a new ADR that supersedes it (mark the old row "superseded by ADR-N").

## New ADRs go here

Phase 3 ADRs continue the numbering (next: **ADR-101**) and are written **in full** in this file, below the index, with their one-line row in the index. Format: context -> decision -> consequences. A new decision also updates the affected live docs.

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
| 93 | The model builds the training block; one routine checker validates blocks and reviews folders | Superseded in part by 97 and 99; heading still says PROPOSED |
| 94 | Block lifecycle: two starting points, a block is a normal folder, "Eliminar bloque", favourite exercises | PROPOSED (as in its heading) |
| 95 | A routine folder with routines cannot be deleted: the user moves or deletes the routines first; no dialog | Supersedes 51; amends 45, 69 (R-08) |
| 96 | The coach never refuses a call because of money: no per-user or total cap; `coach_usage` records the spend and one non-blocking notice shows when the month passes 5 € | Supersedes 77's cap and 90 |
| 97 | The folder review is deterministic and runs on the device: a 0-10 score and a checklist, no model call, no "Aplicar en una copia"; "a block from a folder" keeps its model review | Supersedes 93's folder-review bullet; 94 and 95 unaffected |
| 98 | The nutrition plan is deterministic: `plan_nutrition` drops the model call | Supersedes the model call of 76 for the nutrition plan |
| 99 | Block creation is deterministic: a builder with no model call (split and type per day, 100 % gate) | Supersedes the model-built block of 93 and the block calls of 76; PROPOSED (as in its heading) |
| 100 | Product definition is a user-invoked `product-owner` skill; `epic-planner` no longer writes stories | — |

## Index (Phase 3, ADR-101..)

No Phase 3 ADR yet. The first one will be ADR-101 and its row goes here.

| ADR | Decision (one line) | Status |
|---|---|---|

## Phase 3 ADRs (full text)

None yet: Phase 3 has no ADR. Older ADRs: [`phase2/00-decisions.md`](phase2/00-decisions.md) (73-100) and [`phase1/00-decisions.md`](phase1/00-decisions.md) (01-72).
