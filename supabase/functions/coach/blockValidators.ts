// Validation of the model's block (T-205): the JSON shape, the grounding rules of planning/04 section 9.7 (D7), then the
// one routine checker. Plus the assembly of the stored block and its final invariants. Pure: no I/O. The nutrition
// validators live in validators.ts.
import {
    CARDIO_PACE,
    CARDIO_ROUTINE_CAP_MIN,
    CARDIO_TARGET_MAX,
    CARDIO_UNIT_MIN,
    isMainLift,
    sessionsPerMuscle,
    splitCardio,
    weeklySetsByMuscle,
} from "./blockRules.ts";
import {
    candidateKey,
    cardioEquipmentAllowed,
    type EligibleCatalogue,
    equipmentAllowed,
    isCardioEligible,
    resolvedEquipment,
} from "./candidates.ts";
import {
    type Block,
    type BlockCardioExercise,
    type BlockExercise,
    type BlockPrefs,
    type BlockSession,
    type BlockSet,
    type BlockTraining,
    type CatalogExercise,
    isCardioExercise,
    isLifting,
    type ModelBlock,
    type ModelExercise,
    type MuscleGroup,
    type Phase,
    type Split,
} from "./schemas.ts";
import {
    type CheckerInput,
    type CheckerRoutine,
    checkRoutines,
    MAX_SETS_PER_EXERCISE,
} from "./routineChecker.ts";
import {
    isRecord,
    mentionsCardioReduction,
    parseJson,
    REASON_MAX,
    REASON_MIN,
    sameKeys,
} from "./validators.ts";

// The phrase list lives in validators.ts (shared with the plan_nutrition reasons).
export { mentionsCardioReduction };

const validReason = (r: unknown): r is string => {
    if (typeof r !== "string") return false;
    const t = r.trim();
    return t.length >= REASON_MIN && t.length <= REASON_MAX &&
        !mentionsCardioReduction(t);
};

/** One reason of the model's block; the prompt asks for 160 characters, the validator accepts the stored bound. */
export const TECHNIQUE_NOTE_MAX = 200;
export const SUPERSET_GROUP_MAX = 20;
export const PLANNED_WEIGHT_MAX_KG = 1000;

/** What the validators know besides the text: the user's inputs and what the model was shown. */
export type BlockValidationContext = {
    prefs: Pick<BlockPrefs, "intention" | "place" | "equipment" | "age">;
    days: number;
    minutes: number;
    eligible: EligibleCatalogue;
    /** Keys of exercises with an unresolved level 4-5 discomfort (never in the catalogue). */
    excludedKeys: string[];
    /** Exercise keys present in the history the model saw. */
    history: Set<string>;
    hasPrevious: boolean;
    /** Planned cardio minutes per routine (04 section 9.11). */
    cardioPerRoutine: number[];
};

export type Failure = { code: string; [k: string]: unknown };

export type ModelBlockResult = {
    /** The parsed block, or null when anything failed. */
    block: ModelBlock | null;
    failures: Failure[];
    /** Non-blocking notes of the checker (e.g. "below_recommended"). */
    notes: string[];
};

const EXERCISE_KEYS = [
    "exercise_key",
    "sets",
    "reason",
    "superset_group",
    "planned_weight_kg",
    "technique_note",
];
const SET_KEYS = ["n", "type", "rep_min", "rep_max", "rpe", "rest_seconds"];

const isInt = (v: unknown): v is number =>
    typeof v === "number" && Number.isInteger(v);

