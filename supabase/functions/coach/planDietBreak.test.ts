// T-248: plan_diet_break, the model-free switch of an accepted cut (no client, no budget, no usage).
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    type AnthropicClient,
    type AnthropicEvent,
    NO_USAGE,
} from "../_shared/anthropic.ts";
import type { DietBreakRow, PlanState } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { planInputs } from "./inputsFixtures.ts";
import { computeActivity } from "./cardioRules.ts";
import { computeNutrition } from "./nutritionRules.ts";
import type { Nutrition } from "./schemas.ts";
import { KINDS } from "./router.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const TODAY = "2026-10-30";
const PLAN_ID = "22222222-2222-4222-8222-222222222222";

const summary = (
    o: { expenditure?: number; weight?: number; sex?: string | null } = {},
) => ({
    profile: {
        sex: o.sex === undefined ? "male" : o.sex,
        weight_kg: o.weight ?? 80,
        age: 30,
    },
    training_preferences: { intention: "fat_loss" },
    estimates: {
        activity_level: { provisional: false },
        total_expenditure: {
            value: o.expenditure ?? 2600,
            source: "formula",
            provisional: false,
        },
        experience: { value: "intermediate", provisional: false },
        weight_trend: { slope_kg_week: null, provisional: true },
        situation: { value: "continuous" },
    },
    main_lifts: [],
    weeks: [],
});

const stored = (dietBreak = false): Nutrition => {
    const n = computeNutrition({
        intention: "fat_loss",
        sex: "male",
        weightKg: 80,
        expenditureKcal: 2600,
        expenditureSource: "formula",
        experience: "intermediate",
        dietBreak,
    });
    return {
        ...n,
        activity: computeActivity({ intention: "fat_loss", stepsAvg28: null }),
    };
};
const acceptedCut = (nutrition: Nutrition = stored()): PlanState => ({
    status: "accepted",
    training_saved: true,
    phase: "cut",
    nutrition,
});

type SetupOptions = {
    summary?: unknown;
    plan?: PlanState | null;
    client?: AnthropicClient | null;
    spent?: number;
    writeFails?: boolean;
};

function setup(o: SetupOptions = {}) {
    let calls = 0;
    const writes: DietBreakRow[] = [];
    const usage: unknown[] = [];
    let budgetReads = 0;
    const client: AnthropicClient = {
        async *stream() {
            calls++;
            yield { type: "delta", text: "{}" } as AnthropicEvent;
            yield { type: "usage", usage: { ...NO_USAGE } } as AnthropicEvent;
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
        savePlan: () => Promise.reject(new Error("unexpected savePlan")),
        loadCatalog: () => Promise.reject(new Error("unexpected loadCatalog")),
        loadPlan: () => Promise.resolve(o.plan === undefined ? null : o.plan),
        attachBlock: () => Promise.reject(new Error("unexpected attachBlock")),
        loadPhaseHistory: () =>
            Promise.reject(new Error("unexpected loadPhaseHistory")),
        updatePlanOptions: () =>
            Promise.reject(new Error("unexpected updatePlanOptions")),
        setDietBreak: (_t, row) => {
            if (o.writeFails) return Promise.reject(new Error("db down"));
            writes.push(row);
            return Promise.resolve({
                plan_id: row.planId,
                nutrition: { ...stored(), diet_break: row.week },
            });
        },
        loadReview: () => Promise.reject(new Error("unexpected loadReview")),
        applyPhaseChange: () =>
            Promise.reject(new Error("unexpected applyPhaseChange")),
        applyGoalSwitch: () =>
            Promise.reject(new Error("unexpected applyGoalSwitch")),
        now: () => NOW,
    };
    return {
        handler: createCoachHandler(deps),
        writes,
        usage,
        modelCalls: () => calls,
        budgetReads: () => budgetReads,
    };
}

const post = (input: unknown, token: string | null = "good") =>
    new Request("http://x/coach", {
        method: "POST",
        headers: token ? { authorization: `Bearer ${token}` } : {},
        body: JSON.stringify({ kind: "plan_diet_break", input }),
    });
const toggle = (o: Record<string, unknown> = {}) =>
    post({ today: TODAY, plan_id: PLAN_ID, diet_break: true, ...o });

const bodyOf = async (res: Response) => {
    const text = await res.text();
    const m = text.match(/event: delta\ndata: (.*)\n\n/);
    assert(m, `a delta frame in: ${text}`);
    return JSON.parse(JSON.parse(m![1]).text);
};

Deno.test("plan_diet_break is deterministic: it skips the provider and the budget", () => {
    assertEquals(KINDS.plan_diet_break.deterministic, true);
    assertEquals(KINDS.plan_diet_break.available, true);
});

Deno.test("turning the break on sends the maintenance week and nothing else, with no model call and no usage", async () => {
    const t = setup({ plan: acceptedCut() });
    const res = await t.handler(toggle());
    assertEquals(res.status, 200);
    const text = await res.clone().text();
    assert(text.includes('"input_tokens":0,"output_tokens":0,"cost_eur":0'));
    assertEquals((await bodyOf(res)).plan_id, PLAN_ID);
    assertEquals(t.modelCalls(), 0);
    assertEquals(t.usage.length, 0);
    assertEquals(t.writes.length, 1);
    const w = t.writes[0];
    assertEquals([w.planId, w.dietBreak], [PLAN_ID, true]);
    assertEquals(w.week?.week, 4);
    assertEquals(w.week?.target_kcal, 2600);
    assertEquals(w.week?.calorie_adjustment, 0);
    assertEquals(w.week?.strategy, "maintenance");
    // Only the week travels: the base calories, band and macros are not part of the row.
    assertEquals(Object.keys(w).sort(), ["dietBreak", "planId", "week"]);
});

Deno.test("turning the break off sends a null week", async () => {
    const t = setup({ plan: acceptedCut(stored(true)) });
    const res = await t.handler(toggle({ diet_break: false }));
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.writes, [{ planId: PLAN_ID, dietBreak: false, week: null }]);
});

