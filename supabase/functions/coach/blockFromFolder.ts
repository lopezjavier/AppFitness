// `plan_block` with `start: "folder"` (T-207, planning/04 section 9.14, US-93 "Desde mi carpeta"): the block is the
// user's own folder with the changes they accepted in a stored review. No model call: the server re-applies the
// accepted changes, runs the one routine checker, appends the cardio and attaches the block. A hard failure refuses the
// block with `coach_block_needs_changes` and stores nothing. The folder itself is never written.
import { BLOCK_WEEKS, cardioTargetFor, chooseFrequency } from "./blockRules.ts";
import {
    assembleBlock,
    assertBlockInvariants,
    mentionsCardioReduction,
} from "./blockValidators.ts";
import { buildEligibleCatalogue, candidateKey } from "./candidates.ts";
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
    type StoredReview,
} from "./context.ts";
import {
    applyChanges,
    type ChangeBody,
    type FolderChange,
    type FolderRoutine,
    isCardioExerciseOf,
    type ResolveExercise,
    toCheckerRoutines,
} from "./folderChanges.ts";
import {
    checkerInputFor,
    type Falta,
    faltaOf,
    inputHash,
    type ReviewContext,
} from "./folderReview.ts";
import { assertInputsComplete } from "./inputs.ts";
import { firstPhase } from "./nutritionRules.ts";
import {
    buildPrefs,
    cardioSlots,
    loadOpenPlan,
    normalisePhase,
    prepare as prepareScratch,
    rawModelInput,
    rec,
    UUID,
} from "./planBlock.ts";
import { type CheckerFailure, checkRoutines } from "./routineChecker.ts";
import {
    type CatalogExercise,
    CONSTANTS_VERSION,
    type ModelBlock,
    type ModelExercise,
    type MuscleGroup,
    type Phase,
} from "./schemas.ts";
import { isRecord, REASON_MAX, REASON_MIN, sameKeys } from "./validators.ts";

/** Exercise reasons of a block are at most this long (the prompt of the model-built block asks the same). */
const EXERCISE_REASON_MAX = 160;
const CARDIO_REASON =
    "Cardio suave para llegar a tu objetivo semanal de cardio.";
const NO_CHANGES = "Sin cambios respecto a tu carpeta.";
const NAME_MAX = 100;
const MAX_ACCEPTED = 60;

/** True for the `plan_block` request that starts from a folder; the handler skips the provider and the budget for it. */
export const isFolderStart = (input: unknown): boolean =>
    isRecord(input) && input.start === "folder";

/** The scratch request builds the block with the model; the folder start with no model at all. */
export function prepareBlock(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    return isFolderStart(input)
        ? prepareFromFolder(input, ctx)
        : prepareScratch(input, ctx);
}

/** `{ today, plan_id, start: "folder", review_id, accepted_change_ids }` and nothing else. */
export function parseFolderStartInput(input: unknown): {
    today: string;
    planId: string;
    reviewId: string;
    accepted: string[];
} {
    const bad = (message: string): never => {
        throw new CoachInputError("INVALID_INPUT", message);
    };
    if (
        !isRecord(input) ||
        !sameKeys(input, [
            "today",
            "plan_id",
            "start",
            "review_id",
            "accepted_change_ids",
        ]) || input.start !== "folder" || typeof input.today !== "string" ||
        !/^\d{4}-\d{2}-\d{2}$/.test(input.today) ||
        typeof input.plan_id !== "string" || !UUID.test(input.plan_id) ||
        typeof input.review_id !== "string" || !UUID.test(input.review_id) ||
        !Array.isArray(input.accepted_change_ids) ||
        input.accepted_change_ids.length > MAX_ACCEPTED ||
        input.accepted_change_ids.some((x) =>
            typeof x !== "string" || !/^c\d{1,3}$/.test(x)
        )
    ) {
        bad(
            "Input must be { today, plan_id, start: 'folder', review_id, accepted_change_ids }",
        );
    }
    const i = input as Record<string, unknown>;
    const d = new Date(`${i.today}T00:00:00Z`);
    if (
        Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== i.today
    ) bad("today is not a valid date");
    return {
        today: i.today as string,
        planId: (i.plan_id as string).toLowerCase(),
        reviewId: (i.review_id as string).toLowerCase(),
        accepted: [...new Set(i.accepted_change_ids as string[])],
    };
}

const inRange = (s: string, min: number, max: number): boolean => {
    const t = s.trim();
    return t.length >= min && t.length <= max && !mentionsCardioReduction(t);
};

