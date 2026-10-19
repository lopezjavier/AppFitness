# Implementation Plan: E2 · Authentication and profile → `v0.2.0`

## Overview
Each person signs in with Google, completes a 4-step onboarding (3 biometric steps + goals, "N / 4" since T-039), picks an energy strategy with a calorie target, and later edits their data, strategy and macro split on Profile. E2 is done when a new user can sign in, complete onboarding, set goals, close the app and get straight back in; `profiles`, `body_measurements` and `nutrition_goals` have pgTAP isolation tests; and the user validates the `prodDebug` APK with their real Google account.

Sources: `planning/epics/E2-auth-profile.md` (US-05…US-08), `planning/03-data-model.md` §2 and §4, `planning/04-business-rules.md` §1 and §5, `planning/02-architecture.md`, `planning/05-design-system.md`, mockup `design/stitch/profile-goals/screen.png` (personal data, "Estrategia Energética" and "Distribución de Macros" only). Tasks: `tasks/todo.md` (T-034…T-039). Earlier plans (E0, E1) are in the git history.

Sizing (E1 retrospective): one task per story, as a vertical slice, so each task pays the 6–7 agent hand-offs once. 4 implementation tasks + closure, 2 👤 checkpoints.

## Architecture Decisions
- **ADR-24 (user, 2026-10-14) settles the product gaps found in the breakdown:** onboarding resumes prefilled and re-saving updates the onboarding weight row; onboarding saves the default macros 30/45/25 (no sliders); Profile has one "Guardar" that stores profile, strategy, macros and the recalculated target; "Peso actual" is read-only on Profile until E4. The E2 Gherkin, `03` §4 and `04` §1.4 were updated to match.
- **Otherwise existing ADRs cover E2.** ADR-02 (Credential Manager → ID token → `signInWith(IDToken)`), ADR-17 (publishable key), ADR-19 (`androidApp` + `composeApp` + `shared`, flavor values via `AppConfig`) and ADR-22 (Profile as a full screen from the avatar, no FAB) cover E2.
- **ADR-26 (user, Checkpoint E2-A, 2026-10-14):** Perfil becomes the 4th tab (Diario, Entreno, Progreso, Perfil) with the user's Google photo as its icon; the top-bar avatar is removed. Supersedes ADR-22's navigation part; implemented in T-037.
- **Checkpoint E2-A corrections → T-039 (before T-037):** onboarding regrouped into 4 steps ("N / 4", goals counted), unit-label and weight-decimal picker bugs, activity card typography, a filled non-outlined secondary button for "Atrás", and the PR #50 review follow-ups.
- **E7 · UI refinement** (placeholder) is added as the last Phase 1 epic (`planning/07-roadmap.md` §1.1); deferred UI details from checkpoints go there.
- **Root routing is a pure function** of (session status, `onboarding_completed`): Welcome / Onboarding / Main. Welcome and Onboarding are full-screen destinations above the tab graphs (no bottom bar, no FAB). Profile is the 4th tab since ADR-26 (T-037). A loading state avoids flashing Welcome on cold start with a persisted session.
- **`profiles` already has the biometric columns** (T-018 created the full §2 table and column grants), so US-06 needs no `profiles` migration; its migration is `body_measurements` only. "At most one migration per story" (02 §6): T-035 `body_measurements`, T-036 `nutrition_goals` (macro columns included, defaults 30/45/25). T-037 needs no migration; if the planner wants an atomic Profile save (profile + goals), a `security invoker` RPC would be T-037's only migration.
- **`onboarding_completed = true` only with goals saved** (US-06 note). T-036 writes the goals first and the flag second (or one RPC, planner's choice), so a failure never leaves a "completed" user without goals.
- **"Test login" uses the existing `AppFeatures` flavor mechanism** (as the catalog entry); a `prod` unit test asserts it is absent.
- **Supabase client and auth live in `shared`** (data layer, behind `AuthRepository`); the Google credential provider is an `androidMain` platform implementation of the `AuthCredentialProvider` port. UI never touches supabase-kt types.
- **New design-system pieces are built inside the first story that needs them** (no component tasks): `WheelPicker` in T-035, `MacroStackedBar` in T-037; both get catalog entries and host tests.
- **Formulas live in `shared/domain/usecase/`** with the `04-business-rules.md` examples as tests: `CalculateTdee`, `CalculateCalorieTarget` (T-036), `BalanceMacros` + gram targets (T-037, property tests). Numbers use `AppNumberFormat` ("2.681").
- **US-06 "Editing data after onboarding" moves to T-037**: it asserts the TDEE, which only exists after US-07.
- **US-08 "And the Diary uses the new gram targets"** is verified on the Profile/domain side in E2; the Diary half lands in E3 (note added to "Notes for later epics").
- **Notes for later epics:** none targets E2. The "E2 (food diary)" note from the PR #24 review concerns `MacroProgressBar`/`ProgressRing` in the Diary, i.e. E3; left in place.

## Task List

### Phase 1: New user journey (US-05, US-06, US-07)
- [ ] T-034: Sign in with Google, session routing and sign out (US-05) — UI, frontend
- [ ] T-035: Biometric onboarding (US-06) — UI + backend (`body_measurements`)
- [ ] T-036: Energy strategy and calorie target; onboarding completes (US-07) — UI + backend (`nutrition_goals`)

### Checkpoint E2-A 👤: New user journey on the phone
- [ ] Real Google sign-in on `prodDebug`, onboarding, strategy, save → Diario; reopen → straight in; cancelled picker; offline message; sign out. Placed here because Google sign-in against production is the riskiest part of the epic and the onboarding/strategy UX is the decision most worth early feedback.

### Checkpoint E2-A corrections
- [ ] T-039: Onboarding in 4 steps, picker fixes, secondary button, PR #50 follow-ups — UI (frontend only)

### Phase 2: Profile (US-08 + US-06/US-07 on Profile)
- [ ] T-037: Profile: edit personal data, energy strategy and macro split, one "Guardar"; Perfil as the 4th tab with the Google photo (ADR-26) — UI (backend only for an optional atomic-save RPC)

### Checkpoint E2-B 👤: Epic closure
- [ ] T-038: E2 closure — edit data and goals on Profile, auto-balanced macros, persistence across restarts; tag `v0.2.0` and retrospective.

Internal milestones (not 👤): `deploy-supabase` green after T-035 and T-036; pgTAP isolation for the three tables green in `supabase-ci`.

## Dependencies

```
E1 (v0.1.0) ─► T-034 ─► T-035 ─► T-036 ─► [E2-A 👤] ─► T-039 ─► T-037 ─► T-038 [E2-B 👤]
```
- Strictly sequential: each task builds on the previous one's routing, repositories and screens.
- The backend half of T-035/T-036 (migration + pgTAP) and the domain use cases are decoupled from the UI, so backend-developer and frontend-developer can work in parallel inside each task once the planner fixes the repository contract.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Google sign-in fails on the real device (SHA-1, client IDs, nonce, `serverClientId`) | High | Configuration from T-011/T-012 is already verified; T-034 maps every Credential Manager error to an explicit outcome and logs the cause. Checkpoint E2-A comes right after the onboarding journey, the first point where the user can test it; if the developer cannot sign in with Google on the emulator (local flavor with `GOOGLE_WEB_CLIENT_ID` set), the PM may ask the user for a quick `prodDebug` sign-in check after T-034 instead of waiting. |
| supabase-kt version vs `sb_publishable_` keys, Ktor engine, Kotlin 2.4 | Medium | Pin the latest stable in T-034 and check the release notes (ADR-17); a host test builds the client from `AppConfig`. |
| Wheel pickers in Compose Multiplatform (no stock component) | Medium | `WheelPicker` built on `LazyColumn` + snap fling in T-035 with host tests for bounds and semantics; ux-reviewer checks it against `05-design-system.md`. |
| Large slices (T-034, T-035, T-036 are L) | Medium | Each is one story; backend and UI halves can run in parallel inside the task. If a plan exceeds ~15 non-trivial files, the planner may split it into a backend task followed by a UI task (never per component). |
| Partial writes (profile saved, measurement or goals not; on Profile, profile saved but goals not) | Medium | Write order chosen so the onboarding flag is set last; Profile's single save writes profile then goals (or one RPC); save errors keep the typed values and show a message-only snackbar, and the user taps the save button again (no separate "Reintentar", Checkpoint E2-A feedback). |
| Re-saving onboarding must update, not duplicate, the weight row (ADR-24) | Low | T-035's plan fixes how the onboarding row is identified (e.g. the earliest `manual` row of the user) and covers the update with a repository test. |
| Haptics and the account picker cannot be checked automatically | Low | Host tests for the effects; manual check at Checkpoint E2-A. |

## Open Questions
None open. Resolved by the user on 2026-10-14 (recorded in ADR-24):
- **Q1 · Resuming onboarding (T-035):** steps are prefilled with the saved values; finishing step 5 again updates the existing onboarding weight entry (no duplicate row).
- **Q2 · Macros in onboarding (T-036):** no sliders in onboarding; the default split 30/45/25 is saved and adjusted later on Profile.
- **Q3 · Saving on Profile (T-037):** one "Guardar" button for the whole screen; saving recalculates and stores the daily target from the new data (`04` §1.4).
- **Q4 · Weight on Profile (T-037):** "Peso actual" is read-only (latest weight); editing arrives with US-15 in E4.
