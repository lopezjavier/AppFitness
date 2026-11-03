// Shared harness for the block_splits and plan_block tests: the real handler, a fake model client and fake loaders.
// No tests here, so importing it never re-runs a suite; no network, no paid call.
import {
    type AnthropicClient,
    type AnthropicEvent,
    type AnthropicRequest,
    NO_USAGE,
} from "../_shared/anthropic.ts";
import { fullCatalog } from "./blockFixtures.ts";
import type {
    BlockModelInput,
    BlockRow,
    PlanState,
    PrepareContext,
    Prepared,
    ReviewRow,
    StoredReview,
} from "./context.ts";
import type { FolderForReview } from "./folderChanges.ts";
import type { KindSpec } from "./router.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { blockInputs } from "./inputsFixtures.ts";
import type { CatalogExercise } from "./schemas.ts";

export const NOW = new Date("2026-10-30T12:00:00Z");
export const PLAN_ID = "11111111-1111-4111-8111-111111111111";
export const SECRET = "SECRET-PERSONAL-DATA";
export const REVIEW_ID = "22222222-2222-4222-8222-222222222222";

export const EMPTY_MODEL_INPUT: BlockModelInput = {
    folders: [],
    history: [],
    previous_blocks: [],
    favourites: [],
    unresolved_exercise_ids: [],
};

export const summary = (tp: Record<string, unknown> = {}) => ({
    profile: { sex: "male", weight_kg: 80, email: SECRET },
    estimates: { experience: { value: "intermediate" } },
    training_preferences: {
        intention: "muscle",
        place: "gym",
        equipment: [],
        days_per_week: 4,
        session_minutes: 60,
        ...tp,
    },
});

export type HarnessOptions = {
    kind: string;
    model: "sonnet" | "opus";
    prepare: (input: unknown, ctx: PrepareContext) => Promise<Prepared>;
    /** Text of the model's answer to call n (1-based); the request is what the model was sent. */
    answer: (request: AnthropicRequest, n: number) => string;
    tp?: Record<string, unknown>;
    summary?: unknown;
    plan?: PlanState | null;
    catalog?: CatalogExercise[];
    modelInput?: BlockModelInput;
    inputs?: unknown;
    attachFails?: boolean;
    /** The folder `coach_folder_for_review` answers (null: not the caller's); absent: the loader is not expected. */
    folder?: FolderForReview | null;
    /** The stored review `loadReview` answers; absent: the loader is not expected. */
    review?: StoredReview | null;
    reviewId?: string;
    /** Passed to the registered kind (a predicate for plan_block). */
    deterministic?: KindSpec["deterministic"];
    spent?: number;
    timeoutMs?: number;
    slow?: boolean;
};

export function harness(o: HarnessOptions) {
    const records: Parameters<CoachDeps["recordUsage"]>[0][] = [];
    const attached: BlockRow[] = [];
    const reviews: ReviewRow[] = [];
    const requests: AnthropicRequest[] = [];
    let calls = 0;
    const client: AnthropicClient = {
        async *stream(request) {
            const n = ++calls;
            requests.push(request);
            if (o.slow) await new Promise(() => {});
            yield {
                type: "delta",
                text: o.answer(request, n),
            } as AnthropicEvent;
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 1000, outputTokens: 100 },
            } as AnthropicEvent;
            yield { type: "done" } as AnthropicEvent;
        },
    };
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        userSpent: () => Promise.resolve(o.spent ?? 0),
        globalSpent: () => Promise.resolve(0),
        recordUsage: (r) => {
            records.push(r);
            return Promise.resolve();
        },
        client,
        loadInputs: () => Promise.resolve(o.inputs ?? blockInputs()),
        loadContext: () => Promise.resolve(o.summary ?? summary(o.tp)),
        savePlan: () => Promise.reject(new Error("unexpected savePlan")),
        loadCatalog: () => Promise.resolve(o.catalog ?? fullCatalog()),
        loadPlan: () =>
            Promise.resolve(
                o.plan === undefined
                    ? { status: "proposed", training_saved: false }
                    : o.plan,
            ),
        loadPhaseHistory: () =>
            Promise.reject(new Error("unexpected loadPhaseHistory")),
        loadBlockModelInput: () =>
            Promise.resolve(o.modelInput ?? EMPTY_MODEL_INPUT),
        updatePlanOptions: () =>
            Promise.reject(new Error("unexpected updatePlanOptions")),
        loadFolder: () =>
            o.folder === undefined
                ? Promise.reject(new Error("unexpected loadFolder"))
                : Promise.resolve(o.folder),
        saveReview: (_t, row) => {
            reviews.push(row);
            return Promise.resolve(o.reviewId ?? REVIEW_ID);
        },
        loadReview: () =>
            o.review === undefined
                ? Promise.reject(new Error("unexpected loadReview"))
                : Promise.resolve(o.review),
        attachBlock: (_t, row) => {
            if (o.attachFails) return Promise.reject(new Error("db down"));
            attached.push(row);
            return Promise.resolve({
                plan_id: row.planId,
                session_ids: ["a", "b"],
            });
        },
        now: () => NOW,
        timeoutMs: o.timeoutMs,
        kinds: {
            [o.kind]: {
                model: o.model,
                available: true,
                prepare: o.prepare,
                deterministic: o.deterministic,
            },
        },
    };
    return {
        handler: createCoachHandler(deps),
        records,
        attached,
        reviews,
        requests,
        calls: () => calls,
    };
}

export const post = (kind: string, input: unknown, model?: unknown) =>
    new Request("http://x/coach", {
        method: "POST",
        headers: { authorization: "Bearer good" },
        body: JSON.stringify({ kind, input, model }),
    });

export const bodyOf = async (res: Response) => {
    const text = await res.text();
    const delta = text.match(/event: delta\ndata: (.*)\n/)![1];
    return JSON.parse(JSON.parse(delta).text);
};
