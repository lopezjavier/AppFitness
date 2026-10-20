/** Secret key injected by Supabase: SUPABASE_SECRET_KEYS (JSON map) or legacy SUPABASE_SERVICE_ROLE_KEY. */
export function secretKey(): string {
    const multi = Deno.env.get("SUPABASE_SECRET_KEYS");
    if (multi) {
        try {
            const keys = Object.values(
                JSON.parse(multi) as Record<string, string>,
            );
            if (typeof keys[0] === "string") return keys[0];
        } catch {
            return multi; // plain, non-JSON value
        }
    }
    const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!legacy) throw new Error("no secret key available");
    return legacy;
}
