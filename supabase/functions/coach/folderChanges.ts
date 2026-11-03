// Folder review (T-207, planning/04 section 9.14): the folder as the database returns it, the closed set of typed
// changes the model may propose, and the pure applier of ONE change. The SQL helper coach_apply_change (migration
// 20261228100000_coach_folder_reviews.sql) does the same on the same jsonb; fixtures/folder_changes.json is run
// against both. Pure: no I/O.
import type { CatalogExercise, MuscleGroup } from "./schemas.ts";
import type { CheckerRoutine } from "./routineChecker.ts";

export type FolderSet = {
    reps: number | null;
    set_type: string;
    weight_kg: number | null;
    duration_s: number | null;
};

export type FolderExercise = {
    /** 1-based ordinal in the routine as stored; 0 for an exercise a change added. Changes address this number. */
    position: number;
    exercise_id: string;
    exercise_key: string;
    superset_group: number | null;
    rest_seconds: number;
    rep_min: number | null;
    rep_max: number | null;
    target_rpe: number | null;
    notes: string | null;
    note_kind: string | null;
    sets: FolderSet[];
};

export type FolderRoutine = {
    id: string;
    name: string;
    notes: string | null;
    track_as_routine: boolean;
    exercises: FolderExercise[];
};

/** `coach_folder_for_review` answer. */
export type FolderForReview = {
    folder_id: string;
    name: string;
    routines: FolderRoutine[];
    /** md5 of the routines: changes when the user edits the folder. */
    version: string;
};

export type SetSpec = {
    n: number;
    type: "working" | "drop";
    rep_min: number;
    rep_max: number;
    rpe: number;
    rest_seconds: number;
};

type At = { routine_index: number; position: number };

/** The closed set of typed ops (routine_index and position are 1-based). */
export type ChangeBody =
    | {
        op: "add_exercise";
        routine_index: number;
        exercise_key: string;
        sets: SetSpec[];
    }
    | ({ op: "replace_exercise"; exercise_key: string } & At)
    | ({ op: "remove_exercise" } & At)
    | ({ op: "set_sets"; n: number } & At)
    | ({ op: "set_rep_range"; rep_min: number; rep_max: number } & At)
    | ({ op: "set_rpe"; rpe: number } & At)
    | ({ op: "set_rest"; rest_seconds: number } & At);

export type ChangeOp = ChangeBody["op"];
export const CHANGE_OPS: readonly ChangeOp[] = [
    "add_exercise",
    "replace_exercise",
    "remove_exercise",
    "set_sets",
    "set_rep_range",
    "set_rpe",
    "set_rest",
];

/** A change as stored and sent to the client. */
export type FolderChange = ChangeBody & {
    id: string;
    reason: string;
    /** The code of the first Falta item this change resolves; absent when it fixes none. */
    fixes?: string;
    /** Ids of changes that touch the same target; the client takes only one of each group. */
    conflicts_with?: string[];
};

export type ResolveExercise = (key: string) => { id: string } | null;

const isInt = (v: unknown): v is number =>
    typeof v === "number" && Number.isInteger(v);

/**
 * Applies one change to one routine, or returns null when it cannot apply (target missing, unknown key, value out of
 * the storable range). Mirrors coach_apply_change.
 */
