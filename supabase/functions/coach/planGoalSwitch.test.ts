// T-239: the model-free plan_goal_switch (the preview of "Bloque nuevo" when the saved goal differs from the plan's):
// no client, no budget, no usage; it only stores one proposed plan through applyGoalSwitch.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import type { AnthropicClient } from "../_shared/anthropic.ts";
import { CoachInputError, type GoalSwitchRow } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { planInputs } from "./inputsFixtures.ts";
import { rateKcal } from "./nutritionRules.ts";
import { parseInput } from "./planGoalSwitch.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const TODAY = "2026-10-30";

const summaryOf = (
    intention: string | null,
    o: { weight?: number; expenditure?: number } = {},
) => ({
    profile: {
        sex: "male",
        weight_kg: o.weight ?? 80,
        age: 30,
        height_cm: 178,
    },
    training_preferences: { intention },
    estimates: {
        activity_level: { provisional: false },
        total_expenditure: {
            value: o.expenditure ?? 2600,
            source: "formula",
            provisional: false,
        },
        experience: { value: "intermediate", provisional: false },
        weight_trend: { slope_kg_week: null, provisional: true },
    },
    main_lifts: [],
    weeks: [],
});

function setup(
    o: {
        summary?: unknown;
        client?: AnthropicClient | null;
        spent?: number;
        apply?: () => Promise<never>;
    } = {},
) {
    const applied: GoalSwitchRow[] = [];
    const usage: unknown[] = [];
    let budgetReads = 0;
    const summary = o.summary ?? summaryOf("muscle");
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
        client: o.client === undefined ? null : o.client,
        loadContext: () => Promise.resolve(summary),
        loadInputs: () => Promise.resolve(planInputs()),
        savePlan: () => Promise.reject(new Error("unexpected savePlan")),
        loadCatalog: () => Promise.reject(new Error("unexpected loadCatalog")),
        loadPlan: () => Promise.reject(new Error("unexpected loadPlan")),
        attachBlock: () => Promise.reject(new Error("unexpected attachBlock")),
        loadPhaseHistory: () =>
            Promise.reject(new Error("unexpected loadPhaseHistory")),
        updatePlanOptions: () =>
            Promise.reject(new Error("unexpected updatePlanOptions")),
        loadReview: () => Promise.reject(new Error("unexpected loadReview")),
        applyPhaseChange: () =>
            Promise.reject(new Error("unexpected applyPhaseChange")),
        applyGoalSwitch: o.apply ?? ((_t, row) => {
            applied.push(row);
            return Promise.resolve({
                plan_id: "plan-new",
                current_kcal: 2100,
                new_kcal: row.nutrition.target_kcal,
                phase: row.nutrition.phase,
            });
        }),
        now: () => NOW,
    };
    return {
        handler: createCoachHandler(deps),
        applied,
        usage,
        budgetReads: () => budgetReads,
    };
}

const post = (input: unknown, token: string | null = "good") =>
    new Request("http://x/coach", {
        method: "POST",
        headers: token ? { authorization: `Bearer ${token}` } : {},
        body: JSON.stringify({ kind: "plan_goal_switch", input }),
    });

const bodyOf = async (res: Response) => {
    const text = await res.text();
    const m = text.match(/event: delta\ndata: (.*)\n\n/);
    assert(m, `a delta frame in: ${text}`);
    return JSON.parse(JSON.parse(m![1]).text);
};

const run = async (summary: unknown) => {
    const t = setup({ summary });
    const res = await t.handler(post({ today: TODAY }));
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.applied.length, 1);
    return t.applied[0];
};

Deno.test("parseInput accepts exactly { today }", () => {
    assertEquals(parseInput({ today: TODAY }), { today: TODAY });
});

Deno.test("parseInput refuses missing, extra and malformed fields", () => {
    const bads: unknown[] = [
        null,
        [],
        {},
        { today: TODAY, extra: 1 },
        { today: TODAY, review_id: "x" },
        { today: "2026-02-31" },
        { today: 20261030 },
        { day: TODAY },
    ];
    for (const bad of bads) {
        let code = "";
        try {
            parseInput(bad);
        } catch (e) {
            code = (e as { code?: string }).code ?? "other";
        }
        assertEquals(code, "INVALID_INPUT", JSON.stringify(bad));
    }
});

Deno.test("a cut to a gain starts at the measured maintenance plus the gain rate, in one step", async () => {
    const row = await run(summaryOf("muscle"));
    assertEquals(row.nutrition.phase, "gain");
    assertEquals(row.nutrition.phase_reason, "intention");
    // Intermediate: 0.35 % of 80 kg per week = 308 kcal, rounded up to 310; never the faster 0.5 % rate (440).
    assertEquals(row.nutrition.calorie_adjustment, 310);
    assertEquals(row.nutrition.target_kcal, 2600 + 310);
    assert(row.nutrition.calorie_adjustment < rateKcal(0.5, 80));
    assertEquals(row.nutrition.strategy, "surplus");
});

