# E8 · Finalization → `v1.0.0`

## Goal
Close Phase 1: show the in-app credits and licences, polish the whole app into a stable, accessible release on phone and tablet, produce the signed APK, and apply the deferred UI-refinement pass. This epic ships `v1.0.0`.

## Scope
- Includes: the "Acerca de · Licencias" screen; accessibility, empty/error states, tablet and release build across every screen; the deferred UI-refinement items collected at the 👤 checkpoints.
- Out of scope: new features; AI training assistant and adaptive calorie adjustment (Phase 2).
- This epic depends on every previous epic (`E1`…`E7`), because its polish pass and credits cover all domains (training, food and weight).

## Dependencies
E1, E2, E3, E4, E5, E6 and E7 (its polish pass and the third-party credits cover all screens and domains).

## Reference screen
`design/stitch/progress/screen.png` as the layout baseline; the About screen follows the design system's settings layout.

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas).

---

## US-42 · About, version and licences
**As** a user
**I want** to see the app version and the licences and credits it uses
**So that** the third-party attributions and legal notices are available inside the app.

```gherkin
Feature: About and licences

  Scenario: Opening the screen
    Given the user opens "Perfil" and taps "Acerca de"
    Then the screen shows the app name and the version/build

  Scenario: Third-party credits
    When the user scrolls the screen
    Then they see "Datos de ejercicios por RepDB (repdb.co)"
    And they see the Open Food Facts credit

  Scenario: Open-source licences
    When the user taps "Licencias de código abierto"
    Then they see the licences of the bundled open-source libraries
```

**Technical notes**
- The screen is defined here but its **entry point is the Profile screen** (`Perfil → "Acerca de"`): the link is wired in this epic (E2 is closed; the "Perfil" location is the product decision, recorded here).
- Open-source licences through the `oss-licenses-plugin` (Google Play Services); catalog credits read from a single `credits` resource so RepDB and Open Food Facts live in one place.
- The app's own licence stays **repo-only** (ADR-13); the version/build is shown for support.

---

## US-43 · Polish, accessibility, tablet and release build
**As** a user
**I want** a stable, accessible app that is comfortable on my phone and my tablet
**So that** I can use it every day with confidence.

```gherkin
Feature: Version 1.0 quality

  Scenario: Empty and error states on every screen
    Given any screen with remote data
    When there is no data, the network fails or the server responds with an error
    Then the design system's empty or error state is shown with a retry action
    And never a blank screen or a crash

  Scenario: Accessibility
    Given the Android accessibility scanner is enabled
    When the main screens are walked through
    Then every interactive element is at least 48 dp and has an accessible description
    And the primary text contrast meets WCAG AA

  Scenario: Font scaling
    Given the system font size at 130%
    Then key figures (kcal, weight, kg, reps, timer) are fully readable without clipping

  Scenario: Tablet
    Given a tablet in landscape
    Then Diary, Progress and the Workout session use the two-column layout
    And no element is stretched across the full width without a limit

  Scenario: Signed release build
    When the release build of the prod flavor is run
    Then a signed APK with R8 enabled is produced
    And the app works the same as in debug (login, diary, weight and workout)

  Scenario: Rotation and configuration changes
    Given the user is editing a quantity or a set
    When they rotate the device
    Then they do not lose what they were typing
```

**Technical notes**
- Release keystore outside the repo; release SHA-1 registered in Google Cloud (👤). R8 rules for kotlinx-serialization, Ktor and supabase-kt.
- `release.yml` workflow that builds the signed APK when a `v*` tag is created and attaches it to a GitHub Release (keystore as a base64 secret).
- Performance review: no unnecessary recompositions in the live session (Layout Inspector) and cold start < 2 s on the user's phone.

---

## UI refinement (deferred items)

A presentation-only pass (FR-6) over the screens built in E2…E7. Every 👤 checkpoint that defers a UI detail appends it here with its source; it never changes features.

| Item | Source |
|---|---|
| General onboarding polish (spacing, step layout, transitions) after the T-039 regrouping | Checkpoint E2-A |
| Onboarding "Sexo": better use of the empty space around the two options | Checkpoint E2-A |
| Typography review across screens (hierarchy and sizes of titles, descriptions and labels) | Checkpoint E2-A |
| Tablet: move the primary action out of the top of the NavigationRail into each section's content | E1 closure checkpoint, 2026-10-13 (`tasks/todo.md`, "Notes for later epics") |

---

## Epic Definition of Done
- All stories US-01…US-43 closed; the app is verified manually at the checkpoints.
- The "Acerca de" screen shows version/build, the RepDB and Open Food Facts credits and the OSS licences.
- Security checklist from `02-architecture.md` §7 reviewed and documented in the final PR.
- Signed release APK installed on the user's phone and tablet; positive feedback.
- The deferred UI-refinement items are applied.
- Tag `v1.0.0` and the Phase 1 GitHub Release published.
