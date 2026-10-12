# Implementation Plan: E0 · Project setup

## Overview
Get the repository, GitHub, Supabase and Google OAuth ready so Claude Code can implement Phase 1 with the `planning/06-agent-workflow.md` flow. When E0 is done there will be: a local repo linked to `https://github.com/lopezjavier/AppFitness` (public), `main` protected by required checks, Claude Code configuration (`CLAUDE.md`, subagents, log), a production Supabase project with a GitHub Actions pipeline that automatically deploys `supabase/**` when merged into `main`, and Google authentication configured in Google Cloud and Supabase (ready for US-05).

Source of truth: `planning/` (especially `epics/E0-setup.md`, `02-architecture.md`, `08-environment-setup.md`). Task list: `tasks/todo.md`.

## Architecture Decisions
- **Original documents outside the repo**: their content is absorbed into `planning/` and `CLAUDE.md`; they are not versioned.
- **Public repo** (ADR-13): rulesets, environments, secret scanning + push protection and unlimited Actions on the Free plan. The repo never contains secrets. All rights reserved (`LICENSE`).
- **English repository, Spanish UI** (ADR-16).
- **applicationId** `io.github.lopezjavier.appfitness`; `local` flavor with the `.local` suffix (ADR-14). Two Android OAuth clients are registered in Google Cloud.
- **Supabase deployment in E0** (previously planned in US-02): `deploy-supabase.yml` is created and validated now with an empty project; US-02 only adds migrations, tests and `supabase-ci`/`keepalive`.
- **Secrets are entered by the user** in their terminal (`gh secret set …`) or in the web consoles; they never go through the chat or versioned files. Claude only verifies names (`gh secret list`).
- **Squash merge only**, automatic branch deletion; required approvals = 0 (authors cannot approve themselves), checks are the gate.
- **Production Auth configured in the Supabase dashboard** (Google on, Email off). `supabase/config.toml` only describes the local environment (Email on for "Test login" + optional Google via `env()`).
- **Tasks in `tasks/todo.md`** (`planning-and-task-breakdown` skill convention); `.workflow/` (plan of the active task, PR body, audit log) is local and not versioned.
- In E0 the PM runs the tasks directly (no Planner/UX/QA). From **T-008** on, every change goes through a PR because `main` is already protected.

## Task List

### Phase A: Local repo and Claude Code
- [ ] T-001: Verify local environment and re-authenticate `gh`
- [ ] T-002: Repo content and git base files
- [ ] T-003: Documentation ready for a public repo
- [ ] T-004: CLAUDE.md, execution log and skills review
- [ ] T-005: Claude Code subagents

### Checkpoint A
- [ ] Clean `git status` after the initial commit; a new Claude Code session lists the 7 project subagents; user review.

### Phase B: GitHub
- [ ] T-006: Connect to the GitHub repo and first push
- [ ] T-007: Security and `main` protection

### Checkpoint B
- [ ] Direct push to `main` rejected; a test PR shows the `gitleaks` check; secret scanning enabled.

### Phase C: Supabase and deployment with GitHub Actions
- [ ] T-008: Production Supabase project and `supabase/` initialization
- [ ] T-009: `production` environment and deployment secrets
- [ ] T-010: `deploy-supabase.yml` workflow

### Checkpoint C
- [ ] Manual and merge-triggered `deploy-supabase` runs green against the real project.

### Phase D: Google OAuth
- [ ] T-011: OAuth clients in Google Cloud
- [ ] T-012: Auth providers in Supabase (prod and local)
- [ ] T-013: E0 wrap-up

### Checkpoint D (E0 closure)
- [ ] Production `GET /auth/v1/settings`: `google: true`, `email: false`; DoD of `planning/epics/E0-setup.md` met.

## Dependencies

```
T-001 ─► T-002 ─► T-003 ─► T-004 ─► T-005 ─► T-006 ─► T-007 ─┬─► T-008 ─► T-009 ─► T-010 ─┐
                                                             └─► T-011 ───────────► T-012 ◄┘ (T-012 also needs T-008)
                                                                                     │
                                                                                   T-013
```
T-011 (Google Cloud) can run in parallel with T-008–T-010, but it needs the Supabase project ref (T-008) for the redirect URI.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| A secret gets committed to a public repo | High | `.gitignore` from T-002, push protection + gitleaks (T-007), secrets only via terminal/consoles; if it happens: rotate the secret immediately, deleting the commit is not enough |
| `.claude/skills` junctions cannot be versioned by git | Medium | Version `.agents/skills` + `skills-lock.json`, ignore `.claude/skills/`; the README explains how to recreate the junctions |
| `supabase functions deploy` fails when there are no functions | Medium | The workflow only deploys functions if `supabase/functions/*/index.ts` exists |
| Supabase CLI behavior changes between versions | Medium | Same pinned version locally (`npx supabase@<v>`) and in GHA; Dependabot proposes upgrades |
| Lost DB password or Supabase token | Medium | Store them in a password manager in T-008/T-009 |
| Different debug SHA-1 on another PC | Low | Documented in `08-environment-setup.md`; add another Android client or reuse the keystore |
| Production Auth configured by hand (drift from the docs) | Low | T-012 documents the `curl` verification so it can be repeated |

## Open Questions
- None blocking. Supabase region: proposed `eu-west-3` (Paris) or `eu-central-1` (Frankfurt); the project was created in `eu-north-1` (Stockholm), also EU.
