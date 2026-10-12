# Decisions and findings

## 1. Closed decisions (ADR)

Format: context → decision → consequences. A decision is only changed by adding a new ADR that supersedes it.

### ADR-01 · Connectivity: online only
- **Context:** the user's gym has Wi-Fi; an offline-first model adds bidirectional sync and conflict resolution.
- **Decision:** Supabase is the single source of truth. No Room or local database.
- **Consequences:** a "Sin conexión" (offline) banner and disabled write actions (with retry) when there is no network. A workout session is created in the DB when it starts and every set is persisted when checked, so it survives an app restart. Only exception: during an open workout session, sets checked while offline are retried from memory when the connection returns (US-20); there is no local persistence. Purely local preferences (e.g. last chart filter) may use DataStore.

### ADR-02 · Authentication: Google Sign-In
- **Decision:** Credential Manager (`GetGoogleIdOption`) → ID token → `supabase.auth.signInWith(IDToken)` with the Google provider.
- **Consequences:** no sign-up or password recovery screens. For E2E tests (Maestro cannot automate Google), the `local` flavor includes a "Test login" button using email/password against local Supabase and a seeded user. In the production Supabase project the email provider is disabled.

### ADR-03 · Scope: original requirements only
- **Decision:** the Stitch mockups are used as a **style** reference. Features that appear in the mockups but not in the original requirements are out of Phase 1 (see `01-phase1-scope.md`).

### ADR-04 · Adaptive workflow
- **Decision:** tasks with UI follow the full flow (Planner → UX → Dev → QA Maestro → PR Creator → PR Reviewer). Infrastructure/backend tasks without UI skip UX and Maestro and are validated with pgTAP, Edge Function tests and CI.
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

### ADR-10 · 4-tab navigation
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

---

## 2. Findings from the review of the original documents

### Product and stories (original user stories and frontend features)
| # | Issue | Resolution |
|---|---|---|
| 1 | 3 tabs + profile via avatar (features) vs 4 tabs (stories, mockups) | ADR-10 |
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
| 24 | Maestro cannot complete Google Sign-In | "Test login" button in the `local` flavor (ADR-02) |
| 25 | Gradle commands of a plain Android project, no flavors | KMP commands with flavors (`06-agent-workflow.md`) |
| 26 | CI only deployed the backend; `setup-cli` with `version: latest`; reference to `analyze-meal` (Phase 2) | 4 workflows with pinned versions (`02-architecture.md`) |
| 27 | Documents in the root and a misspelled file name | Content absorbed into `planning/` and `CLAUDE.md`; originals kept outside the repository |
