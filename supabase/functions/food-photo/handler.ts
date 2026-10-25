// Pure request handler for `food-photo`: POST { image_base64, mode, comment? } -> { draft };
// mode weight: POST { image_base64, mode: "weight", food } -> { estimate } (ADR-64).
// Auth and the Gemini call are injected so tests need no network. Never logs the image, the comment
// or the provider body: only the error class / kind.
import {
    bearerToken,
    errorResponse,
    jsonResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import { GeminiError, resolveModel } from "../_shared/gemini-client.ts";
import {
    type FoodPhotoMode,
    MODES,
    normalizeDraft,
    normalizeWeight,
} from "./schema.ts";

/** Largest accepted image once decoded (the app sends ~1.5 MB JPEGs). */
export const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
export const MAX_COMMENT_CHARS = 200;
const MAX_FOOD_TEXT = 120;
// Request bodies above this are refused unread (base64 of the cap plus JSON overhead).
const MAX_BODY_CHARS = Math.ceil(MAX_IMAGE_BYTES / 3) * 4 + 1024;

export type FoodPhotoDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** Value of the optional GEMINI_MODEL secret; only used to name the model in the 404 log line. */
    model?: string;
    /** Calls the model; returns its raw JSON output. Throws GeminiError. */
    generate: (
        req: {
            mode: FoodPhotoMode;
            imageBase64: string;
            comment: string | null;
            /** Chosen food's details; set only for mode weight. */
            food: FoodContext | null;
        },
    ) => Promise<unknown>;
};

const invalid = (message: string) => errorResponse("INVALID_INPUT", message);

export function createFoodPhotoHandler(
    deps: FoodPhotoDeps,
): (req: Request) => Promise<Response> {
    return async (req) => {
        if (req.method !== "POST") return methodNotAllowed("POST");
        const token = bearerToken(req);
        if (!token) return unauthorized();

        try {
            const userId = await deps.authenticate(token);
            if (!userId) return unauthorized();

            const length = Number(req.headers.get("content-length") ?? 0);
            if (length > MAX_BODY_CHARS) return tooLarge();
            const raw = await req.text();
            if (raw.length > MAX_BODY_CHARS) return tooLarge();

            let body: unknown;
            try {
                body = JSON.parse(raw);
            } catch {
                return invalid("Body must be valid JSON");
            }
            if (body === null || typeof body !== "object") {
                return invalid("Body must be a JSON object");
            }
            const { image_base64, mode, comment, food } = body as Record<
                string,
                unknown
            >;

            if (!MODES.includes(mode as FoodPhotoMode)) {
                return invalid("mode must be dish, label, barcode or weight");
            }
            if (typeof image_base64 !== "string") {
                return invalid("image_base64 is required");
            }
            const image = image_base64.replace(/\s+/g, "");
            if (image === "") return invalid("image_base64 is required");
            if (decodedSize(image) > MAX_IMAGE_BYTES) return tooLarge();
            if (
                !/^[A-Za-z0-9+/]+={0,2}$/.test(image) || image.length % 4 !== 0
            ) {
                return invalid("image_base64 must be valid base64");
            }

            let note: string | null = null;
            if (comment !== undefined && comment !== null) {
                if (typeof comment !== "string") {
                    return invalid("comment must be a string");
                }
                if (mode !== "dish") {
                    return invalid("comment is only allowed with mode dish");
                }
                const trimmed = comment.trim();
                if (trimmed.length > MAX_COMMENT_CHARS) {
                    return invalid(
                        `comment must have at most ${MAX_COMMENT_CHARS} characters`,
                    );
                }
                note = trimmed === "" ? null : trimmed;
            }

            let foodContext: FoodContext | null = null;
            if (mode === "weight") {
                const parsed = parseFood(food);
                if (typeof parsed === "string") return invalid(parsed);
                foodContext = parsed;
            } else if (food !== undefined && food !== null) {
                return invalid("food is only allowed with mode weight");
            }

            const output = await deps.generate({
                mode: mode as FoodPhotoMode,
                imageBase64: image,
                comment: note,
                food: foodContext,
            });
            if (foodContext) {
                const estimate = normalizeWeight(output, foodContext.base_unit);
                if (!estimate) {
                    return errorResponse(
                        "NO_FOOD_RECOGNIZED",
                        "The amount could not be estimated from the photo",
                    );
                }
                return jsonResponse(200, { estimate });
            }
            const draft = normalizeDraft(output, mode as FoodPhotoMode);
            if (!draft) {
                return errorResponse(
                    "NO_FOOD_RECOGNIZED",
                    "No food with nutrition could be recognized in the photo",
                );
            }
            return jsonResponse(200, { draft });
        } catch (err) {
            if (err instanceof GeminiError) {
                // A retired model is actionable only if the log names it (config id, never the body).
                console.error(
                    "food-photo: provider failed",
                    err.kind,
                    err.status ?? "no-status",
                    ...(err.kind === "model" ? [resolveModel(deps.model)] : []),
                );
                switch (err.kind) {
                    case "quota":
                        return errorResponse(
                            "QUOTA_EXCEEDED",
                            "The AI service quota is exhausted, try again later",
                        );
                    case "auth":
                    case "missing_key":
                        return errorResponse(
                            "NOT_CONFIGURED",
                            "The AI service is not configured",
                        );
                    default:
                        return errorResponse(
                            "PROVIDER_ERROR",
                            "The AI service is not available right now",
                        );
                }
            }
            // Log only the error class: messages may contain personal data.
            console.error(
                "food-photo: failed",
                err instanceof Error ? err.name : "unknown",
            );
            return errorResponse("INTERNAL", "Could not analyze the photo");
        }
    };
}

type FoodContext = {
    name: string;
    brand: string | null;
    base_unit: "g" | "ml";
    kcal?: number;
    protein_g?: number;
    carbs_g?: number;
    fat_g?: number;
};

const FOOD_NUMBERS = ["kcal", "protein_g", "carbs_g", "fat_g"] as const;

/** Validates the `food` object of weight mode; returns an error message string when invalid. */
function parseFood(value: unknown): FoodContext | string {
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
        return "food is required with mode weight";
    }
    const f = value as Record<string, unknown>;
    if (typeof f.name !== "string") return "food.name is required";
    const name = f.name.trim();
    if (name === "" || name.length > MAX_FOOD_TEXT) {
        return `food.name must have 1 to ${MAX_FOOD_TEXT} characters`;
    }
    let brand: string | null = null;
    if (f.brand !== undefined && f.brand !== null) {
        if (typeof f.brand !== "string") return "food.brand must be a string";
        const b = f.brand.trim();
        if (b.length > MAX_FOOD_TEXT) {
            return `food.brand must have at most ${MAX_FOOD_TEXT} characters`;
        }
        brand = b === "" ? null : b;
    }
    if (f.base_unit !== "g" && f.base_unit !== "ml") {
        return "food.base_unit must be g or ml";
    }
    const out: FoodContext = { name, brand, base_unit: f.base_unit };
    for (const key of FOOD_NUMBERS) {
        const v = f[key];
        if (v === undefined || v === null) continue;
        if (typeof v !== "number" || !Number.isFinite(v) || v < 0) {
            return `food.${key} must be a number >= 0`;
        }
        out[key] = v;
    }
    return out;
}

const tooLarge = () =>
    errorResponse(
        "PAYLOAD_TOO_LARGE",
        `Image must be at most ${MAX_IMAGE_BYTES / (1024 * 1024)} MB`,
    );

function decodedSize(base64: string): number {
    const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
    return Math.floor(base64.length * 3 / 4) - padding;
}
