// The list of data a plan or block reads (`coach_plan_inputs`, planning/04 section 7.9). The same RPC feeds the
// "Antes de empezar" screen, so the function and the screen never disagree about what is missing.
import { CoachInputError } from "./context.ts";

/**
 * Keys of the entries that are `missing` and `blocking`, in list order. Anything that does not look like an entry
 * (unknown shape, a key the client does not know yet) is ignored; only the RPC decides what blocks.
 */
export function missingFromInputs(inputs: unknown): string[] {
    const list = typeof inputs === "object" && inputs !== null
        ? (inputs as { inputs?: unknown }).inputs
        : null;
    if (!Array.isArray(list)) return [];
    const keys: string[] = [];
    for (const e of list) {
        if (
            typeof e === "object" && e !== null &&
            (e as { status?: unknown }).status === "missing" &&
            (e as { blocking?: unknown }).blocking === true &&
            typeof (e as { key?: unknown }).key === "string"
        ) keys.push((e as { key: string }).key);
    }
    return keys;
}

/** Refuses with `coach_missing_data` (names only) when the list has a blocking missing entry. */
export function assertInputsComplete(inputs: unknown): void {
    const missing = missingFromInputs(inputs);
    if (missing.length > 0) {
        throw new CoachInputError(
            "coach_missing_data",
            "Required data is missing",
            missing,
        );
    }
}
