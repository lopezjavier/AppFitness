# Tasks — Phase 1

Plan: [tasks/plan.md](plan.md) · Planning: [planning/](../planning/README.md) · Log: `.workflow/execution.log` (local)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Epic status: E0 ✅ · E1 ✅ · E2 ✅ · E3 ✅ · E4 ✅ · E5 ☐ · E6 ☐

**Next:** T-083 (User feedback: day-only forms, aligned weight sheet, all circumferences at once); then T-079 with no checkpoint in between (E5-A folded into E5-B). T-076, T-077 and T-078 done. E4 closed (tag `v0.4.0`).

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
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green locally.
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
- [x] `tasks/todo.md` (E1 ✅, next-task pointer) updated through a PR.
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
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
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
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
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
- [x] `tasks/todo.md` (E2 ✅, next-task pointer) updated through a PR.

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
Questions in `tasks/plan.md`: Q1, Q3 and Q4 resolved (ADR-38); Q2 resolved: the first-ever session of an exercise is a "PR". Superset rest configuration (rounds, reps, rest between exercises, rest between rounds) is part of T-043 and T-046. Q5 and Q6 (T-051) are **BLOCKING**.
Corrective phase (user feedback from the single E3 test, 2026-10-20): T-050…T-054, a single 👤 checkpoint after T-054 (E3-C…G, user decision 2026-10-20), then T-049 and Checkpoint E3-B as final regression and approval. Out of scope here: per-set weight/reps/set type in routines (E6 US-29) and background rest alerts (Phase 2).

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
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; the production catalog row count matches the bundle.

**Dependencies:** E2 (`v0.2.0`), Q1, the bundle in place
**Files likely touched:** `supabase/migrations/<ts>_exercises.sql`, `supabase/tests/exercises.test.sql`, possibly `supabase/seed.sql` and a one-off generator script (Q1), `.gitignore`, `scripts/deliver-apk.py`, `shared/…/domain/model/{Exercise,MuscleGroup,Equipment,ExerciseMetricType}.kt`, `shared/…/domain/usecase/SearchExercises.kt`, `shared/…/domain/repository/ExerciseRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/exercises/{ExercisePickerScreen,ExercisePickerViewModel,ExerciseDetailScreen,CreateExerciseSheet,ExerciseLibrary}.kt`, `composeApp/…/designsystem/ExerciseAnimation.kt`, catalog, strings, test fixture, tests, `planning/08-environment-setup.md`
**Estimated scope:** L (if the plan passes ~15 non-trivial files, the planner may split it into backend + catalog data, then UI)

---

## T-043: Routine builder and "Mis rutinas"

**Story:** US-10 · **Flow:** UI + backend · **Scenarios:** Creating a routine; Reordering exercises; Grouping into a superset; Validation; Edit, duplicate and archive (the "sessions already performed do not change" half is asserted in T-045, when sessions exist)

**Description:** Backend: one migration with the `set_type` enum, `routine_folders` *(E6: created now so `routines.folder_id` has its target and E6 needs no migration; owner RLS + pgTAP, not rendered)*, `routines` and `routine_exercises` per `03-data-model.md` §5 with every *(E6)* column and its default; checks `target_sets` 1–10, `target_reps` 1–50, `rest_seconds` 0–600 default 120, `target_rpe` 1–10; at most one `is_next` per user (partial unique index); RLS on `routine_exercises` via the parent routine; a routine may only reference exercises the caller can see (catalog or own custom). Transactional save: `save_routine(jsonb)` (`security invoker`; inserts or replaces the routine and its exercises in one transaction) so a routine is never half-saved. App: `RoutineRepository` (list active routines with exercise count, get, save, duplicate, archive). Entreno tab main view becomes "Mis rutinas" (list, "Nueva rutina"; it later gains "Empezar entreno vacío" in T-045 and "Historial" in T-047; the T-042 provisional picker entry is removed). Builder: name, "Añadir ejercicio" through the T-042 picker, per exercise target sets/reps and rest (steppers or `NumberInput`, ux-reviewer), drag-and-drop reorder plus "Subir"/"Bajar" in the exercise menu (accessible alternative), "Agrupar con el siguiente" / ungroup, superset labels "Superserie A1"/"A2" (letter per group in order), "Guardar" disabled without a name or without exercises. Routine menu: edit, duplicate, archive (archived routines leave "Mis rutinas").

**Acceptance criteria:**
- [x] Migration + `supabase/tests/routines.test.sql`: RLS on and forced on `routines`, `routine_exercises` and `routine_folders`; cross-user isolation for select/insert/update/delete; A cannot attach exercises to B's routine nor reference B's custom exercise; checks reject 0 / 11 sets and 601 s rest; `save_routine` is atomic (an invalid exercise row leaves nothing saved) and cannot write into another user's routine; deleting a user with routines succeeds.
- [x] Creating "Torso fuerza" with "Press de banca con barra" and "Dominadas", 3 × 8 and 120 s each, shows it in "Mis rutinas" with "2 ejercicios".
- [x] Dragging C to the first position saves C, A, B; "Subir"/"Bajar" give the same result; the order survives a reload.
- [x] "Agrupar con el siguiente" on "Press de banca con barra" shows "Superserie A1" / "A2"; grouping is stored in `superset_group` (consecutive exercises, same value); ungrouping clears it.
- [x] "Guardar" disabled with a blank name or no exercises; save errors keep the edits with a message-only snackbar (02 §9); write actions disabled offline.
- [x] Edit, duplicate and archive reflected in "Mis rutinas"; the *(E6)* columns are written with their defaults and never rendered.
- [x] Builder state survives configuration changes and process death (`SavedStateHandle`).

**Host tests (per scenario):** `SupersetLabelsTest` (groups → "A1", "A2", "B1"…); `RoutineBuilderViewModelTest` with fakes + Turbine (Creating a routine; Reordering C, A, B by drag and by Subir/Bajar; Grouping into a superset; Validation; Edit, duplicate and archive); `RoutineBuilderScreenTest` (Robolectric: "Guardar" disabled state, superset labels, move-up/down menu); `RoutinesScreenTest` ("Mis rutinas" list, exercise count, empty state); repository test of the `save_routine` payload; pgTAP `routines.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-042
**Files likely touched:** `supabase/migrations/<ts>_routines.sql`, `supabase/tests/routines.test.sql`, `shared/…/domain/model/{Routine,RoutineExercise,SetType}.kt`, `shared/…/domain/usecase/SupersetLabels.kt`, `shared/…/domain/repository/RoutineRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/routines/{RoutinesScreen,RoutinesViewModel,RoutineBuilderScreen,RoutineBuilderViewModel,RoutineBuilderUiState}.kt`, `composeApp/…/feature/workout/WorkoutScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, possibly a reorderable-list helper in `designsystem/`, strings, tests
**Estimated scope:** L

---

## T-044: Workout sessions backend and training domain

**Story:** US-11 (backend half) + the `exercise_history` RPC of US-15 (needed now for PR detection) · **Flow:** backend (no UI, no ux-reviewer) · **Scenarios (data side):** US-11 Starting from a routine (session + pending sets); Completing a set (upsert); Continuing after closing the app (in-progress session with its sets); Finishing and summary (volume "2.120 kg", PR); Finishing with incomplete sets (pending sets dropped); Discarding the session · US-15 Estimated 1RM record (SQL side)

**Description:** One migration with the `session_status` enum, `workout_sessions` and `workout_sets` per `03-data-model.md` §5 with every *(E6)* column (`description`, `gym`, `paused_at`; `set_type` default `working`, `rpe`, `duration_s`, `distance_m`, `steps`, `floors`); at most one `in_progress` session per user (partial unique index); `unique (session_id, exercise_position, set_index)`; checks `weight_kg ≥ 0`, `reps ≥ 0`, `rpe` 1–10; indexes for the history (`user_id, started_at desc`) and per-exercise lookups; RLS on `workout_sets` via the parent session; FKs chosen so account deletion (ADR-29) still succeeds with sessions and custom exercises. The RPC `exercise_history(p_exercise_id, p_from)` (`security invoker`; per completed session: date, best estimated 1RM with Epley rounded to 0.5 kg, total volume; only completed sets) ships here because the summary (T-045) and the history (T-047) need the previous best 1RM; T-048 reuses it for the charts. Finishing is one transaction (pending sets deleted + `completed` + `ended_at`; an RPC or equivalent, planner's choice). Domain (pure, `04-business-rules.md` §4 examples as tests): `EstimateOneRepMax` (§4.1), `CalculateVolume` (§4.2), `DetectPersonalRecords` (§4.5; first-ever session per Q2). Session model with a **polymorphic set entry** (a sealed type keyed by `metric_type`; E3 maps only `weight_reps`) so E6 extends it without a rewrite (ADR-30). `WorkoutSessionRepository`: start from a routine (name snapshot, pending sets from the targets, `superset_group` copied), start empty, get the in-progress session with its sets, upsert a set, finish, discard.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/workout_sessions.test.sql`: RLS on and forced on both tables; cross-user isolation (select/insert/update/delete, including a set inserted into B's session); a second `in_progress` session for the same user fails; the upsert key replaces instead of duplicating; deleting a user with sessions, sets and custom exercises succeeds.
- [x] `exercise_history` pgTAP with the domain cases: 100 kg × 5 → 116.5; 85 kg × 8 → 107.5; 100 kg × 4 → 113.5; sets with 0 reps, > 12 reps or 0 kg excluded from the 1RM; discarded, in-progress and other users' sessions ignored; volume per session.
- [x] Domain tests use the same cases; volume of 60 × 12 + 87.5 × 8 + 87.5 × 8 = 2120 (formatted "2.120 kg" with `AppNumberFormat`); PR flag per §4.5 and Q2.
- [x] Finishing keeps only completed sets and is atomic; discarding sets `discarded` and the session leaves every history query.
- [x] Repository tests with fakes for start-from-routine (3 × 8 → 3 pending rows per exercise), resume, upsert, finish and discard.

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
- [x] "Empezar" on "Torso fuerza" (Press de banca 3 × 8, Dominadas 3 × 8) creates one in-progress session and shows 3 rows per exercise with the five column headers; "Empezar entreno vacío" starts a session with no exercises.
- [x] The window keeps the screen on only while the session screen is visible; leaving it or finishing restores the normal timeout.
- [x] Completing 60 kg × 12 in set 1: checked state, one haptic, one upsert; unchecking returns it to pending and editable.
- [x] "Añadir serie" → set 4 with set 3's values as a placeholder; "Añadir ejercicio" → "Press militar con mancuernas" at the end with one empty set.
- [x] Killing and reopening the app with 2 completed sets opens the Entreno tab on the session with both sets checked.
- [x] Summary for 60 × 12, 87.5 × 8, 87.5 × 8 shows the duration, 3 completed sets and "2.120 kg"; exercises with a new estimated 1RM record show "PR" (Q2 rule).
- [x] Finishing with 3 pending sets shows "Hay 3 series sin completar; no se guardarán" and keeps only the completed ones.
- [x] Discarding asks for confirmation; the session is `discarded` and never appears in the history (checked again in T-047).
- [x] Offline check → "pendiente de guardar" + banner; reconnecting saves it with no user action; no set is lost while the session stays open.
- [x] Editing or archiving a routine after a session leaves that session's name and sets unchanged (US-10).
- [x] Every interactive element has a snake_case `testTag` (e.g. `workout_set_2_done`); targets ≥ 48 dp.

**Host tests (per scenario):** `LiveSessionViewModelTest` with fakes, a test `Clock` and Turbine (Starting from a routine; Completing a set; Unchecking; Adding sets and exercises; Continuing after closing; Finishing and summary; Finishing with incomplete sets; Discarding; Network error → queue → flush on reconnect); `LiveSessionScreenTest` (Robolectric: headers, checkbox state, "EN VIVO", "Pantalla activa", sheet warning text, summary "2.120 kg" and "PR"); `KeepScreenOnTest` (flag on while shown, off after leaving and after finishing); `WorkoutTabRouteTest` (in-progress session → live screen); `RoutineSnapshotTest` (US-10 sessions unchanged).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] Manual (developer, emulator, local flavor): start, check sets, kill and reopen, finish; airplane mode while checking a set.

