// T-233: the model-free plan_phase_change ("Aceptar" at the end-of-block review): no client, no budget, no usage.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import type { AnthropicClient } from "../_shared/anthropic.ts";
import type { PhaseChangeRow, ReviewState } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { planInputs } from "./inputsFixtures.ts";
import { parseInput } from "./planPhaseChange.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const TODAY = "2026-10-30";
const REVIEW_ID = "33333333-3333-4333-8333-333333333333";

const summary = {
    profile: { sex: "male", weight_kg: 80, age: 30, height_cm: 178 },
    training_preferences: { intention: "fat_loss" },
    estimates: {
        activity_level: { provisional: false },
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
};

const review = (
    toPhase: string | null,
    o: Partial<ReviewState> = {},
): ReviewState => ({
    phase_proposal: toPhase
        ? { to_phase: toPhase, reason: "cut_length" }
        : null,
    phase_decision: null,
    intention: "fat_loss",
    ...o,
});

function setup(
    o: {
        review?: ReviewState | null;
        client?: AnthropicClient | null;
        spent?: number;
    } = {},
) {
    const applied: PhaseChangeRow[] = [];
    const usage: unknown[] = [];
    let budgetReads = 0;
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
        setDietBreak: () =>
            Promise.reject(new Error("unexpected setDietBreak")),
        loadReview: () =>
            Promise.resolve(o.review === undefined ? null : o.review),
        applyPhaseChange: (_t, row) => {
            applied.push(row);
            return Promise.resolve({ plan_id: "plan-new" });
        },
        applyGoalSwitch: () =>
            Promise.reject(new Error("unexpected applyGoalSwitch")),
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
        body: JSON.stringify({ kind: "plan_phase_change", input }),
    });
const valid = (o: Record<string, unknown> = {}) => ({
    today: TODAY,
    review_id: REVIEW_ID,
    ...o,
});

const bodyOf = async (res: Response) => {
    const text = await res.text();
    const m = text.match(/event: delta\ndata: (.*)\n\n/);
    assert(m, `a delta frame in: ${text}`);
    return JSON.parse(JSON.parse(m![1]).text);
};

Deno.test("parseInput accepts exactly { today, review_id }", () => {
    assertEquals(parseInput(valid()), { today: TODAY, reviewId: REVIEW_ID });
});

Deno.test("parseInput refuses missing, extra and malformed fields", () => {
    const bads: unknown[] = [
        null,
        [],
        { today: TODAY },
        { review_id: REVIEW_ID },
        valid({ extra: 1 }),
        valid({ review_id: "nope" }),
        valid({ review_id: 7 }),
        valid({ today: "2026-02-31" }),
        valid({ today: 20261030 }),
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

Deno.test("each target phase produces a valid nutrition with no model call and no usage", async () => {
    const cases: [string, string][] = [
        ["maintenance", "fat_loss"],
        ["cut", "fat_loss"],
        ["gain", "muscle"],
        ["recomposition", "recomposition"],
    ];
    for (const [phase, intention] of cases) {
        const t = setup({ review: review(phase, { intention }) });
        const res = await t.handler(post(valid()));
        assertEquals(res.status, 200, phase);
        const text = await res.clone().text();
        assert(
            text.includes('"input_tokens":0,"output_tokens":0,"cost_eur":0'),
        );
        const body = await bodyOf(res);
        assertEquals(body.plan_id, "plan-new");
        assertEquals(body.nutrition.phase, phase);
        assertEquals(body.nutrition.phase_reason, "intention");
        assertEquals(t.applied.length, 1);
        const row = t.applied[0];
        assertEquals([row.reviewId, row.blockStart], [REVIEW_ID, TODAY]);
        assertEquals(row.nutrition.phase, phase);
        assertEquals(row.method.model, null);
        assertEquals(row.method.cost_eur, 0);
        assertEquals(t.usage.length, 0);
        assertEquals(t.budgetReads(), 0);
    }
});

Deno.test("maintenance after a cut has no deficit", async () => {
    const t = setup({ review: review("maintenance") });
    await (await t.handler(post(valid()))).text();
    assertEquals(t.applied[0].nutrition.calorie_adjustment, 0);
    assertEquals(t.applied[0].nutrition.strategy, "maintenance");
});

Deno.test("the same input gives the same output", async () => {
    const a = setup({ review: review("maintenance") });
    const b = setup({ review: review("maintenance") });
    await (await a.handler(post(valid()))).text();
    await (await b.handler(post(valid()))).text();
    assertEquals(a.applied[0], b.applied[0]);
});

Deno.test("works with the budget exhausted and without a provider key", async () => {
    const t = setup({ review: review("maintenance"), spent: 99, client: null });
    const res = await t.handler(post(valid()));
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.applied.length, 1);
    assertEquals(t.budgetReads(), 0);
});

Deno.test("an unknown review, no proposal, an already decided review and an unusable phase are refused", async () => {
    const cases: (ReviewState | null)[] = [
        null,
        review(null),
        review("maintenance", { phase_decision: "kept" }),
        review("maintenance", { phase_decision: "accepted" }),
        review("regain"),
        review("maintenance", { intention: null }),
    ];
    for (const r of cases) {
        const t = setup({ review: r });
        const res = await t.handler(post(valid()));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.applied.length, 0);
        assertEquals(t.usage.length, 0);
    }
});

Deno.test("a request without a valid token is refused before anything is read", async () => {
    const t = setup({ review: review("maintenance") });
    const res = await t.handler(post(valid(), null));
    assertEquals(res.status, 401);
    assertEquals(t.applied.length, 0);
});
