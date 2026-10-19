package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Read-only strength data of the signed-in user (per-session 1RM and volume come from [WorkoutSessionRepository]). */
interface StrengthRepository {
    /** Completed sessions started at or after [since], with their sets. */
    suspend fun recentSessions(since: Instant): Result<List<WorkoutSession>>

    /** Max repetitions per completed session of the exercise, oldest first. */
    suspend fun maxRepsHistory(exerciseId: String, from: LocalDate? = null): Result<List<MaxRepsPoint>>
}
