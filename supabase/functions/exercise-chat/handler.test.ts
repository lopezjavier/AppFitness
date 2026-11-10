import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { generateText } from "../_shared/gemini.ts";
import { GeminiError, type GeminiErrorKind } from "../_shared/gemini.ts";
import {
    createExerciseChatHandler,
    type ExerciseChatDeps,
    MAX_BODY_CHARS,
} from "./handler.ts";

const URL_ = "http://localhost/functions/v1/exercise-chat";
const SECRET = "SENTINEL-question-text";
const KEY = "SENTINEL-api-key";

// CI never touches the network: the real fetch must not be reachable from any test here.
globalThis.fetch = () => {
    throw new Error("real fetch called");
};

const payload = (question = "¿Cómo coloco los codos?") => ({
    exercise: {
        name: "Press banca",
        muscles: ["Pecho", "Tríceps"],
        instructions: "Baja la barra al pecho.",
    },
    question,
});

function call(
    handler: (r: Request) => Promise<Response>,
    body: unknown,
    token: string | null = "ok",
    method = "POST",
) {
    return handler(
        new Request(URL_, {
            method,
            headers: token ? { authorization: `Bearer ${token}` } : {},
            body: method === "GET"
                ? undefined
                : typeof body === "string"
                ? body
                : JSON.stringify(body),
        }),
    );
}

function make(generate: ExerciseChatDeps["generate"]) {
    return createExerciseChatHandler({
        authenticate: (t) => Promise.resolve(t === "ok" ? "user-a" : null),
        generate,
    });
}

const ok = (text = "Respuesta") => make(() => Promise.resolve(text));
const failing = (err: unknown) => make(() => Promise.reject(err));

Deno.test("GET is 405", async () => {
    const res = await call(ok(), null, "ok", "GET");
    assertEquals(res.status, 405);
});

Deno.test("no token and invalid token are 401 and never reach generate", async () => {
    let calls = 0;
    const h = make(() => {
        calls++;
        return Promise.resolve("x");
    });
    assertEquals((await call(h, payload(), null)).status, 401);
    const res = await call(h, payload(), "bad");
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "UNAUTHORIZED");
    assertEquals(calls, 0);
});

Deno.test("happy path returns the trimmed answer, no-store", async () => {
    const res = await call(ok("  Hola entrenador \n"), payload());
    assertEquals(res.status, 200);
    assertEquals(res.headers.get("cache-control"), "no-store");
    assertEquals(await res.json(), { answer: "Hola entrenador" });
});

Deno.test("an over-long answer is truncated, not failed", async () => {
    const res = await call(ok("a".repeat(9000)), payload());
    assertEquals(res.status, 200);
    assertEquals((await res.json()).answer.length, 4000);
});

Deno.test("generate gets the fixed prompt and only the normalized exercise and question", async () => {
    let seen: Parameters<ExerciseChatDeps["generate"]>[0] | undefined;
    const h = make((r) => {
        seen = r;
        return Promise.resolve("ok");
    });
    await call(h, {
        exercise: { name: "  Remo ", muscles: [" Espalda "], instructions: "" },
        question: "  ¿Y la espalda? ",
    });
    assert(seen);
    assert(seen.user.includes('"name":"Remo"'));
    assert(seen.user.includes('"muscles":["Espalda"]'));
    assert(seen.user.includes('"¿Y la espalda?"'));
    assert(!seen.system.includes("Remo"));
});

Deno.test("through the real transport the outgoing body has one user turn and no tools", async () => {
    type Body = {
        contents: { role: string; parts: unknown[] }[];
        tools?: unknown;
        toolConfig?: unknown;
        generationConfig: { thinkingConfig?: unknown };
    };
    const sent: { url: string; headers: Record<string, string>; body: Body }[] =
        [];
    const h = make((req) =>
        generateText({
            apiKey: KEY,
            fetch: (url, init) => {
                sent.push({
                    url,
                    headers: init.headers as Record<string, string>,
                    body: JSON.parse(init.body as string),
                });
                return Promise.resolve(
                    new Response(
                        JSON.stringify({
                            candidates: [{
                                content: { parts: [{ text: "Hola" }] },
                            }],
                        }),
                        { status: 200 },
                    ),
                );
            },
        }, req)
    );
    const res = await call(h, payload());
    assertEquals(await res.json(), { answer: "Hola" });
    assertEquals(sent.length, 1);
    const { body, headers, url } = sent[0];
    assertEquals(Object.keys(body).sort(), [
        "contents",
        "generationConfig",
        "systemInstruction",
    ]);
    assertEquals(body.contents.length, 1);
    assertEquals(body.contents[0].role, "user");
    assertEquals(body.contents[0].parts.length, 1);
    assertEquals(body.generationConfig.thinkingConfig, {
        thinkingLevel: "minimal",
    });
    assertEquals(body.tools, undefined);
    assertEquals(body.toolConfig, undefined);
    const json = JSON.stringify(body);
    assert(!json.includes("inlineData"));
    assert(!json.includes("user-a"));
    assert(!json.includes(KEY));
    assert(!url.includes(KEY));
    assertEquals(headers["x-goog-api-key"], KEY);
});

