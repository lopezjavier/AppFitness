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
| target_weight_kg | numeric(5,1) null | 20–400 (check); goal weight, no date (E5) |
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
| micronutrients | jsonb null | `source = 'user'` only: optional label values typed by the user (`saturated-fat_100g`, `sugars_100g`, `fiber_100g`, `salt_100g`; ADR-43) |
| off_data | jsonb null | complete OFF product as received (`source = 'off'` only; ADR-43); written by Edge Functions, never used to compute entries, not returned by `food-search` |
| fetched_at | timestamptz | cache refresh |

RLS:
- select: `source = 'off' or created_by = (select auth.uid())`.
- insert/update/delete from the client: only `source = 'user' and created_by = (select auth.uid())`.
- `off` rows are only written by Edge Functions using the Supabase secret key (legacy `service_role`).

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

### `food_log_entries` (US-16; used in US-17…US-24)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| log_date | date | Local day |
| meal_slot_id | uuid FK meal_slots | composite FK `(meal_slot_id, user_id)` -> `meal_slots (id, user_id)`; on delete no action (US-23) |
| food_id | uuid FK foods | on delete no action |
| amount | numeric(7,1) | > 0; in the food's `base_unit` |
| portion_label | text null | snapshot of the portion used, if any |
| food_name | text | snapshot |
| kcal, protein_g, carbs_g, fat_g | numeric(7,1) | snapshot calculated for the amount (does not change if the food changes) |

Index: `(user_id, log_date)`.

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
`id`, `user_id` (FK auth.users, cascade), `name` (1..60 chars after trim; unique per user on `lower(btrim(name))`, so "desayuno a" and "Desayuno A" collide), `meal_slot_id` null (suggested meal; null = any), `created_at`. Composite FK `(meal_slot_id, user_id) -> meal_slots (id, user_id) on delete set null (meal_slot_id)`: deleting a meal keeps the template (ADR-41). UI calls them "comidas" (ADR-42).

### `meal_template_items` (US-21)
`id`, `template_id` FK (on delete cascade), `food_id` FK (no action, deferred to commit so deleting a user with personal foods works), `amount numeric(7,1)` (base unit, `> 0 and <= 5000`), `portion_label` null, `position smallint` (`unique (template_id, position)`).
RLS via `exists (select 1 from meal_templates t where t.id = template_id and t.user_id = (select auth.uid()))`.

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
| is_custom | boolean | |
| created_by | uuid null | custom only |

Catalog: one row per RepDB free exercise (`key`, `muscle_group`, `equipment`, `metric_type = weight_reps`, no name), loaded by a generated migration (`supabase/migrations/*_exercise_catalog.sql`, ADR-39), not by `seed.sql`. Consistency check: custom => `created_by` and `name` set (1-100 chars), `key` null; catalog => `key` set, `created_by` and `name` null. Names, instructions and images are **not** stored here: they live in the client asset bundle (`composeApp/src/commonMain/composeResources/files/exercises/`, ADR-30, ADR-39). RLS select: `not is_custom or created_by = (select auth.uid())`; writes only on own custom exercises.

### `routine_folders` (E6)
`user_id`, `name`, `position`. RLS owner policy. Deleting a folder moves its routines to the default folder (never deletes them).

### `routines` (US-10, extended in E6)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| name | text | |
| notes | text null | |
| archived | boolean | default false |
| pinned_note | text null | Shown every time the routine is logged *(E6)* |
| folder_id | uuid null FK routine_folders | *(E6)*; null = "Mis rutinas" |
| track_as_routine | boolean | default true *(E6)* |
| auto_update_routine | boolean | default false *(E6)* |
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
| target_rpe | numeric(3,1) null | 1–10 *(E6)* |
| planned_set_type | set_type | default `working` *(E6)* |
| target_weight_kg | numeric(6,2) null | *(E6)* |
| rest_seconds | smallint | default 120, 0–600 |

