// Kind `plan_block`: the server builds the session skeleton and the candidate exercises per slot, the model only
// picks one exercise per slot and writes its reason, and validators enforce every rule (planning/04 section 9).
import { buildSkeleton } from "./blockRules.ts";
import {
    assembleBlock,
    assertBlockInvariants,
    validateBlockText,
} from "./blockValidators.ts";
import { buildCandidates, candidateKey } from "./candidates.ts";
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
    type PreparedPart,
} from "./context.ts";
import { MODEL_IDS } from "./prices.ts";
import {
    BLOCK_OUTPUT_SCHEMA,
    type BlockPrefs,
    type BlockSkeleton,
    CONSTANTS_VERSION,
    type Experience,
    type Intention,
    type ModelBlockPick,
} from "./schemas.ts";

/**
 * Sessions per model call. 7 = one call. If a measured call on the largest case (6-7 days, gym) takes more than
 * about 90 s, set 3: the parts run in parallel, each validated and retried on its own (T-163 plan, decision 3).
 */
export const SESSIONS_PER_CALL = 7;

const INTENTIONS: Intention[] = [
    "strength",
    "muscle",
    "fat_loss",
    "recomposition",
    "maintenance",
];
const EXPERIENCES: Experience[] = ["beginner", "intermediate", "advanced"];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const isRecord = (v: unknown): v is Record<string, unknown> =>
    typeof v === "object" && v !== null && !Array.isArray(v);
const rec = (v: unknown): Record<string, unknown> => isRecord(v) ? v : {};
const intOrNull = (v: unknown): number | null =>
    typeof v === "number" && Number.isInteger(v) ? v : null;

/** `{ "today": "YYYY-MM-DD", "plan_id": "<uuid>" }` and nothing else. */
export function parseInput(input: unknown): { today: string; planId: string } {
    if (
        !isRecord(input) || Object.keys(input).length !== 2 ||
        typeof input.today !== "string" ||
        !/^\d{4}-\d{2}-\d{2}$/.test(input.today) ||
        typeof input.plan_id !== "string" || !UUID.test(input.plan_id)
    ) {
        throw new CoachInputError(
            "INVALID_INPUT",
            "Input must be { today, plan_id }",
        );
    }
    const d = new Date(`${input.today}T00:00:00Z`);
    if (
        Number.isNaN(d.getTime()) ||
        d.toISOString().slice(0, 10) !== input.today
    ) {
        throw new CoachInputError("INVALID_INPUT", "today is not a valid date");
    }
    return { today: input.today, planId: input.plan_id.toLowerCase() };
}

const SYSTEM = [
    "Eres el entrenador de una app de fitness personal. El servidor ya ha diseñado el bloque: sesiones, huecos (slots),",
    "series, repeticiones y descansos. NO cambias ninguna cifra ni añades ejercicios fuera de la lista.",
    "Tu única tarea: para CADA slot elegir exactamente un exercise_key de la lista de candidatos de ESE slot y escribir en",
    "español una razón breve (10 a 300 caracteres) basada en el objetivo y el material del usuario.",
    "No repitas un ejercicio dentro de la misma sesión. Puedes mencionar el rango completo de movimiento solo como nota",
    "de técnica. Nunca aconsejes reducir el cardio ni los pasos. Sin consejos médicos.",
    'Responde solo con JSON: {"picks":[{"slot_id":"...","exercise_key":"...","reason":"..."}]}.',
    "Cada slot_id aparece exactamente una vez.",
].join(" ");

export function buildPrefs(summary: unknown): BlockPrefs {
    const s = rec(summary);
    const tp = rec(s.training_preferences);
    const intention = INTENTIONS.find((i) => i === tp.intention);
    const place = typeof tp.place === "string" ? tp.place : null;
    if (!intention || !place) {
        throw new CoachInputError(
            "coach_missing_data",
            "Training intention or place is missing",
        );
    }
    const experience =
        EXPERIENCES.find((e) => e === rec(rec(s.estimates).experience).value) ??
            "beginner";
    return {
        intention,
        place,
        equipment: Array.isArray(tp.equipment)
            ? tp.equipment.filter((e): e is string => typeof e === "string")
            : [],
        days_per_week: intOrNull(tp.days_per_week),
        session_minutes: intOrNull(tp.session_minutes),
        experience,
    };
}

