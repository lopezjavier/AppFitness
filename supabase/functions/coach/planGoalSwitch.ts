// Kind `plan_goal_switch` (T-239): the deterministic preview of "Bloque nuevo" when the saved goal differs from the plan's.
// The figures come from the same pure rules as `plan_nutrition`, with the user's saved intention and no model call
// (the handler answers it before the provider and budget checks, with zero usage). propose_goal_switch stores the result
// as ONE proposed plan (the vehicle of the new block) and re-checks every invariant; nothing touches the accepted plan
// or nutrition_goals until the new block is saved. After a cut the phase starts at the measured maintenance in one step
// (07 section 4 "After a cut"): deriveNutrition prices every phase on the measured expenditure, never a reverse diet.
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
} from "./context.ts";
import { assertInputsComplete } from "./inputs.ts";
import { buildCandidates, type CandidateSummary } from "./outcomes.ts";
import { buildOutcomes } from "./outcomeTemplates.ts";
import { deriveNutrition, parseToday } from "./planNutrition.ts";
import { CONSTANTS_VERSION } from "./schemas.ts";
import {
    assertActivityInvariants,
    assertNutritionInvariants,
    isRecord,
} from "./validators.ts";

export type GoalSwitchRequest = { today: string };

/** `{ today }` and nothing else. */
export function parseInput(input: unknown): GoalSwitchRequest {
    if (
        !isRecord(input) || Object.keys(input).length !== 1 ||
        !("today" in input)
    ) {
        throw new CoachInputError("INVALID_INPUT", "Input must be { today }");
    }
    return { today: parseToday(input.today) };
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
    const { today } = parseInput(input);
    const summary = await ctx.loadContext(today);
    assertInputsComplete(await ctx.loadInputs("plan", today));
    // The saved intention picks the phase (no `fixed`): the goal pick is the phase change.
    const { nutrition, activity, stepsAvg } = deriveNutrition(summary, {
        cutRatePct: 0.5,
        dietBreak: false,
    });
    const stored = { ...nutrition, activity };
    const outcomes = buildOutcomes(
        buildCandidates(
            (isRecord(summary) ? summary : {}) as CandidateSummary,
            nutrition,
            today,
        ),
        today,
    );

    return {
        finalize: async () => {
            assertNutritionInvariants(nutrition);
            assertActivityInvariants(activity, stepsAvg, nutrition.phase);
            const saved = await ctx.applyGoalSwitch({
                today,
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
                nutrition: stored,
                outcomes,
                preview: {
                    current_kcal: saved.current_kcal,
                    new_kcal: saved.new_kcal,
                    phase: saved.phase,
                },
            };
        },
    };
}
