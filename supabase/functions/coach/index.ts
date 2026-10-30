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
    // Both RPCs run with the caller's own token: the data is theirs and RLS/auth.uid() apply.
    loadContext: async (token, today) => {
        const { data, error } = await callerClient(token).rpc("coach_summary", {
            p_today: today,
        });
        if (error || data == null) throw new Error("summary read failed");
        return data;
    },
    savePlan: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "propose_coach_plan",
            {
                p_block_start: row.blockStart,
                p_intention: row.intention,
                p_nutrition: row.nutrition,
                p_outcomes: row.outcomes,
                p_method: row.method,
                p_input_summary: row.inputSummary,
            },
        );
        if (error || !data?.plan_id) throw new Error("plan write failed");
        return {
            plan_id: String(data.plan_id),
            recommendation_id: String(data.recommendation_id),
        };
    },
    client: apiKey
        ? createAnthropicClient({ fetch: (u, i) => fetch(u, i), apiKey })
        : null,
    now: () => new Date(),
});

Deno.serve(handler);
