// Validation of the model output (the retry validator) and the final invariants of the plan (planning/04 section 8).
// Pure: no I/O.
import {
    DEFICIT_CAP,
    FAT_MAX_PCT,
    FAT_MIN_PCT,
    FLOOR,
    GAIN_RATE_RANGE,
    LOSS_RATE_RANGE,
    MEAL_PROTEIN_G_PER_KG,
    MIN_MEALS,
    PROTEIN_MAX,
    PROTEIN_MIN,
    RATE_TOLERANCE_PCT,
    SURPLUS_CAP,
} from "./nutritionRules.ts";
import { addDays, type Candidate } from "./outcomes.ts";
import type { ModelOutcome, Nutrition } from "./schemas.ts";

export const MAX_OUTCOMES = 4;
export const REASON_MIN = 10;
export const REASON_MAX = 300;
export const DEADLINE_MIN_DAYS = 7;
export const DEADLINE_MAX_DAYS = 28;

const isRecord = (v: unknown): v is Record<string, unknown> =>
    typeof v === "object" && v !== null && !Array.isArray(v);

const sameKeys = (o: Record<string, unknown>, keys: string[]) => {
    const own = Object.keys(o).sort();
    return own.length === keys.length &&
        [...keys].sort().every((k, i) => k === own[i]);
};

