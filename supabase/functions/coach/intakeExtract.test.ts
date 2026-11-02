import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { type AnthropicClient, NO_USAGE } from "../_shared/anthropic.ts";
import { type CoachDeps, createCoachHandler } from "./handler.ts";
import { INTAKE_OUTPUT_SCHEMA, parseInput } from "./intakeExtract.ts";
import { KINDS } from "./router.ts";
import type { CatalogExercise } from "./schemas.ts";

const NOW = new Date("2026-10-30T12:00:00Z");
const BENCH = "11111111-1111-4111-8111-111111111111";
const SQUAT = "22222222-2222-4222-8222-222222222222";
const CURL = "33333333-3333-4333-8333-333333333333";
const CUSTOM = "44444444-4444-4444-8444-444444444444";

const ex = (
    id: string,
    key: string | null,
    pattern: CatalogExercise["movement_pattern"],
    custom = false,
): CatalogExercise => ({
    id,
    key,
    name: custom ? "Mi press" : null,
    muscle_group: "chest",
    equipment: "barbell",
    movement_pattern: pattern,
    metric_type: "weight_reps",
    is_custom: custom,
});
const CATALOG = [
    ex(BENCH, "barbell_bench_press", "horizontal_push"),
    ex(SQUAT, "barbell_back_squat", "squat"),
    ex(CURL, "dumbbell_curl", "isolation"),
    ex(CUSTOM, null, "horizontal_push", true),
];

const TEXT =
    "Entrené 4 años, lo dejé hace 8 meses por una lesión de hombro; hacía press banca con 90 kg a 5";
const EXAMPLE = {
    training_years: 4,
    situation: "returning_injury",
    injured_area: "shoulder",
    pre_break_bests: [{ exercise_id: BENCH, weight_kg: 90, reps: 5 }],
    known_maintenance_kcal: null,
    known_cut_kcal: null,
};
const answer = (over: Record<string, unknown> = {}) =>
    JSON.stringify({ ...EXAMPLE, ...over });

function setup(
    o: { texts?: string[]; spent?: number; globalTotal?: number } = {},
) {
    const texts = o.texts ?? [answer()];
    let calls = 0;
    const prompts: string[] = [];
    const records: Parameters<CoachDeps["recordUsage"]>[0][] = [];
    const client: AnthropicClient = {
        stream: async function* (request) {
            prompts.push(request.prompt);
            const text = texts[Math.min(calls, texts.length - 1)];
            calls++;
            yield { type: "delta", text };
            yield {
                type: "usage",
                usage: { ...NO_USAGE, inputTokens: 900, outputTokens: 250 },
            };
            yield { type: "done" };
        },
    };
    const unexpected = (name: string) => () =>
        Promise.reject(new Error(`unexpected ${name}`));
    const deps: CoachDeps = {
        authenticate: (t) => Promise.resolve(t === "good" ? "user-1" : null),
        userSpent: () => Promise.resolve(o.spent ?? 0),
        globalSpent: () => Promise.resolve(o.globalTotal ?? 0),
        recordUsage: (r) => {
            records.push(r);
            return Promise.resolve();
        },
        loadCatalog: () => Promise.resolve(CATALOG),
        loadPlan: unexpected("loadPlan"),
        attachBlock: unexpected("attachBlock"),
        loadInputs: unexpected("loadInputs"),
        loadContext: unexpected("loadContext"),
        savePlan: unexpected("savePlan"),
        client,
        now: () => NOW,
    };
    return {
        handler: createCoachHandler(deps),
        records,
        prompts,
        modelCalls: () => calls,
    };
}

const post = (input: unknown) =>
    new Request("http://x/coach", {
        method: "POST",
        headers: { authorization: "Bearer good" },
        body: JSON.stringify({ kind: "intake_extract", input }),
    });

const fieldsOf = async (res: Response) => {
    const text = await res.text();
    const m = text.match(/event: delta\ndata: (.*)\n\n/);
    assert(m, `a delta frame in: ${text}`);
    return JSON.parse(JSON.parse(m![1]).text).fields;
};

Deno.test("the kind uses Sonnet and is available", () => {
    assertEquals(KINDS.intake_extract.model, "sonnet");
    assertEquals(KINDS.intake_extract.available, true);
});

