// Pure request handler for `food-search`: GET ?q=&page= -> { results, source }.
// Local results come from the database (OFF cache + the caller's own foods, via RLS); when fewer than
// MIN_LOCAL_RESULTS match, Open Food Facts is queried and its products are cached for next time.
// Local results are not paginated: `page` only applies to the OFF query; the response is capped at 20.
import {
    bearerToken,
    errorResponse,
    jsonResponse,
    methodNotAllowed,
    unauthorized,
} from "../_shared/errors.ts";
import type { Food, NormalizedFood } from "../_shared/off-client.ts";
import { OffError } from "../_shared/off-client.ts";
import { parsePage, parseQuery } from "../_shared/validation.ts";

export const MIN_LOCAL_RESULTS = 10;
export const MAX_RESULTS = 20;

export type FoodSearchDeps = {
    /** User id of an access token, or null when invalid. */
    authenticate: (token: string) => Promise<string | null>;
    /** Foods the caller may read (OFF + own personal foods) matching the query, with portions. */
    searchLocal: (q: string, userId: string) => Promise<Food[]>;
    /** Open Food Facts search; throws OffError when unreachable. */
    searchOff: (q: string, page: number) => Promise<NormalizedFood[]>;
    /** Stores OFF foods and their portions with the secret key; returns the stored foods. */
    upsertOff: (foods: NormalizedFood[]) => Promise<Food[]>;
};

function merge(local: Food[], fromOff: Food[]): Food[] {
    const seen = new Set<string>();
    const out: Food[] = [];
    for (const f of [...local, ...fromOff]) {
        const keys = [
            `id:${f.id}`,
            ...(f.barcode ? [`bc:${f.source}:${f.barcode}`] : []),
        ];
        if (keys.some((k) => seen.has(k))) continue;
        keys.forEach((k) => seen.add(k));
        out.push(f);
    }
    return out.slice(0, MAX_RESULTS);
}

export function createFoodSearchHandler(
    deps: FoodSearchDeps,
): (req: Request) => Promise<Response> {
    return async (req) => {
        if (req.method !== "GET") return methodNotAllowed();
        const token = bearerToken(req);
        if (!token) return unauthorized();

        try {
            const userId = await deps.authenticate(token);
            if (!userId) return unauthorized();

            const params = new URL(req.url).searchParams;
            const q = parseQuery(params.get("q"));
            if (!q.ok) return errorResponse("INVALID_INPUT", q.message);
            const page = parsePage(params.get("page"));
            if (!page.ok) return errorResponse("INVALID_INPUT", page.message);

            const local = await deps.searchLocal(q.value, userId);
            if (local.length >= MIN_LOCAL_RESULTS) {
                return jsonResponse(200, {
                    results: local.slice(0, MAX_RESULTS),
                    source: "cache",
                });
            }

            let found: NormalizedFood[];
            try {
                found = await deps.searchOff(q.value, page.value);
            } catch (err) {
                if (!(err instanceof OffError)) throw err;
                if (local.length > 0) {
                    return jsonResponse(200, {
                        results: local,
                        source: "cache",
                    });
                }
                return errorResponse(
                    "OFF_UNAVAILABLE",
                    "Open Food Facts is not available right now",
                    { status: err.kind === "timeout" ? 504 : 502 },
                );
            }
            const stored = found.length > 0 ? await deps.upsertOff(found) : [];
            return jsonResponse(200, {
                results: merge(local, stored),
                source: "off",
            });
        } catch (err) {
            // Log only the error class: messages may contain personal data.
            console.error(
                "food-search: failed",
                err instanceof Error ? err.name : "unknown",
            );
            return errorResponse("INTERNAL", "Could not search foods");
        }
    };
}
