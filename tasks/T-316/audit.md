# T-316 audit: every report other than the weekly report (fitness-scientist, 2026-11-22)

Input for the user's decisions and then the planner. Nothing here is a rule yet: rules go into `planning/07-coach-design.md` only after the user decides (T-316 description, step 2). Standard applied: the user's rule of 2026-11-21 (`07` §3 "Weekly report: progress first"): a report shows progress, what changed and whether it moves toward the goal; adherence counts are at most a context line or a reliability gate, never a card of their own. Everything proposed is deterministic (SQL or on the device), **only shown** unless stated; no language model (each item is a fixed comparison of logged numbers). No new monthly report (ADR-66, ADR-103).

Code read on branch `feat/T-316-other-reports` (commit `abbc3e08`). Each "current content" below is taken from the code, not from the docs.

## 0. Reports found

| # | Report (label in the app) | Where | Data source | Stored? | Verdict |
|---|---|---|---|---|---|
| 1 | "Revisión del bloque" | `BlockReviewRoute` (live, Trainer card and Centro de avisos) and `PastReportRoute` (read-only, from "Informes") | RPC `open_coach_block_review` → `coach_block_review_build` (latest body `20270131100000_coach_week_progress.sql`), table `coach_block_reviews.inputs` | **Stored, frozen** at the first open (`20270106100000`), never recomputed | Remake (largest gap) |
| 2 | "Informes" list | `ReportsRoute` (Trainer › "Informes y resumen semanal") | RPC `list_coach_reports` | Reads stored rows | Change (rows carry no progress) |
| 3 | "Calorías" (diary history) | `DiaryHistoryRoute` (Perfil › Estadísticas › Calorías) | RPC `food_diary_summary`, computed on the device | Live | Small changes (log viewer, not a progress report) |
| 4 | "Entrenos" (the Estadísticas training screen) | `StatsRoute` (Perfil › Estadísticas › Entrenos) | RPCs `training_summary`, `weekly_set_counts`, `muscle_group_sets` (`10-feature-inventory.md`) | Live | Remake (no progress at all today) |
| 4b | "Kilos por grupo muscular" | `MuscleVolumeRoute` (link from "Entrenos") | RPC `weekly_muscle_volume` | Live | Change or remove (Q8) |
| 5 | "Fuerza" | `StrengthRoute` (Perfil › Estadísticas › Fuerza) | `StrengthViewModel` (top exercises of 6 weeks, record, 30-day change, 1RM series) | Live | Small change |
| 5b | "Progreso del ejercicio" | `ExerciseProgressRoute` | exercise series | Live | Keep (audited, no change proposed) |
| 6 | "Entreno completado" (end-of-session summary) | `SessionSummaryScreen` inside `LiveSessionRoute` | `LiveSummary` on the device | Live | Change (found by the audit, not in the T-316 list) |
| 7 | Perfil highlights (3 hero stats) | `ProfileHome` › `HeroHighlights` | latest weight, Fuerza default record, Calorías week average | Live | Small change |
| — | "Peso" / "Historial de peso", "Actividad", "Historial de entrenos" / session detail | `WeightRoute`, `WeightHistoryRoute`, `ActivityRoute`, `HistoryRoute`, `SessionDetailRoute` | — | Live | Audited, **not reports**: data viewers. Peso is already progress-first (current weight, weekly rate, "Objetivo del plan" card, trend chart). No change proposed (Q12) |
| — | Early block review ("Bloque nuevo" mid-block) | `open_coach_block_new`, read by `BeforeStartViewModel` / `LoadNextBlockContextUseCase` | — | Not stored | Not shown to the user as a report; out of scope |

## 1. "Revisión del bloque" (live and stored)

**Where:** `feature/trainer/BlockReviewScreen.kt` (`ReviewContent`, shared with `PastReportScreen` through `readOnly`), `BlockReviewSections.kt`, `BlockReviewText.kt`, `BlockReviewStored.kt`, `ReportCards.kt` (`SessionsCard`, `GroupsCard`). Backend: `coach_block_review_build(p_plan_id, p_today, p_end)`, stored once in `coach_block_reviews.inputs` = `{adherence, lifts, groups, weight, outcomes, waist, stalls}` plus `phase_proposal`, `phase_decision`, `next_plan_id`, `seen_at`. Opened the day after the block's last day.

