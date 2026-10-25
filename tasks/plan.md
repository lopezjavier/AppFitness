# Implementation Plan: E7 · Food photos, defaults and cooking → `v0.7.0`

> **Status:** the 👤 checkpoints (E7-A, E7-B) were agreed with the user on 2026-10-24.

## Overview
Cut the typing out of food logging. A personal food can be created from a photo: an AI estimate of a dish, a nutrition label or a barcode photo, always through the editable manual-food form. A new day's Diary fills itself with the saved comidas assigned to that meal and weekday. A solid food can be logged as Crudo, A la plancha, Air fryer or Frito, adding the retained olive oil. The epic also takes the two change requests from Checkpoint E6-C (ADR-62): changing the unit of a manual food that nothing uses yet (US-52), and a saved comida that opens its details with "Añadir" and "Editar" (US-53).

Sources: `planning/epics/E7-food-photos-defaults-and-cooking.md` (US-48…US-53), `planning/01-phase1-scope.md` FR-2.15…FR-2.19, `planning/02-architecture.md` (Edge Function validation, single upload request), `planning/03-data-model.md` §3 (`food_log_entries`, `meal_template_items`, `food_preferences`, `meal_defaults`, `diary_default_applications`), §6 (`save_preferences`, `save_meal_template`, `save_meal_defaults`, `apply_meal_defaults`, `create_personal_food`, `update_personal_food`) and §7 (`food-photo`), `planning/04-business-rules.md` §2.2–§2.3, `planning/05-design-system.md` (`CookingMethodSelector`, `WeekdaySelector`, §6.2 "Cocina", §6.3, §8), `planning/08-environment-setup.md` (`GEMINI_API_KEY`, free-tier limits), ADR-59…ADR-62. Tasks: `tasks/todo.md` (T-104…T-111). Earlier plans are in the git history.

Out of scope (epic file and `01` §4): estimating a whole meal from one photo; storing, uploading or browsing photos (no Storage, no in-app gallery); an `ai` food source; the AI training assistant and adaptive calories (Phase 2); micronutrient targets; applying defaults at midnight, backfilling unopened days, notifications; re-selecting the cooking method of an existing entry.

