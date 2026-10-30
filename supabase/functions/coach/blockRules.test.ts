import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    buildSkeleton,
    chooseFrequency,
    exercisesPerSession,
    isMainLift,
    repScheme,
    requiredGroups,
    REST,
    sessionsPerMuscle,
    STRENGTH_RANGES,
    weeklySetsByMuscle,
} from "./blockRules.ts";
import { prefs } from "./blockFixtures.ts";

const slotsOf = (sk: ReturnType<typeof buildSkeleton>) =>
    sk.sessions.map((s) => s.slots);

Deno.test("days set by the user are used and not flagged as chosen", () => {
    const f = chooseFrequency(prefs({ days_per_week: 5 }));
    assertEquals(f.days_per_week, 5);
    assertEquals(f.days_chosen, false);
});

Deno.test("days unset: the coach chooses by length and experience and says so", () => {
    const short = chooseFrequency(
        prefs({ days_per_week: null, session_minutes: 45 }),
    );
    assertEquals([short.days_per_week, short.days_chosen], [3, true]);
    const beginner = chooseFrequency(
        prefs({ days_per_week: null, experience: "beginner" }),
    );
    assertEquals(beginner.days_per_week, 3);
    const inter = chooseFrequency(prefs({ days_per_week: null }));
    assertEquals(inter.days_per_week, 4);
    const advanced = chooseFrequency(
        prefs({ days_per_week: null, experience: "advanced" }),
    );
    assertEquals(advanced.days_per_week, 4);
});

Deno.test("session length unset defaults to 60 minutes", () => {
    const f = chooseFrequency(
        prefs({ days_per_week: null, session_minutes: null }),
    );
    assertEquals(f.session_minutes, 60);
});

Deno.test("exercises per session follow the length, clamped 3 to 8", () => {
    assertEquals(exercisesPerSession(15), 3);
    assertEquals(exercisesPerSession(60), 5);
    assertEquals(exercisesPerSession(180), 8);
});

Deno.test("one day is a single full-body session", () => {
    const sk = buildSkeleton(prefs({ days_per_week: 1 }));
    assertEquals(sk.sessions.length, 1);
    assertEquals(sk.sessions[0].name, "Cuerpo completo");
});

Deno.test("split by days: 2 full-body, 3 full-body, 4 upper/lower, 6 push/pull/legs", () => {
    const names = (d: number) =>
        buildSkeleton(prefs({ days_per_week: d })).sessions.map((s) => s.name);
    assertEquals(names(2), ["Cuerpo completo A", "Cuerpo completo B"]);
    assertEquals(names(3).length, 3);
    assertEquals(names(4), [
        "Tren superior A",
        "Tren inferior A",
        "Tren superior B",
        "Tren inferior B",
    ]);
    assertEquals(names(6).slice(0, 3), [
        "Empuje A",
        "Tirón A",
        "Piernas A",
    ]);
    assertEquals(names(7).length, 7);
});

Deno.test("with 3 days every required muscle group appears in at least two sessions", () => {
    for (const minutes of [30, 45, 60, 90]) {
        const sk = buildSkeleton(
            prefs({ days_per_week: 3, session_minutes: minutes }),
        );
        const per = sessionsPerMuscle(slotsOf(sk));
        const required = requiredGroups(3, exercisesPerSession(minutes));
        assert(required.length >= 3);
        for (const g of required) assert(per[g] >= 2, `${g} at ${minutes}`);
    }
});

Deno.test("with 2 to 7 days every required group is trained twice a week", () => {
    for (let d = 2; d <= 7; d++) {
        const sk = buildSkeleton(prefs({ days_per_week: d }));
        const per = sessionsPerMuscle(slotsOf(sk));
        for (const g of requiredGroups(d, exercisesPerSession(60))) {
            assert(per[g] >= 2, `${g} with ${d} days`);
        }
    }
});

