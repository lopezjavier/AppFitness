import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { type AnthropicClient, NO_USAGE } from "../_shared/anthropic.ts";
import type { PlanRow } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { parseInput } from "./planNutrition.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const TODAY = "2026-10-30";

const confirmed = { provisional: false };
const summary = (over: Record<string, unknown> = {}) => ({
    profile: { sex: "male", weight_kg: 80, age: 30, height_cm: 180 },
    training_preferences: { intention: "fat_loss" },
    estimates: {
        activity_level: confirmed,
        total_expenditure: {
            value: 2600,
            source: "formula",
            provisional: false,
        },
        experience: { value: "intermediate", provisional: false },
        weight_trend: { slope_kg_week: null, provisional: true },
    },
    main_lifts: [],
    weeks: [],
    ...over,
});

const GOOD = JSON.stringify({
    outcomes: [{
        candidate_id: "weight",
        reason: "Tu peso baja de forma constante hacia el objetivo.",
        deadline: "2026-11-27",
    }],
});

function setup(
    o: {
        texts?: string[];
        summary?: unknown;
        spent?: number;
        globalTotal?: number;
        saveFails?: boolean;
        client?: AnthropicClient | null;
    } = {},
) {
    const texts = o.texts ?? [GOOD];
    let calls = 0;
    let loads = 0;
    const saved: PlanRow[] = [];
    const records: Parameters<CoachDeps["recordUsage"]>[0][] = [];
    const client: AnthropicClient = {
        stream: async function* () {
            const text = texts[Math.min(calls, texts.length - 1)];
            calls++;
            yield { type: "delta", text };
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 1000, outputTokens: 100 },
            };
            yield { type: "done" };
        },
    };
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        userSpent: () => Promise.resolve(o.spent ?? 0),
        globalSpent: () => Promise.resolve(o.globalTotal ?? 0),
        recordUsage: (r) => {
            records.push(r);
            return Promise.resolve();
        },
        client: o.client === undefined ? client : o.client,
        loadContext: () => {
            loads++;
            return Promise.resolve(o.summary ?? summary());
        },
        savePlan: (_t, row) => {
            if (o.saveFails) return Promise.reject(new Error("db down"));
            saved.push(row);
            return Promise.resolve({
                plan_id: "plan-1",
                recommendation_id: "rec-1",
            });
        },
        now: () => NOW,
    };
    return {
        handler: createCoachHandler(deps),
        saved,
        records,
        modelCalls: () => calls,
        loads: () => loads,
    };
}