Deno.test("the US-81 example fills years, situation, area and the bench press, with no calories", async () => {
    const { handler, records } = setup();
    const res = await handler(post({ free_text: TEXT }));
    assertEquals(res.status, 200);
    assertEquals(await fieldsOf(res), EXAMPLE);
    assertEquals(records.length, 1);
});

Deno.test("the prompt carries the text and only the non-custom main lifts", async () => {
    const { handler, prompts } = setup();
    await (await handler(post({ free_text: TEXT }))).text();
    const p = JSON.parse(prompts[0]);
    assertEquals(p.free_text, TEXT);
    assertEquals(p.main_lifts.map((l: { id: string }) => l.id), [BENCH, SQUAT]);
});

Deno.test("an unknown situation or area becomes null and the other fields are kept", async () => {
    const { handler } = setup({
        texts: [answer({ situation: "retired", injured_area: "neck" })],
    });
    const f = await fieldsOf(await handler(post({ free_text: TEXT })));
    assertEquals(f.situation, null);
    assertEquals(f.injured_area, null);
    assertEquals(f.training_years, 4);
    assertEquals(f.pre_break_bests.length, 1);
});

Deno.test("out-of-range years are null", async () => {
    for (const bad of [-1, 60.1, "4", null]) {
        const { handler } = setup({ texts: [answer({ training_years: bad })] });
        const f = await fieldsOf(await handler(post({ free_text: TEXT })));
        assertEquals(f.training_years, null);
        assertEquals(f.situation, "returning_injury");
    }
});

Deno.test("lifts outside the server list, with bad numbers or duplicated are dropped one by one", async () => {
    const bests = [
        { exercise_id: CURL, weight_kg: 20, reps: 10 }, // isolation: not a main lift
        { exercise_id: CUSTOM, weight_kg: 50, reps: 5 }, // custom
        {
            exercise_id: "55555555-5555-4555-8555-555555555555",
            weight_kg: 50,
            reps: 5,
        }, // unknown
        { exercise_id: SQUAT, weight_kg: 4, reps: 5 }, // weight under 5
        { exercise_id: SQUAT, weight_kg: 501, reps: 5 },
        { exercise_id: SQUAT, weight_kg: 100, reps: 0 },
        { exercise_id: SQUAT, weight_kg: 100, reps: 31 },
        { exercise_id: SQUAT, weight_kg: 100, reps: 2.5 },
        { exercise_id: BENCH, weight_kg: 90, reps: 5 },
        { exercise_id: BENCH, weight_kg: 95, reps: 3 }, // duplicate lift: the first wins
    ];
    const { handler } = setup({ texts: [answer({ pre_break_bests: bests })] });
    const f = await fieldsOf(await handler(post({ free_text: TEXT })));
    assertEquals(f.pre_break_bests, [{
        exercise_id: BENCH,
        weight_kg: 90,
        reps: 5,
    }]);
});

Deno.test("boundary lifts 5 kg x 1 and 500 kg x 30 are kept", async () => {
    const bests = [
        { exercise_id: BENCH, weight_kg: 5, reps: 1 },
        { exercise_id: SQUAT, weight_kg: 500, reps: 30 },
    ];
    const { handler } = setup({ texts: [answer({ pre_break_bests: bests })] });
    const f = await fieldsOf(await handler(post({ free_text: TEXT })));
    assertEquals(f.pre_break_bests, bests);
});

Deno.test("a calorie figure whose digits are in the text is kept, with any separator", async () => {
    for (
        const text of [
            "Mi mantenimiento son 2400 kcal",
            "Mi mantenimiento son 2.400 kcal",
            "Mi mantenimiento son 2,400 kcal",
            "Mi mantenimiento son 2 400 kcal",
        ]
    ) {
        const { handler } = setup({
            texts: [answer({ known_maintenance_kcal: 2400 })],
        });
        const f = await fieldsOf(await handler(post({ free_text: text })));
        assertEquals(f.known_maintenance_kcal, 2400, text);
    }
});

