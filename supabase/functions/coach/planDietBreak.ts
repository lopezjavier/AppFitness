// Kind `plan_diet_break` (T-248): the diet-break switch of an ACCEPTED cut ("Tu plan"), deterministic like
// `plan_recompute` (no model call).
// Only `nutrition.diet_break` and `phase_options.diet_break` change: every other figure stays as stored.
// set_coach_plan_diet_break re-checks the invariants and, when the break is turned off during week 4, restores the
// base goals itself.
import {
    CoachInputError,
    type DietBreakSet,
    type PrepareContext,
    type Prepared,
} from "./context.ts";
import { assertInputsComplete } from "./inputs.ts";
import { deriveNutrition, parseToday } from "./planNutrition.ts";
import { assertNutritionInvariants, isRecord } from "./validators.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type DietBreakRequest = {
    today: string;
    planId: string;
    dietBreak: boolean;
};

/** `{ today, plan_id, diet_break }` and nothing else. */
export function parseInput(input: unknown): DietBreakRequest {
    const keys = ["today", "plan_id", "diet_break"];
    if (
        !isRecord(input) || Object.keys(input).length !== keys.length ||
        keys.some((k) => !(k in input))
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Input must be { today, plan_id, diet_break }",
        );
    }
    if (typeof input.plan_id !== "string" || !UUID.test(input.plan_id)) {
        throw new CoachInputError("INVALID_INPUT", "plan_id must be a uuid");
    }
    if (typeof input.diet_break !== "boolean") {
        throw new CoachInputError(
            "INVALID_INPUT",
            "diet_break must be a boolean",
        );
    }
    return {
        today: parseToday(input.today),
        planId: input.plan_id,
        dietBreak: input.diet_break,
    };
}

export function prepare(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    return prepareAsync(input, ctx);
}

/**
 * The summary with the plan's own weight and expenditure: the week-4 figures must be consistent with the stored
 * nutrition (the server checks `calorie_adjustment = target - expenditure_kcal` of the stored plan).
 */
function withStoredFigures(
    summary: unknown,
    weightKg: number,
    expenditureKcal: number,
): unknown {
    const s = isRecord(summary) ? summary : {};
    const profile = isRecord(s.profile) ? s.profile : {};
    const estimates = isRecord(s.estimates) ? s.estimates : {};
    const expenditure = isRecord(estimates.total_expenditure)
        ? estimates.total_expenditure
        : {};
    return {
        ...s,
        profile: { ...profile, weight_kg: weightKg },
        estimates: {
            ...estimates,
            total_expenditure: { ...expenditure, value: expenditureKcal },
        },
    };
}

async function prepareAsync(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    const { today, planId, dietBreak } = parseInput(input);
    const plan = await ctx.loadPlan(planId);
    if (!plan || plan.status !== "accepted") {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Plan not found or not accepted",
        );
    }
    const stored = plan.nutrition ?? null;
    if (!stored || plan.phase !== "cut") {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Only an accepted cut has the diet break",
        );
    }
    if (stored.edited_by_user === true) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "A plan edited by the user has no diet break",
        );
    }

    let week: DietBreakSet["nutrition"]["diet_break"] = null;
    if (dietBreak) {
        const summary = await ctx.loadContext(today);
        assertInputsComplete(await ctx.loadInputs("plan", today));
        const derived = deriveNutrition(
            withStoredFigures(
                summary,
                stored.weight_kg,
                stored.expenditure_kcal,
            ),
            {
                cutRatePct: stored.phase_options?.cut_rate_pct,
                dietBreak: true,
                fixed: {
                    intention: stored.intention,
                    phase: "cut",
                    reason: stored.phase_reason ?? "intention",
                },
            },
        );
        assertNutritionInvariants(derived.nutrition);
        week = derived.nutrition.diet_break;
        if (!week) {
            throw new CoachInputError(
                "INVALID_INPUT",
                "No diet break fits this plan",
            );
        }
    }

    return {
        finalize: async () =>
            await ctx.setDietBreak({ planId, dietBreak, week }),
    };
}
