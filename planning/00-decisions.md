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

### ADR-24 · Onboarding resume and a single save on Profile (E2) (read-only weight amended by ADR-37)
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
- **Context:** E3 (Phase 1) logs sets as weight × reps only, but the follow-up training epics (**E6** depth, **E7** insights) need set types, RPE, per-exercise metric types, routine folders/tracking and a PR catalogue. Building E3 on the minimal shape would force a schema migration and a live-session refactor later. Separately, the exercise catalogue uses the RepDB free assets (495 exercises: thumbnails, start/peak poses and Spanish instructions), whose licence allows only in-app use with visible attribution.
- **Decision:**
  - The training tables are created **forward-compatible**: the enums and columns added in `03-data-model.md` §5 (marked *(E6)*) ship with E3, with defaults; E3 renders only `weight_reps` / `working` sets, and the live session's set row is built **polymorphic** so E6 extends it without a rewrite.
  - The RepDB free catalogue is **bundled in the APK** (`composeApp/src/commonMain/composeResources/files/exercises/`, ≈16 MB, `library.json`; module and layout fixed by ADR-39); catalogue text and images live in the client, never in the DB or a public CDN.
  - The bundle has no male/female variants: one pose pair per exercise (ADR-39). Animations are a **start↔peak crossfade** (3 s cycle; the free tier has no GIF/video). Attribution "Datos de ejercicios por RepDB (repdb.co)" must be **visible** in the app (full policy in the project's `ATRIBUCION_RepDB.md`).
  - The app's own licence stays **repo-only** (ADR-13); third-party credits (RepDB, Open Food Facts, OSS libraries, fonts) are shown in an **"Acerca de · Licencias"** screen opened from Profile (screen defined in E8 · US-45).
- **Consequences:** `03-data-model.md` §5, `04-business-rules.md` §4, the E3/E6/E7/E8 epic files, `01-phase1-scope.md` and `07-roadmap.md` reflect this. **No schema migration** between E3 and E6.
- **Approval:** 2026-10-17, user.

### ADR-31 · Epic order: training first, three training epics and a closing epic (E3…E8)
- **Context:** the first roadmap ordered the domains Diary → Workout → Progress, split training into "core" (E5), "depth/wrap-up" (E6), "analytics" (E7) and "statistics" (E8), and scattered About/licences and polish across them. The user wants training to lead (it is the app's core value), the "wrapper/wrap-up" framing removed, and the plan to end with one closing epic that also absorbs the UI-refinement bucket.
- **Decision:** reorder and reshape Phase 1:
  - E0 Setup · E1 Infrastructure · E2 Auth/profile (unchanged, US-01…US-08).
  - **E3 Training core** (US-09…US-15, v0.3.0): catalog, routine builder, live session, previous performance, rest timer, history and strength-progress charts.
  - **E4 Food diary** (US-16…US-24, v0.4.0): catalog and OFF cache, search, scanner, manual creation, configurable meals, portions and measures, favourites, diary views, copying, templates and history.
  - **E5 Weight tracking** (US-25…US-27, v0.5.0).
  - **E6 Training depth** (US-28…US-38, v0.6.0).
  - **E7 Training insights** (US-39…US-44, v0.7.0).
  - **E8 Finalization** (US-45 About/licences, US-46 polish/accessibility/tablet/release, plus the UI-refinement bucket, v1.0.0).
  - Stories US-09…US-46 are renumbered contiguously. Dependencies: E3→E2; E4→E2; E5→E2; E6→E3; E7→E3 (charts) and E6 (PRs/warm-up); E8→E1…E7.
- **Consequences:** supersedes the epic order of the earlier roadmap. `01-phase1-scope.md`, `02-architecture.md`, `03-data-model.md`, `07-roadmap.md`, `08-environment-setup.md` and the epic files follow the new numbering; the "Phase 1B" framing and the "wrap-up" epic name are dropped (it is all Phase 1). Version tags: v0.1.0…v0.7.0 at E1…E7, **v1.0.0 only at E8**.
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
  - **Perfil root screen:** the Google photo (placeholder fallback) shown large, the user's name below it, a button that opens a full-screen **"Preferencias"** screen (back arrow) with the current editable Profile content (personal data, weight, energy, macros, one "Guardar"), and "Cerrar sesión" and "Borrar cuenta" on the root. Later epics add statistics entry buttons on this root: content planned for the Progreso tab (E5 weight chart and history, E3/E7 strength and training insights, any analytics) becomes entries reached from Perfil, each a full-screen destination with a back arrow.
  - **Name:** taken from Google `full_name`. If it is missing, onboarding step 1 "Sobre ti" asks "¿Cómo te llamas?" (only then). It is always editable in Preferencias.
  - **Weight is editable in Preferencias.** Saving a changed weight records today's manual `body_measurements` weight, replacing today's manual row if one exists; earlier weights stay as history. "Peso actual" shows the latest weight from any source; a later Health Connect sync shows the newer value, which stays editable. This amends ADR-24's read-only weight.
  - **Copy:** "TDEE: %s kcal" becomes "Gasto diario estimado: %s kcal" with the helper line "Calorías que quemas al día según tu cuerpo y tu actividad." (onboarding and Preferencias).
- **Consequences:** `01` FR-5.1, `02`, `03` §2/§4, `05` §5–6, `07`, the E2, E3, E5 and E1 epic notes and `tasks/` are updated; the Profile code (T-037) is reworked in T-041; the Edad/Altura display bug is fixed first in T-040.
- **Approval:** 2026-10-19, user (Checkpoint E2-B feedback).

### ADR-41 · Meals are deleted when unused, never disabled (E4, T-067)
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

### ADR-43 · Store the full Open Food Facts product and show micronutrients (E4, T-068)
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

### ADR-45 · Interaction rules shared by every screen (E4, T-072)
- **Context:** Checkpoint E4-B/E4-C (user, 2026-10-21): the same UX defects were reported on several screens (swipe fires too easily, "Deshacer" added after the user had asked for none, edits saved per action, differing texts for the same concept). Fixing them screen by screen leaves the app inconsistent.
- **Decision:** `05-design-system.md` §8 holds generic rules, enforced by ux-reviewer on every UI task:
  - **Delete:** runs at once, with no confirmation dialog and no "Deshacer" (a deliberate swipe or tap is the confirmation). Deleting from a details screen closes it. Confirmation dialogs stay only for account-level destructive actions ("Borrar cuenta").
  - **Swipe-to-delete:** one shared designsystem component; the action fires only when the row is dragged to the end of its travel (heavy, slow); a shorter drag snaps back.
  - **Editing:** an edit screen has a single "Guardar" that saves every change together; nothing is saved per action; renaming is part of that draft. Delete is the only action that is immediate.
  - **Lists:** a row opens its details; editing happens from the details. Rows of the same kind show the same information on every screen.
  - **Wording:** one concept, one label across screens and across periods (e.g. day, week and month averages are all "Media diaria").
  - **Sweep:** when a task changes one of these behaviours, the ux-reviewer searches the app for every other instance and the plan includes them.
- **Consequences:** supersedes the "Deshacer" of T-069; US-21/US-24, `tasks/todo.md` (T-072, T-073), `05-design-system.md` §8 and `.claude/agents/ux-reviewer.md` reflect this. The E8 refinement item about swipe-to-delete is consumed by T-072.

### ADR-46 · Text food search is limited to Spain (E4, T-074)
- **Context:** User feedback (2026-10-21): the text search returns poor results, many from other countries or without a Spanish name.
- **Decision:** `searchOff` asks Open Food Facts for products sold in Spain (`tagtype_0=countries`, `tag_contains_0=contains`, `tag_0=spain`) and keeps only hits whose `countries_tags` includes `en:spain` and that have a Spanish name (`product_name_es`, or `lang = "es"` with `product_name`). The default v1 order (`unique_scans_n`) is kept; no `sort_by`. Requests stay unfiltered by `fields` (ADR-43). Barcode lookup is unchanged: a scanned product always resolves. Paging relies on the raw OFF page count, not the filtered one.
- **Consequences:** products without a Spanish name no longer appear in text search (they remain reachable by barcode); cached search results from before the change are purged or re-keyed.
