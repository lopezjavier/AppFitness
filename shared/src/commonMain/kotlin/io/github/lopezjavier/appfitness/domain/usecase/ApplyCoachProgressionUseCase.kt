package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository

/** Asks the server to apply the progression of a finished session; idempotent, so a retry is harmless. */
class ApplyCoachProgressionUseCase(private val repository: CoachChangesRepository) {
    suspend operator fun invoke(sessionId: String): Result<List<CoachChange>> = repository.apply(sessionId)
}
