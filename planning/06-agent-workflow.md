# Claude Code workflow and subagents

Phase 2 archive (full text as it was when Phase 2 closed): [`phase2/06-agent-workflow.md`](phase2/06-agent-workflow.md).

`CLAUDE.md` holds the PM's always-on rules; this document holds the detail; the subagents are defined in `.claude/agents/*.md`. If they disagree, `CLAUDE.md` wins. History and the original wording: [`phase1/06-agent-workflow.md`](phase1/06-agent-workflow.md).

## 1. Principles
1. The main session is the **Project Manager (PM)**: coordinates, logs, decides; technical work goes to subagents.
2. **One active task at a time**, each on its own `feature/T-###-<slug>` branch created at the start from an up-to-date `main` (never work on `main`).
3. A task is not closed with unit tests, pgTAP or CI red. UI scenarios are covered by host (Robolectric) tests; the user verifies by hand at checkpoints with the delivered APK (ADR-27).
4. Conventional Commits in English with the task ID (`feat(T-021): ...`).
5. Remote operations only through `gh`.
6. Merging is gated by required CI checks (ruleset); pr-reviewer returns its verdict to the PM, before the PR exists.
7. Only the PM writes `.workflow/execution.log`; subagents return a summary.
8. Missing information or a product decision not in `planning/`: log `BLOCKED` and ask the user.

## 2. Adaptive flow (ADR-04)
### 2.1 Task with UI
```
[PM] git checkout main && git pull && git checkout -b feature/T-###-slug
  -> planner            plan in .workflow/current_task.md
  -> ux-reviewer (plan)  pattern map, rules (05 §8-§10), sweep of existing screens, Spanish copy, testTags
        REJECTED -> planner fixes the plan
  -> kotlin-core-developer, then compose-ui-developer (L tasks: one run per slice), and/or backend-developer
[PM] marks the task in tasks/todo.md on the branch
  -> pr-reviewer + ux-reviewer (diff of the working tree vs main; ux-reviewer only if files under feature/, designsystem/ or composeResources/ changed), in parallel
        a finding -> developer, one combined fix round
  -> pr-creator         verify-local gate (Kotlin changes only); if green: commit(s), push, gh pr create
        red gate -> pr-creator stops and returns the failure to the PM; no PR is created
[PM] on a red gate: resumes the developer who owns the failing area and reads the developer's report.
     Fix changed production behaviour -> PM re-runs the affected reviewer(s) before pr-creator.
     Fix touched only tests, lint or formatting -> PM sends pr-creator again, no re-review.
[PM] green CI -> gh pr merge --squash --delete-branch -> COMPLETED; hand off, next task in a fresh session (/clear)
```
pr-reviewer is skipped for PRs that only touch docs or task files; the ux-reviewer diff pass is skipped for minor corrective tasks (§2.3).
### 2.2 Infrastructure / backend task without UI
PM start -> planner -> backend-developer (pgTAP / deno test / local CI) -> pr-reviewer -> pr-creator -> green CI -> merge. A user-reported bug becomes the next `fix(T-###)` task. L tasks run as slices. The implementing developer stays resumable (SendMessage) from the first commit until the PR is merged, and is used again for CI failures, review findings and re-tests; it is stopped after the merge, and before asking the user for `/clear` the PM runs `ListAgents`, stops every agent of the finished task that is still running, and says so in the handoff message. pr-reviewer and ux-reviewer are always fresh agents.
### 2.3 Minor corrective task (< ~50 lines)
Planner optional (short plan in the PR); the rest follows the applicable flow.
### 2.4 Why the task is marked on the branch
`main` is protected: the checkbox in `tasks/todo.md` is updated on the feature branch and takes effect with the merge. The handoff pointer is a separate docs-only PR (see `CLAUDE.md`). `.workflow/` is local and unversioned.
### 2.5 Product definition
When the user asks, the `product-owner` skill runs in the main session: brainstorm, create or update a phase or epic. An approved epic then goes to `epic-planner` in a fresh session.

## 3. Logging (`.workflow/execution.log`, local)
`[YYYY-MM-DD HH:mm:ss] [<AGENT>] [<TASK_ID>] [<STATUS>] <message>`
- AGENT: PM, EPIC_PLANNER, PLANNER, UX, DEV_CORE, DEV_UI, DEV_BE, PR_CREATOR, PR_REVIEWER. STATUS: STARTED, IN_PROGRESS, PASSED, REJECTED, BLOCKED, COMPLETED.
- `[PM] [T-###] [COMPLETED]` ends with the duration since STARTED (e.g. `CI green · 52 min`).
- `.workflow/` holds `current_task.md` (planner), `pr-body.md` (pr-creator), `execution.log` (PM only). The log holds only the current epic; at closure its lines move to `.workflow/archive/execution-E#.log`. Read with `grep` (`[T-###]`, `[E#]`, timestamp), never in full.

## 4. Project commands
| Action | Command |
|---|---|
| Lint | `bash scripts/check-design-tokens.sh && bash scripts/check-ux-conventions.sh && ./gradlew ktlintCheck detekt` (both guards are the first steps of `android-ci`) |
| UX guard fixtures | `bash scripts/tests/test-check-ux-conventions.sh` |
| Text guard | `bash scripts/check-text-encoding.sh` (UTF-8, no BOM/CRLF/mojibake; runs in the `gitleaks` job) |
| Unit tests (all modules) | `./gradlew testLocalDebugUnitTest` (runs `testAndroidHostTest` incl. `commonTest` in the KMP modules; ADR-19) |
| `prod` flavor unit tests | `./gradlew :androidApp:testProdDebugUnitTest` |
| Install on emulator | `./gradlew :androidApp:installLocalDebug` |
| Delivery APK | `python scripts/deliver-apk.py` (builds `prodDebug`, installs on connected physical devices, imports the RepDB bundle when missing; flags `--no-build`, `--serial`, `--include-emulator`, `--repdb-source`, `--reimport`, `--verbose`) |
| Local Supabase | `npx supabase start` / `db reset` / `stop` |
| DB tests (RLS) | `npx supabase test db` |
| Edge Function tests | `deno test --allow-all supabase/functions` |
| Serve functions | `npx supabase functions serve --env-file supabase/.env.local` |
| PR / CI / merge | `gh pr create --title "<type>(T-###): <title>" --body-file <file>` · `gh pr checks --watch` · `gh pr merge --squash --delete-branch` |

