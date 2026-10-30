// Monthly AI budget (planning/07 section 11). The app mirrors USER_LIMIT_EUR in CoachBudget.USER_MONTHLY_LIMIT_EUR.
export const USER_LIMIT_EUR = 2;
export const GLOBAL_LIMIT_EUR = 5;

export type BudgetVerdict = { ok: true } | {
    ok: false;
    scope: "user" | "global";
};

/**
 * Refuses when the money already spent this month reached the limit (a call cannot be priced in advance, so
 * one call may overshoot by its own cost). The user limit is checked first.
 */
export function checkBudget(
    userSpent: number,
    globalSpent: number,
): BudgetVerdict {
    if (userSpent >= USER_LIMIT_EUR) return { ok: false, scope: "user" };
    if (globalSpent >= GLOBAL_LIMIT_EUR) return { ok: false, scope: "global" };
    return { ok: true };
}

/** First day of the UTC month of `now`, as `YYYY-MM-DD` (the `coach_usage.month` key). */
export function monthKey(now: Date): string {
    return `${now.toISOString().slice(0, 7)}-01`;
}
