# E9 · Finalization → `v1.0.0`

## Goal
Close Phase 1: show the in-app credits and licences, polish the whole app into a stable, accessible build on phone and tablet, and apply the deferred UI-refinement pass. This epic ships `v1.0.0`.

## Scope
- Includes: the "Acerca de · Licencias" screen; accessibility, empty/error states and tablet across every screen; the UX rulebook, designsystem templates and CI guards with the migration of every screen to them (US-56, ADR-69); the deferred UI-refinement items collected at the 👤 checkpoints.
- Out of scope: new features; AI training assistant and adaptive calorie adjustment (Phase 2).
- This epic depends on every previous epic (`E1`…`E8`), because its polish pass and credits cover all domains (training, food and weight).

## Dependencies
E1, E2, E3, E4, E5, E6, E7 and E8 (its polish pass and the third-party credits cover all screens and domains).

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
    And they see the Google Gemini credit for the photo estimates

  Scenario: Open-source licences
    When the user taps "Licencias de código abierto"
    Then they see the licences of the bundled open-source libraries
```

**Technical notes**
- The screen is defined here but its **entry point is the Profile screen** (`Perfil → "Acerca de"`): the link is wired in this epic (E2 is closed; the "Perfil" location is the product decision, recorded here).
- Open-source licences through the `oss-licenses-plugin` (Google Play Services); catalog credits read from a single `credits` resource so RepDB, Open Food Facts and Google Gemini (photo estimates) live in one place.
- The app's own licence stays **repo-only** (ADR-13); the version/build is shown for support.

---

## US-46 · Polish, accessibility and tablet
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

  Scenario: Rotation and configuration changes
    Given the user is editing a quantity or a set
    When they rotate the device
    Then they do not lose what they were typing
```

**Technical notes**
- No signed release build in Phase 1 (ADR-67): the app is delivered as the `prodDebug` APK.
- Performance review: no unnecessary recompositions in the live session (Layout Inspector) and cold start < 2 s on the user's phone, checked manually on the `prodDebug` APK.

---

## US-56 · UX consistency: one rulebook, templates and guards
**As** a user
**I want** every screen to look and behave like the rest of the app
**So that** I never have to relearn a pattern and the app feels modern and professional.

```gherkin
Feature: UX consistency

  Scenario: Same structure on every screen
    Given any screen of the app
    Then its top bar, pinned footer, message host and offline banner come from the shared templates
    And a screen about one object is titled with the object's name and its actions are in the top bar

  Scenario: Editing never applies silently
    Given an editor or a bottom sheet with a changed value
    When the user closes it without tapping "Guardar"
    Then nothing is applied
    And a full-screen editor with unsaved changes first asks "¿Descartar los cambios?"
    And after a successful "Guardar" the editor closes and returns to where it was opened

  Scenario: Primary action always visible
    Given any bottom sheet with a form and the keyboard open
    Then its primary action is pinned and visible without scrolling

  Scenario: Swipe to delete feels like a modern app
    Given a saved record in a list
    When the user drags the row less than half of its width and releases
    Then the row springs back and nothing is deleted
    When the user drags past half of the width
    Then the action is armed with a visible change and one vibration
    When the user releases while armed
    Then the row leaves at once, the rows below close the gap and the record is deleted
    And if the deletion fails the row returns with "No se pudo eliminar. Inténtalo de nuevo."

  Scenario: One vibration per action
    Given the user completes a set, saves an edit or deletes a record
    Then the phone gives exactly one vibration for that action

  Scenario: One wording per concept
    Given any screen
    Then the same concept uses the same label everywhere, per the glossary
    And "Comidas del día" (the structure) is never confused with "Comidas guardadas" (the templates)

  Scenario: Offline
    Given the phone has no connection
    Then only one "Sin conexión" banner is shown
    And an action that needs the network is disabled with "Sin conexión: no se puede <acción>"

  Scenario: Guards
    Given a change that reinvents a template or brings back a decided item
    Then `check-ux-conventions.sh` fails the CI

  Scenario: Amount from a photo always gives an estimate
    Given a food chosen in "Añadir" and a photo of the portion
    When the user taps "Usar foto"
    Then the amount field shows a best estimate in the food's base unit
    And the estimate is never empty
    And only an unusable answer shows "No hemos podido estimar la cantidad" with "Reintentar"

  Scenario: Confidence is shown with colour and text
    Given an estimate arrived with confidence 7 out of 10
    Then "Confianza 7/10" is shown with a colour on the scale from red (1) to green (10)
    And TalkBack reads "Confianza 7 de 10"
    And the colour is never the only cue

  Scenario: Diagnostics only when enabled
    Given the secret FOOD_PHOTO_DEBUG is not set
    Then the function logs no diagnostics beyond errors
    When it is set
    Then the function logs mode, model, elapsed time, provider status, response shape, rejection reason, estimate and confidence
    And never the photo, tokens, user note or full bodies
```

