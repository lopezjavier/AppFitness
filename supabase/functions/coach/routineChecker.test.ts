import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    type CheckerCode,
    type CheckerExercise,
    type CheckerInput,
    type CheckerNote,
    type CheckerRoutine,
    type CheckerSet,
    checkRoutines,
    fractionalSets,
    isKneeExtensionKey,
    KNEE_EXTENSION_KEYS,
    routineSeconds,
} from "./routineChecker.ts";
import { isMainLift } from "./blockRules.ts";
import type { MuscleGroup } from "./schemas.ts";

// --- Fixtures -----------------------------------------------------------------------------------------------------

const set = (o: Partial<CheckerSet> = {}): CheckerSet => ({
    type: "working",
    rep_min: 10,
    rep_max: 12,
    rpe: 8,
    rest_seconds: 90,
    ...o,
});

type ExOpts = Partial<Omit<CheckerExercise, "sets">> & {
    n?: number;
    set?: Partial<CheckerSet>;
    sets?: CheckerSet[];
};

/** An exercise with `n` identical sets; main lifts default to 6-10 reps and 150 s rest. */
const ex = (
    key: string,
    group: MuscleGroup,
    pattern: string | null,
    o: ExOpts = {},
): CheckerExercise => {
    const main = isMainLift(pattern);
    const base = main ? { rep_min: 6, rep_max: 10, rest_seconds: 150 } : {};
    return {
        exercise_key: key,
        muscle_group: group,
        secondary_muscles: [],
        pattern,
        equipment: "machine",
        ...o,
        sets: o.sets ??
            Array.from({ length: o.n ?? 3 }, () => set({ ...base, ...o.set })),
    };
};

const routine = (...exercises: CheckerExercise[]): CheckerRoutine => ({
    name: "Rutina",
    exercises,
});

const input = (
    routines: CheckerRoutine[],
    o: Partial<CheckerInput> = {},
): CheckerInput => ({
    routines,
    intention: "muscle",
    days_per_week: routines.length,
    session_minutes: 300,
    equipment: [],
    exclusions: [],
    ...o,
});

const codes = (i: CheckerInput): CheckerCode[] =>
    checkRoutines(i).failures.map((f) => f.code);
const has = (i: CheckerInput, code: CheckerCode): boolean =>
    codes(i).includes(code);
const notes = (i: CheckerInput): CheckerNote[] =>
    checkRoutines(i).notes.map((n) => n.code);

const lateral = (o: ExOpts = {}) =>
    ex("lateral-raise", "shoulders", "shoulder_abduction", o);
const legExtension = (o: ExOpts = {}) =>
    ex("leg-extension", "quads", "isolation", o);
const bench = (o: ExOpts = {}) =>
    ex("bench-press", "chest", "horizontal_push", o);
const squat = (o: ExOpts = {}) => ex("squat", "quads", "squat", o);

// --- Fractional counting (04 section 4) ---------------------------------------------------------------------------

Deno.test("fractional sets: primary 1, each distinct secondary 0.5", () => {
    const r = routine(
        ex("bench", "chest", "horizontal_push", {
            n: 4,
            secondary_muscles: ["shoulders", "triceps"],
        }),
    );
    const c = fractionalSets([r]);
    assertEquals(c.get("chest"), 4);
    assertEquals(c.get("shoulders"), 2);
    assertEquals(c.get("triceps"), 2);
});

Deno.test("fractional sets: a secondary equal to the primary or repeated counts once, warm-ups are out, a drop set is one set", () => {
    const r = routine(
        ex("row", "back", "horizontal_pull", {
            n: 2,
            secondary_muscles: ["back", "biceps", "biceps"],
        }),
        ex("curl", "biceps", "isolation", {
            sets: [
                set({ type: "warm_up" }),
                set(),
                set({ type: "drop" }),
            ],
        }),
    );
    const c = fractionalSets([r]);
    assertEquals(c.get("back"), 2);
    assertEquals(c.get("biceps"), 1 + 2);
});

// --- H1 weekly sets -----------------------------------------------------------------------------------------------

