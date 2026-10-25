// Gemini `generateContent` client for `food-photo`. All I/O is injected, so tests need no network.
import { COMMENT_PREFIX, PROMPTS } from "../food-photo/prompts.ts";
import {
    type FoodPhotoMode,
    GEMINI_RESPONSE_SCHEMA,
} from "../food-photo/schema.ts";

/** Free-tier flash model. Single place to change it. */
export const GEMINI_MODEL = "gemini-2.5-flash";
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
};

export type GenerateRequest = {
    mode: FoodPhotoMode;
    imageBase64: string;
    comment?: string | null;
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
    return {
        systemInstruction: { parts: [{ text: PROMPTS[req.mode] }] },
        contents: [{ role: "user", parts }],
        generationConfig: {
            temperature: 0.2,
            responseMimeType: "application/json",
            responseSchema: GEMINI_RESPONSE_SCHEMA,
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
                `${GEMINI_BASE_URL}/${GEMINI_MODEL}:generateContent`,
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
            const text = body?.candidates?.[0]?.content?.parts?.[0]?.text;
            if (typeof text !== "string") throw new Error("no text");
            return JSON.parse(text);
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