Deno.test("a calorie figure that is not in the text is null (no hallucinated number reaches the form)", async () => {
    const { handler } = setup({
        texts: [answer({ known_maintenance_kcal: 2500, known_cut_kcal: 1900 })],
    });
    const f = await fieldsOf(
        await handler(post({ free_text: "Mantenimiento unos 2400 kcal" })),
    );
    assertEquals(f.known_maintenance_kcal, null);
    assertEquals(f.known_cut_kcal, null);
});

Deno.test("calories out of range are null even when written in the text", async () => {
    const { handler } = setup({
        texts: [answer({ known_maintenance_kcal: 900, known_cut_kcal: 5200 })],
    });
    const f = await fieldsOf(
        await handler(
            post({ free_text: "Mantenimiento 900 y definición 5200" }),
        ),
    );
    assertEquals(f.known_maintenance_kcal, null);
    assertEquals(f.known_cut_kcal, null);
});

Deno.test("null calories stay null", async () => {
    const { handler } = setup();
    const f = await fieldsOf(await handler(post({ free_text: TEXT })));
    assertEquals(f.known_maintenance_kcal, null);
    assertEquals(f.known_cut_kcal, null);
});

Deno.test("fields the user already filled are not overwritten", async () => {
    const { handler } = setup();
    const f = await fieldsOf(
        await handler(post({
            free_text: TEXT,
            fields: {
                training_years: 2,
                injured_area: "knee",
                pre_break_bests: [],
            },
        })),
    );
    assertEquals(f.training_years, null);
    assertEquals(f.injured_area, null);
    assertEquals(f.situation, "returning_injury");
    assertEquals(f.pre_break_bests.length, 1); // an empty list is "left empty"
});

Deno.test("malformed JSON is retried once and then coach_invalid_output", async () => {
    const { handler, modelCalls, records } = setup({ texts: ["not json"] });
    const res = await handler(post({ free_text: TEXT }));
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(modelCalls(), 2);
    assertEquals(records.length, 1);
});

Deno.test("an answer missing keys is invalid, and a valid second answer after an invalid first is accepted", async () => {
    const { handler, modelCalls } = setup({
        texts: ['{"training_years": 4}', answer()],
    });
    const res = await handler(post({ free_text: TEXT }));
    assertEquals(res.status, 200);
    assertEquals((await fieldsOf(res)).training_years, 4);
    assertEquals(modelCalls(), 2);
});

Deno.test("input validation: empty, too long, extra keys and wrong types are INVALID_INPUT before any model call", async () => {
    const cases: unknown[] = [
        { free_text: "   " },
        { free_text: "a".repeat(501) },
        { free_text: TEXT, extra: 1 },
        { free_text: 5 },
        { free_text: TEXT, fields: "x" },
        {},
        "text",
    ];
    for (const input of cases) {
        const { handler, modelCalls } = setup();
        const res = await handler(post(input));
        assertEquals(res.status, 400, JSON.stringify(input));
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(modelCalls(), 0);
    }
});

Deno.test("500 characters (counted as characters, not bytes) are accepted", () => {
    assertEquals(
        parseInput({ free_text: "é".repeat(500) }).freeText.length,
        500,
    );
    assertEquals(parseInput({ free_text: `  ${TEXT}  ` }).freeText, TEXT);
});

Deno.test("over the 3 EUR user limit is coach_limit_reached before any model call; 2,99 is allowed", async () => {
    const refused = setup({ spent: 3 });
    const res = await refused.handler(post({ free_text: TEXT }));
    assertEquals(res.status, 429);
    const error = (await res.json()).error;
    assertEquals(error.code, "coach_limit_reached");
    assertEquals(error.scope, "user");
    assertEquals(refused.modelCalls(), 0);

    const allowed = setup({ spent: 2.99 });
    assertEquals(
        (await allowed.handler(post({ free_text: TEXT }))).status,
        200,
    );
});

Deno.test("the output schema asks for exactly the six fields", () => {
    assertEquals(
        (INTAKE_OUTPUT_SCHEMA.required as string[]).sort(),
        [
            "injured_area",
            "known_cut_kcal",
            "known_maintenance_kcal",
            "pre_break_bests",
            "situation",
            "training_years",
        ],
    );
});
