# Implementation Plan: E15 · Real life and goal switching → `v1.x` (proposed `v1.6.0`)

> **Status: PROPOSED by the epic-planner, 2026-11-08; E15 Q1-Q5 resolved by the user the same day (below); the final task list (T-239…T-244) and the two checkpoints (E15-A, E15-B) need the user's OK** before the breakdown PR merges and before T-239 starts. No question blocks a task. The E14 plan that lived here is in `tasks/phase2/plan.md` (E14 closed, `v1.5.0`).

## Overview
After any gap the user opens the app and sees what happened to the block and what to do, without repairing anything by hand; a finished block waits in "Centro de avisos"; and the user switches goal (or adapts to any life change) with one entry, "Bloque nuevo", which opens the pre-filled builder and applies the new goal's figures only when the new block is saved. Everything reuses rules that exist: plan weeks that follow the training (`04` §9.9 amended), the 14-day return rule (`07` §12.2, `04` §4.14), discomfort levels and the swap and note of `07` §7, the end-of-block review and the next-block builder of E14 (T-233…T-235), and the deterministic nutrition path (ADR-98). No language model anywhere; "Uso de IA" never changes.

Sources: `planning/epics/E15-real-life-and-goal-switching.md` (intent, non-goals, US-106…US-111), `planning/01-scope.md` (Phase 3, ADR-101), `planning/00-decisions.md` (ADR-101; ADR-73, ADR-88, ADR-89, ADR-98, ADR-99 in the Phase 2 index), `planning/09-open-questions.md` (E13 Q52 and Q55 resolutions), `07` §4, §7, §12.2-§12.5, §12.8, `04` §4.12, §4.13, §4.14, §6, §8.5, §8.9, §8.10, §9.9, §9.10, §9.15-§9.17, `05` §6.2, §8-§10. Tasks: `tasks/todo.md` (E15 section).

Out of scope (epic non-goals, `01-scope.md` §4 and E15 Q3): "Recuperar" or any maintenance bridge; injury, illness or travel rules; the 3-step graded return after a severe discomfort; "Adaptar el bloque" and "Solo unos días"; Android system notifications (ADR-73); a minimum phase length or a limit on switches; an optional maintenance block between a cut and a gain; "¿Seguimos?" after 6 blocks; a goal picker inside the Trainer (E15 Q2).

## Architecture decisions
- **"Bloque nuevo" is an early review that opens the T-235 builder (T-239, T-240).** It reads the same frozen review facts and the same `04` §9.17 pre-fill as the end-of-block path, but before the block ends and without ending it; the current block, its weeks and the accepted plan keep running until "Guardar bloque". It is always on the Trainer during a block and replaces "Crear plan nuevo" (E15 Q1): one entry for every life change (E13 Q55), also the action of the welcome-back notice.
- **The goal is still picked in "Mi entrenamiento" (E15 Q2).** The Trainer gets a button that opens the existing "Mi entrenamiento" editor; saving the intention there changes nothing in the plan (`04` §4.13); when the saved intention differs from the plan's, "Bloque nuevo" shows the calorie confirmation. US-110 "I choose a different goal" is that pick.
- **A goal switch is a proposed plan saved with the block.** The new intention's figures come from the deterministic nutrition path (a sibling of `plan_phase_change` / `plan_recompute`, no model); they are stored as a **proposed** plan and accepted in the same save that replaces the block, so "nothing changes until I save" holds in one transaction. A cut → gain starts at the measured maintenance plus the gain rate in one step (`07` §4 "After a cut"); no bridge.
- **Counters are scoped to the plan and the block.** The calorie band and the "Ahora no" count already read since `block_start`; the 14-day clock (`recent_change`) is scoped to the plan (folds in the carried follow-up), so a new block with a new goal starts clean. The cut-limit count restarts only after 4+ consecutive counted non-deficit weeks (`07` §12.9 rule 4, `coach_phase_history`).
- **Plan weeks follow the training (T-242).** One week-mapping helper replaces the calendar arithmetic (`block_start + 7(n-1)`) in every reader: plan weeks, the link trigger, the block end, deloads, the diet break, the weekly summary, the review and weeks in phase. Existing blocks are re-derived in the migration so done sessions stay done. A paused week's summary reads only "Semana en pausa" with no notice; nutrition is not paused: calorie proposals keep their own data gate (E15 Q4; `04` §6.7 rewritten in T-242).
- **Notices stay in the app (ADR-73).** The block-end alert is one more "Centro de avisos" kind computed on open with its own switch in Preferencias → "Avisos"; welcome back is a Trainer card computed on open from the last completed session. No background job, no system notification.
- **Severe discomfort: a proposal, then a note (T-241).** The swap is offered and applied only on tap, for the rest of the block, inside the current equipment and the same movement pattern; the 14-day note is the first allowed health line (R-52, ADR-89), triggered by levels and dates only. No graded return (E15 Q3).
- **No new designsystem component is planned.** Each UI task uses `CoachProposalCard`, the Trainer card pattern, `AppPickerSheet` + `ExerciseRow`, `ExerciseStatusRow`/`InlineNote`, the notice row of `NotificationCenterScreen`, the Avisos switch row, the "Mi entrenamiento" editor and the T-235 builder screens. If ux-reviewer asks for a new one, it is built in the first task that needs it and retires its ad-hoc copies (R-39).

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR; no S task.

