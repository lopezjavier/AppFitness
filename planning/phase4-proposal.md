# Phase 4 proposal (not approved)

> **Status: PROPOSAL (product-owner brainstorm, 2026-11-16). Not approved, not in scope.** Nothing here changes [`01-scope.md`](01-scope.md): Phase 3 is still the last phase (ADR-101). If the user approves a Phase 4, a new ADR and the scope document record it, and the product-owner skill defines the phase and its epics. No story, task or ID is created by this document.

## 1. Purpose and how to read it

- The user asked to explore new functionalities, starting from [`10-feature-inventory.md`](10-feature-inventory.md), and then asked for a list that ignores the repository, the decisions already taken and the "100 % free" rule ("the sky is the limit").
- The list in §2 is therefore ranked on merit only. §5 lists, separately, what each item would reopen, so the cost of approving it is visible.
- The ideas are the product owner's. No other product was reviewed for this document, so nothing here is market evidence.
- Spanish lines with figures (for example "100 kg en press banca en unas 9 semanas") are examples of copy, not rules. Every training or nutrition rule goes through `fitness-scientist` and [`07-coach-design.md`](07-coach-design.md) before any story uses it.

**Value:** how often the user would meet the functionality and how much effort or doubt it removes (Very high, High, Medium, Low).

**Complexity:** how many layers it touches and how much of it is new. Sizing in tasks belongs to the epic-planner.

| Level | Meaning |
|---|---|
| Low | One layer, existing patterns |
| Medium | App and backend, existing patterns |
| High | New mechanics, or rules that need research or a trial first |
| Very high | A new platform or unproven technology |

## 2. The list

Within each group, the order is best value for the effort first.

### Best ratio: high value, moderate effort

| # | Functionality | What the user would experience | Value | Complexity |
|---|---|---|---|---|
| 1 | "Lo de siempre" | Diario offers one-tap suggestions learned from the diary, such as the weekday breakfast | High | Medium |
| 2 | Smart notifications | The phone speaks at the right moment: weigh-in, what is left before dinner, review ready | High | Medium |
| 3 | Express session | "I have 35 minutes": today's session is trimmed, keeping what matters most | High | Medium |
| 4 | Fill my day | Options made of the user's own foods that close the day's remaining kcal and protein | High in a cut | Medium |
| 5 | Weekly calorie budget | A dinner out is marked and the rest of the week adjusts so the weekly average holds | High | Medium |
| 6 | Voice logging | The meal is spoken and goes through the typed-meal flow | Medium–High | Low |
| 7 | Whole-plate photo | One photo, several foods separated, each editable | Medium–High | Medium |
| 8 | Forecasts | The charts project when a weight or a lift will be reached, with a band | Medium | Low–Medium |

### Big bets: high value, high effort

| # | Functionality | What the user would experience | Value | Complexity |
|---|---|---|---|---|
| 9 | Coach you can talk to, with actions | Questions about the user's data get answers; requests end in a proposal to accept | Very high | High |
| 10 | In-session auto-regulation | After the first set, the rest of the exercise is re-targeted from reps and RPE | High | Medium–High |
| 11 | Personal insights | The app studies the user's own history and says what works for them | High after months of data | High |
| 12 | Hands-free audio session | Earbuds announce the next set and the end of rest; sets are completed by voice | Medium–High | Medium–High |
| 13 | Guided progress photos | Dated photos with the same pose, a comparison slider and a timelapse | Medium–High | Medium–High |
| 14 | Technique check from video | A filmed set returns tempo, range, bar path and rep-by-rep slowdown | High if accurate | High |
| 15 | Watch companion | The watch shows the set, a tap completes it, heart rate is recorded | High | Very high |

### Moonshots and niche

| # | Functionality | What the user would experience | Value | Complexity |
|---|---|---|---|---|
| 16 | Automatic rep counting | The watch's motion sensors count the reps and propose the set | High if reliable | Very high |
| 17 | Blood test tracker | A lab report becomes markers with trends over the years | Medium | Medium |
| 18 | Diet quality | Fibre, ultra-processed share and micronutrient coverage, with foods that fill the gaps | Medium | Medium |
| 19 | Menu scanner | A photo of a restaurant menu ranks the dishes by fit to the remaining macros | Low–Medium | Low–Medium |
| 20 | Year in review | A yearly summary of records, volume and body changes | Low–Medium | Low–Medium |

