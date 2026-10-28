package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class UnconfiguredTrainingStatsRepositoryTest {
    private val repository = UnconfiguredTrainingStatsRepository()

    @Test
    fun everyCallFailsWithUnknown() = runTest {
        assertEquals(AppError.Unknown, repository.summary(null, null).appErrorOrNull())
        assertEquals(AppError.Unknown, repository.weeklySetCounts(null, null).appErrorOrNull())
        assertEquals(AppError.Unknown, repository.muscleGroupSets(null, null).appErrorOrNull())
        assertEquals(AppError.Unknown, repository.exerciseSetCounts(null, null).appErrorOrNull())
        assertEquals(
            AppError.Unknown,
            repository.weeklyRecords(LocalDate(2026, 11, 9), LocalDate(2026, 11, 15)).appErrorOrNull(),
        )
    }
}
