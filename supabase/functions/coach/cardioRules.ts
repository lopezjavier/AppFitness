// Weekly cardio and daily steps targets of the plan (planning/07 section 3, ADR-85). Pure: no I/O, no age input.
// The calories never depend on these figures (Q25).
import type { Intention, Phase } from "./schemas.ts";

/** WHO adult minimum, the same for every intention. */
export const CARDIO_MIN_WEEK = 150;
/** Upper end of the WHO band, shown only for fat loss. */
export const CARDIO_BAND_MAX = 300;
export const STEPS_FLOOR = 7000;
export const STEPS_STEP = 500;
/** Technical bounds of the user edit (mirrored by set_coach_plan_cardio). */
export const CARDIO_EDIT_MIN = 0;
export const CARDIO_EDIT_MAX = 600;
export const CARDIO_EDIT_STEP = 5;

export type Activity = {
    cardio_min_week: number;
    cardio_edited: boolean;
    cardio_band: [number, number] | null;
    steps_day: number;
    steps_basis: "average" | "floor";
    steps_avg: number | null;
};

/** `stepsAvg28` is the 28-day average, null when there are fewer than 7 days of data. */
export function computeActivity(
    input: { intention: Intention; stepsAvg28: number | null; phase?: Phase },
): Activity {
    const avg = input.stepsAvg28;
    const rounded = avg === null || !Number.isFinite(avg) || avg < 0
        ? null
        : Math.floor(avg / STEPS_STEP) * STEPS_STEP;
    const fromAverage = rounded !== null && rounded >= STEPS_FLOOR;
    return {
        cardio_min_week: CARDIO_MIN_WEEK,
        cardio_edited: false,
        cardio_band: input.intention === "fat_loss"
            ? [CARDIO_MIN_WEEK, CARDIO_BAND_MAX]
            : null,
        steps_day: fromAverage ? rounded : STEPS_FLOOR,
        steps_basis: fromAverage ? "average" : "floor",
        steps_avg: avg === null || !Number.isFinite(avg) ? null : avg,
    };
}
