# T-286 · Backend removal: free-text storage and unused SQL objects (US-123)

Slice E of the T-277 split. Source plan: `tasks/T-277/removal-plan.md` S1 + slice E; audit rows B1-B23 in `tasks/T-277/audit_backend.md`; user decisions in `tasks/todo.md` T-277 ("User decisions on the audit", "SPLIT"). Depends on T-277 (app side) merged. Do not repeat their text here; this file only fixes the backend scope.

## Goal
Drop the free-text storage and every unused SQL object the user approved, recreate the readers without `free_text`, scrub existing `coach_recommendations.input_summary`, and prove it with a row-count self-check that REPORTS how many non-empty `weekly_check_ins.notes` were removed. No app code, no docs (T-287).

## Gherkin scenarios covered
US-123 "Behaviour does not change" (backend part: remaining RPCs return their other fields unchanged); "A decision becomes a rule" (nothing stores or forwards user free text to the coach).

## Flow type: backend (backend-developer only; no UI, no host tests, no restoration test)
Gate: none for `verify-local.sh` (SQL task); `supabase test db` + `deno test` by the developer; CI `deploy-supabase` must be green on `main` after merge with data intact (PM confirms before T-287 / next checkpoint), then PM delivers the new APK.

## Changes

### Migration (ONE file, LAST commit): `supabase/migrations/20270127100000_remove_unused.sql`
Confirm timestamp is the next free one after `20270126100000`. Single transaction, this order:
1. Snapshot: row count of every user table that is not dropped, into a `pg_temp` table; plus `count(*) filter (where btrim(notes) <> '')` of `weekly_check_ins` and `count(*)` of `coach_free_text_exclusions` and non-null `coach_user_intake.free_text`; `raise notice` each ("N non-empty weekly_check_ins.notes removed").
2. Scrub: `update coach_recommendations set input_summary = input_summary - 'free_text' where input_summary ? 'free_text'`, idempotent. Then assert `count(*) where input_summary::text like '%free_text%'` = 0; if a nested key exists (e.g. an entry with `key = 'free_text'` in plan inputs) extend the scrub before raising. Count of rows unchanged.
3. Recreate (full `create or replace`; copy the LIVE body via `supabase db reset` + `pg_get_functiondef`, NOT old migration text, because 20270110/20270126 used `pg_temp.patch_fn`): `coach_summary(date)` without the `free_text` key; `coach_plan_inputs(text,date)` without the two `coach_input_entry('free_text', ...)` lines; `save_coach_intake(jsonb)` ignoring a `free_text` key (no error, not stored, not returned, no length validation on it). Re-issue `revoke ... from public, anon` + `grant ... to authenticated` after each (repo R-02 pattern).
4. Before each drop, a `do` block raises if any live `public` function body still references it (`prosrc ilike '%name(%'` / `pg_depend`). Then drop:
   - Functions: `coach_free_text_lines(date)`, `set_free_text_line_excluded(text,text,boolean)`, `save_meal_slots(jsonb)`, `delete_meal_slot(uuid)`, `coach_plan_week_of_date(uuid,date)`, `assert_unique_meal_labels`, `convert_used_off_foods()`, `coach_rederive_plan_links(uuid)`, `migrate_equipment_list` (exact signatures from `pg_proc`, not from memory).
   - Table `coach_free_text_exclusions` (policies go with it); columns `coach_user_intake.free_text` (+ constraint `coach_user_intake_text_length`) and `weekly_check_ins.notes` (+ `weekly_check_ins_notes_length`).
   - Order matters: recreate readers (3) and drop `save_meal_slots` before `delete_meal_slot`; drop `coach_free_text_lines` after `coach_summary` is recreated.
5. Evidence-based decisions (developer decides on the live bodies after `db reset`, records result in the PR body):
   - `off_alcohol_g`: drop only if no live function body (`food_energy_check`, `copy_off_food_core`, food RPCs) calls it; else keep and say so. If dropped, strip its calls from `food_energy_check.test.sql` (10 refs).
   - `coach_plan_calendar(uuid,date)`: drop if `coach_plan_weeks` (recreated in `20270126100000:346` on `coach_plan_calendar_for`) and every live body no longer call it; then fix `coach_plan_weeks_pause.test.sql` lines ~87, 172, 328 (rewrite the helper on `coach_plan_calendar_for` or delete the assertions that need it).
   - `copy_off_food_core`: KEEP (called by live `copy_off_food`); leave its `p_enforce` default alone.
6. Final self-check in the same file: every snapshotted table count unchanged (raise otherwise); `to_regprocedure`/`to_regclass`/`information_schema.columns` prove each dropped object absent; `exercise_discomforts.note` still exists; all `coach_recommendations.input_summary` still valid jsonb with row count unchanged.

Explicitly kept (list in PR body): `exercise_discomforts.note`, `coach_plan_calendar_for`, `copy_off_food_core`, `coach_recommendations.input_summary` column, B14-B18 items, all 8 Edge Functions (nothing to undeploy; `deploy-supabase` runs `functions deploy` only).