## 3. Recommendation

**Proposed goal of a Phase 4: the app adapts to the day.** Phase 2 and 3 made the plan adapt week by week. The gaps left are daily: logging the same things by hand, reminders that are seen only when the app is already open, and sessions and questions that do not fit the plan.

**Recommended items: 1, 2, 3, 9 and 10.**

| Item | Why it is in |
|---|---|
| 1 "Lo de siempre" | It removes the most repeated action in the app, several times a day, with no model |
| 2 Smart notifications | Every reminder the app already computes becomes useful without opening it |
| 3 Express session | Short days are frequent, and today the user cuts the session without knowing what matters |
| 10 In-session auto-regulation | It improves every session; the plan stops being fixed for the day |
| 9 Coach you can talk to | It answers every case that has no screen; it is the largest step in what the app can do |

**Proposed grouping and order** (working names; no epic IDs are assigned):

1. **Faster logging:** item 1, with item 6 as a cheap add-on if typed meals are used often.
2. **Smart notifications:** item 2.
3. **Sessions that adapt:** items 3 and 10. Both change today's session by rule, and both need `fitness-scientist` research, which can start while the first two groups are built.
4. **Coach you can talk to:** item 9, in two steps: questions about the data first, actions second. It goes last because it is the most expensive, it needs a trial, and its actions can reuse what the earlier groups add.

**Second wave**, if the first goes well: items 4, 5, 7 and 8 (nutrition), then 11 and 13.

**Only after a trial shows it works:** item 14.

**Depends on the watch model:** items 15 and 16.

**Optional and small:** items 17 to 20.

**Not recommended even with full freedom:** water tracking and social features (little value for one person), and body fat estimated from photos (not reliable).

## 4. Item details

### 1. "Lo de siempre": entries suggested from the diary

- **Problem:** most days repeat. The same breakfast is logged by hand every morning. Default comidas per meal and weekday (US-51) help only after being set up by hand, and only for whole comidas.
- **Experience:** each meal in Diario shows a suggestion built from what was logged for that meal on similar days, for example "Lo de siempre: avena con leche, café". One tap logs it with the usual amounts; the entries are editable like any other.
- **Simplest version:** per meal and weekday, the most frequent set of foods and amounts of the last weeks; one suggestion per meal; no model.
- **Fuller version:** several suggestions, awareness of training days (the usual post-workout meal), and learning from dismissed suggestions.
- **Builds on:** the diary history, `most_eaten_foods`, saved comidas and default comidas (US-17, US-21, US-51, US-121).
- **Value:** High. It is met several times a day.
- **Complexity:** Medium. Pattern detection over the diary and one new element in Diario.
- **To decide and risks:** whether it replaces "Comidas por defecto" or sits beside it. Nothing is ever logged without a tap, so a wrong suggestion costs nothing.

### 2. Smart notifications

- **Problem:** reminders exist only inside "Centro de avisos" and are computed when the app opens, so a reminder is seen only when the user is already in the app.
- **Experience:** system notifications at the moment they help: the weigh-in in the morning when there is none, "Te quedan 600 kcal y 40 g de proteína" before the usual dinner time, the weekly summary or the block review when ready. Each type can be turned off, and a tap opens the right screen.
- **Simplest version:** three types (weigh-in, weekly summary with its check-in, block review) at a time the user chooses.
- **Fuller version:** timing learned from habits, the remaining-macros nudge, quiet hours and a training-day reminder.
- **Builds on:** "Centro de avisos" and its reminder rules (US-57, US-58, US-109) and the rest-end Android notification (US-64).
- **Value:** High.
- **Complexity:** Medium. Background scheduling on Android, the notification permission, and data that must be available without opening the app.
- **To decide and risks:** notification fatigue, so few types and all optional. It reverses "reminders in the app only" (see §5).

### 3. Express session ("Tengo 35 minutos")

- **Problem:** some days there is less time than the routine needs. Today the user skips exercises by hand or stops early, without knowing what matters most.
- **Experience:** before or during the session, "Tengo poco tiempo" asks for the minutes available. The app shows the trimmed session (what stays, what is paired, what is dropped) and the user accepts or edits it. The routine itself does not change, and the weekly summary knows the session was shortened on purpose.
- **Simplest version:** a priority order inside the routine, and exercises dropped from the end until the estimated duration fits.
- **Fuller version:** accessories paired as supersets, fewer sets in place of dropped exercises, and dropped work offered on another day of the week.
- **Builds on:** routines and the live session (US-10, US-11) and the block (US-72).
- **Value:** High. It happens often in real life.
- **Complexity:** Medium. The trimming rule, a duration estimate, one confirmation screen and the effect on the adherence figures.
- **Needs first:** `fitness-scientist` decides what to keep when time is short.

