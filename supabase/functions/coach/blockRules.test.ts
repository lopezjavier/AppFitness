import { assertEquals } from "jsr:@std/assert@1.0.19";
import {
    cardioTargetFor,
    chooseFrequency,
    isMainLift,
    sessionsPerMuscle,
    weeklySetsByMuscle,
} from "./blockRules.ts";
import { computeActivity } from "./cardioRules.ts";

Deno.test("days set by the user are used as they are", () => {
    assertEquals(
        chooseFrequency({
            days_per_week: 5,
            session_minutes: 45,
            experience: "beginner",
        }),
        { days_per_week: 5, days_chosen: false, session_minutes: 45 },
    );
});

Deno.test("days unset: the coach chooses and says so (3 for short sessions or beginners, else 4)", () => {
    const f = (minutes: number | null, experience: "beginner" | "advanced") =>
        chooseFrequency({
            days_per_week: null,
            session_minutes: minutes,
            experience,
        });
    assertEquals(f(45, "advanced"), {
        days_per_week: 3,
        days_chosen: true,
        session_minutes: 45,
    });
    assertEquals(f(60, "beginner").days_per_week, 3);
    assertEquals(f(60, "advanced").days_per_week, 4);
    assertEquals(f(null, "advanced").session_minutes, 60);
});

Deno.test("cardioTargetFor: 150 in every phase, the user's own edit wins", () => {
    for (
        const phase of ["cut", "gain", "maintenance", "recomposition"] as const
    ) {
        assertEquals(cardioTargetFor(phase, false, 150), 150);
        assertEquals(cardioTargetFor(phase, false, 100), 150);
        assertEquals(cardioTargetFor(phase, false, undefined), 150);
        assertEquals(cardioTargetFor(phase, true, 240), 240);
        assertEquals(cardioTargetFor(phase, true, 0), 0);
    }
    // The band of fat loss is kept in maintenance and does not change the target.
    const a = computeActivity({
        intention: "fat_loss",
        stepsAvg28: null,
        phase: "maintenance",
    });
    assertEquals(a.cardio_band, [150, 300]);
    assertEquals(a.cardio_min_week, 150);
});

Deno.test("main lifts are the multi-joint patterns", () => {
    for (
        const p of [
            "squat",
            "hinge",
            "lunge",
            "horizontal_push",
            "vertical_push",
            "horizontal_pull",
            "vertical_pull",
        ]
    ) assertEquals(isMainLift(p), true, p);
    for (
        const p of [
            "isolation",
            "shoulder_abduction",
            "core",
            "carry",
            "cardio",
            null,
        ]
    ) assertEquals(isMainLift(p), false, String(p));
});

Deno.test("weekly sets and sessions per muscle are plain server arithmetic", () => {
    assertEquals(
        weeklySetsByMuscle([
            { muscle_group: "chest", sets: 3 },
            { muscle_group: "chest", sets: 2 },
            { muscle_group: "back", sets: 4 },
        ]),
        { chest: 5, back: 4 },
    );
    assertEquals(
        sessionsPerMuscle([
            [{ muscle_group: "chest" }, { muscle_group: "chest" }],
            [{ muscle_group: "chest" }, { muscle_group: "back" }],
        ]),
        { chest: 2, back: 1 },
    );
});
