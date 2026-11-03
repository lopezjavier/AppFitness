// Cardio in the assembled block (T-182): invariants mirror the SQL rule names of coach_assert_block.
import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { splitCardio } from "./blockRules.ts";
import {
    assembleBlock,
    assertBlockInvariants,
    BlockInvariantError,
    validateModelBlock,
} from "./blockValidators.ts";
import {
    buildEligibleCatalogue,
    cardioDefaultCandidates,
} from "./candidates.ts";
import {
    cardio,
    fakeBlock,
    fullCatalog,
    prefs,
    splitFor,
    toModelBlock,
} from "./blockFixtures.ts";
import {
    type Block,
    type BlockCardioExercise,
    type BlockPrefs,
    isCardioExercise,
    isLifting,
} from "./schemas.ts";

function setup(over: Partial<BlockPrefs> = {}) {
    const p = prefs({ days_per_week: 4, ...over });
    const catalog = fullCatalog();
    const eligible = buildEligibleCatalogue(catalog, p);
    const split = splitFor(4);
    const model = toModelBlock(
        fakeBlock({ split, catalogue: eligible.byGroup }),
    );
    const cardioSplit = splitCardio(p.cardio_min_week, 4);
    const cardioExercise = cardioSplit.planned > 0
        ? cardioDefaultCandidates(catalog, p, [])[0]
        : null;
    const block = assembleBlock({
        model,
        split,
        eligible,
        prefs: p,
        days: 4,
        sessionMinutes: 60,
        daysChosen: false,
        phase: "maintenance",
        blockWeeks: 4,
        cardio: cardioSplit,
        cardioExercise,
    });
    return { p, catalog, eligible, block };
}

const cardioOf = (b: Block, i = 0) =>
    b.sessions[i].exercises.find(isCardioExercise)!;

const broken = (
    over: Partial<BlockPrefs>,
    f: (b: Block) => void,
    rule: string,
) => {
    const s = setup(over);
    const b = structuredClone(s.block);
    f(b);
    const err = assertThrows(
        () => assertBlockInvariants(b, s.p, s.catalog),
        BlockInvariantError,
    );
    assertEquals(err.rule, rule);
};

Deno.test("a block with cardio closes every routine with the server's minutes and passes", () => {
    const s = setup();
    for (const [i, ses] of s.block.sessions.entries()) {
        const last = ses.exercises[ses.exercises.length - 1];
        assert(isCardioExercise(last));
        assertEquals(last.duration_min, [40, 40, 35, 35][i]);
        assertEquals(last.sets, 1);
        assertEquals(last.pace, "suave");
        assertEquals(ses.exercises.filter(isCardioExercise).length, 1);
    }
    assertEquals(s.block.training.cardio, {
        target_min_week: 150,
        planned_min_week: 150,
        on_your_own_min: 0,
        per_routine_min: [40, 40, 35, 35],
        pace: "suave",
    });
    assertEquals(s.block.training.routine_total_min, [100, 100, 95, 95]);
    assertBlockInvariants(s.block, s.p, s.catalog);
});

Deno.test("a cardio exercise has no reps, rest or RPE keys", () => {
    const c = cardioOf(setup().block) as unknown as Record<string, unknown>;
    assertEquals(Object.keys(c).sort(), [
        "duration_min",
        "exercise_id",
        "exercise_key",
        "pace",
        "reason",
        "role",
        "sets",
    ]);
});

Deno.test("the weekly sets and frequency count the lifting only", () => {
    const s = setup();
    assertEquals(s.block.training.weekly_sets.chest, 10);
    assert(!("full_body" in s.block.training.weekly_sets));
});

Deno.test("a block with cardio 300 min over 4 routines caps at 60 and keeps the rest on your own", () => {
    const s = setup({ cardio_min_week: 300 });
    assertEquals(s.block.training.cardio.per_routine_min, [60, 60, 60, 60]);
    assertEquals(s.block.training.cardio.on_your_own_min, 60);
    assertEquals(s.block.training.routine_total_min, [120, 120, 120, 120]);
    assertBlockInvariants(s.block, s.p, s.catalog);
});

Deno.test("a zero share has no cardio exercise and target 0 has none anywhere", () => {
    const few = setup({ cardio_min_week: 10 });
    assertEquals(
        few.block.sessions.map((x) =>
            x.exercises.filter(isCardioExercise).length
        ),
        [1, 1, 0, 0],
    );
    assertBlockInvariants(few.block, few.p, few.catalog);
    const zero = setup({ cardio_min_week: 0 });
    assertEquals(zero.block.training.cardio.per_routine_min, [0, 0, 0, 0]);
    assertEquals(
        zero.block.sessions.flatMap((x) => x.exercises).filter(isCardioExercise)
            .length,
        0,
    );
    assertBlockInvariants(zero.block, zero.p, zero.catalog);
});

Deno.test("cardio not last is rejected (cardio_order)", () => {
    broken({}, (b) => {
        const e = b.sessions[0].exercises;
        [e[0], e[e.length - 1]] = [e[e.length - 1], e[0]];
    }, "cardio_order");
});

Deno.test("two cardio exercises in a routine are rejected (cardio_count)", () => {
    broken({}, (b) => {
        const e = b.sessions[0].exercises;
        e.push({ ...cardioOf(b) });
    }, "cardio_count");
});

