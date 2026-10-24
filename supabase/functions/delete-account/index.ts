// Entrypoint of the `delete-account` Edge Function (verify_jwt = true in config.toml; ADR-29).
// POST with the user's access token -> deletes that auth user; see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { createClient } from "npm:@supabase/supabase-js@2.58.0";
import { secretKey } from "../_shared/secret-key.ts";
import { createDeleteAccountHandler } from "./handler.ts";

const REQUEST_TIMEOUT_MS = 8000;

function admin() {
    const url = Deno.env.get("SUPABASE_URL");
    if (!url) throw new Error("SUPABASE_URL is not set");
    return createClient(url, secretKey(), {
        auth: { persistSession: false, autoRefreshToken: false },
        global: {
            fetch: (input, init) =>
                fetch(input, {
                    ...init,
                    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
                }),
        },
    });
}

Deno.serve(createDeleteAccountHandler({
    async getUser(token) {
        const { data, error } = await admin().auth.getUser(token);
        if (error || !data.user) return null;
        return { id: data.user.id, email: data.user.email };
    },
    protectedEmails: () => Deno.env.get("PROTECTED_EMAILS"),
    async deleteUser(userId) {
        const { error } = await admin().auth.admin.deleteUser(userId);
        if (!error) return "deleted";
        if (error.code === "user_not_found" || error.status === 404) {
            return "not_found";
        }
        throw error;
    },
}));
