# Implementation Plan: E17 · Food data quality → `v1.x` (proposed `v1.8.0`)

> **Status: PROPOSED by the epic-planner, 2026-11-09.** The task list (T-251…T-256) needs the user's OK on the breakdown PR; one checkpoint only, Checkpoint E17 at the end (user decision 2026-11-09). Q1 resolved by the user (2026-11-09); no question blocks a task. The E16 plan that lived here is in git history (E16 closed, `v1.7.0`; `tasks/todo.md` E16 section).

## Overview
A food whose kcal do not match its own macros is caught before it is saved: a deterministic energy check (EU factors, with fibre, polyols and a new optional "Alcohol (g)") warns with "Corregir" / "Guardar igualmente" on every way a food is created (manual, edit, AI dish estimate, label scan, Open Food Facts copy). The foods already stored are listed in a food audit the user fixes by hand; a food saved anyway leaves the audit until its numbers change. Open Food Facts becomes a fallback: adding an OFF food creates the user's own editable copy (one per barcode or OFF id), found first in later searches, and the OFF foods already in the diary are converted to copies without changing any past total.

Sources: `planning/epics/E17-food-data-quality.md` (intent, non-goals, US-116…US-119, "Decisions (user, 2026-11-09)"), **`07` §4 "Food energy check (E17, US-116/US-117)"** (every factor, threshold, ordering, worked example and text), `04` §2, `03` §3, §6, §7, ADR-43, ADR-53, ADR-59, ADR-64, ADR-72, ADR-101, `05` §8-§10. Tasks: `tasks/todo.md` (E17 section).

Out of scope (epic non-goals, `01-scope.md`): a verified or curated food database; changing the AI model; automatic corrections; changing past diary totals; alcohol, fibre or micronutrient targets.

## Architecture decisions
- **One check, two implementations with shared cases.** `food_energy_check` in SQL (the audit) and `FoodEnergyCheck` in Kotlin (the forms) implement `07` §4 exactly: `computed = 4·protein + 4·(carbs − p) + 2.4·p + 9·fat + 2·fibre + 7·alcohol`, `p = min(polyols, carbs)`; skip when `kcal_100 ≤ 13` and `computed ≤ 13`; `mismatch` above `0.15 × kcal_100 + 5`, `clearly_wrong` above `0.30 × kcal_100 + 5`. pgTAP and `commonTest` share the `07` worked-examples table (the 1RM precedent). No model call.
- **Alcohol is a column, in grams (Q-E17-1).** Optional, per 100 base units, never % vol. Manual entry types grams (help line with × 0,79); `food-photo` must return it in every mode (0 when none; a response without it is invalid); the OFF import and a backfill of the cached OFF rows convert % vol × 0.789.
- **The "saved anyway" flag is enforced by the database (Q-E17-2).** A trigger clears it when any check input changes, so no write path can leave a stale flag; "Guardar igualmente" sets it. It never changes stored numbers or snapshots. New ADR (proposed ADR-102) in T-251.
- **The warning is not a dialog (R-08).** ux-reviewer picks an inline warning note or an action-archetype sheet in T-252; the same component serves the audit's editor (T-253) and the OFF copy (T-255).
- **Copies, not shared rows.** `copy_off_food` creates or reuses the caller's copy (unique per user on barcode and on `off_id`); from T-255 no new reference to a shared OFF row is written. The OFF cache rows stay for searching. The conversion (US-119) only repoints `food_id` in diary entries, favourites and saved comida items (Q-E17-3); snapshots are untouched, so totals cannot move, and the migration self-checks per-day sums before and after (T-242 precedent).
- **Ordering lives in both layers.** `food-search` and `food-barcode` put the caller's foods (and copies) first and hide OFF rows already copied; `FoodSearchViewModel` keeps its own-first block and adds favourites to it. E18 US-122 later makes that block immediate.
- **No new screen template.** The audit is a standard pushed list screen; its entry point and name are ux-reviewer's choice in T-253 (Preferencias or the food search), recorded in `05` §10.

## Ordering and sizing
Every task pays 6-8 agent hand-offs, so stories stay whole unless one is too large for one PR; no S task.

| Order | Task | Story | Flow | Size | Agent runs |
|---|---|---|---|---|---|
| 0 | PM step: `fitness-scientist` records the energy check in `07` §4 (**done** 2026-11-09) | — | PM | — | 1 |
| 1 | T-251: Energy check, alcohol, flag and audit: backend | US-116, US-117 (backend) | backend | M | 5 |
| 2 | T-252: The warning and "Alcohol (g)" in every food form | US-116 | core + UI | M | 7 |
| 3 ✅ | T-253: The food audit | US-117 | core + UI | M | 7 |
| 4 | T-254: OFF personal copies and the conversion: backend | US-118 (backend), US-119 | backend | M | 5 |
| 5 | T-255: An OFF food becomes my food in the app | US-118 (+ US-116 OFF path) | core + UI | M | 7 |
| 6 | T-256: E17 closure | DoD | PM | closure step | PM only |

Why these splits and merges:
- **US-116 → T-251 (backend) + T-252 (UI):** the whole story is about 28 files (migration, SQL check, `food-photo` schema and prompts, OFF import, the shared Kotlin check and every form path), so it is split backend then UI, not per component.
- **US-117's backend rides in T-251:** the audit RPC is the same SQL function over the user's foods; alone it would be S. Its UI (a new screen, route, entry and editor round trip, about 10 files) is T-253.
- **US-118 + US-119 backend → T-254:** the conversion reuses the copy function and is one data migration (S alone), so both go together; the UI of US-118 is T-255 (together about 20 files, hence the split). US-119 has no UI of its own: converted foods are ordinary own foods.
- **Closure T-256** is the PM closure step released by Checkpoint E17's approval; it has no checkpoint of its own.

