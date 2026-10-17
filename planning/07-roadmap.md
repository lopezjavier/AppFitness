# Phase 1 roadmap

## 1. Epics overview

| Epic | Name | Stories | Dominant flow | Delivery |
|---|---|---|---|---|
| E0 | Project setup | — (setup tasks) | Manual with the user | Repo ready, `CLAUDE.md`, agents, Supabase deploy, OAuth |
| E1 | Infrastructure and skeleton | US-01 … US-04 | Backend + UI | `v0.1.0` — navigable APK with a WIP shell |
| E2 | Authentication and profile | US-05 … US-08 | Full UI | `v0.2.0` — login, onboarding, goals |
| E3 | Training core | US-09 … US-15 | Full UI + backend | `v0.3.0` — routines, live session, catalog assets, progress charts |
| E4 | Food diary | US-16 … US-24 | Full UI + backend | `v0.4.0` — meal logging, portions and diary history |
| E5 | Weight tracking | US-25 … US-27 | Full UI | `v0.5.0` — weight, Health Connect, chart |
| E6 | Training depth | US-28 … US-38 | Full UI + backend | `v0.6.0` — set types, RPE, metrics, PRs, routine management |
| E7 | Training insights | US-39 … US-44 | Full UI | `v0.7.0` — advanced stats, muscle distribution, reports |
| E8 | Finalization | US-45 … US-46 + UI refinement | Full UI | `v1.0.0` — About, polish, signed release and UI refinement |

### 1.1 UI refinement (inside E8)
The UI refinement is not a separate placeholder epic: it is part of **E8 · Finalization** (FR-6). It is a presentation-only pass over the screens of E2…E7; every 👤 checkpoint that defers a UI detail appends it to the table in `epics/E8-finalization.md`.

## 2. Dependencies

```
E0 ──► E1 ──► E2 ──┬──► E3 (training core) ──► E6 (training depth) ──► E7 (training insights)
                   ├──► E4 (food diary)
                   └──► E5 (weight tracking)
E3, E4, E5, E6, E7 ─────────────────────────────────────────────────► E8 (finalization)
```
- E2 is a prerequisite for every feature: without an authenticated user and goals there is no Diary.
- E3, E4 and E5 are independent of each other; the order is **Training → Food → Weight** (the user wants the gym experience first).
- E6 (training depth) depends on E3 (forward-compatible model, ADR-30); E7 (training insights) depends on E3 (basic progress charts, US-15) and E6 (PRs and warm-up inclusion).
- E8 (finalization) depends on E1…E7: its polish pass and third-party credits cover all domains and it ships `v1.0.0`.

## 3. User stories

| ID | Epic | Title | Flow |
|---|---|---|---|
| US-01 | E1 | Base KMP project with flavors, quality tooling and Android CI | Backend/infra |
| US-02 | E1 | Supabase baseline: profiles, RLS, health check, CI and keep-alive | Backend/infra |
| US-03 | E1 | Kinetic Dark design system in Compose | UI |
| US-04 | E1 | Navigable shell with 3 tabs + Profile, contextual FAB and adaptive layout | UI |
| US-05 | E2 | Sign in with Google | UI + backend |
| US-06 | E2 | Biometric onboarding | UI + backend |
| US-07 | E2 | Energy strategy and calorie target | UI + backend |
| US-08 | E2 | Macro split with auto-balancing sliders | UI + backend |
| US-09 | E3 | Exercise catalog | UI + backend |
| US-10 | E3 | Routine builder | UI + backend |
| US-11 | E3 | Live workout session | UI + backend |
| US-12 | E3 | Previous performance (ghost data) | UI + backend |
| US-13 | E3 | Non-blocking rest timer | UI |
| US-14 | E3 | Session history | UI |
| US-15 | E3 | Strength progress charts | UI + backend |
| US-16 | E4 | Food catalog and Open Food Facts proxy | Backend |
| US-17 | E4 | Food text search and logging into a meal | UI |
| US-18 | E4 | Barcode scanning and manual food creation | UI |
| US-19 | E4 | Daily diary by meal: view, edit and delete | UI |
| US-20 | E4 | Calorie and macro dashboard | UI |
| US-21 | E4 | Meal templates | UI + backend |
| US-22 | E4 | Food portions and measures | UI + backend |
| US-23 | E4 | Custom meal structure | UI + backend |
| US-24 | E4 | Diary history and summaries | UI + backend |
| US-25 | E5 | Manual weight logging | UI + backend |
| US-26 | E5 | Manual Health Connect sync | UI |
| US-27 | E5 | Metrics panel and trend chart | UI |
| US-28 | E6 | Exercise detail (history and personal records) | UI |
| US-29 | E6 | Set types (warm-up, failure, drop) | UI + backend |
| US-30 | E6 | RPE per set | UI + backend |
| US-31 | E6 | Exercise metric types | UI + backend |
| US-32 | E6 | Volume options | UI + backend |
| US-33 | E6 | Warm-up and plate calculators | UI + backend |
| US-34 | E6 | Personal-record catalogue and live PR | UI + backend |
| US-35 | E6 | Routine folders and next workout | UI + backend |
| US-36 | E6 | Routine tracking and "Update Routine" | UI + backend |
| US-37 | E6 | Workout detail, calendar and editing | UI |
| US-38 | E6 | Session controls | UI |
| US-39 | E7 | Exercise charts: records and non-weight metrics | UI |
| US-40 | E7 | Advanced statistics | UI + backend |
| US-41 | E7 | Muscle distribution (radar and body heatmap) | UI + backend |
| US-42 | E7 | Monthly Report | UI |
| US-43 | E7 | Year in Review | UI |
| US-44 | E7 | Streaks and weekly check-ins | UI |
| US-45 | E8 | About, version and licences | UI |
| US-46 | E8 | Polish, accessibility, tablet and release build | UI |

