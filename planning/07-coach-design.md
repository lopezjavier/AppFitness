# Coach design: evidence base and default rules

Design base of the performance assistant (E13) and of the data it needs (E10-E12). Decisions: ADR-76 (coach), ADR-77 (AI provider and cost), ADR-78 (profile and catalogue).

**Principle:** every rule has a source, a common practice or a "design decision" label. No figure exists for convenience. Every source was checked before the E13 breakdown (2026-10-29, §10).

## 1. What the coach does

**User inputs:**
- **Intention:** "Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición" or "Mantenimiento".
- **Static preferences** (Preferencias): training place (home, gym, other), available equipment and, optionally, days per week and session length.
- **Discomfort:** logged per exercise when it happens (E12). It is not a plan input; it blocks load increases (§7).

**Output:** a plan in 4-week blocks (design decision, §9) with:
- the routines of each week, using the existing routine model;
- calories and macros;
- progression per exercise;
- expected outcomes (weight, body fat, circumferences or e1RM) **only when the plan can justify them**. The user never enters target numbers.

**Review:** at the end of each block; earlier only if the preferences change or the user asks.

**Acceptance:** the user accepts or edits the whole plan. Adjustments inside a block are proposals accepted one by one. Nothing is applied without acceptance.

**Division of work:** every calculation (BMR, activity, adaptive expenditure, weight trend, progression, e1RM, adherence, deload trigger) is deterministic, in SQL or TypeScript, and testable. The model only selects within validated options and explains; its output is structured (JSON schema) and validated before it is shown. The model never writes to the database.

## 2. Derived values (confirmed by the user)

| Value | How it is computed | Confidence without history |
|---|---|---|
| Basal expenditure | Mifflin-St Jeor with sex, age, height and weight (profile, `04` §1) | High (validated equation) |
| Activity level | Starts from `profiles.activity_level` (ADR-78); the coach proposes a change from Health Connect steps (bands of Tudor-Locke et al. 2011), active calories when there are no steps, and the completed sessions per week (3 or more raise a measured level by one). Exact mapping in `04` §7.1. Sessions alone never change the profile level (it already includes training) | Low: shown as provisional |
| Total expenditure | Initially BMR × activity factor (`04` §1.3); then adaptive expenditure from logged intake and weight change | Improves with months of data |
| Weight trend | Slope of weekly averages, with its confidence interval (§5) | Depends on the number of weigh-ins |
| Experience | Training history | Without history: beginner, lowest volume of §3 |

**Confirmation rule:** each block shows a card with the derived values, their source and the missing data. The user accepts or edits them. A value is not used until confirmed; until then it is shown as provisional. A confirmed value is asked again only when its new estimate falls outside the uncertainty of the confirmed one.

**Intake completeness:** adaptive expenditure uses only days with a logged diary; days without entries are excluded, not counted as zero. Below 70 % logged days in the window (design decision) the estimate is shown as unreliable and the coach does not propose calorie changes from it.

## 3. Training

- **Frequency:** each muscle group at least twice a week when the available days allow it (two or more training days); with one day, one full-body session. When weekly volume is equated, frequency itself shows no clear hypertrophy difference [Schoenfeld, Grgic, Krieger 2019]; twice a week is the practical way to spread the volume [Schoenfeld, Ogborn, Krieger 2016]. *Common practice built on that evidence.*
- **Volume:** starting point of 10 weekly sets per muscle group. Dose-response is positive [Schoenfeld, Ogborn, Krieger 2017], with diminishing returns at higher volumes, much more pronounced for strength than for hypertrophy [Pelland et al. 2026].
- **Intensity:**
  - Strength: heavy loads, 1-6 reps.
  - Hypertrophy: 6-15 reps. Low and high loads give similar hypertrophy when sets are taken close to failure [Schoenfeld, Grgic et al. 2017].
