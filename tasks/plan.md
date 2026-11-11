# Implementation Plan: E19 · UI review → `v1.x` (proposed `v1.10.0`)

> **Status: PROPOSED by the epic-planner, 2026-11-11; needs the user's OK on the breakdown PR and on the checkpoints (Q-E19-1).** Tasks T-277…T-284; two 👤 checkpoints (E19-A after the tools, E19-B at the end). No BLOCKING question. The E18 plan that lived here is in git history (E18 closed, `v1.9.0`; `tasks/todo.md` E18 section).

## Overview
E19 first builds the tools to see the whole app without installing it, then reviews all the UI with the user, area by area. The tools are a screen gallery (Roborazzi images from the Robolectric host tests with fake data: every screen, state, sheet and dialog, grouped by area in one index page, with a check that fails when a screen has no entry) and a rich dummy user on the emulator (local flavor, Docker Supabase, one command). Then four review rounds (Diario, Entreno, Trainer, Perfil): ux-reviewer proposes findings, the user decides each one in chat, every accepted change goes into `05` first and is swept across every screen with the pattern. Nothing changes what the app does.

Sources: `planning/epics/E19-ui-review.md` (intent, non-goals, US-131, US-123), ADR-103, ADR-27, ADR-40, ADR-80, ADR-2/ADR-14 (local flavor, "Test login"), `05` §6, §8-§10, `06` (screenshot tests), `08` §3.2 (seed user), `02` §5. Tasks: `tasks/todo.md` (E19 section).

Out of scope (epic non-goals, `01-scope.md`): changing behaviour or adding features; a second UI-automation harness (ADR-27; no Maestro, no `@Preview` fixtures); rebuilding screens from scratch; re-proposing anything in `05` §8 "Decided - do not re-propose".

