package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachReviewRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.GoalChange
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val DAY = LocalDate(2027, 1, 6)

private val EARLY = BlockReview(
    id = null,
    planId = "p1",
    endedOn = DAY,
    adherence = BlockAdherence(3, 4, 75),
    lifts = emptyList(),
    groups = emptyList(),
    weight = null,
    outcomes = emptyList(),
    waist = null,
    stalls = emptyList(),
    phaseProposal = null,
    phaseDecision = null,
    early = true,
)

private val CHANGED = GoalChange(true, "fat_loss", "muscle_gain", 2200)

/** Records the calls and answers with the configured results; the rest of the repository fails as unconfigured. */
private class Recording(
    private val newBlock: Result<NewBlockEntry?> = Result.success(null),
    private val preview: Result<GoalSwitchPreview> = Result.success(GoalSwitchPreview("p3", 2200, 2800, Phase.Gain)),
    private val weeks: Result<PhaseWeeks?> = Result.success(null),
) : CoachReviewRepository by UnconfiguredCoachReviewRepository() {
    val calls = mutableListOf<String>()

    override suspend fun openNewBlock(today: LocalDate): Result<NewBlockEntry?> {
        calls += "new:$today"
        return newBlock
    }

    override suspend fun previewGoalSwitch(today: LocalDate): Result<GoalSwitchPreview> {
        calls += "preview:$today"
        return preview
    }

    override suspend fun weeksInPhase(today: LocalDate): Result<PhaseWeeks?> {
        calls += "weeks:$today"
        return weeks
    }
}

class NewBlockUseCasesTest {
    @Test
    fun openNewBlock_returnsTheEntry_andDoesNotPreviewByItself() = runTest {
        val entry = NewBlockEntry(EARLY, CHANGED)
        val repository = Recording(newBlock = Result.success(entry))

        assertEquals(entry, OpenNewBlockUseCase(repository)(DAY).getOrThrow())
        assertEquals(listOf("new:2027-01-06"), repository.calls)
    }

    @Test
    fun openNewBlock_withoutABlock_isNull() = runTest {
        assertNull(OpenNewBlockUseCase(Recording())(DAY).getOrThrow())
    }

    @Test
    fun openNewBlock_failureIsPassedThrough() = runTest {
        val failure = AppException(AppError.Network)

        val result = OpenNewBlockUseCase(Recording(newBlock = Result.failure(failure)))(DAY)

        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun previewGoalSwitch_returnsThePreview() = runTest {
        val repository = Recording()

        assertEquals(
            GoalSwitchPreview("p3", 2200, 2800, Phase.Gain),
            PreviewGoalSwitchUseCase(repository)(DAY).getOrThrow(),
        )
        assertEquals(listOf("preview:2027-01-06"), repository.calls)
    }

    @Test
    fun previewGoalSwitch_failureIsPassedThrough() = runTest {
        val failure = AppException(AppError.Network)

        val result = PreviewGoalSwitchUseCase(Recording(preview = Result.failure(failure)))(DAY)

        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun loadWeeksInPhase_returnsTheWeeks() = runTest {
        val repository = Recording(weeks = Result.success(PhaseWeeks(Phase.Cut, 9)))

        assertEquals(PhaseWeeks(Phase.Cut, 9), LoadWeeksInPhaseUseCase(repository)(DAY).getOrThrow())
    }

    @Test
    fun loadWeeksInPhase_failureIsPassedThrough() = runTest {
        val failure = AppException(AppError.Network)

        val result = LoadWeeksInPhaseUseCase(Recording(weeks = Result.failure(failure)))(DAY)

        assertEquals(failure, result.exceptionOrNull())
    }
}