/** The failures the client shows: the checker's plus the structure the block needs. */
function structureFailures(
    routines: FolderRoutine[],
    catalogById: Map<string, CatalogExercise>,
    eligibleKeys: Set<string>,
): Falta[] {
    const out: Falta[] = [];
    routines.forEach((r, i) => {
        const lifting = r.exercises.filter((x) =>
            !isCardioExerciseOf(x, catalogById)
        );
        if (lifting.length === 0) {
            out.push({ code: "empty_routine", routine_index: i + 1, data: {} });
        }
        const seen = new Set<string>();
        for (const x of lifting) {
            if (seen.has(x.exercise_key)) {
                out.push({
                    code: "duplicate_exercise",
                    routine_index: i + 1,
                    exercise_key: x.exercise_key,
                    data: {},
                });
            }
            seen.add(x.exercise_key);
            if (!eligibleKeys.has(x.exercise_key)) {
                out.push({
                    code: "exercise_not_available",
                    routine_index: i + 1,
                    exercise_key: x.exercise_key,
                    data: {},
                });
            }
        }
    });
    return out;
}

export async function prepareFromFolder(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    const { today, planId, reviewId, accepted } = parseFolderStartInput(input);
    const plan = await loadOpenPlan(ctx, planId);
    const review: StoredReview | null = await ctx.loadReview(reviewId);
    if (!review || review.purpose !== "block" || review.plan_id !== planId) {
        throw new CoachInputError("INVALID_INPUT", "Review not found");
    }
    const folder = await ctx.loadFolder(review.folder_id);
    if (!folder) {
        throw new CoachInputError("INVALID_INPUT", "Folder not found");
    }
    if (review.result.folder_version !== folder.version) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "The folder changed since the review",
        );
    }

    const summary = await ctx.loadContext(today);
    assertInputsComplete(await ctx.loadInputs("block", today));
    const catalog = await ctx.loadCatalog();
    const base = buildPrefs(summary);
    const freq = chooseFrequency(base);
    if (
        review.input_hash !== await inputHash(
            folder.version,
            freq.days_per_week,
            freq.session_minutes,
            base.intention,
        )
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Your preferences changed since the review",
        );
    }
    const phase: Phase = normalisePhase(plan.nutrition?.phase) ??
        normalisePhase(plan.phase) ?? firstPhase(base.intention).phase;
    const cardioMinWeek = cardioTargetFor(
        phase,
        plan.nutrition?.activity?.cardio_edited === true,
        plan.cardio_min_week,
    );
    const prefs = buildPrefs(summary, cardioMinWeek, { phase });
    const days = freq.days_per_week;

    const modelInput = rawModelInput(await ctx.loadBlockModelInput(today));
    const excludedIds = modelInput.unresolved_exercise_ids;
    const eligible = buildEligibleCatalogue(catalog, prefs, excludedIds);
    const byId = new Map(catalog.map((e) => [e.id, e]));
    const byKey = new Map(catalog.map((e) => [candidateKey(e), e]));
    const excludedKeys = excludedIds
        .map((id) => byId.get(id))
        .filter((e): e is CatalogExercise => e !== undefined)
        .map(candidateKey);
    const resolve: ResolveExercise = (key) => {
        const row = byKey.get(key);
        return row ? { id: row.id } : null;
    };

    // The accepted changes, from the stored review only (a client never sends a change body).
    const stored =
        (Array.isArray(review.result.changes)
            ? review.result.changes
            : []) as FolderChange[];
    const known = new Set(stored.map((c) => c.id));
    if (accepted.some((id) => !known.has(id))) {
        throw new CoachInputError("INVALID_INPUT", "Unknown change id");
    }
    const chosen = stored.filter((c) => accepted.includes(c.id));
    if (
        chosen.some((c) =>
            (c.conflicts_with ?? []).some((o) => accepted.includes(o))
        )
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Two accepted changes touch the same exercise",
        );
    }
    const routines = applyChanges(
        folder.routines,
        chosen as ChangeBody[],
        resolve,
    );

    // The one checker over what the block would be.
    const rc: ReviewContext = {
        prefs,
        days,
        minutes: freq.session_minutes,
        eligible,
        excludedKeys,
    };
    const checked = checkRoutines(
        checkerInputFor(toCheckerRoutines(routines, byId), rc),
    );
    const failures: Falta[] = [
        ...faltaOf(checked.failures as CheckerFailure[]),
        ...structureFailures(
            routines,
            byId,
            new Set(eligible.exercises.map(candidateKey)),
        ),
    ];
    if (failures.length > 0) {
        throw new CoachInputError(
            "coach_block_needs_changes",
            "The folder does not meet the rules yet",
            undefined,
            { failures },
        );
    }

    // Reasons: the user's own note when it fits, else the review comment of the exercise, else a fixed line.
    const comments = new Map<string, string>();
    for (
        const c of Array.isArray(review.result.comments)
            ? review.result.comments
            : []
    ) {
        const o = rec(c);
        if (typeof o.exercise_key === "string" && typeof o.text === "string") {
            comments.set(o.exercise_key, o.text);
        }
    }
    const reasonOf = (key: string, note: string | null): string => {
        if (note && inRange(note, REASON_MIN, EXERCISE_REASON_MAX)) {
            return note.trim();
        }
        const comment = comments.get(key);
        if (comment && inRange(comment, REASON_MIN, EXERCISE_REASON_MAX)) {
            return comment.trim();
        }
        return `Tu ejercicio de la carpeta «${folder.name.slice(0, 100)}»`;
    };

    const checkerRoutines = toCheckerRoutines(routines, byId);
    const lifting = routines.map((r) =>
        r.exercises.filter((x) => !isCardioExerciseOf(x, byId))
    );
    const model: ModelBlock = {
        sessions: checkerRoutines.map((cr, i) => ({
            session_index: i + 1,
            cardio_reason: CARDIO_REASON,
            exercises: cr.exercises.map((cx, xi): ModelExercise => {
                const source = lifting[i][xi];
                const weight = source.sets.find((s) =>
                    s.set_type === "working" && (s.weight_kg ?? 0) > 0
                )?.weight_kg;
                return {
                    exercise_key: cx.exercise_key,
                    sets: cx.sets.map((s) => ({
                        type: s.type === "drop" ? "drop" : "working",
                        rep_min: s.rep_min,
                        rep_max: s.rep_max,
                        rpe: s.rpe,
                        rest_seconds: s.rest_seconds,
                    })),
                    ...(cx.superset_group != null
                        ? { superset_group: cx.superset_group }
                        : {}),
                    ...(weight != null && weight <= 1000
                        ? { planned_weight_kg: weight }
                        : {}),
                    reason: reasonOf(cx.exercise_key, source.notes),
                };
            }),
        })),
        split_reason: `Tus rutinas de «${
            folder.name.slice(0, 100)
        }» con los cambios aceptados.`,
        changes_vs_last_block: chosen.length > 0
            ? chosen.map((c) => c.reason.trim().replace(/[.\s]+$/, "")).join(
                ". ",
            ) + "."
            : NO_CHANGES,
    };
    if (!inRange(model.split_reason, REASON_MIN, REASON_MAX)) {
        model.split_reason = "Tus rutinas con los cambios aceptados.";
    }
    if (
        !inRange(model.changes_vs_last_block ?? "", REASON_MIN, REASON_MAX)
    ) model.changes_vs_last_block = NO_CHANGES;

    const cardioPlan = cardioSlots(catalog, prefs, days);
    const split = {
        name: folder.name.slice(0, NAME_MAX),
        sessions: checkerRoutines.map((cr) => ({
            name: cr.name.slice(0, NAME_MAX),
            focus: focusOf(cr.exercises.map((x) => x.muscle_group)),
        })),
    };
    const block = assembleBlock({
        model,
        split,
        eligible,
        prefs,
        days,
        sessionMinutes: freq.session_minutes,
        daysChosen: freq.days_chosen,
        phase,
        blockWeeks: BLOCK_WEEKS,
        cardio: cardioPlan.split,
        cardioExercise: cardioPlan.exercise,
    });
    assertBlockInvariants(block, prefs, catalog);
    const training = {
        ...block.training,
        split_reason: model.split_reason,
        changes_vs_last_block: model.changes_vs_last_block,
        notes: checked.notes.map((n) => n.code),
    };

    return {
        finalize: async () => {
            const saved = await ctx.attachBlock({
                planId,
                training,
                sessions: block.sessions,
                method: {
                    model: "folder",
                    attempts: 0,
                    cost_eur: 0,
                    constants_version: CONSTANTS_VERSION,
                },
                inputSummary: summary,
            });
            return {
                plan_id: saved.plan_id,
                training,
                sessions: block.sessions,
                split_reason: training.split_reason,
                changes_vs_last_block: training.changes_vs_last_block,
                notes: training.notes,
            };
        },
    };
}

/** The distinct primary muscle groups of a routine, as the split session focus (full_body when it has none). */
function focusOf(groups: MuscleGroup[]): MuscleGroup[] {
    const unique = [...new Set(groups)].filter((g) => g !== "full_body");
    return unique.length > 0 ? unique : ["full_body"];
}
