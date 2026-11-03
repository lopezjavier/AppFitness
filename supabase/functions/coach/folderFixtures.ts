// Builders for the folder review tests (no tests here, so importing it never re-runs a suite).
import { buildEligibleCatalogue, candidateKey } from "./candidates.ts";
import { type FakeBlock, fakeBlock, prefs, splitFor } from "./blockFixtures.ts";
import type { StoredReview } from "./context.ts";
import type { FolderForReview, FolderRoutine } from "./folderChanges.ts";
import { inputHash } from "./folderReview.ts";
import type { CatalogExercise, Split } from "./schemas.ts";

export const FOLDER_ID = "33333333-3333-4333-8333-333333333333";
export const FOLDER_VERSION = "version-1";

export type FolderOptions = {
    days?: number;
    mutate?: (block: FakeBlock, byKey: Map<string, CatalogExercise>) => void;
    intention?: string;
    /** Sets of every main lift and of every isolation exercise (the fixture block defaults to 3 and 2). */
    mainSets?: number;
    isoSets?: number;
    /** Replaces the fixture split of `days`. */
    split?: Split;
};

/** A folder that passes the checker for `catalog` (the model-built fixture block, saved as routines). */
export function goodFolder(
    catalog: CatalogExercise[],
    o: FolderOptions = {},
): FolderForReview {
    const days = o.days ?? 4;
    const split = o.split ?? splitFor(days);
    const catalogue = buildEligibleCatalogue(catalog, prefs()).byGroup;
    const byKey = new Map(catalog.map((e) => [candidateKey(e), e]));
    const block = fakeBlock({ split, catalogue, intention: o.intention }, {
        mainSets: o.mainSets,
        isoSets: o.isoSets,
        mutate: (b) => o.mutate?.(b, byKey),
    });
    const routines: FolderRoutine[] = block.sessions.map((s, i) => ({
        id: `00000000-0000-4000-8000-0000000f${String(i).padStart(4, "0")}`,
        name: split.sessions[i].name,
        notes: null,
        track_as_routine: true,
        exercises: s.exercises.map((x, xi) => ({
            position: xi + 1,
            exercise_id: byKey.get(x.exercise_key)!.id,
            exercise_key: x.exercise_key,
            superset_group: x.superset_group ?? null,
            rest_seconds: x.sets[0].rest_seconds,
            rep_min: Math.min(...x.sets.map((g) => g.rep_min)),
            rep_max: Math.max(...x.sets.map((g) => g.rep_max)),
            target_rpe: x.sets[0].rpe,
            notes: null,
            note_kind: null,
            sets: x.sets.flatMap((g) =>
                Array.from({ length: g.n }, () => ({
                    reps: g.rep_min,
                    set_type: g.type,
                    weight_kg: null,
                    duration_s: null,
                }))
            ),
        })),
    }));
    return {
        folder_id: FOLDER_ID,
        name: "Mi carpeta",
        routines,
        version: FOLDER_VERSION,
    };
}

/** The keys of the first exercise with this pattern in the catalogue (the model-built fixture has no names). */
export const keyOfPattern = (
    catalog: CatalogExercise[],
    group: string,
    pattern: string,
    skip = 0,
): string => {
    const row =
        catalog.filter((e) =>
            e.muscle_group === group && e.movement_pattern === pattern
        )[skip];
    return candidateKey(row);
};

/** A stored review of purpose `block` for the folder, with the hash the function computes for the same inputs. */
export async function storedReview(
    o: {
        planId: string;
        reviewId: string;
        changes?: unknown[];
        comments?: unknown[];
        version?: string;
        days?: number;
        minutes?: number;
        intention?: string;
        purpose?: string;
    },
): Promise<StoredReview> {
    return {
        id: o.reviewId,
        folder_id: FOLDER_ID,
        purpose: o.purpose ?? "block",
        plan_id: o.planId,
        input_hash: await inputHash(
            o.version ?? FOLDER_VERSION,
            o.days ?? 4,
            o.minutes ?? 60,
            o.intention ?? "muscle",
        ),
        result: {
            message: "Revision",
            falta: [],
            notes: [],
            comments: o.comments ?? [],
            changes: o.changes ?? [],
            folder_version: o.version ?? FOLDER_VERSION,
        },
    };
}
