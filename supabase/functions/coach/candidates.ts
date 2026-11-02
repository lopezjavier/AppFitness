// Server-built candidate exercises per slot of the block (planning/04 section 9.4). The model picks among these
// keys only. Pure: no I/O.
import { CoachInputError } from "./context.ts";
import type {
    BlockPrefs,
    BlockSkeleton,
    BlockSlot,
    CatalogExercise,
    MuscleGroup,
    Role,
} from "./schemas.ts";

export const MAX_CANDIDATES_PER_SLOT = 12;

type Pool = { groups: MuscleGroup[]; patterns: string[] };

/**
 * Which catalogue rows can fill a slot. The catalogue files squats, lunges and hinges under glutes (and leg
 * curls / extensions under hamstrings / quads), so the lower-body main slots read from more than one group.
 */
const MAIN_POOLS: Partial<Record<MuscleGroup, Pool>> = {
    quads: { groups: ["quads", "glutes"], patterns: ["squat", "lunge"] },
    hamstrings: { groups: ["hamstrings", "glutes"], patterns: ["hinge"] },
    glutes: { groups: ["glutes"], patterns: ["hinge", "lunge"] },
    chest: {
        groups: ["chest"],
        patterns: ["horizontal_push", "vertical_push"],
    },
    back: { groups: ["back"], patterns: ["horizontal_pull", "vertical_pull"] },
    shoulders: {
        groups: ["shoulders"],
        patterns: ["vertical_push", "horizontal_push"],
    },
};

export function slotPool(group: MuscleGroup, role: Role): Pool {
    if (role === "main") {
        return MAIN_POOLS[group] ?? { groups: [], patterns: [] };
    }
    return {
        groups: [group],
        patterns: group === "core" ? ["core"] : ["isolation"],
    };
}

/** Metric types that a routine set with repetitions can describe. */
export const STRENGTH_METRICS: readonly string[] = [
    "weight_reps",
    "reps_only",
    "bodyweight",
    "weighted",
    "assisted",
];

/** The identifier the model sees: the catalogue key, or the id of a custom exercise. */
export const candidateKey = (e: CatalogExercise): string => e.key ?? e.id;

/** Equipment filter: a gym with nothing selected means all of it; otherwise only what was selected. */
export function equipmentAllowed(
    prefs: Pick<BlockPrefs, "place" | "equipment">,
    equipment: string,
): boolean {
    if (prefs.place === "gym" && prefs.equipment.length === 0) return true;
    return prefs.equipment.includes(equipment);
}

// --- Cardio (04 section 9.4) -------------------------------------------------------------------------------

/** Duration metrics a cardio set (minutes only) can describe. */
export const CARDIO_METRICS: readonly string[] = [
    "duration",
    "distance_duration",
];
/** Interval-style moves with a duration metric: not continuous "suave" cardio (E13 Q28). Mirrored in SQL. */
export const CARDIO_EXCLUDED_KEYS: readonly string[] = [
    "jumping-jacks",
    "jump-rope",
    "battle-ropes",
    "mountain-climbers",
];
/** Always available: no machine needed. */
const CARDIO_ALWAYS_KEYS: readonly string[] = ["walking", "running"];
/** Default by selected machine, in this order (low impact, steady pace). */
const CARDIO_MACHINE_DEFAULTS: readonly string[] = [
    "stationary-bike",
    "elliptical-trainer",
    "incline-treadmill-walk",
    "rowing-machine",
];

/** Cardio pattern with a duration metric, minus the interval-style moves. */
export const isCardioEligible = (e: CatalogExercise): boolean =>
    e.movement_pattern === "cardio" && CARDIO_METRICS.includes(e.metric_type) &&
    !CARDIO_EXCLUDED_KEYS.includes(e.key ?? "");

/** walking / running always; otherwise the equipment filter of the lifting pools (gym + none = all). */
export const cardioEquipmentAllowed = (
    prefs: Pick<BlockPrefs, "place" | "equipment">,
    e: CatalogExercise,
): boolean =>
    CARDIO_ALWAYS_KEYS.includes(e.key ?? "") ||
    equipmentAllowed(prefs, e.equipment);

/**
 * The equipment the block is validated against and stored with: an empty list outside a gym means bodyweight only
 * (the swap list in SQL reads it the same way); a gym with none selected stays empty (= all).
 */
export const resolvedEquipment = (
    prefs: Pick<BlockPrefs, "place" | "equipment">,
): string[] =>
    prefs.equipment.length === 0 && prefs.place !== "gym"
        ? ["bodyweight"]
        : [...prefs.equipment];