const post = (
    input: unknown = { today: TODAY },
    token: string | null = "good",
) => new Request("http://x/coach", {
    method: "POST",
    headers: token ? { authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({ kind: "plan_nutrition", input }),
});

const planOf = async (res: Response) => {
    const text = await res.text();
    const m = text.match(/event: delta\ndata: (.*)\n\n/);
    assert(m, "a delta frame");
    return JSON.parse(JSON.parse(m![1]).text);
};

Deno.test("success streams one delta with the plan, then done, and saves the proposed plan", async () => {
    const { handler, saved, records } = setup();
    const res = await handler(post());
    assertEquals(res.status, 200);
    const body = await res.clone().text();
    assertEquals(body.match(/event: delta/g)?.length, 1);
    assert(body.indexOf("event: delta") < body.indexOf("event: done"));
    const plan = await planOf(res);
    assertEquals(plan.plan_id, "plan-1");
    assertEquals(plan.nutrition.target_kcal, 2160);
    assertEquals(plan.nutrition.requires_confirmation, false);
    assertEquals(plan.outcomes.length, 1);
    assertEquals(plan.outcomes[0].kind, "weight");
    assertEquals(plan.outcomes[0].target_value, 78.4);
    assertEquals(saved.length, 1);
    assertEquals(saved[0].blockStart, TODAY);
    assertEquals(saved[0].intention, "fat_loss");
    assertEquals(saved[0].method.attempts, 1);
    assertEquals(saved[0].method.model, "claude-opus-5-5");
    assertEquals(records.length, 1);
});

Deno.test("an invalid first output is retried once and the usage of both is recorded once", async () => {
    const { handler, saved, records, modelCalls } = setup({
        texts: ["no json", GOOD],
    });
    const res = await handler(post());
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(modelCalls(), 2);
    assertEquals(records.length, 1);
    assertEquals(records[0].inputTokens, 2000);
    assertEquals(saved[0].method.attempts, 2);
});

Deno.test("two invalid outputs are coach_invalid_output, charged, and nothing is saved", async () => {
    const bad = JSON.stringify({ outcomes: [], target_kcal: 1000 });
    const { handler, saved, records } = setup({ texts: [bad, bad] });
    const res = await handler(post());
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(saved.length, 0);
    assertEquals(records.length, 1);
});

Deno.test("a model that picks an unknown candidate fails validation", async () => {
    const wrong = JSON.stringify({
        outcomes: [{
            candidate_id: "e1rm:squat",
            reason: "Tu sentadilla sube cada semana.",
            deadline: "2026-11-27",
        }],
    });
    const { handler, saved } = setup({ texts: [wrong, wrong] });
    assertEquals((await handler(post())).status, 502);
    assertEquals(saved.length, 0);
});

Deno.test("the budget refusal happens before the context is loaded", async () => {
    for (const o of [{ spent: 2 }, { globalTotal: 5 }]) {
        const { handler, loads, modelCalls } = setup(o);
        const res = await handler(post());
        assertEquals(res.status, 429);
        assertEquals((await res.json()).error.code, "coach_limit_reached");
        assertEquals(loads(), 0);
        assertEquals(modelCalls(), 0);
    }
});

Deno.test("an unauthenticated call is 401 and loads nothing", async () => {
    const { handler, loads } = setup();
    assertEquals((await handler(post({ today: TODAY }, null))).status, 401);
    assertEquals((await handler(post({ today: TODAY }, "bad"))).status, 401);
    assertEquals(loads(), 0);
});

Deno.test("a missing provider client is 503 NOT_CONFIGURED", async () => {
    const { handler } = setup({ client: null });
    const res = await handler(post());
    assertEquals(res.status, 503);
    assertEquals((await res.json()).error.code, "NOT_CONFIGURED");
});

Deno.test("a bad today is 400 INVALID_INPUT before any model call", async () => {
    for (
        const input of [
            {},
            { today: "30/10/2026" },
            { today: "2026-02-30" },
            { today: TODAY, weight_kg: 50 },
            "x",
            null,
        ]
    ) {
        const { handler, modelCalls, records } = setup();
        const res = await handler(post(input));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(modelCalls(), 0);
        assertEquals(records.length, 0);
    }
});

Deno.test("missing sex, weight, expenditure or intention is coach_missing_data without a model call", async () => {
    const cases = [
        summary({ profile: { sex: null, weight_kg: 80 } }),
        summary({ profile: { sex: "male", weight_kg: null } }),
        summary({ training_preferences: { intention: null } }),
        summary({
            estimates: {
                total_expenditure: { value: null, provisional: true },
            },
        }),
    ];
    for (const s of cases) {
        const { handler, modelCalls, records } = setup({ summary: s });
        const res = await handler(post());
        assertEquals(res.status, 422);
        assertEquals((await res.json()).error.code, "coach_missing_data");
        assertEquals(modelCalls(), 0);
        assertEquals(records.length, 0);
    }
});

Deno.test("an infeasible plan is 422 coach_plan_infeasible", async () => {
    const { handler, modelCalls } = setup({
        summary: summary({
            profile: { sex: "male", weight_kg: 70 },
            estimates: {
                experience: { value: "beginner" },
                total_expenditure: {
                    value: 1450,
                    source: "formula",
                    provisional: false,
                },
            },
        }),
    });
    const res = await handler(post());
    assertEquals(res.status, 422);
    assertEquals((await res.json()).error.code, "coach_plan_infeasible");
    assertEquals(modelCalls(), 0);
});

Deno.test("unconfirmed values still produce a proposed plan flagged requires_confirmation", async () => {
    const { handler, saved } = setup({
        summary: summary({
            estimates: {
                activity_level: { provisional: true },
                total_expenditure: {
                    value: 2600,
                    source: "formula",
                    provisional: true,
                },
                experience: { value: "intermediate", provisional: false },
                weight_trend: { slope_kg_week: null, provisional: true },
            },
        }),
    });
    const plan = await planOf(await handler(post()));
    assertEquals(plan.nutrition.requires_confirmation, true);
    assertEquals(plan.nutrition.unconfirmed, [
        "activity_level",
        "total_expenditure",
    ]);
    assertEquals(saved.length, 1);
});

Deno.test("a persistence failure is INTERNAL and the usage stays charged", async () => {
    const { handler, records } = setup({ saveFails: true });
    const res = await handler(post());
    assertEquals(res.status, 500);
    assertEquals((await res.json()).error.code, "INTERNAL");
    assertEquals(records.length, 1);
});

Deno.test("the summary is sent to the model and saved with the plan", async () => {
    const { handler, saved } = setup();
    await (await handler(post())).text();
    assertEquals(
        (saved[0].inputSummary as { profile: { sex: string } }).profile.sex,
        "male",
    );
});

Deno.test("other kinds are still not available", async () => {
    const { handler } = setup();
    const res = await handler(
        new Request("http://x/coach", {
            method: "POST",
            headers: { authorization: "Bearer good" },
            body: JSON.stringify({ kind: "plan_block", input: {} }),
        }),
    );
    assertEquals(res.status, 501);
});

Deno.test("parseInput accepts only a real date", () => {
    assertEquals(parseInput({ today: TODAY }), TODAY);
});
