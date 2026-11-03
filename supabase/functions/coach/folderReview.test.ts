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
import { ex, fullCatalog } from "./blockFixtures.ts";
import { CoachInputError } from "./context.ts";
import {
    FOLDER_ID,
    FOLDER_VERSION,
    goodFolder,
    keyOfPattern,
} from "./folderFixtures.ts";
import {
    faltaOf,
    inputHash,
    makePrepare,
    maxTokensFor,
    numberChanges,
    OUTPUT_TOKENS_MAX,
    parseInput,
    validateReview,
} from "./folderReview.ts";
import { candidateKey } from "./candidates.ts";

const catalog = fullCatalog();
const LATERAL = keyOfPattern(catalog, "shoulders", "shoulder_abduction");
const CHEST_MAIN_2 = keyOfPattern(catalog, "chest", "horizontal_push", 1);
const CHEST_MAIN_3 = keyOfPattern(catalog, "chest", "horizontal_push", 2);
const FOREARMS = keyOfPattern(catalog, "forearms", "isolation");
const SET = {
    n: 2,
    type: "working",
    rep_min: 10,
    rep_max: 15,
    rpe: 8,
    rest_seconds: 90,
};
const REASON = "Este cambio ayuda a cumplir tus reglas de entrenamiento.";

const review = (over: Record<string, unknown> = {}) =>
    JSON.stringify({
        message: "La carpeta se ha revisado con tus reglas.",
        comments: [],
        changes: [],
        ...over,
    });
const change = (over: Record<string, unknown>) => ({
    routine_index: 1,
    reason: REASON,
    ...over,
});

const setup = (o: Partial<HarnessOptions> = {}) =>
    harness({
        kind: "folder_review",
        model: "sonnet",
        prepare: makePrepare(),
        answer: () => review(),
        catalog,
        folder: goodFolder(catalog),
        ...o,
    });

const input = (over: Record<string, unknown> = {}) => ({
    today: "2026-10-30",
    folder_id: FOLDER_ID,
    purpose: "review",
    ...over,
});
const run = (t: ReturnType<typeof setup>, i: unknown = input()) =>
    t.handler(post("folder_review", i));

/** A folder without the side-delta exercise: the checker reports h3_side_delta. */
const withoutLateral = () =>
    goodFolder(catalog, {
        mutate: (b, byKey) => {
            for (const s of b.sessions) {
                s.exercises = s.exercises.filter((x) =>
                    byKey.get(x.exercise_key)!.movement_pattern !==
                        "shoulder_abduction"
                );
            }
        },
    });

// --- input -----------------------------------------------------------------------------------------------------

Deno.test("parseInput takes today, a folder, a purpose and a plan only for a block", () => {
    assertEquals(parseInput(input()).purpose, "review");
    assertEquals(parseInput(input()).planId, null);
    assertEquals(
        parseInput(input({ purpose: "block", plan_id: PLAN_ID })).planId,
        PLAN_ID,
    );
    for (
        const bad of [
            input({ purpose: "block" }),
            input({ plan_id: PLAN_ID }),
            input({ purpose: "other" }),
            input({ folder_id: "nope" }),
            input({ today: "2026-02-31" }),
            input({ extra: 1 }),
            null,
        ]
    ) assertThrows(() => parseInput(bad), CoachInputError);
});

// --- the review ------------------------------------------------------------------------------------------------

Deno.test("a good folder has no Falta items and the review is stored without the folder version in the answer", async () => {
    const t = setup({
        answer: () =>
            review({
                comments: [{
                    text: "Buena elección de ejercicios principales.",
                }],
            }),
    });
    const res = await run(t);
    assertEquals(res.status, 200);
    const body = await bodyOf(res);
    assertEquals(body.falta, []);
    assertEquals(body.changes, []);
    assertEquals(body.review_id, REVIEW_ID);
    assertEquals(body.purpose, "review");
    assertEquals(body.comments.length, 1);
    assertEquals("folder_version" in body, false);
    assertEquals(t.reviews.length, 1);
    assertEquals(t.reviews[0].folderId, FOLDER_ID);
    assertEquals(t.reviews[0].purpose, "review");
    assertEquals(t.reviews[0].planId, null);
    assertEquals(
        (t.reviews[0].result as { folder_version: string }).folder_version,
        FOLDER_VERSION,
    );
    assertEquals(
        t.reviews[0].inputHash,
        await inputHash(FOLDER_VERSION, 4, 60, "muscle"),
    );
    assertEquals(t.reviews[0].inputHash.length, 64);
});

