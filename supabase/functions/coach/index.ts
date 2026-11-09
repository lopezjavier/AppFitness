// Entrypoint of the `coach` Edge Function (verify_jwt = true in config.toml).
// POST { kind, input } -> text/event-stream; see handler.ts for the contract.
// Serves unconditionally: tests import handler.ts, never this file.
import { authenticate, callerClient } from "../_shared/food-store.ts";
import { CoachInputError } from "./context.ts";
import { createCoachHandler } from "./handler.ts";
import type { CatalogExercise, Nutrition, PhaseHistory } from "./schemas.ts";

const handler = createCoachHandler({
    authenticate,
    // Both RPCs run with the caller's own token: the data is theirs and RLS/auth.uid() apply.
    loadContext: async (token, today) => {
        const { data, error } = await callerClient(token).rpc("coach_summary", {
            p_today: today,
        });
        if (error || data == null) throw new Error("summary read failed");
        return data;
    },
    loadInputs: async (token, kind, today) => {
        const { data, error } = await callerClient(token).rpc(
            "coach_plan_inputs",
            { p_kind: kind, p_today: today },
        );
        if (error || data == null) throw new Error("inputs read failed");
        return data;
    },
    savePlan: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "propose_coach_plan",
            {
                p_block_start: row.blockStart,
                p_intention: row.intention,
                p_nutrition: row.nutrition,
                p_outcomes: row.outcomes,
                p_method: row.method,
                p_input_summary: row.inputSummary,
            },
        );
        if (error?.message?.includes("coach_block_in_progress")) {
            throw new CoachInputError(
                "coach_block_in_progress",
                "A block is in progress: use Bloque nuevo",
            );
        }
        if (error || !data?.plan_id) throw new Error("plan write failed");
        return {
            plan_id: String(data.plan_id),
            recommendation_id: String(data.recommendation_id),
        };
    },
    loadCatalog: async (token) => {
        // RLS: the catalogue plus the caller's own custom exercises.
        const { data, error } = await callerClient(token)
            .from("exercises")
            .select(
                "id,key,name,muscle_group,secondary_muscles,equipment,movement_pattern,metric_type,is_custom,counts_as_strength",
            );
        if (error || !data) throw new Error("catalog read failed");
        return data as CatalogExercise[];
    },
    loadPlan: async (token, planId) => {
        const { data, error } = await callerClient(token)
            .from("coach_plans")
            .select(
                "status,folder_id,phase,nutrition,cardio_min_week:nutrition->activity->cardio_min_week",
            )
            .eq("id", planId)
            .maybeSingle();
        if (error) throw new Error("plan read failed");
        return data
            ? {
                status: String(data.status),
                training_saved: data.folder_id != null,
                cardio_min_week: typeof data.cardio_min_week === "number"
                    ? data.cardio_min_week
                    : null,
                phase: typeof data.phase === "string" ? data.phase : null,
                nutrition: (data.nutrition ?? null) as Nutrition | null,
            }
            : null;
    },
    attachBlock: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "attach_coach_block",
            {
                p_plan_id: row.planId,
                p_training: row.training,
                p_sessions: row.sessions,
                p_method: row.method,
                p_input_summary: row.inputSummary,
            },
        );
        if (error || !data?.plan_id) throw new Error("block write failed");
        return {
            plan_id: String(data.plan_id),
            session_ids: (data.sessions as unknown[] ?? []).map(String),
        };
    },
    loadPhaseHistory: async (token, today) => {
        const { data, error } = await callerClient(token).rpc(
            "coach_phase_history",
            { p_today: today },
        );
        if (error || data == null) throw new Error("phase history read failed");
        return data as PhaseHistory;
    },
    updatePlanOptions: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "update_coach_plan_options",
            {
                p_plan_id: row.planId,
                p_nutrition: row.nutrition,
                p_cut_rate_pct: row.cutRatePct,
                p_diet_break: row.dietBreak,
            },
        );
        if (error || !data?.plan_id) {
            throw new Error("plan options write failed");
        }
        return {
            plan_id: String(data.plan_id),
            nutrition: data.nutrition,
            outcomes: data.outcomes ?? [],
        };
    },
    setDietBreak: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "set_coach_plan_diet_break",
            {
                p_plan_id: row.planId,
                p_diet_break: row.dietBreak,
                p_week: row.week,
            },
        );
        if (error || !data?.plan_id) {
            throw new Error("diet break write failed");
        }
        return { plan_id: String(data.plan_id), nutrition: data.nutrition };
    },
    loadReview: async (token, reviewId) => {
        const { data, error } = await callerClient(token)
            .from("coach_block_reviews")
            .select("phase_proposal,phase_decision,plan:plan_id(intention)")
            .eq("id", reviewId)
            .maybeSingle();
        if (error) throw new Error("review read failed");
        if (!data) return null;
        const plan = Array.isArray(data.plan) ? data.plan[0] : data.plan;
        return {
            phase_proposal: data.phase_proposal ?? null,
            phase_decision: data.phase_decision ?? null,
            intention: typeof plan?.intention === "string"
                ? plan.intention
                : null,
        };
    },
    applyPhaseChange: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "accept_coach_phase",
            {
                p_review_id: row.reviewId,
                p_nutrition: row.nutrition,
                p_outcomes: row.outcomes,
                p_method: row.method,
                p_input_summary: row.inputSummary,
                p_block_start: row.blockStart,
            },
        );
        if (error || !data?.plan_id) throw new Error("phase change failed");
        return { plan_id: String(data.plan_id) };
    },
    applyGoalSwitch: async (token, row) => {
        const { data, error } = await callerClient(token).rpc(
            "propose_goal_switch",
            {
                p_today: row.today,
                p_nutrition: row.nutrition,
                p_outcomes: row.outcomes,
                p_method: row.method,
                p_input_summary: row.inputSummary,
            },
        );
        if (error || !data?.plan_id) throw new Error("goal switch failed");
        return {
            plan_id: String(data.plan_id),
            current_kcal: typeof data.current_kcal === "number"
                ? data.current_kcal
                : null,
            new_kcal: Number(data.new_kcal),
            phase: String(data.phase),
        };
    },
    now: () => new Date(),
});

Deno.serve(handler);
