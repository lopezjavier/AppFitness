import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    CoachPlanError,
    computeNutrition,
    DEFICIT_CAP,
    type NutritionInput,
    SURPLUS_CAP,
} from "./nutritionRules.ts";
import { assertNutritionInvariants } from "./validators.ts";

const base: NutritionInput = {
    intention: "fat_loss",
    sex: "male",
    weightKg: 80,
    expenditureKcal: 2600,
    expenditureSource: "formula",
    experience: "intermediate",
};
const plan = (o: Partial<NutritionInput> = {}) =>
    computeNutrition({ ...base, ...o });

Deno.test("(a) fat loss, male 80 kg, 2600 kcal: 440 deficit, 33/42/25, 32 g per meal", () => {
    const n = plan();
    assertEquals(n.target_kcal, 2160);
    assertEquals(n.calorie_adjustment, -440);
    assertEquals(n.strategy, "deficit");
    assertEquals(n.rate_pct_week, 0.5);
    assertEquals(n.rate_kg_week, -0.4);
    assertEquals(n.limited_by, null);
    assertEquals(n.protein_g_per_kg, 2.2);
    assertEquals([n.protein_pct, n.carbs_pct, n.fat_pct], [33, 42, 25]);
    assertEquals([n.protein_g, n.carbs_g, n.fat_g], [178, 227, 60]);
    assertEquals(n.distribution, {
        per_meal_g: 32,
        g_per_kg: 0.4,
        min_meals: 4,
    });
    assertEquals(n.edited_by_user, false);
    assertEquals(n.calorie_changes, 0);
});

Deno.test("(b) female 50 kg, 1500 kcal, loss: delta 280, target 1220, floor not hit", () => {
    const n = plan({ sex: "female", weightKg: 50, expenditureKcal: 1500 });
    assertEquals(n.target_kcal, 1220);
    assertEquals(n.limited_by, null);
    assertEquals(n.floor_kcal, 1200);
});

Deno.test("(c) female 50 kg, 1400 kcal: the floor wins and the effective rate is slower", () => {
    const n = plan({ sex: "female", weightKg: 50, expenditureKcal: 1400 });
    assertEquals(n.target_kcal, 1200);
    assertEquals(n.limited_by, "floor");
    assertEquals(n.calorie_adjustment, -200);
    assertEquals(n.rate_pct_week, 0.364);
    assert(n.rate_pct_week < 0.5);
    assertNutritionInvariants(n);
});

Deno.test("the male floor is 1500 and a loss without any deficit left is infeasible", () => {
    const n = plan({ weightKg: 70, expenditureKcal: 1600 });
    assertEquals(n.target_kcal, 1500);
    assertEquals(n.limited_by, "floor");
    const e = assertThrows(
        () => plan({ weightKg: 70, expenditureKcal: 1450 }),
        CoachPlanError,
    );
    assertEquals(e.code, "coach_plan_infeasible");
});

Deno.test("(d) muscle, male beginner 70 kg, 2500 kcal: +390 at 0.5 percent", () => {
    const n = plan({
        intention: "muscle",
        experience: "beginner",
        weightKg: 70,
        expenditureKcal: 2500,
    });
    assertEquals(n.target_kcal, 2890);
    assertEquals(n.strategy, "surplus");
    // 390 kcal/day is the 0.5 percent rate rounded up to 10 kcal: 0.506 percent, 0.355 kg per week.
    assertEquals(n.target_rate_pct, 0.5);
    assertEquals(n.rate_pct_week, 0.506);
    assertEquals(n.rate_kg_week, 0.355);
    assertEquals(n.protein_g_per_kg, 1.8);
});

Deno.test("gain rate by experience is 0.5, 0.35 and 0.25 percent", () => {
    const rate = (experience: NutritionInput["experience"]) =>
        plan({ intention: "muscle", experience, weightKg: 80 }).target_rate_pct;
    assertEquals(rate("beginner"), 0.5);
    assertEquals(rate("intermediate"), 0.35);
    assertEquals(rate("advanced"), 0.25);
});

Deno.test("(e) 75 kg gives 30 g of protein per meal over at least 4 meals", () => {
    const n = plan({ weightKg: 75 });
    assertEquals(n.distribution.per_meal_g, 30);
    assertEquals(n.distribution.min_meals, 4);
});

Deno.test("(f) 120 kg aiming 0.5 percent is a 660 deficit; an aggressive rate hits the 1000 cap", () => {
    const normal = plan({ weightKg: 120, expenditureKcal: 3000 });
    assertEquals(normal.calorie_adjustment, -660);
    assertEquals(normal.limited_by, null);
    const aggressive = plan({
        weightKg: 120,
        expenditureKcal: 3000,
        targetRatePct: 1.0,
    });
    assertEquals(aggressive.calorie_adjustment, -DEFICIT_CAP);
    assertEquals(aggressive.target_kcal, 2000);
    assertEquals(aggressive.limited_by, "goal_range");
    assertNutritionInvariants(aggressive);
});

