# E1 · Infrastructure and app skeleton → `v0.1.0`

## Goal
A KMP project building in CI, a baseline Supabase backend deployed automatically, the design system implemented and an installable APK with full navigation in a WIP state.

## Scope
- Includes: module structure, flavors, code quality tooling, GHA workflows, `profiles` migration, design system, navigation shell.
- Out of scope: login (E2), any business feature.

## Dependencies
E0 completed (including the Supabase project, secrets and `deploy-supabase.yml`).

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language).

---

## US-01 · Base KMP project with flavors, quality tooling and Android CI
**As** the development team
**I want** a buildable Kotlin Multiplatform project with flavors, linters and CI
**So that** every later change is validated automatically from the first PR.

```gherkin
Feature: Buildable KMP project validated in CI

  Scenario: Local build of both flavors
    Given a clean copy of the repository with local.properties configured
    When ./gradlew :composeApp:assembleLocalDebug :composeApp:assembleProdDebug is run
    Then both APKs are built without errors
    And each one contains its environment's Supabase URL in BuildConfig

  Scenario: Quality and tests on a Pull Request
    Given a PR opened against main
    When the android-ci workflow runs
    Then ktlintCheck, detekt, the unit tests and the prodDebug assembly are executed
    And the APK is kept as a workflow artifact
    And the PR cannot be merged if any step fails

  Scenario: Secrets kept out of the repository
    Given local.properties contains the Supabase keys
    When git status is run
    Then local.properties does not appear as a file to be versioned
```

**Technical notes**
- `composeApp` and `shared` modules per `02-architecture.md` §2; `gradle/libs.versions.toml` with pinned stable versions.
- `local` / `prod` flavors (`env` dimension); network security config with cleartext only for `10.0.2.2` in `local`.
- Koin initialized; empty `App()` showing a text; one sample unit test in `shared/commonTest`.
- `.github/workflows/android-ci.yml` with Gradle cache and `paths-ignore: supabase/**`.
- Add `android-ci` as a required check in the `main` ruleset and the `gradle` ecosystem to `dependabot.yml`.

---

## US-02 · Supabase baseline: profiles, RLS, health check, CI and keep-alive
**As** the development team
**I want** the Supabase backend versioned with migrations, security tests and a keep-alive
**So that** there is secure per-user persistence before building features.

```gherkin
Feature: Supabase backend baseline

  Scenario: Local migrations and seed
    Given local Supabase started with npx supabase start
    When npx supabase db reset is run
    Then the profiles table and the on_auth_user_created trigger exist
    And the test user test@appfitness.local exists with its profiles row

  Scenario: Profile isolation with RLS
    Given two users A and B with their profiles
    When user A queries the profiles table
    Then they only get their own row
    And an attempt to update B's profile modifies no rows

  Scenario: Health check
    When a GET request is made to the health function
    Then it responds 200 with {"status":"ok"}

  Scenario: First deployment with content
    Given a PR with the profiles migration and the health function merged into main
    When deploy-supabase runs (created in E0, T-010)
    Then the migration and the health function are applied to the production project

  Scenario: Database tests on the PR
    Given a PR with changes in supabase/
    When supabase-ci runs
    Then the migrations are applied to an ephemeral Supabase and the pgTAP tests pass
    And the PR cannot be merged if they fail

  Scenario: Project kept active on the free plan
    When the scheduled keepalive workflow runs
    Then the production health function responds 200
```

**Technical notes**
- Migration: extensions (`unaccent`), the enums from `03-data-model.md` §1 needed by `profiles`, `profiles` table, reusable `set_updated_at` trigger, sign-up trigger.
- pgTAP tests in `supabase/tests/profiles.test.sql` (RLS enabled, isolation, the trigger creates the row).
- Workflows `supabase-ci.yml` (add it as a required check in the `main` ruleset) and `keepalive.yml` (cron `0 6 */3 * *` + `workflow_dispatch`, uses the `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` repo secrets; the publishable key goes in the `apikey` header, ADR-17). `deploy-supabase.yml` already exists since E0.
- Backend flow (no UX or Maestro).