### 4. Fill my day ("¿Qué como ahora?")

- **Problem:** late in the day the remaining kcal and protein are known, but turning them into a meal is trial and error in the search.
- **Experience:** a button on the Diario dashboard shows three options made of the user's own foods and comidas, with amounts, that fit what is left. One tap logs an option, editable afterwards.
- **Simplest version:** single foods and saved comidas scaled to the remainder and ranked by fit; no model.
- **Fuller version:** combinations of two or three foods, foods that suit the meal (dinner foods at dinner), and dishes proposed by a model beyond the user's own foods.
- **Builds on:** `food_diary_summary`, personal foods, comidas and `most_eaten_foods` (US-19, US-20, US-21).
- **Value:** High during a cut, medium otherwise.
- **Complexity:** Medium.
- **To decide and risks:** it sits near "meal plans" (§5). The options are suggestions for one meal, not a prescription.

### 5. Weekly calorie budget ("Cena fuera")

- **Problem:** targets are daily and life is weekly. One dinner out shows a day far over target although the week can still be on plan, and the user does that arithmetic by hand.
- **Experience:** Diario shows the week's balance next to today's. The user can mark a meal ahead ("Cena fuera el sábado") and the app spreads the difference over the other days within limits. The weekly summary judges the weekly average.
- **Simplest version:** a weekly balance view (over or under so far, and what that leaves per remaining day) with no change to the targets.
- **Fuller version:** planned events that move the daily targets inside a safe band, with protein held daily.
- **Builds on:** the calorie target and macros (US-07, US-08), the nutrition section of the weekly summary (US-112…US-114) and the calorie changes of the coach (US-99).
- **Value:** High.
- **Complexity:** Medium (the simplest version: Low–Medium). It touches the targets, the dashboard and the adherence maths of the coach.
- **Needs first:** `fitness-scientist` decides how far a day may move and how the coach reads such weeks.

### 6. Voice logging

- **Problem:** typing a meal with busy hands.
- **Experience:** a microphone in "Describir comida": the user speaks, sees the transcription and confirms. The draft flow is the same as for typed text.
- **Simplest version:** the phone's speech-to-text fills the existing text field.
- **Fuller version:** voice from the Diario top bar straight to a draft; voice for sets is item 12.
- **Builds on:** "Describir comida" (US-132).
- **Value:** Medium–High, depending on how much typed meals are used.
- **Complexity:** Low.
- **To decide and risks:** transcription errors on Spanish food names; the draft stays editable and the audio is not stored.

### 7. Whole-plate photo

- **Problem:** a photo of a plate with several foods gives one combined food. Correcting one component (more rice, no sauce) means editing totals by hand, and the components cannot be reused.
- **Experience:** one photo; the app lists the foods it sees, each with its amount and macros. The user adjusts or removes each one and logs them together in the meal.
- **Simplest version:** the model returns a list of items; each one is an editable draft, saved as separate entries.
- **Fuller version:** each item is matched with the user's own foods first, so known foods keep their verified values, and corrections are remembered.
- **Builds on:** the AI photo flow and its energy check (US-48, US-49, US-54, ADR-104).
- **Value:** Medium–High.
- **Complexity:** Medium. A draft screen with several items, each of which must pass the energy check.
- **To decide and risks:** per-item portions are less reliable than one total, and without matching every photo creates several personal foods.

### 8. Forecasts

- **Problem:** the charts show the past. "When do I get there?" is answered by eye.
- **Experience:** the weight chart and the exercise progress chart continue as a dotted projection with a band, and one line states it, for example "A este ritmo: 75 kg hacia el 12 de febrero" or "100 kg en press banca en unas 9 semanas".
- **Simplest version:** the weight trend projected to the date of the plan's outcome.
- **Fuller version:** lifts (estimated 1RM), honest bands, a "not enough data" state and the projection compared with the plan's outcome.
- **Builds on:** the weight trend (US-27), strength and exercise progress (US-15, US-39…US-41) and the plan outcomes (US-69).
- **Value:** Medium.
- **Complexity:** Low–Medium.
- **Needs first:** `fitness-scientist` decides which projection is defensible (strength does not grow in a straight line) and when to show nothing.
- **Risk:** false precision; hence the band and a minimum of data.