/** Two routines with `chest` primary sets and an optional half-set contribution; the other groups are not judged here. */
const chestVolume = (primary: number, halfSets = 0) => {
    const exercises = [
        ex("press-a", "chest", "isolation", { n: Math.min(primary, 10) }),
    ];
    if (primary > 10) {
        exercises.push(
            ex("press-b", "chest", "isolation", { n: primary - 10 }),
        );
    }
    if (halfSets > 0) {
        exercises.push(
            ex("raise", "shoulders", "isolation", {
                n: halfSets,
                secondary_muscles: ["chest"],
            }),
        );
    }
    return input([routine(...exercises), routine(ex("x", "core", "core"))]);
};
const chestFailure = (i: CheckerInput) =>
    checkRoutines(i).failures.find((f) =>
        (f.code === "h1_volume_low" || f.code === "h1_volume_high") &&
        f.data.group === "chest"
    )?.code;

Deno.test("H1 boundaries at 9.5, 10, 20 and 20.5 fractional sets", () => {
    assertEquals(chestFailure(chestVolume(9, 1)), "h1_volume_low"); // 9.5
    assertEquals(chestFailure(chestVolume(10)), undefined);
    assertEquals(chestFailure(chestVolume(20)), undefined);
    assertEquals(chestFailure(chestVolume(20, 1)), "h1_volume_high"); // 20.5
});

Deno.test("H1 'Mantener' groups need only 4 weekly sets, 6 from 65", () => {
    const four = chestVolume(4);
    assertEquals(chestFailure(four), "h1_volume_low");
    assertEquals(
        chestFailure({ ...four, maintain_groups: ["chest"], age: 40 }),
        undefined,
    );
    const five = chestVolume(5);
    assertEquals(
        chestFailure({ ...five, maintain_groups: ["chest"], age: 64 }),
        undefined,
    );
    assertEquals(
        chestFailure({ ...five, maintain_groups: ["chest"], age: 65 }),
        "h1_volume_low",
    );
    assertEquals(
        chestFailure({
            ...chestVolume(6),
            maintain_groups: ["chest"],
            age: 65,
        }),
        undefined,
    );
});

Deno.test("below_recommended: one day, or a session too short to fit the minimum, drops H1 and adds the note", () => {
    const oneDay = input([routine(bench({ n: 3 }))], {
        session_minutes: 45,
    });
    assertEquals(notes(oneDay).includes("below_recommended"), true);
    assertEquals(codes(oneDay).some((c) => c.startsWith("h1_")), false);

    const shortWeek = input(
        [routine(bench({ n: 3 })), routine(squat({ n: 3 }))],
        { session_minutes: 30 },
    );
    assertEquals(notes(shortWeek).includes("below_recommended"), true);
    assertEquals(codes(shortWeek).some((c) => c.startsWith("h1_")), false);

    const longWeek = input(
        [routine(bench({ n: 3 })), routine(squat({ n: 3 }))],
        { session_minutes: 60 },
    );
    assertEquals(notes(longWeek).includes("below_recommended"), false);
    assertEquals(has(longWeek, "h1_volume_low"), true);
});

// --- H2 / H6 sessions ---------------------------------------------------------------------------------------------

Deno.test("H2: two or more days need at least two sessions; sessions must equal the days", () => {
    const one = input([routine(bench())], { days_per_week: 3 });
    assertEquals(has(one, "h2_sessions"), true);
    assertEquals(has(one, "h6_sessions"), true);
    const two = input([routine(bench()), routine(squat())], {
        days_per_week: 3,
    });
    assertEquals(has(two, "h2_sessions"), false);
    assertEquals(has(two, "h6_sessions"), true);
    assertEquals(
        has(input([routine(bench())], { days_per_week: 1 }), "h2_sessions"),
        false,
    );
});

// --- H3 / H3b -----------------------------------------------------------------------------------------------------

const torsoLeg = (extra: CheckerExercise[] = []) => [
    routine(bench({ n: 3 }), ...extra),
    routine(squat({ n: 3 })),
    routine(bench({ n: 3 })),
    routine(squat({ n: 3 })),
];

