import { assertEquals } from "jsr:@std/assert@1.0.19";
import { errorResponse, methodNotAllowed } from "./errors.ts";

Deno.test("methodNotAllowed defaults to GET", async () => {
    const res = methodNotAllowed();
    assertEquals(res.status, 405);
    assertEquals(res.headers.get("allow"), "GET");
    assertEquals((await res.json()).error.message, "Only GET is allowed");
});

Deno.test("methodNotAllowed accepts another allowed method", () => {
    const res = methodNotAllowed("POST");
    assertEquals(res.status, 405);
    assertEquals(res.headers.get("allow"), "POST");
});

Deno.test("photo error codes map to their HTTP status", () => {
    const expected = {
        PAYLOAD_TOO_LARGE: 413,
        NO_FOOD_RECOGNIZED: 422,
        QUOTA_EXCEEDED: 429,
        PROVIDER_ERROR: 502,
        NOT_CONFIGURED: 503,
    } as const;
    for (const [code, status] of Object.entries(expected)) {
        assertEquals(
            errorResponse(code as keyof typeof expected, "x").status,
            status,
        );
    }
});
