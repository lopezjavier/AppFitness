// Shared builders for the block tests (no tests here, so importing it never re-runs a suite).
import type {
    BlockPrefs,
    CatalogExercise,
    ModelBlock,
    MuscleGroup,
    Split,
} from "./schemas.ts";

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

export const REASON = "Buen ejercicio base para tu objetivo y tu material.";

const UPPER: MuscleGroup[] = [
    "chest",
    "back",
    "shoulders",
    "biceps",
    "triceps",
];
const LOWER: MuscleGroup[] = [
    "quads",
    "hamstrings",
    "glutes",
    "calves",
    "core",
];
const PUSH: MuscleGroup[] = ["chest", "shoulders", "triceps"];
const PULL: MuscleGroup[] = ["back", "biceps"];
const ARMS: MuscleGroup[] = ["biceps", "triceps", "forearms", "calves", "core"];

const sessionsOf = (list: [string, MuscleGroup[]][]): Split => ({
    name: "Fixture",
    sessions: list.map(([name, focus]) => ({ name, focus })),
});

/** A split that the fake model fills inside 60 minutes with 10+ fractional sets per major group. */
export const splitFor = (days: number): Split => {
    switch (days) {
        case 1:
            return sessionsOf([["Cuerpo completo", [...UPPER, ...LOWER]]]);
        case 2:
            return sessionsOf([["Superior", UPPER], ["Inferior", LOWER]]);
        case 3:
            return sessionsOf([
                ["Superior", UPPER],
                ["Inferior", LOWER],
                ["Superior B", UPPER],
            ]);
        case 4:
            return sessionsOf([
                ["Superior A", UPPER],
                ["Inferior A", LOWER],
                ["Superior B", UPPER],
                ["Inferior B", LOWER],
            ]);
        case 6:
            return sessionsOf([
                ["Empuje A", PUSH],
                ["Tirón A", PULL],
                ["Piernas A", LOWER],
                ["Empuje B", PUSH],
                ["Tirón B", PULL],
                ["Piernas B", LOWER],
            ]);
        default:
            return sessionsOf([
                ["Empuje A", PUSH],
                ["Tirón A", PULL],
                ["Piernas A", LOWER],
                ["Empuje B", PUSH],
                ["Tirón B", PULL],
                ["Piernas B", LOWER],
                ["Brazos", ARMS],
            ]);
    }
};

const MAJOR = ["chest", "back", "shoulders", "quads", "hamstrings", "glutes"];
const MAIN_PATTERN_OF: Record<string, string> = {
    chest: "horizontal_push",
    back: "horizontal_pull",
    shoulders: "vertical_push",
    quads: "squat",
    hamstrings: "hinge",
    glutes: "hinge",
};

export type FakeInput = {
    split: Split;
    catalogue: Record<string, Record<string, string[]>>;
    intention?: string;
    previous_blocks?: unknown[];
};

export type FakeSet = {
    n: number;
    type: string;
    rep_min: number;
    rep_max: number;
    rpe: number;
    rest_seconds: number;
};
export type FakeExercise = {
    exercise_key: string;
    sets: FakeSet[];
    reason: string;
    superset_group?: number;
    planned_weight_kg?: number;
    technique_note?: string;
};
export type FakeBlock = {
    sessions: {
        session_index: number;
        exercises: FakeExercise[];
        cardio_reason: string;
    }[];
    split_reason: string;
    changes_vs_last_block: string;
};

export type FakeOptions = {
    mainSets?: number;
    isoSets?: number;
    /** Lets a test break the generated block. */
    mutate?: (block: FakeBlock) => void;
};

const setOf = (n: number, over: Partial<FakeSet>): FakeSet => ({
    n,
    type: "working",
    rep_min: 10,
    rep_max: 15,
    rpe: 8,
    rest_seconds: 90,
    ...over,
});

/**
 * A block that passes the checker for the fixture catalogue: per group of the split one main lift (major groups) then
 * one isolation (the side-delta pattern for shoulders), mains first, 3 + 2 sets, RPE 8.
 */
export const fakeBlock = (input: FakeInput, o: FakeOptions = {}): FakeBlock => {
    const strength = input.intention === "strength";
    const take = (group: string, pattern: string, used: Set<string>) => {
        const key = (input.catalogue[group]?.[pattern] ?? []).find((k) =>
            !used.has(k)
        );
        if (key) used.add(key);
        return key ?? null;
    };
    const block: FakeBlock = {
        sessions: input.split.sessions.map((s, i) => {
            const used = new Set<string>();
            const mains: FakeExercise[] = [];
            const isos: FakeExercise[] = [];
            for (const g of s.focus) {
                const main = MAJOR.includes(g)
                    ? take(g, MAIN_PATTERN_OF[g], used)
                    : null;
                if (main) {
                    mains.push({
                        exercise_key: main,
                        sets: [setOf(o.mainSets ?? 3, {
                            rep_min: strength ? 4 : 6,
                            rep_max: strength ? 6 : 10,
                            rest_seconds: 150,
                        })],
                        reason: REASON,
                    });
                }
                const iso = take(
                    g,
                    g === "shoulders"
                        ? "shoulder_abduction"
                        : g === "core"
                        ? "core"
                        : "isolation",
                    used,
                );
                if (iso) {
                    isos.push({
                        exercise_key: iso,
                        sets: [setOf(o.isoSets ?? 2, {})],
                        reason: REASON,
                    });
                }
            }
            return {
                session_index: i + 1,
                exercises: [...mains, ...isos],
                cardio_reason: REASON,
            };
        }),
        split_reason: REASON,
        changes_vs_last_block: (input.previous_blocks?.length ?? 0) > 0
            ? REASON
            : "",
    };
    o.mutate?.(block);
    return block;
};

/** The model's answer as the validator parses it: every `n` entry expanded into single sets. */
export const toModelBlock = (b: FakeBlock): ModelBlock => ({
    sessions: b.sessions.map((s) => ({
        session_index: s.session_index,
        cardio_reason: s.cardio_reason,
        exercises: s.exercises.map((x) => ({
            exercise_key: x.exercise_key,
            reason: x.reason,
            ...(x.superset_group !== undefined
                ? { superset_group: x.superset_group }
                : {}),
            ...(x.planned_weight_kg !== undefined
                ? { planned_weight_kg: x.planned_weight_kg }
                : {}),
            ...(x.technique_note !== undefined
                ? { technique_note: x.technique_note }
                : {}),
            sets: x.sets.flatMap((g) =>
                Array.from({ length: g.n }, () => ({
                    type: g.type as "working" | "drop",
                    rep_min: g.rep_min,
                    rep_max: g.rep_max,
                    rpe: g.rpe,
                    rest_seconds: g.rest_seconds,
                }))
            ),
        })),
    })),
    split_reason: b.split_reason,
    changes_vs_last_block: b.changes_vs_last_block === ""
        ? null
        : b.changes_vs_last_block,
});
