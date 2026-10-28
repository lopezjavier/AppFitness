# Phase 2 scope

Approved by the user on 2026-10-28. Nothing outside this document is in scope without asking (see `../CLAUDE.md`). Phase 1 scope (frozen): [`phase1/01-phase1-scope.md`](phase1/01-phase1-scope.md).

## 1. Goal
An evidence-based performance assistant (coach) that plans four-week training and nutrition blocks from the user's intention and preferences and adjusts them with the user's data, plus the data it needs: reminders, watch data, training preferences, notes and discomfort.

## 2. Epics

| Epic | File | Content |
|---|---|---|
| E10 | [E10-reminders-and-check-in.md](epics/E10-reminders-and-check-in.md) | In-app notification center, weight and circumference reminders, weekly check-in |
| E11 | [E11-health-connect-daily-sync.md](epics/E11-health-connect-daily-sync.md) | Daily automatic Health Connect sync; sleep, steps, calories and resting heart rate |
| E12 | [E12-training-data-and-preferences.md](epics/E12-training-data-and-preferences.md) | Rest alert with the screen off, intention and preferences, notes, discomfort log, movement pattern |
| E13 | [E13-performance-assistant.md](epics/E13-performance-assistant.md) | Coach: derived values, plan, progression, report, block creator, adherence, history, deload, AI budget, chat |

**Order:** E10 → E11 → E12 → E13. E13 depends on the other three. Design base of the coach: [`07-coach-design.md`](07-coach-design.md). Stories US-57..US-77.

## 3. Requirement changes

| ID | Change |
|---|---|
| NFR-02 | "100 % free" becomes: free except the coach's AI calls (Anthropic API), accepted up to 5 € per month in total, capped by the app (2 € per user and month) and by the Anthropic account spending limit (ADR-77). Supabase, GitHub, Open Food Facts, Health Connect and the Gemini free tier stay free |

## 4. Decisions (user, 2026-10-28)

| Topic | Decision | ADR |
|---|---|---|
| Reminders | In the app only; computed on open; listed in "Centro de avisos"; no time window and no system notifications | 73 |
| Weight reminder | After 2 days in a row without a weigh-in | 73 |
| Circumference reminder | After 14 days without measuring (design decision) | 73 |
| Muscle soreness | Not collected (poor recovery signal) | 73, 76 |
| Weekly check-in | Returns (energy and notes, with the weekly summary); the streak stays deferred | 73 |
| Health Connect | Automatic sync on the first open of the day for every type; the "Sincronizar" button stays; no background sync | 74 |
| Health Connect data | Sleep, steps, active and total calories, resting heart rate; writes stay weight and body fat | 74 |
| Rest alert | Works with the screen off; new alert sound chosen among the E9 point 16 candidates | 75 |
| Intention | Ganar fuerza, Ganar musculatura, Perder grasa, Recomposición, Mantenimiento | 78 |
| Static preferences | Place (home, gym, other), equipment, optional days and session length | 78 |
| RIR | No field; derived from RPE (10 − RPE) | 76 |
| Activity level | `profiles.activity_level` stays as the starting factor; the coach proposes changes | 78 |
| Target weight | No longer a user input; the coach sets outcomes (E13) | 76, 78 |
| Derived values | Shown with their source and confirmed before use | 76 |
| Plan | 4-week blocks, reviewed at the end of each block | 76 |
| AI provider | Claude: Opus 5.5 for the block plan, Sonnet 5.5 for report and chat; Gemini stays only for food photos | 77 |
| AI cost | Monthly budget: 2 € per user, 5 € in total; estimate ≈ $1-3 per user and month (`07-coach-design.md` §11) | 77 |
| Prior consent | Not required while the app is personal; written condition before distributing it | 76 |
| Coach navigation | New tab "Trainer" between Entreno and Perfil | 79 |
| Water and voice | Phase 3 | — |

## 5. Out of Phase 2 scope
- Background sync and system notifications for reminders (only the rest-end alert posts a notification).
- Writing food or training to Health Connect.
- Water tracking and voice control (Phase 3).
- Training streak and rewards (still deferred, `09-open-questions.md`); Monthly Report and Year in Review (ADR-66).
- Meal plans, medical advice, video analysis.
- Web client and whole-meal photo estimation (Phase 1 "deferred to Phase 2" items not taken up).

## 6. Amendments to frozen Phase 1 planning
The Phase 1 epic files are frozen; these amendments are recorded here and in the ADRs:

| Where | Change | ADR |
|---|---|---|
| E3 US-13 (rest timer) | The end-of-rest alert also works in the background and with the screen off (E12 US-64) | 75 |
| E5 scope line | Steps, sleep, calories and heart rate enter through E11; background sync stays out | 74 |
| E5 US-26 (manual sync) | The button stays; a daily automatic sync is added (E11 US-60) | 74 |
| E5 target weight | Target weight and distance to the goal become the coach's block outcome (E13 US-69) | 78 |
| E5 chart | Unchanged (MA7 line); the coach trend uses the slope of weekly averages | 76 |
| E8 US-44, ADR-66 | The weekly check-in returns (E10 US-59); the streak stays deferred | 73 |
| E9 points 15-16 (T-059) | The alert sound choice is reopened (E12 US-64) | 75 |

## 7. Pending before implementing
- E11: verify `READ_HEALTH_DATA_HISTORY` on the device (the first read covers 90 days).
- E12: verify screen-off alert behaviour on Android 17.
- E13: verify the sources marked *verify* in `07-coach-design.md` §10, the Anthropic API data policy and current prices; 👤 Anthropic account, spending limit and `ANTHROPIC_API_KEY` secret.
- Release version of each epic (`v1.x` for now).
