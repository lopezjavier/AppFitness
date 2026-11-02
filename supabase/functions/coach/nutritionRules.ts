// Deterministic calories and macros of a nutrition plan (planning/04 section 8, planning/07 sections 4 and 9).
// Pure: no I/O. The model never produces a figure from this file.
import type {
    DietBreakWeek,
    Experience,
    Intention,
    Nutrition,
    Phase,
    PhaseReason,
    Sex,
} from "./schemas.ts";

export const KCAL_PER_KG = 7700;
export const DAYS = 7;
/** Calorie floor by sex (ADR-25). */
export const FLOOR: Record<Sex, number> = { female: 1200, male: 1500 };
/** Largest daily deficit below the expenditure in use (07 section 4, Murphy and Koehler 2022). */
export const DEFICIT_CAP = 500;
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
/** Regain gains at the bottom of the sourced gain range for a muscle or strength intention (07 section 4). */
export const REGAIN_GAIN_RATE = 0.25;
/** The rounding of the daily delta (up to 10 kcal) can push the effective rate this far above a range maximum. */
export const RATE_TOLERANCE_PCT = 0.05;

/** Rounding of the daily delta up to 10 kcal can push a target this far past a range edge. */
export const ROUNDING_KCAL = 10;

/** kcal per day for a weekly rate in percent of body weight: rate% * weight / 100 * 7700 / 7 = rate * weight * 11. */
export const rateKcal = (ratePct: number, weightKg: number) =>
    ratePct * weightKg * (KCAL_PER_KG / DAYS / 100);

/** The phase a first plan starts in, and why (07 section 12.3): from the intention only (T-198). */
export function firstPhase(
    intention: Intention,
): { phase: Phase; reason: PhaseReason } {
    switch (intention) {
        case "fat_loss":
            return { phase: "cut", reason: "intention" };
        case "muscle":
            return { phase: "gain", reason: "intention" };
        case "recomposition":
            return { phase: "recomposition", reason: "intention" };
        default:
            return { phase: "maintenance", reason: "intention" };
    }
}

/**
 * Which intake range a phase uses (planning/04 section 8.7): by the phase, never by the intention, so a regain plan of
 * a fat-loss user uses the no-change range. `regain` is the no-change range with the rounding of its optional +0.25 %.
 */
export type RangeClass = "loss" | "gain" | "nochange" | "regain";
export function rangeClass(phase: Phase): RangeClass {
    switch (phase) {
        case "cut":
            return "loss";
        case "gain":
            return "gain";
        case "regain":
            return "regain";
        default:
            return "nochange";
    }
}

/**
 * Allowed `target - intake anchor` in kcal for a range class (planning/04 section 8.7). `shift` is the slope term
 * (expenditure - anchor, 0 without a slope). Single source for computeNutrition and assertNutritionInvariants.
 */