| Order | Task | Story | Flow | Size | Agent runs |
|---|---|---|---|---|---|
| 1 | T-239: "Bloque nuevo" and goal switching (backend) | US-110, US-111 | backend + core | L | 5 |
| 2 | T-240: "Bloque nuevo" in the app: entry, goal button, confirmation, builder, weeks in phase | US-110, US-111 | core + UI | M | 7 |
| 3 | T-242: A missed week pauses the block | US-106 | backend + core + UI | L | 8 |
| 4 | T-243: Welcome back and the block-end notice | US-107, US-109 | core + UI (+ SQL if needed) | M | 7 |
| 5 | T-241: Discomfort follow-up: swap offer and the 14-day note (+ final no-model regression) | US-108 | backend + core + UI | M | 8 |
| 6 | T-244: E15 closure | DoD | PM | closure step | PM only |

Why these splits and merges:
- **US-110 + US-111 → T-239 (backend, L) → T-240 (UI, M).** US-111 is the save of the same new block (counter scoping, the cut count) plus one fact line, so it rides with US-110. The backend alone is about 12 files (early review, goal preview kind, save that accepts the plan and replaces the block, counter scoping, retiring "Crear plan nuevo" during a block, pgTAP and Deno tests); the UI is about 12 files reusing the T-235 builder and the "Mi entrenamiento" editor; reviewable apart.
- **US-106 → T-242 (L), not split:** the backend is heavy (every week reader changes), the UI is two labels ("Semana en pausa", the moved end date); splitting would leave a UI task of S size.
- **US-107 + US-109 → T-243 (M):** each alone is S (a Trainer card from the last session date; one notice kind and its switch). Both are "coming back to the app after an absence" and touch the same notice and Trainer code.
- **US-108 → T-241 (M), one vertical slice, last:** two small server pieces (swap RPC, note trigger and dismissal) and two UI pieces (the Trainer offer with its picker, the note row). It goes last because its swap and level 1-3 behaviour can be tried at once, so the last checkpoint (with the final regression) sits right after the last implementation task with items the user can test (E15 Q5); T-241 carries the no-model regression test.
- **Closure T-244** is the PM closure step released by Checkpoint E15-B's approval; it has no checkpoint of its own.
- **Order differs from the epic file:** goal switching first because "Bloque nuevo" is the welcome-back action (US-107) and the shared entry of the epic; the pause before welcome back and the block-end notice because it moves the block end they read; the discomfort follow-up last (above).

## Phases and checkpoints

Items to test in the epic, collected first, then split by whether they can be tried on demand (E15 Q5: the user does not force time-based triggers).
- **On demand (checkpoint items):** the Trainer shows "Bloque nuevo" and not "Crear plan nuevo"; the goal button opens "Mi entrenamiento"; the calorie confirmation line and "nothing changes until saved"; saving with the new figures and no bridge; days change → pre-filled builder; weeks in phase; discomfort 1-3 (no raise, exercise stays); discomfort 4-5 (no suggestion, the swap offer); the swap list and applying it, "Ahora no"; "Ya no me molesta" removes the offer; "Uso de IA" unchanged; leave the app and come back. 12 items incl. two restoration items → two checkpoints of 6, each one functionality.
- **Natural triggers (not gates; "Pending natural-trigger feedback (E15)" in `tasks/todo.md`):** a paused week (summary, Trainer, end date, nutrition unchanged); resuming after the pause; welcome back after 14+ days, lighter first loads, and the notice going away; the block-end notice (listed, count, opens the review, stays until the next block, silencing); the 14-day discomfort note; the counters restarting after a goal switch. Covered by host tests and seeded pgTAP; the user reports from the main account when each happens; each report becomes a `fix(T-###)` task.

### Phase 1: "Bloque nuevo" and switching goal (US-110, US-111)
- T-239 → T-240.
- **👤 Checkpoint E15-A — "Bloque nuevo" and switching goal** (after T-240; 6 items in `tasks/todo.md`). Try: on Trainer see "Bloque nuevo" (no "Crear plan nuevo") and open "Mi entrenamiento" from the goal button; change your goal, tap "Bloque nuevo", read the calorie line, go back and see nothing changed; save the new block (new phase, calories, protein, outcomes; cut → gain in one step; no maintenance block offered); change only the days per week and open the pre-filled builder; read "Llevas {n} semanas en esta fase"; leave the app and come back mid-flow. Placed before T-242, which re-counts the weeks the new block uses.