Deno.test("a hard failure of the checker is a Falta item with its data", async () => {
    const t = setup({ folder: withoutLateral() });
    const body = await bodyOf(await run(t));
    const h3 = body.falta.filter((f: { code: string }) =>
        f.code === "h3_side_delta"
    );
    assertEquals(h3.length, 1);
    assertEquals(h3[0].data, {});
});

Deno.test("the Falta items of five sets with a bad RPE are one item per exercise", () => {
    const failures = [1, 2, 3, 4, 5].map((set) => ({
        code: "h5_rpe" as const,
        data: { routine: 1, exercise_key: "k", set, rpe: 0 },
    }));
    assertEquals(faltaOf(failures).length, 1);
    assertEquals(faltaOf(failures)[0], {
        code: "h5_rpe",
        routine_index: 1,
        exercise_key: "k",
        data: { set: 1, rpe: 0 },
    });
});

Deno.test("1 day of 45 minutes gives the below_recommended note and no volume Falta", async () => {
    const t = setup({
        tp: { days_per_week: 1, session_minutes: 45 },
        folder: goodFolder(catalog, { days: 1 }),
    });
    const body = await bodyOf(await run(t));
    assert(body.notes.includes("below_recommended"));
    assertEquals(
        body.falta.filter((f: { code: string }) => f.code.startsWith("h1_")),
        [],
    );
});

Deno.test("a change that fixes H3 is kept with fixes and a server id", async () => {
    const t = setup({
        folder: withoutLateral(),
        answer: () =>
            review({
                changes: [change({
                    op: "add_exercise",
                    exercise_key: LATERAL,
                    sets: [SET],
                })],
            }),
    });
    const body = await bodyOf(await run(t));
    assertEquals(body.changes.length, 1);
    assertEquals(body.changes[0].id, "c1");
    assertEquals(body.changes[0].op, "add_exercise");
    // The side raise also restores the shoulder volume: the first Falta item it resolves names it.
    assert(
        ["h1_volume_low", "h3_side_delta"].includes(body.changes[0].fixes),
    );
});

Deno.test("a change that adds a hard failure is dropped, the others stay", async () => {
    const t = setup({
        answer: () =>
            review({
                changes: [
                    change({
                        op: "add_exercise",
                        exercise_key: CHEST_MAIN_2,
                        sets: [{ ...SET, n: 10, rest_seconds: 150 }],
                    }),
                    change({ op: "set_rpe", position: 1, rpe: 9 }),
                ],
            }),
    });
    const body = await bodyOf(await run(t));
    assertEquals(body.changes.length, 1);
    assertEquals(body.changes[0].id, "c1");
    assertEquals(body.changes[0].op, "set_rpe");
});

Deno.test("changes out of the rules are dropped: RPE 6, a rep range outside 6-20, a missing target, a no-op", async () => {
    const t = setup({
        answer: () =>
            review({
                changes: [
                    change({ op: "set_rpe", position: 1, rpe: 6 }),
                    change({
                        op: "set_rep_range",
                        position: 1,
                        rep_min: 3,
                        rep_max: 5,
                    }),
                    change({ op: "set_rpe", position: 99, rpe: 9 }),
                    change({ op: "set_rpe", position: 1, rpe: 8 }),
                    change({ op: "set_sets", position: 1, n: 11 }),
                    change({
                        op: "set_rpe",
                        routine_index: 9,
                        position: 1,
                        rpe: 9,
                    }),
                ],
            }),
    });
    const body = await bodyOf(await run(t));
    assertEquals(body.changes, []);
});

