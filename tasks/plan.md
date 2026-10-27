# Implementation Plan: E9 · Finalization → `v1.0.0`

> **Status:** broken down on 2026-10-26 with the three checkpoints agreed with the user (E9-A, E9-B, E9-C); re-sized the same day at the user's request (16 → 13 tasks, see "Sizing"). E9 Q1–Q3 were answered by the user on 2026-10-26 (ADR-70); no task is blocked. Breakdown approved by the user.

## Overview
Close Phase 1. US-56 turns the UX rulebook adopted in Wave 0 (ADR-69, `05-design-system.md` §8–§10) into designsystem templates and CI guards, then migrates every screen to them domain by domain, closing the deferred UI-refinement table on the way. US-45 adds "Acerca de" with the version, the third-party credits and the open-source licences. US-46 sweeps states, accessibility, font scaling, tablet and rotation across the migrated app. The epic ends with the `v1.0.0` tag and the Phase 1 GitHub Release (notes only).

Sources: `planning/epics/E9-finalization.md` (US-45, US-46, US-56, UI-refinement table), `planning/ux-audit-2026-10.md` (§5–§7 findings, §9 components, §10 guards G1–G10, §13 waves, §14 swipe and haptics), `planning/05-design-system.md` §5–§10, `planning/01-phase1-scope.md` FR-5.4 and FR-6, `planning/03-data-model.md` (`meal_slots`, `meal_templates`, `routines`, `routine_folders`), ADR-13, ADR-24, ADR-40, ADR-41, ADR-45, ADR-65, ADR-67, ADR-69. Tasks: `tasks/todo.md` (T-118…T-130, with the item → task table). Earlier plans are in the git history.

Out of scope (epic, `01` §4, ADR-66, ADR-67, ADR-69): new features; the "Entreno en curso" bar (dropped); typography and colour changes (E9 table item 3); a signed release build, R8 or keystore; Phase 2 items.

## Sizing (user feedback, 2026-10-26)
Every task pays a fixed cost (planner, ux-reviewer, developers, PR, review), so small tasks were merged. First estimate (16 tasks): 1 S (About), 3 M (screen templates, Peso, meals editor, motion/onboarding close to S), the rest L, plus the S closure. Merged:
- screen templates (M) into the guards/feedback foundation → **T-118** (L); the per-screen `SnackbarHost`/offline-banner removals moved to the domain tasks, which rebuild those screens on `AppScreen` anyway;
- motion, tooltips and onboarding (M, close to S) into the wording sweep → **T-128** (L, wave 3, same checkpoint);
- "Acerca de" (S, US-45) into the US-46 polish sweep → **T-129** (L, both are E9-C).

Not merged: the other tasks are already M or L on their own, and joining two different domains would give an unreviewable PR (for example meals editor M + Preferencias hub L ≈ 45 files). Peso (T-123) and the meals editor (T-126) stay M; only the closure (T-130) stays S.