export function applyChange(
    routine: FolderRoutine,
    change: ChangeBody,
    resolve: ResolveExercise,
): FolderRoutine | null {
    const exs = routine.exercises;
    if (change.op === "add_exercise") {
        if (change.sets.length === 0) return null;
        const row = resolve(change.exercise_key);
        if (!row) return null;
        const first = change.sets.find((s) => s.type === "working") ??
            change.sets[0];
        const sets: FolderSet[] = change.sets.flatMap((s) =>
            Array.from({ length: s.n }, () => ({
                reps: s.rep_min,
                set_type: s.type,
                weight_kg: null,
                duration_s: null,
            }))
        );
        const added: FolderExercise = {
            position: 0,
            exercise_id: row.id,
            exercise_key: change.exercise_key,
            superset_group: null,
            rest_seconds: first.rest_seconds,
            rep_min: Math.min(...change.sets.map((s) => s.rep_min)),
            rep_max: Math.max(...change.sets.map((s) => s.rep_max)),
            target_rpe: first.rpe,
            notes: null,
            note_kind: null,
            sets,
        };
        return { ...routine, exercises: [...exs, added] };
    }

    const target = exs.find((e) => e.position === change.position);
    if (!target) return null;
    const swap = (next: FolderExercise): FolderRoutine => ({
        ...routine,
        exercises: exs.map((e) => e.position === change.position ? next : e),
    });

    switch (change.op) {
        case "remove_exercise": {
            let rest = exs.filter((e) => e.position !== change.position);
            const g = target.superset_group;
            if (
                g != null &&
                rest.filter((e) => e.superset_group === g).length === 1
            ) {
                rest = rest.map((e) =>
                    e.superset_group === g ? { ...e, superset_group: null } : e
                );
            }
            return { ...routine, exercises: rest };
        }
        case "replace_exercise": {
            const row = resolve(change.exercise_key);
            if (!row) return null;
            return swap({
                ...target,
                exercise_id: row.id,
                exercise_key: change.exercise_key,
                notes: null,
                note_kind: null,
                sets: target.sets.map((s) => ({ ...s, weight_kg: null })),
            });
        }
        case "set_sets": {
            if (!isInt(change.n) || change.n < 1 || change.n > 10) return null;
            let sets = target.sets;
            if (change.n < sets.length) sets = sets.slice(0, change.n);
            else if (change.n > sets.length) {
                const base = sets.find((s) => s.set_type === "working") ??
                    sets[0];
                if (!base) return null;
                sets = [
                    ...sets,
                    ...Array.from({ length: change.n - sets.length }, () => ({
                        ...base,
                        set_type: "working",
                    })),
                ];
            }
            return swap({ ...target, sets });
        }
        case "set_rep_range": {
            const { rep_min: lo, rep_max: hi } = change;
            if (
                !isInt(lo) || !isInt(hi) || lo > hi || lo < 1 || lo > 50 ||
                hi < 1 || hi > 50
            ) return null;
            return swap({
                ...target,
                rep_min: lo,
                rep_max: hi,
                sets: target.sets.map((s) =>
                    s.reps != null ? { ...s, reps: lo } : s
                ),
            });
        }
        case "set_rpe":
            return typeof change.rpe === "number"
                ? swap({ ...target, target_rpe: change.rpe })
                : null;
        case "set_rest":
            return isInt(change.rest_seconds)
                ? swap({ ...target, rest_seconds: change.rest_seconds })
                : null;
    }
}

/** Applies the changes in order to the routine they address (routine_index, 1-based). Targets that vanished are skipped. */
export function applyChanges(
    routines: FolderRoutine[],
    changes: ChangeBody[],
    resolve: ResolveExercise,
): FolderRoutine[] {
    const out = [...routines];
    for (const c of changes) {
        const i = c.routine_index - 1;
        if (i < 0 || i >= out.length) continue;
        out[i] = applyChange(out[i], c, resolve) ?? out[i];
    }
    return out;
}

/** The (routine, position) a change touches; add_exercise has no existing target. */
export const targetOf = (c: ChangeBody): string | null =>
    c.op === "add_exercise" ? null : `${c.routine_index}:${c.position}`;

const STRUCTURAL: readonly ChangeOp[] = ["replace_exercise", "remove_exercise"];

/** Two changes conflict when they touch the same exercise and either replaces/removes it, or they are the same op. */
export function conflicts(a: ChangeBody, b: ChangeBody): boolean {
    const t = targetOf(a);
    if (t === null || t !== targetOf(b)) return false;
    return STRUCTURAL.includes(a.op) || STRUCTURAL.includes(b.op) ||
        a.op === b.op;
}

const isCardioRow = (e: CatalogExercise | undefined): boolean =>
    e?.movement_pattern === "cardio";

/**
 * The checker's view of the folder. Cardio exercises are outside the lifting rules (the block appends its own cardio),
 * so they are left out. A missing RPE or rep range counts as invalid (0), which the checker reports as a Falta.
 */
export function toCheckerRoutines(
    routines: FolderRoutine[],
    catalogById: Map<string, CatalogExercise>,
): CheckerRoutine[] {
    return routines.map((r) => ({
        name: r.name,
        exercises: r.exercises.flatMap((x) => {
            const row = catalogById.get(x.exercise_id);
            if (!row || isCardioRow(row)) return [];
            return [{
                exercise_key: x.exercise_key,
                muscle_group: row.muscle_group as MuscleGroup,
                secondary_muscles: row.secondary_muscles ?? [],
                pattern: row.movement_pattern,
                equipment: row.equipment,
                superset_group: x.superset_group,
                sets: x.sets.map((s) => ({
                    type: s.set_type as "working",
                    rep_min: x.rep_min ?? s.reps ?? 0,
                    rep_max: x.rep_max ?? s.reps ?? 0,
                    rpe: x.target_rpe ?? 0,
                    rest_seconds: x.rest_seconds,
                })),
            }];
        }),
    }));
}

/** True for an exercise the checker does not judge (cardio). */
export const isCardioExerciseOf = (
    x: FolderExercise,
    catalogById: Map<string, CatalogExercise>,
): boolean => isCardioRow(catalogById.get(x.exercise_id));
