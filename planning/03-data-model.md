# Data model (Supabase Postgres)

Conventions:
- All tables in `public`, `id uuid primary key default gen_random_uuid()` unless stated otherwise.
- `created_at timestamptz not null default now()` and `updated_at` (trigger) columns on every table.
- `user_id uuid not null default auth.uid() references auth.users(id) on delete cascade`.
- RLS enabled on every table. Standard "owner" policy: `using (user_id = (select auth.uid()))` and `with check (user_id = (select auth.uid()))` for select/insert/update/delete.
- Postgres enums for closed value sets.
- User calendar dates (`log_date`) as `date` in the device's local time zone; instants as `timestamptz`.

The story that creates each table is shown in parentheses.

## 1. Enums

```sql
create type sex as enum ('male', 'female');
create type activity_level as enum ('sedentary', 'light', 'moderate', 'high', 'very_high');
create type goal_strategy as enum ('deficit', 'maintenance', 'surplus');
create type meal_type as enum ('breakfast', 'lunch', 'snack', 'dinner');   -- default meal codes (UI: Desayuno, Comida, Merienda, Cena)
create type food_source as enum ('off', 'user');
create type food_base_unit as enum ('g', 'ml');   -- solids use g, drinks use ml
create type food_cooking_method as enum ('raw', 'griddle', 'air_fryer', 'fried');   -- UI: Crudo, A la plancha, Air fryer, Frito
create type measurement_source as enum ('manual', 'health_connect');
create type circumference_site as enum ('neck', 'chest', 'waist', 'hip', 'arm', 'thigh', 'calf');
create type muscle_group as enum ('chest', 'back', 'shoulders', 'biceps', 'triceps', 'forearms',
                                  'quads', 'hamstrings', 'glutes', 'calves', 'core', 'full_body');
create type equipment as enum ('barbell', 'dumbbell', 'machine', 'cable', 'bodyweight', 'kettlebell', 'band', 'other');
create type session_status as enum ('in_progress', 'completed', 'discarded');
create type exercise_metric_type as enum ('weight_reps', 'reps_only', 'duration', 'weight_duration',
                                          'distance_duration', 'bodyweight', 'assisted', 'weighted',
                                          'steps_duration', 'floors_duration');
create type set_type as enum ('working', 'warm_up', 'failure', 'drop');   -- UI: Normal, Calentamiento, Fallo, Drop
```

## 2. Profile and goals

### `profiles` (US-02, extended in US-06)
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | = `auth.users.id` (on delete cascade) |
| display_name | text | From Google when the user is created; 1–100 characters, not blank (check) |
| avatar_url | text null | From Google; `https://` only, at most 2048 characters (check) |
| birth_date | date null | Age is derived |
| sex | sex null | |
| height_cm | numeric(5,1) null | 100–250 (check) |
| activity_level | activity_level null | |
| target_weight_kg | numeric(5,2) null | 20–400 (check); goal weight, no date (E5) |
| onboarding_completed | boolean | default false |

- `on_auth_user_created` trigger (security definer) that inserts the row with name and avatar from `raw_user_meta_data`. The metadata is user-controlled, so the trigger sanitizes it and sign-up never fails on a constraint:
  - keys are read only from a JSON object and only when the value is a JSON string;
  - `display_name`: `full_name`, then `name`, then the email local part, then `Usuario`; each candidate is trimmed and capped at 100 characters, blank ones are skipped;
  - `avatar_url`: `avatar_url`, then `picture`; kept only if it starts with `https://` and has at most 2048 characters, otherwise null.
- Client privileges: `SELECT` and column-level `UPDATE` on the user-editable columns only (`display_name`, `avatar_url`, `birth_date`, `sex`, `height_cm`, `activity_level`, `target_weight_kg`, `onboarding_completed`); `id`, `created_at` and `updated_at` are not client-writable (`updated_at` is set by the `set_updated_at()` trigger).
- RLS: owner by `id = (select auth.uid())`. No delete from the client.
- `display_name` is also the name shown on Perfil and editable in Preferencias (ADR-37). Onboarding step 1 asks "¿Cómo te llamas?" only when Google gave no `full_name` (the `Usuario`/email fallbacks above do not count); the T-041 plan decides how to detect it (e.g. from the auth user metadata).
- Current weight is **not** stored here: it is the latest row of `body_measurements` (onboarding inserts one).

### `nutrition_goals` (US-07, US-08)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid PK | 1 row per user |
| strategy | goal_strategy | default `maintenance` |
| calorie_adjustment | int | kcal over the TDEE (e.g. −400) |
| target_kcal | int | Calculated by the app and stored (allows manual adjustment) |
| protein_pct, carbs_pct, fat_pct | smallint | check: each 5–90 and sum = 100 |

## 3. Nutrition

### `foods` (US-16)
Shared catalog (OFF cache) + personal foods.
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| source | food_source | |
| barcode | text null | unique when `source = 'off'` |
| off_id | text null | OFF product code |
| name, brand | text | |
| base_unit | food_base_unit | `g` for solids, `ml` for drinks; nutrition is per 100 base units |
| kcal_100, protein_100, carbs_100, fat_100 | numeric(6,2) | per 100 base units; ≥ 0 |
| serving_label | text null | Default serving, e.g. "1 vaso" |
| serving_amount | numeric(7,1) null | Default serving in the base unit |
| image_url | text null | OFF URL (images are not stored) |
| created_by | uuid null | only for `source = 'user'` |
| search_vector | generated tsvector | GIN index; `unaccent` + `spanish` config (food names are Spanish) |
| micronutrients | jsonb null | `source = 'user'` only: optional label values typed by the user, OFF keys per 100 base units (`saturated-fat_100g`, `sugars_100g`, `fiber_100g`, `salt_100g` since ADR-43; since T-102 the 35 label keys: saturated, mono/polyunsaturated fat, sugars, polyols, starch, fibre, salt, vitamins A D E K C B1 B2 PP B6 B9 B12, biotin, pantothenic acid and minerals potassium, chloride, calcium, phosphorus, magnesium, iron, zinc, copper, manganese, fluoride, selenium, chromium, molybdenum, iodine, all `<offKey>_100g` in grams, 0 to 9999.99; unknown keys rejected; ADR-53) |
| off_data | jsonb null | complete OFF product as received (`source = 'off'` only; ADR-43); written by Edge Functions, never used to compute entries, not returned by `food-search` |
| fetched_at | timestamptz | cache refresh |

