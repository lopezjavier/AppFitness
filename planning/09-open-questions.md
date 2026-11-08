# Open questions (Phase 3)

The Phase 3 scope was settled in chat on 2026-11-08 (ADR-101, `01-scope.md`); each epic's questions are added here as its Intent is agreed. Earlier questions and their resolutions: Phase 2 in [`phase2/09-open-questions.md`](phase2/09-open-questions.md), Phase 1 in [`phase1/09-open-questions.md`](phase1/09-open-questions.md).

## Carried over from Phase 2 (still marked open there, not resolved)

These three rows were still marked "open" in `phase2/09-open-questions.md` (frozen). Closed here:

| Question | Raised | Status | Where to look |
|---|---|---|---|
| E13 Q48: what "Eliminar historial del entrenador" deletes now (US-74) | 2026-11-01 | **Out of scope for the app (user, 2026-11-08, ADR-101).** "Eliminar historial del entrenador" does not exist and will not: personal app, and E20 adds a data export. | `phase2/09-open-questions.md` row E13 Q48 |
| E13 Q52: training around an injury without an injured area (US-87; `07` §7, §12.1, §12.8 row 7) | 2026-11-02 | **Resolved by `fitness-scientist` (2026-11-07), no user decision needed:** the app has no injured-area field; discomfort is logged per exercise with a level. Levels 1-3: load increases stop for 7 days, the exercise stays. Levels 4-5: no load suggestion for that exercise, a swap to a same-pattern exercise is offered and applied only on tap, then a 3-step return; after 14 days a note suggests a professional. Rules and sources in `07-coach-design.md` §7 and §12.1 (dated 2026-11-07). | `07-coach-design.md` |
| E13 Q55: life changes once the model builds blocks; drop "Adaptar el bloque"? (US-91) | 2026-11-03 | **Resolved (user, 2026-11-08, ADR-101).** "Adaptar el bloque" is dropped. Every life change (days, goal, trip) goes through "Bloque nuevo", the early review that opens the pre-filled builder; E17 provides it for goal changes and E15 offers the same entry after a break. | `phase2/09-open-questions.md` row E13 Q55 |
