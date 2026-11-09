package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import kotlinx.datetime.LocalDate

/** The end-of-block review and the phase decision (US-101, US-103), and the "Bloque nuevo" entry (US-110). */
interface CoachReviewRepository {
    /** The review of the accepted block, created on its first call after the last day; null before that. */
    suspend fun openReview(today: LocalDate): Result<BlockReview?>

    /** "Seguir como estoy": stores the choice and builds no plan. */
    suspend fun keepPhase(reviewId: String): Result<Unit>

    /** Marks the stored review as seen (opening it), so its block-end notice is no longer listed. */
    suspend fun markSeen(reviewId: String): Result<Unit>

    /** "Aceptar": builds the proposed plan of the new phase (deterministic, no model); returns its id. */
    suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String>

    /** "Bloque nuevo": the review so far (computed, nothing stored mid-block) and the goal; null without a block. */
    suspend fun openNewBlock(today: LocalDate): Result<NewBlockEntry?>

    /** Builds and stores the proposed plan of the saved goal (deterministic, no model); changes nothing else. */
    suspend fun previewGoalSwitch(today: LocalDate): Result<GoalSwitchPreview>

    /** Weeks started in the current calorie phase; null without an accepted plan. */
    suspend fun weeksInPhase(today: LocalDate): Result<PhaseWeeks?>
}
