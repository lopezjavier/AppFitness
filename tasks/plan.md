# Implementation Plan: E1 · Infrastructure and app skeleton → `v0.1.0`

## Overview
Turn the E0 repository into a buildable Kotlin Multiplatform Android app with a deployed Supabase baseline. E1 is done when: both flavors build locally and in CI behind required checks (`android-ci`, `supabase-ci`); `profiles` with RLS, the `health` function and the seed user are deployed and tested; the Kinetic Dark design system is implemented with a debug-only catalog; and the installable `prodDebug` APK has the full 4-tab navigation in a WIP state (contextual FAB, adaptive rail, offline banner). No login and no business features (E2+).

Source of truth: `planning/epics/E1-infrastructure-skeleton.md` (US-01…US-04 with Gherkin), `planning/02-architecture.md`, `planning/03-data-model.md`, `planning/05-design-system.md`. Task list: `tasks/todo.md` (T-015…T-030).

## Architecture Decisions
- **Android app module separate from the KMP UI module (ADR-19, approved by the user on 2026-10-12; T-015 checks the official docs and writes it).** AGP 9 no longer supports `org.jetbrains.kotlin.multiplatform` together with `com.android.application` in the same module. Proposed layout: `androidApp` (`com.android.application`: `MainActivity`, `local`/`prod` flavors, `BuildConfig`, network security config, flavor-only code such as the component catalog entry and, in E2, "Test login") + `composeApp` (KMP library: Compose UI in `commonMain`) + `shared` (KMP library: domain/data). Flavor values reach `composeApp` through an `AppConfig` data class provided by `androidApp` (Koin). Consequences: `:composeApp:assembleProdDebug` / `installLocalDebug` become `:androidApp:…`; T-015 updates `CLAUDE.md`, `planning/02-architecture.md` §2/§5, `planning/06-agent-workflow.md` §4 and the E1 epic. If T-015 finds that the pinned AGP still supports the original layout without deprecation, it keeps `composeApp` as the application module and does not write the ADR.
- **Required checks that always report (ADR-20, approved by the user on 2026-10-12; written in T-017).** A required check whose workflow is skipped by `paths`/`paths-ignore` stays "Expected" forever and blocks the PR. So `android-ci` and `supabase-ci` run on every PR; a first `changes` job (path filter) decides, and the heavy job is skipped through `if:`. A skipped job counts as passing. This replaces the `paths-ignore: supabase/**` from the US-01 technical notes and "required when applicable" in `02-architecture.md` §8.
- **CI builds without secrets.** Dependabot PRs do not get repository secrets. The Gradle build falls back to blank `BuildConfig` values when the properties or environment variables are missing, so `android-ci` stays green on Dependabot PRs. Only the delivery APK (built by the PM at epic closure, with `local.properties`) needs real values. A unit test checks the local URL; the prod URL check only runs when the value is present.
- **Seed user is local only.** `supabase db push` does not run `seed.sql`, so `test@appfitness.local` never reaches production. The `deploy-supabase.yml` from E0 must not add `--include-seed`.
- **Design system before the shell, shell before most components.** T-020 builds the theme. T-021/T-022 build the navigable shell (with the components it needs). The component catalog then hangs off Profile from the start (T-023), so the US-03 catalog scenario is testable from its first task and no temporary entry point is needed.
- **Catalog only in `local`.** The catalog entry is contributed by the `local` flavor of `androidApp` (for example a flavor-provided `AppConfig.showComponentCatalog` or flavor source set registration). A `testProdDebugUnitTest` test asserts it is off in `prod`.
- **Haptics in components** use Compose `LocalHapticFeedback` (multiplatform). The domain `Haptics` port from `02-architecture.md` is introduced when a feature needs it (E5).
- **One active task at a time** (CLAUDE.md). Backend tasks (T-018/T-019) do not depend on Android tasks, so they could also run between T-016 and T-017 if CI is blocked.

## Task List

### Phase 1: Buildable project and Android CI (US-01) — backend/infra flow
- [x] T-015: KMP project skeleton with `local`/`prod` flavors
- [x] T-016: ktlint and detekt
- [x] T-017: `android-ci` workflow as a required check

### Checkpoint E1-A: CI gate
- [x] A PR shows `gitleaks` and `android-ci` green and cannot merge if either fails; both flavors build locally.

### Phase 2: Supabase baseline (US-02) — backend flow
- [x] T-018: `profiles` migration, RLS, pgTAP, seed user and `supabase-ci`
- [x] T-019: `health` Edge Function and `keepalive` workflow

### Checkpoint E1-B: Backend live
- [x] `deploy-supabase` applied the migration and `health` in production; `keepalive` green by hand; `supabase-ci` required.

