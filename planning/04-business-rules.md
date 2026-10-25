# Business rules and formulas

All these rules are implemented as pure functions in `shared/domain/usecase/` with unit tests that include the examples on this page. The examples in the Gherkin stories must be consistent with them.

## 1. Energy and macros

### 1.1 Constants
- Protein = 4 kcal/g · Carbohydrates = 4 kcal/g · Fat = 9 kcal/g.

### 1.2 Basal metabolic rate (BMR) — Mifflin-St Jeor
- Male: `BMR = 10·weight_kg + 6.25·height_cm − 5·age + 5`
- Female: `BMR = 10·weight_kg + 6.25·height_cm − 5·age − 161`
- `age` in full years from `birth_date` and today's date.
- `weight_kg` = latest `body_measurements` entry.

### 1.3 Total daily energy expenditure (TDEE)
`TDEE = round(BMR × factor)`

| Level | UI title (Spanish) | UI description (Spanish) | Factor |
|---|---|---|---|
| sedentary | Sedentario | Trabajo sentado y poco o ningún ejercicio | 1.2 |
| light | Ligero | Ejercicio 1–3 días por semana, o trabajo sentado pero caminas a diario | 1.375 |
| moderate | Moderado | Ejercicio 3–5 días por semana | 1.55 |
| high | Alto | Ejercicio intenso 6–7 días por semana | 1.725 |
| very_high | Muy alto | Entrenas dos veces al día o tienes un trabajo físico exigente | 1.9 |

Each level is a selectable card showing the title and the description, so the user can tell the levels apart without guessing what counts.

Example: male, 26 years, 178 cm, 74.2 kg, moderate → BMR = 742 + 1112.5 − 130 + 5 = 1729.5 → TDEE = round(2680.7) = **2681 kcal**.

### 1.4 Calorie target
- `target_kcal = TDEE + calorie_adjustment`.
- Default adjustment when switching strategy: Deficit **−400**, Maintenance **0**, Surplus **+250**.
- The user can change the adjustment in 50 kcal steps within the strategy range: Deficit [−1000, −50], Maintenance [−100, +100], Surplus [+50, +750].
- Safety floor by sex (ADR-25): `target_kcal ≥ 1200` for women and `≥ 1500` for men; if the calculation gives less, it is set to the floor and a warning is shown.
- Adjustment label: |adjustment| ≤ 300 → "ligero" (light); ≤ 600 → "moderado" (moderate); > 600 → "agresivo" (aggressive) (e.g. "−400 kcal · Déficit moderado").
- The target is **only recalculated when the user saves in Profile** (the screen's single "Guardar" button, ADR-24), using the latest weight, and the new value is stored in `nutrition_goals`. Logging a new weight does not change the target automatically (that will be Phase 2's adaptive adjustment).

