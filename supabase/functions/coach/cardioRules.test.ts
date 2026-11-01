import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { computeActivity } from "./cardioRules.ts";
import { computeNutrition } from "./nutritionRules.ts";
import type { Intention } from "./schemas.ts";
import {
    assertActivityInvariants,
    NutritionInvariantError,
    validateOutcomesText,
} from "./validators.ts";

const INTENTIONS: Intention[] = [
    "strength",
    "muscle",
    "fat_loss",
    "recomposition",
    "maintenance",
];

for (const intention of INTENTIONS) {
    Deno.test(`cardio is 150 min for ${intention}`, () => {
        const a = computeActivity({ intention, stepsAvg28: 9000 });
        assertEquals(a.cardio_min_week, 150);
        assertEquals(a.cardio_edited, false);
        assertEquals(
            a.cardio_band,
            intention === "fat_loss" ? [150, 300] : null,
        );
    });
}

const steps = (avg: number | null) =>
    computeActivity({ intention: "muscle", stepsAvg28: avg });

for (
    const [avg, day, basis] of [
        [11000, 11000, "average"],
        [6740, 7000, "floor"],
        [6500, 7000, "floor"],
        [7740, 7500, "average"],
        [8260, 8000, "average"],
        [7000, 7000, "average"],
        [7499, 7000, "average"],
        [7500, 7500, "average"],
        [null, 7000, "floor"],
    ] as const
) {
    Deno.test(`steps average ${avg} gives ${day} (${basis})`, () => {
        const a = steps(avg);
        assertEquals(a.steps_day, day);
        assertEquals(a.steps_basis, basis);
        assertEquals(a.steps_avg, avg);
    });
}

Deno.test("no data gives the same steps target whatever the person (no age input)", () => {
    assertEquals(steps(null), steps(null));
    assertEquals(steps(null).steps_day, 7000);
});

Deno.test("invariants accept computed activity", () => {
    for (const avg of [null, 3000, 7740, 12000]) {
        assertActivityInvariants(steps(avg), avg);
    }
});

const rule = (mutate: (a: ReturnType<typeof steps>) => void, avg = 9000) => {
    const a = steps(avg);
    mutate(a);
    return assertThrows(
        () => assertActivityInvariants(a, avg),
        NutritionInvariantError,
    ).rule;
};

Deno.test("invariants reject cardio other than 150, steps under the floor, under the average or off-step", () => {
    assertEquals(rule((a) => a.cardio_min_week = 120), "cardio_minutes");
    assertEquals(rule((a) => a.steps_day = 6500), "steps_below_floor");
    assertEquals(rule((a) => a.steps_day = 8500), "steps_below_average");
    assertEquals(rule((a) => a.steps_day = 9100, 8000), "steps_not_multiple");
});

Deno.test("calories do not depend on the activity", () => {
    const input = {
        intention: "fat_loss" as const,
        sex: "male" as const,
        weightKg: 80,
        expenditureKcal: 2600,
        expenditureSource: "formula",
        experience: "intermediate" as const,
    };
    const base = computeNutrition(input);
    const stored = { ...computeNutrition(input), activity: steps(11000) };
    const { activity: _a, ...rest } = stored;
    assertEquals(rest, base);
});

Deno.test("the outcome text validator rejects advice to do fewer steps", () => {
    const cands = [{
        id: "weight",
        kind: "weight" as const,
        key: "weight",
        target_value: 78,
        unit: "kg" as const,
        default_deadline: "2026-11-30",
        basis: {},
    }];
    const t = JSON.stringify({
        outcomes: [{
            candidate_id: "weight",
            reason: "Conviene dar menos pasos esta semana.",
            deadline: "2026-11-30",
        }],
    });
    assertEquals(validateOutcomesText(t, cands, "2026-11-02"), null);
});
