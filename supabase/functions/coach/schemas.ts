// Types and JSON schema of the nutrition plan (T-161). Shapes are persisted in coach_plans (data_version 1).

export const DATA_VERSION = 1;
/** Version of the constants in nutritionRules.ts / outcomes.ts, stored in the plan's `method`. */
export const CONSTANTS_VERSION = 1;

export type Intention =
    | "strength"
    | "muscle"
    | "fat_loss"
    | "recomposition"
    | "maintenance";
export type Sex = "male" | "female";
export type Experience = "beginner" | "intermediate" | "advanced";
export type Strategy = "deficit" | "maintenance" | "surplus";

export type Nutrition = {
    intention: Intention;
    weight_kg: number;
    expenditure_kcal: number;
    expenditure_source: string;
    /** Effective rate in the direction of the intention (percent of body weight per week); 0 for no-change intentions. */
    rate_pct_week: number;
    /** Expected weight change per week, signed: negative for a loss, positive for a gain. */
    rate_kg_week: number;
    target_rate_pct: number;
    limited_by: "floor" | "goal_range" | null;
    floor_kcal: number;
    target_kcal: number;
    calorie_adjustment: number;
    strategy: Strategy;
    protein_pct: number;
    carbs_pct: number;
    fat_pct: number;
    protein_g: number;
    carbs_g: number;
    fat_g: number;
    protein_g_per_kg: number;
    distribution: { per_meal_g: number; g_per_kg: number; min_meals: number };
    requires_confirmation: boolean;
    unconfirmed: string[];
    edited_by_user: boolean;
    calorie_changes: number;
};

export type OutcomeBasis = Record<string, number | string | null>;

export type Outcome = {
    kind: "weight" | "e1rm";
    key: string;
    target_value: number;
    unit: "kg";
    reason: string;
    deadline: string;
    basis: OutcomeBasis;
};

export type Method = {
    model: string;
    attempts: number;
    cost_eur: number;
    constants_version: number;
};

/** What the model returns: only a choice among server-built candidates, a reason and a deadline. */
export type ModelOutcome = {
    candidate_id: string;
    reason: string;
    deadline: string;
};

export const MODEL_OUTPUT_SCHEMA: Record<string, unknown> = {
    type: "object",
    additionalProperties: false,
    required: ["outcomes"],
    properties: {
        outcomes: {
            type: "array",
            items: {
                type: "object",
                additionalProperties: false,
                required: ["candidate_id", "reason", "deadline"],
                properties: {
                    candidate_id: { type: "string" },
                    reason: { type: "string" },
                    deadline: { type: "string" },
                },
            },
        },
    },
};

// --- Training block (T-163) ---------------------------------------------------------------------------------

export type MuscleGroup =
    | "chest"
    | "back"
    | "shoulders"
    | "biceps"
    | "triceps"
    | "forearms"
    | "quads"
    | "hamstrings"
    | "glutes"
    | "calves"
    | "core"
    | "full_body";

export type MovementPattern =
    | "squat"
    | "hinge"
    | "lunge"
    | "horizontal_push"
    | "vertical_push"
    | "horizontal_pull"
    | "vertical_pull"
    | "carry"
    | "core"
    | "isolation"
    | "cardio"
    | "other";

export type Role = "main" | "isolation";

/** A row of `exercises` as the coach function reads it (caller's own token: catalogue + own custom exercises). */
export type CatalogExercise = {
    id: string;
    key: string | null;
    name: string | null;
    muscle_group: MuscleGroup;
    equipment: string;
    movement_pattern: MovementPattern | null;
    metric_type: string;
    is_custom: boolean;
    /** Present when read, but a per-hand weight does not matter for a routine. */
    per_hand?: boolean;
};

/** The training preferences the block is built from. */
export type BlockPrefs = {
    intention: Intention;
    place: string | null;
    equipment: string[];
    days_per_week: number | null;
    session_minutes: number | null;
    experience: Experience;
};

export type BlockSlot = {
    slot_id: string;
    muscle_group: MuscleGroup;
    role: Role;
    sets: number;
    rep_min: number;
    rep_max: number;
    rest_seconds: number;
};

export type SkeletonSession = {
    session_index: number;
    name: string;
    focus: MuscleGroup[];
    slots: BlockSlot[];
};

export type BlockSkeleton = {
    days_per_week: number;
    days_chosen: boolean;
    session_minutes: number;
    rep_scheme: "single" | "undulating";
    block_weeks: number;
    sessions: SkeletonSession[];
};

/** What the model returns per slot: a candidate key and a reason. */
export type ModelBlockPick = {
    slot_id: string;
    exercise_key: string;
    reason: string;
};

export type BlockExercise = {
    exercise_id: string;
    exercise_key: string;
    role: Role;
    muscle_group: MuscleGroup;
    sets: number;
    rep_min: number;
    rep_max: number;
    rest_seconds: number;
    reason: string;
};

export type BlockSession = {
    session_index: number;
    name: string;
    focus: MuscleGroup[];
    exercises: BlockExercise[];
};

export type BlockTraining = {
    days_per_week: number;
    days_chosen: boolean;
    session_minutes: number;
    rep_scheme: "single" | "undulating";
    block_weeks: number;
    equipment: string[];
    weekly_sets: Record<string, number>;
    sessions_per_muscle: Record<string, number>;
};

export type Block = {
    training: BlockTraining;
    sessions: BlockSession[];
};

export const BLOCK_OUTPUT_SCHEMA: Record<string, unknown> = {
    type: "object",
    additionalProperties: false,
    required: ["picks"],
    properties: {
        picks: {
            type: "array",
            items: {
                type: "object",
                additionalProperties: false,
                required: ["slot_id", "exercise_key", "reason"],
                properties: {
                    slot_id: { type: "string" },
                    exercise_key: { type: "string" },
                    reason: { type: "string" },
                },
            },
        },
    },
};
