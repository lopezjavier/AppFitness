# Claude Code workflow and subagents

Supersedes the original orchestrator and workflow documents (kept outside the repository). The operational summary lives in `CLAUDE.md`; the subagents are defined in `.claude/agents/*.md`. If this document and `CLAUDE.md` disagree, `CLAUDE.md` wins.

## 1. Principles

1. Claude Code (main session) acts as the **Project Manager (PM)**: coordinates, logs and decides; delegates technical work to subagents.
2. **One active task at a time.** Every task starts from an up-to-date `main` and lives on its own `feature/T-###-<slug>` branch, created **at the start** (never work on `main`).
3. A task is not closed if unit tests, pgTAP or CI fail; Maestro runs in the background after the merge, and a checkpoint is not presented while a Maestro run or a `qa` issue is open (ADR-23).
4. Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`, `ci:`) in English, scoped with the task ID.
5. Remote operations only through `gh`.
6. Merging into `main` is gated by required CI checks (ruleset). The PR Reviewer posts a verdict as a comment; it cannot "approve" a PR from the same user.
7. Only the PM writes `.workflow/execution.log` (avoids conflicts); subagents return a summary and the PM logs it.
8. If information is missing or a product decision is not covered by `planning/`, the PM logs `BLOCKED` and asks the user.

## 2. Adaptive flow (ADR-04)

### 2.1 Task with UI (full flow; QA in the background per ADR-23)
```
[PM] start: check `gh issue list --label qa` (bug → fix task first)
     git checkout main && git pull && git checkout -b feature/T-###-slug
  → planner            plan in .workflow/current_task.md (incl. E2E scope)
  → ux-reviewer        validates the plan vs 05-design-system.md; defines testTags
  → frontend-developer and/or backend-developer (in parallel if decoupled)
                       TDD until green; UI scenarios covered by host tests
[PM] marks the task done in tasks/todo.md on the branch
  → pr-creator         commit(s), push, gh pr create
  → pr-reviewer        gh pr diff → verdict (gh pr review --comment)
        └─ CHANGES REQUIRED → developer
[PM] waits for green CI → gh pr merge --squash --delete-branch → COMPLETED
  ├─ next task starts immediately
  └─ qa-engineer (background, worktree ../AppFitness-qa on the merged main)
                       writes + runs tests/e2e/T-###.yaml and the scoped regression
        ├─ app bug     → GitHub issue labelled `qa` (picked up before the next task)
        └─ flow        → pr-creator opens a `test(T-###)` PR from the worktree
```
Checkpoints and epic closure wait for every background QA run, no open `qa` issue (unless the user defers it) and a green full regression.

pr-reviewer is skipped for PRs that only touch docs or task files; it always runs when code, tests, CI or scripts change.

### 2.2 Infrastructure / backend task without UI
```
[PM] start (same) → planner → backend-developer (pgTAP / deno test / local CI)
  → [PM] mark task → pr-creator → pr-reviewer → [PM] green CI → merge
```

### 2.3 Minor corrective task (epic feedback, < ~50 lines)
Planner optional (short plan in the PR itself); the rest follows the applicable flow.

### 2.4 Why the task is marked on the branch
`main` is protected, so nothing can be committed to it after the merge. The task checkbox in `tasks/todo.md` is updated on the feature branch and becomes effective with the merge. `.workflow/` is local and not versioned.

## 3. Logging (`.workflow/execution.log`, local)

```
[YYYY-MM-DD HH:mm:ss] [<AGENT>] [<TASK_ID>] [<STATUS>] <message / artifact>
```
- `AGENT`: PM, EPIC_PLANNER, PLANNER, UX, DEV_FE, DEV_BE, QA, PR_CREATOR, PR_REVIEWER.
- `STATUS`: STARTED, IN_PROGRESS, PASSED, REJECTED, BLOCKED, COMPLETED. QA's `ISSUES` reply is logged as `REJECTED` with the issue numbers.
- Example:
```
[2026-10-20 10:02:10] [PM] [T-014] [STARTED] Branch feature/T-014-kmp-project created from main
[2026-10-20 10:05:25] [PLANNER] [T-014] [PASSED] Plan in .workflow/current_task.md
[2026-10-20 10:40:00] [DEV_FE] [T-014] [PASSED] testLocalDebugUnitTest 12/12
[2026-10-20 10:46:15] [PR_CREATOR] [T-014] [PASSED] PR #3
[2026-10-20 10:50:30] [PR_REVIEWER] [T-014] [PASSED] No blocking findings
[2026-10-20 10:58:00] [PM] [T-014] [COMPLETED] Squash-merged into main; CI green
```

## 4. Project commands

| Action | Command |
|---|---|
| Lint | `bash scripts/check-design-tokens.sh && ./gradlew ktlintCheck detekt` |
| Text guards | `bash scripts/check-text-encoding.sh && bash scripts/check-e2e-tags.sh` (UTF-8 without BOM/CRLF/mojibake; every flow has area tags) |
| Unit tests (all modules) | `./gradlew testLocalDebugUnitTest` (in `composeApp`/`shared` it runs `testAndroidHostTest`, which includes `commonTest`; ADR-19) |
| `prod` flavor unit tests | `./gradlew :androidApp:testProdDebugUnitTest` |
| Build and install on the emulator | `./gradlew :androidApp:installLocalDebug` |
| Delivery APK | `./gradlew :androidApp:assembleProdDebug` → `androidApp/build/outputs/apk/prod/debug/` |
| Local Supabase | `npx supabase start` / `npx supabase db reset` |
| DB tests (RLS) | `npx supabase test db` |
| Edge Function tests | `deno test --allow-all supabase/functions` |
| Serve functions locally | `npx supabase functions serve --env-file supabase/.env.local` |
| E2E for a task | `maestro test --test-output-dir tests/e2e/screenshots tests/e2e/T-###.yaml` (screenshots land in the gitignored `tests/e2e/screenshots/<timestamp>/`) |
| Scoped E2E regression (background QA per task; catalog flows only at checkpoints) | `maestro test --include-tags=<areas> --test-output-dir tests/e2e/screenshots tests/e2e/` — `<areas>` (comma-separated) comes from the plan's **E2E scope**: the area tags of the flows the task can affect. Every flow carries at least one area tag in its header: `shell` (navigation, top bar, FAB, sheets), `catalog` (debug component catalog); new features add their own (`diary`, `workout`, `progress`, …) |
| Full E2E regression (every 👤 checkpoint and epic closure) | `maestro test --test-output-dir tests/e2e/screenshots tests/e2e/` |
| View hierarchy | `maestro hierarchy` |
| PR | `gh pr create --title "<type>(T-###): <title>" --body-file <file>` |
| CI status | `gh pr checks --watch` |
| Merge | `gh pr merge --squash --delete-branch` |

