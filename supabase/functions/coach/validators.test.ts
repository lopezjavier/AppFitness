import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import {
    computeNutrition,
    intakeRangeViolation,
    type NutritionInput,
} from "./nutritionRules.ts";
import { CARDIO_MIN_WEEK, computeActivity } from "./cardioRules.ts";
import type { Candidate } from "./outcomes.ts";
import type { Nutrition } from "./schemas.ts";
import {
    assertActivityInvariants,
    assertNutritionInvariants,
    NutritionInvariantError,
    unconfirmedKeys,
    validateOutcomesText,
} from "./validators.ts";

const START = "2026-11-02";
const cand = (id: string): Candidate => ({
    id,
    kind: id === "weight" ? "weight" : "e1rm",
    key: id,
    target_value: 78,
    unit: "kg",
    default_deadline: "2026-11-30",
    basis: {},
});
const CANDS = [cand("weight"), cand("e1rm:squat")];
const REASON = "Tu peso baja de forma constante.";
const item = (o: Record<string, unknown> = {}) => ({
    candidate_id: "weight",
    reason: REASON,
    deadline: "2026-11-30",
    ...o,
});
const text = (outcomes: unknown[], extra: Record<string, unknown> = {}) =>
    JSON.stringify({ outcomes, ...extra });
const check = (t: string) => validateOutcomesText(t, CANDS, START);

Deno.test("an empty outcome list is valid", () => {
    assertEquals(check(text([])), []);
});

Deno.test("a valid choice is returned with the reason trimmed", () => {
    const out = check(text([item({ reason: `  ${REASON} ` })]));
    assertEquals(out, [{
        candidate_id: "weight",
        reason: REASON,
        deadline: "2026-11-30",
    }]);
});

Deno.test("fenced JSON is accepted", () => {
    assertEquals(check("```json\n" + text([item()]) + "\n```")?.length, 1);
    assertEquals(check("```\n" + text([]) + "\n```"), []);
});

Deno.test("text that is not JSON, or not an object, is invalid", () => {
    assertEquals(check("hola"), null);
    assertEquals(check("[]"), null);
    assertEquals(check("null"), null);
    assertEquals(check(""), null);
});

Deno.test("an unknown or duplicate candidate id is invalid", () => {
    assertEquals(check(text([item({ candidate_id: "bench" })])), null);
    assertEquals(check(text([item(), item()])), null);
});

Deno.test("extra keys are rejected, whether a figure or a nutrition field", () => {
    assertEquals(check(text([item({ target_value: 70 })])), null);
    assertEquals(check(text([item()], { target_kcal: 1800 })), null);
    assertEquals(check(text([item()], { nutrition: {} })), null);
    assertEquals(check(JSON.stringify({})), null);
    assertEquals(
        check(text([{ candidate_id: "weight", reason: REASON }])),
        null,
    );
});

Deno.test("a reason outside 10..300 characters is invalid", () => {
    assertEquals(check(text([item({ reason: "corto" })])), null);
    assertEquals(check(text([item({ reason: "x".repeat(9) })])), null);
    assertEquals(check(text([item({ reason: "x".repeat(10) })]))?.length, 1);
    assertEquals(check(text([item({ reason: "x".repeat(300) })]))?.length, 1);
    assertEquals(check(text([item({ reason: "x".repeat(301) })])), null);
    assertEquals(check(text([item({ reason: 12 })])), null);
});

Deno.test("a deadline outside the window or not a real date is invalid", () => {
    assertEquals(check(text([item({ deadline: "2026-11-08" })])), null); // day 6
    assertEquals(check(text([item({ deadline: "2026-11-09" })]))?.length, 1); // day 7
    assertEquals(check(text([item({ deadline: "2026-11-30" })]))?.length, 1); // day 28
    assertEquals(check(text([item({ deadline: "2026-12-01" })])), null); // day 29
    assertEquals(check(text([item({ deadline: "2026-02-30" })])), null);
    assertEquals(check(text([item({ deadline: "30/11/2026" })])), null);
    assertEquals(check(text([item({ deadline: 20261130 })])), null);
});

Deno.test("more than 4 outcomes are invalid", () => {
    const many = Array.from({ length: 5 }, (_, i) => cand(`e1rm:l${i}`));
    const items = many.map((c) => item({ candidate_id: c.id }));
    assertEquals(validateOutcomesText(text(items), many, START), null);
    assertEquals(
        validateOutcomesText(text(items.slice(0, 4)), many, START)?.length,
        4,
    );
});

