// Shared builders for the candidates and cardio tests (no tests here, so importing it never re-runs a suite).
import type { BlockPrefs, CatalogExercise, MuscleGroup } from "./schemas.ts";

const KNEE_EXTENSION_KEYS = [
    "leg-extension",
    "single-leg-extension",
    "banded-terminal-knee-extension",
    "reverse-nordic-curl",
];

export const prefs = (over: Partial<BlockPrefs> = {}): BlockPrefs => ({
    intention: "muscle",
    place: "gym",
    equipment: [],
    days_per_week: 4,
    session_minutes: 60,
    experience: "intermediate",
    cardio_min_week: 150,
    cardio_favorites: [],
    ...over,
});

let n = 0;
export const ex = (over: Partial<CatalogExercise> = {}): CatalogExercise => {
    n++;
    return {
        id: `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`,
        key: `ex-${n}`,
        name: null,
        muscle_group: "chest",
        equipment: "barbell",
        movement_pattern: "horizontal_push",
        metric_type: "weight_reps",
        is_custom: false,
        ...over,
    };
};

const MAIN_PATTERN: Record<string, CatalogExercise["movement_pattern"]> = {
    chest: "horizontal_push",
    back: "horizontal_pull",
    shoulders: "vertical_push",
    quads: "squat",
    hamstrings: "hinge",
    glutes: "hinge",
};

/** A cardio catalogue row (pattern cardio, duration metric). */
export const cardio = (
    key: string,
    equipment: string,
    over: Partial<CatalogExercise> = {},
): CatalogExercise =>
    ex({
        key,
        equipment,
        muscle_group: "full_body",
        movement_pattern: "cardio",
        metric_type: key === "walking" || key === "running"
            ? "distance_duration"
            : "duration",
        ...over,
    });

/** The cardio rows of the real catalogue that matter to the coach (keys and equipment as in the seed). */
export const cardioCatalog = (): CatalogExercise[] => [
    cardio("walking", "bodyweight"),
    cardio("running", "bodyweight"),
    cardio("stationary-bike", "cardio_machine"),
    cardio("air-bike", "cardio_machine"),
    cardio("incline-treadmill-walk", "cardio_machine"),
    cardio("treadmill-running", "cardio_machine"),
    cardio("elliptical-trainer", "cardio_machine"),
    cardio("rowing-machine", "cardio_machine"),
    cardio("ski-erg", "cardio_machine"),
    cardio("stair-climber", "cardio_machine", {
        metric_type: "floors_duration",
    }),
    cardio("jumping-jacks", "bodyweight"),
    cardio("jump-rope", "bodyweight"),
    cardio("battle-ropes", "other"),
    cardio("mountain-climbers", "bodyweight"),
    cardio("burpees", "bodyweight", { metric_type: "reps_only" }),
];

/** Secondary muscles of the main lifts of the small catalogue (04 section 4; hinges list hamstrings). */
const MAIN_SECONDARY: Record<string, MuscleGroup[]> = {
    chest: ["triceps", "shoulders"],
    back: ["biceps"],
    shoulders: ["triceps"],
    quads: ["glutes"],
    hamstrings: ["glutes"],
    glutes: ["hamstrings"],
};

/**
 * A small catalogue: three main and three isolation exercises per major group (the shoulder isolations are
 * `shoulder_abduction`), isolation-only for the other groups, core, and the cardio rows.
 */
export const fullCatalog = (): CatalogExercise[] => {
    const out: CatalogExercise[] = [];
    for (const g of Object.keys(MAIN_PATTERN)) {
        for (let i = 0; i < 3; i++) {
            out.push(ex({
                muscle_group: g as CatalogExercise["muscle_group"],
                movement_pattern: MAIN_PATTERN[g],
                secondary_muscles: MAIN_SECONDARY[g],
            }));
            out.push(ex({
                muscle_group: g as CatalogExercise["muscle_group"],
                movement_pattern: g === "shoulders"
                    ? "shoulder_abduction"
                    : "isolation",
                secondary_muscles: [],
                // The quads isolations are real knee extensions: H3b counts them by key (07 section 3).
                ...(g === "quads" ? { key: KNEE_EXTENSION_KEYS[i] } : {}),
            }));
        }
    }
    for (const g of ["biceps", "triceps", "forearms", "calves"] as const) {
        for (let i = 0; i < 3; i++) {
            out.push(ex({ muscle_group: g, movement_pattern: "isolation" }));
        }
    }
    for (let i = 0; i < 3; i++) {
        out.push(ex({ muscle_group: "core", movement_pattern: "core" }));
    }
    return [...out, ...cardioCatalog()];
};