- **Proximity to failure:** 1-3 reps in reserve on main work. Training to momentary failure shows no hypertrophy advantage over stopping short of it, and set failure only a trivial one [Refalo et al. 2023]; the 1-3 range is *common practice built on that evidence*. Reps in reserve are derived from the logged RPE (RIR = 10 − RPE); there is no separate RIR field (ADR-76).
- **Progression:** double progression. Work inside the rep range; when every working set reaches the top of the range at RPE 8 or less, raise the load to the next available increment. *Common practice; little comparative evidence.*
- **Failed sessions:** when most working sets miss the bottom of the range in two consecutive sessions of the exercise, lower the load 5 %, rounded to the available increment. *Design decision.*
- **Rest between sets:** 2-3 min on main multi-joint lifts, 1-2 min on isolation exercises. Trained lifters need more than 2 min to maximise strength [Grgic et al. 2018]; 3 min beat 1 min for strength and thigh hypertrophy [Schoenfeld et al. 2016]; for hypertrophy alone the difference is small [Singer et al. 2024]. The block creator writes these rests into the routines.
- **Exercise order:** the priority multi-joint lifts go first in the session; strength improves most on exercises done early, hypertrophy is similar in any order [Nunes et al. 2021].
- **Periodization ("Ganar fuerza"):** load and reps vary within the week across the block (undulating) instead of one fixed range; a small strength benefit, larger than linear periodization in trained lifters, and no hypertrophy difference [Moesgaard et al. 2022; Williams et al. 2017]. Other intentions keep one rep range per exercise.
- **Range of motion:** full range by default; differences against partial range are small [Pallarés et al. 2021; Wolf et al. 2023]. A technique note, not a computed rule.
- **Cardio:** concurrent aerobic training does not compromise hypertrophy or maximal strength [Schumann et al. 2022], so the coach does not reduce cardio or steps for strength or muscle goals.
- **Warm-up:** ramp-up sets on main lifts. *Common practice.*
- **Exercise selection:** the catalogue is filtered by place and equipment; substitutions keep the muscle group and the movement pattern (`exercises.movement_pattern`, ADR-78).
- **Main lifts:** the "main" or "priority multi-joint" lifts are the exercises whose `movement_pattern` is squat, hinge, lunge, horizontal or vertical push or pull. *Design decision* (user, 2026-10-29, E13 Q3).
- **Rep range:** each routine exercise carries an optional rep range, per session (so undulating weeks keep their ranges); without one, no progression is suggested (user, 2026-10-29, E13 Q2).
- **Deload:** suggested when no main lift (done in each of the three weeks) improved its e1RM over the last three weeks of sessions, and only inside an accepted block; the deload week keeps the weights and cuts working sets by 40 %, rounded to the nearest whole set and at least 1 per exercise. *Design decision* (thresholds from common practice; scope and rounding by the user, 2026-10-29, E13 Q3). Soreness is not a signal (§7). Implementation (T-165, `04` §9.10): a lift improved iff `max(w2, w3) > w1` over the weekly best e1RM of plan weeks `c-2..c` (strict, ties are not improvement); it is evaluated in week `c >= 3` and targets week `c + 1`, so a 4-week block can only get a deload in week 4 (evaluated in week 3).

## 4. Energy and macros

- **Calorie floor:** a plan never goes below 1200 kcal (women) / 1500 kcal (men) (ADR-25).
- **Protein:** at least 1.6 g/kg, plateau around 2.2 g/kg [Morton et al. 2018]. In a deficit with high lean mass, the upper end: 2.3-3.1 g/kg of fat-free mass [Helms, Zinn, Rowlands, Brown 2014]. Shown as guidance, not a meal plan: about 0.4 g/kg per meal over at least 4 meals [Schoenfeld, Aragon 2018].
- **Fat:** at least 20 % of calories; usual range 20-35 %; below 20 % gives no performance benefit [Rodriguez, DiMarco, Langley 2009, ADA/DC/ACSM position]. (The 2016 update [Thomas, Erdman, Burke 2016] could not be checked for these figures, so it is not cited for them.)
- **Rate of loss:** 0.5-1 % of body weight per week [Helms, Aragon, Fitschen 2014]; default 0.5 %. (ACSM 2009 states 0.5-1 kg per week for the general population; the percentage suits lean trainees better.)
- **Rate of gain:** 0.25-0.5 % of body weight per week [Iraki et al. 2019; slower for advanced lifters].
- **Energy constant:** 7 700 kcal per kg is an approximation, used only without history; with history the adaptive estimate is used [Hall 2008; Hall et al. 2011].
- **Adjustment inside a block:** proposed only when the plan's target rate is outside the confidence interval of the weight trend for two consecutive weeks; the proposal changes calories by at most 10 % (*design decision*) and needs acceptance.

## 5. Weight and measurements

