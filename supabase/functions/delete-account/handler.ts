// Pure request handler for the `delete-account` Edge Function (ADR-29).
// Auth access is injected so tests need no server or network.
import { isProtected } from "../_shared/protected-emails.ts";

export type DeleteAccountDeps = {
    /** Resolves the user of an access token, or null when the token is invalid. */
    getUser: (
        token: string,
    ) => Promise<{ id: string; email?: string | null } | null>;
    /** Raw PROTECTED_EMAILS secret (ADR-57), read per request. */
    protectedEmails: () => string | undefined;
    /** Deletes the auth user (rows cascade). "not_found" when the user no longer exists. */
    deleteUser: (userId: string) => Promise<"deleted" | "not_found">;
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

const unauthorized = () =>
    error(401, "unauthorized", "Missing, invalid or expired session");

export function createDeleteAccountHandler(
    deps: DeleteAccountDeps,
): (req: Request) => Promise<Response> {
    return async (req: Request): Promise<Response> => {
        if (req.method !== "POST") {
            return json(
                405,
                {
                    error: {
                        code: "method_not_allowed",
                        message: "Only POST is allowed",
                    },
                },
                { allow: "POST" },
            );
        }
        // The caller comes only from the access token; the body is never read.
        const match = /^Bearer\s+(\S+)$/i.exec(
            req.headers.get("authorization") ?? "",
        );
        if (!match) return unauthorized();

        try {
            const user = await deps.getUser(match[1]);
            if (!user) return unauthorized();
            if (isProtected(user.email, deps.protectedEmails())) {
                return error(
                    403,
                    "account_protected",
                    "This account is protected",
                );
            }
            const result = await deps.deleteUser(user.id);
            if (result === "not_found") return unauthorized();
        } catch (err) {
            // Log only the error class: the message may contain secrets or personal data.
            console.error(
                "delete-account: delete failed",
                err instanceof Error ? err.name : "unknown",
            );
            return error(500, "delete_failed", "Could not delete the account");
        }
        return json(200, { status: "deleted" });
    };
}