### Phase 3: Theme and navigable shell (US-03, US-04) — UI flow
- [x] T-020: Kinetic Dark theme foundation (tokens, font, typography, shapes)
- [x] T-021: 4-tab navigation shell with WIP screens
- [x] T-022: Top App Bar with section title and avatar

### Checkpoint E1-C: First installable APK
- [x] 👤 The user installs `localDebug` or `prodDebug`, moves between the 4 tabs and checks the style on the phone.
- [x] T-031 (corrective, ADR-22): 3 tabs, Profile as a full screen from the avatar, no pill on the active tab

### Phase 4: Component library (US-03) — UI flow
- [ ] T-023: Debug-only component catalog + buttons, card, chips and segmented selector
- [ ] T-024: Data visualization components (ring, macro bar/slider, metric tile, trend pill)
- [ ] T-025: Workout input components (number input, set checkbox, set row, rest timer)
- [ ] T-026: Empty/error states and accessibility sweep of the catalog

### Checkpoint E1-D: Design system complete
- [ ] Every component from `05-design-system.md` §6 is in the catalog; US-03 Gherkin green in Maestro.

### Phase 5: Shell completion (US-04) — UI flow
- [ ] T-027: Contextual FAB with WIP bottom sheets
- [ ] T-028: Adaptive layout with NavigationRail on tablets
- [ ] T-029: Connectivity observer and "Sin conexión" banner

### Checkpoint E1-E: Epic complete
- [ ] T-030: E1 closure (regression, APK delivery, user feedback, tag `v0.1.0`)

## Dependencies

```
T-015 ─► T-016 ─► T-017 ─┬─► T-018 ─► T-019 ───────────────────────────────────────────┐
                         └─► T-020 ─► T-021 ─► T-022 ─► T-023 ─► T-024 ─► T-025 ─► T-026 │
                                                                                   │     │
                                                  T-027 ◄──────────────────────────┘     │
                                                    └─► T-028 ─► T-029 ─► T-030 ◄────────┘
```
- T-018/T-019 only need T-017 (so `android-ci` already reports on their PRs and skips itself for `supabase/**`); they can be interleaved with the UI tasks.
- T-027…T-029 technically only need T-022; they come after T-026 so the FAB, sheet and banner are reviewed against a complete catalog.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| AGP 9 / KMP module layout differs from the plan (application + multiplatform in one module) | High | T-015 goes first and verifies against the official AGP and Kotlin docs; ADR-19 (`androidApp` + `composeApp` + `shared`); commands and docs updated in the same PR |
| Required check never reports on PRs that skip its paths → PR blocked | High | Always-run workflows with a `changes` job and job-level `if:` (ADR-20) |
| Dependabot PRs fail `android-ci` because they have no secrets | Medium | Blank `BuildConfig` fallbacks; no step needs a secret to pass |
| Library versions incompatible (Kotlin, Compose MP, AGP, Navigation, Koin, supabase-kt) | Medium | Pin the latest stable set in T-015 and check the Compose MP / Kotlin compatibility table; supabase-kt is only added when it is needed (E2), but its support for `sb_publishable_` keys is checked when it is pinned (ADR-17) |
| Compose UI tests (touch targets) need Robolectric or an emulator | Medium | T-026 decides the mechanism in its plan (`runComposeUiTest` on the JVM if it works with the pinned versions; otherwise Maestro + UX review), and logs the choice |
| Seed `auth.users` insert breaks with a CLI upgrade | Low | Seed covered by a pgTAP test in T-018; CLI pinned (`package.json`) |
| `supabase test db` fails with no tests on an empty branch | Low | T-018 adds the CI together with the first pgTAP tests |
| Maestro cannot toggle connectivity on the emulator | Low | Use Maestro's airplane mode commands; if unreliable, cover the banner state with a ViewModel test and check it by hand (logged in T-029) |
| Plus Jakarta Sans files committed without a license | Low | Bundle the OFL `.ttf` files from the official source together with `OFL.txt` |

## Open Questions
- None blocking. ADR-19 (separate `androidApp` module) and ADR-20 (always-run required checks) approved by the user on 2026-10-12.

---

# Archive: E0 · Project setup (completed 2026-10-12)

### Overview
Get the repository, GitHub, Supabase and Google OAuth ready so Claude Code can implement Phase 1 with the `planning/06-agent-workflow.md` flow. When E0 is done there will be: a local repo linked to `https://github.com/lopezjavier/AppFitness` (public), `main` protected by required checks, Claude Code configuration (`CLAUDE.md`, subagents, log), a production Supabase project with a GitHub Actions pipeline that automatically deploys `supabase/**` when merged into `main`, and Google authentication configured in Google Cloud and Supabase (ready for US-05).

Source of truth: `planning/` (especially `epics/E0-setup.md`, `02-architecture.md`, `08-environment-setup.md`). Task list: `tasks/todo.md`.

