package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import kotlinx.coroutines.test.runTest
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
    }
}
