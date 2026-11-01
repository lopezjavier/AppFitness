// Deterministic calories and macros of a nutrition plan (planning/04 section 8, planning/07 sections 4 and 9).
// Pure: no I/O. The model never produces a figure from this file.
import type { Experience, Intention, Nutrition, Sex } from "./schemas.ts";

export const KCAL_PER_KG = 7700;
export const DAYS = 7;
/** Calorie floor by sex (ADR-25). */
export const FLOOR: Record<Sex, number> = { female: 1200, male: 1500 };
/** Range of nutrition_goals.calorie_adjustment. */
export const DEFICIT_CAP = 1000;
export const SURPLUS_CAP = 750;
export const PROTEIN_MIN = 1.6;
export const PROTEIN_MAX = 2.2;
export const FAT_MIN_PCT = 20;
export const FAT_MAX_PCT = 35;
export const FAT_DEFAULT_PCT = 25;
export const MEAL_PROTEIN_G_PER_KG = 0.4;
export const MIN_MEALS = 4;

export const LOSS_RATE_DEFAULT = 0.5;
export const LOSS_RATE_RANGE = [0.5, 1.0] as const;
export const GAIN_RATE: Record<Experience, number> = {
    beginner: 0.5,
    intermediate: 0.35,
    advanced: 0.25,
};
export const GAIN_RATE_RANGE = [0.25, 0.5] as const;
/** The rounding of the daily delta (up to 10 kcal) can push the effective rate this far above a range maximum. */
export const RATE_TOLERANCE_PCT = 0.05;

/** Rounding of the daily delta up to 10 kcal can push a target this far past a range edge. */
export const ROUNDING_KCAL = 10;

/** kcal per day for a weekly rate in percent of body weight: rate% * weight / 100 * 7700 / 7 = rate * weight * 11. */
export const rateKcal = (ratePct: number, weightKg: number) =>
    ratePct * weightKg * (KCAL_PER_KG / DAYS / 100);

/**
 * Allowed `target - intake anchor` in kcal for an intention (planning/04 section 8.7). `shift` is the slope term
 * (expenditure - anchor, 0 without a slope). Single source for computeNutrition and assertNutritionInvariants.
 */
export function intakeRange(
    intention: Intention,
    weightKg: number,
    shiftKcal: number,
): { min: number; max: number } {
    const d = (r: number) => rateKcal(r, weightKg);
    switch (intention) {
        case "muscle":
            return {
                min: shiftKcal + d(GAIN_RATE_RANGE[0]),
                max: shiftKcal + d(GAIN_RATE_RANGE[1]) + ROUNDING_KCAL,
            };
        case "fat_loss":
            return {
                min: shiftKcal - d(LOSS_RATE_RANGE[1]) - ROUNDING_KCAL,
                max: shiftKcal - d(LOSS_RATE_RANGE[0]),
            };
        default:
            return {
                min: shiftKcal - d(GAIN_RATE_RANGE[0]),
                max: shiftKcal + d(GAIN_RATE_RANGE[0]),
            };
    }
}

/**
 * The rule broken by a target, or null. A limiter (floor, goal_range) may make a plan slower than the range but never
 * faster, so the slow side is skipped when `limitedBy` is set.
 */
export function intakeRangeViolation(
    intention: Intention,
    weightKg: number,
    shiftKcal: number,
    deltaOverAnchor: number,
    limitedBy: Nutrition["limited_by"],
): "target_above_intake_range" | "target_below_intake_range" | null {
    const { min, max } = intakeRange(intention, weightKg, shiftKcal);
    const eps = 1e-9;
    const limited = limitedBy !== null;
    if (deltaOverAnchor > max + eps) {
        // Above the range is the slow side of a loss; for the no-change intentions only the floor can lift a target.
        const allowed = intention === "fat_loss"
            ? limited
            : intention !== "muscle" && limitedBy === "floor";
        if (!allowed) return "target_above_intake_range";
    }
    if (deltaOverAnchor < min - eps) {
        // Below the range is the slow side of a gain.
        const allowed = intention === "muscle" && limited;
        if (!allowed) return "target_below_intake_range";
    }
    return null;
}

export type CoachPlanErrorCode =
    | "coach_missing_data"
    | "coach_plan_infeasible";

export class CoachPlanError extends Error {
    constructor(readonly code: CoachPlanErrorCode) {
        super(code);
        this.name = "CoachPlanError";
    }
}

export type NutritionInput = {
    intention: Intention;
    sex: Sex | null;
    weightKg: number | null;
    expenditureKcal: number | null;
    expenditureSource: string;
    /** Capped 28-day intake (04 section 7.3); null on the formula path. Enables the target check and the clamp. */
    intakeAnchorKcal?: number | null;
    /** Whether the expenditure already includes the weight-slope term. */
    slopeUsed?: boolean;
    experience: Experience;
    /** Overrides the loss rate (percent of body weight per week); ignored for the other intentions. */
    targetRatePct?: number;
    requiresConfirmation?: boolean;
    unconfirmed?: string[];
};

/** Ceil that ignores floating point noise (0.5 * 80 * 11 / 10 must be 44, not 44.0000001). */
const ceil = (x: number) => Math.ceil(x - 1e-9);
const round = (x: number, decimals = 0) => {
    const f = 10 ** decimals;
    return Math.round((x + Number.EPSILON) * f) / f + 0; // + 0 turns -0 into 0
};

