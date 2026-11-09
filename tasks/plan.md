# Implementation Plan: E18 · Functional improvements → `v1.x` (proposed `v1.9.0`)

> **Status: PROPOSED by the epic-planner, 2026-11-09; updated the same day with the user's feedback.** The task list (T-259…T-267) needs the user's OK on the breakdown PR; one checkpoint only, Checkpoint E18 at the end (user decision 2026-11-09). Q1 and Q2 are resolved by the user (2026-11-09, "Decisions" below). The only open user item is the 👤 Gemini terms check, which gates T-265. The E17 plan that lived here is in git history (E17 closed, `v1.8.0`; `tasks/todo.md` E17 section).

## Overview
The app drops every trace of Anthropic (Edge Function code, key, `coach_usage`, "Uso de IA", the monthly limit, the model selector) and is "100 % free" again. Weekly summaries and block reviews are stored as snapshots from now on and reopened from "Informes" in Centro de avisos. Diario and Entreno get a magnifier that opens a view-only food or exercise search, and the food search lists favourites and my foods at once, with Open Food Facts after. The Trainer shows the essentials and moves the full nutrition plan and block into "Detalles". Last, an exercise's details get a chat tab where a Gemini trainer-expert answers single questions about that exercise only.

Sources: `planning/epics/E18-functional-improvements.md` (intent, non-goals, US-128, US-129, US-130, US-120…US-122), ADR-103, ADR-17, ADR-59, ADR-66, ADR-98, ADR-101, ADR-104, `04` §6.1, §6.7, §6.9, `03` §3, §7, `05` §8-§10, `01` §4. Tasks: `tasks/todo.md` (E18 section).

Out of scope (epic non-goals, `01-scope.md`): comparing reports; storing reminders; saving the chat; a chat disclaimer; chats outside an exercise; a monthly report (ADR-66); any model in the numbers (ADR-98); a new look (E19). Also out of E18 by user decision: rebuilding old reports, follow-up context in the chat, an app-side chat limit.

