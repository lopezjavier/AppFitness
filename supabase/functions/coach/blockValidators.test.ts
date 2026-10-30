import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { buildSkeleton } from "./blockRules.ts";
import {
    assembleBlock,
    assertBlockInvariants,
    BlockInvariantError,
    mentionsCardioReduction,
    validateBlockText,
} from "./blockValidators.ts";
import { buildCandidates, candidateKey } from "./candidates.ts";
import { fullCatalog, prefs } from "./blockFixtures.ts";
import type { Block, BlockPrefs, ModelBlockPick } from "./schemas.ts";

const REASON = "Buen ejercicio base para tu objetivo y tu material.";

function setup(over: Partial<BlockPrefs> = {}) {
    const p = prefs({ days_per_week: 3, ...over });
    const catalog = fullCatalog();
    const sk = buildSkeleton(p);
    const cands = buildCandidates(catalog, p, sk);
    // First candidate not yet used in the same session.
    const picks: ModelBlockPick[] = [];
    for (const s of sk.sessions) {
        const used = new Set<string>();
        for (const slot of s.slots) {
            const key = cands.get(slot.slot_id)!.map(candidateKey)
                .find((k) => !used.has(k))!;
            used.add(key);
            picks.push({
                slot_id: slot.slot_id,
                exercise_key: key,
                reason: REASON,
            });
        }
    }
    const text = JSON.stringify({ picks });
    return { p, catalog, sk, cands, picks, text };
}

const mutate = (picks: ModelBlockPick[], f: (p: ModelBlockPick[]) => unknown) =>
    JSON.stringify({ picks: f(structuredClone(picks)) });

Deno.test("a complete answer validates and trims the reasons", () => {
    const { sk, cands, picks } = setup();
    const padded = JSON.stringify({
        picks: picks.map((p) => ({ ...p, reason: `  ${p.reason} ` })),
    });
    assertEquals(validateBlockText(padded, sk, cands), picks);
});

Deno.test("a fenced answer is accepted", () => {
    const { sk, cands, text } = setup();
    assert(validateBlockText("```json\n" + text + "\n```", sk, cands) !== null);
});

Deno.test("invalid answers are rejected", () => {
    const { sk, cands, picks, text } = setup();
    const bad: Record<string, string> = {
        malformed: "{picks:",
        "extra top key": JSON.stringify({ picks, extra: 1 }),
        "extra pick key": mutate(picks, (l) => {
            (l[0] as unknown as Record<string, unknown>).sets = 3;
            return l;
        }),
        "missing slot": mutate(picks, (l) => l.slice(1)),
        "duplicate slot": mutate(picks, (l) => {
            l[1].slot_id = l[0].slot_id;
            return l;
        }),
        "unknown slot": mutate(picks, (l) => {
            l[0].slot_id = "s9.e9";
            return l;
        }),
        "key outside the slot candidates": mutate(picks, (l) => {
            l[0].exercise_key = "not-a-candidate";
            return l;
        }),
        "reason too short": mutate(picks, (l) => {
            l[0].reason = "corto";
            return l;
        }),
        "reason too long": mutate(picks, (l) => {
            l[0].reason = "x".repeat(301);
            return l;
        }),
        "cardio reduction": mutate(picks, (l) => {
            l[0].reason = "Mejor haz menos pasos esta semana para recuperar.";
            return l;
        }),
    };
    for (const [name, t] of Object.entries(bad)) {
        assertEquals(validateBlockText(t, sk, cands), null, name);
    }
    assert(validateBlockText(text, sk, cands) !== null);
});

Deno.test("a key from another slot's candidates (other equipment or muscle) is rejected", () => {
    const { sk, cands, picks } = setup();
    const other = [...cands.entries()].find(([id]) => id === "s1.e5")!;
    const foreign = cands.get("s1.e1")!.map(candidateKey)
        .find((k) => !other[1].map(candidateKey).includes(k))!;
    const t = mutate(picks, (l) => {
        l.find((x) => x.slot_id === "s1.e5")!.exercise_key = foreign;
        return l;
    });
    assertEquals(validateBlockText(t, sk, cands), null);
});

Deno.test("the same exercise twice in one session is rejected", () => {
    const { sk, cands } = setup({ days_per_week: 1 });
    // Two slots of the same pool in one session (hand-built): picking the same key twice is invalid.
    const twin = structuredClone(sk);
    const [first, second] = twin.sessions[0].slots;
    second.muscle_group = first.muscle_group;
    second.role = first.role;
    const pool = new Map(cands);
    pool.set(second.slot_id, cands.get(first.slot_id)!);
    const keys = pool.get(first.slot_id)!.map(candidateKey);
    const answer = (a: string, b: string) =>
        JSON.stringify({
            picks: twin.sessions[0].slots.map((s, i) => ({
                slot_id: s.slot_id,
                exercise_key: i === 0
                    ? a
                    : i === 1
                    ? b
                    : pool.get(s.slot_id)!.map(candidateKey)[0],
                reason: REASON,
            })),
        });
    assertEquals(validateBlockText(answer(keys[0], keys[0]), twin, pool), null);
    assert(validateBlockText(answer(keys[0], keys[1]), twin, pool) !== null);
});

Deno.test("only the sessions of a part are expected when a part is validated", () => {
    const { sk, cands, picks } = setup();
    const part = picks.filter((p) => p.slot_id.startsWith("s1."));
    assert(
        validateBlockText(JSON.stringify({ picks: part }), sk, cands, [1]) !==
            null,
    );
    assertEquals(
        validateBlockText(JSON.stringify({ picks: part }), sk, cands),
        null,
    );
});

