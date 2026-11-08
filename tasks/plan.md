# Implementation Plan: E16 · Nutrition follow-up → `v1.x` (proposed `v1.7.0`)

> **Status: PROPOSED by the epic-planner, 2026-11-08; updated the same day after the user's decision (one checkpoint at the end) and the experts' answers to Q1-Q3.** The task list (T-246…T-249) and Checkpoint E16 need the user's OK on the breakdown PR. No question blocks a task. The E15 plan that lived here is in git history (E15 closed, `v1.6.0`; `tasks/todo.md` E15 section).

## Overview
Each week the user sees, inside the existing "Resumen semanal", how they ate against the plan (complete diary days, average kcal and protein against the daily goals in force, the weight trend against the target rate, the calorie changes of the week with their reason). When the target rate has been outside the weight trend's interval for two weekly summaries, one card names the first result of logging > average calories (with a weekend variant) > "Meseta" > "Sin causa clara", with one action. In a cut, an optional "Descanso de dieta" runs week 4 at maintenance; it is offered in the plan proposal and stays editable in "Tu plan". Everything extends what is built: the E14 weekly summary (T-231), the stall-card pattern (T-232), the calorie proposal (T-229) and the T-188 diet break. Deterministic: no language model, "Uso de IA" never changes.

Sources: `planning/epics/E16-nutrition-follow-up.md` (intent, non-goals, US-112…US-115; Approach, US-113 and US-114 amended 2026-11-08), **`07` §4 "Nutrition follow-up (E16)" (a)-(d)** (every E16 figure, order and text; with §9, §10, §12.6-§12.7), `07` §2, §3 "Block follow-up" (3) and (7), §5, `04` §6.7, §6.8, §7.3, §7.4, §8.5, §8.9, `05` §8-§10, E14 US-98, US-99, US-105, ADR-101. Tasks: `tasks/todo.md` (E16 section).

Out of scope (epic non-goals, `01-scope.md` §4): meal plans, refeeds, reverse dieting; medical advice or diagnosis; fibre, alcohol and micronutrient targets; a fifth tab or a second weekly summary; any model call; an automatic diet break. Not built by `07` (b) "Dropped": protein, sleep and steps as causes of a weight-trend miss, and a separate weekend check.

## Architecture decisions
- **One summary.** The nutrition section is a field of `coach_week_summary` and a section of `WeekSummaryScreen`; no new route, notice kind or table (US-112 "There is one summary"). The existing missing-records and weight lines move into the section rather than being repeated.
- **Same data rules as the calorie proposal.** Averages read only complete diary days (the 40 % rule of `04` §7.3); the gate is the 5 complete days of `07` §2; the target is the mean of the daily Diario goals in force on those days; the trend and interval are `04` §7.4. No verdict band or colour (`07` (a): no sourced tolerance).
- **One diagnosis, one result (T-247).** A deterministic SQL function mirrors `coach_stall_diagnosis`: the §4 calorie trigger on two consecutive summaries, then the ordered chain logging > average calories > "Meseta" > "Sin causa clara" (`07` (b)-(c)). A window with a diet-break week does not trigger.
- **The diagnosis and the calorie proposal share one card.** In a week where the calorie cause fires, no calorie proposal is made (the 14-day clock is not restarted; amends `04` §8.5). With "Meseta" or "Sin causa clara", the week's proposal, when made, is the card's one action ("Aplicar" / "Ahora no"), so the user never sees two competing messages.
- **The diet break stays a plan option (T-248).** Offered only in the plan proposal's cut options. Because the goal switch (`planGoalSwitch.ts`) and the review's phase change (`planPhaseChange.ts`) build their cut with `dietBreak: false` and never reach the proposal, and `plan_recompute` accepts only proposed plans (code check, 2026-11-08), T-248 makes the option editable on the accepted cut plan in "Tu plan"; if that cannot fit, the planner logs an E9 refinement with an owning follow-up task.
- **No new designsystem component is planned.** `WeekSummaryScreen` sections (`ListSectionHeader` + fact lines), the `WeekSummaryStallCard` pattern with the `CalorieProposalCard` actions, the `CutOptions` switch row with `HelpButton`, a switch row in "Tu plan", the `PlanWeeksSection` week state, the T-245 tappable note row. If ux-reviewer asks for a new one, it is built in the first task that needs it and retires its ad-hoc copies (R-39).

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR; no S task.

| Order | Task | Story | Flow | Size | Agent runs |
|---|---|---|---|---|---|
| 0 | PM step: `fitness-scientist` records the E16 rules in `07` (**done** 2026-11-08) | — | PM | — | 1 |
| 1 | T-246: The nutrition section of the weekly summary | US-112 | backend + core + UI | M | 8 |
| 2 | T-247: "¿Por qué no avanzo?" for nutrition and the plateau | US-113, US-114 | backend + core + UI | L | 8 |
| 3 | T-248: "Descanso de dieta" in a cut, editable in "Tu plan", + the R-59 route | US-115 (+ T-245 leftover) | backend + core + UI | M | 8 |
| 4 | T-249: E16 closure | DoD | PM | closure step | PM only |

Why these splits and merges:
- **US-112 → T-246 (M), one vertical slice:** one SQL field and one summary section, about 13 files.
- **US-113 + US-114 → T-247 (L):** "Meseta" is a link of the same ordered chain (same trigger, same logging check), so separate tasks would build and test the engine twice. Not split into backend and UI: the UI is one card reusing the US-105 pattern (about 3 files), which alone would be S.
- **US-115 → T-248 (M), with the R-59 leftover:** the diet break is built (T-188); the story adds a label, a help sentence, the week-4 lines and the switch in "Tu plan" for an accepted cut. The R-59 leftover from "Notes for later epics" is another Trainer-tab plan/block-flow route of similar size, so it is merged in.
- **Closure T-249** is the PM closure step released by Checkpoint E16's approval; it has no checkpoint of its own.

