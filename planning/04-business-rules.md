# Business rules and formulas (current)

Implemented as pure functions in `shared/domain/usecase/` with unit tests that include the examples below. Full prose, extra examples and history: [`phase1/04-business-rules.md`](phase1/04-business-rules.md). Section numbers are cited by code; do not renumber.

## 1. Energy and macros

### 1.1 Constants
Protein 4 kcal/g, carbohydrates 4 kcal/g, fat 9 kcal/g.

### 1.2 BMR (Mifflin-St Jeor)
Male `10·kg + 6.25·cm − 5·age + 5`; female `… − 161`. Age in full years from `birth_date`; weight = latest `body_measurements` row.

### 1.3 TDEE
`TDEE = round(BMR × factor)`: sedentary 1.2 (Sedentario), light 1.375 (Ligero), moderate 1.55 (Moderado), high 1.725 (Alto), very_high 1.9 (Muy alto). Each level is a selectable card with title + description. Example: male, 26, 178 cm, 74.2 kg, moderate: BMR 1729.5, TDEE **2681**.

### 1.4 Calorie target
- `target_kcal = TDEE + calorie_adjustment`. Default adjustment: Deficit −400, Maintenance 0, Surplus +250. Steps of 50 within Deficit [−1000, −50], Maintenance [−100, +100], Surplus [+50, +750].
- Safety floor by sex (ADR-25): 1200 kcal women, 1500 kcal men; below it the target is the floor and a warning shows.
- Label by |adjustment|: <= 300 ligero, <= 600 moderado, > 600 agresivo (e.g. "−400 kcal · Déficit moderado").
- Recalculated only when the user saves in Preferencias (ADR-24), with the latest weight; stored in `nutrition_goals`. Logging weight does not change it.
- Goal progress: `(start − current) / (start − target)` clamped 0..1, `start` = onboarding weight; null without the three values or when `start == target`.

### 1.5 Macro split (auto-balance)
Integer percentages each in [5, 90], summing to 100; default P 30 / C 45 / F 25. Moving a slider by Δ distributes −Δ over the other two proportionally to their current value, largest-remainder rounding; if one hits 5 the rest goes to the other; if both are 5 the slider cannot rise. Grams = `round(target_kcal × pct / 100 / kcal_per_gram)`. Example (2400 kcal): 30/45/25 -> 180/270/67 g; P to 35 -> C 42, F 23 -> 210/252/61 g.

## 2. Diary
- Base unit `g` (solids) or `ml` (drinks); nutrition per 100 base units. Entry `kcal = kcal_100 × amount / 100` (same for macros), amount to 0.1, stored as a snapshot; editing a manual food applies to future entries only (ADR-53).
- Nutrients are listed in Spanish label order (EU 1169/2011 Annex XV, then vitamins/minerals); absent values hidden.
- A named portion resolves to an amount in base units; the entry stores the amount and a `portion_label` snapshot.
- Meal/day totals = sum of snapshots; UI shows kcal and base unit without decimals. `remaining = target − consumed`; negative shows "+N kcal en exceso" with the over-target ring.
- Macro progress `round(consumed_g / target_g × 100)` %; bar capped at 100, text shows the real value.
- Example (target 2400; 180/270/67 g): consumed 142/210/48 g = 1840 kcal -> "1.840 / 2.400 kcal", P 79 %, C 78 %, F 72 %.

### 2.1 Diary history (US-24)
Per-day summary = day totals. Weekly average = mean of day totals over logged days in the last 7 days. Monthly total = sum in the calendar month; its average uses logged days only.

### 2.2 Cooking methods (US-50, ADR-65)
Only `g` foods; default Crudo. Added fat = `round(raw_g × pct / 100, 1)` g and `round(9 × fat, 1)` kcal; protein, carbs and micronutrients unchanged. Defaults (Preferencias -> Cocina, 0-20 %): A la plancha 1 %, Air fryer 2 %, Frito 8 %. The entry stores adjusted `kcal`/`fat_g`, `cooking_method`, `cooking_oil_retained_g`; per-100 values never change. Saved comidas store a method per item and recompute the fat from current preferences when added. A change applies to future entries only. Example: 200 g chicken (110 kcal, 3 g fat /100 g) fried = 364 kcal, 22 g fat.

