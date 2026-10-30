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

Deno.test("coach error codes map to their HTTP status", () => {
    const expected = {
        coach_limit_reached: 429,
        coach_no_credit: 402,
        coach_timeout: 504,
        coach_invalid_output: 502,
        coach_kind_not_available: 501,
    } as const;
    for (const [code, status] of Object.entries(expected)) {
        assertEquals(
            errorResponse(code as keyof typeof expected, "x").status,
            status,
        );
    }
});

Deno.test("errorResponse merges extra fields into the error object", async () => {
    const res = errorResponse("coach_limit_reached", "x", {
        extra: { scope: "user" },
    });
    assertEquals(await res.json(), {
        error: { code: "coach_limit_reached", message: "x", scope: "user" },
    });
});
