// The one routine checker (T-205, planning/07 section 3 "Complete routine", planning/04 section 9.6). Pure: no I/O, no
// clock. It judges any set of routines against the intention, the days per week and the session length. The block
// generator rejects a block with a hard failure (one retry, then coach_invalid_output); the folder review (T-207)
// shows the same failures as "Falta" items. Catalogue facts (muscles, pattern, equipment) arrive as data.
//
// Failure codes (the structural subset is mirrored in SQL by coach_assert_block, message 'block invariant: <code>';
// see supabase/migrations/20261227100300_coach_block_model_built.sql):
//   h1_volume_low, h1_volume_high, h2_sessions, h3_side_delta, h3b_knee_extension, h4_rep_range, h5_rpe,
//   h5_set_type, h6_equipment, h6_excluded_discomfort, h6_sessions, h6_time, h6_sets_per_exercise, h6_rest,
//   h7_order, superset_invalid, superset_not_needed, superset_sets_mismatch, drop_invalid, drop_not_needed.
// SQL-only structural codes: planned_weight, technique_note, role_pattern, cardio_*.
// Notes (never block): below_recommended, h3_not_applicable, h3b_not_applicable.
import type { Intention, MuscleGroup } from "./schemas.ts";
import { isMainLift } from "./blockRules.ts";

// --- Constants (planning/04 section 9.6) --------------------------------------------------------------------------

/** About 2 s down + 1-2 s up per repetition. Operational estimate: design decision, no evidence claim. */
export const SECONDS_PER_REP = 4;
/** Setup and transition per exercise. */
export const SETUP_SECONDS = 30;
export const MIN_SETS_PER_EXERCISE = 1;
export const MAX_SETS_PER_EXERCISE = 10;
export const RPE_VALUES: readonly number[] = [7, 8, 9];
export const REST_MAIN: readonly [number, number] = [120, 180];
export const REST_ISOLATION: readonly [number, number] = [60, 120];
export const REPS_GENERAL: readonly [number, number] = [6, 20];
export const REPS_STRENGTH_MAIN: readonly [number, number] = [3, 8];
export const WEEKLY_SETS_MIN = 10;
export const WEEKLY_SETS_MAX = 20;
/** "Mantener": at least 4 weekly sets, 6 from 65 (07 H1). */
export const MAINTAIN_MIN = 4;
export const MAINTAIN_MIN_65 = 6;
/**
 * The exercises that satisfy H3b (07 section 3 "Taxonomy"): knee extension only. Hip abduction and adduction, clamshells
 * and the like are isolation work on other muscles and do not count. The one shared check for the routine checker, the
 * block validator and the folder review.
 */
export const KNEE_EXTENSION_KEYS: readonly string[] = [
    "leg-extension",
    "single-leg-extension",
    "banded-terminal-knee-extension",
    "reverse-nordic-curl",
];
export const isKneeExtensionKey = (key: string): boolean =>
    KNEE_EXTENSION_KEYS.includes(key);

/** The groups H1 counts. */
export const H1_GROUPS: readonly MuscleGroup[] = [
    "chest",
    "back",
    "shoulders",
    "quads",
    "hamstrings",
    "glutes",
];
/** Leanest legal set for the "can the minimum volume fit" test: 6 reps and the isolation rest. */
const LEANEST_SET_SECONDS = REPS_GENERAL[0] * SECONDS_PER_REP +
    REST_ISOLATION[0];

// --- Types --------------------------------------------------------------------------------------------------------

export type CheckerSetType = "working" | "drop" | "warm_up" | "failure";

export type CheckerSet = {
    type: CheckerSetType;
    rep_min: number;
    rep_max: number;
    rpe: number;
    rest_seconds: number;
};

export type CheckerExercise = {
    exercise_key: string;
    muscle_group: MuscleGroup;
    secondary_muscles: MuscleGroup[];
    /** movement_pattern of the catalogue row (null for a custom exercise without one). */
    pattern: string | null;
    equipment: string;
    /** False when the sets do not add to H1 (stretch, mobility, cardio, impact, timed hold; 07 section 3). */
    counts_as_strength: boolean;
    sets: CheckerSet[];
    /** Exercises of a superset share the number; absent otherwise. */
    superset_group?: number | null;
};

export type CheckerRoutine = { name: string; exercises: CheckerExercise[] };

