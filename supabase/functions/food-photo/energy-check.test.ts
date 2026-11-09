import { assertAlmostEquals, assertEquals } from "jsr:@std/assert@1.0.19";
import { foodEnergyCheck, isFailing } from "./energy-check.ts";

const check = (
    kcal: number,
    protein: number,
    carbs: number,
    fat: number,
    fibre: number | null = null,
    polyols: number | null = null,
    alcohol: number | null = null,
) => foodEnergyCheck({ kcal, protein, carbs, fat, fibre, polyols, alcohol });

// Parity table: identical rows (inputs and expected values) to supabase/tests/food_energy_check.test.sql.
const PARITY: [string, ReturnType<typeof check>][] = [
    ["epic", { result: "clearly_wrong", computed: 250, gap: -250 }],
    ["small gap", { result: "ok", computed: 87, gap: -13 }],
    ["crispbread with fibre", { result: "ok", computed: 342, gap: -8 }],
    ["crispbread without fibre", {
        result: "mismatch",
        computed: 282,
        gap: -68,
    }],
    ["sweets with polyols", { result: "ok", computed: 236, gap: -4 }],
    ["sweets without polyols", {
        result: "clearly_wrong",
        computed: 380,
        gap: 140,
    }],
];
const PARITY_INPUT = [
    () => check(500, 10, 30, 10),
    () => check(100, 5, 10, 3),
    () => check(350, 12, 45, 6, 30),
    () => check(350, 12, 45, 6),
    () => check(240, 0, 95, 0, null, 90),
    () => check(240, 0, 95, 0),
];

Deno.test("parity table with the SQL test: epic, small gap, crispbread, sweets", () => {
    PARITY.forEach(([name, want], i) => {
        const got = PARITY_INPUT[i]();
        assertEquals(got.result, want.result, name);
        assertAlmostEquals(got.computed, want.computed, 1e-9, name);
        assertAlmostEquals(got.gap, want.gap, 1e-9, name);
    });
});

Deno.test("0 kcal with 20 g protein is clearly wrong", () => {
    assertEquals(check(0, 20, 0, 0).result, "clearly_wrong");
});

Deno.test("black coffee is skipped", () => {
    assertEquals(check(2, 0.1, 0.3, 0).result, "skipped");
});

Deno.test("red wine: ok with alcohol, clearly wrong without", () => {
    const withAlcohol = check(83, 0.1, 2.6, 0, null, null, 9.5);
    assertEquals(withAlcohol.result, "ok");
    assertAlmostEquals(withAlcohol.computed, 77.3, 1e-9);
    assertEquals(check(83, 0.1, 2.6, 0).result, "clearly_wrong");
});

Deno.test("boundaries on 0.15 * kcal + 5 and 0.30 * kcal + 5 (kcal 100)", () => {
    assertEquals(check(100, 30, 0, 0).result, "ok");
    assertEquals(check(100, 30.0025, 0, 0).result, "mismatch");
    assertEquals(check(100, 33.75, 0, 0).result, "mismatch");
    assertEquals(check(100, 33.7525, 0, 0).result, "clearly_wrong");
    assertEquals(check(100, 17.5, 0, 0).result, "mismatch");
});

Deno.test("skip boundary: 13 and 13 skipped, above it is not", () => {
    assertEquals(check(13, 3.25, 0, 0).result, "skipped");
    assertEquals(check(13, 3.26, 0, 0).result !== "skipped", true);
    assertEquals(check(14, 0, 0, 0).result !== "skipped", true);
    assertEquals(check(0, 0, 0, 0).result, "skipped");
});

Deno.test("nulls count as 0, polyols are capped at carbs, factors, no rounding", () => {
    assertEquals(
        foodEnergyCheck({ kcal: 100, protein: null, carbs: null, fat: null }),
        check(100, 0, 0, 0, 0, 0, 0),
    );
    assertEquals(check(100, 0, 10, 0, null, 50).computed, 24);
    assertEquals(check(100, 0, 0, 0, 5).gap, -90);
    assertEquals(check(100, 0, 0, 0, null, null, 10).computed, 70);
    assertAlmostEquals(check(100, 1.111, 0, 0).computed, 4.444, 1e-9);
});

Deno.test("isFailing is true only for mismatch and clearly_wrong", () => {
    assertEquals(
        (["ok", "skipped", "mismatch", "clearly_wrong"] as const).map(
            isFailing,
        ),
        [false, false, true, true],
    );
});
