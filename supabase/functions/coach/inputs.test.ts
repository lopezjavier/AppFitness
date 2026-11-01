import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { CoachInputError } from "./context.ts";
import { assertInputsComplete, missingFromInputs } from "./inputs.ts";
import { blockInputs, MISSING, planInputs } from "./inputsFixtures.ts";

Deno.test("nothing blocking: no missing keys", () => {
    assertEquals(missingFromInputs(planInputs()), []);
    assertEquals(missingFromInputs(blockInputs()), []);
});

Deno.test("blocking missing entries are returned in list order", () => {
    const inputs = planInputs({
        intention: MISSING,
        sex: MISSING,
        weight: MISSING,
    });
    assertEquals(missingFromInputs(inputs), ["intention", "sex", "weight"]);
});

Deno.test("a missing place blocks a block", () => {
    assertEquals(missingFromInputs(blockInputs({ place: MISSING })), ["place"]);
});

Deno.test("stale, provisional and unset_optional entries never block", () => {
    const inputs = planInputs({
        weight: { status: "stale", blocking: false },
        activity_level: { status: "provisional" },
    });
    assertEquals(missingFromInputs(inputs), []);
    assertEquals(
        missingFromInputs(blockInputs({
            equipment: { status: "unset_optional" },
            days_per_week: { status: "unset_optional" },
        })),
        [],
    );
});

Deno.test("a missing entry that does not block does not refuse", () => {
    const inputs = planInputs({
        total_expenditure: { status: "missing", blocking: false },
    });
    assertEquals(missingFromInputs(inputs), []);
});

Deno.test("an unknown blocking key is passed through, an unknown shape is ignored", () => {
    const inputs = planInputs({}, [{
        key: "future_thing",
        status: "missing",
        blocking: true,
    }]);
    assertEquals(missingFromInputs(inputs), ["future_thing"]);
    for (const bad of [null, undefined, "x", 3, {}, { inputs: "x" }, []]) {
        assertEquals(missingFromInputs(bad), []);
    }
    assertEquals(
        missingFromInputs({ inputs: [null, 1, "a", { status: "missing" }] }),
        [],
    );
});

Deno.test("assertInputsComplete throws coach_missing_data with the names only", () => {
    const e = assertThrows(
        () => assertInputsComplete(planInputs({ sex: MISSING })),
        CoachInputError,
    );
    assertEquals(e.code, "coach_missing_data");
    assertEquals(e.missing, ["sex"]);
    assertInputsComplete(planInputs());
});
