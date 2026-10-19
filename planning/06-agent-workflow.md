# Claude Code workflow and subagents

Supersedes the original orchestrator and workflow documents (kept outside the repository). The operational summary lives in `CLAUDE.md`; the subagents are defined in `.claude/agents/*.md`. If this document and `CLAUDE.md` disagree, `CLAUDE.md` wins.

## 1. Principles

1. Claude Code (main session) acts as the **Project Manager (PM)**: coordinates, logs and decides; delegates technical work to subagents.
2. **One active task at a time.** Every task starts from an up-to-date `main` and lives on its own `feature/T-###-<slug>` branch, created **at the start** (never work on `main`).
3. A task is not closed if unit tests, pgTAP or CI fail; UI scenarios are covered by host (Robolectric) tests and the app is verified manually at the checkpoints with the delivered APK.
4. Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`, `ci:`) in English, scoped with the task ID.
5. Remote operations only through `gh`.
6. Merging into `main` is gated by required CI checks (ruleset). The PR Reviewer posts a verdict as a comment; it cannot "approve" a PR from the same user.
7. Only the PM writes `.workflow/execution.log` (avoids conflicts); subagents return a summary and the PM logs it.
8. If information is missing or a product decision is not covered by `planning/`, the PM logs `BLOCKED` and asks the user.

## 2. Adaptive flow (ADR-04)

### 2.1 Task with UI (full flow)
```
[PM] start: git checkout main && git pull && git checkout -b feature/T-###-slug
  → planner            plan in .workflow/current_task.md
  → ux-reviewer        validates the plan vs 05-design-system.md; defines testTags
  → kotlin-core-developer, then compose-ui-developer (L tasks: one run per slice), and/or backend-developer (in parallel if decoupled)
                       TDD until green; UI scenarios covered by host (Robolectric) tests
[PM] marks the task done in tasks/todo.md on the branch
  → pr-creator         commit(s), push, gh pr create
  → pr-reviewer        gh pr diff → verdict (gh pr review --comment)
        └─ CHANGES REQUIRED → developer
[PM] waits for green CI → gh pr merge --squash --delete-branch → COMPLETED
  └─ PM stops and hands off; the next task starts in a fresh session (`/clear`)
```

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
- `AGENT`: PM, EPIC_PLANNER, PLANNER, UX, DEV_CORE, DEV_UI, DEV_BE, PR_CREATOR, PR_REVIEWER.
- `STATUS`: STARTED, IN_PROGRESS, PASSED, REJECTED, BLOCKED, COMPLETED.
- The active log holds only the current epic; a closed epic's lines move to `.workflow/archive/execution-E#.log`. Read it with `grep` (`[T-###]`, `[E#]`, timestamp), never in full.
- Example:
```
[2026-10-20 10:02:10] [PM] [T-014] [STARTED] Branch feature/T-014-kmp-project created from main
[2026-10-20 10:05:25] [PLANNER] [T-014] [PASSED] Plan in .workflow/current_task.md
[2026-10-20 10:40:00] [DEV_CORE] [T-014] [PASSED] testLocalDebugUnitTest 12/12
[2026-10-20 10:46:15] [PR_CREATOR] [T-014] [PASSED] PR #3
[2026-10-20 10:50:30] [PR_REVIEWER] [T-014] [PASSED] No blocking findings
[2026-10-20 10:58:00] [PM] [T-014] [COMPLETED] Squash-merged into main; CI green
```

## 4. Project commands

| Action | Command |
|---|---|
| Lint | `bash scripts/check-design-tokens.sh && ./gradlew ktlintCheck detekt` |
| Text guard | `bash scripts/check-text-encoding.sh` (UTF-8 without BOM/CRLF/mojibake) |
| Unit tests (all modules) | `./gradlew testLocalDebugUnitTest` (in `composeApp`/`shared` it runs `testAndroidHostTest`, which includes `commonTest`; ADR-19) |
| `prod` flavor unit tests | `./gradlew :androidApp:testProdDebugUnitTest` |
| Build and install on the emulator | `./gradlew :androidApp:installLocalDebug` |
| Delivery APK | `python scripts/deliver-apk.py`: builds `prodDebug` (`androidApp/build/outputs/apk/prod/debug/`) and installs it on every connected physical device (`--no-build`, `--serial <id>`, `--include-emulator`) |
| Local Supabase | `npx supabase start` / `npx supabase db reset` |
| DB tests (RLS) | `npx supabase test db` |
| Edge Function tests | `deno test --allow-all supabase/functions` |
| Serve functions locally | `npx supabase functions serve --env-file supabase/.env.local` |
| PR | `gh pr create --title "<type>(T-###): <title>" --body-file <file>` |
| CI status | `gh pr checks --watch` |
| Merge | `gh pr merge --squash --delete-branch` |

> On Windows, run commands from Git Bash (`./gradlew`) or PowerShell (`.\gradlew.bat`).

## 5. Subagents (`.claude/agents/`)

| Agent | Model | Tools | Output |
|---|---|---|---|
| `epic-planner` | opus | Read, Grep, Glob, Write, Edit | `tasks/todo.md` + `tasks/plan.md` for one epic: one task per story, one 👤 checkpoint per functionality |
| `planner` | sonnet | Read, Grep, Glob, Write, Edit | `.workflow/current_task.md` (contracts, tests, testTags, implementation order) |
| `ux-reviewer` | sonnet | Read, Grep, Glob, Edit | "UX notes" section in the plan + PASSED/REJECTED |
| `kotlin-core-developer` | sonnet | Read, Grep, Glob, Write, Edit, Bash | Domain, data, DI, ViewModels + unit tests; lint/tests/build green |
| `compose-ui-developer` | sonnet | Read, Grep, Glob, Write, Edit, Bash | Screens, designsystem, navigation wiring, Robolectric tests; starts from the core |
| `backend-developer` | sonnet | Read, Grep, Glob, Write, Edit, Bash | Migrations, RLS, pgTAP, Edge Functions + `deno test` |
| `pr-creator` | haiku | Read, Grep, Glob, Bash | One commit per task (squash merge), push, PR; never re-encodes files; stops on any secret |
| `pr-reviewer` | sonnet | Read, Grep, Glob, Bash | Verdict as a review comment (`--comment`) |

Models (`model:` in each agent's frontmatter) are set by cost: `opus` where judgement drives everything downstream (the epic-planner); `sonnet` for planning a single task, well-specified implementation, review and UX checks; `haiku` for the mechanical pr-creator. If an agent's output quality drops (e.g. more `CHANGES REQUIRED` from the reviewer, or vaguer plans), suggest the user to change the model back to `opus`.

Each agent carries its own rules and is self-contained. Only `pr-creator` commits; no agent other than the PM edits `tasks/`.

## 6. Epic closure and delivery

1. All the epic's tasks are in `main` with green CI.
2. The user has verified the delivered APK and every bug they reported is fixed.
3. `prodDebug` APK (pointing to production Supabase) → the user installs it on their phone/tablet.
4. Feedback:
   - With changes → the PM creates corrective tasks within the epic.
   - Approved → tag `vX.Y.0`.
5. Retrospective with the user → `retrospectives/E#.md`; durable lessons become rules in `CLAUDE.md` or the agent files.
6. Archive the epic's lines from `.workflow/execution.log` to `.workflow/archive/execution-E#.log`.
7. Next epic: broken down by `epic-planner` in a fresh session; the user confirms the task list and checkpoints before the first task.
