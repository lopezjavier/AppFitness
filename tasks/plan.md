# Implementation Plan: E4 · Food diary → `v0.4.0`

## Overview
Log what you eat in fewer than 3 taps (search, scanner or saved meal) and see the calories and macros consumed against the target at all times. The epic builds our own food catalog cached from Open Food Facts (base unit g or ml, named portions), the Diario tab with configurable meals and a dashboard, text search with recents / most eaten / favourites, barcode scanning and manual foods, editing, copying and view modes, meal templates, and the diary history with weekly and monthly summaries.

Sources: `planning/epics/E4-food-diary.md` (US-16…US-24), `planning/01-phase1-scope.md` FR-2, `planning/03-data-model.md` §1/§3/§6/§7, `planning/04-business-rules.md` §2 and §5, `planning/05-design-system.md` (§5 components: `ProgressRing`, `MacroProgressBar`, `ViewModeSelector`, `PortionSelector`, `FavouriteToggle`, `MonthCalendarGrid`, `DiaryHistoryRow`), ADR-01, ADR-06, ADR-07, ADR-17, ADR-29, ADR-32, ADR-33, ADR-34, ADR-36, ADR-40. Tasks: `tasks/todo.md` (T-055…T-063). Earlier plans are in the git history.

Out of scope (epic file): user-uploaded food images, micronutrient targets or totals, composite recipes, meal planning, data export; water intake (`01` §4). Editing or deleting a created personal food is not in the stories.

