import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    bodyOf,
    harness,
    type HarnessOptions,
    PLAN_ID,
    post,
    REVIEW_ID,
    SECRET,
} from "./blockHarness.ts";
import {
    fakeBlock,
    type FakeInput,
    fullCatalog,
    splitFor,
} from "./blockFixtures.ts";
import {
    isFolderStart,
    parseFolderStartInput,
    prepareBlock,
} from "./blockFromFolder.ts";
import { CoachInputError } from "./context.ts";
import { goodFolder, keyOfPattern, storedReview } from "./folderFixtures.ts";
import { KINDS } from "./router.ts";
import type { MuscleGroup } from "./schemas.ts";

const catalog = fullCatalog();
const LATERAL = keyOfPattern(catalog, "shoulders", "shoulder_abduction");
const REASON = "Añade el deltoides lateral que le falta a la carpeta.";
const ADD_LATERAL = {
    id: "c1",
    op: "add_exercise",
    routine_index: 1,
    exercise_key: LATERAL,
    sets: [{
        n: 2,
        type: "working",
        rep_min: 10,
        rep_max: 15,
        rpe: 8,
        rest_seconds: 90,
    }],
    reason: REASON,
};

const withoutLateral = (days = 4) =>
    goodFolder(catalog, {
        days,
        mutate: (b, byKey) => {
            for (const s of b.sessions) {
                s.exercises = s.exercises.filter((x) =>
                    byKey.get(x.exercise_key)!.movement_pattern !==
                        "shoulder_abduction"
                );
            }
        },
    });

const setup = async (
    o: Partial<HarnessOptions> & {
        reviewOver?: Partial<Parameters<typeof storedReview>[0]>;
    } = {},
) => {
    const { reviewOver, ...rest } = o;
    return harness({
        kind: "plan_block",
        model: "opus",
        prepare: prepareBlock,
        deterministic: KINDS.plan_block.deterministic,
        answer: () => {
            throw new Error("the folder start must not call the model");
        },
        catalog,
        folder: goodFolder(catalog),
        review: await storedReview({
            planId: PLAN_ID,
            reviewId: REVIEW_ID,
            ...reviewOver,
        }),
        ...rest,
    });
};

const input = (over: Record<string, unknown> = {}) => ({
    today: "2026-10-30",
    plan_id: PLAN_ID,
    start: "folder",
    review_id: REVIEW_ID,
    accepted_change_ids: [],
    ...over,
});
const run = (t: Awaited<ReturnType<typeof setup>>, i: unknown = input()) =>
    t.handler(post("plan_block", i));

type Ex = { role: string; exercise_key: string; reason: string };

// --- the request -----------------------------------------------------------------------------------------------

Deno.test("the folder start has its own request shape and the scratch shape is not it", () => {
    assertEquals(parseFolderStartInput(input()).accepted, []);
    assertEquals(
        parseFolderStartInput(
            input({ accepted_change_ids: ["c2", "c2", "c1"] }),
        )
            .accepted,
        ["c2", "c1"],
    );
    for (
        const bad of [
            input({ extra: 1 }),
            input({ review_id: "nope" }),
            input({ accepted_change_ids: "c1" }),
            input({ accepted_change_ids: [1] }),
            input({ accepted_change_ids: ["x1"] }),
            input({ today: "2026-02-31" }),
            { today: "2026-10-30", plan_id: PLAN_ID, start: "folder" },
            null,
        ]
    ) assertThrows(() => parseFolderStartInput(bad), CoachInputError);
});

Deno.test("only start folder is deterministic, and the registered kind says so", () => {
    assert(isFolderStart({ start: "folder" }));
    assert(!isFolderStart({ start: "scratch" }));
    assert(!isFolderStart({ today: "2026-10-30" }));
    assert(!isFolderStart(null));
    const flag = KINDS.plan_block.deterministic;
    assertEquals(typeof flag, "function");
    assertEquals(
        (flag as (i: unknown) => boolean)({ start: "folder" }),
        true,
    );
    assertEquals(
        (flag as (i: unknown) => boolean)({ split: {} }),
        false,
    );
    assertEquals(KINDS.plan_recompute.deterministic, true);
});