Deno.test("weekly sets equal the sum of the slot sets, near 10 per group", () => {
    const sk = buildSkeleton(prefs({ days_per_week: 4 }));
    const flat = sk.sessions.flatMap((s) => s.slots);
    const weekly = weeklySetsByMuscle(flat);
    assertEquals(
        Object.values(weekly).reduce((a, b) => a + b, 0),
        flat.reduce((a, s) => a + s.sets, 0),
    );
    assertEquals(weekly.chest, 10);
    assertEquals(weekly.quads, 10);
    assertEquals(weekly.core, 6);
});

Deno.test("beginners start lower than the 10-set base", () => {
    const sk = buildSkeleton(
        prefs({ days_per_week: 4, experience: "beginner" }),
    );
    const weekly = weeklySetsByMuscle(sk.sessions.flatMap((s) => s.slots));
    assertEquals(weekly.chest, 8);
});

Deno.test("rests per role are the defaults inside the bounds", () => {
    const sk = buildSkeleton(prefs({ days_per_week: 4 }));
    for (const slot of sk.sessions.flatMap((s) => s.slots)) {
        const b = REST[slot.role];
        assert(slot.rest_seconds >= b.min && slot.rest_seconds <= b.max);
    }
    assertEquals(REST.main.default, 150);
    assertEquals(REST.isolation.default, 90);
});

Deno.test("strength alternates main rep ranges across the sessions of a week", () => {
    const sk = buildSkeleton(
        prefs({ intention: "strength", days_per_week: 3 }),
    );
    assertEquals(sk.rep_scheme, "undulating");
    const mainRanges = sk.sessions.map((s) => {
        const m = s.slots.find((x) => x.role === "main")!;
        return [m.rep_min, m.rep_max];
    });
    assertEquals(mainRanges, STRENGTH_RANGES.map((r) => [...r]));
});

Deno.test("other intentions keep one range per role in every session", () => {
    for (
        const intention of [
            "muscle",
            "fat_loss",
            "recomposition",
            "maintenance",
        ] as const
    ) {
        assertEquals(repScheme(intention), "single");
        const sk = buildSkeleton(prefs({ intention, days_per_week: 4 }));
        const ranges = new Set(
            sk.sessions.flatMap((s) =>
                s.slots.map((x) => `${x.role}:${x.rep_min}-${x.rep_max}`)
            ),
        );
        assertEquals([...ranges].sort(), ["isolation:10-15", "main:6-10"]);
    }
});

Deno.test("main slots come before isolation slots in every session", () => {
    for (let d = 1; d <= 7; d++) {
        const sk = buildSkeleton(prefs({ days_per_week: d }));
        for (const s of sk.sessions) {
            const roles = s.slots.map((x) => x.role);
            const firstIso = roles.indexOf("isolation");
            if (firstIso >= 0) {
                assert(!roles.slice(firstIso).includes("main"));
            }
            assertEquals(s.slots.length, 5);
        }
    }
});

Deno.test("slot ids are unique and sets stay within 1 to 10", () => {
    const sk = buildSkeleton(prefs({ days_per_week: 7, session_minutes: 90 }));
    const ids = sk.sessions.flatMap((s) => s.slots.map((x) => x.slot_id));
    assertEquals(new Set(ids).size, ids.length);
    for (const s of sk.sessions.flatMap((x) => x.slots)) {
        assert(s.sets >= 1 && s.sets <= 10);
    }
});

Deno.test("isMainLift is true for the multi-joint patterns only", () => {
    const main = [
        "squat",
        "hinge",
        "lunge",
        "horizontal_push",
        "vertical_push",
        "horizontal_pull",
        "vertical_pull",
    ];
    const other = ["carry", "core", "isolation", "cardio", "other"];
    for (const p of main) assert(isMainLift(p), p);
    for (const p of other) assert(!isMainLift(p), p);
    assert(!isMainLift(null));
});