**Current content, in order (`ReviewContent`):**
1. "Entrenos": completed / planned sessions with % (`SessionsCard`), over every plan week including the deload.
2. "Series por grupo": total sets per muscle group over the block (`GroupsCard`, secondary muscles at half).
3. "Básicos": per main lift (role `main` in week 1), first-week and last-week best e1RM as two bars, a "±x kg" pill, a gold "Récord" badge when the last week is the block's best and the change is positive, and "Estancado". **Defect:** the "Récord" badge means "best week of this block", not a record as `04` §4.5 defines it (beats every earlier session); it can disagree with the exercise's records. No noise margin: +1 kg on 100 kg (1 %) reads as a gain.
4. Stall card ("¿Por qué no progreso?"), lifts stalled at the block end (skipped with an open discomfort, T-313).
5. "Peso": first plan week average → last plan week average, the trend slope and the target rate as text ("Peso medio de 81,2 a 79,8 kg (−0,4 kg/semana; objetivo −0,4 kg/semana)."). **Defect:** both averages have **no weigh-in gate** (one weigh-in in a week is enough), unlike the `07` §2 rule of 3 weigh-in days; no "Tu plan" verdict (the weekly report and Peso use `WeightPaceFit`).
6. "Objetivos": each plan outcome with target and current value and a "Conseguido" / "No conseguido" chip. **Defect:** "reached" is evaluated at the block end while the outcome's deadline is usually later, so "No conseguido" shows for an outcome still on its way.
7. "Cintura": latest waist vs one at least 28 days older, "dentro del margen de medida" under 3,0 cm.
8. Phase proposal (Aceptar / "Seguir como estoy"; stored: the decision as text).
9. Pending calorie proposal (live only).
Footer: "Crear el siguiente bloque".

So cards 1, 2 and half of 3 are adherence or effort; the real progress (did each exercise go up over the block, records, weight toward the goal) is either missing or ungated.

**Proposed content (progress-first, same order logic as the weekly report):**