export function intakeRange(
    kind: RangeClass,
    weightKg: number,
    shiftKcal: number,
): { min: number; max: number } {
    const d = (r: number) => rateKcal(r, weightKg);
    switch (kind) {
        case "gain":
            return {
                min: shiftKcal + d(GAIN_RATE_RANGE[0]),
                max: shiftKcal + d(GAIN_RATE_RANGE[1]) + ROUNDING_KCAL,
            };
        case "loss":
            return {
                min: shiftKcal - d(LOSS_RATE_RANGE[1]) - ROUNDING_KCAL,
                max: shiftKcal - d(LOSS_RATE_RANGE[0]),
            };
        case "regain":
            return {
                min: shiftKcal - d(GAIN_RATE_RANGE[0]),
                max: shiftKcal + d(GAIN_RATE_RANGE[0]) + ROUNDING_KCAL,
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
    kind: RangeClass,
    weightKg: number,
    shiftKcal: number,
    deltaOverAnchor: number,
    limitedBy: Nutrition["limited_by"],
): "target_above_intake_range" | "target_below_intake_range" | null {
    const { min, max } = intakeRange(kind, weightKg, shiftKcal);
    const eps = 1e-9;
    const limited = limitedBy !== null;
    if (deltaOverAnchor > max + eps) {
        // Above the range is the slow side of a loss; for the other phases only the floor can lift a target.
        const allowed = kind === "loss" ? limited : limitedBy === "floor";
        if (!allowed) return "target_above_intake_range";
    }
    if (deltaOverAnchor < min - eps) {
        // Below the range is the slow side of a gain.
        const allowed = kind === "gain" && limited;
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
    /** The phase of the plan; defaults to the first phase of the intention. */
    phase?: Phase;
    phaseReason?: PhaseReason;
    /** Overrides the cut rate (percent of body weight per week, 0.5-1.0); ignored outside a cut. */
    targetRatePct?: number;
    /** Adds the maintenance week of a cut (week 4). */
    dietBreak?: boolean;
    requiresConfirmation?: boolean;
    unconfirmed?: string[];
};

/** Ceil that ignores floating point noise (0.5 * 80 * 11 / 10 must be 44, not 44.0000001). */
const ceil = (x: number) => Math.ceil(x - 1e-9);
const round = (x: number, decimals = 0) => {
    const f = 10 ** decimals;
    return Math.round((x + Number.EPSILON) * f) / f + 0; // + 0 turns -0 into 0
};

function targetRate(input: NutritionInput, phase: Phase): number {
    switch (phase) {
        case "cut": {
            const rate = input.targetRatePct ?? LOSS_RATE_DEFAULT;
            // Defensive: the caller validates the range (INVALID_INPUT) before computing.
            if (
                !(rate >= LOSS_RATE_RANGE[0] - 1e-9) ||
                !(rate <= LOSS_RATE_RANGE[1] + 1e-9)
            ) {
                throw new CoachPlanError("coach_plan_infeasible");
            }
            return rate;
        }
        case "gain":
            return GAIN_RATE[input.experience];
        case "regain":
            return input.intention === "muscle" ||
                    input.intention === "strength"
                ? REGAIN_GAIN_RATE
                : 0;
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
    const phase = input.phase ?? firstPhase(input.intention).phase;
    const rate = targetRate(input, phase);
    const loss = phase === "cut";

    // kcal per day for the rate: rate% * weight / 100 * 7700 / 7 = rate * weight * 11 (rounded up to 10).
    let delta = rate === 0 ? 0 : ceil(rate * weight * 11 / 10) * 10;
    let limitedBy: Nutrition["limited_by"] = null;
    const cap = loss ? DEFICIT_CAP : SURPLUS_CAP;
    if (delta > cap) {
        delta = cap;
        limitedBy = loss ? "deficit_cap" : "goal_range";
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
                rangeClass(phase),
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

    const dietBreak = loss && input.dietBreak === true
        ? maintenanceWeek(input)
        : null;

    return {
        intention: input.intention,
        phase,
        phase_reason: input.phaseReason ?? "intention",
        phase_options: {
            cut_rate_pct: loss ? rate : LOSS_RATE_DEFAULT,
            diet_break: dietBreak !== null,
        },
        diet_break: dietBreak,
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

/** The maintenance week of a diet break: the same plan at rate 0, macros included (07 section 4). */
function maintenanceWeek(input: NutritionInput): DietBreakWeek {
    const m = computeNutrition({
        ...input,
        phase: "maintenance",
        dietBreak: false,
    });
    return {
        target_kcal: m.target_kcal,
        calorie_adjustment: m.calorie_adjustment,
        strategy: m.strategy,
        protein_pct: m.protein_pct,
        carbs_pct: m.carbs_pct,
        fat_pct: m.fat_pct,
        protein_g: m.protein_g,
        carbs_g: m.carbs_g,
        fat_g: m.fat_g,
        week: 4,
    };
}