**Dependencies:** T-044
**Files likely touched:** `composeApp/…/feature/workout/{WorkoutScreen,LiveSessionScreen,LiveSessionViewModel,LiveSessionUiState,SessionSummaryScreen,FinishSessionSheet,SessionSetRow}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, `composeApp/src/androidMain/…/platform/*` (keep-screen-on helper if needed), `designsystem/{SetRow,NumberInput}.kt` (only if the polymorphic row needs a slot; check every caller first), strings, tests
**Estimated scope:** L

---

## T-046: Ghost data and rest timer in the live session

**Story:** US-12 + US-13 · **Flow:** UI + backend (`last_performance`) · **Scenarios:** US-12 Showing the previous performance; No previous performance; More sets than last time; Using the previous performance · US-13 Automatic start; Quick adjustments; Free navigation; End of rest; Accuracy with the app in the background; Superset; Rest configurable during the session

**Description:** Backend: one migration with `last_performance(p_exercise_id)` (`security invoker`; sets `set_index`, `weight_kg`, `reps` of the caller's most recent **completed** session containing the exercise, completed sets only, regardless of routine) + pgTAP. App: ghost data loaded once per exercise when the session starts or an exercise is added; ANTERIOR shows "85 kg × 8" in `ghostData` matched by `set_index` ("—" when missing); KG/REPS show the values as a dim placeholder (never as a value); tapping the ANTERIOR text fills both fields. Rest timer (`04-business-rules.md` §4.4): state from an `endsAt` instant and the injected `Clock`, never a decrementing counter; starts on LISTO with the exercise's `rest_seconds` (120 s for exercises added in the session, 0 = no timer); checking another set restarts it; "+30s" and "Omitir"; in a superset it starts only after the set of the last exercise in the group, with the rest decided in **Q3**; the existing `RestTimerBar` pinned below the Top App Bar with the content inset so it never covers set rows or controls; at 00:00 the `Haptics` port plus a short alert through a new sound port in `androidMain` (silent when the system is muted; only with the app visible, no foreground service), then the bar hides. Exercise menu "Descanso" changes the rest for the rest of this session only (no routine write; E6 adds "Update Routine"). Adds the DoD full-session host test.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/last_performance.test.sql`: returns the latest completed session's sets; ignores other users', discarded and in-progress sessions and pending sets.
- [x] Ghost: "85 kg × 8" in ANTERIOR for set 2; placeholders 85 and 8; "—" for a never-done exercise and for set 4 when last time had 3; tapping fills 85 and 8.
- [x] Timer: LISTO with 120 s → "02:00", cyan decreasing line, "+30s" and "Omitir"; "01:12" + 30 s → "01:42"; "Omitir" hides it; 00:00 → vibration + alert + hidden; 60 s left, 30 s in the background → "00:30" on return.
- [x] Superset A1/A2 (Q3): completing A1's set starts a timer only if A1's own rest is above 0 (0 = nothing); completing the matching A2 set starts the timer with A2's rest.
- [x] Changing an exercise's rest to 90 s from its menu applies to its following sets in this session; the routine is unchanged.
- [x] The bar never overlaps set rows or controls while scrolling or editing (bounds asserted in a Robolectric test).
- [x] DoD: a host test runs a full session of a 4-exercise routine with a superset (short rest durations), with correct ghost data and rest periods.

**Host tests (per scenario):** `GhostDataTest` (Showing the previous performance; No previous performance; More sets than last time; format); `RestTimerTest` with `runTest` + `advanceTimeBy` and a test `Clock` (Automatic start; Quick adjustments; End of rest effects; Accuracy with the app in the background; Rest configurable); `SupersetRestTest` (Superset); `LiveSessionViewModelTest` additions (Using the previous performance; timer restart on another set); `LiveSessionScreenTest` (Robolectric: ghost text, placeholder semantics, bar texts, Free navigation non-overlap); `FullSessionTest` (Epic DoD); pgTAP `last_performance.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-045, Q3
**Files likely touched:** `supabase/migrations/<ts>_last_performance.sql`, `supabase/tests/last_performance.test.sql`, `shared/…/domain/usecase/{GhostData,RestTimer}.kt` + tests, `shared/…/domain/port/AlertSound.kt`, `shared/…/domain/repository/WorkoutSessionRepository.kt`, `shared/…/data/repository/*`, `composeApp/src/androidMain/…/platform/AndroidAlertSound.kt`, `composeApp/…/feature/workout/{LiveSessionScreen,LiveSessionViewModel,LiveSessionUiState,ExerciseMenu}.kt`, DI, strings, tests
**Estimated scope:** L

---

### Checkpoint E3-A — A real workout on the phone 👤
- [x] Merged into one end-of-epic test by user decision; that test was held on 2026-10-20 and its findings became T-050…T-054 (re-tested at E3-C…E3-G).
- [x] T-042…T-046 merged, CI green, `deploy-supabase` applied the four migrations in production.
- [x] 👤 *(superseded by the 2026-10-20 test)* The user installs the `prodDebug` APK (`! python scripts/deliver-apk.py`, with the RepDB bundle in place) and tries:
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
- [x] List newest to oldest with date, name, duration, total volume ("2.120 kg") and number of sets; discarded and in-progress sessions never listed; empty state when there are none.
- [x] Detail shows exercises, sets and "PR" marks consistent with the T-045 summary.
- [x] Editing 87,5 kg × 8 to 87,5 kg × 7 saves it and the volume updates in the detail and the list; save errors keep the edit with a message-only snackbar; disabled offline.
- [x] Deleting asks for confirmation; the session disappears from the list and `last_performance` no longer returns it (repository/pgTAP check).
- [x] With 25 sessions, scrolling to the end loads the remaining 5 without duplicates; a page error shows a retry at the list end.

**Host tests (per scenario):** `HistoryViewModelTest` with fakes + Turbine (Session list; Pagination; Deleting a session); `SessionDetailViewModelTest` (Session detail PRs; Correcting a set → volume recalculated); `HistoryScreenTest` and `SessionDetailScreenTest` (Robolectric: row texts, "PR", edit field, delete dialog); pgTAP only if SQL is added (aggregates isolation; deleted session not returned by `last_performance`).

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` green (+ `npx supabase test db` if a migration is added).

**Dependencies:** T-046
**Files likely touched:** `composeApp/…/feature/workout/{HistoryScreen,HistoryViewModel,SessionDetailScreen,SessionDetailViewModel}.kt`, `composeApp/…/feature/workout/WorkoutScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, `shared/…/domain/repository/WorkoutSessionRepository.kt` (paged list, detail, update set, delete), `shared/…/data/repository/*`, optionally one migration + pgTAP, strings, tests
**Estimated scope:** M–L

---

## T-048: Strength progress: "Fuerza" screen and exercise charts

**Story:** US-15 · **Flow:** UI (weekly muscle volume split into T-048b by the planner) · **Scenarios:** Summary card on the strength screen; Estimated 1RM record; Exercise chart; Exercise without enough data; Exercises without load

**Description:** Backend: one migration with `weekly_muscle_volume(p_from)` (`security invoker`; per ISO week Monday–Sunday and the exercise's `muscle_group`, completed sets of completed sessions) + pgTAP; `exercise_history` exists since T-044. Add Vico (latest stable, multiplatform) and a chart component in `designsystem/` (athleticCyan, tabular numerals, catalog entry, `stateDescription` summarising the data). Perfil root gains its first statistics entry "Fuerza" (ADR-37; layout by ux-reviewer) opening a full-screen destination with a back arrow: the "Fuerza y 1RM estimado" card with chips for the 3 exercises with most sessions in the last 6 weeks (the most frequent selected by default), "Récord 1RM estimado: 113,5 kg" and "+5,0 kg este mes" in green (`TrendPill`) against the best 1RM as of 30 days ago; an entry "Volumen por grupo muscular" (last 8 weeks per group, selecting a group shows only its evolution). Per-exercise detail: best estimated 1RM per session and total volume per session, filters 1M, 3M, 6M, 1A; a single session shows its value and "Necesitas al menos 2 sesiones para ver la evolución"; an exercise logged only with 0 kg shows max reps instead of 1RM and volume. The detail is reachable from the card, from the exercise detail (T-042) and from the session detail (T-047). Domain: `StrengthSummary` (top 3, record, monthly delta) with tests from `04` §4.

**Acceptance criteria:**
- [x] With Press de banca, Sentadilla and Peso muerto trained in the last 6 weeks, the card shows those 3 chips with "Press de banca con barra" selected.
- [x] Best set 100 kg × 4 → "Récord 1RM estimado: 113,5 kg"; best 30 days ago 108,5 kg → "+5,0 kg este mes" in green.
- [x] Exercise detail: two charts (1RM, volume) and the 1M / 3M / 6M / 1A filters change the range; single-session message; Dominadas at 0 kg → max-reps chart.
- [x] Empty state on "Fuerza" for a user with no completed sessions; loading skeleton and `ErrorState` with "Reintentar" on a failed read.
- [x] Perfil root keeps sign-out and delete-account behaviour unchanged.

**Host tests (per scenario):** `StrengthSummaryTest` (Summary card; Estimated 1RM record); `ExerciseProgressViewModelTest` (Exercise chart filters; Exercise without enough data; Exercises without load); `StrengthScreenTest` and `ExerciseProgressScreenTest` (Robolectric: chips, texts "Récord 1RM estimado: 113,5 kg", "+5,0 kg este mes", filters, message); `ProfileRootScreenTest` update (Fuerza entry).

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-047
**Files likely touched:** `supabase/migrations/<ts>_weekly_muscle_volume.sql`, `supabase/tests/weekly_muscle_volume.test.sql`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `shared/…/domain/usecase/StrengthSummary.kt` + tests, `shared/…/domain/repository/StrengthRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/analytics/{StrengthScreen,StrengthViewModel,ExerciseProgressScreen,ExerciseProgressViewModel,MuscleVolumeScreen,MuscleVolumeViewModel}.kt`, `composeApp/…/designsystem/AppLineChart.kt`, `composeApp/…/feature/profile/ProfileRootScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, catalog, strings, tests
**Estimated scope:** L (if the plan passes ~15 non-trivial files, the planner may move the weekly muscle volume into its own UI follow-up task)

---

## T-048b: Weekly muscle volume

**Story:** US-15 · **Flow:** UI + backend · **Scenarios:** Weekly volume per muscle group

**Description:** Split from T-048 by the planner. Migration with `weekly_muscle_volume(p_from)` (`security invoker`; per ISO week Monday–Sunday and the exercise's `muscle_group`, completed sets of completed sessions) + pgTAP. `MuscleVolumeViewModel` and screen; the "Volumen por grupo muscular" entry from "Fuerza" (last 8 weeks per group; selecting a group shows only its evolution).

**Acceptance criteria:**
- [x] Migration + `supabase/tests/weekly_muscle_volume.test.sql`: ISO-week grouping (a Sunday and the next Monday fall in different weeks), completed only, other users ignored.
- [x] Weekly volume: 8 weeks per group; selecting a group isolates it.

**Host tests:** `MuscleVolumeViewModelTest`; screen test; pgTAP.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [x] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-048
**Estimated scope:** M

---

## T-050: App-wide state restoration (tab, screen, drafts, process death)

**Story:** cross-cutting (user feedback 2026-10-20, items 3 and 9; `planning/epics/E3-training-core.md` "App state restoration") · **Flow:** UI (kotlin-core-developer + compose-ui-developer; ux-reviewer only if a visible change is needed) · **Scenarios:** Returning to the same tab; Returning to the same screen; Keeping a draft; Restoring after process death

**Description:** Today, leaving the app and coming back can drop the user on another tab or screen and loses in-progress input (e.g. a half-built "Nueva rutina"). Make restoration a property of the whole app, not of single screens: the selected tab, each tab's navigation back stack (type-safe `@Serializable` routes) and every in-progress form survive both a plain background/foreground and a process death. The planner first finds why the current app resets (e.g. the root/auth routing recreating the `NavHost`, tab back stacks not saved, form state held only in `remember`) and fixes the cause centrally (root navigation, per-tab `saveState`/`restoreState`, `rememberSaveable`/`SavedStateHandle` for form drafts). Drafts in scope: routine builder ("Nueva rutina" and edit), create-custom-exercise form, exercise picker search and filters, history "Corregir serie" edit, Perfil edit forms and Preferencias, onboarding steps not yet saved (ADR-24 resume still applies). The live session already resumes from the server (T-045) and must keep doing so. Record the rule as **ADR-40** ("every screen and draft survives background and process death; a new screen ships with a restoration test") and add it to `planning/02-architecture.md` (UI pattern) so later epics inherit it.

**Acceptance criteria:**
- [x] Root cause identified and written in the PR body; the fix lives in the root navigation / scaffold, not as per-screen workarounds.
- [x] Leaving the app on any tab and returning (home button, app switcher, screen off) shows the same tab and the same screen, with the scroll position of lists kept where the platform allows.
- [x] After process death (developer option "No mantener actividades" or `adb shell am kill`), reopening restores the selected tab, that tab's back stack and the visible screen; a signed-out or expired session still routes to sign-in (auth wins over restoration).
- [x] A partially created "Nueva rutina" (name + 2 exercises + edited sets/rest, superset grouping) is restored exactly after background and after process death; the same holds for editing an existing routine, the create-exercise form, the picker search/filters, the "Corregir serie" edit and the Perfil edit forms.
- [x] Restored drafts are never saved silently: they stay unsaved until the user taps the screen's save action; discarding with back behaves as before.
- [x] An in-progress live session still reopens on the Entreno tab with its sets (T-045 behaviour unchanged).
- [x] ADR-40 written; `planning/02-architecture.md` states the rule; `.claude/agents/planner.md` (or the developer agents) require a restoration test for every new screen with input.

**Host tests (per scenario):** `StateRestorationTester` (Compose UI test) on the app scaffold: selected tab and nested back stack restored (Returning to the same tab; Returning to the same screen); `RoutineBuilderViewModelTest` + screen test recreating the ViewModel from a saved `SavedStateHandle` (Keeping a draft; Restoring after process death); equivalent restoration tests for the create-exercise form, picker filters, set edit and Perfil edit; `WorkoutTabRouteTest` still green (in-progress session).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] Manual (developer, emulator, local flavor): "No mantener actividades" on, build half a routine, switch apps, return. *(not run: covered by host restoration tests; checked at Checkpoint E3-C)*

**Dependencies:** T-048b
**Files likely touched:** `composeApp/…/app/{App,AppRoot,AppNavHost,AppScaffold,AppRoutes,AppViewModel,RootRoute,RootStates}.kt`, `composeApp/…/feature/routines/{RoutineBuilderViewModel,RoutineBuilderUiState}.kt`, `composeApp/…/feature/exercises/{ExercisePickerViewModel,CreateExerciseSheet}.kt`, `composeApp/…/feature/workout/{SessionEditSheet,SessionDetailViewModel}.kt`, `composeApp/…/feature/profile/*` (edit forms), tests, `planning/00-decisions.md`, `planning/02-architecture.md`, `.claude/agents/planner.md`
**Estimated scope:** L (if the plan passes ~15 non-trivial files, the planner may slice it: navigation/tab restoration first, then form drafts, one dev run each)


---

## T-051: Routines and catalog fixes

**Story:** US-09 + US-10 (user feedback 2026-10-20, routines/catalog items) · **Flow:** UI (core + UI; ux-reviewer for the rest picker and the detail entry points) · **Scenarios:** US-09 Exercise detail with animation and how-to (new entry points, per Q5); US-10 Creating a routine (custom exercise name), "Mis rutinas" order, rest picker; removal of "Empezar entreno vacío"

**Description:** Five fixes on the routine and catalog journey. (1) A custom exercise shows the name the user entered wherever it appears (routine builder, "Mis rutinas", live session, summary, history, Fuerza); today the routine shows "Ejercicio" because names are resolved only from the RepDB bundle (catalog rows have no DB name, ADR-38); custom rows must use their DB `name`. (2) Tapping an exercise's thumbnail or name opens its detail (animation, "Cómo hacerlo"; nothing happens today) in the routine builder and the live session cards, and in the picker as answered in **Q5**; custom exercises open a detail without media (placeholder). (3) "Mis rutinas" sorted by name ascending, case- and accent-insensitive (same normalisation as the catalog search). (4) Remove "Empezar entreno vacío" completely: button, strings, ViewModel/use-case path and its tests; the DB keeps `routine_id` nullable (ADR-30, E6). (5) Rest picker (builder and the session "Descanso" menu) moves in 10 s steps within 0–600 s, and values above 60 s read "1 min 10 s" (edge cases per **Q6**). New screens/forms keep T-050's restoration rule.

**Acceptance criteria:**
- [x] Creating custom "Press Hammer unilateral" and adding it to a routine shows "Press Hammer unilateral" in the builder, "Mis rutinas", the live session, the summary, the history detail and Fuerza; never "Ejercicio".
- [x] Thumbnail or name tap opens the exercise detail from the builder and the live session cards (Q5 resolved: the "Nueva rutina" picker is unchanged, a row tap still adds the exercise; elsewhere, including an opened routine, the thumbnail or name opens the detail); back returns to the same place with the draft or session intact.
- [x] "Mis rutinas" lists "Abdominales", "brazos", "Énfasis pierna", "Torso fuerza" in that order (case- and accent-insensitive, ascending); order kept after create, edit, duplicate and archive.
- [x] No "Empezar entreno vacío" anywhere (UI, strings, tests); `rg "start_empty|Empezar entreno vacío"` finds nothing in `composeApp` and `shared`.
- [x] Rest picker: steps of 10 s (0, 10, …, 600); 70 s shows "1 min 10 s"; 45 s shows "45 s"; Q6 resolved: 60 s → "1 min", whole minutes "2 min"; same format in the builder and the session "Descanso" menu.
- [x] `planning/epics/E3-training-core.md` already updated for order and removal (2026-10-20); the T-045 "Empezar entreno vacío" criterion is superseded by this task.

**Host tests (per scenario):** `ExerciseNamesTest` (custom name vs. catalog key); `RoutinesViewModelTest` (sort order incl. accents/case); `RoutinesScreenTest` (no empty-workout button); `RestDurationFormatTest` (10 s steps, "1 min 10 s", Q6 cases); `RoutineBuilderScreenTest` and `LiveSessionScreenTest` (thumbnail/name tap → detail navigation event; custom name shown).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.

**Dependencies:** T-050
**Files likely touched:** `composeApp/…/feature/workout/{ExerciseNames,LiveExerciseCard,ExerciseMenu,LiveSessionFormat}.kt`, `composeApp/…/feature/routines/{RoutinesScreen,RoutinesUiState,RoutinesViewModel,RoutineBuilderRow,RoutineBuilderScreen,RoutineSheetActions}.kt`, `composeApp/…/feature/exercises/{ExercisePickerScreen,ExerciseDetailScreen,ExerciseLabels}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, `shared/…/domain/repository/WorkoutSessionRepository.kt` (drop start-empty), strings, tests
**Estimated scope:** M


---

## T-052: Live session fixes

**Story:** US-11 + US-13 (user feedback 2026-10-20, live-session items) · **Flow:** UI (core + UI; ux-reviewer for the menu and the card) · **Scenarios:** US-11 Starting from a routine (no fixed bottom button); Finishing and summary (from the ⋮ menu); Resuming or discarding from "Mis rutinas"; Starting while a session is in progress · US-13 End of rest (~1 s sound)

**Description:** (1) "Finalizar entreno" leaves the fixed bottom bar and becomes an item of the session's ⋮ menu, placed above "Descartar entreno"; the existing confirmation sheet (with the pending-sets warning) and summary are unchanged; the freed bottom space goes to the set list. (2) The "Tienes un entreno en curso" card on "Mis rutinas" gets a play button (continuar → opens the live session) and a trash button (descartar → the existing "¿Descartar entreno?" confirmation → discarded; the card disappears). (3) Tapping "Empezar" on a routine while a session is in progress shows "Ya hay un entreno en curso" (message-only snackbar) instead of "No se pudo completar la acción"; the in-progress session is left untouched. (4) At 00:00 the rest alert plays a sound of about 1 s together with the existing vibration, through the `AlertSound` port (T-046), only with the app in the foreground, silent when the system is muted (`04-business-rules.md` §4.4); background alerts stay out of scope (Phase 2).

**Acceptance criteria:**
- [x] No fixed "Finalizar entreno" button; the ⋮ menu lists "Finalizar entreno" directly above "Descartar entreno"; finishing with 3 pending sets still warns "Hay 3 series sin completar; no se guardarán" and shows the summary.
- [x] "Tienes un entreno en curso" card: play opens the session; trash asks for confirmation, then the session is `discarded`, never in the history, and the card is gone; both buttons have Spanish `contentDescription`s, snake_case `testTag`s and ≥ 48 dp targets.
- [x] "Empezar" with a session in progress shows "Ya hay un entreno en curso"; no second session is created (the partial unique index error is mapped to this message in the repository/use case, not by string matching in the UI).
- [x] Rest end: one vibration and one ~1 s sound while the app is visible; nothing when the system is muted; no sound if the user left the app.
- [x] `planning/epics/E3-training-core.md` scenarios match (updated 2026-10-20).

**Host tests (per scenario):** `LiveSessionScreenTest` (menu order, no bottom button, finish from the menu → sheet → summary); `RoutinesScreenTest` + `RoutinesViewModelTest` (card play/trash, discard confirmation, "Ya hay un entreno en curso" on start conflict); `WorkoutSessionRepositoryTest` (unique-violation → `SessionAlreadyInProgress`); `RestTimerTest`/`LiveSessionViewModelTest` (alert port called once at 00:00, not when backgrounded, fake records the sound duration request).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] Manual (developer, emulator): the alert sound is audible and about 1 s. *(not verified by the agents; left to the E3-E checkpoint, switched to DTMF tone for fixed duration)*

**Dependencies:** T-051
**Files likely touched:** `composeApp/…/feature/workout/{LiveSessionScreen,LiveSessionTopBar,LiveSessionDialogs,LiveSessionViewModel,LiveSessionUiState,WorkoutEntry,WorkoutEntryViewModel}.kt`, `composeApp/…/feature/routines/{RoutinesScreen,RoutinesUiState,RoutinesViewModel}.kt`, `shared/…/domain/port/AlertSound.kt`, `composeApp/src/androidMain/…/platform/AndroidAlertSound.kt` (+ a ~1 s sound resource if needed), `shared/…/data/repository/*` (error mapping), strings, tests
**Estimated scope:** M


---

## T-053: Fix the "Corregir serie" dialog in the history

**Story:** US-14 (user feedback 2026-10-20) · **Flow:** UI (bug fix; compose-ui-developer, core only if the ViewModel is involved) · **Scenarios:** Correcting a set

**Description:** In the session detail, "Corregir serie" is broken: the reps field cannot be edited and the label is rendered vertically (one character per line, a width/constraint problem). Fix the dialog so KG and REPS are both editable side by side with readable labels on phone and tablet widths, keeping the behaviour of T-047 (save, volume recalculated, message-only snackbar on error, disabled offline). Check every caller of any shared component touched (`NumberInput`, `AppBottomSheet`; E2 lesson T-040).

**Acceptance criteria:**
- [x] Editing 87,5 kg × 8 to 87,5 kg × 7 works by typing in REPS; the volume updates in the detail and the list.
- [x] Labels render horizontally at 360 dp and 600+ dp widths and with the largest system font scale; fields ≥ 48 dp.
- [x] The edit survives leaving the app (T-050 rule).

**Host tests (per scenario):** `SessionDetailScreenTest` (Robolectric: type into REPS, label bounds wider than tall at 360 dp and font scale 2.0, save → volume); `SessionDetailViewModelTest` unchanged and green.

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.

**Dependencies:** T-052
**Files likely touched:** `composeApp/…/feature/workout/{SessionEditSheet,SessionDetailScreen}.kt`, possibly `designsystem/NumberInput.kt` (check callers), tests
**Estimated scope:** S


---

## T-054: Weekly muscle volume starts at the first recorded session

**Story:** US-15 (user feedback 2026-10-20) · **Flow:** UI (+ backend only if the planner moves the range into `weekly_muscle_volume`; then a new migration + pgTAP, never an edit of the merged one) · **Scenarios:** Weekly volume per muscle group (updated)

**Description:** "Volumen por grupo muscular" currently starts at a fixed date (31 August) and shows empty weeks before the user has any data. The chart must start at the ISO week (Monday) of the user's first completed session and run to the current week, capped at the last 8 weeks; no empty weeks before the first record. A user with no completed sessions sees the existing empty state.

**Acceptance criteria:**
- [x] First completed session on a Wednesday two weeks ago → the chart shows 3 weeks (that week, last week, this week); none before.
- [x] First session more than 8 weeks ago → the last 8 weeks, as before.
- [x] Weeks after the first session with no training still appear with 0 (only the leading empty weeks are removed).
- [x] Selecting a group still isolates it; empty state unchanged.

**Host tests (per scenario):** `MuscleVolumeViewModelTest` (first session 2 weeks ago → 3 weeks; > 8 weeks → 8; gap week → 0; no sessions → empty) with a test `Clock`; screen test for the axis start; pgTAP only if SQL changes.

**Verification:**
- [x] Lint + `testLocalDebugUnitTest` green (+ `npx supabase test db` and `deploy-supabase` green if a migration is added).

**Dependencies:** T-053
**Files likely touched:** `composeApp/…/feature/analytics/{MuscleVolumeViewModel,MuscleVolumeUiState,MuscleVolumeScreen,MuscleVolumeComponents}.kt`, `shared/…/domain/repository/StrengthRepository.kt`, `shared/…/data/repository/*`, optionally a migration + pgTAP, tests
**Estimated scope:** S

### Checkpoint E3-C…G — Corrective fixes (single checkpoint, user decision 2026-10-20) 👤
- [x] T-050…T-054 merged, CI green.
- [x] 👤 *(approved by the user, 2026-10-20)* Install the APK (`! python scripts/deliver-apk.py`) and re-test:
  - State restoration (T-050): on each tab open a sub-screen, leave the app and come back (same tab and screen); half-fill "Nueva rutina", "Crear ejercicio" and a Perfil edit form, leave and come back (input intact); close an in-progress session from the app switcher and reopen (Entreno opens it with its sets).
  - Routines and catalog (T-051): a custom exercise shows its own name everywhere; thumbnail or name tap opens the detail in an opened routine (the "Nueva rutina" picker still adds on a row tap); "Mis rutinas" sorted A→Z; no "Empezar entreno vacío"; rest in 10 s steps ("1 min 10 s").
  - Live session (T-052): "Finalizar entreno" in ⋮ above "Descartar entreno"; the "Tienes un entreno en curso" card has play and trash; "Empezar" during a session shows "Ya hay un entreno en curso"; rest at 00:00 gives vibration plus a ~1 s sound.
  - History (T-053): "Corregir serie" edits KG and REPS with horizontal labels; volume updates in the detail and the list.
  - Strength (T-054): Perfil → "Fuerza" → "Volumen por grupo muscular" starts at the week of your first session (no empty weeks); pick a group.
  - Quick regression: search and filter, reorder, superset, save, edit, duplicate, archive; LISTO, ANTERIOR, rest bar, "Añadir serie"/"Añadir ejercicio"; history order, "PR" marks, delete a session; "Récord 1RM estimado", monthly change, 1RM and volume charts with 1M/3M/6M/1A.
  - Leave the app and come back: the same tab, screen and draft are restored.

---

## T-049: E3 closure

**Story:** E3 Definition of Done · **Flow:** PM

**Description:** Closes E3 after the corrective tasks: final regression on the phone, approval, tag `v0.3.0` and the retrospective.

**Acceptance criteria:**
- [x] T-042…T-048b merged with green CI; pgTAP for `exercises`, `routines`, `routine_exercises`, `routine_folders`, `workout_sessions`, `workout_sets`, `last_performance`, `exercise_history` and `weekly_muscle_volume` green; `deploy-supabase` green after the last migration.
- [x] Host tests green for every E3 UI scenario, including the full-session test (T-046).
- [x] Forward compatibility checked (ADR-30): every *(E6)* column and enum of `03-data-model.md` §5 exists in production.
- [x] T-050…T-054 merged with green CI; Checkpoints E3-C…E3-G approved (2026-10-20); `deploy-supabase` green if any of them added a migration.
- [x] Every bug the user reported at the checkpoints fixed; UI-only feedback appended to the E8 backlog.
- [x] `tasks/todo.md` (E3 ✅, next-task pointer) updated through a PR.

**Verification:**
- [x] `gh run list` shows the last `deploy-supabase` green; `git tag` lists `v0.3.0` after approval.

**Dependencies:** T-054 (and Checkpoints E3-C…E3-G)
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

### Checkpoint E3-B — E3 closure: final regression and approval 👤
- [x] T-050…T-054 and T-049 merged, CI green, every reported bug fixed.
- [x] 👤 *(merged into Checkpoint E3-C…G; the user's approval of 2026-10-20 counts as the final approval)* Install the APK (`! python scripts/deliver-apk.py`) and run one real workout end to end as a final regression:
  - Build a routine with a custom exercise and a superset, start it, log sets with LISTO and ANTERIOR, let a rest end (vibration + sound), finish from ⋮ and read the summary.
  - Open it in "Historial", correct a set; check Perfil → "Fuerza" and "Volumen por grupo muscular" with the new data.
  - Leave the app and come back: the same tab, screen and draft are restored.
  - Approval → `git tag -a v0.3.0 -m "Epic E3"` + `git push --tags` + retrospective (includes the Claude Code workflows discussion from `retrospectives/E2.md`).

---

# E4 · Food diary

Stories: `planning/epics/E4-food-diary.md` (US-16…US-24). Plan: [tasks/plan.md](plan.md). One task per story as a vertical slice, with these merges and splits: the catalog backend (US-16) also ships the US-22 data model and the `meal_slots` table, because `food_log_entries` needs both (T-055, backend only); the day view (US-19 day structure) and the dashboard (US-20) form one task (T-056), so text search and logging (US-17 + US-22 logging) can be its own task (T-057); US-18 takes the US-22 "Creating a named portion" scenario; the rest of US-19 (edit, delete, move, copy, repeat, view modes) is T-060.
Flow: UI tasks follow the full flow (planner → ux-reviewer → developers → PR → review → merge); T-055 is backend only (no ux-reviewer). The app is verified by hand at the 👤 checkpoints with `python scripts/deliver-apk.py`. Every new user-owned row cascades from `auth.users` (ADR-29): each migration's pgTAP deletes a user with data. Every screen with input ships a restoration test (ADR-40).
Questions in `tasks/plan.md`: Q1 (T-059), Q2 and Q3 (T-060) resolved by the user (2026-10-20); no open blocking question.
Checkpoints: exactly three (user decision 2026-10-20, overrides the one-per-functionality default for E4): E4-A after T-057 (diary, dashboard, search and logging) · E4-B after T-060 (scanner and manual foods, custom meals, edit/delete/copy/views) · E4-C after T-063 (templates, history, final regression and approval → `v0.4.0`). T-058…T-060 and T-061…T-063 run back to back without a checkpoint in between.

## T-055: Food catalog backend: schema and the Open Food Facts proxy

**Story:** US-16 + US-22 (data model and "Portion from OFF") + US-23 (table and "Default meals", data side) · **Flow:** backend (no UI, no ux-reviewer) · **Scenarios:** US-16 Search with cached results; Search that queries Open Food Facts; Barcode lookup; Base unit per food; Unknown code or missing nutrition data; Invalid input; Unauthenticated call; Personal food isolation · US-22 Portion from OFF · US-23 Default meals (seeding)

**Description:** One migration per `03-data-model.md` §1/§3: the `meal_type`, `food_source` and `food_base_unit` enums; `foods` (`search_vector` generated with `unaccent` + `spanish`, GIN index; `barcode` unique when `source = 'off'`; `created_by` references `auth.users` **on delete cascade**; nutrient checks ≥ 0; consistency check: `user` ⇔ `created_by` set), `food_portions` (amount > 0, `position`), `meal_slots` (`unique (user_id, position)`, `code` null for custom meals) and `food_log_entries` (`meal_slot_id`, `food_id`, amount > 0 and ≤ 5000, `portion_label` and nutrition snapshots, index `(user_id, log_date)`), with `created_at`/`updated_at` and the `set_updated_at()` trigger. RLS enabled and forced with `(select auth.uid())`: `foods` per §3 (select `off` or own; client writes only own `user` rows; `off` rows written only by the Edge Functions with the secret key, ADR-17, reusing the `delete-account` key lookup), `food_portions` via the parent food, owner policy on `meal_slots` (no client delete) and `food_log_entries`. Default meals: Desayuno/Comida/Merienda/Cena (codes breakfast/lunch/snack/dinner, positions in that order) seeded when `profiles.onboarding_completed` becomes true (trigger, idempotent) **and backfilled for users already onboarded** (every E2/E3 user). Edge Functions per `03` §7: `functions/_shared/off-client.ts` (User-Agent from `OFF_USER_AGENT`, 8 s timeout, normalization: `nutrition_data_per` → base unit, kcal from `energy-kcal_100g`/`_100ml` or kJ / 4.184, default serving from `serving_size`, OFF servings stored as `food_portions` ("1 vaso (200 ml)")), input validation and `{ "error": { "code", "message" } }` responses; `food-search` (`q` 2–60 chars, local full-text first, OFF only below 10 local results, upsert by `barcode`, at most 20 results with `base_unit`, per-100 nutrition, default serving and portions; the caller's own `user` foods included, other users' never); `food-barcode` (EAN-8/EAN-13/UPC-A/UPC-E; local lookup including the caller's own foods, OFF when missing or `fetched_at` > 30 days; `404 FOOD_NOT_FOUND` when OFF has no product or no kcal). `verify_jwt = true` for both. No Kotlin in this task.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/foods.test.sql`, `food_portions.test.sql`, `meal_slots.test.sql`, `food_log_entries.test.sql`: RLS on and forced on the four tables; A's personal food "Tortilla de mi madre" and its portions invisible to B; B cannot write `off` rows, A's foods or portions; cross-user select/insert/update/delete on `meal_slots` and `food_log_entries`; an entry pointing at B's meal slot is rejected; checks reject amount 0 and 5001 and negative nutrients; no client delete on `meal_slots`.
- [x] Seeding: completing onboarding creates exactly the four default slots in order (twice → still four); the migration backfills already-onboarded users; a user who has not finished onboarding has none.
- [x] Deleting a user with personal foods, portions, meal slots and entries succeeds (ADR-29; if `on delete restrict` on `meal_slot_id`/`food_id` blocks the cascade, the planner switches it to `no action` and updates `03-data-model.md` §3).
- [x] `deno test` with recorded OFF fixtures (no network in CI): "Yogur griego natural" cached + 10 local results → no OFF call; "avena" with < 10 local results → OFF queried with the User-Agent, valid products stored with `source = 'off'`, ≤ 20 normalized results; "Coca-Cola Zero" per 100 ml → `base_unit = 'ml'`; kJ-only product → kcal = kJ / 4.184; serving "1 vaso (200 ml)" → portion; barcode `8410000810004` → normalized food from cache or OFF; unknown code and no-kcal product → 404 `FOOD_NOT_FOUND`; 1-char `q` and `code=abc` → 400 `INVALID_INPUT`; no JWT → 401; OFF timeout → a JSON 5xx error, never a hang.
- [x] `food-search` for "tortilla" by B never returns A's personal food (Personal food isolation), asserted in pgTAP (search query) and in the function test.

**Host tests (per scenario):** `deno test` `off-client.test.ts` (Base unit per food; kJ → kcal; Portion from OFF), `food-search/handler.test.ts` (Search with cached results; Search that queries OFF; Invalid input; Unauthenticated call; Personal food isolation), `food-barcode/handler.test.ts` (Barcode lookup; Unknown code or missing nutrition data; Invalid input); pgTAP files above (isolation, seeding, account deletion).

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` and `deno test --allow-all supabase/functions` green.
- [x] Manual (developer, local stack): `npx supabase functions serve` + `curl` with the test user's JWT: `food-search?q=avena` returns results and a second call hits only the cache.
- [ ] After the merge: `deploy-supabase` green (migration remote, both functions deployed); `npx supabase secrets list` still shows `OFF_USER_AGENT`; production users already onboarded have their four meal slots.

**Dependencies:** E3 (`v0.3.0`)
**Files likely touched:** `supabase/migrations/<ts>_food_diary.sql`, `supabase/tests/{foods,food_portions,meal_slots,food_log_entries}.test.sql`, `supabase/functions/_shared/{off-client,validation,errors,secret-key}.ts` + tests, `supabase/functions/_shared/fixtures/off/*.json`, `supabase/functions/food-search/{index,handler,handler.test}.ts`, `supabase/functions/food-barcode/{index,handler,handler.test}.ts`, `supabase/config.toml`, `deno.json`, `planning/03-data-model.md` (only if a FK rule changes)
**Estimated scope:** L (backend only; if the plan passes ~15 non-trivial files, the planner may run it as two slices: schema + pgTAP, then the Edge Functions)

---

## T-056: Daily diary by meal and the calorie and macro dashboard

**Story:** US-19 (day structure) + US-20 + US-23 "Default meals" (UI side) · **Flow:** UI · **Scenarios:** US-19 Day structure; Empty section; Changing the day · US-20 Day progress; Calorie excess; User without goals (Immediate update is completed in T-057 for adding and T-060 for edit/delete) · US-23 Default meals

**Description:** Replaces the Diario WIP placeholder (reference `design/stitch/diary/screen.png`, without the water card). Domain (`shared`, pure, `04-business-rules.md` §2 examples as tests): `MealSlot` and `FoodLogEntry` models, a `DiaryDay` aggregate with meal and day totals (sums of snapshots), `remaining = target_kcal − consumed` ("+N kcal en exceso" when negative) and macro progress `round(consumed / target × 100)` (bar capped at 100 %, text shows the real value). Gram targets come from the existing `CalculateMacroTargets` use case (T-037), never recomputed in the Diary (consumes the E2 note). `MealSlotRepository` (enabled slots in `position` order) and `FoodDiaryRepository` (entries of one `log_date`, as a `Flow`/`StateFlow` the later write tasks update in place). `DiaryViewModel` exposes one state from which list and totals derive (no second query). Screen: date header "Hoy"/"Ayer"/"lun, 24 oct" (`04` §5) with arrows (right arrow disabled on today; the local date from the injected `Clock`); dashboard with `ProgressRing` ("1.840", "de 2.400 kcal", "560 restantes"; over target: full ring in `energyAmber` and "+150 kcal en exceso") and three `MacroProgressBar`s ("142 g / 180 g · 79 %"); `ProgressRing` and `MacroProgressBar` gain `Double` (or percent) inputs so 0.1 g snapshots are not rounded early (consumes the PR #24 note; check every caller); one section per enabled slot with its entries (name, amount + unit or portion label, kcal), its kcal and P/C/F grams; empty section "Te quedan 560 kcal" (the day's remaining) and the "Añadir a …" button with the meal's name (wording per ux-reviewer; it emits a navigation event that T-057 connects to the search); collapsible sections (local saved state); no goals → "Configura tu objetivo" linking to Perfil → Preferencias. Tablet: dashboard left column, meals right. The visible day survives leaving the app (ADR-40). Loading skeleton, `ErrorState` with "Reintentar", offline banner.

**Acceptance criteria:**
- [x] A new user sees Desayuno, Comida, Merienda and Cena in that order, each with its "Añadir a …" button; the date reads "Hoy".
- [x] With target 2.400 kcal (180 / 270 / 67 g) and 1.840 kcal logged (142 / 210 / 48 g): ring "1.840", "de 2.400 kcal", "560 restantes"; "142 g / 180 g · 79 %", "210 g / 270 g · 78 %", "48 g / 67 g · 72 %"; each section shows its own kcal and P/C/F.
- [x] 2.550 kcal logged → full ring in `energyAmber` and "+150 kcal en exceso".
- [x] Empty Cena with 1.840 kcal logged shows "Te quedan 560 kcal" and its add button.
- [x] Left arrow → "Ayer" with yesterday's entries; the right arrow is disabled on today.
- [x] No `nutrition_goals` → "Configura tu objetivo" opens Preferencias.
- [x] Tablet (≥ 600 dp): dashboard on the left, meals on the right; phone single column.
- [x] Leaving the app on "Ayer" with a section collapsed and coming back (also after process death) shows the same day and state.

**Host tests (per scenario):** `DiaryTotalsTest` (sums, remaining, excess, macro percent with 0.1 g snapshots; `04` §2 example); `DiaryViewModelTest` with fakes, a test `Clock` and Turbine (Day structure; Empty section; Changing the day; User without goals; Default meals order); `DiaryScreenTest` (Robolectric: ring and bar texts, excess state, "Te quedan 560 kcal", disabled right arrow, two-column layout at 840 dp); `ProgressRingTest`/`MacroProgressBarTest` updates (Double input, 100.9/200 → 50 %); `StateRestorationTester` + recreated-ViewModel test (visible day).

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.

**Dependencies:** T-055
**Files likely touched:** `shared/…/domain/model/{MealSlot,FoodLogEntry,DiaryDay}.kt`, `shared/…/domain/usecase/DiaryTotals.kt` + tests, `shared/…/domain/repository/{MealSlotRepository,FoodDiaryRepository}.kt`, `shared/…/data/repository/*`, `shared/…/data/remote/*Dto.kt`, DI, `composeApp/…/feature/diary/{DiaryScreen,DiaryViewModel,DiaryUiState,DiaryDashboard,MealSection,DiaryDateHeader}.kt`, `composeApp/…/designsystem/{ProgressRing,MacroProgressBar}.kt` (+ callers), `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, catalog, strings, tests
**Estimated scope:** M–L

---

## T-057: Food text search and logging into a meal

**Story:** US-17 + US-22 (logging) + US-20 "Immediate update" (adding) · **Flow:** UI + backend (`favorite_foods`, `most_eaten_foods`) · **Scenarios:** US-17 Debounced search; Adding a food with a serving; Choosing a named portion; Preselected meal; No results (message; the "Crear alimento" button is wired in T-058); Recent foods; Most eaten foods; Marking a favourite · US-22 Logging a drink by millilitres; Logging with a named portion; Logging a custom amount; Scaling per-100 nutrition · US-20 Immediate update (add)

**Description:** Backend: one migration with `favorite_foods` (PK `(user_id, food_id)`, food on delete cascade, owner RLS) and `most_eaten_foods(p_limit)` (`security invoker`, derived counts from `food_log_entries`, ADR-34) + pgTAP. Domain (pure): `ResolvePortion` (raw amount or portion × count → base units; "2 huevos" with "1 huevo" = 55 g → 110 g), `ScaleNutrition` (`kcal_100 × amount / 100`, rounded to 0.1), quantity validation (> 0 and ≤ 5000, 1 decimal), `DefaultMealSlot` (US-17 time rule on the default codes: before 11:00 breakfast, 11–16 lunch, 16–20 snack, later dinner, among enabled slots; see the plan's assumptions). `FoodRepository` (`food-search` through `functions-kt`, recents from the last 14 days, most eaten, favourites toggle/list) and `FoodDiaryRepository.addEntry` (snapshot computed in the domain, stored in the entry). Screens: food search reached from each section's "Añadir a …" (meal preselected) and from a Diary-level add action (ux-reviewer; default meal by time): search box with a 400 ms debounce, skeletons while loading, rows with name, brand and kcal per 100 g or 100 ml, `FavouriteToggle` star (new design-system component); tabs with no text typed: recents, "Más comidos", "Favoritos" ("Plantillas" arrives in T-061); "No hemos encontrado alimentos". Food sheet (`AppBottomSheet`): nutrition card per 100 base units, `PortionSelector` (new design-system component: amount field in the food's base unit + portion dropdown, prefilled with the default serving or 100), meal selector of enabled slots, "Añadir" → entry logged, haptic, back to the Diary with totals updated in place. Search query, tab and sheet draft survive leaving the app (ADR-40); writes and search disabled offline.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/favorite_foods.test.sql`: RLS on and forced; cross-user isolation; `most_eaten_foods` counts only the caller's entries and orders by count; deleting a user with favourites succeeds.
- [x] Typing "pechuga pollo" fires exactly one search 400 ms after the last keystroke, with skeletons meanwhile; rows show name, brand and "110 kcal / 100 g".
- [x] "Pechuga de pollo" (110 kcal/100 g) → sheet prefilled with the default serving or 100 g; 220 g in "Comida" → a 242 kcal entry in today's Comida, one haptic, back on the Diary with the ring and bars updated without a reload.
- [x] "1 huevo" = 55 g in Desayuno → 55 g and its kcal snapshot; "2 huevos" → 110 g; "Zumo de naranja" (45 kcal/100 ml) 250 ml → 250 stored and 113 kcal, the field labelled in ml; "Arroz cocido" 180 g → 234 kcal.
- [x] From "Añadir a la cena" the entry lands in Cena without choosing a meal; quantity 0 or 5001 disables "Añadir" with the field error.
- [x] "xyzxyz" → "No hemos encontrado alimentos".
- [x] With foods logged in the last 14 days, opening the search without typing lists the most recent ones; "Más comidos" lists the most logged first (from the RPC); the star adds and removes a food from "Favoritos".
- [x] DoD: a food found by search is logged in ≤ 3 taps after opening the search (result → "Añadir", meal preselected).
- [x] Draft restoration (query, tab, open sheet with its quantity) after background and process death.

**Host tests (per scenario):** `ResolvePortionTest` and `ScaleNutritionTest` (US-22 Logging a drink by millilitres; Logging with a named portion; Logging a custom amount; Scaling per-100 nutrition); `DefaultMealSlotTest`; `FoodSearchViewModelTest` with fakes, a test dispatcher and Turbine (Debounced search; No results; Recent foods; Most eaten foods; Marking a favourite); `AddFoodViewModelTest` (Adding a food with a serving; Choosing a named portion; Preselected meal; validation); `DiaryViewModelTest` addition (Immediate update after add); `FoodSearchScreenTest` and `AddFoodSheetTest` (Robolectric: skeletons, row texts, star state, ml label, "Añadir" disabled); restoration tests; pgTAP `favorite_foods.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-056
**Files likely touched:** `supabase/migrations/<ts>_favorite_foods.sql`, `supabase/tests/favorite_foods.test.sql`, `shared/…/domain/model/{Food,FoodPortion,NutritionPer100}.kt`, `shared/…/domain/usecase/{ResolvePortion,ScaleNutrition,DefaultMealSlot,ValidateQuantity}.kt` + tests, `shared/…/domain/repository/FoodRepository.kt`, `shared/…/data/repository/*`, `shared/…/data/remote/*Dto.kt`, DI, `composeApp/…/feature/food/{FoodSearchScreen,FoodSearchViewModel,FoodSearchUiState,AddFoodSheet,AddFoodViewModel}.kt`, `composeApp/…/feature/diary/{DiaryScreen,DiaryViewModel}.kt`, `composeApp/…/designsystem/{PortionSelector,FavouriteToggle}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, catalog, strings, tests
**Estimated scope:** L (the planner may run it as two dev slices: domain + data, then screens)

---

### Checkpoint E4-A — Log food by search and see the day 👤
- [x] T-055…T-057 merged, CI green, `deploy-supabase` applied both migrations and deployed `food-search` and `food-barcode`.
- [x] 👤 Install the APK (`! python scripts/deliver-apk.py`) and try:
  - Diario: today shows Desayuno, Comida, Merienda and Cena, the ring and the three macro bars; go to "Ayer" and back; the right arrow stops at today.
  - "Añadir a la comida", search real foods ("pechuga pollo", "avena", "yogur griego"): results appear after you stop typing; add one with grams, one with a named portion ("1 huevo"), a drink in ml; check the ring, bars and section totals update at once.
  - Go past your target and read "+N kcal en exceso"; an empty meal shows "Te quedan N kcal".
  - Open the search without typing (recents), "Más comidos" and "Favoritos"; star and unstar a food; search nonsense ("xyzxyz").
  - Leave the app and come back (also from the app switcher): the same tab, screen, day and half-typed search or quantity are restored.
  - Out of scope here: scanner and "Crear alimento", meal settings, editing, deleting and copying entries (all E4-B); templates and history (E4-C).

---

## T-058: Barcode scanning and manual food creation

**Story:** US-18 + US-22 "Creating a named portion" + US-17 "No results" ("Crear alimento" option) · **Flow:** UI · **Scenarios:** US-18 Product found; Drink scanned; Product not found; Scan cancelled; Manual food creation; Adding a portion to a manual food; Manual creation validation · US-22 Creating a named portion · US-17 No results (button)

**Description:** `BarcodeScanner` port (domain) with the ML Kit `GmsBarcodeScanning` implementation in `androidMain` (EAN-13, EAN-8, UPC-A, UPC-E; Google's own full-screen UI, no camera permission, ADR-07) and a fake for host tests; result: code, cancelled or error. A scanner icon in the food search; a found code (`food-barcode`) opens the T-057 food sheet with an editable quantity (ml for drinks); 404 → "Producto no encontrado" with "Crear alimento", which opens the creation form with the barcode filled in; cancelling returns to the search with no message. "Crear alimento" is also offered on the search's "No hemos encontrado alimentos" state (the search text prefilled as the name). Creation form: name, optional brand, base unit g or ml, kcal / protein / carbs / fat per 100 base units, optional named portions (label + amount in the base unit); "Guardar" disabled with a field error for an empty name or negative values; saving creates a personal food (`source = 'user'`, `created_by` = caller, barcode when it came from a scan) with its portions atomically (an RPC + pgTAP if the planner needs one transaction; then a new migration), then opens its sheet to add it to a meal. Rescanning the same code finds the user's own food (T-055 lookup). Form draft restored after background and process death (ADR-40); disabled offline.

**Acceptance criteria:**
- [x] Scanner icon → the Google scanner; a known code opens the food sheet; "Añadir" logs it in the selected meal.
- [x] A drink per 100 ml shows the quantity in ml.
- [x] Unknown code → "Producto no encontrado" + "Crear alimento" → form with the barcode filled in; after saving, scanning it again opens that food.
- [x] Closing the scanner without a code returns to the search with no error.
- [x] "Tortilla de patatas casera", 190 kcal, 7 g P, 15 g C, 11 g F per 100 g → personal food, source "user", base unit "g", and its sheet opens; with the portion "1 ración" = 250 g, that portion is offered whenever the food is logged.
- [x] Empty name or negative kcal → "Guardar" disabled and the field shows the error.
- [x] Another user never sees the created food (covered by T-055 pgTAP; repository test that the insert carries `source = 'user'`).

**Host tests (per scenario):** `ScanFoodViewModelTest` with a fake `BarcodeScanner` (Product found; Drink scanned; Product not found; Scan cancelled); `CreateFoodViewModelTest` (Manual food creation; Adding a portion; Creating a named portion; Manual creation validation; barcode prefill; search-text prefill); `CreateFoodScreenTest` and `FoodSearchScreenTest` additions (Robolectric: "Producto no encontrado", disabled "Guardar" with errors, "Crear alimento" on no results); restoration tests; pgTAP only if an RPC is added.

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green (+ `npx supabase test db` and `deploy-supabase` if a migration is added).
- [x] `assembleProdDebug` includes the code-scanner dependency (pinned, stable) and no camera permission in the merged manifest.

**Dependencies:** Checkpoint E4-A
**Files likely touched:** `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `shared/…/domain/port/BarcodeScanner.kt`, `composeApp/src/androidMain/…/platform/MlKitBarcodeScanner.kt`, `shared/…/domain/repository/FoodRepository.kt` (barcode lookup, create food), `shared/…/data/repository/*`, DI, `composeApp/…/feature/food/{FoodSearchScreen,FoodSearchViewModel,ScanFoodViewModel,CreateFoodScreen,CreateFoodViewModel,CreateFoodUiState}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, optionally a migration + pgTAP, strings, tests
**Estimated scope:** M–L

---

## T-059: Custom meal structure

**Story:** US-23 · **Flow:** UI + backend (if the planner needs an atomic save) · **Scenarios:** Renaming a meal; Adding a custom meal; Disabling a meal; Reordering meals

**Description:** A "Comidas" settings screen reached from the Diary (entry point and layout by ux-reviewer, e.g. the Diary's menu): the user's slots in order with rename, an enable switch, "Añadir comida" at a chosen position ("after Desayuno"), and reorder by drag plus "Subir"/"Bajar" (reusing `ReorderableColumn`); one "Guardar". Because of `unique (user_id, position)`, inserting and reordering are saved in one transaction (a `security invoker` RPC such as `save_meal_slots(jsonb)` + pgTAP in a new migration, or a deferrable constraint; planner's choice). Rules: labels trimmed, not blank, length-capped (planner), custom meals have `code` null; defaults keep their code (time suggestions still work); disabling is only allowed when the slot has no entries **on the day being viewed** (Q1, user 2026-10-20: entries on past days do not block it and those past days are not edited), otherwise the switch is disabled with an explanatory hint; a disabled slot keeps its label and can be re-enabled; disabled slots are hidden from the Diary and from every "add to" target (food sheet, and later copy and templates). Renaming never changes entry snapshots or totals. Draft restored after background and process death (ADR-40); disabled offline.

**Acceptance criteria:**
- [x] Renaming "Merienda" to "Post-entreno" shows "Post-entreno" in the Diary and the food sheet; its entries keep their totals.
- [x] "Segundo desayuno" added after "Desayuno" appears in that position and can receive foods.
- [x] Empty "Merienda" disabled → hidden from the Diary and the add targets; re-enabled later with the same name; a slot with entries on the viewed day cannot be disabled and shows the hint; a slot with entries only on past days can be disabled.
- [x] Q1 recorded: the US-23 technical note in `planning/epics/E4-food-diary.md` and ADR-33's "disabling requires an empty slot" read "empty on the day being viewed" (doc consistency, no new ADR).
- [x] Moving "Cena" above "Merienda" (drag or "Subir") shows the new order in the Diary; the order survives a reload.
- [x] If an RPC is added: atomic (an invalid row saves nothing), cannot touch another user's slots (pgTAP).

**Host tests (per scenario):** `MealSlotsEditorViewModelTest` with fakes + Turbine (Renaming; Adding a custom meal; Disabling, incl. viewed-day entries block and past-day entries do not, and re-enabling; Reordering by drag and by Subir/Bajar; validation); `MealSlotsScreenTest` (Robolectric: disabled switch with hint, order, "Guardar"); `DiaryViewModelTest` addition (renamed, disabled and reordered slots reflected); restoration tests; pgTAP for the RPC if added.

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green (+ `npx supabase db reset && npx supabase test db` and `deploy-supabase` if a migration is added).

**Dependencies:** T-058
**Files likely touched:** optionally `supabase/migrations/<ts>_save_meal_slots.sql` + `supabase/tests/save_meal_slots.test.sql`, `shared/…/domain/usecase/EditMealSlots.kt` + tests, `shared/…/domain/repository/MealSlotRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/diary/{MealSlotsScreen,MealSlotsViewModel,MealSlotsUiState,DiaryScreen}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, strings, tests
**Estimated scope:** M

---

## T-060: Diary corrections, copying and view modes

**Story:** US-19 (remaining scenarios) + US-20 "Immediate update" (edit, delete) · **Flow:** UI · **Scenarios:** US-19 Switching the view; Summary view; Editing an entry's quantity; Deleting an entry; Moving an entry to another meal; Copying foods to another meal or day; Copying a whole meal; Repeating yesterday's meal · US-20 Immediate update (edit, delete)

**Description:** Tapping an entry opens the edit sheet (reusing `PortionSelector` and the meal selector): change the amount or portion and the meal, "Guardar" recalculates the snapshot from the food's per-100 values (`ScaleNutrition`) and moves it between sections. Swipe left → "Eliminar" → the entry disappears and a snackbar with "Deshacer" re-inserts it. Copy: select entries (selection mode, ux-reviewer) → "Copiar alimentos" → destination meal and day (Q3, user 2026-10-20: any past day, today or a future day; the Diary itself stays capped at today, so future copies show up when that day arrives) → copies logged in one batch insert; "Copiar comida" on a section copies all its entries the same way; "Repetir comida de ayer" on an empty meal adds yesterday's entries of that slot with a "Deshacer" confirmation (undo deletes the inserted rows). Copies keep amounts, portion labels and snapshots. View menu with `ViewModeSelector` (new design-system component on `SegmentedSelector`): "Detalle", "Lista", "Resumen" (Q2, user 2026-10-20: Detalle = each entry with its amount, kcal and P/C/F grams; Lista = one compact line per entry with name, amount and kcal; Resumen = each meal's name, kcal and macros only, entries hidden); the mode is a DataStore preference kept across launches. Ring, bars and section totals update in place after every write. Edit-sheet draft and selection restored after background and process death (ADR-40); writes disabled offline; failed writes keep the user's input with a message-only snackbar (02 §9).

**Acceptance criteria:**
- [x] "Pechuga de pollo 220 g · 242 kcal" edited to 150 g → "150 g · 165 kcal"; meal and day totals recalculated without a reload.
- [x] Swipe + "Eliminar" removes the entry; "Deshacer" restores it with the same values.
- [x] Changing the meal from "Merienda" to "Cena" in the edit sheet moves the entry.
- [x] Copying "Pechuga de pollo" from Comida to Cena or to another day logs a copy there in one request; copying a three-food Comida to tomorrow's Comida adds the three foods with the same quantities (visible when tomorrow is today); the Diary's right arrow still stops at today.
- [x] "Repetir comida de ayer" on an empty meal adds yesterday's foods of that meal; "Deshacer" removes them; the button is not offered when yesterday's meal was empty (ux-reviewer: hidden or disabled).
- [x] Q2/Q3 recorded in the US-19 scenarios or technical notes of `planning/epics/E4-food-diary.md` (no new ADR).
- [x] The view menu offers "Detalle" (entries with amount, kcal and P/C/F), "Lista" (one line per entry: name, amount, kcal) and "Resumen"; "Resumen" hides entries and shows each meal's name, kcal and macros; the chosen view is kept after closing and reopening the app.

**Host tests (per scenario):** `DiaryEditViewModelTest` with fakes + Turbine (Editing an entry's quantity; Moving an entry; Immediate update); `DiaryViewModelTest` additions (Deleting + undo; Copying foods; Copying a whole meal; Repeating yesterday's meal + undo; Switching the view persisted through a fake preferences store); `FoodDiaryRepositoryTest` (batch insert is one request; undo payloads); `DiaryScreenTest` additions (Robolectric: swipe → "Eliminar", snackbar "Deshacer", Summary view hides entries, view menu); restoration tests.

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.

**Dependencies:** T-059
**Files likely touched:** `shared/…/domain/usecase/{CopyEntries,RepeatYesterdayMeal}.kt` + tests, `shared/…/domain/repository/{FoodDiaryRepository,DiaryPreferences}.kt`, `shared/…/data/repository/*`, `shared/…/data/local/*` (DataStore), DI, `composeApp/…/feature/diary/{DiaryScreen,DiaryViewModel,DiaryUiState,MealSection,EditEntrySheet,EditEntryViewModel,CopyEntriesSheet}.kt`, `composeApp/…/designsystem/ViewModeSelector.kt`, catalog, strings, tests
**Estimated scope:** L (the planner may run it as two dev slices: edit/delete/move + view modes, then copy/repeat)

---

## T-066: E4-B feedback — remove what has no value (fix)

**Origin:** Checkpoint E4-B feedback, user 2026-10-20 (items 1, 5, 6, 8). **Flow:** UI + core (removals) · One task per session: plan in a fresh session; show the user the proposal before building.

**Description:** (1) Deleting an entry (swipe "Eliminar" and the edit-sheet delete button) removes it at once: no "Alimento eliminado" message, no confirmation, no undo (user decision: deleting is hard to do by mistake). (5) Remove "Copiar alimentos" and its selection mode (long-press, "Seleccionar alimentos", `SelectionActionBar`, `CopyEntriesSheet`, `CopyEntries`, unused repository batch methods). (6) Remove "Copiar comida" on sections (the named, reusable whole meal the user wants is T-061). (8) Remove the view modes: keep only the "Detalle" layout (drop `ViewModeSelector`, the view-mode `DiaryPreferences`/DataStore if unused). Also remove "Repetir comida de ayer" and its "Deshacer" (user: remove all copy functionality; T-061 replaces it with named meals saved from any day). Drop obsolete strings, testTags and tests; update `E4-food-diary.md` (US-19 Q2/Q3 and scenarios) accordingly.

**Acceptance criteria:**
- [x] Swipe + "Eliminar" and the edit-sheet delete remove the entry immediately, with no message or dialog; totals update.
- [x] No "Copiar alimentos", "Copiar comida", selection mode or view selector anywhere in the Diary; the Diary shows the Detalle layout only.
- [x] "Repetir comida de ayer" and its undo are removed too; no copy functionality remains in the Diary.
- [x] Planning docs and Gherkin updated; lint + `testLocalDebugUnitTest` green.

**Dependencies:** Checkpoint E4-B feedback · **Estimated scope:** M

---

## T-067: E4-B feedback — manual foods, scanner and meal structure (fix)

**Origin:** Checkpoint E4-B feedback, user 2026-10-20 (items 2, 3, 7). **Flow:** UI + core. Plan in a fresh session after T-066 is merged; the scanner may be split into its own task if it grows.

**Description:** (2) "Crear alimento" is reachable from the food search at any time (barcode optional), not only after failed scans or empty results. (3) Barcode scanner accepts a code only after a stable read (same code over several consecutive frames and/or a short settle time; check autofocus and analysis resolution); the user reports blurry early reads and up to 5 retries in low light. (7) Meal structure moves from the Diary "Comidas" button to Perfil → UI preferences, as a compact list with icon buttons: rename/reorder via icons, a trash button per meal (at least 1 meal must remain; a meal with logged entries cannot be deleted: blocked with a hint; an unused meal is deleted at once), no enable/disable toggle, no "+" button (a single "Añadir comida" action). Remove the "Comidas" button from the Diary. UX rule for ux-reviewer: optimise space and use icons for edit actions.

**Acceptance criteria:**
- [x] A food can be created from the search without scanning or failing a scan.
- [x] Scanner: `enableAutoZoom()` added to the Google code scanner (user decision 2026-10-21: keep the Google scanner in phase 1, no CameraX); the user re-tests in low light at the checkpoint.
- [x] Meal structure is edited from Perfil → preferences of the UI; the Diary has no "Comidas" button; at least 1 meal remains; a used meal cannot be deleted.
- [x] `ux-reviewer.md` records the space/icon rule; lint + `testLocalDebugUnitTest` green.

**Dependencies:** T-066 · **Estimated scope:** M-L

---

### Checkpoint E4-B — Scanner, manual foods, your own meals, correct and reuse the diary 👤
- Feedback received 2026-10-20 and handled in T-066/T-067 (OFF lacks liquids like milk: no action, add them manually). T-061 provides the named reusable meal (replaces "Copiar comida"). After T-066 and T-067 re-present only the changed items to re-test, then release T-061.
- [ ] T-058…T-060 merged, CI green (`deploy-supabase` green if T-058 or T-059 added a migration).
- [x] 👤 Install the APK (`! python scripts/deliver-apk.py`) and try: *(2026-10-21: round 2 feedback was UI-only and postponed to E8; released T-061)*
  - Scanner (real products at home): scan a few packaged foods and a drink: the sheet opens, the drink uses ml; add them to a meal. Scan something unknown: "Producto no encontrado" → "Crear alimento" with the code filled in; save it with a portion ("1 ración"), then scan it again: it opens your food. Close the scanner without scanning: back to the search, no error.
  - Manual foods: create a food from a search with no results; try an empty name or negative kcal ("Guardar" disabled).
  - Your own meals: open the meal settings from the Diary; rename "Merienda" (e.g. "Post-entreno"), add "Segundo desayuno" after Desayuno, move Cena above Merienda, save: the Diary follows. Disable an empty meal (gone from the Diary and the meal picker), re-enable it; try to disable a meal with foods on the day you are viewing (blocked, with a hint). Add a food to the custom meal; renamed meals keep their totals.
  - Tap an entry: change its grams or portion, move it to another meal, save; the totals follow.
  - Swipe an entry left → "Eliminar": it disappears at once (no message, no undo) and the totals follow.
  - The Diary has no "Copiar alimentos", "Copiar comida", "Repetir comida de ayer" or view selector (removed in T-066).
  - Leave the app and come back with the food-creation form half filled, with unsaved meal settings and with the edit sheet open: the same tab, screen and draft are restored.
  - Out of scope: editing or deleting foods you created (not in Phase 1 stories); templates and history (E4-C).

---

## T-061: Saved meals ("Comidas", templates in the backend)

**Story:** US-21 · **Flow:** UI + backend · **Scenarios:** Saving a section as a comida; Duplicate name; Adding a comida to a meal; Editing a comida; Renaming and deleting a comida · **Decision:** ADR-42 (no quick add; "Comidas" wording; overwrite or save as new)

**Description:** Backend: one migration with `meal_templates` (`name` unique per user, `meal_slot_id` null = any meal) and `meal_template_items` (`template_id` on delete cascade, `food_id`, `amount`, `portion_label`, `position`), owner RLS on templates and RLS via the parent on items (`03` §3), items may only reference foods the caller can read; pgTAP. App (UI wording "comida"): a "Guardar como comida" icon on each section with entries → name dialog → comida with the section's foods and quantities; "Ya existe una comida con ese nombre" on a duplicate. A "Comidas" tab in the food search adds a comida to the chosen meal (batch insert, snapshots from current food values, dashboard updated at once). From that tab the user edits a comida (change quantities, remove foods): "Guardar" asks "Sobrescribir" or "Guardar como nueva" (the latter asks for a name, same uniqueness rule); rename and delete are also available. Entries already logged never change. Edit draft restored (ADR-40); writes disabled offline. No quick-add chips on the Diary.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/meal_templates.test.sql`: RLS on and forced on both tables; cross-user isolation including an item inserted into B's template and an item referencing B's personal food; duplicate name rejected; deleting a template deletes its items; deleting a user with templates succeeds.
- [x] Desayuno with "Huevos revueltos 120 g" and "Batido de proteína 300 g" → "Guardar como comida" named "Desayuno A" creates it with both foods and quantities; a second "Desayuno A" shows "Ya existe una comida con ese nombre".
- [x] "Añadir a la comida" → "Comidas" → "Desayuno A" adds its foods to the chosen meal and the ring updates at once.
- [x] Editing "Desayuno A": "Sobrescribir" replaces it; "Guardar como nueva" with a new name creates another and leaves the original unchanged; a taken name is rejected.
- [x] Renaming and deleting are saved; entries created from it earlier keep their values.
- [x] DoD: a full breakfast is logged from a saved comida in a few taps from the search.

**Host tests (per scenario):** `MealTemplatesViewModelTest` with fakes + Turbine (Saving a section; Duplicate name; Editing: overwrite / save as new; rename/delete); `FoodSearchViewModelTest` additions (Adding a comida to a meal + immediate dashboard update); `TemplateEditorScreenTest` and `DiaryScreenTest` additions (Robolectric: duplicate-name error, overwrite / save-as-new choice, edit/delete); restoration tests; pgTAP `meal_templates.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** Checkpoint E4-B
**Files likely touched:** `supabase/migrations/<ts>_meal_templates.sql`, `supabase/tests/meal_templates.test.sql`, `shared/…/domain/model/MealTemplate.kt`, `shared/…/domain/repository/MealTemplateRepository.kt`, `shared/…/data/repository/*`, DI, `composeApp/…/feature/templates/{TemplatesTab,TemplateEditorScreen,TemplateEditorViewModel,SaveTemplateDialog}.kt`, `composeApp/…/feature/diary/{DiaryScreen,DiaryViewModel,MealSection}.kt`, `composeApp/…/feature/food/{FoodSearchScreen,FoodSearchViewModel}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, strings, tests
**Estimated scope:** L

---

## T-062: Diary history and summaries

**Story:** US-24 · **Flow:** UI + backend (`food_diary_summary`) · **Scenarios:** Opening the history; Daily summary; Weekly average; Monthly total; No data

**Description:** Backend: one migration with `food_diary_summary(p_from date, p_to date)` (`security invoker`; per `log_date` the caller's kcal and P/C/F totals, only days with entries) + pgTAP. Domain (`04-business-rules.md` §2.1, pure): day summary against the target, weekly average over the logged days of the last 7 days, monthly total and average per logged day. App: "Historial" from the Diary (entry point by ux-reviewer) opens a month calendar (`MonthCalendarGrid`, new design-system component) with the days that have entries marked, month navigation not past the current month; selecting a day shows its summary (`DiaryHistoryRow`, new design-system component: kcal and P/C/F against the target) and opens that day's Diary (reusing T-056's day navigation with a date argument; back returns to the history); week and month summary views; a day without entries reads "Sin registros". Read-only: no write path. Selected month and day restored (ADR-40).

**Acceptance criteria:**
- [x] Migration + `supabase/tests/food_diary_summary.test.sql`: per-day sums match the entries; other users' entries and days outside the range ignored.
- [x] The calendar marks only days with entries; tapping one opens that day's Diary.
- [x] 18 October with 1.840 kcal → its summary shows 1.840 kcal and P/C/F against the target.
- [x] Week summary: average daily kcal and macros over the logged days of the last 7 days (days without entries not counted).
- [x] Month summary: total kcal and the average per logged day of that month.
- [x] A day without entries shows "Sin registros"; loading skeleton and `ErrorState` with "Reintentar".

**Host tests (per scenario):** `DiaryHistorySummaryTest` (Daily summary; Weekly average; Monthly total; logged-days-only averages); `DiaryHistoryViewModelTest` with fakes, a test `Clock` and Turbine (Opening the history; No data; month navigation); `DiaryHistoryScreenTest` (Robolectric: marked days, "Sin registros", week and month texts, tap → day navigation event); `MonthCalendarGrid` catalog entry and test; restoration tests; pgTAP `food_diary_summary.test.sql`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-061
**Files likely touched:** `supabase/migrations/<ts>_food_diary_summary.sql`, `supabase/tests/food_diary_summary.test.sql`, `shared/…/domain/usecase/DiaryHistorySummary.kt` + tests, `shared/…/domain/repository/FoodDiaryRepository.kt`, `shared/…/data/repository/*`, DI, `composeApp/…/feature/diary/{DiaryHistoryScreen,DiaryHistoryViewModel,DiaryHistoryUiState,DiaryScreen}.kt`, `composeApp/…/designsystem/{MonthCalendarGrid,DiaryHistoryRow}.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, catalog, strings, tests
**Estimated scope:** M

---

## T-068: Full OFF product data and micronutrients in the food details

**Story:** US-16b (numbered US-25 until 2026-10-21) · **Flow:** backend + UI (ux-reviewer for the details section) · **Scenarios:** A product is cached with everything OFF sent; Micronutrients in the food details; Food without extra data; Search results stay light; Foods cached before this change · **Decision:** ADR-43 · Origin: user request 2026-10-21.

**Description:** Backend: one migration adds `foods.off_data jsonb null` and deletes `source = 'off'` rows with null `off_data` that nothing references (`food_log_entries`, `favorite_foods`, `meal_template_items`); referenced rows are kept. `off-client.ts` requests products without the `fields` filter (everything OFF returns is stored; if the unfiltered search exceeds the 8 s timeout the planner logs `BLOCKED` and asks the user, never narrowing `fields`) and returns the raw product; `food-store.ts` writes it to `off_data`; `food-barcode` also refreshes rows whose `off_data` is null; search responses omit it. App: the food details (the food sheet) read `off_data` for that food on demand and list the micronutrients OFF provides per 100 g / 100 ml with Spanish labels (fiber, sugars, saturated fat, salt/sodium, vitamins, minerals); absent values are hidden. Manual foods: the creation form gets optional fields for saturated fat, sugars, fiber and salt from the label (per 100 g/ml), stored in `foods.micronutrients` through a new `p_micronutrients jsonb` argument of `create_personal_food` (user decision 2026-10-21; no vitamins/minerals in the form); the details show them like OFF ones. Display only: no targets, totals or changes to how entries are computed. Show the user the proposed wording and layout of the section before building. Do not touch training data, preferences or any user-owned row.

**Acceptance criteria:**
- [x] Migration + extended pgTAP: `off_data` column; unreferenced null-`off_data` OFF foods deleted; referenced ones kept with the same id; entries, favourites and templates untouched; RLS unchanged.
- [x] `deno test` with a full-product fixture: the raw object lands in `off_data`, typed columns unchanged; `food-search` responses carry no `off_data`; `food-barcode` refreshes a row with null `off_data` younger than 30 days.
- [x] A food with fiber, sugars, salt and vitamin C shows them in its details per 100 g (ml for drinks); missing nutrients are not listed; a manual food saved with fiber 3 and salt 0,5 shows them, and one saved without them shows no section; a negative value disables "Guardar" (form draft restored, ADR-40).
- [x] Mapper unit tests (units, ordering, labels, malformed values ignored); details screen host test; restoration unaffected (ADR-40).
- [ ] Looked at by the user at Checkpoint E4-C (scan a real product, open its details).

**Host tests (per scenario):** `OffMicronutrientsMapperTest`; `FoodRepositoryTest` additions (details load `off_data` on demand, search stays light); food sheet Robolectric test (section shown / hidden); pgTAP `foods_off_data.test.sql` (incl. `create_personal_food` with micronutrients: valid, unknown key, negative); food-creation form tests; `deno test` for `_shared/off-client` and `food-barcode`.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; `deno test` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote (the cleanup runs on production inside the migration).

**Dependencies:** T-062
**Files likely touched:** `supabase/migrations/<ts>_foods_off_data.sql`, `supabase/tests/*`, `supabase/functions/_shared/{off-client,food-store}.ts` + tests, `food-barcode/handler.ts`, `composeApp/…/feature/food/` creation form, `shared/…/domain/model/Micronutrients.kt` + mapper, `shared/…/domain/repository/FoodRepository.kt`, `shared/…/data/repository/*`, `composeApp/…/feature/food/*` (food sheet), strings, tests
**Estimated scope:** M

---

## T-072: Shared interaction rules, swipe-to-delete and wording (E4-C round 2, ADR-45)

**Story:** US-21, US-24, US-18 · **Flow:** docs + UI · **Type:** fix

**Description:** User feedback at Checkpoint E4-C round 2 (2026-10-21). (a) ADR-45 and `05-design-system.md` §8 already hold the generic rules; ux-reviewer checks them (done in this PR's docs). (b) One shared swipe-to-delete component in `designsystem/`: slow and heavy, fires only at the end of the travel, a shorter drag snaps back; it replaces `SwipeableEntryRow` and `SwipeableTemplateRow`. (c) Delete never offers "Deshacer" (the user asked for none earlier and T-069 added it): remove the snackbar, the pending-delete state and its tests for comidas, and confirm that diary entries have none. (d) History texts: remove "Total del mes" and "Días registrados"; day/week/month all use "Media diaria" and the "Últimos 7 días · n de 7 días registrados" line goes. (e) Perfil -> Estadísticas: the diary entry reads "Calorías" (like "Fuerza"). Planner/ux-reviewer sweep the app for other swipe or delete instances.

**Acceptance criteria:**
- [x] Swipe on a diary entry and on a comida row deletes only when dragged to the end; a short drag restores the row (host tests); no "Deshacer" anywhere.
- [x] History summaries use "Media diaria" for week and month and show no month total or registered-days count; Perfil entry reads "Calorías".
- [x] State survives leaving the app (ADR-40).

**Verification:** lint, host tests and CI green. **Dependencies:** T-071 · **Estimated scope:** M

---

## T-073: Comida details with nutrition and "Guardar" (E4-C round 2, ADR-45)

**Story:** US-21 · **Flow:** UI + core · **Type:** fix

**Description:** Redesign the comida details. Each food row shows its kcal and macros (not only grams) and the header shows the comida's total kcal and macros. Tapping a food opens its food details, where the grams are edited or the food is removed from the comida; swiping a food to the left removes it (T-072 component). Remove the "Editar" button and the "Editar comida" screen: the details are the editor, with a pencil to rename and a single "Guardar" that saves renames, quantities and removals together (overwrite or save as new stays). Deleting the whole comida from its details runs at once and closes the details. Entries already logged do not change.

**Acceptance criteria:**
- [ ] Rows show kcal and macros per food, the header the sum; both update with the draft.
- [ ] Tapping a food opens its details to change grams or remove it; swipe-left removes it; neither is saved until "Guardar".
- [ ] "Eliminar comida" deletes at once and closes; no "Editar" button or edit screen remains.
- [ ] Draft and screen survive leaving the app (ADR-40).

**Verification:** lint, host tests and CI green. **Dependencies:** T-072 · **Estimated scope:** M

---

## T-074: Text food search limited to Spain (E4-C round 2, ADR-46)

**Story:** US-18 · **Flow:** backend · **Type:** fix

**Description:** The text search is poor (user, 2026-10-21). In `supabase/functions/_shared/off-client.ts`, `searchOff` adds `tagtype_0=countries`, `tag_contains_0=contains`, `tag_0=spain` (keeping `search_terms`, `json=1`, `page`, `page_size=20`, `lc=es`, `cc=es`; no `sort_by`; no `fields`, ADR-43) and filters hits with a new `isSpainSearchHit(raw)` (`countries_tags` includes `en:spain` and either `product_name_es` is non-empty or `lang === "es"` with a non-empty `product_name`) before `normalizeProduct`. `fetchOffProduct` (barcode) is unchanged. Paging must use the raw OFF count so a filtered-out page does not end the search early; stale cached search results are purged or re-keyed.

**Acceptance criteria:**
- [x] Deno tests for the predicate (Spain + Spanish name, Spain + English name only, non-Spain, multi-country) and for paging when the filter empties a page.
- [x] Request parameters verified in tests; barcode lookup untouched.
- [x] Latency of the production search measured and reported in the PR.

**Verification:** `deno test`, supabase-ci and CI green. **Dependencies:** T-073 · **Estimated scope:** S

---

## T-075: Food details in a comida saved with "Guardar" (E4-C round 3, ADR-45)

**Story:** US-21 · **Flow:** UI + core · **Type:** fix

**Description:** User feedback at Checkpoint E4-C (2026-10-21). In a comida's editor, opening a food shows the details sheet with its quantity, and the quantity is applied automatically when the sheet closes. This breaks ADR-45 ("Editing": one "Guardar", nothing saved per action). The sheet gets an explicit "Guardar" that applies the quantity to the comida draft; closing without it discards the change (as the X does). Remove the apply-on-dismiss. The ux-reviewer sweeps the whole app for any other edit that applies on close or per action (diary entry editing, templates, meal structure, food creation) and the plan includes them.

**Acceptance criteria:**
- [x] Host test: change the quantity, close the sheet without "Guardar" → the comida draft is unchanged; with "Guardar" → the quantity and kcal/macros update.
- [x] No other screen applies an edit on dismiss; the sweep result is in the PR body.

**Verification:** lint, host tests and CI green. **Dependencies:** T-074 · **Estimated scope:** S

---

## T-069: Fix comida edit crash and comida details (E4-C feedback)

**Story:** US-21 · **Flow:** UI + core · **Type:** fix

**Description:** User feedback at Checkpoint E4-C (2026-10-21). Editing a comida crashes the app: reproduce with a host test and fix. Redesign the comidas list: tapping a comida opens its details (foods, quantities) with a pencil to rename it, edit and delete; swiping a comida in the list deletes it with "Deshacer"; the three row buttons are removed and "Añadir" becomes a "+". Rename is thus part of the details/edit flow.

**Acceptance criteria:**
- [x] Editing a comida (quantities, remove a food, overwrite or save as new) no longer crashes; a regression host test covers it.
- [x] Tapping a comida opens its details; rename (pencil), edit and delete live there. Swipe in the list deletes with "Deshacer"; the "Añadir" action is a "+" button. Entries already logged do not change.
- [x] The state survives leaving the app (ADR-40).

**Verification:** lint, host tests and CI green. **Dependencies:** T-068 · **Estimated scope:** M

---

## T-070: Fix food details sheet and clean the food cache (E4-C feedback)

**Story:** US-16b · **Flow:** backend + UI · **Type:** fix

**Description:** The food details sheet shows no micronutrients and its layout is broken (a horizontal line over "gramos" and "100 g (100 g)"). Drop the "100 g (100 g)" label when the portion is just 100 g. Cached OFF foods stored before T-068 lack `off_data`; user decision 2026-10-21: delete all `source = 'off'` foods and everything referencing them (`food_log_entries`, `favorite_foods`, `meal_template_items`; templates left empty are removed); food data is not stable yet. Workout data is never touched.

**Acceptance criteria:**
- [x] Migration deletes every OFF food and its dependants; pgTAP covers it and that workout tables are untouched.
- [ ] The details sheet shows the micronutrients (host test with `off_data` and with manual `micronutrients`) and has no stray line; no duplicated "100 g (100 g)".
- [ ] After the merge: `deploy-supabase` green; a fresh search/scan fills `off_data`.

**Verification:** lint, host tests, pgTAP and CI green. **Dependencies:** T-069 · **Estimated scope:** M

---

## T-071: Diario calendar button and Perfil "Estadísticas" (E4-C feedback, ADR-44)

**Story:** US-24 · **Flow:** UI · **Type:** feat

**Description:** One Diario UI that always shows the calendar button next to the day arrows (remove the `showHistory` conditional). Move the diary history (Día/Semana/Mes summaries and month calendar) out of the Diario to a full-screen destination reached from an "Estadísticas" entry on the Perfil root (ADR-37 pattern). Tapping a day there opens the normal Diario on that day, with its calendar button and a back path.

**Acceptance criteria:**
- [x] The Diario shows the calendar button whether opened from its tab or from the statistics calendar; one `DiaryScreen`, no variant.
- [x] Perfil root has "Estadísticas"; the history screen keeps US-24 behaviour (marked days, summaries against target, week average, month total, "Sin registros").
- [x] The Diario no longer has its own history entry; state and navigation survive leaving the app (ADR-40).

**Verification:** lint, host tests and CI green. **Dependencies:** T-070 · **Estimated scope:** M

---

## T-063: E4 closure

**Story:** E4 Definition of Done · **Flow:** PM

**Description:** Closes E4: final regression on the phone with real scanned foods, approval, tag `v0.4.0` and the retrospective.

**Acceptance criteria:**
- [x] T-055…T-062 and T-068 merged with green CI; pgTAP for the 7 tables (`foods`, `food_portions`, `food_log_entries`, `meal_slots`, `meal_templates`, `meal_template_items`, `favorite_foods`) and the two RPCs green; `deno test` for `food-search` and `food-barcode` green; `deploy-supabase` green after the last migration.
- [x] Host tests green for every E4 Gherkin scenario.
- [x] Checkpoints E4-A and E4-B approved; every bug the user reported fixed; look-and-feel feedback appended to the E8 UI-refinement backlog (`planning/epics/E8-finalization.md`).
- [x] `tasks/todo.md` (E4 ✅, next-task pointer) updated through a PR.

**Verification:**
- [x] `gh run list` shows the last `deploy-supabase` green; `git tag` lists `v0.4.0` after approval.

**Dependencies:** T-073
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

### Checkpoint E4-C — Templates, history, final regression and approval 👤
- [x] T-061, T-062, T-068…T-075 merged, CI green, `deploy-supabase` applied every migration, every reported bug fixed.
- [x] 👤 *(approved by the user, 2026-10-21)* Install the APK (`! python scripts/deliver-apk.py`) and try:
  - Micronutrients: scan or search a real packaged food, open its details: fiber, sugars, salt and the vitamins/minerals OFF has are listed per 100 g. Create a food typing saturated fat, sugars, fiber and salt from its label: they show in its details; one created without them shows no section.
  - Comidas: fill a breakfast and "Guardar como comida"; try the same name again (error). From any meal's search, "Comidas" → add it there: the foods appear and the ring jumps at once. Edit it (quantities, remove a food) and choose "Sobrescribir" or "Guardar como nueva"; rename and delete one: entries already logged do not change.
  - History: Diario → "Historial": the logged days are marked; open one (its Diary), go back; read a day's summary against your target, the week average and the month total; a day with nothing shows "Sin registros".
- [x] 👤 *(approved, 2026-10-21)* Final regression: log one real day end to end:
  - Breakfast from a template in one tap; lunch by search (≤ 3 taps after opening the search) with a named portion; a drink in ml; a scanned product.
  - Rename or reorder a meal; copy a food to another meal or day; edit and delete an entry with "Deshacer".
  - Check the ring and bars against your target, then "Historial" for today and the week.
  - Leave the app and come back while editing a template and on a past month in "Historial": the same tab, screen and draft are restored.
  - Approval → `git tag -a v0.4.0 -m "Epic E4"` + `git push --tags` + retrospective.

---

# E5 · Weight tracking

Stories: `planning/epics/E5-weight.md` (US-25…US-27). Plan: [tasks/plan.md](plan.md). One task per story as a vertical slice, with splits for size. US-25 → the manual weight backend (T-076), the weight screen with the weight calendar (T-077; added with Q1, too big for one PR together with T-076) and body circumferences (T-078, its own vertical slice). US-26 (Health Connect) is T-079. US-27 → the trend chart and weekly rate (T-080) and goal, BMI, comparison and interpretation (T-081). Each migration ships with the task that needs it (at most one per task; a merged migration is never edited): T-076 the manual-measurement RPC, T-078 `body_circumferences`, T-079 the composition columns, the Health Connect copy id and `health_sync_state`, T-081 `profiles.target_weight_kg`.
Flow: T-076 is backend only (no ux-reviewer); every other task is UI (planner → ux-reviewer → developers → PR → review → merge); the backend parts are verified with pgTAP and `deploy-supabase`. The app is verified by hand at the 👤 checkpoints with `python scripts/deliver-apk.py`. Every new user-owned table cascades from `auth.users` (ADR-29). Every screen with input ships a restoration test (ADR-40). The interaction rules of ADR-45 / `05-design-system.md` §8 apply to every task: delete at once with no "Deshacer" (the US-25 Gherkin now says so), swipe through `SwipeToDeleteRow`, one explicit "Guardar" per sheet and nothing applied on dismiss, a row opens its details.
Story IDs: the E4 micronutrient story, numbered "US-25" before, is now US-16b (T-068, T-070), so US-25 means only the E5 story.
Questions in `tasks/plan.md`: Q1 (weight calendar, date locked when editing), Q2 (edits and deletes follow in Health Connect) and Q3 (direction-aware target wording, progress from the first weight) resolved by the user (2026-10-21); no open blocking question.
Checkpoint (one, user decision 2026-10-22; it was two, the E5-A feedback became T-083 and E5-A was folded into E5-B): E5-B after T-081 (manual weight with the calendar, circumferences, Health Connect with the real scale, the weight trend, goal and metrics, and the final regression; approval releases T-082 → `v0.5.0`).

## T-076: Manual weight backend: one weight per day, locked date and the repository

**Story:** US-25 (weight part, data side) · **Flow:** backend + shared domain/data (no UI, no ux-reviewer) · **Scenarios (data side):** Logging today's weight; Optional body fat; Logging a weight for a past day; Validation (weight 19,9 / 401, fat 80); Weight calendar, editing and deletion

**Description:** One migration with a `security invoker` RPC (e.g. `save_body_measurement(p_id uuid default null, p_weight_kg, p_body_fat_pct, p_measured_at, p_day_start, p_day_end)`). A new save (null id) inserts the day's manual row or updates the existing manual row of that local day (one manual weight per day, `03-data-model.md` §4). An edit (id set) changes only the weight, the fat and the time within the row's own local day: a `measured_at` outside that day is rejected (`22023`), because the date is locked when editing (Q1 resolved 2026-10-21). Only the caller's `manual` rows (`P0002` otherwise); Health Connect rows are never edited but can be deleted. The per-day semantics match `save_preferences` (share the SQL logic without editing the merged migration; pgTAP covers both). Domain (pure): `ValidateBodyMeasurement` (weight 20–400 and body fat 2–75, 1 decimal, the allowed range as the error), the current-weight rule (latest row by `measured_at`, any source) and `MeasurementsByDay` (group a month's rows by local day for the calendar: manual row, Health Connect rows). `BodyMeasurementRepository` gains the rows of a date range (a calendar month), save through the RPC (local-day bounds from the injected `Clock` and time zone) and delete. No screens in this task.

**Acceptance criteria:**
- [x] Migration + pgTAP (`supabase/tests/save_body_measurement.test.sql` or extended `body_measurements.test.sql`): a second manual save on the same local day updates the first (one row); a save on another day inserts; an edit that moves `measured_at` to another day is rejected; 19.9 kg, 401 kg and 80 % fat rejected; another user's row cannot be edited or deleted; a Health Connect row cannot be edited; `save_preferences` tests still green; deleting a user with measurements succeeds.
- [x] Repository tests: the RPC payload carries the local-day bounds (23:59 and 00:00 cases); a month query returns that month's rows; delete by id.
- [x] Q1 recorded in `03-data-model.md` §4 (done in the breakdown PR) and matched by the RPC.

**Host tests (per scenario):** `ValidateBodyMeasurementTest` (Validation outline, boundaries 20/400 and 2/75); `MeasurementsByDayTest` (manual and Health Connect rows per day, local-day edges); `BodyMeasurementRepositoryTest`; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** E4 (`v0.4.0`)
**Files likely touched:** `supabase/migrations/<ts>_save_body_measurement.sql`, `supabase/tests/save_body_measurement.test.sql`, `shared/…/domain/model/{BodyMeasurement,WeightEntry}.kt`, `shared/…/domain/usecase/{ValidateBodyMeasurement,MeasurementsByDay}.kt` + tests, `shared/…/domain/repository/BodyMeasurementRepository.kt`, `shared/…/data/repository/*`, `shared/…/data/remote/*Dto.kt`, DI, tests
**Estimated scope:** M

---

## T-077: Weight screen with the weight calendar

**Story:** US-25 (weight part, UI) · **Flow:** UI · **Scenarios:** Logging today's weight; Optional body fat; Logging a weight for a past day (calendar; the chart part is in T-080); Validation (weight 19,9 / 401, fat 80); Weight calendar, editing and deletion (manual rows; Health Connect rows are shown with their icon and can only be deleted, tested with fake rows until T-079)

**Description:** Navigation: a "Peso" row in the Perfil root "Estadísticas" section (next to "Fuerza" and "Calorías") opens a full-screen weight destination (`AppTopBar` with a back arrow, ADR-37; reference `design/stitch/progress/screen.png`) that later tasks extend. On that screen: a current-weight panel (`MetricTile`: "73,9 kg", the measurement's date and time, the latest body fat when present). "Registrar peso de hoy" opens today's day sheet: prefilled with the latest weight when today has no manual weight, or today's manual weight to edit when it has one. "Historial de mediciones" is a weight calendar (Q1 resolved 2026-10-21, user): a month grid reusing `MonthCalendarGrid` (T-062; check its callers before changing it) with the days that have a measurement marked, month navigation not past the current month. Tapping an empty day opens a sheet to set that day's weight: the date is fixed to that day, the weight is prefilled with the latest earlier weight, optional body fat %, and a time (now for today, editable within the day, never in the future). Tapping a day with measurements opens that day: its manual weight opens the edit sheet (weight, fat and time; the date is locked; one "Guardar"; delete at once); its Health Connect readings show their icon and are delete-only (`SwipeToDeleteRow` or a delete button); a day with only Health Connect readings also offers setting that day's manual weight. "Guardar" is disabled and the allowed range is shown while a value is invalid. No "Deshacer" (ADR-45). The panel and the calendar update in place after each write. Empty state (every measurement deleted), loading skeleton, `ErrorState` with "Reintentar", writes disabled offline. The visible month, the open day and the sheet draft survive leaving the app (ADR-40).

**Acceptance criteria:**
- [x] Perfil → "Peso" opens the weight screen; back returns to Perfil.
- [x] Latest weight 74,2 kg and no weight today → "Registrar peso de hoy" opens the sheet with 74,2 and today; 73,9 + "Guardar" → a manual 73.9 kg row and the panel reads "73,9 kg"; tapping it again opens today's 73,9 to edit.
- [x] 15,1 % body fat saved → the row stores `body_fat_pct` 15.1 and the panel shows it.
- [x] Calendar: days with a measurement are marked; the next-month arrow stops at the current month; tapping empty yesterday → a sheet for yesterday, time set to 07:30, saved at that instant and the day becomes marked.
- [x] Tapping a day with a manual weight → edit weight, fat or time (no date field) and save; delete at once (no message, no "Deshacer"); a Health Connect reading (fake) shows its icon, offers no edit and can be deleted.
- [x] Weight 19,9 or 401, or fat 80 → "Guardar" disabled and the allowed range shown.
- [x] Leaving the app on a past month with a day's sheet half filled and coming back (also after process death) restores the screen, the month and the draft.
- [x] Tablet: panel on the left, calendar on the right; phone single column.

**Host tests (per scenario):** `WeightViewModelTest` with fakes, a test `Clock` and Turbine (Logging today's weight incl. prefill and reopen-to-edit; Optional body fat; Logging a weight for a past day; Weight calendar month navigation, editing and deletion; Health Connect row delete-only); `MeasurementSheetViewModelTest` (Validation; locked date; time limits); `WeightScreenTest` (Robolectric: panel "73,9 kg", marked days, no date field when editing, disabled "Guardar" with the range, Health Connect icon, delete, back arrow, two columns at 840 dp); `ProfileRootScreenTest` addition ("Peso" row); restoration tests.

**Verification:**
- [x] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.

**Dependencies:** T-076
**Files likely touched:** `composeApp/…/feature/weight/{WeightScreen,WeightViewModel,WeightUiState,WeightCalendar,WeightDaySheet,MeasurementSheet,MeasurementSheetViewModel}.kt`, `composeApp/…/designsystem/MonthCalendarGrid.kt` (only if a parameter is needed; + callers), `composeApp/…/feature/profile/ProfileRootScreen.kt`, `composeApp/…/app/{AppRoutes,AppNavHost}.kt`, strings, tests
**Estimated scope:** M–L

---

## T-078: Body circumferences and the waist-to-hip ratio

**Story:** US-25 (circumference part) · **Flow:** UI + backend · **Scenarios:** Logging a circumference; Waist-to-hip ratio; Editing and deleting a circumference; Circumference validation (19,9 / 251)

**Description:** Backend: one migration with the `circumference_site` enum and `body_circumferences` per `03-data-model.md` §4 (`value_cm numeric(5,1)` with check 20–250, `source` `manual`, index `(user_id, site, measured_at desc)`, `set_updated_at` trigger, `user_id` on delete cascade), RLS enabled and forced with owner policies on `(select auth.uid())`, client grants as for `body_measurements`; pgTAP. Domain (pure): the site list (neck, chest, waist, hip, arm, thigh, calf; Spanish labels in string resources, wording by ux-reviewer), value validation (20–250 cm, 1 decimal), `WaistHipRatio` (`04-business-rules.md` §3.9: latest waist / latest hip, 2 decimals, "Sin datos suficientes" when either is missing). `CircumferenceRepository`. UI: a "Circunferencias" section on the weight screen. "+ Medida" opens a sheet with the site dropdown, the value in cm and the date and time (default now), and "Guardar" disabled with the allowed range shown while invalid. Each site has a history ordered by date (`CircumferenceRow`, a new design-system component with a catalog entry: site, value in cm, date). A row opens the sheet to edit it; delete is at once (swipe or the sheet), with no "Deshacer". The ratio line ("0,86") recalculates in place. The ratio's interpretation (status by sex) is in T-081. Draft restored (ADR-40); writes disabled offline.

**Acceptance criteria:**
- [x] Migration + `supabase/tests/body_circumferences.test.sql`: RLS on and forced; cross-user select/insert/update/delete blocked; 19.9 and 251 rejected; deleting a user with circumferences succeeds.
- [x] "+ Medida" → "Cintura" 84,5 cm → a waist row of 84.5 cm, shown in the waist history in date order.
- [x] Waist 84,5 and hip 98,0 → the section shows "0,86"; only one of them → "Sin datos suficientes".
- [x] Editing a value or deleting a row (at once) recalculates the ratio and the history.
- [x] 19,9 or 251 cm → "Guardar" disabled and the allowed range shown.
- [x] The sheet draft survives leaving the app and process death.

**Host tests (per scenario):** `WaistHipRatioTest` (84.5 / 98.0 → 0.86; a missing site → none; the latest row per site is used); `ValidateCircumferenceTest` (Circumference validation outline); `CircumferencesViewModelTest` with fakes + Turbine (Logging a circumference; Editing and deleting; ratio recalculated); `CircumferenceSectionTest` (Robolectric: "0,86", "Sin datos suficientes", disabled "Guardar" with the range, swipe delete); `CircumferenceRow` catalog entry and test; restoration tests; pgTAP.

**Verification:**
- [x] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-077
**Files likely touched:** `supabase/migrations/<ts>_body_circumferences.sql`, `supabase/tests/body_circumferences.test.sql`, `shared/…/domain/model/{BodyCircumference,CircumferenceSite}.kt`, `shared/…/domain/usecase/{WaistHipRatio,ValidateCircumference}.kt` + tests, `shared/…/domain/repository/CircumferenceRepository.kt`, `shared/…/data/repository/*`, `shared/…/data/remote/*Dto.kt`, DI, `composeApp/…/feature/weight/{CircumferenceSection,CircumferenceSheet,CircumferencesViewModel,WeightScreen}.kt`, `composeApp/…/designsystem/CircumferenceRow.kt`, catalog, strings, tests
**Estimated scope:** M

---

## T-083: User feedback: day-only forms, aligned weight sheet, all circumferences at once

**Story:** US-25 (fix from user feedback on T-076…T-078, 2026-10-22) · **Flow:** UI (+ small data change) · **Scenarios:** Logging today's weight; Logging a weight for a past day; Weight calendar, editing and deletion; Logging a circumference; Logging several circumferences at once; Measuring help; Circumference validation

**Description:** (1) Weight sheet bug: "Peso (kg)" and "Grasa corporal (%, opcional)" must sit on one horizontal line with the same height and no line crossing the inputs (screenshot from the user's phone: the right field is lower and a horizontal rule cuts through the form). (2) Day only, no hour, on the weight and circumference sheets (remove the hour wheels); manual rows are stored for the chosen day (today: the save instant; a past day: midday local time), displayed as a date without time; the current-weight panel shows the date only for manual rows. (3) Circumference sheet redesign: "Añadir medida" opens a sheet with the seven sites as optional fields and the day stepper; "Guardar" saves one row per filled site (disabled while all are empty or any value is invalid, range shown); no dropdown (`AppDropdownField` stays only if something else uses it, otherwise remove it and its catalog entry); a "?" icon per site opens short Spanish step-by-step instructions (text only, wording by ux-reviewer; images later in E8). Editing a saved row opens it with its single site. Rows with the same site and day: the latest saved wins for the ratio. No migration expected (tie-break by `created_at`); the planner confirms.

**Acceptance criteria:**
- [ ] The weight and body-fat fields of the add/edit sheet are on one line, same height, and no stray line crosses them (Robolectric layout test comparing the fields' top positions + a visual check at Checkpoint E5-A).
- [ ] Neither the weight sheet nor the circumference sheet shows an hour; a past-day weight is stored for that day; the panel shows only the date.
- [ ] Waist 84,5 + hip 98,0 in one sheet → two rows, ratio "0,86"; only waist → one row; all empty → "Guardar" disabled.
- [ ] 19,9 or 251 in any site → "Guardar" disabled with "Entre 20 y 250 cm".
- [ ] "?" next to a site shows its instructions.
- [ ] Drafts of both sheets survive leaving the app and process death.

**Verification:**
- [ ] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green; pgTAP green if the migration is needed.

**Dependencies:** T-078
**Estimated scope:** M

---

## T-079: Manual Health Connect sync (read and write-back)

**Story:** US-26 · **Flow:** UI + backend + platform · **Scenarios:** First sync and permissions; Incremental sync without duplicates; Weight and fat from the same weigh-in; Extra composition metrics; Writing back a manual entry; Editing or deleting a written-back entry; Writing back without permission; Permissions denied; Health Connect unavailable; Last sync status

**Description:** Backend: one migration adds the nullable `lean_mass_kg numeric(5,2)`, `water_kg numeric(5,2)`, `bone_kg numeric(4,2)` and `bmr_kcal integer` columns to `body_measurements` (range checks by the planner) and creates `health_sync_state` (`user_id` PK referencing `auth.users` on delete cascade, `last_synced_at timestamptz`, owner RLS enabled and forced). The import is idempotent on `unique (user_id, external_id)` (for example an RPC `import_health_measurements(p_rows jsonb, p_synced_at timestamptz)` that inserts with `on conflict do nothing` and advances `last_synced_at` in the same transaction; planner's choice). The composition columns are only written by the import; manual saves leave them null (ADR-35). Manual rows keep the id of the copy written to Health Connect in a new nullable column (e.g. `hc_record_id`; `external_id` stays for imports), written only after a successful write-back. pgTAP. Platform: `androidx.health.connect:connect-client` (latest stable, pinned). Manifest: READ/WRITE permissions for weight, body fat, lean body mass, body water mass, bone mass and BMR; `<queries>` for the Health Connect package; the privacy-policy rationale activity (`ACTION_SHOW_PERMISSIONS_RATIONALE`, plus the Android 14+ `VIEW_PERMISSION_USAGE` activity-alias) with a short Spanish privacy text, whose wording is shown to the user before building. Domain: the `HealthDataSource` port (availability: available, not installed or update required; granted permissions; read records; write weight and fat), `HealthConnectDataSource` in `composeApp` androidMain (`02-architecture.md` §2) and a `FakeHealthDataSource` for tests. `PairHealthReadings` (pure, `04-business-rules.md` §3.7): one row per weight record, `external_id` = the weight record's `metadata.id`; fat and composition records are merged by instant (± 2 min); records whose data origin is this app (either flavor's package) are skipped, so write-backs never come back as duplicates. `SyncHealthMeasurements`: the first sync reads the last 90 days, later syncs read since `last_synced_at` (the planner picks the change-detection method; see the plan's risks). Write-back: after a successful manual save (T-077 day sheet and the Preferencias weight) and only with the write permission, weight and fat are inserted into Health Connect and the record id is stored on the row. Editing a manual entry that has a Health Connect copy updates that copy, and deleting it deletes the copy (Q2 resolved 2026-10-21, option (a); `04` §3.7). An entry without a copy (saved without permission) is not written on edit. A missing permission or a failed Health Connect call never fails the app's save or delete and shows nothing. Deleting an imported reading removes it from the app only (Health Connect lets an app delete only its own records), and it is not re-imported. UI: a Health Connect banner on the weight screen with "Sincronizar"; states "Sincronizado ahora" (green indicator), "Sincronizado hace 12 min" (relative time from the injected `Clock`), "Permiso necesario" with a shortcut to request the permissions again, syncing, and unavailable (offers to install or update Health Connect from Google Play); disabled offline. Imported rows show on their day in the weight calendar with the Health Connect icon and delete-only (T-077). The real Health Connect integration is tested by hand only (epic technical note).

**Acceptance criteria:**
- [ ] Migration + pgTAP (`health_sync_state.test.sql`, `body_measurements.test.sql` additions): new columns and their ranges; `health_sync_state` RLS on and forced with cross-user isolation; importing the same rows twice adds none (`external_id` uniqueness); deleting a user with sync state succeeds.
- [ ] No permissions → "Sincronizar" opens the Health Connect permission screen for reading and writing; granted → the last 90 days are imported and the banner shows "Sincronizado ahora" with a green indicator.
- [ ] Last sync 2 days ago and 2 new readings → exactly those 2 are imported; tapping again creates nothing.
- [ ] A weight and a fat record within ± 2 min → one row with both; a weigh-in with weight, fat, lean mass, water, bone and BMR → one row with all six.
- [ ] Manual 73,9 kg + 15,1 % with the write permission → both written to Health Connect; without it → saved in the app, nothing written, no error.
- [ ] Denied permissions → "Permiso necesario" with the shortcut; nothing imported.
- [ ] Health Connect not installed or needing an update → the Google Play install/update is offered.
- [ ] Last sync 12 minutes ago → "Sincronizado hace 12 min".
- [ ] Editing a written-back 73,9 kg entry to 73,5 updates the Health Connect copy; deleting it deletes the copy; an entry saved without permission is not written when edited later; a failing Health Connect call keeps the app's change.
- [ ] A deleted imported reading is not re-imported by the next sync.
- [ ] DoD: a manual read and write test on the user's phone with the real scale, documented in the PR.

**Host tests (per scenario):** `PairHealthReadingsTest` (Weight and fat from the same weigh-in incl. the ± 2 min boundary; Extra composition metrics; own-origin records skipped); `SyncHealthMeasurementsTest` (First sync 90 days; Incremental sync; idempotent re-run); `HealthSyncViewModelTest` with `FakeHealthDataSource`, a test `Clock` and Turbine (First sync and permissions; Permissions denied; Health Connect unavailable; Last sync status); `MeasurementSheetViewModelTest` additions (Writing back a manual entry; Editing or deleting a written-back entry; Writing back without permission; a failing write keeps the save); `HealthConnectBannerTest` (Robolectric: every banner state and text); pgTAP.

**Verification:**
- [ ] `npx supabase db reset && npx supabase db lint && npx supabase test db` green; lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.
- [ ] `assembleProdDebug` merged manifest lists the twelve Health Connect permissions, the `<queries>` entry and the rationale activity.
- [ ] After the merge: `deploy-supabase` green; migration remote.

**Dependencies:** T-083 (no checkpoint before E5-B, user decision 2026-10-22)
**Files likely touched:** `supabase/migrations/<ts>_health_sync.sql`, `supabase/tests/{health_sync_state,body_measurements}.test.sql`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`, a rationale activity + strings, `shared/…/domain/port/HealthDataSource.kt`, `shared/…/domain/usecase/{PairHealthReadings,SyncHealthMeasurements}.kt` + tests, `shared/…/domain/repository/{BodyMeasurementRepository,HealthSyncRepository}.kt`, `shared/…/data/repository/*`, DI, `composeApp/src/androidMain/…/platform/HealthConnectDataSource.kt`, `composeApp/…/feature/weight/{HealthConnectBanner,HealthSyncViewModel,MeasurementSheetViewModel,WeightScreen}.kt`, `composeApp/…/feature/profile/PreferencesViewModel.kt` (write-back), tests
**Estimated scope:** L (the planner may run it as two dev slices: backend + domain + data source, then the banner and the write-back)

---

## T-080: Weight trend chart, time filters and weekly rate

**Story:** US-27 (trend part) + US-25 "Logging a weight for a past day" (chart part) · **Flow:** UI · **Scenarios:** US-27 Metrics panel; Pace against the goal; Two-layer chart; Time filter; Not enough data; Point detail · US-25 Logging a weight for a past day (chart)

**Description:** Domain (pure, `04-business-rules.md` §3.1–§3.4 examples as tests): `DailyWeights` (average of the measurements of each local day), `MovingAverage` (MA7 over the available daily values in `[d − 6, d]`; drawn only with at least 2 days in the window), `WeeklyRate` (`MA7(last day with data) − MA7(that day − 7)`, 2 decimals, "Sin datos suficientes" when either is missing; colour by the `nutrition_goals` strategy: deficit/surplus green or red, maintenance green within ± 0,20 else amber), and the filter ranges 1S/1M/3M/6M/1A (7/30/90/180/365 days back from today). Data: each range is loaded with 6 extra days before its start so the first day has its MA7. UI on the weight screen. The panel gains the `TrendPill` ("−0,45 kg esta semana" in the goal colour, or "Sin datos suficientes"). The pill sits inside a `MetricTile` and both are `surfaceOverlay`, so they get a container-colour parameter (consumes the E4 "Notes for later epics" item; check every caller). The chart extends `AppLineChart` (Vico, already pinned; the strength charts keep working): daily readings as low-opacity dots, a thick `athleticCyan` MA7 line and the legend "MA 7D". A `FilterChipRow` offers 1S/1M/3M/6M/1A, default 3M, with an animated transition; the chosen filter is kept in DataStore (a local preference, ADR-01) across launches. Long-pressing a point shows its date, that day's weight and that day's MA7. With a single measurement the pill reads "Sin datos suficientes" and the chart shows the dot with "Registra tu peso varios días para ver la tendencia". Chart and pill update after every write. The chart exposes a text summary as `stateDescription`. Tablet: panel and chart on the left, calendar and circumferences on the right. Restoration of the selected point (the filter is already persisted).

**Acceptance criteria:**
- [ ] Deficit with 90 days of entries → the pill shows "−0,45 kg esta semana" in green (fixture); MA7 up 0,30 kg → "+0,30 kg esta semana" in red; maintenance ± 0,15 → green, 0,25 → amber; surplus mirrored.
- [ ] The panel shows the current weight "74,2 kg" with its date and time and the latest body fat when present.
- [ ] Dots + MA7 line + "MA 7D"; the weight set from the calendar for yesterday at 07:30 sits on yesterday's date; the MA7 of the first visible day uses the 6 days before the range.
- [ ] Default 3M; "1M" shows the last 30 days with an animated transition; leaving the screen and relaunching the app keeps "1M".
- [ ] One measurement → "Sin datos suficientes" and the dot with "Registra tu peso varios días para ver la tendencia".
- [ ] Long-pressing a point shows its date, weight and MA7.
- [ ] `MetricTile` and `TrendPill` take a container colour; the pill's outline is visible inside the tile; existing callers unchanged.

**Host tests (per scenario):** `DailyWeightsTest`, `MovingAverageTest`, `WeeklyRateTest` (`04` §3 examples; colour per strategy; missing MA7 → not enough data); `WeightChartRangeTest` (filter days and the 6-day lead-in); `WeightTrendViewModelTest` with a fake preferences store, a test `Clock` and Turbine (Metrics panel; Pace against the goal; Time filter persisted; Not enough data; Point detail; Logging a weight for a past day, on the chart); `WeightChartTest`, `TrendPillTest`, `MetricTileTest` (Robolectric: legend, pill text and colour state, empty message, container colour, `stateDescription`); restoration tests.

**Verification:**
- [ ] Lint (incl. `check-design-tokens.sh`) + `testLocalDebugUnitTest` green.

**Dependencies:** T-079
**Files likely touched:** `shared/…/domain/usecase/{DailyWeights,MovingAverage,WeeklyRate,WeightChartRange}.kt` + tests, `shared/…/domain/repository/{BodyMeasurementRepository,WeightPreferences}.kt`, `shared/…/data/repository/*`, `shared/…/data/local/*` (DataStore), DI, `composeApp/…/feature/weight/{WeightChart,WeightTrendViewModel,WeightUiState,WeightScreen}.kt`, `composeApp/…/designsystem/{AppLineChart,MetricTile,TrendPill}.kt` (+ callers), catalog, strings, tests
**Estimated scope:** M–L

---

### Checkpoint E5-B — Health Connect, weight trend, goal, metrics and final regression 👤
- [ ] T-079…T-081 merged, CI green, `deploy-supabase` applied every migration, every reported bug fixed.
- [ ] 👤 Install the APK (`! python scripts/deliver-apk.py`) and try:
  - Manual weight and circumferences (from T-076…T-078 and T-083): Perfil → "Peso": "Registrar peso de hoy" (day only, no hour; weight and fat fields aligned); 19,9 kg, 401 kg and 80 % fat disable "Guardar"; a second "Registrar peso de hoy" edits today's weight; the calendar marks days, sets yesterday's weight and edits or deletes a day (gone at once). "Circunferencias" → "Añadir medida": fill waist 84,5 and hip 98,0 at once → ratio "0,86"; the "?" help; edit and delete one; 19,9 and 251 cm disabled.
  - Weight screen → "Sincronizar" for the first time: the Health Connect permission screen (read and write) opens; grant it: the last 90 days of weigh-ins appear on their days in the calendar with the Health Connect icon, and the banner reads "Sincronizado ahora".
  - Weigh yourself, wait until the reading reaches Health Connect, then "Sincronizar": only the new weigh-in arrives, with its fat and the extra composition values in one entry; tap again: nothing new.
  - Set a weight and fat by hand: see them in Health Connect; edit it in the app and Health Connect shows the new value; delete it and it is gone there too. Revoke the write permission and log again: saved in the app, nothing written, no error.
  - Revoke all permissions: "Permiso necesario" with the shortcut. Wait a few minutes: "Sincronizado hace N min". Delete an imported weigh-in and sync again: it does not come back.
  - The chart shows the daily dots and the cyan "MA 7D" line over the imported history; long-press a point: its date, weight and that day's average. Switch 1S, 1M, 3M, 6M and 1A; close the app and reopen it: the last filter is kept.
  - The weekly pill: its sign and colour match your strategy (Déficit: green while the average goes down). Set yesterday's weight from the calendar: the line and the pill update.
  - "Fijar objetivo": enter your target weight; the goal card shows "faltan X kg" and the progress since your first weight in the app; set a target on the other side of your weight (e.g. above it while in Déficit): "objetivo alcanzado"; change it and see the card follow.
  - "IMC" with its status; open the BMI, body fat and waist-to-hip ratio details: value, status, a short explanation and the disclaimer "Este producto no es un dispositivo médico…". The extra values from your scale (lean mass, water, bone, BMR) are visible.
  - In the weight calendar, open an older day's measurement → "Comparar": the weight and fat differences with their sign.
  - Final regression over a few real days: weigh yourself and "Sincronizar"; set a weight from the calendar and check Health Connect follows its edit and delete; log a waist and a hip (the ratio and its interpretation update); the chart, the weekly pill, the goal distance and the BMI move; the weight in Preferencias matches the weight screen.
  - Leave the app on the permission screen, mid-sync, with the target sheet half filled, a comparison open or a non-default chart filter: the same tab, screen and draft are restored.
  - Approval → `v0.5.0` and the retrospective (T-082).
  - Out of scope: a target date or estimated time to reach it (not in Phase 1).

---

## T-082: E5 closure

**Story:** E5 Definition of Done · **Flow:** PM

**Description:** Closes E5: final regression on the phone with the real scale, approval, tag `v0.5.0` and the retrospective.

**Acceptance criteria:**
- [ ] T-076…T-081 (and any fix tasks) merged with green CI; pgTAP green for `body_measurements` (incl. `external_id` uniqueness and the manual-row-per-day RPC), `body_circumferences`, `health_sync_state` and `profiles.target_weight_kg`; `deploy-supabase` green after the last migration.
- [ ] Host tests green for every E5 Gherkin scenario; the weekly rate, BMI, target distance and waist-to-hip ratio match the `04-business-rules.md` §3 examples.
- [ ] Health Connect read and write manual test documented in the T-079 PR.
- [ ] Checkpoint E5-B approved; every bug the user reported fixed; look-and-feel feedback appended to the E8 UI-refinement backlog (`planning/epics/E8-finalization.md`).
- [ ] `tasks/todo.md` (E5 ✅, next-task pointer) updated through a PR.

**Verification:**
- [ ] `gh run list` shows the last `deploy-supabase` green; `git tag` lists `v0.5.0` after approval.

**Dependencies:** T-081 (Checkpoint E5-B approved)
**Files likely touched:** `CLAUDE.md`, `tasks/todo.md`, `tasks/plan.md`
**Estimated scope:** S

---

## Notes for later epics

- **E3 (food diary), from E2 (T-037):** consumed by T-056 (the Diary reads gram targets from `CalculateMacroTargets`).
- **E3 / E4 / E5 FAB question (E1 review):** resolved by ADR-36 (no universal FAB); E3's per-screen actions are in T-043 and T-045.
- **Tablet (later epic, user feedback 2026-10-13):** the FAB at the top of the NavigationRail feels awkward; move the primary action into each section's content on wide screens. Non-blocking; the rail, rotation and offline banner were approved. Listed in the E7 UI refinement backlog (`planning/07-roadmap.md` §1.1).
- **E7 (UI refinement), from Checkpoint E2-A (user, 2026-10-14):** general onboarding polish, better use of space on "Sexo", a typography review. Every later checkpoint appends deferred UI details to `planning/07-roadmap.md` §1.1.
- **E2 (food diary), from the PR #24 review:** consumed by T-056 (`Double`/percent inputs for `MacroProgressBar` and `ProgressRing`).
- **E4 (weight and progress), from the PR #24 review:** moved to T-080 (`MetricTile`/`TrendPill` container-colour parameter).