> On Windows, run commands from Git Bash (`./gradlew`) or PowerShell (`.\gradlew.bat`).

## 5. Subagents (`.claude/agents/`)

| Agent | Model | Tools | Skill applied | Output |
|---|---|---|---|---|
| `epic-planner` | opus | Read, Grep, Glob, Write, Edit | planning-and-task-breakdown (principles; project sizing rules override it) | `tasks/todo.md` + `tasks/plan.md` for one epic: one task per story, 1–3 👤 checkpoints |
| `planner` | opus | Read, Grep, Glob, Write, Edit | planning-and-task-breakdown | `.workflow/current_task.md` (contracts, tests, testTags, implementation order) |
| `ux-reviewer` | sonnet | Read, Grep, Glob, Edit | frontend-ui-engineering (translated to Compose) | "UX notes" section in the plan + PASSED/REJECTED |
| `frontend-developer` | sonnet | Read, Grep, Glob, Write, Edit, Bash | test-driven-development, incremental-implementation, debugging-and-error-recovery | Kotlin code + tests; lint/tests/build green |
| `backend-developer` | sonnet | Read, Grep, Glob, Write, Edit, Bash | test-driven-development, incremental-implementation, debugging-and-error-recovery | Migrations, RLS, pgTAP, Edge Functions + `deno test` |
| `qa-engineer` | sonnet | Read, Grep, Glob, Write, Edit, Bash | debugging-and-error-recovery | `tests/e2e/T-###.yaml` + verdict; writes only in `tests/e2e/` |
| `pr-creator` | haiku | Read, Grep, Glob, Bash | git-workflow-and-versioning | One commit per task (squash merge), push, PR; never re-encodes files; stops on any secret |
| `pr-reviewer` | opus | Read, Grep, Glob, Bash | code-review-and-quality | Verdict as a review comment (`--comment`) |

Models (`model:` in each agent's frontmatter) are set by cost: `opus` where judgement drives everything downstream (the planner) or is the last safety net (the pr-reviewer); `sonnet` for well-specified implementation, QA and UX checks; `haiku` for the mechanical pr-creator. If an agent's output quality drops (e.g. more `CHANGES REQUIRED` from the reviewer), move it back to `opus`.

The skills (`addyosmani/agent-skills`, in `.agents/skills/` and linked from `.claude/skills/`) are generic with web examples (npm, React, Jest); agents apply their **principles** with this project's commands. Only `pr-creator` commits; no agent other than the PM edits `tasks/`.

## 6. Epic closure and delivery

1. All the epic's tasks are in `main` with green CI.
2. Every background QA run is finished, no `qa` issue is open, and the full E2E regression is green.
3. `prodDebug` APK (pointing to production Supabase) → the user installs it on their phone/tablet.
4. Feedback:
   - With changes → the PM creates corrective tasks within the epic.
   - Approved → tag `vX.Y.0`.
5. Retrospective with the user → `retrospectives/E#.md`; durable lessons become rules in `CLAUDE.md` or the agent files.
6. Next epic: broken down by `epic-planner` in a fresh session; the user confirms the task list and checkpoints before the first task.
