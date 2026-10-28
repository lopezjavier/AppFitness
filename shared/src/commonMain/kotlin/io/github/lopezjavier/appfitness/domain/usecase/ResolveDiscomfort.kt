package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository

/** "Ya no me molesta": closes every unresolved discomfort of the exercise at the current instant. */
class ResolveDiscomfort(private val repository: DiscomfortRepository, private val clock: Clock) {
    suspend operator fun invoke(exerciseId: String): Result<Unit> = repository.resolveOpen(exerciseId, clock.now())
}
