import {
    assert,
    assertEquals,
    assertFalse,
    assertStringIncludes,
} from "jsr:@std/assert@1.0.19";
import { createHealthHandler } from "./handler.ts";

const URL_BASE = "http://localhost/functions/v1/health";

function fakePing(fail?: unknown) {
    const state = { calls: 0 };
    const ping = () => {
        state.calls++;
        return fail === undefined ? Promise.resolve() : Promise.reject(fail);
    };
    return { state, ping };
}

/** Runs `fn` with console.error stubbed; returns the arguments it was given. */
async function withSilencedConsoleError(
    fn: () => Promise<void>,
): Promise<unknown[][]> {
    const original = console.error;
    const calls: unknown[][] = [];
    console.error = (...args: unknown[]) => {
        calls.push(args);
    };
    try {
        await fn();
    } finally {
        console.error = original;
    }
    return calls;
}

Deno.test('GET with ping ok returns 200 and exact {"status":"ok"} body', async () => {
    const { ping } = fakePing();
    const handler = createHealthHandler({ ping });

    const res1 = await handler(new Request(URL_BASE));
    assertEquals(res1.status, 200);
    assertEquals(await res1.json(), { status: "ok" });

    const res2 = await handler(new Request(URL_BASE));
    assertEquals(await res2.text(), '{"status":"ok"}');
});

Deno.test("GET returns JSON content-type and cache-control no-store", async () => {
    const { ping } = fakePing();
    const res = await createHealthHandler({ ping })(new Request(URL_BASE));
    await res.body?.cancel();
    assert(
        res.headers.get("content-type")?.startsWith("application/json"),
        `unexpected content-type: ${res.headers.get("content-type")}`,
    );
    assertEquals(res.headers.get("cache-control"), "no-store");
});

Deno.test("GET calls ping exactly once", async () => {
    const { state, ping } = fakePing();
    const res = await createHealthHandler({ ping })(new Request(URL_BASE));
    await res.body?.cancel();
    assertEquals(state.calls, 1);
});

Deno.test("GET with sub-path and query string returns 200", async () => {
    const { ping } = fakePing();
    const res = await createHealthHandler({ ping })(
        new Request(`${URL_BASE}/x?a=1`),
    );
    assertEquals(res.status, 200);
    assertEquals(await res.json(), { status: "ok" });
});

for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
    Deno.test(`${method} returns 405 with Allow: GET and does not ping`, async () => {
        const { state, ping } = fakePing();
        const res = await createHealthHandler({ ping })(
            new Request(URL_BASE, { method }),
        );
        assertEquals(res.status, 405);
        assertEquals(res.headers.get("allow"), "GET");
        assert(res.headers.get("content-type")?.startsWith("application/json"));
        const body = await res.json();
        assertEquals(body.error.code, "method_not_allowed");
        assertEquals(typeof body.error.message, "string");
        assertEquals(state.calls, 0);
    });
}

Deno.test("HEAD returns 405 with Allow: GET and does not ping", async () => {
    const { state, ping } = fakePing();
    const res = await createHealthHandler({ ping })(
        new Request(URL_BASE, { method: "HEAD" }),
    );
    await res.body?.cancel();
    assertEquals(res.status, 405);
    assertEquals(res.headers.get("allow"), "GET");
    assertEquals(state.calls, 0);
});

Deno.test("OPTIONS returns 405 with Allow: GET and no CORS headers", async () => {
    const { state, ping } = fakePing();
    const res = await createHealthHandler({ ping })(
        new Request(URL_BASE, { method: "OPTIONS" }),
    );
    await res.body?.cancel();
    assertEquals(res.status, 405);
    assertEquals(res.headers.get("allow"), "GET");
    for (const [name] of res.headers) {
        assertFalse(name.startsWith("access-control-"), `unexpected ${name}`);
    }
    assertEquals(state.calls, 0);
});

Deno.test("GET with failing ping returns 503 without leaking the error", async () => {
    const { state, ping } = fakePing(new Error("password=secret host=db"));
    let status = 0;
    let text = "";
    const logged = await withSilencedConsoleError(async () => {
        const res = await createHealthHandler({ ping })(new Request(URL_BASE));
        status = res.status;
        text = await res.text();
    });

    assertEquals(status, 503);
    assertEquals(state.calls, 1);
    const body = JSON.parse(text);
    assertEquals(body.error.code, "db_unavailable");
    assertFalse(text.includes("secret"));
    assertFalse(text.includes("host="));

    assertEquals(logged.length, 1);
    const loggedText = logged[0].map(String).join(" ");
    assertStringIncludes(loggedText, "health: db ping failed");
    assertFalse(loggedText.includes("secret"));
    assertFalse(loggedText.includes("host="));
});

Deno.test("GET with ping throwing a non-Error value returns 503", async () => {
    const { ping } = fakePing("boom");
    let status = 0;
    let body: { error?: { code?: string } } = {};
    await withSilencedConsoleError(async () => {
        const res = await createHealthHandler({ ping })(new Request(URL_BASE));
        status = res.status;
        body = await res.json();
    });
    assertEquals(status, 503);
    assertEquals(body.error?.code, "db_unavailable");
});