| # | Card | Action | Reason |
|---|---|---|---|
| 0 | Coach card: one sentence + one action | **Add** | Same template as the weekly card (`07` §3 (0)) over the block: "En este bloque: 5 ejercicios suben, press banca +5 kg (+6 %), peso −1,8 kg." Action, first that applies: pending phase proposal > pending calorie proposal > stall action (`07` §3 (7)) > missing data > "Crea el siguiente bloque". Stored reviews show the decided outcome as text ("Fase aceptada: Mantenimiento"), never a live button (R-60 precedent). Order = *design decision* (decisions waiting first), grade *none*. |
| 1 | Strength per muscle group, whole block | **Add** (replaces cards 1-2) | The weekly Strength card computed at the block's last week is already "block to date" (`07` §3 (1): baseline = first non-deload session of the block, compared = best session of the last week or the carried latest non-deload session, so a deload week 4 is handled). Same states (Sube / Igual or "Mantienes" in a cut / Baja / Estancado / paused by discomfort / Sin comparar), same 1-RPE allowance (Halperin 2022: reps to failure under-predicted by 0.95, 95 % CI 0.17-1.73) and load-or-reps progress (Plotkin 2022). Header count: "12 de 15 ejercicios suben". |
| 1a | Context line "14 de 16 entrenos · 170 de 180 series" | **Change** (cards 1 "Entrenos" and 2 "Series por grupo" become this line) | User rule: adherence is a context line. Sessions: completed/planned over the block (as now); sets: done vs `coach_plan_sessions` planned, hidden when the plan no longer matches (weekly rule). The 80 % adherence of `07` §12.4 stays an internal gate for the next block's volume, never a card. |
| 2 | Main lifts, block sparklines | **Change** (card 3) | Reuse the weekly Main lifts card at the block's last week: weekly best e1RM sparkline, deload weeks hollow; "Sube +x kg (+p %)" only with the stall test improving and at least **4 %** over the block's first non-deload week, else the amount with "dentro del margen de un día a otro"; fewer than 3 counted weeks "provisional". The 4 % is the existing `07` §3 (2) design decision anchored on the true-1RM test-retest median CV of 4.2 % (Grgic 2020). The block-best "Récord" badge is **removed** (records live in card 3). |
| 3 | Records of the block | **Add** | `07` §3 (3) rule with the window = the whole block (sessions that beat every earlier session of the exercise, first session never a record, same set filter as the PR catalogue). At most 3 badges, main lifts first, plus "N récords en el bloque" when there are more (*design decision*). No record: no card. Showing a period's records is *common practice in leading apps* (Hevy monthly report, opened 2026-11-22). |
| 4 | Weight over the block | **Change** (card 5) | Change = average of the last plan week of the block with 3+ weigh-in days minus the first such week (`07` §2 gate, the weekly "since the phase started" rule applied to the block window); one decimal, no colour; sparkline of the block's weekly averages with the dashed plan line to "Meta {x} kg · {fecha}" when the plan has a weight outcome; the "Tu plan" verdict and pace from the shared trend (`04` §7.4, one pace). Below the gate: "Faltan pesajes para ver el cambio del bloque". The "?" keeps the weekly text on noise (within-week fluctuation 0.35 %, Turicchi 2020). |
| 5 | Waist | **Keep** (card 7) | Already the `07` §5 rule (pair at least 28 days apart, 3,0 cm margin). |
| 6 | Outcomes ("Objetivos") | **Change** (card 6) | Show progress toward each target: "Peso: objetivo 78 kg · ahora 79,8 kg". Chip only when it is true: "Conseguido" when reached; "No conseguido" only once the deadline has passed; otherwise no chip (Q3). *Design decision*. |
| 7 | "¿Por qué no progreso?" (stall card) | **Keep** | Unchanged `07` §3 (7). |
| 8 | Phase proposal, calorie proposal | **Keep** | Decisions; they also feed the coach card's action (0). |
| — | "Entrenos" card, "Series por grupo" card | **Remove** | Folded into 1a. The per-group sets stay visible where they drive a choice: the builder's "Priorizar / Mantener" preview (`07` §12.4) and Estadísticas. |

**Card order (Q2):** the weekly R7 order by training intention (Perder grasa → Weight first; Ganar fuerza / Ganar musculatura → Strength first; Mantenimiento / Recomposición → Strength, Waist, Weight); coach card always on top; Outcomes after the first progress cards; stall, phase and calorie cards last.

**Gates and states:**
- Loading skeleton, Empty ("Aún no hay revisión del bloque."), Error with "Reintentar", Offline: unchanged.
- A block with fewer than 3 counted weeks for a lift: "provisional"; an exercise with one session in the block: "Sin comparar"; the first block ever: the baseline is the block's first session, so the card is never empty.
- Weight: no qualifying first or last week → average only + "Faltan pesajes…"; no weigh-in at all → no Weight card, the coach card's missing-data action says "Pésate 3 días por semana".
- Paused weeks inside the block (`07` §12.5) are not plan weeks, so they do not count; a block ended early by "Bloque nuevo" has no stored review (as today).
- Inputs deleted later (session, weigh-in, plan): the line is hidden and listed as missing (T-314 rule).

**Reuse of the weekly report:**
- Backend: the T-313 section builders `coach_progress_strength_for`, `coach_progress_lifts_for`, `coach_progress_context_for` and `coach_progress_waist_for` called with the block's last plan week; `coach_progress_records_for` already takes a `p_start`/`p_end` window (pass the block start and end); `coach_progress_weight_for` is phase-run based, so the block change needs one small addition (first/last qualifying week of the block) or a block-window variant. All are owner-filtered and revoked, so they run inside the definer `open_coach_block_review` and inside a backfill migration.
- App: `WeekReportCoachCard`, `WeekReportStrength` (+ group sheet), `WeekReportLifts`, `WeekReportWeight` (`WeightPaceChart` compact), `RecordBadge`, `WeekReportOrder` (R7), `WeekReportHeadline` pattern (a block variant of the sentence and action), `WeightPaceFit.verdictOf`. `SessionsCard` and `GroupsCard` lose their last caller (block review) and can be deleted with it, per `05` §8 (retire the ad-hoc copies).

