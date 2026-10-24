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
| NFR-05 | Professional level | GitHub, Conventional Commits, PRs, mandatory CI, unit and host tests + pgTAP |
| NFR-06 | Security | RLS on every table with isolation tests; no secrets in the repo or the APK except the public Supabase publishable key |
| NFR-07 | Modern, consistent design | Kinetic Dark design system implemented in Compose and reused across all screens |
| NFR-08 | Web-ready (Phase 2) | KMP with logic and UI in `commonMain` (ADR-05) |
| NFR-09 | One-handed use and readability at the gym | Targets ≥ 48 dp, actions in the lower half, tabular numerals, dark mode |
| NFR-10 | Language | UI in Spanish; strings in resources (i18n-ready). Repository content in English (ADR-16) |
| NFR-11 | Units | Metric system (kg, cm, g, kcal) |

## 3. Functional requirements

### FR-1 · User profile and settings (E2)
- FR-1.1 Sign in with Google; persistent session; sign out; delete the account and all its data from Profile (ADR-29).
- FR-1.2 Onboarding: age (date of birth), sex, height, current weight and activity level.
- FR-1.3 Later editing of that data in Profile.
- FR-1.4 Main goal: Deficit, Maintenance or Surplus.
- FR-1.5 Daily calorie target calculated (TDEE + adjustment) and manually adjustable.
- FR-1.6 Macro split (P/C/F) with auto-balancing sliders that always add up to 100%.

### FR-2 · Calorie tracking — Diary (E4)
- FR-2.1 Food text search on Open Food Facts (with our own cache).
- FR-2.2 Barcode scanning with Google Code Scanner.
- FR-2.3 Manual food creation when a product is not in OFF.
- FR-2.4 Logging foods with a quantity into the day's meals (configurable; Breakfast, Lunch, Snack, Dinner by default; UI: Desayuno, Comida, Merienda, Cena).
- FR-2.5 Edit and delete entries; navigate between days.
- FR-2.6 Daily dashboard: kcal consumed vs target and progress for each macro; totals per meal.
- FR-2.7 Saved meals ("Comidas", templates in the backend): save a meal as a named comida, add it from the search, edit it (overwrite or save as new), manage them. No Diary quick-add chips (ADR-42).
- FR-2.8 Portions and measures: each food has a base unit (`g` for solids, `ml` for drinks) and optional named portions; log by portion or amount.
- FR-2.9 Favourites and "most eaten": star foods and see the most logged ones.
- FR-2.10 Copy foods or a whole meal to another meal or day, and repeat yesterday's meal in one tap.
- FR-2.11 Configurable meals: rename, reorder, add and delete meals (ADR-41).
- FR-2.12 Diary views (Detail, List, Summary) and history: calendar of past days with daily, weekly and monthly totals/averages.
- FR-2.13 Full Open Food Facts product data kept in the catalog and micronutrients shown in the food details (display only; ADR-43).
- FR-2.14 Editing manual foods (E6, US-47): edit a manual food and its portions (add, edit, remove), for future entries only; the full label nutrient list in Spanish label order; Open Food Facts foods are not editable (ADR-53).
- FR-2.15 Photo food creation (E7, US-48/US-49): an "Añadir desde foto" action below the scanner icon with three AI modes — estimate of a single dish, nutrition label and barcode. The barcode mode asks the AI to identify the product and its nutrition; it does not change the existing scanner flow. The AI prefill is editable in the normal manual-food form, saved as a personal food (`source = 'user'`) and logged through the normal add-to-meal sheet. Photos are sent for inference only and never stored (ADR-59).
- FR-2.16 Cooking methods (E7, US-50): Crudo (default), A la plancha, Air fryer and Frito for solid foods, with the energy and fat of the retained olive oil added per the configurable amounts in Preferencias; the method and retained oil are stored in the entry snapshot. Saved comidas store an editable method per item, applied when the comida is added or used as a default (ADR-60).
- FR-2.17 Default comidas (E7, US-51): assign a saved comida to a meal on selected weekdays; on the first open of a new day's Diary, the matching comidas are added once. No backfill, no past dates and no re-add after deletion (ADR-61).

### FR-3 · Weight tracking — reached from Perfil (E5; there is no Progreso tab, ADR-37)
- FR-3.1 Manual weight logging (optional body fat %); edit and delete. Body circumference measurements (neck, chest, waist, hip, arm, thigh, calf) with history, editing and deletion.
- FR-3.2 Manual sync with Health Connect, reading and writing weight, body fat, lean mass, body water, bone mass and BMR from smart scales.
- FR-3.3 Progress chart with daily readings and a 7-day moving average.
- FR-3.4 Time filters 1W, 1M, 3M, 6M, 1Y (UI: 1S, 1M, 3M, 6M, 1A).
- FR-3.5 Weekly rate of change colored according to the goal.
- FR-3.6 Target weight and distance to the goal.
- FR-3.7 BMI and informative interpretation of the metrics (with a non-medical disclaimer).
- ~~FR-3.8 Compare two measurements~~ — dropped at Checkpoint E5-B (ADR-48).