Deno.test("H3: 4 days of muscle building without a lateral raise fails; with 3 sets of one it passes", () => {
    assertEquals(
        has(input(torsoLeg(), { session_minutes: 60 }), "h3_side_delta"),
        true,
    );
    assertEquals(
        has(
            input(torsoLeg([lateral({ n: 3 })]), { session_minutes: 60 }),
            "h3_side_delta",
        ),
        false,
    );
});

Deno.test("H3b: a quads isolation exercise is required under the same conditions", () => {
    assertEquals(has(input(torsoLeg()), "h3b_knee_extension"), true);
    assertEquals(
        has(input(torsoLeg([legExtension()])), "h3b_knee_extension"),
        false,
    );
});

Deno.test("H3b: each of the four knee-extension keys satisfies it", () => {
    assertEquals([...KNEE_EXTENSION_KEYS].sort(), [
        "banded-terminal-knee-extension",
        "leg-extension",
        "reverse-nordic-curl",
        "single-leg-extension",
    ]);
    for (const key of KNEE_EXTENSION_KEYS) {
        const i = input(torsoLeg([ex(key, "quads", "isolation", { n: 2 })]));
        assertEquals(has(i, "h3b_knee_extension"), false, key);
    }
});

Deno.test("H3b: hip adduction, clamshell and fire hydrant isolation do not satisfy it", () => {
    const others: [string, MuscleGroup][] = [
        ["hip-adduction-machine", "adductors"],
        ["hip-adduction", "adductors"],
        ["clamshells", "glutes"],
        ["fire-hydrant", "glutes"],
        ["side-lying-hip-abduction", "glutes"],
    ];
    for (const [key, group] of others) {
        assertEquals(isKneeExtensionKey(key), false, key);
        const i = input(torsoLeg([ex(key, group, "isolation", { n: 3 })]));
        assertEquals(has(i, "h3b_knee_extension"), true, key);
    }
});

Deno.test("fractional sets: a squat adds quads sets and glutes at half weight; a sit-up adds no quads", () => {
    const squatSets = ex("squat", "quads", "squat", {
        n: 4,
        secondary_muscles: ["glutes"],
    });
    const sitUp = ex("sit-ups", "core", "core", { n: 3 });
    const c = fractionalSets([routine(squatSets, sitUp)]);
    assertEquals(c.get("quads"), 4);
    assertEquals(c.get("glutes"), 2);
    assertEquals(c.get("core"), 3);
    assertEquals(c.has("adductors"), false);
});

Deno.test("H1 ignores adductors: a plan with only adductor sets is not judged on them", () => {
    const i = input([
        routine(ex("hip-adduction", "adductors", "isolation", { n: 3 })),
    ]);
    const failing = checkRoutines(i).failures.filter((f) =>
        (f.data as { muscle_group?: string } | undefined)?.muscle_group ===
            "adductors"
    );
    assertEquals(failing.length, 0);
});

Deno.test("H3 and H3b are only a note for strength, one day, or no candidate inside the equipment", () => {
    const strength = input(torsoLeg(), { intention: "strength" });
    assertEquals(has(strength, "h3_side_delta"), false);
    assertEquals(has(strength, "h3b_knee_extension"), false);
    assertEquals(notes(strength).includes("h3_not_applicable"), true);
    assertEquals(notes(strength).includes("h3b_not_applicable"), true);

    const oneDay = input([routine(bench())]);
    assertEquals(has(oneDay, "h3_side_delta"), false);
    assertEquals(notes(oneDay).includes("h3b_not_applicable"), true);

    const noCandidate = input(torsoLeg(), {
        side_delta_available: false,
        knee_extension_available: false,
    });
    assertEquals(has(noCandidate, "h3_side_delta"), false);
    assertEquals(has(noCandidate, "h3b_knee_extension"), false);
    assertEquals(notes(noCandidate).includes("h3_not_applicable"), true);
});

Deno.test("a covered group needs no note", () => {
    const covered = input(torsoLeg([lateral(), legExtension()]));
    assertEquals(notes(covered).includes("h3_not_applicable"), false);
    assertEquals(notes(covered).includes("h3b_not_applicable"), false);
});

