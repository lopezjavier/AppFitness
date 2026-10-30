import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    type AnthropicClient,
    type AnthropicEvent,
    NO_USAGE,
} from "../_shared/anthropic.ts";
import { fullCatalog } from "./blockFixtures.ts";
import { type BlockRow, CoachInputError, type PlanState } from "./context.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { makePrepare, parseInput, SESSIONS_PER_CALL } from "./planBlock.ts";
import type { CatalogExercise } from "./schemas.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const PLAN_ID = "11111111-1111-4111-8111-111111111111";
const SECRET = "SECRET-PERSONAL-DATA";
const REASON = "Buen ejercicio base para tu objetivo y tu material.";

const summary = (tp: Record<string, unknown> = {}) => ({
    profile: { sex: "male", weight_kg: 80, email: SECRET },
    estimates: { experience: { value: "intermediate" } },
    training_preferences: {
        intention: "muscle",
        place: "gym",
        equipment: [],
        days_per_week: 3,
        session_minutes: 60,
        ...tp,
    },
});

type Pick = { slot_id: string; exercise_key: string; reason: string };
type Prompt = {
    sessions: {
        session_index: number;
        slots: { slot_id: string; candidates: { exercise_key: string }[] }[];
    }[];
};

/** Answers every slot of the prompt it receives with the first candidate not yet used in that session. */
const answer = (prompt: string, mutate?: (p: Pick[]) => void): string => {
    const parsed = JSON.parse(prompt) as Prompt;
    const picks: Pick[] = [];
    for (const s of parsed.sessions) {
        const used = new Set<string>();
        for (const slot of s.slots) {
            const key = slot.candidates.map((c) => c.exercise_key)
                .find((k) => !used.has(k))!;
            used.add(key);
            picks.push({
                slot_id: slot.slot_id,
                exercise_key: key,
                reason: REASON,
            });
        }
    }
    mutate?.(picks);
    return JSON.stringify({ picks });
};

function setup(o: {
    tp?: Record<string, unknown>;
    sessionsPerCall?: number;
    /** Return text to override the answer of call number n (1-based) with a bad one. */
    badCalls?: number[];
    plan?: PlanState | null;
    attachFails?: boolean;
    catalog?: CatalogExercise[];
    spent?: number;
    timeoutMs?: number;
    slow?: boolean;
    summary?: unknown;
} = {}) {
    const records: Parameters<CoachDeps["recordUsage"]>[0][] = [];
    const attached: BlockRow[] = [];
    const prompts: string[] = [];
    let calls = 0;
    const client: AnthropicClient = {
        async *stream(request) {
            const n = ++calls;
            prompts.push(request.prompt);
            if (o.slow) await new Promise(() => {});
            const text = o.badCalls?.includes(n)
                ? "not json"
                : answer(request.prompt);
            yield { type: "delta", text } as AnthropicEvent;
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 1000, outputTokens: 100 },
            } as AnthropicEvent;
            yield { type: "done" } as AnthropicEvent;
        },
    };
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        userSpent: () => Promise.resolve(o.spent ?? 0),
        globalSpent: () => Promise.resolve(0),
        recordUsage: (r) => {
            records.push(r);
            return Promise.resolve();
        },
        client,
        loadContext: () => Promise.resolve(o.summary ?? summary(o.tp)),
        savePlan: () => Promise.reject(new Error("unexpected savePlan")),
        loadCatalog: () => Promise.resolve(o.catalog ?? fullCatalog()),
        loadPlan: () =>
            Promise.resolve(
                o.plan === undefined
                    ? { status: "proposed", training_saved: false }
                    : o.plan,
            ),
        attachBlock: (_t, row) => {
            if (o.attachFails) return Promise.reject(new Error("db down"));
            attached.push(row);
            return Promise.resolve({
                plan_id: row.planId,
                session_ids: ["a", "b"],
            });
        },
        now: () => NOW,
        timeoutMs: o.timeoutMs,
        kinds: {
            plan_block: {
                model: "opus",
                available: true,
                prepare: makePrepare(o.sessionsPerCall),
            },
        },
    };
    return {
        handler: createCoachHandler(deps),
        records,
        attached,
        prompts,
        calls: () => calls,
    };
}

