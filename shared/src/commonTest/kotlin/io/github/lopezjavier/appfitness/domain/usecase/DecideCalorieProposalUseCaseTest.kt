package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachPlanRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecideCalorieProposalUseCaseTest {
    private val today = LocalDate(2026, 10, 29)
    private val calls = mutableListOf<Triple<String, Boolean, LocalDate>>()
    private var answer: Result<Unit> = Result.success(Unit)

    private val repository: CoachPlanRepository = object : CoachPlanRepository by UnconfiguredCoachPlanRepository() {
        override suspend fun decideCalorieProposal(id: String, apply: Boolean, today: LocalDate): Result<Unit> {
            calls += Triple(id, apply, today)
            return answer
        }
    }

    @Test
    fun apply_sendsTheProposalAsApplied() = runTest {
        val result = DecideCalorieProposalUseCase(repository).apply("c1", today)

        assertTrue(result.isSuccess)
        assertEquals(listOf(Triple("c1", true, today)), calls)
    }

    @Test
    fun notNow_sendsTheProposalAsDeclined() = runTest {
        val result = DecideCalorieProposalUseCase(repository).notNow("c1", today)

        assertTrue(result.isSuccess)
        assertEquals(listOf(Triple("c1", false, today)), calls)
    }

    @Test
    fun aFailure_isPassedThrough() = runTest {
        answer = Result.failure(AppException(AppError.Network))

        val result = DecideCalorieProposalUseCase(repository).apply("c1", today)

        assertTrue(result.isFailure)
    }
}
