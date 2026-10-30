// Outcome candidates (planning/07 section 6, 04 section 8.4). Every figure is derived here from the summary;
// the model only chooses among the candidates and writes the reason and the deadline.
import type { Nutrition, OutcomeBasis } from "./schemas.ts";

export const BLOCK_WEEKS = 4;
export const E1RM_MIN_WEEKS = 3;
export const E1RM_PROJECTION_WEEKS = 4;

export type SummaryLift = {
    exercise: string;
    sessions?: number;
    /** Best e1RM per ISO week, oldest first; null for a week without data. */
    weekly_e1rm: Array<number | null>;
};

export type CandidateSummary = { main_lifts?: SummaryLift[] };

export type Candidate = {
    id: string;
    kind: "weight" | "e1rm";
    key: string;
    target_value: number;
    unit: "kg";
    default_deadline: string;
    basis: OutcomeBasis;
};

const round1 = (x: number) => Math.round((x + Number.EPSILON) * 10) / 10 + 0;

export function addDays(isoDate: string, days: number): string {
    const d = new Date(`${isoDate}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().slice(0, 10);
}

/** Least-squares slope of y against x. */
function slope(points: Array<[number, number]>): number {
    const n = points.length;
    const mx = points.reduce((s, p) => s + p[0], 0) / n;
    const my = points.reduce((s, p) => s + p[1], 0) / n;
    let sxy = 0;
    let sxx = 0;
    for (const [x, y] of points) {
        sxy += (x - mx) * (y - my);
        sxx += (x - mx) ** 2;
    }
    return sxx === 0 ? 0 : sxy / sxx;
}

export function buildCandidates(
    summary: CandidateSummary,
    nutrition: Nutrition,
    blockStart: string,
    blockWeeks = BLOCK_WEEKS,
): Candidate[] {
    const deadline = addDays(blockStart, 28);
    const out: Candidate[] = [];

    const direction = nutrition.intention === "fat_loss"
        ? -1
        : nutrition.intention === "muscle"
        ? 1
        : 0;
    if (direction !== 0 && nutrition.rate_kg_week * direction > 0) {
        out.push({
            id: "weight",
            kind: "weight",
            key: "weight",
            target_value: round1(
                nutrition.weight_kg + nutrition.rate_kg_week * blockWeeks,
            ),
            unit: "kg",
            default_deadline: deadline,
            basis: {
                weight_kg: nutrition.weight_kg,
                rate_kg_week: nutrition.rate_kg_week,
                block_weeks: blockWeeks,
            },
        });
    }

    for (const lift of summary.main_lifts ?? []) {
        const points: Array<[number, number]> = [];
        lift.weekly_e1rm.forEach((v, i) => {
            if (typeof v === "number") points.push([i, v]);
        });
        if (points.length < E1RM_MIN_WEEKS) continue;
        const s = slope(points);
        if (s <= 0) continue;
        const last = points[points.length - 1][1];
        out.push({
            id: `e1rm:${lift.exercise}`,
            kind: "e1rm",
            key: lift.exercise,
            target_value: round1(last + s * E1RM_PROJECTION_WEEKS),
            unit: "kg",
            default_deadline: deadline,
            basis: {
                weeks_with_data: points.length,
                last_e1rm: last,
                slope_kg_week: Math.round(s * 100) / 100,
            },
        });
    }
    return out;
}
