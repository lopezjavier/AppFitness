// T-187: volume, minimal block and cardio by phase (04 sections 9.3 and 9.11; 07 sections 3 and 12.3).
import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    buildSkeleton,
    cardioTargetFor,
    splitCardio,
    weeklySetsByMuscle,
} from "./blockRules.ts";
import {
    assembleBlock,
    assertBlockInvariants,
    BlockInvariantError,
} from "./blockValidators.ts";
import { buildCandidates, candidateKey } from "./candidates.ts";
import { fullCatalog, prefs } from "./blockFixtures.ts";
import { previousBlock } from "./planBlock.ts";
import {
    CARDIO_BAND_MAX,
    CARDIO_MIN_WEEK,
    computeActivity,
} from "./cardioRules.ts";
import {
    type BlockPrefs,
    isLifting,
    type ModelBlockPick,
    type PhaseHistory,
    type PreviousBlock,
} from "./schemas.ts";

const REASON = "Buen ejercicio base para tu objetivo y tu material.";
const slots = (sk: ReturnType<typeof buildSkeleton>) =>
    sk.sessions.flatMap((s) => s.slots);
const weekly = (p: BlockPrefs) => weeklySetsByMuscle(slots(buildSkeleton(p)));

/** A previous block with the given weekly sets and the first main lifts of the full catalogue. */
function previous(
    sets: Record<string, number>,
    liftCount = 3,
    catalog = fullCatalog(),
): PreviousBlock {
    const mains = catalog.filter((e) =>
        e.movement_pattern !== "isolation" && e.movement_pattern !== "core" &&
        e.movement_pattern !== "cardio"
    );
    // One lift per group first, so the groups differ.
    const seen = new Set<string>();
    const lifts = mains.filter((e) => {
        if (seen.has(e.muscle_group)) return false;
        seen.add(e.muscle_group);
        return true;
    }).slice(0, liftCount);
    return {
        phase: "cut",
        sets_per_group: sets,
        main_lifts: lifts.map((e, i) => ({
            exercise_id: e.id,
            muscle_group: e.muscle_group,
            rep_min: i === 0 ? 3 : 6,
            rep_max: i === 0 ? 5 : 10,
        })),
    };
}

// --- volume by phase ---

Deno.test("every experience starts at the same 10 weekly sets", () => {
    for (
        const experience of ["beginner", "intermediate", "advanced"] as const
    ) {
        const w = weekly(prefs({ days_per_week: 4, experience }));
        assertEquals(w.chest, 10);
        assertEquals(w.quads, 10);
        assertEquals(w.core, 6);
    }
});

Deno.test("a first block without history is 10 sets in every phase but minimal", () => {
    for (
        const phase of [
            "cut",
            "gain",
            "maintenance",
            "recomposition",
        ] as const
    ) {
        assertEquals(weekly(prefs({ days_per_week: 4, phase })).chest, 10);
    }
});

Deno.test("cut keeps the previous block's sets per group and never raises them", () => {
    const prev = previous({ chest: 6, quads: 10, back: 14 });
    const w = weekly(
        prefs({ days_per_week: 4, phase: "cut", previous: prev }),
    );
    assertEquals(w.chest, 6);
    assertEquals(w.quads, 10);
    // 14 sets over 2 slots is 5 + 5 per slot at most: the kept figure, within the 2-5 sets per slot rule.
    assert(w.back <= 14);
    // A group the previous block did not train starts at the base.
    assertEquals(w.shoulders, 10);
});

Deno.test("gain, maintenance and recomposition start from the base, whatever the previous block", () => {
    const prev = previous({ chest: 6 });
    for (const phase of ["gain", "maintenance", "recomposition"] as const) {
        assertEquals(
            weekly(prefs({ days_per_week: 4, phase, previous: prev })).chest,
            10,
        );
    }
});

// --- minimal phase ---

Deno.test("minimal: one full-body session with one set per slot, main lifts only", () => {
    const sk = buildSkeleton(
        prefs({ phase: "minimal", age: 64, days_per_week: 5 }),
    );
    assertEquals(sk.phase, "minimal");
    assertEquals(sk.days_per_week, 1);
    assertEquals(sk.sessions.length, 1);
    assert(sk.sessions[0].slots.length > 0);
    for (const s of sk.sessions[0].slots) {
        assertEquals(s.role, "main");
        assertEquals(s.sets, 1);
    }
});

Deno.test("minimal from 65: two sessions and two sets, 64 stays with one and one", () => {
    const young = buildSkeleton(prefs({ phase: "minimal", age: 64 }));
    const old = buildSkeleton(prefs({ phase: "minimal", age: 65 }));
    assertEquals([young.sessions.length, young.sessions[0].slots[0].sets], [
        1,
        1,
    ]);
    assertEquals([old.sessions.length, old.sessions[0].slots[0].sets], [2, 2]);
    assert(old.sessions.every((s) => s.slots.every((x) => x.role === "main")));
});

