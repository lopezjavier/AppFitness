// Pure request handler for `food-barcode`: GET ?code=<EAN/UPC> -> { food }.
// All I/O (auth, database, Open Food Facts, clock) is injected so tests need no network.
import {
    bearerToken,
    errorResponse,
    jsonResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import type { Food, NormalizedFood } from "../_shared/off-client.ts";
import { OffError } from "../_shared/off-client.ts";
import { parseBarcode } from "../_shared/validation.ts";

export const STALE_AFTER_MS = 30 * 24 * 60 * 60 * 1000;

export type FoodBarcodeDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** Cached food readable by the caller (an OFF row or own personal food) with that barcode. */
    findLocal: (
        code: string,
        userId: string,
    ) => Promise<{ food: Food; fetchedAt: Date } | null>;
    /** Product from OFF; null when unknown or without usable nutrition; throws OffError when unreachable. */
    fetchOff: (code: string) => Promise<NormalizedFood | null>;
    /** Stores (or refreshes) an OFF food and its portions with the secret key; returns the stored food. */
    upsertOff: (food: NormalizedFood) => Promise<Food>;
    now: () => Date;
};

const notFound = () =>
    errorResponse(
        "FOOD_NOT_FOUND",
        "No food with usable nutrition for that code",
    );

export function createFoodBarcodeHandler(
    deps: FoodBarcodeDeps,
): (req: Request) => Promise<Response> {
    return async (req) => {
        if (req.method !== "GET") return methodNotAllowed();
        const token = bearerToken(req);
        if (!token) return unauthorized();

        try {
            const userId = await deps.authenticate(token);
            if (!userId) return unauthorized();

            const parsed = parseBarcode(
                new URL(req.url).searchParams.get("code"),
            );
            if (!parsed.ok) {
                return errorResponse("INVALID_INPUT", parsed.message);
            }
            const code = parsed.value;

            const local = await deps.findLocal(code, userId);
            const fresh = local &&
                (local.food.source === "user" ||
                    deps.now().getTime() - local.fetchedAt.getTime() <=
                        STALE_AFTER_MS);
            if (local && fresh) return jsonResponse(200, { food: local.food });

            let off: NormalizedFood | null;
            try {
                off = await deps.fetchOff(code);
            } catch (err) {
                if (!(err instanceof OffError)) throw err;
                if (local) return jsonResponse(200, { food: local.food });
                return errorResponse(
                    "OFF_UNAVAILABLE",
                    "Open Food Facts is not available right now",
                    { status: err.kind === "timeout" ? 504 : 502 },
                );
            }
            if (!off) {
                return local
                    ? jsonResponse(200, { food: local.food })
                    : notFound();
            }
            return jsonResponse(200, { food: await deps.upsertOff(off) });
        } catch (err) {
            // Log only the error class: messages may contain personal data.
            console.error(
                "food-barcode: failed",
                err instanceof Error ? err.name : "unknown",
            );
            return errorResponse("INTERNAL", "Could not look up the food");
        }
    };
}
