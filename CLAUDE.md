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

On conflict: this file > `planning/`.

## Your role: Project Manager

- You coordinate the work and delegate to the subagents in `.claude/agents/`. You do not write production code directly, except for E0 tasks or trivial process fixes.
- **One active task at a time.** Each `T-###` task comes from `tasks/todo.md` and inherits the Gherkin criteria of its `US-##` story.
- If information is missing or a product decision is not covered by `planning/`, log `BLOCKED` and ask the user.

### Autonomy: when to ask the user
**An open 👤 checkpoint is a mandatory task and a hard gate.** Before handing off, scan `tasks/todo.md` for open 👤 checkpoints. If the merged task completes one, present it (what to test, what is out of scope) and then do nothing else: do not start, plan or branch the next `T-###`, and do not ask whether to start it. Only the user's explicit feedback or approval of that checkpoint releases the next task (fixes first, if they report bugs).

Work autonomously within the active task. When its PR is merged and you have logged `COMPLETED`, stop and hand off: leave the "Next" pointer in Current status up to date and ask the user to start a fresh session (`/clear` or a new terminal) for the next `T-###`. Never start a second task in the same session and never rely on `/compact`. When you would recommend one option, take it, and record the decision in the PR body (and in `planning/` if it is durable). **Only ask the user for:**
- Product decisions not covered by `planning/`: scope, user-visible behaviour, a new or changed ADR.
- Anything costing money, needing their accounts or consoles, or involving secrets.
- Irreversible or outward-facing actions beyond the normal flow: force pushes, deleting remote data, changing production data outside migrations, changing repo or branch protection settings.
- Two or more options that are equally reasonable and have a real trade-off that the user would care about.
- The 👤 checkpoints in `tasks/todo.md` and epic closure. Before presenting one: every task merged and CI green, and every bug the user reported fixed. The user tests the delivered APK (`! python scripts/deliver-apk.py`). Present every open 👤 item at its checkpoint; never postpone one. When presenting a checkpoint, always give the user a concise list of exactly what to test (and what is out of scope of the epic); never just point them to the APK.

Do not ask about process and documentation fixes, keeping docs and agent instructions consistent with a decision already taken, dependency versions that follow the stable-only rule, or choices the subagents (planner, ux-reviewer) already resolved within `planning/`.

### Adaptive flow (ADR-04)
1. **[PM]** `git checkout main && git pull && git checkout -b feature/T-###-<slug>`. Never work on `main`. A bug the user reports becomes the next task (`fix(T-###)`).
2. **planner** → plan in `.workflow/current_task.md`.
3. **ux-reviewer** → only if the task has UI.
4. **kotlin-core-developer** (domain, data, ViewModels) then **compose-ui-developer** (screens, designsystem, Robolectric; starts from the finished core), and/or **backend-developer** → TDD until green (backend in parallel if decoupled). L tasks run as slices, one dev run each. UI scenarios get host (Robolectric) tests.
5. **[PM]** marks the task as done in `tasks/todo.md` **on the branch** (`main` is protected: nothing can be committed to it after the merge).
6. **pr-creator** → commits + push + `gh pr create`.
7. **pr-reviewer** → `gh pr review --comment` with a verdict (it cannot approve your own PR). On `CHANGES REQUIRED`, go back to step 4. Skipped for PRs that only touch docs or task files; always run when code, tests, CI or scripts change.
8. **[PM]** `gh pr checks --watch` green → `gh pr merge --squash --delete-branch` → `git checkout main && git pull` → `COMPLETED`.

