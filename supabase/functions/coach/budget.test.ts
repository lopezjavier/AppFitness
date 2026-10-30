import { assertEquals } from "jsr:@std/assert@1.0.19";
import { checkBudget, monthKey } from "./budget.ts";

Deno.test("under the user limit is ok", () => {
    assertEquals(checkBudget(1.999999, 0), { ok: true });
});

Deno.test("exactly the user limit is refused with scope user", () => {
    assertEquals(checkBudget(2, 2), { ok: false, scope: "user" });
});

Deno.test("over the user limit is refused with scope user", () => {
    assertEquals(checkBudget(2.5, 3), { ok: false, scope: "user" });
});

Deno.test("under the global limit is ok", () => {
    assertEquals(checkBudget(0.5, 4.99), { ok: true });
});

Deno.test("global limit reached refuses with scope global while the user is under", () => {
    assertEquals(checkBudget(0.5, 5), { ok: false, scope: "global" });
});

Deno.test("nothing spent is ok", () => {
    assertEquals(checkBudget(0, 0), { ok: true });
});

Deno.test("monthKey is the first day of the UTC month", () => {
    assertEquals(monthKey(new Date("2026-10-31T23:59:59Z")), "2026-10-01");
    assertEquals(monthKey(new Date("2026-11-01T00:00:00Z")), "2026-11-01");
});
