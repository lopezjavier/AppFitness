# Coach design: evidence base and default rules

Design base of the performance assistant (E13) and of the data it needs (E10-E12). Decisions: ADR-76 (coach), ADR-77 (AI provider and cost), ADR-78 (profile and catalogue).

**Principle:** every rule has a source, a common practice or a "design decision" label. No figure exists for convenience. Sources marked *verify* are cited from memory and must be checked before E13 is broken into tasks (see §10).

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
| Activity level | Starts from `profiles.activity_level` (ADR-78); the coach proposes a change from Health Connect steps and active calories (E11) and the logged training volume | Low: shown as provisional |
| Total expenditure | Initially BMR × activity factor (`04` §1.3); then adaptive expenditure from logged intake and weight change | Improves with months of data |
| Weight trend | Slope of weekly averages, with its confidence interval (§5) | Depends on the number of weigh-ins |
| Experience | Training history | Without history: beginner, lowest volume of §3 |

**Confirmation rule:** each block shows a card with the derived values, their source and the missing data. The user accepts or edits them. A value is not used until confirmed; until then it is shown as provisional. A confirmed value is asked again only when its new estimate falls outside the uncertainty of the confirmed one.

**Intake completeness:** adaptive expenditure uses only days with a logged diary; days without entries are excluded, not counted as zero. Below 70 % logged days in the window (design decision) the estimate is shown as unreliable and the coach does not propose calorie changes from it.

## 3. Training

- **Frequency:** each muscle group at least twice a week when the available days allow it (two or more training days); with one day, one full-body session. When weekly volume is equated, frequency itself shows no clear hypertrophy difference [Schoenfeld, Grgic, Krieger 2019]; twice a week is the practical way to spread the volume [Schoenfeld, Ogborn, Krieger 2016]. *Common practice built on that evidence.*
- **Volume:** starting point of 10 weekly sets per muscle group. Dose-response is positive [Schoenfeld, Ogborn, Krieger 2017], with diminishing returns at higher volumes [Pelland et al. 2024, *verify*].
- **Intensity:**
  - Strength: heavy loads, 1-6 reps.
  - Hypertrophy: 6-15 reps. Low and high loads give similar hypertrophy when sets are taken close to failure [Schoenfeld, Grgic et al. 2017].
- **Proximity to failure:** 1-3 reps in reserve on main work [Refalo et al. 2023, *verify*]. Reps in reserve are derived from the logged RPE (RIR = 10 − RPE); there is no separate RIR field (ADR-76).
- **Progression:** double progression. Work inside the rep range; when every working set reaches the top of the range at RPE 8 or less, raise the load to the next available increment. *Common practice; little comparative evidence.*
- **Failed sessions:** when most working sets miss the bottom of the range in two consecutive sessions of the exercise, lower the load 5 %, rounded to the available increment. *Design decision.*
- **Warm-up:** ramp-up sets on main lifts. *Common practice.*
- **Exercise selection:** the catalogue is filtered by place and equipment; substitutions keep the muscle group and the movement pattern (`exercises.movement_pattern`, ADR-78).
- **Deload:** suggested when no main lift improved its e1RM over the last three weeks of sessions; the deload week keeps the weights and cuts working sets by 40 %. *Design decision* (thresholds from common practice). Soreness is not a signal (§7).

## 4. Energy and macros

- **Calorie floor:** a plan never goes below 1200 kcal (women) / 1500 kcal (men) (ADR-25).
- **Protein:** at least 1.6 g/kg, plateau around 2.2 g/kg [Morton et al. 2018]. In a deficit with high lean mass, the upper end: 2.3-3.1 g/kg of fat-free mass [Helms, Zinn, Rowlands, Brown 2014].
- **Fat:** at least 20 % of calories; usual range 20-35 % [Thomas, Erdman, Burke 2016, ACSM/AND/DC position, *verify*].
- **Rate of loss:** 0.5-1 % of body weight per week [Helms, Aragon, Fitschen 2014]; default 0.5 %. (ACSM 2009 states 0.5-1 kg per week for the general population; the percentage suits lean trainees better.)
- **Rate of gain:** 0.25-0.5 % of body weight per week [Iraki et al. 2019, *verify*].
- **Energy constant:** 7 700 kcal per kg is an approximation, used only without history; with history the adaptive estimate is used [Hall 2008; Hall et al. 2011].
- **Adjustment inside a block:** proposed only when the plan's target rate is outside the confidence interval of the weight trend for two consecutive weeks; the proposal changes calories by at most 10 % (*design decision*) and needs acceptance.

## 5. Weight and measurements

- **Daily weigh-in** recommended: day-to-day changes of 1-2 kg from water, glycogen and gut content make a single weigh-in uninformative.
- **Trend:** slope over weekly averages, shown with its confidence interval computed from the real spread of the user's weigh-ins. There is no minimum of data to use the app; data only changes the confidence shown.
- **Reminders (E10):** weight after 2 days in a row without a weigh-in; circumferences after 14 days without measuring (*design decision*: circumferences change slowly).
- **Body composition:** impedance body fat is unreliable; circumferences are a secondary reference and their uncertainty is shown.

## 6. Outcomes set by the coach

- Weight, body fat, circumferences or e1RM per lift, depending on the intention and the plan.
- Each outcome carries its reason and its deadline (normally the end of the block).
- The user cannot set an outcome's value; they can turn an outcome off. `profiles.target_weight_kg` stops being a user input (ADR-78).

## 7. Discomfort and safety