## Architecture Decisions
- **Vertical slices, sequential, split only where a story is too large.** US-52 and US-53 are small and both fix food/comida editing, so they share one task (T-104). US-48 and US-49 share one Edge Function and one capture flow (the modes differ only in the prompt and the error text), so they are planned together and split backend (T-105) / UI (T-106). US-50 (T-107 / T-108) and US-51 (T-109 / T-110) each have a migration with several RPCs plus three or more UI surfaces, so each splits backend + domain, then UI.
- **Order.** T-104 first: it changes the comida rows and the manual-food form that later tasks build on (T-106 reuses the form; T-108 adds cooking chips to the comida details, which are the editor (ADR-63)). Cooking (US-50) comes before default comidas (US-51) because `apply_meal_defaults` copies the saved cooking method and computes the retained oil.
- **One AI path, no AI in the app (ADR-59).** `food-photo` (`verify_jwt = true`) holds the three fixed prompts, a fixed response schema and the normalisation (per-100 values, `1 ración` for a dish, negatives rejected or clamped, no kcal/macros = error). The app only downsizes and sends the image. `deno test` uses recorded Gemini fixtures (dish, label, barcode, unreadable, invalid input, provider error); CI never calls the network. Nothing is logged or stored. The Gemini key is the Supabase secret `GEMINI_API_KEY`, entered by the user.
- **Capture without permissions.** `PhotoCapture` in the domain; the Android implementation uses `TakePicture` + `FileProvider` and `PickVisualMedia`, one downscale/compress path (≤ 1600 px, JPEG ≈ 1.5 MB), the temporary file deleted on every outcome. No CameraX, no media or camera permission.
- **The AI result is a prefilled `CreateFoodScreen`.** The prefill is a `NewFoodDraft`; saving calls the existing `create_personal_food` and then opens the normal `AddFoodSheet`, exactly as a manual creation. The "Estimado por IA · revisa los valores" note disappears on the first edit (`05` §6.3).
- **Cooking is a snapshot, computed once (ADR-60).** One pure domain function (`retained_g = used_g × factor`, `+9 × retained_g` kcal, `+retained_g` g fat) and one SQL helper with the same test cases (domain tests and pgTAP share them), used by `apply_meal_defaults`. Entries store `cooking_method`, `cooking_oil_retained_g` and adjusted kcal/fat; the food never changes. A missing `food_preferences` row reads as 5/5/50 g. `save_preferences` gains the three oil parameters (defaulted, null = unchanged) so Preferencias keeps one "Guardar". Templates store the method per item, never oil.
- **Default comidas are applied by one idempotent RPC (ADR-61).** `apply_meal_defaults(p_log_date)` runs on the first load of **today's** Diary, before the entries are read; the marker table makes it safe to retry and prevents re-adding deleted entries. The client sends the device-local date; the server derives the ISO weekday. A failure never blocks the Diary (non-blocking "Reintentar").
- **Unit change is enforced by the server (ADR-62).** A new `update_personal_food` version accepts the base unit and refuses a change while any `food_log_entries` or `meal_template_items` row references the food (favourites do not count), inside the same locked transaction, so a stale form cannot slip through. The client disables the control with an explanation when it already knows the food is used.
- **Every schema change is a new migration** with pgTAP (RLS forced, cross-user isolation, cascades from `auth.users`, ADR-29); merged migrations are never edited. A changed RPC signature ships in the same PR as the Kotlin caller (T-097h lesson). `03` §6 is updated by each task.
- **Interaction rules (ADR-45, `05` §8).** One explicit "Guardar" on every edit surface (Cocina, "Comidas por defecto", the comida details, the AI-prefilled form); nothing applied on dismiss; discard confirmation on "Comidas por defecto". US-53 removes the US-47 exception in `05` §8: a row opens its details, editing starts from the details.
- **Restoration (ADR-40).** The unit edit draft, the comida details, the photo flow state (mode, source, comment, prefilled form; the image itself is not restored once discarded), the add-to-meal cooking choice, the Cocina draft, the comida method chips and the defaults editor draft (including an open assignment sheet) survive background and process death, each with `StateRestorationTester` + recreated-ViewModel tests.
- **Retrospective actions still in force.** E5: every task that edits or deletes records has a test with a record outside the screen's loaded range (T-104 reference on an old day, T-108 cooked entry edited on another day, T-109/T-110 deleting a comida or meal with assignments and entries on earlier days). Rules over user data state the assumed usage frequency (defaults: the Diary is opened at least once on the days the user wants them; unopened days stay empty, ADR-61). E6: keep host-test time in check (no new slow suites; reuse the optimised test setup).

## Task List

Checkpoint rule: every user-testable item of the epic is collected first, then grouped by journey into checkpoints of 5–7 items, each placed right after the last task of its journey. An item is one coherent thing to try on the phone; "leave the app and come back" is in every checkpoint but not counted. E7 yields 13 feature items in two journeys (create and fix foods: 7; cook and plan meals: 6) plus the closing multi-day regression (5). Full item lists are in `tasks/todo.md`.

### Phase 1: Create and fix foods (photos, unit, comida details)
- [x] T-104: Unit of an unused manual food and comida details (US-52, US-53) — UI + backend — M
- [ ] T-105: `food-photo` Edge Function: dish, label and barcode (US-48/US-49 backend) — backend — M
- [ ] T-106: Add a food from a photo (US-48/US-49 UI) — UI — L
  - **Checkpoint E7-A 👤 Create and fix foods (7 items):** unit change of an unused food; comida details; photo action, modes and sources; dish estimate; label photo; barcode photo; failures and offline. Why here: the whole "add a new food" journey is complete, and cooking and defaults do not build on the photo flow, so feedback on the form and the comida details lands before T-108 adds cooking chips to them.

### Phase 2: Cook and plan meals (cooking methods, default comidas)
- [x] T-107: Cooking methods backend (US-50 backend) — backend — M
- [ ] T-108: Cooking selector, "Cocina" and cooking in comidas (US-50 UI) — UI — L
- [ ] T-109: Default comidas backend (US-51 backend) — backend — M
- [ ] T-110: "Comidas por defecto" and a new day's Diary (US-51 UI) — UI — L
  - **Checkpoint E7-B 👤 Cook and plan your meals (6 items):** cooking in the add-to-meal sheet; "Cocina" amounts; cooking in comidas; the defaults editor; a new day starts with its defaults; changes and deletions after applying. Why here: it is the last implementation task, so it is also the final regression; cooking alone gives 3 items, below the minimum, and defaults copy the cooking methods, so both form one "plan what I eat" journey. The new-day item needs the next morning, so the user configures the defaults and checks the Diary the following day.

