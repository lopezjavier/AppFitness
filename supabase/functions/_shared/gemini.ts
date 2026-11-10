// Generic Gemini `generateContent` transport shared by `food-photo` and `exercise-chat`.
// All I/O is injected, so tests need no network. Knows nothing about prompts or schemas of any function.

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

/** Never carries the provider body, the image or the user's text: only a generic message, the kind and the HTTP status. */
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

/**
 * Sends `body` to `generateContent` and returns the text of the first non-thought part.
 * Throws GeminiError: no text or an unreadable body is `provider`.
 */
export async function generateContent(
    deps: GeminiDeps,
    body: unknown,
): Promise<string> {
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
                    body: JSON.stringify(body),
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
            const json = await res.json();
            const parts: unknown[] = json?.candidates?.[0]?.content?.parts ??
                [];
            // Skip reasoning ("thought") parts: only the answer holds the output.
            const answer = parts.find((p) => {
                const part = p as { text?: unknown; thought?: unknown };
                return typeof part?.text === "string" && part.thought !== true;
            }) as { text: string } | undefined;
            if (!answer) throw new Error("no text");
            return answer.text;
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

/**
 * Plain-text generation: a fixed system prompt plus one user turn. No tools, no earlier turns,
 * no JSON schema. Returns the model's text (untrimmed).
 */
export function generateText(
    deps: GeminiDeps,
    req: {
        system: string;
        user: string;
        temperature: number;
        maxOutputTokens: number;
        /** Gemini 3 thinking level; "minimal" keeps reasoning tokens from eating the output budget. */
        thinkingLevel?: "minimal" | "low" | "medium" | "high";
    },
): Promise<string> {
    return generateContent(deps, {
        systemInstruction: { parts: [{ text: req.system }] },
        contents: [{ role: "user", parts: [{ text: req.user }] }],
        generationConfig: {
            temperature: req.temperature,
            maxOutputTokens: req.maxOutputTokens,
            ...(req.thinkingLevel
                ? { thinkingConfig: { thinkingLevel: req.thinkingLevel } }
                : {}),
        },
    });
}
