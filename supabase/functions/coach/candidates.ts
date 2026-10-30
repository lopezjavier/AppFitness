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

/** Loaded free-weight and machine lifts come before bodyweight variants, so the cap keeps the useful ones. */
const EQUIPMENT_ORDER = [
    "barbell",
    "dumbbell",
    "machine",
    "cable",
    "kettlebell",
    "bodyweight",
    "band",
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
    prefs: Pick<BlockPrefs, "place" | "equipment">,
    skeleton: BlockSkeleton,
): Map<string, CatalogExercise[]> {
    const out = new Map<string, CatalogExercise[]>();
    for (const session of skeleton.sessions) {
        const needed = new Map<string, number>();
        for (const slot of session.slots) {
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
    }
    return out;
}
