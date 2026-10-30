// Shared JSON response helpers for the food Edge Functions.
// Error contract: { "error": { "code", "message" } } (never includes tokens or personal data).

export type ErrorCode =
    | "INVALID_INPUT"
    | "UNAUTHORIZED"
    | "FOOD_NOT_FOUND"
    | "METHOD_NOT_ALLOWED"
    | "OFF_UNAVAILABLE"
    | "PAYLOAD_TOO_LARGE"
    | "NO_FOOD_RECOGNIZED"
    | "QUOTA_EXCEEDED"
    | "PROVIDER_ERROR"
    | "NOT_CONFIGURED"
    | "coach_limit_reached"
    | "coach_no_credit"
    | "coach_timeout"
    | "coach_invalid_output"
    | "coach_kind_not_available"
    | "INTERNAL";

const STATUS: Record<ErrorCode, number> = {
    INVALID_INPUT: 400,
    UNAUTHORIZED: 401,
    FOOD_NOT_FOUND: 404,
    METHOD_NOT_ALLOWED: 405,
    OFF_UNAVAILABLE: 502,
    PAYLOAD_TOO_LARGE: 413,
    NO_FOOD_RECOGNIZED: 422,
    QUOTA_EXCEEDED: 429,
    PROVIDER_ERROR: 502,
    NOT_CONFIGURED: 503,
    coach_limit_reached: 429,
    coach_no_credit: 402,
    coach_timeout: 504,
    coach_invalid_output: 502,
    coach_kind_not_available: 501,
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
    options: {
        status?: number;
        headers?: Record<string, string>;
        /** Extra fields of the error object (e.g. the budget `scope`); never personal data. */
        extra?: Record<string, unknown>;
    } = {},
): Response {
    return jsonResponse(
        options.status ?? STATUS[code],
        { error: { code, message, ...options.extra } },
        options.headers,
    );
}

export const methodNotAllowed = (allowed = "GET") =>
    errorResponse("METHOD_NOT_ALLOWED", `Only ${allowed} is allowed`, {
        headers: { allow: allowed },
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
