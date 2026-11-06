package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision

sealed interface BlockReviewUiState {
    data object Loading : BlockReviewUiState

    /**
     * The read-only review. [exerciseNames] resolves the lifts, outcomes and stalls (R-54): a lift whose name is
     * unknown is not listed. [calorieProposal] is the pending calorie change, if any. [deciding] is the phase choice
     * in flight; [decisionFailed] is the choice that failed, kept so "Reintentar" repeats it. The stored decision is
     * `review.phaseDecision`. [nextPlanId] is the proposed plan built by "Aceptar".
     */
    data class Content(
        val review: BlockReview,
        val exerciseNames: Map<String, String>,
        val calorieProposal: CalorieProposal? = null,
        val deciding: PhaseDecision? = null,
        val decisionFailed: PhaseDecision? = null,
        val decidingCalorie: Boolean = false,
        val calorieFailed: CalorieChoice? = null,
        val nextPlanId: String? = null,
    ) : BlockReviewUiState {
        /** The proposal still waits for an answer. */
        val phaseDecidable: Boolean
            get() = review.phaseProposal != null && review.phaseDecision == null
    }

    /** The block has not ended (or the review is gone): "Aún no hay revisión del bloque." */
    data object Empty : BlockReviewUiState

    data object Error : BlockReviewUiState

    data object Offline : BlockReviewUiState
}

/** The calorie choice that failed, so the retry repeats the same one. */
enum class CalorieChoice { Apply, NotNow }

sealed interface BlockReviewEvent {
    /** "Reintentar" after a failed load, or "Recargar" when offline. */
    data object Retry : BlockReviewEvent

    /** "Aceptar" or "Seguir como estoy" on the phase proposal. */
    data class DecidePhase(val decision: PhaseDecision) : BlockReviewEvent

    /** "Reintentar" after a failed decision: repeats the kept choice. */
    data object RetryDecision : BlockReviewEvent

    /** "Aplicar" on the pending calorie proposal. */
    data object ApplyCalorie : BlockReviewEvent

    /** "Ahora no" on the pending calorie proposal. */
    data object NotNowCalorie : BlockReviewEvent
}

/** One-off effects for haptics (R-29). */
sealed interface BlockReviewEffect {
    data class PhaseDecided(val decision: PhaseDecision) : BlockReviewEffect

    data object PhaseDecisionFailed : BlockReviewEffect

    data class CalorieDecided(val choice: CalorieChoice) : BlockReviewEffect

    data object CalorieFailed : BlockReviewEffect
}
