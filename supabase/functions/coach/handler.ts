// Pure request handler for `coach`: POST { kind, input } -> text/event-stream (delta then done), or a JSON error.
// Every kind is deterministic (no provider call, ADR-103). Auth and the data access are injected, so tests need no
// network. Never logs prompts, inputs or outputs. A `model` in the body is ignored.
import {
    bearerToken,
    errorResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import {
    type AttachedBlock,
    type BlockRow,
    CoachInputError,
    type DietBreakRow,
    type DietBreakSet,
    type GoalSwitched,
    type GoalSwitchRow,
    type OptionsRow,
    type PhaseChanged,
    type PhaseChangeRow,
    type PlanRow,
    type PlanState,
    type PrepareContext,
    type Prepared,
    type ReviewState,
    type SavedPlan,
    type UpdatedPlan,
} from "./context.ts";
import { KINDS, type KindSpec } from "./router.ts";
import type { CatalogExercise, PhaseHistory } from "./schemas.ts";

export type CoachDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** `coach_summary` of the caller (with the derived estimates), read with their own token. */
    loadContext: (token: string, today: string) => Promise<unknown>;
    /** `coach_plan_inputs` of the caller, read with their own token. */
    loadInputs: (
        token: string,
        kind: "plan" | "block",
        today: string,
    ) => Promise<unknown>;
    /** Stores a proposed plan through `propose_coach_plan`, with the caller's own token. */
    savePlan: (token: string, row: PlanRow) => Promise<SavedPlan>;
    /** Catalogue and own custom exercises, read with the caller's own token. */
    loadCatalog: (token: string) => Promise<CatalogExercise[]>;
    /** Status of one of the caller's plans (null when not theirs), read with their own token. */
    loadPlan: (token: string, planId: string) => Promise<PlanState | null>;
    /** Stores a block through `attach_coach_block`, with the caller's own token. */
    attachBlock: (token: string, row: BlockRow) => Promise<AttachedBlock>;
    /** `coach_phase_history` of the caller, read with their own token. */
    loadPhaseHistory: (token: string, today: string) => Promise<PhaseHistory>;
    /** Rewrites the options of a proposed cut through `update_coach_plan_options`, with the caller's own token. */
    updatePlanOptions: (
        token: string,
        row: OptionsRow,
    ) => Promise<UpdatedPlan>;
    /** Switches the diet break of an accepted cut through `set_coach_plan_diet_break`, with the caller's own token. */
    setDietBreak: (token: string, row: DietBreakRow) => Promise<DietBreakSet>;
    /** One of the caller's block reviews with its plan's intention (T-233), read with their own token. */
    loadReview: (
        token: string,
        reviewId: string,
    ) => Promise<ReviewState | null>;
    /** Stores the accepted phase change through `accept_coach_phase`, with the caller's own token. */
    applyPhaseChange: (
        token: string,
        row: PhaseChangeRow,
    ) => Promise<PhaseChanged>;
    /** Stores the goal-switch proposal through `propose_goal_switch`, with the caller's own token. */
    applyGoalSwitch: (
        token: string,
        row: GoalSwitchRow,
    ) => Promise<GoalSwitched>;
    now: () => Date;
    kinds?: Record<string, KindSpec>;
};

const SSE_HEADERS = {
    "content-type": "text/event-stream",
    "cache-control": "no-store",
} as const;

const encoder = new TextEncoder();
const frame = (event: string, data: unknown) =>
    encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);

/** Runs a kind's `prepare`; an input error the client can fix is answered as JSON (names only in the log). */
async function runPrepare(
    spec: KindSpec,
    kind: unknown,
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared | Response> {
    try {
        return await spec.prepare!(input, ctx);
    } catch (e) {
        if (e instanceof CoachInputError) {
            if (e.code !== "INVALID_INPUT") {
                // Names only: never values, prompts or summary data.
                console.warn("coach: input rejected", {
                    kind,
                    code: e.code,
                    missing: e.missing,
                });
            }
            return errorResponse(
                e.code,
                e.message,
                e.missing || e.extra
                    ? {
                        extra: {
                            ...(e.missing ? { missing: e.missing } : {}),
                            ...(e.extra ?? {}),
                        },
                    }
                    : {},
            );
        }
        throw e;
    }
}

export function createCoachHandler(
    deps: CoachDeps,
): (req: Request) => Promise<Response> {
    const kinds = deps.kinds ?? KINDS;

    return async (req) => {
        if (req.method !== "POST") return methodNotAllowed("POST");
        const token = bearerToken(req);
        if (!token) return unauthorized();

        let body: { kind?: unknown; input?: unknown; model?: unknown };
        try {
            body = await req.json();
        } catch {
            return errorResponse("INVALID_INPUT", "Body must be valid JSON");
        }
        const kindName = body && typeof body === "object" ? body.kind : null;
        const spec = typeof kindName === "string" &&
                Object.hasOwn(kinds, kindName)
            ? kinds[kindName]
            : null;
        if (!spec) return errorResponse("INVALID_INPUT", "Unknown kind");

        try {
            const userId = await deps.authenticate(token);
            if (!userId) return unauthorized();

            const ctx: PrepareContext = {
                loadContext: (today) => deps.loadContext(token, today),
                loadInputs: (k, today) => deps.loadInputs(token, k, today),
                savePlan: (row) => deps.savePlan(token, row),
                loadCatalog: () => deps.loadCatalog(token),
                loadPlan: (id) => deps.loadPlan(token, id),
                attachBlock: (row) => deps.attachBlock(token, row),
                loadPhaseHistory: (today) =>
                    deps.loadPhaseHistory(token, today),
                updatePlanOptions: (row) => deps.updatePlanOptions(token, row),
                setDietBreak: (row) => deps.setDietBreak(token, row),
                loadReview: (id) => deps.loadReview(token, id),
                applyPhaseChange: (row) => deps.applyPhaseChange(token, row),
                applyGoalSwitch: (row) => deps.applyGoalSwitch(token, row),
            };

            if (!spec.prepare) {
                return errorResponse("INTERNAL", "Unexpected error");
            }
            const ready = await runPrepare(spec, body.kind, body.input, ctx);
            if (ready instanceof Response) return ready;
            let text: string;
            try {
                text = JSON.stringify(await ready.finalize());
            } catch (e) {
                // A refusal the client can understand (e.g. coach_block_in_progress); anything else is internal.
                if (e instanceof CoachInputError) {
                    return errorResponse(e.code, e.message);
                }
                return errorResponse("INTERNAL", "Unexpected error");
            }
            // The installed app parses `usage` until it drops the field: always zero.
            return sse([
                frame("delta", { text }),
                frame("done", {
                    usage: { input_tokens: 0, output_tokens: 0, cost_eur: 0 },
                }),
            ]);
        } catch {
            return errorResponse("INTERNAL", "Unexpected error");
        }
    };
}

function sse(frames: Uint8Array[]): Response {
    return new Response(
        new ReadableStream({
            start(controller) {
                for (const f of frames) controller.enqueue(f);
                controller.close();
            },
        }),
        { status: 200, headers: SSE_HEADERS },
    );
}
