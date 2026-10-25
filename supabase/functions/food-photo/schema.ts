// Gemini response schema and normalizer of `food-photo`. The model output is untrusted: the allow-list
// normalizer below is the only thing that reaches the app (ADR-59).

export type FoodPhotoMode = "dish" | "label" | "barcode";
export const MODES: readonly FoodPhotoMode[] = ["dish", "label", "barcode"];

/**
 * Micronutrient keys the app understands: the `offKey` of Kotlin `MicronutrientKind` (same Open Food Facts
 * convention). Values travel as grams per 100 base units under `<key>_100g`.
 */
export const MICRONUTRIENT_KEYS: readonly string[] = [
    "saturated-fat",
    "monounsaturated-fat",
    "polyunsaturated-fat",
    "sugars",
    "polyols",
    "starch",
    "fiber",
    "salt",
    "sodium",
    "vitamin-a",
    "vitamin-d",
    "vitamin-e",
    "vitamin-k",
    "vitamin-c",
    "vitamin-b1",
    "vitamin-b2",
    "vitamin-pp",
    "vitamin-b6",
    "vitamin-b9",
    "vitamin-b12",
    "biotin",
    "pantothenic-acid",
    "potassium",
    "chloride",
    "calcium",
    "phosphorus",
    "magnesium",
    "iron",
    "zinc",
    "copper",
    "manganese",
    "fluoride",
    "selenium",
    "chromium",
    "molybdenum",
    "iodine",
];

const ALLOWED_MICRO = new Set(MICRONUTRIENT_KEYS.map((k) => `${k}_100g`));
const MAX_TEXT = 120;

export type Confidence = "low" | "medium" | "high";

export type FoodDraft = {
    name: string;
    brand: string | null;
    base_unit: "g" | "ml";
    nutrition: {
        kcal: number | null;
        protein_g: number | null;
        carbs_g: number | null;
        fat_g: number | null;
    };
    micronutrients: Record<string, number>;
    estimated_grams: number | null;
    confidence: Confidence | null;
};

const num = { type: "NUMBER", nullable: true } as const;

/** Schema sent to Gemini (`responseSchema`, OpenAPI subset); mirrors what `normalizeDraft` reads. */
export const GEMINI_RESPONSE_SCHEMA = {
    type: "OBJECT",
    properties: {
        name: { type: "STRING" },
        brand: { type: "STRING", nullable: true },
        base_unit: { type: "STRING", enum: ["g", "ml"] },
        nutrition: {
            type: "OBJECT",
            properties: {
                kcal: num,
                protein_g: num,
                carbs_g: num,
                fat_g: num,
            },
        },
        micronutrients: {
            type: "OBJECT",
            properties: Object.fromEntries(
                [...ALLOWED_MICRO].map((k) => [k, num]),
            ),
        },
        estimated_grams: num,
        confidence: {
            type: "STRING",
            nullable: true,
            enum: ["low", "medium", "high"],
        },
    },
    required: ["name", "base_unit", "nutrition"],
} as const;

/** Finite number clamped to >= 0, or null. */
function amount(value: unknown): number | null {
    if (typeof value !== "number" || !Number.isFinite(value)) return null;
    return Math.max(0, value);
}

function text(value: unknown): string | null {
    if (typeof value !== "string") return null;
    const t = value.trim().slice(0, MAX_TEXT).trim();
    return t === "" ? null : t;
}

function obj(value: unknown): Record<string, unknown> {
    return value !== null && typeof value === "object" && !Array.isArray(value)
        ? value as Record<string, unknown>
        : {};
}

/**
 * Validates and cleans the model output. Returns null when it is not a usable food (no name, or neither
 * kcal nor any macro), which the handler answers as 422 NO_FOOD_RECOGNIZED.
 * Rules: negatives clamp to 0; non-finite or non-numeric values become null/dropped; unknown micronutrient
 * keys are dropped; `estimated_grams` is only kept for dish and only when > 0; absent values stay null.
 */
export function normalizeDraft(
    raw: unknown,
    mode: FoodPhotoMode,
): FoodDraft | null {
    const r = obj(raw);
    const name = text(r.name);
    if (!name) return null;

    const n = obj(r.nutrition);
    const nutrition = {
        kcal: amount(n.kcal),
        protein_g: amount(n.protein_g),
        carbs_g: amount(n.carbs_g),
        fat_g: amount(n.fat_g),
    };
    if (Object.values(nutrition).every((v) => v === null)) return null;

    const micronutrients: Record<string, number> = {};
    for (const [key, value] of Object.entries(obj(r.micronutrients))) {
        if (!ALLOWED_MICRO.has(key)) continue;
        const v = amount(value);
        if (v !== null) micronutrients[key] = v;
    }

    const grams = amount(r.estimated_grams);
    const confidence = r.confidence === "low" || r.confidence === "medium" ||
            r.confidence === "high"
        ? r.confidence
        : null;

    return {
        name,
        brand: text(r.brand),
        base_unit: r.base_unit === "ml" ? "ml" : "g",
        nutrition,
        micronutrients,
        estimated_grams: mode === "dish" && grams !== null && grams > 0
            ? grams
            : null,
        confidence,
    };
}