RLS via the parent routine.

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
| rpe | numeric(3,1) null | 1–10 *(E6)* |
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
| `save_preferences(p_display_name, p_sex, p_birth_date, p_height_cm, p_activity_level, p_strategy, p_calorie_adjustment, p_target_kcal, p_protein_pct, p_carbs_pct, p_fat_pct, p_weight_kg default null, p_measured_at default null, p_day_start default null, p_day_end default null)` | US-06/07/08 (T-041, ADR-24, ADR-37; replaces `save_profile_settings` from T-037) | `void`, security invoker. One transaction: updates the caller's `profiles` name (trimmed) and biometric fields, upserts their `nutrition_goals` row and, when `p_weight_kg` is set, replaces the latest `manual` `body_measurements` row with `measured_at` in `[p_day_start, p_day_end)` or inserts one. Weight arguments are all-or-none (`22023` otherwise); `P0002` if the caller has no profile row; table checks surface as `23514` |
| `save_body_measurement(p_id uuid default null, p_weight_kg, p_body_fat_pct default null, p_measured_at, p_day_start, p_day_end)` | US-25 (T-076) | `uuid` of the written row, security invoker, `authenticated` only. Caller sends the bounds of the local day. `p_id` null: replaces the latest `manual` row with `measured_at` in `[p_day_start, p_day_end)` (weight, fat, time; null fat clears it) or inserts one; Health Connect rows are ignored. `p_id` set: the caller's own `manual` row only (`P0002` otherwise: other user, Health Connect, unknown), whose current `measured_at` must lie in the bounds, so the date never changes. `22023` for missing/invalid bounds or `p_measured_at` outside them; `28000` unauthenticated; ranges surface as `23514`. Delete is a plain `DELETE` under the owner policy |
| `last_performance(p_exercise_id uuid)` | US-12 | Sets (`set_index`, `weight_kg`, `reps`) of the user's most recent completed session containing that exercise |
| `start_workout_session(p_routine_id uuid default null)` | US-11 (T-044) | `uuid` of the new `in_progress` session, security invoker. With a routine: name snapshot and one pending `workout_sets` row (weight 0, reps 0, `completed_at` null) per target set, `exercise_position`/`superset_group` copied; without: name `Entreno libre`, no sets. Routine not visible: `P0002`; second in-progress session: `23505` |
| `finish_workout_session(p_session_id uuid)` | US-11 (T-044) | `void`. One transaction: deletes the pending sets and sets `completed` + `ended_at`; `P0002` if not an own `in_progress` session |
| `discard_workout_session(p_session_id uuid)` | US-11 (T-044) | `void`. Sets `discarded` + `ended_at` (sets are kept, history ignores them); `P0002` if not an own `in_progress` session |
| `exercise_history(p_exercise_id uuid, p_from date default null)` | US-15 (T-044) | `table(session_id, session_date, best_one_rm, total_volume)` per `completed` session (UTC date of `started_at`, `>= p_from`) with completed, non-warm-up sets of the exercise, ordered by start. `best_one_rm`: Epley over sets with reps 1..12 and weight > 0 (reps 1 = weight), rounded to 0.5 kg, null if none; `total_volume = sum(weight_kg * reps)` |
| `weekly_muscle_volume(p_from date)` | US-15 (T-048b) | `table(week_start date, muscle_group, total_volume numeric)`, security invoker. `week_start` = Monday of the UTC week of `started_at` (`>= p_from`), only `completed` sessions, completed non-warm-up sets (same filter as `exercise_history`), attributed to the exercise's primary `muscle_group`; `total_volume = sum(weight_kg * reps)`. Only weeks/groups with data; ordered by week, group |
| `create_personal_food(p_name, p_brand, p_base_unit, p_kcal, p_protein, p_carbs, p_fat, p_barcode, p_portions jsonb, p_micronutrients jsonb default null)` (`p_micronutrients` added by T-068: only the four label keys, values >= 0, else `22023`) | US-18, US-22 (T-058) | `uuid` of the new `foods` row, security invoker. One transaction: inserts a `source='user'` food owned by the caller and its portions (`p_portions` = `[{"label","amount"}]`, max 10, `position` = array order). `28000` if unauthenticated; `22023` for blank name, negative values, blank portion label, portion amount <= 0 or more than 10 portions |
| `save_meal_slots(p_slots jsonb)` | US-23 (T-059, T-067) | `void`, security invoker. One transaction: `p_slots` = array in final order of `{"id": uuid\|null, "label"}` (`enabled` is accepted and ignored); updates label/position (0..n-1) of the caller's existing slots, always writes `enabled = true`, and inserts those with null id (`code` null; `code` is never taken from the client). Every existing slot must be present (nothing is deleted here). `22023` for a non-array/empty input, blank or >50-char label, duplicate ids or missing existing slot; `P0002` for an id that is not the caller's. Replaces the T-059 signature with `p_viewed_day` (ADR-41) |
| `delete_meal_slot(p_id uuid)` | US-23 (T-067) | `void`, security invoker, `authenticated` only. Deletes the caller's slot and re-packs positions to 0..n-1. `28000` if unauthenticated; `P0002` if the slot is not the caller's or unknown; `23514` if it is the caller's only slot or has any `food_log_entries` row on any day |
| `save_meal_template(p_template_id uuid, p_name text, p_meal_slot_id uuid, p_items jsonb)` | US-21 (T-061) | `uuid` (template id), security invoker, `authenticated` only. Null id creates; otherwise overwrites name/slot and replaces the items, atomically. `p_items` = `[{"food_id","amount","portion_label"}]` in order. `22023` invalid name/items/amount; `23505` name taken (case-insensitive); `P0002` template not the caller's; `42501` food not visible to the caller |
| `most_eaten_foods(p_limit int)` | US-17 | The user's most-logged foods (food_id, times logged), derived from `food_log_entries` |
| `food_diary_summary(p_from date, p_to date)` | US-24 | Per day: kcal and protein/carbs/fat totals |