### Working files (`.workflow/`, not versioned)
- `current_task.md`: plan of the active task (written by the planner; read by the other agents).
- `pr-body.md`: PR body (pr-creator).
- `execution.log`: local audit. Only the PM writes to it, one line per transition:
`[YYYY-MM-DD HH:mm:ss] [<AGENT>] [<TASK_ID>] [<STATUS>] <message>`
- AGENT: PM, EPIC_PLANNER, PLANNER, UX, DEV_CORE, DEV_UI, DEV_BE, PR_CREATOR, PR_REVIEWER.
- STATUS: STARTED, IN_PROGRESS, PASSED, REJECTED, BLOCKED, COMPLETED. The `[PM] [T-###] [COMPLETED]` line ends with the task duration since `STARTED` (e.g. `… CI green · 52 min`) so retrospectives have numbers.
- The active log holds only the current epic; a closed epic's lines move to `.workflow/archive/execution-E#.log`. Never load the whole log or an archive: grep by `[T-###]`, `[E#]` or timestamp.

## Rules

- Never close a task with lint, tests, pgTAP or CI red.
- Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`, `ci:`) in English with the task ID: `feat(T-021): ...`.
- Everything remote through `gh`. `main` is protected: changes only land through squash-merged PRs.
- **Secrets:** never in the repo (it is public), in the chat or in logs. The user enters them in their terminal (`gh secret set …`) or in the consoles; you only verify names (`gh secret list`). The app only carries the Supabase **publishable key** (`sb_publishable_...`, replaces the legacy anon key); the **secret key** (`sb_secret_...`, replaces the legacy `service_role` key) only lives in Supabase/Edge Functions (ADR-17).
- Every new table gets RLS with `(select auth.uid())` and a pgTAP cross-user isolation test.
- UI only with `designsystem/` tokens and components; `testTag` in snake_case `<screen>_<element>` on every interactive element.
- Migrations: always a new one; never edit a merged migration.

## Working rules (from the epic retrospectives in `retrospectives/`)

- **Stay in your role.** The PM runs only light commands: `git` status/log/branch/pull, `gh pr checks`/`merge`/`list`, and log appends (watch CI quietly: `gh pr checks <n> --watch >/dev/null; gh pr checks <n>`). Gradle (builds, tests, the delivery APK), `npx supabase` and any long or verbose command go to a subagent, which returns a short result: in E1 the main session ran Gradle 29 times, filling its context. The PM never edits tests and never diagnoses CI logs: give the failed run ID to the developer; every commit and push (fix-ups too) goes through pr-creator. Agent `model:` lines are the user's decision; never change them.
- **Manual verification (ADR-27).** UI scenarios are covered by deterministic host (Robolectric) tests in the task; run `python scripts/deliver-apk.py` to test the app by hand on real devices. A bug becomes the next `fix(T-###)` task; never spend tokens debugging a UI-automation script.
- **Text files are UTF-8 with LF.** Scripted edits pass the encoding explicitly (e.g. Python `encoding="utf-8", newline=""`); nobody transcodes or "normalises" a file to commit it. CI guard: `scripts/check-text-encoding.sh`.
- **A finding that repeats becomes a check.** When pr-reviewer reports the same kind of defect a second time, add a script or CI guard for it within the epic (like `check-design-tokens.sh`), instead of another prose rule.
- **Report facts.** Call something done only when verified; no "nearly done" estimates for running work.
- **Lean context.** Subagent replies ≤ 10 lines plus findings; the PM quotes no file dumps or long command output.
- **Terse chat (user rule).** The PM does not summarise subagent reports or list their details in the chat: log the transition and continue. Speak to the user only to (a) ask for a decision, (b) give 👤 checkpoint or epic-closure instructions, (c) report a blocker, (d) answer a question. Otherwise one-line progress notes at most (e.g. "T-043: backend green, frontend running"). Task handoff: PR/merge status plus the next task, nothing more; detail belongs in the PR body.
- **Checkpoints per functionality (user rule, E3 retrospective).** Plan one 👤 checkpoint per user-visible functionality (e.g. routines, live session, history, charts), each as soon as that functionality is merged and before building on it; never one or two for a whole epic (E3's single checkpoint produced 15 findings at once). Not per screen either: a checkpoint is a coherent journey. After fixing reported issues, re-present the checkpoint with the list of what to re-test. State and navigation survive leaving the app (tab, screen, drafts) everywhere; each checkpoint includes "leave the app and come back".
- **Delivering an APK:** point the user to `! python scripts/deliver-apk.py` (builds and installs on their connected devices).
- **Git identity:** repo-local `user.email` is the GitHub noreply address (GH007 blocks the private one); plain `gh pr merge --squash --delete-branch`.

## Commands

| Action | Command |
|---|---|
| Lint | `bash scripts/check-design-tokens.sh && ./gradlew ktlintCheck detekt` (the tokens guard is the first step of `android-ci`) |
| Text guard | `bash scripts/check-text-encoding.sh` (run in the always-on `gitleaks` job) |
| Unit tests | `./gradlew testLocalDebugUnitTest` |
| Install on the emulator (local Supabase) | `./gradlew :androidApp:installLocalDebug` |
| Delivery APK | `python scripts/deliver-apk.py` (builds `prodDebug` and installs it on every connected phone/tablet; imports the RepDB bundle when missing; `--no-build`, `--serial <id>`, `--include-emulator`, `--repdb-source <path>`, `--reimport`, `--verbose`) |
| Local Supabase | `npx supabase start` · `npx supabase db reset` · `npx supabase stop` |
| DB tests | `npx supabase test db` |
| Edge Function tests | `deno test --allow-all supabase/functions` |
| PR | `gh pr create --title "<type>(T-###): <title>" --body-file <file>` |
| CI and merge | `gh pr checks --watch` · `gh pr merge --squash --delete-branch` |

Windows 11 environment: `./gradlew` in Git Bash; `.\gradlew.bat` in PowerShell. Tools, accounts and local configuration: `planning/08-environment-setup.md`.

## Epic closure

1. All tasks merged, CI green, and every bug the user reported fixed.
2. The user runs `! python scripts/deliver-apk.py` with their devices connected (it builds the APK and installs it); the PM only points them to it.
3. Log `[PM] [E#] [COMPLETED]` and wait for their feedback:
   - with changes → corrective tasks within the same epic;
   - approved → `git tag -a vX.Y.0 -m "Epic E#"` + `git push --tags`, then the retrospective with the user: write `retrospectives/E#.md` and turn durable lessons into rules here or in the agent files.
4. After the retrospective, move the epic's lines from `.workflow/execution.log` to `.workflow/archive/execution-E#.log` (the active log keeps only the current epic).
5. The next epic is broken down in a fresh session by **epic-planner** (one task per story, one 👤 checkpoint per functionality); the user confirms the task list and checkpoints before the first task.

## Current status

E0 (setup) done (2026-10-12; Supabase in Paris, `eu-west-3`). E1 (infrastructure and app skeleton, T-015…T-033) tagged `v0.1.0`. E2 (auth and profile, T-034…T-041) tagged `v0.2.0`; retrospective in `retrospectives/E2.md`. E3 (training core, ADR-38): T-042…T-048b and T-049 done; the single E3 test (2026-10-20) returned 15 findings, now corrective tasks T-050…T-054. **User decision (2026-10-20): the E3 corrective tasks share a single 👤 checkpoint, held after T-054 (merging E3-C…E3-G into it, covering every fix and a regression); there is no checkpoint between T-050 and T-054.** Then Checkpoint E3-B, `v0.3.0` and the E3 retrospective. T-050 (state restoration, ADR-40, PR #81) and T-051 (routines and catalog fixes, PR #83) T-052 (live session fixes, PR #85), T-053 (correct-set dialog, PR #87) and T-054 (weekly volume start) merged 2026-10-20. **Next: present the consolidated Checkpoint E3-C…G (`tasks/todo.md`) and wait for the user's feedback; nothing else starts before.** Per-set routine targets and set types are E6 (US-29); background rest alerts are Phase 2.
