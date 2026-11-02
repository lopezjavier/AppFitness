package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.CutRate
import io.github.lopezjavier.appfitness.domain.model.DietBreakApplication
import kotlinx.datetime.LocalDate

/**
 * Nutrition plans of the coach (US-69). Failures carry an [io.github.lopezjavier.appfitness.domain.AppException] or a
 * [io.github.lopezjavier.appfitness.domain.model.CoachException] (the function or a plan RPC refused the call).
 */
interface CoachPlanRepository {
    /** Asks the coach for a nutrition plan as of [today] and returns the stored proposed plan. Costs money. */
    suspend fun requestPlan(today: LocalDate): Result<CoachPlan>

    /** The proposed plan if any, else the accepted one, else null. Used to reload after process death. */
    suspend fun currentPlan(): Result<CoachPlan?>

    /** The accepted plan, or null. */
    suspend fun acceptedPlan(): Result<CoachPlan?>

    /** Accepts the proposed plan (writes the Diario targets). Unconfirmed values fail with NeedsConfirmation. */
    suspend fun acceptPlan(planId: String, today: LocalDate): Result<CoachPlan>

    /** Discards the proposed plan. */
    suspend fun discardPlan(planId: String): Result<Unit>

    /**
     * Changes the weekly cardio minutes of a proposed or accepted plan (0..600 in steps of 5) and returns the plan.
     * Calories, macros and saved routines are never touched.
     */
    suspend fun setCardioMinutes(planId: String, minutes: Int): Result<CoachPlan>

    /**
     * Changes the cut rate ([CutRate]) and the diet break of a proposed cut plan and returns it recomputed.
     * Deterministic: no model call and no usage. Valid rates are 0.5..1.0 (invalid ones fail with `Invalid`).
     */
    suspend fun updatePlanOptions(
        planId: String,
        today: LocalDate,
        cutRatePct: Double,
        dietBreak: Boolean,
    ): Result<CoachPlan>

    /**
     * Switches the Diario target to the diet-break week figures (plan week 4) or back to the base ones (idempotent).
     * Does nothing for a plan without diet break or whose goals the user edited.
     */
    suspend fun applyDietBreak(today: LocalDate): Result<DietBreakApplication>

    /** Evaluates the in-block calorie change (idempotent) and returns the pending one, if any. */
    suspend fun pendingCalorieChange(today: LocalDate): Result<CalorieChange?>

    /** Accepts or discards a pending calorie change. */
    suspend fun decideCalorieChange(id: String, accept: Boolean, today: LocalDate): Result<Unit>
}