Deno.test("all seven ops are applied when they respect the rules", async () => {
    const t = setup({
        answer: () =>
            review({
                changes: [
                    change({ op: "set_rpe", position: 1, rpe: 9 }),
                    change({ op: "set_rest", position: 2, rest_seconds: 130 }),
                    change({
                        op: "set_rep_range",
                        routine_index: 2,
                        position: 1,
                        rep_min: 8,
                        rep_max: 12,
                    }),
                    change({
                        op: "set_sets",
                        routine_index: 2,
                        position: 4,
                        n: 3,
                    }),
                    change({
                        op: "remove_exercise",
                        routine_index: 2,
                        position: 8,
                    }),
                    change({
                        op: "replace_exercise",
                        routine_index: 3,
                        position: 1,
                        exercise_key: CHEST_MAIN_3,
                    }),
                    change({
                        op: "add_exercise",
                        routine_index: 3,
                        exercise_key: FOREARMS,
                        sets: [{ ...SET, n: 1 }],
                    }),
                ],
            }),
    });
    const body = await bodyOf(await run(t));
    assertEquals(
        body.changes.map((c: { id: string; op: string }) => `${c.id}:${c.op}`),
        [
            "c1:set_rpe",
            "c2:set_rest",
            "c3:set_rep_range",
            "c4:set_sets",
            "c5:remove_exercise",
            "c6:replace_exercise",
            "c7:add_exercise",
        ],
    );
    assertEquals(
        body.changes.some((c: { conflicts_with?: string[] }) =>
            c.conflicts_with
        ),
        false,
    );
});

Deno.test("an excluded exercise and one outside the equipment are never kept as additions", async () => {
    const machine = ex({
        key: "machine-curl",
        equipment: "machine",
        muscle_group: "biceps",
        movement_pattern: "isolation",
    });
    const folder = withoutLateral();
    const excluded = catalog.find((e) => e.key === LATERAL)!;
    const t = setup({
        catalog: [...catalog, machine],
        folder,
        tp: { equipment: ["barbell"] },
        modelInput: {
            folders: [],
            history: [],
            previous_blocks: [],
            favourites: [],
            unresolved_exercise_ids: [excluded.id],
        },
        answer: () =>
            review({
                changes: [
                    change({
                        op: "add_exercise",
                        exercise_key: LATERAL,
                        sets: [SET],
                    }),
                    change({
                        op: "add_exercise",
                        exercise_key: candidateKey(machine),
                        sets: [{ ...SET, n: 1 }],
                    }),
                ],
            }),
    });
    const res = await run(t);
    assertEquals(res.status, 200);
    assertEquals((await bodyOf(res)).changes, []);
});

Deno.test("changes on the same exercise are marked as conflicting", () => {
    const out = numberChanges([
        {
            op: "set_rpe",
            routine_index: 1,
            position: 1,
            rpe: 9,
            reason: REASON,
        },
        {
            op: "remove_exercise",
            routine_index: 1,
            position: 1,
            reason: REASON,
        },
        {
            op: "set_rpe",
            routine_index: 1,
            position: 2,
            rpe: 9,
            reason: REASON,
        },
    ]);
    assertEquals(out.map((c) => c.id), ["c1", "c2", "c3"]);
    assertEquals(out[0].conflicts_with, ["c2"]);
    assertEquals(out[1].conflicts_with, ["c1"]);
    assertEquals(out[2].conflicts_with, undefined);
});

// --- validation and retry --------------------------------------------------------------------------------------

Deno.test("an invented exercise key triggers one retry with the failures, then the review is stored", async () => {
    const t = setup({
        answer: (_r, n) =>
            n === 1
                ? review({
                    changes: [change({
                        op: "add_exercise",
                        exercise_key: "no-such-exercise",
                        sets: [SET],
                    })],
                })
                : review(),
    });
    const res = await run(t);
    assertEquals(res.status, 200);
    assertEquals(t.calls(), 2);
    assert(t.requests[1].prompt.includes("unknown_exercise"));
    assertEquals(t.reviews.length, 1);
});

Deno.test("invalid JSON twice is coach_invalid_output and nothing is stored", async () => {
    const t = setup({ answer: () => "not json" });
    const res = await run(t);
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "coach_invalid_output");
    assertEquals(t.calls(), 2);
    assertEquals(t.reviews.length, 0);
    assertEquals(t.records.length, 1);
});