**UX guards (`scripts/check-ux-conventions.sh`, ADR-69).** Production Kotlin in `composeApp/src/*Main` must reuse the design-system templates: e.g. `ModalBottomSheet(` only in `designsystem/AppBottomSheet.kt` (G1), features use sheet archetypes (G2), `SnackbarHost(` only in `app/` (G3), `OfflineBanner(` only in the shell (G4), `AppConfirmDialog(` only for the R-08 allow-list (G5), no other swipe code (G7), no generic action text outside `action_*` keys (G9), R-38 sheet rules (G11: outside-tap dismissal only through `AppBottomSheet(dismissOnClickOutside)` set by the picker, info and action archetypes, ADR-80), every tab root has the notification bell (G13), `AppEditSheet(` only in the allow-listed multi-field files (G14, R-12). Fixtures and allowed warnings: `scripts/tests/`, `scripts/ux-conventions-warnings.txt`.

**Screenshot tests (Roborazzi).** `composeApp/src/androidHostTest/.../designsystem/*ScreenshotTest.kt` capture templates on a 360x640 dp device and compare them with golden PNGs in `composeApp/src/androidHostTest/snapshots/`. A normal `testAndroidHostTest` VERIFIES; `-PrecordScreenshots` rewrites the goldens (see `composeApp/build.gradle.kts`).

**Screen gallery (T-279, US-131).** `python scripts/gallery.py` renders every Diario, Perfil, Inicio, Centro de avisos and Compartido screen state, sheet and dialog on the host (Robolectric + Roborazzi, fake UI states, no device or backend) into `composeApp/build/gallery/<area>/<screen>/<state>.png` plus a sidecar JSON, and writes `composeApp/build/gallery/index.html` (grouped by area, with exemptions and the "not yet in the gallery" lists). The images are never compared and never touch `src/androidHostTest/snapshots/`; CI uploads them as the `screen-gallery` artifact. The capture tests (`gallery/*Gallery.kt`) skip unless `-Pgallery` sets `gallery.dir`. To add a screen or overlay: add a `GalleryEntry` (state fixture, `covers` = route simple names or the overlay composable names) to the area file and remove the name from `GalleryManifest.pending`. Two checks in `GalleryCoverageTest` (in the normal suite) fail naming any route (read from `AppRoute.serializer()` and `RootRoute.kt`) or overlay composable (source scan of `feature/**` and `app/**` for calls to an overlay primitive, derived from `designsystem/`) that has no entry, exemption or pending name. Entries owned by T-280 (Entreno and Trainer) stay in `GalleryManifest.pending`. The photo `Preview` step (`FoodPhotoStep.Preview`) is not rendered: it needs a platform image handle that the host harness cannot build without production changes (`PhotoPreview` is not an overlay composable, so no `covers` name is involved).

> On Windows 11 run from Git Bash (`./gradlew`) or PowerShell (`.\gradlew.bat`). Tools and accounts: `08-environment-setup.md`.

## 5. Subagents (`.claude/agents/`)
| Agent | Output |
|---|---|
| `epic-planner` | `tasks/todo.md` + `tasks/plan.md` for one epic: one task per story, one 👤 checkpoint per functionality |
| `planner` | `.workflow/current_task.md` (contracts, tests, testTags, implementation order) |
| `ux-reviewer` | "UX notes" in the plan + PASSED/REJECTED; diff review in parallel with pr-reviewer, before pr-creator |
| `kotlin-core-developer` | Domain, data, DI, ViewModels + unit tests |
| `compose-ui-developer` | Screens, designsystem, navigation, Robolectric tests; starts from the core |
| `backend-developer` | Migrations, RLS, pgTAP, Edge Functions + `deno test` |
| `pr-creator` | Runs verify-local.sh as the gate when Kotlin changed; commits, push, PR; never re-encodes files; stops on any secret |
| `pr-reviewer` | Verdict returned to the PM (diff of the working tree vs main) |

Models are the `model:` line of each agent file and are the user's decision. Each agent is self-contained. Only `pr-creator` commits; no agent other than the PM edits `tasks/`.

## 6. Epic closure and delivery
1. All the epic's tasks are in `main` with green CI; the PM logs `[PM] [E#] [COMPLETED]` and waits for feedback.
2. The user verifies the delivered APK (`! python scripts/deliver-apk.py`, devices connected) and every reported bug is fixed.
3. Changes requested -> corrective tasks in the epic. Approved -> bump `versionName`/`versionCode` in `androidApp/build.gradle.kts` (PR). The PM owns the version without asking the user: each epic raises the minor and each phase raises the major. Then `git tag -a vX.Y.0 -m "Epic E#"` + `git push --tags`.
4. Retrospective with the user; durable lessons become rules in `CLAUDE.md`, `.claude/` or the agent files.
5. Archive the epic's log lines; the next epic is broken down by `epic-planner` in a fresh session, and the user agrees the task list and checkpoints before the breakdown PR merges and before the first task.
