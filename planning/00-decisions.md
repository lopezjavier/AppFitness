# Decisions and findings

## 1. Closed decisions (ADR)

Format: context → decision → consequences. A decision is only changed by adding a new ADR that supersedes it.

### ADR-01 · Connectivity: online only
- **Context:** the user's gym has Wi-Fi; an offline-first model adds bidirectional sync and conflict resolution.
- **Decision:** Supabase is the single source of truth. No Room or local database.
- **Consequences:** a "Sin conexión" (offline) banner and disabled write actions (with retry) when there is no network. A workout session is created in the DB when it starts and every set is persisted when checked, so it survives an app restart. Only exception: during an open workout session, sets checked while offline are retried from memory when the connection returns (US-20); there is no local persistence. Purely local preferences (e.g. last chart filter) may use DataStore.

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

### ADR-10 · 4-tab navigation (amended by ADR-22: 3 tabs + Profile from the avatar; back to 4 tabs by ADR-26)
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
- **Context:** `planning/` did not fix a minimum Android version. Health Connect (`androidx.health.connect:connect-client`, US-09 in E4) requires API 26.
- **Decision:** `minSdk = 26` (Android 8.0) in all modules; `compileSdk`/`targetSdk` = the highest stable API supported by the pinned AGP (37 with AGP 9.3).
- **Consequences:** no runtime checks are needed for Health Connect availability by API level below 26. Raising `minSdk` later is cheap.
- **Approval:** 2026-10-12, user.

### ADR-22 · 3 tabs; Profile opens from the avatar (navigation part superseded by ADR-26)
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

### ADR-24 · Onboarding resume and a single save on Profile (E2)
- **Context:** the E2 breakdown found four product gaps in US-06…US-08: what an interrupted onboarding shows on restart, whether onboarding includes the macro sliders, how Profile saves (the Gherkin had "Guardar" and "Guardar objetivos", the mockup one button), and whether the current weight is editable on Profile before weight logging exists (E4).
- **Decision:**
  - Resuming onboarding restarts at the first step with every step **prefilled** from the saved values. Finishing the last biometric step again **updates** the onboarding weight entry in `body_measurements`; it never adds a second row.
  - Onboarding has **no macro sliders**: the goal step saves the default split 30/45/25 (`04-business-rules.md` §1.5), adjusted later on Profile.
  - Profile has **one "Guardar" button** for the whole screen (personal data, strategy and macros). Saving recalculates the TDEE with the latest weight and **stores** the new daily target in `nutrition_goals` (`04-business-rules.md` §1.4).
  - "Peso actual" is **read-only** on Profile in E2 (latest `body_measurements` row); weight editing arrives with US-15 (E4).