Deno.test("minimal keeps the previous main lifts and their rep ranges", () => {
    const prev = previous({ chest: 10 }, 3);
    const sk = buildSkeleton(
        prefs({ phase: "minimal", age: 40, previous: prev }),
    );
    const got = sk.sessions[0].slots;
    assertEquals(
        got.map((s) => s.fixed_exercise_id),
        prev.main_lifts.map((l) => l.exercise_id),
    );
    assertEquals(
        got.map((s) => [s.rep_min, s.rep_max]),
        prev.main_lifts.map((l) => [l.rep_min, l.rep_max]),
    );
    assertEquals(
        got.map((s) => s.muscle_group),
        prev.main_lifts.map((l) => l.muscle_group),
    );
});

Deno.test("minimal from 65 repeats the previous main lifts in both sessions", () => {
    const prev = previous({}, 2);
    const sk = buildSkeleton(
        prefs({ phase: "minimal", age: 70, previous: prev }),
    );
    assertEquals(sk.sessions.length, 2);
    for (const s of sk.sessions) {
        assertEquals(
            s.slots.map((x) => x.fixed_exercise_id),
            prev.main_lifts.map((l) => l.exercise_id),
        );
    }
});

Deno.test("minimal without a previous block uses the main slots of the skeleton with the normal ranges", () => {
    const sk = buildSkeleton(
        prefs({ phase: "minimal", age: 30, previous: null }),
    );
    for (const s of sk.sessions[0].slots) {
        assertEquals(s.fixed_exercise_id, undefined);
        assertEquals([s.rep_min, s.rep_max], [6, 10]);
    }
});

Deno.test("minimal candidates are the previous main lifts; one that left the catalogue falls back to the group", () => {
    const catalog = fullCatalog();
    const prev = previous({}, 2, catalog);
    const p = prefs({ phase: "minimal", age: 30, previous: prev });
    const sk = buildSkeleton(p);
    const cands = buildCandidates(catalog, p, sk);
    sk.sessions[0].slots.forEach((slot, i) => {
        assertEquals(
            cands.get(slot.slot_id)!.map((e) => e.id),
            [prev.main_lifts[i].exercise_id],
        );
    });
    const gone = catalog.filter((e) => e.id !== prev.main_lifts[0].exercise_id);
    const again = buildCandidates(gone, p, sk);
    assert(again.get(sk.sessions[0].slots[0].slot_id)!.length > 1);
});

Deno.test("previousBlock keeps only the main lifts still in the catalogue", () => {
    const catalog = fullCatalog();
    const mains = catalog.filter((e) => e.movement_pattern === "squat");
    const history: PhaseHistory = {
        previous: {
            phase: "cut",
            sets_per_group: { quads: 10 },
            main_lifts: [mains[0].id, "00000000-0000-4000-8000-ffffffffffff"],
            rep_ranges: { [mains[0].id]: [6, 10] },
        },
        minimal_blocks_in_a_row: 0,
        deficit_weeks_done: 0,
    };
    const prev = previousBlock(history, catalog)!;
    assertEquals(prev.main_lifts.map((l) => l.exercise_id), [mains[0].id]);
    assertEquals(prev.main_lifts[0].muscle_group, "quads");
    assertEquals(previousBlock({ ...history, previous: null }, catalog), null);
});

// --- cardio by phase ---

Deno.test("splitCardio with the minimal cap: 100 minutes over 1 routine is 30 + 70, over 2 is 30 / 30 + 40", () => {
    assertEquals(splitCardio(100, 1, 30), {
        per_routine: [30],
        planned: 30,
        on_your_own: 70,
    });
    assertEquals(splitCardio(100, 2, 30), {
        per_routine: [30, 30],
        planned: 60,
        on_your_own: 40,
    });
});

Deno.test("cardioTargetFor: 100 in the minimal phase, 150 after 4 in a row, 150 in every other phase", () => {
    assertEquals(cardioTargetFor("minimal", 0, false, 150), 100);
    assertEquals(cardioTargetFor("minimal", 3, false, 100), 100);
    assertEquals(cardioTargetFor("minimal", 4, false, 100), 150);
    for (
        const phase of [
            "cut",
            "gain",
            "maintenance",
            "recomposition",
        ] as const
    ) {
        assertEquals(cardioTargetFor(phase, 0, false, 150), 150);
        // A 100 left on the plan by an earlier minimal block does not leak into another phase.
        assertEquals(cardioTargetFor(phase, 0, false, 100), 150);
    }
});

Deno.test("cardioTargetFor: the user's own edit wins, and the band of fat loss is kept in maintenance", () => {
    assertEquals(cardioTargetFor("maintenance", 0, true, 240), 240);
    const a = computeActivity({
        intention: "fat_loss",
        stepsAvg28: null,
        phase: "maintenance",
    });
    assertEquals(a.cardio_band, [CARDIO_MIN_WEEK, CARDIO_BAND_MAX]);
    assertEquals(cardioTargetFor("minimal", 0, true, 60), 60);
    assertEquals(cardioTargetFor("cut", 0, false, undefined), 150);
});