## Architecture Decisions
- **Foundations first, then migration by domain (US-56 notes, audit §13).** Three foundation tasks (T-118…T-120) are the allowed exception to "one task per story". Each foundation task ends with a sweep: cross-cutting, mechanical copies (raw vibrations, the designsystem pickers, search-field workarounds, swipe and reorder callers) are migrated in the task; each screen-specific copy (message hosts, duplicate offline banners, sheets, cards) is listed in the task with its owning wave-2 task (R-39), so the sweep is complete within the epic.
- **Guards with a shrinking warning list.** `check-ux-conventions.sh` (G1–G10) has a per-guard, per-path warning list: unmigrated paths warn, everything else fails. T-118 makes G10 fail at once; each wave-2 task removes its domain from the list (incl. G3/G4 once its screens are on `AppScreen`); T-128 empties it (epic DoD: every guard a failure).
- **Screens own their bar, gradually.** `AppScreen`/`AppEditorScreen` (T-118) take over the top bar per route through a shell switch, so unmigrated routes look unchanged until their domain task; T-128 removes the switch.
- **Object rows and `FoodSheet` are built in their first domain task**, not in the foundations: `FoodEntryRow` and `FoodSheet` (edit context) in T-121, its add and comida contexts in T-122, `MeasurementRow` in T-123, `ExerciseRow` in T-124, `WorkoutRow` in T-125. Generic rows, cards and controls are foundations (T-120).
- **Server changes only where a rule needs them.** T-122: `save_meal_as_comida` raises on unknown entries (E8 review note). T-124: routine names unique on `lower(btrim(name))` (item 21, R-15), "Archivar" → immediate "Eliminar" (D4), and the global `training_preferences.auto_update_routine` replacing the per-routine column (ADR-70). T-126: meal names unique and one atomic save of the meal structure with removals (R-11, amends ADR-41). New migrations only, same error codes, each with pgTAP and `deploy-supabase`.
- **Preferencias hub without data changes (D9, amends ADR-24).** Same tables and RPCs; each section editor saves only its own fields (T-127).
- **Haptics split.** UI feedback goes through `AppHaptics` (one vibration per action, R-29); the end-of-rest alert keeps the `Haptics` port with a longer pattern (T-125, item 15).
- **Screenshot tests for templates only (D14).** Roborazzi is added in T-118 (stable versions only) and used by T-118…T-120; it is the guard for the stray sheet line (items 27, 38).
- **Carry-overs.** T-112 cooking-fat caveat (ADR-65): editing the amount of a cooked entry keeps the stored fat; T-121 keeps it and the user reviews it at E9-A. E8 non-blocking review notes go to their owning tasks: `DiaryViewModel` state holder (T-121), `save_meal_as_comida` (T-122), radar partial failure, muscle-sheet lookup and the 1A bucket (T-125). The E7 backlog request to simplify Entreno → Historial (calendar directly, "Añadir entrenamiento" in the day details) goes to T-125.

## Task List

Checkpoint rule for E9: the three checkpoints were agreed with the user before the breakdown (2026-10-26). Every user-testable item was collected per checkpoint and grouped by journey into 7; "leave the app and come back" is listed under each but not counted. Full item lists in `tasks/todo.md`.

| Task | Wave | Flow | Size | Expected agent runs |
|---|---|---|---|---|
| T-118: UX guards, feedback layer and screen templates | 1 | UI + core | L (merged) | 7 |
| T-119: Sheet archetypes, Name sheet and field upgrades | 1 | UI | L | 6 |
| T-120: Lists and cards (swipe/reorder v2, rows, `MetricCard`, `TimeRangeSelector`) | 1 | UI + core | L | 7 |
| T-121: Diario migration (layout, `FoodEntryRow`, `FoodSheet`, day picker) | 2 | UI + core | L | 7 |
| T-122: Food search, add flow and saved comidas migration | 2 | backend + core + UI | L | 8 |
| T-123: Peso migration | 2 | UI + core | M | 7 |
| T-124: Routines, builder and exercises migration | 2 | backend + core + UI | L | 8 |
| T-125: Live session, history and statistics migration | 2 | UI + core | L | 7 |
| T-126: Comidas del día editor and meal-structure backend | 2 | backend + core + UI | M | 8 |
| T-127: Perfil root and Preferencias hub | 2 | UI + core | L | 7 |
| T-128: Wording, motion and onboarding; every guard a failure | 3 | UI | L (merged) | 7 |
| T-129: Version 1.0 quality: About and licences, states, accessibility and tablet (US-45 + US-46) | — | UI + core | L (merged) | 8 |
| T-130: E9 closure → `v1.0.0` | — | PM | S | PM only |

Agent runs for UI tasks: planner, ux-reviewer (plan), developers, pr-creator, pr-reviewer ∥ ux-reviewer (diff).