const post = (input: unknown = { today: "2026-10-30", plan_id: PLAN_ID }) =>
    new Request("http://x/coach", {
        method: "POST",
        headers: { authorization: "Bearer good" },
        body: JSON.stringify({ kind: "plan_block", input }),
    });

const bodyOf = async (res: Response) => {
    const text = await res.text();
    const delta = text.match(/event: delta\ndata: (.*)\n/)![1];
    return JSON.parse(JSON.parse(delta).text);
};

Deno.test("parseInput takes exactly today and a plan uuid", () => {
    assertEquals(parseInput({ today: "2026-10-30", plan_id: PLAN_ID }), {
        today: "2026-10-30",
        planId: PLAN_ID,
    });
    for (
        const bad of [
            { today: "2026-10-30" },
            { today: "2026-10-30", plan_id: PLAN_ID, extra: 1 },
            { today: "2026-10-30", plan_id: "not-a-uuid" },
            { today: "2026-02-31", plan_id: PLAN_ID },
            { today: "30/10/2026", plan_id: PLAN_ID },
            null,
            [],
        ]
    ) {
        assertThrows(() => parseInput(bad), CoachInputError);
    }
});

Deno.test("a valid request streams the block and attaches it once", async () => {
    const t = setup();
    const res = await t.handler(post());
    assertEquals(res.status, 200);
    const body = await bodyOf(res);
    assertEquals(body.plan_id, PLAN_ID);
    assertEquals(body.training.days_per_week, 3);
    assertEquals(body.training.days_chosen, false);
    assertEquals(body.training.block_weeks, 4);
    assertEquals(body.sessions.length, 3);
    const first = body.sessions[0].exercises[0];
    for (
        const k of [
            "exercise_id",
            "exercise_key",
            "role",
            "sets",
            "rep_min",
            "rep_max",
            "rest_seconds",
            "reason",
        ]
    ) assert(k in first, k);
    assertEquals(t.attached.length, 1);
    assertEquals(t.attached[0].planId, PLAN_ID);
    assertEquals(
        (t.attached[0].sessions as unknown[]).length,
        3,
    );
    assertEquals(t.attached[0].method.attempts, 1);
    assertEquals(t.calls(), 1);
    assertEquals(t.records.length, 1);
});

Deno.test("days unset: the response states the chosen frequency", async () => {
    const t = setup({ tp: { days_per_week: null, session_minutes: 45 } });
    const body = await bodyOf(await t.handler(post()));
    assertEquals(body.training.days_chosen, true);
    assertEquals(body.training.days_per_week, 3);
});

Deno.test("the prompt carries no personal data beyond what the block needs", async () => {
    const t = setup();
    await (await t.handler(post())).text();
    for (const p of t.prompts) {
        assert(!p.includes(SECRET));
        assert(!p.includes("weight_kg"));
        assert(!p.includes("sex"));
    }
});

Deno.test("missing intention or place is 422 coach_missing_data and no model call", async () => {
    for (
        const [tp, missing] of [
            [{ intention: null }, ["intention"]],
            [{ place: null }, ["place"]],
        ] as const
    ) {
        const t = setup({ tp });
        const res = await t.handler(post());
        assertEquals(res.status, 422);
        const error = (await res.json()).error;
        assertEquals(error.code, "coach_missing_data");
        assertEquals(error.missing, missing);
        assertEquals(t.calls(), 0);
    }
});

Deno.test("a plan that is not the caller's, closed or already saved is 400 and no model call", async () => {
    for (
        const plan of [
            null,
            { status: "superseded", training_saved: false },
            { status: "accepted", training_saved: true },
        ]
    ) {
        const t = setup({ plan });
        const res = await t.handler(post());
        assertEquals(res.status, 400);
        assertEquals(t.calls(), 0);
    }
});

