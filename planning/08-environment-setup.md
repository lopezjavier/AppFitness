# Environment and prerequisites

Supersedes the original local development notes (kept outside the repository). Steps marked with 👤 must be done by the user (accounts, credentials, web consoles); everything else can be done by Claude Code.

## 1. Local tools (Windows 11)

| Tool | Purpose | Check |
|---|---|---|
| JDK 21 (Temurin; JVM 17 bytecode, ADR-18) | Gradle / Android | `java -version` |
| Android Studio + SDK + an emulator (API 34+, with Google Play) | Build and emulate; Health Connect and Code Scanner need Google Play Services | `adb devices` |
| Docker Desktop | Local Supabase | `docker info` |
| Node.js LTS | `npx supabase` | `npx supabase --version` |
| Deno 2.9.7 (same as `supabase-ci`) | Edge Function tests | `deno --version` |
| Git + authenticated GitHub CLI | Version control and PRs | `gh auth status` |
| Maestro CLI | E2E tests | `maestro --version` |

## 2. Accounts and external configuration 👤

### 2.1 GitHub
1. **Public** repository `https://github.com/lopezjavier/AppFitness` (ADR-13). ✅ created.
2. Secret scanning, push protection and Dependabot enabled; `gitleaks` check (T-007).
3. Ruleset on `main` (T-007): PR required (0 approvals), required checks (`gitleaks`; `android-ci` from US-01; `supabase-ci` from US-02), linear history, no force-push or deletion. Squash merge only.
4. `production` environment, deployable only from `main` (T-009).
5. Secrets. The user enters them in their own terminal with `gh secret set <NAME> [--env production]`, never in the chat:

| Secret | Scope | Source | Used by |
|---|---|---|---|
| `SUPABASE_ACCESS_TOKEN` | env `production` | Supabase → Account → Access Tokens: `github-actions-appfitness`, 1 year, scoped to the project; Read: Project Settings, Connection Pooling, API Keys, **API Key Secrets** (needed by `supabase link`); Read-write: Migrations, Edge Functions; everything else None. Tokens cannot be edited: to change permissions, create a new one and revoke the old one | `deploy-supabase` |
| `SUPABASE_PROJECT_ID` | env `production` | Project ref (Project Settings) | `deploy-supabase` |
| `SUPABASE_DB_PASSWORD` | env `production` | DB password chosen when creating the project | `deploy-supabase` |
| `SUPABASE_URL` | repository | Project Settings → API | `android-ci`, `keepalive` |
| `SUPABASE_PUBLISHABLE_KEY` | repository | Project Settings → API Keys → Publishable key (`sb_publishable_...`) | `android-ci`, `keepalive` |
| `GOOGLE_WEB_CLIENT_ID` | repository | Google Cloud (2.3) | `android-ci` |

### 2.2 Supabase (Free plan)
1. Create the project (Free plan, EU region). ✅ Created as `AppFitness` in `eu-west-3` (Paris; T-014 replaced a first project in `eu-north-1`); the local CLI `project_id` is `appfitness`.
2. Auth → Providers: enable **Google** (Client ID and Secret of the web client from 2.3). **Disable Email** (ADR-02).
3. Edge Functions → Secrets: `OFF_USER_AGENT` (e.g. `AppFitness/1.0 (<contact email>)`).

### 2.3 Google Cloud Console
No billing account or free trial is needed: OAuth and Sign in with Google are free. Do not activate the free trial.
1. Create the project and the OAuth consent screen ("External", *Testing* mode, adding the accounts that will use the app as test users).
2. Create a **Web OAuth client ID** (`appfitness-web`) → its Client ID is `GOOGLE_WEB_CLIENT_ID` (used as `serverClientId` in Credential Manager and in Supabase). Add the Supabase callback as a redirect URI.
3. Create two **Android OAuth client IDs** (ADR-14): `appfitness-android` (`io.github.lopezjavier.appfitness`) and `appfitness-android-local` (`io.github.lopezjavier.appfitness.local`), both with the debug keystore SHA-1 (`keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`). In US-25 the release keystore SHA-1 is added to the `io.github.lopezjavier.appfitness` client.

## 3. Local development

### 3.1 App configuration
`local.properties` (never committed; template in `local.properties.example`):
```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
supabase.local.url=http://10.0.2.2:54321
supabase.local.publishableKey=<publishable key printed by `npx supabase start`>
supabase.prod.url=https://<ref>.supabase.co
supabase.prod.publishableKey=<production publishable key (sb_publishable_...)>
google.webClientId=<GOOGLE_WEB_CLIENT_ID>
```
In a `.properties` file the backslash is an escape character: keep `sdk.dir` with escaped backslashes as above, or use forward slashes (`sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`).

