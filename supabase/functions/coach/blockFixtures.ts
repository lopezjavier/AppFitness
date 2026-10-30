// Shared builders for the block tests (no tests here, so importing it never re-runs a suite).
import type { BlockPrefs, CatalogExercise } from "./schemas.ts";

export const prefs = (over: Partial<BlockPrefs> = {}): BlockPrefs => ({
    intention: "muscle",
    place: "gym",
    equipment: [],
    days_per_week: 3,
    session_minutes: 60,
    experience: "intermediate",
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
    return out;
};
