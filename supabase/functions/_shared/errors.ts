// Shared JSON response helpers for the food Edge Functions.
// Error contract: { "error": { "code", "message" } } (never includes tokens or personal data).

export type ErrorCode =
    | "INVALID_INPUT"
    | "UNAUTHORIZED"
    | "FOOD_NOT_FOUND"
    | "METHOD_NOT_ALLOWED"
    | "OFF_UNAVAILABLE"
    | "INTERNAL";

const STATUS: Record<ErrorCode, number> = {
    INVALID_INPUT: 400,
    UNAUTHORIZED: 401,
    FOOD_NOT_FOUND: 404,
    METHOD_NOT_ALLOWED: 405,
    OFF_UNAVAILABLE: 502,
    INTERNAL: 500,
};

const JSON_HEADERS = {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
} as const;

export function jsonResponse(
    status: number,
    body: unknown,
    extra: Record<string, string> = {},
): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { ...JSON_HEADERS, ...extra },
    });
}

/** Builds an error response; `status` overrides the default of the code (e.g. 504 for OFF timeouts). */
export function errorResponse(
    code: ErrorCode,
    message: string,
    options: { status?: number; headers?: Record<string, string> } = {},
): Response {
    return jsonResponse(
        options.status ?? STATUS[code],
        { error: { code, message } },
        options.headers,
    );
}

export const methodNotAllowed = () =>
    errorResponse("METHOD_NOT_ALLOWED", "Only GET is allowed", {
        headers: { allow: "GET" },
    });

export const unauthorized = () =>
    errorResponse("UNAUTHORIZED", "Missing, invalid or expired session");

/** Extracts the Bearer token of a request, or null. */
export function bearerToken(req: Request): string | null {
    const match = /^Bearer\s+(\S+)$/i.exec(
        req.headers.get("authorization") ?? "",
    );
    return match ? match[1] : null;
}
