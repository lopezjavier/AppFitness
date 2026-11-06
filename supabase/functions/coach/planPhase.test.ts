// T-187: plan_nutrition by phase and the model-free plan_recompute (no client, no budget, no usage).
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    type AnthropicClient,
    type AnthropicEvent,
    NO_USAGE,
} from "../_shared/anthropic.ts";
import type { OptionsRow, PlanRow, PlanState, UpdatedPlan } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { planInputs } from "./inputsFixtures.ts";
import { computeActivity } from "./cardioRules.ts";
import { computeNutrition } from "./nutritionRules.ts";
import type { Nutrition } from "./schemas.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const TODAY = "2026-10-30";
const PLAN_ID = "22222222-2222-4222-8222-222222222222";
const EMPTY = JSON.stringify({ outcomes: [] });

const summary = (
    o: {
        intention?: string;
        situation?: string | null;
        expenditure?: number;
        sex?: string | null;
        weight?: number;
    } = {},
) => ({
    profile: {
        sex: o.sex === undefined ? "male" : o.sex,
        weight_kg: o.weight ?? 80,
        age: 30,
    },
    training_preferences: { intention: o.intention ?? "fat_loss" },
    estimates: {
        activity_level: { provisional: false },
        total_expenditure: {
            value: o.expenditure ?? 2600,
            source: "formula",
            provisional: false,
        },
        experience: { value: "intermediate", provisional: false },
        weight_trend: { slope_kg_week: null, provisional: true },
        situation: { value: o.situation ?? "continuous" },
    },
    main_lifts: [],
    weeks: [],
});

type SetupOptions = {
    summary?: unknown;
    plan?: PlanState | null;
    client?: AnthropicClient | null;
    spent?: number;
    updateFails?: boolean;
    updated?: UpdatedPlan;
};

function setup(o: SetupOptions = {}) {
    let calls = 0;
    const saved: PlanRow[] = [];
    const updates: OptionsRow[] = [];
    const usage: unknown[] = [];
    let budgetReads = 0;
    const client: AnthropicClient = {
        async *stream() {
            calls++;
            yield { type: "delta", text: EMPTY } as AnthropicEvent;
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 1000, outputTokens: 100 },
            } as AnthropicEvent;
            yield { type: "done" } as AnthropicEvent;
        },
    };
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        userSpent: () => {
            budgetReads++;
            return Promise.resolve(o.spent ?? 0);
        },
        globalSpent: () => {
            budgetReads++;
            return Promise.resolve(0);
        },
        recordUsage: (r) => {
            usage.push(r);
            return Promise.resolve();
        },
        client: o.client === undefined ? client : o.client,
        loadContext: () => Promise.resolve(o.summary ?? summary()),
        loadInputs: () => Promise.resolve(planInputs()),
        savePlan: (_t, row) => {
            saved.push(row);
            return Promise.resolve({
                plan_id: "plan-1",
                recommendation_id: "rec-1",
            });
        },
        loadCatalog: () => Promise.reject(new Error("unexpected loadCatalog")),
        loadPlan: () => Promise.resolve(o.plan === undefined ? null : o.plan),
        attachBlock: () => Promise.reject(new Error("unexpected attachBlock")),
        loadPhaseHistory: () =>
            Promise.reject(new Error("unexpected loadPhaseHistory")),
        updatePlanOptions: (_t, row) => {
            if (o.updateFails) return Promise.reject(new Error("db down"));
            updates.push(row);
            return Promise.resolve(
                o.updated ?? {
                    plan_id: row.planId,
                    nutrition: row.nutrition,
                    outcomes: [],
                },
            );
        },
        loadReview: () => Promise.reject(new Error("unexpected loadReview")),
        applyPhaseChange: () =>
            Promise.reject(new Error("unexpected applyPhaseChange")),
        now: () => NOW,
    };
    return {
        handler: createCoachHandler(deps),
        saved,
        updates,
        usage,
        modelCalls: () => calls,
        budgetReads: () => budgetReads,
    };
}

const post = (kind: string, input: unknown, token: string | null = "good") =>
    new Request("http://x/coach", {
        method: "POST",
        headers: token ? { authorization: `Bearer ${token}` } : {},
        body: JSON.stringify({ kind, input }),
    });

const bodyOf = async (res: Response) => {
    const text = await res.text();
    const m = text.match(/event: delta\ndata: (.*)\n\n/);
    assert(m, `a delta frame in: ${text}`);
    return JSON.parse(JSON.parse(m![1]).text);
};

