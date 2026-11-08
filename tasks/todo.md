# Tasks — Phase 3

Plan of the current epic: none yet (the epic-planner creates `tasks/plan.md` for the first Phase 3 epic) · Planning: [planning/](../planning/README.md)
Operating instructions: [`CLAUDE.md`](../CLAUDE.md). Phase 1 history (frozen): [tasks/phase1/](phase1/todo.md). Phase 2 history (frozen, with the full per-task log T-134…T-238): [tasks/phase2/](phase2/todo.md).

Legend: **👤** = done by the user (accounts, web consoles, secrets) · **PM** = done by Claude Code.
Secrets rule: never pasted in the chat or in versioned files. The user enters them in their terminal or in the consoles, and Claude only verifies that they exist.

Last task ID used: T-238

**Next:** Phase 3 is defined in `planning/01-scope.md` (E15 Real life and goal switching → E19 Closure, epic files written 2026-11-08). The `epic-planner` breaks E15 into tasks in a fresh session; no task is active.

## Open follow-ups carried into Phase 3

- T-238 (pr-reviewer, non-blocking): extract local vals for the ktlint-wrapped `kind == RestAlertKind.Cardio` in `RestAlertNotifier.post` and `minutes != null` in `SessionSetRow.cardioPlaceholder`.
- T-238: `LiveSessionViewModel.timerBarUi` uses `names.getValue(...)`, which can throw (use a fallback).
- T-238: missing ViewModel tests: deleting the active cardio set, starting a second cardio run, a refused session pause (`restoreCardio`), a target reached while a rest owns the bar.
- T-235 (pr-reviewer/ux-reviewer): comment in the migration's "different plan already accepted" branch that accept already superseded the previous plan; confirm with the user that low adherence still lets freed Mantener sets flow to prioritised groups; show the low-adherence/cut reasons on every row (optional); `FormContentMaxWidth` cap on the targets step and an explicit themed style on the "puedes cambiarlo" text; `05` §8 builder spec text, SegmentedSelector `disabledIndices` catalog entry, haptics and restoration host tests.
- T-234 (pr-reviewer suggestions): extract `CalorieChoice.toEvent()` in `BlockReviewScreen.kt`; add a `BlockReviewViewModel` test for the failed re-read after a decision; "No conseguido" uses `StatusTone.Caution` (no neutral tone exists; ask ux-reviewer whether R-41 wants a neutral one); the review subtitle (date range) is missing because `BlockReview` has no block start date.
- T-232: `coach_stalled_lifts` runs 2-3 times per summary call (a temp CTE would avoid it); add a pgTAP case for a deload week at W-1 in the "first found" comparison; `WeekSummaryViewModel.today()` uses UTC while `LoadReminders` uses the local-zone date (pass one consistent date); `BuilderSlotPickerSheet.kt:55` and `FolderBlockPickers.kt:93` use accent-sensitive `contains(ignoreCase = true)` (use `normalizeText` as `SearchExercises` does); a cancelled deload can be requested again, and the automatic path never re-applies one in the same block (document in `04` §9.10).
- Carried over from #522: `coach_week_gate` should return `{ok:false}` itself on an empty diary; align the -500 cap / +750 limit with `04` §8.5; document that `recent_change` is not scoped to `plan_id`; rename the `change` parameter of `CalorieProposalCard` to `proposal`; merge `acceptOffline`/`discardOffline` in `TrainerScreen.kt`.

---

## Known flaky tests (the user does not accept flakiness)

Rule: every CI failure that passes on re-run is logged here with date, run URL and test. A second occurrence of the same test turns into a `fix(T-###)` task to remove the cause (or, only if the cause is outside our code, the test is marked and documented as flaky here).

| Test | Date | Run | Count | Status |
|---|---|---|---|---|
| `SupabaseTrainingPreferencesRepositoryTest.get_rowWithoutCalculatorColumnsUsesTheDefaults` (`AppException` at line 95, no cause in the log; passes locally and on re-run) | 2026-10-29 (PR #371) | https://github.com/lopezjavier/AppFitness/actions/runs/40959762823 | 1 | watch: next failure → fix task |


## 👤 Checkpoints

No open 👤 checkpoints (Phase 2 checkpoints are in tasks/phase2/todo.md)

## Notes for later epics

- **Out of scope (user, 2026-11-08, ADR-101):** "Recuperar" / returning after a break (regain phase) is out of scope for the app; the 14-day return rule stays as the only break rule.
- **E13 backlog (user request, 2026-10-30): consumed.** Items 1 → T-179, 2 → T-181…T-184, 3 and 4 → T-205/T-206. Nothing from it is open.

## Reminders

- Delete `design/reported/live-session-cardio-row-layout.png` once the user confirms the cardio fix (T-238).
