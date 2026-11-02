// Shared builders for the block tests (no tests here, so importing it never re-runs a suite).
import type { BlockPrefs, CatalogExercise } from "./schemas.ts";

export const prefs = (over: Partial<BlockPrefs> = {}): BlockPrefs => ({
    intention: "muscle",
    place: "gym",
    equipment: [],
    days_per_week: 3,
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

/** A small catalogue with three main and three isolation exercises per major group, isolation-only for the rest. */
export const fullCatalog = (): CatalogExercise[] => {
    const out: CatalogExercise[] = [];
    for (const g of Object.keys(MAIN_PATTERN)) {
        for (let i = 0; i < 3; i++) {
            out.push(ex({
                muscle_group: g as CatalogExercise["muscle_group"],
                movement_pattern: MAIN_PATTERN[g],
            }));
            out.push(ex({
                muscle_group: g as CatalogExercise["muscle_group"],
                movement_pattern: "isolation",
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
