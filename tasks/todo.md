# Tasks — Phase 1

Plan: [tasks/plan.md](plan.md) · Planning: [planning/](../planning/README.md) · Log: `.workflow/execution.log` (local)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Epic status: E0 ✅ · E1 ✅ · E2 ✅ · E3 ☐ · E4 ☐ · E5 ☐ · E6 ☐

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
- [x] 👤 Quick user review of the module layout (ADR-19) and CI before the backend and UI work. *(Never presented at the time; marked approved at the E1 retrospective, 2026-10-13, as E1 was approved on top of both.)*

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
- [x] The title follows the active tab; the avatar target is ≥ 48 dp with a `contentDescription` and a `testTag` (e.g. `topbar_avatar`).
- [x] Tapping the avatar from any tab activates Perfil using the same tab-switch logic as the bottom bar (state preserved).

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-022.yaml` (Launch + avatar scenario); `tests/e2e/` regression green.

**Dependencies:** T-021
**Files likely touched:** `…/designsystem/AppTopBar.kt`, `…/app/AppScaffold.kt`, strings, `tests/e2e/T-022.yaml`
**Estimated scope:** S

---

### Checkpoint E1-C — First installable APK
- [x] Lint, unit tests, Maestro regression and CI green.
- [x] 👤 The user installs the APK, moves between the tabs and gives early feedback on style (cheap to adjust now). *2026-10-12 on a Pixel 9: good overall; Perfil tab redundant with the avatar and the active-tab pill not in the mockups → ADR-22, corrective task T-031.*

---

## T-031: 3 tabs, Profile as a full screen from the avatar, filled-icon active tab (ADR-22)

**Story:** US-04 · **Flow:** UI · **Scenarios:** Launch; Navigating between tabs; The avatar opens Profile (updated by ADR-22)

**Description:** Corrective task from the Checkpoint E1-C feedback. Remove Perfil from the bottom bar (3 tabs: Diario, Entreno, Progreso). Perfil becomes a full-screen destination above the tab graphs, opened only from the avatar: Top App Bar with a back arrow and the "Perfil" title, no bottom bar; back returns to the originating tab with its state and scroll. The active tab drops the `glowLime` pill and shows `neonLime` icon and an extra-bold `neonLime` label, plus the filled icon where the glyph has a distinct one (only Diario today; official Material Symbols `*_fill1_24px` SVG, same Apache-2.0 license file).

**Acceptance criteria:**
- [x] Tab model has 3 tabs; Profile is a separate route; unit tests updated (start tab, tab ↔ route, avatar target = Profile screen).
- [x] Profile screen: back arrow (≥ 48 dp, Spanish `contentDescription`, `testTag`), no bottom bar; system back and the arrow return to the previous tab with state preserved; the avatar is hidden or inert on Profile.
- [x] Active tab: no indicator pill; lime icon (filled for Diario) + extra-bold lime label; inactive tabs outlined grey with the regular label.

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-021.yaml` and `T-022.yaml` updated to the 3-tab model and the new avatar/back scenario; full regression green; screenshot of the active tab.

**Dependencies:** T-022
**Files likely touched:** `…/app/{AppRoutes,AppNavHost,AppScaffold}.kt`, `…/designsystem/{AppNavigation,AppTopBar}.kt`, `drawable/ic_nav_diary_filled.xml`, `drawable/ic_arrow_back.xml`, `…/feature/profile/ProfileScreen.kt`, strings, tests, `tests/e2e/T-021.yaml`, `tests/e2e/T-022.yaml`
**Estimated scope:** S–M

---

## T-023: Debug-only component catalog + buttons, card, chips and segmented selector

**Story:** US-03 · **Flow:** UI · **Scenarios:** Component catalog in debug (first components); Tokens applied globally (cards)

**Description:** A "Catálogo de componentes" screen reachable from Perfil **only in the `local` flavor**, listing components by section with their states. Adds `AppCard`, `PrimaryButton`/`SecondaryButton`/`GhostButton` (enabled, disabled, pressed), `AppChip`/`FilterChipRow` (active cyan) and `SegmentedSelector` (Déficit/Mantenimiento/Superávit).