// --- plan_nutrition by phase ---

Deno.test("plan_nutrition stores the phase, its reason and its options in the saved row", async () => {
    const t = setup();
    const res = await t.handler(post("plan_nutrition", { today: TODAY }));
    assertEquals(res.status, 200);
    const plan = await bodyOf(res);
    const n = t.saved[0].nutrition;
    assertEquals(n.phase, "cut");
    assertEquals(n.phase_reason, "intention");
    assertEquals(n.phase_options, { cut_rate_pct: 0.5, diet_break: false });
    assertEquals(n.diet_break, null);
    assertEquals(plan.nutrition.phase, "cut");
});

Deno.test("T-198: a summary that still carries a situation starts from the intention only", async () => {
    for (const situation of ["returning_break", "returning_injury"]) {
        const t = setup({
            summary: summary({ intention: "fat_loss", situation }),
        });
        const res = await t.handler(post("plan_nutrition", { today: TODAY }));
        assertEquals(res.status, 200);
        await res.text();
        const n = t.saved[0].nutrition;
        assertEquals(n.phase, "cut");
        assertEquals(n.phase_reason, "intention");
    }
});

Deno.test("strength and maintenance start in maintenance, with no model call", async () => {
    for (const intention of ["strength", "maintenance"]) {
        const t = setup({ summary: summary({ intention }) });
        const res = await t.handler(post("plan_nutrition", { today: TODAY }));
        assertEquals(res.status, 200);
        await res.text();
        assertEquals(t.saved[0].nutrition.phase, "maintenance");
        assertEquals(t.saved[0].nutrition.calorie_adjustment, 0);
        assertEquals(t.modelCalls(), 0);
    }
});

Deno.test("the optional rate and diet break reach the plan: deficit_cap and the maintenance week", async () => {
    const t = setup();
    const res = await t.handler(
        post("plan_nutrition", {
            today: TODAY,
            cut_rate_pct: 1.0,
            diet_break: true,
        }),
    );
    assertEquals(res.status, 200);
    await res.text();
    const n = t.saved[0].nutrition;
    assertEquals(n.calorie_adjustment, -500);
    assertEquals(n.limited_by, "deficit_cap");
    assertEquals(n.phase_options, { cut_rate_pct: 1.0, diet_break: true });
    assertEquals(n.diet_break?.target_kcal, 2600);
    assertEquals(n.diet_break?.week, 4);
});

Deno.test("an invalid rate, diet break or extra key is INVALID_INPUT before any model call", async () => {
    const bads = [
        { today: TODAY, cut_rate_pct: 1.2 },
        { today: TODAY, cut_rate_pct: 0.4 },
        { today: TODAY, cut_rate_pct: "0.5" },
        { today: TODAY, diet_break: "yes" },
        { today: TODAY, target_kcal: 2000 },
        { cut_rate_pct: 0.5 },
    ];
    for (const input of bads) {
        const t = setup();
        const res = await t.handler(post("plan_nutrition", input));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.modelCalls(), 0);
    }
});

// --- plan_recompute ---

const stored = (): Nutrition => {
    const n = computeNutrition({
        intention: "fat_loss",
        sex: "male",
        weightKg: 80,
        expenditureKcal: 2600,
        expenditureSource: "formula",
        experience: "intermediate",
    });
    return {
        ...n,
        activity: computeActivity({ intention: "fat_loss", stepsAvg28: null }),
    };
};
const proposedCut = (nutrition: Nutrition = stored()): PlanState => ({
    status: "proposed",
    training_saved: false,
    phase: "cut",
    nutrition,
});
const recompute = (o: Record<string, unknown> = {}) =>
    post("plan_recompute", {
        today: TODAY,
        plan_id: PLAN_ID,
        cut_rate_pct: 1.0,
        diet_break: true,
        ...o,
    });

Deno.test("plan_recompute rewrites the options with no model call and no usage", async () => {
    const t = setup({ plan: proposedCut() });
    const res = await t.handler(recompute());
    assertEquals(res.status, 200);
    const text = await res.clone().text();
    assert(text.includes('"input_tokens":0,"output_tokens":0,"cost_eur":0'));
    const body = await bodyOf(res);
    assertEquals(body.plan_id, PLAN_ID);
    assertEquals(t.modelCalls(), 0);
    assertEquals(t.usage.length, 0);
    assertEquals(t.updates.length, 1);
    const u = t.updates[0];
    assertEquals([u.planId, u.cutRatePct, u.dietBreak], [PLAN_ID, 1.0, true]);
    assertEquals(u.nutrition.phase, "cut");
    assertEquals(u.nutrition.limited_by, "deficit_cap");
    assertEquals(u.nutrition.calorie_adjustment, -500);
    assertEquals(u.nutrition.diet_break?.target_kcal, 2600);
    assertEquals(u.nutrition.activity?.cardio_min_week, 150);
});

