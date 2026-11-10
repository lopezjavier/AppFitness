// Pure request handler for `exercise-chat`: POST { exercise: { name, muscles, instructions }, question }
// -> { answer }. Each question stands alone: no history, no tools, nothing stored. Auth and the Gemini call
// are injected so tests need no network. Never logs the question, the exercise, the answer or the provider
// body: only the error class / kind and status.
import {
    bearerToken,
    errorResponse,
    jsonResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import { GeminiError } from "../_shared/gemini.ts";
import { buildUserContent, SYSTEM_PROMPT } from "./prompt.ts";
import { type ChatInput, parseChatInput } from "./schema.ts";

/** Bytes-safe cap: the field caps in characters can take up to 3 bytes each in UTF-8. */
export const MAX_BODY_CHARS = 16_384;
export const MAX_ANSWER_CHARS = 4000;
export const TEMPERATURE = 0.4;
/** Room for the answer plus any reasoning tokens, so answers are not cut mid-sentence. */
export const MAX_OUTPUT_TOKENS = 1024;

export type ExerciseChatDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** Calls the model with the fixed system prompt and one user turn; returns its text. Throws GeminiError. */
    generate: (req: {
        system: string;
        user: string;
        temperature: number;
        maxOutputTokens: number;
        thinkingLevel: "minimal";
    }) => Promise<string>;
};

const invalid = (message: string) => errorResponse("INVALID_INPUT", message);
const tooLarge = () =>
    errorResponse("PAYLOAD_TOO_LARGE", "Request body is too large");

export function createExerciseChatHandler(
    deps: ExerciseChatDeps,
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
            const input: ChatInput | string = parseChatInput(body);
            if (typeof input === "string") return invalid(input);

            const text = await deps.generate({
                system: SYSTEM_PROMPT,
                user: buildUserContent(input),
                temperature: TEMPERATURE,
                maxOutputTokens: MAX_OUTPUT_TOKENS,
                thinkingLevel: "minimal",
            });
            const answer = text.trim().slice(0, MAX_ANSWER_CHARS).trim();
            if (answer === "") {
                console.error("exercise-chat: provider failed", "empty");
                return errorResponse(
                    "PROVIDER_ERROR",
                    "The AI service returned no answer",
                );
            }
            return jsonResponse(200, { answer });
        } catch (err) {
            if (err instanceof GeminiError) {
                console.error(
                    "exercise-chat: provider failed",
                    err.kind,
                    err.status ?? "no-status",
                );
                switch (err.kind) {
                    case "quota":
                        return errorResponse(
                            "QUOTA_EXCEEDED",
                            "The AI service limit is reached, try again later",
                        );
                    case "timeout":
                        return errorResponse(
                            "PROVIDER_TIMEOUT",
                            "The AI service took too long to answer",
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
                "exercise-chat: failed",
                err instanceof Error ? err.name : "unknown",
            );
            return errorResponse("INTERNAL", "Could not answer the question");
        }
    };
}
