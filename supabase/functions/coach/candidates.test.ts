import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { buildSkeleton } from "./blockRules.ts";
import {
    buildCandidates,
    candidateKey,
    filterCandidates,
} from "./candidates.ts";
import { CoachInputError } from "./context.ts";
import type { CatalogExercise } from "./schemas.ts";
import { ex, fullCatalog, prefs } from "./blockFixtures.ts";

const mainChest = { muscle_group: "chest", role: "main" } as const;

Deno.test("dumbbell + bodyweight keeps only that equipment", () => {
    const list = [
        ex({ key: "bb", equipment: "barbell" }),
        ex({ key: "db", equipment: "dumbbell" }),
        ex({ key: "bw", equipment: "bodyweight" }),
        ex({ key: "cb", equipment: "cable" }),
    ];
    const got = filterCandidates(
        list,
        { place: "home", equipment: ["dumbbell", "bodyweight"] },
        mainChest,
    );
    assertEquals(got.map((e) => e.key), ["db", "bw"]);
});

Deno.test("a gym with no equipment selected allows all of it", () => {
    const list = [ex({ equipment: "barbell" }), ex({ equipment: "machine" })];
    assertEquals(
        filterCandidates(list, { place: "gym", equipment: [] }, mainChest)
            .length,
        2,
    );
});

Deno.test("a home with no equipment selected allows nothing", () => {
    const list = [ex({ equipment: "bodyweight" })];
    assertEquals(
        filterCandidates(list, { place: "home", equipment: [] }, mainChest)
            .length,
        0,
    );
});

Deno.test("cardio, carry, other, null pattern and non-strength metrics are excluded", () => {
    const list = ["cardio", "carry", "other", null].map((p) =>
        ex({ movement_pattern: p as CatalogExercise["movement_pattern"] })
    );
    list.push(ex({ metric_type: "duration" }));
    assertEquals(
        filterCandidates(list, { place: "gym", equipment: [] }, mainChest)
            .length,
        0,
    );
});

Deno.test("per-hand exercises are kept: the weight per hand does not affect a routine", () => {
    const got = filterCandidates(
        [ex({ per_hand: true, equipment: "dumbbell" })],
        { place: "gym", equipment: [] },
        mainChest,
    );
    assertEquals(got.length, 1);
});

Deno.test("lower-body main slots read the squats, lunges and hinges filed under glutes", () => {
    const list = [
        ex({ key: "sq", muscle_group: "glutes", movement_pattern: "squat" }),
        ex({ key: "rdl", muscle_group: "glutes", movement_pattern: "hinge" }),
        ex({ key: "lg", muscle_group: "glutes", movement_pattern: "lunge" }),
        ex({ key: "lp", muscle_group: "quads", movement_pattern: "squat" }),
    ];
    const gym = { place: "gym", equipment: [] };
    const keys = (g: "quads" | "hamstrings" | "glutes") =>
        filterCandidates(list, gym, { muscle_group: g, role: "main" })
            .map((e) => e.key).sort();
    assertEquals(keys("quads"), ["lg", "lp", "sq"]);
    assertEquals(keys("hamstrings"), ["rdl"]);
    assertEquals(keys("glutes"), ["lg", "rdl"]);
});

Deno.test("role selects the patterns: main lifts versus isolation and core", () => {
    const list = [
        ex({ key: "press", movement_pattern: "horizontal_push" }),
        ex({ key: "fly", movement_pattern: "isolation" }),
        ex({ key: "plank", muscle_group: "core", movement_pattern: "core" }),
    ];
    const gym = { place: "gym", equipment: [] };
    assertEquals(
        filterCandidates(list, gym, mainChest).map((e) => e.key),
        ["press"],
    );
    assertEquals(
        filterCandidates(list, gym, {
            muscle_group: "chest",
            role: "isolation",
        })
            .map((e) => e.key),
        ["fly"],
    );
    assertEquals(
        filterCandidates(list, gym, { muscle_group: "core", role: "isolation" })
            .map((e) => e.key),
        ["plank"],
    );
});

Deno.test("order is deterministic (catalogue first, then key) and capped at 12", () => {
    const list = Array.from(
        { length: 20 },
        (_, i) => ex({ key: `k${String(19 - i).padStart(2, "0")}` }),
    );
    const got = filterCandidates(
        [...list].reverse(),
        { place: "gym", equipment: [] },
        mainChest,
    );
    assertEquals(got.length, 12);
    assertEquals(got[0].key, "k00");
    assertEquals(got[11].key, "k11");
});

Deno.test("barbell and machine lifts rank before bodyweight variants", () => {
    const got = filterCandidates(
        [
            ex({ key: "a-push-up", equipment: "bodyweight" }),
            ex({ key: "z-bench", equipment: "barbell" }),
        ],
        { place: "gym", equipment: [] },
        mainChest,
    );
    assertEquals(got.map((e) => e.key), ["z-bench", "a-push-up"]);
});

Deno.test("a custom exercise counts only with a movement pattern and is keyed by id", () => {
    const withPattern = ex({
        key: null,
        is_custom: true,
        name: "Mi press",
    });
    const without = ex({
        key: null,
        is_custom: true,
        name: "Raro",
        movement_pattern: null,
    });
    const got = filterCandidates(
        [withPattern, without],
        { place: "gym", equipment: [] },
        mainChest,
    );
    assertEquals(got.length, 1);
    assertEquals(candidateKey(got[0]), withPattern.id);
});

Deno.test("buildCandidates answers every slot of a skeleton", () => {
    const p = prefs({ days_per_week: 3 });
    const sk = buildSkeleton(p);
    const map = buildCandidates(fullCatalog(), p, sk);
    for (const s of sk.sessions.flatMap((x) => x.slots)) {
        assert(map.get(s.slot_id)!.length > 0);
    }
});

Deno.test("an empty slot makes the block infeasible", () => {
    const p = prefs({ days_per_week: 3 });
    const sk = buildSkeleton(p);
    const err = assertThrows(
        () => buildCandidates([], p, sk),
        CoachInputError,
    );
    assertEquals(err.code, "coach_plan_infeasible");
});
