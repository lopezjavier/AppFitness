// Entrypoint of the `food-photo` Edge Function (verify_jwt = true in config.toml).
// POST { image_base64, mode, comment? } -> { draft } estimated by Gemini; see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { authenticate } from "../_shared/food-store.ts";
import { generateFoodJson } from "../_shared/gemini-client.ts";
import { createFoodPhotoHandler } from "./handler.ts";

const handler = createFoodPhotoHandler({
    authenticate,
    model: Deno.env.get("GEMINI_MODEL"),
    // Non-blank FOOD_PHOTO_DEBUG turns the diagnostic log line on; read per request.
    debug: () => (Deno.env.get("FOOD_PHOTO_DEBUG") ?? "").trim() !== "",
    generate: (req) =>
        generateFoodJson({
            fetch: (input, init) => fetch(input, init),
            apiKey: Deno.env.get("GEMINI_API_KEY"),
            model: Deno.env.get("GEMINI_MODEL"),
        }, req),
});

Deno.serve(handler);
