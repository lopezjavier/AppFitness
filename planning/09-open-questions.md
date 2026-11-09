# Open questions (Phase 3)

The Phase 3 scope was settled in chat on 2026-11-08 (ADR-101, `01-scope.md`); each epic's questions are added here as its Intent is agreed. Earlier questions and their resolutions: Phase 2 in [`phase2/09-open-questions.md`](phase2/09-open-questions.md), Phase 1 in [`phase1/09-open-questions.md`](phase1/09-open-questions.md).

## Carried over from Phase 2 (still marked open there, not resolved)

These three rows were still marked "open" in `phase2/09-open-questions.md` (frozen). Closed here:

| Question | Raised | Status | Where to look |
|---|---|---|---|
| E13 Q48: what "Eliminar historial del entrenador" deletes now (US-74) | 2026-11-01 | **Out of scope for the app (user, 2026-11-08, ADR-101).** "Eliminar historial del entrenador" does not exist and will not: personal app, and E19 adds a data export. | `phase2/09-open-questions.md` row E13 Q48 |
| E13 Q52: training around an injury without an injured area (US-87; `07` §7, §12.1, §12.8 row 7) | 2026-11-02 | **Resolved by `fitness-scientist` (2026-11-07), no user decision needed:** the app has no injured-area field; discomfort is logged per exercise with a level. Levels 1-3: load increases stop for 7 days, the exercise stays. Levels 4-5: no load suggestion for that exercise, a swap to a same-pattern exercise is offered and applied only on tap, then a 3-step return; after 14 days a note suggests a professional. Rules and sources in `07-coach-design.md` §7 and §12.1 (dated 2026-11-07). | `07-coach-design.md` |
| E13 Q55: life changes once the model builds blocks; drop "Adaptar el bloque"? (US-91) | 2026-11-03 | **Resolved (user, 2026-11-08, ADR-101).** "Adaptar el bloque" is dropped. Every life change (days, goal, trip) goes through "Bloque nuevo", the early review that opens the pre-filled builder; E15 provides it for goal changes and after a break. | `phase2/09-open-questions.md` row E13 Q55 |
| E15 Q1: where "Bloque nuevo" lives | 2026-11-08 | **Resolved (user, 2026-11-08)**; built in T-240 | Always on the Trainer root during a block, in the place of "Crear plan nuevo" |
| E15 Q2: how the goal is changed | 2026-11-08 | **Resolved (user, 2026-11-08)**; built in T-240 | A Trainer button opens "Mi entrenamiento"; the goal is still picked there, no goal picker in the Trainer |
| E15 Q3: graded return after a break in US-108 | 2026-11-08 | **Resolved (user, 2026-11-08)**: not built | The graded return stays out of the discomfort follow-up |
| E15 Q4: a paused week | 2026-11-08 | **Resolved (user, 2026-11-08)**; built in T-242 | Shows only "Semana en pausa", no notice; calorie proposals keep their own data gate |
| E15 Q5: time-based triggers and checkpoints | 2026-11-08 | **Resolved (user, 2026-11-08)** | The user does not force time-based triggers; checkpoints hold only what can be tried at once, the rest is "Pending natural-trigger feedback (E15)" in `tasks/todo.md` |
| E16 Q1: "Meseta" and a calorie proposal in the same week | 2026-11-08 | **Resolved (experts, `07` §4 "Nutrition follow-up" (c))**; built in T-247 | Both show: the "Meseta" line first, the proposal as its one action; no proposal in a week where the calorie cause fires |
| E16 Q2: order of the causes in the nutrition diagnosis | 2026-11-08 | **Resolved (experts, `07` (c))**; built in T-247 | The cause wins: logging > average calories > "Meseta" > "Sin causa clara"; protein, sleep and steps dropped (no evidence-backed threshold) |
| E16 Q3: where "Descanso de dieta" is offered | 2026-11-08 | **Resolved (experts)**; built in T-248 | Only in the plan proposal's cut options, editable afterwards in "Tu plan"; no switch on the "Bloque nuevo" confirmation or the review sheet |
| E16 checkpoints | 2026-11-08 | **Resolved (user)** | One checkpoint at the end; branches needing two real weeks stay in "Pending natural-trigger feedback (E16)" |
