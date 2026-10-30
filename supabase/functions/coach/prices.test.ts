import { assertEquals } from "jsr:@std/assert@1.0.19";
import { NO_USAGE } from "../_shared/anthropic.ts";
import { costEur } from "./prices.ts";

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
