// Entrypoint of the `coach` Edge Function (verify_jwt = true in config.toml).
// POST { kind, input } -> text/event-stream; see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { createAnthropicClient } from "../_shared/anthropic.ts";
import {
    adminClient,
    authenticate,
    callerClient,
} from "../_shared/food-store.ts";
import { createCoachHandler } from "./handler.ts";

const apiKey = Deno.env.get("ANTHROPIC_API_KEY")?.trim();

const handler = createCoachHandler({
    authenticate,
    // The caller's own token: RLS limits the select to their rows.
    userSpent: async (token, month) => {
        const { data, error } = await callerClient(token)
            .from("coach_usage")
            .select("cost_eur")
            .eq("month", month)
            .maybeSingle();
        if (error) throw new Error("usage read failed");
        return Number(data?.cost_eur ?? 0);
    },
    // The two RPCs are service_role only: the per-user client cannot write or read other users' rows.
    globalSpent: async (month) => {
        const { data, error } = await adminClient().rpc(
            "coach_global_month_cost",
            { p_month: month },
        );
        if (error) throw new Error("global usage read failed");
        return Number(data ?? 0);
    },
    recordUsage: async (r) => {
        const { error } = await adminClient().rpc("coach_record_usage", {
            p_user: r.userId,
            p_month: r.month,
            p_input: r.inputTokens,
            p_output: r.outputTokens,
            p_cost: r.costEur,
        });
        if (error) throw new Error("usage write failed");
    },
    client: apiKey
        ? createAnthropicClient({ fetch: (u, i) => fetch(u, i), apiKey })
        : null,
    now: () => new Date(),
});

Deno.serve(handler);
