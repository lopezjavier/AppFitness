import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    buildEligibleCatalogue,
    candidateKey,
    equipmentAllowed,
    equipmentRank,
} from "./candidates.ts";
import { CoachInputError } from "./context.ts";
import { cardio, ex, fullCatalog } from "./blockFixtures.ts";

const gym = { place: "gym", equipment: [] as string[] };
const home = (equipment: string[]) => ({ place: "home", equipment });
const keysOf = (c: ReturnType<typeof buildEligibleCatalogue>) =>
    c.exercises.map(candidateKey);

Deno.test("dumbbell + bodyweight keeps only that equipment", () => {
    const catalog = [
        ex({ key: "a-bar", equipment: "barbell" }),
        ex({ key: "b-db", equipment: "dumbbell" }),
        ex({ key: "c-bw", equipment: "bodyweight" }),
    ];
    assertEquals(
        keysOf(
            buildEligibleCatalogue(catalog, home(["dumbbell", "bodyweight"])),
        ),
        ["b-db", "c-bw"],
    );
});

Deno.test("a gym with no equipment selected allows all of it; a home with none allows nothing", () => {
    const catalog = [ex({ key: "a", equipment: "barbell" })];
    assertEquals(keysOf(buildEligibleCatalogue(catalog, gym)), ["a"]);
    assert(!equipmentAllowed(home([]), "barbell"));
    assertThrows(
        () => buildEligibleCatalogue(catalog, home([])),
        CoachInputError,
    );
});

Deno.test("cardio, null pattern and non-strength metrics never reach the model", () => {
    const catalog = [
        ex({ key: "ok" }),
        cardio("walking", "bodyweight"),
        ex({ key: "no-pattern", movement_pattern: null }),
        ex({ key: "timed", metric_type: "duration" }),
    ];
    assertEquals(keysOf(buildEligibleCatalogue(catalog, gym)), ["ok"]);
});

Deno.test("carry and other patterns are offered as isolation-like work", () => {
    const catalog = [
        ex({ key: "farmer", movement_pattern: "carry" }),
        ex({ key: "misc", movement_pattern: "other" }),
    ];
    assertEquals(keysOf(buildEligibleCatalogue(catalog, gym)), [
        "farmer",
        "misc",
    ]);
});

Deno.test("exercises with an unresolved discomfort are removed, by id", () => {
    const a = ex({ key: "a" });
    const b = ex({ key: "b" });
    assertEquals(keysOf(buildEligibleCatalogue([a, b], gym, [a.id])), ["b"]);
});

Deno.test("the catalogue is unranked and uncapped: 40 rows of one pattern all arrive, sorted by key", () => {
    const rows = Array.from(
        { length: 40 },
        (_, i) =>
            ex({
                key: `chest-press-${String(40 - i).padStart(2, "0")}`,
                // Equipment order must not rank anything for the model.
                equipment: i % 2 ? "bodyweight" : "barbell",
            }),
    );
    const c = buildEligibleCatalogue(rows, gym);
    assertEquals(c.exercises.length, 40);
    const keys = c.byGroup.chest.horizontal_push;
    assertEquals(keys.length, 40);
    assertEquals(keys, [...keys].sort());
});

Deno.test("by group and pattern: the side-delta pattern is its own list under shoulders", () => {
    const c = buildEligibleCatalogue(fullCatalog(), gym);
    assertEquals(
        Object.keys(c.byGroup.shoulders).sort(),
        ["shoulder_abduction", "vertical_push"],
    );
    assertEquals(Object.keys(c.byGroup.quads).sort(), ["isolation", "squat"]);
    assert(!("full_body" in c.byGroup));
});

Deno.test("a custom exercise counts only with a movement pattern and is keyed by id", () => {
    const custom = ex({
        key: null,
        is_custom: true,
        movement_pattern: "isolation",
    });
    const noPattern = ex({
        key: null,
        is_custom: true,
        movement_pattern: null,
    });
    const c = buildEligibleCatalogue([custom, noPattern], gym);
    assertEquals(keysOf(c), [custom.id]);
});

Deno.test("nothing eligible is infeasible", () => {
    assertThrows(() => buildEligibleCatalogue([], gym), CoachInputError);
});

Deno.test("swap order (kept for the server-side lists): free weights and machines before bodyweight, other last", () => {
    const order = [
        "barbell",
        "ez_bar",
        "dumbbell",
        "machine",
        "cable",
        "kettlebell",
        "bodyweight",
        "band",
        "band_long",
        "cardio_machine",
        "other",
    ];
    const ranks = order.map(equipmentRank);
    assertEquals(ranks, [...ranks].sort((x, y) => x - y));
    assert(equipmentRank("unknown") > equipmentRank("other"));
});
