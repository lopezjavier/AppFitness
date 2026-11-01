// Cardio in the assembled block (T-182): invariants mirror the SQL rule names of coach_assert_block.
import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { buildSkeleton } from "./blockRules.ts";
import {
    assembleBlock,
    assertBlockInvariants,
    BlockInvariantError,
    validateBlockText,
} from "./blockValidators.ts";
import { buildCandidates, candidateKey } from "./candidates.ts";
import { cardio, fullCatalog, prefs } from "./blockFixtures.ts";
import {
    type Block,
    type BlockCardioExercise,
    type BlockPrefs,
    isCardioExercise,
    isLifting,
    type ModelBlockPick,
} from "./schemas.ts";

const REASON = "Buen ejercicio base para tu objetivo y tu material.";

function setup(over: Partial<BlockPrefs> = {}) {
    const p = prefs({ days_per_week: 3, ...over });
    const catalog = fullCatalog();
    const sk = buildSkeleton(p);
    const cands = buildCandidates(catalog, p, sk);
    const picks: ModelBlockPick[] = [];
    for (const s of sk.sessions) {
        const used = new Set<string>();
        for (const slot of s.slots) {
            const key = cands.get(slot.slot_id)!.map(candidateKey).find((k) =>
                !used.has(k)
            )!;
            used.add(key);
            picks.push({
                slot_id: slot.slot_id,
                exercise_key: key,
                reason: REASON,
            });
        }
        if (s.cardio) {
            picks.push({
                slot_id: s.cardio.slot_id,
                exercise_key: candidateKey(cands.get(s.cardio.slot_id)![0]),
                reason: REASON,
            });
        }
    }
    const block = assembleBlock(sk, picks, cands, p);
    return { p, catalog, sk, cands, picks, block };
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
    for (const ses of s.block.sessions) {
        const last = ses.exercises[ses.exercises.length - 1];
        assert(isCardioExercise(last));
        assertEquals(last.duration_min, 50);
        assertEquals(last.sets, 1);
        assertEquals(last.pace, "suave");
        assertEquals(ses.exercises.filter(isCardioExercise).length, 1);
    }
    assertEquals(s.block.training.cardio, {
        target_min_week: 150,
        planned_min_week: 150,
        on_your_own_min: 0,
        per_routine_min: [50, 50, 50],
        pace: "suave",
    });
    assertEquals(s.block.training.routine_total_min, [110, 110, 110]);
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
    const s = setup({ days_per_week: 4 });
    assertEquals(s.block.training.weekly_sets.chest, 10);
    assert(!("full_body" in s.block.training.weekly_sets));
});

Deno.test("a block with cardio 300 min over 3 routines caps at 60 and keeps the rest on your own", () => {
    const s = setup({ cardio_min_week: 300 });
    assertEquals(s.block.training.cardio.per_routine_min, [60, 60, 60]);
    assertEquals(s.block.training.cardio.on_your_own_min, 120);
    assertEquals(s.block.training.routine_total_min, [120, 120, 120]);
    assertBlockInvariants(s.block, s.p, s.catalog);
});

Deno.test("a zero share has no cardio exercise and target 0 has none anywhere", () => {
    const few = setup({ cardio_min_week: 10 });
    assertEquals(
        few.block.sessions.map((x) =>
            x.exercises.filter(isCardioExercise).length
        ),
        [1, 1, 0],
    );
    assertBlockInvariants(few.block, few.p, few.catalog);
    const zero = setup({ cardio_min_week: 0 });
    assertEquals(zero.block.training.cardio.per_routine_min, [0, 0, 0]);
    assertEquals(
        zero.block.sessions.flatMap((x) => x.exercises).filter(isCardioExercise)
            .length,
        0,
    );
    assertBlockInvariants(zero.block, zero.p, zero.catalog);
});

Deno.test("the picks must answer the cardio slot too", () => {
    const s = setup();
    const withoutCardio = s.picks.filter((p) => !p.slot_id.endsWith(".cardio"));
    assertEquals(
        validateBlockText(
            JSON.stringify({ picks: withoutCardio }),
            s.sk,
            s.cands,
        ),
        null,
    );
    assert(
        validateBlockText(JSON.stringify({ picks: s.picks }), s.sk, s.cands) !==
            null,
    );
});

Deno.test("a pick outside the cardio slot's candidates, or a lifting key on it, is rejected", () => {
    const s = setup();
    const swap = (key: string) =>
        JSON.stringify({
            picks: s.picks.map((p) =>
                p.slot_id === "s1.cardio" ? { ...p, exercise_key: key } : p
            ),
        });
    assertEquals(validateBlockText(swap("running"), s.sk, s.cands), null);
    const lifting = candidateKey(s.cands.get("s1.e1")!.at(0)!);
    assertEquals(validateBlockText(swap(lifting), s.sk, s.cands), null);
});

Deno.test("a reason advising fewer steps on the cardio slot is rejected", () => {
    const s = setup();
    const text = JSON.stringify({
        picks: s.picks.map((p) =>
            p.slot_id === "s2.cardio"
                ? { ...p, reason: "Mejor haz menos pasos esta semana." }
                : p
        ),
    });
    assertEquals(validateBlockText(text, s.sk, s.cands), null);
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
        (b) => void (b.training.cardio.per_routine_min = [60, 50, 40]),
        "cardio_total",
    );
    broken(
        {},
        (b) => void (b.training.routine_total_min = [110, 110, 100]),
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
        b.sessions[2].exercises.push({ ...cardioOf(b) });
    }, "cardio_total");
    broken({}, (b) => {
        b.sessions[0].exercises = b.sessions[0].exercises.filter(isLifting);
    }, "cardio_total");
});

Deno.test("cardio outside the equipment, or a lifting exercise on the cardio slot, is rejected (cardio_equipment)", () => {
    const rowing = cardio("rowing-machine", "rower");
    const interval = cardio("jump-rope", "bodyweight");
    for (const row of [rowing, interval]) {
        const s = setup({
            place: "home",
            equipment: ["barbell", "cardio_bike"],
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
