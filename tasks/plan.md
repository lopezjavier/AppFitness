# Implementation Plan: E14 · Block follow-up: the happy path → `v1.x` (proposed `v1.5.0`)

> **Status: APPROVED by the user 2026-11-05 (epic-planner breakdown).** Stories US-97…US-105 (written into `planning/epics/E14-block-follow-up.md` by this breakdown), tasks T-227…T-236. **User decisions 2026-11-05:** E14 Q1-Q5 resolved (below); **no 👤 checkpoints in E14**: the user tests with real data during the following month and reports feedback and errors when the triggers fire naturally, and each report becomes a `fix(T-###)` task. No question blocks a task. The breakdown PR still needs the user's OK before it merges and before T-227 starts. The E13 plan that lived here is in git history (E13 closed, `v1.4.0`, `retrospectives/E13.md`).

## Overview
Follow the user through one block the way an evidence-based trainer would when nothing goes wrong: loads progress on their own with "Deshacer" (and stop for the user's choice when the step is too big or there is no range), calories are proposed and applied only on "Aplicar", deloads come on their own with "Cancelar descarga", a weekly summary from fixed templates explains the week, "¿Por qué no progreso?" names the first cause of a stall from the user's own data, and at the end of the block a review shows what happened, proposes a phase change when the data calls for it, and opens the builder pre-filled for the next block. Everything is deterministic (SQL or shared Kotlin); no language model, no `coach` Edge Function call, "Uso de IA" never changes.

Sources: `planning/epics/E14-block-follow-up.md` (scope, D1-D5, stories), `planning/07-coach-design.md` §3 "Block follow-up on the happy path" (1)-(8) with refs 126-134, §3 "Progression", "Failed sessions", "Deload", §4 (calorie proposal, band, data cleaning), §12.3-§12.4, §9 (E14 line), rules 16 (deterministic first) and 17 (recorded inputs only); ADR-88 (amended 2026-11-05: calories need approval), ADR-73, ADR-94, ADR-98, ADR-99, ADR-40; `04` §4.14, §8.5, §9.9, §9.10, §9.15; `05` §8-§10. Tasks: `tasks/todo.md` (E14 section). No "Notes for later epics" item targets E14. User decisions applied: the sleep check runs last; a load jump over 10 % pauses until the user picks; calorie changes need the user's approval.

Out of scope (epic): breaks and paused weeks, injuries and the graded return, life changes ("Revisar el bloque", "Solo unos días"), every model feature (report text, chat, evaluation, history and deletion), set-by-set load changes, periodisation between blocks, pump/soreness/readiness questions, peer strength scores, the early review ("Bloque nuevo", E14 Q1) and "¿Cómo te ha ido?" (E14 Q2). The nice-to-have "¿Seguimos?" after 6 blocks is not planned.

## Architecture decisions
- **Existing data is picked up (user question, 2026-11-05).** A block saved by the E13 builder (`save_built_coach_block`), its plan weeks (T-165), the nutrition plan and the sessions logged before E14 deploys are all valid inputs; no task requires creating a new block or plan after E14. Each task acts on events after its deploy (a session completed, a week ending, a block ending), so sessions logged earlier get no retroactive change. Every backend task's pgTAP includes a block and plan created with the E13 functions before the migration.
- **One decision per finished session, on the server (T-227).** When a block session completes, SQL reuses the T-159 double-progression decision and the user's server-side calculator settings (plates, handle) for the step, and writes the planned weight (and `rep_min` on a raise) to every routine of the block folder holding that exercise with the same range (`07` §3 "Progression"; E13 Q44 A). ADR-70's update runs first. Outside a block nothing is written (the live suggestion stays).
- **One table of applied coach changes, one row component.** A `coach_changes`-style table (before/after values, reason code + figures, status applied / undone / pending / decided) for load raises, drops, flags (T-227) and deloads (T-230); one undo RPC; RLS by owner, cascade on account deletion (ADR-29). One designsystem `CoachChangeRow` built in T-228 and reused by T-230 and T-231 (R-39). Reasons are codes; the Spanish text is the client's (`07` templates; the server holds no Spanish).
- **Proposals stay proposals.** The calorie change uses the existing recommendation path and `CoachProposalCard` with "Aplicar" / "Ahora no" (T-229); the user-typed "edited" decision is retired because `07` §4 gives only those two actions. The phase change uses the same card with "Aceptar" / "Seguir como estoy" (T-234).
- **Weekly notice inside the app only (ADR-73).** The weekly summary's "one notification" (D5) is one "Centro de avisos" item computed on open, with a stored "seen" state so it is listed once; no system notification.
- **One stall function.** "¿Por qué no progreso?" (T-232) is one deterministic function (SQL or shared Kotlin, one implementation), read by the weekly summary and the review.
- **The review is a read plus stored decisions (T-233).** Review inputs are computed from the logs; phase triggers are evaluated only at the review (`07` §12.3) from `coach_phase_history`; the decisions are stored per block. The next phase's calories come from the deterministic nutrition path (ADR-98), never a model.
- **The next block is the T-221 builder with targets (T-235).** Pre-fill from the last block (D3); per-group weekly targets replace the fixed 10 ("Priorizar" / "Mantener", freed sets, cut, ≤ 20, ≤ 3 groups); the stalled-group "+2" is a tap-to-apply suggestion (D2). Saving goes through `save_built_coach_block`.
- **Weeks are calendar plan weeks (`04` §9.9).** Breaks that pause the block are a later epic, so E14 keeps calendar weeks from `block_start`.

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR; no S task.

| Task | Story | Flow | Size | Agent runs |
|---|---|---|---|---|
| T-227: Load progression on its own and the flagged progression (backend) | US-97, US-104 | backend + core | L | 5 |
| T-228: Load changes and flags in the Trainer tab, routine and live session | US-97, US-104 | core + UI | L | 7 |
| T-229: Calorie proposals: band, data gates, "Aplicar" / "Ahora no" | US-99 | backend + core + UI | M | 8 |
| T-230: Deloads on their own, cancel, ask for one | US-100 | backend + core + UI | M | 8 |
| T-231: The weekly summary and its notice | US-98 | backend + core + UI | L | 8 |
| T-232: "¿Por qué no progreso?" | US-105 | core (+ SQL) + UI | M | 7-8 |
| T-233: End-of-block review and the phase change (backend) | US-101, US-103 | backend + core | L | 5 |
| T-234: Review screen and the phase proposal | US-101, US-103 | core + UI | M | 7 |
| T-235: The next block in the builder | US-102 | core + UI (+ SQL if needed) | L | 7-8 |
| T-236: E14 closure | DoD | PM | closure step | PM only |

Why these splits and merges:
- **US-97 + US-104 → T-227 (backend) → T-228 (UI).** The flag is a branch of the same server decision and the same row, so it rides with the story it belongs to; together about 25 files with heavy SQL (decision, writes, flags, undo, change table, pgTAP) and heavy UI (new row, Trainer section, live-session and routine markers), reviewable apart.
- **US-99 (T-229) and US-100 (T-230) stay separate M tasks:** both extend an existing E13 path, each a vertical slice of about 10-12 files in a different domain (nutrition vs training); merged they would be an L mixing two migrations and two UI sweeps.
- **US-98 (T-231, L) and US-105 (T-232, M) stay separate:** the summary is about 16 files on its own (a weekly read, the notice, a new screen); the stall diagnosis is a self-contained rule set shared with the review.
- **US-101 + US-103 → T-233 (backend, L) → T-234 (UI, M):** the phase change lives on the review and reads the same block facts; the backend is heavy (inputs, lifecycle, triggers, stored decisions), the screen is one `AppScreen` with two proposal cards.
- **US-102 → T-235 (L), one task run as two slices (core, then UI):** the builder targets and the builder UI change together and are not usable apart.
- **Closure T-236** is the PM closure step; it waits for the user's confirmation, so it cannot merge into a task.

## Phases (no 👤 checkpoints, user decision 2026-11-05)

The journeys a checkpoint would have covered (session to session, the week, the end of the block, "leave the app and come back") are acceptance criteria and host tests of each task; T-235 carries the final no-model regression test. The user validates with real use over the following month; each reported error or piece of feedback becomes a `fix(T-###)` task.

### Phase 1: Session to session (US-97, US-104)
- [ ] 👤 The user's OK on this breakdown (stories, tasks)
- [ ] T-227: Load progression on its own and the flagged progression (backend) — L
- [x] T-228: Load changes and flags in the app — L

### Phase 2: The week (US-99, US-100, US-98, US-105)
- [x] T-229: Calorie proposals — M
- [ ] T-230: Deloads — M
- [x] T-231: The weekly summary — L
- [x] T-232: "¿Por qué no progreso?" — M

### Phase 3: The end of the block (US-101, US-103, US-102) and closure
- [x] T-233: End-of-block review and the phase change (backend) — L
- [ ] T-234: Review screen and the phase proposal — M
- [ ] T-235: The next block in the builder — L
- [ ] T-236: E14 closure — PM closure step, after the last task merges and the user confirms the closure (epic closure is always the user's)

Internal milestones (not 👤): per task lint (`check-design-tokens.sh`, `check-ux-conventions.sh`, ktlint, detekt) + `testLocalDebugUnitTest`, pgTAP green; after each migration `deploy-supabase` green on `main` with data intact before the next task.

## Dependencies
- E13 complete (`v1.4.0`): the block builder and `save_built_coach_block` (ADR-99, T-221…T-226), the deterministic nutrition plan and `plan_recompute` (ADR-98), the block lifecycle (ADR-94), plan weeks and adherence (T-165), the progression decision (T-159), the calorie recommendation path and `CoachProposalCard` (T-161/T-162), `coach_phase_history` (T-187), the server-side calculator settings (E6), `sleep_sessions` (E11), "Centro de avisos" (E10).
- Chain: T-227 → T-228 → T-229 → T-230 → T-231 → T-232 → T-233 → T-234 → T-235 → (user's real-data validation, fix tasks) → T-236. Strictly sequential (one active task at a time).

## Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Time-dependent triggers (two weeks of weigh-ins, week 4, a finished block) cannot be tried by hand in a few days | High | pgTAP and host tests cover every trigger; the user validates with real use |
| The coach writes planned values the user can also edit (and ADR-70 may update) | Medium | T-227's planner defines the undo rule when the user changed the value after the coach (refuse or restore) and pins it with pgTAP |
| The step is computed in SQL from the calculator settings | Medium | A user with no settings row gets the documented defaults (`04` §4.9); pgTAP cases for plates, dumbbell handle and no row |
| A rule implemented twice (set counting, stall test) makes the summary, review and builder disagree | High | One implementation each (SQL or shared Kotlin), read by every screen; shared fixtures |
| Writers keep acting after the block's last week | Medium | T-233's pgTAP runs the T-227, T-229 and T-230 writers past the end and expects nothing |
| Whether a deload-week session counts for the two-failed-sessions rule is not in `07` | Low | T-227/T-230 planners route it to `fitness-scientist` (science gap, not a user question) |
| The Trainer tab gets crowded (changes, proposals, deload, summary, review card) | Medium | ux-reviewer owns the Trainer root layout in T-228 and re-checks it in T-229…T-234; one row component and one proposal card only |

## Open questions (all resolved by the user, 2026-11-05)
- **E14 Q1 · Resolved (recommended): a block ends and the review is not done (E13 Q47).** Nothing starts on its own; the routines stay usable; a card "Tu bloque ha terminado: revísalo" stays on the Trainer root until the next block is saved; no automatic change (load, calories, deload) applies past the block's last week; saving the next block ends the current one. No early review ("Bloque nuevo") in E14. T-233, T-234, T-235.
- **E14 Q2 · Resolved (recommended): no "¿Cómo te ha ido?" in the review.** The "Me ha costado" limit of `07` §12.4 never applies in E14 (R-53). T-233, T-235.
- **E14 Q3 · Resolved: no 👤 checkpoints in E14 and no seed script or test aid.** The user tests with real data during the following month and reports feedback and errors when the triggers fire naturally; each report becomes a `fix(T-###)` task. pgTAP and host tests cover every trigger. Blocks nothing.
- **E14 Q4 · Resolved (recommended): calorie proposals with no saved block (the calorie part of E13 Q58).** Proposals keep running while the plan is accepted (`04` §9.15). T-229.
- **E14 Q5 · Resolved (recommended): the weekly summary vs the E10 weekly check-in.** Both stay (the check-in summary, `04` §6.6, unchanged); the Trainer tab shows the latest finished week only, no list of past weeks. "¿Seguimos?" after 6 blocks is not planned. T-231.
- Recorded, no question: E13 Q49 ("the one change" of the model report) is superseded by D4 (fixed templates; the stall line gives the one action); the PM marks it in `09` at closure. `01-scope.md` E14 row updated to the happy-path scope in this breakdown.
- Not for the user (decided inside the tasks): table and RPC names, where the stall function lives, the undo conflict rule (T-227 planner); every label and the Trainer root layout (ux-reviewer, within the glossary).
