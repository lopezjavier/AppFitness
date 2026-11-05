package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import kotlinx.datetime.LocalDate

/**
 * Weeks of the saved block with their adherence, and the deload week (T-165, T-230). A deload applies on its own and is
 * a [CoachChange] without exercise. Refusals fail with a `CoachChangeException`.
 */
interface CoachWeeksRepository {
    suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>>

    /** Evaluates the rule (idempotent): the deload it applied (or applied before) in plan week 4, or null. */
    suspend fun evaluateDeload(today: LocalDate): Result<CoachChange?>

    /** "Hacer descarga la próxima semana": fails with `DeloadNotAvailable` when one is planned or ran in the block. */
    suspend fun requestDeload(): Result<CoachChange>

    /** "Cancelar descarga": the week goes back to normal; a second cancel fails with `NotUndoable`. */
    suspend fun cancelDeload(changeId: String): Result<CoachChange>
}
