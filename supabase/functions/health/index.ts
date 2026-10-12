// Entrypoint of the public `health` Edge Function (verify_jwt = false in config.toml).
// GET -> `select 1` against Postgres -> 200 {"status":"ok"}; see handler.ts for the contract.
// Serves unconditionally (no `import.meta.main` guard): tests import handler.ts, never this file.
import postgres from "npm:postgres@3.4.9";
import { createHealthHandler } from "./handler.ts";

async function ping(): Promise<void> {
    // Injected by Supabase both locally and in production. Never log it.
    const url = Deno.env.get("SUPABASE_DB_URL");
    if (!url) throw new Error("SUPABASE_DB_URL is not set");
    const sql = postgres(url, {
        max: 1,
        prepare: false, // compatible with the transaction pooler
        connect_timeout: 5,
        idle_timeout: 1,
    });
    try {
        await sql`select 1`;
    } finally {
        await sql.end({ timeout: 1 });
    }
}

Deno.serve(createHealthHandler({ ping }));
