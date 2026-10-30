// Anthropic Messages API client for the `coach` function. All I/O is injected, so tests need no network
// and never make a paid call. Never logs or stores prompts or outputs; errors carry only a kind and status.

export const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
export const ANTHROPIC_VERSION = "2023-06-01";
/** Wall-clock limit of one call. Below the 150 s Edge Function limit so the client gets a clean error. */
export const COACH_TIMEOUT_MS = 120_000;

export type AnthropicRequest = {
    model: string;
    system?: string;
    prompt: string;
    maxTokens: number;
    /** JSON schema of the expected output; sent as structured output (`output_config.format`). */
    schema?: Record<string, unknown>;
};

export type AnthropicUsage = {
    inputTokens: number;
    outputTokens: number;
    cacheReadTokens: number;
    cacheWriteTokens: number;
};

export const NO_USAGE: AnthropicUsage = {
    inputTokens: 0,
    outputTokens: 0,
    cacheReadTokens: 0,
    cacheWriteTokens: 0,
};

export type AnthropicEvent =
    | { type: "delta"; text: string }
    /** Final usage of the call, emitted once before `done`. */
    | { type: "usage"; usage: AnthropicUsage }
    | { type: "done" };

export interface AnthropicClient {
    stream(
        request: AnthropicRequest,
        signal?: AbortSignal,
    ): AsyncIterable<AnthropicEvent>;
}

export type ProviderErrorKind = "provider" | "timeout" | "invalid_output";

/** Never carries the provider body, the prompt or the output: only the kind and the HTTP status. */
export class ProviderError extends Error {
    constructor(readonly kind: ProviderErrorKind, readonly status?: number) {
        super(`anthropic ${kind}`);
        this.name = "ProviderError";
    }
}

/** The provider account has no credit left (billing rejection): not retryable, nothing was consumed. */
export class ProviderCreditError extends Error {
    constructor() {
        super("anthropic credit");
        this.name = "ProviderCreditError";
    }
}

/** The output failed validation twice. Carries the usage of every attempt so it is still charged. */
export class InvalidOutputError extends Error {
    constructor(readonly usage: AnthropicUsage) {
        super("anthropic invalid output");
        this.name = "InvalidOutputError";
    }
}

export function addUsage(a: AnthropicUsage, b: AnthropicUsage): AnthropicUsage {
    return {
        inputTokens: a.inputTokens + b.inputTokens,
        outputTokens: a.outputTokens + b.outputTokens,
        cacheReadTokens: a.cacheReadTokens + b.cacheReadTokens,
        cacheWriteTokens: a.cacheWriteTokens + b.cacheWriteTokens,
    };
}

// --- SSE parsing -----------------------------------------------------------------------------------

export type SseMessage = { event: string; data: string };

/** Parses a `text/event-stream` body into messages (blank-line separated, `event:` and `data:` fields). */
export async function* parseSse(
    body: ReadableStream<Uint8Array>,
): AsyncGenerator<SseMessage> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    try {
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true }).replace(
                /\r\n/g,
                "\n",
            );
            let end: number;
            while ((end = buffer.indexOf("\n\n")) >= 0) {
                const message = toMessage(buffer.slice(0, end));
                buffer = buffer.slice(end + 2);
                if (message) yield message;
            }
        }
        const tail = toMessage(buffer);
        if (tail) yield tail;
    } finally {
        reader.releaseLock();
    }
}

function toMessage(block: string): SseMessage | null {
    let event = "message";
    const data: string[] = [];
    for (const line of block.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
    }
    return data.length ? { event, data: data.join("\n") } : null;
}

const num = (v: unknown) => (typeof v === "number" && v >= 0 ? v : 0);

/** Maps the provider's SSE messages to events. Throws ProviderError on an in-stream `error` event. */
export async function* toAnthropicEvents(
    messages: AsyncIterable<SseMessage>,
): AsyncGenerator<AnthropicEvent> {
    let usage = NO_USAGE;
    for await (const { data } of messages) {
        // deno-lint-ignore no-explicit-any
        let payload: Record<string, any>;
        try {
            payload = JSON.parse(data);
        } catch {
            continue; // not JSON (e.g. a keep-alive comment); ignore
        }
        switch (payload.type) {
            case "message_start": {
                const u = payload.message?.usage ?? {};
                usage = {
                    inputTokens: num(u.input_tokens),
                    outputTokens: num(u.output_tokens),
                    cacheReadTokens: num(u.cache_read_input_tokens),
                    cacheWriteTokens: num(u.cache_creation_input_tokens),
                };
                break;
            }
            case "content_block_delta":
                if (
                    payload.delta?.type === "text_delta" &&
                    typeof payload.delta.text === "string"
                ) {
                    yield { type: "delta", text: payload.delta.text };
                }
                break;
            case "message_delta":
                // Output tokens are cumulative; input tokens may be repeated by newer API versions.
                usage = {
                    ...usage,
                    outputTokens: num(payload.usage?.output_tokens) ||
                        usage.outputTokens,
                };
                break;
            case "message_stop":
                yield { type: "usage", usage };
                yield { type: "done" };
                return;
            case "error":
                throw errorFromBody(undefined, payload);
        }
    }
    // The stream ended without message_stop: treat as a provider failure.
    throw new ProviderError("provider");
}

