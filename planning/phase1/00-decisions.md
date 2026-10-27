# Decisions and findings

## 1. Closed decisions (ADR)

Format: context → decision → consequences. A decision is only changed by adding a new ADR that supersedes it.

### ADR-01 · Connectivity: online only
- **Context:** the user's gym has Wi-Fi; an offline-first model adds bidirectional sync and conflict resolution.
- **Decision:** Supabase is the single source of truth. No Room or local database.
- **Consequences:** a "Sin conexión" (offline) banner and disabled write actions (with retry) when there is no network. A workout session is created in the DB when it starts and every set is persisted when checked, so it survives an app restart. Only exception: during an open workout session, sets checked while offline are retried from memory when the connection returns (US-11); there is no local persistence. Purely local preferences (e.g. last chart filter) may use DataStore.

### ADR-02 · Authentication: Google Sign-In
- **Decision:** Credential Manager (`GetGoogleIdOption`) → ID token → `supabase.auth.signInWith(IDToken)` with the Google provider.
- **Consequences:** no sign-up or password recovery screens. For local development, the `local` flavor includes a "Test login" button using email/password against local Supabase and a seeded user. In the production Supabase project the email provider is disabled.

### ADR-03 · Scope: original requirements only
- **Decision:** the Stitch mockups are used as a **style** reference. Features that appear in the mockups but not in the original requirements are out of Phase 1 (see `01-phase1-scope.md`).

### ADR-04 · Adaptive workflow (QA step removed by ADR-27)
- **Decision:** tasks with UI follow the full flow (Planner → UX → Dev → PR Creator → PR Reviewer). Infrastructure/backend tasks without UI are validated with pgTAP, Edge Function tests and CI.
- **Consequences:** merging into `main` is gated by required CI checks (branch protection), not by the PR Reviewer's approval (GitHub does not allow approving your own PR). Details in `06-agent-workflow.md`.

### ADR-05 · Kotlin Multiplatform from day one
- **Decision:** KMP + Compose Multiplatform project with a single target (Android) in Phase 1. Domain, data and UI in `commonMain`; Android-only APIs (Credential Manager, Health Connect, Code Scanner, haptics) in `androidMain` behind interfaces.
- **Consequences:** multiplatform libraries (supabase-kt, Ktor, Koin, kotlinx-*) and MockK only in `androidUnitTest`. Eases the Phase 2 web client.

### ADR-06 · Open Food Facts through cached Edge Functions
- **Context:** OFF search is rate-limited (~10 requests/min per IP) and requires an identifying User-Agent.
- **Decision:** the app never calls OFF directly. The `food-search` and `food-barcode` Edge Functions first query the `foods` table (shared cache) and only then OFF, storing normalized results.
- **Consequences:** repeated searches are instant and the app is independent of the OFF format.

### ADR-07 · Barcode scanner
- **Decision:** Google Code Scanner (`play-services-code-scanner`), which shows its own full-screen UI and needs no camera permission. The result (editable nutrition card) is shown afterwards in a Bottom Sheet. If the product does not exist, the user is offered to create a manual food.

### ADR-08 · Health Connect with manual sync
- **Decision:** a "Sincronizar" (sync) button in Progress that reads `WeightRecord` and `BodyFatRecord` since the last sync (first time: last 90 days). Deduplication by the Health Connect record id. No background sync.

### ADR-09 · Deterministic 1RM
- **Decision:** in Phase 1 the 1RM is estimated with the Epley formula. The AI training assistant is Phase 2.

### ADR-10 · 4-tab navigation (amended by ADR-22 and ADR-26; tab set now Diario, Entreno, Perfil by ADR-37)
- **Decision:** Bottom Navigation with Diario (Diary), Entreno (Workout), Progreso (Progress) and Perfil (Profile); Diary is the start tab. The Top App Bar avatar also opens Profile. On tablets (width ≥ medium) a NavigationRail is used.
- **Contextual universal FAB:** Diary → search/scan food; Workout → start workout or create routine; Progress → log weight; Profile → hidden (Profile has its own "Guardar" (save) button). Also hidden during a live workout session.

### ADR-11 · Single design system palette
- **Decision:** the source of truth are the tokens described in the prose of `DESIGN.md` (`surface-base #0B0F14`, `surface-raised #121820`, `surface-overlay #1A222D`, `neon-lime #A3E635`…). Macro colors follow the mockups: Protein = lime, Carbs = cyan, Fat = amber. Details in `05-design-system.md`.

### ADR-12 · Keep the Supabase Free project alive
- **Context:** Supabase Free pauses projects after 7 days without activity.
- **Decision:** a GitHub Actions workflow scheduled every 3 days that calls the `health` function.

### ADR-13 · Public GitHub repository
- **Context:** on the Free plan, a private repo has no rulesets/branch protection, environments or secret scanning, and is limited to 2,000 Actions minutes/month. Since an agent performs the merges, branch protection is the most valuable safety net.
- **Decision:** `https://github.com/lopezjavier/AppFitness` is **public**. All rights reserved (see `LICENSE`): personal use only.
- **Consequences:** the repo never contains secrets or personal data. Ruleset on `main` with required checks, `production` environment holding the deployment secrets, secret scanning + push protection + gitleaks.

### ADR-14 · Application identifier
- **Decision:** `applicationId = io.github.lopezjavier.appfitness`; the `local` flavor adds the `.local` suffix.
- **Consequences:** two Android OAuth clients in Google Cloud (one per package) plus the Web client.

### ADR-15 · Supabase deployment from E0 and tasks in `tasks/`
- **Decision:** the `deploy-supabase.yml` workflow is created and validated in E0 with the empty project; US-02 adds migrations, tests, `supabase-ci` and `keepalive`. `T-###` tasks live in `tasks/todo.md` (plan in `tasks/plan.md`, the convention of the `planning-and-task-breakdown` skill).

### ADR-16 · English repository, Spanish UI
- **Decision:** everything in the repository (docs, code, comments, commits, PRs, task files) is written in English. The app UI stays in Spanish (NFR-10) through string resources; UI literals quoted in Gherkin remain in Spanish.

### ADR-17 · Supabase API keys: publishable + secret (no legacy keys)
- **Context:** Supabase replaced the legacy JWT-based `anon` and `service_role` keys with a **publishable key** (`sb_publishable_...`) and **secret keys** (`sb_secret_...`).
- **Decision:** the app (and CI builds) use the publishable key, exposed as `SUPABASE_PUBLISHABLE_KEY`; it is safe to ship because every table has RLS. Secret keys are only used server-side (Edge Functions, never the app or the repo). The legacy keys are not used.
- **Consequences:** user requests are still authorized by the user's Auth JWT (`Authorization: Bearer <access token>`), which is what RLS and `verify_jwt` check; the publishable key only identifies the project (`apikey` header). Calls without a user session (e.g. `keepalive` → `health`) send the publishable key as `apikey`. Verify supabase-kt and Supabase CLI versions support the new keys when pinning them (US-01, T-008).

### ADR-18 · JDK 21 for the build, JVM 17 bytecode
- **Context:** the development PC has Temurin JDK 21; the original plan assumed JDK 17. Current Gradle and the Android Gradle Plugin support (and recommend) JDK 17+ to run the build.
- **Decision:** JDK **21** runs Gradle locally and in CI (`actions/setup-java` with `temurin` 21). Kotlin `jvmTarget` and Java `sourceCompatibility`/`targetCompatibility` stay at **17**, so the bytecode works on every supported Android version and the language level does not depend on the build JDK.
- **Consequences:** `JAVA_HOME` points to JDK 21 (or Android Studio uses its bundled JBR 21). No JDK 17 install is needed. Raising the bytecode target is a separate decision.

### ADR-19 · Android application module separate from the KMP modules
- **Context:** the original layout made `composeApp` both the Kotlin Multiplatform module and the Android application. Since AGP 9.0, the `org.jetbrains.kotlin.multiplatform` plugin can no longer be applied in the same Gradle module as `com.android.application` or `com.android.library`. KMP modules must use the Android-KMP library plugin (`com.android.kotlin.multiplatform.library`), which has a **single variant** (no build types or product flavors). Checked in T-015 with the pinned set (AGP 9.3.3, Kotlin 2.4.21, Compose Multiplatform 1.12.1, Gradle 9.7.1): applying both plugins to one module fails at configuration time with:
  `The 'com.android.library' (or 'com.android.application') plugin is not compatible with the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0.` (the only bypass, `android.builtInKotlin=false` + `android.newDsl=false`, relies on legacy APIs that AGP 10 removes).
- **Decision:** three modules.
  - `androidApp` (`com.android.application` + `org.jetbrains.kotlin.plugin.compose`, AGP built-in Kotlin, no `org.jetbrains.kotlin.android`): `MainActivity`, `AppFitnessApplication` (starts Koin), the `local`/`prod` flavors, `BuildConfig`, the network security config and any flavor-only code.
  - `composeApp` (KMP library, `com.android.kotlin.multiplatform.library` + Compose Multiplatform): Compose UI in `commonMain`, platform implementations of the domain ports in `androidMain`.
  - `shared` (KMP library, `com.android.kotlin.multiplatform.library`): domain and data.
  - Flavor values reach `composeApp`/`shared` only through `AppConfig` (`shared`), built from `BuildConfig` in `androidApp` and injected with Koin.
