# Phase 1 architecture

## 1. Overview

```
┌──────────────────────────── Android app (KMP) ────────────────────────────┐
│ androidApp (Android application, ADR-19)                                   │
│   MainActivity, Application (Koin), local/prod flavors, BuildConfig,       │
│   network security config (local)                                         │
│ composeApp (KMP library)                                                   │
│   commonMain: Compose UI (screens, ViewModels, navigation, designsystem)   │
│   androidMain: Credential Manager, Health Connect,                         │
│                Code Scanner, haptics, keepScreenOn                         │
│ shared (KMP library)                                                       │
│   commonMain: domain (models, use cases, business rules)                   │
│               data (repositories on top of supabase-kt)                    │
└───────────────┬────────────────────────────────────────────────────────────┘
                │ HTTPS (Supabase Auth JWT)
┌───────────────▼──────────────── Supabase (Free) ──────────────────────────┐
│ Auth (Google)   Postgres + RLS (PostgREST)   Edge Functions (Deno/TS)      │
│                                              food-search · food-barcode ·  │
│                                              health                        │
└───────────────────────────────────────────────┬────────────────────────────┘
                                                │ HTTPS + custom User-Agent
                                       Open Food Facts API
```

## 2. Repository layout

```
/androidApp         ← Android application module (ADR-19); depends on composeApp and shared
  src/main/kotlin/<pkg>/
    MainActivity.kt, AppFitnessApplication.kt (starts Koin)
    di/             ← appModule: AppConfig built from BuildConfig
  src/main/         ← AndroidManifest.xml, res/ (app_name)
  src/local/        ← local flavor: network security config (cleartext to 10.0.2.2 only), app_name
  src/prod/         ← prod flavor overrides (created when needed)
  src/testLocal/    ← local flavor unit tests (BuildConfig, Koin graph)
  src/testProd/     ← prod flavor unit tests
/composeApp         ← KMP library (com.android.kotlin.multiplatform.library + Compose Multiplatform)
  src/commonMain/kotlin/<pkg>/
    app/            ← App(), root navigation, adaptive scaffold
    designsystem/   ← tokens, theme, typography, reusable components
    feature/
      auth/ onboarding/ profile/ diary/ food/ templates/
      weight/ workout/ routines/ exercises/ analytics/
        (each feature: <Feature>Screen.kt, <Feature>ViewModel.kt, <Feature>UiState.kt)
    di/             ← UI Koin modules
  src/androidMain/kotlin/<pkg>/
    platform/       ← GoogleAuthCredentialProvider, HealthConnectDataSource,
                      MlKitBarcodeScanner, AndroidHaptics
  src/commonTest/   ← ViewModel tests with fakes + Turbine
/shared             ← KMP library (com.android.kotlin.multiplatform.library)
  src/commonMain/kotlin/<pkg>/
    config/         ← AppConfig (per-environment values, provided by androidApp via Koin)
    domain/
      model/        ← Profile, NutritionGoals, Food, FoodLogEntry, MealType, BodyMeasurement,
                      Exercise, Routine, WorkoutSession, WorkoutSet…
      usecase/      ← CalculateTdee, BalanceMacros, MovingAverage, WeeklyRate,
                      EstimateOneRepMax, CalculateVolume…
      port/         ← interfaces: AuthCredentialProvider, HealthDataSource, BarcodeScanner,
                      Haptics, Clock, ConnectivityObserver
    data/
      remote/       ← @Serializable DTOs + Supabase client
      repository/   ← implementations of the domain repositories
    di/
  src/commonTest/   ← business rule and repository tests (fakes)
/supabase
  config.toml
  migrations/       ← versioned SQL
  tests/            ← pgTAP (*.test.sql)
  functions/        ← food-search, food-barcode, health, _shared/
  seed.sql          ← test user (local only); the exercise catalog is a migration (ADR-39)
/.github/workflows  ← android-ci, supabase-ci, deploy-supabase, keepalive, security
/design/stitch      ← Stitch mockups (visual reference)
/gradle/libs.versions.toml
```

## 3. Layers and dependency rules

