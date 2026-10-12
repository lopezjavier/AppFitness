# E0 · Project setup

## Goal
Get the repository, GitHub, Supabase and Google OAuth ready so Claude Code can work with the `06-agent-workflow.md` flow from E1 on, including automatic Supabase deployment with GitHub Actions (ADR-15).

## Flow type
Manual (PM + user). It does not go through Planner/UX/QA; it is the only epic where the PM writes files directly. From the protection of `main` (T-007) on, every change goes through a PR.

## Dependencies
None. Public repo `lopezjavier/AppFitness` already created (ADR-13).

## Tasks
Full breakdown with criteria and verification in [`tasks/todo.md`](../../tasks/todo.md) and plan in [`tasks/plan.md`](../../tasks/plan.md):

| Phase | Tasks |
|---|---|
| A · Local repo and Claude Code | T-001 environment and `gh` · T-002 repo content + git base files · T-003 docs ready for a public repo · T-004 `CLAUDE.md` + log + skills review · T-005 subagents |
| B · GitHub | T-006 first push + PR template + Dependabot · T-007 security + `main` ruleset |
| C · Supabase + GHA | T-008 production project + `supabase init` · T-009 `production` environment + secrets · T-010 `deploy-supabase.yml` |
| D · OAuth | T-011 OAuth clients in Google Cloud · T-012 Auth providers in Supabase · T-013 wrap-up |

## Acceptance criteria

```gherkin
Feature: Project ready for the agent workflow

  Scenario: Repository published and protected
    Given the public repository lopezjavier/AppFitness
    When the PM completes phase B
    Then main contains the project's initial commit
    And a direct push to main is rejected
    And a PR can only be merged with the gitleaks check green
    And no ignored file (local.properties, .env, keystores) is versioned

  Scenario: Claude Code recognizes the workflow
    Given CLAUDE.md and .claude/agents/ are in the repository
    When the user asks a new session which project subagents are available
    Then planner, ux-reviewer, frontend-developer, backend-developer, qa-engineer, pr-creator and pr-reviewer are listed
    And none of them shows frontmatter errors

  Scenario: Automatic Supabase deployment
    Given the production environment secrets are configured
    When a change in supabase/ is merged into main or the workflow is run manually
    Then deploy-supabase links the production project and applies pending migrations
    And it finishes green

  Scenario: Authentication ready
    When the production project's Auth settings are queried
    Then the Google provider is enabled and the Email provider is disabled
    And Google Cloud has the Web client and the Android clients for io.github.lopezjavier.appfitness and io.github.lopezjavier.appfitness.local
```

## Definition of Done
- Scenarios above verified (commands in `tasks/todo.md`).
- `.workflow/execution.log` contains `[PM] [E0] [COMPLETED]`.
- No tag (there is no APK).
