// Server-side arithmetic of the training block (planning/04 section 9): frequency, cardio split and small helpers.
// The routine itself is built by the model and judged by routineChecker.ts; nothing here chooses exercises.
// Pure: no I/O.
import { CARDIO_MIN_WEEK } from "./cardioRules.ts";
import type { BlockPrefs, Phase } from "./schemas.ts";

export const BLOCK_WEEKS = 4;
export const DEFAULT_SESSION_MINUTES = 60;
export const MAX_DAYS = 7;

/** Cardio in the block (04 section 9.11): whole 5-minute units, at most 60 min per routine, always "suave". */
export const CARDIO_UNIT_MIN = 5;
export const CARDIO_ROUTINE_CAP_MIN = 60;
export const CARDIO_PACE = "suave" as const;
/** Technical bounds of the weekly target (same as the plan edit). */
export const CARDIO_TARGET_MAX = 600;

/**
 * Splits the weekly cardio target over the routines: units of 5 min, remainder to the first routines, at most `cap`
 * min per routine; the minutes that do not fit are "por tu cuenta". A routine with 0 has no cardio exercise.
 */
export function splitCardio(
    targetMin: number,
    routines: number,
    cap: number = CARDIO_ROUTINE_CAP_MIN,
): {
    per_routine: number[];
    planned: number;
    on_your_own: number;
} {
    const units = Math.floor(Math.max(0, targetMin) / CARDIO_UNIT_MIN);
    const planned = Math.min(
        units,
        (cap / CARDIO_UNIT_MIN) * routines,
    );
    const base = Math.floor(planned / routines);
    const rest = planned % routines;
    return {
        per_routine: Array.from(
            { length: routines },
            (_, i) => CARDIO_UNIT_MIN * (base + (i < rest ? 1 : 0)),
        ),
        planned: planned * CARDIO_UNIT_MIN,
        on_your_own: targetMin - planned * CARDIO_UNIT_MIN,
    };
}

/** Multi-joint patterns (E13 Q3): the "main" lifts. */
export const MAIN_PATTERNS: readonly string[] = [
    "squat",
    "hinge",
    "lunge",
    "horizontal_push",
    "vertical_push",
    "horizontal_pull",
    "vertical_pull",
];

export const isMainLift = (pattern: string | null | undefined): boolean =>
    pattern != null && MAIN_PATTERNS.includes(pattern);

export type Frequency = {
    days_per_week: number;
    days_chosen: boolean;
    session_minutes: number;
};

/** Days set by the user are used as they are; otherwise the coach chooses (and states it): 04 section 9.1. */
export function chooseFrequency(
    prefs: Pick<
        BlockPrefs,
        "days_per_week" | "session_minutes" | "experience"
    >,
): Frequency {
    const minutes = prefs.session_minutes ?? DEFAULT_SESSION_MINUTES;
    if (prefs.days_per_week != null) {
        return {
            days_per_week: Math.min(MAX_DAYS, Math.max(1, prefs.days_per_week)),
            days_chosen: false,
            session_minutes: minutes,
        };
    }
    const days = minutes <= 45 ? 3 : prefs.experience === "beginner" ? 3 : 4;
    return {
        days_per_week: Math.min(5, days),
        days_chosen: true,
        session_minutes: minutes,
    };
}

/**
 * Weekly cardio target of a block (07 section 3 "Cardio by phase"): the recommendation (150) in every phase; the
 * user's own edit of the plan's cardio always wins. Deload and diet-break weeks do not touch it.
 */
export function cardioTargetFor(
    _phase: Phase,
    edited: boolean,
    planned: number | null | undefined,
): number {
    if (edited && typeof planned === "number") return planned;
    return CARDIO_MIN_WEEK;
}

/** Working sets per primary muscle group over a week (whole numbers: the folder summary, never the model's). */
export function weeklySetsByMuscle(
    items: { muscle_group: string; sets: number }[],
): Record<string, number> {
    const out: Record<string, number> = {};
    for (const it of items) {
        out[it.muscle_group] = (out[it.muscle_group] ?? 0) + it.sets;
    }
    return out;
}

/** In how many sessions of the week each group appears. */
export function sessionsPerMuscle(
    sessions: { muscle_group: string }[][],
): Record<string, number> {
    const out: Record<string, number> = {};
    for (const s of sessions) {
        for (const g of new Set(s.map((x) => x.muscle_group))) {
            out[g] = (out[g] ?? 0) + 1;
        }
    }
    return out;
}