Deno.test("a gain to a cut uses the cut rate under the 500 kcal cap and above the floor", async () => {
    const row = await run(summaryOf("fat_loss"));
    assertEquals(row.nutrition.phase, "cut");
    assertEquals(row.nutrition.calorie_adjustment, -440);
    assertEquals(row.nutrition.target_kcal, 2160);
    assertEquals(row.nutrition.limited_by, null);

    const heavy = await run(summaryOf("fat_loss", { weight: 120 }));
    assertEquals(heavy.nutrition.calorie_adjustment, -500);
    assertEquals(heavy.nutrition.limited_by, "deficit_cap");

    const low = await run(summaryOf("fat_loss", { expenditure: 1700 }));
    assertEquals(low.nutrition.target_kcal, 1500);
    assertEquals(low.nutrition.limited_by, "floor");
});

Deno.test("maintenance and recomposition are rate 0 on the measured expenditure", async () => {
    for (
        const [intention, phase] of [
            ["maintenance", "maintenance"],
            ["recomposition", "recomposition"],
            ["strength", "maintenance"],
        ]
    ) {
        const row = await run(summaryOf(intention));
        assertEquals(row.nutrition.phase, phase, intention);
        assertEquals(row.nutrition.calorie_adjustment, 0, intention);
        assertEquals(row.nutrition.target_kcal, 2600, intention);
    }
});

Deno.test("the preview answers the calories before and after, with no model call and no usage", async () => {
    const t = setup({ summary: summaryOf("muscle") });
    const res = await t.handler(post({ today: TODAY }));
    assertEquals(res.status, 200);
    const text = await res.clone().text();
    assert(text.includes('"input_tokens":0,"output_tokens":0,"cost_eur":0'));
    const body = await bodyOf(res);
    assertEquals(body.plan_id, "plan-new");
    assertEquals(body.preview, {
        current_kcal: 2100,
        new_kcal: 2910,
        phase: "gain",
    });
    assert(Array.isArray(body.outcomes));
    assertEquals(body.nutrition.phase, "gain");
    assert(body.nutrition.activity);
    assertEquals(t.usage.length, 0);
    assertEquals(t.budgetReads(), 0);
});

Deno.test("applyGoalSwitch gets today, the figures, a model-free method and the input summary", async () => {
    const summary = summaryOf("muscle");
    const row = await run(summary);
    assertEquals(row.today, TODAY);
    assertEquals(row.method.model, null);
    assertEquals(row.method.attempts, 0);
    assertEquals(row.method.cost_eur, 0);
    assertEquals(row.inputSummary, summary);
    assertEquals(row.nutrition.activity !== undefined, true);
});

Deno.test("the same input gives the same output", async () => {
    assertEquals(
        await run(summaryOf("muscle")),
        await run(summaryOf("muscle")),
    );
});

Deno.test("works with the budget exhausted and without a provider key", async () => {
    const t = setup({ spent: 99, client: null });
    const res = await t.handler(post({ today: TODAY }));
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.applied.length, 1);
    assertEquals(t.budgetReads(), 0);
});

Deno.test("a user without a saved intention is refused as missing data and nothing is stored", async () => {
    const t = setup({ summary: summaryOf(null) });
    const res = await t.handler(post({ today: TODAY }));
    assertEquals(res.status, 422);
    assertEquals((await res.json()).error.code, "coach_missing_data");
    assertEquals(t.applied.length, 0);
});

Deno.test("bad input is refused before anything is read", async () => {
    const t = setup();
    const res = await t.handler(post({ today: TODAY, extra: true }));
    assertEquals(res.status, 400);
    assertEquals((await res.json()).error.code, "INVALID_INPUT");
    assertEquals(t.applied.length, 0);
    assertEquals(t.usage.length, 0);
});

Deno.test("a request without a valid token is refused", async () => {
    const t = setup();
    const res = await t.handler(post({ today: TODAY }, null));
    assertEquals(res.status, 401);
    assertEquals(t.applied.length, 0);
});

Deno.test("coach_block_in_progress from the store is a clean 409, any other failure is INTERNAL", async () => {
    const blocked = setup({
        apply: () =>
            Promise.reject(
                new CoachInputError(
                    "coach_block_in_progress",
                    "A block is in progress",
                ),
            ),
    });
    const res = await blocked.handler(post({ today: TODAY }));
    assertEquals(res.status, 409);
    assertEquals((await res.json()).error.code, "coach_block_in_progress");

    const broken = setup({
        apply: () => Promise.reject(new Error("goal switch failed")),
    });
    const res2 = await broken.handler(post({ today: TODAY }));
    assertEquals(res2.status, 500);
    assertEquals((await res2.json()).error.code, "INTERNAL");
});
