import { assert, assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    CoachPlanError,
    computeNutrition,
    DEFICIT_CAP,
    firstPhase,
    GAIN_RATE_RANGE,
    intakeRange,
    type NutritionInput,
    rangeClass,
    rateKcal,
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

Deno.test("(f) 120 kg aiming 0.5 percent asks for 660 and is capped at 500; aiming 1.0 is capped at 500 too", () => {
    assertEquals(DEFICIT_CAP, 500);
    const normal = plan({ weightKg: 120, expenditureKcal: 3000 });
    assertEquals(normal.calorie_adjustment, -500);
    assertEquals(normal.target_kcal, 2500);
    assertEquals(normal.limited_by, "deficit_cap");
    assertEquals(normal.rate_pct_week, 0.379);
    assertNutritionInvariants(normal);
    const aggressive = plan({
        weightKg: 120,
        expenditureKcal: 3000,
        targetRatePct: 1.0,
    });
    assertEquals(aggressive.calorie_adjustment, -DEFICIT_CAP);
    assertEquals(aggressive.target_kcal, 2500);
    assertEquals(aggressive.limited_by, "deficit_cap");
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

// --- T-180: expenditure anchored on the intake, target checked against the rate (04 sections 7.2, 8.7) ---

const anchored = (o: Partial<NutritionInput> = {}) =>
    plan({
        intention: "muscle",
        experience: "beginner",
        expenditureKcal: 2262,
        expenditureSource: "adaptive",
        intakeAnchorKcal: 2262,
        slopeUsed: false,
        ...o,
    });

Deno.test("T-180 regression: intake 2262 without a usable slope gives a muscle target near 2700, not 2972", () => {
    const n = anchored();
    assertEquals(n.target_kcal, 2702);
    assertEquals(n.intake_anchor_kcal, 2262);
    assertEquals(n.slope_used, false);
    assertEquals(n.slope_term_kcal, 0);
    assert(n.target_kcal >= 2700 && n.target_kcal <= 2740);
    assertNutritionInvariants(n);
});

Deno.test("a slope term inside the clamp moves the expenditure and the target with it", () => {
    const n = anchored({ expenditureKcal: 2400, slopeUsed: true });
    assertEquals(n.slope_term_kcal, 138);
    assertEquals(n.expenditure_kcal, 2400);
    assertEquals(n.target_kcal, 2840);
    assertNutritionInvariants(n);
});

Deno.test("a slope term beyond one top-of-range rate (440 kcal at 80 kg) is clamped", () => {
    const up = anchored({ expenditureKcal: 3000, slopeUsed: true });
    assertEquals(up.slope_term_kcal, 440);
    assertEquals(up.expenditure_kcal, 2702);
    const down = anchored({ expenditureKcal: 1500, slopeUsed: true });
    assertEquals(down.slope_term_kcal, -440);
    assertEquals(down.expenditure_kcal, 1822);
    assertNutritionInvariants(up);
    assertNutritionInvariants(down);
});

Deno.test("with an anchor and no slope the expenditure is the anchor", () => {
    const n = anchored({ expenditureKcal: 2500, slopeUsed: false });
    assertEquals(n.expenditure_kcal, 2262);
});

Deno.test("without an anchor (formula) nothing is checked or clamped", () => {
    const n = plan({ intakeAnchorKcal: null, slopeUsed: true });
    assertEquals(n.intake_anchor_kcal, null);
    assertEquals(n.slope_used, false);
    assertEquals(n.slope_term_kcal, 0);
    assertEquals(n.expenditure_kcal, 2600);
});

Deno.test("every intention lands inside its intake range", () => {
    for (
        const [intention, experience, rate] of [
            ["muscle", "beginner", undefined],
            ["muscle", "intermediate", undefined],
            ["muscle", "advanced", undefined],
            ["fat_loss", "intermediate", 0.5],
            ["fat_loss", "intermediate", 1.0],
            ["strength", "intermediate", undefined],
            ["recomposition", "intermediate", undefined],
            ["maintenance", "intermediate", undefined],
        ] as const
    ) {
        const n = anchored({
            intention,
            experience,
            targetRatePct: rate,
            expenditureKcal: 2700,
            intakeAnchorKcal: 2700,
        });
        const phase = firstPhase(intention).phase;
        const { min, max } = intakeRange(rangeClass(phase), 80, 0);
        const delta = n.target_kcal - 2700;
        assert(
            delta >= min && delta <= max,
            `${intention} ${experience} ${rate}: ${delta}`,
        );
        assertNutritionInvariants(n);
    }
});

Deno.test("intakeRange: gain, loss and no-change ranges at 80 kg", () => {
    assertEquals(rateKcal(0.5, 80), 440);
    assertEquals(intakeRange("gain", 80, 0), { min: 220, max: 450 });
    assertEquals(intakeRange("loss", 80, 0), { min: -890, max: -440 });
    assertEquals(intakeRange("nochange", 80, 100), { min: -120, max: 320 });
    assertEquals(GAIN_RATE_RANGE[0], 0.25);
});

Deno.test("a loss with the floor is slower than the range and still allowed", () => {
    const n = plan({
        intakeAnchorKcal: 1600,
        expenditureKcal: 1600,
        slopeUsed: false,
    });
    assertEquals(n.limited_by, "floor");
    assertEquals(n.target_kcal, 1500);
});

// --- T-187: phases, the deficit cap, the diet break (04 sections 8.2, 8.9; 07 sections 4, 12.3) ---

Deno.test("first phase by intention: five rows", () => {
    const rows = [
        ["fat_loss", "cut"],
        ["muscle", "gain"],
        ["recomposition", "recomposition"],
        ["maintenance", "maintenance"],
        ["strength", "maintenance"],
    ] as const;
    for (const [intention, phase] of rows) {
        assertEquals(firstPhase(intention), {
            phase,
            reason: "intention",
        });
    }
});

Deno.test("the deficit cap: a 650 kcal deficit is cut to 500 with deficit_cap", () => {
    // 1.0 percent at 65 kg asks for ceil(715 / 10) * 10 = 720 > 650; the cap holds it at 500.
    const n = plan({ weightKg: 65, expenditureKcal: 2800, targetRatePct: 1.0 });
    assertEquals(n.calorie_adjustment, -500);
    assertEquals(n.limited_by, "deficit_cap");
    assertEquals(n.phase_options, { cut_rate_pct: 1.0, diet_break: false });
    assertNutritionInvariants(n);
});

Deno.test("80 kg at 1.0 percent asks for 880 and is capped at 500", () => {
    const n = plan({ targetRatePct: 1.0 });
    assertEquals(n.calorie_adjustment, -500);
    assertEquals(n.target_kcal, 2100);
    assertEquals(n.limited_by, "deficit_cap");
    assertEquals(n.rate_pct_week, 0.568);
});

Deno.test("the floor wins over the deficit cap", () => {
    // 120 kg, expenditure 1900: the cap would give 1400, below the male floor.
    const n = plan({ weightKg: 120, expenditureKcal: 1900 });
    assertEquals(n.target_kcal, 1500);
    assertEquals(n.limited_by, "floor");
    assertNutritionInvariants(n);
});

Deno.test("a deficit under the cap carries no limiter", () => {
    const n = plan();
    assertEquals(n.calorie_adjustment, -440);
    assertEquals(n.limited_by, null);
});

Deno.test("the cut rate 1.0 and 0.5 are accepted; 1.2 and 0.4 are refused defensively", () => {
    assertEquals(plan({ targetRatePct: 1.0 }).target_rate_pct, 1.0);
    assertEquals(plan({ targetRatePct: 0.5 }).target_rate_pct, 0.5);
    for (const bad of [1.2, 0.4]) {
        const e = assertThrows(
            () => plan({ targetRatePct: bad }),
            CoachPlanError,
        );
        assertEquals(e.code, "coach_plan_infeasible");
    }
});

Deno.test("maintenance, recomposition and minimal use rate 0", () => {
    for (const phase of ["maintenance", "recomposition", "minimal"] as const) {
        const n = plan({ phase });
        assertEquals(n.calorie_adjustment, 0);
        assertEquals(n.target_rate_pct, 0);
        assertEquals(n.rate_pct_week, 0);
        assertEquals(n.limited_by, null);
        assertNutritionInvariants(n);
    }
});

Deno.test("the gain phase keeps the rate by experience", () => {
    const n = plan({
        intention: "muscle",
        phase: "gain",
        experience: "advanced",
    });
    assertEquals(n.target_rate_pct, 0.25);
});

Deno.test("the diet break stores the maintenance week at rate 0 with its macros", () => {
    const n = plan({ expenditureKcal: 2600, dietBreak: true });
    assertEquals(n.calorie_adjustment, -440);
    assertEquals(n.phase_options.diet_break, true);
    const b = n.diet_break!;
    assertEquals(b.target_kcal, 2600);
    assertEquals(b.calorie_adjustment, 0);
    assertEquals(b.strategy, "maintenance");
    assertEquals(b.week, 4);
    assertEquals(b.protein_pct + b.carbs_pct + b.fat_pct, 100);
    assertNutritionInvariants(n);
});

Deno.test("the weeks 1-3 target is the cut figure and week 4 the expenditure (2600)", () => {
    const n = plan({ expenditureKcal: 2600, weightKg: 80, dietBreak: true });
    assertEquals([n.target_kcal, n.diet_break!.target_kcal], [2160, 2600]);
});

Deno.test("without the diet break option the plan carries no maintenance week; other phases never do", () => {
    assertEquals(plan().diet_break, null);
    assertEquals(
        plan({ dietBreak: true, phase: "maintenance" }).diet_break,
        null,
    );
    assertEquals(
        plan({ dietBreak: true, phase: "recomposition" }).diet_break,
        null,
    );
});

Deno.test("intake range by phase class: a maintenance plan of a fat-loss user uses the no-change range", () => {
    // Fat-loss intention in maintenance: target = anchor, never the loss range (-890..-440).
    const f = plan({
        phase: "maintenance",
        expenditureKcal: 2700,
        intakeAnchorKcal: 2700,
        slopeUsed: false,
    });
    assertEquals(f.target_kcal, 2700);
    assertNutritionInvariants(f);
});

Deno.test("intake range by phase class: a maintenance user does not get the gain range", () => {
    assertEquals(rangeClass("maintenance"), "nochange");
    assertEquals(rangeClass("recomposition"), "nochange");
    assertEquals(rangeClass("minimal"), "nochange");
    assertEquals(rangeClass("cut"), "loss");
    assertEquals(rangeClass("gain"), "gain");
    const n = plan({
        intention: "muscle",
        phase: "maintenance",
        intakeAnchorKcal: 2600,
        slopeUsed: false,
    });
    assertEquals(n.calorie_adjustment, 0);
    assertNutritionInvariants(n);
});
