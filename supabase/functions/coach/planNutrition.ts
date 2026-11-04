// Kind `plan_nutrition`: deterministic calories, macros and outcomes, with no model call (ADR-98, planning/04 section 8).
// The handler answers it before the provider and budget checks; reasons come from the templates of outcomeTemplates.ts.
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
} from "./context.ts";
import { type Activity, computeActivity } from "./cardioRules.ts";
import {
    CoachPlanError,
    computeNutrition,
    firstPhase,
    LOSS_RATE_RANGE,
} from "./nutritionRules.ts";
import { buildCandidates, type CandidateSummary } from "./outcomes.ts";
import { buildOutcomes } from "./outcomeTemplates.ts";
import { assertInputsComplete } from "./inputs.ts";
import {
    CONSTANTS_VERSION,
    type Experience,
    type Intention,
    type Nutrition,
    type Phase,
    type PhaseReason,
    type Sex,
} from "./schemas.ts";
import {
    assertActivityInvariants,
    assertNutritionInvariants,
    unconfirmedKeys,
} from "./validators.ts";

const INTENTIONS: Intention[] = [
    "strength",
    "muscle",
    "fat_loss",
    "recomposition",
    "maintenance",
];
const EXPERIENCES: Experience[] = ["beginner", "intermediate", "advanced"];

/** Plausibility bounds of the coach inputs (07 section 4 "Input contract"): design decisions, inclusive. */
export const AGE_BOUNDS = [18, 100] as const;
export const HEIGHT_BOUNDS = [120, 230] as const;
export const WEIGHT_BOUNDS = [30, 300] as const;
/** WHO underweight cut-off (07 section 4 "Underweight stop"). */
export const UNDERWEIGHT_BMI = 18.5;

const within = (v: number, [lo, hi]: readonly [number, number]) =>
    v >= lo && v <= hi;
const isUnderweight = (weightKg: number, heightCm: number) =>
    weightKg / (heightCm / 100) ** 2 < UNDERWEIGHT_BMI;

const isRecord = (v: unknown): v is Record<string, unknown> =>
    typeof v === "object" && v !== null && !Array.isArray(v);
const rec = (v: unknown): Record<string, unknown> => isRecord(v) ? v : {};
const num = (v: unknown): number | null =>
    typeof v === "number" && Number.isFinite(v) ? v : null;

/** A real `YYYY-MM-DD` date, or an INVALID_INPUT error. */
export function parseToday(today: unknown): string {
    if (typeof today !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(today)) {
        throw new CoachInputError("INVALID_INPUT", "today must be a date");
    }
    const d = new Date(`${today}T00:00:00Z`);
    if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== today) {
        throw new CoachInputError("INVALID_INPUT", "today is not a valid date");
    }
    return today;
}

/** The cut rate the user may choose (percent of body weight per week), 0.5-1.0. */
export function parseCutRate(v: unknown): number {
    if (
        typeof v !== "number" || !Number.isFinite(v) ||
        v < LOSS_RATE_RANGE[0] || v > LOSS_RATE_RANGE[1]
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "cut_rate_pct must be between 0.5 and 1.0",
        );
    }
    return v;
}

export type NutritionRequest = {
    today: string;
    cutRatePct?: number;
    dietBreak?: boolean;
};

/**
 * `{ "today": "YYYY-MM-DD" }` plus the optional `cut_rate_pct` (0.5-1.0) and `diet_break` (boolean), and nothing
 * else: the client never sends figures.
 */
export function parseInput(input: unknown): NutritionRequest {
    const allowed = ["today", "cut_rate_pct", "diet_break"];
    if (
        !isRecord(input) || !("today" in input) ||
        Object.keys(input).some((k) => !allowed.includes(k))
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Input must be { today, cut_rate_pct?, diet_break? }",
        );
    }
    const out: NutritionRequest = { today: parseToday(input.today) };
    if ("cut_rate_pct" in input) {
        out.cutRatePct = parseCutRate(input.cut_rate_pct);
    }
    if ("diet_break" in input) {
        if (typeof input.diet_break !== "boolean") {
            throw new CoachInputError(
                "INVALID_INPUT",
                "diet_break must be a boolean",
            );
        }
        out.dietBreak = input.diet_break;
    }
    return out;
}

export type DerivedNutrition = {
    intention: Intention;
    nutrition: Nutrition;
    activity: Activity;
    stepsAvg: number | null;
};

/**
 * Calories, macros and activity targets from the summary. Shared by `plan_nutrition` and `plan_recompute`; the
 * phase comes from the intention unless the caller fixes it (a recompute keeps its plan's).
 */