### FR-4 · Gym performance — Workout (E3, E6, E8)
- FR-4.1 Exercise catalog seeded in Spanish (with the bundled RepDB thumbnail + start↔peak animation + Spanish instructions) and custom exercises.
- FR-4.2 Create and edit routines: reorderable exercises, supersets, target sets/reps, rest per exercise.
- FR-4.3 Live workout session: weight and reps per set, add sets/exercises, screen always on, summary when finishing.
- FR-4.4 Last performance visible during the workout (ghost data).
- FR-4.5 Automatic, configurable, non-blocking rest timer (+30 s, Skip).
- FR-4.6 Session history.
- FR-4.7 Progress charts per exercise (estimated 1RM, volume) and per muscle group (weekly volume).
- FR-4.8 Training depth (E6): per-set routine planning, set types (warm-up/failure/drop) and warm-up inclusion, RPE, per-exercise metric types, volume options, warm-up/plate calculators, PR catalogue (no live notification, §4.1), routine folders/order/tracking and "Update Routine", workout detail/calendar (trained days)/editing and past workouts, session controls (pause, delete set, previous-values modes).
- FR-4.9 Statistics and reports (E8): exercise chart records and non-weight metrics, advanced statistics (time frames, set-count, sets per muscle group, main exercises), muscle distribution (radar, body heatmap), Monthly Report, Year in Review, streaks and weekly check-ins.
- The training tables ship **forward-compatible** in E3 (ADR-30), so E6/E8 add features without a schema migration.

### FR-5 · Cross-cutting (E1, E9)
- FR-5.1 3-tab navigation (Diario, Entreno, Perfil); the Perfil tab icon is the generic `person` glyph, and the Perfil root shows the Google photo, the name and entries to Preferencias and the statistics screens (weight, strength, insights) (ADR-10, ADR-22, ADR-36, ADR-37).
- FR-5.2 Haptic feedback when completing sets and key actions; skeleton loaders for network requests.
- FR-5.3 Visible offline state.
- FR-5.4 "Acerca de · Licencias" screen (E9, US-45): version/build, the RepDB, Open Food Facts and Google Gemini credits and the open-source licences, opened from Profile.

### FR-6 · UI refinement (E9, US-46)
A last refinement pass over the screens built in E2…E8, fed by the feedback collected at the 👤 checkpoints. It changes presentation only (layout, spacing, typography, component styling), never features. The collected items live in `epics/E9-finalization.md`; later epics add theirs there.

## 4. Out of Phase 1 scope

These appear in the Stitch mockups or in the original documents but not in the original requirements. They will be reconsidered in later phases:

- Water intake logging.
- Progress photos ("Bitácora Visual") and Supabase Storage.
- Reminders / scheduled notifications (hydration, workout).
- Apple Health (does not exist on Android) and Strava integrations.
- Plan badges ("Plan Pro Atleta") and the CAL/EFEC decorative labels from the workout mockup (RPE and set types themselves are in E6).
- Training microcycles / phases and the algorithmic program generator / trainer (Phase 2).
- Background Health Connect sync.
- Offline mode.
- Publishing on Google Play (distribution is by APK).

### 4.1 Deferred by the user (may come back later)
Stories and scenarios removed from an epic by the user. They are kept here with their origin and text so they are not lost; bringing one back is a scope decision of the user.

| Item | Origin | Decision | Removed text |
|---|---|---|---|
| Live record notification and its strength levels | E6, US-34 "Live PR"; `04` §4.8 | ADR-52, 2026-10-22 | `When a completed set beats the current best of that exercise / Then a "¡Nuevo récord!" notification appears / And the strength of the notification follows the user's setting` (setting: off / low / normal / high) |
| Training streak, rest days and rewards in the calendar | E6, US-37 "Calendar" | ADR-52, 2026-10-22 | `Then they see the trained days, the streak and the rest days (month, year, multi-year)` (only "the streak and the rest days" removed; trained days kept). E8 US-44 "Streaks and weekly check-ins" is reviewed with the user at the E8 breakdown. |
| Copying a workout | E6, US-37 "Editing" | ADR-52, 2026-10-22 | `When they edit, copy or delete a workout / Then the change is reflected` (only "copy" removed). A past workout can still start from a finished workout. |
| Routine asks only sets and rest | E6, US-29 (user request 2026-10-20) | ADR-50, 2026-10-22 (contradicted per-set planning) | `When the user adds an exercise in the routine builder / Then they configure only the number of sets and the rest / And the reps are entered in the live session` |
| Editing Open Food Facts foods | E6 scope line (food editing, "if feasible") | ADR-53, 2026-10-22 | "edit macros and micronutrients … of Open Food Facts foods (if feasible)" |

### Deferred to Phase 2 (unchanged from the original requirements)
- Web client (Compose for Web / Wasm) and DNS on Cloudflare.
- Rest-timer alert while the app is in the background or closed (scheduled notification with sound; Phase 1 alerts with sound and vibration in the foreground only).
- Whole-meal calorie estimation from a single photo (several foods; Phase 1 estimates one food per photo, FR-2.15).
- Adaptive calorie adjustment based on weight trend (AI).
- AI training assistant.
