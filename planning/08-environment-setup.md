# Environment and prerequisites

Steps marked with 👤 are done by the user (accounts, credentials, consoles). Full original text: [`phase1/08-environment-setup.md`](phase1/08-environment-setup.md).

## 1. Local tools (Windows 11)
| Tool | Purpose | Check |
|---|---|---|
| JDK 21 (Temurin; JVM 17 bytecode, ADR-18) | Gradle / Android | `java -version` |
| Android Studio + SDK + emulator (API 34+, Google Play) | Build/emulate; Health Connect and Code Scanner need Play Services | `adb devices` |
| Docker Desktop | Local Supabase | `docker info` |
| Node.js LTS | `npx supabase` (CLI pinned in `package.json`) | `npx supabase --version` |
| Deno (same version as `supabase-ci`) | Edge Function tests | `deno --version` |
| Git + authenticated GitHub CLI | Version control and PRs | `gh auth status` |

## 2. Accounts and external configuration 👤
### 2.1 GitHub
Public repo `https://github.com/lopezjavier/AppFitness` (ADR-13); secret scanning, push protection, Dependabot, `gitleaks`; ruleset on `main` (PR required, required checks `gitleaks`, `android-ci`, `supabase-ci`, linear history, no force-push, squash only); `production` environment deployable only from `main`. The user sets secrets in their own terminal with `gh secret set <NAME> [--env production]`, never in chat:

| Secret | Scope | Used by |
|---|---|---|
| `SUPABASE_ACCESS_TOKEN` | env `production` (scoped token; to change permissions create a new one and revoke the old) | `deploy-supabase` |
| `SUPABASE_PROJECT_ID`, `SUPABASE_DB_PASSWORD` | env `production` | `deploy-supabase` |
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | repository | `android-ci`, `keepalive` |
| `GOOGLE_WEB_CLIENT_ID` | repository | `android-ci` |

### 2.2 Supabase (Free plan)
Project `AppFitness` in `eu-west-3` (local CLI `project_id` `appfitness`). Auth: Google provider enabled, Email disabled (ADR-02). Edge Functions secrets (set with `npx supabase secrets set`, never in the repo or GitHub): `OFF_USER_AGENT`, `GEMINI_API_KEY` (free tier, `food-photo` only, ADR-59), `PROTECTED_EMAILS` (ADR-57), `ANTHROPIC_API_KEY` (`coach` only; paid, capped by the app budget and the Anthropic account limit; set with `npx supabase secrets set ANTHROPIC_API_KEY=...` by the user, never in the repo; locally in `supabase/.env.local`, template `supabase/.env.example`; the key expires after 2 years, about 2028-10-29: rotate it before then). Without it `coach` answers `NOT_CONFIGURED`.
### 2.3 Google Cloud Console
No billing or free trial. OAuth consent screen "External"/Testing with the app's users as test users; Web client `appfitness-web` (its Client ID is `GOOGLE_WEB_CLIENT_ID`; Supabase callback as redirect URI); two Android clients (ADR-14) `appfitness-android` and `appfitness-android-local` with the debug keystore SHA-1. No release SHA-1 (ADR-67).

## 3. Local development
### 3.1 App configuration
`local.properties` (never committed; template `local.properties.example`) holds `sdk.dir` (escape backslashes or use `/`), `supabase.local.url`, `supabase.local.publishableKey`, `supabase.prod.url`, `supabase.prod.publishableKey`, `google.webClientId`, optional `repdb.source`. Resolution order and flavors: `02-architecture.md` §5. The emulator reaches Docker Supabase at `10.0.2.2`; the phone uses `prod`.
### 3.2 Local Supabase
Run `npm install` once so `npx supabase` uses the pinned CLI; local variables in `supabase/.env` / `supabase/.env.local` (git-ignored; `supabase/.env.example` lists names only).
```bash
npx supabase start | stop
npx supabase migration new <name>
npx supabase db reset                                      # migrations + seed.sql
npx supabase db lint --level warning --fail-on warning     # same gate as supabase-ci
npx supabase test db                                       # pgTAP
npx supabase functions serve --env-file supabase/.env.local
deno fmt --check supabase/functions && deno lint supabase/functions && deno test --allow-all supabase/functions
```
`seed.sql` (local only, never `--include-seed` in production) creates the test user used by the `local` flavor's "Test login": `test@appfitness.local` / `appfitness-local-test` (local-only, not a secret), fixed id `00000000-0000-4000-8000-000000000001`, already onboarded (`supabase/tests/seed.test.sql` checks it). Locally the Email provider is enabled.
### 3.3 App
`./gradlew :androidApp:installLocalDebug` (emulator + local Supabase) · `./gradlew testLocalDebugUnitTest` · `./gradlew :androidApp:assembleProdDebug` (APK in `androidApp/build/outputs/apk/prod/debug/`). On Android 17 (API 37) the `local` build needs `ACCESS_LOCAL_NETWORK` granted after every install to reach `10.0.2.2` (`adb shell pm grant io.github.lopezjavier.appfitness.local android.permission.ACCESS_LOCAL_NETWORK`).
### 3.4 Exercise bundle (RepDB, gitignored; ADR-38/39)
```bash
python scripts/import-repdb.py <path to Biblioteca_Ejercicios_Free>     # library.json + images, idempotent
python scripts/generate-exercise-catalog.py <bundle> --out supabase/migrations/<ts>_exercise_catalog.sql   # only when the catalogue changes
```
CI and unit tests build without the bundle. `scripts/deliver-apk.py` imports it when missing (source from `--repdb-source`, else `repdb.source` in `local.properties`, else `REPDB_SOURCE`; `--reimport` forces it).

## 4. Deployment
- **Backend:** automatic on merges touching `supabase/**` (`deploy-supabase.yml`, `production` environment): `supabase link` -> `db push` -> `functions deploy`. Re-run: `gh workflow run deploy-supabase.yml`, follow with `gh run watch`.
- **App:** `prodDebug` APK at the end of each epic, installed manually; no release signing (ADR-67).
- **Keep-alive:** `keepalive.yml` calls the `health` function every 3 days at 06:00 UTC (ADR-12); the log never prints the URL or key. GitHub disables scheduled workflows after 60 days without repo activity: re-enable with `gh workflow enable keepalive.yml`.

## 5. Free-tier limits to watch
Supabase Free (500 MB DB, 500K function invocations/month, pauses after 7 idle days; keepalive covers it) · GitHub Actions on a public repo (unlimited minutes; revisit if the repo goes private, ADR-67) · Open Food Facts (~10 searches/min; cached in `foods`) · Gemini free tier (model-dependent caps; `429` surfaces as "Reintentar", no billing, ADR-59).

## 6. Hardening and accepted risks
- Exposed API schemas: `public` only (set in the hosted dashboard too, 👤). Email sign-up disabled (`[auth.email] enable_signup = false`, password minimum 8; top-level `[auth] enable_signup` stays true for Google); disable email sign-ups in the hosted dashboard as well.
- Accepted risks (owner decision 2026-10-23, only the owner uses the app): no per-user rate limit on `food-search` or `food-photo` (provider quota is the cap, photos never stored); no privacy policy or health-data note until distribution beyond the owner.
