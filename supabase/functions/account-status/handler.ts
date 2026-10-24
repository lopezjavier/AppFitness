// Pure request handler for the `account-status` Edge Function (ADR-57).
// Tells the caller only whether THEIR account is protected; the list is never returned.

export type AccountStatusDeps = {
    /** Resolves the user of an access token, or null when the token is invalid. */
    getUser: (
        token: string,
    ) => Promise<{ id: string; email?: string | null } | null>;
    /** Raw PROTECTED_EMAILS secret, read per request. */
    protectedEmails: () => string | undefined;
};

const JSON_HEADERS = {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
} as const;

function json(
    status: number,
    body: unknown,
    extra: Record<string, string> = {},
): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { ...JSON_HEADERS, ...extra },
    });
}

function error(status: number, code: string, message: string): Response {
    return json(status, { error: { code, message } });
}

export function createAccountStatusHandler(
    deps: AccountStatusDeps,
    isProtected: (
        email: string | null | undefined,
        raw: string | undefined,
    ) => boolean,
): (req: Request) => Promise<Response> {
    return async (req: Request): Promise<Response> => {
        if (req.method !== "GET") {
            return json(
                405,
                {
                    error: {
                        code: "method_not_allowed",
                        message: "Only GET is allowed",
                    },
                },
                { allow: "GET" },
            );
        }
        const match = /^Bearer\s+(\S+)$/i.exec(
            req.headers.get("authorization") ?? "",
        );
        const unauthorized = () =>
            error(401, "unauthorized", "Missing, invalid or expired session");
        if (!match) return unauthorized();

        try {
            const user = await deps.getUser(match[1]);
            if (!user) return unauthorized();
            return json(200, {
                protected: isProtected(user.email, deps.protectedEmails()),
            });
        } catch (err) {
            console.error(
                "account-status: check failed",
                err instanceof Error ? err.name : "unknown",
            );
            return error(500, "status_failed", "Could not check the account");
        }
    };
}
