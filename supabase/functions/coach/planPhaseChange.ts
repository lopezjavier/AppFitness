// Kind `plan_phase_change` (T-233): "Aceptar" at the end-of-block review. The new phase comes from the stored proposal
// of the review; the calories and outcomes come from the same pure rules as `plan_nutrition`, with no model call.
// accept_coach_phase stores the result as a
// PROPOSED plan (the vehicle of the next block) and re-checks every invariant; nothing touches the current plan.
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
} from "./context.ts";
import { assertInputsComplete } from "./inputs.ts";
import { buildCandidates, type CandidateSummary } from "./outcomes.ts";
import { buildOutcomes } from "./outcomeTemplates.ts";
import { deriveNutrition, parseToday } from "./planNutrition.ts";
import { CONSTANTS_VERSION, type Intention, type Phase } from "./schemas.ts";
import {
    assertActivityInvariants,
    assertNutritionInvariants,
    isRecord,
} from "./validators.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const INTENTIONS: Intention[] = [
    "strength",
    "muscle",
    "fat_loss",
    "recomposition",
    "maintenance",
];
const PHASES: Phase[] = ["cut", "gain", "maintenance", "recomposition"];

export type PhaseChangeRequest = { today: string; reviewId: string };

/** `{ today, review_id }` and nothing else. */
export function parseInput(input: unknown): PhaseChangeRequest {
    const keys = ["today", "review_id"];
    if (
        !isRecord(input) || Object.keys(input).length !== keys.length ||
        keys.some((k) => !(k in input))
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Input must be { today, review_id }",
        );
    }
    if (typeof input.review_id !== "string" || !UUID.test(input.review_id)) {
        throw new CoachInputError("INVALID_INPUT", "review_id must be a uuid");
    }
    return { today: parseToday(input.today), reviewId: input.review_id };
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
    const { today, reviewId } = parseInput(input);
    const review = await ctx.loadReview(reviewId);
    if (!review) {
        throw new CoachInputError("INVALID_INPUT", "Review not found");
    }
    const toPhase = review.phase_proposal?.to_phase;
    if (!toPhase || review.phase_decision !== null) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "The review has no open phase proposal",
        );
    }
    const phase = PHASES.find((p) => p === toPhase);
    const intention = INTENTIONS.find((i) => i === review.intention);
    if (!phase || !intention) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "The review has no usable phase or intention",
        );
    }

    const summary = await ctx.loadContext(today);
    assertInputsComplete(await ctx.loadInputs("plan", today));
    const { nutrition, activity, stepsAvg } = deriveNutrition(summary, {
        cutRatePct: 0.5,
        dietBreak: false,
        fixed: { intention, phase, reason: "intention" },
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
            const saved = await ctx.applyPhaseChange({
                reviewId,
                blockStart: today,
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
            };
        },
    };
}
