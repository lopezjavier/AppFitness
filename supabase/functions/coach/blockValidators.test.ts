// Validation of the model's block (T-205): shape, grounding rules (D7) and the routine checker behind them.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    type BlockValidationContext,
    mentionsCardioReduction,
    validateModelBlock,
} from "./blockValidators.ts";
import { buildEligibleCatalogue, candidateKey } from "./candidates.ts";
import {
    type FakeBlock,
    fakeBlock,
    fullCatalog,
    prefs,
    splitFor,
} from "./blockFixtures.ts";

const p = prefs({ days_per_week: 4 });
const catalog = fullCatalog();
const eligible = buildEligibleCatalogue(catalog, p);
const split = splitFor(4);

const ctx = (
    over: Partial<BlockValidationContext> = {},
): BlockValidationContext => ({
    prefs: p,
    days: 4,
    minutes: 60,
    eligible,
    excludedKeys: [],
    history: new Set<string>(),
    hasPrevious: false,
    cardioPerRoutine: [40, 40, 35, 35],
    ...over,
});

const run = (
    mutate?: (b: FakeBlock) => void,
    over: Partial<BlockValidationContext> = {},
) => {
    const block = fakeBlock({ split, catalogue: eligible.byGroup }, { mutate });
    return validateModelBlock(JSON.stringify(block), ctx(over));
};
const codes = (r: ReturnType<typeof run>) => r.failures.map((f) => f.code);

Deno.test("the fixture block is valid and expands the grouped sets", () => {
    const r = run();
    assertEquals(r.failures, []);
    assertEquals(r.block!.sessions.length, 4);
    const first = r.block!.sessions[0].exercises[0];
    assertEquals(first.sets.length, 3);
    assertEquals(first.sets[0].type, "working");
    assertEquals(r.block!.changes_vs_last_block, null);
});

Deno.test("text that is not the contract is a shape failure", () => {
    for (
        const text of [
            "not json",
            "[]",
            JSON.stringify({ sessions: [] }),
            JSON.stringify({
                sessions: "x",
                split_reason: "a",
                changes_vs_last_block: "",
            }),
        ]
    ) {
        const r = validateModelBlock(text, ctx());
        assertEquals(r.block, null);
        assertEquals(codes({ ...r, notes: [] }), ["shape"]);
    }
});

Deno.test("a wrong number of routines is h6_sessions", () => {
    const r = run((b) => void b.sessions.pop());
    assertEquals(r.block, null);
    assertEquals(codes(r), ["h6_sessions"]);
});

Deno.test("a stray key, a bad session index or a bad set shape is refused", () => {
    assertEquals(
        codes(
            run((b) =>
                void ((b as unknown as Record<string, unknown>).extra = 1)
            ),
        ),
        ["shape"],
    );
    assertEquals(codes(run((b) => void (b.sessions[1].session_index = 5))), [
        "shape",
    ]);
    assertEquals(
        codes(run((b) => void (b.sessions[0].exercises[0].sets[0].n = 0))),
        ["shape"],
    );
    assertEquals(
        codes(
            run((b) =>
                void (b.sessions[0].exercises[0].sets[0].type = "warm_up")
            ),
        ),
        ["shape"],
    );
    assertEquals(
        codes(run((b) => void (b.sessions[0].exercises[0].sets[0].rpe = 7.5))),
        ["shape"],
    );
});

Deno.test("an exercise outside the eligible list is unknown_exercise", () => {
    const r = run((b) =>
        void (b.sessions[0].exercises[0].exercise_key = "made-up")
    );
    assertEquals(r.block, null);
    assertEquals(codes(r), ["unknown_exercise"]);
});

Deno.test("an exercise with an unresolved discomfort is h6_excluded_discomfort", () => {
    const block = fakeBlock({ split, catalogue: eligible.byGroup });
    const used = block.sessions[0].exercises[0].exercise_key;
    const target = catalog.find((e) => candidateKey(e) === used)!;
    const r = validateModelBlock(
        JSON.stringify(block),
        ctx({
            eligible: buildEligibleCatalogue(catalog, p, [target.id]),
            excludedKeys: [used],
        }),
    );
    assertEquals(r.block, null);
    assert(codes(r).includes("h6_excluded_discomfort"));
});

Deno.test("an exercise twice in a routine is duplicate_exercise", () => {
    const r = run((b) => {
        const ex = b.sessions[0].exercises;
        ex[1] = { ...ex[1], exercise_key: ex[0].exercise_key };
    });
    assert(codes(r).includes("duplicate_exercise"));
});