### 9. Coach you can talk to, with actions

- **Problem:** every real-life case needs its own screen and every question about the data needs its own chart. The cases without a screen (a trip, a sore week, a swap for this block, "what was my average protein in October?") have no answer in the app.
- **Experience:** a chat in Trainer. Questions about the user's own data are answered with the figures and where they come from. Requests such as "viajo de jueves a domingo" or "cambia el press inclinado en este bloque" end in a proposal card (what changes, before and after) that the user accepts or discards. Nothing changes without acceptance.
- **Simplest version:** questions only: read-only, over a fixed set of data queries, answered with figures.
- **Fuller version:** actions through the operations the app already has (deload, extend the week, swap an exercise, new block), each one a proposal to confirm, with undo where it exists today.
- **Builds on:** the exercise chat (US-130), the coach operations (US-70, US-75, US-93, US-100, US-108, US-134) and the stored reports (US-129).
- **Value:** Very high.
- **Complexity:** High. A layer between the model and the data, limits on what the model may do, and checks against answers with wrong figures.
- **To decide:** a free model or a paid one (quality and usage limits). In both cases the numbers stay deterministic: the model chooses an operation and the existing rules compute it.
- **Needs first:** a trial with real questions on the free tier before committing.
- **Risk:** a confident wrong answer about the user's data. Mitigation: answers come only from query results and show their source.

### 10. In-session auto-regulation

- **Problem:** the targets of a session are fixed before it starts. On a bad day the planned load is too heavy for the remaining sets, on a good day too light, and the adjustment is made by feel.
- **Experience:** after the first working set of an exercise, when reps or RPE are off target, the next sets show an adjusted target with a one-line reason, for example "Hoy un poco menos: 77,5 kg × 8". The user completes the set as usual or types their own values.
- **Simplest version:** only the load of the remaining sets of the same exercise changes, and only as a suggestion in the set's ghost data.
- **Fuller version:** reps or sets change too, the day's state carries to later exercises of the same muscle group, and the result feeds the next session's progression.
- **Builds on:** the live session with its ghost data and RPE (US-11…US-13, US-29…US-32, US-38) and the progression inside the block (US-97, US-104).
- **Value:** High. It acts in every session.
- **Complexity:** Medium–High. The rule, the live-session change and its interaction with the weekly progression.
- **Needs first:** `fitness-scientist` researches the auto-regulation rule and its evidence.

### 11. Personal insights

- **Problem:** the app stores months of training, food, sleep, steps and discomfort and only reports them. It does not say what works for this user.
- **Experience:** a section "Lo que funciona para ti" with a few findings, each with its evidence and how sure it is, for example "Tu pecho progresa mejor con 12-16 series semanales" or "Con menos de 6 h de sueño haces de media una repetición menos". A finding appears only when there is enough data.
- **Simplest version:** two fixed analyses (weekly sets per muscle against progress, sleep against session performance), recomputed weekly.
- **Fuller version:** more analyses (rest time, time of day, what precedes a discomfort), and findings that feed the block builder.
- **Builds on:** the set history, muscle volume and stats (US-15, US-39…US-41), watch data (US-61…US-63), the discomfort log (US-67) and the check-in energy (US-59).
- **Value:** High after months of data, none at the start.
- **Complexity:** High. Statistics for one person that must not mislead.
- **Needs first:** `fitness-scientist` decides which analyses are defensible for one person and the minimum data for each.
- **Risk:** coincidences shown as findings.

### 12. Hands-free audio session

- **Problem:** the phone is taken out and unlocked for every set.
- **Experience:** with earbuds, the app announces the next set ("Press banca, 80 kilos, 8 repeticiones") and the end of the rest. The user says "hecho" or "hecho, ocho a ochenta" and the phone stays in the pocket.
- **Simplest version:** spoken announcements only (next set, end of rest), no voice input.
- **Fuller version:** voice commands to complete a set with its values, skip it or add a note.
- **Builds on:** the live session and the rest alert (US-11, US-13, US-64).
- **Value:** Medium–High.
- **Complexity:** Medium–High (the simplest version: Low–Medium). Speech recognition in a noisy gym is the hard part.
- **Risk:** a recognition error writes a wrong set; a spoken confirmation and an easy undo are required.