RLS:
- select: `source = 'off' or created_by = (select auth.uid())`.
- insert/update/delete from the client: only `source = 'user' and created_by = (select auth.uid())`.
- `off` rows are only written by Edge Functions using the Supabase secret key (legacy `service_role`).
- Foods created from a photo (E7, US-48/US-49) are normal `source = 'user'` rows written by `create_personal_food` after the user confirms the AI prefill. There is no `ai` source value: `food_source` describes ownership/cache, not provenance. There is no photo column, bucket or Storage policy: the image never leaves the request (ADR-59).

### `food_portions` (US-22)
Named portions of a food ("1 huevo" = 55 g, "1 vaso" = 200 ml).
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| food_id | uuid FK foods | on delete cascade |
| label | text | e.g. "1 ración", "1 huevo" |
| amount | numeric(7,1) | > 0; in the food's `base_unit` |
| position | smallint | order in the picker |

RLS: select when the parent food is readable (`source = 'off' or created_by = (select auth.uid())`); insert/update/delete only on own foods (`source = 'user' and foods.created_by = (select auth.uid())`).

### `food_log_entries` (US-16; used in US-17…US-24; extended in E7)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| log_date | date | Local day |
| meal_slot_id | uuid FK meal_slots | composite FK `(meal_slot_id, user_id)` -> `meal_slots (id, user_id)`; on delete no action (US-23) |
| food_id | uuid FK foods | on delete no action |
| amount | numeric(7,1) | > 0; in the food's `base_unit` |
| portion_label | text null | snapshot of the portion used, if any |
| food_name | text | snapshot |
| cooking_method | food_cooking_method | not null, default `raw` (E7, US-50) |
| cooking_oil_retained_g | numeric(7,2) null | E7, US-50: fat in grams added by the cooking method (raw weight x percentage, ADR-65; column name kept from ADR-60); `>= 0`; null or 0 for `raw`; snapshot |
| kcal, protein_g, carbs_g, fat_g | numeric(7,1) | snapshot calculated for the amount, including the cooking impact (does not change if the food changes; editing a manual food applies to future entries only, ADR-53) |
| group_id | uuid null | E8, US-55 (T-117, ADR-68): entries of one comida added to a meal share a `group_id`; the Diario shows them as a collapsible group. No FK, no link to `meal_templates` (snapshot) |
| group_name | text null | name of the comida at the moment it was added; `1..60` characters after trim |

Index: `(user_id, log_date)`. Check: `cooking_oil_retained_g is null or (cooking_oil_retained_g >= 0 and (cooking_method <> 'raw' or cooking_oil_retained_g = 0))`. Existing rows are backfilled to `raw`/null.

Groups (T-117): checks `(group_id is null) = (group_name is null)` and `group_name` length. No backfill: past entries stay ungrouped. Trigger `food_log_entries_clear_group_on_move` (before update, security invoker) clears both columns when `meal_slot_id` changes, so a group never spans two meals. Groups do not change any total (day/meal totals, `food_diary_summary` and goals sum the flat entries). Any future code that clones entries (copy day / copy meal) must give the clones a new `group_id` per source group.

`no action` (checked at statement end) instead of `restrict` (checked immediately): deleting an `auth.users` row cascades to entries, slots and personal foods in one statement (ADR-29), and `restrict` could block it before the entries are gone. RLS insert/update also requires the food to be readable by the caller.

### `meal_slots` (US-23)
Configured meals per user (rename, reorder, add, delete; ADR-41).
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| user_id | uuid | |
| code | meal_type null | default meals keep their code; custom meals are null |
| label | text | shown in the Diary |
| position | smallint | order within the day |
| enabled | boolean | default true; deprecated (ADR-41): always true, no UI to disable |

Seeded with Desayuno/Comida/Merienda/Cena (codes breakfast/lunch/snack/dinner, positions 0..3) when onboarding completes (trigger on `profiles`, idempotent; existing onboarded users are backfilled). `unique (user_id, position)`. RLS owner policy, including DELETE (ADR-41). A meal is deleted only through `delete_meal_slot` (never the last one, never one with `food_log_entries`; the entries FK is `no action`).

### `meal_templates` (US-21)
`id`, `user_id` (FK auth.users, cascade), `name` (1..60 chars after trim; unique per user on `lower(btrim(name))`, so "desayuno a" and "Desayuno A" collide), `meal_slot_id` null (suggested meal; null = any), `created_at`. Composite FK `(meal_slot_id, user_id) -> meal_slots (id, user_id) on delete set null (meal_slot_id)`: deleting a meal keeps the template (ADR-41). UI calls them "comidas" (ADR-42). `unique (id, user_id)` supports the composite FK from `meal_defaults`.

### `meal_template_items` (US-21)
`id`, `template_id` FK (on delete cascade), `food_id` FK (no action, deferred to commit so deleting a user with personal foods works), `amount numeric(7,1)` (base unit, `> 0 and <= 5000`), `portion_label` null, `cooking_method food_cooking_method not null default 'raw'` (E7, US-50: editable per item; only `g` foods may be non-raw, validated by `save_meal_template` and the client), `position smallint` (`unique (template_id, position)`). Existing items are backfilled to `raw`.
RLS via `exists (select 1 from meal_templates t where t.id = template_id and t.user_id = (select auth.uid()))`.

