# AppFitness

Android app for everyday life at the gym and in the kitchen: **calorie and macro tracking**, **body weight tracking** with a smoothed trend, and **workout logging** with previous performance and a rest timer. A personal, private-use project without ads (see [License](#license)).

> **Status:** setup in progress (epic E0). There is no app code yet; see the [roadmap](planning/07-roadmap.md).

## Features (Phase 1)

| Section | What it does |
|---|---|
| **Diary** | Search foods (Open Food Facts), scan a barcode or capture a photo (AI dish estimate, label or barcode), log them into 4 meals, saved comidas and weekday defaults, cooking methods with added fat, kcal ring and macro bars |
| **Progress** | Manual weight or synced from Health Connect, 7-day moving average chart, weekly rate of change |
| **Workout** | Routines with supersets, live session with the screen always on, previous performance per set, rest timer, history and estimated 1RM |
| **Profile** | Sign in with Google, biometric data, goal (deficit / maintenance / surplus), macro split |

The app UI is in Spanish; the repository is in English.

Phase 2 adds whole-meal photo estimation (several foods at once), adaptive calorie adjustment and the AI training assistant.

## Stack

- **App:** Kotlin Multiplatform + Compose Multiplatform (Android in Phase 1; web in Phase 2), Koin, supabase-kt, Vico.
- **Backend:** Supabase (Postgres with Row Level Security, Google Auth, Edge Functions in Deno/TypeScript).
- **Quality:** ktlint, detekt, unit tests, pgTAP and GitHub Actions.
- **Design:** custom "Kinetic Dark" design system ([planning/05-design-system.md](planning/05-design-system.md)).

## Repository structure

```
planning/        Phase 1 planning: decisions, scope, architecture, data model,
                 business rules, design system, workflow and epics with Gherkin stories
tasks/           Plan and task list of the current epic
CLAUDE.md        Instructions for Claude Code (Project Manager role and project rules)
.claude/agents/  Claude Code subagents (planner, ux-reviewer, developers, PR)
design/stitch/   Visual reference mockups (Google Stitch)
```

Code lives in `androidApp/` (Android application: flavors, `MainActivity`), `composeApp/` (Compose UI, KMP library) and `shared/` (domain and data, KMP library), plus `supabase/` and `.github/` (see [planning/02-architecture.md](planning/02-architecture.md)).

## How it is developed

Development is done by **Claude Code** acting as a Project Manager that coordinates specialized subagents per task:

```
planner → ux-reviewer → developer(s) → PR creator → PR reviewer → merge (green CI)
```

Details in [CLAUDE.md](CLAUDE.md) and [planning/06-agent-workflow.md](planning/06-agent-workflow.md).

## Getting started

Requirements, accounts and local configuration: [planning/08-environment-setup.md](planning/08-environment-setup.md).

## License

**Personal use** project. All rights reserved: no permission is granted to use, copy, modify or distribute this code. See [LICENSE](LICENSE).

## Security

The repository is public and **contains no secrets**. Keys live in GitHub Secrets, in Supabase and in `local.properties` (git-ignored). Each user can only access their own data thanks to Row Level Security.
