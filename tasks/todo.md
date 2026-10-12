# Tasks — Phase 1

Plan: [tasks/plan.md](plan.md) · Planning: [planning/](../planning/README.md) · Log: `.workflow/execution.log` (local)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Epic status: E0 ⏳ · E1 ☐ · E2 ☐ · E3 ☐ · E4 ☐ · E5 ☐ · E6 ☐

---

# E0 · Project setup

## T-001: Verify local environment and re-authenticate `gh`

**Description:** Check that the tools from `planning/08-environment-setup.md` §1 are installed and that `gh` has a valid session for `lopezjavier`. The current `gh` session has an invalid token.

**Acceptance criteria:**
- [x] 👤 `gh auth login -h github.com` completed (scopes `repo`, `workflow`, `read:org`); `gh auth status` OK for `lopezjavier`. *(2026-10-12)*
- [x] PM: report with the version of each tool (JDK 21 (ADR-18), Android SDK/`adb`, Docker, Node/`npx supabase`, Deno, git, gh, Maestro) and the list of what is missing. *(2026-10-12: JDK 21.0.8 (Temurin; accepted in ADR-18), Android SDK + adb 1.0.41 installed but not on PATH, Docker 29.8.2, Node 22.17.1, Supabase CLI 2.120.0, git 2.50.1, gh 2.76.1. Missing: Deno, Maestro, `ANDROID_HOME`/`JAVA_HOME`.)*
- [ ] 👤 Missing tools installed (Docker/Supabase CLI block T-008; the rest can wait until E1).

**Verification:**
- [x] `gh auth status` and `gh repo view lopezjavier/AppFitness` respond without errors. *(2026-10-12)*
- [ ] `java -version`, `docker info`, `npx supabase --version`, `deno --version`, `maestro --version`, `adb version` run.

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
- [ ] A new Claude Code session in the repo correctly summarizes its role and where the tasks are (manual test by the user).

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
- [ ] `main` ruleset (also stored in `.github/rulesets/main.json` for reference): PR required with 0 approvals, required `gitleaks` check, branches up to date before merging, linear history, no force-push or deletion, no bypass.
- [ ] This change goes through a PR (first use of the PR → checks → squash flow).

**Verification:**
- [ ] `git push origin main` with a trivial commit is rejected.
- [ ] This task's PR shows the `gitleaks` check green and is merged with `gh pr merge --squash --delete-branch`.
- [x] `gh api repos/lopezjavier/AppFitness --jq .security_and_analysis` shows secret scanning and push protection `enabled`. *(2026-10-12)*

**Dependencies:** T-006
**Files likely touched:** `.github/workflows/security.yml`, `.github/rulesets/main.json`
**Estimated scope:** S

---

### Checkpoint B — GitHub configured
- [ ] `main` protected and verified; PR flow tested end to end.

---

## T-008: Production Supabase project and `supabase/` initialization

**Description:** Create the production Supabase Free project and add the local Supabase CLI configuration to the repo, verifying that the local environment starts.

**Acceptance criteria:**
- [x] 👤 Project created on supabase.com (name `appfitness`, EU region: `eu-west-3` or `eu-central-1`, Free plan). DB password stored in a password manager. Noted down (not shared in the chat): project ref, URL and publishable key (`sb_publishable_...`; the legacy anon key is not used). Project ID: `lbprakdmgwnozjevsfbk`. *(2026-10-12)*
- [x] 👤 `npx supabase login` in their terminal (local CLI token). *(2026-10-12)*
- [x] PM: CLI version chosen (latest stable) and pinned; `npx supabase@<v> init` → `supabase/config.toml` with `project_id = "appfitness"`; `supabase/.env.example` with the expected local variables (`OFF_USER_AGENT`, `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET`) without values. *(2026-10-12: 2.120.0, pinned as a devDependency in `package.json`)*
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
- [x] 👤 Supabase personal access token created (Account → Access Tokens, name `github-actions-appfitness`, 1-year expiry, granular permissions: Project Settings read, Migrations read-write, Connection Pooling read, Edge Functions read-write, API Keys read; everything else None — widen only if T-010 fails with 401/403). *(2026-10-12)*
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
- [ ] `gh workflow run deploy-supabase.yml` + `gh run watch` → success (link OK, "Remote database is up to date", functions skipped).
- [ ] Merging the PR (if it touches `supabase/**`, or the next PR that does) triggers the workflow automatically and it finishes green; the deployment appears under *Environments → production*.

