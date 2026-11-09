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
| [E15 Real life and goal switching](epics/E15-real-life-and-goal-switching.md) | A missed week pauses the block, a "welcome back" notice after a break, a discomfort follow-up with the existing rules, a block-end alert in "Centro de avisos", and goal switching with "Bloque nuevo" (US-106…US-111). **Done, `v1.6.0`.** |
| [E16 Nutrition follow-up](epics/E16-nutrition-follow-up.md) | A nutrition section in the weekly summary, a nutrition "¿Por qué no avanzo?", a plateau explanation and a diet-break proposal; deterministic (US-112…US-115). **Done, `v1.7.0`.** |
| [E17 Food data quality](epics/E17-food-data-quality.md) | A macro/kcal check, an audit of existing foods, and Open Food Facts as a fallback that copies into editable personal foods (US-116…US-119) |
| [E18 Functional improvements](epics/E18-functional-improvements.md) | Anthropic removed (code, key, "Uso de IA", cost caps), a stored history of weekly and block reports, a Gemini chat about an exercise, a compact Trainer with a Details screen, a quick-search icon in Diario and Entreno, favourites first in food search (US-128, US-129, US-130, US-120…US-122) |
| [E19 UI review](epics/E19-ui-review.md) | A way to see UI changes without installing the APK, then a paired review of Diario, Entreno, Trainer and Perfil (US-131, US-123) |
| [E20 Closure](epics/E20-closure.md) | Supabase keep-alive, a secured data export, a private repository and a project retro (US-124…US-127) |

Intents and stories approved by the user on 2026-11-08; the `epic-planner` breaks them into tasks.

## 3. Rules for every epic
- Screens are built from the existing design-system components, so E19 can review them in one pass.
- Any sports-science rule (for example the macro/kcal tolerance) goes through `fitness-scientist` first and is recorded in `07`.
- Security and data protection are explicit requirements wherever personal data is copied, exported or stored.

## 4. Out of scope for the app
Final, not deferred. New items come only through bugfixes or the user's feedback in maintenance mode.
- Water tracking.
- Voice and text-description food logging, whole-meal photo estimation, and faster-logging extras (copy day, Quick Add).
- The model-written weekly report and the coach chat (the only chat is the exercise chat of E18 US-130; the app-help chat was dropped, ADR-103). Anthropic and every paid model are out (ADR-103).
- A calendar-style long-term plan and an event date.
- "Recuperar" and any maintenance bridge between phases.
- Meal plans, refeeds and reverse dieting; medical advice; video analysis.
- "Eliminar historial del entrenador" and "Adaptar el bloque" (life changes use "Bloque nuevo").
- Training streak and rewards; monthly report and year in review (ADR-66); web client.

## 5. Open product questions
See [`09-open-questions.md`](09-open-questions.md).