How `androidApp/build.gradle.kts` resolves the `BuildConfig` fields (details in `02-architecture.md` §5): `local.properties` (non-blank) > environment variable (non-blank) > default, and a missing value never fails the build. The `SUPABASE_URL`/`SUPABASE_PUBLISHABLE_KEY` environment variables only feed `prod` (CI exports the production secrets and also runs the `local` unit tests); `GOOGLE_WEB_CLIENT_ID` feeds both flavors. Defaults are `""`, except the `local` `SUPABASE_URL`, which defaults to `http://10.0.2.2:54321`, so the `local` build and its tests work without `local.properties` (CI, Dependabot). The resolved values are never printed.

The emulator reaches the PC's Docker Supabase through `10.0.2.2`. A physical phone with the `local` flavor would need the PC's LAN IP (not planned: the phone uses `prod`).

### 3.2 Local Supabase
The Supabase CLI is pinned in `package.json` (`supabase` 2.120.0); run `npm install` once after cloning so `npx supabase` uses that version. Local variables go in `supabase/.env` (git-ignored; template in `supabase/.env.example`).
```bash
npx supabase start                    # starts Postgres, Auth, PostgREST, Studio, Functions
npx supabase migration new <name>     # creates supabase/migrations/<timestamp>_<name>.sql
npx supabase db reset                 # applies migrations + seed.sql locally
npx supabase db lint --level warning --fail-on warning   # same gate as supabase-ci
npx supabase test db                  # pgTAP
npx supabase functions serve --env-file supabase/.env
deno fmt --check supabase/functions && deno lint supabase/functions   # same gates as supabase-ci
deno check supabase/functions/health/index.ts                          # type-check each entrypoint
deno test --allow-all supabase/functions                               # Edge Function unit tests
npx supabase stop
```
- `supabase/.env.local` (git-ignored): `OFF_USER_AGENT=...`.
- The root `deno.json` only configures Deno tooling for `supabase/functions/` (fmt/lint/test scope, no lockfile, no `node_modules` resolution: the root `package.json` only pins the Supabase CLI). Functions import external code with exact inline specifiers (`npm:postgres@3.4.9`, `jsr:@std/assert@1.0.19`), bumped manually.
- Locally the Email provider is enabled and `supabase/seed.sql` creates the test user used by the `local` flavor's "Test login" button and by Maestro:
  - email `test@appfitness.local`, password `appfitness-local-test` (local-only, not a secret), fixed id `00000000-0000-4000-8000-000000000001`;
  - its `profiles` row comes from the `on_auth_user_created` trigger (display name "Usuario de prueba"); `supabase/tests/seed.test.sql` checks all of this.
  - `seed.sql` is never applied in production: `deploy-supabase` runs `supabase db push` without `--include-seed`.
- Google Sign-In locally: configure `[auth.external.google]` in `supabase/config.toml` with `env(...)` for manual testing; Maestro always uses "Test login".

### 3.3 App
```bash
./gradlew :androidApp:installLocalDebug     # emulator + local Supabase ("AppFitness Local")
./gradlew testLocalDebugUnitTest            # unit tests of every module
./gradlew :androidApp:assembleProdDebug     # delivery APK: androidApp/build/outputs/apk/prod/debug/
maestro test --test-output-dir tests/e2e/screenshots tests/e2e/   # E2E regression; screenshots in tests/e2e/screenshots/ (gitignored)
```

## 4. Deployment

- **Backend:** automatic when changes in `supabase/**` are merged into `main` (`.github/workflows/deploy-supabase.yml`, `production` environment, deployable only from `main`): `supabase link` → `supabase db push` → `supabase functions deploy` (only if some `supabase/functions/*/index.ts` exists). The CLI version comes from `package-lock.json` (the same one `npx supabase` uses locally); the job fails if it differs from `package.json`.
  - Re-run manually: `gh workflow run deploy-supabase.yml` and follow it with `gh run watch` (or `gh run list --workflow deploy-supabase.yml`).
  - Result: the run log in GitHub Actions, and the deployment history under *Environments → production* on the repo page.
- **App:** `prodDebug` APK at the end of each epic (manual install). In US-25, release signing is added with a keystore stored outside the repo (and optionally as a base64 secret for a tag-triggered release workflow).
- **Keep-alive:** `keepalive.yml` calls `https://<ref>.supabase.co/functions/v1/health` (GET, publishable key as `apikey`; the function runs a `select 1`) every 3 days at 06:00 UTC (cron `0 6 */3 * *`, ADR-12). The log only prints `health OK`, never the URL or key.
  - Run manually: `gh workflow run keepalive.yml` and follow it with `gh run watch` (or `gh run list --workflow keepalive.yml`).
  - GitHub disables scheduled workflows after 60 days without repository activity: re-enable with `gh workflow enable keepalive.yml`.

## 5. Free-tier limits to watch

| Service | Relevant limit | Impact |
|---|---|---|
| Supabase Free | 500 MB DB, 500K Edge Function invocations/month, paused after 7 days without activity | Plenty for 1–5 users; keep-alive covers the pause |
| GitHub Actions (public repo) | Unlimited minutes on standard runners | Keep CI fast anyway (Gradle cache; `supabase-ci` only when `supabase/**` changes) |
| Open Food Facts | ~10 searches/min, ~100 product reads/min | Cache in `foods` + debounce in the app |
