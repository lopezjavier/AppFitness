# T-287 · Docs: ADR-107, amended stories, rules and the feature inventory (US-123)

Slice D of the T-277 split. Sources: `tasks/todo.md` (T-277 entry: "User decisions", "SPLIT"), `tasks/T-277/removal-plan.md` (S5, slice D), `tasks/T-277/audit_*.md`, `tasks/T-277/feature_inventory*_draft.md`, `tasks/T-286/plan.md`. This file does not repeat them; it fixes the docs scope against what T-277 and T-286 actually removed.

## Goal
Record the removal as ADR-107, mark the stories it amends, remove every live-doc claim about the removed things, add rule R-63, and publish `planning/10-feature-inventory.md` listing only what exists now.

## Gherkin scenarios covered
US-123 "A decision becomes a rule" (no doc or copy claims the coach reads notes) and "Behaviour does not change" (docs describe the remaining behaviour only).

## Flow type: docs only (no code, no Gradle, no SQL, no host tests, no restoration test)
Agent: **kotlin-core-developer** (it can read the Kotlin routes and the SQL; or any docs-capable agent the PM prefers; backend-developer is not needed). ux-reviewer words R-63 and checks the glossary edit; pr-reviewer reviews; pr-creator with no local gate (docs-only; `scripts/check-text-encoding.sh` only). Do not touch `tasks/todo.md` / `tasks/plan.md` (PM handoff PR). Frozen `planning/phase1/`, `planning/phase2/` and epics E0-E9 are NOT edited.

