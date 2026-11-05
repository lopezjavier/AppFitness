# E14 · Block follow-up: the happy path → `v1.x` (version to be defined)

> **Status: BROKEN DOWN, PROPOSED (epic-planner, 2026-11-05):** stories US-97…US-105 below, tasks T-227…T-236 in `tasks/todo.md` / `tasks/plan.md`; they need the user's OK before the breakdown PR merges. **User decisions 2026-11-05:** E14 Q1-Q5 resolved as recommended, except Q3: **no 👤 checkpoints in E14**. The user tests with real data during the following month and reports errors and feedback when the triggers fire; each report becomes a `fix(T-###)` task. No early review ("Bloque nuevo") and no "¿Cómo te ha ido?" in E14. Rules: `07` §3 "Block follow-up on the happy path" (1)-(8), §4 (calorie proposal), §12.3-§12.4.
>
> **Earlier status: SCOPE AGREED, NOT PLANNED (user, 2026-11-05).** The E14 backlog moved out of E13 (2026-11-03/04) is **discarded**: it was written for a block built by the model, before ADR-99. E14 restarts from scratch and covers only the **happy path**: one block, the user trains as planned, nothing goes wrong. The `epic-planner` writes the stories (new IDs from US-97), the Gherkin, the tasks and the checkpoints from this file, after the `fitness-scientist` audit and after the new rules below are recorded in `07`.

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
9. **"¿Por qué no progreso?"** (user, 2026-11-05). It appears in the end-of-block review, and in the weekly summary when a main lift has stalled. A deterministic checklist runs in a fixed order and stops at the first cause it finds; the app shows that cause in one line with one suggested action. The checks: (a) adherence below the plan; (b) effort, meaning sets per muscle below 10 a week or logged RPE consistently low; (c) nutrition for the goal, meaning the weight trend vs the target rate, calories under target or protein below 1.6 g/kg; (d) recovery, from sleep and steps in the Health Connect sync; (e) data reliability, meaning days without food logs or missing weigh-ins. The `fitness-scientist` defines the order and the thresholds in `07` with sources. Under review (user, 2026-11-05): the fitness-scientist keeps only the checks with an evidence-backed threshold; if none remain, the feature is dropped. No model.

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

## Stories (epic-planner, 2026-11-05; proposed)

One story per feature, in the scope's order (feature n = US-(96+n)). Every figure and copy line comes from `07` §3 "Block follow-up on the happy path" (cited as `07` (1)-(8)), §4 and §12.3-§12.4; ux-reviewer finalises the Spanish copy within the glossary without changing a figure. "Block" means an accepted plan with a saved block (`04` §9.15). Weeks are the plan weeks of `04` §9.9 (calendar weeks from `block_start`; breaks are a later epic).

## US-97 · Loads progress on their own inside the block
*Feature 1. Task T-227 (backend), T-228 (UI). Rules: `07` §3 "Progression", "Failed sessions", (1); `04` §4.14 amended. Supersedes the follow-up part of US-70 and US-85 (discarded backlog).*

**As** a user training a block
**I want** the next session's load to change on its own when I earn it, with the reason, and to undo it
**So that** I follow a progression like a trainer's without doing the maths.

