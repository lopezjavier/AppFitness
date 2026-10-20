// Entrypoint of the `food-barcode` Edge Function (verify_jwt = true in config.toml).
// GET ?code= -> cached food (refreshed from Open Food Facts after 30 days); see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { bearerToken } from "../_shared/errors.ts";
import {
    adminClient,
    authenticate,
    callerClient,
    findByBarcode,
    upsertOffFoods,
} from "../_shared/food-store.ts";
import { fetchOffProduct } from "../_shared/off-client.ts";
import { createFoodBarcodeHandler } from "./handler.ts";

const offDeps = () => ({
    fetch: (input: URL | string, init?: RequestInit) => fetch(input, init),
    userAgent: Deno.env.get("OFF_USER_AGENT") ?? "AppFitness/0.1",
});

Deno.serve((req) => {
    // The caller's JWT client is built per request so that RLS applies to every read.
    const token = bearerToken(req) ?? "";
    return createFoodBarcodeHandler({
        authenticate,
        findLocal: (code) => findByBarcode(callerClient(token), code),
        fetchOff: (code) => fetchOffProduct(offDeps(), code),
        upsertOff: async (food) =>
            (await upsertOffFoods(adminClient(), [food]))[0],
        now: () => new Date(),
    })(req);
});