export type CheckerInput = {
    routines: CheckerRoutine[];
    intention: Intention;
    days_per_week: number;
    session_minutes: number;
    /** The user's equipment; empty means no restriction (as the SQL mirror). */
    equipment: string[];
    /** Exercise keys with an unresolved level 4-5 discomfort. */
    exclusions: string[];
    /** Groups marked "Mantener": a lower weekly floor (T-190 passes them). */
    maintain_groups?: MuscleGroup[];
    /** Age in years, for the "Mantener" floor. */
    age?: number | null;
    /** A shoulder_abduction exercise exists inside the equipment (H3 condition). Default true. */
    side_delta_available?: boolean;
    /** A quads isolation exercise exists inside the equipment (H3b condition). Default true. */
    knee_extension_available?: boolean;
};

export type CheckerCode =
    | "h1_volume_low"
    | "h1_volume_high"
    | "h2_sessions"
    | "h3_side_delta"
    | "h3b_knee_extension"
    | "h4_rep_range"
    | "h5_rpe"
    | "h5_set_type"
    | "h6_equipment"
    | "h6_excluded_discomfort"
    | "h6_sessions"
    | "h6_time"
    | "h6_sets_per_exercise"
    | "h6_rest"
    | "h7_order"
    | "superset_invalid"
    | "superset_not_needed"
    | "superset_sets_mismatch"
    | "drop_invalid"
    | "drop_not_needed";

export type CheckerNote =
    | "below_recommended"
    | "h3_not_applicable"
    | "h3b_not_applicable";

export type CheckerFailure = {
    code: CheckerCode;
    data: Record<string, unknown>;
};

export type CheckerResult = {
    failures: CheckerFailure[];
    notes: { code: CheckerNote }[];
};

// --- Counting -----------------------------------------------------------------------------------------------------

const isWorkLike = (s: CheckerSet): boolean =>
    s.type === "working" || s.type === "drop";

const TIMED_METRICS = new Set([
    "duration",
    "weight_duration",
    "distance_duration",
    "floors_duration",
    "steps_duration",
]);

/**
 * Whether the sets of a catalogue or custom row add to H1 (07 section 3 "What H1 counts"): the stored flag for a
 * catalogue row; a custom row counts unless its metric type is time- or distance-based.
 */
export const countsAsStrength = (
    row: {
        is_custom: boolean;
        metric_type: string;
        counts_as_strength?: boolean;
    },
): boolean =>
    row.is_custom
        ? !TIMED_METRICS.has(row.metric_type)
        : row.counts_as_strength !== false;

/**
 * Fractional weekly sets per group (planning/04 section 4): primary 1, each DISTINCT secondary 0.5 (a secondary equal to
 * the primary is ignored). Warm-up and failure sets do not count; a drop set counts as one working set. Exercises
 * that do not count as strength (stretches, mobility, cardio, impact moves, timed holds) add nothing (H1 only; they
 * still take time and a slot).
 */
export const fractionalSets = (
    routines: CheckerRoutine[],
): Map<MuscleGroup, number> => {
    const total = new Map<MuscleGroup, number>();
    const add = (g: MuscleGroup, n: number) =>
        total.set(g, (total.get(g) ?? 0) + n);
    for (const r of routines) {
        for (const ex of r.exercises) {
            if (!ex.counts_as_strength) continue;
            const n = ex.sets.filter(isWorkLike).length;
            if (n === 0) continue;
            add(ex.muscle_group, n);
            for (const sec of new Set(ex.secondary_muscles)) {
                if (sec !== ex.muscle_group) add(sec, n * 0.5);
            }
        }
    }
    return total;
};

// --- Time (planning/04 section 9.6, D1-D3) ------------------------------------------------------------------------

const repsMid = (s: CheckerSet): number =>
    Math.ceil((s.rep_min + s.rep_max) / 2);
const work = (s: CheckerSet): number => repsMid(s) * SECONDS_PER_REP;

/**
 * Setup + for each non-drop set work + rest. A drop set adds only its work (no rest); in the `plain` test it is
 * replaced by a normal working set with the rest of the set before it.
 */
const exerciseSeconds = (ex: CheckerExercise, plain: boolean): number => {
    let t = SETUP_SECONDS;
    let lastRest = 0;
    for (const s of ex.sets) {
        if (s.type === "drop") {
            t += work(s) + (plain ? lastRest : 0);
        } else {
            t += work(s) + s.rest_seconds;
            lastRest = s.rest_seconds;
        }
    }
    return t;
};

