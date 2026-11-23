package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.RangeProgressResult
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleSets
import kotlinx.datetime.LocalDate

/** Training statistics of the signed-in user over UTC-date windows; a null bound is open. */
interface TrainingStatsRepository {
    suspend fun summary(from: LocalDate?, to: LocalDate?): Result<TrainingSummary>

    suspend fun weeklySetCounts(from: LocalDate?, to: LocalDate?): Result<List<SetCountPoint>>

    suspend fun muscleGroupSets(from: LocalDate?, to: LocalDate?): Result<List<MuscleGroupSets>>

    suspend fun exerciseSetCounts(
        from: LocalDate?,
        to: LocalDate?,
        muscleGroup: MuscleGroup? = null,
    ): Result<List<ExerciseSetCount>>

    /** Progress per exercise and records of the window in one call (planning/07 section 3 D1, D2). */
    suspend fun rangeProgress(from: LocalDate?, to: LocalDate?): Result<RangeProgressResult>

    /** Sets per muscle group of each of the last [weeks] ISO weeks, oldest first, the last the current week. */
    suspend fun weeklyMuscleSets(weeks: Int = 8): Result<List<WeeklyMuscleSets>>
}
