// Price table of the coach models (planning/07 section 11). Dollars are counted as euros (conservative).
import type { AnthropicUsage } from "../_shared/anthropic.ts";

export type CoachModel = "opus" | "sonnet";

/** Provider model ids per coach model. */
export const MODEL_IDS: Record<CoachModel, string> = {
    opus: "claude-opus-5-5",
    sonnet: "claude-sonnet-5-5",
};

/** Price per million tokens. */
export const PRICES: Record<CoachModel, { in: number; out: number }> = {
    opus: { in: 4, out: 20 },
    sonnet: { in: 2, out: 10 },
};

/** Cache read costs 0.05x the input price; a 5-minute cache write 1.25x. */
export const CACHE_READ_FACTOR = 0.05;
export const CACHE_WRITE_FACTOR = 1.25;

/** Cost of one call, rounded to 6 decimals (the precision of `coach_usage.cost_eur`). */
export function costEur(model: CoachModel, usage: AnthropicUsage): number {
    const p = PRICES[model];
    const total = usage.inputTokens * p.in +
        usage.outputTokens * p.out +
        usage.cacheReadTokens * p.in * CACHE_READ_FACTOR +
        usage.cacheWriteTokens * p.in * CACHE_WRITE_FACTOR;
    // `total` is in millionths of a euro (price per MTok x tokens), so rounding it gives 6 decimals.
    return Math.round(total) / 1_000_000;
}