- **Consequences:** application tasks are `:androidApp:*` (`:androidApp:assembleLocalDebug`, `:androidApp:assembleProdDebug`, `:androidApp:installLocalDebug`; APKs in `androidApp/build/outputs/apk/<flavor>/debug/`). The library modules have one variant; their host (JVM) unit tests are enabled with `withHostTest {}` and run with `testAndroidHostTest`, which also runs `commonTest`; the Android-only host test source set is `androidHostTest` (ADR-05's `androidUnitTest` under the new plugin). Each library module registers a `testLocalDebugUnitTest` lifecycle task that depends on `testAndroidHostTest`, so `./gradlew testLocalDebugUnitTest` keeps running every module's unit tests. Android resources in the library modules (needed for Compose resources in T-020) are opt-in with `androidResources { enable = true }`.
- **Sources (checked 2026-10-12):**
  - AGP 9.0 release notes, "Switch KMP projects to the Android Gradle Library Plugin for KMP": https://developer.android.com/build/releases/agp-9-0-0-release-notes
  - Set up the Android Gradle Library Plugin for KMP (single variant, `android {}` block, `withHostTest {}`, `androidResources`): https://developer.android.com/kotlin/multiplatform/plugin
  - Migrate to built-in Kotlin ("using `org.jetbrains.kotlin.multiplatform` together with `com.android.library` or `com.android.application` is no longer allowed"): https://developer.android.com/build/migrate-to-built-in-kotlin
  - Updating multiplatform projects with Android apps to use AGP 9 (extract `androidApp`): https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
  - Version compatibility: AGP 9.3 notes (Gradle ≥ 9.5.0, API ≤ 37): https://developer.android.com/build/releases/agp-9-3-0-release-notes · KGP ↔ Gradle/AGP table: https://kotlinlang.org/docs/gradle-configure-project.html · Compose Multiplatform compatibility: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- **Approval:** 2026-10-12, user (conditional on this verification; confirmed in T-015).

### ADR-20 · Path-filtered required CI checks always report
- **Context:** `android-ci` must not spend a build on supabase-only changes, but it is a required check in the `main` ruleset. A workflow filtered out by workflow-level `paths`/`paths-ignore` never creates its check run, so the required check stays "Expected — waiting for status to be reported" and the PR cannot merge.
- **Decision:** required workflows trigger on every PR and push to `main` with no workflow-level path filter. A `changes` job (`dorny/paths-filter`, pinned by major) computes one output per area; the work job, whose `name` is the required check context, depends on it and runs with `if: ${{ !cancelled() && (github.event_name == 'workflow_dispatch' || needs.changes.outputs.<area> != 'false') }}`.
  - A job skipped by its `if:` conditional reports its check as passing, so a genuine supabase-only diff (`android=false`) satisfies `android-ci` without building.
  - Fail-open: a job whose `needs` fails is also skipped (and would pass). Comparing with `!= 'false'` means an empty output from a failed `changes` job makes the work job *run*, so the check only passes through a real green build or a genuine skip. `!cancelled()` (not `always()`) keeps runs superseded by `concurrency` from starting the build.
  - `workflow_dispatch` bypasses the filter and always builds.
  - `android-ci` skips when every changed file is under a path the build never reads: `supabase/**` and, since 2026-10-13 (user request, PR #32), the text-only paths `**/*.md`, `planning/**`, `tasks/**`, `design/**`, `.claude/**` and `.agents/**`. The filter uses `predicate-quantifier: 'every'` with negated patterns, so a file counts only if it is outside all of them. The same pattern applies to `supabase-ci` (T-018) with the inverse filter (`supabase/**`).
  - The required check context is the job `name` (`android-ci`), unique across workflows, with `integration_id: 15368` (GitHub Actions), as for `gitleaks`.
- **Consequences:** the job name is a contract with the ruleset (`.github/rulesets/main.json`): renaming it requires a ruleset update. Supabase-only and text-only PRs show `android-ci` as skipped and can merge (a docs-only merge to `main` produces no APK artifact). If a build step or test ever starts reading an excluded path, remove it from the filter. The `changes` job costs a few seconds per run and needs `pull-requests: read` (granted on that job only). If GitHub ever reported a skipped required check as pending, the fallback is to keep the job always running and gate its steps with `if:` (same check name, ruleset unchanged).
- **Sources (checked 2026-10-12):**
  - GitHub Docs, "Troubleshooting required status checks", section "Handling skipped but required checks" (a job skipped by a conditional reports "Success"; a workflow skipped by path filtering stays "Pending" and blocks merging; a job depending on a failed job is skipped and may not block merging): https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks
  - `dorny/paths-filter` README (v4: `pull_request` uses the API file list; `push` to the default branch compares with the commit before the push; `predicate-quantifier`): https://github.com/dorny/paths-filter
- **Approval:** Approved by the user 2026-10-12 (E1 breakdown); the fail-open `changes` job was added by the PM in T-017.

### ADR-21 · `minSdk` 26
- **Context:** `planning/` did not fix a minimum Android version. Health Connect (`androidx.health.connect:connect-client`, US-26 in E5) requires API 26.
- **Decision:** `minSdk = 26` (Android 8.0) in all modules; `compileSdk`/`targetSdk` = the highest stable API supported by the pinned AGP (37 with AGP 9.3).
- **Consequences:** no runtime checks are needed for Health Connect availability by API level below 26. Raising `minSdk` later is cheap.
- **Approval:** 2026-10-12, user.

### ADR-22 · 3 tabs; Profile opens from the avatar (navigation part superseded by ADR-26; active-tab indicator kept; tab set amended by ADR-37)
- **Context:** at Checkpoint E1-C the user tested the first APK. The Perfil tab and the Top App Bar avatar led to the same screen (redundant), and the `glowLime` pill on the active tab was not in the mockups.
- **Decision:**
  - The bottom navigation (and the tablet NavigationRail) has 3 tabs: Diario, Entreno, Progreso. Diario stays the start tab.
  - Perfil is no longer a tab. It is a full-screen destination opened only from the avatar, over the current tab: Top App Bar with a back arrow, no bottom bar and no FAB. Back returns to the tab it was opened from, with its state and scroll preserved.
  - Active tab indicator: `neonLime` icon and label, the label in extra-bold (W800 instead of W600), and the **filled** icon where the glyph has a distinct filled variant (today only Diario's `local_fire_department`; `fitness_center` and `monitoring` look the same filled); no pill. It keeps the mockup icons while giving every tab a non-colour cue (WCAG 1.4.1).
- **Consequences:** resolves finding #1 (§2) in favour of "3 tabs + profile via avatar". US-04's Gherkin, `01` FR-5.1, `05` §5–6 and tasks T-027/T-028 are updated; the T-021/T-022 code is adjusted in T-031.
- **Approval:** 2026-10-12, user (Checkpoint E1-C feedback).

### ADR-23 · Maestro QA in the background, findings as GitHub issues (superseded by ADR-27)
- **Context:** in E1, QA was the largest share of each task (30–100 min in T-027/T-028) and blocked the merge, yet it found no app bug: the 7 defects caught came from pr-reviewer. Host (Robolectric) tests already cover most UI scenarios.
- **Decision:**
  - A UI task no longer waits for Maestro. After its merge, qa-engineer runs in the background in a separate worktree (`../AppFitness-qa`, on the merged `main`) while the next task starts: it writes `tests/e2e/T-###.yaml` and runs it plus the scoped regression, with a bounded brief (exact flows, at most 2 attempts per flow, never two Maestro runs at once).
  - App bugs become GitHub issues labelled `qa` (scenario, failing step, expected vs actual, log lines); QA never changes an open PR. The flow lands through pr-creator in a `test(T-###)` PR (text-only, so `android-ci` skips its build).
  - The PM checks open `qa` issues before starting each task: a confirmed bug becomes a `fix(T-###)` task; a broken flow goes back to qa-engineer.
  - A 👤 checkpoint or epic closure requires every QA run finished, no open `qa` issue (or one the user explicitly defers) and the full regression green. Catalog flows run only at checkpoints.
- **Consequences:** a bug can live on `main` for a task or two before its fix; UI scenarios must have host tests in the task itself. `CLAUDE.md`, `06-agent-workflow.md` and the qa-engineer, planner and pr-creator instructions are updated.
- **Approval:** 2026-10-13, user (E1 retrospective; the user proposed the design).

### ADR-24 · Onboarding resume and a single save on Profile (E2) (read-only weight amended by ADR-37; Preferencias becomes a hub of sections by ADR-69)
- **Context:** the E2 breakdown found four product gaps in US-06…US-08: what an interrupted onboarding shows on restart, whether onboarding includes the macro sliders, how Profile saves (the Gherkin had "Guardar" and "Guardar objetivos", the mockup one button), and whether the current weight is editable on Profile before weight logging exists (E5).
- **Decision:**
  - Resuming onboarding restarts at the first step with every step **prefilled** from the saved values. Finishing the last biometric step again **updates** the onboarding weight entry in `body_measurements`; it never adds a second row.
  - Onboarding has **no macro sliders**: the goal step saves the default split 30/45/25 (`04-business-rules.md` §1.5), adjusted later on Profile.
  - Profile has **one "Guardar" button** for the whole screen (personal data, strategy and macros). Saving recalculates the TDEE with the latest weight and **stores** the new daily target in `nutrition_goals` (`04-business-rules.md` §1.4).
  - "Peso actual" is **read-only** on Profile in E2 (latest `body_measurements` row); weight editing arrives with US-25 (E5).
- **Consequences:** the E2 epic Gherkin is updated (resume prefill, re-save without duplicates, onboarding saving with the default split, "Guardar" on Profile, read-only weight). The onboarding weight entry must be identifiable to update it (chosen in the T-035 plan, e.g. the user's earliest `manual` row). Profile's save writes `profiles` and `nutrition_goals` together, so the planner picks an order or an RPC that never leaves them inconsistent.
- **Approval:** 2026-10-14, user (answers to the E2 breakdown questions Q1–Q4).

### ADR-25 · Calorie safety floor by sex (E2)
- **Context:** the calorie target had one safety floor of 1200 kcal/day for everyone. Common nutrition guidelines put the minimum at about 1200 kcal/day for women and 1500 kcal/day for men.
- **Decision:** the floor depends on the profile's sex: **1200 kcal/day for women, 1500 kcal/day for men**. When the calculation gives less, the target is set to the floor and the same safety warning is shown.
- **Consequences:** `04-business-rules.md` §1.4, the US-07 "Safety floor" scenario and T-036 use the per-sex floor; recalculating on Profile (sex is editable there) applies the floor for the new sex.
- **Approval:** 2026-10-14, user.

### ADR-26 · Perfil is the 4th tab, its icon is the Google photo (E2) (superseded by ADR-37)
- **Context:** at Checkpoint E2-A (2026-10-14) the user tested the real journey on the phone. With Profile becoming the real screen in T-037 (personal data, strategy, macros, sign-out), reaching it only from the top-bar avatar hides it, and the avatar in every top bar costs space.
- **Decision:**
  - The bottom navigation (and the tablet NavigationRail) has **4 tabs: Diario, Entreno, Progreso, Perfil**. Diario stays the start tab. This supersedes the part of ADR-22 that removed the Perfil tab and made Profile a full-screen destination opened from the avatar; ADR-22's active-tab indicator (lime icon and extra-bold lime label, filled glyph where one exists, no pill) stays.
  - The Perfil tab icon is the **user's Google photo** (`profiles.avatar_url`, loaded with Coil, clipped to a circle), with the existing placeholder avatar as the fallback (no photo, loading or load error). The photo is never tinted; when Perfil is active its non-colour cue is the extra-bold lime label plus a lime ring around the photo (exact ring width chosen by ux-reviewer in T-037, `AppBorder` tokens).
  - The Top App Bar **avatar is removed** from every screen; `AppTopBar` shows the section title only. Profile is a normal tab destination: its own back stack, the bottom bar visible, no back arrow, and still **no FAB** (ADR-10/ADR-22).
- **Consequences:** `01` FR-5.1, `05` §5–6, the E2 epic and T-037 (which implements it) are updated. The T-031/T-034 code (full-screen Profile route, top-bar avatar) is reworked in T-037; the e2e flows that open Profile from the avatar (`auth`, `shell`) are updated by QA after T-037. The photo in the tab is decorative: the visible label "Perfil" names the tab for TalkBack.
- **Approval:** 2026-10-14, user (Checkpoint E2-A feedback).

### ADR-27 · No Maestro QA; manual verification with the delivered APK
- **Context:** the background Maestro QA (ADR-23) was the largest recurring cost per UI task, and the agent sessions spent most of their tokens writing and debugging `tests/e2e` flows, while the defects it caught were few and the host (Robolectric) tests already cover the Gherkin scenarios. The goal is to cut context and token use in Claude Code.
- **Decision:**
  - The `qa-engineer` subagent, the background QA step and the `../AppFitness-qa` worktree are removed from the flow.
  - Every Gherkin UI scenario is covered by a deterministic host (Robolectric) test in `androidHostTest`, written with the task (compose-ui-developer rule 14).
  - The app is verified by hand on real devices with `python scripts/deliver-apk.py`.
  - A defect found in manual testing becomes a corrective `fix(T-###)` task; no time is spent debugging a UI-automation script.
  - The Maestro infrastructure is removed: `tests/e2e/`, the `check-e2e-tags.sh` guard, the Maestro command rows and the `qa` GitHub-issue tracking.
- **Consequences:** supersedes ADR-23 and drops the QA step from ADR-04. `CLAUDE.md`, `planning/06-agent-workflow.md`, the agent files and `README.md` are updated; the qa-engineer agent, `tests/e2e/` and `check-e2e-tags.sh` are removed.
- **Approval:** 2026-10-17, user.

### ADR-28 · One task per session; per-epic audit log
- **Context:** after E1 the rule was to chain tasks up to the next 👤 checkpoint with the user compacting by hand after about two tasks (`retrospectives/E1.md`). That is the last unbounded context growth in a flow that is otherwise optimised for lean context (subagent delegation, ≤10-line replies, ADR-27); `/compact` was observed to degrade performance and quality drops as the window grows. The local audit log (`.workflow/execution.log`) also grows with every epic.
- **Decision:**
  - A task runs in its own session. When its PR is merged and `COMPLETED` is logged, the PM stops and asks the user to start a fresh session (`/clear` or a new terminal) for the next `T-###`. It never starts a second task in the same session and never relies on `/compact`.
  - The resume point is the next-task pointer and the first unchecked task in `tasks/todo.md`.
  - `.workflow/execution.log` holds only the current epic. At epic closure, after the retrospective, the epic's lines move to `.workflow/archive/execution-E#.log` (local, not versioned).
  - The PM never loads the whole log or an archive: it greps by `[T-###]`, `[E#]` or timestamp.
- **Consequences:** supersedes the E1-retro rule "chain tasks up to the checkpoint; the user compacts". `CLAUDE.md` (Autonomy, working files, epic closure) and `planning/06-agent-workflow.md` (§2.1, §3, §6) are updated. The checkpoints are unchanged (1–3 per epic): the *session* boundary becomes per task, the *feedback* boundary stays per checkpoint, so the user runs `/clear` at each task boundary instead of `/compact` every two tasks.
- **Approval:** 2026-10-17, user.

### ADR-29 · Account deletion from Profile (E2)
- **Context:** the E2 epic left account deletion out of scope. With Profile becoming the real screen (ADR-26), the user asked for a way to delete the account and all its data from the app, without going through the Supabase console.
- **Decision:**
  - Profile shows **"Borrar cuenta"** below "Cerrar sesión" (destructive ghost button). It opens the confirmation dialog (approved wording): title "¿Borrar tu cuenta?", text "Se borrarán tu perfil, tu peso y tus objetivos. No se puede deshacer.", buttons "Cancelar" / "Borrar" (destructive). Offline the button is disabled (`02-architecture.md` §9).
  - "Borrar" calls the Edge Function **`delete-account`** (`POST`, JWT required, no body). The function resolves the caller from their access token (never from the request body) and deletes that auth user with the Auth admin API, using the **secret key** that only exists server-side (ADR-17). Every user-owned row goes with it through `on delete cascade` (today `profiles`, `body_measurements`, `nutrition_goals`).
  - On success, or on `401` (the user or session no longer exists, e.g. a retried request whose first response was lost), the app clears the local session and the root shows Welcome. Any other failure keeps the user signed in with a transient message and nothing deleted.
  - A later Google sign-in with the same account creates a brand-new user that goes through onboarding again.
- **Consequences:** `01` FR-1.1, `03-data-model.md` §7, the E2 epic (US-05 scenarios, "Out of scope") and T-037 are updated. Every future table that references `auth.users` must cascade (already the convention in `03` §Conventions); nullable author columns such as `foods.created_by` must pick `on delete cascade` or `set null` when they are created, or deletion fails. The function is deployed by `deploy-supabase` with the others. If manual testing deletes the seeded local test user, `npx supabase db reset` recreates it.
- **Approval:** 2026-10-14, user (follow-up to Checkpoint E2-A).

### ADR-30 · Forward-compatible training model and in-app RepDB assets
- **Context:** E3 (Phase 1) logs sets as weight × reps only, but the follow-up training epics (**E6** depth, **E8** insights) need set types, RPE, per-exercise metric types, routine folders/tracking and a PR catalogue. Building E3 on the minimal shape would force a schema migration and a live-session refactor later. Separately, the exercise catalogue uses the RepDB free assets (495 exercises: thumbnails, start/peak poses and Spanish instructions), whose licence allows only in-app use with visible attribution.
- **Decision:**
  - The training tables are created **forward-compatible**: the enums and columns added in `03-data-model.md` §5 (marked *(E6)*) ship with E3, with defaults; E3 renders only `weight_reps` / `working` sets, and the live session's set row is built **polymorphic** so E6 extends it without a rewrite.
  - The RepDB free catalogue is **bundled in the APK** (`composeApp/src/commonMain/composeResources/files/exercises/`, ≈16 MB, `library.json`; module and layout fixed by ADR-39); catalogue text and images live in the client, never in the DB or a public CDN.
  - The bundle has no male/female variants: one pose pair per exercise (ADR-39). Animations are a **start↔peak crossfade** (3 s cycle; the free tier has no GIF/video). Attribution "Datos de ejercicios por RepDB (repdb.co)" must be **visible** in the app (full policy in the project's `ATRIBUCION_RepDB.md`).
  - The app's own licence stays **repo-only** (ADR-13); third-party credits (RepDB, Open Food Facts, Google Gemini, OSS libraries, fonts) are shown in an **"Acerca de · Licencias"** screen opened from Profile (screen defined in E9 · US-45).
- **Consequences:** `03-data-model.md` §5, `04-business-rules.md` §4, the E3/E6/E8/E9 epic files, `01-phase1-scope.md` and `07-roadmap.md` reflect this. **No schema migration** between E3 and E6.
- **Approval:** 2026-10-17, user.

### ADR-31 · Epic order: training first and a closing epic (E3…E9; amended by ADR-58)
- **Context:** the first roadmap ordered the domains Diary → Workout → Progress, split training into "core", "depth/wrap-up", "analytics" and "statistics", and scattered About/licences and polish across them. The user wants training to lead (it is the app's core value), the "wrapper/wrap-up" framing removed, and the plan to end with one closing epic that also absorbs the UI-refinement bucket.
- **Decision:** reorder and reshape Phase 1:
  - E0 Setup · E1 Infrastructure · E2 Auth/profile (unchanged, US-01…US-08).
  - **E3 Training core** (US-09…US-15, v0.3.0): catalog, routine builder, live session, previous performance, rest timer, history and strength-progress charts.
  - **E4 Food diary** (US-16…US-24, v0.4.0): catalog and OFF cache, search, scanner, manual creation, configurable meals, portions and measures, favourites, diary views, copying, templates and history.
  - **E5 Weight tracking** (US-25…US-27, v0.5.0).
  - **E6 Training depth** (US-28…US-38 and US-47, v0.6.0).
  - **E7 Food photos, defaults and cooking** (US-48…US-51, v0.7.0), inserted by ADR-58.
  - **E8 Training insights** (US-39…US-44, v0.8.0).
  - **E9 Finalization** (US-45 About/licences, US-46 polish/accessibility/tablet/release, plus the UI-refinement bucket, v1.0.0).
  - Story IDs are stable and never renumbered; new stories append (US-47…US-51). Dependencies: E3→E2; E4→E2; E5→E2; E6→E3; E7→E2/E4/E6 US-47; E8→E3 (charts) and E6 (PRs/warm-up); E9→E1…E8.
- **Consequences:** supersedes the epic order of the earlier roadmap. `01-phase1-scope.md`, `02-architecture.md`, `03-data-model.md`, `07-roadmap.md`, `08-environment-setup.md` and the epic files follow the new numbering; the "Phase 1B" framing and the "wrap-up" epic name are dropped (it is all Phase 1). Version tags: v0.1.0…v0.8.0 at E1…E8, **v1.0.0 only at E9**. ADR-58 records the E7/E8/E9 renumbering; the training-first order stands.
- **Approval:** 2026-10-17, user.

### ADR-32 · Food base units and named portions (E4)
- **Context:** logging only in grams does not fit drinks (millilitres) or how people actually log ("1 huevo", "1 rebanada").
- **Decision:**
  - Every food has a **base unit**: `g` for solids, `ml` for drinks; its nutrition is stored **per 100 base units** (`03-data-model.md` §3).
  - `food_portions` holds named portions whose `amount` is expressed in the food's base unit.
  - `food_log_entries` stores the resolved `amount` in base units plus a `portion_label` snapshot; totals and snapshots use `kcal = kcal_100 × amount / 100` (`04-business-rules.md` §2).
  - There is no `g`↔`ml` conversion: a food is either solid or liquid.
  - The OFF normalization sets the base unit from `nutrition_data_per` (`100 g` → `g`, `100 ml` → `ml`).
- **Consequences:** `03-data-model.md` §1/§3/§7, `04-business-rules.md` §2 and the E4 stories US-16, US-17, US-18 and US-22 reflect this.
- **Approval:** 2026-10-17, user.

### ADR-33 · Configurable meals via `meal_slots` (E4) — amended by ADR-41
- **Context:** a fixed four-value meal enum cannot be renamed, reordered or extended, and the Diary is the app's hub.
- **Decision:**
  - Meals are rows in `meal_slots` per user (`code` from the `meal_type` enum for the defaults, `label`, `position`, `enabled`), seeded with Desayuno/Comida/Merienda/Cena when onboarding completes.
  - `food_log_entries` references `meal_slot_id` instead of a fixed `meal_type`.
  - ~~A meal is **disabled**, never deleted; disabling requires the slot to be empty on the day being viewed.~~ Superseded by ADR-41.
- **Consequences:** `03-data-model.md` §1/§3 and the E4 stories US-19 and US-23 reflect this.
- **Approval:** 2026-10-17, user.

### ADR-34 · Favourites and "most eaten" (E4)
- **Context:** re-logging the usual foods should be one tap; recent foods alone are not enough.
- **Decision:** keep an explicit `favorite_foods` table (star toggle) and derive "most eaten" from `food_log_entries` through the `most_eaten_foods` RPC (no stored ranking).
- **Consequences:** `03-data-model.md` §3/§6 and the E4 story US-17 reflect this.
- **Approval:** 2026-10-17, user.

### ADR-35 · Body composition scope in E5 (weight)
- **Context:** E5 covered weight and body fat only; a smart scale and Health Connect can carry more body-composition data, and users expect a goal weight and an understandance of their numbers.
- **Decision:**
  - Extra composition metrics (`lean_mass_kg`, `water_kg`, `bone_kg`, `bmr_kcal`) are stored on `body_measurements` but only written by the Health Connect sync; manual entries leave them null (`03-data-model.md` §4).
  - Health Connect sync is **bidirectional**: it reads those records and writes the user's manual weight and body fat back when the permission is granted (`04-business-rules.md` §3.7).
  - **Circumferences** live in their own `body_circumferences` table (site enum + `value_cm`); the waist-to-hip ratio is derived, never stored (`04-business-rules.md` §3.9).
  - The **target weight** is a plain profile field (`profiles.target_weight_kg`); there is no date or estimated time to reach it.
  - BMI is derived (not stored); metric interpretation is **informative only** and carries a non-medical disclaimer (`04-business-rules.md` §3.6, §3.8).
- **Consequences:** `03-data-model.md` §1/§2/§4/§8, `04-business-rules.md` §3/§5, `01-phase1-scope.md` FR-3, `05-design-system.md` and the E5 stories US-25, US-26 and US-27 reflect this.
- **Approval:** 2026-10-18, user.

### ADR-36 · No universal FAB; each screen owns its actions
- **Context:** US-04 put a single contextual "+ Registro" FAB in the shell, mapped per tab, as a placeholder for logging actions. Each tab will expose its own specific action buttons instead (E3 workout, E5 weight…), so a shared universal button is not useful on any screen.
- **Decision:** remove the universal FAB entirely — the `UniversalFab` component, its per-tab `FabAction` mapping, the WIP action sheet it opened, its placement in the bottom-bar and rail layouts, its catalog entry, strings and tests. This supersedes the "contextual universal FAB" part of ADR-10; ADR-10's navigation decision (4 tabs, adaptive bar/rail) still stands. Each screen defines its own action buttons.
- **Consequences:** `05-design-system.md` §4–§6 and `01-phase1-scope.md` FR-5.1 no longer mention the FAB; `02-architecture.md` (module layout) drops it from the `app/` description; the per-screen actions are specified in the E3 and E5 stories.
- **Approval:** 2026-10-18, user.

### ADR-37 · 3 tabs, Perfil with photo, name and Preferencias; editable weight (E2)
- **Context:** at Checkpoint E2-B (2026-10-19) the user tested Profile on the phone. The Progreso tab has no content of its own (weight and insights will be reached from the profile), the photo as a tab icon is not wanted, the Profile screen should be a personal root rather than a long form, and the weight should be editable.
- **Decision:**
  - **3 tabs: Diario, Entreno, Perfil.** The Progreso tab is removed. The Perfil tab icon is the generic Stitch `person` glyph (`design/stitch/progress/code.html`), not the Google photo; the active state follows ADR-22 (lime icon, extra-bold lime label). This supersedes ADR-26 (photo icon, 4 tabs) and amends ADR-22 and ADR-10 (tab set).
  - **Perfil root screen:** the Google photo (placeholder fallback) shown large, the user's name below it, a button that opens a full-screen **"Preferencias"** screen (back arrow) with the current editable Profile content (personal data, weight, energy, macros, one "Guardar"), and "Cerrar sesión" and "Borrar cuenta" on the root. Later epics add statistics entry buttons on this root: content planned for the Progreso tab (E5 weight chart and history, E3/E8 strength and training insights, any analytics) becomes entries reached from Perfil, each a full-screen destination with a back arrow.
  - **Name:** taken from Google `full_name`. If it is missing, onboarding step 1 "Sobre ti" asks "¿Cómo te llamas?" (only then). It is always editable in Preferencias.
  - **Weight is editable in Preferencias.** Saving a changed weight records today's manual `body_measurements` weight, replacing today's manual row if one exists; earlier weights stay as history. "Peso actual" shows the latest weight from any source; a later Health Connect sync shows the newer value, which stays editable. This amends ADR-24's read-only weight.
  - **Copy:** "TDEE: %s kcal" becomes "Gasto diario estimado: %s kcal" with the helper line "Calorías que quemas al día según tu cuerpo y tu actividad." (onboarding and Preferencias).
- **Consequences:** `01` FR-5.1, `02`, `03` §2/§4, `05` §5–6, `07`, the E2, E3, E5 and E1 epic notes and `tasks/` are updated; the Profile code (T-037) is reworked in T-041; the Edad/Altura display bug is fixed first in T-040.
- **Approval:** 2026-10-19, user (Checkpoint E2-B feedback).

### ADR-41 · Meals are deleted when unused, never disabled (E4, T-067) (inside the editor the removal waits for "Guardar": ADR-69)
- **Context:** at Checkpoint E4-B the user found the enable/disable switch confusing and the meal editor too large on the Diary; a meal should simply be removed when it is not needed. Amends ADR-33.
- **Decision:**
  - A meal is **deleted** with a trash button, at once (no dialog, no undo), only if it has no `food_log_entries` on any day and the user keeps at least one meal. Otherwise the delete is refused and the row explains why. There is no enable/disable: the `enabled` column is kept but always true (a migration re-enables any disabled slot).
  - Rules are enforced server-side by the `delete_meal_slot(p_id)` RPC (re-packs positions to 0..n-1); `save_meal_slots(p_slots)` no longer takes a viewed day or a disable rule.
  - The meal structure is edited from Perfil -> Preferencias -> "Comidas" (compact icon-action rows, single "Añadir comida"), not from the Diary.
- **Consequences:** `03-data-model.md` §3 (`meal_slots`, RPCs), US-23 in `epics/E4-food-diary.md`, `05-design-system.md` (`MealSlotEditor`), `ux-reviewer` space/icon rule. Meal templates (US-21) must handle a deleted slot (`meal_slot_id` is a suggestion, nullable).
- **Approval:** 2026-10-20, user (E4-B feedback item 7).

---

## 2. Findings from the review of the original documents

### ADR-38 · RepDB catalogue stays out of the repo; superset rest model (E3)
- **Context:** the repo is public and the RepDB licence allows in-app use only (ADR-30). A superset also needs two rest values (between its exercises and between rounds).
- **Decision:**
  - Catalogue rows in the DB hold only `key`, `muscle_group` and `equipment`; `name` is nullable for catalogue rows and the Spanish name, instructions and images come from the bundle in the client. The bundle is gitignored; test fixtures are synthetic.
  - Superset: per group the user sets rounds, reps per exercise, the rest between exercises of a round and the rest between rounds. Stored in `routine_exercises.rest_seconds`: non-last members hold the transition rest, the last member holds the rest between rounds. All members share the same set count.
- **Consequences:** `03-data-model.md` §5 (`name` nullable for catalogue rows) and the US-10/US-13 superset scenarios are updated by the planner in T-042/T-043/T-046.

### ADR-40 · State restoration is a requirement of every screen with state (E3, T-050)
- **Context:** the E3 checkpoint showed that leaving the app (home, app switcher, screen off, process death) reset the tab, the screen, the scroll and every unsaved draft. Causes: the root branched on a transient `Loading` route that disposed the shell and its `NavHostController`, and form drafts lived only in ViewModel fields or plain `remember`.
- **Decision:**
  - The `NavHostController` lives above the root route branch and is recreated only on sign-out; a root route already resolved never regresses to `Loading` for the same signed-in user (no profile refetch). Auth always wins over restoration.
  - Every screen with user-editable or navigational state keeps it in `rememberSaveable`/`rememberLazyListState` or, for ViewModel state, in a `SavedStateHandle` (primitives, `@Serializable` JSON, or enums by name). Drafts are restored but never auto-saved, and a restored draft still counts as unsaved (the discard confirmation keeps working).
  - Every UI task that adds such state ships a restoration test: a host test with `StateRestorationTester` that rebuilds the ViewModel from the same `SavedStateHandle`, plus a ViewModel test that a recreated ViewModel reads the draft back.
- **Consequences:** `02-architecture.md` §3 records the rule; the planner lists the restoration test under "Tests" for any UI task; a new draft or form without it is a review finding.

### Product and stories (original user stories and frontend features)
| # | Issue | Resolution |
|---|---|---|
| 1 | 3 tabs + profile via avatar (features) vs 4 tabs (stories, mockups) | ADR-10, revised by ADR-22, ADR-26 and finally ADR-37 (3 tabs: Diario, Entreno, Perfil) |
| 2 | US-05 used `navigator.wakeLock.request('screen')`, a web API | `Modifier.keepScreenOn()` / `FLAG_KEEP_SCREEN_ON` while the live session is visible (US-11) |
| 3 | Health Connect: manual button (requirements) vs automatic background sync (US-09) | ADR-08 |
| 4 | Google Code Scanner cannot be shown inside a Bottom Sheet | ADR-07 |
| 5 | Estimated 1RM was Phase 2 (AI) and also in the Phase 1 mockups | ADR-09 |
| 6 | US-01 proposed anonymous authentication and only 4 generic tables | ADR-02 and `03-data-model.md` |
| 7 | "Search the backend database" vs Open Food Facts | ADR-06 |
| 8 | US-03 example: 175 g P / 260 g C / 65 g F = 2,325 kcal, not 2,400 | Examples recalculated (kcal = 4·P + 4·C + 9·F) |
| 9 | Undefined calculation rules (TDEE, strategy adjustment, auto-balance, weekly rate) | `04-business-rules.md` |
| 10 | Missing stories: login, onboarding, text search, diary editing, template management, manual weight logging, exercise catalog, routine builder, history, tablet | Added (US-01…US-46 in `epics/`) |
| 11 | Tablet not covered by any story | US-04 (adaptive shell) and US-46 (review) |
| 12 | Offline behavior undefined | ADR-01 |
| 13 | Mockup features outside the requirements (water, photos, reminders, Apple Health, Strava, "Plan Pro") | ADR-03 |
| 14 | US-11: on Profile the FAB "saves changes" (not a logging action) | ADR-10 |

### Design (`DESIGN.md`, mockups)
| # | Issue | Resolution |
|---|---|---|
| 15 | Two overlapping palettes in `DESIGN.md` (generated M3 `#101419/#181c21/#ccff80` vs prose `#0B0F14/#121820/#A3E635`); the stories used the former | ADR-11 |
| 16 | `DESIGN.md` assigns purple to carbs and green to protein; the mockups use lime/cyan/amber | ADR-11 |
| 17 | The Stitch HTML files are Tailwind (web) | Visual reference only; the design system is implemented in Compose (`05-design-system.md`) |

### Process and Claude Code (original orchestrator and workflow documents)
| # | Issue | Resolution |
|---|---|---|
| 18 | Both documents duplicated and contradicted each other (Planner creates the branch vs PR Creator creates it after QA) | A single `CLAUDE.md`; the branch is created at the start of the task |
| 19 | `gh pr review --approve` fails on your own PR | PR Reviewer uses `gh pr review --comment`; merge gated by CI (ADR-04) |
| 20 | Agent frontmatter with non-existent tools (`FileRead`, `FileEdit`) and `tools` as a list | `tools: Read, Write, Edit, Bash, Grep, Glob` |
| 21 | A single Kotlin developer agent; the backend had no owner or tests | `frontend-developer` and `backend-developer` agents |
| 22 | Third-party skills (`npx skills add addyosmani/...`) installed without review | Reviewed; their principles apply with this project's commands (see `CLAUDE.md`) |
| 23 | The Planner mentioned Room | Removed (ADR-01) |
| 24 | Google Sign-In cannot be automated | "Test login" button in the `local` flavor (ADR-02) |
| 25 | Gradle commands of a plain Android project, no flavors | KMP commands with flavors (`06-agent-workflow.md`) |
| 26 | CI only deployed the backend; `setup-cli` with `version: latest`; reference to `analyze-meal` (Phase 2) | 4 workflows with pinned versions (`02-architecture.md`) |
| 27 | Documents in the root and a misspelled file name | Content absorbed into `planning/` and `CLAUDE.md`; originals kept outside the repository |

### ADR-39 · Exercise assets live in `composeApp` Compose resources; catalogue rows come from a generated migration (T-042)
- **Context:** ADR-30 placed the RepDB bundle in `app/assets/exercises/`, a path that predates the module split (ADR-19). The real bundle (`Biblioteca_Ejercicios_Free`) has no `library.json`, no male/female variants and no separate thumbnails: it ships `free.es.json` (643 exercises, schema_version 3) and one WebP pose pair per exercise in `images/flat/` (148 exercises have a single `main` image). `seed.sql` never runs in production, so the catalogue rows need another route to every database.
- **Decision:**
  - The assets live in Compose Multiplatform resources of `composeApp`: `composeApp/src/commonMain/composeResources/files/exercises/` (`library.json` + `images/<key>-start.webp` / `-peak.webp`), gitignored with one `.gitignore` line. `composeApp` already hosts the UI, the Robolectric host tests and the `Res` class, so Coil and the tests reach the files without a platform port.
  - `scripts/import-repdb.py <bundle>` writes that folder from `free.es.json` and `images/flat` (trimmed `library.json`: name, description, instructions, tips, poses). Exercises with a single `main` image are imported as `-start` with `poses: ["start"]`. Only `free.es.json` and `images/flat` are used; the other locales, `images/equipment`, `images/muscles`, `premium-samples/` and the sibling Gifs set are out of scope.
  - Thumbnail = the `start` pose (cropped). There is no male-variant selection.
  - The catalogue rows reach every database through a generated, idempotent migration (`scripts/generate-exercise-catalog.py`) holding only `key` + our enum values, no RepDB text. The script fails on bundle ids missing from its mapping tables. Later catalogue changes are a new migration.
  - A catalogue row whose key is not in the client bundle is hidden (it has no name to show); the placeholder icon is for custom exercises and for images that fail to load.
  - CI and unit tests build without the bundle; `scripts/deliver-apk.py` refuses to build without it.
- **Consequences:** `02-architecture.md`, `03-data-model.md` §5, `05-design-system.md` §6.1, `08-environment-setup.md`, the E3 epic file and `tasks/` reflect this.

### ADR-42 · Saved meals: "Comidas" wording, no quick add, overwrite or save-as-new (E4, T-061)
- **Context:** reviewing T-061 before building (user, 2026-10-21): quick-add chips do not scale to many saved meals, and the user thinks of a saved Desayuno as a "comida".
- **Decision:** the UI calls saved templates "comidas" ("Guardar como comida", search tab "Comidas"); backend and code keep `meal_templates`. No quick-add chips on the Diary; a comida is added only from the search tab. Editing a comida ends with a choice: "Sobrescribir" or "Guardar como nueva" (asks a name, unique per user).
- **Consequences:** US-21 in `epics/E4-food-diary.md`, FR-2.7 in `01-phase1-scope.md`, T-061 in `tasks/todo.md` and `tasks/plan.md` reflect this.

### ADR-43 · Store the full Open Food Facts product and show micronutrients (E4, T-068) — manual-food nutrients amended by ADR-53
- **Context:** the user wants micronutrients from OFF kept and shown in the food details for future use (2026-10-21). ADR-06 and the `foods` cache keep only kcal, P/C/F, serving, name, brand and image URL; the rest of what OFF sends is discarded, and micronutrients were listed as out of scope in E4.
- **Decision:**
  - `foods.off_data jsonb null` stores the complete product object exactly as OFF returned it (nutriments, ingredients, allergens, additives, Nutri-Score, NOVA, categories, …), written only by the Edge Functions with the secret key. The typed columns stay the source for kcal/macros and search; `off_data` is never used to compute diary entries (snapshots, ADR-32).
  - `food-search` and `food-barcode` request the product without the `fields` filter. Everything OFF returns is stored; no field is ever dropped. If the unfiltered search proves too slow (8 s timeout), the planner logs `BLOCKED` and asks the user instead of narrowing `fields`.
  - Search responses stay lean (no `off_data`). The food details read `off_data` for one food on demand and show the micronutrients OFF provides per 100 g / 100 ml (fiber, sugars, saturated fat, salt/sodium, vitamins and minerals when present), hiding absent values. Personal foods have no `off_data`; their micronutrients are the ones the user typed from the label (next bullet).
  - Manual foods (user decision 2026-10-21): the creation form has optional fields for the EU label set, per 100 g/ml: saturated fat, sugars, fiber, salt. They are stored in `foods.micronutrients jsonb null` using OFF keys (`saturated-fat_100g`, `sugars_100g`, `fiber_100g`, `salt_100g`) and `create_personal_food` gains a `p_micronutrients jsonb` argument (values >= 0, only those four keys). The details screen reads `off_data.nutriments` for OFF foods and `micronutrients` for personal ones through the same mapper. Vitamins and minerals are not offered in the form.
  - Existing cache rows (`source = 'off'`, `off_data is null`) are handled by the migration: rows nothing references (no `food_log_entries`, `favorite_foods` or `meal_template_items`) are deleted so they refill complete on the next search or scan; referenced rows are kept and filled the next time the product is fetched (`food-barcode` also refreshes any row with null `off_data`, regardless of age). Diary entries, favourites, templates, training data and preferences are never altered.
- **Consequences:** `03-data-model.md` (§3 `foods`, `food-search`/`food-barcode`), `01-phase1-scope.md` (FR-2.13), `epics/E4-food-diary.md` (scope line, US-16b; numbered US-25 until 2026-10-21, when it was renamed to free US-25 for E5), `tasks/plan.md` and T-068 reflect this. The micronutrient values are display-only in Phase 1 (no targets, no totals).

### ADR-44 · Diary history moves to Perfil "Estadísticas"; the Diario always has its calendar button (E4, T-071)
- **Context:** Checkpoint E4-C (user, 2026-10-21): the Historial screen opened a Diario without the calendar button (a flag, `showHistory`), and day/week/month metrics belong with the other statistics. ADR-37 already places training and weight statistics behind statistics buttons on the Perfil root.
- **Decision:**
  - The Diario has one UI and always shows the calendar button next to the day arrows (no conditional). It jumps to a given day; the arrows stay.
  - The diary history (Día / Semana / Mes summaries and the month calendar) leaves the Diario and becomes a full-screen destination (back arrow) reached from an "Estadísticas" entry on the Perfil root (ADR-37 pattern). Tapping a day there opens the normal Diario on that day.
- **Consequences:** US-24 in `epics/E4-food-diary.md`, `tasks/todo.md` and `tasks/plan.md` (T-071) reflect this; no data-model change.

### ADR-45 · Interaction rules shared by every screen (E4, T-072) — folder-delete exception in ADR-51; rulebook, dialog policy and swipe behaviour superseded by ADR-69 (`05-design-system.md` §8–§10)
- **Context:** Checkpoint E4-B/E4-C (user, 2026-10-21): the same UX defects were reported on several screens (swipe fires too easily, "Deshacer" added after the user had asked for none, edits saved per action, differing texts for the same concept). Fixing them screen by screen leaves the app inconsistent.
- **Decision:** `05-design-system.md` §8 holds generic rules, enforced by ux-reviewer on every UI task:
  - **Delete:** runs at once, with no confirmation dialog and no "Deshacer" (a deliberate swipe or tap is the confirmation). Deleting from a details screen closes it. Confirmation dialogs stay only for account-level destructive actions ("Borrar cuenta").
  - **Swipe-to-delete:** one shared designsystem component; the action fires only when the row is dragged to the end of its travel (heavy, slow); a shorter drag snaps back.
  - **Editing:** an edit screen has a single "Guardar" that saves every change together; nothing is saved per action; renaming is part of that draft. Delete is the only action that is immediate.
  - **Lists:** a row opens its details; editing happens from the details. Rows of the same kind show the same information on every screen.
  - **Wording:** one concept, one label across screens and across periods (e.g. day, week and month averages are all "Media diaria").
  - **Sweep:** when a task changes one of these behaviours, the ux-reviewer searches the app for every other instance and the plan includes them.
- **Consequences:** supersedes the "Deshacer" of T-069; US-21/US-24, `tasks/todo.md` (T-072, T-073), `05-design-system.md` §8 and `.claude/agents/ux-reviewer.md` reflect this. The E9 refinement item about swipe-to-delete is consumed by T-072.

### ADR-46 · Text food search is limited to Spain (E4, T-074)
- **Context:** User feedback (2026-10-21): the text search returns poor results, many from other countries or without a Spanish name.
- **Decision:** `searchOff` asks Open Food Facts for products sold in Spain (`tagtype_0=countries`, `tag_contains_0=contains`, `tag_0=spain`) and keeps only hits whose `countries_tags` includes `en:spain` and that have a Spanish name (`product_name_es`, or `lang = "es"` with `product_name`). The default v1 order (`unique_scans_n`) is kept; no `sort_by`. Requests stay unfiltered by `fields` (ADR-43). Barcode lookup is unchanged: a scanned product always resolves. Paging relies on the raw OFF page count, not the filtered one.
- **Consequences:** products without a Spanish name no longer appear in text search (they remain reachable by barcode); cached search results from before the change are purged or re-keyed.

### ADR-47 · Health Connect weight takes precedence over manual weight (E5, T-084)
- **Context:** Checkpoint E5-B (user, 2026-10-22): the user weighs in through the scale and Health Connect; manual entry is secondary and must never override a Health Connect weight.
- **Decision:**
  - For a day with a Health Connect weight, that weight is the day's weight everywhere in the app. The app offers no manual weight entry or edit for that day (the action is disabled with the note "Sincronizado desde Health Connect").
  - The "Peso actual" field in Preferencias is read-only while the most recent weight comes from Health Connect (same note). It stays editable when the latest weight is manual or there is none.
  - Manual entry keeps working for days without a Health Connect weight.
  - Composition values (body fat %, lean mass, water, bone, BMR) are reachable from a "+" button on "Peso actual" ("Composición corporal" sheet with the latest values and dates), in each day's detail and, from T-085, as chart metrics.
- **Consequences:** `epics/E5-weight.md` (US-25, US-27), `tasks/todo.md` (T-084, T-085) and `tasks/plan.md` reflect this; no data-model change expected (the planner confirms).

### ADR-48 · Drop "Comparar"; "Peso actual" box opens the composition; pencil replaces "Registrar peso de hoy" (E5, T-087)
- **Context:** Checkpoint E5-B round 2 (user, 2026-10-22): "Comparar" inside a day's detail has no clear target and makes no sense; the "+" on "Peso actual" is an odd control; the green "Registrar peso de hoy" button is unwanted.
- **Decision:**
  - FR-3.8 (compare two measurements) is dropped from Phase 1: no "Comparar" action, `ComparisonSheet` or `ComparisonDeltaRow` use, and the scenario "Compare two measurements" is removed from US-27.
  - The whole "Peso actual" box opens the "Composición corporal" sheet (the "+" is removed).
  - A pencil in the place of the "+" opens today's weight entry; the green "Registrar peso de hoy" button is removed. Under ADR-47 the pencil is disabled for a day with a Health Connect weight.
- **Consequences:** `01-phase1-scope.md`, `07-roadmap.md`, `epics/E5-weight.md` and `tasks/todo.md` reflect this; the code and tests for the comparison are removed in T-087.

### ADR-49 · Trend line and weekly pace must work with sparse weigh-ins (E5, T-088)
- **Context:** Checkpoint E5-B round 3 (user, 2026-10-22): the user weighs once or twice a week. With two weigh-ins (the 14th and the 19th) the trend shows two dots and no line, and the pace reads "Sin datos suficientes". The 7-day average required 2 days with data in its window, which sparse weighing rarely meets.
- **Decision:**
  - The weight trend always draws a line, however few points there are and however far apart: the line connects consecutive available points across gaps (two weigh-ins a year apart still give a line). A single point stays a dot with the existing hint.
  - `MA7(d)` is the average of the available values in `[d − 6, d]` with **1 or more** days (the "at least 2 days" condition of `04-business-rules.md` §3.2 is dropped). With sparse weighing the line therefore follows the weigh-ins; the "MA 7D" legend stays.
  - The weekly rate is the change between the **last two days with a weigh-in**, scaled to kg/week: `(w_last − w_prev) / days_between × 7`, 2 decimals. With fewer than 2 days with data → "Sin datos suficientes". The colouring by strategy is unchanged.
  - Same behaviour for the other body metrics of the selector.
- **Consequences:** `04-business-rules.md` §3.2/§3.3, `epics/E5-weight.md` (US-25 scenarios) and `tasks/todo.md` (T-088) reflect this. Supersedes the "drawn only if ≥2 days" rule.

### ADR-50 · Routines are planned set by set; planned values are placeholders; target RPE per exercise (E6, T-089, T-091)
- **Context:** US-29 had two contradicting scenarios: "Planning sets in a routine" (each planned set with its own weight, reps and type; scope moved from E3 on 2026-10-19) and "Routine asks only sets and rest" (user request 2026-10-20). E6 breakdown, Q1 (user, 2026-10-22).
- **Decision:**
  - Per-set planning holds: each planned set has its own target weight, reps and type. The scenario "Routine asks only sets and rest" is removed.
  - In the live session, a pending set started from a planned set shows the planned weight and reps as a **dim placeholder** in KG and REPS (the E3 ghost pattern, `04-business-rules.md` §4.3), never as filled values; the planned type is applied to the set as its value. A planned set without a weight falls back to the ghost placeholder. The ANTERIOR column keeps the previous performance.
  - The target RPE is **per exercise** (`routine_exercises.target_rpe`, already in the ADR-30 schema), shown as "Objetivo RPE N" on the exercise's working sets. Chosen over per set because it needs no extra column and RPE targets are usually stated per exercise.
- **Consequences:** `epics/E6-training-depth.md` (US-29, US-30), `04-business-rules.md` §4.3, `tasks/todo.md` (T-089, T-091). The planned-set table itself is a schema exception recorded by T-089.
- **Approval:** 2026-10-22, user (E6 breakdown, Q1; placeholder and per-exercise RPE chosen by the epic-planner as delegated).

### ADR-51 · Deleting a routine folder deletes its routines, after a confirmation (E6, T-098)
- **Context:** US-35 said "deletes a folder and confirms… its routines move to Mis rutinas". ADR-45 keeps confirmation dialogs only for account-level actions. E6 breakdown, Q3 (user, 2026-10-22).
- **Decision:**
  - Deleting a folder deletes the folder **and all its routines** at once. Past workouts of those routines are kept (`workout_sessions.routine_id` is set null, as for a single routine delete).
  - It asks for a confirmation that states how many routines will be deleted. This is an explicit exception to ADR-45 for a delete that removes many items at once. Every other training delete (a set, a routine, a workout) stays immediate with no dialog and no "Deshacer".
  - Deleting a set in the live session keeps ADR-45: immediate, no "Deshacer" (Q2).
- **Consequences:** supersedes "routines move to Mis rutinas" in `epics/E6-training-depth.md` US-35 and `03-data-model.md` §5 (`routine_folders`); `05-design-system.md` §8 lists the exception; T-096 and T-098 in `tasks/todo.md`.
- **Approval:** 2026-10-22, user (E6 breakdown, Q2 and Q3).

### ADR-52 · E6 scope cuts and defaults set at the breakdown (E6)
- **Context:** E6 breakdown questions Q4–Q7 (user, 2026-10-22).
- **Decision:**
  - **Deferred** (out of Phase 1 for now; the scenario text is kept in `01-phase1-scope.md` §4.1 so it is not lost): the live "¡Nuevo récord!" notification and its strength levels (US-34 "Live PR"); the streak, rest days and rewards in the training calendar (US-37 "Calendar"); copying a workout (US-37 "Editing").
  - Kept: PR detection and the catalogue, shown in the session summary, the exercise detail and the workout detail; the calendar with trained days (month, year, multi-year); editing and deleting a workout, "Guardar como rutina" and past workouts (from scratch, from a routine or from a finished workout as a template; never through the live session).
  - "Total de repes" is the **all-time** total of completed reps of the exercise (the business rules did not define it).
  - Calculator defaults (`04-business-rules.md` §4.9): warm-up bar × 10, 40 % × 5, 60 % × 3, 80 % × 2; bars 20, 15, 10 (EZ) and 7,5 kg; plates 25, 20, 15, 10, 5, 2,5, 2, 1,25, 1 and 0,5 kg; rounding 2,5 kg for plates and 2 kg for dumbbells.
- **Consequences:** `01-phase1-scope.md` FR-4.8 and §4.1, `04-business-rules.md` §4.8/§4.9, `epics/E6-training-depth.md` (US-33, US-34, US-37), `03-data-model.md` §6, `tasks/todo.md` (T-094, T-097, T-100, T-101). E8 US-44 (streaks and weekly check-ins) is not changed here; the E8 breakdown confirms it with the user.
- **Approval:** 2026-10-22, user (E6 breakdown, Q4–Q7).

### ADR-53 · Only manual foods are editable; edits apply to future entries; full label nutrients (E6, US-47, T-102)
- **Context:** user request 2026-10-22 (after E5-A): edit foods and portions, enter every value of a Spanish label, and list nutrients in label order. Open Food Facts foods are a cache shared by every user (ADR-06, ADR-43); diary entries are snapshots (ADR-32). E6 breakdown, Q8.
- **Decision:**
  - Only manual foods (`source = 'user'`, owned by the caller) can be edited: name, brand, kcal, macros, micronutrients and portions. Open Food Facts foods are not editable (no edit action). The base unit is not editable.
  - Edits apply to **future** diary entries only. Past entries keep their ADR-32 snapshots (values, `food_name`, `portion_label`); nothing is recomputed. Saved comidas compute from the food, so they use the new values.
  - Portions: adding, editing and removing are allowed. An edited portion applies to new entries only. The user's rule "removing a portion is blocked while diary entries use it" is dropped: entries and comida items keep the amount and label as a snapshot and do not reference the portion row, so the removal changes nothing for them.
  - Manual foods can carry the full Open Food Facts nutrient list in `foods.micronutrients` (OFF keys, values ≥ 0, known keys only). This amends ADR-43, which allowed four keys and no vitamins or minerals.
  - Nutrients are entered and shown in the Spanish label order (EU Regulation 1169/2011, Annex XV): energy, fat, saturates, mono-unsaturates, polyunsaturates, carbohydrates, sugars, polyols, starch, fibre, protein, salt, then vitamins and minerals (Annex XIII order). The food details of every food, manual or OFF, use the same order and hide absent values.
- **Consequences:** `epics/E6-training-depth.md` (US-47), `01-phase1-scope.md` (FR-2.14), `03-data-model.md` §3/§6 (`foods.micronutrients`, the edit RPC, `create_personal_food`), `07-roadmap.md`, `tasks/todo.md` (T-102). ADR-32 is unchanged.
- **Approval:** 2026-10-22, user (E6 breakdown, Q8, revised the same day to future entries only).

### ADR-54 · E6 schema exceptions to ADR-30: planned sets table (E6, T-089)
- **Context:** ADR-30 fixed the forward-compatible schema at E3; per-set routine planning (ADR-50) needs a child table that does not exist there.
- **Decision:**
  - New table `routine_exercise_sets` (`routine_exercise_id` FK on delete cascade, `set_index` >= 1, `target_weight_kg` null >= 0, `target_reps` 1-50, `set_type`; unique `(routine_exercise_id, set_index)`), RLS enabled and forced through the parent routine's owner.
  - `routine_exercises.target_sets` stays equal to the planned-set count; `target_reps`, `target_weight_kg` and `planned_set_type` stay as legacy summaries (not dropped). Existing routines are migrated to `target_sets` identical planned sets.
  - `save_routine` takes `sets: [{weight_kg?, reps, set_type}]` per exercise (1-10) and `target_rpe`; superset members must have the same set count (`22023`, ADR-38). `start_workout_session` creates one pending set per planned set with its type.
  - Planned weight and reps are read from the routine at runtime, not copied onto `workout_sets` (a routine edited mid-session only changes the placeholders).
  - The set type label is "Fallo" (not "Al fallo"); later E6 tasks that need schema exceptions amend this ADR.
  - Amendment (T-090): table `training_preferences(user_id pk, warm_up_included boolean default false, updated_at)`, RLS enabled and forced, owner policies; later E6 tasks add columns. SQL helper `warm_up_included()` (stable, security invoker) is the single entry point for the flag; `exercise_history` and `weekly_muscle_volume` count warm-up sets only when it is true (computed on read, toggling recalculates all history). `save_preferences` takes `p_warm_up_included boolean default null` (null = unchanged) so "Guardar" stays atomic; the old signature was dropped.
  - Amendment (T-096, US-38): `workout_sessions.paused_total_s int not null default 0 check (>= 0)` (existing `paused_at` is reused); RPCs `pause_workout_session`, `resume_workout_session` (idempotent, `in_progress` only, `P0002` otherwise) and `delete_workout_set` (re-indexes the later sets of the exercise); `finish_workout_session` folds an open pause. `training_preferences.previous_values_mode` (enum `previous_values_mode`: `overall` | `routine`, default `overall`); `last_performance` gains `p_routine_id uuid default null` (the 1-arg function was dropped) and `save_preferences` gains `p_previous_values_mode` (null = unchanged; old signature dropped). Business rule in `04-business-rules.md` §4.10.
  - Amendment (T-093): per-exercise options live in a new table `exercise_options(user_id, exercise_id, double_dumbbell boolean default false, updated_at)`, primary key `(user_id, exercise_id)`, RLS enabled and forced with owner policies (not `training_preferences`, which is one row per user); later per-exercise options add columns. SQL helper `volume_factor(exercise_id)` (stable, security invoker) returns 2 when the caller enabled "Contar mancuerna doble" on that exercise and it is a dumbbell exercise, else 1; `exercise_history` and `weekly_muscle_volume` multiply `set_volume(...)` by it (computed on read, so toggling recalculates all history; `set_volume` is unchanged and T-094 records reuse both). RPC `set_exercise_double_dumbbell(p_exercise_id, p_enabled)` upserts the row (`22023` for a non-dumbbell exercise, `P0002` when not found). The 1RM is not doubled (it stays the per-dumbbell weight).
  - Amendment (T-099, US-36): `workout_sessions.exercise_rests jsonb not null default '{}'` (map `exercise_position -> seconds`, 0-600) persists the rests changed from "Descanso" during a session; RPC `set_session_rest(p_session_id, p_exercise_position, p_rest_seconds)` (in-progress sessions only, `P0002` otherwise, `22023` outside 0-600). RPC `update_routine_from_session(p_session_id) returns uuid` replaces the routine's exercises and planned sets with the COMPLETED sets of a finished session of that routine, in one transaction (rest = session rest, else the previous routine rest of the exercise, else 120; `target_rpe` carried over; sets over 10 raise `22023`; a superset whose members end with different set counts loses its `superset_group`); it never touches the session or the routine's name, notes, folder, order or flags. `save_routine` accepts optional `track_as_routine` and `auto_update_routine` (omitted = unchanged; defaults on create).
- **Consequences:** `03-data-model.md` (`routine_exercise_sets`, RPC contracts), `epics/E6-training-depth.md`, `tasks/todo.md` (T-089).

### ADR-55 · E6-A feedback: in-app explanations, mandatory RPE prompt, routine-level calculators and options (E6, T-097d…T-097f)
- **Context:** user feedback at Checkpoint E6-A (2026-10-23): the new training features are not self-explanatory (RPE, set types, calculators, roundings, previous-values mode); the app, not the chat, must teach them.
- **Decision:**
  - Every non-obvious training concept has an in-app "?" help with a short Spanish explanation (RPE, Tipo de serie, both calculators, plate and dumbbell rounding, previous-values mode, "Contar mancuerna doble").
  - Single-choice pickers (set type, RPE) apply on tap and close; no "Listo"/"Guardar" and no "Quitar tipo"/"Quitar RPE". "Normal" is the way back to the default type.
  - RPE: a vertical scale (6–10 in half steps, as today; the user did not ask for 1–10) coloured green to red (ux-reviewer designs it). Completing a non-warm-up set without RPE opens the scale; picking a value saves, closes and completes the set; closing it without picking leaves the set pending. Warm-up sets are exempt. Changing it later: tap the RPE again. This amends US-30 (RPE was optional).
  - Live session: planned weight and reps are pre-filled in grey and editable; "Anterior" fills the last values; "Finalizar entreno" appears below "Añadir ejercicio" once every exercise is complete; pause/resume is an icon in the top bar next to the timer and the ⋮ menu (removed from the "En vivo" menu).
  - The warm-up calculator, the plate calculator and "Contar mancuerna doble" are also available in the routine builder; the double-dumbbell option is per exercise (ADR-54 `exercise_options`), so it applies to every session, not only the live one.
  - Dumbbell exercises get the plate calculator with a 1 kg handle as the "bar" (per dumbbell, editable in Preferencias); the warm-up calculator uses it as the base for dumbbells. This amends ADR-52.
  - Warm-up calculator: a stepper (1–4, default 1: only the 60 % x 3 set) in the sheet picks how many warm-up sets are proposed; per exercise and per use, not stored in Preferencias. Fewer sets keep the heavier steps (3 → 40/60/80 %, 2 → 50/80 %, 1 → 60 %). Plate calculator labels the result "A cada lado de la barra:".
  - Bug: the first back press after starting a live exercise returns to the live exercise (fixed in T-097d).
- **Consequences:** `epics/E6-training-depth.md`, `04-business-rules.md` §4.9, `05-design-system.md` (help affordance), `tasks/todo.md` (T-097d, T-097e, T-097f before T-098), Checkpoint E6-A re-presented after them.
- **Approval:** 2026-10-23, user.

### ADR-56 · E6-A feedback round 2: whole-number RPE, derived rounding, disc loader, hand count per exercise (E6, T-097g…T-097i)
- **Context:** user feedback at the re-test of Checkpoint E6-A (2026-10-24).
- **Decision:**
  - RPE is a whole number 6–10 (no half steps; the scale stays 6–10 as in ADR-55); existing half values are displayed rounded and values below 6 are shown as 6. This amends ADR-50 and ADR-55.  - Sets can be completed in any order (no rule added); the mandatory RPE prompt applies to every non-warm-up set, including the first set of the first exercise (bug).
  - Pausing the workout also pauses the rest countdown; marking a set "Listo" while paused resumes both.
  - The warm-up sheet calls the weight "Peso objetivo (kg)", defaults to the best past weight of the exercise (fallback: the planned weight) and takes the bar or handle from Preferencias (no selector in the sheet).
  - "Redondeo de discos" and "Redondeo de mancuernas" are removed: the rounding is derived as twice the smallest available plate (barbells and dumbbells alike; no fixed dumbbells). The columns `plate_rounding_kg` and `dumbbell_rounding_kg` are dropped. This supersedes the rounding part of ADR-52 and ADR-55.
  - The plate calculator becomes a loader: the user picks how many discs of each weight go on one side and the total is bar (or handle) + 2 × the sum (same discs on both sides). Bar, handle and available plates come from Preferencias.
  - "Contar mancuerna doble" is replaced by a per-exercise flag `per_hand` (weight logged per hand or total) on every equipment type. The catalogue is classified by the user (a local CSV outside the repo (`exercise-hand-count.csv`, temporary)); the new-exercise form asks for it. Manual exercises become editable (name, equipment, muscle group, hand flag) and deletable. The `exercise_options.double_dumbbell` option and `set_exercise_double_dumbbell` are superseded; `volume_factor` reads the flag. This amends ADR-54.
  - Done (T-097i): `exercises.per_hand` (default false; the catalogue flags come from the migration, 178 keys), `volume_factor` reads it, `update_custom_exercise` / `delete_custom_exercise` RPCs (deleting an exercise with logged sets or routine entries is refused with their counts, `23503`); `exercise_options` and `set_exercise_double_dumbbell` are dropped (old toggles discarded). Catalogue flags are read-only; only manual exercises are editable. The help text says "pesa"/"mano", never "mancuerna" (machines and cables can be per hand too).
  - Look-and-feel items (Normal icon, "N" instead of the number, darker grey placeholders, RPE help as a list) go to the E9 refinement table.
- **Consequences:** `04-business-rules.md` §4.9/§4.10, `03-data-model.md`, `epics/E6-training-depth.md`, `tasks/todo.md` (T-097g…T-097i before re-presenting E6-A).
- **Approval:** 2026-10-24, user.

### ADR-57 · Protected accounts cannot be deleted from the app (E6, T-097l)
- **Context:** the user keeps a testing account (deleted and recreated often) and a personal account, and fears deleting the personal one by mistake (2026-10-24).
- **Decision:**
  - The Edge Function `delete-account` refuses (403, code `account_protected`) when the caller's email is in the Edge Function secret `PROTECTED_EMAILS` (comma-separated, case-insensitive). Emails never live in the repo (public); the user sets the secret with `npx supabase secrets set`.
  - The Profile screen shows "Borrar cuenta" disabled with the note "Esta cuenta está protegida" for a protected account; the app learns it from a refusal or a check the planner defines (no email in the app).
  - Permanent: no unlock in the app; changing the secret is the only switch. Deleting the user from the Supabase console is not covered.
- **Consequences:** ADR-29, `tasks/todo.md` (T-097l).
- **Approval:** 2026-10-24, user.

### ADR-58 · Epic renumbering with stable story IDs (E7: food photos, defaults and cooking; E8: training insights; E9: finalization)
- **Context:** the user wants a new food epic before training insights: photo food creation, default comidas and cooking methods. Renumbering the epics while also renumbering stories would break links, history and tests; the story IDs must stay stable.
- **Decision:**
  - The new **E7 · Food photos, defaults and cooking** ships in `v0.7.0` with **US-48** (AI dish photo), **US-49** (AI label and barcode photos), **US-50** (cooking methods) and **US-51** (default comidas).
  - The old E7 becomes **E8 · Training insights** (`v0.8.0`) and keeps **US-39…US-44**. The old E8 becomes **E9 · Finalization** (`v1.0.0`) and keeps **US-45/US-46**. E6 keeps **US-47**.
  - Story IDs are permanent: they are never reused or shifted when an epic moves. New stories append to the sequence (US-48 onward).
  - Epic files are `E7-food-photos-defaults-and-cooking.md`, `E8-training-insights.md` and `E9-finalization.md`; all live cross-references and versions point to them.
- **Consequences:** `07-roadmap.md`, `01-phase1-scope.md`, the epic files, `tasks/plan.md` and the ADR index. Amends ADR-31's epic list and versions. Historical notes in `tasks/todo.md` and `retrospectives/` keep the epic names in use at their date; this ADR maps them to the current files. ADR-59…ADR-61 record the product decisions of the new E7.
- **Approval:** 2026-10-24, user.

### ADR-59 · Food photos use Gemini through a Supabase Edge Function; photos are never stored (E7, US-48/US-49)
- **Context:** the user wants to cut typing when logging food. The app already has barcode scanning and Open Food Facts; what is missing is a single-dish estimate and a way to read a label or identify a product from a barcode in a still photo.
- **Decision:**
  - The add-food screen shows a photo action directly below the barcode icon. The mode picker offers "Estimar con IA", "Etiqueta nutricional" and "Código de barras".
  - The image comes from the system camera or the Android photo picker (`PickVisualMedia`). The picker grants access only to the selected image; no media permission, in-app gallery or CameraX is introduced.
  - The AI call goes through the Supabase Edge Function `food-photo` to a Gemini vision model on the **free tier**, with the key only in the Supabase Edge Function secret `GEMINI_API_KEY`. No AI SDK or key is added to the app, and no GitHub secret is used for it. The model id defaults to a constant in `gemini-client.ts`, overridable by the optional `GEMINI_MODEL` secret; a 404 means the id is no longer served.
  - Prompts are fixed in the function: one for a single dish, one for a nutrition label and one for a barcode. The optional comment is dish-only, at most 200 characters, and is passed as data, never as instructions. The image is downscaled to at most 1600 px/JPEG before upload.
  - A dish estimate returns name, brand, base unit, nutrients per 100 base units, optional micronutrients, an estimated weight and a suggested "1 ración". A label result transcribes only the printed values and leaves the rest empty. A barcode result identifies the product and returns its per-100 values; the code is not saved. Every result opens the existing manual-food form, prefilled and fully editable; saving creates a normal personal food (`source = 'user'`) and then opens the normal add-to-meal sheet. There is no `ai` food source: `food_source` describes ownership/cache (`off` shared vs `user` personal), not how the values were entered. If provenance is ever needed, it would be a separate nullable field.
  - The existing scanner icon, live Google code scanner and `food-barcode`/OFF lookup are unchanged. The barcode photo is an AI mode, not a still-image decode; it never calls `food-barcode` and does not save the code.
  - Photos, comments and provider response bodies are never stored in the database, in Storage or in logs; the temporary file is deleted on every outcome. Whole-meal estimation from one photo (several foods) stays in Phase 2.
- **Consequences:** FR-2.15; `03-data-model.md` §7 (`food-photo`); `08-environment-setup.md` (Supabase secret and free-tier limit); E7 US-48/US-49; no Storage bucket, photo table, still-image barcode dependency or media permission.
- **Approval:** 2026-10-24, user.

### ADR-60 · Cooking methods add only the retained olive oil (E7, US-50)
- **Context:** griddled, air-fried and fried foods carry oil that the label does not reflect. Logging the raw food undercounts calories and fat, but tracking the whole oil used overcounts it.
- **Decision:**
  - Solid foods (`base_unit = 'g'`) offer Crudo (default), A la plancha, Air fryer and Frito. Drinks never offer it.
  - Retention factors are fixed: A la plancha 50 %, Air fryer 100 %, Frito 10 %. The olive oil **used** is configurable in Preferencias → "Cocina" with defaults 5 g, 5 g and 50 g (0–500 g each).
  - `retained_g = used_g × factor`; the entry adds `9 × retained_g` kcal and `retained_g` g of fat. Protein, carbohydrates and micronutrients do not change.
  - The entry stores `cooking_method`, `cooking_oil_retained_g` and the adjusted kcal/fat snapshots; the food is unchanged. Changes to Preferencias apply to future entries only. This amends ADR-31 (snapshots now include the cooking values).
  - `meal_template_items` stores a method per item (default `raw`), editable when saving or editing a comida. Adding a saved comida or applying a default copies that method and computes the retained oil with the current Preferencias; the template itself does not store oil. `save_meal_template` carries the method per item.
- **Consequences:** FR-2.16; `03-data-model.md` (enum, `food_log_entries`, `food_preferences`, `meal_template_items`, `save_preferences`, `save_meal_template`); `04-business-rules.md` §2.2; `05-design-system.md` (`CookingMethodSelector`, comida editing); E7 US-50.
- **Approval:** 2026-10-24, user.

### ADR-61 · Default comidas are saved comidas applied once on the first open of a new day (E7, US-51)
- **Context:** the user eats the same breakfast or lunch on recurring weekdays and does not want to add the same saved comida every time. A background job or a meal-plan calendar would be more machinery than a 1–5-user app needs.
- **Decision:**
  - Defaults use the **saved comidas** (`meal_templates` + items), not single foods or Open Food Facts results. A default may contain several foods.
  - At most one default comida per meal and ISO weekday; different weekdays may have different comidas. The meal slot in the default is authoritative and the saved comida's suggested slot is ignored.
  - In Preferencias → Comidas, "Comidas por defecto" opens a full-screen editor with one card per meal. The weekday picker preselects every available day; days already assigned to another comida for that meal are disabled. Saving replaces all assignments atomically; leaving with unsaved changes asks to discard.
  - The app applies today's defaults on the **first open of today's Diary**, before reading entries. `diary_default_applications` makes it idempotent: if the day was already evaluated, nothing happens; if the day already has entries, only the marker is written. Deleting the added entries never brings them back.
  - There is no backfill, no past-date application, no midnight job and no notification. Configuring defaults after today's Diary was opened applies from the next new day. A saved comida edited later affects only future applications; already-added entries keep their snapshots.
- **Consequences:** FR-2.17; `03-data-model.md` (`meal_defaults`, `diary_default_applications`, `save_meal_defaults`, `apply_meal_defaults`); `04-business-rules.md` §2.3; `05-design-system.md` (`WeekdaySelector` and the defaults destination); E7 US-51.
- **Approval:** 2026-10-24, user.

### ADR-62 · E6-C change requests: change a manual food's unit while unused; a comida row opens its details (E7, US-52/US-53)
- **Context:** Checkpoint E6-C (2026-10-24). The user saved a manual food with `ml` instead of `g` and could not fix it in the app (ADR-53 made the base unit non-editable); it was corrected by hand in the database. Also, in "Añadir comida" → Comidas a row tap adds the comida and a pencil edits it (T-102b), the opposite of Recientes, Más comidos and Favoritos, where a row opens the food details with "Añadir".
- **Decision:**
  - A manual food's base unit (`g`/`ml`) is editable **only while no `food_log_entries` row and no `meal_template_items` row references the food**; favourites do not block it. The server enforces it; once referenced, the unit control is disabled with an explanation. Nutrition values, portion amounts and labels are kept as typed (they are per 100 base units and in base units). Open Food Facts foods stay non-editable. This amends ADR-53 ("The base unit is not editable") and keeps ADR-32 snapshots intact.
  - In "Añadir comida" → Comidas a row tap opens a **comida details** screen (foods, amounts, totals) with "Añadir" (adds the comida to the diary as before) and "Editar" (opens "Editar comida"). The row has no pencil. This supersedes the row-tap-adds and pencil behaviour of T-102b and removes the US-47 exception in `05-design-system.md` §8: a row opens its details; editing starts from the details.
- **Consequences:** `epics/E7-food-photos-defaults-and-cooking.md` (US-52, US-53), `01-phase1-scope.md` (FR-2.18, FR-2.19), `03-data-model.md` (`update_personal_food` gains the unit, new migration, by the planner), `05-design-system.md` §8, `tasks/todo.md` (E7 breakdown).
- **Approval:** 2026-10-24, user (Checkpoint E6-C, treated as a change request; E6-C approved).

### ADR-63 · The comida details are the editor; "Añadir" adds what is shown (E7, US-53)
- **Context:** Checkpoint E7-A (2026-10-25). ADR-62 made a comida row open a read-only details screen with "Añadir" and "Editar", and "Editar" opened "Editar comida". The user found the two screens redundant: both show the same name, foods and totals.
- **Decision:**
  - There is a single comida screen: the details are directly editable (name, amounts, removing and adding foods; US-21, US-47) with a footer of secondary "Guardar" and primary "Añadir". The read-only screen, its "Editar" button and the "Editar comida" title disappear; "Eliminar comida" is a trash icon in the header row.
  - "Añadir" logs the foods **as the screen shows them** (edited amounts, removed and added foods) to the selected meal and day, without storing the comida; the stored comida changes only with "Guardar". When the draft differs from the saved comida, a short text says so ("Se añadirá tal como lo ves; Guardar para conservar los cambios"). Leaving after "Añadir" discards the unsaved edits by design. "Añadir" needs the server (offline: disabled with the banner); the name does not block it.
  - The draft survives leaving the app (ADR-40, ADR-45); the amount sheet keeps its own "Guardar" (`05-design-system.md` §8).
- **Consequences:** amends ADR-62 (comida details); `epics/E7-food-photos-defaults-and-cooking.md` (US-53 rewritten, US-48 photo icon next to the barcode icon), `epics/E4-food-diary.md`, `01-phase1-scope.md` (FR-2.19), `05-design-system.md` §8 and component rows, `tasks/todo.md` (T-106a, T-108, Checkpoint E7-A).

### ADR-64 · Amount estimate from a photo for a chosen food; dish estimates zero-fill (E7, US-54)
- **Context:** Checkpoint E7-A (2026-10-25). The user wants to photograph a portion of a food already chosen in "Añadir comida" and get the weight, without creating a food. Also, a dish estimate showed an empty sugar field although the real value is 0 (the AI returns `null`).
- **Decision:**
  - `food-photo` gains a fourth mode `weight`. The request carries the food as data (name, brand, base unit and per-100 kcal/macros); no comment. The response is the estimated amount in the food's base unit (g, or ml for drinks) plus confidence. Only the amount field of "Añadir comida" is prefilled: no food is created, nothing is stored, the user can edit it.
  - The estimate is the weight of the food as shown (as cooked if it is cooked); it does not touch the cooking method, which the user chooses afterwards (US-50). A selected named portion switches to the base unit.
  - The icon appears only in `AddFoodSheet`, for solid foods and drinks.
  - For **dish** mode, `normalizeDraft` maps a missing/`null` macro or micronutrient to `0` (a response with no nutrition at all is still `NO_FOOD_RECOGNIZED`). Label and barcode keep `null`. This amends ADR-59 and US-48 ("absent values stay null") for dish only; the form shows micronutrients as 0.
  - Photos and food data are never stored or logged (ADR-59 unchanged).
- **Consequences:** FR-2.20; `03-data-model.md` §7; E7 US-54 and US-48 notes; `tasks/todo.md` T-107b.
- **Approval:** 2026-10-25, user.
- **Approval:** 2026-10-25, user (Checkpoint E7-A, Q1/Q2 answered).

### ADR-65 · Cooking adds fat as a percentage of the raw weight (E7, US-50)
- **Context:** Checkpoint E7-B feedback (2026-10-25). ADR-60 asks for grams of oil per method, which does not scale with the amount of food. For 300 g of potato, frying needs ~300 ml of oil, but the default of 50 g gave the wrong result.
- **Decision:**
  - The fat added by a cooking method is `raw weight (g) × factor`. The entry adds that many grams of fat and 9 kcal per gram of it. Protein, carbohydrates and micronutrients do not change.
  - Defaults are A la plancha 1 %, Air fryer 2 % and Frito 8 %, each editable in Preferencias → "Cocina" (0–20 %).
  - The retention factors of ADR-60 (50 / 100 / 10 %) are removed; the columns `olive_oil_*_g` become percentage columns.
  - Everything else from ADR-60 stays: the methods, solid foods only, Crudo as default, a method stored per comida item, snapshots in the entry.
  - Existing entries and Preferencias are not recalculated; only entries logged from now on use the new rule.
- **Consequences:** amends ADR-60. Affects `03-data-model.md` (`food_preferences`, `cooking_impact`, `apply_meal_defaults`), `04-business-rules.md` §2.2, `05-design-system.md` (Cocina fields, impact line) and E7 US-50; the docs are updated in T-112's PR.
- **Approval:** 2026-10-25, user (Checkpoint E7-B feedback).

### ADR-66 · Periodic reports and streak check-ins move to Phase 2 (E8, US-42…US-44)
- **Context:** User request, 2026-10-25, before the E8 breakdown. Periodic reports are out of Phase 1.
- **Decision:**
  - US-42 (Monthly Report), US-43 (Year in Review) and US-44 (Streaks and weekly check-ins) leave Phase 1 and are deferred to Phase 2. Their text is kept in `01-phase1-scope.md` §4.1.
  - E8 keeps US-39…US-41 (exercise charts, advanced statistics, muscle distribution).
  - The trained-days streak in the E6 calendar (US-37) is unchanged.
- **Consequences:** affects `epics/E8-training-insights.md`, `01-phase1-scope.md`, `07-roadmap.md` and the E8 breakdown.
- **Approval:** 2026-10-25, user.

### ADR-67 · No signed release build in Phase 1 (E9, US-46)
- **Context:** User request, 2026-10-25, before the E8 breakdown. The app is a personal project (1–5 users) installed from the `prodDebug` APK with `scripts/deliver-apk.py`; nobody needs a distributable release.
- **Decision:**
  - Phase 1 ships only the `prodDebug` APK. No release keystore, no R8/ProGuard rules, no release SHA-1 in Google Cloud and no tag-triggered `release.yml`. `v1.0.0` is a git tag plus a GitHub Release with notes and no APK attached.
  - The "cold start < 2 s" goal is a manual check on the `prodDebug` APK, not a release-build gate.
  - The repository stays public during Phase 1 (ADR-13, unlimited Actions minutes). The user plans to make it private once the app is fully implemented; at that point ADR-13, NFR-02 and the CI minutes (2,000 per month on GitHub Free) are revisited.
- **Consequences:** affects `epics/E9-finalization.md` (US-46, Definition of Done), `01-phase1-scope.md` §4.1, `02-architecture.md` §7–§8, `07-roadmap.md` and `08-environment-setup.md`.
- **Approval:** 2026-10-25, user.

### ADR-68 · Diary groups the entries added from a saved comida (E8, US-55)
- **Context:** User request, 2026-10-25, during E8. A saved comida added to a meal shows today as separate foods, indistinguishable from foods added one by one.
- **Decision:**
  - `food_log_entries` gains two nullable columns, `group_id uuid` and `group_name text` (the comida's name when it was added). Entries sharing a `group_id` in the same meal and day form one group. It is a snapshot: editing, renaming or deleting the comida never changes the diary, and there is no foreign key to `meal_templates`.
  - Entries get a group when a saved comida is added to a meal (manually or as a default applied on a new day) and when the user saves a meal as a comida: the entries of that meal become one group under the new name from that moment. Applying the same comida twice creates two groups.
  - Only entries created from now on are grouped. Existing entries keep `group_id = null` and stay individual foods; no backfill.
  - Diary row: a collapsed group shows the name, total kcal and macros; expanding shows its entries like regular foods, each editable and deletable. The expanded state is kept across leaving the app. A food added to the meal later is never part of a group. Deleting some entries leaves the group with the rest; deleting the last one removes the group. Copying a day or a meal keeps the grouping with a new `group_id`.
- **Consequences:** affects `03-data-model.md` (`food_log_entries`, the add-comida and save-as-comida RPCs), `04-business-rules.md` if totals are documented there, `epics/E8-training-insights.md` (US-55) and the E8 breakdown (T-117).
- **Approval:** 2026-10-25, user (scope and behaviour); wording pending the user's OK in the docs PR.

### ADR-69 · UX rulebook, templates and guards (E9, US-56)
- **Context:** UX audit of 2026-10-25 (`ux-audit-2026-10.md`). The same UX defects recurred at every checkpoint because the design system offered atoms but no patterns, the rules were prose in four contradicting places, and `ux-reviewer` checked one plan at a time and never the implemented UI.
- **Decision** (user, 2026-10-26: all audit recommendations D1…D14 accepted, except D12):
  - `05-design-system.md` §8 (rules R-01…R-40 and the "decided, do not re-propose" list), §9 (templates, swipe and haptics specs) and §10 (Spanish glossary) are the single UX rulebook; it prevails over older rows and paragraphs.
  - Templates in `designsystem/` encode the rules (`AppScreen`, `AppEditorScreen`, `AppTopBar` with actions, four sheet archetypes, Name sheet, `FoodSheet`, row components per object, `MetricCard`, `AppMessages`, `AppHaptics`, `OfflineState`, field upgrades); `scripts/check-ux-conventions.sh` runs in CI and fails when a feature reinvents one (warnings first, failures once a domain is migrated).
  - Amends ADR-45: dialogs only for R-08 (delete account, sign out, delete a folder with routines, discard a live workout, discard unsaved changes in a full-screen editor); a custom exercise is deleted at once and "Archivar" becomes an immediate "Eliminar"; swipe-to-delete arms at 50 % with haptics instead of the 90 % rule; no "Deshacer" stays.
  - Amends ADR-41: inside an editor every change, removals included, waits for "Guardar". Amends ADR-24: Preferencias becomes a hub of sections, each with its own "Guardar" (no scope change, same data).
  - Tab roots have their own compact header; screens are titled with the object; actions of the object on screen go in the top bar; the rest bar of the live session moves to the bottom; the Diario shows the meals first with a one-line summary strip; Perfil gets a gear icon and the account actions move to Preferencias → "Cuenta".
  - Dropped: the "Entreno en curso" bar on every tab (audit D12) is not built, now or in Phase 2 unless the user asks. Screenshot tests of the designsystem templates are adopted.
  - Process: `ux-reviewer` works from a pattern map, the rules, a mandatory sweep and the glossary, and reviews the implemented diff in parallel with pr-reviewer; a convention stated at a checkpoint becomes a rule in `05` (not a table row); a repeated UX finding becomes a guard.
- **Consequences:** affects `05-design-system.md`, `epics/E9-finalization.md` (US-56 and the refinement table), `CLAUDE.md`, `06-agent-workflow.md` and the agents `ux-reviewer`, `epic-planner`, `planner` and `compose-ui-developer` (separate workflow task, wording approved in chat first), `tasks/plan.md` and the E9 breakdown.
- **Approval:** 2026-10-26, user (decisions); wording pending the user's OK in the docs PR.

### ADR-70 · "Actualizar automáticamente" is one global setting (E9, US-36 amended; E6-B Q1)
- **Context:** E6-B Q1 (2026-10-24): the user wants the automatic routine update after a workout as one setting for all routines, not a switch per routine. Audit R-rule: behaviour that applies to everything lives in Preferencias.
- **Decision** (option A):
  - `training_preferences` gains `auto_update_routine boolean not null default false`; Preferencias → "Entrenamiento" shows one switch "Actualizar automáticamente", off by default.
  - The per-routine switch in the routine builder and the column `routines.auto_update_routine` go away; `save_routine` stops accepting it. Existing per-routine values are dropped (the global setting starts off for everyone).
  - When finishing a workout from a routine, the update is applied without asking only if the global setting is on; otherwise the existing "Actualizar rutina" choice is offered as today.
- **Consequences:** affects `03-data-model.md` (`training_preferences`, `routines`, `save_routine`), `epics/E6-training-depth.md` (US-36 notes), `09-open-questions.md` (E6-B Q1 resolved) and the E9 tasks T-124 (migration, builder, finish flow, temporary switch) and T-127 (switch in the "Entrenamiento" section). The data-model and epic text are updated in T-124's PR.
- **Approval:** 2026-10-26, user (option A).

### ADR-71 · Diario layout restored, sheets close only with their X, icons for clear actions (E9-A feedback; amends ADR-69)
- **Context:** Checkpoint E9-A feedback (2026-10-26): the meals-first Diario layout of T-121 (requested earlier) is worse than the previous one; the X already closes every sheet, so the drag handle and swipe-down dismissal add nothing; the text quick actions of the food search take a full row of height.
- **Decision:**
  - The Diario goes back to its pre-T-121 layout (dashboard on top, meals below); the one-line summary strip goes away. The rest of T-121 stays (food details sheet, swipe, day picker with "Hoy", titles).
  - New rule R-38: a bottom sheet closes only with its X, the system back button or, where it has one, "Cancelar". Dragging it down or tapping outside does not close it, and no sheet shows the drag handle (the small horizontal line). R-10 is amended accordingly.
  - New rule R-39: where an icon clearly represents an action (photo, add, remove, retry, scan), the control is an icon with a contentDescription; text buttons are for actions with no clear icon. The food search quick actions (scan, photo, create) go back to icons in the "Buscar alimentos" row.
  - In the add-food sheet the label above the meal chips is "Añadir a:" (it clashed with the "Comida" meal chip).
- **Consequences:** `05-design-system.md` (R-10, R-38, R-39), a guard in `check-ux-conventions.sh`, task T-131 (`tasks/todo.md`); the E9-A checkpoint is re-presented after it.
- **Approval:** 2026-10-26, user (chat; "Añadir a:" chosen for the label).

### ADR-72 · Amount from a photo always gives a best estimate; confidence is a 1–10 scale (E9-A feedback, T-131; amends ADR-64)
- **Context:** Checkpoint E9-A (2026-10-26): the weight estimate always fails with "No hemos podido estimar la cantidad". The feature exists for users without a scale, so a `null` estimate is the worst outcome. The root cause is not confirmed (the agents have no logs or API key); the user will retry after the merge and deploy and read the logs.
- **Decision** (all inside T-131):
  - `weight` mode must always return a best estimate: the prompt has no null escape and `estimated_amount` is non-null in the response schema. The normalizer still rejects a non-number or a value <= 0 with 422 `NO_FOOD_RECOGNIZED` ("No hemos podido estimar la cantidad" + "Reintentar"). Dish, label and barcode are unchanged.
  - The `food` context of weight mode carries only `name`, `brand?` and `base_unit` (no kcal or macros), in the client DTO and in the function validation.
  - In `weight` mode `confidence` is an integer 1–10 returned by the model (the prompt defines the scale: 1 = pure guess, 10 = near certain); the app shows it in the amount-photo flow as a colour scale red to green plus the text "Confianza 7/10" (new rule R-41). Dish, label and barcode keep the `low|medium|high` enum (`FoodPhotoConfidence`), which no screen displays.
  - The function logs diagnostics only when the optional secret `FOOD_PHOTO_DEBUG` is set (unset = off, no redeploy): mode, model, elapsed ms, provider status, response shape, rejection reason, raw `estimated_amount` and `confidence`; never the photo, tokens, user note or a full body.
- **Consequences:** `03-data-model.md` (`food-photo` weight contract), `epics/E7-...` (US-54 technical notes), `epics/E9-finalization.md` (US-56 scenarios), `05-design-system.md` (R-41, confidence colour scale), `08-environment-setup.md` (optional secret), task T-131.
- **Approval:** 2026-10-26, user (chat).