Deno.test("Gemini errors map to their own codes", async () => {
    const cases: [GeminiErrorKind, number, string][] = [
        ["quota", 429, "QUOTA_EXCEEDED"],
        ["provider", 502, "PROVIDER_ERROR"],
        ["timeout", 504, "PROVIDER_TIMEOUT"],
        ["auth", 503, "NOT_CONFIGURED"],
        ["missing_key", 503, "NOT_CONFIGURED"],
        ["model", 502, "PROVIDER_ERROR"],
    ];
    for (const [kind, status, code] of cases) {
        const res = await call(failing(new GeminiError(kind)), payload());
        assertEquals(res.status, status, kind);
        assertEquals((await res.json()).error.code, code, kind);
    }
});

Deno.test("a blank answer is 502", async () => {
    const res = await call(ok("  \n "), payload());
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "PROVIDER_ERROR");
});

Deno.test("an unexpected throw is 500 INTERNAL", async () => {
    const res = await call(failing(new TypeError("x")), payload());
    assertEquals(res.status, 500);
    assertEquals((await res.json()).error.code, "INTERNAL");
});

Deno.test("bad JSON, non-object and invalid fields are 400", async () => {
    assertEquals((await call(ok(), "{nope")).status, 400);
    assertEquals((await call(ok(), "[]")).status, 400);
    assertEquals((await call(ok(), { ...payload(), history: [] })).status, 400);
    assertEquals((await call(ok(), payload("   "))).status, 400);
    const res = await call(ok(), { question: "x" });
    assertEquals((await res.json()).error.code, "INVALID_INPUT");
});

Deno.test("an oversized body is 413 before parsing", async () => {
    const big = "x".repeat(MAX_BODY_CHARS + 1);
    const res = await call(ok(), big);
    assertEquals(res.status, 413);
    assertEquals((await res.json()).error.code, "PAYLOAD_TOO_LARGE");
});

Deno.test("nothing sensitive is logged or returned, on success or any error", async () => {
    const captured: string[] = [];
    const orig = {
        log: console.log,
        error: console.error,
        warn: console.warn,
        info: console.info,
    };
    const spy = (...a: unknown[]) => {
        captured.push(a.map(String).join(" "));
    };
    console.log =
        console.error =
        console.warn =
        console.info =
            spy;
    const bodies: string[] = [];
    try {
        const body = {
            exercise: {
                name: SECRET,
                muscles: [SECRET],
                instructions: SECRET,
            },
            question: SECRET,
        };
        const handlers = [
            make(() => Promise.resolve(`${SECRET} answer`)),
            ...([
                "quota",
                "provider",
                "timeout",
                "auth",
                "missing_key",
                "model",
            ] as const)
                .map((k) => failing(new GeminiError(k, 500))),
            failing(new Error(SECRET)),
            make(() => Promise.resolve("")),
        ];
        for (const h of handlers) {
            bodies.push(await (await call(h, body)).text());
        }
        bodies.push(await (await call(ok(), `{"question":"${SECRET}`)).text());
    } finally {
        Object.assign(console, orig);
    }
    // Only the success body may echo the answer; error bodies must not carry the sentinel.
    for (const b of bodies.slice(1)) {
        assert(!b.includes(SECRET));
        assert(!b.includes(KEY));
    }
    for (const line of captured) {
        assert(!line.includes(SECRET), line);
        assert(!line.includes(KEY), line);
    }
    assert(captured.length > 0);
});