// --- a block from a folder that passes -------------------------------------------------------------------------

Deno.test("a passing folder becomes the block: cardio appended, attached, no model call, no usage, no budget", async () => {
    const t = await setup({ spent: 1000 });
    const res = await run(t);
    assertEquals(res.status, 200);
    const body = await bodyOf(res);
    assertEquals(t.calls(), 0);
    assertEquals(t.records.length, 0);
    assertEquals(t.attached.length, 1);
    assertEquals(t.attached[0].planId, PLAN_ID);
    assertEquals(body.plan_id, PLAN_ID);
    assertEquals(body.sessions.length, 4);
    assertEquals(body.training.cardio.per_routine_min, [40, 40, 35, 35]);
    for (const s of body.sessions) {
        const last = s.exercises[s.exercises.length - 1] as Ex;
        assertEquals(last.role, "cardio");
        assert(
            s.exercises.slice(0, -1).every((e: Ex) => e.role !== "cardio"),
        );
    }
    assert(body.split_reason.includes("Mi carpeta"));
    assertEquals(
        body.changes_vs_last_block,
        "Sin cambios respecto a tu carpeta.",
    );
    assertEquals(t.attached[0].method.model, "folder");
    assert(!JSON.stringify(t.attached[0]).includes(SECRET.toLowerCase()));
});

Deno.test("3 days of 150 weekly cardio minutes give 50/50/50", async () => {
    const fullBody = {
        name: "Cuerpo completo",
        sessions: ["A", "B", "C"].map((n) => ({
            name: `Cuerpo ${n}`,
            focus: [
                "chest",
                "back",
                "shoulders",
                "quads",
                "hamstrings",
                "glutes",
            ] as MuscleGroup[],
        })),
    };
    const t = await setup({
        tp: { days_per_week: 3, session_minutes: 90 },
        folder: goodFolder(catalog, {
            days: 3,
            split: fullBody,
            isoSets: 1,
            // The hinges already give the glutes 18 weekly sets: the glute isolation would pass the 20 cap.
            mutate: (b, byKey) => {
                for (const s of b.sessions) {
                    s.exercises = s.exercises.filter((x) => {
                        const row = byKey.get(x.exercise_key)!;
                        return !(row.muscle_group === "glutes" &&
                            row.movement_pattern === "isolation");
                    });
                }
            },
        }),
        reviewOver: { days: 3, minutes: 90 },
    });
    const res = await run(t);
    assertEquals(res.status, 200);
    assertEquals((await bodyOf(res)).training.cardio.per_routine_min, [
        50,
        50,
        50,
    ]);
});

Deno.test("the exercise reason is the user's own note when it fits, else a fixed line from the folder", async () => {
    const folder = goodFolder(catalog);
    folder.routines[0].exercises[0].notes = "Mi nota de técnica favorita.";
    const t = await setup({ folder });
    const body = await bodyOf(await run(t));
    assertEquals(
        body.sessions[0].exercises[0].reason,
        "Mi nota de técnica favorita.",
    );
    assertEquals(
        body.sessions[0].exercises[1].reason,
        "Tu ejercicio de la carpeta «Mi carpeta»",
    );
});

// --- a folder that needs changes -------------------------------------------------------------------------------

Deno.test("a folder with a hard failure is refused with coach_block_needs_changes and nothing is stored", async () => {
    const t = await setup({ folder: withoutLateral() });
    const res = await run(t);
    assertEquals(res.status, 422);
    const error = (await res.json()).error;
    assertEquals(error.code, "coach_block_needs_changes");
    assert(
        error.failures.some((f: { code: string }) =>
            f.code === "h3_side_delta"
        ),
    );
    assertEquals(t.attached.length, 0);
    assertEquals(t.calls(), 0);
    assertEquals(t.records.length, 0);
});

