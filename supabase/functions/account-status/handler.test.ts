import { assertEquals, assertFalse } from "jsr:@std/assert@1.0.19";
import { createAccountStatusHandler } from "./handler.ts";
import { isProtected } from "../_shared/protected-emails.ts";

const URL_BASE = "http://localhost/functions/v1/account-status";
const SECRET = "mine@example.com, Other@Example.com";

function handler(email: string | null, secret: string | undefined = SECRET) {
    return createAccountStatusHandler({
        getUser: (token) =>
            Promise.resolve(
                token === "good-token" ? { id: "u1", email } : null,
            ),
        protectedEmails: () => secret,
    }, isProtected);
}

const get = (headers: Record<string, string> = {}) =>
    new Request(URL_BASE, { method: "GET", headers });
const AUTH = { authorization: "Bearer good-token" };

Deno.test("GET for a protected email returns protected true", async () => {
    const res = await handler("MINE@example.com")(get(AUTH));
    assertEquals(res.status, 200);
    assertEquals(await res.json(), { protected: true });
});

Deno.test("GET for an unlisted email returns protected false", async () => {
    const res = await handler("nobody@example.com")(get(AUTH));
    assertEquals(await res.json(), { protected: false });
});

Deno.test("GET without secret or without email returns protected false", async () => {
    const a = await handler("mine@example.com", "")(get(AUTH));
    assertEquals(await a.json(), { protected: false });
    const b = await handler(null)(get(AUTH));
    assertEquals(await b.json(), { protected: false });
});

Deno.test("GET without Authorization returns 401", async () => {
    const res = await handler("mine@example.com")(get());
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "unauthorized");
});

Deno.test("GET with an invalid token returns 401", async () => {
    const res = await handler("mine@example.com")(
        get({ authorization: "Bearer bad-token" }),
    );
    assertEquals(res.status, 401);
    await res.body?.cancel();
});

for (const method of ["POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"]) {
    Deno.test(`${method} returns 405 with Allow: GET`, async () => {
        const res = await handler("mine@example.com")(
            new Request(URL_BASE, { method, headers: AUTH }),
        );
        await res.body?.cancel();
        assertEquals(res.status, 405);
        assertEquals(res.headers.get("allow"), "GET");
    });
}

Deno.test("response is no-store and never contains the email list", async () => {
    const res = await handler("mine@example.com")(get(AUTH));
    assertEquals(res.headers.get("cache-control"), "no-store");
    const text = await res.text();
    assertFalse(text.toLowerCase().includes("example.com"));
});
