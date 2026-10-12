# Phase 1 architecture

## 1. Overview

```
┌──────────────────────────── Android app (KMP) ────────────────────────────┐
│ composeApp                                                                 │
│   commonMain: Compose UI (screens, ViewModels, navigation, designsystem)   │
│   androidMain: MainActivity, Credential Manager, Health Connect,           │
│                Code Scanner, haptics, keepScreenOn                         │
│ shared                                                                     │
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
/composeApp
  src/commonMain/kotlin/<pkg>/
    app/            ← App(), root navigation, adaptive scaffold, contextual FAB
    designsystem/   ← tokens, theme, typography, reusable components
    feature/
      auth/ onboarding/ profile/ diary/ food/ templates/
      weight/ workout/ routines/ exercises/ analytics/
        (each feature: <Feature>Screen.kt, <Feature>ViewModel.kt, <Feature>UiState.kt)
    di/             ← UI Koin modules
  src/androidMain/kotlin/<pkg>/
    MainActivity.kt
    platform/       ← GoogleAuthCredentialProvider, HealthConnectDataSource,
                      MlKitBarcodeScanner, AndroidHaptics
  src/commonTest/   ← ViewModel tests with fakes + Turbine
/shared
  src/commonMain/kotlin/<pkg>/
    domain/
      model/        ← Profile, NutritionGoals, Food, FoodLogEntry, MealType, BodyMeasurement,
                      Exercise, Routine, WorkoutSession, WorkoutSet…
      usecase/      ← CalculateTdee, BalanceMacros, MovingAverage, WeeklyRate,
                      EstimateOneRepMax, CalculateVolume…
      port/         ← interfaces: AuthCredentialProvider, HealthDataSource, BarcodeScanner,
                      Haptics, Clock
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
  seed.sql          ← test user (local only) + exercise catalog
/tests/e2e          ← Maestro flows <task-id>.yaml + shared subflows (login.yaml)
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

## 4. Libraries (versions pinned in `libs.versions.toml` in US-01, latest stable)

| Area | Library |
|---|---|
| Language / build | Kotlin 2.x, Compose Multiplatform, Android Gradle Plugin, JDK 17 |
| Backend client | supabase-kt (`auth-kt`, `postgrest-kt`, `functions-kt`) + Ktor (OkHttp engine on Android) |
| Serialization / dates | kotlinx-serialization, kotlinx-datetime, kotlinx-coroutines |
| DI | Koin (core + compose + viewmodel) |
| Navigation | Multiplatform Navigation Compose (type-safe routes with `@Serializable`) |
| Adaptive | Material3 adaptive / navigation-suite (WindowSizeClass, NavigationRail) |
| Charts | Vico (multiplatform) |
| Images | Coil 3 (Google avatar) |
| Android auth | androidx.credentials + `googleid` |
| Scanner | `com.google.android.gms:play-services-code-scanner` |
| Health | `androidx.health.connect:connect-client` |
| Local preferences | DataStore (multiplatform) |
| Tests | kotlin-test, kotlinx-coroutines-test, Turbine, MockK (`androidUnitTest` only), Maestro (E2E) |
| Quality | ktlint (Gradle plugin), detekt |

## 5. Per-environment configuration (flavors)

| Flavor | Supabase | Login | Use |
|---|---|---|---|
| `local` | `http://10.0.2.2:54321` (Docker Supabase as seen from the emulator) | Google + "Test login" button (email/password, seeded user) | Development and Maestro |
| `prod` | Cloud Supabase project | Google only | APK delivered to the user |

- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` and `GOOGLE_WEB_CLIENT_ID` are injected as `BuildConfig` fields from `local.properties` (local) or environment variables / secrets (CI). `local.properties` is never committed.
- `android:usesCleartextTraffic` is only allowed in the `local` flavor (network security config restricted to `10.0.2.2`).

## 6. Backend (Supabase)

- **Migrations:** SQL in `supabase/migrations/`, at most one per story. A merged migration is never edited; a new one is created.
- **pgTAP tests** (`supabase test db`): for every table, RLS is enabled and user A cannot read/write user B's data.
- **Edge Functions** (Deno/TypeScript) with `verify_jwt = true` except `health`. Tested with `deno test`. Details in `03-data-model.md`.
- **Seed** (`seed.sql`): exercise catalog (all databases) and the `test@appfitness.local` user (local only; the user seed is never applied in production).

## 7. Security

- RLS enabled and forced on **every** table in the `public` schema; policies use `(select auth.uid())`.
- The app only contains the Supabase **publishable key** (`sb_publishable_...`); the legacy anon key is not used. The **secret key** (`sb_secret_...`, legacy `service_role`) is only used server-side by Edge Functions (ADR-17).
- Edge Functions validate the JWT and the input (search length, EAN/UPC format) and cap results.
- No secrets in the repository: `.gitignore` for `local.properties`, `.env*`, `supabase/.env*`, keystores. GitHub secret scanning + push protection + gitleaks.
- Dependabot for Gradle, npm/Deno and GitHub Actions.
- Ruleset on `main`: PR required, required checks, no force-push.
- R8/ProGuard enabled in release with rules for kotlinx-serialization (US-25).

## 8. CI/CD (GitHub Actions)

| Workflow | Trigger | Steps | Required to merge |
|---|---|---|---|
| `security.yml` | PR and push to `main` | gitleaks | Yes (from E0) |
| `android-ci.yml` | PR and push to `main` (changes outside `supabase/**`) | JDK 17 + Gradle cache → `./gradlew ktlintCheck detekt` → `./gradlew testLocalDebugUnitTest` → `./gradlew :composeApp:assembleProdDebug` (APK artifact) | Yes (from US-01) |
| `supabase-ci.yml` | PR with changes in `supabase/**` | setup-cli (pinned) → `supabase start` → `supabase db reset` → `supabase db lint` → `supabase test db` → `deno test` for functions | Yes, when applicable (from US-02) |
| `deploy-supabase.yml` | push to `main` with changes in `supabase/**` + manual | `production` environment → `supabase link` → `supabase db push` → `supabase functions deploy` | — (from E0) |
| `keepalive.yml` | cron every 3 days + manual | `curl` to the project's `health` function (ADR-12) | — (from US-02) |

Optional in E6: a release workflow that, when a `v*` tag is created, builds the `prod` APK and attaches it to a GitHub Release.

## 9. Network behavior

- Connectivity monitor (`ConnectivityManager` in androidMain, exposed as `Flow<Boolean>`).
- Offline: persistent banner below the Top Bar; write buttons disabled; reads show the last in-memory state if available.
- Network errors on a write: snackbar with "Reintentar" (retry); what the user typed is never lost (state kept in the ViewModel / `SavedStateHandle`).
