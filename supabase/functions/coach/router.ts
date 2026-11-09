// Kinds of coach calls (planning/07 section 11). Every kind is deterministic: no model call (ADR-103).
// Any other kind (including the retired `ping`, `report` and `chat`) answers INVALID_INPUT "Unknown kind".
import type { PrepareContext, Prepared } from "./context.ts";
import { prepare as prepareDietBreak } from "./planDietBreak.ts";
import { prepare as prepareGoalSwitch } from "./planGoalSwitch.ts";
import { prepare as prepareNutrition } from "./planNutrition.ts";
import { prepare as preparePhaseChange } from "./planPhaseChange.ts";
import { prepare as prepareRecompute } from "./planRecompute.ts";

export type KindSpec = {
    /** Validates the `input`, reads the context it needs and returns the `finalize` step that stores and answers. */
    prepare: (input: unknown, ctx: PrepareContext) => Promise<Prepared>;
};

export const KINDS: Record<string, KindSpec> = {
    plan_nutrition: { prepare: prepareNutrition },
    plan_recompute: { prepare: prepareRecompute },
    plan_diet_break: { prepare: prepareDietBreak },
    plan_phase_change: { prepare: preparePhaseChange },
    plan_goal_switch: { prepare: prepareGoalSwitch },
};
