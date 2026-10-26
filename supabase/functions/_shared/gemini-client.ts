// Gemini `generateContent` client for `food-photo`. All I/O is injected, so tests need no network.
import {
    COMMENT_PREFIX,
    FOOD_CONTEXT_PREFIX,
    PROMPTS,
} from "../food-photo/prompts.ts";
import {
    type FoodPhotoMode,
    GEMINI_RESPONSE_SCHEMA,
    WEIGHT_RESPONSE_SCHEMA,
} from "../food-photo/schema.ts";

/**
 * Default free-tier model; the optional GEMINI_MODEL secret overrides it
 * (a 404 means the id is no longer served).
 */
export const GEMINI_MODEL = "gemini-3.5-flash-lite";
const MODEL_ID = /^[a-z0-9][a-z0-9.\-]*$/;

/** Override if it is a safe model id (it goes into a URL path), else the default. */
export function resolveModel(override: string | undefined): string {
    const id = override?.trim();
    return id && MODEL_ID.test(id) ? id : GEMINI_MODEL;
}
export const GEMINI_BASE_URL =
    "https://generativelanguage.googleapis.com/v1beta/models";
export const GEMINI_TIMEOUT_MS = 25_000;

export type GeminiErrorKind =
    | "quota"
    | "provider"
    | "timeout"
    | "missing_key"
    | "auth" // 401/403: key rejected or restricted
    | "model"; // 404: model retired or renamed

/** Never carries the provider body, the image or the comment: only a generic message, the kind and the HTTP status. */
export class GeminiError extends Error {
    constructor(readonly kind: GeminiErrorKind, readonly status?: number) {
        super(`gemini ${kind}`);
        this.name = "GeminiError";
    }
}

export type GeminiDeps = {
    fetch: (input: string, init: RequestInit) => Promise<Response>;
    /** Value of the GEMINI_API_KEY secret; undefined or empty means not configured. */
    apiKey: string | undefined;
    timeoutMs?: number;
    /** Value of the optional GEMINI_MODEL secret; blank or invalid means the default. */
    model?: string;
};

export type GenerateRequest = {
    mode: FoodPhotoMode;
    imageBase64: string;
    comment?: string | null;
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
    if (!deps.apiKey) throw new GeminiError("missing_key");

    const controller = new AbortController();
    const timer = setTimeout(
        () => controller.abort(),
        deps.timeoutMs ?? GEMINI_TIMEOUT_MS,
    );
    try {
        let res: Response;
        try {
            res = await deps.fetch(
                `${GEMINI_BASE_URL}/${
                    resolveModel(deps.model)
                }:generateContent`,
                {
                    method: "POST",
                    headers: {
                        "content-type": "application/json",
                        // Header, never the URL: URLs can end up in logs.
                        "x-goog-api-key": deps.apiKey,
                    },
                    body: JSON.stringify(buildBody(req)),
                    signal: controller.signal,
                },
            );
        } catch {
            throw new GeminiError(
                controller.signal.aborted ? "timeout" : "provider",
            );
        }
        if (res.status === 429) throw new GeminiError("quota", 429);
        if (res.status === 401 || res.status === 403) {
            throw new GeminiError("auth", res.status);
        }
        if (res.status === 404) throw new GeminiError("model", 404);
        if (!res.ok) throw new GeminiError("provider", res.status);

        try {
            const body = await res.json();
            const parts: unknown[] = body?.candidates?.[0]?.content?.parts ??
                [];
            // Skip reasoning ("thought") parts: only the answer holds the JSON.
            const answer = parts.find((p) => {
                const part = p as { text?: unknown; thought?: unknown };
                return typeof part?.text === "string" && part.thought !== true;
            }) as { text: string } | undefined;
            if (!answer) throw new Error("no text");
            return JSON.parse(answer.text);
        } catch (err) {
            if (controller.signal.aborted) throw new GeminiError("timeout");
            throw err instanceof GeminiError
                ? err
                : new GeminiError("provider");
        }
    } finally {
        clearTimeout(timer);
    }
}
