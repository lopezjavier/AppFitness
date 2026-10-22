package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.feature.workout.doneSet
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** In-memory [StrengthRepository]. */
internal class FakeStrengthRepository : StrengthRepository {
    var sessions: List<WorkoutSession> = emptyList()
    var maxReps: Map<String, List<MaxRepsPoint>> = emptyMap()
    var sessionsFailure: AppError? = null
    var maxRepsFailure: AppError? = null
    var maxRepsCalls = 0

    /** The `includeWarmUp` flag of every [maxRepsHistory] call. */
    val maxRepsWarmUpArgs = mutableListOf<Boolean>()
    var maxRepsWarmUp: Map<String, List<MaxRepsPoint>> = emptyMap()
    var volumeRows: List<WeeklyMuscleVolumeRow> = emptyList()
    var volumeFailure: AppError? = null
    var volumeFromArgs = mutableListOf<LocalDate>()

    override suspend fun weeklyMuscleVolume(from: LocalDate): Result<List<WeeklyMuscleVolumeRow>> {
        volumeFromArgs += from
        return volumeFailure?.let { Result.failure(AppException(it)) } ?: Result.success(volumeRows)
    }

    override suspend fun recentSessions(since: Instant): Result<List<WorkoutSession>> =
        sessionsFailure?.let { Result.failure(AppException(it)) } ?: Result.success(sessions)

    override suspend fun maxRepsHistory(
        exerciseId: String,
        from: LocalDate?,
        includeWarmUp: Boolean,
    ): Result<List<MaxRepsPoint>> {
        maxRepsCalls++
        maxRepsWarmUpArgs += includeWarmUp
        val source = if (includeWarmUp) maxRepsWarmUp.takeIf { it.isNotEmpty() } ?: maxReps else maxReps
        return maxRepsFailure?.let { Result.failure(AppException(it)) }
            ?: Result.success(source[exerciseId].orEmpty())
    }
}

/** A completed session started [start] where each id in [exerciseIds] has one completed set. */
internal fun completedWith(id: String, start: String, vararg exerciseIds: String) = WorkoutSession(
    id,
    null,
    id,
    SessionStatus.Completed,
    Instant.parse(start),
    null,
    exerciseIds.mapIndexed { index, exerciseId ->
        SessionExercise(exerciseId, index, null, listOf(doneSet(1, 50.0, 5)))
    },
)
