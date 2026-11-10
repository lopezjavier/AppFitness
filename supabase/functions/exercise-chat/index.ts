// Entrypoint of the `exercise-chat` Edge Function (verify_jwt = true in config.toml).
// POST { exercise, question } -> { answer } written by Gemini; see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { authenticate } from "../_shared/food-store.ts";
import { generateText } from "../_shared/gemini.ts";
import { createExerciseChatHandler } from "./handler.ts";

const handler = createExerciseChatHandler({
    authenticate,
    generate: (req) =>
        generateText({
            fetch: (input, init) => fetch(input, init),
            apiKey: Deno.env.get("GEMINI_API_KEY"),
            model: Deno.env.get("GEMINI_MODEL"),
        }, req),
});

Deno.serve(handler);