## Architecture decisions
- **Roborazzi is the tool (epic 2026-11-11; supersedes ADR-103's "spike" bullet, recorded by T-277).** It is already in the repo and runs on the Robolectric host tests, so the gallery needs no device, backend, account or secret.
- **Gallery images are generated, not goldens.** The gallery command writes images and the index to a gitignored build folder and CI uploads them as an artifact (`android-ci.yml` already uploads `**/build/outputs/roborazzi/`). They are not verified against committed PNGs, so a UI change in a round does not require re-recording 100+ images; the existing designsystem goldens are kept. (The T-277 planner confirms; recorded in its PR.)
- **Screens are rendered from fixed fake UI states** through their stateless content composables, with a fixed clock and `es-ES`. Where a screen has no stateless split, the planner chooses the existing host-test fakes or a pure stateless extraction (no behaviour change, existing tests green).
- **The missing-screen check reads the navigation routes** (`app/AppRoutes.kt`, `RootRoute.kt`) and fails naming each route without a gallery entry; the debug component catalog is exempt by name. T-277 carries an explicit pending list (Entreno and Trainer) that T-278 empties; the list cannot grow.
- **The rich dummy user is a separate local-only dataset** applied by the live-flow command after `supabase db reset`, with dates relative to today; `seed.sql` (the minimal test user) and the pgTAP tests that rely on it stay unchanged; production never receives it (`deploy-supabase` runs `db push` without seed). The planner of T-279 confirms the form.
- **Rounds change the design system first.** Each accepted finding is written into `05` (§6/§8/§9/§10) in the round's first commit, then swept app-wide (R-39); a rule that can be checked becomes a guard in `check-ux-conventions.sh`.

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR. There is no S task.

| Order | Task | Size | Story | Flow | Agent runs |
|---|---|---|---|---|---|
| 1 | T-277: Screen gallery, part 1: harness, index, missing-screen check, Diario and Perfil | **L** | US-131 | UI (host tests) | 6 |
| 2 | T-278: Screen gallery, part 2: Entreno and Trainer | **L** | US-131 | UI (host tests) | 6 |
| 3 | T-279: A rich dummy user and one command for the live flow on the emulator | **M** | US-131 | backend + script | 5 |
| — | **👤 Checkpoint E19-A** (proposed; Q-E19-1; 7 items) | — | | | |
| 4 | T-280: Review round 1: Diario | **M** (estimate) | US-123 | review + UI | 7 + review |
| 5 | T-281: Review round 2: Entreno | **M** (estimate) | US-123 | review + UI | 7 + review |
| 6 | T-282: Review round 3: Trainer, with "Lo que el entrenador sabe de ti" | **M** (estimate) | US-123 | review + UI | 7 + review |
| 7 | T-283: Review round 4: Perfil | **M** (estimate) | US-123 | review + UI | 7 + review |
| — | **👤 Checkpoint E19-B** (final regression; 7 items) | — | | | |
| 8 | T-284: E19 closure | closure step (no size class) | DoD | PM | PM only |

Why these splits and merges:
- **US-131 → T-277 + T-278 + T-279.** The gallery alone covers about 55 screens plus their sheets and dialogs in several states, far above one reviewable PR. It is split by review area (harness and two areas, then the other two), not by component; the harness, index and check are built once in T-277. The emulator dataset and command are a different domain (SQL and a script, reviewed apart) and about 6-8 files, so they are an M task of their own.
- **US-123 → one task per area (T-280…T-283).** The story itself defines the rounds by area and requires the user's decision before each fix batch, so a round is the natural vertical slice. Sizes are estimates: the content is known only after the user decides the findings. Above ~15 files a round's planner splits it into batches with new IDs (designsystem and rules first, then the sweep); a round with very few accepted findings (S) merges into the next one instead of shipping alone.
- **Closure T-284** is released by Checkpoint E19-B's approval; no checkpoint of its own, no size class.

## Phases and checkpoints

Items to test in the epic, collected first: (1) the gallery command runs without a phone, account or secret; (2) the index groups images by area and opens them; (3) states, sheets and dialogs are shown for known screens; (4) no screen the user uses is missing; (5) the live-flow command starts the backend with the dummy data and installs the app; (6) the dummy user has history, sessions, a plan and block, reports and notices; (7) leave and come back on the emulator; (8)-(11) each area's accepted changes on the phone, working as before (Diario, Entreno, Trainer with the free-text decision, Perfil); (12) the same pattern looks the same across tabs; (13) data and behaviour unchanged; (14) leave and come back on the phone (final regression). Fourteen items in two functionalities: "see every screen without the phone" (1-7) and "the reviewed app" (8-14) → two checkpoints of 7. The rounds have no gate between them (US-123 "Early feedback, no gates"): the user's feedback during the rounds is the per-round decision in chat and the updated images after each merge.

### Phase 1: The review tools (US-131)
- T-277 → T-278 → T-279 → **👤 Checkpoint E19-A**.
- **👤 Checkpoint E19-A — See every screen without the phone** (proposed; 7 items in `tasks/todo.md`; tried on the computer and the emulator, no phone). Try: run the gallery command and open the index; browse a few screens you know and check their empty, loading, error states and sheets; tell us any screen that is missing; run the live-flow command, log in with "Test login" on the emulator and see weeks of diary and weight, finished sessions, a plan and block, reports and notices; leave the app and come back on the emulator. Placed here so the gallery can be fixed before every round depends on it (Q-E19-1).
- Internal milestones (task verification items, not checkpoints): the gallery artifact attached to T-277's and T-278's CI runs; the missing-screen check green with an empty pending list after T-278; the dataset pgTAP test green after T-279.

### Phase 2: The review rounds (US-123)
- T-280 (Diario) → T-281 (Entreno) → T-282 (Trainer) → T-283 (Perfil) → **👤 Checkpoint E19-B** → T-284 (closure).
- Each round: ux-reviewer findings from the gallery → the user decides each in chat → `05` first → planner → fix batch with the app-wide sweep → updated images sent to the user (no gate).
- **👤 Checkpoint E19-B — The reviewed app on the phone, with the final E19 regression** (7 items in `tasks/todo.md`; its approval releases T-284). Before presenting it, the PM lists under each item the accepted changes of that round. Try on the phone: each tab's changes are there and the main journeys work as before (add a food, run a short session, open "Tu plan", "Tu bloque" and "Informes", save a setting); the same sheets and dialogs look the same across tabs; your data, totals, plan and block are unchanged; leave the app and come back on each tab with a draft open.

## Dependencies
- T-277 needs the breakdown approved. T-278 needs T-277 merged. T-279 needs T-278 merged (no build dependency; one task at a time, and the dataset can cover every screen the gallery lists).
- T-280 needs Checkpoint E19-A approved (or T-279 merged if the user drops E19-A). T-281 needs T-280, T-282 needs T-281, T-283 needs T-282 merged (each round reviews the gallery updated by the previous one).
- T-284 needs Checkpoint E19-B approved.
- Built pieces E19 relies on: Roborazzi and the host-test setup (`composeApp/build.gradle.kts`, `06` "Screenshot tests"), the CI artifact upload, the `local` flavor and "Test login" (ADR-2, ADR-14, `08` §3.2), the existing host-test fakes, `check-ux-conventions.sh`, every screen built through E18.

## Risks
- **Gallery effort per screen (T-277, T-278).** Screens without a stateless content composable need fakes or a refactor. Mitigation: the planner picks per screen; any extraction is a pure refactor checked by the existing host tests; the area split keeps each PR reviewable.
- **Missed routes or sheets.** Sheets and dialogs are not routes, so the check cannot see all of them. Mitigation: ux-reviewer lists each screen's sheets and dialogs in the T-277/T-278 plans; Checkpoint E19-A item 4 asks the user for anything missing.
- **Robolectric rendering differs from the phone** (fonts, insets, the system bars). Mitigation: the emulator flow covers real rendering; Checkpoint E19-B is on the phone.
- **Dataset drift (T-279).** A rich dataset written by hand can disagree with the coach readers or break as migrations change. Mitigation: it goes through the app's tables and RPCs where possible, dates are relative, and a pgTAP test runs it after every reset in CI.
- **A round grows (T-280…T-283).** One accepted rule can touch many screens. Mitigation: designsystem first, sweep by search, split into batches with new IDs above ~15 files.
- **A look change that changes behaviour.** Mitigation: the non-goal is a common acceptance criterion; existing host tests are adjusted only for testTags or layout, and pr-reviewer rejects behaviour changes.
- **No gate between rounds.** A wrong rule could spread before the phone checkpoint. Mitigation: the user decides every finding before it is built and sees the updated images after each merge; comments become findings of the next round or fix tasks.

## Open questions
- **Q-E19-1 (needs the user's OK, not blocking the first task): Checkpoint E19-A.** The epic says "no 👤 checkpoint gates" for the rounds. The planner proposes one checkpoint after the tools (T-279) and before round 1, because every round depends on the gallery being complete and right, and one at the end on the phone (E19-B, the final regression that releases the closure), with no gate between rounds. Alternative: drop E19-A and present the tools to the user only as the first images of round 1.
- **Q-E19-2 (product decision, taken in round 3, not blocking the breakdown): "Lo que el entrenador sabe de ti".** Remove the screen and its entry, hide it until a model feature exists, or fix the copy so it does not claim the coach reads the notes. In every option the stored notes are kept (deleting data is a separate decision).
- **Q-E19-3 (non-blocking, delegated to ux-reviewer in the T-277 plan):** which area owns the shared screens (Centro de avisos, "Informes", the food search reached from Diario and the magnifiers) and the exact list of sheets and dialogs per screen.
