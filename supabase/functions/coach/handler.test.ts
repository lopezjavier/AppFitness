import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { CoachInputError } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { KINDS, type KindSpec } from "./router.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const SECRET_INPUT_WORD = "SECRET-INPUT-TEXT";

/** A deterministic kind that echoes what it was given, so tests need no summary or database. */
const echo: KindSpec = {
    prepare: (input) =>
        Promise.resolve({
            finalize: () => Promise.resolve({ ok: true, input }),
        }),
};

function setup(
    overrides: Partial<CoachDeps> = {},
    kinds: Record<string, KindSpec> = { echo },
) {
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        loadContext: () => Promise.reject(new Error("unexpected loadContext")),
        loadInputs: () => Promise.reject(new Error("unexpected loadInputs")),
        savePlan: () => Promise.reject(new Error("unexpected savePlan")),
        loadCatalog: () => Promise.reject(new Error("unexpected loadCatalog")),
        loadPlan: () => Promise.reject(new Error("unexpected loadPlan")),
        attachBlock: () => Promise.reject(new Error("unexpected attachBlock")),
        loadPhaseHistory: () =>
            Promise.reject(new Error("unexpected loadPhaseHistory")),
        updatePlanOptions: () =>
            Promise.reject(new Error("unexpected updatePlanOptions")),
        setDietBreak: () =>
            Promise.reject(new Error("unexpected setDietBreak")),
        loadReview: () => Promise.reject(new Error("unexpected loadReview")),
        applyPhaseChange: () =>
            Promise.reject(new Error("unexpected applyPhaseChange")),
        applyGoalSwitch: () =>
            Promise.reject(new Error("unexpected applyGoalSwitch")),
        now: () => NOW,
        kinds,
        ...overrides,
    };
    return { handler: createCoachHandler(deps) };
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

const call = { kind: "echo", input: { a: 1 } };

Deno.test("GET is 405", async () => {
    const { handler } = setup();
    assertEquals((await handler(post(call, "good", "GET"))).status, 405);
});

Deno.test("no bearer is 401 UNAUTHORIZED", async () => {
    const { handler } = setup();
    const res = await handler(post(call, null));
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "UNAUTHORIZED");
});

Deno.test("an invalid token is 401", async () => {
    const { handler } = setup();
    assertEquals((await handler(post(call, "bad"))).status, 401);
});

Deno.test("an unknown kind is 400 INVALID_INPUT", async () => {
    const { handler } = setup();
    const res = await handler(post({ kind: "nope", input: {} }));
    assertEquals(res.status, 400);
    assertEquals((await res.json()).error.code, "INVALID_INPUT");
});

Deno.test("T-198: the removed intake_extract kind is an unknown kind", async () => {
    const { handler } = setup({}, KINDS);
    const res = await handler(
        post({ kind: "intake_extract", input: { text: "hola" } }),
    );
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

Deno.test("ADR-103: ping, report and chat answer the unknown-kind error", async () => {
    const { handler } = setup({}, KINDS);
    for (const kind of ["ping", "report", "chat"]) {
        const res = await handler(post({ kind, input: {} }));
        assertEquals(res.status, 400);
        const error = (await res.json()).error;
        assertEquals(error.code, "INVALID_INPUT");
        assertEquals(error.message, "Unknown kind");
    }
});

Deno.test("the removed block kinds answer the unknown-kind error, not a 5xx", async () => {
    const { handler } = setup({}, KINDS);
    for (const kind of ["block_splits", "plan_block", "folder_review"]) {
        const res = await handler(post({ kind, input: {} }));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.message, "Unknown kind");
    }
});

Deno.test("the callable kinds are the five deterministic plan kinds", () => {
    assertEquals(Object.keys(KINDS).sort(), [
        "plan_diet_break",
        "plan_goal_switch",
        "plan_nutrition",
        "plan_phase_change",
        "plan_recompute",
    ]);
});

Deno.test("a kind answers one delta then done with zero usage", async () => {
    const { handler } = setup();
    const res = await handler(post(call));
    assertEquals(res.status, 200);
    assertEquals(res.headers.get("content-type"), "text/event-stream");
    assertEquals(
        await res.text(),
        'event: delta\ndata: {"text":"{\\"ok\\":true,\\"input\\":{\\"a\\":1}}"}\n\n' +
            'event: done\ndata: {"usage":{"input_tokens":0,"output_tokens":0,"cost_eur":0}}\n\n',
    );
});

Deno.test("a body model of any value is ignored and the answer equals the one without it", async () => {
    const { handler } = setup();
    const plain = await (await handler(post(call))).text();
    for (const model of ["opus", "gpt", 123]) {
        const res = await handler(post({ ...call, model }));
        assertEquals(res.status, 200);
        assertEquals(await res.text(), plain);
    }
});

Deno.test("a refusal the client can fix is its JSON error", async () => {
    const { handler } = setup({}, {
        echo: {
            prepare: () =>
                Promise.resolve({
                    finalize: () =>
                        Promise.reject(
                            new CoachInputError(
                                "coach_block_in_progress",
                                "A block is in progress",
                            ),
                        ),
                }),
        },
    });
    const res = await handler(post(call));
    assertEquals(res.status, 409);
    assertEquals((await res.json()).error.code, "coach_block_in_progress");
});

Deno.test("a failure while finalizing is 500 INTERNAL", async () => {
    const { handler } = setup({}, {
        echo: {
            prepare: () =>
                Promise.resolve({
                    finalize: () => Promise.reject(new Error("db down")),
                }),
        },
    });
    const res = await handler(post(call));
    assertEquals(res.status, 500);
    assertEquals((await res.json()).error.code, "INTERNAL");
});

Deno.test("inputs never reach the console", async () => {
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
        const ok = setup();
        await (await ok.handler(
            post({ kind: "echo", input: { x: SECRET_INPUT_WORD } }),
        )).text();
        const failing = setup({}, {
            echo: {
                prepare: () =>
                    Promise.resolve({
                        finalize: () =>
                            Promise.reject(new Error(SECRET_INPUT_WORD)),
                    }),
            },
        });
        await failing.handler(
            post({ kind: "echo", input: { x: SECRET_INPUT_WORD } }),
        );
    } finally {
        Object.assign(console, originals);
    }
    assertEquals(seen.filter((l) => l.includes(SECRET_INPUT_WORD)), []);
});

Deno.test("config.toml enables verify_jwt for coach", async () => {
    const toml = await Deno.readTextFile(
        new URL("../../config.toml", import.meta.url),
    );
    assert(/\[functions\.coach\]\s*\nverify_jwt = true/.test(toml));
});
