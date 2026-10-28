# Data model (Supabase Postgres) - current state

Compact summary; full column tables, constraints and RPC contracts are in [`phase1/03-data-model.md`](phase1/03-data-model.md) (frozen). The authoritative schema is `supabase/migrations/`. New schema decisions: a new ADR in `00-decisions.md`, then update this summary.

## Conventions
- All tables in `public`; `id uuid` PK default `gen_random_uuid()` unless stated; `created_at` and trigger-maintained `updated_at`.
- `user_id uuid not null default auth.uid() references auth.users(id) on delete cascade` (every user-owned table cascades; ADR-29). Nullable author columns pick `cascade` or `set null`.
- RLS enabled and forced on every table; owner policy `user_id = (select auth.uid())` for select/insert/update/delete. Closed value sets are Postgres enums.
- Calendar dates (`log_date`) are `date` in the device's local time zone; instants are `timestamptz`.
- No default privileges for `anon`; every new object needs an explicit grant to `authenticated`/`service_role`.

## 1. Enums
`sex`, `activity_level` (sedentary..very_high), `goal_strategy` (deficit/maintenance/surplus), `meal_type` (default meal codes), `food_source` (off/user), `food_base_unit` (g/ml), `food_cooking_method` (raw/griddle/air_fryer/fried), `measurement_source` (manual/health_connect), `circumference_site`, `muscle_group`, `equipment`, `session_status` (in_progress/completed/discarded), `exercise_metric_type` (weight_reps, reps_only, duration, weight_duration, distance_duration, bodyweight, assisted, weighted, steps_duration, floors_duration), `set_type` (working/warm_up/failure/drop), `notification_kind` (weight/circumference/weekly_check_in).

## 2. Profile and goals
- `profiles` (id = auth user): display_name, avatar_url (Google, `https://` only), birth_date, sex, height_cm, activity_level, target_weight_kg, onboarding_completed. Created by the `on_auth_user_created` trigger (sanitized metadata); `on_auth_user_metadata_updated` refreshes the avatar only. Client has SELECT and column-level UPDATE; no delete. Current weight is the latest `body_measurements` row.
- `nutrition_goals` (one per user): strategy, calorie_adjustment, target_kcal, protein/carbs/fat percentages. Written together with `profiles` through `save_preferences`.

## 3. Nutrition
- `foods`: source (`off`/`user`), barcode, off_id, base_unit, nutrition per 100 base units (kcal, macros), `micronutrients jsonb` (35 label keys), `off_data jsonb` (full OFF product, Edge-Function-written only), serving fields, image_url, created_by, `search_vector`, `fetched_at`. Shared cache for OFF rows; manual foods are owner-only (editable, ADR-53).
- `food_portions` (food_id, label, amount in base units, position).
- `food_log_entries`: log_date, meal_slot_id, food_id, resolved `amount`, `portion_label`, snapshots of kcal/macros/`food_name`, `cooking_method`, `cooking_oil_retained_g` (added fat g), nullable `group_id`/`group_name` (saved-comida group snapshot, ADR-68).
- `meal_slots` (label, position, `enabled` always true; deleted when unused, at least one stays; ADR-41). Seeded Desayuno/Comida/Merienda/Cena at onboarding.
- `meal_templates` ("comidas", unique name per user, suggested slot nullable) + `meal_template_items` (food, amount, portion, cooking_method).
- `food_preferences` (one per user): fat % for griddle / air fryer / fried (0-20).
- `meal_defaults` (user, meal_slot, template, ISO weekday; one per slot+weekday) and `diary_default_applications` (user, log_date marker; idempotent daily apply).
- `favorite_foods` (user, food).

## 4. Weight
- `body_measurements`: measured_at, weight_kg, body_fat_pct, lean_mass_kg, water_kg, bone_kg, bmr_kcal (composition only from Health Connect), source, `external_id` (HC dedup), `hc_record_id` (copy written by the app). The onboarding weight is the user's first row.
- `body_circumferences` (site, value_cm, measured_at). `health_sync_state` (last sync per user).
- `notification_settings` (T-135, US-57): `(user_id, kind)` primary key, `enabled`, `updated_at`; owner select/insert/update (no delete), client upserts on `(user_id, kind)`. **A missing row means enabled.** Reminders are derived on the client from the latest weight and circumference rows, never stored.

