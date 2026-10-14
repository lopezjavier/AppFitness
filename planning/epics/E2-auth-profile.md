# E2 · Authentication and profile → `v0.2.0`

## Goal
Each person signs in with their Google account, completes their biometric profile and defines their energy strategy and macro split. This data feeds the Diary (E3), the weight rate (E4) and the calculations across the app.

## Scope
- Includes: Google sign-in/sign-out, onboarding, profile editing, TDEE, strategy, calorie target, macros.
- Out of scope: account deletion, custom profile photo (the Google one is used), connections with other apps.

## Dependencies
E1. Google OAuth client and Supabase Google provider configured (E0, T-011/T-012).

## Tables involved
`profiles` (extension), `nutrition_goals`, `body_measurements` (only table creation and the first row from onboarding). See `03-data-model.md`.

## Reference screens
`design/stitch/profile-goals/screen.png` (only the personal data, "Estrategia Energética" and "Distribución de Macros" sections).

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language).

---

## US-05 · Sign in with Google
**As** a user
**I want** to sign in to the app with my Google account
**So that** my data is stored in the cloud and only I can see it.

```gherkin
Feature: Google authentication

  Scenario: First sign-in
    Given the user opens the app without a session
    Then the welcome screen is shown with the "Continuar con Google" button
    When they tap the button and choose their account in the Google picker
    Then their Supabase session and their profiles row with name and avatar are created
    And they are taken to onboarding

  Scenario: Persistent session
    Given a user who already signed in and completed onboarding
    When they close the app and open it again
    Then they land directly on the "Diario" tab without authenticating again

  Scenario: Account picker cancelled
    Given the user is on the welcome screen
    When they open the Google picker and close it without choosing an account
    Then they stay on the welcome screen without error messages

  Scenario: Network error when signing in
    Given the device has no connection
    When they tap "Continuar con Google"
    Then the message "Sin conexión. Inténtalo de nuevo" is shown, and tapping "Continuar con Google" again retries

  Scenario: Sign out
    Given a signed-in user on Profile
    When they tap "Cerrar sesión" and confirm
    Then the session is removed from the device
    And the welcome screen is shown

  Scenario: Test login (local flavor only)
    Given a build of the local flavor
    Then the welcome screen also shows the "Test login" button
    When they tap it
    Then they are signed in as the seeded user test@appfitness.local
```

**Technical notes**
- `AuthCredentialProvider` interface (domain) + `GoogleAuthCredentialProvider` (androidMain, Credential Manager with `GetGoogleIdOption`, `serverClientId = GOOGLE_WEB_CLIENT_ID`, nonce).
- `supabase.auth.signInWith(IDToken)`; session persisted by supabase-kt; `sessionStatus` as a `Flow` to decide the root route (Welcome / Onboarding / Main).
- Maestro subflow `tests/e2e/subflows/login.yaml`, reused by the rest of the stories.
- Profile is reached from the 4th bottom tab "Perfil", whose icon is the user's Google photo; the top-bar avatar is removed (ADR-26, implemented in T-037).
- The "Test login" button is shared UI, shown only when the build provides test credentials: the `local` flavor provides them (`FlavorTestLogin` source set) and `prod` provides none, so prod never shows the button nor contains the credentials.

---

## US-06 · Biometric onboarding
**As** a new user
**I want** to enter my physical data quickly and with guidance
**So that** the app can calculate my energy needs.

```gherkin
Feature: Biometric data onboarding

  Scenario: Completing onboarding
    Given an authenticated user with onboarding_completed = false
    When they go through the steps: sex, date of birth, height, current weight and activity level
    And they tap "Continuar" on the last step
    Then sex, date of birth, height and activity level are saved in their profile
    And their current weight is logged as the first body_measurements entry with source "manual"
    And they are taken to their goal setup (US-07)

  Scenario: Progress indicator and going back
    Given the user is on step 3 of 4
    Then they see the indicator "3 / 4"
    When they tap "Atrás"
    Then they return to step 2 with the values they had chosen

  Scenario Outline: Range validation
    Given the user is on the "<step>" step
    When they try to enter the value <value>
    Then the control does not allow selecting it and the value stays within [<min>, <max>]

    Examples:
      | step   | value | min | max |
      | Edad   | 12    | 14  | 100 |
      | Altura | 260   | 100 | 250 |
      | Peso   | 15    | 20  | 400 |

  Scenario: Resuming an interrupted onboarding
    Given a user who closed the app without finishing onboarding
    When they open it again
    Then onboarding is shown from the first step
    And each step is prefilled with the value already saved, if any

  Scenario: Finishing the biometric steps again after an interruption
    Given a user who passed the last biometric step and closed the app on the goal step
    When they reopen the app and tap "Continuar" on the last biometric step again
    Then their onboarding weight entry is updated with the new value
    And no second body_measurements entry is created

  Scenario: Editing data after onboarding
    Given a user who completed onboarding
    When on Profile they change their height to 180 cm and tap "Guardar"
    Then their profile is updated
    And the displayed TDEE is recalculated with the new height
    And the daily calorie target is recalculated and saved in nutrition_goals

  Scenario: Current weight is read-only on Profile
    Given a user who completed onboarding
    When they open Profile
    Then "Peso actual" shows their latest weight
    And it cannot be edited there (weight logging arrives with US-15 in E4)
```

