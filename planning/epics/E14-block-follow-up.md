# E14 · Block follow-up: the happy path → `v1.x` (version to be defined)

> **Status: SCOPE AGREED, NOT PLANNED (user, 2026-11-05).** The E14 backlog moved out of E13 (2026-11-03/04) is **discarded**: it was written for a block built by the model, before ADR-99. E14 restarts from scratch and covers only the **happy path**: one block, the user trains as planned, nothing goes wrong. The `epic-planner` writes the stories (new IDs from US-97), the Gherkin, the tasks and the checkpoints from this file, after the `fitness-scientist` audit and after the new rules below are recorded in `07`.

## Goal
Follow the user through a block the way a real, evidence-based trainer would on the happy path: loads that progress on their own, a weekly summary, calories that stay on target, deloads, an end-of-block review and a next block built from the last one. Everything is deterministic; no language model.

## How the scope was set (2026-11-05)
- A `fitness-scientist` analysis of what a trainer does across one block on the happy path (each step: evidence, deterministic vs model vs leave out, applies on its own / suggested / shown). Sources it opened: Plotkin 2022, Scarpelli 2022, Barsuhn 2025, Halperin 2022, Hickmott 2022, Coleman 2024, Rogerson 2024, Trexler 2014, Burke 2011, Michie 2009, ACSM 2009, Kassiano 2022, Pelland 2026, Ntoumanis 2021. They are proposed for `07` §10 and are not recorded yet.
- A competitor review of how training apps follow the user: JuggernautAI, RP Hypertrophy, MacroFactor (Workouts and nutrition), Carbon, Alpha Progression, Hevy, Boostcamp, Liftosaur, Dr. Muscle, Fitbod and Caliber. Its findings:
  - The common baseline is next-session targets from fixed progression rules, progress views and, in nutrition apps, a weekly calorie check-in.
  - Nearly all "AI" in these apps is deterministic. No app in the review has a shipped language model in its progression loop.
  - Deloads and the end of the block are the weakest areas in the market.

## Scope: features, in priority order (user, 2026-11-05)
Must-have:
1. **Progression inside the block, applied on its own with "Deshacer".** Double progression: when every set reaches the top of the range at RPE ≤ 8, the next session moves to the next weight step. The load drops 5 % after two failed sessions (`07` §3). Today progression is only a suggestion in the live session.
2. **Weekly summary from fixed templates.** It shows sessions done vs planned, sets per muscle group, the weight trend vs the target rate, and the loads the app changed and why. It appears in the Trainer tab with one notification. No model.
3. **Calories within the safe band.** When the weight trend misses the target rate for 2 weeks, the app **proposes** a change and the user approves it. It does not apply on its own; this follows MacroFactor and Carbon, since a calorie change touches every logged meal (`07` §4).
4. **Deloads.** A **triggered** deload (no main lift improved for 3 weeks) and a **scheduled** deload (week 4 of every second block: same weights, 40 % fewer sets). Both apply on their own and can be undone with "Cancelar descarga" (`07` §3).
5. **End-of-block review screen.** It shows adherence, the e1RM trend per main lift (weeks 1-3 when week 4 was a deload), sets done per group, the weight vs the target rate, and whether each outcome was reached.
6. **Next block.** The builder opens pre-filled with the last block's picks. The user chooses "Priorizar" (+2 weekly sets) or "Mantener" per muscle group. Stalled main lifts are marked "puedes cambiarlo". For a stalled group the app only **suggests** +2 weekly sets, without adding them. Loads carry over from the user's history.
7. **Phase change offered at the review.** For example, after 3 cut blocks the review offers maintenance. Reaching an outcome changes nothing before the review.
8. **Weak-recommendation flag** (from MacroFactor). When weight steps or the rep range block a clean progression, the target is flagged and offers one-tap fixes, such as widening the rep range or keeping the weight.

Nice-to-have: the outcome check per block (detail of 5); "¿Seguimos?" after 6 blocks (`07` §9).

### Decisions taken with the scope (user, 2026-11-05; the fitness-scientist's D1-D5)
- D1: the scheduled deload applies on its own, with "Cancelar descarga".
- D2: the +2 sets for a stalled group is a suggestion in the next block, not automatic. The +2 sets for "Priorizar" stays automatic.
- D3: the next block starts pre-filled with the last block's picks.
- D4: the weekly summary uses fixed templates; no model.
- D5: the weekly summary appears in the Trainer tab with one notification.
- Calories: the change is proposed and needs the user's approval. This replaces "on its own with Deshacer" for calories in `07` §4 and ADR-88.

### Left out (evidence or product)
- Set-by-set load changes from the first set's RPE (not better than fixed loading; RPE is imprecise).
- Heavier rep ranges or periodization between blocks for "Ganar fuerza".
- Per-muscle pump and soreness questions (RP).
- A readiness check before the session (JuggernautAI).
- A strength score against people of the same age and sex.
- **Any language model on the happy path.** This covers the model-written report, the coach chat, the evaluation and the recommendation history.

## Out of scope: after the happy path (later epics; no tasks yet)
- Breaks, missed weeks and returning; weeks that pause on breaks.
- Injuries, the graded return and the persistent-discomfort note.
- Life changes mid-block ("Revisar el bloque", "Adaptar el bloque", "Solo unos días", goal change, turning 65).
- The model-based features (report text, chat, evaluation, history and deletion of coach data).
- The E13 out-of-scope list still applies (medical advice, generic disclaimers, illness rules, reverse dieting, refeeds, water and voice).

## Before the epic-planner
1. Audit of `.claude/agents/fitness-scientist.md` (wording shown in chat first).
2. The `fitness-scientist` records the rules above in `07`, including the new §10 refs, D2 and the calorie approval. Each rule needs a source it opened in that session.
3. The ADR changes the scope needs: amend ADR-88 for calories, and mark what ADR-86, ADR-88 and ADR-89 keep only for the later epics.

## Dependencies
E13 complete. The scope relies on the block builder (ADR-99), the deterministic nutrition plan (ADR-98) and the block lifecycle (ADR-94).

## Discarded backlog (user, 2026-11-05)
- **Stories:** the old E14 stories US-69/70/73/75 (automatic parts), US-71, US-74, US-77, US-83…US-88, US-90, US-91, US-92 and the "Evaluation" section. Their Gherkin stays in `E13-performance-assistant.md` as history and is not input for the re-plan. Their story IDs are not reused.
- **Tasks and checkpoints:** T-166…T-169, T-189…T-194, T-196, T-197 and Checkpoints E13-C, E13-E and E13-F. Their task IDs are not reused.
- **Open questions:** E13 Q44, Q45, Q48, Q49, Q52, Q55 and Q58 stay open for the later epics. Q47 (a block ends and the review is not done) is re-asked by the epic-planner for feature 5.