### Architecture Decisions
- **Original documents outside the repo**: their content is absorbed into `planning/` and `CLAUDE.md`; they are not versioned.
- **Public repo** (ADR-13): rulesets, environments, secret scanning + push protection and unlimited Actions on the Free plan. The repo never contains secrets. All rights reserved (`LICENSE`).
- **English repository, Spanish UI** (ADR-16).
- **applicationId** `io.github.lopezjavier.appfitness`; `local` flavor with the `.local` suffix (ADR-14). Two Android OAuth clients are registered in Google Cloud.
- **Supabase deployment in E0** (previously planned in US-02): `deploy-supabase.yml` is created and validated now with an empty project; US-02 only adds migrations, tests and `supabase-ci`/`keepalive`.
- **Secrets are entered by the user** in their terminal (`gh secret set …`) or in the web consoles; they never go through the chat or versioned files. Claude only verifies names (`gh secret list`).
- **Squash merge only**, automatic branch deletion; required approvals = 0 (authors cannot approve themselves), checks are the gate.
- **Production Auth configured in the Supabase dashboard** (Google on, Email off). `supabase/config.toml` only describes the local environment (Email on for "Test login" + optional Google via `env()`).
- **Tasks in `tasks/todo.md`** (`planning-and-task-breakdown` skill convention); `.workflow/` (plan of the active task, PR body, audit log) is local and not versioned.
- In E0 the PM runs the tasks directly (no Planner/UX/QA). From **T-008** on, every change goes through a PR because `main` is already protected.

### Task List

#### Phase A: Local repo and Claude Code
- [x] T-001: Verify local environment and re-authenticate `gh`
- [x] T-002: Repo content and git base files
- [x] T-003: Documentation ready for a public repo
- [x] T-004: CLAUDE.md, execution log and skills review
- [x] T-005: Claude Code subagents

#### Checkpoint A
- [x] Clean `git status` after the initial commit; a new Claude Code session lists the 7 project subagents; user review.

#### Phase B: GitHub
- [x] T-006: Connect to the GitHub repo and first push
- [x] T-007: Security and `main` protection

#### Checkpoint B
- [x] Direct push to `main` rejected; a test PR shows the `gitleaks` check; secret scanning enabled.

#### Phase C: Supabase and deployment with GitHub Actions
- [x] T-008: Production Supabase project and `supabase/` initialization
- [x] T-009: `production` environment and deployment secrets
- [x] T-010: `deploy-supabase.yml` workflow

#### Checkpoint C
- [x] Manual and merge-triggered `deploy-supabase` runs green against the real project.

#### Phase D: Google OAuth
- [x] T-011: OAuth clients in Google Cloud
- [x] T-012: Auth providers in Supabase (prod and local)
- [x] T-013: E0 wrap-up

#### Checkpoint D (E0 closure)
- [x] Production `GET /auth/v1/settings`: `google: true`, `email: false`; DoD of `planning/epics/E0-setup.md` met.

### Dependencies

```
T-001 ─► T-002 ─► T-003 ─► T-004 ─► T-005 ─► T-006 ─► T-007 ─┬─► T-008 ─► T-009 ─► T-010 ─┐
                                                             └─► T-011 ───────────► T-012 ◄┘ (T-012 also needs T-008)
                                                                                     │
                                                                                   T-013
```
T-011 (Google Cloud) can run in parallel with T-008–T-010, but it needs the Supabase project ref (T-008) for the redirect URI.

### Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| A secret gets committed to a public repo | High | `.gitignore` from T-002, push protection + gitleaks (T-007), secrets only via terminal/consoles; if it happens: rotate the secret immediately, deleting the commit is not enough |
| `.claude/skills` junctions cannot be versioned by git | Medium | Version `.agents/skills` + `skills-lock.json`, ignore `.claude/skills/`; the README explains how to recreate the junctions |
| `supabase functions deploy` fails when there are no functions | Medium | The workflow only deploys functions if `supabase/functions/*/index.ts` exists |
| Supabase CLI behavior changes between versions | Medium | Same pinned version locally (`npx supabase@<v>`) and in GHA; Dependabot proposes upgrades |
| Lost DB password or Supabase token | Medium | Store them in a password manager in T-008/T-009 |
| Different debug SHA-1 on another PC | Low | Documented in `08-environment-setup.md`; add another Android client or reuse the keystore |
| Production Auth configured by hand (drift from the docs) | Low | T-012 documents the `curl` verification so it can be repeated |

### Open Questions
- None blocking. Supabase region: proposed `eu-west-3` (Paris) or `eu-central-1` (Frankfurt); first created in `eu-north-1` (Stockholm) and moved to `eu-west-3` (Paris) at the E0 review (T-014).