// --- H4 rep ranges ------------------------------------------------------------------------------------------------

const withReps = (
    intention: CheckerInput["intention"],
    e: CheckerExercise,
    min: number,
    max: number,
) => input(
    [routine({
        ...e,
        sets: e.sets.map((s) => ({ ...s, rep_min: min, rep_max: max })),
    })],
    { intention },
);

for (
    const [label, intention, make, min, max, fails] of [
        ["muscle 5-10", "muscle", bench, 5, 10, true],
        ["muscle 6-20", "muscle", bench, 6, 20, false],
        ["muscle 6-21", "muscle", bench, 6, 21, true],
        ["fat loss isolation 15-25", "fat_loss", legExtension, 15, 25, true],
        ["strength main 2-5", "strength", bench, 2, 5, true],
        ["strength main 3-8", "strength", bench, 3, 8, false],
        ["strength main 3-9", "strength", bench, 3, 9, true],
        ["strength main 10-15", "strength", squat, 10, 15, true],
        ["strength isolation 6-20", "strength", legExtension, 6, 20, false],
        ["strength isolation 3-8", "strength", legExtension, 3, 8, true],
        ["min above max", "muscle", bench, 12, 8, true],
    ] as const
) {
    Deno.test(`H4 ${label}`, () => {
        assertEquals(
            has(withReps(intention, make(), min, max), "h4_rep_range"),
            fails,
        );
    });
}

// --- H5 effort and set types --------------------------------------------------------------------------------------

for (
    const [rpe, fails] of [[6, true], [7, false], [9, false], [10, true], [
        7.5,
        true,
    ]] as const
) {
    Deno.test(`H5 RPE ${rpe}`, () => {
        const i = input([routine(bench({ set: { rpe } }))]);
        assertEquals(has(i, "h5_rpe"), fails);
    });
}

for (const type of ["warm_up", "failure"] as const) {
    Deno.test(`H5 set type ${type} is never planned`, () => {
        const i = input([routine(bench({ sets: [set({ type }), set()] }))]);
        assertEquals(has(i, "h5_set_type"), true);
    });
}

// --- H6 rest, sets, equipment, discomfort -------------------------------------------------------------------------

for (
    const [label, make, rest, fails] of [
        ["main 119", bench, 119, true],
        ["main 120", bench, 120, false],
        ["main 180", bench, 180, false],
        ["main 181", bench, 181, true],
        ["isolation 59", legExtension, 59, true],
        ["isolation 60", legExtension, 60, false],
        ["isolation 120", legExtension, 120, false],
        ["isolation 121", legExtension, 121, true],
    ] as const
) {
    Deno.test(`H6 rest ${label}`, () => {
        const i = input([routine(make({ set: { rest_seconds: rest } }))]);
        assertEquals(has(i, "h6_rest"), fails);
    });
}

for (
    const [n, fails] of [[0, true], [1, false], [10, false], [
        11,
        true,
    ]] as const
) {
    Deno.test(`H6 ${n} sets in one exercise`, () => {
        const i = input([routine(bench({ n }))]);
        assertEquals(has(i, "h6_sets_per_exercise"), fails);
    });
}

Deno.test("H6 equipment: an exercise outside the user's equipment fails; an empty list restricts nothing", () => {
    const r = [routine(bench({ equipment: "barbell" }))];
    assertEquals(
        has(input(r, { equipment: ["dumbbell"] }), "h6_equipment"),
        true,
    );
    assertEquals(
        has(input(r, { equipment: ["barbell"] }), "h6_equipment"),
        false,
    );
    assertEquals(has(input(r, { equipment: [] }), "h6_equipment"), false);
});

Deno.test("H6: an exercise with an unresolved level 4-5 discomfort is excluded", () => {
    const r = [routine(squat())];
    assertEquals(
        has(input(r, { exclusions: ["squat"] }), "h6_excluded_discomfort"),
        true,
    );
    assertEquals(
        has(
            input(r, { exclusions: ["bench-press"] }),
            "h6_excluded_discomfort",
        ),
        false,
    );
});