## Architecture Decisions
- **One backend foundation task (T-055).** `food_log_entries` references both `foods` and `meal_slots`, so the US-16 migration also creates `food_portions` (US-22) and `meal_slots` (US-23) with the default-meal seeding. Later tables come with the story that uses them: `favorite_foods` + `most_eaten_foods` (T-057), `meal_templates` + items (T-061), `food_diary_summary` (T-062), and an optional `save_meal_slots` RPC (T-059). At most one migration per task; never edit a merged one.
- **Default meals for existing users.** Seeding runs when `onboarding_completed` becomes true (idempotent trigger) and the migration backfills every already-onboarded user, so the E2/E3 users get Desayuno/Comida/Merienda/Cena.
- **Open Food Facts only through Edge Functions (ADR-06).** `_shared/off-client.ts` normalizes (base unit from `nutrition_data_per`, kJ → kcal, servings → `food_portions`); `off` rows are written with the secret key (ADR-17), reusing the `delete-account` key lookup (`SUPABASE_SECRET_KEYS`, legacy fallback). `deno test` uses recorded fixtures, never the network.
- **Snapshots in the domain.** `ScaleNutrition` (`kcal_100 × amount / 100`, 0.1) and `ResolvePortion` live in `shared` and are the only place entry values are computed (log, edit, copy, templates). Diary totals derive from one `StateFlow` (no second query); gram targets come from `CalculateMacroTargets` (T-037).
- **Batch writes.** Adding a saved comida is one batch insert of `food_log_entries` (no quick add, ADR-42).
- **Platform port for the scanner.** `BarcodeScanner` (domain) + ML Kit `GmsBarcodeScanning` in `androidMain` (no camera permission, ADR-07); a fake in host tests.
- **Local preferences.** None for the Diary (the view-mode preference was removed in T-066); everything else is server data.
- **Restoration (ADR-40).** Visible day, search query and tab, sheets and every form draft survive background and process death; each UI task ships `StateRestorationTester` + recreated-ViewModel tests.
- **Shared components.** `ProgressRing`/`MacroProgressBar` gain `Double`/percent inputs (PR #24 note) in T-056; before changing `NumberInput`, `AppBottomSheet`, `SegmentedSelector` or `ReorderableColumn`, check every caller (T-040 lesson). New design-system components are built inside the first task that needs them, with a catalog entry.

## Task List

### Phase 1: Log food by search and see the day
- [ ] T-055: Food catalog backend: schema and the OFF proxy (US-16 + US-22 data + US-23 table) — backend — L
- [ ] T-056: Daily diary by meal and the calorie and macro dashboard (US-19 day + US-20) — UI — M–L
- [ ] T-057: Food text search and logging into a meal (US-17 + US-22 logging) — UI + backend — L
  - **Checkpoint E4-A 👤 Log by search:** the Diario day with ring and bars; search and add real foods with grams, a portion and a drink in ml; recents, "Más comidos", "Favoritos"; excess and "Te quedan"; leave the app and come back.

### Phase 2: Scanner, manual foods, meal structure, correct and reuse the diary
- [ ] T-058: Barcode scanning and manual food creation (US-18 + US-22 named portion) — UI — M–L
- [ ] T-059: Custom meal structure (US-23) — UI (+ optional RPC) — M (Q1 resolved)
- [ ] T-060: Diary corrections, copying and view modes (US-19 rest + US-20 immediate update) — UI — L (Q2, Q3 resolved)
  - **Checkpoint E4-B 👤:** scan real products and a drink; unknown code → "Crear alimento" with the code, rescan finds it; validation; rename, add, reorder, disable/re-enable meals; edit, move, swipe-delete + "Deshacer", copy foods/meal to another meal or day, "Repetir comida de ayer", Detalle/Lista/Resumen kept; leave the app and come back.

### Phase 3: Templates, history and closure
- [ ] T-061: Meal templates (US-21) — UI + backend — L
- [x] T-062: Diary history and summaries (US-24) — UI + backend — M
- [ ] T-068: Full OFF product data and micronutrients in the food details (US-25, ADR-43) — backend + UI — M
- [ ] T-063: E4 closure — PM — S
  - **Checkpoint E4-C 👤:** save a section as a comida, duplicate name, "Comidas" tab, edit (overwrite or save as new), rename and delete; history calendar, open a day, day / week / month summaries, "Sin registros"; final regression of one real day end to end; leave the app and come back; approval → `v0.4.0` and the retrospective.

Three checkpoints by user decision (2026-10-20), overriding the one-per-functionality default for E4. Every checkpoint ends with "leave the app and come back: the same tab, screen and draft are restored". Internal milestones (not 👤): pgTAP + `deno test` + lint + `testLocalDebugUnitTest` green per task; `deploy-supabase` green after each migration (T-055, T-057, T-061, T-062, T-068, and T-058/T-059 if they add one).

## Dependencies

```
v0.3.0 ─► T-055 ─► T-056 ─► T-057 ─► [E4-A 👤] ─► T-058 ─► T-059 ─► T-060 ─► [E4-B 👤]
      ─► T-061 ─► T-062 ─► T-068 ─► T-063 ─► [E4-C 👤] ─► v0.4.0
```
- Strictly sequential: E4-A gates T-058 and E4-B gates T-061; between checkpoints tasks run back to back.
- Meal structure (T-059) comes before copying and templates so their meal targets are built on the final slot rules.
- Q1–Q3 are resolved (2026-10-20); no task waits on an open question.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| OFF rate limits (~10 search requests/min per IP) and Spanish search quality | High | Cache-first (OFF only below 10 local results), 400 ms debounce, 8 s timeout with a JSON error; fixtures in CI; real searches tried at E4-A. |
| `on delete restrict` on `food_log_entries.meal_slot_id`/`food_id` blocks the account-deletion cascade (ADR-29) | High | pgTAP deletes a user with slots, foods and entries in T-055; switch to `no action` if needed and update `03` §3. |
| Existing users have no meal slots | High | Backfill in the T-055 migration + idempotent seeding trigger; verified in production after the deploy. |
| `unique (user_id, position)` breaks reorder/insert saved row by row | Medium | Atomic save (RPC or deferrable constraint) in T-059, with pgTAP. |
| Edge Function secret key naming (new `sb_secret_` vs legacy env) | Medium | Reuse the `delete-account` lookup; if production lacks the key the PM asks the user (secrets are theirs). |
| ML Kit scanner module download on first use / devices without Play services | Medium | Port + fake for tests; an error result shows a message, never a crash; tried on the user's phones at E4-B. |
| Local date vs UTC (`log_date`) around midnight | Medium | `log_date` from the device's local date via the injected `Clock`; tests at 23:59/00:00. |
| Changing `ProgressRing`/`MacroProgressBar` signatures breaks catalog or Profile callers | Low | Check every caller; overloads keep the `Int` API where used. |
| L tasks (T-055, T-057, T-060, T-061) overflow one dev run | Medium | Planner slices them (named in each task), one dev run per slice. |

## Open Questions
- **Q1 · RESOLVED 2026-10-20 (user): only entries on the day being viewed block disabling; past days are not edited.** *(was BLOCKING)* — **Q1 (T-059) · Which entries prevent disabling a meal?** US-23 says disabling a meal "with entries" is not allowed. Does that mean (a) entries on **any** day (so a meal ever used can never be hidden, e.g. you could never hide "Merienda" once you logged a snack), or (b) entries on the **day being viewed** only (the meal is hidden from then on; past days that have entries in it still show it)?
- **Q2 · RESOLVED 2026-10-20 (user): the proposal below is accepted.** *(was BLOCKING)* — **Q2 (T-060) · "Detalle" vs "Lista".** "Resumen" is defined (meal name, kcal and macros only); the difference between "Detalle" and "Lista" is not. Proposal: "Detalle" = each entry with its amount, kcal and P/C/F grams; "Lista" = one compact line per entry with name, amount and kcal only. Confirm or describe "Lista".
- **Q3 · RESOLVED 2026-10-20 (user): option (b), copying to future days is allowed and the Diary stays capped at today.** *(was BLOCKING)* — **Q3 (T-060) · Copying to future days.** US-19 copies a meal "to tomorrow's Comida", but the Diary's right arrow stops at today. Options: (a) allow copying to any past day, today and tomorrow, and let the Diary go one day ahead (to see and edit tomorrow); (b) allow copying to future days but keep the Diary at today (tomorrow's copy shows up when tomorrow comes); (c) only past days and today (the "tomorrow" scenario becomes "yesterday → today", which "Repetir comida de ayer" already covers).
- **Assumptions (non-blocking; the planner applies them unless the user objects at E4-A/E4-C):**
  - Default meal when none is preselected: the enabled default slot matching the time rule (before 11:00 Desayuno, 11–16 Comida, 16–20 Merienda, later Cena); if that slot is disabled, the first enabled slot of the day.
  - "Crear alimento" from an empty search prefills the name with the search text; a food created from a scan stores the barcode, so rescanning finds it.
  - No quick-add chips (user decision 2026-10-21, ADR-42): saved comidas are added only from the "Comidas" tab of the search.
  - History summaries compare against the current target (no target history is stored).
  - Where the meal settings and "Historial" are opened from the Diary is a layout choice for ux-reviewer (e.g. the Diary's menu).