Deno.test("more than 60 minutes, off-unit minutes or another pace are rejected (cardio_minutes)", () => {
    broken({}, (b) => void (cardioOf(b).duration_min = 65), "cardio_minutes");
    broken({}, (b) => void (cardioOf(b).duration_min = 47), "cardio_minutes");
    broken({}, (b) => void (cardioOf(b).duration_min = 55), "cardio_minutes");
    broken(
        {},
        (b) => void ((cardioOf(b) as { pace: string }).pace = "fuerte"),
        "cardio_minutes",
    );
});

Deno.test("a wrong weekly total or on-your-own figure is rejected (cardio_total)", () => {
    broken(
        {},
        (b) => void (b.training.cardio.planned_min_week = 140),
        "cardio_total",
    );
    broken(
        {},
        (b) => void (b.training.cardio.on_your_own_min = 10),
        "cardio_total",
    );
    broken(
        {},
        (b) => void (b.training.cardio.per_routine_min = [60, 50, 40, 0]),
        "cardio_total",
    );
    broken(
        {},
        (b) => void (b.training.routine_total_min = [100, 100, 95, 90]),
        "cardio_total",
    );
    broken(
        {},
        (b) => void (b.training.cardio.target_min_week = 601),
        "cardio_total",
    );
});

Deno.test("cardio present with a zero share, or missing with minutes, is rejected (cardio_total)", () => {
    broken({ cardio_min_week: 10 }, (b) => {
        b.sessions[3].exercises.push({ ...cardioOf(b) });
    }, "cardio_total");
    broken({}, (b) => {
        b.sessions[0].exercises = b.sessions[0].exercises.filter(isLifting);
    }, "cardio_total");
});

Deno.test("cardio outside the equipment, or a lifting exercise on the cardio slot, is rejected (cardio_equipment)", () => {
    const rowing = cardio("rowing-machine", "cardio_machine");
    const interval = cardio("jump-rope", "bodyweight");
    for (const row of [rowing, interval]) {
        const s = setup({
            place: "home",
            equipment: ["barbell"],
        });
        const b = structuredClone(s.block);
        cardioOf(b).exercise_id = row.id;
        const err = assertThrows(
            () => assertBlockInvariants(b, s.p, [...s.catalog, row]),
            BlockInvariantError,
        );
        assertEquals(err.rule, "cardio_equipment");
    }
    broken({}, (b) => {
        cardioOf(b).exercise_id =
            b.sessions[0].exercises.find(isLifting)!.exercise_id;
    }, "duplicate_exercise");
});

Deno.test("reps, rest or RPE on a cardio exercise are rejected (cardio_fields)", () => {
    for (
        const k of [
            "rep_min",
            "rep_max",
            "rest_seconds",
            "rpe",
            "target_rpe",
            "reps",
        ]
    ) {
        broken(
            {},
            (b) =>
                void ((cardioOf(b) as unknown as Record<string, unknown>)[k] =
                    8),
            "cardio_fields",
        );
    }
});

Deno.test("a warm-up role or key anywhere is rejected (warmup)", () => {
    broken(
        {},
        (b) =>
            void ((b.sessions[1].exercises[0] as { role: string }).role =
                "warm-up"),
        "warmup",
    );
    broken(
        {},
        (b) => void (b.sessions[1].exercises[0].exercise_key = "warm_up-bike"),
        "warmup",
    );
});

Deno.test("a cardio-only routine is rejected (empty_session)", () => {
    broken({}, (b) => {
        b.sessions[0].exercises = [cardioOf(b) as BlockCardioExercise];
    }, "empty_session");
});

Deno.test("a training part without the cardio plan is rejected, and steps stay forbidden", () => {
    broken(
        {},
        (b) =>
            void ((b.training as unknown as Record<string, unknown>).cardio =
                undefined),
        "cardio_total",
    );
    broken(
        {},
        (b) =>
            void ((b.training as unknown as Record<string, unknown>).steps =
                8000),
        "forbidden_field",
    );
});

Deno.test("the block is stored with the resolved equipment", () => {
    assertEquals(
        setup({ place: "gym", equipment: [] }).block.training.equipment,
        [],
    );
    const bodyweightHome = setup({ place: "home", equipment: ["barbell"] });
    assertEquals(bodyweightHome.block.training.equipment, ["barbell"]);
});

Deno.test("the cardio exercise is the server's: the model block has no say in key or minutes", () => {
    const s = setup();
    const c = cardioOf(s.block);
    assertEquals(c.exercise_key, "stationary-bike");
    assertEquals(c.duration_min, 40);
});

Deno.test("a reason advising fewer steps on the cardio of a routine is rejected by the validator", () => {
    const p = prefs({ days_per_week: 4 });
    const eligible = buildEligibleCatalogue(fullCatalog(), p);
    const split = splitFor(4);
    const ctx = {
        prefs: p,
        days: 4,
        minutes: 60,
        eligible,
        excludedKeys: [],
        history: new Set<string>(),
        hasPrevious: false,
        cardioPerRoutine: [40, 40, 35, 35],
    };
    const ok = fakeBlock({ split, catalogue: eligible.byGroup });
    assert(validateModelBlock(JSON.stringify(ok), ctx).block !== null);
    const bad = fakeBlock({ split, catalogue: eligible.byGroup }, {
        mutate: (b) => {
            b.sessions[1].cardio_reason = "Mejor haz menos pasos esta semana.";
        },
    });
    const r = validateModelBlock(JSON.stringify(bad), ctx);
    assertEquals(r.block, null);
    assertEquals(r.failures.map((f) => f.code), ["cardio_reason"]);
    // A routine without cardio minutes needs no cardio reason.
    const none = validateModelBlock(JSON.stringify(bad), {
        ...ctx,
        cardioPerRoutine: [40, 0, 35, 35],
    });
    assert(none.block !== null);
});