### 2.3 Default comidas (US-51, ADR-61)
- A default is a saved comida per meal and ISO weekday (Mon = 1); its meal slot is authoritative. Days assigned to another comida for that meal are not selectable.
- Evaluated for today only, on the first Diary load: if `diary_default_applications` has the day, nothing; if the day already has entries, mark it and add nothing; otherwise copy the comidas as normal entries (current amounts, saved methods, current cooking preferences) and mark the day. No backfill, no re-add after deletion. The server rejects dates more than one day from UTC today (`22023`).
- Deleting a meal or comida cascades its assignments; logged entries are unchanged. A failed apply never blocks the Diary (non-blocking retry, idempotent).

## 3. Weight
- **3.1 Daily value:** several measurements on one local day -> their average.
- **3.2 MA7:** average of available daily values in `[d − 6, d]`; one day suffices (ADR-49); the chart connects consecutive points across gaps.
- **3.3 Weekly rate:** `(w_last − w_prev) / days_between × 7` kg/week, 2 decimals, over the last two days with data; fewer than 2 -> "Sin datos suficientes". Colour: Deficit rate < 0 green else red; Surplus rate > 0 green else red; Maintenance |rate| <= 0.20 green else amber.
- **3.4 Chart filters:** 1S 7 d, 1M 30, 3M 90 (default), 6M 180, 1A 365, back from today; the first day's MA7 uses data before the range when available.
- **3.5 Target weight:** `distance = current − target` (1 decimal). Deficit: > 0 "faltan X kg", <= 0 "objetivo alcanzado"; Surplus: < 0 "faltan X kg", >= 0 reached; Maintenance: 0,0 reached, > 0 "X kg por encima del objetivo", < 0 "X kg por debajo del objetivo" (X absolute). Progress bar `(start − current) / (start − target)` clamped 0-100 %, integer, `start` = first weight in the app; no bar if `start = target`. No date or ETA.
- **3.6 BMI:** `kg / (cm/100)²`, 1 decimal, derived, hidden without height.
- **3.7 Health Connect:** weight -> `weight_kg`, body fat -> `body_fat_pct`, lean mass, body water, bone, BMR -> their columns. Read: weight and body fat within ± 2 min form one measurement; composition merges by instant; `external_id` prevents duplicates; records written by this app are skipped. Write (permission granted): manual weight/body fat/composition are written back, the record id is stored, edits update and deletes remove the copy; a failed HC call never blocks the app. Imported readings deleted in the app stay deleted. Sync is manual ("Sincronizar"), no background sync. A Health Connect weight wins for its day (ADR-47).
- **3.8 Interpretation (informative, with the non-medical disclaimer "Este producto no es un dispositivo médico; los resultados no sirven para diagnóstico"):** BMI < 18,5 underweight, 18,5-24,9 normal, 25,0-29,9 overweight, >= 30 obese. Body fat men < 10 low, 10-20 normal, 20-25 high, > 25 very high; women < 18, 18-28, 28-32, > 32. Shared edges belong to the lower band; BMI compares the rounded value. WHR men <= 0,90 low risk else high; women <= 0,85.
- **3.9 WHR:** latest waist / latest hip, 2 decimals; both required, else "Sin datos suficientes".