## Phases and checkpoints

Items to test in the epic, collected first, then split by whether they can be tried on demand (E15 Q5 precedent: the user does not force time-based triggers).
- **On demand (checkpoint items):** the section's days, kcal and protein against goals and the weight trend, inside the same summary; the missing-records line with no averages (test account); the diagnosis card each account's data gives, with no medical wording, and "Uso de IA" unchanged; "Descanso de dieta" off by default with its one-sentence `?`; on and accepted shows week 4 at maintenance, and off again in "Tu plan"; the R-59 tap to "Mi entrenamiento" and back; leave the app and come back. 7 items → **one checkpoint** (user decision 2026-11-08: one checkpoint only, at the end).
- **Natural triggers (outside the gate; "Pending natural-trigger feedback (E16)" in `tasks/todo.md`):** a calorie change listed with its reason; each diagnosis branch after two off-target weeks (logging, calorie cause with no proposal, "Sin causa clara"); "Meseta" after two flat weeks with the proposal as its action, and the logging cause with poor logging; week 4 actually moving the Diario goals. Covered by host tests and seeded pgTAP; each real-use report becomes a `fix(T-###)` task.

### Phase 1: Eating against the plan in "Resumen semanal" (US-112, US-113, US-114)
- T-246 → T-247. No checkpoint here (user decision); internal milestones (`deploy-supabase` green with data intact, CI green) are verification items in the tasks.

### Phase 2: "Descanso de dieta" (US-115), the checkpoint and closure
- T-248 → **👤 Checkpoint E16** → T-249.
- **👤 Checkpoint E16 — Nutrition follow-up: "Resumen semanal" and "Descanso de dieta", with the final E16 regression** (after T-248, the last implementation task; 7 items in `tasks/todo.md`; its approval releases T-249). Try: open last week's "Resumen semanal" on the main account and read the nutrition section; on the test account see the missing-records line; read the diagnosis card your data gives (one result, no medical wording, "Uso de IA" unchanged); on the test account create a "Perder grasa" plan and see "Descanso de dieta" off with its one-sentence `?`; turn it on, accept, see week 4 at maintenance, then turn it off in "Tu plan"; use the builder's "no fit" note to reach "Mi entrenamiento" and come back; leave the app and come back.

## Dependencies
- **PM step (done 2026-11-08):** `fitness-scientist` recorded the E16 rules in `07` §4 "Nutrition follow-up (E16)", §9, §12.6-§12.7, §10.
- T-246 needs the breakdown approved; T-247 needs T-246 merged and deployed; T-248 needs T-247 merged and deployed; T-249 needs Checkpoint E16 approved (the natural-trigger items do not gate it).
- Built pieces E16 relies on: T-231 (`coach_week_summary`, `WeekSummaryScreen`, the notice), T-232 (`coach_stall_diagnosis`, `WeekSummaryStallCard`), T-229 (calorie proposals, `coach_week_gate`), T-242 (plan weeks with pauses), T-188 (diet break, `CutOptions`, `plan_recompute`), T-180 (complete-day rule), T-239/T-240 and T-233/T-234 (the goal-switch and review paths, which build a cut without the option), T-245 (R-59 and its tappable note row).

## Risks
- **A checkpoint only at the end.** Feedback on the summary section (T-246, T-247) arrives after T-248 is built. Mitigation: T-248 does not touch the summary; any summary feedback becomes a `fix(T-###)` before closure.
- **Changing an accepted plan's diet break mid-block (T-248).** The toggle must not move the current week's goals, the calorie band or `edited_by_user`. Mitigation: Deno and pgTAP cases inside and outside week 4; fallback to an E9 refinement if it cannot fit.
- **Under-reporting.** Diaries under-report intake by about 19 % (`07` §2), so the copy says "registraste", never "comiste" (`07` (a)).
- **Summary weight (T-246, T-247).** `coach_week_summary` already runs the stall checks; adding the averages and the diagnosis adds queries per open. Mitigation: the carried `coach_stalled_lifts` fix (evaluate once) lands in T-246.
- **Testing a cut plan on the main account (T-248).** Accepting a new plan changes real calories. Mitigation: the checkpoint says to use the test account.
- **Health wording.** New Spanish lines about weight must not read as medical advice (R-52, US-113 "No health advice"). Mitigation: `HealthDisclaimerSweepTest` covers every new string; no allow-list entry is added.

## Decisions (former open questions, resolved 2026-11-08)
- **Checkpoints (user):** one checkpoint only, at the end (Checkpoint E16 after T-248; it releases T-249); the natural-trigger items stay outside the gate.
- **Q1 (experts, `07` §4 "Nutrition follow-up" (c)):** "Meseta" and a calorie proposal both show: the Meseta line first, the proposal as its one action. No proposal in a week where the calorie cause fires.
- **Q2 (experts, `07` (c)):** the cause wins: logging > average calories > "Meseta" > "Sin causa clara"; "Meseta" replaces only "Sin causa clara" (US-114 Gherkin amended: "And no cause of US-113 applies"). Protein, sleep and steps are dropped (no evidence-backed threshold); the weekend pattern is a variant of the calorie cause.
- **Q3 (experts):** "Descanso de dieta" is offered only in the plan proposal's cut options; no switch on the "Bloque nuevo" confirmation or the review sheet; T-248 keeps it editable afterwards in "Tu plan" (else an E9 refinement with an owner).
- **Assumptions confirmed by `07`:** the diagnosis shows in every summary where its trigger holds; exactly 5 complete days passes the logging check but is not a plateau; "Meseta" applies only to cut and gain.
