import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    type AnthropicClient,
    type AnthropicEvent,
    NO_USAGE,
    ProviderCreditError,
    ProviderError,
} from "../_shared/anthropic.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { KINDS } from "./router.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const SECRET_PROMPT_WORD = "SECRET-INPUT-TEXT";

type Recorded = Parameters<CoachDeps["recordUsage"]>[0];

function setup(
    overrides: Partial<CoachDeps> & {
        events?: AnthropicEvent[] | (() => AsyncGenerator<AnthropicEvent>);
        spent?: number;
        globalTotal?: number;
    } = {},
) {
    const records: Recorded[] = [];
    let clientCalls = 0;
    const events: AnthropicEvent[] = Array.isArray(overrides.events)
        ? overrides.events
        : [
            { type: "delta", text: "ok" },
            {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 1000, outputTokens: 5 },
            },
            { type: "done" },
        ];
    const makeStream = typeof overrides.events === "function"
        ? overrides.events
        : async function* () {
            for (const e of events) yield e;
        };
    const client: AnthropicClient = {
        stream: () => {
            clientCalls++;
            return makeStream();
        },
    };
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        userSpent: () => Promise.resolve(overrides.spent ?? 0),
        globalSpent: () => Promise.resolve(overrides.globalTotal ?? 0),
        recordUsage: (r) => {
            records.push(r);
            return Promise.resolve();
        },
        client,
        now: () => NOW,
        ...overrides,
    };
    delete (deps as Record<string, unknown>).events;
    return {
        handler: createCoachHandler(deps),
        records,
        clientCalls: () => clientCalls,
    };
}

const post = (
    body: unknown,
    token: string | null = "good",
    method = "POST",
) => new Request("http://x/coach", {
    method,
    headers: token ? { authorization: `Bearer ${token}` } : {},
    body: method === "GET" ? undefined : JSON.stringify(body),
});

const ping = { kind: "ping", input: {} };

Deno.test("GET is 405", async () => {
    const { handler } = setup();
    assertEquals((await handler(post(ping, "good", "GET"))).status, 405);
});

Deno.test("no bearer is 401 UNAUTHORIZED", async () => {
    const { handler } = setup();
    const res = await handler(post(ping, null));
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "UNAUTHORIZED");
});

Deno.test("an invalid token is 401", async () => {
    const { handler } = setup();
    assertEquals((await handler(post(ping, "bad"))).status, 401);
});

Deno.test("an unknown kind is 400 INVALID_INPUT", async () => {
    const { handler } = setup();
    const res = await handler(post({ kind: "nope", input: {} }));
    assertEquals(res.status, 400);
    assertEquals((await res.json()).error.code, "INVALID_INPUT");
});

Deno.test("a prototype key is not a kind", async () => {
    const { handler } = setup();
    const res = await handler(post({ kind: "constructor", input: {} }));
    assertEquals(res.status, 400);
});

Deno.test("a non-JSON body is 400", async () => {
    const { handler } = setup();
    const req = new Request("http://x/coach", {
        method: "POST",
        headers: { authorization: "Bearer good" },
        body: "{oops",
    });
    assertEquals((await handler(req)).status, 400);
});

Deno.test("skeleton kinds answer 501 coach_kind_not_available without calling the model", async () => {
    for (const kind of ["plan_nutrition", "plan_block", "report", "chat"]) {
        const { handler, clientCalls } = setup();
        const res = await handler(post({ kind, input: {} }));
        assertEquals(res.status, 501);
        assertEquals((await res.json()).error.code, "coach_kind_not_available");
        assertEquals(clientCalls(), 0);
    }
});

Deno.test("plan kinds use Opus and report/chat use Sonnet", () => {
    assertEquals(KINDS.plan_nutrition.model, "opus");
    assertEquals(KINDS.plan_block.model, "opus");
    assertEquals(KINDS.report.model, "sonnet");
    assertEquals(KINDS.chat.model, "sonnet");
    assertEquals(KINDS.ping.model, "sonnet");
});