## 4. Traceability: requirements → stories

| Requirement (`01-phase1-scope.md`) | Stories |
|---|---|
| FR-1.1 Login | US-05 |
| FR-1.2, FR-1.3 Biometric data | US-06 |
| FR-1.4, FR-1.5 Goal and kcal | US-07 |
| FR-1.6 Macros | US-08 |
| FR-2.1 OFF search | US-16, US-17 |
| FR-2.2, FR-2.3 Scanner and manual creation | US-16, US-18 |
| FR-2.4, FR-2.5 Logging and editing | US-17, US-19 |
| FR-2.6 Dashboard | US-20 |
| FR-2.7 Templates | US-21 |
| FR-2.8 Portions and measures | US-22 |
| FR-2.9 Favourites and most eaten | US-17 |
| FR-2.10 Copy and repeat meals | US-19 |
| FR-2.11 Configurable meals | US-23 |
| FR-2.12 Diary views and history | US-19, US-24 |
| FR-3.1 Manual weight | US-25 |
| FR-3.2 Health Connect | US-26 |
| FR-3.3 – FR-3.5 Chart, filters, rate | US-27 |
| FR-4.1 Exercise catalog | US-09 |
| FR-4.2 Routines | US-10 |
| FR-4.3 Live session | US-11 |
| FR-4.4 Last performance | US-12 |
| FR-4.5 Timer | US-13 |
| FR-4.6 History | US-14 |
| FR-4.7 Progress charts | US-15 |
| FR-4.8 Training depth (set types, RPE, metrics, calculators, PRs, routine tracking, workout detail) | US-28 … US-38 |
| FR-4.9 Statistics and reports | US-39 … US-44 |
| FR-5.1 – FR-5.3 Navigation, feedback, offline | US-03, US-04, US-46 (FR-5.1 4th tab: T-037, ADR-26) |
| FR-6 UI refinement | US-46 (E8) |
| NFR-03 Tablet | US-04, US-46 |
| NFR-05, NFR-06 Professional / security | US-01, US-02 (and the DoD of every story) |

## 5. Definition of Done for every story

- Gherkin criteria covered by tests (unit, host and/or pgTAP).
- `ktlintCheck`, `detekt`, unit tests and, when applicable, `supabase test db` green locally and in CI.
- Every new table has RLS and a cross-user isolation test.
- UI built with design system components and tokens; testTags on interactive elements.
- Loading, empty, error and offline states handled on every new screen.
- No secrets in the diff. PR with a complete description, reviewed and squash-merged.
- `tasks/todo.md` updated.

## 6. Task breakdown

For each epic, from its file in `epics/`, the PM generates `T-###` tasks (a story may produce several tasks if it exceeds ~400 changed lines or mixes decoupled backend and UI work) and records them in `tasks/todo.md` (epic plan in `tasks/plan.md`).