## Decisions (user, 2026-11-09)
In plain words:
- **Old reports are not rebuilt (Q1(a)).** "Informes" starts with the weekly summaries produced after the update. Older weeks were never saved, and rebuilding them today could show different numbers, so they are not added. The block reviews already in the database are listed, because they were saved when they were shown.
- **The block-end notice is a report (Q1(b)).** Opening "Tu bloque ha terminado: revísalo" from Centro de avisos removes it from the active list, and the review stays in "Informes". The Trainer's "Revisión del bloque" card still shows until the next block is saved. T-261 amends `04` §6.9 ("opening never clears it").
- **Past reports are read only (Q1(c), the planner's default taken by the user).** A report opened from "Informes" shows its figures and the decision you took, as text. It has no buttons to decide again ("Aplicar", "Ahora no", "Aceptar", "Seguir como estoy", "Crear el siguiente bloque"). The current report opened from its notice keeps its buttons.
- **Paused weeks (Q1(d), the planner's default taken by the user).** A "Semana en pausa" week appears in "Informes" only if its summary was actually produced and shown to you. A paused week nobody opened leaves no report.
- **One question at a time in the chat (Q2(a)).** Each question is answered on its own: the app sends only the exercise (name, muscles, instructions) and that question, never the earlier questions or answers. This matches the Gherkin, and the chat empties when you leave the exercise anyway. A chat that remembers the conversation is a possible idea for a later epic.
- **The daily limit is Gemini's (Q2(b)).** When Gemini says it is busy or its quota is used up, the Edge Function catches it and the app shows a short reason. The app keeps no count of questions and has no limit of its own.

## Architecture decisions
- **Anthropic goes backend first, then app.** T-259 removes the provider, budget and prices from the `coach` Edge Function and drops `coach_usage`. Every deterministic kind keeps its exact response, and the handler ignores a `model` field, so the installed app keeps working until T-260's APK. T-260 removes the app side. The user unsets the secret and revokes the key (👤); the PM only verifies that the name is gone.
- **Reports are snapshots, written when produced.** A new owner-scoped table holds the exact JSON a weekly summary showed. It is stored the first time the app builds a finished week's summary (idempotent security definer writer, the `open_coach_block_review` precedent), and later reads return the stored snapshot. No backfill and no gap filling. Block reviews are already frozen in `coach_block_reviews`; they are listed from there and gain a seen mark for the block-end notice. Reminders are never stored (`04` §6.1).
- **One renderer per report type.** A past report is drawn by the same mapper and content composables as the live `WeekSummaryScreen` and `BlockReviewScreen`, in a read-only mode, with no second copy of the layout.
- **Quick search reuses the existing searches** in a view-only mode (no target meal or workout, no "Añadir"). The food search shows my block (favourites and my foods) before it waits for `food-search`.
- **The compact Trainer moves cards; it does not rebuild them.** The cards and rows of `TrainerContent` move between the Trainer root and a pushed "Detalles" screen. ux-reviewer writes the content map in the plan, and "Nothing is lost" is tested against it.
- **The chat is a thin, closed, single-turn Edge Function.** `exercise-chat` takes only the exercise's name, muscles, instructions and one question. It has no tools and no database access, stores, counts and logs nothing, and maps Gemini busy/quota/timeout errors to codes the app names. It shares a Gemini client extracted from `food-photo`. The app keeps the conversation only in memory and saved state: it survives leaving the app and is cleared when you leave the exercise.

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR. There is no S task.

| Order | Task | Size | Story | Flow | Agent runs |
|---|---|---|---|---|---|
| 1 | T-259 (**M**): Anthropic removed: Edge Functions, `coach_usage` dropped, decisions | **M** | US-128 (backend) | backend | 5 |
| 2 | T-260 (**M**): "Uso de IA", the limit and the model selector removed from the app | **M** | US-128 | core + UI | 7 |
| 3 | T-261 (**M**): Reports stored: snapshots, their list, the block-end seen mark | **M** | US-129 (backend) | backend | 5 |
| 4 | T-262 (**M**): "Informes": past reports from Centro de avisos | **M** | US-129 | core + UI | 7 |
| 5 | T-263 (**M**): Quick search in Diario and Entreno, and my foods at once | **M** | US-121, US-122 | core + UI | 7 |
| 6 | T-264 (**M**): A compact Trainer with "Detalles" | **M** | US-120 | UI + core | 7 |
| — | 👤 Gemini terms check (decision gate, any time before T-265) | — | US-130 | user | — |
| 7 | T-265 (**M**): The exercise chat Edge Function | **M** | US-130 (backend) | backend | 6 |
| 8 | T-266 (**M**): Ask about an exercise: the chat tab | **M** | US-130 | core + UI | 7 |
| — | **👤 Checkpoint E18** (the only one; final regression) | — | | | |
| 9 | T-267: E18 closure | closure step (no size class) | DoD | PM | PM only |

Why these splits and merges:
- **US-128 → T-259 + T-260:** about 45 files (Edge Function, shared client, migration, pgTAP, docs, then the app's screen, models, repository, DI and tests). Backend and app are reviewed apart, and the backend must land first (production data drop, epic order).
- **US-129 → T-261 + T-262:** about 20 files (migration, RPCs, pgTAP, then repository, list, read-only viewer of two report types, Centro de avisos entry, routes, tests).
- **US-121 + US-122 → T-263:** US-122 alone is S (T-255 already orders the list) and touches the same `FoodSearchViewModel`/`FoodSearchScreen` as US-121's Diario path.
- **US-120 → T-264:** one UI task (cards move; about 10 files).
- **US-130 → T-265 + T-266:** about 16 files. The closed backend (prompt, data minimisation, error mapping, the shared Gemini client and the `food-photo` sweep) is reviewed apart from the chat tab.
- **Closure T-267** is released by Checkpoint E18's approval. It has no checkpoint of its own and no size class: it cannot merge into a task because it waits for the approval.

## Phases and checkpoint

Items to test in the epic, collected first: (1) no "Uso de IA", limit or model selector; (2) the plan, block builder, folder review, weekly summary and food photo work as before, data intact; (3) "Informes" newest first, a report as it was shown, read only; (4) back and open another; (5) opening a report notice (weekly or block-end) clears it and it stays in "Informes"; (6) reminders never in "Informes"; (7) the Diario and Entreno magnifiers; (8) view-only details without "Añadir"; (9) favourites and my foods at once, OFF after; (10) Trainer essentials; (11) "Detalles" full content; (12) nothing lost against screenshots; (13) chat tab and a trainer-expert answer; (14) the chat takes no actions; (15) offline gives a short message and leaving the exercise empties the chat; (16) leave the app and come back on each new screen (final regression). Not testable by hand (pgTAP/Deno/host tests): no key or Anthropic code, `coach_usage` gone, another account's reports, only the exercise and one question sent, the busy and quota messages.

**User decision (2026-11-09): one checkpoint only, at the end** (the E16/E17 precedent). The 16 items are kept grouped by functionality: Anthropic removed 2, past reports 4, quick search 3, compact Trainer 3, exercise chat 3, leave and come back 1.

### Phase 1: Anthropic removed and past reports (US-128, US-129)
- T-259 → T-260 → T-261 → T-262. No checkpoint here (user decision). `deploy-supabase` green after T-259 and T-261 and the 👤 secret removal are task verification items and preconditions of Checkpoint E18.

### Phase 2: Quick search and the compact Trainer (US-121, US-122, US-120)
- T-263 → T-264. No checkpoint here.
- **👤 Gemini terms check** (decision gate for T-265): can be done any time before T-265, for example while T-263 or T-264 runs.

### Phase 3: Ask about an exercise, the checkpoint and closure (US-130)
- T-265 → T-266 → **👤 Checkpoint E18** → T-267.
- **👤 Checkpoint E18 — Functional improvements, with the final E18 regression** (after T-266, the last implementation task; 16 items in `tasks/todo.md`; its approval releases T-267). Take screenshots of the Trainer before installing. Try: no "Uso de IA", limit or model selector, and everything you use works as before; "Informes" from Centro de avisos lists your reports newest first, each read only as it was shown; back and open another; opening a report notice clears it; reminders never appear in "Informes"; the magnifiers in Diario and Entreno open searches whose results have no "Añadir"; your foods appear at once with OFF after; the Trainer shows its essentials, "Detalles" holds the full plan and block, and nothing is missing against the screenshots; the chat tab answers as a trainer expert, takes no actions, explains when there is no answer and empties when you leave the exercise; leave the app and come back on each new screen.

## Dependencies
- T-259 needs the breakdown approved. T-260 needs T-259 merged and deployed. T-261 needs T-260 merged. T-262 needs T-261 merged and deployed. T-263 needs T-262 merged (no build dependency). T-264 needs T-263. T-265 needs T-264 merged and the **👤 Gemini terms check** decided. T-266 needs T-265 merged and deployed. T-267 needs Checkpoint E18 approved.
- Built pieces E18 relies on: the deterministic `coach` kinds (ADR-97…99), `coach_week_summary`/`coach_week_summary_week` and `coach_week_summary_seen` (T-231, T-242, T-246, T-247), `coach_block_reviews` and `open_coach_block_review` (T-233, T-243), Centro de avisos (E10, T-231, T-243), the `FoodSearchViewModel` own-first block and the `food-search` ordering (T-230, T-254, T-255), the exercise details tabs (T-179), `food-photo` and `GEMINI_API_KEY` (ADR-59).

## Risks
- **Production drop of `coach_usage` (T-259).** Mitigation: drop only budget objects; row counts of every other table checked before and after; `deploy-supabase` confirmed with data intact before Checkpoint E18.
- **Old installed app against the new backend (T-259 → T-266).** With one checkpoint, the phone keeps the E17 APK until the end of the epic, while each backend change deploys on merge. Mitigation: the `coach` handler ignores `model`; T-261 adds tables and RPCs without changing what the E17 app reads; only the old "Uso de IA" screen fails until the new APK. Every backend task checks that the E17 app's calls still work.
- **Removing the streaming path breaks a deterministic kind (T-259).** Mitigation: every kind's Deno tests are kept; responses are compared before and after.
- **Weeks that are never produced (T-261).** With no gap filling, a finished week whose summary the app never builds (for example, the app is not opened before the next week ends) leaves no report. This is accepted by Q1(a)/(d); the app builds the latest summary on every Trainer or Centro de avisos load, so this only happens after a long absence.
- **Snapshot drift (T-262).** A later change of the summary JSON shape could break old snapshots. Mitigation: the mapper tolerates missing keys (the T-246 precedent: "a summary built before T-246 lacks the key"); a test renders an older snapshot.
- **Tab-root header crowding (T-263).** Diario's header would hold the bell, the calendar and the magnifier, and R-02 caps object actions at 2 icons plus ⋮. Mitigation: ux-reviewer rules on it in the plan (the epic fixes the position).
- **Trainer content regression (T-264).** Moving cards can drop a figure. Mitigation: the content map in the plan, one host test per figure group, and screenshots before the checkpoint.
- **Gemini terms and quota (T-265).** The free tier may not be allowed in the EEA, or its daily quota may be low. Mitigation: the 👤 check before T-265; the paid tier with a low limit as fallback (user decision); the quota message in the app.
- **Chat scope (T-265).** A model may drift into medical or unrelated advice. Mitigation: a scoped system prompt reviewed by `fitness-scientist`, no tools, no user data in the request.
- **A late checkpoint (one at the end).** Feedback on T-259…T-264 arrives after the chat is built. Mitigation: those tasks touch different screens from the chat, so fixes do not rework it.

## Open questions
- **👤 Gemini terms check: RESOLVED (user, 2026-11-09): free tier, same as `food-photo`, which already works on it; no check, T-265 does not wait. No open user item remains. (Superseded text:)** the user checks Google's current Gemini API terms for Spain/EEA and decides free tier or paid tier with a low spending limit, for the chat and `food-photo`.
- **Q1 · RESOLVED (user, 2026-11-09):** see "Decisions" (no backfill; the block-end notice is a report; past reports read only; paused weeks only when shown). Recorded in `09-open-questions.md` by the closure T-267 (not tracked there today).
- **Q2 · RESOLVED (user, 2026-11-09):** see "Decisions" (single-turn; Gemini's quota only). Multi-turn chat is a possible later-epic idea. Recorded in `09-open-questions.md` by T-267.
- **Q3 (non-blocking, delegated to ux-reviewer):** the chat tab label, the "Informes" entry position in Centro de avisos, the Trainer/"Detalles" content map (where "Tus datos", "Lo que el entrenador sabe de ti" and "Bloque nuevo" land), and the glossary row that lists "Informe" as a forbidden word for the weekly summary (amended so "Informes" names the list).