// --- nutrition invariants ---

const good = (o: Partial<NutritionInput> = {}): Nutrition =>
    computeNutrition({
        intention: "fat_loss",
        sex: "male",
        weightKg: 80,
        expenditureKcal: 2600,
        expenditureSource: "formula",
        experience: "intermediate",
        ...o,
    });
const broken = (rule: string, mutate: (n: Nutrition) => void, n = good()) => {
    mutate(n);
    const e = assertThrows(
        () => assertNutritionInvariants(n),
        NutritionInvariantError,
    );
    assertEquals(e.rule, rule);
};

Deno.test("a computed plan satisfies the invariants", () => {
    assertNutritionInvariants(good());
});

Deno.test("loss rate exactly 0.5 is accepted and 0.49 is rejected without a limiter", () => {
    const n = good();
    n.rate_pct_week = 0.5;
    assertNutritionInvariants(n);
    broken("loss_rate_min", (x) => (x.rate_pct_week = 0.49));
});

Deno.test("a slower rate is accepted only with the limiter flag", () => {
    const n = good({ sex: "female", weightKg: 50, expenditureKcal: 1400 });
    assertNutritionInvariants(n);
    broken("loss_rate_min", (x) => (x.limited_by = null), n);
});

Deno.test("a loss above 1 percent per week is rejected", () => {
    broken("loss_rate_max", (x) => (x.rate_pct_week = 1.2));
});

Deno.test("a loss needs a deficit and a gain needs a surplus", () => {
    broken("loss_needs_deficit", (x) => {
        x.target_kcal = 2700;
        x.calorie_adjustment = 100;
        x.strategy = "surplus";
    });
    broken("gain_needs_surplus", (x) => {
        x.target_kcal = 2400;
        x.calorie_adjustment = -200;
        x.strategy = "deficit";
    }, good({ intention: "muscle", experience: "beginner" }));
});

Deno.test("gain rate outside 0.25..0.5 is rejected", () => {
    const m = () => good({ intention: "muscle", experience: "advanced" });
    assertNutritionInvariants(m());
    broken("gain_rate_min", (x) => (x.rate_pct_week = 0.1), m());
    broken("gain_rate_max", (x) => (x.rate_pct_week = 0.8), m());
});

Deno.test("a target under the floor is rejected", () => {
    broken("target_below_floor", (x) => {
        x.target_kcal = 1400;
        x.calorie_adjustment = 1400 - x.expenditure_kcal;
    });
});

Deno.test("the floor flag needs the target at the floor", () => {
    broken("floor_flag_without_floor_target", (x) => (x.limited_by = "floor"));
});

Deno.test("protein under 1.6 g/kg or over 2.2 g/kg is rejected", () => {
    broken("protein_below_min", (x) => {
        x.protein_g = 100;
    });
    broken("protein_above_max", (x) => {
        x.protein_g = 200;
    });
    broken("protein_g_per_kg_range", (x) => (x.protein_g_per_kg = 1.2));
});

Deno.test("fat outside 20..35 percent and a broken percentage sum are rejected", () => {
    broken("pct_sum", (x) => (x.carbs_pct = x.carbs_pct + 1));
    broken("fat_range", (x) => {
        x.fat_pct = 40;
        x.carbs_pct = 100 - x.protein_pct - 40;
    });
    broken("pct_range", (x) => {
        x.carbs_pct = 3;
        x.fat_pct = 100 - x.protein_pct - 3;
    });
});

Deno.test("grams must match the percentages", () => {
    broken("grams_mismatch", (x) => (x.carbs_g = x.carbs_g + 20));
});

Deno.test("the protein distribution is 0.4 g/kg over at least 4 meals", () => {
    broken("distribution", (x) => (x.distribution.per_meal_g = 20));
    broken("distribution", (x) => (x.distribution.min_meals = 3));
});

Deno.test("the strategy and the adjustment must agree with the target", () => {
    broken("adjustment_mismatch", (x) => (x.calorie_adjustment = -100));
    broken("strategy_mismatch", (x) => (x.strategy = "surplus"));
});