---

## US-03 · Kinetic Dark design system in Compose
**As** the product team
**I want** the design system tokens and base components implemented in Compose
**So that** every screen is built consistently and without hardcoded visual values.

```gherkin
Feature: Available and verifiable design system

  Background:
    Given the AppTheme theme with the tokens from planning/05-design-system.md

  Scenario: Tokens applied globally
    When any screen of the app is opened
    Then the background uses surfaceBase (#0B0F14)
    And cards use surfaceRaised (#121820) with a 1 dp border at 8% white and a 16 dp radius
    And text uses the Plus Jakarta Sans font

  Scenario: Component catalog in debug
    Given a debug build of the local flavor
    When the user opens the "Catálogo de componentes" screen from Profile
    Then every component from section 6 of the design system is shown in its different states
    And the screen does not exist in the prod flavor

  Scenario: Accessibility of interactive components
    Given any interactive component in the catalog
    Then its touch target is at least 48 × 48 dp
    And actionable icons have a contentDescription
```

**Technical notes**
- `designsystem/` package: `AppTheme`, `AppColors`, typography with `tnum`, `Spacing`, `Shapes` and the components from `05-design-system.md` §6.
- Unit tests for component logic where there is any (e.g. `ProgressRing` sweep calculation, over-target state).
- Maestro: navigates to the catalog and checks that key components exist by testTag.

---

## US-04 · Navigable shell with 4 tabs, contextual FAB and adaptive layout
**As** a user
**I want** to install the APK and move between all sections of the app
**So that** I can validate navigation and style on my phone and tablet before the features exist.

```gherkin
Feature: Navigable shell with WIP sections

  Background:
    Given the user has installed the APK and opens the app

  Scenario: Launch
    Then the app starts without errors
    And it shows the Top App Bar with the section title and the avatar
    And it shows the navigation with the tabs "Diario", "Entreno", "Progreso" and "Perfil"
    And the initial active tab is "Diario"

  Scenario: Navigating between tabs
    When the user taps "Entreno", "Progreso", "Perfil" and "Diario" in sequence
    Then each screen is shown with its title and a WIP container with skeletons
    And the active tab indicator is highlighted in neonLime
    And returning to a tab preserves its scroll position

  Scenario: The avatar opens Profile
    Given the user is on "Progreso"
    When they tap the Top App Bar avatar
    Then the "Perfil" tab becomes active

  Scenario Outline: Contextual FAB
    Given the user is on the "<tab>" tab
    When they tap the "+ Registro" button
    Then a WIP Bottom Sheet titled "<action>" opens

    Examples:
      | tab      | action             |
      | Diario   | Añadir alimento    |
      | Entreno  | Empezar entreno    |
      | Progreso | Registrar peso     |

  Scenario: FAB hidden on Profile
    When the user is on the "Perfil" tab
    Then the "+ Registro" button is not shown

  Scenario: Tablet layout
    Given the app runs on a screen ≥ 600 dp wide
    Then navigation is shown as a side NavigationRail with the FAB at its top

  Scenario: Offline
    Given the device loses its internet connection
    Then the "Sin conexión" banner appears below the Top App Bar
    And it disappears when the connection is restored
```

**Technical notes**
- Type-safe navigation with one back stack per tab; the FAB and its action are defined by the active route (ADR-10).
- `ConnectivityObserver` (interface in `shared`, implementation in `androidMain`).
- Maestro: navigation, avatar and FAB scenarios. The tablet scenario is verified manually and with a tablet emulator if available.

---

## Epic Definition of Done
- US-01…US-04 merged with green CI; `deploy-supabase` and `keepalive` run successfully at least once.
- Maestro regression green.
- `prodDebug` APK delivered; the user validates navigation and style on phone and tablet.
- Tag `v0.1.0`.
