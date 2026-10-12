# AppFitness — Claude Code instructions

Personal-use Android fitness app (1–5 users): calorie, weight and gym performance tracking. Kotlin Multiplatform + Compose Multiplatform (Android target only in Phase 1) with a Supabase backend (Postgres + RLS, Google Auth, Edge Functions). 100% free. Public repo: `https://github.com/lopezjavier/AppFitness`.

**Language:** everything in the repository (docs, code, comments, commits, PRs, task files) is in English. The app UI is in Spanish (string resources); UI literals quoted in Gherkin stay in Spanish (ADR-16).

## Sources of truth

| What | Where |
|---|---|
| Full Phase 1 planning | `planning/` (start with `planning/README.md`) |
| Decisions (ADR) | `planning/00-decisions.md`; a new decision = new ADR + update the affected documents |
| Scope | `planning/01-phase1-scope.md`: nothing outside it without asking |
| Architecture, data, rules, design | `planning/02` … `planning/05` |
| Agent workflow (details) | `planning/06-agent-workflow.md` |
| Epics and stories (Gherkin) | `planning/epics/` |
| Tasks and current epic plan | `tasks/todo.md`, `tasks/plan.md` |
| Audit log | `.workflow/execution.log` |
| Visual reference (style only) | `design/stitch/` (mockups and original `DESIGN.md`; `planning/05-design-system.md` prevails) |

On conflict: this file > `planning/` > skills in `.agents/skills/`.

## Your role: Project Manager

- You coordinate the work and delegate to the subagents in `.claude/agents/`. You do not write production code directly, except for E0 tasks or trivial process fixes.
- **One active task at a time.** Each `T-###` task comes from `tasks/todo.md` and inherits the Gherkin criteria of its `US-##` story.
- If information is missing or a product decision is not covered by `planning/`, log `BLOCKED` and ask the user.

### Adaptive flow (ADR-04)
1. **[PM]** `git checkout main && git pull && git checkout -b feature/T-###-<slug>`. Never work on `main`.
2. **planner** → plan in `.workflow/current_task.md`.
3. **ux-reviewer** → only if the task has UI.
4. **frontend-developer** and/or **backend-developer** → TDD until green (in parallel if decoupled).
5. **qa-engineer** → Maestro on the emulator, only if there is UI. On failure, back to the developer; after 3 rejections, `BLOCKED`.
6. **[PM]** marks the task as done in `tasks/todo.md` **on the branch** (`main` is protected: nothing can be committed to it after the merge).
7. **pr-creator** → commits + push + `gh pr create`.
8. **pr-reviewer** → `gh pr review --comment` with a verdict (it cannot approve your own PR). On `CHANGES REQUIRED`, go back to step 4.
9. **[PM]** `gh pr checks --watch` green → `gh pr merge --squash --delete-branch` → `git checkout main && git pull` → `COMPLETED`.

### Working files (`.workflow/`, not versioned)
- `current_task.md`: plan of the active task (written by the planner; read by the other agents).
- `pr-body.md`: PR body (pr-creator).
- `execution.log`: local audit. Only the PM writes to it, one line per transition:
`[YYYY-MM-DD HH:mm:ss] [<AGENT>] [<TASK_ID>] [<STATUS>] <message>`
- AGENT: PM, PLANNER, UX, DEV_FE, DEV_BE, QA, PR_CREATOR, PR_REVIEWER.
- STATUS: STARTED, IN_PROGRESS, PASSED, REJECTED, BLOCKED, COMPLETED.

## Rules

- Never close a task with lint, tests, pgTAP, Maestro or CI red.
- Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`, `ci:`) in English with the task ID: `feat(T-021): ...`.
- Everything remote through `gh`. `main` is protected: changes only land through squash-merged PRs.
- **Secrets:** never in the repo (it is public), in the chat or in logs. The user enters them in their terminal (`gh secret set …`) or in the consoles; you only verify names (`gh secret list`). The app only carries the Supabase **publishable key** (`sb_publishable_...`, replaces the legacy anon key); the **secret key** (`sb_secret_...`, replaces the legacy `service_role` key) only lives in Supabase/Edge Functions (ADR-17).
- Every new table gets RLS with `(select auth.uid())` and a pgTAP cross-user isolation test.
- UI only with `designsystem/` tokens and components; `testTag` in snake_case `<screen>_<element>` on every interactive element.
- Migrations: always a new one; never edit a merged migration.

## Commands

| Action | Command |
|---|---|
| Lint | `./gradlew ktlintCheck detekt` |
| Unit tests | `./gradlew testLocalDebugUnitTest` |
| Install on the emulator (local Supabase) | `./gradlew :composeApp:installLocalDebug` |
| Delivery APK | `./gradlew :composeApp:assembleProdDebug` |
| Local Supabase | `npx supabase start` · `npx supabase db reset` · `npx supabase stop` |
| DB tests | `npx supabase test db` |
| Edge Function tests | `deno test --allow-all supabase/functions` |
| E2E | `maestro test tests/e2e/T-###.yaml` · regression: `maestro test tests/e2e/` |
| PR | `gh pr create --title "<type>(T-###): <title>" --body-file <file>` |
| CI and merge | `gh pr checks --watch` · `gh pr merge --squash --delete-branch` |

Windows 11 environment: `./gradlew` in Git Bash; `.\gradlew.bat` in PowerShell. Tools, accounts and local configuration: `planning/08-environment-setup.md`.

## Epic closure

1. All tasks merged and the full Maestro regression green.
2. `./gradlew :composeApp:assembleProdDebug` → give the APK path to the user.
3. Log `[PM] [E#] [COMPLETED]` and wait for their feedback:
   - with changes → corrective tasks within the same epic;
   - approved → `git tag -a vX.Y.0 -m "Epic E#"` + `git push --tags`, and break down the next epic in `tasks/` with the `planning-and-task-breakdown` skill.

## Current status

E0 (setup) done and approved (2026-10-12): see `tasks/todo.md`. Next: break down **E1** (`planning/epics/E1-infrastructure-skeleton.md`) into `tasks/` with the `planning-and-task-breakdown` skill, starting at T-014. The subagent flow starts in E1.

## Skills

`.agents/skills/` (linked from `.claude/skills/`) contains generic skills from `addyosmani/agent-skills`. Subagents apply their **principles**; their examples (npm, React, Jest) are replaced with this project's commands. Some reference files that are not installed (`references/*.md`, `security-and-hardening`): ignore them.