/** Parses the model's text into a block, or the shape failures that refuse it. */
function parseModelBlock(
    text: string,
    days: number,
): { block: ModelBlock | null; failures: Failure[] } {
    const failures: Failure[] = [];
    const shape = (path: string) => failures.push({ code: "shape", path });
    const parsed = parseJson(text);
    if (
        !isRecord(parsed) ||
        !sameKeys(parsed, [
            "sessions",
            "split_reason",
            "changes_vs_last_block",
        ]) ||
        !Array.isArray(parsed.sessions) ||
        typeof parsed.split_reason !== "string" ||
        typeof parsed.changes_vs_last_block !== "string"
    ) return { block: null, failures: [{ code: "shape", path: "root" }] };
    if (parsed.sessions.length !== days) {
        failures.push({
            code: "h6_sessions",
            expected: days,
            actual: parsed.sessions.length,
        });
        return { block: null, failures };
    }
    const sessions: ModelBlock["sessions"] = [];
    parsed.sessions.forEach((s, si) => {
        const where = `sessions[${si}]`;
        if (
            !isRecord(s) ||
            !sameKeys(s, ["session_index", "exercises", "cardio_reason"]) ||
            s.session_index !== si + 1 || !Array.isArray(s.exercises) ||
            s.exercises.length === 0 || typeof s.cardio_reason !== "string"
        ) return shape(where);
        const exercises: ModelExercise[] = [];
        s.exercises.forEach((x, xi) => {
            const at = `${where}.exercises[${xi}]`;
            if (
                !isRecord(x) ||
                !Object.keys(x).every((k) => EXERCISE_KEYS.includes(k)) ||
                typeof x.exercise_key !== "string" ||
                typeof x.reason !== "string" || !Array.isArray(x.sets) ||
                x.sets.length === 0 ||
                ("superset_group" in x && !isInt(x.superset_group)) ||
                ("planned_weight_kg" in x &&
                    typeof x.planned_weight_kg !== "number") ||
                ("technique_note" in x && typeof x.technique_note !== "string")
            ) return shape(at);
            const sets: BlockSet[] = [];
            for (const g of x.sets as unknown[]) {
                if (
                    !isRecord(g) || !sameKeys(g, SET_KEYS) ||
                    !isInt(g.n) || g.n < 1 || g.n > MAX_SETS_PER_EXERCISE ||
                    (g.type !== "working" && g.type !== "drop") ||
                    !isInt(g.rep_min) || !isInt(g.rep_max) || !isInt(g.rpe) ||
                    !isInt(g.rest_seconds)
                ) return shape(`${at}.sets`);
                for (let i = 0; i < g.n; i++) {
                    sets.push({
                        type: g.type,
                        rep_min: g.rep_min,
                        rep_max: g.rep_max,
                        rpe: g.rpe,
                        rest_seconds: g.rest_seconds,
                    });
                }
            }
            exercises.push({
                exercise_key: x.exercise_key,
                sets,
                reason: x.reason,
                ...(isInt(x.superset_group)
                    ? { superset_group: x.superset_group }
                    : {}),
                ...(typeof x.planned_weight_kg === "number"
                    ? { planned_weight_kg: x.planned_weight_kg }
                    : {}),
                ...(typeof x.technique_note === "string"
                    ? { technique_note: x.technique_note }
                    : {}),
            });
        });
        sessions.push({
            session_index: si + 1,
            exercises,
            cardio_reason: s.cardio_reason,
        });
    });
    if (failures.length > 0) return { block: null, failures };
    return {
        block: {
            sessions,
            split_reason: parsed.split_reason.trim(),
            changes_vs_last_block: parsed.changes_vs_last_block.trim() === ""
                ? null
                : parsed.changes_vs_last_block.trim(),
        },
        failures,
    };
}

/**
 * Judges a parsed block: the grounding rules of 04 section 9.7 (exercise inside the eligible list, once per routine,
 * planned weight and technique note only where the history allows, reasons) and then the routine checker (H1-H7).
 */
