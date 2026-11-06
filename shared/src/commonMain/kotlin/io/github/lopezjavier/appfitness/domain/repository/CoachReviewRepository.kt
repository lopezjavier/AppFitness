package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import kotlinx.datetime.LocalDate

/** The end-of-block review and the phase decision (US-101, US-103). */
interface CoachReviewRepository {
    /** The review of the accepted block, created on its first call after the last day; null before that. */
    suspend fun openReview(today: LocalDate): Result<BlockReview?>

    /** "Seguir como estoy": stores the choice and builds no plan. */
    suspend fun keepPhase(reviewId: String): Result<Unit>

    /** "Aceptar": builds the proposed plan of the new phase (deterministic, no model); returns its id. */
    suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String>
}