Deno.test("unconfirmedKeys follows the accept_coach_plan rule", () => {
    const base = {
        activity_level: { provisional: false },
        total_expenditure: { value: 2500, provisional: false },
        experience: { provisional: false },
        weight_trend: { slope_kg_week: -0.4, provisional: false },
    };
    assertEquals(unconfirmedKeys(base), []);
    assertEquals(
        unconfirmedKeys({
            activity_level: { provisional: true },
            total_expenditure: { value: 2500, provisional: true },
            experience: { provisional: true },
            weight_trend: { slope_kg_week: -0.4, provisional: true },
        }),
        ["activity_level", "total_expenditure", "experience", "weight_trend"],
    );
    assertEquals(
        unconfirmedKeys({
            ...base,
            total_expenditure: { value: null, provisional: true },
            weight_trend: { slope_kg_week: null, provisional: true },
        }),
        [],
    );
    assertEquals(unconfirmedKeys(null), []);
});

// --- T-180: target against the intake anchor (04 section 8.7) ---

const anchoredPlan = (o: Partial<NutritionInput> = {}): Nutrition =>
    good({
        intention: "muscle",
        experience: "beginner",
        expenditureKcal: 2262,
        intakeAnchorKcal: 2262,
        ...o,
    });
/** Rewrites the target `delta` kcal over the anchor, keeping every other field consistent. */
const withDelta = (n: Nutrition, delta: number) => {
    n.target_kcal = (n.intake_anchor_kcal ?? 0) + delta;
    n.calorie_adjustment = n.target_kcal - n.expenditure_kcal;
    n.strategy = n.calorie_adjustment < 0
        ? "deficit"
        : n.calorie_adjustment > 0
        ? "surplus"
        : "maintenance";
};
// Only the intake rules matter here: fields after them are not reached because the rule fails first, or are accepted
// as is when the delta is inside the range (checked through the same intakeRange, 80 kg).
const intakeRuleOf = (n: Nutrition): string | null => {
    try {
        assertNutritionInvariants(n);
        return null;
    } catch (e) {
        return e instanceof NutritionInvariantError ? e.rule : null;
    }
};

Deno.test("a gain 1 kcal above the range edge is rejected, the edge itself passes the intake rule", () => {
    const edge = anchoredPlan();
    withDelta(edge, 450);
    assertEquals(intakeRuleOf(edge) === "target_above_intake_range", false);
    const over = anchoredPlan();
    withDelta(over, 451);
    assertEquals(intakeRuleOf(over), "target_above_intake_range");
});

Deno.test("a gain below the range edge is rejected unless a limiter is set", () => {
    const edge = anchoredPlan();
    withDelta(edge, 220);
    assertEquals(intakeRuleOf(edge) === "target_below_intake_range", false);
    const under = anchoredPlan();
    withDelta(under, 219);
    assertEquals(intakeRuleOf(under), "target_below_intake_range");
    const limited = anchoredPlan();
    withDelta(limited, 219);
    limited.limited_by = "goal_range";
    assertEquals(intakeRuleOf(limited) === "target_below_intake_range", false);
});

Deno.test("the unrealistic target of the T-180 report (+710 over the intake) is rejected", () => {
    const n = anchoredPlan();
    withDelta(n, 710);
    assertEquals(intakeRuleOf(n), "target_above_intake_range");
});

Deno.test("a loss deeper than the range is rejected and a floor-slowed one is allowed", () => {
    const deep = anchoredPlan({
        intention: "fat_loss",
        experience: "intermediate",
        expenditureKcal: 2600,
        intakeAnchorKcal: 2600,
    });
    withDelta(deep, -891);
    // The deficit cap (500) is reached first; the range edge itself is checked on its own below.
    assertEquals(intakeRuleOf(deep), "deficit_cap");
    assertEquals(
        intakeRangeViolation("loss", 80, 0, -891, null),
        "target_below_intake_range",
    );
    assertEquals(
        intakeRangeViolation("loss", 80, 0, -500, "deficit_cap"),
        null,
    );
    const slow = anchoredPlan({
        intention: "fat_loss",
        experience: "intermediate",
        expenditureKcal: 2600,
        intakeAnchorKcal: 2600,
    });
    withDelta(slow, -100);
    slow.limited_by = "floor";
    assertEquals(intakeRuleOf(slow) === "target_above_intake_range", false);
    const slowNoLimiter = anchoredPlan({
        intention: "fat_loss",
        experience: "intermediate",
        expenditureKcal: 2600,
        intakeAnchorKcal: 2600,
    });
    withDelta(slowNoLimiter, -100);
    assertEquals(intakeRuleOf(slowNoLimiter), "target_above_intake_range");
});

