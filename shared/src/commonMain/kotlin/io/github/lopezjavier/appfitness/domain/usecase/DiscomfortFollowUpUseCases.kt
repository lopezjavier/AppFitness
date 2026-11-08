package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository

// Thin on purpose: SQL is the source of truth for the 14-day and 3 level 4-5 log triggers (the clock is the server's
// `now()`), for the declined / dismissed state and for the candidate rules. Nothing is recomputed on the device.

/** Follow-ups (swap offer and health note) of the user's unresolved level 4-5 discomforts, one per exercise. */
class LoadDiscomfortFollowUpsUseCase(private val repository: DiscomfortFollowUpRepository) {
    suspend operator fun invoke(): Result<List<DiscomfortFollowUp>> = repository.followUps()
}

/** Replacements for a flagged exercise, in the order the server ranks them (favourites first, then similarity). */
class LoadSwapCandidatesUseCase(private val repository: DiscomfortFollowUpRepository) {
    suspend operator fun invoke(exerciseId: String): Result<List<SwapCandidate>> = repository.swapCandidates(exerciseId)
}

/** Applies the swap, on the user's tap only, to every routine of the block folder. Returns the routines changed. */
class SwapFlaggedExerciseUseCase(private val repository: DiscomfortFollowUpRepository) {
    suspend operator fun invoke(exerciseId: String, newExerciseId: String): Result<Int> =
        repository.swap(exerciseId, newExerciseId)
}

/** "Ahora no" on the swap offer: stored per discomfort row, so it holds on every device. */
class DeclineDiscomfortSwapUseCase(private val repository: DiscomfortFollowUpRepository) {
    suspend operator fun invoke(exerciseId: String): Result<Unit> = repository.declineSwap(exerciseId)
}

/** Closes the one-time health note, stored per discomfort row. */
class DismissDiscomfortNoteUseCase(private val repository: DiscomfortFollowUpRepository) {
    suspend operator fun invoke(exerciseId: String): Result<Unit> = repository.dismissNote(exerciseId)
}