### Phase 1: Foundations (wave 1)
- [x] T-118: UX guards, feedback layer and screen templates — UI + core — L
- [x] T-119: Sheet archetypes, Name sheet and field upgrades — UI — L
- [x] T-120: Lists and cards — UI + core — L

### Phase 2: Food domain (wave 2.1)
- [ ] T-121: Diario migration — UI + core — L
- [x] T-122: Food search, add flow and saved comidas — backend + core + UI — L
  - **Checkpoint E9-A 👤 Log food the new way (7 items):** Diario layout; a logged food's sheet (incl. the T-112 caveat); swipe and delete; day picker; adding with the new search and sheet; saved comidas details and editor; messages and offline. Why here: the first domain on every template, before the other domains copy the same patterns, so feedback can still change the templates.

### Phase 3: Remaining domains, wording and motion (waves 2.2–3)
- [x] T-123: Peso migration — UI + core — M
- [x] T-124: Routines, builder and routine details — backend + core + UI — L
- [x] T-124b: Exercise picker, detail and exercise sheets (split off T-124) — core + UI — M
- [x] T-125: Live session migration — UI + core — M
- [x] T-125b: History and workout detail — UI + core — L
- [ ] T-125c: Statistics and body map (item B) — UI + core — L
- [ ] T-126: Comidas del día editor and meal-structure backend — backend + core + UI — M
- [ ] T-127: Perfil root and Preferencias hub — UI + core — L
- [ ] T-128: Wording, motion and onboarding; every guard a failure — UI — L
  - **Checkpoint E9-B 👤 Every screen, one way (7 items):** Peso; routines; live session; history and statistics; Perfil and Preferencias; Comidas del día; wording and motion. Why here: every domain, the wording and the motion are migrated, before US-45/US-46 build their final checks on them.

### Phase 4: Version 1.0 quality (US-45, US-46)
- [ ] T-129: Version 1.0 quality: About and licences, states, accessibility and tablet — UI + core — L
  - **Checkpoint E9-C 👤 Version 1.0 on your phone and tablet (7 items):** "Acerca de"; licences; states on every screen; TalkBack; font 130 %; tablet and rotation; final regression of a full day. Why here: after the last implementation task, so it is also the final regression of E9 and Phase 1; its approval releases T-130.