**Technical notes**
- Four steps with the indicator "N / 4" (Checkpoint E2-A, T-039): 1 "Sobre ti" (sex + date of birth), 2 "Medidas" (height + weight, two wheels side by side), 3 "Nivel de actividad", 4 "Objetivos" (US-07). "Continuar" on step 3 is the last biometric step: it saves the profile and the onboarding weight.
- Controls: segmented selector (sex), wheel pickers (date, height in cm, weight with 1 decimal), list of selectable cards (activity with the labels from `04-business-rules.md` §1.3).
- Migration: `body_measurements` table with RLS + pgTAP (the `profiles` columns already exist since US-02).
- `onboarding_completed = true` only after the goals are also created (US-07).
- Resume and re-save behaviour per ADR-24: steps prefilled from the saved profile and the onboarding weight entry; redoing the last step updates that entry instead of inserting a new one.
- Profile has a single "Guardar" button for the whole screen (personal data, strategy and macros; ADR-24). "Peso actual" is read-only in E2.

---

## US-07 · Energy strategy and calorie target
**As** an athlete with a body composition goal
**I want** to choose Deficit, Maintenance or Surplus and adjust my calories
**So that** I have a daily calorie target suited to my goal.

```gherkin
Feature: Energy strategy and calorie target

  Scenario: TDEE calculation
    Given a male user, 26 years old, 178 cm, 74.2 kg and activity "Moderado"
    When they open the "Estrategia energética" section
    Then "TDEE: 2.681 kcal" is shown

  Scenario: Changing the strategy
    Given the user's TDEE is 2,800 kcal and their strategy is "Mantenimiento"
    When they select "Déficit" in the segmented selector
    Then "Meta diaria: 2.400 kcal/día" is shown
    And the pill "−400 kcal · Déficit moderado"

  Scenario: Fine-tuning the target
    Given the strategy is "Déficit" with a −400 adjustment
    When the user lowers the adjustment by two steps
    Then the adjustment becomes −500 kcal and the target 2,300 kcal/day

  Scenario Outline: Safety floor by sex (ADR-25)
    Given a <sex> user with a TDEE of <tdee> kcal and strategy "Déficit"
    When the user sets a −400 adjustment
    Then the daily target is set to <floor> kcal/day
    And a safety minimum warning is shown

    Examples:
      | sex    | tdee  | floor |
      | female | 1,500 | 1,200 |
      | male   | 1,800 | 1,500 |

  Scenario: Saving the goals in onboarding
    Given the user is on the last onboarding step
    When the user taps "Guardar objetivos"
    Then strategy, adjustment and calorie target are saved in nutrition_goals
    And the macro split is saved with the default Protein 30%, Carbs 45% and Fat 25%
    And onboarding is marked as completed
    And a confirmation with haptic feedback is shown

  Scenario: Saving the goals on Profile
    Given the user is on Profile
    When they change the strategy or the adjustment and tap "Guardar"
    Then strategy, adjustment and calorie target are saved in nutrition_goals
    And a confirmation with haptic feedback is shown
```

**Technical notes**
- `CalculateTdee` and `CalculateCalorieTarget` use cases (100% tested with the examples from `04-business-rules.md` §1).
- `nutrition_goals` migration + RLS + pgTAP (including the sum-equals-100 constraint).
- In onboarding this is step 4 / 4, after the activity level, and has no macro sliders (default split 30/45/25, adjusted later on Profile; ADR-24). On Profile it is an editable card saved by the screen's single "Guardar" button.

---

## US-08 · Macro split with auto-balancing sliders
**As** a user tracking macronutrients
**I want** to split my calories between protein, carbohydrates and fat
**So that** I know my daily gram targets for each macro.

```gherkin
Feature: Auto-balanced macro split

  Background:
    Given the user's daily target is 2,400 kcal
    And their split is Protein 30%, Carbs 45% and Fat 25%

  Scenario: Initial display
    Then Proteínas shows "30 % · 180 g · 720 kcal"
    And Carbohidratos shows "45 % · 270 g · 1.080 kcal"
    And Grasas shows "25 % · 67 g · 600 kcal"
    And the badge shows "TOTAL: 100 %"

  Scenario: Auto-balance when moving a slider
    When the user raises Protein to 35%
    Then Carbs becomes 42% and Fat 23%
    And Proteínas shows "210 g", Carbohidratos "252 g" and Grasas "61 g"
    And the badge still shows "TOTAL: 100 %"

  Scenario: Per-macro minimum
    Given a split of Protein 90%, Carbs 5%, Fat 5%
    When the user tries to raise Protein
    Then the Protein slider does not go above 90%

  Scenario: Persistence
    When the user taps "Guardar" on Profile
    Then the percentages are saved in nutrition_goals
    And the Diary uses the new gram targets
```

**Technical notes**
- `BalanceMacros` use case (largest-remainder algorithm, `04-business-rules.md` §1.5) with property tests: the sum is always 100 and all values are within [5, 90].
- `MacroSlider` design system component; stacked bar with the three macro colors.
- The sliders live only on Profile; onboarding saves the default split (ADR-24).

---

## Epic Definition of Done
- A new user can: sign in, complete onboarding, set goals, close the app and get straight back in.
- pgTAP isolation tests for `profiles`, `nutrition_goals` and `body_measurements`.
- Maestro regression green (using "Test login").
- `prodDebug` APK validated by the user with their real Google account. Tag `v0.2.0`.