Deno.test("no-change intentions allow +/-220 kcal around the anchor at 80 kg and no more", () => {
    const ok = anchoredPlan({
        intention: "maintenance",
        expenditureKcal: 2262,
    });
    assertNutritionInvariants(ok);
    const high = anchoredPlan({
        intention: "maintenance",
        expenditureKcal: 2262,
    });
    withDelta(high, 221);
    assertEquals(intakeRuleOf(high), "target_above_intake_range");
    const low = anchoredPlan({
        intention: "maintenance",
        expenditureKcal: 2262,
    });
    withDelta(low, -221);
    assertEquals(intakeRuleOf(low), "target_below_intake_range");
});

Deno.test("a missing anchor skips the intake rules (formula path and plans stored before T-180)", () => {
    const n = good({ intention: "muscle", experience: "beginner" });
    assertEquals(n.intake_anchor_kcal, null);
    assertNutritionInvariants(n);
    const old = n as unknown as Record<string, unknown>;
    delete old.intake_anchor_kcal;
    delete old.slope_used;
    delete old.slope_term_kcal;
    assertNutritionInvariants(n);
});

Deno.test("a slope term beyond one top-of-range rate is rejected", () => {
    const n = anchoredPlan();
    n.slope_term_kcal = 442;
    assertEquals(intakeRuleOf(n), "slope_term_range");
});

// --- T-187: invariants by phase (mirror of coach_assert_plan) ---

Deno.test("a deficit of 500 is accepted and 501 is rejected as deficit_cap", () => {
    const ok = good({ weightKg: 120, expenditureKcal: 3000 });
    assertNutritionInvariants(ok);
    broken("deficit_cap", (x) => {
        x.target_kcal = 2499;
        x.calorie_adjustment = -501;
    }, good({ weightKg: 120, expenditureKcal: 3000 }));
});

Deno.test("the deficit_cap flag needs a cut and a deficit of exactly 500", () => {
    broken("deficit_cap_flag", (x) => (x.limited_by = "deficit_cap"));
    broken(
        "deficit_cap_flag",
        (x) => (x.limited_by = "deficit_cap"),
        good({ phase: "maintenance" }),
    );
});

Deno.test("maintenance and recomposition stay inside the no-change range", () => {
    for (const phase of ["maintenance", "recomposition"] as const) {
        assertNutritionInvariants(good({ phase }));
        broken("no_change_range", (x) => {
            x.target_kcal = 2900;
            x.calorie_adjustment = 300;
            x.strategy = "surplus";
        }, good({ phase }));
        broken("no_change_range", (x) => {
            x.target_kcal = 2300;
            x.calorie_adjustment = -300;
            x.strategy = "deficit";
        }, good({ phase }));
    }
});

Deno.test("a diet-break week is valid on a cut and rejected on any other phase", () => {
    assertNutritionInvariants(good({ dietBreak: true }));
    broken("diet_break_phase", (x) => {
        x.diet_break = good({ dietBreak: true }).diet_break;
    }, good({ phase: "maintenance" }));
    broken(
        "diet_break",
        (x) => (x.diet_break = null),
        good({ dietBreak: true }),
    );
    broken(
        "diet_break",
        (x) => (x.diet_break!.target_kcal = 2000),
        good({ dietBreak: true }),
    );
});

Deno.test("phase options outside 0.5-1.0 are rejected", () => {
    broken("phase_options", (x) => (x.phase_options.cut_rate_pct = 1.2));
    broken("phase_options", (x) => (x.phase_options.cut_rate_pct = 0.4));
});

Deno.test("a plan stored before T-187 (no phase keys) is judged by its intention", () => {
    const n = good() as unknown as Record<string, unknown>;
    delete n.phase;
    delete n.phase_reason;
    delete n.phase_options;
    delete n.diet_break;
    assertNutritionInvariants(n as unknown as Nutrition);
});

Deno.test("cardio: 150 minutes in every phase, anything else is rejected", () => {
    const a150 = computeActivity({
        intention: "maintenance",
        stepsAvg28: null,
    });
    assertEquals(a150.cardio_min_week, CARDIO_MIN_WEEK);
    for (
        const phase of ["cut", "gain", "maintenance", "recomposition"] as const
    ) {
        assertActivityInvariants(a150, null, phase);
    }
    assertThrows(
        () => assertActivityInvariants({ ...a150, cardio_min_week: 100 }, null),
        NutritionInvariantError,
    );
});
