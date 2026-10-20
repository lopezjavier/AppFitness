// Entrypoint of the `food-search` Edge Function (verify_jwt = true in config.toml).
// GET ?q=&page= -> cached foods first, then Open Food Facts; see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { bearerToken } from "../_shared/errors.ts";
import {
    adminClient,
    authenticate,
    callerClient,
    searchLocal,
    upsertOffFoods,
} from "../_shared/food-store.ts";
import { searchOff } from "../_shared/off-client.ts";
import { createFoodSearchHandler, MAX_RESULTS } from "./handler.ts";

const offDeps = () => ({
    fetch: (input: URL | string, init?: RequestInit) => fetch(input, init),
    userAgent: Deno.env.get("OFF_USER_AGENT") ?? "AppFitness/0.1",
});

Deno.serve((req) => {
    // The caller's JWT client is built per request so that RLS applies to every read.
    const token = bearerToken(req) ?? "";
    return createFoodSearchHandler({
        authenticate,
        searchLocal: (q) => searchLocal(callerClient(token), q, MAX_RESULTS),
        searchOff: (q, page) => searchOff(offDeps(), q, page),
        upsertOff: (foods) => upsertOffFoods(adminClient(), foods),
    })(req);
});