Deno.test("cardio and steps reduction phrases are detected, neutral text is not", () => {
    for (
        const t of [
            "Te conviene hacer menos pasos hoy",
            "Reduce el cardio de la semana",
            "Evita el cardio en los días de pierna",
            "Conviene recortar los pasos diarios",
        ]
    ) assert(mentionsCardioReduction(t), t);
    for (
        const t of [
            "Rango completo de movimiento para trabajar el pecho",
            "Mantén tus pasos y tu cardio habituales",
        ]
    ) assert(!mentionsCardioReduction(t), t);
});

// --- final invariants ---

function block(over: Partial<BlockPrefs> = {}) {
    const s = setup(over);
    const b = assembleBlock(s.sk, s.picks, s.cands, s.p);
    return { ...s, block: b };
}

const broken = (
    over: Partial<BlockPrefs>,
    f: (b: Block) => void,
    rule: string,
) => {
    const s = block(over);
    const b = structuredClone(s.block);
    f(b);
    const err = assertThrows(
        () => assertBlockInvariants(b, s.p, s.catalog),
        BlockInvariantError,
    );
    assertEquals(err.rule, rule);
};

Deno.test("a block assembled from valid picks passes every invariant", () => {
    for (const intention of ["muscle", "strength"] as const) {
        for (let d = 1; d <= 7; d++) {
            const s = block({ intention, days_per_week: d });
            assertBlockInvariants(s.block, s.p, s.catalog);
        }
    }
});

Deno.test("assembly keeps the server's weekly sets and frequency", () => {
    const s = block({ days_per_week: 4 });
    assertEquals(s.block.training.weekly_sets.chest, 10);
    assertEquals(s.block.training.sessions_per_muscle.chest, 2);
    assertEquals(s.block.training.days_chosen, false);
});

Deno.test("equipment outside the selected list is rejected", () => {
    const s = block({ place: "gym", equipment: [] });
    const err = assertThrows(
        () =>
            assertBlockInvariants(
                s.block,
                {
                    intention: "muscle",
                    place: "home",
                    equipment: ["dumbbell", "bodyweight"],
                },
                s.catalog,
            ),
        BlockInvariantError,
    );
    assertEquals(err.rule, "equipment");
});

Deno.test("rest outside the role bounds is rejected", () => {
    const rest = (role: "main" | "isolation", v: number) => (b: Block) => {
        b.sessions[0].exercises.find((e) => e.role === role)!.rest_seconds = v;
    };
    broken({}, rest("isolation", 59), "rest");
    broken({}, rest("isolation", 121), "rest");
    broken({}, rest("main", 119), "rest");
    broken({}, rest("main", 181), "rest");
});

Deno.test("an isolation exercise before a main lift is rejected", () => {
    broken({}, (b) => {
        const e = b.sessions[0].exercises;
        [e[0], e[e.length - 1]] = [e[e.length - 1], e[0]];
    }, "order");
    broken({}, (b) => {
        const e = b.sessions[0].exercises;
        const iso = e.findIndex((x) => x.role === "isolation");
        e[iso].role = "main";
        e[iso - 1].role = "isolation";
    }, "role_pattern");
});

Deno.test("a rep range changed inside a non-strength block is rejected", () => {
    broken({}, (b) => {
        b.sessions[0].exercises[0].rep_min = 8;
        b.sessions[0].exercises[0].rep_max = 12;
    }, "single_range");
});

Deno.test("identical main ranges across a strength week are rejected", () => {
    broken({ intention: "strength" }, (b) => {
        for (const s of b.sessions) {
            for (const e of s.exercises.filter((x) => x.role === "main")) {
                e.rep_min = 3;
                e.rep_max = 5;
            }
        }
    }, "strength_not_undulating");
});

Deno.test("a strength range outside the allowed set is rejected", () => {
    broken({ intention: "strength" }, (b) => {
        b.sessions[0].exercises[0].rep_min = 8;
        b.sessions[0].exercises[0].rep_max = 12;
    }, "strength_range");
});

Deno.test("a reason that is too short or reduces cardio is rejected", () => {
    broken(
        {},
        (b) => void (b.sessions[0].exercises[0].reason = "corto"),
        "reason",
    );
    broken(
        {},
        (b) =>
            void (b.sessions[0].exercises[0].reason =
                "Haz menos pasos para rendir más."),
        "reason",
    );
});

Deno.test("wrong weekly sets, session count and frequency are rejected", () => {
    broken({}, (b) => void (b.training.weekly_sets.chest = 99), "weekly_sets");
    broken({}, (b) => void b.sessions.pop(), "session_count");
    broken({}, (b) => {
        b.sessions[1].exercises = b.sessions[1].exercises.filter((e) =>
            e.muscle_group !== "chest"
        );
        b.training.weekly_sets = {};
        b.training.sessions_per_muscle = {};
    }, "weekly_sets");
});

Deno.test("a cardio field in the training part is rejected", () => {
    broken(
        {},
        (b) =>
            void ((b.training as unknown as Record<string, unknown>).steps =
                8000),
        "forbidden_field",
    );
});

Deno.test("an unknown or duplicated exercise is rejected", () => {
    broken(
        {},
        (b) => void (b.sessions[0].exercises[0].exercise_id = "nope"),
        "unknown_exercise",
    );
    broken({}, (b) => {
        const e = b.sessions[0].exercises;
        e[1].exercise_id = e[0].exercise_id;
    }, "duplicate_exercise");
});