*Carry-over from T-031 (PR #22 review, small fixes inside this task):* (1) `AppScaffold.kt`: `dropUnlessResumed` on the Profile back action is a no-op (the scaffold sits outside the NavHost, so it checks the always-resumed Activity lifecycle); guard `popBackStack()` by checking the current route is Profile, and avoid a new lambda per composition. (2) `AppNavigation.kt`: `barHeight()` uses `WindowInsets.navigationBars` while `NavigationBar` uses its default insets; pass the same insets to `NavigationBar` so height and padding share one source. Optional nits: `android:autoMirrored="true"` on `ic_arrow_back.xml`; drop the touch-target test in `AppNavigationTest` that duplicates the 64 dp height test.

*Carry-over from T-020 (UX):* M3 `outline` (borderStrong, about 1.3:1) fails 3:1, so do not use stock `Switch`/`OutlinedButton`/`OutlinedTextField` with default colors; components set their own colors and states never rely on the border alone.

**Acceptance criteria:**
- [x] The entry point is contributed by the `local` flavor; a `prod` unit test (`testProdDebugUnitTest`) asserts the catalog is not available.
- [x] `AppCard`: `surfaceRaised`, 1 dp `borderSubtle`, radius 16, padding 16. Buttons 52/48 dp min height; chips 32 dp visual / 48 dp target.
- [x] Each component: `Modifier` first optional param, propagatable `testTag`, preview, catalog entry.
- [x] Unit test for `SegmentedSelector`/`FilterChipRow` selection state logic if extracted.

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` + `testProdDebugUnitTest` green.
- [x] QA: `tests/e2e/T-023.yaml` opens the catalog from Perfil and asserts the components by `testTag`.

**Dependencies:** T-022
**Files likely touched:** `…/designsystem/{AppCard,Buttons,AppChip,SegmentedSelector}.kt`, `…/feature/catalog/ComponentCatalogScreen.kt`, `androidApp/src/{local,prod}/…`, `…/feature/profile/ProfileScreen.kt`, `tests/e2e/T-023.yaml`
**Estimated scope:** M

---

## T-024: Data visualization components

**Story:** US-03 · **Flow:** UI · **Scenarios:** Component catalog in debug (data components)

**Description:** `ProgressRing` (kcal ring, normal and over-target amber state), `MacroProgressBar` ("142 g / 180 g · 79 %"), `MacroSlider` (macro color, % and grams/kcal), `MetricTile` and `TrendPill` (green/red/amber), all added to the catalog with their states.

**Acceptance criteria:**
- [x] Pure functions with unit tests: ring sweep angle (0, partial, 100 %, > 100 % capped) and over-target flag; macro bar percent text and rounding; trend pill color for positive/negative/neutral.
- [x] Ring and bars expose their value through `stateDescription`; numbers use tabular figures.

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-024.yaml` asserts the new catalog entries; regression green.

**Dependencies:** T-023
**Files likely touched:** `…/designsystem/{ProgressRing,MacroProgressBar,MacroSlider,MetricTile,TrendPill}.kt`, their tests, catalog screen, `tests/e2e/T-024.yaml`
**Estimated scope:** M

---

## T-025: Workout input components

**Story:** US-03 · **Flow:** UI · **Scenarios:** Component catalog in debug (input components)

**Description:** `NumberInput` (centered tabular numerals, 1.5 dp focus border, ghost placeholder), `SetCompletionCheckbox` (38 dp visual / 48 dp target, lime fill + dark check, haptic via `LocalHapticFeedback`), `SetRow` (index · previous · KG · REPS · checkbox) and `RestTimerBar` (cyan countdown, progress line, "+30s", "Omitir"), all in the catalog.

*Carry-over from T-020 (UX):* `ghostData` is about 3.3:1, so `NumberInput` keeps a visible label and the ghost placeholder is never the only cue.

**Acceptance criteria:**
- [x] Unit tests: decimal input parsing/filtering for `NumberInput` (comma and dot, max decimals); rest timer `mm:ss` formatting and progress fraction; "+30s" extension.
- [x] Checkbox exposes checked state for accessibility and the catalog shows both states.

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-025.yaml` types into `NumberInput` and toggles the checkbox in the catalog; regression green.

**Dependencies:** T-024
**Files likely touched:** `…/designsystem/{NumberInput,SetCompletionCheckbox,SetRow,RestTimerBar}.kt`, their tests, catalog screen, `tests/e2e/T-025.yaml`
**Estimated scope:** M

---

## T-026: Empty/error states and accessibility sweep of the catalog

**Story:** US-03 · **Flow:** UI · **Scenarios:** Accessibility of interactive components; Component catalog in debug (complete)

*Carry-over from T-023 (PR #23 review, non-blocking):* (1) `SegmentedSelector.kt`: the private `MinLabelFontSize = 8.sp` sits below the smallest type token; move it to `SegmentedSelectorDefaults` with KDoc + test and record the rule in `05-design-system.md` §6–7. (2) Auto-size is per segment, so "Mantenimiento" may shrink while siblings stay 14 sp, and it clips again above ~160 % font scale; try `Hyphens.Auto` + `maxLines = 2` (hyphenated Spanish wrap) or one shared label size for all segments, and pick by screenshot. (3) `AppChip.kt`: `AppChipDefaults.MinTouchTarget` is unused in production (the target comes from `minimumInteractiveComponentSize()`), so its test checks a constant; apply it or delete both. (4) `AppNavHost.kt`: `chrome()` duplicates the full-screen lookup in `isFullScreen()`; share one helper. (5) `ComponentCatalogScreen.kt`: `remember` the chip/segment option lists.

*Carry-over from T-024 (QA):* (6) `stateDescription` of `MacroSlider`, `ProgressRing` and `TrendPill` is set in code but could not be verified on the API 37 emulator (uiautomator/Maestro do not expose it); cover it in this task's a11y-check decision (Compose semantics test, or a logged TalkBack check). Expected: slider "Proteínas, 30 por ciento, 180 gramos, 720 kilocalorías" (not the raw range %), ring "Calorías, 1.840 de 2.400 kcal, 560 kcal restantes", pill text + tone wording. (7) `MacroSlider.kt`: the disabled thumb is semi-transparent, so a lighter rectangle of the track shows through; use an opaque disabled thumb colour or draw the thumb above the track.

*Carry-over from T-024 (PR #24 review, non-blocking):* (8) `MacroProgressBar.kt`: a negative consumed value shows as "−5 g" while the percent and fill treat it as 0; coerce it to 0 in the text too. (9) `MacroSlider.kt`: the header (label, "30 %", detail) adds three TalkBack stops that repeat the slider's spoken state; merge or hide it for TalkBack without breaking Maestro `assertVisible`. (10) `MacroSlider.kt`: `require(percent in valueRange)` crashes a release screen on an out-of-range value; use `coerceIn` or document it as a caller contract. (11) `ProgressRing.kt`: the status pill reuses `TrendPillDefaults.MinHeight`; give it its own constant or a shared internal container. (12) `DataVizCatalogSections.kt`: `remember` the `states` list. (13) `MacroProgressBar.kt`: `DotSize = 10.dp` is off the 4/8 dp grid.

*Carry-over from T-025 (QA):* (14) Not verifiable on the emulator, so include in the a11y-check decision (or a logged TalkBack check on the phone): a `SetRow` field reads "Kilos, serie N" + value and never the ghost placeholder; the checkbox reads "Completar serie N" + checked state; the rest timer reads "Descanso: 1 min 12 s" and the seconds-only branch "Descanso: 30 s"; `Confirm`/`ToggleOff` haptics on a physical device; a completed (read-only) row opens no keyboard (Maestro cannot check it: `hideKeyboard` without a keyboard sends BACK).

*Carry-over from T-025 (PR #25 re-review, non-blocking):* (15) `NumberInput.kt`: the internal state is written during composition right after it is read (extra recomposition); derive the shown value and assign state only in `onValueChange`. (16) `NumberInput` KDoc: `value` must be updated synchronously in `onValueChange` (a `StateFlow` + `collectAsState` caller gets a one-frame revert and the cursor jumps to the end on mid-text edits; matters for E3/E5). (17) `NumberInputTest.applyEdit_separatorThenDigit_givesZeroCommaDigit`: build the second input from the first result (type "5" at the cursor) instead of a hand-written value. (18) `NumberInput.kt`: put `clearAndSetSemantics {}` back on one line.

**Description:** `EmptyState` and `ErrorState` (icon + message + action) in the catalog, plus an accessibility pass on every catalog component: touch targets ≥ 48 × 48 dp and a `contentDescription` on every actionable icon. Decide and document how touch targets are tested (Compose UI test on the JVM if it works with the pinned versions; otherwise Maestro bounds checks + UX review).

**Acceptance criteria:**
- [x] Every component from `05-design-system.md` §6 is in the catalog except `UniversalFab`, `AppBottomSheet` (T-027) and `OfflineBanner` (T-029), which are added there.
- [x] Automated touch target / content description checks for the interactive catalog components, or a logged decision with the fallback.

**Verification:**
- [x] Lint + unit tests (and UI tests if adopted) green.
- [x] QA: `tests/e2e/T-026.yaml`; full regression green.

**Dependencies:** T-025
**Files likely touched:** `…/designsystem/{EmptyState,ErrorState}.kt`, catalog screen, a11y tests, `tests/e2e/T-026.yaml`
**Estimated scope:** S

---

### Checkpoint E1-D — Design system complete
- [x] US-03 scenarios green; catalog absent in `prodDebug`. (approved by the user 2026-10-13)
- [x] 👤 Manual check on the phone (`localDebug` catalog), things the emulator cannot verify: haptics when checking/unchecking a set; a short TalkBack pass (macro slider, kcal ring, set row field and checkbox, rest timer, "Reintentar"); a completed set row opens no keyboard. (approved by the user 2026-10-13)

---

## T-032: Maestro regression in GitHub Actions — DROPPED (2026-10-13)

> **Dropped at the user's request.** PR #27 (closed; branch `feature/T-032-maestro-ci` kept for reference) got 4 of 7 flows green on CI, but the API 37 emulator image (Android 17) aborts surfaceflinger and then `system_server` (`mapper.ranchu` `hasReadColorBufferDma` assertion; see rock3r/clockblock#122, JMR-dev/LibreMediaConverter#108), so the regression would be flaky. The user preferred dropping it over working around it with re-runs. QA keeps running the full regression locally (ADR-23 was never merged). Only the `android-ci` per-test counts (PR #26 review item) were kept, in the PR that drops this task. Revisit if a fixed emulator image ships.

**Story:** E1 process (added 2026-10-13 at the user's request) · **Flow:** PM + backend-developer (CI) · **Order:** first task after Checkpoint E1-D, before T-027

**Description:** A new GitHub Actions workflow runs the full Maestro regression on PRs that touch app code or `tests/e2e/`: an Android emulator on `ubuntu-latest` (KVM, `reactivecircus/android-emulator-runner`), local Supabase (`npx supabase start`), `:androidApp:installLocalDebug`, then `maestro test tests/e2e/` with screenshots and the Maestro debug output uploaded as artifacts. It is free on public-repo runners; no Maestro Cloud. QA's role shrinks to writing and updating flows, reviewing the screenshot artifacts (visual and font-scale checks) and debugging failures locally.

**Acceptance criteria:**
- [ ] New ADR (approved by the user) for e2e in CI; `planning/06-agent-workflow.md` (QA step), `CLAUDE.md` (flow step 5 and commands) and the qa-engineer agent instructions updated to match.
- [ ] Workflow with path filters, caching (Gradle, AVD snapshot), a timeout, and the emulator API level documented (API 37 if the image is available on the runner, otherwise the closest available level and why).
- [ ] The full regression (T-020…T-026) passes in CI 3 times in a row; any CI-only flakiness is fixed in the flows, not hidden by retries.
- [ ] Decide with the user whether the job becomes a required check on `main` (a branch protection change).
- [ ] Fast by design (user request, 2026-10-13): no Supabase in the job until E2 brings login flows (E1 flows don't use the backend); flows sharded across 2–3 parallel matrix jobs, each with its own emulator (target wall time ≈ 6–8 min); AVD snapshot + Gradle caching; path filters skip PRs that only touch docs or `tasks/`.
- [ ] From the PR #26 review: `android-ci` shows per-test counts on green runs too (Gradle test logging in `composeApp/build.gradle.kts`, or always upload `**/build/test-results/**`), so it is visible that the Robolectric host tests ran.
- [ ] QA's role shrinks: run the task's own flow and review the screenshot artifacts; CI owns the full regression, running in parallel with pr-reviewer.

**Verification:**
- [ ] `gh run list` shows 3 consecutive green e2e runs; artifacts contain the screenshots.

**Dependencies:** T-026
**Files likely touched:** `.github/workflows/e2e.yml`, `planning/00-decisions.md`, `planning/06-agent-workflow.md`, `CLAUDE.md`, `.claude/agents/qa-engineer.md`, `tests/e2e/*.yaml`
**Estimated scope:** M

---

## T-033: Catalog deep links for shorter e2e flows — DROPPED (2026-10-13)

> **Dropped at the user's request.** With T-032 dropped, the main reason (CI wall time) is gone, and a debug-only deep-link route adds app code without user value. Instead, the four catalog flows (T-023…T-026) carry the Maestro tag `catalog` and are left out of the per-task regression unless the task touches the design system or the catalog. They still run in the full regression at epic closure.

**Story:** US-03 (debug catalog) · **Flow:** UI · **Order:** next (user request, 2026-10-13; it was planned after T-032, which was dropped, and still shortens the local regression)

**Description:** A debug-only way to open the component catalog at one section (a route argument / deep link, or one screen per category), so e2e flows no longer scroll the whole catalog. Then shorten the T-023…T-026 flows, which are the slowest (2–3 min each) and the flakiest because of long scrolls.

**Acceptance criteria:**
- [ ] The section entry point exists only in `localDebug` (ADR-19 gating unchanged; `CatalogAvailabilityTest` still green) and has unit tests.
- [ ] T-023…T-026 flows open their sections directly; same assertions as before, no coverage lost.

**Verification:**
- [ ] Lint + unit tests green; the full regression is green and faster than before (timings in the PR).

**Dependencies:** T-026
**Files likely touched:** `…/app/{AppRoutes,AppNavHost}.kt`, `…/feature/catalog/*`, `androidApp` (deep link, if used), `tests/e2e/T-023…T-026.yaml`
**Estimated scope:** S

---

## T-027: Contextual FAB with WIP bottom sheets

**Story:** US-04 · **Flow:** UI · **Scenarios:** Contextual FAB (outline); FAB hidden on Profile

**Description:** `UniversalFab` (lime pill "+ Registro", 52 dp, 0.97 scale on press) and `AppBottomSheet` (top radius 24, 36×4 handle, 65 % scrim). The active route defines the FAB action (ADR-10): Diario → "Añadir alimento", Entreno → "Empezar entreno", Progreso → "Registrar peso", Perfil screen → hidden (ADR-22). Each action opens a WIP sheet with that title. Both components are added to the catalog.

*Carry-over from T-020 (UX):* M3 `ModalBottomSheet` defaults to `scrim.copy(alpha = 0.32f)`; `AppBottomSheet` must pass `scrimColor = AppTheme.colors.scrim` explicitly.

**Acceptance criteria:**
- [x] Route → FAB action mapping in one place, with unit tests for the 3 tabs and the Profile screen.
- [x] Sheet title per tab; dismiss by scrim, back and drag; `testTag`s for FAB and sheet.

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-027.yaml` runs the 3 outline rows and the Profile case; regression green.

**Dependencies:** T-026, T-031
**Files likely touched:** `…/designsystem/{UniversalFab,AppBottomSheet}.kt`, `…/app/{FabAction,AppScaffold}.kt`, test, strings, catalog, `tests/e2e/T-027.yaml`
**Estimated scope:** M

---

## T-028: Adaptive layout with NavigationRail on tablets

**Story:** US-04 · **Flow:** UI · **Scenarios:** Tablet layout

*Carry-over from T-031 (PR #22 review):* `AppScaffold.kt` always adds the bottom-bar height to the tab content's bottom padding (so scroll survives opening Profile). With the rail there is no bottom bar, so this must become conditional, otherwise a 64 dp empty gap appears at the bottom.

**Description:** Using the window size class, compact widths keep the bottom bar + FAB; ≥ 600 dp show a `NavigationRail` (3 tabs, same lime + filled-icon indicator, ADR-22) with the FAB at its top. Same tabs, routes and state. `displayHero`/`headlineMetric` use the tablet sizes on wide screens.

**Acceptance criteria:**
- [x] Width → navigation type decided by a pure function with unit tests (599 → bar, 600 → rail).
- [x] Rotating or resizing keeps the active tab and state.

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: phone scoped regression green; tablet checked on a tablet emulator (create a `Pixel Tablet` AVD if missing) with screenshots, or manually if no AVD (logged). *(`T-028.yaml` runs in phone landscape; `shell` flows and `T-028-catalog` green. No tablet AVD: manual tablet check moved to the T-030 👤 validation. Catalog flows T-026 and T-027-catalog were flaky in this run and are fixed before the E1 closure regression.)*

**Dependencies:** T-027
**Files likely touched:** `…/designsystem/AppNavigation.kt`, `…/app/AppScaffold.kt`, `…/designsystem/Typography.kt`, test
**Estimated scope:** S

---

## T-029: Connectivity observer and "Sin conexión" banner

**Story:** US-04 · **Flow:** UI · **Scenarios:** Offline

**Description:** `ConnectivityObserver` interface (`Flow<Boolean>`) in `shared` (`domain/port`), Android implementation with `ConnectivityManager.NetworkCallback` in `androidMain`, injected with Koin. The shell shows `OfflineBanner` below the Top App Bar while offline and hides it when the connection returns (ADR-01). Banner added to the catalog.

**Acceptance criteria:**
- [x] Shell state driven by the observer and covered by a ViewModel test with a fake observer (online → offline → online) using Turbine.
- [x] Android implementation emits the current state on collection and is unregistered on cancellation; `ACCESS_NETWORK_STATE` permission declared.

**Verification:**
- [x] Lint + unit tests green.
- [x] QA: `tests/e2e/T-029.yaml` with Maestro airplane mode (if unreliable on the emulator, manual check logged); scoped regression green. *(Maestro `toggleAirplaneMode`; `setAirplaneMode: enabled` did not take effect on the emulator. Scoped `shell` regression 6/6 green; rail in landscape offline checked.)*

**Dependencies:** T-028
**Files likely touched:** `shared/…/domain/port/ConnectivityObserver.kt`, `composeApp/src/androidMain/…/platform/AndroidConnectivityObserver.kt`, `…/app/AppViewModel.kt`, `…/designsystem/OfflineBanner.kt`, DI module, test, `tests/e2e/T-029.yaml`
**Estimated scope:** M

---

## T-030: E1 closure

**Story:** E1 Definition of Done · **Flow:** PM

**Description:** Check the epic DoD, deliver the APK and handle feedback.

**Acceptance criteria:**
- [x] US-01…US-04 merged with green CI; `deploy-supabase` and `keepalive` green at least once (T-018/T-019).
- [x] Stabilise the flaky catalog flows seen in T-028 QA (T-026 `catalog_section_state_messages` not found; T-027-catalog sheet title not visible). *(Longer `scrollUntilVisible` timeouts; the 20 s default was too short for the grown catalog.)*
- [x] `maestro test tests/e2e/` green on the emulator against local Supabase. *(2026-10-13: 12/12 flows in 21m 18s; T-029 re-run green after fixing a packaging encoding slip in its assertion.)*
- [x] `CLAUDE.md` "Current status" updated (E1 done, next E2); `tasks/todo.md` E1 ✅ (through a PR).
- [x] `./gradlew :androidApp:assembleProdDebug` → APK path given to the user; `[PM] [E1] [COMPLETED]` logged. *(Built on the branch; path given and log written after the merge.)*
- [x] 👤 The user validates navigation and style on phone and tablet → corrective tasks (T-031+) or approval → `git tag -a v0.1.0 -m "Epic E1"` + `git push --tags`, then the retrospective (E2 is broken down in a fresh session). *(2026-10-13: phone approved; tablet works (rail, rotation, offline banner) but the FAB in the rail feels awkward → refined in a later epic, non-blocking. Tagged `v0.1.0`.)*

**Verification:**
- [x] `gh run list` shows green `deploy-supabase` and `keepalive` runs; `git tag` lists `v0.1.0` after approval.

**Dependencies:** T-019, T-026, T-029
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

---

### Checkpoint E1-E — E1 closure
- [x] Full Maestro regression green (all tags).
- [x] Everything above verified; 👤 user approval → `v0.1.0`. *(approved by the user 2026-10-13)*

---

# E2 · Authentication and profile

Stories: `planning/epics/E2-auth-profile.md` (US-05…US-08). One task per story as a vertical slice (migration + RLS + pgTAP, repository, use case, ViewModel, screen, host tests); US-06's last scenario ("Editing data after onboarding") moves to T-037 because it needs US-07's TDEE. Plan: [tasks/plan.md](plan.md).
Flow: every E2 task has UI (planner → ux-reviewer → developers → PR → review → merge; the app is verified manually at the checkpoints).
Product gaps found in the breakdown were answered by the user on 2026-10-14 (ADR-24): onboarding resumes prefilled and re-saving updates the weight row; onboarding saves the default macros; Profile has one "Guardar"; "Peso actual" is read-only until E4.

## T-034: Sign in with Google, session routing and sign out

**Story:** US-05 · **Flow:** UI (frontend; no migration, `profiles` and its trigger exist since T-018) · **Scenarios:** First sign-in; Persistent session; Account picker cancelled; Network error when signing in; Sign out; Test login (local flavor only)

**Description:** Add supabase-kt (auth + postgrest; check `sb_publishable_` key support when pinning, ADR-17), androidx.credentials + `googleid` and Coil 3 to the catalog. `shared`: Supabase client built from `AppConfig`, `AuthCredentialProvider` port, `AuthRepository` (sign in with ID token, sign in with email/password for the local test user, sign out, `sessionStatus` as a `Flow`) and a minimal `ProfileRepository` (read own row: name, avatar, `onboarding_completed`). `composeApp/androidMain`: `GoogleAuthCredentialProvider` (Credential Manager, `GetGoogleIdOption`, `serverClientId = GOOGLE_WEB_CLIENT_ID`, nonce). Root routing from session + `onboarding_completed`: Welcome (no session) / Onboarding (stub destination until T-035) / Main (tabs, Diario). Welcome screen with "Continuar con Google"; the "Test login" button is contributed only by the `local` flavor (same mechanism as the catalog, `AppFeatures`). Profile gets "Cerrar sesión" with a confirmation dialog. The Top App Bar avatar shows the Google photo (Coil) with the E1 placeholder as fallback.

**Acceptance criteria:**
- [x] Root route is a pure function of (session status, onboarding flag) with unit tests: no session → Welcome; session + `false` → Onboarding; session + `true` → Main (Diario); session loading → splash/skeleton, never a flash of Welcome.
- [x] Credential result mapped to a sealed outcome (success / cancelled / no network / other error): cancelled → stays on Welcome with no message; no network → "Sin conexión. Inténtalo de nuevo" as a message only (the user taps "Continuar con Google" again; checkpoint feedback 2026-10-14); Welcome actions disabled while a sign-in is in flight.
- [x] Sign out asks for confirmation, clears the persisted session and returns to Welcome with the tab back stacks reset.
- [x] "Test login" signs in as `test@appfitness.local` (password from `planning/08-environment-setup.md` §3.2) and exists only in `local`: a `testProdDebugUnitTest` asserts it is absent in `prod`.
- [x] Avatar: Google photo when `avatar_url` is set, placeholder otherwise; ≥ 48 dp target and Spanish `contentDescription` unchanged.
- [x] E2E data state decided and documented in the plan (see `tasks/plan.md` risk "E2E user state"): the login subflow lands on Diario for every existing flow.

**Host tests (per scenario):** `RootRouteTest` (First sign-in → Onboarding, Persistent session → Main); `WelcomeViewModelTest` with a fake `AuthCredentialProvider`/`AuthRepository` + Turbine (First sign-in, Account picker cancelled, Network error + tapping Google again, Test login); `WelcomeScreenTest` (Robolectric: button, error message visible without a retry action, Test login visible only when the feature flag is on); `ProfileScreenTest` (Sign out: confirm dialog → sign-out event; cancel keeps the session); `testProdDebugUnitTest` for the Test login gating.

**E2E scope:** `auth` (new `tests/e2e/T-034.yaml`: Test login → Diario; relaunch → Diario without login; sign out → Welcome) + new `tests/e2e/subflows/login.yaml`. Every existing flow (`shell`, `catalog`) gains the login subflow; QA updates them, runs `auth,shell`, and the `catalog` flows at Checkpoint E2-A. Account picker and real Google sign-in are not automatable (checked at the checkpoint).

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` + `:androidApp:testProdDebugUnitTest` green.
- [x] Manual (developer, emulator, local flavor): Test login → Diario; relaunch keeps the session; sign out → Welcome.

**Scope added (user, 2026-10-14):** Welcome shows the app logo + "AppFitness" (no tagline); the launcher icon (adaptive, monochrome layer) uses the user-supplied logo.

**Dependencies:** E1 (`v0.1.0`)
**Files likely touched:** `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `composeApp/build.gradle.kts`, `shared/…/data/remote/SupabaseClientFactory.kt`, `shared/…/domain/port/AuthCredentialProvider.kt`, `shared/…/domain/repository/{AuthRepository,ProfileRepository}.kt`, `shared/…/data/repository/*`, `shared/…/di/*`, `composeApp/src/androidMain/…/platform/GoogleAuthCredentialProvider.kt`, `composeApp/…/feature/auth/{WelcomeScreen,WelcomeViewModel,WelcomeUiState}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost,AppRoot,AppViewModel,AppFeatures}.kt`, `composeApp/…/feature/profile/ProfileScreen.kt`, `composeApp/…/designsystem/AppTopBar.kt`, `androidApp/src/{local,testProd}/…`, strings, tests, possibly `supabase/seed.sql`
**Estimated scope:** L (about 20 files, many of them small wiring edits; kept as one PR because sign-in, routing and sign-out are one reviewable auth slice)

---

## T-035: Biometric onboarding (sex, birth date, height, weight, activity)

**Story:** US-06 · **Flow:** UI + backend · **Scenarios:** Completing onboarding (up to "taken to their goal setup"); Progress indicator and going back; Range validation (Edad, Altura, Peso); Resuming an interrupted onboarding; Finishing the biometric steps again after an interruption. *("Editing data after onboarding" and "Current weight is read-only on Profile" → T-037.)*

**Description:** Backend: one migration with the `measurement_source` enum and `body_measurements` per `03-data-model.md` §4 (weight 20–400, body fat 2–75, `unique (user_id, external_id)`, index `(user_id, measured_at desc)`, `set_updated_at()` trigger, RLS enabled and forced with the owner policy on `(select auth.uid())`) + pgTAP. App: `BodyMeasurementRepository` (insert, latest weight), `ProfileRepository` gains the biometric update, and a 5-step onboarding flow replacing the T-034 stub: sex (`SegmentedSelector`-style), date of birth, height in cm and weight with 1 decimal (a new `WheelPicker` design-system component, built here and added to the catalog), activity level (selectable cards with the `04-business-rules.md` §1.3 labels). "N / 5" indicator (regrouped by T-039 into 4 steps with "N / 4", the goal step included), "Atrás" keeps the chosen values, "Continuar" on step 5 saves the profile fields and the first `body_measurements` row (`source = 'manual'`) and moves to the goal step (stub until T-036).

**Acceptance criteria:**
- [x] Migration + `supabase/tests/body_measurements.test.sql`: RLS on and forced; A sees only their rows; A cannot insert with B's `user_id`, update or delete B's rows; range checks reject 19.9 / 400.1 kg.
- [x] Picker bounds come from one domain place: age 14–100 (birth date range relative to today), height 100–250 cm, weight 20.0–400.0 kg; unit tests on the bounds and the age calculation (full years).
- [x] Onboarding state survives configuration changes and process death (`SavedStateHandle`); "Atrás" restores the previous value; the indicator reads "3 / 5" on step 3 ("3 / 4" since T-039).
- [x] On save errors (offline, network) the values are kept and a message-only snackbar is shown; the user taps "Continuar" again (02 §9); "Continuar" disabled while saving.
- [x] Resuming (ADR-24): a user with `onboarding_completed = false` reopening the app starts at step 1 with every step prefilled from the saved profile and onboarding weight; "Continuar" on step 5 (the last biometric step; step 3 since T-039) again updates that weight row (no second row). The plan fixes how the onboarding row is identified; a repository test covers insert-then-update.
- [x] Activity step: each card shows the level title and its description from `04-business-rules.md` §1.3 (user request 2026-10-14; no extra hint).
- [x] `WheelPicker`: tabular numerals, ≥ 48 dp targets, `contentDescription`/`stateDescription` with the selected value, catalog entry, `testTag`s.

**Host tests (per scenario):** `OnboardingViewModelTest` (Completing onboarding → profile update + measurement insert with `manual`, navigation effect to goals; going back keeps the value; resume starts at step 1 prefilled; re-save updates the weight row instead of inserting; save failure keeps values); `OnboardingBoundsTest` (Range validation outline: 12 → age clamp to 14, 260 → 250, 15 → 20); `OnboardingScreenTest` (Robolectric: "3 / 5" ("3 / 4" since T-039), "Atrás", picker semantics); `WheelPickerTest`.

**E2E scope:** `onboarding`, `auth` (new `tests/e2e/T-035.yaml`: Test login with a not-onboarded user → 5 steps, back, indicator → goal step; updated by T-039's QA to the 4 steps and "N / 4").

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green and the migration listed as remote.

**Dependencies:** T-034
**Files likely touched:** `supabase/migrations/<ts>_body_measurements.sql`, `supabase/tests/body_measurements.test.sql`, `shared/…/domain/model/{BodyMeasurement,Profile}.kt`, `shared/…/domain/repository/BodyMeasurementRepository.kt`, `shared/…/data/repository/*`, `shared/…/domain/usecase/OnboardingBounds.kt`, `composeApp/…/feature/onboarding/{OnboardingScreen,OnboardingViewModel,OnboardingUiState}.kt`, `composeApp/…/designsystem/WheelPicker.kt`, catalog section, strings, tests
**Estimated scope:** L

---

## T-036: Energy strategy and calorie target; onboarding completes

**Story:** US-07 · **Flow:** UI + backend · **Scenarios:** TDEE calculation; Changing the strategy; Fine-tuning the target; Safety floor; Saving the goals in onboarding (all in the onboarding goal step; "Saving the goals on Profile" → T-037, which reuses the section)

**Description:** Backend: one migration with the `goal_strategy` enum and `nutrition_goals` per `03-data-model.md` §2 (one row per user, `strategy` default `maintenance`, `calorie_adjustment`, `target_kcal`, `protein_pct`/`carbs_pct`/`fat_pct` with defaults 30/45/25, each 5–90 and sum = 100 checks, owner RLS on `(select auth.uid())`) + pgTAP. Domain (pure, 100 % unit tested with the `04-business-rules.md` §1 examples): `CalculateTdee` (Mifflin-St Jeor + activity factor, age in full years, latest weight) and `CalculateCalorieTarget` (default adjustment per strategy −400 / 0 / +250, 50 kcal steps within the strategy range, safety floor by sex with a flag: 1200 women / 1500 men, ADR-25, "ligero/moderado/agresivo" label). App: `NutritionGoalsRepository` (upsert, read) and an "Estrategia energética" section (TDEE line, `SegmentedSelector` Déficit/Mantenimiento/Superávit, adjustment stepper, "Meta diaria: N kcal/día", adjustment pill, floor warning) used as the last onboarding step. No macro sliders in onboarding (ADR-24). "Guardar objetivos" upserts the goals with the default split 30/45/25, sets `onboarding_completed = true`, confirms with haptics and lands on Diario.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/nutrition_goals.test.sql`: RLS on and forced; cross-user isolation (select/insert/update/delete); percentage checks reject 4 / 91 and a sum ≠ 100.
- [x] Use case tests include: male 26 y, 178 cm, 74.2 kg, moderate → 2681; TDEE 2800 + Déficit → 2400 and "−400 kcal · Déficit moderado"; two steps down → −500 / 2300; female TDEE 1500 − 400 → 1200 + floor flag; male TDEE 1800 − 400 → 1500 + floor flag; range ends (−1000, −50, ±100, +50, +750) are not crossed; label thresholds 300 / 600.
- [x] Numbers formatted with `AppNumberFormat` ("TDEE: 2.681 kcal", "Meta diaria: 2.400 kcal/día").
- [x] Goals and `onboarding_completed = true` are written so that a failure never leaves `onboarding_completed = true` without a goals row (order or RPC chosen by the planner); save errors keep the values and show a message-only snackbar; the user taps the save button again.
- [x] After saving, relaunching the app goes straight to Diario (closes the US-05 "Persistent session" journey for a new user).

**Host tests (per scenario):** `CalculateTdeeTest`, `CalculateCalorieTargetTest` (TDEE calculation, Changing the strategy, Fine-tuning, Safety floor); `EnergyStrategyViewModelTest` (strategy switch resets the adjustment to the default, stepper, floor warning, Saving the goals → upsert + onboarding flag + haptic/confirmation effect); `EnergyStrategySectionTest` (Robolectric: texts "TDEE: 2.681 kcal", "Meta diaria: 2.400 kcal/día", pill, warning).

**E2E scope:** `onboarding`, `auth` (new `tests/e2e/T-036.yaml`: full onboarding → strategy → "Guardar objetivos" → Diario; relaunch → Diario; the goal step is "4 / 4" since T-039, which updates the flow).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-035
**Files likely touched:** `supabase/migrations/<ts>_nutrition_goals.sql`, `supabase/tests/nutrition_goals.test.sql`, `shared/…/domain/model/{NutritionGoals,GoalStrategy}.kt`, `shared/…/domain/usecase/{CalculateTdee,CalculateCalorieTarget}.kt` + tests, `shared/…/domain/repository/NutritionGoalsRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/goals/{EnergyStrategySection,EnergyStrategyViewModel,EnergyStrategyUiState}.kt`, `composeApp/…/feature/onboarding/*`, strings, tests
**Estimated scope:** L

---

### Checkpoint E2-A — New user journey on the phone 👤
- [ ] Every background QA run finished, no open `qa` issue, full Maestro regression green (all tags, catalog flows included); `deploy-supabase` applied both migrations in production.
- [ ] 👤 The user installs the `prodDebug` APK (`! python scripts/deliver-apk.py`) and tries, with their real Google account:
  - "Continuar con Google" → pick the account → the 5 onboarding steps (try "Atrás" and the picker limits) → choose a strategy, move the adjustment, "Guardar objetivos" → lands on Diario with their Google photo in the top bar.
  - Close the app and reopen it → straight to Diario. Open the Google picker and dismiss it → no error. Airplane mode + "Continuar con Google" → "Sin conexión. Inténtalo de nuevo".
  - Profile → "Cerrar sesión" → Welcome. Kill the app halfway through onboarding (second account, if available) → restarts at step 1 with the saved values prefilled.
  - Feedback on the onboarding pickers and the strategy step (cheap to change before the Profile task).
- Checkpoint E2-A feedback (2026-10-14), applied on `fix/E2-A-checkpoint-feedback`:
  1. Welcome logo bigger: 160 dp on phones, 200 dp from 600 dp up (the brand zone still scrolls on short windows; the buttons stay pinned to the bottom).
  2. "Reintentar" removed where it duplicated the primary button (Welcome sign-in errors, onboarding and goals save errors): they show the message only and the user taps the primary button again. Full-screen errors keep "Reintentar" (ProfileError, onboarding load error).
  3. `scripts/deliver-apk.py` checks the prod config (`supabase.prod.url`, `supabase.prod.publishableKey`, `google.webClientId` or the env equivalents) before building and names the missing keys.
- Checkpoint E2-A feedback, second round on the phone (2026-10-14): onboarding regrouped into 4 steps, the unit-label and weight-decimal bugs, activity card typography and the "Atrás" button → corrective **T-039**; Perfil becomes the 4th tab with the Google photo as its icon → **ADR-26**, implemented in T-037; a UI refinement epic is added at the end of Phase 1 (**E7**, `planning/07-roadmap.md` §1.1).

---

## T-039: Checkpoint E2-A corrections (onboarding in 4 steps, picker fixes, secondary button)

**Story:** US-06 + US-07 (onboarding) · **Flow:** UI (frontend only; no migration) · **Scenarios:** US-06 Completing onboarding; Progress indicator and going back ("3 / 4"); Range validation (Peso with 1 decimal); Resuming an interrupted onboarding; Finishing the biometric steps again after an interruption · US-07 Saving the goals in onboarding (regression)

**Description:** Corrections from the phone checkpoint (2026-10-14). Onboarding becomes 4 steps with the indicator "N / 4", the goal step counted: 1/4 "Sobre ti" (Sexo with its two options + Fecha de nacimiento wheels), 2/4 "Medidas" (Altura and Peso wheels side by side), 3/4 "Nivel de actividad" (cards), 4/4 "Objetivos" (strategy + adjustment). Saves stay where they are: "Continuar" on 3/4 writes the profile and the onboarding weight, then 4/4 "Guardar objetivos" writes the goals and then `onboarding_completed`. Fixes the "cm"/"kg" labels wrapping one letter per line and the weight wheel that only allows integers. The activity card description gets the typography chosen by ux-reviewer. "Atrás" (same position) moves to a filled, non-outlined secondary button designed by ux-reviewer as a reusable design-system component. Folds in the PR #50 review follow-ups (`.workflow/qa-followups.md`, second block).

**Acceptance criteria:**
- [x] Indicator "N / 4" on all four steps (goal step included); step titles "Sobre ti", "Medidas", "Nivel de actividad", "Objetivos"; "Atrás" restores the previous step with every value kept; `SavedStateHandle` restore and the resume prefill (ADR-24) still work, and a saved step name from the old 6-entry enum never crashes the restore.
- [x] "Continuar" on 1/4 is enabled only once a sex is chosen; on 3/4 only with an activity and online; "Continuar" on 3/4 saves profile + onboarding weight (re-save updates the row, no second row); "Guardar objetivos" on 4/4 behaves as in T-036.
- [x] Weight is pickable with 1 decimal over 20.0–400.0 kg (400 allows only ,0); the decimal wheel is visible and scrollable on a 360 dp phone; a host test proves picking 70,5 yields 705 tenths.
- [x] "cm" and "kg" render on one line (no wrapping) on a 360 dp phone at font scale 1.0 and 1.3.
- [x] Activity cards use the description style chosen by ux-reviewer (design-system tokens only).
- [x] "Atrás" uses the new filled, non-outlined secondary button from `designsystem/` (catalog entry, host test of colours, ≥ 48 dp, disabled state); `05-design-system.md` §6 records its spec.
- [x] PR #50 follow-ups: save-error snackbar `SnackbarDuration.Long`; `tasks/plan.md` partial-writes row reworded (done in the planning commit); `deliver-apk.py` `key value` / `\` continuation parsing handled or documented; `WelcomeScreenTest` w800dp case for the 200 dp logo.

**Host tests (per scenario):** `OnboardingViewModelTest` (4-step navigation and "Atrás" keeps values; save on 3/4; resume prefill; re-save updates; old saved step name restores safely); `OnboardingScreenTest` (Robolectric: "3 / 4", titles, decimal wheel → 70,5, unit labels on one line at 360 dp); `WheelPickerTest` (default width inside an unweighted `Row`); `ButtonsTest` + a Robolectric test for the new secondary button; `WelcomeScreenTest` (w800dp).

**E2E scope:** `onboarding`, `auth` (QA updates `tests/e2e/T-035.yaml` and `T-036.yaml` to 4 steps and "N / 4"; `catalog` flows at the next checkpoint for the button entry).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] Manual (developer, emulator, local flavor): 4 steps, weight 70,5 saved, labels on one line.

**Dependencies:** T-036, Checkpoint E2-A feedback
**Files likely touched:** `composeApp/…/feature/onboarding/{OnboardingScreen,OnboardingSteps,OnboardingUiState,OnboardingViewModel}.kt`, `composeApp/…/designsystem/{WheelPicker,Buttons,SelectableCard}.kt`, catalog, strings, `scripts/deliver-apk.py`, tests, `planning/05-design-system.md`
**Estimated scope:** M

---

## T-037: Profile: edit personal data, energy strategy and macro split

**Story:** US-08 + US-06 "Editing data after onboarding" / "Current weight is read-only on Profile" + US-07 on Profile · **Flow:** UI (no new table; the only possible migration is an optional `security invoker` RPC for an atomic save) · **Scenarios:** US-08 Initial display; Auto-balance when moving a slider; Per-macro minimum; Persistence (Diary half verified in E3) · US-06 Editing data after onboarding; Current weight is read-only on Profile · US-07 Saving the goals on Profile (other US-07 scenarios re-run on the Profile card)

**Description:** Navigation per ADR-26 (Checkpoint E2-A): Perfil becomes the 4th bottom tab / rail item (Diario, Entreno, Progreso, Perfil) whose icon is the user's Google photo (Coil, circle, placeholder fallback; active = extra-bold lime label + lime ring), the Top App Bar avatar is removed everywhere, and the full-screen Profile route from T-031/T-034 becomes a tab destination with its own back stack (no back arrow, no FAB). Profile becomes the real screen (sections from `design/stitch/profile-goals/screen.png`, only personal data, "Estrategia Energética" and "Distribución de Macros"; the sign-out from T-034 and the local-only catalog entry stay). Personal data card (Edad, Altura, Actividad and sex editable with the T-035 pickers; "Peso actual" read-only, latest weight, ADR-24); the T-036 strategy section reused as a card; a "Distribución de Macros" card with three `MacroSlider`s (P lime, C cyan, F amber), a stacked bar with the three macro colours and the "TOTAL: 100 %" badge. Domain: `BalanceMacros` (largest remainder, [5, 90], sum 100) with property tests, and grams = `round(target × pct / 100 / kcal_per_gram)`. One "Guardar" button for the whole screen (ADR-24) saves the profile fields, strategy, adjustment and macros, and stores the daily target recalculated from the new data and the latest weight (`04-business-rules.md` §1.4). Grams targets exposed from the domain so the E3 Diary can consume them.

**Acceptance criteria:**
- [x] `BalanceMacros` unit + property tests: sum is always 100 and every value in [5, 90] for random moves; 30/45/25 + P→35 → 35/42/23; 90/5/5 cannot raise P; one other at 5 → the rest of Δ goes to the other.
- [x] Grams/kcal text matches the Gherkin ("30 % · 180 g · 720 kcal", "45 % · 270 g · 1.080 kcal", "25 % · 67 g · 600 kcal"; after P→35: 210 g / 252 g / 61 g).
- [x] Editing height to 180 cm and tapping "Guardar" updates the profile, the displayed TDEE and the stored `target_kcal`; profile and goals are written so a failure never leaves them inconsistent (order or RPC chosen by the planner).
- [x] "Peso actual" shows the latest `body_measurements` weight and has no edit affordance.
- [x] Profile loads with a skeleton, shows `ErrorState` with "Reintentar" on a read failure, and keeps edits on a save failure (02 §9); save disabled offline.
- [x] ADR-26: 4 tabs on the bar and the rail; the Perfil icon shows the Google photo when `avatar_url` is set and the placeholder otherwise (also on load error), decorative (the label "Perfil" names it), with the active ring; no avatar in any `AppTopBar`; tab switching keeps each tab's state; sign-out still resets the tab back stacks.
- [x] Haptic confirmation on save; no FAB anywhere since ADR-36; `MacroStackedBar` (or equivalent) in the design system with a catalog entry and `stateDescription`.
- [x] *Carry-over check:* `MacroSlider` callers pass percentages within range (E1 PR #24 note: it `require`s or coerces; confirm which). → it coerces (clamps); callers pass `BalanceMacros` output, always in [5, 90].

**Delete account (ADR-29; user decision 2026-10-14; planner recommends splitting it into its own task/PR, see `.workflow/current_task.md`):** scenarios US-05 "Delete account (ADR-29)" and "Delete account cancelled".

```gherkin
  Scenario: Delete account (ADR-29)
    Given a signed-in user on Profile
    When they tap "Borrar cuenta"
    Then the dialog "¿Borrar tu cuenta?" is shown with the text "Se borrarán tu perfil, tu peso y tus objetivos. No se puede deshacer."
    When they tap "Borrar"
    Then their account and all their data (profile, weight entries and goals) are deleted
    And the session is removed from the device
    And the welcome screen is shown
```

- [x] "Borrar cuenta" (destructive ghost) below "Cerrar sesión"; dialog with the approved texts and "Cancelar" / "Borrar"; "Cancelar" deletes nothing; disabled offline.
- [x] Edge Function `delete-account` (`03-data-model.md` §7): caller resolved from the JWT only, auth user deleted with the server-side secret key, `profiles`/`body_measurements`/`nutrition_goals` cascade; `verify_jwt = true` in `config.toml`; `deno check` of its entrypoint added to `supabase-ci`; deployed by `deploy-supabase`.
- [x] Deno tests: 405 non-POST, 401 without/with invalid token, 200 deletes exactly the token's user (a `user_id` in the body is ignored), 500 `delete_failed` without leaking details.
- [x] App: success or 401 → local sign-out → Welcome; other failures keep the session with a transient message (no deletion). Host tests for the dialog, the disabled-offline state and each outcome.
- [x] The local test user is re-creatable after a manual deletion: `npx supabase db reset` (Maestro removed, ADR-27).

**Host tests (per scenario):** `BalanceMacrosTest` (Auto-balance, Per-macro minimum, properties); `MacroTargetsTest` (Initial display grams); `ProfileViewModelTest` with fakes (Initial display, slider move → balanced state, Persistence → upsert with the percentages, Editing data → profile update + TDEE and stored target recalculated, read-only weight, US-07 strategy changes saved by the single "Guardar"); `ProfileScreenTest` (Robolectric: badge "TOTAL: 100 %", texts per macro, sliders' `stateDescription`, save button states).

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` green (+ `npx supabase test db` if a migration is added).

**Dependencies:** T-039
**Files likely touched:** `composeApp/…/app/{AppRoutes,AppNavHost,AppRoot}.kt`, `composeApp/…/designsystem/{AppNavigation,AppNavigationParts,AppTopBar}.kt` (ADR-26), `shared/…/domain/usecase/{BalanceMacros,CalculateMacroTargets}.kt` + tests, `shared/…/domain/repository/*` (goals with macros, profile update), `composeApp/…/feature/profile/{ProfileScreen,ProfileViewModel,ProfileUiState,PersonalDataCard,MacroSplitCard}.kt`, `composeApp/…/designsystem/MacroStackedBar.kt`, catalog section, strings, tests
**Estimated scope:** L

---

## T-040: fix: Edad and Altura do not show the new value on Profile

**Story:** US-06 "Editing data after onboarding" · **Flow:** UI (frontend only; no migration) · **Scenarios:** US-06 Editing data after onboarding; Edited values are shown after saving (Checkpoint E2-B bug)

**Description:** At Checkpoint E2-B the user changed "Edad" (birth year) and "Altura" on Profile and the new value was not shown on the screen (the picker or the saved value is not reflected in the personal data card). The developer reproduces it with a failing test first, finds the cause (state not updated from the picker result, or the card reading the stale loaded value) and fixes it; nothing else changes. The screen is still the T-037 Profile (the restructure is T-041).

**Acceptance criteria:**
- [x] After choosing a new birth year or height in its picker, the personal data card shows the new value immediately, before and after "Guardar", and after reopening the screen.
- [x] Regression test for both fields (and the same check for Actividad and sex if they share the cause); no other behaviour changes.

**Host tests (per scenario):** `ProfileViewModelTest` (picking a birth year / height updates the state; save keeps it); `ProfileScreenTest` (Robolectric: the card text shows the new age and height after the picker confirms).

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-037
**Files likely touched:** `composeApp/…/feature/profile/{ProfileViewModel,ProfileUiState,PersonalDataCard}.kt`, tests
**Estimated scope:** S

---

## T-041: Perfil restructure: 3 tabs, root with photo and name, Preferencias, editable weight (ADR-37)

**Story:** US-04 (navigation) + US-05 (sign-out / delete on the root) + US-06 (name, weight, onboarding copy) + US-07 (TDEE copy) · **Flow:** UI (+ a possible migration only if the weight upsert is an RPC; the planner decides) · **Scenarios:** US-04 Navigating between tabs (3 tabs); US-05 sign-out and Delete account (regression, now on the Perfil root); US-06 Editing the weight in Preferencias; Current weight shows the latest from any source; Name from Google; Name missing in Google · US-07 TDEE calculation (new copy)

**Description:** Implements ADR-37 (Checkpoint E2-B feedback, 2026-10-19), superseding ADR-26.
1. **Navigation:** remove the Progreso tab; bar and rail have 3 tabs (Diario, Entreno, Perfil). The Perfil icon is the generic Stitch `person` glyph (`design/stitch/progress/code.html`), active state per ADR-22 (lime icon, extra-bold lime label); `AppAvatar` leaves the navigation. Progreso route, screen and strings are deleted.
2. **Perfil root:** the Google photo (`AppAvatar`, placeholder fallback) large, the name (`profiles.display_name`) below, a button "Preferencias" opening a full-screen destination (back arrow), "Cerrar sesión" and "Borrar cuenta" (moved from the T-037 Profile with their dialog and rules). Layout and sizes by ux-reviewer. The root is where later epics add statistics entry buttons (not built now).
3. **Preferencias:** the current T-037 Profile content (personal data, energy strategy, macros, one "Guardar") in a full-screen destination, plus an editable name and an editable "Peso actual".
4. **Weight:** saving a changed weight records today's manual `body_measurements` weight, replacing today's manual row if one exists; earlier weights stay as history; "Peso actual" shows the latest weight from any source (`03-data-model.md` §4). The recalculated target uses it.
5. **Name:** taken from Google `full_name`; when it is missing, onboarding step 1 "Sobre ti" also asks "¿Cómo te llamas?" (only then); always editable in Preferencias.
6. **Copy:** "TDEE: %s kcal" becomes "Gasto diario estimado: %s kcal" with the helper line "Calorías que quemas al día según tu cuerpo y tu actividad." in onboarding and Preferencias.

**Acceptance criteria:**
- [x] Bar and rail show exactly Diario, Entreno, Perfil; Diario is the start tab; each tab keeps its state; sign-out resets the back stacks; no Progreso route, string or catalog leftovers.
- [x] Perfil icon = `person` glyph, tinted like the other tabs, active = lime + extra-bold label; no photo in the navigation; no avatar in any `AppTopBar`.
- [x] Perfil root shows the photo (placeholder on missing/failed load), the name, "Preferencias", "Cerrar sesión" and "Borrar cuenta"; "Preferencias" opens the full-screen editor with a back arrow and back returns to the root; "Borrar cuenta" keeps the ADR-29 dialog, texts and offline rule; sign-out unchanged.
- [x] Editing the weight and tapping "Guardar" writes one manual row for today (replaces today's manual row; earlier rows untouched) and the displayed "Peso actual" is the latest row from any source; the target is recalculated with it; a newer non-manual row is shown and stays editable.
- [x] The name comes from Google; the "¿Cómo te llamas?" field appears in onboarding step 1 only when `full_name` is missing and blocks "Continuar" while blank (max 100 characters); the name is editable in Preferencias.
- [x] The new TDEE copy and helper line in onboarding and Preferencias; the number keeps `AppNumberFormat`.
- [x] If the weight write is an RPC or touches policies: new migration (never edit a merged one) + pgTAP cross-user isolation; otherwise no DB change.
- [x] Docs consistent with ADR-37 (already updated); `05-design-system.md` §6 records the final Perfil root layout.

**Host tests (per scenario):** `AppNavigationTest` (3 tabs, no photo, `person` icon, active cue); `AppRootTest`/shell test (start tab, state kept, no Progreso); `ProfileRootScreenTest` (photo placeholder, name, buttons, Preferencias navigation and back, sign-out, delete dialog); `PreferencesViewModelTest` (weight edit → today's manual upsert, latest-any-source display, name edit, recalculated target); `PreferencesScreenTest` (Robolectric: editable weight and name, new TDEE copy); `OnboardingViewModelTest` + `OnboardingScreenTest` (name step only when `full_name` is missing; copy); repository test for the same-day manual replace (and pgTAP if SQL changes).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green (+ `npx supabase test db` if a migration is added).

**Dependencies:** T-040
**Files likely touched:** `composeApp/…/app/{AppRoutes,AppNavHost,AppRoot}.kt`, `composeApp/…/designsystem/{AppNavigation,AppNavigationParts,AppAvatar}.kt`, `composeApp/…/feature/profile/*` (root + preferences split), `composeApp/…/feature/onboarding/*`, `shared/…/domain/repository/*` (weight upsert, name), strings, catalog, tests, optionally one migration
**Estimated scope:** L

---

## T-038: E2 closure

**Story:** E2 Definition of Done · **Flow:** PM

**Acceptance criteria:**
- [x] T-034…T-037, T-039, T-040 and T-041 merged with green CI; pgTAP isolation for `profiles`, `body_measurements` and `nutrition_goals` green; `deploy-supabase` green after the last migration.
- [x] Host (Robolectric) tests green for every E2 UI scenario; the user has verified the delivered APK at Checkpoint E2-B.
- [x] `CLAUDE.md` "Current status" and `tasks/todo.md` (E2 ✅) updated through a PR.

**Verification:**
- [x] `gh run list` shows the last `deploy-supabase` green; `git tag` lists `v0.2.0` after approval.

**Dependencies:** T-041
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

### Checkpoint E2-B — E2 closure 👤
- Checkpoint E2-B feedback (2026-10-19, first round): "Borrar cuenta" and the new onboarding were approved (apart from the TDEE copy). Changes → bug **T-040** (Edad/Altura not shown after editing) and **T-041** (**ADR-37**): 3 tabs without Progreso and the generic `person` icon for Perfil; Perfil root with the large Google photo, the name, a "Preferencias" button (the editable Profile in a full-screen destination), "Cerrar sesión" and "Borrar cuenta"; later statistics (E3 strength, E5 weight, E7 insights) become entries from Perfil; name from Google, asked in onboarding only when missing; editable weight in Preferencias (today's manual row); TDEE copy "Gasto diario estimado". **Checkpoint E2-B is re-presented after T-041**, then T-038.
- [x] 👤 The user installs the `prodDebug` APK (`! python scripts/deliver-apk.py`) and tries the Perfil tab (generic icon; photo and name on the root; "Preferencias"; ADR-37):
  - In Preferencias change their height, age and weight (and the other personal data) and tap the single "Guardar" → the new values are shown, the "Gasto diario estimado" and the daily target change; "Peso actual" shows the latest weight and is editable.
  - Switch Déficit / Mantenimiento / Superávit and move the adjustment; push it to the safety floor (1200 kcal women / 1500 kcal men).
  - Move each macro slider → the other two rebalance and the badge stays "TOTAL: 100 %"; try to push one past 90 %.
  - Save, close the app and reopen it → everything persisted. Approval → `git tag -a v0.2.0 -m "Epic E2"` + retrospective (compare the per-story sizing with E1).

---

# E3 · Training core

Stories: `planning/epics/E3-training-core.md` (US-09…US-15). One task per story as a vertical slice; US-11 (live session) is split into a backend task (T-044) and a UI task (T-045) because both halves are heavy; US-12 and US-13 (ghost data and rest timer, both layered on the live screen) are merged into T-046. Plan: [tasks/plan.md](plan.md).
Flow: every task with UI follows the full flow (planner → ux-reviewer → developers → PR → review → merge); T-044 is backend only (no ux-reviewer). The app is verified by hand at the 👤 checkpoints with `python scripts/deliver-apk.py`.
Forward-compatible schema (ADR-30): every training table ships with its *(E6)* columns and enums; E3 renders only `weight_reps` / `working` sets. Account deletion (ADR-29) must keep working: every new user-owned row cascades from `auth.users`.
Questions in `tasks/plan.md`: Q1, Q3 and Q4 resolved (ADR-38); Q2 resolved: the first-ever session of an exercise is a "PR". Superset rest configuration (rounds, reps, rest between exercises, rest between rounds) is part of T-043 and T-046.

## T-042: Exercise catalog: RepDB assets, search and filters, detail, custom exercises

**Story:** US-09 · **Flow:** UI + backend · **Scenarios:** Search and filter; Filter by equipment; Accent-insensitive search; Catalog row with thumbnail; Exercise detail with animation and how-to; Attribution; Creating a custom exercise; Custom exercise privacy

**Prerequisites (👤, before the task starts):** the user has the RepDB free bundle (`Biblioteca_Ejercicios_Free`: `free.es.json` + `images/flat`) and runs `python scripts/import-repdb.py <bundle>` (it generates the gitignored `library.json` and images, ADR-39). Q1 is resolved (ADR-38, ADR-39).

**Description:** Backend: one migration with the `muscle_group`, `equipment` and `exercise_metric_type` enums and `exercises` per `03-data-model.md` §5, including the *(E6)* columns (`secondary_muscles`, `metric_type` default `weight_reps`); `created_by` references `auth.users` **on delete cascade** (ADR-29); consistency check (custom ⇔ `created_by` set and `key` null); RLS enabled and forced, select `not is_custom or created_by = (select auth.uid())`, insert/update/delete only own custom rows. Catalog rows reach every database (local and production) as decided in Q1 (`seed.sql` never runs in production). App: the bundle lives in `composeApp` Compose resources (`composeResources/files/exercises/`, ADR-39), is excluded by `.gitignore`, and its absence never breaks the build or CI (host tests use a small synthetic fixture, never RepDB content); `scripts/deliver-apk.py` refuses to build when the bundle is missing and names the expected path. `ExerciseLibrary` parses `library.json` once (key → Spanish name, instructions, tips and poses; thumbnail = start pose); `ExerciseRepository` (catalog + own custom exercises, create custom); a pure search use case in `shared` (case- and accent-insensitive, muscle-group and equipment filters, in memory). Screens: an exercise picker (search field, muscle-group and equipment chips with Spanish labels, rows with the male thumbnail or a placeholder for custom exercises, visible attribution "Datos de ejercicios por RepDB (repdb.co)", "Crear ejercicio") built as a reusable destination that returns the selected exercise (T-043's builder and T-045's "Añadir ejercicio" reuse it); exercise detail with the new `ExerciseAnimation` design-system component (start↔peak crossfade, 3 s cycle, ease-in-out, brief pause on each pose; `inicio` only when system animations are off; `05-design-system.md` §6.1) and "Cómo hacerlo"; create-custom form (name, group, equipment) that returns the new exercise as selected. Until T-043 wires it into the builder, the picker is reached from the Entreno tab through a provisional entry chosen by the planner (removed in T-043).

**Acceptance criteria:**
- [x] Migration + `supabase/tests/exercises.test.sql`: RLS on and forced; catalog rows visible to every user; A's custom exercise invisible to B (Custom exercise privacy); B cannot update or delete A's custom rows or any catalog row; a custom row without `created_by` or with a `key` is rejected; deleting a user with custom exercises succeeds.
- [x] Catalog rows exist in local and production databases with `name`, `key`, `muscle_group`, `equipment`, `metric_type` as decided in Q1; no instructions or image paths in the DB (ADR-30).
- [x] Search: "press" + "Pecho" returns "Press de banca con barra" and "Press inclinado con mancuernas" (with the real bundle; the fixture mirrors these names); "dominadas" and "Dominádas" give identical results; equipment "Mancuerna" returns dumbbell exercises only.
- [x] Rows show the thumbnail (start pose); custom exercises (no `key`) and images that fail to load show the placeholder, never a crash. Catalog rows have no name in the DB (ADR-38), so rows whose key is missing from the bundle are hidden.
- [x] Attribution text visible on the picker (and on the detail if ux-reviewer places it there too).
- [x] "Crear ejercicio" prefills the search text as the name; the created exercise is custom, saved with the chosen group and equipment, and returned as selected; disabled offline (02 §9).
- [x] Bundle handling: `.gitignore` excludes it; `assembleProdDebug` and the unit tests pass in CI without it; `deliver-apk.py` checks it; `planning/08-environment-setup.md` documents where it goes.
- [x] `ExerciseAnimation`: catalog entry, `testTag`s, reduced-motion behaviour, `contentDescription` in Spanish.

**Host tests (per scenario):** `SearchExercisesTest` (Search and filter; Filter by equipment; Accent-insensitive search); `ExerciseLibraryTest` (fixture parsing, unknown key → no media); `ExercisePickerViewModelTest` (filters, create custom → selected result, offline disables create); `ExercisePickerScreenTest` (Robolectric: thumbnail vs placeholder; Attribution; "Crear ejercicio"); `ExerciseDetailScreenTest` (animation and "Cómo hacerlo"; reduced motion shows the start pose); pgTAP `exercises.test.sql` (Custom exercise privacy).

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; the production catalog row count matches the bundle.

**Dependencies:** E2 (`v0.2.0`), Q1, the bundle in place
**Files likely touched:** `supabase/migrations/<ts>_exercises.sql`, `supabase/tests/exercises.test.sql`, possibly `supabase/seed.sql` and a one-off generator script (Q1), `.gitignore`, `scripts/deliver-apk.py`, `shared/…/domain/model/{Exercise,MuscleGroup,Equipment,ExerciseMetricType}.kt`, `shared/…/domain/usecase/SearchExercises.kt`, `shared/…/domain/repository/ExerciseRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/exercises/{ExercisePickerScreen,ExercisePickerViewModel,ExerciseDetailScreen,CreateExerciseSheet,ExerciseLibrary}.kt`, `composeApp/…/designsystem/ExerciseAnimation.kt`, catalog, strings, test fixture, tests, `planning/08-environment-setup.md`
**Estimated scope:** L (if the plan passes ~15 non-trivial files, the planner may split it into backend + catalog data, then UI)

---

## T-043: Routine builder and "Mis rutinas"

**Story:** US-10 · **Flow:** UI + backend · **Scenarios:** Creating a routine; Reordering exercises; Grouping into a superset; Validation; Edit, duplicate and archive (the "sessions already performed do not change" half is asserted in T-045, when sessions exist)

**Description:** Backend: one migration with the `set_type` enum, `routine_folders` *(E6: created now so `routines.folder_id` has its target and E6 needs no migration; owner RLS + pgTAP, not rendered)*, `routines` and `routine_exercises` per `03-data-model.md` §5 with every *(E6)* column and its default; checks `target_sets` 1–10, `target_reps` 1–50, `rest_seconds` 0–600 default 120, `target_rpe` 1–10; at most one `is_next` per user (partial unique index); RLS on `routine_exercises` via the parent routine; a routine may only reference exercises the caller can see (catalog or own custom). Transactional save: `save_routine(jsonb)` (`security invoker`; inserts or replaces the routine and its exercises in one transaction) so a routine is never half-saved. App: `RoutineRepository` (list active routines with exercise count, get, save, duplicate, archive). Entreno tab main view becomes "Mis rutinas" (list, "Nueva rutina"; it later gains "Empezar entreno vacío" in T-045 and "Historial" in T-047; the T-042 provisional picker entry is removed). Builder: name, "Añadir ejercicio" through the T-042 picker, per exercise target sets/reps and rest (steppers or `NumberInput`, ux-reviewer), drag-and-drop reorder plus "Subir"/"Bajar" in the exercise menu (accessible alternative), "Agrupar con el siguiente" / ungroup, superset labels "Superserie A1"/"A2" (letter per group in order), "Guardar" disabled without a name or without exercises. Routine menu: edit, duplicate, archive (archived routines leave "Mis rutinas").

**Acceptance criteria:**
- [ ] Migration + `supabase/tests/routines.test.sql`: RLS on and forced on `routines`, `routine_exercises` and `routine_folders`; cross-user isolation for select/insert/update/delete; A cannot attach exercises to B's routine nor reference B's custom exercise; checks reject 0 / 11 sets and 601 s rest; `save_routine` is atomic (an invalid exercise row leaves nothing saved) and cannot write into another user's routine; deleting a user with routines succeeds.
- [ ] Creating "Torso fuerza" with "Press de banca con barra" and "Dominadas", 3 × 8 and 120 s each, shows it in "Mis rutinas" with "2 ejercicios".
- [ ] Dragging C to the first position saves C, A, B; "Subir"/"Bajar" give the same result; the order survives a reload.
- [ ] "Agrupar con el siguiente" on "Press de banca con barra" shows "Superserie A1" / "A2"; grouping is stored in `superset_group` (consecutive exercises, same value); ungrouping clears it.
- [ ] "Guardar" disabled with a blank name or no exercises; save errors keep the edits with a message-only snackbar (02 §9); write actions disabled offline.
- [ ] Edit, duplicate and archive reflected in "Mis rutinas"; the *(E6)* columns are written with their defaults and never rendered.
- [ ] Builder state survives configuration changes and process death (`SavedStateHandle`).

**Host tests (per scenario):** `SupersetLabelsTest` (groups → "A1", "A2", "B1"…); `RoutineBuilderViewModelTest` with fakes + Turbine (Creating a routine; Reordering C, A, B by drag and by Subir/Bajar; Grouping into a superset; Validation; Edit, duplicate and archive); `RoutineBuilderScreenTest` (Robolectric: "Guardar" disabled state, superset labels, move-up/down menu); `RoutinesScreenTest` ("Mis rutinas" list, exercise count, empty state); repository test of the `save_routine` payload; pgTAP `routines.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-042
**Files likely touched:** `supabase/migrations/<ts>_routines.sql`, `supabase/tests/routines.test.sql`, `shared/…/domain/model/{Routine,RoutineExercise,SetType}.kt`, `shared/…/domain/usecase/SupersetLabels.kt`, `shared/…/domain/repository/RoutineRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/routines/{RoutinesScreen,RoutinesViewModel,RoutineBuilderScreen,RoutineBuilderViewModel,RoutineBuilderUiState}.kt`, `composeApp/…/feature/workout/WorkoutScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, possibly a reorderable-list helper in `designsystem/`, strings, tests
**Estimated scope:** L

---

## T-044: Workout sessions backend and training domain

**Story:** US-11 (backend half) + the `exercise_history` RPC of US-15 (needed now for PR detection) · **Flow:** backend (no UI, no ux-reviewer) · **Scenarios (data side):** US-11 Starting from a routine (session + pending sets); Completing a set (upsert); Continuing after closing the app (in-progress session with its sets); Finishing and summary (volume "2.120 kg", PR); Finishing with incomplete sets (pending sets dropped); Discarding the session · US-15 Estimated 1RM record (SQL side)

**Description:** One migration with the `session_status` enum, `workout_sessions` and `workout_sets` per `03-data-model.md` §5 with every *(E6)* column (`description`, `gym`, `paused_at`; `set_type` default `working`, `rpe`, `duration_s`, `distance_m`, `steps`, `floors`); at most one `in_progress` session per user (partial unique index); `unique (session_id, exercise_position, set_index)`; checks `weight_kg ≥ 0`, `reps ≥ 0`, `rpe` 1–10; indexes for the history (`user_id, started_at desc`) and per-exercise lookups; RLS on `workout_sets` via the parent session; FKs chosen so account deletion (ADR-29) still succeeds with sessions and custom exercises. The RPC `exercise_history(p_exercise_id, p_from)` (`security invoker`; per completed session: date, best estimated 1RM with Epley rounded to 0.5 kg, total volume; only completed sets) ships here because the summary (T-045) and the history (T-047) need the previous best 1RM; T-048 reuses it for the charts. Finishing is one transaction (pending sets deleted + `completed` + `ended_at`; an RPC or equivalent, planner's choice). Domain (pure, `04-business-rules.md` §4 examples as tests): `EstimateOneRepMax` (§4.1), `CalculateVolume` (§4.2), `DetectPersonalRecords` (§4.5; first-ever session per Q2). Session model with a **polymorphic set entry** (a sealed type keyed by `metric_type`; E3 maps only `weight_reps`) so E6 extends it without a rewrite (ADR-30). `WorkoutSessionRepository`: start from a routine (name snapshot, pending sets from the targets, `superset_group` copied), start empty, get the in-progress session with its sets, upsert a set, finish, discard.

**Acceptance criteria:**
- [ ] Migration + `supabase/tests/workout_sessions.test.sql`: RLS on and forced on both tables; cross-user isolation (select/insert/update/delete, including a set inserted into B's session); a second `in_progress` session for the same user fails; the upsert key replaces instead of duplicating; deleting a user with sessions, sets and custom exercises succeeds.
- [ ] `exercise_history` pgTAP with the domain cases: 100 kg × 5 → 116.5; 85 kg × 8 → 107.5; 100 kg × 4 → 113.5; sets with 0 reps, > 12 reps or 0 kg excluded from the 1RM; discarded, in-progress and other users' sessions ignored; volume per session.
- [ ] Domain tests use the same cases; volume of 60 × 12 + 87.5 × 8 + 87.5 × 8 = 2120 (formatted "2.120 kg" with `AppNumberFormat`); PR flag per §4.5 and Q2.
- [ ] Finishing keeps only completed sets and is atomic; discarding sets `discarded` and the session leaves every history query.
- [ ] Repository tests with fakes for start-from-routine (3 × 8 → 3 pending rows per exercise), resume, upsert, finish and discard.

**Host tests (per scenario):** `EstimateOneRepMaxTest`, `CalculateVolumeTest` (Finishing and summary volume), `DetectPersonalRecordsTest`, `WorkoutSessionRepositoryTest` (Starting from a routine, Completing a set, Continuing after closing, Finishing with incomplete sets, Discarding); pgTAP `workout_sessions.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-043
**Files likely touched:** `supabase/migrations/<ts>_workout_sessions.sql`, `supabase/tests/workout_sessions.test.sql`, `shared/…/domain/model/{WorkoutSession,WorkoutSet,SetEntry,SessionStatus}.kt`, `shared/…/domain/usecase/{EstimateOneRepMax,CalculateVolume,DetectPersonalRecords}.kt` + tests, `shared/…/domain/repository/WorkoutSessionRepository.kt`, `shared/…/data/repository/*`, `shared/…/data/remote/*Dto.kt`, DI
**Estimated scope:** M–L

---

## T-045: Live workout session

**Story:** US-11 (UI half) + US-10 "Edit, duplicate and archive" (sessions-unchanged half) · **Flow:** UI · **Scenarios:** Starting from a routine; Screen always on; Completing a set; Unchecking a set; Adding sets and exercises; Continuing after closing the app; Finishing and summary; Finishing with incomplete sets; Discarding the session; Network error when saving a set · US-10 sessions already performed do not change

**Description:** Entreno main view gains "Empezar" on each routine and "Empezar entreno vacío" (per-screen actions, ADR-36); when an `in_progress` session exists the Entreno tab opens it directly (also after a cold start). Live session screen (reference `design/stitch/live-workout/screen.png`, without RPE, CAL/EFEC or "Fase / Microciclo"): header with the "EN VIVO" stopwatch (from `started_at` and the injected `Clock`), the "Pantalla activa" indicator and "Finalizar"; one card per exercise (superset labels from T-043) with the existing `SetRow` table SERIE · ANTERIOR · KG · REPS · LISTO (ANTERIOR shows "—" until T-046), rendered through the polymorphic set row (only `weight_reps` in E3); KG with a decimal keyboard and 0.25 kg precision. Checking LISTO fills the round `SetCompletionCheckbox` in neonLime, triggers the `Haptics` port and upserts the set at once; unchecking makes it pending and editable. "Añadir serie" (placeholder = previous set's values), "Añadir ejercicio" through the T-042 picker (appended with one empty set, rest 120 s). `keepScreenOn` as a `DisposableEffect` on the session screen only (never a `WakeLock`), released on leaving and after finishing. "Finalizar" opens an `AppBottomSheet` (warning "Hay N series sin completar; no se guardarán" when pending sets exist) → summary (duration, completed sets, volume per exercise, "PR" marks). "Descartar entreno" with a confirmation → discarded, back to "Mis rutinas". Offline: checking a set keeps it in an in-memory retry queue in the ViewModel, marks it "pendiente de guardar" under the "Sin conexión" banner and flushes it when the connection returns while the session is open (ADR-01 exception); starting a session is disabled offline.

**Acceptance criteria:**
- [ ] "Empezar" on "Torso fuerza" (Press de banca 3 × 8, Dominadas 3 × 8) creates one in-progress session and shows 3 rows per exercise with the five column headers; "Empezar entreno vacío" starts a session with no exercises.
- [ ] The window keeps the screen on only while the session screen is visible; leaving it or finishing restores the normal timeout.
- [ ] Completing 60 kg × 12 in set 1: checked state, one haptic, one upsert; unchecking returns it to pending and editable.
- [ ] "Añadir serie" → set 4 with set 3's values as a placeholder; "Añadir ejercicio" → "Press militar con mancuernas" at the end with one empty set.
- [ ] Killing and reopening the app with 2 completed sets opens the Entreno tab on the session with both sets checked.
- [ ] Summary for 60 × 12, 87.5 × 8, 87.5 × 8 shows the duration, 3 completed sets and "2.120 kg"; exercises with a new estimated 1RM record show "PR" (Q2 rule).
- [ ] Finishing with 3 pending sets shows "Hay 3 series sin completar; no se guardarán" and keeps only the completed ones.
- [ ] Discarding asks for confirmation; the session is `discarded` and never appears in the history (checked again in T-047).
- [ ] Offline check → "pendiente de guardar" + banner; reconnecting saves it with no user action; no set is lost while the session stays open.
- [ ] Editing or archiving a routine after a session leaves that session's name and sets unchanged (US-10).
- [ ] Every interactive element has a snake_case `testTag` (e.g. `workout_set_2_done`); targets ≥ 48 dp.

**Host tests (per scenario):** `LiveSessionViewModelTest` with fakes, a test `Clock` and Turbine (Starting from a routine; Completing a set; Unchecking; Adding sets and exercises; Continuing after closing; Finishing and summary; Finishing with incomplete sets; Discarding; Network error → queue → flush on reconnect); `LiveSessionScreenTest` (Robolectric: headers, checkbox state, "EN VIVO", "Pantalla activa", sheet warning text, summary "2.120 kg" and "PR"); `KeepScreenOnTest` (flag on while shown, off after leaving and after finishing); `WorkoutTabRouteTest` (in-progress session → live screen); `RoutineSnapshotTest` (US-10 sessions unchanged).

**Verification:**
- [ ] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] Manual (developer, emulator, local flavor): start, check sets, kill and reopen, finish; airplane mode while checking a set.

**Dependencies:** T-044
**Files likely touched:** `composeApp/…/feature/workout/{WorkoutScreen,LiveSessionScreen,LiveSessionViewModel,LiveSessionUiState,SessionSummaryScreen,FinishSessionSheet,SessionSetRow}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, `composeApp/src/androidMain/…/platform/*` (keep-screen-on helper if needed), `designsystem/{SetRow,NumberInput}.kt` (only if the polymorphic row needs a slot; check every caller first), strings, tests
**Estimated scope:** L

---

## T-046: Ghost data and rest timer in the live session

**Story:** US-12 + US-13 · **Flow:** UI + backend (`last_performance`) · **Scenarios:** US-12 Showing the previous performance; No previous performance; More sets than last time; Using the previous performance · US-13 Automatic start; Quick adjustments; Free navigation; End of rest; Accuracy with the app in the background; Superset; Rest configurable during the session

**Description:** Backend: one migration with `last_performance(p_exercise_id)` (`security invoker`; sets `set_index`, `weight_kg`, `reps` of the caller's most recent **completed** session containing the exercise, completed sets only, regardless of routine) + pgTAP. App: ghost data loaded once per exercise when the session starts or an exercise is added; ANTERIOR shows "85 kg × 8" in `ghostData` matched by `set_index` ("—" when missing); KG/REPS show the values as a dim placeholder (never as a value); tapping the ANTERIOR text fills both fields. Rest timer (`04-business-rules.md` §4.4): state from an `endsAt` instant and the injected `Clock`, never a decrementing counter; starts on LISTO with the exercise's `rest_seconds` (120 s for exercises added in the session, 0 = no timer); checking another set restarts it; "+30s" and "Omitir"; in a superset it starts only after the set of the last exercise in the group, with the rest decided in **Q3**; the existing `RestTimerBar` pinned below the Top App Bar with the content inset so it never covers set rows or controls; at 00:00 the `Haptics` port plus a short alert through a new sound port in `androidMain` (silent when the system is muted; only with the app visible, no foreground service), then the bar hides. Exercise menu "Descanso" changes the rest for the rest of this session only (no routine write; E6 adds "Update Routine"). Adds the DoD full-session host test.

**Acceptance criteria:**
- [ ] Migration + `supabase/tests/last_performance.test.sql`: returns the latest completed session's sets; ignores other users', discarded and in-progress sessions and pending sets.
- [ ] Ghost: "85 kg × 8" in ANTERIOR for set 2; placeholders 85 and 8; "—" for a never-done exercise and for set 4 when last time had 3; tapping fills 85 and 8.
- [ ] Timer: LISTO with 120 s → "02:00", cyan decreasing line, "+30s" and "Omitir"; "01:12" + 30 s → "01:42"; "Omitir" hides it; 00:00 → vibration + alert + hidden; 60 s left, 30 s in the background → "00:30" on return.
- [ ] Superset A1/A2: completing A1's set starts nothing; completing the matching A2 set starts the timer with the Q3 rest.
- [ ] Changing an exercise's rest to 90 s from its menu applies to its following sets in this session; the routine is unchanged.
- [ ] The bar never overlaps set rows or controls while scrolling or editing (bounds asserted in a Robolectric test).
- [ ] DoD: a host test runs a full session of a 4-exercise routine with a superset (short rest durations), with correct ghost data and rest periods.

**Host tests (per scenario):** `GhostDataTest` (Showing the previous performance; No previous performance; More sets than last time; format); `RestTimerTest` with `runTest` + `advanceTimeBy` and a test `Clock` (Automatic start; Quick adjustments; End of rest effects; Accuracy with the app in the background; Rest configurable); `SupersetRestTest` (Superset); `LiveSessionViewModelTest` additions (Using the previous performance; timer restart on another set); `LiveSessionScreenTest` (Robolectric: ghost text, placeholder semantics, bar texts, Free navigation non-overlap); `FullSessionTest` (Epic DoD); pgTAP `last_performance.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-045, Q3
**Files likely touched:** `supabase/migrations/<ts>_last_performance.sql`, `supabase/tests/last_performance.test.sql`, `shared/…/domain/usecase/{GhostData,RestTimer}.kt` + tests, `shared/…/domain/port/AlertSound.kt`, `shared/…/domain/repository/WorkoutSessionRepository.kt`, `shared/…/data/repository/*`, `composeApp/src/androidMain/…/platform/AndroidAlertSound.kt`, `composeApp/…/feature/workout/{LiveSessionScreen,LiveSessionViewModel,LiveSessionUiState,ExerciseMenu}.kt`, DI, strings, tests
**Estimated scope:** L

---

### Checkpoint E3-A — A real workout on the phone 👤
- [ ] T-042…T-046 merged, CI green, `deploy-supabase` applied the four migrations in production, every reported bug fixed.
- [ ] 👤 The user installs the `prodDebug` APK (`! python scripts/deliver-apk.py`, with the RepDB bundle in place) and tries:
  - Entreno → "Nueva rutina": search "press" with "Pecho", filter "Mancuerna", type "dominádas"; open an exercise (animation, "Cómo hacerlo", RepDB attribution); create a custom exercise; drag to reorder, "Agrupar con el siguiente", "Guardar"; then edit, duplicate and archive a routine.
  - At the gym, "Empezar" a routine with a superset: the screen stays on; log sets with LISTO (vibration), uncheck one, "Añadir serie" / "Añadir ejercicio"; the rest bar ("+30s", "Omitir", the alert at 00:00, leaving the app and coming back), the superset waits for A2; change an exercise's rest from its menu.
  - Close the app mid-workout and reopen → back in the session; airplane mode while checking a set → "pendiente de guardar", then saved on reconnecting.
  - "Finalizar" with pending sets → warning, summary with volume and "PR"; a second session of the same routine shows ANTERIOR "kg × reps" and tapping it fills the fields; "Descartar entreno" on a throwaway session.
  - Placed here because the live session is the riskiest user-visible part of E3 and its feedback can still shape history and charts.

---

## T-047: Session history

**Story:** US-14 · **Flow:** UI (+ one migration only if the planner computes the list aggregates in SQL) · **Scenarios:** Session list; Session detail; Correcting a set; Deleting a session; Pagination

**Description:** Entreno main view gains "Historial". List of the caller's completed sessions (never `in_progress` or `discarded`), newest first by `started_at`, 20 per page (`limit`/`offset`), loading the next 20 at the end of the list; each row shows date ("Hoy", "Ayer", "lun, 24 oct"; `04` §5), name, duration, total volume and number of completed sets (aggregates in the client or a `security invoker` view/RPC, planner's choice; SQL means a new migration + pgTAP). Detail: each exercise with its sets "87,5 kg × 8" and the PRs achieved in that session (`DetectPersonalRecords` against earlier sessions); editing a set (`NumberInput`) saves it and recalculates the volume; deleting the session (confirmation dialog) removes it and its sets, so it is no longer previous performance or history. E6 adds the richer detail, calendar and copy/save-as-routine; not built here.

**Acceptance criteria:**
- [ ] List newest to oldest with date, name, duration, total volume ("2.120 kg") and number of sets; discarded and in-progress sessions never listed; empty state when there are none.
- [ ] Detail shows exercises, sets and "PR" marks consistent with the T-045 summary.
- [ ] Editing 87,5 kg × 8 to 87,5 kg × 7 saves it and the volume updates in the detail and the list; save errors keep the edit with a message-only snackbar; disabled offline.
- [ ] Deleting asks for confirmation; the session disappears from the list and `last_performance` no longer returns it (repository/pgTAP check).
- [ ] With 25 sessions, scrolling to the end loads the remaining 5 without duplicates; a page error shows a retry at the list end.

**Host tests (per scenario):** `HistoryViewModelTest` with fakes + Turbine (Session list; Pagination; Deleting a session); `SessionDetailViewModelTest` (Session detail PRs; Correcting a set → volume recalculated); `HistoryScreenTest` and `SessionDetailScreenTest` (Robolectric: row texts, "PR", edit field, delete dialog); pgTAP only if SQL is added (aggregates isolation; deleted session not returned by `last_performance`).

**Verification:**
- [ ] Lint + `testLocalDebugUnitTest` green (+ `npx supabase test db` if a migration is added).

**Dependencies:** T-046
**Files likely touched:** `composeApp/…/feature/workout/{HistoryScreen,HistoryViewModel,SessionDetailScreen,SessionDetailViewModel}.kt`, `composeApp/…/feature/workout/WorkoutScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, `shared/…/domain/repository/WorkoutSessionRepository.kt` (paged list, detail, update set, delete), `shared/…/data/repository/*`, optionally one migration + pgTAP, strings, tests
**Estimated scope:** M–L

---

## T-048: Strength progress: "Fuerza" screen, exercise charts and weekly muscle volume

**Story:** US-15 · **Flow:** UI + backend (`weekly_muscle_volume`) · **Scenarios:** Summary card on the strength screen; Estimated 1RM record; Exercise chart; Weekly volume per muscle group; Exercise without enough data; Exercises without load

**Description:** Backend: one migration with `weekly_muscle_volume(p_from)` (`security invoker`; per ISO week Monday–Sunday and the exercise's `muscle_group`, completed sets of completed sessions) + pgTAP; `exercise_history` exists since T-044. Add Vico (latest stable, multiplatform) and a chart component in `designsystem/` (athleticCyan, tabular numerals, catalog entry, `stateDescription` summarising the data). Perfil root gains its first statistics entry "Fuerza" (ADR-37; layout by ux-reviewer) opening a full-screen destination with a back arrow: the "Fuerza y 1RM estimado" card with chips for the 3 exercises with most sessions in the last 6 weeks (the most frequent selected by default), "Récord 1RM estimado: 113,5 kg" and "+5,0 kg este mes" in green (`TrendPill`) against the best 1RM as of 30 days ago; an entry "Volumen por grupo muscular" (last 8 weeks per group, selecting a group shows only its evolution). Per-exercise detail: best estimated 1RM per session and total volume per session, filters 1M, 3M, 6M, 1A; a single session shows its value and "Necesitas al menos 2 sesiones para ver la evolución"; an exercise logged only with 0 kg shows max reps instead of 1RM and volume. The detail is reachable from the card, from the exercise detail (T-042) and from the session detail (T-047). Domain: `StrengthSummary` (top 3, record, monthly delta) with tests from `04` §4.

**Acceptance criteria:**
- [ ] Migration + `supabase/tests/weekly_muscle_volume.test.sql`: ISO-week grouping (a Sunday and the next Monday fall in different weeks), completed only, other users ignored.
- [ ] With Press de banca, Sentadilla and Peso muerto trained in the last 6 weeks, the card shows those 3 chips with "Press de banca con barra" selected.
- [ ] Best set 100 kg × 4 → "Récord 1RM estimado: 113,5 kg"; best 30 days ago 108,5 kg → "+5,0 kg este mes" in green.
- [ ] Exercise detail: two charts (1RM, volume) and the 1M / 3M / 6M / 1A filters change the range; single-session message; Dominadas at 0 kg → max-reps chart.
- [ ] Weekly volume: 8 weeks per group; selecting a group isolates it.
- [ ] Empty state on "Fuerza" for a user with no completed sessions; loading skeleton and `ErrorState` with "Reintentar" on a failed read.
- [ ] Perfil root keeps sign-out and delete-account behaviour unchanged.

**Host tests (per scenario):** `StrengthSummaryTest` (Summary card; Estimated 1RM record); `ExerciseProgressViewModelTest` (Exercise chart filters; Exercise without enough data; Exercises without load); `MuscleVolumeViewModelTest` (Weekly volume per muscle group); `StrengthScreenTest` and `ExerciseProgressScreenTest` (Robolectric: chips, texts "Récord 1RM estimado: 113,5 kg", "+5,0 kg este mes", filters, message); `ProfileRootScreenTest` update (Fuerza entry); pgTAP `weekly_muscle_volume.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-047
**Files likely touched:** `supabase/migrations/<ts>_weekly_muscle_volume.sql`, `supabase/tests/weekly_muscle_volume.test.sql`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `shared/…/domain/usecase/StrengthSummary.kt` + tests, `shared/…/domain/repository/StrengthRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/analytics/{StrengthScreen,StrengthViewModel,ExerciseProgressScreen,ExerciseProgressViewModel,MuscleVolumeScreen,MuscleVolumeViewModel}.kt`, `composeApp/…/designsystem/AppLineChart.kt`, `composeApp/…/feature/profile/ProfileRootScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, catalog, strings, tests
**Estimated scope:** L (if the plan passes ~15 non-trivial files, the planner may move the weekly muscle volume into its own UI follow-up task)

---

## T-049: E3 closure

**Story:** E3 Definition of Done · **Flow:** PM

**Acceptance criteria:**
- [ ] T-042…T-048 merged with green CI; pgTAP for `exercises`, `routines`, `routine_exercises`, `routine_folders`, `workout_sessions`, `workout_sets`, `last_performance`, `exercise_history` and `weekly_muscle_volume` green; `deploy-supabase` green after the last migration.
- [ ] Host tests green for every E3 UI scenario, including the full-session test (T-046).
- [ ] Forward compatibility checked (ADR-30): every *(E6)* column and enum of `03-data-model.md` §5 exists in production.
- [ ] Every bug the user reported at the checkpoints fixed; UI-only feedback appended to the E8 backlog.
- [ ] `CLAUDE.md` "Current status" and `tasks/todo.md` (E3 ✅) updated through a PR.

**Verification:**
- [ ] `gh run list` shows the last `deploy-supabase` green; `git tag` lists `v0.3.0` after approval.

**Dependencies:** T-048
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

### Checkpoint E3-B — E3 closure 👤
- [ ] 👤 The user installs the `prodDebug` APK (`! python scripts/deliver-apk.py`) and tries:
  - Entreno → "Historial": sessions newest first with date, duration, volume and sets; open one (sets and "PR"), change a set's reps and see the volume update; delete a session and check it no longer shows as ANTERIOR.
  - Perfil → "Fuerza": the chips of the most trained exercises, the 1RM record and the monthly change; open an exercise (1RM and volume charts, 1M/3M/6M/1A), one with a single session (message) and Dominadas (max reps); "Volumen por grupo muscular" and pick a group.
  - Open the progress of an exercise from its catalog detail and from a history session.
  - Approval → `git tag -a v0.3.0 -m "Epic E3"` + retrospective (includes the Claude Code workflows discussion from `retrospectives/E2.md`).

---

## Notes for later epics

- **E3 (food diary), from E2 (T-037):** US-08's "And the Diary uses the new gram targets" is only verified on the Profile side in E2; the E3 Diary must read the macro gram targets from the domain use case added in T-037 (not recompute them).
- **E3 / E4 / E5 FAB question (E1 review):** resolved by ADR-36 (no universal FAB); E3's per-screen actions are in T-043 and T-045.
- **Tablet (later epic, user feedback 2026-10-13):** the FAB at the top of the NavigationRail feels awkward; move the primary action into each section's content on wide screens. Non-blocking; the rail, rotation and offline banner were approved. Listed in the E7 UI refinement backlog (`planning/07-roadmap.md` §1.1).
- **E7 (UI refinement), from Checkpoint E2-A (user, 2026-10-14):** general onboarding polish, better use of space on "Sexo", a typography review. Every later checkpoint appends deferred UI details to `planning/07-roadmap.md` §1.1.
- **E2 (food diary), from the PR #24 review:** `MacroProgressBar` and `ProgressRing` take `Int`, but snapshots are stored to 0.1 g (04 §2), so a caller that rounds first can be off by 1 % (100.9/200 → 50 %, 101/200 → 51 %). Add `Double` overloads or have the domain pass in the percent.
- **E4 (weight and progress), from the PR #24 review:** `MetricTile` and `TrendPill` are both `surfaceOverlay`, so the pill's outline disappears inside a tile; add a container-colour parameter.
