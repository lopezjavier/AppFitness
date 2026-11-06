package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingSummaryRepository(private val result: Result<Unit>) : CoachSummaryRepository {
    val marked = mutableListOf<Pair<String, Int>>()

    override suspend fun getLatest(today: LocalDate): Result<WeekSummary?> = Result.success(null)

    override suspend fun markSeen(planId: String, week: Int): Result<Unit> {
        marked += planId to week
        return result
    }
}

class MarkWeekSummarySeenTest {
    @Test
    fun marksThePlanAndWeek() = runTest {
        val repository = RecordingSummaryRepository(Result.success(Unit))

        MarkWeekSummarySeen(repository)("p1", 4).getOrThrow()

        assertEquals(listOf("p1" to 4), repository.marked)
    }

    @Test
    fun repositoryFailureIsPassedThrough() = runTest {
        val repository = RecordingSummaryRepository(Result.failure(AppException(AppError.Network)))

        assertTrue(MarkWeekSummarySeen(repository)("p1", 4).isFailure)
    }
}
