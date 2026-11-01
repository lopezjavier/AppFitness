// Price table of the coach models (planning/07 section 11). Dollars are counted as euros (conservative).
import type { AnthropicUsage } from "../_shared/anthropic.ts";

export type CoachModel = "opus" | "sonnet" | "haiku";

/** Provider model ids per coach model. */
export const MODEL_IDS: Record<CoachModel, string> = {
    opus: "claude-opus-5-5",
    sonnet: "claude-sonnet-5-5",
    haiku: "claude-haiku-5-5",
};

/** Whitelist check for a client-supplied model key; null when it is not one of the coach models. */
export function parseCoachModel(value: unknown): CoachModel | null {
    return typeof value === "string" && Object.hasOwn(MODEL_IDS, value)
        ? value as CoachModel
        : null;
}

type Price = { in: number; out: number; cacheReadFactor: number };

/** Price per million tokens. */
export const PRICES: Record<CoachModel, Price> = {
    opus: { in: 4, out: 20, cacheReadFactor: 0.05 },
    sonnet: { in: 2, out: 10, cacheReadFactor: 0.05 },
    haiku: { in: 0.1, out: 0.5, cacheReadFactor: 0.1 },
};

/** Haiku 5.5 charges more for a request whose whole prompt (input + cache reads + cache writes) is over 100k tokens. */
export const HAIKU_LONG_PROMPT_TOKENS = 100_000;
const HAIKU_LONG_PRICE: Price = { in: 0.5, out: 2.5, cacheReadFactor: 0.1 };

/** A 5-minute cache write costs 1.25x the input price (all models). */
export const CACHE_WRITE_FACTOR = 1.25;

/** Cost of one call, rounded to 6 decimals (the precision of `coach_usage.cost_eur`). */
export function costEur(model: CoachModel, usage: AnthropicUsage): number {
    const promptTokens = usage.inputTokens + usage.cacheReadTokens +
        usage.cacheWriteTokens;
    const p = model === "haiku" && promptTokens > HAIKU_LONG_PROMPT_TOKENS
        ? HAIKU_LONG_PRICE
        : PRICES[model];
    const total = usage.inputTokens * p.in +
        usage.outputTokens * p.out +
        usage.cacheReadTokens * p.in * p.cacheReadFactor +
        usage.cacheWriteTokens * p.in * CACHE_WRITE_FACTOR;
    // `total` is in millionths of a euro (price per MTok x tokens), so rounding it gives 6 decimals.
    return Math.round(total) / 1_000_000;
}