Deno.test("the minimal skeleton plans the cardio with the 30-minute cap", () => {
    const sk = buildSkeleton(
        prefs({ phase: "minimal", age: 30, cardio_min_week: 100 }),
    );
    assertEquals(sk.cardio.per_routine_min, [30]);
    assertEquals(sk.cardio.on_your_own_min, 70);
    assertEquals(sk.sessions[0].cardio?.minutes, 30);
    const old = buildSkeleton(
        prefs({ phase: "minimal", age: 66, cardio_min_week: 100 }),
    );
    assertEquals(old.cardio.per_routine_min, [30, 30]);
    assertEquals(old.cardio.on_your_own_min, 40);
});

Deno.test("the cardio of a normal block is untouched by the phase (60 cap)", () => {
    const sk = buildSkeleton(
        prefs({ phase: "maintenance", days_per_week: 3, cardio_min_week: 300 }),
    );
    assertEquals(sk.cardio.per_routine_min, [60, 60, 60]);
    assertEquals(sk.cardio.on_your_own_min, 120);
});

// --- invariants of a minimal block (mirror of coach_assert_block) ---

function minimalBlock(over: Partial<BlockPrefs> = {}) {
    const p = prefs({
        phase: "minimal",
        age: 30,
        cardio_min_week: 100,
        ...over,
    });
    const catalog = fullCatalog();
    const sk = buildSkeleton(p);
    const cands = buildCandidates(catalog, p, sk);
    const picks: ModelBlockPick[] = [];
    for (const s of sk.sessions) {
        for (const slot of s.slots) {
            picks.push({
                slot_id: slot.slot_id,
                exercise_key: candidateKey(cands.get(slot.slot_id)![0]),
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
    return { p, catalog, block };
}

const rule = (f: () => void) => {
    const e = assertThrows(f, BlockInvariantError);
    return e.rule;
};

Deno.test("a minimal block passes and carries training.phase", () => {
    const { p, catalog, block } = minimalBlock();
    assertEquals(block.training.phase, "minimal");
    assertBlockInvariants(block, p, catalog);
    const old = minimalBlock({ age: 65 });
    assertBlockInvariants(old.block, old.p, old.catalog);
    assertEquals(old.block.sessions.length, 2);
});

Deno.test("a minimal block with a previous block keeps its ranges and passes", () => {
    const { p, catalog, block } = minimalBlock({ previous: previous({}, 3) });
    assertBlockInvariants(block, p, catalog);
});

Deno.test("minimal rules are enforced: sessions, sets, main lifts only", () => {
    const young = minimalBlock();
    // Two sessions under 65.
    const two = minimalBlock({ age: 65 });
    assertEquals(
        rule(() =>
            assertBlockInvariants(two.block, { ...two.p, age: 30 }, two.catalog)
        ),
        "minimal_sessions",
    );
    // One session from 65.
    assertEquals(
        rule(() =>
            assertBlockInvariants(
                young.block,
                { ...young.p, age: 65 },
                young.catalog,
            )
        ),
        "minimal_sessions",
    );
    // Two sets under 65.
    const sets = minimalBlock();
    isLifting(sets.block.sessions[0].exercises[0]) &&
        (sets.block.sessions[0].exercises[0].sets = 2);
    sets.block.training.weekly_sets = { ...sets.block.training.weekly_sets };
    assertEquals(
        rule(() => assertBlockInvariants(sets.block, sets.p, sets.catalog)),
        "minimal_sets",
    );
    // An isolation exercise.
    const iso = minimalBlock();
    const first = iso.block.sessions[0].exercises[0];
    if (isLifting(first)) first.role = "isolation";
    assertEquals(
        rule(() => assertBlockInvariants(iso.block, iso.p, iso.catalog)),
        "minimal_main_only",
    );
});

Deno.test("a minimal cardio over 30 minutes in one routine is rejected", () => {
    const m = minimalBlock();
    const c = m.block.training.cardio;
    c.per_routine_min = [60];
    c.planned_min_week = 60;
    c.on_your_own_min = 40;
    m.block.training.routine_total_min = [120];
    const last = m.block.sessions[0].exercises.at(-1)!;
    if ("duration_min" in last) last.duration_min = 60;
    assertEquals(
        rule(() => assertBlockInvariants(m.block, m.p, m.catalog)),
        "cardio_total",
    );
});

Deno.test("a non-minimal block is not subject to the minimal rules", () => {
    const p = prefs({ phase: "cut", days_per_week: 3 });
    const catalog = fullCatalog();
    const sk = buildSkeleton(p);
    assertEquals(sk.phase, "cut");
    assertEquals(sk.sessions.length, 3);
    assert(slots(sk).some((s) => s.role === "isolation"));
    assert(catalog.length > 0);
});
