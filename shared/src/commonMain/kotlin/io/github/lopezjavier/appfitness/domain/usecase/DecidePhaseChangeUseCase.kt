package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/** Outcome of a phase decision; a failure keeps the [decision], so the screen can offer a retry with it. */
sealed interface PhaseChangeResult {
    /** Stored; [nextPlanId] is the proposed plan built by "Aceptar" (null after "Seguir como estoy"). */
    data class Done(val decision: PhaseDecision, val nextPlanId: String?) : PhaseChangeResult

    data class Failed(val decision: PhaseDecision, val error: Throwable) : PhaseChangeResult
}

/** "Aceptar" ([PhaseDecision.Accepted]) or "Seguir como estoy" ([PhaseDecision.Kept]) on the review's proposal. */
class DecidePhaseChangeUseCase(private val repository: CoachReviewRepository) {
    suspend operator fun invoke(reviewId: String, decision: PhaseDecision, today: LocalDate): PhaseChangeResult =
        when (decision) {
            PhaseDecision.Accepted -> repository.acceptPhase(reviewId, today).fold(
                onSuccess = { PhaseChangeResult.Done(decision, it) },
                onFailure = { PhaseChangeResult.Failed(decision, it) },
            )

            PhaseDecision.Kept -> repository.keepPhase(reviewId).fold(
                onSuccess = { PhaseChangeResult.Done(decision, null) },
                onFailure = { PhaseChangeResult.Failed(decision, it) },
            )
        }
}
