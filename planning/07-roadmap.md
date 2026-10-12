# Phase 1 roadmap

## 1. Epics overview

| Epic | Name | Stories | Dominant flow | Delivery |
|---|---|---|---|---|
| E0 | Project setup | — (setup tasks) | Manual with the user | Repo ready, `CLAUDE.md`, agents, Supabase deploy, OAuth |
| E1 | Infrastructure and skeleton | US-01 … US-04 | Backend + UI | `v0.1.0` — navigable APK with a WIP shell |
| E2 | Authentication and profile | US-05 … US-08 | Full UI | `v0.2.0` — login, onboarding, goals |
| E3 | Food diary | US-09 … US-14 | Full UI + backend | `v0.3.0` — complete meal logging |
| E4 | Weight tracking | US-15 … US-17 | Full UI | `v0.4.0` — weight, Health Connect, chart |
| E5 | Training | US-18 … US-23 | Full UI + backend | `v0.5.0` — routines and live session |
| E6 | Analytics and wrap-up | US-24 … US-25 | Full UI | `v1.0.0` — stable Phase 1 release |

## 2. Dependencies

```
E0 ──► E1 ──► E2 ──┬──► E3
                   ├──► E4
                   └──► E5 ──► E6 (US-24)
        E3, E4, E5 ──────────► E6 (US-25)
```
- E2 is a prerequisite for every feature: without an authenticated user and goals there is no Diary.
- E3, E4 and E5 are independent of each other; the proposed order (Diary → Weight → Training) follows the priority of the original requirements. It can be reordered if the user prefers.
- US-24 (strength charts) depends on E5 data.

## 3. User stories

| ID | Epic | Title | Flow |
|---|---|---|---|
| US-01 | E1 | Base KMP project with flavors, quality tooling and Android CI | Backend/infra |
| US-02 | E1 | Supabase baseline: profiles, RLS, health check, CI and keep-alive | Backend/infra |
| US-03 | E1 | Kinetic Dark design system in Compose | UI |
| US-04 | E1 | Navigable shell with 4 tabs, contextual FAB and adaptive layout | UI |
| US-05 | E2 | Sign in with Google | UI + backend |
| US-06 | E2 | Biometric onboarding | UI + backend |
| US-07 | E2 | Energy strategy and calorie target | UI + backend |
| US-08 | E2 | Macro split with auto-balancing sliders | UI + backend |
| US-09 | E3 | Food catalog and Open Food Facts proxy | Backend |
| US-10 | E3 | Food text search and logging into a meal | UI |
| US-11 | E3 | Barcode scanning and manual food creation | UI |
| US-12 | E3 | Daily diary with 4 meals: view, edit and delete | UI |
| US-13 | E3 | Calorie and macro dashboard | UI |
| US-14 | E3 | Meal templates | UI + backend |
| US-15 | E4 | Manual weight logging | UI + backend |
| US-16 | E4 | Manual Health Connect sync | UI |
| US-17 | E4 | Metrics panel and trend chart | UI |
| US-18 | E5 | Exercise catalog | UI + backend |
| US-19 | E5 | Routine builder | UI + backend |
| US-20 | E5 | Live workout session | UI + backend |
| US-21 | E5 | Previous performance (ghost data) | UI + backend |
| US-22 | E5 | Non-blocking rest timer | UI |
| US-23 | E5 | Session history | UI |
| US-24 | E6 | Strength analytics per exercise and muscle group | UI + backend |
| US-25 | E6 | Polish, accessibility, tablet and release build | UI |

## 4. Traceability: requirements → stories

| Requirement (`01-phase1-scope.md`) | Stories |
|---|---|
| FR-1.1 Login | US-05 |
| FR-1.2, FR-1.3 Biometric data | US-06 |
| FR-1.4, FR-1.5 Goal and kcal | US-07 |
| FR-1.6 Macros | US-08 |
| FR-2.1 OFF search | US-09, US-10 |
| FR-2.2, FR-2.3 Scanner and manual creation | US-09, US-11 |
| FR-2.4, FR-2.5 Logging and editing | US-10, US-12 |
| FR-2.6 Dashboard | US-13 |
| FR-2.7 Templates | US-14 |
| FR-3.1 Manual weight | US-15 |
| FR-3.2 Health Connect | US-16 |
| FR-3.3 – FR-3.5 Chart, filters, rate | US-17 |
| FR-4.1 Exercise catalog | US-18 |
| FR-4.2 Routines | US-19 |
| FR-4.3 Live session | US-20 |
| FR-4.4 Last performance | US-21 |
| FR-4.5 Timer | US-22 |
| FR-4.6 History | US-23 |
| FR-4.7 Strength charts | US-24 |
| FR-5.1 – FR-5.3 Navigation, feedback, offline | US-03, US-04, US-25 |
| NFR-03 Tablet | US-04, US-25 |
| NFR-05, NFR-06 Professional / security | US-01, US-02 (and the DoD of every story) |

## 5. Definition of Done for every story

- Gherkin criteria covered by tests (unit and/or Maestro and/or pgTAP).
- `ktlintCheck`, `detekt`, unit tests and, when applicable, `supabase test db` green locally and in CI.
- Every new table has RLS and a cross-user isolation test.
- UI built with design system components and tokens; testTags on interactive elements.
- Loading, empty, error and offline states handled on every new screen.
- No secrets in the diff. PR with a complete description, reviewed and squash-merged.
- `tasks/todo.md` updated.

## 6. Task breakdown

For each epic, from its file in `epics/`, the PM generates `T-###` tasks (a story may produce several tasks if it exceeds ~400 changed lines or mixes decoupled backend and UI work) and records them in `tasks/todo.md` (epic plan in `tasks/plan.md`).