Deno.test("accepting the change that adds the missing exercise makes the block pass", async () => {
    const t = await setup({
        folder: withoutLateral(),
        reviewOver: { changes: [ADD_LATERAL] },
    });
    const refused = await run(t);
    assertEquals(refused.status, 422);

    const ok = await run(t, input({ accepted_change_ids: ["c1"] }));
    assertEquals(ok.status, 200);
    const body = await bodyOf(ok);
    assertEquals(t.attached.length, 1);
    assert(body.changes_vs_last_block.includes("deltoides lateral"));
    const routine1 = body.sessions[0].exercises as Ex[];
    assert(routine1.some((e) => e.exercise_key === LATERAL));
});

Deno.test("the accepted change that breaks a rule is refused, not stored", async () => {
    const t = await setup({
        reviewOver: {
            changes: [{
                id: "c1",
                op: "set_rpe",
                routine_index: 1,
                position: 1,
                rpe: 6,
                reason: REASON,
            }],
        },
    });
    const res = await run(t, input({ accepted_change_ids: ["c1"] }));
    assertEquals(res.status, 422);
    assert(
        (await res.json()).error.failures.some((f: { code: string }) =>
            f.code === "h5_rpe"
        ),
    );
    assertEquals(t.attached.length, 0);
});

// --- a review that cannot be used ------------------------------------------------------------------------------

const refusals: [
    string,
    Partial<HarnessOptions> & {
        reviewOver?: Partial<Parameters<typeof storedReview>[0]>;
    },
][] = [
    ["a stale folder", { reviewOver: { version: "older" } }],
    ["a review of purpose review", { reviewOver: { purpose: "review" } }],
    ["a review of another plan", {
        reviewOver: { planId: "99999999-9999-4999-8999-999999999999" },
    }],
    ["changed preferences", { reviewOver: { minutes: 45 } }],
    ["a missing review", { review: null }],
    ["a folder that is gone", { folder: null }],
    ["a plan that already has its block", {
        plan: { status: "accepted", training_saved: true },
    }],
];
for (const [name, over] of refusals) {
    Deno.test(`the folder start refuses ${name} as INVALID_INPUT`, async () => {
        const t = await setup(over);
        const res = await run(t);
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
        assertEquals(t.attached.length, 0);
        assertEquals(t.calls(), 0);
    });
}

Deno.test("an unknown change id and two accepted changes on one exercise are INVALID_INPUT", async () => {
    const changes = [
        {
            id: "c1",
            op: "set_rpe",
            routine_index: 1,
            position: 1,
            rpe: 9,
            reason: REASON,
            conflicts_with: ["c2"],
        },
        {
            id: "c2",
            op: "remove_exercise",
            routine_index: 1,
            position: 1,
            reason: REASON,
            conflicts_with: ["c1"],
        },
    ];
    const t = await setup({ reviewOver: { changes } });
    for (const ids of [["c9"], ["c1", "c2"]]) {
        const res = await run(t, input({ accepted_change_ids: ids }));
        assertEquals(res.status, 400);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
    }
    assertEquals(t.attached.length, 0);
});

// --- the scratch request is unchanged --------------------------------------------------------------------------

const promptOf = (request: { prompt: string }) =>
    JSON.parse(request.prompt.split("\n\n")[0]) as FakeInput;

Deno.test("a scratch plan_block, with or without start scratch, still calls the model and is budget-checked", async () => {
    for (const extra of [{}, { start: "scratch" }]) {
        const t = await setup({
            answer: (request) => JSON.stringify(fakeBlock(promptOf(request))),
            review: undefined,
            folder: undefined,
        });
        const scratch = {
            today: "2026-10-30",
            plan_id: PLAN_ID,
            split: splitFor(4),
            ...extra,
        };
        const res = await run(t, scratch);
        assertEquals(res.status, 200);
        assertEquals(t.calls(), 1);
        assertEquals(t.records.length, 1);
        assertEquals(t.attached.length, 1);
    }
    const limited = await setup({ spent: 1000, review: undefined });
    const res = await run(limited, {
        today: "2026-10-30",
        plan_id: PLAN_ID,
        split: splitFor(4),
    });
    assertEquals(res.status, 429);
});