/** A pair alternates: per round work_a + work_b + max(rest_a, rest_b); one setup per exercise. */
const pairSeconds = (a: CheckerExercise, b: CheckerExercise): number => {
    const rounds = Math.max(a.sets.length, b.sets.length);
    let t = 2 * SETUP_SECONDS;
    for (let i = 0; i < rounds; i++) {
        const x = a.sets[i];
        const y = b.sets[i];
        t += (x ? work(x) : 0) + (y ? work(y) : 0) +
            Math.max(
                x && x.type !== "drop" ? x.rest_seconds : 0,
                y && y.type !== "drop" ? y.rest_seconds : 0,
            );
    }
    return t;
};

/**
 * Seconds of one routine's lifting (cardio is outside the session time). `plain` ungroups every superset and turns
 * every drop set into a normal working set: the "fits without them" test (a drop set that is only removed could never
 * be accepted, since it only adds time).
 */
export const routineSeconds = (
    routine: CheckerRoutine,
    plain = false,
): number => {
    const done = new Set<CheckerExercise>();
    let t = 0;
    for (const ex of routine.exercises) {
        if (done.has(ex)) continue;
        const g = ex.superset_group;
        const partner = !plain && g != null
            ? routine.exercises.filter((o) =>
                o !== ex && o.superset_group === g
            )
            : [];
        if (partner.length === 1) {
            done.add(ex);
            done.add(partner[0]);
            t += pairSeconds(ex, partner[0]);
        } else {
            done.add(ex);
            t += exerciseSeconds(ex, plain);
        }
    }
    return t;
};

// --- The checker --------------------------------------------------------------------------------------------------

const inRange = (n: number, [lo, hi]: readonly [number, number]): boolean =>
    n >= lo && n <= hi;