### Phase 2: Time away (US-106, US-107, US-109)
- T-242 → T-243. No checkpoint: every user-visible item is time-based (E15 Q5) and goes to the natural-trigger list; internal milestones (`deploy-supabase` green with data intact, CI green) are verification items in the tasks.

### Phase 3: Training around a discomfort (US-108), final regression and closure
- T-241 → T-244.
- **👤 Checkpoint E15-B — Training around a discomfort, and the final E15 regression** (after T-241, the last implementation task; 6 items in `tasks/todo.md`; its approval releases T-244). Try: log a level 1-3 discomfort and see the exercise stay with no raise; log a level 4-5 one and see no suggestion and the swap offer; open "Cambiar", see only same-movement exercises in your equipment, pick one and see it in the routines and the next session, and "Ahora no" hide another offer; "Ya no me molesta" removes the offer; "Uso de IA" did not change and the E15-A flows still work after the pause change; leave the app and come back.

Internal milestones are verification items in the tasks, not checkpoints: `deploy-supabase` green on `main` with data intact after T-239, T-242 and T-241 (and T-243 if it adds SQL); CI green on every PR.

## Dependencies
- **Before T-239 (PM step, DONE 2026-11-08, `07` §12.9):** `fitness-scientist` records the 2026-11-08 goal-switching evidence in `07` (immediate switch, no bridge, no minimum length; consecutive cut count; no limit on weeks in phase), with the sources opened in its session; the epic says it "is recorded in `07` … before any task uses" it, and `07` has no 2026-11-08 entry yet.
- T-239 needs the breakdown approved; T-240 needs T-239 merged and deployed; T-242 needs Checkpoint E15-A approved; T-243 needs T-242 merged; T-241 needs T-243 merged; T-244 needs Checkpoint E15-B approved (the natural-trigger items do not gate it).
- Built pieces E15 relies on: T-227 (return rule after 14 days, discomfort keeps the load), T-231 (weekly summary and its notice), T-233/T-234 (review, `coach_block_end`, the Trainer review card), T-235 (next-block builder, `save_built_coach_block`), T-187 (`coach_phase_history`), T-209 (`coach_user_equipment()`), T-152 (discomfort levels), T-149/T-154 ("Mi entrenamiento").

## Risks
- **Re-counting weeks in production (T-242).** The user's current block was linked with calendar weeks; re-deriving plan weeks can move done sessions, the deload week, the diet-break week and the block end. Mitigation: pgTAP with a block created by the E13/E14 functions before the migration; `deploy-supabase` must finish green with data intact before Checkpoint E15-B is presented; the planner writes the exact before/after for the user's current block in the PR body.
- **Many readers of the week (T-242).** Missing one reader (for example the summary or the review) leaves two week numberings in the app. Mitigation: one helper and a grep of every `block_start + 7` in the migrations, listed in the plan; the existing E14 week tests must stay green.
- **The "Bloque nuevo" save is two writes (T-239).** Accepting the proposed plan and replacing the block must be one transaction, or a failed save leaves new calories with the old block. Mitigation: one RPC, pgTAP on a failed save.
- **Time-based features ship without a hand test (Q5).** Pause, welcome back, block-end notice, the 14-day note and the counter restart are only seen when they happen in real use. Mitigation: host tests with an injected `Clock` and seeded pgTAP cover every trigger and boundary (6/13/14 days, one and two paused weeks, the day after the block end); the natural-trigger list makes each one a named item for the user's report.
- **The first allowed health line (T-241).** `HealthDisclaimerSweepTest` has an empty allow-list; the note must be allow-listed with its trigger, not as a generic line (R-52).
- **No dialog for the goal confirmation (R-08).** The confirmation must be a screen step or a footer line; ux-reviewer fixes it in the plan pass.

## Open questions (all resolved by the user, 2026-11-08)
- **Q1 — resolved: A.** "Bloque nuevo" is always on the Trainer during a block and replaces "Crear plan nuevo"; a new goal's plan only comes with a new block (T-239 (7), T-240).
- **Q2 — resolved: changed by the user.** The Trainer gets a button to change the goal that opens "Mi entrenamiento"; the goal is still picked there, with no goal picker inside the Trainer. US-110 "I choose a different goal" is that pick (T-239 (2), T-240).
- **Q3 — resolved: B.** The 3-step graded return stays out of US-108: the swap lasts for the rest of the block; `07` §7 keeps the return documented and unbuilt (T-241).
- **Q4 — resolved: (a) A, (b) A.** A paused week's summary shows only "Semana en pausa", with no "Centro de avisos" item; calorie proposals keep their own data gate (no pause reason). T-242 rewrites `04` §6.7.
- **Q5 — resolved: changed by the user.** The user does not force time-based triggers. Checkpoints hold only what can be tried right away; every item that needs time to pass is in "Pending natural-trigger feedback (E15)" in `tasks/todo.md`, reported from the main account when it happens, not a gate.
