# Architecture (current state)

Compact live copy; detail and history in [`phase1/02-architecture.md`](phase1/02-architecture.md).

## 1. Overview

```
Android app (KMP)
  androidApp   Android application (ADR-19): MainActivity, Application (Koin), local/prod flavors,
               BuildConfig, network security config (local)
  composeApp   KMP library: commonMain = Compose UI (screens, ViewModels, navigation, designsystem);
               androidMain = Credential Manager, Health Connect, Code Scanner, camera, haptics, keepScreenOn
  shared       KMP library: commonMain = domain (models, use cases, ports) + data (repositories on supabase-kt)
        | HTTPS (Supabase Auth JWT)
Supabase (Free): Auth (Google) | Postgres + RLS (PostgREST) | Edge Functions (Deno/TS):
                 food-search, food-barcode, food-photo, delete-account, health
        | HTTPS + custom User-Agent        | Gemini (food-photo)
Open Food Facts API
```

## 2. Repository layout

```
/androidApp     application module; src/main, src/local (cleartext to 10.0.2.2 only), src/prod, src/testLocal, src/testProd
/composeApp     KMP library
  src/commonMain/kotlin/<pkg>/  app/ (App, root navigation, adaptive scaffold) · designsystem/ (tokens, theme, components)
                                · feature/ (auth onboarding profile diary food templates weight workout routines
                                  exercises analytics; each <Feature>Screen/ViewModel/UiState) · di/
  src/androidMain/kotlin/<pkg>/platform/   GoogleAuthCredentialProvider, HealthConnectDataSource, MlKitBarcodeScanner, AndroidHaptics
  src/androidHostTest/ (Robolectric + Compose UI tests, golden PNGs in snapshots/)   src/commonTest/
  src/commonMain/composeResources/files/exercises/   RepDB bundle (gitignored, ADR-39)
/shared         KMP library: config/ (AppConfig) · domain/{model,usecase,port} · data/{remote,repository} · di/
/supabase       config.toml · migrations/ (versioned SQL) · tests/ (pgTAP) · functions/ (+ _shared/) · seed.sql (local only)
/.github/workflows   android-ci, supabase-ci, deploy-supabase, keepalive, security
/scripts, /gradle/libs.versions.toml, /planning, /tasks
```

## 3. Layers and dependency rules

- **UI (composeApp)** depends on `shared.domain`; never uses DTOs or the Supabase client directly.
- **Domain** is pure Kotlin; the rules of `04-business-rules.md` live here, fully unit-tested.
- **Data** implements repositories, maps DTO <-> model, translates errors into `sealed interface AppError` (Network, Unauthorized, NotFound, Validation, Unknown).
- **Platform (androidMain)** implements the domain `port/` interfaces (AuthCredentialProvider, HealthDataSource, BarcodeScanner, Haptics, Clock, ConnectivityObserver), injected with Koin.
- **UI pattern:** MVVM/UDF. `StateFlow<XUiState>` + `onEvent(XEvent)`; one-off effects via `Channel`/`SharedFlow`. States: Loading (skeleton), Content, Empty, Error(retry), plus overlaid Offline (ADR-01).
- **testTag:** every interactive component has `Modifier.testTag("<screen>_<element>")` in snake_case.
- **Languages:** identifiers, comments, logs in English; user-facing text only in Spanish string resources.
- **State restoration (ADR-40):** tab, back stack, scroll and unsaved drafts survive background and process death. Navigation state lives above the root route branch; screen state uses `rememberSaveable` or a ViewModel `SavedStateHandle`; every UI task with such state ships a `StateRestorationTester` host test and a recreated-ViewModel test.

## 4. Libraries (pinned in `libs.versions.toml`, stable only)

Kotlin 2.x + Compose Multiplatform + AGP 9 (built-in Kotlin, Android-KMP library plugin; JDK 21 build, JVM 17 bytecode, `minSdk` 26; ADR-18/19/21) · supabase-kt (auth, postgrest, functions) + Ktor/OkHttp · kotlinx-serialization/datetime/coroutines · Koin · Navigation Compose (type-safe `@Serializable` routes) · in-app `WindowWidthClass` (600 dp) picks bottom bar vs `NavigationRail` · Vico charts · Coil 3 · androidx.credentials + `googleid` · `play-services-code-scanner` (live) and system camera via `TakePicture` + `FileProvider` · Health Connect `connect-client` · DataStore · tests: kotlin-test, coroutines-test, Turbine, MockK (Android host tests only), Robolectric + Compose UI test (`androidHostTest`; `--add-opens` set in `composeApp/build.gradle.kts`) · ktlint + detekt with Compose rules (`.editorconfig`, `config/detekt/detekt.yml`, no baseline).

## 5. Per-environment configuration (flavors)

| Flavor | Supabase | Login | Use |
|---|---|---|---|
| `local` | `http://10.0.2.2:54321` (Docker) | Google + "Test login" (seeded user) | Development |
| `prod` | Cloud project | Google only | APK delivered to the user |