export const checkRoutines = (input: CheckerInput): CheckerResult => {
    const failures: CheckerFailure[] = [];
    const notes: { code: CheckerNote }[] = [];
    const fail = (code: CheckerCode, data: Record<string, unknown> = {}) =>
        failures.push({ code, data });
    const { routines, intention, days_per_week: days } = input;
    const limit = input.session_minutes * 60;
    const strength = intention === "strength";

    // H6 sessions and H2.
    if (routines.length !== days) {
        fail("h6_sessions", { expected: days, actual: routines.length });
    }
    if (days >= 2 && routines.length < 2) {
        fail("h2_sessions", { sessions: routines.length });
    }

    routines.forEach((routine, ri) => {
        const where = { routine: ri + 1 };
        let seenIsolation = false;
        let orderReported = false;

        for (const ex of routine.exercises) {
            const main = isMainLift(ex.pattern);
            const ctx = { ...where, exercise_key: ex.exercise_key };

            // H6 equipment and discomfort.
            if (
                input.equipment.length > 0 &&
                !input.equipment.includes(ex.equipment)
            ) {
                fail("h6_equipment", { ...ctx, equipment: ex.equipment });
            }
            if (input.exclusions.includes(ex.exercise_key)) {
                fail("h6_excluded_discomfort", ctx);
            }

            // H6 sets per exercise (a drop set counts as a set).
            if (
                ex.sets.length < MIN_SETS_PER_EXERCISE ||
                ex.sets.length > MAX_SETS_PER_EXERCISE
            ) {
                fail("h6_sets_per_exercise", { ...ctx, sets: ex.sets.length });
            }

            // H7: the main lifts go first for "Ganar fuerza".
            if (!main) seenIsolation = true;
            else if (strength && seenIsolation && !orderReported) {
                fail("h7_order", ctx);
                orderReported = true;
            }

            const repBounds = strength && main
                ? REPS_STRENGTH_MAIN
                : REPS_GENERAL;
            const restBounds = main ? REST_MAIN : REST_ISOLATION;
            let drops = 0;
            ex.sets.forEach((s, si) => {
                if (!isWorkLike(s)) {
                    fail("h5_set_type", { ...ctx, set: si + 1, type: s.type });
                }
                if (
                    !Number.isInteger(s.rep_min) ||
                    !Number.isInteger(s.rep_max) ||
                    s.rep_min > s.rep_max ||
                    !inRange(s.rep_min, repBounds) ||
                    !inRange(s.rep_max, repBounds)
                ) {
                    fail("h4_rep_range", {
                        ...ctx,
                        set: si + 1,
                        rep_min: s.rep_min,
                        rep_max: s.rep_max,
                    });
                }
                if (!RPE_VALUES.includes(s.rpe)) {
                    fail("h5_rpe", { ...ctx, set: si + 1, rpe: s.rpe });
                }
                if (!inRange(s.rest_seconds, restBounds)) {
                    fail("h6_rest", {
                        ...ctx,
                        set: si + 1,
                        rest_seconds: s.rest_seconds,
                    });
                }
                if (s.type === "drop") {
                    drops++;
                    const last = si === ex.sets.length - 1;
                    if (
                        drops > 1 || !last || main ||
                        !(ex.pattern === "isolation" ||
                            ex.pattern === "shoulder_abduction")
                    ) {
                        fail("drop_invalid", { ...ctx, set: si + 1 });
                    }
                }
            });
        }

        // Supersets: pairs of different muscles with the same number of sets; no main lift for strength.
        const groups = new Map<number, CheckerExercise[]>();
        for (const ex of routine.exercises) {
            if (ex.superset_group != null) {
                groups.set(ex.superset_group, [
                    ...(groups.get(ex.superset_group) ?? []),
                    ex,
                ]);
            }
        }
        for (const [group, members] of groups) {
            const ctx = { ...where, superset_group: group };
            if (
                members.length !== 2 ||
                members[0].muscle_group === members[1].muscle_group ||
                (strength && members.some((m) => isMainLift(m.pattern)))
            ) {
                fail("superset_invalid", ctx);
            } else if (members[0].sets.length !== members[1].sets.length) {
                fail("superset_sets_mismatch", ctx);
            }
        }

        // H6 time, and supersets / drop sets only when the plain routine does not fit.
        const seconds = routineSeconds(routine);
        const hasSuperset = groups.size > 0;
        const hasDrop = routine.exercises.some((e) =>
            e.sets.some((s) => s.type === "drop")
        );
        if (seconds > limit) {
            fail("h6_time", {
                ...where,
                seconds,
                limit,
                over: seconds - limit,
            });
        }
        if (
            (hasSuperset || hasDrop) && routineSeconds(routine, true) <= limit
        ) {
            if (hasSuperset) fail("superset_not_needed", where);
            if (hasDrop) fail("drop_not_needed", where);
        }
    });

    // H1: weekly fractional sets, unless the minimum cannot fit.
    const counts = fractionalSets(routines);
    const capacity = days * limit;
    const minimumSeconds = H1_GROUPS.length * SETUP_SECONDS +
        H1_GROUPS.length * WEEKLY_SETS_MIN * LEANEST_SET_SECONDS;
    const enforceH1 = days >= 2 && capacity >= minimumSeconds;
    if (!enforceH1) {
        notes.push({ code: "below_recommended" });
    } else {
        const maintain = new Set(input.maintain_groups ?? []);
        const floor = (input.age ?? 0) >= 65 ? MAINTAIN_MIN_65 : MAINTAIN_MIN;
        for (const g of H1_GROUPS) {
            const sets = counts.get(g) ?? 0;
            const min = maintain.has(g) ? floor : WEEKLY_SETS_MIN;
            if (sets < min) fail("h1_volume_low", { group: g, sets, min });
            else if (sets > WEEKLY_SETS_MAX) {
                fail("h1_volume_high", {
                    group: g,
                    sets,
                    max: WEEKLY_SETS_MAX,
                });
            }
        }
    }

    // H3 / H3b: conditions of 07 H3 (2+ days, not strength, a candidate inside the equipment).
    const all = routines.flatMap((r) => r.exercises);
    const hasWork = (e: CheckerExercise) => e.sets.some(isWorkLike);
    const sideDelta = all.some((e) =>
        e.pattern === "shoulder_abduction" && hasWork(e)
    );
    const kneeExt = all.some((e) =>
        isKneeExtensionKey(e.exercise_key) && hasWork(e)
    );
    const common = days >= 2 && !strength;
    if (common && (input.side_delta_available ?? true)) {
        if (!sideDelta) fail("h3_side_delta");
    } else if (!sideDelta) notes.push({ code: "h3_not_applicable" });
    if (common && (input.knee_extension_available ?? true)) {
        if (!kneeExt) fail("h3b_knee_extension");
    } else if (!kneeExt) notes.push({ code: "h3b_not_applicable" });

    return { failures, notes };
};