export function checkModelBlock(
    block: ModelBlock,
    ctx: BlockValidationContext,
): { failures: Failure[]; notes: string[] } {
    const failures: Failure[] = [];
    const byKey = new Map(
        ctx.eligible.exercises.map((e) => [candidateKey(e), e]),
    );
    let notes = 0;

    if (!validReason(block.split_reason)) {
        failures.push({ code: "split_reason" });
    }
    if (ctx.hasPrevious) {
        if (!validReason(block.changes_vs_last_block)) {
            failures.push({ code: "changes_vs_last_block" });
        }
    }

    let unknown = false;
    const routines: CheckerRoutine[] = [];
    block.sessions.forEach((s, si) => {
        const seen = new Set<string>();
        const exercises: CheckerRoutine["exercises"] = [];
        for (const x of s.exercises) {
            const row = byKey.get(x.exercise_key);
            const where = { routine: si + 1, exercise_key: x.exercise_key };
            if (!row) {
                unknown = true;
                failures.push({
                    code: ctx.excludedKeys.includes(x.exercise_key)
                        ? "h6_excluded_discomfort"
                        : "unknown_exercise",
                    ...where,
                });
                continue;
            }
            if (seen.has(x.exercise_key)) {
                failures.push({ code: "duplicate_exercise", ...where });
            }
            seen.add(x.exercise_key);
            if (!validReason(x.reason)) {
                failures.push({ code: "reason", ...where });
            }
            if (x.planned_weight_kg !== undefined) {
                if (!ctx.history.has(x.exercise_key)) {
                    failures.push({
                        code: "planned_weight_no_history",
                        ...where,
                    });
                } else if (
                    !(x.planned_weight_kg > 0) ||
                    x.planned_weight_kg > PLANNED_WEIGHT_MAX_KG
                ) failures.push({ code: "planned_weight", ...where });
            }
            if (x.technique_note !== undefined) {
                notes++;
                const t = x.technique_note.trim();
                if (
                    t.length < 1 || t.length > TECHNIQUE_NOTE_MAX ||
                    ctx.history.has(x.exercise_key)
                ) failures.push({ code: "technique_note", ...where });
            }
            if (
                x.superset_group !== undefined &&
                (x.superset_group < 1 || x.superset_group > SUPERSET_GROUP_MAX)
            ) failures.push({ code: "superset_invalid", routine: si + 1 });
            exercises.push({
                exercise_key: x.exercise_key,
                muscle_group: row.muscle_group,
                secondary_muscles: row.secondary_muscles ?? [],
                pattern: row.movement_pattern,
                equipment: row.equipment,
                sets: x.sets,
                superset_group: x.superset_group ?? null,
            });
        }
        if (
            (ctx.cardioPerRoutine[si] ?? 0) > 0 &&
            !validReason(s.cardio_reason)
        ) failures.push({ code: "cardio_reason", routine: si + 1 });
        routines.push({ name: `Rutina ${si + 1}`, exercises });
    });
    if (notes > 1) {
        failures.push({ code: "technique_note_count", count: notes });
    }

    let checkerNotes: string[] = [];
    if (!unknown) {
        const input: CheckerInput = {
            routines,
            intention: ctx.prefs.intention,
            days_per_week: ctx.days,
            session_minutes: ctx.minutes,
            equipment: ctx.prefs.equipment,
            exclusions: ctx.excludedKeys,
            age: ctx.prefs.age ?? null,
            side_delta_available: ctx.eligible.exercises.some((e) =>
                e.movement_pattern === "shoulder_abduction"
            ),
            knee_extension_available: ctx.eligible.exercises.some((e) =>
                e.movement_pattern === "isolation" && e.muscle_group === "quads"
            ),
        };
        const r = checkRoutines(input);
        for (const f of r.failures) failures.push({ code: f.code, ...f.data });
        checkerNotes = r.notes.map((n) => n.code);
    }
    return { failures, notes: checkerNotes };
}

/** The block of one model answer, or the failures that refuse it (which trigger the single retry). */
export function validateModelBlock(
    text: string,
    ctx: BlockValidationContext,
): ModelBlockResult {
    const parsed = parseModelBlock(text, ctx.days);
    if (parsed.block === null) {
        return { block: null, failures: parsed.failures, notes: [] };
    }
    const checked = checkModelBlock(parsed.block, ctx);
    return {
        block: checked.failures.length === 0 ? parsed.block : null,
        failures: checked.failures,
        notes: checked.notes,
    };
}

// --- Assembly ----------------------------------------------------------------------------------------------------

export type AssembleArgs = {
    model: ModelBlock;
    split: Split;
    eligible: EligibleCatalogue;
    prefs: Pick<
        BlockPrefs,
        "intention" | "place" | "equipment" | "cardio_min_week"
    >;
    days: number;
    sessionMinutes: number;
    daysChosen: boolean;
    phase: Phase;
    blockWeeks: number;
    cardio: { per_routine: number[]; planned: number; on_your_own: number };
    /** The one cardio exercise the server chose; null when the weekly target plans no cardio. */
    cardioExercise: CatalogExercise | null;
};

