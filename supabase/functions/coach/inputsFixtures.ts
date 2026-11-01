// RPC-shaped fixtures of `coach_plan_inputs`, shared by the coach tests.
export type InputEntry = {
    key: string;
    value?: unknown;
    source?: string;
    status: string;
    blocking: boolean;
    fix_target?: string | null;
    date?: string;
};

const ok = (key: string, source: string): InputEntry => ({
    key,
    value: "x",
    source,
    status: "ok",
    blocking: false,
    fix_target: null,
});

const PLAN_KEYS: [string, string][] = [
    ["intention", "my_training"],
    ["sex", "profile"],
    ["age", "profile"],
    ["height", "profile"],
    ["weight", "weigh_in"],
    ["activity_level", "coach_estimate"],
    ["total_expenditure", "coach_estimate"],
    ["weight_trend", "coach_estimate"],
    ["diary", "coach_estimate"],
];
const BLOCK_KEYS: [string, string][] = [
    ["intention", "my_training"],
    ["place", "my_training"],
    ["equipment", "my_training"],
    ["cardio", "my_training"],
    ["days_per_week", "my_training"],
    ["session_minutes", "my_training"],
    ["experience", "coach_estimate"],
];

const build = (
    kind: string,
    keys: [string, string][],
    over: Record<string, Partial<InputEntry>>,
    extra: InputEntry[],
) => ({
    kind,
    as_of: "2026-10-30",
    inputs: [
        ...keys.map(([k, s]) => ({ ...ok(k, s), ...(over[k] ?? {}) })),
        ...extra,
    ],
});

/** Everything present; `over` patches entries by key, `extra` appends entries (e.g. an unknown key). */
export const planInputs = (
    over: Record<string, Partial<InputEntry>> = {},
    extra: InputEntry[] = [],
) => build("plan", PLAN_KEYS, over, extra);

export const blockInputs = (
    over: Record<string, Partial<InputEntry>> = {},
    extra: InputEntry[] = [],
) => build("block", BLOCK_KEYS, over, extra);

/** A blocking missing entry. */
export const MISSING: Partial<InputEntry> = {
    value: null,
    status: "missing",
    blocking: true,
};