// --- H7 order -----------------------------------------------------------------------------------------------------

Deno.test("H7: for strength the main lifts go first; otherwise the order is free", () => {
    const wrong = [routine(legExtension(), squat())];
    assertEquals(
        has(input(wrong, { intention: "strength" }), "h7_order"),
        true,
    );
    assertEquals(has(input(wrong, { intention: "muscle" }), "h7_order"), false);
    const right = [routine(squat(), legExtension())];
    assertEquals(
        has(input(right, { intention: "strength" }), "h7_order"),
        false,
    );
});

// --- Time (D1-D3) -------------------------------------------------------------------------------------------------

/** 6 exercises x 3 sets x 10-12 reps x 90 s rest. */
const sixIsolations = () =>
    routine(
        ...Array.from({ length: 6 }, (_, i) =>
            ex(`iso-${i}`, "biceps", "isolation", { n: 3 })),
    );

Deno.test("time: 6 x (30 + 3 x 44 + 3 x 90) = 2 592 s = 43.2 min", () => {
    assertEquals(routineSeconds(sixIsolations()), 2592);
});

Deno.test("time limit: 2 592 s fit 44 minutes and exceed 43 (no tolerance)", () => {
    const r = [sixIsolations()];
    assertEquals(
        has(input(r, { session_minutes: 44, days_per_week: 1 }), "h6_time"),
        false,
    );
    const over = checkRoutines(
        input(r, { session_minutes: 43, days_per_week: 1 }),
    )
        .failures.find((f) => f.code === "h6_time");
    assertEquals(over?.data.over, 12);
});

// --- Supersets ----------------------------------------------------------------------------------------------------

const pair = (aSets = 3, bSets = 3) =>
    routine(
        ex("curl", "biceps", "isolation", {
            n: aSets,
            superset_group: 1,
            set: { rep_min: 10, rep_max: 10 },
        }),
        ex("pushdown", "triceps", "isolation", {
            n: bSets,
            superset_group: 1,
            set: { rep_min: 10, rep_max: 10 },
        }),
    );

Deno.test("superset time: per round work + work + the longer rest; one setup each", () => {
    // 60 + 3 x (40 + 40 + 90)
    assertEquals(routineSeconds(pair()), 570);
    // plain: 2 x (30 + 3 x (40 + 90))
    assertEquals(routineSeconds(pair(), true), 840);
});

Deno.test("a superset is accepted only when the plain routine does not fit", () => {
    const tight = input([pair()], { session_minutes: 10 });
    assertEquals(has(tight, "superset_not_needed"), false);
    assertEquals(has(tight, "h6_time"), false);
    const roomy = input([pair()], { session_minutes: 15 });
    assertEquals(has(roomy, "superset_not_needed"), true);
    const noPair = input(
        [pair().exercises.map((e) => ({ ...e, superset_group: null }))].map((
            es,
        ) => routine(...es)),
        {
            session_minutes: 10,
        },
    );
    assertEquals(has(noPair, "h6_time"), true);
});

Deno.test("superset_invalid: same muscle, three members, one member, or a main lift in strength", () => {
    const sameMuscle = routine(
        ex("a", "biceps", "isolation", { superset_group: 1 }),
        ex("b", "biceps", "isolation", { superset_group: 1 }),
    );
    assertEquals(
        has(input([sameMuscle], { session_minutes: 10 }), "superset_invalid"),
        true,
    );
    const three = routine(
        ex("a", "biceps", "isolation", { superset_group: 1 }),
        ex("b", "triceps", "isolation", { superset_group: 1 }),
        ex("c", "shoulders", "isolation", { superset_group: 1 }),
    );
    assertEquals(
        has(input([three], { session_minutes: 10 }), "superset_invalid"),
        true,
    );
    const alone = routine(
        ex("a", "biceps", "isolation", { superset_group: 1 }),
    );
    assertEquals(
        has(input([alone], { session_minutes: 10 }), "superset_invalid"),
        true,
    );
    const mains = routine(
        bench({ superset_group: 1 }),
        ex("row", "back", "horizontal_pull", { superset_group: 1 }),
    );
    assertEquals(
        has(
            input([mains], { intention: "strength", session_minutes: 10 }),
            "superset_invalid",
        ),
        true,
    );
    assertEquals(
        has(
            input([mains], { intention: "muscle", session_minutes: 10 }),
            "superset_invalid",
        ),
        false,
    );
});

