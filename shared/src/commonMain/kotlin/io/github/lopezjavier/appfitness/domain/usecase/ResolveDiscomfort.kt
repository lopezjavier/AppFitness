package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository

/**
 * "Ya no me molesta": closes every unresolved discomfort of the exercise at the current instant. `logged_at` is set by
 * the server, so a device clock running behind would break `resolved_at >= logged_at`: the instant is clamped up to
 * the newest `logged_at` among the rows being closed.
 */
class ResolveDiscomfort(private val repository: DiscomfortRepository, private val clock: Clock) {
    suspend operator fun invoke(exerciseId: String): Result<Unit> {
        val now = clock.now()
        val newestLogged = repository.unresolved(setOf(exerciseId)).getOrNull()?.maxOfOrNull { it.loggedAt }
        return repository.resolveOpen(exerciseId, if (newestLogged != null && newestLogged > now) newestLogged else now)
    }
}
