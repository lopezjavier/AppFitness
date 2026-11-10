import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import {
    GEMINI_MODEL,
    GeminiError,
    type GeminiErrorKind,
    generateContent,
    generateText,
    resolveModel,
} from "./gemini.ts";

const KEY = "test-key-123";
const OK = JSON.stringify({
    candidates: [{ content: { parts: [{ text: "hola" }] } }],
});

type Call = { url: string; init: RequestInit };

function fakeFetch(status: number, body: string, calls: Call[] = []) {
    return (url: string, init: RequestInit) => {
        calls.push({ url, init });
        return Promise.resolve(new Response(body, { status }));
    };
}

async function kindOf(promise: Promise<unknown>): Promise<GeminiErrorKind> {
    const err = await assertRejects(() => promise, GeminiError);
    return (err as GeminiError).kind;
}

const REQ = {
    system: "sys",
    user: "usr",
    temperature: 0.4,
    maxOutputTokens: 9,
};

Deno.test("generateContent sends the key only in a header and returns the text", async () => {
    const calls: Call[] = [];
    const text = await generateContent(
        { fetch: fakeFetch(200, OK, calls), apiKey: KEY },
        { a: 1 },
    );
    assertEquals(text, "hola");
    assert(calls[0].url.includes(GEMINI_MODEL));
    assert(!calls[0].url.includes(KEY));
    assert(!(calls[0].init.body as string).includes(KEY));
    assertEquals(
        (calls[0].init.headers as Record<string, string>)["x-goog-api-key"],
        KEY,
    );
    assertEquals(JSON.parse(calls[0].init.body as string), { a: 1 });
});

Deno.test("resolveModel uses a safe override, else the default", () => {
    assertEquals(resolveModel("gemini-x.1"), "gemini-x.1");
    assertEquals(resolveModel(undefined), GEMINI_MODEL);
    assertEquals(resolveModel("a/../b?key=1"), GEMINI_MODEL);
});

Deno.test("generateText builds one user turn with a system instruction and no tools", async () => {
    const calls: Call[] = [];
    await generateText({ fetch: fakeFetch(200, OK, calls), apiKey: KEY }, REQ);
    const body = JSON.parse(calls[0].init.body as string);
    assertEquals(body.systemInstruction.parts[0].text, "sys");
    assertEquals(body.contents, [{ role: "user", parts: [{ text: "usr" }] }]);
    assertEquals(body.generationConfig, {
        temperature: 0.4,
        maxOutputTokens: 9,
    });
    assertEquals(body.tools, undefined);
    assertEquals(body.toolConfig, undefined);
});

Deno.test("generateText sets the thinking level only when asked", async () => {
    const calls: Call[] = [];
    await generateText({ fetch: fakeFetch(200, OK, calls), apiKey: KEY }, REQ);
    await generateText({ fetch: fakeFetch(200, OK, calls), apiKey: KEY }, {
        ...REQ,
        thinkingLevel: "minimal",
    });
    const cfg = (i: number) =>
        JSON.parse(calls[i].init.body as string).generationConfig;
    assertEquals(cfg(0).thinkingConfig, undefined);
    assertEquals(cfg(1).thinkingConfig, { thinkingLevel: "minimal" });
});

Deno.test("thought parts are skipped", async () => {
    const body = JSON.stringify({
        candidates: [{
            content: {
                parts: [{ text: "thinking", thought: true }, { text: "real" }],
            },
        }],
    });
    assertEquals(
        await generateText({ fetch: fakeFetch(200, body), apiKey: KEY }, REQ),
        "real",
    );
});

Deno.test("a missing key never calls fetch", async () => {
    const calls: Call[] = [];
    for (const apiKey of [undefined, ""]) {
        assertEquals(
            await kindOf(
                generateText({ fetch: fakeFetch(200, OK, calls), apiKey }, REQ),
            ),
            "missing_key",
        );
    }
    assertEquals(calls.length, 0);
});

Deno.test("HTTP statuses map to error kinds", async () => {
    const cases: [number, GeminiErrorKind][] = [
        [429, "quota"],
        [401, "auth"],
        [403, "auth"],
        [404, "model"],
        [500, "provider"],
        [503, "provider"],
    ];
    for (const [status, kind] of cases) {
        assertEquals(
            await kindOf(
                generateText(
                    { fetch: fakeFetch(status, "{}"), apiKey: KEY },
                    REQ,
                ),
            ),
            kind,
        );
    }
});

Deno.test("a network failure is provider", async () => {
    const fetch = () => Promise.reject(new TypeError("boom"));
    assertEquals(
        await kindOf(generateText({ fetch, apiKey: KEY }, REQ)),
        "provider",
    );
});

Deno.test("empty candidates, no text part and unreadable body are provider", async () => {
    for (const body of ["{}", JSON.stringify({ candidates: [{}] }), "<html>"]) {
        assertEquals(
            await kindOf(
                generateText({ fetch: fakeFetch(200, body), apiKey: KEY }, REQ),
            ),
            "provider",
        );
    }
});

Deno.test("a timeout while waiting for the response is timeout", async () => {
    const fetch = (_u: string, init: RequestInit) =>
        new Promise<Response>((_res, rej) =>
            init.signal!.addEventListener(
                "abort",
                () => rej(new DOMException("aborted", "AbortError")),
            )
        );
    assertEquals(
        await kindOf(generateText({ fetch, apiKey: KEY, timeoutMs: 20 }, REQ)),
        "timeout",
    );
});

Deno.test("a timeout while reading the body is timeout", async () => {
    const fetch = (_u: string, init: RequestInit) => {
        const stream = new ReadableStream({
            start(c) {
                init.signal!.addEventListener(
                    "abort",
                    () => c.error(new DOMException("aborted", "AbortError")),
                );
            },
        });
        return Promise.resolve(new Response(stream, { status: 200 }));
    };
    assertEquals(
        await kindOf(generateText({ fetch, apiKey: KEY, timeoutMs: 20 }, REQ)),
        "timeout",
    );
});