- **Daily weigh-in** recommended: day-to-day changes of 1-2 kg from water, glycogen and gut content make a single weigh-in uninformative.
- **Trend:** slope over weekly averages, shown with its confidence interval computed from the real spread of the user's weigh-ins. There is no minimum of data to use the app; data only changes the confidence shown.
- **Reminders (E10):** weight after 2 days in a row without a weigh-in; circumferences after 14 days without measuring (*design decision*: circumferences change slowly).
- **Body composition:** impedance body fat is unreliable; circumferences are a secondary reference and their uncertainty is shown.

## 6. Outcomes set by the coach

- Weight, body fat, circumferences or e1RM per lift, depending on the intention and the plan. (T-161 ships weight and e1RM per lift: the figures are derived by the server from the summary and the model only picks, explains and sets the deadline; body fat and circumferences wait for data the summary does not carry.)
- Each outcome carries its reason and its deadline (normally the end of the block).
- The user cannot set an outcome's value; they can turn an outcome off. `profiles.target_weight_kg` stops being a user input (ADR-78).

## 7. Discomfort and safety

- Logging discomfort on an exercise (E12) blocks load increases for that exercise for 7 days, or until the user confirms "Ya no me molesta".
- The user picks a **level** (1 to 5, ADR-81) when logging a discomfort; the two highest levels (swelling, tingling or numbness; sharp pain or injury) are severe. The level, not the text, drives the coach. No health message is shown. Levels 1-3 block load increases only (the reps rule and the 5 % drop still apply) and the suggestion's reason states neutrally that the load is kept because of the open discomfort; levels 4-5 give no suggestion for that exercise (E13 Q1, user 2026-10-29). No diagnosis and no medical advice.
- Free text (discomfort or exercise notes) is context for the user only: it is never matched against terms and never interpreted to diagnose.
- Muscle soreness is not collected: it is a poor indicator of recovery or of adaptation [Schoenfeld, Contreras 2013; Nosaka et al. 2002].

## 8. Data, privacy and cost

- Personal use: no prior consent screen, by user decision. Before distributing the app to other people, each must accept the use of their data and the coach provider's terms; this condition is written, not implemented.
- The `coach` Edge Function sends a compact summary (last 8 weeks at most), never raw tables or photos. `coach_summary` (`04` §7.8) caps it at 16 384 bytes (about 4 000 tokens) and excludes all free text (discomfort, set and routine notes, check-in notes, food names, custom exercise names), raw rows and photos; exercises go by catalogue key or custom id.
- Cost control by money, not by calls (ADR-77): 2 € per user and 5 € in total per calendar month, plus a spending limit on the Anthropic account.
- Deleting the account deletes the coach's plans, recommendations, outcomes, usage and conversations (ADR-29); "Eliminar historial del entrenador" deletes the plans, recommendations, outcomes, reports and conversations without deleting the account; the confirmed derived values and the AI usage stay (E13 Q5, Q13).

## 9. Design decisions without evidence backing

Defaults chosen for this app; adjusted with real data, not scientific figures:
- Block length: 4 weeks (planning convention).
- Load increments: those available in the user's equipment (plates or dumbbells, E6).
- Place defines the default equipment; the user can change it.
- 5 % load drop after two failed sessions; 40 % fewer sets in a deload; three weeks without e1RM improvement as the deload trigger; at most 10 % calorie change per adjustment; 70 % logged days for a reliable intake; 14 days between circumference reminders; sleep below 6 h mentioned in the report (below the 7 h adult recommendation).
- Activity level (T-157, `04` §7.1): the active-kcal bands (150 / 300 / 500 / 750 per day), the 28-day window with 7 days minimum per source, the +1 level for 3 or more sessions per week, and the uncertainties 0.1 (data) and 0.2 (profile only). The step bands are published (§10, ref. 28).
- Experience (T-157, `04` §7.5): 24 sessions and 12 weeks for intermediate, 150 sessions and 52 weeks for advanced.
- Nutrition plan (T-161, `04` §8): rate 0 and adjustment 0 for strength, recomposition and maintenance; gain rate by experience 0.5 / 0.35 / 0.25 % per week (the range 0.25-0.5 is sourced, the split by experience follows "slower for advanced"); protein 2.2 / 2.0 / 1.8 g/kg by case (the lean-mass formula of §4 is not used: there is no body-fat input); fat default 25 %; 7 700 kcal per kg for every rate-to-deficit conversion (§4 "only without history" is read as: the adaptive expenditure of `04` §7.2 replaces BMR x factor when available); minimum change 50 kcal; at least 14 days between calorie changes (so the two weeks restart after a decision); a floor-limited plan keeps its slower rate instead of failing.
- Outcome candidates (T-161, `04` §8.4): weight only for loss and gain; e1RM per main lift by a 4-week extrapolation of the user's own least-squares slope over at least 3 weeks with data; body fat and circumferences are deferred (nothing to derive them from in the summary).
- Coach summary: 16 384-byte cap and the 12 main lifts / 10 discomforts / 8 muscle groups per week lists (`04` §7.8); fallback trend uncertainty of 0.5 kg per week without an interval.