/** Joins the model's routines, the split names and the server's cardio into the stored block. */
export function assembleBlock(a: AssembleArgs): Block {
    const byKey = new Map(
        a.eligible.exercises.map((e) => [candidateKey(e), e]),
    );
    const sessions: BlockSession[] = a.model.sessions.map((s, i) => {
        const lifting: BlockExercise[] = s.exercises.map((x) => {
            const row = byKey.get(x.exercise_key)!;
            const working = x.sets.find((st) => st.type === "working") ??
                x.sets[0];
            return {
                exercise_id: row.id,
                exercise_key: x.exercise_key,
                role: isMainLift(row.movement_pattern) ? "main" : "isolation",
                muscle_group: row.muscle_group,
                sets: x.sets.map((st) => ({ ...st })),
                rep_min: Math.min(...x.sets.map((st) => st.rep_min)),
                rep_max: Math.max(...x.sets.map((st) => st.rep_max)),
                rest_seconds: working.rest_seconds,
                target_rpe: working.rpe,
                ...(x.superset_group !== undefined
                    ? { superset_group: x.superset_group }
                    : {}),
                ...(x.planned_weight_kg !== undefined
                    ? { planned_weight_kg: x.planned_weight_kg }
                    : {}),
                ...(x.technique_note !== undefined
                    ? { technique_note: x.technique_note.trim() }
                    : {}),
                reason: x.reason.trim(),
            };
        });
        const exercises: (BlockExercise | BlockCardioExercise)[] = [...lifting];
        const minutes = a.cardio.per_routine[i] ?? 0;
        if (minutes > 0 && a.cardioExercise) {
            // Minutes and pace come from the server, never from the model.
            exercises.push({
                exercise_id: a.cardioExercise.id,
                exercise_key: candidateKey(a.cardioExercise),
                role: "cardio",
                sets: 1,
                duration_min: minutes,
                pace: CARDIO_PACE,
                reason: s.cardio_reason.trim(),
            });
        }
        const planned = a.split.sessions[i];
        return {
            session_index: i + 1,
            name: planned.name,
            focus: planned.focus,
            exercises,
        };
    });
    const liftingOf = sessions.map((s) => s.exercises.filter(isLifting));
    const training: BlockTraining = {
        phase: a.phase,
        intention: a.prefs.intention,
        days_per_week: a.days,
        days_chosen: a.daysChosen,
        session_minutes: a.sessionMinutes,
        block_weeks: a.blockWeeks,
        equipment: resolvedEquipment(a.prefs),
        weekly_sets: weeklySetsByMuscle(
            liftingOf.flat().map((e) => ({
                muscle_group: e.muscle_group,
                sets: e.sets.length,
            })),
        ),
        sessions_per_muscle: sessionsPerMuscle(liftingOf),
        cardio: {
            target_min_week: a.prefs.cardio_min_week,
            planned_min_week: a.cardio.planned,
            on_your_own_min: a.cardio.on_your_own,
            per_routine_min: a.cardio.per_routine,
            pace: CARDIO_PACE,
        },
        routine_total_min: a.cardio.per_routine.map((m) =>
            a.sessionMinutes + m
        ),
    };
    return { training, sessions };
}

// --- Final invariants ---------------------------------------------------------------------------------------------

export class BlockInvariantError extends Error {
    constructor(readonly rule: string) {
        super(`block invariant: ${rule}`);
        this.name = "BlockInvariantError";
    }
}

const sortKeys = (o: Record<string, number>) =>
    JSON.stringify(
        Object.entries(o).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0),
    );

const WARMUP = /warm[-_ ]?up/i;
const CARDIO_FORBIDDEN_FIELDS = [
    "rep_min",
    "rep_max",
    "rest_seconds",
    "rpe",
    "target_rpe",
    "reps",
];

/**
 * Defensive final check of an assembled block (attach_coach_block repeats the structural part in SQL, same rule
 * names). Throws on the first break. The routine rules (H1-H7) were judged by the checker before this.
 */
