// Validation of the model's picks for the training block and the final invariants of the assembled block
// (planning/04 section 9). Pure: no I/O. The nutrition validators live in validators.ts.
import {
    CARDIO_PACE,
    CARDIO_ROUTINE_CAP_MIN,
    CARDIO_ROUTINE_CAP_MINIMAL_MIN,
    CARDIO_TARGET_MAX,
    CARDIO_UNIT_MIN,
    exercisesPerSession,
    isMainLift,
    ISOLATION_PATTERNS,
    MINIMAL_SENIOR_AGE,
    requiredGroups,
    REST,
    sessionsPerMuscle,
    splitCardio,
    STRENGTH_RANGES,
    weeklySetsByMuscle,
} from "./blockRules.ts";
import {
    candidateKey,
    cardioEquipmentAllowed,
    equipmentAllowed,
    isCardioEligible,
    resolvedEquipment,
} from "./candidates.ts";
import {
    type Block,
    type BlockCardioExercise,
    type BlockExercise,
    type BlockPrefs,
    type BlockSkeleton,
    type CatalogExercise,
    isCardioExercise,
    isLifting,
    type ModelBlockPick,
} from "./schemas.ts";
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

/**
 * The picks of one model answer, or null when it breaks any rule (which triggers the single retry). It covers
 * the sessions in `sessionIndexes` (all when omitted): every slot is answered exactly once with a key among
 * that slot's candidates, and an exercise is used once per session.
 */
export function validateBlockText(
    text: string,
    skeleton: BlockSkeleton,
    candidates: Map<string, CatalogExercise[]>,
    sessionIndexes?: number[],
): ModelBlockPick[] | null {
    const parsed = parseJson(text);
    if (!isRecord(parsed) || !sameKeys(parsed, ["picks"])) return null;
    const list = parsed.picks;
    if (!Array.isArray(list)) return null;

    // Lifting slots and cardio slots alike: every slot is answered exactly once.
    const slots = new Map<string, { session: number }>();
    for (const s of skeleton.sessions) {
        if (sessionIndexes && !sessionIndexes.includes(s.session_index)) {
            continue;
        }
        for (const slot of s.slots) {
            slots.set(slot.slot_id, { session: s.session_index });
        }
        if (s.cardio) {
            slots.set(s.cardio.slot_id, { session: s.session_index });
        }
    }
    if (list.length !== slots.size) return null;

    const answered = new Set<string>();
    const usedInSession = new Set<string>();
    const out: ModelBlockPick[] = [];
    for (const item of list) {
        if (
            !isRecord(item) ||
            !sameKeys(item, ["slot_id", "exercise_key", "reason"])
        ) return null;
        const { slot_id, exercise_key, reason } = item;
        if (typeof slot_id !== "string" || typeof exercise_key !== "string") {
            return null;
        }
        const target = slots.get(slot_id);
        if (!target || answered.has(slot_id)) return null;
        const options = candidates.get(slot_id) ?? [];
        if (!options.some((e) => candidateKey(e) === exercise_key)) return null;
        const used = `${target.session}:${exercise_key}`;
        if (usedInSession.has(used)) return null;
        if (!validReason(reason)) return null;
        answered.add(slot_id);
        usedInSession.add(used);
        out.push({ slot_id, exercise_key, reason: reason.trim() });
    }
    return out;
}

/** Joins the skeleton and the picks into the block; the arithmetic (weekly sets, frequency) is the server's. */
export function assembleBlock(
    skeleton: BlockSkeleton,
    picks: ModelBlockPick[],
    candidates: Map<string, CatalogExercise[]>,
    prefs: Pick<BlockPrefs, "equipment" | "place">,
): Block {
    const bySlot = new Map(picks.map((p) => [p.slot_id, p]));
    const sessions = skeleton.sessions.map((s) => {
        const lifting: BlockExercise[] = s.slots.map((slot) => {
            const pick = bySlot.get(slot.slot_id)!;
            const chosen = candidates.get(slot.slot_id)!.find((e) =>
                candidateKey(e) === pick.exercise_key
            )!;
            return {
                exercise_id: chosen.id,
                exercise_key: pick.exercise_key,
                role: slot.role,
                muscle_group: slot.muscle_group,
                sets: slot.sets,
                rep_min: slot.rep_min,
                rep_max: slot.rep_max,
                rest_seconds: slot.rest_seconds,
                reason: pick.reason,
            };
        });
        const exercises: (BlockExercise | BlockCardioExercise)[] = [...lifting];
        if (s.cardio) {
            const pick = bySlot.get(s.cardio.slot_id)!;
            const chosen = candidates.get(s.cardio.slot_id)!.find((e) =>
                candidateKey(e) === pick.exercise_key
            )!;
            // Minutes and pace come from the server, never from the model.
            exercises.push({
                exercise_id: chosen.id,
                exercise_key: pick.exercise_key,
                role: "cardio",
                sets: 1,
                duration_min: s.cardio.minutes,
                pace: CARDIO_PACE,
                reason: pick.reason,
            });
        }
        return {
            session_index: s.session_index,
            name: s.name,
            focus: s.focus,
            exercises,
        };
    });
    const liftingOf = sessions.map((s) => s.exercises.filter(isLifting));
    return {
        training: {
            phase: skeleton.phase,
            days_per_week: skeleton.days_per_week,
            days_chosen: skeleton.days_chosen,
            session_minutes: skeleton.session_minutes,
            rep_scheme: skeleton.rep_scheme,
            block_weeks: skeleton.block_weeks,
            equipment: resolvedEquipment(prefs),
            weekly_sets: weeklySetsByMuscle(liftingOf.flat()),
            sessions_per_muscle: sessionsPerMuscle(liftingOf),
            cardio: { ...skeleton.cardio, pace: CARDIO_PACE },
            routine_total_min: skeleton.cardio.per_routine_min.map((m) =>
                skeleton.session_minutes + m
            ),
        },
        sessions,
    };
}

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
 * Defensive final check of an assembled block (attach_coach_block repeats it in SQL, same rule names). Throws on
 * the first break.
 */