## 10. Sources

Corrected against the earlier draft (frequency, rate of loss, Hall year). Checked on 2026-10-29 before the E13 breakdown: Pelland (published version replaces the 2024 preprint), Refalo (finding restated), Iraki (0.25-0.5 %/week confirmed), Nosaka (confirmed); the fat range is now cited to the 2009 position, where it was confirmed.

1. Schoenfeld BJ, Ogborn D, Krieger JW. Effects of resistance training frequency on measures of muscle hypertrophy: a systematic review and meta-analysis. *Sports Med*. 2016.
2. Schoenfeld BJ, Grgic J, Krieger J. How many times per week should a muscle be trained to maximize muscle hypertrophy? A systematic review and meta-analysis. *J Sports Sci*. 2019.
3. Schoenfeld BJ, Ogborn D, Krieger JW. Dose-response relationship between weekly resistance training volume and increases in muscle mass: a systematic review and meta-analysis. *J Sports Sci*. 2017.
4. Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. The resistance training dose response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gains. *Sports Med*. 2026;56(2):481-505. doi:10.1007/s40279-025-02344-w (preprint SportRxiv 2024).
5. Schoenfeld BJ, Grgic J, Ogborn D, Krieger JW. Strength and hypertrophy adaptations between low- vs. high-load resistance training: a systematic review and meta-analysis. *J Strength Cond Res*. 2017.
6. Refalo MC, Helms ER, Trexler ET, Hamilton DL, Fyfe JJ. Influence of resistance training proximity-to-failure on skeletal muscle hypertrophy: a systematic review with meta-analysis. *Sports Med*. 2023;53(3):649-665. doi:10.1007/s40279-022-01784-y.
7. Morton RW et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. *Br J Sports Med*. 2018.
8. Helms ER, Zinn C, Rowlands DS, Brown SR. A systematic review of dietary protein during caloric restriction in resistance trained lean athletes. *Int J Sport Nutr Exerc Metab*. 2014.
9. Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. *J Int Soc Sports Nutr*. 2014.
10. Iraki J, Fitschen P, Espinar S, Helms E. Nutrition recommendations for bodybuilders in the off-season: a narrative review. *Sports*. 2019;7(7):154.
11. Thomas DT, Erdman KA, Burke LM. Position of the Academy of Nutrition and Dietetics, Dietitians of Canada, and the American College of Sports Medicine: nutrition and athletic performance. *J Acad Nutr Diet*. 2016;116(3):501-528. (Current position; not cited for the fat figures.)
11b. Rodriguez NR, DiMarco NM, Langley S. Position of the American Dietetic Association, Dietitians of Canada, and the American College of Sports Medicine: nutrition and athletic performance. *J Am Diet Assoc*. 2009;109(3):509-527.
12. Donnelly JE et al. (ACSM). Appropriate physical activity intervention strategies for weight loss and prevention of weight regain for adults. *Med Sci Sports Exerc*. 2009.
13. Hall KD. What is the required energy deficit per unit weight loss? *Int J Obes*. 2008.
14. Hall KD et al. Quantification of the effect of energy imbalance on bodyweight. *Lancet*. 2011.
15. Mifflin MD et al. A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr*. 1990.
16. Schoenfeld BJ, Contreras B. Is postexercise muscle soreness a valid indicator of muscular adaptations? *Strength Cond J*. 2013.
17. Nosaka K, Newton M, Sacco P. Delayed-onset muscle soreness does not reflect the magnitude of eccentric exercise-induced muscle damage. *Scand J Med Sci Sports*. 2002;12(6):337-346.

Added by the gap review of 2026-10-29 (user-approved; rules in §3-§4):

