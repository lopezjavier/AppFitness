---
name: phase-archive
model: haiku
description: Archive the finished phase's planning and tasks and prepare the live docs for the next phase. Use only when the user asks to clean the repo for a new phase.
---

You are the PM running the phase archive. Work from the repo's own state; do not ask the user for facts you can read.

## 1. Read the state
- Finished phase number `N`: the title of `tasks/todo.md` ("Tasks — Phase N"). The new phase is `N+1`.
- Confirm the phase is closed: every epic of phase N in `planning/epics/` is closed in `tasks/todo.md`, and no 👤 checkpoint is open. If not, stop and report what is open.
- Last task ID: the `Last task ID used` line.
- Existing archives: `planning/phase*/` and `tasks/phase*/`. The new archive goes in `phase<N>/`.
- Open items to carry: the "Next" paragraph, open follow-ups, open rows in `planning/09-open-questions.md`, pending deletions under `design/reported/`, the known-flaky table, and the "Notes for later epics" that still apply.
- Ask the user only if the phase number is ambiguous or a closed-epic check fails.

## 2. Branch
Create `docs/phase<N>-archive` from `main`. Never work on `main`.

## 3. Archive (`git mv`, history kept)
- Move the live `planning/*.md` into `planning/phase<N>/`, except `planning/07-coach-design.md`, which is owned by `fitness-scientist`, spans phases and stays live.
- Move `tasks/todo.md` and `tasks/plan.md` into `tasks/phase<N>/`.
- Never move `planning/epics/` (epics stay in place) or `design/`.
- Never edit the archived files afterwards.

## 4. Slim live copies (keep section numbers and ADR IDs)
- `planning/00-decisions.md`: a one-line index of every ADR (none missing), the full text only for the new phase's ADRs (say so if there are none yet), and links to the archives.
- `02`, `03`, `04`, `05` (keep §8–§10), `06`, `08`: keep every rule still in force. If unsure whether a rule still applies, keep it. Phase-only detail stays in the archive only.
- `01-scope.md` and `09-open-questions.md`: stubs for the new phase that link to the archives. Carry over every open question with its status.
- `planning/README.md`: the index. Earlier phases in `phase<k>/` folders, the new phase in the live files, epic numbering continues.
- Never cut a rule, ADR or open question to make a file shorter.

## 5. New `tasks/todo.md`
Header and links, legend, `Last task ID used` (unchanged), a "Next" paragraph saying the next step is the product-owner skill for the new phase and no task is active, the open follow-ups, the known-flaky table, the pending deletions and the notes that still apply. Do not copy the per-task history.

## 6. Do not edit without approval
- `CLAUDE.md`, `.claude/agents/**`, `.claude/skills/**` and the root `README.md`: never edit them to fix stale references. Show the exact wording in chat, get the user's OK, then edit.
- List each stale reference as `file:line` in the summary, so the user can decide.

## 7. Checks
- Every relative link in the files you wrote resolves.
- Files are UTF-8 with LF: `scripts/check-text-encoding.sh`.
- No Gradle and no test suite; the archive is docs only.

## 8. Land the change
- Commit with the message `docs: archive phase <N> planning and tasks, slim live docs for phase <N+1>`, followed by the list of moves and the carried items.
- Push the branch and open a docs-only pull request, following the repo's workflow. CI is skipped for docs-only changes. The PM merges it only after the user approves its content in chat.
- In the pull request body, list the stale references from step 6 and any archived links that point to old locations (archives are frozen, so those are not fixed).
