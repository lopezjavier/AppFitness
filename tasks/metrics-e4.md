# E4 quality baseline (compose-ui-developer on Sonnet 5.5)

Purpose: baseline for comparing the compose-ui-developer agent on Sonnet 5.5 (model used for the UI slice during all of E4) against Haiku 5.5.

Sources: `.workflow/execution.log` (E4 entries), merged PR reviews and comments (`gh pr view`), CI runs per branch (`gh run list`), `gh pr diff` of each merged PR, `tasks/todo.md`, `planning/epics/E8-finalization.md` (feedback table), `git log`.

Definitions:
- UI lines = added + removed lines in `.kt` files under `composeApp/src/` (commonMain screens/designsystem, commonTest, androidHostTest) of the merged PR. Resource XML is excluded. Includes the PR's whole composeApp diff, so it counts ViewModel/state code that lives in composeApp, not only Composables.
- Host tests = `@Test` annotations added (+) and removed (-) in `composeApp/src/androidHostTest` (Robolectric).
- Red CI = runs with conclusion `failure` before the first green one. `cancelled` runs (superseded by newer pushes) are ignored.
- Verdicts come from the log and, where the log has no entry, from the PR review itself.
- Not every task used the UI slice; tasks without UI show 0 / "-".

## Sonnet 5.5 (E4)

| Task | pr-reviewer P / R | UX-reviewer P / R | Red CI before green | Follow-up fix tasks from checkpoint feedback | Host tests (+/-) | UI lines (PR) |
|---|---|---|---|---|---|---|
| T-055 Food catalog backend (#95), backend only | 1 / 0 | - | 0 | none | 0 | 0 |
| T-064 Center exercise animation (#96), fix, minimal flow | 1 / 0 (PR review; no log entry) | - | 0 | none | 0 | 18 |
| T-056 Daily diary and dashboard (#97) | 1 / 0 | 1 / 0 | 0 | none (E4-A UI feedback went to E8 table) | +24 / -0 | 1812 |
| T-057 Food search and logging (#98) | 1 / 0 | 1 / 0 | 0 | T-065 (bug found at E4-A) | +47 / -0 | 3435 |
| T-065 Food search error retry (#99), backend only | 1 / 1 | - | 0 failures; 1 supabase-ci port flake rerun (log) | none | 0 | 0 |
| T-058 Barcode and manual food (#101) | 1 / 0 | 1 / 1 (plan stage) | 0 | T-067 items 2, 3 (partly) | +25 / -0 | 2127 |
| T-059 Custom meal structure (#102) | 1 / 0 | 1 / 0 | 0 | T-067 item 7 | +37 / -0 | 2185 |
| T-060 Diary corrections, copying, view modes (#104) | 1 / 0 | 1 / 0 | 0 | T-066 (items 1, 5, 6, 8; removes most of it) | +34 / -0 | 4073 |
| T-066 E4-B feedback: remove low-value features (#106), fix | 0 / 1 (PR review CHANGES REQUIRED on docs; no re-review found, no log entry) | n/a | 0 | none | +1 / -22 | 2113 |
| T-067 E4-B feedback: manual foods, scanner, meals (#107), fix | 1 / 0 | 1 / 0 | 0 | none (round 2 feedback went to E8 table) | +17 / -4 | 1376 |

Notes:
- Totals over the 10 merged tasks: pr-reviewer 9 PASSED / 2 REJECTED; UX-reviewer 6 PASSED / 1 REJECTED; red CI 0 (1 flake rerun); 185 host tests added, 26 removed; 17,139 UI lines.
- The log hint "1 pr-reviewer REJECTED" is incomplete: T-066 also got a CHANGES REQUIRED review on the PR (docs item, answered by a comment), and the log has no pr-reviewer entry for T-064 or T-066.
- The log holds 10 pr-reviewer lines (9 tasks; T-065 has REJECTED then PASSED). T-064 and T-066 have PR reviews only.
- The T-066/T-067 follow-up mapping is task-level: T-066 and T-067 were born from the whole E4-B round 1 feedback, attributed here to the task that built the feature being corrected.
- T-061..T-063 are not merged yet and are excluded.

## Checkpoints

Classification is the PM's reading of the items (UI = look, layout, placement, labels; functional = behaviour, bugs, product scope).

| Checkpoint | Feedback items | UI | Functional | Where handled |
|---|---|---|---|---|
| E4-A (after T-057) | 8 | 7 | 1 (food search error, T-065) | UI items in the E8 table (planning/epics/E8-finalization.md), bug as T-065 |
| E4-B round 1 | 8 (item 4 informational: OFF lacks liquids, no action) | 1 (item 7 meal structure placement/layout) | 6 (items 1, 2, 3, 5, 6, 8) | T-066 (1, 5, 6, 8), T-067 (2, 3, 7) |
| E4-B round 2 | 7 | 7 (todo.md says all UI; 3 of them, persist-on-Guardar, unique names, no double-add, are behavioural in nature) | 0 | E8 table, postponed by the user |

## Haiku 5.5

Same columns as the Sonnet table; add rows here for the tasks built with compose-ui-developer on Haiku.

| Task | pr-reviewer P / R | UX-reviewer P / R | Red CI before green | Follow-up fix tasks from checkpoint feedback | Host tests (+/-) | UI lines (PR) |
|---|---|---|---|---|---|---|
| | | | | | | |
