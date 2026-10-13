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
create type meal_type as enum ('breakfast', 'lunch', 'snack', 'dinner');   -- UI: Desayuno, Comida, Merienda, Cena
create type food_source as enum ('off', 'user');
create type measurement_source as enum ('manual', 'health_connect');
create type muscle_group as enum ('chest', 'back', 'shoulders', 'biceps', 'triceps', 'forearms',
                                  'quads', 'hamstrings', 'glutes', 'calves', 'core', 'full_body');
create type equipment as enum ('barbell', 'dumbbell', 'machine', 'cable', 'bodyweight', 'kettlebell', 'band', 'other');
create type session_status as enum ('in_progress', 'completed', 'discarded');
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
| onboarding_completed | boolean | default false |

- `on_auth_user_created` trigger (security definer) that inserts the row with name and avatar from `raw_user_meta_data`. The metadata is user-controlled, so the trigger sanitizes it and sign-up never fails on a constraint:
  - keys are read only from a JSON object and only when the value is a JSON string;
  - `display_name`: `full_name`, then `name`, then the email local part, then `Usuario`; each candidate is trimmed and capped at 100 characters, blank ones are skipped;
  - `avatar_url`: `avatar_url`, then `picture`; kept only if it starts with `https://` and has at most 2048 characters, otherwise null.
- Client privileges: `SELECT` and column-level `UPDATE` on the user-editable columns only (`display_name`, `avatar_url`, `birth_date`, `sex`, `height_cm`, `activity_level`, `onboarding_completed`); `id`, `created_at` and `updated_at` are not client-writable (`updated_at` is set by the `set_updated_at()` trigger).
- RLS: owner by `id = (select auth.uid())`. No delete from the client.
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

### `foods` (US-09)
Shared catalog (OFF cache) + personal foods.
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| source | food_source | |
| barcode | text null | unique when `source = 'off'` |
| off_id | text null | OFF product code |
| name, brand | text | |
| kcal_100g, protein_100g, carbs_100g, fat_100g | numeric(6,2) | ≥ 0 |
| serving_size_g | numeric(6,1) null | Suggested serving |
| image_url | text null | OFF URL (images are not stored) |
| created_by | uuid null | only for `source = 'user'` |
| search_vector | generated tsvector | GIN index; `unaccent` + `spanish` config (food names are Spanish) |
| fetched_at | timestamptz | cache refresh |

RLS:
- select: `source = 'off' or created_by = (select auth.uid())`.
- insert/update/delete from the client: only `source = 'user' and created_by = (select auth.uid())`.
- `off` rows are only written by Edge Functions using the Supabase secret key (legacy `service_role`).

### `food_log_entries` (US-09; used in US-10…US-14)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| log_date | date | Local day |
| meal_type | meal_type | |
| food_id | uuid FK foods | on delete restrict |
| quantity_g | numeric(7,1) | > 0 |
| food_name | text | snapshot |
| kcal, protein_g, carbs_g, fat_g | numeric(7,1) | snapshot calculated for the quantity (does not change if the food changes) |

Index: `(user_id, log_date)`.

### `meal_templates` (US-14)
`user_id`, `name` (unique per user), suggested `meal_type`.

### `meal_template_items` (US-14)
`template_id` FK (on delete cascade), `food_id` FK, `quantity_g`, `position`.
RLS via `exists (select 1 from meal_templates t where t.id = template_id and t.user_id = (select auth.uid()))`.

## 4. Weight

### `body_measurements` (US-06 creates the first row; US-15, US-16)
| Column | Type | Notes |
|---|---|---|
| user_id | uuid | |
| measured_at | timestamptz | |
| weight_kg | numeric(5,2) | 20–400 |
| body_fat_pct | numeric(4,1) null | 2–75 |
| source | measurement_source | |
| external_id | text null | Health Connect record id; `unique (user_id, external_id)` |

Index: `(user_id, measured_at desc)`.

Onboarding inserts one `manual` row; finishing the onboarding weight step again updates that row instead of inserting another (ADR-24).

### `health_sync_state` (US-16)
`user_id` PK, `last_synced_at timestamptz`. (Per user, not per device: enough for Phase 1.)