export function assertBlockInvariants(
    block: Block,
    prefs:
        & Pick<BlockPrefs, "intention" | "place" | "equipment">
        & Partial<Pick<BlockPrefs, "cardio_min_week" | "age">>,
    catalog: CatalogExercise[],
): void {
    const fail = (rule: string): never => {
        throw new BlockInvariantError(rule);
    };
    const t = block.training;
    const byId = new Map(catalog.map((e) => [e.id, e]));
    // The minimal phase (07 section 12.3): 1 session and 1 set per slot, 2 and 2 from 65 years, main lifts only.
    const minimal = t.phase === "minimal";
    const senior = (prefs.age ?? 0) >= MINIMAL_SENIOR_AGE;
    const routineCap = minimal
        ? CARDIO_ROUTINE_CAP_MINIMAL_MIN
        : CARDIO_ROUTINE_CAP_MIN;

    if (
        t.days_per_week < 1 || t.days_per_week > 7 ||
        block.sessions.length !== t.days_per_week
    ) fail("session_count");
    if (minimal && t.days_per_week !== (senior ? 2 : 1)) {
        fail("minimal_sessions");
    }

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
    const split = splitCardio(c.target_min_week, t.days_per_week, routineCap);
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

    const rangeOf = new Map<string, string>();
    const mainRangePerSession: string[] = [];
    block.sessions.forEach((s, i) => {
        if (s.session_index !== i + 1) fail("session_index");
        if (s.exercises.length === 0) fail("empty_session");
        let seenIsolation = false;
        let seenCardio = false;
        let lifting = 0;
        const ids = new Set<string>();
        for (const e of s.exercises) {
            if (
                WARMUP.test(`${e.role} ${e.exercise_key}`)
            ) fail("warmup");
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
                    e.duration_min > routineCap ||
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
            if (e.role === "main") {
                if (!isMainLift(row.movement_pattern)) fail("role_pattern");
                if (seenIsolation) fail("order");
            } else if (e.role === "isolation") {
                if (minimal) fail("minimal_main_only");
                if (!ISOLATION_PATTERNS.includes(row.movement_pattern ?? "")) {
                    fail("role_pattern");
                }
                seenIsolation = true;
            } else fail("role");
            const rest = REST[e.role];
            if (e.rest_seconds < rest.min || e.rest_seconds > rest.max) {
                fail("rest");
            }
            if (!Number.isInteger(e.sets) || e.sets < 1 || e.sets > 10) {
                fail("sets");
            }
            if (
                !Number.isInteger(e.rep_min) || !Number.isInteger(e.rep_max) ||
                e.rep_min < 1 || e.rep_max > 50 || e.rep_min > e.rep_max
            ) fail("rep_range");
            if (!validReason(e.reason)) fail("reason");
            if (minimal && e.sets !== (senior ? 2 : 1)) fail("minimal_sets");

            const range = `${e.rep_min}-${e.rep_max}`;
            // A minimal block keeps the previous block's ranges, whatever they were.
            if (minimal) {
                const prev = rangeOf.get(e.exercise_id);
                if (prev && prev !== range) fail("range_changed");
                rangeOf.set(e.exercise_id, range);
            } else if (prefs.intention === "strength") {
                if (e.role === "main") {
                    if (
                        !STRENGTH_RANGES.some((r) =>
                            `${r[0]}-${r[1]}` === range
                        )
                    ) {
                        fail("strength_range");
                    }
                    mainRangePerSession[i] ??= range;
                    if (mainRangePerSession[i] !== range) {
                        fail("strength_session_range");
                    }
                } else if (range !== "10-15") fail("isolation_range");
            } else {
                if (range !== (e.role === "main" ? "6-10" : "10-15")) {
                    fail("single_range");
                }
                const prev = rangeOf.get(e.exercise_id);
                if (prev && prev !== range) fail("range_changed");
                rangeOf.set(e.exercise_id, range);
            }
        }
        // No cardio-only routine, and a routine with cardio minutes carries its cardio exercise.
        if (lifting === 0) fail("empty_session");
        if (split.per_routine[i] > 0 && !seenCardio) fail("cardio_total");
    });
    if (
        !minimal && prefs.intention === "strength" &&
        mainRangePerSession.length >= 2
    ) {
        if (new Set(mainRangePerSession.filter(Boolean)).size < 2) {
            fail("strength_not_undulating");
        }
    }

    // Weekly sets and frequency count the lifting only.
    const liftingBySession = block.sessions.map((s) =>
        s.exercises.filter(isLifting)
    );
    const weekly = weeklySetsByMuscle(liftingBySession.flat());
    if (sortKeys(weekly) !== sortKeys(t.weekly_sets)) fail("weekly_sets");
    const per = sessionsPerMuscle(liftingBySession);
    if (sortKeys(per) !== sortKeys(t.sessions_per_muscle)) {
        fail("sessions_per_muscle");
    }
    for (
        const g of minimal ? [] : requiredGroups(
            t.days_per_week,
            exercisesPerSession(t.session_minutes),
        )
    ) {
        if ((per[g] ?? 0) < 2) fail("frequency_per_muscle");
    }
    // The block has no steps or range-of-motion fields (the cardio plan lives in training.cardio).
    for (const k of ["steps", "range_of_motion"]) {
        if (k in (t as Record<string, unknown>)) fail("forbidden_field");
    }
}