18. Grgic J, Schoenfeld BJ, Skrepnik M, Davies TB, Mikulic P. Effects of rest interval duration in resistance training on measures of muscular strength: a systematic review. *Sports Med*. 2018;48(1):137-151.
19. Schoenfeld BJ et al. Longer interset rest periods enhance muscle strength and hypertrophy in resistance-trained men. *J Strength Cond Res*. 2016;30(7):1805-1812.
20. Singer A, Wolf M, Generoso L et al. Give it a rest: a systematic review with Bayesian meta-analysis on the effect of inter-set rest interval duration on muscle hypertrophy. *Front Sports Act Living*. 2024;6:1429789.
21. Nunes JP et al. What influence does resistance exercise order have on muscular strength gains and muscle hypertrophy? A systematic review and meta-analysis. *Eur J Sport Sci*. 2021;21(2):149-157.
22. Moesgaard L, Beck MM, Christiansen L, Aagaard P, Lundbye-Jensen J. Effects of periodization on strength and muscle hypertrophy in volume-equated resistance training programs: a systematic review and meta-analysis. *Sports Med*. 2022.
23. Williams TD, Tolusso DV, Fedewa MV, Esco MR. Comparison of periodized and non-periodized resistance training on maximal strength: a meta-analysis. *Sports Med*. 2017.
24. Pallarés JG et al. Effects of range of motion on resistance training adaptations: a systematic review and meta-analysis. *Scand J Med Sci Sports*. 2021.
25. Wolf M et al. Partial vs full range of motion resistance training: a systematic review and meta-analysis. *Int J Strength Cond*. 2023.
26. Schoenfeld BJ, Aragon AA. How much protein can the body use in a single meal for muscle-building? Implications for daily protein distribution. *J Int Soc Sports Nutr*. 2018;15:10.
27. Schumann M et al. Compatibility of concurrent aerobic and strength training for skeletal muscle size and function: an updated systematic review and meta-analysis. *Sports Med*. 2022;52:601-612.

Added with T-157 (checked 2026-10-29 against the PubMed record 21798015 and the open full text, PMC3197470):

28. Tudor-Locke C, Craig CL, Brown WJ, Clemes SA, De Cocker K, Giles-Corti B et al. How many steps/day are enough? For adults. *Int J Behav Nutr Phys Act*. 2011;8:79. doi:10.1186/1479-5868-8-79. Used for the steps-per-day bands of the activity level (below 5 000 sedentary, 5 000-7 499 low active, 7 500-9 999 somewhat active, 10 000-12 499 active, 12 500 or more highly active, the "Adult Graduated Step Index"). Only the bands come from the paper; mapping them onto the five activity levels of `04` §1.3 is a design decision (§9).

## 11. Cost estimate (ADR-77)

Prices re-checked on the Anthropic price list on 2026-10-29, unchanged: Opus 5.5 $4 / $20, Sonnet 5.5 $2 / $10 per million input / output tokens; cache reads 0.05× input ($0.20 / $0.10), 5-minute cache writes 1.25× input. US-only inference (`inference_geo`) would multiply every price by 1.1; it is not used. The function's price table (US-76) holds these figures.

**Data policy (checked 2026-10-29):** the Anthropic API deletes inputs and outputs within 30 days (longer only for Usage Policy enforcement or legal reasons) and does not use them for training by default.

**Edge Function limit (checked 2026-10-29):** wall clock 150 s per invocation on the Supabase Free plan (400 s on paid), 2 s CPU (waiting for the API does not count). The block plan call must finish within 150 s, or be streamed or split.

| Use | Model | Assumption per call | Per call | Per user and month |
|---|---|---|---|---|
| Block plan | Opus 5.5 | 30k in, 15k out (with thinking); 2 per month | ≈ $0.42 | ≈ $0.85 |
| Weekly report | Sonnet 5.5 | 20k in, 5k out; up to 8 per month | ≈ $0.09 | ≈ $0.70 |
| Chat | Sonnet 5.5 | 15k in (12k cached), 1.5k out; 60 messages | ≈ $0.024 | ≈ $1.45 |
| Derived values, progression, deload, trend | none (deterministic) | | $0 | $0 |

Heavy user ≈ $3 per month, light user ≈ $1. The 2 € cap stops a heavy user's chat around message 40; the plan and the report are served first.
