import { assertEquals } from "jsr:@std/assert@1.0.19";
import {
    MAX_AMOUNT,
    normalizeWeight,
    WEIGHT_RESPONSE_SCHEMA,
} from "./schema.ts";

const weightOf = (confidence: unknown) =>
    normalizeWeight({ estimated_amount: 100, confidence }, "g")?.confidence;

Deno.test("normalizeWeight keeps an integer confidence 1-10", () => {
    for (let c = 1; c <= 10; c++) assertEquals(weightOf(c), c);
});

Deno.test("normalizeWeight rounds and clamps confidence to 1-10", () => {
    assertEquals(weightOf(7.4), 7);
    assertEquals(weightOf(7.5), 8);
    assertEquals(weightOf(0), 1);
    assertEquals(weightOf(-3), 1);
    assertEquals(weightOf(11), 10);
    assertEquals(weightOf(1000), 10);
});

Deno.test("normalizeWeight turns a non-number confidence into null", () => {
    for (
        const c of [null, undefined, "high", "7", NaN, Infinity, {}, [7], true]
    ) {
        assertEquals(weightOf(c), null, String(c));
    }
});

Deno.test("normalizeWeight rejects null, string, zero, negative and non-finite amounts", () => {
    for (const a of [null, undefined, "180", 0, -5, NaN, Infinity, -Infinity]) {
        assertEquals(
            normalizeWeight({ estimated_amount: a, confidence: 5 }, "g"),
            null,
            String(a),
        );
    }
});

Deno.test("normalizeWeight clamps the amount to MAX_AMOUNT and uses the food's unit", () => {
    assertEquals(
        normalizeWeight({ estimated_amount: 9000, confidence: 5 }, "ml"),
        { estimated_amount: MAX_AMOUNT, base_unit: "ml", confidence: 5 },
    );
});

Deno.test("weight response schema: amount is non-nullable and both fields are required", () => {
    assertEquals(WEIGHT_RESPONSE_SCHEMA.properties.estimated_amount, {
        type: "NUMBER",
    });
    assertEquals(WEIGHT_RESPONSE_SCHEMA.properties.confidence, {
        type: "INTEGER",
    });
    assertEquals(WEIGHT_RESPONSE_SCHEMA.required, [
        "estimated_amount",
        "confidence",
    ]);
});