The 1RM is computed in SQL with the same formula as the domain (see `04-business-rules.md`); domain and pgTAP tests use the same cases.

E6 adds the RPCs for the PR catalogue, the warm-up/plate calculators and routine tracking (`Update Routine`); E7 adds the extended statistics (time frames, sets per muscle group, current-vs-previous distribution). Their signatures are fixed when those epics are broken down.

## 7. Edge Functions

| Function | Story | Auth | Input | Behavior |
|---|---|---|---|---|
| `health` | US-02 | public (`verify_jwt = false`) | — | `GET` → `200 {"status":"ok"}` after a `select 1`; other methods → `405` (`Allow: GET`); DB failure → `503` (`db_unavailable`) |
| `delete-account` | US-05 (ADR-29) | JWT | `POST`, no body | Resolves the caller from the access token (never from the body) and deletes that auth user with the Auth admin API using the server-side secret key; user-owned rows cascade. `200 {"status":"deleted"}`; missing/invalid token or user already gone → `401` (`unauthorized`); other methods → `405` (`Allow: POST`); admin failure → `500` (`delete_failed`) |
| `food-search` | US-16 | JWT | `?q=<2..60 chars>&page=1` | 1) full-text search in `foods` (OFF + the user's own); 2) if < 10 results, query OFF (`search`, minimal fields, `lc=es`, `cc=es`), normalize and upsert by `barcode`; 3) return up to 20 normalized results (each with `base_unit`, nutrition per 100 base units and the default serving) |
| `food-barcode` | US-16 | JWT | `?code=<EAN-8/EAN-13/UPC-A>` | Look up in `foods`; if missing or `fetched_at` > 30 days, query OFF `product/<code>` (also when `off_data` is null; ADR-43); `404` if OFF does not have it or kcal is missing |

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
auth.users 1─n body_measurements
auth.users 1─n body_circumferences
auth.users 1─n routine_folders 1─n routines 1─n routine_exercises n─1 exercises
auth.users 1─n workout_sessions 1─n workout_sets n─1 exercises
```