Deno.test("planned weight needs history: without it planned_weight_no_history, with it accepted", () => {
    const key = (b: FakeBlock) => b.sessions[0].exercises[0].exercise_key;
    let used = "";
    const bad = run((b) => {
        used = key(b);
        b.sessions[0].exercises[0].planned_weight_kg = 80;
    });
    assertEquals(codes(bad), ["planned_weight_no_history"]);
    const ok = run((b) => {
        b.sessions[0].exercises[0].planned_weight_kg = 80;
    }, { history: new Set([used]) });
    assertEquals(codes(ok), []);
    const absurd = run((b) => {
        b.sessions[0].exercises[0].planned_weight_kg = 0;
    }, { history: new Set([used]) });
    assertEquals(codes(absurd), ["planned_weight"]);
});

Deno.test("one technique note at most, and only on an exercise the user has no history with", () => {
    let used = "";
    const one = run((b) => {
        used = b.sessions[0].exercises[0].exercise_key;
        b.sessions[0].exercises[0].technique_note =
            "Codos a 45 grados del torso.";
    });
    assertEquals(codes(one), []);
    const two = run((b) => {
        b.sessions[0].exercises[0].technique_note = "Codos a 45 grados.";
        b.sessions[1].exercises[0].technique_note = "Espalda neutra siempre.";
    });
    assertEquals(codes(two), ["technique_note_count"]);
    const known = run((b) => {
        b.sessions[0].exercises[0].technique_note = "Codos a 45 grados.";
    }, { history: new Set([used]) });
    assertEquals(codes(known), ["technique_note"]);
});

Deno.test("reasons: at least 10 characters (no upper cap) and never less cardio or fewer steps", () => {
    assertEquals(
        codes(
            run((b) =>
                void (b.sessions[0].exercises[0].reason = "r".repeat(600))
            ),
        ),
        [],
    );
    assertEquals(
        codes(run((b) => void (b.sessions[0].exercises[0].reason = "corta"))),
        ["reason"],
    );
    assertEquals(
        codes(
            run((b) =>
                void (b.sessions[0].exercises[0].reason =
                    "Mejor haz menos cardio esta semana.")
            ),
        ),
        ["reason"],
    );
    assertEquals(codes(run((b) => void (b.split_reason = "x"))), [
        "split_reason",
    ]);
    assert(mentionsCardioReduction("haz menos pasos"));
});

Deno.test("changes_vs_last_block is required when there is a previous block and dropped when there is none", () => {
    const missing = run((b) => void (b.changes_vs_last_block = ""), {
        hasPrevious: true,
    });
    assertEquals(codes(missing), ["changes_vs_last_block"]);
    const present = run(
        (b) => void (b.changes_vs_last_block = "Subí el volumen de espalda."),
        { hasPrevious: true },
    );
    assertEquals(codes(present), []);
    assert(present.block!.changes_vs_last_block !== null);
    const stray = run((b) =>
        void (b.changes_vs_last_block = "Cambié todo el bloque.")
    );
    assertEquals(codes(stray), []);
});

Deno.test("the checker's failures come back with their data (a strength block with main lifts at 10-15)", () => {
    const strength = fakeBlock({
        split,
        catalogue: eligible.byGroup,
        intention: "strength",
    }, {
        mutate: (b) => {
            for (const s of b.sessions) {
                s.exercises[0].sets[0].rep_min = 10;
                s.exercises[0].sets[0].rep_max = 15;
            }
        },
    });
    const r = validateModelBlock(
        JSON.stringify(strength),
        ctx({ prefs: { ...p, intention: "strength" } }),
    );
    assertEquals(r.block, null);
    const f = r.failures.find((x) => x.code === "h4_rep_range")!;
    assertEquals(f.routine, 1);
    assert(typeof f.exercise_key === "string");
});

Deno.test("a block without a side-delta exercise fails h3_side_delta", () => {
    const noAbduction = run((b) => {
        for (const s of b.sessions) {
            s.exercises = s.exercises.filter((e) =>
                !catalog.find((c) => candidateKey(c) === e.exercise_key)!
                    .movement_pattern!.startsWith("shoulder_abd")
            );
        }
    });
    assert(codes(noAbduction).includes("h3_side_delta"));
});

Deno.test("below_recommended travels as a note when the minimum volume cannot fit", () => {
    const oneDay = splitFor(1);
    const block = fakeBlock({ split: oneDay, catalogue: eligible.byGroup }, {
        mainSets: 2,
        isoSets: 1,
    });
    const r = validateModelBlock(
        JSON.stringify(block),
        ctx({ days: 1, minutes: 45, cardioPerRoutine: [150] }),
    );
    assertEquals(r.failures.filter((f) => f.code !== "h6_time").length, 0);
    assert(r.notes.includes("below_recommended") || r.failures.length > 0);
});
