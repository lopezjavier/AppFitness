package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Whether the welcome-back card shows for the accepted [CoachPlan] whose block is saved. Reads the newest completed
 * session; days are local to [timeZone]. The block's saved day is its acceptance day, or its start when unknown.
 * A failed read is a failure, which the caller hides.
 */
class LoadWelcomeBack(
    private val sessions: WorkoutSessionRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val compute: ComputeWelcomeBack = ComputeWelcomeBack(),
) {
    suspend operator fun invoke(plan: CoachPlan): Result<Boolean> = runCatching {
        val latest = sessions.listCompleted(offset = 0, limit = 1).getOrThrow().firstOrNull()
        compute(
            today = clock.today(),
            lastCompletedDay = latest?.startedAt?.toLocalDateTime(timeZone)?.date,
            blockSavedDay = plan.acceptedAt?.toLocalDateTime(timeZone)?.date ?: plan.blockStart,
            hasSavedBlock = true,
        )
    }
}
