import { assertEquals } from "jsr:@std/assert@1.0.19";
import { parseBarcode, parsePage, parseQuery } from "./validation.ts";

Deno.test("parseQuery trims and enforces 2-60 characters", () => {
    assertEquals(parseQuery("  avena "), { ok: true, value: "avena" });
    assertEquals(parseQuery("a").ok, false);
    assertEquals(parseQuery("   ").ok, false);
    assertEquals(parseQuery(null).ok, false);
    assertEquals(parseQuery("x".repeat(60)).ok, true);
    assertEquals(parseQuery("x".repeat(61)).ok, false);
});

Deno.test("parsePage defaults to 1 and requires an integer >= 1", () => {
    assertEquals(parsePage(null), { ok: true, value: 1 });
    assertEquals(parsePage("3"), { ok: true, value: 3 });
    for (const bad of ["0", "-1", "1.5", "abc", "99999"]) {
        assertEquals(parsePage(bad).ok, false, bad);
    }
});

Deno.test("parseBarcode accepts only 8, 12 or 13 digits", () => {
    for (const ok of ["12345678", "123456789012", "8410000810004"]) {
        assertEquals(parseBarcode(ok), { ok: true, value: ok });
    }
    for (
        const bad of [
            "abc",
            "1234567",
            "123456789",
            "12345678901234",
            "8410000a10004",
            "",
        ]
    ) {
        assertEquals(parseBarcode(bad).ok, false, bad);
    }
    assertEquals(parseBarcode(null).ok, false);
});