**Technical notes**
- Rules, templates and glossary: `05-design-system.md` §8–§10; decision and amendments of ADR-24, ADR-41 and ADR-45: ADR-69. Waves: (0) rulebook and workflow, (1) designsystem foundations and guards, (2) migration by domain (Diario/food/comidas, Peso, Training, Perfil/Preferencias/meals editor), (3) wording and motion, (4) 👤 checkpoints.
- Every template task ends with a sweep: its ad-hoc copies are migrated in the same epic (R-39). Guards start as warnings and become failures per migrated domain.
- Out of scope: the "Entreno en curso" bar (dropped, ADR-69) and any new feature.

---

## UI refinement (deferred items)

A presentation-only pass (FR-6) over the screens built in E2…E8. Items collected at the 👤 checkpoints are mapped to the rule or template that closes them (audit `ux-audit-2026-10.md` §7; numbering is the audit's, **A…C** were added after it). Items already done, obsolete or superseded were dropped. A convention stated at a future checkpoint goes into `05-design-system.md` §8–§10, not into this table; only one-off visual details are appended here.

| # | Item | Solution (rule or template) |
|---|---|---|
| 1 | Onboarding polish: spacing, step layout, transitions (E2-A) | Step template and a slide transition. Spacing is visual and belongs to the UI pass. |
| 2 | Onboarding "Sexo": empty space (E2-A) | Two equal `SelectableCard`s side by side, with the birth date in the same view. |
| 3 | Typography review (E2-A) | Out of scope here. The templates fix the title *levels*: one style each for screen titles, section headers and sheet titles. |
| 5 | Preferencias: remove the "Calorías que quemas…" helper (E2-B) | Delete `energy_tdee_helper` from both screens. |
| 6 | Preferencias: "Guardar" closes the screen (E2-B) | R-13, through `AppEditorScreen`. |
| 7 | Perfil: gear or pencil next to the name (E2-B) | D8: a gear in the profile header; the account actions move into Preferencias. |
| 8 | Diario: remove "restantes" and the macro % (E4-A) | The ring shows only what you ate and "de N kcal", plus "+N kcal en exceso" when you go over. `MacroProgressBar` reads "9 g / 233 g". |
| 9 | Diario: remove the full-width "Añadir comida" (E4-A) | Remove it; each meal keeps its own `AddItemButton`. |
| 10 | Diario: meals first, ring at the bottom (E4-A) | D11: meals first and the full dashboard at the bottom. A one-line summary strip on top ("1.240 / 2.100 kcal · P 80 · C 150 · G 40") keeps the remaining budget visible without scrolling. |
| 12 | Diario: tapping a logged food opens its details (E4-A) | One FoodSheet (LST-1). |
| 14 | Diario day picker: mark the logged days and add a "Hoy" chip (the calendar button itself is done, T-071) | Mark the days with entries and show a "Hoy" chip whenever the visible day is not today. |
| 15 | End of rest: the vibration is too short (T-059) | A longer pattern through the `Haptics` port, for example three strong pulses over about 1.2 s, tested on your phone (§14.4). Treat it as a functional fix. |
| 16 | End of rest: the sound is odd (T-059) | You choose among 2–3 short candidates: the system notification sound, a soft chime or a double beep. Still silent when the phone is muted. |
| 18 | Preferencias: meals below macros (E4-B round 2) | Reorder, or moot with the hub (D9). |
| 19 | Meals editor: a removal is saved by "Guardar" (E4-B round 2) | R-11 (everything inside an editor waits for "Guardar"), amending ADR-41 (D3). The server still checks "in use" and "last meal" on save, and the row shows the hint beforehand. |
| 20 | Meals editor: a ✓ inside the name field (E4-B round 2) | Name sheet (R-15, D6). If you keep inline rows, use the ✓ in the `AppTextField` v2 trailing slot. |
| 21 | Meals editor: unique names, ignoring case (E4-B round 2) | One rule for every collection you name: meals, folders, saved comidas, routines. One message template: "Ya existe <una comida / una carpeta…> con ese nombre". |
| 22 | Meals editor: "Añadir comida" disabled while a meal is being created (E4-B round 2) | Moot with the Name sheet, which blocks the screen behind it. |
| 23 | Top bar too tall (E4-C round 1) | D1: tab roots without a title bar, compact screen headers, entity titles. |
| 24 | Peso: "Objetivo" and "Peso actual" drift apart (E5-B round 3) | One `MetricCard` (§9); `GoalCard` becomes a `MetricCard` with a progress slot. |
| 25 | Meals editor: ✓ and ✕ for a meal being created (E4-B round 2) | Name sheet (D6). |
| 26 | Swipe like modern apps, with haptics (E4-C round 3) | R-21 and D5 (LST-5). The full specification, vibration design and implementation steps are in §14. |
| 27 | Stray line across text fields (E4-C round 3) | SHT-3: diagnose with a screenshot test, then use a top-only hairline. |
| 28 | Comida details: a divider "Alimentos en esta comida" (E4-C) | `SectionHeader` (§9). |
| 30 | Peso: Health Connect status inside "Peso actual", and "Sincronizar" as a real button (E5-B) | The status becomes a supporting line of the "Peso actual" `MetricCard`, with "Sincronizar" as its action. |
| 31 | Peso: measurement history opened from a calendar button (E5-B) | R-03: a calendar icon in the top bar. |
| 32 | Weight trend: show goal progress visually (E5-B) | A chart overlay: a marker on the first weight, the target line (already there), and a shaded band between them. |
| 33 | Diario: "1 vaso (200 ml)" (user, 2026-10-22) | `FoodEntryRow` shows the portion label plus the amount in base units. |
| 35 | Normal set: an "N" instead of the number (E6-A) | `SetTypeBadge` everywhere: builder, live session, history, detail. |
| 36 | Live session: planned placeholders darker (E6-A) | Planned values use `textSecondary` today, chosen for contrast. Make them clearly different from typed values without relying on colour alone (for example a lighter font weight), or switch to `ghostData` if you accept lower contrast for planned values. |
| 37 | RPE help as a list (E6-A) | `HelpButton` with structured content (FBK-4). |
| 38 | Folders: stray line on "Nueva carpeta" (E6-B) | Same as 27. |
| A | Estadísticas → Musculación radar: "Isquiotibiales" wraps with the final "s"; show "Isquios" (NIT) | Shorter axis label (glossary). |
| B | Estadísticas → Últimos 7 días: the body map feels unprofessional; study how to get a better result (reference `design/reported/E8-A-stats-radar-bodymap.png`, gitignored) | ux-reviewer assesses options and complexity in the Training migration task; the user picks before any rebuild. |
| C | Comida details (editable screen): kcal/P/C/G summary, "+" and trash scattered; "Guardar" in its own row above "Añadir"; remove the hint "Se añadirá tal como lo ves…" (reference `design/reported/E7-A-comida-details-layout.png`) | Built on the shared templates: name in the title, actions in the top bar, `AddItemButton` at the end of the list, pinned "Guardar" (§6.3 of the audit). |
| D | Chart metric and range on other screens: `ExerciseProgressScreen` (`ProgressMetric` chips and its own range selector) and `TrainingStatsScreen` (`FrameChips`) still use chips, while Peso now picks the metric from the chart title (`AppPickerSheet`, T-123) and the range with `TimeRangeSelector` | Owner T-125: decide one pattern (title picker vs chips) and move both to `TimeRangeSelector` |

---

### UX consistency guardrails (E4 retrospective, 2026-10-21)

The user found repeated UX defects and inconsistencies across screens (icons vs. text for add/remove, no "Deshacer" on swipe-delete, tapping a food or exercise must open its detail, feedback repeated at several checkpoints) and suspects `ux-reviewer` is not catching them. The fix is deliberately done here, not earlier:
- Consolidate every convention the user has stated at checkpoints into `planning/05-design-system.md` as explicit rules.
- Update `ux-reviewer` to check each UI plan against those rules and against the existing screens, so the same mistakes never come back.
- Run a cross-screen consistency audit as part of the polish pass.
- Success criterion (E5 retrospective, 2026-10-22): after E9 the user must not see new screens follow a different approach than existing ones. The `ux-reviewer` refinement is part of the epic's Definition of Done, not optional polish.

## Epic Definition of Done
- All stories US-01…US-56 closed (US-42…US-44 deferred, ADR-66); the app is verified manually at the checkpoints.
- The "Acerca de" screen shows version/build, the RepDB, Open Food Facts and Google Gemini credits and the OSS licences.
- Security checklist from `02-architecture.md` §7 reviewed and documented in the final PR.
- The `prodDebug` APK installed on the user's phone and tablet; positive feedback.
- The deferred UI-refinement items are applied and `check-ux-conventions.sh` passes in CI with every guard a failure.
- The `ux-reviewer` refinement and the workflow changes of ADR-69 are in place and have run on the E9 UI tasks (plan and diff).
- Tag `v1.0.0` and the Phase 1 GitHub Release (notes only, no APK) published.