### `food_preferences` (E7, US-50)
One row per user with the added-fat percentage (of the raw weight) used by the cooking selector. It lives beside `training_preferences` and is written by `save_preferences`.
| Column | Type | Notes |
|---|---|---|
| user_id | uuid PK | 1 row per user |
| fat_pct_griddle | numeric(4,1) | default 1; check 0–20 |
| fat_pct_air_fryer | numeric(4,1) | default 2; check 0–20 |
| fat_pct_fried | numeric(4,1) | default 8; check 0–20 |

A missing row means the three defaults. RLS owner policy. T-112 (ADR-65) replaced the former `olive_oil_*_g` gram columns; the old values were discarded (rows reset to the defaults).

### `meal_defaults` (E7, US-51)
The recurring saved comidas applied on the first open of a new day.
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| meal_slot_id | uuid | composite FK `(meal_slot_id, user_id)` -> `meal_slots (id, user_id)` on delete cascade |
| template_id | uuid | composite FK `(template_id, user_id)` -> `meal_templates (id, user_id)` on delete cascade |
| weekday | smallint | ISO weekday, 1 (Monday) – 7 (Sunday) |
| created_at | timestamptz | default now() |

PK `(user_id, meal_slot_id, weekday)`: one default comida per meal and weekday, while the same comida may cover several days or meals. RLS owner policy. `save_meal_defaults` replaces all rows atomically.

### `diary_default_applications` (E7, US-51)
Idempotency marker for `apply_meal_defaults`.
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| log_date | date | local day already evaluated |
| applied_at | timestamptz | default now() |

PK `(user_id, log_date)`; RLS owner policy. A day is marked even when it already had entries and nothing was inserted, so deleting the defaults later never re-adds them. There is no backfill.

### `favorite_foods` (US-17)
`user_id`, `food_id` FK (on delete cascade); PK `(user_id, food_id)`. RLS owner policy. "Más comidos" (most eaten) is derived from `food_log_entries`, not stored here (see §6).

## 4. Weight

### `body_measurements` (US-06 creates the first row; US-25, US-26)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| measured_at | timestamptz | |
| weight_kg | numeric(5,2) | 20–400 |
| body_fat_pct | numeric(4,1) null | 2–75 |
| lean_mass_kg | numeric(5,2) null | from Health Connect |
| water_kg | numeric(5,2) null | from Health Connect (body water mass) |
| bone_kg | numeric(4,2) null | from Health Connect |
| bmr_kcal | integer null | from Health Connect |
| source | measurement_source | |
| external_id | text null | Health Connect record id of an imported reading; `unique (user_id, external_id)` |
| hc_record_id | text null | Manual rows only: id of the copy this app wrote to Health Connect, used to update or delete it (US-26, T-079; exact name chosen by its planner) |

Index: `(user_id, measured_at desc)`.

Onboarding inserts one `manual` row; finishing the onboarding weight step again updates that row instead of inserting another (ADR-24). The onboarding row is the user's earliest `source = 'manual'` row by `created_at` (tie-break `id`); this holds because nothing else writes `body_measurements` before onboarding completes. The composition columns (`lean_mass_kg`, `water_kg`, `bone_kg`, `bmr_kcal`) are only written by the Health Connect sync (US-26); manual entries leave them null.

Weight edited in Preferencias (ADR-37): saving a changed weight upserts **one manual row per day** (the user's `source = 'manual'` row whose `measured_at` falls on today's local date is replaced; otherwise a row is inserted); earlier days stay as history. "Peso actual" is the latest row by `measured_at` from any source. The weight screen (US-25, E5) follows the same rule: weights are revisited in a calendar by day; an empty day gets its manual weight, a day with one opens it to edit. **The date of a measurement is never edited** (only the weight, body fat and the time within the same local day), so an edit can never put two manual rows on one day (user decision 2026-10-21, E5 Q1; enforced by the T-076 RPC). It is done inside the `save_preferences` RPC (section 6) so the weight, the name, the biometrics and the recalculated target are saved atomically; the client sends its local-day bounds.

### `body_circumferences` (US-25)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| measured_at | timestamptz | |
| site | circumference_site | |
| value_cm | numeric(5,1) | 20–250 |
| source | measurement_source | |

Index: `(user_id, site, measured_at desc)`. Waist-to-hip ratio (WHR) is **derived** from the latest `waist` and `hip` rows, never stored.

### `health_sync_state` (US-26)
`user_id` PK, `last_synced_at timestamptz`. (Per user, not per device: enough for Phase 1.)

## 5. Training

The training model is **forward-compatible** (ADR-30): the columns marked *(E6)* are created now, with defaults, but only used from E6, so the follow-up epic adds features **without a schema migration**. E3 implements only the subset it renders (`metric_type = weight_reps`, `set_type = working`).

### `exercises` (US-09)
| Column | Type | Notes |
|---|---|---|
| name | text null | Custom exercises only (user-facing, any language); null for catalog rows, whose Spanish name comes from the client bundle (ADR-38) |
| key | text null | Slug linking to the client bundle (`library.json`, generated from `free.es.json`); null for custom |
| muscle_group | muscle_group | Primary group |
| secondary_muscles | muscle_group[] | default `'{}'` *(E6)* |
| equipment | equipment | |
| metric_type | exercise_metric_type | default `weight_reps` *(E6 uses the non-weight variants)* |
| per_hand | boolean | not null, default false; the logged weight is per hand/side and volume counts x2 *(T-097i, ADR-56)*; catalogue flags are set by migration, read-only |
| is_custom | boolean | |
| created_by | uuid null | custom only |

Catalog: one row per RepDB free exercise (`key`, `muscle_group`, `equipment`, `metric_type = weight_reps`, no name), loaded by a generated migration (`supabase/migrations/*_exercise_catalog.sql`, ADR-39), not by `seed.sql`. Consistency check: custom => `created_by` and `name` set (1-100 chars), `key` null; catalog => `key` set, `created_by` and `name` null. Names, instructions and images are **not** stored here: they live in the client asset bundle (`composeApp/src/commonMain/composeResources/files/exercises/`, ADR-30, ADR-39). RLS select: `not is_custom or created_by = (select auth.uid())`; writes only on own custom exercises.