Deno.test("over the user limit is 429 scope user and the model is not called", async () => {
    const { handler, clientCalls } = setup({ spent: 2 });
    const res = await handler(post(ping));
    assertEquals(res.status, 429);
    const error = (await res.json()).error;
    assertEquals(error.code, "coach_limit_reached");
    assertEquals(error.scope, "user");
    assertEquals(clientCalls(), 0);
});

Deno.test("over the global limit is 429 scope global while the user is under", async () => {
    const { handler, clientCalls } = setup({ spent: 0.1, globalTotal: 5 });
    const res = await handler(post(ping));
    assertEquals(res.status, 429);
    assertEquals((await res.json()).error.scope, "global");
    assertEquals(clientCalls(), 0);
});

Deno.test("a missing provider key is 503 NOT_CONFIGURED", async () => {
    const { handler } = setup({ client: null });
    const res = await handler(post(ping));
    assertEquals(res.status, 503);
    assertEquals((await res.json()).error.code, "NOT_CONFIGURED");
});

Deno.test("a successful ping streams delta then done and records the returned usage", async () => {
    const { handler, records } = setup();
    const res = await handler(post(ping));
    assertEquals(res.status, 200);
    assertEquals(res.headers.get("content-type"), "text/event-stream");
    const text = await res.text();
    assertEquals(
        text,
        'event: delta\ndata: {"text":"ok"}\n\n' +
            'event: done\ndata: {"usage":{"input_tokens":1000,"output_tokens":5,"cost_eur":0.002050}}\n\n'
                .replace("0.002050", "0.00205"),
    );
    assertEquals(records, [{
        userId: "user-1",
        month: "2026-10-01",
        inputTokens: 1000,
        outputTokens: 5,
        costEur: 0.00205,
    }]);
});

Deno.test("credit rejection is 402 coach_no_credit, one call, nothing recorded", async () => {
    const { handler, records, clientCalls } = setup({
        // deno-lint-ignore require-yield
        events: async function* () {
            throw new ProviderCreditError();
        },
    });
    const res = await handler(post(ping));
    assertEquals(res.status, 402);
    assertEquals((await res.json()).error.code, "coach_no_credit");
    assertEquals(clientCalls(), 1);
    assertEquals(records.length, 0);
});

Deno.test("another provider failure is 502 PROVIDER_ERROR and records nothing", async () => {
    const { handler, records } = setup({
        // deno-lint-ignore require-yield
        events: async function* () {
            throw new ProviderError("provider", 529);
        },
    });
    const res = await handler(post(ping));
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "PROVIDER_ERROR");
    assertEquals(records.length, 0);
});

Deno.test("a call that never answers is 504 coach_timeout and records nothing", async () => {
    const { handler, records } = setup({
        timeoutMs: 20,
        // deno-lint-ignore require-yield
        events: async function* () {
            await new Promise(() => {}); // never resolves
        },
    });
    const res = await handler(post(ping));
    assertEquals(res.status, 504);
    assertEquals((await res.json()).error.code, "coach_timeout");
    assertEquals(records.length, 0);
});

Deno.test("a failure after streaming started becomes an error event and records nothing", async () => {
    const { handler, records } = setup({
        events: async function* () {
            yield { type: "delta", text: "par" } as AnthropicEvent;
            throw new ProviderError("provider");
        },
    });
    const res = await handler(post(ping));
    assertEquals(res.status, 200);
    const text = await res.text();
    assert(text.includes('event: delta\ndata: {"text":"par"}'));
    assert(text.endsWith('event: error\ndata: {"code":"PROVIDER_ERROR"}\n\n'));
    assertEquals(records.length, 0);
});

// --- validated kinds (injected, since no real kind validates yet) ---

