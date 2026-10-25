// Pure request handler for `food-photo`: POST { image_base64, mode, comment? } -> { draft }.
// Auth and the Gemini call are injected so tests need no network. Never logs the image, the comment
// or the provider body: only the error class / kind.
import {
    bearerToken,
    errorResponse,
    jsonResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import { GeminiError } from "../_shared/gemini-client.ts";
import { type FoodPhotoMode, MODES, normalizeDraft } from "./schema.ts";

/** Largest accepted image once decoded (the app sends ~1.5 MB JPEGs). */
export const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
export const MAX_COMMENT_CHARS = 200;
// Request bodies above this are refused unread (base64 of the cap plus JSON overhead).
const MAX_BODY_CHARS = Math.ceil(MAX_IMAGE_BYTES / 3) * 4 + 1024;

export type FoodPhotoDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** Calls the model; returns its raw JSON output. Throws GeminiError. */
    generate: (
        req: {
            mode: FoodPhotoMode;
            imageBase64: string;
            comment: string | null;
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
            const { image_base64, mode, comment } = body as Record<
                string,
                unknown
            >;

            if (!MODES.includes(mode as FoodPhotoMode)) {
                return invalid("mode must be dish, label or barcode");
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

            const output = await deps.generate({
                mode: mode as FoodPhotoMode,
                imageBase64: image,
                comment: note,
            });
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
                console.error("food-photo: provider failed", err.kind);
                switch (err.kind) {
                    case "quota":
                        return errorResponse(
                            "QUOTA_EXCEEDED",
                            "The AI service quota is exhausted, try again later",
                        );
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

const tooLarge = () =>
    errorResponse(
        "PAYLOAD_TOO_LARGE",
        `Image must be at most ${MAX_IMAGE_BYTES / (1024 * 1024)} MB`,
    );

function decodedSize(base64: string): number {
    const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
    return Math.floor(base64.length * 3 / 4) - padding;
}