Deno.test("the surplus is capped at 750", () => {
    const n = plan({
        intention: "muscle",
        experience: "beginner",
        weightKg: 200,
        expenditureKcal: 3000,
    });
    assertEquals(n.calorie_adjustment, SURPLUS_CAP);
    assertEquals(n.limited_by, "goal_range");
});

Deno.test("strength, recomposition and maintenance keep the expenditure", () => {
    for (
        const intention of ["strength", "recomposition", "maintenance"] as const
    ) {
        const n = plan({ intention });
        assertEquals(n.calorie_adjustment, 0);
        assertEquals(n.target_kcal, 2600);
        assertEquals(n.strategy, "maintenance");
        assertEquals(n.rate_kg_week, 0);
        assertNutritionInvariants(n);
    }
});

Deno.test("protein per kg by case stays within 1.6 and 2.2", () => {
    const g = (o: Partial<NutritionInput>) => plan(o).protein_g_per_kg;
    assertEquals(g({ experience: "advanced" }), 2.2);
    assertEquals(g({ experience: "intermediate" }), 2.2);
    assertEquals(g({ experience: "beginner" }), 2.0);
    assertEquals(g({ intention: "recomposition" }), 2.0);
    assertEquals(g({ intention: "maintenance" }), 1.8);
    assertEquals(g({ intention: "strength" }), 1.8);
});

Deno.test("percentages sum to 100, stay in 5..90 and keep protein at least 1.6 g/kg", () => {
    for (const weightKg of [45, 58.3, 71.3, 80, 95.5, 120]) {
        for (
            const intention of ["fat_loss", "muscle", "maintenance"] as const
        ) {
            const n = plan({
                intention,
                weightKg,
                expenditureKcal: Math.round(weightKg * 33) + 800,
            });
            assertEquals(n.protein_pct + n.carbs_pct + n.fat_pct, 100);
            for (const p of [n.protein_pct, n.carbs_pct, n.fat_pct]) {
                assert(p >= 5 && p <= 90);
            }
            assert(n.protein_g >= 1.6 * weightKg - 0.5);
            assertNutritionInvariants(n);
        }
    }
});

Deno.test("when carbs would fall under 5 percent, fat drops to 20 then protein to 1.6 g/kg", () => {
    const at1500 = (weightKg: number) =>
        plan({ weightKg, expenditureKcal: 1500, intention: "maintenance" });
    // 1.8 g/kg on 1500 kcal: 100 kg needs 48 percent, 130 kg needs 63 percent; fat keeps 25.
    assertEquals(at1500(100).fat_pct, 25);
    assertEquals(at1500(130).protein_pct, 63);
    assertEquals(at1500(130).carbs_pct, 12);
    // 150 kg: 72 percent protein leaves 3 percent carbs with fat 25, so fat drops to 20 (carbs 8).
    const lowFat = at1500(150);
    assertEquals([lowFat.protein_pct, lowFat.carbs_pct, lowFat.fat_pct], [
        72,
        8,
        20,
    ]);
    assertEquals(lowFat.protein_g_per_kg, 1.8);
    // 160 kg: still 3 percent carbs at 1.8 g/kg, so protein falls to 1.6 g/kg (69 percent).
    const lowProtein = at1500(160);
    assertEquals([
        lowProtein.protein_pct,
        lowProtein.carbs_pct,
        lowProtein.fat_pct,
    ], [69, 11, 20]);
    assertEquals(lowProtein.protein_g_per_kg, 1.6);
    assertNutritionInvariants(lowProtein);
});

Deno.test("an impossible split is a clean coach_plan_infeasible", () => {
    const e = assertThrows(
        () =>
            plan({
                weightKg: 250,
                expenditureKcal: 1500,
                intention: "maintenance",
            }),
        CoachPlanError,
    );
    assertEquals(e.code, "coach_plan_infeasible");
});

Deno.test("missing sex, weight or expenditure is coach_missing_data", () => {
    for (
        const o of [{ sex: null }, { weightKg: null }, {
            expenditureKcal: null,
        }]
    ) {
        const e = assertThrows(() => plan(o), CoachPlanError);
        assertEquals(e.code, "coach_missing_data");
    }
});

Deno.test("the unconfirmed keys are carried into the plan", () => {
    const n = plan({ requiresConfirmation: true, unconfirmed: ["experience"] });
    assertEquals(n.requires_confirmation, true);
    assertEquals(n.unconfirmed, ["experience"]);
});
