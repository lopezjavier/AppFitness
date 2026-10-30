// Pure request handler for `coach`: POST { kind, input } -> text/event-stream (delta* then done), or a JSON error
// before streaming starts. Auth, budget reads/writes and the Anthropic client are injected, so tests need no
// network and make no paid call. Never logs prompts, inputs or outputs.
import {
    addUsage,
    type AnthropicClient,
    type AnthropicEvent,
    type AnthropicUsage,
    COACH_TIMEOUT_MS,
    completeValidated,
    InvalidOutputError,
    NO_USAGE,
    ProviderCreditError,
    ProviderError,
} from "../_shared/anthropic.ts";
import {
    bearerToken,
    errorResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import { checkBudget, monthKey } from "./budget.ts";
import { costEur } from "./prices.ts";
import { KINDS, type KindSpec } from "./router.ts";

export type CoachDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** Euros the caller spent in `month` (read with the caller's own token, through RLS). */
    userSpent: (token: string, month: string) => Promise<number>;
    /** Euros every user spent in `month`. */
    globalSpent: (month: string) => Promise<number>;
    recordUsage: (
        record: {
            userId: string;
            month: string;
            inputTokens: number;
            outputTokens: number;
            costEur: number;
        },
    ) => Promise<void>;
    /** Null when ANTHROPIC_API_KEY is not configured. */
    client: AnthropicClient | null;
    now: () => Date;
    timeoutMs?: number;
    kinds?: Record<string, KindSpec>;
};

const SSE_HEADERS = {
    "content-type": "text/event-stream",
    "cache-control": "no-store",
} as const;

const encoder = new TextEncoder();
const frame = (event: string, data: unknown) =>
    encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);

class Deadline {
    readonly controller = new AbortController();
    private timer: ReturnType<typeof setTimeout> | undefined;
    private readonly expired: Promise<never>;
    constructor(ms: number) {
        this.expired = new Promise<never>((_, reject) => {
            this.timer = setTimeout(() => {
                this.controller.abort();
                reject(new ProviderError("timeout"));
            }, ms);
        });
        this.expired.catch(() => {}); // avoid an unhandled rejection when nobody is racing
    }
    race<T>(p: Promise<T>): Promise<T> {
        return Promise.race([p, this.expired]);
    }
    clear() {
        clearTimeout(this.timer);
    }
}

/** Maps a failed call to its JSON error; nothing from the provider body or the prompt is included. */
function failure(e: unknown): Response {
    if (e instanceof ProviderCreditError) {
        return errorResponse("coach_no_credit", "AI provider has no credit");
    }
    if (e instanceof InvalidOutputError) {
        return errorResponse("coach_invalid_output", "Invalid model output");
    }
    if (e instanceof ProviderError) {
        if (e.kind === "timeout") {
            return errorResponse("coach_timeout", "The AI call timed out");
        }
        return errorResponse("PROVIDER_ERROR", "AI provider failed");
    }
    return errorResponse("INTERNAL", "Unexpected error");
}

const failureCode = (e: unknown): string =>
    e instanceof ProviderCreditError
        ? "coach_no_credit"
        : e instanceof InvalidOutputError
        ? "coach_invalid_output"
        : e instanceof ProviderError && e.kind === "timeout"
        ? "coach_timeout"
        : e instanceof ProviderError
        ? "PROVIDER_ERROR"
        : "INTERNAL";

