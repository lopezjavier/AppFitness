// Food-specific Gemini request for `food-photo` (prompts, schemas, body); the transport is in gemini.ts.
import {
    COMMENT_PREFIX,
    FOOD_CONTEXT_PREFIX,
    PREVIOUS_ESTIMATE_PREFIX,
    PROMPTS,
} from "../food-photo/prompts.ts";
import type { PreviousEstimate } from "../food-photo/previous.ts";
import {
    type FoodPhotoMode,
    GEMINI_RESPONSE_SCHEMA,
    WEIGHT_RESPONSE_SCHEMA,
} from "../food-photo/schema.ts";
import { type GeminiDeps, GeminiError, generateContent } from "./gemini.ts";

export {
    GEMINI_BASE_URL,
    GEMINI_MODEL,
    GEMINI_TIMEOUT_MS,
    GeminiError,
    resolveModel,
} from "./gemini.ts";
export type { GeminiDeps, GeminiErrorKind } from "./gemini.ts";

export type GenerateRequest = {
    mode: FoodPhotoMode;
    imageBase64: string;
    comment?: string | null;
    /** Failed estimate to correct (retry of dish and label); sent as a data part. */
    previous?: PreviousEstimate | null;
    /** Chosen food's identity (weight mode only); sent as a data part. No nutrition values (ADR-72). */
    food?: { name: string; brand: string | null; base_unit: "g" | "ml" } | null;
};

function mimeType(base64: string): string {
    if (base64.startsWith("iVBOR")) return "image/png";
    if (base64.startsWith("UklGR")) return "image/webp";
    return "image/jpeg";
}

/** Builds the request body; exported for tests. */
export function buildBody(req: GenerateRequest) {
    const parts: unknown[] = [{
        inlineData: {
            mimeType: mimeType(req.imageBase64),
            data: req.imageBase64,
        },
    }];
    if (req.comment) parts.push({ text: COMMENT_PREFIX + req.comment });
    if (req.previous) {
        // Explicit fields: anything else a caller passes never reaches the model.
        const { estimate, reason } = req.previous;
        parts.push({
            text: PREVIOUS_ESTIMATE_PREFIX +
                JSON.stringify({ estimate, reason }),
        });
    }
    if (req.food) {
        // Explicit fields: anything else a caller passes never reaches the model.
        const { name, brand, base_unit } = req.food;
        parts.push({
            text: FOOD_CONTEXT_PREFIX +
                JSON.stringify({ name, brand, base_unit }),
        });
    }
    return {
        systemInstruction: { parts: [{ text: PROMPTS[req.mode] }] },
        contents: [{ role: "user", parts }],
        generationConfig: {
            temperature: 0.2,
            responseMimeType: "application/json",
            responseSchema: req.mode === "weight"
                ? WEIGHT_RESPONSE_SCHEMA
                : GEMINI_RESPONSE_SCHEMA,
        },
    };
}

/** Returns the JSON object the model produced (unvalidated). Throws GeminiError. */
export async function generateFoodJson(
    deps: GeminiDeps,
    req: GenerateRequest,
): Promise<unknown> {
    const text = await generateContent(deps, buildBody(req));
    try {
        return JSON.parse(text);
    } catch {
        throw new GeminiError("provider");
    }
}
