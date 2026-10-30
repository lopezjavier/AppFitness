// Kinds of coach calls and the model each one uses (planning/07 section 11).
// `ping` (T-160) and `plan_nutrition` (T-161) are callable; the others answer coach_kind_not_available until T-162+.
import type { AnthropicRequest } from "../_shared/anthropic.ts";
import type { PrepareContext, Prepared } from "./context.ts";
import { prepare as prepareNutrition } from "./planNutrition.ts";
import { type CoachModel, MODEL_IDS } from "./prices.ts";

export type KindSpec = {
    model: CoachModel;
    /** False while the feature that owns the kind is not built yet. */
    available: boolean;
    /** Builds the provider request from the validated `input`. */
    buildRequest?: (input: unknown) => AnthropicRequest;
    /**
     * Output validator: parsed value, or null when invalid. A kind with a validator is buffered (no partial
     * deltas) and gets one retry before coach_invalid_output.
     */
    validate?: (text: string) => unknown | null;
    /**
     * Kinds that need context (summary, candidates) build their request, validator and `finalize` step here
     * instead of `buildRequest`/`validate`; the validator is a closure over the server-built candidates.
     */
    prepare?: (input: unknown, ctx: PrepareContext) => Promise<Prepared>;
};

export const PING_PROMPT = "Responde: ok";
export const PING_MAX_TOKENS = 16;

export const KINDS: Record<string, KindSpec> = {
    ping: {
        model: "sonnet",
        available: true,
        buildRequest: () => ({
            model: MODEL_IDS.sonnet,
            prompt: PING_PROMPT,
            maxTokens: PING_MAX_TOKENS,
        }),
    },
    plan_nutrition: {
        model: "opus",
        available: true,
        prepare: prepareNutrition,
    },
    plan_block: { model: "opus", available: false },
    report: { model: "sonnet", available: false },
    chat: { model: "sonnet", available: false },
};