## What was really removed (verify first; facts, not the drafts)
- Migration `supabase/migrations/20270127100000_remove_unused.sql` (T-286) already cites ADR-107. It drops functions `coach_free_text_lines`, `set_free_text_line_excluded`, `save_meal_slots`, `delete_meal_slot`, `coach_plan_week_of_date`, `assert_unique_meal_labels`, `convert_used_off_foods`, `coach_rederive_plan_links`, `migrate_equipment_list`, **`off_alcohol_g` and `coach_plan_calendar`** (both were conditional in the plan; the file drops them, so the ADR lists them as dropped), table `coach_free_text_exclusions`, columns `coach_user_intake.free_text` and `weekly_check_ins.notes`; recreates `coach_summary`, `coach_plan_inputs`, `save_coach_intake` without `free_text` (the last ignores the key); scrubs `coach_recommendations.input_summary`. Kept: `exercise_discomforts.note`, `coach_plan_calendar_for`, `copy_off_food_core`, all 8 Edge Functions.
- Executor: re-read the migration's final self-check and the PR body/merge notes of T-286 (`git log`, `gh pr view`) for the real non-empty-notes count and any deviation, and record that number in the ADR. If unavailable, write "reported by the migration notice, see the T-286 PR".
- App side (T-277, PR #648): `FreeTextLinesRoute` + screen/ViewModel/UiState, Trainer row `trainer_free_text_row`, the "Para conocerte" screen/route/ViewModel (deleted entirely, not only the field: years training and maintenance kcal are edited through the `IntakeRowEditor` sheets, "Tus datos"), check-in note field, `CoachLimitMessage` part, `NAV_STATE_VERSION` bump, dead use cases (see `removal-plan.md` S2). Read `git show --stat 0867f507` to list it accurately; do not copy the removal plan blindly (its "Deviation" note wins). Also verify what T-277 did with `scripts/check-unused-resources.py` and the Python-test wiring, and say it in the ADR if present.

## Changes (exact files)

1. `planning/00-decisions.md`
   - Header line 7: "next: **ADR-106**" -> "next: **ADR-108**". ADR-107 is verified as the next free number (index ends at ADR-106; `grep -n "ADR-107" planning/00-decisions.md` must be empty before the edit).
   - Phase 3 index (after the ADR-106 row, line ~121 table): one row for 107.
   - Full text after ADR-106 (context -> decision -> consequences): title "ADR-107 — Free-text notes and unused code removed; the coach is deterministic (E19 US-123; amends ADR-97, ADR-98, ADR-99 and the US-81/US-89 decisions)". Content: context (the `plan_*` kinds are `deterministic: true`, the Anthropic text path is gone by ADR-103, so the free-text switches controlled nothing); decision (list of removed backend objects above, with the notes count; removed app items by name; kept items with the reason, notably `exercise_discomforts.note` kept for cross-session discomfort tracking, possible later task); consequences (rule R-63; old APK v1.9.0 window and the mitigations used; "no keep for later" policy and the resource check script if it exists; `07` words-never-change-numbers (ADR-87) stays true).
   - Phase 2 index rows for 97, 98, 99 (lines ~96-109 area of this same file, where they appear): append "Amended by ADR-107" to the status cell. Frozen `phase2/00-decisions.md` untouched.
2. `planning/epics/E13-performance-assistant.md`: US-81 (line ~1032) and US-89 (line ~1488): add a blockquote right under each heading, in the style of `E17-food-data-quality.md:26`: "**Amended by ADR-107 (2026-11-11):** ..." US-81: the "Para conocerte" screen and its free-text field are gone; years training and maintenance kcal live on in "Tus datos"/row editors (state what the code does). US-89: the whole story is superseded (screen, switches, notes reaching the coach removed). Reword or strike the scenarios that mention the notes screen/switches, including line ~354 ("the line is not left out in ..."), line ~27 and the 5 hits at lines 27, 354, 1488-1522. No Gherkin may keep promising removed UI.
3. `planning/epics/E12-training-data-and-preferences.md`: only if a live scenario promises the notes reaching the coach (grep `coach` near lines 110, 157); otherwise add nothing. `E10-reminders-and-check-in.md:18`: the `weekly_check_ins` description loses `notes`, add "Amended by ADR-107: the check-in note was removed". `E4-food-diary.md:437`: the `delete_meal_slot` sentence now says deletion rules live in `save_meal_structure` (read the live RPC to say the right thing). `E19-ui-review.md`: US-123 gets a one-line "Done by T-277/T-286/T-287" only if that file has a status convention; otherwise untouched.
4. `planning/05-design-system.md`
   - §8: add **R-63** after R-106 (line ~257; verified R-106 is the highest, `grep -n "R-63"` empty): "No copy claims the coach reads or uses something it does not" (ux-reviewer words the final text: any help, row, title or message that says what the coach reads or uses must name something the coach really reads; when a datum stops being used, its copy goes in the same task). Follow the format and the "sweep" convention of R-104/R-106.
   - §10 glossary row at line 219 ("What the coach reads (T-186)", "Lo que el entrenador sabe de ti"): delete the row, or, if a glossary entry for the concept is still needed, rewrite it to what the coach reads now (verify in `07`). Check lines 45 and 116 (long lines with hits) and fix any mention.
5. `planning/03-data-model.md` (lines 30, 42, 48, 49, 50, 74, 82, 83, 85, 88, 92, found by the sweep): drop the `coach_free_text_exclusions` entry (line 50); `weekly_check_ins` loses `notes` (line 48); `coach_user_intake` loses `free_text`; `coach_plan_inputs`, `coach_summary`, `save_coach_intake` rows lose `free_text` (lines 42, 49, 74-92 as the sweep shows); RPC lists drop the 11 dropped functions (line 83 "legacy save_meal_slots, delete_meal_slot"); line 30 keeps the history of the `20270120100100_convert_used_off_foods` migration only as a past event (the function is dropped: say "one-shot, function dropped by T-286"). `exercise_discomforts.note` stays documented. Verify each statement against `supabase/migrations/` heads (the live definition), not the old text.
6. `planning/04-business-rules.md` (lines 36, 37, 95, 106, 107, 142, 143 per the sweep): remove or reword the rules about free text/notes (the section for "Lo que el entrenador sabe de ti", §7.8 referenced from 03:50), `coach_free_text_lines` order/limits, the 2 000-byte cut. Numeric rules and examples do not change; if a formula text is affected, stop and report to the PM.
7. `planning/07-coach-design.md` (line 637 and the ~63 loose "notes/words" hits; only mentions that the coach reads the user's notes/free text): reword to "the coach reads only the structured data" or delete the sentence. No prescription changes, so no `fitness-scientist` involvement; if a rule's number or default would change, stop and report. Keep ADR-87 "words never change numbers" true.
8. `planning/02-architecture.md`: Grep `FreeText|free_text|IntakeRoute|Para conocerte|coach_free`; edit only on hits (expected none). Edge Functions list stays at 8 (verified: health, food-barcode, account-status, delete-account, food-photo, food-search, coach, exercise-chat). Add a mention of `scripts/check-unused-resources.py` in the CI section only if T-277 created it (`ls scripts`).
9. `planning/README.md`: add a table row `| [10-feature-inventory.md](10-feature-inventory.md) | Every functionality of the app by area, with route and story; kept up to date by the task that adds or removes a feature |` after the 09 row (line ~18); also fix the stale ADR range in the 00 row ("ADR-01..100 ... none yet") to "ADR-01..107".
10. **`planning/10-feature-inventory.md`** (new; built from the current code). Method:
    - Start from `tasks/T-277/feature_inventory_draft.md` and `feature_inventory_backend_draft.md`, then REVERIFY each line against the repo and drop what T-277/T-286 removed.
    - Routes: Grep `composable<` and `dialog<` in `composeApp/src/commonMain/.../app/AppNavHost.kt` (50 matches today) and the route types in `AppRoutes.kt`; the draft counted 48 routes after removal but "Para conocerte" `IntakeRoute` was also deleted by T-277, so recount (expected 47-48; the number in the file is the verified one, and a line records the count and the date).
    - Backend: `ls supabase/functions` (8 functions), `cron.schedule` entries in `supabase/migrations`, the RPC names the app calls (`rpc("` in `shared/src`), cross-checked against live definitions (last migration defining each).
    - Format: sections Diario, Peso, Entreno, Trainer, Perfil, Inicio, Centro de avisos, Backend jobs and Edge Functions; one line per functionality: name (Spanish UI label as shown), route type name or "sheet/dialog of <screen>", RPC/Edge Function if any, story `US-##` (grep the epics for the owner story; "-" only if none). First line states "Updated by T-287 on 2026-11-11 after the T-277 cleanup; a task that adds or removes a functionality updates this file". No copied planning text, no code.
    - It lists nothing removed. A "Removed (history)" section is NOT included (ADR-107 holds that).

## Contracts
None (no code or API change). The inventory is the only new contract: every route in `AppNavHost.kt` appears in it.

## Tests / verification (no automated test; the executor runs these and pastes results in the PR body)
Run from the repo root; every grep over `planning/` excludes `planning/phase1`, `planning/phase2`, `planning/epics/E[0-9].md`... (frozen) and `00-decisions.md` ADR-107/ADR-97-99 text:
1. `Grep -i "free_?text|FreeText|coach_free_text|Lo que el entrenador sabe|coach_summary_free_text"` over `planning/` minus frozen = only ADR-107 and the "Amended by" notes.
2. `Grep "save_meal_slots|delete_meal_slot|coach_plan_week_of_date|convert_used_off_foods|coach_rederive_plan_links|migrate_equipment_list|assert_unique_meal_labels|off_alcohol_g|coach_plan_calendar\b"` = only ADR-107 and, if kept as history, the single `03` line 30 note. (`coach_plan_calendar_for` must still appear: it is kept; the `\b` plus a check of `_for` avoids a false hit.)
3. `Grep "weekly_check_ins" -A2` in `03`, E10: no `notes`. `Grep "Para conocerte"` in live docs: every hit describes something that exists (row editors/"Tus datos"), verified against `strings.xml`.
4. Reverse check of the new inventory: for each `composable<`/`dialog<` route type in `AppNavHost.kt`, `Grep` its name in `planning/10-feature-inventory.md` = found; for each folder in `supabase/functions` = found; each RPC named in the inventory exists in `supabase/migrations` and is not in the dropped list.
5. Links: `planning/README.md` row exists; relative links in the new file resolve (`Glob`).
6. `scripts/check-text-encoding.sh` (UTF-8, LF) passes; `grep -n "ADR-107" planning/00-decisions.md` shows index row + full text + the three "Amended by" cells; header says next ADR-108.
7. `check-ux-conventions.sh` is unaffected (no app files touched); do not run Gradle.

## Implementation order
1. Read the T-277/T-286 facts above (git log/show, migration, PR bodies); fix the list of removed items.
2. `00-decisions.md` (ADR-107, index, header, amended cells).
3. `03`, `04`, `07`, `02` edits, then E13/E10/E4(/E12) amendments.
4. `05` (R-63 text goes to ux-reviewer for the wording before commit; glossary).
5. `10-feature-inventory.md`, then `README.md`.
6. Run verification sweep 1-6; fix residue; commit as `docs(T-287): ...` (one commit is fine).
7. pr-reviewer + ux-reviewer (docs/copy) in parallel; pr-creator, no gate.

## Risks and open questions
- BLOCKING: none.
- The todo/removal plan say `off_alcohol_g` and `coach_plan_calendar` were "conditional"; the merged migration drops both, so the docs follow the migration. If the executor finds `03`/`04` documenting alcohol energy via `off_alcohol_g` in a formula, rewrite to the live definition (the SQL check function `food_energy_check`), do not change the rule.
- `IntakeRoute` was deleted by T-277 (deviation), so any live doc/inventory line for "Para conocerte" as a screen must be reworded to the row editors; US-81's amendment note must say so. If the executor cannot tell which strings/screens survive, read `composeApp` strings and `AppNavHost.kt`, not the drafts.
- Docs-only PR for a decision (ADR) the user already approved in chat (2026-11-11 decisions, SPLIT); R-63 wording is new text: ux-reviewer words it, and the PM shows it if CLAUDE.md "rule changes need an explicit OK" applies (it concerns CLAUDE.md/agents/workflow, not `05` §8; no hook issue expected).
- Shared docs with other readers: `03`/`04`/`07` are read by every agent; keep edits minimal and factual. `07` owner is `fitness-scientist`: wording-only edits here, no prescription change.
- Old-app window text in ADR-107 must match what happened (T-277 APK installed before T-286 deployed?); take it from the T-286 merge notes.