export function deriveNutrition(
    summary: unknown,
    o: {
        cutRatePct?: number;
        dietBreak?: boolean;
        fixed?: { intention: Intention; phase: Phase; reason: PhaseReason };
    } = {},
): DerivedNutrition {
    const s = rec(summary);
    const profile = rec(s.profile);
    const estimates = rec(s.estimates);
    const prefs = rec(s.training_preferences);
    const expenditure = rec(estimates.total_expenditure);

    const intention = o.fixed?.intention ??
        INTENTIONS.find((i) => i === prefs.intention);
    const sex: Sex | null = profile.sex === "male" || profile.sex === "female"
        ? profile.sex
        : null;
    const positive = (v: number | null) => v !== null && v > 0;
    const age = num(profile.age);
    const heightCm = num(profile.height_cm);
    const weightKg = num(profile.weight_kg);
    // Backstop only: the list of inputs already refused any missing root cause. A figure outside the plausibility
    // bounds counts as missing (07 section 4); an absent age or height is not an error (the list decides).
    const missing: string[] = [];
    if (!intention) missing.push("intention");
    if (sex === null) missing.push("sex");
    if (age !== null && !within(age, AGE_BOUNDS)) missing.push("age");
    if (heightCm !== null && !within(heightCm, HEIGHT_BOUNDS)) {
        missing.push("height");
    }
    if (!positive(weightKg) || !within(weightKg!, WEIGHT_BOUNDS)) {
        missing.push("weight");
    }
    if (!positive(num(expenditure.value))) missing.push("expenditure");
    if (!intention || missing.length > 0) {
        throw new CoachInputError(
            "coach_missing_data",
            "Required data is missing",
            missing,
        );
    }
    const experience =
        EXPERIENCES.find((e) => e === rec(estimates.experience).value) ??
            "beginner";
    const unconfirmed = unconfirmedKeys(estimates);
    // Underweight stop (07 section 4): a "Perder grasa" plan of a user under BMI 18.5 starts in maintenance. A recompute
    // keeps its plan's phase (`fixed`), so it never lands here.
    const first = o.fixed ?? (
        intention === "fat_loss" && heightCm !== null &&
            isUnderweight(weightKg!, heightCm)
            ? { phase: "maintenance" as const, reason: "underweight" as const }
            : firstPhase(intention)
    );
    const { phase, reason } = first;

    let nutrition: Nutrition;
    try {
        nutrition = computeNutrition({
            intention,
            phase,
            phaseReason: reason,
            sex,
            weightKg,
            expenditureKcal: num(expenditure.value),
            expenditureSource: typeof expenditure.source === "string"
                ? expenditure.source
                : "formula",
            intakeAnchorKcal: num(rec(estimates.intake).anchor_kcal),
            slopeUsed: expenditure.slope_used === true,
            experience,
            targetRatePct: o.cutRatePct,
            dietBreak: o.dietBreak,
            requiresConfirmation: unconfirmed.length > 0,
            unconfirmed,
        });
    } catch (e) {
        if (e instanceof CoachPlanError) {
            throw new CoachInputError(
                e.code,
                e.code === "coach_missing_data"
                    ? "Sex, weight or expenditure is missing"
                    : "No safe plan fits these numbers",
            );
        }
        throw e;
    }

    // Cardio and steps targets (07 section 3): independent of the calories, which stay as computed above.
    const stepsAvg = num(rec(rec(estimates.activity_level).sources).steps_avg);
    const activity = computeActivity({
        intention,
        stepsAvg28: stepsAvg,
        phase,
    });
    return { intention, nutrition, activity, stepsAvg };
}

export function prepare(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    return prepareAsync(input, ctx);
}

async function prepareAsync(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    const { today, cutRatePct, dietBreak } = parseInput(input);
    const summary = await ctx.loadContext(today);
    // The shared list decides what blocks (same source as the "Antes de empezar" screen).
    assertInputsComplete(await ctx.loadInputs("plan", today));
    const { intention, nutrition, activity, stepsAvg } = deriveNutrition(
        summary,
        { cutRatePct, dietBreak },
    );
    const stored = { ...nutrition, activity };
    // Outcomes by rule and Spanish templates, no model (ADR-98).
    const outcomes = buildOutcomes(
        buildCandidates(rec(summary) as CandidateSummary, nutrition, today),
        today,
    );

    return {
        finalize: async () => {
            assertNutritionInvariants(nutrition);
            assertActivityInvariants(activity, stepsAvg, nutrition.phase);
            const saved = await ctx.savePlan({
                blockStart: today,
                intention,
                nutrition: stored,
                outcomes,
                method: {
                    model: null,
                    attempts: 0,
                    cost_eur: 0,
                    constants_version: CONSTANTS_VERSION,
                },
                inputSummary: summary,
            });
            return {
                plan_id: saved.plan_id,
                recommendation_id: saved.recommendation_id,
                nutrition: stored,
                outcomes,
            };
        },
    };
}