```gherkin
Feature: Load progression applied on its own

  Background:
    Given my accepted plan has a saved block
    And "Press banca con barra" has a rep range of 8-12 at 60 kg in a block routine

  Scenario: A raise is applied after the session
    When I finish a session where every working set of "Press banca con barra" reached 12 repetitions at RPE 8 or less
    Then its planned weight in every block routine with the same range is the next available step, 62,5 kg
    And its planned repetitions go to 8
    And the Trainer tab lists the change with "Subimos Press banca con barra a 62,5 kg: la última vez hiciste 12 repeticiones en todas las series." and "Deshacer"

  Scenario: Undoing a load change
    Given the coach raised "Press banca con barra" to 62,5 kg
    When I tap "Deshacer" on that change
    Then its planned weight and repetitions are the ones from before the change

  Scenario: Two failed sessions lower the load 5 %
    When most working sets missed 8 repetitions in my last two sessions of "Press banca con barra"
    Then its planned weight drops 5 %, rounded to the available step
    And the change is listed with its reason and "Deshacer"

  Scenario: An open discomfort keeps the load
    Given "Press banca con barra" has an open discomfort of level 1 to 3
    When a raise would be due
    Then the planned weight stays the same and the reason says the load is kept because of the open discomfort

  Scenario: A severe discomfort changes nothing
    Given "Press banca con barra" has an unresolved discomfort of level 4 or 5
    When I finish a session
    Then the coach does not change that exercise

  Scenario: "Actualizar automáticamente" runs first
    Given the routine has "Actualizar automáticamente" on
    When I finish a session that earns a raise
    Then the routine is updated from what I performed and the coach's change is applied on top

  Scenario: Outside a block it stays a suggestion
    Given I have no saved block
    When I finish a session
    Then the live session keeps showing the progression suggestion and no routine changes
```

## US-98 · The weekly summary
*Feature 2, D4, D5. Task T-231 ("¿Por qué no progreso?" line: T-232). Rules: `07` (3), §12.6, §2 data cleaning; ADR-73 (in-app only).*

**As** a user training a block
**I want** a short summary of each week from my own numbers
**So that** I see what I did and what the coach changed, and why.

```gherkin
Feature: Weekly summary

  Scenario: The summary of a finished week
    Given plan week 2 of my block has ended with at least one session, weigh-in or diary day
    When I open the Trainer tab
    Then the summary of week 2 shows "Esta semana: 3 de 4 sesiones."
    And the working sets per muscle group, such as "Series por grupo: Pecho 10 · Espalda 12"
    And "Peso medio 80,2 kg (−0,3 kg/semana; objetivo −0,4)."
    And my complete diary days and weigh-ins of the week
    And every load the coach changed that week with its reason
    And any calorie proposal and any deload of the week

  Scenario: Indirect sets count half
    Given I did 4 working sets of bench press (chest; triceps and shoulders secondary)
    Then the week's summary counts 4 sets for chest and 2 for triceps and for shoulders

  Scenario: One notification per summary
    When a new weekly summary is ready
    Then "Centro de avisos" lists it once
    And opening the summary removes it from "Centro de avisos"

  Scenario: An empty week has no summary
    Given a plan week with no session, weigh-in or diary day
    Then no summary is made for it and "Centro de avisos" lists nothing for it

  Scenario: Missing records
    Given the week had fewer than 5 complete diary days or fewer than 3 weigh-ins
    Then the summary says "Esta semana faltan registros: no cambiamos tus calorías."

  Scenario: No model
    When the summary is shown
    Then it is made from fixed templates with my numbers and "Uso de IA" does not change
```

## US-99 · Calorie changes I approve
*Feature 3; ADR-88 amended 2026-11-05. Task T-229. Rules: `07` §4 "Calorie change is proposed…", "Adjustment inside a block", §2 data cleaning; `04` §8.5 amended. Supersedes the calorie part of US-69 amended and US-85 (discarded backlog).*

**As** a user with an accepted plan
**I want** the app to propose a calorie change when my weight trend misses the target, and to decide myself
**So that** my targets never change without my say.

