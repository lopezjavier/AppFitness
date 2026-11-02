// Kinds of coach calls and the model each one uses (planning/07 section 11).
// `ping` (T-160), `plan_nutrition` (T-161), `plan_block` (T-163), `intake_extract` (T-185) and the model-free
// `plan_recompute` (T-187) are callable; `report` and `chat` answer
// coach_kind_not_available until their tasks.
import type { AnthropicRequest } from "../_shared/anthropic.ts";
import type { PrepareContext, Prepared } from "./context.ts";
import { prepare as prepareIntake } from "./intakeExtract.ts";
import { prepare as prepareBlock } from "./planBlock.ts";
import { prepare as prepareNutrition } from "./planNutrition.ts";
import { prepare as prepareRecompute } from "./planRecompute.ts";
import { type CoachModel, MODEL_IDS } from "./prices.ts";

export type KindSpec = {
    model: CoachModel;
    /** False while the feature that owns the kind is not built yet. */
    available: boolean;
    /** Builds the provider request from the validated `input`. */
    buildRequest?: (input: unknown, model: CoachModel) => AnthropicRequest;
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
    /**
     * No model call: the handler answers it before the provider and budget checks, records no usage and sends the
     * result of `finalize` with zero usage (T-187 `plan_recompute`). `model` is irrelevant.
     */
    deterministic?: boolean;
};

export const PING_PROMPT = "Responde: ok";
export const PING_MAX_TOKENS = 16;

export const KINDS: Record<string, KindSpec> = {
    ping: {
        model: "sonnet",
        available: true,
        buildRequest: (_input, model) => ({
            model: MODEL_IDS[model],
            prompt: PING_PROMPT,
            maxTokens: PING_MAX_TOKENS,
        }),
    },
    plan_nutrition: {
        model: "opus",
        available: true,
        prepare: prepareNutrition,
    },
    plan_block: { model: "opus", available: true, prepare: prepareBlock },
    plan_recompute: {
        model: "sonnet",
        available: true,
        deterministic: true,
        prepare: prepareRecompute,
    },
    intake_extract: {
        model: "sonnet",
        available: true,
        prepare: prepareIntake,
    },
    report: { model: "sonnet", available: false },
    chat: { model: "sonnet", available: false },
};
