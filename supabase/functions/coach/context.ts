// Types shared by the handler and the kinds that prepare their own request (T-161).
import type { AnthropicRequest } from "../_shared/anthropic.ts";
import type { ErrorCode } from "../_shared/errors.ts";
import type { Intention, Method, Nutrition, Outcome } from "./schemas.ts";

/** Row sent to propose_coach_plan. */
export type PlanRow = {
    blockStart: string;
    intention: Intention;
    nutrition: Nutrition;
    outcomes: Outcome[];
    method: Method;
    inputSummary: unknown;
};

export type SavedPlan = { plan_id: string; recommendation_id: string };

/** What a kind needs from the outside world; built by the handler for the caller's token. */
export type PrepareContext = {
    /** The `coach_summary` of the caller (includes the derived estimates), read with their own token. */
    loadContext: (today: string) => Promise<unknown>;
    savePlan: (row: PlanRow) => Promise<SavedPlan>;
};

export type Prepared = {
    request: AnthropicRequest;
    /** Closure over the server-built candidates: parsed value, or null when the text is invalid. */
    validate: (text: string) => unknown | null;
    /** Runs after the validated model call: final checks, persistence, and the value sent to the client. */
    finalize: (value: unknown, usage: { costEur: number }) => Promise<unknown>;
};

/** An error the client caused or can fix; answered as JSON before any model call. */
export class CoachInputError extends Error {
    constructor(readonly code: ErrorCode, message: string) {
        super(message);
        this.name = "CoachInputError";
    }
}
