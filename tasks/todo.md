# Tasks — Phase 1

Plan: [tasks/plan.md](plan.md) · Planning: [planning/](../planning/README.md) · Log: `.workflow/execution.log` (local)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Epic status: E0 ✅ · E1 ☐ · E2 ☐ · E3 ☐ · E4 ☐ · E5 ☐ · E6 ☐

---

# E0 · Project setup

## T-001: Verify local environment and re-authenticate `gh`

**Description:** Check that the tools from `planning/08-environment-setup.md` §1 are installed and that `gh` has a valid session for `lopezjavier`. The current `gh` session has an invalid token.

**Acceptance criteria:**
- [x] 👤 `gh auth login -h github.com` completed (scopes `repo`, `workflow`, `read:org`); `gh auth status` OK for `lopezjavier`. *(2026-10-12)*
- [x] PM: report with the version of each tool (JDK 21 (ADR-18), Android SDK/`adb`, Docker, Node/`npx supabase`, Deno, git, gh, Maestro) and the list of what is missing. *(2026-10-12: JDK 21.0.8 (Temurin; accepted in ADR-18), Android SDK + adb 1.0.41 installed but not on PATH, Docker 29.8.2, Node 22.17.1, Supabase CLI 2.120.0, git 2.50.1, gh 2.76.1. Missing: Deno, Maestro, `ANDROID_HOME`/`JAVA_HOME`.)*
- [x] 👤 Missing tools installed (Docker/Supabase CLI block T-008; the rest can wait until E1). *(2026-10-12: Deno 2.9.7, Maestro 2.11.0, `JAVA_HOME`/`ANDROID_HOME`/PATH set; AVD `Medium_Phone_API_37.0` with Google Play)*

**Verification:**
- [x] `gh auth status` and `gh repo view lopezjavier/AppFitness` respond without errors. *(2026-10-12)*
- [x] `java -version`, `docker info`, `npx supabase --version`, `deno --version`, `maestro --version`, `adb version` run. *(2026-10-12)*

**Dependencies:** None
**Files likely touched:** none
**Estimated scope:** XS

---

## T-002: Repo content and git base files

**Description:** Gather in the local `lopezjavier/AppFitness` repository (empty, `main` branch, `origin` configured) everything the project needs. Add the base files so secrets and build artifacts are never versioned and line endings are consistent between Windows and the Linux GHA runners.

**Acceptance criteria:**
- [x] In the repo: `planning/`, `tasks/`, `.agents/`, `skills-lock.json` and `design/stitch/`. *(2026-10-12; `planning/` copy verified by hash.)*
- [x] The original high-level documents stay **outside the repo**: their content is absorbed into `planning/`.
- [x] `.claude/skills/` recreated as Windows junctions to `.agents/skills/`.
- [x] Skills versioning: `.agents/skills/` + `skills-lock.json` are versioned; `.claude/skills/` is ignored and the README explains how to recreate the junctions.
- [x] All repository content in English (ADR-16); `LICENSE` (all rights reserved). *(2026-10-12)*
- [x] `.gitignore` covers: `local.properties`, `.env`, `.env.*` (except `*.example`), `supabase/.env*` (except `*.example`), `*.jks`, `*.keystore`, `.gradle/`, `build/`, `.idea/`, `*.iml`, `.kotlin/`, `captures/`, `.DS_Store`, `.cxx/`, `supabase/.temp/`, `supabase/.branches/`, `.workflow/` (PM local working files), `.claude/skills/` and `.claude/settings.local.json`.
- [x] `.gitattributes`: `* text=auto`, `gradlew text eol=lf`, `*.sh text eol=lf`, `*.bat text eol=crlf`, binaries (`*.png`, `*.jar`, `*.ttf`) as `binary`.
- [x] Project `README.md`: what it is, features, stack, structure, how it is developed, setup, license and security. *(2026-10-12)*

**Verification:**
- [x] `git status` shows no file that should be ignored (create a test `local.properties` and check it does not appear). *(2026-10-12)*
- [x] `git check-attr -a gradlew` shows `eol: lf`. *(2026-10-12)*
- [x] In a Claude Code session opened in the repo, `/planning-and-task-breakdown` (or another skill) is available, confirming the junctions work. *(2026-10-12)*

**Dependencies:** T-001
**Files likely touched:** `.gitignore`, `.gitattributes`
**Estimated scope:** S

---

## T-003: Documentation ready for a public repo

**Description:** Check that the versioned documentation does not expose personal data and that its references are valid without the original documents.

**Acceptance criteria:**
- [x] No personal paths (`C:\Users\<name>\...`) in `planning/`, `tasks/`, `CLAUDE.md` or `README.md`: replaced with repo-relative paths or `%USERPROFILE%`. *(2026-10-12)*
- [x] Mentions of the original documents in `planning/` are historical context only ("kept outside the repository"), never links. *(2026-10-12)*
- [x] No real emails or identifiers (the OFF User-Agent stays as `<contact email>`). *(2026-10-12; the public GitHub handle and the Supabase project ref are not secret)*

**Verification:**
- [x] `grep -rniE "C:\\\\Users\\\\[a-z]|@gmail" planning/ tasks/ CLAUDE.md README.md` returns nothing. *(2026-10-12)*
- [x] The relative links in `planning/README.md`, `CLAUDE.md`, `README.md` and the epic files resolve to existing files. *(2026-10-12)*

**Dependencies:** T-002
**Files likely touched:** `planning/*.md`, `tasks/*.md`, `CLAUDE.md`
**Estimated scope:** S

---

## T-004: CLAUDE.md, execution log and skills review

**Description:** `CLAUDE.md` (already created) supersedes the original process documents and is intentionally short: it is loaded in every session and points to `planning/` for details. The audit log is local (`.workflow/` is not versioned because `main` is protected). The `addyosmani/agent-skills` skills installed in `.agents/skills/` had to be reviewed before being trusted.

**Acceptance criteria:**
- [x] `CLAUDE.md` in the root with: sources of truth, PM role, adaptive flow, logging, rules, commands, epic closure and status. *(2026-10-12)*
- [x] `.workflow/execution.log` created (local, ignored) with the first entry `[PM] [T-004] [COMPLETED]`. *(2026-10-12; started from T-001)*
- [x] Review of the 7 skills: generic, with web examples (npm, React, Jest) and references to non-installed files; their principles are applied with the project's commands. Resolved in `CLAUDE.md` (Skills section and precedence order). *(2026-10-12)*

**Verification:**
- [x] A new Claude Code session in the repo correctly summarizes its role and where the tasks are (manual test by the user). *(2026-10-12: the session that ran E0 recognized its PM role, the 7 subagents and `tasks/todo.md`)*

