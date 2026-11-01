import { assertEquals } from "jsr:@std/assert@1.0.19";
import { NO_USAGE } from "../_shared/anthropic.ts";
import { costEur, parseCoachModel } from "./prices.ts";

Deno.test("Opus costs 4 in / 20 out per million tokens", () => {
    assertEquals(
        costEur("opus", {
            ...NO_USAGE,
            inputTokens: 1_000_000,
            outputTokens: 1_000_000,
        }),
        24,
    );
});

Deno.test("Sonnet costs 2 in / 10 out per million tokens", () => {
    assertEquals(
        costEur("sonnet", {
            ...NO_USAGE,
            inputTokens: 1_000_000,
            outputTokens: 1_000_000,
        }),
        12,
    );
});

Deno.test("cache reads cost 0.05x and 5-minute cache writes 1.25x of the input price", () => {
    assertEquals(
        costEur("opus", { ...NO_USAGE, cacheReadTokens: 1_000_000 }),
        0.2,
    );
    assertEquals(
        costEur("opus", { ...NO_USAGE, cacheWriteTokens: 1_000_000 }),
        5,
    );
});

Deno.test("zero usage costs nothing", () => {
    assertEquals(costEur("sonnet", NO_USAGE), 0);
});

Deno.test("the cost is rounded to 6 decimals", () => {
    // 1 cache-read token on Sonnet = 0.1 millionths of a euro -> rounds to 0.
    assertEquals(costEur("sonnet", { ...NO_USAGE, cacheReadTokens: 1 }), 0);
    // 7 input tokens on Sonnet = 14 millionths.
    assertEquals(costEur("sonnet", { ...NO_USAGE, inputTokens: 7 }), 0.000014);
});

Deno.test("Haiku costs 0.1 in / 0.5 out per million tokens", () => {
    assertEquals(
        costEur("haiku", {
            ...NO_USAGE,
            inputTokens: 100_000,
            outputTokens: 100_000,
        }),
        0.06,
    );
});

Deno.test("Haiku cache reads cost 0.1x and 5-minute cache writes 1.25x of the input price", () => {
    assertEquals(
        costEur("haiku", { ...NO_USAGE, cacheReadTokens: 100_000 }),
        0.001,
    );
    assertEquals(
        costEur("haiku", { ...NO_USAGE, cacheWriteTokens: 100_000 }),
        0.0125,
    );
});

Deno.test("a Haiku request with a prompt over 100k tokens pays the long-prompt price", () => {
    // 100k tokens is still the base price.
    assertEquals(costEur("haiku", { ...NO_USAGE, inputTokens: 100_000 }), 0.01);
    // 100,001 prompt tokens pay 0.5 in / 0.05 cache read / 2.5 out: 25,000 x 0.5 + 75,001 x 0.05 + 1,000 x 2.5 = 18,750.05 + ... computed below.
    assertEquals(
        costEur("haiku", {
            ...NO_USAGE,
            inputTokens: 25_000,
            cacheReadTokens: 75_001,
            outputTokens: 1_000,
        }),
        Math.round(25_000 * 0.5 + 75_001 * 0.05 + 1_000 * 2.5) / 1_000_000,
    );
});

Deno.test("parseCoachModel accepts the three keys and rejects everything else", () => {
    for (const k of ["opus", "sonnet", "haiku"]) {
        assertEquals(parseCoachModel(k), k);
    }
    for (
        const v of ["gpt", "", null, 5, undefined, "__proto__", "constructor"]
    ) {
        assertEquals(parseCoachModel(v), null);
    }
});
