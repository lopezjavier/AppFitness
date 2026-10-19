import { assert, assertEquals, assertFalse } from "jsr:@std/assert@1.0.19";
import { createDeleteAccountHandler } from "./handler.ts";

const URL_BASE = "http://localhost/functions/v1/delete-account";
const TOKEN_USER = "11111111-1111-4111-8111-111111111111";
const OTHER_USER = "22222222-2222-4222-8222-222222222222";

type Fake = {
    resolved: string[];
    deleted: string[];
    deps: Parameters<typeof createDeleteAccountHandler>[0];
};

/** Fake auth backend: only "good-token" resolves, to TOKEN_USER. */
function fake(
    opts: { deleteResult?: "deleted" | "not_found" | Error } = {},
): Fake {
    const resolved: string[] = [];
    const deleted: string[] = [];
    return {
        resolved,
        deleted,
        deps: {
            getUserId: (token) => {
                resolved.push(token);
                return Promise.resolve(
                    token === "good-token" ? TOKEN_USER : null,
                );
            },
            deleteUser: (id) => {
                deleted.push(id);
                const r = opts.deleteResult ?? "deleted";
                return r instanceof Error
                    ? Promise.reject(r)
                    : Promise.resolve(r);
            },
        },
    };
}

function post(headers: Record<string, string> = {}, body?: unknown): Request {
    return new Request(URL_BASE, {
        method: "POST",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });
}

const AUTH = { authorization: "Bearer good-token" };

for (const method of ["GET", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"]) {
    Deno.test(`${method} returns 405 with Allow: POST and deletes nothing`, async () => {
        const f = fake();
        const res = await createDeleteAccountHandler(f.deps)(
            new Request(URL_BASE, { method, headers: AUTH }),
        );
        await res.body?.cancel();
        assertEquals(res.status, 405);
        assertEquals(res.headers.get("allow"), "POST");
        assertEquals(f.deleted, []);
    });
}

Deno.test("POST without Authorization returns 401 unauthorized and deletes nothing", async () => {
    const f = fake();
    const res = await createDeleteAccountHandler(f.deps)(post());
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "unauthorized");
    assertEquals(f.deleted, []);
});

Deno.test("POST with an invalid token returns 401 unauthorized and deletes nothing", async () => {
    const f = fake();
    const res = await createDeleteAccountHandler(f.deps)(
        post({ authorization: "Bearer bad-token" }, { user_id: OTHER_USER }),
    );
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "unauthorized");
    assertEquals(f.deleted, []);
});

Deno.test("POST for a user already gone returns 401 unauthorized", async () => {
    const f = fake({ deleteResult: "not_found" });
    const res = await createDeleteAccountHandler(f.deps)(post(AUTH));
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "unauthorized");
});

Deno.test("POST deletes exactly the token's user and returns 200 deleted", async () => {
    const f = fake();
    const res = await createDeleteAccountHandler(f.deps)(post(AUTH));
    assertEquals(res.status, 200);
    assertEquals(await res.text(), '{"status":"deleted"}');
    assertEquals(f.resolved, ["good-token"]);
    assertEquals(f.deleted, [TOKEN_USER]);
});

Deno.test("POST ignores a user_id in the body", async () => {
    const f = fake();
    const res = await createDeleteAccountHandler(f.deps)(
        post(AUTH, { user_id: OTHER_USER, id: OTHER_USER }),
    );
    await res.body?.cancel();
    assertEquals(f.deleted, [TOKEN_USER]);
});

Deno.test("POST with a failing delete returns 500 delete_failed without leaking details", async () => {
    const f = fake({ deleteResult: new Error("password=secret host=db") });
    const original = console.error;
    const logged: string[] = [];
    console.error = (...a: unknown[]) => logged.push(a.map(String).join(" "));
    let text = "";
    let status = 0;
    try {
        const res = await createDeleteAccountHandler(f.deps)(post(AUTH));
        status = res.status;
        text = await res.text();
    } finally {
        console.error = original;
    }
    assertEquals(status, 500);
    assertEquals(JSON.parse(text).error.code, "delete_failed");
    assertFalse(text.includes("secret"));
    assert(logged.length > 0);
    assertFalse(logged.join(" ").includes("secret"));
    assertFalse(logged.join(" ").includes(TOKEN_USER));
});
