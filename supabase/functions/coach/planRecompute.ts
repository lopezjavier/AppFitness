// Kind `plan_recompute` (T-187): the cut-rate choice and the diet-break switch of a proposed plan, recomputed by the
// server with no model call and no usage (the handler answers it before the provider and the budget checks).
// Every figure comes from the same pure rules as `plan_nutrition`; update_coach_plan_options re-checks them and drops the
// weight outcome (its target and reason cited the old rate).
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
} from "./context.ts";
import { assertInputsComplete } from "./inputs.ts";
import { deriveNutrition, parseCutRate, parseToday } from "./planNutrition.ts";
import {
    assertActivityInvariants,
    assertNutritionInvariants,
    isRecord,
} from "./validators.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type RecomputeRequest = {
    today: string;
    planId: string;
    cutRatePct: number;
    dietBreak: boolean;
};

/** `{ today, plan_id, cut_rate_pct, diet_break }` and nothing else. */
export function parseInput(input: unknown): RecomputeRequest {
    const keys = ["today", "plan_id", "cut_rate_pct", "diet_break"];
    if (
        !isRecord(input) || Object.keys(input).length !== keys.length ||
        keys.some((k) => !(k in input))
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Input must be { today, plan_id, cut_rate_pct, diet_break }",
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
        cutRatePct: parseCutRate(input.cut_rate_pct),
        dietBreak: input.diet_break,
    };
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
    const { today, planId, cutRatePct, dietBreak } = parseInput(input);
    const plan = await ctx.loadPlan(planId);
    if (!plan || plan.status !== "proposed") {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Plan not found or not proposed",
        );
    }
    const stored = plan.nutrition ?? null;
    if (!stored || plan.phase !== "cut") {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Only a proposed cut has options",
        );
    }

    const summary = await ctx.loadContext(today);
    assertInputsComplete(await ctx.loadInputs("plan", today));
    // The plan keeps its own intention, phase and reason; only the rate and the diet break change.
    const derived = deriveNutrition(summary, {
        cutRatePct,
        dietBreak,
        fixed: {
            intention: stored.intention,
            phase: "cut",
            reason: stored.phase_reason ?? "intention",
        },
    });
    // The user's own cardio edit (set_coach_plan_cardio) survives the recompute; it was validated when stored.
    const activity = stored.activity ?? derived.activity;

    return {
        finalize: async () => {
            assertNutritionInvariants(derived.nutrition);
            if (!stored.activity) {
                assertActivityInvariants(
                    activity,
                    derived.stepsAvg,
                    derived.nutrition.phase,
                );
            }
            return await ctx.updatePlanOptions({
                planId,
                nutrition: { ...derived.nutrition, activity },
                cutRatePct,
                dietBreak,
            });
        },
    };
}