### 1.5 Macro split (auto-balance)
- Integer percentages, each in [5, 90], always adding up to 100. Default: P 30 / C 45 / F 25.
- When a slider moves from `x` to `x'` (Δ = x' − x), −Δ is distributed between the other two **proportionally to their current value**, rounded with the largest-remainder method so the sum is exactly 100. If one of the others reaches 5, the rest of Δ goes to the other; if both are at 5, the moved slider cannot go higher.
- Grams = `round(target_kcal × pct / 100 / kcal_per_gram)`.

Example (target 2400): P 30 / C 45 / F 25 → 180 g / 270 g / 67 g. Raising P to 35 (Δ = +5): C drops 5·45/70 = 3.21 → 41.79 and F drops 5·25/70 = 1.79 → 23.21; largest remainder → **C 42 / F 23**. Result 35/42/23 → 210 g / 252 g / 61 g.

## 2. Diary

- Every food has a **base unit** (`g` for solids, `ml` for drinks) and its nutrition is expressed per 100 base units.
- Entry values: `kcal = kcal_100 × amount / 100` (same for macros), where `amount` is in the food's base unit, rounded to 0.1; stored as a snapshot. Editing a manual food or its portions (US-47) applies to future entries only; past snapshots never change (ADR-53).
- Nutrients are entered and listed in the Spanish label order (EU Reg. 1169/2011 Annex XV): energy, fat, saturates, mono-unsaturates, polyunsaturates, carbohydrates, sugars, polyols, starch, fibre, protein, salt, then vitamins and minerals (Annex XIII order); absent values are hidden (ADR-53).
- A named portion resolves to an `amount` in base units (e.g. "1 huevo" = 55 g); logging a portion stores the resolved amount and a `portion_label` snapshot.
- Meal and day totals = sum of snapshots. In the UI, kcal and the base unit without decimals.
- `remaining = target_kcal − consumed`. If negative, "+N kcal en exceso" (over) is shown with alert styling and the ring in its over-target state.
- Progress for each macro: `round(consumed_g / target_g × 100)` %. The bar fills to 100% at most; the text shows the real value (may exceed 100).
- kcal come from the food label; they may not exactly match 4P + 4C + 9F (fiber, alcohol). Story examples do add up for clarity.

Example (target 2400 kcal; 180 g P / 270 g C / 67 g F): consumed 142 g P, 210 g C, 48 g F = 1840 kcal → "1.840 / 2.400 kcal", "560 restantes", P 79%, C 78%, F 72%.

### 2.1 Diary history (US-24)
- Per-day summary = the day's kcal and P/C/F totals (the same sums as the Diary).
- Weekly average = the average of the day totals over the logged days in the last 7 days (days without entries are not counted).
- Monthly total = the sum of the day totals in the calendar month; its average uses the logged days only.

### 2.2 Cooking methods (E7, US-50)

Only foods whose base unit is `g` are cooked; drinks stay raw. The default is always **Crudo**.

Cooking adds fat as a percentage of the **raw weight** (ADR-65).

| Method | Added fat (default, % of raw weight) | Added fat for 300 g |
|---|---|---|
| Crudo | 0 % | 0 g |
| A la plancha | 1 % | 3 g (+27 kcal) |
| Air fryer | 2 % | 6 g (+54 kcal) |
| Frito | 8 % | 24 g (+216 kcal) |

- `added_fat_g = round(raw_g × pct / 100, 1)`; `added_kcal = round(9 × added_fat_g, 1)`.
- Protein, carbohydrates and micronutrients do not change.
- The entry snapshot stores the adjusted `kcal` and `fat_g` plus `cooking_method` and `cooking_oil_retained_g` (the added fat in grams); the food's per-100 values are never modified.
- Saved comidas store a method per item (`meal_template_items.cooking_method`, default raw), editable when saving or editing a comida. Adding a comida from the "Comidas" tab, copying it or applying a default uses that method and computes the added fat from the item's amount and the current Preferencias; the template does not store the fat.
- Percentages are configured in Preferencias → "Cocina" (0–20 %). A change applies to future entries only; past entries are never recalculated.

Example: 200 g of chicken at 110 kcal and 3 g fat per 100 g = 220 kcal and 6 g fat. Frito at 8 % → 16 g added fat, +144 kcal → **364 kcal** and 6 + 16 = **22 g fat**.

### 2.3 Default comidas (E7, US-51)

- A default comida is a **saved comida** (`meal_templates` + items), never a single food or an Open Food Facts result. The meal slot assigned in the default is authoritative; the saved comida's suggested slot is ignored.
- One default comida per meal and ISO weekday (Monday = 1 … Sunday = 7); the same saved comida may cover several days or meals. Days already assigned to another comida for that meal cannot be selected.
- The app evaluates **today only**, on the first load of the Diary, before reading entries:
  - If `diary_default_applications` already has the day, nothing happens (no duplicate, and deleted entries never come back).
  - If the day already has any entry, the day is marked and nothing is added.
  - Otherwise the matching saved comidas are copied as normal entries, with their current amounts, saved cooking methods, added fat from the current Preferencias and nutrition snapshots, and the day is marked.
- The server rejects dates more than one day from UTC today (`22023`); the client only sends its own today.
- No backfill and no past days: a day the app was not opened on stays empty. Configuring or changing defaults after today's Diary was opened applies from the next new day.
- Deleting a meal or a saved comida removes its assignments (cascade); entries already logged do not change. Editing a saved comida does not change entries already added, and a future application uses its current items and cooking methods.
- If the apply call fails, the Diary still opens and shows a non-blocking retry; retrying is safe because the marker makes it idempotent.

## 3. Weight

### 3.1 Daily value
- If there are several measurements on the same local day, the day's value is their average.

### 3.2 7-day moving average (MA7)
- `MA7(d)` = average of the available daily values in `[d − 6, d]`. If there are none, there is no point.
- A single day with data is enough (ADR-49: sparse weighing); the chart connects consecutive points across gaps.

### 3.3 Weekly rate of change
- `rate = (w_last − w_prev) / days_between × 7`, in kg/week with 2 decimals, where `w_last` and `w_prev` are the daily values of the last two days with data (ADR-49). Example: 79,4 kg on the 19th and 79,5 kg on the 14th → −0,14 kg/week.
- With fewer than 2 days with data → "Sin datos suficientes" (not enough data).
- Color by goal:
  - Deficit: rate < 0 → green; rate ≥ 0 → red.
  - Surplus: rate > 0 → green; rate ≤ 0 → red.
  - Maintenance: |rate| ≤ 0.20 → green; otherwise → amber.

### 3.4 Chart filters
1W = 7 days · 1M = 30 · 3M = 90 (default) · 6M = 180 · 1Y = 365, counted back from today (UI labels: 1S, 1M, 3M, 6M, 1A). The MA7 of the first day in the range uses data before the range when available.

### 3.5 Target weight
- `distance = current weight − target_weight`, in kg with 1 decimal. The wording depends on the strategy (user decision 2026-10-21, E5 Q3):
  - Deficit: distance > 0 → "faltan X kg"; distance ≤ 0 → "objetivo alcanzado".
  - Surplus: distance < 0 → "faltan X kg"; distance ≥ 0 → "objetivo alcanzado".
  - Maintenance: distance = 0,0 → "objetivo alcanzado"; > 0 → "X kg por encima del objetivo"; < 0 → "X kg por debajo del objetivo".
  - X is the absolute distance. Example: Deficit, target 70,0, current 74,2 → "faltan 4,2 kg"; current 69,5 → "objetivo alcanzado".
- Progress bar: `progress = (start − current) / (start − target)`, clamped to 0–100 % and shown as an integer, where `start` is the user's first weight in the app (the onboarding row, `03-data-model.md` §4). 100 % once the target is reached; no bar when `start = target`. Example: start 78,0, target 70,0, current 74,2 → 48 %.
- "Current weight" is the latest `body_measurements` row. There is no date or estimated time to reach the target.

### 3.6 BMI
- `BMI = weight_kg / (height_cm / 100)²`, rounded to 1 decimal. Derived, never stored.
- If the profile has no height, BMI is not shown.

### 3.7 Body composition via Health Connect
- Health Connect records map to `body_measurements` columns: weight → `weight_kg`, body fat → `body_fat_pct`, lean body mass → `lean_mass_kg`, body water mass → `water_kg`, bone mass → `bone_kg`, basal metabolic rate → `bmr_kcal`.
- **Read:** a weight and a body-fat record with the same instant (± 2 min) are stored as a single measurement; the composition records are merged into the matching weight row by instant. Rows already imported (`external_id`) are never duplicated.
- **Write:** when the user logs a measurement manually and has granted the write permission, the app writes weight and body fat (and any available composition values) to Health Connect, so other apps see the user's data. Without the permission, writing is skipped silently.
- **Edit and delete (user decision 2026-10-21, E5 Q2):** the app stores the id of the record it wrote. Editing that manual measurement updates the Health Connect copy, and deleting it deletes the copy. A measurement saved without the permission has no copy and is not written later. A failed Health Connect call never blocks the app's change.
- **Imported readings** deleted in the app are removed from the app only (an app can only delete its own Health Connect records) and are not re-imported. Records written by this app are skipped on import.
- Sync is manual (the "Sincronizar" button); there is no background sync.

### 3.8 Metric interpretation (informative)
Every interpreted metric shows a value, a status and a short explanation. Ranges are for adults and are **informative only**; the screen carries the disclaimer "Este producto no es un dispositivo médico; los resultados no sirven para diagnóstico".

- BMI: `< 18,5` underweight · `18,5–24,9` normal · `25,0–29,9` overweight · `≥ 30` obese.
- Body fat % (adult): men `< 10` low · `10–20` normal · `20–25` high · `> 25` very high; women `< 18` low · `18–28` normal · `28–32` high · `> 32` very high.
- Shared edge values belong to the lower band (body fat men 20,0 normal, 25,0 high; women 28,0 normal, 32,0 high); BMI compares on the rounded value.
- Waist-to-hip ratio (§3.9): men `≤ 0,90` low risk · `> 0,90` high; women `≤ 0,85` low · `> 0,85` high.

### 3.9 Waist-to-hip ratio (WHR)
- `WHR = latest waist_cm / latest hip_cm`, rounded to 2 decimals. Requires both a `waist` and a `hip` measurement.
- Show "Sin datos suficientes" if either is missing.

## 4. Training

### 4.1 Estimated 1RM — Epley
- `1RM = weight × (1 + reps / 30)` for 2 ≤ reps ≤ 12; if reps = 1 → `1RM = weight`.
- Sets with reps = 0, reps > 12 or weight = 0 do not count for the 1RM.
- Rounded to 0.5 kg.
- A session's 1RM for an exercise = maximum over its completed sets.

Example: 100 kg × 5 → 116.67 → **116.5 kg**; 85 kg × 8 → 107.67 → **107.5 kg**.

### 4.2 Volume
- `volume = Σ weight × reps` over completed sets (`completed_at` not null). Bodyweight, assisted and weighted exercises follow §4.7 (latest body weight ± the added or assisted load; `weighted` counts the added load only); duration and distance exercises have volume 0.
- Weekly volume per muscle group: grouped by ISO week (Monday–Sunday) and the exercise's `muscle_group`.

### 4.3 Ghost data (previous performance)
- For each exercise in the current session: sets from the most recent **completed** session containing that exercise (`last_performance`).
- Matched by `set_index`; if the previous session had fewer sets, "—" is shown.
- Format: "85 kg × 8".
- The KG/REPS fields of a new set are prefilled with the ghost data as a placeholder (dim text, not as a value).
- A pending set started from a planned routine set (E6, ADR-50) shows the planned weight and reps as the placeholder instead; without a planned weight, the ghost placeholder applies. The ANTERIOR column always shows the previous performance.

### 4.4 Rest timer
- Duration = the exercise's `rest_seconds` in the routine (default 120 s; 0 = no timer). For exercises added during the session, 120 s.
- Starts when a set is checked as completed; checking another set restarts it.
- "+30s" adds 30 s; "Omitir" (skip) stops it.
- Computed from an end instant (`endsAt`), not by decrementing a counter, so it stays correct when the app goes to the background.
- At 0: vibration + short sound (unless the system is muted).
- In a superset, the timer only starts after completing the set of the last exercise in the group.

### 4.5 Personal records (PR) in the summary
- An exercise is marked as PR in a session if its session estimated 1RM exceeds the maximum of all previous sessions.

### 4.6 Set types and warm-up inclusion
- Every set has a type: `working` (Normal), `warm_up` (Calentamiento), `failure` (Fallo) or `drop` (Drop).
- By default **volume, 1RM and PRs ignore `warm_up` sets**; `working`, `failure` and `drop` count. The setting "Incluir calentamiento" (`warmUpIncluded`) makes warm-up sets count too, and toggling it **recalculates** the affected aggregates (past and future). Total sets and reps in the session header follow the same setting.
- The type can be assigned and removed while planning (routine) and while logging.

### 4.7 Volume options
- **Per-hand exercises** (`perHandVolume`): when the logged weight is per hand/side, `volume = 2 × weight × reps`. It is a flag on the exercise (`exercises.per_hand`, ADR-56): set for the catalogue by migration (any equipment, including machines and cables) and chosen by the user for manual exercises (editable later); it applies to past and future sessions and to the muscle volume, and does not change the 1RM (weight per hand). Example: 20 kg × 10 → 400 kg per hand, 200 kg without the flag.
- **Bodyweight** (`bodyweight`/`assisted` metric types): `volume = (latest body weight ± added/assisted weight) × reps`; the latest body weight is the newest `body_measurements` row. `weighted` counts the added load only.

### 4.8 PR catalogue
- Per exercise family: `best_1rm` (Epley, §4.1), `heaviest_lift` (max `weight_kg`), `best_set` (max single-set volume), `max_volume` (max session volume), `total_reps` (the **all-time** total of completed reps of the exercise; ADR-52), and for duration/distance families `best_duration`, `best_distance`, `most_steps`, `most_floors`.
- Records are shown in the session summary, the exercise detail and the workout detail. ~~A live PR notification fires when a completed set beats the current best (configurable off / low / normal / high).~~ Deferred (ADR-52).
- PRs respect the warm-up inclusion setting (§4.6).

### 4.9 Calculators (defaults, ADR-52)
- Warm-up scheme from the working weight W: bar × 10, 40 % W × 5, 60 % W × 3, 80 % W × 2; each weight rounded to the nearest multiple of the derived rounding (below) and never below the bar. Example: W = 100 kg, 20 kg bar → 20 × 10, 40 × 5, 60 × 3, 80 × 2. The user picks 1–4 sets in the sheet (ADR-55, per use, not stored; default 1, so only the 60 % × 3 set is shown at first): 3 → 40/60/80 %, 2 → 50/80 % (× 5, × 2), 1 → 60 % (× 3); the bar set (× 10) only appears with 4.
- Bars: 20, 15, 10 (EZ) and 7,5 kg; default 20 kg.
- Available plates (per side): 25, 20, 15, 10, 5, 2,5, 2, 1,25, 1 and 0,5 kg; all on by default.
- Disc loader (ADR-56, T-097h; replaces the plate calculator): the user picks how many discs of each enabled weight go on ONE side (0-10 per weight, starts empty) and the same go on the other side. Total = bar (or handle) + 2 × the sum of the discs of a side. Read-only: nothing is stored. Example: 20 kg bar with a 25 and a 15 kg disc per side → 100 kg; 1 kg handle with one 10 kg disc → 21 kg. The bar or handle comes from Preferencias, never from the sheet.
- Rounding (ADR-56): derived, not a setting: 2 × the smallest enabled plate, for barbells and dumbbells alike (default plates → 1 kg; smallest 1,25 → 2,5 kg). Preferencias shows it read-only.
- Dumbbell handle (ADR-55, T-097f): dumbbell exercises use the disc loader with the handle as the bar (per dumbbell; default 1 kg, range 0,5-10 kg in 0,5 steps, user setting, no bar selector). Example: target 21 kg, handle 1 kg → 10 per side. The dumbbell warm-up uses the handle as its base (never below the handle). Rule: bar used = handle for dumbbells, the selected bar otherwise.

### 4.10 Session duration and pauses (US-38, T-096)
- `duration = end - started_at - paused_total_s`, never below 0, where `end` = `ended_at` when finished, else `paused_at` while paused (the display freezes), else now. A pause still open when the session finishes counts as paused until `ended_at`.
- Resuming adds the whole seconds since `paused_at` to `paused_total_s` (a negative span, from clock skew, adds 0).
- Example: 10:00 elapsed, paused for 5:00, resumed, 20:00 later: the duration is the wall clock minus 5:00; at the pause instant the display freezes at 10:00 and continues from 10:00. Summary, history and detail use the same rule.
- Pausing does not affect the rest timer or the per-set stopwatches. "Finalizar entreno" is enabled only when at least one set (of any type) is completed.
- Previous values (§4.3): in "Última vez en esta rutina" mode only sessions of the same routine are considered; a free session, or an exercise never done in that routine, shows the overall last performance respectively no ghost (no fallback to other routines for the latter).

## 5. Formatting (UI, Spanish locale)
- Thousands separator "." (also for 4 digits: "1.840") and decimal ",". Use a custom formatter in `commonMain`: the CLDR `es-ES` locale does not group 4-digit numbers.
- Weight with 1 decimal; cm with 1 decimal; BMI with 1 decimal; waist-to-hip ratio with 2 decimals; kcal and grams without decimals; integer percentages.
- Dates: "Hoy", "Ayer" or "lun, 24 oct".