const validatedKinds = (texts: string[]) => {
    let call = 0;
    const kinds = {
        v: {
            model: "opus" as const,
            available: true,
            buildRequest: () => ({ model: "m", prompt: "p", maxTokens: 10 }),
            validate: (t: string) => t.startsWith("{") ? t : null,
        },
    };
    const events = async function* () {
        yield { type: "delta", text: texts[call++] } as AnthropicEvent;
        yield {
            type: "usage",
            usage: { ...NO_USAGE, inputTokens: 100, outputTokens: 10 },
        } as AnthropicEvent;
        yield { type: "done" } as AnthropicEvent;
    };
    return { kinds, events, calls: () => call };
};

Deno.test("an invalid output is retried once and both attempts are recorded", async () => {
    const v = validatedKinds(["bad", "{}"]);
    const { handler, records } = setup({ kinds: v.kinds, events: v.events });
    const res = await handler(post({ kind: "v", input: {} }));
    assertEquals(res.status, 200);
    const text = await res.text();
    assert(text.includes('event: delta\ndata: {"text":"{}"}'));
    assertEquals(v.calls(), 2);
    assertEquals(records.length, 1);
    assertEquals(records[0].inputTokens, 200);
    assertEquals(records[0].outputTokens, 20);
});

Deno.test("two invalid outputs give 502 coach_invalid_output and still record the spent usage", async () => {
    const v = validatedKinds(["bad", "worse"]);
    const { handler, records } = setup({ kinds: v.kinds, events: v.events });
    const res = await handler(post({ kind: "v", input: {} }));
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(v.calls(), 2);
    assertEquals(records.length, 1);
    assertEquals(records[0].inputTokens, 200);
});

// --- privacy and config ---

Deno.test("prompts, inputs and outputs never reach the console", async () => {
    const seen: string[] = [];
    const originals = {
        log: console.log,
        error: console.error,
        warn: console.warn,
        info: console.info,
    };
    for (const k of Object.keys(originals) as (keyof typeof originals)[]) {
        console[k] = (...a: unknown[]) => void seen.push(a.join(" "));
    }
    try {
        const ok = setup({
            events: [
                { type: "delta", text: SECRET_PROMPT_WORD },
                { type: "usage", usage: NO_USAGE },
                { type: "done" },
            ],
        });
        await (await ok.handler(
            post({ kind: "ping", input: { x: SECRET_PROMPT_WORD } }),
        ))
            .text();
        const failing = setup({
            recordUsage: () => Promise.reject(new Error(SECRET_PROMPT_WORD)),
        });
        await (await failing.handler(post(ping))).text();
        const credit = setup({
            // deno-lint-ignore require-yield
            events: async function* () {
                throw new ProviderCreditError();
            },
        });
        await credit.handler(
            post({ kind: "ping", input: { x: SECRET_PROMPT_WORD } }),
        );
    } finally {
        Object.assign(console, originals);
    }
    assertEquals(seen.filter((l) => l.includes(SECRET_PROMPT_WORD)), []);
});

Deno.test("config.toml enables verify_jwt for coach", async () => {
    const toml = await Deno.readTextFile(
        new URL("../../config.toml", import.meta.url),
    );
    assert(/\[functions\.coach\]\s*\nverify_jwt = true/.test(toml));
});

Deno.test("a mid-stream failure after a usage event still records that usage", async () => {
    const { handler, records } = setup({
        events: async function* () {
            yield { type: "delta", text: "par" } as AnthropicEvent;
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 500, outputTokens: 9 },
            } as AnthropicEvent;
            throw new ProviderError("provider");
        },
    });
    const text = await (await handler(post(ping))).text();
    assert(text.includes("event: error"));
    assertEquals(records.length, 1);
    assertEquals(records[0].inputTokens, 500);
    assertEquals(records[0].outputTokens, 9);
});

Deno.test("a credit rejection after a usage event records nothing", async () => {
    const { handler, records } = setup({
        events: async function* () {
            yield { type: "delta", text: "x" } as AnthropicEvent;
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 5 },
            } as AnthropicEvent;
            throw new ProviderCreditError();
        },
    });
    await (await handler(post(ping))).text();
    assertEquals(records.length, 0);
});
