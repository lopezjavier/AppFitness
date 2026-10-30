import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { computeNutrition, type NutritionInput } from "./nutritionRules.ts";
import type { Candidate } from "./outcomes.ts";
import type { Nutrition } from "./schemas.ts";
import {
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
