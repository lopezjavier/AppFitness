// Types and JSON schema of the nutrition plan (T-161). Shapes are persisted in coach_plans (data_version 1).
import type { Activity } from "./cardioRules.ts";

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

/** The phase of a block (planning/07 section 12.3). One phase per block. */
export type Phase =
    | "cut"
    | "gain"
    | "maintenance"
    | "recomposition";
/** Why the plan is in that phase; the Spanish text lives in the client. */
export type PhaseReason =
    | "intention"
    | "underweight";
export type PhaseOptions = { cut_rate_pct: number; diet_break: boolean };

/** The maintenance week (week 4) of a cut with a diet break. */
export type DietBreakWeek = {
    target_kcal: number;
    calorie_adjustment: number;
    strategy: Strategy;
    protein_pct: number;
    carbs_pct: number;
    fat_pct: number;
    protein_g: number;
    carbs_g: number;
    fat_g: number;
    week: 4;
};

export type Nutrition = {
    intention: Intention;
    phase: Phase;
    phase_reason: PhaseReason;
    phase_options: PhaseOptions;
    /** Null unless the plan is a cut with the diet break on. */
    diet_break: DietBreakWeek | null;
    weight_kg: number;
    expenditure_kcal: number;
    expenditure_source: string;
    /** Capped 28-day intake the expenditure is anchored on (04 section 7.3); null on the formula path and in old plans. */
    intake_anchor_kcal: number | null;
    /** Whether the weight slope moved the expenditure away from the anchor (04 section 7.2). */
    slope_used: boolean;
    /** expenditure_kcal - intake_anchor_kcal after the plausibility clamp; 0 without a slope. */
    slope_term_kcal: number;
    /** Effective rate in the direction of the intention (percent of body weight per week); 0 for no-change intentions. */
    rate_pct_week: number;
    /** Expected weight change per week, signed: negative for a loss, positive for a gain. */
    rate_kg_week: number;
    target_rate_pct: number;
    limited_by: "floor" | "goal_range" | "deficit_cap" | null;
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
    /** Cardio and steps targets (T-181); absent on plans stored before it. */
    activity?: Activity;
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
    /** Null for a model-free plan (plan_nutrition, ADR-98). */
    model: string | null;
    attempts: number;
    cost_eur: number;
    constants_version: number;
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
    | "adductors"
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
    | "shoulder_abduction"
    | "cardio"
    | "other";

/** A row of `exercises` as the coach function reads it (caller's own token: catalogue + own custom exercises). */
export type CatalogExercise = {
    id: string;
    key: string | null;
    name: string | null;
    muscle_group: MuscleGroup;
    /** Secondary muscles (fractional sets, planning/04 section 4); absent in older fixtures. */
    secondary_muscles?: MuscleGroup[];
    equipment: string;
    movement_pattern: MovementPattern | null;
    metric_type: string;
    is_custom: boolean;
    /** False for stretches, mobility, cardio, impact and timed holds (07 section 3 "What H1 counts"); absent = true. */
    counts_as_strength?: boolean;
    /** Present when read, but a per-hand weight does not matter for a routine. */
    per_hand?: boolean;
};

/** Phase history of the caller (rpc coach_phase_history): deficit weeks of the current run of cut blocks. */
export type PhaseHistory = {
    previous: unknown | null;
    /** Deficit weeks of the current run of cut blocks (4 per block, 3 with a diet break). */
    deficit_weeks_done: number;
};

/** The training preferences the block is built from. */
export type BlockPrefs = {
    intention: Intention;
    place: string | null;
    equipment: string[];
    days_per_week: number | null;
    session_minutes: number | null;
    experience: Experience;
    /** Weekly cardio target of the plan (minutes); 0 means no cardio in the block. */
    cardio_min_week: number;
    /** Most-logged cardio exercises of the last 8 weeks (key or id), most logged first. */
    cardio_favorites: string[];
    /** The phase of the block (T-187); absent means the first phase of the intention. */
    phase?: Phase;
    /** Age in whole years (profile); the "Mantener" floor of the checker rises from 65. */
    age?: number | null;
};