**Stored past reviews:** **production backfill: yes, if the user approves (Q4).** `coach_block_reviews.inputs` is frozen since T-233 and the past reviews in "Informes" open through the same `ReviewContent`, so without a rebuild they would keep the old layout (T-310 feedback (c): remake the stored past reports too). Proposed, as T-314: one idempotent migration adds `inputs_v1 jsonb` (backup), adds `inputs.progress` rebuilt "as of `ended_on`" with the builders above and `schema_version: 2`; kept exactly as stored: `phase_proposal`, `phase_decision`, `decided_at`, `next_plan_id`, `stalls`, `adherence`, `outcomes` (they record what was decided). Self-check and post-deploy check: same row count, a backup on every v1 row, frozen keys byte-equal. A review whose plan was deleted is already gone (cascade). The number of stored reviews in production is unknown to me (likely 0-2 after a month of use); the backend developer counts them before the migration. An app that ignores `progress` keeps working (v1 keys kept).

## 2. "Informes" list

**Where:** `feature/trainer/ReportsScreen.kt`; RPC `list_coach_reports` (`20270123100000_coach_reports.sql`) returns `id, kind, plan_id, week, paused, period_start, period_end, created_at, phase_decision`, no snapshot data.

**Current content:** one `RowGroupCard`, newest first; each row: icon (calendar for a week, fact-check for a block), title "Resumen semanal" or "Revisión del bloque", the date range, "· Semana en pausa" for a paused week, chevron. Empty: "Aún no hay informes" / "Tus resúmenes semanales y revisiones de bloque aparecerán aquí."; Error with "Reintentar"; Offline; skeleton.

**Proposed content:**

| Item | Action | Reason |
|---|---|---|
| Progress teaser per row | **Add** | One line from the stored progress, so the list itself shows progress: week "3 suben · −0,3 kg" (the coach-card sentence parts of that week); block "12 de 15 suben · −1,8 kg". A paused week keeps "Semana en pausa"; a v1 row without `progress` shows only the dates. The parts come from the same pure headline builder as the report (no second wording). |
| Week label | **Change** | "Semana 3" plus the date range instead of the generic "Resumen semanal" title on every row; a block row reads "Revisión del bloque" with its range. |
| Grouping by block | **Add (optional, Q5)** | Weeks listed under their block's heading, the block review first, newest block first. *Design decision* for scanning a long list (reports are kept without a limit, ADR-103). |
| Empty, error, offline states | **Keep** | |

**Gates:** the teaser uses only gated figures (weight change only when the report's own gate passed; strength count only when the week is not week 1). **Reuse:** `WeekReportHeadline` (week) and its block variant. **Backend:** `list_coach_reports` returns the few teaser fields read from the stored v2 `progress` (weeks, already backfilled by T-314) and from the block review's `progress` (after the item 1 backfill). **Backfill:** none of its own.

## 3. "Calorías" (diary history)

**Where:** `feature/diary/DiaryHistoryScreen.kt`, `DiaryHistorySummaries.kt`, `DiaryHistoryViewModel.kt`; designsystem `DiaryHistoryRow`, `MiniBarChart`, `MonthCalendarGrid`.

**Current content:** "Día / Semana / Mes" selector; then the summary card of the panel, then the month calendar (days with entries marked; Semana shows the 7-day band).
- Día: the selected day's kcal against the target and the three macros as bars (with targets) or plain grams.
- Semana ("Media diaria"): averages of the **last 7 days ending today** over **logged days**; bars of kcal per day with the target line, **days over the target in amber**, the last bar lit.
- Mes ("{mes} · media diaria"): averages per logged day of the month; bars = average kcal of each 7-day block (1-7, 8-14, …).

**Assessment:** this is a log viewer (what did I eat), not a progress report; by the user's decision Q15 of T-310, nutrition shows as progress only where it explains progress (the weekly report's "¿Por qué no avanza tu peso?"). So no progress card is added here (Q10). Two figures disagree with existing app rules and should change:

