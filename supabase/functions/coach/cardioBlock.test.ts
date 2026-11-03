// Cardio in the block (T-182, planning/04 sections 9.4 and 9.11): split, candidates and swap list.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { splitCardio } from "./blockRules.ts";
import { cardio, cardioCatalog, ex } from "./blockFixtures.ts";
import {
    candidateKey,
    cardioDefaultCandidates,
    cardioEquipmentAllowed,
    cardioOptions,
    isCardioEligible,
    resolvedEquipment,
} from "./candidates.ts";
import type { CatalogExercise } from "./schemas.ts";

Deno.test("splitCardio: whole 5-minute units, remainder to the first routines", () => {
    assertEquals(splitCardio(150, 3), {
        per_routine: [50, 50, 50],
        planned: 150,
        on_your_own: 0,
    });
    assertEquals(splitCardio(150, 4).per_routine, [40, 40, 35, 35]);
    assertEquals(splitCardio(10, 3).per_routine, [5, 5, 0]);
    assertEquals(splitCardio(5, 7).per_routine, [5, 0, 0, 0, 0, 0, 0]);
});

Deno.test("splitCardio: at most 60 min per routine, the rest is on your own", () => {
    assertEquals(splitCardio(300, 3), {
        per_routine: [60, 60, 60],
        planned: 180,
        on_your_own: 120,
    });
    assertEquals(splitCardio(150, 1), {
        per_routine: [60],
        planned: 60,
        on_your_own: 90,
    });
});

Deno.test("splitCardio: target 0 plans no cardio anywhere", () => {
    assertEquals(splitCardio(0, 4), {
        per_routine: [0, 0, 0, 0],
        planned: 0,
        on_your_own: 0,
    });
});

Deno.test("splitCardio: planned plus on your own is the target and no routine passes 60 (1-7 routines)", () => {
    for (let r = 1; r <= 7; r++) {
        for (const target of [0, 5, 90, 150, 300, 500, 600]) {
            const s = splitCardio(target, r);
            assertEquals(s.per_routine.length, r);
            assertEquals(s.planned + s.on_your_own, target);
            assertEquals(s.per_routine.reduce((a, b) => a + b, 0), s.planned);
            assert(s.per_routine.every((m) => m % 5 === 0 && m <= 60));
        }
    }
});

// --- candidates ---

const keys = (l: CatalogExercise[]) => l.map(candidateKey);
const home = (equipment: string[]) => ({ place: "home", equipment });
const defaultKey = (
    p: { place: string; equipment: string[] },
    favorites: string[] = [],
) => keys(cardioDefaultCandidates(cardioCatalog(), p, favorites));

Deno.test("cardio eligibility: cardio pattern, duration metric, not an interval move", () => {
    const eligible = cardioCatalog().filter(isCardioEligible).map(candidateKey)
        .sort();
    assertEquals(eligible, [
        "air-bike",
        "elliptical-trainer",
        "incline-treadmill-walk",
        "rowing-machine",
        "running",
        "ski-erg",
        "stationary-bike",
        "treadmill-running",
        "walking",
    ]);
    assert(
        !isCardioEligible(
            ex({ movement_pattern: "isolation", metric_type: "duration" }),
        ),
    );
});

Deno.test("home with dumbbells and bodyweight: only bodyweight cardio, default walking", () => {
    const p = home(["dumbbell", "bodyweight"]);
    assertEquals(keys(cardioOptions(cardioCatalog(), p)), [
        "running",
        "walking",
    ]);
    assertEquals(defaultKey(p), ["walking"]);
});

Deno.test("the default follows the selected cardio machine", () => {
    assertEquals(defaultKey(home(["cardio_machine"])), ["stationary-bike"]);
    assertEquals(defaultKey(home(["machine"])), ["walking"]);
    assertEquals(defaultKey({ place: "gym", equipment: [] }), [
        "stationary-bike",
    ]);
});

Deno.test("never by default: running, treadmill running, air bike, ski erg, stair climber, interval moves", () => {
    const never = [
        "running",
        "treadmill-running",
        "air-bike",
        "ski-erg",
        "stair-climber",
        "jumping-jacks",
        "jump-rope",
        "battle-ropes",
        "mountain-climbers",
    ];
    for (
        const equipment of [[], ["cardio_machine"], ["bodyweight"]]
    ) {
        for (const place of ["gym", "home"]) {
            const [k] = defaultKey({ place, equipment });
            assert(!never.includes(k), `${place} ${equipment}: ${k}`);
        }
    }
});

Deno.test("the most-logged favourite wins inside the equipment and is ignored outside it", () => {
    assertEquals(
        defaultKey(home(["cardio_machine"]), ["rowing-machine"]),
        ["rowing-machine"],
    );
    assertEquals(defaultKey(home(["bodyweight"]), ["rowing-machine"]), [
        "walking",
    ]);
    // Running is allowed as a default when the user logs it.
    assertEquals(defaultKey(home([]), ["running"]), ["running"]);
    // An interval move is never a default, even if logged.
    assertEquals(defaultKey(home([]), ["jump-rope"]), ["walking"]);
});

Deno.test("the swap list does not depend on the exercise on the slot and leaves out the interval moves", () => {
    const p = home(["cardio_machine"]);
    // The stair climber has a floors metric, so it is not continuous cardio: seven machines plus walking and running.
    const expected = [
        "air-bike",
        "elliptical-trainer",
        "incline-treadmill-walk",
        "rowing-machine",
        "running",
        "ski-erg",
        "stationary-bike",
        "treadmill-running",
        "walking",
    ];
    assertEquals(keys(cardioOptions(cardioCatalog(), p)).sort(), expected);
});

Deno.test("a custom cardio exercise is offered last", () => {
    const custom = cardio("my-cardio", "bodyweight", { is_custom: true });
    const list = keys(
        cardioOptions([custom, ...cardioCatalog()], home(["bodyweight"])),
    );
    assertEquals(list[list.length - 1], "my-cardio");
});

Deno.test("cardio equipment: walking and running always, bodyweight only when selected", () => {
    const bike = cardio("stationary-bike", "cardio_machine");
    const walking = cardio("walking", "bodyweight");
    assert(cardioEquipmentAllowed(home([]), walking));
    assert(!cardioEquipmentAllowed(home([]), bike));
    assert(cardioEquipmentAllowed({ place: "gym", equipment: [] }, bike));
    assert(!cardioEquipmentAllowed(home(["bodyweight"]), bike));
    assert(cardioEquipmentAllowed(home(["cardio_machine"]), bike));
});

Deno.test("resolvedEquipment: nothing selected outside a gym means bodyweight; a gym stays empty", () => {
    assertEquals(resolvedEquipment(home([])), ["bodyweight"]);
    assertEquals(resolvedEquipment({ place: "gym", equipment: [] }), []);
    assertEquals(resolvedEquipment(home(["dumbbell"])), ["dumbbell"]);
});