- **UI (composeApp)** → depends on `shared.domain` (use cases / repository interfaces). Never uses DTOs or the Supabase client directly.
- **Domain (shared/domain)** → pure Kotlin, no framework dependencies. The rules of `04-business-rules.md` live here, 100% covered by unit tests.
- **Data (shared/data)** → implements repositories; maps DTO ↔ model; translates errors into a `sealed interface AppError` (Network, Unauthorized, NotFound, Validation, Unknown).
- **Platform (androidMain)** → implements the domain `port/` interfaces. Injected with Koin.
- **UI pattern:** MVVM/UDF. Each ViewModel exposes `StateFlow<XUiState>` and receives events (`onEvent(XEvent)`). One-off effects (navigation, haptics, snackbars) via `Channel`/`SharedFlow`.
- **Screen states:** `Loading` (skeleton), `Content`, `Empty`, `Error(retry)`, plus an overlaid `Offline` state (ADR-01).
- **testTag:** every interactive component has `Modifier.testTag("<screen>_<element>")` in snake_case (e.g. `diary_fab`, `workout_set_2_done`).
- **Code language:** identifiers, comments and logs in English; user-facing text only in string resources (Spanish).
- **State restoration (ADR-40):** the tab, back stack, scroll and unsaved drafts survive background and process death. Navigation state lives above the root route branch; screen state uses `rememberSaveable` or a ViewModel `SavedStateHandle`; every UI task with such state ships a `StateRestorationTester` host test and a recreated-ViewModel test.

## 4. Libraries (versions pinned in `libs.versions.toml` in US-01, latest stable)

| Area | Library |
|---|---|
| Language / build | Kotlin 2.x, Compose Multiplatform, Android Gradle Plugin 9 (built-in Kotlin; Android-KMP library plugin, ADR-19), JDK 21 running the build, JVM 17 bytecode (ADR-18), `minSdk` 26 (ADR-21) |
| Backend client | supabase-kt (`auth-kt`, `postgrest-kt`, `functions-kt`) + Ktor (OkHttp engine on Android) |
| Serialization / dates | kotlinx-serialization, kotlinx-datetime, kotlinx-coroutines |
| DI | Koin (core + compose + viewmodel) |
| Navigation | Multiplatform Navigation Compose (type-safe routes with `@Serializable`) |
| Adaptive | In-app `WindowWidthClass` (600 dp = the M3 WindowSizeClass Medium breakpoint) picks bottom bar vs. `NavigationRail` (material3); no `material3-adaptive` / `navigation-suite` dependency |
| Charts | Vico (multiplatform) |
| Images | Coil 3 (Google avatar) |
| Android auth | androidx.credentials + `googleid` |
| Scanner | `com.google.android.gms:play-services-code-scanner` |
| Health | `androidx.health.connect:connect-client` |
| Local preferences | DataStore (multiplatform) |
| Tests | kotlin-test, kotlinx-coroutines-test, Turbine, MockK (Android host tests only: `androidHostTest` in the KMP modules, `src/test*` in `androidApp`; ADR-19), Robolectric + Compose UI test (`androidHostTest`: semantics and touch-target checks; needs `--add-opens` JVM args, set in `composeApp/build.gradle.kts`) |
| Quality | ktlint (Gradle plugin), detekt + Compose rules (io.nlopez.compose.rules); shared config in .editorconfig and config/detekt/detekt.yml, no baseline |

## 5. Per-environment configuration (flavors)

| Flavor | Supabase | Login | Use |
|---|---|---|---|
| `local` | `http://10.0.2.2:54321` (Docker Supabase as seen from the emulator) | Google + "Test login" button (email/password, seeded user) | Development |
| `prod` | Cloud Supabase project | Google only | APK delivered to the user |

- Flavors and `BuildConfig` live only in `androidApp` (ADR-19): the KMP library modules have a single variant. `minSdk` 26 (ADR-21); `compileSdk`/`targetSdk` 37.
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` and `GOOGLE_WEB_CLIENT_ID` are `BuildConfig` fields resolved at build time with this precedence: `local.properties` (non-blank) > environment variable / CI secret (non-blank) > default. A missing value never fails the build:

  | Field | `local` | `prod` |
  |---|---|---|
  | `SUPABASE_URL` | `supabase.local.url` → `http://10.0.2.2:54321` | `supabase.prod.url` → env `SUPABASE_URL` → `""` |
  | `SUPABASE_PUBLISHABLE_KEY` | `supabase.local.publishableKey` → `""` | `supabase.prod.publishableKey` → env `SUPABASE_PUBLISHABLE_KEY` → `""` |
  | `GOOGLE_WEB_CLIENT_ID` | `google.webClientId` → env `GOOGLE_WEB_CLIENT_ID` → `""` | same |

  The `SUPABASE_*` environment variables feed `prod` only: CI exports the production secrets and also runs the `local` unit tests. `local.properties` is never committed.