Deno.test("superset members must have the same number of sets", () => {
    const i = input([pair(3, 4)], { session_minutes: 10 });
    assertEquals(has(i, "superset_sets_mismatch"), true);
    assertEquals(
        has(
            input([pair(3, 3)], { session_minutes: 10 }),
            "superset_sets_mismatch",
        ),
        false,
    );
});

// --- Drop sets ----------------------------------------------------------------------------------------------------

const dropRoutine = (sets: CheckerSet[], e = lateral) => routine(e({ sets }));

Deno.test("a drop set adds only its work time (no rest)", () => {
    const plain = routineSeconds(dropRoutine([set(), set()]));
    const withDrop = routineSeconds(
        dropRoutine([set(), set(), set({ type: "drop" })]),
    );
    assertEquals(withDrop - plain, 44);
});

Deno.test("drop_invalid: two drops, not last, on a main lift, or on a non-isolation exercise", () => {
    const check = (r: CheckerRoutine) =>
        has(input([r], { session_minutes: 1 }), "drop_invalid");
    assertEquals(
        check(
            dropRoutine([set(), set({ type: "drop" }), set({ type: "drop" })]),
        ),
        true,
    );
    assertEquals(check(dropRoutine([set({ type: "drop" }), set()])), true);
    assertEquals(
        check(dropRoutine([set(), set({ type: "drop" })], bench)),
        true,
    );
    assertEquals(
        check(
            routine(
                ex("row", "back", "horizontal_pull", {
                    sets: [set(), set({ type: "drop" })],
                }),
            ),
        ),
        true,
    );
    assertEquals(check(dropRoutine([set(), set({ type: "drop" })])), false);
    assertEquals(
        check(dropRoutine([set(), set({ type: "drop" })], legExtension)),
        false,
    );
});

Deno.test("a drop set is accepted only when the routine does not fit with it as a normal set", () => {
    const r = dropRoutine([set(), set(), set({ type: "drop" })]);
    // with the drop 30 + 2 x 134 + 44 = 342 s; as a normal set 342 + 90 = 432 s
    assertEquals(routineSeconds(r), 342);
    assertEquals(routineSeconds(r, true), 432);
    assertEquals(
        has(input([r], { session_minutes: 6 }), "drop_not_needed"),
        false,
    );
    assertEquals(has(input([r], { session_minutes: 6 }), "h6_time"), false);
    assertEquals(
        has(input([r], { session_minutes: 8 }), "drop_not_needed"),
        true,
    );
});

// --- A whole valid block ------------------------------------------------------------------------------------------

Deno.test("a valid 4-day muscle block passes with no failures and no notes", () => {
    const upper = routine(
        bench({ n: 5, secondary_muscles: ["triceps", "shoulders"] }),
        ex("row", "back", "horizontal_pull", {
            n: 4,
            secondary_muscles: ["biceps"],
        }),
        ex("ohp", "shoulders", "vertical_push", {
            n: 3,
            secondary_muscles: ["triceps"],
        }),
        lateral({ n: 3 }),
    );
    const lower = routine(
        squat({ n: 4, secondary_muscles: ["glutes", "hamstrings"] }),
        ex("rdl", "glutes", "hinge", {
            n: 4,
            secondary_muscles: ["hamstrings", "back"],
        }),
        legExtension({ n: 3 }),
        ex("curl", "hamstrings", "isolation", { n: 3 }),
    );
    const result = checkRoutines(
        input([upper, lower, upper, lower], { session_minutes: 90 }),
    );
    assertEquals(result.failures, []);
    assertEquals(result.notes, []);
    assert(routineSeconds(upper) < 90 * 60);
});