export function makePrepare(sessionsPerCall = SESSIONS_PER_CALL) {
    return async function prepareBlock(
        input: unknown,
        ctx: PrepareContext,
    ): Promise<Prepared> {
        const { today, planId } = parseInput(input);
        const plan = await ctx.loadPlan(planId);
        if (
            !plan || !["proposed", "accepted"].includes(plan.status) ||
            plan.training_saved
        ) {
            throw new CoachInputError(
                "INVALID_INPUT",
                "Plan not found or not open for a block",
            );
        }
        const summary = await ctx.loadContext(today);
        const prefs = buildPrefs(summary);
        const catalog = await ctx.loadCatalog();
        const skeleton = buildSkeleton(prefs);
        const candidates = buildCandidates(catalog, prefs, skeleton);

        const model = MODEL_IDS.opus;
        let attempts = 0;
        const parts: PreparedPart[] = [];
        for (let i = 0; i < skeleton.sessions.length; i += sessionsPerCall) {
            const indexes = skeleton.sessions.slice(i, i + sessionsPerCall)
                .map((s) => s.session_index);
            const slots = skeleton.sessions.filter((s) =>
                indexes.includes(s.session_index)
            ).reduce((n, s) => n + s.slots.length, 0);
            parts.push({
                request: {
                    model,
                    system: SYSTEM,
                    prompt: buildPrompt(skeleton, prefs, candidates, indexes),
                    maxTokens: Math.min(8000, 400 + 170 * slots),
                    schema: BLOCK_OUTPUT_SCHEMA,
                },
                validate: (text) => {
                    attempts++;
                    return validateBlockText(
                        text,
                        skeleton,
                        candidates,
                        indexes,
                    );
                },
            });
        }

        return {
            parts,
            finalize: async (values, usage) => {
                const picks = (values as ModelBlockPick[][]).flat();
                const block = assembleBlock(skeleton, picks, candidates, prefs);
                assertBlockInvariants(block, prefs, catalog);
                const saved = await ctx.attachBlock({
                    planId,
                    training: block.training,
                    sessions: block.sessions,
                    method: {
                        model,
                        attempts,
                        cost_eur: usage.costEur,
                        constants_version: CONSTANTS_VERSION,
                    },
                    inputSummary: summary,
                });
                return {
                    plan_id: saved.plan_id,
                    training: block.training,
                    sessions: block.sessions,
                };
            },
        };
    };
}

export const prepare = makePrepare();

function buildPrompt(
    skeleton: BlockSkeleton,
    prefs: BlockPrefs,
    candidates: ReturnType<typeof buildCandidates>,
    indexes: number[],
): string {
    return JSON.stringify({
        intention: prefs.intention,
        experience: prefs.experience,
        place: prefs.place,
        equipment: prefs.equipment,
        rep_scheme: skeleton.rep_scheme,
        sessions: skeleton.sessions
            .filter((s) => indexes.includes(s.session_index))
            .map((s) => ({
                session_index: s.session_index,
                name: s.name,
                slots: s.slots.map((slot) => ({
                    slot_id: slot.slot_id,
                    muscle_group: slot.muscle_group,
                    role: slot.role,
                    sets: slot.sets,
                    reps: `${slot.rep_min}-${slot.rep_max}`,
                    rest_seconds: slot.rest_seconds,
                    candidates: candidates.get(slot.slot_id)!.map((e) => ({
                        exercise_key: candidateKey(e),
                        name: e.name,
                        equipment: e.equipment,
                        pattern: e.movement_pattern,
                    })),
                })),
            })),
    });
}