**Dependencies:** T-003
**Files likely touched:** `CLAUDE.md`, `.workflow/execution.log`
**Estimated scope:** S

---

## T-005: Claude Code subagents

**Description:** Create the 7 subagents described in `planning/06-agent-workflow.md` §5, adjusted to the real task location and the installed skills.

**Acceptance criteria:**
- [x] `.claude/agents/` contains `planner`, `ux-reviewer`, `frontend-developer`, `backend-developer`, `qa-engineer`, `pr-creator` and `pr-reviewer` with valid frontmatter (`name`, `description`, `tools` with real names). *(2026-10-12)*
- [x] Each agent reads its skill from `.claude/skills/<skill>/SKILL.md` (planner → planning; ux → frontend-ui; developers → TDD + incremental + debugging; QA → debugging; pr-creator → git-workflow; pr-reviewer → code-review).
- [x] Tasks are read from `tasks/todo.md`; the active task's plan goes in `.workflow/current_task.md` and the PR body in `.workflow/pr-body.md`.

**Verification:**
- [x] Frontmatter validated by script: all 7 have `name`, `description` and only valid tools. *(2026-10-12)*
- [x] Asking Claude "Which project subagents do you have available?" in a new session lists all 7 (the `/agents` wizard no longer exists in Claude Code). *(2026-10-12)*

**Dependencies:** T-004
**Files likely touched:** `.claude/agents/*.md` (7 small files, already written)
**Estimated scope:** M

---

### Checkpoint A — Local repo ready
- [x] Initial commit `chore: initial project setup` created (not pushed yet); clean `git status`. *(2026-10-12)*
- [x] Claude Code recognizes the 7 subagents (see T-005). *(2026-10-12)*
- [x] 👤 User review of `CLAUDE.md` and the agents. *(2026-10-12)*

---

## T-006: Connect to the GitHub repo and first push

**Description:** Push the initial commit to `https://github.com/lopezjavier/AppFitness` (public, already created and empty) and add the GitHub base files.

**Acceptance criteria:**
- [x] `origin` (already configured) receives the initial commit with `git push -u origin main`. The remote is empty, so no rebase is needed. *(2026-10-12)*
- [x] `.github/pull_request_template.md` with sections: Summary, Story/Task, Gherkin scenarios covered, Tests run, QA notes/screenshots, Security checklist (no secrets, RLS on new tables). *(2026-10-12)*
- [x] `.github/dependabot.yml` with the `github-actions` ecosystem (weekly). Gradle is added in US-01. *(2026-10-12)*
- [x] Repo description and topics configured (`gh repo edit`). *(2026-10-12)*

**Verification:**
- [x] `git log origin/main --oneline` shows the initial commit. *(2026-10-12)*
- [x] `gh repo view lopezjavier/AppFitness --json visibility` → `PUBLIC`. *(2026-10-12)*

**Dependencies:** Checkpoint A
**Files likely touched:** `.github/pull_request_template.md`, `.github/dependabot.yml`
**Estimated scope:** S

---

## T-007: Security and `main` protection

**Description:** Enable the protections that justify the public repo: secret scanning, a gitleaks check and a ruleset on `main`. From here on every change goes through a PR.