- **Consequences:** the E2 epic Gherkin is updated (resume prefill, re-save without duplicates, onboarding saving with the default split, "Guardar" on Profile, read-only weight). The onboarding weight entry must be identifiable to update it (chosen in the T-035 plan, e.g. the user's earliest `manual` row). Profile's save writes `profiles` and `nutrition_goals` together, so the planner picks an order or an RPC that never leaves them inconsistent.
- **Approval:** 2026-10-14, user (answers to the E2 breakdown questions Q1–Q4).

### ADR-25 · Calorie safety floor by sex (E2)
- **Context:** the calorie target had one safety floor of 1200 kcal/day for everyone. Common nutrition guidelines put the minimum at about 1200 kcal/day for women and 1500 kcal/day for men.
- **Decision:** the floor depends on the profile's sex: **1200 kcal/day for women, 1500 kcal/day for men**. When the calculation gives less, the target is set to the floor and the same safety warning is shown.
- **Consequences:** `04-business-rules.md` §1.4, the US-07 "Safety floor" scenario and T-036 use the per-sex floor; recalculating on Profile (sex is editable there) applies the floor for the new sex.
- **Approval:** 2026-10-14, user.

### ADR-26 · Perfil is the 4th tab, its icon is the Google photo (E2)
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
  - Every Gherkin UI scenario is covered by a deterministic host (Robolectric) test in `androidHostTest`, written with the task (frontend-developer rule 13).
  - The app is verified by hand on real devices with `python scripts/deliver-apk.py`.
  - A defect found in manual testing becomes a corrective `fix(T-###)` task; no time is spent debugging a UI-automation script.
  - The Maestro infrastructure is removed: `tests/e2e/`, the `check-e2e-tags.sh` guard, the Maestro command rows and the `qa` GitHub-issue tracking.
- **Consequences:** supersedes ADR-23 and drops the QA step from ADR-04. `CLAUDE.md`, `planning/06-agent-workflow.md`, the agent files and `README.md` are updated; the qa-engineer agent, `tests/e2e/` and `check-e2e-tags.sh` are removed.
- **Approval:** 2026-10-17, user.

### ADR-28 · One task per session; per-epic audit log
- **Context:** after E1 the rule was to chain tasks up to the next 👤 checkpoint with the user compacting by hand after about two tasks (`retrospectives/E1.md`). That is the last unbounded context growth in a flow that is otherwise optimised for lean context (subagent delegation, ≤10-line replies, ADR-27); `/compact` was observed to degrade performance and quality drops as the window grows. The local audit log (`.workflow/execution.log`) also grows with every epic.
- **Decision:**
  - A task runs in its own session. When its PR is merged and `COMPLETED` is logged, the PM stops and asks the user to start a fresh session (`/clear` or a new terminal) for the next `T-###`. It never starts a second task in the same session and never relies on `/compact`.
  - The resume point is the "Next" pointer in `CLAUDE.md` plus the first unchecked task in `tasks/todo.md`.
  - `.workflow/execution.log` holds only the current epic. At epic closure, after the retrospective, the epic's lines move to `.workflow/archive/execution-E#.log` (local, not versioned).
  - The PM never loads the whole log or an archive: it greps by `[T-###]`, `[E#]` or timestamp.
- **Consequences:** supersedes the E1-retro rule "chain tasks up to the checkpoint; the user compacts". `CLAUDE.md` (Autonomy, working files, epic closure) and `planning/06-agent-workflow.md` (§2.1, §3, §6) are updated. The checkpoints are unchanged (1–3 per epic): the *session* boundary becomes per task, the *feedback* boundary stays per checkpoint, so the user runs `/clear` at each task boundary instead of `/compact` every two tasks.
- **Approval:** 2026-10-17, user.

---

## 2. Findings from the review of the original documents

### Product and stories (original user stories and frontend features)
| # | Issue | Resolution |
|---|---|---|
| 1 | 3 tabs + profile via avatar (features) vs 4 tabs (stories, mockups) | ADR-10, revised by ADR-22 (3 tabs) |
| 2 | US-05 used `navigator.wakeLock.request('screen')`, a web API | `Modifier.keepScreenOn()` / `FLAG_KEEP_SCREEN_ON` while the live session is visible (US-20) |
| 3 | Health Connect: manual button (requirements) vs automatic background sync (US-09) | ADR-08 |
| 4 | Google Code Scanner cannot be shown inside a Bottom Sheet | ADR-07 |
| 5 | Estimated 1RM was Phase 2 (AI) and also in the Phase 1 mockups | ADR-09 |
| 6 | US-01 proposed anonymous authentication and only 4 generic tables | ADR-02 and `03-data-model.md` |
| 7 | "Search the backend database" vs Open Food Facts | ADR-06 |
| 8 | US-03 example: 175 g P / 260 g C / 65 g F = 2,325 kcal, not 2,400 | Examples recalculated (kcal = 4·P + 4·C + 9·F) |
| 9 | Undefined calculation rules (TDEE, strategy adjustment, auto-balance, weekly rate) | `04-business-rules.md` |
| 10 | Missing stories: login, onboarding, text search, diary editing, template management, manual weight logging, exercise catalog, routine builder, history, tablet | Added (US-01…US-25 in `epics/`) |
| 11 | Tablet not covered by any story | US-04 (adaptive shell) and US-25 (review) |
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