Deno.test("an empty slot is 422 coach_plan_infeasible and no model call", async () => {
    const t = setup({ catalog: [] });
    const res = await t.handler(post());
    assertEquals(res.status, 422);
    assertEquals((await res.json()).error.code, "coach_plan_infeasible");
    assertEquals(t.calls(), 0);
});

Deno.test("an invalid first answer is retried once and the usage is summed", async () => {
    const t = setup({ badCalls: [1] });
    const res = await t.handler(post());
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.calls(), 2);
    assertEquals(t.records.length, 1);
    assertEquals(t.records[0].inputTokens, 2000);
    assertEquals(t.attached[0].method.attempts, 2);
});

Deno.test("two invalid answers are 502 coach_invalid_output, charged, nothing attached", async () => {
    const t = setup({ badCalls: [1, 2] });
    const res = await t.handler(post());
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(t.attached.length, 0);
    assertEquals(t.records.length, 1);
    assertEquals(t.records[0].inputTokens, 2000);
});

Deno.test("seven sessions are one call by default", async () => {
    assertEquals(SESSIONS_PER_CALL, 7);
    const t = setup({ tp: { days_per_week: 7 } });
    await (await t.handler(post())).text();
    assertEquals(t.calls(), 1);
    assertEquals(t.attached.length, 1);
});

Deno.test("split: parallel parts, one record, one attach, all sessions answered", async () => {
    const t = setup({ tp: { days_per_week: 7 }, sessionsPerCall: 3 });
    const res = await t.handler(post());
    assertEquals(res.status, 200);
    const body = await bodyOf(res);
    assertEquals(t.calls(), 3);
    assertEquals(body.sessions.length, 7);
    assertEquals(t.records.length, 1);
    assertEquals(t.records[0].inputTokens, 3000);
    assertEquals(t.attached.length, 1);
    // Each part only saw its own sessions.
    const seen = t.prompts.map((p) =>
        (JSON.parse(p) as Prompt).sessions.map((s) => s.session_index)
    );
    assertEquals(seen.sort(), [[1, 2, 3], [4, 5, 6], [7]]);
});

Deno.test("split: only the failing part is retried and every attempt is charged", async () => {
    // Calls 1-3 are the first attempts; call 2 fails, so exactly one retry follows.
    const t = setup({
        tp: { days_per_week: 7 },
        sessionsPerCall: 3,
        badCalls: [2],
    });
    const res = await t.handler(post());
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.calls(), 4);
    assertEquals(t.records.length, 1);
    assertEquals(t.records[0].inputTokens, 4000);
    assertEquals(t.attached.length, 1);
});

Deno.test("split: a part that fails twice is coach_invalid_output with all usage charged", async () => {
    const t = setup({
        tp: { days_per_week: 7 },
        sessionsPerCall: 3,
        badCalls: [1, 2, 3, 4, 5, 6],
    });
    const res = await t.handler(post());
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(t.records.length, 1);
    assertEquals(t.records[0].inputTokens, 6000);
    assertEquals(t.attached.length, 0);
});

Deno.test("a slow model is 504 coach_timeout and nothing is attached", async () => {
    const t = setup({ slow: true, timeoutMs: 20 });
    const res = await t.handler(post());
    assertEquals(res.status, 504);
    assertEquals((await res.json()).error.code, "coach_timeout");
    assertEquals(t.attached.length, 0);
});

Deno.test("an exhausted budget answers 429 without calling the model", async () => {
    const t = setup({ spent: 2 });
    const res = await t.handler(post());
    assertEquals(res.status, 429);
    assertEquals(t.calls(), 0);
});

Deno.test("an attach failure is 500 INTERNAL and the usage stays charged", async () => {
    const t = setup({ attachFails: true });
    const res = await t.handler(post());
    assertEquals(res.status, 500);
    assertEquals((await res.json()).error.code, "INTERNAL");
    assertEquals(t.records.length, 1);
});
