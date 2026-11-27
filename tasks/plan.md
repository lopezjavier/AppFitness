# Implementation Plan: E21 · Closure → `v1.x` (proposed `v1.11.0`)

> **Status: APPROVED by the user, 2026-11-27 (breakdown and 👤 Checkpoint E21); epic-planner draft revised the same day with the user's decisions (ADR-114).** Tasks T-330 (user request, first in plan order) and T-327…T-329, one 👤 checkpoint. E21 is the last epic (ADR-101; E20 was dropped, ADR-113); after it the app is in maintenance mode, where only bugfixes and the user's feedback create tasks. The E19 plan that lived here is in git history (E19 closed, `v1.10.0`; `tasks/todo.md` E19 section; `retrospectives/E19.md`).

## Overview
E21 first ships one user request, then puts the project in a safe end state in the order ADR-114 fixes:
0. **Food lists by comida (T-330, user request 2026-11-27).** "Habituales" lists the foods logged most often in the comida being added to over the last 90 days, most used first, with ties going to the most recent use and then the name. "Favoritos" gets a fixed order: uses in this comida, uses overall, most recent use, name. Today Habituales counts every comida over all time, and Favoritos is ordered by when the star was added, which is why both feel random.
1. **Keep-alive (US-124).** The existing `keepalive.yml` → `health` ping (ADR-12) must keep the Supabase Free project active through months with no development and no app use, with no secret in versioned files.
2. **Private repository (US-126).** A full-history secret scan; any rotation done by the user; the `production` environment secrets moved to repository secrets; the docs updated; then the switch to private. No ruleset afterwards (maintenance mode).
3. **Export (US-125, amended by ADR-114).** No app feature. A weekly job in the keep-alive's scheduled workflow, also started by hand, dumps every row the user owns to one JSON file and attaches it to the run as an artifact kept 90 days. It runs only on the private repository, because artifacts of a public repository can be downloaded by any signed-in GitHub user.
4. **Retro (US-127).** A whole-project retrospective that can be reused for a new app, written at the closure.

Sources: `planning/epics/E21-closure.md` (US-125 amended), ADR-114, ADR-101, ADR-17, ADR-12, ADR-13 (superseded), ADR-113; `03` (user-owned tables), `02` (workflows), `08` §2.1, §4, §5; `06` §6. Tasks: `tasks/todo.md` (E21 section).