```gherkin
Feature: Calorie proposal

  Scenario: A proposal after two weeks off target
    Given my plan's target rate has been outside my weight trend's interval for two consecutive weeks on clean data
    When I open the Trainer tab
    Then I see "Te proponemos bajar 150 kcal: llevas 2 semanas perdiendo menos de lo previsto." with "Aplicar" and "Ahora no"
    And my calories do not change until I tap "Aplicar"

  Scenario: Applying the proposal
    When I tap "Aplicar"
    Then my calorie target in Diario changes by the proposed amount
    And my protein grams and fat percentage stay the same

  Scenario: "Ahora no"
    When I tap "Ahora no"
    Then my calories stay the same
    And no new proposal appears for 14 days

  Scenario: Two "Ahora no" in a block
    Given I tapped "Ahora no" twice in this block
    Then no more calorie proposals appear until the end-of-block review

  Scenario: Not enough records
    Given the week had fewer than 5 complete diary days or fewer than 3 weigh-ins
    Then no calorie proposal is made

  Scenario: Inside the safe band only
    Given the change would take my calories outside ±10 % of the block's accepted calories, under the floor, beyond the 500 kcal deficit or outside my phase's rate
    Then no proposal is made during the block

  Scenario: One proposal at a time
    Given a proposal is pending
    When the next week's computation runs
    Then the pending proposal is replaced, never stacked

  Scenario: Goals I edited
    Given I edited my nutrition goals myself
    Then no calorie proposal is made
```

## US-100 · Deloads on their own, and cancelling or asking for one
*Feature 4, D1. Task T-230. Rules: `07` §3 "Deload", (4); `04` §9.10 amended. Supersedes US-75 amended and US-86 (discarded backlog).*

**As** a user training a block
**I want** the lighter week to come on its own when I need it, and to cancel or ask for one
**So that** I recover without planning it myself.

```gherkin
Feature: Deloads

  Scenario: Triggered deload
    Given no main lift done in each of plan weeks 1-3 improved its best weekly e1RM
    When plan week 4 starts
    Then week 4 is a deload week: same weights and 40 % fewer working sets per exercise, at least 1
    And the Trainer tab shows "Semana de descarga: mismos pesos, menos series. Llevas 3 semanas sin mejorar en los básicos." with "Cancelar descarga"

  Scenario: Scheduled deload
    Given no deload ran in my previous 7 training weeks
    When plan week 4 of the block starts
    Then week 4 is a deload week with the reason "Llevas 7 semanas entrenando sin descarga." and "Cancelar descarga"

  Scenario: The live session follows the deload
    Given this is a deload week
    When I start a block routine
    Then each exercise shows the reduced number of working sets at the planned weights

  Scenario: Cancelling the deload
    When I tap "Cancelar descarga"
    Then the week goes back to the normal sets

  Scenario: Asking for a deload
    Given no deload is planned or has run in this block
    When I tap "Hacer descarga la próxima semana"
    Then the next plan week is a deload week with "Cancelar descarga"

  Scenario: One deload per block
    Given a deload already ran in this block
    Then no other deload is planned or offered in this block
```

## US-101 · The end-of-block review
*Feature 5 (with the nice-to-have outcome check). Tasks T-233 (backend), T-234 (UI). Rules: `07` (5), §12.4; E13 Q47 re-asked as E14 Q1 and resolved by the user 2026-11-05 as recommended (the card stays until the next block is saved; no early review in E14). Supersedes US-83 (discarded backlog).*

**As** a user who finished a block
**I want** to see what I did against the plan and how I progressed
**So that** I start the next block knowing where I stand.

```gherkin
Feature: End-of-block review

  Scenario: The review after the last week
    Given the last plan week of my block has ended
    When I open the Trainer tab
    Then I see "Tu bloque ha terminado: revísalo"
    And the review shows my adherence as completed of planned sessions
    And the e1RM trend of each main lift over the block
    And the working sets I did per muscle group
    And my weight trend against the target rate
    And whether each outcome of the plan was reached

  Scenario: A deload week is left out of the e1RM trend
    Given week 4 was a deload week
    Then the e1RM trend uses weeks 1-3 and the adherence still counts week 4

  Scenario: Nothing starts on its own
    Given my block has ended and I have not done the review
    Then no new block starts, my routines stay usable in Entreno
    And no calorie proposal, load change or deload applies after the block's last week

  Scenario: A stalled lift says why
    Given a main lift did not improve over the last three plan weeks
    Then the review shows the "¿Por qué no progreso?" line for it (US-105)
```

## US-102 · The next block starts from the last one
*Feature 6, D2, D3. Task T-235. Rules: `07` (6), §12.4, §3 "Block builder", §12.8 "Loads". Supersedes US-84 (discarded backlog).*