| Item | Action | Reason |
|---|---|---|
| Averages over "logged days" | **Change**: average over **complete days** only, and say how many: "Media de 5 días completos" | `07` §2 "Data cleaning": a day under 40 % of the median of the logged days of the 28-day window is partial and excluded "from every count of logged days" (one rule for the whole app). Today the screen (and the Perfil highlight, item 7) count partial days, so a forgotten dinner lowers the average. Diaries are biased low anyway (energy intake under-estimated by 19 % against doubly labelled water, 11 studies, athletes, Capling 2017), so the copy says "registrado", never "comido". |
| Today in the Semana average | **Change**: the week = the 7 days **before today**; today's bar is still drawn, unlit until the day ends | A day in progress lowers the mean every morning; the app already applies this to the steps average (`07` §3 "Weekly goal rings", "check that `steps_avg` excludes today"). *Design decision*. |
| Amber "over target" bars | **Change (Q9)**: one colour, the target line stays | The user removed colour change by threshold on the Trainer root ("just put everything in blue", 2026-11-15, `07` §3 "Trainer root colours"); a "too high" colour on a biased-low diary mean is never firm (Capling 2017). Whether that rule extends to this screen is the user's call. |
| Día panel, calendar, Mes bars | **Keep** | |

**States:** skeleton, Error with "Reintentar", "Sin registros" per panel: unchanged; a period whose logged days are all partial reads "Sin días completos" with the day count. **Reuse:** the complete-day rule already exists server-side for the expenditure anchor (`04` §7.3); the screen needs the same 40 %-of-median filter on the device or a flag from `food_diary_summary`. **Backfill:** none (live).

## 4. "Entrenos" (Estadísticas) and "Kilos por grupo muscular"

**Where:** `feature/analytics/stats/TrainingStatsScreen.kt`, `TrainingStatsSections.kt`, `MuscleDistributionSection.kt`, `MuscleDetailSheet.kt`; `feature/analytics/MuscleVolumeScreen.kt`. Range selector 1M / 3M / 6M / 1A / Todo.

**Current content (compact order):**
1. Four tiles: "Entrenos", "Series", "Volumen" (tonnage, kg or t), "Duración".
2. "Series por semana" / "Series por mes" bar chart.
3. "Series por grupo muscular" bars, with the link "Kilos por grupo muscular".
4. "Ejercicios principales": most-trained exercises, "N series · M entrenos", each opens "Progreso del ejercicio".
5. "Distribución muscular": radar "Actual" vs "Periodo anterior".
6. "Últimos 7 días" body heat map (tap a muscle: sheet).
"Kilos por grupo muscular": tonnage per group per week, last 8 weeks.

**Assessment:** every card is effort or compliance (count of workouts, sets, kilos moved, minutes); none says whether the user got stronger in the range. This is almost exactly Hevy's monthly report (workouts, time, volume, sets, muscle distribution, top exercises, records; opened 2026-11-22), i.e. *common practice in leading apps*, but it is the "empty data" the user rejected.

**Proposed content (Q6, Q7, Q8):**

| # | Card | Action | Reason |
|---|---|---|---|
| 1 | "Tu progreso en este periodo" | **Add, on top** | Per exercise trained in the range with at least 2 sessions: best e1RM of the last session window vs the first, state Sube (+p %) / "dentro del margen" / Baja, with the **4 %** display margin of `07` §3 (2) (Grgic 2020, median CV 4.2 %); header "8 de 11 ejercicios suben"; main lifts first; a tap opens "Progreso del ejercicio". Window = the range; an exercise with one session: "Sin comparar". "Todo" compares the first and last month with sessions. *Design decision* (no source sets a window for this display). |
| 2 | Records of the period | **Add** | Count + at most 3 badges ("Press banca: 1RM estimado 92,5 kg (+2,5)"), same record rule and set filter as `07` §3 (3) / `04` §4.5. *Common practice in leading apps* (Hevy). |
| 3 | Tiles | **Change** | Keep "Entrenos" and "Series" as one context line ("24 entrenos · 412 series"); **remove "Volumen" (tonnage)** and "Duración". Tonnage was dropped from the weekly report by the user's accepted design (`07` §3 "No longer shown": the dose is counted in sets). |
| 4 | Sets per week chart, sets per muscle group | **Keep**, below 1-2 | They show the dose (sets), which the app's volume rules use; not a progress claim. |
| 5 | "Ejercicios principales" | **Change** | Row supporting text becomes the e1RM change over the range ("+5 kg · 6 entrenos") instead of "N series · M entrenos"; or merge into card 1 (Q6). |
| 6 | Radar, body map | **Keep or remove (Q7)** | Distribution, not progress. Recommended: keep, last. |
| 4b | "Kilos por grupo muscular" | **Remove or convert (Q8)** | Tonnage per group. Recommended: convert to weekly **sets** per group (the dose the app counts) or remove the screen. |