// --- Error mapping ---------------------------------------------------------------------------------

/** Billing rejections: HTTP 402, or 400/403 `invalid_request_error` whose message talks about credit/billing. */
export function errorFromBody(
    status: number | undefined,
    body: unknown,
): ProviderError | ProviderCreditError {
    // deno-lint-ignore no-explicit-any
    const err = (body as any)?.error ?? body;
    const message = typeof err?.message === "string"
        ? err.message.toLowerCase()
        : "";
    const billing = /credit balance|billing|payment/.test(message);
    if (status === 402 || ((status === 400 || status === 403) && billing)) {
        return new ProviderCreditError();
    }
    return new ProviderError("provider", status);
}

// --- fetch implementation --------------------------------------------------------------------------

export type AnthropicDeps = {
    fetch: (input: string, init: RequestInit) => Promise<Response>;
    apiKey: string;
    timeoutMs?: number;
};

export function createAnthropicClient(deps: AnthropicDeps): AnthropicClient {
    return {
        async *stream(request, signal) {
            const controller = new AbortController();
            const timer = setTimeout(
                () => controller.abort(),
                deps.timeoutMs ?? COACH_TIMEOUT_MS,
            );
            const onAbort = () => controller.abort();
            signal?.addEventListener("abort", onAbort);
            try {
                const response = await deps.fetch(ANTHROPIC_URL, {
                    method: "POST",
                    headers: {
                        "content-type": "application/json",
                        "x-api-key": deps.apiKey,
                        "anthropic-version": ANTHROPIC_VERSION,
                    },
                    body: JSON.stringify(buildBody(request)),
                    signal: controller.signal,
                });
                if (!response.ok) {
                    let body: unknown = null;
                    try {
                        body = await response.json();
                    } catch {
                        // keep null: classification falls back to the status
                    }
                    throw errorFromBody(response.status, body);
                }
                if (!response.body) throw new ProviderError("provider");
                yield* toAnthropicEvents(parseSse(response.body));
            } catch (e) {
                if (
                    e instanceof ProviderError ||
                    e instanceof ProviderCreditError
                ) {
                    throw e;
                }
                if (controller.signal.aborted) {
                    throw new ProviderError("timeout");
                }
                throw new ProviderError("provider");
            } finally {
                clearTimeout(timer);
                signal?.removeEventListener("abort", onAbort);
            }
        },
    };
}

function buildBody(request: AnthropicRequest): Record<string, unknown> {
    const body: Record<string, unknown> = {
        model: request.model,
        max_tokens: request.maxTokens,
        stream: true,
        messages: [{ role: "user", content: request.prompt }],
    };
    if (request.system) body.system = request.system;
    if (request.schema) {
        body.output_config = {
            format: { type: "json_schema", schema: request.schema },
        };
    }
    return body;
}

// --- complete() -------------------------------------------------------------------------------------

export type Completion = { text: string; usage: AnthropicUsage };

/** Runs one call to the end and returns the whole text and the usage reported by the API. */
export async function complete(
    client: AnthropicClient,
    request: AnthropicRequest,
    signal?: AbortSignal,
): Promise<Completion> {
    let text = "";
    let usage = NO_USAGE;
    for await (const event of client.stream(request, signal)) {
        if (event.type === "delta") text += event.text;
        else if (event.type === "usage") usage = event.usage;
    }
    return { text, usage };
}

export type Validated<T> = { value: T; text: string; usage: AnthropicUsage };

/**
 * Completes and validates; an invalid output gets one retry, then InvalidOutputError (carrying the usage of
 * both attempts). `validate` returns the parsed value or null when invalid.
 */
export async function completeValidated<T>(
    client: AnthropicClient,
    request: AnthropicRequest,
    validate: (text: string) => T | null,
    signal?: AbortSignal,
): Promise<Validated<T>> {
    let total = NO_USAGE;
    for (let attempt = 0; attempt < 2; attempt++) {
        const { text, usage } = await complete(client, request, signal);
        total = addUsage(total, usage);
        const value = validate(text);
        if (value !== null) return { value, text, usage: total };
    }
    throw new InvalidOutputError(total);
}
