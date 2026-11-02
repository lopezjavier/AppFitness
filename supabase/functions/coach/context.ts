// Types shared by the handler and the kinds that prepare their own request (T-161).
import type { AnthropicRequest } from "../_shared/anthropic.ts";
import type { ErrorCode } from "../_shared/errors.ts";
import type { CoachModel } from "./prices.ts";
import type {
    CatalogExercise,
    Intention,
    Method,
    Nutrition,
    Outcome,
    PhaseHistory,
} from "./schemas.ts";

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

/** Row sent to attach_coach_block (T-163). */
export type BlockRow = {
    planId: string;
    training: unknown;
    sessions: unknown;
    method: Method;
    inputSummary: unknown;
};

export type AttachedBlock = { plan_id: string; session_ids: string[] };

/** The caller's plan a block is attached to; null when it is not theirs. */
export type PlanState = {
    status: string;
    training_saved: boolean;
    /** `nutrition.activity.cardio_min_week`; null/absent for a plan stored before T-181. */
    cardio_min_week?: number | null;
    /** The plan JSON and its phase column (T-187); absent in fakes of older kinds. */
    nutrition?: Nutrition | null;
    phase?: string | null;
};

/** Row sent to update_coach_plan_options (T-187). */
export type OptionsRow = {
    planId: string;
    nutrition: Nutrition;
    cutRatePct: number;
    dietBreak: boolean;
};

/** What update_coach_plan_options answers: the rewritten plan without the weight outcome. */
export type UpdatedPlan = {
    plan_id: string;
    nutrition: Nutrition;
    outcomes: Outcome[];
};

/** What a kind needs from the outside world; built by the handler for the caller's token. */
export type PrepareContext = {
    /** The model resolved for this request (client override or the kind's default). */
    model: CoachModel;
    /** The `coach_summary` of the caller (includes the derived estimates), read with their own token. */
    loadContext: (today: string) => Promise<unknown>;
    /** `coach_plan_inputs` of the caller: what a plan or a block reads and what is missing (see inputs.ts). */
    loadInputs: (kind: "plan" | "block", today: string) => Promise<unknown>;
    savePlan: (row: PlanRow) => Promise<SavedPlan>;
    /** The exercises the caller can use (catalogue and own custom ones), read with their own token. */
    loadCatalog: () => Promise<CatalogExercise[]>;
    /** Status of one of the caller's plans, or null when it does not exist for them. */
    loadPlan: (planId: string) => Promise<PlanState | null>;
    attachBlock: (row: BlockRow) => Promise<AttachedBlock>;
    /** Phase history of the caller (previous block, minimal blocks in a row, deficit weeks), with their own token. */
    loadPhaseHistory: (today: string) => Promise<PhaseHistory>;
    /** Rewrites the options of a proposed cut through update_coach_plan_options, with the caller's own token. */
    updatePlanOptions: (row: OptionsRow) => Promise<UpdatedPlan>;
};

/** One independently validated model call of a multi-part kind. */
export type PreparedPart = {
    request: AnthropicRequest;
    validate: (text: string) => unknown | null;
};

export type Prepared = {
    request?: AnthropicRequest;
    /** Closure over the server-built candidates: parsed value, or null when the text is invalid. */
    validate?: (text: string) => unknown | null;
    /**
     * Several calls in parallel (each validated and retried once on its own). When present, `request` and
     * `validate` are ignored and `finalize` receives the array of validated values, in order.
     */
    parts?: PreparedPart[];
    /** Runs after the validated model call: final checks, persistence, and the value sent to the client. */
    finalize: (value: unknown, usage: { costEur: number }) => Promise<unknown>;
};

/** An error the client caused or can fix; answered as JSON before any model call. */
export class CoachInputError extends Error {
    constructor(
        readonly code: ErrorCode,
        message: string,
        /** Names of the missing fields (never values) for `coach_missing_data`. */
        readonly missing?: string[],
    ) {
        super(message);
        this.name = "CoachInputError";
    }
}