const byCustomThenKey = (a: CatalogExercise, b: CatalogExercise): number =>
    Number(a.is_custom) - Number(b.is_custom) ||
    (candidateKey(a) < candidateKey(b)
        ? -1
        : candidateKey(a) > candidateKey(b)
        ? 1
        : 0);

/** Every cardio exercise the user may have on the cardio slot, also the swap list (same set as SQL). */
export function cardioOptions(
    catalog: CatalogExercise[],
    prefs: Pick<BlockPrefs, "place" | "equipment">,
): CatalogExercise[] {
    return catalog
        .filter((e) => isCardioEligible(e) && cardioEquipmentAllowed(prefs, e))
        .sort(byCustomThenKey);
}

/**
 * The one candidate of a cardio slot: the most-logged favourite inside the equipment, else by selected machine,
 * else walking. Never by default: running (unless logged), treadmill running, air bike, ski erg, stair climber.
 */
export function cardioDefaultCandidates(
    catalog: CatalogExercise[],
    prefs: Pick<BlockPrefs, "place" | "equipment">,
    favorites: readonly string[] = [],
): CatalogExercise[] {
    const options = cardioOptions(catalog, prefs);
    const find = (key: string) => options.find((e) => candidateKey(e) === key);
    for (const key of [...favorites, ...CARDIO_MACHINE_DEFAULTS, "walking"]) {
        const found = find(key);
        if (found) return [found];
    }
    return [];
}

/** Loaded free-weight and machine lifts come before bodyweight variants, so the cap keeps the useful ones. */
const EQUIPMENT_ORDER = [
    "barbell",
    "ez_bar",
    "dumbbell",
    "machine",
    "cable",
    "kettlebell",
    "bodyweight",
    "band",
    "band_long",
    "cardio_machine",
    "other",
];
const equipmentRank = (e: string): number => {
    const i = EQUIPMENT_ORDER.indexOf(e);
    return i < 0 ? EQUIPMENT_ORDER.length : i;
};

export function filterCandidates(
    catalog: CatalogExercise[],
    prefs: Pick<BlockPrefs, "place" | "equipment">,
    slot: Pick<BlockSlot, "muscle_group" | "role">,
): CatalogExercise[] {
    const pool = slotPool(slot.muscle_group, slot.role);
    return catalog
        .filter((e) =>
            pool.groups.includes(e.muscle_group) &&
            e.movement_pattern != null &&
            pool.patterns.includes(e.movement_pattern) &&
            STRENGTH_METRICS.includes(e.metric_type) &&
            equipmentAllowed(prefs, e.equipment)
        )
        .sort((a, b) =>
            Number(a.is_custom) - Number(b.is_custom) ||
            equipmentRank(a.equipment) - equipmentRank(b.equipment) ||
            (candidateKey(a) < candidateKey(b)
                ? -1
                : candidateKey(a) > candidateKey(b)
                ? 1
                : 0)
        )
        .slice(0, MAX_CANDIDATES_PER_SLOT);
}

/** Candidates of every slot; a slot without any (or short of distinct ones) makes the block infeasible. */
export function buildCandidates(
    catalog: CatalogExercise[],
    prefs:
        & Pick<BlockPrefs, "place" | "equipment">
        & Partial<Pick<BlockPrefs, "cardio_favorites">>,
    skeleton: BlockSkeleton,
): Map<string, CatalogExercise[]> {
    const out = new Map<string, CatalogExercise[]>();
    for (const session of skeleton.sessions) {
        const needed = new Map<string, number>();
        for (const slot of session.slots) {
            // A minimal block keeps the previous main lift when it is still usable.
            const fixed = slot.fixed_exercise_id
                ? catalog.find((e) =>
                    e.id === slot.fixed_exercise_id &&
                    STRENGTH_METRICS.includes(e.metric_type) &&
                    equipmentAllowed(prefs, e.equipment)
                )
                : undefined;
            if (fixed) {
                out.set(slot.slot_id, [fixed]);
                continue;
            }
            const list = filterCandidates(catalog, prefs, slot);
            out.set(slot.slot_id, list);
            const k = `${slot.muscle_group}:${slot.role}`;
            needed.set(k, (needed.get(k) ?? 0) + 1);
            if (list.length < needed.get(k)!) {
                throw new CoachInputError(
                    "coach_plan_infeasible",
                    "No exercise fits a slot of the block",
                );
            }
        }
        if (session.cardio) {
            const list = cardioDefaultCandidates(
                catalog,
                prefs,
                prefs.cardio_favorites,
            );
            if (list.length === 0) {
                throw new CoachInputError(
                    "coach_plan_infeasible",
                    "No cardio exercise fits the block",
                );
            }
            out.set(session.cardio.slot_id, list);
        }
    }
    return out;
}