**Acceptance criteria:**
- [x] Repo settings (via `gh repo edit` / `gh api`): squash merge only, automatic branch deletion on merge, auto-merge allowed, wiki and projects disabled. *(2026-10-12)*
- [x] Secret scanning + push protection and Dependabot alerts enabled. *(2026-10-12)*
- [x] `.github/workflows/security.yml`: `gitleaks` job (`gitleaks/gitleaks-action@v3` + `actions/checkout@v7`, pinned by major version; v2 runs on Node 20, removed from GitHub runners on 2026-09-16) on `pull_request` and `push` to `main`.
- [x] `main` ruleset (also stored in `.github/rulesets/main.json` for reference): PR required with 0 approvals, required `gitleaks` check, branches up to date before merging, linear history, no force-push or deletion, no bypass. *(2026-10-12; ruleset id 24923545)*
- [x] This change goes through a PR (first use of the PR → checks → squash flow). *(PR #1)*

**Verification:**
- [x] `git push origin main` with a trivial commit is rejected. *(2026-10-12: GH013)*
- [x] This task's PR shows the `gitleaks` check green and is merged with `gh pr merge --squash --delete-branch`. *(PR #1)*
- [x] `gh api repos/lopezjavier/AppFitness --jq .security_and_analysis` shows secret scanning and push protection `enabled`. *(2026-10-12)*

**Dependencies:** T-006
**Files likely touched:** `.github/workflows/security.yml`, `.github/rulesets/main.json`
**Estimated scope:** S

---

### Checkpoint B — GitHub configured
- [x] `main` protected and verified; PR flow tested end to end. *(2026-10-12)*

---

## T-008: Production Supabase project and `supabase/` initialization

**Description:** Create the production Supabase Free project and add the local Supabase CLI configuration to the repo, verifying that the local environment starts.

**Acceptance criteria:**
- [x] 👤 Project created on supabase.com (name `appfitness`, EU region: `eu-west-3` or `eu-central-1`, Free plan; created as `AppFitness` in `eu-north-1`, replaced by `eu-west-3` in T-014). DB password stored in a password manager. Noted down (not shared in the chat): project ref, URL and publishable key (`sb_publishable_...`; the legacy anon key is not used). Project ID: `ternuxiebhfpqcnftzqh` (Paris, since T-014). *(2026-10-12)*
- [x] 👤 `npx supabase login` in their terminal (local CLI token). *(2026-10-12)*
- [x] PM: CLI version chosen (latest stable) and pinned; `npx supabase@<v> init` → `supabase/config.toml` with `project_id = "appfitness"`; `supabase/.env.example` with the expected local variables (`OFF_USER_AGENT`, `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET`; `GOOGLE_WEB_CLIENT_ID` added in T-012) without values. *(2026-10-12: 2.120.0, pinned as a devDependency in `package.json`)*
- [x] PM: `npx supabase link --project-ref <ref>` locally (asks for the DB password interactively → 👤). *(2026-10-12; CLI 2.120.0 authenticates with the access token through a temporary login role, no password prompt)*
- [x] Changes through a PR. *(PR #2)*

**Verification:**
- [x] `npx supabase start` brings up the local Docker stack and `npx supabase status` shows API URL `http://127.0.0.1:54321`; `npx supabase stop`. *(2026-10-12)*
- [x] `npx supabase db push --dry-run` against production responds without errors ("Remote database is up to date"). *(2026-10-12)*

**Dependencies:** T-007
**Files likely touched:** `supabase/config.toml`, `supabase/.gitignore`, `supabase/.env.example`
**Estimated scope:** S

---

## T-009: `production` environment and deployment secrets

**Description:** Prepare in GitHub the environment and secrets GitHub Actions will use to deploy to Supabase, restricted to the `main` branch.

**Acceptance criteria:**
- [x] 👤 `production` environment created. *(2026-10-12)*
- [x] PM: deployment branch policy allowing only `main` on the `production` environment. *(2026-10-12)*
- [x] 👤 Supabase personal access token created (Account → Access Tokens, name `github-actions-appfitness`, 1-year expiry, granular permissions: Project Settings read, Migrations read-write, Connection Pooling read, Edge Functions read-write, API Keys read, **API Key Secrets read** (required by `supabase link`); everything else None). *Replaced on 2026-10-12 by `github-actions-appfitness-v2`: the first token lacked API Key Secrets and T-010 failed with 403; tokens cannot be edited, so a new one is created.*. *(2026-10-12)*
- [x] 👤 In their terminal, **environment** `production` secrets:
  `gh secret set SUPABASE_ACCESS_TOKEN --env production`, `gh secret set SUPABASE_DB_PASSWORD --env production`, `gh secret set SUPABASE_PROJECT_ID --env production`.
- [x] 👤 **Repository** secrets (used by `android-ci` and `keepalive` in E1): `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`. *(2026-10-12)*
- [x] 👤 Edge Functions secret in Supabase: `npx supabase secrets set OFF_USER_AGENT="AppFitness/0.1 (<contact email>)"`.

**Verification:**
- [x] `gh secret list --env production` shows the 3 names; `gh secret list` shows the repo ones (+ `GOOGLE_WEB_CLIENT_ID`). *(2026-10-12)*
- [x] `gh api repos/lopezjavier/AppFitness/environments/production --jq .deployment_branch_policy` is not null. *(2026-10-12)*
- [x] `npx supabase secrets list` includes `OFF_USER_AGENT`. *(2026-10-12)*

**Dependencies:** T-008
**Files likely touched:** none (remote configuration)
**Estimated scope:** XS

---

## T-010: `deploy-supabase.yml` workflow

**Description:** GitHub Actions pipeline that applies migrations and deploys Edge Functions to the production project every time a change in `supabase/**` is merged into `main`, and on demand.

**Acceptance criteria:**
- [x] `.github/workflows/deploy-supabase.yml`:
  - triggers: `push` to `main` with `paths: supabase/**` and `workflow_dispatch`;
  - `environment: production`, `concurrency: deploy-supabase` (without cancelling running deployments), `permissions: contents: read`;
  - `supabase/setup-cli@v3` with the **same pinned version** as in T-008 (read from `package-lock.json`, checked against `package.json`);
  - steps: `supabase link --project-ref $SUPABASE_PROJECT_ID` → `supabase db push` → `supabase functions deploy` **only if** some `supabase/functions/*/index.ts` exists;
  - `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD` variables from the environment secrets.
- [x] Documented in `planning/08-environment-setup.md` §4 (how to re-run it manually and how to see the result). *(2026-10-12)*
- [x] Changes through a PR. *(PR #3)*

**Verification:**
- [x] `gh workflow run deploy-supabase.yml` + `gh run watch` → success (link OK, "Remote database is up to date", functions skipped). *(2026-10-12, after replacing the token: see T-009)*
- [x] Merging the PR (if it touches `supabase/**`, or the next PR that does) triggers the workflow automatically and it finishes green; the deployment appears under *Environments → production*. *(2026-10-12: PR #5 merge → push run green; deployment in production)*

**Dependencies:** T-009
**Files likely touched:** `.github/workflows/deploy-supabase.yml`, `planning/08-environment-setup.md`
**Estimated scope:** S

---

### Checkpoint C — Automatic Supabase deployment
- [x] `deploy-supabase` green both manually and on merge. *(2026-10-12)*
- [x] 👤 User review. *(2026-10-12)*

---

## T-011: OAuth clients in Google Cloud

**Description:** Configure Google Cloud for Google Sign-In with Credential Manager + Supabase (ADR-02). Step-by-step guidance by the PM, performed by the user in the web console.

**Acceptance criteria:**
- [x] 👤 Google Cloud project `AppFitness` created. **Do not activate the free trial or billing:** OAuth and Sign in with Google are free and need no card.
- [x] 👤 Consent screen (Google Auth Platform → Branding/Audience): *External* type, *Testing* status, scopes `openid`, `email` and `profile`, and test users with the accounts that will use the app.
- [x] 👤 **Web** OAuth client `appfitness-web`: authorized redirect URI `https://<project-ref>.supabase.co/auth/v1/callback`. Client ID and Client Secret stored in the password manager.
- [x] 👤 **Android** OAuth client `appfitness-android`: package `io.github.lopezjavier.appfitness` + debug keystore SHA-1. *(2026-10-12, confirmed by the user)*
- [x] 👤 **Android** OAuth client `appfitness-android-local`: package `io.github.lopezjavier.appfitness.local` + the same SHA-1. *(2026-10-12, confirmed by the user)*
- [x] PM: command to get the SHA-1 on Windows provided. Debug keystore generated; SHA-1 `7D:F9:ED:8D:EF:0B:0C:27:96:DF:83:B5:5E:4D:E7:A2:D1:7C:4F:2A` (public fingerprint, this PC only). If `%USERPROFILE%\.android\debug.keystore` does not exist, it is generated by opening Android Studio or with `keytool`.

**Verification:**
- [x] Screenshot or list from the console with the 3 clients (Web + 2 Android) and the correct packages (without showing the secret). *(2026-10-12: Web client used by Supabase; both Android clients confirmed by the user, no screenshot shared)*
- [x] 👤 `gh secret set GOOGLE_WEB_CLIENT_ID` (repository secret, used by `android-ci`); `gh secret list` shows it. *(2026-10-12)*

**Dependencies:** T-008 (project ref for the redirect URI)
**Files likely touched:** `planning/08-environment-setup.md` (guide adjustments if the console differs)
**Estimated scope:** S

---

## T-012: Auth providers in Supabase (prod and local)

**Description:** Enable Google and disable Email in production; locally, keep Email enabled (for "Test login" and Maestro) and Google optional through environment variables.

**Acceptance criteria:**
- [x] 👤 Production dashboard → Authentication → Providers (Google enabled with the Web client ID/secret, nonce checks on, users without email not allowed). *(2026-10-12)* — Email disabled by the user after the first curl check showed it enabled. *(2026-10-12)*
  - Google enabled, with *Client ID* = the Web client ID and *Client Secret* = the Web client's secret. If the panel has an "authorized client IDs" field for native flows, include the Web client ID.
  - Email **disabled** (no email sign-up or login).
- [x] PM: local `supabase/config.toml`: `[auth.email] enable_signup = true`; `[auth.external.google] enabled = true`, `client_id = "env(GOOGLE_WEB_CLIENT_ID)"`, `secret = "env(SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET)"`; `.env.example` updated. With empty variables the local stack still starts. *(2026-10-12)*
- [x] Changes through a PR (triggers `deploy-supabase`, which must stay green: `config.toml` does not overwrite production auth).

**Verification:**
- [x] `curl -s https://<ref>.supabase.co/auth/v1/settings -H "apikey: <publishable key>"`: `external.google = true` and `external.email = false` (run by 👤 or by the PM reading environment variables, without printing the key). *(2026-10-12: `google = true`, `email = false`)*
- [x] Local: `npx supabase start` and `curl -s http://127.0.0.1:54321/auth/v1/settings -H "apikey: <local publishable key>"`: `external.email = true`. *(2026-10-12: `email = true`, `google = true` with empty variables)*
- Note: end-to-end sign-in is validated in US-05 (there is no app yet).

**Dependencies:** T-010, T-011
**Files likely touched:** `supabase/config.toml`, `supabase/.env.example`
**Estimated scope:** S

---

## T-013: E0 wrap-up

**Description:** Check the Definition of Done of `planning/epics/E0-setup.md`, leave local configuration templates and prepare the start of E1.

**Acceptance criteria:**
- [x] `local.properties.example` in the root with the keys from `planning/08-environment-setup.md` §3.1 (without values). *(2026-10-12)*
- [x] `planning/08-environment-setup.md` reflects what was actually configured (region, OAuth client names, CLI version); no secrets. *(2026-10-12: region (`eu-west-3` after T-014), OAuth client names, CLI 2.120.0, token permissions)*
- [x] `tasks/todo.md`: tasks T-001…T-013 checked and E0 ✅. *(T-014 added as a corrective task at the review)* *(2026-10-12)*
- [x] `.workflow/execution.log`: `[PM] [E0] [COMPLETED]`. *(written when this PR is merged)*
- [x] Changes through a PR. *(PR #6)*

**Verification:**
- [x] E0 checklist: public and protected repo · `CLAUDE.md` and agents · Supabase deploy green · production Auth with Google on and Email off · no secret in the history (`gitleaks` green on `main`). *(2026-10-12)*

**Dependencies:** T-012
**Files likely touched:** `local.properties.example`, `planning/08-environment-setup.md`, `tasks/todo.md`
**Estimated scope:** S

---

## T-014: Move the Supabase project to Paris (`eu-west-3`) — corrective

**Description:** At the E0 review the user chose Paris (`eu-west-3`, lower latency from Spain) over the existing `eu-north-1` project. Supabase cannot change a project's region, so a new empty project is created and everything that depends on the project ref is repointed. Done now because the project has no data or migrations yet.

**Acceptance criteria:**
- [x] 👤 New project `AppFitness` in `eu-west-3`, Free plan; new DB password in the password manager. *(2026-10-12: ref `ternuxiebhfpqcnftzqh`)*
- [x] 👤 New project Auth → Providers: Google on (Web client ID/secret, nonce checks on, users without email not allowed), Email off. *(2026-10-12)*
- [x] 👤 Google Cloud `appfitness-web`: redirect URI `https://<new-ref>.supabase.co/auth/v1/callback` added (the old one is removed at the end). *(2026-10-12)*
- [x] 👤 New deploy token scoped to the new project with the T-009 permissions (incl. API Key Secrets read) → `gh secret set SUPABASE_ACCESS_TOKEN --env production`. *(2026-10-12: named `github-actions-appfitness`)*
- [x] 👤 Secrets updated: `SUPABASE_PROJECT_ID`, `SUPABASE_DB_PASSWORD` (env `production`); `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` (repository); `OFF_USER_AGENT` in the new project's Edge Function secrets. *(2026-10-12)*
- [x] PM: local `supabase link` to the new ref; docs and tasks updated with the new ref and region. *(2026-10-12)*
- [x] 👤 After everything is green: old project deleted, old deploy token revoked, old redirect URI removed. *(2026-10-12, confirmed by the user; old project no longer listed)*
- [x] Changes through a PR. *(PR #6)*

**Verification:**
- [x] `npx supabase db push --dry-run` → "Remote database is up to date". *(2026-10-12)*
- [x] `gh workflow run deploy-supabase.yml` green against the new project. *(2026-10-12)*
- [x] `GET https://<new-ref>.supabase.co/auth/v1/settings`: `google = true`, `email = false`. *(2026-10-12)*
- [x] `npx supabase secrets list --project-ref <new-ref>` includes `OFF_USER_AGENT`. *(2026-10-12)*
- [x] `npx supabase projects list` shows only the Paris project. *(2026-10-12: only `ternuxiebhfpqcnftzqh`, `eu-west-3`)*

**Dependencies:** T-013
**Files likely touched:** `tasks/todo.md`, `planning/08-environment-setup.md`, `tasks/plan.md`
**Estimated scope:** S

---

### Checkpoint D — E0 closure
- [x] Everything above verified. *(2026-10-12)*
- [x] 👤 User review → the **E1** tasks are generated (from T-015). *(2026-10-12: first review → corrective T-014, move Supabase to Paris; approved after T-014)*

---

# E1 · Infrastructure and app skeleton

Stories: `planning/epics/E1-infrastructure-skeleton.md`. Each task inherits the Gherkin of its story; the **Scenarios** line says which ones it must make pass. Module names follow ADR-19 (approved 2026-10-12) (`androidApp` + `composeApp` + `shared`); if T-015 keeps the original layout, read `androidApp` as `composeApp`.
Flow: **infra/backend** = planner → developer → PR (no UX, no Maestro) · **UI** = full flow with ux-reviewer and qa-engineer (ADR-04).

## T-015: KMP project skeleton with `local`/`prod` flavors

**Story:** US-01 · **Flow:** infra · **Scenarios:** Local build of both flavors; Secrets kept out of the repository

**Description:** Create the Gradle project: wrapper, `gradle/libs.versions.toml` with the latest stable compatible versions (Kotlin, Compose Multiplatform, AGP, Koin, kotlinx-*, Navigation), `shared` and `composeApp` KMP modules and the Android application module. First confirm the AGP 9 module layout against the official docs and record ADR-19 (or drop it). The app has `local`/`prod` flavors (`env` dimension, `.local` suffix, ADR-14), `BuildConfig` fields from `local.properties` or environment variables with blank fallbacks, cleartext only for `10.0.2.2` in `local`, Koin started, and an empty `App()` showing a text.

**Acceptance criteria:**
- [x] ADR-19 written (or explicitly dropped) with the official sources; `CLAUDE.md`, `planning/02-architecture.md` §2/§5, `planning/06-agent-workflow.md` §4 and the E1 epic use the real module and task names.
- [x] `./gradlew :androidApp:assembleLocalDebug :androidApp:assembleProdDebug` builds both APKs; `applicationId` is `io.github.lopezjavier.appfitness(.local)`; JDK 21 build, JVM 17 bytecode (ADR-18).
- [x] `BuildConfig.SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_WEB_CLIENT_ID` per flavor from `local.properties` (`supabase.local.*`, `supabase.prod.*`, `google.webClientId`) or env vars (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` feed `prod` only; `GOOGLE_WEB_CLIENT_ID`); a missing value builds with `""` instead of failing, except the `local` `SUPABASE_URL`, which defaults to `http://10.0.2.2:54321` *(user decision 2026-10-12)*. `minSdk` 26 (Health Connect).
- [x] Network security config allows cleartext only to `10.0.2.2` and only in `local`.
- [x] Unit tests: one sample test in `shared/commonTest`; a `local` flavor test asserting `SUPABASE_URL == "http://10.0.2.2:54321"`.

**Verification:**
- [x] `./gradlew testLocalDebugUnitTest` green.
- [x] Both assemble tasks green from a clean checkout with `local.properties` filled in; `git status` does not list `local.properties`.
- [x] Manual: `installLocalDebug` on the emulator shows the placeholder text.

**Dependencies:** E0
**Files likely touched:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew*`, `androidApp/build.gradle.kts` + `src/{main,local}/…`, `composeApp/…/App.kt`, `shared/…`, docs listed above
**Estimated scope:** L (scaffolding; most files are generated boilerplate. Do not add features beyond this list.)

---

## T-016: ktlint and detekt

**Story:** US-01 · **Flow:** infra · **Scenarios:** (prepares "Quality and tests on a Pull Request")

**Description:** Add the ktlint Gradle plugin and detekt to every module with a shared config that accepts Compose conventions (PascalCase `@Composable` functions, `Modifier` parameter rules), and leave the code base clean.

**Acceptance criteria:**
- [x] `./gradlew ktlintCheck detekt` runs on `androidApp`, `composeApp` and `shared` (all source sets).
- [x] `.editorconfig` and `config/detekt/detekt.yml` set the Compose exceptions; no baseline file (the code base starts clean).
- [x] A deliberate violation makes the command fail (checked locally, not committed).
- [x] PR #8 review follow-ups *(user decision 2026-10-12)*:
  - `gradle/libs.versions.toml`: reword the Kotlin compatibility comment (Gradle 9.7.1 / AGP 9.3.3 are patch releases above the KGP table's tested 9.7.0 / 9.3.1; no warning).
  - `androidApp/build.gradle.kts` `toJavaStringLiteral()`: also escape `\n` and `\r`, with a test.
  - `LocalBuildConfigTest` and `AppModuleTest`: KDoc noting they assume the default `supabase.local.url`.
  - `planning/02-architecture.md` §2: mark `androidApp/src/prod/` as "(created when needed)".

**Verification:**
- [x] `./gradlew ktlintCheck detekt testLocalDebugUnitTest` green.

**Dependencies:** T-015
**Files likely touched:** `gradle/libs.versions.toml`, `build.gradle.kts`, `.editorconfig`, `config/detekt/detekt.yml`, `androidApp/build.gradle.kts`, `androidApp/src/testLocal/…`, `planning/02-architecture.md`
**Estimated scope:** S

---

## T-017: `android-ci` workflow as a required check

**Story:** US-01 · **Flow:** infra · **Scenarios:** Quality and tests on a Pull Request

**Description:** Create `.github/workflows/android-ci.yml` (JDK 21 Temurin + Gradle cache → `ktlintCheck detekt` → `testLocalDebugUnitTest` → `:androidApp:assembleProdDebug` → APK artifact) that runs on every PR and push to `main`, skipping the build job when only `supabase/**` changed (ADR-20). Make it a required check and add Gradle to Dependabot.

**Acceptance criteria:**
- [x] Workflow: `permissions: contents: read`; `concurrency` per ref with cancel-in-progress for PRs; `changes` job (path filter) + build job with `if:`; the build reads `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_WEB_CLIENT_ID` repo secrets as env vars; APK uploaded with `actions/upload-artifact`. Actions pinned by major version.
- [x] ADR-20 in `planning/00-decisions.md`; `planning/02-architecture.md` §8 and the US-01 technical notes updated.
- [x] `dependabot.yml` gets the `gradle` ecosystem (weekly, prefix `chore`).
- [x] PM: after the first green run, the `android-ci` build job is added as a required check in the `main` ruleset (`gh api`) and in `.github/rulesets/main.json`.

**Verification:**
- [x] The task PR shows `gitleaks` and `android-ci` green and the APK artifact.
- [x] `gh api repos/lopezjavier/AppFitness/rulesets/24923545` lists both required checks.
- [x] A PR touching only `supabase/**` (T-018) reports `android-ci` as skipped/passing and can merge (probe PR #14: `android-ci` skipped, merge state CLEAN; closed unmerged).

**Dependencies:** T-016
**Files likely touched:** `.github/workflows/android-ci.yml`, `.github/dependabot.yml`, `.github/rulesets/main.json`, `planning/00-decisions.md`, `planning/02-architecture.md`
**Estimated scope:** S

---

### Checkpoint E1-A — CI gate
- [x] `main` requires `gitleaks` + `android-ci`; both flavors build locally and in CI.
- [ ] 👤 Quick user review of the module layout (ADR-19) and CI before the backend and UI work.

---

## T-018: `profiles` migration, RLS, pgTAP, seed user and `supabase-ci`

**Story:** US-02 · **Flow:** backend · **Scenarios:** Local migrations and seed; Profile isolation with RLS; Database tests on the PR; First deployment with content (migration part)

**Description:** First migration: `unaccent` extension, the `sex` and `activity_level` enums, `profiles` per `03-data-model.md` §2, a reusable `set_updated_at()` trigger function, the `on_auth_user_created` trigger (security definer, fixed `search_path`) and RLS (select/update for the owner, no client insert/delete). `seed.sql` creates `test@appfitness.local` locally. `supabase-ci.yml` runs start → db reset → db lint → test db, always reporting (ADR-20), and becomes a required check.

**Acceptance criteria:**
- [x] `supabase/migrations/<ts>_profiles.sql`: RLS enabled **and forced**; policies use `(select auth.uid())`; `height_cm` check 100–250; `updated_at` maintained by the trigger.
- [x] `supabase/tests/profiles.test.sql` (pgTAP): RLS on; the trigger creates the row with name/avatar from metadata; A sees only their row; A updating B's row affects 0 rows; A cannot delete.
- [x] `supabase/seed.sql` creates the test user (password documented in `planning/08-environment-setup.md` §3.2; it is a local-only, non-secret value) with its `profiles` row through the trigger.
- [x] `.github/workflows/supabase-ci.yml` with the pinned CLI (`supabase/setup-cli@v3`, version from `package-lock.json`); added to the ruleset and `main.json` as required after its first green run.
- [x] `supabase-ci` follows ADR-20 (`changes` job with the `supabase/**` filter, explicit `predicate-quantifier`, fail-open build job); the `supabase-ci` row in `planning/02-architecture.md` §8 is updated to match (PR #10 review).
- [x] Merging deploys the migration to production via `deploy-supabase` (no seed).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green locally.
- [x] PR checks: `gitleaks`, `android-ci` (builds: the PR also touches `.github/`, `planning/` and `tasks/`), `supabase-ci` green. The supabase-only skip from T-017 is proven with a throwaway probe PR after the merge (closed unmerged; ticked on the T-019 branch).
- [x] After merge: `deploy-supabase` run green; `npx supabase migration list` shows the migration remote; production has no `test@appfitness.local` user.

**Dependencies:** T-017
**Files likely touched:** `supabase/migrations/<ts>_profiles.sql`, `supabase/tests/profiles.test.sql`, `supabase/seed.sql`, `.github/workflows/supabase-ci.yml`, `.github/rulesets/main.json`, `planning/08-environment-setup.md`
**Estimated scope:** M

---

## T-019: `health` Edge Function and `keepalive` workflow

**Story:** US-02 · **Flow:** backend · **Scenarios:** Health check; First deployment with content (function part); Project kept active on the free plan

**Description:** `supabase/functions/health/index.ts` (Deno) answers `GET` with `200 {"status":"ok"}` and `verify_jwt = false` in `config.toml`; tested with `deno test`. `supabase-ci` gains the `deno test` step. `keepalive.yml` (cron `0 6 */3 * *` + `workflow_dispatch`) calls the production function with the publishable key as `apikey` (ADR-17) and fails on a non-200 response.

**Acceptance criteria:**
- [x] Function (`health/index.ts` entrypoint + pure `health/handler.ts`) + `health/handler.test.ts`: `GET` → 200 + JSON body + `content-type: application/json`; other methods → 405.
- [x] `[functions.health] verify_jwt = false` in `supabase/config.toml`.
- [x] `supabase-ci.yml` runs `deno test --allow-all supabase/functions`.
- [x] `.github/workflows/keepalive.yml` uses the `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` repo secrets, never prints them, `curl --fail` with a timeout and retry.
- [x] Dependabot: no new ecosystem needed (Deno std imports pinned by version in the import URL/specifier).

**Verification:**
- [x] `deno test --allow-all supabase/functions` green; `npx supabase functions serve` + `curl http://127.0.0.1:54321/functions/v1/health` → 200.
- [x] After merge: `deploy-supabase` deploys `health`; `gh workflow run keepalive.yml` + `gh run watch` green.

**Dependencies:** T-018
**Files likely touched:** `supabase/functions/health/index.ts`, `supabase/functions/health/handler.ts`, `supabase/functions/health/handler.test.ts`, `deno.json`, `supabase/config.toml`, `.github/workflows/supabase-ci.yml`, `.github/workflows/keepalive.yml`
**Estimated scope:** S

---

### Checkpoint E1-B — Backend live
- [x] Production has `profiles` + `health`; `deploy-supabase` and `keepalive` green at least once; `supabase-ci` required.

---

## T-020: Kinetic Dark theme foundation

**Story:** US-03 · **Flow:** UI · **Scenarios:** Tokens applied globally (background and font; cards are checked in T-023)

**Description:** `designsystem/` in `composeApp/commonMain`: `AppColors` (all §2 tokens) exposed via `AppTheme.colors`, the M3 `darkColorScheme` mapping of §2.4, Plus Jakarta Sans (400/600/700/800) bundled as Compose resources with `OFL.txt`, the §3 typography (with `tnum, lnum` on numeric styles and phone/tablet sizes for `displayHero`/`headlineMetric`), `Spacing` and `Shapes` (§4). `App()` uses `AppTheme`; the placeholder screen has a `surfaceBase` background. Spanish string resources set up.

**Acceptance criteria:**
- [x] Every token in `05-design-system.md` §2–§4 exists with the exact value; no hardcoded color/dp/sp outside `designsystem/`.
- [x] Unit tests: color scheme mapping (`primary == neonLime`, `background == surfaceBase`…); numeric text styles include `tnum`.
- [x] Strings come from `composeResources` (Spanish).
- [x] Material3 version checked against the Compose Multiplatform version in the catalog (PR #8 review: 1.9.0 stable vs. the 1.12.0-alpha pairing); keep or bump, noted in the catalog comment.

**Verification:**
- [x] `./gradlew ktlintCheck detekt testLocalDebugUnitTest` green.
- [x] QA: `tests/e2e/T-020.yaml` launches the app and finds the placeholder; screenshot shows the dark background and the Jakarta font.

**Dependencies:** T-017
**Files likely touched:** `composeApp/src/commonMain/kotlin/<pkg>/designsystem/{AppColors,AppTheme,Typography,Spacing,Shapes}.kt`, `composeApp/src/commonMain/composeResources/font/*`, `…/values/strings.xml`, `App.kt`, tests
**Estimated scope:** M

---

## T-021: 4-tab navigation shell with WIP screens

**Story:** US-04 · **Flow:** UI · **Scenarios:** Launch (tabs and initial tab); Navigating between tabs

**Description:** Type-safe `@Serializable` routes for Diario, Entreno, Progreso, Perfil with one back stack per tab (ADR-10), the `AppNavigation` bottom bar (active indicator in `neonLime`), and one WIP screen per tab built with `WipPlaceholder` + `SkeletonBox` (shimmer) in a scrollable list. Switching tabs keeps each tab's state and scroll position.

**Acceptance criteria:**
- [x] Start tab Diario; the 4 labels in Spanish; `testTag`s `nav_tab_diary`, `nav_tab_workout`, `nav_tab_progress`, `nav_tab_profile` (final names set by the ux-reviewer).
- [x] Re-selecting a tab restores its back stack and scroll position (`rememberSaveable`/saved state).
- [x] `SkeletonBox`, `WipPlaceholder`, `AppNavigation` (bottom bar variant) live in `designsystem/` with previews.
- [x] Unit test for the tab/route model (start destination, tab ↔ route mapping).

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-021.yaml` covers "Navigating between tabs" (incl. scroll preservation).

**Dependencies:** T-020
**Files likely touched:** `composeApp/…/app/{AppRoutes,AppNavHost,AppScaffold}.kt`, `…/designsystem/{AppNavigation,SkeletonBox,WipPlaceholder}.kt`, `…/feature/{diary,workout,progress,profile}/…Screen.kt`, strings, test, `tests/e2e/T-021.yaml`
**Estimated scope:** M

---

## T-022: Top App Bar with section title and avatar

**Story:** US-04 · **Flow:** UI · **Scenarios:** Launch (Top App Bar); The avatar opens Profile

**Description:** `AppTopBar` with the active section title and a circular avatar (placeholder initials/icon until E2 provides the Google photo) that switches to the Perfil tab. Content description in Spanish.

*Carry-over from T-021:* reuse `navigateToTab` for the avatar. Each WIP screen shows its own section title (`<screen>_wip_title`), which duplicates the top bar title; if T-022 drops it, update the scroll assertions in `tests/e2e/T-021.yaml` that use it as a reference. Maestro `takeScreenshot` with a relative `tests/e2e/screenshots/...` path writes no file; fix with `--test-output-dir` or an absolute path.

**Acceptance criteria:**
- [ ] The title follows the active tab; the avatar target is ≥ 48 dp with a `contentDescription` and a `testTag` (e.g. `topbar_avatar`).
- [ ] Tapping the avatar from any tab activates Perfil using the same tab-switch logic as the bottom bar (state preserved).

**Verification:**
- [ ] Lint + unit tests green.
- [ ] QA: `tests/e2e/T-022.yaml` (Launch + avatar scenario); `tests/e2e/` regression green.

**Dependencies:** T-021
**Files likely touched:** `…/designsystem/AppTopBar.kt`, `…/app/AppScaffold.kt`, strings, `tests/e2e/T-022.yaml`
**Estimated scope:** S

---

### Checkpoint E1-C — First installable APK
- [ ] Lint, unit tests, Maestro regression and CI green.
- [ ] 👤 The user installs the APK, moves between the tabs and gives early feedback on style (cheap to adjust now).

---

## T-023: Debug-only component catalog + buttons, card, chips and segmented selector

**Story:** US-03 · **Flow:** UI · **Scenarios:** Component catalog in debug (first components); Tokens applied globally (cards)

**Description:** A "Catálogo de componentes" screen reachable from Perfil **only in the `local` flavor**, listing components by section with their states. Adds `AppCard`, `PrimaryButton`/`SecondaryButton`/`GhostButton` (enabled, disabled, pressed), `AppChip`/`FilterChipRow` (active cyan) and `SegmentedSelector` (Déficit/Mantenimiento/Superávit).

*Carry-over from T-020 (UX):* M3 `outline` (borderStrong, about 1.3:1) fails 3:1, so do not use stock `Switch`/`OutlinedButton`/`OutlinedTextField` with default colors; components set their own colors and states never rely on the border alone.

**Acceptance criteria:**
- [ ] The entry point is contributed by the `local` flavor; a `prod` unit test (`testProdDebugUnitTest`) asserts the catalog is not available.
- [ ] `AppCard`: `surfaceRaised`, 1 dp `borderSubtle`, radius 16, padding 16. Buttons 52/48 dp min height; chips 32 dp visual / 48 dp target.
- [ ] Each component: `Modifier` first optional param, propagatable `testTag`, preview, catalog entry.
- [ ] Unit test for `SegmentedSelector`/`FilterChipRow` selection state logic if extracted.

**Verification:**
- [ ] Lint + `testLocalDebugUnitTest` + `testProdDebugUnitTest` green.
- [ ] QA: `tests/e2e/T-023.yaml` opens the catalog from Perfil and asserts the components by `testTag`.

**Dependencies:** T-022
**Files likely touched:** `…/designsystem/{AppCard,Buttons,AppChip,SegmentedSelector}.kt`, `…/feature/catalog/ComponentCatalogScreen.kt`, `androidApp/src/{local,prod}/…`, `…/feature/profile/ProfileScreen.kt`, `tests/e2e/T-023.yaml`
**Estimated scope:** M

---

## T-024: Data visualization components

**Story:** US-03 · **Flow:** UI · **Scenarios:** Component catalog in debug (data components)

**Description:** `ProgressRing` (kcal ring, normal and over-target amber state), `MacroProgressBar` ("142 g / 180 g · 79 %"), `MacroSlider` (macro color, % and grams/kcal), `MetricTile` and `TrendPill` (green/red/amber), all added to the catalog with their states.

**Acceptance criteria:**
- [ ] Pure functions with unit tests: ring sweep angle (0, partial, 100 %, > 100 % capped) and over-target flag; macro bar percent text and rounding; trend pill color for positive/negative/neutral.
- [ ] Ring and bars expose their value through `stateDescription`; numbers use tabular figures.

**Verification:**
- [ ] Lint + unit tests green.
- [ ] QA: `tests/e2e/T-024.yaml` asserts the new catalog entries; regression green.

**Dependencies:** T-023
**Files likely touched:** `…/designsystem/{ProgressRing,MacroProgressBar,MacroSlider,MetricTile,TrendPill}.kt`, their tests, catalog screen, `tests/e2e/T-024.yaml`
**Estimated scope:** M

---

## T-025: Workout input components

**Story:** US-03 · **Flow:** UI · **Scenarios:** Component catalog in debug (input components)

**Description:** `NumberInput` (centered tabular numerals, 1.5 dp focus border, ghost placeholder), `SetCompletionCheckbox` (38 dp visual / 48 dp target, lime fill + dark check, haptic via `LocalHapticFeedback`), `SetRow` (index · previous · KG · REPS · checkbox) and `RestTimerBar` (cyan countdown, progress line, "+30s", "Omitir"), all in the catalog.

*Carry-over from T-020 (UX):* `ghostData` is about 3.3:1, so `NumberInput` keeps a visible label and the ghost placeholder is never the only cue.

**Acceptance criteria:**
- [ ] Unit tests: decimal input parsing/filtering for `NumberInput` (comma and dot, max decimals); rest timer `mm:ss` formatting and progress fraction; "+30s" extension.
- [ ] Checkbox exposes checked state for accessibility and the catalog shows both states.

**Verification:**
- [ ] Lint + unit tests green.
- [ ] QA: `tests/e2e/T-025.yaml` types into `NumberInput` and toggles the checkbox in the catalog; regression green.

**Dependencies:** T-024
**Files likely touched:** `…/designsystem/{NumberInput,SetCompletionCheckbox,SetRow,RestTimerBar}.kt`, their tests, catalog screen, `tests/e2e/T-025.yaml`
**Estimated scope:** M

---

## T-026: Empty/error states and accessibility sweep of the catalog

**Story:** US-03 · **Flow:** UI · **Scenarios:** Accessibility of interactive components; Component catalog in debug (complete)

**Description:** `EmptyState` and `ErrorState` (icon + message + action) in the catalog, plus an accessibility pass on every catalog component: touch targets ≥ 48 × 48 dp and a `contentDescription` on every actionable icon. Decide and document how touch targets are tested (Compose UI test on the JVM if it works with the pinned versions; otherwise Maestro bounds checks + UX review).

**Acceptance criteria:**
- [ ] Every component from `05-design-system.md` §6 is in the catalog except `UniversalFab`, `AppBottomSheet` (T-027) and `OfflineBanner` (T-029), which are added there.
- [ ] Automated touch target / content description checks for the interactive catalog components, or a logged decision with the fallback.

**Verification:**
- [ ] Lint + unit tests (and UI tests if adopted) green.
- [ ] QA: `tests/e2e/T-026.yaml`; full regression green.

**Dependencies:** T-025
**Files likely touched:** `…/designsystem/{EmptyState,ErrorState}.kt`, catalog screen, a11y tests, `tests/e2e/T-026.yaml`
**Estimated scope:** S

---

### Checkpoint E1-D — Design system complete
- [ ] US-03 scenarios green; catalog absent in `prodDebug`.

---

## T-027: Contextual FAB with WIP bottom sheets

**Story:** US-04 · **Flow:** UI · **Scenarios:** Contextual FAB (outline); FAB hidden on Profile

**Description:** `UniversalFab` (lime pill "+ Registro", 52 dp, 0.97 scale on press) and `AppBottomSheet` (top radius 24, 36×4 handle, 65 % scrim). The active route defines the FAB action (ADR-10): Diario → "Añadir alimento", Entreno → "Empezar entreno", Progreso → "Registrar peso", Perfil → hidden. Each action opens a WIP sheet with that title. Both components are added to the catalog.

*Carry-over from T-020 (UX):* M3 `ModalBottomSheet` defaults to `scrim.copy(alpha = 0.32f)`; `AppBottomSheet` must pass `scrimColor = AppTheme.colors.scrim` explicitly.

**Acceptance criteria:**
- [ ] Route → FAB action mapping in one place, with unit tests for the 4 tabs.
- [ ] Sheet title per tab; dismiss by scrim, back and drag; `testTag`s for FAB and sheet.

**Verification:**
- [ ] Lint + unit tests green.
- [ ] QA: `tests/e2e/T-027.yaml` runs the 3 outline rows and the Profile case; regression green.

**Dependencies:** T-026
**Files likely touched:** `…/designsystem/{UniversalFab,AppBottomSheet}.kt`, `…/app/{FabAction,AppScaffold}.kt`, test, strings, catalog, `tests/e2e/T-027.yaml`
**Estimated scope:** M

---

## T-028: Adaptive layout with NavigationRail on tablets

**Story:** US-04 · **Flow:** UI · **Scenarios:** Tablet layout

**Description:** Using the window size class, compact widths keep the bottom bar + FAB; ≥ 600 dp show a `NavigationRail` (lime indicator) with the FAB at its top. Same tabs, routes and state. `displayHero`/`headlineMetric` use the tablet sizes on wide screens.

**Acceptance criteria:**
- [ ] Width → navigation type decided by a pure function with unit tests (599 → bar, 600 → rail).
- [ ] Rotating or resizing keeps the active tab and state.

**Verification:**
- [ ] Lint + unit tests green.
- [ ] QA: phone regression green; tablet checked on a tablet emulator (create a `Pixel Tablet` AVD if missing) with screenshots, or manually if no AVD (logged).

**Dependencies:** T-027
**Files likely touched:** `…/designsystem/AppNavigation.kt`, `…/app/AppScaffold.kt`, `…/designsystem/Typography.kt`, test
**Estimated scope:** S

---

## T-029: Connectivity observer and "Sin conexión" banner

**Story:** US-04 · **Flow:** UI · **Scenarios:** Offline

**Description:** `ConnectivityObserver` interface (`Flow<Boolean>`) in `shared` (`domain/port`), Android implementation with `ConnectivityManager.NetworkCallback` in `androidMain`, injected with Koin. The shell shows `OfflineBanner` below the Top App Bar while offline and hides it when the connection returns (ADR-01). Banner added to the catalog.

**Acceptance criteria:**
- [ ] Shell state driven by the observer and covered by a ViewModel test with a fake observer (online → offline → online) using Turbine.
- [ ] Android implementation emits the current state on collection and is unregistered on cancellation; `ACCESS_NETWORK_STATE` permission declared.

**Verification:**
- [ ] Lint + unit tests green.
- [ ] QA: `tests/e2e/T-029.yaml` with Maestro airplane mode (if unreliable on the emulator, manual check logged); full regression green.

**Dependencies:** T-028
**Files likely touched:** `shared/…/domain/port/ConnectivityObserver.kt`, `composeApp/src/androidMain/…/platform/AndroidConnectivityObserver.kt`, `…/app/AppViewModel.kt`, `…/designsystem/OfflineBanner.kt`, DI module, test, `tests/e2e/T-029.yaml`
**Estimated scope:** M

---

## T-030: E1 closure

**Story:** E1 Definition of Done · **Flow:** PM

**Description:** Check the epic DoD, deliver the APK and handle feedback.

**Acceptance criteria:**
- [ ] US-01…US-04 merged with green CI; `deploy-supabase` and `keepalive` green at least once (T-018/T-019).
- [ ] `maestro test tests/e2e/` green on the emulator against local Supabase.
- [ ] `CLAUDE.md` "Current status" updated (E1 done, next E2); `tasks/todo.md` E1 ✅ (through a PR).
- [ ] `./gradlew :androidApp:assembleProdDebug` → APK path given to the user; `[PM] [E1] [COMPLETED]` logged.
- [ ] 👤 The user validates navigation and style on phone and tablet → corrective tasks (T-031+) or approval → `git tag -a v0.1.0 -m "Epic E1"` + `git push --tags`, then break down E2.

**Verification:**
- [ ] `gh run list` shows green `deploy-supabase` and `keepalive` runs; `git tag` lists `v0.1.0` after approval.

**Dependencies:** T-019, T-026, T-029
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

---

### Checkpoint E1-E — E1 closure
- [ ] Everything above verified; 👤 user approval → `v0.1.0`.
