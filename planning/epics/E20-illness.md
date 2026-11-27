# E20 · Illness

> **Status: DROPPED (user, 2026-11-27, ADR-113).** Out of the Phase 3 scope: the user no longer needs it. Nothing below is planned or open; the draft is kept only as a record. A few sick days are still handled by the ADR-106 pencil (extend the week), and illness stays a non-goal (`07` §12.5).

> **Status: DRAFT (product-owner, 2026-11-15), not agreed, not planned.** No stories and no Gherkin yet: writing them is the first task of the epic, done with the user and `fitness-scientist` (see "First task"). Origin: the user was ill during week 1 of a block (2 of 4 sessions, 1 kg lost) and asked whether to restart the block and the nutrition plan; the answer was no, but the app has no way to say "I am ill".

## Intent

**Problem.** An illness of 2-4 days (a cold, gastroenteritis) breaks the block's training, weight and calorie data, and the app cannot be told about it. Today a pause only happens on its own when a week has no sessions (US-106), and the pencil of ADR-106 only moves the week's end date. The user is left to choose between a block that looks failed and a restart that throws good data away.

**Outcome (how we will know it worked).** The user marks an illness, picks it from a short list, and the app changes in a fixed, predictable way: the block does not count those days against them, those days do not feed the weight trend or the calorie proposals, and on return the block resumes at the next planned session. Layer 2 (food diary) is judged separately, see Q5.

**Non-goals (draft).**
- Diagnosing, treating or detecting an illness; the app only records what the user says.
- Free-text or AI interpretation of symptoms; every effect comes from a fixed table.
- Long illnesses, injuries (E15 already covers discomfort per exercise) and travel.
- Reverse dieting, refeeds, a graded return to training (E15 Q3 stays closed), unless `fitness-scientist` verifies a rule and the user asks for it.

**Approach chosen and why (draft).** Three layers, each usable without the next:
1. **Mark and pause.** "Estoy enfermo" with a start day and an end day; the plan pauses; the days are excluded from data; the block resumes after.
2. **Fixed catalogue.** A few common illnesses (cold, gastroenteritis, flu or fever, "Otra" with only the pause), each defining by table what pauses: training, cardio, calorie and step targets.
3. **Diary adapted to the illness** (bland diet, warnings on heavy foods): only if clinical sources are verified (Q5).

Layers 1 and 2 first; layer 3 as a later delivery inside the epic.

## Scope tension to resolve first (needs an ADR)
- `01-scope.md` lists "medical advice" as out of scope; E13, E14 and E15 list "illness rules" as non-goals; `07` §12.5 says "Illness: no rule (none verified)". ADR-106 relaxed this only for extending the week.
- E20 reverses that for illness. A new ADR must say exactly how far: the pause and the data exclusion (layers 1-2) are product behaviour; the food guidance (layer 3) is health advice and needs its own decision (Q5).

## Relations with what exists (to resolve)
- **Automatic pause (US-106, E15):** "Semana en pausa" when a week has no sessions. How does a declared illness relate to it (replaces it, same state, or separate)?
- **ADR-106 pencil (extend the week, 14-day cap):** keep, replace or fold into the illness days?
- **ADR-101 14-day return rule** and the "welcome back" notice (US-107): does an illness of 2-4 days ever trigger them?
- **Block clock:** today a block counts calendar weeks. Whether training progression and deload count sessions instead of weeks is an open question that this epic depends on (Q1).

## Open questions (to resolve at the start of the epic)

| ID | Question | Who decides | Recommendation |
|---|---|---|---|
| Q1 | Does an illness **shift the whole block** (weeks, review, next block move by the days lost, as the pencil does) or does the block keep its calendar and the days are only marked "enfermo"? Related: should a block be counted in **completed sessions** instead of calendar weeks (options: keep calendar + pencil; sessions with a calendar cap; hybrid where a training week advances only when its sessions are done)? | User, after `fitness-scientist` says whether progression and deload should count sessions or weeks | Hybrid: calendar weeks stay for nutrition and reports; the training week advances on completed sessions |
| Q2 | If a block ends "on its date" with sessions missing, does the review say "hiciste 14 de 16", or does the block end only when the sessions are done? | User | Decide together with Q1 |
| Q3 | Which illnesses are in the catalogue and what does each one pause (training, cardio, calorie target, step target)? Proposal: cold, gastroenteritis, flu or fever, "Otra" (pause only). | User + `fitness-scientist` | Short list; effects in a table in `07` |
| Q4 | How are the days entered: start and end day chosen in advance, or open-ended "estoy enfermo" closed with "Ya estoy bien"? Maximum length? Can past days be marked (the user may notice late)? | User | Open-ended with a closing action, and past days editable |
| Q5 | Food guidance (bland diet, warnings on heavy foods): wanted from the first delivery or later? Which clinical sources are acceptable (gastroenterology or primary-care guidelines, not sports science)? Shown as a visible suggestion or as a block on logging? What disclaimer, if any? | User, with sources from `fitness-scientist` | Later delivery; suggestion only; no blocking; only with verified clinical sources |
| Q6 | What happens to **weight**, **calories** and **steps** on illness days: excluded from the trend and the averages, or kept and flagged? Does the calorie target relax (eating less is expected) or just not be judged? Hydration is not a feature (water is out of scope, ADR-101). | `fitness-scientist` | Excluded from the averages and the trend; no proposal built on those days |
| Q7 | Return to training: resume at the planned loads, or lighter for the first session after an illness? No rule is verified today (`07` §12.5). | `fitness-scientist` | Resume as planned unless a verified rule says otherwise |
| Q8 | Does the 14-day return rule (ADR-101) or the "welcome back" notice apply to a short illness? | User | No, only to breaks longer than the rule |
| Q9 | Where does the user mark an illness (Trainer card, block menu, notification centre) and how is it shown ("Enfermo" chip on the days and on the Trainer)? Copy in Spanish. | User + `ux-reviewer` | Trainer, next to "Tu bloque" |
| Q10 | Epic number and order | User | **Resolved (user, 2026-11-15):** Illness is E20 and runs before Closure, renumbered E21 |

## Decisions to take (ADRs and doc updates)
- **ADR (new):** illness is in scope, up to where (layers 1-2; layer 3 separately); amends the "medical advice" and "illness rules" exclusions in `01-scope.md`, E13/E14/E15 non-goals and `07` §12.5; builds on ADR-106.
- **ADR (new, if Q1 changes it):** what a block counts (weeks or sessions) and what ends it.
- **ADR (new, layer 3):** whether and how the app suggests food for an illness, with its disclaimer and sources.
- **Docs to update with the tasks:** `01-scope.md` §4, `04` (new rules and the pause state), `07` §12.5 (owner `fitness-scientist`), `05` §8-§10 (new rule and glossary for "Enfermo" and its copy), `10-feature-inventory.md`, the data model (`03`) if a table for illness days is needed.

## First task (defines the epic)
**T-A · Define the epic with the user and `fitness-scientist`.** Resolve Q1-Q10 (the sports-science and nutrition ones through `fitness-scientist` first, with sources opened in the session and recorded in `07`), approve the Intent, then write the stories and Gherkin for approval before they enter the repo, and record the ADRs. Only after that the `epic-planner` breaks the epic into tasks.