function targetRate(input: NutritionInput): number {
    switch (input.intention) {
        case "fat_loss":
            return input.targetRatePct ?? LOSS_RATE_DEFAULT;
        case "muscle":
            return GAIN_RATE[input.experience];
        default:
            return 0;
    }
}

function proteinPerKg(
    strategy: string,
    intention: Intention,
    experience: Experience,
): number {
    if (strategy === "deficit") {
        return experience === "beginner" ? 2.0 : 2.2;
    }
    return intention === "recomposition" ? 2.0 : 1.8;
}

export function computeNutrition(input: NutritionInput): Nutrition {
    const { sex, weightKg: weight, expenditureKcal: rawExpenditure } = input;
    if (sex === null || weight === null || rawExpenditure === null) {
        throw new CoachPlanError("coach_missing_data");
    }
    if (!(weight > 0) || !(rawExpenditure > 0)) {
        throw new CoachPlanError("coach_missing_data");
    }
    // With an intake anchor the expenditure is the anchor plus the slope term, which cannot exceed one top-of-range
    // rate (04 section 7.2). Without a slope the expenditure is the anchor itself.
    const anchor = input.intakeAnchorKcal ?? null;
    const slopeUsed = anchor !== null && (input.slopeUsed ?? false);
    const maxShift = rateKcal(GAIN_RATE_RANGE[1], weight);
    const shift = anchor === null || !slopeUsed ? 0 : Math.round(
        Math.max(-maxShift, Math.min(maxShift, rawExpenditure - anchor)),
    );
    const expenditure = anchor === null ? rawExpenditure : anchor + shift;
    const floor = FLOOR[sex];
    const rate = targetRate(input);
    const loss = input.intention === "fat_loss";

    // kcal per day for the rate: rate% * weight / 100 * 7700 / 7 = rate * weight * 11 (rounded up to 10).
    let delta = rate === 0 ? 0 : ceil(rate * weight * 11 / 10) * 10;
    let limitedBy: Nutrition["limited_by"] = null;
    const cap = loss ? DEFICIT_CAP : SURPLUS_CAP;
    if (delta > cap) {
        delta = cap;
        limitedBy = "goal_range";
    }
    let target = loss ? expenditure - delta : expenditure + delta;
    if (target < floor) {
        target = floor;
        limitedBy = "floor";
    }

    // A loss needs a deficit: with an expenditure at or below the floor there is none to give.
    if (loss && target >= expenditure) {
        throw new CoachPlanError("coach_plan_infeasible");
    }

    if (
        anchor !== null &&
        intakeRangeViolation(
                input.intention,
                weight,
                shift,
                target - anchor,
                limitedBy,
            ) !== null
    ) {
        throw new CoachPlanError("coach_plan_infeasible");
    }

    const adjustment = target - expenditure;
    const strategy = adjustment < 0
        ? "deficit"
        : adjustment > 0
        ? "surplus"
        : "maintenance";
    const rateKgWeek = adjustment * DAYS / KCAL_PER_KG;
    const ratePctWeek = (loss ? -1 : 1) * rateKgWeek / weight * 100;

    // Macros (04 section 1.5): protein first, fat 25 %, carbohydrates take the rest.
    let gPerKg = proteinPerKg(strategy, input.intention, input.experience);
    let fatPct: number = FAT_DEFAULT_PCT;
    const proteinPctFor = (g: number) =>
        Math.max(5, ceil(g * weight * 4 / target * 100));
    let proteinPct = proteinPctFor(gPerKg);
    let carbsPct = 100 - proteinPct - fatPct;
    if (carbsPct < 5) {
        fatPct = FAT_MIN_PCT;
        carbsPct = 100 - proteinPct - fatPct;
    }
    if (carbsPct < 5) {
        gPerKg = PROTEIN_MIN;
        proteinPct = proteinPctFor(gPerKg);
        carbsPct = 100 - proteinPct - fatPct;
    }
    if (carbsPct < 5 || proteinPct > 90) {
        throw new CoachPlanError("coach_plan_infeasible");
    }

    return {
        intention: input.intention,
        weight_kg: weight,
        expenditure_kcal: expenditure,
        expenditure_source: input.expenditureSource,
        intake_anchor_kcal: anchor,
        slope_used: slopeUsed,
        slope_term_kcal: shift,
        rate_pct_week: round(ratePctWeek, 3),
        rate_kg_week: round(rateKgWeek, 3),
        target_rate_pct: rate,
        limited_by: limitedBy,
        floor_kcal: floor,
        target_kcal: target,
        calorie_adjustment: adjustment,
        strategy,
        protein_pct: proteinPct,
        carbs_pct: carbsPct,
        fat_pct: fatPct,
        protein_g: round(target * proteinPct / 100 / 4),
        carbs_g: round(target * carbsPct / 100 / 4),
        fat_g: round(target * fatPct / 100 / 9),
        protein_g_per_kg: gPerKg,
        distribution: {
            per_meal_g: round(MEAL_PROTEIN_G_PER_KG * weight),
            g_per_kg: MEAL_PROTEIN_G_PER_KG,
            min_meals: MIN_MEALS,
        },
        requires_confirmation: input.requiresConfirmation ?? false,
        unconfirmed: input.unconfirmed ?? [],
        edited_by_user: false,
        calorie_changes: 0,
    };
}
