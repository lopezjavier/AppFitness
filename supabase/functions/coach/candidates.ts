// Exercises the block may use (planning/04 section 9.4): the eligible catalogue the model reads, unranked and
// uncapped, and the cardio options. Pure: no I/O.
import { CoachInputError } from "./context.ts";
import type { BlockPrefs, CatalogExercise, MuscleGroup } from "./schemas.ts";

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

/** Loaded free-weight and machine lifts come before bodyweight variants (the server-side swap list keeps this order). */
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
export const equipmentRank = (e: string): number => {
    const i = EQUIPMENT_ORDER.indexOf(e);
    return i < 0 ? EQUIPMENT_ORDER.length : i;
};

/** Pattern of a row the block can use for lifting: cardio and rows without a pattern are out. */
const isLiftingRow = (e: CatalogExercise): boolean =>
    e.movement_pattern != null && e.movement_pattern !== "cardio" &&
    STRENGTH_METRICS.includes(e.metric_type);

export type EligibleCatalogue = {
    /** Every eligible row (equipment, discomfort exclusions and metric applied), sorted by key. */
    exercises: CatalogExercise[];
    /** What the model reads: `{group: {pattern: [keys]}}`, keys sorted, nothing ranked or cut. */
    byGroup: Record<string, Record<string, string[]>>;
};

/**
 * The exercises the model may put in the block (07 section 3 "Model input"): inside the user's equipment, minus the
 * exercises with an unresolved level 4-5 discomfort. Unranked and uncapped: the checker is the gate, not a cap.
 */
export function buildEligibleCatalogue(
    catalog: CatalogExercise[],
    prefs: Pick<BlockPrefs, "place" | "equipment">,
    excludedIds: readonly string[] = [],
): EligibleCatalogue {
    const exercises = catalog
        .filter((e) =>
            isLiftingRow(e) && equipmentAllowed(prefs, e.equipment) &&
            !excludedIds.includes(e.id)
        )
        .sort((a, b) =>
            candidateKey(a) < candidateKey(b)
                ? -1
                : candidateKey(a) > candidateKey(b)
                ? 1
                : 0
        );
    if (exercises.length === 0) {
        throw new CoachInputError(
            "coach_plan_infeasible",
            "No exercise fits the block",
        );
    }
    const byGroup: Record<string, Record<string, string[]>> = {};
    for (const e of exercises) {
        const g: MuscleGroup = e.muscle_group;
        const patterns = byGroup[g] ??= {};
        (patterns[e.movement_pattern!] ??= []).push(candidateKey(e));
    }
    return { exercises, byGroup };
}
