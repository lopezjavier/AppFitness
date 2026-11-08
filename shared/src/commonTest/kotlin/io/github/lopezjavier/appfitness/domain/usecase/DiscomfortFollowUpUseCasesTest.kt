package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private class RecordingFollowUpRepository : DiscomfortFollowUpRepository {
    val calls = mutableListOf<String>()
    val followUp = DiscomfortFollowUp("e1", "d1", Instant.fromEpochSeconds(0), offerSwap = true, showNote = false)

    override suspend fun followUps(): Result<List<DiscomfortFollowUp>> {
        calls += "followUps"
        return Result.success(listOf(followUp))
    }

    override suspend fun swapCandidates(exerciseId: String): Result<List<SwapCandidate>> {
        calls += "candidates:$exerciseId"
        return Result.success(listOf(SwapCandidate("e2", true)))
    }

    override suspend fun swap(exerciseId: String, newExerciseId: String): Result<Int> {
        calls += "swap:$exerciseId>$newExerciseId"
        return Result.success(2)
    }

    override suspend fun declineSwap(exerciseId: String): Result<Unit> {
        calls += "decline:$exerciseId"
        return Result.success(Unit)
    }

    override suspend fun dismissNote(exerciseId: String): Result<Unit> {
        calls += "dismiss:$exerciseId"
        return Result.success(Unit)
    }
}

class DiscomfortFollowUpUseCasesTest {
    private val repository = RecordingFollowUpRepository()

    @Test
    fun load_returnsTheRepositoryFollowUps() = runTest {
        assertEquals(listOf(repository.followUp), LoadDiscomfortFollowUpsUseCase(repository)().getOrThrow())
    }

    @Test
    fun candidates_passTheFlaggedExerciseThrough() = runTest {
        assertEquals(listOf(SwapCandidate("e2", true)), LoadSwapCandidatesUseCase(repository)("e1").getOrThrow())
        assertEquals(listOf("candidates:e1"), repository.calls)
    }

    @Test
    fun swap_passesBothExercisesAndReturnsTheRoutinesChanged() = runTest {
        assertEquals(2, SwapFlaggedExerciseUseCase(repository)("e1", "e2").getOrThrow())
        assertEquals(listOf("swap:e1>e2"), repository.calls)
    }

    @Test
    fun declineAndDismiss_passTheExerciseThrough() = runTest {
        DeclineDiscomfortSwapUseCase(repository)("e1").getOrThrow()
        DismissDiscomfortNoteUseCase(repository)("e2").getOrThrow()
        assertEquals(listOf("decline:e1", "dismiss:e2"), repository.calls)
    }
}
