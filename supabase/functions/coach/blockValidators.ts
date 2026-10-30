// Validation of the model's picks for the training block and the final invariants of the assembled block
// (planning/04 section 9). Pure: no I/O. The nutrition validators live in validators.ts.
import {
    exercisesPerSession,
    isMainLift,
    ISOLATION_PATTERNS,
    requiredGroups,
    REST,
    sessionsPerMuscle,
    STRENGTH_RANGES,
    weeklySetsByMuscle,
} from "./blockRules.ts";
import { candidateKey, equipmentAllowed } from "./candidates.ts";
import type {
    Block,
    BlockPrefs,
    BlockSkeleton,
    BlockSlot,
    CatalogExercise,
    ModelBlockPick,
} from "./schemas.ts";
import {
    isRecord,
    parseJson,
    REASON_MAX,
    REASON_MIN,
    sameKeys,
} from "./validators.ts";

/** Phrases that advise less cardio or fewer steps: the block never reduces either (07 section 3). */
const CARDIO_REDUCTION: RegExp[] = [
    /menos\s+pasos/i,
    /menos\s+cardio/i,
    /\b(reduc\w*|disminu\w*|recort\w*|acort\w*|limit\w*)\b[^.]{0,40}\b(cardio|pasos)\b/i,
    /\b(evit\w*|elimin\w*|quit\w*|deja\w*)\b[^.]{0,40}\b(cardio|pasos)\b/i,
];
export const mentionsCardioReduction = (text: string): boolean =>
    CARDIO_REDUCTION.some((re) => re.test(text));

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

    const slots = new Map<string, { slot: BlockSlot; session: number }>();
    for (const s of skeleton.sessions) {
        if (sessionIndexes && !sessionIndexes.includes(s.session_index)) {
            continue;
        }
        for (const slot of s.slots) {
            slots.set(slot.slot_id, { slot, session: s.session_index });
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
    const sessions = skeleton.sessions.map((s) => ({
        session_index: s.session_index,
        name: s.name,
        focus: s.focus,
        exercises: s.slots.map((slot) => {
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
        }),
    }));
    return {
        training: {
            days_per_week: skeleton.days_per_week,
            days_chosen: skeleton.days_chosen,
            session_minutes: skeleton.session_minutes,
            rep_scheme: skeleton.rep_scheme,
            block_weeks: skeleton.block_weeks,
            equipment: [...prefs.equipment],
            weekly_sets: weeklySetsByMuscle(
                sessions.flatMap((s) => s.exercises),
            ),
            sessions_per_muscle: sessionsPerMuscle(
                sessions.map((s) => s.exercises),
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

/** Defensive final check of an assembled block (attach_coach_block repeats it in SQL). Throws on the first break. */
export function assertBlockInvariants(
    block: Block,
    prefs: Pick<BlockPrefs, "intention" | "place" | "equipment">,
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

    const rangeOf = new Map<string, string>();
    const mainRangePerSession: string[] = [];
    block.sessions.forEach((s, i) => {
        if (s.session_index !== i + 1) fail("session_index");
        if (s.exercises.length === 0) fail("empty_session");
        let seenIsolation = false;
        const ids = new Set<string>();
        for (const e of s.exercises) {
            const row = byId.get(e.exercise_id);
            if (!row) return fail("unknown_exercise");
            if (ids.has(e.exercise_id)) fail("duplicate_exercise");
            ids.add(e.exercise_id);
            if (!equipmentAllowed(prefs, row.equipment)) fail("equipment");
            if (e.role === "main") {
                if (!isMainLift(row.movement_pattern)) fail("role_pattern");
                if (seenIsolation) fail("order");
            } else if (e.role === "isolation") {
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

            const range = `${e.rep_min}-${e.rep_max}`;
            if (prefs.intention === "strength") {
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
    });
    if (prefs.intention === "strength" && mainRangePerSession.length >= 2) {
        if (new Set(mainRangePerSession.filter(Boolean)).size < 2) {
            fail("strength_not_undulating");
        }
    }

    const weekly = weeklySetsByMuscle(
        block.sessions.flatMap((s) => s.exercises),
    );
    if (sortKeys(weekly) !== sortKeys(t.weekly_sets)) fail("weekly_sets");
    const per = sessionsPerMuscle(block.sessions.map((s) => s.exercises));
    if (sortKeys(per) !== sortKeys(t.sessions_per_muscle)) {
        fail("sessions_per_muscle");
    }
    for (
        const g of requiredGroups(
            t.days_per_week,
            exercisesPerSession(t.session_minutes),
        )
    ) {
        if ((per[g] ?? 0) < 2) fail("frequency_per_muscle");
    }
    // The block has no cardio, steps or range-of-motion fields at all.
    for (const k of ["cardio", "steps", "range_of_motion"]) {
        if (k in (t as Record<string, unknown>)) fail("forbidden_field");
    }
}
