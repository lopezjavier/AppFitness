// Outcomes of a nutrition plan without a model (ADR-98, planning/07 section 4): the candidates are chosen by rule and
// the reasons come from Spanish templates. Pure: no I/O.
import { addDays, BLOCK_WEEKS, type Candidate } from "./outcomes.ts";
import type { Outcome } from "./schemas.ts";

export const MAX_E1RM_OUTCOMES = 3;
const DEADLINE_DAYS = BLOCK_WEEKS * 7;

/** A number with a decimal comma and at most two decimals, no trailing zeros ("0,4", "78,4", "1,25"). */
export function formatEs(x: number): string {
    return String(Math.round((x + Number.EPSILON) * 100) / 100 + 0).replace(
        ".",
        ",",
    );
}

const num = (v: unknown): number => typeof v === "number" ? v : 0;

// The e1RM reason does not name the exercise: the server has no Spanish catalogue names (they live in the client bundle).
function reasonOf(c: Candidate): string {
    const target = formatEs(c.target_value);
    if (c.kind === "weight") {
        const rate = num(c.basis.rate_kg_week);
        const verb = rate < 0 ? "Perdiendo" : "Ganando";
        return `${verb} unos ${
            formatEs(Math.abs(rate))
        } kg por semana, en ${BLOCK_WEEKS} semanas llegarías a ${target} kg.`;
    }
    return `Tu levantamiento sube unos ${
        formatEs(num(c.basis.slope_kg_week))
    } kg por semana en las últimas ${
        num(c.basis.weeks_with_data)
    } semanas; si sigues así, ${target} kg.`;
}

/**
 * Weight first, then at most 3 e1RM candidates ordered by weeks with data (desc), sessions (desc, unknown last) and
 * the exercise key (asc). The deadline is the block end, `today` + 28 days.
 */
export function buildOutcomes(
    candidates: Candidate[],
    today: string,
): Outcome[] {
    const deadline = addDays(today, DEADLINE_DAYS);
    const weight = candidates.filter((c) => c.kind === "weight").slice(0, 1);
    const lifts = candidates.filter((c) => c.kind === "e1rm").sort((a, b) =>
        num(b.basis.weeks_with_data) - num(a.basis.weeks_with_data) ||
        (b.sessions ?? -1) - (a.sessions ?? -1) ||
        (a.key < b.key ? -1 : a.key > b.key ? 1 : 0)
    ).slice(0, MAX_E1RM_OUTCOMES);
    return [...weight, ...lifts].map((c) => ({
        kind: c.kind,
        key: c.key,
        target_value: c.target_value,
        unit: c.unit,
        reason: reasonOf(c),
        deadline,
        basis: c.basis,
    }));
}