### `routine_folders` (E6)
`user_id`, `name`, `position`. RLS owner policy. Deleting a folder deletes its routines too, after a confirmation in the UI (ADR-51); their past sessions are kept (`workout_sessions.routine_id` is set null). This supersedes "its routines move to the default folder".

### `routines` (US-10, extended in E6)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| name | text | unique per user ignoring case and surrounding spaces among non-archived rows (index `routines_user_name_key`, SQLSTATE 23505) *(T-124)* |
| notes | text null | |
| archived | boolean | default false |
| pinned_note | text null | Shown every time the routine is logged *(E6)* |
| folder_id | uuid null FK routine_folders | *(E6)*; null = "Mis rutinas" |
| track_as_routine | boolean | default true *(E6)* |
| is_next | boolean | default false *(E6)*; at most one per user |

### `routine_exercises` (US-10, extended in E6)
| Column | Type | Notes |
|---|---|---|
| routine_id | uuid FK | on delete cascade |
| exercise_id | uuid FK | |
| position | smallint | order |
| superset_group | smallint null | consecutive exercises with the same value form a superset |
| target_sets | smallint | 1–10 |
| target_reps | smallint | 1–50 (lower bound of the range) |
| target_reps_max | smallint null | Upper bound of the rep range *(E6)*; when null the target is `target_reps` |
| target_rpe | numeric(3,1) null | 6–10 *(E6)* |
| planned_set_type | set_type | default `working` *(E6)* |
| target_weight_kg | numeric(6,2) null | *(E6)* |
| rest_seconds | smallint | default 120, 0–600 |

RLS via the parent routine. `target_reps`, `target_weight_kg` and `planned_set_type` are legacy summaries since T-089 (ADR-54): `target_reps` is the first working set's reps, `target_sets` the planned-set count.

### `routine_exercise_sets` (E6, T-089, ADR-54)
| Column | Type | Notes |
|---|---|---|
| routine_exercise_id | uuid FK | on delete cascade |
| set_index | smallint | >= 1, unique per routine exercise; planned order |
| target_weight_kg | numeric(6,2) null | >= 0; null = no planned weight |
| target_reps | smallint | 1-50 |
| set_type | set_type | default `working` |

RLS via the parent routine exercise -> routine owner, forced. Existing routines were migrated to `target_sets` identical planned sets.

### `training_preferences` (E6, T-090, ADR-54)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid PK | default `auth.uid()`, FK `auth.users` on delete cascade |
| warm_up_included | boolean | not null, default false; warm-up sets count in volume, 1RM, PRs, muscle volume and set totals |
| previous_values_mode | enum `previous_values_mode` (`overall`, `routine`) | not null, default `overall` *(T-096)*; `routine` = the live session's previous values come from the last session of the same routine |
| bar_weight_kg | numeric(4,1) | not null, default 20, one of 20, 15, 10, 7.5 *(T-097)*; default bar of the calculators |
| plate_kg | numeric(4,2)[] | not null, default `{25,20,15,10,5,2.5,2,1.25,1,0.5}`; non-empty, no duplicates, every element in that catalogue (`is_valid_plate_set`) *(T-097)* |
| dumbbell_handle_kg | numeric(4,2) | not null, default 1, `> 0 and <= 10` *(T-097f, ADR-55)* |
| auto_update_routine | boolean | not null, default false *(T-124, ADR-70)*; global setting: finishing a routine workout updates the routine without asking |
| updated_at | timestamptz | trigger |

Owner-only RLS (enabled and forced). Helper `warm_up_included()` returns the caller's flag (false without a row). No row = default.

### `exercise_options` (dropped)
Created by T-093 (ADR-54) and dropped by T-097i (ADR-56): the per-user "double dumbbell" toggles were discarded; the flag now lives on the exercise (`exercises.per_hand`, see the `exercises` table).