## 4. Training
- **4.1 Estimated 1RM (Epley):** `w × (1 + reps/30)` for 2-12 reps; reps = 1 -> `w`; reps 0, > 12 or weight 0 do not count; round to 0.5 kg; session 1RM = max over completed sets. 100 × 5 -> **116.5**; 85 × 8 -> **107.5**.
- **4.2 Volume:** `Σ weight × reps` over completed sets (`completed_at` not null); bodyweight/assisted/weighted per §4.7; duration/distance exercises 0. Weekly muscle volume: ISO week (Mon-Sun) by `muscle_group`.
- **4.3 Ghost data:** per exercise, the sets of the most recent completed session containing it, matched by `set_index` ("—" if fewer), format "85 kg × 8", shown as dim placeholder in KG/REPS. A pending set from a planned routine set shows the planned weight/reps instead (ADR-50); the ANTERIOR column always shows the previous performance.
- **4.4 Rest timer:** the exercise's `rest_seconds` (default 120; 0 = none); starts when a set completes (restarts on another); "+30s", "Omitir"; computed from an end instant so background is safe; at 0 vibration + the alarm sound unless muted; in a superset it starts after the group's last exercise.
- **4.5 PR in summary:** an exercise is a PR when its session 1RM exceeds all previous sessions.
- **4.6 Set types:** `working`, `warm_up`, `failure`, `drop`. Volume, 1RM, PRs and totals ignore `warm_up` unless "Incluir calentamiento" is on (toggling recalculates past and future). Types can be set while planning and logging.
- **4.7 Volume options:** per-hand exercises (`exercises.per_hand`, ADR-56): `volume = 2 × weight × reps`, 1RM unchanged. `bodyweight`/`assisted`: `(latest body weight ± added/assisted) × reps`; `weighted` counts the added load only.
- **4.8 PR catalogue:** per exercise family `best_1rm`, `heaviest_lift`, `best_set` (single-set volume), `max_volume` (session), `total_reps` (all-time, ADR-52), and for duration/distance `best_duration`, `best_distance`, `most_steps`, `most_floors`. Shown in the session summary, exercise detail and workout detail; respects §4.6. The live PR notification is deferred (ADR-52).
- **4.9 Calculators (ADR-52/55/56):** warm-up from working weight W: bar × 10, 40 % × 5, 60 % × 3, 80 % × 2, rounded to the derived rounding and never below the bar; 1-4 sets chosen per use (default 1 = 60 % × 3; 3 -> 40/60/80; 2 -> 50/80 × 5,2; the bar set only with 4). Bars 20, 15, 10 (EZ), 7,5 kg (default 20). Plates per side 25, 20, 15, 10, 5, 2,5, 2, 1,25, 1, 0,5 (all on by default). Disc loader: 0-10 discs per weight per side, total = bar + 2 × side sum, read-only (20 kg bar + 25 + 15 per side = 100 kg). Rounding is derived: 2 × smallest enabled plate. Dumbbells: handle (default 1 kg, 0,5-10 in 0,5 steps) acts as the bar, per dumbbell.
- **4.10 Duration and pauses (US-38):** `duration = end − started_at − paused_total_s`, never below 0 (`end` = `ended_at`, else `paused_at` while paused, else now). Resuming adds whole seconds since `paused_at` (negative adds 0). Pausing does not affect rest or set stopwatches; "Finalizar entreno" needs at least one completed set. "Última vez en esta rutina" mode considers only sessions of that routine.
- **4.11 Statistics (US-40/41):** eligible = `completed` sessions with UTC `started_at` date in `[p_from, p_to]` (null = open), completed sets, warm-ups only when included. `workouts` counts sessions once (even with no eligible set); `volume = Σ set_volume × factor`; `duration` = Σ per session of `greatest(0, ended_at − started_at − paused_total_s)`. Weeks start UTC Monday. Sets per muscle group: primary +1, each distinct secondary +0.5 (secondary equal to primary adds nothing); ties in `muscle_group` enum order; `full_body` is handled by the client. `exercise_set_counts` with a group weighs 1/0.5 and excludes exercises without it. Example: bench (chest; triceps, shoulders), 3 working + 1 warm-up: chest 3, triceps 1,5, shoulders 1,5 (with warm-ups 4/2/2).

## 5. Formatting (UI, Spanish)
Thousands "." (also 4 digits, "1.840") and decimal ","; custom formatter in `commonMain` (CLDR `es-ES` does not group 4 digits). Weight, cm and BMI 1 decimal; WHR 2; kcal and grams none; integer percentages. Dates: "Hoy", "Ayer", "lun, 24 oct".

## 6. Reminders and weekly check-in (E10, ADR-73)
- **6.1 Reminders:** computed on the client at each open or return to the foreground, never stored, no system notifications, no time window: a condition met while the app was closed is listed on the next open ("Centro de avisos").
- **6.2 Weight:** listed when the latest weigh-in is 2 or more days old (2 days in a row without one). **Circumferences:** listed when the latest circumference is 14 or more days old. A Health Connect weight stored for today clears the weight reminder.
- **6.3 Silencing:** each type (weight, circumference, check-in) is silenced in Preferencias > Avisos through `notification_settings`; a missing row means enabled; a silenced type is neither listed nor counted.
- **6.4 Check-in day:** `profiles.check_in_weekday` (1-7, default Sunday). From that weekday the current week's check-in (week = ISO Monday-Sunday) is listed and counted; unanswered, it stays listed until the next check-in day, when only the current one is listed (never two); not listed before the first check-in day.
- **6.5 Answer:** energy 1-5 (required) and optional notes (<= 1000 chars), one row per user and week; saving removes the reminder. An answered check-in can be edited (same row, `updated_at` refreshed). Sleep is not asked.
- **6.6 Summary (deterministic):** this week's training volume against last week's (`weekly_set_counts`), the week's records (`weekly_records`) and the week's weight trend.