### Phase 5: Closure
- [ ] T-130: E9 closure → `v1.0.0` — PM — S (no checkpoint; E9-C's approval releases it)

Internal milestones (not 👤 checkpoints):
- lint (incl. `check-design-tokens.sh` and, from T-118, `check-ux-conventions.sh`) + `testLocalDebugUnitTest` green per task; pgTAP green for T-122, T-124, T-126;
- `deploy-supabase` green after T-122 (before E9-A) and after T-124 and T-126 (before E9-B), data intact;
- the guard warning list shrinks with each wave-2 task and is empty after T-128;
- `:androidApp:assembleProdDebug` green with the licences plugin (T-129).

## Dependencies

```
v0.8.0 ─► T-118 ─► T-119 ─► T-120 ─► T-121 ─► T-122 ─► [E9-A 👤]
        ─► T-123 ─► T-124 ─► T-125 ─► T-125b ─► T-125c ─► T-126 ─► T-127 ─► T-128 ─► [E9-B 👤]
        ─► T-129 ─► [E9-C 👤] ─► T-130 (closure) ─► v1.0.0
```
- Strictly sequential (one active task at a time); each foundation needs the previous one (top bar and `AppActionSheet` → sheets → fields in the Name sheet → rows and swipe on the haptics).
- T-125c needs the user's body-map choice (item B) before it starts; E9 Q1–Q3 are resolved.
- T-128 needs every domain migrated (it removes the shell switch and empties the warning list).

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Fewer, larger tasks: most are L and some exceed ~25 files (T-118, T-124, T-125, T-127, T-128, T-129) | Medium | Most changes are mechanical; developers run in two slices where named (T-128, T-129); each size note names a planner-decided fallback split (T-124b, T-125b, two runs for T-127) instead of splitting up front |
| A foundation template turns out wrong once a domain uses it | High | E9-A comes right after the first domain, before the other five copy the patterns; template fixes then sweep only two domains |
| Optimistic swipe removal desynchronises the list on server failure | Medium | One shared restore path in T-120, ViewModel tests per caller, the failure message from §9.1 |
| New uniqueness constraints fail on existing duplicate names in production | Low | Routines verified clean (E9 Q3); the `meal_slots` migration pre-checks and fails loudly naming any duplicate; `deploy-supabase` checked with data intact before E9-B |
| Dropping `routines.auto_update_routine` (ADR-70) breaks an installed client that still sends it | Low | Column drop and the `save_routine` change ship with their Kotlin caller in T-124; the APK is reinstalled at each checkpoint |
| Splitting Preferencias into sections changes what one "Guardar" writes | Medium | Same RPCs; each section saves only its fields; tests per section; the user checks at E9-B |
| `oss-licenses-plugin` incompatible with the current AGP/KMP setup | Medium | Checked first in T-129 part A with stable versions; if it cannot work, the PM asks the user before any alternative library (the epic names this plugin) |
| Semantic haptics are silent on older Android versions | Low | Per-API fallbacks in `AppHaptics` (§9.2); the user's Pixel 9 supports all types |
| Roborazzi adds host-test time (E6 retro) | Low | Screenshot tests for templates only (D14), not per screen |
| The guard script yields false positives and blocks unrelated PRs | Medium | Fixture tests per guard in T-118; the warning list keeps unmigrated domains non-blocking |
| Two-column and 130 % font fixes found late (T-129) cascade into many screens | Medium | Templates cap widths and set title levels from T-118; small fixes stay in T-129, large ones become a `fix(T-###)` before E9-C |

## Open Questions

**Resolved (user, 2026-10-26; `planning/09-open-questions.md`):**
- **E9 Q1 · End-of-rest sound (item 16).** No preference and no file: the Android system alarm sound (`RingtoneManager.TYPE_ALARM`, falling back to the notification sound), no bundled asset, so nothing to credit; still silent when muted. T-125.
- **E9 Q2 · E6-B Q1, "Actualizar automáticamente".** Option A, ADR-70: one global setting in `training_preferences`, off by default; the per-routine switch and `routines.auto_update_routine` go away (migration, builder and finish flow in T-124, temporary switch in the current Preferencias; the "Entrenamiento" section of the hub in T-127).
- **E9 Q3 · Existing duplicate names.** Verified read-only in production: no case-insensitive trimmed duplicates in `routines` or `meal_templates`, so T-124 adds the routine-name unique index directly, with no cleanup or rename. `meal_slots` was not checked, so T-126's migration runs a pre-check that raises naming any duplicate (never renames), with a pgTAP case.

No task is blocked.

**Assumptions (non-blocking; applied unless the user objects at the checkpoint):**
- Routine card → a light routine details view (exercises, planned sets, last done, "Empezar", ✎ and 🗑 in the top bar), because R-17 (binding since ADR-69) requires a row tap to open details (audit NAV-5).
- Planned placeholders (item 36) are told apart by a lighter font weight, keeping AA contrast (`05` §7), not by `ghostData`.
- Item B (body map): ux-reviewer proposes 2–3 options with their complexity in T-125's plan pass and the PM asks the user to pick before any rebuild; with no rebuild, only the label fix (item A) ships.
- `TimeRangeSelector` uses one string set "1S 1M 3M 6M 1A" plus "Todo", each screen showing the subset it already offers (the E8 Q2 frames stay).
- US-46 "Progress" means Peso and "Estadísticas" (no Progreso tab, ADR-37).
- The Spanish copy of new strings (credits, hints, duplicate messages) is worded by ux-reviewer within the glossary, and the user sees it at the checkpoints.
