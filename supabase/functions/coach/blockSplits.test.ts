import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    bodyOf,
    harness,
    type HarnessOptions,
    post,
    SECRET,
    summary,
} from "./blockHarness.ts";
import {
    MAX_OPTIONS,
    parseInput,
    prepare,
    validateSplits,
} from "./blockSplits.ts";
import { CoachInputError } from "./context.ts";
import { MUSCLE_GROUPS } from "./schemas.ts";
import { KINDS } from "./router.ts";
import { blockInputs, MISSING } from "./inputsFixtures.ts";
import { FREE_TEXT_KEY } from "./planBlock.ts";

const REASON = "Reparto equilibrado para tu objetivo y tu tiempo.";
const GROUPS = ["chest", "back", "shoulders", "quads", "hamstrings", "glutes"];

const option = (
    id: string,
    days: number,
    over: Record<string, unknown> = {},
) => ({
    id,
    name: `Split ${id}`,
    recommended: id === "a",
    reason: REASON,
    sessions: Array.from({ length: days }, (_, i) => ({
        name: `Sesión ${i + 1}`,
        focus: GROUPS.slice(0, 3),
    })),
    ...over,
});
const answerOf = (options: unknown[]) => JSON.stringify({ options });

const setup = (o: Partial<HarnessOptions> & { answers?: string[] } = {}) =>
    harness({
        kind: "block_splits",
        model: "sonnet",
        prepare,
        answer: (_r, n) =>
            (o.answers ?? [answerOf([option("a", 4), option("b", 4)])])[
                Math.min(n, (o.answers ?? [""]).length) - 1
            ],
        ...o,
    });

const run = (t: ReturnType<typeof setup>) =>
    t.handler(post("block_splits", { today: "2026-10-30" }));

Deno.test("block_splits is registered with Sonnet", () => {
    assertEquals(KINDS.block_splits.model, "sonnet");
    assertEquals(KINDS.block_splits.available, true);
});

Deno.test("parseInput takes today and nothing else", () => {
    assertEquals(parseInput({ today: "2026-10-30" }), { today: "2026-10-30" });
    for (
        const bad of [
            {},
            { today: "2026-10-30", plan_id: "x" },
            { today: "2026-02-31" },
            { today: "30/10/2026" },
            null,
        ]
    ) assertThrows(() => parseInput(bad), CoachInputError);
});

Deno.test("2 options for 4 days: streamed whole, one recommended, nothing stored", async () => {
    const t = setup();
    const res = await run(t);
    assertEquals(res.status, 200);
    const body = await bodyOf(res);
    assertEquals(body.options.length, 2);
    assertEquals(
        body.options.filter((o: { recommended: boolean }) => o.recommended)
            .length,
        1,
    );
    assertEquals(body.options[0].sessions.length, 4);
    assertEquals(t.calls(), 1);
    assertEquals(t.attached.length, 0);
});

Deno.test("the prompt carries the days and minutes, no personal data, and the free text under its key", async () => {
    const base = summary({ days_per_week: 3, session_minutes: 45 }) as Record<
        string,
        unknown
    >;
    const t = setup({
        answers: [answerOf([option("a", 3), option("b", 3)])],
        summary: {
            ...base,
            free_text: [{
                origin: "intake",
                date: "2026-10-30",
                text: "PALABRAS",
            }],
        },
    });
    await (await run(t)).text();
    const prompt = JSON.parse(t.requests[0].prompt);
    assertEquals(prompt.days_per_week, 3);
    assertEquals(prompt.session_minutes, 45);
    assertEquals(prompt[FREE_TEXT_KEY].length, 1);
    assert(!t.requests[0].prompt.includes(SECRET));
    assert(!t.requests[0].prompt.includes("weight_kg"));
    assert(t.requests[0].system!.includes(`"${FREE_TEXT_KEY}"`));
});