Deno.test("validateReview refuses unknown ops, extra keys, bad reasons and a comment on a foreign exercise", () => {
    const v = {
        folderKeys: new Set(["a"]),
        catalogKeys: new Set(["a", "b"]),
    };
    const ok = {
        message: "La carpeta se ha revisado con tus reglas.",
        comments: [],
        changes: [],
    };
    const fails = (o: Record<string, unknown>) =>
        validateReview(JSON.stringify({ ...ok, ...o }), v).failures.length > 0;
    assertEquals(fails({}), false);
    assert(fails({ message: "corto" }));
    // T-212: no upper cap on the model's texts.
    assertEquals(fails({ message: "m".repeat(2000) }), false);
    assertEquals(
        fails({ comments: [{ text: "c".repeat(2000), exercise_key: "a" }] }),
        false,
    );
    assertEquals(
        fails({
            changes: [
                change({
                    op: "set_rpe",
                    position: 1,
                    rpe: 9,
                    reason: "r".repeat(2000),
                }),
            ],
        }),
        false,
    );
    assert(fails({ extra: 1 }));
    assert(fails({ changes: [change({ op: "teleport", position: 1 })] }));
    assert(
        fails({
            changes: [change({ op: "set_rpe", position: 1, rpe: 9, id: "c9" })],
        }),
    );
    assert(
        fails({ changes: [change({ op: "set_rpe", position: 1 })] }),
    );
    assert(
        fails({
            changes: [
                change({ op: "set_rpe", position: 1, rpe: 9, reason: "no" }),
            ],
        }),
    );
    assert(
        fails({
            comments: [{
                text: "Un comentario válido y largo.",
                exercise_key: "b",
            }],
        }),
    );
    assertEquals(
        fails({
            comments: [{
                text: "Un comentario válido y largo.",
                exercise_key: "a",
            }],
        }),
        false,
    );
    assert(
        fails({
            changes: [change({
                op: "add_exercise",
                exercise_key: "a",
                sets: [{ ...SET, n: 11 }],
            })],
        }),
    );
    assert(
        fails({
            changes: Array.from(
                { length: 41 },
                () => change({ op: "set_rpe", position: 1, rpe: 9 }),
            ),
        }),
    );
    assert(
        fails({
            changes: [change({
                op: "set_rpe",
                position: 1,
                rpe: 9,
                reason: "Conviene reducir el cardio de la semana.",
            })],
        }),
    );
});

// --- the handler paths -----------------------------------------------------------------------------------------

Deno.test("a folder that is not the caller's is INVALID_INPUT before any model call", async () => {
    const t = setup({ folder: null });
    const res = await run(t);
    assertEquals(res.status, 400);
    assertEquals((await res.json()).error.code, "INVALID_INPUT");
    assertEquals(t.calls(), 0);
});

Deno.test("the monthly limit refuses the review before any model call", async () => {
    const t = setup({ spent: 1000 });
    const res = await run(t);
    assertEquals(res.status, 429);
    assertEquals((await res.json()).error.code, "coach_limit_reached");
    assertEquals(t.calls(), 0);
    assertEquals(t.reviews.length, 0);
});

Deno.test("a block review stores the plan and needs an open plan", async () => {
    const t = setup();
    const res = await run(t, input({ purpose: "block", plan_id: PLAN_ID }));
    assertEquals(res.status, 200);
    assertEquals(t.reviews[0].purpose, "block");
    assertEquals(t.reviews[0].planId, PLAN_ID);

    const closed = setup({
        plan: { status: "proposed", training_saved: true },
    });
    const refused = await run(
        closed,
        input({ purpose: "block", plan_id: PLAN_ID }),
    );
    assertEquals(refused.status, 400);
    assertEquals(closed.calls(), 0);
});

Deno.test("the prompt carries the folder, the Falta items and the catalogue, and no personal data", async () => {
    const t = setup({ folder: withoutLateral() });
    await run(t);
    const prompt = JSON.parse(t.requests[0].prompt);
    assertEquals(prompt.folder.routines.length, 4);
    assert(
        prompt.falta.some((f: { code: string }) => f.code === "h3_side_delta"),
    );
    assert(prompt.catalogue.shoulders.shoulder_abduction.includes(LATERAL));
    assertEquals(prompt.days_per_week, 4);
    assert(!t.requests[0].prompt.includes(SECRET));
    assert(!JSON.stringify(t.reviews).includes(SECRET));
});

Deno.test("the output budget grows with the folder and is capped", () => {
    assert(maxTokensFor(10) < maxTokensFor(30));
    assertEquals(maxTokensFor(1000), OUTPUT_TOKENS_MAX);
});