## 5. Training
- `exercises`: catalogue rows hold only `key`, `muscle_group`, `secondary_muscles`, `equipment`, `metric_type`, `per_hand` (names/text/images come from the client bundle, ADR-38/39); custom rows (`is_custom`, owner) have a name.
- `routine_folders`; `routines` (name unique per user, folder_id, `track_as_routine`, `is_next`, pinned_note); `routine_exercises` (position, superset_group, target_rpe, rest_seconds, legacy summary columns) and `routine_exercise_sets` (planned weight/reps/type per set, ADR-54).
- `training_preferences` (one per user): warm_up_included, previous_values_mode, bar_weight_kg, plate_kg, dumbbell_handle_kg, auto_update_routine (global, ADR-70).
- `workout_sessions`: routine_id (set null on routine delete), name snapshot, status, started_at/ended_at, description, gym, paused_at, paused_total_s, exercise_rests. `workout_sets`: session, exercise, exercise_position, superset_group, set_index, set_type, rpe (6-10), weight_kg, reps, duration_s, distance_m, steps, floors, completed_at (null = pending).

## 6. RPC functions (all `security invoker`, `authenticated` only, owner-scoped)
- Profile: `save_preferences` (profile + goals + optional weight, one transaction).
- Weight: `save_body_measurement`.
- Training session: `start_workout_session`, `pause_/resume_workout_session`, `finish_workout_session`, `discard_workout_session`, `delete_workout_set`, `set_session_rest`, `last_performance`, `save_completed_workout`, `save_workout_as_routine`, `update_routine_from_session`.
- Routines: `save_routine` (jsonb with per-exercise planned sets, `target_rpe`, supersets share set counts).
- Records and stats: `exercise_history`, `weekly_muscle_volume`, `exercise_session_records`, `personal_records`, `training_summary`, `weekly_set_counts`, `muscle_group_sets`, `exercise_set_counts` (rules in `04` §4.8, §4.11).
- Food: `create_personal_food`, `update_personal_food`, `personal_food_in_use`, `most_eaten_foods`, `food_diary_summary`, `cooking_impact`.
- Meals and comidas: `save_meal_structure`, `list_meal_slots_in_use` (legacy `save_meal_slots`, `delete_meal_slot`), `save_meal_template`, `save_meal_as_comida`, `save_meal_defaults`, `apply_meal_defaults`.
- Error codes: `28000` unauthenticated, `P0002` not found / not owned, `22023` invalid input, `23505` duplicate name, `23514` check violation.
- The 1RM is computed in SQL with the same formula as the domain; domain and pgTAP tests share the cases.

## 7. Edge Functions (Deno/TS, `_shared/` holds the OFF and Gemini clients)

| Function | Auth | Behavior |
|---|---|---|
| `health` | public | `GET` -> `200 {"status":"ok"}` after `select 1`; keepalive target |
| `delete-account` | JWT | `POST`, resolves the caller from the token, deletes the auth user with the secret key (cascade); `403 account_protected` for `PROTECTED_EMAILS` (ADR-29, ADR-57) |
| `food-search` | JWT | `?q=` (2-60 chars): full-text in `foods`, then OFF (Spain, Spanish name, ADR-46) when < 10 results; normalizes, upserts, returns up to 20 |
| `food-barcode` | JWT | `?code=` EAN-8/13/UPC-A: `foods` first, OFF when missing or older than 30 days; `404` if absent or without kcal |
| `food-photo` | JWT | `POST` JSON `{image_base64, mode: dish\|label\|barcode\|weight, comment?, food?}` -> Gemini -> normalized draft or amount estimate (ADR-59, 64, 72); never stores or logs the image |

OFF client: custom `User-Agent`, 8 s timeout, normalization (base unit from `nutrition_data_per`).

## 8. Relationships
`auth.users` 1-1 profiles, nutrition_goals, health_sync_state, food_preferences, training_preferences; 1-n meal_slots, food_log_entries (n-1 foods, n-1 meal_slots), favorite_foods, meal_templates (1-n items n-1 foods), meal_defaults, diary_default_applications, body_measurements, body_circumferences, routine_folders 1-n routines 1-n routine_exercises (n-1 exercises) 1-n routine_exercise_sets, workout_sessions 1-n workout_sets (n-1 exercises); foods 1-n food_portions.