**As** a user who finished a block
**I want** the next block to start from my last one, with my priorities per muscle group
**So that** I build it in a few taps and keep what worked.

```gherkin
Feature: Next block

  Scenario: The builder opens pre-filled
    Given I finished the review of my block
    When I start the next block from the review
    Then the block builder opens with the last block's split, routine types and exercises

  Scenario: "Priorizar" a muscle group
    Given my adherence was 80 % or more
    When I choose "Priorizar" for Hombros
    Then Hombros gets 2 more weekly sets, never above 20
    And the builder shows the sets per group before and after

  Scenario: "Mantener" a muscle group
    When I choose "Mantener" for Bíceps
    Then Bíceps drops to 4 weekly sets (6 from 65 years)
    And the freed sets go first to the groups I chose to prioritise

  Scenario: At most three prioritised groups
    Given I prioritised three groups
    Then "Priorizar" is not available for a fourth group

  Scenario: Low adherence adds no sets
    Given my adherence was under 80 %
    Then "Priorizar" adds no sets and the builder says why

  Scenario: In a cut no volume is added
    Given the next block is in a cut
    Then prioritised groups receive only the sets freed by "Mantener"

  Scenario: A stalled main lift can be changed
    Given "Sentadilla" did not improve over the block
    Then it is marked "puedes cambiarlo" and I can swap it like any exercise

  Scenario: A suggestion for a stalled group
    Given Pecho stalled, my adherence was high, it has no open discomfort and the next block is not a cut
    Then the builder shows "Sugerencia: +2 series" on Pecho
    And the sets change only if I tap it

  Scenario: Loads carry over
    When I save the next block
    Then each exercise's planned weight comes from my own history

  Scenario: Saving starts the next block
    When I tap "Guardar bloque"
    Then the new block's first week starts on that day and the previous block ends
```

## US-103 · A phase change offered at the review
*Feature 7. Task T-233 (backend), T-234 (UI). Rules: `07` (6b), §12.3, §12.7; §12.8 row 8. Supersedes the review part of US-82 (phases) for the follow-up.*

**As** a user who finished a block
**I want** the review to propose a phase change when my data calls for it, and to keep my phase if I prefer
**So that** my year follows a sensible sequence without me tracking it.

```gherkin
Feature: Phase change at the review

  Scenario: Maintenance after three cut blocks
    Given I finished my third deficit block in a row
    When I open the review
    Then it shows "Llevas 12 semanas de definición. Te proponemos 4 semanas comiendo lo que gastas antes de seguir." with "Aceptar" and "Seguir como estoy"

  Scenario: A cut held at the floor
    Given my whole block was limited by the calorie floor and my trend was slower than half the target rate
    Then the review proposes maintenance with the reason from my data

  Scenario: Back to my goal after maintenance
    Given I finished at least one maintenance block after a cut
    Then the review proposes my intention's phase

  Scenario: Accepting the phase
    When I tap "Aceptar"
    Then the next block uses the new phase and its calories

  Scenario: Keeping my phase
    When I tap "Seguir como estoy"
    Then the phase stays the same and the proposal comes back at the next review

  Scenario: A reached outcome changes nothing before the review
    Given I reached my weight outcome in week 2
    Then my phase, calories and block do not change until the review
```

## US-104 · A progression that needs my choice
*Feature 8 (from MacroFactor); user decision 2026-11-05: a jump over 10 % pauses until I pick. Task T-227 (backend), T-228 (UI). Rules: `07` (2).*

**As** a user whose equipment makes the next step too big, or whose exercise has no rep range
**I want** the app to stop and give me one-tap choices
**So that** a progression the rules cannot apply cleanly is still mine to decide.

