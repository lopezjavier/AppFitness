// Validation of the model output (the retry validator) and the final invariants of the plan (planning/04 section 8).
// Pure: no I/O.
import {
    type Activity,
    CARDIO_MIN_WEEK,
    STEPS_FLOOR,
    STEPS_STEP,
} from "./cardioRules.ts";
import {
    DEFICIT_CAP,
    FAT_MAX_PCT,
    FAT_MIN_PCT,
    firstPhase,
    FLOOR,
    GAIN_RATE_RANGE,
    intakeRangeViolation,
    LOSS_RATE_RANGE,
    MEAL_PROTEIN_G_PER_KG,
    MIN_MEALS,
    PROTEIN_MAX,
    PROTEIN_MIN,
    rangeClass,
    RATE_TOLERANCE_PCT,
    rateKcal,
    ROUNDING_KCAL,
    SURPLUS_CAP,
} from "./nutritionRules.ts";
import { addDays, type Candidate } from "./outcomes.ts";
import type { ModelOutcome, Nutrition, Phase } from "./schemas.ts";

export const MAX_OUTCOMES = 4;
export const REASON_MIN = 10;
export const REASON_MAX = 300;
export const DEADLINE_MIN_DAYS = 7;
export const DEADLINE_MAX_DAYS = 28;

export const isRecord = (v: unknown): v is Record<string, unknown> =>
    typeof v === "object" && v !== null && !Array.isArray(v);

export const sameKeys = (o: Record<string, unknown>, keys: string[]) => {
    const own = Object.keys(o).sort();
    return own.length === keys.length &&
        [...keys].sort().every((k, i) => k === own[i]);
};

/** Extracts the JSON from a bare or fenced (```json) answer. */
export function parseJson(text: string): unknown {
    let body = text.trim();
    const fenced = body.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
    if (fenced) body = fenced[1];
    try {
        return JSON.parse(body);
    } catch {
        return undefined;
    }
}

/** Phrases that advise less cardio or fewer steps: the plan never reduces either (07 section 3). */
const CARDIO_REDUCTION: RegExp[] = [
    /menos\s+pasos/i,
    /menos\s+cardio/i,
    /\b(reduc\w*|disminu\w*|recort\w*|acort\w*|limit\w*)\b[^.]{0,40}\b(cardio|pasos)\b/i,
    /\b(evit\w*|elimin\w*|quit\w*|deja\w*)\b[^.]{0,40}\b(cardio|pasos)\b/i,
];
export const mentionsCardioReduction = (text: string): boolean =>
    CARDIO_REDUCTION.some((re) => re.test(text));

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
        if (mentionsCardioReduction(trimmed)) return null;
        // ISO dates compare correctly as strings.
        if (!isRealDate(deadline) || deadline < earliest || deadline > latest) {
            return null;
        }
        seen.add(candidate_id);
        out.push({ candidate_id, reason: trimmed, deadline });
    }
    return out;
}

/** Final check of the activity targets before they are stored; throws on the first broken rule. */
export function assertActivityInvariants(
    a: Activity,
    stepsAvg: number | null,
    _phase: Phase = "maintenance",
): void {
    const fail = (rule: string): never => {
        throw new NutritionInvariantError(rule);
    };
    // 150 min in every phase (07 section 3 "Cardio by phase").
    if (a.cardio_min_week !== CARDIO_MIN_WEEK) fail("cardio_minutes");
    if (a.steps_day < STEPS_FLOOR) fail("steps_below_floor");
    if (a.steps_day % STEPS_STEP !== 0) fail("steps_not_multiple");
    if (
        stepsAvg !== null &&
        a.steps_day < Math.floor(stepsAvg / STEPS_STEP) * STEPS_STEP
    ) fail("steps_below_average");
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
    // A plan stored before T-187 has no phase: the one its intention implies.
    const phase: Phase = n.phase ?? firstPhase(n.intention).phase;
    const dietBreak = n.diet_break ?? null;
    const options = n.phase_options ?? null;

    if (!Object.values(FLOOR).includes(n.floor_kcal)) fail("floor_value");
    if (n.target_kcal < n.floor_kcal) fail("target_below_floor");
    if (n.limited_by === "floor" && n.target_kcal !== n.floor_kcal) {
        fail("floor_flag_without_floor_target");
    }
    if (n.calorie_adjustment !== n.target_kcal - n.expenditure_kcal) {
        fail("adjustment_mismatch");
    }
    // The deficit cap (07 section 4): at most 500 kcal below the expenditure in use.
    if (n.calorie_adjustment < -DEFICIT_CAP) fail("deficit_cap");
    if (n.calorie_adjustment > SURPLUS_CAP) fail("adjustment_range");
    if (
        n.limited_by === "deficit_cap" &&
        (phase !== "cut" || n.calorie_adjustment !== -DEFICIT_CAP)
    ) fail("deficit_cap_flag");
    if (
        options !== null &&
        (!(options.cut_rate_pct >= LOSS_RATE_RANGE[0] - 1e-9) ||
            !(options.cut_rate_pct <= LOSS_RATE_RANGE[1] + 1e-9) ||
            typeof options.diet_break !== "boolean")
    ) fail("phase_options");
    const strategy = n.calorie_adjustment < 0
        ? "deficit"
        : n.calorie_adjustment > 0
        ? "surplus"
        : "maintenance";
    if (n.strategy !== strategy) fail("strategy_mismatch");

    // Target against the intake anchor (04 section 8.7). Plans stored before T-180 lack the fields: skipped.
    const anchor = n.intake_anchor_kcal ?? null;
    if (anchor !== null) {
        const shift = n.slope_term_kcal ?? 0;
        if (Math.abs(shift) > rateKcal(GAIN_RATE_RANGE[1], w) + 1) {
            fail("slope_term_range");
        }
        const violation = intakeRangeViolation(
            rangeClass(phase),
            w,
            shift,
            n.target_kcal - anchor,
            n.limited_by,
        );
        if (violation !== null) fail(violation);
    }

    const limited = n.limited_by !== null;
    if (phase === "cut") {
        if (n.target_kcal >= n.expenditure_kcal) fail("loss_needs_deficit");
        const [min, max] = LOSS_RATE_RANGE;
        if (n.rate_pct_week > max + RATE_TOLERANCE_PCT) fail("loss_rate_max");
        if (n.rate_pct_week < min && !limited) fail("loss_rate_min");
    } else if (phase === "gain") {
        if (n.target_kcal <= n.expenditure_kcal) fail("gain_needs_surplus");
        const [min, max] = GAIN_RATE_RANGE;
        if (n.rate_pct_week > max + RATE_TOLERANCE_PCT) fail("gain_rate_max");
        if (n.rate_pct_week < min && !limited) fail("gain_rate_min");
    } else if (
        // Maintenance, recomposition: the no-change range of 04 section 8.7 on the adjustment.
        Math.abs(n.calorie_adjustment) >
            rateKcal(GAIN_RATE_RANGE[0], w) + ROUNDING_KCAL
    ) {
        fail("no_change_range");
    }

    // The diet-break week: only inside a cut, maintenance figures.
    if (dietBreak !== null) {
        if (phase !== "cut" || options?.diet_break !== true) {
            fail("diet_break_phase");
        }
        if (
            dietBreak.week !== 4 ||
            dietBreak.target_kcal < n.floor_kcal ||
            dietBreak.calorie_adjustment !==
                dietBreak.target_kcal - n.expenditure_kcal ||
            dietBreak.calorie_adjustment < 0
        ) fail("diet_break");
    } else if (phase === "cut" && options?.diet_break === true) {
        fail("diet_break");
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
