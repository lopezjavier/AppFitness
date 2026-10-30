import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import {
    type AnthropicClient,
    type AnthropicEvent,
    type AnthropicRequest,
    complete,
    completeValidated,
    createAnthropicClient,
    InvalidOutputError,
    NO_USAGE,
    ProviderCreditError,
    ProviderError,
} from "./anthropic.ts";

const REQUEST: AnthropicRequest = {
    model: "m",
    prompt: "p",
    maxTokens: 16,
};

// Recorded shape of a streamed Messages response (text redacted to a fixture word).
const STREAM = [
    'event: message_start\ndata: {"type":"message_start","message":{"usage":{"input_tokens":12,"output_tokens":1,"cache_read_input_tokens":3,"cache_creation_input_tokens":4}}}',
    'event: ping\ndata: {"type":"ping"}',
    'event: content_block_delta\ndata: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"o"}}',
    'event: content_block_delta\ndata: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"k"}}',
    'event: message_delta\ndata: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":7}}',
    'event: message_stop\ndata: {"type":"message_stop"}',
].join("\n\n") + "\n\n";

const sse = (text: string, chunk = 64) => {
    const bytes = new TextEncoder().encode(text);
    return new ReadableStream<Uint8Array>({
        start(controller) {
            for (let i = 0; i < bytes.length; i += chunk) {
                controller.enqueue(bytes.slice(i, i + chunk));
            }
            controller.close();
        },
    });
};

const clientFor = (res: () => Response, timeoutMs?: number) =>
    createAnthropicClient({
        fetch: () => Promise.resolve(res()),
        apiKey: "test-key",
        timeoutMs,
    });

async function collect(
    client: AnthropicClient,
): Promise<AnthropicEvent[]> {
    const out: AnthropicEvent[] = [];
    for await (const e of client.stream(REQUEST)) out.push(e);
    return out;
}

Deno.test("stream parses deltas, usage and done from a chunked SSE body", async () => {
    const events = await collect(
        clientFor(() => new Response(sse(STREAM, 17))),
    );
    assertEquals(events, [
        { type: "delta", text: "o" },
        { type: "delta", text: "k" },
        {
            type: "usage",
            usage: {
                inputTokens: 12,
                outputTokens: 7,
                cacheReadTokens: 3,
                cacheWriteTokens: 4,
            },
        },
        { type: "done" },
    ]);
});

Deno.test("complete concatenates the text and returns the reported usage", async () => {
    const result = await complete(
        clientFor(() => new Response(sse(STREAM))),
        REQUEST,
    );
    assertEquals(result.text, "ok");
    assertEquals(result.usage.outputTokens, 7);
});

Deno.test("the request carries the key, version, stream flag and schema", async () => {
    let seen: { headers: Headers; body: Record<string, unknown> } | null = null;
    const client = createAnthropicClient({
        fetch: (_url, init) => {
            seen = {
                headers: new Headers(init.headers),
                body: JSON.parse(init.body as string),
            };
            return Promise.resolve(new Response(sse(STREAM)));
        },
        apiKey: "test-key",
    });
    await complete(client, { ...REQUEST, schema: { type: "object" } });
    assert(seen);
    const s = seen as { headers: Headers; body: Record<string, unknown> };
    assertEquals(s.headers.get("x-api-key"), "test-key");
    assertEquals(s.headers.get("anthropic-version"), "2023-06-01");
    assertEquals(s.body.stream, true);
    assertEquals(s.body.max_tokens, 16);
    assertEquals(s.body.output_config, {
        format: { type: "json_schema", schema: { type: "object" } },
    });
});

Deno.test("HTTP 400 credit balance too low maps to ProviderCreditError", async () => {
    const body = JSON.stringify({
        type: "error",
        error: {
            type: "invalid_request_error",
            message:
                "Your credit balance is too low to access the Anthropic API.",
        },
    });
    await assertRejects(
        () => collect(clientFor(() => new Response(body, { status: 400 }))),
        ProviderCreditError,
    );
});

Deno.test("HTTP 402 maps to ProviderCreditError", async () => {
    await assertRejects(
        () => collect(clientFor(() => new Response("{}", { status: 402 }))),
        ProviderCreditError,
    );
});

Deno.test("other HTTP failures map to ProviderError with the status only", async () => {
    const err = await assertRejects(
        () =>
            collect(
                clientFor(() =>
                    new Response('{"error":{"message":"secret prompt echo"}}', {
                        status: 529,
                    })
                ),
            ),
        ProviderError,
    );
    assertEquals(err.status, 529);
    assertEquals(err.message.includes("secret"), false);
});

Deno.test("an in-stream error event maps to ProviderError", async () => {
    const body =
        'event: error\ndata: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}\n\n';
    await assertRejects(
        () => collect(clientFor(() => new Response(sse(body)))),
        ProviderError,
    );
});

Deno.test("a stream that ends without message_stop is a provider failure", async () => {
    const cut = STREAM.split("event: message_stop")[0];
    await assertRejects(
        () => collect(clientFor(() => new Response(sse(cut)))),
        ProviderError,
    );
});

Deno.test("a hanging call aborts at the timeout with kind timeout", async () => {
    const client = createAnthropicClient({
        fetch: (_url, init) =>
            new Promise((_resolve, reject) => {
                init.signal?.addEventListener(
                    "abort",
                    () => reject(new DOMException("aborted", "AbortError")),
                );
            }),
        apiKey: "k",
        timeoutMs: 20,
    });
    const err = await assertRejects(() => collect(client), ProviderError);
    assertEquals(err.kind, "timeout");
});

// --- completeValidated ---

const textClient = (texts: string[]) => {
    let calls = 0;
    const client: AnthropicClient = {
        async *stream() {
            const text = texts[calls++];
            yield { type: "delta", text };
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 10, outputTokens: 5 },
            };
            yield { type: "done" };
        },
    };
    return { client, calls: () => calls };
};

const parseJson = (t: string) => {
    try {
        return JSON.parse(t) as unknown;
    } catch {
        return null;
    }
};

Deno.test("completeValidated returns the first valid output without retrying", async () => {
    const { client, calls } = textClient(['{"a":1}']);
    const r = await completeValidated(client, REQUEST, parseJson);
    assertEquals(r.value, { a: 1 });
    assertEquals(calls(), 1);
});

Deno.test("completeValidated retries once and sums the usage of both attempts", async () => {
    const { client, calls } = textClient(["nope", '{"a":2}']);
    const r = await completeValidated(client, REQUEST, parseJson);
    assertEquals(r.value, { a: 2 });
    assertEquals(calls(), 2);
    assertEquals(r.usage.inputTokens, 20);
});

Deno.test("completeValidated gives InvalidOutputError after the retry, with both usages", async () => {
    const { client, calls } = textClient(["nope", "still nope", "never"]);
    const err = await assertRejects(
        () => completeValidated(client, REQUEST, parseJson),
        InvalidOutputError,
    );
    assertEquals(calls(), 2);
    assertEquals(err.usage.outputTokens, 10);
});