```gherkin
Feature: Flagged progression

  Scenario: A jump over 10 % waits for me
    Given "Elevaciones laterales" is at 6 kg and the next available step is 8 kg
    When I finish a session that earns a raise
    Then its planned weight does not change
    And it is flagged with "La siguiente subida es de 2 kg (+33 %), más de lo recomendable de una vez. Puedes subir igualmente o mantener el peso y sumar repeticiones."
    And the options "Subir a 8 kg" and "Mantener el peso y sumar repeticiones"

  Scenario: Raising anyway
    When I tap "Subir a 8 kg"
    Then its planned weight becomes 8 kg and the change can be undone with "Deshacer"

  Scenario: Keeping the weight and adding repetitions
    When I tap "Mantener el peso y sumar repeticiones"
    Then the weight stays and the top of its rep range rises by 2 for the rest of the block

  Scenario: No rep range
    Given a block exercise has a single planned repetition figure and no range
    Then it is flagged with "Añadir un rango"
    And tapping it opens that exercise's rep range in the routine

  Scenario: The flag stays until I choose
    Given a flagged exercise
    When I finish another session without choosing
    Then the flag stays and the weight does not change on its own
```

## US-105 · "¿Por qué no progreso?"
*Feature 9 (kept by the fitness-scientist with four evidence-backed checks; user decision 2026-11-05: the sleep check stays last). Task T-232. Rules: `07` (7).*

**As** a user whose main lift stopped improving
**I want** one plain line with the first likely cause in my data and one thing to do
**So that** I know what to change without guessing.

```gherkin
Feature: Why am I not progressing

  Background:
    Given "Press banca con barra" is a main lift whose best weekly e1RM did not improve over my last 3 plan weeks, deload weeks excluded

  Scenario: Too few sessions
    Given I completed an average of 1,5 lifting sessions per week in those weeks
    Then I see "Entrenaste de media 1,5 días por semana. La fuerza mejora con al menos 2." and "Intenta entrenar al menos 2 días por semana."

  Scenario: Too few sets for the lift's group
    Given my intention is not "Ganar fuerza" and I averaged 7 weekly working sets of Pecho
    Then I see "Hiciste de media 7 series semanales de Pecho; para ganar músculo se recomiendan al menos 10." and "Completa las series del plan."

  Scenario: A group on "Mantener"
    Given Pecho is on "Mantener" with fewer than 10 sets in the plan
    Then I see "Pecho está en Mantener: con menos series es normal no progresar." and "Elige Priorizar en el próximo bloque si quieres progresar aquí."

  Scenario: Low protein
    Given at least 10 complete diary days with an average protein under 1,6 g per kg
    Then I see the protein line with what I logged and "Tu objetivo es 160 g al día."

  Scenario: Short sleep is checked last
    Given sessions, sets and protein are fine and at least 10 nights with sleep data average under 7 h
    Then I see "Dormiste de media 6,2 h. Dormir menos de 7 horas se asocia con rendir peor." and "Intenta dormir al menos 7 horas."

  Scenario: Only the first cause
    Given both my sessions and my sleep are below their thresholds
    Then only the sessions line is shown

  Scenario: No clear cause
    Given no check fires
    Then I see "En tus datos no vemos una causa clara." and "En el próximo bloque puedes cambiar este ejercicio."
    And when the protein check could not run, "Faltan registros de comida para valorar la proteína."

  Scenario: Where it appears
    Then the line appears in the weekly summary of the week the stall is first found and in the end-of-block review
```

## Discarded backlog (user, 2026-11-05)
- **Stories:** the old E14 stories US-69/70/73/75 (automatic parts), US-71, US-74, US-77, US-83…US-88, US-90, US-91, US-92 and the "Evaluation" section. Their Gherkin stays in `E13-performance-assistant.md` as history and is not input for the re-plan. Their story IDs are not reused.
- **Tasks and checkpoints:** T-166…T-169, T-189…T-194, T-196, T-197 and Checkpoints E13-C, E13-E and E13-F. Their task IDs are not reused.
- **Open questions:** E13 Q44, Q45, Q48, Q49, Q52, Q55 and Q58 stay open for the later epics. Q47 (a block ends and the review is not done) is re-asked by the epic-planner for feature 5.
