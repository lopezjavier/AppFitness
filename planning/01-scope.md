# Phase 3 scope

Approved by the user on 2026-11-08 (ADR-101). Phase 3 is the last phase. Nothing outside this document is in scope without asking (see `../CLAUDE.md`).

## Where the earlier scope is
- Phase 2 scope, decisions and amendments (frozen): [`phase2/01-scope.md`](phase2/01-scope.md).
- Phase 1 scope (frozen): [`phase1/01-phase1-scope.md`](phase1/01-phase1-scope.md).

## 1. Goal
Finish the app. Close the gaps that matter in real use, then leave it in maintenance mode: only bugfixes and the user's feedback create new tasks. The app is for personal use: one person, with a second account allowed for testing.

## 2. Epics (in this order)

| Epic | Content |
|---|---|
| E15 Real life | The coach copes with missed weeks (the block pauses), a "welcome back" notice after a break and a logged discomfort, using the existing 14-day return rule; no new phase |
| E16 Nutrition follow-up | A nutrition section in the weekly summary, an adherence diagnosis, a plateau explanation and a diet-break proposal inside the cut-phase proposal; deterministic, no model |
| E17 Goal switching | Changing the goal mid-block offers "Bloque nuevo"; figures are recomputed from the new goal, the proposal counters restart, a confirmation line shows the calorie change; no forced bridge |
| E18 Food data quality | A deterministic check that macros match kcal when a food is created, edited or estimated by AI; an audit of existing foods fixed from a list; Open Food Facts as a fallback that copies into editable personal foods |
| E19 UI review | Diario, Entreno, Trainer and Perfil improved without changing the style; the Trainer is compact, with the nutrition plan and the block on a Details screen; a magnifier search icon in Diario and Entreno; favourites first in food search |
| E20 Closure | Supabase keep-alive, secured data export of the user's own data, repository made private, project retro for future apps |

Each epic's Intent is agreed with the user before its file and stories are written.

## 3. Rules for every epic
- Screens are built from the existing design-system components, so E19 can review them in one pass.
- Any sports-science rule (for example the macro/kcal tolerance) goes through `fitness-scientist` first and is recorded in `07`.
- Security and data protection are explicit requirements wherever personal data is copied, exported or stored.

## 4. Out of scope for the app
Final, not deferred. New items come only through bugfixes or the user's feedback in maintenance mode.
- Water tracking.
- Voice and text-description food logging, whole-meal photo estimation, and faster-logging extras (copy day, Quick Add).
- The model-written weekly report and the chat.
- A calendar-style long-term plan and an event date.
- "Recuperar" and any maintenance bridge between phases.
- Meal plans, refeeds and reverse dieting; medical advice; video analysis.
- Training streak and rewards; monthly report and year in review (ADR-66); web client.

## 5. Open product questions
See [`09-open-questions.md`](09-open-questions.md).
