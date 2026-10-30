package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import kotlinx.datetime.LocalDate

/** Weeks of the saved block with their adherence, and the deload suggestion (T-165). */
interface CoachWeeksRepository {
    suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>>

    /** Evaluates the rule (idempotent) and returns the pending proposal, or null when there is none. */
    suspend fun pendingDeload(today: LocalDate): Result<DeloadProposal?>

    /** Accepting marks the proposal's week as a deload week; discarding changes nothing else. */
    suspend fun decideDeload(id: String, accept: Boolean): Result<Unit>
}
