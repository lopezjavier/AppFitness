# AppFitness Planning

Live planning docs (current state, kept short). Claude Code operating rules live in `../CLAUDE.md`. Phase 1 is finished and frozen under [`phase1/`](phase1/); phase 2 continues here.

## Index

| Document | Content |
|---|---|
| [00-decisions.md](00-decisions.md) | Index of all ADRs (one line each); full text of the Phase 2 ADRs (73+) |
| [01-scope.md](01-scope.md) | Phase 2 scope: epics E10-E13, decisions, NFR-02 change, amendments to Phase 1 planning |
| [02-architecture.md](02-architecture.md) | KMP modules, layers, libraries, security and CI/CD |
| [03-data-model.md](03-data-model.md) | Tables, RLS, RPCs and Edge Functions (summary) |
| [04-business-rules.md](04-business-rules.md) | Formulas and calculation rules (TDEE, macros, MA7, 1RM...) |
| [05-design-system.md](05-design-system.md) | Palette, typography, components, UX rulebook (§8-§10) |
| [06-agent-workflow.md](06-agent-workflow.md) | Claude Code workflow, commands and subagents |
| [07-coach-design.md](07-coach-design.md) | Coach design: evidence-based rules, figures, sources and AI cost estimate (E13) |
| [08-environment-setup.md](08-environment-setup.md) | Tools, accounts, secrets and local development |
| [09-open-questions.md](09-open-questions.md) | Open product questions for phase 2 |
| [epics/](epics/) | One file per epic with Gherkin stories. E0-E9 are phase 1; phase 2 continues at E10 |

## Frozen history: `phase1/`

Full-text copies of every Phase 1 document, never edited: `00-decisions.md` (full ADR text), `01-phase1-scope.md` (requirements and deferred items), `02`-`06`, `07-roadmap.md`, `08`, `09-open-questions.md` (resolved questions), `ux-audit-2026-10.md` (evidence behind the UX rulebook) and its own `README.md`. The live docs link to them for detail.

The Stitch mockups were removed after phase 1; they are recoverable from git history. `05-design-system.md` is the visual source of truth.

## How to use this folder
1. The PM breaks each epic in `epics/` into `T-###` tasks (`tasks/todo.md`, plan in `tasks/plan.md`); every task inherits the Gherkin of its `US-##`.
2. Before implementing a story the planner reads the epic file, `03-data-model.md`, `04-business-rules.md` and `05-design-system.md`.
3. A new decision = a new ADR in `00-decisions.md` plus updates to the affected docs.

## Language and identifiers
Repository content is English; the app UI is Spanish (UI literals quoted in Gherkin stay Spanish, ADR-16). Prefixes: `E#` epic, `US-##` story (global numbering, stable), `T-###` task, `ADR-##` decision. Branches `feature/T-###-<slug>`; tags `vX.Y.Z` at epic closure.