**Gates and states:** an exercise needs 2 sessions in the range to be compared; e1RM only from 2-12 reps (`04` §4.1 Epley), working sets; bodyweight or duration exercises are listed with their own metric or left out of card 1. Empty range: the existing "Sin entrenos en este periodo". **Reuse:** the e1RM and record definitions of `04` §4.1/§4.5 (the PR catalogue already computes records per session, `exercise_session_records`); `RecordBadge`, `Sparkline`, the state icons of `WeekReportStrength`. **Backend:** one read-only RPC for the range comparison (or the existing `exercise_history` per exercise on the device; the developer picks). **Backfill:** none (live).

## 5. "Fuerza" and "Progreso del ejercicio"

**"Fuerza" current content** (`StrengthScreen.kt`): a card listing the top exercises of the last 6 weeks (one selected); a second card with the selected exercise's "Récord 1RM estimado: x kg", the sparkline of its 1RM per session, the change "x kg este mes" / "Sin cambios este mes", and "Progreso del ejercicio". Empty: "Aún no tienes entrenos en las últimas semanas…" with "Empezar entreno".

**Proposed:** **change** the monthly change line: when the 30-day change is under **4 %** of the earlier value, read "+1,5 kg este mes · dentro del margen de un día a otro" (same `07` §3 (2) margin, Grgic 2020); otherwise as today. Everything else **keep**. If the user prefers one place for strength progress, Fuerza can fold into the new card 1 of "Entrenos" (Q6, not recommended: Fuerza is the one-exercise view the Perfil highlight links to).

**"Progreso del ejercicio":** 1RM (or reps, duration…) per session with record markers, volume chart, range selector. It is already a progress view; **keep**, no change proposed.

**Backfill:** none (live).

## 6. "Entreno completado" (end-of-session summary)

**Where:** `feature/workout/SessionSummaryScreen.kt` (shown when a live session finishes; "Hecho" closes it).

**Current content:** celebration burst, "Entreno completado", "Buen trabajo, sesión guardada", a "N récords" badge; tiles duration and completed sets; "Volumen por ejercicio": one card per exercise with its tonnage and a bar relative to the largest, gold when it has records, and its record pills.

**Proposed (Q11):**

| Item | Action | Reason |
|---|---|---|
| "Volumen por ejercicio" bars | **Change** to "Frente a la última vez": per exercise "80 kg × 8 → 82,5 kg × 6" with the state icon (Sube / Igual / Baja / Sin comparar) | The comparison of `07` §3 (1) applied to this session vs the exercise's previous non-deload session (same range, within 14 days, else "Sin comparar"): load up with every set at or above the range bottom, or same load with more reps over the shared sets (Plotkin 2022: load and rep progression both "viable strategies"); a Sube needs the top-load RPE at most 1 above (Halperin 2022). Tonnage per exercise is effort, not progress. *Design decision* for the session window. |
| Records badge and pills | **Keep** | Already progress (`04` §4.5). |
| Duration and sets tiles | **Change** to one context line | Adherence as context. |
| A paused-by-discomfort exercise | **Add** its state, no note | `07` §7: increases are blocked while open; the health note stays on the Trainer root. |

**Reuse:** the state rule and `ExerciseStatusRow`/state icon of `WeekReportStrength`; `last_performance` already returns the previous session for the ghost data. **Backfill:** none (not stored). "Historial de entrenos" and the session detail stay logs (keep).

## 7. Perfil highlights

**Current:** three hero stats: latest weight, "the 1RM record of the most trained exercise of the last 6 weeks" (the exercise is not named), and the Calorías week average per logged day.