- Flavors and `BuildConfig` live only in `androidApp`; the library modules have a single variant. `compileSdk`/`targetSdk` 37.
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_WEB_CLIENT_ID` are `BuildConfig` fields resolved as `local.properties` (non-blank) > environment variable / CI secret (non-blank) > default; a missing value never fails the build. The `SUPABASE_*` variables feed `prod` only. `local.properties` is never committed.
- `androidApp` builds `AppConfig` (`shared`) from `BuildConfig` and provides it with Koin; `composeApp` and `shared` never read `BuildConfig`.
- Cleartext HTTP only in `local`, via a network security config restricted to `10.0.2.2`.

## 6. Backend (Supabase)

- Migrations in `supabase/migrations/`, at most one per story; a merged migration is never edited.
- pgTAP (`supabase test db`): per table RLS enabled and user A cannot touch user B's data.
- Edge Functions (Deno/TS), `verify_jwt = true` except `health`; tested with `deno test` (`03-data-model.md` §7).
- AI photos (ADR-59): image from the system camera or `PickVisualMedia`, downscaled JPEG to `food-photo`, which calls Gemini with a fixed mode prompt (dish, label, barcode, weight) and returns a normalized draft. No key or SDK in the app, no storage, no logging of image/comment/provider body.
- `seed.sql` creates the local test user only (never in production). The exercise catalogue is a generated migration (ADR-39); assets are produced by `scripts/import-repdb.py` and `scripts/generate-exercise-catalog.py`.

## 7. Security

- RLS enabled and forced on every `public` table; policies use `(select auth.uid())`.
- The app carries only the publishable key (`sb_publishable_...`); the secret key lives only in Edge Functions (ADR-17). `GEMINI_API_KEY`, `PROTECTED_EMAILS` and `OFF_USER_AGENT` are Supabase secrets.
- Default privileges of `postgres` in `public` grant nothing to `anon`/PUBLIC; every new object needs an explicit grant to `authenticated`/`service_role`.
- Edge Functions validate the JWT and the input and cap results.
- No secrets in the repo; secret scanning + push protection + gitleaks; Dependabot (Gradle, npm/Deno, Actions); ruleset on `main` (PR, required checks, no force-push).
- No release build in Phase 1 (ADR-67): no R8 rules, no release keystore.

## 8. CI/CD (GitHub Actions)

| Workflow | Trigger | Steps | Required |
|---|---|---|---|
| `security.yml` | PR + push `main` | gitleaks, text-encoding guard | Yes |
| `android-ci.yml` | PR + push `main` (build skipped for supabase-only/text-only diffs; check always reports, ADR-20) | design-token guard, UX conventions guard (+ fixtures) -> JDK 21 -> `ktlintCheck detekt` -> `testLocalDebugUnitTest` -> `:androidApp:testProdDebugUnitTest` -> `:androidApp:assembleProdDebug` | Yes |
| `supabase-ci.yml` | PR + push `main` (skipped unless `supabase/**` changed) | Deno fmt/lint/check/test -> `supabase start` -> `db reset` -> `db lint --fail-on warning` -> `supabase test db` | Yes |
| `deploy-supabase.yml` | push `main` touching `supabase/**` + manual | `production` env -> `supabase link` -> `db push` -> `functions deploy` | - |
| `keepalive.yml` | cron `0 6 */3 * *` + manual | GET `health` with the publishable key | - |

## 9. Network behavior

- Connectivity monitor in androidMain exposed as `Flow<Boolean>`. Offline: persistent banner below the top bar, write buttons disabled, reads show the last in-memory state.
- A transient error (failed write/sign-in) shows a message only when a primary action on screen already retries; typed input is never lost. Full-screen load errors keep "Reintentar".
- AI photo uploads are one request to `food-photo`; no background retry, upload queue or offline capture. The live scanner keeps using `food-barcode`.

## 10. Rest alert outside the app (ADR-75, T-146)

- **Technique:** `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, endsAt)` (fallback `setAndAllowWhileIdle` when exact alarms are not allowed) -> `RestAlertReceiver` (`goAsync`) -> `RestAlertHandler` -> `RestAlertNotifier`. It survives screen off, Doze and process death; a reboot clears it (accepted). `setAlarmClock` was rejected (it shows an alarm icon in the system UI); a foreground service would need a persistent notification for the whole rest.
- **Permissions:** `USE_EXACT_ALARM` (API 33+, granted at install, no user action; its Play policy would need review if the app ever leaves sideloading), `SCHEDULE_EXACT_ALARM` with `maxSdkVersion=32`, and the Android 13+ `POST_NOTIFICATIONS` runtime prompt at the first rest of a session (bridge: `AndroidNotificationPermission` + the Activity's result launcher).
- **Android 17 finding (verified 2026-10-28 against https://developer.android.com/about/versions/17/changes/bg-audio, linked from /about/versions/17/behavior-changes-all):** background audio (playback, audio focus, volume) needs a visible activity or a non-`SHORT_SERVICE` foreground service, for all apps whatever their target SDK. Targeting 37 adds a while-in-use requirement that is waived only for apps with the exact-alarm permission playing `USAGE_ALARM`; the base requirement is not waived. An alarm receiver with no foreground service therefore gets silent failures. **Consequence:** the receiver posts the notification only; the sound and the vibration come from the notification channel, which the system plays. One high-importance channel per sound candidate (`rest_alert_<sound id>`), because a channel sound is frozen after creation; the notification uses the channel of the sound in `RestAlertPreferences`. Channel sounds follow the notification stream (muted by the ringer like any notification). In the foreground `AndroidAlertSound` plays the chosen sound itself (a visible activity is allowed). Other pages checked: https://developer.android.com/develop/background-work/services/alarms (`USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`) and https://developer.android.com/training/monitoring-device-state/doze-standby (`setExactAndAllowWhileIdle` fires in Doze).
- **Foreground check:** a resumed activity (`CurrentActivityHolder`), not `ProcessLifecycleOwner`; a locked screen pauses the activity, so the alert is posted.
- **Assets:** `rest_chime.wav` and `rest_double_beep.wav` are synthesised by `scripts/gen-rest-sounds.py` (own work, no third-party licence) into `composeApp/src/androidMain/res/raw/` and serve both the channel sound and the in-app alert.
- **T-148:** keeps the chosen sound in a new channel ID, deletes the three T-146 channels, the chooser (`RestSoundChooser*`, the About row, `rest_sound_*` strings) and the unused asset.