`volume_factor(exercise_id)` returns 2 when `exercises.per_hand` is true for that exercise, else 1 (no equipment condition, not per user); it multiplies `set_volume` in `exercise_history` and `weekly_muscle_volume`. Manual exercises are written through `update_custom_exercise(p_id, p_name, p_muscle_group, p_equipment, p_per_hand)` and `delete_custom_exercise(p_id)` (security invoker; `P0002` when not found or not the caller's own; `22023` for an invalid name; `23503` with the set and routine counts when the exercise is in use, nothing is deleted). The metric type cannot change.

### `workout_sessions` (US-11, extended in E6)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| routine_id | uuid null FK | |
| name | text | snapshot |
| status | session_status | |
| started_at | timestamptz | |
| ended_at | timestamptz null | |
| description | text null | *(E6)* |
| gym | text null | *(E6)* |
| paused_at | timestamptz null | *(E6)*; non-null while paused, duration excludes paused time |
| paused_total_s | int | not null, default 0, check >= 0 *(T-096)*; whole seconds of closed pauses, excluded from the duration (04 §4.10) |
| exercise_rests | jsonb | not null, default `'{}'`, a JSON object `{"<exercise_position>": <seconds 0-600>}` *(T-099, ADR-54)*; rests changed from "Descanso", written by `set_session_rest` |

Constraint: at most one `in_progress` session per user (partial unique index).

### `workout_sets` (US-11, extended in E6)
| Column | Type | Notes |
|---|---|---|
| session_id | uuid FK | on delete cascade |
| exercise_id | uuid FK | |
| exercise_position | smallint | exercise order within the session |
| superset_group | smallint null | |
| set_index | smallint | 1..n within the exercise |
| set_type | set_type | default `working` *(E6 uses warm_up/failure/drop)* |
| rpe | numeric(3,1) null | 6–10 *(E6)* |
| weight_kg | numeric(6,2) | ≥ 0 (for weighted pull-ups = added kg) |
| reps | smallint | ≥ 0 |
| duration_s | int null | *(E6)* |
| distance_m | numeric(7,1) null | *(E6)* |
| steps | int null | *(E6)* |
| floors | int null | *(E6)* |
| completed_at | timestamptz null | null = pending set |

Constraint `unique (session_id, exercise_position, set_index)` (upsert key from the live session). RLS via the parent session. Which metric columns apply depends on the exercise's `metric_type`; the columns that do not apply stay null.

## 6. RPC functions (Postgres, `security invoker`)

| Function | Story | Returns |
|---|---|---|
| `save_preferences(p_display_name, p_sex, p_birth_date, p_height_cm, p_activity_level, p_strategy, p_calorie_adjustment, p_target_kcal, p_protein_pct, p_carbs_pct, p_fat_pct, p_weight_kg default null, p_measured_at default null, p_day_start default null, p_day_end default null, p_warm_up_included boolean default null, p_previous_values_mode previous_values_mode default null, p_bar_weight_kg numeric default null, p_plate_kg numeric[] default null, p_dumbbell_handle_kg numeric default null, p_fat_pct_griddle numeric default null, p_fat_pct_air_fryer numeric default null, p_fat_pct_fried numeric default null, p_auto_update_routine boolean default null)` | US-06/07/08 (T-041, ADR-24, ADR-37; replaces `save_profile_settings` from T-037; added-fat percentages in E7, T-112) | `void`, security invoker. One transaction: updates the caller's `profiles` name (trimmed) and biometric fields, upserts their `nutrition_goals` row and, when `p_weight_kg` is set, replaces the latest `manual` `body_measurements` row with `measured_at` in `[p_day_start, p_day_end)` or inserts one. Weight arguments are all-or-none (`22023` otherwise); `P0002` if the caller has no profile row; table checks surface as `23514`; `p_warm_up_included`, `p_previous_values_mode` and the five calculator parameters upsert `training_preferences` (null = unchanged, T-090, T-096, T-097; invalid calculator values surface as `23514`); the three percentage parameters upsert `food_preferences` (null = unchanged, 0–20, `23514` otherwise); `p_auto_update_routine` upserts `training_preferences` (null = unchanged, T-124) |
| `save_body_measurement(p_id uuid default null, p_weight_kg, p_body_fat_pct default null, p_measured_at, p_day_start, p_day_end)` | US-25 (T-076) | `uuid` of the written row, security invoker, `authenticated` only. Caller sends the bounds of the local day. `p_id` null: replaces the latest `manual` row with `measured_at` in `[p_day_start, p_day_end)` (weight, fat, time; null fat clears it) or inserts one; Health Connect rows are ignored. `p_id` set: the caller's own `manual` row only (`P0002` otherwise: other user, Health Connect, unknown), whose current `measured_at` must lie in the bounds, so the date never changes. `22023` for missing/invalid bounds or `p_measured_at` outside them; `28000` unauthenticated; ranges surface as `23514`. Delete is a plain `DELETE` under the owner policy |
| `last_performance(p_exercise_id uuid, p_routine_id uuid default null)` | US-12, US-38 (T-096) | Sets (`set_index`, `weight_kg`, `reps`, `rpe`, `duration_s`, `distance_m`, `steps`, `floors`) of the user's most recent completed session containing that exercise; with `p_routine_id` only sessions of that routine count (no fallback to other routines). The 1-arg signature was dropped |
| `save_routine(p_routine jsonb)` | US-10, US-29 (T-043, T-089) | `uuid`, security invoker, one transaction. `{id?, name, notes?, exercises:[{exercise_id, superset_group?, rest_seconds?, target_rpe?, sets:[{weight_kg?, reps, set_type}]}]}`; replaces the routine's exercises and planned sets; positions and `set_index` come from array order; `target_sets` = set count, `target_reps` = first working set's reps. Invalid payload/set (1-10 sets, reps 1-50, weight >= 0, known type) or superset members with different set counts: `22023`; routine not visible: `P0002` |
| `start_workout_session(p_routine_id uuid default null)` | US-11 (T-044) | `uuid` of the new `in_progress` session, security invoker. With a routine: name snapshot and one pending `workout_sets` row (weight 0, reps 0, `completed_at` null, `set_type` and `set_index` of the planned set) per `routine_exercise_sets` row (a routine exercise without planned sets falls back to `target_sets` generic sets), `exercise_position`/`superset_group` copied; without: name `Entreno libre`, no sets. Routine not visible: `P0002`; second in-progress session: `23505` |
| `finish_workout_session(p_session_id uuid)` | US-11 (T-044), T-096 | `void`. One transaction: folds an open pause (`now() - paused_at`) into `paused_total_s` and clears `paused_at`, deletes the pending sets and sets `completed` + `ended_at`; `P0002` if not an own `in_progress` session |
| `pause_workout_session(p_session_id uuid, p_at timestamptz)` | US-38 (T-096) | `void`, security invoker. Sets `paused_at = p_at`; idempotent (already paused keeps the first instant); `P0002` if not an own `in_progress` session; `authenticated` only |
| `resume_workout_session(p_session_id uuid, p_at timestamptz)` | US-38 (T-096) | `void`, security invoker. Adds `floor(p_at - paused_at)` seconds (never negative) to `paused_total_s` and clears `paused_at`; no-op when not paused; `P0002` if not an own `in_progress` session; `authenticated` only |
| `delete_workout_set(p_session_id uuid, p_exercise_position smallint, p_set_index smallint)` | US-38 (T-096) | `void`, security invoker. Deletes the set and shifts the later `set_index` of that exercise down by one, atomically (two steps through a +1000 offset because the upsert key is a non-deferrable unique); idempotent when the row does not exist yet; `P0002` if not an own `in_progress` session; `authenticated` only |
| `discard_workout_session(p_session_id uuid)` | US-11 (T-044) | `void`. Sets `discarded` + `ended_at` (sets are kept, history ignores them); `P0002` if not an own `in_progress` session |
| `exercise_history(p_exercise_id uuid, p_from date default null)` | US-15 (T-044) | `table(session_id, session_date, best_one_rm, total_volume)` per `completed` session (UTC date of `started_at`, `>= p_from`) with completed, sets (warm-up sets only when `warm_up_included()`, T-090) of the exercise, ordered by start. `best_one_rm`: Epley over sets with reps 1..12 and weight > 0 (reps 1 = weight), rounded to 0.5 kg, null if none; `total_volume = sum(weight_kg * reps)` |
| `weekly_muscle_volume(p_from date)` | US-15 (T-048b) | `table(week_start date, muscle_group, total_volume numeric)`, security invoker. `week_start` = Monday of the UTC week of `started_at` (`>= p_from`), only `completed` sessions, completed sets (warm-up sets only when `warm_up_included()`, T-090) (same filter as `exercise_history`), attributed to the exercise's primary `muscle_group`; `total_volume = sum(weight_kg * reps)`. Only weeks/groups with data; ordered by week, group |
| `exercise_session_records(p_exercise_id uuid)` | US-34 (T-094) | `table(session_id, started_at, heaviest_lift, best_set, session_volume, best_one_rm, total_reps bigint, best_duration_s, best_distance_m, total_steps, total_floors)` per `completed` session with completed sets of the exercise (warm-up only when `warm_up_included()`), ordered by `started_at`. `heaviest_lift` = max `weight_kg` (> 0); `best_set` = max single-set `set_volume * volume_factor` (> 0); `session_volume` and `best_one_rm` identical to `exercise_history`; `total_reps` = sum of reps; duration, distance, steps and floors = max single set; null when no data. Security invoker, `authenticated` only; unauthenticated/unknown exercise = empty |
| `personal_records(p_exercise_id uuid)` | US-34 (T-094) | `table(record_type text, value numeric, session_id uuid, session_date date)`: one row per family with value > 0, in order `heaviest_lift, best_set, max_volume, best_1rm, total_reps, best_duration, best_distance, most_steps, most_floors`. The session is the earliest one holding the best value (UTC date); `total_reps` = sum over all sessions, `session_id`/`session_date` null. Built on `exercise_session_records`; security invoker, `authenticated` only |
| `create_personal_food(p_name, p_brand, p_base_unit, p_kcal, p_protein, p_carbs, p_fat, p_barcode, p_portions jsonb, p_micronutrients jsonb default null)` (`p_micronutrients` added by T-068, whitelist widened to the 35 label keys by T-102, values 0..9999.99, else `22023`) | US-18, US-22 (T-058) | `uuid` of the new `foods` row, security invoker. One transaction: inserts a `source='user'` food owned by the caller and its portions (`p_portions` = `[{"label","amount"}]`, max 10, `position` = array order). `28000` if unauthenticated; `22023` for blank name, negative values, blank portion label, portion amount <= 0 or more than 10 portions |
| `update_personal_food(p_food_id, p_name, p_brand, p_kcal, p_protein, p_carbs, p_fat, p_portions jsonb, p_micronutrients jsonb default null, p_base_unit text default null)` | US-47 (T-102, ADR-53); unit: US-52 (T-104, ADR-62) | `void`, security invoker. Locks the caller's `source='user'` food (`P0002` if it is OFF, someone else's or missing) and updates name, brand, kcal/macros and `micronutrients`; barcode, source and owner are untouched. `p_base_unit` (`g`/`ml`, null = keep; other values `22023`) changes the base unit only when it differs from the stored one and no `food_log_entries` or `meal_template_items` row references the food (favourites do not count), otherwise the whole call rolls back with `23514` `food unit locked: referenced`; values and portions stay as sent (per 100 of the new unit). The `for update` row lock serialises against entry inserts; an uncommitted comida item (deferred FK) is not seen, accepted. Deletes and re-inserts the portions (`position` = array order) in the same transaction. Same validation as create (`22023`; `28000` if unauthenticated). Past `food_log_entries` are never touched |
| `personal_food_in_use(p_food_id uuid)` | US-52 (T-104, ADR-62) | `boolean`, security invoker, `authenticated` only. True when a `food_log_entries` or `meal_template_items` row of the caller references the food. `28000` unauthenticated; `P0002` if it is not the caller's manual food (OFF, other user, missing) |
| `save_meal_slots(p_slots jsonb)` | US-23 (T-059, T-067) | `void`, security invoker. One transaction: `p_slots` = array in final order of `{"id": uuid\|null, "label"}` (`enabled` is accepted and ignored); updates label/position (0..n-1) of the caller's existing slots, always writes `enabled = true`, and inserts those with null id (`code` null; `code` is never taken from the client). Every existing slot must be present (nothing is deleted here). `22023` for a non-array/empty input, blank or >50-char label, duplicate ids or missing existing slot; `P0002` for an id that is not the caller's. Replaces the T-059 signature with `p_viewed_day` (ADR-41) |
| `delete_meal_slot(p_id uuid)` | US-23 (T-067) | `void`, security invoker, `authenticated` only. Deletes the caller's slot and re-packs positions to 0..n-1. `28000` if unauthenticated; `P0002` if the slot is not the caller's or unknown; `23514` if it is the caller's only slot or has any `food_log_entries` row on any day |
| `save_meal_template(p_template_id uuid, p_name text, p_meal_slot_id uuid, p_items jsonb)` | US-21 (T-061); cooking extended in E7, US-50 | `uuid` (template id), security invoker, `authenticated` only. Null id creates; otherwise overwrites name/slot and replaces the items, atomically. `p_items` = `[{"food_id","amount","portion_label","cooking_method"}]` in order; a missing/none `cooking_method` is `raw`. `22023` invalid name/items/amount/method or a non-raw method on an `ml` food; `23505` name taken (case-insensitive); `P0002` template not the caller's; `42501` food not visible to the caller |
| `save_meal_defaults(p_defaults jsonb)` | E7, US-51 | `void`, security invoker, `authenticated` only. One transaction replaces all the caller's `meal_defaults`: `p_defaults` = `[{"meal_slot_id","template_id","weekdays":[1,3,5]}]`; `[]` clears them. Validates ownership of every meal and saved comida, weekday 1–7 without duplicates and a non-empty template; `22023` invalid payload, `P0002` unknown/not owned, `23514` empty template |
| `save_meal_as_comida(p_log_date date, p_meal_slot_id uuid, p_name text, p_items jsonb, p_entry_ids uuid[])` | E8, US-55 (T-117, ADR-68) | `uuid` (template id), security invoker, `authenticated` only. Atomically calls `save_meal_template(null, ...)` (same validation and errors, `23505` name taken) and then gives the caller's entries of that day and meal listed in `p_entry_ids` one new shared `group_id` with `group_name` = trimmed name; entries that already had another group leave it; ids not found or not owned are skipped. `22023` if `p_entry_ids` is null or empty; `28000` if unauthenticated. A failure leaves the entries ungrouped |
| `cooking_impact(p_method food_cooking_method, p_amount_g numeric, p_user uuid default auth.uid())` | E7, US-50 (T-107, T-112) | `table (added_fat_g, added_kcal)`, security invoker, `authenticated` only. `added_fat_g = round(p_amount_g x pct / 100, 1)`, `added_kcal = round(added_fat_g x 9, 1)`, with the user's `food_preferences` percentages (missing row = defaults 1/2/8); `raw` returns zeros. Used by `apply_meal_defaults` |
| `apply_meal_defaults(p_log_date date)` | E7, US-51 | `integer` (entries added), security invoker, `authenticated` only. Atomically inserts the `diary_default_applications` marker (`on conflict do nothing`); if the day was already evaluated, returns 0. If the day already has any `food_log_entries`, keeps the marker and returns 0. Otherwise copies the template items of the ISO weekday with their current amounts, saved cooking methods, added fat (raw amount x percentage from the current `food_preferences`) and adjusted nutrition snapshots, and returns the count. Each default (one per meal and weekday) is stamped with its own `group_id` and `group_name` = the comida's name (T-117, US-55). Idempotent: deleting the added entries never re-adds them. Dates outside UTC today ±1 day raise `22023` and write nothing (the server does not know the user's timezone; UTC-12..UTC+14 local dates always fit); `28000` if unauthenticated |
| `most_eaten_foods(p_limit int)` | US-17 | The user's most-logged foods (food_id, times logged), derived from `food_log_entries` |
| `food_diary_summary(p_from date, p_to date)` | US-24 | Per day: kcal and protein/carbs/fat totals |