**Proposed:** weight: **keep** (latest value; the trend lives in Peso); strength: **change** to name the exercise ("Press banca · 92,5 kg") or replace by "N ejercicios suben" from item 4 (Q12, recommended: name it, smallest change); kcal: **change** to the complete-day average of item 3 (same figure, same rule). Live, no backfill.

## 8. Rules this audit would add to `07` (only after the user decides)

All reuse rules already in `07`; the new parts are windows and display choices, every one a *design decision* (grade *none*) anchored as stated:
1. Block review = the weekly progress rules (`07` §3 (0)-(5)) evaluated at the block's last plan week, records over the block window, weight change first-to-last qualifying block week; outcomes chip only when true.
2. Estadísticas range comparison with the 4 % display margin; records of the range.
3. Session summary: the `07` §3 (1) comparison against the previous session of the exercise.
4. Diary history: complete days only (existing §2 rule extended to the screen), today excluded from the week mean.
5. Fuerza: 4 % margin on the monthly change.

**Verdict table (sources opened in this session, 2026-11-22):**

| Claim used | Grade | Population, transfer | Source |
|---|---|---|---|
| True-1RM test-retest CV 0.5-12.1 %, median 4.2 % (32 studies, n = 1 595) → the 4 % display margin | *strong* (systematic review) for true 1RM; the margin itself is a *design decision*; e1RM from submaximal sets is noisier | Adults of any experience; transfers | Grgic, Lazinica, Schoenfeld, Pedisic 2020, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:32681399%20AND%20SRC:MED&resultType=core&format=json |
| Reps to failure under-predicted by 0.95 (95 % CI 0.17-1.73), training status no effect (12 studies, 414) → 1-RPE allowance | *moderate* (exploratory meta-analysis) | Healthy adults; transfers | Halperin et al. 2022, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:34542869%20AND%20SRC:MED&resultType=core&format=json |
| Load vs rep progression: strength 2.0 kg (−2.4, 7.8), "questionable practical significance"; both "viable strategies" → reps or load count as Sube | *moderate* (single RCT, 43 trained adults, 8 weeks, lower body) | Trained adults; transfers to recreational lifters | Plotkin et al. 2022, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:36199287%20AND%20SRC:MED&resultType=core&format=json |
| Within-week weight fluctuation 0.35 % → weight gates and the "?" text | *moderate* (observational, n = 1 421) | Adults after weight loss; transfers as a measurement fact | Turicchi et al. 2020, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=DOI:10.1371/journal.pone.0232152&resultType=core&format=json |
| Energy intake under-estimated 19 % vs doubly labelled water (11 studies) → "registrado", no "too high" colour | *strong* (systematic review) | Athletes; transfer to recreational lifters assumed | Capling et al. 2017, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:29207495%20AND%20SRC:MED&resultType=core&format=json |
| Strength progress is not muscle size ("hypertrophy … load independent"; strength "superior in high-load") → the Strength "?" text, Igual is neutral | *strong* (network meta-analysis, 28 studies, 747 adults) | Healthy adults; transfers | Lopez et al. 2021, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:33433148%20AND%20SRC:MED&resultType=core&format=json |
| Monitoring goal progress raised goal attainment d+ = 0.40 (95 % CI 0.32-0.48); larger when progress was reported or physically recorded → progress-first reports | *strong* as meta-analysis (138 studies, N = 19 951); *weak* transfer to lifting | General adults, mixed behaviours | Harkin et al. 2016, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:26479070%20AND%20SRC:MED&resultType=core&format=json |
| Protein beyond 1.62 g/kg/day gave no further fat-free-mass gain (49 RCTs, 1 863) | *strong* | Healthy adults; transfers. **Not proposed** for the diary history (Q10 recommendation: no) | Morton et al. 2018, https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:28698222%20AND%20SRC:MED&resultType=core&format=json |
| A monthly report of workouts, time, volume, sets, records, muscle distribution and top exercises | *common practice in leading apps* (product evidence, not science) | — | Hevy monthly report, https://www.hevyapp.com/features/monthly-report/ |

**Not found (searched 2026-11-22):** the minimal detectable change of an e1RM estimated from a submaximal set (so the 4 % stays a conservative display margin, not a measured threshold); any study of what a period summary should show to recreational lifters; any study of a window (session, block, month) for displaying strength progress.