## 5. Training

### `exercises` (US-18)
| Column | Type | Notes |
|---|---|---|
| name | text | In Spanish (user-facing data) |
| muscle_group | muscle_group | Primary group |
| equipment | equipment | |
| is_custom | boolean | |
| created_by | uuid null | custom only |

Seed: ~80 common exercises with Spanish names. RLS select: `not is_custom or created_by = (select auth.uid())`; writes only on own custom exercises.

### `routines` (US-19)
`user_id`, `name`, `notes`, `archived boolean`.

### `routine_exercises` (US-19)
| Column | Type | Notes |
|---|---|---|
| routine_id | uuid FK | on delete cascade |
| exercise_id | uuid FK | |
| position | smallint | order |
| superset_group | smallint null | consecutive exercises with the same value form a superset |
| target_sets | smallint | 1–10 |
| target_reps | smallint | 1–50 |
| rest_seconds | smallint | default 120, 0–600 |

RLS via the parent routine.

### `workout_sessions` (US-20)
`user_id`, `routine_id` null, `name` (snapshot), `status session_status`, `started_at`, `ended_at` null.
Constraint: at most one `in_progress` session per user (partial unique index).

### `workout_sets` (US-20)
| Column | Type | Notes |
|---|---|---|
| session_id | uuid FK | on delete cascade |
| exercise_id | uuid FK | |
| exercise_position | smallint | exercise order within the session |
| superset_group | smallint null | |
| set_index | smallint | 1..n within the exercise |
| weight_kg | numeric(6,2) | ≥ 0 (for weighted pull-ups = added kg) |
| reps | smallint | ≥ 0 |
| completed_at | timestamptz null | null = pending set |

Constraint `unique (session_id, exercise_position, set_index)` (upsert key from the live session). RLS via the parent session.

## 6. RPC functions (Postgres, `security invoker`)

| Function | Story | Returns |
|---|---|---|
| `last_performance(p_exercise_id uuid)` | US-21 | Sets (`set_index`, `weight_kg`, `reps`) of the user's most recent completed session containing that exercise |
| `exercise_history(p_exercise_id uuid, p_from date)` | US-24 | Per session: date, best estimated 1RM, total volume |
| `weekly_muscle_volume(p_from date)` | US-24 | Per ISO week and muscle group: total volume |

The 1RM is computed in SQL with the same formula as the domain (see `04-business-rules.md`); domain and pgTAP tests use the same cases.

## 7. Edge Functions

| Function | Story | Auth | Input | Behavior |
|---|---|---|---|---|
| `health` | US-02 | public (`verify_jwt = false`) | — | `GET` → `200 {"status":"ok"}` after a `select 1`; other methods → `405` (`Allow: GET`); DB failure → `503` (`db_unavailable`) |
| `food-search` | US-09 | JWT | `?q=<2..60 chars>&page=1` | 1) full-text search in `foods` (OFF + the user's own); 2) if < 10 results, query OFF (`search`, minimal fields, `lc=es`, `cc=es`), normalize and upsert by `barcode`; 3) return up to 20 normalized results |
| `food-barcode` | US-09 | JWT | `?code=<EAN-8/EAN-13/UPC-A>` | Look up in `foods`; if missing or `fetched_at` > 30 days, query OFF `product/<code>`; `404` if OFF does not have it or kcal is missing |

Shared (`functions/_shared/`): OFF client with `User-Agent: AppFitness/<version> (<contact email>)`, 8 s timeout, normalization (kcal from `energy-kcal_100g`, or `energy_100g / 4.184` if only kJ is available), input validation and JSON error responses `{ "error": { "code", "message" } }`.

## 8. Relationship diagram

```
auth.users 1─1 profiles
auth.users 1─1 nutrition_goals
auth.users 1─1 health_sync_state
auth.users 1─n food_log_entries n─1 foods
auth.users 1─n meal_templates 1─n meal_template_items n─1 foods
auth.users 1─n body_measurements
auth.users 1─n routines 1─n routine_exercises n─1 exercises
auth.users 1─n workout_sessions 1─n workout_sets n─1 exercises
```