- `androidApp` builds an `AppConfig` (`shared`) from `BuildConfig` and provides it with Koin; `composeApp` and `shared` never read `BuildConfig`.
- Cleartext HTTP is only allowed in the `local` flavor, through a network security config restricted to `10.0.2.2` (no `android:usesCleartextTraffic`). `prod` uses the platform default (cleartext blocked).

## 6. Backend (Supabase)

- **Migrations:** SQL in `supabase/migrations/`, at most one per story. A merged migration is never edited; a new one is created.
- **pgTAP tests** (`supabase test db`): for every table, RLS is enabled and user A cannot read/write user B's data.
- **Edge Functions** (Deno/TypeScript) with `verify_jwt = true` except `health`. Tested with `deno test`. Details in `03-data-model.md`.
- **Seed** (`seed.sql`): the `test@appfitness.local` user (local only; never applied in production). The exercise catalog rows are a generated migration, so they reach every database (ADR-39).
- **Exercise assets** (gitignored RepDB bundle): `composeApp/src/commonMain/composeResources/files/exercises/`, written by `scripts/import-repdb.py`; `scripts/generate-exercise-catalog.py` produces the catalog migration (ADR-39).

## 7. Security

- RLS enabled and forced on **every** table in the `public` schema; policies use `(select auth.uid())`.
- The app only contains the Supabase **publishable key** (`sb_publishable_...`); the legacy anon key is not used. The **secret key** (`sb_secret_...`, legacy `service_role`) is only used server-side by Edge Functions (ADR-17).
- Default privileges of the `postgres` role in `public` grant nothing to `anon` (nor to PUBLIC for functions; the PUBLIC revoke is global, so postgres-created functions outside `public` also need an explicit grant); every new object needs an explicit grant to `authenticated`/`service_role`.
- Edge Functions validate the JWT and the input (search length, EAN/UPC format) and cap results.
- No secrets in the repository: `.gitignore` for `local.properties`, `.env*`, `supabase/.env*`, keystores. GitHub secret scanning + push protection + gitleaks.
- Dependabot for Gradle, npm/Deno and GitHub Actions.
- Ruleset on `main`: PR required, required checks, no force-push.
- R8/ProGuard enabled in release with rules for kotlinx-serialization (US-46).

## 8. CI/CD (GitHub Actions)

| Workflow | Trigger | Steps | Required to merge |
|---|---|---|---|
| `security.yml` | PR and push to `main` | gitleaks | Yes (from E0) |
| `android-ci.yml` | PR and push to `main` (build job skipped when only `supabase/**` or text-only paths changed; the check always reports, ADR-20) | Design tokens guard (`scripts/check-design-tokens.sh`) → JDK 21 (Temurin) + Gradle cache → `./gradlew ktlintCheck detekt` → `./gradlew testLocalDebugUnitTest` → `./gradlew :androidApp:testProdDebugUnitTest` → `./gradlew :androidApp:assembleProdDebug` (APK artifact) | Yes (from US-01) |
| `supabase-ci.yml` | PR and push to `main` (test job skipped when nothing under `supabase/**` changed; the check always reports, ADR-20) | setup-cli (pinned via `package-lock.json`) → `supabase start` → `supabase db reset` → `supabase db lint --level warning --fail-on warning` → `supabase test db`, preceded by Deno fmt/lint/check/test for functions | Yes (from US-02) |
| `deploy-supabase.yml` | push to `main` with changes in `supabase/**` + manual | `production` environment → `supabase link` → `supabase db push` → `supabase functions deploy` | — (from E0) |
| `keepalive.yml` | cron `0 6 */3 * *` + manual | `curl` GET to `health` with the publishable key as `apikey` (ADR-12, ADR-17); fails on non-200 | — (from US-02) |

Optional in E8: a release workflow that, when a `v*` tag is created, builds the `prod` APK and attaches it to a GitHub Release.

## 9. Network behavior

- Connectivity monitor (`ConnectivityManager` in androidMain, exposed as `Flow<Boolean>`).
- Offline: persistent banner below the Top Bar; write buttons disabled; reads show the last in-memory state if available.
- Transient errors (a failed write or sign-in) show a message only, with no action, when a primary action on screen already retries (the user taps it again); what the user typed is never lost (state kept in the ViewModel / `SavedStateHandle`). Full-screen error states (a failed load) keep "Reintentar" as their action.
