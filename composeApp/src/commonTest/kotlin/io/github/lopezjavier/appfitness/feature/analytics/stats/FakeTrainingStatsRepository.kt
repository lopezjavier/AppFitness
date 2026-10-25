package io.github.lopezjavier.appfitness.feature.analytics.stats

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.repository.TrainingStatsRepository
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

/** Records every window it is asked for; answers depend only on the window bounds via the lambdas. */
class FakeTrainingStatsRepository : TrainingStatsRepository {
    data class Window(val from: LocalDate?, val to: LocalDate?)

    var summaryFor: (Window) -> TrainingSummary = { TrainingSummary(4, 40, 1000.0, 7200) }
    var weeklyFor: (Window) -> List<SetCountPoint> = { emptyList() }
    var muscleFor: (Window) -> List<MuscleGroupSets> = { emptyList() }
    var exercisesFor: (Window, MuscleGroup?) -> List<ExerciseSetCount> = { _, _ -> emptyList() }

    /** Per-call failures; set to a non-null error to fail that call. */
    var summaryFailure: AppError? = null
    var weeklyFailure: AppError? = null
    var muscleFailure: AppError? = null
    var exercisesFailure: AppError? = null
    var detailFailure: AppError? = null

    /** Virtual delay for the summary call, per window (to test stale results). */
    var summaryDelay: (Window) -> Long = { 0L }

    val summaryCalls = mutableListOf<Window>()
    val weeklyCalls = mutableListOf<Window>()
    val muscleCalls = mutableListOf<Window>()
    val exerciseCalls = mutableListOf<Pair<Window, MuscleGroup?>>()

    override suspend fun summary(from: LocalDate?, to: LocalDate?): Result<TrainingSummary> {
        val window = Window(from, to)
        summaryCalls += window
        delay(summaryDelay(window))
        return summaryFailure?.let { Result.failure(AppException(it)) } ?: Result.success(summaryFor(window))
    }

    override suspend fun weeklySetCounts(from: LocalDate?, to: LocalDate?): Result<List<SetCountPoint>> {
        val window = Window(from, to)
        weeklyCalls += window
        return weeklyFailure?.let { Result.failure(AppException(it)) } ?: Result.success(weeklyFor(window))
    }

    override suspend fun muscleGroupSets(from: LocalDate?, to: LocalDate?): Result<List<MuscleGroupSets>> {
        val window = Window(from, to)
        muscleCalls += window
        return muscleFailure?.let { Result.failure(AppException(it)) } ?: Result.success(muscleFor(window))
    }

    override suspend fun exerciseSetCounts(
        from: LocalDate?,
        to: LocalDate?,
        muscleGroup: MuscleGroup?,
    ): Result<List<ExerciseSetCount>> {
        val window = Window(from, to)
        exerciseCalls += window to muscleGroup
        val failure = if (muscleGroup == null) exercisesFailure else detailFailure
        return failure?.let { Result.failure(AppException(it)) } ?: Result.success(exercisesFor(window, muscleGroup))
    }
}