Deno.test("3 options are fine; 1 and 4 are not", () => {
    const days = 4;
    const make = (n: number) =>
        answerOf(
            ["a", "b", "c", "d"].slice(0, n).map((id) => option(id, days)),
        );
    assertEquals(validateSplits(make(3), days).failures, []);
    assertEquals(MAX_OPTIONS, 3);
    for (const n of [1, 4]) {
        const r = validateSplits(make(n), days);
        assertEquals(r.options, null);
        assertEquals(r.failures[0].code, "option_count");
    }
});

Deno.test("two recommended or none, a wrong session count, repeated ids or names, bad groups are refused", () => {
    const codes = (opts: unknown[]) =>
        validateSplits(answerOf(opts), 4).failures.map((f) => f.code);
    assertEquals(
        codes([option("a", 4), option("b", 4, { recommended: true })]),
        ["recommended"],
    );
    assertEquals(
        codes([option("a", 4, { recommended: false }), option("b", 4)])
            .includes("recommended"),
        true,
    );
    assertEquals(codes([option("a", 4), option("b", 3)]), ["session_count"]);
    assertEquals(
        codes([
            option("a", 4),
            option("a", 4, { recommended: false, name: "Otro" }),
        ]),
        ["id"],
    );
    assertEquals(
        codes([
            option("a", 4),
            option("b", 4, { name: "split a", recommended: false }),
        ]),
        ["name_repeated"],
    );
    assertEquals(
        codes([
            option("a", 4),
            option("b", 4, { reason: "corta", recommended: false }),
        ]),
        ["reason"],
    );
    assertEquals(
        codes([
            option("a", 4),
            option("b", 4, { reason: "r".repeat(2000), recommended: false }),
        ]),
        [],
        "T-212: a long split reason has no upper cap",
    );
    const badFocus = option("b", 4, { recommended: false });
    badFocus.sessions[0].focus = ["full_body"];
    assertEquals(codes([option("a", 4), badFocus]), ["session"]);
    const empty = option("b", 4, { recommended: false });
    empty.sessions[1].focus = [];
    assertEquals(codes([option("a", 4), empty]), ["session"]);
    assertEquals(validateSplits("not json", 4).failures, [{ code: "shape" }]);
});

Deno.test("adductors is a muscle group the schema knows but a split session never targets it", () => {
    assert(MUSCLE_GROUPS.includes("adductors"));
    const adductors = option("b", 4, { recommended: false });
    adductors.sessions[0].focus = ["adductors"];
    assertEquals(
        validateSplits(answerOf([option("a", 4), adductors]), 4).failures.map((
            f,
        ) => f.code),
        ["session"],
    );
});

Deno.test("an invalid first answer is retried once with the failure codes in the prompt, usage summed", async () => {
    const t = setup({
        answers: [
            answerOf([option("a", 4)]),
            answerOf([option("a", 4), option("b", 4)]),
        ],
    });
    const res = await run(t);
    assertEquals(res.status, 200);
    await res.text();
    assertEquals(t.calls(), 2);
    assert(!t.requests[0].prompt.includes("option_count"));
    assert(t.requests[1].prompt.includes("option_count"));
    assertEquals(t.records.length, 1);
    assertEquals(t.records[0].inputTokens, 2000);
});

Deno.test("two invalid answers are 502 coach_invalid_output and both are charged", async () => {
    const t = setup({ answers: ["nope", "nope"] });
    const res = await run(t);
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(t.records[0].inputTokens, 2000);
});

Deno.test("missing data is 422 coach_missing_data and no model call", async () => {
    const t = setup({ inputs: blockInputs({ place: MISSING }) });
    const res = await run(t);
    assertEquals(res.status, 422);
    assertEquals((await res.json()).error.code, "coach_missing_data");
    assertEquals(t.calls(), 0);
});

Deno.test("days unset: the coach's chosen frequency is the session count to ask for", async () => {
    const t = setup({
        tp: { days_per_week: null, session_minutes: 45 },
        answers: [answerOf([option("a", 3), option("b", 3)])],
    });
    const res = await run(t);
    assertEquals(res.status, 200);
    assertEquals(JSON.parse(t.requests[0].prompt).days_per_week, 3);
});
