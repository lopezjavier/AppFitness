package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val TODAY = LocalDate(2027, 1, 6)

private val REVIEW = BlockReview(
    id = "r1",
    planId = "p1",
    endedOn = LocalDate(2027, 1, 5),
    adherence = BlockAdherence(14, 16, 88),
    lifts = emptyList(),
    groups = emptyList(),
    weight = null,
    outcomes = emptyList(),
    waist = null,
    stalls = emptyList(),
    phaseProposal = null,
    phaseDecision = null,
)

private class FakeReviewRepository(
    private val review: Result<BlockReview?> = Result.success(null),
    private val accept: Result<String> = Result.success("p2"),
    private val keep: Result<Unit> = Result.success(Unit),
) : CoachReviewRepository {
    val calls = mutableListOf<String>()

    override suspend fun openReview(today: LocalDate): Result<BlockReview?> {
        calls += "open:$today"
        return review
    }

    override suspend fun keepPhase(reviewId: String): Result<Unit> {
        calls += "keep:$reviewId"
        return keep
    }

    override suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String> {
        calls += "accept:$reviewId:$today"
        return accept
    }
}

class LoadBlockReviewUseCaseTest {
    @Test
    fun beforeTheEnd_isNull() = runTest {
        val repository = FakeReviewRepository()

        assertNull(LoadBlockReviewUseCase(repository)(TODAY).getOrThrow())
        assertEquals(listOf("open:2027-01-06"), repository.calls)
    }

    @Test
    fun afterTheEnd_returnsTheReview() = runTest {
        val repository = FakeReviewRepository(review = Result.success(REVIEW))

        assertEquals(REVIEW, LoadBlockReviewUseCase(repository)(TODAY).getOrThrow())
    }

    @Test
    fun repositoryFailure_isPassedThrough() = runTest {
        val repository = FakeReviewRepository(review = Result.failure(AppException(AppError.Network)))

        assertEquals(true, LoadBlockReviewUseCase(repository)(TODAY).isFailure)
    }
}

class DecidePhaseChangeUseCaseTest {
    @Test
    fun accept_buildsThePlanAndReturnsItsId() = runTest {
        val repository = FakeReviewRepository()

        val result = DecidePhaseChangeUseCase(repository)("r1", PhaseDecision.Accepted, TODAY)

        assertEquals(PhaseChangeResult.Done(PhaseDecision.Accepted, "p2"), result)
        assertEquals(listOf("accept:r1:2027-01-06"), repository.calls)
    }

    @Test
    fun keep_storesTheChoiceAndBuildsNoPlan() = runTest {
        val repository = FakeReviewRepository()

        val result = DecidePhaseChangeUseCase(repository)("r1", PhaseDecision.Kept, TODAY)

        assertEquals(PhaseChangeResult.Done(PhaseDecision.Kept, null), result)
        assertEquals(listOf("keep:r1"), repository.calls)
    }

    @Test
    fun failedAccept_keepsTheDecisionForARetry() = runTest {
        val failure = AppException(AppError.Network)
        val repository = FakeReviewRepository(accept = Result.failure(failure))

        val result = DecidePhaseChangeUseCase(repository)("r1", PhaseDecision.Accepted, TODAY)

        assertIs<PhaseChangeResult.Failed>(result)
        assertEquals(PhaseDecision.Accepted, result.decision)
        assertEquals(failure, result.error)
    }

    @Test
    fun failedKeep_keepsTheDecisionForARetry() = runTest {
        val repository = FakeReviewRepository(keep = Result.failure(AppException(AppError.Network)))

        val result = DecidePhaseChangeUseCase(repository)("r1", PhaseDecision.Kept, TODAY)

        assertIs<PhaseChangeResult.Failed>(result)
        assertEquals(PhaseDecision.Kept, result.decision)
    }
}
