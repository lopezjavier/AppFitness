# Phase 1 scope

## 1. Goal

Android app (phone and tablet) for personal use, shareable with close friends and family, to track calories, body weight and gym performance. All data is stored in the cloud, separated per user profile. Cost: €0 (free services only). Professional quality: version control on GitHub, CI/CD with GitHub Actions, security enforced by RLS and a consistent design system.

## 2. Non-functional requirements

| ID | Requirement | How it is met |
|---|---|---|
| NFR-01 | Multi-user with profiles (1–5 expected users) | Supabase Auth with Google + RLS by `user_id` |
| NFR-02 | 100% free | Supabase Free, GitHub Free with a public repo (unlimited Actions minutes, ADR-13), free OFF and Health Connect |
| NFR-03 | Clients: Android phone and tablet | Adaptive layout by WindowSizeClass |
| NFR-04 | Cloud data per profile | Supabase Postgres as the single source of truth (ADR-01) |
| NFR-05 | Professional level | GitHub, Conventional Commits, PRs, mandatory CI, unit tests + pgTAP + Maestro |
| NFR-06 | Security | RLS on every table with isolation tests; no secrets in the repo or the APK except the public Supabase publishable key |
| NFR-07 | Modern, consistent design | Kinetic Dark design system implemented in Compose and reused across all screens |
| NFR-08 | Web-ready (Phase 2) | KMP with logic and UI in `commonMain` (ADR-05) |
| NFR-09 | One-handed use and readability at the gym | Targets ≥ 48 dp, actions in the lower half, tabular numerals, dark mode |
| NFR-10 | Language | UI in Spanish; strings in resources (i18n-ready). Repository content in English (ADR-16) |
| NFR-11 | Units | Metric system (kg, cm, g, kcal) |

## 3. Functional requirements

### FR-1 · User profile and settings (E2)
- FR-1.1 Sign in with Google; persistent session; sign out.
- FR-1.2 Onboarding: age (date of birth), sex, height, current weight and activity level.
- FR-1.3 Later editing of that data in Profile.
- FR-1.4 Main goal: Deficit, Maintenance or Surplus.
- FR-1.5 Daily calorie target calculated (TDEE + adjustment) and manually adjustable.
- FR-1.6 Macro split (P/C/F) with auto-balancing sliders that always add up to 100%.

### FR-2 · Calorie tracking — Diary (E3)
- FR-2.1 Food text search on Open Food Facts (with our own cache).
- FR-2.2 Barcode scanning with Google Code Scanner.
- FR-2.3 Manual food creation when a product is not in OFF.
- FR-2.4 Logging foods with a quantity in grams into 4 meals: Breakfast, Lunch, Snack, Dinner (UI: Desayuno, Comida, Merienda, Cena).
- FR-2.5 Edit and delete entries; navigate between days.
- FR-2.6 Daily dashboard: kcal consumed vs target and progress for each macro; totals per meal.
- FR-2.7 Meal templates: save a meal as a template, add it with one tap, manage templates.

### FR-3 · Weight tracking — Progress (E4)
- FR-3.1 Manual weight logging (optional body fat %); edit and delete.
- FR-3.2 Manual sync with Health Connect (weight and body fat % from smart scales).
- FR-3.3 Progress chart with daily readings and a 7-day moving average.
- FR-3.4 Time filters 1W, 1M, 3M, 6M, 1Y (UI: 1S, 1M, 3M, 6M, 1A).
- FR-3.5 Weekly rate of change colored according to the goal.

### FR-4 · Gym performance — Workout (E5, E6)
- FR-4.1 Exercise catalog (seeded in Spanish) and custom exercises.
- FR-4.2 Create and edit routines: reorderable exercises, supersets, target sets/reps, rest per exercise.
- FR-4.3 Live workout session: weight and reps per set, add sets/exercises, screen always on, summary when finishing.
- FR-4.4 Last performance visible during the workout (ghost data).
- FR-4.5 Automatic, configurable, non-blocking rest timer (+30 s, Skip).
- FR-4.6 Session history.
- FR-4.7 Progress charts per exercise (estimated 1RM, volume) and per muscle group (weekly volume).

### FR-5 · Cross-cutting (E1)
- FR-5.1 4-tab navigation (Diario, Entreno, Progreso, Perfil) with a contextual FAB; the Perfil tab icon is the user's Google photo (ADR-10, ADR-22, ADR-26).
- FR-5.2 Haptic feedback when completing sets and key actions; skeleton loaders for network requests.
- FR-5.3 Visible offline state.

### FR-6 · UI refinement (E7, placeholder)
A last refinement pass over the screens built in E2…E6, fed by the feedback collected at the 👤 checkpoints. It changes presentation only (layout, spacing, typography, component styling), never features. Items collected so far are listed in `07-roadmap.md` §1.1; later epics add theirs there.

## 4. Out of Phase 1 scope

These appear in the Stitch mockups or in the original documents but not in the original requirements. They will be reconsidered in later phases:

- Water intake logging.
- Progress photos ("Bitácora Visual") and Supabase Storage.
- Reminders / scheduled notifications (hydration, workout).
- Apple Health (does not exist on Android) and Strava integrations.
- Plan badges ("Plan Pro Atleta"), RPE indicators and set-type labels (CAL/EFEC) from the workout mockup.
- Training microcycles / phases.
- Background Health Connect sync.
- Offline mode.
- Publishing on Google Play (distribution is by APK).

### Deferred to Phase 2 (unchanged from the original requirements)
- Web client (Compose for Web / Wasm) and DNS on Cloudflare.
- Calorie estimation from a meal photo (AI).
- Adaptive calorie adjustment based on weight trend (AI).
- AI training assistant.
