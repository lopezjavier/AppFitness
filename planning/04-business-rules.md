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

| Level | UI label (Spanish) | Factor |
|---|---|---|
| sedentary | Sedentario (poco o ningún ejercicio) | 1.2 |
| light | Ligero (1–3 días/sem) | 1.375 |
| moderate | Moderado (3–5 días/sem) | 1.55 |
| high | Alto (6–7 días/sem) | 1.725 |
| very_high | Muy alto (2 sesiones/día o trabajo físico) | 1.9 |

Example: male, 26 years, 178 cm, 74.2 kg, moderate → BMR = 742 + 1112.5 − 130 + 5 = 1729.5 → TDEE = round(2680.7) = **2681 kcal**.

### 1.4 Calorie target
- `target_kcal = TDEE + calorie_adjustment`.
- Default adjustment when switching strategy: Deficit **−400**, Maintenance **0**, Surplus **+250**.
- The user can change the adjustment in 50 kcal steps within the strategy range: Deficit [−1000, −50], Maintenance [−100, +100], Surplus [+50, +750].
- Safety floor: `target_kcal ≥ 1200`; if the calculation gives less, it is set to 1200 and a warning is shown.
- Adjustment label: |adjustment| ≤ 300 → "ligero" (light); ≤ 600 → "moderado" (moderate); > 600 → "agresivo" (aggressive) (e.g. "−400 kcal · Déficit moderado").
- The target is **only recalculated when the user saves in Profile** (the screen's single "Guardar" button, ADR-24), using the latest weight, and the new value is stored in `nutrition_goals`. Logging a new weight does not change the target automatically (that will be Phase 2's adaptive adjustment).

### 1.5 Macro split (auto-balance)
- Integer percentages, each in [5, 90], always adding up to 100. Default: P 30 / C 45 / F 25.
- When a slider moves from `x` to `x'` (Δ = x' − x), −Δ is distributed between the other two **proportionally to their current value**, rounded with the largest-remainder method so the sum is exactly 100. If one of the others reaches 5, the rest of Δ goes to the other; if both are at 5, the moved slider cannot go higher.
- Grams = `round(target_kcal × pct / 100 / kcal_per_gram)`.

Example (target 2400): P 30 / C 45 / F 25 → 180 g / 270 g / 67 g. Raising P to 35 (Δ = +5): C drops 5·45/70 = 3.21 → 41.79 and F drops 5·25/70 = 1.79 → 23.21; largest remainder → **C 42 / F 23**. Result 35/42/23 → 210 g / 252 g / 61 g.

## 2. Diary

- Entry values: `kcal = kcal_100g × quantity_g / 100` (same for macros), rounded to 0.1; stored as a snapshot.
- Meal and day totals = sum of snapshots. In the UI, kcal and grams without decimals.
- `remaining = target_kcal − consumed`. If negative, "+N kcal en exceso" (over) is shown with alert styling and the ring in its over-target state.
- Progress for each macro: `round(consumed_g / target_g × 100)` %. The bar fills to 100% at most; the text shows the real value (may exceed 100).
- kcal come from the food label; they may not exactly match 4P + 4C + 9F (fiber, alcohol). Story examples do add up for clarity.

Example (target 2400 kcal; 180 g P / 270 g C / 67 g F): consumed 142 g P, 210 g C, 48 g F = 1840 kcal → "1.840 / 2.400 kcal", "560 restantes", P 79%, C 78%, F 72%.

## 3. Weight

### 3.1 Daily value
- If there are several measurements on the same local day, the day's value is their average.

### 3.2 7-day moving average (MA7)
- `MA7(d)` = average of the available daily values in `[d − 6, d]`. If there are none, there is no point.
- Drawn only if there are at least 2 days with data in the window.

### 3.3 Weekly rate of change
- `rate = MA7(last day with data) − MA7(that day − 7)`, in kg/week with 2 decimals.
- If either MA7 does not exist → "Sin datos suficientes" (not enough data).
- Color by goal:
  - Deficit: rate < 0 → green; rate ≥ 0 → red.
  - Surplus: rate > 0 → green; rate ≤ 0 → red.
  - Maintenance: |rate| ≤ 0.20 → green; otherwise → amber.

### 3.4 Chart filters
1W = 7 days · 1M = 30 · 3M = 90 (default) · 6M = 180 · 1Y = 365, counted back from today (UI labels: 1S, 1M, 3M, 6M, 1A). The MA7 of the first day in the range uses data before the range when available.

## 4. Training

### 4.1 Estimated 1RM — Epley
- `1RM = weight × (1 + reps / 30)` for 2 ≤ reps ≤ 12; if reps = 1 → `1RM = weight`.
- Sets with reps = 0, reps > 12 or weight = 0 do not count for the 1RM.
- Rounded to 0.5 kg.
- A session's 1RM for an exercise = maximum over its completed sets.

Example: 100 kg × 5 → 116.67 → **116.5 kg**; 85 kg × 8 → 107.67 → **107.5 kg**.

### 4.2 Volume
- `volume = Σ weight × reps` over completed sets (`completed_at` not null). For bodyweight exercises without added load the volume is 0 (only the number of reps is shown).
- Weekly volume per muscle group: grouped by ISO week (Monday–Sunday) and the exercise's `muscle_group`.

### 4.3 Ghost data (previous performance)
- For each exercise in the current session: sets from the most recent **completed** session containing that exercise (`last_performance`).
- Matched by `set_index`; if the previous session had fewer sets, "—" is shown.
- Format: "85 kg × 8".
- The KG/REPS fields of a new set are prefilled with the ghost data as a placeholder (dim text, not as a value).

### 4.4 Rest timer
- Duration = the exercise's `rest_seconds` in the routine (default 120 s; 0 = no timer). For exercises added during the session, 120 s.
- Starts when a set is checked as completed; checking another set restarts it.
- "+30s" adds 30 s; "Omitir" (skip) stops it.
- Computed from an end instant (`endsAt`), not by decrementing a counter, so it stays correct when the app goes to the background.
- At 0: vibration + short sound (unless the system is muted).
- In a superset, the timer only starts after completing the set of the last exercise in the group.

### 4.5 Personal records (PR) in the summary
- An exercise is marked as PR in a session if its session estimated 1RM exceeds the maximum of all previous sessions.

## 5. Formatting (UI, Spanish locale)
- Thousands separator "." (also for 4 digits: "1.840") and decimal ",". Use a custom formatter in `commonMain`: the CLDR `es-ES` locale does not group 4-digit numbers.
- Weight with 1 decimal; kcal and grams without decimals; integer percentages.
- Dates: "Hoy", "Ayer" or "lun, 24 oct".