Routine tracking (T-099, US-36, ADR-54), all `security invoker`:
- `set_session_rest(p_session_id uuid, p_exercise_position int, p_rest_seconds int) returns void`: upserts `exercise_rests[position]` of the caller's in-progress session (`P0002` otherwise, `22023` if seconds are outside 0-600).
- `update_routine_from_session(p_session_id uuid) returns uuid` (routine id): the session must be the caller's, `completed` and with an existing `routine_id` (`P0002`). In one transaction it replaces the routine's `routine_exercises` and `routine_exercise_sets` with the session's completed sets grouped by `exercise_position` (renumbered 0..n-1 and 1..n). Rest = session rest, else the previous rest of the same exercise, else 120; `target_rpe` is carried over. Exercises without completed sets are dropped; a session with zero completed sets is rejected (`22023`) so the routine is never emptied; more than 10 sets: `22023`; reps clamped to 1-50; superset groups with unequal set counts are nulled. It runs after `finish_workout_session`, and never touches the session or the routine's name, notes, folder, order or flags.
- `save_completed_workout(p_workout jsonb) returns uuid` (T-101, US-37): creates (no `id`) or updates (`id` of the caller's `completed` session, else `P0002`) a finished workout, never through the live session. Payload `{id?, routine_id?, name, description?, gym?, started_at (create only, not in the future, `22023`), duration_s? (0..86400), exercises: [{exercise_id, superset_group?, rest_seconds? (0-600), sets: [{set_type?, rpe? (6-10 whole), weight_kg?, reps?, duration_s?, distance_m?, steps?, floors?}]}]}`. 1-30 exercises with 1-30 sets, superset members consecutive with equal set counts (`22023`); `routine_id` must be the caller's (`P0002`). `ended_at = started_at + duration_s`, every set gets `completed_at = ended_at`; update keeps `started_at` and the existing duration unless `duration_s` is sent, and rewrites name, description, gym, `exercise_rests` and all sets atomically. Positions and `set_index` come from array order.
- `save_workout_as_routine(p_session_id uuid, p_name text) returns uuid` (T-101): creates a NEW routine (no folder, end position, default flags) from a caller's `completed` session (`P0002`), built like `update_routine_from_session` (rest = session rest else 120, no `target_rpe`). Blank/over-100 name, no completed sets or an exercise with more than 10 sets: `22023`. The session is never modified. Deleting a finished workout is a plain `DELETE` on `workout_sessions` (cascade to sets).
- `save_routine(p_routine jsonb)` also accepts an optional `track_as_routine` boolean (omitted = unchanged). The former per-routine `auto_update_routine` key is ignored (T-124, ADR-70). A duplicate name raises `23505`. Deleting a routine is a plain `DELETE` (RLS); sessions keep `routine_id = null`.

The 1RM is computed in SQL with the same formula as the domain (see `04-business-rules.md`); domain and pgTAP tests use the same cases.

E6 adds the RPCs for the PR catalogue (added in T-094: `exercise_session_records`, `personal_records`), routine folders/order, routine tracking (T-099, below), workout editing and past workouts, and the manual-food edit (US-47, ADR-53: e.g. `update_personal_food`, atomic with its portions; `create_personal_food` then accepts the full nutrient key list). The warm-up/plate calculators are client-side domain with no RPC; only their settings are stored (ADR-52). E7 adds the cooking and default-comida tables and RPCs listed above. US-39 (per-exercise charts, record markers) reads `exercise_session_records` and adds no RPC (`exercise_history` is unchanged). E8 adds the extended statistics (T-114, US-40/US-41; rules in `04-business-rules.md` §4.11), four read-only RPCs, all `security invoker`, `authenticated` only (no `anon`/`public` execute), no table change. `p_from`/`p_to` are inclusive UTC session dates, null = open bound:
- `training_summary(p_from date default null, p_to date default null) returns table(workouts bigint, sets bigint, volume numeric, duration_s bigint)`: exactly one row (zeros without data).
- `weekly_set_counts(p_from date default null, p_to date default null) returns table(week_start date, sets bigint)`: Monday of the UTC week, only weeks with data, ordered by week.
- `muscle_group_sets(p_from date default null, p_to date default null) returns table(muscle_group muscle_group, sets numeric)`: primary 1, each distinct secondary 0.5; ordered by `sets desc, muscle_group` (enum order on ties).
- `exercise_set_counts(p_from date default null, p_to date default null, p_muscle_group muscle_group default null) returns table(exercise_id uuid, sets numeric, sessions bigint)`: per exercise; with a group only exercises including it, weighted 1 / 0.5; ordered by `sets desc, sessions desc, exercise_id`.

## 7. Edge Functions

| Function | Story | Auth | Input | Behavior |
|---|---|---|---|---|
| `health` | US-02 | public (`verify_jwt = false`) | — | `GET` → `200 {"status":"ok"}` after a `select 1`; other methods → `405` (`Allow: GET`); DB failure → `503` (`db_unavailable`) |
| `delete-account` | US-05 (ADR-29) | JWT | `POST`, no body | Resolves the caller from the access token (never from the body) and deletes that auth user with the Auth admin API using the server-side secret key; user-owned rows cascade. `200 {"status":"deleted"}`; missing/invalid token or user already gone → `401` (`unauthorized`); other methods → `405` (`Allow: POST`); admin failure → `500` (`delete_failed`) |
| `food-search` | US-16 | JWT | `?q=<2..60 chars>&page=1` | 1) full-text search in `foods` (OFF + the user's own); 2) if < 10 results, query OFF (`search`, minimal fields, `lc=es`, `cc=es`), normalize and upsert by `barcode`; 3) return up to 20 normalized results (each with `base_unit`, nutrition per 100 base units and the default serving) |
| `food-barcode` | US-16 | JWT | `?code=<EAN-8/EAN-13/UPC-A>` | Look up in `foods`; if missing or `fetched_at` > 30 days, query OFF `product/<code>` (also when `off_data` is null; ADR-43); `404` if OFF does not have it or kcal is missing |
| `food-photo` | E7, US-48/US-49/US-54 | JWT | `POST` JSON `{image_base64, mode:"dish"\|"label"\|"barcode"\|"weight", comment?, food?}`. **Mode `weight` (US-54, ADR-64):** `food:{name, brand\|null, base_unit:"g"\|"ml"}` is required (kcal and macros dropped, ADR-72) (data, never instructions) and `comment` is rejected; response `200` `{estimate:{estimated_amount (>0, in the food's base unit), base_unit, confidence (integer 1-10)}}` (ADR-72; the other modes keep `"low"|"medium"|"high"|null`), `422` `NO_FOOD_RECOGNIZED` when unusable; `food` is rejected in the other modes. **Dish mode zero-fills** a missing/`null` macro or micronutrient to `0` (label and barcode keep `null`). The rest of this row describes the draft modes. | Sends the image to the Gemini vision model with a fixed prompt per mode (the comment is optional, dish-only, ≤ 200 chars and treated as data); returns a normalized draft (name, brand, base unit, per-100 kcal/macros/micronutrients, grams/confidence). Barcode mode asks the AI to identify the product and its nutrition; it does not call `food-barcode` or save the code. No photo, comment or provider body is stored or logged. Response `200` `{draft:{name, brand|null, base_unit:"g"|"ml", nutrition:{kcal,protein_g,carbs_g,fat_g} (each number|null), micronutrients:{"<offKey>_100g": grams per 100 base units}, estimated_grams|null (dish only), confidence:"low"|"medium"|"high"|null}}`; negatives clamp to 0 and unknown keys are dropped. Errors `{error:{code,message}}`: `400` `INVALID_INPUT`, `401` `UNAUTHORIZED`, `405` `METHOD_NOT_ALLOWED`, `413` `PAYLOAD_TOO_LARGE` (decoded image > 4 MB), `422` `NO_FOOD_RECOGNIZED`, `429` `QUOTA_EXCEEDED`, `502` `PROVIDER_ERROR`, `503` `NOT_CONFIGURED` (`GEMINI_API_KEY` missing), `500` `INTERNAL` |

Shared (`functions/_shared/`): OFF client with `User-Agent: AppFitness/<version> (<contact email>)`, 8 s timeout, normalization (base unit from `nutrition_data_per`: `100 g` → `g`, `100 ml` → `ml`; kcal from `energy-kcal_100g`/`energy-kcal_100ml`, or `energy_100g / 4.184` if only kJ is available; default serving from `serving_size`), input validation and JSON error responses `{ "error": { "code", "message" } }`.

## 8. Relationship diagram

```
auth.users 1─1 profiles
auth.users 1─1 nutrition_goals
auth.users 1─1 health_sync_state
auth.users 1─n meal_slots
auth.users 1─n food_log_entries n─1 foods
auth.users 1─n food_log_entries n─1 meal_slots
auth.users 1─n favorite_foods n─1 foods
foods 1─n food_portions
auth.users 1─n meal_templates n─1 meal_slots
auth.users 1─n meal_templates 1─n meal_template_items n─1 foods
auth.users 1─1 food_preferences
auth.users 1─n meal_defaults n─1 meal_slots
auth.users 1─n meal_defaults n─1 meal_templates
auth.users 1─n diary_default_applications
auth.users 1─n body_measurements
auth.users 1─n body_circumferences
auth.users 1─n routine_folders 1─n routines 1─n routine_exercises n─1 exercises
auth.users 1─n workout_sessions 1─n workout_sets n─1 exercises
```