## Phases and checkpoints

Items to test in the epic, collected first: (1) the 500 kcal mismatch warning and "Corregir"; (2) small gap and near-zero foods pass; (3) wine without and with "Alcohol (g)" and its help; (4) "Guardar igualmente", a name-only edit and a kcal edit; (5) the AI dish and label paths with alcohol pre-filled; (6) the audit, open an editor, fix and leave the list; (7) past OFF days unchanged and editable; (8) my foods and favourites first, OFF when none; (9) adding an OFF food creates my copy; (10) no duplicate by search or barcode; (11) the copy edits like a manual food; (12) converted copies in the audit and the final regression; plus "leave the app and come back" in each group. **User decision (2026-11-09): one checkpoint only, at the end** (E16 precedent). The items are merged into 10, grouped by journey (catching wrong foods 5, OFF foods become mine 4, leave and come back 1). Not testable by hand (pgTAP only): "Only my data is touched".

### Phase 1: Catching wrong foods (US-116, US-117)
- T-251 → T-252 → T-253. No checkpoint here (user decision); internal milestones (`deploy-supabase` green, `food-photo` redeployed, CI green) are verification items in T-251.

### Phase 2: Open Food Facts foods become mine (US-118, US-119), the checkpoint and closure
- T-254 → T-255 → **👤 Checkpoint E17** → T-256. The T-254 conversion runs in production on merge; `deploy-supabase` green with its self-check is a T-254 verification item and a precondition of the checkpoint.
- **👤 Checkpoint E17 — Food data quality, with the final E17 regression** (after T-255, the last implementation task; 10 items in `tasks/todo.md`; its approval releases T-256). Try: a 500 kcal food with 10/30/10 g shows "Parece un error:" (no dialog) and "Corregir" keeps the values; small-gap foods save silently, a red wine warns without alcohol and saves with 9,5 g; "Guardar igualmente", then a name edit (silent) and a kcal edit (warns again); dish and label photos arrive pre-filled and warn on a mismatch, "Uso de IA" unchanged; the food audit (converted copies included) opens editors and fixed foods leave it; past days with OFF foods keep their totals and those foods are editable; your foods and favourites come first, OFF when none match; a new OFF product becomes your food with no second copy on re-add or scan; the copy edits like a manual food and a mismatching OFF product warns; leave the app and come back.

### Follow-up (ADR-104, user feedback 2026-11-09)
- Checkpoint E17: items 6-10 approved, items 1-5 superseded. T-257 → T-258 → **👤 Checkpoint E17-fix** (7 items in `tasks/todo.md`) → T-256.

## Dependencies
- **PM step (done 2026-11-09):** `fitness-scientist` recorded the rule in `07` §4 "Food energy check" (§10 refs 152-157).
- T-251 needs the breakdown approved; T-252 needs T-251 merged and deployed (`food-photo` redeployed); T-253 needs T-252; T-254 needs T-253 merged (no build dependency beyond T-251); T-255 needs T-254 merged and deployed; T-256 needs Checkpoint E17 approved.
- Built pieces E17 relies on: `create_personal_food` / `update_personal_food` and `CreateFoodScreen` (US-47, ADR-53), the `food-photo` prefilled form (ADR-59, ADR-64, ADR-72, T-250), `search_user_food_ids` and the own-first search block (T-230), `food-search` / `food-barcode` and the OFF cache with `off_data` (ADR-43, ADR-46), favourites and saved comidas.

## Risks
- **A production data migration (T-254).** Repointing diary entries must not lose or duplicate one. Mitigation: snapshots untouched, per-user counts and per-day sums checked before and after with an abort on any difference, pgTAP with two users, `deploy-supabase` confirmed with data intact before Checkpoint E17.
- **Unique index on existing data (T-254).** The user may already have manual foods sharing a barcode (label scans). Mitigation: the planner checks for existing duplicates; the conversion reuses an existing own food with that barcode rather than failing.
- **Flag and "save anyway" in one write (T-251).** A trigger that clears the flag on an input change must still let "Guardar igualmente" set it in the same update. Mitigation: pgTAP for each input, for the combined write and for edits that touch no input.
- **AI schema change (T-251).** Making alcohol mandatory can turn valid responses into errors if the model omits it. Mitigation: the prompt states it explicitly per mode; Deno tests; a missing value is reported as the existing invalid-output error with "Reintentar", never a silent 0 (`07`: never omitted). If real use shows frequent omissions, a `fix(T-###)` revisits it.
- **US labels (`07`).** A converted US label may warn within tolerance rules of its own country; "Guardar igualmente" covers it and the copy explains fibre, alcohol and polyols.
- **Warning fatigue on OFF copies (T-255).** Many OFF products may fail; the warning is shown once per add and "Guardar igualmente" flags the copy so it is not asked again.

## Open questions
- **Q1 · RESOLVED (user, 2026-11-09; Q-E17-3 in the epic file, US-119 Gherkin amended): every reference moves to the personal copy.** Diary entries, favourites and saved comida items (hence default comidas), in the T-254 conversion and for every new add from T-255 on (diary, favourite star, comida item, barcode scan).
- **Q2 (non-blocking; assumption unless the user says otherwise): OFF results when my foods also match.** US-118 only says OFF is queried when none of my foods match. The planned behaviour keeps today's: OFF results are still appended below my foods when both match (the < 10 rule). E18 US-122 then makes my block appear at once.
- **Q3 (non-blocking, delegated to ux-reviewer in T-253): where the food audit is opened and what it is called.** Candidates: a row in Perfil → Preferencias or in the food search; recorded in `05` §10.