Deno.test("plan_recompute works with the budget exhausted and without a provider key", async () => {
    const t = setup({ plan: proposedCut(), spent: 99, client: null });
    const res = await t.handler(recompute());
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.updates.length, 1);
    assertEquals(t.budgetReads(), 0);
    assertEquals(t.usage.length, 0);
});

Deno.test("plan_recompute without diet break and with the default rate returns the plain cut", async () => {
    const t = setup({ plan: proposedCut() });
    await (await t.handler(recompute({ cut_rate_pct: 0.5, diet_break: false })))
        .text();
    const n = t.updates[0].nutrition;
    assertEquals(n.calorie_adjustment, -440);
    assertEquals(n.diet_break, null);
    assertEquals(n.phase_options, { cut_rate_pct: 0.5, diet_break: false });
});

Deno.test("plan_recompute keeps the cardio the user edited", async () => {
    const n = stored();
    n.activity = { ...n.activity!, cardio_min_week: 90, cardio_edited: true };
    const t = setup({ plan: proposedCut(n) });
    await (await t.handler(recompute())).text();
    assertEquals(t.updates[0].nutrition.activity?.cardio_min_week, 90);
    assertEquals(t.updates[0].nutrition.activity?.cardio_edited, true);
});

Deno.test("plan_recompute refuses a plan that is missing, not proposed or not a cut", async () => {
    const cases: (PlanState | null)[] = [
        null,
        { ...proposedCut(), status: "accepted" },
        { ...proposedCut(), phase: "maintenance" },
        { status: "proposed", training_saved: false },
    ];
    for (const plan of cases) {
        const t = setup({ plan });
        const res = await t.handler(recompute());
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.updates.length, 0);
        assertEquals(t.modelCalls(), 0);
    }
});

Deno.test("plan_recompute refuses a rate outside 0.5-1.0 and malformed input", async () => {
    const bads = [
        { cut_rate_pct: 1.2 },
        { cut_rate_pct: 0.4 },
        { cut_rate_pct: "1" },
        { diet_break: "yes" },
        { plan_id: "nope" },
        { today: "2026-02-31" },
        { extra: 1 },
    ];
    for (const over of bads) {
        const t = setup({ plan: proposedCut() });
        const res = await t.handler(recompute(over));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.updates.length, 0);
    }
    const t = setup({ plan: proposedCut() });
    const res = await t.handler(
        post("plan_recompute", { today: TODAY, plan_id: PLAN_ID }),
    );
    assertEquals(res.status, 400);
});

Deno.test("plan_recompute reports missing data and an infeasible plan without updating", async () => {
    const missing = setup({
        plan: proposedCut(),
        summary: summary({ sex: null }),
    });
    const r1 = await missing.handler(recompute());
    assertEquals((await r1.json()).error.code, "coach_missing_data");
    assertEquals(missing.updates.length, 0);

    const tight = setup({
        plan: proposedCut(),
        summary: summary({ expenditure: 1450, weight: 70 }),
    });
    const r2 = await tight.handler(recompute());
    assertEquals((await r2.json()).error.code, "coach_plan_infeasible");
    assertEquals(tight.updates.length, 0);
});

Deno.test("a failed write is INTERNAL and still leaves no usage", async () => {
    const t = setup({ plan: proposedCut(), updateFails: true });
    const res = await t.handler(recompute());
    assertEquals(res.status, 500);
    assertEquals((await res.json()).error.code, "INTERNAL");
    assertEquals(t.usage.length, 0);
});

Deno.test("plan_recompute needs a valid token", async () => {
    const t = setup({ plan: proposedCut() });
    const res = await t.handler(
        new Request("http://x/coach", {
            method: "POST",
            body: JSON.stringify({ kind: "plan_recompute", input: {} }),
        }),
    );
    assertEquals(res.status, 401);
    assertEquals(
        (await t.handler(post("plan_recompute", {}, "bad"))).status,
        401,
    );
    assertEquals(t.updates.length, 0);
});