### Phase 3: Closure
- [ ] T-111: E7 closure — PM — S

Every checkpoint ends with "leave the app and come back: the same tab, screen and draft are restored". Internal milestones are not 👤 checkpoints:
- pgTAP + `deno test` + lint + `testLocalDebugUnitTest` green per task;
- `deploy-supabase` green after each migration or function (T-104, T-105, T-107, T-109);
- `GEMINI_API_KEY` set by the user in Supabase secrets (name verified, value never seen) before E7-A;
- existing entries and template items backfilled to `raw` after T-107.

## Dependencies

```
v0.6.0 ─► T-104 ─► T-105 ─► T-106 ─► [E7-A 👤]
       ─► T-107 ─► T-108 ─► T-109 ─► T-110 ─► [E7-B 👤]
       ─► T-111 (closure, no checkpoint) ─► v0.7.0
```
- Strictly sequential; each checkpoint gates the next task.
- T-106 reuses the manual-food form T-104 touches (unit control) and needs T-105 deployed with the secret set.
- T-108 needs T-107's columns, `save_preferences`/`save_meal_template` versions and the domain function; it extends T-104's comida details with the cooking impact.
- T-109's `apply_meal_defaults` uses T-107's cooking SQL helper and `food_preferences`; T-110 needs T-109 and the saved comidas with methods (T-108).

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Gemini free-tier quota or model changes break the photo flow | High | `429`/`502`/`503` mapped to "Reintentar" + "Crear manualmente"; fixed response schema validated in the function; recorded fixtures in `deno test`; no billing account (ADR-59) |
| A prompt injection through the comment or the image changes the output | Medium | Comment dish-only, ≤ 200 chars, passed as data; the response must match the schema (extra fields dropped, negatives rejected); the user always reviews the prefilled form |
| A photo, comment or provider body leaks into logs or storage | High | Function never logs them (reviewed in the PR); temporary file deleted on every outcome (host test per outcome); no Storage bucket |
| Large images exceed Edge Function limits or are slow on mobile data | Medium | Downscale ≤ 1600 px / JPEG ≈ 1.5 MB before upload; `413` handled; single request, no background retry |
| Cooking kcal/fat diverge between the client, the SQL helper and the defaults RPC | High | One domain function and one SQL helper with shared test cases (`04` §2.2 examples) |
| The `save_preferences` / `save_meal_template` / `update_personal_food` signature changes break the installed app | Medium | New parameters defaulted; migration and Kotlin caller in the same PR; old signature dropped explicitly |
| `apply_meal_defaults` adds a day twice or brings back deleted entries | High | Marker table with `on conflict do nothing` in the same transaction; pgTAP for re-run, delete-then-reopen, a day with entries and a past date |
| The device-local date and the server weekday disagree around midnight or across time zones | Medium | The client sends the local date; the server derives the ISO weekday from that date only (never from `now()`); domain test for the weekday helper |
| The unit is changed while another device logs the food | Low | Check inside the locked RPC transaction; pgTAP proves the refusal; host test for the error message |
| L UI tasks (T-106, T-108, T-110) overflow one dev run | Medium | The planner slices them (domain/ViewModel, then screens), one dev run per slice |

## Open Questions
None blocking: E7 Q1–Q4 were answered by the user on 2026-10-24 (`planning/09-open-questions.md`, ADR-58…ADR-61) and ADR-62 covers US-52/US-53.

**Assumptions (non-blocking; applied unless the user objects at the checkpoints):**
- Copying a meal or a day in the Diary (T-060) copies each entry's snapshot as today, including its cooking method and retained oil (no recomputation with the current "Cocina" amounts) (T-108, E7-B).
- Saving today's meal as a comida stores each entry's method; it never stores the oil (ADR-60) (T-108, E7-B).
- A food created from a photo with base unit `ml` behaves like any drink: no cooking selector (T-106/T-108).
- The diary entry row shows the cooking method next to the amount when it is not Crudo (wording and placement by ux-reviewer) (T-108, E7-B).
- The unit hint for a used food explains that it is already in the diary or in a comida, without listing where (wording by ux-reviewer) (T-104, E7-A).
- Defaults usage frequency: the Diary is opened at least once on the days the user wants their defaults; a day not opened stays empty (ADR-61) (T-110, E7-B).
- ux-reviewer words the stale-unit refusal, the photo hints and the "Comidas por defecto" note within `05` §6.3, and the user sees them at the checkpoints (feedback rule).
