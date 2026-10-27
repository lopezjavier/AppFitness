# Decisions (ADR index)

Live index of every closed decision, with the numbers frozen in Phase 1. The full text (context, decision, consequences, sources, approvals) of ADR-01..72 is in [`phase1/00-decisions.md`](phase1/00-decisions.md) and does not change. A decision is only changed by adding a new ADR that supersedes it (mark the old row "superseded by ADR-N").

## New ADRs go here

Phase 2 ADRs continue the numbering (next: **ADR-73**) and are written **in full** in this file (context, decision, consequences, approval), below the index. Add their one-line row to the table too. Format: context -> decision -> consequences. A new decision also updates the affected live docs.

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
| 08 | Health Connect with a manual "Sincronizar" button (weight, body fat), dedup by record id, no background sync | |
| 09 | 1RM estimated with Epley; AI assistant deferred | |
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
| 37 | 3 tabs Diario/Entreno/Perfil; Perfil root = photo, name, Preferencias, statistics entries; weight editable in Preferencias; name from Google or onboarding | Supersedes 26 |
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
| 66 | Periodic reports and streak check-ins move to Phase 2 | |
| 67 | No signed release build in Phase 1: `prodDebug` APK, v1.0.0 = tag + GitHub Release without APK | |
| 68 | Diary groups the entries added from a saved comida (`group_id`, `group_name` snapshot) | |
| 69 | UX rulebook (`05` §8-§10), designsystem templates and `check-ux-conventions.sh` guards | Amends 45 |
| 70 | "Actualizar automáticamente" is one global training setting | |
| 71 | Diario layout restored; sheets close only with X/back/"Cancelar" (R-38); icon for clear actions (R-39) | Amends 69 |
| 72 | Photo amount always gives a best estimate; confidence is a 1-10 scale (R-41) | Amends 64 |

(Numbers below 10 are zero-padded as in the frozen text and in code comments.)
