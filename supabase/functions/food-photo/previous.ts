// Retry contract of `food-photo` (ADR-104): the app sends back the failed estimate and why it failed, so the model
// can correct it. Validated with the bounds of the draft (finite numbers >= 0, text <= 120).

const MAX_TEXT = 120;

/** Why a previous estimate failed the energy check. */
export type PreviousReason = {
    result: "mismatch" | "clearly_wrong";
    kcal: number;
    computed: number;
    gap: number;
};

/** The failed estimate sent back by the app. */
export type PreviousEstimate = {
    estimate: {
        name: string;
        base_unit: "g" | "ml";
        nutrition: {
            kcal: number;
            protein_g: number | null;
            carbs_g: number | null;
            fat_g: number | null;
            alcohol_g: number;
        };
        micronutrients: { fiber_100g?: number; polyols_100g?: number };
    };
    reason: PreviousReason;
};

const isObject = (v: unknown): v is Record<string, unknown> =>
    v !== null && typeof v === "object" && !Array.isArray(v);

const finite = (v: unknown): v is number =>
    typeof v === "number" && Number.isFinite(v);

/** Optional amount: absent or null is null; otherwise a finite number >= 0 (else undefined = invalid). */
function optionalAmount(v: unknown): number | null | undefined {
    if (v === undefined || v === null) return null;
    return finite(v) && v >= 0 ? v : undefined;
}

/**
 * Validates the `previous` request field. Returns the cleaned value (explicit fields only, nothing else is
 * forwarded to the model) or an error message.
 */
export function parsePrevious(value: unknown): PreviousEstimate | string {
    if (!isObject(value)) return "previous must be an object";
    const { estimate: e, reason: r } = value;
    if (!isObject(e)) return "previous.estimate must be an object";
    if (!isObject(r)) return "previous.reason must be an object";

    if (typeof e.name !== "string") return "previous.estimate.name is required";
    const name = e.name.trim();
    if (name === "" || name.length > MAX_TEXT) {
        return `previous.estimate.name must have 1 to ${MAX_TEXT} characters`;
    }
    if (e.base_unit !== "g" && e.base_unit !== "ml") {
        return "previous.estimate.base_unit must be g or ml";
    }
    if (!isObject(e.nutrition)) {
        return "previous.estimate.nutrition is required";
    }
    const n = e.nutrition;
    if (!finite(n.kcal) || n.kcal < 0) {
        return "previous.estimate.nutrition.kcal must be a number >= 0";
    }
    const protein = optionalAmount(n.protein_g);
    const carbs = optionalAmount(n.carbs_g);
    const fat = optionalAmount(n.fat_g);
    if (protein === undefined || carbs === undefined || fat === undefined) {
        return "previous.estimate.nutrition macros must be numbers >= 0";
    }
    const alcohol = optionalAmount(n.alcohol_g);
    if (alcohol === undefined) {
        return "previous.estimate.nutrition.alcohol_g must be a number >= 0";
    }

    const micronutrients: { fiber_100g?: number; polyols_100g?: number } = {};
    if (e.micronutrients !== undefined && e.micronutrients !== null) {
        if (!isObject(e.micronutrients)) {
            return "previous.estimate.micronutrients must be an object";
        }
        for (const key of ["fiber_100g", "polyols_100g"] as const) {
            const v = optionalAmount(e.micronutrients[key]);
            if (v === undefined) {
                return `previous.estimate.micronutrients.${key} must be a number >= 0`;
            }
            if (v !== null) micronutrients[key] = v;
        }
    }

    if (r.result !== "mismatch" && r.result !== "clearly_wrong") {
        return "previous.reason.result must be mismatch or clearly_wrong";
    }
    if (
        !finite(r.kcal) || r.kcal < 0 || !finite(r.computed) || r.computed < 0
    ) {
        return "previous.reason.kcal and computed must be numbers >= 0";
    }
    if (!finite(r.gap)) return "previous.reason.gap must be a number";

    return {
        estimate: {
            name,
            base_unit: e.base_unit,
            nutrition: {
                kcal: n.kcal,
                protein_g: protein,
                carbs_g: carbs,
                fat_g: fat,
                alcohol_g: alcohol ?? 0,
            },
            micronutrients,
        },
        reason: {
            result: r.result,
            kcal: r.kcal,
            computed: r.computed,
            gap: r.gap,
        },
    };
}
