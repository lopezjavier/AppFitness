# AppFitness Planning

Live planning docs (current state, kept short). Claude Code operating rules live in `../CLAUDE.md`. Phase 1 is finished and frozen in [`phase1/`](phase1/), Phase 2 is finished and frozen in [`phase2/`](phase2/). Phase 3 continues in the live docs below.

## Index

| Document | Content |
|---|---|
| [00-decisions.md](00-decisions.md) | Index of all ADRs (one line each, ADR-01..107); full text of the Phase 3 ADRs (from ADR-101) |
| [01-scope.md](01-scope.md) | Phase 3 stub: scope to be written by the product-owner skill |
| [02-architecture.md](02-architecture.md) | KMP modules, layers, libraries, security and CI/CD |
| [03-data-model.md](03-data-model.md) | Tables, RLS, RPCs and Edge Functions (summary) |
| [04-business-rules.md](04-business-rules.md) | Formulas and calculation rules (TDEE, macros, MA7, 1RM, coach rules...) |
| [05-design-system.md](05-design-system.md) | Palette, typography, components, UX rulebook (§8-§10) |
| [06-agent-workflow.md](06-agent-workflow.md) | Claude Code workflow, commands and subagents |
| [07-coach-design.md](07-coach-design.md) | Coach design: evidence-based rules, figures and sources; cross-phase, owned by fitness-scientist |
| [08-environment-setup.md](08-environment-setup.md) | Tools, accounts, secrets and local development |
| [09-open-questions.md](09-open-questions.md) | Phase 3 open questions (stub; Phase 2 items still open are carried there) |
| [10-feature-inventory.md](10-feature-inventory.md) | Every functionality of the app by area, with route and story; kept up to date by the task that adds or removes a feature |
| [epics/](epics/) | One file per epic with Gherkin stories. E0-E9 are phase 1, E10-E14 are phase 2; new epics continue at E15 |

## Frozen history

- [`phase1/`](phase1/): full-text copies of every Phase 1 document (ADR-01..72, scope, roadmap, UX audit...), never edited.
- [`phase2/`](phase2/): full-text copies of the Phase 2 documents (00-09 and this README), never edited after the archive. Their relative links point to the old live locations (`phase1/`, `epics/`, `07-coach-design.md`) and resolve only from this folder's parent.

## How to use this folder
1. The PM breaks each epic in `epics/` into `T-###` tasks (`tasks/todo.md`, plan in `tasks/plan.md`); every task inherits the Gherkin of its `US-##`.
2. Before implementing a story the planner reads the epic file, `03-data-model.md`, `04-business-rules.md` and `05-design-system.md`.
3. A new decision = a new ADR in `00-decisions.md` plus updates to the affected docs.

## Language and identifiers
Repository content is English; the app UI is Spanish (UI literals quoted in Gherkin stay Spanish, ADR-16). Prefixes: `E#` epic, `US-##` story (global numbering, stable), `T-###` task, `ADR-##` decision. Branches `feature/T-###-<slug>`; tags `vX.Y.Z` at epic closure.