Out of scope: new features and any app change other than T-330 (the user's request); the text-search order; a Play Store release; backups of other users' data; the carried Phase 3 follow-ups (Q-E21-5 decides at the closure whether they stay as the maintenance backlog).

## Decisions (user, 2026-11-27, ADR-114)
- **Q-E21-1:** the export is one JSON file with every row the user owns.
- **Q-E21-2, superseded:** there is no export in the app. A GitHub Actions job runs weekly on the keep-alive's schedule and on `workflow_dispatch`, and uploads the JSON as a run artifact kept 90 days. It reuses the existing Supabase CI secrets. No app UI, no migration, no upload anywhere but the private repository's artifact.
- **Order:** keep-alive → private repository → export → retro. The export ships only after the switch.
- **Q-E21-3:** the repository goes private on GitHub Free. The user moves the `production` environment secrets to repository secrets (the PM verifies names only). No ruleset after that: "never on `main`, squash-merged PRs only" stays a process rule.
- **Q-E21-4 (the epic-planner's call):** the keep-alive and the private switch are one task (T-327).
- **Q-E21-5:** decided at the closure.
- **T-330 (user request, 2026-11-27; PM default recorded as the request):**
  - Habituales is per comida, most used first; ties go to the most recent use, then the name.
  - Favoritos uses the key: uses in this comida, uses overall, most recent use, name.
  - The counting window is the epic-planner's decision: the last 90 days. It follows current habits, holds about 13 meals of each comida, and Recientes covers the last few days. A food unused for 90 days leaves Habituales but stays in Favoritos and search.

## Architecture decisions
- **The food lists rank on the server (T-330).** The Habituales RPC gains an optional comida id. A call with only `p_limit` keeps working, so the installed `v1.10.0` does not break when the migration deploys. Favoritos gets a ranked read with the same counts. Both are security invoker and owner-scoped, with a two-user pgTAP test. Without a comida (pick or view-only mode) both rank by uses overall. The text search's "favourites first" stays as it is.
- **One scheduled workflow (T-327, T-328).** `keepalive.yml` stays the only scheduled workflow, so there is a single thing to keep enabled against GitHub's 60-day rule. The 3-day keep-alive job and the weekly export job share it (a second cron entry, plus `workflow_dispatch`).
- **The keep-alive is proven, not assumed (T-327).** The current Supabase docs show whether an Edge Function call that queries the database counts as activity; if it does not, the ping also hits the REST API. The 60-day auto-disable is checked for a private repository. A new external service or account is a user decision.
- **The export reads the database directly with the existing secrets (T-328).** It uses `SUPABASE_PROJECT_ID` and `SUPABASE_DB_PASSWORD` (repository secrets after T-327) in a read-only transaction, with no migration and no RPC. Because this connection bypasses RLS, ownership is enforced in the script:
  - every query filters by the owner's id;
  - the table list is derived from the schema (every `public` table with `user_id`, plus the owner's `profiles` row);
  - a coverage check fails when a user-owned table is missing;
  - a validation step rejects any other `user_id`.

  Excluded: shared Open Food Facts cache rows and `off_data` (diary entries carry snapshots), the `auth` schema beyond the owner's id, tokens, keys and passwords.
- **No partial artifact (T-328).** The JSON goes to a temporary file and is validated before upload; `upload-artifact` (`retention-days: 90`) runs only on success. The job refuses to run unless `github.event.repository.private` is true. Logs mask the connection string, the password and the owner's id, and never print a row.
- **`deploy-supabase` without an environment (T-327).** The workflow drops `environment: production` and reads the same three names as repository secrets. The user sets them before the merge, so the deploy never breaks.
- **The retro is part of the closure (T-329).** `06` §6 step 4 already makes the retrospective a closure step that waits for the same approval. The file is `retrospectives/project.md`, plus a short `retrospectives/E21.md`.

## Ordering and sizing
Every task pays 6-7 agent hand-offs, so stories stay whole and S tasks are avoided.

| Order | Task | Size | Story | Flow | Agent runs |
|---|---|---|---|---|---|
| 0 | T-330: Habituales and Favoritos ordered by use in the comida (migration; APK for the checkpoint) | **M** | user request 2026-11-27 | backend + core (2.1 without ui-designer, no visual change): planner → ux-reviewer → backend-developer → kotlin-core-developer → pr-reviewer ∥ ux-reviewer → pr-creator | 6 |
| 0b | T-331: Secondary muscles in the exercise "Resumen" tab (APK for the checkpoint) | **S** | US-150, user request 2026-11-27 | UI (2.1 with ui-designer): ui-designer → planner → ux-reviewer → compose-ui-developer → pr-reviewer ∥ ux-reviewer → pr-creator | 6 + 👤 look |
| 1 | T-327: Keep-alive that holds through maintenance mode, then the private repository (👤 rotation, secrets, switch) | **M** | US-124, US-126 | infra + docs (2.2) | 4 + 👤 |
| 2 | T-328: Weekly data export as a private run artifact | **M** | US-125 | infra (2.2): workflow job + script + tests | 4 |
| 2b | 👤 Checkpoint E21: food lists by comida, private repository, weekly export, final regression (7 items) | checkpoint | US-125, US-126 | user | — |
| 3 | T-329: Project retro and E21 closure (after Checkpoint E21 and the closure approval) | closure step (no size class) | US-127, DoD | PM | PM only |

Why these sizes and merges:
- **T-330 is M and stands alone.** It is a migration, a pgTAP test, the repository and ViewModel changes and their tests (about 9 files), in a different domain from the ops tasks. It goes first because it is the only app change and the checkpoint tests it.
- **T-327 merges US-124 and US-126 (Q-E21-4).** In the ADR-114 order they are adjacent and both are repository operations. The 60-day rule depends on the visibility, so the keep-alive is checked once, after the switch. The keep-alive alone would be an S task. Mostly docs plus two workflow edits (about 10 files).
- **T-328 is M:** one workflow job, one script with its tests, one CI check step and docs. It is not merged into T-327, because it must run only after the switch, which T-327 ends with, and it is a separate deliverable.
- **T-329 holds US-127** (see "Architecture decisions").

## Phases

### Phase 0: Food lists by comida (user request)
- T-330. It deploys a migration (`deploy-supabase` green on `main`; this is the last deploy through the `production` environment before T-327 moves the secrets). The APK is delivered for Checkpoint E21.

### Phase 0b: Secondary muscles in Resumen (user request)
- T-331, asked for by the user after T-330 merged, before T-327. UI only, no migration. The APK is delivered for Checkpoint E21.

### Phase 1: The backend stays alive and the repository goes private (US-124, US-126)
- T-327, in this order (the planner may refine it):
  1. Full-history scan while the repository is still public; the user rotates any exposed secret.
  2. The user sets the three repository secrets.
  3. The PR (keep-alive checks, `deploy-supabase` on repository secrets, docs with the `CLAUDE.md` wording approved by the user) merged with CI.
  4. 👤 Switch.
  5. `gh repo view` shows `PRIVATE`; `keepalive` and `deploy-supabase` manual runs green (internal milestones, not 👤 items).
  6. The user removes the `production` environment and the `main` ruleset.

### Phase 2: The user's data, weekly (US-125)
- T-328. Internal milestone: a manual run on `main` green, with its 90-day artifact.

### 👤 Checkpoint E21 · Food lists by comida, private repository, weekly export and final regression (agreed with the user, 2026-11-27)
Right after T-328, the last implementation task. Why one: the food order (1 item plus leave and come back) and the private repository (1-2 items) each give fewer than the 5 a checkpoint needs. Nothing later builds on T-330, so its feedback can still change it. What to try:
1. Adding to "Desayuno", "Habituales" shows your usual breakfast foods first; "Cena" has its own order; "Favoritos" follows the same rule, with never-logged favourites last.
1b. An exercise's "Resumen" tab shows the primary and the secondary muscles, told apart (T-331).
2. A signed-out browser shows the repository as "not found".
3. Actions: start the export by hand (or open the latest weekly run); it is green, with an artifact kept 90 days.
4. Download the artifact: a recent meal, a recent workout and the latest weight are in the JSON.
5. Only your data: no test-account rows, no key, token or password; the log shows no row or secret.
6. Final regression on the phone: Diario, Entreno, Trainer and Perfil load and save as before.
7. Leave the app and come back (also with "Habituales" open): the same tab, screen, draft and order.

The keep-alive is not an item: it needs weeks without use, so it is listed as "Pending natural-trigger feedback (E21)" in `tasks/todo.md`, outside the gate (as E15 Q5). The approval releases T-329.

### Phase 3: Retro and closure (US-127)
- T-329 after Checkpoint E21 and the user's closure approval. It covers the project retro, the version, the tag, `09` (the E21 section with Q-E21-1…6), `01` and the maintenance-mode pointer.

## Dependencies
- T-330 needs the breakdown approved. It is first in plan order; T-327 has no technical dependency on it.
- T-331 has no technical dependency; the user asked for it next after T-330 (2026-11-27).
- T-327 needs the breakdown approved, plus the user's rotations and repository secrets before its merge.
- T-328 needs T-327 merged, the repository private and the secrets moved, and the user has set `EXPORT_USER_ID` (Q-E21-6).
- Checkpoint E21 needs T-330, T-331 and T-328 merged, CI green, T-330's `deploy-supabase` green, a manual export run green and the APK delivered.
- T-329 needs Checkpoint E21 approved and the user's epic-closure approval.
- Built pieces E21 relies on: `keepalive.yml` and the `health` function (ADR-12), `deploy-supabase`, the `gitleaks` job and the Python script tests in `security.yml`, local Supabase in `supabase-ci`, the per-epic retrospectives.

## Risks
- **The installed app breaks when T-330's migration deploys.** Mitigation: a call with only `p_limit` keeps working (a default parameter or a new function); the APK is delivered right after.
- **A short Habituales for a rarely used comida** (90-day window, per comida). Mitigation: the existing empty state, with its copy checked by ux-reviewer; Recientes, Favoritos and search still list every food.
- **Supabase does not count the Edge Function ping as activity.** If it did not, the project would pause after 7 idle days. Mitigation: T-327 checks the current docs and, if needed, makes the ping also go through the REST API.
- **GitHub disables the scheduled workflow after 60 days without commits.** That would stop both the keep-alive and the export. Mitigation: T-327 checks the rule for a private repository and adds a free mitigation or documents the 👤 re-enable step; a failed run reaches the user through GitHub's notification.
- **`deploy-supabase` breaks at the switch.** Environments are not available on a private Free repository. Mitigation: the workflow reads repository secrets, set by the user before the merge; a manual run after the switch.
- **No ruleset or secret scanning after the switch.** Mitigation: accepted by the user (ADR-114); `gitleaks` stays in CI; "never on `main`" stays a process rule.
- **A secret was exposed in history.** Mitigation: full-history gitleaks plus the GitHub alerts read before the switch; the user rotates; values never appear in the PR or the chat.
- **The export bypasses RLS** (it uses a direct database connection). Mitigation: an owner filter on every query, a schema-derived table list with a coverage check, a validation step that rejects any other `user_id`, and a two-user CI test.
- **Personal data in a log or a public artifact.** Mitigation: the private-only guard, masking, nothing echoed, and pr-reviewer reads the workflow.
- **Artifact storage on the Free private plan.** Mitigation: one JSON a week kept 90 days is far below the quota; `08` §5 records it.
- **The retro leaks personal data.** Mitigation: CI `gitleaks` plus a PM read for names, e-mails, project refs and device serials.

## Open questions
- **Q-E21-1, Q-E21-2, Q-E21-3: resolved (user, 2026-11-27, ADR-114).** See "Decisions".
- **Q-E21-4: resolved (the epic-planner's call).** The keep-alive and the private repository are one task, T-327.
- **Q-E21-6: resolved (user, 2026-11-27): (a), the repository secret `EXPORT_USER_ID`.** How does the export pick the owner's account? The direct connection sees every account, and production also holds the test account. Options:
  - (a) A repository secret `EXPORT_USER_ID` with the owner's account id, set by the user (the PM verifies the name only). The id is personal data, not a credential.
  - (b) No new secret: the oldest account in `auth.users`. This is fragile if accounts were ever recreated.

  Recommendation: (a).
- **Q-E21-5 · non-blocking, decided at the closure T-329.** The "Open follow-ups carried into Phase 3" in `tasks/todo.md`: keep them as the maintenance backlog, or close them as won't-do? Recommendation: keep them.
- **Delegated, not for the user:** the keep-alive mitigation details and the export script's language and connection method (T-327 and T-328 planners); the version number (PM, `06` §6).
