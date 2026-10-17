# AppFitness Planning — Phase 1

This folder is the **source of truth** for Phase 1 planning. It supersedes the original high-level documents (requirements, frontend features, user stories, orchestrator, workflow and local development notes), which are kept outside the repository. Claude Code operating rules live in `../CLAUDE.md`.

Phase 2 (web client and AI) is out of scope for this planning; only what is deferred is noted.

## Index

| Document | Content |
|---|---|
| [00-decisions.md](00-decisions.md) | Closed decisions (short ADRs) and findings from the review of the original documents |
| [01-phase1-scope.md](01-phase1-scope.md) | Consolidated Phase 1 requirements and what is out of scope |
| [02-architecture.md](02-architecture.md) | KMP modules, libraries, layers, security and CI/CD |
| [03-data-model.md](03-data-model.md) | Tables, relationships, RLS policies, RPCs and Edge Functions |
| [04-business-rules.md](04-business-rules.md) | Formulas and calculation rules (TDEE, macros, moving average, 1RM…) |
| [05-design-system.md](05-design-system.md) | Unified palette, typography, Compose components and mockup references |
| [06-agent-workflow.md](06-agent-workflow.md) | Claude Code workflow, subagents and their specification |
| [07-roadmap.md](07-roadmap.md) | Epics, order, dependencies, versions and deliverables |
| [08-environment-setup.md](08-environment-setup.md) | Tools, accounts, secrets and local development |
| [epics/](epics/) | One file per epic with its user stories in Gherkin |

## How to use this folder

1. For each epic in `epics/`, the PM (Claude Code) breaks its stories into `T-###` tasks with the `planning-and-task-breakdown` skill and records them in `tasks/todo.md`; the current epic's plan goes in `tasks/plan.md` (ADR-15). E0 is already broken down (T-001…T-013).
2. Every task references the story it comes from (`US-##`) and inherits its Gherkin criteria.
3. Before implementing any story, the Planner reads: the epic file, `03-data-model.md`, `04-business-rules.md` and `05-design-system.md`.
4. Any new decision that changes what is written here is added to `00-decisions.md` (new ADR) and the affected documents are updated.

## Language

All repository content (docs, code, comments, commits, PRs) is in **English**. The app UI is in **Spanish** (NFR-10): UI strings quoted in Gherkin scenarios stay in Spanish because they are the literal texts the tests assert.

## Identifier conventions

| Prefix | Meaning | Example |
|---|---|---|
| `E#` | Epic | `E3` — Training core |
| `US-##` | User story (global numbering) | `US-12` |
| `T-###` | Technical task | `T-031` |
| `ADR-##` | Architecture/product decision | `ADR-03` |

Branches: `feature/T-###-<slug>`. Tags: `vX.Y.Z` when each epic is closed.

## Design resources

- Original Stitch design system: `../design/stitch/kinetic-dark-performance/DESIGN.md`
- Mockups (**style** reference only, not features): `../design/stitch/*/screen.png`