export function createCoachHandler(
    deps: CoachDeps,
): (req: Request) => Promise<Response> {
    const kinds = deps.kinds ?? KINDS;

    return async (req) => {
        if (req.method !== "POST") return methodNotAllowed("POST");
        const token = bearerToken(req);
        if (!token) return unauthorized();

        let body: { kind?: unknown; input?: unknown };
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

            if (!spec.available || !spec.buildRequest) {
                return errorResponse(
                    "coach_kind_not_available",
                    "This kind is not available yet",
                );
            }
            if (!deps.client) {
                return errorResponse(
                    "NOT_CONFIGURED",
                    "AI provider is not configured",
                );
            }

            const month = monthKey(deps.now());
            const [userSpent, globalSpent] = await Promise.all([
                deps.userSpent(token, month),
                deps.globalSpent(month),
            ]);
            const verdict = checkBudget(userSpent, globalSpent);
            if (!verdict.ok) {
                return errorResponse(
                    "coach_limit_reached",
                    "Monthly AI limit reached",
                    { extra: { scope: verdict.scope } },
                );
            }

            const request = spec.buildRequest(body.input);
            const deadline = new Deadline(deps.timeoutMs ?? COACH_TIMEOUT_MS);
            const record = async (usage: AnthropicUsage) => {
                const cost = costEur(spec.model, usage);
                const inputTokens = usage.inputTokens + usage.cacheReadTokens +
                    usage.cacheWriteTokens;
                try {
                    await deps.recordUsage({
                        userId,
                        month,
                        inputTokens,
                        outputTokens: usage.outputTokens,
                        costEur: cost,
                    });
                } catch {
                    console.error("coach: usage not recorded");
                }
                return {
                    input_tokens: inputTokens,
                    output_tokens: usage.outputTokens,
                    cost_eur: cost,
                };
            };

            if (spec.validate) {
                // Buffered: validated output is sent whole, after the (single) retry.
                const validate = spec.validate;
                try {
                    const result = await deadline.race(
                        completeValidated(
                            deps.client,
                            request,
                            validate,
                            deadline.controller.signal,
                        ),
                    );
                    const usage = await record(result.usage);
                    return sse([
                        frame("delta", { text: result.text }),
                        frame("done", { usage }),
                    ]);
                } catch (e) {
                    if (e instanceof InvalidOutputError) await record(e.usage);
                    return failure(e);
                } finally {
                    deadline.clear();
                }
            }

            // Streamed: pull the first event before answering so early failures are JSON errors.
            const iterator = deps.client
                .stream(request, deadline.controller.signal)[
                Symbol.asyncIterator
            ]();
            let first: IteratorResult<AnthropicEvent>;
            try {
                first = await deadline.race(iterator.next());
            } catch (e) {
                deadline.clear();
                return failure(e);
            }
            return streamResponse(iterator, first, deadline, record);
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

function streamResponse(
    iterator: AsyncIterator<AnthropicEvent>,
    first: IteratorResult<AnthropicEvent>,
    deadline: Deadline,
    record: (usage: AnthropicUsage) => Promise<
        { input_tokens: number; output_tokens: number; cost_eur: number }
    >,
): Response {
    const body = new ReadableStream<Uint8Array>({
        async start(controller) {
            let usage = NO_USAGE;
            let finished = false;
            let recorded = false;
            try {
                let step = first;
                while (!step.done) {
                    const event = step.value;
                    if (event.type === "delta") {
                        controller.enqueue(
                            frame("delta", { text: event.text }),
                        );
                    } else if (event.type === "usage") {
                        usage = addUsage(NO_USAGE, event.usage);
                    } else {
                        finished = true;
                        break;
                    }
                    step = await deadline.race(iterator.next());
                }
                if (!finished) throw new ProviderError("provider");
                recorded = true;
                controller.enqueue(
                    frame("done", { usage: await record(usage) }),
                );
            } catch (e) {
                // Tokens seen before the failure were spent: charge them (a billing rejection consumed nothing).
                if (
                    !(e instanceof ProviderCreditError) && !recorded &&
                    usage !== NO_USAGE
                ) {
                    recorded = true;
                    await record(usage);
                }
                controller.enqueue(frame("error", { code: failureCode(e) }));
                try {
                    await iterator.return?.();
                } catch {
                    // already failed; nothing to add
                }
            } finally {
                deadline.clear();
                controller.close();
            }
        },
    });
    return new Response(body, { status: 200, headers: SSE_HEADERS });
}