- Logging discomfort on an exercise (E12) blocks load increases for that exercise for 7 days, or until the user confirms "Ya no me molesta".
- Strong-pain wording from a fixed term list (in the discomfort log or an exercise note) shows a fixed message: consult a health professional; no suggestion is made for that exercise. No diagnosis and no medical advice.
- Free text is matched only against the fixed list; it is never interpreted by the model to diagnose.
- Muscle soreness is not collected: it is a poor indicator of recovery or of adaptation [Schoenfeld, Contreras 2013; Nosaka et al. 2002, *verify*].

## 8. Data, privacy and cost

- Personal use: no prior consent screen, by user decision. Before distributing the app to other people, each must accept the use of their data and the coach provider's terms; this condition is written, not implemented.
- The `coach` Edge Function sends a compact summary (last 8 weeks at most), never raw tables or photos.
- Cost control by money, not by calls (ADR-77): 2 € per user and 5 € in total per calendar month, plus a spending limit on the Anthropic account.
- Deleting the account deletes the coach's plans, recommendations, outcomes, usage and conversations (ADR-29); "Borrar historial del asistente" deletes them without deleting the account.

## 9. Design decisions without evidence backing

Defaults chosen for this app; adjusted with real data, not scientific figures:
- Block length: 4 weeks (planning convention).
- Load increments: those available in the user's equipment (plates or dumbbells, E6).
- Place defines the default equipment; the user can change it.
- 5 % load drop after two failed sessions; 40 % fewer sets in a deload; three weeks without e1RM improvement as the deload trigger; at most 10 % calorie change per adjustment; 70 % logged days for a reliable intake; 14 days between circumference reminders; sleep below 6 h mentioned in the report (below the 7 h adult recommendation).

## 10. Sources

Corrected against the earlier draft (frequency, rate of loss, Hall year). Entries marked *verify* above are still cited from memory; every source is checked before the E13 breakdown.

1. Schoenfeld BJ, Ogborn D, Krieger JW. Effects of resistance training frequency on measures of muscle hypertrophy: a systematic review and meta-analysis. *Sports Med*. 2016.
2. Schoenfeld BJ, Grgic J, Krieger J. How many times per week should a muscle be trained to maximize muscle hypertrophy? A systematic review and meta-analysis. *J Sports Sci*. 2019.
3. Schoenfeld BJ, Ogborn D, Krieger JW. Dose-response relationship between weekly resistance training volume and increases in muscle mass: a systematic review and meta-analysis. *J Sports Sci*. 2017.
4. Pelland JC et al. The resistance training dose-response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gain. 2024.
5. Schoenfeld BJ, Grgic J, Ogborn D, Krieger JW. Strength and hypertrophy adaptations between low- vs. high-load resistance training: a systematic review and meta-analysis. *J Strength Cond Res*. 2017.
6. Refalo MC et al. Influence of resistance training proximity-to-failure on skeletal muscle hypertrophy: a systematic review with meta-analysis. *Sports Med*. 2023.
7. Morton RW et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. *Br J Sports Med*. 2018.
8. Helms ER, Zinn C, Rowlands DS, Brown SR. A systematic review of dietary protein during caloric restriction in resistance trained lean athletes. *Int J Sport Nutr Exerc Metab*. 2014.
9. Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. *J Int Soc Sports Nutr*. 2014.
10. Iraki J, Fitschen P, Espinar S, Helms E. Nutrition recommendations for bodybuilders in the off-season: a narrative review. *Sports*. 2019.
11. Thomas DT, Erdman KA, Burke LM. Position of the Academy of Nutrition and Dietetics, Dietitians of Canada, and the American College of Sports Medicine: nutrition and athletic performance. *J Acad Nutr Diet*. 2016.
12. Donnelly JE et al. (ACSM). Appropriate physical activity intervention strategies for weight loss and prevention of weight regain for adults. *Med Sci Sports Exerc*. 2009.
13. Hall KD. What is the required energy deficit per unit weight loss? *Int J Obes*. 2008.
14. Hall KD et al. Quantification of the effect of energy imbalance on bodyweight. *Lancet*. 2011.
15. Mifflin MD et al. A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr*. 1990.
16. Schoenfeld BJ, Contreras B. Is postexercise muscle soreness a valid indicator of muscular adaptations? *Strength Cond J*. 2013.
17. Nosaka K, Newton M, Sacco P. Delayed-onset muscle soreness does not reflect the magnitude of eccentric exercise-induced muscle damage. *Scand J Med Sci Sports*. 2002.

## 11. Cost estimate (ADR-77)

Prices from the Anthropic price list cached on 2026-10-06 (Opus 5.5 $4 / $20, Sonnet 5.5 $2 / $10 per million input / output tokens); re-check before E13.

| Use | Model | Assumption per call | Per call | Per user and month |
|---|---|---|---|---|
| Block plan | Opus 5.5 | 30k in, 15k out (with thinking); 2 per month | ≈ $0.42 | ≈ $0.85 |
| Weekly report | Sonnet 5.5 | 20k in, 5k out; up to 8 per month | ≈ $0.09 | ≈ $0.70 |
| Chat | Sonnet 5.5 | 15k in (12k cached), 1.5k out; 60 messages | ≈ $0.024 | ≈ $1.45 |
| Derived values, progression, deload, trend | none (deterministic) | | $0 | $0 |

Heavy user ≈ $3 per month, light user ≈ $1. The 2 € cap stops a heavy user's chat around message 40; the plan and the report are served first.