export function assertBlockInvariants(
    block: Block,
    prefs:
        & Pick<BlockPrefs, "place" | "equipment">
        & Partial<Pick<BlockPrefs, "cardio_min_week">>,
    catalog: CatalogExercise[],
): void {
    const fail = (rule: string): never => {
        throw new BlockInvariantError(rule);
    };
    const t = block.training;
    const byId = new Map(catalog.map((e) => [e.id, e]));

    if (
        t.days_per_week < 1 || t.days_per_week > 7 ||
        block.sessions.length !== t.days_per_week
    ) fail("session_count");

    // Weekly cardio: the stated split must be the one derived from the target.
    const c = t.cardio;
    if (
        !c || !Number.isInteger(c.target_min_week) || c.target_min_week < 0 ||
        c.target_min_week > CARDIO_TARGET_MAX
    ) fail("cardio_total");
    if (
        prefs.cardio_min_week !== undefined &&
        prefs.cardio_min_week !== c.target_min_week
    ) fail("cardio_total");
    const split = splitCardio(
        c.target_min_week,
        t.days_per_week,
        CARDIO_ROUTINE_CAP_MIN,
    );
    if (
        c.planned_min_week !== split.planned ||
        c.on_your_own_min !== split.on_your_own ||
        JSON.stringify(c.per_routine_min) !==
            JSON.stringify(split.per_routine) ||
        JSON.stringify(t.routine_total_min) !==
            JSON.stringify(
                split.per_routine.map((m) => t.session_minutes + m),
            )
    ) fail("cardio_total");
    if (c.pace !== CARDIO_PACE) fail("cardio_pace");

    block.sessions.forEach((s, i) => {
        if (s.session_index !== i + 1) fail("session_index");
        if (s.exercises.length === 0) fail("empty_session");
        let seenCardio = false;
        let lifting = 0;
        const ids = new Set<string>();
        for (const e of s.exercises) {
            if (WARMUP.test(`${e.role} ${e.exercise_key}`)) fail("warmup");
            const row = byId.get(e.exercise_id);
            if (!row) return fail("unknown_exercise");

            if (isCardioExercise(e)) {
                if (seenCardio) fail("cardio_count");
                seenCardio = true;
                if (split.per_routine[i] === 0) fail("cardio_total");
                if (
                    CARDIO_FORBIDDEN_FIELDS.some((k) =>
                        k in (e as Record<string, unknown>)
                    )
                ) fail("cardio_fields");
                if (ids.has(e.exercise_id)) fail("duplicate_exercise");
                ids.add(e.exercise_id);
                if (
                    !isCardioEligible(row) ||
                    !cardioEquipmentAllowed(prefs, row)
                ) fail("cardio_equipment");
                if (e.sets !== 1) fail("sets");
                if (
                    !Number.isInteger(e.duration_min) ||
                    e.duration_min < CARDIO_UNIT_MIN ||
                    e.duration_min > CARDIO_ROUTINE_CAP_MIN ||
                    e.duration_min % CARDIO_UNIT_MIN !== 0 ||
                    e.duration_min !== split.per_routine[i] ||
                    e.pace !== CARDIO_PACE
                ) fail("cardio_minutes");
                if (!validReason(e.reason)) fail("reason");
                continue;
            }

            if (seenCardio) fail("cardio_order");
            lifting++;
            if (ids.has(e.exercise_id)) fail("duplicate_exercise");
            ids.add(e.exercise_id);
            if (!equipmentAllowed(prefs, row.equipment)) fail("equipment");
            const main = isMainLift(row.movement_pattern);
            if ((e.role === "main") !== main) fail("role_pattern");
            if (!Array.isArray(e.sets) || e.sets.length === 0) fail("sets");
            if (!validReason(e.reason)) fail("reason");
        }
        // No cardio-only routine, and a routine with cardio minutes carries its cardio exercise.
        if (lifting === 0) fail("empty_session");
        if (split.per_routine[i] > 0 && !seenCardio) fail("cardio_total");
    });

    // Weekly sets and frequency count the lifting only.
    const liftingBySession = block.sessions.map((s) =>
        s.exercises.filter(isLifting)
    );
    const weekly = weeklySetsByMuscle(
        liftingBySession.flat().map((e) => ({
            muscle_group: e.muscle_group as MuscleGroup,
            sets: e.sets.length,
        })),
    );
    if (sortKeys(weekly) !== sortKeys(t.weekly_sets)) fail("weekly_sets");
    const per = sessionsPerMuscle(liftingBySession);
    if (sortKeys(per) !== sortKeys(t.sessions_per_muscle)) {
        fail("sessions_per_muscle");
    }
    // The block has no steps or range-of-motion fields (the cardio plan lives in training.cardio).
    for (const k of ["steps", "range_of_motion"]) {
        if (k in (t as Record<string, unknown>)) fail("forbidden_field");
    }
}