### 13. Guided progress photos

- **Problem:** weight and circumferences do not show changes in shape, above all in a recomposition, where weight barely moves.
- **Experience:** in "Peso", a photo session (front, side, back) with a ghost outline of the previous photo to repeat the pose, a comparison slider between two dates and a timelapse, each date shown with its weight and measures.
- **Simplest version:** dated photos, compared side by side.
- **Fuller version:** the outline, automatic alignment, the timelapse and a reminder together with the circumference reminder.
- **Builds on:** "Peso" and the circumferences (US-25, US-27) and the circumference reminder (US-58).
- **Value:** Medium–High.
- **Complexity:** Medium–High. Camera, storage and data protection.
- **To decide:** where the photos live (on the device only: private, but lost with the phone; in the cloud: backed up, but sensitive data stored remotely) and whether the data export includes them.

### 14. Technique check from video

- **Problem:** there is no feedback on how a lift was done, and RPE is subjective.
- **Experience:** the user films a set from the app and gets, per rep, tempo, range and bar path, how much the last reps slowed down (an objective effort signal) and two or three cues.
- **Simplest version:** rep timing and slowdown for barbell lifts filmed from the side.
- **Fuller version:** range and bar path, cues, comparison with an earlier video, and the slowdown offered as the set's RPE.
- **Builds on:** the live session and RPE (US-11, US-29…US-32) and the exercise chat (US-130).
- **Value:** High if accurate, negative if it gives wrong cues.
- **Complexity:** High. Accuracy is unproven and filming conditions in a gym vary.
- **Needs first:** a trial on the user's real videos before any commitment, and `fitness-scientist` on which measures are valid.

### 15. Watch companion

- **Problem:** the same as item 12, and no heart rate during the session.
- **Experience:** the watch shows the current set, a tap completes it, the rest ends with a vibration and the heart rate is recorded with the session.
- **Simplest version:** the rest timer and "set done" on the watch, with the phone still the main screen.
- **Fuller version:** reps and load edited on the watch, heart rate in the session summary and cardio pace guided by heart rate.
- **Builds on:** the live session (US-11, US-13).
- **Value:** High for a wearer of a Wear OS watch.
- **Complexity:** Very high. A second app with its own UI, sync and release.
- **Depends on:** the user's watch model (§6).

### 16. Automatic rep counting

- **Problem:** every set is entered by hand.
- **Experience:** the watch's motion sensors detect the reps and the set is proposed as done with its count; the user confirms it.
- **Simplest version:** counting for a few regular lifts.
- **Value:** High if reliable.
- **Complexity:** Very high. It requires item 15 and a motion model per exercise, with unknown accuracy.
- **Needs first:** item 15 and a trial.

### 17. Blood test tracker

- **Problem:** lab results live in PDFs and trends across years are invisible.
- **Experience:** a photo or PDF of a report; the app extracts the markers into an editable draft, and each marker gets a chart across reports with the reference range printed on the report.
- **Simplest version:** manual entry of a few markers with a chart.
- **Fuller version:** extraction from a photo or PDF and grouping by panel.
- **Builds on:** the "photo to editable draft" pattern of the food photos (US-48, US-49, US-54).
- **Value:** Medium. It is used once or twice a year, but nothing else in the app covers it.
- **Complexity:** Medium.
- **Limits:** it stores and shows, with no interpretation and no advice. It would be the most sensitive data in the app, so data protection is an explicit requirement.

### 18. Diet quality

- **Problem:** the diary judges kcal and macros only. Two days with the same macros can differ a lot in quality.
- **Experience:** a weekly view with fibre per day, the share of ultra-processed foods, the coverage of key micronutrients and which of the user's foods fill a gap.
- **Simplest version:** a daily fibre total in Diario.
- **Fuller version:** micronutrient coverage against reference intakes and the processing level from Open Food Facts data.
- **Builds on:** micronutrients per food and the full Open Food Facts data (US-118, US-119).
- **Value:** Medium.
- **Complexity:** Medium (the simplest version: Low–Medium).
- **Needs first:** `fitness-scientist` for the reference values.
- **Risk:** AI-estimated and manual foods often lack micronutrients, so totals under-count; the view must show how much of the diary has data.

### 19. Menu scanner