**Dependencies:** T-009
**Files likely touched:** `.github/workflows/deploy-supabase.yml`, `planning/08-environment-setup.md`
**Estimated scope:** S

---

### Checkpoint C — Automatic Supabase deployment
- [ ] `deploy-supabase` green both manually and on merge.
- [ ] 👤 User review.

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
- [ ] Screenshot or list from the console with the 3 clients (Web + 2 Android) and the correct packages (without showing the secret).
- [x] 👤 `gh secret set GOOGLE_WEB_CLIENT_ID` (repository secret, used by `android-ci`); `gh secret list` shows it. *(2026-10-12)*

**Dependencies:** T-008 (project ref for the redirect URI)
**Files likely touched:** `planning/08-environment-setup.md` (guide adjustments if the console differs)
**Estimated scope:** S

---

## T-012: Auth providers in Supabase (prod and local)

**Description:** Enable Google and disable Email in production; locally, keep Email enabled (for "Test login" and Maestro) and Google optional through environment variables.

**Acceptance criteria:**
- [x] 👤 Production dashboard → Authentication → Providers (Google enabled with the Web client ID/secret, nonce checks on, users without email not allowed). *(2026-10-12)* — Email disabled pending confirmation by the curl check below:
  - Google enabled, with *Client ID* = the Web client ID and *Client Secret* = the Web client's secret. If the panel has an "authorized client IDs" field for native flows, include the Web client ID.
  - Email **disabled** (no email sign-up or login).
- [ ] PM: local `supabase/config.toml`: `[auth.email] enable_signup = true`; `[auth.external.google] enabled = true`, `client_id = "env(GOOGLE_WEB_CLIENT_ID)"`, `secret = "env(SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET)"`; `.env.example` updated. With empty variables the local stack still starts.
- [ ] Changes through a PR (triggers `deploy-supabase`, which must stay green: `config.toml` does not overwrite production auth).

**Verification:**
- [ ] `curl -s https://<ref>.supabase.co/auth/v1/settings -H "apikey: <publishable key>"`: `external.google = true` and `external.email = false` (run by 👤 or by the PM reading environment variables, without printing the key).
- [ ] Local: `npx supabase start` and `curl -s http://127.0.0.1:54321/auth/v1/settings -H "apikey: <local publishable key>"`: `external.email = true`.
- [ ] End-to-end sign-in is validated in US-05 (there is no app yet).

**Dependencies:** T-010, T-011
**Files likely touched:** `supabase/config.toml`, `supabase/.env.example`
**Estimated scope:** S

---

## T-013: E0 wrap-up

**Description:** Check the Definition of Done of `planning/epics/E0-setup.md`, leave local configuration templates and prepare the start of E1.

**Acceptance criteria:**
- [ ] `local.properties.example` in the root with the keys from `planning/08-environment-setup.md` §3.1 (without values).
- [ ] `planning/08-environment-setup.md` reflects what was actually configured (region, OAuth client names, CLI version); no secrets.
- [ ] `tasks/todo.md`: tasks T-001…T-013 checked and E0 ✅.
- [ ] `.workflow/execution.log`: `[PM] [E0] [COMPLETED]`.
- [ ] Changes through a PR.

**Verification:**
- [ ] E0 checklist: public and protected repo · `CLAUDE.md` and agents · Supabase deploy green · production Auth with Google on and Email off · no secret in the history (`gitleaks` green on `main`).

**Dependencies:** T-012
**Files likely touched:** `local.properties.example`, `planning/08-environment-setup.md`, `tasks/todo.md`
**Estimated scope:** S

---

### Checkpoint D — E0 closure
- [ ] Everything above verified.
- [ ] 👤 User review → the **E1** tasks are generated (from T-014).
