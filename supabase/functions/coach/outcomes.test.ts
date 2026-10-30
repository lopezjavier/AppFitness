import { assertEquals } from "jsr:@std/assert@1.0.19";
import { computeNutrition, type NutritionInput } from "./nutritionRules.ts";
import { addDays, buildCandidates, type SummaryLift } from "./outcomes.ts";

const input = (o: Partial<NutritionInput> = {}): NutritionInput => ({
    intention: "fat_loss",
    sex: "male",
    weightKg: 80,
    expenditureKcal: 2600,
    expenditureSource: "formula",
    experience: "intermediate",
    ...o,
});
const lift = (exercise: string, weekly: Array<number | null>): SummaryLift => ({
    exercise,
    weekly_e1rm: weekly,
});

Deno.test("fat loss gets a weight candidate below the current weight", () => {
    const n = computeNutrition(input());
    const [c] = buildCandidates({}, n, "2026-11-02");
    assertEquals(c.id, "weight");
    assertEquals(c.target_value, 78.4); // 80 - 0.4 kg/week x 4
    assertEquals(c.unit, "kg");
    assertEquals(c.default_deadline, "2026-11-30");
});

Deno.test("muscle gets a weight candidate above the current weight", () => {
    const n = computeNutrition(
        input({
            intention: "muscle",
            experience: "beginner",
            weightKg: 70,
            expenditureKcal: 2500,
        }),
    );
    const [c] = buildCandidates({}, n, "2026-11-02");
    assertEquals(c.target_value, 71.4);
});

Deno.test("strength, recomposition and maintenance get no weight candidate", () => {
    for (
        const intention of ["strength", "recomposition", "maintenance"] as const
    ) {
        const n = computeNutrition(input({ intention }));
        assertEquals(buildCandidates({}, n, "2026-11-02"), []);
    }
});

Deno.test("an e1RM candidate needs 3 weeks of data and a positive slope", () => {
    const n = computeNutrition(input({ intention: "strength" }));
    const summary = {
        main_lifts: [
            lift("squat", [100, null, 102, null, 104, null, null, 106]),
            lift("bench", [60, null, null, null, null, null, null, 62]),
            lift("row", [80, 78, 76, null, null, null, null, null]),
            lift("press", [40, 40, 40, 40, null, null, null, null]),
        ],
    };
    const out = buildCandidates(summary, n, "2026-11-02");
    assertEquals(out.map((c) => c.id), ["e1rm:squat"]);
    // least-squares slope 23 / 26.75 = 0.86 kg/week over weeks 0,2,4,7: 106 + 0.86 x 4
    const squat = out[0];
    assertEquals(squat.kind, "e1rm");
    assertEquals(squat.basis.weeks_with_data, 4);
    assertEquals(squat.basis.last_e1rm, 106);
    assertEquals(squat.target_value, 109.4);
});

Deno.test("a beginner with no history gets no e1RM candidate", () => {
    const n = computeNutrition(
        input({ intention: "strength", experience: "beginner" }),
    );
    assertEquals(buildCandidates({ main_lifts: [] }, n, "2026-11-02"), []);
    assertEquals(buildCandidates({}, n, "2026-11-02"), []);
});

Deno.test("addDays crosses month and year boundaries", () => {
    assertEquals(addDays("2026-12-20", 28), "2027-01-17");
    assertEquals(addDays("2026-02-01", 28), "2026-03-01");
});
