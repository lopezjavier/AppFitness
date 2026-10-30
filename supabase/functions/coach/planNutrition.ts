// Kind `plan_nutrition`: deterministic calories and macros, plus a model call that only chooses outcomes among
// server-built candidates and writes their reasons and deadlines (planning/04 section 8).
import {
    CoachInputError,
    type PrepareContext,
    type Prepared,
} from "./context.ts";
import { CoachPlanError, computeNutrition } from "./nutritionRules.ts";
import {
    addDays,
    buildCandidates,
    type Candidate,
    type CandidateSummary,
} from "./outcomes.ts";
import { MODEL_IDS } from "./prices.ts";
import {
    CONSTANTS_VERSION,
    type Experience,
    type Intention,
    MODEL_OUTPUT_SCHEMA,
    type ModelOutcome,
    type Outcome,
    type Sex,
} from "./schemas.ts";
import {
    assertNutritionInvariants,
    unconfirmedKeys,
    validateOutcomesText,
} from "./validators.ts";

export const PLAN_NUTRITION_MAX_TOKENS = 1200;

const INTENTIONS: Intention[] = [
    "strength",
    "muscle",
    "fat_loss",
    "recomposition",
    "maintenance",
];
const EXPERIENCES: Experience[] = ["beginner", "intermediate", "advanced"];

const isRecord = (v: unknown): v is Record<string, unknown> =>
    typeof v === "object" && v !== null && !Array.isArray(v);
const rec = (v: unknown): Record<string, unknown> => isRecord(v) ? v : {};
const num = (v: unknown): number | null =>
    typeof v === "number" && Number.isFinite(v) ? v : null;

/** `{ "today": "YYYY-MM-DD" }` and nothing else: the client never sends figures. */
export function parseInput(input: unknown): string {
    if (
        !isRecord(input) || Object.keys(input).length !== 1 ||
        typeof input.today !== "string" ||
        !/^\d{4}-\d{2}-\d{2}$/.test(input.today)
    ) {
        throw new CoachInputError("INVALID_INPUT", "Input must be { today }");
    }
    const d = new Date(`${input.today}T00:00:00Z`);
    if (
        Number.isNaN(d.getTime()) ||
        d.toISOString().slice(0, 10) !== input.today
    ) {
        throw new CoachInputError("INVALID_INPUT", "today is not a valid date");
    }
    return input.today;
}

const SYSTEM = [
    "Eres el entrenador de una app de fitness personal. Recibes un plan de nutrición ya calculado por el servidor y una lista de",
    "objetivos candidatos con sus cifras. NO calculas ni cambias ninguna cifra.",
    "Tu única tarea: elegir entre 0 y 4 candidatos, y para cada uno escribir en español una razón breve (10 a 300 caracteres,",
    "basada en los datos del usuario, sin consejos medicos ni diagnosticos) y una fecha límite.",
    'Responde solo con JSON: {"outcomes":[{"candidate_id":"...","reason":"...","deadline":"YYYY-MM-DD"}]}.',
    "Cada candidate_id debe existir en la lista y no repetirse. Una lista vacía es válida.",
].join(" ");

export function prepare(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    return prepareAsync(input, ctx);
}

async function prepareAsync(
    input: unknown,
    ctx: PrepareContext,
): Promise<Prepared> {
    const today = parseInput(input);
    const summary = await ctx.loadContext(today);
    const s = rec(summary);
    const profile = rec(s.profile);
    const estimates = rec(s.estimates);
    const prefs = rec(s.training_preferences);
    const expenditure = rec(estimates.total_expenditure);

    const intention = INTENTIONS.find((i) => i === prefs.intention);
    const sex: Sex | null = profile.sex === "male" || profile.sex === "female"
        ? profile.sex
        : null;
    const positive = (v: number | null) => v !== null && v > 0;
    const missing: string[] = [];
    if (!intention) missing.push("intention");
    if (sex === null) missing.push("sex");
    if (!positive(num(profile.weight_kg))) missing.push("weight");
    if (!positive(num(expenditure.value))) missing.push("expenditure");
    if (!intention || missing.length > 0) {
        throw new CoachInputError(
            "coach_missing_data",
            "Required data is missing",
            missing,
        );
    }
    const experience =
        EXPERIENCES.find((e) => e === rec(estimates.experience).value) ??
            "beginner";
    const unconfirmed = unconfirmedKeys(estimates);

    let nutrition;
    try {
        nutrition = computeNutrition({
            intention,
            sex,
            weightKg: num(profile.weight_kg),
            expenditureKcal: num(expenditure.value),
            expenditureSource: typeof expenditure.source === "string"
                ? expenditure.source
                : "formula",
            experience,
            requiresConfirmation: unconfirmed.length > 0,
            unconfirmed,
        });
    } catch (e) {
        if (e instanceof CoachPlanError) {
            throw new CoachInputError(
                e.code,
                e.code === "coach_missing_data"
                    ? "Sex, weight or expenditure is missing"
                    : "No safe plan fits these numbers",
            );
        }
        throw e;
    }

    const candidates = buildCandidates(
        s as CandidateSummary,
        nutrition,
        today,
    );
    const model = MODEL_IDS.opus;
    const prompt = buildPrompt(today, nutrition, candidates, s);
    let attempts = 0;

    return {
        request: {
            model,
            system: SYSTEM,
            prompt,
            maxTokens: PLAN_NUTRITION_MAX_TOKENS,
            schema: MODEL_OUTPUT_SCHEMA,
        },
        validate: (text) => {
            attempts++;
            return validateOutcomesText(text, candidates, today);
        },
        finalize: async (value, usage) => {
            const chosen = value as ModelOutcome[];
            const byId = new Map(candidates.map((c) => [c.id, c]));
            const outcomes: Outcome[] = chosen.map((o) => {
                const c = byId.get(o.candidate_id)!;
                return {
                    kind: c.kind,
                    key: c.key,
                    target_value: c.target_value,
                    unit: c.unit,
                    reason: o.reason,
                    deadline: o.deadline,
                    basis: c.basis,
                };
            });
            assertNutritionInvariants(nutrition);
            const saved = await ctx.savePlan({
                blockStart: today,
                intention,
                nutrition,
                outcomes,
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
                recommendation_id: saved.recommendation_id,
                nutrition,
                outcomes,
            };
        },
    };
}

function buildPrompt(
    today: string,
    nutrition: unknown,
    candidates: Candidate[],
    summary: Record<string, unknown>,
): string {
    return JSON.stringify({
        today,
        block: {
            start: today,
            deadline_window: [addDays(today, 7), addDays(today, 28)],
        },
        nutrition_plan: nutrition,
        candidates: candidates.map((c) => ({
            candidate_id: c.id,
            kind: c.kind,
            target_value: c.target_value,
            unit: c.unit,
            default_deadline: c.default_deadline,
            basis: c.basis,
        })),
        summary,
    });
}