### Edge Functions
`supabase/functions/coach/planNutrition.test.ts`: delete the test "free text in the summary never changes a figure" (~519-531) and any `free_text` fixture field in `coach/*.test.ts` (grep). No production TS change. `deno test` of `coach` green.

### pgTAP (`supabase/tests`; adjust every `plan(n)`)
- Delete: `coach_free_text.test.sql`, `save_meal_slots.test.sql`, `delete_meal_slot.test.sql`, `off_food_conversion.test.sql` (first move any non-convert assertion to a kept file, e.g. `copy_off_food.test.sql`).
- Edit: `coach_summary.test.sql` (~206-225 free_text and the 16 384-byte worst-case test), `coach_plan_inputs.test.sql` (8 refs), `coach_user_intake.test.sql` (15 refs; add: `save_coach_intake` with a `free_text` key succeeds, stores nothing, returns the other fields unchanged), `delete_account_cascade.test.sql` (remove insert ~101 and its count assertion; stays green), `weekly_check_in.test.sql` (22 refs; drop notes, keep energy), `equipment_ez_bar_cardio_machine.test.sql` (14 `migrate_equipment_list` refs), `meal_slots.test.sql` (1), `coach_plan_weeks_pause.test.sql` (week_of_date/rederive/calendar assertions), `copy_off_food.test.sql`, `meal_structure_save.test.sql`, `food_energy_*` (only name references).
- New `removed_objects.test.sql`: `hasnt_function` for every dropped function, `hasnt_table`, `hasnt_column` (`coach_user_intake.free_text`, `weekly_check_ins.notes`), `has_column` `exercise_discomforts.note`; `coach_summary` result has no `free_text` key and keeps its other keys; `coach_plan_inputs` has no `free_text` entry; scrub test: insert a `coach_recommendations` row with `free_text` in `input_summary`, run the same `update` statement, assert key gone and other keys intact; cross-user isolation: user B cannot read/alter user A's `weekly_check_ins` / `coach_user_intake` rows (existing tests stay green; add one assertion if the edited files lose coverage).

## Contracts
- `save_coach_intake(p jsonb)`: same keys as before except `free_text` (ignored, not returned).
- `coach_plan_inputs(text,date)`: no `free_text` entry. `coach_summary(date)`: no `free_text` key.
- `weekly_check_ins(user_id, week_start, energy, ...)` without `notes`. No new objects, no new grants.

## Tests
Unit: n/a (no Kotlin). pgTAP and deno as above (about 1 new file with ~25 assertions, ~12 edited, 4 deleted). testTags/host/restoration: not applicable (no UI).

## Implementation order (single developer run, backend-developer; each step leaves `supabase test db` green until the last)
1. `supabase db reset` on the current head; dump live bodies and grep for `off_alcohol_g`, `coach_plan_calendar(`, and every dropped name over `pg_proc.prosrc`; record the three evidence decisions.
2. Deno test edit; `deno test` green.
3. pgTAP edits/deletions and new `removed_objects.test.sql` (red against the old schema is fine locally; commit them separately from the migration).
4. Migration as THE LAST COMMIT (a review fix never redeploys a half-done drop; if a fix is needed after, amend by a new commit only to tests, never touch the migration after merge-ready unless re-reviewed).
5. `supabase db reset` + `supabase test db` + `deno test` green; capture the `raise notice` row-count output (non-empty notes count) for the PR body. Also run the migration against a copy with seeded `free_text` and a non-empty `weekly_check_ins.notes` row to prove the notice reports it.
6. Sweep, recorded in the PR body: Grep `free_?text|coach_free_text|weekly_check_ins.*notes` over `supabase/functions`, `supabase/tests`, `supabase/seed.sql` = only the new migration's drop/scrub and `removed_objects.test.sql`; Grep each dropped function name over `supabase/` non-migration = only `removed_objects.test.sql`.
7. pr-reviewer (no ux-reviewer: no UI), then pr-creator with no local gate; CI runs pgTAP/deno; after merge `deploy-supabase` green on `main`, PM confirms data intact (counts match the notice), then delivers the APK.

## Risks and open questions
- Old app v1.9.0 impact after deploy: free-text screen error and failing switches; "Para conocerte" save works (key ignored); old Check-in breaks (`WeeklyCheckInDto.COLUMNS = "week_start,energy,notes"` selects the dropped column and upsert sends `notes`). Mitigation: T-277 merged and its APK installed before this merges, or install it right after `deploy-supabase` is green. PM tells the user before merge.
- Production UPDATE (scrub) and DROP COLUMN `weekly_check_ins.notes` are irreversible; both approved by name by the user (2026-11-11). Self-check must raise on any count mismatch so the transaction rolls back.
- `pg_temp.patch_fn` ghosts: live function bodies differ from migration text; hence copying from `pg_get_functiondef` and the per-drop reference check.
- Conditional drops (`off_alcohol_g`, `coach_plan_calendar`) depend on live-body evidence; if still referenced they stay and the PR lists them as kept-used (not a blocker; the user's "drop unused" decision covers only unused ones).
- BLOCKING: none.