- **Problem:** choosing in a restaurant with a target in mind.
- **Experience:** a photo of the menu; the dishes are listed with an estimate and ranked by fit to the remaining kcal and protein. One tap logs the chosen dish as a draft.
- **Builds on:** the AI photo flow and "Describir comida" (US-48, US-132).
- **Value:** Low–Medium. Only when eating out.
- **Complexity:** Low–Medium.
- **Risk:** an estimate from a menu line is rougher than one from a plate photo, and must be shown as such.

### 20. Year in review

- **Problem:** a year of work is never shown as a whole.
- **Experience:** once a year, or on demand, a summary: sessions, volume, records, change in weight and measures, most eaten foods and blocks completed.
- **Builds on:** stats and records (US-15, US-28, US-39…US-41) and the stored reports (US-129).
- **Value:** Low–Medium. Once a year, but motivating.
- **Complexity:** Low–Medium.

## 5. What a Phase 4 would reopen

The list ignores past decisions on purpose. This table shows what approving each item would change. Every item reopens ADR-101 (Phase 3 is the last phase; maintenance mode after E21).

| Item | Decision it touches |
|---|---|
| 1 "Lo de siempre" | [`01-scope.md`](01-scope.md) §4 "faster-logging extras" (same family; this item is not named there) |
| 2 Smart notifications | ADR-73 (reminders in the app only, no system notifications); ADR-74 (no background sync) |
| 4 Fill my day | `01-scope.md` §4 "meal plans" (adjacent: options for one meal, not a plan) |
| 5 Weekly calorie budget | `01-scope.md` §4 "refeeds" (adjacent); the calorie rules of `07` |
| 6 Voice logging, 12 Audio session | `01-scope.md` §4 "voice … food logging" |
| 7 Whole-plate photo | `01-scope.md` §4 "whole-meal photo estimation" |
| 8 Forecasts | `01-scope.md` §4 "a calendar-style long-term plan and an event date" (adjacent) |
| 9 Coach you can talk to | `01-scope.md` §4 "the coach chat"; ADR-103 (the exercise chat is the only chat; paid models are out); ADR-97…ADR-99 (the coach is deterministic) |
| 13 Progress photos | Phase 1 scope, out of scope: progress photos and Supabase Storage |
| 14 Technique check | `01-scope.md` §4 "video analysis" |
| 17 Blood test tracker | `01-scope.md` §4 "medical advice" (adjacent: tracking only) |
| 18 Diet quality | E4 scope line: micronutrient targets or totals are out |
| 20 Year in review | `01-scope.md` §4 and ADR-66 (monthly report and year in review) |
| 3, 10, 11, 15, 16, 19 | No recorded decision found. Item 3 is near "Adaptar el bloque" (§4), which changed the block, not one session |

## 6. Open questions

| # | Question | Recommendation |
|---|---|---|
| 1 | Which items enter a Phase 4? | Items 1, 2, 3, 9 and 10, in the order of §3 |
| 2 | Item 9: free model only, or a paid model? | Run the trial on the free tier first; decide with its results |
| 3 | Which watch does the user wear? | Items 15 and 16 make sense only on a Wear OS watch; leave them out until answered |
| 4 | Where does a Phase 4 sit relative to E20 Illness and E21 Closure? | E20 as planned; the data export of E21 at any time; the private repository and the retro after Phase 4, because they assume the project has ended |
| 5 | Item 2 needs system notifications. Reverse ADR-73? | Yes, limited to the notification types the user turns on |
| 6 | Which items need a trial before any commitment? | Item 9 (answer quality on the free tier) and item 14 (accuracy on real videos) |

## 7. Appendix: ideas not in the list

**Discarded by the user in the brainstorm (2026-11-16):**

- Offline-proof live session: the gym has signal and no sets were lost.
- Watch-recorded cardio counted towards the plan: there is no cardio outside the routines.
- A recipe made from a comida with its cooked weight: no value seen.

**Smaller ideas from the first round** (based on the inventory; not discussed further):

- A live record notice during the session (deferred by ADR-52).
- App icon shortcuts: "Añadir alimento", scan a barcode, start the next workout.
- Lock-screen session controls: the rest countdown and a "set done" action.
- A body-fat estimate from the circumferences (`fitness-scientist` first).
- A home-screen widget: remaining kcal and protein, and the next routine.
- The exercise chat keeps the thread while the screen is open.
- A daily fibre total, now the simplest version of item 18.
