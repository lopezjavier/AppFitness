import { assertEquals } from "jsr:@std/assert@1.0.19";
import type { Candidate } from "./outcomes.ts";
import { buildOutcomes, formatEs } from "./outcomeTemplates.ts";

const TODAY = "2026-11-02";

const weight = (rateKgWeek: number, target: number): Candidate => ({
    id: "weight",
    kind: "weight",
    key: "weight",
    target_value: target,
    unit: "kg",
    default_deadline: "2026-11-30",
    basis: { weight_kg: 80, rate_kg_week: rateKgWeek, block_weeks: 4 },
});

const lift = (
    key: string,
    weeks: number,
    sessions: number | undefined,
    target = 100,
    slope = 1.25,
): Candidate => ({
    id: `e1rm:${key}`,
    kind: "e1rm",
    key,
    target_value: target,
    unit: "kg",
    default_deadline: "2026-11-30",
    basis: { weeks_with_data: weeks, last_e1rm: 95, slope_kg_week: slope },
    ...(sessions === undefined ? {} : { sessions }),
});

Deno.test("formatEs writes a decimal comma and drops trailing zeros", () => {
    assertEquals(formatEs(0.4), "0,4");
    assertEquals(formatEs(78), "78");
    assertEquals(formatEs(0.456), "0,46");
    assertEquals(formatEs(1.2), "1,2");
});

Deno.test("no candidates give no outcomes", () => {
    assertEquals(buildOutcomes([], TODAY), []);
});

Deno.test("a weight cut uses the cut template, the fixed figures and a deadline 28 days out", () => {
    const [o] = buildOutcomes([weight(-0.4, 78.4)], TODAY);
    assertEquals(o.kind, "weight");
    assertEquals(o.key, "weight");
    assertEquals(o.target_value, 78.4);
    assertEquals(o.unit, "kg");
    assertEquals(o.deadline, "2026-11-30");
    assertEquals(
        o.reason,
        "Perdiendo unos 0,4 kg por semana, en 4 semanas llegarías a 78,4 kg.",
    );
    assertEquals(o.basis, {
        weight_kg: 80,
        rate_kg_week: -0.4,
        block_weeks: 4,
    });
});

Deno.test("a weight gain uses the gain template with the rate unsigned", () => {
    const [o] = buildOutcomes([weight(0.28, 81.1)], TODAY);
    assertEquals(
        o.reason,
        "Ganando unos 0,28 kg por semana, en 4 semanas llegarías a 81,1 kg.",
    );
});

Deno.test("an e1RM outcome names the lift, the slope and the weeks of data", () => {
    const [o] = buildOutcomes([lift("back_squat", 5, 10, 100.5, 1.25)], TODAY);
    assertEquals(o.kind, "e1rm");
    assertEquals(o.key, "back_squat");
    assertEquals(
        o.reason,
        "Tu levantamiento sube unos 1,25 kg por semana en las últimas 5 semanas; si sigues así, 100,5 kg.",
    );
    assertEquals(o.deadline, "2026-11-30");
});

Deno.test("an e1RM reason never contains the catalogue key", () => {
    const [o] = buildOutcomes([lift("back_squat", 5, 10)], TODAY);
    assertEquals(/back|squat|_/.test(o.reason), false);
});

Deno.test("e1RM outcomes are capped at 3, ordered by weeks, then sessions, then key", () => {
    const out = buildOutcomes([
        lift("d_lift", 4, 5),
        lift("c_lift", 6, 2),
        lift("b_lift", 6, 9),
        lift("a_lift", 4, 5),
        lift("e_lift", 3, 50),
    ], TODAY);
    assertEquals(out.map((o) => o.key), ["b_lift", "c_lift", "a_lift"]);
});

Deno.test("the weight outcome comes first and does not count against the 3 e1RM", () => {
    const out = buildOutcomes([
        lift("a", 5, 1),
        lift("b", 5, 1),
        weight(-0.4, 78.4),
        lift("c", 5, 1),
        lift("d", 5, 1),
    ], TODAY);
    assertEquals(out.map((o) => o.key), ["weight", "a", "b", "c"]);
});

Deno.test("a missing session count sorts after a known one and ties fall to the key", () => {
    const out = buildOutcomes([
        lift("z", 4, undefined),
        lift("y", 4, 1),
        lift("x", 4, undefined),
    ], TODAY);
    assertEquals(out.map((o) => o.key), ["y", "x", "z"]);
});

Deno.test("the same candidates give the same outcomes twice", () => {
    const cands = [weight(-0.4, 78.4), lift("a", 5, 3), lift("b", 4, 3)];
    assertEquals(buildOutcomes(cands, TODAY), buildOutcomes(cands, TODAY));
});

Deno.test("every reason is at least 10 characters (the SQL check)", () => {
    for (
        const o of buildOutcomes([weight(-0.4, 78.4), lift("a", 3, 1)], TODAY)
    ) {
        assertEquals(o.reason.length >= 10, true);
    }
});