/** Extracts the JSON from a bare or fenced (```json) answer. */
function parseJson(text: string): unknown {
    let body = text.trim();
    const fenced = body.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
    if (fenced) body = fenced[1];
    try {
        return JSON.parse(body);
    } catch {
        return undefined;
    }
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;
function isRealDate(s: string): boolean {
    if (!DATE.test(s)) return false;
    const d = new Date(`${s}T00:00:00Z`);
    return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s;
}

/** The parsed outcomes, or null when the text breaks any rule (which triggers the single retry). */
export function validateOutcomesText(
    text: string,
    candidates: Candidate[],
    blockStart: string,
): ModelOutcome[] | null {
    const parsed = parseJson(text);
    if (!isRecord(parsed) || !sameKeys(parsed, ["outcomes"])) return null;
    const list = parsed.outcomes;
    if (!Array.isArray(list) || list.length > MAX_OUTCOMES) return null;

    const ids = new Set(candidates.map((c) => c.id));
    const seen = new Set<string>();
    const earliest = addDays(blockStart, DEADLINE_MIN_DAYS);
    const latest = addDays(blockStart, DEADLINE_MAX_DAYS);
    const out: ModelOutcome[] = [];
    for (const item of list) {
        if (
            !isRecord(item) ||
            !sameKeys(item, ["candidate_id", "reason", "deadline"])
        ) return null;
        const { candidate_id, reason, deadline } = item;
        if (
            typeof candidate_id !== "string" || typeof reason !== "string" ||
            typeof deadline !== "string"
        ) return null;
        if (!ids.has(candidate_id) || seen.has(candidate_id)) return null;
        const trimmed = reason.trim();
        if (trimmed.length < REASON_MIN || trimmed.length > REASON_MAX) {
            return null;
        }
        // ISO dates compare correctly as strings.
        if (!isRealDate(deadline) || deadline < earliest || deadline > latest) {
            return null;
        }
        seen.add(candidate_id);
        out.push({ candidate_id, reason: trimmed, deadline });
    }
    return out;
}

export class NutritionInvariantError extends Error {
    constructor(readonly rule: string) {
        super(`nutrition invariant: ${rule}`);
        this.name = "NutritionInvariantError";
    }
}

/** Defensive final check of a plan before it is stored. Should be unreachable; throws on the first broken rule. */
export function assertNutritionInvariants(n: Nutrition): void {
    const fail = (rule: string): never => {
        throw new NutritionInvariantError(rule);
    };
    const w = n.weight_kg;

    if (!Object.values(FLOOR).includes(n.floor_kcal)) fail("floor_value");
    if (n.target_kcal < n.floor_kcal) fail("target_below_floor");
    if (n.limited_by === "floor" && n.target_kcal !== n.floor_kcal) {
        fail("floor_flag_without_floor_target");
    }
    if (n.calorie_adjustment !== n.target_kcal - n.expenditure_kcal) {
        fail("adjustment_mismatch");
    }
    if (
        n.calorie_adjustment < -DEFICIT_CAP ||
        n.calorie_adjustment > SURPLUS_CAP
    ) {
        fail("adjustment_range");
    }
    const strategy = n.calorie_adjustment < 0
        ? "deficit"
        : n.calorie_adjustment > 0
        ? "surplus"
        : "maintenance";
    if (n.strategy !== strategy) fail("strategy_mismatch");

    const limited = n.limited_by !== null;
    if (n.intention === "fat_loss") {
        if (n.target_kcal >= n.expenditure_kcal) fail("loss_needs_deficit");
        const [min, max] = LOSS_RATE_RANGE;
        if (n.rate_pct_week > max + RATE_TOLERANCE_PCT) fail("loss_rate_max");
        if (n.rate_pct_week < min && !limited) fail("loss_rate_min");
    } else if (n.intention === "muscle") {
        if (n.target_kcal <= n.expenditure_kcal) fail("gain_needs_surplus");
        const [min, max] = GAIN_RATE_RANGE;
        if (n.rate_pct_week > max + RATE_TOLERANCE_PCT) fail("gain_rate_max");
        if (n.rate_pct_week < min && !limited) fail("gain_rate_min");
    } else if (n.calorie_adjustment < 0) {
        fail("no_deficit_for_intention");
    } else if (n.calorie_adjustment > 0 && n.limited_by !== "floor") {
        fail("no_surplus_for_intention");
    }

    if (n.protein_g_per_kg < PROTEIN_MIN || n.protein_g_per_kg > PROTEIN_MAX) {
        fail("protein_g_per_kg_range");
    }
    // The grams are rounded to 1 g, so the bounds carry that rounding.
    if (n.protein_g < PROTEIN_MIN * w - 0.5) fail("protein_below_min");
    // The percentage is rounded up to the next whole point (about target / 400 g), so the ceiling carries it.
    if (n.protein_g > PROTEIN_MAX * w + n.target_kcal / 400 + 1) {
        fail("protein_above_max");
    }

    const pcts = [n.protein_pct, n.carbs_pct, n.fat_pct];
    if (pcts.some((p) => !Number.isInteger(p) || p < 5 || p > 90)) {
        fail("pct_range");
    }
    if (n.protein_pct + n.carbs_pct + n.fat_pct !== 100) fail("pct_sum");
    if (n.fat_pct < FAT_MIN_PCT || n.fat_pct > FAT_MAX_PCT) fail("fat_range");

    const grams = (pct: number, kcalPerG: number) =>
        Math.round(n.target_kcal * pct / 100 / kcalPerG);
    if (
        n.protein_g !== grams(n.protein_pct, 4) ||
        n.carbs_g !== grams(n.carbs_pct, 4) ||
        n.fat_g !== grams(n.fat_pct, 9)
    ) fail("grams_mismatch");

    if (
        n.distribution.per_meal_g !== Math.round(MEAL_PROTEIN_G_PER_KG * w) ||
        n.distribution.min_meals < MIN_MEALS
    ) fail("distribution");
}

/** Keys of the derived values that are provisional, with the same rule as accept_coach_plan. */
export function unconfirmedKeys(estimates: unknown): string[] {
    const e = isRecord(estimates) ? estimates : {};
    const part = (k: string): Record<string, unknown> =>
        isRecord(e[k]) ? e[k] as Record<string, unknown> : {};
    const keys: string[] = [];
    if (part("activity_level").provisional === true) {
        keys.push("activity_level");
    }
    const exp = part("total_expenditure");
    if (exp.value != null && exp.provisional === true) {
        keys.push("total_expenditure");
    }
    if (part("experience").provisional === true) keys.push("experience");
    const trend = part("weight_trend");
    if (trend.slope_kg_week != null && trend.provisional === true) {
        keys.push("weight_trend");
    }
    return keys;
}
