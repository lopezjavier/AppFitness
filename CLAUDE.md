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
- **One active task at a time** (the previous task's background QA may run alongside). Each `T-###` task comes from `tasks/todo.md` and inherits the Gherkin criteria of its `US-##` story.
- If information is missing or a product decision is not covered by `planning/`, log `BLOCKED` and ask the user.

### Autonomy: when to ask the user
Work autonomously: after a merge, start the next task without asking and chain tasks up to the next 👤 checkpoint. The user tracks the context size and stops you when they want to `/compact`. When you would recommend one option, take it, and record the decision in the PR body (and in `planning/` if it is durable). **Only ask the user for:**
- Product decisions not covered by `planning/`: scope, user-visible behaviour, a new or changed ADR.
- Anything costing money, needing their accounts or consoles, or involving secrets.
- Irreversible or outward-facing actions beyond the normal flow: force pushes, deleting remote data, changing production data outside migrations, changing repo or branch protection settings.
- Two or more options that are equally reasonable and have a real trade-off that the user would care about.
- The 👤 checkpoints in `tasks/todo.md` and epic closure. Before presenting one: every background QA run finished, every open `qa` issue fixed (or deferred by the user), and the full Maestro regression green (all tags). Present every open 👤 item at its checkpoint; never postpone one.

Do not ask about process and documentation fixes, keeping docs and agent instructions consistent with a decision already taken, dependency versions that follow the stable-only rule, or choices the subagents (planner, ux-reviewer) already resolved within `planning/`.

### Adaptive flow (ADR-04, QA in the background per ADR-23)
1. **[PM]** `git checkout main && git pull && git checkout -b feature/T-###-<slug>`. Never work on `main`. First check `gh issue list --label qa`: a confirmed app bug becomes the next task (`fix(T-###)`); a broken flow goes back to qa-engineer.
2. **planner** → plan in `.workflow/current_task.md`.
3. **ux-reviewer** → only if the task has UI.
4. **frontend-developer** and/or **backend-developer** → TDD until green (in parallel if decoupled). UI scenarios get host (Robolectric) tests.
5. **[PM]** marks the task as done in `tasks/todo.md` **on the branch** (`main` is protected: nothing can be committed to it after the merge).
6. **pr-creator** → commits + push + `gh pr create`.
7. **pr-reviewer** → `gh pr review --comment` with a verdict (it cannot approve your own PR). On `CHANGES REQUIRED`, go back to step 4. Skipped for PRs that only touch docs or task files; always run when code, tests, CI or scripts change.
8. **[PM]** `gh pr checks --watch` green → `gh pr merge --squash --delete-branch` → `git checkout main && git pull` → `COMPLETED`.
9. **qa-engineer** (UI tasks only), **in the background** while the next task starts: in the worktree `../AppFitness-qa` on the merged `main`, it writes and runs `tests/e2e/T-###.yaml` + the scoped regression. App bugs → a GitHub issue labelled `qa` (never a change to the open PR). The flow lands through pr-creator in a `test(T-###)` PR from that worktree (text-only: CI skips the build).

### Working files (`.workflow/`, not versioned)
- `current_task.md`: plan of the active task (written by the planner; read by the other agents).
- `pr-body.md`: PR body (pr-creator).
- `execution.log`: local audit. Only the PM writes to it, one line per transition:
`[YYYY-MM-DD HH:mm:ss] [<AGENT>] [<TASK_ID>] [<STATUS>] <message>`
- AGENT: PM, EPIC_PLANNER, PLANNER, UX, DEV_FE, DEV_BE, QA, PR_CREATOR, PR_REVIEWER.
- STATUS: STARTED, IN_PROGRESS, PASSED, REJECTED, BLOCKED, COMPLETED. The `[PM] [T-###] [COMPLETED]` line ends with the task duration since `STARTED` (e.g. `… CI green · 52 min`) so retrospectives have numbers.

## Rules

- Never close a task with lint, tests, pgTAP, Maestro or CI red.
- Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`, `ci:`) in English with the task ID: `feat(T-021): ...`.
- Everything remote through `gh`. `main` is protected: changes only land through squash-merged PRs.
- **Secrets:** never in the repo (it is public), in the chat or in logs. The user enters them in their terminal (`gh secret set …`) or in the consoles; you only verify names (`gh secret list`). The app only carries the Supabase **publishable key** (`sb_publishable_...`, replaces the legacy anon key); the **secret key** (`sb_secret_...`, replaces the legacy `service_role` key) only lives in Supabase/Edge Functions (ADR-17).
- Every new table gets RLS with `(select auth.uid())` and a pgTAP cross-user isolation test.
- UI only with `designsystem/` tokens and components; `testTag` in snake_case `<screen>_<element>` on every interactive element.
- Migrations: always a new one; never edit a merged migration.

## Working rules (from the epic retrospectives in `retrospectives/`)

- **Stay in your role.** The PM runs only light commands: `git` status/log/branch/pull, `gh pr checks`/`merge`/`list`, and log appends (watch CI quietly: `gh pr checks <n> --watch >/dev/null; gh pr checks <n>`). Gradle (builds, tests, the delivery APK), Maestro, `npx supabase` and any long or verbose command go to a subagent, which returns a short result: in E1 the main session ran Gradle 29 times and Maestro 16 times, filling its context. The PM never edits tests and never diagnoses CI logs: give the failed run ID to the developer; e2e flows go to qa-engineer; every commit and push (fix-ups too) goes through pr-creator. Agent `model:` lines are the user's decision; never change them.
- **Bounded QA.** Every QA brief names the exact flows, allows at most 2 attempts per flow and then a report (no open-ended stabilisation), and never two Maestro runs on the device at once. Per task: the task's flow + its area tags; catalog flows only at checkpoints (catalog UI is covered by host tests per task).
- **Text files are UTF-8 with LF.** Scripted edits pass the encoding explicitly (e.g. Python `encoding="utf-8", newline=""`); nobody transcodes or "normalises" a file to commit it. CI guards: `scripts/check-text-encoding.sh`, `scripts/check-e2e-tags.sh`.
- **A finding that repeats becomes a check.** When pr-reviewer reports the same kind of defect a second time, add a script or CI guard for it within the epic (like `check-design-tokens.sh`), instead of another prose rule.
- **Report facts.** Call something done only when verified; no "nearly done" estimates for running work.
- **Lean context.** Subagent replies ≤ 10 lines plus findings; the PM quotes no file dumps or long command output, and user updates are a few lines per transition.
- **Delivering an APK:** point the user to `! python scripts/deliver-apk.py` (builds and installs on their connected devices).
- **Git identity:** repo-local `user.email` is the GitHub noreply address (GH007 blocks the private one); plain `gh pr merge --squash --delete-branch`.

## Commands

| Action | Command |
|---|---|
| Lint | `bash scripts/check-design-tokens.sh && ./gradlew ktlintCheck detekt` (the tokens guard is the first step of `android-ci`) |
| Text guards | `bash scripts/check-text-encoding.sh && bash scripts/check-e2e-tags.sh` (run in the always-on `gitleaks` job) |
| Unit tests | `./gradlew testLocalDebugUnitTest` |
| Install on the emulator (local Supabase) | `./gradlew :androidApp:installLocalDebug` |
| Delivery APK | `python scripts/deliver-apk.py` (builds `prodDebug` and installs it on every connected phone/tablet; `--no-build`, `--serial <id>`, `--include-emulator`) |
| Local Supabase | `npx supabase start` · `npx supabase db reset` · `npx supabase stop` |
| DB tests | `npx supabase test db` |
| Edge Function tests | `deno test --allow-all supabase/functions` |
| E2E | `maestro test -e SUPABASE_LOCAL_PUBLISHABLE_KEY=<key> --test-output-dir tests/e2e/screenshots tests/e2e/T-###.yaml` · scoped regression (per task): same with `--include-tags=<areas> tests/e2e/`, areas from the plan's E2E scope · full regression (every 👤 checkpoint and epic closure): same with `tests/e2e/` (screenshots → gitignored `tests/e2e/screenshots/`). `-e SUPABASE_LOCAL_PUBLISHABLE_KEY=<key>` is required by flows that reset the test user (T-035+); the key is `PUBLISHABLE_KEY` from `npx supabase status`, never committed |
| PR | `gh pr create --title "<type>(T-###): <title>" --body-file <file>` |
| CI and merge | `gh pr checks --watch` · `gh pr merge --squash --delete-branch` |

Windows 11 environment: `./gradlew` in Git Bash; `.\gradlew.bat` in PowerShell. Tools, accounts and local configuration: `planning/08-environment-setup.md`.

## Epic closure

1. All tasks merged, background QA finished, no open `qa` issues, and the full Maestro regression green.
2. The user runs `! python scripts/deliver-apk.py` with their devices connected (it builds the APK and installs it); the PM only points them to it.
3. Log `[PM] [E#] [COMPLETED]` and wait for their feedback:
   - with changes → corrective tasks within the same epic;
   - approved → `git tag -a vX.Y.0 -m "Epic E#"` + `git push --tags`, then the retrospective with the user: write `retrospectives/E#.md` and turn durable lessons into rules here or in the agent files.
4. The next epic is broken down in a fresh session by **epic-planner** (one task per story, few 👤 checkpoints); the user confirms the task list and checkpoints before the first task.

## Current status

E0 (setup) done (2026-10-12; Supabase in Paris, `eu-west-3`). E1 (infrastructure and app skeleton, T-015…T-033) done and tagged `v0.1.0` (2026-10-13): see `tasks/todo.md`. E2 (auth and profile) broken down into T-034…T-038 (2026-10-14; decisions in ADR-24): see `tasks/todo.md`. Next: T-034.

## Skills

`.agents/skills/` (linked from `.claude/skills/`) contains generic skills from `addyosmani/agent-skills`. Subagents apply their **principles**; their examples (npm, React, Jest) are replaced with this project's commands. Some reference files that are not installed (`references/*.md`, `security-and-hardening`): ignore them.
