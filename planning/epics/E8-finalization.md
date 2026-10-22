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

## US-45 · About, version and licences
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

## US-46 · Polish, accessibility, tablet and release build
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
| Preferencias: drop the helper line "Calorías que quemas al día según tu cuerpo y tu actividad." under "Gasto diario estimado" (not needed) | Checkpoint E2-B, 2026-10-19 |
| Preferencias: "Guardar" should close the screen and return to the Perfil root (today the user taps Guardar and then back: two actions) | Checkpoint E2-B, 2026-10-19 |
| Perfil root: replace the full-width "Preferencias" button with a gear or pencil icon button next to the user's name, to leave room for the metrics and charts the Perfil will gain | Checkpoint E2-B, 2026-10-19 |
| Diario: remove the redundant "restantes" label and the macro percentages (the grams "9 g / 233 g" are enough) | Checkpoint E4-A, 2026-10-20 |
| Diario: remove the full-width "Añadir comida" button (each meal already has its "Añadir a …" button) | Checkpoint E4-A, 2026-10-20 |
| Diario: layout with the meals first (Desayuno, Comida, Merienda, Cena) and the calorie ring at the bottom | Checkpoint E4-A, 2026-10-20 |
| Diario: each logged food shows P / C / G (not only the meal total), besides name, grams and kcal | Checkpoint E4-A, 2026-10-20 |
| Diario: tapping a logged food opens its details (the nutrition data Open Food Facts returns for it); to be reconciled with the entry editing of T-060 | Checkpoint E4-A, 2026-10-20 |
| Diario: add an "Almuerzo" meal between Desayuno and Comida (product decision: default meals / ADR-?, to confirm with the user) | Checkpoint E4-A, 2026-10-20 |
| Diario: a calendar button next to the day name (to the right of "Hoy") to jump to a given day, e.g. the same day last month; to be reconciled with the history calendar of T-063 | Checkpoint E4-A, 2026-10-20 |
| Rest timer alert (live session): the vibration when the rest ends is too short to notice; make it longer/stronger (functional fix, may be done earlier as a `fix(T-###)`) | User report during T-059, 2026-10-20 |
| Rest timer alert (live session): the end-of-rest sound plays but sounds odd; replace it with a different one (ask the user which) | User report during T-059, 2026-10-20 |
| Live session: remove the eye icon and "Pantalla activa" indicator (no value; the screen still stays on). | User report |
| Preferencias: place the meal-structure section below "Distribución de macros" | Checkpoint E4-B round 2, 2026-10-21 |
| Meal structure: removing a meal must be persisted by "Guardar" like adding and reordering (today the trash button writes at once; mixed behaviour) | Checkpoint E4-B round 2, 2026-10-21 |
| Meal structure: "Añadir comida" name field gets a tick button inside the text input to confirm (today only the keyboard tick confirms) | Checkpoint E4-B round 2, 2026-10-21 |
| Meal structure: meal names must be unique (case-insensitive); adding an existing name such as "Desayuno" is rejected with a hint | Checkpoint E4-B round 2, 2026-10-21 |
| Meal structure: "Añadir comida" is disabled while a new meal is being created (no double-add) | Checkpoint E4-B round 2, 2026-10-21 |
| Diario/Perfil/Comidas: top bar with the section name takes too much vertical space; make it smaller (all sections) | Checkpoint E4-C round 1, 2026-10-21 |
| Weight screen: the "Objetivo" card differs in pencil alignment and text padding from "Peso actual"; extract one shared card component (title, value, subtitle, pencil) so similar cards cannot drift (local screenshot: `design/reported/e5b-r3-objetivo-vs-peso-actual-and-trend-two-dots.png`, gitignored) | Checkpoint E5-B round 3, 2026-10-22 |
| Meal structure: a meal being created shows tick (save) and X (discard) buttons; discarding does not require saving then deleting | Checkpoint E4-B round 2, 2026-10-21 |
| Swipe-to-delete still feels unlike modern apps: ux-reviewer researches how swipe works in modern apps and aligns the shared component; add haptic feedback when the drag reaches the action threshold and when the delete fires | Checkpoint E4-C round 3, 2026-10-21 |
| Visual bug: a stray horizontal line crosses the value of text fields (seen on the quantity "30" of the food details sheet; reference `design/reported/e4c-line-through-quantity-field.png (gitignored)`). It recurs across screens: find the cause in the shared field/sheet components and fix it app-wide | Checkpoint E4-C round 3, 2026-10-21 |
| Comida details: no visual separation between the comida summary and its first food; add a section divider with the label "Alimentos en esta comida" | Checkpoint E4-C approval, 2026-10-21 |
| Weight screen: a pencil button inside the "Peso actual" card replaces the "Registrar peso de hoy" button | Checkpoint E5-B, 2026-10-22 |
| Weight screen: the Health Connect sync status moves inside "Peso actual" and "Sincronizar" becomes a real button (not text) | Checkpoint E5-B, 2026-10-22 |
| Weight screen: "Historial de mediciones" is reached from a calendar button, the same pattern as the calories (Diario) calendar | Checkpoint E5-B, 2026-10-22 |
| Weight trend: goal progress is shown visually in "Tendencia" (first-weight marker, target line, shaded band between them) instead of a percentage on the goal card | Checkpoint E5-B, 2026-10-22 |
---

### UX consistency guardrails (E4 retrospective, 2026-10-21)

The user found repeated UX defects and inconsistencies across screens (icons vs. text for add/remove, no "Deshacer" on swipe-delete, tapping a food or exercise must open its detail, feedback repeated at several checkpoints) and suspects `ux-reviewer` is not catching them. The fix is deliberately done here, not earlier:
- Consolidate every convention the user has stated at checkpoints into `planning/05-design-system.md` as explicit rules.
- Update `ux-reviewer` to check each UI plan against those rules and against the existing screens, so the same mistakes never come back.
- Run a cross-screen consistency audit as part of the polish pass.

## Epic Definition of Done
- All stories US-01…US-46 closed; the app is verified manually at the checkpoints.
- The "Acerca de" screen shows version/build, the RepDB and Open Food Facts credits and the OSS licences.
- Security checklist from `02-architecture.md` §7 reviewed and documented in the final PR.
- Signed release APK installed on the user's phone and tablet; positive feedback.
- The deferred UI-refinement items are applied.
- Tag `v1.0.0` and the Phase 1 GitHub Release published.