Deno.test("turning it off needs no coach context at all", async () => {
    const t = setup({
        plan: acceptedCut(stored(true)),
        summary: summary({ sex: null }),
    });
    const res = await t.handler(toggle({ diet_break: false }));
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.writes.length, 1);
});

Deno.test("the week follows the plan's own expenditure even when the context moved", async () => {
    const t = setup({
        plan: acceptedCut(),
        summary: summary({ expenditure: 2700, weight: 78 }),
    });
    await (await t.handler(toggle())).text();
    assertEquals(t.writes[0].week?.target_kcal, 2600);
    assertEquals(t.writes[0].week?.calorie_adjustment, 0);
});

Deno.test("works with the budget exhausted and without a provider key", async () => {
    const t = setup({ plan: acceptedCut(), spent: 99, client: null });
    const res = await t.handler(toggle());
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.writes.length, 1);
    assertEquals(t.budgetReads(), 0);
    assertEquals(t.usage.length, 0);
});

Deno.test("refuses a plan that is missing, proposed, not a cut or edited by the user", async () => {
    const edited = { ...stored(), edited_by_user: true };
    const cases: (PlanState | null)[] = [
        null,
        { ...acceptedCut(), status: "proposed" },
        { ...acceptedCut(), status: "discarded" },
        { ...acceptedCut(), phase: "maintenance" },
        { ...acceptedCut(), phase: "gain" },
        { status: "accepted", training_saved: true },
        acceptedCut(edited),
    ];
    for (const plan of cases) {
        const t = setup({ plan });
        const res = await t.handler(toggle());
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.writes.length, 0);
        assertEquals(t.modelCalls(), 0);
    }
});

Deno.test("refuses malformed input and extra keys", async () => {
    const bads = [
        { diet_break: "yes" },
        { plan_id: "nope" },
        { today: "2026-02-31" },
        { cut_rate_pct: 0.5 },
        { extra: 1 },
    ];
    for (const over of bads) {
        const t = setup({ plan: acceptedCut() });
        const res = await t.handler(toggle(over));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.writes.length, 0);
    }
    const t = setup({ plan: acceptedCut() });
    const res = await t.handler(post({ today: TODAY, plan_id: PLAN_ID }));
    assertEquals(res.status, 400);
});

Deno.test("reports missing data and an infeasible plan without writing", async () => {
    const missing = setup({
        plan: acceptedCut(),
        summary: summary({ sex: null }),
    });
    const r1 = await missing.handler(toggle());
    assertEquals((await r1.json()).error.code, "coach_missing_data");
    assertEquals(missing.writes.length, 0);

    const tight = setup({
        plan: acceptedCut({
            ...stored(),
            expenditure_kcal: 1450,
            weight_kg: 70,
        }),
    });
    const r2 = await tight.handler(toggle());
    assertEquals((await r2.json()).error.code, "coach_plan_infeasible");
    assertEquals(tight.writes.length, 0);
});

Deno.test("a plan of another user is not found: loadPlan returns null for it", async () => {
    const t = setup({ plan: null });
    const res = await t.handler(toggle());
    assertEquals(res.status, 400);
    assertEquals(t.writes.length, 0);
});

Deno.test("a failed write is INTERNAL and leaves no usage", async () => {
    const t = setup({ plan: acceptedCut(), writeFails: true });
    const res = await t.handler(toggle());
    assertEquals(res.status, 500);
    assertEquals((await res.json()).error.code, "INTERNAL");
    assertEquals(t.usage.length, 0);
});

Deno.test("needs a valid token", async () => {
    const t = setup({ plan: acceptedCut() });
    const res = await t.handler(
        new Request("http://x/coach", {
            method: "POST",
            body: JSON.stringify({ kind: "plan_diet_break", input: {} }),
        }),
    );
    assertEquals(res.status, 401);
    assertEquals((await t.handler(post({}, "bad"))).status, 401);
    assertEquals(t.writes.length, 0);
});