**Trainer journey (`07` §12) mapped:** session (item 6), week (item 2 teaser), block review (item 1), phases (item 1 weight since block start, phase card), plateaus (items 1, 4: Estancado, "dentro del margen"), autonomy over months (items 4, 5: progress over 3M-1A). **No evidence-backed rule** for any display window, cap or order above (all *design decisions*), nor for the stages breaks, injury and life changes, which no report in scope covers beyond the paused-by-discomfort state.

## 9. Open questions for the user (product decisions the evidence cannot settle)

1. **Block review content.** Adopt the item 1 table (coach card; Strength per group over the block; context line instead of "Entrenos" and "Series por grupo"; Main lifts with the 4 % margin and no block-best "Récord"; Records of the block; gated Weight with the "Tu plan" verdict; Waist; Outcomes; stall, phase and calorie cards)? **Recommended: yes.** Trade-off: the per-group set totals of the block are no longer a card (they remain in the builder preview and Estadísticas).
2. **Block review card order** by training intention, as the weekly report (R7)? **Recommended: yes**, one rule for both reports. Trade-off: the order differs between users with different goals.
3. **Outcomes chip:** "No conseguido" only after the outcome's deadline, no chip before it? **Recommended: yes.** Trade-off: an outcome far from its target shows no warning until the deadline (the figure "ahora x" still shows the gap).
4. **Rebuild the stored block reviews** in production (backup `inputs_v1`, progress rebuilt as of the block end, decisions frozen), as T-314 did for the weeks? **Recommended: yes**, with your explicit OK on the migration file before merge. Trade-off: a production data migration for probably very few rows; the alternative (old reviews keep the old layout) leaves two looks in "Informes".
5. **"Informes" list:** add the one-line progress teaser and "Semana N" per row (recommended); also group weeks under their block? **Recommended: teaser yes, grouping yes.** Trade-off: grouping changes the strict newest-first order.
6. **"Entrenos" (Estadísticas):** add "Tu progreso en este periodo" and the period's records on top, keep "Entrenos · Series" as a context line, remove the "Volumen" (tonnage) and "Duración" tiles? **Recommended: yes**, and change the "Ejercicios principales" rows to show the e1RM change rather than merging them into the new card. Trade-off: loses the tonnage and time totals that leading apps show.
7. **Radar and body map** in "Entrenos": keep at the bottom (recommended) or remove? Trade-off: they show balance between muscles, not progress; removing them shortens the screen.
8. **"Kilos por grupo muscular":** convert to weekly sets per group (recommended) or remove the screen? Trade-off: converting keeps a per-group history in the unit the coach uses; removing is less to maintain but drops the per-group weekly view.
9. **Calorías colours:** apply your Trainer-root rule here (one colour for every bar, target line kept, no amber over target)? **Recommended: yes.** Trade-off: over-target days are no longer visible at a glance (the bar height against the line still shows them).
10. **Calorías progress:** keep the screen a log with no progress card (recommended, as Q15 of T-310: nutrition shows where it explains progress, in the weekly report), or add a protein g/kg line against 1.6 g/kg (Morton 2018)? Trade-off: the protein line is evidence-backed but it is a nutrition target, not progress, and a biased-low diary can flag adequate intake.
11. **"Entreno completado":** replace "Volumen por ejercicio" with "Frente a la última vez" per exercise (state icon, "antes → ahora"), keep the records, fold duration and sets into one line? **Recommended: yes.** Trade-off: the per-exercise tonnage bars go away.
12. **Perfil highlight and data viewers:** name the exercise of the strength highlight (recommended) instead of replacing it; and confirm that Peso, Historial de peso, Actividad, Historial de entrenos and "Progreso del ejercicio" stay as they are (recommended: they are data viewers, Peso is already progress-first). Trade-off: a smaller remake; any later wish for them becomes a new task.

## 10. Blocking

Nothing blocks the user's decisions. Before the planner: (a) the count of stored `coach_block_reviews` in production (backend developer, read-only) sizes Q4; (b) `05` §8 decides whether `SessionsCard` and `GroupsCard` are deleted once the block review stops using them (item 1).
