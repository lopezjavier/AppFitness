// Pure request handler for the public `health` Edge Function.
// Import-free on purpose: the database check is injected so tests need no DB, server or network.

export type HealthDeps = { ping: () => Promise<void> };

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

export function createHealthHandler(
    deps: HealthDeps,
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
        try {
            await deps.ping();
        } catch (err) {
            // Log only the error class: the message may contain connection details.
            console.error(
                "health: db ping failed",
                err instanceof Error ? err.name : "unknown",
            );
            return json(503, {
                error: {
                    code: "db_unavailable",
                    message: "Database unavailable",
                },
            });
        }
        return json(200, { status: "ok" });
    };
}
